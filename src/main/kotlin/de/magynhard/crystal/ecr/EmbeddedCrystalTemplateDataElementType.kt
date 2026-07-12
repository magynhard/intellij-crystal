package de.magynhard.crystal.ecr

import com.intellij.psi.templateLanguages.TemplateDataElementType
import com.intellij.psi.tree.OuterLanguageElementType

object EmbeddedCrystalTemplateDataElementType :
    TemplateDataElementType(
        "ECR_TEMPLATE_DATA",
        EmbeddedCrystalLanguage,
        EmbeddedCrystalTypes.ECR_OUTER,
        OuterLanguageElementType("ECR_OUTER", EmbeddedCrystalLanguage)
    )
