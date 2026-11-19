#!/usr/bin/env python3
"""A short test film with Blu-ray (PGS) picture subtitles, for checking burn-in against a scratch Arc.

ffmpeg can't turn text into picture subtitles, so this draws two captions with Pillow, writes them
as a PGS (.sup) stream, and muxes them with a test pattern into an MKV:

    python3 tools/pgs-test-film.py "/tmp/arc-tv-media/Movies/Arrival (2016)/Arrival (2016).mkv"

Needs ffmpeg and Pillow, and a bold TrueType font (DejaVu Sans Bold by default, or --font).
"""
import argparse
import os
import struct
import subprocess
import tempfile

from PIL import Image, ImageDraw, ImageFont

W, H = 1280, 720
CAPTIONS = [(0.5, 4.0, "BURNED IN BY ARC"), (4.0, 9.5, "PICTURE SUBTITLES WORK")]


def seg(pts, typ, data):
    """One PGS segment: magic, presentation and decoding times (90 kHz), type, size, data."""
    return b"PG" + struct.pack(">IIBH", pts, 0, typ, len(data)) + data


def rle(img):
    """PGS run-length coding of a palette image, line by line."""
    px = img.load()
    out = bytearray()
    for y in range(img.height):
        x = 0
        while x < img.width:
            c = px[x, y]
            n = 1
            while x + n < img.width and px[x + n, y] == c and n < 16383:
                n += 1
            if c != 0 and n <= 2:
                out += bytes([c]) * n
            elif c == 0:
                out += bytes([0, n]) if n < 64 else bytes([0, 0x40 | (n >> 8), n & 0xFF])
            else:
                out += bytes([0, 0x80 | n, c]) if n < 64 else bytes([0, 0xC0 | (n >> 8), n & 0xFF, c])
            x += n
        out += b"\x00\x00"
    return bytes(out)


def caption(text, font):
    """White text with a black outline: palette 0 clear, 1 white, 2 black."""
    img = Image.new("P", (900, 80), 0)
    d = ImageDraw.Draw(img)
    w = d.textlength(text, font=font)
    d.text(((900 - w) / 2, 10), text, font=font, fill=1, stroke_width=3, stroke_fill=2)
    return img


def show(pts, n, img):
    x, y = (W - img.width) // 2, H - img.height - 40
    pcs = struct.pack(">HHBHBBBB", W, H, 0x10, n, 0x80, 0, 0, 1) + struct.pack(">HBBHH", 0, 0, 0, x, y)
    wds = struct.pack(">BBHHHH", 1, 0, x, y, img.width, img.height)
    pds = struct.pack(">BB", 0, 0) + bytes([1, 235, 128, 128, 255, 2, 16, 128, 128, 255])
    data = rle(img)
    ods = struct.pack(">HBB", 0, 0, 0xC0) + (len(data) + 4).to_bytes(3, "big") + struct.pack(">HH", img.width, img.height) + data
    return seg(pts, 0x16, pcs) + seg(pts, 0x17, wds) + seg(pts, 0x14, pds) + seg(pts, 0x15, ods) + seg(pts, 0x80, b"")


def clear(pts, n):
    pcs = struct.pack(">HHBHBBBB", W, H, 0x10, n, 0x00, 0, 0, 0)
    wds = struct.pack(">BBHHHH", 1, 0, 0, 0, 900, 80)
    return seg(pts, 0x16, pcs) + seg(pts, 0x17, wds) + seg(pts, 0x80, b"")


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("out", help="the MKV to write")
    ap.add_argument("--font", default="/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf")
    args = ap.parse_args()
    font = ImageFont.truetype(args.font, 48)
    sup = bytearray()
    n = 0
    for start, end, text in CAPTIONS:
        sup += show(int(start * 90000), n, caption(text, font))
        n += 1
        sup += clear(int(end * 90000) - 1, n)
        n += 1
    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        path = os.path.join(tmp, "subs.sup")
        with open(path, "wb") as f:
            f.write(sup)
        subprocess.run([
            "ffmpeg", "-v", "error", "-y",
            "-f", "lavfi", "-i", "testsrc2=size=1280x720:rate=24:duration=10",
            "-f", "lavfi", "-i", "sine=frequency=440:duration=10", "-i", path,
            "-map", "0:v", "-map", "1:a", "-map", "2:s",
            "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac", "-c:s", "copy",
            "-metadata:s:s:0", "language=eng", args.out,
        ], check=True)


if __name__ == "__main__":
    main()
