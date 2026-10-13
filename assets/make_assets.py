#!/usr/bin/env python3
"""Generate Mahar OS branding assets: wallpaper + app icons + hicolor icon tree.
Run: python3 assets/make_assets.py
Inputs:  assets/logo-source/mahar-logo.png (AI-generated master logo, optional)
Outputs: assets/wallpaper/mahar-wallpaper.png
         assets/icons/*.png (512px masters)
         assets/hicolor/<size>/apps/*.png
"""
import math
import os
from PIL import Image, ImageDraw, ImageFont, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
WALLPAPER_DIR = os.path.join(HERE, "wallpaper")
ICONS_DIR = os.path.join(HERE, "icons")
HICOLOR_DIR = os.path.join(HERE, "hicolor")
LOGO_SRC = os.path.join(HERE, "logo-source", "mahar-logo.png")

BLUE = (26, 115, 232)
DARK_BG = (11, 16, 32)
WHITE = (255, 255, 255)

FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT_REG = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"


def rounded_bg(size, radius, top_color, bottom_color):
    """Vertical gradient rounded square."""
    img = Image.new("RGB", (size, size), (0, 0, 0))
    d = ImageDraw.Draw(img)
    for y in range(size):
        t = y / max(size - 1, 1)
        c = tuple(int(top_color[i] + (bottom_color[i] - top_color[i]) * t) for i in range(3))
        d.line([(0, y), (size, y)], fill=c)
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, size, size], radius=radius, fill=255)
    img.putalpha(mask)
    return img


def make_wallpaper():
    W, H = 1920, 1080
    img = Image.new("RGB", (W, H))
    d = ImageDraw.Draw(img)
    top, bottom = (8, 20, 45), (10, 32, 66)
    for y in range(H):
        t = y / (H - 1)
        c = tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
        d.line([(0, y), (W, y)], fill=c)
    # soft radial glow behind the text
    glow = Image.new("L", (W, H), 0)
    gd = ImageDraw.Draw(glow)
    gd.ellipse([W // 2 - 420, H // 2 - 420, W // 2 + 420, H // 2 + 420], fill=90)
    glow = glow.filter(ImageFilter.GaussianBlur(180))
    blue_layer = Image.new("RGB", (W, H), (30, 110, 220))
    img = Image.composite(blue_layer, img, glow)

    # giant watermark "M" on its own layer, composited at low opacity
    wm_layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    wmd = ImageDraw.Draw(wm_layer)
    try:
        wm_font = ImageFont.truetype(FONT_BOLD, 620)
    except OSError:
        wm_font = ImageFont.load_default()
    wmd.text((W // 2, H // 2 - 60), "M", font=wm_font, anchor="mm",
             fill=(120, 170, 255, 255))
    wm_layer = wm_layer.filter(ImageFilter.GaussianBlur(6))
    # scale layer alpha down to ~10%
    r, g, b, a = wm_layer.split()
    a = a.point(lambda v: int(v * 0.10))
    wm_layer = Image.merge("RGBA", (r, g, b, a))
    img = Image.alpha_composite(img.convert("RGBA"), wm_layer).convert("RGB")

    # title + tagline with soft shadow
    d = ImageDraw.Draw(img)
    try:
        title_font = ImageFont.truetype(FONT_BOLD, 130)
        sub_font = ImageFont.truetype(FONT_REG, 40)
    except OSError:
        title_font = sub_font = ImageFont.load_default()
    d.text((W // 2 + 3, H // 2 - 47), "Mahar OS", font=title_font,
           anchor="mm", fill=(0, 0, 0))
    d.text((W // 2, H // 2 - 50), "Mahar OS", font=title_font,
           anchor="mm", fill=(255, 255, 255))
    d.text((W // 2 + 2, H // 2 + 82), "Simple. Fast. Yours.",
           font=sub_font, anchor="mm", fill=(0, 0, 0))
    d.text((W // 2, H // 2 + 80), "Simple. Fast. Yours.",
           font=sub_font, anchor="mm", fill=(150, 190, 235))
    os.makedirs(WALLPAPER_DIR, exist_ok=True)
    out = os.path.join(WALLPAPER_DIR, "mahar-wallpaper.png")
    img.save(out)
    print("wallpaper:", out)


def globe_icon():
    s = 512
    img = rounded_bg(s, 110, (32, 130, 245), (14, 70, 170))
    d = ImageDraw.Draw(img, "RGBA")
    cx = cy, r = s // 2, 150
    cx = s // 2
    d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=WHITE, width=22)
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(255, 255, 255, 26))
    d.line([(cx - r, cy), (cx + r, cy)], fill=WHITE, width=14)
    for k in (-0.55, 0.55):
        w = int(r * 0.62)
        d.ellipse([cx - w, cy - r, cx + w, cy + r], outline=WHITE, width=14)
    return img


def player_icon():
    s = 512
    img = rounded_bg(s, 110, (18, 24, 40), (10, 14, 26))
    d = ImageDraw.Draw(img, "RGBA")
    cx, cy = s // 2, s // 2
    d.ellipse([cx - 150, cy - 150, cx + 150, cy + 150], outline=BLUE, width=20)
    d.polygon([(cx - 45, cy - 80), (cx - 45, cy + 80), (cx + 85, cy)],
              fill=(46, 140, 250))
    return img


def files_icon():
    s = 512
    img = rounded_bg(s, 110, (32, 130, 245), (14, 70, 170))
    d = ImageDraw.Draw(img, "RGBA")
    # folder: back plate + tab + front plate
    d.rounded_rectangle([116, 170, 396, 350], radius=26, fill=(13, 55, 130))
    d.rounded_rectangle([116, 150, 250, 205], radius=18, fill=(13, 55, 130))
    d.rounded_rectangle([116, 200, 396, 372], radius=26, fill=(235, 242, 255))
    d.rounded_rectangle([116, 200, 396, 250], radius=20, fill=(200, 218, 245))
    return img


def build_hicolor(masters):
    """masters: dict name -> 512px PIL image. Writes hicolor tree."""
    sizes = [16, 22, 24, 32, 48, 64, 128, 256]
    for size in sizes:
        appdir = os.path.join(HICOLOR_DIR, f"{size}x{size}", "apps")
        os.makedirs(appdir, exist_ok=True)
        for name, im in masters.items():
            small = im.resize((size, size), Image.LANCZOS)
            small.save(os.path.join(appdir, f"{name}.png"))
    print("hicolor tree:", HICOLOR_DIR)


def main():
    os.makedirs(ICONS_DIR, exist_ok=True)
    masters = {
        "mahar-browser": globe_icon(),
        "mahar-player": player_icon(),
        "mahar-files": files_icon(),
    }
    for name, im in masters.items():
        p = os.path.join(ICONS_DIR, f"{name}.png")
        im.save(p)
        print("icon:", p)
    if os.path.exists(LOGO_SRC):
        logo = Image.open(LOGO_SRC).convert("RGBA")
        p = os.path.join(ICONS_DIR, "mahar-logo.png")
        logo.save(p)
        print("logo:", p)
        masters["mahar-logo"] = logo
    else:
        print("note: no AI logo found at", LOGO_SRC, "— skipping mahar-logo")
    make_wallpaper()
    build_hicolor(masters)


if __name__ == "__main__":
    main()
