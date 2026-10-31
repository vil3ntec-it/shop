'use strict';
/**
 * تحویل و پیک — پایهٔ سرور (v3 §۲۲.۲ مرحلهٔ C بندهای ۱ تا ۶؛ DLV، PCK-11).
 *
 *   پرچم ──── `shop.delivery` + `shop.orders` در «تنظیماتِ زنده»؛ نوشتنِ دکان
 *             کلیدهای اشتراکِ `online_orders` و `delivery` را هم می‌خواهد.
 *             ⛔ پیش‌فرض خاموش: بی پرچم هیچ دری باز نیست و هیچ ردیفی ساخته نمی‌شود.
 *   مأموریت ─ چهار روش (§۲۰.۶): پیکِ دکان، شبکهٔ VILL3N (تا فعال نشده رد)،
 *             شرکتِ بیرونی (ثبتِ دستی)، دریافتِ حضوری (با کد).
 *   رویداد ── هر گذار یک ردیف با زمانِ سرور، شناسهٔ دستگاه، زمان و ترتیبِ
 *             خودِ دستگاه؛ کلیدِ یکتاییِ رویداد ⇒ صفِ آفلاین بعداً بی‌خطر
 *             دوباره فرستاده می‌شود. زمانِ دستگاه هرگز مرجعِ تسویه نیست.
 *   سفارش ─── هر اثر فقط از راهِ `orders.transitionIn` / `orders.codIn`، در
 *             همان تراکنش.
 *   پول ───── مبلغِ موردِ انتظار (سرور)، گرفته‌شده، کرایه، تحویل‌داده،
 *             دریافت‌شده، کسری/اضافه — جدا و عددِ صحیح به واحدِ کوچک.
 */
const crypto = require('crypto');
const db = require('../db');
const { one, many, now, tx } = db;
const D = require('./delivery-states');
const codeRule = require('./delivery-code-rule');   //  DLV-08: «نقد یا گران‌قیمت ⇒ کد»، پشتِ پرچمِ خاموش
const orders = require('./orders');
const { ulid } = require('./ulid');
const { can } = require('./permissions');
const { sniff, MAX_BYTES: PHOTO_MAX_BYTES } = require('./product-photos');
const { badRequest, notFound, conflict, ApiError } = require('../middleware/errors');

const FLAG_KEY = 'shop.delivery';
const FEATURE_KEY = 'delivery';

const MAX_FARE_MINOR = 100_000_000;        // یک میلیون افغانی
const MAX_BATCH = 100;
const MAX_SETTLE = 500;
const MAX_PHOTOS = 3;
const DEFAULT_CASH_CAP_MINOR = 1_000_000;  // D-18 (پیش‌نهاد): ۱۰٬۰۰۰ افغانی

const { VIEW_FLAG, LOCATION_FLAG, ON_THE_WAY, LOCATION_TTL_MS, LOCATION_MIN_INTERVAL_MS } = require('./delivery-live');

// ════════════════════════════════════════════════════════════════════
//  پرچم و اشتراک
// ════════════════════════════════════════════════════════════════════

async function stateOf(shopId) {
  const ordersFlag = await orders.flagOf(shopId);
  const flag = await orders.flagOf(shopId, FLAG_KEY);
  const { entitlementOf } = require('./entitlement');
  const ent = await entitlementOf(shopId, now());
  const entitled = ent.features.includes(orders.FEATURE_KEY) && ent.features.includes(FEATURE_KEY);
  return { ordersFlag, flag, readable: ordersFlag && flag, entitled, enabled: ordersFlag && flag && entitled, ent };
}

//  ورودی (`delivery-input`) و کدِ تحویل (`delivery-codes`، PCK-11، DLV-08، DLV-15) — ماژول‌های کنار
const { KEY_RE, ID_RE, MAX_MONEY_MINOR, str, text, int, optInt, eventKey, idOf, n, asJson, hashOf } = require('./delivery-input');
const { CODE_MAX_ATTEMPTS, codeHash, codeFor, codeMatches } = require('./delivery-codes');

// ════════════════════════════════════════════════════════════════════
//  مدلِ خواندنی
// ════════════════════════════════════════════════════════════════════

function staffView(a) {
  return {
    id: a.id,
    orderId: a.order_id,
    attemptNo: Number(a.attempt_no),
    mode: a.mode,
    courierId: a.courier_id,
    externalName: a.external_name,
    externalRef: a.external_ref,
    status: a.status,
    statusReason: a.status_reason,
    live: D.LIVE.includes(a.status),
    expectedCollectMinor: Number(a.expected_collect_minor),
    cashCollectedMinor: n(a.cash_collected_minor),
    fareMinor: Number(a.fare_minor),
    cashReview: a.cash_review === true,
    hasCode: Boolean(a.code_hash),
    codeUsed: a.code_used_at != null,
    codeLocked: Number(a.code_attempts) >= CODE_MAX_ATTEMPTS,
    recall: a.recall_requested_at == null ? null : {
      requestedAt: Number(a.recall_requested_at), reason: a.recall_reason, by: a.recall_by, outcome: a.recall_outcome,
    },
    deliveredAt: n(a.delivered_at),
    settlementId: a.settlement_id,
    version: Number(a.version),
    createdAt: Number(a.created_at),
    updatedAt: Number(a.updated_at),
  };
}

/**
 * کارتِ مأموریتِ پیک (DLV-02) — «اول نشانه»، شمارهٔ پوشیده (۳.۱۱)، بی
 * زمان‌سنج (DLV-20). نشانی فقط تا مأموریت زنده است (§۳۸.۴).
 */
function courierView(r) {
  const live = D.LIVE.includes(r.status);
  const addr = asJson(r.address);
  const pref = asJson(r.delivery);
  return {
    id: r.id,
    orderId: r.order_id,
    orderNo: Number(r.order_no),
    orderStatus: r.order_status,
    status: r.status,
    live,
    //  «اول نشانه» (DLV-02): نشانه، ناحیه و رنگِ دروازه؛ `correctedAt` = دکان پس
    //  از ناموفق نشانی را اصلاح کرد (DLV-09) ⇒ پیک پیش از تلاشِ دوباره می‌بیند
    address: live ? {
      //  ناحیه: آن‌چه مشتری نوشت، وگرنه نامِ ناحیهٔ ارسالِ ویترین (CHK-07)
      landmark: addr.landmark || '', area: addr.area || addr.zone?.name || '', gateColor: addr.gateColor || '',
      text: addr.text || '',
      ...(addr.lat !== undefined ? { lat: addr.lat, lng: addr.lng } : {}),
      ...(Number.isSafeInteger(addr.correctedAt) ? { correctedAt: addr.correctedAt } : {}),
    } : null,
    customer: live ? { name: r.customer_name, phoneMasked: orders.maskPhone(r.customer_phone) } : null,
    delivery: live ? { callBeforeArrival: pref.callBeforeArrival === true, leaveWith: pref.leaveWith || '' } : null,
    itemsCount: Number(r.items_count),
    paymentMethod: r.payment_method,
    expectedCollectMinor: Number(r.expected_collect_minor),
    cashCollectedMinor: n(r.cash_collected_minor),
    fareMinor: Number(r.fare_minor),
    requiresCode: Boolean(r.code_hash) && r.code_used_at == null,
    photoAllowed: pref.photoConsent === true,
    recall: r.recall_requested_at == null ? null : { requestedAt: Number(r.recall_requested_at), reason: r.recall_reason },
    version: Number(r.version),
    updatedAt: Number(r.updated_at),
  };
}

function eventView(e) {
  return {
    seq: Number(e.seq), kind: e.kind, fromStatus: e.from_status, toStatus: e.to_status,
    actorType: e.actor_type, actorId: e.actor_id, deviceId: e.device_id,
    deviceTime: n(e.device_time), deviceSeq: n(e.device_seq), serverTime: Number(e.server_time),
    reason: e.reason, note: e.note, detail: asJson(e.detail),
  };
}

function courierRowView(c, stats = {}) {
  return {
    id: c.id, userId: c.user_id, name: c.name, phone: c.phone, active: c.active === true,
    cashCapMinor: Number(c.cash_cap_minor),
    cashHeldMinor: Number(stats.held || 0),
    liveCount: Number(stats.live || 0),
    liveExpectedMinor: Number(stats.live_expected || 0),
    createdAt: Number(c.created_at), updatedAt: Number(c.updated_at),
  };
}

// ════════════════════════════════════════════════════════════════════
//  پیک‌ها
// ════════════════════════════════════════════════════════════════════

