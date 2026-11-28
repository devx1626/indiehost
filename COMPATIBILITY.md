# Compatibility matrix

> **Status:** Template only. **No measured results are recorded here.** The container used
> to prepare M1–M3 has no working `adb`, no legal game copy and no device, so no fps, boot
> time, audio-quality or thermal number in this project is backed by a measurement yet.

This file is the single table for device claims. Copy the header, fill one row per
game/device pair, and commit **device data only**. Never commit game files, save files,
cover art extracted from games, or screenshots of copyrighted art.

## Engine routes

| Engine | Route | Evidence |
| --- | --- | --- |
| GameMaker VM (WAD 8–17) | Butterscotch shortcut handoff | MT-05, MT-23 |
| RPG Maker MV | Offline WebView | MT-29 |
| RPG Maker MZ | Offline WebView | MT-30 |
| Ren'Py | Detected, not hosted | ADR-0008, MT-34 |
| LÖVE | Detected, not hosted | ADR-0009, MT-34 |
| ScummVM | Detected, not hosted | ADR-0010, MT-34 |

`CompatibilityTest` asserts this table still covers every `Engine` value.

## Table

| Device | Android | RAM | Engine | Game | WAD / version | Walk fps | Battle fps | Audio | Boot time | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Helio A22 (baseline) | — | — | GameMaker VM | Undertale 1.08 | 16 | unmeasured | unmeasured | unmeasured | unmeasured | M0 playable per device owner; no numbers |
|  |  |  |  |  |  |  |  |  |  |  |

## Required rows before M4 can close

- [ ] Five GameMaker VM titles spanning WAD 8–17 on the baseline device.
- [ ] At least one MV title and one MZ title launched from a scanned SAF folder.
- [ ] A Ren'Py, LÖVE and ScummVM row marked "detected, not hosted" with the ADR link.
- [ ] Cold start, warm resume and drawer readiness recorded on the baseline.
- [ ] Idle PSS and a 15-minute battery/thermal note.

On a workstation with a connected phone, `tools/device_checks.sh --perf` prints the raw
cold-start, warm-resume and memory output to copy into this table; it also pushes a
synthetic MV fixture for the offline WebView check. See [TESTING.md](TESTING.md) §2.1.

Use the protocol in [TESTING.md](TESTING.md) §4. Record the median of three cold-start runs
and the actual `adb` output where possible; do not round a guess into a claim. Game fps must
be measured externally (screen recording frame count or a benchmarking app) — IndieHost has
no in-game overlay.

## Known-bad configurations

None recorded. An empty section means "not tested", not "works everywhere".

## Claim policy

README, [spec.md](spec.md) §11 and release notes may state a number only when it appears
in this table with a device, Android version and measurement method. "Runs at a usable frame
rate" is the Milestone 0 report from the device owner, not a measured fps figure.
