# bug_02: KEYCODE_SETTINGS intercepted by system quick-settings

## Symptom
Pressing the star (⊛) button opens the system quick-settings overlay instead of
cycling lyrics modes in PlayerScreen.

## Root cause
`KEYCODE_SETTINGS` is handled at the system level by Google TV before it reaches
the app. The correct keycode for the star button is `KEYCODE_STAR`.

## Fix
Replace `KEYCODE_SETTINGS` with `KEYCODE_STAR` in the lyrics toggle handler.
