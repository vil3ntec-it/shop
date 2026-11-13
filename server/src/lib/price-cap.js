'use strict';
/**
 * ⛔ STF-03 — سقفِ تغییرِ قیمتِ ردیف و «تخفیفِ پنهان» در سرور (پرچمِ خاموشِ `shop.price_cap`).
 *
 * تا امروز سرور فقط تخفیفِ نوشتهٔ فاکتور (`discount`) را با سقفِ `sales.discount` می‌سنجید؛ قیمتِ هر ردیف را با فهرست
 * نمی‌سنجید (`LinePricing`: «سنجش در صندوق است»)، پس کارمندِ بی تخفیف قیمتِ ردیف را پایین می‌آورد یا «جمعِ نهایی» را
 * کمتر از «جمع − تخفیف» می‌فرستاد و هیچ سقفی نمی‌دید. با پرچمِ روشن، سرور با **همان قاعدهٔ صندوق** (`sale-caps.js` =
 * `core/perm/SaleCaps.kt`، بردارِ `sale-caps-v1.json`) و قیمتِ کالایی که **خودش** دارد می‌سنجد:
 *   sales.discount  تخفیف + کاهشِ ردیف‌ها زیرِ فهرست + شکافِ جمعِ نهایی، روی جمع + کاهش‌ها (همان `LinePricing.basis`)
 *   sales.price     بیشترین فاصلهٔ یک ردیف از فهرست (بالا یا پایین)، سقفِ `maxPct`
 *   sales.free_line قلمِ آزاد (بی کارتِ کالا، یا کالایی که سرور ندارد) با بیشترین قیمتِ واحد، سقفِ `maxAmount` — تصمیمِ ۱-۱۱ (۲)
 * قیمتِ عمده فقط وقتی «قیمتِ فهرست» است که مشتریِ فاکتور روی **همین سرور** لیستِ پیش‌فرضِ عمده دارد (`Debtor.priceList`)؛
 * وگرنه فروش به قیمتِ عمده کاهشِ ردیف (تخفیفِ پنهان) است — تصمیمِ ۱-۱۱ (۳). ادعای صندوق دربارهٔ لیست پذیرفته نمی‌شود.
 * تصمیم همان سه‌حالتهٔ `staff-approval.judge`؛ «نیازمند تأیید» با برگهٔ امضاشده یا «درخواستِ تأیید» ِ STF-02:
 *   تخفیف ⇒ همان `approval` ِ فاکتور؛ قیمت ⇒ `priceApproval` (op یا فیلد)، یا `approval` اگر خودش `sales.price` است؛
 *   قلمِ آزاد ⇒ `freeLineApproval` (op یا فیلد)، یا `approval` اگر خودش `sales.free_line` است.
 *
 *   v3   درجِ `sales`: ردیف‌های همین دسته (`batchOf`) + ردیف‌های نشستهٔ همان فاکتور. قلمِ دیرآمده (`saleItems` ِ فاکتوری
 *        که سرش در دسته‌ای پیش‌تر نشست): جمعِ تازهٔ همان فاکتور؛ تأییدِ پیشینِ همان سند (`shop_approvals`) اگر بپوشاند بس است.
 *   راهِ قدیم (`sync.pushChanges`): فاکتور یا قلمِ تازه ⇒ همان سنجش؛ برگه نمی‌برد ⇒ ۴۰۳ و کلِ دسته برمی‌گردد.
 *   v1   ⛔ نه (`lib/sync-v1.js` ِ مشترک با پمپ دست نمی‌خورد؛ سند در STATUS).
 *
 * قیمتِ فهرست از ردیفِ کالای همین سرور پیش از همین op (تغییرِ قیمتِ کالا در همان دسته فاکتور را «پاک» نمی‌کند).
 * مصرفِ امتیازِ مشتری (PRM-13) قیمتِ ردیف‌ها را پایین می‌آورد: مبلغِ ردیفِ `loyalty_spend` ِ همان فاکتور (همان دسته یا نشسته)
 * پیش از سنجش برمی‌گردد (`sale-caps.loyaltyShares`). ⚠️ این‌جا اعلامِ صندوق پذیرفته است؛ سنجشِ آن با تعریف‌های دکان فقط «بررسی»
 * است (`lib/promo-loyalty-review.js`، پرچمِ خاموشِ `shop.promo_loyalty_review`).
 * پرچمِ خاموش (پیش‌فرض) ⇒ `undefined` و صدازننده همان راهِ امروز. صاحبِ دکان هرگز سنجیده نمی‌شود.
 */
