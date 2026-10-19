/**
 * سناریوی سرتاسریِ امنیت و نشست — E2E-SEC-13، #246 (دو تازه‌سازیِ پشتِ سرِ هم)، SYN-10 (خروج)
 * و نقشِ شاگرد روی خودِ op.
 *
 *   • دکانِ دو با عوض کردنِ شناسه در نشانی به سفارش، تحویل، کاتالوگ و پشتیبانِ دکانِ یک
 *     نمی‌رسد؛ توکنِ پیگیریِ مشتریِ دکانِ یک روی ویترینِ دکانِ دو کار نمی‌کند.
 *   • شاگرد: نه بازپرداخت، نه تسویه، نه سابقهٔ دکان؛ و opِ «تخفیف» از دستگاهِ شاگرد رد می‌شود.
 *   • نشست: توکنِ دسترسی دو بار پیر می‌شود و برنامه هر دو بار با توکنِ چرخیده ادامه می‌دهد
 *     (#246)؛ توکنِ تازه‌سازیِ کهنه که دوباره بیاید «دزدی» است و کلِ نشست بسته می‌شود.
 *   • خروج پس از همگام‌سازی: سرور هر دو توکن را باطل می‌کند و هیچ داده‌ای گم نمی‌شود
 *     (دستگاهِ نو با Snapshot همه را دارد).
 *
 * اجرا:  node test/e2e/security.e2e.mjs
 */
import { createHash } from 'node:crypto';
import { boot, assert } from './lib/harness.mjs';

const E = await boot({ name: 'security' });
const { db } = E;
const one = await E.owner('owner-sec1@e2e.local', 'صاحبِ یک', 'دکانِ یک');
const staff = await E.staff(one, 'staff-sec1@e2e.local', 'شاگردِ یک');
const two = await E.owner('owner-sec2@e2e.local', 'صاحبِ دو', 'دکانِ دو');
for (const s of [one, two]) for (const k of ['shop.orders', 'shop.storefront', 'shop.delivery', 'shop.catalog', 'shop.orders.refunds']) await E.flag(s.shopId, k, true);
const hash = (t) => createHash('sha256').update(String(t)).digest('hex');

let A; let S; let X; let product; let orderOne; let backupId; let trackToken;

await E.step('E2E-SEC-13.0', 'دکانِ یک: کالا، سفارش، پشتیبان و ویترین؛ دکانِ دو: دستگاهِ خودش', async () => {
  A = await E.device('A-owner-one', one.email);
  S = await E.device('S-staff-one', staff.email);
  X = await E.device('X-owner-two', two.email);
  for (const d of [A, S, X]) assert.equal((await d.firstSync()).ok, true);
  product = A.stockIn({ name: 'چای', price: 50, cost: 30, units: 10 });
  await A.drain();
  const o = await A.api('POST', '/shop/orders', {
    clientKey: E.key('app'), source: 'phone', fulfillment: 'pickup', paymentMethod: 'cod',
    customer: { name: 'مشتریِ دکانِ یک', phone: '0791231234' }, lines: [{ name: 'چای', qtyMilli: 1000, unitPriceMinor: 5000 }],
  }, { headers: { 'Idempotency-Key': E.key('o') } });
  orderOne = E.ok(o, 'سفارشِ دستی', 201).order;
  //  پشتیبانِ رمزشدهٔ برنامه (بایتِ خام، همان `BackupRepository`)
  const up = await fetch(`${E.apiBase}/api/me/backups`, { method: 'POST', headers: { ...A.headers({ 'Content-Type': 'application/octet-stream' }) }, body: Buffer.from('E2E-ENCRYPTED-BACKUP-'.repeat(20)) });
  const bk = await up.json();
  assert.equal(up.status, 201, JSON.stringify(bk));
  backupId = bk.backup.id;
  E.ok(await A.api('PUT', '/shop/storefront', { slug: 'e2e-sec-one', enabled: true, title: 'یک', contactPhone: '0700000005', deliveryFeeMinor: 0, minOrderMinor: 0 }), 'ویترینِ یک');
  E.ok(await X.api('PUT', '/shop/storefront', { slug: 'e2e-sec-two', enabled: true, title: 'دو', contactPhone: '0700000006', deliveryFeeMinor: 0, minOrderMinor: 0 }), 'ویترینِ دو');
  E.ok(await A.api('PUT', `/shop/storefront/products/${product}`, { published: true }), 'انتشار');
  const v = E.ok(await E.post('/api/store/e2e-sec-one/verify/start', { phone: '0791239999' }), 'کد');
  const t = E.ok(await E.post('/api/store/e2e-sec-one/verify/check', { phone: '0791239999', code: v.devCode }), 'تأیید');
  const c = E.ok(await E.post('/api/store/e2e-sec-one/checkout', { fulfillment: 'pickup', paymentMethod: 'cod', customer: { name: 'م', phone: '0791239999' }, lines: [{ productId: product, qtyMilli: 1000 }] }, null, { 'X-Store-Ticket': t.ticket, 'Idempotency-Key': E.key('w') }), 'سفارشِ ویترین', 201);
  trackToken = c.order.token;
});

