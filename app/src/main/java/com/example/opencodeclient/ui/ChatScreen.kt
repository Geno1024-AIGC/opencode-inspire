package com.example.opencodeclient.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

import com.example.opencodeclient.R
import com.example.opencodeclient.data.FileNode
import com.example.opencodeclient.data.ModelInfo
import com.example.opencodeclient.data.QuestionRequest
import com.example.opencodeclient.data.Tokens
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.model.DefaultMarkdownTypography
import com.mikepenz.markdown.model.MarkdownTypography
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: MainViewModel,
    onMenu: () -> Unit,
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val sending by viewModel.sending.collectAsStateWithLifecycle()
    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
    val sessionTokens by viewModel.sessionTokens.collectAsStateWithLifecycle()
    val contextWindow by viewModel.contextWindow.collectAsStateWithLifecycle()
    val promptTokens by viewModel.promptTokens.collectAsStateWithLifecycle()
    val cumulativeTokens by viewModel.cumulativeTokens.collectAsStateWithLifecycle()
    val sessionElapsed by viewModel.sessionElapsed.collectAsStateWithLifecycle()
    val pendingQuestions by viewModel.pendingQuestions.collectAsStateWithLifecycle()
    val todos by viewModel.todos.collectAsStateWithLifecycle()
    val commands by viewModel.commands.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val currentModelId by viewModel.currentModelId.collectAsStateWithLifecycle()
    val pendingPermissions by viewModel.pendingPermissions.collectAsStateWithLifecycle()
    val shortTokens by viewModel.shortTokens.collectAsStateWithLifecycle()
    val userBubbleColor by viewModel.userBubbleColor.collectAsStateWithLifecycle()
    val assistantBubbleColor by viewModel.assistantBubbleColor.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var input by rememberSaveable { mutableStateOf("") }
    var showFiles by rememberSaveable { mutableStateOf(false) }
    var userScrolledAway by remember { mutableStateOf(false) }

    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { (firstIndex, offset) ->
            userScrolledAway = !(firstIndex == 0 && offset == 0)
        }
    }

    LaunchedEffect(
        messages.size,
        sending,
        userScrolledAway,
    ) {
        if (!userScrolledAway && messages.isNotEmpty()) {
            listState.scrollToItem(0)
        }
     }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.statusBarsPadding(),
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                activeSession?.title?.ifBlank { "OpenCode" } ?: "OpenCode",
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (activeSession != null && models.isNotEmpty()) {
                                ModelSwitcher(
                                    models = models,
                                    currentModelId = currentModelId,
                                    onSelect = { model -> viewModel.switchModel(model.providerId ?: "opencode", model.id ?: "") },
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onMenu) { Icon(Icons.Filled.Menu, stringResource(R.string.drawer_settings)) }
                    },
                    actions = {
                        if (activeSession != null) {
                            IconButton(onClick = { showFiles = true }) {
                                Icon(Icons.Filled.Folder, stringResource(R.string.files_title))
                            }
                            SessionActionsMenu(
                                onRename = { title -> viewModel.renameSession(title) },
                                onDelete = { viewModel.deleteSession() },
                            )
                        }
                        IconButton(onClick = { viewModel.refreshSession() }) {
                            Icon(Icons.Filled.Refresh, stringResource(R.string.chat_refresh))
                        }
                        if (sending) {
                            IconButton(onClick = { viewModel.abort() }) {
                                Icon(Icons.Filled.Stop, stringResource(R.string.chat_abort))
                            }
                        }
                    },
                )
            },
            floatingActionButton = {
                if (sending) {
                    FloatingActionButton(onClick = { viewModel.abort() }) {
                    Icon(Icons.Filled.Stop, stringResource(R.string.chat_abort))
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            TokenStatsBar(viewModel = viewModel, tokens = sessionTokens, promptTokens = promptTokens, contextWindow = contextWindow, shortTokens = shortTokens)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                reverseLayout = true,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (sending) {
                    item(key = "sending") { SendingIndicator() }
                }
                val reversedMessages = messages.asReversed()
                itemsIndexed(reversedMessages, key = { idx, item -> item.id }) { index, msg ->
                    val prevCumulative = if (index + 1 < reversedMessages.size) reversedMessages[index + 1].cumulativeTokens else null
                    val responseTime = if (msg.role == "assistant" && msg.time > 0L) {
                        val precedingUserTime = (index + 1 until reversedMessages.size)
                            .firstOrNull { reversedMessages[it].role == "user" && reversedMessages[it].time > 0L }
                            ?.let { reversedMessages[it].time }
                        if (precedingUserTime != null && msg.time > precedingUserTime) msg.time - precedingUserTime else null
                    } else null
                    MessageBubble(
                        msg = msg,
                        cumulativeTokens = if (msg.cumulativeTokens > 0) msg.cumulativeTokens else null,
                        deltaTokens = if (msg.cumulativeTokens > 0) {
                            if (prevCumulative != null) (msg.cumulativeTokens - prevCumulative).coerceAtLeast(0L) else msg.cumulativeTokens
                        } else null,
                        sessionElapsed = if (index == 0) sessionElapsed else null,
                        responseTime = responseTime,
                        userColor = userBubbleColor,
                        assistantColor = assistantBubbleColor,
                    )
                }
            }

            if (todos.isNotEmpty()) {
                TodoPanel(todos)
                Spacer(Modifier.height(4.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    val trimmed = input.trimStart()
                    val showCommands = input.startsWith("/") && !trimmed.contains(" ")
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.chat_placeholder)) },
                    )
                    if (showCommands) {
                        DropdownMenu(
                            expanded = true,
                            onDismissRequest = {},
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (commands.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.slash_no_commands)) },
                                    onClick = {},
                                )
                            } else {
                                commands.forEach { cmd ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text("/${cmd.name}", fontWeight = FontWeight.SemiBold)
                                                if (cmd.description.isNotBlank()) {
                                                    Text(
                                                        cmd.description,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            viewModel.runCommand(cmd)
                                            input = ""
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                IconButton(
                    onClick = {
                        viewModel.send(input.trim())
                         input = ""
                     },
                     enabled = input.isNotBlank(),
                     modifier = Modifier.padding(bottom = 4.dp),
                 ) {
                     Icon(Icons.AutoMirrored.Filled.Send, "Send")
                 }
             }
         }
     }

     if (userScrolledAway) {
         FloatingActionButton(
             onClick = { coroutineScope.launch { listState.scrollToItem(0) } },
             modifier = Modifier
                 .align(Alignment.BottomEnd)
                 .padding(16.dp),
         ) {
             Icon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.scroll_to_bottom))
         }
     }

      if (showFiles) {
        val directory = activeSession?.directory
        if (directory != null) {
            ModalBottomSheet(onDismissRequest = { showFiles = false }) {
                FileBrowserSheet(
                    root = directory,
                    viewModel = viewModel,
                    onClose = { showFiles = false },
                )
            }
        }
    }

    if (pendingQuestions.isNotEmpty()) {
        PendingQuestionsSheet(
            requests = pendingQuestions,
            onReply = { q, answers -> viewModel.replyQuestions(q, answers) },
            onReject = { viewModel.rejectQuestion(it) },
            onDismissAll = { pendingQuestions.forEach(viewModel::rejectQuestion) },
        )
    }

     pendingPermissions.firstOrNull()?.let { permission ->
         PermissionDialog(permission = permission) { reply ->
             viewModel.replyPermission(permission, reply)
         }
     }
    }
}

