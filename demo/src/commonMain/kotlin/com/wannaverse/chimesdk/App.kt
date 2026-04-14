package com.wannaverse.chimesdk

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun App(
    initialMeetingInfo: MeetingInformation = MeetingInformation(),
    viewModel: AppViewModel = viewModel { AppViewModel(initialMeetingInfo) }
) {
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
