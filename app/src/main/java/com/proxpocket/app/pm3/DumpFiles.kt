package com.proxpocket.app.pm3

import java.io.File

/** pm3 客户端产出的数据文件（dump / 密钥） */
data class DumpFile(
    val file: File,
    val kind: Kind,
    val uid: String?,
) {
    enum class Kind { MFC_DUMP, MFC_KEY, MFU_DUMP, T55XX_DUMP, OTHER }

    val name: String get() = file.name
    val size: Long get() = file.length()
    val modified: Long get() = file.lastModified()

    companion object {
        /** 扫描工作目录里的 pm3 产出文件 */
        fun scan(dir: File): List<DumpFile> {
            val out = mutableListOf<DumpFile>()
            dir.listFiles()?.forEach { f ->
                if (!f.isFile) return@forEach
                val n = f.name
                val uid = Regex("""(?:hf-mf|hf-mfu|lf-t55xx)-([0-9A-Fa-f]{4,20})-""")
                    .find(n)?.groupValues?.get(1)?.uppercase()
                when {
                    n.startsWith("hf-mfu-") && n.contains("-dump") ->
                        out += DumpFile(f, Kind.MFU_DUMP, uid)
                    n.startsWith("hf-mf-") && n.contains("-key") ->
                        out += DumpFile(f, Kind.MFC_KEY, uid)
                    n.startsWith("lf-t55xx-") && n.contains("-dump") ->
                        out += DumpFile(f, Kind.T55XX_DUMP, uid)
                    (n.endsWith(".bin", true) || n.endsWith(".eml", true)) && MfcDump.parse(f) != null ->
                        out += DumpFile(f, Kind.MFC_DUMP, uid)
                    n.endsWith(".dic", true) || n.endsWith(".eml", true) ||
                        n.endsWith(".json", true) || n.endsWith(".bin", true) ->
                        out += DumpFile(f, Kind.OTHER, uid)
                }
            }
            return out.sortedByDescending { it.modified }
        }
    }
}

/** Mifare Classic 1K/4K dump 解析与序列化 */
object MfcDump {

    class Block(val sector: Int, val index: Int, val data: ByteArray) {
        val blocksPerSector: Int get() = if (sector < 32) 4 else 16
        val isTrailer: Boolean get() = index == blocksPerSector - 1
        val blockNumber: Int
            get() = if (sector < 32) sector * 4 + index else 128 + (sector - 32) * 16 + index

        fun hex(): String = data.joinToString("") { "%02X".format(it) }
        fun keyA(): String = data.copyOfRange(0, 6).joinToString("") { "%02X".format(it) }
        fun accessBits(): String = data.copyOfRange(6, 10).joinToString("") { "%02X".format(it) }
        fun keyB(): String = data.copyOfRange(10, 16).joinToString("") { "%02X".format(it) }
    }

    /** 解析 1K/4K .bin 或每行 32 个十六进制字符的 .eml，失败返回 null */
    fun parse(file: File): List<Block>? {
        val bytes = when {
            file.extension.equals("eml", true) -> {
                val lines = file.readLines().map { it.trim() }
                if (lines.size !in setOf(64, 256) ||
                    lines.any { !it.matches(Regex("[0-9A-Fa-f]{32}")) }
                ) return null
                lines.map { it.chunked(2).map { b -> b.toInt(16).toByte() }.toByteArray() }
                    .fold(ByteArray(0)) { acc, b -> acc + b }
            }
            file.length() == 1024L || file.length() == 4096L -> file.readBytes()
            else -> return null
        }
        if (bytes.size != 1024 && bytes.size != 4096) return null

        var offset = 0
        val sectorCount = if (bytes.size == 1024) 16 else 40
        return (0 until sectorCount).flatMap { sector ->
            val blocksInSector = if (sector < 32) 4 else 16
            (0 until blocksInSector).map { block ->
                val data = bytes.copyOfRange(offset, offset + 16)
                offset += 16
                Block(sector, block, data)
            }
        }
    }

    fun encode(blocks: List<Block>, asEml: Boolean): ByteArray {
        val ordered = blocks.sortedBy { it.blockNumber }
        require(ordered.size == 64 || ordered.size == 256) { "仅支持 Mifare Classic 1K/4K dump" }
        require(ordered.withIndex().all { (index, block) ->
            block.blockNumber == index && block.data.size == 16
        }) { "dump 块序列不完整或格式无效" }

        if (!asEml) {
            return ByteArray(ordered.size * 16).also { bytes ->
                ordered.forEachIndexed { index, block ->
                    block.data.copyInto(bytes, index * 16)
                }
            }
        }

        return (ordered.joinToString("\n") { it.hex() } + "\n")
            .toByteArray(Charsets.US_ASCII)
    }
}
