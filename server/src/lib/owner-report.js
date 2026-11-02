'use strict';
/**
 * PAY-20 / RPT-12 — گزارشِ خودکار برای صاحبِ دکان (پایانِ روز، یا هفتگی).
 *
 *   پرچم ── `shop.owner_report` (همان قاعدهٔ `orders.flagOf`؛ ⛔ پیش‌فرض خاموش). خاموش ⇒ کارگر به آن دکان دست
 *           نمی‌زند، هیچ ردیف و اعلانی نیست و `GET /api/shop/owner-report` فقط `{enabled:false}` است.
 *   کی ──── ساعتِ انتخابیِ صاحب (`hour`، وقتِ محلیِ دکان: `storefronts.timezone`، وگرنه `DEFAULT_TIMEZONE`) و
 *           در هفتگی فقط روزِ انتخابی (`weekday`، ۰ = یکشنبه … ۶ = شنبه). سروری که خاموش بوده تا [CATCHUP_MS]
 *           پس از آن ساعت هنوز می‌فرستد؛ دیرتر نه.
 *   بازه ── ساعتِ پیش از [MORNING_BEFORE] (صبح) ⇒ روزِ **دیروز**، کامل؛ از ظهر به بعد ⇒ **امروز** تا همان ساعت.
 *           هفتگی ⇒ هفت روزِ محلی که به همان روز ختم می‌شوند. روزِ هر فاکتور و برگشتی با `createdAt` و ساعتِ
 *           محلیِ دکان؛ ردیفِ بی `createdAt` با `date` ِ خودش (همان قاعدهٔ `report-core`).
 *   عدد ─── فقط از ردیف‌های همگام‌شدهٔ همین دکان (`sync_rows`، یا `sync_v3_rows` اگر منبعِ کاتالوگ v3 است) و
 *           فقط با هستهٔ مشترکِ گزارش (`report-core.js`، رونوشتِ بایت‌به‌بایتِ `license/report-core.js`):
 *           شمار و جمعِ فروش، برگشتی، خالص، تفکیکِ روشِ پرداختِ ثبت‌شده روی فاکتور، پرفروش‌ها. کم‌موجود فقط
 *           وقتی موجودیِ سرور (`shop.catalog`) روشن است؛ وگرنه `null` (نه صفرِ ساختگی). بیش از [MAX_ROWS]
 *           ردیف ⇒ `partial: true` و همین در متن گفته می‌شود.
 *   کانال ─ همان مرکزِ اعلان (`notices.deliverOne`): داخلِ برنامه، پوش، ایمیل — هر کدام نتیجهٔ **واقعی** در
 *           `notice_deliveries`. گیرنده فقط صاحبِ دکان (`shops.owner_user_id`) و ردیف بی `tenant_id` ⇒ کارمندانِ
 *           دکان آن را در صندوقِ اعلانِ خود نمی‌بینند. واتس‌اپ در این مخزن هیچ سرویس‌دهنده‌ای ندارد (D-09) ⇒
 *           کانالِ پذیرفتنی نیست. ایمیل در `notify-cost.js` کانالِ پولی نیست؛ سقفِ عملی همان یک گزارش در روز است.
 *   ایمنی ─ هر دکان در هر روزِ محلی **یک** گزارش: ردیفِ `owner_reports` با `UNIQUE (shop_id, report_date)` پیش از
 *           ساختن و فرستادن درج می‌شود (`ON CONFLICT DO NOTHING`) و فقط برندهٔ درج می‌فرستد ⇒ دو نمونهٔ سرور یا
 *           دو دورِ پیاپی پیامِ دوم نمی‌سازند. شکستِ پس از گرفتن ⇒ `error` و بی تلاشِ دوباره (حداکثر یک بار).
 */
const config = require('../config');
const { query, one, many, now } = require('../db');
const { ulid } = require('./ulid');
const { badRequest, conflict } = require('../middleware/errors');
const R = require('./report-core');
const T = require('./text-core');
const C = require('./calendar-core');
const fw = require('./finance-window');
const schema = require('./sync-v3-schema');
const { toLegacy } = require('./sync-v3-to-v1');

