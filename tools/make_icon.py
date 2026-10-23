"""Иконка TiggoHelper: шкала спидометра со стрелкой на тёмном скруглённом квадрате.

Рисуется функциями знакового расстояния (SDF) со сглаживанием по краю, поэтому любой
размер получается чётким без масштабирования. Только стандартная библиотека Python.

    python3 tools/make_icon.py            # иконки приложения в res/mipmap-*
    python3 tools/make_icon.py 512 out.png
"""
import math
import os
import struct
import sys
import zlib

# Координаты фигур — в «единицах» холста 512×512; при отрисовке масштабируются.
CORNER = 112
CENTER = (256, 286)
RADIUS = 150
STROKE = 34
SWEEP = (-30, 210)      # шкала: от правого низа до левого низа через верх, градусы
NEEDLE_ANGLE = 58       # куда смотрит стрелка (0° — вправо, 90° — вверх)
NEEDLE_LEN = 128

BG_TOP = (0x13, 0x3A, 0x4C)
BG_BOTTOM = (0x05, 0x10, 0x16)
TRACK = (0x1D, 0x3D, 0x4B)
ACCENT_FROM = (0x1E, 0x9F, 0xB4)
ACCENT_TO = (0x5F, 0xE3, 0xEE)
NEEDLE = (0xF4, 0xF7, 0xF9)
HUB_HOLE = (0x0B, 0x22, 0x2D)


def mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def sd_round_rect(x, y, half, r):
    qx = abs(x - half) - (half - r)
    qy = abs(y - half) - (half - r)
    outside = math.hypot(max(qx, 0), max(qy, 0))
    return outside + min(max(qx, qy), 0) - r


def angle_of(x, y):
    """Угол точки относительно центра шкалы: 0° вправо, против часовой, y вверх."""
    return math.degrees(math.atan2(CENTER[1] - y, x - CENTER[0]))


def point_at(angle, radius):
    a = math.radians(angle)
    return CENTER[0] + radius * math.cos(a), CENTER[1] - radius * math.sin(a)


def sd_arc(x, y, start, end):
    """Дуга толщиной STROKE с круглыми концами между углами start..end (start < end)."""
    a = angle_of(x, y)
    if a < start - 90:      # угол левее -90 относится к нижней части круга (210° = -150°)
        a += 360
    if start <= a <= end:
        return abs(math.hypot(x - CENTER[0], y - CENTER[1]) - RADIUS) - STROKE / 2
    ends = [point_at(start, RADIUS), point_at(end, RADIUS)]
    return min(math.hypot(x - ex, y - ey) for ex, ey in ends) - STROKE / 2


def sd_needle(x, y):
    """Стрелка — сужающийся отрезок от центра: 16 у основания, 5 на конце."""
    tip = point_at(NEEDLE_ANGLE, NEEDLE_LEN)
    ax, ay = CENTER
    bx, by = tip
    vx, vy = bx - ax, by - ay
    t = max(0.0, min(1.0, ((x - ax) * vx + (y - ay) * vy) / (vx * vx + vy * vy)))
    px, py = ax + vx * t, ay + vy * t
    return math.hypot(x - px, y - py) - (16 + (5 - 16) * t)


def coverage(d, px):
    """Доля пикселя внутри фигуры по расстоянию d (в единицах холста), px — размер пикселя."""
    return max(0.0, min(1.0, 0.5 - d / px))


def render(size):
    px = 512 / size
    rows = []
    for j in range(size):
        row = bytearray()
        for i in range(size):
            x, y = (i + 0.5) * px, (j + 0.5) * px
            a_bg = coverage(sd_round_rect(x, y, 256, CORNER), px)
            if a_bg == 0:
                row += b"\x00\x00\x00\x00"
                continue
            color = mix(BG_TOP, BG_BOTTOM, y / 512)

            def over(c, a):
                nonlocal color
                if a > 0:
                    color = mix(color, c, a)

            over(TRACK, coverage(sd_arc(x, y, SWEEP[0], SWEEP[1]), px))
            # Заполненная часть шкалы — от левого низа до стрелки, с градиентом по дуге.
            fill_a = coverage(sd_arc(x, y, NEEDLE_ANGLE, SWEEP[1]), px)
            if fill_a > 0:
                a = angle_of(x, y)
                if a < SWEEP[0] - 90:
                    a += 360
                t = max(0.0, min(1.0, (SWEEP[1] - a) / (SWEEP[1] - NEEDLE_ANGLE)))
                over(mix(ACCENT_FROM, ACCENT_TO, t), fill_a)
            over(NEEDLE, coverage(sd_needle(x, y), px))
            hub = math.hypot(x - CENTER[0], y - CENTER[1])
            over(NEEDLE, coverage(hub - 30, px))
            over(HUB_HOLE, coverage(hub - 11, px))
            row += bytes(color) + bytes([round(a_bg * 255)])
        rows.append(bytes(row))
    return rows


def write_png(path, size, rows):
    raw = b"".join(b"\x00" + r for r in rows)

    def chunk(tag, data):
        body = tag + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(raw, 9))
           + chunk(b"IEND", b""))
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)


def main():
    if len(sys.argv) == 3:
        size = int(sys.argv[1])
        write_png(sys.argv[2], size, render(size))
        return
    res = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "res")
    for density, size in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
        write_png(os.path.join(res, "mipmap-" + density, "ic_launcher.png"), size, render(size))


if __name__ == "__main__":
    main()
