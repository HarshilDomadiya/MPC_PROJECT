package com.example.plantmonitor.data.model

data class HistoryReading(
    val readingId: String = "",
    val moisture: Double = 0.0,
    val temperature: Double = 0.0,
    val humidity: Double = 0.0,
    val timestamp: Long = 0L
)
