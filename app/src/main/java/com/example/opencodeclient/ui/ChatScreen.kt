package com.example.opencodeclient.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.opencodeclient.R
import com.example.opencodeclient.data.QuestionRequest
import com.example.opencodeclient.data.Tokens
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.model.DefaultMarkdownTypography
import com.mikepenz.markdown.model.MarkdownTypography

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
    val pendingQuestions by viewModel.pendingQuestions.collectAsStateWithLifecycle()
    val shortTokens by viewModel.shortTokens.collectAsStateWithLifecycle()
    val userBubbleColor by viewModel.userBubbleColor.collectAsStateWithLifecycle()
    val assistantBubbleColor by viewModel.assistantBubbleColor.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var input by rememberSaveable { mutableStateOf("") }
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

    Scaffold(
        modifier = Modifier.statusBarsPadding(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        activeSession?.title?.ifBlank { "OpenCode" } ?: "OpenCode",
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onMenu) { Icon(Icons.Filled.Menu, stringResource(R.string.drawer_settings)) }
                },
                actions = {
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
                items(messages.asReversed(), key = { it.id }) { msg ->
                    MessageBubble(
                        msg = msg,
                        userColor = userBubbleColor,
                        assistantColor = assistantBubbleColor,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.chat_placeholder)) },
                )
                IconButton(
                    onClick = {
                        viewModel.send(input.trim())
                        input = ""
                    },
                    enabled = input.isNotBlank() && !sending,
                    modifier = Modifier.padding(bottom = 4.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Send")
                }
            }
        }
    }

    pendingQuestions.firstOrNull()?.let { question ->
        QuestionDialog(
            question = question,
            onReply = { answers -> viewModel.replyQuestions(question, answers) },
            onReject = { viewModel.rejectQuestion(question) },
        )
    }
}

@Composable
private fun QuestionDialog(
    question: QuestionRequest,
    onReply: (List<List<String>>) -> Unit,
    onReject: () -> Unit,
) {
    var selections by rememberSaveable {
        mutableStateOf(List(question.questions.size) { mutableListOf<String>() } as List<MutableList<String>>)
    }
    var customs by rememberSaveable {
        mutableStateOf(List(question.questions.size) { "" } as List<String>)
    }

    AlertDialog(
        onDismissRequest = onReject,
        title = { Text(stringResource(R.string.chat_erase_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val answers = question.questions.mapIndexed { i, q ->
                        val labels = selections[i].toList()
                        val custom = customs[i].trim()
                        when {
                            custom.isNotEmpty() -> labels + custom
                            else -> labels
                        }
                    }
                    onReply(answers)
                },
            ) { Text(stringResource(R.string.chat_submit)) }
        },
        dismissButton = {
            TextButton(onClick = onReject) { Text(stringResource(R.string.chat_reject)) }
        },
    )
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
    Markdown(
        content = content,
        typography = typography,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun MessageBubble(
    msg: ChatMessage,
    userColor: Long = -1L,
    assistantColor: Long = -1L,
) {
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
            }
        }
    }
}

@Composable
private fun ToolPart(part: PartUi) {
    var expanded by remember { mutableStateOf(false) }
    val title = part.toolTitle ?: part.tool ?: "tool"
    val status = part.toolState ?: "running"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        verticalArrangement = Arrangement.spacedBy(2.dp),
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
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
        if (expanded) {
            val input = part.toolInput
            val output = part.toolOutput
            if (!input.isNullOrBlank()) {
                Text(
                    input,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .padding(top = 4.dp),
                )
            }
            if (!output.isNullOrBlank()) {
                Text(
                    output,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .padding(top = 4.dp),
                )
            }
        }
    }
}

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
                    "${(ratio * 100).toInt()}% of ${formatTokens(contextWindow, shortTokens)}",
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
