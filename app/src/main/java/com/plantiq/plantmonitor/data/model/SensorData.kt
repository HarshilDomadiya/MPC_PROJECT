package com.plantiq.plantmonitor.data.model

data class SensorData(
    val moisture: Double = 0.0,
    val temperature: Double = 0.0,
    val humidity: Double = 0.0,
    val timestamp: Long = 0L,
    val dhtConnected: Boolean = false,
    val soilConnected: Boolean = false
)
