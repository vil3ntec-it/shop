'use strict';
/**
 * بایت‌های عکس — بی هیچ کتابخانهٔ بومی (بانکِ تصاویر، v3 ۳۴.۳، ۳۶.۹، ۳۶.۱۱).
 *
 *   inspect(buf)           ⇒ { mime, width, height, orientation, warnings[] } یا خطای ۴۰۰
 *   stripMetadata(buf)     ⇒ همان عکس بی EXIF/XMP/IPTC/متن (رنگ‌نمای ICC می‌ماند)
 *
 * ⛔ نوع فقط از محتوا (همان `sniff`ِ عکسِ کالاها، ‎lib/product-photos.js‎)؛
 *    پسوند و Content-Type هیچ‌وقت باور نمی‌شوند.
 * ⛔ این‌جا «سرآیند» خوانده می‌شود، نه خودِ تصویر: ابعاد، ساختارِ قطعه‌ها و
 *    CRCِ PNG. رمزگشاییِ کاملِ پیکسل‌ها (و در نتیجه کوچک‌کردن، WebP/AVIF،
 *    تشخیصِ تاری) روی سرور نیست — هیچ وابستگیِ نصب‌شده‌ای این کار را نمی‌کند
 *    و افزودنِ کتابخانهٔ بومیِ سنگین ممنوع است. نسخه‌های کوچک را برنامه
 *    می‌سازد (مثلِ امروزِ عکسِ ۸۰۰ پیکسلی) و این‌جا فقط سنجیده می‌شوند.
 * ⛔ بمبِ فشرده: بیش از ۱۰٬۰۰۰ پیکسل در یک بُعد یا ۴۰ مگاپیکسل رد می‌شود.
 */
const { sniff } = require('./product-photos');
const { badRequest } = require('../middleware/errors');

const MAX_SIDE = 10000;
const MAX_PIXELS = 40 * 1000 * 1000;

const corrupt = (why) => badRequest('فایلِ عکس خراب یا ناقص است (' + why + ')', 'image_corrupt');

/* ------------------------------------------------------------- JPEG */

/** قطعه‌های پیش از SOS: [{ marker, start, end }] — end یعنی پایانِ قطعه با سرآیندش. */
function jpegSegments(buf) {
  const out = [];
  let i = 2;
  while (i + 4 <= buf.length) {
    if (buf[i] !== 0xff) throw corrupt('jpeg-marker');
    let m = buf[i + 1];
    while (m === 0xff && i + 2 < buf.length) { i++; m = buf[i + 1]; }  // پرکننده
    if (m === 0xd8 || m === 0x01 || (m >= 0xd0 && m <= 0xd7)) { i += 2; continue; }
    const len = buf.readUInt16BE(i + 2);
    if (len < 2 || i + 2 + len > buf.length) throw corrupt('jpeg-length');
    out.push({ marker: m, start: i, end: i + 2 + len });
    if (m === 0xda) return out;                 // پس از SOS دادهٔ فشرده است
    i += 2 + len;
  }
  throw corrupt('jpeg-sos');
}

function jpegOrientation(buf, seg) {
  const s = seg.start + 4;
  if (buf.toString('latin1', s, s + 6) !== 'Exif\0\0') return 0;
  const t = s + 6;
  if (t + 8 > seg.end) return 0;
  const le = buf.toString('latin1', t, t + 2) === 'II';
  const u16 = (o) => (le ? buf.readUInt16LE(o) : buf.readUInt16BE(o));
  const u32 = (o) => (le ? buf.readUInt32LE(o) : buf.readUInt32BE(o));
  const ifd = t + u32(t + 4);
  if (ifd + 2 > seg.end) return 0;
  const n = u16(ifd);
  for (let k = 0; k < n; k++) {
    const e = ifd + 2 + k * 12;
    if (e + 12 > seg.end) return 0;
    if (u16(e) === 0x0112) return u16(e + 8);
  }
  return 0;
}

function inspectJpeg(buf) {
  const segs = jpegSegments(buf);
  let width = 0, height = 0, orientation = 0;
  for (const s of segs) {
    const m = s.marker;
    if (m >= 0xc0 && m <= 0xcf && m !== 0xc4 && m !== 0xc8 && m !== 0xcc) {
      if (s.end - s.start < 9) throw corrupt('jpeg-sof');
      height = buf.readUInt16BE(s.start + 5);
      width = buf.readUInt16BE(s.start + 7);
    } else if (m === 0xe1 && !orientation) {
      orientation = jpegOrientation(buf, s);
    }
  }
  const warnings = [];
  const tail = buf.subarray(Math.max(0, buf.length - 64));
  if (tail.indexOf(Buffer.from([0xff, 0xd9])) < 0) warnings.push('truncated_suspect');
  return { width, height, orientation, warnings };
}

/** APP1 (EXIF/XMP)، APP13 (IPTC) و COM حذف؛ APP0، APP2 (ICC) و APP14 می‌مانند. */
function stripJpeg(buf) {
  const segs = jpegSegments(buf);
  const parts = [buf.subarray(0, 2)];
  for (const s of segs) {
    if (s.marker === 0xda) { parts.push(buf.subarray(s.start)); break; }
    if (s.marker === 0xe1 || s.marker === 0xed || s.marker === 0xfe) continue;
    parts.push(buf.subarray(s.start, s.end));
  }
  return Buffer.concat(parts);
}

/* -------------------------------------------------------------- PNG */

const CRC_TABLE = (() => {
  const t = new Int32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    t[n] = c;
  }
  return t;
})();
function crc32(buf, start, end) {
  let c = -1;
  for (let i = start; i < end; i++) c = CRC_TABLE[(c ^ buf[i]) & 0xff] ^ (c >>> 8);
  return (c ^ -1) >>> 0;
}

