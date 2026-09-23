package ru.tikhonov.task_1

class RequestResult(
    val startTime: Long,
    val endTime: Long,
    val response: String,
) {
    val rtt: Long
        get() = endTime - startTime

    val serverTime: String
        get() = response
            .substringAfter("[t=")
            .substringBefore("]")
}