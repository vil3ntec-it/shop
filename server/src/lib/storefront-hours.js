'use strict';
/**
 * ساعتِ کار و وضعیتِ دکان برای مشتری (§۳۱.۱) — تابع‌های خالص؛ از `lib/storefront.js` جدا شد تا
 * زیرِ سقفِ اندازه بماند، رفتار بایت‌به‌بایت همان.
 */
const config = require('../config');

const asJson = (x, def) => {
  if (x && typeof x === 'object') return x;
  try { return JSON.parse(x); } catch { return def; }
};

/** روز، دقیقه و تاریخِ محلیِ دکان — ساعتِ گوشیِ مشتری مرجع نیست. */
function localParts(at, timezone) {
  const parts = Object.fromEntries(new Intl.DateTimeFormat('en-US', {
    timeZone: timezone, hourCycle: 'h23', weekday: 'short', year: 'numeric', month: '2-digit',
    day: '2-digit', hour: '2-digit', minute: '2-digit',
  }).formatToParts(new Date(at)).map((p) => [p.type, p.value]));
  const dow = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'].indexOf(parts.weekday);
  const hour = Number(parts.hour) % 24;
  return {
    dow, minutes: hour * 60 + Number(parts.minute),
    date: `${parts.year}-${parts.month}-${parts.day}`,
    time: `${String(hour).padStart(2, '0')}:${parts.minute}`,
  };
}

function scheduleOf(sf, at) {
  const hours = asJson(sf.weekly_hours, {});
  const closed = asJson(sf.closed_dates, []);
  const local = localParts(at, sf.timezone || 'Asia/Kabul');
  const today = Array.isArray(hours[String(local.dow)]) ? hours[String(local.dow)] : [];
  const set = Object.keys(hours).length > 0;
  const holiday = closed.includes(local.date);
  let open = null;   // null = ساعتِ کاری تنظیم نشده
  if (holiday) open = false;
  else if (set) open = today.some(([o, c]) => local.minutes >= o && local.minutes < c);
  return { set, open, holiday, today, local };
}

/**
 * سه چیزِ جدا (۳۱.۱؛ ۳۱.۲ بندِ ۷):
 *   badge            — open_online | open_unconfirmed | declared_until | closed | offline
 *   lastConfirmedAt  — آخرین تماسِ واقعیِ یکی از دستگاه‌های دکان با سرور، یا null
 *   orderAcceptance  — accepting | delayed | later. سرور سفارش را در هر سه
 *                      پایدار ثبت می‌کند (۳۱.۲ بندِ ۶)؛ این فقط به مشتری راست
 *                      می‌گوید کی بررسی می‌شود (WEB-03/25).
 */
function presenceOf(sf, at, lastSeenAt) {
  let mode = sf.presence_mode || 'auto';
  const until = sf.declared_until === null || sf.declared_until === undefined ? null : Number(sf.declared_until);
  if (mode === 'online_until' && !(until > at)) mode = 'auto';   // پایانِ اعلام ⇒ خودکار منقضی (۳۱.۲ بندِ ۵)
  const sched = scheduleOf(sf, at);
  const seen = Number(lastSeenAt) > 0 ? Number(lastSeenAt) : null;
  const age = seen === null ? Infinity : at - seen;
  const { staleMs, offlineMs } = config.storefront;
  const declaredOpen = mode === 'online' || mode === 'online_until';

  let badge;
  if (mode === 'closed' || (!declaredOpen && sched.open === false)) badge = 'closed';
  else if (mode === 'offline' || age > offlineMs) badge = 'offline';
  else if (mode === 'online_until') badge = 'declared_until';
  else badge = age <= staleMs ? 'open_online' : 'open_unconfirmed';

  const connection = age <= staleMs ? 'confirmed' : (age > offlineMs ? 'lost' : 'unconfirmed');
  let orderAcceptance = 'delayed';
  if (badge === 'closed' || mode === 'offline') orderAcceptance = 'later';
  else if (connection === 'confirmed') orderAcceptance = 'accepting';
  return {
    badge,
    mode,
    declaredUntil: mode === 'online_until' ? until : null,
    connection,
    lastConfirmedAt: seen,
    orderAcceptance,
    timezone: sf.timezone || 'Asia/Kabul',
    localTime: sched.local.time,
    hoursSet: sched.set,
    holiday: sched.holiday,
    today: sched.today,
    notice: sf.notice || '',
  };
}

module.exports = { localParts, scheduleOf, presenceOf };