function pngChunks(buf) {
  const out = [];
  let i = 8;
  while (i + 12 <= buf.length) {
    const len = buf.readUInt32BE(i);
    const type = buf.toString('latin1', i + 4, i + 8);
    const end = i + 12 + len;
    if (end > buf.length) throw corrupt('png-length');
    if (crc32(buf, i + 4, i + 8 + len) !== buf.readUInt32BE(i + 8 + len)) throw corrupt('png-crc');
    out.push({ type, start: i, end });
    i = end;
    if (type === 'IEND') return out;
  }
  throw corrupt('png-iend');
}

const PNG_DROP = new Set(['tEXt', 'zTXt', 'iTXt', 'eXIf', 'tIME']);

function inspectPng(buf) {
  const chunks = pngChunks(buf);
  if (!chunks.length || chunks[0].type !== 'IHDR') throw corrupt('png-ihdr');
  return { width: buf.readUInt32BE(16), height: buf.readUInt32BE(20), orientation: 0, warnings: [] };
}

function stripPng(buf) {
  const parts = [buf.subarray(0, 8)];
  for (const c of pngChunks(buf)) if (!PNG_DROP.has(c.type)) parts.push(buf.subarray(c.start, c.end));
  return Buffer.concat(parts);
}

/* ------------------------------------------------------------- WebP */

function webpChunks(buf) {
  const riff = buf.readUInt32LE(4);
  if (riff + 8 > buf.length || riff < 4) throw corrupt('webp-riff');
  const out = [];
  let i = 12;
  while (i + 8 <= riff + 8) {
    const type = buf.toString('latin1', i, i + 4);
    const len = buf.readUInt32LE(i + 4);
    const end = i + 8 + len + (len & 1);
    if (i + 8 + len > buf.length) throw corrupt('webp-chunk');
    out.push({ type, start: i, end: Math.min(end, buf.length), data: i + 8, len });
    i = end;
  }
  if (!out.length) throw corrupt('webp-empty');
  return out;
}

function inspectWebp(buf) {
  const chunks = webpChunks(buf);
  const c = chunks[0];
  let width = 0, height = 0;
  if (c.type === 'VP8 ' && c.len >= 10) {
    width = buf.readUInt16LE(c.data + 6) & 0x3fff;
    height = buf.readUInt16LE(c.data + 8) & 0x3fff;
  } else if (c.type === 'VP8L' && c.len >= 5 && buf[c.data] === 0x2f) {
    const b = buf.readUInt32LE(c.data + 1);
    width = (b & 0x3fff) + 1;
    height = ((b >>> 14) & 0x3fff) + 1;
  } else if (c.type === 'VP8X' && c.len >= 10) {
    width = buf.readUIntLE(c.data + 4, 3) + 1;
    height = buf.readUIntLE(c.data + 7, 3) + 1;
  } else {
    throw corrupt('webp-header');
  }
  return { width, height, orientation: 0, warnings: [] };
}

function stripWebp(buf) {
  const chunks = webpChunks(buf);
  const kept = chunks.filter((c) => c.type !== 'EXIF' && c.type !== 'XMP ');
  if (kept.length === chunks.length) return buf;
  const body = Buffer.concat(kept.map((c) => Buffer.from(buf.subarray(c.start, c.end))));
  if (kept[0].type === 'VP8X') body[8] &= ~0x0c;               // پرچمِ EXIF و XMP
  const head = Buffer.alloc(12);
  head.write('RIFF', 0, 'latin1');
  head.writeUInt32LE(body.length + 4, 4);
  head.write('WEBP', 8, 'latin1');
  return Buffer.concat([head, body]);
}

/* ------------------------------------------------------------ عمومی */

function inspect(buf) {
  if (!Buffer.isBuffer(buf) || buf.length < 24) throw badRequest('عکس خالی است', 'image_empty');
  const mime = sniff(buf);
  if (!mime) throw badRequest('فقط عکسِ JPG، PNG یا WebP — نوعِ فایل از محتوا سنجیده می‌شود', 'image_type');
  const info = mime === 'image/jpeg' ? inspectJpeg(buf) : mime === 'image/png' ? inspectPng(buf) : inspectWebp(buf);
  if (!info.width || !info.height) throw corrupt('dimensions');
  if (info.width > MAX_SIDE || info.height > MAX_SIDE || info.width * info.height > MAX_PIXELS) {
    throw badRequest('ابعادِ عکس بیش از حد بزرگ است', 'image_too_large_dimensions');
  }
  return { mime, ...info };
}

function stripMetadata(buf) {
  const mime = sniff(buf);
  if (mime === 'image/jpeg') return stripJpeg(buf);
  if (mime === 'image/png') return stripPng(buf);
  if (mime === 'image/webp') return stripWebp(buf);
  throw badRequest('فقط عکسِ JPG، PNG یا WebP', 'image_type');
}

const EXT_MIME = { jpg: 'image/jpeg', jpeg: 'image/jpeg', png: 'image/png', webp: 'image/webp' };
/** پسوندِ نامِ فایل با محتوا جور است؟ (نامِ بی پسوند ⇒ بله) */
function extensionMatches(name, mime) {
  const m = /\.([A-Za-z0-9]{1,5})$/.exec(String(name || ''));
  if (!m) return true;
  return EXT_MIME[m[1].toLowerCase()] === mime;
}

module.exports = { inspect, stripMetadata, extensionMatches, crc32, MAX_SIDE, MAX_PIXELS };
