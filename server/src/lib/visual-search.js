'use strict';
/**
 * جست‌وجوی تصویریِ کالا بر پایهٔ شباهت — AI-11 (§۶.۲۲). محلی و قطعی: بی سرویسِ بیرونی، بی کتابخانه (D-16).
 *
 *   search(shopId, buf, { limit, store })  ⇒ { label, notice, threshold, indexed, items[] }
 *   indexedCount(shopId)                   ⇒ شمارِ کالاهای نمایه‌شده (درِ «روشن است؟» ِ برنامه)
 *   backfill(shopId, { limit })            ⇒ { hashed, removed, more, enabled } — نمایهٔ عکسِ کالاها، دسته‌ای و بی‌تکرار
 *   sweep({ budget })                      ⇒ کارِ دوره‌ای روی دکان‌های پرچم‌روشن (start/stop)
 *
 * روش: همان dHash ِ ۶۴ بیتیِ IMG-15 (‎lib/image-quality.js‎، `fingerprint`) و هیستوگرامِ ۸ خانه‌ایِ روشنایی
 * (رمزگشا فقط کانالِ Y دارد ⇒ رنگ نه). نتیجه فقط اگر همینگ ≤ HAMMING_MAX **و** L1 ِ هیستوگرام ≤ HIST_MAX؛
 * بیرون از آستانه هیچ. ترتیب: همینگ، سپس L1، سپس شناسهٔ کالا.
 *
 * ⛔ پرچمِ خاموشِ `shop.visual_search` (دکان یا `all`): خاموش ⇒ نه نمایه، نه مسیر (همان پاسخِ امروز).
 *    ویترین جدا: `shop.storefront.visual_search` و همان پرچمِ نمایه.
 * ⛔ عکسِ پرسش ذخیره نمی‌شود: سنجش و هش فقط در حافظه و رها؛ هیچ ردیف، فایل یا سابقه‌ای از بایت‌ها یا هشش.
 * ⛔ سنجشِ بایت‌ها همان درِ بارگذاری (`image-originals.checkOriginal`: نوع از محتوا، ابعاد، سقفِ حجمِ عکسِ کالا).
 * ⛔ نتیجه فقط از کالاهای همان دکان؛ برچسب همیشه «مشابه»، هرگز ادعای یکی بودن؛ هیچ انتخاب یا افزودن به
 *    سبدِ خودکار — فقط فهرست، تا کاربر خودش انتخاب کند.
 * ⛔ آستانه‌ها روی مجموعهٔ مصنوعیِ ‎test/visual-search.test.js‎ آزموده شده‌اند، نه روی عکسِ واقعیِ دکان‌ها.
 */
const { one, many, query, now } = require('../db');
const bytes = require('./image-bytes');
const quality = require('./image-quality');
const { checkOriginal } = require('./image-originals');
const photos = require('./product-photos');
const imgStore = require('./image-store');
const { flagOf } = require('./orders');
const { ApiError } = require('../middleware/errors');

const FLAG = 'shop.visual_search';
const STORE_FLAG = 'shop.storefront.visual_search';
const LABEL = 'مشابه';
const NOTICE = 'این فهرست فقط کالاهای مشابه از روی عکس است؛ پیش از هر کاری خودتان کالا را ببینید و انتخاب کنید.';
const LIMITS = Object.freeze({
  HAMMING_MAX: 10,     // از ۶۴ بیت؛ بیشتر ⇒ «مشابه» نیست
  HIST_MAX: 100,       // L1 ِ دو هیستوگرامِ درصدی (۰ تا ۲۰۰، نیمی ناهمپوشان)؛ بیشتر ⇒ «مشابه» نیست
  RESULTS_MAX: 20, RESULTS_DEFAULT: 8,
  SCAN_MAX: photos.MAX_PHOTOS,   // همهٔ کالاهای عکس‌دارِ یک دکان (سقفِ ۲۰٬۰۰۰ ِ ۰۴۵)
  BATCH_DEFAULT: 25, BATCH_MAX: 200,
});
const HEX16 = /^[0-9a-f]{16}$/;

const flagOn = (shopId) => flagOf(shopId, FLAG);
const storeFlagOn = async (shopId) => (await flagOf(shopId, STORE_FLAG)) && flagOn(shopId);
const clamp = (v, def, max) => Math.min(max, Math.max(1, Math.floor(Number(v)) || def));

function histL1(a, b) {
  if (!HEX16.test(a) || !HEX16.test(b)) return 200;
  let d = 0;
  for (let i = 0; i < 16; i += 2) d += Math.abs(parseInt(a.slice(i, i + 2), 16) - parseInt(b.slice(i, i + 2), 16));
  return d;
}

