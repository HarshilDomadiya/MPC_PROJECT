package com.plantiq.plantmonitor.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plantiq.plantmonitor.data.model.*
import com.plantiq.plantmonitor.data.repository.FirebaseRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlantDashboardViewModel(
    private val repository: FirebaseRepository = FirebaseRepository()
) : ViewModel() {

    private val _selectedPlantId = MutableStateFlow<String?>(null)
    val selectedPlantId: StateFlow<String?> = _selectedPlantId.asStateFlow()

    private val _plant = MutableStateFlow<Plant?>(null)
    val plant: StateFlow<Plant?> = _plant.asStateFlow()

    private val _sensorData = MutableStateFlow<SensorData?>(null)
    val sensorData: StateFlow<SensorData?> = _sensorData.asStateFlow()

    private val _settings = MutableStateFlow<PlantSettings?>(null)
    val settings: StateFlow<PlantSettings?> = _settings.asStateFlow()

    private val _pumpState = MutableStateFlow<PumpState?>(null)
    val pumpState: StateFlow<PumpState?> = _pumpState.asStateFlow()

    private val _currentTimeMillis = MutableStateFlow(System.currentTimeMillis())
    val currentTimeMillis: StateFlow<Long> = _currentTimeMillis.asStateFlow()

    private val _isDeviceOnline = MutableStateFlow(false)
    val isDeviceOnline: StateFlow<Boolean> = _isDeviceOnline.asStateFlow()

    private val _statusText = MutableStateFlow("Loading...")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    private var plantJob: Job? = null
    private var sensorJob: Job? = null
    private var settingsJob: Job? = null
    private var pumpJob: Job? = null
    private var timerJob: Job? = null

    init {
        startLocalTimer()
    }

    private fun startLocalTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                _currentTimeMillis.value = System.currentTimeMillis()
                evaluateOnlineStatus()
                delay(10_000L) // 10 seconds tick
            }
        }
    }

    fun selectPlant(plantId: String) {
        if (_selectedPlantId.value == plantId) return
        _selectedPlantId.value = plantId

        val uid = repository.getCurrentUid() ?: return

        // Cancel previous plant listeners
        plantJob?.cancel()
        sensorJob?.cancel()
        settingsJob?.cancel()
        pumpJob?.cancel()

        plantJob = viewModelScope.launch {
            repository.getPlantStream(uid, plantId).collect { p ->
                _plant.value = p
                evaluateOnlineStatus()
            }
        }

        startSensorListener(uid, plantId)

        settingsJob = viewModelScope.launch {
            repository.getSettingsStream(uid, plantId).collect { st ->
                _settings.value = st
            }
        }

        pumpJob = viewModelScope.launch {
            repository.getPumpStream(uid, plantId).collect { pm ->
                _pumpState.value = pm
            }
        }
    }

    private fun startSensorListener(uid: String, plantId: String) {
        sensorJob?.cancel()
        Log.d("PlantIQ", "[ViewModel] Starting sensor listener for plant $plantId")
        sensorJob = viewModelScope.launch {
            repository.getSensorStream(uid, plantId).collect { s ->
                Log.d("PlantIQ", "[ViewModel] Collector received sensor: $s")
                _sensorData.value = s
                
                // If we got a sensor update, the device is communicating
                if (s != null) {
                    val currentP = _plant.value
                    if (currentP != null) {
                        // Update lastSeen based on sensor timestamp if newer
                        if (s.timestamp > currentP.lastSeen) {
                            _plant.value = currentP.copy(lastSeen = s.timestamp)
                            evaluateOnlineStatus()
                        }
                    }
                    // Force online if we just received a sensor snapshot
                    _isDeviceOnline.value = true
                    _statusText.value = "ONLINE"
                } else {
                    evaluateOnlineStatus()
                }
            }
        }
    }

    private fun evaluateOnlineStatus() {
        val currentPlant = _plant.value
        if (currentPlant == null || currentPlant.deviceId.isBlank()) {
            _isDeviceOnline.value = false
            _statusText.value = "No device connected"
            Log.d("PlantIQ", "[Status] No device connected")
            return
        }

        val lastSeen = currentPlant.lastSeen
        val now = _currentTimeMillis.value
        
        if (lastSeen == 0L) {
            _isDeviceOnline.value = false
            _statusText.value = "OFFLINE"
            Log.d("PlantIQ", "[Status] Offline (lastSeen=0)")
            return
        }

        // Normalize units. If lastSeen is small, it's likely Unix seconds (1.7e9 vs 1.7e12)
        val lastSeenMs = if (lastSeen < 10_000_000_000L) lastSeen * 1000L else lastSeen
        val diff = now - lastSeenMs
        
        val isOnline = diff in -120_000L..120_000L // 120s timeout, allow drift
        _isDeviceOnline.value = isOnline
        _statusText.value = if (isOnline) "ONLINE" else "OFFLINE"
        Log.d("PlantIQ", "[Status] ${if (isOnline) "ONLINE" else "OFFLINE"} | Diff: $diff ms (Now: $now, Last: $lastSeenMs)")
    }

    fun updateAutoWatering(enabled: Boolean) {
        val uid = repository.getCurrentUid() ?: return
        val pId = _selectedPlantId.value ?: return
        val currentThreshold = _settings.value?.moistureThreshold ?: 40.0

        // Optimistic UI update
        _settings.value = _settings.value?.copy(autoWatering = enabled)
            ?: PlantSettings(autoWatering = enabled, moistureThreshold = currentThreshold)

        viewModelScope.launch {
            val result = repository.updateSettings(uid, pId, enabled, currentThreshold)
            result.onFailure { err ->
                _actionError.value = "Failed to update auto watering: ${err.localizedMessage}"
            }
        }
    }

    fun updateThreshold(threshold: Double) {
        val uid = repository.getCurrentUid() ?: return
        val pId = _selectedPlantId.value ?: return
        val currentAuto = _settings.value?.autoWatering ?: false

        // Optimistic UI update
        _settings.value = _settings.value?.copy(moistureThreshold = threshold)
            ?: PlantSettings(autoWatering = currentAuto, moistureThreshold = threshold)

        viewModelScope.launch {
            val result = repository.updateSettings(uid, pId, currentAuto, threshold)
            result.onFailure { err ->
                _actionError.value = "Failed to update threshold: ${err.localizedMessage}"
            }
        }
    }

    fun togglePump(newStatus: Boolean) {
        val uid = repository.getCurrentUid() ?: return
        val pId = _selectedPlantId.value ?: return

        // Optimistic UI update
        _pumpState.value = PumpState(status = newStatus, lastChangedAt = System.currentTimeMillis())

        viewModelScope.launch {
            val result = repository.togglePump(uid, pId, newStatus)
            result.onFailure { err ->
                _actionError.value = "Failed to send pump command: ${err.localizedMessage}"
            }
        }
    }

    fun clearActionError() {
        _actionError.value = null
    }

    fun deletePlant(onSuccess: () -> Unit) {
        val uid = repository.getCurrentUid() ?: return
        val pId = _selectedPlantId.value ?: return
        viewModelScope.launch {
            repository.deletePlant(uid, pId).onSuccess {
                onSuccess()
            }
        }
    }

    fun reset() {
        plantJob?.cancel()
        sensorJob?.cancel()
        settingsJob?.cancel()
        pumpJob?.cancel()
        _plant.value = null
        _sensorData.value = null
        _settings.value = null
        _pumpState.value = null
        _selectedPlantId.value = null
        _statusText.value = "Loading..."
        _isDeviceOnline.value = false
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        plantJob?.cancel()
        sensorJob?.cancel()
        settingsJob?.cancel()
        pumpJob?.cancel()
    }
}
