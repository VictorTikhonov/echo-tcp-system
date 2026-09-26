package ru.tikhonov.task_3

import io.github.oshai.kotlinlogging.KotlinLogging
import io.grpc.stub.StreamObserver
import ru.tikhonov.task_3.proto.EchoRequest
import ru.tikhonov.task_3.proto.EchoResponse
import ru.tikhonov.task_3.proto.EchoServiceGrpc

class GrpcServer : EchoServiceGrpc.EchoServiceImplBase() {

    override fun echoStream(
        responseObserver: StreamObserver<EchoResponse>,
    ): StreamObserver<EchoRequest> {
        return object : StreamObserver<EchoRequest> {

            override fun onNext(request: EchoRequest) {
                val response = EchoResponse.newBuilder()
                    .setSequence(request.sequence)
                    .setMessage("ECHO: ${request.message}")
                    .setServerTime(System.currentTimeMillis() / 1000.0)
                    .build()

                responseObserver.onNext(response)

                logger.info {
                    "seq=${request.sequence}, " +
                            "msg=${request.message}"
                }
            }

            override fun onError(t: Throwable) {
                logger.warn(t) { "Client stream error" }
                responseObserver.onError(t)
            }

            override fun onCompleted() {
                logger.info { "Client stream completed" }
                responseObserver.onCompleted()
            }
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}