const caps = require('./sale-caps');

const FLAG = caps.PRICE_FLAG;
const PERM = caps.PRICE;
const isObj = (v) => v !== null && typeof v === 'object' && !Array.isArray(v);
const str = (v) => (typeof v === 'string' ? v : '');

/** پرچمِ دکانِ همین درخواست — یک بار برای هر دسته */
function on(ctx, run = null) {
  if (!ctx || !ctx.accountId || ctx.role === 'owner') return Promise.resolve(false);
  if (ctx._priceCap === undefined) ctx._priceCap = require('./orders').flagOf(ctx.accountId, FLAG, run);
  return ctx._priceCap;
}

/** POS-37 — پرچمِ `shop.sale_tax` ِ همین دکان (مالیاتِ ثبت‌شده در پایهٔ تخفیف)؛ یک بار برای هر دسته */
function taxOn(ctx, run = null) {
  if (!ctx || !ctx.accountId) return Promise.resolve(false);
  if (ctx._saleTax === undefined) ctx._saleTax = require('./orders').flagOf(ctx.accountId, caps.TAX_FLAG, run);
  return ctx._saleTax;
}

/** این op به قیمت/تخفیفِ فاکتور دست می‌زند؟ (بی پرس‌وجو) */
function touchesV3(op) {
  return isObj(op) && op.kind === 'insert' && (op.table === 'sales' || op.table === 'saleItems') && isObj(op.fields);
}

/** v3: پرچم فقط وقتی دسته فاکتور یا قلم دارد */
async function onUnits(ctx, units) {
  return units.some((u) => u.items.some((it) => it.ops.some(touchesV3))) && on(ctx);
}

/** نمایهٔ دسته: سرهای فاکتور و قلم‌های هر فاکتور (همه یا هیچِ واحد بعداً؛ این‌جا فقط دیدن) */
function batchOf(units) {
  const headers = new Set();
  const lines = new Map();
  const loyalty = new Map();
  for (const u of units) for (const it of u.items) for (const op of it.ops) {
    if (isObj(op) && op.kind === 'insert' && op.table === 'auditLog' && isObj(op.fields)
      && op.fields.type === caps.LOYALTY_SPEND && str(op.fields.refId)) {
      loyalty.set(op.fields.refId, (loyalty.get(op.fields.refId) || 0) + Math.max(0, Math.round(Number(op.fields.amountMinor) || 0)));
      continue;
    }
    if (!touchesV3(op)) continue;
    if (op.table === 'sales') { headers.add(op.row_id); continue; }
    const sid = str(op.fields.saleId);
    if (!sid) continue;
    if (!lines.has(sid)) lines.set(sid, new Map());
    lines.get(sid).set(op.row_id, op.fields);
  }
  return { headers, lines, loyalty };
}

/** برگهٔ قیمت: `priceApproval` ِ op یا فیلد؛ بی نیازِ تخفیف همان `approval` هم اگر خودش `sales.price` است */
function slipOf(op, withDiscount, { field = 'priceApproval', perm = PERM } = {}) {
  if (isObj(op[field])) return op[field];
  if (isObj(op.fields) && isObj(op.fields[field])) return op.fields[field];
  if (withDiscount) return null;
  const s = require('./staff-approval').slipOf(op);
  return s && s.perm === perm ? s : null;
}

/** برگهٔ هر نیاز: تخفیف ⇒ `approval`؛ قیمت ⇒ `priceApproval`؛ قلمِ آزاد ⇒ `freeLineApproval` (تصمیمِ ۱-۱۱ (۲)) */
function slipFor(op, perm, withDiscount) {
  if (perm === caps.DISCOUNT) return require('./staff-approval').slipOf(op);
  if (perm === caps.FREE_LINE) return slipOf(op, withDiscount, { field: 'freeLineApproval', perm: caps.FREE_LINE });
  return slipOf(op, withDiscount);
}

const needOf = (n) => ({
  perm: n.perm, doc: n.doc, req: caps.req(n),
  ...(n.pctBp !== null ? { pct_bp: n.pctBp } : {}), ...(n.amount !== null ? { amount: n.amount } : {}),
});

