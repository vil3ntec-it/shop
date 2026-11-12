'use strict';
/**
 * O-06 = ب (ORD-20، CHK-18؛ تصمیمِ صاحب‌کار ۱۴۰۵/۰۸/۱۳، همان طرحِ PR ‏#429): نگه‌داشتنِ کوتاهِ موجودی برای
 * سفارشِ آنلاین تا دکان قبول کند — پشتِ پرچمِ پیش‌فرض خاموشِ `shop.stock_reserve` (با `shop.catalog`).
 *
 *   کِی ─── فقط با هر دو پرچمِ روشن **و** موجودیِ «تازه» (`catalog.freshness`)؛ سفارشِ آزمایشی هرگز. وگرنه
 *           همان رفتارِ امروز: بی نگه‌داشتن («در حالِ بررسی»).
 *   ساخت ── درونِ تراکنشِ `orders.create`، زیرِ قفلِ `orders:<دکان>` و سپس `catalog:<دکان>` (همان قفلِ همهٔ
 *           نوشتن‌های موجودی): «آزاد = قابل‌فروش − نگه‌داشتهٔ فعالِ دیگران» سنجیده و برای هر قلمِ کالادار یک
 *           ردیفِ `stock_reservations` (`kind = soft`، `expires_at = اکنون + reserve_ttl_sec`) و حرکتِ بی‌مکانِ
 *           `reserve` نوشته می‌شود ⇒ دو سفارشِ هم‌زمانِ آخرین عدد: دقیقاً یکی ۲۰۱ و دیگری ۴۰۹ `out_of_stock`.
 *           تکرارِ همان کلیدِ یکتایی پیش از این‌جا برمی‌گردد؛ نمایهٔ یکتای `(shop, order, line) WHERE held`
 *           پشتوانهٔ دوم است.
 *   قبول ── بی انقضا تا پایانِ سفارش. نگه‌داشتنِ گذشته ⇒ اگر هنوز آزاد است دوباره (بی‌مهلت)، وگرنه ۴۰۹
 *           `out_of_stock` و گذار انجام نمی‌شود (دکان با دلیلِ «ناموجود» رد می‌کند).
 *   تحویل ─ ⛔ «کسرِ قطعی» ِ ORD-20 فقط فروشِ تحویلِ برنامهٔ دکان است (`OrderFulfillment.kt`، شناسهٔ ثابتِ
 *           `o-<سفارش>-<قلم>`؛ فاکتورِ قدیمیِ W: `w-…`) — سرور خودش هرگز موجودی را کم نمی‌کند. «مشتری گرفت /
 *           به پیک داده شد / رسید» نگه‌داشتن را آزاد **نمی‌کند** (وگرنه تا رسیدنِ فروش همان عدد دوباره فروخته
 *           می‌شد)؛ فقط مهلتِ پشتیبانِ [HANDOVER_GRACE_MS] می‌گیرد (برنامهٔ کهنه که فروشِ `o-…` نمی‌سازد).
 *           وقتی همان فروش در دفتر نشست (`stock_sources` ِ همان قلم، هر کالا — جایگزین هم)، در **همان تراکنش**
 *           ِ کسر رزرو «انجام‌شده» می‌شود ⇒ یک بار کم، یک بار آزاد؛ همگام‌سازیِ دوباره فقط «تفاوت» می‌نویسد
 *           و ردیفِ بسته دوباره بسته نمی‌شود.
 *   پایان ─ رد، ردِ خودکار، لغو (دکان یا مشتری)، برگشت به دکان، به انبار، بازپرداختِ کامل ⇒ آزاد.
 *   انقضا ─ تنبل هنگامِ خواندن (`heldOf` فقط نگذشته را می‌شمارد) + جاروی دقیقه‌ای ([sweep]) که ردیف و دفتر
 *           را می‌بندد.
 *   INV-19  فروشِ حضوری **هرگز** رد نمی‌شود. اگر نگه‌داشته را برد، سفارش به بررسی می‌رود: تازه‌ترین‌ها
 *           `short_at` می‌گیرند، وظیفهٔ `reservation_short` (`soft-short:<کالا>`)، `stockHold.review` و (با
 *           `shop.order_notify`) یک بار خبرِ `order_review` در صفِ اعلانِ سفارش (مهاجرتِ ۵۲۰).
 *   دفتر ─ هر نگه‌داشتن/آزادسازی یک حرکتِ `reserve`/`release` با کلیدِ یکتا (`res:<ردیف>:hold|release|commit`)
 *           ⇒ تطبیقِ INV-16 (`stock.reconcile`) سالم می‌ماند.
 *
 *   سقف ─── ۲-۲۱ ِ صاحب‌کار (R5 الف): سفارشِ مشتری (ویترین/بازار، نه منبعِ کارمند) با شماره — آن شماره روی هم (سفارش‌های
 *           بازِ دیگرش + همین) از هر کالا فقط تا نیمِ «آزاد از دیدِ خودش» (قابل‌فروش − نگه‌داشتهٔ شماره‌های دیگر) نگه می‌دارد،
 *           دست‌کم یک دانه ([phoneCapOk]). بیشتر ⇒ سفارش مثلِ امروز ثبت می‌شود، فقط **کلش** بی نگه‌داشتن (هیچ ردیفی ⇒ قبول،
 *           ویرایش و جمع‌آوری هم بعداً برایش نمی‌سازند؛ رد هرگز). پیش‌تر هر شمارهٔ تأییدشده با ۱۰ سفارش در ساعت همهٔ موجودیِ
 *           آزاد را «ناموجود» نشان می‌داد. ⚠️ افزودنِ قلم به همان سفارش (CHK-21، پرچمِ خاموشِ جدا) سقف نمی‌گیرد — فقط سقفِ هر سفارش.
 *   ویرایش ─ ORD-14 و CHK-21 ([syncOrderIn]): نگه‌داشتهٔ بازِ سفارش هم‌اندازهٔ قلم‌های تازه می‌شود؛ افزودن با همان
 *           سنجشِ ساخت (نگه‌داشتهٔ خودِ سفارش «دیگران» نیست)؛ افزودنِ مشتری (CHK-21) همان سقفِ شمارهٔ بالا را دارد.
 *   خاموش ─ ⛔ R6: پرچمِ `shop.stock_reserve` خاموش ⇒ نگه‌داشتهٔ باز هیچ‌جا اثر ندارد (همان main): [heldOf] ⇒ ۰ (ویترین،
 *           کاتالوگ، سنجش)، [orderView]/[reviewIdsOf] ⇒ هیچ، وظیفه و خبرِ بررسی نه؛ و سرور نگه‌داشته‌ای نمی‌سازد، بزرگ
 *           نمی‌کند و مهلتش را دراز نمی‌کند (قبول دست نمی‌زند، تحویل فقط کوتاه می‌کند). ردیف‌ها در جدول می‌مانند و فقط بسته
 *           می‌شوند (پایان، فروشِ تحویل، قلمِ عوض‌شده، جارو با مهلتِ خودش) ⇒ روشنِ دوباره امن: نگذشته می‌شمارد، گذشته نه.
 */
