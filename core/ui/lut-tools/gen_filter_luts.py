#!/usr/bin/env python3
"""Writes the 20 Memix filter LUTs (spec docs/ux/specs/P2-04-image-tools.md, "The 20 filters").

Original work: each look is a short recipe of ordinary color operations written for Memix, not derived from any third-party
preset or LUT pack. Needs Python 3 and numpy. Run from anywhere:

    python3 core/ui/lut-tools/gen_filter_luts.py

Output: one `<id>.lut` per filter in core/ui/src/commonMain/composeResources/files/filters/.
Format (read by app.memix.core.domain.image.Lut and, in P4-07, fed to Media3's SingleColorLut):
  33 x 33 x 33 colors, 8 bits per channel, bytes R, G, B per color, red index varying fastest, then green, then blue
  (index = (b * 33 + g) * 33 + r, the lattice point (r, g, b) / 32 in sRGB gamma space). 107,811 bytes each.
Sampling is trilinear between the 8 nearest lattice points; the intensity mix is done by the caller (out = mix(color, lut, i)).
"""
import os
import numpy as np

SIZE = 33
LUMA = np.array([0.2126, 0.7152, 0.0722])
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "commonMain", "composeResources", "files", "filters")


def luma(c):
    return (c * LUMA).sum(axis=-1, keepdims=True)


def sat(c, k):
    y = luma(c)
    return y + (c - y) * k


def contrast(c, amount):
    return (c - 0.5) * (1 + amount) + 0.5


def lift(c, amount):  # raises the blacks
    return c * (1 - amount) + amount


def gain(c, amount):  # scales the whites
    return c * (1 + amount)


def gamma(c, g):  # g > 1 darkens the mids
    return np.clip(c, 0, 1) ** g


def mult(c, r=1.0, g=1.0, b=1.0):
    return c * np.array([r, g, b])


def shadows(c, r=0.0, g=0.0, b=0.0):  # added where the picture is dark
    w = (1 - luma(c)) ** 2
    return c + w * np.array([r, g, b])


def highlights(c, r=0.0, g=0.0, b=0.0):  # added where the picture is light
    w = luma(c) ** 2
    return c + w * np.array([r, g, b])


def s_curve(c, amount):
    x = np.clip(c, 0, 1)
    return x + amount * (x * x * (3 - 2 * x) - x)


def tint_luma(c, r, g, b):
    return luma(c) * np.array([r, g, b])


RECIPES = {
    "vivid": lambda c: contrast(sat(c, 1.35), 0.10),
    "warm": lambda c: sat(mult(c, r=1.10, b=0.86), 1.12),
    "cool": lambda c: sat(mult(c, r=0.88, b=1.12), 1.05),
    "mono": lambda c: contrast(np.repeat(luma(c), 3, axis=-1), 0.05),
    "noir": lambda c: gamma(s_curve(np.repeat(luma(c), 3, axis=-1), 0.35), 1.10),
    "fade": lambda c: contrast(sat(lift(c, 0.08), 0.80), -0.10),
    "vintage": lambda c: gain(mult(sat(lift(c, 0.06), 0.75), r=1.06, b=0.88), -0.04),
    "film": lambda c: sat(highlights(shadows(lift(contrast(c, 0.12), 0.03), r=-0.06, g=0.03), r=0.05, g=0.02), 0.90),
    "sepia": lambda c: tint_luma(c, 1.00, 0.84, 0.62),
    "cinema": lambda c: contrast(highlights(shadows(c, r=-0.10, b=0.10), r=0.10, b=-0.10), 0.15),
    "pastel": lambda c: lift(sat(c + 0.05, 0.65), 0.06) + np.array([0.03, 0.0, 0.02]),
    "punch": lambda c: sat(contrast(c, 0.25), 1.50),
    "drama": lambda c: shadows(gamma(contrast(sat(c, 0.70), 0.30), 1.15), r=-0.03, b=0.04),
    "sunset": lambda c: sat(shadows(highlights(mult(c, r=1.08), r=0.12, g=-0.03, b=0.04), b=0.08), 1.25),
    "neon": lambda c: sat(highlights(shadows(c, g=0.08, b=0.12), r=0.12, b=0.10), 1.45),
    "aqua": lambda c: sat(shadows(mult(c, r=0.80, g=1.04, b=1.10), g=0.04, b=0.04), 1.15),
    "matte": lambda c: sat(contrast(gain(lift(c, 0.14), -0.10), -0.05), 0.95),
    "chrome": lambda c: highlights(sat(contrast(c, 0.30), 0.70), b=0.06),
    "dusk": lambda c: sat(lift(mult(gamma(c, 1.15), r=0.95, g=0.90, b=1.12), 0.03), 0.85),
    "bleach": lambda c: contrast(sat(c, 0.55), 0.28),
}


def lattice():
    axis = np.arange(SIZE) / (SIZE - 1)
    b, g, r = np.meshgrid(axis, axis, axis, indexing="ij")  # index [b][g][r]: red varies fastest
    return np.stack([r, g, b], axis=-1).reshape(-1, 3)


def report(outputs, grid):
    """The design review's pass test: each filter moves the picture at least ~8/255 on average, and no two filters are within ~4/255."""
    names = list(outputs)
    shifts = {n: np.abs(outputs[n] - grid).mean() * 255 for n in names}
    closest = {n: min((np.abs(outputs[n] - outputs[m]).mean() * 255, m) for m in names if m != n) for n in names}
    print("filter    mean shift from None   nearest other filter (mean difference)")
    for n in names:
        print(f"{n:9} {shifts[n]:6.1f}                {closest[n][1]} ({closest[n][0]:.1f})")
    print("min shift %.1f, min pairwise %.1f (targets: >= 8 and >= 4)" % (min(shifts.values()), min(v[0] for v in closest.values())))


def main():
    os.makedirs(OUT, exist_ok=True)
    grid = lattice()
    outputs = {}
    for name, recipe in RECIPES.items():
        out = np.clip(recipe(grid), 0, 1)
        outputs[name] = out
        data = np.rint(out * 255).astype(np.uint8).reshape(-1)
        with open(os.path.join(OUT, name + ".lut"), "wb") as f:
            f.write(data.tobytes())
        print(f"{name}.lut {data.size} bytes")
    report(outputs, grid)


if __name__ == "__main__":
    main()
