package de.magynhard.crystal.injection

import com.intellij.openapi.util.TextRange
import com.intellij.psi.AbstractElementManipulator
import com.intellij.util.IncorrectOperationException
import de.magynhard.crystal.psi.CrystalStringExpression
import de.magynhard.crystal.psi.impl.CrystalStringExpressionMixin

/**
 * Element manipulator for [CrystalStringExpression] injection hosts.
 *
 * The manipulable range is the string content between the enclosing quotes;
 * content changes replace the content through the host mixin's
 * [CrystalStringExpressionMixin.replaceContentWith].
 */
class CrystalStringExpressionManipulator : AbstractElementManipulator<CrystalStringExpression>() {

    override fun handleContentChange(
        element: CrystalStringExpression,
        range: TextRange,
        newContent: String
    ): CrystalStringExpression {
        val contentRange = CrystalStringExpressionMixin.contentRange(element)
        val shifted = range.shiftLeft(element.textRange.startOffset)
        if (!contentRange.contains(shifted)) {
            throw IncorrectOperationException("Cannot change content outside the string: $range in $element")
        }
        val body = contentRange.substring(element.text)
        val newBody = body.substring(0, shifted.startOffset - contentRange.startOffset) +
            newContent +
            body.substring(shifted.endOffset - contentRange.startOffset)
        return (element as CrystalStringExpressionMixin).replaceContentWith(newBody)
    }

    override fun getRangeInElement(element: CrystalStringExpression): TextRange =
        CrystalStringExpressionMixin.contentRange(element)
}