// ---------- خواندنِ سرور ----------

async function v3Rows(c, ctx, table, where, params) {
  return (await c.query(
    `SELECT row_id, data FROM sync_v3_rows WHERE app=$1 AND account_kind=$2 AND account_id=$3 AND table_name=$4
        AND deleted_at IS NULL AND ${where}`,
    [ctx.app || 'shop', ctx.accountKind || 'shop', ctx.accountId, table, ...params]
  )).rows;
}

/** قیمتِ کالاهای این ردیف‌ها (v3) */
async function refsV3(c, ctx, list) {
  const ids = [...new Set(list.map((l) => l.productId).filter(Boolean))];
  const out = new Map();
  if (!ids.length) return out;
  for (const r of await v3Rows(c, ctx, 'products', 'row_id = ANY($5::text[])', [ids])) out.set(r.row_id, caps.refOf(r.data));
  return out;
}

/** ردیف‌های نشستهٔ یک فاکتور + ردیف‌های همین دسته (دسته جایگزین) */
async function linesV3(c, ctx, saleId, batch) {
  const m = new Map();
  for (const r of await v3Rows(c, ctx, 'saleItems', `data->>'saleId' = $5`, [saleId])) m.set(r.row_id, r.data);
  const mine = batch && batch.lines.get(saleId);
  if (mine) for (const [id, f] of mine) m.set(id, f);
  return [...m.values()].filter(isObj).map((f) => caps.lineOf(f));
}

/**
 * امتیازِ مصرف‌شدهٔ همین فاکتور (Minor): ردیفِ `loyalty_spend` همیشه در همان ثبت (همان tx) با سرِ فاکتور می‌آید ⇒ برای سر
 * فقط همین دسته (`stored` نادرست، بی پرس‌وجو)؛ برای قلمِ دیرآمده ردیفِ نشسته هم
 */
async function loyaltyV3(c, ctx, saleId, batch, stored = false) {
  if (batch && batch.loyalty && batch.loyalty.has(saleId)) return batch.loyalty.get(saleId);
  if (!stored) return 0;
  const rows = await v3Rows(c, ctx, 'auditLog', `data->>'refId' = $5 AND data->>'type' = $6`, [saleId, caps.LOYALTY_SPEND]);
  return rows.reduce((a, r) => a + Math.max(0, Math.round(Number(r.data && r.data.amountMinor) || 0)), 0);
}

/** مشتریِ فاکتور روی همین سرور لیستِ عمده دارد؟ (تصمیمِ ۱-۱۱ (۳)؛ بی مشتری یا مشتریِ ناشناخته ⇒ خرده) */
async function wholesaleV3(c, ctx, head) {
  const id = isObj(head) ? str(head.debtorId) : '';
  if (!id) return false;
  const rows = await v3Rows(c, ctx, 'debtors', 'row_id = $5', [id]);
  return rows.length > 0 && caps.isWholesaleDebtor(rows[0].data);
}

// ---------- SR46-1: لیستِ عمدهٔ مشتری = تخفیفِ ماندگار ----------

/**
 * عمده کردنِ مشتری (درجِ مشتریِ تازه با `priceList = wholesale` یا تغییرِ لیستِ مشتریِ خرده به عمده) همان «ادعای لیست» است
 * که تصمیمِ ۱-۱۱ (۳) از سرِ فاکتور نمی‌پذیرد — بی این، کارمندِ بی تخفیف اول مشتری را عمده می‌کرد و بعد به قیمتِ عمده می‌فروخت.
 * پس با همین پرچم فقط «کسی که اجازه‌اش را دارد»: `sales.discount` ِ بی سقف (۱۰۰٪ و هر مبلغ) ⇒ آزاد؛ سقف‌دار، «با تأیید» یا
 * «ممنوع» ⇒ رد (ردیفِ مشتری برگه نمی‌برد، همان `credit-limit-perm`). ویرایشِ مشتریِ عمدهٔ موجود و برگرداندن به خرده آزاد.
 */
const LIST_REQ = Object.freeze({ pct: 100, amount: Number.MAX_SAFE_INTEGER });
const grantsWholesale = (prev, next) => isObj(next) && Object.prototype.hasOwnProperty.call(next, 'priceList')
  && caps.isWholesaleDebtor(next) && !caps.isWholesaleDebtor(prev);
