package de.magynhard.crystal.ameba

import com.intellij.psi.PsiFile
import de.magynhard.crystal.CrystalFileType
import de.magynhard.crystal.ecr.EmbeddedCrystalFileType

/**
 * File shapes the Ameba pipeline accepts. `.cr` files lint directly;
 * `.ecr` templates pass through raw — Ameba 1.7.0+ translates them itself
 * (`ECR.process_string`) and reports template coordinates, so no
 * client-side extraction or position mapping is needed.
 */
internal fun isLintableAmebaFile(file: PsiFile): Boolean {
    val type = file.fileType
    return type == CrystalFileType || type == EmbeddedCrystalFileType
}
