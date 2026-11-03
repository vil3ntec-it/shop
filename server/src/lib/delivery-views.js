'use strict';
/**
 * مدلِ خواندنیِ تحویل و پیک (نمای کارمند، کارتِ مأموریتِ پیک DLV-02، رویداد، ردیفِ پیک) — از `lib/delivery.js` جدا شد تا زیرِ سقفِ اندازه بماند؛
 * رفتار همان.
 */
const D = require('./delivery-states');
const orders = require('./orders');
const liveCap = require('./delivery-live-cap');
const { n, asJson } = require('./delivery-input');
const { CODE_MAX_ATTEMPTS } = require('./delivery-codes');

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
    delivery: live ? { callBeforeArrival: pref.callBeforeArrival === true, leaveWith: pref.leaveWith || '', ...require('./delivery-prefs').courierCash(pref, r.expected_collect_minor) } : null,   // CHK-14: اسکناس و پول خرد
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
    cashCapMinor: Number(c.cash_cap_minor), maxLive: liveCap.view(c),
    cashHeldMinor: Number(stats.held || 0),
    liveCount: Number(stats.live || 0),
    liveExpectedMinor: Number(stats.live_expected || 0),
    createdAt: Number(c.created_at), updatedAt: Number(c.updated_at),
  };
}

module.exports = { staffView, courierView, eventView, courierRowView };
