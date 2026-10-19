'use strict';
/**
 * عکس‌های آزمونیِ کوچک ولی ساختار-درست (JPEG، PNG، WebP) — بی کتابخانه.
 * CRCِ PNG با `zlib.crc32` ِ خودِ Node ساخته می‌شود تا سنجشِ ‎lib/image-bytes‎
 * مستقل آزموده شود.
 */
const zlib = require('zlib');

function u16(n) { const b = Buffer.alloc(2); b.writeUInt16BE(n); return b; }

/** JPEG: SOI، APP0، [APP1 Exif با orientation]، APP2 (ICC)، COM، SOF0، SOS، داده، EOI */
function jpeg(w, h, { orientation = 0, seed = 1, eoi = true, gps = false } = {}) {
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

/** WebP با VP8X + EXIF + VP8L */
function webp(w, h, { exif = true } = {}) {
  const chunk = (t, d) => {
    const len = Buffer.alloc(4); len.writeUInt32LE(d.length);
    return Buffer.concat([Buffer.from(t, 'latin1'), len, d, d.length & 1 ? Buffer.alloc(1) : Buffer.alloc(0)]);
  };
  const x = Buffer.alloc(10);
  x[0] = exif ? 0x08 : 0; x.writeUIntLE(w - 1, 4, 3); x.writeUIntLE(h - 1, 7, 3);
  const l = Buffer.alloc(9);
  l[0] = 0x2f; l.writeUInt32LE(((w - 1) & 0x3fff) | (((h - 1) & 0x3fff) << 14), 1);
  const body = Buffer.concat([chunk('VP8X', x), ...(exif ? [chunk('EXIF', Buffer.from('MM\0*gps', 'latin1'))] : []), chunk('VP8L', l)]);
  const head = Buffer.alloc(12);
  head.write('RIFF', 0, 'latin1'); head.writeUInt32LE(body.length + 4, 4); head.write('WEBP', 8, 'latin1');
  return Buffer.concat([head, body]);
}

/** GTINِ معتبر از ۱۲ رقمِ بدنه */
function ean13(body12) {
  let sum = 0;
  for (let i = 0; i < 12; i++) sum += Number(body12[i]) * (i % 2 ? 3 : 1);
  return body12 + ((10 - (sum % 10)) % 10);
}

module.exports = { jpeg, png, webp, ean13 };