const FLAG_KEY = 'shop.owner_report';
const FREQUENCIES = Object.freeze(['daily', 'weekly']);
const CHANNELS = Object.freeze(['inapp', 'push', 'email']);
const HOUR_MS = 60 * 60 * 1000;
const CATCHUP_MS = 3 * HOUR_MS;
const MORNING_BEFORE = 12;
const TICK_MS = 60 * 1000;
const PAGE = 500;
const MAX_ROWS = 50_000;
const TOP_N = 5;
const RECENT = 14;
const DEFAULTS = Object.freeze({ active: false, frequency: 'daily', weekday: 6, hour: 21, channels: Object.freeze(['inapp']) });
const STORES = Object.freeze({ v1: 'sync_rows', v3: 'sync_v3_rows' });
const ACC = "app='shop' AND account_kind='shop' AND account_id=$1";
const METHOD_LABELS = Object.freeze({
  cash: 'نقد', credit: 'نسیه', account: 'نسیه', card: 'کارت', transfer: 'حواله', wallet: 'کیف پول',
  store_credit: 'اعتبارِ فروشگاه', gift_card: 'کارتِ هدیه', mixed: 'ترکیبی', online: 'آنلاین', cod: 'نقد هنگامِ تحویل',
  other: 'سایر', '': 'نامعلوم',
});

const flagOf = (shopId) => require('./orders').flagOf(shopId, FLAG_KEY);

// ── تنظیم ────────────────────────────────────────────────────────────

function cleanChannels(list) {
  const arr = Array.isArray(list) ? list : (typeof list === 'string' ? safeJson(list, []) : []);
  return CHANNELS.filter((c) => arr.includes(c));
}
function safeJson(s, def) { try { return JSON.parse(s); } catch { return def; } }

function shapeSettings(r) {
  if (!r) return { ...DEFAULTS, channels: [...DEFAULTS.channels], version: 0, updatedAt: null };
  return {
    active: r.active === true, frequency: FREQUENCIES.includes(r.frequency) ? r.frequency : DEFAULTS.frequency,
    weekday: Number(r.weekday), hour: Number(r.hour), channels: cleanChannels(r.channels),
    version: Number(r.version), updatedAt: Number(r.updated_at),
  };
}

async function settingsOf(shopId) {
  return shapeSettings(await one('SELECT * FROM owner_report_settings WHERE shop_id=$1', [String(shopId)]));
}

function intIn(v, field, min, max) {
  if (!Number.isInteger(v) || v < min || v > max) throw badRequest(`${field}: عددِ صحیح از ${min} تا ${max}`, 'bad_setting');
  return v;
}

/** بدنهٔ `PUT` ⇒ تنظیمِ تازه (هر فیلدِ نیامده همان پیشین). نادرست ⇒ ۴۰۰ `bad_setting`. */
function parseSettings(b, prev) {
  const next = { active: prev.active, frequency: prev.frequency, weekday: prev.weekday, hour: prev.hour, channels: prev.channels };
  if (b.active !== undefined) {
    if (typeof b.active !== 'boolean') throw badRequest('active: true یا false', 'bad_setting');
    next.active = b.active;
  }
  if (b.frequency !== undefined) {
    if (!FREQUENCIES.includes(b.frequency)) throw badRequest('frequency: daily یا weekly', 'bad_setting');
    next.frequency = b.frequency;
  }
  if (b.weekday !== undefined) next.weekday = intIn(b.weekday, 'weekday', 0, 6);
  if (b.hour !== undefined) next.hour = intIn(b.hour, 'hour', 0, 23);
  if (b.channels !== undefined) {
    if (!Array.isArray(b.channels) || !b.channels.every((c) => CHANNELS.includes(c))) {
      throw badRequest('channels: فقط inapp، push و email (واتس‌اپ وصل نیست)', 'bad_setting');
    }
    next.channels = cleanChannels(b.channels);
  }
  if (!next.channels.length) throw badRequest('دست‌کم یک کانال لازم است', 'bad_setting');
  return next;
}

