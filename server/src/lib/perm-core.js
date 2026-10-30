/* ==========================================================
   فروشگاه من | هستهٔ سنجشِ مجوز (STF-01 تا STF-03، بخشِ ۱۰.۱)
   ----------------------------------------------------------
   همتای دقیقِ `core/src/commonMain/kotlin/ir/vil3ntec/tohid/core/perm/PermCore.kt`.
   هر دو با یک فایلِ مرجع سنجیده می‌شوند: `core/test-vectors/perm-v1.json`.
   سرور نسخهٔ عیناً همین فایل را در `server/src/lib/perm-core.js` دارد
   (پوشهٔ ساختِ داکرِ سرور فقط `server/` است) و آزمون برابریِ بایت‌به‌بایتِ
   دو نسخه را می‌سنجد. ⛔ یکی را بی دیگری و بی فایلِ مرجع عوض نکنید.

   ── مدل ──────────────────────────────────────────────────────────
   هر مجوز سه حالت دارد: allow (مجاز)، deny (ممنوع)، approve (نیازمند تأیید).
   حالتِ پیش‌فرضِ هر نقش «رفتارِ امروز» است. روی آن، لایه‌ها به‌ترتیب
   می‌نشینند (لایهٔ نقش در این دکان، سپس لایهٔ همین کاربر):
     { "settings.write": { "mode": "deny" },
       "sales.discount": { "mode": "allow", "caps": { "maxPct": 5 } } }
   • مالک همیشه همه‌کاره است؛ هیچ لایه‌ای او را قفل نمی‌کند.
   • نقشِ ناشناخته و مجوزِ ناشناخته ⇐ ممنوع (بسته‌به‌خطا).
   • مجوزِ «restrict» فقط تنگ‌تر از پیش‌فرضِ نقش می‌شود، هرگز گشادتر:
     لایه‌ای که بیش از نقش بدهد، تا حدِ نقش بریده می‌شود. تنها استثنا
     «نیازمند تأیید» برای مجوزِ تأییدپذیر است: به خودِ کاربر هیچ قدرتی
     نمی‌دهد، چون سرور op را فقط با «برگهٔ امضاشده»ی کسی می‌پذیرد که خودش
     همان کار را «مجاز» دارد (بخشِ ۱۰.۱؛ `server/src/lib/staff-approval.js`).
   • مجوزِ «none» از لایه‌ها اثر نمی‌گیرد.
   • سقف (caps) فقط وقتی اثر دارد که حالت allow باشد؛ عددِ بالاتر از
     سقف ⇐ approve (اگر مجوز تأییدپذیر است) وگرنه deny.

   ── نقش‌ها (STF-01، بخشِ ۱۰.۱) ─────────────────────────────────────
   owner/manager/staff همان سه نقشِ قدیم‌اند و رفتارشان عوض نمی‌شود. چهار
   نقشِ تازه (A16) فقط وقتی کسی را می‌گیرند که صاحب دکان خودش بدهد:
     cashier     صندوق‌دار — فروش، قرض و برگشت؛ نه انبار، نه خرید
     storekeeper انباردار — کالا، رسیدِ انبار، خرید و شمارش؛ نه فروش و پول
     picker      جمع‌آور — سفارشِ اینترنتی را آماده می‌کند؛ دفتر نمی‌نویسد
     accountant  حسابدار — همه را می‌بیند (عددِ مالی و سابقه هم)، هیچ نمی‌نویسد
   پیک (courier) عمداً این‌جا نیست: توکنش دامنهٔ خودش را دارد.

   `canWrite(role, table)` = کدام جدولِ همگام‌سازی را این نقش می‌نویسد.
   سه نقشِ قدیم «*» (همه، همان امروز)؛ نقشِ ناشناخته هیچ. سرور در هر سه
   راهِ همگام‌سازی همین را اجرا می‌کند و برنامه پیش از ذخیره همین را می‌پرسد.

   این فایل هیچ چیزی از مرورگر نمی‌خواهد؛ همان کد در Node هم می‌دود.
   ========================================================== */
