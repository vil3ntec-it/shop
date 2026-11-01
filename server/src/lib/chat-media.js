'use strict';
/**
 * رسانهٔ چتِ مشتری ↔ دکان — شناختن از روی بایت و پاک کردنِ metadata (v3 §۳۳.۲–۳۳.۳).
 *
 *   sniff(buf)               ⇒ { kind, mime } یا null — فقط از روی بایت‌های آغاز
 *   stripImageMetadata(buf)  ⇒ همان عکس بی EXIF ِ مکان/XMP/IPTC/توضیح؛ جهت می‌ماند
 *   send(res, m)             ⇒ بایت‌ها به بیننده با سرآیندهای امن (`chat.sendMedia`)
 *
 * ⛔ سرآیندِ `Content-Type` و پسوندِ کلاینت هیچ‌وقت ملاک نیست: فایلِ اجرایی
 * یا HTML ای که خودش را «image/jpeg» می‌نامد همین‌جا null می‌گیرد (CHAT-10).
 * ⛔ SVG، GIF، HEIC، ویدیو و WAV پذیرفته نمی‌شوند: SVG اسکریپت دارد، بقیه
 * یا حجیم‌اند یا روی همهٔ دستگاه‌ها باز نمی‌شوند. برنامه پیش از فرستادن به
 * JPEG/WebP و صدای فشرده (Opus/AAC) برمی‌گرداند.
 * ⛔ هیچ رمزگشاییِ تصویری این‌جا نیست (سرور کتابخانهٔ تصویر ندارد): فقط
 * بخش‌های metadata کنار می‌روند و دادهٔ تصویر بایت‌به‌بایت همان می‌ماند ⇒ نه
 * افتِ کیفیت، نه چرخشِ غلط (CHAT-06). ساختارِ خراب ⇒ خطای `bad_media`.
 */
const { sniff: sniffImage } = require('./product-photos');
const { badRequest } = require('../middleware/errors');

const ascii = (buf, from, to) => buf.slice(from, to).toString('latin1');

/** نوع و mime از روی بایت‌ها — یا null. */
function sniff(buf) {
  if (!Buffer.isBuffer(buf) || buf.length < 12) return null;
  const image = sniffImage(buf);
  if (image) return { kind: 'image', mime: image };
  if (ascii(buf, 0, 5) === '%PDF-') return { kind: 'file', mime: 'application/pdf' };
  if (ascii(buf, 0, 4) === 'OggS') return { kind: 'audio', mime: 'audio/ogg' };
  if (buf[0] === 0x1a && buf[1] === 0x45 && buf[2] === 0xdf && buf[3] === 0xa3) return { kind: 'audio', mime: 'audio/webm' };
  if (ascii(buf, 0, 6) === '#!AMR\n') return { kind: 'audio', mime: 'audio/amr' };
  if (ascii(buf, 4, 8) === 'ftyp') {
    const brand = ascii(buf, 8, 12);
    if (/^3g[p2]/.test(brand)) return { kind: 'audio', mime: 'audio/3gpp' };
    if (/^(M4A |M4B |mp41|mp42|isom|iso[2-6]|dash)$/.test(brand)) return { kind: 'audio', mime: 'audio/mp4' };
    return null;   // ‎qt‎ و برندهای دیگرِ ویدیو
  }
  if (ascii(buf, 0, 3) === 'ID3') return { kind: 'audio', mime: 'audio/mpeg' };
  if (buf[0] === 0xff && (buf[1] & 0xe0) === 0xe0) {
    //  هم‌گامِ قاب: لایهٔ ‎00‎ یعنی ADTS ِ AAC؛ بقیه MP3
    return (buf[1] & 0x06) === 0 ? { kind: 'audio', mime: 'audio/aac' } : { kind: 'audio', mime: 'audio/mpeg' };
  }
  return null;
}

// ════════════════════════════════════════════════════════════════════
//  JPEG — همهٔ APP1 (Exif و XMP)، APP13 (IPTC) و COM می‌روند؛ اگر جهتِ
//  عکس چیزی جز ۱ بود، یک Exif ِ کمینه فقط با همان یک برچسب برمی‌گردد.
// ════════════════════════════════════════════════════════════════════

function orientationOf(tiff) {
  if (tiff.length < 8) return 0;
  const order = ascii(tiff, 0, 2);
  if (order !== 'II' && order !== 'MM') return 0;
  const le = order === 'II';
  const u16 = (o) => (o + 2 <= tiff.length ? (le ? tiff.readUInt16LE(o) : tiff.readUInt16BE(o)) : -1);
  const u32 = (o) => (o + 4 <= tiff.length ? (le ? tiff.readUInt32LE(o) : tiff.readUInt32BE(o)) : -1);
  if (u16(2) !== 42) return 0;
  const ifd = u32(4);
  const count = u16(ifd);
  if (ifd < 8 || count < 0) return 0;
  for (let i = 0; i < Math.min(count, 512); i++) {
    const e = ifd + 2 + i * 12;
    if (u16(e) === 0x0112 && u16(e + 2) === 3) {
      const v = u16(e + 8);
      return v >= 1 && v <= 8 ? v : 0;
    }
  }
  return 0;
}