/** فقط صاحبِ دکان (درِ `routes/owner-report.js`). `expectedVersion` ِ کهنه ⇒ ۴۰۹. */
async function saveSettings(shopId, body, userId, at = now()) {
  const prev = await settingsOf(shopId);
  const b = body && typeof body === 'object' ? body : {};
  if (b.expectedVersion !== undefined && b.expectedVersion !== prev.version) {
    throw conflict('تنظیم در این میان عوض شده است؛ دوباره باز کنید', 'version_conflict');
  }
  const n = parseSettings(b, prev);
  const row = await one(
    `INSERT INTO owner_report_settings (shop_id, active, frequency, weekday, hour, channels, version, updated_by, updated_at)
     VALUES ($1,$2,$3,$4,$5,$6::jsonb,1,$7,$8)
     ON CONFLICT (shop_id) DO UPDATE SET active=excluded.active, frequency=excluded.frequency, weekday=excluded.weekday,
       hour=excluded.hour, channels=excluded.channels, version=owner_report_settings.version + 1,
       updated_by=excluded.updated_by, updated_at=excluded.updated_at
     RETURNING *`,
    [String(shopId), n.active, n.frequency, n.weekday, n.hour, JSON.stringify(n.channels), String(userId || ''), at]
  );
  return shapeSettings(row);
}

// ── زمان ─────────────────────────────────────────────────────────────

const clocks = new Map();
/** ساعتِ دکان برای `report-core`: روزِ محلی و ساعتِ محلیِ هر لحظه (با Intl، پس ساعتِ تابستانی هم درست). */
function clockFor(zone) {
  if (clocks.has(zone)) return clocks.get(zone);
  const f = new Intl.DateTimeFormat('en-CA', {
    timeZone: zone, hourCycle: 'h23', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit',
  });
  const parts = (ms) => Object.fromEntries(f.formatToParts(new Date(ms)).map((x) => [x.type, x.value]));
  const clock = {
    date: (ms) => { const p = parts(ms); return `${p.year}-${p.month}-${p.day}`; },
    hour: (ms) => Number(parts(ms).hour) % 24,
  };
  if (clocks.size > 64) clocks.clear();
  clocks.set(zone, clock);
  return clock;
}

const dowOf = (ymd) => { const [y, m, d] = ymd.split('-').map(Number); return new Date(Date.UTC(y, m - 1, d)).getUTCDay(); };

/**
 * گزارشِ وقت‌رسیده در لحظهٔ `at` — یا null. خالص (بی پایگاه): همین در آزمونِ مرزِ روز و منطقه سنجیده می‌شود.
 * ⇒ { reportDate, frequency, from, to, fromAt, until, dueAt }؛ `until` مرزِ بالای بازه است (بی خودش).
 */
function dueOf(s, at, zone) {
  if (!s || s.active !== true || !FREQUENCIES.includes(s.frequency)) return null;
  const today = clockFor(zone).date(at);
  for (const d of [today, fw.ymdAdd(today, -1)]) {
    const start = fw.dayStart(d, zone);
    const dueAt = start + s.hour * HOUR_MS;
    if (!(dueAt <= at && at < dueAt + CATCHUP_MS)) continue;
    if (s.frequency === 'weekly' && dowOf(d) !== s.weekday) continue;
    const morning = s.hour < MORNING_BEFORE;
    const to = morning ? fw.ymdAdd(d, -1) : d;
    const from = s.frequency === 'weekly' ? fw.ymdAdd(to, -6) : to;
    return { reportDate: d, frequency: s.frequency, from, to, fromAt: fw.dayStart(from, zone), until: morning ? start : dueAt, dueAt };
  }
  return null;
}

async function timezoneOf(shopId) {
  const sf = await one('SELECT timezone FROM storefronts WHERE shop_id=$1', [String(shopId)]);
  return (sf && sf.timezone) || config.defaults.timezone || 'Asia/Kabul';
}

