'use strict';
/**
 * اعلانِ وضعیتِ سفارش به مشتری — NTF-01، NTF-03، NTF-04 (`lib/customer-notify.js`، مهاجرتِ ۱۳۹).
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) NTF-01: هر وضعیتِ سفارش یا رویدادِ مشتری دارد یا عمداً بی‌پیام است (وضعیتِ تازه بی تصمیم ⇒
 *      سرخ)؛ هر رویداد کانال‌های مجازِ خودش را به ترتیبِ §۳.۱۲ دارد؛ پیامک فقط برای رویدادِ فوری.
 *   ۲) NTF-03: هر رویداد به fa، ps و en قالب دارد، فقط با {no} و {shop}؛ زبانِ مشتری از ثبتِ
 *      ویترین روی سفارش می‌نشیند و پیام به همان زبان می‌رود.
 *   ۳) ⛔ پرچمِ `shop.customer_notify` خاموش ⇒ هیچ ردیف و هیچ پیام؛ خاموش شدن پس از صف ⇒ cancelled.
 *   ۴) NTF-04: هر رویداد یک ردیف و حداکثر یک پیام؛ زنجیره در اولین «پذیرفت» می‌ایستد؛ پاسخِ مبهم
 *      کانالِ بعدی را صدا نمی‌زند و همان کلید را محدود دوباره می‌فرستد؛ رویدادِ تازه‌تر پیامِ نرفته را
 *      کنار می‌زند؛ سقفِ هر مشتری.
 *   ۵) بی دادهٔ حساس: متن بی نام، شماره، نشانی و مبلغ؛ نمای دکان بی شماره و متن؛ دکانِ دیگر ۴۰۴.
 *   ۶) امروز هیچ سرویس‌دهنده‌ای وصل نیست ⇒ no_channel ِ صادقانه (هیچ پیامِ واقعی).
 */
process.env.RATE_STORE_CHECKOUT_MAX = '200';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');
const orders = require('../src/lib/orders');
const S = require('../src/lib/order-states');
const N = require('../src/lib/customer-notify');

let admin = '';
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  admin = (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
  assert.ok(admin);
});
test.after(async () => {
  N.setProviders(null);
  delete process.env.STORE_PUBLIC_URL;
  await h.stop();
});
test.beforeEach(() => {
  N.setProviders(null);
  delete process.env.STORE_PUBLIC_URL;
});

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

