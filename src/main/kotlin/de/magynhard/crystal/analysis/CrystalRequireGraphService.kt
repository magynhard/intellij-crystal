package de.magynhard.crystal.analysis

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import java.util.Collections
import java.util.concurrent.CompletableFuture

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
)

@Service(Service.Level.PROJECT)
internal class CrystalRequireGraphService private constructor(
    private val project: Project,
    private val pathResolver: CrystalRequirePathResolver,
    @Suppress("UNUSED_PARAMETER") registerListeners: Boolean,
) {
    constructor(project: Project) : this(project, CrystalRequirePathResolver(project), true)

    internal constructor(project: Project, pathResolver: CrystalRequirePathResolver) :
        this(project, pathResolver, false)

    private data class Node(
        val fingerprint: String,
        val outgoing: List<VirtualFile>,
        val watchedDirectories: Set<VirtualFile>,
        val version: Long,
    )

    private data class Closure(
        val files: Set<VirtualFile>,
        val dependencyVersions: Map<VirtualFile, Long>,
    )

    private data class PreludeFoundation(
        val generation: Long,
        val files: Set<VirtualFile>,
    )

    private data class PreludeBuild(
        val generation: Long,
        val result: CompletableFuture<PreludeFoundation>,
    )

    private data class EffectiveSnapshot(
        val foundation: PreludeFoundation,
        val closure: Closure,
        val sources: CrystalEffectiveSourceSet,
    )

    private data class NodeInput(
        val directRequires: CrystalDirectRequires,
        val modificationStamp: Long,
    )

    private object StaleGeneration : RuntimeException()

    private val lock = Any()
    private val nodes = mutableMapOf<VirtualFile, Node>()
    private val reverseEdges = mutableMapOf<VirtualFile, MutableSet<VirtualFile>>()
    private val closures = mutableMapOf<VirtualFile, Closure>()
    private val effectiveSnapshots = mutableMapOf<VirtualFile, EffectiveSnapshot>()
    private var preludeBuild: PreludeBuild? = null
    private var generation = 0L
    private var nextNodeVersion = 0L
    private var nodeBuilds = 0L
    private var closureBuilds = 0L
    private var fullInvalidations = 0L

    fun effectiveSources(context: PsiElement): CrystalEffectiveSourceSet {
        val root = physicalOriginalFile(context) ?: return EMPTY_SOURCES
        while (true) {
            val capturedGeneration = synchronized(lock) { generation }
            try {
                val foundation = preludeFoundation(capturedGeneration)
                val closure = closure(root, capturedGeneration) ?: return EMPTY_SOURCES
                synchronized(lock) {
                    if (generation != capturedGeneration || !closureIsCurrent(closure)) {
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
            generation++
            nodes.clear()
            reverseEdges.clear()
            closures.clear()
            effectiveSnapshots.clear()
            preludeBuild = null
            fullInvalidations++
        }
    }

    internal fun cacheStats(): CrystalRequireCacheStats = synchronized(lock) {
        CrystalRequireCacheStats(nodeBuilds, closureBuilds, fullInvalidations)
    }

    private fun preludeFoundation(capturedGeneration: Long): PreludeFoundation {
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
                val prelude = pathResolver.resolvePrelude()?.let(::physicalOriginalFile)
                val files = if (prelude == null) {
                    emptySet()
                } else {
                    closure(prelude, capturedGeneration)?.files ?: emptySet()
                }
                val foundation = PreludeFoundation(capturedGeneration, immutableSet(files))
                synchronized(lock) {
                    if (generation != capturedGeneration) throw StaleGeneration
                }
                build.result.complete(foundation)
            } catch (error: Throwable) {
                build.result.completeExceptionally(error)
                throw error
            }
        }
        return try {
            build.result.join()
        } catch (error: java.util.concurrent.CompletionException) {
            throw error.cause ?: error
        }
    }

    private fun closure(root: VirtualFile, capturedGeneration: Long): Closure? {
        if (!isUsableCrystalFile(root)) {
            discardInvalidNode(root, capturedGeneration)
            return null
        }
        val files = linkedSetOf<VirtualFile>()
        val dependencyVersions = linkedMapOf<VirtualFile, Long>()

        fun visit(file: VirtualFile) {
            if (!files.add(file)) return
            val node = node(file, capturedGeneration)
            if (node == null) {
                files.remove(file)
                return
            }
            dependencyVersions[file] = node.version
            node.outgoing.forEach(::visit)
        }

        visit(root)
        if (!dependencyVersions.containsKey(root)) return null

        synchronized(lock) {
            if (generation != capturedGeneration) throw StaleGeneration
            if (!versionsAreCurrent(dependencyVersions)) throw StaleGeneration
            closures[root]?.takeIf { it.dependencyVersions == dependencyVersions }?.let { return it }
            return Closure(
                immutableSet(files),
                immutableMap(dependencyVersions),
            ).also {
                closures[root] = it
                closureBuilds++
            }
        }
    }

    private fun node(file: VirtualFile, capturedGeneration: Long): Node? {
        if (!isUsableCrystalFile(file)) {
            discardInvalidNode(file, capturedGeneration)
            return null
        }
        while (true) {
            val input = collectNodeInput(file) ?: run {
                discardInvalidNode(file, capturedGeneration)
                return null
            }
            synchronized(lock) {
                if (generation != capturedGeneration) throw StaleGeneration
                nodes[file]?.takeIf { it.fingerprint == input.directRequires.fingerprint }?.let { return it }
            }

            val resolutions = input.directRequires.paths.map { pathResolver.resolve(file, it) }
            if (file.modificationStamp != input.modificationStamp) continue
            val outgoing = resolutions.asSequence()
                .flatMap { it.files.asSequence() }
                .mapNotNull(::physicalOriginalFile)
                .distinct()
                .toList()
            val watchedDirectories = resolutions.asSequence()
                .flatMap { it.watchedDirectories.asSequence() }
                .filter { it.isValid && it.isDirectory }
                .toSet()

            synchronized(lock) {
                if (generation != capturedGeneration) throw StaleGeneration
                nodes[file]?.takeIf { it.fingerprint == input.directRequires.fingerprint }?.let { return it }
                publishNode(file, input.directRequires.fingerprint, outgoing, watchedDirectories)
                    .let { return it }
            }
        }
    }

    private fun collectNodeInput(file: VirtualFile): NodeInput? = ReadAction.computeBlocking<NodeInput?, RuntimeException> {
        val psiFile = PsiManager.getInstance(project).findFile(file) ?: return@computeBlocking null
        if (!psiFile.isValid || !psiFile.isPhysical) return@computeBlocking null
        NodeInput(CrystalRequireCollector.collect(psiFile), file.modificationStamp)
    }

    private fun publishNode(
        file: VirtualFile,
        fingerprint: String,
        outgoing: List<VirtualFile>,
        watchedDirectories: Set<VirtualFile>,
    ): Node {
        nodes[file]?.outgoing?.forEach { dependency -> reverseEdges[dependency]?.remove(file) }
        val node = Node(
            fingerprint,
            Collections.unmodifiableList(outgoing.toList()),
            immutableSet(watchedDirectories),
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
            val removed = nodes.remove(file) ?: return
            removed.outgoing.forEach { dependency -> reverseEdges[dependency]?.remove(file) }
            invalidateDependentClosures(file)
        }
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
        affected.forEach {
            closures.remove(it)
            effectiveSnapshots.remove(it)
        }
    }

    private fun versionsAreCurrent(dependencyVersions: Map<VirtualFile, Long>): Boolean =
        dependencyVersions.all { (file, version) -> nodes[file]?.version == version }

    private fun closureIsCurrent(closure: Closure): Boolean = versionsAreCurrent(closure.dependencyVersions)

    private fun physicalOriginalFile(context: PsiElement): VirtualFile? {
        return ReadAction.computeBlocking<VirtualFile?, RuntimeException> {
            val file: PsiFile = context.containingFile?.originalFile ?: return@computeBlocking null
            if (!file.isPhysical) return@computeBlocking null
            file.virtualFile?.takeIf(::isUsableCrystalFile)
        }
    }

    private fun physicalOriginalFile(file: VirtualFile): VirtualFile? =
        ReadAction.computeBlocking<VirtualFile?, RuntimeException> {
            val psiFile = PsiManager.getInstance(project).findFile(file) ?: return@computeBlocking null
            if (!psiFile.isValid || !psiFile.isPhysical) return@computeBlocking null
            psiFile.originalFile.virtualFile?.takeIf(::isUsableCrystalFile)
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
