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
- ADB (2026-10-27): this Mac is paired; the tablet connects as `adb-R9RRC0242QX-1GVz2Z._adb-tls-connect._tcp` (192.168.1.115).
  Screen 800x1340 portrait-native, density 213 (so the WebView's CSS viewport is about 1006x601; the page scales to fit).
  Installed apps for the APPS list: Waze com.waze, YT Music com.google.android.apps.youtube.music, Spotify com.spotify.music,
  Apple Music com.apple.android.music, Prambors com.pandacode.prambors.prambors, CarbitLink net.easyconn.carman.wws (likely),
  Smart Launcher ginlemon.flowerfree. Google Maps is a system app. WhatsApp is not installed. Freeform support is off.
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
  8 built-in photos (vendor/walls: 1600x1000 + 480x288 thumbnails, Unsplash via Picsum), then 6 CSS gradients (Midnight, Ember, Deep forest,
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
- Navigation: Controls has "Navigation app: Waze / Google Maps" (default Waze). Decided 2026-10-27 after testing the
  pop-up idea on the tablet (see "Pop-up limit" below): the map panel is our own live map, and the nav app opens full screen.
  - Map (app): the Dark map, the same as Lite's (glass.html vmUse / vmMove since 2026-10-28; puck at 66%, so top
    padding 32%), following the GPS fix. Darkened OSM tiles at zoom 16 only as the fallback. No route line (nav apps do
    not share it); the browser prototype keeps its sample route over Semanggi, Jakarta.
  - Turn card + ETA (app): from the nav app's notification via MediaListener (title / text / sub / big text): card shows
    title + text, arrow guessed from the words (EN + ID), ETA row from "12 mnt", "4,2 km", "17:31" patterns. Hidden when
    not navigating; then the panel says "Tap for Waze". Real Waze notification wording still to be checked on a drive.
  - Map tap opens the nav app full screen; Home returns to the dashboard. "Open <nav app> when the car starts" was
    removed 2026-10-28 (user): plugging in only ever shows home.
- Performance goal: the launcher must feel faster than stock One UI on this tablet. Animate only transform/opacity,
  no per-frame timers, no live blur, keep idle CPU at zero between events.
- Preview: `python3 -m http.server 8765 --bind 0.0.0.0 --directory launcher-prototype`, open `http://<computer-ip>:8765/glass.html` on the tablet.

## Car Home Lite (launcher-prototype/lite.html, added 2026-10-27)
- 2026-10-27 (user): Car Home (Lite) is the Home app; Smart Launcher, Easy Slider, Volume Button, Brightness Dimmer
  and Muviz Edge are force-stopped (still installed; Muviz Edge's notification access withdrawn) because their features
  will move into Car Home: volume/brightness over any app, an on-screen volume button, dimming below minimum
  brightness, and the music visualiser/screensaver. Back to Smart Launcher:
  cmd package set-home-activity ginlemon.flowerfree/ginlemon.flower.HomeScreen.
- Same features, Android bridge and settings key as glass.html, on a UI the tablet draws cheaply: solid dark surfaces only
  (no wallpaper, glass, blur, filters, shadows or translucency), no CSS animation or transition, fluid layout at the real
  screen size (no scaled 1340x800 canvas), plain text clock, still weather icon, music title with an ellipsis (no
  marquee), horizontal volume/brightness bars, instant pages for All apps and Controls, one accent (#5aa7ff).
- Tried and reverted 2026-10-27 (user didn't like it): hiding the map column unless a route runs. The map column stays.
- Home grid (2026-10-27): one grid, areas "map today car car" / "map music music sliders", columns 0.95 / 1.15 /
  0.5 / 0.4 fr, rows 1fr / auto (the cassette is as tall as its content; the clock and car panels take the rest).
  The .col wrappers are display: contents. The sliders column is narrow so the cassette gets the width.
- Map: the Dark map only (2026-10-28, user's choice; the Controls > Map choice is gone, old S.mapSrc is deleted).
  OSM tiles darkened once per tile into a canvas are only the fallback when the Dark map can't load.
  Dark: openmaptiles/dark-matter-gl-style (BSD, sprites in vendor/dark-matter) on OpenFreeMap's free OpenMapTiles
  vector tiles (tiles.openfreemap.org/planet, no key), fonts swapped to OpenFreeMap's Noto Sans (no Metropolis there),
  drawn by MapLibre GL JS 4.7.1 (vendor/maplibre). Car tuning in
  lite.html lifts road and label colours (the stock style is too faint to read while driving). No gestures, 1x pixel
  ratio, no fades, jumpTo once per GPS fix, top padding 28% so the fix sits under the puck. MapLibre's CSS sets
  position: relative on the container, hence `.map .vmap`. Measured on the tablet: parked ~4%, moving (1 Hz) ~18%.
  Dropped 2026-10-28: Waze live (embed.waze.com/iframe in the panel, ~22% CPU parked, 33% moving) and Google (Maps
  JavaScript API, needed a billed key the user didn't set up). See git history before that date for both.
  Turn card sources (checked on the tablet 2026-10-27 with a real route): Google Maps' notification has title "110 m",
  text = street, sub text "7 min · 3.4 km · 21:52 ETA" and the turn arrow as its large icon (MediaListener sends it
  as a PNG data URL, "icon"; the card shows it instead of guessing). Waze's notification stays "Running. Tap to open."
  while navigating: no turn data at all, so with Waze the card stays hidden. Google Maps goes picture-in-picture
  over Car Home when you leave it mid-route (stays on top). Turned off on the tablet 2026-10-27:
  cmd appops set com.google.android.apps.maps PICTURE_IN_PICTURE ignore (undo: ... allow).
  Turn card: only a nav notification with route details counts (turn word, distance, time); Waze's plain
  "Running. Tap to open." no longer shows as a card. The ETA row hides when it has nothing.
- Screensaver: screensaver.html?from=home&home=lite.html&lite: the bbot face in burst mode, clock + weather top-left,
  song and controls; returns to lite.html. `home=` picks the page to return to (default glass.html).
  Burst mode (2026-10-27): the face draws (at 30 fps) only while it has something to do: a mood change (every 16-26 s in
  lite), a reaction or glance (every 12-22 s) or a touch; otherwise its frame loop is parked and nothing redraws. Burn-in
  drift moves every 10 s, the clock is written only when the minute changes. Measured on the tablet (app + engine):
  face nonstop 60 fps ~175%, 30 fps ~130%, 20 fps ~98%; burst mode 17-26% averaged over a minute (about 1-3% between
  bursts); no face ~3%. `&nonstop` = face animating all the time (30 fps), `&fps=<n>`, `&noface` = old large still clock.
  Blinks (2026-10-28): during the rest a blink every 4-7 s (face.react('blink'), wakes 0.5 s), so the face never looks
  frozen; rest cost rises from ~2% to ~20% (face only, song stopped; 39% averaged over a minute).
  Screen stays on while any Car Home page shows (Android.keepAwake -> FLAG_KEEP_SCREEN_ON; lite/glass/screensaver
  call it, home pages again on every battery update), so idle on home leads to the screensaver, not a blank screen;
  unplugged with battery <= 20% it lets the screen time out. Other apps (Waze, Maps) keep their own screen rules. Before this, unplugged, the 5-min screen
  timeout dimmed then blanked the screensaver (stay_on_while_plugged_in only works on power).
  Lite music controls: flat, plain white skip icons and a solid light circle for play/pause (no glass rim).
  Lite song line: the same nonstop CSS marquee as the full screensaver (user's choice 2026-10-27, over cheaper passes).
  Any moving line costs: CSS marquee nonstop ~120%, JS-stepped 30 fps ~150% (main thread), 10 fps ~76%, 5 fps ~40%;
  the fade mask made no difference. Tried and dropped: one pass every 30 s (face + passes averaged 66% over 2 min). The face's frame
  loop runs through one shared timer (lite requestAnimationFrame wrapper) so things woken together draw in the same frame.
- Added back in Lite form (2026-10-27): HTC-style flip cards (static: digits change once a minute, no flip animation),
  a flat cassette music card (still reels, album art as the right reel, progress between them, controls on a trapezoid
  bridge), vertical volume / brightness bars. Measured on the tablet after: idle 2-17% CPU.
- Dropped on purpose: wallpaper picker and Glass toggle (nothing to show through solid panels), flip animation, smooth
  spinning reels. Instead (2026-10-27) the left reel steps 20deg (a third of its 60deg spoke gap, so it reads as turning
  forward) on the existing one-second progress tick while playing, live radio included. Measured: playing 12% CPU with
  the step, 9% without.
- Theme (2026-10-28): Controls > Theme: Lite / Glass, in both pages (data-seg="theme", handled before the generic
  segment code). Lite: the Controls page's bottom bar (2026-10-28, user: Theme + note + Close, flat --panel strip; header and Done gone;
  the grid fills four equal rows like Glass's). Glass: in the Controls drawer's bottom bar with Wallpaper and Close (moved from the header 2026-10-28,
  user: the top is out of easy reach on the 7.8" tablet; the header and its title are gone), because the Glass card grid
  has exactly four rows (a fifth squeezed the first row and the cards overlapped). Icons: feather = Lite, drop = Glass (same as the Glass effect card). Switching navigates to the other page, so only one is ever loaded: with Lite chosen nothing of Glass
  runs. Each page sets S.theme to itself on load and sends it with setPrefs; MainActivity.startPage() reads it
  (SharedPreferences "carhome", default lite). Lite's note class is .note (.hint is the map pill).
- Measured on the tablet 2026-10-27 (CPU = app + WebView engine, 10 s averages; song faked in the page, no audio):
  Lite idle 14%, song playing 23-31%, memory ~121 MB + 74 MB engine. Glass (Glass effect off) idle 23-34%, short title
  playing 40%, long title playing 164-195% (the YouTube-style title scroll is a continuous animation), ~163 MB + 91 MB.
  Both 61 fps at rest. Open item for glass: in calm mode, show a long title with an ellipsis instead of scrolling.

## Android app (android/, WebView shell, v1.2 = versionName 1.2 / versionCode 3; see CHANGELOG.md)
- `android/build.sh [install]`: no Gradle (aapt2, javac --release 17, d8, zipalign, apksigner). Copies glass.html, screensaver.html
  and vendor/ into assets/www. Signing key android/carhome.keystore + keystore.properties are git-ignored: back them up, a
  build signed with another key cannot install over this one. targetSdk 28 on purpose (see the manifest).
- MainActivity: full-screen immersive WebView, Home + Launcher intent filters, pages served from https://carhome.app/ (assets),
  photo picker for the wallpaper input, Back leaves the screensaver, Home closes drawers. Remote debugging is on
  (adb forward tcp:9333 localabstract:webview_devtools_remote_<pid>, then CDP; see the measuring notes below).
- Installed apps (2026-10-28): the 12 curated APPS tiles (brand colours) come first, then every other app with a
  launcher icon from Android.apps() ([{pkg, name}], A to Z, not Car Home), drawn with its own icon from
  https://carhome.app/icon/<pkg>.png (MainActivity renders getApplicationIcon to a 96 px PNG once, cached in memory).
  AdaptiveIconDrawable: background + foreground layers drawn unmasked at (-24,-24,120,120) (the visible middle 72 of
  108 dp) so the PNG is a full square and the tile's border-radius shapes it (img.appicon border-radius: inherit;
  Glass drops the sheen / inner rim on these tiles via :has). Legacy icons keep their own shape (Camera, DiPlay,
  CarWebGuru on this tablet).
  Their id is the package name. All apps pages scroll sideways (Lite and Glass). A newly installed app shows on the next page
  load (returning home or reopening Car Home). Browser prototype: curated list only.
- Bridge (window.Android): has/apps/openApp, openPanel (pop-ups over Car Home, checked on the tablet 2026-10-27: Wi-Fi =
  Settings.Panel.ACTION_WIFI, data = the internet panel, Bluetooth = One UI's BluetoothScanDialog via action
  com.samsung.settings.bluetooth.scandialog.LAUNCH (paired devices + scan; falls back to Bluetooth settings);
  hotspot and DND pull down the quick settings drawer via StatusBarManager.expandSettingsPanel (hidden API allowed at
  targetSdk 28, EXPAND_STATUS_BAR). Not reachable: One UI's quick settings tile details (the tile name is ignored, also
  Samsung's names Wifi/Bluetooth/Hotspot/Dnd; SystemUI's indexsearch DetailPanelLaunchActivity is not exported) and
  its WifiPickerDialog (closes at once when another app opens it). "media" opens Notification access),
  volume/brightness, media
  transport, setPrefs. Pushes host.on(type, data): battery, net, gps (1 Hz), place (Geocoder), media (MediaSessionManager via
  the MediaListener notification-listener), nav (Waze / Maps notification), home. Pages call Android.ready() when loaded.
- Unplug / plug in (2026-10-28, issues #3 #4, user): Assist listens for ACTION_POWER_DISCONNECTED too. Unplugged:
  media key PAUSE if music is active (AudioManager.dispatchMediaKeyEvent, no Notification access needed), start
  MainActivity with ACTION_SAVER (me.faizal.carhome.SAVER: fresh start loads saverPage(), otherwise Bridge.saver()
  pushes 'saver' and the home page calls goSaver()), then after 120 s, if still unplugged,
  performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) = screen off. Plugged in: cancel that, wake if the screen is off
  (as before), and 3 s later media key PLAY unless music is already playing (Android routes it to the last media app).
  Fallback without the accessibility service: Lite/Glass see charging go false and call Android.media('pause') +
  goSaver(); sleep then comes from the screen timeout.
- Moving (2026-10-28, issues #1 #2): GPS speed alone was trusted, so the last fix of a drive (kept when GPS goes quiet)
  or one noisy fix blocked the idle screensaver, and the cached fix Bridge.ready() re-sends bounced a fresh screensaver
  home. Now: home pages count "moving" only after 3 fixes in a row > 5 km/h with accuracy <= 25 m and the newest < 5 s
  old (moveRun / fixAt / moving()); the screensaver goes home only after 3 such fixes > 10 km/h (fastRun).
- Network icon (2026-10-28, issue #5): Bridge 'net' has online = NET_CAPABILITY_VALIDATED; when false the pill shows
  wifi-slash (on Wi-Fi) or cell-signal-slash.
- Sleep and wake (2026-10-28, user's request): unplugged, nothing keeps the screen on (keepAwake(charging) in Lite,
  Glass and the screensaver) and the screensaver doesn't start by itself, so the tablet sleeps after its 5-min screen
  timeout. Charging, stay_on_while_plugged_in (7) keeps it on. Plugging in with the screen off: Assist (bound, so it
  runs while the screen is off) gets ACTION_POWER_CONNECTED, takes a SCREEN_BRIGHT | ACQUIRE_CAUSES_WAKEUP wake lock
  and starts MainActivity with ACTION_MAIN (setShowWhenLocked / setTurnScreenOn; this tablet has no lock screen), so
  onNewIntent -> Bridge.home(). With the screen on, plugging in does nothing. The car-start nav app option (autoOpen,
  car-start push, #carstart, CAR_START extra) was removed 2026-10-28 at the user's request. Tested with
  `dumpsys battery unplug` / `set ac 1` (then `dumpsys battery reset`), Waze in front before sleep: woke to Car Home's
  home screen. Needs the accessibility service on.
- Split screen and recent apps (2026-10-28): Assist.java, an AccessibilityService (Settings > Accessibility > Installed
  apps > Car Home; the user turns it on, never set it from adb). One UI 6.1 gives apps no way into split screen:
  FLAG_ACTIVITY_LAUNCH_ADJACENT and `am start --windowingMode 6` open the app full screen, and
  GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN returns false. So Assist taps One UI's own route: first app full screen ->
  GLOBAL_ACTION_RECENTS -> the card's icon (content-desc "Advanced options, <label>, Button") -> "Open in split screen
  view" -> the "Select app" picker in the other half -> the second app by its launcher label (activity label, e.g.
  "YT Music", not the app label "YouTube Music"), else the picker's "All apps Button" and scrolling. Launching the second
  app directly at the picker cancels the split. Only the One UI launcher's windows are searched (others' trees are slow
  over binder). About 7 s end to end. Bridge: assistOn, openAssistSettings, recents, split(pkgA, pkgB) -> push
  'split' {ok}. Pages: All apps header has Split screen (pick left, then right; last pair S.split as a one-tap button)
  and Recent apps (GLOBAL_ACTION_RECENTS: One UI's recents, swipe apps away there). The rail's former screensaver
  (eyes) button is now #splitRail = lastSplit() (2026-10-28, user; it briefly opened One UI's own picker with the last
  app, replaced the same day): the last pair S.split, or with none All apps in pick mode. Assist.find() polls the
  picker's All apps list up to 3 s before giving up (missed once at 600 ms); any failed split step = GLOBAL_ACTION_HOME.
- All apps layout (2026-10-28, user): title on top, then #appsGrid as horizontal pages (.apps-pg, 6 columns x as
  many rows as fit, scroll-snap x; layoutApps() measures when All apps opens and moves the existing tiles into pages,
  so pin / pick marks stay), page dots (#appsDots, updated on scrollend, tap = jump), then the toolbar at the bottom.
  Labels are clamped to two lines and always two lines tall, so rows are equal. .apps-grid has touch-action: pan-x;
  swipe right on the first page = home via touch events on the grid (the browser takes the drag, so the pointer
  swipe never sees it). Tablet (Lite, 1006x601 CSS): 3 rows = 18 apps a page, 2 pages.
- All apps toolbar (2026-10-28, user asked to improve the three pills): .tools, one grouped bar (Lite: --panel with
  --line border; Glass: clear glass with --rim) of .tool buttons = icon chip + two lines (name, then what it does or
  its state). Pair button: the two apps' tiles as tall halves of a split screen. While picking, only the split tool
  (Cancel) and the pair stay. Clean up (#cleanBtn, "N MB free" from Bridge.memory(), refreshed when All apps opens):
  Bridge.cleanUp() = ActivityManager.killBackgroundProcesses for every launcher app but Car Home
  (KILL_BACKGROUND_PROCESSES; foreground services, i.e. playing music or navigation, are untouched) + Assist.closeAll()
  (recents, tap "Close all"; none = go home, ok false), then 1.5 s later push 'clean' {ok, freed, free} in MB.
  Tested 2026-10-28: 8 recent tasks -> 4 (the rest are Car Home / system), 810 -> 873 MB free, back on Car Home. Third-party apps can't list or close
  other apps' tasks themselves.
- Volume / brightness strip over full-screen apps (EdgeStrip.java, 2026-10-28; design: launcher-prototype/overlay.html,
  option B full height). Two TYPE_ACCESSIBILITY_OVERLAY windows from Assist (no draw-over-apps permission): a handle
  (10 x 140 dp pill mid-height on the sidebar's side, prefs "side" from setPrefs) and the strip (96 dp wide, full
  height: pin, volume, brightness). Tap the handle to open; it hides 4 s after the last touch or on a tap outside,
  unless pinned (prefs "stripPinned"). Shown only while the front TYPE_APPLICATION window isn't Car Home or the One UI
  launcher; checked 300 ms after the last window event (the list is not settled at the event). Waze's window root is
  null to accessibility, which counts as another app. Dragging in from the very edge is One UI's Back gesture
  (setSystemGestureExclusionRects is ignored for overlays). Icons: android/fonts/phosphor-fill.ttf -> assets/fonts.
  The home pages re-read volume and brightness on visibilitychange.
- Swipes on Car Home (Lite and Glass, 2026-10-28): on home, left = All apps, right = the screensaver (goSaver; was
  the last app opened, changed the same day at the user's request); on a page or Controls, right or up from the bottom edge = home. Not on the volume / brightness bars;
  the page and the All apps grid have touch-action: pan-y so horizontal drags arrive as pointer events (the
  grid scrolls, and the browser checks touch-action only up to the nearest scroller: without it on .apps-grid a
  drag there ended in pointercancel). Screensaver (opened from home): swipe right to left = home; its #screen has
  touch-action: none for the same reason.
- Two-finger volume swipe (2026-10-28, user): on home (not All apps / Controls / wallpaper) and the screensaver, two
  fingers up = volume up, down = down, a step per 50 px of the fingers' midpoint. Home also has two fingers right =
  openRecents(), left = lastSplit(); the first 24 px decide horizontal or vertical, then it sticks (> 100 px to act).
  Swipe up from the bottom edge in any app = One UI's gesture navigation (navigation_mode 2) Home, which is Car Home:
  checked from Waze, Chrome, YT Music, Spotify. Touch events (touchstart/move with
  two touches, passive), not pointer events: pan-y would cancel those. Bridge.volumeStep(dir) =
  adjustStreamVolume(MUSIC, RAISE/LOWER, FLAG_SHOW_UI), so One UI's own indicator shows; home's bar re-reads on a
  'carhome-levels' event. A second finger (!isPrimary) cancels the one-finger swipe / screensaver gestures. Tested
  with CDP Input.dispatchTouchEvent (two points), since adb input has one finger only. Inside other apps the bottom swipe is
  One UI's own gesture navigation.
- Pages: NATIVE = !!window.Android. In the app, sample data is off: apps filtered to installed, trip from GPS (starts at 0,
  saved in localStorage carhome-trip), weather at the GPS fix, idle player shows "Nothing playing" (or asks for
  Notification access), Wi-Fi/BT/data/hotspot/DND toggles open system panels, map = live OSM, map tap = nav app full screen.
- Screensaver by itself (glass.html goSaver): after 5 min with no touch while home is showing and speed <= 3 km/h
  (glass.html?idle=<sec> to test). It goes back home on a tap, a swipe left, or on moving off (> 10 km/h). Built into the launcher, not a DreamService: Android
  only starts those when the screen would sleep while charging, and stay_on_while_plugged_in keeps it on.
- Nav app in the panel (built, then dropped): One UI added its caption (23 px) above the requested rect; a rect 17.3 dp
  shorter fitted the panel exactly. Removed because the pop-up stays above every app (below). Freeform settings are off again.
- Pop-up limit (tested 2026-10-27): a One UI freeform window stays above everything, other full-screen apps included,
  and an app cannot demote it. `am start --windowingMode 1` (also with NEW_TASK|CLEAR_TASK) on the existing Waze task
  leaves it freeform; removing/moving another app's task needs signature permissions. Only the user (pop-up handle:
  minimise / maximise / close) or an AccessibilityService pressing those buttons can.
- Not done yet: OBD, checking the turn card against real Waze notifications on a drive, the 10 s "left the nav app" rule, trip counting while Car Home is hidden. Default Home since 2026-10-27
  (cmd package set-home-activity me.faizal.carhome/.MainActivity; back to Smart Launcher: ginlemon.flowerfree/ginlemon.flower.HomeScreen).
- First measurements on the tablet (Waze pop-up open, no music): Glass on 13 fps (worst frame 132 ms), app 217 MB PSS
  (137 MB of it GPU) + WebView renderer ~81 MB. Glass off 30 fps (53 ms), 155 MB (76 MB GPU) + ~74 MB. For reference
  Smart Launcher 102 MB, One UI Home 89 MB. First paint 9.4 s on a cold start (network fonts/icons). Idle it used 128% CPU,
  which slowed the whole tablet.
- Smoothness pass (same day), re-measured: Glass on 58 fps, Glass off 61 fps; app 170 MB PSS (96 MB GPU) + renderer ~74 MB;
  idle CPU ~11% app + ~6% renderer; opens in 1.8 s. What did it:
  1. Baked glass (glass.html bakeGlass): wallpaper blurred once into a half-size JPEG, each .glass shows its slice
     (--gx/--gy); gradients are see-through; live backdrop-filter only as a fallback.
  2. Calm mode (#screen.calm, always on in the app): no decorative loops (weather bob/glow/rain, warning and LIVE pulses);
     reels stop when nothing plays.
  3. WebView onPause while Car Home is not visible (stops drawing and animations). Never WebView.pauseTimers(): it is
     process-wide, and a second Car Home instance going to the background froze the screensaver page half-loaded.
  5. Cost model measured on the tablet: every screen update costs roughly 10% CPU per update per second, wherever it is
     (a weak PowerVR GPU recompositing the whole WebView). Anything animating every frame costs 150%+ of CPU: spinning reels
     or easing spools alone 170%, both still 24%. So in calm mode nothing eases or spins: the needle steps, the spools and
     reels move on the one-second progress tick (music playing 54% instead of 174%). The live map costs little (~7%).
     Rule for new UI: no CSS animation/transition that runs continuously in the app; batch changes into existing ticks.
  4. Fonts (Geist, Sofia Sans Extra Condensed), Phosphor (woff2 only), brand icons and the 8 wallpapers bundled in
     launcher-prototype/vendor; only weather (Open-Meteo) and map tiles still use the network. APK 2.8 MB.

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
- Face reactions in the app (screensaver host.on, 2026-10-27): media play/pause -> music-play (happy + ~10 nods) /
  music-pause (bored), also when a song is already playing as the screensaver opens; battery charging changes ->
  charging (relieved) / unplugged (sleepy); level <= 15% and not charging -> battery-low (sleepy), once until > 20% or
  Not wired yet: turns (Google Maps gives the arrow only as an image), call, message.
- Look: eyes only (mouth off, no head ring), off-black background. Lids are filled with the background, so shut eyes
  (sleep at night, blinks) used to vanish. Since 2026-10-27 the lids stop 0.6 face units short of meeting (screensaver
  patches SVGRectElement.setAttribute for .bwf-lid transforms), so a thin slit of eye stays: shut eyes read as two
  closed-eye lines. (A lid stroke was tried first: it drew a ledge and cap on half-closed eyes.) Lite burst mode never pauses with the eyes shut unless
  the mood is sleep (window.faceMood). Published test page (same file, debug bar always on): https://claude.ai/artifact/9XmBNmuNQU7BJHVGpu4E5T

## Not decided / parked
- Combined replacement for Easy Slider + Muviz Edge (2-finger volume / 3-finger brightness edge zone + screensaver). User paused it.
