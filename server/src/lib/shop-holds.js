'use strict';
/**
 * حالت‌های مدیریتیِ دکان — چهار حالتِ جدا و بایگانی (فاز ۳ ِ پنلِ مرکزی، یافتهٔ S14 ِ ممیزی).
 *
 * | حالت                | اثر (همه در سرور)                                                     | نقش (گذاشتن و برداشتن) |
 * |---------------------|-----------------------------------------------------------------------|------------------------|
 * | access_suspended    | هیچ عضوی به دکان نمی‌رسد: ۴۰۳ `shop_suspended`، ویترین و رسید ۴۱۰/۴۰۴  | superadmin، admin       |
 * | service_disabled    | فقط همان سرویس (کلیدِ پرچمش) برای همین دکان خاموش (`orders.flagOf`)    | superadmin، admin       |
 * | subscription_ended  | قابلیت‌های پولی بسته (`entitlementOf`)؛ خواندن و نوشتنِ صاحب باز      | superadmin، finance     |
 * | deletion_requested  | دسترسی بسته تا حذف؛ خودِ حذف فقط `DELETE /api/admin/users/:id` ِ ارشد  | superadmin              |
 * | archived            | دسترسی بسته، دکان «بایگانی»                                           | superadmin، admin       |
 *
 * ⛔ **هیچ‌کدام چیزی پاک نمی‌کند** — نه دفتر، نه اشتراک، نه پرداخت. «پایانِ اشتراک» به ردیفِ اشتراک دست
 *    نمی‌زند؛ برداشتنش اشتراک را همان‌جا می‌گذارد که ساعتِ سرور می‌گوید (روزی اضافه نمی‌شود).
 * ⛔ همه برگشت‌پذیرند (برداشتن). فقط «حذفِ دائمی» برگشت ندارد، و آن کارِ این‌جا نیست.
 * ⛔ دلیل اجباری (۳ تا ۲۰۰ نویسه)؛ ردیفِ سابقه در **همان تراکنش** (شکستش کار را برمی‌گرداند).
 * ⛔ کارِ پول‌مجاور («پایانِ اشتراک») فقط با `Idempotency-Key`؛ بقیه با کلید هم یک‌بار‌اند.
 * ⛔ `shops.status` فقط این‌جا نوشته می‌شود: 'disabled' ⇔ یکی از سه حالتِ بستنِ دسترسی باز است.
 */
const { tx, many, one, newId, now } = require('../db');
const { badRequest, notFound, conflict, forbidden } = require('../middleware/errors');
const idempotency = require('./idempotency');
const audit = require('./audit');

const KINDS = Object.freeze(['access_suspended', 'service_disabled', 'subscription_ended', 'deletion_requested', 'archived']);
/** حالت‌هایی که دسترسیِ اعضا را می‌بندند — به ترتیبِ اولویتِ پیام. */
const ACCESS_KINDS = Object.freeze(['deletion_requested', 'access_suspended', 'archived']);
/** سرویس = کلیدِ پرچمی که همهٔ درهایش از `orders.flagOf` می‌گذرند. */
const SERVICES = Object.freeze({
  'shop.storefront': 'ویترین',
  'shop.orders': 'سفارشِ آنلاین',
  'shop.delivery': 'تحویل و پیک',
  'shop.chat': 'گفت‌وگو با مشتری',
  'market.directory': 'بازار',
});
const ROLES_OF = Object.freeze({
  access_suspended: ['superadmin', 'admin'],
  service_disabled: ['superadmin', 'admin'],
  subscription_ended: ['superadmin', 'finance'],
  deletion_requested: ['superadmin'],
  archived: ['superadmin', 'admin'],
});
/** پول‌مجاور ⇒ کلیدِ یکتایی اجباری. */
const MONEY = new Set(['subscription_ended']);

const BLOCK = Object.freeze({
  access_suspended: {
    code: 'shop_suspended',
    message: 'دسترسی به این دکان از سوی پشتیبانی معلق شده است. داده‌های شما سرِ جایش است؛ با پشتیبانی تماس بگیرید.',
  },
  archived: {
    code: 'shop_archived',
    message: 'این دکان بایگانی شده است. داده‌های شما سرِ جایش است؛ برای بازگشایی با پشتیبانی تماس بگیرید.',
  },
  deletion_requested: {
    code: 'shop_deletion_pending',
    message: 'برای این دکان درخواستِ حذف ثبت شده و دسترسی بسته است. تا پیش از حذف، با پشتیبانی می‌توانید آن را لغو کنید.',
  },
});

const num = (v) => (v === null || v === undefined ? null : Number(v));
const json = (v) => (v && typeof v === 'string' ? JSON.parse(v) : v || null);

function shape(r) {
  return {
    id: r.id, shopId: r.shop_id, kind: r.kind, service: r.service || null, reason: r.reason,
    placedBy: r.placed_by, placedActor: json(r.placed_actor), placedAt: num(r.placed_at),
    liftedAt: num(r.lifted_at), liftedBy: r.lifted_by || null, liftedActor: json(r.lifted_actor),
    liftReason: r.lift_reason || null, active: r.lifted_at === null || r.lifted_at === undefined,
  };
}

