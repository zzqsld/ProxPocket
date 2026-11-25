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
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.RadioButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.proxpocket.app.Pm3ViewModel
import com.proxpocket.app.pm3.DumpFile
import com.proxpocket.app.ui.CardInfoHeader
import com.proxpocket.app.ui.TerminalView
import com.proxpocket.app.ui.ToolButton
import kotlinx.coroutines.launch

private data class MfAction(
    val title: String,
    val command: String?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MifareScreen(
    vm: Pm3ViewModel,
    onBack: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    val busy by vm.busy.collectAsState()
    val log by vm.log.collectAsState()
    val cardInfo by vm.cardInfo.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mifare Classic") },
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
                0 -> MfToolsTab(vm, busy, cardInfo["UID"])
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
}

private const val PAGE_ANALYZE = 0
private const val PAGE_WRITE = 1
private const val PAGE_REPAIR = 2
private const val PAGE_COUNT = 3

@Composable
private fun MfToolsTab(vm: Pm3ViewModel, busy: Boolean, targetUid: String?) {
    val dumpFiles by vm.dumpFiles.collectAsState()
    val mfcDumps = dumpFiles.filter { it.kind == DumpFile.Kind.MFC_DUMP }

    var restoreWithKey by remember { mutableStateOf(false) }
    var showRestorePicker by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<Pair<DumpFile, Boolean>?>(null) }
    var showUidDialog by remember { mutableStateOf(false) }
    var showCuidDialog by remember { mutableStateOf(false) }
    var showWipeConfirm by remember { mutableStateOf(false) }
    var wipeGdm by remember { mutableStateOf(false) }
    var showKnownKeyDialog by remember { mutableStateOf(false) }
    var showManualDialog by remember { mutableStateOf(false) }
    var showGdmWriteDialog by remember { mutableStateOf(false) }
    var showGen3UidDialog by remember { mutableStateOf(false) }
    var showGen3FreezeConfirm by remember { mutableStateOf(false) }
    var showUfuidConfirm by remember { mutableStateOf(false) }
    var showEncryptDialog by remember { mutableStateOf(false) }
    var showTrailerConfirm by remember { mutableStateOf(false) }
    var repairMethod by remember { mutableStateOf<Pm3ViewModel.RepairMethod?>(null) }

    val analysis = listOf(
        MfAction("读卡类型", "hf search"),
        MfAction("一键破解", "hf mf autopwn"),
        MfAction("三代破解", "hf mf sen"),
        MfAction("PRNG解析", "hf mf hardnested --blk 0 -a -k FFFFFFFFFFFF --tblk 4 -ta"),
        MfAction("卡片信息", "hf mf info"),
    )

    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { PAGE_COUNT })
    val pagerScope = rememberCoroutineScope()

    Column {
        TabRow(selectedTabIndex = pagerState.currentPage) {
            listOf("分析", "写入", "修复").forEachIndexed { index, title ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { pagerScope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(title) },
                )
            }
        }
        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
        ) { page ->
            when (page) {
                // ── 页 1：分析与读取 ─────────────────────────────
                PAGE_ANALYZE -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(span = { GridItemSpan(2) }) { SectionTitle("魔法分析") }
                    items(analysis) { a ->
                        ToolButton(a.title, enabled = !busy && a.command != null) {
                            a.command?.let { vm.exec(it) }
                        }
                    }
                    item(span = { GridItemSpan(2) }) {
                        Text(
                            "三代破解 = FM11RF08S 静态加密随机数卡（SEN）攻击，一键破解会自动识别卡类型",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    item(span = { GridItemSpan(2) }) { SectionTitle("读取") }
                    item { ToolButton("读取卡片(默认密)", enabled = !busy) { vm.exec("hf mf dump") } }
                    item { ToolButton("密钥扫描", enabled = !busy) { vm.exec("hf mf chk --1k -f mfc_default_keys.dic") } }
                    item(span = { GridItemSpan(2) }) {
                        ToolButton("知一求全", enabled = !busy) { showKnownKeyDialog = true }
                    }
                    item(span = { GridItemSpan(2) }) {
                        ToolButton("手动分析", enabled = !busy) { showManualDialog = true }
                    }
                }

                // ── 页 2：写入 ──────────────────────────────────
                PAGE_WRITE -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(span = { GridItemSpan(2) }) { SectionTitle("写入（目标卡放天线上，谨慎操作）") }
                    item(span = { GridItemSpan(2) }) {
                        ToolButton("写空白卡", enabled = !busy) {
                            restoreWithKey = false
                            showRestorePicker = true
                        }
                    }
                    item(span = { GridItemSpan(2) }) {
                        ToolButton("写有密卡", enabled = !busy) {
                            restoreWithKey = true
                            showRestorePicker = true
                        }
                    }
                    item(span = { GridItemSpan(2) }) {
                        ToolButton("写UID卡(Gen1a)", enabled = !busy) { showUidDialog = true }
                    }
                    item(span = { GridItemSpan(2) }) {
                        ToolButton("修改卡号(CUID)", enabled = !busy) { showCuidDialog = true }
                    }
                    item(span = { GridItemSpan(2) }) {
                        ToolButton("写Gen3卡号", enabled = !busy) { showGen3UidDialog = true }
                    }
                    item(span = { GridItemSpan(2) }) {
                        ToolButton("写GDM块", enabled = !busy) { showGdmWriteDialog = true }
                    }
                    item(span = { GridItemSpan(2) }) {
                        ToolButton("加密卡片", enabled = !busy) { showEncryptDialog = true }
                    }
                    item(span = { GridItemSpan(2) }) {
                        ToolButton("格式化(Gen1a)", enabled = !busy, danger = true) {
                            wipeGdm = false
                            showWipeConfirm = true
                        }
                    }
                    item(span = { GridItemSpan(2) }) {
                        ToolButton("格式化(GDM)", enabled = !busy, danger = true) {
                            wipeGdm = true
                            showWipeConfirm = true
                        }
                    }
                }

                // ── 页 3：修复 ──────────────────────────────────
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(span = { GridItemSpan(2) }) { SectionTitle("卡片修复（块 0 损坏 / 密钥丢失时尝试）") }
                    item(span = { GridItemSpan(2) }) {
                        ToolButton("修复块0 (CUID)", enabled = !busy) {
                            repairMethod = Pm3ViewModel.RepairMethod.GEN2_CUID
                        }
                    }
                    item(span = { GridItemSpan(2) }) {
                        ToolButton("修复块0 (Gen1a)", enabled = !busy) {
                            repairMethod = Pm3ViewModel.RepairMethod.GEN1A_UID
                        }
                    }
                    item(span = { GridItemSpan(2) }) {
                        ToolButton("修复块0 (GDM)", enabled = !busy) {
                            repairMethod = Pm3ViewModel.RepairMethod.GDM
                        }
                    }
                    item(span = { GridItemSpan(2) }) {
                        ToolButton("恢复尾块出厂", enabled = !busy) { showTrailerConfirm = true }
                    }
                    item(span = { GridItemSpan(2) }) { SectionTitle("锁定（不可逆！）") }
                    item(span = { GridItemSpan(2) }) {
                        ToolButton("锁Gen3卡号", enabled = !busy, danger = true) { showGen3FreezeConfirm = true }
                    }
                    item(span = { GridItemSpan(2) }) {
                        ToolButton("锁UFUID卡", enabled = !busy, danger = true) { showUfuidConfirm = true }
                    }
                    item(span = { GridItemSpan(2) }) {
                        Text(
                            "UFUID 锁定通过发送固化序列实现；FUID 卡本身为写一次设计，出厂后无法解锁",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }
    }

    // ── 对话框 ──────────────────────────────────────────────

    // 选择 dump 执行 restore
    if (showRestorePicker) {
        AlertDialog(
            onDismissRequest = { showRestorePicker = false },
            title = { Text(if (restoreWithKey) "写有密卡（用配套密钥认证）" else "写空白卡") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (mfcDumps.isEmpty()) {
                        Text("没有可用的 M1 dump\n先在「数据管理」导入或读取一张卡", color = MaterialTheme.colorScheme.outline)
                    } else {
                        mfcDumps.take(8).forEach { d ->
                            TextButton(onClick = {
                                showRestorePicker = false
                                pendingRestore = d to restoreWithKey
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

    pendingRestore?.let { (dump, useKeyAuth) ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("确认写入卡片") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("来源文件：${dump.name}" + (dump.uid?.let { "（UID $it）" } ?: ""))
                    Text("写入会覆盖目标卡片数据，请确认目标卡片属于你且已放在天线上。")
                    CurrentCardTarget(targetUid)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    restoreToCard(vm, dump, useKeyAuth)
                }) { Text("确认写入", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }) { Text("取消") }
            },
        )
    }

    if (showUidDialog) {
        UidInputDialog(
            title = "写 UID 卡（Gen1a 后门卡）",
            hint = "新 UID（8 位十六进制）",
            maxLen = 8,
            targetUid = targetUid,
            onDismiss = { showUidDialog = false },
            onConfirm = { uid ->
                showUidDialog = false
                vm.exec("hf mf csetuid -u $uid")
            },
        )
    }

    if (showCuidDialog) {
        UidInputDialog(
            title = "修改卡号（CUID / Gen2）",
            hint = "新 UID（8 位十六进制）",
            maxLen = 8,
            targetUid = targetUid,
            onDismiss = { showCuidDialog = false },
            onConfirm = { uid ->
                showCuidDialog = false
                vm.cuidChangeUid(uid)
            },
        )
    }

    if (showGen3UidDialog) {
        UidInputDialog(
            title = "写 Gen3 卡号（APDU 魔术卡）",
            hint = "新 UID（8 或 14 位十六进制）",
            maxLen = 14,
            validLengths = setOf(8, 14),
            targetUid = targetUid,
            onDismiss = { showGen3UidDialog = false },
            onConfirm = { uid ->
                showGen3UidDialog = false
                vm.exec("hf mf gen3uid --uid $uid")
            },
        )
    }

    if (showKnownKeyDialog) {
        KnownKeyDialog(
            onDismiss = { showKnownKeyDialog = false },
            onConfirm = { key, keyB, sector ->
                showKnownKeyDialog = false
                val cmd = buildString {
                    append("hf mf autopwn -k $key")
                    if (keyB) append(" -b")
                    if (sector != null) append(" -s $sector")
                }
                vm.exec(cmd)
            },
        )
    }

    if (showManualDialog) {
        ManualAttackDialog(
            onDismiss = { showManualDialog = false },
            onExec = { cmd ->
                showManualDialog = false
                vm.exec(cmd)
            },
        )
    }

    if (showGdmWriteDialog) {
        GdmWriteDialog(
            targetUid = targetUid,
            onDismiss = { showGdmWriteDialog = false },
            onConfirm = { blk, data ->
                showGdmWriteDialog = false
                vm.exec("hf mf gdmsetblk --blk $blk -d $data")
            },
        )
    }

    if (showEncryptDialog) {
        EncryptDialog(
            targetUid = targetUid,
            onDismiss = { showEncryptDialog = false },
            onConfirm = { key ->
                showEncryptDialog = false
                vm.encryptCard(key)
            },
        )
    }

    if (showWipeConfirm) {
        AlertDialog(
            onDismissRequest = { showWipeConfirm = false },
            title = { Text(if (wipeGdm) "格式化 GDM 卡" else "格式化 Gen1a 卡") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("将把卡片恢复为出厂状态（数据清零、密钥恢复默认 FFFFFFFFFFFF）。")
                    CurrentCardTarget(targetUid)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showWipeConfirm = false
                    vm.exec(if (wipeGdm) "hf mf cwipe --gdm" else "hf mf cwipe")
                }) { Text("格式化", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showWipeConfirm = false }) { Text("取消") }
            },
        )
    }

    repairMethod?.let { method ->
        val name = when (method) {
            Pm3ViewModel.RepairMethod.GEN2_CUID -> "CUID"
            Pm3ViewModel.RepairMethod.GEN1A_UID -> "Gen1a"
            Pm3ViewModel.RepairMethod.GDM -> "GDM"
        }
        AlertDialog(
            onDismissRequest = { repairMethod = null },
            title = { Text("修复块 0（$name）") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("将读取块 0，修正 BCC 校验字节后写回。要求 0 块密钥为默认 FFFFFFFFFFFF。")
                    CurrentCardTarget(targetUid)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val m = method
                    repairMethod = null
                    vm.repairBlock0(m)
                }) { Text("修复") }
            },
            dismissButton = {
                TextButton(onClick = { repairMethod = null }) { Text("取消") }
            },
        )
    }

    if (showTrailerConfirm) {
        AlertDialog(
            onDismissRequest = { showTrailerConfirm = false },
            title = { Text("恢复尾块出厂值") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("将把 16 个扇区的尾块恢复为：密钥 FFFFFFFFFFFF + 控制位 FF078069。仅对当前密钥为默认值的扇区有效。")
                    CurrentCardTarget(targetUid)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showTrailerConfirm = false
                    vm.restoreTrailers()
                }) { Text("恢复") }
            },
            dismissButton = {
                TextButton(onClick = { showTrailerConfirm = false }) { Text("取消") }
            },
        )
    }

    if (showGen3FreezeConfirm) {
        AlertDialog(
            onDismissRequest = { showGen3FreezeConfirm = false },
            title = { Text("锁定 Gen3 卡号") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("此操作永久锁定 Gen3 卡的 UID，之后将无法再次修改，卡片变为普通卡。此操作不可逆！")
                    CurrentCardTarget(targetUid)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showGen3FreezeConfirm = false
                    vm.exec("hf mf gen3freeze -y")
                }) { Text("永久锁定", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showGen3FreezeConfirm = false }) { Text("取消") }
            },
        )
    }

    if (showUfuidConfirm) {
        AlertDialog(
            onDismissRequest = { showUfuidConfirm = false },
            title = { Text("锁定 UFUID 卡") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("将向卡片发送 UFUID 固化序列，之后块 0（卡号）永久无法修改，卡片变为普通卡。此操作不可逆！")
                    CurrentCardTarget(targetUid)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showUfuidConfirm = false
                    vm.lockUfuid()
                }) { Text("永久锁定", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showUfuidConfirm = false }) { Text("取消") }
            },
        )
    }
}

