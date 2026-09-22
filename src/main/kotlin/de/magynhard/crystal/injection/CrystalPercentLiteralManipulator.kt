package de.magynhard.crystal.injection

import com.intellij.openapi.util.TextRange
import com.intellij.psi.AbstractElementManipulator
import com.intellij.util.IncorrectOperationException
import de.magynhard.crystal.psi.CrystalPercentLiteral
import de.magynhard.crystal.psi.impl.CrystalPercentLiteralMixin

/**
 * Element manipulator for [CrystalPercentLiteral] injection hosts.
 *
 * The manipulable range is the injectable body span between the percent
 * delimiters; content changes replace the body through the host mixin's
 * [CrystalPercentLiteralMixin.replaceBodyWith].
 */
class CrystalPercentLiteralManipulator : AbstractElementManipulator<CrystalPercentLiteral>() {

    override fun handleContentChange(
        element: CrystalPercentLiteral,
        range: TextRange,
        newContent: String
    ): CrystalPercentLiteral {
        val bodyRange = CrystalPercentLiteralMixin.contentRange(element)
        val shifted = range.shiftLeft(element.textRange.startOffset)
        if (!bodyRange.contains(shifted)) {
            throw IncorrectOperationException("Cannot change content outside the percent body: $range in $element")
        }
        val bodyText = bodyRange.substring(element.text)
        val newText = bodyText.substring(0, shifted.startOffset - bodyRange.startOffset) +
            newContent +
            bodyText.substring(shifted.endOffset - bodyRange.startOffset)
        return (element as CrystalPercentLiteralMixin).replaceBodyWith(newText)
    }

    override fun getRangeInElement(element: CrystalPercentLiteral): TextRange =
        CrystalPercentLiteralMixin.contentRange(element)
}
