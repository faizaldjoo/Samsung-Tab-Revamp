# Tab A7 Lite car head unit

Samsung Galaxy Tab A7 Lite LTE (SM-T225, Android 14 / One UI 6.1, ~2.8 GB RAM) used as a car head unit.
Driven over ADB from a computer on the same Wi-Fi (Wireless debugging; pair once per computer).

## State of the tablet (as of 2026-10-25)
- Debloated with `pm uninstall --user 0`: every package in `removed-packages.txt` (50 apps).
- Disabled instead of removed: Game Optimizer (`com.samsung.android.game.gos`, One UI restores it) and Voice Access.
- Car tweaks: `stay_on_while_plugged_in=7`, animation scales 0.35, Google TTS as default,
  battery-optimisation exemptions for Waze, CarbitLink, Smart Launcher, music apps, Easy Slider, Muviz Edge, Brightness Dimmer, Volume Button.
- `restore.sh` undoes all of it (`ADB=/path/to/adb ./restore.sh`).
- Kept on purpose: Easy Slider (volume/brightness overlay), Muviz Edge (screensaver; installed from a modded APK, flagged to the user),
  Smart Launcher (current home app), Android-side telephony/IMS (LTE model).

## Custom launcher (in progress)
- `launcher-prototype/glass.html` is the chosen design (option B, Liquid Glass over a photo wallpaper). `index.html` is option A (dark).
- Canvas is 1340x800 landscape. Sections: Rail, Map, Now playing, Glance (speed + weather), App grid, Sliders.
- Next step: user is drawing a wireframe to revamp some sections; edit only the sections they change.
- Build plan once the design is final: plain Java Android app, no Gradle (javac + d8 + aapt2 + apksigner from the Android SDK),
  HOME intent filter. Map = own OSM raster tile view + Waze/Google Maps turn info from a NotificationListenerService.
  Now playing via MediaSessionManager, weather from Open-Meteo, sliders via AudioManager / Settings.System.
  Blur the wallpaper once at set time instead of live blur (weak GPU).
- Preview: `python3 -m http.server 8765 --bind 0.0.0.0 --directory launcher-prototype`, open `http://<computer-ip>:8765/glass.html` on the tablet.

## Not decided / parked
- Combined replacement for Easy Slider + Muviz Edge (2-finger volume / 3-finger brightness edge zone + screensaver). User paused it.
