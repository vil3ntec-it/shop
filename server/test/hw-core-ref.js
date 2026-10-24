/* ==========================================================
   هستهٔ دستگاه‌ها (HW) — پیاده‌سازیِ مرجعِ جاوااسکریپت، فقط برای آزمون
   ----------------------------------------------------------
   همتای مستقلِ `core/hw/*.kt` (ESC/POS، EAN-13، Code128، برچسب، بارکدِ
   ترازو و پیش‌نمایش). در هیچ صفحه‌ای بار نمی‌شود — دستورِ صاحب مخزن:
   سامانهٔ دکان فقط برنامه‌های اندروید و کامپیوتر است.

   کارش «پیشگو»ست: بردارهای `core/test-vectors/hw-v1.json` را دو
   پیاده‌سازیِ جدا (کاتلین و این) باید یکسان بدهند، و بارکدهای این را
   رمزگشای مستقلِ zxing در `hw-core.test.js` می‌خواند. هر دو با هم یعنی
   بایت‌های کاتلین هم درست‌اند.

   • تصویرِ یک‌بیتی: { width, height, bits: Uint8Array } — هر سطر
     ceil(width/8) بایت، بیتِ پرارزش = نقطهٔ چپ‌تر، ۱ = سیاه.
   ========================================================== */
