# bug_01: Screensaver interrupts ExoPlayer video playback

## Symptom
On Android 14 TV streaming boxes, the system screensaver activates during karaoke video
playback, interrupting the ExoPlayer session. The screen goes dark or shows the screensaver
after the device's idle timeout, even while a video is actively playing.

## Root cause
`PlayerScreen.kt` does not set `FLAG_KEEP_SCREEN_ON` on the Activity window.
Neither `AndroidManifest.xml` nor `MainActivity.kt` contains any wakelock or
keep-screen-on logic. ExoPlayer does not suppress the screensaver on its own.

## Fix
Add a `DisposableEffect` in `PlayerScreen.kt` scoped to the player being active:

```kotlin
val view = LocalView.current
DisposableEffect(Unit) {
    val window = (view.context as Activity).window
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    onDispose {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
```

This suppresses the screensaver only while the player screen is composed, and
clears the flag on exit — no extra permissions required.
