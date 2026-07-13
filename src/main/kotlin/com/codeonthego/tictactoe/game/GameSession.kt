package com.codeonthego.tictactoe.game

import com.codeonthego.tictactoe.net.GameClient
import com.codeonthego.tictactoe.net.GameMessage
import com.codeonthego.tictactoe.net.GameServer
import com.codeonthego.tictactoe.net.Mark
import com.codeonthego.tictactoe.net.NetUtil
import com.itsaky.androidide.plugins.PluginLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.java_websocket.WebSocket
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/**
 * Orchestrator for a two-player LAN game session — the tic-tac-toe analogue of Pair's
 * EditBroker. It owns the socket, mints the pairing token, and is the single place that
 * touches [GameEngine]. The host is authoritative: it applies its own moves immediately and
 * only ever applies a guest's move after validating it; the guest never applies a move until
 * the host echoes it back. That one rule is what keeps both boards identical without needing
 * per-move sequence numbers the way Pair's free-form text editing does.
 */
class GameSession(
    private val logger: PluginLogger,
    private var displayName: String = "Player",
) {

    private val localPeerId: String = UUID.randomUUID().toString()
    private val engine = GameEngine()

    // All engine reads/writes are confined to this single-threaded dispatcher so a UI click
    // and an incoming network callback can never race on the board array.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val scope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))

    private val serverRef = AtomicReference<GameServer?>(null)
    private val clientRef = AtomicReference<GameClient?>(null)

    private val _state = MutableStateFlow(GameUiState())
    val state: StateFlow<GameUiState> = _state

    fun startHosting(port: Int = NetUtil.DEFAULT_PORT): Result<Unit> {
        if (_state.value.role != Role.IDLE) {
            return Result.failure(IllegalStateException("A session is already active"))
        }
        val localIp = NetUtil.findLanIpv4() ?: run {
            _state.value = _state.value.copy(lastError = "No network connection available")
            return Result.failure(IllegalStateException("No LAN address available"))
        }
        val token = generateToken()
        val server = GameServer(port = port, expectedToken = token, callbacks = ServerCallbacksImpl())
        return runCatching {
            server.start()
            serverRef.set(server)
            engine.reset()
            _state.value = GameUiState(
                role = Role.HOST,
                localMark = Mark.X,
                localAddress = localIp,
                localPort = port,
                localToken = token,
                board = engine.snapshot(),
                turn = engine.turn(),
            )
        }.onFailure {
            logger.error("TicTacToePlugin: failed to start server", it)
            _state.value = _state.value.copy(lastError = "Could not start hosting (${it.message})")
        }
    }

    fun joinSession(host: String, port: Int, token: String): Result<Unit> {
        if (_state.value.role != Role.IDLE || _state.value.connecting) {
            return Result.failure(IllegalStateException("A session is already active"))
        }
        val client = GameClient(host = host, port = port, token = token, callbacks = ClientCallbacksImpl())
        _state.value = _state.value.copy(connecting = true, lastError = null)
        return runCatching {
            client.connect()
            clientRef.set(client)
        }.onFailure {
            logger.error("TicTacToePlugin: failed to connect to $host:$port", it)
            _state.value = _state.value.copy(connecting = false, lastError = "Could not connect to $host:$port")
        }
    }

    fun playCell(index: Int) {
        scope.launch {
            val current = _state.value
            val mark = current.localMark ?: return@launch
            if (!current.opponentConnected || current.winner != null || current.isDraw) return@launch
            if (engine.turn() != mark || !engine.canPlay(index)) return@launch
            when (current.role) {
                Role.HOST -> {
                    engine.play(index, mark)
                    publishBoard()
                    serverRef.get()?.sendMessage(GameMessage.Move(localPeerId, index, mark))
                }
                Role.GUEST -> {
                    // Proposal only — applied locally when the host echoes it back.
                    clientRef.get()?.sendMessage(GameMessage.Move(localPeerId, index, mark))
                }
                Role.IDLE -> Unit
            }
        }
    }

    fun requestNewGame() {
        scope.launch {
            if (_state.value.role != Role.HOST) return@launch
            engine.reset()
            publishBoard()
            serverRef.get()?.sendMessage(GameMessage.Reset(localPeerId))
        }
    }

    fun stopSession() {
        serverRef.getAndSet(null)?.let { server ->
            runCatching { server.sendMessage(GameMessage.Goodbye(localPeerId)) }
            runCatching { server.stop(200) }
        }
        clientRef.getAndSet(null)?.let { client ->
            runCatching { client.sendMessage(GameMessage.Goodbye(localPeerId)) }
            runCatching { client.close() }
        }
        scope.launch {
            engine.reset()
            _state.value = GameUiState()
        }
    }

    fun dispose() {
        stopSession()
        scope.cancel()
    }

    private fun publishBoard() {
        _state.value = _state.value.copy(
            board = engine.snapshot(),
            turn = engine.turn(),
            winner = engine.winner(),
            isDraw = engine.isDraw(),
        )
    }

    private fun generateToken(): String =
        SecureRandom().nextInt(10_000).toString().padStart(4, '0')

    private fun handleIncoming(message: GameMessage, isHost: Boolean) {
        scope.launch {
            when (message) {
                is GameMessage.Hello -> {
                    _state.value = _state.value.copy(
                        opponentConnected = true,
                        opponentName = message.displayName,
                    )
                }
                is GameMessage.Move -> {
                    if (isHost) {
                        // The host validates a guest's proposal before it becomes real.
                        if (message.mark == Mark.O && engine.turn() == Mark.O && engine.canPlay(message.cell)) {
                            engine.play(message.cell, Mark.O)
                            publishBoard()
                            serverRef.get()?.sendMessage(message)
                        }
                    } else {
                        // The guest only ever applies what the host has already confirmed.
                        if (engine.canPlay(message.cell)) {
                            engine.play(message.cell, message.mark)
                            publishBoard()
                        }
                    }
                }
                is GameMessage.Reset -> {
                    engine.reset()
                    publishBoard()
                }
                is GameMessage.Goodbye -> {
                    _state.value = _state.value.copy(opponentConnected = false, opponentName = null)
                }
            }
        }
    }

    private inner class ServerCallbacksImpl : GameServer.Callbacks {
        override fun onGuestConnected(connection: WebSocket) {
            serverRef.get()?.sendMessage(GameMessage.Hello(localPeerId, displayName))
        }

        override fun onGuestDisconnected(connection: WebSocket, reason: String?) {
            _state.value = _state.value.copy(opponentConnected = false, opponentName = null)
        }

        override fun onMessage(message: GameMessage) = handleIncoming(message, isHost = true)

        override fun onServerStarted(port: Int) {
            logger.info("TicTacToePlugin: hosting on port $port")
        }

        override fun onError(error: Throwable) {
            logger.warn("TicTacToePlugin: server error (${error.message})")
        }
    }

    private inner class ClientCallbacksImpl : GameClient.Callbacks {
        override fun onConnected() {
            engine.reset()
            _state.value = _state.value.copy(
                role = Role.GUEST,
                localMark = Mark.O,
                connecting = false,
                board = engine.snapshot(),
                turn = engine.turn(),
            )
            clientRef.get()?.sendMessage(GameMessage.Hello(localPeerId, displayName))
        }

        override fun onDisconnected(reason: String?) {
            val wasGuest = _state.value.role == Role.GUEST
            clientRef.set(null)
            _state.value = GameUiState(
                lastError = if (wasGuest) "Disconnected from host (${reason ?: "connection lost"})" else null,
            )
        }

        override fun onMessage(message: GameMessage) = handleIncoming(message, isHost = false)

        override fun onError(error: Throwable) {
            logger.warn("TicTacToePlugin: client error (${error.message})")
        }
    }
}
