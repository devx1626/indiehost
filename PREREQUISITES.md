# Prerequisites

> **Status:** Living document · **Last updated:** 2026-11-25
> **Related:** [README](README.md) · [spec.md](spec.md) · [DEVELOPMENT](DEVELOPMENT.md) · [TESTING](TESTING.md)

Everything needed to build IndieHost, install it on a phone, and exercise the full
GameMaker flow. The short version: **JDK 17, Android SDK 34, Gradle 8.7, an Android phone,
and your own legal copy of a GameMaker VM game.**

---

## 1. Toolchain

Versions are pinned by the build files; do not upgrade one in isolation.

| Tool | Version | Defined in |
| --- | --- | --- |
| JDK | 17 | module `build.gradle.kts` files |
| Gradle | 8.7 | `gradle/wrapper/gradle-wrapper.properties` |
| Android Gradle Plugin | 8.5.2 | `build.gradle.kts` |
| Kotlin | 1.9.24 | `build.gradle.kts` |
| Compose compiler | 1.5.14 | `app/build.gradle.kts` |
| Compose BOM | 2024.09.02 | `app/build.gradle.kts` |
| Android SDK (compile/target) | 34 | module `build.gradle.kts` files |
| Android SDK (minimum) | 24 | module `build.gradle.kts` files |
| JUnit | 4.13.2 | `detect/build.gradle.kts` |
| Python 3 + Pillow | any recent | `tools/make_icons.py` (icons only) |

### 1.1 Workstation

- Any OS that runs Android Studio: Windows, macOS or Linux.
- **JDK 17** (Temurin, Zulu or the Android Studio bundled JDK).
- **Android Studio** Koala (2024.1.1) or newer, **or** the command-line tools with:
  - SDK Platform 34
  - Build-Tools 34.0.0
  - Platform-Tools (`adb`)
- **Gradle 8.7** on the PATH for the one-time wrapper regeneration. If you only use
  Android Studio, opening the project regenerates the wrapper jar for you.
- Git.
- Python 3 with Pillow, only if you regenerate launcher icons.

### 1.2 Android SDK setup

With the command-line tools:

```bash
sdkmanager "platforms;android-34" "build-tools;34.0.0" "platform-tools"
```

Point the build at the SDK either with `ANDROID_HOME`:

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$ANDROID_HOME/platform-tools:$PATH"
```

or with a local, untracked `local.properties`:

```properties
sdk.dir=/home/you/Android/Sdk
```

`local.properties` is ignored by Git; never commit it.

### 1.3 Linux on ARM64

Google's build-tools archives contain x86-64 binaries. On an ARM64 host the SDK's `aapt2`
cannot run, but the rest of the build is JVM code. Install the distribution's `aapt2` (on
Debian/Ubuntu: `apt install aapt`) and point Gradle at it:

```bash
gradle :app:assembleDebug \
  -Pandroid.aapt2FromMavenOverride=/usr/bin/aapt2
```

The SDK must still contain Platform 34 and Build-Tools 34.0.0. This path was verified on
aarch64 with Gradle 8.7: the debug APK built successfully. It does not replace the device
tests.

---

## 2. First build

```bash
git clone https://github.com/devx1626/indiehost.git
cd indiehost

# One-time: the binary wrapper jar is deliberately not committed.
# Skip this if you opened the project in Android Studio first.
gradle wrapper --gradle-version 8.7

# Host-side engine detection tests (12 tests, no device needed).
./gradlew :detect:test

