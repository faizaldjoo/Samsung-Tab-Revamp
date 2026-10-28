#!/bin/bash
# Build Car Home without Gradle: aapt2 (resources + manifest) -> javac -> d8 (dex) -> zipalign -> apksigner.
# Usage:  android/build.sh            build android/build/carhome.apk
#         android/build.sh install    build, then adb install -r (uses $ADB_SERIAL if set)
# Needs the Android SDK (build-tools + platforms;android-34) and a JDK. The pages are copied from launcher-prototype/.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/opt/homebrew/share/android-commandlinetools}}"
BT="$SDK/build-tools/$(ls "$SDK/build-tools" | sort -V | tail -1)"
JAR="$SDK/platforms/android-34/android.jar"
OUT="$HERE/build"
KS="$HERE/carhome.keystore"
KSPROPS="$HERE/keystore.properties"

rm -rf "$OUT"
mkdir -p "$OUT/res" "$OUT/classes" "$OUT/dex" "$OUT/assets/www"

# Pages and their local files. The prototype keeps working in a browser; this copy is what ships.
cp "$ROOT/launcher-prototype/glass.html" "$ROOT/launcher-prototype/lite.html" "$ROOT/launcher-prototype/screensaver.html" "$OUT/assets/www/"
cp -R "$ROOT/launcher-prototype/vendor" "$OUT/assets/www/"
# Icon font for the native overlay (EdgeStrip): Phosphor Fill as TTF (Android can't load woff2).
mkdir -p "$OUT/assets/fonts" && cp "$HERE/fonts/phosphor-fill.ttf" "$OUT/assets/fonts/"

echo "== aapt2"
"$BT/aapt2" compile --dir "$HERE/res" -o "$OUT/res/res.zip"
"$BT/aapt2" link -o "$OUT/base.apk" -I "$JAR" --manifest "$HERE/AndroidManifest.xml" \
    -A "$OUT/assets" "$OUT/res/res.zip" --auto-add-overlay

echo "== javac"
javac --release 17 -nowarn -Xlint:-options -classpath "$JAR" -d "$OUT/classes" $(find "$HERE/src" -name '*.java')

echo "== d8"
"$BT/d8" --release --min-api 26 --lib "$JAR" --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')
(cd "$OUT/dex" && zip -q -j "$OUT/base.apk" classes.dex)

echo "== sign"
if [ ! -f "$KS" ]; then
    # First build: make a private signing key. Keep carhome.keystore and keystore.properties safe (both git-ignored):
    # without them a new build cannot install over the old one.
    PASS="$(openssl rand -hex 16)"
    keytool -genkeypair -keystore "$KS" -alias carhome -keyalg RSA -keysize 2048 -validity 10000 \
        -storepass "$PASS" -keypass "$PASS" -dname "CN=Car Home" >/dev/null 2>&1
    printf 'storePassword=%s\n' "$PASS" > "$KSPROPS"
    chmod 600 "$KS" "$KSPROPS"
fi
PASS="$(sed -n 's/^storePassword=//p' "$KSPROPS")"
"$BT/zipalign" -f -p 4 "$OUT/base.apk" "$OUT/aligned.apk"
"$BT/apksigner" sign --ks "$KS" --ks-pass "pass:$PASS" --out "$OUT/carhome.apk" "$OUT/aligned.apk"
echo "Built $OUT/carhome.apk ($(du -h "$OUT/carhome.apk" | cut -f1))"

if [ "${1:-}" = "install" ]; then
    adb ${ADB_SERIAL:+-s "$ADB_SERIAL"} install -r "$OUT/carhome.apk"
fi
