# Changelog

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
