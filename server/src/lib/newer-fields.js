'use strict';
/**
 * تورِ ایمنیِ سرور: «برنامهٔ قدیمی فیلدِ تازه را null نمی‌کند» — A00.
 *
 * ── مشکل ─────────────────────────────────────────────────────────────
 * برنامهٔ دکان دفتر را از درِ مدلِ خودش می‌خواند (`ignoreUnknownKeys`). تا پیش
 * از A00، `LedgerDiff.changedFields` هر کلیدی را که در سایه بود و در دفتر نه
 * «پاک‌شده» می‌دید و `null` ِ صریح می‌فرستاد. پس نسخهٔ قدیمی‌تر با نخستین
 * ذخیره پس از Snapshot (گوشیِ نو، «بازیابی از سرور») یا هر ویرایشِ آن ردیف،
 * فیلدی را که نسخهٔ تازه‌تر یا خودِ سرور نوشته بود (`payments`، `approval`،
 * `orderId`، …) روی سرور پاک می‌کرد. برنامه از A00 این کار را نمی‌کند، ولی
 * نسخه‌های نصب‌شدهٔ پیشین هنوز روی گوشی‌ها و کامپیوترها هستند.
 *
 * ── راه ──────────────────────────────────────────────────────────────
 * `REGISTRY`: برای هر فیلدِ «تازه»، نخستین نسخه از هر خطِ برنامه که آن را
 * می‌شناسد (کلید = عددِ اصلیِ نسخه: گوشی `3.2.N`، کامپیوتر `1.0.N`؛ `N` شمارهٔ
 * ساختِ CI است). `null` ِ صریح برای چنین فیلدی از برنامه‌ای که `X-App-Version`
 * اش پایین‌تر است کنار می‌رود — فقط همان کلید؛ بقیهٔ op و اثرِ انگشتش (hash
 * دوباره ساخته می‌شود) دست نمی‌خورد. opی که جز آن چیزی نداشت به `sync-v1.js`
 * نمی‌رسد (ویرایشِ تهی ردیفِ پاک‌شده را زنده می‌کرد) و پاسخش «applied» ِ بی‌اثر
 * است تا برنامه آن را از صف بردارد.
 *
 *   `null` به‌جای نسخه ⇒ هیچ نسخهٔ منتشرشده‌ای آن را نمی‌شناسد ⇒ `null` ِ
 *   هیچ برنامه‌ای پذیرفته نمی‌شود. نسخهٔ نخوانا یا بی‌سرآیند ⇒ «نمی‌شناسد».
 *
 * ⚠️ بدترین خطای این تور «مقدارِ کهنه می‌ماند» است، هرگز «داده پاک می‌شود».
 * ⛔ `sync-v1.js` (مشترک با پمپ) دست نمی‌خورد: میان‌افزارِ فقط‌دکانِ همان درِ
 *    `v1Guard` (`routes/sync-v1.js`). پمپ بی هیچ کاری از آن رد می‌شود.
 *
 * ── قاعده برای هر PR که فیلد به مدلِ برنامه می‌افزاید ──────────────────
 * همان فیلد را با `null` این‌جا بنویسید؛ پس از نخستین ساختِ `main` که آن را
 * دارد، شمارهٔ همان ساخت (گوشی و کامپیوتر) جای `null` می‌نشیند.
 */
const compat = require('./app-compat');

/** گوشی (`android-native`، `3.2.N`) و کامپیوتر (`desktop`، `1.0.N`) */
const since = (phone, desktop) => Object.freeze({ 3: phone, 1: desktop });

/*
 * شمارهٔ ساخت = نخستین اجرای موفقِ `tohid-native.yml` / `tohid-desktop.yml` روی
 * main که کامیتِ آن فیلد را دارد (اگر نبود، نخستین اجرای پس از آن).
 */
const REGISTRY = Object.freeze({
  shop: Object.freeze({
    sales: Object.freeze({
      series: since('3.2.405', '1.0.152'),          // F0-03 (705eae7)
      channel: since('3.2.522', '1.0.333'),         // ORD-20 (49d1a91)
      orderId: null, orderNo: null,                 // فاکتورِ سفارشِ اینترنتی (ORD-20)
      approval: null,                               // برگهٔ تأیید (#283/#284)
      payments: null, tenderedMinor: null, changeMinor: null, // پرداختِ ترکیبی (#286)
    }),
    saleItems: Object.freeze({ lotId: null, locationId: null }), // #315
    returns: Object.freeze({
      channel: since('3.2.582', '1.0.333'),         // ORD-20 (380d223)
      userId: null,                                 // #315
      docId: null, disposition: null, refund: null, toAccount: null, toCredit: null, // #318
      creditTo: null, unitPrice: null, by: null, approvedBy: null, exchangeSaleId: null,
    }),
    debtors: Object.freeze({ creditLimit: since('3.2.466', '1.0.217') }), // CRM-03 (e577c9e)
    warehouseEntries: Object.freeze({ landedCost: null, locationId: null, userId: null, adjKind: null, lotId: null }), // #315
    purchases: Object.freeze({ landedCost: null }), // #315
    stockMovements: Object.freeze({ userId: null, reason: null, locationId: null, toLocationId: null, lotId: null }), // #315
  }),
});

