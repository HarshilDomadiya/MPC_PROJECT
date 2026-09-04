package com.example.plantmonitor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.plantmonitor.ui.theme.OfflineGray
import com.example.plantmonitor.ui.theme.SuccessGreen

@Composable
fun StatusBadge(
    isOnline: Boolean,
    statusText: String,
    modifier: Modifier = Modifier
) {
    val badgeBg = when {
        statusText.contains("No device", ignoreCase = true) -> OfflineGray.copy(alpha = 0.15f)
        isOnline -> SuccessGreen.copy(alpha = 0.15f)
        else -> OfflineGray.copy(alpha = 0.15f)
    }

    val dotColor = when {
        statusText.contains("No device", ignoreCase = true) -> OfflineGray
        isOnline -> SuccessGreen
        else -> OfflineGray
    }

    val textColor = when {
        statusText.contains("No device", ignoreCase = true) -> OfflineGray
        isOnline -> SuccessGreen
        else -> OfflineGray
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(badgeBg)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = statusText.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        )
    }
}
