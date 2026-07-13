package com.codeonthego.tictactoe.net

import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI

/** The guest side: one socket to the host. */
class GameClient(
    host: String,
    port: Int,
    token: String,
    private val callbacks: Callbacks,
) : WebSocketClient(URI("ws://$host:$port"), mapOf(GameServer.TOKEN_HEADER to token)) {

    interface Callbacks {
        fun onConnected()
        fun onDisconnected(reason: String?)
        fun onMessage(message: GameMessage)
        fun onError(error: Throwable)
    }

    override fun onOpen(handshakedata: ServerHandshake?) {
        callbacks.onConnected()
    }

    override fun onMessage(message: String) {
        val parsed = runCatching { MessageCodec.decode(message) }.getOrNull() ?: return
        callbacks.onMessage(parsed)
    }

    override fun onClose(code: Int, reason: String?, remote: Boolean) {
        callbacks.onDisconnected(reason)
    }

    override fun onError(ex: Exception) {
        callbacks.onError(ex)
    }

    fun sendMessage(message: GameMessage) {
        if (!isOpen) return
        runCatching { send(MessageCodec.encode(message)) }
    }
}
