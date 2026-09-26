package ru.tikhonov.task_2


import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.concurrent.thread

private val logger = KotlinLogging.logger {}

/**
 * Точка входа Netty-сервера.
 */
fun main() {
    val server = NettyServer(port = 8082)

    val serverThread = thread(isDaemon = true, name = "netty-server") {
        server.start()
    }

    logger.info { "Press ENTER to stop the server" }
    readlnOrNull()
    server.close()
    serverThread.join()
    logger.info { "Server stopped" }
}