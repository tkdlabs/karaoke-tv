# CLAUDE.md — karaoke-tv

Android TV client for the [tkdlabs/karaoke](https://github.com/tkdlabs/karaoke)
backend. Built with Kotlin + Jetpack Compose for TV. Plays karaoke videos with
pitch shifting, vocal-level switching, and synced lyrics.

## Working conventions

- Work on a single bug or feature at a time. Do not mix changes across multiple
  items in one commit.
- Commit after a feature or bug is completed and verified.
- Each commit should correspond to exactly one feature or bug fix.
- File new work in `features/` or `bugs/` before implementing it.

### Naming

- Features: `feat_NN_short_description.md` (rename to `done_feat_NN_...` when done)
- Bugs: `bug_NN_short_description.md` (rename to `done_bug_NN_...` when fixed)
- Increment `NN` from the highest existing number.

## Requirements

- JDK 17
- Android SDK 34 (`platforms;android-34`, `build-tools;34.0.0`)
- `adb` (Android platform tools)
- A running [karaoke backend](https://github.com/tkdlabs/karaoke) on the same
  network, listening on port 8001 by default

## Build & install

```bash
# Debug APK
./gradlew assembleDebug

# Install on a TV / emulator
adb connect <TV_IP>:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

There is no hardcoded backend address. On first launch, open **Settings** and
enter the backend URL (for example `http://192.168.1.50:8001`). The Android
emulator can reach a host machine via `http://10.0.2.2:8001`.

## Backend API used

The app expects the karaoke backend on the configured base URL:

- `GET /api/songs` — song library
- `GET /api/songs/{id}/video/{variant}` — video stream
  (`original`, `overlay_lyrics`, `overlay_romanized`)
- `GET /api/songs/{id}/variants/{pitch}/{vocal}` — audio variant stream
  (pitch `-3..3`, vocal `0|25|100`)
- `GET /api/songs/{id}/lyrics?romanize=<bool>` — native LRC lyrics
- `GET /api/songs/{id}/thumbnail` — song artwork

## Remote controls

| Input | Action |
|---|---|
| D-pad up / down | Pitch up / down |
| D-pad left / right | Vocal level: Off → Guide → Full |
| OK / red / `L` | Cycle lyrics mode (video overlay mode only) |
| Back | Leave player / screen |

## Architecture

- `MainActivity.kt` — navigation, Retrofit client, DataStore settings
- `ui/SongGridScreen.kt` — song library and first-run setup state
- `ui/PlayerScreen.kt` — muted video player + two audio ExoPlayer instances for
  seamless variant switching, drift correction, native lyric overlay
- `ui/SettingsScreen.kt` — backend URL, default vocal level, lyrics defaults
- `data/Settings.kt` — DataStore preferences and URL normalization
- `data/KaraokeApi.kt` — Retrofit interface and stream URL helpers
- `data/Song.kt` — API models
- `util/PlaybackLogger.kt` — structured playback diagnostics

`PlayerScreen` keeps video muted and drives audio separately. It preloads the
next audio variant, crossfades at a future playback position, and nudges video
playback speed for small audio/video drift instead of seeking constantly.

## Notes

- Cleartext HTTP is enabled intentionally so the app can talk to a LAN backend.
  Use only on a trusted network; HTTPS is recommended for remote access.
- The TV build has no microphone permission. Voice-input hardware on most TV
  boxes is not exposed to apps; future pitch-scoring work should assume a USB
  microphone.
- CI builds the debug APK with JDK 17 and Android SDK 34 (`.github/workflows/build.yml`).
