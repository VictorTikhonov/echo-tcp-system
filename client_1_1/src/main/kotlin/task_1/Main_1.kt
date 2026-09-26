package ru.tikhonov.task_1

import ru.tikhonov.benchmark.BlockingBenchmark
import ru.tikhonov.utils.startBenchmark

fun main() {

    val benchmark = BlockingBenchmark(host = "localhost", port = 8081)
    startBenchmark(benchmark)
}