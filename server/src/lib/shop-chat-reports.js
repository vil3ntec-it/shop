'use strict';
/**
 * چتِ مشتری ↔ دکان — گزارش‌های سوءاستفاده برای مدیرِ سامانه (`routes/admin-shop-chat.js`، v3 §۳۳.۳).
 * جدا از `lib/shop-chat.js` تا آن زیرِ سقفِ اندازه بماند (`tools/quality`)؛ رفتار همان. مدیر فقط رونوشتِ
 * پیامِ گزارش‌شده را می‌بیند، نه گفت‌وگو را.
 */
const { one, many } = require('../db');
const { badRequest, notFound } = require('../middleware/errors');

const n = (x) => Number(x || 0);
const asJson = (x) => {
  if (x && typeof x === 'object') return x;
  try { return JSON.parse(x) || {}; } catch { return {}; }
};

function limitOf(v) {
  if (v === undefined || v === null || v === '') return 50;
  const x = Number(v);
  if (!Number.isSafeInteger(x) || x < 1 || x > 200) throw badRequest('عدد در بازهٔ مجاز نیست', 'bad_number');
  return x;
}

async function reportsForAdmin({ status = 'open', limit } = {}) {
  const st = ['open', 'reviewed', 'all'].includes(status) ? status : 'open';
  const lim = limitOf(limit);
  const rows = await many(
    `SELECT r.*, s.name AS shop_name FROM shop_chat_reports r JOIN shops s ON s.id = r.shop_id
      ${st === 'all' ? '' : 'WHERE r.status = $2'} ORDER BY r.created_at DESC LIMIT $1`,
    st === 'all' ? [lim] : [lim, st]
  );
  return rows.map((r) => ({
    id: r.id, shopId: r.shop_id, shopName: r.shop_name, conversationId: r.conversation_id,
    messageId: r.message_id || null, reporter: r.reporter, reason: r.reason, note: r.note,
    snapshot: asJson(r.snapshot), status: r.status, createdAt: n(r.created_at),
  }));
}

async function reviewReport(id) {
  const r = await one("UPDATE shop_chat_reports SET status='reviewed' WHERE id=$1 RETURNING id", [String(id || '')]);
  if (!r) throw notFound('این گزارش پیدا نشد', 'report_not_found');
  return { id: r.id, status: 'reviewed' };
}

module.exports = { reportsForAdmin, reviewReport };
