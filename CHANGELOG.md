# Changelog

## v0.2.0 (2026-10-23)

The transform loop, finished, plus sound and battery diagnostics.

### Added
- **Transformations for all 50 aliens**: slam flash, green DNA surge, the alien's signature beat while its art is revealed, then the name slam (~1.9 s, tap to skip). There are 27 effect families, each driven by the alien's own colours in `data/aliens.json`: fire (GPU shader), ice, prism and crystal shards, electricity, clones, sound waves, web, rewinding clock, bandages, vines, goo, magnetism, eyes, rolling shell, starfield, radiation, wind, growing, invisibility and more.
- **Timeout and recharge like the show**: when alien time runs out you get the four beeps and buzzes and turn back to human. The Omnitrix then recharges (2 min by default) with a red face and a countdown, and transforming is locked until the ready chime. Reverting early never costs a recharge.
- **Sound**: original synthesized cues for the dial-up, carousel ticks, slam, timeout, revert, denied and ready. They play only when the watch is in Sound mode (never on Vibrate or Mute), outside Do Not Disturb, and they follow the watch's system volume.
- **Omnitrix menu** (swipe down on the selector):
  - **Settings**: alien time (1–15 min, ×3 in Master Control), recharge length, animations (full / quick / off), haptics (strong / light / off), sound, diagnostics.
  - **Battery**: current level, drain per hour off the charger, app time and transformations today.
- **Battery diagnostics**: battery samples every ~15 min (no extra wake-ups) plus app events. Run `tools/battery_report.py start` in the morning and `report` in the evening for a full breakdown that includes Android's per-app power estimates.

### Improved
- **Smoothness**:
  - Transformations are prepared off the main thread while you browse, and the fire shader is pre-compiled, so slam-to-transform runs with 1 janky frame in ~137 (it used to stall at launch).
  - The countdown screen updates without recomposing.
  - The install script compiles the whole app ahead of time.

### Fixed
- Tapping the face did nothing after the app was updated or reinstalled, because the face held a stale tap link. The app now refreshes the face after updates, after reboots and whenever it opens.

## v0.1.0 (2026-10-23)

First release: the Omnitrix on the Galaxy Watch8 (44mm).

### Watch face (Watch Face Format v4)
- Minimal Omnitrix face: a big hourglass core with empty wedges, the time and date printed small in the top bulb, and the activation button in the bottom bulb.
- Energy states follow the battery: **Master Control** above 90% (gold bezel, orbiting roster), **Omnitrix** green from 20–90%, **Recharge** red below 20% (blinking lights, low-energy warning).
- **Alien form**: when you transform, the face becomes the white alien badge with the alien's silhouette and a live countdown, driven by the app's state complication.
- Always-on display: a powered-down Omnitrix with neon-tube digits and dimly glowing bulbs, plus an alien-form variant.
- Tapping the face opens the Omnitrix app.

### Omnitrix app
- **Hologram selector**: a DNA carousel on the bezel (rotary or drag, spring-snapped) and the selected alien projected as a hologram with scanlines, sweep, orbit ring and rising sparks.
- **50 aliens**: the original series, Alien Force, Ultimate Alien and Ultimate forms, each with species, home world, powers, weakness and stats.
- **Dossier**: swipe up on the hologram for the full profile.
- **Transform**: a slam burst with a haptic surge. Alien time is 10 minutes, or 30 in Master Control. Transforming is locked below 20% battery.
- **Alien-form screen**: countdown ring, hold to revert, and the four-buzz timeout.
- **Performance**: a single-canvas renderer with no per-frame clipping, art decoded off the main thread with prefetch, and a minified release build with baseline profiles. Measured 0 janky frames while spinning on the Watch8.

### Tooling
- The face is generated from the design renderer (`tools/build_watchface.py`) and validated with Google's wff-validator.
- Alien art pipeline: `tools/fetch_alien_art.py` (candidates + contact sheets) and `tools/build_alien_art.py` (hologram plates, glows, icons, face silhouettes). Art stays local and gitignored.
- One-command build and install: `tools/install_watch.sh`.

### Known limitations
- Tapping heart rate on the face no longer opens Samsung Health, because the full-face alien slot takes all taps.
- Wear OS hides complication contents on a locked watch, so the alien badge shows only while the watch is unlocked.
