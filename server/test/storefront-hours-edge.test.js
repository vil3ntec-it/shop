'use strict';
/**
 * PRES-06 (§۳۱.۳): ساعتِ کاریِ ویترین در لبه‌ها.
 *
 *   ۱) عبور از نیمه‌شب: «جمعه ۲۰:۰۰ تا شنبه ۰۲:۰۰» = بازهٔ جمعه تا ۲۴ + بازهٔ شنبه از ۰
 *      (قراردادِ `weeklyHoursOf`: «شبِ پس از ۲۴ در روزِ بعد»)؛ لبه‌ها نیم‌باز [آغاز، پایان).
 *   ۲) روزِ هفته و تاریخ از ساعتِ محلیِ دکان است، نه UTC: شنبه ۰۰:۳۰ِ کابل = جمعه ۲۰:۰۰ِ UTC.
 *      تعطیلیِ استثناییِ شنبه فقط بخشِ پس از نیمه‌شب را می‌بندد.
 *   ۳) Asia/Kabul ساعتِ تابستانی ندارد: در همهٔ ماه‌ها (و روزهای جابه‌جاییِ اروپا) +۰۴:۳۰ است.
 *   ۴) منطقه‌ای که تابستانی دارد (Europe/Berlin): ۰۹:۰۰ِ محلی در زمستان و تابستان باز است،
 *      هرچند لحظهٔ UTCاش یک ساعت جابه‌جا می‌شود؛ روزِ جابه‌جایی هم درست است.
 *   ۵) تغییرِ منطقهٔ زمانی، وضعیتِ همان لحظه را با ساعتِ محلیِ تازه حساب می‌کند.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const sf = require('../src/lib/storefront');

const KABUL_MIN = 270;   // +04:30
/** لحظهٔ UTCِ یک ساعتِ محلیِ کابل. ماه ۱..۱۲. */
const kabul = (y, m, d, hh, mm) => Date.UTC(y, m - 1, d, hh, mm) - KABUL_MIN * 60_000;
/** دستگاهِ دکان همین لحظه دیده شده ⇒ نشانِ «باز» یعنی open_online؛ بسته ⇒ closed. */
const badge = (row, at) => sf.presenceOf({ presence_mode: 'auto', closed_dates: [], ...row }, at, at).badge;

//  ۲۳ اکتبرِ ۲۰۲۶ جمعه است (dow=5)، ۲۴ اکتبر شنبه (dow=6)
const LATE = { timezone: 'Asia/Kabul', weekly_hours: { 5: [[1200, 1440]], 6: [[0, 120]] } };

test('PRES-06: عبور از نیمه‌شب — جمعه ۲۰:۰۰ تا شنبه ۰۲:۰۰، لبه‌ها نیم‌باز', () => {
  const cases = [
    [kabul(2026, 10, 23, 19, 59), 'closed', 5, '19:59'],
    [kabul(2026, 10, 23, 20, 0), 'open_online', 5, '20:00'],
    [kabul(2026, 10, 23, 23, 59), 'open_online', 5, '23:59'],
    [kabul(2026, 10, 24, 0, 0), 'open_online', 6, '00:00'],
    [kabul(2026, 10, 24, 1, 59), 'open_online', 6, '01:59'],
    [kabul(2026, 10, 24, 2, 0), 'closed', 6, '02:00'],
    //  شنبه شبِ بعد بازه‌ای ندارد ⇒ بسته
    [kabul(2026, 10, 24, 21, 0), 'closed', 6, '21:00'],
  ];
  for (const [at, want, dow, time] of cases) {
    const p = sf.localParts(at, 'Asia/Kabul');
    assert.equal(p.dow, dow, time);
    assert.equal(p.time, time);
    assert.equal(badge(LATE, at), want, `${time} dow=${dow}`);
  }
  const after = sf.presenceOf({ ...LATE, closed_dates: [] }, kabul(2026, 10, 24, 0, 30), kabul(2026, 10, 24, 0, 30));
  assert.deepEqual(after.today, [[0, 120]], 'پس از نیمه‌شب «امروز» شنبه است');
  assert.equal(after.localTime, '00:30');
});

test('PRES-06: روز و تاریخ از ساعتِ محلیِ دکان؛ تعطیلیِ شنبه فقط پس از نیمه‌شب را می‌بندد', () => {
  const at = kabul(2026, 10, 24, 0, 30);
  assert.equal(at, Date.UTC(2026, 9, 23, 20, 0), 'شنبه ۰۰:۳۰ِ کابل = جمعه ۲۰:۰۰ِ UTC');
  assert.equal(new Date(at).getUTCDay(), 5);
  assert.deepEqual(sf.localParts(at, 'Asia/Kabul'), { dow: 6, minutes: 30, date: '2026-10-24', time: '00:30' });

  const holiday = { ...LATE, closed_dates: ['2026-10-24'] };
  assert.equal(badge(holiday, kabul(2026, 10, 23, 23, 30)), 'open_online', 'جمعه شب هنوز جمعه است');
  assert.equal(badge(holiday, at), 'closed', 'پس از نیمه‌شب تعطیلیِ شنبه');
  assert.equal(sf.presenceOf(holiday, at, at).holiday, true);
  //  تعطیلیِ جمعه برعکس: شبِ جمعه بسته، پس از نیمه‌شب باز
  const fri = { ...LATE, closed_dates: ['2026-10-23'] };
  assert.equal(badge(fri, kabul(2026, 10, 23, 23, 30)), 'closed');
  assert.equal(badge(fri, at), 'open_online');
});

