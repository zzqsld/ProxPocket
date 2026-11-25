package com.proxpocket.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.proxpocket.app.pm3.AccessBits
import com.proxpocket.app.pm3.DumpFile
import com.proxpocket.app.pm3.MfcDump
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

/** Sector-based Mifare dump editor with copy-first and explicitly confirmed overwrite saves. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DumpViewScreen(
    dump: DumpFile,
    onBack: () -> Unit,
    onSave: suspend (DumpFile, List<MfcDump.Block>, Boolean) -> File,
) {
    val fileKey = dump.file.absolutePath
    val parsedBlocks = remember(fileKey, dump.file.lastModified()) { MfcDump.parse(dump.file) }
    val initialBlocks = parsedBlocks?.let(::copyBlocks).orEmpty()
    var currentDump by remember(fileKey) { mutableStateOf(dump) }
    var blocks by remember(fileKey) { mutableStateOf(initialBlocks) }
    var originalBlocks by remember(fileKey) { mutableStateOf(copyBlocks(initialBlocks)) }
    var search by rememberSaveable(fileKey) { mutableStateOf("") }
    var editingBlock by remember { mutableStateOf<MfcDump.Block?>(null) }
    var inspectingTrailer by remember { mutableStateOf<MfcDump.Block?>(null) }
    var editValue by remember { mutableStateOf("") }
    var showOverwriteConfirmation by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val hasChanges = blocks.size != originalBlocks.size ||
        blocks.indices.any { blocks[it].hex() != originalBlocks.getOrNull(it)?.hex() }

    fun save(overwrite: Boolean) {
        scope.launch {
            saving = true
            saveError = null
            try {
                val destination = onSave(currentDump, blocks, overwrite)
                currentDump = currentDump.copy(file = destination)
                originalBlocks = copyBlocks(blocks)
                showOverwriteConfirmation = false
            } catch (error: IOException) {
                saveError = error.message ?: "文件写入失败"
            } catch (error: IllegalArgumentException) {
                saveError = error.message ?: "dump 数据格式无效"
            } finally {
                saving = false
            }
        }
    }

    inspectingTrailer?.let { block ->
        AccessBitsDialog(
            block = block,
            onDismiss = { inspectingTrailer = null },
            onCopy = { clipboard.setText(AnnotatedString(block.hex())) },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentDump.uid?.let { "M1 数据 · $it" } ?: "M1 数据") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        if (parsedBlocks == null) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
            ) {
                Text(
                    "无法解析该文件（支持 1K/4K .bin 或 .eml）",
                    color = MaterialTheme.colorScheme.error,
                )
            }
            return@Scaffold
        }

        val visibleBlocks = blocks.filter { block ->
            search.isBlank() ||
                "${block.sector} ${block.index} ${block.blockNumber} ${block.hex()}"
                    .contains(search.trim(), ignoreCase = true)
        }
        val blocksBySector = visibleBlocks.groupBy { it.sector }.toSortedMap()
        val sectorCount = blocks.maxOfOrNull { it.sector + 1 } ?: 0

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                ),
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(currentDump.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${if (blocks.size == 64) "1K" else "4K"} · $sectorCount 个扇区 · ${blocks.size} 个块",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Text(
                        if (hasChanges) "有未保存的编辑" else "内容已保存",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (hasChanges) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.outline,
                    )
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { save(overwrite = false) },
                    enabled = hasChanges && !saving,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (saving) "保存中…" else "另存为副本")
                }
                OutlinedButton(
                    onClick = { showOverwriteConfirmation = true },
                    enabled = hasChanges && !saving,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("覆盖当前文件")
                }
            }
            Text(
                "默认建议另存副本；覆盖操作会先要求确认。",
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
            saveError?.let { error ->
                Text(
                    error,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 4.dp),
                label = { Text("搜索扇区、块号或十六进制内容") },
                singleLine = true,
            )

            LazyColumn(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (blocksBySector.isEmpty()) {
                    item {
                        Text(
                            "没有匹配的数据块",
                            modifier = Modifier.padding(24.dp),
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
                items(blocksBySector.keys.toList(), key = { it }) { sector ->
                    SectorCard(
                        sector = sector,
                        blocks = blocksBySector.getValue(sector),
                        onEdit = { block ->
                            editingBlock = block
                            editValue = block.hex()
                        },
                        onInspectTrailer = { inspectingTrailer = it },
                        onCopy = { block ->
                            clipboard.setText(AnnotatedString(block.hex()))
                        },
                    )
                }
            }
        }
    }

    editingBlock?.let { block ->
        val validHex = editValue.length == 32 && editValue.all { it.digitToIntOrNull(16) != null }
        AlertDialog(
            onDismissRequest = { editingBlock = null },
            title = { Text("编辑扇区 ${block.sector} · 块 ${block.index}") },
            text = {
                Column {
                    OutlinedTextField(
                        value = editValue,
                        onValueChange = { value ->
                            editValue = value.filter { it.digitToIntOrNull(16) != null }.take(32)
                        },
                        label = { Text("16 字节（32 位十六进制）") },
                        supportingText = { Text("${editValue.length}/32") },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            keyboardType = KeyboardType.Ascii,
                        ),
                        isError = !validHex,
                        singleLine = true,
                    )
                    if (block.isTrailer) {
                        Text(
                            "尾块包含 Key A、访问控制位和 Key B；修改后请确认卡片权限数据正确。",
                            modifier = Modifier.padding(top = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = validHex,
                    onClick = {
                        val data = editValue.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                        blocks = blocks.map { existing ->
                            if (existing.blockNumber == block.blockNumber) {
                                MfcDump.Block(existing.sector, existing.index, data)
                            } else {
                                existing
                            }
                        }
                        saveError = null
                        editingBlock = null
                    },
                ) { Text("应用修改") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString(block.hex()))
                    }) { Text("复制原数据") }
                    TextButton(onClick = { editingBlock = null }) { Text("取消") }
                }
            },
        )
    }

    if (showOverwriteConfirmation) {
        AlertDialog(
            onDismissRequest = { showOverwriteConfirmation = false },
            title = { Text("覆盖原始 dump？") },
            text = {
                Text("将直接替换“${currentDump.name}”。此操作不可撤销，建议先选择“另存为副本”。")
            },
            confirmButton = {
                TextButton(
                    enabled = !saving,
                    onClick = { save(overwrite = true) },
                ) { Text("确认覆盖") }
            },
            dismissButton = {
                TextButton(onClick = { showOverwriteConfirmation = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SectorCard(
    sector: Int,
    blocks: List<MfcDump.Block>,
    onEdit: (MfcDump.Block) -> Unit,
    onInspectTrailer: (MfcDump.Block) -> Unit,
    onCopy: (MfcDump.Block) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        ),
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(
                "扇区 %02d · %d 个块".format(sector, blocks.size),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            blocks.forEach { block ->
                DumpBlockRow(
                    block,
                    onEdit = onEdit,
                    onInspectTrailer = onInspectTrailer,
                    onCopy = onCopy,
                )
            }
        }
    }
}

@Composable
private fun DumpBlockRow(
    block: MfcDump.Block,
    onEdit: (MfcDump.Block) -> Unit,
    onInspectTrailer: (MfcDump.Block) -> Unit,
    onCopy: (MfcDump.Block) -> Unit,
) {
    val isZero = block.data.all { it == 0.toByte() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (block.isTrailer) onInspectTrailer(block) else onEdit(block)
            }
            .padding(vertical = 5.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "%03d".format(block.blockNumber),
            modifier = Modifier.width(34.dp),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.outline,
        )
        Text(
            block.hex().chunked(2).chunked(8).joinToString("\n") { it.joinToString(" ") },
            modifier = Modifier.weight(1f),
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            color = if (isZero) MaterialTheme.colorScheme.outline
            else if (block.isTrailer) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = { onCopy(block) }) {
            Icon(Icons.Filled.ContentCopy, contentDescription = "复制块数据")
        }
        IconButton(onClick = { onEdit(block) }) {
            Icon(Icons.Filled.Edit, contentDescription = "编辑块数据")
        }
    }
}

@Composable
private fun AccessBitsDialog(block: MfcDump.Block, onDismiss: () -> Unit, onCopy: () -> Unit) {
    val decoded = remember(block.data) { AccessBits.decode(block.data) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("扇区 ${block.sector} 权限解码") },
        text = {
            if (decoded == null) {
                Text("无法解析该尾块")
            } else {
                Column {
                    Text(
                        "控制位 ${block.accessBits()}（C1C2C3: ${decoded.bits.joinToString(" ") { it.toString(2).padStart(3, '0') }}）",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (!decoded.valid) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "反向校验位不一致，控制位数据可能损坏",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    decoded.dataPerms.forEachIndexed { index, permission ->
                        Text(
                            "块 $index  读:${permission.read} 写:${permission.write} 加:${permission.incr} 减:${permission.decr}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    decoded.trailerLines.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (decoded.keyBUsable) "KeyB 不可读，可用于认证"
                        else "KeyB 明文可读，不能再用于认证（读卡器只能用 KeyA）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onCopy) { Text("复制尾块") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

private fun copyBlocks(blocks: List<MfcDump.Block>): List<MfcDump.Block> =
    blocks.map { MfcDump.Block(it.sector, it.index, it.data.copyOf()) }
