'use strict';
/**
 * تورِ ایمنیِ سرور: «ساختِ پیش از A00 فیلدی را که نمی‌شناسد null نمی‌کند» — A00 (#341).
 *
 * ── مشکل ─────────────────────────────────────────────────────────────
 * برنامهٔ دکان دفتر را از درِ مدلِ خودش می‌خواند (`ignoreUnknownKeys`). تا پیش
 * از A00، `LedgerDiff.changedFields` هر کلیدی را که در سایه بود و در دفتر نه
 * «پاک‌شده» می‌دید و `null` ِ صریح می‌فرستاد. پس ساختِ قدیمی‌تر با نخستین
 * ذخیره پس از Snapshot (گوشیِ نو، «بازیابی از سرور») یا هر ویرایشِ آن ردیف،
 * فیلدی را که ساختِ تازه‌تر یا خودِ سرور نوشته بود (`payments`، `approval`،
 * `orderId`، …) روی سرور پاک می‌کرد. ساخت‌های A00 به بعد هرگز چنین `null`ی
 * نمی‌فرستند، ولی ساخت‌های پیشین هنوز روی گوشی‌ها و کامپیوترها نصب‌اند.
 *
 * ── راه ──────────────────────────────────────────────────────────────
 * `PRE_A00`: فهرستِ **منجمدِ** فیلدهایی که ساخت‌های پیش از A00 می‌شناسند، هر
 * کدام با نخستین ساختِ **منتشرشده** (اجرای موفقِ `tohid-native.yml` /
 * `tohid-desktop.yml` روی main) که آن را دارد؛ `ALL` یعنی از نخستین ساخت
 * (گوشی 3.2.274، کامپیوتر 1.0.13). `null` ِ صریح از ساختِ پیش از A00 برای
 * کلیدی که آن ساخت نمی‌شناسد کنار می‌رود — فقط همان کلید؛ بقیهٔ op و اثرِ
 * انگشتش (hash دوباره ساخته می‌شود) دست نمی‌خورد. opی که جز آن چیزی نداشت به
 * دفتر نمی‌رسد (ویرایشِ تهی ردیفِ پاک‌شده را زنده می‌کرد) و پاسخش «applied» ِ
 * بی‌اثر است تا برنامه آن را از صف بردارد. این برای فیلدِ ناتهیِ مدل‌های
 * تازه‌تر (`active`، `unitFactor`) حیاتی است: `null` روی سرور `fromJson` ِ ساختِ
 * تازه را در Pull می‌انداخت و همگام‌سازی می‌ایستاد.
 *
 * کی از تور می‌گذرد (هیچ کاری نمی‌شود) — نشانِ صریح، نه حدس از شمارهٔ ساخت:
 *   • `caps: ["a00"]` در بدنهٔ push (v1 و v3): ساختِ A00 به بعد (`LedgerDiff.SYNC_CAPS`)
 *     کلیدِ ناشناخته را null نمی‌کند؛ ساختِ پیشین هرگز این نشان را نمی‌فرستد. پس فیلدِ
 *     تازه‌ای که پس از A00 به مدل بیاید هیچ ردیفی این‌جا لازم ندارد و `PRE_A00` منجمد است؛
 *   • `client: "web"` (یا `caps: ["raw"]`): موتورهای همگام‌سازیِ وب (`license/sync-engine.js`،
 *     `license/sync-v3-engine.js`) ردیفِ JSON ِ خام را نگه می‌دارند و هرگز null ِ کهنه
 *     نمی‌فرستند؛ `null` شان یعنی کاربر پاک کرد (سقفِ اعتبار) و باید بنشیند. صفحهٔ وبی
 *     که پیش از این نشان در حافظهٔ مرورگر مانده با `X-App-Version` ِ بی شمارهٔ ساخت
 *     (`3.2` از `<meta tohid-app-version>`) شناخته می‌شود؛
 *   • عددِ اصلیِ ۲ (پوستهٔ وبِ قدیمی که همان کدِ وب را اجرا می‌کند) و خطِ ناشناختهٔ
 *     بالاتر (گوشی 4.x به بعد).
 * ساختِ آزمایشیِ `3.2.0`، نسخهٔ نخوانا و نبودِ سرآیند «قدیمی‌ترین» حساب می‌شوند.
 *
 * ⚠️ بدترین خطای این تور «مقدارِ کهنه می‌ماند» است، هرگز «داده پاک می‌شود».
 * ⛔ `sync-v1.js` (مشترک با پمپ) دست نمی‌خورد: v1 از درِ میان‌افزارِ فقط‌دکانِ
 *    `routes/sync-v1.js` (پس از `v1Guard`) و v3 در خودِ `routes/sync-v3.js`.
 * ⛔ `PRE_A00` منجمد است (`test/newer-fields-baseline.test.js`): فیلدِ تازه این‌جا
 *    نمی‌آید؛ تنها دلیلِ دست زدن، اصلاحِ کفِ یک فیلدِ پیش از A00 است.
 */
