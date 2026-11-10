#!/usr/bin/env python3
"""Generate adaptive + legacy launcher icons — crop hex tight, fill icon button."""

from __future__ import annotations

import struct
import sys
import zlib
from pathlib import Path


def read_png(path: Path):
    data = path.read_bytes()
    assert data[:8] == b"\x89PNG\r\n\x1a\n", path
    i = 8
    idats: list[bytes] = []
    w = h = color = bit = None
    while i < len(data):
        ln = int.from_bytes(data[i : i + 4], "big")
        typ = data[i + 4 : i + 8]
        chunk = data[i + 8 : i + 8 + ln]
        i += 12 + ln
        if typ == b"IHDR":
            w, h, bit, color, _comp, _filt, _inter = struct.unpack(">IIBBBBB", chunk)
        elif typ == b"IDAT":
            idats.append(chunk)
        elif typ == b"IEND":
            break
    assert w is not None and bit == 8
    raw = zlib.decompress(b"".join(idats))
    bpp = {2: 3, 6: 4, 0: 1}[color]
    rows: list[bytearray] = []
    idx = 0
    prev = bytearray(w * bpp)

    def paeth(a, b, c):
        p = a + b - c
        pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
        if pa <= pb and pa <= pc:
            return a
        if pb <= pc:
            return b
        return c

    for _y in range(h):
        f = raw[idx]
        idx += 1
        row = bytearray(raw[idx : idx + w * bpp])
        idx += w * bpp
        if f == 1:
            for x in range(bpp, len(row)):
                row[x] = (row[x] + row[x - bpp]) & 255
        elif f == 2:
            for x in range(len(row)):
                row[x] = (row[x] + prev[x]) & 255
        elif f == 3:
            for x in range(len(row)):
                left = row[x - bpp] if x >= bpp else 0
                row[x] = (row[x] + ((left + prev[x]) // 2)) & 255
        elif f == 4:
            for x in range(len(row)):
                a = row[x - bpp] if x >= bpp else 0
                b = prev[x]
                c = prev[x - bpp] if x >= bpp else 0
                row[x] = (row[x] + paeth(a, b, c)) & 255
        elif f != 0:
            raise SystemExit(f"bad filter {f}")
        rows.append(row)
        prev = row
    return w, h, color, bpp, rows


def write_png(path: Path, w: int, h: int, color_type: int, rows: list[bytes]) -> None:
    def chunk(tag: bytes, data: bytes) -> bytes:
        return (
            struct.pack(">I", len(data))
            + tag
            + data
            + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
        )

    raw = b"".join(b"\x00" + row for row in rows)
    ihdr = struct.pack(">IIBBBBB", w, h, 8, color_type, 0, 0, 0)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", ihdr)
        + chunk(b"IDAT", zlib.compress(raw, 9))
        + chunk(b"IEND", b"")
    )


def crush_rgb(r: int, g: int, b: int) -> tuple[int, int, int]:
    if (r + g + b) / 3 < 18:
        return 0, 0, 0
    return r, g, b


def content_bbox(w: int, h: int, bpp: int, rows: list[bytearray], thresh: int = 28):
    xs: list[int] = []
    ys: list[int] = []
    for y in range(h):
        for x in range(w):
            o = x * bpp
            if rows[y][o] + rows[y][o + 1] + rows[y][o + 2] > thresh:
                xs.append(x)
                ys.append(y)
    if not xs:
        return 0, 0, w - 1, h - 1
    return min(xs), min(ys), max(xs), max(ys)


def crop_square(
    w: int, h: int, bpp: int, rows: list[bytearray], pad_frac: float = 0.04
) -> tuple[int, int, int, list[bytearray]]:
    """Tight crop around violet content, squared with small black pad."""
    x0, y0, x1, y1 = content_bbox(w, h, bpp, rows)
    cw, ch = x1 - x0 + 1, y1 - y0 + 1
    side = max(cw, ch)
    pad = max(2, int(side * pad_frac))
    side += pad * 2
    # Center the content box in the square
    cx = (x0 + x1) / 2.0
    cy = (y0 + y1) / 2.0
    sx0 = int(round(cx - side / 2.0))
    sy0 = int(round(cy - side / 2.0))
    out: list[bytearray] = []
    for y in range(side):
        row = bytearray(side * 3)
        sy = sy0 + y
        for x in range(side):
            sx = sx0 + x
            if 0 <= sx < w and 0 <= sy < h:
                so = sx * bpp
                r, g, b = crush_rgb(rows[sy][so], rows[sy][so + 1], rows[sy][so + 2])
                row[x * 3 : x * 3 + 3] = bytes((r, g, b))
        out.append(row)
    return side, side, 3, out


def resize_nn(sw, sh, bpp, srows, size: int) -> list[bytes]:
    out: list[bytes] = []
    for y in range(size):
        sy = min(sh - 1, y * sh // size)
        row = bytearray(size * 3)
        for x in range(size):
            sx = min(sw - 1, x * sw // size)
            so = sx * bpp
            r, g, b = crush_rgb(srows[sy][so], srows[sy][so + 1], srows[sy][so + 2])
            row[x * 3 : x * 3 + 3] = bytes((r, g, b))
        out.append(bytes(row))
    return out


def place_zoomed(
    crows,
    cw: int,
    ch: int,
    cbpp: int,
    canvas: int,
    zoom: float,
    rgba: bool,
) -> list[bytes]:
    """Place cropped logo centered; zoom>1 crops edges so hex fills the mask."""
    logo = max(8, int(round(canvas * zoom)))
    logo_rows = resize_nn(cw, ch, cbpp, crows, logo)
    off = (canvas - logo) // 2
    out_rows: list[bytes] = []
    bpp_out = 4 if rgba else 3
    for y in range(canvas):
        row = bytearray(canvas * bpp_out)
        sy = y - off
        for x in range(canvas):
            sx = x - off
            if 0 <= sx < logo and 0 <= sy < logo:
                so = sx * 3
                r, g, b = logo_rows[sy][so], logo_rows[sy][so + 1], logo_rows[sy][so + 2]
            else:
                r = g = b = 0
            if rgba:
                o = x * 4
                row[o : o + 4] = bytes((r, g, b, 255))
            else:
                o = x * 3
                row[o : o + 3] = bytes((r, g, b))
        out_rows.append(bytes(row))
    return out_rows


def make_foreground(
    src: Path,
    out: Path,
    canvas: int = 432,
    # Tight crop fills the mask; keep zoom <=1 so the full hexagon stays visible.
    zoom: float = 0.62,
) -> None:
    sw, sh, _color, bpp, srows = read_png(src)
    cw, ch, cbpp, crows = crop_square(sw, sh, bpp, srows, pad_frac=0.02)
    out_rows = place_zoomed(crows, cw, ch, cbpp, canvas, zoom, rgba=True)
    write_png(out, canvas, canvas, 6, out_rows)
    print(f"foreground {out} canvas={canvas} crop={cw} zoom={zoom}")


def make_legacy(src: Path, out: Path, size: int, zoom: float = 0.62) -> None:
    sw, sh, _color, bpp, srows = read_png(src)
    cw, ch, cbpp, crows = crop_square(sw, sh, bpp, srows, pad_frac=0.02)
    out_rows = place_zoomed(crows, cw, ch, cbpp, size, zoom, rgba=False)
    write_png(out, size, size, 2, out_rows)
    print(f"legacy {out} {size}x{size} crop={cw} zoom={zoom}")


def main() -> None:
    root = Path(__file__).resolve().parents[1]
    src = Path(sys.argv[1]) if len(sys.argv) > 1 else None
    if src is None or src.suffix.lower() in {".jpg", ".jpeg"}:
        raise SystemExit("Pass a cleaned PNG path")

    res = root / "app/src/main/res"
    make_foreground(src, res / "drawable/ic_launcher_foreground.png")
    for size, dens in [
        (48, "mdpi"),
        (72, "hdpi"),
        (96, "xhdpi"),
        (144, "xxhdpi"),
        (192, "xxxhdpi"),
    ]:
        make_legacy(src, res / f"mipmap-{dens}/ic_launcher.png", size)


if __name__ == "__main__":
    main()
