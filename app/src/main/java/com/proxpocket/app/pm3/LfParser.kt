package com.proxpocket.app.pm3

/** 解析 lf search / lf hid read 等命令的文本输出为结构化字段 */
object LfParser {

    /**
     * 从一次命令输出中提取低频卡信息，返回 null 表示没识别到卡。
     * 字段: TYPE / ID / FC / CN / FORMAT / RAW
     */
    fun parse(output: String): Map<String, String>? {
        val clean = output.replace(Regex("""\[[0-9;]*[A-Za-z]"""), "")
        val map = mutableMapOf<String, String>()

        // EM410x："[+] EM 410x ID 0F0012345A" / "EM410x - RF/64 ID: ..."
        Regex("""EM ?410x[^\n]*?([0-9A-Fa-f]{10})""", RegexOption.IGNORE_CASE).find(clean)?.let {
            map["TYPE"] = "EM4100"
            map["ID"] = it.groupValues[1].uppercase()
        }

        // HID Prox："Format len 26 bit - FC 123 - Card 4567" / "FC: 123 CN: 4567"
        if (clean.contains("HID", ignoreCase = true)) {
            Regex("""TAG ID: ([0-9A-Fa-f]+)""").find(clean)?.let {
                map["ID"] = it.groupValues[1].uppercase()
            }
            Regex("""Format len\D*(\d+)""").find(clean)?.let {
                map["FORMAT"] = "${it.groupValues[1]}-bit"
            }
            Regex("""FC[: ]+(\d+)""").find(clean)?.let { map["FC"] = it.groupValues[1] }
            Regex("""(?:Card|CN)[: ]+(\d+)""").find(clean)?.let { map["CN"] = it.groupValues[1] }
            if (map.isNotEmpty()) map["TYPE"] = "HID Prox"
        }

        // Indala
        if (clean.contains("Indala", ignoreCase = true)) {
            Regex("""Indala[^\n]*""").find(clean)?.let { m ->
                map["TYPE"] = "Indala"
                Regex("""([0-9A-Fa-f]{8,})""").find(m.value)?.let {
                    map["ID"] = it.groupValues[1].uppercase()
                }
            }
        }

        // AWID / FDX-B / Viking / Noralsy 等：取 "[+] xxx found/detected" 行
        if (map.isEmpty()) {
            Regex("""(?m)^\[\+] (.+?(?:found|detected|TAG ID)[^\n]*)$""").find(clean)?.let {
                map["TYPE"] = "其他"
                map["RAW"] = it.groupValues[1].trim()
            }
        }

        // T55xx 检测
        if (clean.contains("T55", ignoreCase = true) && clean.contains("chip", ignoreCase = true)) {
            map["CHIP"] = "T55xx 可写芯片"
        }

        return map.takeIf { it.isNotEmpty() }
    }
}