const compat = require('./app-compat');

/** گوشی (`android-native`، `3.2.N`) و کامپیوتر (`desktop`، `1.0.N`) — `N` شمارهٔ اجرای CI */
const LINES = Object.freeze({ 3: 'phone', 1: 'desktop' });

/** نشانِ ساختِ A00 به بعد در `caps` ِ بدنهٔ push — همان `LedgerDiff.SYNC_CAPS` */
const CAP_A00 = 'a00';

const ALL = null;
const since = (phone, desktop) => Object.freeze({ phone, desktop });
const f = (o) => Object.freeze(o);

/* eslint-disable max-len */
/** منجمد — مدل‌های دفترِ ساخت‌های پیش از A00 (`android-native/.../data/Models.kt` روی main ِ #341) */
const PRE_A00 = Object.freeze({
  products: f({ id: ALL, name: ALL, category: ALL, unit: ALL, purchasePrice: ALL, salePrice: ALL, wholesalePrice: ALL, minStock: ALL, notes: ALL, barcodes: ALL, photo: ALL, createdAt: ALL, subcategory: since('3.2.300', '1.0.39'), supplierId: since('3.2.300', '1.0.39'), active: since('3.2.300', '1.0.39'), saleUnit: since('3.2.300', '1.0.39'), unitFactor: since('3.2.300', '1.0.39') }),
  warehouseEntries: f({ id: ALL, productId: ALL, cartons: ALL, perCarton: ALL, units: ALL, unit: ALL, price: ALL, date: ALL, notes: ALL, isAdjustment: ALL, purchaseId: ALL, createdAt: ALL, expiryDate: since('3.2.280', '1.0.19'), batch: since('3.2.280', '1.0.19'), productionDate: since('3.2.300', '1.0.39') }),
  sales: f({ id: ALL, invoiceNumber: ALL, total: ALL, discountType: ALL, discountValue: ALL, discount: ALL, finalTotal: ALL, paymentMethod: ALL, debtorId: ALL, paidAmount: ALL, remaining: ALL, status: ALL, debtGiven: ALL, debtSettled: ALL, createdAt: ALL, date: ALL, syncStatus: ALL, cancelReason: since('3.2.300', '1.0.39'), seller: since('3.2.300', '1.0.39'), series: since('3.2.405', '1.0.152'), channel: since('3.2.522', '1.0.366') }),
  saleItems: f({ id: ALL, saleId: ALL, productId: ALL, name: ALL, quantity: ALL, unitPrice: ALL, purchasePrice: ALL, totalPrice: ALL, returnedQty: ALL }),
  returns: f({ id: ALL, saleId: ALL, saleItemId: ALL, productId: ALL, quantity: ALL, amount: ALL, reason: ALL, date: ALL, createdAt: ALL, channel: since('3.2.615', '1.0.366') }),
  debtors: f({ id: ALL, name: ALL, phone: ALL, notes: ALL, createdAt: ALL, creditLimit: since('3.2.466', '1.0.217') }),
  transactions: f({ id: ALL, debtorId: ALL, type: ALL, amount: ALL, date: ALL, notes: ALL, createdAt: ALL, dueDate: since('3.2.466', '1.0.217') }),
  expenses: f({ id: ALL, title: ALL, category: ALL, amount: ALL, date: ALL, notes: ALL, createdAt: ALL }),
  suppliers: f({ id: ALL, name: ALL, phone: ALL, address: ALL, notes: ALL, createdAt: ALL }),
  purchases: f({ id: ALL, productId: ALL, supplierId: ALL, quantity: ALL, unit: ALL, purchasePrice: ALL, totalAmount: ALL, date: ALL, notes: ALL, paidAmount: ALL, debt: ALL, warehouseEntryId: ALL, createdAt: ALL, invoiceNo: since('3.2.337', '1.0.88') }),
  supplierPayments: f({ id: ALL, supplierId: ALL, amount: ALL, date: ALL, notes: ALL, createdAt: ALL }),
  stockMovements: f({ id: ALL, productId: ALL, type: ALL, qty: ALL, date: ALL, notes: ALL, refId: ALL, createdAt: ALL }),
  priceHistory: f({ id: ALL, productId: ALL, oldPrice: ALL, newPrice: ALL, date: ALL, createdAt: ALL }),
  auditLog: f({ id: ALL, type: ALL, date: ALL, refId: ALL, notes: ALL, createdAt: ALL, amount: since('3.2.300', '1.0.39') }),
});
/* eslint-enable max-len */