# Debug APK for sideloading.
./gradlew :app:assembleDebug
```

Expected results:

- `:detect:test` — **12 tests, 0 failures**.
- `:app:assembleDebug` — `BUILD SUCCESSFUL`, APK at
  `app/build/outputs/apk/debug/app-debug.apk` (about 9–10 MB).

If `./gradlew` fails with `Could not find or load main class
org.gradle.wrapper.GradleWrapperMain`, the wrapper jar is missing: run the
`gradle wrapper` command above, or open the project in Android Studio once.

---

## 3. Device requirements

| Requirement | Detail |
| --- | --- |
| Android version | 7.0 (API 24) minimum. Shortcut-host APIs need 7.1 (API 25); accepting pins needs 8.0 (API 26). **Recommend Android 8.0+.** |
| CPU | ARM64 (`arm64-v8a`) or `armeabi-v7a`. Butterscotch ships both. |
| Baseline test device | MediaTek Helio A22, 4× Cortex-A53, PowerVR GE8300 — the Milestone 0 phone. |
| Storage | Enough for the APK, Butterscotch and your game. A large `data.win` can be hundreds of MB. |
| Connection | USB debugging enabled, or a way to sideload the APK. |

An emulator can run the launcher, but it is **not** a substitute for the phone test:
Butterscotch's ARM runner, shortcut pinning and real frame rate all need a device.

---

## 4. Device setup checklist

Do these in order. Each step has an expected result.

1. **Enable developer options and USB debugging.**
   Settings → About phone → tap Build number seven times → Developer options → USB
   debugging.
2. **Authorise the computer.**
   ```bash
   adb devices
   ```
   Expected: your device listed as `device`, not `unauthorized`. Accept the RSA prompt on
   the phone if it appears.
3. **Install Butterscotch** — from the Play Store (`net.perfectdreams.butterscotch`) or a
   signed APK:
   ```bash
   adb install -r butterscotch.apk
   ```
4. **Install IndieHost:**
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
   Expected: package `com.indiehost.launcher` appears in the app list.
5. **Make IndieHost the home app.**
   Open IndieHost → `CHOOSE HOME APP`, or Settings → Apps → Default apps → Home app.
   Expected: pressing Home lands on IndieHost. The library switches from the setup panel
   to either the empty-library panel or your pinned games.
6. **Grant usage access (optional).**
   Home screen → `ALLOW USAGE ACCESS`, or Settings → Apps → Special access → Usage access →
   IndieHost. Expected: the recents row can show apps opened outside IndieHost.
7. **Import a game into Butterscotch.**
   Open Butterscotch → import your `data.win` through its file picker. Keep the file on the
   phone; do not push copyrighted data into this repository.
8. **Pin the game.**
   In Butterscotch, open the game's settings → **Add Shortcut to Home Screen**.
   Expected: the system pin dialog is handled by IndieHost (a brief translucent activity),
   and the game appears in IndieHost's library.
9. **Launch it.**
   Tap the tile or `PLAY`. Expected: Butterscotch's own game activity opens; IndieHost
   stays behind it. Press Home to return.

If step 8 does nothing, see §7. A pin belongs to the launcher that accepted it, so a game
pinned while another launcher was active must be re-pinned with IndieHost as home.

---

## 5. Game data

You need **your own legally obtained copy** of a game. Nothing is provided by this
repository.

- **Reference title:** Undertale 1.08, GameMaker Studio 1.4.1804, **WAD 16**. It is
  Butterscotch's reference target and the Milestone 0 game.
- **Verify the build is VM, not YYC.** Open the `data.win` in UndertaleModTool on a PC and
  check the bytecode version / CODE chunk. IndieHost's own `CHECK DATA.WIN` tool reports
  the same thing, but it can only tell you after you have the file.
- **Do not commit game files.** `games/`, `*.win`, `*.droid`, `*.unx`, `*.love`, `*.rpy*`,
  `*.ogg`, `*.exe` and friends are in `.gitignore`. Never attach them to an issue or a PR.
- **Do not upload them anywhere.** IndieHost has no network permission; keep it that way.

A synthetic `data.win` fixture for tests is built in code
(`detect/src/test/kotlin/.../GameMakerWadTest.kt`); no real game data is needed for
`:detect:test`.

---

## 6. Signing

- Debug builds use the standard debug keystore; no setup needed. CI builds these.
- Release signing is opt-in and local only. Generate a keystore outside the repository,
  export `INDIEHOST_KEYSTORE`, `INDIEHOST_KEY_ALIAS`, `INDIEHOST_STORE_PASSWORD` and
  `INDIEHOST_KEY_PASSWORD`, then run `gradle :app:assembleRelease`. No key or password is
  committed, and CI does not sign. **Back up the keystore**: losing it prevents future
  updates signed with the same key.
- The application id `com.indiehost.launcher` is a placeholder. Changing it changes the
  installed identity and invalidates existing pins; do it before a public release.

---

## 7. Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| `gradle: command not found` | Gradle not installed | Install Gradle 8.7, or open the project in Android Studio once |
| `Could not find or load main class org.gradle.wrapper.GradleWrapperMain` | Wrapper jar not committed | `gradle wrapper --gradle-version 8.7` |
| `SDK location not found` | No `ANDROID_HOME` or `local.properties` | Set one of them (see §1.2) |
| `Failed to install ... INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Different signing key or package id | Uninstall the old app, reinstall; re-pin games afterwards |
| Library says "Set IndieHost as your home app" | Another launcher is active | Set IndieHost as home; shortcut-host permission follows the home role |
| Pin dialog appears but no game in the library | Pin belongs to another launcher, or id does not start with `game-` | Re-pin with IndieHost active; check the id in Butterscotch |
| Tapping a game shows "did not accept the launch request" | Stale shortcut, permission revoked, or Butterscotch uninstalled | Re-pin; confirm Butterscotch is installed |
| Usage access toggle missing | OEM moved the setting | Settings → Apps → Special access → Usage access; search "usage access" |
| `data.win` check says "No CODE chunk" | YYC or GMRT build | Use a VM build; no bytecode runner can run this file |
| `adb devices` shows `unauthorized` | RSA prompt not accepted | Accept it on the phone, or revoke USB debugging authorisations and reconnect |
| Butterscotch installs but games will not start | Wrong ABI or a non-VM game | Confirm ARM64/armeabi-v7a and a VM `data.win` |

---

## 8. Sandbox / CI note

Some sandboxed environments have no JDK, no Android SDK or a broken `adb`. The container
this repository was last verified in now has JDK 17, Gradle 8.7 and SDK 34, and
`:app:assembleDebug` succeeds there with the ARM64 `aapt2` override above. Its `adb` still
cannot load its native library, so **no device test has run**. Do not report a build or a
device test as passing unless it actually ran. See [AGENTS.md](AGENTS.md) for the rule
agents must follow.

---

## 9. Verification checklist

Before calling a checkout "ready":

- [ ] `java -version` reports 17.
- [ ] `./gradlew :detect:test` passes 12/12.
- [ ] `./gradlew :app:assembleDebug` succeeds.
- [ ] APK installs on the phone.
- [ ] IndieHost can be set as home.
- [ ] A pinned game appears and launches.
- [ ] `adb logcat -s IndieHostPin` shows a successful pin, if you needed to debug it.
- [ ] No game data appears in `git status`.
