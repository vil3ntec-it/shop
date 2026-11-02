'use strict';
/**
 * ── داوریِ نقشِ سندهای «سابقه‌سوار» در Sync v3 و v1 ِ دکان (PAY-24، PRM-13، POS-26/27، INV-20/21) ─────────────
 * کارتِ هدیه، امتیازِ وفاداری، فروشِ امانی، سفارشِ ویژه، محلِ انبار و بسته‌بندی جدولِ تازه ندارند: سندشان ردیفِ
 * `auditLog` (با `type`) و پولشان رسید/بردِ `transactions` (با `kind`) است. هر دو جدول برای هر نقشِ نویسنده باز است
 * (`perm-core.WRITES`) و قاعدهٔ ردیف ندارند، پس اجازه‌ای که برنامه پیش از ساختنِ ردیف می‌سنجد فقط روی دستگاه بود:
 * برنامهٔ دست‌کاری‌شده «باطل کردنِ کارت» یا «لغوِ امانی با بازپرداختِ نقد» را با نقشِ صندوق‌دار می‌فرستاد.
 *
 * پرچمِ `shop.sync.doc_roles` (`live_config`، دکان یا `all`، فقط `true`؛ همان `orders.flagOf` با «غیرفعال کردنِ
 * سرویس»). ⛔ پیش‌فرض خاموش ⇒ پاسخ همان امروز؛ تنها کار خواندنِ پرچم است، آن هم فقط برای opی از همین نوع‌ها.
 *
 * روشن ⇒ فقط `insert` ِ v3/v2 روی ردیفی که هنوز ننشسته (درجِ دوباره روی ردیفِ نشسته را خودِ سیاستِ `log`/`doc`
 * بی‌اثر یا صندوقِ تعارض می‌کند). رد همان مسیرِ `table_not_allowed` ِ قاعدهٔ ردیف است (`authorize` ِ `sync-v3.js`):
 * `{status: 'rejected', reason}` در پاسخ، ردِ کلِ واحدِ tx، و در برنامه فهرستِ ردشده‌ها (`SyncV3Engine.rejected`).
 *   doc_invalid      شکلِ نادرست: نوعِ ناشناخته در یکی از این خانواده‌ها، مبلغ یا امتیازِ نه عددِ صحیحِ مثبت،
 *                    `refId` ِ خالی جایی که برنامه همیشه می‌گذارد، … — برای همه، صاحب دکان هم (ردیفِ بدشکل
 *                    خواندنِ دفترِ همهٔ دستگاه‌ها را می‌شکند؛ برنامه هرگز چنین ردیفی نمی‌سازد)
 *   doc_not_allowed  نقش (با لایه‌های `staff-perms`، همان `policy` ِ برنامه) اجازهٔ همان کار را در برنامه ندارد.
 *                    صاحب دکان هرگز.
 *
 * ── اجازهٔ هر نوع = همان که برنامه می‌سنجد (آزمونِ آینه: `doc-roles.test.js`) ─────────────────────
 *   SELL    `ShopRole.canWrite("sales")` = `canSell` ِ `GiftCardBarAndroid` (فروش و شارژِ کارت)؛ و ردیفی که برنامه
 *           فقط درونِ ثبتِ فاکتور می‌سازد: خرجِ کارت (`GiftCards.forSale`)، کسب و مصرفِ امتیاز (`LoyaltyPoints.post`)،
 *           تحویلِ امانی و سفارشِ ویژه (فروشِ صندوق) — فاکتورِ نقشی که `sales` نمی‌نویسد خودش رد می‌شود
 *   CANCEL  `ShopRole.canCancelSale` = `PermCore.allows(sales.cancel)`: لغوِ امانی و سفارشِ ویژه
 *           (`LayawayBarAndroid`، `SpecialOrderBarAndroid`) — وضعیتِ `cancelled` و بردِ بازپرداختِ نقد
 *   VOID    `canSell && canCancelSale` = `canVoid` ِ باطل کردنِ کارت (#801)
 *   BACK    ردیفی که فقط درونِ برگشت یا لغوِ فاکتور ساخته می‌شود (برگشت به کارت `GiftCards.forReturn/forCancel`،
 *           برگشتِ امتیاز `LoyaltyPoints.afterReturn/afterCancel`): برگشت ⇐ `returns` نوشتنی و `returns.create`
 *           نه «ممنوع» (`ReturnFlow.permDecision`: allow، یا approve با PIN)، یا لغو ⇐ CANCEL
 *   PLACES  `StockPlacesEntry`: هر نقش جز `staff` (شاگرد) — محل‌ها و بسته‌بندیِ دوباره
 *   ''      بی سنجشِ نقش در برنامه (ساختِ امانی و سفارش، «آماده»، پیش‌پرداخت و قسط): فقط شکل
 *
 * ── Sync v1 ِ دکان ([v1Guard]، از `routes/sync-v1.js`) ─────────────────────────────────────────────
 *   همین قاعده‌ها، همین پرچم. `lib/sync-v1.js` (مشترک با پمپ) دست نمی‌خورد: داوری از قلابِ `guard` ِ همان push
 *   است که برای هر op پس از تکراری‌سنجی، `authorizeOp` و قفلِ ردیف صدا زده می‌شود (همان تراکنش؛ کنارِ نگهبانِ
 *   `finalized-sales`). حسابِ پمپ (`station`) و دسته‌ای بی `auditLog`/`transactions` همان نگهبانِ پیشین را
 *   می‌گیرند، بی هیچ پرسشی. فیلدهای v1 پیش از سنجش با همان مبدلِ v1⇒v3 (`sync-v3-convert.convertFields`:
 *   `amount` ِ افغانی ⇒ `amountMinor`) شکلِ v3 می‌گیرند. چون v1 درج روی ردیفِ موجود را ادغام می‌کند و ویرایش
 *   دارد، ردیفِ **پس از** ادغام سنجیده می‌شود (درج یا ویرایش؛ حذف نه)؛ opی که چیزی را عوض نمی‌کند
 *   (`safe-ledger.within`، همان درجِ دوبارهٔ بی‌اثرِ v3) داوری نمی‌شود. رد همان شکلِ ردِ v1 است:
 *   `{op_id, status: 'rejected', reason}` با همان دو کد، و برنامه (`SyncV1Engine`) آن را مثلِ هر ردِ دیگرِ v1
 *   از صف به دفترِ ردشده‌ها (`OpQueue.drop`) می‌برد. آینهٔ v1 در v3 فقط ردیفِ پذیرفته را می‌برد.
 */
