package com.codeonthego.tictactoe.game

import com.codeonthego.tictactoe.net.Mark

enum class Role { IDLE, HOST, GUEST }

data class GameUiState(
    val role: Role = Role.IDLE,
    val localMark: Mark? = null,
    val localAddress: String? = null,
    val localPort: Int? = null,
    val localToken: String? = null,
    val connecting: Boolean = false,
    val opponentConnected: Boolean = false,
    val opponentName: String? = null,
    val board: List<Mark?> = List(9) { null },
    val turn: Mark = Mark.X,
    val winner: Mark? = null,
    val isDraw: Boolean = false,
    val lastError: String? = null,
)