/** عکسِ پرسش ⇒ { dhash, hist }؛ همان سنجشِ درِ بارگذاری، و فقط در حافظه. */
function probe(buf) {
  checkOriginal(buf, photos.MAX_BYTES);
  const fp = quality.fingerprint(buf, bytes.inspect(buf));
  if (!fp) {
    throw new ApiError(422, 'image_unmeasurable',
      'این عکس سنجیدنی نیست (یک‌رنگ، WebP یا JPEG ِ progressive)؛ عکسِ JPEG یا PNG ِ دیگری بگیرید');
  }
  return fp;
}

/** نامزدهای درونِ آستانه به ترتیبِ فاصله — خالص (آزمونِ رتبه‌بندی بی دیتابیس). */
function rank(q, rows) {
  const out = [];
  for (const r of rows) {
    if (!HEX16.test(r.dhash)) continue;
    const distance = quality.hamming(q.dhash, r.dhash);
    const hist = histL1(q.hist, r.hist);
    if (distance > LIMITS.HAMMING_MAX || hist > LIMITS.HIST_MAX) continue;
    out.push({ product_id: r.product_id, distance, hist });
  }
  return out.sort((a, b) => a.distance - b.distance || a.hist - b.hist || (a.product_id < b.product_id ? -1 : 1));
}

const itemOf = (c, extra) => ({
  ...extra, product_id: c.product_id, distance: c.distance, similarity: Math.round(100 * (1 - c.distance / 64)), label: LABEL,
});

/** شمارِ کالاهای نمایه‌شدهٔ یک دکان — همان ردیف‌هایی که `search` می‌سنجد */
async function indexedCount(shopId) {
  const r = await one(`SELECT COUNT(*)::int AS n FROM product_visual_hashes WHERE shop_id=$1 AND dhash <> '-'`, [shopId]);
  return Number(r ? r.n : 0);
}

/**
 * `store` = تابعِ ویترین (`ids ⇒ کالاهای منتشرشده`)؛ نبود ⇒ کارمندِ دکان (نامِ کالا از دفترِ همان دکان، حذف‌شده نه).
 */
async function search(shopId, buf, { limit, store = null } = {}) {
  const q = probe(buf);   //  از این‌جا فقط { dhash, hist } ِ پرسش؛ بایت‌ها هیچ جا نمی‌روند
  const n = clamp(limit, LIMITS.RESULTS_DEFAULT, LIMITS.RESULTS_MAX);
  const rows = await many(
    `SELECT product_id, dhash, hist FROM product_visual_hashes WHERE shop_id=$1 AND dhash <> '-' LIMIT ${LIMITS.SCAN_MAX}`,
    [shopId]);
  const hits = rank(q, rows).slice(0, n * 3);
  let items = [];
  if (hits.length && store) {
    const live = new Map((await store(hits.map((c) => c.product_id))).map((p) => [p.id, p]));
    items = hits.filter((c) => live.has(c.product_id)).map((c) => itemOf(c, { product: live.get(c.product_id) }));
  } else if (hits.length) {
    const names = new Map((await many(
      `SELECT row_id, data->>'name' AS name FROM sync_rows
        WHERE app='shop' AND account_kind='shop' AND table_name='products' AND account_id=$1
          AND row_id = ANY($2::text[]) AND deleted_at IS NULL`,
      [shopId, hits.map((c) => c.product_id)])).map((r) => [r.row_id, String(r.name || '').slice(0, 200)]));
    items = hits.filter((c) => names.has(c.product_id)).map((c) => itemOf(c, { name: names.get(c.product_id) }));
  }
  return {
    label: LABEL, notice: NOTICE,
    threshold: { hamming_max: LIMITS.HAMMING_MAX, hist_max: LIMITS.HIST_MAX },
    indexed: rows.length,
    items: items.slice(0, n),
  };
}

// ════════════════════════════════════════════════════════════════════
//  نمایه: عکسِ کنونیِ هر کالا (انتخابِ بانک با پرچمِ بانک، وگرنه عکسِ همگام‌شدهٔ ۰۴۵)
// ════════════════════════════════════════════════════════════════════

/** همان قاعدهٔ `image-bank.resolve` و ویترین: ردیفِ انتخابِ بانک (پرچمِ بانک روشن) بر عکسِ همگام‌شده جلو می‌افتد. */
async function currentSql(shopId) {
  const bankOn = await require('./image-bank').shopEnabled(shopId);
  const legacy = `SELECT p.product_id, 'p' || p.rev AS src FROM product_photos p
     WHERE p.app='shop' AND p.account_id=$1 AND ${imgStore.has('p.')}`;
  if (!bankOn) return legacy;
  return `${legacy} AND NOT EXISTS (SELECT 1 FROM shop_product_images s WHERE s.shop_id=$1 AND s.product_id=p.product_id)
    UNION ALL
    SELECT s.product_id, 'b' || v.blob_hash AS src FROM shop_product_images s
      JOIN image_versions v ON v.image_id=s.image_id AND v.version=s.version
     WHERE s.shop_id=$1 AND s.source <> 'none' AND s.image_id <> ''`;
}

