package com.proxpocket.app.pm3

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MfcDumpTest {

    @Test
    fun fourKBinaryRoundTripsWithLargeSectorTrailers() {
        val bytes = ByteArray(4096) { it.toByte() }
        val file = File.createTempFile("mfc-dump-", ".bin")
        try {
            file.writeBytes(bytes)

            val blocks = MfcDump.parse(file)
            assertNotNull(blocks)
            assertEquals(256, blocks!!.size)
            assertEquals(40, blocks.map { it.sector }.distinct().size)
            assertEquals(32, blocks[128].sector)
            assertEquals(0, blocks[128].index)
            assertEquals(39, blocks[255].sector)
            assertEquals(15, blocks[255].index)
            assertTrue(blocks[255].isTrailer)
            assertArrayEquals(bytes, MfcDump.encode(blocks, asEml = false))
        } finally {
            file.delete()
        }
    }

    @Test
    fun emlRoundTripsAndRejectsMalformedLines() {
        val bytes = ByteArray(1024) { (it % 256).toByte() }
        val file = File.createTempFile("mfc-dump-", ".eml")
        try {
            val lines = bytes.asList().chunked(16).joinToString("\n") { row ->
                row.joinToString("") { "%02X".format(it.toInt() and 0xff) }
            }
            file.writeText("$lines\n", Charsets.US_ASCII)

            val blocks = MfcDump.parse(file)
            assertNotNull(blocks)
            assertEquals(64, blocks!!.size)
            assertTrue(blocks[3].isTrailer)
            assertArrayEquals(bytes, MfcDump.encode(blocks, asEml = false))
            assertEquals(
                "$lines\n",
                String(MfcDump.encode(blocks, asEml = true), Charsets.US_ASCII),
            )

            file.writeText("not-hex\n$lines", Charsets.US_ASCII)
            assertNull(MfcDump.parse(file))
        } finally {
            file.delete()
        }
    }
}
