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
  `wireframe.html` is the flat mid-fidelity mockup of the same layout, used to settle changes cheaply before they go into glass.html.
- Canvas is 1340x800 landscape. Sections: Rail, Map, Today (flip clock, date, location, weather + next 4 hours), Car (speedometer + 4 stats),
  Now playing (cassette: playing app's icon + title/artist on the label, album art or station logo spins as the right reel, tape moves
  left to right with progress, controls on the bottom bridge; live radio = LIVE badge, tape still, skip hidden if unsupported; panel stays
  neutral glass), Sliders. `cassette.html` is the standalone cassette prototype shown in the home layout.
  `weather.html` is the standalone weather card (current conditions only: temp, condition - place, H/L, a soft 3D cloud/sun/rain drawn
  once to a canvas per condition) in the bottom-left slot left of a narrower Now playing; home screen not changed yet (on hold).
  `weather.html?art=icons` shows the same card with Weatherglass TV's glass icons instead, animated per condition with CSS
  transform/opacity only (sun glow breathes, rain drops fall, lightning flash, gentle bob) (`vendor/weatherglass/`, copied from
  faizaldjoo/weatherglass-tv app/assets/weather at 94a2d9f, MIT); night swaps sun icons for moon ones. Controls panel slides out from the rail; All apps page cross-fades over home.
  Published preview (icons by default, in-page switch to the canvas art): https://claude.ai/artifact/Qxrv1da88QtyLbf8gQikSc
- Rail is CarPlay-style, on the right by default (setting: left/right): status pill on top (tap = Controls; signal, Bluetooth, battery %,
  warning icons only when not charging or too hot), the 4 most recently opened apps (open app marked), all-apps / Home button at the bottom.
  One `APPS` list in the JS drives the rail, the all-apps page and the navigation setting. Settings persist in localStorage (`carhome`).
- Prototype-only taps: clock = next wallpaper, Car source chip = OBD/GPS, music source chip = next app (Spotify / YT Music / radio),
  battery and temperature cards = preview warning states.
- Next step: user is drawing a wireframe to revamp some sections; edit only the sections they change.
- Build plan once the design is final: plain Java Android app, no Gradle (javac + d8 + aapt2 + apksigner from the Android SDK),
  HOME intent filter. Map = own OSM raster tile view + Waze/Google Maps turn info from a NotificationListenerService.
  The blue route line in glass.html is a hand-drawn placeholder kept for looks; nav apps do not share the route, so the app shows
  live position + turn card + ETA only (tap opens the nav app for the full route).
  Now playing via MediaSessionManager, weather from Open-Meteo, sliders via AudioManager / Settings.System.
  Blur the wallpaper once at set time instead of live blur (weak GPU).
  Flip clock (Today panel): one custom View, digits drawn to Canvas, a single minute-aligned callback (ACTION_TIME_TICK),
  flip = short rotationX ValueAnimator on the changed digits only; nothing runs between minutes.
- Car panel: OBD (ELM327 over Bluetooth) shows car speed, RPM, coolant, fuel, battery V; without OBD it shows GPS speed,
  heading, trip km, drive time, average. Trip rules: distance summed from fixes with accuracy <= 20 m, drive time counts only
  while speed > 3 km/h, average = trip / drive time. Trip auto-resets after 20 min stationary; manual reset = long-press the
  reset button next to the source chip. Persist counters once a minute.
- Navigation: Controls has "Navigation app: Waze / Google Maps" (default Waze). The map panel tap and the turn-card
  notification parser follow it. "Open <nav app> when the car starts" (default on): on ACTION_POWER_CONNECTED / wake with
  the launcher in front, start the nav app full screen; if the user leaves it within 10 s, don't reopen it that trip.
- Performance goal: the launcher must feel faster than stock One UI on this tablet. Animate only transform/opacity,
  no per-frame timers, no live blur, keep idle CPU at zero between events.
- Preview: `python3 -m http.server 8765 --bind 0.0.0.0 --directory launcher-prototype`, open `http://<computer-ip>:8765/glass.html` on the tablet.

## Screensaver prototype
- `launcher-prototype/screensaver.html`: bbot face screensaver. bbot (github.com/bwndapp/bbot, MIT) is SVG + springs, no WebGL;
  its bundle is vendored in `launcher-prototype/vendor/bbot/` (from commit 93b2080). The page adds mood cycling, night mode
  (sleepy/sleep 22:00-06:00), burn-in drift, clock and colour swatches. Plan: Android DreamService with a WebView on the same files.
- Triggers: touch gestures (tap, double/triple tap, long press, rub, swipe) and car events via `bbot.event(name)`
  (music-play/pause, turn-left/right, arrived, call, message, charging, battery-low, speeding); `?debug` shows event buttons.
  Android side: DreamService.setInteractive(true), listeners call `evaluateJavascript("bbot.event('...')")`.
- Horn detector (`?mic`): mic FFT, tonal peak 300-700 Hz + harmonic, +10 dB over adaptive background (+8 dB while music-play,
  4 s grace after music starts), steady 300 ms -> `horn` (scared then annoyed). Tuned on synthetic audio only; tune in the car with `?debug`.
  Mic needs a secure origin: `adb reverse tcp:8765 tcp:8765`, open http://localhost:8765/screensaver.html?mic&debug.
- GPS director (`?gps` or `bbot.gps({speed,heading,lat,lon,accuracy})`): pull-away, hard brake/accel, sharp turn (look into it),
  waiting at a light -> bored/sleepy, highway cruise, speeding (>110), long drive, home geofence (`bbot.setHome()`). Thresholds in `GPS`;
  `?debug` has "simulate drive (5x)". Android side: LocationManager fixes piped to `bbot.gps(...)` at 1 Hz.
- Now playing: `bbot.player.set({title, artist, playing})` shows a marquee bottom-right, no controls. Clock (light 60px, small AM/PM) + date + weather
  centred under the face; weather from Open-Meteo at the last GPS fix (fallback KL) or pushed via `bbot.weather.set({temp, text})`.
  Browser build keeps a local-file queue ("add songs", ID3 title/artist) through the analyser; face nods on bass onsets while music-play is on.
  Android side: MediaSessionManager -> active MediaController metadata/playback state -> player.set().
- Look: eyes only (mouth off, no head ring), off-black background. Published test page (same file, debug bar always on): https://claude.ai/artifact/9XmBNmuNQU7BJHVGpu4E5T

## Not decided / parked
- Combined replacement for Easy Slider + Muviz Edge (2-finger volume / 3-finger brightness edge zone + screensaver). User paused it.
