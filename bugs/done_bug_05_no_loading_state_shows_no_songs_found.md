# bug_05: "No songs found" shown during initial load and on server error

## Symptom
On launch, the grid immediately shows "No songs found" while the API call is in
flight. If the server is unreachable, the same message is shown with no indication
of what went wrong.

## Root cause
`songs` in `MainActivity` is initialised to `emptyList()` with no loading/error
distinction. `SongGridScreen` receives a bare `List<Song>` and cannot tell the
difference between loading, empty, and error states.

## Fix
- Introduce a `SongsState` sealed class: `Loading`, `Success(songs)`, `Error(message)`
- `MainActivity` tracks `SongsState` instead of `List<Song>` and captures the
  exception message on failure
- `SongGridScreen` receives `SongsState` and renders:
  - `Loading` → splash screen with music artwork and "Loading…" text
  - `Error` → error message with server URL hint
  - `Success` with no playable songs → "No songs found"
  - `Success` with songs → the grid (existing behaviour)
