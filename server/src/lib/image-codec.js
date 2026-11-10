'use strict';
/**
 * رمزگشا و رمزگذارِ پیکسلیِ عکس برای استودیوی عکس (AI-02، §۳۴.۱، §۳۶.۴) — بی هیچ کتابخانه.
 *
 *   decode(buf, info)          ⇒ { w, h, rgb, alpha|null } یا { reason }   (info = ‎image-bytes.inspect‎)
 *   orient(img, orientation)   ⇒ همان عکس با جهتِ EXIF روی پیکسل (۱ تا ۸)
 *   resize(img, maxSide)       ⇒ کوچک‌سازیِ میانگینِ مساحتی؛ ⛔ هرگز بزرگ نمی‌کند (جزئیاتِ ساختگی نه)
 *   encodeJpeg(img, quality)   ⇒ Buffer ِ JPEG ِ baseline، ۴:۲:۰، جدول‌های پیوستِ K، فقط JFIF (بی EXIF/GPS)
 *
 * ⛔ رمزگشایی فقط پس از سنجشِ سرآیند (‎image-bytes‎: نوع از محتوا، ≤ ۴۰ مگاپیکسل) و با سقفِ جدای خودش
 *    (`MAX_PIXELS`)؛ PNG با `maxOutputLength` = اندازهٔ دقیقِ خام. JPEG ِ progressive، چنداسکنی، CMYK،
 *    PNG ِ درهم یا کم‌تر از ۸ بیت و WebP ⇒ `{ reason }`، هرگز حدس یا خطای ۵۰۰.
 * ⛔ همان جدول‌خوان و همان ضریب‌های IDCT ِ ‎lib/image-quality.js‎ (IMG-15) — یک پیاده‌سازیِ JPEG، نه دو.
 */
const zlib = require('zlib');
const Q = require('./image-quality');

const MAX_PIXELS = 16000000;          // همان DECODE_MAX_PIXELS ِ IMG-15
const PNG_MAX_RAW = 64 * 1024 * 1024;
const clamp8 = (v) => (v < 0 ? 0 : v > 255 ? 255 : Math.round(v));

/* ------------------------------------------------------------- PNG */

function paeth(a, b, c) {
  const p = a + b - c, pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
  return pa <= pb && pa <= pc ? a : pb <= pc ? b : c;
}

function decodePng(buf) {
  let i = 8, ihdr = null, plte = null, trns = null;
  const idat = [];
  while (i + 12 <= buf.length) {
    const len = buf.readUInt32BE(i), type = buf.toString('latin1', i + 4, i + 8), data = buf.subarray(i + 8, i + 8 + len);
    if (type === 'IHDR') { if (ihdr) return null; ihdr = data; }   // SR44-1: IHDR ِ دوم ⇒ ابعادِ سنجیده‌نشده، نه رمزگشایی
    else if (type === 'PLTE') plte = data;
    else if (type === 'tRNS') trns = data;
    else if (type === 'IDAT') idat.push(data);
    else if (type === 'IEND') break;
    i += 12 + len;
  }
  if (!ihdr || ihdr.length !== 13 || !idat.length) return null;
  const w = ihdr.readUInt32BE(0), h = ihdr.readUInt32BE(4), depth = ihdr[8], ct = ihdr[9];
  const ch = { 0: 1, 2: 3, 3: 1, 4: 2, 6: 4 }[ct];
  if (!ch || ihdr[12] !== 0 || (depth !== 8 && depth !== 16) || (ct === 3 && (depth !== 8 || !plte))) return null;
  const bpp = (ch * depth) / 8, stride = w * bpp, raw = (stride + 1) * h;
  if (raw > PNG_MAX_RAW) return null;
  let data;
  try { data = zlib.inflateSync(Buffer.concat(idat), { maxOutputLength: raw }); } catch { return null; }
  if (data.length !== raw) return null;
  const hi = depth === 16 ? 2 : 1;
  //  کلیدِ رنگِ شفاف (tRNS ِ نوعِ ۰ و ۲): بایتِ پرارزشِ هر نمونه
  const key = trns && ct === 0 && trns.length >= 2 ? [trns[0]] : trns && ct === 2 && trns.length >= 6 ? [trns[0], trns[2], trns[4]] : null;
  const rgb = new Uint8Array(w * h * 3), alpha = new Uint8Array(w * h);
  let prev = new Uint8Array(stride), cur = new Uint8Array(stride), clear = false;
  for (let y = 0; y < h; y++) {
    const o = y * (stride + 1), ft = data[o];
    if (ft > 4) return null;
    for (let k = 0; k < stride; k++) {
      const x = data[o + 1 + k], a = k >= bpp ? cur[k - bpp] : 0, b = prev[k], c = k >= bpp ? prev[k - bpp] : 0;
      cur[k] = (ft === 0 ? x : ft === 1 ? x + a : ft === 2 ? x + b : ft === 3 ? x + ((a + b) >> 1) : x + paeth(a, b, c)) & 255;
    }
    for (let px = 0; px < w; px++) {
      const p = px * bpp, n = y * w + px;
      let r, g, bl, al = 255;
      if (ct === 3) {
        const e = cur[p];
        if (e * 3 + 2 >= plte.length) return null;
        r = plte[e * 3]; g = plte[e * 3 + 1]; bl = plte[e * 3 + 2];
        if (trns && e < trns.length) al = trns[e];
      } else if (ct === 0 || ct === 4) {
        r = g = bl = cur[p];
        if (ct === 4) al = cur[p + hi];
        else if (key && r === key[0]) al = 0;
      } else {
        r = cur[p]; g = cur[p + hi]; bl = cur[p + 2 * hi];
        if (ct === 6) al = cur[p + 3 * hi];
        else if (key && r === key[0] && g === key[1] && bl === key[2]) al = 0;
      }
      rgb[n * 3] = r; rgb[n * 3 + 1] = g; rgb[n * 3 + 2] = bl; alpha[n] = al;
      if (al < 255) clear = true;
    }
    const t = prev; prev = cur; cur = t;
  }
  return { w, h, rgb, alpha: clear ? alpha : null };
}

