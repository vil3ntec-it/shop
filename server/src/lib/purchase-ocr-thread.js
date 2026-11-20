'use strict';
/**
 * رشتهٔ جدای بازسازیِ عکسِ فاکتور (‎lib/purchase-ocr.js‎ ‹prepare›): رمزگشایی بیرون از حلقهٔ رویدادِ سرور با سقفِ حافظه.
 * JPEG/PNG ِ سنجیده ⇒ جهتِ EXIF روی پیکسل ⇒ کوچک تا `side` (هرگز بزرگ‌تر) ⇒ خاکستری روی زمینهٔ سفید ⇒ PGM (P5).
 * PGM هیچ جایی برای متادیتا ندارد، پس EXIF/GPS/XMP/ICC ِ اصل هرگز به Tesseract نمی‌رسد.
 * فقط محاسبه — نه پایگاه، نه شبکه، نه فایل.
 */
const { parentPort, workerData } = require('worker_threads');
const codec = require('./image-codec');

try {
  const { buf, info, side } = workerData;
  let img = codec.decode(Buffer.from(buf), info);
  if (!img || img.reason) {
    parentPort.postMessage({ ok: false, code: 'image_unsupported' });
  } else {
    img = codec.resize(codec.orient(img, info.orientation), side);
    const { w, h, rgb, alpha } = img;
    const head = Buffer.from(`P5\n${w} ${h}\n255\n`, 'ascii');
    const out = Buffer.allocUnsafe(head.length + w * h);
    head.copy(out, 0);
    for (let i = 0, o = head.length; i < w * h; i++, o++) {
      const y = 0.299 * rgb[i * 3] + 0.587 * rgb[i * 3 + 1] + 0.114 * rgb[i * 3 + 2];
      const a = alpha ? alpha[i] / 255 : 1;
      out[o] = Math.max(0, Math.min(255, Math.round(a * y + (1 - a) * 255)));
    }
    parentPort.postMessage({ ok: true, pgm: out });
  }
} catch {
  parentPort.postMessage({ ok: false, code: 'prep_failed' });
}