(function (factory) {
  'use strict';
  var api = factory();
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  if (typeof window !== 'undefined') window.TohidPerm = api;
  else if (typeof globalThis !== 'undefined') globalThis.TohidPerm = api;
})(function () {
  'use strict';

  var VERSION = 1;
  var MODES = ['allow', 'approve', 'deny'];
  var RANK = { deny: 0, approve: 1, allow: 2 };
  var ROLES = ['owner', 'manager', 'staff', 'cashier', 'storekeeper', 'picker', 'accountant'];

  /** نامِ سقف ⇐ نامِ فیلدِ درخواست */
  var CAP_FIELDS = { maxPct: 'pct', maxAmount: 'amount' };

  /** پیش‌فرض‌ها به ترتیبِ `ROLES` */
  function d(owner, manager, staff, cashier, storekeeper, picker, accountant) {
    return {
      owner: owner, manager: manager, staff: staff,
      cashier: cashier, storekeeper: storekeeper, picker: picker, accountant: accountant,
    };
  }

  /*
   *  فهرستِ مجوزهایی که از هسته می‌گذرند. پیش‌فرض‌ها = رفتارِ امروز:
   *  همان `MATRIX`ِ `server/src/lib/permissions.js` برای کلیدهای مشترک، و
   *  همان `ShopRole`ِ برنامه برای `reports.money` (شاگرد عددِ مالی نمی‌بیند).
   *    edit: none | restrict | full
   *    approvable: آیا «نیازمند تأیید» برایش معنا دارد
   *    display: فقط نمایش است (داده روی دستگاه هست؛ امنیت نیست)
   */
  var CATALOG = {
    'sales.discount': {
      defaults: d('allow', 'allow', 'deny', 'deny', 'deny', 'deny', 'deny'),
      edit: 'restrict', approvable: true, caps: ['maxPct', 'maxAmount'], display: false,
    },
    'sales.cancel': {
      defaults: d('allow', 'allow', 'deny', 'deny', 'deny', 'deny', 'deny'),
      edit: 'restrict', approvable: true, caps: [], display: false,
    },
    //  برگشتِ کالا و بازپرداخت (STF-03 «مبلغِ برگشت»): امروز هر عضوِ نویسنده
    //  ثبت می‌کند، پس پیش‌فرض برای همه allow است؛ از نقش‌های تازه فقط صندوق‌دار
    'returns.create': {
      defaults: d('allow', 'allow', 'allow', 'allow', 'deny', 'deny', 'deny'),
      edit: 'restrict', approvable: true, caps: ['maxAmount'], display: false,
    },
    'settings.write': {
      defaults: d('allow', 'allow', 'deny', 'deny', 'deny', 'deny', 'deny'),
      edit: 'restrict', approvable: false, caps: [], display: false,
    },
    //  حسابدار سابقه و عددِ مالی را می‌بیند — کارش همین است
    'audit.view': {
      defaults: d('allow', 'allow', 'deny', 'deny', 'deny', 'deny', 'allow'),
      edit: 'restrict', approvable: false, caps: [], display: false,
    },
    'reports.money': {
      defaults: d('allow', 'allow', 'deny', 'deny', 'deny', 'deny', 'allow'),
      edit: 'full', approvable: false, caps: [], display: true,
    },
    //  HW-03: باز کردنِ دستیِ کشو بی فروش (`no_sale`)؛ شاگرد فقط با تأییدکننده — همان no_sale ِ #286.
    //  نقش‌های تازه هم «ممنوع» (صندوق‌دار هم: لایهٔ نقش می‌تواند «با تأیید» کند، مثلِ شاگرد)
    'drawer.open': {
      defaults: d('allow', 'allow', 'deny', 'deny', 'deny', 'deny', 'deny'),
      edit: 'restrict', approvable: true, caps: [], display: false,
    },
  };

  function has(o, k) { return o != null && Object.prototype.hasOwnProperty.call(o, k); }

  /*
   *  جدول‌های همگام‌سازی که هر نقش می‌نویسد (نام‌های Sync v1/v3، همان
   *  `SyncEngine.COLLECTIONS`ِ برنامه). «*» = همه — رفتارِ امروزِ سه نقشِ قدیم.
   *  `auditLog` برای هر نقشِ نویسنده است: سابقهٔ همان کاری که کرده.
   *  فروش با قلم، حرکتِ انبار، پرداختِ قرض و سابقه یک‌جا می‌نشیند؛ رسیدِ خرید
   *  با کالا، تاریخچهٔ قیمت، رسیدِ انبار و حرکتِ انبار.
   *  صندوق‌دار `warehouseEntries` و `stockMovements` را فقط با «قاعدهٔ ردیف» دارد (`ROW_RULES`
   *  پایین): اصلاحِ موجودیِ قلمِ برگشت (RET، #318) و حرکتِ انبارِ فاکتور و برگشت — نه رسیدِ
   *  انبارِ دستی، نه اصلاح یا خریدِ آزاد.
   */
  var WRITES = {
    owner: '*',
    manager: '*',
    staff: '*',
    cashier: ['auditLog', 'debtors', 'returns', 'saleItems', 'sales', 'stockMovements', 'transactions', 'warehouseEntries'],
    storekeeper: ['auditLog', 'priceHistory', 'productCategories', 'productUnits', 'products', 'purchases',
      'stockCounts', 'stockMovements', 'suppliers', 'warehouseEntries'],
    picker: ['auditLog'],
    accountant: [],
  };

  /** این نقش این جدولِ همگام‌سازی را می‌نویسد؟ نقشِ ناشناخته ⇐ نه (بسته‌به‌خطا) */
  function canWrite(role, table) {
    if (typeof role !== 'string' || !has(WRITES, role) || typeof table !== 'string' || !table) return false;
    var w = WRITES[role];
    return w === '*' || w.indexOf(table) >= 0;
  }

  /*
   *  قاعدهٔ ردیف: جدولی که نقش فقط ردیف‌های تازهٔ معینی از آن را می‌نویسد. ویرایش و حذف
   *  هرگز؛ «insert» روی ردیفِ موجود هم ویرایش است (گردآورنده آن را null می‌دهد).
   *    return_adjustment  اصلاحِ موجودیِ یک قلمِ برگشت (#318): `isAdjustment` = true، بی قیمت و
   *                       کارتن، `adjKind` یکی از RETURN_ADJ_KINDS با علامتِ همان نوع (ضایعات منفی،
   *                       برگشتِ بی‌رسید مثبت)، `refId` = شناسه یا `docId`ِ برگشتی که مالِ خودِ
   *                       نویسنده است (همین دسته، یا ثبت‌شده با همین دستگاه/کاربر)، `productId`
   *                       یکی از قلم‌های همان برگشت و |units| نه بیش از مقدارِ آن قلم. هر قلم فقط
   *                       یک اصلاح (اصلاح‌های ثبت‌شده در `taken`).
   *    doc_movement       حرکتِ انبارِ یک سند: `type` یکی از DOC_MOVE_TYPES با علامتِ همان نوع،
   *                       `refId` = فاکتور (sale، sale_cancel) یا قلمِ برگشت (customer_return،
   *                       damage) از همین دسته یا ثبت‌شده، و `productId` یکی از قلم‌های همان سند
   *                       (`saleItems` یا قلمِ برگشت). جمعِ |qty| ِ هر نوع برای هر قلم — حرکت‌های
   *                       ثبت‌شده (`moved`) و پذیرفته‌های پیشینِ همین دسته — نه بیش از مقدارِ قلم.
   *                       `sale_cancel` فقط برای فاکتورِ لغوشده (`cancelled`: همین دسته یا ثبت‌شده).
   *  ردیف (`row`) نمای یک‌دستِ درج است با یکاهای برنامه (v3: Milli/Minor ÷ ۱۰۰۰/۱۰۰، کارِ گردآورنده).
   *  refs = { returns: [{id, docId, productId, qty, own}], sales: [id], taken: [{refId, productId, units}],
   *           saleItems: [{id, saleId, productId, qty}], cancelled: [saleId], moved: [{refId, productId, type, qty}] }
   */
  var RETURN_ADJ_KINDS = ['customer_return', 'damage'];
  /** نوعِ حرکت ⇐ سندی که `refId` به آن اشاره می‌کند */
  var DOC_MOVE_TYPES = { sale: 'sale', sale_cancel: 'sale', customer_return: 'return', damage: 'return' };
  /** نوع‌هایی که مقدارشان منفی است (بیرون رفتن از موجودی)؛ بقیه مثبت */
  var NEGATIVE_KINDS = ['damage', 'sale'];
  var ROW_RULES = { cashier: { warehouseEntries: 'return_adjustment', stockMovements: 'doc_movement' } };

  /** نامِ قاعدهٔ ردیفِ این نقش برای این جدول، یا null (یعنی همهٔ ردیف‌ها، اگر `canWrite`) */
  function rowRule(role, table) {
    if (typeof role !== 'string' || !has(ROW_RULES, role)) return null;
    return typeof table === 'string' && has(ROW_RULES[role], table) ? ROW_RULES[role][table] : null;
  }

  function isObj(v) { return v !== null && typeof v === 'object' && !Array.isArray(v); }
  function isId(v) { return typeof v === 'string' && v !== ''; }
  function num(v) { return typeof v === 'number' && isFinite(v) ? v : null; }
  function signOk(kind, v) { return v !== null && (NEGATIVE_KINDS.indexOf(kind) >= 0 ? v < 0 : v > 0); }
  /** قلم‌های برگشتی که `ref` شناسه یا `docId`ِ آن است */
  function linesOf(refs, ref) {
    var out = [];
    var list = isObj(refs) && Array.isArray(refs.returns) ? refs.returns : [];
    for (var i = 0; i < list.length; i++) {
      var r = list[i];
      if (isObj(r) && isId(r.id) && (r.id === ref || r.docId === ref)) out.push(r);
    }
    return out;
  }
  /** کوچک‌ترین قلمِ آزادی که مقدارِ `need` در آن جا می‌شود (اولی در برابری) */
  function bestFit(lines, need, used) {
    var best = null;
    for (var i = 0; i < lines.length; i++) {
      var q = num(lines[i].qty);
      if (used[lines[i].id] === true || q === null || q < need) continue;
      if (best === null || q < num(best.qty)) best = lines[i];
    }
    return best;
  }
  function fits(lines, need) {
    for (var i = 0; i < lines.length; i++) {
      var q = num(lines[i].qty);
      if (q !== null && q >= need) return true;
    }
    return false;
  }
  function ofProduct(lines, productId) {
    if (!isId(productId)) return [];
    return lines.filter(function (r) { return r.productId === productId; });
  }

  function no(reason) { return { ok: false, reason: reason }; }
  function yes() { return { ok: true, reason: '' }; }

  function checkAdjustment(row, refs, used) {
    if (row.isAdjustment !== true) return no('adjustment');
    var money = ['price', 'cartons', 'perCarton'];
    for (var i = 0; i < money.length; i++) {
      var m = row[money[i]];
      if (m !== undefined && m !== null && m !== 0) return no('price');
    }
    if (RETURN_ADJ_KINDS.indexOf(row.adjKind) < 0) return no('kind');
    var units = num(row.units);
    if (!signOk(row.adjKind, units)) return no('sign');
    if (!isId(row.refId)) return no('ref');
    var lines = linesOf(refs, row.refId);
    if (!lines.length) return no('ref');
    lines = lines.filter(function (r) { return r.own === true; });
    if (!lines.length) return no('owner');
    lines = ofProduct(lines, row.productId);
    if (!lines.length) return no('product');
    var need = Math.abs(units);
    if (!fits(lines, need)) return no('qty');
    var line = bestFit(lines, need, used);
    if (!line) return no('used');
    used[line.id] = true;
    return yes();
  }

  /** ریزِ گرد کردنِ جمعِ اعشاری (۰٫۱ + ۰٫۲)؛ همان در کاتلین */
  var EPS = 1e-9;
  function listOf(refs, k) { return isObj(refs) && Array.isArray(refs[k]) ? refs[k] : []; }
  /** قلم‌هایی که حرکتِ این نوع با این نشانی و این کالا از آن‌ها برمی‌دارد: {key, qty} */
  function moveLines(refs, type, ref, productId) {
    var doc = DOC_MOVE_TYPES[type];
    if (!isId(productId)) return [];
    if (doc === 'sale') {
      var items = listOf(refs, 'saleItems');
      var out = [];
      for (var i = 0; i < items.length; i++) {
        var it = items[i];
        if (isObj(it) && isId(it.id) && it.saleId === ref && it.productId === productId) {
          out.push({ key: type + '\u0000s\u0000' + it.id, qty: num(it.qty) });
        }
      }
      return out;
    }
    return ofProduct(linesOf(refs, ref), productId)
      .map(function (r) { return { key: type + '\u0000r\u0000' + r.id, qty: num(r.qty) }; });
  }
  function room(lines, spent) {
    var free = 0;
    for (var i = 0; i < lines.length; i++) {
      var q = lines[i].qty;
      if (q !== null && q > 0) free += Math.max(0, q - (spent[lines[i].key] || 0));
    }
    return free;
  }
  /** `need` را به ترتیب از قلم‌ها برمی‌دارد (ثبت‌شده‌ها بیش از جا را هم نمی‌گیرند) */
  function spend(lines, need, spent) {
    for (var i = 0; i < lines.length && need > EPS; i++) {
      var q = lines[i].qty;
      if (q === null || q <= 0) continue;
      var take = Math.min(need, Math.max(0, q - (spent[lines[i].key] || 0)));
      spent[lines[i].key] = (spent[lines[i].key] || 0) + take;
      need -= take;
    }
  }
  /** آنچه حرکت‌های ثبت‌شده (`moved`) از قلم‌ها برداشته‌اند */
  function spentLines(refs) {
    var spent = Object.create(null);
    var moved = listOf(refs, 'moved');
    for (var i = 0; i < moved.length; i++) {
      var m = moved[i];
      if (!isObj(m) || typeof m.type !== 'string' || !has(DOC_MOVE_TYPES, m.type) || !isId(m.refId)) continue;
      var q = num(m.qty);
      if (q === null) continue;
      spend(moveLines(refs, m.type, m.refId, m.productId), Math.abs(q), spent);
    }
    return spent;
  }

  function checkMovement(row, refs, spent) {
    var doc = typeof row.type === 'string' && has(DOC_MOVE_TYPES, row.type) ? DOC_MOVE_TYPES[row.type] : null;
    if (!doc) return no('kind');
    var qty = num(row.qty);
    if (!signOk(row.type, qty)) return no('sign');
    if (!isId(row.refId)) return no('ref');
    if (doc === 'sale') {
      if (listOf(refs, 'sales').indexOf(row.refId) < 0) return no('ref');
      if (row.type === 'sale_cancel' && listOf(refs, 'cancelled').indexOf(row.refId) < 0) return no('cancel');
    } else if (!linesOf(refs, row.refId).length) return no('ref');
    var lines = moveLines(refs, row.type, row.refId, row.productId);
    if (!lines.length) return no('product');
    var need = Math.abs(qty);
    if (need > room(lines, spent) + EPS) return no('qty');
    spend(lines, need, spent);
    return yes();
  }

  /** قلم‌هایی که اصلاحِ ثبت‌شده دارند (`refs.taken`): هر اصلاح یک قلم، با همان قاعدهٔ جا شدن */
  function usedLines(refs) {
    var used = Object.create(null);   //  شناسهٔ «__proto__» هم کلیدِ ساده است
    var taken = isObj(refs) && Array.isArray(refs.taken) ? refs.taken : [];
    for (var i = 0; i < taken.length; i++) {
      var t = taken[i];
      if (!isObj(t) || !isId(t.refId)) continue;
      var lines = ofProduct(linesOf(refs, t.refId), t.productId);
      var u = num(t.units);
      var line = bestFit(lines, u === null ? 0 : Math.abs(u), used) || bestFit(lines, 0, used);
      if (line) used[line.id] = true;
    }
    return used;
  }

  /**
   *  داوریِ ردیف‌های یک دسته، به ترتیب (قلمی که اصلاحِ یک ردیف گرفت برای بعدی آزاد نیست).
   *  @param {Array<{table: string, row: Object|null}>} rows  `row` = نمای درجِ تازه؛ null =
   *         ویرایش، حذف، ردیفِ موجود یا شناسهٔ تکراریِ دسته
   *  @returns {Array<{ok: boolean, reason: string}>}  reason: '' | table | not_insert |
   *         adjustment | price | kind | sign | ref | cancel | owner | product | qty | used | rule
   */
  function checkRows(role, rows, refs) {
    var used = usedLines(refs);
    var spent = spentLines(refs);
    var out = [];
    var list = Array.isArray(rows) ? rows : [];
    for (var i = 0; i < list.length; i++) {
      var it = isObj(list[i]) ? list[i] : {};
      if (!canWrite(role, it.table)) { out.push(no('table')); continue; }
      var rule = rowRule(role, it.table);
      if (!rule) { out.push(yes()); continue; }
      if (!isObj(it.row)) { out.push(no('not_insert')); continue; }
      if (rule === 'return_adjustment') out.push(checkAdjustment(it.row, refs, used));
      else if (rule === 'doc_movement') out.push(checkMovement(it.row, refs, spent));
      else out.push(no('rule'));
    }
    return out;
  }

  /** این نقش همین یک ردیف را می‌نویسد؟ — `checkRows` با یک ردیف */
  function canWriteRow(role, table, row, refs) {
    return checkRows(role, [{ table: table, row: row }], refs)[0].ok;
  }

  /** فهرستِ جدول‌های نوشتنیِ یک نقش: «*»، فهرستِ مرتب، یا null برای نقشِ ناشناخته */
  function writable(role) {
    if (typeof role !== 'string' || !has(WRITES, role)) return null;
    var w = WRITES[role];
    return w === '*' ? '*' : w.slice().sort();
  }
  function isCapValue(v) { return typeof v === 'number' && isFinite(v) && v >= 0; }
  function keys() { return Object.keys(CATALOG).sort(); }

  /** حالتِ پیش‌فرضِ یک نقش — یا null برای نقش/مجوزِ ناشناخته */
  function defaultMode(role, key) {
    var spec = CATALOG[key];
    if (!spec || !has(spec.defaults, role)) return null;
    return spec.defaults[role];
  }

  /**
   *  حالت و سقفِ نهایی.
   *  @param {string} role
   *  @param {Array<Object>|null} layers  لایه‌ها به‌ترتیب (نقش، سپس کاربر)
   *  @param {string} key
   *  @returns {{mode: string, caps: Object}}
   */
  function resolve(role, layers, key) {
    var spec = CATALOG[key];
    var base = defaultMode(role, key);
    if (!spec || base === null) return { mode: 'deny', caps: {} };
    if (role === 'owner') return { mode: 'allow', caps: {} };
    var mode = base;
    var caps = {};
    if (spec.edit !== 'none' && layers) {
      for (var i = 0; i < layers.length; i++) {
        var o = layers[i] && has(layers[i], key) ? layers[i][key] : null;
        if (!o || typeof o !== 'object') continue;
        if (MODES.indexOf(o.mode) >= 0) {
          var m = o.mode;
          if (m === 'approve' && !spec.approvable) m = 'deny';
          if (spec.edit === 'restrict' && RANK[m] > RANK[base] && m !== 'approve') m = base;
          mode = m;
        }
        if (o.caps && typeof o.caps === 'object') {
          for (var j = 0; j < spec.caps.length; j++) {
            var name = spec.caps[j];
            if (!has(o.caps, name)) continue;
            var v = o.caps[name];
            if (v === null) delete caps[name];
            else if (isCapValue(v)) caps[name] = v;
          }
        }
      }
    }
    var out = {};
    var names = Object.keys(caps).sort();
    for (var c = 0; c < names.length; c++) out[names[c]] = caps[names[c]];
    return { mode: mode, caps: out };
  }

  /**
   *  تصمیم برای یک کارِ مشخص.
   *  @param {{pct?: number, amount?: number}} [req]
   *  @returns {{decision: string, reason: string, cap?: string, limit?: number}}
   */
  function evaluate(role, layers, key, req) {
    var r = resolve(role, layers, key);
    if (r.mode === 'deny') return { decision: 'deny', reason: 'denied' };
    if (r.mode === 'approve') return { decision: 'approve', reason: 'needs_approval' };
    var spec = CATALOG[key];
    for (var i = 0; i < spec.caps.length; i++) {
      var name = spec.caps[i];
      if (!has(r.caps, name)) continue;
      var value = req ? req[CAP_FIELDS[name]] : undefined;
      if (typeof value === 'number' && isFinite(value) && value > r.caps[name]) {
        return { decision: spec.approvable ? 'approve' : 'deny', reason: 'over_cap', cap: name, limit: r.caps[name] };
      }
    }
    return { decision: 'allow', reason: 'ok' };
  }

  /*
   *  درصدِ تخفیف برای سنجشِ سقف، به «صدمِ درصد» (عددِ صحیح؛ ۵٪ = ۵۰۰).
   *  برنامه‌ها تخفیف را تا افغانیِ کامل گرد می‌کنند (۵٪ِ ۳۳۳ می‌شود ۱۷)، پس
   *  تا نیم افغانی (۵۰ Minor) بخشیده می‌شود تا ۵٪ِ درست «بیش از ۵٪» نشود.
   *  جمعِ صفر یا منفی با تخفیفِ مثبت ⇐ ۱۰۰٪.
   */
  var ROUNDING_MINOR = 50;
  function discountBp(discountMinor, totalMinor) {
    var d = Number(discountMinor);
    var t = Number(totalMinor);
    if (!isFinite(d) || d <= 0) return 0;
    if (!isFinite(t) || t <= 0) return 10000;
    var forgiven = Math.max(0, Math.floor(d) - ROUNDING_MINOR);
    return Math.min(10000, Math.floor(forgiven * 10000 / Math.floor(t)));
  }
  function discountPct(discountMinor, totalMinor) {
    return discountBp(discountMinor, totalMinor) / 100;
  }

  /** بی درخواستِ عددی: آیا دکمه دیده شود و بی‌واسطه کار کند */
  function allows(role, layers, key) {
    return evaluate(role, layers, key, null).decision === 'allow';
  }

  /** نقشهٔ کاملِ همهٔ مجوزهای هسته برای یک نقش */
  function effective(role, layers) {
    var out = {};
    var ks = keys();
    for (var i = 0; i < ks.length; i++) out[ks[i]] = resolve(role, layers, ks[i]);
    return out;
  }

  /**
   *  سنجشِ یک ردیفِ لایه پیش از ذخیره — null یعنی درست است.
   *  @returns {null|string} کدِ خطا
   */
  function validate(role, key, o) {
    var spec = CATALOG[key];
    if (!spec) return 'unknown_permission';
    if (role === 'owner') return 'owner_fixed';
    var base = defaultMode(role, key);
    if (base === null) return 'unknown_role';
    if (spec.edit === 'none') return 'not_editable';
    if (!o || typeof o !== 'object' || Array.isArray(o)) return 'bad_override';
    var fields = Object.keys(o);
    for (var i = 0; i < fields.length; i++) {
      if (fields[i] !== 'mode' && fields[i] !== 'caps') return 'bad_override';
    }
    if (has(o, 'mode')) {
      if (MODES.indexOf(o.mode) < 0) return 'bad_mode';
      if (o.mode === 'approve' && !spec.approvable) return 'not_approvable';
      if (spec.edit === 'restrict' && RANK[o.mode] > RANK[base] && o.mode !== 'approve') return 'exceeds_role';
    }
    if (has(o, 'caps')) {
      if (!o.caps || typeof o.caps !== 'object' || Array.isArray(o.caps)) return 'bad_cap';
      var names = Object.keys(o.caps);
      for (var j = 0; j < names.length; j++) {
        var n = names[j];
        if (spec.caps.indexOf(n) < 0) return 'unknown_cap';
        var v = o.caps[n];
        if (v !== null && !isCapValue(v)) return 'bad_cap';
        if (n === 'maxPct' && v !== null && v > 100) return 'bad_cap';
      }
    }
    return null;
  }

  return {
    VERSION: VERSION,
    MODES: MODES,
    ROLES: ROLES,
    CAP_FIELDS: CAP_FIELDS,
    CATALOG: CATALOG,
    keys: keys,
    defaultMode: defaultMode,
    resolve: resolve,
    evaluate: evaluate,
    allows: allows,
    effective: effective,
    validate: validate,
    WRITES: WRITES,
    canWrite: canWrite,
    writable: writable,
    RETURN_ADJ_KINDS: RETURN_ADJ_KINDS,
    DOC_MOVE_TYPES: DOC_MOVE_TYPES,
    rowRule: rowRule,
    checkRows: checkRows,
    canWriteRow: canWriteRow,
    ROUNDING_MINOR: ROUNDING_MINOR,
    discountBp: discountBp,
    discountPct: discountPct,
  };
});
