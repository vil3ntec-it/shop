'use strict';
/**
 * عکس‌های آزمونیِ کوچک ولی ساختار-درست (JPEG، PNG، WebP) — بی کتابخانه.
 * CRCِ PNG با `zlib.crc32` ِ خودِ Node ساخته می‌شود تا سنجشِ ‎lib/image-bytes‎
 * مستقل آزموده شود.
 */
const zlib = require('zlib');

function u16(n) { const b = Buffer.alloc(2); b.writeUInt16BE(n); return b; }

/**
 * JPEG: SOI، APP0، [APP1 Exif با orientation]، APP2 (ICC)، COM، SOF0، SOS، داده، EOI.
 * `sof`: ابعادِ یک SOFِ دیگر **پیش از** SOFِ اصلی (بمبِ «دو فریم»: رمزگشا اولی را می‌خواند).
 */
function jpeg(w, h, { orientation = 0, seed = 1, eoi = true, gps = false, sof = null } = {}) {
  const seg = (m, body) => Buffer.concat([Buffer.from([0xff, m]), u16(body.length + 2), body]);
  const parts = [Buffer.from([0xff, 0xd8]), seg(0xe0, Buffer.from('JFIF\0\x01\x01\0\0\x01\0\x01\0\0', 'latin1'))];
  if (orientation || gps) {
    //  TIFF (MM): IFD0 با یک ورودی — 0x0112 (orientation) یا 0x8825 (GPS)
    const tiff = Buffer.alloc(8 + 2 + 12 + 4);
    tiff.write('MM', 0, 'latin1'); tiff.writeUInt16BE(42, 2); tiff.writeUInt32BE(8, 4);
    tiff.writeUInt16BE(1, 8);
    tiff.writeUInt16BE(orientation ? 0x0112 : 0x8825, 10); tiff.writeUInt16BE(3, 12); tiff.writeUInt32BE(1, 14);
    tiff.writeUInt16BE(orientation || 7, 18);
    parts.push(seg(0xe1, Buffer.concat([Buffer.from('Exif\0\0', 'latin1'), tiff])));
  }
  parts.push(seg(0xe2, Buffer.from('ICC_PROFILE\0\x01\x01fake-icc', 'latin1')));
  parts.push(seg(0xfe, Buffer.from('comment: shot at 34.5,69.1', 'latin1')));
  if (sof) parts.push(seg(0xc0, Buffer.concat([Buffer.from([8]), u16(sof[1]), u16(sof[0]), Buffer.from([1, 1, 0x11, 0])])));
  parts.push(seg(0xc0, Buffer.concat([Buffer.from([8]), u16(h), u16(w), Buffer.from([1, 1, 0x11, 0])])));
  parts.push(seg(0xda, Buffer.from([1, 1, 0, 0, 0x3f, 0])));
  const data = Buffer.alloc(200, 7); data.writeUInt32BE(seed >>> 0, 0);
  parts.push(data);
  if (eoi) parts.push(Buffer.from([0xff, 0xd9]));
  return Buffer.concat(parts);
}

function pngChunk(type, data) {
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
  const td = Buffer.concat([Buffer.from(type, 'latin1'), data]);
  const crc = Buffer.alloc(4); crc.writeUInt32BE(zlib.crc32(td) >>> 0);
  return Buffer.concat([len, td, crc]);
}

function png(w, h, { text = true, seed = 1 } = {}) {
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 2;
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    pngChunk('IHDR', ihdr),
    ...(text ? [pngChunk('tEXt', Buffer.from('Comment\0GPS 34.5,69.1', 'latin1'))] : []),
    pngChunk('IDAT', zlib.deflateSync(Buffer.from(String(seed).repeat(16)))),
    pngChunk('IEND', Buffer.alloc(0)),
  ]);
}

/**
 * WebP با VP8X + EXIF + VP8L. `bits`: ابعادِ خودِ جریانِ VP8L اگر با بومِ VP8X فرق کند
 * (بمبِ «بومِ کوچک، تصویرِ بزرگ»).
 */
function webp(w, h, { exif = true, bits = null } = {}) {
  const chunk = (t, d) => {
    const len = Buffer.alloc(4); len.writeUInt32LE(d.length);
    return Buffer.concat([Buffer.from(t, 'latin1'), len, d, d.length & 1 ? Buffer.alloc(1) : Buffer.alloc(0)]);
  };
  const x = Buffer.alloc(10);
  x[0] = exif ? 0x08 : 0; x.writeUIntLE(w - 1, 4, 3); x.writeUIntLE(h - 1, 7, 3);
  const l = Buffer.alloc(9);
  const [bw, bh] = bits || [w, h];
  l[0] = 0x2f; l.writeUInt32LE((((bw - 1) & 0x3fff) | (((bh - 1) & 0x3fff) << 14)) >>> 0, 1);
  const body = Buffer.concat([chunk('VP8X', x), ...(exif ? [chunk('EXIF', Buffer.from('MM\0*gps', 'latin1'))] : []), chunk('VP8L', l)]);
  const head = Buffer.alloc(12);
  head.write('RIFF', 0, 'latin1'); head.writeUInt32LE(body.length + 4, 4); head.write('WEBP', 8, 'latin1');
  return Buffer.concat([head, body]);
}

