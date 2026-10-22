#!/usr/bin/env python3
"""Build the Omnitrix Watch Face Format resources from the design prototype.

1. Cuts static Orbitron weights from the variable font (fonttools).
2. Renders every face layer with headless Chrome via design/export.html,
   so the watch uses exactly the same artwork as the prototype.
3. Crops layers to their bounding boxes (watch memory is precious).
4. Writes res/raw/watchface.xml with the measured positions.

Usage:  python3 tools/build_watchface.py   (needs Pillow + fonttools, see README)
"""
import functools
import http.server
import os
import subprocess
import sys
import tempfile
import threading
import urllib.request
from pathlib import Path

from PIL import Image
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "android/watchface/src/main/res"
FONT_CACHE = Path.home() / ".cache/omnitrix-fonts"
CHROME = os.environ.get("CHROME", "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome")
FONT_URLS = {
    "Orbitron-VF.ttf": "https://raw.githubusercontent.com/google/fonts/main/ofl/orbitron/Orbitron%5Bwght%5D.ttf",
}

THEMES = ["green", "master", "red"]
ALIEN_COUNT = 13
APP_STATE_PROVIDER = "dev.omnitrix.app/dev.omnitrix.app.complication.OmnitrixStateService"
# Text colours per theme, mirroring THEMES in design/omnitrix.js
COLORS = {
    "green":  {"text": "#8AFF52", "ink": "#062A02", "slit": "#5DFF2A", "glow": "#4DFF1F", "dim": "#4F7A45"},
    "master": {"text": "#D6FF6A", "ink": "#0A2A02", "slit": "#F4FF9A", "glow": "#C4FF3A", "dim": "#8A8A45"},
    "red":    {"text": "#FF5A46", "ink": "#2A0202", "slit": "#FF3A2A", "glow": "#FF2A1A", "dim": "#7A3A35"},
    "alien":  {"text": "#9BFF66", "ink": "#0D3008", "slit": "#9BFF66", "glow": "#4DFF1F", "dim": "#9BFF66"},
}


# ---------------------------------------------------------------- fonts
def build_fonts():
    FONT_CACHE.mkdir(parents=True, exist_ok=True)
    for name, url in FONT_URLS.items():
        if not (FONT_CACHE / name).exists():
            urllib.request.urlretrieve(url, FONT_CACHE / name)
    (RES / "font").mkdir(parents=True, exist_ok=True)
    for weight, res_name in [(900, "orbitron_black"), (700, "orbitron_bold")]:
        font = instantiateVariableFont(TTFont(FONT_CACHE / "Orbitron-VF.ttf"), {"wght": weight})
        font.save(RES / "font" / f"{res_name}.ttf")


# ---------------------------------------------------------------- rendering
def serve_repo():
    class Quiet(http.server.SimpleHTTPRequestHandler):
        def log_message(self, *args):
            pass
    handler = functools.partial(Quiet, directory=str(ROOT))
    server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    return server


