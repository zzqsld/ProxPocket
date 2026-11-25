package com.proxpocket.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.graphics.Color

/** 共享卡片信息栏：UID / ATQA / SAK / TYPE */
@Composable
fun CardInfoHeader(
    cardInfo: Map<String, String>,
    busy: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Nfc,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    "UID: ${cardInfo["UID"] ?: "--"}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.weight(1f))
                if (busy) {
                    CircularProgressIndicator(
                        Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("ATQA ${cardInfo["ATQA"] ?: "--"}", color = Color.White.copy(0.85f), style = MaterialTheme.typography.labelMedium)
                Text("SAK ${cardInfo["SAK"] ?: "--"}", color = Color.White.copy(0.85f), style = MaterialTheme.typography.labelMedium)
                Text(cardInfo["TYPE"] ?: "", color = Color.White.copy(0.85f), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** 统一规格的工具按钮：固定高度，所有界面等大 */
@Composable
fun ToolButton(
    title: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(56.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (danger) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surface,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 终端行颜色：只有真正的错误才标红，调试/提示类降级 */
private fun terminalLineColor(line: String, cs: androidx.compose.material3.ColorScheme): Color = when {
    line.startsWith("[!]") || line.contains("FAILED", true) -> cs.error
    line.startsWith("[+]") -> cs.secondary
    line.startsWith("pm3 -->") -> cs.primary
    // 设备调试输出与无害提示（Cmd Error 04 等）降为灰色
    line.startsWith("[#]") || line.contains("Cmd Error", true) ||
        line.contains("can occur", true) || line.contains("Wrong response", true) ->
        cs.outline
    else -> cs.onSurface
}

/** 共享终端组件：滚动日志 + 命令输入 */
@Composable
fun TerminalView(
    log: List<String>,
    busy: Boolean,
    onSend: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current
    var input by remember { mutableStateOf("") }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var previousLogSize by remember { mutableIntStateOf(log.size) }

    LaunchedEffect(log) {
        val wasAtBottom = previousLogSize <= 1 ||
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index == previousLogSize - 1
        previousLogSize = log.size
        if (wasAtBottom && log.isNotEmpty()) listState.scrollToItem(log.lastIndex)
    }

    LaunchedEffect(busy) {
        elapsedSeconds = 0
        while (busy) {
            kotlinx.coroutines.delay(1000)
            elapsedSeconds += 1
        }
    }

    Column(modifier = modifier) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 4.dp, top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "终端输出 · ${log.size} 行",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        if (busy) {
                            Text(
                                "设备执行中 · 输出将在命令完成后显示（${elapsedSeconds}s）",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                    }
                    IconButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(log.joinToString("\n")))
                        },
                        enabled = log.isNotEmpty(),
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "复制全部日志")
                    }
                    IconButton(onClick = onClear, enabled = log.isNotEmpty()) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = "清空日志")
                    }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                ) {
                    items(log) { line ->
                        Text(
                            text = line,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = terminalLineColor(line, MaterialTheme.colorScheme),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("输入 pm3 命令，如 hf search") },
                singleLine = true,
                enabled = !busy,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    val cmd = input.trim()
                    if (cmd.isNotEmpty()) {
                        onSend(cmd)
                        input = ""
                    }
                }),
            )
            Button(
                onClick = {
                    val cmd = input.trim()
                    if (cmd.isNotEmpty()) {
                        onSend(cmd)
                        input = ""
                    }
                },
                enabled = !busy,
            ) {
                Text(if (busy) "执行中" else "发送")
            }
        }
        // Keep shortcuts on one horizontally scrollable row on narrow screens.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf("hw version", "hf search", "lf search", "hw status").forEach { cmd ->
                TextButton(
                    onClick = { onSend(cmd) },
                    enabled = !busy,
                    modifier = Modifier.heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Text(cmd, fontSize = 12.sp, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}
