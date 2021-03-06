package com.seagazer.liteplayer.player.exo

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.view.Surface
import android.view.SurfaceHolder
import androidx.lifecycle.MutableLiveData
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.ext.rtmp.RtmpDataSourceFactory
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.source.dash.DashMediaSource
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.upstream.DataSpec
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.upstream.RawResourceDataSource
import com.google.android.exoplayer2.util.EventLogger
import com.google.android.exoplayer2.util.Util
import com.google.android.exoplayer2.video.VideoListener
import com.seagazer.liteplayer.bean.IDataSource
import com.seagazer.liteplayer.config.PlayerState
import com.seagazer.liteplayer.event.PlayerStateEvent
import com.seagazer.liteplayer.helper.MediaLogger
import com.seagazer.liteplayer.player.IPlayer

/**
 * Google exo decoder.
 *
 * Author: Seagazer
 * Date: 2020/6/19
 */
class ExoPlayerImpl constructor(val context: Context) : IPlayer {
    companion object {
        private const val TYPE_RTMP = -110
    }

    private val appContext = context.applicationContext
    private lateinit var player: SimpleExoPlayer
    private var dataSourceFactory: DefaultDataSourceFactory
    private var currentState = PlayerState.STATE_NOT_INITIALIZED
    private var videoWidth = 0
    private var videoHeight = 0
    private var startPosition = 0L
    private var isPreparing = true
    private var isBuffering = true
    private var isPendingSeek = false
    private var liveData: MutableLiveData<PlayerStateEvent>? = null

    private val videoListener = object : VideoListener {
        override fun onVideoSizeChanged(width: Int, height: Int, unappliedRotationDegrees: Int, pixelWidthHeightRatio: Float) {
            this@ExoPlayerImpl.videoWidth = width
            this@ExoPlayerImpl.videoHeight = height
            liveData?.value = PlayerStateEvent(PlayerState.STATE_VIDEO_SIZE_CHANGED).apply {
                videoWidth = width
                videoHeight = height
            }
        }

        override fun onRenderedFirstFrame() {
            liveData?.value = PlayerStateEvent(PlayerState.STATE_RENDERED_FIRST_FRAME)
        }

        override fun onSurfaceSizeChanged(width: Int, height: Int) {
            liveData?.value = PlayerStateEvent(PlayerState.STATE_SURFACE_SIZE_CHANGED)
        }
    }

    private val eventListener = object : Player.EventListener {
        override fun onLoadingChanged(isLoading: Boolean) {
            if (!isLoading) {
                liveData?.value =
                    PlayerStateEvent(PlayerState.STATE_BUFFER_UPDATE).apply {
                        videoWidth = getVideoWidth()
                        videoHeight = getVideoHeight()
                        bufferedPercentage = player.bufferedPercentage
                    }
            }
        }

        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
            // 已经开始播放，两种状态：播放，暂停
            if (!isPreparing) {
                if (playWhenReady) {
                    notifyPlayStateChanged(PlayerState.STATE_STARTED)
                } else {
                    notifyPlayStateChanged(PlayerState.STATE_PAUSED)
                }
            }
            // setDataSource初始化，第一次启播状态：播放，准备好了但是未播放
            if (isPreparing) {
                if (playbackState == Player.STATE_READY) {
                    liveData?.value = PlayerStateEvent(PlayerState.STATE_PREPARED)
                    isPreparing = false
                    if (playWhenReady) {
                        notifyPlayStateChanged(PlayerState.STATE_STARTED)
                    } else {
                        notifyPlayStateChanged(PlayerState.STATE_PREPARED)
                    }
                    if (startPosition > 0) {
                        seekTo(startPosition)
                        startPosition = 0
                    }
                }
            }
            // 正在缓冲状态，播放器准备结束或者播放结束 -> 缓冲结束
            if (isBuffering) {
                if (playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED) {
                    isBuffering = false
                    liveData?.value = PlayerStateEvent(PlayerState.STATE_BUFFER_END)
                }
            }

            // 开始拖动进度动作，播放器准备结束 -> 拖动结束
            if (isPendingSeek) {
                if (playbackState == Player.STATE_READY) {
                    isPendingSeek = false
                    liveData?.value = PlayerStateEvent(PlayerState.STATE_SEEK_COMPLETED)
                }
            }
            // 已经开始播放了，缓冲状态，停止状态
            if (!isPreparing) {
                if (playbackState == Player.STATE_BUFFERING) {
                    isBuffering = true
                    liveData?.value = PlayerStateEvent(PlayerState.STATE_BUFFER_START)
                } else if (playbackState == Player.STATE_ENDED) {
                    notifyPlayStateChanged(PlayerState.STATE_PLAYBACK_COMPLETE)
                }
            }
        }

