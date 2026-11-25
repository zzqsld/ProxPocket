package com.proxpocket.app.usb

import android.util.Log
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * USB CDC <-> 本地 TCP 桥。
 * pm3 原生客户端的 uart 层支持 "tcp:host:port" 连接串口，
 * 因此 App 侧在 127.0.0.1 起一个单连接 TCP 服务，把字节流转发到 USB Bulk 端点。
 */
class Pm3Bridge(private val usb: UsbCdcDevice) : Closeable {

    @Volatile
    var port: Int = 0
        private set

    private var server: ServerSocket? = null
    private val running = AtomicBoolean(false)
    private var activeSocket: Socket? = null

    fun start(): Int {
        check(!running.get()) { "bridge already started" }
        val srv = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        server = srv
        port = srv.localPort
        running.set(true)
        thread(name = "pm3-bridge-accept", isDaemon = true) { acceptLoop() }
        Log.i(TAG, "bridge listening on 127.0.0.1:$port")
        return port
    }

    private fun acceptLoop() {
        while (running.get()) {
            val socket = try {
                server?.accept()
            } catch (e: Exception) {
                break
            } ?: break
            Log.i(TAG, "native client connected")
            activeSocket = socket
            handleClient(socket)
            activeSocket = null
            Log.i(TAG, "native client disconnected")
        }
    }

    private fun handleClient(socket: Socket) {
        socket.tcpNoDelay = true
        val pumper = thread(name = "pm3-bridge-usb2tcp", isDaemon = true) {
            val buf = ByteArray(16384)
            try {
                val out = socket.getOutputStream()
                while (running.get() && !socket.isClosed) {
                    val n = usb.read(buf, 50)
                    if (n > 0) {
                        out.write(buf, 0, n)
                        out.flush()
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "usb->tcp pump ended: ${e.message}")
            }
        }
        try {
            val input = socket.getInputStream()
            val buf = ByteArray(16384)
            while (running.get()) {
                val n = input.read(buf)
                if (n < 0) break
                usb.write(buf, n, 1000)
            }
        } catch (e: Exception) {
            Log.d(TAG, "tcp->usb pump ended: ${e.message}")
        }
        try {
            socket.close()
        } catch (_: Exception) {
        }
        pumper.interrupt()
    }

    override fun close() {
        running.set(false)
        try {
            activeSocket?.close()
        } catch (_: Exception) {
        }
        try {
            server?.close()
        } catch (_: Exception) {
        }
        server = null
    }

    companion object {
        private const val TAG = "Pm3Bridge"
    }
}
