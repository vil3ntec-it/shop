'use strict';
/**
 * SUB-01/SUB-03 — مرزهای اشتراکِ دکان با ساعتِ سرور: نیمه‌شبِ کابل، پایانِ ماه، شروعِ آینده در دورهٔ آزمایشی.
 *
 * دو باگِ واقعی که دکانِ پول‌داده را زودتر از حقش می‌بست:
 *  ۱) «۱ ماهه یعنی همین روز در ماهِ بعد» (`plans.endOfPeriod`) با تقویمِ UTC حساب می‌شد. دکانی که اشتراکش
 *     ساعتِ ۰۰:۰۰ تا ۰۴:۲۹ ِ کابل ِ روزِ یکمِ ماه تمام می‌شود (در UTC هنوز روزِ آخرِ ماهِ پیش است)، با تمدیدِ
 *     ماهانه تا ۲۹ ِ همان ماه می‌گرفت، نه یکمِ ماهِ بعد: سه روز (اسفند/فوریه) یا یک روز کم.
 *  ۲) اشتراکی که مدیر با شروعِ آینده (`startsAt`) برای دکانِ در دورهٔ آزمایشی می‌گذاشت، همان لحظه دورهٔ آزمایشی
 *     را «مصرف‌شده» می‌کرد ⇒ دکان تا رسیدنِ شروع نه آزمایشی داشت نه اشتراک (`free`).
 * و قفلِ مرزهای دیگر: لحظهٔ پایان، لحظهٔ پایانِ مهلت و `expireDue` با همان قاعدهٔ `stateOf`.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, now } = require('../src/db');
const subs = require('../src/lib/subscriptions');
const plans = require('../src/lib/plans');
const { entitlementOf } = require('../src/lib/entitlement');
const { PAID_KEYS } = require('../src/lib/features');

const DAY = subs.DAY;
const PAID = PAID_KEYS[0];
/** کابل همیشه UTC+04:30 است (بی ساعتِ تابستانی) */
const kabul = (y, mo, d, hh = 0, mm = 0) => Date.UTC(y, mo - 1, d, hh, mm) - (4 * 60 + 30) * 60_000;
const iso = (ms) => new Date(ms + (4 * 60 + 30) * 60_000).toISOString().slice(0, 16).replace('T', ' ');

test.before(async () => { await h.start(); });
test.after(async () => { await h.stop(); });

async function shop(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { token: u.accessToken, shopId: made.body.shop.id };
}

/** اشتراکِ زنده‌ای که درست در `endsAt` تمام می‌شود، سپس تمدید با پلنِ ماهانه/ساله ⇒ پایانِ تازه */
async function renewFrom(endsAt, plan) {
  const s = await shop(`تمدید-${plan}-${endsAt}`);
  await subs.grant(s.shopId, { plan: 'custom', endsAt });
  const row = await subs.grant(s.shopId, { plan });
  return Number(row.ends_at);
}

test('SUB-03: تمدیدِ ماهانهٔ اشتراکی که بامدادِ یکمِ ماه (کابل) تمام می‌شود ⇒ یکمِ ماهِ بعد، نه ۲۹ ِ همان ماه', async () => {
  //  ۱ حمل ۱۴۰۵‌ی نیست؛ تقویمِ پلن میلادی است — فقط روزِ تقویمیِ دکان (کابل) ملاک است، نه UTC
  const cases = [
    [kabul(2027, 3, 1, 2, 0), 'm1', kabul(2027, 4, 1, 2, 0)],     //  UTC هنوز ۲۸ فوریه ⇒ پیش‌تر ۲۹ مارس (سه روز کم)
    [kabul(2027, 5, 1, 1, 15), 'm1', kabul(2027, 6, 1, 1, 15)],   //  UTC ۳۰ آوریل ⇒ پیش‌تر ۳۱ مه (یک روز کم)
    [kabul(2027, 10, 1, 4, 29), 'm1', kabul(2027, 11, 1, 4, 29)], //  آخرین دقیقهٔ پنجرهٔ ۴:۳۰
    [kabul(2027, 12, 1, 0, 0), 'm1', kabul(2028, 1, 1, 0, 0)],    //  نیمه‌شبِ کابل، گذر از سال
    [kabul(2027, 3, 1, 3, 0), 'm6', kabul(2027, 9, 1, 3, 0)],     //  شش‌ماهه هم
  ];
  for (const [base, plan, want] of cases) {
    const got = await renewFrom(base, plan);
    assert.equal(iso(got), iso(want), `${plan} از ${iso(base)} (کابل)`);
  }
});

