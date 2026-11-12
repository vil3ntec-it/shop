'use strict';
/**
 * RSK-08 — پروندهٔ مدرکِ اختلافِ سفارش (v3 §۶.۲۱؛ §۱۹.۹ بندِ ۵؛ §۲۱.۴)، پشتِ پرچمِ خاموشِ `shop.orders.dispute_file`.
 *
 *   پرونده ── فقط خواندن و فقط آن‌چه امروز ثبت است: خطِ زمانیِ سفارش، مأموریت‌ها و رویدادهای پیک (زمانِ دستگاه در
 *             برابرِ زمانِ سرور)، فاصلهٔ «تحویل شد» تا پین، اسکن‌های جمع‌آوری، قفسه و کیسه، تأییدِ تحویل (کد)،
 *             فرادادهٔ عکس‌ها، گزارشِ مشکلِ مشتری (NTF-07)، تماسِ پیک و گفت‌وگو (فقط شمار) و خودِ اختلاف‌ها.
 *   اختلاف ── باز کردن (نوع + دلیل)، یادداشت، سپردن به مسئول با مهلت (§۱۹.۹ بندِ ۵)، و بستن با نتیجه؛ هر کدام یک
 *             ردیفِ `audit_logs` (`order.dispute.*`) در همان تراکنش. هیچ جدولِ تازه، هیچ ویرایش و هیچ پاک کردن: وضعیت
 *             همیشه از همین ردیف‌ها ساخته می‌شود؛ سپردنِ دوباره ردیفِ تازه است و آخرین سپردن مسئول و مهلتِ امروز است.
 *             مسئول فقط صاحبِ دکان یا عضوِ فعالِ دارای `orders.manage`؛ مهلت از اکنون تا ۹۰ روز؛ گذشتنِ مهلتِ اختلافِ باز
 *             ⇒ `overdue` (فقط نما، هیچ کارِ خودکاری — §۱۹.۹ بندِ ۷).
 *
 * ⛔ هیچ دادهٔ تازه‌ای گردآوری نمی‌شود. موقعیتِ خامِ پیک هیچ‌جا نگه داشته نمی‌شود (DLV-16، `courier-checks`) ⇒ فقط
 *    فاصلهٔ گردشده؛ نقطهٔ زندهٔ `delivery_locations` (فقط در راه، پاک‌شونده) در پرونده نیست. شمارهٔ مشتری پوشیده،
 *    از نشانی فقط «پین دارد/ندارد»، فرادادهٔ عکسِ تحویل فقط برای `delivery.photo` و بایتِ هیچ عکسی این‌جا نیست.
 * ⛔ نوشتن زیرِ قفلِ ردیفِ سفارش: یک اختلافِ باز در هر زمان؛ کلیدِ یکتای درخواست ⇒ ارسالِ دوباره همان ردیف،
 *    همان کلید با بدنهٔ دیگر ⇒ ۴۰۹. سقفِ ۲۰۰ ردیف برای هر سفارش.
 */
const db = require('../db');
const { tx, now } = db;
const audit = require('./audit');
const orders = require('./orders');
const delivery = require('./delivery');
const pickPhotos = require('./pick-photos');
const issues = require('./order-issues');
const maskCards = (t) => require('./ops-errors').maskCards(t);   //  S117: شمارهٔ کاملِ کارت ⇒ «[کارت]» پیش از دیتابیس و هشِ تکرار
const { can } = require('./permissions');
const { badRequest, notFound, conflict, forbidden } = require('../middleware/errors');

const FLAG = 'shop.orders.dispute_file';
const KINDS = Object.freeze(['not_delivered', 'missing_item', 'wrong_item', 'damaged', 'cash', 'late', 'other']);
const OUTCOMES = Object.freeze(['customer_favor', 'shop_favor', 'settled', 'withdrawn']);
const ACTION = Object.freeze({
  open: 'order.dispute.open', note: 'order.dispute.note', resolve: 'order.dispute.resolve', assign: 'order.dispute.assign',
});
/** مهلتِ سپردن: از اکنون (با ۵ دقیقه گذشتِ ساعت) تا ۹۰ روز */
const DUE = Object.freeze({ pastMs: 5 * 60 * 1000, maxMs: 90 * 24 * 60 * 60 * 1000 });
const USER_RE = /^[A-Za-z0-9_-]{1,80}$/;
const TEXT = Object.freeze({ min: 3, max: 1000 });
const MAX_ENTRIES = 200;
const CLOCK_WARN_MS = 5 * 60 * 1000;
const LIST_MAX = 1000;
const KEY_RE = /^[A-Za-z0-9_.:-]{8,64}$/;
const DISPUTE_RE = /^aud_[0-9a-f]{24}$/;

