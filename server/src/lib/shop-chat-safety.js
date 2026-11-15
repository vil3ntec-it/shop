'use strict';
/**
 * چتِ دکان: بلاک (به‌ازای دکان و کلیدِ مشتری)، گزارشِ سوءاستفاده به مدیرِ سامانه و جست‌وجو در پیام‌های مجاز (CHAT-14) —
 * از `lib/shop-chat.js` جدا شد تا زیرِ سقفِ اندازه بماند (`tools/quality`)؛ رفتار و درها همان. کمک‌های همان پرونده (قفل و
 * نمای گفت‌وگو، کلیدِ بیننده، سنجنده‌ها) تزریق می‌شوند؛ مرزِ «مالِ همین مشتری» همان `shop-chat-account.mineSql`.
 */
const { one, many, query, newId, now } = require('../db');
const { notify } = require('./chat-wait');
const acct = require('./shop-chat-account');
const { badRequest, notFound } = require('../middleware/errors');

module.exports = ({ rowOf, viewOf, viewerKey, shopSide, shortText, intIn, obj, str, n, REASONS, ID_RE }) => {
/** بلاک به‌ازای (دکان، شماره): دکان ⇒ مشتری؛ مشتری ⇒ دکان. خواندنِ گذشته آزاد می‌ماند. */
async function setBlock(viewer, id, on) {
  const conv = await rowOf(viewer, id);
  const blocker = viewer.side;
  if (on) {
    await query(
      `INSERT INTO shop_chat_blocks (shop_id, customer_phone, blocker, by_user_id, created_at)
       VALUES ($1,$2,$3,$4,$5) ON CONFLICT DO NOTHING`,
      [conv.shop_id, conv.customer_phone, blocker, shopSide(viewer.side) ? viewer.userId : '', now()]
    );
  } else {
    await query('DELETE FROM shop_chat_blocks WHERE shop_id=$1 AND customer_phone=$2 AND blocker=$3',
      [conv.shop_id, conv.customer_phone, blocker]);
  }
  if (shopSide(viewer.side)) {
    await require('./audit').log({
      shopId: conv.shop_id, userId: viewer.userId, action: on ? 'chat.customer_blocked' : 'chat.customer_unblocked',
      targetType: 'chat_conversation', targetId: conv.id,
    });
  }
  notify(conv.id);
  return viewOf(viewer, conv.id);
}

/** گزارشِ سوءاستفاده به مدیرِ سامانه — با رونوشتِ همان پیام در همان لحظه. یک گزارش برای هر (پیام، طرف): تکرار ردیفِ دوم نمی‌سازد. */
async function report(viewer, id, body) {
  const b = obj(body);
  const reason = str(b.reason);
  if (!REASONS.includes(reason)) throw badRequest('دلیلِ گزارش نامعتبر است', 'bad_reason');
  const note = require('./ops-errors').maskCards(shortText(b.note, 500));   //  S117
  const conv = await rowOf(viewer, id);
  let messageId = '';
  let snapshot = {};
  if (b.messageId) {
    messageId = str(b.messageId);
    const m = ID_RE.test(messageId) && await one(
      'SELECT * FROM shop_chat_messages WHERE id=$1 AND conversation_id=$2', [messageId, conv.id]
    );
    if (!m) throw notFound('این پیام پیدا نشد', 'message_not_found');
    if (m.sender === viewer.side) throw badRequest('پیامِ خودتان را گزارش نمی‌کنید', 'bad_report');
    snapshot = {
      sender: m.sender, senderUserId: m.sender_user_id || '', kind: m.kind, body: m.body,
      mediaId: m.media_id || '', createdAt: n(m.created_at), editedAt: m.edited_at == null ? null : n(m.edited_at),
      deleted: !!m.deleted_at,
    };
  }
  const ins = await one(
    `INSERT INTO shop_chat_reports (id, shop_id, conversation_id, message_id, reporter, reporter_user_id, reason, note, snapshot, created_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10)
     ON CONFLICT (conversation_id, message_id, reporter) DO NOTHING RETURNING id`,
    [newId('scr'), conv.shop_id, conv.id, messageId, viewer.side, shopSide(viewer.side) ? viewer.userId : '',
      reason, note, JSON.stringify(snapshot), now()]
  );
  return { reported: true, duplicate: !ins };
}

/** جست‌وجو در پیام‌های مجاز — پاک‌شده و «حذف برای من» نمی‌آیند (CHAT-14). */
async function search(viewer, q, { limit } = {}) {
  const term = shortText(q, 100);
  if (term.length < 2) throw badRequest('دست‌کم دو نویسه بنویسید', 'query_too_short');
  const lim = intIn(limit, { min: 1, max: 50, def: 30 });
  const args = [viewer.shopId, `%${term.replace(/[\\%_]/g, (ch) => `\\${ch}`)}%`, viewerKey(viewer)];
  let scope = '';
  if (viewer.side === 'customer') scope = ` AND ${acct.mineSql(viewer, args)}`;
  args.push(lim);
  const rows = await many(
    `SELECT m.id, m.conversation_id, m.seq, m.sender, m.sender_user_id, m.kind, m.body, m.created_at
       FROM shop_chat_messages m JOIN shop_chat_conversations c ON c.id = m.conversation_id
      WHERE m.shop_id = $1 AND c.shop_id = $1${scope} AND m.deleted_at IS NULL
        AND m.body ILIKE $2 ESCAPE '\\'
        AND NOT EXISTS (SELECT 1 FROM shop_chat_hidden h WHERE h.message_id = m.id AND h.viewer = $3)
      ORDER BY m.created_at DESC, m.id DESC LIMIT $${args.length}`,
    args
  );
  return {
    results: rows.map((r) => ({
      conversationId: r.conversation_id, id: r.id, seq: n(r.seq), sender: r.sender, kind: r.kind,
      body: String(r.body).slice(0, 200), createdAt: n(r.created_at),
      ...(shopSide(viewer.side) && shopSide(r.sender) ? { senderUserId: r.sender_user_id } : {}),
    })),
  };
}

return { setBlock, report, search };
};
