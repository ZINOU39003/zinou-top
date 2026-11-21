#!/usr/bin/env python3
"""
ZINOU TOP placeholder artwork generator.

Produces every image the 'zinou' flavor overrides, with the exact file names and
sizes Android expects. To use the FINAL logo later, either:
  * replace the generated PNGs in place (same names, same sizes), or
  * edit this script and re-run it.
No code changes are needed in either case.

Usage:
  python3 generate_placeholders.py <repo_root> <latin_bold_font> <arabic_bold_font>
Fonts used for the placeholders: Montserrat 900 and Noto Kufi Arabic 800 (both SIL OFL).
"""
import os, sys
from PIL import Image, ImageDraw, ImageFont

NAVY_TOP = (11, 20, 38)
NAVY_BOTTOM = (22, 44, 82)
AMBER = (255, 180, 0)
WHITE = (255, 255, 255)
GREY_BG = (38, 38, 40)
GREY_ACCENT = (170, 170, 175)

root, LATIN, ARABIC = sys.argv[1], sys.argv[2], sys.argv[3]
APP = os.path.join(root, "smarttubetv/src/zinou/res")


def font(path, size):
    return ImageFont.truetype(path, size, layout_engine=ImageFont.Layout.RAQM)


def gradient(w, h, top=NAVY_TOP, bottom=NAVY_BOTTOM):
    img = Image.new("RGBA", (w, h))
    d = ImageDraw.Draw(img)
    for y in range(h):
        t = y / max(1, h - 1)
        c = tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3)) + (255,)
        d.line([(0, y), (w, y)], fill=c)
    return img


def monogram(size, bg="navy", rounded=True, scale=1.0):
    """Rounded badge with a white 'Z' and an amber play-triangle accent."""
    S = size * 4  # supersample
    if bg == "navy":
        base = gradient(S, S)
        fg, accent = WHITE, AMBER
    elif bg == "amber":
        base = Image.new("RGBA", (S, S), AMBER + (255,))
        fg, accent = NAVY_TOP, WHITE
    elif bg == "grey":
        base = Image.new("RGBA", (S, S), GREY_BG + (255,))
        fg, accent = WHITE, GREY_ACCENT
    else:  # transparent (adaptive foreground)
        base = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        fg, accent = WHITE, AMBER
    d = ImageDraw.Draw(base)
    f = font(LATIN, int(S * 0.62 * scale))
    bbox = d.textbbox((0, 0), "Z", font=f)
    tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
    x = (S - tw) / 2 - bbox[0] - S * 0.04 * scale
    y = (S - th) / 2 - bbox[1] - S * 0.02 * scale
    d.text((x, y), "Z", font=f, fill=fg)
    # play triangle, bottom-right
    t = S * 0.17 * scale
    cx, cy = S / 2 + S * 0.25 * scale, S / 2 + S * 0.22 * scale
    d.polygon([(cx - t * 0.45, cy - t * 0.55), (cx - t * 0.45, cy + t * 0.55), (cx + t * 0.6, cy)], fill=accent)
    if rounded:
        mask = Image.new("L", (S, S), 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, S - 1, S - 1], radius=int(S * 0.22), fill=255)
        out = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        out.paste(base, (0, 0), mask)
        base = out
    return base.resize((size, size), Image.LANCZOS)


def padded(img, canvas, inner):
    out = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
    m = img.resize((inner, inner), Image.LANCZOS)
    out.paste(m, ((canvas - inner) // 2, (canvas - inner) // 2), m)
    return out


def wordmark(w, h, with_bg=True):
    """Badge + 'ZINOU TOP' + Arabic name. Used for TV banner and splash."""
    S = 2
    W, H = w * S, h * S
    img = gradient(W, H) if with_bg else Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    badge = int(H * 0.46)
    b = monogram(badge)
    gap = int(H * 0.07)
    lf = font(LATIN, int(H * 0.145))
    af = font(ARABIC, int(H * 0.115))
    lb = d.textbbox((0, 0), "ZINOU TOP", font=lf)
    ab = d.textbbox((0, 0), "زينو توب", font=af, direction="rtl", language="ar")
    text_w = max(lb[2] - lb[0], ab[2] - ab[0])
    total = badge + gap + text_w
    x0 = (W - total) // 2
    by = (H - badge) // 2
    if with_bg:
        d.rounded_rectangle([x0 - 2 * S, by - 2 * S, x0 + badge + 2 * S, by + badge + 2 * S],
                            radius=int(badge * 0.24), outline=(64, 88, 132, 255), width=2 * S)
    img.paste(b, (x0, by), b)
    tx = x0 + badge + gap
    lh, ah = lb[3] - lb[1], ab[3] - ab[1]
    block = lh + int(H * 0.16) + ah
    ty = (H - block) // 2
    d.text((tx - lb[0], ty - lb[1]), "ZINOU TOP", font=lf, fill=WHITE)
    # amber rule between names
    ry = ty + lh + int(H * 0.06)
    d.rectangle([tx, ry - 2 * S, tx + int(text_w * 0.25), ry + S], fill=AMBER)
    d.text((tx + text_w - (ab[2] - ab[0]) - ab[0], ry + int(H * 0.06) - ab[1]), "زينو توب",
           font=af, fill=AMBER, direction="rtl", language="ar")
    return img.resize((w, h), Image.LANCZOS)


def save(img, rel, mode="RGBA"):
    p = os.path.join(APP, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.convert(mode).save(p, optimize=True)
    print("wrote", os.path.relpath(p, root), img.size)


# Launcher icons (same names/sizes as upstream flavors)
save(padded(monogram(320), 320, 300), "mipmap-nodpi/app_icon.png")
save(padded(monogram(320, bg="amber"), 320, 300), "mipmap-nodpi/app_icon_alt.png")
# Adaptive icon foreground (108dp canvas, content inside the 66dp safe zone) - Android 8+
save(padded(monogram(432, bg="none", rounded=False, scale=0.62), 432, 432), "mipmap-nodpi/app_icon_foreground.png")
# Android TV banner (launcher row), 16:9
save(wordmark(640, 360), "mipmap-nodpi/app_banner.png", "RGB")
# In-app header logos (one per colour scheme)
save(padded(monogram(180), 180, 168), "mipmap-nodpi/app_logo.png")
save(padded(monogram(180), 180, 168), "mipmap-nodpi/app_logo_semi_red.png")
save(padded(monogram(180, bg="grey"), 180, 168), "mipmap-nodpi/app_logo_semi_grey.png")
# Splash (starting window) wordmark, transparent, scaled by density from xhdpi
save(wordmark(640, 260, with_bg=False), "drawable-xhdpi/zinou_splash_logo.png")
