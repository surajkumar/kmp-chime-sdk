package com.wannaverse.chimesdk

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun App(viewModel: AppViewModel = viewModel { AppViewModel() }) {
    val state by viewModel.callState.collectAsStateWithLifecycle()
    val info by viewModel.meetingInfo.collectAsStateWithLifecycle()
    val chatInput by viewModel.chatInput.collectAsStateWithLifecycle()

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when {
                state.isJoined -> InMeetingScreen(
                    state = state,
                    chatInput = chatInput,
                    viewModel = viewModel
                )
                state.isLoading -> LoadingScreen(
                    status = state.connectionStatus,
                    onCancel = { viewModel.leaveMeeting() }
                )
                else -> JoinScreen(
                    info = info,
                    state = state,
                    viewModel = viewModel
                )
            }
        }
    }
}

// ─── Join Screen ─────────────────────────────────────────────────────────────

@Composable
private fun JoinScreen(
    info: MeetingInformation,
    state: CallState,
    viewModel: AppViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .safeContentPadding()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "ChimeSDK",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Kotlin Multiplatform Demo",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(8.dp))

        // Meeting credentials section
        SectionLabel("Meeting Credentials")

        FormField(
            value = info.meetingId,
            onValueChange = { viewModel.updateMeetingInfo(info.copy(meetingId = it)) },
            label = "Meeting ID *",
            placeholder = "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
        )
        FormField(
            value = info.externalMeetingId,
            onValueChange = { viewModel.updateMeetingInfo(info.copy(externalMeetingId = it)) },
            label = "External Meeting ID",
            placeholder = "your-external-id (defaults to Meeting ID)"
        )
        FormField(
            value = info.attendeeId,
            onValueChange = { viewModel.updateMeetingInfo(info.copy(attendeeId = it)) },
            label = "Attendee ID *",
            placeholder = "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
        )
        FormField(
            value = info.externalUserId,
            onValueChange = { viewModel.updateMeetingInfo(info.copy(externalUserId = it)) },
            label = "External User ID",
            placeholder = "your-user-id (defaults to Attendee ID)"
        )
        FormField(
            value = info.joinToken,
            onValueChange = { viewModel.updateMeetingInfo(info.copy(joinToken = it)) },
            label = "Join Token *",
            placeholder = "QUJ..."
        )

        Spacer(Modifier.height(4.dp))
        SectionLabel("Connection URLs")

        FormField(
            value = info.audioHostURL,
            onValueChange = { viewModel.updateMeetingInfo(info.copy(audioHostURL = it)) },
            label = "Audio Host URL *",
            placeholder = "https://..."
        )
        FormField(
            value = info.audioFallbackURL,
            onValueChange = { viewModel.updateMeetingInfo(info.copy(audioFallbackURL = it)) },
            label = "Audio Fallback URL *",
            placeholder = "wss://..."
        )
        FormField(
            value = info.turnControlURL,
            onValueChange = { viewModel.updateMeetingInfo(info.copy(turnControlURL = it)) },
            label = "Turn Control URL *",
            placeholder = "https://..."
        )
        FormField(
            value = info.signalingURL,
            onValueChange = { viewModel.updateMeetingInfo(info.copy(signalingURL = it)) },
            label = "Signaling URL *",
            placeholder = "wss://..."
        )
        FormField(
            value = info.ingestionURL,
            onValueChange = { viewModel.updateMeetingInfo(info.copy(ingestionURL = it)) },
            label = "Ingestion URL",
            placeholder = "https://... (optional)"
        )

        Spacer(Modifier.height(4.dp))
        SectionLabel("Options")

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Join Muted", style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = state.startMuted,
                onCheckedChange = { viewModel.setStartMuted(it) }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Camera", style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.cameraFacing == CameraFacing.FRONT,
                    onClick = { viewModel.setCameraFacing(CameraFacing.FRONT) },
                    label = { Text("Front") }
                )
                FilterChip(
                    selected = state.cameraFacing == CameraFacing.BACK,
                    onClick = { viewModel.setCameraFacing(CameraFacing.BACK) },
                    label = { Text("Back") }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = { viewModel.joinMeeting() },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Join Meeting", style = MaterialTheme.typography.titleMedium)
        }

        if (state.errorMessage != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = state.errorMessage,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text("Dismiss")
                    }
                }
            }
        }
    }
}

