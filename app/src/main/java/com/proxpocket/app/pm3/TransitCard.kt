package com.proxpocket.app.pm3

/**
 * PBOC / 交通联合 CPU 公交卡读取：APDU 会话构造与响应解析。
 * 流程移植自开源 NFCard 项目（GPL-3.0）的 HardReader/PbocCard 通用流程：
 * 选 PSE(1PAY.SYS.DDF01) → 读 PSE 目录找 AID → 选应用 → 读余额(805C)
 * → 读卡片信息文件(SFI 21) → 读交易记录(SFI 24, 每条 23 字节)。
 */
object TransitCard {

    data class Txn(
        val dateTime: String,
        val expense: Boolean,
        val amount: String,
        val overdraft: String?,
        val terminal: String,
    )

    data class Result(
        val success: Boolean,
        val message: String? = null,
        val aids: List<String> = emptyList(),
        val balance: String? = null,
        val balanceUnit: String = "¥",
        val serial: String? = null,
        val validity: String? = null,
        val version: String? = null,
        val txns: List<Txn> = emptyList(),
        val suicaEntries: List<com.proxpocket.app.pm3.Suica.Entry> = emptyList(),
    )

    // ── APDU 构造 ────────────────────────────────────────────
    const val SELECT_PSE = "00A404000E315041592E5359532E4444463031"
    const val SELECT_MF = "00A40000023F00"
    const val SELECT_EP = "00A40000021001"
    const val SELECT_PXX = "00A404000150" // 单字节名字 'P'
    const val GET_BALANCE_EP = "805C000204"
    const val GET_BALANCE_ED = "805C000104"
    const val READ_INFO_SFI21 = "00B0950000"

    fun selectAid(aid: String) = "00A40400${"%02X".format(aid.length / 2)}$aid"

    /** 读 PSE 目录（SFI 1）第 rec 条 */
    fun readPseRecord(rec: Int) = "00B2%02X0C00".format(rec)

    /** 读交易记录（SFI 24）第 rec 条 */
    fun readLogRecord(rec: Int) = "00B2%02XC400".format(rec)

    // ── 响应解析 ─────────────────────────────────────────────

    /** 从 pm3 `hf 14a apdu` 输出中取出响应字节（含 SW1 SW2），失败返回 null */
    fun parseApduResponse(output: String): ByteArray? {
        val clean = output.replace(Regex("""\[[0-9;]*[A-Za-z]"""), "")
        val m = Regex("""<<< ([0-9A-Fa-f ]+) \|""").findAll(clean).lastOrNull()
            ?: return null
        val hex = m.groupValues[1].replace(" ", "")
        if (hex.length < 4 || hex.length % 2 != 0) return null
        return runCatching {
            ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        }.getOrNull()
    }

    fun sw12(r: ByteArray): Int =
        ((r[r.size - 2].toInt() and 0xFF) shl 8) or (r[r.size - 1].toInt() and 0xFF)

    fun isOk(r: ByteArray?): Boolean = r != null && r.size >= 2 && sw12(r) == 0x9000

    /** 数据部分（去掉 SW） */
    fun dataOf(r: ByteArray): ByteArray = r.copyOfRange(0, r.size - 2)

    // ── TLV（仅支持 1-2 字节 tag、短/长式长度） ───────────────

    private class Tlv(val tag: Int, val value: ByteArray, val size: Int)

    private fun readTlv(b: ByteArray, off: Int): Tlv? {
        if (off >= b.size) return null
        var i = off
        var tag = b[i++].toInt() and 0xFF
        if (tag and 0x1F == 0x1F && i < b.size) {
            tag = (tag shl 8) or (b[i++].toInt() and 0xFF)
        }
        if (i >= b.size) return null
        var len = b[i++].toInt() and 0xFF
        if (len and 0x80 == 0x80) {
            val n = len and 0x7F
            if (n < 1 || n > 2 || i + n > b.size) return null
            len = 0
            repeat(n) { len = (len shl 8) or (b[i++].toInt() and 0xFF) }
        }
        if (i + len > b.size) return null
        return Tlv(tag, b.copyOfRange(i, i + len), (i + len) - off)
    }

    /** 从 PSE 目录记录（70{61{4F aid}}）里提取 AID */
    fun findAidInRecord(record: ByteArray): String? {
        val t70 = readTlv(record, 0) ?: return null
        if (t70.tag != 0x70) return null
        val t61 = readTlv(t70.value, 0) ?: return null
        if (t61.tag != 0x61) return null
        val t4f = readTlv(t61.value, 0) ?: return null
        if (t4f.tag != 0x4F) return null
        return t4f.value.toHex()
    }

    // ── 业务解析 ─────────────────────────────────────────────

    fun parseBalance(r: ByteArray?): String? {
        if (!isOk(r)) return null
        val d = dataOf(r!!)
        if (d.size < 4) return null
        var n = beInt(d, 0, 4)
        if (n > 100000 || n < -100000) n -= Int.MIN_VALUE
        return "%.2f".format(n / 100.0)
    }

    fun parseInfo(r: ByteArray?): Triple<String?, String?, String?> {
        if (!isOk(r)) return Triple(null, null, null)
        val d = dataOf(r!!)
        if (d.size < 30) return Triple(null, null, null)
        val serial = d.copyOfRange(10, 20).toHex()
        val version = if (d[9].toInt() != 0) d[9].toInt().toString() else null
        val validity = "%02X%02X.%02X.%02X - %02X%02X.%02X.%02X".format(
            d[20], d[21], d[22], d[23], d[24], d[25], d[26], d[27]
        )
        return Triple(serial, version, validity)
    }

    /** 解析 23 字节交易记录；金额 <= 0 视为空记录返回 null */
    fun parseTxn(v: ByteArray): Txn? {
        if (v.size < 23) return null
        val cash = beInt(v, 5, 4)
        if (cash <= 0) return null
        val type = v[9].toInt() and 0xFF
        val expense = type == 6 || type == 9
        val over = beInt(v, 2, 3)
        return Txn(
            dateTime = "%02X%02X.%02X.%02X %02X:%02X:%02X".format(
                v[16], v[17], v[18], v[19], v[20], v[21], v[22]
            ),
            expense = expense,
            amount = "%.2f".format(cash / 100.0),
            overdraft = if (over > 0) "%.2f".format(over / 100.0) else null,
            terminal = v.copyOfRange(10, 16).toHex(),
        )
    }

    // ── 工具 ─────────────────────────────────────────────────

    fun beInt(b: ByteArray, s: Int, n: Int): Int {
        var r = 0
        for (i in s until s + n) r = (r shl 8) or (b[i].toInt() and 0xFF)
        return r
    }

    fun ByteArray.toHex(): String =
        joinToString("") { "%02X".format(it) }
}
