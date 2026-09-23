package ru.tikhonov.utils

import ru.tikhonov.task_1.ParallelBenchmark

class BlockingBenchmarkRunner(
    val benchmark: ParallelBenchmark
) : BenchmarkRunnerInterface {

    override fun runBenchmark(countTcpClient: Int, messagesPerClient : Int) {
        benchmark.start(countTcpClient, messagesPerClient )
    }
}