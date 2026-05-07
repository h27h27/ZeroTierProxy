package com.zerotier.proxy

import android.content.Context
import com.zerotier.sockets.ZeroTierNode
import com.zerotier.sockets.ZeroTierSocket
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicBoolean

class LibztBridge(private val context: Context) {
    private val started = AtomicBoolean(false)
    private var node: ZeroTierNode? = null

    fun startNode(config: LibztConfig): Result<Unit> = runCatching {
        if (started.get()) return@runCatching

        config.storageDir.mkdirs()
        stageCustomFiles(config)

        val instance = ZeroTierNode()
        check(instance.initFromStorage(config.storageDir.absolutePath) == 0) { "initFromStorage failed" }
        check(instance.initAllowPeerCache(true) == 0) { "initAllowPeerCache failed" }
        check(instance.initAllowNetworkCache(true) == 0) { "initAllowNetworkCache failed" }
        check(instance.initAllowIdCache(true) == 0) { "initAllowIdCache failed" }
        check(instance.initAllowRootsCache(true) == 0) { "initAllowRootsCache failed" }
        check(instance.initSetPort(config.servicePort.toShort()) == 0) { "initSetPort failed" }
        check(instance.start() == 0) { "node start failed" }

        node = instance
        started.set(true)
    }

    fun stopNode(): Result<Unit> = runCatching {
        node?.stop()
        node = null
        started.set(false)
    }

    fun openSocket(host: String, port: Int): Result<BridgeSocket> = runCatching {
        check(started.get()) { "libzt node not started" }
        val socket = ZeroTierSocket(host, port)
        BridgeSocket(
            inputStream = socket.getInputStream(),
            outputStream = socket.getOutputStream(),
            close = { socket.close() }
        )
    }

    fun isStarted(): Boolean = started.get()

    private fun stageCustomFiles(config: LibztConfig) {
        config.planetPath?.let { source ->
            val sourceFile = File(source)
            if (sourceFile.exists()) {
                Files.copy(
                    sourceFile.toPath(),
                    File(config.storageDir, "planet").toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
        }

        config.moonPath?.let { source ->
            val sourceFile = File(source)
            if (sourceFile.exists()) {
                Files.copy(
                    sourceFile.toPath(),
                    File(config.storageDir, "moon").toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
        }
    }

    companion object {
        fun createDefaultConfig(context: Context, settingsManager: SettingsManager): LibztConfig {
            val storageDir = File(context.filesDir, "libzt-state").apply { mkdirs() }
            return LibztConfig(
                planetPath = settingsManager.planetFile()?.absolutePath,
                moonPath = settingsManager.moonFile()?.absolutePath,
                storageDir = storageDir,
                servicePort = 9993
            )
        }
    }
}

data class BridgeSocket(
    val inputStream: InputStream,
    val outputStream: OutputStream,
    val close: () -> Unit
)

data class LibztConfig(
    val planetPath: String?,
    val moonPath: String?,
    val storageDir: File,
    val servicePort: Int
)
