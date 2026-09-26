package ru.tikhonov.task_2

import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.LineBasedFrameDecoder
import io.netty.handler.codec.string.StringDecoder
import io.netty.handler.codec.string.StringEncoder
import io.netty.util.CharsetUtil
import java.io.Closeable
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit


class NettyClient(
    private val host: String,
    private val port: Int,
    private val group: EventLoopGroup,
    private val timeoutMs: Long = 5_000,
) : Closeable {

    private lateinit var channel: Channel

    // Последовательная схема send -> response: достаточно одного слота.
    private val responses: BlockingQueue<String> = ArrayBlockingQueue(1)

    /**
     * Открывает TCP-соединение и ждёт его установки
     */
    fun connect() {
        val bootstrap = Bootstrap()
            .group(group)
            .channel(NioSocketChannel::class.java)
            .option(ChannelOption.TCP_NODELAY, true)
            .option(ChannelOption.SO_KEEPALIVE, true)
            .handler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(channel: SocketChannel) {
                    // Pipeline обработки входящих и исходящих данных.
                    channel.pipeline().apply {
                        addLast(LineBasedFrameDecoder(1024))
                        addLast(StringDecoder(CharsetUtil.UTF_8))
                        addLast(StringEncoder(CharsetUtil.UTF_8))
                        addLast(ResponseHandler(responses))
                    }
                }
            })

        // connect() асинхронный, sync() ждёт завершения.
        channel = bootstrap.connect(host, port).sync().channel()
    }

    /**
     * Отправляет [message] и ждёт ответ
     *
     * writeAndFlush().sync() — гарантирует, что строка ушла в сокет
     * responses.poll() — блокирует benchmark-поток до прихода ответа
     * (или до таймаута [timeoutMs])
     */
    fun sendMessage(message: String): String {
        channel.writeAndFlush("$message\n").sync()

        return responses.poll(timeoutMs, TimeUnit.MILLISECONDS)
            ?: throw RuntimeException("Timed out waiting for response")
    }

    /**
     * Закрывает канал
     */
    override fun close() {
        if (::channel.isInitialized) {
            channel.close().syncUninterruptibly()
        }
    }

    /**
     * Хендлер входящих сообщений: каждая готовая строка кладётся в очередь
     */
    private class ResponseHandler(
        private val responses: BlockingQueue<String>,
    ) : SimpleChannelInboundHandler<String>() {

        override fun channelRead0(
            ctx: ChannelHandlerContext,
            message: String,
        ) {
            responses.add(message)
        }

        // При любой ошибке закрываем соединение
        override fun exceptionCaught(
            ctx: ChannelHandlerContext,
            cause: Throwable,
        ) {
            ctx.close()
        }
    }
}