/* ------------------------------------------------------------ JPEG */

/** APP14 ِ Adobe: transform = 0 ⇒ سه کانالِ RGB (نه YCbCr) */
function adobeRgb(buf) {
  let i = 2;
  while (i + 4 <= buf.length && buf[i] === 0xff) {
    const m = buf[i + 1];
    if (m === 0xda || m === 0xd9) break;
    const len = buf.readUInt16BE(i + 2);
    if (m === 0xee && len >= 14 && buf.toString('latin1', i + 4, i + 9) === 'Adobe') return buf[i + 4 + 11] === 0;
    i += 2 + len;
  }
  return false;
}

function decodeJpeg(buf) {
  const t = Q.jpegTables(buf);
  if (!t || t.unsupported || !t.frame) return null;
  const { frame, qt, ht, dri } = t;
  const W = frame.width, H = frame.height, nc = frame.comps.length;
  if (!W || !H) return null;
  const hmax = Math.max(...frame.comps.map((c) => c.h)), vmax = Math.max(...frame.comps.map((c) => c.v));
  const ns = buf[t.sos];
  if (ns !== nc) return null;                       // هر کانال در اسکنِ جدا ⇒ پشتیبانی نمی‌شود
  const mcusX = Math.ceil(W / (8 * hmax)), mcusY = Math.ceil(H / (8 * vmax));
  const scan = [];
  for (let k = 0; k < ns; k++) {
    const o = t.sos + 1 + k * 2;
    const c = frame.comps.find((x) => x.id === buf[o]);
    const dc = ht[buf[o + 1] >> 4], ac = ht[4 + (buf[o + 1] & 15)], q = c && qt[c.tq];
    if (!c || !dc || !ac || !q || scan.some((s) => s.c === c)) return null;
    //  تک‌کانال: هر واحد یک بلوک و شمارِ بلوک از ابعادِ خودِ کانال؛ چندکانال: MCU ِ کامل
    const bw = ns === 1 ? Math.ceil(Math.ceil((W * c.h) / hmax) / 8) : mcusX * c.h;
    const bh = ns === 1 ? Math.ceil(Math.ceil((H * c.v) / vmax) / 8) : mcusY * c.v;
    scan.push({ c, dc, ac, q, pred: 0, pw: bw * 8, plane: new Uint8Array(bw * 8 * bh * 8), bw, bh });
  }
  const tail = t.sos + 1 + ns * 2;
  if (buf[tail] !== 0 || buf[tail + 1] !== 63 || buf[tail + 2] !== 0) return null;

  let pos = t.data, cur = 0, cnt = 0, overrun = false;
  const bit = () => {
    if (cnt === 0) {
      let b = 0;
      if (pos < buf.length && !(buf[pos] === 0xff && buf[pos + 1] !== 0)) {
        b = buf[pos];
        pos += b === 0xff ? 2 : 1;
      } else overrun = true;
      cur = b; cnt = 8;
    }
    cnt--;
    return (cur >> cnt) & 1;
  };
  const receive = (s) => {
    let v = 0;
    for (let k = 0; k < s; k++) v = (v << 1) | bit();
    return v < 1 << (s - 1) ? v - (1 << s) + 1 : v;
  };
  const huff = (tb) => {
    let code = 0;
    for (let l = 1; l <= 16; l++) {
      code = (code << 1) | bit();
      if (tb.maxcode[l] >= 0 && code <= tb.maxcode[l]) return tb.symbols[tb.valptr[l] + code - tb.mincode[l]];
    }
    throw new Error('huffman');
  };
  const { ZZ, COS } = Q;
  const F = new Float64Array(64), tmp = new Float64Array(64);
  const block = (sc, bx, by) => {
    F.fill(0);
    const sDc = huff(sc.dc);
    if (sDc > 11) throw new Error('dc');
    sc.pred += sDc ? receive(sDc) : 0;
    F[0] = sc.pred * sc.q[0];
    let onlyDc = true;
    for (let k = 1; k < 64;) {
      const rs = huff(sc.ac), r = rs >> 4, s = rs & 15;
      if (!s) { if (r !== 15) break; k += 16; continue; }
      k += r;
      if (k > 63) throw new Error('ac');
      F[ZZ[k]] = receive(s) * sc.q[k]; onlyDc = false; k++;
    }
    if (bx >= sc.bw || by >= sc.bh) return;
    const base = by * 8 * sc.pw + bx * 8, P = sc.plane;
    if (onlyDc) {
      const v = clamp8(F[0] / 8 + 128);
      for (let y = 0; y < 8; y++) P.fill(v, base + y * sc.pw, base + y * sc.pw + 8);
      return;
    }
    for (let v = 0; v < 8; v++) for (let x = 0; x < 8; x++) {
      let a = 0;
      for (let u = 0; u < 8; u++) a += COS[x * 8 + u] * F[v * 8 + u];
      tmp[v * 8 + x] = a;
    }
    for (let y = 0; y < 8; y++) for (let x = 0; x < 8; x++) {
      let a = 0;
      for (let v = 0; v < 8; v++) a += COS[y * 8 + v] * tmp[v * 8 + x];
      P[base + y * sc.pw + x] = clamp8(a + 128);
    }
  };
  const units = ns === 1 ? scan[0].bw * scan[0].bh : mcusX * mcusY;
  const unit = ns === 1
    ? (n) => block(scan[0], n % scan[0].bw, Math.floor(n / scan[0].bw))
    : (n) => {
      const mx = n % mcusX, my = Math.floor(n / mcusX);
      for (const sc of scan) for (let by = 0; by < sc.c.v; by++) for (let bx = 0; bx < sc.c.h; bx++) block(sc, mx * sc.c.h + bx, my * sc.c.v + by);
    };
  try {
    for (let n = 0; n < units; n++) {
      if (dri && n && n % dri === 0) {
        cnt = 0;
        while (buf[pos] === 0xff && buf[pos + 1] === 0xff) pos++;
        if (buf[pos] !== 0xff || buf[pos + 1] < 0xd0 || buf[pos + 1] > 0xd7) return null;
        pos += 2;
        for (const sc of scan) sc.pred = 0;
      }
      unit(n);
      if (overrun) return null;
    }
  } catch { return null; }

  //  به ترتیبِ کانال‌های SOF (نه اسکن)؛ نمونه‌برداری با نزدیک‌ترین نمونه
  const planes = frame.comps.map((c) => scan.find((s) => s.c === c));
  const rgb = new Uint8Array(W * H * 3), rgbIn = nc === 3 && (adobeRgb(buf) || frame.comps.map((c) => c.id).join() === '82,71,66');
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    const s = planes.map((p) => p.plane[Math.floor((y * p.c.v) / vmax) * p.pw + Math.floor((x * p.c.h) / hmax)]);
    const o = (y * W + x) * 3;
    if (nc === 1) { rgb[o] = rgb[o + 1] = rgb[o + 2] = s[0]; continue; }
    if (rgbIn) { rgb[o] = s[0]; rgb[o + 1] = s[1]; rgb[o + 2] = s[2]; continue; }
    const Y = s[0], cb = s[1] - 128, cr = s[2] - 128;
    rgb[o] = clamp8(Y + 1.402 * cr);
    rgb[o + 1] = clamp8(Y - 0.344136 * cb - 0.714136 * cr);
    rgb[o + 2] = clamp8(Y + 1.772 * cb);
  }
  return { w: W, h: H, rgb, alpha: null };
}

