'use strict';
/**
 * ABUSE-01 در پنلِ مرکزی (پنل ۱.۵۰.۹۷، server#147) — کنارِ `lib/market-trust.js` (آن پرونده به سقفِ اندازه رسیده):
 *
 *   ۱) «انجام‌دهنده» ِ نوشتنِ پرونده: از راهِ کلیدِ سرویس شناسهٔ **کاربرِ پنل** (`req.actor.id`، مثلاً `panel:3`) و نامش
 *      (`X-Actor-Name`، کران‌دار و پاک‌شده) — نه شناسهٔ مشترکِ کلید که برای همهٔ کاربرانِ پنل یکی است. مدیرِ مستقیمِ کنسولِ
 *      خودِ سرورِ حساب همان امروز: شناسهٔ خودش، بی نام.
 *   ۲) نمای کم‌ترِ پرونده برای نقشِ زیرِ ارشد (`admin`، `viewer`؛ مالی اصلاً به این درها نمی‌رسد — `lib/admin-roles.js`):
 *      نه نشانهٔ پوشیدهٔ شاکی، نه سابقهٔ گزارش‌هایش، نه پرونده‌های هم‌متن، نه مدرک؛ و **هیچ شناسهٔ آدم** (کاربرِ دکان یا
 *      مدیر) در تاریخچه، تصمیم، اعتراض و جریمه — همان شناسه‌ها از راهِ دیگر نشت نکنند. «مسئول» و نامش می‌مانند (کارِ صف).
 *      نوشتن (مسئول، تصمیم، رأی، برداشتن) همچنان فقط ارشد + دلیل (`routes/admin-trust.js`).
 */
const NAME_MAX = 80;
//  نویسهٔ کنترلی و جهت‌دهیِ پنهان (RLO، LRI…) — نامی که در صف وارونه یا فریبنده نشان داده شود
const HIDDEN = /[\u0000-\u001f\u007f-\u009f؜​-‏‪-‮⁠-⁩﻿]/g;

/** نامِ خواندنیِ کاربرِ پنل: پاک، فاصلهٔ یکی، حداکثر ۸۰ نویسه. */
function cleanName(raw) {
  return Array.from(String(raw == null ? '' : raw).replace(HIDDEN, '').replace(/\s+/g, ' ').trim()).slice(0, NAME_MAX).join('');
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

/** پروندهٔ `adminView` برای نقشِ زیرِ ارشد. */
function caseView(c) {
  const out = without(c, ['reporterHint', 'reporterStats', 'sameText', 'evidence']);
  if (c.decision) out.decision = without(c.decision, ['by']);
  if (c.appeal) out.appeal = without(c.appeal, ['by', 'decidedBy']);
  out.sanctions = (c.sanctions || []).map(sanction);
  out.history = (c.history || []).map((e) => {
    const { actorId, ...rest } = e;   // eslint-disable-line no-unused-vars
    return { ...rest, data: without(e.data || {}, ['assignee']) };
  });
  return out;
}

module.exports = { NAME_MAX, cleanName, actorOf, fullView, caseView, sanction };
