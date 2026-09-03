package com.example.plantmonitor.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.plantmonitor.data.model.TimeRange
import com.example.plantmonitor.ui.components.CustomHistoryChart
import com.example.plantmonitor.ui.theme.PumpBlue
import com.example.plantmonitor.ui.theme.WarningOrange
import com.example.plantmonitor.viewmodel.PlantDashboardViewModel
import com.example.plantmonitor.viewmodel.PlantHistoryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlantHistoryScreen(
    plantId: String,
    historyViewModel: PlantHistoryViewModel,
    dashboardViewModel: PlantDashboardViewModel,
    onNavigateBack: () -> Unit
) {
    val plant by dashboardViewModel.plant.collectAsState()

    LaunchedEffect(plantId) {
        historyViewModel.loadHistory(plantId)
    }

    val selectedRange by historyViewModel.selectedRange.collectAsState()
    val aggregatedPoints by historyViewModel.aggregatedPoints.collectAsState()
    val isLoading by historyViewModel.isLoading.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "History & Charts",
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                        )
                        if (plant?.name?.isNotBlank() == true) {
                            Text(
                                text = plant!!.name,
                                color = Color.White.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1B5E20)
                )
            )
        },
        containerColor = Color(0xFFE8F5E9)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Time Range Filter Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TimeRange.values().forEach { range ->
                    FilterChip(
                        selected = range == selectedRange,
                        onClick = { historyViewModel.selectRange(range) },
                        label = {
                            Text(
                                text = range.label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (range == selectedRange) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF1B5E20),
                            selectedLabelColor = Color.White,
                            containerColor = Color.White.copy(alpha = 0.5f),
                            labelColor = Color(0xFF1B5E20)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = range == selectedRange,
                            borderColor = Color(0xFF1B5E20),
                            selectedBorderColor = Color(0xFF1B5E20)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading && aggregatedPoints.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF1B5E20))
                }
            } else {
                // 1. Soil Moisture Chart
                CustomHistoryChart(
                    title = "Soil Moisture",
                    unit = "%",
                    points = aggregatedPoints,
                    valueSelector = { it.avgMoisture },
                    lineColor = Color(0xFF2E7D32),
                    isPercentage = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 2. Temperature Chart
                CustomHistoryChart(
                    title = "Temperature",
                    unit = "°C",
                    points = aggregatedPoints,
                    valueSelector = { it.avgTemperature },
                    lineColor = WarningOrange,
                    isPercentage = false
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 3. Humidity Chart
                CustomHistoryChart(
                    title = "Humidity",
                    unit = "%",
                    points = aggregatedPoints,
                    valueSelector = { it.avgHumidity },
                    lineColor = PumpBlue,
                    isPercentage = true
                )

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