@Composable
private fun PermissionDialog(
    permission: com.example.opencodeclient.data.PermissionRequest,
    onReply: (String) -> Unit,
) {
    val resourceText = permission.patterns.joinToString(", ")
    AlertDialog(
        onDismissRequest = { onReply("reject") },
        title = { Text(stringResource(R.string.permission_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    permission.permission,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                )
                if (resourceText.isNotBlank()) {
                    Text(
                        resourceText,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                permission.metadata?.takeIf { it.isNotEmpty() }?.entries?.take(5)?.forEach { (k, v) ->
                    SummaryRow(label = k, value = v, monospace = true)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onReply("once") }) { Text(stringResource(R.string.permission_allow_once)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onReply("always") }) { Text(stringResource(R.string.permission_allow_always)) }
                TextButton(onClick = { onReply("reject") }) { Text(stringResource(R.string.permission_deny)) }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PendingQuestionsSheet(
    requests: List<QuestionRequest>,
    onReply: (QuestionRequest, List<List<String>>) -> Unit,
    onReject: (QuestionRequest) -> Unit,
    onDismissAll: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismissAll) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.chat_questions_title, requests.size),
                    style = MaterialTheme.typography.titleMedium,
                )
                TextButton(onClick = onDismissAll) { Text(stringResource(R.string.chat_reject_all)) }
            }
            requests.forEach { question ->
                QuestionCard(
                    question = question,
                    onReply = { answers -> onReply(question, answers) },
                    onReject = { onReject(question) },
                )
            }
        }
    }
}

