#!/usr/bin/env python3
"""Battery diagnostics for a day of wear.

  python3 tools/battery_report.py start    # morning: reset Android battery stats + the app's log
  python3 tools/battery_report.py report   # evening: pull everything and summarise

The report combines the app's own CSV (battery samples every ~15 min + app events) with
Android's batterystats per-app power estimates, so you can see how much of the day's drain
came from the Omnitrix app, the watch face renderer, and everything else. Reports are written
to diagnostics/ (gitignored: it's personal usage data).
"""
import csv
import io
import re
import subprocess
import sys
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ADB = str(Path.home() / "Library/Android/sdk/platform-tools/adb")
CSV_PATH = "/sdcard/Android/data/dev.omnitrix.app/files/omnitrix-diagnostics.csv"
PACKAGES = {
    "dev.omnitrix.app": "Omnitrix app",
    "dev.omnitrix.watchface": "Omnitrix face (package)",
    "com.samsung.wear.watchface.runtime": "Watch face renderer (draws the Omnitrix face)",
}


def adb(*args, check=True):
    serial = next((l.split()[0] for l in subprocess.run([ADB, "devices"], capture_output=True, text=True).stdout.splitlines()[1:]
                   if l.strip().endswith("device")), None)
    if not serial:
        sys.exit("No watch connected (adb connect <ip>:<port>).")
    return subprocess.run([ADB, "-s", serial, *args], capture_output=True, text=True, check=check).stdout


def start():
    adb("shell", "dumpsys", "batterystats", "--reset")
    adb("shell", "rm", "-f", CSV_PATH, check=False)
    adb("shell", "am", "broadcast", "-n", "dev.omnitrix.app/.RefreshReceiver", "-a", "android.intent.action.MY_PACKAGE_REPLACED", check=False)
    print("Battery stats reset and diagnostics log cleared. Unplug the watch and wear it as usual,\n"
          "then run:  python3 tools/battery_report.py report")


def uid_of(package):
    out = adb("shell", "cmd", "package", "list", "packages", "-U", package, check=False)
    m = re.search(rf"package:{re.escape(package)} uid:(\d+)", out)
    if not m:
        return None
    uid = int(m.group(1))
    return f"u0a{uid - 10000}" if uid >= 10000 else str(uid)


def uid_names():
    """{'u0a172': 'com.example', '1000': 'android (system)', ...}"""
    names = {"1000": "android system", "0": "kernel/root", "2000": "adb shell (testing)"}
    for line in adb("shell", "cmd", "package", "list", "packages", "-U", check=False).splitlines():
        m = re.match(r"package:(\S+) uid:(\d+)", line)
        if m:
            uid = int(m.group(2))
            key = f"u0a{uid - 10000}" if uid >= 10000 else str(uid)
            names.setdefault(key, m.group(1))
    return names


def power_estimates():
    """{uid_string: mAh} from batterystats' 'Estimated power use' section."""
    text = adb("shell", "dumpsys", "batterystats", "--charged", check=False)
    result, capacity, total = {}, None, None
    section = False
    for line in text.splitlines():
        if "Estimated power use (mAh)" in line:
            section = True
            continue
        if section:
            if not line.strip():
                if result:
                    break
                continue
            m = re.search(r"Capacity: ([\d.]+), Computed drain: ([\d.]+)", line)
            if m:
                capacity, total = float(m.group(1)), float(m.group(2))
            m = re.match(r"\s*UID (\S+): ([\d.]+)", line)
            if m:
                result[m.group(1)] = float(m.group(2))
    screen = re.search(r"Screen on: ([^\n(]+)", text)
    return result, capacity, total, screen.group(1).strip() if screen else None


def report():
    raw = adb("shell", "cat", CSV_PATH, check=False)
    rows = list(csv.DictReader(io.StringIO(raw))) if raw.startswith("epoch_ms") else []
    lines = [f"# Omnitrix battery report · {datetime.now():%Y-%m-%d %H:%M}", ""]

    samples = [r for r in rows if r["battery_pct"].lstrip("-").isdigit() and int(r["battery_pct"]) >= 0]
    off = [r for r in samples if r["charging"] == "0"]
    if len(off) >= 2:
        # longest unplugged stretch
        runs, run = [], [off[0]]
        for a, b in zip(off, off[1:]):
            if int(b["epoch_ms"]) - int(a["epoch_ms"]) > 3 * 3600_000 or int(b["battery_pct"]) > int(a["battery_pct"]) + 2:
                runs.append(run); run = []
            run.append(b)
        runs.append(run)
        best = max(runs, key=len)
        hours = (int(best[-1]["epoch_ms"]) - int(best[0]["epoch_ms"])) / 3600_000
        drop = int(best[0]["battery_pct"]) - int(best[-1]["battery_pct"])
        screen_on = sum(r["screen_on"] == "1" for r in best) / len(best)
        lines += ["## Day of wear (from the app's log)", "",
                  f"- Off the charger: **{hours:.1f} h**, battery {best[0]['battery_pct']}% → {best[-1]['battery_pct']}% (−{drop}%)",
                  f"- Average drain: **{drop / hours:.1f}%/h**" + (f" → full battery lasts ~{100 / (drop / hours):.0f} h" if drop > 0 else "") if hours > 0 else "- Not enough time yet",
                  f"- Screen on in {screen_on:.0%} of samples", ""]
    else:
        lines += ["## Day of wear", "", "- Not enough samples yet (they're logged every ~15 min).", ""]

    events = {}
    for r in rows:
        events[r["event"]] = events.get(r["event"], 0) + 1
    app_ms = sum(int(r["detail"].removeprefix("ms=") or 0) for r in rows if r["event"] == "app_close" and r["detail"].startswith("ms="))
    lines += ["## Omnitrix usage", "",
              f"- App opened {events.get('app_open', 0)}×, total {app_ms / 60000:.1f} min on screen",
              f"- Transformations: {events.get('transform', 0)} · timeouts: {events.get('timeout', 0)} · reverts: {events.get('revert', 0)}", ""]

    estimates, capacity, total, screen = power_estimates()
    if estimates:
        lines += ["## Android's power estimate since `start`", ""]
        if capacity and total:
            lines.append(f"- Total computed drain: {total:.0f} mAh of {capacity:.0f} mAh ({total / capacity:.0%})")
        if screen:
            lines.append(f"- Screen on: {screen}")
        for package, label in PACKAGES.items():
            uid = uid_of(package)
            mah = estimates.get(uid, 0.0) if uid else 0.0
            share = f" ({mah / total:.1%} of drain)" if total else ""
            lines.append(f"- {label}: **{mah:.1f} mAh**{share}")
        names = uid_names()
        top = sorted(estimates.items(), key=lambda kv: -kv[1])[:6]
        lines += ["", "Top consumers:", *[f"- {names.get(k, k)}: {v:.0f} mAh" for k, v in top], ""]

    out_dir = ROOT / "diagnostics"
    out_dir.mkdir(exist_ok=True)
    (out_dir / f"omnitrix-diagnostics-{datetime.now():%Y%m%d-%H%M}.csv").write_text(raw)
    path = out_dir / f"report-{datetime.now():%Y%m%d-%H%M}.md"
    path.write_text("\n".join(lines) + "\n")
    print("\n".join(lines))
    print(f"\nSaved {path.relative_to(ROOT)}")


if __name__ == "__main__":
    {"start": start, "report": report}.get(sys.argv[1] if len(sys.argv) > 1 else "", lambda: sys.exit(__doc__))()
