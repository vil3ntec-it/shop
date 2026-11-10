'use strict';
/**
 * معیارهای کیفیتِ عکس پیش از انتشار — IMG-15 (§۳۴.۲ «قبل از انتشار …»، §۳۴.۴).
 *
 *   analyze(buf, info)   ⇒ { metrics, flags[], dhash }   (info = خروجیِ ‎image-bytes.inspect‎)
 *   flagsFrom(metrics)   ⇒ flags[]                       (خالص؛ آستانه‌ها در LIMITS)
 *   isNearDuplicate(a,b) ⇒ dHash ِ دو عکس حداکثر DUP_HAMMING_MAX بیت فرق دارد؟
 *   fingerprint(buf,info)⇒ { dhash, hist } یا null — جست‌وجوی تصویری (AI-11)
 *
 * ⛔ فقط نشانِ پیشنهادی برای بازبینِ انسانی («نتایج پیشنهاد هستند»، §۳۴.۲)؛ هیچ بارگذاری
 *    با این‌ها رد نمی‌شود. پشتِ پرچمِ خاموشِ `image_bank.quality` در ‎lib/image-bank.js‎.
 * ⛔ رمزگشایی فقط پس از سنجشِ سرآیند (≤ ۴۰ مگاپیکسل) و با سقفِ جدای خودش: PNG با zlib ِ
 *    خودِ Node و `maxOutputLength` = اندازهٔ دقیقِ خام؛ JPEG ِ baseline (Huffman) فقط کانالِ
 *    روشنایی. progressive، PNG ِ درهم یا کم‌تر از ۸ بیت، WebP، دادهٔ خراب یا بیش از سقف ⇒
 *    `quality_unmeasured` و هیچ نشانِ پیکسلی — نه خطا. بی هیچ کتابخانه.
 * ⛔ آستانه‌ها روی مجموعهٔ مصنوعیِ ‎test/image-quality.test.js‎ آزموده شده‌اند، نه روی عکسِ
 *    واقعیِ مجاز (GATE-NEW-04 بیرونی است) — برای تنظیم، `metrics` در پاسخِ بارگذاری می‌آید.
 */
const zlib = require('zlib');

const LIMITS = Object.freeze({
  ASPECT_MAX: 3,                  // ضلعِ بلند ÷ کوتاه > ۳ ⇒ aspect_extreme
  JPEG_QUALITY_MIN: 50,           // کیفیتِ تخمینیِ جدولِ کوانتیزهٔ روشنایی < ۵۰ ⇒ over_compressed
  HEAVY_BYTES: 256 * 1024,        // حجم > ۲۵۶ کیلوبایت و …
  HEAVY_BPP: 1,                   // … بیش از ۱ بایت بر پیکسل ⇒ heavy_file (پیشنهادِ JPEG/WebP)
  DECODE_MAX_PIXELS: 16000000,    // بیشتر ⇒ quality_unmeasured (حافظهٔ روشنایی ≤ ۱۶ مگابایت)
  PNG_MAX_RAW: 32 * 1024 * 1024,  // سقفِ خروجیِ zlib برای PNG
  GRID_SIDE: 1024,                // شبکهٔ سنجش: میانگینِ جعبه‌ایِ k×k تا ضلعِ بلند ≤ ۱۰۲۴
  BLANK_STD: 4,                   // انحرافِ معیارِ روشنایی < ۴ ⇒ blank (یک‌رنگ؛ تاری/پس‌زمینه سنجیده نمی‌شود)
  BLUR_VAR_MIN: 100,              // واریانسِ لاپلاسینِ ۴-همسایه < ۱۰۰ ⇒ blurry
  DARK_P95: 70,                   // صدکِ ۹۵ ِ روشنایی < ۷۰ ⇒ too_dark
  BRIGHT_P05: 215,                // صدکِ ۵ ِ روشنایی > ۲۱۵ ⇒ too_bright
  BG_STD_MAX: 40,                 // انحرافِ معیارِ نوارِ لبه (۱/۱۶ ِ ضلعِ کوتاه) > ۴۰ ⇒ busy_background
  DHASH_EPS: 2,                   // بیتِ dHash فقط وقتی خانهٔ چپ دست‌کم ۲ سطح روشن‌تر است (نویزِ زمینهٔ صاف)
  DUP_HAMMING_MAX: 6,             // فاصلهٔ همینگ ≤ ۶ از ۶۴ ⇒ near_duplicate
});

