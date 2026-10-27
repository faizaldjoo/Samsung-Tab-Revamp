# Changelog

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
