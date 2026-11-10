/* ==========================================================
   فروشگاه من | PDF ِ مستقیمِ گزارش در وب (RPT-11)
   ----------------------------------------------------------
   دو تکه:
     • `write` — دوقلوی بایت‌به‌بایتِ `core/pdf/ImagePdf.kt`: صفحه‌های تصویریِ یک‌بیتی (همان قالبِ `MonoImage`:
       هر سطر ⌈w/8⌉ بایت، بیتِ پرارزش = نقطهٔ چپ‌تر، ۱ = سیاه) با `RunLengthDecode`، عنوانِ UTF-16 و xref ِ دقیق.
       بردار: `core/test-vectors/image-pdf-v1.json` (JS: `report-pdf-vectors.test.js`، Kotlin: `ImagePdfVectorsTest`).
     • `render` — فقط مرورگر: جدول روی canvas با قلمِ خودِ صفحه و راست‌به‌چپ (شکل‌دهیِ حروفِ دری/پشتو کارِ مرورگر است)،
       A4 در ۱۵۰ نقطه، همان چیدمانِ `TablePrintable` ِ برنامهٔ کامپیوتر (حاشیهٔ ۲۸ پوینت، قلمِ ۸٫۵، سرستونِ پررنگ و سایه‌دار،
       ستون‌های هم‌پهنا، شکستنِ خط)، سپس آستانهٔ ۱۶۰ ⇒ سیاه‌وسفید. سقفِ ۵۰۰ صفحه: بلندتر ⇒ خطا، نه PDF ِ ناقص.
   خالص و بی شبکه؛ چیزی در دفتر نوشته نمی‌شود. بارکننده: `report-ui.js` با نخستین «PDF» (index.html ِ یخ‌زده دست نخورد).
   ========================================================== */
