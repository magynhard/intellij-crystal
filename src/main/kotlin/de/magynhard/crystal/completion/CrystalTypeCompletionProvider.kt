package de.magynhard.crystal.completion

import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.analysis.CrystalEffectiveSourceSet
import de.magynhard.crystal.analysis.CrystalRequireGraphService
import de.magynhard.crystal.analysis.CrystalRequireVisibility
import de.magynhard.crystal.psi.CrystalClassDefinition
import de.magynhard.crystal.psi.CrystalEnumDefinition
import de.magynhard.crystal.psi.CrystalModuleDefinition
import de.magynhard.crystal.psi.CrystalStructDefinition
import de.magynhard.crystal.stubs.CrystalIndexService

/**
 * Provides type completions for type annotation contexts (after `:` in parameters and return types).
 * Includes all Crystal stdlib types + project types from StubIndex + `self` when inside a class/struct.
 */
object CrystalTypeCompletionProvider {

    /**
     * Stdlib types available through `prelude.cr` without any explicit require,
     * verified against the Crystal distribution's `prelude.cr` (plus
     * `concurrent.cr`, which pulls in `fiber`/`channel`). Always offered as a
     * reliable baseline, even when no SDK is configured.
     */
    private val CORE_STDLIB_TYPES = listOf(
        // Primitive / Numeric
        "Int8", "Int16", "Int32", "Int64", "Int128",
        "UInt8", "UInt16", "UInt32", "UInt64", "UInt128",
        "Float32", "Float64",
        "Bool", "Char", "String", "Symbol", "Nil", "NoReturn", "Void",

        // Abstract numeric / comparable
        "Number", "Int", "Float", "Comparable",

        // Collections
        "Array", "Hash", "Set", "Tuple", "NamedTuple",
        "Deque", "BitArray", "StaticArray", "Slice", "Range",
        "Indexable", "Iterable", "Iterator", "Enumerable", "Steppable",

        // IO / Filesystem
        "IO", "File", "Dir", "Path", "Bytes",

        // Concurrency
        "Channel", "Fiber", "Mutex",

        // String / Regex
        "Regex",

        // Time
        "Time",

        // Math
        "Math",

        // Exceptions
        "Exception", "ArgumentError", "IndexError", "KeyError",
        "RuntimeError", "OverflowError", "DivisionByZeroError",
        "TypeCastError", "NilAssertionError", "NotImplementedError",
        "InvalidByteSequenceError", "SystemError",

        // OOP base types
        "Object", "Value", "Reference", "Struct", "Enum", "Class",

        // Proc / Pointer / Memory
        "Proc", "Pointer", "Box",

        // Random
        "Random",

        // Process / System
        "Process", "Signal", "System", "ENV",

        // Encoding
        "Base64",

        // Misc prelude
        "Atomic", "GC", "VaList", "Unicode", "PrettyPrint",
    )

    /**
     * Well-known stdlib types that need an explicit `require` (e.g.
     * `require "json"`). Offered only when the stub index cannot judge them
     * (no indexed declaration, e.g. without a configured SDK) or when at
     * least one indexed declaration is visible through the current file's
     * require closure — never when the index knows them but none is required.
     */
    private val OPTIONAL_STDLIB_TYPES = listOf(
        // Concurrency helper
        "WaitGroup",

        // Memory
        "WeakRef",

        // Numbers
        "Complex", "BigInt", "BigFloat", "BigDecimal", "BigRational",

        // String helpers
        "StringPool", "StringScanner", "URI", "UUID",

        // Filesystem helper
        "FileUtils",

        // Network / HTTP
        "Socket", "TCPSocket", "TCPServer", "UDPSocket",
        "UNIXSocket", "UNIXServer", "IPSocket",
        "HTTP", "Termios",

        // Logging
        "Log",

        // Serialization / Data formats
        "JSON", "YAML", "CSV", "XML", "INI",

        // Compression / Crypto / Security
        "Compress", "Crypto", "OpenSSL", "Digest",

        // Encoding / Web
        "HTML", "MIME",

        // Auth
        "OAuth", "OAuth2",

        // Misc require-gated
        "Colorize", "SemanticVersion",
        "OptionParser", "Benchmark", "Spec",
        "ECR", "Levenshtein",
    )

    private val KNOWN_STDLIB_TYPES = (CORE_STDLIB_TYPES + OPTIONAL_STDLIB_TYPES).toSet()

    /**
     * Returns LookupElements for prelude (core) types plus require-gated
     * (optional) stdlib types visible from [context]. Injected fragments
     * without their own require closure (ECR) keep the legacy unfiltered list.
     */
    fun getStdlibTypeLookups(context: PsiElement, prefixMatcher: PrefixMatcher): List<LookupElementBuilder> {
        val result = mutableListOf<LookupElementBuilder>()
        for (typeName in CORE_STDLIB_TYPES) {
            if (prefixMatcher.prefixMatches(typeName)) {
                result.add(stdlibLookup(typeName))
            }
        }

        val gate = visibilityGate(context) ?: return result + OPTIONAL_STDLIB_TYPES
            .filter(prefixMatcher::prefixMatches)
            .map(::stdlibLookup)

        for (typeName in OPTIONAL_STDLIB_TYPES) {
            if (!prefixMatcher.prefixMatches(typeName)) continue
            if (isOptionalTypeOffered(typeName, context.project, gate)) {
                result.add(stdlibLookup(typeName))
            }
        }
        return result
    }

