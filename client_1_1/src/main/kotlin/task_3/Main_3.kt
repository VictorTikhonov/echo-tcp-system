package ru.tikhonov.task_3

import ru.tikhonov.benchmark.GrpcBenchmark
import ru.tikhonov.utils.startGrpcBenchmark


fun main() {

    val benchmark = GrpcBenchmark(host = "localhost", port = 8083)
    startGrpcBenchmark(benchmark)
}