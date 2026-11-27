# Future emulation boundary — research only

> **Status:** Future-phase planning, not work to start. No rule is amended by this document.

Current `AGENTS.md` hard rules forbid Wine, x86 translation/emulation, `INTERNET`,
forking/linking/bundling Butterscotch, YYC/GMRT builds, and committing game data. Every
publicly documented Android PC-game stack collides with at least one of these rules.

## High-level taxonomy (public sources)

| Layer | Examples | Licence/status | Boundary |
| --- | --- | --- | --- |
| Instruction-set translation | [FEX](https://github.com/FEX-Emu/FEX) (MIT), [Box64](https://github.com/ptitSeb/box64) (MIT) | Active | Forbidden by "no x86 translation/emulation" |
| Windows API compatibility | [Wine](https://gitlab.winehq.org/wine/wine) (LGPL-2.1+), [Proton](https://github.com/ValveSoftware/Proton) (BSD + bundled LGPL/zlib + proprietary Steam lib) | Active | Forbidden by "no Wine" |
| Graphics translation | DXVK (zlib), vkd3d-proton (LGPL-2.1), Turnip/VirGL | Active | Part of the forbidden stack |
| Android integration | PRoot rootfs, termux-box, [Winlator](https://github.com/brunodev85/winlator) (LGPL-2.1), [GameNative](https://github.com/utkarshdalal/GameNative) (GPL-3) | Active/archived | New permissions, copyleft, W^X/exec-from-data constraints |
| GameMaker bytecode runner | [Butterscotch](https://github.com/ButterscotchRunner/Butterscotch) (AGPL-3, WAD 8–17 only) | Active | Launch-only; never fork/link/bundle; no YYC/GMRT |

Helpful map: [AOSP Internals — Windows Games on Android](https://aospbooks.github.io/aosp-internal-book/66-windows-games/). Public analyses describe the stack as workable but brittle at Bionic/glibc, UID, filesystem and X11/Surface boundaries; practical floors cited are ≥8 GB RAM and ≥20 GB free for 3D titles.

## What can be done now (no rule change)

- Maintain this taxonomy and a licence inventory as documentation.
- Record the questions below and the exact rule IDs each answer would affect.
- Continue the only permitted path: starting Butterscotch's exported activities/shortcuts for bytecode WADs, with the YYC/GMRT blocker intact.

## What must wait

Any Wine/Proton/Box64/FEX/rootfs/DXVK/Turnip code, any `INTERNET` permission or network call, any Butterscotch fork/link/bundle, any YYC/GMRT attempt, any game data or third-party binary in the repo, and any device performance claim.

## Maintainer inputs required before scoping

1. Is the phase desired, and which requirement ID/milestone governs it?
2. Exact target games and engine types; are YYC/GMRT permanently excluded?
3. Legal basis and who carries responsibility for user-supplied copies.
4. Is `INTERNET` acceptable in any form, or offline-files-only?
5. May the "no Wine / no x86 translation or emulation" rules be amended, how narrowly?
6. Must Butterscotch remain strictly launch-only?
7. Minimum device floor and test devices.
8. Performance/UX pass bar and sign-off owner.
9. DRM/anti-cheat policy — excluded, or known per-title failure (no bypass attempts).
10. Acceptable combined-work licences, notices and source offers.
11. Distribution channel (Play policy prohibits apps that install other apps).
12. Storage/permission model for game files and container state.
13. Separate repo/branch/app so current gates and patterns remain untouched?
14. Who approves each rule change and what evidence is required before code starts.

**Bottom line:** no action now. Research only until the maintainer supplies these answers and explicitly amends the spec.
