'use strict';
/**
 * NTF-10 — عذرخواهی و کوپنِ خودکار برای تأخیرِ بیش از حد (§۶.۱۹؛ مهاجرتِ ۱۹۷).
 *
 *   پرچم ── `shop.orders.late_apology` (همان قاعدهٔ `orders.flagOf`؛ ⛔ پیش‌فرض خاموش). خاموش ⇒ کارگر به آن دکان
 *           دست نمی‌زند، پیگیری بی کلیدِ `apology`، کدِ پوزش در ثبت/پیش‌نمایش ناشناخته (همان راهِ کدِ تخفیف) و
 *           درِ صاحب ۴۰۳ `late_apology_disabled`. روی آن، سیاستِ خودِ صاحب (`order_late_apology_policy.enabled`).
 *   تأخیر ─ سفارشِ **ویترین** (نه آزمایشی) که هنوز در دستِ دکان است ([LATE_STATUSES]: تازه، قبول‌شده، جمع‌آوری،
 *           بسته‌بندی) و [lateMinutes] (تنظیمِ صاحب، ۵ تا ۱۴۴۰) از زمانِ وعده‌شده گذشته است: `ready_at` (قبول و
 *           عقب‌انداختنِ ORD-07)، وگرنه زمانِ زمان‌بندی‌شده، وگرنه زمانِ ثبت. سفارشی که بیش از [MAX_LATE_MS] از
 *           وعده‌اش گذشته نامزد نیست (روشن کردنِ پرچم سیلِ پوزش برای سفارش‌های کهنه نمی‌سازد). در راه بودنِ پیک
 *           این‌جا نیست (زمانِ رسیدنِ پیک وعدهٔ دکان نیست).
 *   یکی ── **حداکثر یک** تصمیم برای هر سفارش: ردیفِ `order_late_apologies` با کلیدِ اصلیِ `order_id`، درونِ تراکنشی که
 *           ردیفِ سفارش را `FOR UPDATE` قفل و وضعیت و زمان را دوباره می‌سنجد؛ `ON CONFLICT DO NOTHING` ⇒ دو نمونهٔ سرور
 *           یا دو دورِ کارگر پوزشِ دوم، پیامِ دوم یا کوپنِ دوم نمی‌سازند. سفارشِ لغوشده هرگز نامزد نیست.
 *   مشتری ─ تأخیری که کارِ خودِ مشتری است (`skipped_customer`: بی پیام، بی کوپن): عقب‌انداختن با دلیلِ
 *           `customer_request` (ORD-07)، پیشنهادِ بازِ ORD-14 یا جایگزینِ منتظرِ پاسخِ PCK-05.
 *   پیام ── رویدادِ `order_late_apology` در صفِ پیامِ مشتری (`customer-notify`، پرچمِ `shop.customer_notify`): همان
 *           یکتاییِ (order_id, event)، فقط کانال‌های رایگان، سقفِ هر مشتری و سقفِ هزینهٔ روزانهٔ هر دکان و هر کانال
 *           (NTF-02، `notify-cost`). متن بی دادهٔ حساس (فقط نامِ ویترین و شمارهٔ سفارش)؛ کد در صفحهٔ پیگیری است.
 *   کوپن ── اختیاری (صاحب): مبلغ (`amountMinor`) یا درصد (`bp`، تا ۵۰٪)، مهلت به روز. کد `AP` + ۸ نویسهٔ تصادفی،
 *           **یک‌بارمصرف** و **فقط برای همان شماره** (`phone_key` = HMAC ِ شماره) در همان دکان. مصرف در ثبتِ ویترین با
 *           همان کلیدِ `promoCode` (`storefront-promos.wrap`)، درونِ تراکنشِ ثبت، زیرِ قفلِ ردیفِ کوپن و فقط اگر هنوز
 *           مصرف نشده (`redeemed_at IS NULL`) ⇒ دو ثبتِ هم‌زمان یکی را می‌برند؛ تکرارِ همان ثبت همان سفارش است.
 *           سفارشِ مبدأ لغو/رد شده ⇒ کوپن بی‌اعتبار. تخفیف فقط از جمعِ اقلام (نه هزینهٔ ارسال)، پلهٔ ۱۰۰.
 */