const { query, many, now, tx } = require('../db');
const stock = require('./stock');
const { ulid } = require('./ulid');

const DEFAULT_TTL_SEC = 1800;
/** پس از تحویل و پیش از رسیدنِ فروشِ برنامه (مهلتِ پشتیبان) */
const HANDOVER_GRACE_MS = 24 * 60 * 60 * 1000;
/** گذارهایی که کالا را از دکان بیرون می‌برند — همان `order-ledger.HANDED_OVER` */
const HANDED_OVER = Object.freeze(['picked_up', 'out_for_delivery', 'delivered']);
/** پایانِ سفارش بی فروش (یا کالا به دکان برگشت) ⇒ آزاد */
const RELEASE_ON = Object.freeze(['rejected', 'auto_rejected', 'cancelled_by_customer', 'cancelled',
  'returned_to_store', 'restocked', 'refunded_full']);
const TICK_MS = 60 * 1000;
const BATCH = 100;

const num = (v) => (v === null || v === undefined ? 0 : Number(v));
const runOf = (c) => (c ? (t, p) => c.query(t, p) : query);

async function enabled(shopId, c) {
  return (await stock.flagOf(shopId, c)) && (await stock.reserveFlagOf(shopId, c));
}

async function ttlSecOf(c, shopId) {
  const r = (await runOf(c)('SELECT reserve_ttl_sec FROM catalog_settings WHERE shop_id=$1', [shopId])).rows[0];
  const v = r ? num(r.reserve_ttl_sec) : DEFAULT_TTL_SEC;
  return Number.isSafeInteger(v) && v >= 60 && v <= 86400 ? v : DEFAULT_TTL_SEC;
}

/**
 * نگه‌داشتهٔ فعال (نگه‌داشته و نگذشته) به تفکیکِ کالا ⇒ Map<pid, milli>. بی ردیف یا پرچمِ خاموش (R6) ⇒ Map ِ خالی؛ پرچم
 * فقط وقتی ردیفی هست خوانده می‌شود (دکانِ بی نگه‌داشته همان یک پرسش).
 * [exceptOrderId]: نگه‌داشتهٔ همان سفارش «دیگران» نیست (افزودنِ قلم، CHK-21: مقدارِ کلِ سفارش سنجیده می‌شود).
 */
async function heldOf(c, shopId, ids, at = now(), exceptOrderId = '') {
  const map = await sumHeld(c, shopId, ids, at, exceptOrderId);
  return map.size && !(await stock.reserveFlagOf(shopId, c)) ? new Map() : map;
}

/** همان [heldOf] بی پرسشِ پرچم — فقط برای فراخواننده‌ای که پرچم را همین حالا روشن دیده ([planIn]، [reholdIn]) */
async function sumHeld(c, shopId, ids, at, exceptOrderId = '') {
  const map = new Map();
  if (!ids || !ids.length) return map;
  const rows = (await runOf(c)(
    `SELECT product_id, COALESCE(SUM(qty_milli),0)::bigint AS q FROM stock_reservations
      WHERE shop_id=$1 AND kind='soft' AND status='held' AND product_id = ANY($2::text[])
        AND (expires_at IS NULL OR expires_at > $3) AND order_id <> $4
      GROUP BY product_id`, [shopId, ids, at, String(exceptOrderId || '')]
  )).rows;
  for (const r of rows) map.set(r.product_id, num(r.q));
  return map;
}

/** حرکتِ آزادسازیِ دفتر برای ردیف‌هایی که همین حالا بسته شدند (کلیدِ یکتا ⇒ تکرار بی‌اثر) */
async function postRelease(c, shopId, rows, suffix, reason, at) {
  for (const r of rows) {
    await stock.insertMove(c, shopId, {
      productId: r.product_id, location: '', type: 'release', qtyMilli: num(r.qty_milli),
      deltaOnHand: 0, deltaReserved: -num(r.qty_milli), mode: 'local', source: 'order',
      sourceKey: `res:${r.id}:${suffix}`, refType: 'order', refId: r.order_id,
      actorType: 'system', actorId: '', reason, at,
    }, at);
  }
}

/** نگه‌داشته‌های گذشتهٔ یک دکان ⇒ `expired` + آزادسازی در دفتر. درونِ تراکنش و زیرِ قفلِ `catalog:<دکان>`. */
async function expireDueIn(c, shopId, at) {
  const rows = (await c.query(
    `UPDATE stock_reservations SET status='expired', closed_at=$2, close_reason='ttl'
      WHERE shop_id=$1 AND kind='soft' AND status='held' AND expires_at IS NOT NULL AND expires_at <= $2
      RETURNING id, product_id, order_id, qty_milli`, [shopId, at]
  )).rows;
  await postRelease(c, shopId, rows, 'release', 'reservation_expired', at);
  return rows.length;
}

/**
 * یک ردیفِ نگه‌داشته + حرکتِ `reserve` برای هر قلم. `expiresAt = null` ⇒ تا پایانِ سفارش. `l.createdAt` (کوچک یا هم‌اندازه
 * کردنِ قلمِ موجودِ همان کالا، [syncOrderIn]) ⇒ جای همان قلم در صفِ «بررسی» ([recheckShort]: تازه‌ترین‌ها کم می‌آورند) نمی‌رود.
 */
async function insertHolds(c, shopId, orderId, lines, expiresAt, at) {
  for (const l of lines) {
    const id = ulid(at);
    await c.query(
      `INSERT INTO stock_reservations (id, shop_id, order_id, line_no, product_id, branch_id, qty_milli,
         status, expires_at, created_at, kind)
       VALUES ($1,$2,$3,$4,$5,'',$6,'held',$7,$8,'soft')`,
      [id, shopId, orderId, l.lineNo, l.productId, l.qtyMilli, expiresAt, l.createdAt ? num(l.createdAt) : at]
    );
    await stock.insertMove(c, shopId, {
      productId: l.productId, location: '', type: 'reserve', qtyMilli: l.qtyMilli,
      deltaOnHand: 0, deltaReserved: l.qtyMilli, mode: 'local', source: 'order',
      sourceKey: `res:${id}:hold`, refType: 'order', refId: orderId,
      actorType: 'system', actorId: '', reason: 'online_order', at,
    }, at);
  }
}

/**
 * نگه‌داشتن روشن و موجودی تازه ⇒ قفلِ موجودیِ دکان گرفته و گذشته‌ها بسته می‌شوند تا سنجش و نوشتن هر دو
 * زیرِ همان قفل باشند (هیچ فروش/نگه‌داشتنِ دیگری میانشان نمی‌نشیند) ⇒ `{ held, ttlSec }`؛ وگرنه null.
 */
async function planIn(c, shopId, settings, ids, at, orderId = '') {
  if (!(await stock.reserveFlagOf(shopId, c))) return null;
  const fr = await require('./catalog').freshness(shopId, settings, at, c);
  if (!fr.fresh) return null;
  await stock.lock(c, shopId);
  await expireDueIn(c, shopId, at);
  return { held: await sumHeld(c, shopId, ids, at, orderId), ttlSec: await ttlSecOf(c, shopId) };
}

