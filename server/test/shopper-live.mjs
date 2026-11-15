/**
 * برنامهٔ خریدِ مشتری (O-04 «ب»، مرحلهٔ ۱) — کارخواهِ Kotlin ِ `core/.../shopper` روی سرورِ واقعی.
 *
 *     TEST_DATABASE_URL=pglite:memory node test/shopper-live.mjs
 *
 * سرورِ واقعی بالا می‌آید و یک دکانِ ساختگی از همان راه‌های واقعی کاشته می‌شود (ثبت‌نام، همگام‌سازیِ کالا،
 * انتشار، ناحیهٔ ارسال، فهرستِ بازارچه)؛ سپس `ShopperLiveTest` (core/src/jvmTest) با Gradle و
 * `SHOPPER_LIVE_BASE`/`SHOPPER_LIVE_SLUG` همان کارخواهی را می‌زند که برنامهٔ اندروید می‌زند: بازارچه، کاتالوگ،
 * جست‌وجو، کالا، تأییدِ شماره، ثبت با کلیدِ یکتایی (و تکرارش)، پیگیری، لغو، ۴۰۹ ِ قیمتِ عوض‌شده و دریافت از دکان.
 *
 * فاز ۲ (برنامهٔ خرید، مرحلهٔ ۲): پس از سبز شدنِ همان آزمون، پرچم‌های گفت‌وگو، نظر، امتیازِ پیک و تخفیفِ ویترین روشن،
 * کدِ `VIP10` ساخته و یک سفارشِ «دریافت از دکان» از راهِ واقعی تا «تحویل شد» برده می‌شود؛ سپس `ShopperStage2LiveTest`
 * (`SHOPPER_LIVE_DELIVERED`): تخفیفِ سرور در ثبت، پیگیری با `review`/`chat`، نظر و امتیاز، «خریدِ دوباره»، گفت‌وگو با
 * clientId و عکس، و نشانی‌های حسابِ مشتری (سقفِ ۱۰، بسته به همان مشتری). فاز ۱ دست نخورده و پیش از این پرچم‌هاست.
 *
 * Gradle در فرآیندِ جدا و **ناهم‌گام** (spawn) اجرا می‌شود تا همین فرآیند پاسخ بدهد. `SHOPPER_GRADLE_ARGS`
 * پارامترهای افزوده (مثلاً `--offline --init-script …/offline.init.gradle` در کانتینرِ بی‌اینترنت).
 */
import { createRequire } from 'node:module';
import { spawn } from 'node:child_process';
import path from 'node:path';
const mailOf = (p) => `c${String(p).replace(/\D/g, '').slice(-9)}@test.local`;   //  «فقط ایمیل»: بلیتِ مشتری با ایمیل (شمارهٔ تماس جدا)

const require = createRequire(import.meta.url);
process.env.NODE_ENV = 'test';
process.env.DATABASE_URL = process.env.TEST_DATABASE_URL || 'postgres://shop:shoppass@127.0.0.1:5432/shop_test';
process.env.API_SECRET = 'test-secret-test-secret-test-sec';
process.env.OTP_SECRET = 'test-otp-secret-test-otp-secret1';
process.env.BACKUP_ENABLED = 'false';
process.env.RATE_GENERAL_MAX = '100000';
//  فاز ۲ نوشتنِ ویترین (ثبت، حساب، ۱۲ نشانی) را از یک IP زیاد می‌زند؛ سقف‌ها این‌جا آزموده نمی‌شوند
process.env.RATE_STORE_CHECKOUT_MAX = '100000';
process.env.RATE_VERIFY_MAX = '100000';
process.env.OTP_RESEND_SECONDS = '0';
process.env.OTP_UNIFORM_MS = '0';

const { createApp } = require('../src/app');
const { query, newId, now, closeDb } = require('../src/db');
const migrate = require('../src/migrate');
const pw = require('../src/lib/password');

let bad = 0;
const ok = (c, m, d = '') => { if (!c) bad++; console.log((c ? '✅ ' : '❌ ') + m + (d ? ` — ${d}` : '')); };

await query('DROP SCHEMA public CASCADE');
await query('CREATE SCHEMA public');
await migrate.run();
await query(`INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
             VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`, [newId('adm'), await pw.hashPassword('Admin!12345'), now()]);