const crypto = require('crypto');
const db = require('../db');
const { one, many, now, tx } = db;
const { ulid } = require('./ulid');
const orders = require('./orders');
const { badRequest, conflict } = require('../middleware/errors');

const FLAG_KEY = 'shop.orders.late_apology';
const EVENT_TYPE = 'late_apology';
const NOTIFY_EVENT = 'order_late_apology';
const LATE_STATUSES = Object.freeze(['new', 'accepted', 'picking', 'packed']);
const VOID_STATUSES = Object.freeze(['rejected', 'auto_rejected', 'cancelled_by_customer', 'cancelled']);
const COUPON_KINDS = Object.freeze(['', 'amount', 'percent']);
const DEFAULTS = Object.freeze({ enabled: false, lateMinutes: 30, coupon: null, version: 0, updatedAt: null });
const MIN = 60_000;
const DAY = 24 * 60 * MIN;
const MAX_LATE_MS = DAY;
const TICK_MS = MIN;
const BATCH = 200;
const STEP_MINOR = 100;
const CODE_RE = /^AP[0-9A-Z]{8}$/;
const CODE_ALPHABET = 'ABCDEFGHJKMNPQRSTVWXYZ23456789';
const MAX_LIST = 50;

const flagOf = (shopId) => orders.flagOf(shopId, FLAG_KEY);
const asJson = (x) => (x && typeof x === 'object' ? x : (() => { try { return JSON.parse(x); } catch { return {}; } })());
const int = (v) => (v === null || v === undefined ? null : Number(v));

// ════════════════════════════════════════════════════════════════════
//  سیاستِ صاحب
// ════════════════════════════════════════════════════════════════════

function policyView(r) {
  if (!r) return { ...DEFAULTS };
  const kind = r.coupon_kind || '';
  return {
    enabled: r.enabled === true, lateMinutes: Number(r.late_minutes),
    coupon: kind ? { kind, amountMinor: Number(r.coupon_amount_minor), bp: Number(r.coupon_bp), days: Number(r.coupon_days) } : null,
    version: Number(r.version), updatedAt: Number(r.updated_at),
  };
}

async function policyOf(shopId) {
  return policyView(await one('SELECT * FROM order_late_apology_policy WHERE shop_id=$1', [String(shopId)]));
}

const intIn = (v, min, max, field, code) => {
  if (!Number.isSafeInteger(v) || v < min || v > max) throw badRequest(`${field} باید عددی از ${min} تا ${max} باشد`, code);
  return v;
};

function parsePolicy(body) {
  const b = body && typeof body === 'object' ? body : {};
  if (typeof b.enabled !== 'boolean') throw badRequest('روشن یا خاموش؟', 'bad_enabled');
  const lateMinutes = intIn(b.lateMinutes, 5, 1440, 'آستانهٔ تأخیر (دقیقه)', 'bad_late_minutes');
  let coupon = null;
  if (b.coupon !== undefined && b.coupon !== null) {
    const c = b.coupon && typeof b.coupon === 'object' ? b.coupon : {};
    if (!COUPON_KINDS.includes(c.kind) || !c.kind) throw badRequest('نوعِ کوپن: مبلغ یا درصد', 'bad_coupon');
    coupon = {
      kind: c.kind,
      amountMinor: c.kind === 'amount' ? intIn(c.amountMinor, 1, 100_000_000, 'مبلغِ کوپن', 'bad_coupon') : 0,
      bp: c.kind === 'percent' ? intIn(c.bp, 1, 5000, 'درصدِ کوپن (۱ تا ۵۰۰۰ = ۵۰٪)', 'bad_coupon') : 0,
      days: intIn(c.days, 1, 90, 'مهلتِ کوپن (روز)', 'bad_coupon'),
    };
  }
  const expectedVersion = b.expectedVersion === undefined || b.expectedVersion === null ? null
    : intIn(b.expectedVersion, 0, 2_000_000_000, 'نسخه', 'bad_version');
  return { enabled: b.enabled, lateMinutes, coupon, expectedVersion };
}

