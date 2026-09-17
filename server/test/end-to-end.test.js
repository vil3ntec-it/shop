'use strict';
/**
 * زنجیرهٔ کامل — از پنلِ مدیر تا داخلِ هر دو برنامه.
 *
 * ── خواستهٔ صاحب مخزن ──────────────────────────────────────────────
 * «ببین اشتراک از سرور می‌رود به برنامه‌ها و می‌شود از برنامهٔ ادمین هم
 *  داد یا نه · ببین کدِ شش‌رقمیِ هر برنامه جدا است یا نه · ببین
 *  اطلاعاتِ هر برنامه می‌آید به سایت و ثبت می‌شود یا نه · و بک‌اپ‌ها
 *  می‌آیند یا نه · و حساب‌های هر برنامه و هر حسابِ کاربر توی فولدرِ
 *  سرورها ذخیره می‌شود یا نه.»
 *
 * هر سنجهٔ این پرونده یکی از همان جمله‌هاست و **از سرِ زنجیره** شروع
 * می‌کند: مدیر در پنل یک کار می‌کند، و همان چیزی سنجیده می‌شود که
 * برنامه واقعاً می‌بیند — نه ردیفِ دیتابیس.
 *
 * ⚠️ اپِ اندرویدِ مدیریت همین مسیرها را می‌زند (`AdminApi.kt`)، پس
 * «از برنامهٔ ادمین هم می‌شود داد؟» یعنی همین مسیرها سالم باشند.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const path = require('path');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const config = require('../src/config');

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
});
test.after(async () => { await h.stop(); });

const adminToken = async () =>
  (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;

/** بارِ مجوزِ امضاشده — بی سنجشِ امضا، فقط برای دیدنِ ادعاها. */
const claims = (t) => JSON.parse(Buffer.from(String(t).split('.')[1], 'base64url').toString('utf8'));

async function shopOwner(name) {
  const u = await h.newUser(name);
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { ...u, shopId: made.body.shop.id };
}

