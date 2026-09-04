package com.plantiq.plantmonitor.data.model

data class PlantSettings(
    val autoWatering: Boolean = false,
    val moistureThreshold: Double = 40.0
)