/** نوشتنِ سیاست (صاحب/مدیر) — همهٔ فیلدها با هم؛ `expectedVersion` ⇒ اگر کسی زودتر عوضش کرده ۴۰۹. */
async function setPolicy(shopId, body, userId) {
  const p = parsePolicy(body);
  const at = now();
  return tx(async (c) => {
    const cur = (await c.query('SELECT * FROM order_late_apology_policy WHERE shop_id=$1 FOR UPDATE', [shopId])).rows[0];
    const version = cur ? Number(cur.version) : 0;
    if (p.expectedVersion !== null && p.expectedVersion !== version) {
      throw Object.assign(conflict('تنظیم را کسِ دیگری زودتر عوض کرده است', 'version_conflict'), { data: { policy: policyView(cur) } });
    }
    const k = p.coupon || { kind: '', amountMinor: 0, bp: 0, days: 14 };
    const r = (await c.query(
      `INSERT INTO order_late_apology_policy (shop_id, enabled, late_minutes, coupon_kind, coupon_amount_minor, coupon_bp,
         coupon_days, version, updated_by, updated_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,1,$8,$9)
       ON CONFLICT (shop_id) DO UPDATE SET enabled=EXCLUDED.enabled, late_minutes=EXCLUDED.late_minutes,
         coupon_kind=EXCLUDED.coupon_kind, coupon_amount_minor=EXCLUDED.coupon_amount_minor, coupon_bp=EXCLUDED.coupon_bp,
         coupon_days=EXCLUDED.coupon_days, version=order_late_apology_policy.version+1, updated_by=EXCLUDED.updated_by,
         updated_at=EXCLUDED.updated_at
       RETURNING *`,
      [shopId, p.enabled, p.lateMinutes, k.kind, k.amountMinor, k.bp, k.days, String(userId || ''), at]
    )).rows[0];
    return policyView(r);
  });
}

// ════════════════════════════════════════════════════════════════════
//  کارگر — یک تصمیم برای هر سفارش
// ════════════════════════════════════════════════════════════════════

const dueOf = (o) => Number(o.ready_at ?? o.scheduled_for ?? o.created_at);

/** سفارش‌های نامزد (همهٔ دکان‌های با سیاستِ روشن)؛ پرچم برای هر دکان جدا سنجیده می‌شود. */
function candidates(at) {
  return many(
    `SELECT o.id, o.shop_id, o.order_no
       FROM online_orders o
       JOIN order_late_apology_policy p ON p.shop_id = o.shop_id AND p.enabled
      WHERE o.status = ANY($1::text[]) AND o.is_test = false AND o.source = 'storefront' AND o.customer_phone <> ''
        AND COALESCE(o.ready_at, o.scheduled_for, o.created_at) + p.late_minutes * ${MIN} <= $2
        AND COALESCE(o.ready_at, o.scheduled_for, o.created_at) > $3
        AND NOT EXISTS (SELECT 1 FROM order_late_apologies a WHERE a.order_id = o.id)
      ORDER BY o.created_at, o.id
      LIMIT ${BATCH}`,
    [LATE_STATUSES, at, at - MAX_LATE_MS]
  );
}

/** تأخیر کارِ خودِ مشتری است؟ (همان تراکنش و همان رویدادهای قفل‌شده) */
async function customerCausedIn(c, row, events, at) {
  for (const e of events) {
    const d = e.kind === 'note' ? asJson(e.detail) : {};
    if (d.type === 'ready_delay' && (d.reason === 'customer_request' || e.reason === 'customer_request')) return true;
  }
  if (require('./order-amend').ledgerOf(events, at).pending) return true;
  //  جایگزین یا وزنِ بیشتری (S56) که هنوز منتظرِ پاسخِ خودِ مشتری است
  const sub = (await c.query(
    `SELECT 1 FROM order_pick_substitutions WHERE order_id=$1 AND status='pending'
      UNION ALL SELECT 1 FROM order_pick_weight_holds WHERE order_id=$1 AND status='pending' LIMIT 1`, [row.id]
  )).rows[0];
  return Boolean(sub);
}

function newCode() {
  const bytes = crypto.randomBytes(8);
  let s = 'AP';
  for (const b of bytes) s += CODE_ALPHABET[b % CODE_ALPHABET.length];
  return s;
}

