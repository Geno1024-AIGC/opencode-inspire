package com.example.opencodeclient.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.ui.res.painterResource
import com.example.opencodeclient.R
import com.example.opencodeclient.data.Message
import com.example.opencodeclient.data.Session
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

import com.example.opencodeclient.data.FileNode
import com.example.opencodeclient.data.ModelInfo
import com.example.opencodeclient.data.QuestionRequest
import com.example.opencodeclient.data.StoredHistoryStats
import com.example.opencodeclient.data.Tokens
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
    val sessionTotalElapsed by viewModel.sessionTotalElapsed.collectAsStateWithLifecycle()
    val pendingQuestions by viewModel.pendingQuestions.collectAsStateWithLifecycle()
    val todos by viewModel.todos.collectAsStateWithLifecycle()
    var todosHidden by rememberSaveable { mutableStateOf(false) }
    var lastTodosKey by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(todos) {
        val key = todos.joinToString("\u0001") { it.content }
        if (key.isNotEmpty() && key != lastTodosKey) {
            todosHidden = false
        }
        lastTodosKey = key
    }
    val commands by viewModel.commands.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val currentModelId by viewModel.currentModelId.collectAsStateWithLifecycle()
    val pendingPermissions by viewModel.pendingPermissions.collectAsStateWithLifecycle()
    val shortTokens by viewModel.shortTokens.collectAsStateWithLifecycle()
    val userBubbleColor by viewModel.userBubbleColor.collectAsStateWithLifecycle()
    val assistantBubbleColor by viewModel.assistantBubbleColor.collectAsStateWithLifecycle()
    val collapsedMessageIds by viewModel.collapsedMessageIds.collectAsStateWithLifecycle()
    val exportMarkdown by viewModel.exportMarkdown.collectAsStateWithLifecycle()
    val historyStats by viewModel.historyStats.collectAsStateWithLifecycle()
    val computingHistory by viewModel.computingHistory.collectAsStateWithLifecycle()
    val historyProgress by viewModel.historyProgress.collectAsStateWithLifecycle()
    val storedStats by viewModel.storedStats.collectAsStateWithLifecycle()
    val autoTiming by viewModel.autoTiming.collectAsStateWithLifecycle()
    val effectiveTotalElapsed: Long? = run {
        val storedMs = activeSession?.let { storedStats[it.id]?.totalElapsed } ?: 0L
        val live = sessionTotalElapsed
        if (storedMs > 0L) storedMs else live
    }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var input by rememberSaveable { mutableStateOf("") }
    var commandMenuOpen by remember { mutableStateOf(false) }
    var recentCommands by rememberSaveable { mutableStateOf(listOf<String>()) }
    var showFiles by rememberSaveable { mutableStateOf(false) }
    var userScrolledAway by remember { mutableStateOf(false) }
    var rawMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var previewUrl by remember { mutableStateOf<String?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var attachedFile by remember { mutableStateOf<android.net.Uri?>(null) }
    var showSessionDetails by remember { mutableStateOf(false) }
    val attachLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> attachedFile = uri }
    val selectedFileName = attachedFile?.let { uri ->
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        val name = cursor?.use {
            val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && it.moveToFirst()) it.getString(idx) else null
        }
        name ?: uri.lastPathSegment
    }

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

    LaunchedEffect(exportMarkdown) {
        exportMarkdown?.let { markdown ->
            val file = java.io.File(context.cacheDir, "chat_export.md")
            file.writeText(markdown)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/markdown"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "导出对话"))
            viewModel.clearExportMarkdown()
        }
    }

    var now by remember { mutableLongStateOf(0L) }
    val lastUserTime = messages.lastOrNull { it.role == "user" && it.text.isNotBlank() }?.time ?: 0L
    LaunchedEffect(sending, lastUserTime) {
        if (sending && lastUserTime > 0L) {
            while (isActive) {
                now = System.currentTimeMillis()
                delay(1000)
            }
        } else {
            now = 0L
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.statusBarsPadding(),
            topBar = {
                TopAppBar(
                    title = {
                        Column(
                            modifier = if (activeSession != null) {
                                Modifier.clickable { showSessionDetails = true }
                            } else {
                                Modifier
                            },
                        ) {
                            Text(
                                activeSession?.title?.ifBlank { stringResource(R.string.app_name) } ?: stringResource(R.string.app_name),
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
                            IconButton(onClick = { searchActive = !searchActive }) {
                                Icon(Icons.Filled.Search, stringResource(R.string.search))
                            }
                            IconButton(onClick = { showFiles = true }) {
                                Icon(painterResource(R.drawable.ic_folder), stringResource(R.string.files_title))
                            }
                        }
                        IconButton(onClick = { viewModel.refreshSession() }) {
                            Icon(Icons.Filled.Refresh, stringResource(R.string.chat_refresh))
                        }
                        if (sending) {
                            IconButton(onClick = { viewModel.abort() }) {
                                Icon(painterResource(R.drawable.ic_stop), stringResource(R.string.chat_abort))
                            }
                        }
                    },
                )
            },
            floatingActionButton = {
                if (sending) {
                    FloatingActionButton(onClick = { viewModel.abort() }) {
                    Icon(painterResource(R.drawable.ic_stop), stringResource(R.string.chat_abort))
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
            TokenStatsBar(viewModel = viewModel, tokens = sessionTokens, promptTokens = promptTokens, contextWindow = contextWindow, shortTokens = shortTokens, totalElapsed = effectiveTotalElapsed, messageCount = activeSession?.let { storedStats[it.id]?.messageCount })
            if (searchActive) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    placeholder = { Text(stringResource(R.string.search)) },
                    singleLine = true,
                )
            }
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
                val filteredMessages = if (searchActive && searchQuery.isNotBlank()) {
                    reversedMessages.filter { msg ->
                        msg.text.contains(searchQuery, ignoreCase = true) ||
                        msg.reasoning?.contains(searchQuery, ignoreCase = true) == true ||
                        msg.parts.any { it.toolOutput?.contains(searchQuery, ignoreCase = true) == true }
                    }
                } else {
                    reversedMessages
                }
                itemsIndexed(filteredMessages, key = { idx, item -> item.id }) { index, msg ->
                    val prevCumulative = if (index + 1 < reversedMessages.size) reversedMessages[index + 1].cumulativeTokens else null
                    val responseTime = if (sending && index == 0 && msg.role == "assistant" && lastUserTime > 0L) {
                        (now - lastUserTime).coerceAtLeast(0L)
                    } else if (msg.role == "assistant" && msg.time > 0L) {
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
                        collapsed = msg.id in collapsedMessageIds,
                        onToggleCollapse = { viewModel.toggleMessageCollapsed(msg.id) },
                        onShowRaw = { rawMessage = msg },
                        onRegenerate = { viewModel.regenerate() },
                        onOpenLink = { previewUrl = it },
                    )
                }
            }

            if (todos.isNotEmpty() && !todosHidden) {
                TodoPanel(todos = todos, onDismiss = { todosHidden = true })
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
                    val showCommands = commandMenuOpen && input.startsWith("/") && !trimmed.contains(" ")
                    if (attachedFile != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = "📎 " + (selectedFileName ?: ""),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "✕",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clickable { attachedFile = null }
                                    .padding(horizontal = 4.dp),
                            )
                        }
                    }
                    OutlinedTextField(
                        value = input,
                        onValueChange = {
                            input = it
                            if (it.startsWith("/") && !it.trimStart().contains(" ")) commandMenuOpen = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.chat_placeholder)) },
                    )
                    if (showCommands) {
                        val orderedCommands = buildList {
                            recentCommands.forEach { name ->
                                commands.firstOrNull { it.name == name }?.let { add(it) }
                            }
                            commands.forEach { if (it.name !in recentCommands) add(it) }
                        }
                        DropdownMenu(
                            expanded = true,
                            onDismissRequest = { commandMenuOpen = false },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (orderedCommands.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.slash_no_commands)) },
                                    onClick = {},
                                )
                            } else {
                                orderedCommands.forEach { cmd ->
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
                                            recentCommands = listOf(cmd.name) + recentCommands.filter { it != cmd.name }
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
                    onClick = { attachLauncher.launch("*/*") },
                    modifier = Modifier.padding(bottom = 4.dp),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_insert_drive_file),
                        contentDescription = stringResource(R.string.attach_file),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = {
                        val uri = attachedFile
                        if (uri != null) {
                            val content = runCatching {
                                context.contentResolver.openInputStream(uri)?.use {
                                    it.readBytes().decodeToString()
                                }.orEmpty()
                            }.getOrDefault("")
                            val name = selectedFileName ?: "attachment"
                            viewModel.sendWithFile(input.trim(), name, content)
                        } else {
                            viewModel.send(input.trim())
                        }
                        attachedFile = null
                        input = ""
                     },
                     enabled = input.isNotBlank() || attachedFile != null,
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
                    locationDir = directory,
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
         PermissionDialog(permission = permission, directory = activeSession?.directory) { reply ->
             viewModel.replyPermission(permission, reply)
         }
     }

    rawMessage?.let { msg ->
        RawMessageDialog(msg = msg, onDismiss = { rawMessage = null })
    }

    previewUrl?.let { url ->
        LinkPreviewSheet(url = url, onDismiss = { previewUrl = null })
    }

    if (computingHistory) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelHistoryStats() },
            title = { Text(stringResource(R.string.history_stats_computing)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    historyProgress?.let { p ->
                        Text(
                            stringResource(R.string.history_stats_progress_msgs, p.fetched),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (p.lastTimestamp > 0L) {
                            Text(
                                stringResource(R.string.history_stats_progress_upto, formatMillis(p.lastTimestamp)),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    } ?: Text(
                        stringResource(R.string.history_stats_computing_sub),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { viewModel.cancelHistoryStats() }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    historyStats?.let { stats ->
        HistoryStatsDialog(
            stats = stats,
            onDismiss = { viewModel.dismissHistoryStats() },
        )
    }

    val session = activeSession
    if (showSessionDetails && session != null) {
        SessionDetailsScreen(
            viewModel = viewModel,
            session = session,
            sessionTokens = sessionTokens,
            contextWindow = contextWindow,
            historyStats = historyStats,
            storedStats = storedStats[session.id],
            autoTiming = autoTiming,
            shortTokens = shortTokens,
            onBack = { showSessionDetails = false },
        )
    }
    }
}

@Composable
private fun RawMessageDialog(
    msg: ChatMessage,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.message_raw_title)) },
        text = {
            SelectionContainer {
                Text(
                    buildRawMessageText(msg),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) }
        },
    )
}