await E.step('E2E-SEC-13.idswap', 'دکانِ دو با شناسه‌های دکانِ یک در نشانی به هیچ‌چیز نمی‌رسد', async () => {
  const probes = [
    ['GET', `/shop/orders/${orderOne.id}`],
    ['POST', `/shop/orders/${orderOne.id}/transition`, { to: 'accepted', from: 'new' }],
    ['POST', `/shop/orders/${orderOne.id}/refunds`, { amountMinor: 100, reason: 'x' }],
    ['GET', `/shop/delivery/orders/${orderOne.id}`],
    ['GET', `/shop/catalog/products/${product}`],
    ['GET', `/shop/catalog/moves?productId=${product}`],
    ['GET', `/me/backups/${backupId}`],
  ];
  for (const [m, p, b] of probes) {
    const r = await X.api(m, p, b, { headers: { 'Idempotency-Key': E.key('x') } });
    assert.ok([403, 404].includes(r.status) || (r.status === 200 && !JSON.stringify(r.body).includes(orderOne.id) && !JSON.stringify(r.body).includes('چای')),
      `${m} ${p} ⇐ ${r.status} ${JSON.stringify(r.body).slice(0, 160)}`);
  }
  const row = await db.one('SELECT status FROM online_orders WHERE id=$1', [orderOne.id]);
  assert.equal(row.status, 'new', 'دکانِ دو وضعیتِ سفارشِ دکانِ یک را عوض کرد');
  const list = E.ok(await X.api('GET', '/shop/orders?tab=new'), 'فهرستِ دکانِ دو');
  assert.equal(JSON.stringify(list).includes(orderOne.id), false);
  const cross = await E.get(`/api/store/e2e-sec-two/orders/${encodeURIComponent(trackToken)}`);
  assert.equal(cross.status, 404, `توکنِ پیگیریِ دکانِ یک روی ویترینِ دو: ${cross.status}`);
  const own = await E.get(`/api/store/e2e-sec-one/orders/${encodeURIComponent(trackToken)}`);
  assert.equal(own.status, 200);
  const guess = await E.get(`/api/store/e2e-sec-one/orders/${encodeURIComponent(trackToken.slice(0, -2) + 'AA')}`);
  assert.equal(guess.status, 404, 'توکنِ حدسی سفارش را نشان داد');
}, { needs: ['E2E-SEC-13.0'] });

await E.step('E2E-SEC-13.staff', 'شاگرد: شمارهٔ پوشیده؛ نه بازپرداخت، نه سابقهٔ دکان، نه تعریفِ پیک', async () => {
  const o = E.ok(await S.api('GET', `/shop/orders/${orderOne.id}`), 'سفارش برای شاگرد');
  assert.equal(JSON.stringify(o).includes('791231234'), false);
  for (const [m, p, b] of [
    ['POST', `/shop/orders/${orderOne.id}/refunds`, { amountMinor: 100, reason: 'x' }],
    ['GET', '/shop/audit'],
    ['POST', '/shop/delivery/couriers', { userId: staff.user.id }],
  ]) {
    const r = await S.api(m, p, b, { headers: { 'Idempotency-Key': E.key('s') } });
    assert.equal(r.status, 403, `${m} ${p} ⇐ ${r.status}`);
  }
}, { needs: ['E2E-SEC-13.0'] });

let discountSale;
await E.step('E2E-SEC-13.op', 'opِ «تخفیف» از دستگاهِ شاگرد (برنامهٔ دست‌کاری‌شده) رد می‌شود و سرور فاکتورِ تخفیف‌دار نمی‌گیرد', async () => {
  await S.drain();
  const r = S.sell({ lines: [{ id: product, qty: 2 }], discountType: 'amount', discountValue: 20 });
  assert.equal(r.ok, true);
  discountSale = r.saleId;
  await S.sync();
  const why = S.dropped.find((x) => x.table === 'sales' && x.row_id === r.saleId);
  assert.ok(why && /discount_not_allowed/.test(why.why), `opِ تخفیفِ شاگرد رد نشد: ${JSON.stringify(S.dropped)}`);
  const srv = await db.many(`SELECT 1 FROM sync_rows WHERE account_id=$1 AND table_name='sales' AND row_id=$2`, [one.shopId, r.saleId]);
  assert.equal(srv.length, 0, 'فاکتورِ تخفیف‌دارِ شاگرد روی سرور نشست');
}, { needs: ['E2E-SEC-13.0'] });

