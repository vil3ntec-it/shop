'use strict';
/**
 * CRM-15/SEC-10 (د۱۸) — «فرمانِ سرور ⇐ دستگاه» برای ناشناس کردنِ پرونده‌های قرض‌دار در دفترِ دستگاه پس از حذفِ دادهٔ مشتری.
 *
 * حذفِ سرور (`customer-erasure.erase`) دفترِ همگام‌سازیِ دستگاه را دست نمی‌زد («سازوکارِ فرمانِ حذف به دستگاه نیست»): نام،
 * شماره، نشانی و یادداشتِ پرونده روی هر دستگاه می‌ماند، و ردیف‌های `customer_view` ِ سابقهٔ دستگاه (که فقط شناسهٔ پرونده
 * دارند و با زنجیرهٔ STF-09 مُهر خورده‌اند — ویرایش‌نشدنی) هنوز به پروندهٔ نام‌دار اشاره می‌کردند.
 *
 * راه (پرچمِ `shop.customer_erasure_ledger`، `live_config`، پیش‌فرض خاموش ⇒ هیچ ⇒ همان امروز):
 *   ۱) [queueIn] درونِ تراکنشِ حذف: هر پروندهٔ قرض‌دارِ همان شماره در دفترِ سرور (راهِ قدیم، v1، v3) ⇐ ردیفِ
 *      `customer_erasure_ledger` (مهاجرتِ ۸۹۵؛ حذفِ دوباره همان پرونده را دوباره باز می‌کند).
 *   ۲) `GET /api/shop/customer-erasure/ledger` [pending]: برنامهٔ صاحب/مدیر (`data.delete.any`) پس از هر دورِ همگام‌سازی
 *      فهرست را می‌گیرد و نام/شماره/نشانی/یادداشتِ همان پرونده‌ها را در دفترِ خودش پاک می‌کند (`CustomerErasureLedger`) —
 *      op ِ عادی ⇒ دفترِ سرور و همهٔ دستگاه‌ها؛ سندهای مالی و `customer_view` (فقط شناسه) دست نمی‌خورند و حالا به پرونده‌ای
 *      بی‌نام اشاره می‌کنند.
 *   ۳) `POST /api/shop/customer-erasure/ledger/applied` [applied] { debtorIds }: سرور فقط پرونده‌ای را «انجام شد» می‌نشاند که
 *      در دفترِ سرور (v1 و v3) دیگر شماره ندارد (پاک کردنِ دستگاه هنوز نرسیده ⇒ می‌ماند، بی خطا).
 * ⛔ دکان فقط از نشست؛ فقط شناسه، نه شماره یا نام؛ سقفِ نرخِ دکان.
 */
const { many, query, now } = require('../db');
const { flagOf } = require('./orders');

const FLAG = 'shop.customer_erasure_ledger';
const MAX = 200;
const ID_RE = /^[A-Za-z0-9_.:-]{1,80}$/;

/** درونِ تراکنشِ حذف: پرونده‌های همین شماره ⇐ فرمان؛ پرچمِ خاموش ⇒ ۰ */
async function queueIn(c, shopId, forms, erasureId) {
  if (!forms || !forms.length || !(await flagOf(shopId, FLAG))) return 0;
  const ids = [...new Set((await require('./customer-access')._debtorIdsByPhone(c, shopId, forms)).map((d) => d.row_id))]
    .filter((id) => typeof id === 'string' && ID_RE.test(id));
  for (const id of ids) {
    await c.query(
      `INSERT INTO customer_erasure_ledger (shop_id, debtor_id, erasure_id, created_at) VALUES ($1,$2,$3,$4)
       ON CONFLICT (shop_id, debtor_id) DO UPDATE SET erasure_id = excluded.erasure_id, created_at = excluded.created_at, applied_at = NULL`,
      [shopId, id, erasureId, now()]
    );
  }
  return ids.length;
}

/** فرمان‌های انجام‌نشدهٔ همین دکان (کهنه‌ترین اول) */
async function pending(shopId) {
  const rows = await many(
    `SELECT debtor_id, erasure_id, created_at FROM customer_erasure_ledger WHERE shop_id=$1 AND applied_at IS NULL
      ORDER BY created_at, debtor_id LIMIT $2`, [shopId, MAX]
  );
  return rows.map((r) => ({ debtorId: r.debtor_id, erasureId: r.erasure_id, at: Number(r.created_at) }));
}

/** پرونده‌ای که دفترِ سرور (v1 یا v3) هنوز شماره/نام برایش دارد */
async function stillPersonal(shopId, ids) {
  const where = `row_id = ANY($2::text[]) AND deleted_at IS NULL AND (COALESCE(data->>'phone','') <> '' OR COALESCE(data->>'phone2','') <> ''
    OR COALESCE(data->>'notes','') <> '' OR COALESCE(data->>'deliveryAddress','') <> '' OR COALESCE(data->>'deliveryPin','') <> '')`;
  const rows = await many(
    `SELECT row_id FROM sync_rows WHERE app='shop' AND account_kind='shop' AND account_id=$1 AND table_name='debtors' AND ${where}
     UNION SELECT row_id FROM sync_v3_rows WHERE app='shop' AND account_kind='shop' AND account_id=$1 AND table_name='debtors' AND ${where}`,
    [shopId, ids]
  );
  return new Set(rows.map((r) => r.row_id));
}

/** `{ debtorIds }` ⇒ `{ applied, pending }`؛ شناسهٔ بدشکل ⇒ null (۴۰۰) */
async function applied(shopId, body) {
  const ids = Array.isArray(body?.debtorIds) ? body.debtorIds : null;
  if (!ids || !ids.length || ids.length > MAX || !ids.every((x) => typeof x === 'string' && ID_RE.test(x))) return null;
  const uniq = [...new Set(ids)];
  const open = new Set((await many(
    'SELECT debtor_id FROM customer_erasure_ledger WHERE shop_id=$1 AND debtor_id = ANY($2::text[]) AND applied_at IS NULL', [shopId, uniq]
  )).map((r) => r.debtor_id));
  const asked = uniq.filter((id) => open.has(id));
  const dirty = asked.length ? await stillPersonal(shopId, asked) : new Set();
  const done = asked.filter((id) => !dirty.has(id));
  if (done.length) {
    await query('UPDATE customer_erasure_ledger SET applied_at=$3 WHERE shop_id=$1 AND debtor_id = ANY($2::text[]) AND applied_at IS NULL',
      [shopId, done, now()]);
  }
  return { applied: done, pending: asked.filter((id) => dirty.has(id)) };
}

module.exports = { FLAG, MAX, queueIn, pending, applied, stillPersonal };
