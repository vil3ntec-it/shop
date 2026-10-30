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
 *   خواندن ─ `since(seq)` برای `lib/market.js` (بی‌اعتبارسازیِ کش با `seq` به‌جای زمان).
 *   نگهداری ─ `prune()` در کارِ دوره‌ای: ردیف‌های کهنه‌تر از `config.market.changesKeepMs`،
 *           دسته‌ای و محدود.
 */
const crypto = require('crypto');
const { query, many, one, now } = require('../db');
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

module.exports = { FLAG_KEY, enabled, record, head, since, prune, sigOf };
