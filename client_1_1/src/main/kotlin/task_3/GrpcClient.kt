package ru.tikhonov.task_3

import io.github.oshai.kotlinlogging.KotlinLogging
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.stub.StreamObserver
import ru.tikhonov.task_3.proto.EchoRequest
import ru.tikhonov.task_3.proto.EchoResponse
import ru.tikhonov.task_3.proto.EchoServiceGrpc
import java.io.Closeable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class GrpcClient(
    host: String,
    port: Int,
    private val timeoutMs: Long = 30_000,
) : Closeable {

    // gRPC-канал для соединения с сервером
    private val channel: ManagedChannel =
        ManagedChannelBuilder.forAddress(host, port)
            .usePlaintext()
            .build()

    // Асинхронный gRPC stub для bidirectional streaming RPC
    private val stub = EchoServiceGrpc.newStub(channel)

    // Очередь для получения ответов от сервера
    private val responses = ConcurrentLinkedQueue<EchoResponse>()

    fun sendStream(messages: List<String>): List<EchoResponse> {
        // Очищаем результаты предыдущего streaming RPC
        responses.clear()

        // Используется для ожидания завершения всего потока ответов
        // Начальное значение 1 означает, что нужно дождаться одного события:
        // завершения или ошибки gRPC-потока
        val latch = CountDownLatch(1)

        var error: Throwable? = null

        // Callback, через который gRPC передаёт ответы от сервера
        val responseObserver = object : StreamObserver<EchoResponse> {

            // Вызывается при получении очередного ответа от сервера
            override fun onNext(value: EchoResponse) {
                responses.add(value)
            }

            // Вызывается при возникновении ошибки во время streaming RPC
            override fun onError(t: Throwable) {
                logger.warn(t) {
                    "gRPC stream error after ${responses.size}/${messages.size} responses"
                }

                error = t

                // Уменьшаем счётчик до 0, чтобы освободить поток, ожидающий на latch.await().
                latch.countDown()
            }

            // Вызывается после получения всех ответов от сервера
            override fun onCompleted() {
                // Сигнализируем основному потоку, что streaming RPC завершён
                latch.countDown()
            }
        }

        // Открываем двунаправленный поток
        // requestObserver используется клиентом для отправки сообщений серверу
        // responseObserver используется gRPC для передачи ответов клиенту
        val requestObserver = stub.echoStream(responseObserver)

        // Отправляем все сообщения через один streaming RPC
        messages.forEachIndexed { index, msg ->
            val request = EchoRequest.newBuilder()
                .setSequence(index.toLong())
                .setMessage(msg)
                .build()

            requestObserver.onNext(request)
        }

        // Сообщаем серверу, что клиент закончил отправлять сообщения
        // После этого сервер должен продолжить отправлять оставшиеся ответы
        requestObserver.onCompleted()

        // Ждём, пока сервер завершит поток ответов или пока не истечёт timeout
        if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            throw RuntimeException(
                "Timed out after $timeoutMs ms: " +
                        "received ${responses.size}/${messages.size}"
            )
        }

        // Если во время работы потока произошла ошибка,
        // пробрасываем её вызывающему коду
        error?.let { throw RuntimeException("gRPC error", it) }

        // Возвращаем все полученные ответы
        return responses.toList()
    }

    override fun close() {
        // Корректно закрываем gRPC-канал и ждём его завершения.
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}