/** نقدِ در دستِ پیک (DLV-11): گرفته و هنوز تسویه‌نشده + موردِ انتظارِ مأموریت‌های زنده. */
async function cashStats(run, shopId, courierId) {
  const r = (await run(
    `SELECT COALESCE(SUM(cash_collected_minor) FILTER (WHERE status='delivered' AND settlement_id=''), 0) AS held,
            COUNT(*) FILTER (WHERE status IN ('requested','assigned','picked_up','moving','arrived','failed'))::int AS live,
            COALESCE(SUM(expected_collect_minor) FILTER (WHERE status IN ('requested','assigned','picked_up','moving','arrived','failed')), 0) AS live_expected
       FROM delivery_assignments WHERE shop_id=$1 AND courier_id=$2`,
    [shopId, courierId]
  )).rows[0];
  return { held: Number(r.held), live: Number(r.live), live_expected: Number(r.live_expected) };
}

const viaDb = (t, p) => db.query(t, p);

async function listCouriers(shopId) {
  const rows = await many('SELECT * FROM shop_couriers WHERE shop_id=$1 ORDER BY created_at, id', [shopId]);
  const out = [];
  for (const c of rows) out.push(courierRowView(c, await cashStats(viaDb, shopId, c.id)));
  return out;
}

async function courierOfUser(shopId, userId) {
  return one('SELECT * FROM shop_couriers WHERE shop_id=$1 AND user_id=$2', [shopId, String(userId || '')]);
}

async function createCourier(shopId, body, actor) {
  const b = body && typeof body === 'object' ? body : {};
  const userId = str(b.userId);
  if (!userId) throw badRequest('عضوِ دکان را انتخاب کنید', 'bad_user');
  const m = await one(
    `SELECT m.user_id, u.name FROM shop_members m JOIN users u ON u.id = m.user_id
      WHERE m.shop_id=$1 AND m.user_id=$2 AND m.status='active'`,
    [shopId, userId]
  );
  //  ⛔ فقط عضوِ همین دکان — شناسهٔ کاربرِ دکانِ دیگر «پیدا نشد» است
  if (!m) throw notFound('این شخص عضوِ این دکان نیست', 'member_not_found');
  const name = text(b.name, 80) || text(m.name, 80) || 'پیک';
  const v = require('./validate');
  const phone = v.phone(b.phone) || '';
  const cap = int(b.cashCapMinor, 'سقفِ نقد', { min: 0, max: MAX_MONEY_MINOR, def: DEFAULT_CASH_CAP_MINOR });
  const at = now();
  const id = ulid(at);
  const r = await db.query(
    `INSERT INTO shop_couriers (id, shop_id, user_id, name, phone, cash_cap_minor, active, created_by, created_at, updated_at)
     VALUES ($1,$2,$3,$4,$5,$6,true,$7,$8,$8) ON CONFLICT (shop_id, user_id) DO NOTHING RETURNING *`,
    [id, shopId, userId, name, phone, cap, actor.userId || '', at]
  );
  if (!r.rows[0]) throw conflict('این عضو پیش‌تر پیک ثبت شده است', 'courier_exists');
  return courierRowView(r.rows[0]);
}

/**
 * عضوِ تازه با نقشِ «پیک» (DLV-01) — درونِ تراکنشِ `staffCodes.redeem`.
 *
 *  • ردیفِ `shop_couriers` اگر نبود ساخته می‌شود (نام و شماره از حساب، سقفِ
 *    نقدِ پیش‌فرضِ D-18). ردیفِ موجود دست نمی‌خورد: اگر صاحب دکان پیک را
 *    غیرفعال کرده، دعوتِ تازه بی‌صدا فعالش نمی‌کند.
 *  • ⛔ همهٔ نشست‌های دکانیِ این حساب باطل می‌شوند: توکنی که پیش از پیک
 *    شدن گرفته شده دامنه ندارد و نباید زنده بماند. نشستِ تازه (ورود با کد
 *    یا پاسخِ `/api/shop/join`) از عضویتِ تازه دامنهٔ `courier` می‌گیرد.
 */
async function enrollCourierMember(c, shopId, userId, createdBy, at) {
  const u = (await c.query('SELECT name, phone FROM users WHERE id=$1', [userId])).rows[0] || {};
  const v = require('./validate');
  let phone = '';
  try { phone = v.phone(u.phone) || ''; } catch { phone = ''; }
  await c.query(
    `INSERT INTO shop_couriers (id, shop_id, user_id, name, phone, cash_cap_minor, active, created_by, created_at, updated_at)
     VALUES ($1,$2,$3,$4,$5,$6,true,$7,$8,$8) ON CONFLICT (shop_id, user_id) DO NOTHING`,
    [ulid(at), shopId, userId, text(u.name, 80) || 'پیک', phone, DEFAULT_CASH_CAP_MINOR, createdBy || '', at]
  );
  await c.query(
    `UPDATE tokens SET revoked_at=$1 WHERE subject_id=$2 AND revoked_at IS NULL
        AND kind IN ('access','refresh') AND COALESCE(app,'shop')='shop'`,
    [at, userId]
  );
}

async function updateCourier(shopId, id, body) {
  const b = body && typeof body === 'object' ? body : {};
  const c = await one('SELECT * FROM shop_couriers WHERE shop_id=$1 AND id=$2', [shopId, idOf(id, 'courier_not_found')]);
  if (!c) throw notFound('پیک پیدا نشد', 'courier_not_found');
  const name = b.name !== undefined ? (text(b.name, 80) || c.name) : c.name;
  const v = require('./validate');
  const phone = b.phone !== undefined ? (v.phone(b.phone) || '') : c.phone;
  const cap = b.cashCapMinor !== undefined ? int(b.cashCapMinor, 'سقفِ نقد') : Number(c.cash_cap_minor);
  if (b.active !== undefined && typeof b.active !== 'boolean') throw badRequest('فعال/غیرفعال نامعتبر است', 'bad_active');
  const active = b.active !== undefined ? b.active : c.active;
  const r = await one(
    `UPDATE shop_couriers SET name=$3, phone=$4, cash_cap_minor=$5, active=$6, updated_at=$7
      WHERE shop_id=$1 AND id=$2 RETURNING *`,
    [shopId, c.id, name, phone, cap, active, now()]
  );
  return courierRowView(r, await cashStats(viaDb, shopId, c.id));
}

// ════════════════════════════════════════════════════════════════════
//  مأموریت: ساخت
// ════════════════════════════════════════════════════════════════════

async function appendEvent(c, a, ev, at) {
  const seq = Number((await c.query(
    'SELECT COALESCE(MAX(seq), 0) + 1 AS n FROM delivery_events WHERE assignment_id=$1', [a.id]
  )).rows[0].n);
  await c.query(
    `INSERT INTO delivery_events (id, shop_id, assignment_id, order_id, seq, event_key, request_hash, kind,
       from_status, to_status, actor_type, actor_id, device_id, device_time, device_seq, server_time,
       reason, note, detail)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$17,$18,$19::jsonb)`,
    [ulid(at), a.shop_id, a.id, a.order_id, seq, ev.key, ev.hash || '', ev.kind,
      ev.from || '', ev.to || '', ev.actorType, ev.actorId || '', ev.deviceId || '',
      ev.deviceTime ?? null, ev.deviceSeq ?? null, at, ev.reason || '', ev.note || '',
      JSON.stringify(ev.detail || {})]
  );
}

/**
 * سفارش را به یک روشِ تحویل می‌سپارد (DLV-03 دستی؛ §۲۰.۶).
 * @returns {{assignment:object, code:string|null, replayed:boolean}}
 */
