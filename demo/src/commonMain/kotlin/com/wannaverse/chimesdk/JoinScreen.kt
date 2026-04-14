package com.wannaverse.chimesdk

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun JoinScreen(
    info: MeetingInformation,
    state: CallState,
    viewModel: AppViewModel
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
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
        }

        item { SectionLabel("Meeting Credentials") }

        item {
            FormField(
                value = info.meetingId,
                onValueChange = { viewModel.updateMeetingInfo(info.copy(meetingId = it)) },
                label = "Meeting ID *",
                placeholder = "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
            )
        }
        item {
            FormField(
                value = info.externalMeetingId,
                onValueChange = { viewModel.updateMeetingInfo(info.copy(externalMeetingId = it)) },
                label = "External Meeting ID",
                placeholder = "your-external-id (defaults to Meeting ID)"
            )
        }
        item {
            FormField(
                value = info.attendeeId,
                onValueChange = { viewModel.updateMeetingInfo(info.copy(attendeeId = it)) },
                label = "Attendee ID *",
                placeholder = "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
            )
        }
        item {
            FormField(
                value = info.externalUserId,
                onValueChange = { viewModel.updateMeetingInfo(info.copy(externalUserId = it)) },
                label = "External User ID",
                placeholder = "your-user-id (defaults to Attendee ID)"
            )
        }
        item {
            FormField(
                value = info.joinToken,
                onValueChange = { viewModel.updateMeetingInfo(info.copy(joinToken = it)) },
                label = "Join Token *",
                placeholder = "QUJ..."
            )
        }

        item {
            Spacer(Modifier.height(4.dp))
            SectionLabel("Connection URLs")
        }

        item {
            FormField(
                value = info.audioHostURL,
                onValueChange = { viewModel.updateMeetingInfo(info.copy(audioHostURL = it)) },
                label = "Audio Host URL *",
                placeholder = "https://..."
            )
        }
        item {
            FormField(
                value = info.audioFallbackURL,
                onValueChange = { viewModel.updateMeetingInfo(info.copy(audioFallbackURL = it)) },
                label = "Audio Fallback URL *",
                placeholder = "wss://..."
            )
        }
        item {
            FormField(
                value = info.turnControlURL,
                onValueChange = { viewModel.updateMeetingInfo(info.copy(turnControlURL = it)) },
                label = "Turn Control URL *",
                placeholder = "https://..."
            )
        }
        item {
            FormField(
                value = info.signalingURL,
                onValueChange = { viewModel.updateMeetingInfo(info.copy(signalingURL = it)) },
                label = "Signaling URL *",
                placeholder = "wss://..."
            )
        }
        item {
            FormField(
                value = info.ingestionURL,
                onValueChange = { viewModel.updateMeetingInfo(info.copy(ingestionURL = it)) },
                label = "Ingestion URL",
                placeholder = "https://... (optional)"
            )
        }

        item {
            Spacer(Modifier.height(4.dp))
            SectionLabel("Options")
        }

        item {
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
        }

        item {
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
        }

        item {
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
        }

        if (state.errorMessage != null) {
            item {
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
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}

@Composable
fun FormField(
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
