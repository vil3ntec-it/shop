'use strict';
/**
 * جریانِ تغییرِ بازار (S6، `docs/MARKETPLACE-CONTRACT-fa.md` §۳؛ جدولِ `market_changes`).
 *
 *   پرچم ── `market.changes` (دکان / `all`، پیش‌فرض خاموش؛ همان `orders.flagOf`).
 *           خاموش ⇒ هیچ ردیفی نوشته نمی‌شود و بازار همان کشِ زمانیِ امروز را دارد.
 *   نوشتن ─ `record(c, …)` فقط درونِ تراکنشِ خودِ تغییر (`c`)؛ اگر تغییر برگردد ردیف هم
 *           برمی‌گردد. `state` امضا (`sig`) می‌شود و اگر آخرین ردیفِ همان موجودیت همان
 *           امضا را دارد چیزی نوشته نمی‌شود ⇒ تکرارِ درخواست ردیفِ دوم نمی‌سازد. قفلِ
 *           مشورتیِ همان موجودیت دو تراکنشِ هم‌زمان را پشتِ هم می‌گذارد.
 *   خواندن ─ `since(seq)` برای `lib/market.js` (بی‌اعتبارسازیِ کش با `seq` به‌جای زمان):
 *           `sync` هر `changesPollMs` یک پرسشِ باریک «ردیف‌های پس از seq»؛ هر دکانِ تغییرکرده
 *           از حافظه‌های بازار بیرون می‌رود و برش (و با آن نمایهٔ `market_search`) از نو ساخته
 *           می‌شود. بی ردیف (پرچم خاموش) ⇒ هیچ چیز عوض نمی‌شود و همان کشِ زمانی می‌ماند؛ خطا ⇒
 *           همان کشِ زمانی.
 *   سیم‌ها ─ از `lib/storefront.js`، `lib/market.js` و `lib/catalog.js` جدا شد تا آن‌ها زیرِ سقفِ
 *           اندازه بمانند: `within` (نوشتن + ردیف در یک تراکنش، مثلِ «فهرستِ بازار»؛ مختصات فقط
 *           درونِ امضای sha1، نه خام در جدول)، `productTx` (انتشار/قیمتِ کالای ویترین) و
 *           `catalogProduct` (کالای کاتالوگ در همان تراکنشِ همگام‌سازی).
 *   نگهداری ─ `prune()` در کارِ دوره‌ای: ردیف‌های کهنه‌تر از `config.market.changesKeepMs`،
 *           دسته‌ای و محدود.
 */
const crypto = require('crypto');
const { query, many, one, now, tx } = require('../db');
const config = require('../config');

const FLAG_KEY = 'market.changes';
const ENTITIES = new Set(['shop', 'presence', 'listing', 'product', 'sanction']);
const PRUNE_BATCH = 5000;

/** پرچمِ همین دکان؛ با `c` درونِ همان تراکنش خوانده می‌شود. */
async function enabled(shopId, c = null) {
  if (!c) return require('./orders').flagOf(shopId, FLAG_KEY);
  const rows = (await c.query(
    'SELECT scope, value FROM live_config WHERE key = $1 AND scope IN ($2, $3)', [FLAG_KEY, 'all', String(shopId)]
  )).rows;
  const dec = (v) => { try { return JSON.parse(v); } catch { return v; } };
  let v;
  for (const r of rows) if (r.scope === 'all') v = dec(r.value);
  for (const r of rows) if (r.scope !== 'all') v = dec(r.value);
  return v === true;
}

const sigOf = (state) => crypto.createHash('sha1').update(JSON.stringify(state === undefined ? null : state)).digest('hex');

/**
 * یک ردیف در همان تراکنش. `on` اگر از پیش دانسته است (مثلاً یک بار برای همهٔ کالاهای
 * یک همگام‌سازی) پاس داده شود. ⇒ `seq` ِ تازه یا `null` (خاموش یا بی تغییر).
 */
async function record(c, shopId, { entity, entityId = '', kind, state, at = now() }, { on } = {}) {
  if (!ENTITIES.has(entity)) throw new Error(`market_changes: entity «${entity}» شناخته نیست`);
  if (!(on === undefined ? await enabled(shopId, c) : on)) return null;
  const id = String(entityId || '');
  //  امضا فقط از حالت (نه `kind`): همان حالت با برچسبِ دیگر هم «بی تغییر» است
  const sig = sigOf(state);
  await c.query('SELECT pg_advisory_xact_lock(hashtext($1))', [`market-changes:${shopId}:${entity}:${id}`]);
  const last = (await c.query(
    `SELECT sig FROM market_changes WHERE shop_id = $1 AND entity = $2 AND entity_id = $3 ORDER BY seq DESC LIMIT 1`,
    [shopId, entity, id]
  )).rows[0];
  if (last && last.sig === sig) return null;
  const r = await c.query(
    `INSERT INTO market_changes (shop_id, entity, entity_id, kind, sig, at) VALUES ($1,$2,$3,$4,$5,$6) RETURNING seq`,
    [shopId, entity, id, kind, sig, at]
  );
  return Number(r.rows[0].seq);
}

/** بزرگ‌ترین `seq` تا این لحظه (۰ اگر خالی). */
async function head() {
  const r = await one('SELECT COALESCE(MAX(seq), 0) AS seq FROM market_changes');
  return Number(r.seq);
}

