package de.magynhard.crystal.injection

import com.intellij.openapi.util.TextRange
import com.intellij.psi.AbstractElementManipulator
import com.intellij.util.IncorrectOperationException
import de.magynhard.crystal.psi.CrystalSymbolStringExpression
import de.magynhard.crystal.psi.impl.CrystalSymbolStringExpressionMixin

/**
 * Element manipulator for [CrystalSymbolStringExpression] injection hosts.
 *
 * The manipulable range is the injectable content span after the colon;
 * content changes replace the content through the host mixin's
 * [CrystalSymbolStringExpressionMixin.replaceContentWith].
 */
class CrystalSymbolStringExpressionManipulator : AbstractElementManipulator<CrystalSymbolStringExpression>() {

    override fun handleContentChange(
        element: CrystalSymbolStringExpression,
        range: TextRange,
        newContent: String
    ): CrystalSymbolStringExpression {
        val contentRange = CrystalSymbolStringExpressionMixin.contentRange(element)
        val shifted = range.shiftLeft(element.textRange.startOffset)
        if (!contentRange.contains(shifted)) {
            throw IncorrectOperationException("Cannot change content outside the symbol: $range in $element")
        }
        val body = contentRange.substring(element.text)
        val newBody = body.substring(0, shifted.startOffset - contentRange.startOffset) +
            newContent +
            body.substring(shifted.endOffset - contentRange.startOffset)
        return (element as CrystalSymbolStringExpressionMixin).replaceContentWith(newBody)
    }

    override fun getRangeInElement(element: CrystalSymbolStringExpression): TextRange =
        CrystalSymbolStringExpressionMixin.contentRange(element)
}