const listDecision = (role, layers) => require('./perm-core').evaluate(role, layers, caps.DISCOUNT, LIST_REQ);

/** `sync-v3-apply.authorize` ⇐ op ِ `debtors`: null یا رد با صندوق */
async function judgeListV3(c, ctx, op, row, origin) {
  if ((op.kind !== 'insert' && op.kind !== 'update') || !isObj(op.fields)) return null;
  if (!grantsWholesale(row && !row.deleted_at ? row.data : null, op.fields)) return null;
  if (!(await on(ctx, (s, p) => c.query(s, p)))) return null;
  const d = listDecision(ctx.role, await require('./staff-approval').requesterLayers(ctx, c));
  if (d.decision === 'allow') return null;
  return {
    reason: 'discount_not_allowed', box: 'perm_denied',
    detail: { perm: caps.DISCOUNT, decision: 'deny', reason_core: d.reason, requester: ctx.userId || '', role: ctx.role || '',
      debtor: op.row_id, priceList: 'wholesale', ...(d.cap ? { cap: d.cap, limit: d.limit } : {}), path: origin },
  };
}

/** تأییدِ پذیرفته‌شدهٔ پیشینِ همین سند و کار، که همین نیاز را بپوشاند (قلمِ دیرآمده) */
async function coveredBefore(c, shopId, n) {
  const rows = (await c.query(
    'SELECT pct_bp, amount FROM shop_approvals WHERE shop_id=$1 AND doc=$2 AND perm=$3', [shopId, n.doc, n.perm]
  )).rows;
  return rows.some((r) => (n.pctBp === null || (r.pct_bp !== null && Number(r.pct_bp) >= n.pctBp))
    && (n.amount === null || (r.amount !== null && Number(r.amount) >= n.amount)));
}

// ---------- v3 ----------

/**
 * درجِ فاکتورِ v3. ⇒ `undefined` (پرچمِ خاموش یا ناروا: راهِ امروز)، یا `{ denied, withDiscount }`
 * (`denied` همان شکلِ ردِ `authorize` یا null؛ `withDiscount` = نیازِ تخفیف داشت، برای برگهٔ قرض).
 */
async function judgeSaleV3(c, ctx, op, row, { batch = null, deviceId = '', origin = 'v3' } = {}) {
  if (!touchesV3(op) || op.table !== 'sales') return undefined;
  if (row && row.inserted) return undefined;          //  درجِ دوبارهٔ سندِ نشسته: همان «تکراری»/تعارضِ امروز
  if (!(await on(ctx, (s, p) => c.query(s, p)))) return undefined;
  const list = await linesV3(c, ctx, op.row_id, batch);
  const refs = await refsV3(c, ctx, list);
  const sale = caps.saleOf(op.row_id, op.fields, {
    loyaltyMinor: await loyaltyV3(c, ctx, op.row_id, batch), wholesale: await wholesaleV3(c, ctx, op.fields),
    taxOn: await taxOn(ctx, (s, p) => c.query(s, p)),
  });
  const needs = caps.needs(sale, list, refs, { enforcePrice: true });
  const withDiscount = needs.some((n) => n.perm === caps.DISCOUNT);
  const sa = require('./staff-approval');
  const at = sa.wallOf(op.hlc);
  for (const n of needs) {
    const slip = slipFor(op, n.perm, withDiscount);
    const denied = await sa.judge(c, ctx, needOf(n), slip, { opId: op.op_id, at, deviceId, path: origin });
    if (denied) return { denied, withDiscount };
  }
  return { denied: null, withDiscount };
}

/**
 * قلمِ v3 ِ فاکتوری که سرش در همین دسته نیست ولی روی سرور نشسته (دستهٔ بزرگِ بریده، یا op ِ دست‌ساز). ⇒ null یا رد.
 * فقط وقتی همین قلم خودش از فهرست فاصله دارد یا قلمِ آزاد است؛ سرِ نانشسته ⇒ همان سر بعداً با همهٔ قلم‌ها سنجیده می‌شود.
 */