(function (root, factory) {
  'use strict';
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.TohidReportPdf = factory();
})(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  var MAX_PAGES = 500;
  var DPI = 150;

  /* ------------------------------ ImagePdf ------------------------------ */

  function ascii(s) {
    var out = new Uint8Array(s.length);
    for (var i = 0; i < s.length; i++) out[i] = s.charCodeAt(i) & 0xFF;
    return out;
  }

  function Out() { this.parts = []; this.size = 0; }
  Out.prototype.bytes = function (b) { this.parts.push(b); this.size += b.length; };
  Out.prototype.text = function (s) { this.bytes(ascii(s)); };
  Out.prototype.done = function () {
    var all = new Uint8Array(this.size), at = 0;
    this.parts.forEach(function (p) { all.set(p, at); at += p.length; });
    return all;
  };

  /** نقطه ⇒ پوینتِ PDF (۱/۷۲ اینچ) با دو رقمِ اعشار — همان `ImagePdf.pt` */
  function pt(px, dpi) {
    var hundredths = Math.floor((px * 7200 + Math.floor(dpi / 2)) / dpi);
    var frac = hundredths % 100;
    var f = frac !== 0 ? '.' + ('0' + frac).slice(-2).replace(/0+$/, '') : '';
    return String(Math.floor(hundredths / 100)) + f;
  }

  /** عنوان به UTF-16BE با BOM (هگز) */
  function utf16Hex(s) {
    s = String(s || '');
    if (!s.length) return '';
    var out = 'FEFF';
    for (var i = 0; i < s.length; i++) out += ('000' + s.charCodeAt(i).toString(16).toUpperCase()).slice(-4);
    return out;
  }

  /** `RunLengthDecode` (PackBits): ۰..۱۲۷ ⇒ n+1 بایتِ لخت؛ ۱۲۹..۲۵۵ ⇒ بایتِ بعدی 257−n بار؛ ۱۲۸ = پایان */
  function runLength(src) {
    var n = src.length, out = [], i = 0;
    while (i < n) {
      var run = 1;
      while (i + run < n && run < 128 && src[i + run] === src[i]) run++;
      if (run >= 2) { out.push(257 - run, src[i]); i += run; continue; }
      var start = i, len = 0;
      while (i < n && len < 128) {
        if (i + 1 < n && src[i + 1] === src[i]) break;
        i++; len++;
      }
      if (len === 0) { i++; len = 1; }
      out.push(len - 1);
      for (var k = start; k < start + len; k++) out.push(src[k]);
    }
    out.push(128);
    return Uint8Array.from(out);
  }

  /** وارونهٔ `runLength` — برای آزمون و سنجشِ صفحه */
  function runLengthDecode(src) {
    var out = [], i = 0;
    while (i < src.length) {
      var len = src[i++];
      if (len === 128) break;
      if (len < 128) { for (var k = 0; k <= len; k++) out.push(src[i + k]); i += len + 1; }
      else { for (var j = 0; j < 257 - len; j++) out.push(src[i]); i++; }
    }
    return Uint8Array.from(out);
  }

  /** صفحه‌ها `{width, height, bits}` ⇒ بایت‌های PDF؛ همان `ImagePdf.write` */
  function write(pages, dpi, title) {
    dpi = dpi === undefined ? DPI : dpi;
    if (!pages || !pages.length) throw new Error('PDF بی صفحه ساخته نمی‌شود'); // داده: خطای برنامه‌نویس، همان پیامِ ImagePdf
    if (!(dpi >= 36 && dpi <= 1200)) throw new Error('dpi نامعقول است'); // داده: خطای برنامه‌نویس، همان پیامِ ImagePdf
    pages.forEach(function (p) {
      if (!(p.width > 0 && p.height > 0) || p.bits.length !== Math.ceil(p.width / 8) * p.height) throw new Error('bad page');
    });
    var out = new Out(), offsets = [];
    function obj(body) {
      offsets.push(out.size);
      out.text(offsets.length + ' 0 obj\n');
      body();
      out.text('\nendobj\n');
    }
    function stream(dict, data) { out.text(dict + '\nstream\n'); out.bytes(data); out.text('\nendstream'); }
    out.text('%PDF-1.4\n');
    out.bytes(Uint8Array.from([37, 0xE2, 0xE3, 0xCF, 0xD3, 10]));
    var first = 4;
    obj(function () { out.text('<< /Type /Catalog /Pages 2 0 R >>'); });
    var kids = pages.map(function (p, i) { return (first + i * 3) + ' 0 R'; }).join(' ');
    obj(function () { out.text('<< /Type /Pages /Kids [' + kids + '] /Count ' + pages.length + ' >>'); });
    obj(function () { out.text('<< /Producer (VILL3N) /Title <' + utf16Hex(title) + '> >>'); });
    pages.forEach(function (img, i) {
      var page = first + i * 3, w = pt(img.width, dpi), h = pt(img.height, dpi);
      obj(function () {
        out.text('<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ' + w + ' ' + h + '] ');
        out.text('/Resources << /XObject << /Im0 ' + (page + 2) + ' 0 R >> >> /Contents ' + (page + 1) + ' 0 R >>');
      });
      var content = ascii('q ' + w + ' 0 0 ' + h + ' 0 0 cm /Im0 Do Q');
      obj(function () { stream('<< /Length ' + content.length + ' >>', content); });
      var data = runLength(img.bits);
      obj(function () {
        stream('<< /Type /XObject /Subtype /Image /Width ' + img.width + ' /Height ' + img.height + ' /ColorSpace /DeviceGray ' +
          '/BitsPerComponent 1 /Decode [1 0] /Filter /RunLengthDecode /Length ' + data.length + ' >>', data);
      });
    });
    var xref = out.size;
    out.text('xref\n0 ' + (offsets.length + 1) + '\n0000000000 65535 f \n');
    offsets.forEach(function (o) { out.text(('000000000' + o).slice(-10) + ' 00000 n \n'); });
    out.text('trailer\n<< /Size ' + (offsets.length + 1) + ' /Root 1 0 R /Info 3 0 R >>\nstartxref\n' + xref + '\n%%EOF\n');
    return out.done();
  }

  /* ------------------------------ صفحه‌ها روی canvas ------------------------------ */

  /** خط‌های یک متن در پهنای `w` (شکستن روی فاصله؛ واژهٔ بلندتر از پهنا نویسه‌به‌نویسه) */
  function wrap(ctx, text, w) {
    if (!text) return [];
    var lines = [], cur = '';
    String(text).split(/\s+/).forEach(function (word) {
      var next = cur ? cur + ' ' + word : word;
      if (ctx.measureText(next).width <= w) { cur = next; return; }
      if (cur) lines.push(cur);
      cur = '';
      while (ctx.measureText(word).width > w && word.length > 1) {
        var k = word.length - 1;
        while (k > 1 && ctx.measureText(word.slice(0, k)).width > w) k--;
        lines.push(word.slice(0, k));
        word = word.slice(k);
      }
      cur = word;
    });
    if (cur) lines.push(cur);
    return lines;
  }

  /**
   *  ماتریسِ متن (ردیفِ اول سرستون) ⇒ صفحه‌های `{width, height, bits}`. `opts`: {title, sub, font, empty}.
   *  همان چیدمانِ `TablePrintable`: پوینت با مقیاسِ ۱۵۰/۷۲، حاشیهٔ ۲۸، عنوانِ پررنگِ ۱۳، زیرعنوانِ ۷٫۵، متنِ ۸٫۵.
   */
  function render(rows, opts) {
    opts = opts || {};
    var W = 595.2756, H = 841.8898, M = 28, s = DPI / 72;
    var pw = Math.round(W * s), ph = Math.round(H * s), iw = W - 2 * M, ih = H - 2 * M;
    var head = (rows && rows[0]) || [], body = (rows || []).slice(1), cols = Math.max(1, head.length), colW = iw / cols;
    var family = opts.font || 'sans-serif';
    var canvas = document.createElement('canvas');
    canvas.width = pw; canvas.height = ph;
    var ctx = canvas.getContext('2d');
    ctx.direction = 'rtl';
    var F = { text: '8.5px ' + family, head: 'bold 8.5px ' + family, title: 'bold 13px ' + family, sub: '7.5px ' + family };
    var LH = 8.5 * 1.3;
    function cellLines(cells, font) {
      ctx.font = font;
      var out = [];
      for (var i = 0; i < cols; i++) out.push(wrap(ctx, cells[i] === undefined || cells[i] === null ? '' : String(cells[i]), colW - 6));
      return out;
    }
    function rowH(ls) { var n = 1; ls.forEach(function (l) { if (l.length > n) n = l.length; }); return n * LH + 6; }
    //  صفحه‌بندی: همان قاعده — ردیف اگر جا نشود و صفحه دست‌کم یک ردیف دارد، صفحهٔ تازه با سرستون
    var headLs = cellLines(head, F.head), headH = rowH(headLs);
    var bodyLs = body.map(function (r) { return cellLines(r, F.text); });
    var starts = [0], y = 46 + headH;
    bodyLs.forEach(function (ls, i) {
      var h = rowH(ls);
      if (y + h > ih && i > starts[starts.length - 1]) { starts.push(i); y = headH; }
      y += h;
    });
    if (starts.length > MAX_PAGES) {
      var e = new Error('too_many_pages');
      e.code = 'too_many_pages'; e.max = MAX_PAGES;
      throw e;
    }
    function draw(lines, font, right, top) {
      ctx.font = font; ctx.textAlign = 'right'; ctx.textBaseline = 'top';
      lines.forEach(function (l, k) { ctx.fillText(l, right, top + k * LH + 1); });
    }
    /** یک بند در همهٔ پهنا (عنوان، زیرعنوان، «چیزی نیست») — اندازه‌گیری با همان قلم */
    function para(text, font, color, top) { ctx.font = font; ctx.fillStyle = color; draw(wrap(ctx, text || '', iw), font, iw, top); }
    function row(ls, font, shaded, at) {
      var h = rowH(ls);
      if (shaded) { ctx.fillStyle = 'rgb(240,240,240)'; ctx.fillRect(0, at, iw, h); }
      ls.forEach(function (l, i) {
        var x = iw - (i + 1) * colW;
        ctx.strokeStyle = 'rgb(128,128,128)'; ctx.lineWidth = 0.5; ctx.strokeRect(x, at, colW, h);
        ctx.fillStyle = '#000'; draw(l, font, x + colW - 3, at + 3);
      });
      return at + h;
    }
    var pages = [];
    starts.forEach(function (start, p) {
      ctx.setTransform(1, 0, 0, 1, 0, 0);
      ctx.fillStyle = '#fff'; ctx.fillRect(0, 0, pw, ph);
      ctx.setTransform(s, 0, 0, s, M * s, M * s);
      var at = 0;
      if (p === 0) {
        para(opts.title, F.title, '#000', at); at += 20;
        para(opts.sub, F.sub, 'rgb(64,64,64)', at); at += 26;
      }
      if (!body.length) para(opts.empty, F.text, '#000', at);
      else {
        at = row(headLs, F.head, true, at);
        var end = p + 1 < starts.length ? starts[p + 1] : body.length;
        for (var i = start; i < end; i++) at = row(bodyLs[i], F.text, false, at);
      }
      pages.push(mono(ctx.getImageData(0, 0, pw, ph)));
    });
    return pages;
  }

  /** RGBA ⇒ یک‌بیتی: روشناییِ کمتر از ۱۶۰ سیاه (همان آستانهٔ `ReportPrint.render`) */
  function mono(img) {
    var w = img.width, h = img.height, rb = Math.ceil(w / 8), bits = new Uint8Array(rb * h), d = img.data;
    for (var y = 0; y < h; y++) {
      for (var x = 0; x < w; x++) {
        var o = (y * w + x) * 4;
        //  شفاف = سفید؛ خاکستری = میانگینِ سه رنگ (canvas همه را سیاه/خاکستری می‌کشد)
        var lum = d[o + 3] < 128 ? 255 : (d[o] + d[o + 1] + d[o + 2]) / 3;
        if (lum < 160) bits[y * rb + (x >> 3)] |= 0x80 >> (x & 7);
      }
    }
    return { width: w, height: h, bits: bits };
  }

  /** جدول ⇒ Blob ِ PDF (فقط مرورگر) */
  function blob(rows, opts) {
    return new Blob([write(render(rows, opts), DPI, (opts && opts.title) || '')], { type: 'application/pdf' });
  }

  return {
    MAX_PAGES: MAX_PAGES, DPI: DPI,
    write: write, pt: pt, utf16Hex: utf16Hex, runLength: runLength, runLengthDecode: runLengthDecode,
    render: render, mono: mono, blob: blob,
  };
});
