#!/usr/bin/env python3
"""Writes the wallpaper fixtures the screenshot scenes install instead of the system wallpaper.

A system wallpaper is not a fixed input: it ships with the emulator image, changes with the image, and
differs between a contributor's machine and CI. Every baseline containing it would expire for reasons
that have nothing to do with the launcher. So the scenes install these two instead - committed,
generated and reproducible, and deliberately unlike each other so a scrim tuned for one is visibly
wrong over the other.

Run from the repository root:  python3 scripts/make-screenshot-fixtures.py
"""

import math
import pathlib
import struct
import zlib

WIDTH, HEIGHT = 1080, 2400
OUT = pathlib.Path(__file__).resolve().parent.parent / "app/src/androidTest/assets/screenshots"


def blob(x, y, cx, cy, radius):
    """A soft circular falloff in [0,1], used to give the flat gradients some shape."""
    return max(0.0, 1.0 - math.hypot(x - cx, y - cy) / radius) ** 2


def mix(base, top, amount):
    return tuple(int(round(b + (t - b) * amount)) for b, t in zip(base, top))


def dark_wallpaper():
    """Deep navy to near-black, with a teal highlight. The average stays dark."""
    top, bottom = (11, 31, 58), (5, 7, 12)
    rows = []
    for y in range(HEIGHT):
        t = y / (HEIGHT - 1)
        row = bytearray()
        for x in range(WIDTH):
            colour = mix(top, bottom, t)
            colour = mix(colour, (30, 111, 122), 0.55 * blob(x, y, 0.30 * WIDTH, 0.22 * HEIGHT, 0.55 * WIDTH))
            colour = mix(colour, (120, 60, 90), 0.30 * blob(x, y, 0.78 * WIDTH, 0.68 * HEIGHT, 0.45 * WIDTH))
            row += bytes(colour)
        rows.append(row)
    return rows


def light_wallpaper():
    """Warm off-white with a cool shadow. This is the one the light scrim has to survive."""
    top, bottom = (244, 241, 234), (220, 216, 208)
    rows = []
    for y in range(HEIGHT):
        t = y / (HEIGHT - 1)
        row = bytearray()
        for x in range(WIDTH):
            colour = mix(top, bottom, t)
            colour = mix(colour, (255, 252, 244), 0.7 * blob(x, y, 0.25 * WIDTH, 0.18 * HEIGHT, 0.5 * WIDTH))
            colour = mix(colour, (154, 166, 178), 0.75 * blob(x, y, 0.82 * WIDTH, 0.74 * HEIGHT, 0.5 * WIDTH))
            row += bytes(colour)
        rows.append(row)
    return rows


def chunk(tag, payload):
    return struct.pack(">I", len(payload)) + tag + payload + struct.pack(">I", zlib.crc32(tag + payload) & 0xFFFFFFFF)


def write_png(path, rows):
    raw = b"".join(b"\x00" + bytes(row) for row in rows)
    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", WIDTH, HEIGHT, 8, 2, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9))
    png += chunk(b"IEND", b"")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(png)
    print(f"wrote {path.relative_to(path.parent.parent.parent)} ({len(png) // 1024} KiB)")


if __name__ == "__main__":
    write_png(OUT / "wallpaper-dark.png", dark_wallpaper())
    write_png(OUT / "wallpaper-light.png", light_wallpaper())