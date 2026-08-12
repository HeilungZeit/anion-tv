package tv.anion.player

import android.content.Context
import android.view.SurfaceView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import tv.anion.resolve.StreamResolver
import tv.anion.source.model.PlayableStream
import tv.anion.source.model.Segment

class ExoPlaybackController(
    context: Context,
    private val okHttp: OkHttpClient,
    private val resolver: StreamResolver? = null,
    private val progress: PlaybackProgressListener? = null,
) : PlaybackController {

    private val appContext = context.applicationContext
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Main.immediate)
    private val reResolve = ReResolveOnForbidden()

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var player: ExoPlayer? = null
    private var session: PlaybackSession? = null
    private var skip: SkipController? = null
    private var ticker: Job? = null
    private var surfaceView: SurfaceView? = null
    @Volatile private var refreshInFlight = false
    @Volatile private var expectResume = false

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publishState()
            // READY на каждом тике сбросил бы затвор 403 и пропустил шторм сегментов.
            if (expectResume && player.playbackState == Player.STATE_READY) {
                expectResume = false
                reResolve.onPlaybackResumed()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            if (refreshInFlight) return
            val code = error.httpCode()
            when (reResolve.onSegmentError(code ?: -1)) {
                ReResolveOnForbidden.Decision.ReResolve -> scope.launch { refresh() }
                ReResolveOnForbidden.Decision.Fail ->
                    publishError("подпись потока истекла")
                ReResolveOnForbidden.Decision.Propagate ->
                    publishError(error.localizedMessage ?: "ошибка воспроизведения")
            }
        }
    }

    override suspend fun prepare(session: PlaybackSession) {
        this.session = session
        skip = SkipController(session.skips, session.isLastEpisode)
        reResolve.rememberPosition(session.startPositionMs)
        attach(session.stream, session.startPositionMs)
    }

    override fun play() {
        player?.play()
    }

    override fun pause() {
        player?.pause()
    }

    override fun seekBy(deltaMs: Long) {
        val p = player ?: return
        val duration = p.duration.takeIf { it != C.TIME_UNSET } ?: Long.MAX_VALUE
        p.seekTo((p.currentPosition + deltaMs).coerceIn(0L, duration))
    }

    override fun skipTo(segment: Segment) {
        player?.seekTo(skip?.skipTargetMs(segment) ?: (segment.endSeconds * 1000L))
    }

    override fun setVideoSurfaceView(view: SurfaceView?) {
        val exo = player
        surfaceView?.let { exo?.clearVideoSurfaceView(it) }
        surfaceView = view
        view?.let { exo?.setVideoSurfaceView(it) }
    }

    override fun release() {
        ticker?.cancel()
        ticker = null
        surfaceView?.let { player?.clearVideoSurfaceView(it) }
        surfaceView = null
        player?.removeListener(listener)
        player?.release()
        player = null
        job.cancel()
        _state.value = PlaybackState()
    }

    private fun attach(stream: PlayableStream, positionMs: Long) {
        val mediaSource = HlsMediaSource.Factory(StreamDataSourceFactory.create(okHttp, stream.headers))
            .setLoadErrorHandlingPolicy(NoRetryOnForbiddenPolicy())
            .createMediaSource(MediaItem.fromUri(stream.url))

        val exo = player ?: ExoPlayer.Builder(appContext).build().also {
            it.addListener(listener)
            player = it
        }

        surfaceView?.let { exo.setVideoSurfaceView(it) }
        exo.setMediaSource(mediaSource, positionMs.coerceAtLeast(0L))
        exo.prepare()
        exo.playWhenReady = true
        startTicker()
        publishState()
    }

    private suspend fun refresh() {
        if (refreshInFlight) return
        refreshInFlight = true
        try {
            val current = session
            val iframe = current?.iframeUrl
            if (current == null || iframe == null || resolver == null) {
                publishError("подпись потока истекла")
                return
            }
            val position = player?.currentPosition?.takeIf { it > 0 } ?: reResolve.savedPositionMs
            val resolved = resolver.resolve(iframe, current.preferredQuality)
            val stream = PlayableStream(resolved.manifestUrl, resolved.quality, resolved.headers)
            session = current.copy(stream = stream)
            expectResume = true
            attach(stream, position)
        } catch (e: Exception) {
            publishError(e.message ?: "не удалось обновить поток")
        } finally {
            refreshInFlight = false
        }
    }

    private suspend fun maybeRefreshAhead() {
        val current = session ?: return
        if (refreshInFlight || current.iframeUrl == null || resolver == null) return
        if (!SignatureClock.shouldRefreshAhead(current.stream.url)) return
        refresh()
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                val p = player
                if (p != null) {
                    reResolve.rememberPosition(p.currentPosition)
                    publishState()
                    val duration = p.duration.takeIf { it != C.TIME_UNSET } ?: 0L
                    progress?.onProgress(p.currentPosition, duration)
                    maybeRefreshAhead()
                }
                delay(TICK_MS)
            }
        }
    }

    private fun publishState() {
        val p = player
        val position = p?.currentPosition ?: 0L
        val duration = p?.duration?.takeIf { it != C.TIME_UNSET } ?: 0L
        val playing = p?.isPlaying == true
        _state.value = PlaybackState(
            positionMs = position,
            durationMs = duration,
            isPlaying = playing,
            buffering = p?.playbackState == Player.STATE_BUFFERING,
            error = if (playing) null else _state.value.error,
            visibleSkip = skip?.visibleSkip(position),
            ended = p?.playbackState == Player.STATE_ENDED,
        )
    }

    private fun publishError(message: String) {
        _state.value = _state.value.copy(error = message, isPlaying = false)
    }

    private companion object {
        const val TICK_MS = 500L
    }
}

/** 403 не ретраится внутри ExoPlayer — иначе шторм сегментов обойдёт [ReResolveOnForbidden]. */
internal class NoRetryOnForbiddenPolicy : DefaultLoadErrorHandlingPolicy() {
    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val cause = loadErrorInfo.exception
        if (cause is HttpDataSource.InvalidResponseCodeException && cause.responseCode == 403) {
            return C.TIME_UNSET
        }
        return super.getRetryDelayMsFor(loadErrorInfo)
    }
}

internal fun PlaybackException.httpCode(): Int? {
    var current: Throwable? = this
    while (current != null) {
        if (current is HttpDataSource.InvalidResponseCodeException) return current.responseCode
        current = current.cause
    }
    return null
}
