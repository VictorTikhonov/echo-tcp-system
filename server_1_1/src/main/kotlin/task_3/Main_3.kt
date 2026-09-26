package ru.tikhonov.task_3

import io.github.oshai.kotlinlogging.KotlinLogging
import io.grpc.ServerBuilder
import kotlin.concurrent.thread

private val logger = KotlinLogging.logger {}

fun main() {
    val port = 8083

    val server = ServerBuilder
        .forPort(port)
        .addService(GrpcServer())
        .build()
        .start()

    logger.info { "gRPC server started on port $port" }

    val serverThread = thread(isDaemon = true, name = "grpc-server") {
        server.awaitTermination()
    }

    logger.info { "Press ENTER to stop the server" }
    readln()

    logger.info { "Stopping..." }
    server.shutdown()
    server.awaitTermination()
    serverThread.join()
    logger.info { "gRPC server stopped" }
}