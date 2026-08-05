from __future__ import annotations

from pathlib import Path
from typing import Iterable

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "docs" / "assets" / "brand"

COLORS = {
    "background": "#0B0F14",
    "surface": "#121821",
    "surface_elevated": "#19212C",
    "primary": "#7C5CFC",
    "primary_container": "#2A2057",
    "secondary": "#25C2A0",
    "secondary_container": "#103D36",
    "text_primary": "#F5F7FA",
    "text_secondary": "#98A2B3",
    "outline": "#2B3543",
    "success": "#38C172",
    "warning": "#F4B740",
    "error": "#EF5B5B",
}


def font(size: int, bold: bool = False, mono: bool = False) -> ImageFont.FreeTypeFont:
    if mono:
        path = "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf"
    elif bold:
        path = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
    else:
        path = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
    return ImageFont.truetype(path, size)


def rgb(hex_value: str) -> tuple[int, int, int]:
    value = hex_value.lstrip("#")
    return tuple(int(value[i : i + 2], 16) for i in (0, 2, 4))


def rgba(hex_value: str, alpha: int = 255) -> tuple[int, int, int, int]:
    return (*rgb(hex_value), alpha)


def draw_symbol(draw: ImageDraw.ImageDraw, box: tuple[int, int, int, int]) -> None:
    x0, y0, x1, y1 = box
    w = x1 - x0
    h = y1 - y0
    cx = (x0 + x1) // 2
    left_outer = x0 + int(w * 0.08)
    left_inner = cx - int(w * 0.17)
    right_inner = cx + int(w * 0.17)
    right_outer = x1 - int(w * 0.08)
    top = y0 + int(h * 0.08)
    mid_top = y0 + int(h * 0.42)
    mid_bottom = y0 + int(h * 0.58)
    bottom = y1 - int(h * 0.08)

    purple = rgb(COLORS["primary"])
    teal = rgb(COLORS["secondary"])

    draw.polygon(
        [
            (left_outer, top),
            (left_inner, y0 + int(h * 0.2)),
            (left_inner, mid_top),
            (left_outer + int(w * 0.08), (mid_top + mid_bottom) // 2),
            (left_inner, mid_bottom),
            (left_inner, y1 - int(h * 0.2)),
            (left_outer, bottom),
        ],
        fill=purple,
    )
    draw.polygon(
        [
            (right_outer, top),
            (right_inner, y0 + int(h * 0.2)),
            (right_inner, mid_top),
            (right_outer - int(w * 0.08), (mid_top + mid_bottom) // 2),
            (right_inner, mid_bottom),
            (right_inner, y1 - int(h * 0.2)),
            (right_outer, bottom),
        ],
        fill=teal,
    )

    bridge_y = (y0 + y1) // 2
    square = max(4, int(w * 0.028))
    gap = max(4, int(w * 0.018))
    bridge_colors = [
        rgb(COLORS["primary"]),
        (100, 103, 235),
        (62, 151, 205),
        rgb(COLORS["secondary"]),
    ]
    total = square * 4 + gap * 3
    start = cx - total // 2
    for index, color in enumerate(bridge_colors):
        x = start + index * (square + gap)
        draw.rectangle((x, bridge_y - square // 2, x + square, bridge_y + square // 2), fill=color)


def save_png(image: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, format="PNG", optimize=True, compress_level=9)


def create_symbol(mode: str) -> None:
    size = 1024
    image = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw_symbol(draw, (120, 120, 904, 904))
    save_png(image, OUT / mode / "symbol.png")


def create_app_icon(mode: str) -> None:
    size = 1024
    image = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    if mode == "dark":
        tile = rgb(COLORS["background"])
        outline = rgb(COLORS["outline"])
    else:
        tile = (250, 251, 253)
        outline = (208, 214, 224)
    draw.rounded_rectangle((72, 72, 952, 952), radius=210, fill=tile, outline=outline, width=12)
    draw_symbol(draw, (210, 210, 814, 814))
    save_png(image, OUT / mode / "app-icon.png")


def create_favicon(mode: str) -> None:
    size = 256
    image = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    tile = rgb(COLORS["background"]) if mode == "dark" else (250, 251, 253)
    outline = rgb(COLORS["outline"]) if mode == "dark" else (208, 214, 224)
    draw.rounded_rectangle((12, 12, 244, 244), radius=52, fill=tile, outline=outline, width=4)
    draw_symbol(draw, (54, 54, 202, 202))
    save_png(image, OUT / mode / "favicon.png")


def create_logo_lockup(mode: str) -> None:
    width, height = 1600, 600
    image = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw_symbol(draw, (80, 90, 500, 510))
    primary_text = rgb(COLORS["text_primary"]) if mode == "dark" else (24, 31, 42)
    secondary_text = rgb(COLORS["text_secondary"]) if mode == "dark" else (82, 92, 108)
    divider = rgb(COLORS["outline"]) if mode == "dark" else (211, 216, 224)
    draw.text((555, 110), "Harness", font=font(122, bold=True), fill=primary_text)
    draw.text((565, 265), "Local AI Console", font=font(52), fill=secondary_text)
    draw.line((565, 355, 1460, 355), fill=divider, width=3)
    draw.text((565, 395), "Run local. Measure everything.", font=font(38), fill=secondary_text)
    save_png(image, OUT / mode / "logo-lockup.png")


def rounded_card(draw: ImageDraw.ImageDraw, rect: tuple[int, int, int, int], fill: tuple[int, int, int], outline: tuple[int, int, int]) -> None:
    draw.rounded_rectangle(rect, radius=18, fill=fill, outline=outline, width=2)


def create_component_sheet(mode: str) -> None:
    width, height = 1440, 1080
    light = mode == "light"
    background = (248, 250, 252) if light else rgb(COLORS["background"])
    surface = (255, 255, 255) if light else rgb(COLORS["surface"])
    elevated = (245, 247, 250) if light else rgb(COLORS["surface_elevated"])
    outline = (218, 224, 232) if light else rgb(COLORS["outline"])
    text = (25, 32, 43) if light else rgb(COLORS["text_primary"])
    muted = (103, 113, 128) if light else rgb(COLORS["text_secondary"])
    purple = rgb(COLORS["primary"])
    teal = rgb(COLORS["secondary"])
    success = rgb(COLORS["success"])
    warning = rgb(COLORS["warning"])
    error = rgb(COLORS["error"])

    image = Image.new("RGB", (width, height), background)
    draw = ImageDraw.Draw(image)

    draw_symbol(draw, (36, 32, 144, 140))
    draw.text((165, 40), "Harness", font=font(48, bold=True), fill=text)
    draw.text((166, 98), "Component sheet", font=font(24), fill=muted)

    draw.text((720, 28), "APP BAR", font=font(16, bold=True), fill=muted)
    rounded_card(draw, (715, 55, 1395, 140), surface, outline)
    draw_symbol(draw, (740, 68, 805, 133))
    draw.text((825, 78), "Harness", font=font(28, bold=True), fill=text)
    draw.text((955, 84), "Local AI Console", font=font(19), fill=muted)
    draw.ellipse((1300, 88, 1316, 104), fill=success)
    draw.text((1325, 79), "Local", font=font(22), fill=text)

    draw.text((36, 175), "BUTTONS", font=font(16, bold=True), fill=muted)
    draw.rounded_rectangle((36, 205, 250, 270), radius=14, fill=purple)
    draw.text((68, 222), "▶  Run locally", font=font(22, bold=True), fill=(255, 255, 255))
    rounded_card(draw, (270, 205, 490, 270), elevated, outline)
    draw.text((300, 222), "↓  Import model", font=font(22), fill=text)
    draw.rounded_rectangle((510, 205, 755, 270), radius=14, fill=surface, outline=error, width=3)
    draw.text((540, 222), "■  Stop generation", font=font(22), fill=error)

    draw.text((790, 175), "STATUS BADGES", font=font(16, bold=True), fill=muted)
    badges = [("● Local", success), ("✓ Healthy", success), ("△ Warning", warning), ("! Error", error)]
    bx = 790
    for label, color in badges:
        draw.rounded_rectangle((bx, 205, bx + 145, 260), radius=14, fill=surface, outline=color, width=2)
        draw.text((bx + 18, 221), label, font=font(19), fill=color)
        bx += 155

    draw.text((36, 320), "TELEMETRY CHIPS", font=font(16, bold=True), fill=muted)
    chips = [("TTFT 184 ms", teal), ("13.8 tok/s", teal), ("512 MB", muted), ("Warm", warning), ("Cache on", teal)]
    cx = 36
    for label, color in chips:
        chip_width = 190 if len(label) > 8 else 145
        draw.rounded_rectangle((cx, 350, cx + chip_width, 405), radius=14, fill=surface, outline=outline, width=2)
        draw.text((cx + 18, 366), label, font=font(19, mono=True), fill=color)
        cx += chip_width + 16

    draw.text((36, 455), "TOP NAVIGATION", font=font(16, bold=True), fill=muted)
    rounded_card(draw, (36, 485, 675, 555), surface, outline)
    draw_symbol(draw, (55, 495, 115, 545))
    draw.text((135, 504), "Harness", font=font(26, bold=True), fill=text)
    draw.text((250, 510), "Local AI Console", font=font(18), fill=muted)
    draw.ellipse((570, 512, 585, 527), fill=success)
    draw.text((595, 504), "Local", font=font(20), fill=text)

    draw.text((735, 455), "BOTTOM NAVIGATION", font=font(16, bold=True), fill=muted)
    rounded_card(draw, (735, 485, 1395, 555), surface, outline)
    nav_items = [("▦ Overview", purple), (">_ Playground", text), ("◇ Models", text), ("∿ Diagnostics", text)]
    nx = 765
    for label, color in nav_items:
        draw.text((nx, 507), label, font=font(18), fill=color)
        nx += 155

    draw.text((36, 610), "RUNTIME CARD", font=font(16, bold=True), fill=muted)
    rounded_card(draw, (36, 640, 465, 910), surface, outline)
    draw.text((60, 665), "Runtime", font=font(26, bold=True), fill=text)
    draw.ellipse((395, 675, 410, 690), fill=success)
    draw.text((417, 666), "Local", font=font(18), fill=muted)
    runtime_rows = [("Model", "Qwen 0.6B"), ("Context", "4,096"), ("Batch size", "128"), ("Threads", "8"), ("Uptime", "00:42:18"), ("Memory", "512 MB")]
    ry = 720
    for key, value in runtime_rows:
        draw.text((60, ry), key, font=font(18), fill=muted)
        draw.text((270, ry), value, font=font(18, mono=True), fill=text)
        ry += 30
    draw.rounded_rectangle((60, 875, 435, 888), radius=6, fill=outline)
    draw.rounded_rectangle((60, 875, 245, 888), radius=6, fill=teal)

    draw.text((500, 610), "MODEL CARD", font=font(16, bold=True), fill=muted)
    rounded_card(draw, (500, 640, 930, 910), surface, outline)
    draw.text((525, 665), "Qwen 0.6B GGUF", font=font(26, bold=True), fill=text)
    draw.text((810, 669), "Loaded", font=font(18), fill=success)
    model_rows = [("Quantization", "Q4_K_M"), ("File size", "520 MB"), ("Context", "4,096"), ("Source", "Local file"), ("Model ID", "qwen-0.6b")]
    my = 725
    for key, value in model_rows:
        draw.text((525, my), key, font=font(18), fill=muted)
        draw.text((710, my), value, font=font(18, mono=True), fill=text)
        my += 34
    draw.rounded_rectangle((750, 850, 900, 895), radius=12, fill=surface, outline=error, width=2)
    draw.text((790, 860), "Unload", font=font(18), fill=error)

    draw.text((970, 610), "ICON SET", font=font(16, bold=True), fill=muted)
    icon_items = [("▦", "Overview"), (">_", "Playground"), ("◇", "Models"), ("∿", "Diagnostics"), ("⚙", "Settings"), ("▣", "Privacy"), ("▤", "Runtime"), ("♨", "Thermal"), ("▧", "Logs"), ("▥", "Benchmark"), ("♡", "Health")]
    for index, (symbol, label) in enumerate(icon_items):
        col = index % 4
        row = index // 4
        x = 970 + col * 105
        y = 645 + row * 125
        draw.rounded_rectangle((x, y, x + 76, y + 76), radius=16, fill=elevated, outline=outline, width=2)
        icon_color = success if label == "Health" else text
        draw.text((x + 20, y + 18), symbol, font=font(30), fill=icon_color)
        draw.text((x - 4, y + 86), label, font=font(14), fill=muted)

    save_png(image, OUT / mode / "component-sheet.png")


def create_readme() -> None:
    content = """# Harness brand assets\n\nGenerated PNG assets for the Harness visual identity.\n\n## Dark mode\n\n- `dark/logo-lockup.png`\n- `dark/symbol.png`\n- `dark/app-icon.png`\n- `dark/favicon.png`\n- `dark/component-sheet.png`\n\n## Light mode\n\n- `light/logo-lockup.png`\n- `light/symbol.png`\n- `light/app-icon.png`\n- `light/favicon.png`\n- `light/component-sheet.png`\n\nRun `python scripts/generate_brand_assets.py` to regenerate the complete set.\n"""
    path = OUT / "README.md"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def main() -> None:
    for mode in ("dark", "light"):
        create_symbol(mode)
        create_app_icon(mode)
        create_favicon(mode)
        create_logo_lockup(mode)
        create_component_sheet(mode)
    create_readme()


if __name__ == "__main__":
    main()
