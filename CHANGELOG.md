# Changelog

## v1.0 as nDashMo (in progress)

- **The app is now called nDashMo**, "Dashboard meets Motion." (app name, accessibility service and music listener in Android, page titles and
  messages). The package stays me.faizal.carhome, so it installs over Car Home and keeps the settings. Version 1.0.
- **Controls, redone** (Matte and Glass): a full-screen page beside the rail, like All apps, in three columns by job:
  Quick (big Wi-Fi, Bluetooth, data and hotspot tiles, Do Not Disturb and auto brightness switches), Display and
  driving (theme, night mode, navigation app, sidebar side, scroll title; in Glass also Glass effect and Wallpaper),
  and an nDashMo card with the name, tagline, version, the creator (@faizald.jo on Threads), what it needs from
  Android (with "Turn on" when off), battery and temperature, and Details. Close at the top right, or the status button, or swipe right.
- **About nDashMo** (Details): name, tagline, version, the creator's Threads link and credits on the left; the four
  things it needs from Android (accessibility, notification access, location, default home app, each with why) on the
  right, with a red mark and Open settings when one is off. Version only, no change list in the app.
- Updating the app turns nDashMo's accessibility service off on this tablet (seen 2026-10-29): after an update, the
  About card shows Accessibility "Turn on".

- The Lite theme is now called **Matte** (Controls > Theme: Matte / Glass). Only the name on screen changed: the
  saved choice carries over.

Tested on the tablet 2026-10-29 (simulated drive and charger): the driving view, unplug = pause + sleep within 2 s,
plug in = the last music resumes. Not yet tried in the car.

- Map driving view (Lite and Glass), after TomTom's free-driving look, all the time (parked too): the map tilts and
  turns with the direction of travel (parked: the last direction), the dot is an arrow lower in the panel, the street
  is named under it, and a speed limit sign shows on the left when OpenStreetMap has one (solid red above the
  limit). The speed itself is not on the map (removed on request; the Car panel shows it). Speed limits are rare around
  Sumedang in OSM (the Cisumdawu toll road and a few streets). Try it in a browser: lite.html?drive or glass.html?drive.
  Roads are brighter in the driving view (light grey streets, lighter labels), so they read at a glance when tilted.
  The map is closer in (zoom 18, was 16.4) and street names are bigger: 16 px bold in normal case instead of 10 px
  capitals, repeated more often along the road.
  Light and dark map (Lite and Glass): the map follows Night mode in Controls, light by day (white roads on a cool grey
  ground, blue-grey water, dark street names) and dark at night; Auto switches at 18:00 and 06:00 with the rest of the
  screen. Your position is a car seen from above instead of an arrow: white on the dark
  map, dark grey on the light map, tipped back to lie on the tilted road, with a soft shadow and a faint blue pool.
- Lite clock: the same flip clock as Glass, with shaded card halves, a hinge, and the flip when a digit changes (only the
  card that changed, once a minute). On the tablet the flip costs about 2% CPU on average.
- Matte in a browser window keeps the tablet's proportions, like Glass (it used to stretch to the window's height,
  leaving tall empty panels). Nothing changes on the tablet.
- Controls > Scroll title (Lite and Glass, off by default): on, every song title scrolls while it plays, short ones
  too (one copy at a time), on the home player and the screensaver. Off: only titles too long to fit scroll. On costs
  about 80-100% more CPU while music plays. The battery and temperature cards are a bit narrower to make room.
- Home player (Lite and Glass): the time labels are gone, only the progress bar stays. In Lite a title too long for
  the label now scrolls like Glass's (rests at the start, slides, repeats; stops while paused); a title that fits stays
  still and centred. Scrolling costs CPU on the tablet: about 84% (Lite) and 103% (Glass) while a long title runs,
  against 6-9% still.
- Cassette reels (Lite and Glass): both reels turn one 20 degree step a second while music plays, on the progress
  tick, in the browser prototype too (it used to spin them smoothly, which costs over a full CPU core on the tablet).
  Glass's step was 40 degrees, which on the 6-spoke reel looked like turning backwards. In Lite the album art now
  turns too. On the tablet: about 10-18% CPU playing, 1-2% paused.
- Glass weather icon on the tablet: rain no longer shows the sun's orange glow behind the cloud, and the still rain
  drops are staggered and slanted instead of one level row.
- Weather icons, all pages: Glass shows fog and snow with their own icons (fog was a plain cloud), like Lite and the
  screensaver; snow showers are snow everywhere; the sun / moon icon switches at 06:00 and 18:00 instead of at the
  next weather update (up to 20 minutes late). All nine icons checked in Lite and Glass, day and night. The icon stays still on the tablet: even
  3 seconds of its animation when the weather changes cost 150-175% CPU, so it was left out.