/**
 * سنجشِ سفارش (`catalog.checkOrder`، تراکنشِ `orders.create`): نگه‌داشتن خاموش یا موجودیِ کهنه ⇒ همان
 * `stock.check` ِ امروز؛ وگرنه نگه‌داشتهٔ دیگران هم از آزاد کم می‌شود و `hold` برای [holdIn] برمی‌گردد.
 */
async function checkOrderIn(c, shopId, { lines, settings, names, at, orderId = '' }) {
  const plan = await planIn(c, shopId, settings, [...new Set(lines.map((l) => l.productId))].sort(), at, orderId);
  const out = await stock.check(c, shopId, { lines, settings, names, held: plan ? plan.held : null });
  return plan ? { ...out, hold: { lines, ttlSec: plan.ttlSec } } : out;
}

/** ۲-۲۱ — کمترین سقفِ هر شماره (یک دانه، به هزارم) */
const PHONE_MIN_MILLI = 1000;

/**
 * ۲-۲۱ (R5 الف) — شمارهٔ سفارشِ مشتریِ [orderId] با [lines] از سقفِ خودش می‌گذرد؟ ⇐ true (نگه دار) | false (کلِ سفارش بی
 * نگه‌داشتن). سفارشِ کارمند (`orders.STAFF_SOURCES`) یا بی شماره ⇐ true. درونِ همان تراکنش و زیرِ قفلِ موجودیِ [planIn]؛
 * شکل‌های همان شماره با `validate.phoneForms`؛ فقط نگه‌داشتهٔ فعال (نگذشته) می‌شمارد.
 */
async function phoneCapOk(c, shopId, orderId, lines, at) {
  const o = (await c.query('SELECT source, customer_phone FROM online_orders WHERE shop_id=$1 AND id=$2', [shopId, orderId])).rows[0];
  if (!o || !o.customer_phone || require('./orders').STAFF_SOURCES.includes(o.source)) return true;
  const forms = [...require('./validate').phoneForms(o.customer_phone)];
  if (!forms.length) return true;
  const ids = [...new Set(lines.map((l) => l.productId))].sort();
  const want = new Map();
  for (const l of lines) want.set(l.productId, (want.get(l.productId) || 0) + num(l.qtyMilli));
  const mine = new Map((await c.query(
    `SELECT r.product_id, COALESCE(SUM(r.qty_milli),0)::bigint AS q
       FROM stock_reservations r JOIN online_orders o ON o.id = r.order_id AND o.shop_id = r.shop_id
      WHERE r.shop_id=$1 AND r.kind='soft' AND r.status='held' AND r.product_id = ANY($2::text[])
        AND (r.expires_at IS NULL OR r.expires_at > $3) AND r.order_id <> $4 AND o.customer_phone = ANY($5::text[])
      GROUP BY r.product_id`, [shopId, ids, at, orderId, forms]
  )).rows.map((r) => [r.product_id, num(r.q)]));
  const all = await sumHeld(c, shopId, ids, at, orderId);
  const settings = await stock.settingsOf(shopId, c);
  const levels = await stock.levelsOf(c, shopId, ids);
  for (const pid of ids) {
    const own = mine.get(pid) || 0;
    const free = Math.max(0, stock.sellableOnHand(levels.get(pid), settings) - Math.max(0, (all.get(pid) || 0) - own));
    if (own + want.get(pid) > Math.max(PHONE_MIN_MILLI, Math.floor(free / 2))) return false;
  }
  return true;
}

/** پس از درجِ سفارش، در همان تراکنش و زیرِ همان قفل. شماره از سقفش بگذرد (۲-۲۱) ⇒ هیچ ردیفی، `{capped:true}`. */
async function holdIn(c, shopId, { orderId, lines, ttlSec, at }) {
  if (!(await phoneCapOk(c, shopId, orderId, lines, at))) return { expiresAt: null, capped: true };
  const expiresAt = at + ttlSec * 1000;
  await insertHolds(c, shopId, orderId, lines, expiresAt, at);
  return { expiresAt };
}

/**
 * قلم‌های کالادارِ امروزِ سفارش به واحدِ موجودی ⇒ Map<lineNo, {lineNo, productId, qtyMilli}>. کالایی که دیگر در
 * کاتالوگ نیست یا واحدش ناشناس است ⇒ بی نگه‌داشته (کنار).
 */
async function wantOf(c, shopId, orderId) {
  const raw = (await c.query(
    `SELECT line_no, product_id, unit, qty_milli FROM online_order_lines WHERE order_id=$1 AND shop_id=$2 AND product_id <> ''
      ORDER BY line_no`, [orderId, shopId]
  )).rows.map((l) => ({ lineNo: num(l.line_no), productId: l.product_id, unit: l.unit, qtyMilli: num(l.qty_milli) }));
  const want = new Map();
  for (const l of raw) {
    try {
      const [sl] = (await require('./catalog').toStockLines(c, shopId, [l])).lines;
      want.set(sl.lineNo, sl);
    } catch { /* ناموجود یا واحدِ ناشناس ⇒ بی نگه‌داشته */ }
  }
  return want;
}

/** قلم‌هایی که آخرین ردیفشان «گذشته» است (پس از [expireDueIn]) ⇒ با مقدارِ امروزِ قلم دوباره نگه داشتن یا ۴۰۹ */
async function reholdIn(c, shopId, orderId, at) {
  const latest = (await c.query(
    `SELECT DISTINCT ON (line_no) line_no, status FROM stock_reservations
      WHERE shop_id=$1 AND order_id=$2 AND kind='soft' ORDER BY line_no, created_at DESC, id DESC`, [shopId, orderId]
  )).rows.filter((r) => r.status === 'expired');
  if (!latest.length || !(await stock.reserveFlagOf(shopId, c))) return;
  const want = await wantOf(c, shopId, orderId);
  const lapsed = latest.map((r) => want.get(num(r.line_no))).filter(Boolean);
  if (!lapsed.length) return;
  const settings = await stock.settingsOf(shopId, c);
  if (!(await require('./catalog').freshness(shopId, settings, at, c)).fresh) return;
  const ids = [...new Set(lapsed.map((l) => l.productId))].sort();
  const names = new Map((await c.query(
    'SELECT product_id, name, unit FROM catalog_products WHERE shop_id=$1 AND product_id = ANY($2::text[])', [shopId, ids]
  )).rows.map((r) => [r.product_id, { name: r.name, unit: r.unit }]));
  await stock.check(c, shopId, { lines: lapsed, settings, names, held: await sumHeld(c, shopId, ids, at) });
  await insertHolds(c, shopId, orderId, lapsed, null, at);
}