// ── ردیف‌های همگام‌شده ⇒ دفترِ `report-core` ─────────────────────────────

function decode(v) {
  if (typeof v === 'string') return safeJson(v, {});
  return v && typeof v === 'object' && !Array.isArray(v) ? v : {};
}

/** ردیف ⇒ شکلِ دفترِ وب (یکاهای v1). حذف‌شده: فاکتور «لغو» می‌شود؛ قلم و برگشت کنار می‌روند (null). */
function legacyRow(fmt, table, r) {
  const d = decode(r.data);
  const data = fmt === 'v3' ? toLegacy(schema.specOf(table) || { money: [], qty: [] }, d) : d;
  const gone = r.deleted_at !== null && r.deleted_at !== undefined;
  if (gone && table !== 'sales') return null;
  return { ...data, id: r.row_id, ...(gone ? { status: 'cancelled' } : {}) };
}

//  `createdAt` ِ متنی یا اعشاری ⇒ ۰ (یعنی «با `date`»)؛ CASE تا cast هرگز روی متنِ نادرست نرود
const CREATED = "(CASE WHEN data->>'createdAt' ~ '^[0-9]{1,15}$' THEN (data->>'createdAt')::bigint ELSE 0 END)";

//  نامِ جدول فقط از همین فهرستِ ثابت و در متنِ پرسش (نه پارامتر) تا نمایهٔ جزئیِ `saleItems` (مهاجرتِ ۱۱۹) به کار بیاید
const TABLE_SQL = Object.freeze({ sales: "'sales'", saleItems: "'saleItems'", returns: "'returns'", products: "'products'" });

async function rowsInWindow(T0, table, shopId, w) {
  return (await query(
    `SELECT row_id, data, deleted_at FROM ${T0}
      WHERE ${ACC} AND table_name=${TABLE_SQL[table]}
        AND ((${CREATED} >= $2 AND ${CREATED} < $3) OR (${CREATED} = 0 AND data->>'date' >= $4 AND data->>'date' <= $5))
      ORDER BY row_id LIMIT ${MAX_ROWS + 1}`,
    [shopId, w.fromAt, w.until, w.from, w.to]
  )).rows;
}

async function rowsById(T0, table, shopId, ids, bySale = false) {
  if (!ids.length) return [];
  const where = bySale ? "data->>'saleId' = ANY($2::text[])" : 'row_id = ANY($2::text[])';
  return (await query(
    `SELECT row_id, data, deleted_at FROM ${T0} WHERE ${ACC} AND table_name=${TABLE_SQL[table]} AND ${where}
      ORDER BY row_id LIMIT ${MAX_ROWS + 1}`,
    [shopId, ids]
  )).rows;
}

/** دفترِ بازه: فاکتورها و برگشتی‌های بازه، فاکتورِ هر برگشت، قلم‌ها و نامِ کالاها. */
async function ledgerOf(shopId, w) {
  const fmt = (await require('./stock').settingsOf(shopId)).syncSource === 'v3' ? 'v3' : 'v1';
  const T0 = STORES[fmt];
  let partial = false;
  const cap = (rows) => { if (rows.length > MAX_ROWS) { partial = true; return rows.slice(0, MAX_ROWS); } return rows; };
  const map = (table, rows) => cap(rows).map((r) => legacyRow(fmt, table, r)).filter(Boolean);
  const sales = map('sales', await rowsInWindow(T0, 'sales', shopId, w));
  const returns = map('returns', await rowsInWindow(T0, 'returns', shopId, w));
  const known = new Set(sales.map((s) => s.id));
  const extra = [...new Set(returns.map((r) => String(r.saleId || '')).filter((id) => id && !known.has(id)))];
  sales.push(...map('sales', await rowsById(T0, 'sales', shopId, extra)));
  const saleItems = map('saleItems', await rowsById(T0, 'saleItems', shopId, sales.map((s) => s.id), true));
  const pids = [...new Set([...saleItems, ...returns].map((x) => String(x.productId || '')).filter(Boolean))];
  const products = map('products', await rowsById(T0, 'products', shopId, pids));
  return { fmt, partial, raw: { sales, saleItems, returns, products } };
}

