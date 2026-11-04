package com.tkdlabs.karaoke.util

import android.os.SystemClock
import android.util.Log

/**
 * Lightweight structured logger for playback performance events.
 *
 * Read with:  adb logcat -s KARAOKE_PERF
 *
 * Each line is: [elapsed_ms_since_boot] event_name key=value ...
 */
object PlaybackLogger {
  private const val TAG = "KARAOKE_PERF"

  /** Emit a named event with optional key=value pairs. */
  fun event(name: String, vararg kvs: Pair<String, Any>) {
    val ts = nowMs()
    val pairs = if (kvs.isEmpty()) "" else " " + kvs.joinToString(" ") { "${it.first}=${it.second}" }
    Log.d(TAG, "[$ts] $name$pairs")
  }

  /** Return the current monotonic time in milliseconds (for use with elapsedMs). */
  fun startTimer(): Long = nowMs()

  /** Return milliseconds elapsed since the value returned by startTimer(). */
  fun elapsedMs(startMs: Long): Long = nowMs() - startMs

  private fun nowMs(): Long = SystemClock.elapsedRealtimeNanos() / 1_000_000L
}
