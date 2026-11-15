'use strict';
/**
 * S09 — «یک فاکتور در دو دستگاه» به صندوقِ تعارضِ صاحب/مدیر (`/api/shop/conflict-inbox`، گونهٔ `held_twice`).
 *
 * دو دستگاهِ بی‌اینترنت یک نگه‌داشته را باز کردند و هر دو فروختند؛ پس از وصل، `DELETE` ِ در صفِ دومی ۴۰۹ `held_conflict`
 * می‌گیرد (`shop-held-sync.remove`). با پرچمِ خاموشِ `shop.held_twice_cancel` (پیش‌فرض خاموش) همان دستگاهِ دوم — وقتی فروشِ
 * نخست را در دفترش می‌بیند و هر دو **قلم‌به‌قلم و مبلغ‌به‌مبلغ یکی‌اند** — فروشِ **خودش** را از راهِ لغوِ عادیِ برنامه
 * (`SalesVoid.run` ⇐ op ِ لغوِ v1، و سندِ معکوسِ زنجیرهٔ SEC-02) لغو می‌کند و این‌جا گزارش می‌دهد:
 *
 *   POST /api/shop/held-invoices/:id/twice  (`routes/shop-held-invoices.js`)
 *     { sale_id, kept_id?, kept_series?, cancelled: bool, number?, total_minor, created_at?,
 *       items: [{ productId, name?, qtyMilli, unitPriceMinor, totalMinor }] (≤ ۲۰۰), device_id? }
 *     ⇒ ۲۰۱ { ok, id, created } — یک ردیفِ باز برای هر فروش (`dedupe_key = held_twice:<sale_id>`)؛ تکرار همان ردیف.
 *
 * پس پول هرگز بی‌صدا نمی‌رود: صاحب ردیف را در صندوق می‌بیند — «فروشِ دوم خودکار لغو شد» (`cancelled`) یا، اگر کاربرِ
 * دستگاه اجازهٔ لغو نداشت، «دو فروش؛ یکی را لغو کنید» — و یا «کنار بگذار» (دید و پذیرفت) یا «اصلاح و ثبت» (همان قلم‌ها در
 * صندوق دوباره فروخته شوند، اگر لغو بجا نبود). «دوباره ثبت با تأییدِ من» ندارد (`NO_APPROVAL`): بسته فقط برای نمایش است.
 *
 * ⛔ سرور با این گزارش هیچ پولی نمی‌سازد و هیچ ردیفِ دفتری را عوض نمی‌کند — خودِ لغو op ِ عادیِ همگام‌سازی است و همهٔ
 *    نگهبان‌های امروز (نقش، `sales.cancel`) همان‌جا. ⛔ shop_id فقط از عضویتِ توکن؛ پرچم پیش از بدنه (۴۰۴ `held_twice_off`).
 */
const { one, query, newId, now } = require('../db');
const { flagOf } = require('./orders');
const { SHOP } = require('./tenancy');
const { badRequest, notFound } = require('../middleware/errors');

const FLAG = 'shop.held_twice_cancel';
const KIND = 'held_twice';
const ID_RE = /^[0-9A-Za-z_-]{1,64}$/;
const SERIES_RE = /^[A-Z][1-9][0-9]{0,3}$/;
const MAX_ITEMS = 200;
const MAX_NAME = 120;
const MAX_MINOR = 9007199254740991;

const flagOn = (shopId) => flagOf(shopId, FLAG);

async function requireOn(shopId) {
  if (!(await flagOn(shopId))) throw notFound('لغوِ خودکارِ فروشِ دوم برای این دکان روشن نیست', 'held_twice_off');
}

const bad = (m) => badRequest(m, 'bad_held_twice');
const int = (v, min, max) => (Number.isSafeInteger(v) && v >= min && v <= max ? v : null);
const idOr = (v, required, what) => {
  if ((v === undefined || v === null || v === '') && !required) return '';
  if (typeof v !== 'string' || !ID_RE.test(v)) throw bad(`${what} درست نیست`);
  return v;
};