async function assign(shopId, orderId, body, actor, key = '') {
  const b = body && typeof body === 'object' ? body : {};
  const mode = str(b.mode);
  if (!D.MODES.includes(mode)) throw badRequest('روشِ تحویل معتبر نیست', 'bad_mode');
  //  §۲۰.۶ بندِ ۲: شبکهٔ پیکِ VILL3N هنوز هیچ‌جا فعال نیست ⇒ این گزینه پذیرفته نمی‌شود
  if (mode === 'platform') {
    throw conflict('شبکهٔ پیکِ VILL3N در این محل فعال نیست', 'platform_unavailable');
  }
  const fareMinor = int(b.fareMinor, 'کرایهٔ پیک', { min: 0, max: MAX_FARE_MINOR, def: 0 });
  if (mode === 'pickup' && fareMinor) throw badRequest('دریافتِ حضوری کرایه ندارد', 'bad_fee');
  const courierId = mode === 'shop_courier' ? str(b.courierId) : '';
  if (mode === 'shop_courier' && !ID_RE.test(courierId)) throw badRequest('پیک را انتخاب کنید', 'courier_required');
  const externalName = mode === 'external' ? text(b.externalName, 80) : '';
  if (mode === 'external' && !externalName) throw badRequest('نامِ شرکتِ پیک لازم است', 'external_name_required');
  const externalRef = mode === 'external' ? text(b.externalRef, 80) : '';
  const requireCode = mode === 'pickup' || b.requireCode === true;
  if (b.overrideCashCap !== undefined && typeof b.overrideCashCap !== 'boolean') throw badRequest('نامعتبر', 'bad_override');
  const reqHash = hashOf({ orderId: String(orderId || ''), mode, fareMinor, courierId, externalName, externalRef, requireCode });
  const codePolicy = await codeRule.policyOf(shopId);
  const rebuildable = (requireCode || codePolicy.on) && await orders.flagOf(shopId, VIEW_FLAG);

  return tx(async (c) => {
    const order = (await c.query(
      'SELECT * FROM online_orders WHERE shop_id=$1 AND id=$2 FOR UPDATE', [shopId, String(orderId || '')]
    )).rows[0];
    if (!order) throw notFound('سفارش پیدا نشد', 'order_not_found');

    if (key) {
      const prev = (await c.query(
        'SELECT assignment_id, request_hash FROM delivery_events WHERE shop_id=$1 AND event_key=$2', [shopId, key]
      )).rows[0];
      if (prev) {
        if (prev.request_hash !== reqHash) throw conflict('این کلیدِ یکتایی پیش‌تر برای کارِ دیگری به کار رفته است', 'idempotency_key_reused');
        const a = (await c.query('SELECT * FROM delivery_assignments WHERE id=$1', [prev.assignment_id])).rows[0];
        return { assignment: staffView(a), code: null, replayed: true };
      }
    }

    if ((mode === 'pickup') !== (order.fulfillment === 'pickup')) {
      throw conflict('روشِ تحویل با نوعِ سفارش جور نیست', 'fulfillment_mismatch');
    }
    const okStatus = mode === 'pickup'
      ? ['accepted', 'picking', 'packed', 'ready_for_pickup']
      : ['accepted', 'picking', 'packed'];
    if (!okStatus.includes(order.status)) throw conflict('این سفارش در وضعی نیست که بشود تحویلش را سپرد', 'order_not_assignable');

    const live = (await c.query(
      `SELECT id FROM delivery_assignments WHERE order_id=$1
          AND status IN ('requested','assigned','picked_up','moving','arrived','failed')`, [order.id]
    )).rows[0];
    if (live) throw conflict('این سفارش همین حالا مأموریتِ زنده دارد', 'already_assigned');

    const expected = order.payment_method === 'cod' && order.payment_status !== 'paid' ? Number(order.total_minor) : 0;
    const codeBy = codeRule.requires(codePolicy, { mode, totalMinor: Number(order.total_minor), expectedCollectMinor: expected });   //  DLV-08؛ خاموش ⇒ ''

    let overCap = false;
    if (mode === 'shop_courier') {
      const cr = (await c.query(
        'SELECT * FROM shop_couriers WHERE shop_id=$1 AND id=$2 FOR UPDATE', [shopId, courierId]
      )).rows[0];
      if (!cr) throw notFound('پیک پیدا نشد', 'courier_not_found');
      if (!cr.active) throw conflict('این پیک غیرفعال است', 'courier_inactive');
      const st = await cashStats((t, p) => c.query(t, p), shopId, courierId);
      overCap = st.held + st.live_expected + expected > Number(cr.cash_cap_minor);
      //  DLV-11 / S40: بالای سقفِ نقد کار به پیک نمی‌رود، مگر مدیر آگاهانه بپذیرد
      if (overCap && b.overrideCashCap !== true) {
        throw conflict('نقدِ در دستِ این پیک از سقفش بیشتر می‌شود؛ اول تسویه کنید', 'cash_cap_exceeded');
      }
    }

    const at = now();
    const id = ulid(at);
    const attempt = Number((await c.query(
      'SELECT COALESCE(MAX(attempt_no), 0) + 1 AS n FROM delivery_assignments WHERE order_id=$1', [order.id]
    )).rows[0].n);
    const { code, nonce } = requireCode || codeBy ? codeFor(id, rebuildable) : { code: null, nonce: '' };
    const status = mode === 'shop_courier' ? 'requested' : 'assigned';
    await c.query(
      `INSERT INTO delivery_assignments (id, shop_id, order_id, attempt_no, mode, courier_id, external_name,
         external_ref, status, expected_collect_minor, fare_minor, code_hash, code_nonce, created_by, created_at, updated_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$15)`,
      [id, shopId, order.id, attempt, mode, courierId, externalName, externalRef, status, expected, fareMinor,
        code ? codeHash(id, code) : '', nonce, actor.userId || '', at]
    );
    const a = (await c.query('SELECT * FROM delivery_assignments WHERE id=$1', [id])).rows[0];
    await appendEvent(c, a, {
      key: key || `srv:${id}:created`, hash: reqHash, kind: 'created', to: status,
      actorType: 'user', actorId: actor.userId, deviceId: actor.deviceId,
      detail: { mode, courierId, externalName, fareMinor, expectedCollectMinor: expected, requireCode: requireCode || Boolean(codeBy),
        ...(codeBy ? { codeRule: codeBy } : {}),
        ...(overCap ? { overCashCap: true } : {}) },
    }, at);
    return { assignment: staffView(a), code, replayed: false };
  });
}

/** کدِ تازه (کدِ قبلی باطل، شمارِ تلاش صفر) — کد فقط همین یک بار دیده می‌شود. */
async function regenerateCode(shopId, assignmentId, actor) {
  const rebuildable = await orders.flagOf(shopId, VIEW_FLAG);
  return tx(async (c) => {
    const a = (await c.query(
      'SELECT * FROM delivery_assignments WHERE shop_id=$1 AND id=$2 FOR UPDATE', [shopId, String(assignmentId || '')]
    )).rows[0];
    if (!a) throw notFound('مأموریت پیدا نشد', 'assignment_not_found');
    if (!D.LIVE.includes(a.status) || a.status === 'failed') throw conflict('این مأموریت کدِ تازه نمی‌پذیرد', 'not_live');
    const { code, nonce } = codeFor(a.id, rebuildable);
    const at = now();
    await c.query(
      `UPDATE delivery_assignments SET code_hash=$3, code_nonce=$5, code_attempts=0, code_used_at=NULL,
              version=version+1, updated_at=$4
        WHERE shop_id=$1 AND id=$2`,
      [shopId, a.id, codeHash(a.id, code), at, nonce]
    );
    await appendEvent(c, a, {
      key: `srv:${ulid(at)}:code`, kind: 'code', from: a.status, to: a.status,
      actorType: 'user', actorId: actor.userId, deviceId: actor.deviceId, detail: { regenerated: true },
    }, at);
    return { code };
  });
}

// ════════════════════════════════════════════════════════════════════
//  مأموریت: رویداد (پیک یا کارمند) — هستهٔ یگانه
// ════════════════════════════════════════════════════════════════════

const REJECT_STATUS = {
  invalid_transition: 409, not_your_mission: 404, staff_only: 403, courier_only: 403, courier_inactive: 403,
  permission_denied: 403, reason_required: 400, bad_reason: 400, note_required: 400,
  assignment_not_found: 404, event_key_reused: 409, bad_code: 422, code_required: 422, code_locked: 423,
  cash_required: 400, cash_reason_required: 400, bad_cash: 400,
  order_not_ready: 409, order_state_conflict: 409, status_changed: 409,
};
const REJECT_MESSAGE = {
  invalid_transition: 'این تغییرِ وضعیت مجاز نیست',
  not_your_mission: 'مأموریت پیدا نشد',
  staff_only: 'این کار با دکان است، نه پیک',
  courier_only: 'این کار را پیک ثبت می‌کند',
  courier_inactive: 'این پیک غیرفعال است',
  permission_denied: 'این کار در حدِ دسترسیِ شما نیست',
  reason_required: 'دلیل لازم است',
  bad_reason: 'دلیل معتبر نیست',
  note_required: 'توضیح لازم است',
  assignment_not_found: 'مأموریت پیدا نشد',
  event_key_reused: 'این شناسهٔ رویداد پیش‌تر برای رویدادِ دیگری به کار رفته است',
  bad_code: 'کدِ تحویل درست نیست',
  code_required: 'کدِ تحویلِ مشتری لازم است',
  code_locked: 'کدِ تحویل پس از چند تلاشِ نادرست قفل شد؛ مدیر باید تأیید کند',
  cash_required: 'مبلغِ گرفته‌شده لازم است',
  cash_reason_required: 'مبلغِ گرفته‌شده با مبلغِ سفارش فرق دارد؛ دلیل لازم است',
  bad_cash: 'مبلغِ گرفته‌شده معتبر نیست',
  order_not_ready: 'سفارش هنوز آمادهٔ این کار نیست',
  order_state_conflict: 'وضعیتِ سفارش با این رویداد جور نیست',
  status_changed: 'وضعیتِ مأموریت همین حالا عوض شده است',
};

function rejection(code, extra = {}) {
  return { ok: false, status: 'rejected', code, http: REJECT_STATUS[code] || 409, message: REJECT_MESSAGE[code] || 'مجاز نیست', ...extra };
}

