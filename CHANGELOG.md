# Changelog

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