/** کالای کم‌موجود از موجودیِ سرور (همان قاعدهٔ `stock.checkProducts`)؛ بی `shop.catalog` ⇒ null. */
async function lowStockOf(shopId) {
  const stock = require('./stock');
  if (!(await stock.flagOf(shopId))) return null;
  const st = await stock.settingsOf(shopId);
  const r = await one(
    `SELECT COUNT(*)::int AS n FROM catalog_products p
      WHERE p.shop_id=$1 AND p.deleted=false AND p.active=true AND p.min_stock_milli > 0
        AND COALESCE((SELECT SUM(l.on_hand_milli) FROM stock_levels l WHERE l.shop_id=p.shop_id AND l.branch_id=''
                       AND l.product_id=p.product_id AND l.location = ANY($2::text[])), 0) <= p.min_stock_milli`,
    [shopId, st.sellableLocations]
  );
  return r ? Number(r.n) : 0;
}

/**
 * خلاصهٔ بازه از دفترِ خام — خالص، فقط با `report-core` (همان عددهای صفحهٔ گزارشِ برنامه). پول به واحدِ کوچک.
 */
function summarize(raw, w, zone, { partial = false, lowStock = null } = {}) {
  const r = R.sales(raw, { from: w.from, to: w.to, clock: clockFor(zone) });
  const t = r.totals;
  const payments = r.payment.filter((p) => p.invoices > 0 || p.returns !== 0)
    .map((p) => ({ method: p.key, invoices: p.invoices, sales: p.sales, returns: p.returns, netSales: p.netSales }));
  const cashNet = payments.filter((p) => p.method === 'cash').reduce((s, p) => s + p.netSales, 0);
  const topProducts = r.product.filter((p) => p.key !== '' && p.netSales > 0).slice(0, TOP_N)
    .map((p) => ({ productId: p.key, name: p.name, qtyMilli: p.qty, netSales: p.netSales }));
  return {
    v: 1, frequency: w.frequency, from: w.from, to: w.to, until: w.until, timezone: zone,
    invoices: t.invoices, sales: t.sales, discount: t.discount, returnCount: t.returnCount, returns: t.returns,
    netSales: t.netSales, payments, cashNet, otherNet: t.netSales - cashNet, topProducts, lowStock, partial,
  };
}

async function buildSummary(shopId, w, zone) {
  const L = await ledgerOf(shopId, w);
  return summarize(L.raw, w, zone, { partial: L.partial, lowStock: await lowStockOf(shopId) });
}

// ── متن ──────────────────────────────────────────────────────────────

const fa = (n) => T.displayDigits(String(n), 'fa');
const money = (minor) => T.formatAfghani(Math.round(Number(minor) / 100), '', 'fa');
const day = (iso) => C.formatSolarLong(iso, 'fa', 'fa');
const clean = (s) => String(s || '').replace(/[{}<>]/g, '').trim().slice(0, 60) || 'بی‌نام';

