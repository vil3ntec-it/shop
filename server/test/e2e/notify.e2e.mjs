/**
 * سناریوی سرتاسریِ اعلانِ سفارش وقتی سرور قطع یا بازیابی می‌شود — NOTIF-07 و NOTIF-08
 * (§۳۰.۳ بند ۴: «اگر بک‌اند در دسترس نیست، سیستم نباید به مشتری بگوید سفارش با موفقیت ثبت
 * شده … بعد از بازیابی، outbox باید اعلان‌های معوق را بدون ساخت سفارش تکراری پردازش کند»).
 *
 * بازیگرها:
 *   • A — گوشیِ صاحبِ دکان (سطحِ پروتکل): کالا از راهِ همگام‌سازی؛ یک توکنِ پوش ثبت کرده؛
 *   • G — مرورگرِ مشتری (کرومیوم، عرضِ گوشی)؛
 *   • R — سرورِ تازه‌ای که از پشتیبانِ رمزدارِ همین سرور بالا می‌آید (فرایندِ جدا، `lib/restored-notify.mjs`).
 * پرچم‌ها فقط از راهِ live_config: shop.orders، shop.storefront و shop.order_notify (فقط همین دکان).
 * کارگرِ اعلان در این فرایند روشن نیست (همان «سرور پیش از فرستادن افتاد»)؛ پوش به هیچ سرویسی نمی‌رود.
 *
 * اجرا:  node test/e2e/notify.e2e.mjs
 */
import { createRequire } from 'node:module';
import { spawn } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';
import { boot, assert } from './lib/harness.mjs';

const require = createRequire(import.meta.url);
const PASS = 'e2e-backup-passphrase-notify';
const E = await boot({ name: 'notify', env: { BACKUP_PASSPHRASE: PASS } });
const { db } = E;
const push = require('../../src/lib/push');
const sent = [];
push.setDeliver((row, message) => { sent.push(message); });

const owner = await E.owner('owner-notify@e2e.local', 'صاحبِ اعلان', 'دکانِ اعلان');
for (const k of ['shop.orders', 'shop.storefront', 'shop.order_notify']) await E.flag(owner.shopId, k, true);

const SLUG = 'e2e-notify-store';
const X = '0793334455';
const NET = 'اتصال برقرار نشد';
const CUSTOMER_NAME = 'مشتریِ اعلان';

const orders = () => db.many('SELECT id, order_no, status FROM online_orders WHERE shop_id=$1 ORDER BY order_no', [owner.shopId]);
const outbox = () => db.many(
  'SELECT order_id, event, status, attempts FROM order_notify_outbox WHERE shop_id=$1 ORDER BY created_at', [owner.shopId]);

let A; let rice; let G; let lost = null; let placed = null; let file = ''; let report = null;

/* ───────────── کمک‌ها: همان کارهایی که مشتری با انگشت می‌کند ───────────── */