/** خلاصهٔ حالِ دکان از ردیف‌های باز. */
function summarize(open) {
  const kinds = new Set(open.map((h) => h.kind));
  const blockedBy = ACCESS_KINDS.filter((k) => kinds.has(k));
  return {
    access: blockedBy.length ? 'blocked' : 'open',
    blockedBy,
    servicesDisabled: open.filter((h) => h.kind === 'service_disabled').map((h) => h.service),
    subscriptionEnded: kinds.has('subscription_ended'),
    deletionRequested: kinds.has('deletion_requested'),
    archived: kinds.has('archived'),
  };
}

/** حالت‌های باز و تاریخچهٔ یک دکان. */
async function listOf(shopId, { limit = 100 } = {}) {
  const rows = await many(
    'SELECT * FROM shop_holds WHERE shop_id=$1 ORDER BY placed_at DESC, id DESC LIMIT $2',
    [shopId, Math.min(Math.max(1, Number(limit) || 100), 500)]
  );
  const all = rows.map(shape);
  const open = all.filter((h) => h.active);
  return { state: summarize(open), open, history: all };
}

async function stateOf(shopId) {
  const open = (await many('SELECT * FROM shop_holds WHERE shop_id=$1 AND lifted_at IS NULL', [shopId])).map(shape);
  return summarize(open);
}

/**
 * چرا این کاربر به دکانش نمی‌رسد — اگر دلیلش یکی از حالت‌های بستنِ دسترسی است. ⇒ {code, message, kind} یا null.
 * فقط در راهِ شکست صدا زده می‌شود (هیچ پرسشی روی راهِ سالم نیست).
 * @param {string|null} shopId دکانِ برگزیدهٔ نشست، اگر هست
 */
async function blockFor(userId, shopId = null) {
  const rows = await many(
    `SELECT h.kind FROM shop_members m
       JOIN shop_holds h ON h.shop_id = m.shop_id AND h.lifted_at IS NULL
      WHERE m.user_id=$1 AND m.status='active' AND h.kind = ANY($2::text[])
        AND ($3::text IS NULL OR m.shop_id = $3::text)`,
    [userId, ACCESS_KINDS, shopId || null]
  );
  const kinds = new Set(rows.map((r) => r.kind));
  const kind = ACCESS_KINDS.find((k) => kinds.has(k));
  return kind ? { kind, ...BLOCK[kind] } : null;
}

function blockError(block) {
  const err = forbidden(block.message, block.code);
  return err;
}

function checkRole(kind, role) {
  if (!(ROLES_OF[kind] || []).includes(role)) {
    throw forbidden('این حالت در حدِ نقشِ شما نیست', 'role_forbidden');
  }
}

function parseKind(body) {
  const kind = String(body?.kind || '').trim();
  if (!KINDS.includes(kind)) throw badRequest('نوعِ حالت درست نیست', 'bad_hold_kind');
  let service = '';
  if (kind === 'service_disabled') {
    service = String(body?.service || '').trim();
    if (!Object.prototype.hasOwnProperty.call(SERVICES, service)) {
      throw badRequest('کدام سرویس؟ (shop.storefront، shop.orders، shop.delivery، shop.chat، market.directory)', 'bad_service');
    }
  }
  return { kind, service };
}

/** دسترسی ⇐ ردیف‌های باز؛ در همان تراکنش. */
async function syncStatus(c, shopId, t) {
  await c.query(
    `UPDATE shops SET status = CASE WHEN EXISTS (
        SELECT 1 FROM shop_holds WHERE shop_id=$1 AND lifted_at IS NULL AND kind = ANY($3::text[])
      ) THEN 'disabled' ELSE 'active' END, updated_at=$2
      WHERE id=$1`,
    [shopId, t, ACCESS_KINDS]
  );
}

async function lockShop(c, shopId) {
  const r = await c.query('SELECT id, name FROM shops WHERE id=$1 FOR UPDATE', [shopId]);
  if (!r.rows.length) throw notFound('دکان پیدا نشد');
  return r.rows[0];
}

async function auditIn(c, { shopId, req, action, hold, reason, ip }) {
  await audit.log({
    shopId, actorType: 'admin', userId: req.admin.id, action, targetType: 'shop_hold', targetId: hold.id, ip,
    detail: { kind: hold.kind, ...(hold.service ? { service: hold.service } : {}), reason, shop_id: shopId },
  }, { client: c });
}

/**
 * گذاشتنِ یک حالت. ⇒ { hold, state, replayed, accessChanged }
 * @param req   درخواستِ مدیر (نقش، انجام‌دهنده، کلیدِ یکتایی)
 */
