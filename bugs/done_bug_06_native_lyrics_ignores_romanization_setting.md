# Bug 06: Native lyrics always shows romanization regardless of lyrics mode setting

## Summary

When using native (LRC) lyrics rendering, romanization is always shown for Korean songs
even when the lyrics mode is set to "Lyrics" (original language only). The `romanize`
flag in the API call is hardcoded to `true` instead of being derived from the
`defaultLyricsMode` setting.

## Steps to reproduce

1. In Settings, set Lyrics Mode to "Lyrics" (mode index 1 — original language).
2. Set Lyrics Source to "Native (LRC)".
3. Play a Korean song.
4. Observe that romanized text still appears below the Korean lyrics.

## Expected behaviour

- Lyrics mode "Lyrics" (index 1): show original Korean only, no romanization.
- Lyrics mode "Romanized" (index 2): show original Korean + romanized text.

## Root cause

`PlayerScreen.kt`, `LaunchedEffect(song.id, nativeLyrics)` block:

```kotlin
val response = api.getLyrics(song.id, romanize = true)  // hardcoded
```

Should be:

```kotlin
val response = api.getLyrics(song.id, romanize = defaultLyricsMode == 2)
```

`defaultLyricsMode == 2` corresponds to `Settings.LYRICS_MODES[2]` ("Romanized").
