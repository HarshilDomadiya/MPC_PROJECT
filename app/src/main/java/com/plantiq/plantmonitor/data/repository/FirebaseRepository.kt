package com.plantiq.plantmonitor.data.repository

import android.util.Log
import com.plantiq.plantmonitor.data.FirebasePaths
import com.plantiq.plantmonitor.data.model.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirebaseRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance("https://plantmonitor-4ab71-default-rtdb.asia-southeast1.firebasedatabase.app")
) {

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    fun getCurrentUid(): String? = auth.currentUser?.uid

    // ============================================================
    // AUTHENTICATION & USER PROFILE
    // ============================================================

    suspend fun signUp(name: String, email: String, pass: String): Result<FirebaseUser> {
        return try {
            val authResult = auth.createUserWithEmailAndPassword(email, pass).await()
            val user = authResult.user ?: throw Exception("User creation failed")
            
            // Create user profile record in database
            val profile = UserProfile(
                name = name,
                email = email,
                createdAt = System.currentTimeMillis()
            )
            database.getReference(FirebasePaths.user(user.uid)).setValue(profile).await()
            
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun login(email: String, pass: String): Result<FirebaseUser> {
        return try {
            val authResult = auth.signInWithEmailAndPassword(email, pass).await()
            val user = authResult.user ?: throw Exception("Login failed")
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun logout() {
        auth.signOut()
    }

    fun getUserProfileStream(uid: String): Flow<Resource<UserProfile>> = callbackFlow {
        trySend(Resource.Loading)
        val ref = database.getReference(FirebasePaths.user(uid))
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val name = snapshot.child("name").getValue(String::class.java) ?: ""
                    val email = snapshot.child("email").getValue(String::class.java) ?: ""
                    val createdAt = snapshot.child("createdAt").getValue(Long::class.java) ?: 0L
                    trySend(Resource.Success(UserProfile(name, email, createdAt)))
                } else {
                    trySend(Resource.Success(UserProfile(name = "User", email = "")))
                }
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(Resource.Error("Firebase permission denied or network error: ${error.message}"))
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    // ============================================================
    // PLANT MANAGEMENT
    // ============================================================

    fun getUserPlantsStream(uid: String): Flow<Resource<List<Plant>>> = callbackFlow {
        trySend(Resource.Loading)
        val ref = database.getReference(FirebasePaths.userPlants(uid))
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val plants = mutableListOf<Plant>()
                for (child in snapshot.children) {
                    val plantId = child.child("plantId").getValue(String::class.java) ?: child.key ?: continue
                    val ownerId = child.child("ownerId").getValue(String::class.java) ?: uid
                    val deviceId = child.child("deviceId").getValue(String::class.java) ?: ""
                    val name = child.child("name").getValue(String::class.java) ?: "Plant"
                    val createdAt = child.child("createdAt").getValue(Long::class.java) ?: 0L
                    val online = child.child("online").getValue(Boolean::class.java) ?: false
                    val lastSeen = child.child("lastSeen").getValue(Long::class.java) ?: 0L
                    
                    plants.add(
                        Plant(
                            plantId = plantId,
                            ownerId = ownerId,
                            deviceId = deviceId,
                            name = name,
                            createdAt = createdAt,
                            online = online,
                            lastSeen = lastSeen
                        )
                    )
                }
                trySend(Resource.Success(plants))
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(Resource.Error("Unable to load plant list: ${error.message}"))
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun createPlant(uid: String, name: String, deviceId: String): Result<String> {
        return try {
            val userPlantsRef = database.getReference(FirebasePaths.userPlants(uid))
            val plantId = userPlantsRef.push().key ?: throw Exception("Failed to generate plant ID")
            
            val newPlantMap = mapOf(
                "plantId" to plantId,
                "ownerId" to uid,
                "deviceId" to deviceId,
                "name" to name,
                "createdAt" to System.currentTimeMillis(),
                "online" to false,
                "lastSeen" to 0L
            )
            
            // Set plant core info
            userPlantsRef.child(plantId).setValue(newPlantMap).await()
            
            // Initialize default settings (Matches ESP32 logic)
            database.getReference(FirebasePaths.settings(uid, plantId)).setValue(
                mapOf(
                    "autoWatering" to false,
                    "moistureThreshold" to 40.0
                )
            ).await()
            
            Result.success(plantId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ============================================================
    // DEVICE CLAIM FLOW
    // ============================================================

    suspend fun claimDevice(uid: String, plantId: String, deviceId: String, claimCode: String): Result<Unit> {
        Log.d("PlantIQ", "CLAIM START | UID: $uid | Device: $deviceId | Plant: $plantId")
        return try {
            val formattedDeviceId = deviceId.trim()
            val formattedClaimCode = claimCode.trim()
            
            if (formattedClaimCode.length != 6) {
                throw Exception("Claim code must be exactly 6 digits.")
            }
            
            val claimReqRef = database.getReference(FirebasePaths.claimRequest(formattedDeviceId))
            val claimData = mapOf(
                "ownerId" to uid,
                "plantId" to plantId,
                "claimCode" to formattedClaimCode
            )
            
            // 1. Create claim request
            Log.d("PlantIQ", "Writing claim request...")
            claimReqRef.setValue(claimData).await()
            Log.d("PlantIQ", "Claim request written successfully")
            
            // 2. Wait for ESP32 to create metadata (timeout after 30s)
            val metadataRef = database.getReference(FirebasePaths.deviceMetadata(formattedDeviceId))
            Log.d("PlantIQ", "Waiting for metadata...")
            var success = false
            val start = System.currentTimeMillis()
            
            while (System.currentTimeMillis() - start < 30000) {
                val snapshot = try {
                    metadataRef.get().await()
                } catch (e: Exception) {
                    // Rule might deny read until metadata exists
                    Log.d("PlantIQ", "Metadata read denied or failed, retrying... (${e.message})")
                    null
                }
                
                if (snapshot != null && snapshot.exists()) {
                    val mOwnerId = snapshot.child("ownerId").getValue(String::class.java)
                    val mPlantId = snapshot.child("plantId").getValue(String::class.java)
                    Log.d("PlantIQ", "Metadata found! Owner: $mOwnerId, Plant: $mPlantId")
                    if (mOwnerId == uid && mPlantId == plantId) {
                        success = true
                        break
                    }
                }
                delay(2000)
            }
            
            if (!success) {
                Log.e("PlantIQ", "Handshake timeout - metadata never appeared or mismatched")
                throw Exception("Handshake timeout. Ensure ESP32 is online and listening for claim requests.")
            }

            // 3. Update the plant node with the deviceId
            Log.d("PlantIQ", "Updating plant node with device ID...")
            database.getReference(FirebasePaths.userPlant(uid, plantId))
                .child("deviceId").setValue(formattedDeviceId).await()

            // 4. Cleanup claim request (SKIPPED because Rules deny Android deletion)
            // ESP32 should clean up or we ignore it.
            Log.d("PlantIQ", "CLAIM SUCCESS")

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("PlantIQ", "CLAIM FAILURE: ${e.message}")
            Result.failure(e)
        }
    }

    // ============================================================
    // REALTIME DASHBOARD STREAMS
    // ============================================================

    fun getPlantStream(uid: String, plantId: String): Flow<Plant?> = callbackFlow {
        val ref = database.getReference(FirebasePaths.userPlant(uid, plantId))
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    trySend(null)
                    return
                }
                val pId = snapshot.child("plantId").getValue(String::class.java) ?: plantId
                val oId = snapshot.child("ownerId").getValue(String::class.java) ?: uid
                val dId = snapshot.child("deviceId").getValue(String::class.java) ?: ""
                val name = snapshot.child("name").getValue(String::class.java) ?: ""
                val createdAt = snapshot.child("createdAt").getValue(Long::class.java) ?: 0L
                val online = snapshot.child("online").getValue(Boolean::class.java) ?: false
                val lastSeen = snapshot.child("lastSeen").getValue(Long::class.java) ?: 0L
                
                trySend(Plant(pId, oId, dId, name, createdAt, online, lastSeen))
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(null)
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun getSensorStream(uid: String, plantId: String): Flow<SensorData?> = callbackFlow {
        if (uid.isBlank() || plantId.isBlank()) {
            Log.d("PlantIQ", "[Sensor] Stream failed: Blank IDs")
            trySend(null)
            return@callbackFlow
        }
        val path = FirebasePaths.sensor(uid, plantId)
        Log.d("PlantIQ", "[Sensor] Starting listener at: $path")
        val ref = database.getReference(path)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d("PlantIQ", "[Sensor] Snapshot received from $path | Exists: ${snapshot.exists()}")
                if (!snapshot.exists()) {
                    trySend(null)
                    return
                }
                try {
                    val moisture = snapshot.child("moisture").getValue(Double::class.java) 
                        ?: snapshot.child("moisture").getValue(Long::class.java)?.toDouble() ?: 0.0
                    val temp = snapshot.child("temperature").getValue(Double::class.java)
                        ?: snapshot.child("temperature").getValue(Long::class.java)?.toDouble() ?: 0.0
                    val humidity = snapshot.child("humidity").getValue(Double::class.java)
                        ?: snapshot.child("humidity").getValue(Long::class.java)?.toDouble() ?: 0.0
                    val ts = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L
                    val dhtConn = snapshot.child("dhtConnected").getValue(Boolean::class.java) ?: true
                    val soilConn = snapshot.child("soilConnected").getValue(Boolean::class.java) ?: true
                    
                    Log.d("PlantIQ", "[Sensor] Parsed: M=$moisture, T=$temp, H=$humidity, TS=$ts")
                    trySend(SensorData(moisture, temp, humidity, ts, dhtConn, soilConn))
                } catch (e: Exception) {
                    Log.e("PlantIQ", "[Sensor] Parsing error: ${e.message}", e)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("PlantIQ", "[Sensor] Listener cancelled: ${error.message}")
                trySend(null)
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { 
            Log.d("PlantIQ", "[Sensor] Closing listener for $path")
            ref.removeEventListener(listener) 
        }
    }

    fun getSettingsStream(uid: String, plantId: String): Flow<PlantSettings?> = callbackFlow {
        val ref = database.getReference(FirebasePaths.settings(uid, plantId))
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    trySend(null)
                    return
                }
                val autoWatering = snapshot.child("autoWatering").getValue(Boolean::class.java) ?: false
                val threshold = snapshot.child("moistureThreshold").getValue(Double::class.java)
                    ?: snapshot.child("moistureThreshold").getValue(Long::class.java)?.toDouble() ?: 40.0
                trySend(PlantSettings(autoWatering, threshold))
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(null)
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun getPumpStream(uid: String, plantId: String): Flow<PumpState?> = callbackFlow {
        val ref = database.getReference(FirebasePaths.pump(uid, plantId))
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    trySend(null)
                    return
                }
                val status = snapshot.child("status").getValue(Boolean::class.java) ?: false
                val lastChangedAt = snapshot.child("lastChangedAt").getValue(Long::class.java) ?: 0L
                trySend(PumpState(status, lastChangedAt))
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(null)
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun getHistoryStream(uid: String, plantId: String): Flow<List<HistoryReading>> = callbackFlow {
        if (uid.isBlank() || plantId.isBlank()) {
            trySend(emptyList())
            return@callbackFlow
        }
        val path = FirebasePaths.history(uid, plantId)
        val ref = database.getReference(path)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<HistoryReading>()
                for (child in snapshot.children) {
                    val rId = child.key ?: ""
                    val moisture = child.child("moisture").getValue(Double::class.java)
                        ?: child.child("moisture").getValue(Long::class.java)?.toDouble() ?: 0.0
                    val temp = child.child("temperature").getValue(Double::class.java)
                        ?: child.child("temperature").getValue(Long::class.java)?.toDouble() ?: 0.0
                    val humidity = child.child("humidity").getValue(Double::class.java)
                        ?: child.child("humidity").getValue(Long::class.java)?.toDouble() ?: 0.0
                    val ts = child.child("timestamp").getValue(Long::class.java) ?: 0L
                    
                    list.add(HistoryReading(rId, moisture, temp, humidity, ts))
                }
                trySend(list)
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(emptyList())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    // ============================================================
    // CONTROLS & WRITES
    // ============================================================

    suspend fun updateSettings(
        uid: String,
        plantId: String,
        autoWatering: Boolean,
        moistureThreshold: Double
    ): Result<Unit> {
        return try {
            val ref = database.getReference(FirebasePaths.settings(uid, plantId))
            val map = mapOf(
                "autoWatering" to autoWatering,
                "moistureThreshold" to moistureThreshold
            )
            ref.setValue(map).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun togglePump(uid: String, plantId: String, status: Boolean): Result<Unit> {
        return try {
            val ref = database.getReference(FirebasePaths.pump(uid, plantId))
            val map = mapOf(
                "status" to status,
                "lastChangedAt" to System.currentTimeMillis()
            )
            ref.setValue(map).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deletePlant(uid: String, plantId: String): Result<Unit> {
        return try {
            database.getReference(FirebasePaths.userPlant(uid, plantId)).removeValue().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
