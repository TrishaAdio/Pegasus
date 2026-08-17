#!/usr/bin/env python3
"""
Pegasus Java Edition — Created by Anirban <3

Generates the project's original entity textures and mod icon.

Everything here is drawn procedurally from scratch: no external image is read, traced or
sampled, so the output is original work owned by this project. Re-run after changing the
model UV layout:

    python3 assets/generate_textures.py

UV layout (128x64), matching PegasusEntityModel / UnicornEntityModel:
    body      (0,0)   10x10x22
    neck      (0,35)  5x15x5
    head      (22,35) 6x6x10   ears (22,52) (30,52)   horn (38,52)
    wing      (58,0)  18x2x14
    wing tip  (58,18) 16x1x12
    leg       (0,56)  3x12x3
    tail      (44,52) 3x12x3
"""

from __future__ import annotations

import os
import random
from PIL import Image, ImageDraw

WIDTH, HEIGHT = 128, 64
OUT_DIR = os.path.join(os.path.dirname(__file__), "..", "fabric", "src", "main", "resources",
                       "assets", "pegasus", "textures", "entity")
ICON_PATH = os.path.join(os.path.dirname(__file__), "..", "fabric", "src", "main", "resources",
                         "assets", "pegasus", "icon.png")


class Palette:
    """A coat palette expressed as base, shadow, highlight, mane and eye colours."""

    def __init__(self, base, shadow, highlight, mane, mane_shadow, eye, hoof, horn=None):
        self.base = base
        self.shadow = shadow
        self.highlight = highlight
        self.mane = mane
        self.mane_shadow = mane_shadow
        self.eye = eye
        self.hoof = hoof
        self.horn = horn or highlight


CLASSIC = Palette(
    base=(238, 234, 226, 255),
    shadow=(200, 194, 186, 255),
    highlight=(252, 250, 246, 255),
    mane=(226, 198, 132, 255),
    mane_shadow=(188, 158, 96, 255),
    eye=(78, 56, 40, 255),
    hoof=(120, 112, 104, 255),
)

BLUE_EYE = Palette(
    base=(232, 240, 248, 255),
    shadow=(190, 205, 222, 255),
    highlight=(250, 252, 255, 255),
    mane=(168, 208, 240, 255),
    mane_shadow=(120, 166, 210, 255),
    eye=(46, 140, 214, 255),
    hoof=(112, 124, 140, 255),
)

UNICORN = Palette(
    base=(246, 240, 250, 255),
    shadow=(208, 198, 222, 255),
    highlight=(255, 253, 255, 255),
    mane=(214, 178, 240, 255),
    mane_shadow=(170, 132, 202, 255),
    eye=(120, 74, 166, 255),
    hoof=(126, 116, 138, 255),
    horn=(240, 220, 250, 255),
)


def noise_fill(draw: ImageDraw.ImageDraw, box, palette: Palette, rng: random.Random,
               shade_bias: float = 0.0) -> None:
    """Fills a rect with subtle per-pixel dithering so flat areas do not look plastic."""
    x0, y0, x1, y1 = box
    for y in range(y0, y1):
        for x in range(x0, x1):
            roll = rng.random() + shade_bias
            if roll < 0.14:
                colour = palette.shadow
            elif roll > 0.90:
                colour = palette.highlight
            else:
                colour = palette.base
            draw.point((x, y), fill=colour)


def shaded_box(draw, box, palette, rng, shade_bias=0.0, outline=True):
    noise_fill(draw, box, palette, rng, shade_bias)
    if outline:
        x0, y0, x1, y1 = box
        draw.rectangle([x0, y0, x1 - 1, y1 - 1], outline=palette.shadow)


def draw_body(draw, palette, rng):
    # Cuboid 10 wide, 10 tall, 22 deep at (0,0). Box UV occupies 64x42 overall.
    shaded_box(draw, (0, 0, 64, 42), palette, rng)
    # Suggest a dorsal shadow line along the top faces.
    for x in range(10, 32):
        for y in range(0, 10):
            if rng.random() < 0.25:
                draw.point((x, y), fill=palette.shadow)


def draw_neck(draw, palette, rng):
    shaded_box(draw, (0, 35, 20, 55), palette, rng)
    # Mane runs down the back of the neck.
    for y in range(35, 55):
        for x in range(0, 5):
            draw.point((x, y), fill=palette.mane if (x + y) % 3 else palette.mane_shadow)


