'use strict';
/**
 * خبرهای شعبهٔ غیرِ اصلی — فازِ ۳ب ِ `docs/BRANCHES-fa.md` (مهاجرتِ ۹۳۰؛ پرچمِ خاموشِ `shop.branches`).
 *
 * «یک منطق، دو جدول» ِ خودِ `lib/events.js`: همان `build` روی `shop_branch_events`/`shop_branch_event_reads` با کلیدِ
 * `branch_key` = `<دکان>#<شعبه>` — همان سنجشِ نوع/اندازه، همان «تکراری نه» با `clientId`، همان خواندن و «دیده شد».
 * ⛔ `lib/events.js` (مشترک با پمپ) و `shop_events` ِ امروز دست نمی‌خورند: شعبهٔ اصلی همان دفترِ امروز را دارد.
 * ⛔ کلید فقط از `branches.partitionOf` پس از سنجشِ دسترسی (`branch-rest.scope`)، هرگز از درخواست.
 * ⛔ زنگِ گوشی فقط برای اعضایی که به همان شعبه می‌رسند (صاحب و منتسب‌های فعالِ همان شعبه)، نه کلِ دکان. پوشِ درونیِ
 *    `build` با شناسهٔ بخش هیچ دستگاهی پیدا نمی‌کند (`push_tokens.shop_id` شناسهٔ دکان است) ⇒ همین‌جا با فهرستِ همان شعبه.
 */
const events = require('./events');
const push = require('./push');
const bk = require('./branch-key');
const { many } = require('../db');
const { SHOP } = require('./tenancy');

const T = Object.freeze({
  ...SHOP,
  eventsTable: 'shop_branch_events',
  eventReadsTable: 'shop_branch_event_reads',
  tenantKey: 'branch_key',
});
const store = events.build(T);

/** کاربرانی که به این شعبه می‌رسند (صاحب، و عضوِ فعالِ منتسب به همین شعبه) */
async function reachers(key) {
  const rows = await many(
    `SELECT m.user_id FROM shop_members m
      WHERE m.shop_id=$1 AND m.status='active'
        AND (m.role='owner' OR EXISTS (SELECT 1 FROM shop_member_branches b
                                        WHERE b.shop_id=m.shop_id AND b.user_id=m.user_id AND b.branch_id=$2))`,
    [bk.shopOf(key), bk.branchOf(key)],
  );
  return rows.map((r) => r.user_id);
}

/** همان پیامِ `events.notify` (یک زنگ برای یک دسته، نه برای کننده) — فقط برای اعضای همان شعبه */
async function notify(key, userId, saved) {
  const worthy = saved.filter((e) => events.PUSH_KINDS.includes(e.kind));
  if (!worthy.length) return { sent: 0, skipped: 'nothing_worthy' };
  try {
    const first = worthy[0];
    const more = worthy.length - 1;
    return await push.sendTo(
      { shopId: bk.shopOf(key), app: SHOP.app, exceptUserId: userId, onlyUserIds: await reachers(key) },
      {
        title: SHOP.eventTitles[first.kind] || `خبرِ ${SHOP.label}`,
        body: more > 0 ? `${first.title || first.body || ''} و ${more} خبرِ دیگر` : (first.title || first.body || ''),
        channel: 'events',
        data: { type: 'event', kind: first.kind, count: String(worthy.length) },
      }
    );
  } catch (err) {
    console.error('[branch-events:push]', err.message);
    return { sent: 0, error: err.message };
  }
}

/** ثبت در دفترِ همین شعبه (همان قاعده‌های `events.record`) و زنگ به اعضای همان شعبه */
async function record(key, ctx, items) {
  const out = await store.record({ ...ctx, tenantId: key }, items);
  await notify(key, ctx.userId || '', out.events);
  return out;
}

module.exports = {
  T, record, notify, reachers,
  list: store.list, seenAt: store.seenAt, markSeen: store.markSeen, unreadCount: store.unreadCount,
};
