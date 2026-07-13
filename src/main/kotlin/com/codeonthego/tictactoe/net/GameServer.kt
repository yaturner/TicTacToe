package com.codeonthego.tictactoe.net

import org.java_websocket.WebSocket
import org.java_websocket.framing.CloseFrame
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress
import java.security.MessageDigest

/**
 * The host side of the star topology. Tic-tac-toe is strictly two players, so — unlike a
 * broadcast server — this accepts exactly one guest and rejects any second connection
 * attempt outright.
 */
class GameServer(
    port: Int,
    private val expectedToken: String,
    private val callbacks: Callbacks,
) : WebSocketServer(InetSocketAddress(port)) {

    interface Callbacks {
        fun onGuestConnected(connection: WebSocket)
        fun onGuestDisconnected(connection: WebSocket, reason: String?)
        fun onMessage(message: GameMessage)
        fun onServerStarted(port: Int)
        fun onError(error: Throwable)
    }

    @Volatile private var guest: WebSocket? = null

    init {
        isReuseAddr = true
    }

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        val provided = handshake.getFieldValue(TOKEN_HEADER).orEmpty()
        if (!tokenMatches(provided)) {
            runCatching { conn.close(CloseFrame.POLICY_VALIDATION, "unauthorized") }
            return
        }
        if (guest != null) {
            runCatching { conn.close(CloseFrame.POLICY_VALIDATION, "table full") }
            return
        }
        guest = conn
        callbacks.onGuestConnected(conn)
    }

    private fun tokenMatches(provided: String): Boolean {
        if (provided.isEmpty()) return false
        return MessageDigest.isEqual(
            provided.toByteArray(Charsets.UTF_8),
            expectedToken.toByteArray(Charsets.UTF_8),
        )
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String?, remote: Boolean) {
        if (guest === conn) guest = null
        callbacks.onGuestDisconnected(conn, reason)
    }

    override fun onMessage(conn: WebSocket, message: String) {
        val parsed = runCatching { MessageCodec.decode(message) }.getOrNull() ?: return
        callbacks.onMessage(parsed)
    }

    override fun onError(conn: WebSocket?, ex: Exception) {
        callbacks.onError(ex)
    }

    override fun onStart() {
        callbacks.onServerStarted(port)
    }

    fun sendMessage(message: GameMessage) {
        val target = guest ?: return
        if (!target.isOpen) return
        runCatching { target.send(MessageCodec.encode(message)) }
    }

    companion object {
        const val TOKEN_HEADER: String = "X-Game-Token"
    }
}
