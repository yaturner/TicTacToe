package com.codeonthego.tictactoe.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.codeonthego.tictactoe.R
import com.codeonthego.tictactoe.TicTacToePlugin
import com.codeonthego.tictactoe.game.GameUiState
import com.codeonthego.tictactoe.game.Role
import com.codeonthego.tictactoe.net.Mark
import com.codeonthego.tictactoe.net.NetUtil
import kotlinx.coroutines.launch

class TicTacToeFragment : Fragment(R.layout.fragment_tic_tac_toe) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val session = TicTacToePlugin.session ?: return

        val tvStatus = view.findViewById<TextView>(R.id.tvStatus)
        val groupIdle = view.findViewById<View>(R.id.groupIdle)
        val groupInvite = view.findViewById<View>(R.id.groupInvite)
        val groupBoard = view.findViewById<View>(R.id.groupBoard)
        val tvInvite = view.findViewById<TextView>(R.id.tvInvite)
        val tvTurn = view.findViewById<TextView>(R.id.tvTurn)
        val btnHost = view.findViewById<Button>(R.id.btnHost)
        val etJoinAddress = view.findViewById<EditText>(R.id.etJoinAddress)
        val etJoinToken = view.findViewById<EditText>(R.id.etJoinToken)
        val btnJoin = view.findViewById<Button>(R.id.btnJoin)
        val btnCancel = view.findViewById<Button>(R.id.btnCancel)
        val btnNewGame = view.findViewById<Button>(R.id.btnNewGame)
        val btnLeave = view.findViewById<Button>(R.id.btnLeave)
        val cellButtons = listOf(
            R.id.cell0, R.id.cell1, R.id.cell2,
            R.id.cell3, R.id.cell4, R.id.cell5,
            R.id.cell6, R.id.cell7, R.id.cell8,
        ).map { id -> view.findViewById<Button>(id) }

        btnHost.setOnClickListener {
            session.startHosting().onFailure { showError(it.message) }
        }

        btnJoin.setOnClickListener {
            val address = NetUtil.parseAddress(etJoinAddress.text.toString())
            val token = etJoinToken.text.toString().trim()
            if (address == null || token.length != 4) {
                showError("Enter a valid host address and the 4-digit token")
                return@setOnClickListener
            }
            session.joinSession(address.first, address.second, token).onFailure { showError(it.message) }
        }

        btnCancel.setOnClickListener { session.stopSession() }
        btnLeave.setOnClickListener { session.stopSession() }
        btnNewGame.setOnClickListener { session.requestNewGame() }

        cellButtons.forEachIndexed { index, button ->
            button.setOnClickListener { session.playCell(index) }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                session.state.collect { state ->
                    render(state, tvStatus, groupIdle, groupInvite, groupBoard, tvInvite, tvTurn, btnNewGame, cellButtons)
                }
            }
        }
    }

    private fun showError(message: String?) {
        Toast.makeText(requireContext(), message ?: "Something went wrong", Toast.LENGTH_SHORT).show()
    }

    private fun render(
        state: GameUiState,
        tvStatus: TextView,
        groupIdle: View,
        groupInvite: View,
        groupBoard: View,
        tvInvite: TextView,
        tvTurn: TextView,
        btnNewGame: Button,
        cellButtons: List<Button>,
    ) {
        groupIdle.visibility = if (state.role == Role.IDLE && !state.connecting) View.VISIBLE else View.GONE
        groupInvite.visibility = if (state.role != Role.IDLE && !state.opponentConnected) View.VISIBLE else View.GONE
        groupBoard.visibility = if (state.opponentConnected) View.VISIBLE else View.GONE
        btnNewGame.visibility = if (state.role == Role.HOST) View.VISIBLE else View.GONE

        tvStatus.text = state.lastError ?: getString(R.string.status_default)

        tvInvite.text = when {
            state.role == Role.HOST ->
                "Share this with your opponent:\n${state.localAddress}:${state.localPort}\nToken: ${state.localToken}"
            state.connecting -> "Connecting…"
            else -> "Waiting for the host to accept…"
        }

        state.board.forEachIndexed { index, mark ->
            val button = cellButtons[index]
            button.text = when (mark) {
                Mark.X -> "X"
                Mark.O -> "O"
                null -> ""
            }
            button.isEnabled = mark == null && state.winner == null && !state.isDraw &&
                state.turn == state.localMark
        }

        tvTurn.text = when {
            state.winner != null -> if (state.winner == state.localMark) "You win!" else "${state.opponentName ?: "Opponent"} wins"
            state.isDraw -> "Draw"
            !state.opponentConnected -> "Waiting for opponent…"
            state.turn == state.localMark -> "Your turn (${state.localMark})"
            else -> "${state.opponentName ?: "Opponent"}'s turn"
        }
    }
}
