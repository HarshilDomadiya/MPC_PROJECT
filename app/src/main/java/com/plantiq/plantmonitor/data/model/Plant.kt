package com.plantiq.plantmonitor.data.model

data class Plant(
    val plantId: String = "",
    val ownerId: String = "",
    val deviceId: String = "",
    val name: String = "",
    val createdAt: Long = 0L,
    val online: Boolean = false,
    val lastSeen: Long = 0L
)
