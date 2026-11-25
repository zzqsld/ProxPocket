package com.proxpocket.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.proxpocket.app.Pm3ViewModel
import com.proxpocket.app.ui.CardInfoHeader
import com.proxpocket.app.ui.TerminalView
import com.proxpocket.app.ui.ToolButton

/** ISO15693 / 特斯联卡（ICODE SLIX、Tag-it 等） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Iso15693Screen(
    vm: Pm3ViewModel,
    onBack: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    val busy by vm.busy.collectAsState()
    val log by vm.log.collectAsState()
    val cardInfo by vm.cardInfo.collectAsState()

    var showRdblDialog by remember { mutableStateOf(false) }
    var showWrblDialog by remember { mutableStateOf(false) }
    var showUidDialog by remember { mutableStateOf(false) }
    var showWipeConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ISO15693 / 特斯联卡") },
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
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(span = { GridItemSpan(4) }) { I15SectionTitle("读取") }
                    item { ToolButton("读卡信息", enabled = !busy) { vm.exec("hf 15 info") } }
                    item { ToolButton("监听卡片", enabled = !busy) { vm.exec("hf 15 reader") } }
                    item { ToolButton("读取整卡", enabled = !busy) { vm.exec("hf 15 dump") } }
                    item { ToolButton("读块", enabled = !busy) { showRdblDialog = true } }
                    item(span = { GridItemSpan(4) }) { I15SectionTitle("写入（目标卡放天线上，谨慎操作）") }
                    item(span = { GridItemSpan(2) }) { ToolButton("写块", enabled = !busy) { showWrblDialog = true } }
                    item(span = { GridItemSpan(2) }) { ToolButton("写UID(魔术卡)", enabled = !busy) { showUidDialog = true } }
                    item(span = { GridItemSpan(2) }) {
                        ToolButton("格式化", enabled = !busy, danger = true) { showWipeConfirm = true }
                    }
                    item(span = { GridItemSpan(4) }) {
                        Text(
                            "特斯联 / 图书馆标签等多为 ISO15693；写 UID 仅对魔术卡有效",
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
                        vm.exec("hf 15 rdbl -b ${blk.toInt()}")
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
                        vm.exec("hf 15 wrbl -b ${blk.toInt()} -d $data")
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
            title = { Text("写 UID（ISO15693 魔术卡）") },
            text = {
                OutlinedTextField(
                    value = uid,
                    onValueChange = { uid = it.filter { c -> c in "0123456789abcdefABCDEF" }.take(16).uppercase() },
                    label = { Text("新 UID（16 位十六进制 = 8 字节）") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUidDialog = false
                        vm.exec("hf 15 csetuid -u $uid")
                    },
                    enabled = uid.length == 16,
                ) { Text("写入") }
            },
            dismissButton = {
                TextButton(onClick = { showUidDialog = false }) { Text("取消") }
            },
        )
    }

    if (showWipeConfirm) {
        AlertDialog(
            onDismissRequest = { showWipeConfirm = false },
            title = { Text("格式化标签") },
            text = { Text("将把标签数据区清零（UID 不变）。确定继续？") },
            confirmButton = {
                TextButton(onClick = {
                    showWipeConfirm = false
                    vm.exec("hf 15 wipe")
                }) { Text("格式化", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showWipeConfirm = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun I15SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}