/** دکانِ روشن با ویترینِ «{title}» (و اگر خواستیم، پرچمِ اعلانِ مشتری). */
async function liveShop(label, { notify = true, title = `ویترینِ ${label}` } = {}) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { ...u, token: u.accessToken, shopId: made.body.shop.id, slug: `n-${uniq()}`, title };
  await setFlag(o.shopId, 'shop.orders', true);
  await setFlag(o.shopId, 'shop.storefront', true);
  if (notify) await setFlag(o.shopId, N.FLAG_KEY, true);
  const put = await h.put('/api/shop/storefront', {
    slug: o.slug, enabled: true, title, contactPhone: '0700000001',
  }, { token: o.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  return o;
}

const CUSTOMER = Object.freeze({ type: 'customer', id: '' });
const STAFF = (o) => ({ type: 'user', id: o.user.id, role: 'owner' });
const PHONE = '+93791234567';

/** همان شکلی که قیمت‌گذارِ ویترین به `orders.create` می‌دهد (بی OTP؛ سرتاسری جدا آزموده می‌شود). */
function storefrontInput(phone = PHONE) {
  return {
    source: 'storefront', fulfillment: 'delivery', paymentMethod: 'cod',
    customerName: 'زرمینه', customerPhone: phone,
    address: { lat: 34.5, lng: 69.2, text: 'کوچهٔ گل‌ها، خانهٔ ۷' }, delivery: {}, note: '', isTest: false,
    lines: [{ lineNo: 1, productId: 'rice', name: 'برنج', unit: 'کیلو', qtyMilli: 3000, unitPriceMinor: 12345,
      lineTotalMinor: 37035, note: '', substitution: '' }],
    subtotalMinor: 37035, discountMinor: 0, deliveryFeeMinor: 0, totalMinor: 37035, scheduledFor: null, itemsCount: 1,
  };
}

async function placeOrder(o, { lang, phone } = {}) {
  const out = await orders.create(o.shopId, {
    key: `cn-${uniq()}-key`, body: { lang, n: uniq() }, actor: CUSTOMER, price: async () => storefrontInput(phone),
  });
  return out.order;
}

const go = (o, order, to, extra = {}) => orders.transition(o.shopId, order.id, { to, ...extra }, STAFF(o));
const rowsOf = (orderId) => many('SELECT * FROM customer_notify_outbox WHERE order_id=$1 ORDER BY created_at, id', [orderId]);
const rowOf = async (orderId, event) => one('SELECT * FROM customer_notify_outbox WHERE order_id=$1 AND event=$2', [orderId, event]);

/** سرویس‌دهندهٔ جعلی: هر پیام را نگه می‌دارد؛ `script` پاسخ‌های بعدی را می‌گوید (پیش‌فرض: پذیرفت). */
function fake(script = []) {
  const calls = [];
  return {
    calls,
    send: async (msg) => {
      calls.push(msg);
      const next = script.length ? script.shift() : { status: 'accepted', ref: `ref-${calls.length}` };
      if (next instanceof Error) throw next;
      return next;
    },
  };
}

/* ══════════════════════════════════════════════════════════════════
   ۱) NTF-01: جدولِ «کدام رویداد از کدام کانال»
   ══════════════════════════════════════════════════════════════════ */

/** وضعیت‌هایی که عمداً به مشتری پیام نمی‌دهند — با دلیل. */
const SILENT = {
  picking: 'کارِ درونیِ دکان', packed: 'کارِ درونیِ دکان',
  cancelled_by_customer: 'کارِ خودِ مشتری', return_requested: 'درخواستِ خودِ مشتری',
  returned_to_store: 'کارِ درونی؛ مشتری پیشتر «تحویل نشد» گرفته', restocked: 'کارِ درونیِ دکان',
  refunded_partial: 'دفترِ بازپرداخت (NTF-08)', refunded_full: 'دفترِ بازپرداخت (NTF-08)',
};

test('NTF-01: هر وضعیت یا رویدادِ مشتری دارد یا عمداً بی‌پیام است؛ هر رویداد کانال‌های مجازِ خودش را دارد', () => {
  for (const st of S.ORDER_STATUSES) {
    assert.ok((st in N.EVENT_OF) !== (st in SILENT), `وضعیتِ ${st}: یا رویداد، یا عمداً بی‌پیام — نه هر دو، نه هیچ`);
  }
  for (const st of Object.keys(N.EVENT_OF)) assert.ok(S.ORDER_STATUSES.includes(st), `${st} وضعیتِ واقعی است`);
  assert.deepEqual(new Set(Object.values(N.EVENT_OF)), new Set(N.EVENTS), 'هر رویداد از دست‌کم یک وضعیت می‌آید');
  assert.deepEqual(N.CHANNELS, ['push', 'whatsapp', 'telegram', 'sms'], 'ترتیبِ §۳.۱۲: ارزان ⇒ گران');
  const URGENT = ['order_rejected', 'order_ready', 'order_on_the_way', 'order_delivery_failed', 'order_cancelled'];
  for (const ev of N.EVENTS) {
    const chans = N.MATRIX[ev];
    assert.ok(chans.length > 0, `${ev}: دست‌کم یک کانال`);
    assert.deepEqual(chans, N.CHANNELS.filter((c) => chans.includes(c)), `${ev}: به ترتیبِ زنجیره`);
    assert.equal(chans[0], 'push', `${ev}: اول کانالِ رایگان`);
    assert.equal(chans.includes('sms'), URGENT.includes(ev), `${ev}: پیامک فقط برای رویدادِ فوری`);
  }
});

/* ══════════════════════════════════════════════════════════════════
   ۲) NTF-03: قالب به زبانِ مشتری
   ══════════════════════════════════════════════════════════════════ */

const ARABIC = /[؀-ۿ]/;

test('NTF-03: هر رویداد به fa، ps و en قالب دارد؛ فقط {no} و {shop}؛ en بی حرفِ فارسی؛ رقمِ هر زبان', () => {
  assert.deepEqual(Object.keys(N.TEMPLATES).sort(), ['en', 'fa', 'ps']);
  for (const lang of N.LANGS) {
    assert.deepEqual(Object.keys(N.TEMPLATES[lang]).sort(), [...N.EVENTS].sort(), `${lang}: همهٔ رویدادها`);
    for (const ev of N.EVENTS) {
      const t = N.TEMPLATES[lang][ev];
      assert.deepEqual(t.match(/\{[^}]*\}/g).sort(), ['{no}', '{shop}'], `${lang}.${ev}: فقط شمارهٔ سفارش و نامِ ویترین`);
      assert.equal(ARABIC.test(t), lang !== 'en', `${lang}.${ev}: خطِ همان زبان`);
    }
  }
  assert.equal(N.render('order_accepted', 'fa', { no: 42, shop: 'دکانِ گل' }), '«دکانِ گل» سفارشِ شمارهٔ ۴۲ را قبول کرد.');
  assert.equal(N.render('order_accepted', 'ps', { no: 42, shop: 'دکانِ گل' }), '«دکانِ گل» د فرمایش شمېره ۴۲ ومانه.');
  assert.equal(N.render('order_accepted', 'en', { no: 42, shop: 'Gul' }), 'Gul accepted order #42.');
  assert.equal(N.render('order_accepted', 'xx', { no: 7, shop: 'a' }), N.render('order_accepted', 'fa', { no: 7, shop: 'a' }), 'زبانِ ناشناس ⇒ fa');
  //  نامِ ویترین بی نویسهٔ کنترلی و جهتی (فریبِ متن) و حداکثر ۶۰
  const evil = N.render('order_ready', 'en', { no: 1, shop: `A‮B\nC${'x'.repeat(100)}` });
  assert.ok(!/[‮\n]/.test(evil));
  assert.ok(evil.length < 120);
});

