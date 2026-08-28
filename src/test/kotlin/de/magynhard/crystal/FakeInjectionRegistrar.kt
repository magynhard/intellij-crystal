package de.magynhard.crystal

import com.intellij.lang.Language
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiLanguageInjectionHost

/**
 * Recording [MultiHostRegistrar] for injector tests: captures the injected
 * language and the registered places (prefix, suffix, host-relative range and
 * the raw host text inside the range) without performing a real injection.
 */
class FakeInjectionRegistrar : MultiHostRegistrar {
    var started = false
    var done = false
    lateinit var language: Language
        private set
    val places = mutableListOf<Place>()

    class Place(val prefix: String, val suffix: String, val range: TextRange, val content: String)

    override fun startInjecting(language: Language): MultiHostRegistrar {
        started = true
        this.language = language
        return this
    }

    override fun addPlace(
        prefix: String?,
        suffix: String?,
        context: PsiLanguageInjectionHost,
        rangeInsideHost: TextRange
    ): MultiHostRegistrar {
        places.add(Place(prefix ?: "", suffix ?: "", rangeInsideHost, rangeInsideHost.substring(context.text)))
        return this
    }

    override fun doneInjecting() {
        done = true
    }
}
