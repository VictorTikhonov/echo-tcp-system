package ru.tikhonov.benchmark

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.withLoggingContext
import ru.tikhonov.task_3.GrpcClient
import java.util.Locale

class GrpcBenchmark(
    private val host: String,
    private val port: Int,
) {

    fun start(messagesPerClient: Int) {
        if (messagesPerClient <= 0) {
            logger.warn { "Grpc benchmark not started" }
            return
        }

        val messages = (0 until messagesPerClient).map { "Message $it" }

        GrpcClient(host, port).use { client ->
            val start = System.nanoTime()
            val responses = client.sendStream(messages)
            val end = System.nanoTime()

            val sequenceOk = responses.map { it.sequence } ==
                    (0 until messagesPerClient).map { it.toLong() }
            val contentOk = responses.all { it.message.startsWith("ECHO: ") }
            val timeOk = responses.all { it.serverTime > 0 }

            val totalMs = (end - start) / 1_000_000.0

            val totalMessages = responses.size
            val throughput = totalMessages / (totalMs / 1000.0)
            val avgRttMs = totalMs / totalMessages

            withLoggingContext(
                "sent" to messages.size.toString(),
                "received" to responses.size.toString(),
                "totalTime_ms" to "%.3f".format(Locale.US, totalMs),
                "avgRtt_ms" to "%.3f".format(Locale.US, avgRttMs),
                "throughput_msgPerSec" to "%.1f".format(Locale.US, throughput),
                "sequenceOk" to sequenceOk.toString(),
                "contentOk" to contentOk.toString(),
                "serverTimeOk" to timeOk.toString(),
            ) {
                summaryLogger.info { "Grpc benchmark completed" }
            }
        }
    }

    companion object {
        private val summaryLogger = KotlinLogging.logger("Benchmark.Grpc.Summary")
        private val logger = KotlinLogging.logger {}
    }
}