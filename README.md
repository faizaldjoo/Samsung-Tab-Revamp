# nDashMo

Dashboard meets Motion. A car home screen for the Samsung Galaxy Tab A7 Lite: map, music, clock and car info in one
dashboard, with two themes (Matte and Glass). It runs as an Android Home app that shows the pages in
`launcher-prototype/` in a WebView. Made by [@faizald.jo](https://www.threads.com/@faizald.jo).

Project notes for working on it are in `CLAUDE.md`.

`android-native/` is an early start of a native version without the WebView (package me.faizal.carnative: map,
media listener, UI helpers, tablet data). It is not built by `android/build.sh` yet.

## Try it in a browser

```bash
python3 -m http.server 8765 --directory launcher-prototype
```

Then open http://localhost:8765/lite.html (Matte) or http://localhost:8765/glass.html (Glass). Add `?drive` to either
for a simulated drive. The browser shows sample data; the real data comes from the tablet.

## Build and install on the tablet

### What you need

- A JDK and the Android SDK command-line tools, with `build-tools` and `platforms;android-34`.
- `ANDROID_HOME` set to the SDK folder (the script falls back to the Homebrew path on a Mac).
- `adb`. On Windows, run the build script from Git Bash or WSL; it is a bash script.

### Signing key (not in the repo)

`android/carhome.keystore` and `android/keystore.properties` are git-ignored on purpose. **Copy both from the
computer that built the app before into `android/` on the new one, and keep a backup.** Without them the script makes
a new key, and Android will not install that build over the app already on the tablet: you would have to uninstall it
first and lose its settings.

### Steps

1. On the tablet: Settings > Developer options > Wireless debugging, on (it turns off after every restart).
2. Find the tablet:
   ```bash
   adb mdns services
   ```
3. Build and install (use the serial `adb devices` shows):
   ```bash
   ADB_SERIAL=<tablet-serial> android/build.sh install
   ```
   Without `install`, the script only builds `android/build/carhome.apk`.

### After installing

- Updating the app turns its accessibility service off on this tablet. Turn it back on: Controls > nDashMo card >
  "Turn on", or Settings > Accessibility > Installed apps > nDashMo. It is needed for sleep when unplugged, wake when
  plugged in, split screen and recent apps.
- On a first install: make nDashMo the default Home app, and allow Location and Notification access. The About page
  (Controls > Details) shows what is still off and opens the right setting.
