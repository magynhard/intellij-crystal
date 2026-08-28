package de.magynhard.crystal.injection

import com.intellij.openapi.util.TextRange
import com.intellij.psi.AbstractElementManipulator
import com.intellij.util.IncorrectOperationException
import de.magynhard.crystal.psi.CrystalHeredocLiteral
import de.magynhard.crystal.psi.impl.CrystalHeredocLiteralMixin

/**
 * Element manipulator for [CrystalHeredocLiteral] injection hosts.
 *
 * The manipulable range is the injectable body span between the heredoc
 * delimiters; content changes replace the body through the host mixin's
 * [CrystalHeredocLiteralMixin.replaceBodyWith].
 */
class CrystalHeredocLiteralManipulator : AbstractElementManipulator<CrystalHeredocLiteral>() {

    override fun handleContentChange(
        element: CrystalHeredocLiteral,
        range: TextRange,
        newContent: String
    ): CrystalHeredocLiteral {
        val bodyRange = CrystalHeredocLiteralMixin.contentRange(element)
        val shifted = range.shiftLeft(element.textRange.startOffset)
        if (!bodyRange.contains(shifted)) {
            throw IncorrectOperationException("Cannot change content outside the heredoc body: $range in $element")
        }
        val bodyText = bodyRange.substring(element.text)
        val newText = bodyText.substring(0, shifted.startOffset - bodyRange.startOffset) +
            newContent +
            bodyText.substring(shifted.endOffset - bodyRange.startOffset)
        return (element as CrystalHeredocLiteralMixin).replaceBodyWith(newText)
    }

    override fun getRangeInElement(element: CrystalHeredocLiteral): TextRange =
        CrystalHeredocLiteralMixin.contentRange(element)
}