(function (root, factory) {
  'use strict';
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  if (root) root.TohidHw = api;
})(typeof window !== 'undefined' ? window : null, function () {
  'use strict';

  const WIDTH_58MM = 384;
  const WIDTH_80MM = 576;
  const BAND_ROWS = 128;

  /* ============================ تصویرِ یک‌بیتی ============================ */

  function mono(width, height) {
    if (!(width > 0 && height > 0)) throw new Error('اندازهٔ تصویر باید مثبت باشد');
    return { width, height, bits: new Uint8Array(((width + 7) >> 3) * height) };
  }
  const rowBytes = (img) => (img.width + 7) >> 3;
  function get(img, x, y) {
    if (x < 0 || y < 0 || x >= img.width || y >= img.height) return false;
    return (img.bits[y * rowBytes(img) + (x >> 3)] & (0x80 >> (x & 7))) !== 0;
  }
  function set(img, x, y, black) {
    if (x < 0 || y < 0 || x >= img.width || y >= img.height) return;
    const i = y * rowBytes(img) + (x >> 3), m = 0x80 >> (x & 7);
    if (black) img.bits[i] |= m; else img.bits[i] &= ~m;
  }
  function fill(img, x0, y0, w, h) {
    for (let y = y0; y < y0 + h; y++) for (let x = x0; x < x0 + w; x++) set(img, x, y, true);
  }
  function blit(dst, src, x0, y0) {
    for (let y = 0; y < src.height; y++) for (let x = 0; x < src.width; x++) if (get(src, x, y)) set(dst, x0 + x, y0 + y, true);
  }

  /** همان قاعدهٔ `MonoImage.isBlack` و `toEscPos`ِ پیشین */
  function isBlack(r, g, b, a) {
    if (a < 128) return false;
    return (0.299 * r + 0.587 * g + 0.114 * b) < 128;
  }

  /** از RGBAِ کانواس (getImageData().data) */
  function fromRgba(rgba, width, height, paperWidth) {
    const pw = paperWidth || width;
    const out = mono(pw, height);
    const w = Math.min(width, pw);
    for (let y = 0; y < height; y++) for (let x = 0; x < w; x++) {
      const i = (y * width + x) * 4;
      if (isBlack(rgba[i], rgba[i + 1], rgba[i + 2], rgba[i + 3])) set(out, x, y, true);
    }
    return out;
  }

  function fromCanvas(canvas, paperWidth) {
    const data = canvas.getContext('2d').getImageData(0, 0, canvas.width, canvas.height).data;
    return fromRgba(data, canvas.width, canvas.height, paperWidth);
  }

  /** برای پیش‌نمایش: تصویرِ یک‌بیتی ⇐ RGBA (سیاه/سفید) */
  function toRgba(img) {
    const out = new Uint8ClampedArray(img.width * img.height * 4);
    for (let y = 0; y < img.height; y++) for (let x = 0; x < img.width; x++) {
      const v = get(img, x, y) ? 0 : 255, i = (y * img.width + x) * 4;
      out[i] = v; out[i + 1] = v; out[i + 2] = v; out[i + 3] = 255;
    }
    return out;
  }

  /* ============================ بایت ============================ */

  function concat(parts) {
    let n = 0; parts.forEach((p) => { n += p.length; });
    const out = new Uint8Array(n); let at = 0;
    parts.forEach((p) => { out.set(p, at); at += p.length; });
    return out;
  }
  const u8 = (a) => new Uint8Array(a);

  /* ============================ ESC/POS ============================ */

  const INIT = u8([0x1B, 0x40]);
  const PAPER_STATUS = u8([0x10, 0x04, 0x04]);

  /** پروفایلِ چاپگر — پیش‌فرض‌ها رفتارِ امروزند (همان `PrinterProfile`) */
  function profile(p) {
    const o = p || {};
    return {
      paperDots: o.paperDots >= WIDTH_80MM ? WIDTH_80MM : WIDTH_58MM,
      mode: o.mode === 'column' ? 'column' : 'raster',
      cut: o.cut === 'full' || o.cut === 'none' ? o.cut : 'partial',
      feedLines: Number.isInteger(o.feedLines) ? o.feedLines : 3,
      drawer: o.drawer === true,
      drawerPin: o.drawerPin === 1 ? 1 : 0,
      logo: o.logo === true,
    };
  }

  function raster(img, bandRows) {
    const band = bandRows || BAND_ROWS, rb = rowBytes(img), parts = [];
    for (let top = 0; top < img.height; top += band) {
      const rows = Math.min(band, img.height - top);
      parts.push(u8([0x1D, 0x76, 0x30, 0x00, rb & 0xFF, (rb >> 8) & 0xFF, rows & 0xFF, (rows >> 8) & 0xFF]));
      parts.push(img.bits.subarray(top * rb, (top + rows) * rb));
    }
    return concat(parts);
  }

  function column(img) {
    const w = img.width, parts = [u8([0x1B, 0x33, 24])];
    for (let top = 0; top < img.height; top += 24) {
      parts.push(u8([0x1B, 0x2A, 33, w & 0xFF, (w >> 8) & 0xFF]));
      const band = new Uint8Array(w * 3);
      for (let x = 0; x < w; x++) for (let k = 0; k < 3; k++) {
        let b = 0;
        for (let bit = 0; bit < 8; bit++) if (get(img, x, top + k * 8 + bit)) b |= (0x80 >> bit);
        band[x * 3 + k] = b;
      }
      parts.push(band, u8([0x0A]));
    }
    parts.push(u8([0x1B, 0x32]));
    return concat(parts);
  }

  const units = (ms) => Math.max(1, Math.min(255, Math.floor(ms / 2)));
  function drawerKick(pin, onMs, offMs) {
    return u8([0x1B, 0x70, pin === 1 ? 1 : 0, units(onMs == null ? 50 : onMs), units(offMs == null ? 500 : offMs)]);
  }
  function feed(n) { return new Uint8Array(Math.max(0, Math.min(20, n))).fill(0x0A); }
  function cut(mode) {
    if (mode === 'none') return u8([]);
    return u8([0x1D, 0x56, mode === 'full' ? 0x41 : 0x42, 0x00]);
  }

  /** `ESC @` ← (کشو) ← تصویر ← سطرهای خالی ← برش — همان `EscPos.document` */
  function documentBytes(img, prof, openDrawer) {
    const p = profile(prof);
    const parts = [INIT];
    if (openDrawer) parts.push(drawerKick(p.drawerPin));
    parts.push(p.mode === 'column' ? column(img) : raster(img));
    parts.push(feed(p.feedLines), cut(p.cut));
    return concat(parts);
  }
  function drawerOnly(prof) { return concat([INIT, drawerKick(profile(prof).drawerPin)]); }

  function parsePaperStatus(b) {
    const v = b & 0xFF;
    if ((v & 0x93) !== 0x12) return null;
    return { nearEnd: (v & 0x0C) !== 0, out: (v & 0x60) !== 0 };
  }

  /* ============================ پیش‌نمایش (چاپِ خیالی) ============================ */

  /** همان `EscPosPreview.decode`: بایت‌ها ⇐ کاغذ + رویدادها */
  function decode(bytes, paperDots) {
    const rb = (paperDots + 7) >> 3, rows = [], events = [], unknown = [];
    const DEFAULT_LINE = 30;
    let spacing = DEFAULT_LINE, pending = null, i = 0;
    const blank = (n) => { for (let k = 0; k < n; k++) rows.push(new Uint8Array(rb)); };
    while (i < bytes.length) {
      const b = bytes[i], n1 = bytes[i + 1];
      if (b === 0x1B && n1 === 0x40) { events.push('init'); spacing = DEFAULT_LINE; i += 2; }
      else if (b === 0x1B && n1 === 0x33 && i + 2 < bytes.length) { spacing = bytes[i + 2]; events.push('spacing ' + bytes[i + 2]); i += 3; }
      else if (b === 0x1B && n1 === 0x32) { spacing = DEFAULT_LINE; events.push('spacing default'); i += 2; }
      else if (b === 0x1B && n1 === 0x70 && i + 4 < bytes.length) {
        events.push('drawer pin' + (bytes[i + 2] === 1 ? 5 : 2) + ' ' + (bytes[i + 3] * 2) + '/' + (bytes[i + 4] * 2) + 'ms'); i += 5;
      } else if (b === 0x1B && n1 === 0x2A && i + 4 < bytes.length) {
        const m = bytes[i + 2], n = bytes[i + 3] | (bytes[i + 4] << 8), tall = (m === 32 || m === 33) ? 3 : 1, start = i + 5;
        if (start + n * tall > bytes.length) { unknown.push('ESC * کوتاه'); break; }
        const band = []; for (let k = 0; k < tall * 8; k++) band.push(new Uint8Array(rb));
        for (let x = 0; x < Math.min(n, paperDots); x++) for (let k = 0; k < tall; k++) {
          const v = bytes[start + x * tall + k];
          for (let bit = 0; bit < 8; bit++) if (v & (0x80 >> bit)) band[k * 8 + bit][x >> 3] |= (0x80 >> (x & 7));
        }
        pending = band; events.push('column ' + n + 'x' + (tall * 8)); i = start + n * tall;
      } else if (b === 0x1D && n1 === 0x76 && bytes[i + 2] === 0x30 && i + 7 < bytes.length) {
        const xb = bytes[i + 4] | (bytes[i + 5] << 8), h = bytes[i + 6] | (bytes[i + 7] << 8), start = i + 8;
        if (start + xb * h > bytes.length) { unknown.push('GS v 0 کوتاه'); break; }
        for (let r = 0; r < h; r++) {
          const row = new Uint8Array(rb);
          row.set(bytes.subarray(start + r * xb, start + r * xb + Math.min(xb, rb)));
          rows.push(row);
        }
        events.push('raster ' + (xb * 8) + 'x' + h); i = start + xb * h;
      } else if (b === 0x1D && n1 === 0x56 && i + 2 < bytes.length) {
        const m = bytes[i + 2];
        events.push('cut ' + ((m === 0x42 || m === 0x01 || m === 0x31) ? 'partial' : 'full'));
        i += (m === 0x41 || m === 0x42) ? 4 : 3;
      } else if (b === 0x10 && n1 === 0x04 && i + 2 < bytes.length) { events.push('status ' + bytes[i + 2]); i += 3; }
      else if (b === 0x0A) {
        if (pending) { pending.forEach((r) => rows.push(r)); blank(Math.max(0, spacing - pending.length)); pending = null; }
        else blank(spacing);
        events.push('feed'); i += 1;
      } else { unknown.push('0x' + b.toString(16).padStart(2, '0') + '@' + i); i += 1; }
    }
    if (pending) pending.forEach((r) => rows.push(r));
    let paper = null;
    if (rows.length) {
      paper = mono(paperDots, rows.length);
      rows.forEach((r, k) => paper.bits.set(r, k * rb));
    }
    return {
      paper, events, unknown,
      cuts: events.filter((e) => e.startsWith('cut')).length,
      drawerKicks: events.filter((e) => e.startsWith('drawer')).length,
    };
  }

  /** پیش‌نمایش روی کانواس — سیاه/سفید، همان چیزی که چاپگر می‌زند */
  function paintPreview(canvas, img) {
    canvas.width = img.width; canvas.height = img.height;
    const ctx = canvas.getContext('2d');
    ctx.putImageData(new ImageData(toRgba(img), img.width, img.height), 0, 0);
    return canvas;
  }

  /* ============================ EAN-13 ============================ */

  const EAN_L = ['0001101', '0011001', '0010011', '0111101', '0100011', '0110001', '0101111', '0111011', '0110111', '0001011'];
  const EAN_G = ['0100111', '0110011', '0011011', '0100001', '0011101', '0111001', '0000101', '0010001', '0001001', '0010111'];
  const EAN_R = ['1110010', '1100110', '1101100', '1000010', '1011100', '1001110', '1010000', '1000100', '1001000', '1110100'];
  const EAN_PARITY = ['LLLLLL', 'LLGLGG', 'LLGGLG', 'LLGGGL', 'LGLLGG', 'LGGLLG', 'LGGGLG', 'LGLGLG', 'LGLGGL', 'LGGLGL'];

  function ean13Check(first12) {
    if (!/^\d{12}$/.test(first12)) throw new Error('EAN-13 دوازده رقم می‌خواهد');
    let sum = 0;
    for (let i = 0; i < 12; i++) sum += (first12.charCodeAt(i) - 48) * (i % 2 === 0 ? 1 : 3);
    return (10 - sum % 10) % 10;
  }
  function ean13Valid(code) {
    return /^\d{13}$/.test(code) && ean13Check(code.slice(0, 12)) === code.charCodeAt(12) - 48;
  }
  function ean13Modules(code) {
    if (!ean13Valid(code)) throw new Error('EAN-13 نادرست: ' + code);
    const parity = EAN_PARITY[code.charCodeAt(0) - 48];
    let s = '101';
    for (let i = 1; i <= 6; i++) { const d = code.charCodeAt(i) - 48; s += parity[i - 1] === 'L' ? EAN_L[d] : EAN_G[d]; }
    s += '01010';
    for (let i = 7; i <= 12; i++) s += EAN_R[code.charCodeAt(i) - 48];
    return s + '101';
  }

  /* ============================ Code128 ============================ */

  const C128 = [
    '212222', '222122', '222221', '121223', '121322', '131222', '122213', '122312', '132212', '221213',
    '221312', '231212', '112232', '122132', '122231', '113222', '123122', '123221', '223211', '221132',
    '221231', '213212', '223112', '312131', '311222', '321122', '321221', '312212', '322112', '322211',
    '212123', '212321', '232121', '111323', '131123', '131321', '112313', '132113', '132311', '211313',
    '231113', '231311', '112133', '112331', '132131', '113123', '113321', '133121', '313121', '211331',
    '231131', '213113', '213311', '213131', '311123', '311321', '331121', '312113', '312311', '332111',
    '314111', '221411', '431111', '111224', '111422', '121124', '121421', '141122', '141221', '112214',
    '112412', '122114', '122411', '142112', '142211', '241211', '221114', '413111', '241112', '134111',
    '111242', '121142', '121241', '114212', '124112', '124211', '411212', '421112', '421211', '212141',
    '214121', '412121', '111143', '111341', '131141', '114113', '114311', '411113', '411311', '113141',
    '114131', '311141', '411131', '211412', '211214', '211232', '2331112',
  ];
  const isDigit = (c) => c >= 48 && c <= 57;
  function digitsAt(s, i) { let n = 0; while (i + n < s.length && isDigit(s.charCodeAt(i + n))) n++; return n; }

  /** همان `Code128.symbols`: دستهٔ C برای رقم‌های پشتِ‌سرِهم، بقیه B */
  function code128Symbols(text) {
    if (!text) throw new Error('بارکدِ خالی');
    for (const ch of text) { const c = ch.codePointAt(0); if (c < 32 || c > 126) throw new Error('نویسهٔ «' + ch + '» در Code128 نمی‌گنجد'); }
    const n = text.length, out = [], lead = digitsAt(text, 0);
    let setC = (lead === n && n >= 2 && n % 2 === 0) || lead >= 4;
    out.push(setC ? 105 : 104);
    let i = 0;
    while (i < n) {
      if (setC) {
        if (digitsAt(text, i) >= 2) { out.push((text.charCodeAt(i) - 48) * 10 + (text.charCodeAt(i + 1) - 48)); i += 2; }
        else { out.push(100); setC = false; }
      } else {
        const run = digitsAt(text, i);
        if (run >= 6 || (run >= 4 && i + run === n)) {
          if (run % 2 === 1) { out.push(text.charCodeAt(i) - 32); i += 1; }
          out.push(99); setC = true;
        } else { out.push(text.charCodeAt(i) - 32); i += 1; }
      }
    }
    let sum = out[0];
    for (let k = 1; k < out.length; k++) sum += out[k] * k;
    out.push(sum % 103, 106);
    return out;
  }
  function code128Modules(text) {
    let s = '';
    code128Symbols(text).forEach((sym) => {
      const w = C128[sym];
      for (let k = 0; k < w.length; k++) s += (k % 2 === 0 ? '1' : '0').repeat(w.charCodeAt(k) - 48);
    });
    return s;
  }

  /** همان `Barcodes.forProduct`: EAN-13ِ درست ⇐ EAN-13؛ بقیه Code128؛ نشد ⇐ null */
  function barcodeForProduct(code) {
    const c = String(code || '').trim();
    if (!c) return null;
    if (ean13Valid(c)) return { kind: 'EAN13', text: c, modules: ean13Modules(c) };
    try { return { kind: 'CODE128', text: c, modules: code128Modules(c) }; } catch (e) { return null; }
  }

  function renderBarcode(modules, moduleDots, heightDots, quiet) {
    const q = quiet == null ? 10 : quiet;
    const img = mono((modules.length + q * 2) * moduleDots, heightDots);
    for (let m = 0; m < modules.length; m++) if (modules[m] === '1') fill(img, (q + m) * moduleDots, 0, moduleDots, heightDots);
    return img;
  }

  /* ============================ برچسب ============================ */

  const DOTS_PER_MM = 8;
  const SIZE_40x30 = { widthMm: 40, heightMm: 30, gapMm: 2 };
  const A4 = { PAGE_W: 2100, PAGE_H: 2970, COLS: 3, ROWS: 8, MARGIN_X: 0, MARGIN_Y: 45, CELL_W: 700, CELL_H: 360 };
  function a4Cell(index) {
    const i = index % (A4.COLS * A4.ROWS), row = Math.floor(i / A4.COLS), colRtl = A4.COLS - 1 - (i % A4.COLS);
    return [A4.MARGIN_X + colRtl * A4.CELL_W, A4.MARGIN_Y + row * A4.CELL_H];
  }
  function moduleDots(modules, maxDots, quiet) {
    const m = Math.floor(maxDots / (modules + (quiet == null ? 10 : quiet) * 2));
    return m >= 2 ? Math.min(m, 4) : 0;
  }
  const ascii = (s) => { const o = new Uint8Array(s.length); for (let i = 0; i < s.length; i++) o[i] = s.charCodeAt(i); return o; };
  function tspl(img, size, copies) {
    const sz = size || SIZE_40x30, rb = rowBytes(img);
    const head = 'SIZE ' + sz.widthMm + ' mm,' + sz.heightMm + ' mm\r\nGAP ' + (sz.gapMm == null ? 2 : sz.gapMm) + ' mm,0 mm\r\n' +
      'DIRECTION 1\r\nCLS\r\nBITMAP 0,0,' + rb + ',' + img.height + ',0,';
    const data = img.bits.map((b) => b ^ 0xFF);
    const pad = rb * 8 - img.width;
    if (pad > 0) { const mask = (1 << pad) - 1; for (let r = 0; r < img.height; r++) data[r * rb + rb - 1] |= mask; }
    const n = Math.max(1, Math.min(999, copies || 1));
    return concat([ascii(head), data, ascii('\r\nPRINT ' + n + ',1\r\n')]);
  }
  function labelEscPos(img, copies) {
    const one = concat([raster(img), feed(1)]), parts = [INIT];
    for (let k = 0; k < Math.max(1, Math.min(999, copies || 1)); k++) parts.push(one);
    return concat(parts);
  }

  /* ============================ بارکدِ ترازو ============================ */

  function ruleValid(r) {
    return !!r && /^2\d{0,2}$/.test(r.prefix) && r.itemDigits >= 1 && r.valueDigits >= 1 &&
      r.prefix.length + r.itemDigits + r.valueDigits === 12 && r.decimals >= 0 && r.decimals <= 4;
  }
  function rule(o) {
    return { prefix: String(o.prefix), itemDigits: o.itemDigits || 5, valueDigits: o.valueDigits || 5,
      kind: o.kind === 'price' ? 'price' : 'weight', decimals: o.decimals == null ? 3 : o.decimals };
  }
  function standardRules() { const out = []; for (let p = 20; p <= 29; p++) out.push(rule({ prefix: String(p) })); return out; }

  /** همان `WeightBarcode.parse`: { type:'hit'|'badCheck'|'notScale', … } */
  function parseWeight(code, rules) {
    const c = String(code || '').trim();
    if (!/^\d{13}$/.test(c)) return { type: 'notScale' };
    const r = (rules || []).map(rule).filter(ruleValid).find((x) => c.startsWith(x.prefix));
    if (!r) return { type: 'notScale' };
    if (!ean13Valid(c)) return { type: 'badCheck', code: c };
    const p = r.prefix.length;
    const item = c.slice(p, p + r.itemDigits);
    const raw = parseInt(c.slice(p + r.itemDigits, p + r.itemDigits + r.valueDigits), 10);
    return { type: 'hit', rule: r, itemCode: item, value: raw / Math.pow(10, r.decimals), raw };
  }
  function buildWeight(r0, itemCode, raw) {
    const r = rule(r0);
    if (!ruleValid(r)) throw new Error('قاعدهٔ نادرست');
    const body = r.prefix + String(itemCode).padStart(r.itemDigits, '0') + String(raw).padStart(r.valueDigits, '0');
    if (body.length !== 12) throw new Error('کد یا مقدار بلند است');
    return body + ean13Check(body);
  }

  return {
    WIDTH_58MM, WIDTH_80MM, BAND_ROWS,
    mono, get, set, fill, blit, isBlack, fromRgba, fromCanvas, toRgba, rowBytes,
    INIT, PAPER_STATUS, profile, raster, column, drawerKick, feed, cut,
    document: documentBytes, drawerOnly, parsePaperStatus,
    decode, paintPreview,
    ean13Check, ean13Valid, ean13Modules, code128Symbols, code128Modules, barcodeForProduct, renderBarcode,
    DOTS_PER_MM, SIZE_40x30, A4, a4Cell, moduleDots, tspl, labelEscPos,
    standardRules, parseWeight, buildWeight,
  };
});
