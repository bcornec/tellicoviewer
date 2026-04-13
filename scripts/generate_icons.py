#!/usr/bin/env python3
"""
scripts/generate_icons.py
Génère les icônes de lancement Android pour toutes les densités.
Requiert : pip install pillow

Usage : python3 scripts/generate_icons.py
"""
from PIL import Image, ImageDraw
import os, sys

BASE = os.path.join(os.path.dirname(__file__), "..",
                    "app", "src", "main", "res")

TEAL_DARK  = (26, 107, 92)
TEAL_LIGHT = (78, 205, 196)
WHITE      = (255, 255, 255)
DARK_GREEN = (13, 74, 62)

def draw_icon(size, rounded=False):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    s = size
    r = int(s * 0.22)
    if rounded:
        d.ellipse([0, 0, s-1, s-1], fill=TEAL_DARK)
    else:
        d.rounded_rectangle([0, 0, s-1, s-1], radius=r, fill=TEAL_DARK)
    mg = int(s * 0.18); cx = s // 2; sp = int(s * 0.04)
    top = int(s * 0.22); bot = int(s * 0.78)
    d.rounded_rectangle([mg, top, cx-sp, bot], radius=int(s*0.03), fill=WHITE)
    d.rounded_rectangle([cx+sp, top, s-mg, bot], radius=int(s*0.03), fill=(255,255,255,230))
    d.rounded_rectangle([cx-sp, top-int(s*0.02), cx+sp, bot+int(s*0.02)], radius=int(s*0.02), fill=DARK_GREEN)
    lx1 = mg+int(s*0.05); lx2 = cx-sp-int(s*0.05); lw = max(1, int(s*0.03))
    rx1 = cx+sp+int(s*0.05); rx2 = s-mg-int(s*0.05)
    ys = [top+int((bot-top)*t) for t in [0.18,0.35,0.52,0.68,0.82]]
    for i, y in enumerate(ys):
        d.rounded_rectangle([lx1, y-lw//2, lx2-(int(s*0.08) if i%2 else 0), y+lw//2],
                            radius=lw//2, fill=(26,107,92,200))
        d.rounded_rectangle([rx1, y-lw//2, rx2-(int(s*0.08) if i%2 else 0), y+lw//2],
                            radius=lw//2, fill=(78,205,196,200))
    return img

DENSITIES = [("mipmap-mdpi",48),("mipmap-hdpi",72),("mipmap-xhdpi",96),
             ("mipmap-xxhdpi",144),("mipmap-xxxhdpi",192)]

for folder, size in DENSITIES:
    path = os.path.join(BASE, folder)
    os.makedirs(path, exist_ok=True)
    draw_icon(size, False).save(os.path.join(path, "ic_launcher.png"), "PNG")
    draw_icon(size, True).save(os.path.join(path, "ic_launcher_round.png"), "PNG")
    print(f"  {folder}: {size}px ✓")
print("Icônes générées avec succès.")
