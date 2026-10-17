'use strict';
/**
 * F0-03 — «سری سند» هر دستگاه: شمارهٔ فاکتور میان گوشی و کامپیوتر تکراری نشود.
 *
 * مشکل: در Sync v1 `nextInvoiceNo` هماهنگ نمی‌شود (و نباید بشود)؛ وب بازهٔ
 * جدا دارد (۱۰۰۰۰۰ × شمارهٔ عضو) ولی برنامهٔ اندروید و کامپیوتر هر دو از ۱
 * می‌شمارند ⇒ دو فاکتورِ «#۵» در یک دکان.
 *
 * راهِ فاز ۰ (طرحِ کامل در ۳.۵ِ مشخصات، فاز ۱): هر دستگاه هنگامِ پیوستن یک
 * سریِ کوتاه از سرور می‌گیرد (A1، B1، …) و شمارهٔ نمایشی `{سری}-{شماره}` می‌شود.
 * سرور هرگز شماره را عوض نمی‌کند؛ فقط سری می‌دهد — یکتا در هر دکان، ثابت برای
 * هر دستگاه. `W` برای سفارشِ اینترنتی کنار گذاشته شده (۳.۵).
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');

test.before(async () => { await h.start(); });
test.after(async () => { await h.stop(); });

async function ownerWithShop(label) {
  const u = await h.newUser(`صاحب-${label}`, 'shop');
  const s = await h.api('POST', '/api/shop', { token: u.accessToken, body: { name: `دکان ${label}` } });
  assert.ok(s.status < 300, JSON.stringify(s.body));
  return u;
}

const ask = (token, deviceId) => h.api('POST', '/api/shop/series', { token, body: { deviceId } });

test('دو دستگاهِ یک دکان دو سریِ جدا و کوتاه می‌گیرند؛ همان دستگاه همیشه همان سری را', async () => {
  const u = await ownerWithShop('سری-۱');
  const phone = await ask(u.accessToken, 'android-phone-1');
  const desk = await ask(u.accessToken, 'desktop-pc-1');
  assert.equal(phone.status, 200, JSON.stringify(phone.body));
  assert.equal(desk.status, 200, JSON.stringify(desk.body));
  assert.match(phone.body.series, /^[A-Z][1-9][0-9]*$/);
  assert.match(desk.body.series, /^[A-Z][1-9][0-9]*$/);
  assert.notEqual(phone.body.series, desk.body.series, 'دو دستگاه یک سری نمی‌گیرند');
  assert.ok(phone.body.series.length <= 3);

  const again = await ask(u.accessToken, 'android-phone-1');
  assert.equal(again.body.series, phone.body.series, 'سری ثابت است — شماره‌های گذشته معنایشان را نگه می‌دارند');
  assert.equal(again.body.created, false);
});

test('سری مالِ هر دکان است: دکانِ دیگر از اول می‌شمارد و سریِ این دکان را نمی‌بیند', async () => {
  const a = await ownerWithShop('سری-الف');
  const b = await ownerWithShop('سری-ب');
  const a1 = await ask(a.accessToken, 'same-device-id');
  const b1 = await ask(b.accessToken, 'same-device-id');
  assert.equal(a1.body.series, 'A1');
  assert.equal(b1.body.series, 'A1', 'هر دکان از A1 شروع می‌کند');
});

test('چند دستگاه هم‌زمان ⇒ همه سریِ جدا؛ بی I و O و W', async () => {
  const u = await ownerWithShop('سری-هم‌زمان');
  const ids = Array.from({ length: 30 }, (_, i) => `dev-${i}`);
  const got = await Promise.all(ids.map(id => ask(u.accessToken, id)));
  for (const r of got) assert.equal(r.status, 200, JSON.stringify(r.body));
  const series = got.map(r => r.body.series);
  assert.equal(new Set(series).size, ids.length, `تکراری: ${series.join(',')}`);
  assert.ok(series.every(s => !/^[IOW]/.test(s)), 'I و O با ۱ و ۰ اشتباه می‌شوند؛ W مالِ سفارشِ اینترنتی است');
  //  بیست‌وسه حرف ⇒ دستگاهِ بیست‌وچهارم دورِ دوم را شروع می‌کند
  assert.ok(series.includes('A2'), series.join(','));
});

test('بی دکان ⇒ رد؛ بی شناسهٔ دستگاه ⇒ ۴۰۰', async () => {
  const lone = await h.newUser('بی‌دکان', 'shop');
  const r = await ask(lone.accessToken, 'x-1');
  assert.ok(r.status >= 400 && r.status < 500, `انتظارِ ۴xx، آمد ${r.status}`);
  const u = await ownerWithShop('سری-بد');
  const bad = await ask(u.accessToken, '');
  assert.equal(bad.status, 400);
});
