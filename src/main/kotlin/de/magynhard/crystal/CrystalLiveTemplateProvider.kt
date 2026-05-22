package de.magynhard.crystal

import com.intellij.codeInsight.template.impl.DefaultLiveTemplatesProvider

class CrystalLiveTemplateProvider : DefaultLiveTemplatesProvider {
    override fun getDefaultLiveTemplateFiles(): Array<String> = arrayOf("/liveTemplates/Crystal")
    override fun getHiddenLiveTemplateFiles(): Array<String>? = null
}
