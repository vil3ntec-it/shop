'use strict';
/**
 * تخفیفِ ویترین — MKT-01 (کد و خودکار) و MKT-09 (تخفیفِ زمان‌دار، «پایان در …»)، پشتِ پرچمِ `shop.storefront.promos`.
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) پرچمِ خاموش ⇒ درهای عمومی ۴۰۴ و ثبتِ ویترین همان پیشین (کلیدِ `promoCode` نادیده، تخفیف ۰).
 *   ۲) فقط قاعده‌های «اینترنتی» (نه صندوق)، در بازهٔ زمانیِ دکان؛ حساب همان `promo-core.evaluate`.
 *   ۳) پیش‌نمایش = ثبت؛ کدِ ناشناخته در ثبت ۴۰۹ `promo_code_rejected`، در پیش‌نمایش فقط `status`.
 *   ۴) کدِ سقف‌دار زیرِ قفل و در همان تراکنش در دفترِ مصرف؛ تکرارِ همان درخواست مصرفِ دوم نمی‌سازد؛ پر ⇒ ۴۰۹ `used_up`.
 *   ۵) «جمعِ دیده‌شده» پس از تخفیف سنجیده می‌شود؛ فهرستِ عمومی هیچ کدی را نشان نمی‌دهد.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');

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
  assert.ok(admin, 'ورودِ مدیر');
});
test.after(async () => { await h.stop(); });
test.beforeEach(() => resetAll());

async function setFlag(scope, k, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key: k, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}
const push = (token, ops) => h.api('POST', '/api/sync/v1/push', {
  token, headers: { 'X-App': 'shop', 'X-Device': 'dev-promo' },
  body: { device_id: 'dev-promo', schema_version: 2, ops },
});
const op = (rowId, fields) => ({ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: rowId, type: 'insert', fields });

async function liveShop(label, { promos = true } = {}) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const s = { token: u.accessToken, shopId: made.body.shop.id, slug: `p-${uniq()}` };
  for (const k of ['shop.orders', 'shop.storefront', 'shop.promo_uses']) await setFlag(s.shopId, k, true);
  if (promos) await setFlag(s.shopId, 'shop.storefront.promos', true);
  const put = await h.put('/api/shop/storefront', { slug: s.slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001' }, { token: s.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  assert.equal((await push(s.token, [
    op('rice', { name: 'برنج', unit: 'بسته', salePrice: 120, category: 'خوراکی', purchasePrice: 100 }),
    op('soap', { name: 'صابون', unit: 'دانه', salePrice: 40, category: 'بهداشتی' }),
  ])).status, 200);
  for (const id of ['rice', 'soap']) assert.equal((await h.put(`/api/shop/storefront/products/${id}`, { published: true }, { token: s.token })).status, 200);
  return s;
}

async function rule(s, id, data) {
  const r = await h.put(`/api/shop/promos/rules/${id}`, { rule: { name: id, stack: 'best', active: true, ...data } }, { token: s.token });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body.rule;
}

const tickets = new Map();
async function ticketFor(slug, phone) {
  const k = `${slug}|${phone}`;
  if (tickets.has(k)) return tickets.get(k);
  const st = await h.post(`/api/store/${slug}/verify/start`, { phone });
  assert.equal(st.status, 200, JSON.stringify(st.body));
  const c = await h.post(`/api/store/${slug}/verify/check`, { phone, code: st.body.devCode });
  assert.equal(c.status, 200, JSON.stringify(c.body));
  tickets.set(k, c.body.ticket);
  return c.body.ticket;
}

const LINES = [{ productId: 'rice', qtyMilli: 2000 }, { productId: 'soap', qtyMilli: 1000 }];   //  ۲۴۰ + ۴۰ = ۲۸۰ افغانی
async function checkout(s, extra = {}, key = `ck-${uniq()}-key`) {
  const phone = '0791112233';
  return h.post(`/api/store/${s.slug}/checkout`, {
    fulfillment: 'delivery', customer: { name: 'مشتری', phone }, address: { landmark: 'پشتِ مسجد' }, lines: LINES, ...extra,
  }, { headers: { 'Idempotency-Key': key, 'X-Store-Ticket': await ticketFor(s.slug, phone) } });
}
const quote = (s, body) => h.post(`/api/store/${s.slug}/promos/quote`, { lines: LINES, ...body });
const orderOf = async (s, r) => one('SELECT subtotal_minor, discount_minor, delivery_fee_minor, total_minor FROM online_orders WHERE shop_id=$1 AND order_no=$2', [s.shopId, r.body.order.orderNo]);
const code = (r) => r.body?.error?.code;

/* ══════════════════════════════════════════════════════════════════ */

