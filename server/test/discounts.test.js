'use strict';
/**
 * تخفیف، کمپین، افزونه و تاریخچهٔ قیمت — بخشِ ۱۱.۳.۲ی پرامپت.
 *
 * ── خواستهٔ صاحب سامانه ────────────────────────────────────────────
 *   «کدهای تخفیف: درصدی یا مبلغی، محدود به برنامه / پلن / مشتری خاص،
 *    تاریخ انقضا، سقف تعداد استفاده، یک‌بار برای هر مشتری… تخفیف
 *    مستقیم برای یک مشتری… کمپین… ویژگی‌های اضافه (Add-on) به‌صورت
 *    Feature Flag در لایسنس… تاریخچهٔ قیمت‌ها نگه داشته شود؛
 *    اشتراک‌های قبلی با قیمت زمان خرید خودشان می‌مانند.»
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) ⛔ **هر پرس‌وجویی روی کد و پلن `app` را شرط می‌کند** — کدِ دکان
 *      روی پمپ کار نمی‌کند و برعکس. بدترین جای قاطی شدن، چون پول است.
 *   ۲) قیمت فقط از سرور: هیچ عددِ ثابتی، و تخفیف روی قیمتِ **روز**.
 *   ۳) عوض شدنِ قیمتِ پلن، اشتراکِ فروخته‌شده را دست نمی‌زند.
 *   ۴) افزونه فقط **به** فهرستِ قابلیت‌ها اضافه می‌شود — قاعدهٔ «فهرستِ
 *      خالی = پلنِ کامل» دست نمی‌خورد.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');

const DAY = 24 * 60 * 60 * 1000;

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

async function adminToken() {
  const r = await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' });
  return r.body.token;
}

async function shopOwner(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { ...u, shopId: made.body.shop.id };
}

async function pumpOwner(name, code) {
  const u = await h.newUser(name, 'pump');
  const made = await h.post('/api/pump', { name: `پمپِ ${name}`, code }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { ...u, stationId: made.body.station.id };
}

/* ══════════════════════════════════════════════════════════════════
   ۱) کدهای تخفیف — و مرزِ دو بخش
   ══════════════════════════════════════════════════════════════════ */

test('کدِ درصدی و مبلغی ساخته می‌شود؛ درصدِ بیرون از ۱ تا ۱۰۰ رد می‌شود', async () => {
  const t = await adminToken();
  const pct = await h.post('/api/admin/discount-codes',
    { app: 'shop', code: 'eid20', kind: 'percent', value: 20, note: 'عید' }, { token: t });
  assert.equal(pct.status, 201, JSON.stringify(pct.body));
  assert.equal(pct.body.code.code, 'EID20', 'کد همیشه بزرگ و یکدست می‌شود');
  assert.equal(pct.body.code.currency, 'AFN', 'ارزِ کدِ دکان افغانی است');

  const amt = await h.post('/api/admin/discount-codes',
    { app: 'pump', kind: 'amount', value: 25 }, { token: t });
  assert.equal(amt.status, 201);
  assert.ok(amt.body.code.code.length >= 4, 'کدِ نیامده خودش ساخته می‌شود');
  assert.equal(amt.body.code.currency, 'USD', 'ارزِ کدِ پمپ دالر است');

  for (const bad of [{ kind: 'percent', value: 0 }, { kind: 'percent', value: 120 }, { kind: 'amount', value: 0 }]) {
    const r = await h.post('/api/admin/discount-codes', { app: 'shop', ...bad }, { token: t });
    assert.equal(r.status, 400, JSON.stringify(bad));
  }

  const dup = await h.post('/api/admin/discount-codes',
    { app: 'shop', code: 'EID20', kind: 'percent', value: 10 }, { token: t });
  assert.equal(dup.status, 400, 'کدِ تکراری در همان بخش ساخته نمی‌شود');

  //  ⛔ ولی همان رشته در بخشِ دیگر کدِ **دیگری** است
  const otherApp = await h.post('/api/admin/discount-codes',
    { app: 'pump', code: 'EID20', kind: 'percent', value: 10 }, { token: t });
  assert.equal(otherApp.status, 201, 'کدِ دکان و کدِ پمپ دو دفترِ جدا هستند');
});

