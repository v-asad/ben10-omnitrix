# OMNITRIX OS: Design & Build Plan

A full Ben 10 watch experience for the **Samsung Galaxy Watch8 44mm**
(1.47" Super AMOLED, 480 × 480, Wear OS 6 / One UI 8 Watch).

The goal isn't a skin. The watch should *behave* like the Omnitrix: the dial pops up,
you pick an alien, you slam it, you transform, the timer runs out, it beeps, and you're
human again. Its energy is tied to your real battery.

> Interactive prototype: open `design/index.html` (or run `python3 -m http.server` in `design/`).

---

## 1. The four faces

| State | Trigger | Look | Behaviour |
|---|---|---|---|
| **Omnitrix (green)** | battery 20–90% | Classic green hourglass, dark gunmetal bezel, green energy slits | Default. All standard aliens available, timed transformations |
| **Master Control** | battery > 90% | Brighter lime core, **gold-chrome bezel**, all 4 slits lit, full roster orbiting the dial, light sweeping across the core | No timeout, every alien unlocked, faster dial animations |
| **Recharge (red)** | battery < 20% | Red core with a flicker and scan line, a blinking slit, "ENERGY LOW · RECHARGING" on the ring | Transforming is **locked**. Tapping the dial gives a "denied" buzz and a shake |
| **Alien form (white)** | you transformed from the app | White badge with black wedges and a white bezel; the alien's glyph sits in the top bulb | Bezel arc + seconds track count down the remaining alien time |

### Face anatomy (all states)
```
            ┌── curved label: OMNITRIX / MASTER CONTROL / ALIEN FORM
        ╭───┴───╮
   slit ●  THU 22  ● slit      ← 4 energy slits = battery quarters (25/50/75/100)
       ╲  ⚡64%  ╱
   10   ╲      ╱   10          ← hours | minutes live in the black wedges;
         ╲ ·  ╱                   the hourglass waist is the colon (blinks each second)
         ╱    ╲
       ╱  ♥ 72  ╲              ← live heart rate in the bottom bulb
      ╱   BPM    ╲
        ╰───┬───╯
            └── 6,842 STEPS / countdown / warning
   outer ring: 60-tick seconds track (or alien countdown)
```

### Extra states
- **Charging:** while on the charger, the energy slits fill one by one in a loop and the core breathes. The watch is "recharging the Omnitrix" whatever the percentage.
- **Ambient / AOD:** outline-only hourglass with time and HR, under 5% of pixels lit, no glow.
- **Master Control unlock moment:** crossing 90% plays a one-time gold flash and the line "MASTER CONTROL UNLOCKED".

---

## 2. Core interaction loop

```
 HOME (face) ──tap core──▶ DIAL UP (selector)
      ▲                        │ swipe the bezel / drag the ring → cycle DNA (tick haptic per alien)
      │                        │ tap centre → SLAM
      │                        ▼
      │                 GREEN FLASH + whoosh + heavy haptic
      │                        ▼
      │                 ALIEN FORM (white face, countdown)
      │                        │ alien timer hits 0:30 → slow beeping starts
      │                        │ timer hits 0 → 4 fast beeps
      │                        ▼
      └──── cooldown ◀── RED FLASH "TIMED OUT" (red face for the cooldown period)
```

| Action | Input | Feedback |
|---|---|---|
| Raise dial | Tap the core on the face | Core scales up ("pops"), *chk-ching* haptic |
| Cycle aliens | Drag around the bezel (ring follows finger, snaps on release) / rotary input | One tick per alien; the new hologram materializes from the projector |
| Alien dossier | Swipe up on the hologram (swipe down / tap to return) | Full profile slides in |
| Transform | Tap the hologram (or press the top key) | Green full-screen flash, rising-pitch whoosh, long haptic |
| Revert early | Long-press the core in alien form | Reverse flash, no cooldown penalty |
| Timeout | Automatic | Warning beeps, red flash, cooldown |
| Locked (red) | Any transform attempt | Low double buzz, the face shakes, hologram goes dark red |

### The selector screen (dial up)
```
              ▼  ← selection notch at 12 o'clock
         (🔥)        ← DNA carousel: every alien sits on the bezel ring,
    (⚙)        (»)     fading with distance from the top
   ╭──────────────────╮
   │  10:10 · DNA 01/13│
   │      ░▓▓▓░       │  ← hologram: alien art tinted green, scanlines,
   │     ░▓▓▓▓▓░      │     edge fade, sweep line, flicker
   │      ╲▓▓▓╱       │  ← light cone from the projector
   │      ══●══       │  ← projector pad
   │   HEATBLAST      │
   │ PYRONITE · PYROS │
   │ STR▮▮ SPD▮▮ INT▮ DEF▮│
   │ TAP TO TRANSFORM │
   ╰──────────────────╯
```
- Hologram colour follows the energy state: green, gold-lime in Master Control, dead red when locked.
- **Dossier** (swipe up): bigger hologram, species, home world, three powers, weakness, the watch tool it opens, and STR / SPD / INT / DEF arcs in the four bezel quadrants.
- **Artwork:** the app ships with original glyphs. Users can add their own alien images, which are tinted into holograms on the fly. They live on-device and aren't distributed. In the prototype that folder is `design/aliens/`, which is gitignored.

**Timings (user-configurable):** alien time 10 min (default), cooldown 2 min. In Master Control there's no timeout.

---

## 3. Aliens = watch tools

Every alien is an app shortcut, so transforming actually *does* something.
Glyphs are original geometric emblems, not official artwork.

| Alien | Power → Watch function |
|---|---|
| Heatblast | Flashlight (full-screen warm white) |
| XLR8 | Stopwatch |
| Four Arms | Start workout |
| Grey Matter | Calculator |
| Diamondhead | Brightness / display |
| Ripjaws | Water lock |
| Upgrade | Settings |
| Ghostfreak | Do Not Disturb / Theater mode |
| Stinkfly | Weather |
| Wildmutt | Find my phone |
| Cannonbolt | Steps / activity |
| Echo Echo | Music controls |
| Clockwork | Timer |

Each alien gets its own short **transformation tag line** on the flash ("HEATBLAST!").
Inside the tool, a slim white Omnitrix badge sits at the top edge with the countdown,
so you always know you're "transformed".

### The road to 100 aliens: DNA collection
- You start with the **original 10**.
- New DNA unlocks through real activity: hit your step goal 3 days running and you get Cannonbolt. Close a 30-minute workout for Four Arms Ultimate. Sleep 8 h for Ghostfreak's night mode, and so on.
- A **DNA Codex** screen shows the roster as a grid of silhouettes, with locked ones blacked out ("SCAN REQUIRED").
- The counter on the codex reads `013 / 100`. At 100 you earn the **Ben 10,000** title and a permanent gold bezel option.

---

## 4. Bio-scan (heart rate)

The heart rate screen is a "DNA scan":
- A **mini Omnitrix core beats** at your actual BPM, at the top.
- A big BPM readout sits over a **live ECG trace** that scrolls across a faint rotating DNA helix.
- The outer **zone gauge** (40–200 bpm) has five segments, each named after an alien:

| Zone | BPM | Alien | Tag |
|---|---|---|---|
| 1 | < 100 | Grey Matter | CALM |
| 2 | 100–119 | Diamondhead | STEADY |
| 3 | 120–139 | Four Arms | STRENGTH |
| 4 | 140–159 | Heatblast | BURN |
| 5 | 160+ | XLR8 | MAX SPEED |

Zone boundaries come from age-based max HR in settings. The table shows defaults.
- Footer: today's min / resting / max.
- Moving into a new zone gives a short haptic and a glyph swap.
- In alien form the bio-scan is tinted white/green to match.

---

## 5. Tile & complications

- **Omnitrix Tile:** the last 3 aliens used as one-tap transforms, plus current energy state and DNA count.
- **Complication "Omnitrix State"** (provided by the app): alien id + remaining seconds. The face reads it (see §6).
- **Complication "DNA Count"**: short text `13/100` for any other face.

---

## 6. Technical architecture

### Why it's split in two
Watch faces on Wear OS 6 (which the Galaxy Watch8 runs) must use **Watch Face Format (WFF)**,
Google's declarative XML format. Legacy code-based watch faces are being phased out. WFF is battery-efficient
and can render everything above, but it **can't run arbitrary code**, so the interactive
parts live in a companion Wear OS app.

```
┌────────────────────────── Galaxy Watch8 ──────────────────────────┐
│                                                                   │
│  omnitrix-face  (WFF XML)                omnitrix-app (Kotlin)    │
│  ├─ 4 themes via [BATTERY_PERCENT]       ├─ Compose for Wear OS   │
│  │  Conditions  (>90 / 20–90 / <20)      ├─ Selector / transform  │
│  ├─ [HEART_RATE], [STEP_COUNT], time     ├─ Alien timer service   │
│  ├─ tap core → Launch(app/.Selector)     │  (Ongoing Activity)    │
│  ├─ charging: [IS_CHARGING]? anim        ├─ Bio-scan (Health      │
│  └─ full-dial complication slot  ◀────── │  Services, HR)         │
│     reads "Omnitrix State" →             ├─ Complication data     │
│     renders the WHITE alien face         │  source (alien, secs)  │
│                                          ├─ Tile                  │
│                                          └─ DNA Codex + goals     │
└───────────────────────────────────────────────────────────────────┘
```

### Battery → theme (in the face)
Pure WFF expressions on `[BATTERY_PERCENT]`. No app needed, works in ambient, near-zero cost.
WFF is stateless, so thresholds are exact (no hysteresis). That's fine, because battery moves slowly.

### Alien mode → white face (the tricky bit)
The face has to know the app's state. Plan:
1. The app exposes a **complication data source** publishing `RANGED_VALUE` (remaining seconds / total) plus the alien id in its text.
2. The face has a fixed, non-configurable complication slot whose **default provider is our own app**. When it reports an active alien, the slot's own drawing layer covers the dial with the white alien theme. When it's empty, nothing is drawn.
3. **The same slot handles tapping the core.** WFF's `Launch` element only accepts system targets (alarm, heart rate, settings…), so the only way to open our own app from the face is a complication tap. The slot sits over the core, and its tap action opens the selector.
4. The app pushes an update on transform and revert, and on timeout via `ComplicationDataSourceUpdateRequester`.

**Risk:** this needs a hands-on spike on the real watch (update latency, how large a complication may draw, whether default-provider permission is granted automatically).
**Fallback:** while transformed, the app itself stays foreground as an **Ongoing Activity** and draws the white face in Compose. Same look, slightly more battery. The user never sees the difference.

### Inputs on the Watch8
- The Watch8 (non-Classic) has no physical rotating bezel. We support **rotary input events** (`onRotaryScrollEvent`, which also catches Samsung's touch-bezel gesture where it's enabled) plus drag-around-the-ring and swipe.
- Haptics: `Vibrator` with custom waveforms. Tick (dial), double-tick (pop), long ramp (transform), 4 pulses (timeout), low buzz (denied).
- Sound: short OGG samples (synthesized originals, not show audio), off by default, respects system mute.

### Tech stack
- **Face:** WFF v2+ XML, packaged as a resource-only APK, with vector assets exported from the prototype's SVG.
- **App:** Kotlin, Jetpack Compose for Wear OS (Material 3), Health Services (`MeasureClient` for live HR), Tiles + ProtoLayout, `androidx.wear.watchface.complications.datasource`, DataStore for unlocks.
- **Build/deploy:** Gradle, `adb` over Wi-Fi to the watch (developer mode), Samsung Galaxy Wearable for face selection.
- **Font:** Orbitron (OFL) for digits, Chakra Petch (OFL) for labels.

---

## 7. Milestones

1. ✅ **Design sign-off**: this doc + `design/index.html`
2. ✅ **WFF face v1**: green / master / red themes, time, HR, steps, battery slits, AOD. Built from the prototype art by `tools/build_watchface.py`, passes Google's WFF v4 validator, 3.4 MB active / 1.6 MB ambient. **Next: sideload and live with it for a day** (← we are here)
3. ✅ **Spike**: complication bridge for alien mode. **GO**, verified on the Galaxy Watch8:
   - The face's slot binds to our app's provider automatically, with no user setup.
   - The face switches to the white badge within about 2 s of transforming, and the system ticks the countdown with no app wake-ups.
   - Timeout revert by exact alarm works, and tapping the face opens the app.
   - Gotchas: complication drawing is clipped to the slot's *bounding shape*, so the slot spans the whole face and the whole face is the tap target. The fallback provider must be a supported type, so it's battery, filtered out by `RANGED_VALUE_MAX == 13`. Heart rate tap-through is lost in exchange.
4. ✅ **App v1 (v0.1.0)**: hologram selector over a 50-alien roster (OS, AF, UA, Ultimates) with real Alien Force-era art, dossier, transform burst, alien timer, timeout, hold to revert, haptics. Released as v0.1.0. The minimal face redesign and the powered-down AOD shipped with it.
4b. **Next: alien-specific transformation animations** (research + 10 samples for review)
5. **Alien tools**: wire up shortcuts (deferred: the dossier shows each alien's era for now)
6. **Bio-scan**: live HR, ECG trace, zones
7. **Tile + DNA Codex + unlock rules**
8. **Polish**: sound, transition timing, battery profiling (target: < 3% extra drain/day)

---

## 8. Ground rules

- **Personal use only.** Ben 10 and the Omnitrix are Cartoon Network / Warner Bros. Discovery trademarks. We sideload this to your own watch. It doesn't go on Google Play or the Galaxy Store, and the repo carries a fan-project disclaimer.
- All visuals and sounds are **original** recreations in the spirit of the show; no ripped assets.

## 9. Open questions for you

1. Default alien time: **10 min** like the show, or shorter?
2. Should the face's red state also kick in during the post-timeout cooldown (show-accurate), or only on low battery?
3. Which **top 10 aliens** do you want as your starting roster?
4. 12h or 24h time?