/** رمزگشایی ⇒ { w, h, rgb, alpha } یا { reason } — دلیل‌ها همان واژه‌های IMG-15 */
function decode(buf, info) {
  if (!info || !info.width || !info.height) return { reason: 'undecodable' };
  if (info.width * info.height > MAX_PIXELS) return { reason: 'too_many_pixels' };
  if (info.mime === 'image/webp') return { reason: 'format' };
  let img = null;
  try { img = info.mime === 'image/jpeg' ? decodeJpeg(buf) : info.mime === 'image/png' ? decodePng(buf) : null; } catch { img = null; }
  if (img && (img.w !== info.width || img.h !== info.height)) img = null;
  return img || { reason: 'undecodable' };
}

/* ------------------------------------------------------- هندسه */

/** جهتِ EXIF روی پیکسل: (x', y') ِ خروجی از کدام (x, y) ِ ورودی می‌آید (۲ تا ۸؛ ۵ تا ۸ ضلع‌ها جابه‌جا) */
function orient(img, orientation) {
  const o = Number(orientation) || 1;
  if (o < 2 || o > 8) return img;
  const { w: W, h: H } = img, swap = o >= 5;
  const w = swap ? H : W, h = swap ? W : H;
  const src = (x, y) => {
    switch (o) {
      case 2: return [W - 1 - x, y];
      case 3: return [W - 1 - x, H - 1 - y];
      case 4: return [x, H - 1 - y];
      case 5: return [y, x];
      case 6: return [y, H - 1 - x];
      case 7: return [W - 1 - y, H - 1 - x];
      default: return [W - 1 - y, x];       // ۸
    }
  };
  const rgb = new Uint8Array(w * h * 3), alpha = img.alpha ? new Uint8Array(w * h) : null;
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    const [sx, sy] = src(x, y), s = sy * W + sx, d = y * w + x;
    rgb[d * 3] = img.rgb[s * 3]; rgb[d * 3 + 1] = img.rgb[s * 3 + 1]; rgb[d * 3 + 2] = img.rgb[s * 3 + 2];
    if (alpha) alpha[d] = img.alpha[s];
  }
  return { w, h, rgb, alpha };
}