function minimalExif(orientation) {
  const seg = Buffer.alloc(36);
  seg.writeUInt16BE(0xffe1, 0);
  seg.writeUInt16BE(34, 2);                     // طول (بی خودِ نشانگر)
  seg.write('Exif\0\0', 4, 'latin1');
  seg.write('MM', 10, 'latin1');
  seg.writeUInt16BE(42, 12);
  seg.writeUInt32BE(8, 14);                     // IFD0
  seg.writeUInt16BE(1, 18);                     // یک برچسب
  seg.writeUInt16BE(0x0112, 20);                // Orientation
  seg.writeUInt16BE(3, 22);                     // SHORT
  seg.writeUInt32BE(1, 24);
  seg.writeUInt16BE(orientation, 28);
  seg.writeUInt32BE(0, 32);                     // IFD ِ بعدی: نیست
  return seg;
}

function stripJpeg(buf) {
  const corrupt = () => badRequest('عکس خراب است', 'bad_media');
  const keep = [];
  let orientation = 0;
  let i = 2;
  for (;;) {
    if (i + 2 > buf.length) throw corrupt();
    if (buf[i] !== 0xff) throw corrupt();
    if (buf[i + 1] === 0xff) { i += 1; continue; }        // بایتِ پُرکننده
    const marker = buf[i + 1];
    if (marker === 0xda) { keep.push(buf.slice(i)); break; } // آغازِ داده: بقیه دست‌نخورده
    if (marker === 0xd9) { keep.push(buf.slice(i, i + 2)); break; }
    if (marker === 0x01 || (marker >= 0xd0 && marker <= 0xd7)) { keep.push(buf.slice(i, i + 2)); i += 2; continue; }
    if (i + 4 > buf.length) throw corrupt();
    const len = buf.readUInt16BE(i + 2);
    if (len < 2 || i + 2 + len > buf.length) throw corrupt();
    const seg = buf.slice(i, i + 2 + len);
    if (marker === 0xe1) {
      if (ascii(seg, 4, 10) === 'Exif\0\0') orientation = orientationOf(seg.slice(10)) || orientation;
    } else if (marker !== 0xed && marker !== 0xfe) {
      keep.push(seg);
    }
    i += 2 + len;
  }
  const head = [buf.slice(0, 2)];
  if (orientation > 1) head.push(minimalExif(orientation));
  return Buffer.concat([...head, ...keep]);
}

// ════════════════════════════════════════════════════════════════════
//  PNG — تکه‌های eXIf، tEXt، zTXt، iTXt و tIME می‌روند.
// ════════════════════════════════════════════════════════════════════

const PNG_DROP = new Set(['eXIf', 'tEXt', 'zTXt', 'iTXt', 'tIME']);

function stripPng(buf) {
  const out = [buf.slice(0, 8)];
  let i = 8;
  for (;;) {
    if (i + 12 > buf.length) throw badRequest('عکس خراب است', 'bad_media');
    const len = buf.readUInt32BE(i);
    const type = ascii(buf, i + 4, i + 8);
    const end = i + 12 + len;
    if (len > buf.length || end > buf.length) throw badRequest('عکس خراب است', 'bad_media');
    if (!PNG_DROP.has(type)) out.push(buf.slice(i, end));
    i = end;
    if (type === 'IEND') break;
  }
  return Buffer.concat(out);
}

// ════════════════════════════════════════════════════════════════════
//  WebP — تکه‌های EXIF و XMP می‌روند و بیتِ همان‌ها در VP8X صفر می‌شود.
// ════════════════════════════════════════════════════════════════════

function stripWebp(buf) {
  const out = [];
  let i = 12;
  while (i + 8 <= buf.length) {
    const type = ascii(buf, i, i + 4);
    const len = buf.readUInt32LE(i + 4);
    const end = i + 8 + len + (len & 1);
    if (end > buf.length + 1) throw badRequest('عکس خراب است', 'bad_media');
    if (type !== 'EXIF' && type !== 'XMP ') {
      let chunk = Buffer.from(buf.slice(i, Math.min(end, buf.length)));
      if (type === 'VP8X' && chunk.length > 8) chunk[8] &= ~(0x08 | 0x04);
      out.push(chunk);
    }
    i = end;
  }
  const body = Buffer.concat(out);
  const head = Buffer.alloc(12);
  head.write('RIFF', 0, 'latin1');
  head.writeUInt32LE(body.length + 4, 4);
  head.write('WEBP', 8, 'latin1');
  return Buffer.concat([head, body]);
}

function stripImageMetadata(buf, mime) {
  if (mime === 'image/jpeg') return stripJpeg(buf);
  if (mime === 'image/png') return stripPng(buf);
  if (mime === 'image/webp') return stripWebp(buf);
  return buf;
}

/**
 * بایت‌ها به بیننده — هرگز صفحه نمی‌شود، کش نمی‌شود؛ PDF فقط دانلود. از `lib/shop-chat.js` (همان
 * `chat.sendMedia`) به این‌جا آمد تا آن زیرِ سقفِ اندازه بماند؛ رفتار همان.
 */
function send(res, m) {
  res.set('Content-Type', m.mime);
  res.set('Content-Length', String(m.data.length));
  res.set('Cache-Control', 'private, no-store');
  res.set('X-Content-Type-Options', 'nosniff');
  res.set('Content-Security-Policy', "default-src 'none'; sandbox");
  res.set('Cross-Origin-Resource-Policy', 'same-origin');
  res.set('Content-Disposition', m.kind === 'file' ? `attachment; filename="chat-${m.id}.pdf"` : 'inline');
  res.end(m.data);
}

module.exports = { sniff, stripImageMetadata, orientationOf, send };