const r2 = (x) => Math.round(x * 100) / 100;

/* ------------------------------------------------------------- PNG */

function paeth(a, b, c) {
  const p = a + b - c, pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
  return pa <= pb && pa <= pc ? a : pb <= pc ? b : c;
}

/** روشنایی (۰..۲۵۵) هر پیکسل؛ شفافی روی سفید. فقط ۸/۱۶ بیت، بی درهم‌سازی. */
function pngLuma(buf) {
  let i = 8, ihdr = null, plte = null, trns = null;
  const idat = [];
  while (i + 12 <= buf.length) {
    const len = buf.readUInt32BE(i);
    const type = buf.toString('latin1', i + 4, i + 8);
    const data = buf.subarray(i + 8, i + 8 + len);
    if (type === 'IHDR') ihdr = data;
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
  if (raw > LIMITS.PNG_MAX_RAW) return null;
  let data;
  try { data = zlib.inflateSync(Buffer.concat(idat), { maxOutputLength: raw }); } catch { return null; }
  if (data.length !== raw) return null;
  const out = new Uint8Array(w * h);
  let prev = new Uint8Array(stride), cur = new Uint8Array(stride);
  const hi = depth === 16 ? 2 : 1;            // ۱۶ بیت ⇒ بایتِ پرارزش
  for (let y = 0; y < h; y++) {
    const o = y * (stride + 1), ft = data[o];
    if (ft > 4) return null;
    for (let k = 0; k < stride; k++) {
      const x = data[o + 1 + k], a = k >= bpp ? cur[k - bpp] : 0, b = prev[k], c = k >= bpp ? prev[k - bpp] : 0;
      cur[k] = (ft === 0 ? x : ft === 1 ? x + a : ft === 2 ? x + b : ft === 3 ? x + ((a + b) >> 1) : x + paeth(a, b, c)) & 255;
    }
    for (let px = 0; px < w; px++) {
      const p = px * bpp;
      let r, g, bl, al = 255;
      if (ct === 3) {
        const e = cur[p];
        if (e * 3 + 2 >= plte.length) return null;
        r = plte[e * 3]; g = plte[e * 3 + 1]; bl = plte[e * 3 + 2];
        if (trns && e < trns.length) al = trns[e];
      } else if (ct === 0 || ct === 4) {
        r = g = bl = cur[p];
        if (ct === 4) al = cur[p + hi];
      } else {
        r = cur[p]; g = cur[p + hi]; bl = cur[p + 2 * hi];
        if (ct === 6) al = cur[p + 3 * hi];
      }
      const yv = 0.299 * r + 0.587 * g + 0.114 * bl;
      out[y * w + px] = Math.round((yv * al + 255 * (255 - al)) / 255);
    }
    const t = prev; prev = cur; cur = t;
  }
  return { w, h, luma: out };
}

/* ------------------------------------------------------------ JPEG */

//  ZZ[k] = جایگاهِ طبیعیِ ضریبِ k-ام در ترتیبِ زیگزاگ
const ZZ = (() => {
  const z = [];
  for (let s = 0; s < 15; s++) {
    const d = [];
    for (let r = 0; r <= s; r++) if (r < 8 && s - r < 8) d.push(r * 8 + (s - r));
    z.push(...(s % 2 === 0 ? d.reverse() : d));
  }
  return z;
})();
const COS = new Float64Array(64);   // COS[x*8+u] = c(u)/2 · cos((2x+1)uπ/16)
for (let x = 0; x < 8; x++) for (let u = 0; u < 8; u++) {
  COS[x * 8 + u] = ((u === 0 ? Math.SQRT1_2 : 1) / 2) * Math.cos(((2 * x + 1) * u * Math.PI) / 16);
}
//  جدولِ روشناییِ پایهٔ JPEG (پیوستِ K، کیفیتِ ۵۰) — مجموعش برای تخمینِ کیفیت (ترتیب بی‌اثر است)
const BASE_LUMA_SUM = [16, 11, 10, 16, 24, 40, 51, 61, 12, 12, 14, 19, 26, 58, 60, 55, 14, 13, 16, 24, 40, 57, 69, 56,
  14, 17, 22, 29, 51, 87, 80, 62, 18, 22, 37, 56, 68, 109, 103, 77, 24, 35, 55, 64, 81, 104, 113, 92,
  49, 64, 78, 87, 103, 121, 120, 101, 72, 92, 95, 98, 112, 100, 103, 99].reduce((a, b) => a + b, 0);

function jpegTables(buf) {
  const qt = [], ht = {};
  let frame = null, dri = 0, i = 2;
  while (i + 4 <= buf.length) {
    if (buf[i] !== 0xff) return null;
    const m = buf[i + 1];
    if (m === 0xff) { i++; continue; }
    if (m === 0xd8 || m === 0x01 || (m >= 0xd0 && m <= 0xd7)) { i += 2; continue; }
    const len = buf.readUInt16BE(i + 2), s = i + 4, e = i + 2 + len;
    if (len < 2 || e > buf.length) return null;
    if (m === 0xdb) {
      for (let p = s; p < e;) {
        const pq = buf[p] >> 4, id = buf[p] & 15, n = pq ? 128 : 64;
        if (id > 3 || p + 1 + n > e) return null;
        const t = new Int32Array(64);
        for (let k = 0; k < 64; k++) t[k] = pq ? buf.readUInt16BE(p + 1 + 2 * k) : buf[p + 1 + k];
        qt[id] = t; p += 1 + n;
      }
    } else if (m === 0xc4) {
      for (let p = s; p < e;) {
        if (p + 17 > e) return null;
        const cls = buf[p] >> 4, id = buf[p] & 15, counts = buf.subarray(p + 1, p + 17);
        const n = counts.reduce((a, b) => a + b, 0);
        if (cls > 1 || id > 3 || n > 256 || p + 17 + n > e) return null;
        const t = huffTable(counts, buf.subarray(p + 17, p + 17 + n));
        if (!t) return null;
        ht[cls * 4 + id] = t; p += 17 + n;
      }
    } else if (m === 0xc0 || m === 0xc1) {
      const nc = buf[s + 5], comps = [];
      if (buf[s] !== 8 || (nc !== 1 && nc !== 3) || s + 6 + nc * 3 > e) return null;
      for (let k = 0; k < nc; k++) {
        const o = s + 6 + k * 3;
        comps.push({ id: buf[o], h: buf[o + 1] >> 4, v: buf[o + 1] & 15, tq: buf[o + 2] & 3 });
      }
      if (comps.some((c) => c.h < 1 || c.h > 4 || c.v < 1 || c.v > 4)) return null;
      frame = { height: buf.readUInt16BE(s + 1), width: buf.readUInt16BE(s + 3), comps };
    } else if (m >= 0xc2 && m <= 0xcf && m !== 0xc4 && m !== 0xc8 && m !== 0xcc) {
      return { qt, unsupported: true };          // progressive/حسابی/بی‌اتلاف: فقط تخمینِ کیفیت
    } else if (m === 0xdd) {
      dri = buf.readUInt16BE(s);
    } else if (m === 0xda) {
      return { qt, ht, frame, dri, sos: s, data: e };
    }
    i = e;
  }
  return null;
}

/** کیفیتِ تخمینیِ IJG (۱..۱۰۰) از جدولِ کوانتیزهٔ کانالِ روشنایی؛ نبود ⇒ null */
function jpegQuality(buf) {
  let t;
  try { t = jpegTables(buf); } catch { return null; }
  const q = t && t.qt[t.frame ? t.frame.comps[0].tq : 0];
  if (!q) return null;
  const sum = q.reduce((a, b) => a + b, 0);
  const s = (100 * sum) / BASE_LUMA_SUM;
  return Math.max(1, Math.min(100, Math.round(s <= 100 ? (200 - s) / 2 : 5000 / s)));
}

function huffTable(counts, symbols) {
  const maxcode = new Int32Array(17).fill(-1), valptr = new Int32Array(17), mincode = new Int32Array(17);
  let code = 0, k = 0;
  for (let l = 1; l <= 16; l++) {
    valptr[l] = k; mincode[l] = code;
    code += counts[l - 1]; k += counts[l - 1];
    if (code > (1 << l)) return null;
    maxcode[l] = counts[l - 1] ? code - 1 : -1;
    code <<= 1;
  }
  return { maxcode, valptr, mincode, symbols };
}

/** JPEG ِ baseline ⇒ روشنایی (کانالِ Y، اندازهٔ کامل)؛ هر ناسازگاری ⇒ null */
function jpegLuma(buf) {
  const t = jpegTables(buf);
  if (!t || t.unsupported || !t.frame) return null;
  const { frame, qt, ht, dri } = t;
  const W = frame.width, H = frame.height;
  const hmax = Math.max(...frame.comps.map((c) => c.h)), vmax = Math.max(...frame.comps.map((c) => c.v));
  const luma0 = frame.comps[0];
  if (luma0.h !== hmax || luma0.v !== vmax) return null;
  const ns = buf[t.sos];
  if (ns < 1 || ns > frame.comps.length) return null;
  const scan = [];
  for (let k = 0; k < ns; k++) {
    const o = t.sos + 1 + k * 2;
    const c = frame.comps.find((x) => x.id === buf[o]);
    const dc = ht[buf[o + 1] >> 4], ac = ht[4 + (buf[o + 1] & 15)], q = c && qt[c.tq];
    if (!c || !dc || !ac || !q) return null;
    scan.push({ c, dc, ac, q, luma: c === luma0, pred: 0 });
  }
  const tail = t.sos + 1 + ns * 2;
  if (buf[tail] !== 0 || buf[tail + 1] !== 63 || buf[tail + 2] !== 0) return null;   // baseline: 0..63، بی تقریبِ پی‌درپی
  if (!scan.some((x) => x.luma)) return null;

  //  خوانندهٔ بیت با 0xFF00 و نشانگرِ RST
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
  const decode = (tb) => {
    let code = 0;
    for (let l = 1; l <= 16; l++) {
      code = (code << 1) | bit();
      if (tb.maxcode[l] >= 0 && code <= tb.maxcode[l]) return tb.symbols[tb.valptr[l] + code - tb.mincode[l]];
    }
    throw new Error('huffman');
  };

  const out = new Uint8Array(W * H);
  const F = new Float64Array(64), tmp = new Float64Array(64);
  const block = (sc, bx, by) => {
    F.fill(0);
    const sDc = decode(sc.dc);
    if (sDc > 11) throw new Error('dc');
    sc.pred += sDc ? receive(sDc) : 0;
    F[0] = sc.pred * sc.q[0];
    let onlyDc = true;
    for (let k = 1; k < 64;) {
      const rs = decode(sc.ac), r = rs >> 4, s = rs & 15;
      if (!s) { if (r !== 15) break; k += 16; continue; }
      k += r;
      if (k > 63) throw new Error('ac');
      F[ZZ[k]] = receive(s) * sc.q[k]; onlyDc = false; k++;
    }
    if (!sc.luma) return;
    const x0 = bx * 8, y0 = by * 8;
    if (x0 >= W || y0 >= H) return;
    if (onlyDc) {
      const v = Math.max(0, Math.min(255, Math.round(F[0] / 8 + 128)));
      for (let y = y0; y < Math.min(y0 + 8, H); y++) out.fill(v, y * W + x0, y * W + Math.min(x0 + 8, W));
      return;
    }
    for (let v = 0; v < 8; v++) for (let x = 0; x < 8; x++) {
      let a = 0;
      for (let u = 0; u < 8; u++) a += COS[x * 8 + u] * F[v * 8 + u];
      tmp[v * 8 + x] = a;
    }
    for (let y = 0; y < 8 && y0 + y < H; y++) for (let x = 0; x < 8 && x0 + x < W; x++) {
      let a = 0;
      for (let v = 0; v < 8; v++) a += COS[y * 8 + v] * tmp[v * 8 + x];
      out[(y0 + y) * W + x0 + x] = Math.max(0, Math.min(255, Math.round(a + 128)));
    }
  };

  let units, unitsX, unit;
  if (ns === 1) {                                   // بی درهم‌آمیزی: هر واحد یک بلوک
    const c = scan[0].c;
    unitsX = Math.ceil(Math.ceil((W * c.h) / hmax) / 8);
    units = unitsX * Math.ceil(Math.ceil((H * c.v) / vmax) / 8);
    unit = (n) => block(scan[0], n % unitsX, Math.floor(n / unitsX));
  } else {
    unitsX = Math.ceil(W / (8 * hmax));
    units = unitsX * Math.ceil(H / (8 * vmax));
    unit = (n) => {
      const mx = n % unitsX, my = Math.floor(n / unitsX);
      for (const sc of scan) {
        for (let by = 0; by < sc.c.v; by++) for (let bx = 0; bx < sc.c.h; bx++) block(sc, mx * sc.c.h + bx, my * sc.c.v + by);
      }
    };
  }
  try {
    for (let n = 0; n < units; n++) {
      if (dri && n && n % dri === 0) {               // RST: بیت‌های مانده دور، پیش‌بینِ DC صفر
        cnt = 0;
        while (buf[pos] === 0xff && buf[pos + 1] === 0xff) pos++;
        if (buf[pos] !== 0xff || buf[pos + 1] < 0xd0 || buf[pos + 1] > 0xd7) return null;
        pos += 2;
        for (const sc of scan) sc.pred = 0;
      }
      unit(n);
      if (overrun) return null;                      // داده پیش از پایان تمام شد ⇒ خراب/ناقص
    }
  } catch { return null; }
  return { w: W, h: H, luma: out };
}

/* ----------------------------------------------------------- سنجش */

/** میانگینِ جعبه‌ایِ k×k تا ضلعِ بلند ≤ GRID_SIDE؛ کم‌تر از ۹×۸ ⇒ null */
function grid({ w, h, luma }) {
  const k = Math.max(1, Math.ceil(Math.max(w, h) / LIMITS.GRID_SIDE));
  const gw = Math.floor(w / k), gh = Math.floor(h / k);
  if (gw < 9 || gh < 8) return null;
  const g = new Float32Array(gw * gh);
  for (let y = 0; y < gh * k; y++) {
    const row = y * w, gr = Math.floor(y / k) * gw;
    for (let x = 0; x < gw * k; x++) g[gr + Math.floor(x / k)] += luma[row + x];
  }
  const kk = k * k;
  for (let n = 0; n < g.length; n++) g[n] /= kk;
  return { gw, gh, g, k };
}

function measure({ gw, gh, g, k }) {
  const n = g.length, hist = new Uint32Array(256);
  let sum = 0, sq = 0;
  for (let i = 0; i < n; i++) { const v = g[i]; sum += v; sq += v * v; hist[Math.round(v)]++; }
  const mean = sum / n;
  const pct = (p) => {
    const rank = Math.ceil(p * n);
    let c = 0;
    for (let v = 0; v < 256; v++) { c += hist[v]; if (c >= rank) return v; }
    return 255;
  };
  //  لاپلاسینِ ۴-همسایه روی درونِ شبکه
  let ls = 0, lq = 0, ln = 0;
  for (let y = 1; y < gh - 1; y++) for (let x = 1; x < gw - 1; x++) {
    const i = y * gw + x;
    const l = g[i - 1] + g[i + 1] + g[i - gw] + g[i + gw] - 4 * g[i];
    ls += l; lq += l * l; ln++;
  }
  //  نوارِ لبه
  const band = Math.max(1, Math.floor(Math.min(gw, gh) / 16));
  let bs = 0, bq = 0, bn = 0;
  for (let y = 0; y < gh; y++) for (let x = 0; x < gw; x++) {
    if (x >= band && x < gw - band && y >= band && y < gh - band) continue;
    const v = g[y * gw + x]; bs += v; bq += v * v; bn++;
  }
  //  dHash: ۹×۸ خانه، بیت = خانهٔ چپ > راست + DHASH_EPS
  const cell = [];
  for (let r = 0; r < 8; r++) for (let c = 0; c < 9; c++) {
    const x0 = Math.floor((c * gw) / 9), x1 = Math.floor(((c + 1) * gw) / 9);
    const y0 = Math.floor((r * gh) / 8), y1 = Math.floor(((r + 1) * gh) / 8);
    let s = 0;
    for (let y = y0; y < y1; y++) for (let x = x0; x < x1; x++) s += g[y * gw + x];
    cell.push(s / ((x1 - x0) * (y1 - y0)));
  }
  let hex = '';
  for (let r = 0; r < 8; r++) {
    let byte = 0;
    for (let c = 0; c < 8; c++) byte = (byte << 1) | (cell[r * 9 + c] > cell[r * 9 + c + 1] + LIMITS.DHASH_EPS ? 1 : 0);
    hex += byte.toString(16).padStart(2, '0');
  }
  const std = Math.sqrt(Math.max(0, sq / n - mean * mean));
  return {
    grid: [gw, gh], scale: k,
    luma_mean: r2(mean), luma_std: r2(std), p05: pct(0.05), p95: pct(0.95),
    sharpness: r2(Math.max(0, lq / ln - (ls / ln) ** 2)),
    background_std: r2(Math.sqrt(Math.max(0, bq / bn - (bs / bn) ** 2))),
    dhash: std < LIMITS.BLANK_STD ? '' : hex,
  };
}

/** نشان‌ها به ترتیبِ ثابت؛ مقایسه‌ها اکید (مرزِ دقیق نشان نمی‌گیرد). */
function flagsFrom(m) {
  const L = LIMITS, f = [];
  if (m.aspect > L.ASPECT_MAX) f.push('aspect_extreme');
  if (m.jpeg_quality != null && m.jpeg_quality < L.JPEG_QUALITY_MIN) f.push('over_compressed');
  if (m.bytes > L.HEAVY_BYTES && m.bytes_per_pixel > L.HEAVY_BPP) f.push('heavy_file');
  if (!m.measured) { f.push('quality_unmeasured'); return f; }
  const blank = m.luma_std < L.BLANK_STD;
  if (blank) f.push('blank');
  if (m.p95 < L.DARK_P95) f.push('too_dark');
  if (m.p05 > L.BRIGHT_P05) f.push('too_bright');
  if (!blank && m.sharpness < L.BLUR_VAR_MIN) f.push('blurry');
  if (!blank && m.background_std > L.BG_STD_MAX) f.push('busy_background');
  return f;
}

function analyze(buf, info) {
  const W = info.width, H = info.height;
  const m = {
    width: W, height: H, bytes: buf.length,
    aspect: Math.round((Math.max(W, H) / Math.min(W, H)) * 10000) / 10000,   // ۴ رقم: (3h+1)/h با h ≤ ۳۳۳۴ هنوز > ۳
    bytes_per_pixel: Math.round((buf.length / (W * H)) * 1000) / 1000,
    jpeg_quality: info.mime === 'image/jpeg' ? jpegQuality(buf) : null,
    measured: false, reason: '',
  };
  const d = decode(buf, info);
  if (d.gr) Object.assign(m, measure(d.gr), { measured: true });
  else m.reason = d.reason;
  const dhash = m.dhash || '';
  delete m.dhash;
  return { metrics: m, flags: flagsFrom(m), dhash };
}

/** رمزگشایی تا شبکهٔ سنجش ⇒ { gr } یا { reason } (همان سقف‌ها و همان دلیل‌های `analyze`) */
function decode(buf, info) {
  const W = info.width, H = info.height;
  if (W * H > LIMITS.DECODE_MAX_PIXELS) return { reason: 'too_many_pixels' };
  if (info.mime === 'image/webp') return { reason: 'format' };
  let px = null;
  try { px = info.mime === 'image/jpeg' ? jpegLuma(buf) : info.mime === 'image/png' ? pngLuma(buf) : null; } catch { px = null; }
  if (px && (px.w !== W || px.h !== H)) px = null;
  if (!px) return { reason: 'undecodable' };
  const gr = grid(px);
  return gr ? { gr } : { reason: 'too_thin' };
}

/**
 * AI-11 (‎lib/visual-search.js‎): همان رمزگشایی و همان dHash ِ `analyze`، و هیستوگرامِ ۸ خانه‌ایِ روشنایی
 * روی همان شبکه (درصدِ گرد، ۱۶ رقمِ hex). رمزگشا فقط کانالِ Y دارد ⇒ رنگ نه. سنجیدنی نیست یا یک‌رنگ ⇒ null.
 */
function fingerprint(buf, info) {
  const d = decode(buf, info);
  if (!d.gr) return null;
  const dhash = measure(d.gr).dhash;
  if (!dhash) return null;
  const g = d.gr.g, bins = new Array(8).fill(0);
  for (let i = 0; i < g.length; i++) bins[Math.min(7, Math.floor(g[i] / 32))]++;
  return { dhash, hist: bins.map((c) => Math.round((c * 100) / g.length).toString(16).padStart(2, '0')).join('') };
}

function hamming(a, b) {
  let d = 0;
  for (let i = 0; i < 16; i += 4) {
    let x = (parseInt(a.slice(i, i + 4), 16) ^ parseInt(b.slice(i, i + 4), 16)) >>> 0;
    while (x) { d += x & 1; x >>>= 1; }
  }
  return d;
}
const HEX16 = /^[0-9a-f]{16}$/;
const isNearDuplicate = (a, b) => HEX16.test(a) && HEX16.test(b) && hamming(a, b) <= LIMITS.DUP_HAMMING_MAX;

/**
 * استودیوی عکس (‎lib/image-studio.js‎): همان شبکه و همان سنجه‌های `analyze` روی روشناییِ آماده
 * ({ w, h, luma }) ⇒ همان `sharpness`/`p05`/`p95`/`background_std` ِ IMG-15، یا null (کم‌تر از ۹×۸).
 */
function lumaMetrics(px) {
  const gr = grid(px);
  if (!gr) return null;
  const m = measure(gr);
  delete m.dhash;
  return m;
}

module.exports = {
  analyze, fingerprint, flagsFrom, hamming, isNearDuplicate, jpegQuality, pngLuma, jpegLuma, lumaMetrics, LIMITS,
  //  رمزگشای رنگیِ ‎lib/image-codec.js‎ همان جدول‌خوان و همان ضریب‌ها را به کار می‌برد (یک پیاده‌سازی)
  jpegTables, ZZ, COS,
};
