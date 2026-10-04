'use strict';
/**
 * نماینده‌های فروش — شورا، چ۳.
 *
 * ── قاعده‌ها ────────────────────────────────────────────────────────
 * ⛔ **دفترِ دومی برای فروش نیست.** فروشِ نماینده همان ردیف‌های
 *    `discount_uses` است که کدشان مالِ اوست؛ این‌جا فقط خوانده و جمع
 *    می‌شود.
 * ⛔ **درصدِ کمیسیون فقط از پنل** و پیش‌فرض ندارد (`commission_required`).
 *    هیچ عددِ پولی یا درصدی در این فایل نوشته نشده.
 * ⛔ **نماینده و درصدِ لحظهٔ فروش روی خودِ ردیف** (`discount_uses.rep_id` و
 *    `commission_bp`) — عوض شدنِ درصد فروشِ گذشته را دست نمی‌زند.
 * ⛔ **هر پرس‌وجو `app` را شرط می‌کند** — نمایندهٔ دکان فروشِ پمپ را نمی‌بیند.
 * ⛔ **نماینده هویتش را از توکن می‌گیرد** (`forUser(req.user.id)`)؛ هیچ
 *    شناسهٔ نماینده‌ای از درخواستِ خودِ نماینده خوانده نمی‌شود.
 */
const { one, many, newId, now } = require('../db');
const { badRequest, notFound, conflict } = require('../middleware/errors');
const tenancy = require('./tenancy');

function appOf(raw) {
  const a = tenancy.sectionOf(raw);
  if (!a) throw badRequest('بخش (دکان یا پمپ) را بگویید', 'bad_app');
  return a;
}

/** «۷٫۵» ⇒ ۷۵۰. فقط ۰ تا ۱۰۰ و حداکثر دو رقمِ اعشار؛ نبودن خطاست، نه صفر. */
function pctToBp(raw) {
  if (raw === undefined || raw === null || String(raw).trim() === '') {
    throw badRequest('درصدِ کمیسیون را از پنل بنویسید', 'commission_required');
  }
  const s = String(raw).trim()
    .replace(/[۰-۹]/g, d => String(d.charCodeAt(0) - 0x06F0))
    .replace(/[٠-٩]/g, d => String(d.charCodeAt(0) - 0x0660))
    .replace(/[٫,]/g, '.');
  if (!/^\d{1,3}(\.\d{1,2})?$/.test(s)) throw badRequest('درصدِ کمیسیون عدد است (۰ تا ۱۰۰)', 'bad_commission');
  const bp = Math.round(Number(s) * 100);
  if (bp < 0 || bp > 10000) throw badRequest('درصدِ کمیسیون باید بین ۰ تا ۱۰۰ باشد', 'bad_commission');
  return bp;
}

function shape(r) {
  return {
    id: r.id, app: r.app, userId: r.user_id, name: r.name || '', email: r.email || '',
    commissionPct: Number(r.commission_bp) / 100, status: r.status,
    createdAt: Number(r.created_at), createdBy: r.created_by || '',
  };
}

const SELECT = `SELECT r.*, u.email FROM sales_reps r JOIN users u ON u.id = r.user_id`;

async function create(input = {}, { createdBy = '' } = {}) {
  const app = appOf(input.app);
  const bp = pctToBp(input.commissionPct);
  const email = String(input.email || '').trim().toLowerCase();
  if (!email) throw badRequest('ایمیلِ حسابِ نماینده لازم است', 'email_required');
  const user = await one('SELECT id, name FROM users WHERE lower(email) = $1', [email]);
  if (!user) throw notFound('حسابی با این ایمیل نیست — نماینده اول در برنامه حساب بسازد', 'user_not_found');
  const dup = await one('SELECT id FROM sales_reps WHERE app=$1 AND user_id=$2', [app, user.id]);
  if (dup) throw conflict('این حساب در همین بخش از قبل نماینده است', 'rep_exists');
  const id = newId('rep');
  await one(
    `INSERT INTO sales_reps (id, app, user_id, name, commission_bp, status, created_by, created_at)
     VALUES ($1,$2,$3,$4,$5,'active',$6,$7) RETURNING id`,
    [id, app, user.id, String(input.name || user.name || '').slice(0, 120), bp, createdBy, now()]
  );
  return get(id);
}

async function get(id) {
  const r = await one(`${SELECT} WHERE r.id = $1`, [String(id || '')]);
  if (!r) throw notFound('نماینده پیدا نشد', 'rep_not_found');
  return shape(r);
}

