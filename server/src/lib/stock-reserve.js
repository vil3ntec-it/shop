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
 *           `short_at` می‌گیرند، وظیفهٔ `reservation_short` (`soft-short:<کالا>`) و `stockHold.review`.
 *   دفتر ─ هر نگه‌داشتن/آزادسازی یک حرکتِ `reserve`/`release` با کلیدِ یکتا (`res:<ردیف>:hold|release|commit`)
 *           ⇒ تطبیقِ INV-16 (`stock.reconcile`) سالم می‌ماند.
 *
 *   ویرایش ─ ORD-14 و CHK-21 ([syncOrderIn]): نگه‌داشتهٔ بازِ سفارش هم‌اندازهٔ قلم‌های تازه می‌شود؛ افزودن با همان
 *           سنجشِ ساخت (نگه‌داشتهٔ خودِ سفارش «دیگران» نیست).
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
 * نگه‌داشتهٔ فعال (نگه‌داشته و نگذشته) به تفکیکِ کالا ⇒ Map<pid, milli>. بی ردیف ⇒ Map ِ خالی.
 * [exceptOrderId]: نگه‌داشتهٔ همان سفارش «دیگران» نیست (افزودنِ قلم، CHK-21: مقدارِ کلِ سفارش سنجیده می‌شود).
 */
async function heldOf(c, shopId, ids, at = now(), exceptOrderId = '') {
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
 * یک ردیفِ نگه‌داشته + حرکتِ `reserve` برای هر قلم. `expiresAt = null` ⇒ تا پایانِ سفارش. `l.createdAt` (هم‌اندازه
 * کردنِ قلمِ موجود) ⇒ جای همان قلم در صفِ «بررسی» ([recheckShort]: تازه‌ترین‌ها کم می‌آورند) نمی‌رود.
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
  return { held: await heldOf(c, shopId, ids, at, orderId), ttlSec: await ttlSecOf(c, shopId) };
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

/** پس از درجِ سفارش، در همان تراکنش و زیرِ همان قفل. */
async function holdIn(c, shopId, { orderId, lines, ttlSec, at }) {
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
  await stock.check(c, shopId, { lines: lapsed, settings, names, held: await heldOf(c, shopId, ids, at) });
  await insertHolds(c, shopId, orderId, lapsed, null, at);
}

/**
 * پس از عوض شدنِ قلم‌های سفارش (ویرایش ORD-14، افزودن CHK-21) در همان تراکنش: نگه‌داشتهٔ **باز** ِ سفارش هم‌اندازهٔ
 * قلم‌های تازه می‌شود — قلمِ برداشته یا کم/زیادشده ⇒ ردیفِ پیشین آزاد و (اگر هنوز هست) ردیفِ تازه با همان مهلت؛ قلمِ
 * تازه ⇒ نگه‌داشتنِ تازه با همان مهلت. سنجشِ «آزاد» پیش‌تر با [checkOrderIn] (و نگه‌داشتهٔ خودی کنار) انجام شده؛ ویرایشِ
 * بی سنجش (کارمند) هرگز رد نمی‌شود و کمبود به بررسی می‌رود (INV-19). سفارشِ بی نگه‌داشتهٔ باز ⇒ هیچ.
 */
async function syncOrderIn(c, shopId, orderId, at) {
  const heldSql = `SELECT id, order_id, line_no, product_id, qty_milli, expires_at, created_at FROM stock_reservations
      WHERE shop_id=$1 AND order_id=$2 AND kind='soft' AND status='held' ORDER BY line_no`;
  if (!(await c.query(heldSql, [shopId, orderId])).rows.length) return;
  await stock.lock(c, shopId);
  await expireDueIn(c, shopId, at);
  const held = (await c.query(heldSql, [shopId, orderId])).rows;
  if (!held.length) return;
  const exp = held.map((r) => (r.expires_at === null ? null : num(r.expires_at)));
  const expiresAt = exp.includes(null) ? null : Math.min(...exp);
  //  به واحدِ موجودی؛ کالایی که دیگر در کاتالوگ نیست ⇒ قلمش نگه‌داشته ندارد (آزاد می‌شود)
  const want = await wantOf(c, shopId, orderId);
  const byLine = new Map(held.map((r) => [num(r.line_no), r]));
  const drop = held.filter((r) => {
    const w = want.get(num(r.line_no));
    return !w || w.productId !== r.product_id || w.qtyMilli !== num(r.qty_milli);
  });
  const add = [...want.values()].filter((w) => {
    const r = byLine.get(w.lineNo);
    return !r || drop.includes(r);
  }).map((w) => (byLine.get(w.lineNo) ? { ...w, createdAt: byLine.get(w.lineNo).created_at } : w));
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
      `UPDATE stock_reservations SET expires_at=$3 WHERE shop_id=$1 AND order_id=$2 AND kind='soft' AND status='held'`,
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

/** «بررسی»: تازه‌ترین نگه‌داشته‌هایی که موجودیِ قابل‌فروش دیگر پوششان نمی‌دهد `short_at` می‌گیرند. */
async function recheckShort(c, shopId, ids, at, settings = null) {
  if (!ids.length) return [];
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
      } else if (!isShort && r.short_at !== null) {
        await c.query('UPDATE stock_reservations SET short_at=NULL WHERE id=$1', [r.id]);
      }
    }
    if (short.length) {
      await stock.openTask(c, shopId, {
        kind: 'reservation_short', productId: pid, dedupe: `soft-short:${pid}`,
        detail: { sellableMilli: sell, heldMilli: cum, orderIds: [...new Set(short.map((r) => r.order_id))].slice(0, 50) },
      }, at);
      shortAll.push(...short);
    }
  }
  return shortAll;
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

/** خلاصهٔ نگه‌داشتنِ یک سفارش برای جزئیات ⇒ null (بی نگه‌داشتهٔ نرم) یا { status, review, expiresAt, lines }. */
async function orderView(run, shopId, orderId, at = now()) {
  const rows = (await run(
    `SELECT line_no, status, expires_at, short_at FROM stock_reservations
      WHERE shop_id=$1 AND order_id=$2 AND kind='soft' ORDER BY line_no, created_at, id`, [shopId, orderId]
  )).rows;
  if (!rows.length) return null;
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

/** از میانِ [orderIds] آن‌هایی که در بررسی‌اند ⇒ Set (کارتِ فهرستِ سفارش‌ها). بی شناسه ⇒ بی پرسش. */
async function reviewIdsOf(run, shopId, orderIds, at = now()) {
  const out = new Set();
  if (!orderIds || !orderIds.length) return out;
  const rows = (await run(
    `SELECT DISTINCT order_id FROM stock_reservations
      WHERE shop_id=$1 AND kind='soft' AND status='held' AND short_at IS NOT NULL
        AND (expires_at IS NULL OR expires_at > $2) AND order_id = ANY($3::text[])`, [shopId, at, orderIds]
  )).rows;
  for (const r of rows) out.add(r.order_id);
  return out;
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
  orderView, reviewIdsOf, sweep, start, stop,
};
