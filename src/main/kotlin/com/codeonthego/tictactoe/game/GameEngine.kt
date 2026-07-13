package com.codeonthego.tictactoe.game

import com.codeonthego.tictactoe.net.Mark

/** Pure board logic — 9 cells, no I/O. Not thread-safe by itself; GameSession confines all
 *  access to a single-threaded dispatcher. */
class GameEngine {

    private val cells = arrayOfNulls<Mark>(9)

    fun snapshot(): List<Mark?> = cells.toList()

    /** X always opens; whoever has played fewer marks moves next. */
    fun turn(): Mark {
        val x = cells.count { it == Mark.X }
        val o = cells.count { it == Mark.O }
        return if (x == o) Mark.X else Mark.O
    }

    fun canPlay(index: Int): Boolean =
        index in 0..8 && cells[index] == null && winner() == null

    fun play(index: Int, mark: Mark): Boolean {
        if (!canPlay(index)) return false
        cells[index] = mark
        return true
    }

    fun reset() {
        cells.fill(null)
    }

    fun winner(): Mark? {
        for ((a, b, c) in WIN_LINES) {
            val mark = cells[a] ?: continue
            if (mark == cells[b] && mark == cells[c]) return mark
        }
        return null
    }

    fun isDraw(): Boolean = winner() == null && cells.none { it == null }

    private companion object {
        val WIN_LINES = listOf(
            Triple(0, 1, 2), Triple(3, 4, 5), Triple(6, 7, 8), // rows
            Triple(0, 3, 6), Triple(1, 4, 7), Triple(2, 5, 8), // columns
            Triple(0, 4, 8), Triple(2, 4, 6),                  // diagonals
        )
    }
}
