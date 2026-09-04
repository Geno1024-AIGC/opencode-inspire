# OpenCode Client (Android)

A simple Android client for [OpenCode](https://opencode.ai/) servers.

It talks to a running `opencode serve` instance over its HTTP API, letting you
connect to your server, pick a project directory, and chat with OpenCode from
your phone.

## Features

- Connect to any OpenCode server by host and port (e.g. `http://192.168.1.10:4096`)
- Health-check the server before connecting, and remember the last used URL
- Browse the server filesystem to choose a project / working directory
- Normal chat conversation with OpenCode, including tool-call display and the
  ability to abort a running turn
- Per-session file browser: list the session's project files, preview a file's
  content, then send it to OpenCode as a prompt (send only happens on a tap of
  **Send to AI**)
- Compact a running session via `executeCommand(sid, "compact")`
- Per-session history stats (messages, tool calls, tokens, elapsed time) with
  cumulative delta arrows
- Bookmark / star sessions to pin them at the top of the drawer
- Streaming notification while OpenCode is responding
- Dark / light theme

## Requirements

- [Android SDK](https://developer.android.com/studio) (API 36)
- [JDK 17+](https://adoptium.net/)
- [Gradle](https://gradle.org/) 8.x (or use the bundled `./gradlew` wrapper)
- A running OpenCode server:

  ```bash
  opencode serve --hostname 0.0.0.0 --port 4096
  ```

  > Note: on a phone you usually need to reach your computer over the local
  > network, so bind the server to your LAN address (or use `--hostname 0.0.0.0`)
  > and make sure port `4096` is reachable from the device.

## Build

The project is a standard Kotlin + Jetpack Compose Android app using a Gradle
Kotlin DSL build.

Using the Gradle wrapper:

```bash
./gradlew :app:assembleDebug
```

Or with a local Gradle installation:

```bash
gradle :app:assembleDebug
```

The debug APK is produced at:

```
app/build/outputs/apk/debug/app-debug.apk
```

You can install it on a connected device with:

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Usage

1. Open the app. Enter your server URL, e.g. `http://192.168.1.10:4096`.
2. Tap **Connect**. The app verifies the server health.
3. Browse to a project directory and tap **Start conversation here**.
4. Chat with OpenCode.

## Project structure

```
app/src/main/java/com/example/opencodeclient/
├── MainActivity.kt              # Activity + Compose entry point
├── data/
│   ├── Models.kt                # Protocol DTOs (Session, Message, Part, ...)
│   ├── OpenCodeClient.kt        # HTTP client for the OpenCode server API
│   └── SettingsRepository.kt    # DataStore persistence
└── ui/
    ├── OpenCodeApp.kt           # Navigation between screens
    ├── MainViewModel.kt         # Shared state / ViewModel
    ├── ConnectScreen.kt         # Server connection
    ├── ProjectBrowserScreen.kt  # Project directory selection
    ├── ChatScreen.kt            # Conversation
    └── theme/Theme.kt           # Material 3 theme
```

## License

This project is distributed under the MIT License.
