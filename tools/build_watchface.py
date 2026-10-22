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
import shutil
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
# Always-on palette, mirroring AOD in design/omnitrix.js
AOD_COLORS = {
    "green": {"line": "#46C424", "dim": "#0E3A08", "label": "#2A6A1A"},
    "alien": {"line": "#D6F5CC", "dim": "#26332A", "label": "#7FA878"},
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


APP_RES = ROOT / "android/app/src/main/res"


def export_app_assets():
    """Emblems for the companion app: crisp + pre-blurred glow, fixed 240x240 canvas so they align."""
    drawable = APP_RES / "drawable-nodpi"
    drawable.mkdir(parents=True, exist_ok=True)
    (APP_RES / "font").mkdir(parents=True, exist_ok=True)
    for f in ("orbitron_black.ttf", "orbitron_bold.ttf"):
        shutil.copy(RES / "font" / f, APP_RES / "font" / f)
    server = serve_repo()
    try:
        with tempfile.TemporaryDirectory() as tmp:
            for i in range(ALIEN_COUNT):
                for kind, query in (("emblem", f"layer=emblem&i={i}&s=160"),
                                    ("emblem_glow", f"layer=emblemglow&i={i}&s=160&blur=9")):
                    raw = Path(tmp) / f"{kind}_{i}.png"
                    render(server.server_port, query, raw)
                    Image.open(raw).convert("RGBA").crop((120, 120, 360, 360)).save(drawable / f"{kind}_{i}.png", optimize=True)
    finally:
        server.shutdown()
    print(f"  app emblems → {drawable.relative_to(ROOT)}")


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
    jobs["ambient"] = ("layer=ambient", False)
    jobs["ambient_alien"] = ("layer=ambient&mode=alien", False)
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
def image(name, boxes, extra="", dy=0):
    x, y, w, h = boxes[name]
    return f'<PartImage x="{x}" y="{y + dy}" width="{w}" height="{h}" {extra}><Image resource="{name}"/></PartImage>'


def text(x, y, w, h, template, size, color, family="orbitron_black", align="CENTER",
         glow=None, outline=None, spacing=None, name="", upper=False):
    deco = f'<OutGlow color="{glow}" radius="10"/>' if glow else ""
    if outline:
        deco += f'<Outline color="{outline}" width="1.2"/>'
    ls = f' letterSpacing="{spacing}"' if spacing is not None else ""
    nm = f' name="{name}"' if name else ""
    # Static strings go straight into <Font>; <Template> requires a <Parameter>.
    body = f"<Template>{template}</Template>" if "<Parameter" in template else template
    if upper:   # e.g. [DAY_OF_WEEK_S] is "Fri"; the face wants "FRI"
        body = f"<Upper>{body}</Upper>"
    return (f'<PartText x="{x}" y="{y}" width="{w}" height="{h}"{nm}>'
            f'<Text align="{align}"><Font family="{family}" size="{size}" color="{color}"{ls}>'
            f'{deco}{body}</Font></Text></PartText>')


def condition(expr, then, otherwise=None):
    other = f"<Default>{otherwise}</Default>" if otherwise is not None else ""
    return (f'<Condition><Expressions><Expression name="c">{expr}</Expression></Expressions>'
            f'<Compare expression="c">{then}</Compare>{other}</Condition>')


def time_text(color, **kw):
    """Small time printed in the top bulb; follows the phone's 12/24h setting."""
    t12 = text(140, 116, 200, 34, '%s:%s<Parameter expression="[HOUR_1_12_Z]"/><Parameter expression="[MINUTE_Z]"/>', 26, color, **kw)
    t24 = text(140, 116, 200, 34, '%s:%s<Parameter expression="[HOUR_0_23_Z]"/><Parameter expression="[MINUTE_Z]"/>', 26, color, **kw)
    return condition("[IS_24_HOUR_MODE] == 1", t24, t12)


def date_text(color, **kw):
    return text(160, 157, 160, 16, '%s %s<Parameter expression="[DAY_OF_WEEK_S]"/><Parameter expression="[DAY_Z]"/>',
                11, color, family="orbitron_bold", spacing=0.3, upper=True, **kw)


def blink(content):
    return condition("[SECOND] % 2 == 0", content)


def theme_layer(th, boxes):
    c = COLORS[th]
    parts = [image(f"base_{th}", boxes)]

    # Four energy slits: signature Omnitrix lights (decorative, not a battery gauge).
    for k in range(4):
        slit = image(f"slit_{k}", boxes, f'tintColor="{c["slit"]}"')
        parts.append(blink(slit) if th == "red" else slit)

    # Seconds track: a dashed arc whose dashes land exactly on the 60 ticks.
    circumference = 2 * 3.14159265 * 207
    parts.append(
        f'<PartDraw x="0" y="0" width="480" height="480">'
        f'<Arc centerX="240" centerY="240" width="414" height="414" startAngle="-0.3" endAngle="360">'
        f'<Transform target="endAngle" value="[SECOND] * 6 + 0.9"/>'
        f'<Stroke color="{c["text"]}" thickness="6" dashIntervals="2 {circumference / 60 - 2:.3f}"/></Arc></PartDraw>')

    if th == "master":
        x, y, w, h = boxes["orbit"]
        parts.append(
            f'<Group name="orbit" x="{x}" y="{y}" width="{w}" height="{h}" pivotX="{(240 - x) / w:.4f}" pivotY="{(240 - y) / h:.4f}">'
            f'<Transform target="angle" value="[SECONDS_IN_DAY] % 360"/>'
            + image("orbit", {"orbit": (0, 0, w, h)}, f'tintColor="{c["slit"]}"') + '</Group>')
    if th == "red":
        parts.append(blink(image("warn_red", boxes)))

    # Minimal: the hourglass is the hero; time and date are printed small in the top bulb.
    parts.append(time_text(c["ink"]))
    parts.append(date_text("#C0" + c["ink"][1:]))
    return "\n".join(parts)


def alien_slot(boxes):
    """Hidden full-face slot fed by the Omnitrix app.

    value 0 = human (draws nothing), value n = alien n-1: the slot repaints the face as the
    white alien badge. Drawing is clipped to the bounding shape, so the slot spans the whole
    face and any tap opens the app (WFF can't launch apps directly).
    """
    c = COLORS["alien"]
    value = "[COMPLICATION.RANGED_VALUE_VALUE]"
    emblem_exprs = "".join(f'<Expression name="a{i}">{value} == {i + 1}</Expression>' for i in range(ALIEN_COUNT))

    def emblems(tint):
        attr = f'tintColor="{tint}"'
        compares = "".join(f'<Compare expression="a{i}">' + image(f"emblem_{i}", boxes, attr, dy=66) + '</Compare>'
                           for i in range(ALIEN_COUNT))
        return f'<Condition><Expressions>{emblem_exprs}</Expressions>{compares}</Condition>'

    def name(color):
        return text(150, 157, 180, 16, '%s<Parameter expression="[COMPLICATION.TITLE]"/>', 10, color,
                    family="orbitron_bold", spacing=0.15)

    def countdown(color, **kw):
        return text(170, 356, 140, 26, '%s<Parameter expression="[COMPLICATION.TEXT]"/>', 18, color, **kw)

    aod = AOD_COLORS["alien"]
    interactive = "\n".join([
        image("base_alien", boxes),
        time_text(c["ink"]), name(c["ink"]), emblems(c["ink"]), countdown(c["ink"]),
    ])
    ambient = "\n".join([
        '<PartDraw x="0" y="0" width="480" height="480"><Ellipse x="0" y="0" width="480" height="480"><Fill color="#FF000000"/></Ellipse></PartDraw>',
        image("ambient_alien", boxes),
        time_text(aod["dim"], outline=aod["line"]), name(aod["label"]), emblems(aod["line"]),
        countdown(aod["label"], family="orbitron_bold"),
    ])
    return f'''    <ComplicationSlot slotId="0" name="omnitrix_core" x="0" y="0" width="480" height="480"
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
              <Variant mode="AMBIENT" target="alpha" value="0"/>
{interactive}
            </Group>
            <Group name="alien_aod" x="0" y="0" width="480" height="480" alpha="0">
              <Variant mode="AMBIENT" target="alpha" value="255"/>
{ambient}
            </Group>
          </Compare>
        </Condition>
      </Complication>
    </ComplicationSlot>'''


def watchface_xml(boxes):
    themes = {th: theme_layer(th, boxes) for th in THEMES}
    aod = AOD_COLORS["green"]
    ambient = "\n".join([
        image("ambient", boxes),
        time_text(aod["dim"], outline=aod["line"]),
        date_text(aod["label"]),
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
    <!-- Always-on display: a powered-down Omnitrix. -->
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
    print("app assets…")
    export_app_assets()
    print("wrote", (RES / "raw/watchface.xml").relative_to(ROOT))


if __name__ == "__main__":
    main()
