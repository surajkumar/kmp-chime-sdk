package com.wannaverse.chimesdk

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun InMeetingScreen(
    state: CallState,
    chatInput: String,
    viewModel: AppViewModel
) {
    var showChat by remember { mutableStateOf(false) }
    var showAudioDevices by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        // Remote video grid
        RemoteVideoGrid(tileIds = state.remoteTileIds)

        // No-video placeholder
        if (state.remoteTileIds.isEmpty()) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("👤", style = MaterialTheme.typography.displaySmall)
                }
                Text(
                    "Waiting for other participant...",
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        // Bottom column: panels stack above control bar
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            if (showChat) {
                ChatPanel(
                    messages = state.chatMessages,
                    input = chatInput,
                    onInputChange = { viewModel.updateChatInput(it) },
                    onSend = { viewModel.sendChat() },
                    onDismiss = { showChat = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.45f)
                )
            }

            if (showAudioDevices) {
                AudioDevicePicker(
                    devices = state.audioDevices,
                    onSelect = { device ->
                        viewModel.selectAudioDevice(device)
                        showAudioDevices = false
                    },
                    onDismiss = { showAudioDevices = false },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Control bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.75f))
                    .navigationBarsPadding()
                    .padding(vertical = 12.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CallControlButton(
                    label = if (state.isMuted) "Unmute" else "Mute",
                    icon = if (state.isMuted) "🔇" else "🎤",
                    isActive = !state.isMuted,
                    onClick = { viewModel.toggleMute() }
                )
                CallControlButton(
                    label = if (state.isCameraOn) "Cam Off" else "Cam On",
                    icon = if (state.isCameraOn) "📷" else "🚫",
                    isActive = state.isCameraOn,
                    onClick = { viewModel.toggleCamera() }
                )
                CallControlButton(
                    label = "Flip",
                    icon = "🔄",
                    isActive = true,
                    onClick = { viewModel.onSwitchCamera() }
                )
                CallControlButton(
                    label = "Audio",
                    icon = "🔊",
                    isActive = true,
                    onClick = { showAudioDevices = !showAudioDevices }
                )
                CallControlButton(
                    label = "Chat",
                    icon = "💬",
                    isActive = showChat,
                    onClick = { showChat = !showChat }
                )
                CallControlButton(
                    label = "Leave",
                    icon = "📵",
                    isActive = false,
                    isDestructive = true,
                    onClick = { viewModel.leaveMeeting() }
                )
            }
        }

        // Local video overlay — bottom-right, above the control bar
        if (state.isCameraOn) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 116.dp, end = 12.dp)
                    .size(width = 108.dp, height = 160.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                LocalVideoView(
                    modifier = Modifier.fillMaxSize(),
                    cameraFacing = state.cameraFacing,
                    isOnTop = true
                )
            }
        }

        // Connection status banners
        if (state.connectionStatus == ConnectionStatus.RECONNECTING ||
            state.connectionStatus == ConnectionStatus.POOR_CONNECTION
        ) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 8.dp),
                shape = RoundedCornerShape(20.dp),
                color = if (state.connectionStatus == ConnectionStatus.RECONNECTING)
                    Color(0xFFE65100) else Color(0xFFF57F17)
            ) {
                Text(
                    text = if (state.connectionStatus == ConnectionStatus.RECONNECTING)
                        "⟳  Reconnecting..." else "⚡ Poor connection",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }

        if (state.activeSpeakers.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(top = 8.dp, start = 12.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF1B5E20).copy(alpha = 0.85f)
            ) {
                Text(
                    "🎤 Speaking",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        if (state.errorMessage != null) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 48.dp)
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        state.errorMessage,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text("✕", color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }
    }
}

@Composable
fun RemoteVideoGrid(tileIds: List<Int>) {
    when (tileIds.size) {
        0 -> {}
        1 -> RemoteVideoView(modifier = Modifier.fillMaxSize(), tileId = tileIds[0], isOnTop = false)
        2 -> Column(modifier = Modifier.fillMaxSize()) {
            RemoteVideoView(modifier = Modifier.fillMaxWidth().weight(1f), tileId = tileIds[0], isOnTop = false)
            RemoteVideoView(modifier = Modifier.fillMaxWidth().weight(1f), tileId = tileIds[1], isOnTop = false)
        }
        else -> {
            val rows = (tileIds.size + 1) / 2
            Column(modifier = Modifier.fillMaxSize()) {
                repeat(rows) { row ->
                    Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        val leftIdx = row * 2
                        val rightIdx = leftIdx + 1
                        RemoteVideoView(
                            modifier = Modifier.fillMaxHeight().weight(1f),
                            tileId = tileIds[leftIdx],
                            isOnTop = false
                        )
                        if (rightIdx < tileIds.size) {
                            RemoteVideoView(
                                modifier = Modifier.fillMaxHeight().weight(1f),
                                tileId = tileIds[rightIdx],
                                isOnTop = false
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
