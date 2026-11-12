'use strict';
/**
 * IMG-PRO-17 (§۳۶.۱۲) — تغییرِ دسته‌ایِ عکسِ کالاهای دکان و برگرداندنِ همهٔ آن یک‌جا. جدول‌ها: ‎migrations/207…‎.
 *
 *   پرچم ── همان `shop.image_bank` (پیش‌فرض خاموش؛ سنجش در درِ `routes/shop-image-changes.js`) ⇒ خاموش: هیچ ردیفی.
 *   اعمال ── چند «انتخابِ عکس» با **همان** قاعدهٔ `image-bank.selectIn` (منبع، نسخهٔ تأییدشده، تطبیق و تأییدِ انسانی)
 *            در **یک تراکنش**: یا همه یا هیچ (شکستِ یک قلم ⇒ هیچ کالایی عوض نمی‌شود و خطا شمارهٔ همان قلم را دارد).
 *            وضعِ پیش و پسِ هر کالا در `shop_image_change_items`. کلیدِ تکرار (`key`) ⇒ همان دسته بی نوشتنِ دوباره؛
 *            همان کلید با قلم‌های دیگر ⇒ ۴۰۹ `key_reused`.
 *   برگشت ── هر کالا دقیقاً به وضعِ پیش از همین دسته (بی ردیفِ پیشین ⇐ ردیف پاک ⇒ همان عکسِ همگام‌شده یا «بی عکس»).
 *            ⛔ کالایی که پس از این دسته دوباره عوض شده (دستی یا دستهٔ تازه‌تر) ⇒ هیچ چیز برنمی‌گردد: ۴۰۹ `changed_since`
 *            با فهرستِ همان کالاها — اول دستهٔ تازه‌تر را برگردانید؛ هیچ تغییرِ تازه‌تری بی‌صدا پاک نمی‌شود.
 *            برگشتِ دوباره (تکرار پس از قطعِ شبکه) ⇒ همان پاسخ با `already: true`، بی نوشتن.
 *   سابقه ── `image.changes_applied` و `image.selected` ِ هر کالا؛ `image.changes_rolled_back` و `image.restored` ِ هر کالا
 *            — همه در **همان تراکنش** (`audit.log({client})`): کاری بی ردیفِ سابقه نمی‌ماند و سابقه‌ای بی کار نه.
 *   ⛔ `images.use_count` (شمارِ انتخاب‌ها) با برگشت کم نمی‌شود؛ بایت و نسخه‌ای پاک نمی‌شود — فقط انتخابِ کالا برمی‌گردد.
 */
const crypto = require('crypto');
const { many, now, tx } = require('../db');
const bank = require('./image-bank');
const audit = require('./audit');
const photos = require('./product-photos');
const { ulid } = require('./ulid');
const { stripControl } = require('./validate');
const { ApiError, badRequest, notFound } = require('../middleware/errors');

const MAX_ITEMS = 100;
const LIST_MAX = 50;
/** ستون‌هایی که «کدام عکس برای این کالا» را می‌سازند — پیش و پس فقط همین‌ها */
const COLS = Object.freeze([
  'source', 'image_id', 'version', 'match_status', 'confirmed_by',
  'central_image_id', 'central_version', 'central_match', 'central_confirmed_by', 'bank_product_id',
]);
const NUM = new Set(['version', 'central_version']);

const str = (v, max) => stripControl(String(v == null ? '' : v)).trim().slice(0, max);
const q1 = async (c, t, p) => (await c.query(t, p)).rows[0] || null;
const parse = (t) => { try { return t == null ? null : JSON.parse(t); } catch { return null; } };

/** ردیفِ انتخاب ⇒ فقط ستون‌های تصمیم، با نوعِ یکسان */
function pick(row) {
  if (!row) return null;
  const out = {};
  for (const k of COLS) out[k] = NUM.has(k) ? Number(row[k] || 0) : String(row[k] == null ? '' : row[k]);
  return out;
}

const same = (a, b) => !!a && !!b && COLS.every((k) => a[k] === b[k]);

