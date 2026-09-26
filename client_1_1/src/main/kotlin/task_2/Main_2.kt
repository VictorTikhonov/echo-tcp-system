package ru.tikhonov.task_2

import ru.tikhonov.benchmark.NettyBenchmark
import ru.tikhonov.utils.startBenchmark

fun main() {
    val benchmark = NettyBenchmark(host = "localhost", port = 8082)
    try {
        startBenchmark(benchmark)
    } finally {
        benchmark.close()
    }
}