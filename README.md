# OMNITRIX OS

A Ben 10 Omnitrix experience for the **Samsung Galaxy Watch8 (44mm)**. It's more than a skin: a minimal Omnitrix watch face plus a companion Wear OS app. Tap the face and the dial pops up. Spin the bezel through **50 aliens** projected as holograms, slam to transform, and the face turns into the white alien badge until your time runs out.

- **Watch face** (Watch Face Format v4): a big hourglass core with a seconds ring, the time and date printed small in the top bulb, battery-driven energy states, a red timeout after you switch back from an alien, and a powered-down always-on mode.
- **Omnitrix app**: a hologram selector with a DNA carousel on the bezel (one alien per bezel click), a dossier for each alien (swipe up), an alien-specific transformation sequence for every alien, a live countdown, and hold to revert.
- **DNA Missions**: you start with the original ten. The other 40 aliens unlock one mission at a time across five chapters, with Alien X as the final mission. Missions ask for steps, transformations, full alien time, variety, Bio-scans and streaks, and earn XP and ranks from Rookie to Ben 10,000.
- **Bio-scan**: a minimal live heart monitor with heart-rate zones.
- **Tiles**: Bio-scan, DNA Missions and quick settings, a swipe from the watch face.
- **Sound & haptics**: original Omnitrix cues that only play when the watch is in Sound mode and follow its volume, plus haptic patterns for every beat.
- **Omnitrix menu** (swipe down on the selector): Bio-scan, DNA Codex, settings (alien time, timeout, animations, haptics, sound, DNA Codex, age, diagnostics) and battery diagnostics. Swipe in from the left edge to go back on any screen.
- **Roster**: all 22 original-series aliens, the Alien Force line-up, the best of Ultimate Alien and five Ultimate forms ([data/aliens.json](data/aliens.json)).

| Battery | Face |
|---|---|
| > 90% | **Master Control**: brighter lime core, orbiting roster, every alien available, alien time tripled |
| 20–90% | **Omnitrix**: classic green hourglass |
| < 20% | **Low energy**: red, transforming locked |
| transformed | **Alien form**: white badge with countdown |
| after switching back | **Timeout**: red with a countdown (10 s by default, set in Settings) |

## Try the design prototype

```sh
cd design && python3 -m http.server 8765
# open http://localhost:8765
```

Click the face to raise the dial, scroll to cycle aliens, click again to transform. The page also shows every screen: the faces, selector, dossier, DNA Missions, Bio-scan and always-on. It uses 13 original emblems instead of the 50-alien art.

## Install on your watch

The watch face is a Watch Face Format v4 package (Wear OS 6, which the Galaxy Watch8 runs).

1. On the watch, go to **Settings → About watch → Software** and tap **Software version** 5 times to unlock Developer options.
2. Go to **Settings → Developer options** and turn on **ADB debugging** and **Wireless debugging**. Tap **Pair new device** to get a pairing code.
3. On your computer:
   ```sh
   adb pair <ip>:<pair-port>      # enter the code shown on the watch
   adb connect <ip>:<port>        # the address on the Wireless debugging screen
   tools/install_watch.sh
   ```
4. Long-press the watch face and pick **Omnitrix**. Tap the face to open the dial.
5. Optional: swipe left from the face and add the **Omnitrix Bio-scan**, **Omnitrix DNA Codex** and **Omnitrix settings** tiles.

### Alien artwork (local only)

The holograms use official Alien Force / Ultimate Alien-era renders. Those are copyrighted, so they're **never committed**: they live in the gitignored `art/` folder and are only bundled into builds you install on your own watch. Without them, every alien falls back to an original hourglass emblem.

```sh
python3 tools/fetch_alien_art.py        # candidates + contact sheets in art/candidates/
# review the sheets, copy the picks you approve to art/aliens/<slug>.png
tools/install_watch.sh                  # runs tools/build_alien_art.py, then builds + installs
```

### Rebuilding the face from the design

The face artwork is generated from `design/omnitrix.js`, so the watch matches the prototype pixel for pixel:

```sh
python3 -m venv ~/.cache/omnitrix-venv && ~/.cache/omnitrix-venv/bin/pip install pillow fonttools
tools/install_watch.sh --rebuild
```

Validate against Google's schema with [wff-validator](https://github.com/google/watchface/releases):
`java -jar wff-validator.jar 4 android/watchface/src/main/res/raw/watchface.xml`

### Battery check (a day of wear)

```sh
python3 tools/battery_report.py start    # morning, watch connected: resets Android's battery stats + the app's log
python3 tools/battery_report.py report   # evening: drain per hour, Omnitrix usage, per-app power estimates
```

Reports are saved to the gitignored `diagnostics/` folder.

### When you're done developing

Wireless debugging keeps the watch's Wi-Fi awake, which was the biggest overnight drain in our diagnostics. Turn it off when you're finished (this also disconnects adb):

```sh
tools/wireless_debugging.sh off     # or: tools/wireless_debugging.sh status
```

Turn it back on from the watch in **Settings → Developer options → Wireless debugging**. The IP and port change each time.

See [PLAN.md](PLAN.md) for the design, interaction model, architecture and milestones, [CHANGELOG.md](CHANGELOG.md) for release notes, and [docs/RELEASING.md](docs/RELEASING.md) for the branch and release workflow.

## Disclaimer

Unofficial, non-commercial fan project for personal use. *Ben 10* and the Omnitrix are trademarks of Cartoon Network / Warner Bros. Discovery. This repository contains no official artwork. The face, the UI and the fallback emblems are original. The alien renders are fetched locally by each user for personal use and are excluded from version control and releases.