/** وزن‌های میانگینِ مساحتیِ یک محور: هر خانهٔ خروجی بازهٔ [i·s, (i+1)·s) ِ ورودی را می‌پوشاند */
function weights(n, m) {
  const s = n / m, out = [];
  for (let i = 0; i < m; i++) {
    const a = i * s, b = a + s, list = [];
    for (let k = Math.floor(a); k < Math.min(n, Math.ceil(b)); k++) list.push([k, (Math.min(b, k + 1) - Math.max(a, k)) / s]);
    out.push(list);
  }
  return out;
}

/** کوچک‌سازی تا ضلعِ بلند ≤ maxSide؛ کوچک‌تر یا برابر ⇒ همان عکس (هرگز بزرگ‌نمایی) */
function resize(img, maxSide) {
  const { w: W, h: H } = img, k = maxSide / Math.max(W, H);
  if (k >= 1) return img;
  const w = Math.max(1, Math.round(W * k)), h = Math.max(1, Math.round(H * k));
  const ch = img.alpha ? 4 : 3, wx = weights(W, w), wy = weights(H, h);
  const at = (n, c) => (c < 3 ? img.rgb[n * 3 + c] : img.alpha[n]);
  const tmp = new Float32Array(w * H * ch);
  for (let y = 0; y < H; y++) for (let x = 0; x < w; x++) {
    for (let c = 0; c < ch; c++) {
      let a = 0;
      for (const [k, wt] of wx[x]) a += at(y * W + k, c) * wt;
      tmp[(y * w + x) * ch + c] = a;
    }
  }
  const rgb = new Uint8Array(w * h * 3), alpha = img.alpha ? new Uint8Array(w * h) : null;
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    for (let c = 0; c < ch; c++) {
      let a = 0;
      for (const [k, wt] of wy[y]) a += tmp[(k * w + x) * ch + c] * wt;
      if (c < 3) rgb[(y * w + x) * 3 + c] = clamp8(a); else alpha[y * w + x] = clamp8(a);
    }
  }
  return { w, h, rgb, alpha };
}