test('⛔ کدِ یک بخش در بخشِ دیگر پیدا نمی‌شود', async () => {
  const t = await adminToken();
  await h.post('/api/admin/discount-codes', { app: 'shop', code: 'ONLYSHOP', kind: 'percent', value: 50 }, { token: t });
  const wrong = await h.post('/api/admin/discount-codes/quote', { code: 'ONLYSHOP', app: 'pump' }, { token: t });
  assert.equal(wrong.status, 404, 'کدِ دکان روی پمپ نباید پیدا شود');
  const right = await h.post('/api/admin/discount-codes/quote', { code: 'ONLYSHOP', app: 'shop' }, { token: t });
  assert.equal(right.status, 200);
});

test('قیمتِ نهایی از قیمتِ سرور حساب می‌شود، نه از عددِ برنامه', async () => {
  const t = await adminToken();
  const plans = await h.get('/api/plans');
  const m1 = plans.body.plans.find(p => p.code === 'm1');
  assert.ok(m1, 'پلنِ ماهانه باید باشد');

  await h.post('/api/admin/discount-codes', { app: 'shop', code: 'HALF', kind: 'percent', value: 50 }, { token: t });
  const q = await h.post('/api/admin/discount-codes/quote', { code: 'HALF', app: 'shop', plan: 'm1' }, { token: t });
  assert.equal(q.status, 200);
  assert.equal(q.body.price, m1.price, 'قیمتِ پایه همان قیمتِ سرور است');
  assert.equal(q.body.finalPrice, m1.price - Math.round(m1.price * 50 / 100));
  assert.equal(q.body.savings, q.body.price - q.body.finalPrice);
  //  فهرستِ همهٔ پلن‌ها هم با همان کد حساب می‌شود
  assert.ok(q.body.prices.length >= 1);
});

test('مهلت، سقفِ استفاده، «یک‌بار برای هر مشتری» و «مالِ یک مشتری»', async () => {
  const t = await adminToken();
  const a = await shopOwner('کددار');
  const b = await shopOwner('کدنداشته');

  //  مهلتِ گذشته اصلاً ساخته نمی‌شود
  const past = await h.post('/api/admin/discount-codes',
    { app: 'shop', kind: 'percent', value: 10, expiresAt: now() - DAY }, { token: t });
  assert.equal(past.status, 400);

  //  مالِ یک مشتری
  const mine = await h.post('/api/admin/discount-codes',
    { app: 'shop', code: 'MINE', kind: 'percent', value: 30, userId: a.user.id }, { token: t });
  assert.equal(mine.status, 201);
  const forOther = await h.post('/api/portal/redeem', { code: 'MINE', plan: 'm1', app: 'shop' }, { token: b.accessToken });
  assert.equal(forOther.status, 403, 'کدِ شخصی برای دیگری کار نمی‌کند');
  const forMe = await h.post('/api/portal/redeem', { code: 'MINE', plan: 'm1', app: 'shop' }, { token: a.accessToken });
  assert.equal(forMe.status, 200, JSON.stringify(forMe.body));
  assert.ok(forMe.body.finalPrice < forMe.body.price);
  assert.ok(forMe.body.currencyLabel, 'واحدِ پول هم از سرور می‌آید');

  //  سقفِ استفاده
  const limited = await h.post('/api/admin/discount-codes',
    { app: 'shop', code: 'ONEONLY', kind: 'percent', value: 10, maxUses: 1 }, { token: t });
  const codeId = limited.body.code.id;
  const sub = await h.post('/api/admin/subscriptions',
    { shopId: a.shopId, plan: 'm1', days: 30, discountCode: 'ONEONLY' }, { token: t });
  assert.equal(sub.status, 201, JSON.stringify(sub.body));
  assert.ok(sub.body.discount, 'کد باید خرج شده باشد');
  const after = (await h.get('/api/admin/discount-codes?app=shop', { token: t }))
    .body.codes.find(c => c.id === codeId);
  assert.equal(after.uses, 1);
  const again = await h.post('/api/portal/redeem', { code: 'ONEONLY', plan: 'm1', app: 'shop' }, { token: b.accessToken });
  assert.equal(again.status, 403, 'سقف پر شده');

  //  یک‌بار برای هر مشتری
  const once = await h.post('/api/admin/discount-codes',
    { app: 'shop', code: 'ONCEPER', kind: 'percent', value: 10, oncePerCustomer: true }, { token: t });
  await h.post('/api/admin/subscriptions',
    { shopId: a.shopId, plan: 'm1', days: 30, discountCode: 'ONCEPER' }, { token: t });
  const twice = await h.post('/api/portal/redeem', { code: 'ONCEPER', plan: 'm1', app: 'shop' }, { token: a.accessToken });
  assert.equal(twice.status, 403);
  assert.equal(twice.body.error.code, 'code_used');
  //  مشتریِ دیگر همچنان می‌تواند
  const other = await h.post('/api/portal/redeem', { code: 'ONCEPER', plan: 'm1', app: 'shop' }, { token: b.accessToken });
  assert.equal(other.status, 200);
  assert.ok(once.body.code.id);
});