const asJson = (x) => (x && typeof x === 'object' ? x : (() => { try { return JSON.parse(x); } catch { return {}; } })());
const num = (v) => (v === null || v === undefined ? null : Number(v));

async function enabled(shopId) {
  return (await orders.flagOf(shopId)) && (await orders.flagOf(shopId, FLAG));
}

async function needFlag(shopId) {
  if (!(await enabled(shopId))) throw forbidden('پروندهٔ اختلافِ سفارش برای این دکان روشن نیست', 'dispute_file_disabled');
}

// ════════════════════════════════════════════════════════════════════
//  اختلاف: ردیف‌های سابقه ⇒ نما
// ════════════════════════════════════════════════════════════════════

async function entriesOf(run, shopId, orderId) {
  const rows = (await run(
    `SELECT id, user_id, action, detail, created_at FROM audit_logs
      WHERE shop_id=$1 AND target_type='online_order' AND target_id=$2 AND action = ANY($3::text[])
      ORDER BY created_at, id`, [shopId, orderId, Object.values(ACTION)]
  )).rows.map((r) => ({ ...r, detail: asJson(r.detail) }));
  return rows.sort((a, b) => (Number(a.detail.seq) || 0) - (Number(b.detail.seq) || 0));
}

function disputesOf(entries, at0 = now()) {
  const byId = new Map();
  for (const e of entries) {
    const d = e.detail;
    const by = { userId: e.user_id, role: d.role || '' };
    const at = Number(e.created_at);
    if (e.action === ACTION.open) {
      byId.set(e.id, {
        id: e.id, kind: d.kind, reason: d.text, status: 'open', openedBy: by, openedAt: at, notes: [], resolution: null,
        assigneeId: null, dueAt: null, overdue: false, assignments: [],
      });
      continue;
    }
    const x = byId.get(d.disputeId);
    if (!x) continue;
    if (e.action === ACTION.note) x.notes.push({ id: e.id, text: d.text, by, at });
    if (e.action === ACTION.assign && !x.resolution) {
      x.assignments.push({ id: e.id, assigneeId: d.assigneeId, dueAt: Number(d.dueAt), text: d.text || '', by, at });
      x.assigneeId = d.assigneeId;
      x.dueAt = Number(d.dueAt);
    }
    if (e.action === ACTION.resolve && !x.resolution) {
      x.status = 'resolved';
      x.resolution = { id: e.id, outcome: d.outcome, text: d.text, by, at };
    }
  }
  for (const x of byId.values()) x.overdue = x.status === 'open' && x.dueAt !== null && x.dueAt < at0;
  return [...byId.values()];
}

// ════════════════════════════════════════════════════════════════════
//  اختلاف: نوشتن (فقط افزودن)
// ════════════════════════════════════════════════════════════════════

function parse(action, body) {
  const b = body && typeof body === 'object' ? body : {};
  const key = typeof b.requestKey === 'string' ? b.requestKey.trim() : '';
  if (!KEY_RE.test(key)) throw badRequest('کلیدِ یکتای درخواست لازم است', 'bad_request_key');
  const out = { key };
  if (action === ACTION.open) {
    if (!KINDS.includes(b.kind)) throw badRequest('نوعِ اختلاف معتبر نیست', 'bad_kind');
    out.kind = b.kind;
  } else {
    if (b.disputeId !== undefined && b.disputeId !== null && b.disputeId !== '') {
      if (typeof b.disputeId !== 'string' || !DISPUTE_RE.test(b.disputeId)) throw badRequest('شناسهٔ اختلاف معتبر نیست', 'bad_dispute_id');
      out.disputeId = b.disputeId;
    }
    if (action === ACTION.resolve) {
      if (!OUTCOMES.includes(b.outcome)) throw badRequest('نتیجهٔ اختلاف معتبر نیست', 'bad_outcome');
      out.outcome = b.outcome;
    }
    if (action === ACTION.assign) {
      if (typeof b.assigneeId !== 'string' || !USER_RE.test(b.assigneeId)) throw badRequest('مسئولِ اختلاف معتبر نیست', 'bad_assignee');
      out.assigneeId = b.assigneeId;
      if (!Number.isSafeInteger(b.dueAt)) throw badRequest('مهلتِ اختلاف معتبر نیست', 'bad_due');
      out.dueAt = b.dueAt;
    }
  }
  //  سپردن: یادداشت اختیاری است (نبود ⇒ '')
  out.text = action === ACTION.assign && (b.text === undefined || b.text === null || b.text === '')
    ? '' : maskCards(issues.cleanText(action === ACTION.open ? b.reason : b.text, TEXT, 'bad_text'));
  return out;
}

