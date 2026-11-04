# feat_04 — Video Stutter: Instrumentation & Adaptive Playback Design

## Problem Statement

Two distinct failure modes have been observed:

1. **Local (good network)**: Brief stutter when the user changes pitch/vocal variant. Audio switches cleanly but the video drift-correction loop fires a visible seek.
2. **High-latency remote (e.g., VPN over geographic distance)**: Video/audio freezes indefinitely when switching variants. The stream never recovers on its own.

Both modes suggest the fixed-lookahead switch strategy (`currentPosition + 500ms`) does not account for actual network latency or buffer health.

---

## Part A.5 — Findings from First Instrumented Session (2026-04-14)

Logs collected via `adb logcat -s KARAOKE_PERF` during a local repro of the laggy-video case.

### What the logs showed

The drift correction loop is the stutter — a self-reinforcing death spiral that runs continuously even without any user input:

```
drift_seek drift_ms=509 → seekTo(video)
  → player_state video BUFFERING  (~400ms freeze)
  → player_state video READY
  → audio advanced ~500ms during buffering
  → drift_seek fires again immediately
  → repeat forever
```

Key data points:
- `in_switch=false` on every single `drift_seek` — no variant switch triggered this, it happens during **normal playback**
- `drift_seek` fired on every 500ms tick without exception for the entire session
- `audio_buffered_ms`: 50,000–70,000ms — audio is rock solid, 50-70s ahead
- `video_buffered_ms`: 2,000–10,000ms — video buffer is tiny; each hard seek empties and rebuilds it
- Each seek causes ~300–500ms of `BUFFERING`, during which audio advances again, restoring the same drift

### Root cause

The video stream is inherently slower to buffer than the audio (higher bitrate, heavier decode). A persistent ~500ms lag accumulates. The drift correction loop detects this, fires `seekTo`, which causes BUFFERING for as long as the seek takes, which creates new drift equal to the BUFFERING duration — so the condition never heals.

**The drift correction loop is the cause of the visible stutter, not the cure.**