/** ورودیِ یک رویداد — از پیک (صف) یا کارمند. خطای شکل ⇒ رد، نه استثنا. */
function parseEvent(raw, { requireKey = true } = {}) {
  const e = raw && typeof raw === 'object' ? raw : {};
  const key = eventKey(e.eventId, { required: requireKey });
  const to = str(e.type || e.to);
  if (!D.STATUSES.includes(to)) throw badRequest('نوعِ رویداد معتبر نیست', 'bad_event_type');
  const reason = str(e.reason);
  if (reason && !/^[a-z_]{1,40}$/.test(reason)) throw badRequest('دلیل معتبر نیست', 'bad_reason');
  //  CAS اختیاری (§۲۸.۵): «از این وضعیت» — اگر عوض شده باشد رد می‌شود
  const from = str(e.from);
  if (from && !D.STATUSES.includes(from)) throw badRequest('وضعیتِ مبدأ معتبر نیست', 'bad_status');
  const cashReason = str(e.cashReason);
  if (cashReason && !D.REASONS.cash.includes(cashReason)) throw badRequest('دلیلِ اختلافِ نقد معتبر نیست', 'bad_reason');
  return {
    key,
    assignmentId: str(e.assignmentId),
    to,
    from,
    reason,
    note: text(e.note, 300),
    cashCollectedMinor: optInt(e.cashCollectedMinor, 'مبلغِ گرفته‌شده'),
    cashReason,
    code: e.code === undefined || e.code === null ? '' : String(e.code).slice(0, 12),
    deviceTime: optInt(e.deviceTime, 'زمانِ دستگاه', { min: 0, max: 4102444800000 }),
    deviceSeq: optInt(e.deviceSeq, 'ترتیبِ دستگاه', { min: 0, max: Number.MAX_SAFE_INTEGER }),
  };
}

/** اثرِ انگشتِ رویداد — بی کد (کد هرگز، حتی هش‌شده، نگه داشته نمی‌شود). */
function eventHash(ev) {
  const { code, key, ...rest } = ev;
  return hashOf(rest);
}

/**
 * یک رویداد را درونِ تراکنشِ `c` اعمال می‌کند. ردِ کسب‌وکاری استثنا نیست
 * (شمارِ تلاشِ کدِ نادرست باید بماند)؛ فقط خطای غیرمنتظره پرتاب می‌شود.
 * @param {{kind:'courier'|'staff', userId:string, role:string, courierId?:string, active?:boolean,
 *          canAssign?:boolean, canHandover?:boolean, deviceId?:string}} actor
 */
async function applyEvent(c, shopId, ev, actor) {
  const a = (await c.query(
    'SELECT * FROM delivery_assignments WHERE shop_id=$1 AND id=$2 FOR UPDATE', [shopId, ev.assignmentId]
  )).rows[0];
  //  ⛔ پیک مأموریتِ دیگری را «پیدا نشد» می‌بیند — نه «مالِ تو نیست»
  if (!a || (actor.kind === 'courier' && a.courier_id !== actor.courierId)) return rejection('assignment_not_found');

  const hash = eventHash(ev);
  if (ev.key) {
    const prev = (await c.query(
      'SELECT assignment_id, request_hash FROM delivery_events WHERE shop_id=$1 AND event_key=$2', [shopId, ev.key]
    )).rows[0];
    if (prev) {
      if (prev.assignment_id !== a.id || prev.request_hash !== hash) return rejection('event_key_reused');
      return { ok: true, status: 'duplicate', assignment: a };
    }
  }
  if (a.status === ev.to) return { ok: true, status: 'unchanged', assignment: a };
  if (ev.from && ev.from !== a.status) return rejection('status_changed', { current: a.status });

  const verdict = D.check(a, ev.to, { actor, reason: ev.reason, note: ev.note });
  if (!verdict.ok) return rejection(verdict.code, { current: a.status });

  const detail = {};
  let collected = null;
  let cashReview = a.cash_review === true;
  let codeUsed = false;

  if (ev.to === 'delivered') {
    //  ── کدِ تحویل ──
    if (a.code_hash && a.code_used_at == null) {
      if (!ev.code) {
        //  جایگزینِ مشتریِ بی‌گوشی (§۲۰.۶): تأییدِ دستیِ مدیر با توضیح، در سابقه
        if (actor.kind === 'staff' && actor.canAssign && ev.note) detail.manualConfirm = true;
        else return rejection('code_required', { current: a.status });
      } else {
        if (Number(a.code_attempts) >= CODE_MAX_ATTEMPTS) return rejection('code_locked', { current: a.status });
        if (!codeMatches(a, ev.code)) {
          await c.query('UPDATE delivery_assignments SET code_attempts = code_attempts + 1 WHERE id=$1', [a.id]);
          return rejection('bad_code', { current: a.status });
        }
        codeUsed = true;
        detail.codeVerified = true;
      }
    }
    //  ── نقد ── مبلغِ موردِ انتظار را سرور گذاشته؛ پیک فقط «گرفتم» را می‌گوید
    const expected = Number(a.expected_collect_minor);
    collected = ev.cashCollectedMinor;
    if (collected === null) {
      if (expected > 0) return rejection('cash_required', { current: a.status });
      collected = 0;
    }
    if (collected !== expected) {
      if (!ev.cashReason) return rejection('cash_reason_required', { current: a.status });
      if (ev.cashReason === 'other' && !ev.note) return rejection('note_required', { current: a.status });
      cashReview = true;
    }
    detail.expectedCollectMinor = expected;
    detail.cashCollectedMinor = collected;
    if (ev.cashReason) detail.cashReason = ev.cashReason;
  }

  //  ── اثر بر سفارش: فقط از راهِ ماشینِ وضعیتِ سفارش ──
  const order = (await c.query('SELECT * FROM online_orders WHERE shop_id=$1 AND id=$2', [shopId, a.order_id])).rows[0];
  //  «برگشت» دلیلِ سفارشش همیشه «فراخوانده/برگشت» است، نه متنِ آزادِ کارمند
  const plan = D.orderSteps(a.status, ev.to, order.status,
    { mode: a.mode, reason: ev.to === 'returned' ? '' : ev.reason, note: ev.note });
  if (plan.error) return rejection(plan.error, { current: a.status, orderStatus: order.status });
  const orderActor = { type: 'user', id: actor.userId || '', role: actor.role || '' };
  for (const step of plan.steps) {
    await orders.transitionIn(c, shopId, a.order_id, step, orderActor);
  }
  if (plan.steps.length) detail.order = plan.steps.map((s) => s.to);
  if (ev.to === 'delivered' && order.payment_method === 'cod' && order.cod_status === 'to_collect' && collected > 0) {
    const codTo = a.mode === 'pickup' ? 'collected_in_store' : 'collected_by_courier';
    await orders.codIn(c, shopId, a.order_id, codTo, orderActor,
      { assignmentId: a.id, collectedMinor: collected, expectedMinor: Number(a.expected_collect_minor) });
    detail.cod = codTo;
  }

  //  ── لغوِ پس از حرکت: نتیجه همان است که واقعاً شد ──
  let recallOutcome = a.recall_outcome;
  if (a.recall_outcome === 'pending' && ev.to === 'delivered') recallOutcome = 'too_late';
  if (a.recall_outcome === 'pending' && ev.to === 'returned') recallOutcome = 'returned';
  if (recallOutcome !== a.recall_outcome) detail.recallOutcome = recallOutcome;

  const at = now();
  const reason = ev.reason || (ev.to === 'returned' && a.recall_outcome === 'pending' ? 'recalled' : '');
  await c.query(
    `UPDATE delivery_assignments
        SET status=$3, status_reason=$4,
            cash_collected_minor = CASE WHEN $3 = 'delivered' THEN $5::bigint ELSE cash_collected_minor END,
            cash_review=$6::boolean, delivered_at = CASE WHEN $3 = 'delivered' THEN $7::bigint ELSE delivered_at END,
            code_used_at = CASE WHEN $8::boolean THEN $7::bigint ELSE code_used_at END,
            --  S5: کدِ مصرف‌شده یا مأموریتِ پایان‌یافته دیگر بازسازی نمی‌شود
            code_nonce = CASE WHEN $8::boolean OR NOT ($3 = ANY($10::text[])) THEN '' ELSE code_nonce END,
            eta_at = CASE WHEN $3 = ANY($11::text[]) THEN eta_at ELSE NULL END,
            recall_outcome=$9, version=version+1, updated_at=$7
      WHERE shop_id=$1 AND id=$2`,
    [shopId, a.id, ev.to, reason, collected, cashReview, at, codeUsed, recallOutcome, D.LIVE, ON_THE_WAY]
  );
  //  S5: موقعیت فقط «در راه» می‌ماند — هر گذارِ دیگر (رسیدم، تحویل، …) همین‌جا پاکش می‌کند
  if (!ON_THE_WAY.includes(ev.to)) await c.query('DELETE FROM delivery_locations WHERE assignment_id=$1', [a.id]);
  await appendEvent(c, a, {
    key: ev.key || `srv:${ulid(at)}:${ev.to}`, hash, kind: 'status', from: a.status, to: ev.to,
    actorType: actor.kind === 'courier' ? 'courier' : 'user', actorId: actor.userId,
    deviceId: actor.deviceId, deviceTime: ev.deviceTime, deviceSeq: ev.deviceSeq,
    reason, note: ev.note, detail,
  }, at);
  const fresh = (await c.query('SELECT * FROM delivery_assignments WHERE id=$1', [a.id])).rows[0];
  return { ok: true, status: 'applied', assignment: fresh };
}

