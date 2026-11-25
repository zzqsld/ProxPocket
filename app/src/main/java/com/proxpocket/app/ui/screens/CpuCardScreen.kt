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
import androidx.compose.ui.unit.dp
import com.proxpocket.app.Pm3ViewModel
import com.proxpocket.app.ui.CardInfoHeader
import com.proxpocket.app.ui.TerminalView

/** CPU 卡（ISO14443-A / APDU） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CpuCardScreen(
    vm: Pm3ViewModel,
    onBack: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    val busy by vm.busy.collectAsState()
    val log by vm.log.collectAsState()
    val cardInfo by vm.cardInfo.collectAsState()

    var showApduDialog by remember { mutableStateOf(false) }
    var showRawDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CPU 卡") },
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
                    item(span = { GridItemSpan(4) }) { CpuSectionTitle("识别") }
                    item { CpuTool("读卡信息", busy) { vm.exec("hf 14a info") } }
                    item { CpuTool("深度识别", busy) { vm.cpuDeepInfo() } }
                    item { CpuTool("监听卡片", busy) { vm.exec("hf 14a reader") } }
                    item { CpuTool("NDEF读取", busy) { vm.exec("hf 14a ndefread") } }
                    item(span = { GridItemSpan(4) }) { CpuSectionTitle("DESFire") }
                    item { CpuTool("DF信息", busy) { vm.exec("hf mfdes info") } }
                    item { CpuTool("DF密钥检测", busy) { vm.exec("hf mfdes detect") } }
                    item { CpuTool("DF整卡导出", busy) { vm.exec("hf mfdes dump") } }
                    item { CpuTool("DF应用列表", busy) { vm.exec("hf mfdes getappnames") } }
                    item(span = { GridItemSpan(4) }) { CpuSectionTitle("指令交互") }
                    item(span = { GridItemSpan(2) }) { CpuTool("发送 APDU", busy) { showApduDialog = true } }
                    item(span = { GridItemSpan(2) }) { CpuTool("原始指令", busy) { showRawDialog = true } }
                    item(span = { GridItemSpan(4) }) {
                        Text(
                            "深度识别 = hf search + 两次 14a info，部分 CPU 卡需二次选卡才显示完整信息",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
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

    if (showApduDialog) {
        var apdu by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showApduDialog = false },
            title = { Text("发送 APDU") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = apdu,
                        onValueChange = { apdu = it.filter { c -> c in "0123456789abcdefABCDEF" }.uppercase() },
                        label = { Text("APDU 指令（十六进制）") },
                        placeholder = { Text("如 00A4040007D2760000850100") },
                    )
                    Text(
                        "自动选卡后发送，并解析返回的 TLV 数据",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showApduDialog = false
                        vm.exec("hf 14a apdu -s -t -d $apdu")
                    },
                    enabled = apdu.length >= 4 && apdu.length % 2 == 0,
                ) { Text("发送") }
            },
            dismissButton = {
                TextButton(onClick = { showApduDialog = false }) { Text("取消") }
            },
        )
    }

    if (showRawDialog) {
        var raw by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showRawDialog = false },
            title = { Text("原始指令（hf 14a raw）") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = raw,
                        onValueChange = { raw = it.filter { c -> c in "0123456789abcdefABCDEF" }.uppercase() },
                        label = { Text("指令数据（十六进制）") },
                    )
                    Text(
                        "带选卡发送原始 ISO14443-A 指令，用于特殊卡片（如玩具卡写数据）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRawDialog = false
                        vm.exec("hf 14a raw -s -c $raw")
                    },
                    enabled = raw.length >= 2 && raw.length % 2 == 0,
                ) { Text("发送") }
            },
            dismissButton = {
                TextButton(onClick = { showRawDialog = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun CpuSectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun CpuTool(
    title: String,
    busy: Boolean,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    com.proxpocket.app.ui.ToolButton(title, enabled = !busy, danger = danger, onClick = onClick)
}
