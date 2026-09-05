'use strict';
/**
 * تست‌های اجباری — همان ۱۲ موردی که باید قبل از تحویل واقعاً کار کنند.
 * روی PostgreSQL واقعی اجرا می‌شوند.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');

test.before(async () => { await h.start(); });
test.after(async () => { await h.stop(); });

// ---------- TEST 1 و 2: دو کاربر مستقل ----------
test('۱ و ۲: دو کاربر ثبت‌نام می‌کنند و اطلاعاتشان قاطی نمی‌شود', async () => {
  const a = await h.newUser('علی');
  const b = await h.newUser('محمود');

  assert.notEqual(a.user.id, b.user.id);
  const meA = await h.get('/api/me', { token: a.accessToken });
  const meB = await h.get('/api/me', { token: b.accessToken });
  assert.equal(meA.body.user.id, a.user.id);
  assert.equal(meB.body.user.id, b.user.id);
  assert.notEqual(meA.body.user.email, meB.body.user.email);
});

// ---------- TEST 3: دو دکان جدا ----------
test('۳: هیچ اطلاعاتی بین دکان A و دکان B دیده نمی‌شود', async () => {
  const a = await h.newUser('صاحب الف');
  const b = await h.newUser('صاحب ب');

  const shopA = await h.post('/api/shop', { name: 'دکان الف' }, { token: a.accessToken });
  const shopB = await h.post('/api/shop', { name: 'دکان ب' }, { token: b.accessToken });
  assert.equal(shopA.status, 201);
  assert.notEqual(shopA.body.shop.id, shopB.body.shop.id);

  await h.post('/api/products', { id: 'p-a', data: { name: 'برنج', price: 100 } }, { token: a.accessToken });
  await h.post('/api/products', { id: 'p-b', data: { name: 'روغن', price: 200 } }, { token: b.accessToken });

  const listA = await h.get('/api/products', { token: a.accessToken });
  const listB = await h.get('/api/products', { token: b.accessToken });
  assert.deepEqual(listA.body.items.map(i => i.id), ['p-a']);
  assert.deepEqual(listB.body.items.map(i => i.id), ['p-b']);

  // حتی با دانستن شناسه‌ی رکورد دکان دیگر هم دیده نمی‌شود
  const stolen = await h.get('/api/products/p-b', { token: a.accessToken });
  assert.equal(stolen.status, 404);

  // و فرستادن shopId دیگری در بدنه هیچ اثری ندارد
  const spoof = await h.post('/api/sync',
    { shopId: shopB.body.shop.id, changes: [{ collection: 'products', id: 'p-spoof', updatedAt: Date.now(), data: { name: 'نفوذ' } }] },
    { token: a.accessToken });
  assert.equal(spoof.status, 200);
  const afterB = await h.get('/api/products', { token: b.accessToken });
  assert.equal(afterB.body.items.find(i => i.id === 'p-spoof'), undefined);
});

// ---------- TEST 4 و 5 و 6: کد شاگرد ----------
test('۴ و ۵ و ۶: صاحب دکان کد می‌سازد، شاگرد وارد می‌شود و فقط همان دکان را می‌بیند', async () => {
  const owner = await h.newUser('صاحب');
  const staff = await h.newUser('شاگرد');
  const other = await h.newUser('بیگانه');

  const shop = await h.post('/api/shop', { name: 'دکان توحید' }, { token: owner.accessToken });
  await h.post('/api/shop', { name: 'دکان بیگانه' }, { token: other.accessToken });

  const made = await h.post('/api/shop/staff-code', { role: 'staff', maxUses: 1 }, { token: owner.accessToken });
  assert.equal(made.status, 201);
  assert.match(made.body.code, /^SHG-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$/);

  // شاگرد هنوز دکانی ندارد
  const before = await h.get('/api/shop', { token: staff.accessToken });
  assert.equal(before.body.shop, null);

  const joined = await h.post('/api/shop/staff/join', { code: made.body.code }, { token: staff.accessToken });
  assert.equal(joined.status, 201);
  assert.equal(joined.body.shop.id, shop.body.shop.id);
  assert.equal(joined.body.role, 'staff');

  // همان کد دوباره کار نمی‌کند
  const again = await h.post('/api/shop/staff/join', { code: made.body.code }, { token: other.accessToken });
  assert.equal(again.status, 403);

  // شاگرد فقط دکان خودش را می‌بیند
  await h.post('/api/products', { id: 'p-owner', data: { name: 'شکر' } }, { token: owner.accessToken });
  const seen = await h.get('/api/products', { token: staff.accessToken });
  assert.deepEqual(seen.body.items.map(i => i.id), ['p-owner']);

  // و کارهای مدیریتی برایش بسته است
  const denied = await h.post('/api/shop/staff-code', { role: 'staff' }, { token: staff.accessToken });
  assert.equal(denied.status, 403);
  const members = await h.get('/api/shop/members', { token: staff.accessToken });
  assert.equal(members.status, 403);
});

// ---------- TEST 7 و 8: همگام‌سازی دوطرفه ----------
test('۷ و ۸: فروش صاحب دکان و فروش شاگرد برای هر دو دیده می‌شود', async () => {
  const owner = await h.newUser('صاحب۲');
  const staff = await h.newUser('شاگرد۲');
  await h.post('/api/shop', { name: 'دکان همگام' }, { token: owner.accessToken });
  const code = (await h.post('/api/shop/staff-code', {}, { token: owner.accessToken })).body.code;
  await h.post('/api/shop/staff/join', { code }, { token: staff.accessToken });

  // صاحب دکان می‌فروشد
  const saleOwner = await h.post('/api/sales/full', {
    sale: { id: 'sale-owner', total: 500, at: Date.now() },
    items: [{ id: 'it-1', productId: 'p1', qty: 2, price: 250 }],
  }, { token: owner.accessToken });
  assert.equal(saleOwner.status, 201);

  // شاگرد همگام می‌شود و فروش را می‌بیند
  const pull1 = await h.get('/api/sync?since=0', { token: staff.accessToken });
  const ids1 = pull1.body.changes.filter(c => c.collection === 'sales').map(c => c.id);
  assert.ok(ids1.includes('sale-owner'));

  // شاگرد می‌فروشد
  const push = await h.post('/api/sync', {
    deviceId: 'dev-staff',
    changes: [
      { collection: 'sales', id: 'sale-staff', updatedAt: Date.now(), data: { total: 300 } },
      { collection: 'saleItems', id: 'it-2', updatedAt: Date.now(), data: { saleId: 'sale-staff', qty: 1 } },
    ],
  }, { token: staff.accessToken });
  assert.equal(push.body.applied, 2);

  // صاحب دکان همگام می‌شود و فروش شاگرد را می‌بیند
  const pull2 = await h.get(`/api/sync?since=${pull1.body.rev}`, { token: owner.accessToken });
  const ids2 = pull2.body.changes.map(c => c.id);
  assert.ok(ids2.includes('sale-staff'));

  // همگام‌سازی تفاضلی است: از rev آخر به بعد چیزی تازه نیست
  const pull3 = await h.get(`/api/sync?since=${pull2.body.rev}`, { token: owner.accessToken });
  assert.equal(pull3.body.changes.length, 0);
});

/*
 * تغییری که رد می‌شود، نباید بی‌صدا گم شود.
 *
 * سرور همیشه تعارض را برمی‌گرداند، ولی تا امروز فقط «رد شد» را می‌گفت.
 * چون rev رکورد عوض نمی‌شود، آن رکورد در pull بعدی هم نمی‌آمد — یعنی
 * گوشی هیچ راهی نداشت بفهمد چه چیزی جای ویرایشش نشسته و دو طرف تا ابد
 * ناهمگام می‌ماندند. حالا نسخه‌ی خود سرور همراه تعارض می‌آید.
 */