/** تصمیمِ یک سفارش — `null` اگر هنوز/دیگر نامزد نیست یا کسِ دیگری زودتر تصمیم گرفته. */
async function record(o, at) {
  return tx(async (c) => {
    const row = (await c.query('SELECT * FROM online_orders WHERE shop_id=$1 AND id=$2 FOR UPDATE', [o.shop_id, o.id])).rows[0];
    if (!row || !LATE_STATUSES.includes(row.status) || row.is_test === true || row.source !== 'storefront' || !row.customer_phone) return null;
    const pol = (await c.query('SELECT * FROM order_late_apology_policy WHERE shop_id=$1', [row.shop_id])).rows[0];
    if (!pol || !pol.enabled) return null;
    const due = dueOf(row);
    if (at < due + Number(pol.late_minutes) * MIN || due <= at - MAX_LATE_MS) return null;
    if ((await c.query('SELECT 1 FROM order_late_apologies WHERE order_id=$1', [row.id])).rows[0]) return null;
    const events = (await c.query('SELECT * FROM online_order_events WHERE order_id=$1 ORDER BY seq', [row.id])).rows;
    const byCustomer = await customerCausedIn(c, row, events, at);
    const cn = require('./customer-notify');
    const kind = byCustomer ? '' : (pol.coupon_kind || '');
    const coupon = kind ? {
      code: newCode(), kind, amountMinor: kind === 'amount' ? Number(pol.coupon_amount_minor) : 0,
      bp: kind === 'percent' ? Number(pol.coupon_bp) : 0, expiresAt: at + Number(pol.coupon_days) * DAY,
    } : null;
    const ins = await c.query(
      `INSERT INTO order_late_apologies (order_id, shop_id, outcome, late_minutes, due_at, phone_key, coupon_code, coupon_kind,
         coupon_amount_minor, coupon_bp, coupon_expires_at, created_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12) ON CONFLICT (order_id) DO NOTHING RETURNING order_id`,
      [row.id, row.shop_id, byCustomer ? 'skipped_customer' : 'apology', Number(pol.late_minutes), due,
        coupon ? cn.phoneKey(row.customer_phone) : '', coupon ? coupon.code : '', kind,
        coupon ? coupon.amountMinor : 0, coupon ? coupon.bp : 0, coupon ? coupon.expiresAt : null, at]
    );
    if (!ins.rows[0]) return null;
    let notifyId = null;
    if (!byCustomer) {
      notifyId = await cn.onExtraIn((t, p) => c.query(t, p), { shopId: row.shop_id, order: row, event: NOTIFY_EVENT, at });
      if (notifyId) await c.query('UPDATE order_late_apologies SET notify_id=$2 WHERE order_id=$1', [row.id, notifyId]);
    }
    const seq = Number((await c.query('SELECT COALESCE(MAX(seq), 0) + 1 AS n FROM online_order_events WHERE order_id=$1', [row.id])).rows[0].n);
    const detail = {
      type: EVENT_TYPE, outcome: byCustomer ? 'skipped_customer' : 'apology', lateMinutes: Number(pol.late_minutes), dueAt: due,
      queued: Boolean(notifyId),
      coupon: coupon ? { kind: coupon.kind, amountMinor: coupon.amountMinor, bp: coupon.bp, expiresAt: coupon.expiresAt } : null,
    };
    await c.query(
      `INSERT INTO online_order_events (id, order_id, shop_id, seq, kind, from_status, to_status,
         actor_type, actor_id, reason, note, detail, created_at)
       VALUES ($1,$2,$3,$4,'note',$5,$5,'system','','','',$6::jsonb,$7)`,
      [ulid(at), row.id, row.shop_id, seq, row.status, JSON.stringify(detail), at]
    );
    return { orderId: row.id, shopId: row.shop_id, orderNo: Number(row.order_no), ...detail };
  });
}

