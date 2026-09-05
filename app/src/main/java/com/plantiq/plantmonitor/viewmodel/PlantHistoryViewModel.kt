package com.plantiq.plantmonitor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plantiq.plantmonitor.data.model.AggregatedHistoryPoint
import com.plantiq.plantmonitor.data.model.HistoryReading
import com.plantiq.plantmonitor.data.model.TimeRange
import com.plantiq.plantmonitor.data.repository.FirebaseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class PlantHistoryViewModel(
    private val repository: FirebaseRepository = FirebaseRepository()
) : ViewModel() {

    private val _selectedRange = MutableStateFlow(TimeRange.SIX_HOURS)
    val selectedRange: StateFlow<TimeRange> = _selectedRange.asStateFlow()

    private val _rawReadings = MutableStateFlow<List<HistoryReading>>(emptyList())
    val rawReadings: StateFlow<List<HistoryReading>> = _rawReadings.asStateFlow()

    private val _aggregatedPoints = MutableStateFlow<List<AggregatedHistoryPoint>>(emptyList())
    val aggregatedPoints: StateFlow<List<AggregatedHistoryPoint>> = _aggregatedPoints.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadHistory(plantId: String) {
        val uid = repository.getCurrentUid() ?: return
        viewModelScope.launch {
            _isLoading.value = true
            repository.getHistoryStream(uid, plantId).collect { readings ->
                _rawReadings.value = readings
                _isLoading.value = false
                processAndAggregateData()
            }
        }
    }

    fun selectRange(range: TimeRange) {
        _selectedRange.value = range
        processAndAggregateData()
    }

    fun reset() {
        _rawReadings.value = emptyList()
        _aggregatedPoints.value = emptyList()
        _isLoading.value = false
    }

    private fun processAndAggregateData() {
        val raw = _rawReadings.value
        if (raw.isEmpty()) {
            _aggregatedPoints.value = emptyList()
            return
        }

        val range = _selectedRange.value
        val now = System.currentTimeMillis()
        val cutoffTime = now - range.durationMillis

        // 1. Normalize timestamps to milliseconds
        val normalized = raw.map { reading ->
            val normTs = normalizeTimestamp(reading.timestamp, now)
            reading.copy(timestamp = normTs)
        }

        // 2. Filter within selected range and sort chronologically
        val filtered = normalized.filter { it.timestamp >= cutoffTime || normalized.size <= 5 }
            .sortedBy { it.timestamp }

        if (filtered.isEmpty()) {
            _aggregatedPoints.value = emptyList()
            return
        }

        // 3. If dataset is very small (<= 5 readings), show raw without over-aggregating
        if (filtered.size <= 5) {
            _aggregatedPoints.value = filtered.map { r ->
                AggregatedHistoryPoint(
                    timestamp = r.timestamp,
                    formattedTime = formatTime(r.timestamp, range),
                    avgMoisture = r.moisture.coerceIn(0.0, 100.0),
                    avgTemperature = r.temperature,
                    avgHumidity = r.humidity.coerceIn(0.0, 100.0)
                )
            }
            return
        }

        // 4. Group into time buckets
        val bucketMs = range.bucketMinutes * 60 * 1000L
        val grouped = filtered.groupBy { reading ->
            reading.timestamp / bucketMs
        }

        // 5. Calculate average per bucket
        val aggregated = grouped.map { (_, bucketReadings) ->
            val avgMoisture = bucketReadings.map { it.moisture }.average().coerceIn(0.0, 100.0)
            val avgTemp = bucketReadings.map { it.temperature }.average()
            val avgHumidity = bucketReadings.map { it.humidity }.average().coerceIn(0.0, 100.0)
            val midTimestamp = bucketReadings.map { it.timestamp }.average().toLong()

            AggregatedHistoryPoint(
                timestamp = midTimestamp,
                formattedTime = formatTime(midTimestamp, range),
                avgMoisture = avgMoisture,
                avgTemperature = avgTemp,
                avgHumidity = avgHumidity
            )
        }.sortedBy { it.timestamp }

        _aggregatedPoints.value = aggregated
    }

    /**
     * Normalizes timestamp format. Handles Unix seconds vs Unix millis.
     */
    private fun normalizeTimestamp(ts: Long, now: Long): Long {
        if (ts <= 0L) return now
        
        // If timestamp is in seconds (e.g. 1700000000), convert to millis
        if (ts in 1_000_000_000L..9_999_999_999L) {
            return ts * 1000L
        }
        
        return ts
    }

    private fun formatTime(timestamp: Long, range: TimeRange): String {
        val date = Date(timestamp)
        val pattern = if (range == TimeRange.SEVEN_DAYS) "dd MMM" else "HH:mm"
        val sdf = SimpleDateFormat(pattern, Locale.getDefault())
        return sdf.format(date)
    }
}
