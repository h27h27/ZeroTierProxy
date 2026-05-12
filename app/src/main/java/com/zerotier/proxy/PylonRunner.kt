package com.zerotier.proxy

import android.content.Context
import java.io.File
import java.io.IOException

class PylonRunner(
    private val context: Context,
    private val settingsManager: SettingsManager
) {
    private var process: Process? = null
    private var stdoutReader: Thread? = null

    fun start(): Result<Unit> = runCatching {
        if (process?.isAlive == true) return@runCatching

        val binary = ensureBinaryInstalled()
        val command = mutableListOf(binary.absolutePath)

        settingsManager.planetFile()?.let { command += listOf("--planet", it.absolutePath) }
        settingsManager.moonFile()?.let { command += listOf("--moon", it.absolutePath) }

        process = ProcessBuilder(command)
            .directory(context.filesDir)
            .redirectErrorStream(true)
            .start()

        // Consume stdout in a daemon thread to prevent buffer blocking
        // Without this, the process will hang once its output buffer fills up (~64KB)
        stdoutReader = Thread {
            try {
                process?.inputStream?.bufferedReader()?.use { reader ->
                    reader.lineSequence().forEach { /* discard to prevent backpressure */ }
                }
            } catch (_: IOException) {
                // Process terminated
            }
        }.apply {
            isDaemon = true
            name = "pylon-stdout-reader"
            start()
        }
    }

    fun stop(): Result<Unit> = runCatching {
        process?.let {
            if (it.isAlive) it.destroy()
            process = null
        }
        stdoutReader?.interrupt()
        stdoutReader = null
    }

    private fun ensureBinaryInstalled(): File {
        val target = File(context.filesDir, "pylon_arm64")
        if (!target.exists()) {
            context.assets.open("bin/pylon_arm64").use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
        target.setExecutable(true, true)
        return target
    }
}
