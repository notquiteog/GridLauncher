#!/usr/bin/env python3
"""Locates and crops the region where two PNGs differ.

Pure stdlib, all five PNG filters, so it can be trusted on screenshots written by Android's encoder
(which uses Paeth, and which a three-filter decoder will quietly misread into a wrong answer).

  python3 scripts/png-region.py a.png b.png [--crop out.png] [--box x0 y0 x1 y1]
"""
import pathlib
import struct
import sys
import zlib


def read_png(path):
    data = pathlib.Path(path).read_bytes()
    pos, idat, width, height = 8, b"", 0, 0
    while pos < len(data):
        length = struct.unpack(">I", data[pos:pos + 4])[0]
        tag = data[pos + 4:pos + 8]
        payload = data[pos + 8:pos + 8 + length]
        if tag == b"IHDR":
            width, height, depth, colour = struct.unpack(">IIBB", payload[:10])
            assert depth == 8 and colour in (2, 6), "expected 8-bit RGB or RGBA"
            channels = 3 if colour == 2 else 4
        elif tag == b"IDAT":
            idat += payload
        pos += 12 + length
    raw = zlib.decompress(idat)
    stride = width * channels
    rows, previous, i = [], bytearray(stride), 0
    for _ in range(height):
        kind = raw[i]
        line = bytearray(raw[i + 1:i + 1 + stride])
        i += 1 + stride
        if kind == 1:
            for x in range(channels, stride):
                line[x] = (line[x] + line[x - channels]) & 0xFF
        elif kind == 2:
            for x in range(stride):
                line[x] = (line[x] + previous[x]) & 0xFF
        elif kind == 3:
            for x in range(stride):
                left = line[x - channels] if x >= channels else 0
                line[x] = (line[x] + ((left + previous[x]) >> 1)) & 0xFF
        elif kind == 4:
            for x in range(stride):
                a = line[x - channels] if x >= channels else 0
                b = previous[x]
                c = previous[x - channels] if x >= channels else 0
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                pred = a if pa <= pb and pa <= pc else b if pb <= pc else c
                line[x] = (line[x] + pred) & 0xFF
        elif kind != 0:
            raise ValueError(f"unknown filter {kind}")
        rows.append(bytes(line))
        previous = line
    return width, height, channels, rows


def write_png(path, width, height, rows):
    def chunk(tag, payload):
        return struct.pack(">I", len(payload)) + tag + payload + struct.pack(">I", zlib.crc32(tag + payload) & 0xFFFFFFFF)

    raw = b"".join(b"\x00" + bytes(row) for row in rows)
    pathlib.Path(path).write_bytes(
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(raw, 9))
        + chunk(b"IEND", b"")
    )


if __name__ == "__main__":
    args = sys.argv[1:]
    a_path, b_path = args[0], args[1]
    wa, ha, ca, a = read_png(a_path)
    wb, hb, cb, b = read_png(b_path)
    print(f"{a_path}: {wa}x{ha}  {b_path}: {wb}x{hb}")
    assert (wa, ha) == (wb, hb), "different sizes"
    minx, miny, maxx, maxy, count = wa, ha, -1, -1, 0
    for y in range(ha):
        ra, rb = a[y], b[y]
        if ra == rb:
            continue
        for x in range(wa):
            if ra[x * ca:x * ca + 3] != rb[x * cb:x * cb + 3]:
                count += 1
                minx, maxx = min(minx, x), max(maxx, x)
                miny, maxy = min(miny, y), max(maxy, y)
    print(f"exact-differing pixels: {count}")
    if count:
        print(f"bbox x[{minx}..{maxx}] y[{miny}..{maxy}]")
    if "--crop" in args:
        out = args[args.index("--crop") + 1]
        box = [int(v) for v in args[args.index("--box") + 1:args.index("--box") + 5]] if "--box" in args \
            else [max(0, minx - 20), max(0, miny - 20), min(wa, maxx + 20), min(ha, maxy + 20)]
        x0, y0, x1, y1 = box
        rows = [b"\x00\x00\x00" + a[y][x0 * ca:x1 * ca] for y in range(y0, y1)]
        rows += [b"\xff\x00\xff" * (x1 - x0) for _ in range(4)]
        rows += [b"\x00\x00\x00" + b[y][x0 * cb:x1 * cb] for y in range(y0, y1)]
        write_png(out, x1 - x0, (y1 - y0) * 2 + 4, rows)
        print(f"wrote {out} (baseline above, actual below)")