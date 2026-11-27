# Licensing

> **Status:** Policy · **Last updated:** 2026-11-27
> **Related:** [spec.md](spec.md) · [SECURITY](SECURITY.md) · [README](README.md)

This is an engineering policy, not legal advice. If a distribution decision matters
commercially, get a lawyer to review it.

---

## 1. The launcher's own licence

The launcher source is licensed under **MIT**; see [LICENSE](LICENSE). The maintainer chose
MIT on 2026-11-27 from the options previously listed here.

This licence covers only IndieHost's own code and docs. It does **not** cover Butterscotch
(§2), game data (§3) or third-party components (§4), each of which keeps its own terms.
Forks must keep the copyright and permission notice, and must not bundle AGPL components.

---

## 2. Butterscotch and the AGPL boundary

Butterscotch is **AGPL-3.0**. IndieHost does not link, fork, bundle or repackage it. The
only interaction is Android IPC:

- IndieHost declares itself a home app, which grants shortcut-host permission.
- It accepts `CONFIRM_PIN_SHORTCUT` requests.
- It reads pinned shortcuts through `LauncherApps` and starts them with
  `LauncherApps.startShortcut`.

Starting another app's exported activity or shortcut is not linking, and it does not make
IndieHost a derivative work. This is the basis of [ADR-0002](docs/adr/0002-no-butterscotch-fork.md).

### 2.1 What would change that

Any of the following makes the combined work AGPL-3.0 and triggers source-offer
obligations:

- Forking `ButterscotchAndroid` and shipping the fork.
- Copying its native libraries, runner code or assets into the IndieHost APK.
- Repackaging a game with Butterscotch's runner and distributing the result.
- Linking against its code through JNI, AIDL or a shared library.

If that ever becomes necessary, the whole combined work must be AGPL-3.0, the source must
be offered to recipients, and the README/spec must say so. It is not a small change.

### 2.2 What is safe

- Requiring the user to install Butterscotch from the Play Store or a signed APK.
- Launching its activities and shortcuts.
- Documenting its pin flow.
- Linking to its repository and Play Store listing.

Do not imply that Butterscotch endorses IndieHost, and do not use its name as the product
name.

---

## 3. Game data

Game data is copyrighted by the game's authors and is **never** part of this project.

- The user supplies their own legally obtained copy.
- Nothing is committed, packaged, uploaded, backed up or shared.
- `.gitignore` covers `games/`, `*.win`, `*.droid`, `*.unx`, `*.love`, `*.rpy*`, `*.rpa`,
  `*.scummvm`, `*.ogg`, `*.exe`.
- `android:allowBackup="false"` keeps paths and recents out of cloud backup.
- The checker copies a chosen file into `cacheDir/import/` and deletes that directory
  before the next check. It is local and transient.
- Cover art is **only** what the user picks from their own images. It is downsampled to
  at most 512 px, stored app-private under `filesDir/covers`, and never extracted from a
  game folder or committed. Do not point the picker at copyrighted art you cannot use.
- The M2 WebView serves SAF documents as streams into WebView; nothing is copied into app
  storage, and no game asset is committed or backed up.