test('۸-ب: تعارض، نسخه‌ی سرور را هم با خودش برمی‌گرداند', async () => {
  const owner = await h.newUser('صاحب-تعارض');
  await h.post('/api/shop', { name: 'دکان تعارض' }, { token: owner.accessToken });

  const late = Date.now() + 60000;
  const first = await h.post('/api/sync', {
    deviceId: 'dev-a',
    changes: [{ collection: 'products', id: 'p-conf', updatedAt: late, data: { name: 'نسخه‌ی سرور' } }],
  }, { token: owner.accessToken });
  assert.equal(first.body.applied, 1);

  // ویرایشی که ساعتش عقب‌تر است — سرور قبولش نمی‌کند
  const second = await h.post('/api/sync', {
    deviceId: 'dev-b',
    changes: [{ collection: 'products', id: 'p-conf', updatedAt: late - 30000, data: { name: 'نسخه‌ی قدیمی' } }],
  }, { token: owner.accessToken });

  assert.equal(second.body.applied, 0);
  assert.equal(second.body.conflicts.length, 1);

  const conflict = second.body.conflicts[0];
  assert.equal(conflict.reason, 'stale');
  assert.equal(conflict.collection, 'products');
  assert.equal(conflict.id, 'p-conf');
  assert.equal(conflict.deleted, false);
  // همین است که گوشی با آن خودش را اصلاح می‌کند
  assert.equal(conflict.data.name, 'نسخه‌ی سرور');
});

