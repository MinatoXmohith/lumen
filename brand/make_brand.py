"""Lumen icon and TV banner (glass, light, mkdziner). Run with Pillow + numpy from the lumen folder."""
import os
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__)); ROOT = os.path.dirname(HERE)
FONT = os.path.join(ROOT, "launcher/app/src/main/res/font/outfit.ttf")
RES = os.path.join(ROOT, "launcher/app/src/main/res")

def font(size, w):
    f = ImageFont.truetype(FONT, size); f.set_variation_by_axes([w]); return f

def backdrop(w, h, s):
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    base = np.array([10, 12, 22], np.float32)
    img = np.ones((h, w, 3), np.float32) * base
    def glow(cx, cy, r, col, k):
        d = np.exp(-(((xx - cx) ** 2 + (yy - cy) ** 2) / (r * r)))[..., None]
        return d * np.array(col, np.float32) * k
    img += glow(w * 0.30, h * 0.30, 0.55 * max(w, h), (255, 170, 90), 0.42)    # warm light
    img += glow(w * 0.78, h * 0.85, 0.50 * max(w, h), (80, 150, 255), 0.38)    # cool light
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))

def glass(im, box, radius, s):
    x0, y0, x1, y1 = box
    region = im.crop(box).filter(ImageFilter.GaussianBlur(10 * s)).point(lambda v: int(v * 0.9))
    m = Image.new("L", region.size, 0); ImageDraw.Draw(m).rounded_rectangle([0, 0, region.width - 1, region.height - 1], radius, fill=255)
    im.paste(region, box[:2], m)
    over = Image.new("RGBA", im.size, (0, 0, 0, 0)); d = ImageDraw.Draw(over)
    d.rounded_rectangle(box, radius, fill=(8, 10, 20, 70))
    d.rounded_rectangle(box, radius, outline=(255, 255, 255, 150), width=max(2, int(2 * s)))
    sheen = Image.new("L", im.size, 0); ImageDraw.Draw(sheen).rounded_rectangle([x0 + 3 * s, y0 + 3 * s, x1 - 3 * s, y0 + (y1 - y0) * 0.45], radius, fill=26)
    over.paste((255, 255, 255, 255), (0, 0), sheen)
    return Image.alpha_composite(im.convert("RGBA"), over)

# TV banner 320x180 (rendered at 4x)
S = 4; W, H = 320 * S, 180 * S
im = backdrop(W, H, S)
im = glass(im, (28 * S, 34 * S, 292 * S, 146 * S), 26 * S, S)
d = ImageDraw.Draw(im)
d.ellipse([52 * S, 66 * S, 100 * S, 114 * S], fill=(255, 214, 150, 255))                 # the light
for o in (1 * S,):
    d.text((116 * S + o, 90 * S + o), "Lumen", font=font(50 * S, 600), fill=(0, 0, 0, 60), anchor="lm")
d.text((116 * S, 90 * S), "Lumen", font=font(50 * S, 600), fill=(255, 255, 255), anchor="lm")
d.text((118 * S, 126 * S), "by mkdziner", font=font(13 * S, 500), fill=(220, 225, 240), anchor="lm")
glow = Image.new("RGBA", im.size, (0, 0, 0, 0)); ImageDraw.Draw(glow).ellipse([40 * S, 54 * S, 112 * S, 126 * S], fill=(255, 190, 110, 140))
im = Image.alpha_composite(glow.filter(ImageFilter.GaussianBlur(10 * S)), im)
im.convert("RGB").resize((320, 180), Image.LANCZOS).save(os.path.join(RES, "drawable-nodpi/banner.jpg"), quality=92)
im.convert("RGB").resize((640, 360), Image.LANCZOS).save(os.path.join(HERE, "banner@2x.jpg"), quality=92)

# app icon 96x96 (rendered at 8x): glass tile with the light and an L
S = 8; W = H = 96 * S
im = backdrop(W, H, S)
im = glass(im, (10 * S, 10 * S, 86 * S, 86 * S), 20 * S, S)
glow = Image.new("RGBA", im.size, (0, 0, 0, 0)); ImageDraw.Draw(glow).ellipse([22 * S, 22 * S, 58 * S, 58 * S], fill=(255, 190, 110, 170))
im = Image.alpha_composite(glow.filter(ImageFilter.GaussianBlur(6 * S)), im)
d = ImageDraw.Draw(im)
d.ellipse([30 * S, 30 * S, 50 * S, 50 * S], fill=(255, 220, 160, 255))
d.text((60 * S, 62 * S), "L", font=font(44 * S, 600), fill=(255, 255, 255), anchor="mm")
im.convert("RGB").resize((96, 96), Image.LANCZOS).save(os.path.join(RES, "mipmap-xhdpi/ic_launcher.jpg"), quality=95) if False else None
im.convert("RGB").resize((192, 192), Image.LANCZOS).save(os.path.join(RES, "mipmap-xhdpi/ic_launcher.jpg"), quality=95)
im.convert("RGB").resize((512, 512), Image.LANCZOS).save(os.path.join(HERE, "icon-512.png"))
print("ok")
