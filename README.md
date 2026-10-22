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

See [PLAN.md](PLAN.md) for the full UX, interaction model, architecture and milestones.

## Disclaimer

Unofficial, non-commercial fan project for personal use. *Ben 10* and the Omnitrix are trademarks of Cartoon Network / Warner Bros. Discovery. This repo contains no official artwork; all glyphs and visuals are original.
