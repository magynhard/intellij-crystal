package de.magynhard.crystal

import com.intellij.lexer.Lexer
import com.intellij.psi.impl.cache.impl.BaseFilterLexer
import com.intellij.psi.impl.cache.impl.OccurrenceConsumer
import com.intellij.psi.impl.cache.impl.todo.LexerBasedTodoIndexer
import com.intellij.psi.search.UsageSearchContext
import de.magynhard.crystal.lexer.CrystalLexerAdapter
import de.magynhard.crystal.psi.CrystalTypes

class CrystalTodoIndexer : LexerBasedTodoIndexer() {
    override fun createLexer(consumer: OccurrenceConsumer): Lexer {
        return CrystalTodoFilterLexer(CrystalLexerAdapter(), consumer)
    }
}

private class CrystalTodoFilterLexer(
    lexer: Lexer,
    consumer: OccurrenceConsumer
) : BaseFilterLexer(lexer, consumer) {

    override fun advance() {
        val tokenType = delegate.tokenType
        if (tokenType == CrystalTypes.LINE_COMMENT) {
            val start = delegate.tokenStart
            val end = delegate.tokenEnd
            addOccurrenceInToken(UsageSearchContext.IN_COMMENTS.toInt())
            advanceTodoItemCountsInToken()
        }
        delegate.advance()
    }
}
