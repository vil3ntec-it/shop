/* ---------------------------------------------------------------------------
 *  خروجیِ کاملِ داده برای مالک — JSON و Excel (OPS-08) — نسخهٔ وب
 *
 *  ── چه چیزی را می‌بندد ──────────────────────────────────────────────────
 *  دادهٔ دکان مالِ صاحبِ دکان است. تا امروز تنها راهِ بیرون بردنش «پشتیبان»
 *  بود: فایلی برای برگرداندن به همین برنامه، نه برای خواندن یا بردن به جای
 *  دیگر. گزارش‌ها هم فقط برش بودند و هیچ‌کدام همهٔ داده نبود.
 *  ──────────────────────────────────────────────────────────────────────
 *
 *  همان قالبِ برنامهٔ نصبی (`android-native/…/data/OwnerExport.kt`) با همان
 *  نام‌های فارسی؛ یکی بودنِ دو فهرست را `server/test/owner-export.test.js`
 *  می‌سنجد.
 *
 *    • JSON: سرآیندِ نسخه‌دار (format, formatVersion, exportedAt, source,
 *      counts) و بعد همهٔ مجموعه‌ها با همان کلیدهای دفتر — پس همین فایل
 *      در «بازیابی از فایل» هم باز می‌شود.
 *    • Excel: برگهٔ «اطلاعات خروجی» و بعد یک برگه برای هر مجموعه، با
 *      سرستونِ فارسی. نویسندهٔ ‎.xlsx‎ همین‌جاست (ZIP + چند XML) — بی
 *      کتابخانه و بی هیچ درخواستِ شبکه؛ بی اینترنت هم کار می‌کند.
 *
 *  ⛔ **این‌جا فقط خوانده می‌شود.** مُهرِ «آخرین پشتیبان» نمی‌خورد. تنها
 *     نوشتن کارِ صفحه است: یک ردیفِ `data_export` در سابقهٔ عملیات، و شاگرد
 *     اصلاً خروجی نمی‌گیرد (مشخصات ۲۱.۲: فقط مالک/نقشِ مجاز، با ثبتِ audit).
 *  قالبِ امن: هر رشته inlineStr است و هیچ خانه‌ای <f> ندارد — متنِ «=…» فرمول نمی‌شود.
 * ------------------------------------------------------------------------- */
