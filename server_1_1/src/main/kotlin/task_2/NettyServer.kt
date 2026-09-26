package ru.tikhonov.task_2

import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.LineBasedFrameDecoder
import io.netty.handler.codec.string.StringDecoder
import io.netty.handler.codec.string.StringEncoder
import io.netty.util.CharsetUtil
import java.io.Closeable

/**
 * Асинхронный сервер на Netty
 */
class NettyServer(
    private val port: Int,
) : Closeable {

    // Принимает входящие соединения. Достаточно одного потока.
    private val bossGroup: EventLoopGroup = NioEventLoopGroup(1)

    // Обслуживает I/O всех соединений несколькими event loop потоками.
    private val workerGroup: EventLoopGroup = NioEventLoopGroup()

    private var channel: Channel? = null

    /**
     * Запускает сервер и блокирует вызывающий поток до закрытия канала.
     * Для остановки из другого потока — close().
     */
    fun start() {
        val bootstrap = ServerBootstrap()
            .group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel::class.java)
            // Размер очереди ожидающих подключений.
            .option(ChannelOption.SO_BACKLOG, 512)
            // Настройки дочерних каналов (для принятых соединений).
            .childOption(ChannelOption.TCP_NODELAY, true)
            .childOption(ChannelOption.SO_KEEPALIVE, true)
            .childHandler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(channel: SocketChannel) {
                    // Свой pipeline на каждое соединение.
                    channel.pipeline().apply {
                        addLast(LineBasedFrameDecoder(1024))
                        addLast(StringDecoder(CharsetUtil.UTF_8))
                        addLast(StringEncoder(CharsetUtil.UTF_8))
                        addLast(MessageHandler())
                    }
                }
            })

        // bind асинхронный, sync() ждёт завершения.
        channel = bootstrap.bind(port).sync().channel()
        logger.info { "Netty server started on port $port" }

        // Блокируем поток до закрытия канала.
        channel!!.closeFuture().sync()
    }

    /**
     * Останавливает сервер:
     *   1. закрывает серверный канал (новые подключения не принимаются);
     *   2. останавливает bossGroup и workerGroup.
     *
     * Без shutdownGracefully event loop потоки (non-daemon)
     * не дадут JVM завершиться.
     */
    override fun close() {
        channel?.close()?.syncUninterruptibly()
        bossGroup.shutdownGracefully().syncUninterruptibly()
        workerGroup.shutdownGracefully().syncUninterruptibly()
        logger.info { "Netty server stopped" }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}