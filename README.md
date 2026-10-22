# OMNITRIX OS

A Ben 10 Omnitrix experience for the **Samsung Galaxy Watch8 (44mm)**. It's more than a skin: a minimal Omnitrix watch face plus a companion Wear OS app. Tap the face and the dial pops up. Spin the bezel through **50 aliens** projected as holograms, slam to transform, and the face turns into the white alien badge until your time runs out.

- **Watch face** (Watch Face Format v4): a big hourglass core, the time printed small in the top bulb, battery-driven energy states, and a powered-down always-on mode.
- **Omnitrix app**: a hologram selector with a DNA carousel on the bezel, a dossier for each alien, the transform flash with haptics, a live countdown, hold to revert, and the timeout buzz.
- **Roster**: all 22 original-series aliens, the Alien Force line-up, the best of Ultimate Alien and five Ultimate forms ([data/aliens.json](data/aliens.json)).

| Battery | Face |
|---|---|
| > 90% | **Master Control**: gold bezel, every alien unlocked, no timeout |
| 20–90% | **Omnitrix**: classic green hourglass |
| < 20% | **Recharge**: red, transforming locked |
| transformed | **Alien form**: white badge with countdown |

## Try the design prototype

```sh
cd design && python3 -m http.server 8765
# open http://localhost:8765
```

Click the face to raise the dial, scroll to cycle aliens, click again to transform.

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

See [PLAN.md](PLAN.md) for the full UX, interaction model, architecture and milestones, [CHANGELOG.md](CHANGELOG.md) for release notes, and [docs/RELEASING.md](docs/RELEASING.md) for the branch and release workflow.

## Disclaimer

Unofficial, non-commercial fan project for personal use. *Ben 10* and the Omnitrix are trademarks of Cartoon Network / Warner Bros. Discovery. This repository contains no official artwork. The face, the UI and the fallback emblems are original. The alien renders are fetched locally by each user for personal use and are excluded from version control and releases.
