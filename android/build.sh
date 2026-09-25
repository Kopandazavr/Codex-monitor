#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
VERSION_NAME="$(awk -F'"' '/versionName = "/ { print $2; exit }' "$ROOT/app/build.gradle.kts")"
[[ -n "$VERSION_NAME" ]] || { echo "Unable to read versionName from app/build.gradle.kts" >&2; exit 2; }
DIST="$ROOT/dist"
SIGNING_DIR="$ROOT/.local-signing"
# Canonical local Codex Monitor signing identity; persistent test builds are re-signed from Drive.
KEYSTORE="$SIGNING_DIR/codex-monitor-local.p12"
PASS_FILE="$SIGNING_DIR/password"

if [[ -z "${JAVA_HOME:-}" && -d "/Applications/Android Studio.app/Contents/jbr/Contents/Home" ]]; then
  export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
fi
if [[ -z "${ANDROID_SDK_ROOT:-}" && -d "$HOME/Library/Android/sdk" ]]; then
  export ANDROID_SDK_ROOT="$HOME/Library/Android/sdk"
fi

mkdir -p "$DIST" "$SIGNING_DIR"
if [[ ! -f "$KEYSTORE" ]]; then
  openssl rand -hex 24 > "$PASS_FILE"
  chmod 600 "$PASS_FILE"
  STORE_PASS="$(<"$PASS_FILE")"
  "$JAVA_HOME/bin/keytool" -genkeypair \
    -storetype PKCS12 \
    -keystore "$KEYSTORE" -storepass "$STORE_PASS" -keypass "$STORE_PASS" \
    -alias codexmonitor -keyalg RSA -keysize 3072 -validity 10000 \
    -dname "CN=Codex Monitor Local Build, OU=Personal Android App, O=Local Build" \
    >/dev/null 2>&1
fi

"$ROOT/gradlew" --project-dir "$ROOT" \
  :app:assembleRelease \
  --console=plain

SOURCE_APK="$ROOT/app/build/outputs/apk/release/app-release.apk"
OUT="$DIST/CodexMonitor-$VERSION_NAME.apk"
cp "$SOURCE_APK" "$OUT"

APKSIGNER="$(find "$ANDROID_SDK_ROOT/build-tools" -type f -name apksigner | sort | tail -1)"
"$APKSIGNER" verify --verbose --print-certs "$OUT"
(cd "$DIST" && sha256sum "$(basename "$OUT")") | tee "$DIST/SHA256SUMS.txt"
echo "Built $OUT"