/* ══════════════════════════════════════════════════════════════════
   ۳) پیش‌فرض خاموش
   ══════════════════════════════════════════════════════════════════ */

test('⛔ پرچم خاموش: ثبت و همهٔ گذارها بی هیچ ردیف و بی هیچ پیام؛ فقط زبانِ سفارش ثبت می‌شود', async () => {
  const o = await liveShop('خاموش', { notify: false });
  const p = fake();
  N.setProviders({ push: p, whatsapp: p, telegram: p, sms: p });
  const order = await placeOrder(o, { lang: 'ps' });
  await go(o, order, 'accepted');
  await go(o, order, 'cancelled', { reason: 'out_of_stock' });
  assert.equal((await rowsOf(order.id)).length, 0);
  await N.tick();
  assert.equal(p.calls.length, 0);
  assert.equal((await one('SELECT customer_lang FROM online_orders WHERE id=$1', [order.id])).customer_lang, 'ps');
  //  زبانِ ناشناس ⇒ خالی (نه هر رشته‌ای)
  const other = await placeOrder(o, { lang: 'ru' });
  assert.equal((await one('SELECT customer_lang FROM online_orders WHERE id=$1', [other.id])).customer_lang, '');
});

test('فقط سفارشِ ویترین: سفارشِ دستیِ کارمند (تلفنی) با شماره هم هیچ ردیفی نمی‌سازد', async () => {
  const o = await liveShop('دستی');
  const out = await orders.create(o.shopId, {
    key: `cn-${uniq()}-key`, actor: STAFF(o),
    body: { source: 'phone', fulfillment: 'pickup', customer: { name: 'م', phone: '0791112233' },
      lines: [{ name: 'برنج', qtyMilli: 1000, unitPriceMinor: 5000 }] },
  });
  await go(o, out.order, 'accepted');
  assert.equal((await rowsOf(out.order.id)).length, 0);
});

