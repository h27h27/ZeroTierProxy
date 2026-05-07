package com.zerotier.proxy

import android.content.Context
import android.net.Uri
import java.io.File

class SettingsManager(private val context: Context) {
    private val configDir: File by lazy {
        File(context.filesDir, "zt-config").apply { mkdirs() }
    }

    fun savePlanet(uri: Uri): Result<File> = copyToInternal(uri, "planet")

    fun saveMoon(uri: Uri): Result<File> = copyToInternal(uri, "moon")

    fun planetFile(): File? = File(configDir, "planet").takeIf { it.exists() }

    fun moonFile(): File? = File(configDir, "moon").takeIf { it.exists() }

    private fun copyToInternal(uri: Uri, targetName: String): Result<File> = runCatching {
        val target = File(configDir, targetName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Unable to open selected file")
        target
    }
}
