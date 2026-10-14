'use strict';
/**
 * F0-13 — مدتِ دورهٔ آزمایشی یک منبع دارد.
 *
 * پیش از این دو پیش‌فرضِ ناهمسان بود: `DEFAULT_CONFIG.trial_days = '7'`
 * (همان که سرِ هر راه‌اندازی در `app_config` کاشته می‌شود و **در عمل** همه
 * می‌بینند) و `TRIAL_DEFAULT.shop = '14'` (پشتوانهٔ `trialDaysOf`)؛ به‌علاوهٔ
 * `TRIAL_DAYS` در محیط (۷ در `.env.example`، ۱۴ در `docker-compose.yml`) که
 * هیچ جای کد نمی‌خواندش، و سه مسیرِ پاسخ (`/config`، `/plans`، `/me/plans`)
 * که با `|| 0` جدا می‌خواندند. حالا:
 *   • تنها جای مقدار: `app_config.trial_days` / `pump_trial_days` (پنلِ مدیر)
 *   • تنها پیش‌فرض: `plans.TRIAL_DEFAULT` — همان که کاشته می‌شود
 *   • تنها خواننده: `plans.trialDaysOf(app)`
 *
 * ⚠️ مقدار عوض نشده: دکان ۷ روز (همان که کاشته می‌شد)، پمپ ۳۰ روز. خودِ
 * عدد منتظرِ تصمیمِ D-03 است.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const plans = require('../src/lib/plans');
const config = require('../src/config');
const { query } = require('../src/db');

test.before(async () => { await h.start(); });
test.after(async () => { await h.stop(); });

test('F0-13 ۱) یک پیش‌فرض: آن‌چه کاشته می‌شود همان پشتوانهٔ trialDaysOf است', () => {
  assert.equal(plans.DEFAULT_CONFIG.trial_days, plans.TRIAL_DEFAULT.shop);
  assert.equal(plans.DEFAULT_CONFIG.pump_trial_days, plans.TRIAL_DEFAULT.pump);
  //  ⛔ رفتارِ موجود: دکان هفت روز، پمپ یک ماه — تا تصمیمِ D-03
  assert.equal(plans.TRIAL_DEFAULT.shop, '7');
  assert.equal(plans.TRIAL_DEFAULT.pump, '30');
});

test('F0-13 ۲) TRIAL_DAYS در محیط منبعِ دوم نیست', () => {
  assert.equal(config.defaults?.trialDays, undefined,
    'config.defaults.trialDays هیچ جا خوانده نمی‌شد و فقط عددِ دیگری (۷/۱۴) نشان می‌داد');
});

async function shown(app, token) {
  const pub = await h.get(`/api/plans?app=${app}`);
  assert.equal(pub.status, 200, JSON.stringify(pub.body));
  const out = { plans: pub.body.trialDays, server: await plans.trialDaysOf(app) };
  if (app === 'shop') {
    out.config = (await h.get('/api/config')).body.trialDays;
    out.mePlans = (await h.get('/api/me/plans', { token })).body.trialDays;
  }
  return out;
}

test('F0-13 ۳) همهٔ پاسخ‌ها یک عدد می‌گویند — با ردیفِ کاشته و بی آن', async () => {
  const u = await h.newUser('آزمایشی-یک-منبع');

  //  نصبِ عادی: ردیف کاشته شده است
  for (const [app, days] of [['shop', 7], ['pump', 30]]) {
    const s = await shown(app, u.accessToken);
    for (const [where, n] of Object.entries(s)) assert.equal(n, days, `${app} ${where}: ${JSON.stringify(s)}`);
  }

  //  ردیف نیست (پاک شده): هیچ مسیری نباید «۰» یا عددِ دیگری بگوید
  await query(`DELETE FROM app_config WHERE key IN ('trial_days','pump_trial_days')`);
  try {
    for (const [app, days] of [['shop', 7], ['pump', 30]]) {
      const s = await shown(app, u.accessToken);
      for (const [where, n] of Object.entries(s)) assert.equal(n, days, `${app} ${where}: ${JSON.stringify(s)}`);
    }
  } finally {
    await plans.seedDefaults();
  }

  //  مقدارِ پنلِ مدیر همیشه جلوتر است
  await plans.setConfig('trial_days', '10');
  try {
    const s = await shown('shop', u.accessToken);
    for (const [where, n] of Object.entries(s)) assert.equal(n, 10, `shop ${where}: ${JSON.stringify(s)}`);
  } finally {
    await plans.setConfig('trial_days', plans.TRIAL_DEFAULT.shop);
  }
});