/* ══════════════════════════════════════════════════════════════════
   ۴) بی سرویس‌دهنده (امروز): no_channel ِ صادقانه
   ══════════════════════════════════════════════════════════════════ */

test('امروز هیچ کانالی وصل نیست ⇒ no_channel با فهرستِ کانال‌های آزموده؛ نمای دکان بی شماره و متن', async () => {
  const o = await liveShop('بی‌کانال');
  const order = await placeOrder(o, { lang: 'en' });
  const [row] = await rowsOf(order.id);
  assert.equal(row.event, 'order_received');
  assert.equal(row.lang, 'en');
  assert.match(row.phone_key, /^[0-9a-f]{64}$/, 'شماره نه، فقط HMAC');
  assert.ok(!JSON.stringify(row).includes('791234567'), 'شماره در صف نیست');

  assert.equal(await N.tick(), 1);
  const done = await rowOf(order.id, 'order_received');
  assert.equal(done.status, 'no_channel');
  assert.deepEqual(done.tried.map((t) => [t.channel, t.result]),
    [['push', 'not_configured'], ['whatsapp', 'not_configured'], ['telegram', 'not_configured']]);
  assert.equal(await N.tick(), 0, 'پایانی است؛ دوباره گرفته نمی‌شود');

  const r = await h.get(`/api/shop/orders/${order.id}/notices`, { token: o.token });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.enabled, true);
  assert.equal(r.body.eligible, true);
  assert.deepEqual(r.body.channels, { push: 'not_configured', whatsapp: 'not_configured', telegram: 'not_configured', sms: 'not_configured' });
  assert.equal(r.body.items.length, 1);
  assert.equal(r.body.items[0].status, 'no_channel');
  const text = JSON.stringify(r.body);
  for (const bad of ['791234567', 'phone_key', 'زرمینه', 'کوچهٔ', '37035']) assert.ok(!text.includes(bad), `بی ${bad}`);

  //  دکانِ دیگر همین سفارش را نمی‌بیند
  const b = await liveShop('دیگر');
  const x = await h.get(`/api/shop/orders/${order.id}/notices`, { token: b.token });
  assert.equal(x.status, 404);
});

/* ══════════════════════════════════════════════════════════════════
   ۵) زنجیره و «یک پیام برای هر رویداد»
   ══════════════════════════════════════════════════════════════════ */

test('NTF-03/04: پوش رد می‌کند ⇒ واتس‌اپ می‌پذیرد ⇒ یک پیام به پشتو و بی دادهٔ حساس؛ تیکِ دوباره هیچ', async () => {
  const o = await liveShop('زنجیره', { title: 'د ګل دوکان' });
  const pushP = fake([{ status: 'rejected', code: 'no_subscription' }]);
  const wa = fake();
  const sms = fake();
  N.setProviders({ push: pushP, whatsapp: wa, sms });
  const order = await placeOrder(o, { lang: 'ps' });
  await go(o, order, 'accepted');
  //  «ثبت شد» پیش از رفتن با «قبول شد» کنار رفت ⇒ فقط پیامِ تازه
  assert.equal((await rowOf(order.id, 'order_received')).status, 'superseded');

  assert.equal(await N.tick(), 1);
  const row = await rowOf(order.id, 'order_accepted');
  assert.equal(row.status, 'sent');
  assert.equal(row.channel, 'whatsapp');
  assert.equal(row.provider_ref, 'ref-1');
  assert.deepEqual(row.tried.map((t) => [t.channel, t.result]), [['push', 'rejected'], ['whatsapp', 'accepted']]);
  assert.equal(pushP.calls.length, 1);
  assert.equal(wa.calls.length, 1);
  assert.equal(sms.calls.length, 0, 'پس از «پذیرفت» زنجیره ایستاد');

  const msg = wa.calls[0];
  assert.equal(msg.text, `«د ګل دوکان» د فرمایش شمېره ${'۰۱۲۳۴۵۶۷۸۹'[order.orderNo]} ومانه.`);
  assert.equal(msg.lang, 'ps');
  assert.equal(msg.key, `${row.id}:whatsapp`);
  assert.equal(msg.to.phone, PHONE, 'شماره فقط به خودِ سرویس‌دهنده');
  assert.equal(msg.link, '', 'بی STORE_PUBLIC_URL ⇒ بی لینک');
  const payload = `${msg.title} ${msg.text} ${msg.link}`;
  for (const bad of ['زرمینه', 'کوچهٔ', '791234567', '370', 'برنج']) assert.ok(!payload.includes(bad), `بی ${bad} در متن`);

  //  تکرارِ همان گذار ⇒ بی ردیفِ دوم؛ تیکِ دوباره ⇒ بی پیامِ دوم
  await go(o, order, 'accepted');
  await N.tick();
  await N.tick(Date.now() + 60 * 60_000);
  assert.equal((await rowsOf(order.id)).length, 2);
  assert.equal(wa.calls.length, 1);
});

