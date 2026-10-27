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
- Canvas is 1340x800 landscape. Sections: Rail, Map, Today (HTC Sense style flip clock + current weather, see today.html), Car (speedometer + 4 stats),
  Now playing (cassette: title/artist centred on the label, the playing app as a pill (white logo + name) under the progress bar;
  tapping the pill or the tape window opens the app; the title still and centred when it fits, when longer it scrolls the YouTube Music way (rests 2.5 s at the start, slides at ~45 px/s, repeats; stops while paused), album art or station logo spins as the right reel, tape moves
  left to right with progress, controls on the bottom bridge; live radio = LIVE badge, tape still, skip hidden if unsupported; panel stays
  neutral glass), Sliders. `cassette.html` is the standalone cassette prototype shown in the home layout.
  `weather.html` is the standalone weather card (current conditions only: temp, condition - place, H/L, a soft 3D cloud/sun/rain drawn
  once to a canvas per condition) in the bottom-left slot left of a narrower Now playing; superseded by the Today panel (weather merged into the clock), kept for reference.
  `weather.html?art=icons` shows the same card with Weatherglass TV's glass icons instead, animated per condition with CSS
  transform/opacity only (sun glow breathes, rain drops fall, lightning flash, gentle bob) (`vendor/weatherglass/`, copied from
  faizaldjoo/weatherglass-tv app/assets/weather at 94a2d9f, MIT); night swaps sun icons for moon ones. Controls panel slides out from the rail; All apps page cross-fades over home.
  Published preview (icons by default, in-page switch to the canvas art): https://claude.ai/artifact/Qxrv1da88QtyLbf8gQikSc
  `today.html` is the Today panel redone as an HTC Sense style flip clock: two white flip cards (HH, MM; only the changed card flips,
  minute-aligned) on a dark weather slab, animated Weatherglass icon overlapping both, place + temp left, day + H/L right,
  condition + location + updated time below. Merges clock and weather, so the bottom row keeps the full-width player.
  It is the live Today panel in glass.html (same CSS/markup/JS, classes scoped under .today; flip cards are .fcard because
  .card is the Controls tile class; night mode dims the card paper).
  Location: slab shows the regency/city (Geocoder subAdminArea, "Kabupaten"/"Kota" prefix stripped), footer shows
  "district, regency" (locality + subAdminArea) with an ellipsis; a long slab name shrinks 17 -> 15 px, then wraps to 2 lines.
  Tap clock = flip forward a minute, tap place = next sample location, tap panel = next weather (prototype only).
  Published preview: https://claude.ai/artifact/Uy8NJRbmhcygbKFidviNQT
- Rail is CarPlay-style, on the right by default (setting: left/right): status pill on top (tap = Controls; battery % (number only, first), signal, Bluetooth,
  warning icons only when not charging or too hot), settings gear (opens Controls), screensaver button (eyes icon: fades to black and
  opens the screensaver; in the prototype screensaver.html?from=home, where a single tap goes back home), then 4 app slots:
  pinned apps first (pin badge, order pinned, `S.pins`, default Waze + Spotify), a hairline, then the most recent other apps
  (open app marked). Hold an app in the rail or on the all-apps page to pin/unpin it. All-apps / Home button at the bottom.
  One `APPS` list in the JS drives the rail, the all-apps page and the navigation setting. Settings persist in localStorage (`carhome`).
- Prototype-only taps: Today panel = next weather, Today place name = next location, Car source chip = OBD/GPS, music source: hold the app pill for the next app, or glass.html?src=ytmusic|spotify|prambors (default YT Music),
  battery and temperature cards = preview warning states.
