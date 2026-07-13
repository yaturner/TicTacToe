package com.codeonthego.tictactoe.net

import org.json.JSONObject

/** Same compact-JSON-over-ws:// approach as the Pair plugin's MessageCodec. */
object MessageCodec {

    private const val KEY_TYPE = "t"
    private const val KEY_PEER = "pid"
    private const val KEY_NAME = "name"
    private const val KEY_PROTO = "proto"
    private const val KEY_CELL = "cell"
    private const val KEY_MARK = "mark"

    private const val T_HELLO = "hi"
    private const val T_MOVE = "mv"
    private const val T_RESET = "rst"
    private const val T_GOODBYE = "bye"

    fun encode(message: GameMessage): String {
        val json = JSONObject()
        json.put(KEY_PEER, message.peerId)
        when (message) {
            is GameMessage.Hello -> {
                json.put(KEY_TYPE, T_HELLO)
                json.put(KEY_NAME, message.displayName)
                json.put(KEY_PROTO, message.protocolVersion)
            }
            is GameMessage.Move -> {
                json.put(KEY_TYPE, T_MOVE)
                json.put(KEY_CELL, message.cell)
                json.put(KEY_MARK, message.mark.name)
            }
            is GameMessage.Reset -> json.put(KEY_TYPE, T_RESET)
            is GameMessage.Goodbye -> json.put(KEY_TYPE, T_GOODBYE)
        }
        return json.toString()
    }

    fun decode(raw: String): GameMessage {
        val json = JSONObject(raw)
        val peerId = json.getString(KEY_PEER)
        return when (val type = json.getString(KEY_TYPE)) {
            T_HELLO -> GameMessage.Hello(
                peerId = peerId,
                displayName = json.getString(KEY_NAME),
                protocolVersion = json.optInt(KEY_PROTO, 1),
            )
            T_MOVE -> GameMessage.Move(
                peerId = peerId,
                cell = json.getInt(KEY_CELL),
                mark = Mark.valueOf(json.getString(KEY_MARK)),
            )
            T_RESET -> GameMessage.Reset(peerId)
            T_GOODBYE -> GameMessage.Goodbye(peerId)
            else -> throw IllegalArgumentException("Unknown message type: $type")
        }
    }
}
