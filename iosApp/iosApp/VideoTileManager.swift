import Foundation
import AmazonChimeSDK
import ComposeApp

/// Manages video tile binding between Chime SDK tiles and render views.
/// Creates one DefaultVideoRenderView per remote tile on demand.
class VideoTileManager: NSObject, VideoTileObserver {

    private let localView: DefaultVideoRenderView
    private weak var audioVideo: (any AudioVideoFacade)?

    private var localTileId: Int?
    private var remoteTiles: [Int: DefaultVideoRenderView] = [:]

    init(
        localView: DefaultVideoRenderView,
        audioVideo: any AudioVideoFacade
    ) {
        self.localView = localView
        self.audioVideo = audioVideo
    }

    // MARK: – Public accessor for Compose bridge

    func remoteView(for tileId: Int) -> DefaultVideoRenderView? {
        return remoteTiles[tileId]
    }

    // MARK: – VideoTileObserver

    func videoTileDidAdd(tileState: VideoTileState) {
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }

            if tileState.isLocalTile {
                self.localTileId = tileState.tileId
                self.audioVideo?.bindVideoView(videoView: self.localView, tileId: tileState.tileId)
                ChimeSdkBridge.shared.eventDelegate?.onLocalVideoTileAdded(tileId: Int32(tileState.tileId))
            } else {
                let renderView = self.remoteTiles[tileState.tileId] ?? DefaultVideoRenderView()
                self.remoteTiles[tileState.tileId] = renderView
                self.audioVideo?.bindVideoView(videoView: renderView, tileId: tileState.tileId)
                ChimeSdkBridge.shared.eventDelegate?.onRemoteTileAdded(tileId: Int32(tileState.tileId))
            }
        }
    }

    func videoTileDidRemove(tileState: VideoTileState) {
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            self.audioVideo?.unbindVideoView(tileId: tileState.tileId)

            if tileState.tileId == self.localTileId {
                self.localTileId = nil
                ChimeSdkBridge.shared.eventDelegate?.onLocalVideoTileRemoved()
            } else if self.remoteTiles[tileState.tileId] != nil {
                self.remoteTiles.removeValue(forKey: tileState.tileId)
                ChimeSdkBridge.shared.eventDelegate?.onRemoteTileRemoved(tileId: Int32(tileState.tileId))
            }
        }
    }

    func videoTileDidPause(tileState: VideoTileState) {}

    func videoTileDidResume(tileState: VideoTileState) {
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            if tileState.isLocalTile {
                self.localTileId = tileState.tileId
                self.audioVideo?.bindVideoView(videoView: self.localView, tileId: tileState.tileId)
                ChimeSdkBridge.shared.eventDelegate?.onLocalVideoTileAdded(tileId: Int32(tileState.tileId))
            } else {
                let renderView = self.remoteTiles[tileState.tileId] ?? DefaultVideoRenderView()
                self.remoteTiles[tileState.tileId] = renderView
                self.audioVideo?.bindVideoView(videoView: renderView, tileId: tileState.tileId)
                ChimeSdkBridge.shared.eventDelegate?.onRemoteTileAdded(tileId: Int32(tileState.tileId))
            }
        }
    }

    func videoTileSizeDidChange(tileState: VideoTileState) {}
}
