import Foundation
import ComposeApp

/// Registers all native function bridges between Swift and Kotlin.
/// Call ChimeSdkSetup.configure() once during app initialization (e.g., AppDelegate / iOSApp.init()).
enum ChimeSdkSetup {

    static func configure() {
        let meeting = ChimeMeeting.shared

        // ── Provide video render views to Kotlin Compose ───────────────────────
        ChimeSdkBridge.shared.localVideoViewFactory = {
            meeting.localRenderView
        }
        ChimeSdkBridge.shared.remoteVideoViewFactory = { tileId in
            meeting.remoteVideoView(for: Int32(truncating: tileId))
        }

        // ── Meeting lifecycle ──────────────────────────────────────────────────
        ChimeSdkBridge.shared.joinMeetingNative = {
            externalMeetingId, meetingId, audioHostURL, audioFallbackURL,
            turnControlURL, signalingURL, ingestionURL,
            attendeeId, externalUserId, joinToken in

            meeting.joinMeeting(
                externalMeetingId: externalMeetingId,
                meetingId: meetingId,
                audioHostURL: audioHostURL,
                audioFallbackURL: audioFallbackURL,
                turnControlURL: turnControlURL,
                signalingURL: signalingURL,
                ingestionURL: ingestionURL,
                attendeeId: attendeeId,
                externalUserId: externalUserId,
                joinToken: joinToken
            )
        }

        ChimeSdkBridge.shared.leaveMeetingNative = {
            meeting.leaveMeeting()
        }

        // ── Video ──────────────────────────────────────────────────────────────
        ChimeSdkBridge.shared.startLocalVideoNative = {
            meeting.startLocalVideo()
        }

        ChimeSdkBridge.shared.stopLocalVideoNative = {
            meeting.stopLocalVideo()
        }

        ChimeSdkBridge.shared.switchCameraNative = {
            meeting.switchCamera()
        }

        // ── Audio ──────────────────────────────────────────────────────────────
        ChimeSdkBridge.shared.setMuteNative = { shouldMute in
            meeting.setMute(shouldMute.boolValue)
        }

        ChimeSdkBridge.shared.switchAudioDeviceNative = { deviceId in
            meeting.switchAudioDevice(deviceId: deviceId)
        }

        // ── Messaging ──────────────────────────────────────────────────────────
        ChimeSdkBridge.shared.sendRealtimeMessageNative = { topic, data, lifetimeMs in
            meeting.sendRealtimeMessage(topic: topic, data: data, lifetimeMs: Int(truncating: lifetimeMs))
        }

        // ── Topic subscriptions ────────────────────────────────────────────────
        ChimeSdkBridge.shared.subscribeTopicNative = { topic in
            meeting.subscribeTopic(topic)
        }

        ChimeSdkBridge.shared.unsubscribeTopicNative = { topic in
            meeting.unsubscribeTopic(topic)
        }
    }
}