/**
 * صفِ رویدادهای پیک (DLV-10) — آنلاین یا پس از آفلاین. هر رویداد تراکنشِ
 * خودش را دارد؛ ترتیب با `deviceSeq` (و جای آن در فهرست). تکرار ⇒ «duplicate».
 */
async function courierEvents(shopId, body, actor) {
  const b = body && typeof body === 'object' ? body : {};
  if (!Array.isArray(b.events) || b.events.length === 0) throw badRequest('رویدادی نیامد', 'bad_events');
  if (b.events.length > MAX_BATCH) throw badRequest(`حداکثر ${MAX_BATCH} رویداد در هر بار`, 'bad_events');
  const deviceId = text(actor.deviceId || b.deviceId, 80);
  const parsed = b.events.map((raw, i) => {
    try {
      return { i, ev: parseEvent(raw) };
    } catch (err) {
      return { i, err: { ok: false, status: 'rejected', code: err.code || 'bad_event', message: err.message } };
    }
  });
  const order = [...parsed].sort((x, y) => {
    const xs = x.ev?.deviceSeq; const ys = y.ev?.deviceSeq;
    if (xs != null && ys != null && xs !== ys) return xs - ys;
    return x.i - y.i;
  });
  const results = new Array(parsed.length);
  for (const p of order) {
    const eventId = str(b.events[p.i]?.eventId);
    if (p.err) { results[p.i] = { eventId, ...p.err }; continue; }
    let out;
    try {
      out = await tx((c) => applyEvent(c, shopId, p.ev, { ...actor, kind: 'courier', deviceId }));
    } catch (err) {
      //  ردِ ماشینِ سفارش (۴xx) فقط همین رویداد را رد می‌کند، نه کلِ صف را
      if (!err || !err.expose || (err.status || 500) >= 500) throw err;
      results[p.i] = { eventId, status: 'rejected', code: err.code, message: err.message };
      continue;
    }
    results[p.i] = out.ok
      ? { eventId, status: out.status, assignment: { id: out.assignment.id, status: out.assignment.status, version: Number(out.assignment.version) } }
      : { eventId, status: 'rejected', code: out.code, message: out.message, ...(out.current ? { current: out.current } : {}) };
  }
  return { results, serverTime: now() };
}

/** رویدادِ کارمند (شرکتِ بیرونی، دریافتِ حضوری، برگشت، پس‌گرفتن، تأییدِ دستی). */
async function staffEvent(shopId, assignmentId, body, actor, key = '') {
  const ev = parseEvent({ ...(body || {}), eventId: key || undefined, assignmentId }, { requireKey: false });
  const out = await tx((c) => applyEvent(c, shopId, ev, { ...actor, kind: 'staff' }));
  if (!out.ok) {
    const err = new ApiError(out.http, out.code, out.message);
    err.current = out.current;
    throw err;
  }
  return { assignment: staffView(out.assignment), status: out.status };
}

// ════════════════════════════════════════════════════════════════════
//  لغو پس از حرکتِ پیک (۲۲.۲ C بندِ ۶)
// ════════════════════════════════════════════════════════════════════

/**
 * دکان می‌خواهد بسته برگردد. ⛔ چیزی را «متوقف» وانمود نمی‌کند: بسته‌ای که
 * تحویل یا برگردانده شده رد می‌شود؛ پیش از حرکت باید «پس‌گرفتن» زد. پس از
 * درخواست، نتیجه همان است که واقعاً می‌شود: پیک برمی‌گرداند (returned) یا
 * پیش از رسیدنِ خبر تحویل داده بود (too_late).
 */
async function recall(shopId, assignmentId, body, actor, key = '') {
  const b = body && typeof body === 'object' ? body : {};
  const reason = str(b.reason);
  if (!D.REASONS.recall.includes(reason)) throw badRequest('دلیلِ لغو لازم است', reason ? 'bad_reason' : 'reason_required');
  const note = text(b.note, 300);
  if (reason === 'other' && !note) throw badRequest('توضیح لازم است', 'note_required');
  const reqHash = hashOf({ assignmentId: String(assignmentId || ''), reason, note });

  return tx(async (c) => {
    const a = (await c.query(
      'SELECT * FROM delivery_assignments WHERE shop_id=$1 AND id=$2 FOR UPDATE', [shopId, String(assignmentId || '')]
    )).rows[0];
    if (!a) throw notFound('مأموریت پیدا نشد', 'assignment_not_found');
    if (key) {
      const prev = (await c.query(
        'SELECT request_hash FROM delivery_events WHERE shop_id=$1 AND event_key=$2', [shopId, key]
      )).rows[0];
      if (prev) {
        if (prev.request_hash !== reqHash) throw conflict('این کلیدِ یکتایی پیش‌تر به کار رفته است', 'idempotency_key_reused');
        return { assignment: staffView(a), unchanged: true };
      }
    }
    if (a.status === 'delivered') {
      throw conflict('بسته تحویلِ مشتری شده است؛ لغو ممکن نیست — درخواستِ برگشتِ کالا را ثبت کنید', 'already_delivered');
    }
    if (a.status === 'returned') throw conflict('بسته پیش‌تر به دکان برگشته است', 'already_returned');
    if (a.status === 'requested' || a.status === 'assigned') {
      throw conflict('پیک هنوز حرکت نکرده است؛ مأموریت را پس بگیرید', 'not_departed');
    }
    if (!D.DEPARTED.includes(a.status)) throw conflict('این مأموریت زنده نیست', 'not_live');
    if (a.recall_outcome === 'pending') return { assignment: staffView(a), unchanged: true };

    const at = now();
    await c.query(
      `UPDATE delivery_assignments SET recall_requested_at=$3, recall_reason=$4, recall_by=$5,
              recall_outcome='pending', version=version+1, updated_at=$3
        WHERE shop_id=$1 AND id=$2`,
      [shopId, a.id, at, reason, actor.userId || '']
    );
    await appendEvent(c, a, {
      key: key || `srv:${ulid(at)}:recall`, hash: reqHash, kind: 'recall', from: a.status, to: a.status,
      actorType: 'user', actorId: actor.userId, deviceId: actor.deviceId, reason, note,
    }, at);
    const fresh = (await c.query('SELECT * FROM delivery_assignments WHERE id=$1', [a.id])).rows[0];
    return { assignment: staffView(fresh), unchanged: false };
  });
}

// ════════════════════════════════════════════════════════════════════
//  اصلاحِ نشانی (DLV-09: کارِ بعدی پس از ناموفق)
// ════════════════════════════════════════════════════════════════════

/** کلیدهای نشانی که در گزارش می‌آیند — فقط نامِ کلید، هرگز خودِ نشانی. */
const ADDRESS_KEYS = Object.freeze(['landmark', 'area', 'gateColor', 'text', 'pin']);

function addressFields(prev, next) {
  const pick = (x, k) => (k === 'pin' ? (x.lat === undefined ? '' : `${x.lat},${x.lng}`) : String(x[k] || ''));
  return ADDRESS_KEYS.filter((k) => pick(prev, k) !== pick(next, k));
}

/**
 * دکان نشانیِ سفارشِ یک مأموریتِ زندهٔ ارسال را درست می‌کند (پیک «پیدا نشد» زده یا
 * مشتری زنگ زد) تا پیک با نشانیِ درست دوباره برود. کارتِ پیک همین را از سفارش
 * می‌خواند و `correctedAt` را می‌بیند.
 *
 * بدنه: `{address, keepPin?, expectedVersion?}` — `address` همان `orders.parseAddress`.
 * ⛔ فقط ارسال (shop_courier / external)، فقط مأموریتِ زنده؛ دریافتِ حضوری نشانی ندارد.
 * ⛔ `expectedVersion` (اختیاری) = نسخهٔ مأموریتی که کارمند دید: اگر میانِ کار عوض
 *    شده باشد ۴۰۹ `assignment_changed` با وضعِ تازه، نه نوشتن روی وضعِ دیگر.
 * ⛔ کلیدِ یکتایی (`idem`، سرآیندِ Idempotency-Key): پاسخِ گم‌شده با همان کلید همان
 *    پاسخ است و نسخه دوباره بالا نمی‌رود.
 * قفل‌ها به همان ترتیبِ `applyEvent`: اول مأموریت، بعد سفارش.
 */
