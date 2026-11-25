package com.proxpocket.app.pm3

import android.content.Context
import android.util.Log
import java.io.File

/**
 * 把 assets/pm3 下的 pm3 运行时资源（resources / dictionaries / luascripts / lualibs）
 * 释放到 filesDir/.proxmark3/，pm3 客户端通过 HOME 环境变量定位该目录。
 */
object Pm3Assets {

    private const val ASSET_ROOT = "pm3"
    private const val TARGET_DIR = ".proxmark3"
    private const val STAMP_FILE = ".extract_version"
    private const val EXTRACT_VERSION = 3

    /** 资源是否已就绪（首启释放完成后为 true） */
    val ready = kotlinx.coroutines.flow.MutableStateFlow(false)

    fun extractIfNeeded(context: Context) {
        val target = File(context.filesDir, TARGET_DIR)
        val stamp = File(target, STAMP_FILE)
        if (stamp.isFile && stamp.readText().trim().toIntOrNull() == EXTRACT_VERSION) {
            ready.value = true
            return
        }
        Log.i(TAG, "extracting pm3 assets to ${target.absolutePath}")
        val assets = context.assets
        val entries = assets.list(ASSET_ROOT) ?: return
        for (entry in entries) {
            copyAssetTree(assets, "$ASSET_ROOT/$entry", File(target, entry))
        }
        stamp.writeText(EXTRACT_VERSION.toString())
        ready.value = true
        Log.i(TAG, "extract done")
    }

    private fun copyAssetTree(assets: android.content.res.AssetManager, assetPath: String, target: File) {
        val children = assets.list(assetPath)
        if (children.isNullOrEmpty()) {
            target.parentFile?.mkdirs()
            assets.open(assetPath).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        } else {
            target.mkdirs()
            for (child in children) {
                copyAssetTree(assets, "$assetPath/$child", File(target, child))
            }
        }
    }

    private const val TAG = "Pm3Assets"
}
