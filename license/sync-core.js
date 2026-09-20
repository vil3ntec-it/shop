/* ==========================================================
   توحید | هستهٔ Sync v1 — دفترِ تغییرات (Oplog)
   ----------------------------------------------------------
   بخشِ ۲۰ی پرامپت، سمتِ برنامه. این فایل **هیچ چیزی از مرورگر
   نمی‌خواهد**: نه DOM، نه fetch، نه localStorage. پس همان کدی که روی
   گوشیِ آیفون می‌دود، در آزمونِ Node هم عیناً دویده می‌شود و ادعاهایش
   سنجیده می‌شوند، نه باور.

   ── قانونِ طلایی ─────────────────────────────────────────────────
   هرگز کلِ دفتر به سرور نمی‌رود، فقط **تغییر**. پاک کردنِ یک اسم یک
   رکوردِ چندصدبایتی است، نه کلِ فهرست.

   ── چرا «تفاضلِ دفتر» و نه «هر تابع خودش op بنویسد» ──────────────
   کلِ منطقِ دکان در `index.html` است و هر جا که چیزی عوض می‌شود، در
   پایان `saveState()` صدا زده می‌شود — یک درِ واحد. اگر می‌خواستیم هر
   یک از صدها جای نوشتن خودش op بسازد، کافی بود یکی‌شان فراموش شود تا
   آن تغییر برای همیشه از همگام‌سازی بیفتد و هیچ‌کس نفهمد.

   پس op از **تفاضلِ دفترِ تازه با سایهٔ آخرین حالتِ همگام‌شده** ساخته
   می‌شود، در همان یک در. یعنی «نوشتنِ مستقیمی که op نسازد» از نظرِ
   ساختاری ممکن نیست — قویتر از قاعدهٔ «هیچ نوشتنی بیرونِ Repository».
   ⛔ این را به «هر تابع خودش op بنویسد» برنگردانید.

   ── شناسه‌ها ────────────────────────────────────────────────────
   ردیف‌های تازه ULID می‌گیرند (۲۶ نویسه، مرتب بر اساس زمان، ۸۰ بیت
   تصادفیِ امن). ردیف‌های امروزِ مشتری شناسهٔ خودشان را نگه می‌دارند —
   شرحِ کاملِ این تصمیم در `docs/SYNC-CLIENT-fa.md`.
   ========================================================== */
