package de.magynhard.crystal.completion

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.psi.CrystalClassDefinition
import de.magynhard.crystal.psi.CrystalStructDefinition
import de.magynhard.crystal.stubs.CrystalClassIndex
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
     * Returns LookupElements for all type completions in the given context.
     */
    fun getTypeLookups(position: PsiElement, project: Project): List<LookupElementBuilder> {
        val result = mutableListOf<LookupElementBuilder>()

        // 1. Crystal stdlib types
        for (typeName in STDLIB_TYPES) {
            result.add(
                LookupElementBuilder.create(typeName)
                    .withIcon(AllIcons.Nodes.Class)
                    .withTypeText("stdlib", true)
            )
        }

        // 2. Project types from StubIndex
        val projectTypes = StubIndex.getInstance().getAllKeys(CrystalClassIndex.KEY, project)
        for (typeName in projectTypes) {
            // Avoid duplicates with stdlib
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
}
