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
import androidx.compose.ui.unit.sp
import com.example.plantmonitor.viewmodel.PlantDashboardViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlantSettingsScreen(
    plantId: String,
    dashboardViewModel: PlantDashboardViewModel,
    onNavigateBack: () -> Unit
) {
    LaunchedEffect(plantId) {
        dashboardViewModel.selectPlant(plantId)
    }

    val settings by dashboardViewModel.settings.collectAsState()
    
    var localAutoWatering by remember { mutableStateOf(false) }
    var localThreshold by remember { mutableStateOf(40f) }

    LaunchedEffect(settings) {
        settings?.let {
            localAutoWatering = it.autoWatering
            localThreshold = it.moistureThreshold.toFloat()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Irrigation Settings",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        bottomBar = {
            Button(
                onClick = {
                    dashboardViewModel.updateAutoWatering(localAutoWatering)
                    dashboardViewModel.updateThreshold(localThreshold.toDouble())
                    onNavigateBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(
                    text = "SAVE SETTINGS",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                )
            }
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
            Spacer(modifier = Modifier.height(24.dp))

            // 1. Automatic Watering Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Automatic Watering",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Allow ESP32 to water the plant based on threshold.",
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color.Gray)
                        )
                    }

                    Switch(
                        checked = localAutoWatering,
                        onCheckedChange = { localAutoWatering = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color.Gray.copy(alpha = 0.5f)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 2. Soil Moisture Threshold Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Soil Moisture Threshold",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Current threshold: ${localThreshold.roundToInt()} %",
                        color = Color(0xFF2E7D32),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Slider(
                        value = localThreshold,
                        onValueChange = { localThreshold = it },
                        valueRange = 0f..100f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.Gray.copy(alpha = 0.3f)
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Watering will be requested when soil moisture falls below the configured threshold. The physical decision is performed by the ESP32.",
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.Gray),
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}