async function list({ app = '' } = {}) {
  const rows = await many(`${SELECT} WHERE ($1 = '' OR r.app = $1) ORDER BY r.created_at DESC`,
    [app ? appOf(app) : '']);
  return rows.map(shape);
}

async function update(id, input = {}) {
  const cur = await get(id);
  const bp = input.commissionPct === undefined ? Math.round(cur.commissionPct * 100) : pctToBp(input.commissionPct);
  const status = input.status === undefined ? cur.status
    : (['active', 'disabled'].includes(input.status) ? input.status : null);
  if (!status) throw badRequest('وضعیت فقط active یا disabled', 'bad_status');
  const name = input.name === undefined ? cur.name : String(input.name || '').slice(0, 120);
  await one(`UPDATE sales_reps SET commission_bp=$2, status=$3, name=$4 WHERE id=$1 RETURNING id`,
    [cur.id, bp, status, name]);
  return get(cur.id);
}

/** نماینده‌های فعالِ همین حساب — هویت فقط از توکن. */
async function forUser(userId) {
  const rows = await many(`${SELECT} WHERE r.user_id = $1 AND r.status = 'active' ORDER BY r.app`, [String(userId || '')]);
  return rows.map(shape);
}

/** نمایندهٔ فعالِ همین بخش برای کدِ تخفیف — نبود ⇒ خطا. */
async function activeRepFor(repId, app) {
  const r = await one(`SELECT * FROM sales_reps WHERE id=$1 AND app=$2 AND status='active'`, [String(repId || ''), app]);
  if (!r) throw badRequest('نمایندهٔ فعالی با این شناسه در این بخش نیست', 'bad_rep');
  return r;
}

function range({ from, to } = {}) {
  const f = Number(from) > 0 ? Number(from) : 0;
  const t = Number(to) > 0 ? Number(to) : 8.64e15;
  return [f, t];
}

/**
 * فروش‌های این نماینده‌ها — فقط ردیف‌هایی که `rep_id`ِ خودشان را دارند.
 * نامِ مشتری همان نامِ دکان یا پمپ است؛ نه ایمیل، نه شماره.
 */
async function sales(repIds, opts = {}) {
  const ids = (repIds || []).map(String).filter(Boolean);
  if (!ids.length) return [];
  const [f, t] = range(opts);
  const rows = await many(
    `SELECT du.*, dc.code AS code,
            COALESCE(sh.name, st.name, '') AS customer,
            COALESCE(ss.plan, s.plan, '') AS plan
       FROM discount_uses du
       JOIN discount_codes dc ON dc.id = du.code_id
       LEFT JOIN shops sh ON du.app = 'shop' AND sh.id = du.tenant_id
       LEFT JOIN stations st ON du.app = 'pump' AND st.id = du.tenant_id
       LEFT JOIN subscriptions s ON du.app = 'shop' AND s.id = du.subscription_id
       LEFT JOIN station_subscriptions ss ON du.app = 'pump' AND ss.id = du.subscription_id
      WHERE du.rep_id = ANY($1::text[]) AND du.created_at >= $2 AND du.created_at < $3
      ORDER BY du.created_at DESC
      LIMIT $4`,
    [ids, f, t, Math.min(Number(opts.limit) || 500, 2000)]
  );
  return rows.map(r => ({
    id: r.id, repId: r.rep_id, app: r.app, code: r.code, customer: r.customer, plan: r.plan,
    price: Number(r.price), finalPrice: Number(r.final_price), currency: r.currency || '',
    commissionPct: Number(r.commission_bp) / 100,
    commission: Math.round(Number(r.final_price) * Number(r.commission_bp) / 10000),
    at: Number(r.created_at),
  }));
}

/** جمعِ فروش و کمیسیون به تفکیکِ ارز — از همان ردیف‌ها، نه از جای دیگر. */
function totals(rows) {
  const by = {};
  for (const r of rows) {
    const k = r.currency || '';
    by[k] ||= { currency: k, count: 0, sales: 0, commission: 0 };
    by[k].count++; by[k].sales += r.finalPrice; by[k].commission += r.commission;
  }
  return {
    count: rows.length,
    customers: new Set(rows.map(r => r.app + ':' + r.customer)).size,
    byCurrency: Object.values(by),
  };
}

async function report(repId, opts = {}) {
  const rep = await get(repId);
  const rows = await sales([rep.id], opts);
  return { rep, totals: totals(rows), sales: rows };
}

module.exports = { pctToBp, create, get, list, update, forUser, activeRepFor, sales, totals, report };