const permCore = require('./perm-core');
const staffPerms = require('./staff-perms');
const { SHOP } = require('./tenancy');

const FLAG = 'shop.sync.doc_roles';

const SELL = 'sell';
const CANCEL = 'cancel';
const VOID = 'void';
const BACK = 'back';
const PLACES = 'places';
const OPEN = '';
/** شکلِ نادرست */
const BAD = 'bad';

/** همان `GiftCard.MAX_AMOUNT` و `GiftCard.REASON_MIN` و `Loyalty.MAX_POINTS` ِ هسته */
const GIFT_MAX = 10_000_000_000;
const REASON_MIN = 3;
const MAX_POINTS = 1_000_000_000;

const isObj = (v) => v !== null && typeof v === 'object' && !Array.isArray(v);
const absent = (v) => v === undefined || v === null;
const text = (v) => typeof v === 'string' && v.trim() !== '';
const int = (v, min, max = Number.MAX_SAFE_INTEGER) => Number.isSafeInteger(v) && v >= min && v <= max;
/** مبلغِ صفرپذیر: نبود = صفر */
const zeroOk = (v) => absent(v) || int(v, 0);
const own = (o, k) => typeof k === 'string' && Object.prototype.hasOwnProperty.call(o, k);

/** ردیفِ کارت (`GiftCard.check`): کد، و مبلغِ ۱ تا سقف؛ با `ref` شناسهٔ فاکتور یا سندِ برگشت هم */
const card = (need, ref) => (f) => (text(f.payRef) && int(f.amountMinor, 1, GIFT_MAX) && (!ref || text(f.refId)) ? need : BAD);
/** باطل: بی مبلغ، با دلیل */
const voided = (f) => (text(f.payRef) && (absent(f.amountMinor) || f.amountMinor === 0)
  && typeof f.reason === 'string' && f.reason.trim().length >= REASON_MIN ? VOID : BAD);
