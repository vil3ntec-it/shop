'use strict';
/**
 * NOTIF-11 — کانالِ پشتیبانِ اعلانِ سفارش = ایمیلِ سرور به ایمیلِ تأییدشدهٔ صاحب (`lib/order-notify-email.js`،
 * پرچمِ خاموشِ `shop.order_notify_email`، مهاجرتِ ۹۴۴).
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) ⛔ پرچمِ خاموش ⇒ بایت‌به‌بایت همان پیشین: `fallback_state = not_configured`، `/config` ⇒ `not_configured`، هیچ نامه —
 *      حتی با ایمیلِ سرورِ آماده.
 *   ۲) روشن ولی ایمیلِ سرور پیکربندی‌نشده (`log` یا SMTP ِ ناقص) ⇒ هیچ نامه و حالِ صادقانهٔ «پیکربندی نشده».
 *   ۳) روشن و آماده ⇒ پوشِ نرسیده (و «کسی باز نکرد») دقیقاً **یک** نامه به ایمیلِ صاحب؛ بی نام، شماره یا اقلامِ مشتری.
 *   ۴) سقفِ روزانهٔ هر دکان (`shop.order_notify_email.per_day`) ⇒ `throttled`؛ پس از ۲۴ ساعت دوباره؛ دکانِ دیگر جدا.
 *   ۵) خطای سرویس ⇒ `provider_error`، یک تلاش (و همان در سقف شمرده)؛ صاحبِ بسته ⇒ `no_email`.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, many, newId, now } = require('../src/db');
const mailer = require('../src/lib/mailer');
const push = require('../src/lib/push');
const orders = require('../src/lib/orders');
const N = require('../src/lib/order-notify');
const E = require('../src/lib/order-notify-email');

const READY = Object.freeze({
  provider: 'smtp', host: 'smtp.test.local', port: '587', user: 'u', pass: 'p', from: 'shop@test.local', fromName: 'دکان', secure: 'starttls',
});
let admin = '';
let mails = [];
let mailFail = null;
const keep = { send: mailer.send, current: mailer.current };

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  admin = (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
  push.setDeliver(() => { throw new Error('FCM 503'); });
});
test.after(async () => {
  Object.assign(mailer, keep);
  push.setDeliver(null);
  await h.stop();
});

/** ایمیلِ سرور: `cfg` ⇒ آماده/ناآماده؛ هر نامه در `mails` */
function mailWith(cfg) {
  mailer.current = async () => cfg;
  mailer.send = async (m) => {
    if (mailFail) throw mailFail;
    mails.push(m);
    return { delivered: true };
  };
}
const restoreMail = () => Object.assign(mailer, keep);

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

let keySeq = 0;
const customerOrder = (shopId) => orders.create(shopId, {
  key: `one-key-${++keySeq}-${Date.now()}`,
  body: {
    source: 'phone', fulfillment: 'pickup', paymentMethod: 'cod',
    customer: { name: 'احمدِ مشتری', phone: '0791234567' },
    lines: [{ name: 'برنجِ ویژه', qtyMilli: 1000, unitPriceMinor: 5000 }],
  },
  actor: { type: 'customer', id: '' },
});

/** دکانِ روشن با `shop.order_notify` و یک دستگاهِ ثبت‌شده (پوشش همیشه خطا) */
async function shop(label, { email = true } = {}) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const s = { ...u, token: u.accessToken, shopId: made.body.shop.id };
  assert.equal((await h.post('/api/me/push', { token: `tok-${s.shopId}` }, { token: s.token })).status, 201);
  await setFlag(s.shopId, N.FLAG_KEY, true);
  if (email) await setFlag(s.shopId, E.FLAG_KEY, true);
  return s;
}

const rowsOf = (shopId) => many('SELECT * FROM order_notify_outbox WHERE shop_id = $1 ORDER BY created_at, id', [shopId]);
const mailsTo = (s) => mails.filter((m) => m.to === s.email);

/** تیک تا همهٔ ردیف‌های این دکان از صف بیرون بروند (پوشِ خراب ⇒ MAX_ATTEMPTS ⇒ failed + کانالِ پشتیبان) */
async function drive(shopId, t0 = now() + 1000) {
  let t = t0;
  for (let i = 0; i < N.MAX_ATTEMPTS + 4; i++) {
    await N.tick(t);
    if ((await rowsOf(shopId)).every((r) => !['pending', 'sending', 'provider_accepted'].includes(r.status))) break;
    t += 10 * 60_000;
  }
  return t;
}