// ─── Loading Screen ───────────────────────────────────────────────────────────

@Composable
private fun LoadingScreen(status: ConnectionStatus, onCancel: () -> Unit) {
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

// ─── Remote Video Grid ────────────────────────────────────────────────────────

@Composable
private fun RemoteVideoGrid(tileIds: List<Int>) {
    when (tileIds.size) {
        0 -> {} // placeholder shown by caller
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

// ─── In-Meeting Screen ────────────────────────────────────────────────────────

@Composable
private fun InMeetingScreen(
    state: CallState,
    chatInput: String,
    viewModel: AppViewModel
) {
    var showChat by remember { mutableStateOf(false) }
    var showAudioDevices by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        // ── Remote video grid ─────────────────────────────────────────────────
        RemoteVideoGrid(tileIds = state.remoteTileIds)

        // ── No-video placeholder ──────────────────────────────────────────────
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

        // ── Bottom column: panels stack above control bar ─────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            // Chat panel — sits directly above the controls
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

            // Audio device picker — sits directly above the controls
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

            // Control bar — always at the very bottom
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

        // ── Local video overlay — bottom-right, above the control bar ─────────
        // Fix: padding must come BEFORE size so it acts as margin, not content clipping
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

        // ── Top overlays (status bar insets only) ─────────────────────────────
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

// ─── Chat Panel ───────────────────────────────────────────────────────────────

@Composable
private fun ChatPanel(
    messages: List<TextMessage>,
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E).copy(alpha = 0.95f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Chat", color = Color.White, style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = onDismiss) {
                    Text("✕", color = Color.White.copy(alpha = 0.6f))
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (messages.isEmpty()) {
                    Text(
                        "No messages yet",
                        color = Color.White.copy(alpha = 0.4f),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
                messages.forEach { msg ->
                    Column {
                        Text(
                            text = msg.senderId.take(8) + "...",
                            color = Color.White.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            text = msg.content,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    placeholder = { Text("Message...", color = Color.White.copy(alpha = 0.4f)) },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White.copy(alpha = 0.5f),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                    ),
                    singleLine = true
                )
                Button(
                    onClick = onSend,
                    enabled = input.isNotBlank()
                ) {
                    Text("Send")
                }
            }
        }
    }
}

// ─── Audio Device Picker ──────────────────────────────────────────────────────

@Composable
private fun AudioDevicePicker(
    devices: List<AudioDevice>,
    onSelect: (AudioDevice) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E).copy(alpha = 0.95f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Audio Output", color = Color.White, style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = onDismiss) {
                    Text("Done", color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(8.dp))
            devices.forEach { device ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(device.label, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    if (device.isSelected) {
                        Text("✓", color = MaterialTheme.colorScheme.primary)
                    } else {
                        TextButton(onClick = { onSelect(device) }) {
                            Text("Select", color = Color.White.copy(alpha = 0.6f))
                        }
                    }
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            }
        }
    }
}

// ─── Reusable Components ──────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = ""
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder, style = MaterialTheme.typography.bodySmall) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(10.dp)
    )
}

@Composable
private fun CallControlButton(
    label: String,
    icon: String,
    isActive: Boolean,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        FilledTonalButton(
            onClick = onClick,
            modifier = Modifier.size(52.dp),
            shape = CircleShape,
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = when {
                    isDestructive -> Color(0xFFE53935)
                    isActive -> Color.White.copy(alpha = 0.15f)
                    else -> Color.White.copy(alpha = 0.08f)
                }
            )
        ) {
            Text(icon, style = MaterialTheme.typography.titleMedium)
        }
        Text(
            label,
            color = Color.White.copy(alpha = 0.75f),
            style = MaterialTheme.typography.labelSmall
        )
    }
}
