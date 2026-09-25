package ru.tikhonov.task_1

import ru.tikhonov.utils.startBlockingBenchmark

fun main() {

    val benchmark = ParallelBenchmark(host = "localhost", port = 8081)
    startBlockingBenchmark(benchmark)
}