package com.plantiq.plantmonitor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plantiq.plantmonitor.data.model.Resource
import com.plantiq.plantmonitor.viewmodel.PlantListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClaimDeviceScreen(
    plantListViewModel: PlantListViewModel,
    onNavigateBack: () -> Unit,
    onSuccess: () -> Unit
) {
    val plantsState by plantListViewModel.plantsState.collectAsState()
    val isSubmitting by plantListViewModel.isSubmitting.collectAsState()
    val dialogError by plantListViewModel.dialogError.collectAsState()
    val actionSuccess by plantListViewModel.actionSuccess.collectAsState()

    var selectedPlantId by remember { mutableStateOf("") }
    var selectedPlantName by remember { mutableStateOf("Select a Plant") }
    var deviceId by remember { mutableStateOf("") }
    var claimCode by remember { mutableStateOf("") }
    var isExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        plantListViewModel.loadPlants()
    }

    LaunchedEffect(actionSuccess) {
        if (actionSuccess) {
            plantListViewModel.resetActionState()
            onSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Claim ESP32 Device",
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
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "Connect your physical hardware to a plant profile.",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onBackground
                ),
                lineHeight = 26.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Error Message
            if (dialogError != null) {
                Text(
                    text = dialogError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            // 1. Plant Dropdown
            Text(
                text = "Assign to Plant",
                style = MaterialTheme.typography.labelLarge.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            ExposedDropdownMenuBox(
                expanded = isExpanded,
                onExpandedChange = { isExpanded = !isExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedPlantName,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                    )
                )

                ExposedDropdownMenu(
                    expanded = isExpanded,
                    onDismissRequest = { isExpanded = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                ) {
                    when (val state = plantsState) {
                        is Resource.Loading -> {
                            DropdownMenuItem(
                                text = { Text("Loading plants...", color = Color.Gray) },
                                onClick = {}
                            )
                        }
                        is Resource.Error -> {
                            DropdownMenuItem(
                                text = { Text("Error loading plants", color = Color.Red) },
                                onClick = { plantListViewModel.loadPlants() }
                            )
                        }
                        is Resource.Success -> {
                            val plants = state.data
                            if (plants.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No plants added yet.", color = Color.Gray) },
                                    onClick = { isExpanded = false }
                                )
                            } else {
                                plants.forEach { plant ->
                                    DropdownMenuItem(
                                        text = { Text(plant.name, color = MaterialTheme.colorScheme.onSurface) },
                                        onClick = {
                                            selectedPlantId = plant.plantId
                                            selectedPlantName = plant.name
                                            isExpanded = false
                                            plantListViewModel.clearDialogError()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 2. Device ID
            OutlinedTextField(
                value = deviceId,
                onValueChange = { deviceId = it; plantListViewModel.clearDialogError() },
                placeholder = { Text("Device ID", color = Color.Gray.copy(alpha = 0.6f)) },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f)
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 3. Claim Code
            OutlinedTextField(
                value = claimCode,
                onValueChange = { if (it.length <= 6) claimCode = it; plantListViewModel.clearDialogError() },
                placeholder = { Text("Claim Code", color = Color.Gray.copy(alpha = 0.6f)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f)
                )
            )

            Spacer(modifier = Modifier.height(60.dp))

            // Claim Button
            Button(
                onClick = { 
                    if (selectedPlantId.isEmpty()) {
                        // Manual error if no plant selected
                        // But ViewModel also handles checks.
                        plantListViewModel.createPlantAndClaimDevice(selectedPlantName, deviceId, claimCode)
                    } else {
                        // Update existing logic to support claiming for existing plant if needed
                        // or just use current logic. The ViewModel 'createPlantAndClaimDevice' 
                        // currently always creates a new plant.
                        // I will update the ViewModel to handle claiming to an existing plant.
                        plantListViewModel.claimDeviceToExistingPlant(selectedPlantId, deviceId, claimCode)
                    }
                },
                enabled = !isSubmitting,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .align(Alignment.CenterHorizontally),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text(
                        text = "CLAIM DEVICE",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                    )
                }
            }
        }
    }
}
