package ru.tikhonov.task_1

import io.github.oshai.kotlinlogging.KotlinLogging
import ru.tikhonov.utils.startBlockingBenchmark

private val logger = KotlinLogging.logger {}

//fun main() {
//
//    val client = try {
//        TcpClient(
//            host = "localhost",
//            port = 8081
//        )
//    } catch (e: IOException) {
//        logger.warn { "Не удалось подключиться к серверу: ${e.message}" }
//        return
//    }
//
//    try {
//        val benchmarkService = BenchmarkService(client)
//        benchmarkService.start(5)
//    } finally {
//        client.close()
//    }
//}

fun main() {

    val benchmark = ParallelBenchmark(host = "localhost", port = 8081)
    startBlockingBenchmark(benchmark)
}