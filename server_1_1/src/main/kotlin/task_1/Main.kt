package ru.tikhonov.task_1

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.concurrent.thread

private val logger = KotlinLogging.logger {}

fun main() {
    val server = TcpServer(8081, 100)

    val serverThread = thread(isDaemon = true, name = "tcp-server") {
        server.start()
    }

    logger.info { "Press ENTER to stop the server" }
    readln()
    server.stop()
    serverThread.join()
    logger.info { "Server stopped" }
}