@Composable
private fun QuestionCard(
    question: QuestionRequest,
    onReply: (List<List<String>>) -> Unit,
    onReject: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            var selections by rememberSaveable(question.id) {
                mutableStateOf(List(question.questions.size) { mutableListOf<String>() } as List<MutableList<String>>)
            }
            var customs by rememberSaveable(question.id) {
                mutableStateOf(List(question.questions.size) { "" } as List<String>)
            }
            question.questions.forEachIndexed { index, q ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        q.header.ifBlank { q.question },
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (q.header.isNotBlank() && q.question != q.header) {
                        Text(q.question, style = MaterialTheme.typography.bodySmall)
                    }
                    q.options.forEach { opt ->
                        val selected = selections[index].contains(opt.label)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selections = selections.toMutableList().also { list ->
                                        if (q.multiple) {
                                            if (selected) {
                                                list[index] = selections[index].filterNot { it == opt.label }.toMutableList()
                                            } else {
                                                list[index] = (selections[index] + opt.label).toMutableList()
                                            }
                                        } else {
                                            list[index] = if (selected) mutableListOf() else mutableListOf(opt.label)
                                        }
                                    }
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (q.multiple) {
                                Checkbox(checked = selected, onCheckedChange = null)
                            } else {
                                RadioButton(selected = selected, onClick = null)
                            }
                            Column {
                                Text(opt.label, style = MaterialTheme.typography.bodyMedium)
                                if (opt.description.isNotBlank()) {
                                    Text(opt.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    if (q.custom != false) {
                        OutlinedTextField(
                            value = customs[index],
                            onValueChange = { v ->
                                customs = customs.toMutableList().also { it[index] = v }
                            },
                            label = { Text(stringResource(R.string.chat_custom_answer)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onReject) { Text(stringResource(R.string.chat_reject)) }
                TextButton(
                    onClick = {
                        val answers = question.questions.mapIndexed { i, q ->
                            val labels = selections[i].toList()
                            val custom = customs[i].trim()
                            if (custom.isNotEmpty()) labels + custom else labels
                        }
                        onReply(answers)
                    },
                ) { Text(stringResource(R.string.chat_submit)) }
            }
        }
    }
}

@Composable
private fun SendingIndicator() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.height(18.dp).width(18.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.chat_working), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun MarkdownMessage(content: String) {
    val t = MaterialTheme.typography
    val codeStyle = t.bodyMedium.copy(fontFamily = FontFamily.Monospace)
    val typography: MarkdownTypography = DefaultMarkdownTypography(
        text = t.bodyLarge,
        code = codeStyle,
        h1 = t.headlineLarge,
        h2 = t.headlineMedium,
        h3 = t.headlineSmall,
        h4 = t.titleLarge,
        h5 = t.titleMedium,
        h6 = t.titleSmall,
        quote = t.bodyMedium,
        paragraph = t.bodyLarge,
        ordered = t.bodyLarge,
        bullet = t.bodyLarge,
        list = t.bodyLarge,
    )
    val components = markdownComponents(
        codeFence = { model ->
            CopyableCode(fenceCode(model.content, model.node))
        },
        codeBlock = { model ->
            CopyableCode(blockCode(model.content, model.node))
        },
    )
    Markdown(
        content = content,
        typography = typography,
        components = components,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun fenceCode(content: String, node: org.intellij.markdown.ast.ASTNode): String =
    if (node.children.size >= 3) {
        val start = node.children[2].startOffset
        val end = node.children[node.children.size - 2].endOffset
        content.substring(start, end).replaceIndent()
    } else {
        content
    }

private fun blockCode(content: String, node: org.intellij.markdown.ast.ASTNode): String {
    if (node.children.isEmpty()) return content
    val start = node.children[0].startOffset
    val end = node.children[node.children.size - 1].endOffset
    return content.substring(start, end).replaceIndent()
}

@Composable
private fun CopyableCode(code: String) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val copyLabel = stringResource(if (copied) R.string.copied else R.string.copy)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
            .padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(code))
                copied = true
            }) {
                Text(copyLabel, style = MaterialTheme.typography.labelSmall)
            }
        }
        Text(
            code,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun TodoPanel(todos: List<TodoUi>) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .clickable { expanded = !expanded }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val done = todos.count { it.status == "completed" }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Icons.Filled.CheckCircle,
                null,
                Modifier.height(16.dp).width(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(R.string.todo_title, done, todos.size),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (expanded) "▲" else "▼",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded) {
            todos.forEach { todo ->
                TodoRow(todo)
            }
        }
    }
}

@Composable
private fun TodoRow(todo: TodoUi) {
    val done = todo.status == "completed"
    val color = when (todo.status) {
        "completed" -> MaterialTheme.colorScheme.primary
        "in_progress" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            when (todo.status) {
                "completed" -> "✓"
                "in_progress" -> "…"
                else -> "○"
            },
            style = MaterialTheme.typography.bodySmall,
            color = color,
        )
        Text(
            todo.content,
            style = MaterialTheme.typography.bodySmall,
            color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            textDecoration = if (done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
        )
    }
}

@Composable
private fun MessageBubble(
    msg: ChatMessage,
    userColor: Long = -1L,
    assistantColor: Long = -1L,
    cumulativeTokens: Long? = null,
    deltaTokens: Long? = null,
    sessionElapsed: Long? = null,
    responseTime: Long? = null,
) {
    if (msg.role == "system") {
        SystemNotice(msg.text)
        return
    }
    val isUser = msg.role == "user"
    val isError = msg.role == "error"
    val custom = when {
        isUser && userColor >= 0 -> Color(userColor)
        !isUser && !isError && assistantColor >= 0 -> Color(assistantColor)
        else -> null
    }
    val background = when {
        isUser -> custom ?: MaterialTheme.colorScheme.primaryContainer
        isError -> MaterialTheme.colorScheme.errorContainer
        else -> custom ?: MaterialTheme.colorScheme.surfaceVariant
    }
    val onBackground = if (custom != null) {
        val luminance = (0.299f * background.red + 0.587f * background.green + 0.114f * background.blue)
        if (luminance > 0.5f) Color.Black else Color.White
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val shape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = if (isUser) 16.dp else 4.dp,
        bottomEnd = if (isUser) 4.dp else 16.dp,
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(if (isUser) 0.85f else 0.95f),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!isUser && msg.parts.isNotEmpty()) {
                Icon(
                    Icons.Filled.Build,
                    contentDescription = stringResource(R.string.chat_agent),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(background, shape)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            ) {
                if (!isUser && msg.reasoning != null) {
                    ReasoningBlock(msg.reasoning)
                    if (msg.text.isNotBlank()) Spacer(Modifier.height(8.dp))
                }
                val tools = msg.parts.filter { it.type == "tool" }
                if (!isUser && tools.isNotEmpty()) {
                    tools.forEach { ToolPart(it) }
                    if (msg.text.isNotBlank()) Spacer(Modifier.height(8.dp))
                }
                if (msg.text.isNotBlank()) {
                    if (isUser) {
                        Text(
                            msg.text.trim(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = onBackground,
                        )
                    } else {
                        MarkdownMessage(msg.text.trim())
                    }
                }
                MessageMeta(
                    msg = msg,
                    cumulativeTokens = cumulativeTokens,
                    deltaTokens = deltaTokens,
                )
            }
        }
        if (sessionElapsed != null) {
            Text(
                stringResource(R.string.session_elapsed, sessionElapsed / 1000.0),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.align(
                    if (isUser) Alignment.End else Alignment.Start
                ).padding(top = 2.dp),
            )
        }
        if (responseTime != null && responseTime > 0L) {
            Text(
                stringResource(R.string.session_elapsed, responseTime / 1000.0),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.align(Alignment.Start).padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun MessageMeta(
    msg: ChatMessage,
    cumulativeTokens: Long? = null,
    deltaTokens: Long? = null,
) {
    val time = msg.time
    val parts = buildList {
        if (!msg.model.isNullOrBlank()) add(msg.model)
        if (cumulativeTokens != null) {
            if (deltaTokens != null && deltaTokens > 0) {
                add("$cumulativeTokens (+$deltaTokens)")
            } else {
                add("$cumulativeTokens")
            }
        } else {
            val tokens = msg.tokens
            if (tokens != null) {
                val total = tokens.total
                    ?: (tokens.input + tokens.output + tokens.reasoning)
                if (total > 0) add("${total} tok")
            }
        }
        if (time > 0) add(formatMillis(time))
    }
    if (parts.isEmpty()) return
    Text(
        parts.joinToString(" · "),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        fontFamily = FontFamily.Monospace,
        textAlign = TextAlign.End,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    )
}

private fun formatMillis(millis: Long): String {
    return java.time.Instant.ofEpochMilli(millis)
        .atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
}

@Composable
private fun SystemNotice(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

private data class ToolLabels(
    val ran: String,
    val edited: String,
)

@Composable
private fun ToolPart(part: PartUi) {
    var expanded by remember { mutableStateOf(false) }
    val title = part.toolTitle ?: part.tool ?: "tool"
    val status = part.toolState ?: "running"
    val labels = ToolLabels(
        ran = stringResource(R.string.tool_ran),
        edited = stringResource(R.string.tool_edited),
    )
    val details = buildToolDetails(part, labels)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .clickable { expanded = !expanded }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                Icons.Filled.Build,
                null,
                Modifier.height(14.dp).width(14.dp),
                tint = MaterialTheme.colorScheme.tertiary,
            )
            Text(
                when (status) {
                    "completed" -> "✓"
                    "error" -> "✗"
                    else -> "…"
                },
                style = MaterialTheme.typography.labelSmall,
                color = when (status) {
                    "completed" -> MaterialTheme.colorScheme.primary
                    "error" -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (expanded) "▲" else "▼",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (!part.toolInput.isNullOrBlank()) {
            SummaryRow(
                label = stringResource(R.string.tool_input),
                value = details.inputSummary,
                monospace = true,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
            )
        }
        if (details.outputPrefix != null) {
            Text(
                details.outputPrefix,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (details.outputRows.isNotEmpty()) {
            details.outputRows.forEach { SummaryRow(label = it.first, value = it.second, monospace = true) }
        }
        if (details.outputText != null) {
            Text(
                details.outputText,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (details.note != null) {
            Text(
                details.note,
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, monospace: Boolean = false, maxLines: Int = Int.MAX_VALUE) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = if (monospace) FontFamily.Monospace else null,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private data class ToolDetails(
    val inputSummary: String = "",
    val outputRows: List<Pair<String, String>> = emptyList(),
    val outputText: String? = null,
    val outputPrefix: String? = null,
    val note: String? = null,
)

private fun buildToolDetails(part: PartUi, labels: ToolLabels): ToolDetails {
    val tool = part.tool ?: ""
    val inputObj = part.toolInput?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
    val inputSummary = inputObj?.let { summarizeInput(tool, it) }
        ?: part.toolInput?.let { prettyText(it) } ?: ""

    val outputStr = part.toolOutput ?: return ToolDetails(inputSummary = inputSummary)
    val trimmed = outputStr.trim()

    return when (tool) {
        "bash" -> bashDetails(inputObj, trimmed, outputStr, labels)
        "read" -> readDetails(tool, trimmed, outputStr)
        "write", "edit" -> fileEditDetails(tool, inputObj, trimmed, outputStr, labels)
        "websearch" -> webSearchDetails(trimmed)
        "webfetch" -> webFetchDetails(inputObj, trimmed)
        else -> genericDetails(trimmed, outputStr)
    }
}

private fun summarizeInput(tool: String, o: JsonObject): String {
    val cmd = o["command"]?.jsonPrimitive?.contentOrNull
    if (cmd != null) return cmd
    val query = o["query"]?.jsonPrimitive?.contentOrNull
    if (query != null) return query
    val file = o["file"]?.jsonPrimitive?.contentOrNull ?: o["filePath"]?.jsonPrimitive?.contentOrNull
    if (file != null) return file
    val url = o["url"]?.jsonPrimitive?.contentOrNull
    if (url != null) return url
    val path = o["path"]?.jsonPrimitive?.contentOrNull
    if (path != null) return path
    return prettyText(o.toString())
}

private fun bashDetails(inputObj: JsonObject?, trimmed: String, raw: String, labels: ToolLabels): ToolDetails {
    val cmd = inputObj?.get("command")?.jsonPrimitive?.contentOrNull
    val prefix = if (cmd != null) "${labels.ran} $" else null
    return ToolDetails(
        inputSummary = cmd ?: prettyText(inputObj?.toString().orEmpty()),
        outputPrefix = prefix,
        outputText = summarizeText(trimmed),
        note = if (cmd != null) null else noteFor(trimmed),
    )
}

private fun readDetails(tool: String, trimmed: String, raw: String): ToolDetails {
    return ToolDetails(
        outputText = summarizeText(trimmed),
        note = "(content length ${trimmed.length} chars)",
    )
}

private fun fileEditDetails(tool: String, inputObj: JsonObject?, trimmed: String, raw: String, labels: ToolLabels): ToolDetails {
    val file = inputObj?.get("file")?.jsonPrimitive?.contentOrNull
        ?: inputObj?.get("filePath")?.jsonPrimitive?.contentOrNull
        ?: inputObj?.get("path")?.jsonPrimitive?.contentOrNull
    return ToolDetails(
        inputSummary = inputObj?.let { prettyJson(it) } ?: "",
        outputPrefix = if (file != null) "${labels.edited} $file" else null,
        outputText = summarizeText(trimmed),
    )
}

private fun webSearchDetails(trimmed: String): ToolDetails {
    val rows = mutableListOf<Pair<String, String>>()
    val elems = runCatching { Json.parseToJsonElement(trimmed) }.getOrNull()
    if (elems is JsonArray) {
        elems.forEachIndexed { i, el ->
            val title = (el as? JsonObject)?.get("title")?.jsonPrimitive?.contentOrNull
            val url = (el as? JsonObject)?.get("url")?.jsonPrimitive?.contentOrNull
            if (title != null || url != null) {
                rows.add("#${i + 1}" to (title ?: url ?: ""))
            }
        }
    }
    return if (rows.isNotEmpty()) ToolDetails(outputRows = rows)
    else ToolDetails(outputText = summarizeText(trimmed))
}

private fun webFetchDetails(inputObj: JsonObject?, trimmed: String): ToolDetails {
    val url = inputObj?.get("url")?.jsonPrimitive?.contentOrNull
    return ToolDetails(
        inputSummary = inputObj?.let { prettyText(it.toString()) } ?: "",
        outputPrefix = if (url != null) "↗ $url" else null,
        outputText = summarizeText(trimmed),
    )
}

private fun genericDetails(trimmed: String, raw: String): ToolDetails {
    val obj = runCatching { Json.parseToJsonElement(trimmed).jsonObject }.getOrNull()
    return if (obj != null) {
        val rows = obj.entries.mapNotNull { (k, v) ->
            val s = if (v is JsonPrimitive) v.contentOrNull else null
            if (s != null && s.length < 120) k to s else null
        }.take(8)
        ToolDetails(outputRows = rows, note = if (trimmed.length > rows.size * 120) "(object summary)" else null)
    } else {
        ToolDetails(outputText = summarizeText(trimmed))
    }
}

private fun summarizeText(s: String, max: Int = 400): String =
    if (s.length <= max) s else s.take(max) + "\n... (${s.length} chars)"

private fun noteFor(s: String): String =
    when {
        s.startsWith("[") -> "array of ${countJsonArray(s)} items"
        s.startsWith("{") -> "object"
        else -> "output length ${s.length} chars"
    }

private fun countJsonArray(s: String): Int =
    runCatching { (Json.parseToJsonElement(s) as JsonArray).size }.getOrDefault(0)

private fun prettyText(s: String): String =
    runCatching { Json.parseToJsonElement(s).jsonObject }.getOrNull()?.toString()?.let { it } ?: s

private val prettyJsonInstance = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
    ignoreUnknownKeys = true
}

private fun prettyJson(obj: JsonObject): String =
    runCatching { prettyJsonInstance.encodeToString(JsonElement.serializer(), obj) }
        .getOrNull() ?: obj.toString()

@Composable
private fun ReasoningBlock(reasoning: String) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Thinking",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            reasoning.trim(),
            style = MaterialTheme.typography.bodySmall,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun TokenStatsBar(
    viewModel: MainViewModel,
    tokens: Tokens?,
    promptTokens: Long,
    contextWindow: Long,
    shortTokens: Boolean,
) {
    if (tokens == null && contextWindow <= 0) return
    val input = tokens?.input ?: 0L
    val output = tokens?.output ?: 0L
    val reasoning = tokens?.reasoning ?: 0L
    val total = input + output + reasoning
    val ctx = if (promptTokens > 0) promptTokens else total
    val ratio = if (contextWindow > 0) (ctx.toFloat() / contextWindow).coerceIn(0f, 1f) else 0f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "in ${formatTokens(input, shortTokens)} · out ${formatTokens(output, shortTokens)} · ${formatTokens(total, shortTokens)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
            )
            if (contextWindow > 0 && total > 0) {
                Text(
                    "${formatTokens(ctx, shortTokens)} / ${formatTokens(contextWindow, shortTokens)} (${(ratio * 100).toInt()}%)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        if (contextWindow > 0 && total > 0) {
            LinearProgressIndicator(
                progress = { ratio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
            )
        }
    }
}

@Composable
private fun ModelSwitcher(
    models: List<ModelInfo>,
    currentModelId: String?,
    onSelect: (ModelInfo) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val current = models.firstOrNull { it.id == currentModelId } ?: models.firstOrNull()
    Box {
        Text(
            text = current?.id ?: stringResource(R.string.model_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clickable { expanded = true }
                .padding(vertical = 2.dp),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            models.forEach { model ->
                val selected = model.id == currentModelId
                DropdownMenuItem(
                    text = {
                        Text(
                            model.id ?: "",
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(model)
                    },
                )
            }
        }
    }
}

@Composable
private fun SessionActionsMenu(
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { menuOpen = true }) {
            Icon(Icons.Filled.MoreVert, stringResource(R.string.settings_title))
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.session_rename)) },
                onClick = {
                    menuOpen = false
                    showRename = true
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.session_delete)) },
                onClick = {
                    menuOpen = false
                    showDelete = true
                },
            )
        }
    }

    if (showRename) {
        val title = stringResource(R.string.rename_title)
        var name by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text(title) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text(stringResource(R.string.rename_placeholder)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRename = false
                        onRename(name)
                    },
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) { Text(stringResource(R.string.chat_reject)) }
            },
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(stringResource(R.string.session_delete_title)) },
            text = { Text(stringResource(R.string.session_delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDelete = false
                        onDelete()
                    },
                ) { Text(stringResource(R.string.session_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text(stringResource(R.string.chat_reject)) }
            },
        )
    }
}

@Composable
private fun FileBrowserSheet(
    root: String,
    viewModel: MainViewModel,
    onClose: () -> Unit,
) {
    var path by rememberSaveable { mutableStateOf(root) }
    var files by remember { mutableStateOf<List<FileNode>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(path) {
        loading = true
        files = try {
            viewModel.listFiles(path)
        } catch (_: Exception) {
            emptyList()
        }
        loading = false
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                fileLabel(path),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            if (loading) {
                CircularProgressIndicator(Modifier.height(16.dp).width(16.dp), strokeWidth = 2.dp)
            }
        }
        Spacer(Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
            if (path != root) {
                item(key = "up") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { path = parentOf(path, root) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Filled.Folder, null, Modifier.height(18.dp).width(18.dp), tint = MaterialTheme.colorScheme.tertiary)
                        Text(stringResource(R.string.files_up), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (files.isEmpty() && !loading) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.files_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }
            items(files, key = { it.path }) { node ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            when (node.type) {
                                "directory" -> path = node.path
                                else -> viewModel.openFileInChat(node.path)
                            }
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        if (node.type == "directory") Icons.Filled.Folder else Icons.Filled.InsertDriveFile,
                        null,
                        Modifier.height(18.dp).width(18.dp),
                        tint = if (node.type == "directory") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(node.name, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

private fun parentOf(path: String, root: String): String {
    val p = path.trimEnd('/')
    val r = root.trimEnd('/')
    if (p == r) return path
    val idx = p.lastIndexOf('/')
    if (idx <= 0) return "/"
    return p.substring(0, idx)
}

private fun fileLabel(path: String): String =
    path.substringAfterLast('/').ifBlank { path }
