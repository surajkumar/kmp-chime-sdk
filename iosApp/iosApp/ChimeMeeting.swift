import Foundation
import AmazonChimeSDK
import AVFoundation
import ComposeApp

/// Full Amazon Chime SDK implementation for iOS.
/// Registered with the Kotlin bridge in ChimeSdkSetup.configure().
class ChimeMeeting: NSObject {

    static let shared = ChimeMeeting()

    private var meetingSession: DefaultMeetingSession?
    private var videoTileManager: VideoTileManager?
    private var muted = false
    private var isFrontCamera = true
    private var subscribedTopics: Set<String> = []

    // Pre-created local render view; remote views are created per-tile in VideoTileManager
    let localRenderView = DefaultVideoRenderView()

    private override init() {
        super.init()
        localRenderView.mirror = true
    }

    func remoteVideoView(for tileId: Int32) -> DefaultVideoRenderView {
        return videoTileManager?.remoteView(for: Int(tileId)) ?? DefaultVideoRenderView()
    }

    // MARK: – Join

    func joinMeeting(
        externalMeetingId: String,
        meetingId: String,
        audioHostURL: String,
        audioFallbackURL: String,
        turnControlURL: String,
        signalingURL: String,
        ingestionURL: String,
        attendeeId: String,
        externalUserId: String,
        joinToken: String
    ) {
        let logger = ConsoleLogger(name: "ChimeMeeting", level: .INFO)

        let credentials = MeetingSessionCredentials(
            attendeeId: attendeeId,
            externalUserId: externalUserId,
            joinToken: joinToken
        )

        let urls = MeetingSessionURLs(
            audioFallbackUrl: audioFallbackURL,
            audioHostUrl: audioHostURL,
            turnControlUrl: turnControlURL,
            signalingUrl: signalingURL,
            urlRewriter: { $0 },
            ingestionUrl: ingestionURL
        )

        let configuration = MeetingSessionConfiguration(
            meetingId: meetingId,
            externalMeetingId: externalMeetingId,
            credentials: credentials,
            urls: urls,
            urlRewriter: { $0 },
            primaryMeetingId: externalMeetingId
        )

        meetingSession = DefaultMeetingSession(
            configuration: configuration,
            logger: logger
        )

        let tileManager = VideoTileManager(
            localView: localRenderView,
            audioVideo: meetingSession!.audioVideo
        )
        videoTileManager = tileManager

        meetingSession?.audioVideo.addVideoTileObserver(observer: tileManager)
        meetingSession?.audioVideo.addAudioVideoObserver(observer: self)
        meetingSession?.audioVideo.addRealtimeObserver(observer: self)
        meetingSession?.audioVideo.addDeviceChangeObserver(observer: self)
        meetingSession?.audioVideo.addActiveSpeakerObserver(
            policy: DefaultActiveSpeakerPolicy(),
            observer: self
        )
        // Topics are registered via subscribeTopic() after joinMeeting()

        // Notify Kotlin of local attendee ID
        ChimeSdkBridge.shared.eventDelegate?.onLocalAttendeeIdAvailable(attendeeId: attendeeId)

        // Select first available audio device
        configureAudioSession()

        // Select default audio output
        if let devices = meetingSession?.audioVideo.listAudioDevices(), !devices.isEmpty {
            meetingSession?.audioVideo.chooseAudioDevice(mediaDevice: devices[0])
        }

        // Request microphone then camera permission, then start
        AVAudioApplication.requestRecordPermission { [weak self] _ in
            AVCaptureDevice.requestAccess(for: .video) { [weak self] _ in
                DispatchQueue.main.async {
                    self?.startAudioAndVideo()
                }
            }
        }
    }

    private func configureAudioSession() {
        do {
            try AVAudioSession.sharedInstance().setCategory(
                .playAndRecord,
                mode: .videoChat,
                options: [.allowBluetoothHFP, .allowBluetoothA2DP]
            )
            try AVAudioSession.sharedInstance().setActive(true)
        } catch {
            print("ChimeMeeting: audio session setup failed: \(error)")
        }
    }

    private func startAudioAndVideo() {
        guard let session = meetingSession else { return }
        do {
            try session.audioVideo.start()
        } catch {
            ChimeSdkBridge.shared.eventDelegate?.onSessionError(
                message: "Failed to start audio: \(error.localizedDescription)",
                isRecoverable: false
            )
            return
        }
        session.audioVideo.startRemoteVideo()

        do {
            try session.audioVideo.startLocalVideo()
        } catch {
            print("ChimeMeeting: local video start failed: \(error)")
        }
    }

    // MARK: – Leave

