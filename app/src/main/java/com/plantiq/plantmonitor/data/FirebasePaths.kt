package com.plantiq.plantmonitor.data

/**
 * Centralized Firebase Realtime Database path helper.
 * Strictly adheres to the Firebase Realtime Database Security Rules.
 */
object FirebasePaths {
    fun user(uid: String) = "users/$uid"
    
    fun unclaimedDevice(deviceId: String) = "unclaimed-devices/$deviceId"
    
    fun claimRequest(deviceId: String) = "claim-requests/$deviceId"
    
    fun deviceMetadata(deviceId: String) = "devices/$deviceId/metadata"
    
    fun userPlants(uid: String) = "user-plants/$uid"
    
    fun userPlant(uid: String, plantId: String) = "user-plants/$uid/$plantId"
    
    // Sensor and History are plant-centric according to the verified structure
    fun sensor(uid: String, plantId: String) = "user-plants/$uid/$plantId/sensor"
    
    fun history(uid: String, plantId: String) = "user-plants/$uid/$plantId/history"
    
    fun settings(uid: String, plantId: String) = "user-plants/$uid/$plantId/settings"
    
    fun pump(uid: String, plantId: String) = "user-plants/$uid/$plantId/pump"
    
    fun online(uid: String, plantId: String) = "user-plants/$uid/$plantId/online"
    
    fun lastSeen(uid: String, plantId: String) = "user-plants/$uid/$plantId/lastSeen"
}
