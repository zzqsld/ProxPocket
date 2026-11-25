package com.proxpocket.app.pm3

/** Mifare Classic 尾块访问位（Access Bits）解码器 */
object AccessBits {

    /** 数据块权限 */
    data class DataPerm(
        val read: String,
        val write: String,
        val incr: String,
        val decr: String,
    )

    data class DecodeResult(
        val valid: Boolean,              // 反向校验位是否一致
        val bits: List<Int>,             // 每块的 (C1<<2)|(C2<<1)|C3
        val dataPerms: List<DataPerm>,   // 块 0-2
        val trailerLines: List<String>,  // 尾块自身权限描述（KeyA / 访问位 / KeyB）
        val keyBUsable: Boolean,         // KeyB 可读时不能再当认证密钥用
    )

    /** trailer: 完整 16 字节尾块 */
    fun decode(trailer: ByteArray): DecodeResult? {
        if (trailer.size < 16) return null
        val b6 = trailer[6].toInt() and 0xFF
        val b7 = trailer[7].toInt() and 0xFF
        val b8 = trailer[8].toInt() and 0xFF

        val bits = (0..3).map { i ->
            val c1 = (b7 shr (4 + i)) and 1
            val c2 = (b8 shr i) and 1
            val c3 = (b8 shr (4 + i)) and 1
            (c1 shl 2) or (c2 shl 1) or c3
        }
        // 反向位校验：byte6 低4位=!C1 高4位=!C2；byte7 低4位=!C3
        val valid = (0..3).all { i ->
            (((b6 shr i) and 1) == 1 - ((b7 shr (4 + i)) and 1)) &&
                (((b6 shr (4 + i)) and 1) == 1 - ((b8 shr i) and 1)) &&
                (((b7 shr i) and 1) == 1 - ((b8 shr (4 + i)) and 1))
        }

        return DecodeResult(
            valid = valid,
            bits = bits,
            dataPerms = (0..2).map { dataPerm(bits[it]) },
            trailerLines = trailerLines(bits[3]),
            // KeyB 在 000/001/010 下明文可读，可读则不能再当认证密钥
            keyBUsable = bits[3] !in listOf(0b000, 0b001, 0b010),
        )
    }

    /** 数据块权限表（NXP 标准） */
    private fun dataPerm(code: Int) = when (code) {
        0b000 -> DataPerm("KeyA|B", "KeyA|B", "KeyA|B", "KeyA|B")   // 出厂配置
        0b010 -> DataPerm("KeyA|B", "禁止", "禁止", "禁止")
        0b100 -> DataPerm("KeyA|B", "KeyB", "禁止", "禁止")
        0b110 -> DataPerm("KeyA|B", "KeyB", "KeyB", "KeyB")
        0b001 -> DataPerm("KeyA|B", "禁止", "禁止", "KeyA|B")       // 充值型值块
        0b011 -> DataPerm("KeyB", "KeyB", "禁止", "禁止")
        0b101 -> DataPerm("KeyB", "禁止", "禁止", "禁止")
        else -> DataPerm("禁止", "禁止", "禁止", "禁止")             // 111
    }

    /** 尾块权限表：三行分别为 KeyA / 访问位 / KeyB */
    private fun trailerLines(code: Int): List<String> = when (code) {
        0b000, 0b001 -> listOf("KeyA: 读✗ 写KeyA", "访问位: 读KeyA 写KeyA", "KeyB: 读KeyA 写KeyA")
        0b010 -> listOf("KeyA: 读✗ 写✗", "访问位: 读KeyA 写✗", "KeyB: 读KeyA 写✗")
        0b011, 0b100 -> listOf("KeyA: 读✗ 写KeyB", "访问位: 读KeyA|B 写KeyB", "KeyB: 读✗ 写KeyB")
        0b101 -> listOf("KeyA: 读✗ 写✗", "访问位: 读KeyA|B 写KeyB", "KeyB: 读✗ 写✗")
        0b110 -> listOf("KeyA: 读✗ 写✗", "访问位: 读KeyA|B 写✗", "KeyB: 读✗ 写✗")
        else -> listOf("KeyA: 读✗ 写✗", "访问位: 读KeyA|B 写✗", "KeyB: 读✗ 写✗") // 111
    }
}