test('کدِ باطل‌شده دیگر کار نمی‌کند', async () => {
  const t = await adminToken();
  const made = await h.post('/api/admin/discount-codes',
    { app: 'shop', code: 'KILLME', kind: 'percent', value: 15 }, { token: t });
  const ok = await h.post('/api/admin/discount-codes/quote', { code: 'KILLME', app: 'shop' }, { token: t });
  assert.equal(ok.status, 200);
  const rev = await h.post(`/api/admin/discount-codes/${made.body.code.id}/revoke`, {}, { token: t });
  assert.equal(rev.status, 200);
  assert.equal(rev.body.code.status, 'revoked');
  const gone = await h.post('/api/admin/discount-codes/quote', { code: 'KILLME', app: 'shop' }, { token: t });
  assert.equal(gone.status, 403);
});

/* ══════════════════════════════════════════════════════════════════
   ۲) قیمتِ خودِ اشتراک و تاریخچهٔ قیمت
   ══════════════════════════════════════════════════════════════════ */

test('⛔ عوض شدنِ قیمتِ پلن، اشتراکِ فروخته‌شده را دست نمی‌زند', async () => {
  const t = await adminToken();
  const u = await shopOwner('قیمتی');
  const before = (await h.get('/api/plans')).body.plans.find(p => p.code === 'y1');

  const sub = await h.post('/api/admin/subscriptions', { shopId: u.shopId, plan: 'y1', days: 365 }, { token: t });
  assert.equal(sub.status, 201);
  assert.equal(Number(sub.body.subscription.price), before.price,
    'قیمتِ روزِ خرید روی خودِ اشتراک می‌نشیند');

  const changed = await h.patch('/api/admin/plans/y1', { price: before.price + 5000, app: 'shop' }, { token: t });
  assert.equal(changed.status, 200, JSON.stringify(changed.body));

  //  قیمتِ تازه در فهرستِ پلن‌ها هست…
  const nowPlans = (await h.get('/api/plans')).body.plans.find(p => p.code === 'y1');
  assert.equal(nowPlans.fullPrice, before.price + 5000);

  //  …ولی اشتراکِ فروخته‌شده همان قیمتِ خودش را دارد
  const row = await one('SELECT price FROM subscriptions WHERE id=$1', [sub.body.subscription.id]);
  assert.equal(Number(row.price), before.price);

  //  و تاریخچهٔ قیمت ردیفش را دارد
  const hist = await h.get('/api/admin/plans/y1/price-history?app=shop', { token: t });
  assert.equal(hist.status, 200);
  assert.ok(hist.body.history.length >= 1);
  assert.equal(hist.body.history[0].price, before.price + 5000);
  assert.equal(hist.body.history[0].prevPrice, before.price);
  assert.equal(hist.body.history[0].app, 'shop', '⛔ تاریخچه به بخش بسته است');
});

test('تخفیفِ مستقیم روی یک اشتراک، با دلیل، در تاریخچه می‌نشیند', async () => {
  const t = await adminToken();
  const u = await shopOwner('تخفیف‌دار');
  const sub = await h.post('/api/admin/subscriptions', { shopId: u.shopId, plan: 'm1', days: 30 }, { token: t });
  const id = sub.body.subscription.id;
  const base = Number(sub.body.subscription.price);

  const noReason = await h.post(`/api/admin/subscriptions/${id}/discount`, { percent: 30 }, { token: t });
  assert.equal(noReason.status, 400, '⛔ تخفیفِ بی دلیل ثبت نمی‌شود');

  const done = await h.post(`/api/admin/subscriptions/${id}/discount`,
    { percent: 30, reason: 'مشتریِ قدیمی' }, { token: t });
  assert.equal(done.status, 200, JSON.stringify(done.body));
  assert.equal(done.body.price, base);
  assert.equal(done.body.finalPrice, base - Math.round(base * 30 / 100));

  const hist = await one(
    `SELECT * FROM subscription_history WHERE subscription_id=$1 AND action='discount'`, [id]);
  assert.ok(hist, 'ردیفِ تخفیف باید در تاریخچه باشد');
  assert.ok(hist.note.includes('مشتریِ قدیمی'), 'دلیل در یادداشت می‌ماند');
});

