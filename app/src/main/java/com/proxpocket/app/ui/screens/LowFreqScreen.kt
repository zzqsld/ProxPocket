package com.proxpocket.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.proxpocket.app.Pm3ViewModel
import com.proxpocket.app.pm3.DumpFile
import com.proxpocket.app.ui.TerminalView
import com.proxpocket.app.ui.ToolButton

private data class LfAction(val title: String, val command: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LowFreqScreen(
    vm: Pm3ViewModel,
    onBack: () -> Unit,
) {
    val busy by vm.busy.collectAsState()
    val log by vm.log.collectAsState()
    val lfInfo by vm.lfInfo.collectAsState()
    val dumpFiles by vm.dumpFiles.collectAsState()
    val t55xxDumps = dumpFiles.filter { it.kind == DumpFile.Kind.T55XX_DUMP }

    var showEm4100Dialog by remember { mutableStateOf(false) }
    var showHidDialog by remember { mutableStateOf(false) }
    var showWipeConfirm by remember { mutableStateOf(false) }
    var showRestorePicker by remember { mutableStateOf(false) }

    val readTools = listOf(
        LfAction("搜索ID卡", "lf search"),
        LfAction("T55xx检测", "lf t55xx detect"),
        LfAction("T55xx读取", "lf t55xx dump"),
        LfAction("T55xx配置", "lf t55xx info"),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("低频 ID 卡") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            // 工具区：普通行列布局，所有按钮一次显示完整，不再裁剪
            LfSectionTitle("读取")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                readTools.take(4).forEach { a ->
                    ToolButton(a.title, enabled = !busy, modifier = Modifier.weight(1f)) {
                        vm.exec(a.command)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            LfSectionTitle("写入（T5577/白卡放天线上）")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolButton("写EM4100", enabled = !busy, modifier = Modifier.weight(1f)) { showEm4100Dialog = true }
                ToolButton("写HID卡", enabled = !busy, modifier = Modifier.weight(1f)) { showHidDialog = true }
                ToolButton("恢复dump", enabled = !busy, modifier = Modifier.weight(1f)) { showRestorePicker = true }
                ToolButton("擦除T55xx", enabled = !busy, modifier = Modifier.weight(1f), danger = true) { showWipeConfirm = true }
            }
            Spacer(Modifier.height(8.dp))

            // 解析结果卡（lf search 等命令后显示）
            if (lfInfo.isNotEmpty()) {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text(
                            lfInfo["TYPE"] ?: "低频卡",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                        )
                        lfInfo["ID"]?.let {
                            Text("ID: $it", color = Color.White, fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            lfInfo["FORMAT"]?.let { Text("格式 $it", color = Color.White.copy(0.85f), style = MaterialTheme.typography.labelMedium) }
                            lfInfo["FC"]?.let { Text("FC $it", color = Color.White.copy(0.85f), style = MaterialTheme.typography.labelMedium) }
                            lfInfo["CN"]?.let { Text("卡号 $it", color = Color.White.copy(0.85f), style = MaterialTheme.typography.labelMedium) }
                            lfInfo["CHIP"]?.let { Text(it, color = Color.White.copy(0.85f), style = MaterialTheme.typography.labelMedium) }
                        }
                        lfInfo["RAW"]?.let {
                            Text(it, color = Color.White.copy(0.85f), style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(8.dp))
            TerminalView(
                log = log,
                busy = busy,
                onSend = { vm.exec(it) },
                onClear = vm::clearLog,
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (showEm4100Dialog) {
        var id by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showEm4100Dialog = false },
            title = { Text("写入 EM4100 卡号") },
            text = {
                OutlinedTextField(
                    value = id,
                    onValueChange = { id = it.filter { c -> c in "0123456789abcdefABCDEF" }.take(10).uppercase() },
                    label = { Text("10 位十六进制卡号") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showEm4100Dialog = false
                        vm.exec("lf em 410x clone --id $id")
                    },
                    enabled = id.length == 10,
                ) { Text("写入") }
            },
            dismissButton = {
                TextButton(onClick = { showEm4100Dialog = false }) { Text("取消") }
            },
        )
    }

    if (showHidDialog) {
        var fc by remember { mutableStateOf("") }
        var cn by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showHidDialog = false },
            title = { Text("写入 HID H10301（26 位）") },
            text = {
                Column {
                    OutlinedTextField(
                        value = fc,
                        onValueChange = { fc = it.filter(Char::isDigit).take(3) },
                        label = { Text("设施码 FC（0-255）") },
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = cn,
                        onValueChange = { cn = it.filter(Char::isDigit).take(5) },
                        label = { Text("卡号 CN（0-65535）") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showHidDialog = false
                        vm.exec("lf hid clone -w H10301 --fc $fc --cn $cn")
                    },
                    enabled = fc.isNotEmpty() && cn.isNotEmpty(),
                ) { Text("写入") }
            },
            dismissButton = {
                TextButton(onClick = { showHidDialog = false }) { Text("取消") }
            },
        )
    }

    if (showWipeConfirm) {
        AlertDialog(
            onDismissRequest = { showWipeConfirm = false },
            title = { Text("擦除 T55xx 卡") },
            text = { Text("将块 1-7 清零并写入默认配置块。确定继续？") },
            confirmButton = {
                TextButton(onClick = {
                    showWipeConfirm = false
                    vm.exec("lf t55xx wipe")
                }) { Text("擦除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showWipeConfirm = false }) { Text("取消") }
            },
        )
    }

    if (showRestorePicker) {
        AlertDialog(
            onDismissRequest = { showRestorePicker = false },
            title = { Text("选择 T55xx dump") },
            text = {
                if (t55xxDumps.isEmpty()) {
                    Text("没有 T55xx dump\n先用「T55xx读取」保存一份", color = MaterialTheme.colorScheme.outline)
                } else {
                    Column {
                        t55xxDumps.take(8).forEach { d ->
                            TextButton(onClick = {
                                showRestorePicker = false
                                vm.exec("lf t55xx restore -f \"${d.file.absolutePath}\"")
                            }) {
                                Text(d.name, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRestorePicker = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun LfSectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
    )
}