async function place(shopId, req, { reason, ip = '' }) {
  const { kind, service } = parseKind(req.body);
  checkRole(kind, req.admin.role);
  const idem = idempotency.fromRequest(req, `shop-hold:${shopId}`);
  if (MONEY.has(kind) && !idem) {
    throw badRequest('برای این کار سرآیندِ Idempotency-Key لازم است', 'idempotency_key_required');
  }
  const actor = require('./admin-service').actorRecord(req);
  return tx(async (c) => {
    if (idem) {
      await idempotency.lock(c, idem);
      const prior = await idempotency.find(idem, idempotency.viaClient(c));
      if (prior) return { ...prior.response, replayed: true, accessChanged: false };
    }
    await lockShop(c, shopId);
    const before = (await c.query(
      'SELECT kind FROM shop_holds WHERE shop_id=$1 AND lifted_at IS NULL AND kind = ANY($2::text[])', [shopId, ACCESS_KINDS]
    )).rows.length;
    const dup = await c.query(
      'SELECT * FROM shop_holds WHERE shop_id=$1 AND kind=$2 AND service=$3 AND lifted_at IS NULL', [shopId, kind, service]
    );
    if (dup.rows.length) {
      const err = conflict('این حالت همین حالا روی این دکان هست', 'hold_exists');
      err.hold = shape(dup.rows[0]);
      throw err;
    }
    const t = now();
    const r = await c.query(
      `INSERT INTO shop_holds (id, shop_id, kind, service, reason, placed_by, placed_actor, placed_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7::jsonb,$8) RETURNING *`,
      [newId('shh'), shopId, kind, service, reason, req.admin.id, JSON.stringify(actor), t]
    );
    const hold = shape(r.rows[0]);
    await syncStatus(c, shopId, t);
    await auditIn(c, { shopId, req, action: 'admin.shop_hold_placed', hold, reason, ip });
    const open = (await c.query('SELECT * FROM shop_holds WHERE shop_id=$1 AND lifted_at IS NULL', [shopId])).rows.map(shape);
    const out = { hold, state: summarize(open) };
    if (idem) await idempotency.save(c, idem, { tenantId: shopId, resultId: hold.id, response: out });
    return { ...out, replayed: false, accessChanged: !before && ACCESS_KINDS.includes(kind) };
  });
}

/** برداشتنِ یک حالت (همان دکان — شناسهٔ حالتِ دکانِ دیگر ۴۰۴). */
async function lift(shopId, holdId, req, { reason, ip = '' }) {
  const idem = idempotency.fromRequest(req, `shop-hold-lift:${shopId}`);
  const pre = await one('SELECT kind FROM shop_holds WHERE id=$1 AND shop_id=$2', [holdId, shopId]);
  if (!pre) throw notFound('این حالت روی این دکان پیدا نشد', 'hold_not_found');
  checkRole(pre.kind, req.admin.role);
  if (MONEY.has(pre.kind) && !idem) {
    throw badRequest('برای این کار سرآیندِ Idempotency-Key لازم است', 'idempotency_key_required');
  }
  const actor = require('./admin-service').actorRecord(req);
  return tx(async (c) => {
    if (idem) {
      await idempotency.lock(c, idem);
      const prior = await idempotency.find(idem, idempotency.viaClient(c));
      if (prior) return { ...prior.response, replayed: true };
    }
    await lockShop(c, shopId);
    const cur = await c.query('SELECT * FROM shop_holds WHERE id=$1 AND shop_id=$2', [holdId, shopId]);
    if (!cur.rows.length) throw notFound('این حالت روی این دکان پیدا نشد', 'hold_not_found');
    if (cur.rows[0].lifted_at !== null && cur.rows[0].lifted_at !== undefined) {
      throw conflict('این حالت پیش‌تر برداشته شده است', 'hold_already_lifted');
    }
    const t = now();
    const r = await c.query(
      `UPDATE shop_holds SET lifted_at=$2, lifted_by=$3, lifted_actor=$4::jsonb, lift_reason=$5
        WHERE id=$1 AND lifted_at IS NULL RETURNING *`,
      [holdId, t, req.admin.id, JSON.stringify(actor), reason]
    );
    const hold = shape(r.rows[0]);
    await syncStatus(c, shopId, t);
    await auditIn(c, { shopId, req, action: 'admin.shop_hold_lifted', hold, reason, ip });
    const open = (await c.query('SELECT * FROM shop_holds WHERE shop_id=$1 AND lifted_at IS NULL', [shopId])).rows.map(shape);
    const out = { hold, state: summarize(open) };
    if (idem) await idempotency.save(c, idem, { tenantId: shopId, resultId: hold.id, response: out });
    return { ...out, replayed: false };
  });
}

/** برای `entitlementOf`: زمانِ «پایانِ اشتراک» ِ باز، یا null. */
async function subscriptionEndedAt(shopId) {
  const r = await one(
    `SELECT placed_at FROM shop_holds WHERE shop_id=$1 AND kind='subscription_ended' AND lifted_at IS NULL LIMIT 1`, [shopId]
  );
  return r ? Number(r.placed_at) : null;
}

module.exports = {
  KINDS, ACCESS_KINDS, SERVICES, ROLES_OF, MONEY, BLOCK,
  listOf, stateOf, blockFor, blockError, place, lift, subscriptionEndedAt, summarize, shape,
};
