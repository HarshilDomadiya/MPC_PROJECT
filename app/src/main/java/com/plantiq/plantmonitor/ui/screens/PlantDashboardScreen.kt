package com.plantiq.plantmonitor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.plantiq.plantmonitor.viewmodel.PlantDashboardViewModel
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlantDashboardScreen(
    plantId: String,
    dashboardViewModel: PlantDashboardViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToSettings: (String) -> Unit,
    onNavigateToHistory: (String) -> Unit
) {
    LaunchedEffect(plantId) {
        dashboardViewModel.selectPlant(plantId)
    }

    val plant by dashboardViewModel.plant.collectAsState()
    val sensorData by dashboardViewModel.sensorData.collectAsState()
    val settings by dashboardViewModel.settings.collectAsState()
    val pumpState by dashboardViewModel.pumpState.collectAsState()
    val isOnline by dashboardViewModel.isDeviceOnline.collectAsState()
    val statusText by dashboardViewModel.statusText.collectAsState()

    var showPumpDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = plant?.name ?: "Plant",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { onNavigateToSettings(plantId) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // 1. Header Information
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = plant?.name ?: "",
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    )
                    Text(
                        text = "ID: ${plant?.plantId ?: ""}",
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.Gray)
                    )
                    Text(
                        text = "Device: ${plant?.deviceId ?: "None"}",
                        style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground)
                    )
                }

                // Status Badge (matched to image)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isOnline) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .background(if (isOnline) Color(0xFF4CAF50) else Color(0xFFF44336), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = statusText,
                            color = if (isOnline) Color(0xFF2E7D32) else Color(0xFFD32F2F),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 2. Real-time Sensors Section
            Text(
                text = "Real-time Sensors",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                contentAlignment = Alignment.Center
            ) {
                if (sensorData == null) {
                    Text(
                        text = if (isOnline) "Waiting for data..." else "Device Offline",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            color = Color.Gray.copy(alpha = 0.6f),
                            fontWeight = FontWeight.Medium
                        )
                    )
                } else {
                    val sensorFormat = "%.1f"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SensorMetric(
                            label = "Moisture",
                            value = if (sensorData?.soilConnected == true) 
                                "${String.format(Locale.US, sensorFormat, sensorData?.moisture)}%"
                                else "DISC",
                            icon = Icons.Default.WaterDrop,
                            isConnected = sensorData?.soilConnected ?: true
                        )
                        SensorMetric(
                            label = "Temp",
                            value = if (sensorData?.dhtConnected == true) 
                                "${String.format(Locale.US, sensorFormat, sensorData?.temperature)}°C"
                                else "DISC",
                            icon = Icons.Default.Thermostat,
                            isConnected = sensorData?.dhtConnected ?: true
                        )
                        SensorMetric(
                            label = "Humidity",
                            value = if (sensorData?.dhtConnected == true) 
                                "${String.format(Locale.US, sensorFormat, sensorData?.humidity)}%"
                                else "DISC",
                            icon = Icons.Default.Cloud,
                            isConnected = sensorData?.dhtConnected ?: true
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Color.Gray.copy(alpha = 0.2f))

            // 3. Automatic Irrigation Section
            Text(
                text = "Automatic Irrigation",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    StatusRow(label = "Status", value = statusText, isOnline = isOnline)
                    Spacer(modifier = Modifier.height(8.dp))
                    StatusRow(label = "Auto Watering", value = if (settings?.autoWatering == true) "Enabled" else "Disabled", isOnline = settings?.autoWatering == true)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Threshold", style = MaterialTheme.typography.bodyLarge, color = Color.Gray)
                        Text(
                            text = "${settings?.moistureThreshold?.roundToInt() ?: 40} %",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 4. Water Pump Section
            Text(
                text = "Water Pump",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Status", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(if (pumpState?.status == true) Color(0xFF4CAF50) else Color(0xFFF44336), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = if (pumpState?.status == true) "ON" else "OFF",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (pumpState?.status == true) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                                )
                            )
                        }

                        Button(
                            onClick = { showPumpDialog = true },
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text(
                                text = if (pumpState?.status == true) "STOP PUMP" else "START PUMP",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    if (!isOnline) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFFA000), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Device is offline. Command will sync when online.",
                                color = Color(0xFFE65100),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { onNavigateToHistory(plantId) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.ShowChart, contentDescription = null)
                Spacer(modifier = Modifier.width(12.dp))
                Text("VIEW HISTORY CHARTS")
            }

            Spacer(modifier = Modifier.height(40.dp))
        }

        if (showPumpDialog) {
            val isCurrentlyOn = pumpState?.status == true
            AlertDialog(
                onDismissRequest = { showPumpDialog = false },
                title = {
                    Text(
                        text = if (isCurrentlyOn) "Stop Pump?" else "Start Pump?",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                text = {
                    Text(
                        text = if (isCurrentlyOn) "Are you sure you want to stop the water pump?" else "Are you sure you want to start the water pump?",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.DarkGray
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            dashboardViewModel.togglePump(!isCurrentlyOn)
                            showPumpDialog = false
                        }
                    ) {
                        Text(
                            text = if (isCurrentlyOn) "STOP" else "START",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPumpDialog = false }) {
                        Text(
                            text = "CANCEL",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.Gray
                        )
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(28.dp)
            )
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = {
                    Text(
                        text = "Delete Plant?",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                text = {
                    Text(
                        text = "This will permanently remove this plant and its device connection. Are you sure?",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.DarkGray
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            dashboardViewModel.deletePlant {
                                onNavigateBack()
                            }
                            showDeleteDialog = false
                        }
                    ) {
                        Text(
                            text = "DELETE",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFD32F2F)
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text(
                            text = "CANCEL",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.Gray
                        )
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(28.dp)
            )
        }
    }
}

@Composable
fun SensorMetric(
    label: String,
    value: String,
    icon: ImageVector,
    isConnected: Boolean = true
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = if (isConnected) Color(0xFF2E7D32) else Color(0xFFD32F2F)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = if (isConnected) Color.Black else Color(0xFFD32F2F)
            )
        )
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
    }
}

@Composable
fun StatusRow(label: String, value: String, isOnline: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = Color.Gray)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(if (isOnline) Color(0xFF4CAF50) else Color(0xFFF44336), CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = value,
                color = if (isOnline) Color(0xFF2E7D32) else Color(0xFFD32F2F),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}