/** ردیف‌های پس از `seq`، به ترتیب، حداکثر `limit`. */
async function since(seq, limit = 1000) {
  const rows = await many(
    'SELECT seq, shop_id, entity, entity_id, kind, at FROM market_changes WHERE seq > $1 ORDER BY seq LIMIT $2',
    [seq, limit]
  );
  return rows.map((r) => ({ seq: Number(r.seq), shopId: r.shop_id, entity: r.entity, entityId: r.entity_id, kind: r.kind, at: Number(r.at) }));
}

/**
 * پاک‌سازی: کهنه‌تر از `keepMs`. آخرین ردیفِ کلِ جدول هرگز پاک نمی‌شود تا `seq` پس از
 * بازآغازِ سرور (و بی هیچ تغییرِ تازه) از صفر برنگردد.
 */
async function prune({ keepMs = config.market.changesKeepMs, at = now(), maxBatches = 20 } = {}) {
  const cutoff = at - Math.max(0, Number(keepMs) || 0);
  let removed = 0;
  for (let i = 0; i < maxBatches; i++) {
    const r = await query(
      `DELETE FROM market_changes WHERE seq IN (
         SELECT seq FROM market_changes WHERE at < $1 AND seq < (SELECT MAX(seq) FROM market_changes)
          ORDER BY seq LIMIT $2)`,
      [cutoff, PRUNE_BATCH]
    );
    removed += r.rowCount || 0;
    if ((r.rowCount || 0) < PRUNE_BATCH) break;
  }
  return { removed };
}

// ── سیم‌ها ─────────────────────────────────────────────────────────────

/** نوشتنِ `fn(c)` و ردیفِ تغییرِ `change` در یک تراکنش ⇒ همان خروجیِ `fn`. */
function within(shopId, change, fn) {
  return tx(async (c) => {
    const out = await fn(c);
    await record(c, shopId, change);
    return out;
  });
}

/**
 * انتشار/برداشتن، قیمت یا بقیهٔ کالای ویترین: `fn(c)` ردیفِ `storefront_products` را می‌نویسد و
 * `stateOf(ردیف)` حالتِ تازه را می‌دهد؛ نوعِ تغییر از حالتِ پیشینِ قفل‌شده. تکرارِ همان درخواست
 * ردیفِ دوم نمی‌سازد (امضای حالت).
 */
function productTx(shopId, id, at, fn, stateOf) {
  return tx(async (c) => {
    const prev = (await c.query(
      'SELECT published, online_price_minor FROM storefront_products WHERE shop_id = $1 AND product_id = $2 FOR UPDATE',
      [shopId, id]
    )).rows[0];
    const saved = await fn(c);
    const state = stateOf(saved);
    const wasPub = prev ? prev.published === true : false;
    const prevPrice = prev && prev.online_price_minor !== null ? Number(prev.online_price_minor) : null;
    const kind = wasPub !== state.published ? (state.published ? 'publish' : 'unpublish')
      : prevPrice !== state.onlinePriceMinor ? 'price' : 'update';
    await record(c, shopId, { entity: 'product', entityId: id, kind, at, state });
    return saved;
  });
}

/**
 * کالای کاتالوگ (نام، قیمت، فعال/حذف) در همان تراکنشِ همگام‌سازی (`catalog.upsertProduct`، فقط وقتی
 * `ingestTx` پرچم را یک بار برای کلِ همان خواندن روشن دیده). همان حالت ⇒ بی ردیف.
 */
function catalogProduct(c, shopId, row, name, price, active, at) {
  const deleted = row.deleted_at !== null && row.deleted_at !== undefined;
  return record(c, shopId, {
    entity: 'product', entityId: row.row_id, kind: deleted ? 'unpublish' : 'update', at,
    state: { source: 'catalog', name, priceMinor: price !== null && price > 0 ? price : null, active, deleted },
  }, { on: true });
}

const CHANGES_BATCH = 1000;
const changes = { seq: null, at: 0, flight: null };

/**
 * `cache`: حافظه‌های بازار — `forget` (Mapهای کلیدخورده با شناسهٔ دکان یا `دکان\0…`)، `reset` (برش و
 * «ارزان‌تر») و `all` (همه، پس از بیش از یک دسته).
 */
async function pullChanges(cache, counters) {
  if (changes.seq === null) { changes.seq = await head(); return; }
  const rows = await since(changes.seq, CHANGES_BATCH);
  if (!rows.length) return;
  if (rows.length >= CHANGES_BATCH) cache.all();
  else {
    for (const id of new Set(rows.map((r) => r.shopId))) {
      const pre = `${id}\u0000`;
      for (const m of cache.forget) {
        m.delete(id);
        for (const k of m.keys()) if (typeof k === 'string' && k.startsWith(pre)) m.delete(k);
      }
    }
    cache.reset();
  }
  changes.seq = rows[rows.length - 1].seq;
  counters.changes = (counters.changes || 0) + rows.length;
}

/** پیش از برشِ بازار: تغییرهای تازه (یک‌پرواز، هر `changesPollMs`). */
function sync(cache, counters) {
  if (changes.flight) return changes.flight;
  const every = Math.max(0, Number(config.market.changesPollMs) || 0);
  if (every > 0 && Date.now() - changes.at < every) return Promise.resolve();
  changes.at = Date.now();
  changes.flight = pullChanges(cache, counters)
    .catch((err) => { console.error('[market-changes]', err.message); })
    .finally(() => { changes.flight = null; });
  return changes.flight;
}

module.exports = { FLAG_KEY, enabled, record, head, since, prune, sigOf, within, productTx, catalogProduct, sync };