test('NTF-04: پاسخِ مبهم کانالِ بعدی را صدا نمی‌زند؛ همان کلید دوباره و فقط یک پیام؛ تلاش‌ها سقف دارند', async () => {
  const o = await liveShop('مبهم');
  const pushP = fake([{ status: 'retry', error: '503' }, new Error('socket hang up')]);
  const wa = fake();
  N.setProviders({ push: pushP, whatsapp: wa });
  const order = await placeOrder(o, { lang: 'fa' });
  const t0 = Date.now();
  await N.tick(t0);
  let row = await rowOf(order.id, 'order_received');
  assert.equal(row.status, 'pending');
  assert.equal(Number(row.attempts), 1);
  assert.equal(wa.calls.length, 0, 'شاید پوش رفته باشد ⇒ واتس‌اپ نه');
  assert.equal(await N.tick(t0 + 1000), 0, 'پیش از فاصله دوباره نمی‌رود');

  await N.tick(t0 + 31_000);
  row = await rowOf(order.id, 'order_received');
  assert.equal(row.status, 'pending');
  assert.equal(Number(row.attempts), 2);
  await N.tick(t0 + 31_000 + 61_000);
  row = await rowOf(order.id, 'order_received');
  assert.equal(row.status, 'sent');
  assert.equal(row.channel, 'push');
  assert.equal(pushP.calls.length, 3);
  assert.deepEqual(new Set(pushP.calls.map((c) => c.key)), new Set([`${row.id}:push`]), 'هر بار همان کلید');
  assert.equal(wa.calls.length, 0);

  //  سقفِ تلاش: همیشه مبهم ⇒ پس از MAX_ATTEMPTS ‌«failed» و دیگر هیچ
  const always = { calls: [], send: async (m) => { always.calls.push(m); return { status: 'retry' }; } };
  N.setProviders({ push: always, whatsapp: wa });
  const o2 = await placeOrder(o, { phone: '+93791234568' });
  let at = Date.now();
  for (let i = 0; i < N.MAX_ATTEMPTS; i++) { await N.tick(at); at += 60 * 60_000 / 8; }
  const failed = await rowOf(o2.id, 'order_received');
  assert.equal(failed.status, 'failed', 'همان تلاشِ آخر پایان است، نه یک «گرفتنِ» دیگر');
  assert.equal(Number(failed.attempts), N.MAX_ATTEMPTS);
  for (let i = 0; i < 3; i++) { await N.tick(at); at += 60 * 60_000 / 8; }
  assert.equal(always.calls.length, N.MAX_ATTEMPTS);
  assert.equal(wa.calls.length, 0);
});

