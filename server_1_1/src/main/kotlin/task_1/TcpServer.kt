package ru.tikhonov.task_1

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.withLoggingContext
import java.io.IOException
import java.lang.Thread.sleep
import java.net.ServerSocket
import java.net.Socket
import java.time.Instant
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class TcpServer(
    private val port: Int,
    private val maxConcurrentClients: Int,
) {

    private val executor: ExecutorService =
        Executors.newFixedThreadPool(maxConcurrentClients)

    @Volatile
    private var running = true

    private var serverSocket: ServerSocket? = null

    fun start() {
        if (!running) return

        val ss = ServerSocket(port)
        serverSocket = ss

        ss.use { socket ->
            logger.info {
                "TCP server started on port: $port, maxConcurrentClients: $maxConcurrentClients"
            }

            while (running) {
                try {
                    val clientSocket = socket.accept()
                    executor.submit {
                        try {
                            handleClient(clientSocket)
                        } catch (e: Throwable) {
                            logger.error(e) { "Unhandled error for $clientSocket" }
                        }
                    }
                } catch (e: IOException) {
                    if (running) logger.error(e) { "accept() failed" }
                }
            }
        }
    }

    fun stop() {
        running = false
        serverSocket?.close()
        executor.shutdown()
        if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
            executor.shutdownNow()
        }
    }

    private fun handleClient(socket: Socket) {
        val remote = socket.remoteSocketAddress

        socket.use { clientSocket ->
            val reader = clientSocket.getInputStream().bufferedReader()
            val writer = clientSocket.getOutputStream().bufferedWriter()

            while (true) {
                val message = reader.readLine() ?: break

                val now = Instant.now()
                val timestamp = now.epochSecond + now.nano / 1_000_000_000.0
                val ts = "%.6f".format(Locale.US, timestamp)

                val response = "ECHO: $message [t=$ts]\n"

                writer.write(response)
                writer.flush()

                withLoggingContext(
                    "remote" to remote.toString(),
                    "thread" to Thread.currentThread().name
                ) {
                    responseLogger.info { "Response: ${response.trimEnd()}" }
                }
            }
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
        private val responseLogger = KotlinLogging.logger("Benchmark.Server.Response")
    }
}