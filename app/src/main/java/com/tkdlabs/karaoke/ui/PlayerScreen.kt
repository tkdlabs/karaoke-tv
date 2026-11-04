package com.tkdlabs.karaoke.ui

import android.app.Activity
import android.view.KeyEvent
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.input.key.*
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.tkdlabs.karaoke.util.PlaybackLogger
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.tkdlabs.karaoke.data.KaraokeApi
import com.tkdlabs.karaoke.data.LyricsLine
import com.tkdlabs.karaoke.data.Settings
import com.tkdlabs.karaoke.data.Song
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@kotlin.OptIn(ExperimentalTvMaterial3Api::class)
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    song: Song,
    baseUrl: String,
    api: KaraokeApi,
    defaultVocalIndex: Int,
    defaultLyricsMode: Int,
    nativeLyrics: Boolean,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    var pitch by remember { mutableIntStateOf(0) }
    var vocalIndex by remember { mutableIntStateOf(defaultVocalIndex) }
    var lyricsModeIndex by remember {
        mutableIntStateOf(if (song.hasOverlay && !nativeLyrics) defaultLyricsMode else 0)
    }
    var hudVisible by remember { mutableStateOf(true) }
    var hudTimestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Native LRC lyrics state
    var lyricsLines by remember { mutableStateOf<List<LyricsLine>>(emptyList()) }
    var activeLyricIndex by remember { mutableIntStateOf(-1) }

    val vocal = KaraokeApi.VOCALS[vocalIndex]
    val lyricsMode = Settings.LYRICS_MODES[lyricsModeIndex]

    // Video player (muted)
    val videoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            volume = 0f
            playWhenReady = false
        }
    }

    // Two audio players: active plays, preload buffers the next variant
    val audioPlayerA = remember {
        ExoPlayer.Builder(context).build().apply { playWhenReady = false }
    }
    val audioPlayerB = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = false
            volume = 0f
        }
    }
    var audioPlayer by remember { mutableStateOf(audioPlayerA) }
    var preloadPlayer by remember { mutableStateOf(audioPlayerB) }

    // Load initial media — native mode always uses the plain video stream
    LaunchedEffect(song.id) {
        val videoVariant = if (nativeLyrics) "original" else lyricsMode.videoVariant
        val videoPrepareStart = PlaybackLogger.startTimer()
        videoPlayer.setMediaItem(MediaItem.fromUri(KaraokeApi.videoUrl(baseUrl, song.id, videoVariant)))
        videoPlayer.prepare()

        val audioUrl = KaraokeApi.variantUrl(baseUrl, song.id, pitch, vocal.value)
        val audioPrepareStart = PlaybackLogger.startTimer()
        audioPlayer.setMediaItem(MediaItem.fromUri(audioUrl))
        audioPlayer.prepare()

        suspend fun awaitReady(player: ExoPlayer) {
            while (player.playbackState != Player.STATE_READY) {
                delay(50)
            }
        }
        awaitReady(videoPlayer)
        val videoPrepareMs = PlaybackLogger.elapsedMs(videoPrepareStart)
        awaitReady(audioPlayer)
        val audioPrepareMs = PlaybackLogger.elapsedMs(audioPrepareStart)

        PlaybackLogger.event(
            "initial_ready",
            "video_prepare_ms" to videoPrepareMs,
            "audio_prepare_ms" to audioPrepareMs,
            "video_buffered_ms" to videoPlayer.totalBufferedDuration,
            "audio_buffered_ms" to audioPlayer.totalBufferedDuration,
        )

        videoPlayer.play()
        audioPlayer.play()
    }

    // Fetch LRC lyrics when in native mode
    LaunchedEffect(song.id, nativeLyrics) {
        if (!nativeLyrics) return@LaunchedEffect
        lyricsLines = try {
            val response = api.getLyrics(song.id, romanize = defaultLyricsMode == 2)
            if (response.hasLyrics) response.lines else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    var switchJob by remember { mutableStateOf<Job?>(null) }

    // Sync video to audio — graduated correction to avoid seek-induced BUFFERING loops.
    // Hard seeks cause BUFFERING which creates new drift equal to the seek time, so we
    // only hard-seek for genuinely large divergence. Small chronic lag is corrected by
    // nudging playback speed; imperceptible drift (<500ms) is left alone entirely.
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            val audioPos = audioPlayer.currentPosition
            val videoPos = videoPlayer.currentPosition
            val drift = audioPos - videoPos  // signed: positive = video behind audio
            val absDrift = kotlin.math.abs(drift)
            when {
                absDrift < 500 -> {
                    // Imperceptible — reset speed in case we were nudging, do nothing else
                    videoPlayer.setPlaybackSpeed(1.0f)
                }
                absDrift < 3000 -> {
                    // Moderate lag: nudge speed to converge without seeking
                    val speed = if (drift > 0) 1.05f else 0.95f
                    videoPlayer.setPlaybackSpeed(speed)
                    PlaybackLogger.event(
                        "drift_nudge",
                        "drift_ms" to drift,
                        "speed" to speed,
                        "audio_pos" to audioPos,
                        "video_pos" to videoPos,
                        "in_switch" to (switchJob != null),
                    )
                }
                else -> {
                    // Large divergence: hard seek is unavoidable
                    videoPlayer.setPlaybackSpeed(1.0f)
                    PlaybackLogger.event(
                        "drift_seek",
                        "drift_ms" to drift,
                        "audio_pos" to audioPos,
                        "video_pos" to videoPos,
                        "in_switch" to (switchJob != null),
                        "audio_buffered_ms" to audioPlayer.totalBufferedDuration,
                        "video_buffered_ms" to videoPlayer.totalBufferedDuration,
                    )
                    videoPlayer.seekTo(audioPos)
                }
            }
        }
    }

    // Predictive lyrics sync — schedule next update at the exact timestamp
    LaunchedEffect(lyricsLines, nativeLyrics) {
        if (!nativeLyrics || lyricsLines.isEmpty()) return@LaunchedEffect
        while (true) {
            val audioPos = audioPlayer.currentPosition
            val newIndex = lyricsLines.indexOfLast { it.timeMs <= audioPos }
            if (newIndex != activeLyricIndex) {
                activeLyricIndex = newIndex
            }
            val nextIndex = if (newIndex < 0) 0 else newIndex + 1
            if (nextIndex >= lyricsLines.size) break
            val animationLeadMs = 300L
            val delayMs = (lyricsLines[nextIndex].timeMs - audioPos - animationLeadMs).coerceAtLeast(50L)
            delay(delayMs)
        }
    }

    // Auto-hide HUD
    LaunchedEffect(hudTimestamp) {
        hudVisible = true
        delay(3000)
        hudVisible = false
    }

    // Handle song end
    DisposableEffect(audioPlayer) {
        val currentPlayer = audioPlayer
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) onBack()
            }
        }
        currentPlayer.addListener(listener)
        onDispose { currentPlayer.removeListener(listener) }
    }

    // Player state logging — all three instances
    DisposableEffect(videoPlayer) {
        val listener = playbackStateListener("video", videoPlayer)
        videoPlayer.addListener(listener)
        onDispose { videoPlayer.removeListener(listener) }
    }
    DisposableEffect(audioPlayerA) {
        val listener = playbackStateListener("audioA", audioPlayerA)
        audioPlayerA.addListener(listener)
        onDispose { audioPlayerA.removeListener(listener) }
    }
    DisposableEffect(audioPlayerB) {
        val listener = playbackStateListener("audioB", audioPlayerB)
        audioPlayerB.addListener(listener)
        onDispose { audioPlayerB.removeListener(listener) }
    }

    // Keep screen on during playback
    DisposableEffect(view) {
        val window = (view.context as Activity).window
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            videoPlayer.release()
            audioPlayerA.release()
            audioPlayerB.release()
        }
    }

    fun showHud() {
        hudTimestamp = System.currentTimeMillis()
    }

    fun switchAudioVariant(newPitch: Int, newVocal: Int) {
        switchJob?.cancel()
        preloadPlayer.stop()
        preloadPlayer.volume = 0f

        val targetPos = audioPlayer.currentPosition + 500
        val url = KaraokeApi.variantUrl(baseUrl, song.id, newPitch, newVocal)
        val prepareStart = PlaybackLogger.startTimer()

        preloadPlayer.setMediaItem(MediaItem.fromUri(url))
        preloadPlayer.prepare()

        switchJob = scope.launch {
            while (preloadPlayer.playbackState != Player.STATE_READY) {
                delay(50)
            }
            val prepareMs = PlaybackLogger.elapsedMs(prepareStart)
            val audioPos = audioPlayer.currentPosition
            val overshootMs = (audioPos - targetPos).coerceAtLeast(0)
            PlaybackLogger.event(
                "audio_switch_ready",
                "prepare_ms" to prepareMs,
                "target_pos" to targetPos,
                "audio_pos" to audioPos,
                "overshoot_ms" to overshootMs,
                "preload_buffered_ms" to preloadPlayer.totalBufferedDuration,
                "active_buffered_ms" to audioPlayer.totalBufferedDuration,
            )

            preloadPlayer.seekTo(targetPos)
            while (audioPlayer.currentPosition < targetPos) {
                delay(20)
            }
            val livePos = audioPlayer.currentPosition
            PlaybackLogger.event(
                "audio_switch_live",
                "target_pos" to targetPos,
                "live_pos" to livePos,
                "gap_ms" to (livePos - targetPos),
            )
            preloadPlayer.volume = 1f
            preloadPlayer.play()
            val old = audioPlayer
            audioPlayer = preloadPlayer
            preloadPlayer = old
            old.stop()
            old.volume = 0f
            switchJob = null
        }
    }

    fun switchVideo(variant: String) {
        val capturedPos = videoPlayer.currentPosition
        val prepareStart = PlaybackLogger.startTimer()
        videoPlayer.setMediaItem(MediaItem.fromUri(KaraokeApi.videoUrl(baseUrl, song.id, variant)))
        videoPlayer.prepare()
        scope.launch {
            while (videoPlayer.playbackState != Player.STATE_READY) {
                delay(50)
            }
            val prepareMs = PlaybackLogger.elapsedMs(prepareStart)
            val audioPos = audioPlayer.currentPosition
            PlaybackLogger.event(
                "video_switch_ready",
                "prepare_ms" to prepareMs,
                "seek_to" to capturedPos,
                "audio_pos" to audioPos,
                "staleness_ms" to (audioPos - capturedPos),
            )
            videoPlayer.seekTo(capturedPos)
            videoPlayer.play()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                showHud()
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_BACK -> { onBack(); true }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        val newPitch = (pitch + 1).coerceAtMost(3)
                        if (newPitch != pitch) {
                            pitch = newPitch
                            switchAudioVariant(pitch, vocal.value)
                        }
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        val newPitch = (pitch - 1).coerceAtLeast(-3)
                        if (newPitch != pitch) {
                            pitch = newPitch
                            switchAudioVariant(pitch, vocal.value)
                        }
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        val newIdx = (vocalIndex - 1 + KaraokeApi.VOCALS.size) % KaraokeApi.VOCALS.size
                        vocalIndex = newIdx
                        switchAudioVariant(pitch, KaraokeApi.VOCALS[newIdx].value)
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        val newIdx = (vocalIndex + 1) % KaraokeApi.VOCALS.size
                        vocalIndex = newIdx
                        switchAudioVariant(pitch, KaraokeApi.VOCALS[newIdx].value)
                        true
                    }
                    KeyEvent.KEYCODE_PROG_RED,
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_L -> {
                        if (!nativeLyrics && song.hasOverlay) {
                            val newIdx = (lyricsModeIndex + 1) % Settings.LYRICS_MODES.size
                            lyricsModeIndex = newIdx
                            switchVideo(Settings.LYRICS_MODES[newIdx].videoVariant)
                        }
                        true
                    }
                    else -> false
                }
            }
            .focusable(),
    ) {
        // Video surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = videoPlayer
                    useController = false
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Native LRC lyrics overlay
        if (nativeLyrics) {
            NativeLyricsOverlay(
                lines = lyricsLines,
                activeIndex = activeLyricIndex,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 64.dp),
            )
        }

        // HUD overlay — top
        AnimatedVisibility(
            visible = hudVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(16.dp),
            ) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Pitch: ${if (pitch >= 0) "+$pitch" else "$pitch"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                )
                Text(
                    text = "Vocal: ${vocal.label}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                )
                if (!nativeLyrics && song.hasOverlay) {
                    Text(
                        text = "Lyrics: ${lyricsMode.label}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                    )
                }
                if (nativeLyrics) {
                    Text(
                        text = "Lyrics: Native",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                    )
                }
            }
        }

        // Controls hint — bottom
        AnimatedVisibility(
            visible = hudVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Row(
                modifier = Modifier
                    .padding(24.dp)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Text("↑↓ Pitch", color = Color.White, style = MaterialTheme.typography.bodySmall)
                Text("←→ Vocal", color = Color.White, style = MaterialTheme.typography.bodySmall)
                if (!nativeLyrics && song.hasOverlay) {
                    Text("OK Lyrics", color = Color.White, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun playbackStateListener(name: String, player: ExoPlayer) = object : Player.Listener {
    override fun onPlaybackStateChanged(state: Int) {
        val stateName = when (state) {
            Player.STATE_IDLE -> "IDLE"
            Player.STATE_BUFFERING -> "BUFFERING"
            Player.STATE_READY -> "READY"
            Player.STATE_ENDED -> "ENDED"
            else -> "UNKNOWN($state)"
        }
        PlaybackLogger.event(
            "player_state",
            "player" to name,
            "state" to stateName,
            "pos" to player.currentPosition,
            "buffered_ms" to player.totalBufferedDuration,
        )
    }

    override fun onPlayerError(error: PlaybackException) {
        PlaybackLogger.event(
            "player_error",
            "player" to name,
            "error" to (error.message ?: "unknown"),
            "code" to error.errorCode,
        )
    }
}

@kotlin.OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun NativeLyricsOverlay(
    lines: List<LyricsLine>,
    activeIndex: Int,
    modifier: Modifier = Modifier,
) {
    if (activeIndex < 0 || activeIndex >= lines.size) return
    val current = lines[activeIndex]
    val next = lines.getOrNull(activeIndex + 1)

    // Alternate which physical slot holds the current line so it never appears
    // to move when transitioning from "next" to "current" — the line stays in
    // place and only its styling changes.
    // Even activeIndex: top = current, bottom = next
    // Odd  activeIndex: top = next,    bottom = current
    val isEven = activeIndex % 2 == 0
    val topLine = if (isEven) current else next
    val bottomLine = if (isEven) next else current
    val topIsCurrent = isEven

    Column(
        modifier = modifier
            .padding(horizontal = 32.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        topLine?.let { LyricsLineBlock(line = it, isCurrent = topIsCurrent) }
        if (topLine != null && bottomLine != null) {
            Spacer(modifier = Modifier.height(12.dp))
        }
        bottomLine?.let { LyricsLineBlock(line = it, isCurrent = !topIsCurrent) }
    }
}

@kotlin.OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LyricsLineBlock(line: LyricsLine, isCurrent: Boolean) {
    OutlinedText(
        text = line.text,
        color = if (isCurrent) Color(0xFFFFD700) else Color.White,
        style = MaterialTheme.typography.headlineMedium,
    )
    line.romanized?.let { romanized ->
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedText(
            text = romanized,
            color = if (isCurrent) Color(0xFF00FFFF) else Color.White,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@kotlin.OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun OutlinedText(text: String, color: Color, style: TextStyle) {
    Box(contentAlignment = Alignment.Center) {
        Text(
            text = text,
            color = Color.Black,
            style = style.copy(
                drawStyle = Stroke(
                    width = 6f,
                    join = StrokeJoin.Round,
                    cap = StrokeCap.Round,
                ),
            ),
            textAlign = TextAlign.Center,
        )
        Text(
            text = text,
            color = color,
            style = style.copy(drawStyle = Fill),
            textAlign = TextAlign.Center,
        )
    }
}
