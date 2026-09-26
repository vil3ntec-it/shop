'use strict';
/**
 * همگام‌سازی سطلِ نرخِ خودش را دارد — نه سطلِ عمومیِ آی‌پی (۲.۱۱.۹).
 *
 * سنجهٔ `tensync`ِ برنامهٔ پمپ (۱۴۰۵/۰۷/۱۴) روی همین سرور: «بارِ اولِ» یک دفترِ
 * ده‌ساله ~۱٬۶۵۰ دسته است و سطلِ عمومی (۶۰۰ در ربع ساعت برای هر آی‌پی) پمپ را
 * چهل دقیقه پشتِ «تلاشِ زیاد» نگه می‌داشت — همراهِ مجوز و اپِ کارمندان و هر
 * دستگاهِ دیگرِ همان اینترنت. این‌جا هر دو سمت سنجیده می‌شود:
 *   ۱) همگام‌سازی سطلِ عمومی را نمی‌خورد (و بسته شدنِ آن همگام‌سازی را نمی‌بندد)
 *   ۲) ولی بی‌سقف هم نیست: سطلِ خودش به‌ازای حساب
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const config = require('../src/config');

let owner;
const pull = (token) => h.api('GET', '/api/sync/v1/pull?device_id=dev-rl&since=0',
  { token, headers: { 'X-App': 'shop' } });

test.before(async () => {
  //  ⚠️ پیش از ساختنِ برنامه: سقف‌ها همان لحظه خوانده می‌شوند
  config.rateLimit.generalMax = 40;
  config.rateLimit.syncMax = 60;
  await h.start();
  await require('../src/lib/sync-v1').syncSchemaTable();
  owner = await h.newUser('صاحبِ دکانِ نرخ', 'shop');
  await h.api('POST', '/api/shop', { token: owner.accessToken, body: { name: 'دکانِ نرخ' } });
  owner = { ...owner, ...(await h.api('POST', '/api/auth/login', {
    body: { identifier: owner.email, password: owner.password, app: 'shop',
      device: { deviceId: 'dev-rl', name: 'A', platform: 'test' } },
  })).body };
});
test.after(() => h.stop());

test('همگام‌سازی سطلِ عمومی را نمی‌خورد، و بسته شدنِ آن همگام‌سازی را نمی‌بندد', async () => {
  //  سطلِ عمومی را تا ته پر کن
  let general = 0;
  for (let i = 0; i < 60; i++) {
    const r = await h.api('GET', '/api/plans');
    if (r.status === 429) { general = i; break; }
  }
  assert.ok(general > 0 && general <= 40, `سطلِ عمومی باید پر شود (${general})`);

  //  همگام‌سازی همچنان کار می‌کند
  const r = await pull(owner.accessToken);
  assert.equal(r.status, 200, JSON.stringify(r.body));
});

test('ولی بی‌سقف هم نیست — سطلِ خودش به‌ازای حساب', async () => {
  let hit = 0;
  for (let i = 0; i < 80; i++) {
    const r = await pull(owner.accessToken);
    if (r.status === 429) { hit = i; break; }
    assert.equal(r.status, 200, JSON.stringify(r.body));
  }
  assert.ok(hit > 40 && hit <= 60, `سقفِ همگام‌سازی باید برسد، بالاتر از سقفِ عمومی (${hit})`);
});