/**
 * پس از عوض شدنِ قلم‌های سفارش (ویرایش ORD-14، افزودن CHK-21) در همان تراکنش: نگه‌داشتهٔ **باز** ِ سفارش هم‌اندازهٔ
 * قلم‌های تازه می‌شود — قلمِ برداشته یا کم/زیادشده ⇒ ردیفِ پیشین آزاد و (اگر هنوز هست) ردیفِ تازه با همان مهلت؛ قلمِ
 * تازه ⇒ نگه‌داشتنِ تازه با همان مهلت. سنجشِ «آزاد» پیش‌تر با [checkOrderIn] (و نگه‌داشتهٔ خودی کنار) انجام شده؛ ویرایشِ
 * بی سنجش (کارمند) هرگز رد نمی‌شود و کمبود به بررسی می‌رود (INV-19). سفارشِ بی نگه‌داشتهٔ باز ⇒ هیچ. پرچمِ خاموش (R6) ⇒
 * فقط آزاد کردن و کوچک کردن؛ قلمِ تازه/بزرگ‌شده/جایگزین نگه‌داشته نمی‌گیرد.
 * `phoneCap` (افزودنِ خودِ مشتری، CHK-21؛ CHK-18 ِ ۲-۲۱): ادعای تازه (قلمِ تازه یا بزرگ‌شده) همان سقفِ شماره را دارد
 * ([phoneCapOk] روی کلِ مقدارِ همان کالاها در سفارش) — گذشتن ⇒ افزودن ثبت می‌شود ولی ادعای تازه بی نگه‌داشتن و نگه‌داشتهٔ
 * پیشینِ همان قلم‌ها (اندازهٔ پیشین، جای پیشین در صف) می‌ماند؛ هیچ افزودنی رد نمی‌شود. ویرایشِ کارمند (ORD-14) سقف ندارد.
 */
async function syncOrderIn(c, shopId, orderId, at, { phoneCap = false } = {}) {
  const heldSql = `SELECT id, order_id, line_no, product_id, qty_milli, expires_at, created_at FROM stock_reservations
      WHERE shop_id=$1 AND order_id=$2 AND kind='soft' AND status='held' ORDER BY line_no`;
  if (!(await c.query(heldSql, [shopId, orderId])).rows.length) return;
  await stock.lock(c, shopId);
  await expireDueIn(c, shopId, at);
  const held = (await c.query(heldSql, [shopId, orderId])).rows;
  if (!held.length) return;
  const on = await stock.reserveFlagOf(shopId, c);
  const exp = held.map((r) => (r.expires_at === null ? null : num(r.expires_at)));
  const expiresAt = exp.includes(null) ? null : Math.min(...exp);
  //  به واحدِ موجودی؛ کالایی که دیگر در کاتالوگ نیست ⇒ قلمش نگه‌داشته ندارد (آزاد می‌شود)
  const want = await wantOf(c, shopId, orderId);
  const byLine = new Map(held.map((r) => [num(r.line_no), r]));
  let drop = held.filter((r) => {
    const w = want.get(num(r.line_no));
    return !w || w.productId !== r.product_id || w.qtyMilli !== num(r.qty_milli);
  });
  //  ⛔ SR42-1: جای صف (`created_at`) فقط برای همان کالا و نه بیشتر از پیش می‌ماند؛ بزرگ شدن یا کالای دیگر (افزودنِ مشتری،
  //  ویرایش، جایگزین) ادعای تازه است و ته صف می‌رود — وگرنه افزودهٔ دیر نگه‌داشتهٔ سفارش‌های پیش‌ترِ دیگران را به بررسی می‌برد
  let add = [...want.values()].filter((w) => {
    const r = byLine.get(w.lineNo);
    return !r || drop.includes(r);
  }).map((w) => {
    const r = byLine.get(w.lineNo);
    return r && r.product_id === w.productId && w.qtyMilli <= num(r.qty_milli) ? { ...w, createdAt: r.created_at } : w;
  }).filter((w) => on || w.createdAt !== undefined);   //  ⛔ R6: خاموش ⇒ فقط کوچک/هم‌اندازه (همان جا)؛ تازه یا بزرگ‌شده نه
  const claims = add.filter((w) => w.createdAt === undefined);
  if (phoneCap && claims.length) {
    //  هر کالا جدا (کلِ مقدارِ همان کالا در سفارش): کالایی که از سقف گذشت نگه‌داشتنِ کالای دیگرِ همان افزودن را نمی‌برد
    const over = new Set();
    for (const pid of new Set(claims.map((w) => w.productId))) {
      if (!(await phoneCapOk(c, shopId, orderId, [...want.values()].filter((w) => w.productId === pid), at))) over.add(pid);
    }
    if (over.size) {
      const grown = new Set(claims.filter((w) => over.has(w.productId)).map((w) => w.lineNo));
      //  همان قلم و همان کالا که فقط بزرگ شد ⇒ ردیفِ پیشین دست نمی‌خورد؛ ادعای تازهٔ همان کالاها کنار
      drop = drop.filter((r) => !(grown.has(num(r.line_no)) && want.get(num(r.line_no)).productId === r.product_id));
      add = add.filter((w) => w.createdAt !== undefined || !over.has(w.productId));
    }
  }
  if (!drop.length && !add.length) return;
  if (drop.length) {
    const ids = drop.map((r) => r.id);
    await c.query(
      `UPDATE stock_reservations SET status='released', closed_at=$3, close_reason='order_amended'
        WHERE shop_id=$1 AND id = ANY($2::text[]) AND status='held'`, [shopId, ids, at]
    );
    await postRelease(c, shopId, drop, 'release', 'order_amended', at);
  }
  await insertHolds(c, shopId, orderId, add, expiresAt, at);
  await recheckShort(c, shopId, [...new Set([...drop.map((r) => r.product_id), ...add.map((w) => w.productId)])].sort(), at);
}

/**
 * درونِ تراکنشِ گذارِ سفارش (`orders.applyTransition`، و «بازپرداختِ کامل» ِ `order-refunds`)، پس از قفلِ
 * ردیفِ سفارش: قبول ⇒ بی انقضا (یا دوباره / ۴۰۹)؛ تحویل ⇒ مهلتِ پشتیبان تا فروشِ برنامه؛ پایان ⇒ آزاد.
 * سفارشِ بی نگه‌داشتهٔ نرم ⇒ یک پرسشِ خالی و هیچ.
 */
async function onTransitionIn(c, shopId, orderId, to, at) {
  const accept = to === 'accepted';
  const out = HANDED_OVER.includes(to);
  const release = RELEASE_ON.includes(to);
  if (!accept && !out && !release) return;
  const any = (await c.query(
    `SELECT 1 FROM stock_reservations WHERE shop_id=$1 AND order_id=$2 AND kind='soft' LIMIT 1`, [shopId, orderId]
  )).rows.length > 0;
  if (!any) return;
  //  ⛔ R6: خاموش ⇒ قبول به نگه‌داشته دست نمی‌زند (بی‌مهلت نمی‌شود؛ جارو با مهلتِ خودش می‌بندد) و تحویل فقط کوتاه می‌کند
  const on = await stock.reserveFlagOf(shopId, c);
  if (accept && !on) return;
  await stock.lock(c, shopId);
  await expireDueIn(c, shopId, at);
  if (accept) {
    await c.query(
      `UPDATE stock_reservations SET expires_at=NULL WHERE shop_id=$1 AND order_id=$2 AND kind='soft' AND status='held'`,
      [shopId, orderId]
    );
    await reholdIn(c, shopId, orderId, at);
    return;
  }
  if (out) {
    await c.query(
      `UPDATE stock_reservations SET expires_at=${on ? '$3' : 'LEAST(expires_at, $3)'}
        WHERE shop_id=$1 AND order_id=$2 AND kind='soft' AND status='held'`,
      [shopId, orderId, at + HANDOVER_GRACE_MS]
    );
    return;
  }
  const rows = (await c.query(
    `UPDATE stock_reservations SET status='released', closed_at=$3, close_reason=$4
      WHERE shop_id=$1 AND order_id=$2 AND kind='soft' AND status='held'
      RETURNING id, product_id, order_id, qty_milli`, [shopId, orderId, at, `order_${to}`]
  )).rows;
  await postRelease(c, shopId, rows, 'release', `order_${to}`, at);
  //  کمبودِ سفارش‌های دیگرِ همان کالاها شاید حالا پوشش دارد
  if (rows.length) await recheckShort(c, shopId, [...new Set(rows.map((r) => r.product_id))].sort(), at);
}