A 500ms video-behind-audio offset is not perceptible in karaoke (the muted video's lyrics overlay drifts slightly, but this is far less disruptive than a freeze every second). The constant `BUFFERING` from seeking is brutally visible.

### Conclusions

- B3 (drift correction overhaul) is confirmed as the highest-priority fix — it directly causes the observed stutter
- B1/B2 (dynamic lookahead, listener-based await) remain relevant for the variant-switch freeze on high-latency links but were not the issue in this session
- B4 (buffer tuning) may help the video buffer stay larger, reducing BUFFERING duration after seeks, but does not address the root loop

---

## Part A — Instrumentation

Before changing anything, add lightweight event logging so we can observe what's actually happening. The goal is data, not fixes.

### A1. What to measure

| Metric | Where | Why |
|---|---|---|
| Time from `prepare()` call to `STATE_READY` | `switchAudioVariant`, `switchVideo`, initial load | Directly measures "effective latency to first byte" for each stream request |
| `targetPos` vs actual `currentPosition` at switch moment | `switchAudioVariant` | Quantifies how far behind the lookahead was |
| Drift magnitude at each 500ms sync tick | drift correction loop | Tells us if drift is chronic (network) or spikes only on switch |
| Drift correction seek count | drift correction loop | Frequency of visible seeks |
| `playbackState` transitions (BUFFERING → READY → ENDED etc.) | `Player.Listener` on all three players | Catch unexpected buffer stalls |
| Buffer head at moment of switch | `audioPlayer.totalBufferedDuration` | Is RAM thrashing the issue? |
| Switch-to-audible latency | time from key event to preload volume=1f | End-to-end user-perceptible gap |

### A2. Implementation sketch

Add a `PlaybackLogger` singleton (or just `android.util.Log` with a consistent tag `KARAOKE_PERF`) that timestamps events. Use `SystemClock.elapsedRealtimeNanos()` for precision.

```kotlin
object PlaybackLogger {
    private const val TAG = "KARAOKE_PERF"

    fun event(name: String, vararg kvs: Pair<String, Any>) {
        val ts = SystemClock.elapsedRealtimeNanos() / 1_000_000  // ms
        val pairs = kvs.joinToString(" ") { "${it.first}=${it.second}" }
        Log.d(TAG, "[$ts] $name $pairs")
    }
}
```

Instrument these call sites:

```kotlin
// In switchAudioVariant:
val prepareStartMs = SystemClock.elapsedRealtimeNanos() / 1_000_000
preloadPlayer.prepare()
// ... await loop:
PlaybackLogger.event("audio_ready",
    "prepare_ms" to (now - prepareStartMs),
    "target_pos" to targetPos,
    "audio_pos" to audioPlayer.currentPosition,
    "overshoot_ms" to (audioPlayer.currentPosition - targetPos).coerceAtLeast(0))

// In drift correction loop:
if (drift > 300) {
    PlaybackLogger.event("drift_seek", "drift_ms" to drift, "audio_pos" to audioPos, "video_pos" to videoPos)
    videoPlayer.seekTo(audioPos)
}

// In STATE_READY / STATE_BUFFERING listeners:
PlaybackLogger.event("player_state", "player" to playerName, "state" to stateName,
    "buffered_ms" to player.totalBufferedDuration)
```

Add a `Player.Listener` to each ExoPlayer instance that logs `onPlaybackStateChanged` and `onPlayerError`.

### A3. How to read the logs

```bash
adb logcat -s KARAOKE_PERF
```

From a session with stutter, look for:
- `prepare_ms` values > 300ms → latency is the root cause
- `overshoot_ms` > 0 → lookahead was too short; the switch fired into the past
- `drift_seek` entries → video sync is firing corrective seeks visible to the user
- `STATE_BUFFERING` entries after initial ready → ExoPlayer ran out of buffer

---

## Part B — Fix Strategies

Ordered roughly from simplest to most complex. Start with B1 and B2 (low risk) before B3+.

### B1. Dynamic lookahead based on measured prepare latency

**Problem**: Fixed 500ms lookahead doesn't account for actual network RTT.

**Fix**: Track a rolling EMA of `prepare_ms` from the last N switches and use `max(500, ema * 1.5)` as the lookahead. This adapts automatically to the current network condition.

```kotlin
private var avgPrepareMs = 500L  // EMA, updated on each switch

fun switchAudioVariant(...) {
    val prepareStart = SystemClock.elapsedRealtimeNanos() / 1_000_000
    preloadPlayer.prepare()
    // ... await ...
    val actualPrepareMs = SystemClock.elapsedRealtimeNanos() / 1_000_000 - prepareStart
    avgPrepareMs = (avgPrepareMs * 7 + actualPrepareMs * 3) / 10  // EMA α=0.3
    val nextLookahead = (avgPrepareMs * 1.5).toLong().coerceAtLeast(300L)
    // Store for next call
}
```

**Tradeoff**: Slightly longer gap after a switch on bad networks, but avoids the "switch into the past" problem.

### B2. Replace polling with coroutine-based state listener

**Problem**: `while (state != READY) { delay(50) }` wastes CPU and has up to 50ms of jitter.

**Fix**: Use `ExoPlayer.addListener` with a `CompletableDeferred` to get an exact callback when ready, with a timeout that falls back gracefully.

```kotlin
suspend fun awaitReady(player: ExoPlayer, timeoutMs: Long = 5000L): Boolean {
    val ready = CompletableDeferred<Unit>()
    val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_READY && !ready.isCompleted) ready.complete(Unit)
            if (state == Player.STATE_ENDED || state == Player.STATE_IDLE) ready.cancel()
        }
        override fun onPlayerError(error: PlaybackException) { ready.cancel(error) }
    }
    player.addListener(listener)
    return try {
        withTimeout(timeoutMs) { ready.await(); true }
    } catch (e: Exception) {
        false  // timed out or error
    } finally {
        player.removeListener(listener)
    }
}
```

If `awaitReady` returns `false`, the caller can show an error state rather than hanging forever (which is the current freeze behavior on high-latency links).

### B3. Reduce drift-correction aggression

**Problem**: The 500ms / 300ms drift loop fires corrective `videoPlayer.seekTo()` calls that cause visible stutters in the video even when the audio switch itself was fine.

**Fix**: Widen the threshold, slow down correction, and use a graduated approach:

```kotlin
// Instead of binary seek on any drift > 300ms:
val drift = audioPos - videoPos  // signed
when {
    kotlin.math.abs(drift) < 200 -> { /* fine, do nothing */ }
    kotlin.math.abs(drift) < 1000 -> {
        // Gentle: let ExoPlayer catch up by adjusting playback rate slightly
        videoPlayer.setPlaybackSpeed(if (drift > 0) 1.05f else 0.95f)
    }
    else -> {
        // Hard seek only for large divergence
        videoPlayer.setPlaybackSpeed(1.0f)
        videoPlayer.seekTo(audioPos)
        PlaybackLogger.event("drift_hard_seek", "drift_ms" to drift)
    }
}
```

Also pause drift correction during an in-flight `switchAudioVariant` (while `switchJob != null`) since the audio position is volatile.

### B4. ExoPlayer buffer tuning

**Problem**: Default ExoPlayer buffers (min 15s, max 50s of audio) may cause GC pressure / RAM thrashing on the TV (1-2 GB RAM), while also being insufficient during high-latency variant switches since the buffer doesn't carry over.

**Fix**: Configure `DefaultLoadControl` explicitly. Two profiles:

```kotlin
// Local LAN profile (fast network)
val localLoadControl = DefaultLoadControl.Builder()
    .setBufferDurationsMs(
        minBufferMs = 5_000,
        maxBufferMs = 15_000,
        bufferForPlaybackMs = 1_000,
        bufferForPlaybackAfterRebufferMs = 3_000
    ).build()

// High-latency profile (slow/VPN network)
val remoteLoadControl = DefaultLoadControl.Builder()
    .setBufferDurationsMs(
        minBufferMs = 10_000,
        maxBufferMs = 30_000,
        bufferForPlaybackMs = 3_000,
        bufferForPlaybackAfterRebufferMs = 8_000
    ).build()
```

Detection: use `avgPrepareMs` from B1. If `avgPrepareMs > 1000`, switch to the remote profile on the next player construction (or restart).

### B5. Gapless pre-seek before switch (eliminate the seekTo lag)

**Problem**: In `switchAudioVariant`, `seekTo(targetPos)` is called after `STATE_READY`, but seeking on a fresh stream incurs an extra round-trip (the server must serve from that position).

**Fix**: Instead of seeking to a future position in the new stream, start from the live position at the moment of switch (no seek), accept the ~1-frame gap, and compensate with the existing drift loop. This eliminates the double round-trip (prepare + seek):

```kotlin
// After preloadPlayer reaches STATE_READY:
val switchPos = audioPlayer.currentPosition  // live position now
preloadPlayer.seekTo(switchPos)
// Then await a brief buffer (100ms) after seek before going live
```

Or alternatively: if `overshoot_ms > 0` (we're already past `targetPos`), skip the seek entirely and start from wherever the new stream is buffered, then let the drift loop align.

### B6. Latency probe endpoint (server-side helper)

**Problem**: We have no runtime signal for RTT to the backend. All decisions are inferred from `prepare_ms`, which conflates TCP setup + server processing + first-byte time.

**Fix**: Add a lightweight ping endpoint to the karaoke backend:

```python
# FastAPI
@app.get("/api/ping")
async def ping():
    return {"ts": time.time()}
```

The TV app pings on startup and every 60s, storing a rolling RTT estimate. This gives an explicit network-quality signal that informs B1 (lookahead), B4 (buffer profile), and future UI (show a "weak network" indicator in the HUD).

---

## Recommended Sequence

1. **Ship instrumentation (A)** — one session collecting `adb logcat` during both local and remote play will confirm which metrics spike.
2. **B2 (listener-based await)** — eliminates the hang-forever freeze. Low risk, pure improvement.
3. **B1 (dynamic lookahead)** — directly addresses the "switch into the past" root cause.
4. **B3 (drift correction refinement)** — stops corrective seeks from causing stutter after a clean switch.
5. **B4 (buffer tuning)** — validate with measurements from step 1; tune numbers based on observed `buffered_ms` values.
6. **B5, B6** — optional follow-ups if B1-B4 don't fully resolve remote freezes.