/** آیا برنامه با نسخهٔ [version] فیلدی با این [floors] را می‌شناسد؟ */
function knows(version, floors) {
  if (!floors) return false;
  const v = compat.valid(version);
  if (!v) return false;
  const floor = floors[Number(v.split('.')[0])];
  return Boolean(floor) && compat.compare(v, floor) >= 0;
}

const isPlainObject = (v) => v !== null && typeof v === 'object' && !Array.isArray(v);

/**
 * کلیدهای `null` ِ کهنهٔ یک op. ورودی همان opِ خام است (نامِ جدول هر شکلی که
 * برنامه فرستاده؛ `table` نامِ قانونیِ آن).
 * @returns {string[]} کلیدهایی که باید کنار بروند
 */
function staleNulls(app, table, fields, version) {
  const reg = REGISTRY[app]?.[table];
  if (!reg || !isPlainObject(fields)) return [];
  const out = [];
  for (const [k, v] of Object.entries(fields)) {
    if (v !== null || !Object.prototype.hasOwnProperty.call(reg, k)) continue;
    if (!knows(version, reg[k])) out.push(k);
  }
  return out;
}

/**
 * میان‌افزارِ `POST /sync/v1/push` — فقط حسابِ دکان، پس از `v1Guard`.
 * opها را پیش از `sync-v1.js` پالایش می‌کند و پاسخ را دوباره به ترتیبِ اصلی
 * برمی‌گرداند (همان الگوی `v1Guard`).
 */
function v1NewerFields(req, res, next) {
  const ctx = req.sync;
  if (!ctx || ctx.app !== 'shop' || ctx.accountKind !== 'shop') return next();
  try {
    const ops = Array.isArray(req.body?.ops) ? req.body.ops : null;
    if (!ops || !ops.length) return next();
    const version = String(req.headers['x-app-version'] || '').slice(0, 32);
    const { validateOp, hashOf } = require('./sync-v1');
    const dropped = new Map();                 // i ⇒ پاسخِ بی‌اثر
    const trimmed = new Map();                 // op_id ⇒ کلیدهای کنارگذاشته
    const out = [];
    ops.forEach((raw, i) => {
      const v = validateOp('shop', raw);
      if (!v.ok || v.op.type === 'delete') { out.push(raw); return; }
      const stale = staleNulls('shop', v.op.table, v.op.fields, version);
      if (!stale.length) { out.push(raw); return; }
      const fields = { ...v.op.fields };
      for (const k of stale) delete fields[k];
      const opId = String(v.op.op_id);
      if (!Object.keys(fields).length) {
        dropped.set(i, { op_id: opId, status: 'applied', server_seq: null, ignored: stale, reason: 'stale_null_ignored' });
        return;
      }
      trimmed.set(opId, stale);
      //  اثرِ انگشت روی همان نامِ جدولی است که برنامه فرستاده (`validateOp`)
      const rawTable = raw.table ?? raw.table_name ?? raw.tableName;
      out.push({
        ...raw, fields,
        ...(raw.hash ? { hash: hashOf({ table: rawTable, row_id: v.op.row_id, type: v.op.type, fields }) } : {}),
      });
    });
    if (!dropped.size && !trimmed.size) return next();
    req.body.ops = out;
    const send = res.json.bind(res);
    res.json = (body) => {
      if (body && body.ok && Array.isArray(body.results)) {
        const rest = body.results.map((r) => (r && trimmed.has(r.op_id) ? { ...r, ignored: trimmed.get(r.op_id) } : r));
        const merged = [];
        for (let i = 0; i < ops.length; i++) merged.push(dropped.has(i) ? dropped.get(i) : rest.shift());
        body.results = merged.filter(Boolean).concat(rest);
      }
      return send(body);
    };
    next();
  } catch (err) { next(err); }
}

module.exports = { REGISTRY, knows, staleNulls, v1NewerFields };
