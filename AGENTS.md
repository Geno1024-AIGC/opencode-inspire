# AGENTS.md — 项目规范（权威，供 AI 代理遵守）

以下规则约束本仓库每一次编码会话，请务必遵守，无需再次提醒。

## 提交规则
- **一项一个 commit**。不要把无关改动揉进同一个 commit。每个独立事项完成后立即提交。
- 提交信息格式：`<bra><action><ket> <summary>`。
    - <action> 为动词，例如 Update、Typo、Refactor 等。
    - <braket> 为表示事件大小的括号对，大事件用大括号 {}，小事件用圆括号 ()，中事件用方括号 []。
    - <summary> 为一句简短的英文描述修改内容。
- 每次 commit 后将（或小批量的）改动推送到 `origin` 的 `master`。
- 除非用户明确要求，绝不 amend、绝不强推、绝不改写已推送历史。
- 每个 commit 只包含本意要包含的文件，绝不提交密钥等敏感信息。

## tokens.txt —— 每次提交前必须刷新
- 在 **每次** commit（`git add`）之前刷新 `tokens.txt`，无论代码是否有改动。
- 刷新方式：读取 OpenCode 应用数据库（SQLite）`~/.local/share/opencode/opencode.db`，并**仅限本项目**：`project_id = d8b3f3c991927ab17d475b9ccd2fa060fbe74456`（仓库 `Geno1024-AIGenerated/opencode-inspire`）。刷新脚本：`scripts/gen_tokens.py`（已固定 DB 与 project_id）。
- 文件格式（由 `app/build.gradle.kts` 消费）：
  - 第 0 行：input
  - 第 1 行：output
  - 第 2 行：reasoning
  - 第 3 行：cacheRead
  - 第 4 行：cacheWrite
  - 第 5 行：消息数（user+assistant 消息）
  - 第 6 行起：每个模型一行 `[*]model:input:output:reasoning:cacheRead:cacheWrite:msgs:cost`
    - `*` 前缀 = 名字加粗显示（对于贡献大、Token 消耗大的模型进行标记）
    - 按 input+output+reasoning+cacheRead+cacheWrite 总数降序排列；本项目不存在某模型数据时省略该行（不得伪造）
  - token 总数（= input+output+reasoning+cacheRead+cacheWrite）由 `build.gradle.kts` 计算，文件中不单独一行
- 模型 id = `session.model.id`（JSON 字段）；各行列来自聚合 `session.tokens_*` 列；消息数来自 `message.data.role in ("user","assistant")`。
- 绝不凭空构想或估算数字，文件必须与数据库一致。

## 构建与验证
- 构建命令（Ubuntu，显式指定 JDK）：
  `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew -Dorg.gradle.java.home=/usr/lib/jvm/java-21-openjdk-amd64 assembleDebug`
- `app/build.gradle.kts` 每次构建会自动递增 `build_count.txt`；其变更随代码一起提交。
- 推送前必须构建成功（exit 0）。若失败，先修复错误再继续。
- 若项目内提供 lint/类型检查，可运行。

## 代码库事实
- Android 应用，Kotlin，Jetpack Compose，Material 3。数值/统计用等宽字体：`MonoFontFamily`（定义于 ui）。
- 应用图标：自适应图标；背景色 `#0D1117`（`ic_launcher_background`），前景 `ic_launcher_foreground`。
- 关于页 token 统计与 `tokens.txt` 均按项目范围统计；关于页小节标题为 "App dev tokens" / 「本应用开发消耗 Token」，无项目范围说明句。
- 本项目没有 deepseek-v4-flash-free 数据；除非它真的出现在项目统计里，否则不要在 UI 文案中提及。
- 使用日历：日历顶部有模型过滤下拉框（全部 + 已使用模型）；会话详情页在 Token 行内显示模型小胶囊（不是单独一行）。