/* ------------------------------------------------------ رمزگذار */

//  پیوستِ K: جدول‌های کوانتیزه (ترتیبِ طبیعی) و Huffman ِ استاندارد
const QL = [16, 11, 10, 16, 24, 40, 51, 61, 12, 12, 14, 19, 26, 58, 60, 55, 14, 13, 16, 24, 40, 57, 69, 56,
  14, 17, 22, 29, 51, 87, 80, 62, 18, 22, 37, 56, 68, 109, 103, 77, 24, 35, 55, 64, 81, 104, 113, 92,
  49, 64, 78, 87, 103, 121, 120, 101, 72, 92, 95, 98, 112, 100, 103, 99];
const QC = [17, 18, 24, 47, 99, 99, 99, 99, 18, 21, 26, 66, 99, 99, 99, 99, 24, 26, 56, 99, 99, 99, 99, 99,
  47, 66, 99, 99, 99, 99, 99, 99, ...new Array(32).fill(99)];
const DC_L = [[0, 1, 5, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0], [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11]];
const DC_C = [[0, 3, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0], [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11]];
const AC_L = [[0, 2, 1, 3, 3, 2, 4, 3, 5, 5, 4, 4, 0, 0, 1, 0x7d], [
  0x01, 0x02, 0x03, 0x00, 0x04, 0x11, 0x05, 0x12, 0x21, 0x31, 0x41, 0x06, 0x13, 0x51, 0x61, 0x07,
  0x22, 0x71, 0x14, 0x32, 0x81, 0x91, 0xa1, 0x08, 0x23, 0x42, 0xb1, 0xc1, 0x15, 0x52, 0xd1, 0xf0,
  0x24, 0x33, 0x62, 0x72, 0x82, 0x09, 0x0a, 0x16, 0x17, 0x18, 0x19, 0x1a, 0x25, 0x26, 0x27, 0x28,
  0x29, 0x2a, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39, 0x3a, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49,
  0x4a, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58, 0x59, 0x5a, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68, 0x69,
  0x6a, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78, 0x79, 0x7a, 0x83, 0x84, 0x85, 0x86, 0x87, 0x88, 0x89,
  0x8a, 0x92, 0x93, 0x94, 0x95, 0x96, 0x97, 0x98, 0x99, 0x9a, 0xa2, 0xa3, 0xa4, 0xa5, 0xa6, 0xa7,
  0xa8, 0xa9, 0xaa, 0xb2, 0xb3, 0xb4, 0xb5, 0xb6, 0xb7, 0xb8, 0xb9, 0xba, 0xc2, 0xc3, 0xc4, 0xc5,
  0xc6, 0xc7, 0xc8, 0xc9, 0xca, 0xd2, 0xd3, 0xd4, 0xd5, 0xd6, 0xd7, 0xd8, 0xd9, 0xda, 0xe1, 0xe2,
  0xe3, 0xe4, 0xe5, 0xe6, 0xe7, 0xe8, 0xe9, 0xea, 0xf1, 0xf2, 0xf3, 0xf4, 0xf5, 0xf6, 0xf7, 0xf8,
  0xf9, 0xfa]];
