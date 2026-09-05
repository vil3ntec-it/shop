'use strict';
/**
 * چت پشتیبانی — کاربر، مهمان و مدیر.
 *
 * چیزی که باید ثابت شود: مهمانِ بی‌حساب هم می‌تواند بنویسد، مدیر پاسخ
 * می‌دهد، هر کس فقط گفت‌وگوی خودش را می‌بیند، و مهمانی که بعداً حساب
 * می‌سازد همان گفت‌وگوی قبلی‌اش را دارد.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'admin','پشتیبان',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
});
test.after(async () => { await h.stop(); });

async function adminToken() {
  const r = await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' });
  return r.body.token;
}

test('مهمانِ بی‌حساب می‌تواند پیام بدهد', async () => {
  const sent = await h.post('/api/support/messages', {
    deviceUid: 'guest-device-1',
    name: 'مهمان',
    body: 'سلام، نمی‌توانم ثبت‌نام کنم',
  });
  assert.equal(sent.status, 201);
  assert.equal(sent.body.message.sender, 'user');

  const mine = await h.get('/api/support/thread?deviceUid=guest-device-1');
  assert.equal(mine.status, 200);
  assert.equal(mine.body.messages.length, 1);
  assert.equal(mine.body.messages[0].body, 'سلام، نمی‌توانم ثبت‌نام کنم');
});

test('مدیر پیام را می‌بیند و جواب می‌دهد، و کاربر جواب را می‌گیرد', async () => {
  await h.post('/api/support/messages', { deviceUid: 'guest-device-2', body: 'قیمت اشتراک چند است؟' });

  const t = await adminToken();
  const threads = await h.get('/api/admin/support/threads', { token: t });
  const thread = threads.body.threads.find(x => x.deviceUid === 'guest-device-2');
  assert.ok(thread, 'گفت‌وگو در فهرست مدیر نیست');
  assert.equal(thread.unreadAdmin, 1);

  const replied = await h.post(`/api/admin/support/threads/${thread.id}/messages`,
    { body: 'ماهانه ۵۰۰ افغانی' }, { token: t });
  assert.equal(replied.status, 201);
  assert.equal(replied.body.message.sender, 'admin');

  const back = await h.get('/api/support/thread?deviceUid=guest-device-2');
  assert.equal(back.body.messages.length, 2);
  assert.equal(back.body.messages[1].sender, 'admin');
  assert.equal(back.body.thread.unreadUser, 1);

  //  «خواندم» نقطه‌ی قرمز را پاک می‌کند
  await h.post('/api/support/read', { deviceUid: 'guest-device-2' });
  const after = await h.get('/api/support/thread?deviceUid=guest-device-2');
  assert.equal(after.body.thread.unreadUser, 0);
});

test('هر دستگاه فقط گفت‌وگوی خودش را می‌بیند', async () => {
  await h.post('/api/support/messages', { deviceUid: 'device-A', body: 'رازِ الف' });
  await h.post('/api/support/messages', { deviceUid: 'device-B', body: 'رازِ ب' });

  const a = await h.get('/api/support/thread?deviceUid=device-A');
  const b = await h.get('/api/support/thread?deviceUid=device-B');
  assert.notEqual(a.body.thread.id, b.body.thread.id);
  assert.ok(!JSON.stringify(a.body.messages).includes('رازِ ب'));
  assert.ok(!JSON.stringify(b.body.messages).includes('رازِ الف'));
});

test('مهمانی که حساب می‌سازد، همان گفت‌وگوی قبلی‌اش را دارد', async () => {
  await h.post('/api/support/messages', { deviceUid: 'becomes-user', body: 'پیش از ثبت‌نام' });
  const before = await h.get('/api/support/thread?deviceUid=becomes-user');

  const u = await h.newUser('تازه‌وارد');
  const sent = await h.post('/api/support/messages',
    { deviceUid: 'becomes-user', body: 'بعد از ثبت‌نام' }, { token: u.accessToken });
  assert.equal(sent.status, 201);

  const after = await h.get('/api/support/thread?deviceUid=becomes-user', { token: u.accessToken });
  assert.equal(after.body.thread.id, before.body.thread.id, 'گفت‌وگو عوض شد');
  assert.equal(after.body.messages.length, 2);
  assert.equal(after.body.thread.userId, u.user.id);
});

test('کاربر با توکن، گفت‌وگویش را حتی از دستگاه دیگری می‌بیند', async () => {
  const u = await h.newUser('چنددستگاهی');
  await h.post('/api/support/messages', { deviceUid: 'phone-1', body: 'از گوشی' }, { token: u.accessToken });

  const fromTablet = await h.get('/api/support/thread?deviceUid=tablet-9', { token: u.accessToken });
  assert.equal(fromTablet.body.messages.length, 1);
  assert.equal(fromTablet.body.messages[0].body, 'از گوشی');
});

test('پیام خالی و پیام بی‌هویت رد می‌شوند', async () => {
  assert.equal((await h.post('/api/support/messages', { deviceUid: 'd-x', body: '   ' })).status, 400);
  assert.equal((await h.post('/api/support/messages', { body: 'بی‌شناسه' })).status, 400);
});

test('کاربر عادی به فهرست گفت‌وگوهای مدیر نمی‌رسد', async () => {
  const u = await h.newUser('کنجکاو');
  assert.equal((await h.get('/api/admin/support/threads', { token: u.accessToken })).status, 401);
  assert.equal((await h.get('/api/admin/support/threads')).status, 401);
});

test('مدیر گفت‌وگو را می‌بندد و پیام تازه دوباره بازش می‌کند', async () => {
  await h.post('/api/support/messages', { deviceUid: 'closing-1', body: 'یک سؤال' });
  const t = await adminToken();
  const threads = await h.get('/api/admin/support/threads', { token: t });
  const thread = threads.body.threads.find(x => x.deviceUid === 'closing-1');

  const closed = await h.post(`/api/admin/support/threads/${thread.id}/status`,
    { status: 'closed' }, { token: t });
  assert.equal(closed.body.thread.status, 'closed');

  await h.post('/api/support/messages', { deviceUid: 'closing-1', body: 'یک سؤال دیگر' });
  const again = await one('SELECT status FROM support_threads WHERE id=$1', [thread.id]);
  assert.equal(again.status, 'open');
});

test('پیام سامانه‌ای درباره‌ی پایان اشتراک به گفت‌وگوی همان دکان‌دار می‌رود', async () => {
  const u = await h.newUser('نزدیکِ‌پایان');
  await h.post('/api/shop', { name: 'دکان رو به پایان' }, { token: u.accessToken });

  const t = await adminToken();
  const shops = await h.get('/api/admin/shops', { token: t });
  const shop = shops.body.shops.find(s => s.owner_name === 'نزدیکِ‌پایان');

  //  اشتراکی که دو روز دیگر تمام می‌شود
  await h.post('/api/admin/subscriptions', { shopId: shop.id, plan: 'm1', days: 2 }, { token: t });

  const expiring = await h.get('/api/admin/subscriptions/expiring?days=7', { token: t });
  assert.ok(expiring.body.expiring.some(e => e.shopId === shop.id), 'در فهرست رو به پایان نیست');

  const notified = await h.post('/api/admin/subscriptions/notify-expiring', {}, { token: t });
  assert.ok(notified.body.sent >= 1);

  const mine = await h.get('/api/support/thread', { token: u.accessToken });
  assert.ok(mine.body.messages.some(m => m.sender === 'system' && m.body.includes('اشتراک')));

  //  بار دوم پیام تکراری نمی‌رود
  const again = await h.post('/api/admin/subscriptions/notify-expiring', {}, { token: t });
  assert.equal(again.body.sent, 0);
});
