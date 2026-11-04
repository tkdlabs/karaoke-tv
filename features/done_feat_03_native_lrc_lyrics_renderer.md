# feat_03: Native LRC lyrics renderer in PlayerScreen

## Description

Add a native lyrics rendering mode to the karaoke-tv Android TV app as an
alternative to the pre-rendered video overlay. This avoids the video stream
swap lag entirely: only the plain video stream is used, and lyrics are drawn
in the app using data fetched from the new backend lyrics API (karaoke feat_33).

This mode is configurable per-session: the user can toggle it in Settings and
switch back to video-overlay mode at any time.

## Motivation

Switching video streams (e.g. plain → lyrics overlay) causes a buffering stall
on lower-end streaming boxes (e.g. a low-end Android TV box). Native rendering eliminates
the stream swap — the video stays on the plain stream and lyrics are composited
in the app.

## Visual design

Mirrors the style of the pre-rendered overlay:

- **Current/upcoming line**: large font, **yellow** (`#FFD700`)
- **Romanization** (when available): smaller font below the main line, **cyan** (`#00FFFF`)
- Lines are positioned at the bottom of the screen (above the HUD)
- Previous line fades or is hidden; only the active line is prominent

## API contract

Calls `GET /api/songs/{id}/lyrics?romanize=true` (karaoke feat_33).

Response shape:
```json
{
  "has_lyrics": true,
  "lines": [
    { "time_ms": 1000, "text": "안녕하세요", "romanized": "annyeonghaseyo" },
    { "time_ms": 5400, "text": "Hello world",  "romanized": null }
  ]
}
```

## Settings

Add a **Lyrics Source** setting alongside the existing Vocal and Lyrics Mode settings:

| Option | Behaviour |
|--------|-----------|
| `Video overlay` (default) | Existing behaviour — stream pre-rendered overlay video |
| `Native (LRC)` | Fetch LRC from API, render natively; always use plain video stream |

Setting is persisted in DataStore like existing settings.

When `Native (LRC)` is selected:
- `PlayerScreen` loads the plain video stream regardless of lyrics mode
- Lyrics are fetched once on song load and rendered as a composable overlay
- The lyrics/video toggle controls (OK button, red button) are hidden from HUD
  since they don't apply in this mode

## Implementation notes

- Fetch lyrics in a `LaunchedEffect(song.id)` alongside the player setup
- Track current active line by comparing `audioPlayer.currentPosition` against
  `time_ms` — can reuse the existing 500 ms sync loop
- `LyricsLine` data class: `(timeMs: Long, text: String, romanized: String?)`
- Add `GET /api/songs/{id}/lyrics` to `KaraokeApi.kt` (Retrofit interface)
- If `has_lyrics` is false, native mode silently shows no overlay (no error)

## Acceptance criteria

- [ ] Backend feat_33 (lyrics API) is implemented
- [ ] `Lyrics Source` setting added to `SettingsScreen` (Video overlay / Native LRC)
- [ ] Setting persisted in DataStore and read in `MainActivity`
- [ ] In native mode, `PlayerScreen` always uses plain video stream
- [ ] Active lyric line rendered in yellow at bottom of screen
- [ ] Romanization rendered in cyan smaller font below when present
- [ ] Active line updates in sync with audio playback (≤500 ms lag)
- [ ] Lyrics/video toggle controls hidden from HUD in native mode
- [ ] Songs with no stored lyrics show no overlay (no crash or error)
- [ ] Toggling the setting and returning to the grid applies the new mode immediately
