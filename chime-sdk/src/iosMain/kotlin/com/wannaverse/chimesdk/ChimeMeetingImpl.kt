@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.wannaverse.chimesdk

import cocoapods.AmazonChimeSDK.*
import platform.AVFoundation.*
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIView

internal object chimeMeeting : NSObject(),
    AudioVideoObserver,
    RealtimeObserver,
    VideoTileObserver,
    DeviceChangeObserver,
    ActiveSpeakerObserver,
    DataMessageObserver {

    private var meetingSession: DefaultMeetingSession? = null
    private var isFrontCamera = true
    private val subscribedTopics: MutableSet<String> = mutableSetOf()

    private var localTileId: Long? = null
    private val remoteTiles: MutableMap<Long, DefaultVideoRenderView> = mutableMapOf()
    private val attendeeTileMap: MutableMap<String, Long> = mutableMapOf()

    val localRenderView: DefaultVideoRenderView = DefaultVideoRenderView().also { it.mirror = true }
    var onConnectionStatusChanged: ((ConnectionStatus) -> Unit)? = null
    var onActiveSpeakersChanged: ((Set<String>) -> Unit)? = null
    var onLocalVideoTileAdded: ((Int?) -> Unit)? = null
    var onRemoteVideoAvailable: ((Boolean, Int) -> Unit)? = null
    var onCameraSendAvailable: ((Boolean) -> Unit)? = null
    var onSessionError: ((String, Boolean) -> Unit)? = null
    var onVideoNeedsRestart: (() -> Unit)? = null
    var onLocalVideoTileRemoved: (() -> Unit)? = null
    var onRemoteTileAdded: ((Int) -> Unit)? = null
    var onRemoteTileRemoved: ((Int) -> Unit)? = null
    var onLocalAttendeeIdAvailable: ((String) -> Unit)? = null
    var realTimeListener: RealTimeEventListener? = null
    val topicListeners: MutableMap<String, (TextMessage) -> Unit> = mutableMapOf()

    override val observerId: String get() = "KotlinChimeMeeting"

    fun getRemoteView(tileId: Int): UIView? = remoteTiles[tileId.toLong()]

    fun join(
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
        val logger = ConsoleLogger(name = "ChimeMeetingImpl", level = LogLevel.INFO)

        val credentials = MeetingSessionCredentials(
            attendeeId = attendeeId,
            externalUserId = externalUserId,
            joinToken = joinToken
        )

        val urls = MeetingSessionURLs(
            audioFallbackUrl = audioFallbackURL,
            audioHostUrl = audioHostURL,
            turnControlUrl = turnControlURL,
            signalingUrl = signalingURL,
            urlRewriter = { it },
            ingestionUrl = ingestionURL
        )

        val configuration = MeetingSessionConfiguration(
            meetingId = meetingId,
            externalMeetingId = externalMeetingId,
            credentials = credentials,
            urls = urls,
            urlRewriter = { it },
            primaryMeetingId = externalMeetingId
        )

        val session = DefaultMeetingSession(configuration = configuration, logger = logger)
        meetingSession = session

        session.audioVideo.addVideoTileObserver(observer = this)
        session.audioVideo.addAudioVideoObserver(observer = this)
        session.audioVideo.addRealtimeObserver(observer = this)
        session.audioVideo.addDeviceChangeObserver(observer = this)
        session.audioVideo.addActiveSpeakerObserver(
            policy = DefaultActiveSpeakerPolicy(),
            observer = this
        )

        onLocalAttendeeIdAvailable?.invoke(attendeeId)
        configureAudioSession()

        session.audioVideo.listAudioDevices().firstOrNull()?.let {
            session.audioVideo.chooseAudioDevice(mediaDevice = it)
        }

        AVAudioSession.sharedInstance().requestRecordPermission { _ ->
            AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { _ ->
                NSOperationQueue.mainQueue.addOperationWithBlock {
                    startAudioAndVideo()
                }
            }
        }
    }

    private fun configureAudioSession() {
        val s = AVAudioSession.sharedInstance()
        s.setCategory(
            AVAudioSessionCategoryPlayAndRecord,
            mode = AVAudioSessionModeVideoChat,
            options = AVAudioSessionCategoryOptionAllowBluetoothHFP or
                    AVAudioSessionCategoryOptionAllowBluetoothA2DP,
            error = null
        )
        s.setActive(true, error = null)
    }

    private fun startAudioAndVideo() {
        val session = meetingSession ?: return
        try {
            session.audioVideo.start()
        } catch (e: Throwable) {
            onSessionError?.invoke("Failed to start audio: ${e.message}", false)
            return
        }
        session.audioVideo.startRemoteVideo()
        try {
            session.audioVideo.startLocalVideo()
        } catch (e: Throwable) {
            println("ChimeMeetingImpl: local video start failed: ${e.message}")
        }
    }

    fun leave() {
        val session = meetingSession ?: return
        session.audioVideo.stopLocalVideo()
        session.audioVideo.stopRemoteVideo()
        session.audioVideo.stop()
        session.audioVideo.removeVideoTileObserver(observer = this)
        session.audioVideo.removeAudioVideoObserver(observer = this)
        session.audioVideo.removeRealtimeObserver(observer = this)
        session.audioVideo.removeDeviceChangeObserver(observer = this)
        session.audioVideo.removeActiveSpeakerObserver(observer = this)
        subscribedTopics.forEach {
            session.audioVideo.removeRealtimeDataMessageObserverFromTopic(topic = it)
        }
        subscribedTopics.clear()
        meetingSession = null
        localTileId = null
        remoteTiles.clear()
        attendeeTileMap.clear()
        isFrontCamera = true
        clearCallbacks()
    }

    private fun clearCallbacks() {
        onConnectionStatusChanged = null
        onActiveSpeakersChanged = null
        onLocalVideoTileAdded = null
        onRemoteVideoAvailable = null
        onCameraSendAvailable = null
        onSessionError = null
        onVideoNeedsRestart = null
        onLocalVideoTileRemoved = null
        onRemoteTileAdded = null
        onRemoteTileRemoved = null
        onLocalAttendeeIdAvailable = null
        realTimeListener = null
        topicListeners.clear()
    }

    fun startLocalVideo() {
        try {
            meetingSession?.audioVideo?.startLocalVideo()
        } catch (e: Throwable) {
            println("ChimeMeetingImpl: startLocalVideo failed: ${e.message}")
        }
    }

    fun stopLocalVideo() {
        meetingSession?.audioVideo?.stopLocalVideo()
    }

    fun switchCamera() {
        val session = meetingSession ?: return
        val desired = if (isFrontCamera) MediaDeviceType.videoBackCamera else MediaDeviceType.videoFrontCamera
        if (MediaDevice.listVideoDevices().any { it.type == desired }) {
            isFrontCamera = !isFrontCamera
            session.audioVideo.switchCamera()
        }
    }

    fun setMute(shouldMute: Boolean) {
        if (shouldMute) meetingSession?.audioVideo?.realtimeLocalMute()
        else meetingSession?.audioVideo?.realtimeLocalUnmute()
    }

    fun switchAudioDevice(deviceId: String?) {
        val id = deviceId ?: return
        meetingSession?.audioVideo
            ?.listAudioDevices()
            ?.firstOrNull { it.label == id }
            ?.let { meetingSession?.audioVideo?.chooseAudioDevice(mediaDevice = it) }
    }

    fun sendRealtimeMessage(topic: String, data: String, lifetimeMs: Long) {
        try {
            meetingSession?.audioVideo?.realtimeSendDataMessage(
                topic = topic,
                data = data,
                lifetimeMs = lifetimeMs.toInt()
            )
        } catch (e: Throwable) {
            println("ChimeMeetingImpl: sendRealtimeMessage failed: ${e.message}")
        }
    }

    fun subscribeTopic(topic: String) {
        val session = meetingSession ?: return
        subscribedTopics.add(topic)
        session.audioVideo.addRealtimeDataMessageObserver(topic = topic, observer = this)
    }

    fun unsubscribeTopic(topic: String) {
        val session = meetingSession ?: return
        subscribedTopics.remove(topic)
        session.audioVideo.removeRealtimeDataMessageObserverFromTopic(topic = topic)
    }

    override fun audioSessionDidStartConnecting(reconnecting: Boolean) {
        onConnectionStatusChanged?.invoke(
            if (reconnecting) ConnectionStatus.RECONNECTING else ConnectionStatus.CONNECTING
        )
    }

    override fun audioSessionDidStart(reconnecting: Boolean) {
        onConnectionStatusChanged?.invoke(ConnectionStatus.CONNECTED)
    }

    override fun audioSessionDidDrop() {
        onConnectionStatusChanged?.invoke(ConnectionStatus.RECONNECTING)
        onSessionError?.invoke("Audio dropped, reconnecting...", true)
    }

    override fun audioSessionDidStopWithStatus(sessionStatus: MeetingSessionStatus) {
        onConnectionStatusChanged?.invoke(ConnectionStatus.DISCONNECTED)
        val message = when (sessionStatus.statusCode) {
            MeetingSessionStatusCode.ok -> "Meeting ended"
            MeetingSessionStatusCode.audioServerHungup -> "Server disconnected"
            MeetingSessionStatusCode.audioJoinedFromAnotherDevice -> "Joined from another device"
            else -> "Session ended: ${sessionStatus.statusCode}"
        }
        onSessionError?.invoke(message, false)
    }

    override fun audioSessionDidCancelReconnect() {
        onConnectionStatusChanged?.invoke(ConnectionStatus.ERROR)
        onSessionError?.invoke("Failed to reconnect", false)
    }

    override fun connectionDidBecomePoor() {
        onConnectionStatusChanged?.invoke(ConnectionStatus.POOR_CONNECTION)
    }

    override fun connectionDidRecover() {
        onConnectionStatusChanged?.invoke(ConnectionStatus.CONNECTED)
    }

    override fun videoSessionDidStartConnecting() {}

    override fun videoSessionDidStartWithStatus(sessionStatus: MeetingSessionStatus) {
        if (sessionStatus.statusCode == MeetingSessionStatusCode.videoAtCapacityViewOnly) {
            onSessionError?.invoke("Video at capacity. View only.", false)
        }
    }

    override fun videoSessionDidStopWithStatus(sessionStatus: MeetingSessionStatus) {}

    override fun remoteVideoSourcesDidBecomeAvailable(sources: List<*>) {
        onRemoteVideoAvailable?.invoke(true, sources.size)
    }

    override fun remoteVideoSourcesDidBecomeUnavailable(sources: List<*>) {
        onRemoteVideoAvailable?.invoke(false, sources.size)
    }

    override fun cameraSendAvailabilityDidChange(available: Boolean) {
        onCameraSendAvailable?.invoke(available)
    }

    override fun attendeesDidJoin(attendeeInfo: List<*>) {
        realTimeListener?.onAttendeesJoined(attendeeInfo.filterIsInstance<AttendeeInfo>().map { it.attendeeId })
    }

    override fun attendeesDidLeave(attendeeInfo: List<*>) {
        realTimeListener?.onAttendeesLeft(attendeeInfo.filterIsInstance<AttendeeInfo>().map { it.attendeeId })
    }

    override fun attendeesDidDrop(attendeeInfo: List<*>) {
        realTimeListener?.onAttendeesDropped(attendeeInfo.filterIsInstance<AttendeeInfo>().map { it.attendeeId })
    }

    override fun attendeesDidMute(attendeeInfo: List<*>) {
        realTimeListener?.onAttendeesMuted(attendeeInfo.filterIsInstance<AttendeeInfo>().map { it.attendeeId })
    }

    override fun attendeesDidUnmute(attendeeInfo: List<*>) {
        realTimeListener?.onAttendeesUnmuted(attendeeInfo.filterIsInstance<AttendeeInfo>().map { it.attendeeId })
    }

    override fun signalStrengthDidChange(signalUpdates: List<*>) {
        signalUpdates.filterIsInstance<SignalUpdate>().forEach { update ->
            realTimeListener?.onSignalStrengthChanged(
                update.attendeeInfo.attendeeId,
                update.attendeeInfo.externalUserId,
                update.signalStrength.rawValue.toInt()
            )
        }
    }

    override fun volumeDidChange(volumeUpdates: List<*>) {
        volumeUpdates.filterIsInstance<VolumeUpdate>().forEach { update ->
            realTimeListener?.onVolumeChanged(
                update.attendeeInfo.attendeeId,
                update.attendeeInfo.externalUserId,
                update.volumeLevel.rawValue.toInt()
            )
        }
    }

    override fun videoTileDidAdd(tileState: VideoTileState) {
        NSOperationQueue.mainQueue.addOperationWithBlock {
            val tileId = tileState.tileId
            if (tileState.isLocalTile) {
                localTileId = tileId
                meetingSession?.audioVideo?.bindVideoView(videoView = localRenderView, tileId = tileId)
                onLocalVideoTileAdded?.invoke(tileId.toInt())
            } else {
                val attendeeId = tileState.attendeeId
                val oldTileId = attendeeTileMap[attendeeId]
                if (oldTileId != null && oldTileId != tileId) {
                    meetingSession?.audioVideo?.unbindVideoView(tileId = oldTileId)
                    remoteTiles.remove(oldTileId)
                    attendeeTileMap.remove(attendeeId)
                    onRemoteTileRemoved?.invoke(oldTileId.toInt())
                }
                val renderView = remoteTiles.getOrPut(tileId) {
                    DefaultVideoRenderView().also { it.mirror = false }
                }
                attendeeTileMap[attendeeId] = tileId
                meetingSession?.audioVideo?.bindVideoView(videoView = renderView, tileId = tileId)
                onRemoteTileAdded?.invoke(tileId.toInt())
            }
        }
    }

    override fun videoTileDidRemove(tileState: VideoTileState) {
        NSOperationQueue.mainQueue.addOperationWithBlock {
            val tileId = tileState.tileId
            meetingSession?.audioVideo?.unbindVideoView(tileId = tileId)
            if (tileId == localTileId) {
                localTileId = null
                onLocalVideoTileRemoved?.invoke()
            } else if (remoteTiles.containsKey(tileId)) {
                remoteTiles.remove(tileId)
                if (attendeeTileMap[tileState.attendeeId] == tileId) {
                    attendeeTileMap.remove(tileState.attendeeId)
                }
                onRemoteTileRemoved?.invoke(tileId.toInt())
            }
        }
    }

    override fun videoTileDidPause(tileState: VideoTileState) {}

    override fun videoTileDidResume(tileState: VideoTileState) {
        NSOperationQueue.mainQueue.addOperationWithBlock {
            val tileId = tileState.tileId
            if (tileState.isLocalTile) {
                localTileId = tileId
                meetingSession?.audioVideo?.bindVideoView(videoView = localRenderView, tileId = tileId)
                onLocalVideoTileAdded?.invoke(tileId.toInt())
            } else {
                val renderView = remoteTiles.getOrPut(tileId) {
                    DefaultVideoRenderView().also { it.mirror = false }
                }
                attendeeTileMap[tileState.attendeeId] = tileId
                meetingSession?.audioVideo?.bindVideoView(videoView = renderView, tileId = tileId)
                onRemoteTileAdded?.invoke(tileId.toInt())
            }
        }
    }

    override fun videoTileSizeDidChange(tileState: VideoTileState) {}

    override fun audioDeviceDidChange(freshAudioDeviceList: List<*>) {}

    override fun activeSpeakerDidDetect(attendeeInfo: List<*>) {
        val ids = attendeeInfo.filterIsInstance<AttendeeInfo>().map { it.externalUserId }.toSet()
        onActiveSpeakersChanged?.invoke(ids)
    }

    override fun activeSpeakerScoreDidChange(scores: Map<*, *>) {}

    override fun dataMessageDidReceived(dataMessage: DataMessage) {
        topicListeners[dataMessage.topic]?.invoke(
            TextMessage(
                topic = dataMessage.topic,
                senderId = dataMessage.senderAttendeeId,
                content = dataMessage.text() ?: "",
                timestamp = dataMessage.timestampMs
            )
        )
    }
}
