# OMNITRIX OS

A Ben 10 Omnitrix experience for the **Samsung Galaxy Watch8 (44mm)**. It's more than a skin: a watch face plus a companion Wear OS app that brings the dial pop-up, the alien selector, the slam to transform, the timeout beeps, and battery-driven energy states.

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
4. Long-press the watch face and pick **Omnitrix**.

### Rebuilding the face from the design

The face artwork is generated from `design/omnitrix.js`, so the watch matches the prototype pixel for pixel:

```sh
python3 -m venv ~/.cache/omnitrix-venv && ~/.cache/omnitrix-venv/bin/pip install pillow fonttools
tools/install_watch.sh --rebuild
```

Validate against Google's schema with [wff-validator](https://github.com/google/watchface/releases):
`java -jar wff-validator.jar 4 android/watchface/src/main/res/raw/watchface.xml`

See [PLAN.md](PLAN.md) for the full UX, interaction model, architecture and milestones.

## Disclaimer

Unofficial, non-commercial fan project for personal use. *Ben 10* and the Omnitrix are trademarks of Cartoon Network / Warner Bros. Discovery. This repo contains no official artwork; all glyphs and visuals are original.