/** مسئول فقط صاحبِ همین دکان یا عضوِ فعالی که `orders.manage` دارد (همان کسانی که پرونده را می‌بینند) */
async function assignable(run, shopId, userId) {
  const owner = (await run('SELECT 1 FROM shops WHERE id=$1 AND owner_user_id=$2', [shopId, userId])).rows[0];
  if (owner) return true;
  const m = (await run("SELECT role FROM shop_members WHERE shop_id=$1 AND user_id=$2 AND status='active'", [shopId, userId])).rows[0];
  return !!m && can(m.role, 'orders.manage');
}

const sameBody = (d, p) => d.text === p.text && (d.kind || '') === (p.kind || '') && (d.outcome || '') === (p.outcome || '')
  && (d.assigneeId || '') === (p.assigneeId || '') && (d.dueAt ?? null) === (p.dueAt ?? null)
  && (p.disputeId === undefined || d.disputeId === p.disputeId);

/** ⇒ `{dispute, replayed}` — `actor`: `{id, role, ip}` */
async function act(shopId, orderId, action, body, actor) {
  await needFlag(shopId);
  const p = parse(action, body);
  const id = String(orderId || '');
  const out = await tx(async (c) => {
    const run = (t, v) => c.query(t, v);
    const o = (await run('SELECT id, order_no FROM online_orders WHERE shop_id=$1 AND id=$2 FOR UPDATE', [shopId, id])).rows[0];
    if (!o) throw notFound('سفارش پیدا نشد', 'order_not_found');
    const entries = await entriesOf(run, shopId, o.id);
    const prev = entries.find((e) => e.detail.requestKey === p.key);
    if (prev) {
      if (prev.action !== action || !sameBody(prev.detail, p)) throw conflict('این کلید برای درخواستِ دیگری به کار رفته', 'request_key_reused');
      return { orderId: o.id, disputeId: action === ACTION.open ? prev.id : prev.detail.disputeId, replayed: true };
    }
    if (entries.length >= MAX_ENTRIES) throw conflict('سقفِ ردیف‌های اختلافِ این سفارش پر است', 'dispute_limit');
    const open = disputesOf(entries).find((x) => x.status === 'open');
    if (action === ACTION.open && open) {
      const err = conflict('برای این سفارش اختلافِ باز هست', 'dispute_open');
      err.data = { disputeId: open.id };
      throw err;
    }
    if (action !== ACTION.open) {
      if (!open) throw conflict('اختلافِ بازی برای این سفارش نیست', 'no_open_dispute');
      if (p.disputeId && p.disputeId !== open.id) throw conflict('اختلافِ باز عوض شده است؛ پرونده را دوباره بخوانید', 'dispute_changed');
    }
    if (action === ACTION.assign) {
      const t = now();
      if (p.dueAt < t - DUE.pastMs || p.dueAt > t + DUE.maxMs) throw badRequest('مهلت باید از اکنون تا ۹۰ روز باشد', 'bad_due');
      if (!(await assignable(run, shopId, p.assigneeId))) throw badRequest('مسئول باید صاحب یا کارمندِ فعالِ دارای اجازهٔ سفارش‌ها باشد', 'bad_assignee');
    }
    const seq = entries.reduce((m, e) => Math.max(m, Number(e.detail.seq) || 0), 0) + 1;
    const detail = {
      seq, orderNo: Number(o.order_no), role: actor.role || '', text: p.text, requestKey: p.key,
      ...(p.kind ? { kind: p.kind } : {}), ...(p.outcome ? { outcome: p.outcome } : {}),
      ...(p.assigneeId ? { assigneeId: p.assigneeId, dueAt: p.dueAt } : {}),
      ...(action === ACTION.open ? {} : { disputeId: open.id }),
    };
    const row = await audit.logIn(c, {
      shopId, userId: actor.id, action, targetType: 'online_order', targetId: o.id, detail, ip: actor.ip || '',
    });
    return { orderId: o.id, disputeId: action === ACTION.open ? row.id : open.id, replayed: false, after: row.afterCommit };
  });
  if (out.after) await out.after();
  const all = disputesOf(await entriesOf((t, v) => db.query(t, v), shopId, out.orderId));
  return { dispute: all.find((x) => x.id === out.disputeId) || null, replayed: out.replayed };
}

