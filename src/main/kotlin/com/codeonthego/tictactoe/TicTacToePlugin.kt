package com.codeonthego.tictactoe

import android.os.Build
import com.codeonthego.tictactoe.game.GameSession
import com.codeonthego.tictactoe.ui.TicTacToeFragment
import com.itsaky.androidide.plugins.IPlugin
import com.itsaky.androidide.plugins.PluginContext
import com.itsaky.androidide.plugins.extensions.EditorTabExtension
import com.itsaky.androidide.plugins.extensions.EditorTabItem
import com.itsaky.androidide.plugins.extensions.NavigationItem
import com.itsaky.androidide.plugins.extensions.UIExtension
import com.itsaky.androidide.plugins.services.IdeEditorTabService

class TicTacToePlugin : IPlugin, EditorTabExtension, UIExtension {

    private lateinit var pluginContext: PluginContext

    override fun initialize(context: PluginContext): Boolean {
        pluginContext = context
        return runCatching {
            session = GameSession(context.logger, displayName = defaultDisplayName())
            context.logger.info("TicTacToePlugin initialized")
            true
        }.getOrElse {
            context.logger.error("TicTacToePlugin: initialize failed", it)
            false
        }
    }

    override fun activate(): Boolean {
        pluginContext.logger.info("TicTacToePlugin activated")
        return true
    }

    override fun deactivate(): Boolean {
        runCatching { session?.stopSession() }
        return true
    }

    override fun dispose() {
        runCatching { session?.dispose() }
        session = null
    }

    override fun getMainEditorTabs(): List<EditorTabItem> = listOf(
        EditorTabItem(
            id = TAB_ID,
            title = "Tic-Tac-Toe",
            icon = R.drawable.ic_game,
            fragmentFactory = { TicTacToeFragment() },
            isCloseable = true,
            isPersistent = false,
            order = 0,
            isEnabled = true,
            isVisible = true,
            tooltip = "Play tic-tac-toe with a peer on your LAN",
        )
    )

    override fun getSideMenuItems(): List<NavigationItem> = listOf(
        NavigationItem(
            id = "tictactoe_open",
            title = "Tic-Tac-Toe",
            icon = R.drawable.ic_game,
            isEnabled = true,
            isVisible = true,
            group = "tools",
            order = 0,
            action = { openTab() },
        )
    )

    private fun openTab() {
        val tabService = pluginContext.services.get(IdeEditorTabService::class.java) ?: run {
            pluginContext.logger.error("TicTacToePlugin: editor tab service unavailable")
            return
        }
        if (!tabService.isTabSystemAvailable()) {
            pluginContext.logger.error("TicTacToePlugin: editor tab system not available")
            return
        }
        runCatching { tabService.selectPluginTab(TAB_ID) }
            .onFailure { pluginContext.logger.error("TicTacToePlugin: failed to open tab", it) }
    }

    private fun defaultDisplayName(): String =
        Build.MODEL?.takeIf { it.isNotBlank() } ?: "Android"

    companion object {
        const val PLUGIN_ID: String = "com.codeonthego.tictactoe"
        const val TAB_ID: String = "tictactoe_main"

        // Lives for the plugin's process lifetime so the session survives the tab being
        // closed and reopened — mirrors PairServiceLocator's role in the Pair plugin.
        var session: GameSession? = null
            private set
    }
}