const AC_C = [[0, 2, 1, 2, 4, 4, 3, 4, 7, 5, 4, 4, 0, 1, 2, 0x77], [
  0x00, 0x01, 0x02, 0x03, 0x11, 0x04, 0x05, 0x21, 0x31, 0x06, 0x12, 0x41, 0x51, 0x07, 0x61, 0x71,
  0x13, 0x22, 0x32, 0x81, 0x08, 0x14, 0x42, 0x91, 0xa1, 0xb1, 0xc1, 0x09, 0x23, 0x33, 0x52, 0xf0,
  0x15, 0x62, 0x72, 0xd1, 0x0a, 0x16, 0x24, 0x34, 0xe1, 0x25, 0xf1, 0x17, 0x18, 0x19, 0x1a, 0x26,
  0x27, 0x28, 0x29, 0x2a, 0x35, 0x36, 0x37, 0x38, 0x39, 0x3a, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48,
  0x49, 0x4a, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58, 0x59, 0x5a, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68,
  0x69, 0x6a, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78, 0x79, 0x7a, 0x82, 0x83, 0x84, 0x85, 0x86, 0x87,
  0x88, 0x89, 0x8a, 0x92, 0x93, 0x94, 0x95, 0x96, 0x97, 0x98, 0x99, 0x9a, 0xa2, 0xa3, 0xa4, 0xa5,
  0xa6, 0xa7, 0xa8, 0xa9, 0xaa, 0xb2, 0xb3, 0xb4, 0xb5, 0xb6, 0xb7, 0xb8, 0xb9, 0xba, 0xc2, 0xc3,
  0xc4, 0xc5, 0xc6, 0xc7, 0xc8, 0xc9, 0xca, 0xd2, 0xd3, 0xd4, 0xd5, 0xd6, 0xd7, 0xd8, 0xd9, 0xda,
  0xe2, 0xe3, 0xe4, 0xe5, 0xe6, 0xe7, 0xe8, 0xe9, 0xea, 0xf2, 0xf3, 0xf4, 0xf5, 0xf6, 0xf7, 0xf8,
  0xf9, 0xfa]];

/** پیوستِ C: کد و طولِ هر نماد از شمارِ طول‌ها */
function codesOf([bits, vals]) {
  //  نمادِ جاافتاده یا اضافه جدول را می‌شکند ⇒ همین‌جا، هنگامِ بارِ ماژول
  if (bits.reduce((a, b) => a + b, 0) !== vals.length || new Set(vals).size !== vals.length) throw new Error('jpeg huffman table');
  const code = new Int32Array(256), len = new Int32Array(256);
  let c = 0, k = 0;
  for (let l = 1; l <= 16; l++) {
    for (let i = 0; i < bits[l - 1]; i++) { code[vals[k]] = c; len[vals[k]] = l; c++; k++; }
    c <<= 1;
  }
  return { code, len };
}
const HUFF = { dcL: codesOf(DC_L), acL: codesOf(AC_L), dcC: codesOf(DC_C), acC: codesOf(AC_C) };

function scaled(base, quality) {
  const s = quality < 50 ? 5000 / quality : 200 - 2 * quality;
  return Q.ZZ.map((i) => Math.max(1, Math.min(255, Math.floor((base[i] * s + 50) / 100))));   // ترتیبِ زیگزاگ
}

