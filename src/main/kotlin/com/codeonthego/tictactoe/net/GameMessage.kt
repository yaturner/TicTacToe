package com.codeonthego.tictactoe.net

enum class Mark { X, O }

sealed interface GameMessage {
    val peerId: String

    /** Handshake, sent both directions right after the socket opens. */
    data class Hello(
        override val peerId: String,
        val displayName: String,
        val protocolVersion: Int = PROTOCOL_VERSION,
    ) : GameMessage

    /**
     * A guest sends this as a *proposal*; the host is the only side that ever applies a
     * move to its own board on receipt, then echoes the same message back so the guest
     * (and the host's own UI, for the host's own moves) stays in sync. See GameSession.
     */
    data class Move(
        override val peerId: String,
        val cell: Int,
        val mark: Mark,
    ) : GameMessage

    /** Host-only: clears the board on both sides for a rematch. */
    data class Reset(
        override val peerId: String,
    ) : GameMessage

    /** Either side leaving; the socket closing without one implies the same thing. */
    data class Goodbye(
        override val peerId: String,
    ) : GameMessage

    companion object {
        const val PROTOCOL_VERSION: Int = 1
    }
}