- Next step: user is drawing a wireframe to revamp some sections; edit only the sections they change.
- Build plan once the design is final: plain Java Android app, no Gradle (javac + d8 + aapt2 + apksigner from the Android SDK),
  HOME intent filter. Map = own OSM raster tile view + Waze/Google Maps turn info from a NotificationListenerService.
  The blue route line in glass.html is a hand-drawn placeholder kept for looks; nav apps do not share the route, so the app shows
  live position + turn card + ETA only (tap opens the nav app for the full route).
  Now playing via MediaSessionManager, weather from Open-Meteo (glass.html fetches it live for Sumedang Utara, -6.842, 107.921, the preview location; the app uses the last GPS fix), sliders via AudioManager / Settings.System.
  Blur the wallpaper once at set time instead of live blur (weak GPU).
  Glance-legible text: secondary labels at least 14px and weight 500, --muted 84% / --faint 68% white, text-shadow 0 1px 2px
  rgba(0,0,0,0.35); the glass carries a faint dark wash under its white sheen so labels hold up over a pale sky.
  Glass is styled after iOS 26 Liquid Glass (approximation): clear rather than frosted (blur 7px, saturate 1.9, brightness 0.8), lens edges
  from a per-panel two-pass SVG displacement map (lensEdges(): x then y pass, each neutral in the middle; one combined map
  shears in Chromium), rim lit top-left and bottom-right (`--rim` for round controls). Toggles: off = clear disc; on =
  Wi-Fi/Bluetooth blue fill, data/hotspot green fill, others white disc with coloured glyph. Sliders: clear pill, white fill,
  dark grey speaker / sun glyphs.
  Wallpaper (Controls header > Wallpaper): picker page in the home cell; "From gallery" (an <input type=file>: the browser's
  file chooser here, the Android photo picker via WebChromeClient.onShowFileChooser in the app), the user's photo, then
  8 built-in photos (Picsum ids here; bundled screen-sized in the app), then 6 CSS gradients (Midnight, Ember, Deep forest,
  Ocean, Dusk rose, Graphite: dark layered glows + a faint grain against banding; no files, no shade added). Tap = preview on the real home screen with a
  Set / Cancel bar. Gallery photos are centre-cropped to 1340x800 JPEG once (~200 KB; localStorage 'carhome-wall', app
  files on Android). Each wallpaper's brightness is measured once; brighter photos get extra shade (--dim on .wall::after).
  Glass effect toggle (Controls, under Hotspot; saved as S.glass, default on). Off = clear panels: the tinted see-through
  look without backdrop-filter, the main GPU cost on the tablet. The Controls drawer turns solid dark when it is off.
  Lens edges are off by default (glass.html?lens to preview): live they measured 28 fps vs 60 without (frames up to 118 ms),
  since every animation re-runs the filter chain. Android plan, baked glass: when the wallpaper is set, render each panel's
  plate once (wallpaper crop, blur, saturate, lens displacement, rim) into one screen-sized ARGB bitmap (~4 MB) and draw it
  as a plain image; zero per-frame cost. Only the Controls drawer blurs live, once, as it opens.
  Flip clock (Today panel): one custom View, digits drawn to Canvas, a single minute-aligned callback (ACTION_TIME_TICK),
  flip = short rotationX ValueAnimator on the changed digits only; nothing runs between minutes.
- Car panel: OBD (ELM327 over Bluetooth) shows car speed, RPM, coolant, fuel, battery V; without OBD it shows GPS speed,
  heading, trip km, drive time, average. Trip rules: distance summed from fixes with accuracy <= 20 m, drive time counts only
  while speed > 3 km/h, average = trip / drive time. Trip auto-resets after 20 min stationary; manual reset = long-press the
  reset button next to the source chip. Persist counters once a minute.
- Navigation: Controls has "Navigation app: Waze / Google Maps" (default Waze). The map panel tap and the turn-card
  notification parser follow it. "Show <nav app> in the map panel" (default on), decided 2026-10-27:
  1. Preferred: on ACTION_POWER_CONNECTED / wake with the launcher in front, start the nav app as a Samsung pop-up
     (freeform) window with launch bounds = the map panel's screen rect, so the rest of home stays visible. An expand
     control makes it full screen; Home returns to the panel. Android cannot embed another app's UI, this is the closest.
     Needs freeform enabled once over ADB (settings global enable_freeform_support 1, force_resizable_activities 1);
     test first with `am start --windowingMode 5` + `am task resize <task> l t r b` before building it.
  2. Fallback (freeform refused, or the nav app looks wrong at that size): the panel keeps the OSM map with live position,
     turn card and ETA parsed from the nav app's notification; tap opens the nav app full screen.
  If the user leaves the nav app within 10 s, don't reopen it that trip.