// ---------- TEST 9: نوشتن همزمان ----------
test('۹: دو کاربر همزمان ثبت می‌کنند و هیچ رکوردی گم نمی‌شود', async () => {
  const owner = await h.newUser('صاحب۳');
  const staff = await h.newUser('شاگرد۳');
  await h.post('/api/shop', { name: 'دکان همزمان' }, { token: owner.accessToken });
  const code = (await h.post('/api/shop/staff-code', {}, { token: owner.accessToken })).body.code;
  await h.post('/api/shop/staff/join', { code }, { token: staff.accessToken });

  const now = Date.now();
  const batch = (who, n) => h.post('/api/sync', {
    deviceId: `dev-${who}`,
    changes: Array.from({ length: n }, (_, i) => ({
      collection: 'sales', id: `${who}-${i}`, updatedAt: now + i, data: { total: i },
    })),
  }, { token: who === 'owner' ? owner.accessToken : staff.accessToken });

  await Promise.all([batch('owner', 20), batch('staff', 20), batch('owner2', 0)]);

  const all = await h.get('/api/sales?limit=500', { token: owner.accessToken });
  const ids = all.body.items.map(i => i.id);
  for (let i = 0; i < 20; i++) {
    assert.ok(ids.includes(`owner-${i}`), `فروش owner-${i} گم شد`);
    assert.ok(ids.includes(`staff-${i}`), `فروش staff-${i} گم شد`);
  }

  // revها یکتا هستند — پس هیچ تغییری در همگام‌سازی از قلم نمی‌افتد
  const revs = all.body.items.map(i => i.rev);
  assert.equal(new Set(revs).size, revs.length);
});

// ---------- TEST 10: ثبت تکراری ----------
test('۱۰: ارسال دوباره‌ی یک درخواست، رکورد تکراری نمی‌سازد', async () => {
  const owner = await h.newUser('صاحب۴');
  await h.post('/api/shop', { name: 'دکان تکرار' }, { token: owner.accessToken });

  const body = {
    operationId: 'op-fixed-1',
    deviceId: 'dev-x',
    changes: [{ collection: 'sales', id: 'sale-once', updatedAt: Date.now(), data: { total: 999 } }],
  };
  const first = await h.post('/api/sync', body, { token: owner.accessToken });
  const second = await h.post('/api/sync', body, { token: owner.accessToken });

  assert.equal(first.body.applied, 1);
  assert.equal(second.body.replayed, true);
  assert.equal(second.body.rev, first.body.rev, 'درخواست تکراری نباید rev تازه بگیرد');

  const sales = await h.get('/api/sales', { token: owner.accessToken });
  assert.equal(sales.body.items.filter(s => s.id === 'sale-once').length, 1);

  // همان کار برای فروش کامل هم صدق می‌کند
  const full = { operationId: 'op-fixed-2', sale: { id: 'sale-full', total: 100 }, items: [{ id: 'i1', qty: 1 }] };
  await h.post('/api/sales/full', full, { token: owner.accessToken });
  await h.post('/api/sales/full', full, { token: owner.accessToken });
  const items = await h.get('/api/sale-items', { token: owner.accessToken });
  assert.equal(items.body.items.filter(i => i.id === 'i1').length, 1);
});

// ---------- TEST 11: کار آفلاین و صف ----------
test('۱۱: صف آفلاین بعد از وصل شدن، همه را یکجا می‌فرستد', async () => {
  const owner = await h.newUser('صاحب۵');
  await h.post('/api/shop', { name: 'دکان آفلاین' }, { token: owner.accessToken });

  // چیزی که گوشی در نبودِ اینترنت جمع کرده است
  const queued = Array.from({ length: 5 }, (_, i) => ({
    collection: 'expenses', id: `exp-${i}`, updatedAt: Date.now() - (5 - i) * 1000,
    data: { title: `مصرف ${i}`, amount: (i + 1) * 100 },
  }));
  const res = await h.post('/api/sync', { deviceId: 'dev-offline', operationId: 'queue-1', changes: queued },
    { token: owner.accessToken });
  assert.equal(res.body.applied, 5);

  const list = await h.get('/api/expenses', { token: owner.accessToken });
  assert.equal(list.body.items.length, 5);
  // ترتیب و مقدارها دست‌نخورده مانده‌اند
  assert.equal(list.body.items.find(i => i.id === 'exp-3').amount, 400);
});

// ---------- TEST 12: نصب دوباره / گوشی تازه ----------
test('۱۲: بعد از حذف برنامه و ورود دوباره، همه چیز برمی‌گردد', async () => {
  const owner = await h.newUser('صاحب۶');
  await h.post('/api/shop', { name: 'دکان بازگشت' }, { token: owner.accessToken });
  await h.post('/api/sync', {
    deviceId: 'old-phone',
    changes: [
      { collection: 'debtors', id: 'd1', updatedAt: Date.now(), data: { name: 'احمد', phone: '0700000000' } },
      { collection: 'transactions', id: 't1', updatedAt: Date.now(), data: { debtorId: 'd1', type: 'give', amount: 500 } },
    ],
  }, { token: owner.accessToken });

  // گوشی جدید: همان حساب، دستگاه دیگر، دیتابیس محلی خالی
  const relogin = await h.post('/api/auth/login', {
    identifier: owner.email, password: owner.password,
    device: { deviceId: 'new-phone', name: 'گوشی نو', platform: 'android' },
  });
  assert.equal(relogin.status, 200);
  assert.equal(relogin.body.shop.name, 'دکان بازگشت');

  const restored = await h.get('/api/sync?since=0', { token: relogin.body.accessToken });
  const ids = restored.body.changes.map(c => c.id);
  assert.ok(ids.includes('d1'));
  assert.ok(ids.includes('t1'));
  const debtor = restored.body.changes.find(c => c.id === 'd1');
  assert.equal(debtor.data.name, 'احمد');
});
