# Tic-Tac-Toe

A minimal two-player LAN game for [CodeOnTheGo](https://github.com/appdevforall/CodeOnTheGo), built the same way the **Pair** plugin does real-time collaboration: one device hosts a `WebSocketServer`, the other opens a `WebSocketClient` to it, and they trade compact JSON messages over `ws://` on the local network. No server, no account, no cloud.

It surfaces as a **Tic-Tac-Toe** tab in the editor. Host a game and an invite card shows the address and a 4-digit token; a guest types both in to join. The host plays X and always moves first; the guest plays O.

## Building

```sh
cd tic-tac-toe
./gradlew clean assemblePlugin
```

The `.cgp` lands in `build/plugin/`. Install it from inside CodeOnTheGo via the Plugin Manager. Always `clean` first — an incremental build can package an empty artifact.

## How it works

- **Host authority.** The host is the only side that ever applies a move to its own board and then broadcasts it. A guest's tap sends a `Move` *proposal*; the guest doesn't touch its own board until the host echoes that same message back, validated. This is the same trick Pair uses to avoid split-brain state, just simpler — tic-tac-toe only needs "is it your mark's turn and is the cell empty," not per-file sequence numbers.
- **Transport.** `GameServer` / `GameClient` wrap `org.java_websocket`, same as Pair's `PairWebSocketServer` / `PairWebSocketClient`. Messages are five types: `hi` (handshake), `mv` (move), `rst` (host resets for a rematch), `bye` (leave).
- **Pairing.** The host mints a `SecureRandom` 4-digit token on `startHosting()`; the guest supplies it as an `X-Game-Token` handshake header, checked with a constant-time comparison. Exactly one guest is accepted — a second connection attempt is rejected at the socket.
- **State.** `GameSession.state: StateFlow<GameUiState>` is the single source of truth; `TicTacToeFragment` collects it and renders the idle/host-invite/guest-invite/board screens with plain `View`s (no Compose dependency, to keep the plugin buildable against this repo's current toolchain).

## Source layout

```
tic-tac-toe/
└── src/main/
    ├── AndroidManifest.xml            plugin id, main class, permissions
    └── kotlin/com/codeonthego/tictactoe/
        ├── TicTacToePlugin.kt         IPlugin + EditorTabExtension + UIExtension entry
        ├── net/                       GameMessage, MessageCodec, GameServer, GameClient, NetUtil
        ├── game/                      GameEngine (board logic), GameSession (orchestrator), GameUiState
        └── ui/                        TicTacToeFragment
```

## Dependencies from `../libs/`

- `plugin-api.jar` — `IPlugin`, extensions, `PluginContext`; `compileOnly`, provided by the IDE at runtime.
- `gradle-plugin.jar` — the `com.itsaky.androidide.plugins.build` Gradle plugin that packages the `.cgp`.

No `eventbus-events.jar` or `shared.jar` needed — unlike Pair, this plugin never touches the editor, project, or file services, so it doesn't observe the IDE's EventBus at all.

## Playing

1. Put both devices on the **same WiFi** (a phone hotspot works).
2. Host: open the **Tic-Tac-Toe** tab, tap **Host a game**, share the address and token shown.
3. Guest: open the **Tic-Tac-Toe** tab, enter that address and token, tap **Join game**.
4. Host plays X, guest plays O, X moves first. **New game** (host only) starts a rematch on both boards.