async function fixAddress(shopId, assignmentId, body, idem = null) {
  const b = body && typeof body === 'object' ? body : {};
  const address = orders.parseAddress(b.address);
  if (b.keepPin !== undefined && typeof b.keepPin !== 'boolean') throw badRequest('نامعتبر', 'bad_keep_pin');
  const expectedVersion = optInt(b.expectedVersion, 'نسخه', { min: 0, max: Number.MAX_SAFE_INTEGER });
  const idempotency = require('./idempotency');

  return tx(async (c) => {
    if (idem) {
      await idempotency.lock(c, idem);
      const prev = await idempotency.find(idem, idempotency.viaClient(c));
      if (prev) return { ...prev.response, replayed: true };
    }
    const a = (await c.query(
      'SELECT * FROM delivery_assignments WHERE shop_id=$1 AND id=$2 FOR UPDATE', [shopId, String(assignmentId || '')]
    )).rows[0];
    if (!a) throw notFound('مأموریت پیدا نشد', 'assignment_not_found');
    if (a.mode === 'pickup') throw conflict('دریافتِ حضوری نشانی ندارد', 'not_delivery');
    if (!D.LIVE.includes(a.status)) throw conflict('این مأموریت تمام شده است', 'not_live');
    if (expectedVersion !== null && Number(a.version) !== expectedVersion) {
      const err = conflict('این مأموریت همین حالا جای دیگری عوض شد', 'assignment_changed');
      err.current = staffView(a);
      throw err;
    }
    const order = (await c.query(
      'SELECT id, address FROM online_orders WHERE shop_id=$1 AND id=$2 FOR UPDATE', [shopId, a.order_id]
    )).rows[0];
    if (!order) throw notFound('سفارش پیدا نشد', 'order_not_found');

    const at = now();
    const prev = asJson(order.address);
    //  `keepPin`: میز نقشه ندارد؛ نشانه را درست می‌کند و پینِ درستِ مشتری می‌ماند.
    //  بی آن (یا با پینِ تازه) نشانیِ آمده جای کلِ نشانی را می‌گیرد.
    const kept = b.keepPin === true && address.lat === undefined && prev.lat !== undefined && prev.lat !== null
      ? { lat: prev.lat, lng: prev.lng } : {};
    const fixed = { ...kept, ...address };
    const fields = addressFields(prev, fixed);
    //  ناحیهٔ ارسالِ ویترین (`zone`، CHK-07) سندِ قیمتِ همان سفارش است و با اصلاحِ
    //  نشانی عوض نمی‌شود — هزینهٔ ارسالِ گرفته‌شده پس از رخداد بازحساب نمی‌شود
    const next = { ...fixed, ...(prev.zone && typeof prev.zone === 'object' ? { zone: prev.zone } : {}), correctedAt: at };
    await c.query(
      `UPDATE online_orders SET address=$3::jsonb, version=version+1, updated_at=$4 WHERE shop_id=$1 AND id=$2`,
      [shopId, order.id, JSON.stringify(next), at]
    );
    await c.query(
      'UPDATE delivery_assignments SET version=version+1, updated_at=$3 WHERE shop_id=$1 AND id=$2', [shopId, a.id, at]
    );
    const fresh = (await c.query('SELECT * FROM delivery_assignments WHERE id=$1', [a.id])).rows[0];
    //  پاسخ (و نسخهٔ نگه‌داشتهٔ کلیدِ یکتایی) خودِ نشانی را ندارد — یک رونوشتِ کمتر از دادهٔ مشتری
    const out = { assignment: staffView(fresh), correctedAt: at, fields };
    if (idem) await idempotency.save(c, idem, { tenantId: shopId, resultId: a.id, response: out });
    return { ...out, replayed: false };
  });
}

// ════════════════════════════════════════════════════════════════════
//  خواندن
// ════════════════════════════════════════════════════════════════════

const MISSION_SELECT = `
  SELECT a.*, o.order_no, o.status AS order_status, o.customer_name, o.customer_phone, o.address,
         o.delivery, o.items_count, o.payment_method
    FROM delivery_assignments a JOIN online_orders o ON o.id = a.order_id`;

async function missions(shopId, courierId, { scope = 'active', limit } = {}) {
  if (!['active', 'done'].includes(scope)) throw badRequest('برگه معتبر نیست', 'bad_scope');
  const lim = Math.min(100, Math.max(1, Number.parseInt(limit, 10) || 50));
  const live = `a.status IN ('requested','assigned','picked_up','moving','arrived','failed')`;
  const rows = await many(
    `${MISSION_SELECT} WHERE a.shop_id=$1 AND a.courier_id=$2 AND ${scope === 'active' ? live : `NOT ${live}`}
      ORDER BY ${scope === 'active' ? 'a.created_at ASC' : 'a.updated_at DESC'}, a.id LIMIT $3`,
    [shopId, courierId, lim]
  );
  return { scope, items: rows.map(courierView), serverTime: now() };
}

async function forOrder(shopId, orderId) {
  const order = await one(
    `SELECT id, order_no, status, fulfillment, payment_method, payment_status, cod_status, total_minor, delivery
       FROM online_orders WHERE shop_id=$1 AND id=$2`,
    [shopId, String(orderId || '')]
  );
  if (!order) throw notFound('سفارش پیدا نشد', 'order_not_found');
  const rows = await many('SELECT * FROM delivery_assignments WHERE shop_id=$1 AND order_id=$2 ORDER BY attempt_no', [shopId, order.id]);
  const events = await many('SELECT * FROM delivery_events WHERE shop_id=$1 AND order_id=$2 ORDER BY server_time, seq', [shopId, order.id]);
  const byA = new Map();
  for (const e of events) {
    if (!byA.has(e.assignment_id)) byA.set(e.assignment_id, []);
    byA.get(e.assignment_id).push(eventView(e));
  }
  return {
    order: {
      id: order.id, orderNo: Number(order.order_no), status: order.status, fulfillment: order.fulfillment,
      paymentMethod: order.payment_method, paymentStatus: order.payment_status, codStatus: order.cod_status,
      totalMinor: Number(order.total_minor), photoConsent: asJson(order.delivery).photoConsent === true,
    },
    assignments: rows.map((a) => ({ ...staffView(a), events: byA.get(a.id) || [] })),
  };
}

async function board(shopId, { scope = 'live', limit, before } = {}) {
  if (!['live', 'done'].includes(scope)) throw badRequest('برگه معتبر نیست', 'bad_scope');
  const lim = Math.min(100, Math.max(1, Number.parseInt(limit, 10) || 50));
  const params = [shopId];
  let where = `a.shop_id=$1 AND a.status ${scope === 'live' ? '' : 'NOT '}IN ('requested','assigned','picked_up','moving','arrived','failed')`;
  if (scope === 'done' && before !== undefined && before !== '') {
    const b = Number(before);
    if (!Number.isSafeInteger(b)) throw badRequest('نشانگرِ صفحه معتبر نیست', 'bad_cursor');
    params.push(b);
    where += ` AND a.updated_at < $${params.length}`;
  }
  params.push(lim);
  const rows = await many(
    `SELECT a.*, o.order_no, o.status AS order_status, o.customer_name
       FROM delivery_assignments a JOIN online_orders o ON o.id = a.order_id
      WHERE ${where} ORDER BY ${scope === 'live' ? 'a.created_at ASC' : 'a.updated_at DESC'}, a.id
      LIMIT $${params.length}`,
    params
  );
  //  نامِ مشتری و وضعیتِ سفارش برای میزِ تحویل (C01): «تطبیقِ نام» در
  //  دریافتِ حضوری (S43) و «سفارش هنوز آماده نیست» پیش از زدنِ کد
  return {
    scope,
    items: rows.map((r) => ({
      ...staffView(r), orderNo: Number(r.order_no), orderStatus: r.order_status, customerName: r.customer_name || '',
    })),
  };
}

const { externalCompanies, externalStatement } = require('./delivery-external');   //  DLV-19

// ════════════════════════════════════════════════════════════════════
//  عکسِ تحویل (۲۲.۲ C بندِ ۴، DLV-08)
// ════════════════════════════════════════════════════════════════════

/**
 * ⛔ قاعدهٔ رضایت — عکس فقط وقتی ذخیره می‌شود که همه باشد:
 *   ۱) مشتری هنگامِ سفارش رضایت داده (`delivery.photoConsent === true`)؛
 *   ۲) پیک تأیید کرده عکس فقط از بسته است، نه شخص (`parcelOnly: true`)؛
 *   ۳) خودِ پیکِ همین مأموریت، در «رسیدم» یا «تحویل شد».
 * در غیرِ این، هیچ بایتی نوشته نمی‌شود.
 */