- Fixed (full check on the tablet, 2026-10-29):
  - Plugging in (car start) opened a second copy of Car Home next to the home one, both running (double CPU and GPS).
    It now comes back the way the Home button does, so there is only ever one.
  - Unplugging a full (100%) tablet didn't count as unplugged, so the trip didn't reset and the screensaver rules
    thought it was still charging. Charging now means power is plugged in.
  - A GPS jump (cold start, out of a tunnel) was added to the trip: a 1 km jump made the average 189 km/h. Steps
    faster than 200 km/h between two fixes are now skipped.
  - The screensaver opened without a theme goes back to the saved theme, not always Glass.
- Screensaver song line: a title that fits, or any paused song, shows once and centred; only a long title scrolls,
  and only while it plays. (A short or paused song sat off-centre with its scrolling copy showing.)
- Unplugging the charger (car off) resets the trip: distance and drive time go back to 0, and nothing adds to them
  until the tablet is charging again, so carrying it around doesn't count as driving.
- Unplugging the charger (car off) now pauses the music and puts the tablet to sleep straight away, whatever app is
  in front. (#3; first built as the screensaver, then sleep 2 minutes later, changed to sleep at once on request.)
- Plugging the charger in (car on) resumes the music that played last, about 3 seconds later, so the car's Bluetooth
  has time to connect. (#4)
- The screensaver starts again after 5 minutes idle: a GPS reading left over from the last drive, or a single noisy
  one while parked, no longer counts as driving. Moving now means 3 good fixes in a row over 5 km/h. (#1)
- Swiping to the screensaver no longer bounces back home: it returns on moving off only after 3 fixes in a row over
  10 km/h, not on the old fix sent as the page opens. (#2)
- The network icon shows "no network" (crossed-out Wi-Fi or signal) when the connection doesn't reach the internet,
  for example a hotspot without data. (#5)
- Unplug and plug-in actions need Car Home's accessibility service on (Settings > Accessibility > Installed apps).
  Without it, the home page still opens the screensaver and pauses the music when unplugged.
- Controls: Theme, Wallpaper and Close sit in a bar at the bottom of the drawer (easier to reach); Lite's Controls is
  a drawer beside the sidebar like Glass's, not a full page.
- Sidebar: bigger buttons with no gaps between them; the status pill is the Controls button (the separate gear is gone).
- Car panel (GPS): a HUD-style gauge: big speed, a compass rose, a speed bar (red from 100 km/h), then trip, drive
  time and average.

## v1.2 (2026-10-28)

### Apps
- All apps shows every app installed on the tablet, with its own icon, after the main car apps. Their icons fill the
  tile with Car Home's rounded corners (they used to sit inside the tile with One UI's own, different corners).
  Any of them can be pinned to the sidebar.

### Split screen, recent apps and swipes
- Split screen with any two apps: All apps > Split screen, pick the left app, then the right one. Car Home remembers
  the last pair as a one-tap button (for example "Waze + YT Music"). It takes about 7 seconds, because Car Home taps
  through One UI's own recent apps menu for you (One UI lets no app start split screen directly).
- Recent apps button (All apps): opens One UI's recent apps screen, to swipe away running apps one by one.
- All apps scrolls sideways, a page at a time (6 apps across, as many rows as fit), with page dots underneath; tap a
  dot to jump to that page. Swiping right on the first page still goes home. Long app names wrap to two lines.
- All apps tools redone as one toolbar along the bottom of All apps: each tool has an icon and a second line saying
  what it does or its state.
  The last split pair shows the two apps' own icons side by side, like the two halves of a split screen.
- Clean up (All apps): frees memory held by apps in the background and closes the recent apps (One UI's Close all),
  then says how much memory it freed. The button shows the free memory. Music or navigation that is playing keeps
  running.
- Both need Car Home switched on once in Settings > Accessibility > Installed apps.
- Volume and brightness over full-screen apps: a thin handle on the sidebar's side of the screen; tap it and a
  full-height strip slides in with volume and brightness sliders. It hides after 4 seconds, or stays on screen with
  its pin. Shown only while another app is in front, never over Car Home.
- Swipes on Car Home: left for All apps, right for the screensaver; on a page, swipe right (or up from the bottom)
  for home. On the screensaver, swipe left for home. Fixed: swiping right on All apps didn't go home when the swipe
  started on the app grid.
- Two-finger swipe up / down on the home screen and the screensaver: volume up / down, one step every 50 px,
  with One UI's volume indicator.
- The sidebar's eyes (screensaver) button is now a Split screen button: it opens your last split pair; with none
  saved yet, All apps opens to pick the two apps.
- Two-finger swipes on the home screen: right opens recent apps, left opens the last split pair (or the picker).
- Split screen no longer gives up when the second app is only in One UI's full app list (it now waits for the list
  to open), and goes back home if a step fails instead of leaving One UI's list on screen.

### Controls
- Glass gets the same Dark map as Lite (it still had darkened OpenStreetMap tiles).
- Map: only the Dark map now. The Map choice (Dark / OpenStreetMap / Waze live / Google) is gone from Lite's Controls,
  with the Waze live and Google map code; OpenStreetMap is only the fallback if the Dark map can't load.
- Theme switch, Lite / Glass, in both versions. Only the chosen one runs, and the app opens on the last one used.

### Sleep and wake
- Unplugged and left alone, the tablet goes to sleep after its screen timeout (5 minutes); the screensaver only
  starts while charging. Plugging in (starting the car) wakes it to Car Home's home screen. Needs Car Home on in
  Settings > Accessibility.
- "Open Waze when the car starts" is gone from Controls (user's choice): plugging in always shows the home screen
  and leaves the nav app alone. In Glass's Controls the battery and temperature cards are wider to fill its row.
- Glass: opening Controls closes All apps and the other way round (both used to stay open, one over the other).

### Screensaver
- While charging, the screen stays on while Car Home shows, so an idle tablet goes to the screensaver instead of a
  blank screen; the face blinks every few seconds so it never looks frozen.
- Bigger weather, place, date and song text, readable at arm's length on the tablet.

## v1.1 (2026-10-27)

First version that runs on the tablet, as the **Car Home** Android app (`android/`), plus UI changes from testing it there.

### Android app
- WebView launcher built without Gradle (`android/build.sh`), registered as a Home app, pages served from the app.
- Real tablet data: installed apps, battery, Wi-Fi and Bluetooth, GPS speed, heading and trip, place name, weather at
  the GPS position, the playing song with working controls, volume and brightness.
- Wallpaper "From gallery" opens Android's photo picker.

### Home screen
- Map panel: live OpenStreetMap around the GPS position, with the turn card and ETA read from Waze or Google Maps'
  notification. Tapping it, or starting the car, opens the nav app full screen. (Waze as a pop-up window over the panel
  was built and dropped: One UI pop-ups stay on top of every app.)
- Glass effect toggle (Controls): on = glass, off = clear panels.
- Wallpaper picker (Controls > Wallpaper): 8 bundled photos, 6 gradient wallpapers and "From gallery", with preview.
- Glass is baked: the wallpaper is blurred once and each panel shows its slice, instead of blurring live.
- Calm mode in the app: no animation runs every frame. The speed needle steps, and the cassette reels and spools move
  once a second. Decorative loops (weather icon, pulsing dots) are off.
- Fonts, icons and wallpapers are bundled, so it starts fast and works offline.
- Smaller fixes: centred weather icon, readable secondary text, slider icons dark grey (white when the fill is low),
  white OBD chip icon, "Nothing playing" state for the player.

### Car Home Lite (`launcher-prototype/lite.html`)
- New lighter version with the same features and the same Android bridge, on a flat, solid UI with nothing that
  redraws on its own: text clock, still weather icon, live dark map, simple music card, car stats, horizontal sliders,
  All apps and Controls as instant pages. Its screensaver keeps the bbot face, in burst mode: it moves only during
  mood changes, glances and touches and holds still in between (17-26% CPU instead of 130-175% animating nonstop).
  Its music controls are flat like the home screen's (light round play/pause), and the song title scrolls nonstop
  (about 120% CPU while a song shows).

- Controls: Wi-Fi opens the Wi-Fi panel (switch and networks), Bluetooth opens One UI's Bluetooth pop-up (paired
  devices), mobile data the internet panel; hotspot and Do Not Disturb pull down the quick settings drawer.
- Lite cassette: the left reel turns a step each second while music plays (about 3% CPU), the sliders are a narrow
  column so the cassette is wider, and the cassette is only as tall as its content.
- Lite map is the Dark Matter style by default (Controls > Map: Dark): a dark vector map on free OpenFreeMap tiles,
  roads lifted for reading at a glance. About 4% CPU parked.
- Turn card shows Google Maps' own turn arrow. Waze shares no turn details in its notification, so the card only works
  while navigating with Google Maps.
- Lite map can show Waze's live map (Controls > Map: Waze live, the default): Waze's roads, traffic and reports in the
  map panel, following the car. Waze's background "Running" notification no longer shows as an empty turn card.
- Lite map can use Google's map (Controls > Map: OpenStreetMap / Google) with your own Maps API key in
  `launcher-prototype/maps-key.js` (not in the repo); without a key it stays on OpenStreetMap.

### Screensaver
- Shut eyes (asleep at night, blinks) show as closed-eye lines instead of disappearing.
- The face reacts on the tablet: music playing / paused, plugged in / unplugged, low battery.
- Starts by itself after 5 minutes without a touch while parked on the home screen; a tap, moving off or starting the
  car brings back the home screen.

### Measured on the tablet (Galaxy Tab A7 Lite)
- Home with glass: 13 fps before, 58 fps after. Idle CPU: 128% before, about 11% after. Music playing: 174% before,
  54% after. Opens in 1.8 s (was 9.4 s to first paint).

## v1.0 (up to commit a3f8b55)

The browser prototype: Liquid Glass home screen (`launcher-prototype/glass.html`) with map, Today, Car, cassette player
and sliders; CarPlay-style rail with pinned and recent apps; Controls drawer; screensaver with the bbot face.
