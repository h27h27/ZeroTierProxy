package com.zerotier.proxy

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

class LocalSocks5Proxy(
    private val libztBridge: LibztBridge,
    private val listenPort: Int = 1080,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private var scope: CoroutineScope? = null
    private val running = AtomicBoolean(false)

    fun start(): Result<Unit> = runCatching {
        if (running.get()) return@runCatching
        val socket = ServerSocket(listenPort).apply { reuseAddress = true }
        serverSocket = socket
        running.set(true)

        scope = CoroutineScope(ioDispatcher)
        acceptJob = scope?.launch {
            while (running.get()) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                launch { handleClient(client) }
            }
        }
    }

    fun stop(): Result<Unit> = runCatching {
        running.set(false)
        acceptJob?.cancel()
        acceptJob = null
        serverSocket?.close()
        serverSocket = null
        scope?.cancel()
        scope = null
    }

    fun isRunning(): Boolean = running.get()

    private fun handleClient(client: Socket) {
        client.use { clientSocket ->
            val input = clientSocket.getInputStream()
            val output = clientSocket.getOutputStream()

            performHandshake(input, output)
            val (targetHost, targetPort) = readConnectRequest(input, output)
            val upstream = libztBridge.openSocket(targetHost, targetPort).getOrElse {
                writeReply(output, 0x05)
                return
            }

            writeReply(output, 0x00)
            try {
                val a = pipe(clientSocket.getInputStream(), upstream.outputStream)
                val b = pipe(upstream.inputStream, clientSocket.getOutputStream())
                a.join()
                b.join()
            } finally {
                upstream.close.invoke()
            }
        }
    }

    private fun pipe(input: InputStream, output: OutputStream): Thread {
        val thread = Thread {
            val buffer = ByteArray(8192)
            while (true) {
                val count = runCatching { input.read(buffer) }.getOrElse { -1 }
                if (count <= 0) break
                runCatching {
                    output.write(buffer, 0, count)
                    output.flush()
                }.onFailure { break }
            }
        }
        thread.start()
        return thread
    }

    private fun performHandshake(input: InputStream, output: OutputStream) {
        val version = input.readOrThrow()
        if (version != 0x05) error("Unsupported SOCKS version: $version")
        val methodCount = input.readOrThrow()
        repeat(methodCount) { input.readOrThrow() } // consume offered methods
        output.write(byteArrayOf(0x05, 0x00)) // no-auth
        output.flush()
    }

    private fun readConnectRequest(input: InputStream, output: OutputStream): Pair<String, Int> {
        val version = input.readOrThrow()
        val command = input.readOrThrow()
        input.readOrThrow() // reserved
        val addressType = input.readOrThrow()
        if (version != 0x05 || command != 0x01) {
            writeReply(output, 0x07)
            error("Only SOCKS5 CONNECT is supported")
        }

        val host = when (addressType) {
            0x01 -> { // IPv4
                val ip = ByteArray(4)
                input.readFully(ip)
                "${ip[0].toUByte()}.${ip[1].toUByte()}.${ip[2].toUByte()}.${ip[3].toUByte()}"
            }
            0x03 -> { // Domain
                val len = input.readOrThrow()
                val raw = ByteArray(len)
                input.readFully(raw)
                String(raw, StandardCharsets.UTF_8)
            }
            else -> {
                writeReply(output, 0x08)
                error("Address type $addressType not supported")
            }
        }

        val portMsb = input.readOrThrow()
        val portLsb = input.readOrThrow()
        val port = (portMsb shl 8) or portLsb
        return host to port
    }

    private fun writeReply(output: OutputStream, code: Int) {
        output.write(byteArrayOf(0x05, code.toByte(), 0x00, 0x01, 0, 0, 0, 0, 0, 0))
        output.flush()
    }

    private fun InputStream.readOrThrow(): Int {
        val value = read()
        if (value < 0) throw EOFException("Unexpected EOF")
        return value
    }

    private fun InputStream.readFully(buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val count = read(buffer, offset, buffer.size - offset)
            if (count < 0) throw EOFException("Unexpected EOF")
            offset += count
        }
    }
}