test('رویدادِ تازه پیامِ نرفته را کنار می‌زند؛ تکرارِ رویداد (تحویلِ دوباره) نه پیامِ دوم دارد نه کنار می‌زند', async () => {
  const o = await liveShop('تازه‌تر');
  const p = fake();
  N.setProviders({ push: p });
  const order = await placeOrder(o);
  for (const to of ['accepted', 'picking', 'packed', 'out_for_delivery']) await go(o, order, to);
  const before = await rowsOf(order.id);
  assert.deepEqual(before.map((r) => [r.event, r.status]),
    [['order_received', 'superseded'], ['order_accepted', 'superseded'], ['order_on_the_way', 'pending']]);
  await N.tick();
  assert.deepEqual(p.calls.map((c) => c.event), ['order_on_the_way']);

  await go(o, order, 'delivery_failed', { reason: 'no_answer' });
  await go(o, order, 'retry_scheduled');
  await go(o, order, 'out_for_delivery');      // «در راه» ِ دوم: همان رویداد ⇒ هیچ
  const after = await rowsOf(order.id);
  assert.deepEqual(after.map((r) => [r.event, r.status]).slice(3),
    [['order_delivery_failed', 'superseded'], ['order_delivery_retry', 'pending']]);
  await N.tick();
  assert.deepEqual(p.calls.map((c) => c.event), ['order_on_the_way', 'order_delivery_retry']);
});

test('پیامک فقط برای رویدادِ فوری: تنها پیامک وصل ⇒ «قبول شد» no_channel، «لغو شد» با پیامک', async () => {
  const o = await liveShop('پیامک');
  const sms = fake();
  N.setProviders({ sms });
  const order = await placeOrder(o);
  await go(o, order, 'accepted');
  await N.tick();
  assert.equal((await rowOf(order.id, 'order_accepted')).status, 'no_channel');
  await go(o, order, 'cancelled', { reason: 'out_of_stock' });
  await N.tick();
  const row = await rowOf(order.id, 'order_cancelled');
  assert.equal(row.status, 'sent');
  assert.equal(row.channel, 'sms');
  assert.deepEqual(sms.calls.map((c) => c.event), ['order_cancelled']);
});

/* ══════════════════════════════════════════════════════════════════
   ۶) قطعِ فوری، کهنگی و سقفِ هر مشتری
   ══════════════════════════════════════════════════════════════════ */

test('پرچم پس از صف خاموش شد ⇒ cancelled و هیچ پیام؛ ردیفِ کهنه ⇒ expired', async () => {
  const o = await liveShop('قطع');
  const p = fake();
  N.setProviders({ push: p });
  const order = await placeOrder(o);
  await setFlag(o.shopId, N.FLAG_KEY, false);
  await N.tick();
  assert.equal((await rowOf(order.id, 'order_received')).status, 'cancelled');
  assert.equal((await rowOf(order.id, 'order_received')).last_error, 'flag_off');

  await setFlag(o.shopId, N.FLAG_KEY, true);
  const old = await placeOrder(o, { phone: '+93791234569' });
  await N.tick(Date.now() + N.MAX_AGE_MS + 1000);
  assert.equal((await rowOf(old.id, 'order_received')).status, 'expired');
  assert.equal(p.calls.length, 0);
});

