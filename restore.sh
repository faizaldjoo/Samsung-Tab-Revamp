#!/bin/sh
# Restores every app in removed-packages.txt. Usage: ./restore.sh   (tablet connected via adb)
ADB=${ADB:-$HOME/platform-tools/adb}
for p in $(sed 's/#.*//' "$(dirname "$0")/removed-packages.txt"); do
  printf '%s: ' "$p"; $ADB shell -n cmd package install-existing "$p" </dev/null
done
# Game Optimizer is protected by One UI, so it was disabled instead of removed
$ADB shell -n pm enable com.samsung.android.game.gos </dev/null
# Voice Access was disabled, not removed (re-enable it in Settings > Accessibility afterwards)
$ADB shell -n pm enable com.google.android.apps.accessibility.voiceaccess </dev/null
# Car tweaks: screen-on while charging and 0.35x animations. To undo, uncomment:
# $ADB shell -n settings put global stay_on_while_plugged_in 0 </dev/null
# for k in window_animation_scale transition_animation_scale animator_duration_scale; do $ADB shell -n settings put global $k 0.5 </dev/null; done