test('SUB-03: بیرون از پنجرهٔ بامداد و در سرریزِ پایانِ ماه هیچ دکانی کمتر از پیش نمی‌گیرد', async () => {
  //  روزِ کابل = روزِ UTC ⇒ همان پاسخِ پیشین
  assert.equal(iso(await renewFrom(kabul(2027, 1, 15, 12, 0), 'm1')), iso(kabul(2027, 2, 15, 12, 0)));
  //  سرریز (۳۱ ژانویه در UTC ⇒ ۳ مارس) همان پیشین می‌ماند: قاعدهٔ تازه فقط روزِ کم‌شده را پس می‌دهد
  const base = kabul(2027, 2, 1, 2, 0);
  assert.equal(await renewFrom(base, 'm1'), plans.endOfPeriod(base, 1, 'month'));
  //  ساله از یکمِ مارسِ سالِ کبیسه (UTC ۲۹ فوریه) ⇒ دستِ کم یکمِ مارسِ سالِ بعد
  const leap = kabul(2028, 3, 1, 2, 0);
  assert.ok(await renewFrom(leap, 'y1') >= kabul(2029, 3, 1, 2, 0));
  //  تمدید با روز (`days`) همان جمعِ ساده است و دست نمی‌خورد
  const s = await shop('روزشمار');
  await subs.grant(s.shopId, { plan: 'custom', endsAt: kabul(2027, 3, 1, 2, 0) });
  const r = await subs.grant(s.shopId, { plan: 'custom', days: 30 });
  assert.equal(Number(r.ends_at), kabul(2027, 3, 1, 2, 0) + 30 * DAY);
});

test('SUB-01: شروعِ آینده در دورهٔ آزمایشی، دوره را نمی‌سوزاند — دکان تا شروع «آزمایشی» است، بعد «اشتراک»', async () => {
  const s = await shop('شروع‌آینده');
  const before = await entitlementOf(s.shopId);
  assert.equal(before.source, 'trial');
  const start = before.trial.endsAt;   //  مدیر شروع را درست روی پایانِ دورهٔ آزمایشی گذاشت
  await subs.grant(s.shopId, { plan: 'custom', startsAt: start, endsAt: start + 30 * DAY });

  let ent = await entitlementOf(s.shopId);
  assert.equal(ent.subscription.phase, 'scheduled');
  assert.deepEqual([ent.source, ent.trial.active], ['trial', true], '⛔ دکانِ پول‌داده تا شروع از آزمایشی بیرون نمی‌افتد');
  assert.ok(ent.features.includes(PAID));
  ent = await entitlementOf(s.shopId, start - 1);
  assert.equal(ent.source, 'trial', 'یک میلی‌ثانیه پیش از شروع هنوز آزمایشی');
  ent = await entitlementOf(s.shopId, start);
  assert.deepEqual([ent.source, ent.subscription.phase, ent.trial.active], ['subscription', 'active', false], 'لحظهٔ شروع ⇒ اشتراک');
  ent = await entitlementOf(s.shopId, start + 30 * DAY + 1);
  assert.deepEqual([ent.source, ent.trial.active], ['free', false], '⛔ پس از اشتراک، دورهٔ آزمایشی زنده نمی‌شود');

  //  شروعی دیرتر از پایانِ آزمایشی: میانِ آن دو فقط رایگان، نه دسترسیِ بیشتر
  const s2 = await shop('شروع‌دیرتر');
  const end2 = (await entitlementOf(s2.shopId)).trial.endsAt;
  await subs.grant(s2.shopId, { plan: 'custom', startsAt: end2 + 5 * DAY, endsAt: end2 + 35 * DAY });
  assert.equal((await entitlementOf(s2.shopId, end2 + DAY)).source, 'free');
  assert.equal((await entitlementOf(s2.shopId, end2 + 5 * DAY)).source, 'subscription');

  //  شروعِ آیندهٔ معلق دوره را همچنان می‌سوزاند (تصمیمِ مدیر)
  const s3 = await shop('شروع‌معلق');
  const end3 = (await entitlementOf(s3.shopId)).trial.endsAt;
  const row3 = await subs.grant(s3.shopId, { plan: 'custom', startsAt: end3, endsAt: end3 + 30 * DAY });
  await subs.setStatus(row3.id, 'suspended', 'test');
  assert.deepEqual([(await entitlementOf(s3.shopId)).source, (await entitlementOf(s3.shopId)).trial.active], ['free', false]);
});

test('SUB-01: لحظهٔ پایان و پایانِ مهلت — `expireDue` درست همان مرزِ `stateOf` را دارد (نه یک میلی‌ثانیه زودتر)', async () => {
  const s = await shop('مرز');
  const row = await subs.grant(s.shopId, { plan: 'custom', days: 30, graceDays: 2 });
  const graceEnd = Number(row.ends_at) + 2 * DAY;
  assert.equal(await subs.expireDue(graceEnd), 0, 'آخرین لحظهٔ مهلت هنوز زنده است');
  assert.equal((await entitlementOf(s.shopId, graceEnd)).source, 'subscription');
  assert.ok(await subs.expireDue(graceEnd + 1) >= 1);
  const stored = (await query('SELECT status FROM subscriptions WHERE id=$1', [row.id])).rows[0];
  assert.equal(stored.status, 'expired');
  assert.ok(now() < graceEnd, 'ساعتِ واقعیِ سرور جلو نرفته؛ فقط `at` ِ آزمون');
});