private fun buildRawMessageText(msg: ChatMessage): String = buildString {
    appendLine("id: ${msg.id}")
    appendLine("role: ${msg.role}")
    if (!msg.model.isNullOrBlank()) appendLine("model: ${msg.model}")
    if (msg.time > 0) appendLine("time: ${msg.time}")
    msg.tokens?.let { t ->
        appendLine("tokens: input=${t.input} output=${t.output} reasoning=${t.reasoning} total=${t.total}")
    }
    if (!msg.reasoning.isNullOrBlank()) {
        appendLine("--- reasoning ---")
        appendLine(msg.reasoning)
    }
    if (msg.text.isNotBlank()) {
        appendLine("--- text ---")
        appendLine(msg.text)
    }
    appendLine("--- parts (${msg.parts.size}) ---")
    msg.parts.forEachIndexed { i, p ->
        appendLine("[$i] type=${p.type}")
        if (p.text != null) appendLine("    text=${p.text}")
        if (p.tool != null) appendLine("    tool=${p.tool}")
        if (p.toolTitle != null) appendLine("    toolTitle=${p.toolTitle}")
        if (p.toolState != null) appendLine("    toolState=${p.toolState}")
        if (p.toolInput != null) appendLine("    toolInput=${p.toolInput}")
        if (p.toolOutput != null) appendLine("    toolOutput=${p.toolOutput}")
    }
}