async function judgeLineV3(c, ctx, op, row, { batch = null, deviceId = '', origin = 'v3' } = {}) {
  if (!touchesV3(op) || op.table !== 'saleItems' || (row && row.inserted)) return null;
  const sid = str(op.fields.saleId);
  if (!sid || (batch && batch.headers.has(sid))) return null;
  if (!(await on(ctx, (s, p) => c.query(s, p)))) return null;
  const head = (await v3Rows(c, ctx, 'sales', 'row_id = $5', [sid]))[0];
  if (!head || !isObj(head.data)) return null;
  const sale = caps.saleOf(sid, head.data, {
    loyaltyMinor: await loyaltyV3(c, ctx, sid, batch, true), wholesale: await wholesaleV3(c, ctx, head.data),
    taxOn: await taxOn(ctx, (s, p) => c.query(s, p)),
  });
  const me = caps.lineOf(op.fields);
  const own = await refsV3(c, ctx, [me]);
  if (!caps.isFree(me, own) && caps.line(me, own.get(me.productId) || null, sale.date, sale.wholesale).deviationBp === 0) return null;
  const list = await linesV3(c, ctx, sid, { headers: new Set(), lines: new Map([[sid, new Map([[op.row_id, op.fields]])]]) });
  const needs = caps.needs(sale, list, await refsV3(c, ctx, list), { enforcePrice: true });
  const sa = require('./staff-approval');
  const core = require('./perm-core');
  const layers = await sa.requesterLayers(ctx, c);
  for (const n of needs) {
    const d = core.evaluate(ctx.role, layers, n.perm, caps.req(n));
    if (d.decision === 'allow') continue;
    if (d.decision === 'approve' && await coveredBefore(c, ctx.accountId, n)) continue;
    const slip = slipFor(op, n.perm, true);
    const denied = await sa.judge(c, ctx, needOf(n), slip, { opId: op.op_id, at: sa.wallOf(op.hlc), deviceId, path: origin });
    if (denied) return denied;
  }
  return null;
}

/** نمایهٔ دسته از واحدهای `push` (یک بار برای هر دسته، روی همان آرایه) */
const batches = new WeakMap();
function batchFromPlan(plan) {
  if (!Array.isArray(plan)) return null;
  if (!batches.has(plan)) batches.set(plan, batchOf(plan));
  return batches.get(plan);
}

/**
 * درِ `sync-v3.authorize` (پیش از تخفیف/لغو/برگشت): درجِ فاکتور ⇒ تخفیف با تخفیفِ پنهان، تغییرِ قیمت، بعد قرض — نتیجهٔ نهایی؛
 * قلمِ دیرآمده ⇒ رد یا `undefined`. پرچمِ خاموش یا opِ بی‌ربط ⇒ `undefined` و همان راهِ امروز.
 * (`doc-roles`/`till-guard` ِ پس از آن فقط `auditLog`/`transactions` را می‌سنجند؛ درجِ فاکتور از آن‌ها چیزی نمی‌خواهد.)
 */
async function judgeV3(c, ctx, op, row, { plan = null, deviceId = '', origin = 'v3' } = {}) {
  if (isObj(op) && op.table === 'debtors') return (await judgeListV3(c, ctx, op, row, origin)) || undefined;   //  SR46-1
  if (!touchesV3(op) || !(await on(ctx, (s, p) => c.query(s, p)))) return undefined;
  const batch = batchFromPlan(plan);
  const s = await judgeSaleV3(c, ctx, op, row, { batch, deviceId, origin });
  if (s !== undefined) return s.denied || require('./credit-cap').judgeV3(c, ctx, op, row, { withDiscount: s.withDiscount, deviceId, origin });
  return (await judgeLineV3(c, ctx, op, row, { batch, deviceId, origin })) || undefined;
}

// ---------- REST / راهِ قدیم ----------

/**
 * `sync.pushChanges` (درونِ همان تراکنش، پیش از نوشتن): هر فاکتورِ تازه، فاکتوری که قلمِ تازه گرفت، یا سرِ نشسته‌ای که دوباره
 * آمد (فقط آنچه از نیازِ پیشینِ همان سند بیشتر شد) ⇒ همان سنجش با
 * ردیف‌ها و کالاهای جدول‌های قدیم. ⇒ null، یا خطای ۴۰۳ (کلِ دسته برمی‌گردد). REST برگه نمی‌برد؛ تأییدِ پیشینِ همان
 * سند (`shop_approvals`، مثلاً از Sync v3) اگر بپوشاند بس است.
 */