await E.step('E2E-SEC-13.op-children', 'رد شدنِ سرِ فاکتور، اقلام و حرکت‌های همان فاکتور را هم بیرون نگه می‌دارد (موجودیِ دستگاهِ دیگر دست نمی‌خورد)', async () => {
  const items = await db.many(`SELECT row_id FROM sync_rows WHERE account_id=$1 AND table_name='saleItems' AND data->>'saleId'=$2`, [one.shopId, discountSale]);
  await A.drain();
  assert.equal(items.length, 0, `${items.length} قلمِ بی‌فاکتور روی سرور ماند و موجودیِ A را ${10 - A.stock(product)} کم کرد`);
  assert.equal(A.stock(product), 10);
}, { needs: ['E2E-SEC-13.op'] });

/** «یک ساعت گذشت»: توکنِ دسترسیِ این دستگاه پیر می‌شود و پنجرهٔ ارفاقِ توکن‌های چرخیده تمام */
async function anHourLater(userId) {
  await db.query('UPDATE tokens SET expires_at=$1 WHERE kind=$2 AND subject_id=$3', [Date.now() - 1000, 'access', userId]);
  await db.query('UPDATE tokens SET grace_until=$1 WHERE grace_until IS NOT NULL AND subject_id=$2', [Date.now() - 1000, userId]);
}

await E.step('#246', 'دو تازه‌سازیِ پشتِ سرِ هم در میانِ همگام‌سازی ⇐ برنامه وارد می‌ماند، داده می‌رسد، توکنِ ذخیره‌شده همان حلقهٔ زنده است', async () => {
  for (let round = 1; round <= 2; round++) {
    await anHourLater(one.user.id);
    A.sell({ lines: [{ id: product, qty: 1 }] });
    const r = await A.sync();
    assert.equal(r.ok, true, `دورِ ${round}: ${JSON.stringify(r)} ${JSON.stringify(A.lastRefresh || {})}`);
    assert.equal(A.queue.length, 0);
    assert.equal(A.refreshes, round, `دورِ ${round}: تازه‌سازی ${A.refreshes}`);
    const row = await db.one('SELECT revoked_at FROM tokens WHERE token_hash=$1 AND kind=$2', [hash(A.refreshToken), 'refresh']);
    assert.ok(row && row.revoked_at === null, `دورِ ${round}: توکنِ تازه‌سازیِ ذخیره‌شده زنده نیست`);
  }
}, { needs: ['E2E-SEC-13.0'] });

await E.step('#246.reuse', 'توکنِ تازه‌سازیِ کهنه (بیرون از پنجرهٔ ارفاق) دوباره می‌آید ⇐ «دزدی»: کلِ نشستِ همان دستگاه بسته می‌شود', async () => {
  const B = await E.device('B-owner-tablet', one.email);
  const stale = B.refreshToken;
  await anHourLater(one.user.id);
  assert.equal(await B.refresh(), true);
  await anHourLater(one.user.id);
  const thief = await E.post('/api/auth/refresh', { refreshToken: stale });
  assert.equal(thief.status, 401);
  assert.equal(thief.body.error.code, 'refresh_reused');
  const after = await B.api('GET', '/sync/v1/status?device_id=' + B.deviceId, undefined, { retry: false });
  assert.equal(after.status, 401, 'نشستِ دستگاهی که توکنش دزدیده شد باز ماند');
  //  دستگاهِ دیگرِ همان آدم (A) نشستِ خودش را دارد
  assert.equal((await A.api('GET', '/sync/v1/status?device_id=' + A.deviceId)).status, 200);
}, { needs: ['#246'] });

await E.step('SYN-10.logout', 'خروج پس از همگام‌سازی: سرور هر دو توکن را باطل می‌کند؛ دستگاهِ نو با Snapshot همه را دارد', async () => {
  const sales = A.ledger.sales.map((s) => s.id).sort();
  const rt = A.refreshToken;
  const out = await A.logout();
  assert.equal(out.blocked, false);
  const old = await E.post('/api/auth/refresh', { refreshToken: rt });
  assert.equal(old.status, 401, 'توکنِ تازه‌سازی پس از خروج هنوز کار می‌کند');
  const N = await E.device('N-new-phone', one.email);
  assert.equal((await N.firstSync()).ok, true);
  assert.deepEqual(N.ledger.sales.map((s) => s.id).sort(), sales, 'پس از خروج چیزی گم شد');
  assert.equal(N.stock(product), A.stock(product));
}, { needs: ['#246'] });

await E.finish();
