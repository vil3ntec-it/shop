'use strict';
/**
 * سنجاقِ پیامِ چتِ دکان (§۳۳.۱، CHAT-14؛ جدولِ `shop_chat_pins`، مهاجرتِ ۱۴۱) — جدا از `lib/shop-chat.js`
 * تا آن زیرِ سقفِ اندازه بماند (`tools/quality`). کمک‌های همان پرونده (قفلِ گفت‌وگو و پیام، کلیدِ
 * بیننده، نما) تزریق می‌شوند؛ درها همان `chat.pin` اند.
 *
 * هر طرف سنجاقِ خودش را دارد: سنجاقِ دکان را همهٔ کارمندانِ همان دکان می‌بینند و مشتری نه؛ سنجاقِ
 * مشتری فقط برای خودش. ⛔ چیزی به طرفِ دیگر فرستاده نمی‌شود (نه پیام، نه پوش)؛ دستگاه‌های دیگرِ همان
 * طرف با long-poll می‌فهمند.
 */
const { tx, now } = require('../db');
const { notify } = require('./chat-wait');
const { notFound, conflict } = require('../middleware/errors');

//  سنجاقِ هر طرف در هر گفت‌وگو — کم و بسته، تا فهرستِ سنجاق خودش صندوق نشود
const MAX_PINS = 5;

module.exports = ({ rowOf, lockedMessage, viewerKey, viewOf, n, asJson }) => {
  /** ستونِ `pinned` ِ نمای گفت‌وگو؛ `mine` همان نامِ ثابتِ طرفِ بیننده در `viewSql` است (هرگز از درخواست). */
  const pinnedSql = (mine) => `(SELECT COALESCE(json_agg(json_build_object('messageId', p.message_id, 'seq', pm.seq, 'at', p.created_at)
                                ORDER BY p.created_at DESC, p.message_id), '[]'::json)
         FROM shop_chat_pins p JOIN shop_chat_messages pm ON pm.id = p.message_id
        WHERE p.conversation_id = c.id AND p.side = '${mine}' AND pm.deleted_at IS NULL) AS pinned`;

  /** فقط سنجاق‌های طرفِ خودِ بیننده — بی متن؛ تازه‌ترین اول */
  const pinnedView = (raw) => (Array.isArray(raw) ? raw : Object.values(asJson(raw)))
    .map((x) => ({ messageId: x.messageId, seq: n(x.seq), pinnedAt: n(x.at) }));

  /** «حذف برای من»: مشتری تنها بینندهٔ طرفِ خودش است ⇒ پیامِ پنهان‌شده سنجاقِ او هم نیست */
  async function dropHidden(c, viewer, convId, messageId) {
    if (viewer.side !== 'customer') return;
    await c.query("DELETE FROM shop_chat_pins WHERE conversation_id=$1 AND side='customer' AND message_id=$2", [convId, messageId]);
  }

  /** «حذف برای همه» سنجاقِ هر دو طرف را برمی‌دارد */
  const dropAll = (c, messageId) => c.query('DELETE FROM shop_chat_pins WHERE message_id=$1', [messageId]);

  /**
   * سنجاق / برداشتنِ سنجاق. فقط پیامِ زندهٔ همین گفت‌وگو که از دیدِ همین بیننده پنهان نشده (پیامِ
   * گفت‌وگو یا دکانِ دیگر، پاک‌شده یا «حذف برای من» ⇒ همان ۴۰۴). حداکثر `MAX_PINS` برای هر طرف،
   * زیرِ قفلِ ردیفِ گفت‌وگو. تکرار بی‌اثر است.
   */
  async function pin(viewer, id, messageId, on) {
    const out = await tx(async (c) => {
      const conv = await rowOf(viewer, id, { c, lock: true });
      const m = await lockedMessage(c, conv, messageId);
      if (!on) {
        const r = await c.query('DELETE FROM shop_chat_pins WHERE conversation_id=$1 AND side=$2 AND message_id=$3',
          [conv.id, viewer.side, m.id]);
        return { changed: r.rowCount > 0, convId: conv.id };
      }
      const hidden = (await c.query('SELECT 1 FROM shop_chat_hidden WHERE message_id=$1 AND viewer=$2',
        [m.id, viewerKey(viewer)])).rows[0];
      if (m.deleted_at || hidden) throw notFound('این پیام پیدا نشد', 'message_not_found');
      const have = (await c.query('SELECT message_id FROM shop_chat_pins WHERE conversation_id=$1 AND side=$2',
        [conv.id, viewer.side])).rows;
      if (have.some((x) => x.message_id === m.id)) return { changed: false, convId: conv.id };
      if (have.length >= MAX_PINS) throw conflict(`حداکثر ${MAX_PINS} پیام سنجاق می‌شود؛ اول یکی را بردارید`, 'pin_limit');
      //  سنجاق‌کنندهٔ کارمند ثبت می‌شود؛ مشتری شناسهٔ کاربر ندارد (طرف‌ها فقط همین دو‌اند)
      await c.query(
        `INSERT INTO shop_chat_pins (conversation_id, message_id, side, shop_id, pinned_by_user_id, created_at)
         VALUES ($1,$2,$3,$4,$5,$6)`,
        [conv.id, m.id, viewer.side, conv.shop_id, viewer.side === 'customer' ? '' : viewer.userId, now()]
      );
      return { changed: true, convId: conv.id };
    });
    if (out.changed) notify(out.convId);
    return viewOf(viewer, out.convId);
  }

  return { MAX_PINS, pinnedSql, pinnedView, dropHidden, dropAll, pin };
};