def draw_head(draw, palette, rng, horn=False):
    shaded_box(draw, (22, 35, 54, 51), palette, rng)
    # Eyes on both side faces of the head cuboid.
    for cx in (26, 45):
        draw.rectangle([cx, 41, cx + 1, 42], fill=palette.eye)
        draw.point((cx, 41), fill=(255, 255, 255, 255))
    # Muzzle shading at the front face.
    for x in range(34, 42):
        for y in range(44, 50):
            if rng.random() < 0.5:
                draw.point((x, y), fill=palette.shadow)
    # Ears
    shaded_box(draw, (22, 52, 26, 55), palette, rng, outline=False)
    shaded_box(draw, (30, 52, 34, 55), palette, rng, outline=False)
    if horn:
        # Spiral horn: alternating bands read as a twist at 16px scale.
        for y in range(52, 58):
            band = palette.horn if (y % 2 == 0) else palette.mane_shadow
            draw.rectangle([38, y, 41, y], fill=band)


def draw_wing(draw, palette, rng):
    # Main wing (58,0) 18x2x14 and tip (58,18) 16x1x12.
    shaded_box(draw, (58, 0, 122, 18), palette, rng, shade_bias=-0.05)
    shaded_box(draw, (58, 18, 118, 33), palette, rng, shade_bias=-0.05)
    # Feather banding: darker leading edge, lighter trailing edge.
    for x in range(58, 122):
        for y in range(0, 18):
            if (x - 58) % 4 == 0:
                draw.point((x, y), fill=palette.mane_shadow)
            elif (x - 58) % 4 == 2 and rng.random() < 0.6:
                draw.point((x, y), fill=palette.mane)
    for x in range(58, 118):
        for y in range(18, 33):
            if (x - 58) % 5 == 0:
                draw.point((x, y), fill=palette.mane_shadow)


def draw_legs_and_tail(draw, palette, rng):
    shaded_box(draw, (0, 56, 18, 64), palette, rng)
    # Hooves at the bottom of the leg UV.
    for x in range(0, 18):
        for y in range(62, 64):
            draw.point((x, y), fill=palette.hoof)
    shaded_box(draw, (44, 52, 56, 64), palette, rng)
    for y in range(52, 64):
        for x in range(44, 56):
            if (x + y) % 3:
                draw.point((x, y), fill=palette.mane)
            else:
                draw.point((x, y), fill=palette.mane_shadow)


def build(palette: Palette, winged: bool, horn: bool, seed: int) -> Image.Image:
    rng = random.Random(seed)
    image = Image.new("RGBA", (WIDTH, HEIGHT), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw_body(draw, palette, rng)
    draw_neck(draw, palette, rng)
    draw_head(draw, palette, rng, horn=horn)
    if winged:
        draw_wing(draw, palette, rng)
    draw_legs_and_tail(draw, palette, rng)
    return image


def build_icon() -> Image.Image:
    """A simple 128x128 icon: a winged silhouette over a sky gradient."""
    size = 128
    image = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    for y in range(size):
        t = y / (size - 1)
        draw.line([(0, y), (size, y)],
                  fill=(int(88 + 90 * t), int(140 + 70 * t), int(214 + 30 * t), 255))
    # Body
    draw.ellipse([44, 60, 92, 84], fill=(246, 244, 238, 255))
    # Neck and head
    draw.polygon([(84, 66), (100, 40), (110, 44), (92, 70)], fill=(246, 244, 238, 255))
    draw.ellipse([98, 32, 116, 50], fill=(246, 244, 238, 255))
    draw.rectangle([108, 40, 110, 42], fill=(46, 140, 214, 255))
    # Wings
    draw.polygon([(60, 62), (26, 26), (34, 60), (18, 56), (54, 74)], fill=(255, 255, 255, 255))
    draw.polygon([(66, 64), (100, 96), (78, 88), (86, 104), (58, 78)],
                 fill=(226, 236, 250, 255))
    # Legs
    for x in (52, 62, 74, 84):
        draw.rectangle([x, 82, x + 3, 100], fill=(238, 234, 226, 255))
    return image


def main() -> None:
    targets = [
        ("pegasus/classic.png", CLASSIC, True, False, 20260817),
        ("pegasus/blue_eye.png", BLUE_EYE, True, False, 20260818),
        ("unicorn/unicorn.png", UNICORN, False, True, 20260819),
    ]
    for relative, palette, winged, horn, seed in targets:
        path = os.path.normpath(os.path.join(OUT_DIR, relative))
        os.makedirs(os.path.dirname(path), exist_ok=True)
        build(palette, winged, horn, seed).save(path)
        print(f"wrote {path}")

    icon_path = os.path.normpath(ICON_PATH)
    os.makedirs(os.path.dirname(icon_path), exist_ok=True)
    build_icon().save(icon_path)
    print(f"wrote {icon_path}")


if __name__ == "__main__":
    main()
