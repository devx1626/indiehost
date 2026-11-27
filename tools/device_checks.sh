#!/usr/bin/env bash
# Device verification for IndieHost, run from a workstation with a working adb.
#
# This repository's build container has a broken aarch64 adb and no shell uid
# that can install packages, so instrumented/manual tests cannot run there.
# Run this script on your own machine with the phone connected and USB debugging
# authorised. It never commits, packages or uploads game data.
#
#   tools/device_checks.sh --help
#   tools/device_checks.sh --all
#   tools/device_checks.sh --dao
#   tools/device_checks.sh --fixture
#   tools/device_checks.sh --perf
#   tools/device_checks.sh --clean

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="$ROOT/library/build/outputs/apk/androidTest/debug/library-debug-androidTest.apk"
TEST_RUNNER="androidx.test.runner.AndroidJUnitRunner"
TEST_PKG="com.indiehost.library.test"
DEVICE_DIR="/sdcard/IndieHostDeviceCheck"
ADB="${ADB:-adb}"

usage() {
  cat <<'EOF'
Usage: tools/device_checks.sh [mode] ...

Modes (combine as needed; default is --all):
  --all        install APKs, run GameDaoTest, create the MV fixture, launch the app
  --install    install the debug APK and the library androidTest APK
  --replace    uninstall the installed app first (drops its pins), then install
  --dao        run the instrumented Room DAO test
  --fixture    push a synthetic MV folder to /sdcard/IndieHostDeviceCheck
  --launch     start IndieHost on the device
  --perf       print cold-start, warm-resume and memory output for COMPATIBILITY.md
  --clean      remove only /sdcard/IndieHostDeviceCheck from the device
  --help       show this text

Environment:
  ADB              path to adb (default: adb)
  ANDROID_SERIAL   device serial when more than one device is connected

Manual steps after --fixture:
  1. In IndieHost tap ADD GAMES FOLDER and pick "IndieHostDeviceCheck".
  2. Wait for the scan message, then open the row named "MVGame".
  3. Tap PLAY and check MT-29: it loads offline, fullscreen, with no browser UI.
  4. Remove the fixture with --clean when done.

Only synthetic HTML/JS written by this script is pushed. No copyrighted game
file, save or screenshot is ever copied to or from the device by this script.
EOF
}

die() { echo "device_checks: $*" >&2; exit 1; }

ADB_ARGS=()
adb_cmd() {
  if [ "${#ADB_ARGS[@]}" -gt 0 ]; then
    "$ADB" "${ADB_ARGS[@]}" "$@"
  else
    "$ADB" "$@"
  fi
}

require_adb() {
  command -v "$ADB" >/dev/null 2>&1 || die "adb not found; set ADB=/path/to/adb"
  adb_cmd start-server >/dev/null 2>&1 || true
  local devices
  devices="$({ adb_cmd devices 2>/dev/null || true; } | awk 'NR > 1 && $2 == "device" {print $1}')"
  [ -n "$devices" ] || die "no authorised device found; check 'adb devices'"
  if [ -n "${ANDROID_SERIAL:-}" ]; then
    ADB_ARGS=(-s "$ANDROID_SERIAL")
  else
    local count
    count="$(printf '%s\n' "$devices" | grep -c .)"
    [ "$count" -eq 1 ] || die "$count devices found; set ANDROID_SERIAL=<serial>"
    ADB_ARGS=(-s "$(printf '%s\n' "$devices" | head -1)")
  fi
  echo "device: $(adb_cmd getprop ro.product.model) / Android $(adb_cmd getprop ro.build.version.release)"
}

install_apks() {
  [ -f "$APK" ] || die "missing $APK (run: gradle :app:assembleDebug)"
  [ -f "$TEST_APK" ] || die "missing $TEST_APK (run: gradle :library:assembleDebugAndroidTest)"
  adb_cmd install -r "$APK"
  adb_cmd install -r "$TEST_APK"
  echo "APKs installed. Note: a different signing key will require --replace (which drops pins)."
}

replace_app() {
  echo "WARNING: uninstalling com.indiehost.launcher drops its library and pins." >&2
  adb_cmd uninstall com.indiehost.launcher || true
  install_apks
}

run_dao() {
  local output
  output="$(adb_cmd shell am instrument -w "$TEST_PKG/$TEST_RUNNER" 2>&1)"
  printf '%s\n' "$output"
  printf '%s\n' "$output" | grep -qE 'OK \(1 test\)|OK \(1 test\)' \
    || die "GameDaoTest did not report OK; see output above"
}

push_fixture() {
  local tmp
  tmp="$(mktemp -d)"
  trap 'rm -rf "$tmp"' RETURN
  mkdir -p "$tmp/www/js"
  cat > "$tmp/www/index.html" <<'HTML'
<!doctype html>
<html><head><meta charset="utf-8"><title>IndieHost synthetic MV fixture</title></head>
<body><h1>Synthetic MV fixture</h1><p>This is test data, not a game.</p>
<script src="js/rpg_core.js"></script></body></html>
HTML
  cat > "$tmp/www/js/rpg_core.js" <<'JS'
// Synthetic core JS for IndieHost's offline WebView check.
window.indiehostFixtureReady = true;
JS

  adb_cmd shell mkdir -p "$DEVICE_DIR/MVGame/www/js"
  adb_cmd push "$tmp/www/index.html" "$DEVICE_DIR/MVGame/www/index.html" >/dev/null
  adb_cmd push "$tmp/www/js/rpg_core.js" "$DEVICE_DIR/MVGame/www/js/rpg_core.js" >/dev/null
  echo "Synthetic MV fixture pushed to $DEVICE_DIR/MVGame"
  echo "In IndieHost: ADD GAMES FOLDER -> pick IndieHostDeviceCheck -> open MVGame -> PLAY."
  echo "Expected (MT-29): fullscreen load, no browser chrome, offline, no game data copied."
  echo "MZ still needs a legal MZ game copy; MT-30 cannot use this fixture."
}

launch_app() {
  adb_cmd shell am start -n com.indiehost.launcher/.MainActivity
}

run_perf() {
  echo "=== cold start (record TotalTime; repeat three times for the median) ==="
  adb_cmd shell am force-stop com.indiehost.launcher
  adb_cmd shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME
  echo "=== warm resume (record TotalTime) ==="
  adb_cmd shell input keyevent KEYCODE_HOME
  adb_cmd shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME
  echo "=== idle memory (record PSS) ==="
  adb_cmd shell dumpsys meminfo com.indiehost.launcher | head -30
  echo "Copy the numbers into COMPATIBILITY.md; do not round a guess into a claim."
}

clean_fixture() {
  adb_cmd shell rm -rf "$DEVICE_DIR"
  echo "Removed $DEVICE_DIR (library rows in IndieHost are untouched)."
}

main() {
  local mode="${1:---all}"
  case "$mode" in
    --help|-h) usage; exit 0 ;;
  esac
  for arg in "$@"; do
    case "$arg" in
      --all|--install|--replace|--dao|--fixture|--launch|--perf|--clean|--help|-h) ;;
      *) die "unknown option: $arg (try --help)" ;;
    esac
  done

  require_adb
  for arg in "$@"; do
    case "$arg" in
      --all)
        install_apks
        run_dao
        push_fixture
        launch_app
        ;;
      --install) install_apks ;;
      --replace) replace_app ;;
      --dao) run_dao ;;
      --fixture) push_fixture ;;
      --launch) launch_app ;;
      --perf) run_perf ;;
      --clean) clean_fixture ;;
    esac
  done
}

main "$@"
