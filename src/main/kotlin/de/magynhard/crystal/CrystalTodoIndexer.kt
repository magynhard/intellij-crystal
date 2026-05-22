package de.magynhard.crystal

import com.intellij.lexer.Lexer
import com.intellij.psi.impl.cache.impl.OccurrenceConsumer
import com.intellij.psi.impl.cache.impl.todo.LexerBasedTodoIndexer
import de.magynhard.crystal.lexer.CrystalLexerAdapter

class CrystalTodoIndexer : LexerBasedTodoIndexer() {
    override fun createLexer(consumer: OccurrenceConsumer): Lexer {
        return CrystalLexerAdapter()
    }
}
