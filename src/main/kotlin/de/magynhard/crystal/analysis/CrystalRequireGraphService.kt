@file:Suppress("DEPRECATION")

package de.magynhard.crystal.analysis

import com.intellij.ProjectTopics
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiTreeChangeAdapter
import com.intellij.psi.PsiTreeChangeEvent
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.CrystalFile
import de.magynhard.crystal.psi.CrystalRequireStatement
import de.magynhard.crystal.sdk.CrystalStdlibResolver
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicLong

internal data class CrystalEffectiveSourceSet(val files: Set<VirtualFile>) {
    fun contains(element: PsiElement): Boolean {
        val file = element.containingFile?.originalFile ?: return false
        if (!file.isPhysical) return false
        val virtualFile = file.virtualFile ?: return false
        return virtualFile.isValid && files.contains(virtualFile)
    }
}

internal data class CrystalRequireCacheStats(
    val nodeBuilds: Long,
    val closureBuilds: Long,
    val fullInvalidations: Long,
    val targetedInvalidations: Long,
)

internal data class CrystalRequireCacheIdentity(
    val node: Any?,
    val closure: Any?,
    val sources: CrystalEffectiveSourceSet?,
)

@Service(Service.Level.PROJECT)
internal class CrystalRequireGraphService private constructor(
    private val project: Project,
    private val pathResolver: CrystalRequirePathResolver,
    registerListeners: Boolean,
    private val validationMode: ValidationMode,
    private val collector: (PsiFile) -> CrystalDirectRequires,
    private val trackClosureVisits: Boolean,
) {
    internal enum class ValidationMode {
        EVENT_DRIVEN,
        ALWAYS,
    }

    constructor(project: Project) : this(
        project,
        CrystalRequirePathResolver(project) { CrystalStdlibResolver.cachedStdlibPath(project) },
        true,
        ValidationMode.EVENT_DRIVEN,
        CrystalRequireCollector::collect,
        false,
    )

    internal constructor(project: Project, pathResolver: CrystalRequirePathResolver) :
        this(project, pathResolver, false, ValidationMode.ALWAYS, CrystalRequireCollector::collect, false)

    internal constructor(
        project: Project,
        pathResolver: CrystalRequirePathResolver,
        validationMode: ValidationMode,
        collector: (PsiFile) -> CrystalDirectRequires = CrystalRequireCollector::collect,
        trackClosureVisits: Boolean = false,
    ) : this(project, pathResolver, false, validationMode, collector, trackClosureVisits)

    internal constructor(
        project: Project,
        pathResolver: CrystalRequirePathResolver,
        listenerDisposable: Disposable,
    ) : this(
        project,
        pathResolver,
        false,
        ValidationMode.EVENT_DRIVEN,
        CrystalRequireCollector::collect,
        false,
    ) {
        registerListeners(listenerDisposable)
    }

    private data class Node(
        val fingerprint: String,
        val outgoing: List<VirtualFile>,
        val exactCandidatePaths: Set<String>,
        val wildcardWatches: Set<NodeWildcardWatch>,
        val version: Long,
    )

    private data class NodeWildcardWatch(
        val targetPath: String,
        val mode: CrystalWildcardMode,
    )

    private data class StructuralVfsChange(
        val file: VirtualFile?,
        val paths: Set<String>,
        val isDirectory: Boolean,
    )

    private data class Closure(
        val files: Set<VirtualFile>,
        val dependencyVersions: Map<VirtualFile, Long>,
    )

    private data class PreludeFoundation(
        val generation: Long,
        val closure: Closure?,
        val files: Set<VirtualFile>,
    )

    private data class PreludeBuild(
        val generation: Long,
        val result: CompletableFuture<VirtualFile?>,
    )

    private data class EffectiveSnapshot(
        val foundation: PreludeFoundation,
        val closure: Closure,
        val sources: CrystalEffectiveSourceSet,
    )

    private object StaleGeneration : RuntimeException()

    private val lock = Any()
    private val nodes = mutableMapOf<VirtualFile, Node>()
    private val reverseEdges = mutableMapOf<VirtualFile, MutableSet<VirtualFile>>()
    private val closures = mutableMapOf<VirtualFile, Closure>()
    private val closureOwners = mutableMapOf<VirtualFile, MutableSet<VirtualFile>>()
    private val effectiveSnapshots = mutableMapOf<VirtualFile, EffectiveSnapshot>()
    private val dirtyContentNodes = mutableSetOf<VirtualFile>()
    private val dirtyClosureRoots = mutableSetOf<VirtualFile>()
    private var preludeClosureDirty = false
    private var preludeBuild: PreludeBuild? = null
    private var cachedPreludeFoundation: PreludeFoundation? = null
    private var observedStdlibRoot: VirtualFile? = null
    private var hasObservedStdlibRoot = false
    private var generation = 0L
    private var nextNodeVersion = 0L
    private var nodeBuilds = 0L
    private var closureBuilds = 0L
    private var fullInvalidations = 0L
    private var targetedInvalidations = 0L
    private val closureVisits = AtomicLong()

    init {
        if (registerListeners) registerListeners(project)
    }

    fun effectiveSources(context: PsiElement): CrystalEffectiveSourceSet {
        return ReadAction.computeBlocking<CrystalEffectiveSourceSet, RuntimeException> {
            val root = physicalOriginalFileInReadAction(context) ?: return@computeBlocking EMPTY_SOURCES
            effectiveSources(root)
        }
    }

    private fun effectiveSources(root: VirtualFile): CrystalEffectiveSourceSet {
        while (true) {
            ProgressManager.checkCanceled()
            val stdlibRoot = pathResolver.currentStdlibRoot()
            val capturedGeneration = captureGeneration(stdlibRoot)
            cleanEffectiveSnapshot(root, capturedGeneration)?.let { return it }
            try {
                val foundation = preludeFoundation(capturedGeneration, stdlibRoot)
                val closure = closure(root, capturedGeneration, stdlibRoot) ?: return EMPTY_SOURCES
                synchronized(lock) {
                    if (
                        generation != capturedGeneration ||
                        foundation.closure?.let(::closureIsCurrent) == false ||
                        !closureIsCurrent(closure)
                    ) {
                        return@synchronized
                    }
                    val cached = effectiveSnapshots[root]
                    if (cached != null && cached.foundation === foundation && cached.closure === closure) {
                        return cached.sources
                    }
                    val files = immutableSet(foundation.files + closure.files)
                    return CrystalEffectiveSourceSet(files).also { sources ->
                        effectiveSnapshots[root] = EffectiveSnapshot(foundation, closure, sources)
                    }
                }
            } catch (_: StaleGeneration) {
                // A full invalidation raced this read; retry against the new generation.
            }
        }
    }

    fun invalidateAll() {
        synchronized(lock) {
            invalidateAllLocked()
        }
    }

    private fun captureGeneration(stdlibRoot: VirtualFile?): Long = synchronized(lock) {
        if (!hasObservedStdlibRoot) {
            observedStdlibRoot = stdlibRoot
            hasObservedStdlibRoot = true
        } else if (observedStdlibRoot !== stdlibRoot) {
            observedStdlibRoot = stdlibRoot
            invalidateAllLocked()
        }
        generation
    }

    private fun invalidateAllLocked() {
        generation++
        nodes.clear()
        reverseEdges.clear()
        closures.clear()
        closureOwners.clear()
        effectiveSnapshots.clear()
        dirtyContentNodes.clear()
        dirtyClosureRoots.clear()
        preludeClosureDirty = false
        preludeBuild = null
        cachedPreludeFoundation = null
        fullInvalidations++
    }

    internal fun invalidateRequireFile(file: VirtualFile) {
        if (synchronized(lock) { !nodes.containsKey(file) }) return
        if (!file.isValid) {
            synchronized(lock) { removeNode(file) }
            return
        }
        val current = ReadAction.computeBlocking<Pair<VirtualFile, String>?, RuntimeException> {
            val psiFile = PsiManager.getInstance(project).findFile(file)
            if (psiFile == null || !psiFile.isValid || !psiFile.isPhysical) return@computeBlocking null
            val originalFile = psiFile.originalFile.virtualFile?.takeIf(::isUsableCrystalFile)
                ?: return@computeBlocking null
            originalFile to collector(psiFile).fingerprint
        }
        val normalizedFile = current?.first ?: file
        synchronized(lock) {
            val cached = nodes[normalizedFile] ?: return
            if (current != null && cached.fingerprint == current.second) return
            removeNode(normalizedFile)
        }
    }

    internal fun cacheStats(): CrystalRequireCacheStats = synchronized(lock) {
        CrystalRequireCacheStats(nodeBuilds, closureBuilds, fullInvalidations, targetedInvalidations)
    }

    internal fun cacheIdentity(file: VirtualFile): CrystalRequireCacheIdentity = synchronized(lock) {
        CrystalRequireCacheIdentity(nodes[file], closures[file], effectiveSnapshots[file]?.sources)
    }

    internal fun closureNodeVisits(): Long = closureVisits.get()

    private fun cleanEffectiveSnapshot(root: VirtualFile, capturedGeneration: Long): CrystalEffectiveSourceSet? =
        synchronized(lock) {
            if (validationMode != ValidationMode.EVENT_DRIVEN || generation != capturedGeneration) return@synchronized null
            if (root in dirtyClosureRoots || preludeClosureDirty) return@synchronized null
            effectiveSnapshots[root]
                ?.takeIf { it.foundation.generation == capturedGeneration }
                ?.sources
        }

    private fun registerListeners(parentDisposable: Disposable) {
        PsiManager.getInstance(project).addPsiTreeChangeListener(object : PsiTreeChangeAdapter() {
            override fun childAdded(event: PsiTreeChangeEvent) = handlePsiChange(event)
            override fun childRemoved(event: PsiTreeChangeEvent) = handlePsiChange(event)
            override fun childReplaced(event: PsiTreeChangeEvent) = handlePsiChange(event)
            override fun childMoved(event: PsiTreeChangeEvent) = handlePsiChange(event)
            override fun childrenChanged(event: PsiTreeChangeEvent) = handlePsiChange(event)
            override fun propertyChanged(event: PsiTreeChangeEvent) = handlePsiChange(event)
        }, parentDisposable)
        project.messageBus.connect(parentDisposable).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) = handleVfsEvents(events)
        })
        project.messageBus.connect(parentDisposable).subscribe(ProjectTopics.PROJECT_ROOTS, object : ModuleRootListener {
            override fun rootsChanged(event: ModuleRootEvent) = invalidateAll()
        })
    }

    internal fun handlePsiChange(event: PsiTreeChangeEvent) {
        val file = (event.file?.takeIf(PsiFile::isValid) ?: sequenceOf(
            event.element,
            event.child,
            event.oldChild,
            event.newChild,
            event.parent,
            event.oldParent,
            event.newParent,
        ).filterNotNull().filter(PsiElement::isValid).mapNotNull(PsiElement::getContainingFile).firstOrNull())
            as? CrystalFile ?: return
        val virtualFile = file.virtualFile ?: return
        val fileLevelChange = sequenceOf(event.parent, event.oldParent, event.newParent).any { it is PsiFile }
        if (
            !fileLevelChange &&
            sequenceOf(event.child, event.oldChild, event.newChild, event.element).none(::affectsRequire)
        ) return
        invalidateRequireFile(virtualFile)
    }

    private fun affectsRequire(element: PsiElement?): Boolean {
        if (element == null || !element.isValid) return false
        return element is CrystalRequireStatement ||
            PsiTreeUtil.getParentOfType(element, CrystalRequireStatement::class.java, false) != null ||
            PsiTreeUtil.findChildOfType(element, CrystalRequireStatement::class.java) != null
    }

    internal fun handleVfsEvents(events: List<VFileEvent>) {
        val projectPath = project.basePath?.trimEnd('/')
        val structuralChanges = events.mapNotNull(::structuralVfsChange)
        val metadataPaths = events.asSequence().map(VFileEvent::getPath).toMutableSet().apply {
            structuralChanges.flatMapTo(this) { it.paths }
        }
        if (projectPath != null && metadataPaths.any { it == "$projectPath/shard.yml" || it == "$projectPath/shard.lock" }) {
            invalidateAll()
            return
        }
        if (projectPath != null && structuralChanges.any { change ->
                change.isDirectory && change.paths.any { path ->
                    path == "$projectPath/lib" || path.substringBeforeLast('/', "") == "$projectPath/lib"
                }
            }
        ) {
            invalidateAll()
            return
        }
        if (structuralChanges.isNotEmpty()) {
            synchronized(lock) {
                val affected = linkedSetOf<VirtualFile>()
                nodes.forEach { (file, node) ->
                    if (
                        node.exactCandidatePaths.any { candidate ->
                            structuralChanges.any { change -> change.affectsExactCandidate(candidate) }
                        } || node.wildcardWatches.any { watch ->
                            structuralChanges.any { change -> change.affectsWildcard(watch) }
                        }
                    ) {
                        affected += file
                    }
                }
                structuralChanges.mapNotNull(StructuralVfsChange::file).forEach { changedFile ->
                    affected += changedFile
                    affected += reverseEdges[changedFile].orEmpty()
                }
                affected.forEach(::removeNode)
            }
        }
        events.asSequence()
            .filterIsInstance<VFileContentChangeEvent>()
            .map(VFileContentChangeEvent::getFile)
            .filter { it.extension == "cr" }
            .distinct()
            .forEach(::markContentChanged)
    }

    private fun markContentChanged(file: VirtualFile) {
        synchronized(lock) {
            if (!nodes.containsKey(file)) return
            dirtyContentNodes += file
            closureOwners[file].orEmpty().forEach(dirtyClosureRoots::add)
            if (cachedPreludeFoundation?.closure?.dependencyVersions?.containsKey(file) == true) {
                preludeClosureDirty = true
            }
        }
    }

    private fun structuralVfsChange(event: VFileEvent): StructuralVfsChange? = when (event) {
        is VFileCreateEvent -> StructuralVfsChange(event.file, setOf(event.path), event.isDirectory)
        is VFileCopyEvent -> StructuralVfsChange(
            event.findCreatedFile(),
            setOf(path(event.newParent.path, event.newChildName)),
            event.file.isDirectory,
        )
        is VFileDeleteEvent -> StructuralVfsChange(event.file, setOf(event.path), event.file.isDirectory)
        is VFileMoveEvent -> StructuralVfsChange(
            event.file,
            setOf(event.oldPath, event.newPath),
            event.file.isDirectory,
        )
        is VFilePropertyChangeEvent -> if (event.isRename) {
            StructuralVfsChange(
                event.file,
                setOf(event.oldPath, event.newPath),
                event.file.isDirectory,
            )
        } else {
            null
        }
        else -> null
    }

    private fun StructuralVfsChange.affectsExactCandidate(candidatePath: String): Boolean =
        paths.any { path -> pathsOverlap(path, candidatePath) }

    private fun StructuralVfsChange.affectsWildcard(watch: NodeWildcardWatch): Boolean = paths.any { path ->
        if (isAncestorOrSame(path, watch.targetPath)) return@any isDirectory || path == watch.targetPath
        if (!isAncestorOrSame(watch.targetPath, path)) return@any false
        if (isDirectory) return@any watch.mode == CrystalWildcardMode.RECURSIVE
        if (path.substringAfterLast('.', "") != "cr") return@any false
        watch.mode == CrystalWildcardMode.RECURSIVE || path.substringBeforeLast('/', "") == watch.targetPath
    }

    private fun pathsOverlap(first: String, second: String): Boolean =
        isAncestorOrSame(first, second) || isAncestorOrSame(second, first)

    private fun isAncestorOrSame(ancestor: String, path: String): Boolean =
        path == ancestor || path.startsWith("${ancestor.trimEnd('/')}/")

    private fun path(parent: String, child: String): String = "${parent.trimEnd('/')}/$child"

    private fun preludeFoundation(capturedGeneration: Long, stdlibRoot: VirtualFile?): PreludeFoundation {
        val root = preludeRoot(capturedGeneration, stdlibRoot)
        val closure = root?.let { closure(it, capturedGeneration, stdlibRoot) }
        synchronized(lock) {
            if (generation != capturedGeneration) throw StaleGeneration
            if (closure != null && !closureIsCurrent(closure)) throw StaleGeneration
            cachedPreludeFoundation?.takeIf {
                it.generation == capturedGeneration && it.closure === closure
            }?.let {
                preludeClosureDirty = false
                return it
            }
            return PreludeFoundation(
                capturedGeneration,
                closure,
                closure?.files ?: emptySet(),
            ).also {
                cachedPreludeFoundation = it
                preludeClosureDirty = false
            }
        }
    }

    private fun preludeRoot(capturedGeneration: Long, stdlibRoot: VirtualFile?): VirtualFile? {
        var owner = false
        val build = synchronized(lock) {
            if (generation != capturedGeneration) throw StaleGeneration
            preludeBuild?.takeIf { it.generation == capturedGeneration } ?: PreludeBuild(
                capturedGeneration,
                CompletableFuture(),
            ).also {
                preludeBuild = it
                owner = true
            }
        }
        if (owner) {
            try {
                val prelude = pathResolver.resolvePrelude(stdlibRoot)?.let(::physicalOriginalFile)
                synchronized(lock) {
                    if (generation != capturedGeneration) throw StaleGeneration
                    if (prelude == null && preludeBuild === build) preludeBuild = null
                }
                build.result.complete(prelude)
            } catch (error: Throwable) {
                synchronized(lock) {
                    if (preludeBuild === build) preludeBuild = null
                }
                build.result.completeExceptionally(error)
                throw error
            }
        }
        return try {
            build.result.join()
        } catch (error: java.util.concurrent.CancellationException) {
            throw error.cause ?: error
        } catch (error: java.util.concurrent.CompletionException) {
            throw error.cause ?: error
        }
    }

    private fun closure(root: VirtualFile, capturedGeneration: Long, stdlibRoot: VirtualFile?): Closure? {
        if (!isUsableCrystalFile(root)) {
            discardInvalidNode(root, capturedGeneration)
            return null
        }
        synchronized(lock) {
            if (generation != capturedGeneration) throw StaleGeneration
            if (validationMode == ValidationMode.EVENT_DRIVEN && root !in dirtyClosureRoots) {
                closures[root]?.let { return it }
            }
        }
        val files = linkedSetOf<VirtualFile>()
        val dependencyVersions = linkedMapOf<VirtualFile, Long>()
        val pending = ArrayDeque<VirtualFile>()
        pending.add(root)
        while (pending.isNotEmpty()) {
            ProgressManager.checkCanceled()
            val file = pending.removeFirst()
            if (!files.add(file)) continue
            if (trackClosureVisits) closureVisits.incrementAndGet()
            val node = node(file, capturedGeneration, stdlibRoot)
            if (node == null) {
                files.remove(file)
                continue
            }
            dependencyVersions[file] = node.version
            node.outgoing.asReversed().forEach(pending::addFirst)
        }
        if (!dependencyVersions.containsKey(root)) return null

        synchronized(lock) {
            if (generation != capturedGeneration) throw StaleGeneration
            if (!versionsAreCurrent(dependencyVersions)) throw StaleGeneration
            closures[root]?.takeIf { it.dependencyVersions == dependencyVersions }?.let {
                dirtyClosureRoots.remove(root)
                return it
            }
            return Closure(
                immutableSet(files),
                immutableMap(dependencyVersions),
            ).also {
                replaceClosureOwnership(root, it)
                closures[root] = it
                dirtyClosureRoots.remove(root)
                closureBuilds++
            }
        }
    }

    private fun node(file: VirtualFile, capturedGeneration: Long, stdlibRoot: VirtualFile?): Node? {
        ProgressManager.checkCanceled()
        if (!isUsableCrystalFile(file)) {
            discardInvalidNode(file, capturedGeneration)
            return null
        }
        return ReadAction.computeBlocking<Node?, RuntimeException> {
            ProgressManager.checkCanceled()
            synchronized(lock) {
                if (generation != capturedGeneration) throw StaleGeneration
                val cached = nodes[file]
                if (
                    cached != null &&
                    file !in dirtyContentNodes &&
                    validationMode == ValidationMode.EVENT_DRIVEN
                ) {
                    return@computeBlocking cached
                }
            }
            val psiFile = PsiManager.getInstance(project).findFile(file)
            if (psiFile == null || !psiFile.isValid || !psiFile.isPhysical || !isUsableCrystalFile(file)) {
                discardInvalidNode(file, capturedGeneration)
                return@computeBlocking null
            }
            val directRequires = collector(psiFile)
            synchronized(lock) {
                if (generation != capturedGeneration) throw StaleGeneration
                nodes[file]?.takeIf { it.fingerprint == directRequires.fingerprint }?.let {
                    dirtyContentNodes.remove(file)
                    return@computeBlocking it
                }
            }

            val resolutions = directRequires.paths.map { pathResolver.resolve(file, it, stdlibRoot) }
            val outgoing = resolutions.asSequence()
                .flatMap { it.files.asSequence() }
                .mapNotNull(::physicalOriginalFileInReadAction)
                .distinct()
                .toList()
            val exactCandidatePaths = resolutions.asSequence()
                .flatMap { it.exactCandidatePaths.asSequence() }
                .toSet()
            val wildcardWatches = resolutions.asSequence()
                .flatMap { it.wildcardWatches.asSequence() }
                .map { NodeWildcardWatch(it.targetPath, it.mode) }
                .toSet()

            synchronized(lock) {
                if (generation != capturedGeneration) throw StaleGeneration
                nodes[file]?.takeIf { it.fingerprint == directRequires.fingerprint }?.let {
                    dirtyContentNodes.remove(file)
                    return@computeBlocking it
                }
                dirtyContentNodes.remove(file)
                publishNode(
                    file,
                    directRequires.fingerprint,
                    outgoing,
                    exactCandidatePaths,
                    wildcardWatches,
                )
            }
        }
    }

    private fun publishNode(
        file: VirtualFile,
        fingerprint: String,
        outgoing: List<VirtualFile>,
        exactCandidatePaths: Set<String>,
        wildcardWatches: Set<NodeWildcardWatch>,
    ): Node {
        nodes[file]?.outgoing?.forEach { dependency -> reverseEdges[dependency]?.remove(file) }
        val node = Node(
            fingerprint,
            Collections.unmodifiableList(outgoing.toList()),
            immutableSet(exactCandidatePaths),
            immutableSet(wildcardWatches),
            ++nextNodeVersion,
        )
        nodes[file] = node
        outgoing.forEach { dependency -> reverseEdges.getOrPut(dependency, ::linkedSetOf).add(file) }
        invalidateDependentClosures(file)
        nodeBuilds++
        return node
    }

    private fun discardInvalidNode(file: VirtualFile, capturedGeneration: Long) {
        synchronized(lock) {
            if (generation != capturedGeneration) throw StaleGeneration
            removeNode(file)
        }
    }

    private fun removeNode(file: VirtualFile) {
        dirtyContentNodes.remove(file)
        val removed = nodes.remove(file) ?: return
        removed.outgoing.forEach { dependency -> reverseEdges[dependency]?.remove(file) }
        invalidateDependentClosures(file)
        targetedInvalidations++
    }

    private fun invalidateDependentClosures(file: VirtualFile) {
        val pending = ArrayDeque<VirtualFile>()
        val affected = linkedSetOf<VirtualFile>()
        pending.add(file)
        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            if (!affected.add(current)) continue
            reverseEdges[current].orEmpty().forEach(pending::addLast)
        }
        val foundationInvalidated = cachedPreludeFoundation?.closure?.dependencyVersions?.keys
            ?.any(affected::contains) == true
        if (foundationInvalidated) {
            cachedPreludeFoundation = null
            preludeClosureDirty = false
            effectiveSnapshots.clear()
        }
        affected.forEach {
            removeClosure(it)
            effectiveSnapshots.remove(it)
        }
    }

    private fun replaceClosureOwnership(root: VirtualFile, closure: Closure) {
        closures[root]?.dependencyVersions?.keys?.forEach { dependency ->
            closureOwners[dependency]?.let { owners ->
                owners.remove(root)
                if (owners.isEmpty()) closureOwners.remove(dependency)
            }
        }
        closure.dependencyVersions.keys.forEach { dependency ->
            closureOwners.getOrPut(dependency, ::linkedSetOf).add(root)
        }
    }

    private fun removeClosure(root: VirtualFile) {
        dirtyClosureRoots.remove(root)
        closures.remove(root)?.dependencyVersions?.keys?.forEach { dependency ->
            closureOwners[dependency]?.let { owners ->
                owners.remove(root)
                if (owners.isEmpty()) closureOwners.remove(dependency)
            }
        }
    }

    private fun versionsAreCurrent(dependencyVersions: Map<VirtualFile, Long>): Boolean =
        dependencyVersions.all { (file, version) -> nodes[file]?.version == version }

    private fun closureIsCurrent(closure: Closure): Boolean = versionsAreCurrent(closure.dependencyVersions)

    private fun physicalOriginalFileInReadAction(context: PsiElement): VirtualFile? {
        val file: PsiFile = context.containingFile?.originalFile ?: return null
        if (!file.isPhysical) return null
        return file.virtualFile?.takeIf(::isUsableCrystalFile)
    }

    private fun physicalOriginalFile(file: VirtualFile): VirtualFile? =
        ReadAction.computeBlocking<VirtualFile?, RuntimeException> {
            physicalOriginalFileInReadAction(file)
        }

    private fun physicalOriginalFileInReadAction(file: VirtualFile): VirtualFile? {
        val psiFile = PsiManager.getInstance(project).findFile(file) ?: return null
        if (!psiFile.isValid || !psiFile.isPhysical) return null
        return psiFile.originalFile.virtualFile?.takeIf(::isUsableCrystalFile)
    }

    private fun isUsableCrystalFile(file: VirtualFile): Boolean =
        file.isValid && !file.isDirectory && file.extension == "cr"

    private fun <T> immutableSet(values: Collection<T>): Set<T> =
        Collections.unmodifiableSet(LinkedHashSet(values))

    private fun <K, V> immutableMap(values: Map<K, V>): Map<K, V> =
        Collections.unmodifiableMap(LinkedHashMap(values))

    companion object {
        private val EMPTY_SOURCES = CrystalEffectiveSourceSet(emptySet())

        fun getInstance(project: Project): CrystalRequireGraphService = project.service()
    }
}