/** یک دور. خروجی: شمارِ تصمیم‌های تازه. */
async function sweep(at = now()) {
  const flags = new Map();
  let n = 0;
  for (const o of await candidates(at)) {
    try {
      if (!flags.has(o.shop_id)) flags.set(o.shop_id, await flagOf(o.shop_id));
      if (!flags.get(o.shop_id)) continue;
      const d = await record(o, at);
      if (!d) continue;
      n++;
      await require('./audit').log({
        shopId: d.shopId, actorType: 'system', action: 'order.late_apology', targetType: 'online_order', targetId: d.orderId,
        detail: { orderNo: d.orderNo, outcome: d.outcome, lateMinutes: d.lateMinutes, coupon: d.coupon ? d.coupon.kind : '' },
      }).catch(() => {});
    } catch (err) {
      console.error('[order-late-apology]', o.id, err && err.message);
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
  timer = setInterval(() => tick().catch((err) => console.error('[order-late-apology]', err.message)), TICK_MS);
  if (timer.unref) timer.unref();
}
function stop() {
  if (timer) clearInterval(timer);
  timer = null;
}

// ════════════════════════════════════════════════════════════════════
//  کوپن — مصرف در ثبتِ ویترین (`storefront-promos.wrap`/`quote`)
// ════════════════════════════════════════════════════════════════════

/** تخفیفِ کوپن روی [baseMinor] (جمعِ اقلام پس از تخفیف‌های دیگر)؛ پلهٔ ۱۰۰، نیمه رو به بالا، هرگز بیش از پایه. */
function discountOf(r, baseMinor) {
  if (!(baseMinor > 0)) return 0;
  const raw = r.coupon_kind === 'amount' ? Number(r.coupon_amount_minor)
    : Math.floor((baseMinor * Number(r.coupon_bp) / 10000 + STEP_MINOR / 2) / STEP_MINOR) * STEP_MINOR;
  return Math.max(0, Math.min(baseMinor, raw));
}

/** وضعیتِ کوپن بی شماره (پیش‌نمایش) — `applied` یا یکی از دلیل‌های ردِ همان راهِ کدِ تخفیف. */
function stateOf(r, at) {
  if (!r || !r.coupon_code) return 'unknown';
  if (r.redeemed_at !== null && r.redeemed_at !== undefined) return 'used_up';
  if (Number(r.coupon_expires_at) <= at || VOID_STATUSES.includes(r.source_status)) return 'inactive';
  return 'applied';
}

const REJECT = {
  unknown: 'این کدِ تخفیف در این ویترین نیست',
  inactive: 'این کدِ تخفیف الان فعال نیست',
  used_up: 'این کدِ تخفیف پیش‌تر به کار رفته است',
  no_effect: 'این کدِ تخفیف روی این سبد اثری ندارد',
};
const reject = (code, status) => Object.assign(conflict(REJECT[status] || REJECT.unknown, 'promo_code_rejected'), { data: { promo: { code, status } } });

const SELECT_COUPON = `SELECT a.*, o.status AS source_status FROM order_late_apologies a
  JOIN online_orders o ON o.id = a.order_id WHERE a.shop_id=$1 AND a.coupon_code=$2`;

/**
 * کدِ واردشده کوپنِ پوزشِ همین دکان است؟ پرچمِ خاموش یا کدِ دیگر ⇒ `null` (همان راهِ کدِ تخفیف، بی تغییر).
 * ⇒ `{ code, apply(run, out, {at, phone, sale}), preview(baseMinor, at) }`
 */
async function couponOf(shopId, code) {
  if (!code || !CODE_RE.test(code)) return null;
  if (!(await flagOf(shopId))) return null;
  if (!(await one('SELECT 1 AS x FROM order_late_apologies WHERE shop_id=$1 AND coupon_code=$2', [String(shopId), code]))) return null;
  return {
    code,
    /** درونِ تراکنشِ ثبت: قفلِ ردیفِ کوپن، شماره، مهلت، یک‌بار ⇒ تخفیف روی `out` و ثبتِ مصرف */
    async apply(run, out, { at, phone, sale }) {
      const r = (await run(`${SELECT_COUPON} FOR UPDATE OF a`, [String(shopId), code])).rows[0];
      let st = stateOf(r, at);
      if (st === 'applied' && r.phone_key !== require('./customer-notify').phoneKey(phone)) st = 'unknown';
      const d = st === 'applied' ? discountOf(r, out.subtotalMinor - out.discountMinor) : 0;
      if (st === 'applied' && d <= 0) st = 'no_effect';
      if (st !== 'applied') throw reject(code, st);
      const u = await run(
        `UPDATE order_late_apologies SET redeemed_sale=$3, redeemed_at=$4
          WHERE shop_id=$1 AND coupon_code=$2 AND redeemed_at IS NULL RETURNING order_id`,
        [String(shopId), code, String(sale), at]
      );
      if (!u.rows[0]) throw reject(code, 'used_up');
      return { ...out, discountMinor: out.discountMinor + d, totalMinor: out.totalMinor - d };
    },
    /** پیش‌نمایش (`/promos/quote`): بی قفل، بی مصرف و بی شماره — شماره در ثبت سنجیده می‌شود */
    async preview(baseMinor, at = now()) {
      const r = await one(SELECT_COUPON, [String(shopId), code]);
      let status = stateOf(r, at);
      const discountMinor = status === 'applied' ? discountOf(r, baseMinor) : 0;
      if (status === 'applied' && discountMinor <= 0) status = 'no_effect';
      return { status, discountMinor: status === 'applied' ? discountMinor : 0 };
    },
  };
}

/** پرچمِ تخفیفِ ویترین خاموش ولی کدِ پوزش آمده: همان `price` با کوپن و سنجشِ «جمعِ دیده‌شده» پس از آن. */
function wrapPrice(price, coupon, { key, input }) {
  return async (run, at) => {
    const expected = input.expectedTotalMinor;
    input.expectedTotalMinor = null;
    let out;
    try { out = await price(run, at); } finally { input.expectedTotalMinor = expected; }
    const res = await coupon.apply(run, out, { at, phone: input.customerPhone, sale: `sf:${key}` });
    if (expected !== null && expected !== res.totalMinor) {
      throw Object.assign(conflict('قیمت‌ها عوض شده است؛ سبد را دوباره ببینید', 'price_changed'), {
        data: { quote: { subtotalMinor: res.subtotalMinor, discountMinor: res.discountMinor, deliveryFeeMinor: res.deliveryFeeMinor, totalMinor: res.totalMinor }, changes: [] },
      });
    }
    return res;
  };
}

// ════════════════════════════════════════════════════════════════════
//  نماها
// ════════════════════════════════════════════════════════════════════

/** پیگیریِ مشتری با کلیدِ `apology` — فقط با پرچم؛ خاموش ⇒ همان شیءِ پیشین. کد فقط برای دارندهٔ توکن. */
async function withApology(shopId, orderId, view) {
  if (!(await flagOf(shopId))) return view;
  const r = await one(`${SELECT_COUPON.replace('a.coupon_code=$2', 'a.order_id=$2')} AND a.outcome = 'apology'`, [String(shopId), String(orderId)]);
  if (!r) return { ...view, apology: null };
  const at = now();
  const st = stateOf(r, at);
  return {
    ...view,
    apology: {
      at: Number(r.created_at),
      coupon: r.coupon_code && !VOID_STATUSES.includes(r.source_status) ? {
        code: r.coupon_code, kind: r.coupon_kind, amountMinor: Number(r.coupon_amount_minor), bp: Number(r.coupon_bp),
        expiresAt: int(r.coupon_expires_at), redeemed: st === 'used_up', usable: st === 'applied',
      } : null,
    },
  };
}

/** فهرستِ تازه‌ترین تصمیم‌ها برای صاحب — بی شماره و بی کدِ کامل (فقط ۴ نویسهٔ آخر). */
async function listOf(shopId) {
  const rows = await many(
    `SELECT a.*, o.order_no FROM order_late_apologies a JOIN online_orders o ON o.id = a.order_id
      WHERE a.shop_id=$1 ORDER BY a.created_at DESC, a.order_id DESC LIMIT ${MAX_LIST}`, [String(shopId)]
  );
  return rows.map((r) => ({
    orderId: r.order_id, orderNo: Number(r.order_no), outcome: r.outcome, lateMinutes: Number(r.late_minutes),
    dueAt: Number(r.due_at), at: Number(r.created_at), queued: r.notify_id !== '',
    coupon: r.coupon_code ? {
      last4: r.coupon_code.slice(-4), kind: r.coupon_kind, amountMinor: Number(r.coupon_amount_minor), bp: Number(r.coupon_bp),
      expiresAt: int(r.coupon_expires_at), redeemedAt: int(r.redeemed_at),
    } : null,
  }));
}

module.exports = {
  FLAG_KEY, EVENT_TYPE, NOTIFY_EVENT, LATE_STATUSES, VOID_STATUSES, MAX_LATE_MS, CODE_RE,
  flagOf, policyOf, parsePolicy, setPolicy, candidates, record, sweep, start, stop,
  discountOf, couponOf, wrapPrice, withApology, listOf,
};
