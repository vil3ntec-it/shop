'use strict';
/**
 * ABUSE-01 در پنلِ مرکزی (پنل ۱.۵۰.۹۷، server#147) — کنارِ `lib/market-trust.js` (آن پرونده به سقفِ اندازه رسیده):
 *
 *   ۱) «انجام‌دهنده» ِ نوشتنِ پرونده: از راهِ کلیدِ سرویس شناسهٔ **کاربرِ پنل** (`req.actor.id`، مثلاً `panel:3`) و نامش
 *      (`X-Actor-Name`، کران‌دار و پاک‌شده) — نه شناسهٔ مشترکِ کلید که برای همهٔ کاربرانِ پنل یکی است. مدیرِ مستقیمِ کنسولِ
 *      خودِ سرورِ حساب همان امروز: شناسهٔ خودش، بی نام.
 *   ۲) نمای کم‌ترِ پرونده برای نقشِ زیرِ ارشد (`admin`، `viewer`؛ مالی اصلاً به این درها نمی‌رسد — `lib/admin-roles.js`):
 *      نه نشانهٔ پوشیدهٔ شاکی، نه سابقهٔ گزارش‌هایش، نه پرونده‌های هم‌متن (`sameText`، `duplicateOf`، پرچمِ `same_*` و رخدادِ
 *      `flagged` ِ آن)، نه مدرک (نه حتی شناسه‌اش در رخداد)؛ و **هیچ شناسهٔ آدم** (کاربرِ دکان یا مدیر) در تاریخچه، تصمیم، اعتراض
 *      و جریمه — همان شناسه‌ها از راهِ دیگر نشت نکنند. «مسئول» و نامش می‌مانند (کارِ صف)، مگر همان تصمیم‌گیرنده باشد. صف
 *      (`GET /cases`) هم از همین نما می‌گذرد ([queueRow]).
 *      نوشتن (مسئول، تصمیم، رأی، برداشتن) همچنان فقط ارشد + دلیل (`routes/admin-trust.js`).
 */
const NAME_MAX = 80;
//  نویسهٔ کنترلی و جهت‌دهیِ پنهان (RLO، LRI، ALM…) — نامی که در صف وارونه یا فریبنده نشان داده شود — و نویسه‌های نامرئیِ دیگر:
//  soft hyphen، جداکنندهٔ مغولی، پُرکنندهٔ هانگول، Unicode Tags (متنِ پنهان برای ابزار/مدل). ⛔ نیم‌فاصله (U+200C) و ZWJ (U+200D)
//  نه: بخشی از نوشتنِ درستِ نامِ فارسی‌اند («محمد‌رضا»).
const HIDDEN = /[\u0000-\u001f\u007f-\u009f­؜ᅟᅠ᠎​‎‏‪-‮⁠-⁩ㅤ﻿ﾠ\u{e0000}-\u{e007f}]/gu;
//  بیش از دو نشانهٔ ترکیبیِ پشتِ هم (Zalgo: نام از ردیفش بیرون می‌زند)؛ دوتا برای اعرابِ فارسی (تشدید + فتحه) بس است
const MARKS = /(\p{M}{2})\p{M}+/gu;

/** نامِ خواندنیِ کاربرِ پنل: پاک، فاصلهٔ یکی، حداکثر ۸۰ نویسه. */
function cleanName(raw) {
  return Array.from(String(raw == null ? '' : raw).replace(HIDDEN, '').replace(MARKS, '$1').replace(/\s+/g, ' ').trim())
    .slice(0, NAME_MAX).join('');
}

/** «چه کسی» برای `assignee`، `decided_by`، `appeal_decided_by`، `created_by`/`lifted_by` و `actor_id` ِ رخدادها. */
function actorOf(req) {
  if (req.service && req.actor && req.actor.id) return { id: String(req.actor.id), name: cleanName(req.actor.name) };
  return { id: String(req.admin.id), name: '' };
}

/** نمای کامل فقط برای ارشد (انسان یا انجام‌دهندهٔ ارشدِ پل). */
const fullView = (req) => Boolean(req.admin && req.admin.role === 'superadmin');

const without = (o, keys) => Object.fromEntries(Object.entries(o).filter(([k]) => !keys.includes(k)));

const sanction = (s) => without(s, ['createdBy', 'liftedBy']);

/** پرچم‌های «هم‌متن/هم‌IP» (`same_text`، `same_ip`) همان پرونده‌های هم‌متن و سابقهٔ شاکی‌اند که پنهان است */
const flagsOf = (flags) => (Array.isArray(flags) ? flags.filter((f) => !(typeof f === 'string' && f.startsWith('same_'))) : []);

/** دادهٔ رخداد: نه شناسهٔ مسئول، نه پروندهٔ هم‌متن، نه شناسهٔ مدرک (مدرک فقط ارشد) */
function eventData(d) {
  const out = without(d || {}, ['assignee', 'duplicateOf', 'evidenceId']);
  if ('flags' in out) out.flags = flagsOf(out.flags);
  return out;
}

/**
 * «مسئول» ِ پروندهٔ بی مسئول با تصمیم خودکار همان تصمیم‌گیرنده می‌شود (`market-trust.decide`) ⇒ همان شناسهٔ پنهانِ «تصمیم/رأی»؛
 * برای زیرِ ارشد خالی (نامش هم). مسئولی که جدا از تصمیم‌گیرنده است می‌ماند (کارِ صف).
 */
function hideDecider(out, deciders) {
  if (out.assignee && deciders.includes(out.assignee)) Object.assign(out, { assignee: '', assigneeName: '' });
  return out;
}

/** پروندهٔ `adminView` برای نقشِ زیرِ ارشد. */
function caseView(c) {
  const out = without(c, ['reporterHint', 'reporterStats', 'sameText', 'evidence', 'duplicateOf']);
  out.flags = flagsOf(c.flags);
  if (c.decision) out.decision = without(c.decision, ['by']);
  if (c.appeal) out.appeal = without(c.appeal, ['by', 'decidedBy']);
  out.sanctions = (c.sanctions || []).map(sanction);
  out.history = (c.history || []).map((e) => {
    const { actorId, ...rest } = e;   // eslint-disable-line no-unused-vars
    return { ...rest, data: eventData(e.data) };
  }).filter((e) => !(e.type === 'flagged' && !(e.data.flags || []).length));   //  پرچمِ خودکار فقط هم‌متن/هم‌IP ⇒ رخدادش هم نه
  return hideDecider(out, [c.decision && c.decision.by, c.appeal && c.appeal.decidedBy].filter(Boolean));
}

/** ردیفِ صف (`market-trust.queue`) برای نقشِ زیرِ ارشد: [row] شکلِ `adminRow`، [raw] ردیفِ پایگاه (برای تصمیم‌گیرنده‌ها) */
function queueRow(row, raw) {
  const out = without(row, ['duplicateOf']);
  out.flags = flagsOf(row.flags);
  return hideDecider(out, [raw.decided_by, raw.appeal_decided_by].filter(Boolean));
}

module.exports = { NAME_MAX, cleanName, actorOf, fullView, caseView, queueRow, sanction };