/** WebPِ ساده‌ی «VP8 » (lossy): سرآیندِ فریمِ کلیدی با ابعاد. `start=false` ⇒ کدِ آغازِ خراب. */
function webpLossy(w, h, { start = true } = {}) {
  const d = Buffer.alloc(30);
  d[0] = 0x10; d[1] = 0x02; d[2] = 0x00;                                   // برچسبِ فریم: کلیدی، نمایش
  Buffer.from(start ? [0x9d, 0x01, 0x2a] : [0x00, 0x00, 0x00]).copy(d, 3);
  d.writeUInt16LE(w & 0x3fff, 6); d.writeUInt16LE(h & 0x3fff, 8);
  const len = Buffer.alloc(4); len.writeUInt32LE(d.length);
  const body = Buffer.concat([Buffer.from('VP8 ', 'latin1'), len, d]);
  const head = Buffer.alloc(12);
  head.write('RIFF', 0, 'latin1'); head.writeUInt32LE(body.length + 4, 4); head.write('WEBP', 8, 'latin1');
  return Buffer.concat([head, body]);
}

/**
 * بمبِ فشردهٔ واقعی: PNGِ سیاه‌وسفیدِ یک‌بیتیِ w×h با IDATِ درست (zlib). چند ده کیلوبایت
 * روی دیسک، ولی رمزگشای کامل w×h×۴ بایت حافظه می‌خواهد (۱۶۰۰۰² ⇐ حدودِ ۱ گیگابایت).
 */
function pngBomb(w, h) {
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 1; ihdr[9] = 0;
  const raw = Buffer.alloc((1 + Math.ceil(w / 8)) * h);   // هر سطر: بایتِ صافی + w بیت، همه صفر
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    pngChunk('IHDR', ihdr),
    pngChunk('IDAT', zlib.deflateSync(raw, { level: 9 })),
    pngChunk('IEND', Buffer.alloc(0)),
  ]);
}

/* ---------------------------------------------- عکس‌های پیکسل‌دار (IMG-15) */

const clamp8 = (v) => Math.max(0, Math.min(255, Math.round(v)));
const rgbOf = (v) => (typeof v === 'number' ? [v, v, v, 255] : [v[0], v[1], v[2], v[3] == null ? 255 : v[3]]).map(clamp8);
function paeth(a, b, c) {
  const p = a + b - c, pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
  return pa <= pb && pa <= pc ? a : pb <= pc ? b : c;
}

/**
 * PNG ِ واقعی از تابعِ پیکسل: fn(x, y) ⇒ عدد (خاکستری) یا [r, g, b] یا [r, g, b, a].
 * صافیِ هر سطر (y % 5) است تا هر پنج صافی رمزگشایی شوند؛ `depth: 16` بایتِ کم‌ارزش = همان.
 */
function pngImage(w, h, fn, { mode = 'gray', depth = 8 } = {}) {
  const ct = { gray: 0, rgb: 2, rgba: 6 }[mode], ch = { gray: 1, rgb: 3, rgba: 4 }[mode];
  const bpp = (ch * depth) / 8, stride = w * bpp;
  const raw = Buffer.alloc((stride + 1) * h);
  let prev = Buffer.alloc(stride);
  for (let y = 0; y < h; y++) {
    const row = Buffer.alloc(stride);
    for (let x = 0; x < w; x++) {
      const px = rgbOf(fn(x, y));
      const vals = mode === 'gray' ? [px[0]] : mode === 'rgb' ? px.slice(0, 3) : px;
      vals.forEach((c, k) => { const o = x * bpp + (k * depth) / 8; row[o] = c; if (depth === 16) row[o + 1] = c; });
    }
    const ft = y % 5, o = y * (stride + 1);
    raw[o] = ft;
    for (let k = 0; k < stride; k++) {
      const a = k >= bpp ? row[k - bpp] : 0, b = prev[k], c = k >= bpp ? prev[k - bpp] : 0;
      const pred = ft === 0 ? 0 : ft === 1 ? a : ft === 2 ? b : ft === 3 ? (a + b) >> 1 : paeth(a, b, c);
      raw[o + 1 + k] = (row[k] - pred) & 255;
    }
    prev = row;
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = depth; ihdr[9] = ct;
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    pngChunk('IHDR', ihdr), pngChunk('IDAT', zlib.deflateSync(raw)), pngChunk('IEND', Buffer.alloc(0)),
  ]);
}

