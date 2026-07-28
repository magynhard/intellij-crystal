package de.magynhard.crystal.completion

import junit.framework.TestCase

class CrystalCompletionExtractionTest : TestCase() {

    fun testCompletionResponsibilitiesAreExtracted() {
        assertDeclaresMethods(
            "de.magynhard.crystal.completion.CrystalLocalCompletionProvider",
            "addLocalCompletions",
            "addTopLevelMethods",
            "addClassMethods",
            "findCompletionScope",
            "collectClassVariables",
            "prioritizedLookup"
        )
        assertDeclaresMethods(
            "de.magynhard.crystal.completion.CrystalSymbolCompletionProvider",
            "addAllClasses",
            "addFileLevelConstants",
            "addClassConstants"
        )
        assertDeclaresMethods(
            "de.magynhard.crystal.completion.CrystalCompletionContextKt",
            "computeCompletionPrefix",
            "getPreviousNonWhitespaceLeaf",
            "isAfterDefKeywordInClassBody",
            "isInTypeAnnotationContext",
            "isInClassBodyNotMethod",
            "isInAnnotationContext",
            "isDotCompletion",
            "isAfterNumericLiteral",
            "isInsideStringLiteral"
        )
        assertDeclaresMethods(
            "de.magynhard.crystal.completion.CrystalCompletionReceiverResolver",
            "resolve"
        )
        assertDeclaresMethods(
            "de.magynhard.crystal.completion.CrystalTypeObjectCompletionProvider",
            "addCompletions"
        )
    }

    private fun assertDeclaresMethods(className: String, vararg methodNames: String) {
        val declaredNames = Class.forName(className).declaredMethods.map { it.name }
        for (methodName in methodNames) {
            assertTrue(
                "$className should declare $methodName; declared methods: $declaredNames",
                declaredNames.any { it.startsWith(methodName) }
            )
        }
    }
}