/**
 * «بررسی»: تازه‌ترین نگه‌داشته‌هایی که موجودیِ قابل‌فروش دیگر پوششان نمی‌دهد `short_at` می‌گیرند. پرچمِ خاموش (R6) ⇒ همین
 * دفترداری (نادیده تا روشنِ دوباره، که همان دم درست است) ولی بی وظیفه و بی خبر.
 */
async function recheckShort(c, shopId, ids, at, settings = null) {
  if (!ids.length) return [];
  const on = await stock.reserveFlagOf(shopId, c);
  const s = settings || await stock.settingsOf(shopId, c);
  const lvs = await stock.levelsOf(c, shopId, ids);
  const rows = (await c.query(
    `SELECT id, order_id, product_id, qty_milli, short_at FROM stock_reservations
      WHERE shop_id=$1 AND kind='soft' AND status='held' AND product_id = ANY($2::text[])
        AND (expires_at IS NULL OR expires_at > $3)
      ORDER BY product_id, created_at, id`, [shopId, ids, at]
  )).rows;
  const byPid = new Map();
  for (const r of rows) {
    if (!byPid.has(r.product_id)) byPid.set(r.product_id, []);
    byPid.get(r.product_id).push(r);
  }
  const shortAll = [];
  const newly = new Set();
  for (const [pid, list] of byPid) {
    const sell = Math.max(0, stock.sellableOnHand(lvs.get(pid), s));
    let cum = 0;
    const short = [];
    for (const r of list) {
      cum += num(r.qty_milli);
      const isShort = cum > sell;
      if (isShort) short.push(r);
      if (isShort && r.short_at === null) {
        await c.query('UPDATE stock_reservations SET short_at=$2 WHERE id=$1', [r.id, at]);
        newly.add(r.order_id);
      } else if (!isShort && r.short_at !== null) {
        await c.query('UPDATE stock_reservations SET short_at=NULL WHERE id=$1', [r.id]);
      }
    }
    if (short.length && on) {
      await stock.openTask(c, shopId, {
        kind: 'reservation_short', productId: pid, dedupe: `soft-short:${pid}`,
        detail: { sellableMilli: sell, heldMilli: cum, orderIds: [...new Set(short.map((r) => r.order_id))].slice(0, 50) },
      }, at);
    }
    shortAll.push(...short);
  }
  if (on) for (const orderId of [...newly].sort()) await notifyReviewIn(c, shopId, orderId, at);
  return shortAll;
}

/**
 * «سفارش به بررسی رفت» (`order_review`، طرحِ #429) ⇒ همان صفِ پایدارِ اعلانِ سفارش، در همین تراکنش (بی بررسی، بی
 * خبر) و پشتِ هر دو پرچم؛ هر سفارش یک بار (یکتاییِ پایگاه). زیرِ SAVEPOINT: نوشته نشدنِ خبر (مثلاً CHECK ِ کهنه پس از
 * rollback ِ ۵۲۰) هرگز کارِ موجودی یا همگام‌سازی را پس نمی‌زند.
 */
async function notifyReviewIn(c, shopId, orderId, at) {
  await c.query('SAVEPOINT stock_review_notify');
  try {
    //  ⛔ SR42-2: ما قفلِ موجودی را داریم؛ گذار/ویرایش/بسته‌بندیِ همین سفارش ردیف را `FOR UPDATE` گرفته و منتظرِ همین قفل است ⇒
    //  منتظرِ قفلِ کلید (FK ِ صفِ اعلان) ماندن بن‌بست بود (۴۰P01 ⇒ گذارِ کارمند ۵۰۰). ردیفِ گرفته ⇒ بی پوش، همان دم؛ کمبود
    //  (`short_at`، نشانِ «در بررسی»، وظیفه) نوشته می‌ماند و همان سفارش همین لحظه در دستِ کارمند است.
    await c.query('SELECT 1 FROM online_orders WHERE id=$1 AND shop_id=$2 FOR KEY SHARE NOWAIT', [String(orderId || ''), String(shopId)]);
    await require('./order-notify').enqueueReviewIn((t, p) => c.query(t, p), { shopId, orderId, at });
    await c.query('RELEASE SAVEPOINT stock_review_notify');
  } catch (err) {
    await c.query('ROLLBACK TO SAVEPOINT stock_review_notify');
    if (err && err.code === '55P03') console.warn('[stock-reserve] اعلانِ بررسی کنار ماند: سفارش همین حالا در دستِ کارمند است', orderId);
    else console.error('[stock-reserve] اعلانِ بررسی', orderId, err.message);
  }
}

/**
 * از `stock.checkProducts` (پس از هر دسته حرکتِ همگام‌سازی، دستی یا انتقال؛ همان تراکنش): ۱) نگه‌داشتهٔ
 * قلمی که فروشِ تحویلش (`si:o-<سفارش>-<قلم>` یا `si:w-…`) در دفتر نشست ⇒ «انجام‌شده»؛ ۲) کمبود ⇒ بررسیِ
 * سفارش. بی نگه‌داشتهٔ نرمِ باز ⇒ یک پرسشِ خالی و هیچ.
 */
async function afterMovesIn(c, shopId, ids, settings, at) {
  const any = (await c.query(
    `SELECT 1 FROM stock_reservations WHERE shop_id=$1 AND kind='soft' AND status='held' LIMIT 1`, [shopId]
  )).rows.length > 0;
  if (!any) return;
  await stock.lock(c, shopId);   //  همه از پیش گرفته‌اند؛ قفلِ تراکنشی دوباره‌گیر است
  await expireDueIn(c, shopId, at);
  const done = (await c.query(
    `UPDATE stock_reservations r SET status='committed', closed_at=$2, close_reason='fulfilled'
      WHERE r.shop_id=$1 AND r.kind='soft' AND r.status='held'
        AND EXISTS (SELECT 1 FROM stock_sources s WHERE s.shop_id = r.shop_id AND s.posted_milli <> 0
                     AND s.source_key IN ('si:o-' || r.order_id || '-' || r.line_no, 'si:w-' || r.order_id || '-' || r.line_no))
      RETURNING r.id, r.product_id, r.order_id, r.qty_milli`, [shopId, at]
  )).rows;
  await postRelease(c, shopId, done, 'commit', 'order_fulfilled', at);
  await recheckShort(c, shopId, [...new Set([...(ids || []), ...done.map((r) => r.product_id)])].sort(), at, settings);
}