/** قلم‌های درخواست ⇒ شکلِ یکسان (برای سنجشِ کلیدِ تکرار) — اعتبارِ کامل را خودِ `selectIn` می‌سنجد */
function itemsOf(body) {
  const raw = body && body.items;
  if (!Array.isArray(raw) || raw.length < 1 || raw.length > MAX_ITEMS) {
    throw badRequest(`فهرستِ کالاها: ۱ تا ${MAX_ITEMS} قلم`, 'bad_items');
  }
  const seen = new Set();
  return raw.map((it, i) => {
    if (!it || typeof it !== 'object' || Array.isArray(it)) throw badRequest(`قلمِ ${i + 1} درست نیست`, 'bad_items');
    const productId = photos.checkId(it.product_id);
    if (seen.has(productId)) throw badRequest(`کالای «${productId}» دو بار آمده است`, 'duplicate_product');
    seen.add(productId);
    const out = { product_id: productId, source: str(it.source, 10) };
    if (it.image_id != null) out.image_id = str(it.image_id, 40);
    if (it.version != null) out.version = it.version;
    if (it.confirm === true) out.confirm = true;
    if (it.expect && typeof it.expect === 'object' && !Array.isArray(it.expect)) out.expect = it.expect;
    return out;
  });
}

const digestOf = (items) => crypto.createHash('sha256').update(JSON.stringify(items)).digest('hex');

function shape(ch, items = null) {
  const out = {
    id: ch.id, key: ch.client_key, note: ch.note, status: ch.status, item_count: Number(ch.item_count),
    created_by: ch.created_by, created_at: Number(ch.created_at),
    rolled_back_by: ch.rolled_back_by || '', rolled_back_at: ch.rolled_back_at == null ? null : Number(ch.rolled_back_at),
    rollback_reason: ch.rollback_reason || '',
  };
  if (items) {
    out.items = items.map((r) => ({ product_id: r.product_id, before: parse(r.before_row), after: parse(r.after_row) }));
  }
  return out;
}

async function itemsIn(c, id) {
  return (await c.query(
    'SELECT product_id, before_row, after_row FROM shop_image_change_items WHERE change_id=$1 ORDER BY seq', [id])).rows;
}

/**
 * چند انتخابِ عکس در یک تراکنش. ⇒ `{ change, replayed }` — `replayed` یعنی همین کلید پیش‌تر نشسته بود.
 * @param body `{ key, note?, items: [{ product_id, source, image_id?, version?, confirm?, expect? }] }`
 */
async function apply(shopId, userId, body = {}, { ip = '' } = {}) {
  const key = str(body.key, 80);
  if (!key) throw badRequest('کلیدِ کار (key) لازم است', 'key_required');
  const note = str(body.note, 200);
  const items = itemsOf(body);
  const digest = digestOf(items);
  return tx(async (c) => {
    const id = ulid();
    const at = now();
    const made = await q1(c,
      `INSERT INTO shop_image_changes (id, shop_id, client_key, request_digest, note, status, item_count, created_by, created_at)
       VALUES ($1,$2,$3,$4,$5,'applied',$6,$7,$8) ON CONFLICT (shop_id, client_key) DO NOTHING RETURNING id`,
      [id, shopId, key, digest, note, items.length, userId, at]);
    if (!made) {
      const had = await q1(c, 'SELECT * FROM shop_image_changes WHERE shop_id=$1 AND client_key=$2', [shopId, key]);
      if (!had || had.request_digest !== digest) {
        throw new ApiError(409, 'key_reused', 'این کلید پیش‌تر برای دستهٔ دیگری به کار رفته است');
      }
      return { change: shape(had, await itemsIn(c, had.id)), replayed: true };
    }
    for (let i = 0; i < items.length; i++) {
      const it = items[i];
      const before = pick(await q1(c, 'SELECT * FROM shop_product_images WHERE shop_id=$1 AND product_id=$2 FOR UPDATE',
        [shopId, it.product_id]));
      let out;
      try {
        out = await bank.selectIn(c, shopId, userId, it);
      } catch (e) {
        //  یک قلم ⇒ هیچ: تراکنش برمی‌گردد و خطا می‌گوید کدام قلم
        if (e instanceof ApiError) e.data = { ...(e.data || {}), index: i, product_id: it.product_id };
        throw e;
      }
      const after = pick(out);
      await c.query(
        `INSERT INTO shop_image_change_items (change_id, shop_id, seq, product_id, before_row, after_row)
         VALUES ($1,$2,$3,$4,$5,$6)`,
        [id, shopId, i, it.product_id, before ? JSON.stringify(before) : null, JSON.stringify(after)]);
      await audit.log({
        shopId, userId, action: 'image.selected', targetType: 'product', targetId: it.product_id, ip,
        detail: { change_id: id, source: after.source, image_id: after.image_id, version: after.version, match: after.match_status },
      }, { client: c });
    }
    await audit.log({
      shopId, userId, action: 'image.changes_applied', targetType: 'image_change', targetId: id, ip,
      detail: { items: items.length, products: items.map((x) => x.product_id), note },
    }, { client: c });
    const ch = await q1(c, 'SELECT * FROM shop_image_changes WHERE id=$1', [id]);
    return { change: shape(ch, await itemsIn(c, id)), replayed: false };
  });
}

