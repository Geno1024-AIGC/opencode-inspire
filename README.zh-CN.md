# OpenCode 客户端（Android）

一个用于 [OpenCode](https://opencode.ai/) 服务器的简单 Android 客户端。

它通过 HTTP API 与正在运行的 `opencode serve` 实例通信，让你可以在手机上
连接自己的服务器、选择项目目录，并与 OpenCode 正常对话。

## 功能

- 通过主机和端口连接任意 OpenCode 服务器（例如 `http://192.168.1.10:4096`）
- 连接前检查服务器健康状态，并记住上次使用的地址
- 浏览服务器文件系统，选择项目 / 工作目录
- 与 OpenCode 进行正常对话，包括工具调用展示，以及中止当前回合的功能
- 深色 / 浅色主题

## 环境要求

- [Android SDK](https://developer.android.com/studio)（API 36）
- [JDK 17+](https://adoptium.net/)
- [Gradle](https://gradle.org/) 8.x（或使用自带的 `./gradlew` 包装器）
- 一个正在运行的 OpenCode 服务器：

  ```bash
  opencode serve --hostname 0.0.0.0 --port 4096
  ```

  > 注意：在手机上通常需要通过局域网访问你的电脑，因此请将服务器绑定到
  > 局域网地址（或使用 `--hostname 0.0.0.0`），并确保设备可以访问 `4096` 端口。

## 构建

这是一个标准的 Kotlin + Jetpack Compose Android 应用，使用 Gradle Kotlin DSL 构建。

使用 Gradle 包装器：

```bash
./gradlew :app:assembleDebug
```

或使用本地安装的 Gradle：

```bash
gradle :app:assembleDebug
```

调试版 APK 生成于：

```
app/build/outputs/apk/debug/app-debug.apk
```

可以通过以下命令安装到已连接的设备：

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## 使用说明

1. 打开应用，输入你的服务器地址，例如 `http://192.168.1.10:4096`。
2. 点击 **Connect**，应用会检查服务器健康状态。
3. 浏览到某个项目目录，点击 **Start conversation here**。
4. 与 OpenCode 进行对话。

## 项目结构

```
app/src/main/java/com/example/opencodeclient/
├── MainActivity.kt              # Activity + Compose 入口
├── data/
│   ├── Models.kt                # 协议 DTO（Session、Message、Part 等）
│   ├── OpenCodeClient.kt        # OpenCode 服务器 API 的 HTTP 客户端
│   └── SettingsRepository.kt    # DataStore 持久化
└── ui/
    ├── OpenCodeApp.kt           # 界面之间的导航
    ├── MainViewModel.kt         # 共享状态 / ViewModel
    ├── ConnectScreen.kt         # 服务器连接
    ├── ProjectBrowserScreen.kt  # 项目目录选择
    ├── ChatScreen.kt            # 对话
    └── theme/Theme.kt           # Material 3 主题
```

## 许可证

本项目基于 MIT 许可证分发。