async function putPhoto(shopId, assignmentId, body, actor) {
  const b = body && typeof body === 'object' ? body : {};
  const key = eventKey(b.eventId);
  if (b.parcelOnly !== true) throw badRequest('عکس فقط از بسته — تأییدش لازم است', 'parcel_only_required');
  const b64 = typeof b.imageBase64 === 'string' ? b.imageBase64 : '';
  if (!b64 || b64.length > Math.ceil(PHOTO_MAX_BYTES * 4 / 3) + 8) throw badRequest('عکس بزرگ‌تر از حدِ مجاز است', 'photo_too_large');
  const buf = Buffer.from(b64, 'base64');
  if (buf.length < 100) throw badRequest('عکس خالی است', 'bad_photo');
  if (buf.length > PHOTO_MAX_BYTES) throw badRequest('عکس بزرگ‌تر از حدِ مجاز است', 'photo_too_large');
  const mime = sniff(buf);
  if (!mime) throw badRequest('فقط JPEG، PNG یا WebP', 'bad_photo');
  const hash = crypto.createHash('sha256').update(buf).digest('hex');
  const deviceTime = optInt(b.deviceTime, 'زمانِ دستگاه', { min: 0, max: 4102444800000 });

  return tx(async (c) => {
    const a = (await c.query(
      'SELECT * FROM delivery_assignments WHERE shop_id=$1 AND id=$2 FOR UPDATE', [shopId, String(assignmentId || '')]
    )).rows[0];
    if (!a || a.mode !== 'shop_courier' || a.courier_id !== actor.courierId) throw notFound('مأموریت پیدا نشد', 'assignment_not_found');
    const prev = (await c.query(
      'SELECT id, hash, assignment_id FROM delivery_photos WHERE shop_id=$1 AND event_key=$2', [shopId, key]
    )).rows[0];
    if (prev) {
      if (prev.hash !== hash || prev.assignment_id !== a.id) throw conflict('این شناسه پیش‌تر برای عکسِ دیگری به کار رفته است', 'event_key_reused');
      return { photoId: prev.id, replayed: true };
    }
    if (!['arrived', 'delivered'].includes(a.status)) throw conflict('عکسِ تحویل فقط هنگامِ رسیدن یا تحویل', 'photo_not_allowed_now');
    const order = (await c.query('SELECT delivery FROM online_orders WHERE id=$1', [a.order_id])).rows[0];
    if (asJson(order.delivery).photoConsent !== true) {
      throw new ApiError(403, 'photo_consent_missing', 'مشتری به عکسِ تحویل رضایت نداده است');
    }
    const count = Number((await c.query('SELECT COUNT(*)::int AS n FROM delivery_photos WHERE assignment_id=$1', [a.id])).rows[0].n);
    if (count >= MAX_PHOTOS) throw conflict(`حداکثر ${MAX_PHOTOS} عکس برای هر مأموریت`, 'too_many_photos');
    const at = now();
    const id = ulid(at);
    const consent = { customer: true, parcelOnly: true };
    await c.query(
      `INSERT INTO delivery_photos (id, shop_id, assignment_id, order_id, courier_id, event_key, mime, size, hash,
         data, consent, device_time, created_by, created_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11::jsonb,$12,$13,$14)`,
      [id, shopId, a.id, a.order_id, a.courier_id, key, mime, buf.length, hash, buf, JSON.stringify(consent),
        deviceTime, actor.userId || '', at]
    );
    await appendEvent(c, a, {
      key: `photo:${key}`, hash, kind: 'photo', from: a.status, to: a.status,
      actorType: 'courier', actorId: actor.userId, deviceId: actor.deviceId, deviceTime,
      detail: { photoId: id, size: buf.length, mime },
    }, at);
    return { photoId: id, replayed: false };
  });
}

async function listPhotos(shopId, assignmentId) {
  //  مأموریتِ دکانِ دیگر (یا ناموجود) همان ۴۰۴ِ بقیهٔ درهای شناسه‌دار است، نه «۲۰۰ و فهرستِ خالی»
  const own = await one('SELECT 1 AS ok FROM delivery_assignments WHERE shop_id=$1 AND id=$2', [shopId, String(assignmentId || '')]);
  if (!own) throw notFound('مأموریت پیدا نشد', 'assignment_not_found');
  const rows = await many(
    `SELECT id, assignment_id, order_id, courier_id, mime, size, hash, consent, device_time, created_at
       FROM delivery_photos WHERE shop_id=$1 AND assignment_id=$2 ORDER BY created_at, id`,
    [shopId, String(assignmentId || '')]
  );
  return rows.map((r) => ({
    id: r.id, assignmentId: r.assignment_id, orderId: r.order_id, courierId: r.courier_id, mime: r.mime,
    size: Number(r.size), hash: r.hash, consent: asJson(r.consent), deviceTime: n(r.device_time),
    createdAt: Number(r.created_at),
  }));
}

async function getPhoto(shopId, photoId) {
  const r = await one('SELECT mime, data, order_id FROM delivery_photos WHERE shop_id=$1 AND id=$2', [shopId, String(photoId || '')]);
  if (!r) throw notFound('عکس پیدا نشد', 'photo_not_found');
  return { mime: r.mime, data: Buffer.from(r.data), orderId: r.order_id };
}

// ════════════════════════════════════════════════════════════════════
//  تسویهٔ پیک (DLV-11، DLV-12، S40، S57)
// ════════════════════════════════════════════════════════════════════

const SETTLE_ROWS = `
  SELECT a.id, a.order_id, a.expected_collect_minor, a.cash_collected_minor, a.fare_minor, a.cash_review,
         a.delivered_at, a.status, a.settlement_id, o.order_no
    FROM delivery_assignments a JOIN online_orders o ON o.id = a.order_id`;

function settleRowView(r) {
  const expected = Number(r.expected_collect_minor);
  const collected = Number(r.cash_collected_minor || 0);
  return {
    assignmentId: r.id, orderId: r.order_id, orderNo: Number(r.order_no), deliveredAt: n(r.delivered_at),
    expectedCollectMinor: expected, cashCollectedMinor: collected, collectionDiffMinor: collected - expected,
    fareMinor: Number(r.fare_minor), cashReview: r.cash_review === true,
  };
}

async function courierInShop(run, shopId, courierId) {
  const cr = (await run('SELECT * FROM shop_couriers WHERE shop_id=$1 AND id=$2', [shopId, String(courierId || '')])).rows[0];
  if (!cr) throw notFound('پیک پیدا نشد', 'courier_not_found');
  return cr;
}

/** پیش‌نمایشِ تسویه: مأموریت‌های تحویل‌شده و تسویه‌نشده، به زمانِ سرور. */
async function settlementPreview(shopId, courierId) {
  const cr = await courierInShop(viaDb, shopId, courierId);
  const rows = await many(
    `${SETTLE_ROWS} WHERE a.shop_id=$1 AND a.courier_id=$2 AND a.status='delivered' AND a.settlement_id=''
      ORDER BY a.delivered_at, a.id LIMIT ${MAX_SETTLE}`,
    [shopId, cr.id]
  );
  const t = D.settlementTotals(rows);
  return {
    courier: courierRowView(cr, await cashStats(viaDb, shopId, cr.id)),
    rows: rows.map(settleRowView),
    totals: {
      count: t.count, expectedCashMinor: t.expectedCashMinor, collectedCashMinor: t.collectedCashMinor,
      collectionDiffMinor: t.collectionDiffMinor, fareTotalMinor: t.fareTotalMinor,
    },
    serverTime: now(),
  };
}

function settlementView(s) {
  return {
    id: s.id, settleNo: Number(s.settle_no), courierId: s.courier_id, status: s.status, currency: s.currency,
    assignmentsCount: Number(s.assignments_count), periodFrom: n(s.period_from), periodTo: n(s.period_to),
    expectedCashMinor: Number(s.expected_cash_minor), collectedCashMinor: Number(s.collected_cash_minor),
    collectionDiffMinor: Number(s.collection_diff_minor), fareTotalMinor: Number(s.fare_total_minor),
    farePaidFromCash: s.fare_paid_from_cash === true, handoverDueMinor: Number(s.handover_due_minor),
    handedOverMinor: Number(s.handed_over_minor), receivedMinor: Number(s.received_minor),
    shortageMinor: Number(s.shortage_minor), excessMinor: Number(s.excess_minor),
    reason: s.reason, note: s.note, confirmedBy: s.confirmed_by, createdAt: Number(s.created_at),
  };
}

/**
 * تسویهٔ پایانِ روز (DLV-12): شمارش (received)، مقایسه با بدهیِ پیک،
 * اختلاف با دلیل، تأییدِ مدیر (همین کننده با `delivery.settle`). سندِ بسته.
 * ⛔ هر مأموریت فقط یک‌بار؛ تکرارِ همان درخواست ⇒ همان سند (کلیدِ یکتایی).
 */
