package de.magynhard.crystal.ecr.structure

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import de.magynhard.crystal.ecr.EmbeddedCrystalFile
import de.magynhard.crystal.ecr.psi.CrystalEcrEcrPart

data class InstanceVariableInfo(
    val name: String,
    val firstOffset: Int,
    val element: PsiElement
)

object CrystalInstanceVariableExtractor {

    fun extractAll(file: PsiFile): List<InstanceVariableInfo> {
        if (file !is EmbeddedCrystalFile) return emptyList()

        val firstOccurrences = mutableMapOf<String, Int>()

        file.children
            .filterIsInstance<CrystalEcrEcrPart>()
            .forEach { part ->
                part.ecrTag?.ecrBody?.let { body ->
                    val regex = Regex("@[a-zA-Z_][a-zA-Z0-9_]*")
                    regex.findAll(body.text).forEach { match ->
                        val varName = match.value
                        val varOffset = body.textOffset + match.range.first
                        if (varName !in firstOccurrences) {
                            firstOccurrences[varName] = varOffset
                        }
                    }
                }
            }

        return firstOccurrences
            .map { (name, offset) -> InstanceVariableInfo(name, offset, file) }
            .sortedBy { it.name }
    }
}