/** خلاصهٔ نگه‌داشتنِ یک سفارش برای جزئیات ⇒ null (بی نگه‌داشتهٔ نرم یا پرچمِ خاموش، R6) یا { status, review, expiresAt, lines }. */
async function orderView(run, shopId, orderId, at = now()) {
  const rows = (await run(
    `SELECT line_no, status, expires_at, short_at FROM stock_reservations
      WHERE shop_id=$1 AND order_id=$2 AND kind='soft' ORDER BY line_no, created_at, id`, [shopId, orderId]
  )).rows;
  if (!rows.length || !(await stock.reserveFlagOf(shopId, { query: run }))) return null;
  const byLine = new Map();
  for (const r of rows) byLine.set(num(r.line_no), r);   //  آخرین ردیفِ هر قلم
  const lines = [...byLine.values()].map((r) => {
    let st = r.status;
    if (st === 'held' && r.expires_at !== null && num(r.expires_at) <= at) st = 'expired';
    return { lineNo: num(r.line_no), status: st, short: st === 'held' && r.short_at !== null, expiresAt: st === 'held' && r.expires_at !== null ? num(r.expires_at) : null };
  });
  let status = 'released';
  if (lines.some((l) => l.status === 'held')) status = 'held';
  else if (lines.some((l) => l.status === 'expired')) status = 'expired';
  else if (lines.some((l) => l.status === 'committed')) status = 'committed';
  const exp = lines.map((l) => l.expiresAt).filter((x) => x !== null);
  return {
    status, review: lines.some((l) => l.short),
    expiresAt: exp.length ? Math.min(...exp) : null,
    lines: lines.map(({ lineNo, status: st, short }) => ({ lineNo, status: st, short })),
  };
}

/** از میانِ [orderIds] آن‌هایی که در بررسی‌اند ⇒ Set (کارتِ فهرستِ سفارش‌ها). بی شناسه ⇒ بی پرسش؛ پرچمِ خاموش (R6) ⇒ هیچ. */
async function reviewIdsOf(run, shopId, orderIds, at = now()) {
  const out = new Set();
  if (!orderIds || !orderIds.length) return out;
  const rows = (await run(
    `SELECT DISTINCT order_id FROM stock_reservations
      WHERE shop_id=$1 AND kind='soft' AND status='held' AND short_at IS NOT NULL
        AND (expires_at IS NULL OR expires_at > $2) AND order_id = ANY($3::text[])`, [shopId, at, orderIds]
  )).rows;
  if (rows.length && !(await stock.reserveFlagOf(shopId, { query: run }))) return out;
  for (const r of rows) out.add(r.order_id);
  return out;
}

/** سقفِ ردیف‌های یک پاسخِ صندوق ([posView]) — بیشتر ⇒ `truncated` (فقط نمایش؛ کمتر شمردن بهتر از پاسخِ بی‌سقف) */
const POS_MAX = 2000;

/**
 * POS-05/INV-03 — نگه‌داشته‌های فعالِ سفارشِ آنلاین برای کاشیِ صندوق (`GET /api/shop/catalog/reserved`). همان شرطِ [heldOf]
 * (نرم، نگه‌داشته، نگذشته)؛ هر قلم یک ردیف با کلیدِ فروشِ تحویلِ همان قلم (`o-<سفارش>-<قلم>` = `OrderFulfillment.itemIdOf`) تا
 * صندوقی که آن فروش را ساخته و هنوز نفرستاده دو بار کم نکند (همان کلیدی که کسرِ قطعی با آن رزرو را می‌بندد). `expiresAt`
 * با `now` ِ همین سرور می‌رود تا برنامه مهلت را با ساعتِ خودش نسنجد. پرچمِ خاموش (R6) ⇒ `enabled: false` و هیچ ردیف.
 */
async function posView(shopId, at = now()) {
  if (!(await enabled(shopId))) return { enabled: false, now: at, lines: [], truncated: false };
  const rows = (await query(
    `SELECT product_id, order_id, line_no, qty_milli, expires_at FROM stock_reservations
      WHERE shop_id=$1 AND kind='soft' AND status='held' AND (expires_at IS NULL OR expires_at > $2)
      ORDER BY created_at, id LIMIT $3`, [shopId, at, POS_MAX + 1]
  )).rows;
  return {
    enabled: true, now: at, truncated: rows.length > POS_MAX,
    lines: rows.slice(0, POS_MAX).map((r) => ({
      productId: r.product_id, qtyMilli: num(r.qty_milli), saleItemId: `o-${r.order_id}-${num(r.line_no)}`,
      expiresAt: r.expires_at === null ? null : num(r.expires_at),
    })),
  };
}

/** جاروی دوره‌ای: نگه‌داشته‌های گذشته ⇒ بسته در ردیف و دفتر (هر دکان در تراکنش و قفلِ خودش). */
async function sweep(at = now()) {
  const shops = await many(
    `SELECT DISTINCT shop_id FROM stock_reservations
      WHERE status='held' AND kind='soft' AND expires_at IS NOT NULL AND expires_at <= $1 LIMIT $2`, [at, BATCH]
  );
  let n = 0;
  for (const { shop_id: shopId } of shops) {
    try {
      n += await tx(async (c) => {
        await stock.lock(c, shopId);
        return expireDueIn(c, shopId, at);
      });
    } catch (err) {
      console.error('[stock-reserve] sweep', shopId, err.message);
    }
  }
  return n;
}

// ════════════════════════════════════════════════════════════════════
//  S86 — بازسازیِ projection ِ نگه‌داشته از دفتر و سفارش‌ها
// ════════════════════════════════════════════════════════════════════

const HOLD_KEY = /^res:(.+):hold$/;

/** کلیدِ فروشِ تحویلِ همان قلم در دفتر (`afterMovesIn`) */
const saleKeys = (orderId, lineNo) => [`si:o-${orderId}-${lineNo}`, `si:w-${orderId}-${lineNo}`];