const hasOwn = (o, k) => Object.prototype.hasOwnProperty.call(o, k);
const isPlainObject = (v) => v !== null && typeof v === 'object' && !Array.isArray(v);

/**
 * برنامهٔ پشتِ push: اول نشانِ صریحِ بدنه (`caps`، `client`)، بعد `X-App-Version`.
 * @returns {{bypass: string} | {line: string|null, v: string}}
 */
function clientOf(version, { caps = [], client = '' } = {}) {
  const list = Array.isArray(caps) ? caps.map(String) : [];
  if (list.includes(CAP_A00)) return { bypass: 'a00' };
  if (String(client) === 'web' || list.includes('raw')) return { bypass: 'web' };
  const v = compat.valid(version);
  if (!v) return { line: null, v: '' };                       // نبود یا نخوانا ⇒ قدیمی‌ترین
  const parts = v.split('.');
  if (parts.length === 2) return { bypass: 'web-cached' };     // صفحهٔ وبِ پیش از نشان: بی شمارهٔ ساخت
  const major = Number(parts[0]);
  const line = LINES[major];
  if (line) return { line, v };
  if (major === 2) return { bypass: 'web-wrapper' };           // پوستهٔ وبِ قدیمی: همان کدِ وب
  return major > 3 ? { bypass: 'newer-line' } : { line: null, v };
}

/** آیا ساختِ پیش از A00 ِ [client] کلیدِ [key] ِ جدولِ [table] را می‌شناسد؟ */
function knows(client, table, key) {
  if (client.bypass) return true;
  const t = PRE_A00[table];
  if (!t || !hasOwn(t, key)) return false;                     // فیلدی که پس از A00 آمد، یا ناشناخته
  const floor = t[key];
  if (!floor) return true;                                     // از نخستین ساخت
  return Boolean(client.line) && compat.compare(client.v, floor[client.line]) >= 0;
}

/** نامِ v3 ⇒ نامِ دفتر: پسوندِ واحد (`creditLimitMinor`، `qtyMilli`) برداشته می‌شود */
function legacyKey(table, key, shape) {
  const t = PRE_A00[table];
  if (shape !== 'v3' || !t || hasOwn(t, key)) return key;
  const m = /^(.+)(Minor|Milli)$/.exec(key);
  return m && hasOwn(t, m[1]) ? m[1] : key;
}