test('PRES-06: Asia/Kabul ساعتِ تابستانی ندارد — همهٔ سال +۰۴:۳۰', () => {
  const instants = [];
  for (let m = 0; m < 12; m++) instants.push(Date.UTC(2026, m, 15, 12, 0));
  //  روزهای جابه‌جاییِ اروپا و آمریکا در ۲۰۲۶، پیش و پس از ساعتِ جابه‌جایی
  for (const [mo, d] of [[2, 8], [2, 29], [9, 25], [10, 1]]) {
    instants.push(Date.UTC(2026, mo, d, 0, 30), Date.UTC(2026, mo, d, 3, 30));
  }
  for (const at of instants) {
    const p = sf.localParts(at, 'Asia/Kabul');
    const local = new Date(at + KABUL_MIN * 60_000);
    assert.equal(p.minutes, local.getUTCHours() * 60 + local.getUTCMinutes(), new Date(at).toISOString());
    assert.equal(p.date, local.toISOString().slice(0, 10));
    assert.equal(p.dow, local.getUTCDay());
  }
  //  ۰۸:۰۰ تا ۱۷:۰۰ِ هر روز؛ ۰۸:۰۰ِ محلی در دی و تیر همان ۰۳:۳۰ِ UTC است
  const day = { timezone: 'Asia/Kabul', weekly_hours: Object.fromEntries([0, 1, 2, 3, 4, 5, 6].map((d) => [d, [[480, 1020]]])) };
  for (const m of [0, 6]) {
    assert.equal(badge(day, Date.UTC(2026, m, 15, 3, 29)), 'closed');
    assert.equal(badge(day, Date.UTC(2026, m, 15, 3, 30)), 'open_online');
  }
});

test('PRES-06: منطقهٔ تابستانی‌دار (Europe/Berlin) با ساعتِ محلی حساب می‌شود، روزِ جابه‌جایی هم', () => {
  const berlin = { timezone: 'Europe/Berlin', weekly_hours: Object.fromEntries([0, 1, 2, 3, 4, 5, 6].map((d) => [d, [[540, 1020]]])) };
  //  زمستان +۰۱:۰۰ ⇒ ۰۹:۰۰ِ محلی = ۰۸:۰۰ِ UTC؛ تابستان +۰۲:۰۰ ⇒ ۰۷:۰۰ِ UTC
  assert.equal(badge(berlin, Date.UTC(2026, 0, 15, 7, 59)), 'closed');
  assert.equal(badge(berlin, Date.UTC(2026, 0, 15, 8, 0)), 'open_online');
  assert.equal(badge(berlin, Date.UTC(2026, 6, 15, 6, 59)), 'closed');
  assert.equal(badge(berlin, Date.UTC(2026, 6, 15, 7, 0)), 'open_online');
  //  ۲۹ مارسِ ۲۰۲۶: ۰۲:۰۰ِ محلی به ۰۳:۰۰ می‌پرد؛ ۰۰:۵۹ِ UTC = ۰۱:۵۹ و ۰۱:۰۰ِ UTC = ۰۳:۰۰
  assert.equal(sf.localParts(Date.UTC(2026, 2, 29, 0, 59), 'Europe/Berlin').time, '01:59');
  assert.equal(sf.localParts(Date.UTC(2026, 2, 29, 1, 0), 'Europe/Berlin').time, '03:00');
  //  ۲۵ اکتبرِ ۲۰۲۶: ۰۳:۰۰ِ محلی به ۰۲:۰۰ برمی‌گردد
  assert.equal(sf.localParts(Date.UTC(2026, 9, 25, 0, 30), 'Europe/Berlin').time, '02:30');
  assert.equal(sf.localParts(Date.UTC(2026, 9, 25, 1, 30), 'Europe/Berlin').time, '02:30');
  assert.equal(badge(berlin, Date.UTC(2026, 9, 25, 7, 0)), 'closed', '۰۸:۰۰ِ محلی پس از برگشت');
  assert.equal(badge(berlin, Date.UTC(2026, 9, 25, 8, 0)), 'open_online', '۰۹:۰۰ِ محلی پس از برگشت');
});

test('PRES-06: تغییرِ منطقهٔ زمانی، همان لحظه را با ساعتِ محلیِ تازه می‌سنجد', () => {
  const hours = Object.fromEntries([0, 1, 2, 3, 4, 5, 6].map((d) => [d, [[480, 1020]]]));
  const at = kabul(2026, 10, 22, 8, 15);   // ۰۸:۱۵ِ کابل = ۰۳:۴۵ِ UTC
  assert.equal(badge({ timezone: 'Asia/Kabul', weekly_hours: hours }, at), 'open_online');
  assert.equal(badge({ timezone: 'UTC', weekly_hours: hours }, at), 'closed');
  const p = sf.presenceOf({ timezone: 'UTC', weekly_hours: hours, closed_dates: [] }, at, at);
  assert.equal(p.timezone, 'UTC');
  assert.equal(p.localTime, '03:45');
  //  بی منطقه ⇒ پیش‌فرضِ Asia/Kabul
  assert.equal(sf.presenceOf({ weekly_hours: hours, closed_dates: [] }, at, at).localTime, '08:15');
});