/** بدنه ⇒ گزارشِ پاک؛ بدشکل ⇒ ۴۰۰ */
function parse(rawId, body) {
  const b = body && typeof body === 'object' && !Array.isArray(body) ? body : {};
  const held = idOr(rawId, true, 'شناسهٔ نگه‌داشته');
  const sale = idOr(b.sale_id, true, 'شناسهٔ فروش');
  const kept = idOr(b.kept_id, false, 'شناسهٔ فروشِ نخست');
  if (kept && kept === sale) throw bad('فروشِ نخست و دوم یکی‌اند');
  const keptSeries = typeof b.kept_series === 'string' && SERIES_RE.test(b.kept_series) ? b.kept_series : '';
  if (typeof b.cancelled !== 'boolean') throw bad('cancelled لازم است');
  const total = int(b.total_minor, 0, MAX_MINOR);
  if (total === null) throw bad('مبلغ درست نیست');
  const number = b.number === undefined || b.number === null ? null : int(b.number, 1, 1e9);
  if (b.number !== undefined && b.number !== null && number === null) throw bad('شمارهٔ فاکتور درست نیست');
  const createdAt = b.created_at === undefined ? null : int(b.created_at, 0, MAX_MINOR);
  if (!Array.isArray(b.items) || !b.items.length || b.items.length > MAX_ITEMS) throw bad('قلم‌ها درست نیستند');
  const items = b.items.map((x) => {
    const o = x && typeof x === 'object' && !Array.isArray(x) ? x : {};
    const productId = typeof o.productId === 'string' && o.productId.length <= 80 ? o.productId : null;
    const qty = int(o.qtyMilli, -MAX_MINOR, MAX_MINOR);
    const unit = int(o.unitPriceMinor, -MAX_MINOR, MAX_MINOR);
    const line = int(o.totalMinor, -MAX_MINOR, MAX_MINOR);
    if (productId === null || qty === null || unit === null || line === null) throw bad('قلم درست نیست');
    const name = typeof o.name === 'string' ? o.name.slice(0, MAX_NAME) : '';
    return { productId, name, qty, unit, line };
  });
  const device = typeof b.device_id === 'string' ? b.device_id.slice(0, 64) : '';
  return { held, sale, kept, keptSeries, cancelled: b.cancelled, total, number, createdAt, items, device };
}

/** بستهٔ نمایشِ صندوق (همان شکلِ v1 ِ `conflict-inbox.invoiceOf`؛ مبلغ به افغانی، مقدار به واحد) */
function payloadOf(r) {
  const ops = [{
    table: 'sales', row_id: r.sale, type: 'insert',
    fields: {
      invoiceNumber: r.number, createdAt: r.createdAt, status: r.cancelled ? 'cancelled' : 'completed',
      total: r.total / 100, finalTotal: r.total / 100,
    },
  }];
  r.items.forEach((it, i) => ops.push({
    table: 'saleItems', row_id: `${r.sale}-i${i + 1}`, type: 'insert',
    fields: { saleId: r.sale, productId: it.productId, name: it.name, quantity: it.qty / 1000, unitPrice: it.unit / 100, totalPrice: it.line / 100 },
  }));
  return { v: 1, path: 'v1', ops };
}

/** یک ردیفِ باز برای هر فروش؛ تکرار (پاسخِ گم‌شده، دورِ بعد) ⇒ همان ردیف با آخرین حال */
async function report(shopId, actor, r) {
  const shop = String(shopId);
  const dedupe = `${KIND}:${r.sale}`;
  const detail = {
    //  `held_twice` = فروشِ دوم خودکار لغو شد؛ `held_twice_open` = یکی‌اند ولی کاربرِ دستگاه اجازهٔ لغو نداشت (صاحب لغو کند)
    reason: r.cancelled ? KIND : `${KIND}_open`, held: r.held, kept: r.kept, kept_series: r.keptSeries, cancelled: r.cancelled,
    requester: String(actor.userId || ''), role: String(actor.role || ''),
  };
  const payload = JSON.stringify(payloadOf(r));
  const open = await one(
    "SELECT id FROM sync_v3_conflicts WHERE app=$1 AND account_kind=$2 AND account_id=$3 AND dedupe_key=$4 AND status='open' LIMIT 1",
    [SHOP.app, SHOP.app, shop, dedupe],
  );
  if (open) {
    await query('UPDATE sync_v3_conflicts SET detail=$2::jsonb, loser_value=$3::jsonb WHERE id=$1', [open.id, JSON.stringify(detail), payload]);
    return { id: open.id, created: false };
  }
  const id = newId('cf');
  await query(
    `INSERT INTO sync_v3_conflicts (id, app, account_kind, account_id, kind, table_name, row_id, field,
                                    loser_device, detail, dedupe_key, at, loser_value)
     VALUES ($1,$10,$11,$2,$3,'sales',$4,'',$5,$6::jsonb,$7,$8,$9::jsonb)`,
    [id, shop, KIND, r.sale, r.device, JSON.stringify(detail), dedupe, now(), payload, SHOP.app, SHOP.app],
  );
  return { id, created: true };
}

module.exports = { FLAG, KIND, MAX_ITEMS, flagOn, requireOn, parse, payloadOf, report };