/**
 * پالایشِ یک دسته. `shape`: `v1` (همان شکلِ v1 — و صفِ v2 ِ راهِ v3) یا `v3`؛ `caps` و
 * `client` همان فیلدهای بدنهٔ push.
 * @returns {{ops:Array, length:number, dropped:Map<number,object>, trimmed:Map<string,string[]>, client:object}}
 */
function filterOps(ops, { version = '', shape = 'v1', caps = [], client: who = '' } = {}) {
  const client = clientOf(version, { caps, client: who });
  const res = { ops, length: Array.isArray(ops) ? ops.length : 0, dropped: new Map(), trimmed: new Map(), client };
  if (client.bypass || !Array.isArray(ops) || !ops.length) return res;
  const { tableOf, hashOf } = require('./sync-v1');
  const out = [];
  let touched = false;
  ops.forEach((raw, i) => {
    if (!isPlainObject(raw) || !isPlainObject(raw.fields)) { out.push(raw); return; }
    const rawTable = raw.table ?? raw.table_name ?? raw.tableName;
    const table = tableOf('shop', rawTable);
    const kind = shape === 'v3' ? raw.kind : (raw.type ?? raw.op_type ?? raw.opType);
    if (!table || (kind !== 'insert' && kind !== 'update')) { out.push(raw); return; }
    const stale = Object.keys(raw.fields)
      .filter((k) => raw.fields[k] === null && !knows(client, table, legacyKey(table, k, shape)));
    if (!stale.length) { out.push(raw); return; }
    touched = true;
    const fields = { ...raw.fields };
    for (const k of stale) delete fields[k];
    const opId = String(raw.op_id ?? raw.opId ?? '');
    if (!Object.keys(fields).length) {
      res.dropped.set(i, { op_id: opId, status: 'applied', server_seq: null, ignored: stale, reason: 'stale_null_ignored' });
      return;
    }
    res.trimmed.set(opId, stale);
    const next = { ...raw, fields };
    //  اثرِ انگشتِ v1 روی همان نامِ جدولی است که برنامه فرستاده (`validateOp`)
    if (shape !== 'v3' && raw.hash) {
      next.hash = hashOf({ table: rawTable, row_id: raw.row_id ?? raw.rowId ?? raw.id, type: kind, fields });
    }
    out.push(next);
  });
  if (touched) res.ops = out;
  return res;
}

/** پاسخ‌های دستهٔ پالایش‌شده ⇒ به ترتیبِ دستهٔ اصلی، با `ignored` برای opِ کوتاه‌شده */
function mergeResults(fl, results) {
  if (!Array.isArray(results) || (!fl.dropped.size && !fl.trimmed.size)) return results;
  const rest = results.map((r) => (r && fl.trimmed.has(r.op_id) ? { ...r, ignored: fl.trimmed.get(r.op_id) } : r));
  const merged = [];
  for (let i = 0; i < fl.length; i++) merged.push(fl.dropped.has(i) ? fl.dropped.get(i) : rest.shift());
  return merged.filter(Boolean).concat(rest);
}

/** میان‌افزارِ `POST /sync/v1/push` — فقط حسابِ دکان، پس از `v1Guard` (همان الگو) */
function v1NewerFields(req, res, next) {
  const ctx = req.sync;
  if (!ctx || ctx.app !== 'shop' || ctx.accountKind !== 'shop') return next();
  try {
    const fl = filterOps(req.body?.ops, {
      version: String(req.headers['x-app-version'] || '').slice(0, 32), shape: 'v1',
      caps: req.body?.caps, client: req.body?.client,
    });
    if (!fl.dropped.size && !fl.trimmed.size) return next();
    req.body.ops = fl.ops;
    const send = res.json.bind(res);
    res.json = (body) => {
      if (body && body.ok && Array.isArray(body.results)) body.results = mergeResults(fl, body.results);
      return send(body);
    };
    next();
  } catch (err) { next(err); }
}

module.exports = { PRE_A00, CAP_A00, LINES, clientOf, knows, filterOps, mergeResults, v1NewerFields };