/**
 * S86 (§۱۹.۱۲) — بازسازیِ `stock_reservations` ِ نرم (O-06 ب) از دو منبع: دفترِ موجودی (هر نگه‌داشتن یک حرکتِ
 * `res:<ردیف>:hold` و هر بستن `…:release|commit`) و سفارش‌ها (وضعیت، قلم‌های امروز به واحدِ موجودی، فروشِ تحویل در
 * `stock_sources`). هر نگه‌داشته‌ای که باید باشد و نیست، یا هست و نباید باشد:
 *   کهنه ──────── ردیفِ «نگه‌داشته» که دفتر بسته (`release|commit`) یا هرگز نگه نداشته ⇒ همان بستنِ دفتر، **بی حرکتِ تازه**
 *                 (SR47-1: داور دفتر است — نه بستنِ دوم با پسوندِ دیگر، نه نگه‌داشتهٔ شبح که جای نگه‌داشتنِ بازِ دفتر را بگیرد)
 *   گم ────────── نگه‌داشتنِ بازِ دفتر بی ردیف ⇒ همان ردیف با همان شناسه (قلمِ همان کالا در سفارش؛ مهلت از وضعیتِ سفارش)
 *   پایان ─────── سفارشِ بسته (`RELEASE_ON`) یا نبوده ⇒ `released`
 *   گذشته ─────── مهلت سر رسید ⇒ `expired` (همان `expireDueIn`)
 *   تحویل ─────── فروشِ همان قلم در دفتر نشست ⇒ `committed` (همان `afterMovesIn`)
 *   ناهم‌اندازه ── کالا/مقدار با قلمِ امروز نمی‌خواند ⇒ آزاد و، اگر قلم هست، نگه‌داشتنِ تازه با همان مهلت (همان `syncOrderIn`؛
 *                 ⛔ R6: پرچمِ خاموش ⇒ فقط کوچک/هم‌اندازه؛ قلمِ بزرگ‌شده‌ای که از سقفِ شماره می‌گذرد همان اندازهٔ پیشین می‌ماند)
 * هر بستن حرکتِ آزادسازیِ خودش را دارد ⇒ تطبیقِ INV-16 (`stock.reconcile`) پس از آن سالم.
 *   • پیش‌فرض فقط نشان می‌دهد (هیچ نوشتنی)؛ با `apply` زیرِ قفلِ موجودیِ دکان و در یک تراکنش با سابقهٔ `reserve.rebuild`؛
 *   • بی تفاوت ⇒ هیچ نوشتن و هیچ سابقه ⇒ بارِ دوم بی‌اثر؛ دکانِ دیگر، ردیفِ `legacy` و سفارش هرگز دست نمی‌خورند.
 *   • قلمِ بازی که هیچ ردِ نگه‌داشتنی در دفتر ندارد ساخته نمی‌شود: پرچم یا موجودیِ «تازه» هنگامِ ثبت شاید نبود (همان سفارشِ بی رزرو).
 * @returns {{ok, applied, flag, count: {projection, source}, changes: Array<{kind, id, orderId, lineNo, productId, qtyMilli, toMilli?}>}}
 */
