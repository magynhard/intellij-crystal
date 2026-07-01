package de.magynhard.crystal.completion

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.psi.CrystalClassDefinition
import de.magynhard.crystal.psi.CrystalEnumDefinition
import de.magynhard.crystal.psi.CrystalModuleDefinition
import de.magynhard.crystal.psi.CrystalNamedElement
import de.magynhard.crystal.psi.CrystalStructDefinition
import de.magynhard.crystal.stubs.CrystalClassByEnclosingIndex
import de.magynhard.crystal.stubs.CrystalClassIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex

/**
 * Provides type completions for type annotation contexts (after `:` in parameters and return types).
 * Includes all Crystal stdlib types + project types from StubIndex + `self` when inside a class/struct.
 */
object CrystalTypeCompletionProvider {

    /**
     * All Crystal stdlib top-level types commonly used in type annotations.
     */
    private val STDLIB_TYPES = listOf(
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
        "IO", "File", "Dir", "Path", "Bytes", "FileUtils",

        // Network / HTTP
        "Socket", "TCPSocket", "TCPServer", "UDPSocket",
        "UNIXSocket", "UNIXServer", "IPSocket",
        "HTTP",

        // Concurrency
        "Channel", "Fiber", "Mutex", "WaitGroup",

        // String / Regex / URI
        "Regex", "URI", "UUID",
        "StringPool", "StringScanner",

        // Time
        "Time",

        // Serialization / Data formats
        "JSON", "YAML", "CSV", "XML", "INI",

        // Compression
        "Compress",

        // Crypto / Security
        "Crypto", "OpenSSL",

        // Digest
        "Digest",

        // Math
        "Math", "Complex", "BigInt", "BigFloat", "BigDecimal", "BigRational",

        // Exceptions
        "Exception", "ArgumentError", "IndexError", "KeyError",
        "RuntimeError", "OverflowError", "DivisionByZeroError",
        "TypeCastError", "NilAssertionError", "NotImplementedError",
        "InvalidByteSequenceError", "SystemError",

        // OOP base types
        "Object", "Value", "Reference", "Struct", "Enum", "Class",

        // Proc / Pointer / Memory
        "Proc", "Pointer", "WeakRef", "Box",

        // Random
        "Random",

        // Logging
        "Log",

        // Process / System
        "Process", "Signal", "System", "ENV",

        // Encoding / Parsing
        "Base64", "HTML", "MIME",

        // Auth
        "OAuth", "OAuth2",

        // Misc
        "Atomic", "Colorize", "SemanticVersion",
        "OptionParser", "PrettyPrint",
        "Benchmark", "Spec",
        "ECR", "Levenshtein",
        "GC", "VaList",

        // Networking high-level
        "Termios", "Unicode"
    )

    /**
     * Returns LookupElements for stdlib types only (used in free-text completion when prefix is uppercase).
     */
    fun getStdlibTypeLookups(): List<LookupElementBuilder> {
        return STDLIB_TYPES.map { typeName ->
            LookupElementBuilder.create(typeName)
                .withIcon(AllIcons.Nodes.Class)
                .withTypeText("stdlib", true)
        }
    }

    /**
     * Returns LookupElements for all type completions in the given context.
     * Always includes hardcoded STDLIB_TYPES as baseline. When Crystal stdlib is indexed,
     * additional types from the index are added.
     */
    fun getTypeLookups(position: PsiElement, project: Project): List<LookupElementBuilder> {
        val result = mutableListOf<LookupElementBuilder>()

        // 1. Hardcoded stdlib types (always available, reliable baseline)
        for (typeName in STDLIB_TYPES) {
            result.add(
                LookupElementBuilder.create(typeName)
                    .withIcon(AllIcons.Nodes.Class)
                    .withTypeText("stdlib", true)
            )
        }

        // 2. Additional types from StubIndex (includes stdlib when CrystalStdlibLibraryProvider is active)
        val allTypes = StubIndex.getInstance().getAllKeys(CrystalClassIndex.KEY, project)
        for (typeName in allTypes) {
            if (typeName !in STDLIB_TYPES) {
                result.add(
                    LookupElementBuilder.create(typeName)
                        .withIcon(AllIcons.Nodes.Class)
                        .withTypeText("project", true)
                )
            }
        }

        // 3. `self` if inside a class or struct
        if (isInsideClassOrStruct(position)) {
            result.add(
                LookupElementBuilder.create("self")
                    .withIcon(AllIcons.Nodes.Type)
                    .withTypeText("current type", true)
            )
        }

        return result
    }

    private fun isInsideClassOrStruct(position: PsiElement): Boolean {
        return PsiTreeUtil.getParentOfType(position, CrystalClassDefinition::class.java) != null ||
            PsiTreeUtil.getParentOfType(position, CrystalStructDefinition::class.java) != null
    }

    /**
     * Returns LookupElements for types nested inside the given enclosing type name.
     * Used for `Foo::<caret>` completion — shows only types defined inside `Foo`.
     *
     * Queries [CrystalClassByEnclosingIndex] for O(1) lookup of nested types.
     */
    fun getEnclosingTypeLookups(enclosingName: String, project: Project): List<LookupElementBuilder> {
        val scope = GlobalSearchScope.allScope(project)
        val nestedTypes = StubIndex.getElements(
            CrystalClassByEnclosingIndex.KEY, enclosingName, project, scope,
            CrystalNamedElement::class.java
        )
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
