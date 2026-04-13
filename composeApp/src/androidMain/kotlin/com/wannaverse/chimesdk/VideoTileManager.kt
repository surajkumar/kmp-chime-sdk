package com.wannaverse.chimesdk

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.amazonaws.services.chime.sdk.meetings.audiovideo.video.VideoTileObserver
import com.amazonaws.services.chime.sdk.meetings.audiovideo.video.VideoTileState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object VideoTileManager : VideoTileObserver {
    var localTileId by mutableStateOf<Int?>(null)
    var remoteTileIds by mutableStateOf<List<Int>>(emptyList())

    var onLocalTileAdded: (() -> Unit)? = null
    var onLocalTileRemoved: (() -> Unit)? = null
    var onRemoteTileAdded: ((Int) -> Unit)? = null
    var onRemoteTileRemoved: ((Int) -> Unit)? = null

    private val boundViews = mutableMapOf<Int, Any?>()

    override fun onVideoTileAdded(tileState: VideoTileState) {
        CoroutineScope(Dispatchers.Main).launch {
            if (tileState.isLocalTile) {
                localTileId = tileState.tileId
                onLocalTileAdded?.invoke()
            } else {
                if (tileState.tileId !in remoteTileIds) {
                    remoteTileIds = remoteTileIds + tileState.tileId
                }
                onRemoteTileAdded?.invoke(tileState.tileId)
            }
        }
    }

    override fun onVideoTileRemoved(tileState: VideoTileState) {
        CoroutineScope(Dispatchers.Main).launch {
            when {
                tileState.tileId == localTileId -> {
                    localTileId = null
                    onLocalTileRemoved?.invoke()
                }
                tileState.tileId in remoteTileIds -> {
                    remoteTileIds = remoteTileIds - tileState.tileId
                    onRemoteTileRemoved?.invoke(tileState.tileId)
                }
            }
        }
    }

    override fun onVideoTilePaused(tileState: VideoTileState) {}

    override fun onVideoTileResumed(tileState: VideoTileState) {
        CoroutineScope(Dispatchers.Main).launch {
            if (tileState.isLocalTile) {
                localTileId = tileState.tileId
            } else if (tileState.tileId !in remoteTileIds) {
                remoteTileIds = remoteTileIds + tileState.tileId
                onRemoteTileAdded?.invoke(tileState.tileId)
            }
        }
    }

    override fun onVideoTileSizeChanged(tileState: VideoTileState) {}

    fun updateBoundView(tileId: Int, view: Any) {
        boundViews[tileId] = view
    }

    fun isAlreadyBound(tileId: Int, view: Any): Boolean = boundViews[tileId] === view

    fun clearBoundView(tileId: Int) {
        boundViews.remove(tileId)
    }

    fun clearAll() {
        boundViews.forEach { (tileId, _) ->
            try {
                meetingSession?.audioVideo?.unbindVideoView(tileId)
            } catch (_: Exception) {}
        }
        boundViews.clear()
        localTileId = null
        remoteTileIds = emptyList()
        onLocalTileAdded = null
        onLocalTileRemoved = null
        onRemoteTileAdded = null
        onRemoteTileRemoved = null
    }
}