/** ردیفِ امتیاز: مشتری (`payRef`)، فاکتور (`refId`)، امتیازِ مثبت، ارزشِ صفرپذیر */
const points = (need) => (f) => (text(f.payRef) && text(f.refId) && int(f.points, 1, MAX_POINTS) && zeroOk(f.amountMinor) ? need : BAD);
/** سندِ امانی/سفارش: شیءِ تو در تو و پیش‌پرداختِ صفرپذیر */
const doc = (key) => (f) => (isObj(f[key]) && zeroOk(f.amountMinor) ? OPEN : BAD);
/** وضعیتِ امانی/سفارش: اجازه از مقصد (`<key>.to`) */
const moved = (key, to) => (f) => (text(f.refId) && zeroOk(f.amountMinor) && isObj(f[key]) && own(to, f[key].to) ? to[f[key].to] : BAD);
const placed = (f) => (text(f.refId) && (f.reason === 'add' || f.reason === 'archive') ? PLACES : BAD);
const repacked = (f) => (text(f.refId) ? PLACES : BAD);

/** `auditLog.type` ⇒ (fields) ⇒ اجازهٔ لازم یا BAD */
const AUDIT = Object.freeze({
  gift_card_issue: card(SELL, false),
  gift_card_recharge: card(SELL, false),
  gift_card_redeem: card(SELL, true),
  gift_card_refund: card(BACK, true),
  gift_card_void: voided,
  loyalty_earn: points(SELL),
  loyalty_spend: points(SELL),
  loyalty_earn_back: points(BACK),
  loyalty_spend_back: points(BACK),
  layaway: doc('layaway'),
  layaway_status: moved('layaway', { delivered: SELL, cancelled: CANCEL }),
  special_order: doc('specialOrder'),
  special_order_status: moved('specialOrder', { ready: OPEN, delivered: SELL, cancelled: CANCEL }),
  stock_place: placed,
  stock_repack: repacked,
  stock_assembly: repacked,
});

/** `transactions.kind` ⇒ [type، اجازه] — مبلغِ مثبت و `refId` = سند */
const RECEIPT = Object.freeze({
  layaway_deposit: ['receive', OPEN],
  layaway_installment: ['receive', OPEN],
  layaway_refund: ['give', CANCEL],
  special_order_deposit: ['receive', OPEN],
  special_order_refund: ['give', CANCEL],
});

/** خانواده‌هایی که نوعِ ناشناخته‌شان شکلِ نادرست است؛ نوع‌های دیگرِ سابقه (`sale`، `shift_open`، …) مالِ این‌جا نیستند */
const AUDIT_FAMILY = /^(gift_card_|loyalty_|layaway(_|$)|special_order(_|$))/;
const RECEIPT_FAMILY = /^(layaway_|special_order_)/;

/**
 * اجازهٔ لازم برای درجِ این ردیف: '' (هیچ)، یکی از SELL/CANCEL/VOID/BACK/PLACES، `BAD` (شکلِ نادرست)،
 * یا null وقتی ردیف مالِ این داوری نیست.
 */
function needOf(table, fields) {
  const f = isObj(fields) ? fields : {};
  if (table === 'auditLog') {
    if (typeof f.type !== 'string') return null;
    if (own(AUDIT, f.type)) return AUDIT[f.type](f);
    return AUDIT_FAMILY.test(f.type) ? BAD : null;
  }
  if (table === 'transactions') {
    if (typeof f.kind !== 'string') return null;
    if (own(RECEIPT, f.kind)) {
      const [type, need] = RECEIPT[f.kind];
      return f.type === type && int(f.amountMinor, 1) && text(f.refId) ? need : BAD;
    }
    return RECEIPT_FAMILY.test(f.kind) ? BAD : null;
  }
  return null;
}

/** آیا این نقش با این لایه‌ها همان کار را در برنامه می‌کند — صاحب دکان همیشه */
function permits(need, role, layers) {
  if (!need || role === 'owner') return true;
  const cancel = () => permCore.allows(role, layers, 'sales.cancel');
  if (need === SELL) return permCore.canWrite(role, 'sales');
  if (need === CANCEL) return cancel();
  if (need === VOID) return permCore.canWrite(role, 'sales') && cancel();
  if (need === BACK) {
    return (permCore.canWrite(role, 'returns') && permCore.evaluate(role, layers, 'returns.create', null).decision !== 'deny')
      || cancel();
  }
  if (need === PLACES) return role !== 'staff';
  return false;
}

//  پرچم و لایه‌ها یک بار برای هر درخواستِ push (`ctx` ِ همان درخواست)، از اتصالِ تراکنش
const memo = new WeakMap();
function once(ctx, key, make) {
  let m = memo.get(ctx);
  if (!m) { m = {}; memo.set(ctx, m); }
  if (!m[key]) m[key] = make();
  return m[key];
}

/**
 * داوریِ یک opِ v3/v2 (`authorize` ِ `sync-v3.js`، پس از همهٔ داوری‌های دیگر). null یعنی بنشیند.
 * @param c  اتصالِ تراکنشِ push
 * @param row  ردیفِ فعلیِ v3 یا null
 */