test('MKT-01 خاموش: درهای عمومی ۴۰۴؛ ثبتِ ویترین همان پیشین — تخفیف ۰ حتی با قاعدهٔ اینترنتی و promoCode', async () => {
  const s = await liveShop('تخفیف-خاموش', { promos: false });
  await rule(s, 'online10', { kind: 'percent', bp: 1000, channels: ['online'] });
  for (const r of [await h.get(`/api/store/${s.slug}/promos`), await quote(s, {})]) {
    assert.equal(r.status, 404, JSON.stringify(r.body));
    assert.equal(code(r), 'promos_disabled');
  }
  const r = await checkout(s, { promoCode: 'ANYTHING' });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const o = await orderOf(s, r);
  assert.equal(Number(o.discount_minor), 0);
  assert.equal(Number(o.total_minor), Number(o.subtotal_minor) + Number(o.delivery_fee_minor));
});

test('MKT-01: خودکارِ اینترنتی روی دستهٔ خوراکی — پیش‌نمایش = ثبت؛ قاعدهٔ صندوق اعمال نمی‌شود؛ فهرستِ عمومی بی کد', async () => {
  const s = await liveShop('تخفیف-خودکار');
  await rule(s, 'food10', { kind: 'percent', bp: 1000, channels: ['online'], scope: { categories: ['خوراکی'] }, window: { to: '2099-12-31' } });
  await rule(s, 'posonly', { kind: 'percent', bp: 5000, channels: ['pos'] });
  await rule(s, 'secret', { kind: 'percent', bp: 500, channels: ['online'], code: 'VIP5', stack: 'combine' });
  await rule(s, 'expired', { kind: 'percent', bp: 3000, channels: ['online'], window: { to: '2001-01-01' } });

  const list = await h.get(`/api/store/${s.slug}/promos`);
  assert.equal(list.status, 200, JSON.stringify(list.body));
  assert.deepEqual(list.body.promos, [{ id: 'food10', name: 'food10', kind: 'percent', endsOn: '2099-12-31' }], 'فقط خودکار، زنده و اینترنتی');
  assert.equal(list.body.codes, true, 'کدی پذیرفته می‌شود');
  assert.ok(!JSON.stringify(list.body).includes('VIP5'), 'هیچ کدی فهرست نمی‌شود');
  assert.equal(list.headers.get('cache-control'), 'no-store');

  const q = await quote(s, {});
  assert.equal(q.status, 200, JSON.stringify(q.body));
  assert.deepEqual([q.body.subtotalMinor, q.body.discountMinor, q.body.totalMinor], [28000, 2400, 25600]);
  assert.deepEqual(q.body.lines, [{ lineNo: 1, discountMinor: 2400 }, { lineNo: 2, discountMinor: 0 }]);
  assert.deepEqual(q.body.applied, [{ name: 'food10', discountMinor: 2400 }]);
  assert.equal(q.body.code, null);

  const r = await checkout(s);
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const o = await orderOf(s, r);
  assert.equal(Number(o.discount_minor), 2400, 'همان پیش‌نمایش');
  assert.equal(Number(o.total_minor), 28000 - 2400 + Number(o.delivery_fee_minor));
  assert.equal(r.body.order.discountMinor, 2400);
});