- Performance goal: the launcher must feel faster than stock One UI on this tablet. Animate only transform/opacity,
  no per-frame timers, no live blur, keep idle CPU at zero between events.
- Preview: `python3 -m http.server 8765 --bind 0.0.0.0 --directory launcher-prototype`, open `http://<computer-ip>:8765/glass.html` on the tablet.

## Screensaver prototype
- `launcher-prototype/screensaver.html`: bbot face screensaver. bbot (github.com/bwndapp/bbot, MIT) is SVG + springs, no WebGL;
  its bundle is vendored in `launcher-prototype/vendor/bbot/` (from commit 93b2080). The page adds mood cycling, night mode
  (sleepy/sleep 22:00-06:00), burn-in drift and the clock (face colour fixed, no colour picker). Plan: Android DreamService with a WebView on the same files.
- Triggers: touch gestures (tap, double/triple tap, long press, rub, swipe) and car events via `bbot.event(name)`
  (music-play/pause, turn-left/right, arrived, call, message, charging, battery-low, speeding); `?debug` shows event buttons.
  Android side: DreamService.setInteractive(true), listeners call `evaluateJavascript("bbot.event('...')")`.
- Horn detector (`?mic`): mic FFT, tonal peak 300-700 Hz + harmonic, +10 dB over adaptive background (+8 dB while music-play,
  4 s grace after music starts), steady 300 ms -> `horn` (scared then annoyed). Tuned on synthetic audio only; tune in the car with `?debug`.
  Mic needs a secure origin: `adb reverse tcp:8765 tcp:8765`, open http://localhost:8765/screensaver.html?mic&debug.
- GPS director (`?gps` or `bbot.gps({speed,heading,lat,lon,accuracy})`): pull-away, hard brake/accel, sharp turn (look into it),
  waiting at a light -> bored/sleepy, highway cruise, speeding (>110), long drive, home geofence (`bbot.setHome()`). Thresholds in `GPS`;
  `?debug` has "simulate drive (5x)". Android side: LocationManager fixes piped to `bbot.gps(...)` at 1 Hz.
- Now playing: `bbot.player.set({title, artist, playing})` shows a 640px marquee bottom-centre with prev / play-pause / next above it (64/80/64px round glass buttons; presses call
  `bbot.player.onControl(cmd)` with 'prev' | 'toggle' | 'next', which Android maps to MediaController transport controls;
  paused = line stays, dimmed, not scrolling), scrolling at ~45 px/s for any title length (prototype loads a long sample song; ?nosong hides it). Top-left: 24-hour clock (Sofia Sans Extra Condensed Bold 112px, stand-in for the iOS compressed SF clock) with the date
  under it, a hairline, then the weather to the right (static Weatherglass icon + temp/condition, place under it) (face centred on screen); weather from Open-Meteo
  at the last GPS fix (fallback Sumedang Utara) or pushed via `bbot.weather.set({temp, text, code, place})` (code = WMO weather code; place from
  the Android Geocoder, district + city; the browser has no geocoder, so after a GPS fix it hides the place instead of showing the fallback).
  Browser build keeps a local-file queue ("add songs", ID3 title/artist) through the analyser; face nods on bass onsets while music-play is on.
  Android side: MediaSessionManager -> active MediaController metadata/playback state -> player.set().
- Look: eyes only (mouth off, no head ring), off-black background. Published test page (same file, debug bar always on): https://claude.ai/artifact/9XmBNmuNQU7BJHVGpu4E5T

## Not decided / parked
- Combined replacement for Easy Slider + Muviz Edge (2-finger volume / 3-finger brightness edge zone + screensaver). User paused it.