const ZZ = (() => {
  const z = [];
  for (let s = 0; s < 15; s++) {
    const d = [];
    for (let r = 0; r <= s; r++) if (r < 8 && s - r < 8) d.push(r * 8 + (s - r));
    z.push(...(s % 2 === 0 ? d.reverse() : d));
  }
  return z;
})();
const COS = [];
for (let x = 0; x < 8; x++) for (let u = 0; u < 8; u++) COS.push(((u ? 1 : Math.SQRT1_2) / 2) * Math.cos(((2 * x + 1) * u * Math.PI) / 16));
//  جدولِ روشناییِ پیوستِ K (ترتیبِ طبیعی)
const BASE_LUMA = [16, 11, 10, 16, 24, 40, 51, 61, 12, 12, 14, 19, 26, 58, 60, 55, 14, 13, 16, 24, 40, 57, 69, 56,
  14, 17, 22, 29, 51, 87, 80, 62, 18, 22, 37, 56, 68, 109, 103, 77, 24, 35, 55, 64, 81, 104, 113, 92,
  49, 64, 78, 87, 103, 121, 120, 101, 72, 92, 95, 98, 112, 100, 103, 99];
//  جدول‌های Huffman ِ ساده ولی معتبر: DC ِ ۰..۱۱ همه ۴ بیت، AC ِ ۱۶۲ نماد همه ۸ بیت (کد = جایگاه)
const AC_SYMS = [0x00, 0xf0];
for (let r = 0; r < 16; r++) for (let s = 1; s <= 10; s++) AC_SYMS.push((r << 4) | s);
const AC_CODE = new Map(AC_SYMS.map((s, i) => [s, i]));

/**
 * JPEG ِ baseline ِ واقعی (DCT، کوانتیزهٔ IJG با `quality`، Huffman) از تابعِ پیکسل.
 * `color` ⇒ YCbCr با ۴:۲:۰ (MCU ِ ۱۶×۱۶)، وگرنه خاکستری؛ `restart` ⇒ DRI و RST هر n واحد.
 */
