#!/usr/bin/env python3
"""Generate Apachiy brand placeholders. Run once to populate the icon/banner/wordmark
slots. Each output is a clearly-Apachiy placeholder so no Nuvio branding can sneak
into the APK by accident. Drop in a real logo later by overwriting the same paths
(see docs/BRANDING.md).
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path("app/src/main/res")

# Brand palette (placeholder; change in docs/BRANDING.md)
BG = (13, 13, 13)        # #0D0D0D
FG = (30, 136, 229)      # #1E88E5
ACCENT = (255, 152, 0)   # #FF9800

# Icon densities (ic_launcher)
ICON_SIZES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}

# Android TV banner
BANNER_SIZE = (320, 180)  # xhdpi; xhdpi is the only density that ships

# Wordmark + mark sizes
WORDMARK_SIZE = (512, 128)
MARK_SIZE = 512

FONT_PATHS = [
    r"C:\Windows\Fonts\segoeuib.ttf",  # Windows Segoe UI Bold
    r"C:\Windows\Fonts\arialbd.ttf",
    r"C:\Windows\Fonts\arial.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
    "/Library/Fonts/Arial.ttf",
]


def get_font(size):
    for path in FONT_PATHS:
        if Path(path).exists():
            try:
                return ImageFont.truetype(path, size)
            except OSError:
                continue
    return ImageFont.load_default()


def render_icon(size: int, path: Path, text: str = "A"):
    img = Image.new("RGBA", (size, size), BG + (255,))
    draw = ImageDraw.Draw(img)
    # Corner radius approx 22% (Android adaptive icons handle the masking;
    # but plain ic_launcher.png is shown on devices that don't apply it)
    r = int(size * 0.22)
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, size, size), radius=r, fill=255)
    rounded = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    rounded.paste(img, (0, 0), mask)
    draw = ImageDraw.Draw(rounded)
    font = get_font(int(size * 0.55))
    bbox = draw.textbbox((0, 0), text, font=font)
    w, h = bbox[2] - bbox[0], bbox[3] - bbox[1]
    draw.text(((size - w) / 2 - bbox[0], (size - h) / 2 - bbox[1]), text, font=font, fill=FG + (255,))
    # Subtle accent ring
    ring_w = max(2, int(size * 0.02))
    draw.ellipse((ring_w, ring_w, size - ring_w, size - ring_w), outline=ACCENT + (255,), width=ring_w)
    rounded.save(path, "PNG", optimize=True)
    print(f"  {path}")


def render_banner(path: Path, size=BANNER_SIZE):
    img = Image.new("RGBA", size, BG + (255,))
    draw = ImageDraw.Draw(img)
    font = get_font(int(size[1] * 0.4))
    text = "Apachiy"
    bbox = draw.textbbox((0, 0), text, font=font)
    w, h = bbox[2] - bbox[0], bbox[3] - bbox[1]
    x = (size[0] - w) / 2 - bbox[0]
    y = (size[1] - h) / 2 - bbox[1]
    draw.text((x, y), text, font=font, fill=FG + (255,))
    # Accent bar on left
    bar_w = int(size[0] * 0.02)
    draw.rectangle((0, 0, bar_w, size[1]), fill=ACCENT + (255,))
    img.save(path, "PNG", optimize=True)
    print(f"  {path}")


def render_wordmark(path: Path, size=WORDMARK_SIZE):
    img = Image.new("RGBA", size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    font = get_font(int(size[1] * 0.6))
    text = "Apachiy"
    bbox = draw.textbbox((0, 0), text, font=font)
    w, h = bbox[2] - bbox[0], bbox[3] - bbox[1]
    draw.text(((size[0] - w) / 2 - bbox[0], (size[1] - h) / 2 - bbox[1]), text, font=font, fill=FG + (255,))
    img.save(path, "PNG", optimize=True)
    print(f"  {path}")


def render_mark(path: Path, size=MARK_SIZE):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    inner = int(size * 0.85)
    ox = (size - inner) // 2
    draw.rounded_rectangle((ox, ox, ox + inner, ox + inner), radius=int(inner * 0.22), fill=BG + (255,))
    font = get_font(int(inner * 0.55))
    text = "A"
    bbox = draw.textbbox((0, 0), text, font=font)
    w, h = bbox[2] - bbox[0], bbox[3] - bbox[1]
    draw.text(((size - w) / 2 - bbox[0], (size - h) / 2 - bbox[1]), text, font=font, fill=FG + (255,))
    img.save(path, "PNG", optimize=True)
    print(f"  {path}")


def render_text_logo(path: Path, size=(1024, 256)):
    """Replaces the legacy nuvio_text.png used in splash/onboarding."""
    img = Image.new("RGBA", size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    font = get_font(int(size[1] * 0.6))
    text = "Apachiy"
    bbox = draw.textbbox((0, 0), text, font=font)
    w, h = bbox[2] - bbox[0], bbox[3] - bbox[1]
    draw.text(((size[0] - w) / 2 - bbox[0], (size[1] - h) / 2 - bbox[1]), text, font=font, fill=FG + (255,))
    img.save(path, "PNG", optimize=True)
    print(f"  {path}")


def main():
    print("Generating ic_launcher placeholders...")
    for density, size in ICON_SIZES.items():
        d = ROOT / f"mipmap-{density}"
        d.mkdir(parents=True, exist_ok=True)
        render_icon(size, d / "ic_launcher.png")

    print("Generating banner (xhdpi)...")
    (ROOT / "mipmap-xhdpi").mkdir(parents=True, exist_ok=True)
    render_banner(ROOT / "mipmap-xhdpi" / "banner.png")

    print("Generating app_logo_mark.png + app_logo_wordmark.png + apachiy_text.png...")
    (ROOT / "drawable").mkdir(parents=True, exist_ok=True)
    render_mark(ROOT / "drawable" / "app_logo_mark.png")
    render_wordmark(ROOT / "drawable" / "app_logo_wordmark.png")
    render_text_logo(ROOT / "drawable" / "apachiy_text.png")

    print("Generating adaptive icon foreground/background...")
    adaptive_dir = ROOT / "mipmap-anydpi-v26"
    adaptive_dir.mkdir(parents=True, exist_ok=True)
    # Adaptive icon foreground (108dp visual area inside 108x108 dp canvas)
    fg_size = 432
    fg = Image.new("RGBA", (fg_size, fg_size), (0, 0, 0, 0))
    fgd = ImageDraw.Draw(fg)
    inner = int(fg_size * 0.55)
    ox = (fg_size - inner) // 2
    fgd.rounded_rectangle((ox, ox, ox + inner, ox + inner), radius=int(inner * 0.18), fill=FG + (255,))
    font = get_font(int(inner * 0.6))
    text = "A"
    bbox = fgd.textbbox((0, 0), text, font=font)
    w, h = bbox[2] - bbox[0], bbox[3] - bbox[1]
    fgd.text(((fg_size - w) / 2 - bbox[0], (fg_size - h) / 2 - bbox[1]), text, font=font, fill=BG + (255,))
    fg.save(adaptive_dir / "ic_launcher_foreground.png", "PNG", optimize=True)
    print(f"  {adaptive_dir / 'ic_launcher_foreground.png'}")

    bg = Image.new("RGBA", (fg_size, fg_size), BG + (255,))
    bg.save(adaptive_dir / "ic_launcher_background.png", "PNG", optimize=True)
    print(f"  {adaptive_dir / 'ic_launcher_background.png'}")

    # Same for banner adaptive
    banner_fg = Image.new("RGBA", (640, 360), (0, 0, 0, 0))
    bd = ImageDraw.Draw(banner_fg)
    inner = 300
    ox = (640 - inner) // 2
    bd.rounded_rectangle((ox, ox, ox + inner, ox + inner), radius=int(inner * 0.18), fill=FG + (255,))
    font = get_font(int(inner * 0.6))
    text = "A"
    bbox = bd.textbbox((0, 0), text, font=font)
    w, h = bbox[2] - bbox[0], bbox[3] - bbox[1]
    bd.text(((640 - w) / 2 - bbox[0], (360 - h) / 2 - bbox[1]), text, font=font, fill=BG + (255,))
    banner_fg.save(adaptive_dir / "ic_banner_foreground.png", "PNG", optimize=True)
    banner_bg = Image.new("RGBA", (640, 360), BG + (255,))
    banner_bg.save(adaptive_dir / "ic_banner_background.png", "PNG", optimize=True)

    print("Done.")


if __name__ == "__main__":
    main()