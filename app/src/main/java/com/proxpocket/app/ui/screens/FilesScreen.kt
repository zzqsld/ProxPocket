package com.proxpocket.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.proxpocket.app.Pm3ViewModel
import com.proxpocket.app.pm3.DumpFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    vm: Pm3ViewModel,
    onBack: () -> Unit,
    onViewDump: (DumpFile) -> Unit,
) {
    val files by vm.dumpFiles.collectAsState()
    var pendingDelete by remember { mutableStateOf<DumpFile?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf("全部") }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.importDump(it) } }

    val exportTarget = remember { mutableStateOf<DumpFile?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val src = exportTarget.value
        if (uri != null && src != null) vm.exportDump(src, uri)
        exportTarget.value = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("数据管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Filled.Add, contentDescription = "导入")
                    }
                },
            )
        },
    ) { padding ->
        val visibleFiles = files.filter { file ->
            val matchesFilter = selectedFilter == "全部" || fileKindName(file.kind) == selectedFilter
            val query = search.trim()
            val matchesSearch = query.isEmpty() ||
                file.name.contains(query, ignoreCase = true) ||
                file.uid.orEmpty().contains(query, ignoreCase = true)
            matchesFilter && matchesSearch
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                "共 ${files.size} 个文件 · ${files.sumOf { it.size }} 字节",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
            )
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                label = { Text("搜索文件名或 UID") },
                singleLine = true,
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FILE_FILTERS.forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter) },
                    )
                }
            }

            if (visibleFiles.isEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        if (files.isEmpty()) {
                            "暂无数据文件\n读取卡片后的 dump / 密钥会出现在这里\n也可以点右上角导入"
                        } else {
                            "没有匹配的文件\n试试更短的文件名或 UID"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(visibleFiles, key = { it.file.absolutePath }) { item ->
                        DumpFileRow(
                            item = item,
                            onView = { onViewDump(item) },
                            onExport = {
                                exportTarget.value = item
                                exportLauncher.launch(item.name)
                            },
                            onDelete = { pendingDelete = item },
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除文件") },
            text = { Text("确定删除 ${item.name}？") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteDump(item)
                    pendingDelete = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun DumpFileRow(
    item: DumpFile,
    onView: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    val time = remember(item.modified) {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(item.modified))
    }
    Card(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = item.kind == DumpFile.Kind.MFC_DUMP, onClick = onView),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                when (item.kind) {
                    DumpFile.Kind.MFC_DUMP -> Icons.Filled.Memory
                    DumpFile.Kind.MFC_KEY -> Icons.Filled.VpnKey
                    else -> Icons.Filled.Description
                },
                contentDescription = null,
                tint = when (item.kind) {
                    DumpFile.Kind.MFC_DUMP -> MaterialTheme.colorScheme.primary
                    DumpFile.Kind.MFC_KEY -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.outline
                },
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                Text(
                    "${fileKindName(item.kind)} · ${item.size} 字节 · $time" +
                            (if (item.kind == DumpFile.Kind.MFC_DUMP) " · 点击查看" else ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            IconButton(onClick = onExport) {
                Icon(Icons.Filled.Share, contentDescription = "导出", modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

private val FILE_FILTERS = listOf("全部", "M1 Dump", "密钥", "Ultralight Dump", "T55xx Dump", "其他")

private fun fileKindName(kind: DumpFile.Kind): String = when (kind) {
    DumpFile.Kind.MFC_DUMP -> "M1 Dump"
    DumpFile.Kind.MFC_KEY -> "密钥"
    DumpFile.Kind.MFU_DUMP -> "Ultralight Dump"
    DumpFile.Kind.T55XX_DUMP -> "T55xx Dump"
    DumpFile.Kind.OTHER -> "其他"
}