/** متنِ اعلان: فقط عددهای خلاصه، بی هیچ عددِ دیگری. */
function render(sum, { shopName = '' } = {}) {
  const weekly = sum.frequency === 'weekly';
  const title = `${weekly ? 'گزارشِ هفتگی' : 'گزارشِ پایانِ روز'}${shopName ? ` — ${clean(shopName)}` : ''}`;
  const clock = clockFor(sum.timezone);
  const cut = clock.date(sum.until) === sum.to ? ` (تا ساعتِ ${fa(String(clock.hour(sum.until)).padStart(2, '0'))}:۰۰)` : '';
  const lines = [weekly ? `از ${day(sum.from)} تا ${day(sum.to)}${cut}` : `${day(sum.to)}${cut}`];
  if (sum.invoices === 0 && sum.returnCount === 0) {
    lines.push('در این بازه هیچ فاکتور یا برگشتیِ همگام‌شده‌ای نیست.');
  } else {
    lines.push(`فروش: ${fa(sum.invoices)} فاکتور — ${money(sum.sales)}`);
    lines.push(`برگشتی: ${fa(sum.returnCount)} — ${money(sum.returns)}`);
    lines.push(`خالص: ${money(sum.netSales)}`);
    if (sum.payments.length) {
      lines.push(`روشِ پرداخت: ${sum.payments.map((p) => `${METHOD_LABELS[p.method] || clean(p.method)} ${money(p.netSales)}`).join('، ')}`);
    }
    if (sum.topProducts.length) {
      lines.push(`پرفروش‌ها: ${sum.topProducts.map((p, i) => `${fa(i + 1)}. ${clean(p.name)} (${money(p.netSales)})`).join('، ')}`);
    }
  }
  if (sum.lowStock !== null && sum.lowStock !== undefined) lines.push(`کالای کم‌موجود: ${fa(sum.lowStock)}`);
  if (sum.partial) lines.push('⚠️ ردیف‌ها از سقفِ یک گزارش بیشتر بود؛ این عددها ناقص‌اند.');
  lines.push('عددها از ردیف‌های همگام‌شده تا لحظهٔ ساختِ گزارش است؛ مبلغ‌ها به پولِ دکان.');
  return { title, body: lines.join('\n') };
}

// ── فرستادن ──────────────────────────────────────────────────────────

/** همان مرکزِ اعلان؛ گیرنده فقط صاحب، بی `tenant_id` (صندوقِ اعلانِ کارمندان آن را نمی‌بیند). */
async function deliver(row, text, channels) {
  const notices = require('./notices');
  const n = await notices.create({
    app: 'shop', audience: { kind: 'user', user_id: row.owner_user_id }, channels,
    title: text.title, body: text.body, templateKey: 'owner_report',
  }, { createdBy: 'system', system: true });
  const b = await notices.brand();
  const who = {
    app: 'shop', userId: row.owner_user_id, tenantId: '', tenantKind: 'shop',
    name: row.owner_name || '', tenantName: row.shop_name || '', email: row.email || '',
  };
  const results = {};
  for (const ch of channels) results[ch] = await notices.deliverOne(n, who, ch, { run: 1, title: text.title, body: text.body, b });
  const sent = Object.values(results).filter((s) => s !== 'error').length;
  const t = now();
  await query(
    'UPDATE notices SET status=$2, sent_at=$3, runs=1, counts=$4::jsonb, updated_at=$3 WHERE id=$1',
    [n.id, sent ? 'sent' : 'failed', t, JSON.stringify({ recipients: 1, sent, error: channels.length - sent })]
  );
  return { noticeId: n.id, results, sent };
}

/** یک دکان: وقت‌رسیده؟ گرفتنِ ردیفِ یکتا، ساختن، فرستادن. ⇒ شناسهٔ گزارشِ تازه یا null. */
async function runShop(row, at) {
  //  منطقه از همان پرسشِ `sweep` (بی پرسشِ جدا برای هر دکان در هر دقیقه)؛ پرچم فقط وقتی گزارش وقت‌رسیده است
  const zone = row.sf_timezone || config.defaults.timezone || 'Asia/Kabul';
  const s = shapeSettings(row);
  const w = dueOf(s, at, zone);
  if (!w || !s.channels.length || !row.owner_user_id || !(await flagOf(row.shop_id))) return null;
  const id = ulid(at);
  const claimed = await one(
    `INSERT INTO owner_reports (id, shop_id, report_date, frequency, from_date, to_date, until_at, timezone, created_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9) ON CONFLICT (shop_id, report_date) DO NOTHING RETURNING id`,
    [id, row.shop_id, w.reportDate, w.frequency, w.from, w.to, w.until, zone, at]
  );
  if (!claimed) return null;
  try {
    const summary = await buildSummary(row.shop_id, w, zone);
    const out = await deliver(row, render(summary, { shopName: row.shop_name }), s.channels);
    await query(
      `UPDATE owner_reports SET status=$2, summary=$3::jsonb, results=$4::jsonb, notice_id=$5, sent_at=$6 WHERE id=$1`,
      [id, out.sent ? 'sent' : 'error', JSON.stringify(summary), JSON.stringify(out.results), out.noticeId, now()]
    );
    await require('./audit').log({
      shopId: row.shop_id, actorType: 'system', action: 'owner_report.sent', targetType: 'owner_report', targetId: id,
      detail: { reportDate: w.reportDate, frequency: w.frequency, results: out.results },
    }).catch(() => {});
  } catch (err) {
    await query(`UPDATE owner_reports SET status='error', error=$2 WHERE id=$1`, [id, String((err && err.message) || err).slice(0, 300)]);
    console.error('[owner-report]', row.shop_id, err && err.message);
  }
  return id;
}