    func leaveMeeting() {
        guard let session = meetingSession else { return }
        session.audioVideo.stopLocalVideo()
        session.audioVideo.stopRemoteVideo()
        session.audioVideo.stop()

        session.audioVideo.removeVideoTileObserver(observer: videoTileManager!)
        session.audioVideo.removeAudioVideoObserver(observer: self)
        session.audioVideo.removeRealtimeObserver(observer: self)
        session.audioVideo.removeDeviceChangeObserver(observer: self)
        session.audioVideo.removeActiveSpeakerObserver(observer: self)
        subscribedTopics.forEach {
            session.audioVideo.removeRealtimeDataMessageObserverFromTopic(topic: $0)
        }
        subscribedTopics.removeAll()

        meetingSession = nil
        videoTileManager = nil
        muted = false
    }

    // MARK: – Controls

    func startLocalVideo() {
        do {
            try meetingSession?.audioVideo.startLocalVideo()
        } catch {
            print("ChimeMeeting: startLocalVideo failed: \(error)")
        }
    }

    func stopLocalVideo() {
        meetingSession?.audioVideo.stopLocalVideo()
    }

    func setMute(_ shouldMute: Bool) {
        if shouldMute {
            meetingSession?.audioVideo.realtimeLocalMute()
        } else {
            meetingSession?.audioVideo.realtimeLocalUnmute()
        }
        muted = shouldMute
    }

    func switchCamera() {
        guard let session = meetingSession else { return }
        let devices = MediaDevice.listVideoDevices()
        let desiredType: MediaDeviceType = isFrontCamera ? .videoBackCamera : .videoFrontCamera
        if let device = devices.first(where: { $0.type == desiredType }) {
            isFrontCamera = !isFrontCamera
            session.audioVideo.switchCamera()
        }
    }

    func switchAudioDevice(deviceId: String?) {
        guard let id = deviceId,
              let devices = meetingSession?.audioVideo.listAudioDevices() else { return }
        if let device = devices.first(where: { $0.label == id || ($0.port?.uid == id) }) {
            meetingSession?.audioVideo.chooseAudioDevice(mediaDevice: device)
        }
    }

    func sendRealtimeMessage(topic: String, data: String, lifetimeMs: Int) {
        try? meetingSession?.audioVideo.realtimeSendDataMessage(
            topic: topic,
            data: data,
            lifetimeMs: Int32(lifetimeMs)
        )
    }

    func subscribeTopic(_ topic: String) {
        guard let session = meetingSession else { return }
        subscribedTopics.insert(topic)
        session.audioVideo.addRealtimeDataMessageObserver(topic: topic, observer: self)
    }

    func unsubscribeTopic(_ topic: String) {
        guard let session = meetingSession else { return }
        subscribedTopics.remove(topic)
        session.audioVideo.removeRealtimeDataMessageObserverFromTopic(topic: topic)
    }
}

// MARK: – AudioVideoObserver

extension ChimeMeeting: AudioVideoObserver {
    func audioSessionDidStartConnecting(reconnecting: Bool) {
        let status = reconnecting ? "RECONNECTING" : "CONNECTING"
        ChimeSdkBridge.shared.eventDelegate?.onConnectionStatusChanged(status: status)
    }

    func audioSessionDidStart(reconnecting: Bool) {
        ChimeSdkBridge.shared.eventDelegate?.onConnectionStatusChanged(status: "CONNECTED")
    }

    func audioSessionDidDrop() {
        ChimeSdkBridge.shared.eventDelegate?.onConnectionStatusChanged(status: "RECONNECTING")
        ChimeSdkBridge.shared.eventDelegate?.onSessionError(
            message: "Audio dropped, reconnecting...",
            isRecoverable: true
        )
    }

    func audioSessionDidStopWithStatus(sessionStatus: MeetingSessionStatus) {
        ChimeSdkBridge.shared.eventDelegate?.onConnectionStatusChanged(status: "DISCONNECTED")
        let message: String
        switch sessionStatus.statusCode {
        case .ok: message = "Meeting ended"
        case .audioServerHungup: message = "Server disconnected"
        case .audioJoinedFromAnotherDevice: message = "Joined from another device"
        default: message = "Session ended: \(sessionStatus.statusCode)"
        }
        ChimeSdkBridge.shared.eventDelegate?.onSessionError(message: message, isRecoverable: false)
    }

    func audioSessionDidCancelReconnect() {
        ChimeSdkBridge.shared.eventDelegate?.onConnectionStatusChanged(status: "ERROR")
        ChimeSdkBridge.shared.eventDelegate?.onSessionError(
            message: "Failed to reconnect",
            isRecoverable: false
        )
    }

    func connectionDidBecomePoor() {
        ChimeSdkBridge.shared.eventDelegate?.onConnectionStatusChanged(status: "POOR_CONNECTION")
    }

    func connectionDidRecover() {
        ChimeSdkBridge.shared.eventDelegate?.onConnectionStatusChanged(status: "CONNECTED")
    }

