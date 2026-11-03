'use strict';
/**
 * S109 (§۲۰.۱۱، §۲۰.۱۲، §۲۰.۴، و گزارشِ مدیرِ §۲۲ مرحلهٔ D بندِ ۵) — شمارشِ منطقه‌ای ِ فروشگاه‌ها برای پنلِ مرکزی.
 *
 * ⛔ فقط فروشگاهِ **عمومی** شمرده می‌شود: همان دکانی که مشتری همین حالا در بازارچه می‌بیند (`market.eligible()`:
 *    ویترینِ روشن، «در فهرست» ِ صاحبِ دکان، دکانِ فعال، دروازه‌های پرچم و حالت و جریمه، و کالای منتشرشده). بیرون:
 *      • دکانِ تعلیق‌شده (دکانِ غیرفعال، حالتِ مدیریتیِ باز، جریمهٔ «تعلیقِ ویترین») ⇒ `excluded.suspended`
 *      • دکانِ بی وضعیتِ عمومی (ویترینِ خاموش، بیرون از فهرست، بی کالا، دروازهٔ بسته) ⇒ `excluded.notPublic`
 *      • دکانی که مدیر «آزمایشی» یا «تکراری» نشان زده (`shop_census_marks`) ⇒ `excluded.test` / `excluded.duplicate`
 * ⛔ سه وضعیتِ جدا، هیچ‌کدام «آنلاینِ همین لحظه» نیست (§۲۰.۱۱ بندِ ۳): `registered` (عمومی، شمرده‌شده)،
 *    `accepting` (همین حالا سفارش می‌پذیرد، فوری یا با تأخیر)، `temporarilyClosed` (بسته به اعلامِ صاحب یا ساعتِ کاری،
 *    یا «بی‌اینترنت» ِ اعلام‌شده). دستگاهِ کهنه «سفارش‌پذیر با تأخیر» است، نه «آنلاین».
 * ⛔ هیچ دادهٔ شخصی (نام، شماره، نشانیِ دقیق) در پاسخ نیست؛ فقط شمار. ولایت از «نمایش در بازارچه» ِ خودِ دکان
 *    (`market_listings.province`)؛ دکانِ بی ولایت جدا (`unknownProvince`)، نه حدس از مختصات.
 */
const { one, many, now, tx } = require('../db');
const market = require('./market');
const region = require('./af-provinces');
const audit = require('./audit');
const { badRequest, notFound } = require('../middleware/errors');

const MARKET_SERVICES = Object.freeze(['market.directory', 'shop.storefront']);
const MARK_KINDS = Object.freeze(['test', 'duplicate']);

const zero = () => ({ registered: 0, accepting: 0, temporarilyClosed: 0 });
function add(b, status) {
  b.registered++;
  if (status && status.orderAcceptance === 'later') b.temporarilyClosed++;
  else b.accepting++;
}

/** همهٔ دکان‌های ویترین‌دار یا فهرست‌دار، با نشانه‌های تعلیق، ولایت و نشانِ مدیر */
function candidates() {
  return many(
    `WITH ids AS (SELECT shop_id FROM storefronts UNION SELECT shop_id FROM market_listings)
     SELECT ids.shop_id, s.status AS shop_status, COALESCE(ml.province, '') AS province, COALESCE(ml.district, '') AS district,
            cm.kind AS mark,
            EXISTS (SELECT 1 FROM trust_sanctions ts WHERE ts.shop_id = ids.shop_id AND ts.kind = 'suspend_storefront'
                      AND ts.lifted_at IS NULL) AS sanctioned,
            EXISTS (SELECT 1 FROM shop_holds h WHERE h.shop_id = ids.shop_id AND h.lifted_at IS NULL
                      AND (h.kind <> 'service_disabled' OR h.service = ANY($1::text[]))) AS held
       FROM ids JOIN shops s ON s.id = ids.shop_id
       LEFT JOIN market_listings ml ON ml.shop_id = ids.shop_id
       LEFT JOIN shop_census_marks cm ON cm.shop_id = ids.shop_id`,
    [MARKET_SERVICES]
  );
}