- Issue reports must not attach game files; see [TESTING §7](TESTING.md#7-bug-report-template).

IndieHost does not circumvent DRM or copy protection. If a game cannot be run from a
user-supplied folder without that, it is out of scope.

---

## 4. Third-party components

| Component | Licence | Use |
| --- | --- | --- |
| AndroidX / Jetpack Compose / Material3 | Apache-2.0 | UI and activity plumbing |
| AndroidX Room + Room KSP compiler | Apache-2.0 | M1 game-library persistence |
| AndroidX DocumentFile | Apache-2.0 | SAF tree walking |
| AndroidX WebKit (`WebViewAssetLoader`) | Apache-2.0 | Offline MV/MZ runtime; no network permission |
| Kotlin standard library | Apache-2.0 | Language/runtime |
| Android Gradle Plugin, Gradle | Apache-2.0 | Build |
| JUnit 4 | EPL-1.0 | Host tests |
| Python Pillow | HPND | Icon generation only (not shipped) |
| Butterscotch | AGPL-3.0 | Separate app; not linked or shipped |
| Agent skills (`.opencode/skills/`) | MIT / Apache-2.0 | Development-time instructions; not shipped |

If a dependency is added, record it here with its licence. Do not add a dependency whose
licence is incompatible with the launcher's chosen licence.

### 4.1 Agent skills

The repository vendors a project-local agent skill set in `.opencode/skills/`. These are
Markdown instructions and small helper scripts for development tools, not code that is
compiled into or shipped with the APK. Every skill directory keeps its upstream licence.

| Source | Licence | Skills |
| --- | --- | --- |
| [obra/superpowers](https://github.com/obra/superpowers) | MIT | 13 workflow skills |
| [Drjacky/claude-android-ninja](https://github.com/Drjacky/claude-android-ninja) | Apache-2.0 | `android-ninja` + references |
| [Trystan-SA/product-engineer-skills](https://github.com/Trystan-SA/product-engineer-skills) | MIT | 15 UX/product skills |
| [anthropics/skills](https://github.com/anthropics/skills) | Apache-2.0 | `frontend-design`, `skill-creator` |
| [bergside/awesome-design-skills](https://github.com/bergside/awesome-design-skills) | MIT | `material`, `design-styles` catalog |
| [mxyhi/ok-skills](https://github.com/mxyhi/ok-skills) | Apache-2.0 / MIT | 9 architecture and research skills |

The full provenance, commit pins and update procedure are in
[.opencode/SKILLS.md](.opencode/SKILLS.md). Only skills with a clear upstream licence were
vendored. The skills are advice, not project policy; [AGENTS.md](AGENTS.md) and
[spec.md](spec.md) take precedence.

---

## 5. Branding and the palette

The UI palette is a generic dark game-client look. It contains no third-party artwork,
logos or fonts, and it makes no network calls.

- The public version string is `0.4.0`: no third-party product name appears in the APK
  metadata, UI or artwork.
- The old internal `SteamTheme`/`Steam` identifiers were renamed to `IndieTheme`/`Indie`
  on 2026-11-27 (CON-07). `STEAM`-style codenames no longer exist in the build.
- [ADR-0005](docs/adr/0005-compose-material3.md) still describes the original Compose
  decision in historical terms; the rename is a mechanical follow-up, not a change to that
  decision.

---

## 6. Distribution checklist

- [x] A `LICENSE` file exists and matches the intended terms (MIT).
- [x] `README` states the licence.
- [ ] No Butterscotch code, native libraries or assets are bundled.
- [ ] No game data, saves, screenshots of copyrighted art or extracted covers are included.
- [ ] `allowBackup="false"` remains set.
- [ ] No `INTERNET` permission.
- [x] The `-steam` codename and third-party product symbols are renamed.
- [ ] Release signing is configured and the application id is final. The id remains
      `com.indiehost.launcher` by explicit maintainer decision.
- [ ] Third-party notices are included if required by their licences.
- [ ] The compatibility claims in README/spec match measured results.

---

## 7. Questions and answers

**Can I publish a fork of IndieHost?**
Yes, under MIT, provided the copyright and permission notice are kept and no AGPL
component or game data is bundled.

**Can I bundle Butterscotch to make installation easier?**
No. That makes the combined work AGPL-3.0 and requires complying with the AGPL for the
whole distribution. Keep it a separate install.

**Can I include a game to demo the launcher?**
No. Game data is copyrighted and the repository's rules forbid it. Use a synthetic fixture
or ask the user to supply their own copy.

**Does reading a pinned shortcut count as linking?**
No. It is an Android IPC call to another app's exported shortcut, the same mechanism every
launcher uses.

**Is the game-client look a problem?**
No third-party artwork or product name is shipped, and the old internal codenames have been
renamed. The look is generic.