def render(port, query, out):
    subprocess.run([
        CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars",
        "--force-device-scale-factor=1", "--window-size=480,480",
        "--default-background-color=00000000", "--virtual-time-budget=5000",
        f"--screenshot={out}", f"http://127.0.0.1:{port}/design/export.html?{query}",
    ], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def export_layers():
    """Render all layers; returns {resource_name: (x, y, w, h)}."""
    jobs = {}
    for th in THEMES + ["alien"]:
        jobs[f"base_{th}"] = (f"layer=base&theme={th}", False)
    for i in range(ALIEN_COUNT):
        jobs[f"emblem_{i}"] = (f"layer=emblem&i={i}", True)
    for k in range(4):
        jobs[f"slit_{k}"] = (f"layer=slit&k={k}", True)
    jobs["orbit"] = ("layer=orbit", True)
    jobs["warn_red"] = ("layer=warn&theme=red", True)
    jobs["icon_heart"] = ("layer=heart", True)
    jobs["icon_bolt"] = ("layer=bolt", True)
    jobs["ambient"] = ("layer=ambient", False)
    jobs["preview"] = ("layer=preview&theme=green", False)

    drawable = RES / "drawable-nodpi"
    drawable.mkdir(parents=True, exist_ok=True)
    boxes = {}
    server = serve_repo()
    try:
        with tempfile.TemporaryDirectory() as tmp:
            for name, (query, crop) in jobs.items():
                raw = Path(tmp) / f"{name}.png"
                render(server.server_port, query, raw)
                img = Image.open(raw).convert("RGBA")
                box = img.getbbox() if crop else (0, 0, 480, 480)
                if box is None:
                    sys.exit(f"layer {name} rendered empty")
                img.crop(box).save(drawable / f"{name}.png", optimize=True)
                boxes[name] = (box[0], box[1], box[2] - box[0], box[3] - box[1])
                print(f"  {name:12s} {boxes[name]}")
    finally:
        server.shutdown()
    return boxes


# ---------------------------------------------------------------- XML
def image(name, boxes, extra=""):
    x, y, w, h = boxes[name]
    return f'<PartImage x="{x}" y="{y}" width="{w}" height="{h}" {extra}><Image resource="{name}"/></PartImage>'


def text(x, y, w, h, template, size, color, family="orbitron_black", align="CENTER",
         glow=None, spacing=None, name="", upper=False):
    deco = f'<OutGlow color="{glow}" radius="10"/>' if glow else ""
    ls = f' letterSpacing="{spacing}"' if spacing is not None else ""
    nm = f' name="{name}"' if name else ""
    # Static strings go straight into <Font>; <Template> requires a <Parameter>.
    body = f"<Template>{template}</Template>" if "<Parameter" in template else template
    if upper:   # e.g. [DAY_OF_WEEK_S] is "Fri"; the face wants "FRI"
        body = f"<Upper>{body}</Upper>"
    return (f'<PartText x="{x}" y="{y}" width="{w}" height="{h}"{nm}>'
            f'<Text align="{align}"><Font family="{family}" size="{size}" color="{color}"{ls}>'
            f'{deco}{body}</Font></Text></PartText>')


def theme_layer(th, boxes):
    c = COLORS[th]
    parts = [image(f"base_{th}", boxes)]

    # Energy slits: one per battery quarter, tinted with the theme colour.
    for k in range(4):
        slit = image(f"slit_{k}", boxes, f'tintColor="{c["slit"]}"')
        if th == "red":   # low energy: the slit blinks
            slit = (f'<Condition><Expressions><Expression name="on">[SECOND] % 2 == 0</Expression></Expressions>'
                    f'<Compare expression="on">{slit}</Compare></Condition>')
        parts.append(f'<Condition><Expressions><Expression name="lit">[BATTERY_PERCENT] &gt; {k * 25}</Expression>'
                     f'</Expressions><Compare expression="lit">{slit}</Compare></Condition>')

    # Seconds track: a dashed arc whose dashes land exactly on the 60 ticks.
    circumference = 2 * 3.14159265 * 203.5
    parts.append(
        f'<PartDraw x="0" y="0" width="480" height="480">'
        f'<Arc centerX="240" centerY="240" width="407" height="407" startAngle="-0.3" endAngle="360">'
        f'<Transform target="endAngle" value="[SECOND] * 6 + 0.9"/>'
        f'<Stroke color="{c["text"]}" thickness="7" dashIntervals="2 {circumference / 60 - 2:.3f}"/></Arc></PartDraw>')

    if th == "master":
        x, y, w, h = boxes["orbit"]
        parts.append(
            f'<Group name="orbit" x="{x}" y="{y}" width="{w}" height="{h}" pivotX="{(240 - x) / w:.4f}" pivotY="{(240 - y) / h:.4f}">'
            f'<Transform target="angle" value="[SECONDS_IN_DAY] % 360"/>'
            + image("orbit", {"orbit": (0, 0, w, h)}, f'tintColor="{c["slit"]}"') + '</Group>')
    if th == "red":
        parts.append(f'<Condition><Expressions><Expression name="on">[SECOND] % 2 == 0</Expression></Expressions>'
                     f'<Compare expression="on">{image("warn_red", boxes)}</Compare></Condition>')

    # Time: hours and minutes glow in the black wedges; the hourglass waist is the colon.
    parts.append(text(96, 205, 140, 70, '%s<Parameter expression="[HOUR_1_12_Z]"/>', 54, c["text"], glow=c["glow"], name="hours"))
    parts.append(text(244, 205, 140, 70, '%s<Parameter expression="[MINUTE_Z]"/>', 54, c["text"], glow=c["glow"], name="minutes"))
    # Date and battery in the top bulb, heart rate in the bottom bulb.
    parts.append(text(160, 113, 160, 28, '%s %s<Parameter expression="[DAY_OF_WEEK_S]"/><Parameter expression="[DAY_Z]"/>',
                      20, c["ink"], spacing=0.1, upper=True))
    _, _, bw, bh = boxes["icon_bolt"]
    parts.append(f'<Group name="battery" x="196" y="150" width="96" height="26">'
                 f'<PartImage x="0" y="{(26 - bh) // 2}" width="{bw}" height="{bh}" tintColor="{c["ink"]}"><Image resource="icon_bolt"/></PartImage>'
                 + text(bw + 3, 0, 70, 26, '%d%%<Parameter expression="[BATTERY_PERCENT]"/>', 15, c["ink"], family="orbitron_bold", align="START")
                 + '</Group>')
    _, _, hw, hh = boxes["icon_heart"]
    bpm_none = text(hw + 14, 2, 70, 36, "--", 28, c["ink"], align="START")
    bpm = text(hw + 14, 2, 70, 36, '%d<Parameter expression="[HEART_RATE]"/>', 28, c["ink"], align="START")
    parts.append(f'<Group name="heart_rate" x="186" y="314" width="110" height="40">'
                 f'<Launch target="HEALTH_HEART_RATE"/>'
                 f'<PartImage x="6" y="{(40 - hh) // 2}" width="{hw}" height="{hh}" tintColor="{c["ink"]}"><Image resource="icon_heart"/></PartImage>'
                 f'<Condition><Expressions><Expression name="none">[HEART_RATE] == 0</Expression></Expressions>'
                 f'<Compare expression="none">{bpm_none}</Compare><Default>{bpm}</Default>'
                 f'</Condition></Group>')
    parts.append(text(200, 355, 80, 16, "BPM", 11, c["ink"], family="orbitron_bold", spacing=0.25))
    if th == "green":
        parts.append(f'<PartText x="0" y="0" width="480" height="480">'
                     f'<TextCircular centerX="240" centerY="240" width="344" height="344" startAngle="242" endAngle="118" direction="COUNTER_CLOCKWISE">'
                     f'<Font family="orbitron_bold" size="11" color="{c["dim"]}" letterSpacing="0.25">'
                     f'<Template>%d STEPS<Parameter expression="[STEP_COUNT]"/></Template></Font></TextCircular></PartText>')
    return "\n".join(parts)


def alien_slot(boxes):
    """Hidden slot over the core, fed by the Omnitrix app.

    value 0 = human (draws nothing), value n = alien n-1: the slot repaints the face as
    the white alien badge. Tapping the core opens the app (WFF can't launch apps directly).
    """
    c = COLORS["alien"]
    value = "[COMPLICATION.RANGED_VALUE_VALUE]"
    emblems = "".join(
        f'<Compare expression="a{i}">'
        + image(f"emblem_{i}", {f"emblem_{i}": (boxes[f"emblem_{i}"][0], boxes[f"emblem_{i}"][1] - 128,
                                               boxes[f"emblem_{i}"][2], boxes[f"emblem_{i}"][3])}, f'tintColor="{c["ink"]}"')
        + "</Compare>" for i in range(ALIEN_COUNT))
    emblem_exprs = "".join(f'<Expression name="a{i}">{value} == {i + 1}</Expression>' for i in range(ALIEN_COUNT))
    _, _, hw, hh = boxes["icon_heart"]
    face = "\n".join([
        image("base_alien", boxes),
        f'<Condition><Expressions>{emblem_exprs}</Expressions>{emblems}</Condition>',
        text(160, 140, 160, 20, '%s<Parameter expression="[COMPLICATION.TITLE]"/>', 13, c["ink"], spacing=0.1),
        text(96, 205, 140, 70, '%s<Parameter expression="[HOUR_1_12_Z]"/>', 54, c["text"], glow=c["glow"]),
        text(244, 205, 140, 70, '%s<Parameter expression="[MINUTE_Z]"/>', 54, c["text"], glow=c["glow"]),
        f'<PartImage x="192" y="{314 + (40 - hh) // 2}" width="{hw}" height="{hh}" tintColor="{c["ink"]}"><Image resource="icon_heart"/></PartImage>',
        text(186 + hw + 14, 316, 70, 36, '%d<Parameter expression="[HEART_RATE]"/>', 28, c["ink"], align="START"),
        text(200, 355, 80, 16, "BPM", 11, c["ink"], family="orbitron_bold", spacing=0.25),
        f'<PartText x="0" y="0" width="480" height="480">'
        f'<TextCircular centerX="240" centerY="240" width="344" height="344" startAngle="242" endAngle="118" direction="COUNTER_CLOCKWISE">'
        f'<Font family="orbitron_bold" size="12" color="{c["text"]}" letterSpacing="0.2">'
        f'<Template>%s REMAINING<Parameter expression="[COMPLICATION.TEXT]"/></Template></Font></TextCircular></PartText>',
    ])
    return f'''    <!-- Full-face slot so the badge can repaint everything (drawing is clipped to the
         slot bounds); only the oval over the core is tappable. -->
    <ComplicationSlot slotId="0" name="omnitrix_core" x="0" y="0" width="480" height="480"
        supportedTypes="RANGED_VALUE" isCustomizable="FALSE">
      <!-- The runtime requires a fallback of a supported type; battery is RANGED_VALUE.
           Alien mode below also checks MAX == ALIEN_COUNT so battery data is ignored. -->
      <DefaultProviderPolicy defaultSystemProvider="WATCH_BATTERY" defaultSystemProviderType="RANGED_VALUE"
          primaryProvider="{APP_STATE_PROVIDER}" primaryProviderType="RANGED_VALUE"/>
      <BoundingBox x="0" y="0" width="480" height="480"/>
      <Complication type="RANGED_VALUE">
        <Condition>
          <Expressions><Expression name="alien">{value} &gt; 0 &amp;&amp; [COMPLICATION.RANGED_VALUE_MAX] == {ALIEN_COUNT}</Expression></Expressions>
          <Compare expression="alien">
            <Group name="alien_form" x="0" y="0" width="480" height="480">
{face}
            </Group>
          </Compare>
        </Condition>
      </Complication>
      <Variant mode="AMBIENT" target="alpha" value="0"/>
    </ComplicationSlot>'''


def watchface_xml(boxes):
    themes = {th: theme_layer(th, boxes) for th in THEMES}
    ambient = "\n".join([
        image("ambient", boxes),
        text(96, 205, 140, 70, '%s<Parameter expression="[HOUR_1_12_Z]"/>', 54, "#3FAE22", family="orbitron_bold"),
        text(244, 205, 140, 70, '%s<Parameter expression="[MINUTE_Z]"/>', 54, "#3FAE22", family="orbitron_bold"),
        text(160, 113, 160, 28, '%s %s<Parameter expression="[DAY_OF_WEEK_S]"/><Parameter expression="[DAY_Z]"/>', 18, "#3FAE22", family="orbitron_bold", upper=True),
    ])
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- GENERATED by tools/build_watchface.py from design/. Do not edit by hand. -->
<WatchFace width="480" height="480" clipShape="CIRCLE">
  <Metadata key="CLOCK_TYPE" value="DIGITAL"/>
  <Metadata key="PREVIEW_TIME" value="10:10:38"/>
  <Scene backgroundColor="#FF000000">
    <!-- Interactive face: theme follows the battery (energy core). -->
    <Group name="interactive" x="0" y="0" width="480" height="480">
      <Variant mode="AMBIENT" target="alpha" value="0"/>
      <Condition>
        <Expressions>
          <Expression name="master">[BATTERY_PERCENT] &gt; 90</Expression>
          <Expression name="recharge">[BATTERY_PERCENT] &lt; 20</Expression>
        </Expressions>
        <Compare expression="master">
          <Group name="master_control" x="0" y="0" width="480" height="480">
{themes["master"]}
          </Group>
        </Compare>
        <Compare expression="recharge">
          <Group name="recharge" x="0" y="0" width="480" height="480">
{themes["red"]}
          </Group>
        </Compare>
        <Default>
          <Group name="omnitrix" x="0" y="0" width="480" height="480">
{themes["green"]}
          </Group>
        </Default>
      </Condition>
    </Group>
    <!-- Always-on display: outline hourglass, under 5% of pixels lit. -->
    <Group name="ambient" x="0" y="0" width="480" height="480" alpha="0">
      <Variant mode="AMBIENT" target="alpha" value="255"/>
{ambient}
    </Group>
    <!-- Alien form: the app's state complication repaints the face when you transform. -->
{alien_slot(boxes)}
  </Scene>
</WatchFace>
'''


def main():
    print("fonts…")
    build_fonts()
    print("layers…")
    boxes = export_layers()
    (RES / "raw").mkdir(parents=True, exist_ok=True)
    (RES / "raw/watchface.xml").write_text(watchface_xml(boxes))
    print("wrote", (RES / "raw/watchface.xml").relative_to(ROOT))


if __name__ == "__main__":
    main()
