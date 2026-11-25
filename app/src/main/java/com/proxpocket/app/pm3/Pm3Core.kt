package com.proxpocket.app.pm3

/**
 * libpm3rrg_rdv4 的 JNI 封装。
 * native 层提供 pm3_open / pm3_console / pm3_close 的绑定。
 */
object Pm3Core {

    val nativeAvailable: Boolean = try {
        System.loadLibrary("pm3bridge")
        true
    } catch (e: UnsatisfiedLinkError) {
        false
    }

    /** 初始化运行环境（设置 HOME 用于资源查找），返回是否成功 */
    external fun nativeInit(homePath: String): Boolean

    /** 连接设备，port 形如 "tcp:127.0.0.1:4389"，返回句柄，0 表示失败 */
    external fun nativeOpen(port: String): Long

    /** 执行一条 pm3 命令，返回捕获的完整输出 */
    external fun nativeExec(handle: Long, cmd: String): String

    /** 断开并释放 */
    external fun nativeClose(handle: Long)
}
