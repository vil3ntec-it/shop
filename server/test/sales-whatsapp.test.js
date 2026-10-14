'use strict';
/**
 * F0-24 — شمارهٔ واتس‌اپِ فروشِ اشتراک از `app_config` می‌آید.
 *
 * برنامهٔ اندروید/کامپیوتر از 2.14.2 به‌جای شمارهٔ ثابتِ `Vip.kt`، همان
 * `whatsapp.number`ِ پاسخِ `/me/plans` را به کار می‌برد (و بی آن، شمارهٔ
 * همیشگی). این پرونده قراردادِ سمتِ سرور را قفل می‌کند تا آن شماره بی‌صدا از
 * پاسخ نیفتد.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const plans = require('../src/lib/plans');

test.before(async () => { await h.start(); });
test.after(async () => { await h.stop(); });

test('F0-24 شمارهٔ فروش کلیدِ عمومی است و پیش‌فرضش همان شمارهٔ همیشگی', () => {
  assert.ok(plans.PUBLIC_CONFIG_KEYS.includes('whatsapp_number'));
  assert.equal(plans.DEFAULT_CONFIG.whatsapp_number, '0792236008');
});

test('F0-24 /me/plans همان شماره‌ای را می‌دهد که مدیر در app_config نوشته', async () => {
  const u = await h.newUser('فروش-واتساپ');
  const before = await h.get('/api/me/plans', { token: u.accessToken });
  assert.equal(before.status, 200, JSON.stringify(before.body));
  assert.equal(before.body.whatsapp.number, '0792236008');

  await plans.setConfig('whatsapp_number', '0700123456');
  const after = await h.get('/api/me/plans', { token: u.accessToken });
  assert.equal(after.body.whatsapp.number, '0700123456');
  assert.equal((await plans.publicConfig()).whatsapp_number, '0700123456');
});
