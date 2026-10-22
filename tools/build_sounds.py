#!/usr/bin/env python3
"""Synthesize the Omnitrix sound cues (original audio, standard library only).

Writes 22.05 kHz mono 16-bit WAVs to android/app/src/main/res/raw/:
  sfx_dial_up   dial pops up            sfx_tick     carousel detent
  sfx_slam      transform surge         sfx_timeout  four beeps + power-down
  sfx_revert    voluntary revert        sfx_denied   no energy / recharging
  sfx_ready     recharge finished

Usage:  python3 tools/build_sounds.py
"""
import math
import random
import struct
import wave
from pathlib import Path

RATE = 22050
OUT = Path(__file__).resolve().parent.parent / "android/app/src/main/res/raw"
rnd = random.Random(10)


def silence(seconds):
    return [0.0] * int(RATE * seconds)


def envelope(n, attack=0.004, release=0.04):
    a, r = int(RATE * attack), int(RATE * release)
    return [min(1.0, i / max(1, a), (n - i) / max(1, r)) for i in range(n)]


def tone(freq, seconds, shape="sine", attack=0.004, release=0.04, gain=1.0, freq_end=None):
    n = int(RATE * seconds)
    env = envelope(n, attack, release)
    out, phase = [], 0.0
    for i in range(n):
        f = freq if freq_end is None else freq + (freq_end - freq) * i / n
        phase += 2 * math.pi * f / RATE
        if shape == "sine":
            v = math.sin(phase)
        elif shape == "square":   # softened square: odd harmonics only, so it isn't harsh
            v = math.sin(phase) + math.sin(3 * phase) / 3 + math.sin(5 * phase) / 5
        else:                     # gentle saw
            v = sum(math.sin(k * phase) / k for k in range(1, 6))
        out.append(v * env[i] * gain)
    return out


def noise(seconds, gain=1.0, release=0.05):
    n = int(RATE * seconds)
    env = envelope(n, 0.001, release)
    return [(rnd.random() * 2 - 1) * env[i] * gain for i in range(n)]


def mix(*tracks, offsets=None):
    offsets = offsets or [0.0] * len(tracks)
    length = max(int(RATE * o) + len(t) for t, o in zip(tracks, offsets))
    out = [0.0] * length
    for t, o in zip(tracks, offsets):
        start = int(RATE * o)
        for i, v in enumerate(t):
            out[start + i] += v
    return out


def concat(*parts):
    return [v for p in parts for v in p]


def write(name, samples, peak_db=-4.0):
    peak = max(abs(v) for v in samples) or 1.0
    scale = (10 ** (peak_db / 20)) / peak
    OUT.mkdir(parents=True, exist_ok=True)
    with wave.open(str(OUT / f"{name}.wav"), "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, v * scale)) * 32767)) for v in samples))
    print(f"  {name:12s} {len(samples) / RATE:.2f}s")


def main():
    write("sfx_dial_up", mix(noise(0.02, 0.6), concat(tone(520, 0.07, "square"), tone(880, 0.12, "square", release=0.08)), offsets=[0, 0.01]))
    write("sfx_tick", mix(tone(1650, 0.022, "sine", 0.001, 0.018), noise(0.012, 0.35, 0.01)), peak_db=-9)
    write("sfx_slam", mix(
        tone(220, 0.45, "saw", 0.01, 0.06, 0.8, freq_end=1700),       # rising surge
        noise(0.5, 0.25, 0.3),                                        # whoosh
        tone(95, 0.35, "sine", 0.002, 0.3, 1.4, freq_end=55),         # thump
        offsets=[0, 0, 0.38]))
    beeps = concat(*[concat(tone(1960, 0.16, "square", 0.003, 0.03, 0.8), silence(0.18)) for _ in range(4)])
    write("sfx_timeout", concat(beeps, tone(320, 0.55, "saw", 0.01, 0.25, 0.9, freq_end=110)))
    write("sfx_revert", mix(tone(1200, 0.35, "saw", 0.005, 0.12, 0.7, freq_end=300), tone(90, 0.25, "sine", 0.002, 0.2, 1.0), offsets=[0, 0.25]))
    write("sfx_denied", concat(tone(170, 0.18, "saw", 0.005, 0.05), silence(0.06), tone(125, 0.22, "saw", 0.005, 0.08)), peak_db=-6)
    write("sfx_ready", concat(tone(660, 0.09, "sine"), tone(880, 0.09, "sine"), tone(1320, 0.18, "sine", release=0.12)))


if __name__ == "__main__":
    main()