async function judgeLegacy(c, ctx, changes) {
  const { shopId, role, userId } = ctx;
  if (!shopId || role === 'owner') return null;
  const live = (ch, col) => ch && ch.collection === col && ch.deleted !== true && isObj(ch.data) && typeof ch.id === 'string' && ch.id;
  const heads = changes.filter((ch) => live(ch, 'sales'));
  const items = changes.filter((ch) => live(ch, 'saleItems'));
  const spends = changes.filter((ch) => live(ch, 'auditLog') && ch.data.type === caps.LOYALTY_SPEND && str(ch.data.refId));
  //  تصمیمِ ۱-۱۱ (۳): لیستِ عمده از مشتریِ همین دسته (راهِ قدیم پیش از نوشتن می‌سنجد) وگرنه همان ردیفِ سرور
  const debtorsHere = new Map(changes.filter((ch) => live(ch, 'debtors')).map((ch) => [ch.id, ch.data]));
  const wholesaleOf = async (h) => {
    const id = isObj(h) ? str(h.debtorId) : '';
    if (!id) return false;
    if (debtorsHere.has(id)) return caps.isWholesaleDebtor(debtorsHere.get(id));
    const r = (await c.query('SELECT data FROM debtors WHERE shop_id=$1 AND id=$2 AND deleted = false', [shopId, id])).rows[0];
    return !!r && caps.isWholesaleDebtor(r.data);
  };
  const lists = [...debtorsHere.entries()].filter(([, d]) => caps.isWholesaleDebtor(d));   //  SR46-1
  if (!heads.length && !items.length && !lists.length) return null;
  const run = (s, p) => c.query(s, p);
  if (!(await require('./orders').flagOf(shopId, FLAG, run))) return null;
  if (lists.length) {
    //  SR46-1: مشتریِ عمدهٔ تازه یا خرده‌ای که عمده شد (راهِ قدیم کلِ ردیف را جایگزین می‌کند) ⇒ فقط با تخفیفِ بی سقف
    const was = new Map((await c.query('SELECT id, data FROM debtors WHERE shop_id=$1 AND id = ANY($2::text[]) AND deleted = false',
      [shopId, lists.map(([id]) => id)])).rows.map((r) => [r.id, r.data]));
    const grant = lists.find(([id, d]) => grantsWholesale(was.get(id) || null, d));
    if (grant && listDecision(role, await require('./staff-perms').layersFor(shopId, role, userId, run)).decision !== 'allow') {
      return require('../middleware/errors').forbidden(
        `عمده کردنِ مشتری (لیستِ قیمتِ عمده) همان تخفیف است و برای نقشِ شما بی سقف نیست (debtors/${grant[0]})`, 'discount_not_allowed');
    }
  }
  if (!heads.length && !items.length) return null;
  const known = async (table, ids) => new Set(ids.length ? (await c.query(
    `SELECT id FROM ${table} WHERE shop_id=$1 AND id = ANY($2::text[]) AND deleted = false`, [shopId, ids]
  )).rows.map((r) => r.id) : []);
  const oldHeads = await known('sales', heads.map((h) => h.id));
  const oldItems = await known('sale_items', items.map((i) => i.id));
  const freshItems = items.filter((i) => !oldItems.has(i.id));
  //  فاکتورِ تازه، فاکتوری که قلمِ تازه گرفت، و سرِ نشسته‌ای که دوباره فرستاده شد (راهِ قدیم کلِ ردیف را جایگزین می‌کند:
  //  تخفیف بالاتر یا «جمعِ نهایی» پایین‌تر ⇒ همان سنجش؛ فقط آنچه از نیازِ پیشینِ همان سند بیشتر شد)
  const sids = new Set([...heads.map((h) => h.id), ...freshItems.map((i) => str(i.data.saleId)).filter(Boolean)]);
  if (!sids.size) return null;
  const layers = await require('./staff-perms').layersFor(shopId, role, userId, run);
  const core = require('./perm-core');
  const { forbidden } = require('../middleware/errors');
  const sumAfn = (vs) => vs.reduce((a, v) => a + Math.max(0, Math.round(Number(v) * 100) || 0), 0);
  const tax = await taxOn({ accountId: shopId }, run);   //  POS-37
  for (const sid of sids) {
    const given = heads.find((h) => h.id === sid);
    const stored = given && !oldHeads.has(sid) ? null
      : (await c.query('SELECT data FROM sales WHERE shop_id=$1 AND id=$2 AND deleted = false', [shopId, sid])).rows[0]?.data;
    const head = given ? given.data : stored;
    if (!isObj(head)) continue;
    const m = new Map();
    for (const r of (await c.query(
      `SELECT id, data FROM sale_items WHERE shop_id=$1 AND deleted = false AND data->>'saleId' = $2`, [shopId, sid]
    )).rows) m.set(r.id, r.data);
    const before = [...m.values()].filter(isObj).map((f) => caps.lineOf(f, { legacy: true }));
    for (const i of items) if (str(i.data.saleId) === sid) m.set(i.id, i.data);
    const list = [...m.values()].filter(isObj).map((f) => caps.lineOf(f, { legacy: true }));
    const ids = [...new Set(list.map((l) => l.productId).filter(Boolean))];
    const refs = new Map();
    if (ids.length) {
      for (const r of (await c.query(
        'SELECT id, data FROM products WHERE shop_id=$1 AND deleted = false AND id = ANY($2::text[])', [shopId, ids]
      )).rows) refs.set(r.id, caps.refOf(r.data, { legacy: true }));
    }
    //  امتیازِ مصرف‌شدهٔ همین فاکتور (افغانی ⇒ Minor): ردیفِ همین دسته؛ دفترِ نشسته فقط برای سندی که پیش‌تر نشسته بود
    const kept = isObj(stored) ? sumAfn((await c.query(
      `SELECT data->>'amount' AS amount FROM shop_audit_log WHERE shop_id=$1 AND deleted = false AND data->>'refId' = $2 AND data->>'type' = $3`,
      [shopId, sid, caps.LOYALTY_SPEND])).rows.map((r) => r.amount)) : 0;
    const mine = spends.filter((a) => a.data.refId === sid);
    const loyaltyMinor = mine.length ? sumAfn(mine.map((a) => a.data.amount)) : kept;
    const wholesale = await wholesaleOf(head);
    const needs = caps.needs(caps.saleOf(sid, head, { legacy: true, loyaltyMinor, wholesale, taxOn: tax }), list, refs, { enforcePrice: true });
    //  نیازِ همان سند پیش از این دسته (سر و قلم‌های نشسته): آنچه بیش از آن نیست ویرایشِ بی‌پول است، آزاد
    const prior = isObj(stored) ? caps.needs(caps.saleOf(sid, stored, { legacy: true, loyaltyMinor: kept, wholesale, taxOn: tax }), before, refs, { enforcePrice: true }) : [];
    const covered = (n) => prior.some((b) => b.perm === n.perm
      && (n.pctBp === null || (b.pctBp !== null && b.pctBp >= n.pctBp)) && (n.amount === null || (b.amount !== null && b.amount >= n.amount)));
    for (const n of needs) {
      if (covered(n)) continue;
      const d = core.evaluate(role, layers, n.perm, caps.req(n));
      if (d.decision === 'allow') continue;
      if (d.decision === 'deny') {
        if (n.perm === caps.FREE_LINE) return forbidden(`قلمِ آزاد (بی کارتِ کالا) برای نقشِ شما ممنوع است (sales/${sid})`, 'free_line_not_allowed');
        return n.perm === PERM
          ? forbidden(`تغییرِ قیمتِ ردیف برای نقشِ شما ممنوع است (sales/${sid})`, 'price_not_allowed')
          : forbidden(`تخفیف (با کاهشِ قیمتِ ردیف) برای نقشِ شما ممنوع است (sales/${sid})`, 'discount_not_allowed');
      }
      if (await coveredBefore(c, shopId, n)) continue;
      const what = n.perm === PERM ? 'تغییرِ قیمت' : n.perm === caps.FREE_LINE ? 'قلمِ آزاد' : 'تخفیف';
      return forbidden(`این ${what} تأییدِ مدیر می‌خواهد؛ از صندوقِ برنامه با تأیید بفرستید (sales/${sid})`, 'approval_required');
    }
  }
  return null;
}

module.exports = { FLAG, PERM, on, onUnits, touchesV3, batchOf, slipOf, slipFor, judgeV3, judgeSaleV3, judgeLineV3, judgeLegacy };