    func videoSessionDidStartConnecting() {}

    func videoSessionDidStartWithStatus(sessionStatus: MeetingSessionStatus) {
        if sessionStatus.statusCode == .videoAtCapacityViewOnly {
            ChimeSdkBridge.shared.eventDelegate?.onSessionError(
                message: "Video at capacity. View only.",
                isRecoverable: false
            )
        }
    }

    func videoSessionDidStopWithStatus(sessionStatus: MeetingSessionStatus) {}

    func remoteVideoSourcesDidBecomeAvailable(sources: [RemoteVideoSource]) {
        ChimeSdkBridge.shared.eventDelegate?.onRemoteVideoAvailable(
            isAvailable: true,
            sourceCount: Int32(sources.count)
        )
    }

    func remoteVideoSourcesDidBecomeUnavailable(sources: [RemoteVideoSource]) {
        ChimeSdkBridge.shared.eventDelegate?.onRemoteVideoAvailable(
            isAvailable: false,
            sourceCount: Int32(sources.count)
        )
    }

    func cameraSendAvailabilityDidChange(available: Bool) {
        ChimeSdkBridge.shared.eventDelegate?.onCameraSendAvailable(available: available)
    }
}

// MARK: – RealtimeObserver

extension ChimeMeeting: RealtimeObserver {
    func attendeesDidJoin(attendeeInfo: [AttendeeInfo]) {
        ChimeSdkBridge.shared.eventDelegate?.onAttendeesJoined(
            attendeeIds: attendeeInfo.map { $0.attendeeId }
        )
    }

    func attendeesDidLeave(attendeeInfo: [AttendeeInfo]) {
        ChimeSdkBridge.shared.eventDelegate?.onAttendeesLeft(
            attendeeIds: attendeeInfo.map { $0.attendeeId }
        )
    }

    func attendeesDidDrop(attendeeInfo: [AttendeeInfo]) {
        ChimeSdkBridge.shared.eventDelegate?.onAttendeesDropped(
            attendeeIds: attendeeInfo.map { $0.attendeeId }
        )
    }

    func attendeesDidMute(attendeeInfo: [AttendeeInfo]) {
        ChimeSdkBridge.shared.eventDelegate?.onAttendeesMuted(
            attendeeIds: attendeeInfo.map { $0.attendeeId }
        )
    }

    func attendeesDidUnmute(attendeeInfo: [AttendeeInfo]) {
        ChimeSdkBridge.shared.eventDelegate?.onAttendeesUnmuted(
            attendeeIds: attendeeInfo.map { $0.attendeeId }
        )
    }

    func signalStrengthDidChange(signalUpdates: [SignalUpdate]) {
        for update in signalUpdates {
            ChimeSdkBridge.shared.eventDelegate?.onSignalStrengthChanged(
                attendeeId: update.attendeeInfo.attendeeId,
                externalAttendeeId: update.attendeeInfo.externalUserId,
                signal: Int32(update.signalStrength.rawValue)
            )
        }
    }

    func volumeDidChange(volumeUpdates: [VolumeUpdate]) {
        for update in volumeUpdates {
            ChimeSdkBridge.shared.eventDelegate?.onVolumeChanged(
                attendeeId: update.attendeeInfo.attendeeId,
                externalAttendeeId: update.attendeeInfo.externalUserId,
                volume: Int32(update.volumeLevel.rawValue)
            )
        }
    }
}

// MARK: – DeviceChangeObserver

extension ChimeMeeting: DeviceChangeObserver {
    func audioDeviceDidChange(freshAudioDeviceList: [MediaDevice]) {
        let labels = freshAudioDeviceList.map { $0.label }
        let selectedLabel = freshAudioDeviceList.first?.label
        // Simplified: notify Kotlin via a system message (audio devices list)
        // For full device management, expose a richer callback in ChimeIOSDelegate
    }
}

// MARK: – ActiveSpeakerObserver

extension ChimeMeeting: ActiveSpeakerObserver {
    var observerId: String { return "ChimeMeeting" }

    func activeSpeakerDidDetect(attendeeInfo: [AttendeeInfo]) {
        ChimeSdkBridge.shared.eventDelegate?.onActiveSpeakersChanged(
            speakerIds: attendeeInfo.map { $0.externalUserId }
        )
    }

    func activeSpeakerScoreDidChange(scores: [AttendeeInfo: Double]) {}
}

// MARK: – DataMessageObserver

extension ChimeMeeting: DataMessageObserver {
    func dataMessageDidReceived(dataMessage: DataMessage) {
        ChimeSdkBridge.shared.eventDelegate?.onDataMessageReceived(
            topic: dataMessage.topic,
            senderId: dataMessage.senderAttendeeId,
            content: dataMessage.text() ?? "",
            timestamp: dataMessage.timestampMs
        )
    }
}