test('MKT-01: کد — اعمال، ناشناخته (ثبت ۴۰۹، پیش‌نمایش فقط وضعیت)، و «جمعِ دیده‌شده» پس از تخفیف', async () => {
  const s = await liveShop('تخفیف-کد');
  await rule(s, 'vip', { kind: 'percent', bp: 1000, channels: ['online'], code: 'VIP10' });
  let q = await quote(s, { promoCode: ' vip-10 ' });
  assert.deepEqual([q.body.discountMinor, q.body.code], [2800, { code: 'VIP10', status: 'applied' }], 'همان نرمال‌سازیِ صندوق');
  q = await quote(s, { promoCode: 'NOPE99' });
  assert.equal(q.status, 200);
  assert.deepEqual([q.body.discountMinor, q.body.code.status], [0, 'unknown']);

  let r = await checkout(s, { promoCode: 'NOPE99' });
  assert.equal(r.status, 409, JSON.stringify(r.body));
  assert.equal(code(r), 'promo_code_rejected');
  assert.deepEqual(r.body.error.promo, { code: 'NOPE99', status: 'unknown' });

  const fee = Number((await one('SELECT delivery_fee_minor FROM storefronts WHERE shop_id=$1', [s.shopId])).delivery_fee_minor);
  r = await checkout(s, { promoCode: 'VIP10', expectedTotalMinor: 28000 + fee });
  assert.equal(r.status, 409, 'جمعِ بی تخفیف دیگر درست نیست');
  assert.equal(code(r), 'price_changed');
  r = await checkout(s, { promoCode: 'VIP10', expectedTotalMinor: 28000 - 2800 + fee });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.equal(Number((await orderOf(s, r)).discount_minor), 2800);
});

test('MKT-01: کدِ سقف‌دار در همان تراکنش در دفترِ مصرف — تکرارِ همان درخواست یک مصرف؛ پر ⇒ ۴۰۹ used_up', async () => {
  const s = await liveShop('تخفیف-سقف');
  await rule(s, 'once', { kind: 'percent', bp: 2000, channels: ['online'], code: 'ONCE20', maxUses: 1 });
  const key = `ck-${uniq()}-key`;
  const first = await checkout(s, { promoCode: 'ONCE20' }, key);
  assert.equal(first.status, 201, JSON.stringify(first.body));
  const again = await checkout(s, { promoCode: 'ONCE20' }, key);
  assert.ok([200, 201].includes(again.status), JSON.stringify(again.body));
  assert.equal(again.body.order.orderNo, first.body.order.orderNo, 'همان سفارش');
  const uses = await many(`SELECT sale_id, device_id FROM shop_promo_redemptions WHERE shop_id=$1 AND promo_id='once' AND released_at IS NULL`, [s.shopId]);
  assert.deepEqual(uses.map((u) => [u.sale_id, u.device_id]), [[`sf:${key}`, 'storefront']]);
  const g = await h.get('/api/shop/promos', { token: s.token });
  assert.equal(g.body.rules.find((x) => x.id === 'once').uses, 1, 'صندوق همان شمار را می‌بیند');

  const second = await checkout(s, { promoCode: 'ONCE20' });
  assert.equal(second.status, 409, JSON.stringify(second.body));
  assert.equal(code(second), 'promo_code_rejected');
  assert.equal(second.body.error.promo.status, 'used_up');
  const q = await quote(s, { promoCode: 'ONCE20' });
  assert.equal(q.body.code.status, 'used_up');
  const list = await h.get(`/api/store/${s.slug}/promos`);
  assert.equal(list.body.codes, false, 'کدِ پر دیگر «پذیرفته» شمرده نمی‌شود');
});

test('MKT-01: ورودیِ پیش‌نمایش سنجیده می‌شود؛ کالای نبوده جدا می‌آید؛ دکانِ دیگر قاعده‌های این دکان را نمی‌بیند', async () => {
  const s = await liveShop('تخفیف-ورودی');
  await rule(s, 'all5', { kind: 'percent', bp: 500, channels: ['online'] });
  for (const bad of [{ lines: [] }, { lines: 'x' }, { lines: [{ productId: 'rice', qtyMilli: 0 }] }, { lines: [{ productId: '', qtyMilli: 1 }] }]) {
    const r = await h.post(`/api/store/${s.slug}/promos/quote`, bad);
    assert.equal(r.status, 400, JSON.stringify(bad));
    assert.equal(code(r), 'bad_lines');
  }
  const r = await h.post(`/api/store/${s.slug}/promos/quote`, { lines: [{ productId: 'rice', qtyMilli: 1000 }, { productId: 'ghost', qtyMilli: 1000 }] });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual([r.body.subtotalMinor, r.body.discountMinor, r.body.unavailable], [12000, 600, ['ghost']]);

  const t = await liveShop('دکانِ دیگر');
  const other = await h.post(`/api/store/${t.slug}/promos/quote`, { lines: [{ productId: 'rice', qtyMilli: 1000 }] });
  assert.equal(other.body.discountMinor, 0, 'قاعدهٔ دکانِ دیگر');
});