async function judgeV3(c, ctx, op, row) {
  if (op.kind !== 'insert' || (row && row.inserted)) return null;
  return judge(c, ctx, op.table, op.fields);
}

/** داوریِ یک ردیف به شکلِ v3 — مشترکِ v3 و v1. null یعنی بنشیند. */
async function judge(c, ctx, table, fields) {
  const need = needOf(table, fields);
  if (need === null) return null;
  const run = (sql, params) => c.query(sql, params);
  if (!(await once(ctx, 'on', () => require('./orders').flagOf(ctx.accountId, FLAG, run)))) return null;
  if (need === BAD) return 'doc_invalid';
  if (!need || ctx.role === 'owner') return null;
  const layers = await once(ctx, 'layers', () => staffPerms.layersFor(ctx.accountId, ctx.role, ctx.userId, run));
  return permits(need, ctx.role, layers) ? null : 'doc_not_allowed';
}

// ---------------------------------------------------------------- Sync v1 ِ دکان

/** جدول‌هایی که این داوری می‌بیند (`needOf`) */
const V1_TABLES = new Set(['auditLog', 'transactions']);

/** حسابِ دکان در بخشِ دکان (حسابِ پمپ `station` است) */
const isShopV1 = (ctx) => Boolean(ctx && ctx.app === SHOP.app && ctx.accountKind === SHOP.app && ctx.accountId);

/** آیا opِ خامِ push مالِ یکی از این جدول‌هاست — همان یک‌دست‌سازیِ نامِ جدولِ `validateOp` */
function v1Touches(raw) {
  if (!isObj(raw)) return false;
  const { tableOf } = require('./sync-v1');
  return V1_TABLES.has(tableOf(SHOP.app, raw.table ?? raw.table_name ?? raw.tableName));
}

/** ردیفِ v1 پس از ادغامِ این op (`$inc` روی عددِ موجود) */
function mergedV1(cur, fields) {
  const out = { ...(isObj(cur) ? cur : {}) };
  for (const [k, v] of Object.entries(fields)) {
    out[k] = isObj(v) && typeof v.$inc === 'number' && typeof out[k] === 'number' ? out[k] + v.$inc : v;
  }
  return out;
}

/**
 * داوریِ یک opِ v1 ِ دکان (شکلِ `validateOp`، پس از مهاجرت). null یعنی بنشیند.
 * @param cur  دادهٔ ردیفِ موجود در `sync_rows` (پاک‌شده یا نبود ⇒ null)
 */
async function judgeV1(c, ctx, op, cur) {
  if (op.type === 'delete' || !V1_TABLES.has(op.table)) return null;
  const fields = isObj(op.fields) ? op.fields : {};
  if (cur && require('./safe-ledger').within(fields, cur, op.rowId)) return null;
  const spec = require('./sync-v3-schema').specOf(op.table);
  if (!spec) return null;
  return judge(c, ctx, op.table, require('./sync-v3-convert').convertFields(spec, mergedV1(cur, fields)).set);
}

/**
 * نگهبانِ push ِ v1 (`routes/sync-v1.js`): [inner] (نگهبانِ `finalized-sales`، یا null) را می‌پیچد.
 * پمپ، یا دسته‌ای بی این جدول‌ها ⇒ خودِ [inner]، بی هیچ کار و پرسشی. `lib/sync-v1.js` ردِ `{reject}` را
 * همان `{status: 'rejected', reason}` ِ v1 می‌کند.
 */
function v1Guard(ctx, ops, inner = null) {
  if (!isShopV1(ctx) || !Array.isArray(ops) || !ops.some(v1Touches)) return inner;
  //  پرچم و لایه‌ها یک بار برای همین درخواست (کلیدِ تازهٔ `once`)
  const scope = { accountId: ctx.accountId, role: ctx.role, userId: ctx.userId };
  return {
    async check(c, op, cur) {
      const g = inner ? await inner.check(c, op, cur) : null;
      if (g && (g.reject || g.replay)) return g;
      const reason = await judgeV1(c, scope, op, cur);
      return reason ? { reject: reason } : g;
    },
    finish: () => (inner ? inner.finish() : undefined),
  };
}

module.exports = {
  FLAG, SELL, CANCEL, VOID, BACK, PLACES, OPEN, BAD, AUDIT, RECEIPT, needOf, permits, judgeV3, judgeV1, v1Guard,
};