const open = (shopId, orderId, body, actor) => act(shopId, orderId, ACTION.open, body, actor);
const note = (shopId, orderId, body, actor) => act(shopId, orderId, ACTION.note, body, actor);
const resolve = (shopId, orderId, body, actor) => act(shopId, orderId, ACTION.resolve, body, actor);
const assign = (shopId, orderId, body, actor) => act(shopId, orderId, ACTION.assign, body, actor);

// ════════════════════════════════════════════════════════════════════
//  پرونده (فقط خواندن)
// ════════════════════════════════════════════════════════════════════

const capped = (rows, name, truncated) => {
  if (rows.length > LIST_MAX) { truncated.push(name); return rows.slice(0, LIST_MAX); }
  return rows;
};

function clockOf(at, deviceAt) {
  if (deviceAt === null || deviceAt === undefined) return null;
  return Number(deviceAt) - Number(at);
}

function assignmentView(a, photos, photoOk) {
  const events = a.events.map((e) => ({ ...e, skewMs: clockOf(e.serverTime, e.deviceTime) }));
  return {
    id: a.id, attemptNo: a.attemptNo, mode: a.mode, courierId: a.courierId, externalName: a.externalName,
    status: a.status, statusReason: a.statusReason, expectedCollectMinor: a.expectedCollectMinor,
    cashCollectedMinor: a.cashCollectedMinor, fareMinor: a.fareMinor, cashReview: a.cashReview,
    deliveredAt: a.deliveredAt, createdAt: a.createdAt,
    confirmation: {
      hasCode: a.hasCode, codeUsed: a.codeUsed, codeLocked: a.codeLocked,
      codeVerified: events.some((e) => e.detail.codeVerified === true),
      manualConfirm: events.some((e) => e.detail.manualConfirm === true),
      //  امضای مشتری در هیچ درِ امروز گرفته نمی‌شود
      signature: null,
    },
    recall: a.recall,
    photos: photoOk ? photos.map((p) => ({ ...p, skewMs: clockOf(p.createdAt, p.deviceTime), bytes: `/api/shop/delivery/photos/${p.id}` })) : null,
    events,
  };
}

async function pickOf(shopId, orderId, truncated) {
  const pick = await db.one(
    `SELECT status, picker_id, batch_id, bags, shelf_slot, ready_scanned_at, packed_at, started_at
       FROM order_picks WHERE shop_id=$1 AND order_id=$2`, [shopId, orderId]);
  const events = await db.many(
    `SELECT kind, line_no, outcome, code, detail, actor_id, created_at FROM order_pick_events
      WHERE shop_id=$1 AND order_id=$2 ORDER BY created_at, id LIMIT ${LIST_MAX + 1}`, [shopId, orderId]);
  const shelf = await db.many(
    'SELECT bag, slot, scanned_by, scanned_at FROM order_pick_shelf_scans WHERE shop_id=$1 AND order_id=$2 ORDER BY scanned_at, bag',
    [shopId, orderId]);
  return {
    pick: pick ? {
      status: pick.status, pickerId: pick.picker_id, batchId: pick.batch_id, bags: Number(pick.bags), shelfSlot: pick.shelf_slot,
      startedAt: num(pick.started_at), packedAt: num(pick.packed_at), readyScannedAt: num(pick.ready_scanned_at),
    } : null,
    pickEvents: capped(events, 'pickEvents', truncated).map((e) => ({
      kind: e.kind, lineNo: num(e.line_no), outcome: e.outcome, reject: e.code, detail: asJson(e.detail),
      actorId: e.actor_id, at: Number(e.created_at),
    })),
    shelfScans: shelf.map((s) => ({ bag: Number(s.bag), slot: s.slot, by: s.scanned_by, at: Number(s.scanned_at) })),
  };
}