@Composable
private fun HistoryStatsDialog(
    stats: HistoryStats,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.history_stats_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (stats.computed) {
                    StatText(stringResource(R.string.history_stats_total, stats.totalElapsed / 1000.0))
                    Text(
                        stringResource(R.string.history_stats_messages, stats.messageCount, stats.userMessages, stats.assistantMessages),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    if (stats.toolCalls > 0L) {
                        Text(
                            stringResource(R.string.history_stats_tools, stats.toolCalls),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (stats.lastTimestamp > 0L) {
                        Text(
                            stringResource(R.string.history_stats_latest, formatMillis(stats.lastTimestamp)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (stats.firstMessages.isNotEmpty()) {
                        Text(
                            stringResource(R.string.history_stats_first5),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            stats.firstMessages.forEachIndexed { idx, t ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        "${idx + 1}.",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = MonoFontFamily,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        t,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                } else if (stats.fallbackSpanMs > 0L) {
                    Text(
                        stringResource(R.string.history_stats_fallback_title),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        stringResource(R.string.history_stats_fallback, stats.fallbackSpanMs / 1000.0),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = MonoFontFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        stringResource(R.string.history_stats_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    if (!stats.error.isNullOrBlank() && stats.error != "empty") {
                        Text(
                            stats.error,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) }
        },
    )
}

@Composable
private fun PermissionDialog(
    permission: com.example.opencodeclient.data.PermissionRequest,
    directory: String?,
    onReply: (String) -> Unit,
) {
    val typeLabel = when (permission.permission) {
        "fileRead" -> stringResource(R.string.permission_read_files)
        "fileWrite" -> stringResource(R.string.permission_write_files)
        "bash", "subprocess" -> stringResource(R.string.permission_bash)
        "edit" -> stringResource(R.string.permission_edit)
        "webfetch" -> stringResource(R.string.permission_webfetch)
        "tool", "mcp" -> stringResource(R.string.permission_tool)
        else -> permission.permission
    }
    val paths = permission.patterns.map { it.trim() }.filter { it.isNotBlank() }
    val outsideText = stringResource(R.string.permission_outside_project)
    val pathsTitle = stringResource(R.string.permission_paths)
    AlertDialog(
        onDismissRequest = { onReply("reject") },
        title = { Text(stringResource(R.string.permission_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.height(18.dp).width(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(typeLabel, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                }
                if (paths.isNotEmpty()) {
                    Text(pathsTitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    paths.forEach { path ->
                        val outside = isOutsideProjectDir(path, directory)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Icon(
                                Icons.Filled.Warning,
                                contentDescription = null,
                                tint = if (outside) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.height(14.dp).width(14.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                path,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = MonoFontFamily,
                                color = if (outside) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (outside) {
                            Text(
                                outsideText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(start = 20.dp),
                            )
                        }
                    }
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

private fun isOutsideProjectDir(path: String, directory: String?): Boolean {
    if (directory.isNullOrBlank()) return false
    val p = path.trimEnd('/')
    val d = directory.trimEnd('/')
    return p != d && !p.startsWith("$d/")
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
private fun MarkdownMessage(content: String, color: Color = Color.Unspecified) {
    if (color != Color.Unspecified) {
        Box(modifier = Modifier.fillMaxWidth()) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides color
            ) {
                com.example.opencodeclient.ui.MarkdownMessage(content)
            }
        }
    } else {
        com.example.opencodeclient.ui.MarkdownMessage(content)
    }
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
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun TodoPanel(todos: List<TodoUi>, onDismiss: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .combinedClickable(
                onClick = { expanded = !expanded },
                onLongClick = onDismiss,
            )
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
    collapsed: Boolean = false,
    onToggleCollapse: () -> Unit = {},
    onShowRaw: () -> Unit = {},
    onRegenerate: () -> Unit = {},
    onOpenLink: (String) -> Unit = {},
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
                    painterResource(R.drawable.ic_build),
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
                if (collapsed) {
                    Text(
                        stringResource(R.string.message_collapsed),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                } else {
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
                            SelectionContainer {
                                Text(
                                    msg.text.trim(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = onBackground,
                                )
                            }
                        } else {
                            MarkdownMessage(msg.text.trim(), color = onBackground)
                        }
                    }
                }
                val links = extractMarkdownLinks(msg.text)
                if (links.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    links.forEach { link ->
                        Text(
                            text = "🔗 " + (link.takeLast(48)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .clickable { onOpenLink(link) }
                                .padding(vertical = 1.dp),
                        )
                    }
                }
                MessageMeta(
                    msg = msg,
                    cumulativeTokens = cumulativeTokens,
                    deltaTokens = deltaTokens,
                    collapsed = collapsed,
                    onToggleCollapse = onToggleCollapse,
                    onShowRaw = onShowRaw,
                )
            }
        }
        if (sessionElapsed != null) {
            Text(
                stringResource(R.string.session_elapsed, sessionElapsed / 1000.0),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                fontFamily = MonoFontFamily,
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
                fontFamily = MonoFontFamily,
                modifier = Modifier.align(Alignment.Start).padding(top = 2.dp),
            )
        }
        if (!isUser && !collapsed) {
            Text(
                text = stringResource(R.string.regenerate),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier
                    .align(Alignment.Start)
                    .clickable { onRegenerate() }
                    .padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun MessageMeta(
    msg: ChatMessage,
    cumulativeTokens: Long? = null,
    deltaTokens: Long? = null,
    collapsed: Boolean = false,
    onToggleCollapse: () -> Unit = {},
    onShowRaw: () -> Unit = {},
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
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            parts.joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            fontFamily = MonoFontFamily,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = onToggleCollapse,
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                painterResource(if (collapsed) R.drawable.ic_expand_more else R.drawable.ic_expand_less),
                contentDescription = stringResource(R.string.message_collapse_toggle),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
        }
        IconButton(
            onClick = onShowRaw,
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                Icons.Filled.Info,
                contentDescription = stringResource(R.string.message_raw),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
        }
    }
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
                painterResource(R.drawable.ic_build),
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
                fontFamily = MonoFontFamily,
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
            fontFamily = if (monospace) MonoFontFamily else null,
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
        "grep", "rg" -> grepDetails(trimmed, outputStr)
        else -> genericDetails(trimmed, outputStr)
    }
}

private fun summarizeInput(tool: String, o: JsonObject): String {
    val cmd = o["command"]?.jsonPrimitive?.contentOrNull
    if (cmd != null) return cmd
    val query = o["query"]?.jsonPrimitive?.contentOrNull
    if (query != null) {
        return when (tool) {
            "websearch" -> {
                val num = o["numResults"]?.jsonPrimitive?.contentOrNull
                val type = o["type"]?.jsonPrimitive?.contentOrNull
                buildString {
                    append("🔍 $query")
                    if (num != null) append("  ($num results)")
                    if (type != null && type != "auto") append("  [$type]")
                }
            }
            else -> query
        }
    }
    val file = o["file"]?.jsonPrimitive?.contentOrNull ?: o["filePath"]?.jsonPrimitive?.contentOrNull
    if (file != null) {
        return when (tool) {
            "read" -> {
                val offset = o["offset"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                val limit = o["limit"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                buildString {
                    append(file)
                    if (offset != null) append(":$offset")
                    if (limit != null) append(" (${limit} lines)")
                }
            }
            "edit" -> {
                val old = o["oldString"]?.jsonPrimitive?.contentOrNull ?: ""
                val new = o["newString"]?.jsonPrimitive?.contentOrNull ?: ""
                buildString {
                    append(file)
                    if (old.isNotBlank()) append("\n- ${old.take(80)}${if (old.length > 80) "..." else ""}")
                    if (new.isNotBlank()) append("\n+ ${new.take(80)}${if (new.length > 80) "..." else ""}")
                }
            }
            "write" -> {
                val content = o["content"]?.jsonPrimitive?.contentOrNull ?: ""
                "$file (${content.length} chars)"
            }
            else -> file
        }
    }
    val url = o["url"]?.jsonPrimitive?.contentOrNull
    if (url != null) return url
    val pattern = o["pattern"]?.jsonPrimitive?.contentOrNull
    if (pattern != null) {
        val include = o["include"]?.jsonPrimitive?.contentOrNull ?: o["glob"]?.jsonPrimitive?.contentOrNull
        val gpath = o["path"]?.jsonPrimitive?.contentOrNull
        return buildString {
            append("🔎 $pattern")
            if (!include.isNullOrBlank() && include != "**/*") append("  (in $include)")
            if (!gpath.isNullOrBlank()) append("  @ $gpath")
        }
    }
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
        note = "${trimmed.length} chars",
    )
}

private fun fileEditDetails(tool: String, inputObj: JsonObject?, trimmed: String, raw: String, labels: ToolLabels): ToolDetails {
    val file = inputObj?.get("file")?.jsonPrimitive?.contentOrNull
        ?: inputObj?.get("filePath")?.jsonPrimitive?.contentOrNull
        ?: inputObj?.get("path")?.jsonPrimitive?.contentOrNull
    return ToolDetails(
        outputPrefix = if (file != null) "${labels.edited} $file" else null,
        outputText = summarizeText(trimmed),
    )
}

private fun webSearchDetails(trimmed: String): ToolDetails {
    val rows = mutableListOf<Pair<String, String>>()
    var extraNote: String? = null
    val elems = runCatching { Json.parseToJsonElement(trimmed) }.getOrNull()
    if (elems is JsonArray) {
        elems.take(8).forEachIndexed { i, el ->
            val obj = el as? JsonObject
            val title = obj?.get("title")?.jsonPrimitive?.contentOrNull
            val url = obj?.get("url")?.jsonPrimitive?.contentOrNull
            val snippet = obj?.get("snippet")?.jsonPrimitive?.contentOrNull
                ?: obj?.get("excerpt")?.jsonPrimitive?.contentOrNull
                ?: obj?.get("description")?.jsonPrimitive?.contentOrNull
            if (title != null || url != null) {
                val display = buildString {
                    append(title ?: url ?: "")
                    if (snippet != null && snippet.isNotBlank()) {
                        append("\n")
                        append(snippet.take(120))
                        if (snippet.length > 120) append("…")
                    }
                }
                rows.add("#${i + 1}" to display)
            }
        }
        if (elems.size > 8) extraNote = "+${elems.size - 8} more results"
    }
    return if (rows.isNotEmpty()) ToolDetails(outputRows = rows, note = extraNote)
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

private fun grepDetails(trimmed: String, raw: String): ToolDetails {
    val lines = trimmed.lineSequence().filter { it.isNotBlank() }.toList()
    val note = if (lines.isEmpty()) null else "${lines.size} matches"
    return ToolDetails(outputText = summarizeText(trimmed), note = note)
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
        SelectionContainer {
            Text(
                reasoning.trim(),
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = MonoFontFamily,
            )
        }
    }
}

@Composable
private fun TokenStatsBar(
    viewModel: MainViewModel,
    tokens: Tokens?,
    promptTokens: Long,
    contextWindow: Long,
    shortTokens: Boolean,
    totalElapsed: Long? = null,
    messageCount: Long? = null,
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        TokenStatLine(label = "in", value = input, short = shortTokens)
                        TokenStatLine(label = "out", value = output, short = shortTokens)
                        TokenStatLine(label = "infer", value = reasoning, short = shortTokens)
                        TokenStatLine(label = "crd", value = tokens?.cache?.read ?: 0L, short = shortTokens)
                        TokenStatLine(label = "cwr", value = tokens?.cache?.write ?: 0L, short = shortTokens)
                    }
                    Text(
                        formatTokens(total, shortTokens),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = MonoFontFamily,
                    )
                }
                if (contextWindow > 0 && (total > 0 || promptTokens > 0)) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(
                        "${formatTokens(ctx, shortTokens)} / ${formatTokens(contextWindow, shortTokens)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = MonoFontFamily,
                        maxLines = 1,
                    )
                    Text(
                        "${(ratio * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = MonoFontFamily,
                    )
                }
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
        if (totalElapsed != null && totalElapsed > 0L || messageCount != null && messageCount > 0L) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 2.dp),
            ) {
                if (totalElapsed != null && totalElapsed > 0L) {
                    Text(
                        stringResource(R.string.session_total_elapsed, totalElapsed / 1000.0),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        fontFamily = MonoFontFamily,
                    )
                }
                if (messageCount != null && messageCount > 0L) {
                    Text(
                        stringResource(R.string.session_total_messages, messageCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        fontFamily = MonoFontFamily,
                    )
                }
            }
        }
    }
}

@Composable
private fun TokenStatLine(label: String, value: Long, short: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = MonoFontFamily,
        )
        Text(
            formatTokens(value, short),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = MonoFontFamily,
        )
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
private fun StatText(text: String, modifier: Modifier = Modifier, bold: Boolean = false) {
    Text(
        text,
        modifier = modifier,
        style = if (bold) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
        fontWeight = if (bold) FontWeight.Bold else null,
        color = if (bold) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun SessionDetailsScreen(
    viewModel: MainViewModel,
    session: Session,
    sessionTokens: Tokens?,
    contextWindow: Long,
    historyStats: HistoryStats?,
    storedStats: StoredHistoryStats?,
    autoTiming: Boolean,
    shortTokens: Boolean = true,
    onBack: () -> Unit,
) {
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    val tokens = sessionTokens ?: session.tokens
    val stored = storedStats
    val hasStored = stored != null && stored.totalElapsed > 0L
    val canIncremental = stored != null && !stored.lastMessageId.isNullOrEmpty()

    BackHandler { onBack() }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.chat_back))
                }
                Text(
                    stringResource(R.string.session_details_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            HorizontalDivider()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DetailRow(
                    label = stringResource(R.string.session_details_title_label),
                    value = session.title.ifBlank { stringResource(R.string.untitled_session) },
                    onClick = { showRename = true },
                )
                DetailRow(
                    label = stringResource(R.string.session_details_id),
                    value = session.id,
                    fontFamily = MonoFontFamily,
                    onCopyValue = session.id,
                )
                DetailRow(
                    label = stringResource(R.string.session_details_created),
                    value = if (session.time?.created != null && session.time!!.created > 0L)
                        formatMillis(session.time!!.created) else "—",
                    onCopyValue = if (session.time?.created != null && session.time!!.created > 0L)
                        session.time!!.created.toString() else null,
                )
                DetailRow(
                    label = stringResource(R.string.session_details_context),
                    value = if (contextWindow > 0) formatTokens(contextWindow) else "—",
                    onCopyValue = if (contextWindow > 0) contextWindow.toString() else null,
                )
                DetailRow(
                    label = stringResource(R.string.session_details_tokens),
                    value = buildString {
                        append(stringResource(R.string.session_details_tokens_in, formatTokens(tokens?.input ?: 0L, shortTokens)))
                        append(" / ")
                        append(stringResource(R.string.session_details_tokens_out, formatTokens(tokens?.output ?: 0L, shortTokens)))
                        if ((tokens?.reasoning ?: 0L) > 0) {
                            append(" / ")
                            append(stringResource(R.string.session_details_tokens_reasoning, formatTokens(tokens?.reasoning ?: 0L, shortTokens)))
                        }
                        append(" / ")
                        append(stringResource(R.string.session_details_tokens_cache_read, formatTokens(tokens?.cache?.read ?: 0L, shortTokens)))
                        if ((tokens?.cache?.write ?: 0L) > 0) {
                            append(" / ")
                            append(stringResource(R.string.session_details_tokens_cache_write, formatTokens(tokens?.cache?.write ?: 0L, shortTokens)))
                        }
                    },
                    onCopyValue = tokens?.let { "${it.input}/${it.output}/${it.reasoning}/${it.cache?.read ?: 0}/${it.cache?.write ?: 0}" },
                )

                HorizontalDivider()

                Text(stringResource(R.string.session_details_history), style = MaterialTheme.typography.titleMedium)

                val showElapsed = historyStats?.computed == true
                val displayElapsed = if (showElapsed) historyStats!!.totalElapsed else (stored?.totalElapsed ?: 0L)
                val displayLastTime = if (showElapsed) historyStats!!.lastTimestamp else (stored?.lastTimestamp ?: 0L)
                val hist = if (showElapsed) historyStats!! else null
                val displayMessages = hist?.messageCount ?: (stored?.messageCount ?: 0L)
                val displayUser = hist?.userMessages ?: (stored?.userMessages ?: 0L)
                val displayAssistant = hist?.assistantMessages ?: (stored?.assistantMessages ?: 0L)
                val displayTools = hist?.toolCalls ?: (stored?.toolCalls ?: 0L)

                if (displayElapsed > 0L) {
                    StatText(stringResource(R.string.session_details_history_total, displayElapsed / 1000.0))
                    if (displayMessages > 0L) {
                        Text(
                            stringResource(R.string.session_details_history_messages, displayMessages, displayUser, displayAssistant),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    if (displayTools > 0L) {
                        Text(
                            stringResource(R.string.session_details_history_tools, displayTools),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        stringResource(R.string.session_details_history_upto, formatMillis(displayLastTime)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        stringResource(R.string.session_details_history_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        stringResource(R.string.session_details_auto_update),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = autoTiming,
                        onCheckedChange = { viewModel.setAutoUpdateTiming(it) },
                    )
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.incrementHistoryStats() },
                        enabled = canIncremental,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.session_details_history_incremental))
                    }
                    OutlinedButton(
                        onClick = { viewModel.computeHistoryStats() },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.session_details_history_full))
                    }
                }

                HorizontalDivider()

                OutlinedButton(
                    onClick = { viewModel.compactSession() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.compact))
                }
                Button(
                    onClick = { viewModel.exportChatAsMarkdown() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.export_chat))
                }
                OutlinedButton(
                    onClick = { showDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(R.string.session_delete))
                }
            }
        }
    }

    if (showRename) {
        var name by rememberSaveable { mutableStateOf(session.title) }
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text(stringResource(R.string.rename_title)) },
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
                        viewModel.renameSession(name)
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
                        onBack()
                        viewModel.deleteSession()
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
private fun DetailRow(
    label: String,
    value: String,
    fontFamily: androidx.compose.ui.text.font.FontFamily? = null,
    onClick: (() -> Unit)? = null,
    onCopyValue: String? = null,
) {
    val context = LocalContext.current
    val copyable = onCopyValue != null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                when {
                    onClick != null -> Modifier.clickable(onClick = onClick)
                    copyable -> Modifier.clickable {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText(label, onCopyValue))
                        Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show()
                    }
                    else -> Modifier
                }
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(140.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = fontFamily),
            fontWeight = if (onClick != null || copyable) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (onClick != null) {
            Text(
                "✎",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        } else if (copyable) {
            Text(
                "⎘",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FileBrowserSheet(
    locationDir: String,
    viewModel: MainViewModel,
    onClose: () -> Unit,
) {
    var path by rememberSaveable { mutableStateOf("") }
    var files by remember { mutableStateOf<List<FileNode>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var previewPath by remember { mutableStateOf<String?>(null) }
    var previewName by remember { mutableStateOf<String?>(null) }

    val label = if (path.isEmpty())
        locationDir.trimEnd('/').substringAfterLast('/').ifBlank { locationDir }
    else
        path.trimEnd('/').substringAfterLast('/')

    LaunchedEffect(path) {
        loading = true
        files = try {
            viewModel.listSessionDirFiles(path, locationDir)
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
                label,
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
            if (path.isNotEmpty()) {
                item(key = "up") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { path = parentOfRelative(path) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_folder), null, Modifier.height(18.dp).width(18.dp), tint = MaterialTheme.colorScheme.tertiary)
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
                                else -> { previewPath = node.path; previewName = node.name }
                            }
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        if (node.type == "directory") painterResource(R.drawable.ic_folder) else painterResource(R.drawable.ic_insert_drive_file),
                        null,
                        Modifier.height(18.dp).width(18.dp),
                        tint = if (node.type == "directory") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(node.name, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }

    val currentPreview = previewPath
    if (currentPreview != null) {
        FilePreviewDialog(
            path = currentPreview,
            name = previewName ?: currentPreview.substringAfterLast('/'),
            locationDir = locationDir,
            viewModel = viewModel,
            onDismiss = { previewPath = null; previewName = null },
        )
    }
}

@Composable
private fun FilePreviewDialog(
    path: String,
    name: String,
    locationDir: String,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
) {
    var content by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(path, locationDir) {
        loading = true
        failed = false
        content = try {
            viewModel.readSessionFileContent(path, locationDir)
        } catch (_: Exception) {
            null
        }
        loading = false
        if (content == null) failed = true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
        },
        text = {
            when {
                loading -> CircularProgressIndicator()
                failed -> Text(stringResource(R.string.files_read_failed), style = MaterialTheme.typography.bodyMedium)
                else -> Text(
                    content.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                )
            }
        },
        confirmButton = {
            if (!loading && !failed) {
                TextButton(
                    enabled = content != null,
                    onClick = {
                        viewModel.sendFileInChat(path, content.orEmpty())
                        onDismiss()
                    },
                ) {
                    Text(stringResource(R.string.files_send_to_ai))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.files_cancel))
            }
        },
    )
}

private fun parentOfRelative(path: String): String {
    val p = path.trimEnd('/')
    val idx = p.lastIndexOf('/')
    if (idx <= 0) return ""
    return p.substring(0, idx)
}
private fun extractMarkdownLinks(text: String): List<String> {
    val markdown = Regex("""\[[^\]]*\]\(\s*(https?://[^\s)]+)\)""")
    val rawUrls = Regex("""https?://[^\s<>()]+""")
    val result = mutableListOf<String>()
    markdown.findAll(text).forEach { m ->
        m.groupValues.getOrNull(1)?.let { u ->
            if (u.startsWith("http")) result.add(u.trimEnd(')', '.'))
        }
    }
    rawUrls.findAll(text).forEach { m ->
        val u = m.value.trimEnd(')', '.', '，', '。', ',', ';', ';', '"', ' ')
        if (u.startsWith("http") && u !in result) result.add(u)
    }
    return result.distinct()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LinkPreviewSheet(
    url: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(
                    onClick = {
                        runCatching {
                            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                            context.startActivity(intent)
                        }
                    },
                ) { Text(stringResource(R.string.open_in_browser)) }
            }
            AndroidView(
                factory = { ctx ->
                    android.webkit.WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = android.webkit.WebViewClient()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(500.dp),
                update = { wv ->
                    if (wv.url != url) wv.loadUrl(url)
                },
            )
        }
    }
}
