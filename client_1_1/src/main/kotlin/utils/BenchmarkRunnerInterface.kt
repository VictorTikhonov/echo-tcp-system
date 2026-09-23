package ru.tikhonov.utils

interface BenchmarkRunnerInterface {
    fun runBenchmark(countTcpClient: Int, messagesPerClient: Int)
}