test('NOTIF-11 ⛔ پرچمِ خاموش ⇒ همان پیشین: not_configured، `/config` بی تغییر، هیچ نامه — حتی با ایمیلِ سرورِ آماده', async () => {
  const s = await shop('پشتیبانِ خاموش', { email: false });
  mails = [];
  mailWith(READY);
  try {
    const before = await h.get('/api/shop/order-notify/config', { token: s.token });
    assert.equal(before.status, 200);
    assert.deepEqual(before.body.fallback, { email: 'not_configured' });
    await customerOrder(s.shopId);
    await drive(s.shopId);
    const [row] = await rowsOf(s.shopId);
    assert.equal(row.status, 'failed');
    assert.deepEqual([row.fallback_channel, row.fallback_state], ['email', 'not_configured']);
    assert.ok(row.fallback_at);
    assert.equal(mailsTo(s).length, 0, 'بی پرچم هیچ نامه');
    assert.deepEqual((await h.get('/api/shop/order-notify/config', { token: s.token })).body, before.body, 'پاسخِ `/config` بایت‌به‌بایت همان');
  } finally { restoreMail(); }
});

test('NOTIF-11: روشن ولی ایمیلِ سرور پیکربندی‌نشده (log یا SMTP ِ ناقص) ⇒ هیچ نامه و حالِ «پیکربندی نشده»', async () => {
  for (const cfg of [{ provider: 'log' }, { ...READY, host: '' }]) {
    const s = await shop(`بی ایمیلِ سرور ${cfg.provider}${cfg.host === '' ? '-ناقص' : ''}`);
    mails = [];
    mailWith(cfg);
    try {
      assert.equal((await h.get('/api/shop/order-notify/config', { token: s.token })).body.fallback.email, 'not_configured');
      await customerOrder(s.shopId);
      await drive(s.shopId);
      const [row] = await rowsOf(s.shopId);
      assert.deepEqual([row.status, row.fallback_channel, row.fallback_state], ['failed', 'email', 'not_configured']);
      assert.equal(mails.length, 0, 'هیچ تلاشی برای فرستادن');
    } finally { restoreMail(); }
  }
});

test('NOTIF-11: پوشِ نرسیده ⇒ دقیقاً یک نامه به ایمیلِ صاحب، بی دادهٔ مشتری؛ تیک‌های بعدی نامهٔ دوم نمی‌سازند', async () => {
  const s = await shop('پشتیبانِ ایمیل');
  mails = [];
  mailWith(READY);
  try {
    const cfg = await h.get('/api/shop/order-notify/config', { token: s.token });
    assert.equal(cfg.body.fallback.email, 'configured');
    const out = await customerOrder(s.shopId);
    const t = await drive(s.shopId);
    const [row] = await rowsOf(s.shopId);
    assert.deepEqual([row.status, row.fallback_channel, row.fallback_state], ['failed', 'email', 'provider_accepted']);
    const mine = mailsTo(s);
    assert.equal(mine.length, 1, 'یک نامه');
    const m = mine[0];
    assert.match(m.subject, /سفارشِ تازه — شمارهٔ/);
    assert.ok(m.subject.includes(`دکانِ پشتیبانِ ایمیل`), 'نامِ دکان برای صاحبِ چند دکان');
    assert.match(m.text, /به هیچ گوشیِ دکان نرسید/);
    const all = `${m.subject}\n${m.text}\n${m.html}`;
    for (const secret of ['احمد', '0791234567', '791234567', 'برنج', '5000', '۵۰۰۰', out.order.id]) {
      assert.ok(!all.includes(secret), `دادهٔ مشتری/سفارش در نامه: ${secret}`);
    }
    //  پس از failed و یک روزِ دیگر: باز همان یک نامه
    await N.tick(t + 24 * 3600_000);
    await N.tick(t + 48 * 3600_000);
    assert.equal(mailsTo(s).length, 1, 'نامهٔ تکراری نه');
    assert.equal((await h.get(`/api/shop/order-notify/config`, { token: s.token })).body.fallback.email, 'configured');
  } finally { restoreMail(); }
});