/** یک دور برای همهٔ دکان‌های با تنظیمِ روشن. ⇒ شمارِ گزارش‌های تازه. */
async function sweep(at = now()) {
  let last = '';
  let n = 0;
  for (;;) {
    const page = await many(
      `SELECT s.*, sh.name AS shop_name, sh.owner_user_id, u.email, u.name AS owner_name, sf.timezone AS sf_timezone
         FROM owner_report_settings s
         JOIN shops sh ON sh.id = s.shop_id AND sh.status = 'active'
         LEFT JOIN users u ON u.id = sh.owner_user_id
         LEFT JOIN storefronts sf ON sf.shop_id = s.shop_id
        WHERE s.active = true AND s.shop_id > $1
        ORDER BY s.shop_id LIMIT ${PAGE}`,
      [last]
    );
    for (const row of page) {
      try { if (await runShop(row, at)) n++; } catch (err) { console.error('[owner-report]', row.shop_id, err && err.message); }
    }
    if (page.length < PAGE) break;
    last = page[page.length - 1].shop_id;
  }
  return n;
}

// ── نمای صاحب ────────────────────────────────────────────────────────

function shapeReport(r) {
  return {
    id: r.id, reportDate: r.report_date, frequency: r.frequency, from: r.from_date, to: r.to_date,
    until: Number(r.until_at), status: r.status, summary: decode(r.summary), results: decode(r.results),
    createdAt: Number(r.created_at), sentAt: r.sent_at === null || r.sent_at === undefined ? null : Number(r.sent_at),
  };
}

/** `GET /api/shop/owner-report` برای صاحب: تنظیم، کانال‌های ممکن، گزارش‌های اخیر. */
async function viewOf(shopId) {
  const owner = await one(
    'SELECT u.email FROM shops sh LEFT JOIN users u ON u.id = sh.owner_user_id WHERE sh.id=$1', [String(shopId)]
  );
  const reports = await many(
    `SELECT * FROM owner_reports WHERE shop_id=$1 ORDER BY report_date DESC LIMIT ${RECENT}`, [String(shopId)]
  );
  return {
    settings: await settingsOf(shopId),
    timezone: await timezoneOf(shopId),
    morningBefore: MORNING_BEFORE,
    channels: { inapp: true, push: true, email: !!(owner && owner.email), whatsapp: false },
    reports: reports.map(shapeReport),
  };
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
  const run = require('./job-beat').wrap('owner-report', TICK_MS, tick);   //  پنلِ مرکزی ۴.۹
  timer = setInterval(() => run().catch((err) => console.error('[owner-report]', err.message)), TICK_MS);
  if (timer.unref) timer.unref();
}
function stop() {
  if (timer) clearInterval(timer);
  timer = null;
}

module.exports = {
  FLAG_KEY, FREQUENCIES, CHANNELS, CATCHUP_MS, MORNING_BEFORE, MAX_ROWS, DEFAULTS,
  flagOf, settingsOf, saveSettings, parseSettings, dueOf, clockFor, summarize, buildSummary, render,
  runShop, sweep, viewOf, start, stop,
};
