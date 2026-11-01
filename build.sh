#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
export JAVA_HOME="${JAVA_HOME:-$HOME/.local/share/android-jdk}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export PATH="$JAVA_HOME/bin:$PATH"
tools="$ANDROID_HOME/build-tools/35.0.0"
platform="$ANDROID_HOME/platforms/android-35/android.jar"
rm -rf build
mkdir -p build/classes build/dex
"$tools/aapt2" compile --dir app/src/main/res -o build/resources.zip
"$tools/aapt2" link -I "$platform" --manifest app/src/main/AndroidManifest.xml -o build/base.apk build/resources.zip
javac -encoding UTF-8 --release 8 -classpath "$platform" -d build/classes $(find app/src/main/java -name '*.java')
"$tools/d8" --min-api 26 --lib "$platform" --output build/dex $(find build/classes -name '*.class')
cp build/base.apk build/unsigned.apk
(cd build/dex && zip -q ../unsigned.apk classes.dex)
"$tools/zipalign" -f 4 build/unsigned.apk build/aligned.apk
mkdir -p "$HOME/.android"
if [[ ! -f "$HOME/.android/debug.keystore" ]]; then
  keytool -genkeypair -keystore "$HOME/.android/debug.keystore" -storepass android -alias androiddebugkey -keypass android -dname 'CN=Android Debug,O=Android,C=US' -keyalg RSA -keysize 2048 -validity 10000 >/dev/null 2>&1
fi
"$tools/apksigner" sign --ks "$HOME/.android/debug.keystore" --ks-pass pass:android --key-pass pass:android --out build/sms-printer-debug.apk build/aligned.apk
"$tools/apksigner" verify build/sms-printer-debug.apk
printf 'Built %s/build/sms-printer-debug.apk\n' "$PWD"
