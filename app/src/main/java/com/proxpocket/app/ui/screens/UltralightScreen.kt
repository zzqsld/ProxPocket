package com.proxpocket.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.proxpocket.app.Pm3ViewModel
import com.proxpocket.app.pm3.DumpFile
import com.proxpocket.app.ui.CardInfoHeader
import com.proxpocket.app.ui.TerminalView

/** Mifare Ultralight / NTAG / Amiibo */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UltralightScreen(
    vm: Pm3ViewModel,
    onBack: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    val busy by vm.busy.collectAsState()
    val log by vm.log.collectAsState()
    val cardInfo by vm.cardInfo.collectAsState()
    val dumpFiles by vm.dumpFiles.collectAsState()
    val mfuDumps = dumpFiles.filter {
        it.kind == DumpFile.Kind.MFU_DUMP ||
            (it.kind == DumpFile.Kind.OTHER && (it.name.endsWith(".bin") || it.name.endsWith(".eml")))
    }

    var showRdblDialog by remember { mutableStateOf(false) }
    var showWrblDialog by remember { mutableStateOf(false) }
    var showUidDialog by remember { mutableStateOf(false) }
    var showWipeConfirm by remember { mutableStateOf(false) }
    var showRestorePicker by remember { mutableStateOf(false) }
    var showAmiiboPicker by remember { mutableStateOf(false) }
    var showNdefDialog by remember { mutableStateOf(false) }
    // Amiibo = NTAG215 540 字节镜像
    val amiiboDumps = dumpFiles.filter { it.name.endsWith(".bin") && it.file.length() == 540L }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ultralight / NTAG") },
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
            CardInfoHeader(cardInfo = cardInfo, busy = busy)
            Spacer(Modifier.height(12.dp))
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("工具") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("终端") })
            }
            Spacer(Modifier.height(8.dp))
            when (tab) {
                0 -> LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    contentPadding = PaddingValues(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(span = { GridItemSpan(4) }) { UfSectionTitle("读取") }
                    item { UfTool("读卡信息", busy) { vm.exec("hf mfu info") } }
                    item { UfTool("读取整卡", busy) { vm.exec("hf mfu dump") } }
                    item { UfTool("密钥检测", busy) { vm.exec("hf mfu chk") } }
                    item { UfTool("NDEF读取", busy) { vm.exec("hf mfu ndefread") } }
                    item { UfTool("读块", busy) { showRdblDialog = true } }
                    item(span = { GridItemSpan(4) }) { UfSectionTitle("写入（目标卡放天线上，谨慎操作）") }
                    item(span = { GridItemSpan(2) }) { UfTool("写块", busy) { showWrblDialog = true } }
                    item(span = { GridItemSpan(2) }) { UfTool("写网址NDEF", busy) { showNdefDialog = true } }
                    item(span = { GridItemSpan(2) }) { UfTool("写UID(魔术卡)", busy) { showUidDialog = true } }
                    item(span = { GridItemSpan(2) }) { UfTool("恢复dump", busy) { showRestorePicker = true } }
                    item(span = { GridItemSpan(2) }) { UfTool("格式化", busy, danger = true) { showWipeConfirm = true } }
                    item(span = { GridItemSpan(4) }) { UfSectionTitle("Amiibo（NTAG215 540 字节镜像）") }
                    item(span = { GridItemSpan(2) }) { UfTool("读取标签到库", busy) { vm.exec("hf mfu dump") } }
                    item(span = { GridItemSpan(2) }) { UfTool("写入标签", busy) { showAmiiboPicker = true } }
                }
                else -> TerminalView(
                    log = log,
                    busy = busy,
                    onSend = { vm.exec(it) },
                    onClear = vm::clearLog,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (showRdblDialog) {
        var blk by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showRdblDialog = false },
            title = { Text("读块") },
            text = {
                OutlinedTextField(
                    value = blk,
                    onValueChange = { blk = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("块号（十进制）") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRdblDialog = false
                        vm.exec("hf mfu rdbl -b ${blk.toInt()}")
                    },
                    enabled = blk.toIntOrNull() != null,
                ) { Text("读取") }
            },
            dismissButton = {
                TextButton(onClick = { showRdblDialog = false }) { Text("取消") }
            },
        )
    }

    if (showWrblDialog) {
        var blk by remember { mutableStateOf("") }
        var data by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showWrblDialog = false },
            title = { Text("写块") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = blk,
                        onValueChange = { blk = it.filter { c -> c.isDigit() }.take(3) },
                        label = { Text("块号（十进制）") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = data,
                        onValueChange = { data = it.filter { c -> c in "0123456789abcdefABCDEF" }.take(8).uppercase() },
                        label = { Text("数据（8 位十六进制 = 4 字节）") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showWrblDialog = false
                        vm.exec("hf mfu wrbl -b ${blk.toInt()} -d $data")
                    },
                    enabled = blk.toIntOrNull() != null && data.length == 8,
                ) { Text("写入") }
            },
            dismissButton = {
                TextButton(onClick = { showWrblDialog = false }) { Text("取消") }
            },
        )
    }

    if (showUidDialog) {
        var uid by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showUidDialog = false },
            title = { Text("写 UID（Ultralight 魔术卡）") },
            text = {
                OutlinedTextField(
                    value = uid,
                    onValueChange = { uid = it.filter { c -> c in "0123456789abcdefABCDEF" }.take(14).uppercase() },
                    label = { Text("新 UID（14 位十六进制 = 7 字节）") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUidDialog = false
                        vm.exec("hf mfu setuid -u $uid")
                    },
                    enabled = uid.length == 14,
                ) { Text("写入") }
            },
            dismissButton = {
                TextButton(onClick = { showUidDialog = false }) { Text("取消") }
            },
        )
    }

    if (showRestorePicker) {
        AlertDialog(
            onDismissRequest = { showRestorePicker = false },
            title = { Text("恢复 dump 到卡片") },
            text = {
                if (mfuDumps.isEmpty()) {
                    Text("没有可用的 dump\n先「读取整卡」或在「数据管理」导入", color = MaterialTheme.colorScheme.outline)
                } else {
                    Column {
                        mfuDumps.take(8).forEach { d ->
                            TextButton(onClick = {
                                showRestorePicker = false
                                vm.exec("hf mfu restore -f \"${d.file.absolutePath}\"")
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

    if (showNdefDialog) {
        var uri by remember { mutableStateOf("https://") }
        AlertDialog(
            onDismissRequest = { showNdefDialog = false },
            title = { Text("写网址（NDEF URI）") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uri,
                        onValueChange = { uri = it.trim() },
                        label = { Text("网址") },
                        singleLine = true,
                    )
                    Text(
                        "写入后手机碰一碰即可弹出链接。要求标签已完成 NDEF 格式化（空白 NTAG 默认已格式化）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val hex = com.proxpocket.app.pm3.Ndef.uriRecordHex(uri)
                        showNdefDialog = false
                        if (hex != null) {
                            vm.exec("hf 14a ndefwrite -p -d $hex")
                        }
                    },
                    enabled = uri.length > 4 && com.proxpocket.app.pm3.Ndef.uriRecordHex(uri) != null,
                ) { Text("写入") }
            },
            dismissButton = {
                TextButton(onClick = { showNdefDialog = false }) { Text("取消") }
            },
        )
    }

    if (showAmiiboPicker) {
        AlertDialog(
            onDismissRequest = { showAmiiboPicker = false },
            title = { Text("写入 Amiibo 镜像") },
            text = {
                if (amiiboDumps.isEmpty()) {
                    Text(
                        "库里没有 540 字节的 NTAG215 镜像\n先用「读取标签到库」读取，或在「数据管理」导入 .bin",
                        color = MaterialTheme.colorScheme.outline,
                    )
                } else {
                    Column {
                        Text(
                            "需要空白 NTAG215 标签放在天线上",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        amiiboDumps.take(8).forEach { d ->
                            TextButton(onClick = {
                                showAmiiboPicker = false
                                vm.exec("hf mfu restore -f \"${d.file.absolutePath}\"")
                            }) {
                                Text(d.name, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAmiiboPicker = false }) { Text("取消") }
            },
        )
    }

    if (showWipeConfirm) {        AlertDialog(
            onDismissRequest = { showWipeConfirm = false },
            title = { Text("格式化标签") },
            text = { Text("将把标签数据区清零（UID 不变）。确定继续？") },
            confirmButton = {
                TextButton(onClick = {
                    showWipeConfirm = false
                    vm.exec("hf mfu wipe")
                }) { Text("格式化", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showWipeConfirm = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun UfSectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun UfTool(
    title: String,
    busy: Boolean,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    com.proxpocket.app.ui.ToolButton(title, enabled = !busy, danger = danger, onClick = onClick)
}