async function bytesOf(shopId, productId, src) {
  if (src[0] === 'p') {
    const r = await one(`SELECT data, store_key FROM product_photos WHERE app='shop' AND account_id=$1 AND product_id=$2 AND ${photos.HAS}`,
      [shopId, productId]);
    return imgStore.bytes(imgStore.shopTenant(shopId), r);
  }
  return imgStore.bytes(imgStore.BLOBS, await one('SELECT data, store_key FROM image_blobs WHERE hash=$1', [src.slice(1)]));
}

function hashOf(buf) {
  try { return quality.fingerprint(buf, bytes.inspect(buf)); } catch { return null; }
}

/**
 * یک دسته از کالاهای بی هش یا با منبعِ تازه (حداکثر `limit`)؛ دوباره اجرا ⇒ فقط باقی‌مانده‌ها (بی‌تکرار).
 * منبعِ رفته (عکس حذف شد، انتخابِ «بی عکس») ⇒ ردیفِ نمایه پاک. پرچمِ خاموش ⇒ هیچ.
 */
async function backfill(shopId, { limit } = {}) {
  if (!(await flagOn(shopId))) return { hashed: 0, removed: 0, more: false, enabled: false };
  const n = clamp(limit, LIMITS.BATCH_DEFAULT, LIMITS.BATCH_MAX);
  const cur = await currentSql(shopId);
  const removed = (await query(
    `DELETE FROM product_visual_hashes h WHERE h.shop_id=$1
       AND NOT EXISTS (SELECT 1 FROM (${cur}) c WHERE c.product_id=h.product_id)`, [shopId])).rowCount || 0;
  const todo = await many(
    `SELECT c.product_id, c.src FROM (${cur}) c
       LEFT JOIN product_visual_hashes h ON h.shop_id=$1 AND h.product_id=c.product_id
      WHERE h.product_id IS NULL OR h.src <> c.src
      ORDER BY c.product_id LIMIT ${n + 1}`, [shopId]);
  let hashed = 0;
  for (const t of todo.slice(0, n)) {
    const buf = await bytesOf(shopId, t.product_id, t.src);
    const fp = buf && hashOf(buf);
    await query(
      `INSERT INTO product_visual_hashes (shop_id, product_id, src, dhash, hist, updated_at) VALUES ($1,$2,$3,$4,$5,$6)
       ON CONFLICT (shop_id, product_id) DO UPDATE SET src=EXCLUDED.src, dhash=EXCLUDED.dhash, hist=EXCLUDED.hist,
         updated_at=EXCLUDED.updated_at`,
      [shopId, t.product_id, t.src, fp ? fp.dhash : '-', fp ? fp.hist : '', now()]);
    hashed++;
  }
  return { hashed, removed, more: todo.length > n, enabled: true };
}

// ════════════════════════════════════════════════════════════════════
//  کارِ دوره‌ای — دکان‌های پرچم‌روشن، با سقفِ عکس در هر نوبت
// ════════════════════════════════════════════════════════════════════

const TICK_MS = 60 * 1000;
const SHOPS_PER_TICK = 20;
let cursor = '';

/** دکان‌های نامزد: `all` روشن ⇒ همهٔ دکان‌ها (چرخشی)، وگرنه فقط scope ِ دکان‌های روشن؛ `flagOf` هر کدام را می‌سنجد. */
async function candidates() {
  const all = await one(`SELECT value FROM live_config WHERE key=$1 AND scope='all'`, [FLAG]);
  const page = all && String(all.value).trim() === 'true'
    ? (from) => many('SELECT id FROM shops WHERE id > $1 ORDER BY id LIMIT $2', [from, SHOPS_PER_TICK])
    : (from) => many(`SELECT scope AS id FROM live_config WHERE key=$3 AND scope <> 'all' AND scope > $1 ORDER BY scope LIMIT $2`,
      [from, SHOPS_PER_TICK, FLAG]);
  let rows = await page(cursor);
  if (!rows.length && cursor) rows = await page('');
  cursor = rows.length ? rows[rows.length - 1].id : '';
  return rows.map((r) => r.id);
}

async function sweep({ budget = LIMITS.BATCH_DEFAULT * 2 } = {}) {
  let left = budget, hashed = 0;
  for (const shopId of await candidates()) {
    if (left <= 0) break;
    const r = await backfill(shopId, { limit: left });
    hashed += r.hashed; left -= r.hashed;
  }
  return { hashed };
}

let timer = null;
let sweeping = false;
async function tick() {
  if (sweeping) return { hashed: 0 };
  sweeping = true;
  try { return await sweep(); } finally { sweeping = false; }
}
function start() {
  if (timer) return;
  timer = setInterval(() => tick().catch((err) => console.error('[visual-search]', err.message)), TICK_MS);
  if (timer.unref) timer.unref();
}
function stop() {
  if (timer) clearInterval(timer);
  timer = null;
}

module.exports = {
  FLAG, STORE_FLAG, LABEL, NOTICE, LIMITS, flagOn, storeFlagOn, histL1, probe, rank, search, indexedCount, backfill, sweep, tick, start, stop,
};