/**
 * برگرداندنِ همهٔ کالاهای یک دسته به وضعِ پیش از آن — یا هیچ. ⇒ `{ change, already }`.
 * `actorType: 'admin'` (درِ پنلِ مرکزی، `routes/admin-image-changes.js`): `userId` شناسهٔ مدیر است ⇒ سابقه با نوعِ «مدیر»،
 * `rolled_back_by` و `updated_by` با پیشوندِ ‎admin:‎ (هرگز با شناسهٔ کاربرِ دکان یکی نمی‌شود) و شناسهٔ اجازهٔ پشتیبانی (`grant`).
 */
async function rollback(shopId, userId, id, body = {}, { ip = '', actorType = 'user', grant = '' } = {}) {
  const cid = str(id, 40);
  const reason = str(body && body.reason, 200);
  const by = actorType === 'admin' ? `admin:${userId}` : userId;
  const extra = grant ? { grant } : {};
  return tx(async (c) => {
    const ch = await q1(c, 'SELECT * FROM shop_image_changes WHERE id=$1 AND shop_id=$2 FOR UPDATE', [cid, shopId]);
    if (!ch) throw notFound('این دسته پیدا نشد');
    const rows = await itemsIn(c, cid);
    if (ch.status === 'rolled_back') return { change: shape(ch, rows), already: true };
    const changed = [];
    const plan = [];
    for (const r of rows) {
      const cur = pick(await q1(c, 'SELECT * FROM shop_product_images WHERE shop_id=$1 AND product_id=$2 FOR UPDATE',
        [shopId, r.product_id]));
      if (!same(cur, parse(r.after_row))) changed.push(r.product_id);
      plan.push({ productId: r.product_id, before: parse(r.before_row) });
    }
    if (changed.length) {
      const e = new ApiError(409, 'changed_since',
        'عکسِ بعضی کالاها پس از این دسته دوباره عوض شده است — اول همان تغییرِ تازه‌تر را برگردانید');
      e.data = { products: changed };
      throw e;
    }
    const at = now();
    for (const { productId, before } of plan) {
      if (!before) {
        await c.query('DELETE FROM shop_product_images WHERE shop_id=$1 AND product_id=$2', [shopId, productId]);
      } else {
        await c.query(
          `UPDATE shop_product_images SET source=$3, image_id=$4, version=$5, match_status=$6, confirmed_by=$7,
              central_image_id=$8, central_version=$9, central_match=$10, central_confirmed_by=$11, bank_product_id=$12,
              updated_by=$13, updated_at=$14
            WHERE shop_id=$1 AND product_id=$2`,
          [shopId, productId, ...COLS.map((k) => before[k]), by, at]);
      }
      await audit.log({
        shopId, actorType, userId, action: 'image.restored', targetType: 'product', targetId: productId, ip,
        detail: before
          ? { change_id: cid, source: before.source, image_id: before.image_id, version: before.version, ...extra }
          : { change_id: cid, source: 'previous_default', ...extra },
      }, { client: c });
    }
    await c.query(
      `UPDATE shop_image_changes SET status='rolled_back', rolled_back_by=$2, rolled_back_at=$3, rollback_reason=$4
        WHERE id=$1`, [cid, by, at, reason]);
    await audit.log({
      shopId, actorType, userId, action: 'image.changes_rolled_back', targetType: 'image_change', targetId: cid, ip,
      detail: { items: rows.length, reason, ...extra },
    }, { client: c });
    const done = await q1(c, 'SELECT * FROM shop_image_changes WHERE id=$1', [cid]);
    return { change: shape(done, rows), already: false };
  });
}

/** آخرین دسته‌های همین دکان (تازه‌ترین بالا) */
async function list(shopId, { limit = 20 } = {}) {
  const n = Math.min(LIST_MAX, Math.max(1, Math.floor(Number(limit)) || 20));
  const rows = await many(
    'SELECT * FROM shop_image_changes WHERE shop_id=$1 ORDER BY created_at DESC, id DESC LIMIT $2', [shopId, n]);
  return { changes: rows.map((r) => shape(r)) };
}

async function detail(shopId, id) {
  return tx(async (c) => {
    const ch = await q1(c, 'SELECT * FROM shop_image_changes WHERE id=$1 AND shop_id=$2', [str(id, 40), shopId]);
    if (!ch) throw notFound('این دسته پیدا نشد');
    return { change: shape(ch, await itemsIn(c, ch.id)) };
  });
}

module.exports = { MAX_ITEMS, COLS, apply, rollback, list, detail, pick, same };