        @SuppressLint("SwitchIntDef")
        override fun onPlayerError(error: ExoPlaybackException) {
            setPlayerState(PlayerState.STATE_ERROR)
            when (error.type) {
                ExoPlaybackException.TYPE_SOURCE -> {
                    MediaLogger.e("-->TYPE_SOURCE")
                }
                ExoPlaybackException.TYPE_RENDERER -> {
                    MediaLogger.e("-->TYPE_RENDERER")
                }
                ExoPlaybackException.TYPE_UNEXPECTED -> {
                    MediaLogger.e("-->TYPE_UNEXPECTED")
                }
            }
            liveData?.value = PlayerStateEvent(PlayerState.STATE_ERROR).apply {
                errorCode = error.type
            }
        }
    }

    init {
        player = SimpleExoPlayer.Builder(appContext)
            .setTrackSelector(DefaultTrackSelector(appContext))
            .build()
        player.addVideoListener(videoListener)
        player.addListener(eventListener)
        if (MediaLogger.isOpenLogger()) {
            player.addAnalyticsListener(EventLogger(DefaultTrackSelector(appContext)))
        }
        dataSourceFactory = DefaultDataSourceFactory(appContext, Util.getUserAgent(appContext, appContext.packageName))
    }

    private fun isInPlaybackState(): Boolean {
        val state = getPlayerState()
        return state != PlayerState.STATE_ERROR &&
                state != PlayerState.STATE_NOT_INITIALIZED && state != PlayerState.STATE_STOPPED
    }

    override fun registerStateObserver(liveData: MutableLiveData<PlayerStateEvent>) {
        if (this.liveData != liveData) {
            this.liveData = liveData
        }
    }

    override fun getPlayer() = this

    override fun setDataSource(source: IDataSource) {
        try {
            isBuffering = false
            isPendingSeek = false
            player.stop(true)
            if (currentState == PlayerState.STATE_NOT_INITIALIZED) {
                notifyPlayStateChanged(PlayerState.STATE_INITIALIZED)
            }
            val url = source.mediaUrl
            val uri = Uri.parse(url)
            val contentType = when {
                url.startsWith("rtmp:") -> {
                    TYPE_RTMP
                }
                else -> {
                    Util.inferContentType(uri)
                }
            }
            val mediaSource = if (source.rawId > 0) {// raw resource
                val dataSpec = DataSpec(RawResourceDataSource.buildRawResourceUri(source.rawId))
                val rawResourceDataSource = RawResourceDataSource(context)
                rawResourceDataSource.open(dataSpec)
                ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(rawResourceDataSource.uri)
            } else {// assets or normal resource
                when (contentType) {
                    TYPE_RTMP -> ProgressiveMediaSource.Factory(RtmpDataSourceFactory(null)).createMediaSource(uri)
                    C.TYPE_OTHER -> ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(uri)
                    C.TYPE_DASH -> DashMediaSource.Factory(dataSourceFactory).createMediaSource(uri)
                    C.TYPE_SS -> SsMediaSource.Factory(dataSourceFactory).createMediaSource(uri)
                    C.TYPE_HLS -> HlsMediaSource.Factory(dataSourceFactory).createMediaSource(uri)
                    else -> ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(uri)
                }
            }
            player.prepare(mediaSource)
            isPreparing = true
        } catch (ex: Exception) {
            MediaLogger.e(ex.message!!)
        }
    }

    override fun start() {
        start(0)
    }

    override fun start(startPosition: Long) {
        this.startPosition = startPosition
        player.playWhenReady = true
    }

    override fun pause(fromUser: Boolean) {
        val state = getPlayerState()
        if (state != PlayerState.STATE_STOPPED && state != PlayerState.STATE_ERROR &&
            state != PlayerState.STATE_NOT_INITIALIZED && state != PlayerState.STATE_PAUSED
        ) {
            player.playWhenReady = false
            isPreparing = true
            notifyPlayStateChanged(PlayerState.STATE_PAUSED)
        }
    }

    override fun resume() {
        if (isInPlaybackState() && getPlayerState() == PlayerState.STATE_PAUSED || getPlayerState() == PlayerState.STATE_PREPARED) {
            player.playWhenReady = true
        }
    }

    override fun setSurfaceHolder(surfaceHolder: SurfaceHolder) {
        player.setVideoSurfaceHolder(surfaceHolder)
    }

    override fun setSurface(surface: Surface) {
        player.setVideoSurface(surface)
    }

    override fun supportSoftwareDecode(softwareDecode: Boolean) {
        MediaLogger.w("Not support!")
    }

    override fun notifyPlayStateChanged(newState: PlayerState) {
        setPlayerState(newState)
        liveData?.value = PlayerStateEvent(newState)
    }

    override fun setAutoPausedWhenAudioFocusLoss(autoPaused: Boolean) {
    }

    override fun getVideoWidth() = videoWidth

    override fun getVideoHeight() = videoHeight

    override fun getDuration() = if (player.duration < 0) 0 else player.duration

    override fun isPlaying() =
        isInPlaybackState() && currentState != PlayerState.STATE_INITIALIZED
                && currentState != PlayerState.STATE_PAUSED

    override fun getBufferedPercentage() = player.bufferedPercentage

    override fun seekTo(position: Long) {
        if (isInPlaybackState()) {
            isPendingSeek = true
            liveData?.value = PlayerStateEvent(PlayerState.STATE_SEEK_START)
            player.seekTo(position)
        }
    }

    override fun stop() {
        isPreparing = true
        isBuffering = false
        isPendingSeek = false
        notifyPlayStateChanged(PlayerState.STATE_STOPPED)
        player.stop(true)
    }

    override fun reset() {
        // not support reset, just mark state not init
        notifyPlayStateChanged(PlayerState.STATE_NOT_INITIALIZED)
    }

    override fun destroy() {
        isPreparing = true
        isBuffering = false
        isPendingSeek = false
        setPlayerState(PlayerState.STATE_NOT_INITIALIZED)
        player.setVideoSurface(null)
        player.setVideoSurfaceHolder(null)
        player.removeListener(eventListener)
        player.removeVideoListener(videoListener)
        player.release()
    }

    override fun getCurrentPosition() = player.currentPosition

    override fun setPlaySpeed(speed: Float) {
        val parameters = PlaybackParameters(speed, 1f)
        player.setPlaybackParameters(parameters)
    }

    override fun setVolume(volume: Int) {
        // do nothing, audio manager will handle it always
    }

    override fun setPlayerState(state: PlayerState) {
        currentState = state
    }

    override fun getPlayerState() = currentState

}