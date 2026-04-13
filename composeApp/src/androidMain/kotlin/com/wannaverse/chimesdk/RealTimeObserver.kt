package com.wannaverse.chimesdk

import com.amazonaws.services.chime.sdk.meetings.audiovideo.AttendeeInfo
import com.amazonaws.services.chime.sdk.meetings.audiovideo.SignalUpdate
import com.amazonaws.services.chime.sdk.meetings.audiovideo.VolumeUpdate
import com.amazonaws.services.chime.sdk.meetings.realtime.RealtimeObserver

class RealTimeObserver : RealtimeObserver {
    private var listener: RealTimeEventListener? = null

    fun setListener(listener: RealTimeEventListener) {
        this.listener = listener
    }

    override fun onAttendeesDropped(attendeeInfo: Array<AttendeeInfo>) {
        listener?.onAttendeesDropped(attendeeInfo.map { it.attendeeId })
    }

    override fun onAttendeesJoined(attendeeInfo: Array<AttendeeInfo>) {
        listener?.onAttendeesJoined(attendeeInfo.map { it.attendeeId })
    }

    override fun onAttendeesLeft(attendeeInfo: Array<AttendeeInfo>) {
        listener?.onAttendeesLeft(attendeeInfo.map { it.attendeeId })
    }

    override fun onAttendeesMuted(attendeeInfo: Array<AttendeeInfo>) {
        listener?.onAttendeesMuted(attendeeInfo.map { it.attendeeId })
    }

    override fun onAttendeesUnmuted(attendeeInfo: Array<AttendeeInfo>) {
        listener?.onAttendeesUnmuted(attendeeInfo.map { it.attendeeId })
    }

    override fun onSignalStrengthChanged(signalUpdates: Array<SignalUpdate>) {
        signalUpdates.forEach { update ->
            listener?.onSignalStrengthChanged(
                attendeeId = update.attendeeInfo.attendeeId,
                externalAttendeeId = update.attendeeInfo.externalUserId,
                signal = update.signalStrength.ordinal
            )
        }
    }

    override fun onVolumeChanged(volumeUpdates: Array<VolumeUpdate>) {
        volumeUpdates.forEach { update ->
            listener?.onVolumeChanged(
                attendeeId = update.attendeeInfo.attendeeId,
                externalAttendeeId = update.attendeeInfo.externalUserId,
                volume = update.volumeLevel.ordinal
            )
        }
    }
}
