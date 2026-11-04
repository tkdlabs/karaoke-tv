# bug_03: KEYCODE_STAR intercepted by system shortcut dialog

## Symptom
Pressing the star (⊛) button triggers a system "create shortcut" dialog instead of
cycling lyrics modes in PlayerScreen.

## Root cause
`KEYCODE_STAR` is handled at the system level by Google TV. The next candidate
to try is `KEYCODE_BOOKMARK`.

## Fix
Replace `KEYCODE_STAR` with `KEYCODE_BOOKMARK` in the lyrics toggle handler.
