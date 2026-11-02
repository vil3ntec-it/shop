'use strict';
/**
 * WEB-10 — «خبرم کن وقتی موجود شد / ارزان شد» (`lib/storefront-alerts.js`، پرچمِ خاموشِ `shop.storefront.alerts`).
 *
 *   ۱) ⛔ خاموش ⇒ همان پاسخِ مسیرِ ناشناخته، هیچ نوشتن، `features` بی کلید؛ کارگر هشدارِ دکانِ خاموش را نمی‌سوزاند.
 *   ۲) ثبت فقط با شمارهٔ تأییدشده؛ یکتایی (دکان، شماره، کالا، نوع) — تکرار بی‌اثر و قیدِ پایگاه؛ شمارهٔ دیگر و
 *      ویترینِ دیگر جدا (نه می‌بیند نه پاک می‌کند)؛ «موجود» ⇒ ۴۰۹، «نامعلوم» ⇒ ۴۰۹؛ سقفِ هشدار و سقفِ نوشتنِ هر شماره؛ لغو.
 *   ۳) گذار دقیقاً یک بار — حتی با دو دورِ هم‌زمان یا پشتِ سرِ هم؛ پس از آن هشدار مصرف شده (رفت‌وبرگشتِ
 *      موجودی یا ارزان‌ترِ دوباره خبری نمی‌دهد) و فقط ثبتِ دوباره آن را با مبنای تازه مسلح می‌کند.
 *   ۴) سقفِ روزانهٔ هزینهٔ دکان (NTF-02، مشترک با اعلانِ سفارش)، سقفِ خبرِ هر شماره، و `shop.customer_notify` ِ خاموش.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const { h, boot, flag, liveStore, setStock, setPrice, ticketFor } = require('./storefront-finder-fixture');
const { query, one, now } = require('../src/db');
const A = require('../src/lib/storefront-alerts');
const notify = require('../src/lib/customer-notify');

boot();

const calls = [];
const fake = { send: async (m) => { calls.push(m); return { status: 'accepted', ref: `ref-${calls.length}` }; } };
test.afterEach(() => { notify.setProviders({}); calls.length = 0; });

const rows = async (shopId) => (await one('SELECT COUNT(*)::int AS n FROM storefront_alerts WHERE shop_id = $1', [shopId])).n;
const alertOf = (shopId, productId, kind) => one(
  'SELECT * FROM storefront_alerts WHERE shop_id = $1 AND product_id = $2 AND kind = $3', [shopId, productId, kind]
);
const sub = (s, k, id, kind) => h.put(`/api/store/${s.slug}/alerts/${id}/${kind}`, {}, k);

test('WEB-10 خاموش ⇒ همان پاسخِ مسیرِ ناشناخته، بی نوشتن؛ کارگر دکانِ خاموش را نمی‌سوزاند', async () => {
  const s = await liveStore('هشدار خاموش', { catalog: true });
  const k = await ticketFor(s.slug, '0791250001');
  const unknown = await h.put(`/api/store/${s.slug}/no-such-door/tea-a/stock`, {}, k);
  for (const r of [await sub(s, k, 'tea-a', 'stock'), await h.get(`/api/store/${s.slug}/alerts`, k)]) {
    assert.equal(r.status, unknown.status);
    assert.deepEqual(r.body, unknown.body);
  }
  assert.equal(await rows(s.shopId), 0, 'خاموش ⇒ هیچ نوشتنی');
  assert.equal('features' in (await h.get(`/api/store/${s.slug}`)).body, false);

  //  هشدارِ مسلح (پیش از خاموش شدن) + کالای موجود + پرچمِ خاموش ⇒ کارگر نمی‌سوزاند
  await flag(s.shopId, 'shop.storefront.alerts', true);
  assert.equal((await sub(s, k, 'tea-a', 'stock')).status, 201);
  await flag(s.shopId, 'shop.storefront.alerts', false);
  await setStock(s, 'tea-a', 2000);
  await A.tick();
  assert.equal((await alertOf(s.shopId, 'tea-a', 'stock')).state, 'armed');
  await flag(s.shopId, 'shop.storefront.alerts', true);
  await flag(s.shopId, 'shop.customer_notify', true);
  notify.setProviders({ push: fake });
  await A.tick();
  assert.equal((await alertOf(s.shopId, 'tea-a', 'stock')).state, 'fired', 'روشن ⇒ همان دورِ بعد');
  assert.equal(calls.length, 1);
});

test('WEB-10 ثبت: هویت، یکتایی، جدایی، سقف و لغو', async () => {
  const s = await liveStore('هشدار ثبت', { catalog: true });
  await flag(s.shopId, 'shop.storefront.alerts', true);
  assert.deepEqual((await h.get(`/api/store/${s.slug}`)).body.features, { alerts: true });
  const base = `/api/store/${s.slug}/alerts`;
  const anon = await h.put(`${base}/tea-a/stock`, {});
  assert.equal(anon.status, 401);
  assert.equal(anon.body.error.code, 'verification_required');
  assert.equal((await h.api('PUT', `${base}/tea-a/stock`, { headers: { 'X-Store-Ticket': 'forged.ticket' } })).status, 401);

  const k = await ticketFor(s.slug, '0791250002');
  const first = await sub(s, k, 'tea-a', 'stock');
  assert.equal(first.status, 201, JSON.stringify(first.body));
  assert.deepEqual([first.body.created, first.body.alert.state, first.body.alert.kind], [true, 'armed', 'stock']);
  const again = await sub(s, k, 'tea-a', 'stock');
  assert.equal(again.status, 200);
  assert.equal(again.body.created, false, 'تکرار بی‌اثر');
  const price = await sub(s, k, 'tea-a', 'price');
  assert.equal(price.status, 201);
  assert.equal(price.body.alert.basePriceMinor, 12000, 'مبنا = قیمتِ کارت');
  assert.equal(await rows(s.shopId), 2);
  await assert.rejects(query(
    `INSERT INTO storefront_alerts (id, shop_id, phone, product_id, kind, checked_at, created_at, updated_at)
     SELECT 'dup', shop_id, phone, product_id, kind, 1, 1, 1 FROM storefront_alerts WHERE shop_id = $1 AND kind = 'stock'`, [s.shopId]
  ), 'قیدِ یکتاییِ پایگاه');

  for (const [path, status, code] of [['tea-a/soon', 400, 'bad_alert_kind'], ['nothing/stock', 404, 'product_not_found'],
    ['bad%20id/price', 404, 'product_not_found']]) {
    const r = await h.put(`${base}/${path}`, {}, k);
    assert.equal(r.status, status, path);
    assert.equal(r.body.error.code, code, path);
  }
  await setStock(s, 'tea-b', 2000);
  const avail = await sub(s, k, 'tea-b', 'stock');
  assert.equal(avail.status, 409);
  assert.equal(avail.body.error.code, 'in_stock', 'موجود ⇒ همین حالا بخرید');
  const blind = await liveStore('بی کاتالوگ');
  await flag(blind.shopId, 'shop.storefront.alerts', true);
  const kb = await ticketFor(blind.slug, '0791250002');
  const unknown = await sub(blind, kb, 'tea-a', 'stock');
  assert.equal(unknown.status, 409);
  assert.equal(unknown.body.error.code, 'stock_unknown');
  assert.equal((await sub(blind, kb, 'tea-a', 'price')).status, 201, 'ارزان شدن بی کاتالوگ هم معنا دارد');

  //  شمارهٔ دیگر و ویترینِ دیگر: نه می‌بینند نه پاک می‌کنند
  const other = await ticketFor(s.slug, '0791250003');
  assert.deepEqual((await h.get(base, other)).body.items, []);
  assert.deepEqual(await h.del(`${base}/tea-a/stock`, other).then((r) => r.body), { deleted: false });
  assert.equal((await h.get(`/api/store/${blind.slug}/alerts`, kb)).body.items.length, 1, 'هر ویترین جدا');
  const mine = (await h.get(base, k)).body;
  assert.deepEqual(mine.items.map((a) => `${a.productId}:${a.kind}:${a.state}`).sort(), ['tea-a:price:armed', 'tea-a:stock:armed']);
  assert.equal(mine.max, A.MAX_PER_SHOP);

  //  لغو؛ تکرارش بی‌اثر
  assert.deepEqual((await h.del(`${base}/tea-a/price`, k)).body, { deleted: true });
  assert.deepEqual((await h.del(`${base}/tea-a/price`, k)).body, { deleted: false });

  //  سقفِ هشدارِ مسلح در هر ویترین
  const phone = (await alertOf(s.shopId, 'tea-a', 'stock')).phone;
  for (let i = 1; i < A.MAX_PER_SHOP; i++) {
    await query(
      `INSERT INTO storefront_alerts (id, shop_id, phone, product_id, kind, checked_at, created_at, updated_at)
       VALUES ($1,$2,$3,$4,'price',$5,$5,$5)`, [`cap-${i}`, s.shopId, phone, `ghost-${i}`, now()]
    );
  }
  const full = await sub(s, k, 'tea-c', 'price');
  assert.equal(full.status, 409);
  assert.equal(full.body.error.code, 'too_many_alerts');
});

test('WEB-10 سقفِ نوشتنِ هر شماره ⇒ ۴۲۹', async () => {
  const s = await liveStore('هشدار سقف', { catalog: true });
  await flag(s.shopId, 'shop.storefront.alerts', true);
  const k = await ticketFor(s.slug, '0791250004');
  for (let i = 0; i < A.WRITES_PER_HOUR; i++) assert.ok((await sub(s, k, 'tea-c', 'price')).status < 300, `${i}`);
  const r = await sub(s, k, 'tea-c', 'price');
  assert.equal(r.status, 429);
  assert.equal((await h.get(`/api/store/${s.slug}/alerts`, k)).status, 200, 'خواندن سطلِ جدا دارد');
});

test('WEB-10 گذار دقیقاً یک بار؛ مصرف و مسلح‌سازیِ دوباره', async () => {
  const s = await liveStore('هشدار گذار', { catalog: true });
  for (const key of ['shop.storefront.alerts', 'shop.customer_notify']) await flag(s.shopId, key, true);
  notify.setProviders({ push: fake });
  const k = await ticketFor(s.slug, '0791250005');
  assert.equal((await sub(s, k, 'tea-a', 'stock')).status, 201);
  assert.equal((await sub(s, k, 'tea-c', 'price')).status, 201);
  await A.tick();
  assert.equal(calls.length, 0, 'بی گذار ⇒ بی خبر');

  await setStock(s, 'tea-a', 2000);
  //  دو نمونهٔ سرور که همان دستهٔ مسلح را خوانده‌اند، دو دورِ هم‌زمانِ همین نمونه، و یک دورِ بعد ⇒ یک خبر
  const batch = (await query("SELECT * FROM storefront_alerts WHERE shop_id = $1 AND state = 'armed'", [s.shopId])).rows;
  const twins = await Promise.all([A.checkShop(s.shopId, batch, now()), A.checkShop(s.shopId, batch, now())]);
  const both = await Promise.all([A.tick(), A.tick()]);
  await A.tick();
  assert.equal(twins[0] + twins[1], 1);
  assert.equal(both[0].fired + both[1].fired, 0);
  assert.equal(calls.length, 1, 'هر گذار یک خبر');
  assert.deepEqual([calls[0].channel, calls[0].to.phone, calls[0].event], ['push', '+93791250005', 'product_stock_alert']);
  assert.match(calls[0].text, /چای الف/);
  assert.doesNotMatch(calls[0].text, /[0-9۰-۹]{3}/, 'بی قیمت و بی شماره');
  const fired = await alertOf(s.shopId, 'tea-a', 'stock');
  assert.deepEqual([fired.state, fired.notice, fired.channel], ['fired', 'sent', 'push']);

  //  مصرف شد: تمام و دوباره موجود ⇒ هیچ
  await setStock(s, 'tea-a', -2000);
  await A.tick();
  await setStock(s, 'tea-a', 1000);
  await A.tick();
  assert.equal(calls.length, 1, 'رفت‌وبرگشتِ دوباره خبر نمی‌دهد');

  //  ارزان شدن: کمتر از مبنا ⇒ یک خبر؛ ارزان‌ترِ دوباره ⇒ هیچ
  assert.equal((await setPrice(s, 'tea-c', 30000)).status, 200);
  await A.tick();
  assert.equal(calls.length, 1, 'برابرِ مبنا ارزان شدن نیست');
  await setPrice(s, 'tea-c', 25000);
  await A.tick();
  await A.tick();
  assert.equal(calls.length, 2);
  assert.equal(calls[1].event, 'product_price_alert');
  assert.equal(Number((await alertOf(s.shopId, 'tea-c', 'price')).fired_price_minor), 25000);
  await setPrice(s, 'tea-c', 20000);
  await A.tick();
  assert.equal(calls.length, 2, 'هشدارِ سوخته دوباره نمی‌سوزد');

  //  ثبتِ دوباره ⇒ همان ردیف با مبنای تازه
  const re = await sub(s, k, 'tea-c', 'price');
  assert.equal(re.status, 201);
  assert.deepEqual([re.body.alert.state, re.body.alert.basePriceMinor, re.body.alert.notice], ['armed', 20000, null]);
  assert.equal(await rows(s.shopId), 2, 'یکتایی می‌ماند');
  await A.tick();
  assert.equal(calls.length, 2);
  await setPrice(s, 'tea-c', 19000);
  await A.tick();
  assert.equal(calls.length, 3);
});

test('WEB-10 سقفِ هزینهٔ دکان (مشترک با اعلانِ سفارش)، سقفِ هر شماره و اعلانِ خاموش', async () => {
  const s = await liveStore('هشدار هزینه', { catalog: true });
  for (const key of ['shop.storefront.alerts', 'shop.customer_notify']) await flag(s.shopId, key, true);
  notify.setProviders({ push: fake });
  const saved = { c: process.env.CUSTOMER_NOTIFY_COST_MICROS_PUSH, m: process.env.CUSTOMER_NOTIFY_DAILY_CAP_MICROS_PUSH };
  process.env.CUSTOMER_NOTIFY_COST_MICROS_PUSH = '500000';
  process.env.CUSTOMER_NOTIFY_DAILY_CAP_MICROS_PUSH = '600000';
  try {
    const a = await ticketFor(s.slug, '0791250006');
    const b = await ticketFor(s.slug, '0791250007');
    assert.equal((await sub(s, a, 'tea-a', 'stock')).status, 201);
    assert.equal((await sub(s, b, 'tea-b', 'stock')).status, 201);
    await setStock(s, 'tea-a', 1000);
    await A.tick();
    assert.equal(calls.length, 1);
    assert.equal(await notify.spentToday(s.shopId, 'push', now()), 500000, 'اعلانِ سفارش هم این هزینه را می‌بیند');
    await setStock(s, 'tea-b', 1000);
    await A.tick();
    assert.equal(calls.length, 1, 'سقفِ دکان ⇒ سرویس‌دهنده صدا زده نمی‌شود');
    const capped = await alertOf(s.shopId, 'tea-b', 'stock');
    assert.deepEqual([capped.state, capped.notice], ['fired', 'cost_cap']);
    assert.deepEqual(capped.tried.map((t) => `${t.channel}:${t.result}`), ['push:cost_cap', 'whatsapp:not_configured', 'telegram:not_configured']);
  } finally {
    for (const [k, v] of [['CUSTOMER_NOTIFY_COST_MICROS_PUSH', saved.c], ['CUSTOMER_NOTIFY_DAILY_CAP_MICROS_PUSH', saved.m]]) {
      if (v === undefined) delete process.env[k]; else process.env[k] = v;
    }
  }

  //  سقفِ هر شماره: حداکثر `PER_DAY` خبر در ۲۴ ساعت (همهٔ ویترین‌ها)
  const c = await ticketFor(s.slug, '0791250008');
  await setStock(s, 'tea-a', -1000);
  for (const id of ['tea-c', 'oil-a', 'soap-a', 'tea-a']) assert.equal((await sub(s, c, id, 'stock')).status, 201, id);
  for (const id of ['tea-c', 'oil-a', 'soap-a', 'tea-a']) await setStock(s, id, 1000);
  await A.tick();
  const notices = (await query("SELECT notice FROM storefront_alerts WHERE phone = '+93791250008'")).rows.map((r) => r.notice).sort();
  assert.deepEqual(notices, [...Array(A.PER_DAY).fill('sent'), 'rate_limited'].sort());

  //  `shop.customer_notify` خاموش ⇒ هشدار می‌سوزد (در «هشدارهای من» دیده می‌شود) ولی هیچ پیامی نمی‌رود
  const n = calls.length;
  await flag(s.shopId, 'shop.customer_notify', false);
  const d = await ticketFor(s.slug, '0791250009');
  assert.equal((await sub(s, d, 'tea-c', 'price')).status, 201);
  await setPrice(s, 'tea-c', 100);
  await A.tick();
  assert.equal(calls.length, n);
  const off = (await h.get(`/api/store/${s.slug}/alerts`, d)).body.items[0];
  assert.deepEqual([off.state, off.notice], ['fired', 'notify_off']);
});
