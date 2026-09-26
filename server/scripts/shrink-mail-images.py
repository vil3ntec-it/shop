"""تصویرهای بزرگِ ایمیل را به پالتِ ۲۵۶رنگه (با شفافیت) می‌برد — ایمیلِ سبک‌تر.

    python3 scripts/shrink-mail-images.py   (پس از build-mail-images.cjs؛ Pillow لازم است)
"""
import glob, os
from PIL import Image
for f in glob.glob(os.path.join(os.path.dirname(__file__), '..', 'src', 'lib', 'mail-templates', '*-img', '*.png')):
    if os.path.getsize(f) < 20000:
        continue
    im = Image.open(f).convert('RGBA')
    q = im.quantize(colors=256, method=Image.Quantize.FASTOCTREE, dither=Image.Dither.FLOYDSTEINBERG)
    before = os.path.getsize(f)
    q.save(f, optimize=True)
    print(os.path.basename(f), before, '->', os.path.getsize(f))