/** JPEG ِ baseline ِ ۴:۲:۰ (کیفیتِ ۵۰ تا ۹۵؛ بیرون از بازه به مرز) — فقط JFIF، بی هیچ فراداده */
function encodeJpeg(img, quality = 85) {
  const qv = Math.max(50, Math.min(95, Math.round(quality)));
  const { w, h } = img, qL = scaled(QL, qv), qC = scaled(QC, qv);
  const Y = new Float32Array(w * h), cw = Math.ceil(w / 2), chh = Math.ceil(h / 2);
  const Cb = new Float32Array(cw * chh), Cr = new Float32Array(cw * chh), cn = new Float32Array(cw * chh);
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    const n = y * w + x, r = img.rgb[n * 3], g = img.rgb[n * 3 + 1], b = img.rgb[n * 3 + 2];
    Y[n] = 0.299 * r + 0.587 * g + 0.114 * b;
    const m = (y >> 1) * cw + (x >> 1);
    Cb[m] += -0.168736 * r - 0.331264 * g + 0.5 * b + 128;
    Cr[m] += 0.5 * r - 0.418688 * g - 0.081312 * b + 128;
    cn[m]++;
  }
  for (let m = 0; m < cn.length; m++) { Cb[m] /= cn[m]; Cr[m] /= cn[m]; }

  let out = Buffer.alloc(Math.max(65536, w * h * 2)), op = 0, acc = 0, nb = 0;
  const byte = (v) => {
    if (op >= out.length) { const g = Buffer.alloc(out.length * 2); out.copy(g); out = g; }
    out[op++] = v;
  };
  const put = (v, l) => {
    for (let k = l - 1; k >= 0; k--) {
      acc = (acc << 1) | ((v >> k) & 1);
      if (++nb === 8) { byte(acc); if (acc === 0xff) byte(0); acc = 0; nb = 0; }
    }
  };
  const cat = (v) => (v ? 32 - Math.clz32(Math.abs(v)) : 0);
  const { ZZ, COS } = Q;
  const blk = new Float64Array(64), tmp = new Float64Array(64), pred = [0, 0, 0];
  const code = (ci, plane, pw, ph, x0, y0, q, dc, ac) => {
    for (let y = 0; y < 8; y++) for (let x = 0; x < 8; x++) {
      blk[y * 8 + x] = plane[Math.min(ph - 1, y0 + y) * pw + Math.min(pw - 1, x0 + x)] - 128;
    }
    for (let y = 0; y < 8; y++) for (let u = 0; u < 8; u++) {
      let a = 0;
      for (let x = 0; x < 8; x++) a += COS[x * 8 + u] * blk[y * 8 + x];
      tmp[y * 8 + u] = a;
    }
    const z = new Int32Array(64);
    for (let k = 0; k < 64; k++) {
      const i = ZZ[k], u = i & 7, v = i >> 3;
      let a = 0;
      for (let y = 0; y < 8; y++) a += COS[y * 8 + v] * tmp[y * 8 + u];
      z[k] = Math.max(-1023, Math.min(1023, Math.round(a / q[k])));
    }
    const d = z[0] - pred[ci], s = cat(d);
    pred[ci] = z[0];
    put(dc.code[s], dc.len[s]);
    if (s) put(d < 0 ? d + (1 << s) - 1 : d, s);
    let run = 0;
    for (let k = 1; k < 64; k++) {
      if (!z[k]) { run++; continue; }
      while (run > 15) { put(ac.code[0xf0], ac.len[0xf0]); run -= 16; }
      const t = cat(z[k]), sym = (run << 4) | t;
      put(ac.code[sym], ac.len[sym]);
      put(z[k] < 0 ? z[k] + (1 << t) - 1 : z[k], t);
      run = 0;
    }
    if (run) put(ac.code[0], ac.len[0]);
  };
  const mx = Math.ceil(w / 16), my = Math.ceil(h / 16);
  for (let m = 0; m < mx * my; m++) {
    const X = (m % mx) * 16, Yy = Math.floor(m / mx) * 16;
    for (let by = 0; by < 2; by++) for (let bx = 0; bx < 2; bx++) code(0, Y, w, h, X + bx * 8, Yy + by * 8, qL, HUFF.dcL, HUFF.acL);
    code(1, Cb, cw, chh, X / 2, Yy / 2, qC, HUFF.dcC, HUFF.acC);
    code(2, Cr, cw, chh, X / 2, Yy / 2, qC, HUFF.dcC, HUFF.acC);
  }
  while (nb) put(1, 1);
  const seg = (m, body) => {
    const b = Buffer.from(body), hd = Buffer.from([0xff, m, 0, 0]);
    hd.writeUInt16BE(b.length + 2, 2);
    return Buffer.concat([hd, b]);
  };
  const dht = (cls, [bits, vals]) => [cls, ...bits, ...vals];
  return Buffer.concat([
    Buffer.from([0xff, 0xd8]),
    seg(0xe0, [0x4a, 0x46, 0x49, 0x46, 0, 1, 1, 0, 0, 1, 0, 1, 0, 0]),
    seg(0xdb, [0, ...qL, 1, ...qC]),
    seg(0xc0, [8, h >> 8, h & 255, w >> 8, w & 255, 3, 1, 0x22, 0, 2, 0x11, 1, 3, 0x11, 1]),
    seg(0xc4, [...dht(0x00, DC_L), ...dht(0x10, AC_L), ...dht(0x01, DC_C), ...dht(0x11, AC_C)]),
    seg(0xda, [3, 1, 0x00, 2, 0x11, 3, 0x11, 0, 63, 0]),
    out.subarray(0, op), Buffer.from([0xff, 0xd9]),
  ]);
}

module.exports = { decode, orient, resize, encodeJpeg, MAX_PIXELS };
