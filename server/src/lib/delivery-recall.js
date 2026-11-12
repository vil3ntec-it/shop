'use strict';
/**
 * لغو پس از حرکتِ پیک (۲۲.۲ C بندِ ۶) — از `lib/delivery.js` جدا شد تا زیرِ سقفِ اندازه بماند؛
 * رفتار همان. نوشتنِ رویدادِ مأموریت (`appendEvent`) از همان پرونده
 * تزریق می‌شود.
 */
const { now, tx } = require('../db');
const D = require('./delivery-states');
const { ulid } = require('./ulid');
const { badRequest, notFound, conflict } = require('../middleware/errors');
const { str, noteText, hashOf } = require('./delivery-input');
const { staffView } = require('./delivery-views');

module.exports = ({ appendEvent }) => {
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
  const note = noteText(b.note, 300);   //  S117
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

  return { recall };
};
