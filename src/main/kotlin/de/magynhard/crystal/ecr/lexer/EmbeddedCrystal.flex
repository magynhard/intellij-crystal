package de.magynhard.crystal.ecr.lexer;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import de.magynhard.crystal.ecr.EmbeddedCrystalTypes;

%%

%class EmbeddedCrystalLexer
%implements FlexLexer
%unicode
%function advance
%type IElementType
%eof{ return;
%eof}

%state IN_TAG

%%
<YYINITIAL> {
  "<%" / ("=" | "-" | "#" | "%")? { yybegin(IN_TAG); return EmbeddedCrystalTypes.ECR_TAG_BEGIN; }
  "<%"                       { yybegin(IN_TAG); return EmbeddedCrystalTypes.ECR_TAG_BEGIN; }
  [^<]+ | "<"               { return EmbeddedCrystalTypes.ECR_OUTER; }
}

<IN_TAG> {
  "%>"                      { yybegin(YYINITIAL); return EmbeddedCrystalTypes.ECR_TAG_END; }
  ([^%]|"%"[^>])+           { return EmbeddedCrystalTypes.ECR_RAW; }
}