/* ══════════════════════════════════════════════════════════════════
   ۳) افزونه‌ها — Feature Flag
   ══════════════════════════════════════════════════════════════════ */

test('افزونه فقط به فهرستِ قابلیت‌ها اضافه می‌شود و برداشتنش همان لحظه می‌بندد', async () => {
  const t = await adminToken();
  const u = await shopOwner('افزونه‌دار');
  //  پلنِ محدود: فهرستِ نام‌دار، نه خالی
  const sub = await h.post('/api/admin/subscriptions',
    { shopId: u.shopId, plan: 'm1', days: 30, features: ['sales'] }, { token: t });
  const id = sub.body.subscription.id;

  const before = await h.get('/api/me', { token: u.accessToken });
  assert.ok(before.body.entitlement.features.includes('sales'));
  assert.ok(!before.body.entitlement.features.includes('barcode'), 'پیش از افزونه بسته است');

  const bad = await h.post(`/api/admin/subscriptions/${id}/addons`, { feature: 'ماه-در-آسمان' }, { token: t });
  assert.equal(bad.status, 400, '⛔ قابلیتِ بیرونِ کاتالوگ افزونه نمی‌شود');

  const add = await h.post(`/api/admin/subscriptions/${id}/addons`,
    { feature: 'barcode', price: 500, note: 'فروشِ جدا' }, { token: t });
  assert.equal(add.status, 201, JSON.stringify(add.body));

  const after = await h.get('/api/me', { token: u.accessToken });
  assert.ok(after.body.entitlement.features.includes('barcode'), 'افزونه همان لحظه باز می‌کند');
  assert.ok(after.body.entitlement.features.includes('sales'), '⛔ قابلیت‌های خودِ پلن دست نمی‌خورند');

  //  دوباره زدن ردیفِ دوم نمی‌سازد
  const twice = await h.post(`/api/admin/subscriptions/${id}/addons`, { feature: 'barcode' }, { token: t });
  assert.equal(twice.body.addon.id, add.body.addon.id);

  const list = await h.get(`/api/admin/subscriptions/${id}/addons`, { token: t });
  assert.equal(list.body.addons.length, 1);

  const gone = await h.del(`/api/admin/subscriptions/${id}/addons/${add.body.addon.id}`, { token: t });
  assert.equal(gone.status, 200);
  const closed = await h.get('/api/me', { token: u.accessToken });
  assert.ok(!closed.body.entitlement.features.includes('barcode'), 'برداشتنِ افزونه همان لحظه می‌بندد');
});

test('⛔ «فهرستِ خالی = پلنِ کامل» با آمدنِ افزونه‌ها دست نخورد', async () => {
  const t = await adminToken();
  const u = await shopOwner('کامل');
  await h.post('/api/admin/subscriptions', { shopId: u.shopId, plan: 'm1', days: 30, features: [] }, { token: t });
  const me = await h.get('/api/me', { token: u.accessToken });
  const cat = require('../src/lib/features');
  const paid = cat.forApp ? cat.forApp('shop').PAID_KEYS : cat.PAID_KEYS;
  for (const k of paid) {
    assert.ok(me.body.entitlement.features.includes(k), `فهرستِ خالی یعنی ${k} هم باز است`);
  }
});

test('افزونهٔ پمپ روی اشتراکِ دکان نمی‌نشیند', async () => {
  const t = await adminToken();
  const u = await shopOwner('مرزی');
  const sub = await h.post('/api/admin/subscriptions', { shopId: u.shopId, plan: 'm1', days: 30 }, { token: t });
  //  مسیرِ پمپ با شناسهٔ اشتراکِ دکان ⇒ پیدا نمی‌شود (جدول‌ها جدا هستند)
  const r = await h.post(`/api/admin/pump/subscriptions/${sub.body.subscription.id}/addons`,
    { feature: 'cloudbackup' }, { token: t });
  assert.equal(r.status, 404, '⛔ دو بخش، دو دفتر');
});

/* ══════════════════════════════════════════════════════════════════
   ۴) کمپین
   ══════════════════════════════════════════════════════════════════ */

