package com.plantiq.plantmonitor.data.model

enum class TimeRange(
    val label: String,
    val durationMillis: Long,
    val bucketMinutes: Int
) {
    ONE_HOUR("1H", 60 * 60 * 1000L, 10),
    SIX_HOURS("6H", 6 * 60 * 60 * 1000L, 10),
    TWENTY_FOUR_HOURS("24H", 24 * 60 * 60 * 1000L, 30),
    SEVEN_DAYS("7D", 7 * 24 * 60 * 60 * 1000L, 30)
}
