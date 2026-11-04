# feat_02: Map star button (KEYCODE_SETTINGS) to lyrics mode toggle

## Description

The remote for an Android TV device has a star (⊛) button mapped to `KEYCODE_SETTINGS`.
Add it as an additional trigger for cycling lyrics modes in `PlayerScreen`,
alongside the existing `KEYCODE_PROG_RED` and `KEYCODE_L` (keyboard) bindings.

## Acceptance criteria

- [ ] `KEYCODE_SETTINGS` cycles lyrics mode in `PlayerScreen` (same behaviour as `KEYCODE_PROG_RED`)
- [ ] `KEYCODE_L` retained for keyboard/ADB testing
- [ ] HUD controls hint updated to show the star button label
