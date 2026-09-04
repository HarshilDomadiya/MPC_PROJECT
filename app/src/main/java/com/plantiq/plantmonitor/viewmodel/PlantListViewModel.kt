package com.plantiq.plantmonitor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plantiq.plantmonitor.data.model.Plant
import com.plantiq.plantmonitor.data.model.Resource
import com.plantiq.plantmonitor.data.repository.FirebaseRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlantListViewModel(
    private val repository: FirebaseRepository = FirebaseRepository()
) : ViewModel() {

    private val _plantsState = MutableStateFlow<Resource<List<Plant>>>(Resource.Loading)
    val plantsState: StateFlow<Resource<List<Plant>>> = _plantsState.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _dialogError = MutableStateFlow<String?>(null)
    val dialogError: StateFlow<String?> = _dialogError.asStateFlow()

    private val _actionSuccess = MutableStateFlow(false)
    val actionSuccess: StateFlow<Boolean> = _actionSuccess.asStateFlow()

    private val _currentTime = MutableStateFlow(System.currentTimeMillis())
    private var timerJob: Job? = null
    private var loadPlantsJob: Job? = null

    init {
        loadPlants()
        startStatusTimer()
    }

    private fun startStatusTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                _currentTime.value = System.currentTimeMillis()
                recalculatePlantsStatus()
                delay(10_000L) // Refresh every 10 seconds
            }
        }
    }

    private fun recalculatePlantsStatus() {
        val currentState = _plantsState.value
        if (currentState is Resource.Success) {
            val now = _currentTime.value
            val updatedList = currentState.data.map { plant ->
                val isOnline = plant.lastSeen != 0L && (now - plant.lastSeen <= 120_000L)
                if (plant.online != isOnline) {
                    plant.copy(online = isOnline)
                } else {
                    plant
                }
            }
            _plantsState.value = Resource.Success(updatedList)
        }
    }

    fun loadPlants() {
        val uid = repository.getCurrentUid() ?: return
        if (loadPlantsJob?.isActive == true) return // Already loading

        loadPlantsJob = viewModelScope.launch {
            repository.getUserPlantsStream(uid).collect { resource ->
                if (resource is Resource.Success) {
                    val now = System.currentTimeMillis()
                    val processed = resource.data.map { plant ->
                        plant.copy(online = plant.lastSeen != 0L && (now - plant.lastSeen <= 120_000L))
                    }
                    _plantsState.value = Resource.Success(processed)
                } else {
                    _plantsState.value = resource
                }
            }
        }
    }

    fun createPlantAndClaimDevice(name: String, deviceId: String, claimCode: String) {
        val uid = repository.getCurrentUid()
        if (uid == null) {
            _dialogError.value = "User is not authenticated."
            return
        }
        if (name.isBlank()) {
            _dialogError.value = "Plant name cannot be empty."
            return
        }
        val cleanDeviceId = deviceId.trim()
        val cleanClaimCode = claimCode.trim()

        if (cleanDeviceId.isBlank()) {
            _dialogError.value = "Device ID cannot be empty."
            return
        }
        if (cleanClaimCode.length != 6) {
            _dialogError.value = "Claim code must be exactly 6 digits."
            return
        }

        viewModelScope.launch {
            _isSubmitting.value = true
            _dialogError.value = null

            // 1. Check if this user already has a plant with this deviceId locally
            val currentState = _plantsState.value
            if (currentState is Resource.Success) {
                if (currentState.data.any { it.deviceId == cleanDeviceId }) {
                    _isSubmitting.value = false
                    _dialogError.value = "You already have a plant connected to this device ($cleanDeviceId)."
                    return@launch
                }
            }

            // 2. Create the plant node under user-plants/$uid/$plantId
            val createResult = repository.createPlant(uid, name.trim(), cleanDeviceId)
            if (createResult.isFailure) {
                _isSubmitting.value = false
                _dialogError.value = createResult.exceptionOrNull()?.localizedMessage ?: "Failed to create plant entry."
                return@launch
            }

            val plantId = createResult.getOrThrow()

            // 3. Submit device claim request under claim-requests/$deviceId
            // IMPORTANT: If this fails, it's usually because the ESP32 is not online/registered.
            val claimResult = repository.claimDevice(uid, plantId, cleanDeviceId, cleanClaimCode)
            
            if (claimResult.isSuccess) {
                _isSubmitting.value = false
                _actionSuccess.value = true
            } else {
                _isSubmitting.value = false
                val errorMsg = claimResult.exceptionOrNull()?.localizedMessage ?: ""
                if (errorMsg.contains("Permission denied", ignoreCase = true)) {
                    _dialogError.value = "Access Denied. Ensure ESP32 is registered and claim code is correct."
                } else if (errorMsg.contains("timeout", ignoreCase = true)) {
                    _dialogError.value = "ESP32 did not respond. Check if it's powered ON and connected."
                } else {
                    _dialogError.value = "Claim failed: $errorMsg"
                }
            }
        }
    }

    fun claimDeviceToExistingPlant(plantId: String, deviceId: String, claimCode: String) {
        val uid = repository.getCurrentUid()
        if (uid == null) {
            _dialogError.value = "User is not authenticated."
            return
        }
        val cleanDeviceId = deviceId.trim()
        val cleanClaimCode = claimCode.trim()

        if (cleanDeviceId.isBlank()) {
            _dialogError.value = "Device ID cannot be empty."
            return
        }
        if (cleanClaimCode.length != 6) {
            _dialogError.value = "Claim code must be exactly 6 digits."
            return
        }

        viewModelScope.launch {
            _isSubmitting.value = true
            _dialogError.value = null

            val claimResult = repository.claimDevice(uid, plantId, cleanDeviceId, cleanClaimCode)
            _isSubmitting.value = false

            if (claimResult.isSuccess) {
                _actionSuccess.value = true
            } else {
                val errorMsg = claimResult.exceptionOrNull()?.localizedMessage ?: ""
                if (errorMsg.contains("Permission denied", ignoreCase = true)) {
                    _dialogError.value = "Access Denied. Ensure ESP32 is registered and claim code is correct."
                } else if (errorMsg.contains("timeout", ignoreCase = true)) {
                    _dialogError.value = "ESP32 did not respond. Check if it's powered ON and connected."
                } else {
                    _dialogError.value = "Claim failed: $errorMsg"
                }
            }
        }
    }

    fun resetActionState() {
        _actionSuccess.value = false
        _dialogError.value = null
        _isSubmitting.value = false
    }

    fun clearDialogError() {
        _dialogError.value = null
    }
}