test('سقفِ هر مشتری روی همهٔ دکان‌ها (‎+930…‎ همان شماره) ⇒ rate_limited؛ مشتریِ دیگر آزاد', async () => {
  const o = await liveShop('سقف');
  const p = fake();
  N.setProviders({ push: p });
  const phone = '+93795550001';
  //  PER_HOUR پیامِ رفته در همین ساعت، از دکانِ دیگری
  const b = await liveShop('سقف ب');
  const prior = await placeOrder(b, { phone });
  const key = N.phoneKey('+930795550001');
  assert.equal(key, N.phoneKey(phone));
  const at = Date.now();
  await N.tick(at);   // ردیفِ «ثبت شد» ِ همان سفارشِ پیشین می‌رود (یک پیام) — سقف از همین هم می‌شمارد
  assert.equal((await rowOf(prior.id, 'order_received')).status, 'sent');
  for (let i = 1; i < N.PER_HOUR; i++) {
    await query(
      `INSERT INTO customer_notify_outbox (id, shop_id, order_id, event, lang, phone_key, status, next_attempt_at,
         sent_at, created_at, updated_at)
       VALUES ($1,$2,$3,$4,'fa',$5,'sent',$6,$6,$6,$6) ON CONFLICT DO NOTHING`,
      [`x${uniq()}`.padEnd(26, '0').slice(0, 26), b.shopId, prior.id, N.EVENTS[i + 1], key, at - 1000]
    );
  }
  const order = await placeOrder(o, { phone: '+930795550001' });
  const other = await placeOrder(o, { phone: '+93795550002' });
  await N.tick();
  assert.equal((await rowOf(order.id, 'order_received')).status, 'rate_limited');
  assert.equal((await rowOf(other.id, 'order_received')).status, 'sent');
  assert.deepEqual(p.calls.map((c) => c.to.phone), [phone, '+93795550002']);
});

/* ══════════════════════════════════════════════════════════════════
   ۷) سرتاسری: زبان از ثبتِ ویترین و لینکِ پیگیری
   ══════════════════════════════════════════════════════════════════ */

test('سرتاسری: ثبت از ویترین با lang=en ⇒ پیامِ انگلیسی با لینکِ همان صفحهٔ «سفارشِ من» (‎?lang=en‎)', async () => {
  const o = await liveShop('سرتاسری', { title: 'Gul Store' });
  const push = (ops) => h.api('POST', '/api/sync/v1/push', {
    token: o.token, headers: { 'X-App': 'shop', 'X-Device': 'dev-cn' },
    body: { device_id: 'dev-cn', schema_version: 2, ops },
  });
  const pr = await push([{ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: 'rice', type: 'insert',
    fields: { name: 'برنج', unit: 'کیلو', salePrice: 120, category: 'خوراکی' } }]);
  assert.equal(pr.status, 200, JSON.stringify(pr.body));
  assert.equal((await h.put('/api/shop/storefront/products/rice', { published: true }, { token: o.token })).status, 200);

  const phone = '0791230099';
  const s = await h.post(`/api/store/${o.slug}/verify/start`, { phone });
  const c = await h.post(`/api/store/${o.slug}/verify/check`, { phone, code: s.body.devCode });
  assert.equal(c.status, 200, JSON.stringify(c.body));
  const r = await h.post(`/api/store/${o.slug}/checkout`, {
    fulfillment: 'pickup', customer: { name: 'Zarmina', phone }, lines: [{ productId: 'rice', qtyMilli: 1000 }], lang: 'en',
  }, { headers: { 'Idempotency-Key': `cn-${uniq()}-key`, 'X-Store-Ticket': c.body.ticket } });
  assert.equal(r.status, 201, JSON.stringify(r.body));

  process.env.STORE_PUBLIC_URL = 'https://vill3n.example/store/';
  const p = fake();
  N.setProviders({ push: p });
  await N.tick();
  const orderId = r.body.order.token.split('.')[0];
  const mine = p.calls.filter((m) => m.orderId === orderId);
  assert.equal(mine.length, 1);
  const msg = mine[0];
  assert.equal(msg.text, `Order #${r.body.order.orderNo} at Gul Store is placed — waiting for the shop.`);
  assert.equal(msg.link, `https://vill3n.example/store/${o.slug}?lang=en#/o/${r.body.order.token}`);
  //  لینک همان صفحهٔ پیگیری است
  assert.equal((await h.get(`/api/store/${o.slug}/orders/${r.body.order.token}`)).status, 200);
  //  نشانیِ غیرِ https ⇒ بی لینک
  process.env.STORE_PUBLIC_URL = 'http://vill3n.example/store';
  assert.equal(N.linkFor({ shopId: o.shopId, orderId, slug: o.slug, lang: 'fa' }), '');
});
