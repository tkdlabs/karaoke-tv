# Karaoke TV

[![Android CI](https://github.com/tkdlabs/karaoke-tv/actions/workflows/build.yml/badge.svg)](https://github.com/tkdlabs/karaoke-tv/actions/workflows/build.yml)

An Android TV client for the [tkdlabs/karaoke](https://github.com/tkdlabs/karaoke)
backend. Browse your karaoke library, play videos with pitch shifting and
vocal-level control, and sing along with synced lyrics.

## Features

- Song library with thumbnails and durations
- Pitch shifting from -3 to +3 semitones
- Vocal level switching: Off, Guide (25%), Full (100%)
- Two lyrics modes:
  - Pre-rendered video overlay (`overlay_lyrics` / `overlay_romanized`)
  - Native LRC rendering, with optional Korean romanization
- Seamless audio variant switching with a preloaded second player
- Structured playback diagnostics through `adb logcat`
- First-run backend setup; no hardcoded server address

## Requirements

- Android TV 11+ (API 30)
- A running [karaoke backend](https://github.com/tkdlabs/karaoke) on the same
  network, port `8001` by default
- Songs must have stems and audio variants generated in the backend before they
  appear as playable in the app

To build:

- JDK 17
- Android SDK 34 (`platforms;android-34`, `build-tools;34.0.0`)

## Build and install

```bash
./gradlew assembleDebug

adb connect <TV_IP>:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or open the project in Android Studio.

A convenience wrapper is available:

```bash
./scripts/deploy.sh <TV_IP>
```

Release builds are unsigned by default. Add your own signing configuration before
distributing a release APK.

## Configuration

The app has no default backend address. On first launch, open **Settings** and
enter the backend URL, for example:

```text
http://192.168.1.50:8001
```

The Android emulator can reach a backend running on the host machine at
`http://10.0.2.2:8001`.

Settings are stored with Android DataStore and can be changed at any time.

## Remote controls

| Input | Action |
|---|---|
| D-pad up / down | Pitch up / down |
| D-pad left / right | Vocal level: Off → Guide → Full |
| OK / red / `L` | Cycle lyrics mode (video overlay mode only) |
| Back | Leave player / screen |

The on-screen HUD shows the current pitch, vocal level, and lyrics mode, then
hides after a few seconds.

## Backend API

The client uses the following karaoke backend endpoints:

```text
GET /api/songs
GET /api/songs/{id}/thumbnail
GET /api/songs/{id}/video/{variant}        # original | overlay_lyrics | overlay_romanized
GET /api/songs/{id}/variants/{pitch}/{vocal}  # pitch -3..3, vocal 0|25|100
GET /api/songs/{id}/lyrics?romanize=<bool>
```

## Architecture

- Kotlin + Jetpack Compose for TV
- Media3 ExoPlayer: one muted video player, two audio players for variant
  preloading and switching
- Retrofit + Moshi for the backend API
- DataStore Preferences for settings
- Coil for thumbnails

Playback keeps audio as the timing source. Video drift is corrected with small
playback-speed nudges; large divergence falls back to a seek.

## Security

The app enables cleartext HTTP so it can talk to a backend on a local network.
Use it only on a trusted network. If the backend is exposed outside your LAN,
put it behind HTTPS and configure the app with an `https://` URL.

## Development

- `bugs/` and `features/` contain project notes and history.
- CI builds the debug APK on every push and pull request.
- Playback diagnostics are available with:

  ```bash
  adb logcat -s KARAOKE_PERF
  ```

## License

MIT — see [LICENSE](LICENSE).

This project is a client for the karaoke backend and does not include media,
lyrics, or model weights. You are responsible for the content you play and for
clearing the necessary rights.