const app = await createApp({ runMigrations: false });
const server = await new Promise((r) => { const s = app.listen(0, '127.0.0.1', () => r(s)); });
const base = `http://127.0.0.1:${server.address().port}`;

const call = async (method, p, b, t, headers = {}) => {
  const r = await fetch(base + p, { method, headers: { 'Content-Type': 'application/json', ...(t ? { Authorization: `Bearer ${t}` } : {}), ...headers },
    body: b === undefined || b === null ? undefined : JSON.stringify(b) });
  return { status: r.status, body: await r.json().catch(() => null) };
};

//  ── دکانِ آزمایشی (دادهٔ ساختگی، نه دکانِ واقعی) ──
const email = 'owner@shopper.local', password = 'Passw0rd!test';
const s1 = await call('POST', '/api/auth/register/start', { name: 'صاحب', email, password });
const v1 = await call('POST', '/api/auth/register/verify', { email, code: s1.body.devCode });
const d1 = await call('POST', '/api/auth/register/complete', { ticket: v1.body.ticket, name: 'صاحب', password,
  device: { deviceId: 'dev-owner', name: 'تست', platform: 'test' }, app: 'shop', terms: { accepted: true } });
const T = d1.body.accessToken;
const shopId = (await call('POST', '/api/shop', { name: 'دکانِ آزمایشی' }, T)).body.shop.id;
const A = (await call('POST', '/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
const flag = (scope, key) => call('PUT', '/api/admin/pump/live-config', { scope, key, value: true }, A);
for (const key of ['shop.orders', 'shop.storefront', 'shop.storefront.zones']) await flag(shopId, key);
await flag('all', 'market.directory');
const slug = 'shopper-live';
const put = await call('PUT', '/api/shop/storefront', {
  slug, enabled: true, title: 'ویترینِ آزمایشی', tagline: 'کالای روزانه', contactPhone: '0700000001',
  deliveryFeeMinor: 5000, minOrderMinor: 0,
}, T);
ok(put.status === 200, 'ویترین ساخته و روشن شد', JSON.stringify(put.body).slice(0, 120));
const listed = await call('PUT', '/api/shop/market', { listed: true, category: 'grocery', area: 'کارتهٔ سه' }, T);
ok(listed.status === 200, 'دکان در بازارچه فهرست شد', JSON.stringify(listed.body).slice(0, 120));
const op = (row, fields) => ({ op_id: `op-${row}-${Date.now()}`, ts: Date.now(), table: 'products', row_id: row, type: 'insert', fields });
const pushed = await call('POST', '/api/sync/v1/push', { device_id: 'dev-owner', schema_version: 2, ops: [
  op('rice', { name: 'برنج', unit: 'کیلو', salePrice: 120.5, category: 'خوراکی' }),
  op('oil', { name: 'روغن', unit: 'بوتل', salePrice: 300, category: 'خوراکی' }),
  op('soap', { name: 'صابون', unit: 'دانه', salePrice: 40, category: 'بهداشتی' }),
] }, T, { 'X-App': 'shop', 'X-Device': 'dev-owner' });
ok(pushed.status === 200, 'کالاها از راهِ همگام‌سازیِ واقعی رسیدند');
for (const id of ['rice', 'oil']) await call('PUT', `/api/shop/storefront/products/${id}`, { published: true }, T);
const zones = await call('PUT', '/api/shop/storefront/delivery', { zones: [{
  name: 'کارته‌سه', kind: 'polygon', polygon: [[34.50, 69.12], [34.50, 69.22], [34.56, 69.22], [34.56, 69.12]],
  feeMinor: 3000, minOrderMinor: 60000, freeOverMinor: 100000, smallBasketFeeMinor: 1500,
}] }, T);
ok(zones.status === 200, 'ناحیهٔ ارسال ذخیره شد', JSON.stringify(zones.body).slice(0, 120));

//  ── کارخواهِ Kotlin ِ برنامه روی همین سرور ──
const repo = path.join(import.meta.dirname, '..', '..');
const extra = (process.env.SHOPPER_GRADLE_ARGS || '').split(/\s+/).filter(Boolean);
const args = ['-p', 'core', '--no-daemon', ...extra, 'jvmTest', '--rerun', '--tests', 'ir.vil3ntec.tohid.core.shopper.ShopperLiveTest'];
console.log(`gradle ${args.join(' ')}`);
const gradle = (testClass, env) => new Promise((resolve) => {
  const child = spawn(process.env.GRADLE || 'gradle', [...args.slice(0, -1), testClass], {
    cwd: repo, stdio: 'inherit',
    env: { ...process.env, SHOPPER_LIVE_BASE: base, SHOPPER_LIVE_SLUG: slug, ...env },
  });
  child.on('error', (e) => { console.error(e.message); resolve(127); });
  child.on('exit', (c) => resolve(c ?? 1));
});
const code = await gradle('ir.vil3ntec.tohid.core.shopper.ShopperLiveTest', {});
ok(code === 0, 'ShopperLiveTest (کارخواهِ برنامهٔ خرید) روی سرورِ واقعی سبز', `gradle ⇒ ${code}`);

//  ShopperLiveTest رد شده (skip) سبز نیست: نتیجهٔ XML ِ همان اجرا باید یک آزمونِ بی skip و بی شکست بگوید
const fs = await import('node:fs');
const junit = (name) => {
  const xml = path.join(repo, 'core', 'build', 'test-results', 'jvmTest', `TEST-ir.vil3ntec.tohid.core.shopper.${name}.xml`);
  const report = fs.existsSync(xml) ? fs.readFileSync(xml, 'utf8') : '';
  ok(/tests="1"/.test(report) && /skipped="0"/.test(report) && /failures="0"/.test(report) && /errors="0"/.test(report),
    `گزارشِ JUnit ِ ${name}: یک آزمون، بی skip و بی شکست`, report.slice(0, 200).replace(/\s+/g, ' '));
};
junit('ShopperLiveTest');

//  ── فاز ۲: گفت‌وگو، نظر، امتیاز، تخفیف و سفارشِ تحویل‌شده (پس از فاز ۱، تا آن را عوض نکند) ──
for (const key of ['shop.chat', 'shop.orders.reviews', 'shop.delivery_ratings', 'shop.storefront.promos', 'shop.promo_uses']) await flag(shopId, key);
const rule = await call('PUT', '/api/shop/promos/rules/vip', { rule: {
  name: 'vip', stack: 'best', active: true, kind: 'percent', bp: 1000, channels: ['online'], code: 'VIP10',
} }, T);
ok(rule.status === 200, 'کدِ تخفیفِ VIP10 ساخته شد', JSON.stringify(rule.body).slice(0, 120));
const dPhone = '0700999888';
const dStart = await call('POST', `/api/store/${slug}/verify/start`, { email: mailOf(dPhone) });
const dCheck = await call('POST', `/api/store/${slug}/verify/check`, { email: mailOf(dPhone), code: dStart.body.devCode });
const dOrder = await call('POST', `/api/store/${slug}/checkout`, {
  fulfillment: 'pickup', customer: { name: 'مشتریِ تحویل', phone: dPhone }, lines: [{ productId: 'rice', qtyMilli: 1000 }], lang: 'fa',
}, null, { 'Idempotency-Key': 'live-delivered-1', 'X-Store-Ticket': dCheck.body.ticket });
ok(dOrder.status === 201, 'سفارشِ «دریافت از دکان» ثبت شد', JSON.stringify(dOrder.body).slice(0, 120));
const dRow = (await query('SELECT id FROM online_orders WHERE shop_id=$1 AND order_no=$2', [shopId, dOrder.body.order.orderNo])).rows[0];
for (const to of ['accepted', 'picking', 'packed', 'ready_for_pickup', 'picked_up']) {
  const tr = await call('POST', `/api/shop/orders/${dRow.id}/transition`, { to }, T);
  ok(tr.status === 200, `گذارِ سفارش به ${to}`, JSON.stringify(tr.body).slice(0, 100));
}
const code2 = await gradle('ir.vil3ntec.tohid.core.shopper.ShopperStage2LiveTest', { SHOPPER_LIVE_DELIVERED: dOrder.body.order.token });
ok(code2 === 0, 'ShopperStage2LiveTest (نشانی، گفت‌وگو، نظر، تخفیف) روی سرورِ واقعی سبز', `gradle ⇒ ${code2}`);
junit('ShopperStage2LiveTest');

server.close();
await closeDb();
console.log(bad ? `\n❌ ${bad} مورد نادرست` : '\n✅ همه درست');
process.exit(bad ? 1 : 0);