function jpegImage(w, h, fn, { quality = 90, color = false, restart = 0 } = {}) {
  const S = quality < 50 ? 5000 / quality : 200 - 2 * quality;
  const qz = ZZ.map((i) => Math.max(1, Math.min(255, Math.floor((BASE_LUMA[i] * S + 50) / 100))));
  const Y = new Float64Array(w * h), Cb = new Float64Array(w * h), Cr = new Float64Array(w * h);
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    const [r, g, b] = rgbOf(fn(x, y)), i = y * w + x;
    Y[i] = 0.299 * r + 0.587 * g + 0.114 * b;
    Cb[i] = -0.168736 * r - 0.331264 * g + 0.5 * b + 128;
    Cr[i] = 0.5 * r - 0.418688 * g - 0.081312 * b + 128;
  }
  const at = (p, x, y) => p[Math.min(h - 1, y) * w + Math.min(w - 1, x)];
  const sub = (p, x, y) => (at(p, 2 * x, 2 * y) + at(p, 2 * x + 1, 2 * y) + at(p, 2 * x, 2 * y + 1) + at(p, 2 * x + 1, 2 * y + 1)) / 4;
  const bytes = [];
  let acc = 0, n = 0;
  const put = (v, len) => {
    for (let k = len - 1; k >= 0; k--) {
      acc = (acc << 1) | ((v >> k) & 1);
      if (++n === 8) { bytes.push(acc); if (acc === 0xff) bytes.push(0); acc = 0; n = 0; }
    }
  };
  const flush = () => { while (n) put(1, 1); };
  const bitlen = (v) => (v ? 32 - Math.clz32(Math.abs(v)) : 0);
  const amp = (v, c) => (v >= 0 ? v : v + (1 << c) - 1);
  const pred = [0, 0, 0];
  const block = (ci, get) => {
    const F = new Array(64).fill(0);
    for (let v = 0; v < 8; v++) for (let u = 0; u < 8; u++) {
      let a = 0;
      for (let y = 0; y < 8; y++) for (let x = 0; x < 8; x++) a += COS[y * 8 + v] * COS[x * 8 + u] * (get(x, y) - 128);
      F[v * 8 + u] = a;
    }
    const z = ZZ.map((i, k) => Math.round(F[i] / qz[k]));
    const d = z[0] - pred[ci]; pred[ci] = z[0];
    const c = bitlen(d); put(c, 4); if (c) put(amp(d, c), c);
    let run = 0;
    for (let k = 1; k < 64; k++) {
      if (!z[k]) { run++; continue; }
      while (run > 15) { put(AC_CODE.get(0xf0), 8); run -= 16; }
      const s = bitlen(z[k]); put(AC_CODE.get((run << 4) | s), 8); put(amp(z[k], s), s); run = 0;
    }
    if (run) put(AC_CODE.get(0x00), 8);
  };
  const mcu = color ? 16 : 8, mx = Math.ceil(w / mcu), my = Math.ceil(h / mcu);
  for (let m = 0; m < mx * my; m++) {
    if (restart && m && m % restart === 0) {
      flush(); bytes.push(0xff, 0xd0 + (((m / restart) - 1) % 8)); pred.fill(0);
    }
    const X = (m % mx) * mcu, Yy = Math.floor(m / mx) * mcu;
    if (!color) { block(0, (x, y) => at(Y, X + x, Yy + y)); continue; }
    for (let by = 0; by < 2; by++) for (let bx = 0; bx < 2; bx++) block(0, (x, y) => at(Y, X + bx * 8 + x, Yy + by * 8 + y));
    block(1, (x, y) => sub(Cb, X / 2 + x, Yy / 2 + y));
    block(2, (x, y) => sub(Cr, X / 2 + x, Yy / 2 + y));
  }
  flush();
  const seg = (m, body) => Buffer.concat([Buffer.from([0xff, m]), u16(body.length + 2), Buffer.from(body)]);
  const nc = color ? 3 : 1;
  const sof = [8, h >> 8, h & 255, w >> 8, w & 255, nc, 1, color ? 0x22 : 0x11, 0];
  if (color) sof.push(2, 0x11, 0, 3, 0x11, 0);
  const counts = (len, k) => Array.from({ length: 16 }, (_, i) => (i === len - 1 ? k : 0));
  const sos = [nc, 1, 0];
  if (color) sos.push(2, 0, 3, 0);
  sos.push(0, 63, 0);
  return Buffer.concat([
    Buffer.from([0xff, 0xd8]),
    seg(0xdb, [0, ...qz]),
    seg(0xc0, sof),
    seg(0xc4, [0x00, ...counts(4, 12), ...Array.from({ length: 12 }, (_, i) => i)]),
    seg(0xc4, [0x10, ...counts(8, AC_SYMS.length), ...AC_SYMS]),
    ...(restart ? [seg(0xdd, [restart >> 8, restart & 255])] : []),
    seg(0xda, sos), Buffer.from(bytes), Buffer.from([0xff, 0xd9]),
  ]);
}

/** صحنهٔ محصول: زمینهٔ یک‌دست و جعبهٔ شطرنجی در مختصاتِ نسبی (همان صحنه در هر اندازه ⇒ تکراریِ واقعی) */
const scene = (w, h, { bg = 245, lo = 0, hi = 255, box = [0.25, 0.2, 0.75, 0.8], cells = 40 } = {}) => (x, y) => {
  const u = x / w, v = y / h;
  if (u < box[0] || u >= box[2] || v < box[1] || v >= box[3]) return bg;
  return (Math.floor(u * cells) + Math.floor(v * cells)) % 2 ? hi : lo;
};

/** دو بارِ تارِ جعبه‌ایِ (2r+1) افقی و عمودی */
function blurred(w, h, fn, r) {
  let a = new Float64Array(w * h);
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) a[y * w + x] = fn(x, y);
  const pass = (src, dx, dy) => {
    const out = new Float64Array(w * h);
    for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
      let s = 0;
      for (let k = -r; k <= r; k++) {
        const xx = Math.min(w - 1, Math.max(0, x + k * dx)), yy = Math.min(h - 1, Math.max(0, y + k * dy));
        s += src[yy * w + xx];
      }
      out[y * w + x] = s / (2 * r + 1);
    }
    return out;
  };
  for (let p = 0; p < 2; p++) a = pass(pass(a, 1, 0), 0, 1);
  return (x, y) => a[y * w + x];
}

/** مولّدِ شبه‌تصادفیِ قطعی (mulberry32) — نویزِ یکسان در هر اجرا */
function rng(seed) {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/** GTINِ معتبر از ۱۲ رقمِ بدنه */
function ean13(body12) {
  let sum = 0;
  for (let i = 0; i < 12; i++) sum += Number(body12[i]) * (i % 2 ? 3 : 1);
  return body12 + ((10 - (sum % 10)) % 10);
}

module.exports = { jpeg, png, webp, webpLossy, pngBomb, pngChunk, ean13, pngImage, jpegImage, scene, blurred, rng };