async function rebuild(shopId, { apply = false, by = '' } = {}) {
  if (!shopId) throw require('../middleware/errors').badRequest('شناسهٔ دکان لازم است', 'shop_required');
  return tx(async (c) => {
    await stock.lock(c, shopId);
    const at = now();
    const on = await stock.reserveFlagOf(shopId, c);
    const ttlMs = (await ttlSecOf(c, shopId)) * 1000;
    const known = new Set((await c.query('SELECT id FROM stock_reservations WHERE shop_id=$1', [shopId])).rows.map((r) => r.id));
    const held = (await c.query(
      `SELECT id, order_id, line_no, product_id, qty_milli, expires_at, created_at FROM stock_reservations
        WHERE shop_id=$1 AND kind='soft' AND status='held' ORDER BY created_at, id FOR UPDATE`, [shopId]
    )).rows.map((r) => ({ id: r.id, orderId: r.order_id, lineNo: num(r.line_no), productId: r.product_id, qtyMilli: num(r.qty_milli),
      expiresAt: r.expires_at === null ? null : num(r.expires_at), createdAt: num(r.created_at), lost: false }));
    //  نگه‌داشتنِ بازِ دفتر (بی `release`/`commit`) که ردیفی ندارد — همان امضای `insertHolds`
    const ledgerOpen = (await c.query(
      `SELECT h.source_key, h.product_id, h.qty_milli, h.ref_id, h.at FROM stock_moves h
        WHERE h.shop_id=$1 AND h.type='reserve' AND h.source='order' AND h.reason='online_order' AND h.source_key LIKE 'res:%:hold'
          AND NOT EXISTS (SELECT 1 FROM stock_moves x WHERE x.shop_id=h.shop_id AND x.source_key IN
                (substr(h.source_key, 1, length(h.source_key) - 4) || 'release', substr(h.source_key, 1, length(h.source_key) - 4) || 'commit'))
        ORDER BY h.at, h.source_key`, [shopId]
    )).rows.filter((m) => HOLD_KEY.test(m.source_key));
    const openIds = new Set(ledgerOpen.map((m) => HOLD_KEY.exec(m.source_key)[1]));
    const open = ledgerOpen.filter((m) => !known.has(HOLD_KEY.exec(m.source_key)[1]));
    //  SR47-1: ردیفِ «نگه‌داشته» ای که دفتر نگه نمی‌دارد ⇒ همان بستنِ دفتر (`commit` ⇒ تحویل، وگرنه آزاد)
    const stale = held.filter((r) => !openIds.has(r.id));
    const committedIn = new Set(stale.length ? (await c.query(
      'SELECT source_key FROM stock_moves WHERE shop_id=$1 AND source_key = ANY($2::text[])', [shopId, stale.map((r) => `res:${r.id}:commit`)]
    )).rows.map((m) => m.source_key) : []);
    const orderIds = [...new Set([...held.map((r) => r.orderId), ...open.map((m) => m.ref_id)])];
    const rowsOf = (await c.query('SELECT id, status, created_at FROM online_orders WHERE shop_id=$1 AND id = ANY($2::text[])',
      [shopId, orderIds])).rows;
    const orders = new Map(rowsOf.map((o) => [o.id, o.status]));
    const placed = new Map(rowsOf.map((o) => [o.id, num(o.created_at)]));
    const wants = new Map();
    const wantOfOrder = async (id) => {
      if (!wants.has(id)) wants.set(id, await wantOf(c, shopId, id));
      return wants.get(id);
    };
    const isOpen = (id) => orders.has(id) && !RELEASE_ON.includes(orders.get(id));
    //  ۱) ردیفِ گم از دفتر: قلمِ همان کالا که نگه‌داشتهٔ دیگری ندارد؛ مهلت از وضعیتِ امروزِ سفارش — «تازه» (یا پرچمِ خاموش، R6)
    //     ⇒ همان مهلتِ ثبت (`created_at` ِ سفارش + مهلت)، تحویل‌شده ⇒ مهلتِ پشتیبان از همین حالا، بقیه ⇒ تا پایانِ سفارش
    const taken = new Set(held.filter((r) => openIds.has(r.id)).map((r) => `${r.orderId}\u0000${r.lineNo}`));
    for (const m of open) {
      const orderId = m.ref_id;
      const holdAt = num(m.at);
      const st = orders.get(orderId);
      let lineNo = 0;
      if (isOpen(orderId)) {
        const line = [...(await wantOfOrder(orderId)).values()]
          .find((w) => w.productId === m.product_id && !taken.has(`${orderId}\u0000${w.lineNo}`));
        if (line) lineNo = line.lineNo;
      }
      if (lineNo) taken.add(`${orderId}\u0000${lineNo}`);
      let expiresAt = null;
      if (st === 'new' || !on) expiresAt = (placed.get(orderId) || holdAt) + ttlMs;
      else if (HANDED_OVER.includes(st)) expiresAt = at + HANDOVER_GRACE_MS;
      held.push({ id: HOLD_KEY.exec(m.source_key)[1], orderId, lineNo, productId: m.product_id, qtyMilli: num(m.qty_milli),
        expiresAt, createdAt: holdAt, lost: true });
    }
    //  ۲) هر نگه‌داشته در برابرِ سفارشِ امروز
    const sold = new Set((await c.query(
      'SELECT DISTINCT source_key FROM stock_sources WHERE shop_id=$1 AND posted_milli <> 0 AND source_key = ANY($2::text[])',
      [shopId, held.flatMap((r) => saleKeys(r.orderId, r.lineNo))]
    )).rows.map((r) => r.source_key));
    const changes = [];
    for (const r of held) {
      const base = { id: r.id, orderId: r.orderId, lineNo: r.lineNo, productId: r.productId, qtyMilli: r.qtyMilli };
      if (!openIds.has(r.id)) changes.push({ kind: 'stale', ...base, r });
      else if (!isOpen(r.orderId)) changes.push({ kind: 'released', ...base, r });
      else if (r.expiresAt !== null && r.expiresAt <= at) changes.push({ kind: 'expired', ...base, r });
      else if (saleKeys(r.orderId, r.lineNo).some((k) => sold.has(k))) changes.push({ kind: 'committed', ...base, r });
      else {
        const w = r.lineNo ? (await wantOfOrder(r.orderId)).get(r.lineNo) : null;
        //  CHK-18 (۲-۲۱): قلمی که مشتری بزرگ کرد و از سقفِ شماره گذشت عمداً همان اندازهٔ پیشین را نگه می‌دارد ([syncOrderIn]
        //  با `phoneCap`) ⇒ «ناهم‌اندازه» نیست، وگرنه بازسازی همان سقف را دور می‌زد
        const capped = on && w && w.productId === r.productId && w.qtyMilli > r.qtyMilli
          && !(await phoneCapOk(c, shopId, r.orderId, [...(await wantOfOrder(r.orderId)).values()].filter((x) => x.productId === r.productId), at));
        if (capped || (w && w.productId === r.productId && w.qtyMilli === r.qtyMilli)) {
          if (r.lost) changes.push({ kind: 'restored', ...base, r });
          continue;
        }
        //  ⛔ R6: خاموش ⇒ نگه‌داشتنِ تازه فقط برای همان کالا و نه بیشتر از پیش (و جای صف همان)
        const keep = w && w.productId === r.productId && w.qtyMilli <= r.qtyMilli;
        const add = w && (on || keep) ? { ...w, ...(keep ? { createdAt: r.createdAt } : {}) } : null;
        changes.push({ kind: 'resized', ...base, toMilli: add ? add.qtyMilli : 0, r, add });
      }
    }
    const view = changes.map(({ r, add, ...x }) => x);
    //  شمارِ نگه‌داشته‌ها: امروز (ردیف) و آن‌چه منبع می‌گوید (پس از بازسازی)
    const closing = changes.filter((x) => x.kind !== 'restored').length;
    const count = { projection: held.filter((r) => !r.lost).length,
      source: held.length - closing + changes.filter((x) => x.add).length };
    if (!apply || !changes.length) return { ok: !changes.length, applied: false, flag: on, count, changes: view };

    const CLOSE = { released: ['released', 'release', 'rebuild_order_closed'], expired: ['expired', 'release', 'ttl'],
      committed: ['committed', 'commit', 'fulfilled'], resized: ['released', 'release', 'rebuild_line_changed'] };
    const touched = new Set();
    for (const ch of changes) {
      const { r } = ch;
      touched.add(r.productId);
      if (ch.kind === 'restored') {
        await c.query(
          `INSERT INTO stock_reservations (id, shop_id, order_id, line_no, product_id, branch_id, qty_milli, status, expires_at,
             created_at, kind) VALUES ($1,$2,$3,$4,$5,'',$6,'held',$7,$8,'soft')`,
          [r.id, shopId, r.orderId, r.lineNo, r.productId, r.qtyMilli, r.expiresAt, r.createdAt]
        );
        continue;
      }
      if (ch.kind === 'stale') {
        const done = committedIn.has(`res:${r.id}:commit`);
        await c.query(
          `UPDATE stock_reservations SET status=$3, closed_at=$4, close_reason=$5 WHERE shop_id=$1 AND id=$2 AND status='held'`,
          [shopId, r.id, done ? 'committed' : 'released', at, done ? 'fulfilled' : 'rebuild_ledger_closed']
        );
        continue;
      }
      const [status, suffix, reason] = CLOSE[ch.kind];
      if (r.lost) {
        await c.query(
          `INSERT INTO stock_reservations (id, shop_id, order_id, line_no, product_id, branch_id, qty_milli, status, expires_at,
             created_at, closed_at, close_reason, kind) VALUES ($1,$2,$3,$4,$5,'',$6,$7,$8,$9,$10,$11,'soft')`,
          [r.id, shopId, r.orderId, r.lineNo, r.productId, r.qtyMilli, status, r.expiresAt, r.createdAt, at, reason]
        );
      } else {
        await c.query(
          `UPDATE stock_reservations SET status=$3, closed_at=$4, close_reason=$5 WHERE shop_id=$1 AND id=$2 AND status='held'`,
          [shopId, r.id, status, at, reason]
        );
      }
      await postRelease(c, shopId, [{ id: r.id, product_id: r.productId, order_id: r.orderId, qty_milli: r.qtyMilli }],
        suffix, `reservation_rebuild_${ch.kind}`, at);
      if (ch.add) {
        await insertHolds(c, shopId, r.orderId, [ch.add], r.expiresAt, at);
        touched.add(ch.add.productId);
      }
    }
    await recheckShort(c, shopId, [...touched].sort(), at);
    await require('./audit').logIn(c, {
      shopId, actorType: 'system', userId: String(by || '').slice(0, 64), action: 'reserve.rebuild', targetType: 'shop', targetId: shopId,
      detail: { count, changes: changes.length, items: view.slice(0, 50) },
    });
    return { ok: true, applied: true, flag: on, count, changes: view };
  });
}

let timer = null;
let sweeping = false;
async function tick() {
  if (sweeping) return 0;
  sweeping = true;
  try { return await sweep(); } finally { sweeping = false; }
}
function start() {
  if (timer) return;
  timer = setInterval(() => tick().catch((err) => console.error('[stock-reserve]', err.message)), TICK_MS);
  if (timer.unref) timer.unref();
}
function stop() {
  if (timer) clearInterval(timer);
  timer = null;
}

module.exports = {
  DEFAULT_TTL_SEC, HANDOVER_GRACE_MS, HANDED_OVER, RELEASE_ON,
  enabled, ttlSecOf, heldOf, expireDueIn, planIn, checkOrderIn, holdIn, syncOrderIn, onTransitionIn, recheckShort, afterMovesIn,
  orderView, reviewIdsOf, POS_MAX, posView, sweep, start, stop, rebuild,
};
