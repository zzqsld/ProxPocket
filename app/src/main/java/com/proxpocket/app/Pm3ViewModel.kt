package com.proxpocket.app

import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.proxpocket.app.pm3.DumpFile
import com.proxpocket.app.pm3.MfcDump
import com.proxpocket.app.pm3.Pm3Core
import com.proxpocket.app.pm3.TransitCard
import com.proxpocket.app.usb.Pm3Bridge
import com.proxpocket.app.usb.UsbCdcDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class Pm3ViewModel(app: Application) : AndroidViewModel(app) {

    data class UsbDeviceItem(
        val name: String,
        val deviceName: String,
        val granted: Boolean,
        val device: UsbDevice,
    )

    sealed class ConnState {
        data object Disconnected : ConnState()
        data object Connecting : ConnState()
        data class Connected(val deviceName: String, val version: String) : ConnState()
        data class Error(val message: String) : ConnState()
    }

    private val usbManager = app.getSystemService(Context.USB_SERVICE) as UsbManager

    private val _devices = MutableStateFlow<List<UsbDeviceItem>>(emptyList())
    val devices: StateFlow<List<UsbDeviceItem>> = _devices.asStateFlow()

    private val _connState = MutableStateFlow<ConnState>(ConnState.Disconnected)
    val connState: StateFlow<ConnState> = _connState.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** 终端输出滚动日志 */
    private val _log = MutableStateFlow<List<String>>(listOf("ProxPocket 终端就绪"))
    val log: StateFlow<List<String>> = _log.asStateFlow()

    /** 最近一次 hf search 摘要（UID/ATQA/SAK 等），供卡片信息栏显示 */
    private val _cardInfo = MutableStateFlow<Map<String, String>>(emptyMap())
    val cardInfo: StateFlow<Map<String, String>> = _cardInfo.asStateFlow()

    /** 最近一次 lf search 解析结果（EM4100/HID 等），供低频页显示 */
    private val _lfInfo = MutableStateFlow<Map<String, String>>(emptyMap())
    val lfInfo: StateFlow<Map<String, String>> = _lfInfo.asStateFlow()

    /** 工作目录中的 dump/密钥文件列表（数据管理页） */
    private val _dumpFiles = MutableStateFlow<List<com.proxpocket.app.pm3.DumpFile>>(emptyList())
    val dumpFiles: StateFlow<List<com.proxpocket.app.pm3.DumpFile>> = _dumpFiles.asStateFlow()

    /** 一次性操作提示（读卡成功/解卡完成等），UI 层用 Toast 展示 */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    fun consumeNotice() {
        _notice.value = null
    }

    fun clearLog() {
        _log.value = emptyList()
    }

    /** 当前在查看的 dump（数据管理页 → dump 查看页 传递） */
    val selectedDump = androidx.compose.runtime.mutableStateOf<com.proxpocket.app.pm3.DumpFile?>(null)

    /** 原生资源是否就绪（首启释放完成前禁止跑 autopwn 等依赖资源的命令） */
    val coreReady: StateFlow<Boolean> = com.proxpocket.app.pm3.Pm3Assets.ready

    fun selectDump(d: com.proxpocket.app.pm3.DumpFile) {
        selectedDump.value = d
    }

    fun refreshDumpFiles() {
        _dumpFiles.value = com.proxpocket.app.pm3.DumpFile.scan(
            getApplication<Application>().filesDir
        )
    }

    suspend fun saveDumpEdits(
        source: DumpFile,
        blocks: List<MfcDump.Block>,
        overwrite: Boolean,
    ): File {
        val savedFile = withContext(Dispatchers.IO) {
            val destination = if (overwrite) source.file else editedDumpCopy(source.file)
            val data = MfcDump.encode(blocks, source.file.extension.equals("eml", ignoreCase = true))
            writeAtomically(destination, data)
            refreshDumpFiles()
            destination
        }
        _notice.value = if (overwrite) {
            "已覆盖保存：${savedFile.name}"
        } else {
            "已保存编辑副本：${savedFile.name}"
        }
        return savedFile
    }

    private fun editedDumpCopy(source: File): File {
        val extension = source.extension
        val suffix = if (extension.isBlank()) "" else ".$extension"
        var index = 1
        var candidate: File
        do {
            val version = if (index == 1) "" else "-$index"
            candidate = File(source.parentFile, "${source.nameWithoutExtension}-edited$version$suffix")
            index++
        } while (candidate.exists())
        return candidate
    }

    private fun writeAtomically(destination: File, data: ByteArray) {
        val parent = destination.parentFile ?: error("dump 文件没有父目录")
        val temporary = File.createTempFile("prox-dump-", ".tmp", parent)
        try {
            temporary.writeBytes(data)
            try {
                Files.move(
                    temporary.toPath(),
                    destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    temporary.toPath(),
                    destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    fun deleteDump(item: com.proxpocket.app.pm3.DumpFile) {
        item.file.delete()
        refreshDumpFiles()
        appendLog("[=] 已删除 ${item.name}")
    }

    fun importDump(uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val name = queryDisplayName(uri) ?: "import-${System.currentTimeMillis()}.bin"
                val out = java.io.File(getApplication<Application>().filesDir, name)
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                    out.outputStream().use { input.copyTo(it) }
                } ?: error("无法读取文件")
                appendLog("[+] 已导入 $name")
            }.onFailure { appendLog("[!] 导入失败: ${it.message}") }
            refreshDumpFiles()
        }
    }

    fun exportDump(item: com.proxpocket.app.pm3.DumpFile, uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use { output ->
                    item.file.inputStream().use { it.copyTo(output) }
                } ?: error("无法写入目标")
                appendLog("[+] 已导出 ${item.name}")
            }.onFailure { appendLog("[!] 导出失败: ${it.message}") }
        }
    }

    private fun queryDisplayName(uri: android.net.Uri): String? = runCatching {
        getApplication<Application>().contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
    }.getOrNull()

    private var usb: UsbCdcDevice? = null
    private var bridge: Pm3Bridge? = null
    private var handle = 0L
    private var pendingDevice: UsbDevice? = null
    private var activeUsbDeviceName: String? = null
    private val cmdMutex = Mutex()
    @Volatile
    private var nativeReady = false

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            @Suppress("DEPRECATION")
            val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            } else {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            }

            if (intent.action == UsbManager.ACTION_USB_DEVICE_DETACHED) {
                if (device != null &&
                    (device.deviceName == activeUsbDeviceName ||
                        device.deviceName == pendingDevice?.deviceName)
                ) {
                    disconnectInternal()
                    pendingDevice = null
                    _connState.value = ConnState.Error("USB 设备已断开")
                    _notice.value = "PM3 USB 设备已断开"
                    appendLog("[!] PM3 USB 设备已断开")
                }
                refreshDevices()
                return
            }

            if (intent.action != ACTION_USB_PERMISSION) return
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            Log.i(TAG, "usb permission granted=$granted for ${device?.deviceName}")
            if (granted && device != null) {
                doConnect(device)
            } else {
                pendingDevice = null
                activeUsbDeviceName = null
                _connState.value = ConnState.Error("USB 权限被拒绝")
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        ContextCompat.registerReceiver(
            app, permissionReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
        nativeReady = Pm3Core.nativeAvailable && runCatching {
            Pm3Core.nativeInit(app.filesDir.absolutePath)
        }.getOrDefault(false)
        if (!nativeReady) {
            appendLog("[!] 原生库加载失败，请检查安装包")
        }
        // 首次启动释放 pm3 运行时资源（字典/hardnested 表等），后台执行
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { com.proxpocket.app.pm3.Pm3Assets.extractIfNeeded(app) }
                .onFailure { appendLog("[!] 资源释放失败: ${it.message}") }
        }
        refreshDevices()
        refreshDumpFiles()
    }

    override fun onCleared() {
        getApplication<Application>().unregisterReceiver(permissionReceiver)
        disconnectInternal()
        super.onCleared()
    }

    fun refreshDevices() {
        val list = usbManager.deviceList.values
            .filter { UsbCdcDevice.looksLikePm3(it) }
            .map {
                UsbDeviceItem(
                    name = UsbCdcDevice.displayName(it),
                    deviceName = it.deviceName,
                    granted = usbManager.hasPermission(it),
                    device = it,
                )
            }
        _devices.value = list
    }

    fun connect(item: UsbDeviceItem) {
        if (_connState.value is ConnState.Connecting) return
        clearCardState()
        activeUsbDeviceName = item.device.deviceName
        if (usbManager.hasPermission(item.device)) {
            doConnect(item.device)
        } else {
            pendingDevice = item.device
            _connState.value = ConnState.Connecting
            val intent = PendingIntent.getBroadcast(
                getApplication(), 0,
                Intent(ACTION_USB_PERMISSION),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            usbManager.requestPermission(item.device, intent)
        }
    }

    private fun doConnect(device: UsbDevice) {
        _connState.value = ConnState.Connecting
        activeUsbDeviceName = device.deviceName
        pendingDevice = null
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    withTimeout(45_000) {
                        disconnectInternal()
                        activeUsbDeviceName = device.deviceName
                        appendLog("[=] 打开 USB 设备...")
                        val u = UsbCdcDevice(usbManager, device)
                        if (!u.open()) error("无法打开 USB 设备（检查 OTG 连接）")
                        appendLog("[=] 启动数据桥...")
                        val b = Pm3Bridge(u)
                        val port = b.start()
                        // 给 accept 线程一点启动时间
                        Thread.sleep(200)
                        appendLog("[=] 连接 pm3 核心 (tcp:127.0.0.1:$port)...")
                        val h = Pm3Core.nativeOpen("tcp:127.0.0.1:$port")
                        if (h == 0L) {
                            b.close()
                            u.close()
                            error("pm3 客户端握手失败（协议不匹配？）")
                        }
                        usb = u
                        bridge = b
                        handle = h
                        appendLog("[=] 读取设备信息...")
                        val version = execLocked("hw version")
                        Triple(UsbCdcDevice.displayName(device), version, Unit)
                    }
                }
            }
            result.onSuccess { (name, version, _) ->
                _connState.value = ConnState.Connected(name, version)
                appendLog("[+] 设备已连接: $name")
            }.onFailure { e ->
                disconnectInternal()
                val msg = if (e is kotlinx.coroutines.TimeoutCancellationException)
                    "连接超时（设备无响应）" else (e.message ?: "连接失败")
                _connState.value = ConnState.Error(msg)
                appendLog("[!] $msg")
            }
        }
    }

    fun disconnect() {
        disconnectInternal()
        _connState.value = ConnState.Disconnected
        appendLog("[=] 已断开连接")
    }

    private fun disconnectInternal() {
        if (handle != 0L) {
            runCatching { Pm3Core.nativeClose(handle) }
            handle = 0L
        }
        bridge?.close()
        bridge = null
        usb?.close()
        usb = null
        activeUsbDeviceName = null
        clearCardState()
    }

    private fun clearCardState() {
        _cardInfo.value = emptyMap()
        _lfInfo.value = emptyMap()
    }

    /**
     * 执行一条 pm3 命令，输出追加到终端日志并返回。
     * 长命令（autopwn 等）在 IO 线程执行，期间 busy=true。
     */
    fun exec(cmd: String, showInLog: Boolean = true) {
        if (handle == 0L) {
            appendLog("[!] 设备未连接")
            _notice.value = "未连接设备，请先在首页连接 PM3"
            return
        }
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                clearCardStateForScan(cmd)
                if (showInLog) appendLog("pm3 --> $cmd")
                val out = withContext(Dispatchers.IO) { execLocked(cmd) }
                if (showInLog) appendLog(out.trimEnd())
                parseCardInfo(out)
                parseLfInfo(out)
                notifyResult(cmd, out)
                if (out.contains("[!]") || out.startsWith("!!")) {
                    _notice.value = "执行未成功，详情见终端日志"
                }
                refreshDumpFiles()
            } finally {
                _busy.value = false
            }
        }
    }

    /** 执行命令并挂起返回输出（供自动化流程组合使用） */
    suspend fun execAsync(cmd: String): String {
        if (handle == 0L) return "!! not connected"
        _busy.value = true
        return try {
            clearCardStateForScan(cmd)
            val out = withContext(Dispatchers.IO) {
                appendLog("pm3 --> $cmd")
                val r = execLocked(cmd)
                appendLog(r.trimEnd())
                r
            }
            parseCardInfo(out)
            parseLfInfo(out)
            notifyResult(cmd, out)
            refreshDumpFiles()
            out
        } finally {
            _busy.value = false
        }
    }

    private fun clearCardStateForScan(cmd: String) {
        val normalized = cmd.trim().lowercase()
        if (normalized.startsWith("hf search") ||
            normalized.startsWith("hf 14a info") ||
            normalized.startsWith("hf 14a reader") ||
            normalized.startsWith("hf mf info") ||
            normalized.startsWith("hf mf autopwn")
        ) {
            _cardInfo.value = emptyMap()
        }
        if (normalized.startsWith("lf search")) {
            _lfInfo.value = emptyMap()
        }
    }

    /** 检查连接状态；未连接时给提示并返回 false */
    private fun requireConnection(): Boolean {
        if (handle == 0L) {
            appendLog("[!] 设备未连接")
            _notice.value = "未连接设备，请先在首页连接 PM3"
            return false
        }
        return true
    }

    /**
     * CUID（Gen2 魔术卡）改卡号：
     * 读块 0 → 替换前 4 字节 UID + 第 5 字节 BCC（其余字节保留）→ 写回。
     */
    fun cuidChangeUid(newUid: String) {
        viewModelScope.launch {
            if (!requireConnection()) return@launch
            val uidBytes = newUid.chunked(2).map { it.toInt(16) }
            if (uidBytes.size != 4) {
                appendLog("[!] UID 必须是 8 位十六进制")
                return@launch
            }
            val bcc = uidBytes.fold(0) { acc, b -> acc xor b }
            val rd = execAsync("hf mf rdbl --blk 0 -k FFFFFFFFFFFF")
            val dataHex = Regex("""(?m)^\s*(?:\d+\s*\|\s*)?([0-9A-Fa-f]{32})\s*$""")
                .find(rd)?.groupValues?.get(1)
                ?: Regex("""\b([0-9A-Fa-f]{32})\b""").find(rd)?.groupValues?.get(1)
            if (dataHex == null) {
                appendLog("[!] 读取块 0 失败（卡片不在天线上或密钥非默认）")
                _notice.value = "读取块 0 失败，详情见终端日志"
                return@launch
            }
            val tail = dataHex.substring(10) // 保留原 BCC 之后的字节（SAK/ATQA/厂商数据）
            val newBlock = newUid.uppercase() + "%02X".format(bcc) + tail
            appendLog("[=] 新块 0: $newBlock")
            execAsync("hf mf wrbl --blk 0 -k FFFFFFFFFFFF -d $newBlock")
            appendLog("[+] CUID 卡号修改完成，请重新读卡确认")
            _notice.value = "CUID 卡号修改完成"
        }
    }

    /** 修复块 0 的方式 */
    enum class RepairMethod { GEN2_CUID, GEN1A_UID, GDM }

    /**
     * 卡片修复：读取块 0 → 修正 BCC（前 4 字节异或）→ 按卡的类型写回。
     * 用于块 0 BCC 损坏导致无法正常选卡的魔术卡。
     */
    fun repairBlock0(method: RepairMethod) {
        viewModelScope.launch {
            if (!requireConnection()) return@launch
            val rd = execAsync("hf mf rdbl --blk 0 -k FFFFFFFFFFFF")
            val dataHex = Regex("""(?m)^\s*(?:\d+\s*\|\s*)?([0-9A-Fa-f]{32})\s*$""")
                .find(rd)?.groupValues?.get(1)
                ?: Regex("""\b([0-9A-Fa-f]{32})\b""").find(rd)?.groupValues?.get(1)
            if (dataHex == null) {
                appendLog("[!] 读取块 0 失败：请确认卡片在天线上且 0 块密钥为默认 FFFFFFFFFFFF")
                _notice.value = "读取块 0 失败，详情见终端日志"
                return@launch
            }
            val uid = dataHex.substring(0, 8)
            val uidBytes = uid.chunked(2).map { it.toInt(16) }
            val bcc = uidBytes.fold(0) { acc, b -> acc xor b }
            val fixed = uid + "%02X".format(bcc) + dataHex.substring(10)
            if (fixed == dataHex.uppercase()) {
                appendLog("[=] 块 0 BCC 正确，无需修复")
            } else {
                appendLog("[=] 修正块 0: $fixed")
            }
            val cmd = when (method) {
                RepairMethod.GEN2_CUID -> "hf mf wrbl --blk 0 -k FFFFFFFFFFFF -d $fixed"
                RepairMethod.GEN1A_UID -> "hf mf csetblk --blk 0 -d $fixed"
                RepairMethod.GDM -> "hf mf gdmsetblk --blk 0 -d $fixed"
            }
            execAsync(cmd)
            appendLog("[+] 修复完成，请重新读卡确认")
            _notice.value = "块 0 修复完成，请重新读卡确认"
        }
    }

    /** 恢复全部扇区尾块为出厂值（密钥 FFFFFFFFFFFF / 控制位 FF078069），逐块执行 */
    fun restoreTrailers() {
        viewModelScope.launch {
            if (!requireConnection()) return@launch
            appendLog("[=] 恢复 16 个扇区尾块为出厂值...")
            var ok = 0
            for (s in 0 until 16) {
                val blk = s * 4 + 3
                val out = execAsync(
                    "hf mf wrbl --blk $blk -k FFFFFFFFFFFF -d FFFFFFFFFFFFFFFF078069FFFFFFFFFFFF"
                )
                if (!out.contains("Can't", ignoreCase = true) &&
                    !out.contains("error", ignoreCase = true)
                ) ok++
            }
            appendLog("[+] 尾块恢复完成（$ok/16），失败的扇区可能密钥非默认")
            _notice.value = "尾块恢复完成（$ok/16）"
        }
    }

    /** 加密卡片：把全部扇区尾块的 A/B 密钥改为指定密钥（控制位保持 FF078069） */
    fun encryptCard(newKey: String) {
        viewModelScope.launch {
            if (!requireConnection()) return@launch
            if (!Regex("""[0-9A-Fa-f]{12}""").matches(newKey)) {
                appendLog("[!] 密钥必须是 12 位十六进制")
                return@launch
            }
            val k = newKey.uppercase()
            appendLog("[=] 将 16 个扇区密钥写为 $k ...")
            var ok = 0
            for (s in 0 until 16) {
                val blk = s * 4 + 3
                val out = execAsync(
                    "hf mf wrbl --blk $blk -k FFFFFFFFFFFF -d ${k}FF078069$k"
                )
                if (!out.contains("Can't", ignoreCase = true)) ok++
            }
            appendLog("[+] 加密完成（$ok/16），请妥善保管密钥")
            _notice.value = "加密完成（$ok/16 个扇区）"
        }
    }

    /** 锁定 UFUID 卡（写一次型魔术卡）：发送锁定序列后块 0 永久固化，不可逆 */
    fun lockUfuid() {
        viewModelScope.launch {
            if (!requireConnection()) return@launch
            appendLog("[=] 发送 UFUID 锁定序列（不可逆）...")
            val seq = listOf(
                "hf 14a raw -ak -b 7 40",
                "hf 14a raw -ak 43",
                "hf 14a raw -ak E0 00 39 F7",
                "hf 14a raw -ak E1 00 E1 EE",
                "hf 14a raw -ak 85 00 00 00 00 00 00 00 00 00 00 00 00 00 00 08 18 47",
                "hf 14a raw -a 52",
            )
            for (cmd in seq) execAsync(cmd)
            appendLog("[+] UFUID 锁定序列已发送，请重新读卡确认（卡号应已永久固化）")
            _notice.value = "UFUID 锁定完成，请重新读卡确认"
        }
    }

    /** CPU 卡深度识别：hf search + 两次 hf 14a info（部分 CPU 卡第二次 select 后才上报完整信息） */
    fun cpuDeepInfo() {
        viewModelScope.launch {
            if (!requireConnection()) return@launch
            execAsync("hf search")
            execAsync("hf 14a info")
            execAsync("hf 14a info")
            _notice.value = "深度识别完成"
        }
    }

    // ── 公交卡读取 ─────────────────────────────────────────

    private val _transitRunning = MutableStateFlow(false)
    val transitRunning: StateFlow<Boolean> = _transitRunning.asStateFlow()

    private val _transitStep = MutableStateFlow("")
    val transitStep: StateFlow<String> = _transitStep.asStateFlow()

    private val _transitResult = MutableStateFlow<com.proxpocket.app.pm3.TransitCard.Result?>(null)
    val transitResult: StateFlow<com.proxpocket.app.pm3.TransitCard.Result?> = _transitResult.asStateFlow()

    /** 发一条 APDU；first=带选卡，last=收完后关闭场（恢复天线） */
    private suspend fun apdu(hex: String, first: Boolean = false, last: Boolean = false): ByteArray? {
        val flags = buildString {
            if (first) append(" -s")
            if (!last) append(" -k")
        }
        val out = execAsync("hf 14a apdu$flags -d $hex")
        return com.proxpocket.app.pm3.TransitCard.parseApduResponse(out)
    }

    /** 读取 PBOC/交通联合 CPU 公交卡：余额 + 卡号 + 有效期 + 最近交易 */
    fun readTransitPboc() {
        if (handle == 0L) {
            appendLog("[!] 设备未连接")
            _notice.value = "未连接设备，请先在首页连接 PM3"
            return
        }
        viewModelScope.launch {
            val T = com.proxpocket.app.pm3.TransitCard
            _transitRunning.value = true
            _transitResult.value = null
            var balance: String? = null
            var serial: String? = null
            var validity: String? = null
            var version: String? = null
            val txns = mutableListOf<com.proxpocket.app.pm3.TransitCard.Txn>()
            val aids = mutableListOf<String>()
            try {
                _transitStep.value = "选择 PSE 支付环境..."
                var r = apdu(T.SELECT_PSE, first = true)
                if (!T.isOk(r)) {
                    appendLog("[=] PSE 不存在，尝试直接选 MF")
                    r = apdu(T.SELECT_MF)
                }
                if (!T.isOk(r)) {
                    _transitResult.value = TransitCard.Result(false, "卡片不支持 ISO-DEP/PBOC（不是 CPU 公交卡？）")
                    return@launch
                }

                _transitStep.value = "读取余额..."
                var bal = apdu(T.GET_BALANCE_EP)
                if (!T.isOk(bal)) bal = apdu(T.GET_BALANCE_ED)
                balance = T.parseBalance(bal)

                _transitStep.value = "扫描应用列表 (PSE 目录)..."
                for (rec in 1..6) {
                    val recResp = apdu(T.readPseRecord(rec))
                    if (!T.isOk(recResp)) break
                    T.findAidInRecord(T.dataOf(recResp!!))?.let { aids.add(it) }
                }
                if (aids.isNotEmpty()) {
                    appendLog("[+] 发现 ${aids.size} 个支付应用: ${aids.joinToString()}")
                }

                // 应用列表为空时用常见回退入口
                val targets: List<String> = aids.ifEmpty {
                    appendLog("[=] PSE 目录为空，尝试常见回退入口")
                    listOf(T.SELECT_PXX, T.SELECT_EP)
                }

                var gotInfo = false
                for ((idx, target) in targets.withIndex()) {
                    if (idx >= 3) break // 最多试 3 个应用
                    _transitStep.value = "选择应用 ${idx + 1}/${targets.size}..."
                    // 每次先回 PSE/MF 再选目标应用（与 NFCard 流程一致）
                    if (apdu(T.SELECT_PSE).let { !T.isOk(it) }) apdu(T.SELECT_MF)
                    val sel = if (target.startsWith("00A4")) apdu(target) else apdu(T.selectAid(target))
                    if (!T.isOk(sel)) continue

                    if (balance == null) {
                        var b2 = apdu(T.GET_BALANCE_EP)
                        if (!T.isOk(b2)) b2 = apdu(T.GET_BALANCE_ED)
                        balance = T.parseBalance(b2)
                    }
                    if (!gotInfo) {
                        val info = apdu(T.READ_INFO_SFI21)
                        val (s, v, d) = T.parseInfo(info)
                        if (s != null) {
                            serial = s; version = v; validity = d; gotInfo = true
                        }
                    }
                    _transitStep.value = "读取交易记录..."
                    for (rec in 1..10) {
                        val lr = apdu(T.readLogRecord(rec), last = (rec == 10 && idx == targets.lastIndex))
                        if (!T.isOk(lr)) break
                        T.parseTxn(T.dataOf(lr!!))?.let { txns.add(it) }
                    }
                }

                // 关掉天线场（若上面的流程没有发出 last 指令）
                apdu(T.GET_BALANCE_EP, last = true)

                val ok = balance != null || serial != null || txns.isNotEmpty()
                _transitResult.value = TransitCard.Result(
                    success = ok,
                    message = if (ok) null else "未能读到余额或记录（可能是不支持的卡种）",
                    aids = aids,
                    balance = balance,
                    serial = serial,
                    validity = validity,
                    version = version,
                    txns = txns.distinctBy { it.dateTime + it.amount + it.terminal }.takeLast(10).reversed(),
                )
                _notice.value = if (ok) {
                    "公交卡读取完成" + (balance?.let { "，余额 $it" } ?: "")
                } else {
                    "未能读到公交卡信息（不支持的卡种？）"
                }
            } finally {
                _transitRunning.value = false
                _transitStep.value = ""
            }
        }
    }

    /** 读取 Suica / PASMO 乘车记录（FeliCa 服务 0x090F，免密） */
    fun readSuica() {
        if (handle == 0L) {
            appendLog("[!] 设备未连接")
            _notice.value = "未连接设备，请先在首页连接 PM3"
            return
        }
        viewModelScope.launch {
            _transitRunning.value = true
            _transitResult.value = null
            try {
                _transitStep.value = "探测 FeliCa 卡..."
                execAsync("hf felica reader")
                val entries = mutableListOf<com.proxpocket.app.pm3.Suica.Entry>()
                // 服务 0x090F 小端为 0F09；读块 0-9（最多 10 条历史）
                for (blk in 0 until 10) {
                    _transitStep.value = "读取乘车记录 ${blk + 1}/10..."
                    val out = execAsync("hf felica rdbl --sn 01 --scl 0F09 --bn 01 --ble 800$blk")
                    val block = com.proxpocket.app.pm3.Suica.extractBlock(out) ?: break
                    com.proxpocket.app.pm3.Suica.parseEntry(block)?.let { entries.add(it) }
                }
                _transitResult.value = if (entries.isEmpty()) {
                    TransitCard.Result(false, "没有读到乘车记录（卡不在天线上，或不是 Suica/PASMO）")
                } else {
                    TransitCard.Result(
                        success = true,
                        message = "Suica / PASMO · ${entries.size} 条记录（站点为线路:站代码）",
                        balance = entries.first().balance?.toString(),
                        balanceUnit = "JPY",
                        suicaEntries = entries,
                    )
                }
                _notice.value = if (entries.isEmpty()) "没有读到乘车记录" else "Suica 读取完成（${entries.size} 条记录）"
            } finally {
                _transitRunning.value = false
                _transitStep.value = ""
            }
        }
    }

    /** 读取八达通/深圳通 FeliCa 版余额（免密服务 0x0117 / 0x0118） */
    fun readFelicaBalance() {
        if (handle == 0L) {
            appendLog("[!] 设备未连接")
            _notice.value = "未连接设备，请先在首页连接 PM3"
            return
        }
        viewModelScope.launch {
            _transitRunning.value = true
            _transitResult.value = null
            try {
                _transitStep.value = "探测 FeliCa 卡..."
                execAsync("hf felica reader")
                // 八达通: 系统 0x8008 服务 0x0117；深圳通互通行: 系统 0x8005 服务 0x0118
                // 服务代码在线上为小端：0x0117 -> 1701
                var found: String? = null
                var name = "八达通"
                for ((svc, label) in listOf("1701" to "八达通", "1801" to "深圳通(FeliCa)")) {
                    _transitStep.value = "读取 $label 余额..."
                    val out = execAsync("hf felica rdbl --sn 01 --scl $svc --bn 01 --ble 8000")
                    val hex = Regex("""\b([0-9A-Fa-f]{32})\b""").find(out)?.groupValues?.get(1)
                    if (hex != null && !hex.startsWith("0000000000000000")) {
                        found = hex
                        name = label
                        break
                    }
                }
                if (found == null) {
                    _transitResult.value = com.proxpocket.app.pm3.TransitCard.Result(
                        false, "未读到 FeliCa 余额（卡不在天线上，或不是八达通/深圳通FeliCa版）"
                    )
                    _notice.value = "未读到 FeliCa 余额"
                } else {
                    val raw = com.proxpocket.app.pm3.TransitCard.run {
                        val b = ByteArray(16) { found.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
                        beInt(b, 0, 4)
                    }
                    val bal = (raw - 350) / 10.0
                    _transitResult.value = com.proxpocket.app.pm3.TransitCard.Result(
                        success = true,
                        balance = "%.1f".format(bal),
                        message = "$name 余额（含押金 350 已扣除）",
                    )
                    _notice.value = "$name 余额：%.1f".format(bal)
                }
            } finally {
                _transitRunning.value = false
                _transitStep.value = ""
            }
        }
    }

    private fun execLocked(cmd: String): String = runCatching {
        // pm3 原生客户端非线程安全，串行执行
        synchronized(this) {
            Pm3Core.nativeExec(handle, cmd)
        }
    }.getOrElse { "!! exec error: ${it.message}" }

    private fun appendLog(line: String) {
        if (line.isBlank()) return
        val lines = line.split('\n')
        lines.forEach { Log.i(TAG, it) }
        _log.value = (_log.value + lines).takeLast(MAX_LOG_LINES)
    }

    private fun parseCardInfo(out: String) {
        // 防御：去掉可能残留的 ANSI 转义
        val clean = out.replace(Regex("""\[[0-9;]*[A-Za-z]"""), "")
        val map = _cardInfo.value.toMutableMap()
        Regex("""UID\s*[:=]\s*([0-9A-Fa-f]{2}(?:\s?[0-9A-Fa-f]{2}){3,9})""").find(clean)?.let {
            map["UID"] = it.groupValues[1].replace(" ", "").uppercase()
        }
        Regex("""ATQA\s*[:=]\s*([0-9A-Fa-f]{2}(?:\s?[0-9A-Fa-f]{2})?)""").find(clean)?.let {
            map["ATQA"] = it.groupValues[1].trim().uppercase()
        }
        Regex("""SAK\s*[:=]\s*([0-9A-Fa-f]{2})""").find(clean)?.let {
            map["SAK"] = it.groupValues[1].trim().uppercase()
        }
        Regex("""TYPE\s*[:=]\s*(.+)""").find(clean)?.let {
            map["TYPE"] = it.groupValues[1].trim()
        }
        if (map.isNotEmpty()) _cardInfo.value = map
    }

    private fun parseLfInfo(out: String) {
        com.proxpocket.app.pm3.LfParser.parse(out)?.let { parsed ->
            _lfInfo.value = parsed
        }
    }

    /** 命令完成后检查结果，给 UI 发一次性 Toast 提示 */
    private fun notifyResult(cmd: String, out: String) {
        val uid = _cardInfo.value["UID"]
        when {
            // autopwn 解卡完成（密钥+dump 都已保存）
            cmd.startsWith("hf mf autopwn") && out.contains("to binary file") -> {
                _notice.value = "解卡完成" + (uid?.let { "：UID $it" } ?: "") + "，可前往数据管理查看"
            }
            // 保存了 dump 文件
            out.contains("Saved") && out.contains("to binary file") -> {
                _notice.value = "读卡完成，dump 已保存到数据管理"
            }
            // 高频读卡识别到 UID
            (cmd.startsWith("hf search") || cmd.startsWith("hf 14a info") ||
                cmd.startsWith("hf mf info") || cmd.startsWith("hf 14a reader")) &&
                out.contains("UID") && uid != null -> {
                _notice.value = "读到卡片：UID $uid"
            }
            // 低频识别到卡
            cmd.startsWith("lf search") && _lfInfo.value["ID"] != null -> {
                _notice.value = "读到低频卡：${_lfInfo.value["TYPE"] ?: ""} ID ${_lfInfo.value["ID"]}"
            }
        }
    }

    companion object {
        private const val TAG = "Pm3ViewModel"
        private const val MAX_LOG_LINES = 2000
        private const val ACTION_USB_PERMISSION = "com.proxpocket.app.USB_PERMISSION"
    }
}
