package com.proxpocket.app.usb

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.util.Log
import java.io.Closeable

/**
 * 极简 CDC-ACM 驱动：Proxmark3 通过 USB CDC 枚举为虚拟串口。
 * 打开控制 + 数据接口，拿到一对 Bulk 端点后即可收发字节流。
 */
class UsbCdcDevice(
    private val manager: UsbManager,
    val device: UsbDevice,
) : Closeable {

    private var connection: UsbDeviceConnection? = null
    private var controlInterface: UsbInterface? = null
    private var dataInterface: UsbInterface? = null
    private var epIn: UsbEndpoint? = null
    private var epOut: UsbEndpoint? = null

    val isOpen: Boolean get() = connection != null

    fun open(): Boolean {
        var ctrl: UsbInterface? = null
        var data: UsbInterface? = null
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            Log.i(TAG, "interface ${intf.id}: class=${intf.interfaceClass} eps=${intf.endpointCount}")
            when (intf.interfaceClass) {
                UsbConstants.USB_CLASS_COMM -> ctrl = intf
                UsbConstants.USB_CLASS_CDC_DATA -> data = intf
            }
        }
        // 有些固件把端点直接放在通信接口上（单接口 CDC），或只暴露一个 vendor 接口
        if (data == null) {
            for (i in 0 until device.interfaceCount) {
                val intf = device.getInterface(i)
                var bulkIn = 0
                var bulkOut = 0
                for (e in 0 until intf.endpointCount) {
                    val ep = intf.getEndpoint(e)
                    if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                        if (ep.direction == UsbConstants.USB_DIR_IN) bulkIn++ else bulkOut++
                    }
                }
                if (bulkIn > 0 && bulkOut > 0) {
                    data = intf
                    break
                }
            }
        }
        if (data == null) {
            Log.w(TAG, "no CDC data interface found")
            return false
        }
        Log.i(TAG, "ctrl=${ctrl?.id} data=${data.id}, opening connection")

        val conn = manager.openDevice(device) ?: run {
            Log.w(TAG, "openDevice failed (permission?)")
            return false
        }
        var claimed = true
        if (ctrl != null) {
            claimed = conn.claimInterface(ctrl, true)
            Log.i(TAG, "claim ctrl(${ctrl.id}) -> $claimed")
        }
        if (claimed && data !== ctrl) {
            claimed = conn.claimInterface(data, true)
            Log.i(TAG, "claim data(${data.id}) -> $claimed")
        }
        if (!claimed) {
            conn.close()
            return false
        }

        for (e in 0 until data.endpointCount) {
            val ep = data.getEndpoint(e)
            if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (ep.direction == UsbConstants.USB_DIR_IN) epIn = ep else epOut = ep
            }
        }
        if (epIn == null || epOut == null) {
            Log.w(TAG, "bulk endpoints not found")
            conn.close()
            return false
        }
        Log.i(TAG, "epIn=${epIn?.address} epOut=${epOut?.address}")

        // SET_LINE_CODING: 115200 8N1（PM3 是 USB 全速设备，实际忽略波特率，但部分固件要求该请求）
        // 仅对真正的 CDC 控制接口发送，vendor 接口（如 CH341）跳过
        if (ctrl != null) {
            val lineCoding = byteArrayOf(0x00, 0xC2.toByte(), 0x01, 0x00, 0, 0, 8)
            val r1 = conn.controlTransfer(0x21, 0x20, 0, ctrl.id, lineCoding, lineCoding.size, 1000)
            // SET_CONTROL_LINE_STATE: DTR | RTS
            val r2 = conn.controlTransfer(0x21, 0x22, 3, ctrl.id, null, 0, 1000)
            Log.i(TAG, "cdc line coding=$r1 line state=$r2")
        }

        connection = conn
        controlInterface = ctrl
        dataInterface = data
        Log.i(TAG, "usb open OK")
        return true
    }

    /** 返回读取到的字节数，超时或出错返回 <= 0 */
    fun read(buf: ByteArray, timeoutMs: Int): Int {
        val conn = connection ?: return -1
        return conn.bulkTransfer(epIn, buf, buf.size, timeoutMs)
    }

    fun write(buf: ByteArray, length: Int, timeoutMs: Int): Int {
        val conn = connection ?: return -1
        var sent = 0
        while (sent < length) {
            val n = conn.bulkTransfer(epOut, buf, sent, length - sent, timeoutMs)
            if (n < 0) return if (sent > 0) sent else n
            sent += n
        }
        return sent
    }

    override fun close() {
        val conn = connection ?: return
        controlInterface?.let { conn.releaseInterface(it) }
        dataInterface?.let { conn.releaseInterface(it) }
        conn.close()
        connection = null
        controlInterface = null
        dataInterface = null
        epIn = null
        epOut = null
    }

    companion object {
        private const val TAG = "UsbCdcDevice"

        /** 判断一个 UsbDevice 是否像 Proxmark3（含 CDC 接口或已知 VID/PID） */
        fun looksLikePm3(device: UsbDevice): Boolean {
            // 已知 VID/PID：Iceman 原生 CDC、CH341 串口（部分 PM3 Max）
            if ((device.vendorId == 0x9AC4 && device.productId == 0x4B8F) ||
                (device.vendorId == 0x1A86 && device.productId == 0x5523)
            ) return true
            for (i in 0 until device.interfaceCount) {
                val intf = device.getInterface(i)
                if (intf.interfaceClass == UsbConstants.USB_CLASS_COMM ||
                    intf.interfaceClass == UsbConstants.USB_CLASS_CDC_DATA
                ) return true
            }
            return false
        }

        fun displayName(device: UsbDevice): String {
            val vid = "%04X".format(device.vendorId)
            val pid = "%04X".format(device.productId)
            val product = device.productName
            return if (product.isNullOrBlank()) "Proxmark3 ($vid:$pid)" else product
        }
    }
}
