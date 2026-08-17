@file:Suppress("DEPRECATION")

package de.magynhard.crystal.analysis

import com.intellij.ProjectTopics
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.fileEditor.FileDocumentManager
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
import de.magynhard.crystal.ecr.psi.CrystalEcrEcrBody
import de.magynhard.crystal.ecr.EmbeddedCrystalFileType
import de.magynhard.crystal.psi.CrystalMacroControl
import de.magynhard.crystal.psi.CrystalRequireStatement
import de.magynhard.crystal.sdk.CrystalStdlibResolver
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicLong

internal data class CrystalEffectiveSourceSet(val files: Set<VirtualFile>) {
    fun contains(element: PsiElement): Boolean {
        if (!element.isValid) return false
        val file = element.containingFile?.originalFile ?: return false
        if (!file.isValid || !file.isPhysical) return false
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
    private val preludeResolver: (VirtualFile?) -> VirtualFile?,
    private val onPreludeWait: () -> Unit,
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
        { root -> root?.findChild("prelude.cr") },
        {},
    )

    internal constructor(project: Project, pathResolver: CrystalRequirePathResolver) :
        this(
            project,
            pathResolver,
            false,
            ValidationMode.ALWAYS,
            CrystalRequireCollector::collect,
            false,
            pathResolver::resolvePrelude,
            {},
        )