/** شمارشِ منطقه‌ای ⇒ `{ provinces: [۳۴ ولایت، هر کدام با ولسوالی‌ها], unknownProvince, totals, excluded, … }` */
async function regions() {
  const at = now();
  const [rows, open] = await Promise.all([candidates(), market.eligible(at)]);
  const pub = new Map(open.map((e) => [e.row.shop_id, e.status]));
  const excluded = { test: 0, duplicate: 0, suspended: 0, notPublic: 0 };
  const byCode = new Map(region.LIST.map((p) => [p.code, { code: p.code, name: p.name, ...zero(), districts: new Map() }]));
  const unknownProvince = zero();
  const totals = zero();
  for (const r of rows) {
    if (!pub.has(r.shop_id)) {
      if (r.shop_status !== 'active' || r.sanctioned || r.held) excluded.suspended++;
      else excluded.notPublic++;
      continue;
    }
    if (r.mark === 'test' || r.mark === 'duplicate') { excluded[r.mark]++; continue; }
    const status = pub.get(r.shop_id);
    add(totals, status);
    const p = byCode.get(r.province);
    if (!p) { add(unknownProvince, status); continue; }
    add(p, status);
    const dk = region.districtKey(r.district);
    if (!p.districts.has(dk)) p.districts.set(dk, { name: region.cleanDistrict(r.district), ...zero() });
    add(p.districts.get(dk), status);
  }
  const provinces = [...byCode.values()].map((p) => ({
    ...p,
    districts: [...p.districts.values()].sort((a, b) => b.registered - a.registered || a.name.localeCompare(b.name, 'fa')),
  }));
  return {
    generatedAt: at,
    provinces,
    unknownProvince,
    totals,
    excluded,
    statuses: ['registered', 'accepting', 'temporarilyClosed'],
    onlineNow: null,   //  §۲۰.۱۱: بی تعریفِ heartbeat ِ تازه «آنلاینِ همین لحظه» گفته نمی‌شود
  };
}

/* ─────────────────── نشانِ «آزمایشی/تکراری» ِ مدیر ─────────────────── */

const shape = (r) => (r ? { kind: r.kind, duplicateOf: r.duplicate_of || null, reason: r.reason, setBy: r.set_by, setAt: Number(r.set_at) } : null);

async function markOf(shopId) {
  if (!(await one('SELECT 1 FROM shops WHERE id = $1', [shopId]))) throw notFound('دکان پیدا نشد');
  return { shopId, mark: shape(await one('SELECT * FROM shop_census_marks WHERE shop_id = $1', [shopId])), kinds: MARK_KINDS };
}

/**
 * گذاشتن یا برداشتنِ نشان (`kind: 'test'|'duplicate'|'none'`)، با دلیل و سابقه در همان تراکنش. «تکراری» دکانِ
 * اصل را می‌خواهد (موجود و غیرِ خودش). فقط شمارشِ منطقه‌ای را عوض می‌کند؛ ویترین و فروشِ دکان دست نمی‌خورد.
 */
async function setMark(shopId, body, req, { reason, ip = '' }) {
  const b = body && typeof body === 'object' ? body : {};
  const kind = typeof b.kind === 'string' ? b.kind : '';
  if (kind !== 'none' && !MARK_KINDS.includes(kind)) throw badRequest('نوعِ نشان معتبر نیست', 'bad_kind');
  const duplicateOf = kind === 'duplicate' && typeof b.duplicateOf === 'string' ? b.duplicateOf.trim() : '';
  if (kind === 'duplicate') {
    if (!duplicateOf || duplicateOf.length > 80 || duplicateOf === shopId) throw badRequest('دکانِ اصل را بنویسید', 'bad_duplicate_of');
    if (!(await one('SELECT 1 FROM shops WHERE id = $1', [duplicateOf]))) throw badRequest('دکانِ اصل پیدا نشد', 'bad_duplicate_of');
  }
  if (!(await one('SELECT 1 FROM shops WHERE id = $1', [shopId]))) throw notFound('دکان پیدا نشد');
  const t = now();
  return tx(async (c) => {
    const before = shape((await c.query('SELECT * FROM shop_census_marks WHERE shop_id = $1 FOR UPDATE', [shopId])).rows[0]);
    let mark = null;
    if (kind === 'none') {
      await c.query('DELETE FROM shop_census_marks WHERE shop_id = $1', [shopId]);
    } else {
      mark = shape((await c.query(
        `INSERT INTO shop_census_marks (shop_id, kind, duplicate_of, reason, set_by, set_at) VALUES ($1,$2,$3,$4,$5,$6)
         ON CONFLICT (shop_id) DO UPDATE SET kind = EXCLUDED.kind, duplicate_of = EXCLUDED.duplicate_of,
           reason = EXCLUDED.reason, set_by = EXCLUDED.set_by, set_at = EXCLUDED.set_at
         RETURNING *`,
        [shopId, kind, duplicateOf, reason, req.admin.id, t]
      )).rows[0]);
    }
    await audit.log({
      shopId, actorType: 'admin', userId: req.admin.id, action: 'admin.shop_census_mark', targetType: 'shop', targetId: shopId, ip,
      detail: { kind, ...(duplicateOf ? { duplicate_of: duplicateOf } : {}), before: before ? before.kind : 'none', reason, shop_id: shopId },
    }, { client: c });
    return { shopId, mark };
  });
}

module.exports = { regions, markOf, setMark, MARK_KINDS };