async function settle(shopId, courierId, body, actor, key) {
  const b = body && typeof body === 'object' ? body : {};
  if (!key) throw badRequest('کلیدِ یکتاییِ تسویه لازم است', 'idempotency_key_required');
  if (!Array.isArray(b.assignmentIds) || b.assignmentIds.length === 0) throw badRequest('مأموریتی برای تسویه نیامد', 'bad_assignments');
  if (b.assignmentIds.length > MAX_SETTLE) throw badRequest(`حداکثر ${MAX_SETTLE} مأموریت`, 'bad_assignments');
  const ids = [...new Set(b.assignmentIds.map((x) => str(x)))];
  if (ids.length !== b.assignmentIds.length || ids.some((x) => !ID_RE.test(x))) throw badRequest('فهرستِ مأموریت‌ها نامعتبر است', 'bad_assignments');
  const handedOverMinor = int(b.handedOverMinor, 'مبلغِ تحویل‌داده');
  const receivedMinor = int(b.receivedMinor, 'مبلغِ دریافت‌شده');
  if (b.farePaidFromCash !== undefined && typeof b.farePaidFromCash !== 'boolean') throw badRequest('نامعتبر', 'bad_fare_policy');
  const farePaidFromCash = b.farePaidFromCash === true;
  const reason = text(b.reason, 200);
  const note = text(b.note, 300);
  const reqHash = hashOf({ courierId: String(courierId || ''), ids: [...ids].sort(), handedOverMinor, receivedMinor, farePaidFromCash, reason, note });

  return tx(async (c) => {
    const run = (t, p) => c.query(t, p);
    await c.query('SELECT pg_advisory_xact_lock(hashtext($1))', [`delivery-settle:${shopId}`]);
    const prev = (await c.query(
      'SELECT * FROM courier_settlements WHERE shop_id=$1 AND client_key=$2', [shopId, key]
    )).rows[0];
    if (prev) {
      if (prev.request_hash !== reqHash) throw conflict('این کلیدِ یکتایی پیش‌تر برای تسویهٔ دیگری به کار رفته است', 'idempotency_key_reused');
      const rows = (await c.query(`${SETTLE_ROWS} WHERE a.shop_id=$1 AND a.settlement_id=$2 ORDER BY a.delivered_at, a.id`, [shopId, prev.id])).rows;
      return { settlement: settlementView(prev), rows: rows.map(settleRowView), replayed: true };
    }
    const cr = await courierInShop(run, shopId, courierId);
    const ph = ids.map((_, i) => `$${i + 3}`).join(',');
    const rows = (await c.query(
      `${SETTLE_ROWS} WHERE a.shop_id=$1 AND a.courier_id=$2 AND a.id IN (${ph}) ORDER BY a.delivered_at, a.id FOR UPDATE OF a`,
      [shopId, cr.id, ...ids]
    )).rows;
    if (rows.length !== ids.length || rows.some((r) => r.status !== 'delivered' || r.settlement_id !== '')) {
      throw conflict('فهرستِ تسویه عوض شده است (مأموریتِ تسویه‌شده یا نامربوط)؛ دوباره پیش‌نمایش بگیرید', 'settlement_changed');
    }
    const t = D.settlementTotals(rows, { handedOverMinor, receivedMinor, farePaidFromCash });
    if ((t.shortageMinor || t.excessMinor || t.collectionDiffMinor) && !reason) {
      throw badRequest('اختلاف هست؛ دلیلش لازم است', 'reason_required');
    }
    const at = now();
    const id = ulid(at);
    const no = Number((await c.query(
      'SELECT COALESCE(MAX(settle_no), 0) + 1 AS n FROM courier_settlements WHERE shop_id=$1', [shopId]
    )).rows[0].n);
    const times = rows.map((r) => Number(r.delivered_at));
    await c.query(
      `INSERT INTO courier_settlements (id, shop_id, courier_id, settle_no, client_key, request_hash, status,
         assignments_count, period_from, period_to, expected_cash_minor, collected_cash_minor, collection_diff_minor,
         fare_total_minor, fare_paid_from_cash, handover_due_minor, handed_over_minor, received_minor,
         shortage_minor, excess_minor, reason, note, confirmed_by, created_at)
       VALUES ($1,$2,$3,$4,$5,$6,'confirmed',$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$17,$18,$19,$20,$21,$22,$23)`,
      [id, shopId, cr.id, no, key, reqHash, t.count, Math.min(...times), Math.max(...times),
        t.expectedCashMinor, t.collectedCashMinor, t.collectionDiffMinor, t.fareTotalMinor, farePaidFromCash,
        t.handoverDueMinor, t.handedOverMinor, t.receivedMinor, t.shortageMinor, t.excessMinor,
        reason, note, actor.userId || '', at]
    );
    await c.query(
      `UPDATE delivery_assignments SET settlement_id=$2, version=version+1, updated_at=$3
        WHERE shop_id=$1 AND id IN (${ids.map((_, i) => `$${i + 4}`).join(',')})`,
      [shopId, id, at, ...ids]
    );
    //  نقدِ «گرفته‌شده توسطِ پیک» اکنون به دکان رسید — ماشینِ جدای COD
    const orderActor = { type: 'user', id: actor.userId || '', role: actor.role || '' };
    for (const r of rows) {
      const o = (await c.query('SELECT cod_status FROM online_orders WHERE id=$1', [r.order_id])).rows[0];
      if (o && o.cod_status === 'collected_by_courier') {
        await orders.codIn(c, shopId, r.order_id, 'settled_to_shop', orderActor, { settlementId: id, settleNo: no });
      }
    }
    const s = (await c.query('SELECT * FROM courier_settlements WHERE id=$1', [id])).rows[0];
    return { settlement: settlementView(s), rows: rows.map(settleRowView), replayed: false };
  });
}

/** گزارشِ تسویه‌ها با جمعِ کل (به زمانِ سرور). */
async function listSettlements(shopId, { courierId, from, to, limit } = {}) {
  const params = [shopId];
  let where = 'shop_id=$1';
  if (courierId) { params.push(String(courierId)); where += ` AND courier_id=$${params.length}`; }
  for (const [v, op] of [[from, '>='], [to, '<']]) {
    if (v === undefined || v === '') continue;
    const x = Number(v);
    if (!Number.isSafeInteger(x)) throw badRequest('بازهٔ زمانی معتبر نیست', 'bad_range');
    params.push(x);
    where += ` AND created_at ${op} $${params.length}`;
  }
  const lim = Math.min(200, Math.max(1, Number.parseInt(limit, 10) || 50));
  const items = await many(`SELECT * FROM courier_settlements WHERE ${where} ORDER BY created_at DESC, id LIMIT ${lim}`, params);
  const sums = await one(
    `SELECT COUNT(*)::int AS n, COALESCE(SUM(assignments_count),0) AS assignments,
            COALESCE(SUM(expected_cash_minor),0) AS expected, COALESCE(SUM(collected_cash_minor),0) AS collected,
            COALESCE(SUM(fare_total_minor),0) AS fares, COALESCE(SUM(handover_due_minor),0) AS due,
            COALESCE(SUM(received_minor),0) AS received, COALESCE(SUM(shortage_minor),0) AS shortage,
            COALESCE(SUM(excess_minor),0) AS excess
       FROM courier_settlements WHERE ${where}`,
    params
  );
  return {
    items: items.map(settlementView),
    totals: {
      settlements: Number(sums.n), assignments: Number(sums.assignments), expectedCashMinor: Number(sums.expected),
      collectedCashMinor: Number(sums.collected), fareTotalMinor: Number(sums.fares),
      handoverDueMinor: Number(sums.due), receivedMinor: Number(sums.received),
      shortageMinor: Number(sums.shortage), excessMinor: Number(sums.excess),
    },
  };
}

async function getSettlement(shopId, id) {
  const s = await one('SELECT * FROM courier_settlements WHERE shop_id=$1 AND id=$2', [shopId, String(id || '')]);
  if (!s) throw notFound('تسویه پیدا نشد', 'settlement_not_found');
  const rows = await many(`${SETTLE_ROWS} WHERE a.shop_id=$1 AND a.settlement_id=$2 ORDER BY a.delivered_at, a.id`, [shopId, s.id]);
  return { settlement: settlementView(s), rows: rows.map(settleRowView) };
}

const { customerView, putLocation, putEta, purgeLocations } = require('./delivery-live');   //  S5: نمای پیک برای مشتری

/** دسترسی‌های کارمند برای `D.check`. */
function staffActor(req) {
  return {
    userId: req.user.id, role: req.role, deviceId: req.device?.id || '',
    canAssign: can(req.role, 'delivery.assign'), canHandover: can(req.role, 'delivery.handover'),
  };
}

module.exports = {
  FLAG_KEY, FEATURE_KEY, KEY_RE, DEFAULT_CASH_CAP_MINOR, CODE_MAX_ATTEMPTS,
  VIEW_FLAG, LOCATION_FLAG, LOCATION_TTL_MS, LOCATION_MIN_INTERVAL_MS, ON_THE_WAY, customerView, putLocation, putEta, purgeLocations,
  stateOf, staffActor, courierOfUser, listCouriers, createCourier, updateCourier, enrollCourierMember,
  assign, regenerateCode, courierEvents, staffEvent, recall, fixAddress, missions, forOrder, board,
  putPhoto, listPhotos, getPhoto, settlementPreview, settle, listSettlements, getSettlement,
  externalCompanies, externalStatement, eventKey,
};
