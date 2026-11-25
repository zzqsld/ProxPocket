package com.proxpocket.app.pm3

/** Suica / PASMO FeliCa 历史记录解析（服务 0x090F，每条 16 字节） */
object Suica {

    data class Entry(
        val date: String,
        val kind: String,
        val balance: Int?,
        val detail: String?,
    )

    private val PROCESS = mapOf(
        0x01 to "出站扣费", 0x02 to "充值", 0x03 to "购票", 0x04 to "精算",
        0x05 to "入场精算", 0x06 to "窗口处理", 0x07 to "新发行", 0x08 to "窗口控除",
        0x0D to "巴士", 0x0F to "巴士", 0x11 to "再发行", 0x13 to "新干线",
        0x14 to "入场充值", 0x15 to "出场充值", 0x17 to "巴士",
        0x46 to "购物", 0x47 to "商店消费", 0x48 to "入金", 0x49 to "特典",
    )

    private val TERMINAL = mapOf(
        0x03 to "精算机", 0x04 to "便携终端", 0x05 to "车载终端", 0x07 to "售票机",
        0x08 to "售票机", 0x09 to "充值机", 0x12 to "检票机", 0x14 to "窗口终端",
        0x17 to "简易检票机", 0x18 to "机场", 0xC7 to "商店", 0xC8 to "自动售货机",
    )

    /** 解析一条 16 字节历史记录，全零/无效返回 null */
    fun parseEntry(b: ByteArray): Entry? {
        if (b.size < 16) return null
        if (b.all { it == 0.toByte() || it == 0xFF.toByte() }) return null

        val terminal = b[0].toInt() and 0xFF
        val process = b[1].toInt() and 0xFF
        val year = 2000 + ((b[4].toInt() and 0xFE) shr 1)
        val month = ((b[4].toInt() and 1) shl 3) or ((b[5].toInt() and 0xE0) shr 5)
        val day = b[5].toInt() and 0x1F
        if (month < 1 || month > 12 || day < 1 || day > 31) return null

        val balance = (b[7].toInt() and 0xFF shl 8) or (b[6].toInt() and 0xFF)
        val kind = PROCESS[process] ?: "类型%02X".format(process)

        val detail = when {
            // 乘车类记录：入站线/站 + 出站线/站代码
            process in listOf(0x01, 0x13) || terminal in listOf(0x12, 0x17) -> {
                val inLine = b[9].toInt() and 0xFF
                val inSta = b[10].toInt() and 0xFF
                val outLine = b[11].toInt() and 0xFF
                val outSta = b[12].toInt() and 0xFF
                if (inSta != 0 || outSta != 0)
                    "入 %02X:%02X → 出 %02X:%02X（线路:站代码）".format(inLine, inSta, outLine, outSta)
                else TERMINAL[terminal]
            }
            else -> TERMINAL[terminal]
        }

        return Entry(
            date = "%04d.%02d.%02d".format(year, month, day),
            kind = kind,
            balance = balance,
            detail = detail,
        )
    }

    /** 从 hf felica rdbl 输出提取 16 字节块数据 */
    fun extractBlock(output: String): ByteArray? {
        val hex = Regex("""\b([0-9A-Fa-f]{32})\b""").findAll(output)
            .map { it.groupValues[1] }
            .firstOrNull { h ->
                // 跳过全零块
                !h.startsWith("00000000000000000000000000000000")
            } ?: return null
        return ByteArray(16) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }
}