(function (factory) {
  'use strict';
  var api = factory();
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  if (typeof window !== 'undefined') window.TohidSyncCore = api;
  else if (typeof globalThis !== 'undefined') globalThis.TohidSyncCore = api;
})(function () {
  'use strict';

  /* ==========================================================
     ۰) نسخهٔ Schema
     ----------------------------------------------------------
     همان عددی که `server/src/lib/sync-v1-migrations.js` برای `shop`
     می‌شناسد. پلهٔ ۲ یعنی ردیفِ فروش فیلدِ `channel` دارد.

     ⚠️ هر فیلد یا مجموعهٔ تازه = یک پله بالاتر، این‌جا **و** روی سرور.
     برنامه‌ای که جلوتر از سرور باشد ۴۲۶ می‌گیرد و opهایش را نگه
     می‌دارد — هیچ چیزی گم نمی‌شود.
     ========================================================== */
  var SCHEMA_VERSION = 2;

  /* ==========================================================
     ۱) مجموعه‌ها — فهرستِ سفید، همان نام‌هایی که سرور می‌شناسد
     ----------------------------------------------------------
     `server/src/lib/sync.js → TABLES` کلیدهایش همین‌هاست و
     `sync-v1.js` همان‌ها را به‌عنوان نامِ جدولِ مجاز می‌پذیرد (بی
     حساسیت به حروفِ بزرگ و کوچک). نامِ بیرونِ این فهرست `rejected`
     می‌گیرد.

     ⚠️ `expenseCategories` · `productCategories` · `productUnits` و
     `nextInvoiceNo` این‌جا **نیستند** و این عمدی است: آن‌ها ردیفِ
     شناسه‌دار نیستند، یک فهرستِ تنظیماتی‌اند. سرور برایشان جدولی
     ندارد و ساختنِ جدولِ ساختگی برای آن‌ها یعنی دروغ. همگام‌سازیِ
     تنظیمات همان `license/shop-sync.js`ِ امروز است و دست نخورده
     ماند.
     ========================================================== */
  var COLLECTIONS = [
    'products', 'warehouseEntries', 'sales', 'saleItems', 'returns',
    'debtors', 'transactions', 'expenses',
    'suppliers', 'purchases', 'supplierPayments',
    'stockMovements', 'priceHistory', 'auditLog',
  ];

  /* ==========================================================
     ۲) فیلدهای شمارنده — دلتا می‌روند، نه «مقدارِ تازه»
     ----------------------------------------------------------
     بندِ ۲۰.۴: «دو فروشِ هم‌زمان از دو دستگاه باید هر دو حساب شوند».

     ⚠️ نکتهٔ راست‌گویانه: در دفترِ این دکان، **موجودیِ انبار اصلاً
     ذخیره نمی‌شود** — از روی ردیف‌های ورود منهای ردیف‌های فروش حساب
     می‌شود (`ShopStore.StockIndex`). پس دو فروشِ هم‌زمان از دو گوشی
     دو ردیفِ جدا می‌سازند و هیچ‌کدام دیگری را نمی‌خورد؛ این مدل از
     روزِ اول جمع‌پذیر بوده.

     آن‌چه واقعاً روی خودِ ردیف «جمع می‌شود» همین چند فیلد است: مقدارِ
     مرجوع‌شدهٔ یک قلم، و پولی که روی یک فاکتور یا یک خرید نشسته. دو
     نفر که هم‌زمان قسط بگیرند، با «مقدارِ تازه» یکی‌شان گم می‌شد.

     ⛔ فیلدی را بی دلیل به این فهرست اضافه نکنید: دلتا روی فیلدی که
     شمارنده نیست یعنی عددی که با هر همگام‌سازی دو برابر می‌شود.
     ========================================================== */
  var INC_FIELDS = {
    sales: ['paidAmount', 'remaining', 'debtGiven', 'debtSettled'],
    saleItems: ['returnedQty'],
    purchases: ['paidAmount', 'debt'],
  };

  function isIncField(collection, field) {
    var list = INC_FIELDS[collection];
    return !!list && list.indexOf(field) !== -1;
  }

  /* ==========================================================
     ۳) ULID — شناسهٔ ردیف و شناسهٔ op
     ----------------------------------------------------------
     ۴۸ بیت زمان + ۸۰ بیت تصادفیِ امن، در الفبای Crockford. مرتب بر
     اساس زمان است، پس opها به همان ترتیبی که ساخته شده‌اند مرتب
     می‌مانند حتی وقتی سه روز در صف بوده‌اند.

     ⚠️ در یک میلی‌ثانیه چند ULID: بخشِ تصادفی یکی بالا می‌رود
     (monotonic)، نه اینکه دوباره تاس بیندازیم. بی این، دو ردیفِ یک
     میلی‌ثانیه ترتیبشان تصادفی می‌شد.
     ========================================================== */
  var B32 = '0123456789ABCDEFGHJKMNPQRSTVWXYZ';

  function randomBytes(n) {
    var out = new Uint8Array(n);
    var c = (typeof globalThis !== 'undefined' && (globalThis.crypto || globalThis.msCrypto)) || null;
    if (c && typeof c.getRandomValues === 'function') { c.getRandomValues(out); return out; }
    //  محیطی که تصادفِ امن ندارد وجود ندارد (Node ۲۰ به بالا و هر
    //  مرورگرِ امروزی دارند). ولی بی‌صدا به `Math.random` برگشتن یعنی
    //  شناسه‌ای که به نظر امن می‌آید و نیست — پس صریح می‌گوییم.
    throw new Error('این محیط تصادفِ امن ندارد؛ ULID ساخته نمی‌شود');
  }

  var lastUlidMs = -1;
  var lastUlidRand = null;

  function ulid(nowMs) {
    var t = typeof nowMs === 'number' ? nowMs : Date.now();
    var time = '';
    var rest = t;
    for (var i = 9; i >= 0; i--) { time = B32[rest % 32] + time; rest = Math.floor(rest / 32); }

    var rnd;
    if (t === lastUlidMs && lastUlidRand) {
      //  همان میلی‌ثانیه: یکی به بخشِ تصادفی اضافه کن تا ترتیب حفظ شود
      rnd = lastUlidRand.slice();
      for (var k = rnd.length - 1; k >= 0; k--) {
        if (rnd[k] < 255) { rnd[k] += 1; break; }
        rnd[k] = 0;
      }
    } else {
      rnd = randomBytes(10);
    }
    lastUlidMs = t;
    lastUlidRand = rnd;

    //  ده بایت = هشتاد بیت = دقیقاً شانزده نویسهٔ پنج‌بیتی
    var bits = 0, acc = 0, tail = '';
    for (var j = 0; j < rnd.length; j++) {
      acc = (acc << 8) | rnd[j];
      bits += 8;
      while (bits >= 5) { bits -= 5; tail += B32[(acc >> bits) & 31]; }
    }
    return time + tail;
  }

  /* ==========================================================
     ۴) اثرِ انگشتِ op — باید مو‌به‌مو با سرور یکی باشد
     ----------------------------------------------------------
     سرور در `sync-v1.js → hashOf` این را می‌سازد:
         sha256("<table>|<row_id>|<type>|<canonical(fields)>")
     و اگر برنامه `hash` بفرستد و نخواند، op را `hash_mismatch` رد
     می‌کند. پس این دو تابع قرارداد مشترک‌اند.

     `canonical` یعنی JSON با کلیدهای **مرتب**، تا اثرِ انگشت روی هر
     زبان و هر کتابخانه‌ای یکی دربیاید.
     ========================================================== */
  function canonical(value) {
    if (value === null || typeof value !== 'object') return JSON.stringify(value);
    if (Array.isArray(value)) {
      var parts = [];
      for (var i = 0; i < value.length; i++) parts.push(canonical(value[i]));
      return '[' + parts.join(',') + ']';
    }
    var keys = Object.keys(value).sort();
    var out = [];
    for (var k = 0; k < keys.length; k++) {
      out.push(JSON.stringify(keys[k]) + ':' + canonical(value[keys[k]]));
    }
    return '{' + out.join(',') + '}';
  }

  /* ---- SHA-256، خالص و همگام ----
     ⚠️ چرا دستی و نه `crypto.subtle`: آن یکی **ناهمگام** است و
     ساختنِ op باید داخلِ همان `saveState()`ِ همگام تمام شود، وگرنه
     تفاضل و سایه از هم می‌افتند. پیاده‌سازیِ زیر در آزمون با
     `crypto.createHash('sha256')`ِ خودِ Node سنجیده می‌شود، پس ادعا
     نیست. */
  var K = [
    0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
    0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
    0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
    0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
    0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
    0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
    0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
    0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2,
  ];

  function utf8Bytes(str) {
    if (typeof TextEncoder !== 'undefined') return new TextEncoder().encode(str);
    throw new Error('این محیط TextEncoder ندارد');
  }

  function sha256Hex(text) {
    var msg = utf8Bytes(text);
    var bitLen = msg.length * 8;
    //  بالشتک: یک بیتِ ۱، بعد صفر، بعد طولِ ۶۴بیتی
    var withPad = new Uint8Array((((msg.length + 8) >> 6) + 1) << 6);
    withPad.set(msg);
    withPad[msg.length] = 0x80;
    var dv = new DataView(withPad.buffer);
    dv.setUint32(withPad.length - 8, Math.floor(bitLen / 0x100000000));
    dv.setUint32(withPad.length - 4, bitLen >>> 0);

    var h = [0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a, 0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19];
    var w = new Int32Array(64);
    for (var off = 0; off < withPad.length; off += 64) {
      for (var i = 0; i < 16; i++) w[i] = dv.getInt32(off + i * 4);
      for (i = 16; i < 64; i++) {
        var g0 = w[i - 15], g1 = w[i - 2];
        var s0 = ((g0 >>> 7) | (g0 << 25)) ^ ((g0 >>> 18) | (g0 << 14)) ^ (g0 >>> 3);
        var s1 = ((g1 >>> 17) | (g1 << 15)) ^ ((g1 >>> 19) | (g1 << 13)) ^ (g1 >>> 10);
        w[i] = (w[i - 16] + s0 + w[i - 7] + s1) | 0;
      }
      var a = h[0], b = h[1], c = h[2], d = h[3], e = h[4], f = h[5], g = h[6], hh = h[7];
      for (i = 0; i < 64; i++) {
        var S1 = ((e >>> 6) | (e << 26)) ^ ((e >>> 11) | (e << 21)) ^ ((e >>> 25) | (e << 7));
        var ch = (e & f) ^ (~e & g);
        var t1 = (hh + S1 + ch + K[i] + w[i]) | 0;
        var S0 = ((a >>> 2) | (a << 30)) ^ ((a >>> 13) | (a << 19)) ^ ((a >>> 22) | (a << 10));
        var maj = (a & b) ^ (a & c) ^ (b & c);
        var t2 = (S0 + maj) | 0;
        hh = g; g = f; f = e; e = (d + t1) | 0;
        d = c; c = b; b = a; a = (t1 + t2) | 0;
      }
      h[0] = (h[0] + a) | 0; h[1] = (h[1] + b) | 0; h[2] = (h[2] + c) | 0; h[3] = (h[3] + d) | 0;
      h[4] = (h[4] + e) | 0; h[5] = (h[5] + f) | 0; h[6] = (h[6] + g) | 0; h[7] = (h[7] + hh) | 0;
    }
    var hex = '';
    for (var n = 0; n < 8; n++) hex += ('00000000' + (h[n] >>> 0).toString(16)).slice(-8);
    return hex;
  }

  /** اثرِ انگشتِ یک op — همان فرمولِ `sync-v1.js` روی سرور. */
  function hashOf(op) {
    return sha256Hex(
      op.table + '|' + op.row_id + '|' + op.type + '|' + canonical(op.fields || {})
    );
  }

  /* ==========================================================
     ۵) تفاضلِ دفتر ⇒ opها
     ========================================================== */

  function isPlainObject(v) {
    return v !== null && typeof v === 'object' && !Array.isArray(v);
  }
  function same(a, b) { return canonical(a) === canonical(b); }

  function indexById(list) {
    var map = Object.create(null);
    if (!Array.isArray(list)) return map;
    for (var i = 0; i < list.length; i++) {
      var row = list[i];
      if (isPlainObject(row) && row.id !== undefined && row.id !== null && row.id !== '') {
        map[String(row.id)] = row;
      }
    }
    return map;
  }

  /** ردیف بی `id` و بی فیلدِ تهی — همان چیزی که داخلِ op می‌نشیند. */
  function bodyOf(row) {
    var out = {};
    var keys = Object.keys(row);
    for (var i = 0; i < keys.length; i++) {
      var k = keys[i];
      if (k === 'id') continue;
      if (row[k] === undefined) continue;
      out[k] = row[k];
    }
    return out;
  }

  /**
   *  فیلدهای عوض‌شدهٔ یک ردیف. شمارنده‌ها دلتا می‌شوند.
   *  @returns {object|null} null یعنی هیچ چیزی عوض نشده
   */
  function changedFields(collection, before, after) {
    var out = {};
    var touched = false;
    var keys = Object.keys(after);
    var i, k;
    for (i = 0; i < keys.length; i++) {
      k = keys[i];
      if (k === 'id' || after[k] === undefined) continue;
      if (same(before[k], after[k])) continue;
      if (isIncField(collection, k) && typeof after[k] === 'number' && Number.isFinite(after[k])) {
        var was = typeof before[k] === 'number' && Number.isFinite(before[k]) ? before[k] : 0;
        var delta = after[k] - was;
        //  دلتای صفر op نمی‌خواهد؛ و دلتای غیرِعددی (NaN) هم نه
        if (!delta) continue;
        out[k] = { $inc: delta };
      } else {
        out[k] = after[k];
      }
      touched = true;
    }
    //  فیلدی که از ردیف برداشته شده: با `null` صریح می‌رود، وگرنه
    //  سرور همان مقدارِ قدیمی را برای همیشه نگه می‌دارد
    var oldKeys = Object.keys(before);
    for (i = 0; i < oldKeys.length; i++) {
      k = oldKeys[i];
      if (k === 'id') continue;
      if (after[k] !== undefined) continue;
      if (before[k] === null) continue;
      out[k] = null;
      touched = true;
    }
    return touched ? out : null;
  }

  /**
   *  تفاضلِ «سایهٔ آخرین حالتِ همگام‌شده» با «دفترِ همین حالا».
   *
   *  @param shadow دفترِ سایه (همان شکلِ دفتر؛ خالی یعنی دستگاهِ نو)
   *  @param next   دفترِ همین حالا
   *  @param opts   {ts, ulid} — فقط برای آزمون؛ در برنامه لازم نیست
   *  @returns {{ops: Array, counts: object}}
   */
  function diff(shadow, next, opts) {
    var o = opts || {};
    var ts = typeof o.ts === 'number' ? o.ts : Date.now();
    var mint = typeof o.ulid === 'function' ? o.ulid : ulid;
    var ops = [];
    var counts = { insert: 0, update: 0, delete: 0 };

    for (var c = 0; c < COLLECTIONS.length; c++) {
      var key = COLLECTIONS[c];
      var before = indexById(shadow && shadow[key]);
      var after = indexById(next && next[key]);
      var id;

      //  درج و ویرایش — به ترتیبِ خودِ فهرست، تا opها همان ترتیبِ
      //  دفتر را داشته باشند و «فاکتور پیش از اقلامش» بماند
      var rows = Array.isArray(next && next[key]) ? next[key] : [];
      for (var r = 0; r < rows.length; r++) {
        var row = rows[r];
        if (!isPlainObject(row) || row.id === undefined || row.id === null || row.id === '') continue;
        id = String(row.id);
        var was = before[id];
        if (!was) {
          ops.push(makeOp(mint(ts), ts, key, id, 'insert', bodyOf(row)));
          counts.insert++;
        } else {
          var fields = changedFields(key, bodyOf(was), bodyOf(row));
          if (fields) {
            ops.push(makeOp(mint(ts), ts, key, id, 'update', fields));
            counts.update++;
          }
        }
      }

      //  حذف — چند صد بایت، بی هیچ فیلدی (بندِ ۲۰.۱)
      var oldIds = Object.keys(before);
      for (var d = 0; d < oldIds.length; d++) {
        id = oldIds[d];
        if (after[id]) continue;
        ops.push(makeOp(mint(ts), ts, key, id, 'delete', undefined));
        counts['delete']++;
      }
    }
    return { ops: ops, counts: counts };
  }

  function makeOp(opId, ts, table, rowId, type, fields) {
    var op = { op_id: opId, ts: ts, table: table, row_id: rowId, type: type };
    if (type !== 'delete') op.fields = fields || {};
    op.hash = hashOf({ table: table, row_id: rowId, type: type, fields: op.fields });
    return op;
  }

  /* ==========================================================
     ۶) اعمالِ opهای دستگاه‌های دیگر روی دفترِ محلی
     ----------------------------------------------------------
     ⚠️ نتیجه **باید** هم روی دفتر بنشیند و هم روی سایه، در یک لحظه.
     اگر فقط روی دفتر بنشیند، تفاضلِ بعدی همان را «تغییرِ تازهٔ خودم»
     می‌بیند و به سرور پس می‌فرستد — حلقهٔ بی‌پایانِ رفت‌وبرگشت.
     ========================================================== */

  /**
   *  @param data دفتر (همان شکلِ `tohid-shop-data-v1`)؛ دست‌نخورده می‌ماند
   *  @param ops  opهایی که از `pull` آمده‌اند، به ترتیبِ `server_seq`
   *  @returns {{data: object, applied: number, skipped: number, tables: object}}
   */
  function applyRemote(data, ops) {
    var next = cloneLedger(data);
    var applied = 0, skipped = 0;
    var tables = {};

    for (var i = 0; i < (ops || []).length; i++) {
      var op = normalizeIncoming(ops[i]);
      if (!op || COLLECTIONS.indexOf(op.table) === -1) { skipped++; continue; }
      if (!Array.isArray(next[op.table])) next[op.table] = [];
      var list = next[op.table];
      var at = -1;
      for (var r = 0; r < list.length; r++) {
        if (list[r] && String(list[r].id) === op.row_id) { at = r; break; }
      }

      if (op.type === 'delete') {
        if (at >= 0) list.splice(at, 1);
        applied++;
        tables[op.table] = (tables[op.table] || 0) + 1;
        continue;
      }

      var row = at >= 0 ? Object.assign({}, list[at]) : { id: op.row_id };
      var keys = Object.keys(op.fields || {});
      for (var k = 0; k < keys.length; k++) {
        var f = keys[k];
        var v = op.fields[f];
        if (isPlainObject(v) && typeof v.$inc === 'number' && Number.isFinite(v.$inc)) {
          var cur = typeof row[f] === 'number' && Number.isFinite(row[f]) ? row[f] : 0;
          row[f] = cur + v.$inc;
        } else {
          row[f] = v;
        }
      }
      row.id = op.row_id;
      if (at >= 0) list[at] = row; else list.push(row);
      applied++;
      tables[op.table] = (tables[op.table] || 0) + 1;
    }
    return { data: next, applied: applied, skipped: skipped, tables: tables };
  }

  function normalizeIncoming(raw) {
    if (!isPlainObject(raw)) return null;
    var table = String(raw.table || raw.table_name || '');
    var rowId = String(raw.row_id || raw.rowId || '');
    var type = String(raw.type || raw.op_type || '');
    if (!table || !rowId || ['insert', 'update', 'delete'].indexOf(type) === -1) return null;
    return {
      op_id: String(raw.op_id || raw.opId || ''),
      table: table, row_id: rowId, type: type,
      fields: isPlainObject(raw.fields) ? raw.fields : {},
      server_seq: Number(raw.server_seq || 0) || 0,
    };
  }

  /* ==========================================================
     ۷) Snapshot ⇒ دفتر — گوشیِ نو، یک بار
     ----------------------------------------------------------
     پاسخِ `GET /api/sync/v1/snapshot` به شکلِ
     `{tables: {products: [{id, data}], …}, cursor}` است.
     ========================================================== */
  function fromSnapshot(snapshot, base) {
    var out = cloneLedger(base || {});
    var tables = (snapshot && snapshot.tables) || {};
    for (var c = 0; c < COLLECTIONS.length; c++) {
      var key = COLLECTIONS[c];
      var rows = tables[key];
      if (!Array.isArray(rows)) continue;
      var list = [];
      for (var i = 0; i < rows.length; i++) {
        var r = rows[i];
        if (!isPlainObject(r)) continue;
        var body = isPlainObject(r.data) ? r.data : {};
        list.push(Object.assign({}, body, { id: String(r.id) }));
      }
      out[key] = list;
    }
    return out;
  }

  /** فقط مجموعه‌های همگام‌شدنی — سایه چیزِ دیگری لازم ندارد. */
  function shadowOf(data) {
    var out = {};
    for (var c = 0; c < COLLECTIONS.length; c++) {
      var key = COLLECTIONS[c];
      out[key] = Array.isArray(data && data[key]) ? JSON.parse(JSON.stringify(data[key])) : [];
    }
    return out;
  }

  function cloneLedger(data) {
    var out = Object.assign({}, data || {});
    for (var c = 0; c < COLLECTIONS.length; c++) {
      var key = COLLECTIONS[c];
      out[key] = Array.isArray(out[key]) ? out[key].slice() : [];
    }
    return out;
  }

  /* ==========================================================
     ۸) صفِ op — سه روز آفلاین هم گم نمی‌شود
     ----------------------------------------------------------
     صف روی همان حافظه‌ای می‌نشیند که دفتر می‌نشیند، پس بستنِ برنامه
     چیزی را نمی‌برد. `io` بیرون داده می‌شود تا آزمون بتواند حافظهٔ
     ساده بدهد و مرورگر `localStorage`.

     ⛔ opی که سرور `applied` یا `duplicate` نگفته، **از صف بیرون
     نمی‌رود**. `rejected` هم بیرون می‌رود ولی در دفترِ کنار می‌نشیند
     (`dropped`) تا معلوم باشد چه شد — بی‌صدا دور ریختن یعنی دادهٔ
     گم‌شده‌ای که هیچ‌کس نمی‌فهمد.
     ========================================================== */
  var QUEUE_MAX = 50000;

  function OpQueue(io, key) {
    this.io = io;
    this.key = key || 'tohid-oplog-v1';
    this.dropKey = this.key + '-dropped';
  }

  OpQueue.prototype.all = function () {
    var raw = this.io.read(this.key);
    return Array.isArray(raw) ? raw : [];
  };
  OpQueue.prototype.size = function () { return this.all().length; };

  OpQueue.prototype.push = function (ops) {
    if (!ops || !ops.length) return 0;
    var list = this.all().concat(ops);
    //  سقفِ ایمنی: دفترِ صف نباید حافظهٔ مرورگر را پر کند. کهنه‌ترین
    //  می‌رود **و در دفترِ کنار ثبت می‌شود**، نه بی‌صدا.
    if (list.length > QUEUE_MAX) {
      var cut = list.splice(0, list.length - QUEUE_MAX);
      this.drop(cut, 'queue_overflow');
    }
    this.io.write(this.key, list);
    return list.length;
  };

  /**
   *  دستهٔ بعدی — با سقفِ خودِ سرور: ۲۰۰ op یا ۲۵۶ کیلوبایت.
   *
   *  ⚠️ سقف بر حسبِ **بایت** است نه نویسه. `'ن'` در UTF-8 دو بایت
   *  است، پس شمردنِ `String.length` روی دفترِ فارسی نزدیک به نصفِ
   *  اندازهٔ واقعی را می‌شمرد و دسته‌ای می‌ساخت که سرور پسش می‌زد.
   *  (سنجهٔ «دستهٔ سنگین» همین را گرفت.)
   *
   *  ⚠️ و اگر خودِ یک op از سقف بزرگ‌تر باشد، باز هم تنها می‌رود:
   *  دستهٔ خالی یعنی صفی که تا ابد گیر کرده.
   */
  OpQueue.prototype.batch = function (maxOps, maxBytes) {
    var cap = maxOps || 200;
    var bytes = maxBytes || 256 * 1024;
    var list = this.all();
    var out = [];
    var size = 2;
    for (var i = 0; i < list.length && out.length < cap; i++) {
      var s = utf8Bytes(JSON.stringify(list[i])).length + 1;
      if (out.length && size + s > bytes) break;
      size += s;
      out.push(list[i]);
    }
    return out;
  };

  /** opهای پذیرفته‌شده بیرون می‌روند؛ بقیه سرِ جایشان می‌مانند. */
  OpQueue.prototype.ack = function (ids) {
    if (!ids || !ids.length) return 0;
    var gone = Object.create(null);
    for (var i = 0; i < ids.length; i++) gone[ids[i]] = true;
    var list = this.all();
    var keep = [];
    var removed = 0;
    for (var j = 0; j < list.length; j++) {
      if (gone[list[j].op_id]) removed++; else keep.push(list[j]);
    }
    this.io.write(this.key, keep);
    return removed;
  };

  OpQueue.prototype.drop = function (ops, reason) {
    if (!ops || !ops.length) return;
    var book = this.io.read(this.dropKey);
    if (!Array.isArray(book)) book = [];
    for (var i = 0; i < ops.length; i++) {
      book.push({ op: ops[i], reason: String(reason || ''), at: Date.now() });
    }
    //  دفترِ ردشده‌ها هم بی‌کران نیست؛ دویست تای آخر برای عیب‌یابی بس است
    if (book.length > 200) book = book.slice(book.length - 200);
    this.io.write(this.dropKey, book);
  };

  OpQueue.prototype.dropped = function () {
    var book = this.io.read(this.dropKey);
    return Array.isArray(book) ? book : [];
  };

  OpQueue.prototype.clear = function () { this.io.write(this.key, []); };

  /* ==========================================================
     ۹) حالِ همگام‌سازی — چراغِ نوارِ بالا
     ----------------------------------------------------------
     سبز = همگام · زرد = در صف · خاکستری = آفلاین/بی‌حساب · قرمز = خطا
     یک تابعِ خالص، تا هم وب و هم اندروید و هم آزمون یک تصمیم بگیرند.
     ========================================================== */
  function dotOf(state) {
    var s = state || {};
    if (s.error) return 'red';
    if (!s.online || !s.signedIn || !s.configured) return 'grey';
    if (s.queued > 0 || s.busy) return 'yellow';
    return 'green';
  }

  var DOT_LABEL = {
    green: 'همگام با سرور',
    yellow: 'در صف ارسال',
    grey: 'آفلاین',
    red: 'خطای همگام‌سازی',
  };

  /* ==========================================================
     ۱۰) عقب‌نشینیِ نمایی — ۲، ۴، ۸ … تا پنج دقیقه، بی‌نهایت تلاش
     ========================================================== */
  var BACKOFF_MIN_MS = 2000;
  var BACKOFF_MAX_MS = 5 * 60 * 1000;

  function backoffMs(attempt, jitter) {
    var n = Math.max(0, Number(attempt) || 0);
    var ms = Math.min(BACKOFF_MAX_MS, BACKOFF_MIN_MS * Math.pow(2, n));
    //  پراکندگی، تا صد گوشی که با هم قطع شده‌اند با هم برنگردند
    var j = typeof jitter === 'number' ? jitter : Math.random();
    return Math.round(ms * (0.75 + j * 0.5));
  }

  return {
    SCHEMA_VERSION: SCHEMA_VERSION,
    COLLECTIONS: COLLECTIONS,
    INC_FIELDS: INC_FIELDS,
    QUEUE_MAX: QUEUE_MAX,
    BACKOFF_MIN_MS: BACKOFF_MIN_MS,
    BACKOFF_MAX_MS: BACKOFF_MAX_MS,
    DOT_LABEL: DOT_LABEL,
    ulid: ulid,
    canonical: canonical,
    sha256Hex: sha256Hex,
    hashOf: hashOf,
    diff: diff,
    applyRemote: applyRemote,
    fromSnapshot: fromSnapshot,
    shadowOf: shadowOf,
    cloneLedger: cloneLedger,
    isIncField: isIncField,
    OpQueue: OpQueue,
    dotOf: dotOf,
    backoffMs: backoffMs,
  };
});
