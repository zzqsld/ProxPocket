package com.proxpocket.app.pm3

/** NDEF 记录构造 */
object Ndef {

    /** URI 前缀 → NDEF URI Identifier Code */
    private val URI_PREFIXES = listOf(
        "http://www." to 0x01,
        "https://www." to 0x02,
        "http://" to 0x03,
        "https://" to 0x04,
        "tel:" to 0x05,
        "mailto:" to 0x06,
    )

    /**
     * 构造一条短记录格式的 URI NDEF 记录（不含 03/FE 的 TLV 包装，
     * 交给 `hf 14a ndefwrite -p` 自动补全）。
     */
    fun uriRecordHex(uri: String): String? {
        val (prefix, code) = URI_PREFIXES.firstOrNull { uri.startsWith(it.first, ignoreCase = true) }
            ?: ("" to 0x00)
        val body = uri.removePrefix(prefix).toByteArray(Charsets.US_ASCII)
        val payload = byteArrayOf(code.toByte()) + body
        if (payload.size > 254) return null
        val record = byteArrayOf(
            0xD1.toByte(),          // MB|ME|SR, TNF=well-known
            0x01,                   // type length
            payload.size.toByte(),  // payload length
            0x55,                   // 'U' = URI record
        ) + payload
        return record.joinToString("") { "%02X".format(it) }
    }
}