    internal constructor(
        project: Project,
        pathResolver: CrystalRequirePathResolver,
        validationMode: ValidationMode,
        collector: (PsiFile) -> CrystalDirectRequires = CrystalRequireCollector::collect,
        trackClosureVisits: Boolean = false,
        preludeResolver: (VirtualFile?) -> VirtualFile? = pathResolver::resolvePrelude,
        onPreludeWait: () -> Unit = {},
    ) : this(
        project,
        pathResolver,
        false,
        validationMode,
        collector,
        trackClosureVisits,
        preludeResolver,
        onPreludeWait,
    )

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
        pathResolver::resolvePrelude,
        {},
    ) {
        registerListeners(listenerDisposable)
    }

    private data class Node(
        val fingerprint: String,
        val outgoing: List<NodeKey>,
        val exactCandidatePaths: Set<String>,
        val wildcardWatches: Set<NodeWildcardWatch>,
        val version: Long,
    )

    private enum class TraversalProvenance {
        PROJECT,
        STDLIB_FOUNDATION,
    }

    private data class NodeKey(
        val file: VirtualFile,
        val provenance: TraversalProvenance,
    )

    private data class ClosureKey(
        val root: VirtualFile,
        val provenance: TraversalProvenance,
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
        val dependencyVersions: Map<NodeKey, Long>,
    )

    private data class PreludeFoundation(
        val generation: Long,
        val closure: Closure?,
        val files: Set<VirtualFile>,
        val sources: CrystalEffectiveSourceSet,
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

    private class TestOverride(
        val service: CrystalRequireGraphService,
        val previous: TestOverride?,
        var active: Boolean = true,
    )

    private val lock = Any()
    private val nodes = mutableMapOf<NodeKey, Node>()
    private val reverseEdges = mutableMapOf<NodeKey, MutableSet<NodeKey>>()
    private val closures = mutableMapOf<ClosureKey, Closure>()
    private val closureOwners = mutableMapOf<NodeKey, MutableSet<ClosureKey>>()
    private val effectiveSnapshots = mutableMapOf<VirtualFile, EffectiveSnapshot>()
    private val dirtyContentNodes = mutableSetOf<NodeKey>()
    private val dirtyClosureReasons = mutableMapOf<ClosureKey, MutableSet<NodeKey>>()
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
            if (!context.isValid) return@computeBlocking EMPTY_SOURCES
            val containingFile = context.containingFile ?: return@computeBlocking EMPTY_SOURCES
            if (isValidEcrInjection(containingFile)) return@computeBlocking preludeSources()
            val file = containingFile.originalFile
            if (!file.isPhysical) {
                return@computeBlocking EMPTY_SOURCES
            }
            if (file.virtualFile?.extension != "cr") return@computeBlocking EMPTY_SOURCES
            val root = file.virtualFile?.takeIf(::isUsableCrystalFile) ?: return@computeBlocking EMPTY_SOURCES
            effectiveSources(root)
        }
    }

    private fun preludeSources(): CrystalEffectiveSourceSet {
        while (true) {
            ProgressManager.checkCanceled()
            val stdlibRoot = pathResolver.currentStdlibRoot()
            val capturedGeneration = captureGeneration(stdlibRoot)
            try {
                return preludeFoundation(capturedGeneration, stdlibRoot).sources
            } catch (_: StaleGeneration) {
                // A full invalidation raced this read; retry against the new generation.
            }
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
                val closure = closure(
                    root,
                    TraversalProvenance.PROJECT,
                    capturedGeneration,
                    stdlibRoot,
                ) ?: return EMPTY_SOURCES
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
        dirtyClosureReasons.clear()
        preludeClosureDirty = false
        preludeBuild = null
        cachedPreludeFoundation = null
        fullInvalidations++
    }

    internal fun invalidateRequireFile(file: VirtualFile) {
        if (synchronized(lock) { nodes.keys.none { it.file == file } }) return
        if (!file.isValid) {
            synchronized(lock) { nodeKeys(file).forEach(::removeNode) }
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
            val keys = nodeKeys(normalizedFile)
            if (keys.isEmpty()) return
            if (current != null && keys.all { nodes[it]?.fingerprint == current.second }) return
            keys.forEach(::removeNode)
        }
    }

    internal fun cacheStats(): CrystalRequireCacheStats = synchronized(lock) {
        CrystalRequireCacheStats(nodeBuilds, closureBuilds, fullInvalidations, targetedInvalidations)
    }

    internal fun cacheIdentity(file: VirtualFile): CrystalRequireCacheIdentity = synchronized(lock) {
        CrystalRequireCacheIdentity(
            nodes[NodeKey(file, TraversalProvenance.PROJECT)],
            closures[ClosureKey(file, TraversalProvenance.PROJECT)],
            effectiveSnapshots[file]?.sources,
        )
    }

    internal fun closureNodeVisits(): Long = closureVisits.get()

    private fun cleanEffectiveSnapshot(root: VirtualFile, capturedGeneration: Long): CrystalEffectiveSourceSet? =
        synchronized(lock) {
            if (validationMode != ValidationMode.EVENT_DRIVEN || generation != capturedGeneration) return@synchronized null
            if (ClosureKey(root, TraversalProvenance.PROJECT) in dirtyClosureReasons || preludeClosureDirty) {
                return@synchronized null
            }
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
        val macroControlChange = sequenceOf(event.parent, event.oldParent, event.newParent).any {
            it is CrystalMacroControl || PsiTreeUtil.getParentOfType(it, CrystalMacroControl::class.java, false) != null
        }
        if (
            !fileLevelChange && !macroControlChange &&
            sequenceOf(event.child, event.oldChild, event.newChild, event.element).none(::affectsRequire)
        ) return
        invalidateRequireFile(virtualFile)
    }

    private fun affectsRequire(element: PsiElement?): Boolean {
        if (element == null) return false
        return element is CrystalRequireStatement ||
            element.takeIf(PsiElement::isValid)?.let {
                PsiTreeUtil.getParentOfType(it, CrystalRequireStatement::class.java, false) != null ||
                    PsiTreeUtil.findChildOfType(it, CrystalRequireStatement::class.java) != null
            } == true
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
                val affected = linkedSetOf<NodeKey>()
                nodes.forEach { (key, node) ->
                    if (
                        node.exactCandidatePaths.any { candidate ->
                            structuralChanges.any { change -> change.affectsExactCandidate(candidate) }
                        } || node.wildcardWatches.any { watch ->
                            structuralChanges.any { change -> change.affectsWildcard(watch) }
                        }
                    ) {
                        affected += key
                    }
                }
                structuralChanges.mapNotNull(StructuralVfsChange::file).forEach { changedFile ->
                    nodeKeys(changedFile).forEach { key ->
                        affected += key
                        affected += reverseEdges[key].orEmpty()
                    }
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
            val keys = nodeKeys(file)
            if (keys.isEmpty()) return
            dirtyContentNodes += keys
            keys.forEach { key ->
                closureOwners[key].orEmpty().forEach { owner ->
                    dirtyClosureReasons.getOrPut(owner, ::linkedSetOf).add(key)
                }
            }
            if (cachedPreludeFoundation?.closure?.dependencyVersions?.keys?.any { it.file == file } == true) {
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
        val closure = root?.let {
            closure(
                it,
                TraversalProvenance.STDLIB_FOUNDATION,
                capturedGeneration,
                stdlibRoot,
            )
        }
        synchronized(lock) {
            if (generation != capturedGeneration) throw StaleGeneration
            if (closure != null && !closureIsCurrent(closure)) throw StaleGeneration
            cachedPreludeFoundation?.takeIf {
                it.generation == capturedGeneration && it.closure === closure
            }?.let {
                preludeClosureDirty = false
                return it
            }
            val files = closure?.files ?: emptySet()
            return PreludeFoundation(
                capturedGeneration,
                closure,
                files,
                CrystalEffectiveSourceSet(files),
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
                val prelude = preludeResolver(stdlibRoot)?.let(::physicalOriginalFile)
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
        if (!owner) onPreludeWait()
        return try {
            build.result.join()
        } catch (error: java.util.concurrent.CancellationException) {
            throw error.cause ?: error
        } catch (error: java.util.concurrent.CompletionException) {
            throw error.cause ?: error
        }
    }

    private fun closure(
        root: VirtualFile,
        provenance: TraversalProvenance,
        capturedGeneration: Long,
        stdlibRoot: VirtualFile?,
    ): Closure? {
        val closureKey = ClosureKey(root, provenance)
        if (!isUsableCrystalFile(root)) {
            discardInvalidNode(NodeKey(root, provenance), capturedGeneration)
            return null
        }
        synchronized(lock) {
            if (generation != capturedGeneration) throw StaleGeneration
            if (validationMode == ValidationMode.EVENT_DRIVEN && closureKey !in dirtyClosureReasons) {
                closures[closureKey]?.let { return it }
            }
        }
        val files = linkedSetOf<VirtualFile>()
        val dependencyVersions = linkedMapOf<NodeKey, Long>()
        val pending = ArrayDeque<NodeKey>()
        pending.add(NodeKey(root, provenance))
        while (pending.isNotEmpty()) {
            ProgressManager.checkCanceled()
            val key = pending.removeFirst()
            if (!files.add(key.file)) continue
            if (trackClosureVisits) closureVisits.incrementAndGet()
            val node = node(key, capturedGeneration, stdlibRoot)
            if (node == null) {
                files.remove(key.file)
                continue
            }
            dependencyVersions[key] = node.version
            node.outgoing.asReversed().forEach(pending::addFirst)
        }
        if (!dependencyVersions.containsKey(NodeKey(root, provenance))) return null

        synchronized(lock) {
            if (generation != capturedGeneration) throw StaleGeneration
            if (!versionsAreCurrent(dependencyVersions)) throw StaleGeneration
            closures[closureKey]?.takeIf { it.dependencyVersions == dependencyVersions }?.let {
                dirtyClosureReasons.remove(closureKey)
                return it
            }
            return Closure(
                immutableSet(files),
                immutableMap(dependencyVersions),
            ).also {
                replaceClosureOwnership(closureKey, it)
                closures[closureKey] = it
                dirtyClosureReasons.remove(closureKey)
                closureBuilds++
            }
        }
    }

    private fun node(key: NodeKey, capturedGeneration: Long, stdlibRoot: VirtualFile?): Node? {
        val file = key.file
        ProgressManager.checkCanceled()
        if (!isUsableCrystalFile(file)) {
            discardInvalidNode(key, capturedGeneration)
            return null
        }
        return ReadAction.computeBlocking<Node?, RuntimeException> {
            ProgressManager.checkCanceled()
            synchronized(lock) {
                if (generation != capturedGeneration) throw StaleGeneration
                val cached = nodes[key]
                if (
                    cached != null &&
                    key !in dirtyContentNodes &&
                    validationMode == ValidationMode.EVENT_DRIVEN
                ) {
                    return@computeBlocking cached
                }
            }
            val psiFile = PsiManager.getInstance(project).findFile(file)
            if (psiFile == null || !psiFile.isValid || !psiFile.isPhysical || !isUsableCrystalFile(file)) {
                discardInvalidNode(key, capturedGeneration)
                return@computeBlocking null
            }
            val directRequires = collector(psiFile)
            synchronized(lock) {
                if (generation != capturedGeneration) throw StaleGeneration
                nodes[key]?.takeIf { it.fingerprint == directRequires.fingerprint }?.let {
                    clearDirtyReason(key)
                    return@computeBlocking it
                }
            }

            val resolutions = directRequires.paths.map { requirePath ->
                if (key.provenance == TraversalProvenance.STDLIB_FOUNDATION && stdlibRoot != null) {
                    pathResolver.resolveFromStdlib(file, requirePath, stdlibRoot)
                } else {
                    pathResolver.resolve(file, requirePath, stdlibRoot)
                }
            }
            val outgoing = resolutions.asSequence()
                .flatMap { it.files.asSequence() }
                .mapNotNull(::physicalOriginalFileInReadAction)
                .distinct()
                .map { NodeKey(it, key.provenance) }
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
                nodes[key]?.takeIf { it.fingerprint == directRequires.fingerprint }?.let {
                    clearDirtyReason(key)
                    return@computeBlocking it
                }
                clearDirtyReason(key)
                publishNode(
                    key,
                    directRequires.fingerprint,
                    outgoing,
                    exactCandidatePaths,
                    wildcardWatches,
                )
            }
        }
    }

    private fun publishNode(
        key: NodeKey,
        fingerprint: String,
        outgoing: List<NodeKey>,
        exactCandidatePaths: Set<String>,
        wildcardWatches: Set<NodeWildcardWatch>,
    ): Node {
        nodes[key]?.outgoing?.forEach { dependency -> reverseEdges[dependency]?.remove(key) }
        val node = Node(
            fingerprint,
            Collections.unmodifiableList(outgoing.toList()),
            immutableSet(exactCandidatePaths),
            immutableSet(wildcardWatches),
            ++nextNodeVersion,
        )
        nodes[key] = node
        outgoing.forEach { dependency -> reverseEdges.getOrPut(dependency, ::linkedSetOf).add(key) }
        invalidateDependentClosures(key)
        nodeBuilds++
        return node
    }

    private fun discardInvalidNode(key: NodeKey, capturedGeneration: Long) {
        synchronized(lock) {
            if (generation != capturedGeneration) throw StaleGeneration
            removeNode(key)
        }
    }

    private fun removeNode(key: NodeKey) {
        clearDirtyReason(key)
        val removed = nodes.remove(key) ?: return
        removed.outgoing.forEach { dependency -> reverseEdges[dependency]?.remove(key) }
        invalidateDependentClosures(key)
        targetedInvalidations++
    }

    private fun invalidateDependentClosures(key: NodeKey) {
        val pending = ArrayDeque<NodeKey>()
        val affected = linkedSetOf<NodeKey>()
        pending.add(key)
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
            closureKeys(it.file).forEach(::removeClosure)
            effectiveSnapshots.remove(it.file)
        }
    }

    private fun replaceClosureOwnership(key: ClosureKey, closure: Closure) {
        closures[key]?.dependencyVersions?.keys?.forEach { dependency ->
            closureOwners[dependency]?.let { owners ->
                owners.remove(key)
                if (owners.isEmpty()) closureOwners.remove(dependency)
            }
        }
        closure.dependencyVersions.keys.forEach { dependency ->
            closureOwners.getOrPut(dependency, ::linkedSetOf).add(key)
        }
    }

    private fun removeClosure(key: ClosureKey) {
        dirtyClosureReasons.remove(key)
        closures.remove(key)?.dependencyVersions?.keys?.forEach { dependency ->
            closureOwners[dependency]?.let { owners ->
                owners.remove(key)
                if (owners.isEmpty()) closureOwners.remove(dependency)
            }
        }
    }

    private fun versionsAreCurrent(dependencyVersions: Map<NodeKey, Long>): Boolean =
        dependencyVersions.all { (key, version) -> nodes[key]?.version == version }

    private fun closureIsCurrent(closure: Closure): Boolean = versionsAreCurrent(closure.dependencyVersions)

    private fun nodeKeys(file: VirtualFile): List<NodeKey> = nodes.keys.filter { it.file == file }

    private fun closureKeys(file: VirtualFile): List<ClosureKey> = closures.keys.filter { it.root == file }

    private fun clearDirtyReason(key: NodeKey) {
        dirtyContentNodes.remove(key)
        closureOwners[key].orEmpty().forEach { owner ->
            dirtyClosureReasons[owner]?.let { reasons ->
                reasons.remove(key)
                if (reasons.isEmpty()) dirtyClosureReasons.remove(owner)
            }
        }
    }

    private fun isValidEcrInjection(context: PsiElement): Boolean {
        if (!context.isValid) return false
        val file = context.containingFile ?: return false
        val manager = InjectedLanguageManager.getInstance(project)
        val host = manager.getInjectionHost(context) ?: file.context
        if (host is CrystalEcrEcrBody && host.isValid) return true
        val window = file.virtualFile as? VirtualFileWindow ?: return false
        val hostFile = FileDocumentManager.getInstance().getFile(window.documentWindow.delegate) ?: return false
        return manager.isInjectedFragment(file) && hostFile.fileType == EmbeddedCrystalFileType
    }

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
        private val TEST_OVERRIDE = Key.create<TestOverride>("crystal.require.graph.test.override")
        private val TEST_OVERRIDE_LOCK = Any()

        fun getInstance(project: Project): CrystalRequireGraphService = synchronized(TEST_OVERRIDE_LOCK) {
            project.getUserData(TEST_OVERRIDE)?.service ?: project.service()
        }

        internal fun installForTests(
            project: Project,
            pathResolver: CrystalRequirePathResolver,
            parentDisposable: Disposable,
        ): CrystalRequireGraphService {
            val service = CrystalRequireGraphService(project, pathResolver, parentDisposable)
            val override = synchronized(TEST_OVERRIDE_LOCK) {
                TestOverride(service, project.getUserData(TEST_OVERRIDE)).also {
                    project.putUserData(TEST_OVERRIDE, it)
                }
            }
            Disposer.register(parentDisposable) {
                synchronized(TEST_OVERRIDE_LOCK) {
                    override.active = false
                    if (project.getUserData(TEST_OVERRIDE) === override) {
                        var previous = override.previous
                        while (previous != null && !previous.active) previous = previous.previous
                        project.putUserData(TEST_OVERRIDE, previous)
                    }
                }
            }
            return service
        }
    }
}