/** 用 dump 文件 restore 到卡片；useKeyAuth=true 时用配套密钥文件认证（写有密卡） */
private fun restoreToCard(vm: Pm3ViewModel, dump: DumpFile, useKeyAuth: Boolean) {
    val dumpPath = dump.file.absolutePath
    val uidArg = dump.uid?.let { "--uid $it" } ?: ""
    val cmd = buildString {
        append("hf mf restore --1k $uidArg -f \"$dumpPath\"")
        if (useKeyAuth) {
            val keyFile = dump.uid?.let { uid ->
                dump.file.parentFile?.listFiles()?.firstOrNull {
                    it.name.startsWith("hf-mf-$uid-key") && it.extension == "bin"
                }
            }
            if (keyFile != null) {
                append(" -k \"${keyFile.absolutePath}\" --ka")
            }
        }
    }
    vm.exec(cmd)
}

/** 知一求全：输入已知密钥，自动破解其余密钥并读取整卡 */
@Composable
private fun KnownKeyDialog(
    onDismiss: () -> Unit,
    onConfirm: (key: String, keyB: Boolean, sector: Int?) -> Unit,
) {
    var key by remember { mutableStateOf("") }
    var keyB by remember { mutableStateOf(false) }
    var sector by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("知一求全") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it.filter { c -> c in "0123456789abcdefABCDEF" }.take(12).uppercase() },
                    label = { Text("已知密钥（12 位十六进制）") },
                    singleLine = true,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = !keyB, onClick = { keyB = false })
                    Text("Key A")
                    Spacer(Modifier.size(12.dp))
                    RadioButton(selected = keyB, onClick = { keyB = true })
                    Text("Key B")
                }
                OutlinedTextField(
                    value = sector,
                    onValueChange = { sector = it.filter { c -> c.isDigit() }.take(2) },
                    label = { Text("密钥所在扇区（留空默认 0）") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(key, keyB, sector.toIntOrNull()) },
                enabled = key.length == 12,
            ) { Text("开始破解") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** 手动分析：选择攻击方式并指定已知/目标参数 */
@Composable
private fun ManualAttackDialog(
    onDismiss: () -> Unit,
    onExec: (String) -> Unit,
) {
    val attacks = listOf("nested", "staticnested", "hardnested", "darkside")
    var attack by remember { mutableStateOf(attacks[0]) }
    var key by remember { mutableStateOf("FFFFFFFFFFFF") }
    var blk by remember { mutableStateOf("0") }
    var keyB by remember { mutableStateOf(false) }
    var tblk by remember { mutableStateOf("4") }
    var tKeyB by remember { mutableStateOf(false) }

    val needsKey = attack != "darkside"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("手动分析") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("攻击方式", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    attacks.take(2).forEach { a ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = attack == a, onClick = { attack = a })
                            Text(a, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    attacks.drop(2).forEach { a ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = attack == a, onClick = { attack = a })
                            Text(a, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                if (needsKey) {
                    OutlinedTextField(
                        value = key,
                        onValueChange = { key = it.filter { c -> c in "0123456789abcdefABCDEF" }.take(12).uppercase() },
                        label = { Text("已知密钥（12 位十六进制）") },
                        singleLine = true,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = blk,
                            onValueChange = { blk = it.filter { c -> c.isDigit() }.take(2) },
                            label = { Text("已知块号") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.size(8.dp))
                        RadioButton(selected = !keyB, onClick = { keyB = false })
                        Text("A")
                        RadioButton(selected = keyB, onClick = { keyB = true })
                        Text("B")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = tblk,
                            onValueChange = { tblk = it.filter { c -> c.isDigit() }.take(2) },
                            label = { Text("目标块号") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.size(8.dp))
                        RadioButton(selected = !tKeyB, onClick = { tKeyB = false })
                        Text("A")
                        RadioButton(selected = tKeyB, onClick = { tKeyB = true })
                        Text("B")
                    }
                } else {
                    Text("Darkside 攻击无需已知密钥，直接从卡上采集数据破解（约需数分钟）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val cmd = if (attack == "darkside") {
                        "hf mf darkside"
                    } else {
                        buildString {
                            append("hf mf $attack --blk ${blk.toIntOrNull() ?: 0}")
                            append(if (keyB) " -b" else " -a")
                            append(" -k $key")
                            append(" --tblk ${tblk.toIntOrNull() ?: 4}")
                            append(if (tKeyB) " --tb" else " --ta")
                        }
                    }
                    onExec(cmd)
                },
                enabled = !needsKey || key.length == 12,
            ) { Text("执行") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** 写 GDM 块 */
@Composable
private fun GdmWriteDialog(
    targetUid: String?,
    onDismiss: () -> Unit,
    onConfirm: (blk: Int, data: String) -> Unit,
) {
    var blk by remember { mutableStateOf("") }
    var data by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("写 GDM 块") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = blk,
                    onValueChange = { blk = it.filter { c -> c.isDigit() }.take(2) },
                    label = { Text("块号（0-63）") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = data,
                    onValueChange = { data = it.filter { c -> c in "0123456789abcdefABCDEF" }.take(32).uppercase() },
                    label = { Text("数据（32 位十六进制）") },
                    singleLine = true,
                )
                Text("使用 GDM 魔术卡专用唤醒方式写入，无需密钥。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline)
                CurrentCardTarget(targetUid)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(blk.toInt(), data) },
                enabled = blk.toIntOrNull() != null && data.length == 32,
            ) { Text("写入") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** 加密卡片：把所有扇区密钥改为指定密钥 */
@Composable
private fun EncryptDialog(
    targetUid: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var key by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("加密卡片") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it.filter { c -> c in "0123456789abcdefABCDEF" }.take(12).uppercase() },
                    label = { Text("新密钥（12 位十六进制）") },
                    singleLine = true,
                )
                Text("将把 16 个扇区的 A/B 密钥都改为该密钥（控制位 FF078069 不变）。仅对当前密钥为默认值的扇区有效，请务必牢记新密钥！",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
                CurrentCardTarget(targetUid)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(key) }, enabled = key.length == 12) { Text("加密") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun UidInputDialog(
    title: String,
    hint: String,
    maxLen: Int = 8,
    validLengths: Set<Int> = setOf(8),
    targetUid: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var uid by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CurrentCardTarget(targetUid)
                OutlinedTextField(
                    value = uid,
                    onValueChange = { uid = it.filter { c -> c in "0123456789abcdefABCDEF" }.take(maxLen).uppercase() },
                    label = { Text(hint) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(uid) }, enabled = uid.length in validLengths) { Text("执行") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun CurrentCardTarget(uid: String?) {
    Text(
        uid?.let { "最近识别的目标卡：UID $it。操作前请确认天线上仍是这张卡。" }
            ?: "尚未识别目标卡。请先确认目标卡已放在天线上；操作前建议先识卡。",
        style = MaterialTheme.typography.bodySmall,
        color = if (uid == null) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.outline,
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}