async function contactsOf(shopId, orderId, assignmentIds) {
  const calls = assignmentIds.length ? await db.many(
    `SELECT target_id, user_id, detail, created_at FROM audit_logs
      WHERE shop_id=$1 AND action='delivery.courier.call' AND target_type='delivery_assignment' AND target_id = ANY($2::text[])
      ORDER BY created_at, id LIMIT ${LIST_MAX}`, [shopId, assignmentIds]) : [];
  const chats = await db.many(
    `SELECT c.id, c.status, c.created_at, c.last_message_at,
            (SELECT COUNT(*)::int FROM shop_chat_messages m WHERE m.conversation_id=c.id AND m.sender='customer' AND m.deleted_at IS NULL) AS from_customer,
            (SELECT COUNT(*)::int FROM shop_chat_messages m WHERE m.conversation_id=c.id AND m.sender<>'customer' AND m.deleted_at IS NULL) AS from_staff
       FROM shop_chat_conversations c WHERE c.shop_id=$1 AND c.order_id=$2 ORDER BY c.created_at, c.id`, [shopId, orderId]);
  const courierCalls = calls.map((r) => ({
    assignmentId: r.target_id, userId: r.user_id, courierId: asJson(r.detail).courierId || '',
    status: asJson(r.detail).status || '', at: Number(r.created_at),
  }));
  const conversations = chats.map((c) => ({
    id: c.id, status: c.status, fromCustomer: Number(c.from_customer), fromStaff: Number(c.from_staff),
    startedAt: Number(c.created_at), lastMessageAt: num(c.last_message_at),
  }));
  const none = !courierCalls.length && !conversations.length;
  return {
    courierCalls, conversations, none,
    note: none
      ? 'برای این سفارش هیچ تماسِ پیک یا گفت‌وگویی ثبت نشده است.'
      : 'از تماسِ پیک فقط زمان و پیک ثبت است (نه شماره، مدت یا نتیجه)؛ از گفت‌وگو فقط شمارِ پیام‌ها این‌جاست.',
  };
}

async function peopleOf(shopId, userIds, courierIds) {
  const users = userIds.length ? await db.many(
    `SELECT u.id, u.name FROM users u
      WHERE u.id = ANY($2::text[])
        AND (EXISTS (SELECT 1 FROM shop_members m WHERE m.shop_id=$1 AND m.user_id=u.id)
             OR EXISTS (SELECT 1 FROM shops s WHERE s.id=$1 AND s.owner_user_id=u.id))`, [shopId, userIds]) : [];
  const couriers = courierIds.length ? await db.many(
    'SELECT id, name FROM shop_couriers WHERE shop_id=$1 AND id = ANY($2::text[])', [shopId, courierIds]) : [];
  return {
    users: Object.fromEntries(users.map((u) => [u.id, u.name])),
    couriers: Object.fromEntries(couriers.map((c) => [c.id, c.name])),
  };
}

const uniq = (xs) => [...new Set(xs.filter((x) => typeof x === 'string' && x))];

/** کسانی که اختلاف را می‌شود به آن‌ها سپرد (همان `assignable`): صاحب و اعضای فعالِ دارای `orders.manage` — فقط نام و نقش */
async function assigneesOf(shopId) {
  const rows = await db.many(
    `SELECT u.id, u.name, 'owner' AS role FROM shops s JOIN users u ON u.id = s.owner_user_id WHERE s.id=$1
     UNION ALL
     SELECT u.id, u.name, m.role FROM shop_members m JOIN users u ON u.id = m.user_id
      WHERE m.shop_id=$1 AND m.status='active'`, [shopId]);
  const seen = new Set();
  return rows.filter((r) => can(r.role, 'orders.manage') && !seen.has(r.id) && seen.add(r.id))
    .map((r) => ({ userId: r.id, name: r.name || '', role: r.role }));
}

