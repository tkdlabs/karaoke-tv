# bug_04: No usable remote button for lyrics toggle — map to center/OK

## Symptom
All attempted keycodes for the star button (KEYCODE_SETTINGS, KEYCODE_STAR,
KEYCODE_BOOKMARK) are intercepted by the Google TV system before reaching the app.

## Root cause
The ⊛ button on an Android TV device is fully owned by the system. No app-level
intercept is possible for it.

## Fix
Map lyrics toggle to KEYCODE_DPAD_CENTER (center/OK button), which is unassigned
in PlayerScreen and reliably reaches the app. Remove KEYCODE_BOOKMARK.
Also remove the debug keycode logging added during investigation.
Update HUD hint to show OK instead of ⊛.
