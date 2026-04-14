package com.wannaverse.chimesdk

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun LoadingScreen(status: ConnectionStatus, onCancel: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(56.dp))
            Text(
                text = when (status) {
                    ConnectionStatus.CONNECTING -> "Connecting..."
                    ConnectionStatus.RECONNECTING -> "Reconnecting..."
                    else -> "Please wait..."
                },
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )
            TextButton(onClick = onCancel) {
                Text("Cancel", color = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}