async function pumpOwner(name) {
  const u = await h.newUser(name, 'pump');
  const made = await h.post('/api/pump', { name: `پمپِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { ...u, stationId: made.body.station.id, stationCode: made.body.station.code };
}

/* ══════════════════════════════════════════════════════════════════
   ۱) «اشتراک از سرور می‌رود به برنامه‌ها؟»
   ══════════════════════════════════════════════════════════════════ */

test('مدیر اشتراکِ دکان را می‌دهد و برنامهٔ دکان همان لحظه می‌بیندش', async () => {
  const o = await shopOwner('اشتراکی');
  const t = await adminToken();

  const before = await h.get('/api/me/subscription', { token: o.accessToken });
  assert.notEqual(before.body.source, 'subscription');

  const granted = await h.post('/api/admin/subscriptions',
    { shopId: o.shopId, plan: 'm6', days: 180 }, { token: t });
  assert.equal(granted.status, 201, JSON.stringify(granted.body));

  const after = await h.get('/api/me/subscription', { token: o.accessToken });
  assert.equal(after.body.source, 'subscription');
  assert.equal(after.body.plan, 'm6');
  assert.ok(after.body.daysLeft > 170);
  //  و همان چیز از درِ `‎/api/me‎` هم دیده می‌شود — صفحهٔ اولِ برنامه
  const home = await h.get('/api/me', { token: o.accessToken });
  assert.equal(home.body.entitlement.source, 'subscription');
});

test('اشتراکِ دکان قابلیتِ قفل‌شده را همان لحظه باز می‌کند', async () => {
  const o = await shopOwner('قفل‌باز');
  const t = await adminToken();
  //  پلنی که `multi_device` ندارد ⇒ کدِ شاگرد بسته
  await h.post('/api/admin/subscriptions',
    { shopId: o.shopId, plan: 'custom', days: 30, features: ['sales'] }, { token: t });
  const closed = await h.post('/api/shop/staff-code', {}, { token: o.accessToken });
  assert.equal(closed.status, 403, JSON.stringify(closed.body));

  //  و با پلنی که داردش ⇒ باز
  await h.post('/api/admin/subscriptions',
    { shopId: o.shopId, plan: 'custom', days: 30, features: ['multi_device'] }, { token: t });
  const open = await h.post('/api/shop/staff-code', {}, { token: o.accessToken });
  assert.ok(open.status < 300, JSON.stringify(open.body));
});

test('مدیر اشتراکِ پمپ را می‌دهد و برنامهٔ پمپ همان لحظه می‌بیندش', async () => {
  const o = await pumpOwner('پمپِ اشتراکی');
  const t = await adminToken();

  await h.post('/api/admin/pump/subscriptions',
    { stationId: o.stationId, plan: 'vip', days: 365 }, { token: t });

  const me = await h.get('/api/pump/me', { token: o.accessToken });
  assert.equal(me.body.entitlement.source, 'subscription');
  assert.equal(me.body.entitlement.subscription.plan, 'vip');
});

test('اشتراکِ حساب، بی هیچ کدی به خودِ برنامه می‌رسد (bind)', async () => {
  const o = await pumpOwner('بندشو');
  const t = await adminToken();
  await h.post('/api/admin/pump/subscriptions',
    { stationId: o.stationId, plan: 'vip', days: 365 }, { token: t });

  const bound = await h.post('/api/pump/device/bind',
    { device: { uid: 'pc-e2e-1', name: 'کامپیوترِ پمپ', platform: 'windows' } },
    { token: o.accessToken });
  assert.equal(bound.status, 201, JSON.stringify(bound.body));
  assert.ok(bound.body.deviceToken, 'توکنِ دستگاه باید بیاید');
  assert.ok(bound.body.license, 'مجوزِ امضاشده باید بیاید');

  const c = claims(bound.body.license);
  assert.equal(c.aud, 'tohid-pump-app', 'شنوندهٔ مجوز همان برنامهٔ پمپ است');
  assert.equal(c.duid, 'pc-e2e-1');
  assert.equal(c.stn, o.stationId);
  //  وی‌آی‌پی یعنی هر شش قابلیتِ پولی
  for (const key of ['kar_app', 'bot', 'cloud', 'cloudbackup', 'profit', 'history']) {
    assert.ok(c.feat.includes(key), `وی‌آی‌پی باید ${key} را داشته باشد — ${JSON.stringify(c.feat)}`);
  }
});

test('پلنِ استاندارد فهرستِ خودش را می‌برد، نه همه‌چیز را', async () => {
  const o = await pumpOwner('استانداردی');
  const t = await adminToken();
  //  ⚠️ پنل همیشه `days` هم می‌فرستد — همان‌جایی که یک بار فهرستِ پلن
  //  جا می‌ماند و «استاندارد» عملاً وی‌آی‌پی می‌شد
  await h.post('/api/admin/pump/subscriptions',
    { stationId: o.stationId, plan: 'std', days: 365 }, { token: t });

  const bound = await h.post('/api/pump/device/bind',
    { device: { uid: 'pc-e2e-2' } }, { token: o.accessToken });
  const c = claims(bound.body.license);
  //  ⚠️ `feat` هر چیزی است که **باز** است: دفترِ همیشه‌رایگان به‌علاوهٔ
  //  آن‌چه پلن خریده. پس مرزِ استاندارد یعنی `cloudbackup` باشد و
  //  بقیهٔ پولی‌ها نباشند.
  assert.ok(c.feat.includes('cloudbackup'), JSON.stringify(c.feat));
  for (const locked of ['kar_app', 'bot', 'cloud', 'profit', 'history', 'dashboard']) {
    assert.ok(!c.feat.includes(locked),
      `استاندارد نباید ${locked} را داشته باشد — ${JSON.stringify(c.feat)}`);
  }
  //  و دفترِ خودِ کاربر هیچ‌وقت قفل نمی‌شود
  for (const free of ['debtors', 'safe', 'invoice', 'storage']) {
    assert.ok(c.feat.includes(free), `${free} باید همیشه باز بماند`);
  }
});

test('گرفتنِ اشتراک، همان لحظه در برنامه دیده می‌شود', async () => {
  const o = await pumpOwner('پس‌گرفته');
  const t = await adminToken();
  const g = await h.post('/api/admin/pump/subscriptions',
    { stationId: o.stationId, plan: 'vip', days: 365 }, { token: t });

  const off = await h.post(`/api/admin/pump/subscriptions/${g.body.subscription.id}/status`,
    { status: 'cancelled' }, { token: t });
  assert.equal(off.status, 200, JSON.stringify(off.body));

  const me = await h.get('/api/pump/me', { token: o.accessToken });
  assert.notEqual(me.body.entitlement.source, 'subscription');
});

test('اشتراکِ دکان پمپ را باز نمی‌کند و برعکس', async () => {
  const u = await h.newUser('دوزیست');
  await h.post('/api/shop', { name: 'دکانش' }, { token: u.accessToken });
  const asPump = await h.signIn(u, 'pump');
  const st = await h.post('/api/pump', { name: 'پمپش' }, { token: asPump.accessToken });
  const t = await adminToken();

  const shopId = (await h.get('/api/shop/me', { token: u.accessToken })).body.shop.id;
  await h.post('/api/admin/subscriptions', { shopId, plan: 'y1', days: 365 }, { token: t });

  const pumpMe = await h.get('/api/pump/me', { token: asPump.accessToken });
  assert.notEqual(pumpMe.body.entitlement.source, 'subscription',
    'اشتراکِ دکان نباید روی پمپ بنشیند');
  assert.equal(st.body.station.id, pumpMe.body.station.id);
});

/* ══════════════════════════════════════════════════════════════════
   ۲) «کدِ شش‌رقمیِ هر برنامه جدا است؟»
   ══════════════════════════════════════════════════════════════════ */

test('کدِ دکان، اشتراکِ دکان را باز می‌کند', async () => {
  const o = await shopOwner('کدی');
  const t = await adminToken();
  const made = await h.post('/api/admin/vip-codes', { plan: 'm6', days: 180 }, { token: t });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  assert.match(String(made.body.code), /^\d{6}$/, 'شش رقم، نه بیشتر و نه کمتر');

  const used = await h.post('/api/vip/redeem', { code: made.body.code }, { token: o.accessToken });
  assert.equal(used.status, 201, JSON.stringify(used.body));
  assert.equal(used.body.entitlement.source, 'subscription');
});

test('کدِ پمپ، پمپ را فعال می‌کند و توکنِ دستگاه می‌دهد', async () => {
  const t = await adminToken();
  const made = await h.post('/api/admin/pump/vip-codes', { plan: 'vip', days: 365 }, { token: t });
  assert.equal(made.status, 201, JSON.stringify(made.body));

  const on = await h.post('/api/pump/device/activate', {
    code: made.body.code,
    device: { uid: 'pc-code-1', name: 'کامپیوترِ پمپ', platform: 'windows' },
    station: { name: 'پمپِ کدی' },
  });
  assert.equal(on.status, 201, JSON.stringify(on.body));
  assert.ok(on.body.deviceToken);
  assert.equal(on.body.entitlement.source, 'subscription');
});

test('کدِ دکان روی پمپ کار نمی‌کند', async () => {
  const t = await adminToken();
  const shopCode = await h.post('/api/admin/vip-codes', { plan: 'm6', days: 180 }, { token: t });

  const tried = await h.post('/api/pump/device/activate', {
    code: shopCode.body.code, device: { uid: 'pc-cross-1' }, station: { name: 'پمپِ دزد' },
  });
  assert.equal(tried.status, 404);
  assert.equal(tried.body.error.code, 'bad_code');

  //  و همان کد هنوز برای دکانِ خودش سالم است
  const o = await shopOwner('صاحبِ کد');
  const used = await h.post('/api/vip/redeem', { code: shopCode.body.code },
    { token: o.accessToken });
  assert.equal(used.status, 201, 'کدِ دکان با تلاشِ پمپ خرج نشده باشد');
});

test('کدِ پمپ روی دکان کار نمی‌کند', async () => {
  const t = await adminToken();
  const pumpCode = await h.post('/api/admin/pump/vip-codes', { plan: 'vip', days: 365 }, { token: t });

  const o = await shopOwner('دکانِ مدعی');
  const tried = await h.post('/api/vip/redeem', { code: pumpCode.body.code },
    { token: o.accessToken });
  assert.equal(tried.status, 404);
  assert.equal(tried.body.error.code, 'bad_code');
});

test('کدِ هر بخش در دفترِ خودش می‌نشیند و در دفترِ دیگری نیست', async () => {
  const t = await adminToken();
  const before = {
    shop: (await h.query('SELECT COUNT(*)::int n FROM vip_codes')).rows[0].n,
    pump: (await h.query('SELECT COUNT(*)::int n FROM station_vip_codes')).rows[0].n,
  };
  await h.post('/api/admin/vip-codes', { plan: 'm1', days: 30 }, { token: t });
  const mid = {
    shop: (await h.query('SELECT COUNT(*)::int n FROM vip_codes')).rows[0].n,
    pump: (await h.query('SELECT COUNT(*)::int n FROM station_vip_codes')).rows[0].n,
  };
  assert.equal(mid.shop, before.shop + 1);
  assert.equal(mid.pump, before.pump, 'ساختِ کدِ دکان نباید ردیفی در دفترِ پمپ بگذارد');

  await h.post('/api/admin/pump/vip-codes', { plan: 'std', days: 365 }, { token: t });
  const after = {
    shop: (await h.query('SELECT COUNT(*)::int n FROM vip_codes')).rows[0].n,
    pump: (await h.query('SELECT COUNT(*)::int n FROM station_vip_codes')).rows[0].n,
  };
  assert.equal(after.shop, mid.shop);
  assert.equal(after.pump, mid.pump + 1);
});

test('کدِ یک‌بارمصرف دو بار خرج نمی‌شود', async () => {
  const t = await adminToken();
  const made = await h.post('/api/admin/vip-codes', { plan: 'm1', days: 30 }, { token: t });
  const a = await shopOwner('اولی');
  const b = await shopOwner('دومی');

  assert.equal((await h.post('/api/vip/redeem', { code: made.body.code },
    { token: a.accessToken })).status, 201);
  const second = await h.post('/api/vip/redeem', { code: made.body.code }, { token: b.accessToken });
  assert.equal(second.status, 403);
  assert.equal(second.body.error.code, 'code_used');
});

test('کدِ باطل‌شده دیگر کار نمی‌کند', async () => {
  const t = await adminToken();
  const made = await h.post('/api/admin/pump/vip-codes', { plan: 'std', days: 365 }, { token: t });
  await h.post(`/api/admin/pump/vip-codes/${made.body.vipCode.id}/revoke`, {}, { token: t });

  const tried = await h.post('/api/pump/device/activate', {
    code: made.body.code, device: { uid: 'pc-revoked' }, station: { name: 'پمپ' },
  });
  assert.ok(tried.status >= 400, JSON.stringify(tried.body));
});

test('کدِ خام نه در پاسخِ فهرست است و نه در دیتابیس', async () => {
  const t = await adminToken();
  const made = await h.post('/api/admin/pump/vip-codes', { plan: 'std', days: 365 }, { token: t });
  const code = String(made.body.code);

  const list = await h.get('/api/admin/pump/vip-codes', { token: t });
  assert.ok(!JSON.stringify(list.body).includes(code), 'کدِ خام در فهرست دیده نشود');

  const rows = await h.query('SELECT * FROM station_vip_codes');
  assert.ok(!JSON.stringify(rows.rows).includes(code), 'کدِ خام در دیتابیس ذخیره نشود');
});

/* ══════════════════════════════════════════════════════════════════
   ۳) «اطلاعاتِ هر برنامه می‌آید به سرور و ثبت می‌شود؟»
   ══════════════════════════════════════════════════════════════════ */

test('دفترِ دکان به سرور می‌رود و همان‌جا برمی‌گردد', async () => {
  const o = await shopOwner('همگام');
  const pushed = await h.post('/api/shop/sync/push', {
    changes: [{ collection: 'products', id: 'p1', updatedAt: Date.now(),
      data: { name: 'روغن', price: 120 } }],
  }, { token: o.accessToken });
  assert.ok(pushed.status < 300, JSON.stringify(pushed.body));
  assert.equal(pushed.body.applied, 1);

  const pulled = await h.get('/api/shop/sync/pull', { token: o.accessToken });
  assert.equal(pulled.status, 200);
  const rows = pulled.body.changes.filter(c => c.collection === 'products');
  assert.equal(rows.length, 1, JSON.stringify(pulled.body).slice(0, 300));
  assert.equal(rows[0].data.name, 'روغن');
});

test('دفترِ یک دکان در دکانِ دیگر پیدا نمی‌شود', async () => {
  const a = await shopOwner('الف');
  const b = await shopOwner('ب');
  await h.post('/api/shop/sync/push', {
    changes: [{ collection: 'products', id: 'p-secret', updatedAt: Date.now(),
      data: { name: 'رازِ الف' } }],
  }, { token: a.accessToken });

  const his = await h.get('/api/shop/sync/pull', { token: b.accessToken });
  assert.ok(!JSON.stringify(his.body).includes('رازِ الف'));
});

test('پوشهٔ ابریِ پمپ پر می‌شود و مدیر فهرستش را می‌بیند', async () => {
  const o = await pumpOwner('پوشه‌دار');
  const t = await adminToken();
  await h.post('/api/admin/pump/subscriptions',
    { stationId: o.stationId, plan: 'vip', days: 365 }, { token: t });

  const put = await h.put('/api/pump/files/live.json',
    { data: { banner: { debtors: 12 }, at: Date.now() } }, { token: o.accessToken });
  assert.equal(put.status, 200, JSON.stringify(put.body));

  const seen = await h.get(`/api/admin/pump/stations/${o.stationId}`, { token: t });
  assert.equal(seen.status, 200);
  const file = seen.body.files.find(f => f.path === 'live.json');
  assert.ok(file, 'فایل باید در پوشهٔ همان پمپ دیده شود');
  assert.ok(file.size > 0);
  //  ⚠️ خودِ داده در پنل باز نمی‌شود — فقط نام و اندازه
  assert.ok(!JSON.stringify(seen.body.files).includes('debtors'));
});

test('خبرهای برنامه در سرور ثبت می‌شوند و مدیر می‌بیندشان', async () => {
  const o = await pumpOwner('خبرده');
  const t = await adminToken();
  const bound = await h.post('/api/pump/device/bind', { device: { uid: 'pc-e2e-news' } },
    { token: o.accessToken });

  await h.post('/api/pump/device/events',
    { events: [{ kind: 'stock_out', title: 'دیزل تمام شد', clientId: 'x1' }] },
    { token: bound.body.deviceToken });

  const seen = await h.get(`/api/admin/pump/stations/${o.stationId}/events`, { token: t });
  assert.equal(seen.body.events.length, 1);
  assert.equal(seen.body.events[0].title, 'دیزل تمام شد');
});

test('برنامهٔ دکان هم خبرش در سرور می‌نشیند', async () => {
  const o = await shopOwner('خبردهٔ دکان');
  const sent = await h.post('/api/events',
    { events: [{ kind: 'low_stock', title: 'روغن کم مانده', clientId: 'y1' }] },
    { token: o.accessToken });
  assert.equal(sent.status, 201, JSON.stringify(sent.body));

  const seen = await h.get('/api/events', { token: o.accessToken });
  assert.equal(seen.body.events.length, 1);
  assert.equal(seen.body.events[0].title, 'روغن کم مانده');
});

test('نسخهٔ برنامه روی نشست ثبت می‌شود — مدیر می‌داند کدام نسخه در دست است', async () => {
  const u = await h.newUser('نسخه‌دار');
  const r = await h.post('/api/auth/login', {
    identifier: u.email, password: u.password, device: { deviceId: 'd-ver' }, appVersion: '2.5.9',
  });
  const { hashToken } = require('../src/lib/tokens');
  const row = await one('SELECT app_version FROM tokens WHERE token_hash=$1',
    [hashToken(r.body.accessToken)]);
  assert.equal(row.app_version, '2.5.9');
});

/* ══════════════════════════════════════════════════════════════════
   ۴) «بک‌اپ‌ها می‌آیند؟» و «در فولدرِ سرور ذخیره می‌شوند؟»
   ══════════════════════════════════════════════════════════════════ */

const blob = (n, fill = 7) => Buffer.alloc(n, fill);

test('پشتیبانِ دکان می‌رسد، در فهرست می‌آید و دست‌نخورده برمی‌گردد', async () => {
  const o = await shopOwner('پشتیبان‌دار');
  const data = blob(2048, 3);

  const up = await h.raw('POST', '/api/me/backups?ext=db&kind=manual&label=دستی', data,
    { token: o.accessToken });
  assert.equal(up.status, 201, JSON.stringify(up.body));

  const list = await h.get('/api/me/backups', { token: o.accessToken });
  assert.equal(list.body.backups.length, 1);
  assert.equal(list.body.backups[0].bytes, data.length);

  const back = await h.download(`/api/me/backups/${up.body.backup.id}`, { token: o.accessToken });
  assert.equal(back.status, 200);
  assert.ok(back.buffer.equals(data), 'همان بایت‌ها، بی یک بیت کم و زیاد');
});

test('پشتیبانِ پمپ از کامپیوتر می‌رود و صاحبش روی گوشی می‌بیندش', async () => {
  const o = await pumpOwner('پشتیبانِ پمپ');
  const bound = await h.post('/api/pump/device/bind', { device: { uid: 'pc-e2e-bk' } },
    { token: o.accessToken });

  const up = await h.raw('POST', '/api/pump/device/backups?ext=db', blob(512, 9),
    { token: bound.body.deviceToken });
  assert.equal(up.status, 201, JSON.stringify(up.body));

  const fromPhone = await h.get('/api/pump/backups', { token: o.accessToken });
  assert.equal(fromPhone.body.backups.length, 1, 'یک پوشه، دو در');
  assert.equal(fromPhone.body.backups[0].id, up.body.backup.id);
});

test('هر حسابِ هر برنامه پوشهٔ خودش را روی دیسکِ سرور دارد', async () => {
  const s = await shopOwner('روی‌دیسک');
  const p = await pumpOwner('پمپِ روی‌دیسک');
  await h.raw('POST', '/api/me/backups?ext=json', Buffer.from('{"shop":1}'),
    { token: s.accessToken });
  const bound = await h.post('/api/pump/device/bind', { device: { uid: 'pc-e2e-disk' } },
    { token: p.accessToken });
  await h.raw('POST', '/api/pump/device/backups?ext=json', Buffer.from('{"pump":1}'),
    { token: bound.body.deviceToken });

  const shopDir = path.join(config.backup.dir, 'accounts', 'shop', s.shopId);
  const pumpDir = path.join(config.backup.dir, 'accounts', 'pump', p.stationId);
  assert.ok(fs.existsSync(shopDir), `پوشهٔ دکان باید ساخته شود: ${shopDir}`);
  assert.ok(fs.existsSync(pumpDir), `پوشهٔ پمپ باید ساخته شود: ${pumpDir}`);

  const shopFiles = fs.readdirSync(shopDir);
  const pumpFiles = fs.readdirSync(pumpDir);
  assert.equal(shopFiles.length, 1);
  assert.equal(pumpFiles.length, 1);
  //  ⚠️ دو بخش، دو شاخهٔ جدا — نه یک پوشه با نامِ مشترک
  assert.ok(!shopDir.startsWith(pumpDir) && !pumpDir.startsWith(shopDir));
  assert.equal(
    fs.readFileSync(path.join(shopDir, shopFiles[0]), 'utf8'), '{"shop":1}');
});

test('پشتیبانِ یک حساب به دستِ حسابِ دیگر نمی‌رسد', async () => {
  const a = await shopOwner('صاحبِ فایل');
  const b = await shopOwner('همسایه');
  const up = await h.raw('POST', '/api/me/backups?ext=json', Buffer.from('{"a":1}'),
    { token: a.accessToken });

  const steal = await h.download(`/api/me/backups/${up.body.backup.id}`, { token: b.accessToken });
  assert.equal(steal.status, 404);
  const kill = await h.del(`/api/me/backups/${up.body.backup.id}`, { token: b.accessToken });
  assert.equal(kill.status, 404);
});

test('مدیر پشتیبان‌های هر حسابِ هر بخش را می‌بیند', async () => {
  const s = await shopOwner('دیده‌شونده');
  const p = await pumpOwner('پمپِ دیده‌شونده');
  const t = await adminToken();
  await h.raw('POST', '/api/me/backups?ext=json', Buffer.from('{"s":1}'), { token: s.accessToken });
  const bound = await h.post('/api/pump/device/bind', { device: { uid: 'pc-e2e-admin-bk' } },
    { token: p.accessToken });
  await h.raw('POST', '/api/pump/device/backups?ext=json', Buffer.from('{"p":1}'),
    { token: bound.body.deviceToken });

  const shopSide = await h.get(`/api/admin/accounts/shop/${s.shopId}/backups`, { token: t });
  const pumpSide = await h.get(`/api/admin/accounts/pump/${p.stationId}/backups`, { token: t });
  assert.equal(shopSide.status, 200, JSON.stringify(shopSide.body));
  assert.equal(shopSide.body.backups.length, 1);
  assert.equal(pumpSide.status, 200, JSON.stringify(pumpSide.body));
  assert.equal(pumpSide.body.backups.length, 1);
});

test('خواندن و برگرداندنِ پشتیبان با اشتراکِ تمام‌شده هم باز است', async () => {
  const o = await shopOwner('بی‌اشتراک');
  const up = await h.raw('POST', '/api/me/backups?ext=json', Buffer.from('{"x":1}'),
    { token: o.accessToken });
  //  دورهٔ آزمایشی را هم تمام می‌کنیم
  await h.query('UPDATE shops SET created_at=$2 WHERE id=$1',
    [o.shopId, Date.now() - 400 * 24 * 3600 * 1000]);

  const list = await h.get('/api/me/backups', { token: o.accessToken });
  assert.equal(list.status, 200);
  const back = await h.download(`/api/me/backups/${up.body.backup.id}`, { token: o.accessToken });
  assert.equal(back.status, 200, 'گروگان گرفتنِ دادهٔ کاربر ممنوع');
});

/* ══════════════════════════════════════════════════════════════════
   ۵) پیام و پشتیبانی — «هر دو برنامه»
   ══════════════════════════════════════════════════════════════════ */

test('پیامِ همگانی به دکان و پمپ، هر دو می‌رسد', async () => {
  const s = await shopOwner('گیرندهٔ دکان');
  const p = await pumpOwner('گیرندهٔ پمپ');
  const t = await adminToken();

  const sent = await h.post('/api/admin/support/broadcast',
    { body: 'سلام به هر دو برنامه', app: 'both', target: 'all' }, { token: t });
  assert.ok(sent.status < 300, JSON.stringify(sent.body));
  assert.ok(sent.body.sent >= 2, JSON.stringify(sent.body));

  const shopThread = await h.get('/api/support/thread', { token: s.accessToken });
  assert.ok(JSON.stringify(shopThread.body).includes('سلام به هر دو برنامه'));
  const pumpThread = await h.get('/api/pump/support/thread', { token: p.accessToken });
  assert.ok(JSON.stringify(pumpThread.body).includes('سلام به هر دو برنامه'));
});

test('پشتیبانیِ صاحبِ پمپ حتی بی اشتراک باز است', async () => {
  const p = await pumpOwner('گیرِکرده');
  await h.query('UPDATE stations SET created_at=$2 WHERE id=$1',
    [p.stationId, Date.now() - 400 * 24 * 3600 * 1000]);

  const asked = await h.post('/api/pump/support/messages', { body: 'چرا قفل شد؟' },
    { token: p.accessToken });
  assert.equal(asked.status, 201, JSON.stringify(asked.body));
});
