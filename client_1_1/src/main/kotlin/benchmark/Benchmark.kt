package ru.tikhonov.benchmark

interface Benchmark {
    fun start(
        clientCount: Int,
        messagesPerClient: Int
    )
}