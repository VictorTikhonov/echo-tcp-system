package ru.tikhonov.task_1

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.withLoggingContext
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.concurrent.thread
import kotlin.math.ceil

class ParallelBenchmark(
    private val host: String,
    private val port: Int,
) {

    private val requestResults = ConcurrentLinkedQueue<RequestResult>()

    fun start(clientCount: Int, messagesPerClient: Int) {
        if (clientCount <= 0 || messagesPerClient <= 0) {
            logger.warn { "Parallel benchmark not started" }
            return
        }

        requestResults.clear()

        val wallStart = System.nanoTime()

        val threads = (1..clientCount).map { clientId ->
            thread(name = "client-$clientId") {
                runClient(clientId, messagesPerClient)
            }
        }

        threads.forEach { it.join() }

        val wallEnd = System.nanoTime()

        logBenchmarkSummary(
            clientCount = clientCount,
            messagesPerClient = messagesPerClient,
            wallNanos = wallEnd - wallStart
        )
    }

    private fun runClient(
        clientId: Int,
        messagesPerClient: Int,
    ) {
        val client = connectWithRetry(clientId) ?: return

        client.use { client ->
            repeat(messagesPerClient) {
                executeRequest(
                    client = client,
                    clientId = clientId,
                )
            }
        }
    }

    private fun connectWithRetry(
        clientId: Int,
        attempts: Int = 10,
        delayMs: Long = 100,
    ): TcpClient? {
        var lastError: Exception? = null

        repeat(attempts) { attempt ->
            try {
                return TcpClient(host, port)
            } catch (e: Exception) {
                lastError = e
                logger.debug {
                    "client-$clientId: attempt ${attempt + 1}/$attempts failed: ${e.message}"
                }
                if (attempt < attempts - 1) {
                    Thread.sleep(delayMs)
                }
            }
        }

        logger.warn {
            "client-$clientId: connection failed after $attempts attempts: ${lastError?.message}"
        }
        return null
    }

    private fun executeRequest(
        client: TcpClient,
        clientId: Int,
    ) {
        val message = "Message ${(0..1000000).random()}"

        val startTime = System.nanoTime()
        val response = client.sendMessage(message)
        val endTime = System.nanoTime()

        val requestResult = RequestResult(
            startTime = startTime,
            endTime = endTime,
            response = response
        )

        logRequestResult(
            result = requestResult,
            clientId = clientId,
        )

        requestResults.add(requestResult)
    }

    private fun calculateP95(
        requestResults: List<RequestResult>
    ): Long {

        val sortedRtt = requestResults
            .map { it.rtt }
            .sorted()

        val p95Index = ceil(sortedRtt.size * 0.95).toInt() - 1
        return sortedRtt[p95Index]
    }

    private fun logRequestResult(
        result: RequestResult,
        clientId: Int,
    ) {
        withLoggingContext(
            "clientId" to clientId.toString(),
            "startTime_ms" to "%.3f".format(Locale.US, result.startTime / 1_000_000.0),
            "endTime_ms" to "%.3f".format(Locale.US, result.endTime / 1_000_000.0),
            "rtt_ms" to "%.3f".format(Locale.US, result.rtt / 1_000_000.0),
            "serverTime_s" to result.serverTime
        ) {
            requestLogger.info { result.response }
        }
    }

    private fun logBenchmarkSummary(
        clientCount: Int,
        messagesPerClient: Int,
        wallNanos: Long,
    ) {
        val results = requestResults.toList()

        if (results.isEmpty()) {
            logger.warn { "Parallel benchmark: no results" }
            return
        }

        val totalMessages = results.size
        val wallTimeMs = wallNanos / 1_000_000.0
        val totalRTT = results.sumOf { it.rtt }
        val maxRTTms = results.maxOf { it.rtt } / 1_000_000.0
        val minRTTms = results.minOf { it.rtt } / 1_000_000.0
        val totalRTTms = totalRTT / 1_000_000.0
        val averageRTTms = totalRTT.toDouble() / totalMessages / 1_000_000.0
        val p95ms = calculateP95(results) / 1_000_000.0

        withLoggingContext(
            "clients" to clientCount.toString(),
            "messagesPerClient" to messagesPerClient.toString(),
            "totalMessages" to totalMessages.toString(),
            "wallTime_ms" to "%.3f".format(Locale.US, wallTimeMs),
            "totalRTT_ms" to "%.3f".format(Locale.US, totalRTTms),
            "averageRTT_ms" to "%.3f".format(Locale.US, averageRTTms),
            "minRTT_ms" to "%.3f".format(Locale.US, minRTTms),
            "maxRTT_ms" to "%.3f".format(Locale.US, maxRTTms),
            "p95RTT_ms" to "%.3f".format(Locale.US, p95ms),
        ) {
            summaryLogger.info {
                "Parallel benchmark completed"
            }
        }
    }

    companion object {
        private val summaryLogger =
            KotlinLogging.logger("Benchmark.Parallel.Summary")

        private val requestLogger =
            KotlinLogging.logger("Benchmark.Parallel.Request")

        private val logger =
            KotlinLogging.logger {}
    }
}