test('NOTIF-11: «کسی باز نکرد» ⇒ پس از یادآوری‌ها همان یک نامه با دلیلِ «باز نشده»', async () => {
  const s = await shop('بازنشده');
  push.setDeliver(() => {});   //  پوش پذیرفته می‌شود، ولی هیچ رسیدی نمی‌آید
  mails = [];
  mailWith(READY);
  try {
    await customerOrder(s.shopId);
    let t = now() + 1000;
    await N.tick(t);
    for (let i = 0; i < N.MAX_REMINDERS + 3; i++) { t += N.ACK_WINDOW_MS; await N.tick(t); }
    const [row] = await rowsOf(s.shopId);
    assert.deepEqual([row.status, row.fallback_state], ['unacked', 'provider_accepted']);
    const mine = mailsTo(s);
    assert.equal(mine.length, 1);
    assert.match(mine[0].text, /هنوز در برنامهٔ دکان باز نشده/);
  } finally {
    restoreMail();
    push.setDeliver(() => { throw new Error('FCM 503'); });
  }
});

test('NOTIF-11: سقفِ روزانهٔ هر دکان ⇒ throttled؛ پس از ۲۴ ساعت دوباره؛ دکانِ دیگر سقفِ جدا دارد', async () => {
  const s = await shop('سقفِ ایمیل');
  const other = await shop('سقفِ دکانِ دیگر');
  await setFlag(s.shopId, E.PER_DAY_KEY, 2);
  assert.equal(await E.perDay(s.shopId), 2);
  assert.equal(await E.perDay(other.shopId), E.DEFAULT_PER_DAY, 'بی ردیف ⇒ پیش‌فرضِ ۳');
  mails = [];
  mailWith(READY);
  try {
    const t0 = now() + 1000;
    for (let i = 0; i < 3; i++) await customerOrder(s.shopId);
    await customerOrder(other.shopId);
    await drive(s.shopId, t0);
    await drive(other.shopId, t0);
    const states = (await rowsOf(s.shopId)).map((r) => r.fallback_state).sort();
    assert.deepEqual(states, ['provider_accepted', 'provider_accepted', 'throttled']);
    assert.equal(mailsTo(s).length, 2, 'حداکثر ۲ نامه در ۲۴ ساعت');
    assert.equal(mailsTo(other).length, 1, 'سقفِ دکانِ دیگر جداست');

    //  روزِ بعد: سفارشِ تازه دوباره نامه می‌گیرد
    await customerOrder(s.shopId);
    await drive(s.shopId, t0 + 25 * 3600_000);
    assert.equal(mailsTo(s).length, 3, 'پس از ۲۴ ساعت دوباره');
    //  مقدارِ نامعتبر ⇒ پیش‌فرض
    for (const bad of [0, 51, 2.5, 'x']) assert.equal(E.perDayOf(bad), E.DEFAULT_PER_DAY, String(bad));
  } finally { restoreMail(); }
});

test('NOTIF-11: خطای سرویسِ ایمیل ⇒ provider_error با یک تلاش (در سقف شمرده)؛ صاحبِ بسته ⇒ no_email', async () => {
  const s = await shop('خطای ایمیل');
  await setFlag(s.shopId, E.PER_DAY_KEY, 1);
  mails = [];
  mailWith(READY);
  let calls = 0;
  mailer.send = async () => { calls += 1; throw Object.assign(new Error('mailbox unavailable for x@y'), { smtpCode: 550 }); };
  try {
    const t0 = now() + 1000;
    await customerOrder(s.shopId);
    await drive(s.shopId, t0);
    assert.equal(calls, 1, 'یک تلاش');
    assert.equal((await rowsOf(s.shopId))[0].fallback_state, 'provider_error');
    //  تلاشِ ناموفق هم جای سقف را گرفت ⇒ دومی throttled، بی صدا زدن
    await customerOrder(s.shopId);
    await drive(s.shopId, t0);
    assert.deepEqual((await rowsOf(s.shopId)).map((r) => r.fallback_state).sort(), ['provider_error', 'throttled']);
    assert.equal(calls, 1);

    restoreMail();   //  ثبت‌نامِ صاحبِ تازه کدِ آزمایشی را از راهِ `log` می‌گیرد
    const closed = await shop('صاحبِ بسته');
    mailWith(READY);
    await query("UPDATE users SET status = 'disabled' WHERE email = $1", [closed.email]);
    assert.equal(await E.ownerEmailOf(closed.shopId), '');
    await customerOrder(closed.shopId);
    await drive(closed.shopId, t0);
    assert.equal((await rowsOf(closed.shopId))[0].fallback_state, 'no_email');
    assert.equal(mailsTo(closed).length, 0);
  } finally { restoreMail(); }
});
