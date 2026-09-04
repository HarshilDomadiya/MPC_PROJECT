package com.plantiq.plantmonitor.data.model

data class PumpState(
    val status: Boolean = false,
    val lastChangedAt: Long = 0L
)