(function (root, factory) {
  'use strict';
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.TohidOwnerExport = factory();
})(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  var FORMAT = 'tohid-owner-export';
  var FORMAT_VERSION = 1;
  var MAX_ROWS = 1048575;
  var MAX_CELL_TEXT = 32767;
  var INFO_SHEET = 'اطلاعات خروجی';
  var ARCHIVE_PREFIX = 'بایگانی — ';
  var XLSX_MIME = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';

  /** مجموعه ⇐ نامِ برگه؛ ترتیبِ برگه‌ها همین است */
  var SHEETS = [
    ['products', 'کالاها'],
    ['productCategories', 'دسته‌های کالا'],
    ['productUnits', 'واحدهای کالا'],
    ['warehouseEntries', 'ورودی‌های انبار'],
    ['sales', 'فاکتورهای فروش'],
    ['saleItems', 'اقلام فاکتورها'],
    ['returns', 'برگشتی‌های فروش'],
    ['debtors', 'قرض‌داران'],
    ['transactions', 'حساب قرض‌داران'],
    ['expenses', 'مصارف'],
    ['expenseCategories', 'دسته‌های مصارف'],
    ['suppliers', 'تأمین‌کنندگان'],
    ['purchases', 'خریدها'],
    ['supplierPayments', 'پرداخت به تأمین‌کنندگان'],
    ['stockMovements', 'حرکات انبار'],
    ['priceHistory', 'سابقهٔ قیمت‌ها'],
    ['auditLog', 'سابقهٔ عملیات']
  ];

  /** فیلد ⇐ سرستونِ فارسی */
  var HEADERS = {
    id: 'شناسه',
    name: 'نام',
    phone: 'تلفن',
    dueDate: 'سررسید',
    creditLimit: 'سقف اعتبار',
    notes: 'یادداشت',
    createdAt: 'زمان ثبت (UTC)',
    date: 'تاریخ',
    amount: 'مبلغ',
    type: 'نوع',
    title: 'عنوان',
    category: 'دسته',
    subcategory: 'زیردسته',
    unit: 'واحد',
    debtorId: 'شناسهٔ قرض‌دار',
    productId: 'شناسهٔ کالا',
    supplierId: 'شناسهٔ تأمین‌کننده',
    saleId: 'شناسهٔ فاکتور',
    saleItemId: 'شناسهٔ قلم فاکتور',
    purchaseId: 'شناسهٔ خرید',
    warehouseEntryId: 'شناسهٔ ورودی انبار',
    refId: 'شناسهٔ مرجع',
    purchasePrice: 'قیمت خرید',
    salePrice: 'قیمت فروش',
    wholesalePrice: 'قیمت عمده',
    minStock: 'حداقل موجودی',
    barcodes: 'بارکدها',
    photo: 'عکس دارد',
    active: 'فعال',
    saleUnit: 'واحد فروش',
    unitFactor: 'ضریب واحد',
    cartons: 'تعداد کارتن',
    perCarton: 'تعداد در هر کارتن',
    units: 'تعداد واحد',
    price: 'قیمت',
    isAdjustment: 'اصلاح موجودی',
    expiryDate: 'تاریخ انقضا',
    batch: 'شمارهٔ بچ',
    productionDate: 'تاریخ تولید',
    invoiceNumber: 'شمارهٔ فاکتور',
    series: 'سری سند',
    channel: 'کانال فروش',
    total: 'جمع',
    discountType: 'نوع تخفیف',
    discountValue: 'مقدار تخفیف',
    discount: 'تخفیف',
    finalTotal: 'مبلغ نهایی',
    paymentMethod: 'روش پرداخت',
    paidAmount: 'پرداخت‌شده',
    remaining: 'باقی‌مانده',
    status: 'وضعیت',
    debtGiven: 'قرض داده‌شده',
    debtSettled: 'قرض تسویه‌شده',
    syncStatus: 'وضعیت همگام‌سازی',
    cancelReason: 'دلیل لغو',
    seller: 'فروشنده',
    //  PAY (v3 A4): فقط فروش و صندوقِ پرچم‌دار
    payments: 'ردیف‌های پرداخت',
    tenderedMinor: 'دریافتی (پول)',
    changeMinor: 'پول پس (پول)',
    by: 'کاربر',
    approvedBy: 'تأییدکننده',
    note: 'توضیح',
    z: 'گزارش Z',
    quantity: 'تعداد',
    unitPrice: 'قیمت واحد',
    totalPrice: 'جمع قلم',
    returnedQty: 'تعداد برگشتی',
    reason: 'دلیل',
    address: 'نشانی',
    totalAmount: 'مبلغ کل',
    debt: 'بدهی',
    invoiceNo: 'شمارهٔ فاکتور خرید',
    qty: 'مقدار',
    oldPrice: 'قیمت قبلی',
    newPrice: 'قیمت تازه',
    nextInvoiceNo: 'شمارهٔ فاکتور بعدی'
  };

  /** کلیدهای سرآیند — داده نیستند و برگه نمی‌شوند */
  var HEAD = { format: 1, formatVersion: 1, exportedAt: 1, source: 1, counts: 1, settings: 1, archive: 1 };

  var own = function (o, k) { return Object.prototype.hasOwnProperty.call(o, k); };
  var label = function (k) { return own(HEADERS, k) ? HEADERS[k] : k; };
  var sheetLabel = function (k) {
    for (var i = 0; i < SHEETS.length; i++) if (SHEETS[i][0] === k) return SHEETS[i][1];
    return k;
  };

  /** «2026-10-14T08:00:00Z» — بی میلی‌ثانیه، همان شکلِ برنامهٔ نصبی */
  function iso(ms) { return new Date(ms).toISOString().replace(/\.\d{3}Z$/, 'Z'); }

  /* ------------------------------ JSON ------------------------------ */

  /**
   *  درختِ خروجی از روی دادهٔ دفتر (همان `backupPayload()`ِ صفحه).
   *  آرایه‌های دفتر کپی نمی‌شوند و دست هم نمی‌خورند؛ فقط خوانده می‌شوند.
   */
  function tree(payload, meta) {
    payload = payload || {};
    meta = meta || {};
    var out = {
      format: FORMAT,
      formatVersion: FORMAT_VERSION,
      exportedAt: iso(meta.exportedAt != null ? meta.exportedAt : Date.now()),
      source: { app: meta.app || 'web', appVersion: meta.appVersion || '' },
      counts: {}
    };
    var keys = Object.keys(payload);
    keys.forEach(function (k) {
      if (!own(HEAD, k) && Array.isArray(payload[k])) out.counts[k] = payload[k].length;
    });
    keys.forEach(function (k) {
      if (!own(HEAD, k) && payload[k] !== undefined) out[k] = payload[k];
    });
    out.settings = (payload.settings && typeof payload.settings === 'object') ? payload.settings : {};
    if (payload.archive && typeof payload.archive === 'object') out.archive = payload.archive;
    return out;
  }

  function json(payload, meta) { return JSON.stringify(tree(payload, meta), null, 2); }

  /** خواندنِ همین قالب؛ نسخهٔ تازه‌تر یا فایلِ دیگر رد می‌شود */
  function parse(raw) {
    var t;
    try { t = JSON.parse(raw); } catch (e) { t = null; }
    if (!t || typeof t !== 'object' || Array.isArray(t)) throw new Error('فایلِ خروجی معتبر نیست');
    if (t.format !== FORMAT) throw new Error('این فایل، خروجیِ کاملِ فروشگاه من نیست');
    var v = t.formatVersion;
    if (typeof v !== 'number' || v < 1 || v > FORMAT_VERSION) {
      throw new Error('این خروجی با قالبِ ' + v + ' ساخته شده و این نسخهٔ برنامه آن را نمی‌شناسد');
    }
    var data = {};
    Object.keys(t).forEach(function (k) { if (!own(HEAD, k)) data[k] = t[k]; });
    data.settings = t.settings || {};
    return { formatVersion: v, data: data, archive: t.archive || null };
  }

  /* ------------------------------ برگه‌ها ------------------------------ */

  //  یک خانه: {t:'s'|'n'|'b'|'d', v} — نوع را خودِ داده می‌گوید، نه حدس
  function cell(key, e) {
    if (e === null || e === undefined) return null;
    if (typeof e === 'string') return { t: 's', v: e };
    if (typeof e === 'boolean') return { t: 'b', v: e };
    if (typeof e === 'number') {
      if (!isFinite(e)) return { t: 's', v: String(e) };
      //  زمانِ میلی‌ثانیه‌ای (`createdAt` و…) ⇐ تاریخ و ساعتِ خوانا
      if (/At$/.test(key) && Number.isInteger(e) && e > 100000000000) return { t: 'd', v: e };
      return { t: 'n', v: e };
    }
    if (Array.isArray(e)) {
      var flat = e.every(function (x) { return x !== null && typeof x !== 'object'; });
      return { t: 's', v: flat ? e.map(String).join('، ') : JSON.stringify(e) };
    }
    return { t: 's', v: JSON.stringify(e) };
  }

  function table(arr) {
    if (!arr.length) return [[{ t: 's', v: 'هیچ ردیفی ثبت نشده' }]];
    var objs = arr.every(function (x) { return x && typeof x === 'object' && !Array.isArray(x); });
    if (!objs) return [[{ t: 's', v: 'مقدار' }]].concat(arr.map(function (x) { return [cell('', x)]; }));
    var cols = [];
    var seen = {};
    arr.forEach(function (o) {
      Object.keys(o).forEach(function (k) { if (!own(seen, k)) { seen[k] = 1; cols.push(k); } });
    });
    var rows = [cols.map(function (k) { return { t: 's', v: label(k) }; })];
    arr.forEach(function (o) { rows.push(cols.map(function (k) { return cell(k, o[k]); })); });
    return rows;
  }

  /** برگهٔ بیش از سقفِ اکسل ⇐ چند برگه، هر کدام با همان سرستون */
  function split(name, rows) {
    if (rows.length - 1 <= MAX_ROWS) return [{ name: name, rows: rows }];
    var out = [];
    for (var i = 1; i < rows.length; i += MAX_ROWS) out.push({ name: name, rows: [rows[0]].concat(rows.slice(i, i + MAX_ROWS)) });
    return out;
  }

  function sheets(t) {
    var tables = [];
    var keys = SHEETS.map(function (s) { return s[0]; }).filter(function (k) { return Array.isArray(t[k]); });
    Object.keys(t).forEach(function (k) {
      if (keys.indexOf(k) < 0 && !own(HEAD, k) && Array.isArray(t[k])) keys.push(k);
    });
    keys.forEach(function (k) { tables = tables.concat(split(sheetLabel(k), table(t[k]))); });
    if (t.archive && typeof t.archive === 'object') {
      Object.keys(t.archive).forEach(function (k) {
        if (Array.isArray(t.archive[k])) tables = tables.concat(split(ARCHIVE_PREFIX + sheetLabel(k), table(t.archive[k])));
      });
    }
    return [{ name: INFO_SHEET, rows: info(t, tables) }].concat(tables);
  }

  function info(t, tables) {
    var s = function (v) { return { t: 's', v: v == null ? '' : String(v) }; };
    var src = t.source || {};
    var st = t.settings || {};
    var rows = [
      [s('عنوان'), s('مقدار')],
      [s('قالب'), s(t.format)],
      [s('نسخهٔ قالب'), cell('formatVersion', t.formatVersion)],
      [s('زمان خروجی (UTC)'), s(t.exportedAt)],
      [s('برنامه'), s(src.app)],
      [s('نسخهٔ برنامه'), s(src.appVersion)],
      [s('نام فروشگاه'), s(st.storeName)]
    ];
    Object.keys(t).forEach(function (k) {
      if (!own(HEAD, k) && !Array.isArray(t[k])) rows.push([s(label(k)), cell(k, t[k])]);
    });
    Object.keys(st).forEach(function (k) {
      if (k !== 'storeName') rows.push([s('تنظیمات — ' + k), cell(k, st[k])]);
    });
    tables.forEach(function (tb) { rows.push([s('ردیف‌های «' + tb.name + '»'), { t: 'n', v: tb.rows.length - 1 }]); });
    rows.push([s('عکس‌ها'), s('در این خروجی نیستند؛ «پشتیبان کامل» آن‌ها را دارد')]);
    return rows;
  }

  /* ------------------------------ نویسندهٔ ‎.xlsx‎ ------------------------------ */

  function esc(str) {
    var out = '';
    for (var i = 0; i < str.length; i++) {
      var ch = str.charAt(i);
      var c = str.charCodeAt(i);
      if (ch === '&') out += '&amp;';
      else if (ch === '<') out += '&lt;';
      else if (ch === '>') out += '&gt;';
      else if (ch === '"') out += '&quot;';
      else if (c < 0x20 && c !== 9 && c !== 10 && c !== 13) continue;
      else out += ch;
    }
    return out;
  }

  function clip(str) {
    if (str.length <= MAX_CELL_TEXT) return str;
    var cut = MAX_CELL_TEXT - 1;
    var c = str.charCodeAt(cut - 1);
    if (c >= 0xD800 && c <= 0xDBFF) cut--;
    return str.slice(0, cut) + '…';
  }

  /** «12» نه «12.0»؛ اعشار و نمادِ علمی همان‌طور که هست (‎xsd:double‎ هر دو را می‌پذیرد) */
  function num(v) { return String(v); }

  function shown(c) {
    if (!c) return '';
    if (c.t === 's') return c.v;
    if (c.t === 'n') return num(c.v);
    if (c.t === 'b') return 'TRUE';
    return '0000-00-00 00:00';
  }

  function col(i) {
    var n = i + 1;
    var out = '';
    while (n > 0) { var m = (n - 1) % 26; out = String.fromCharCode(65 + m) + out; n = Math.floor((n - 1) / 26); }
    return out;
  }

  function textCell(ref, str, head) {
    return '<c r="' + ref + '" t="inlineStr"' + (head ? ' s="1"' : '') + '><is><t xml:space="preserve">' + esc(clip(str)) + '</t></is></c>';
  }

  function cellXml(ref, c, head) {
    if (head) return textCell(ref, shown(c), true);
    if (c.t === 's') return textCell(ref, c.v, false);
    if (c.t === 'b') return '<c r="' + ref + '" t="b"><v>' + (c.v ? 1 : 0) + '</v></c>';
    //  ۲۵۵۶۹ = روزهای ۱۹۰۰/۰۱/۰۰ تا ۱۹۷۰/۰۱/۰۱ در تقویمِ اکسل
    if (c.t === 'd') return '<c r="' + ref + '" s="3"><v>' + (c.v / 86400000 + 25569) + '</v></c>';
    var v = num(c.v);
    return '<c r="' + ref + '"' + (/[.eE]/.test(v) ? '' : ' s="2"') + '><v>' + v + '</v></c>';
  }

  function sheetXml(rows) {
    var cols = 0;
    rows.forEach(function (r) { if (r.length > cols) cols = r.length; });
    var parts = [
      '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>',
      '<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">',
      '<sheetViews><sheetView rightToLeft="1" workbookViewId="0">',
      rows.length > 1 ? '<pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/>' : '',
      '</sheetView></sheetViews>'
    ];
    if (cols > 0) {
      var width = [];
      for (var i = 0; i < cols; i++) width.push(0);
      rows.forEach(function (r) { r.forEach(function (c, j) { var l = shown(c).length; if (l > width[j]) width[j] = l; }); });
      parts.push('<cols>');
      width.forEach(function (w, j) {
        parts.push('<col min="' + (j + 1) + '" max="' + (j + 1) + '" width="' + (Math.min(60, Math.max(6, w)) + 2) + '" customWidth="1"/>');
      });
      parts.push('</cols>');
    }
    parts.push('<sheetData>');
    rows.forEach(function (r, ri) {
      var line = '<row r="' + (ri + 1) + '">';
      r.forEach(function (c, ci) { if (c) line += cellXml(col(ci) + (ri + 1), c, ri === 0); });
      parts.push(line + '</row>');
    });
    parts.push('</sheetData></worksheet>');
    return parts.join('');
  }

  function sheetName(title) {
    var n = String(title).replace(/[\\/?*\[\]:]/g, ' ').trim().slice(0, 31);
    return n || 'Sheet1';
  }

  /** اکسل دو برگهٔ هم‌نام (بی توجه به بزرگی و کوچکیِ حروف) را باز نمی‌کند */
  function uniqueNames(titles) {
    var seen = {};
    return titles.map(function (t) {
      var base = sheetName(t);
      var name = base;
      var n = 2;
      while (own(seen, name.toLowerCase())) {
        var tail = ' (' + n + ')';
        name = base.slice(0, 31 - tail.length) + tail;
        n++;
      }
      seen[name.toLowerCase()] = 1;
      return name;
    });
  }

  var STYLES = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><numFmts count="1"><numFmt numFmtId="164" formatCode="yyyy-mm-dd hh:mm"/></numFmts><fonts count="2"><font><sz val="11"/><name val="Tahoma"/></font><font><b/><sz val="11"/><name val="Tahoma"/></font></fonts><fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FFE8E6F5"/><bgColor indexed="64"/></patternFill></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="4"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/><xf numFmtId="3" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/><xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>';

  function bookFiles(list) {
    if (!list.length) list = [{ name: 'Sheet1', rows: [] }];
    var names = uniqueNames(list.map(function (s) { return s.name; }));
    var n = list.length;
    var ct = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>';
    var wb = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>';
    var rels = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">';
    for (var i = 1; i <= n; i++) {
      ct += '<Override PartName="/xl/worksheets/sheet' + i + '.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>';
      wb += '<sheet name="' + esc(names[i - 1]) + '" sheetId="' + i + '" r:id="rId' + i + '"/>';
      rels += '<Relationship Id="rId' + i + '" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet' + i + '.xml"/>';
    }
    ct += '<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>';
    wb += '</sheets></workbook>';
    rels += '<Relationship Id="rId' + (n + 1) + '" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>';
    var files = [
      ['[Content_Types].xml', ct],
      ['_rels/.rels', '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>'],
      ['xl/workbook.xml', wb],
      ['xl/_rels/workbook.xml.rels', rels],
      ['xl/styles.xml', STYLES]
    ];
    list.forEach(function (s, i) { files.push(['xl/worksheets/sheet' + (i + 1) + '.xml', sheetXml(s.rows)]); });
    return files;
  }

  /* ------------------------------ ZIP ------------------------------ */

  var CRC_TABLE = null;
  function crc32(u8) {
    if (!CRC_TABLE) {
      CRC_TABLE = new Uint32Array(256);
      for (var n = 0; n < 256; n++) {
        var c = n;
        for (var k = 0; k < 8; k++) c = (c & 1) ? (0xEDB88320 ^ (c >>> 1)) : (c >>> 1);
        CRC_TABLE[n] = c >>> 0;
      }
    }
    var crc = 0xFFFFFFFF;
    for (var i = 0; i < u8.length; i++) crc = CRC_TABLE[(crc ^ u8[i]) & 0xFF] ^ (crc >>> 8);
    return (crc ^ 0xFFFFFFFF) >>> 0;
  }

  /**
   *  فشرده‌سازی با `CompressionStream`ِ خودِ مرورگر؛ مرورگری که ندارد
   *  (Safariِ قدیمی) ⇐ بی فشرده‌سازی (روشِ «store») — فایل بزرگ‌تر ولی درست.
   */
  function deflateRaw(u8) {
    try {
      if (typeof CompressionStream !== 'function' || typeof Blob !== 'function' || typeof Response !== 'function') return Promise.resolve(null);
      var stream = new Blob([u8]).stream().pipeThrough(new CompressionStream('deflate-raw'));
      return new Response(stream).arrayBuffer().then(function (b) { return new Uint8Array(b); }, function () { return null; });
    } catch (e) {
      return Promise.resolve(null);
    }
  }

  function zip(files, compress) {
    var enc = new TextEncoder();
    var entries = files.map(function (f) { return { name: enc.encode(f[0]), data: enc.encode(f[1]) }; });
    return Promise.all(entries.map(function (e) { return compress === false ? null : deflateRaw(e.data); })).then(function (packed) {
      var chunks = [];
      var central = [];
      var offset = 0;
      entries.forEach(function (e, i) {
        var body = packed[i] || e.data;
        var method = packed[i] ? 8 : 0;
        var crc = crc32(e.data);
        var lh = new DataView(new ArrayBuffer(30));
        lh.setUint32(0, 0x04034b50, true);
        lh.setUint16(4, 20, true);
        lh.setUint16(6, 0x0800, true);          // نام‌ها UTF-8
        lh.setUint16(8, method, true);
        lh.setUint16(10, 0, true);              // ساعت و تاریخِ ثابت (۱۹۸۰/۱/۱) — خروجیِ تکرارپذیر
        lh.setUint16(12, 0x21, true);
        lh.setUint32(14, crc, true);
        lh.setUint32(18, body.length, true);
        lh.setUint32(22, e.data.length, true);
        lh.setUint16(26, e.name.length, true);
        lh.setUint16(28, 0, true);
        chunks.push(new Uint8Array(lh.buffer), e.name, body);
        var ch = new DataView(new ArrayBuffer(46));
        ch.setUint32(0, 0x02014b50, true);
        ch.setUint16(4, 20, true);
        ch.setUint16(6, 20, true);
        ch.setUint16(8, 0x0800, true);
        ch.setUint16(10, method, true);
        ch.setUint16(12, 0, true);
        ch.setUint16(14, 0x21, true);
        ch.setUint32(16, crc, true);
        ch.setUint32(20, body.length, true);
        ch.setUint32(24, e.data.length, true);
        ch.setUint16(28, e.name.length, true);
        ch.setUint32(42, offset, true);
        central.push(new Uint8Array(ch.buffer), e.name);
        offset += 30 + e.name.length + body.length;
      });
      var cdSize = central.reduce(function (s, c) { return s + c.length; }, 0);
      var end = new DataView(new ArrayBuffer(22));
      end.setUint32(0, 0x06054b50, true);
      end.setUint16(8, entries.length, true);
      end.setUint16(10, entries.length, true);
      end.setUint32(12, cdSize, true);
      end.setUint32(16, offset, true);
      var all = chunks.concat(central, [new Uint8Array(end.buffer)]);
      var size = all.reduce(function (s, c) { return s + c.length; }, 0);
      var out = new Uint8Array(size);
      var at = 0;
      all.forEach(function (c) { out.set(c, at); at += c.length; });
      return out;
    });
  }

  /** فایلِ ‎.xlsx‎ از برگه‌ها ⇐ Promise<Uint8Array> */
  function xlsx(list, opts) { return zip(bookFiles(list || []), !(opts && opts.compress === false)); }

  /* ------------------------------ ذخیره در مرورگر ------------------------------ */

  /**
   *  خروجی گرفتن و دادنِ فایل به کاربر. `payload` همان `backupPayload()`ِ
   *  صفحه است. این تابع چیزی نمی‌نویسد؛ ردیفِ سابقه را صفحه پس از موفقیت می‌گذارد.
   */
  function save(kind, payload, today) {
    var cfg = (typeof window !== 'undefined' && window.TohidApiConfig) || null;
    var meta = {
      exportedAt: Date.now(),
      app: 'web',
      appVersion: (cfg && typeof cfg.appVersion === 'function') ? String(cfg.appVersion() || '') : ''
    };
    var t = tree(payload, meta);
    var made = kind === 'xlsx'
      ? xlsx(sheets(t)).then(function (u8) { return new Blob([u8], { type: XLSX_MIME }); })
      : Promise.resolve(new Blob([JSON.stringify(t, null, 2)], { type: 'application/json' }));
    return made.then(function (blob) {
      var url = URL.createObjectURL(blob);
      var a = document.createElement('a');
      a.href = url;
      a.download = 'tohid-export-' + today + '.' + (kind === 'xlsx' ? 'xlsx' : 'json');
      document.body.appendChild(a);
      a.click();
      a.remove();
      setTimeout(function () { URL.revokeObjectURL(url); }, 10000);
      return a.download;
    });
  }

  return {
    FORMAT: FORMAT,
    FORMAT_VERSION: FORMAT_VERSION,
    MAX_ROWS: MAX_ROWS,
    MAX_CELL_TEXT: MAX_CELL_TEXT,
    INFO_SHEET: INFO_SHEET,
    ARCHIVE_PREFIX: ARCHIVE_PREFIX,
    XLSX_MIME: XLSX_MIME,
    SHEETS: SHEETS,
    HEADERS: HEADERS,
    tree: tree,
    json: json,
    parse: parse,
    sheets: sheets,
    xlsx: xlsx,
    crc32: crc32,
    save: save
  };
});
