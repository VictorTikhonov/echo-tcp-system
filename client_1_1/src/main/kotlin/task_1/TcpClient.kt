package ru.tikhonov.task_1

import java.io.Closeable
import java.net.Socket

class TcpClient(
    val host: String,
    val port: Int,
    timeoutMs: Int = 5_000,
) : Closeable {

    private val socket = Socket(host, port).apply {
        soTimeout = timeoutMs
    }

    private val reader = socket.getInputStream().bufferedReader()
    private val writer = socket.getOutputStream().bufferedWriter()

    fun sendMessage(message: String): String {
        writer.write("$message\n")
        writer.flush()
        return reader.readLine()
    }

    override fun close() {
        socket.close()
    }
}