    /**
     * Returns LookupElements for all type completions in the given context:
     * prelude types as baseline, require-gated stdlib and indexed project /
     * shard types only when visible through the current file's require closure.
     */
    fun getTypeLookups(
        position: PsiElement,
        project: Project,
        prefixMatcher: PrefixMatcher,
    ): List<LookupElementBuilder> {
        val result = mutableListOf<LookupElementBuilder>()
        for (typeName in CORE_STDLIB_TYPES) {
            if (prefixMatcher.prefixMatches(typeName)) {
                result.add(stdlibLookup(typeName))
            }
        }

        val gate = visibilityGate(position)

        for (typeName in OPTIONAL_STDLIB_TYPES) {
            if (!prefixMatcher.prefixMatches(typeName)) continue
            if (gate == null || isOptionalTypeOffered(typeName, project, gate)) {
                result.add(stdlibLookup(typeName))
            }
        }

        // Additional types from StubIndex (includes stdlib when CrystalStdlibLibraryProvider is active).
        val scope = GlobalSearchScope.allScope(project)
        val allTypes = CrystalIndexService.getAllTypeNames(project)
        for (typeName in allTypes) {
            if (typeName in KNOWN_STDLIB_TYPES) continue
            if (!prefixMatcher.prefixMatches(typeName)) continue
            if (gate != null &&
                !CrystalRequireVisibility.isTypeNameVisible(typeName, project, scope, gate.sources)
            ) {
                continue
            }
            result.add(
                LookupElementBuilder.create(typeName)
                    .withIcon(AllIcons.Nodes.Class)
                    .withTypeText("project", true)
            )
        }

        // `self` if inside a class or struct
        if (isInsideClassOrStruct(position)) {
            result.add(
                LookupElementBuilder.create("self")
                    .withIcon(AllIcons.Nodes.Type)
                    .withTypeText("current type", true)
            )
        }

        return result
    }

    private fun stdlibLookup(typeName: String): LookupElementBuilder =
        LookupElementBuilder.create(typeName)
            .withIcon(AllIcons.Nodes.Class)
            .withTypeText("stdlib", true)

    /**
     * The shared visibility gate for index-backed candidates, or `null` when
     * no program can be established (injected fragments, unresolvable
     * context): callers then keep the legacy unfiltered behavior instead of
     * emptying the popup.
     */
    private data class VisibilityGate(
        val scope: GlobalSearchScope,
        val sources: CrystalEffectiveSourceSet,
    )

    private fun visibilityGate(context: PsiElement): VisibilityGate? {
        val project = context.project
        val service = CrystalRequireGraphService.getInstance(project)
        if (service.isProgramLessInjection(context)) return null
        val sources = service.effectiveSources(context).takeIf { it.files.isNotEmpty() } ?: return null
        return VisibilityGate(GlobalSearchScope.allScope(project), sources)
    }

    /**
     * True when a require-gated stdlib [typeName] may be offered: always when
     * the index knows no declaration (e.g. without a configured SDK), and
     * otherwise only when a declaration is visible through the require closure.
     */
    private fun isOptionalTypeOffered(typeName: String, project: Project, gate: VisibilityGate): Boolean =
        !CrystalRequireVisibility.hasIndexedType(typeName, project, gate.scope) ||
            CrystalRequireVisibility.isTypeNameVisible(typeName, project, gate.scope, gate.sources)

    private fun isInsideClassOrStruct(position: PsiElement): Boolean {
        return PsiTreeUtil.getParentOfType(position, CrystalClassDefinition::class.java) != null ||
            PsiTreeUtil.getParentOfType(position, CrystalStructDefinition::class.java) != null
    }

    /**
     * Returns LookupElements for types nested inside the given enclosing type name.
     * Used for `Foo::<caret>` completion — shows only types defined inside `Foo`.
     *
     * Uses the nested-type index for O(1) lookup.
     */
    fun getEnclosingTypeLookups(enclosingName: String, project: Project, context: PsiElement): List<LookupElementBuilder> {
        val scope = GlobalSearchScope.allScope(project)
        val nestedTypes = CrystalIndexService.findNestedTypes(enclosingName, project, scope)
            .filter { CrystalRequireVisibility.isVisible(it, context) }
        return nestedTypes.mapNotNull { element ->
            val name = element.name ?: return@mapNotNull null
            val kind = when (element) {
                is CrystalClassDefinition -> "class"
                is CrystalModuleDefinition -> "module"
                is CrystalStructDefinition -> "struct"
                is CrystalEnumDefinition -> "enum"
                else -> "type"
            }
            LookupElementBuilder.create(name)
                .withIcon(AllIcons.Nodes.Class)
                .withTypeText("$kind in $enclosingName", true)
        }
    }
}
