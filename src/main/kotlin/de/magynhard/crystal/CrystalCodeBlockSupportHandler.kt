package de.magynhard.crystal

import com.intellij.codeInsight.highlighting.AbstractCodeBlockSupportHandler
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import de.magynhard.crystal.psi.CrystalTypes

class CrystalCodeBlockSupportHandler : AbstractCodeBlockSupportHandler() {

    override fun getTopLevelElementTypes(): TokenSet = TokenSet.create(
        CrystalTypes.IF_STATEMENT,
        CrystalTypes.UNLESS_STATEMENT,
        CrystalTypes.WHILE_STATEMENT,
        CrystalTypes.UNTIL_STATEMENT,
        CrystalTypes.FOR_STATEMENT,
        CrystalTypes.CASE_STATEMENT,
        CrystalTypes.BEGIN_STATEMENT,
        CrystalTypes.METHOD_DEFINITION,
        CrystalTypes.CLASS_DEFINITION,
        CrystalTypes.MODULE_DEFINITION,
        CrystalTypes.STRUCT_DEFINITION,
        CrystalTypes.ENUM_DEFINITION,
        CrystalTypes.ANNOTATION_DEFINITION,
        CrystalTypes.LIB_DEFINITION,
        CrystalTypes.MACRO_DEFINITION,
        CrystalTypes.BLOCK,
    )

    override fun getKeywordElementTypes(): TokenSet = TokenSet.create(
        CrystalTypes.DEF, CrystalTypes.CLASS, CrystalTypes.MODULE,
        CrystalTypes.STRUCT, CrystalTypes.ENUM, CrystalTypes.ANNOTATION,
        CrystalTypes.LIB, CrystalTypes.MACRO, CrystalTypes.VERBATIM,
        CrystalTypes.IF, CrystalTypes.UNLESS, CrystalTypes.WHILE,
        CrystalTypes.UNTIL, CrystalTypes.FOR, CrystalTypes.CASE,
        CrystalTypes.DO, CrystalTypes.BEGIN,
        CrystalTypes.END, CrystalTypes.ELSE, CrystalTypes.ELSIF,
        CrystalTypes.WHEN, CrystalTypes.RESCUE, CrystalTypes.ENSURE,
    )

    override fun getBlockElementTypes(): TokenSet = getTopLevelElementTypes()

    override fun getDirectChildrenElementTypes(parentType: IElementType?): TokenSet = when (parentType) {
        CrystalTypes.IF_STATEMENT -> TokenSet.create(
            CrystalTypes.IF, CrystalTypes.ELSIF_CLAUSE, CrystalTypes.ELSE_CLAUSE, CrystalTypes.END,
        )
        CrystalTypes.UNLESS_STATEMENT -> TokenSet.create(
            CrystalTypes.UNLESS, CrystalTypes.ELSE_CLAUSE, CrystalTypes.END,
        )
        CrystalTypes.CASE_STATEMENT -> TokenSet.create(
            CrystalTypes.CASE, CrystalTypes.WHEN_CLAUSE, CrystalTypes.IN_CLAUSE,
            CrystalTypes.ELSE_CLAUSE, CrystalTypes.END,
        )
        CrystalTypes.BEGIN_STATEMENT -> TokenSet.create(
            CrystalTypes.BEGIN, CrystalTypes.RESCUE_CLAUSE, CrystalTypes.ELSE_CLAUSE,
            CrystalTypes.ENSURE_CLAUSE, CrystalTypes.END,
        )
        CrystalTypes.BLOCK -> TokenSet.create(
            CrystalTypes.DO, CrystalTypes.RESCUE_CLAUSE, CrystalTypes.ELSE_CLAUSE,
            CrystalTypes.ENSURE_CLAUSE, CrystalTypes.END,
        )
        CrystalTypes.METHOD_DEFINITION -> TokenSet.create(CrystalTypes.DEF, CrystalTypes.END)
        CrystalTypes.CLASS_DEFINITION -> TokenSet.create(CrystalTypes.CLASS, CrystalTypes.END)
        CrystalTypes.MODULE_DEFINITION -> TokenSet.create(CrystalTypes.MODULE, CrystalTypes.END)
        CrystalTypes.STRUCT_DEFINITION -> TokenSet.create(CrystalTypes.STRUCT, CrystalTypes.END)
        CrystalTypes.ENUM_DEFINITION -> TokenSet.create(CrystalTypes.ENUM, CrystalTypes.END)
        CrystalTypes.ANNOTATION_DEFINITION -> TokenSet.create(CrystalTypes.ANNOTATION, CrystalTypes.END)
        CrystalTypes.LIB_DEFINITION -> TokenSet.create(CrystalTypes.LIB, CrystalTypes.END)
        CrystalTypes.MACRO_DEFINITION -> TokenSet.create(CrystalTypes.MACRO, CrystalTypes.END)
        CrystalTypes.WHILE_STATEMENT -> TokenSet.create(CrystalTypes.WHILE, CrystalTypes.END)
        CrystalTypes.UNTIL_STATEMENT -> TokenSet.create(CrystalTypes.UNTIL, CrystalTypes.END)
        CrystalTypes.FOR_STATEMENT -> TokenSet.create(CrystalTypes.FOR, CrystalTypes.END)
        CrystalTypes.ELSIF_CLAUSE -> TokenSet.create(CrystalTypes.ELSIF)
        CrystalTypes.ELSE_CLAUSE -> TokenSet.create(CrystalTypes.ELSE)
        CrystalTypes.RESCUE_CLAUSE -> TokenSet.create(CrystalTypes.RESCUE)
        CrystalTypes.ENSURE_CLAUSE -> TokenSet.create(CrystalTypes.ENSURE)
        CrystalTypes.WHEN_CLAUSE -> TokenSet.create(CrystalTypes.WHEN)
        CrystalTypes.IN_CLAUSE -> TokenSet.create(CrystalTypes.IN)
        else -> TokenSet.EMPTY
    }
}
