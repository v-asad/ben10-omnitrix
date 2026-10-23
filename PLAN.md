# OMNITRIX OS: Design & Build Plan

A full Ben 10 watch experience for the **Samsung Galaxy Watch8 44mm**
(1.47" Super AMOLED, 480 × 480, Wear OS 6 / One UI 8 Watch).

The goal isn't a skin. The watch should *behave* like the Omnitrix: the dial pops up,
you pick an alien, you slam it, you transform, the timer runs out, it beeps, and you're
human again. Its energy is tied to your real battery, and new DNA is earned by moving.

> Interactive prototype: open `design/index.html` (or run `python3 -m http.server` in `design/`).
> Release notes: [CHANGELOG.md](CHANGELOG.md).

---

## 1. The faces

| State | Trigger | Look | Behaviour |
|---|---|---|---|
| **Omnitrix (green)** | battery 20–90% | Big green hourglass, time and date small in the top bulb, seconds ring | Default. Unlocked aliens available, timed transformations |
| **Master Control** | battery > 90% | Brighter lime core, light sweep, the roster orbiting the dial | Every alien available whatever your DNA Missions progress; alien time ×3 |
| **Low energy (red)** | battery < 20% | Red core with a flicker, scan line and "ENERGY LOW" | Transforming is **locked**: "denied" buzz and a shake |
| **Alien form (white)** | you transformed | White badge, the alien's silhouette and name, countdown in the bottom bulb and on the ring | Ends with four beeps, or hold the core to revert |
| **Timeout (red)** | you switched back (timeout or revert) | Red face, "TIMEOUT" and a countdown | 10 s by default (off, 10 s – 5 min in Settings); transforming is locked until it ends |

### Face anatomy
```
        ╭───────────╮
        │   10:10   │   ← time, small, printed in ink in the top bulb
        │   FRI 23  │   ← date (or alien name / TIMEOUT)
         ╲         ╱
          ╲       ╱
           ╲     ╱      ← hourglass core fills the screen
           ╱     ╲
          ╱       ╲
         ╱  09:48  ╲    ← alien countdown / timeout countdown
        ╰───────────╯
   outer ring: 60-tick seconds track (alien countdown in alien form)
```
No heart rate, steps, battery readout, labels or buttons on the face: the hourglass is the hero.

### Always-on
A powered-down Omnitrix: thin neon outlines, dim bulbs, hour ticks and the time, kept under
15% lit pixels (enforced by `tools/build_watchface.py`). Alien form has its own AOD variant.

---

## 2. Core interaction loop

```
 FACE ──tap anywhere──▶ SELECTOR (hologram)
   ▲                        │ bezel / drag the ring → cycle DNA (one alien per click, tick haptic)
   │                        │ swipe up → dossier   ·   swipe down → Omnitrix menu
   │                        │ tap centre → SLAM
   │                        ▼
   │             ALIEN-SPECIFIC TRANSFORMATION (27 effect families)
   │                        ▼
   │                 ALIEN FORM (white face, countdown)
   │                        │ timer hits 0 → 4 beeps   ·   hold the core → revert
   │                        ▼
   └──── ready ◀── RED TIMEOUT FACE (countdown) ◀──┘
```

| Action | Input | Feedback |
|---|---|---|
| Raise dial | Tap the face (the whole face is the tap target) | Dial-up chirp and haptic |
| Cycle aliens | Turn the bezel (one alien per click) or drag around the ring | Tick per alien; the hologram materializes from the projector |
| Alien dossier | Swipe up on the hologram | Species, home world, powers, weakness, stat arcs |
| Omnitrix menu | Swipe down on the hologram | Bio-scan, DNA Codex, Settings, Battery |
| Transform | Tap the hologram | Alien-specific transformation, whoosh, long haptic |
| Revert early | Hold the core in alien form | Back to human, red timeout |
| Timeout | Automatic | Four beeps, red timeout face |
| Locked | Low battery, timeout running, or alien not yet earned | Low double buzz, the hologram shakes and dims |
| Back | Swipe in from the left edge, on every screen | The screen follows your finger |

### The selector
Just the hologram, no text: the selected alien's art tinted to the energy colour with
scanlines and a projector beam, and the DNA carousel on the bezel. The ring follows the DNA
Missions order (original ten → Summer Vacation → Alien Force → Ultimate Alien → the Ultimates →
Alien X), so the aliens you've unlocked sit together and the next one to earn comes right after.

**Artwork:** official Alien Force-era renders, fetched locally by `tools/fetch_alien_art.py` into
the gitignored `art/` folder and turned into holograms by `tools/build_alien_art.py`. Never
committed, never attached to releases. Without them, aliens fall back to original emblems.

---

## 3. DNA Missions (the DNA Codex)

- You start with the **original ten**. The other **40** unlock one mission at a time.
- **Chapters:** Summer Vacation (12), Alien Force (12), Ultimate Alien (10), the Ultimates (5),
  and the **Final Mission**: Alien X.
- **Objectives:** walk N steps, transform N times, hold alien form until it times out, use N
  different aliens, complete Bio-scans (15 s of readings), keep N active days (5,000+ steps).
  The last mission of each chapter has two objectives; Alien X needs 20,000 steps, 10
  transformations and a 3-day streak.
- **Rewards:** the alien, plus XP (100 / 150 / 200 / 300 per mission by chapter, 1,000 for Alien X).
  Ranks: Rookie → Hero (400) → Plumber (1,200) → Magister (3,000) → Master Control (5,000) →
  Ben 10,000 (every mission done).
- **Screen:** rank and XP bar; the active mission with the reward alien as a mystery hologram
  that fills in as you progress, objective bars and the reward; a chapter map of alien icons;
  a dedicated Final Mission card. A "MISSION COMPLETE" splash shows the new alien and XP.
- Steps come from the hardware step counter, sampled by the existing ~15-minute job (no extra
  wake-ups). Progress for each mission counts from when it starts.
- Settings → DNA Codex off unlocks everything; so does Master Control.

---

## 4. Bio-scan (heart rate)

A minimal heart monitor with no logo and three words of text:
- Zone gauge on the bezel (40 bpm … max HR + 10) with a white needle that pulses on each beat.
- Big BPM readout, a pulse trace paced by your rate, and the zone name.
- Zones are fractions of max HR (220 − age, age in Settings): CALM < 60%, STEADY < 70%,
  STRONG < 80%, BURN < 90%, MAX. Crossing into a new zone gives a tick haptic.

---

## 5. Tiles & complications

- **Tiles:** Omnitrix Bio-scan (live heart rate), Omnitrix DNA Codex (rank, active mission
  progress), Omnitrix settings (quick toggles). Each opens the matching screen; Back returns
  to where you came from.
- **Complication "Omnitrix State"** (provided by the app, consumed by our face): a
  `RANGED_VALUE` whose value encodes the state (0 = human, alien index + 1, count + 1 =
  timeout) with the countdown in its text. The face reads it to draw alien form and timeout.

---

## 6. Technical architecture

Watch faces on Wear OS 6 must use **Watch Face Format (WFF)**, which is declarative and can't
run code, so the interactive parts live in a companion Wear OS app.

```
┌────────────────────────── Galaxy Watch8 ──────────────────────────┐
│  omnitrix-face  (WFF v4 XML)             omnitrix-app (Kotlin)    │
│  ├─ themes via [BATTERY_PERCENT]         ├─ Compose for Wear OS   │
│  ├─ time, date, seconds ring, AOD        ├─ Selector / transforms │
│  └─ full-face complication slot ◀─────── ├─ Complication source   │
│     draws alien form + timeout,          ├─ Timeout alarms        │
│     tap opens the app                    ├─ DNA Missions + steps  │
│                                          ├─ Bio-scan, Tiles       │
│                                          └─ Sounds, haptics, logs │
└───────────────────────────────────────────────────────────────────┘
```

### The complication bridge
- The face has one fixed, full-face complication slot whose default provider is our app.
  Drawing is clipped to the slot's bounding shape, so it spans the whole face, and its tap
  action is the only way to open our app from WFF.
- The fallback provider must be a supported type, so it's WATCH_BATTERY `RANGED_VALUE`,
  filtered out by checking the max value.
- The app pushes updates on transform, revert, timeout and recharge
  (`ComplicationDataSourceUpdateRequester`), and refreshes after an update, boot or app open
  so the tap intent never goes stale. Exact alarms end alien time and timeouts.
- Wear OS hides complications while the watch is locked, so alien form shows only when unlocked.

### Performance
- Single-Canvas screens in a virtual 480 space; animations read only in draw lambdas; no
  per-frame clips.
- `AlienArt` is an LRU, prefetching cache; transformation effects are prebuilt off the main
  thread and the AGSL shader is warmed with a 1 px draw.
- Minified release build with profileinstaller, AOT-compiled on install (`cmd package compile -m speed`).

### Sound & haptics
- SoundPool cues (synthesized originals) play only in Sound mode (never on Vibrate, Mute or
  DND) and follow the system volume. Haptic patterns scale with the Haptics setting.

### Asset pipeline
- `design/omnitrix.js` is the shared SVG renderer. `tools/build_watchface.py` renders face
  layers through `design/export.html` in headless Chrome, builds the fonts and writes
  `watchface.xml`, then checks the AOD budget.
- Validate with Google's `wff-validator` (format 4) and the memory-footprint checker.

### Tech stack
Kotlin, Jetpack Compose for Wear OS, Tiles + ProtoLayout, `androidx.wear.watchface.complications.datasource`,
SharedPreferences, Gradle, `adb` over Wi-Fi. Fonts: Orbitron and Chakra Petch (OFL).

---

## 7. Milestones

1. ✅ **Design sign-off**: this doc + `design/index.html`.
2. ✅ **WFF face**: green / Master Control / red themes, AOD, built from the prototype art.
3. ✅ **Spike**: the complication bridge for alien mode, verified on the Galaxy Watch8.
4. ✅ **v0.1.0**: hologram selector over a 50-alien roster with real art, dossier, transform, alien timer, revert, haptics.
5. ✅ **v0.2.0**: transformations for all 50 aliens, timeout, sound, settings, battery diagnostics, smoothness pass.
6. ✅ **v0.3.x**: Bio-scan and the DNA Codex; AOD lit-pixel fix from overnight diagnostics; face-tap fixes.
7. ✅ **v0.4.x**: tiles, configurable red timeout face, minimal Bio-scan, left-edge swipe back on every screen.
8. ✅ **v0.5.x**: DNA Missions (chapters, XP, ranks, Alien X last), selector in missions order, one alien per bezel click.
9. **Later**: verify tile rendering and real tile previews, a full day's battery check with wireless debugging off, library upgrades (Wear Compose 1.7), gesture control (on hold).
10. **Dropped**: Omnitrix transform tile, aliens as watch tools, heart-rate shortcut on the face.

---

## 8. Ground rules

- **Personal use only.** Ben 10 and the Omnitrix are Cartoon Network / Warner Bros. Discovery
  trademarks. This is sideloaded to your own watch and never goes on Google Play or the Galaxy Store.
- The face, UI, sounds and fallback emblems are **original**. Official alien renders stay in the
  gitignored `art/` folder and generated directories, and no APKs are attached to releases.

## 9. Decisions

- Default alien time: **10 minutes** (1–15 in Settings; tripled in Master Control).
- The face goes red after every switch back (show-accurate timeout), not only on low battery.
- Starting roster: the **original ten**.
- Time follows the watch's 12h / 24h setting.