async function openStore(page, hash = '') {
  await page.goto(`${E.apiBase}/store/${SLUG}${hash}`);
  await page.waitForSelector('#products li[data-id]', { state: 'attached', timeout: 15_000 });
}
/** بعد از «ثبت»: یا پیامِ کادرِ ثبت عوض می‌شود یا رسید باز می‌شود — هر کدام زودتر */
async function afterSubmit(page) {
  await page.waitForFunction(() => {
    const m = document.getElementById('checkout-msg');
    const sending = m && !m.hidden && m.classList.contains('info');
    return !document.getElementById('view-order').hidden || (m && !m.hidden && !sending);
  }, null, { timeout: 15_000 });
}
/** «ثبت شد» گفته نشد: صفحهٔ رسید بسته، هنوز روی ثبت، پیامِ خطا پیدا و «ثبت» برای تلاشِ دوباره باز */
async function notAnnounced(page, why) {
  assert.equal(await page.isHidden('#view-order'), true, `${why}: رسید باز شد`);
  assert.equal(await page.isVisible('#view-checkout'), true, `${why}: از صفحهٔ ثبت بیرون رفت`);
  assert.match(new URL(page.url()).hash, /^#\/checkout/, `${why}: نشانیِ رسید`);
  assert.equal(await page.getAttribute('#checkout-msg', 'class').then((c) => /\berr\b/.test(c || '')), true, `${why}: پیامِ خطا نیست`);
  assert.equal(await page.isEnabled('#submit-order'), true, `${why}: «ثبت» برای تلاشِ دوباره بسته ماند`);
}

await E.step('NOTIF.0', 'دکان از برنامه: کالا با همگام‌سازی، ویترینِ روشن و یک توکنِ پوش برای صاحب', async () => {
  A = await E.device('A-owner-phone', owner.email);
  assert.equal((await A.firstSync()).ok, true);
  rice = A.stockIn({ name: 'برنج', price: 120, cost: 90, units: 20 });
  await A.drain();
  E.ok(await A.api('PUT', '/shop/storefront', {
    slug: SLUG, enabled: true, title: 'ویترینِ اعلان', contactPhone: '0700000007', deliveryFeeMinor: 0, minOrderMinor: 0,
  }), 'ویترین');
  E.ok(await A.api('PUT', `/shop/storefront/products/${rice}`, { published: true }), 'انتشارِ برنج');
  E.ok(await E.post('/api/me/push', { token: 'notify-e2e-owner-token' }, owner.token), 'توکنِ پوشِ صاحب', 201);
});

await E.step('NOTIF-07.ready', 'مشتری سبد می‌چیند و شماره‌اش را با کد تأیید می‌کند', async () => {
  G = await E.page('customer');
  const { page } = G;
  await openStore(page);
  await page.locator(`#products li[data-id="${rice}"]`).getByRole('button', { name: /افزودن/ }).click();
  await page.goto(`${E.apiBase}/store/${SLUG}#/checkout`);
  await page.waitForSelector('#view-checkout:not([hidden])');
  await page.fill('#c-name', CUSTOMER_NAME);
  await page.fill('#c-phone', X);
  await page.click('#verify-send');
  await page.waitForSelector('#verify-code-row:not([hidden])', { timeout: 10_000 });
  await page.waitForFunction(() => /\d{4,8}/.test(document.getElementById('verify-msg')?.textContent || ''), null, { timeout: 10_000 });
  await page.fill('#v-code', (await page.textContent('#verify-msg')).match(/(\d{4,8})/)[1]);
  await page.click('#verify-check');
  await page.waitForFunction(() => !document.getElementById('submit-order').disabled, null, { timeout: 10_000 });
  await page.check('input[name="fulfillment"][value="pickup"]');
}, { needs: ['NOTIF.0'] });

await E.step('NOTIF-07.down', 'سرور در دسترس نیست (اتصال قطع) ⇒ «ثبت شد» گفته نمی‌شود؛ نه سفارش، نه ردیفِ اعلان', async () => {
  const { page } = G;
  await page.route('**/checkout', (r) => r.abort('connectionrefused'));
  await page.click('#submit-order');
  await afterSubmit(page);
  await page.unroute('**/checkout');
  assert.match(await page.textContent('#checkout-msg'), new RegExp(NET));
  await notAnnounced(page, 'اتصالِ قطع');
  assert.deepEqual([(await orders()).length, (await outbox()).length, sent.length], [0, 0, 0]);
}, { needs: ['NOTIF-07.ready'] });

await E.step('NOTIF-07.5xx', 'سرور خطای ۵۰۳ می‌دهد ⇒ همان: «ثبت شد» نیست، چیزی ساخته نشد', async () => {
  const { page } = G;
  await page.route('**/checkout', (r) => r.fulfill({
    status: 503, contentType: 'application/json',
    body: JSON.stringify({ error: { code: 'service_unavailable', message: 'سرور موقتاً در دسترس نیست' } }),
  }));
  await page.click('#submit-order');
  await afterSubmit(page);
  await page.unroute('**/checkout');
  assert.match(await page.textContent('#checkout-msg'), /در دسترس نیست/);
  await notAnnounced(page, '۵۰۳');
  assert.deepEqual([(await orders()).length, (await outbox()).length, sent.length], [0, 0, 0]);
}, { needs: ['NOTIF-07.down'] });

await E.step('NOTIF-07.lost', 'سرور ثبت کرد ولی پاسخ به مرورگر نرسید ⇒ «ثبت شد» گفته نمی‌شود؛ زدنِ دوباره همان سفارش را نشان می‌دهد، نه سفارش یا اعلانِ دوم', async () => {
  const { page } = G;
  await page.route('**/checkout', async (r) => {
    const resp = await r.fetch();
    lost = { status: resp.status(), body: await resp.json(), headers: r.request().headers(), post: r.request().postData() };
    await r.abort('connectionreset');
  });
  await page.click('#submit-order');
  await afterSubmit(page);
  await page.unroute('**/checkout');
  assert.equal(lost && lost.status, 201, JSON.stringify(lost && lost.body));
  assert.match(await page.textContent('#checkout-msg'), new RegExp(NET));
  await notAnnounced(page, 'پاسخِ گم‌شده');
  const o1 = await orders();
  const q1 = await outbox();
  assert.equal(o1.length, 1, 'سرور سفارش را ثبت نکرد');
  assert.deepEqual(q1.map((x) => [x.order_id, x.event, x.status]), [[o1[0].id, 'order_created', 'pending']], 'ردیفِ اعلان در همان تراکنش');
  //  برگشتِ شبکه: همان دکمه، همان بدنه ⇒ همان کلیدِ یکتایی ⇒ همان سفارش
  await page.click('#submit-order');
  await page.waitForSelector('#view-order:not([hidden])', { timeout: 15_000 });
  await page.waitForFunction(() => /شمارهٔ/.test(document.getElementById('o-title').textContent), null, { timeout: 10_000 });
  assert.match(await page.textContent('#o-title'), new RegExp(Number(lost.body.order.orderNo).toLocaleString('fa-AF')));
  const o2 = await orders();
  assert.deepEqual(o2.map((o) => o.id), [o1[0].id], 'سفارشِ دوم ساخته شد');
  assert.equal((await outbox()).length, 1, 'ردیفِ اعلانِ دوم ساخته شد');
  assert.equal(sent.length, 0, 'کارگرِ این فرایند خاموش است؛ پیامی نباید رفته باشد');
  placed = { id: o1[0].id, orderNo: Number(o1[0].order_no) };
  await E.shot(G, 'NOTIF-07-after-retry');
}, { needs: ['NOTIF-07.5xx'] });

await E.step('NOTIF-08.backup', 'پشتیبانِ رمزدارِ همین لحظه: سفارش ثبت‌شده و اعلانش هنوز در صف (سرور پیش از فرستادن افتاد)', async () => {
  const backup = require('../../src/lib/backup');
  const out = await backup.run({ kind: 'manual' });
  file = out.file;
  assert.equal(out.encrypted, true);
  assert.equal(fs.readFileSync(file).includes(Buffer.from(CUSTOMER_NAME)), false, 'نامِ مشتری در فایلِ رمزدار خوانا است');
}, { needs: ['NOTIF-07.lost'] });

await E.step('NOTIF-08.restore', 'سرورِ بازیابی‌شده اعلانِ معوق را یک بار می‌فرستد؛ زدنِ دوبارهٔ مشتری همان سفارش است، نه سفارش یا اعلانِ دوم', async () => {
  const backup = require('../../src/lib/backup');
  const tgz = path.join(E.tmp, 'restore.tar.gz');
  fs.writeFileSync(tgz, zlib.gzipSync(await backup.decode(file)));
  const dir = path.join(E.tmp, 'restored-db');
  const pick = (h) => Object.fromEntries(Object.entries(h)
    .filter(([k]) => ['content-type', 'accept', 'accept-language', 'idempotency-key', 'x-store-ticket'].includes(k)));
  const input = path.join(E.tmp, 'restore-input.json');
  fs.writeFileSync(input, JSON.stringify({
    shopId: owner.shopId, path: `/api/store/${SLUG}/checkout`, headers: pick(lost.headers), body: lost.post,
  }));
  assert.ok(pick(lost.headers)['idempotency-key'], 'مرورگر کلیدِ یکتایی نفرستاد');
  const child = spawn(process.execPath, [path.join(import.meta.dirname, 'lib', 'restored-notify.mjs'), dir, tgz, input], {
    env: { ...process.env, DATABASE_URL: `pglite:${dir}` }, stdio: ['ignore', 'pipe', 'pipe'],
  });
  report = await new Promise((resolve, reject) => {
    let out = '';
    const t = setTimeout(() => { child.kill(); reject(new Error(`سرورِ بازیابی‌شده گزارش نداد: ${out.slice(-400)}`)); }, 120_000);
    child.stdout.on('data', (d) => { out += d; });
    child.stderr.on('data', (d) => { out += d; });
    child.on('exit', (c) => {
      clearTimeout(t);
      const m = /^REPORT (.+)$/m.exec(out);
      if (c === 0 && m) resolve(JSON.parse(m[1])); else reject(new Error(`سرورِ بازیابی‌شده (${c}): ${out.slice(-600)}`));
    });
  });
  //  لحظهٔ بازیابی: همان یک سفارش و همان یک اعلانِ در انتظار
  assert.deepEqual(report.before.orders.map((o) => o.orderNo), [placed.orderNo]);
  assert.deepEqual(report.before.outbox.map((x) => [x.orderId, x.status]), [[placed.id, 'pending']]);
  //  کارگر: دورِ اول همان یک ردیف، دورِ دوم هیچ
  assert.deepEqual(report.ticks.slice(0, 2), [1, 0]);
  const forOrder = report.sends.filter((m) => m.data && m.data.orderId === placed.id);
  assert.equal(forOrder.length, 1, `پیام‌ها: ${JSON.stringify(report.sends)}`);
  assert.equal(report.sends.length, 1);
  assert.equal(forOrder[0].data.orderNo, String(placed.orderNo));
  //  §۳۰.۱ بند ۴: بی دادهٔ مشتری در پیام
  const raw = JSON.stringify(report.sends);
  for (const secret of [CUSTOMER_NAME, X, '793334455']) assert.equal(raw.includes(secret), false, `پیام «${secret}» را لو داد`);
  //  مشتری پس از برگشتِ سرور همان «ثبت» را دوباره می‌زند ⇒ همان سفارش
  assert.ok([200, 201].includes(report.retry.status), JSON.stringify(report.retry));
  assert.equal(report.retry.body.order.orderNo, placed.orderNo);
  assert.equal(report.retry.body.order.token, lost.body.order.token);
  //  پایان: یک سفارش، یک اعلان که به provider رسید، هیچ پیامِ دیگری
  assert.deepEqual(report.after.orders.map((o) => o.orderNo), [placed.orderNo]);
  assert.deepEqual(report.after.outbox.map((x) => [x.orderId, x.status, x.attempts]), [[placed.id, 'provider_accepted', 1]]);
  assert.equal(report.ticks[2], 0, 'زدنِ دوباره اعلانِ تازه‌ای در صف گذاشت');
}, { needs: ['NOTIF-08.backup'] });

await E.step('NOTIF.errors', 'بی خطای جاوااسکریپت در ویترین', async () => { assert.deepEqual(E.pageErrors, []); });

await E.finish();
