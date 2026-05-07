package com.zerotier.proxy

import android.content.Context
import java.io.File

class PylonRunner(
    private val context: Context,
    private val settingsManager: SettingsManager
) {
    private var process: Process? = null

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
    }

    fun stop(): Result<Unit> = runCatching {
        process?.let {
            if (it.isAlive) it.destroy()
            process = null
        }
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
