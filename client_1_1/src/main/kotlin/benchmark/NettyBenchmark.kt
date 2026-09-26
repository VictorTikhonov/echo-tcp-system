package ru.tikhonov.benchmark

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.withLoggingContext
import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import ru.tikhonov.model.RequestResult
import ru.tikhonov.task_2.NettyClient
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.concurrent.thread
import kotlin.math.ceil

class NettyBenchmark(
    private val host: String,
    private val port: Int,
) : Benchmark {

    // Одна EventLoop-группа на весь бенчмарк.
    // Если бы каждый клиент создавал свою — получилось бы "поток на соединение",
    // что прямо запрещено требованием 2.2.
    private val sharedGroup: EventLoopGroup = NioEventLoopGroup()

    // Потокобезопасная очередь результатов: пишут все client-потоки.
    private val requestResults = ConcurrentLinkedQueue<RequestResult>()

    /**
     * Запускает [clientCount] клиентов, каждый отправляет [messagesPerClient] сообщений.
     */
    override fun start(
        clientCount: Int,
        messagesPerClient: Int,
    ) {
        if (clientCount <= 0 || messagesPerClient <= 0) {
            logger.warn { "Netty benchmark not started" }
            return
        }

        requestResults.clear()

        val wallStart = System.nanoTime()

        // Каждый клиент — свой benchmark-поток.
        // Потоки нужны, чтобы запустить N клиентов параллельно.
        // Внутри они делят один sharedGroup (event loop).
        val threads = (1..clientCount).map { clientId ->
            thread(name = "netty-client-$clientId") {
                runClient(
                    clientId = clientId,
                    messagesPerClient = messagesPerClient,
                )
            }
        }

        // Ждём завершения всех клиентов.
        threads.forEach { it.join() }

        val wallEnd = System.nanoTime()

        logBenchmarkSummary(
            clientCount = clientCount,
            messagesPerClient = messagesPerClient,
            wallNanos = wallEnd - wallStart,
        )
    }

    /**
     * Один клиент: подключение, [messagesPerClient] обменов, закрытие.
     *
     * Если хоть одно сообщение падает — клиент завершается целиком.
     * После таймаута TCP-соединение считается отравленным:
     * продолжать по нему работу некорректно.
     */
    private fun runClient(
        clientId: Int,
        messagesPerClient: Int,
    ) {
        val client = NettyClient(
            host = host,
            port = port,
            group = sharedGroup,
        )

        try {
            client.connect()

            repeat(messagesPerClient) { messageIndex ->
                executeRequest(
                    client = client,
                    clientId = clientId,
                    messageIndex = messageIndex,
                )
            }
        } catch (e: Exception) {
            logger.warn(e) {
                "client-$clientId failed: ${e.message}"
            }
        } finally {
            client.close()
        }
    }

    /**
     * Одно сообщение: замер RTT, проверка ответа, запись результата.
     */
    private fun executeRequest(
        client: NettyClient,
        clientId: Int,
        messageIndex: Int,
    ) {
        val message = "Hello $messageIndex"

        val startTime = System.nanoTime()
        val response = client.sendMessage(message)
        val endTime = System.nanoTime()

        // Сервер обязан вернуть ECHO: <наше сообщение>.
        if (!response.startsWith("ECHO: $message")) {
            throw RuntimeException("Invalid response: $response")
        }

        val result = RequestResult(
            startTime = startTime,
            endTime = endTime,
            response = response,
        )

        logRequestResult(result = result, clientId = clientId)
        requestResults.add(result)
    }

    /** 95-й перцентиль RTT. */
    private fun calculateP95(results: List<RequestResult>): Long {
        val sortedRtt = results.map { it.rtt }.sorted()
        val index = ceil(sortedRtt.size * 0.95).toInt() - 1
        return sortedRtt[index]
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
            "serverTime_s" to result.serverTime,
        ) {
            requestLogger.info { result.response }
        }
    }

    /**
     * Сводная статистика: wall time, throughput, avg/min/max/p95 RTT.
     */
    private fun logBenchmarkSummary(
        clientCount: Int,
        messagesPerClient: Int,
        wallNanos: Long,
    ) {
        val results = requestResults.toList()

        if (results.isEmpty()) {
            logger.warn { "Netty benchmark: no results" }
            return
        }

        // Проверка полноты: если клиент упал, часть сообщений потеряна.
        val expectedMessages = clientCount * messagesPerClient
        if (results.size != expectedMessages) {
            logger.warn {
                "Incomplete: expected $expectedMessages, got ${results.size}"
            }
        }

        val totalMessages = results.size
        val wallTimeMs = wallNanos / 1_000_000.0
        val throughput = totalMessages / (wallNanos / 1_000_000_000.0)
        val totalRtt = results.sumOf { it.rtt }
        val averageRttMs = totalRtt.toDouble() / totalMessages / 1_000_000.0
        val minRttMs = results.minOf { it.rtt } / 1_000_000.0
        val maxRttMs = results.maxOf { it.rtt } / 1_000_000.0
        val p95RttMs = calculateP95(results) / 1_000_000.0
        val totalRttMs = totalRtt / 1_000_000.0

        withLoggingContext(
            "clients" to clientCount.toString(),
            "messagesPerClient" to messagesPerClient.toString(),
            "totalMessages" to totalMessages.toString(),
            "wallTime_ms" to "%.3f".format(Locale.US, wallTimeMs),
            "throughput_msgPerSec" to "%.1f".format(Locale.US, throughput),
            "totalRTT_ms" to "%.3f".format(Locale.US, totalRttMs),
            "averageRTT_ms" to "%.3f".format(Locale.US, averageRttMs),
            "minRTT_ms" to "%.3f".format(Locale.US, minRttMs),
            "maxRTT_ms" to "%.3f".format(Locale.US, maxRttMs),
            "p95RTT_ms" to "%.3f".format(Locale.US, p95RttMs),
        ) {
            summaryLogger.info { "Netty benchmark completed" }
        }
    }

    /** Останавливает event loop. Вызывается из main после бенчмарка. */
    fun close() {
        sharedGroup.shutdownGracefully().syncUninterruptibly()
    }

    companion object {
        private val summaryLogger = KotlinLogging.logger("Benchmark.Netty.Summary")
        private val requestLogger = KotlinLogging.logger("Benchmark.Netty.Request")
        private val logger = KotlinLogging.logger {}
    }
}