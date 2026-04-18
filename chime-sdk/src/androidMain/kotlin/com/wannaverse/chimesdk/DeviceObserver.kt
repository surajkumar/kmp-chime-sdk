package com.wannaverse.chimesdk

import com.amazonaws.services.chime.sdk.meetings.device.DeviceChangeObserver
import com.amazonaws.services.chime.sdk.meetings.device.MediaDevice
import com.amazonaws.services.chime.sdk.meetings.session.DefaultMeetingSession

class DeviceObserver(
    private val meetingSession: DefaultMeetingSession,
    private val realTimeEventListener: RealTimeEventListener
) : DeviceChangeObserver {

    private var currentSelectedDevice: MediaDevice? = null

    override fun onAudioDeviceChanged(freshAudioDeviceList: List<MediaDevice>) {
        val devices = freshAudioDeviceList.map { device ->
            AudioDevice(
                label = device.label,
                type = device.type.ordinal,
                id = device.id
            )
        }

        if (currentSelectedDevice == null && freshAudioDeviceList.isNotEmpty()) {
            selectAudioDevice(freshAudioDeviceList[0])
        } else if (currentSelectedDevice != null) {
            val stillAvailable = freshAudioDeviceList.any { it.id == currentSelectedDevice?.id }
            if (!stillAvailable && freshAudioDeviceList.isNotEmpty()) {
                selectAudioDevice(freshAudioDeviceList[0])
            }
        }

        val selected = currentSelectedDevice?.let { sel ->
            devices.firstOrNull { it.id == sel.id }
        }

        realTimeEventListener.onAudioDevicesUpdated(devices, selected)
    }

    fun selectAudioDevice(device: MediaDevice) {
        currentSelectedDevice = device
        meetingSession.audioVideo.chooseAudioDevice(device)
    }

    fun clearCurrentDevice() {
        currentSelectedDevice = null
    }
}
