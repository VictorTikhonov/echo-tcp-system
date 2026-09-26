package ru.tikhonov.task_2

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.withLoggingContext
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import java.time.Instant
import java.util.Locale


class MessageHandler : SimpleChannelInboundHandler<String>() {

    /**
     * Вызывается, когда из канала пришла очередная готовая строка
     * (StringDecoder разбил входящий поток по \n).
     *
     * writeAndFlush асинхронный — не блокирует event loop.
     * Ответ уйдёт, когда сокет будет готов к записи.
     */
    override fun channelRead0(
        ctx: ChannelHandlerContext,
        message: String,
    ) {
        val now = Instant.now()
        val timestamp = now.epochSecond + now.nano / 1_000_000_000.0
        val ts = "%.6f".format(Locale.US, timestamp)

        val response = "ECHO: $message [t=$ts]\n"

        // Асинхронная отправка — не блокирует event loop.
        ctx.writeAndFlush(response)

        // Логируем ответ в отдельный appender (как TCP-сервер в task_1).
        // remote — адрес клиента, thread — имя event loop потока.
        withLoggingContext(
            "remote" to ctx.channel().remoteAddress().toString(),
            "thread" to Thread.currentThread().name,
        ) {
            responseLogger.info { "Response: ${response.trimEnd()}" }
        }
    }

    /**
     * Любая ошибка в pipeline — логируем и закрываем соединение.
     * Не пробрасываем дальше: соединение нерабочее.
     */
    override fun exceptionCaught(
        ctx: ChannelHandlerContext,
        cause: Throwable,
    ) {
        logger.warn(cause) {
            "Client ${ctx.channel().remoteAddress()} error: ${cause.message}"
        }
        ctx.close()
    }

    /**
     * Новое соединение установлено.
     * super.channelActive вызывает fireChannelActive для следующих
     * handler'ов в pipeline (у нас их нет, но правильнее так).
     */
    override fun channelActive(ctx: ChannelHandlerContext) {
        logger.info {
            "Client connected: ${ctx.channel().remoteAddress()}"
        }
        super.channelActive(ctx)
    }

    /**
     * Соединение закрыто (клиент отключился или мы закрыли).
     */
    override fun channelInactive(ctx: ChannelHandlerContext) {
        logger.info {
            "Client disconnected: ${ctx.channel().remoteAddress()}"
        }
        super.channelInactive(ctx)
    }

    companion object {
        // Основной логгер: старт/стоп соединений, ошибки.
        private val logger = KotlinLogging.logger {}

        // Логгер ответов: пишет через appender SERVER_RESPONSE
        // с полями remote и thread (симметрично TCP-серверу).
        private val responseLogger =
            KotlinLogging.logger("Benchmark.Server.Response")
    }
}