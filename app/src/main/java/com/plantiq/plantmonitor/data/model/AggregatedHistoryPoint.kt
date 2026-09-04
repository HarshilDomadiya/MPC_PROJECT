package com.plantiq.plantmonitor.data.model

data class AggregatedHistoryPoint(
    val timestamp: Long,
    val formattedTime: String,
    val avgMoisture: Double,
    val avgTemperature: Double,
    val avgHumidity: Double
)