/** پروندهٔ یک سفارش برای `viewer` (`{id, role}`) — سفارشِ دکانِ دیگر ⇒ ۴۰۴. */
async function fileOf(shopId, orderId, viewer) {
  await needFlag(shopId);
  const order = await orders.get(shopId, String(orderId || ''), viewer);
  const dlv = await delivery.forOrder(shopId, order.id);
  const photoOk = can(viewer.role, 'delivery.photo');
  const truncated = [];
  const assignments = [];
  for (const a of dlv.assignments) {
    const photos = photoOk ? await delivery.listPhotos(shopId, a.id) : [];
    assignments.push(assignmentView(a, photos, photoOk));
  }
  const events = assignments.flatMap((a) => a.events.map((e) => ({ assignmentId: a.id, ...e })));
  const skews = events.filter((e) => e.skewMs !== null);
  const scans = await pickOf(shopId, order.id, truncated);
  const pick = (await pickPhotos.list(shopId, order.id)).photos.map((p) => ({
    ...p, skewMs: clockOf(p.createdAt, p.deviceTime), bytes: `/api/shop/orders/${order.id}/pick/photos/${p.id}`,
  }));
  const issueList = (await issues.forOrder(shopId, order.id)).map((i) => ({
    ...i, photos: i.photos.map((p) => ({ ...p, bytes: `/api/shop/order-feedback/issues/${i.id}/photos/${p.id}` })),
  }));
  const contacts = await contactsOf(shopId, order.id, assignments.map((a) => a.id));
  const disputes = disputesOf(await entriesOf((t, v) => db.query(t, v), shopId, order.id));
  const people = await peopleOf(shopId, uniq([
    order.createdBy,
    ...order.events.filter((e) => e.actorType === 'user').map((e) => e.actorId),
    ...events.map((e) => e.actorId),
    ...scans.pickEvents.map((e) => e.actorId), ...scans.shelfScans.map((s) => s.by), scans.pick && scans.pick.pickerId,
    ...pick.map((p) => p.createdBy), ...contacts.courierCalls.map((c) => c.userId),
    ...disputes.flatMap((d) => [d.openedBy.userId, ...d.notes.map((n) => n.by.userId), d.resolution && d.resolution.by.userId,
      ...d.assignments.flatMap((a) => [a.assigneeId, a.by.userId])]),
  ]), uniq(assignments.map((a) => a.courierId)));
  const addr = order.address || {};
  return {
    version: 1,
    generatedAt: now(),
    order: {
      id: order.id, orderNo: order.orderNo, status: order.status, statusReason: order.statusReason, source: order.source,
      isTest: order.isTest, fulfillment: order.fulfillment, paymentMethod: order.paymentMethod,
      paymentStatus: order.paymentStatus, codStatus: order.codStatus, currency: order.currency, totalMinor: order.totalMinor,
      itemsCount: order.itemsCount, createdAt: order.createdAt, updatedAt: order.updatedAt,
      customer: { name: order.customer.name, phoneMasked: order.customer.phoneMasked },
      address: {
        landmark: addr.landmark || '', area: addr.area || (addr.zone && addr.zone.name) || '', text: addr.text || '',
        hasPin: addr.lat !== undefined && addr.lat !== null,
      },
      lines: order.lines.map((l) => ({
        lineNo: l.lineNo, name: l.name, unit: l.unit, qtyMilli: l.qtyMilli, unitPriceMinor: l.unitPriceMinor,
        lineTotalMinor: l.lineTotalMinor, substituteName: l.substituteName,
      })),
    },
    timeline: order.events,
    delivery: { photoConsent: dlv.order.photoConsent, photosVisible: photoOk, assignments },
    location: {
      rawRetained: false,
      atDelivery: events.filter((e) => Number.isFinite(e.detail.distanceM))
        .map((e) => ({ assignmentId: e.assignmentId, seq: e.seq, toStatus: e.toStatus, distanceM: e.detail.distanceM, serverTime: e.serverTime })),
      note: 'موقعیتِ خامِ پیک نگه داشته نمی‌شود؛ فقط فاصلهٔ گردشدهٔ «تحویل شد» تا پینِ سفارش (اگر سنجیده شده باشد).',
    },
    clock: {
      warnMs: CLOCK_WARN_MS, samples: skews.length,
      maxAbsSkewMs: skews.reduce((m, e) => Math.max(m, Math.abs(e.skewMs)), 0),
      warnings: skews.filter((e) => Math.abs(e.skewMs) > CLOCK_WARN_MS).length,
      note: 'skewMs = زمانِ دستگاه منهای زمانِ سرور؛ مرجعِ ترتیب و تسویه همیشه زمانِ سرور است.',
    },
    scans: {
      ...scans,
      bagsAtPickup: events.filter((e) => Number.isFinite(e.detail.bagsScanned))
        .map((e) => ({ assignmentId: e.assignmentId, seq: e.seq, bagsScanned: e.detail.bagsScanned, serverTime: e.serverTime })),
    },
    pickPhotos: pick,
    issues: issueList,
    contacts,
    disputes,
    current: disputes.find((d) => d.status === 'open') || null,
    people,
    truncated,
    assignees: await assigneesOf(shopId),
  };
}

module.exports = {
  FLAG, KINDS, OUTCOMES, ACTION, TEXT, MAX_ENTRIES, CLOCK_WARN_MS, DUE,
  enabled, needFlag, fileOf, open, note, resolve, assign,
};