test('کمپین: فیلتر ⇒ کد ⇒ اعلان، در یک تماس — و آمارش', async () => {
  const t = await adminToken();
  const a = await shopOwner('کمپینی الف');
  const b = await shopOwner('کمپینی ب');
  await h.post('/api/admin/subscriptions', { shopId: a.shopId, plan: 'm1', days: 5 }, { token: t });
  await h.post('/api/admin/subscriptions', { shopId: b.shopId, plan: 'm1', days: 300 }, { token: t });

  const made = await h.post('/api/admin/campaigns', {
    name: 'عیدِ امسال', app: 'shop',
    filter: { kind: 'filter', expiring_days: 30 },
    discount: { kind: 'percent', value: 20, code: 'EIDCAMP' },
    notice: { title: 'تخفیفِ عید', body: 'کدِ شما: {کد-تخفیف}', channels: ['inapp'] },
  }, { token: t });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  assert.equal(made.body.codes.length, 1);
  assert.equal(made.body.codes[0].code, 'EIDCAMP');
  assert.ok(made.body.sent.recipients >= 1);

  //  گیرندهٔ رو به پایان کد را در متنِ اعلانِ خودش می‌بیند
  const inbox = await h.get('/api/me/notices', { token: a.accessToken });
  const one_ = inbox.body.notices.find(n => n.title === 'تخفیفِ عید');
  assert.ok(one_, 'اعلانِ کمپین باید رسیده باشد');
  assert.ok(one_.body.includes('EIDCAMP'), 'متغیرِ {کد-تخفیف} با کدِ واقعی پر می‌شود');

  const far = await h.get('/api/me/notices', { token: b.accessToken });
  assert.ok(!far.body.notices.some(n => n.title === 'تخفیفِ عید'),
    'کسی که رو به پایان نیست در فیلتر نبود');

  const stats = await h.get(`/api/admin/campaigns/${made.body.campaign.id}/stats`, { token: t });
  assert.equal(stats.status, 200);
  assert.ok(stats.body.recipients >= 1);
  assert.ok(stats.body.sent >= 1);
  assert.equal(stats.body.codeUses, 0, 'هنوز کسی خرید نکرده');

  //  حالا با همان کد تمدید ⇒ آمار عوض می‌شود
  await h.post('/api/admin/subscriptions',
    { shopId: a.shopId, plan: 'm1', days: 30, discountCode: 'EIDCAMP' }, { token: t });
  const stats2 = await h.get(`/api/admin/campaigns/${made.body.campaign.id}/stats`, { token: t });
  assert.equal(stats2.body.codeUses, 1);
  assert.ok(stats2.body.renewed >= 1);
});

test('کمپینِ «both» دو کد می‌سازد — چون قیمتِ دو بخش یکی نیست', async () => {
  const t = await adminToken();
  await shopOwner('دوبخشی الف');
  await pumpOwner('دوبخشی ب', 'PMPD1');

  const made = await h.post('/api/admin/campaigns', {
    name: 'هر دو بخش', app: 'both',
    filter: { kind: 'all' },
    discount: { kind: 'percent', value: 10, code: 'BOTH' },
    notice: { title: 'خبر', body: 'کد: {کد-تخفیف}', channels: ['inapp'] },
  }, { token: t });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  assert.equal(made.body.codes.length, 2);
  const byApp = Object.fromEntries(made.body.codes.map(c => [c.app, c]));
  assert.equal(byApp.shop.code, 'BOTH-SHOP');
  assert.equal(byApp.pump.code, 'BOTH-PUMP');
  assert.equal(byApp.shop.currency, 'AFN');
  assert.equal(byApp.pump.currency, 'USD');
});

test('کمپینِ بی نام ساخته نمی‌شود', async () => {
  const t = await adminToken();
  const r = await h.post('/api/admin/campaigns',
    { app: 'shop', discount: { kind: 'percent', value: 10 } }, { token: t });
  assert.equal(r.status, 400);
});

test('مسیرهای تخفیف بی توکنِ مدیر بسته‌اند', async () => {
  for (const [m, p] of [['GET', '/api/admin/discount-codes'], ['POST', '/api/admin/discount-codes'],
    ['GET', '/api/admin/campaigns'], ['GET', '/api/admin/price-history']]) {
    const r = await h.api(m, p, { body: m === 'POST' ? { app: 'shop' } : null });
    assert.ok(r.status === 401 || r.status === 403, `${p} باید بسته باشد (${r.status})`);
  }
});
