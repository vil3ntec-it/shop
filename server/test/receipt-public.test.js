'use strict';
/**
 * فاکتورِ عمومی — QRِ هر فاکتور (فاکتورِ نسخهٔ ۲): `/api/receipt/:tag/:token` و پوستهٔ `/r/…`.
 *
 * فروش از راهِ واقعیِ Sync v1 (push ِ دستگاه) می‌نشیند. سنجه‌ها: صفحه فقط آن‌چه روی
 * کاغذ است نشان می‌دهد (بی نامِ قرض‌دار و بی شناسه)، هرگز فروشِ دکانِ دیگر را باز
 * نمی‌کند (حتی وقتی همان توکن زودتر در دکانِ دیگری نشسته)، «هنوز همگام نشده» و
 * همهٔ حالت‌های بسته یک ۴۰۴ ِ یکسان‌اند، و هر پاسخ noindex و بی کش است.
 */
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';
process.env.RATE_RECEIPT_READ_MAX = '100000';
process.env.RATE_RECEIPT_READ_BURST_MAX = '6';

const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const h = require('./helpers');
const F = require('./image-fixtures');
const { query, now } = require('../src/db');
const config = require('../src/config');
const syncV1 = require('../src/lib/sync-v1');
const { resetAll } = require('../src/middleware/ratelimit');
const R = require('../src/lib/receipt-token');
const { NOT_YET } = require('../src/lib/receipt-public');

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
});
test.after(async () => { await h.stop(); });
test.beforeEach(() => resetAll());

let n = 0;
const opId = () => `01JRC${String(Date.now()).slice(-8)}${String(++n).padStart(8, '0')}`;
const op = (type, table, rowId, fields = {}) => ({ op_id: opId(), ts: Date.now(), table, row_id: rowId, type, fields });
const newToken = () => R.encodeToken(crypto.randomBytes(16));
const AT = Date.UTC(2026, 9, 24, 10, 5);   // ۱۴:۳۵ ِ کابل

async function shop(label) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const s = await h.signIn(u, 'shop');
  const shopId = made.body.shop.id;
  return { token: s.accessToken, shopId, tag: R.shopTag(shopId), device: `dev-${shopId}`.slice(0, 60) };
}

async function push(s, ops) {
  const r = await h.api('POST', '/api/sync/v1/push', {
    token: s.token, body: { device_id: s.device, schema_version: 2, ops }, headers: { 'X-App': 'shop', 'X-Device': s.device },
  });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  for (const x of r.body.results) assert.ok(['applied', 'duplicate'].includes(x.status), JSON.stringify(x));
}

/** فروشِ دو قلمی: کالای ثبت‌شده و قلمِ آزادِ وزنی، با قرض‌دار (که نباید دیده شود) */
async function sell(s, saleId, token, extra = {}) {
  const pid = `${saleId}-p`;
  await push(s, [
    op('insert', 'debtors', `${saleId}-d`, { name: 'محمودِ قرض‌دار', phone: '+93700000001', createdAt: AT }),
    op('insert', 'products', pid, { name: 'چای سبز', unit: 'بسته', salePrice: 150, purchasePrice: 100, active: true, barcodes: [], createdAt: AT }),
    op('insert', 'sales', saleId, {
      invoiceNumber: 154, series: 'B1', total: 450, discount: 50, finalTotal: 400, paymentMethod: 'cash',
      paidAmount: 400, remaining: 0, status: 'completed', createdAt: AT, date: '2026-10-24', seller: 'احمد',
      debtorId: `${saleId}-d`, receiptToken: token, ...extra,
    }),
    op('insert', 'saleItems', `${saleId}-i1`, { saleId, productId: pid, quantity: 3, unitPrice: 150, totalPrice: 450, returnedQty: 0 }),
    op('insert', 'saleItems', `${saleId}-i2`, { saleId, productId: '', name: 'قلمِ آزاد', quantity: 1.5, unitPrice: 0, totalPrice: 0, returnedQty: 0 }),
  ]);
}

const read = (tag, token) => h.get(`/api/receipt/${tag}/${token}`);

function assertPrivate(r) {
  assert.equal(r.headers.get('x-robots-tag'), 'noindex, nofollow, noarchive');
  assert.equal(r.headers.get('cache-control'), 'no-store');
  assert.equal(r.headers.get('referrer-policy'), 'no-referrer');
}

test('QR ِ فروشِ همگام‌شده همان فاکتورِ کاغذی را نشان می‌دهد — بی قرض‌دار، بی شناسه', async () => {
  const a = await shop('الف');
  const token = newToken();
  await sell(a, 'sale-a1', token);
  const r = await read(a.tag, token);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assertPrivate(r);
  const x = r.body.receipt;
  assert.deepEqual(x.shop, { name: 'دکانِ الف', address: '', phone: '', footer: '', logo: null });
  assert.equal(x.no, 'B1-154');
  assert.equal(x.date, '1405/08/02');
  assert.equal(x.time, '14:35');
  assert.equal(x.cashier, 'احمد');
  assert.equal(x.kind, 'نقدی');
  assert.equal(x.cancelled, false);
  assert.deepEqual(x.items, [
    { name: 'چای سبز', qty: '3', unit: 'بسته', unitPrice: '150', total: '450', returned: null },
    { name: 'قلمِ آزاد', qty: '1.5', unit: '', unitPrice: '0', total: '0', returned: null },
  ]);
  assert.deepEqual(x.count, { lines: '2', qty: null });
  assert.equal(x.subtotal, '450 افغانی');
  assert.deepEqual(x.discounts, [{ label: 'تخفیف', amount: '50 افغانی' }]);
  assert.equal(x.total, '400 افغانی');
  assert.deepEqual(x.payments, [{ label: 'پرداختی', amount: '400 افغانی' }]);
  assert.equal(x.remaining, null);
  const json = JSON.stringify(r.body);
  for (const secret of ['محمود', '+93700000001', 'sale-a1', a.shopId, a.device, token]) {
    assert.ok(!json.includes(secret), `نشت: ${secret}`);
  }

  //  اسکنرِ «همه بزرگ» هم همان را باز می‌کند
  assert.equal((await read(a.tag.toUpperCase(), token.toUpperCase())).status, 200);
});

test('تخفیفِ خودکار (پرچمِ shop.promo_stamp): ستون و «جمع اقلام» همان، قلم با قیمتِ فهرست و یک ردیفِ آگاهی', async () => {
  const a = await shop('ت');
  const token = newToken();
  const saleId = 'sale-p1';
  const pid = `${saleId}-p`;
  //  ۳ × ۳۵۰ = ۱۰۵۰، تخفیفِ خودکار ۵۰ ⇒ ۱۰۰۰ (همان قیمتِ ردیفی که برنامه ثبت می‌کند)
  await push(a, [
    op('insert', 'products', pid, { name: 'چای سبز', unit: 'بسته', salePrice: 350, purchasePrice: 100, active: true, barcodes: [], createdAt: AT }),
    op('insert', 'sales', saleId, {
      invoiceNumber: 155, series: 'B1', total: 1000, discount: 0, finalTotal: 1000, paymentMethod: 'cash',
      paidAmount: 1000, remaining: 0, status: 'completed', createdAt: AT, date: '2026-10-24', seller: 'احمد',
      receiptToken: token, promoDiscountMinor: 5000, coupons: ['EID5'],
    }),
    op('insert', 'saleItems', `${saleId}-i1`, {
      saleId, productId: pid, quantity: 3, unitPrice: 1000 / 3, totalPrice: 1000, returnedQty: 0,
      listPriceMinor: 35000, promo: [{ id: 'r1', rev: 2, name: 'ده درصدِ نوشیدنی', discountMinor: 5000 }],
    }),
  ]);
  const off = (await read(a.tag, token)).body.receipt;
  //  خاموش ⇒ همان پاسخِ پیشین
  assert.deepEqual(off.items, [{ name: 'چای سبز', qty: '3', unit: 'بسته', unitPrice: '333', total: '1,000', returned: null }]);
  assert.equal(off.included, undefined);
  assert.equal(off.subtotal, '1,000 افغانی');

  await query("INSERT INTO live_config (scope, key, value, updated_at) VALUES ($1,'shop.promo_stamp','true',1)", [a.shopId]);
  const on = (await read(a.tag, token)).body.receipt;
  assert.deepEqual(on.items, [{ name: 'چای سبز', qty: '3', unit: 'بسته', unitPrice: '350', total: '1,000', returned: null, autoDiscount: '50' }]);
  //  «جمع اقلام» = جمعِ ستونِ قلم‌ها؛ تخفیفِ خودکار فقط آگاهی، بی کسرِ دوباره
  assert.equal(on.subtotal, '1,000 افغانی');
  assert.deepEqual(on.included, [{ label: 'شاملِ تخفیفِ خودکار (ده درصدِ نوشیدنی)', amount: '50 افغانی' }]);
  assert.deepEqual(on.discounts, []);
  assert.equal(on.total, '1,000 افغانی');
  assert.ok(!JSON.stringify(on).includes('EID5'), 'کدِ تخفیف روی صفحهٔ عمومی نمی‌آید');
});

test('یادداشتِ ردیف و فاکتور (POS-11) روی صفحهٔ عمومی: یک خط و تمیز؛ بی یادداشت کلیدی نیست', async () => {
  const a = await shop('یادداشت');
  const token = newToken();
  await sell(a, 'sale-n1', token, { note: '  تحویل\n  ساعت ۵ ' });
  await push(a, [op('update', 'saleItems', 'sale-n1-i1', { note: 'بدون\tشکر\u0007' })]);
  const r = await read(a.tag, token);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  const x = r.body.receipt;
  assert.equal(x.note, 'تحویل ساعت ۵');
  assert.equal(x.items[0].note, 'بدون شکر');
  assert.ok(!('note' in x.items[1]));
  //  فروشِ بی یادداشت (یا با یادداشتِ تهی) همان پاسخِ پیشین است
  const plain = newToken();
  await sell(a, 'sale-n2', plain, { note: '   ' });
  const y = (await read(a.tag, plain)).body.receipt;
  assert.ok(!('note' in y));
  assert.ok(y.items.every((it) => !('note' in it)));
});

test('ناشناخته، همگام‌نشده، نشانِ دکانِ دیگر و شکلِ بد: یک ۴۰۴ ِ یکسان', async () => {
  const a = await shop('ب');
  const b = await shop('پ');
  const token = newToken();
  await sell(a, 'sale-b1', token);
  const bodies = [];
  for (const [tag, tok] of [[a.tag, newToken()], [b.tag, token], ['zzzzzzzz', token], [a.tag, 'nope'], ['x', token]]) {
    const r = await read(tag, tok);
    assert.equal(r.status, 404, `${tag}/${tok}`);
    assertPrivate(r);
    bodies.push(JSON.stringify(r.body));
  }
  assert.equal(new Set(bodies).size, 1);
  assert.deepEqual(JSON.parse(bodies[0]), { error: { code: 'receipt_not_found', message: NOT_YET } });
});

test('همان توکن در دکانِ دیگر — حتی زودتر — هرگز فاکتورِ این دکان را عوض نمی‌کند', async () => {
  const victim = await shop('ت');
  const thief = await shop('ث');
  const token = newToken();
  //  دزد توکنِ رسیدِ چاپ‌شده را پیش از همگام شدنِ دکانِ اصلی در فروش‌های خودش می‌گذارد —
  //  بیش از سقفِ نامزدها، تا اگر نامزدها به‌ازای دکان نبودند، فروشِ اصلی بیرون می‌ماند
  await push(thief, Array.from({ length: 60 }, (_, i) => op('insert', 'sales', `sale-thief-${i}`, {
    invoiceNumber: i + 1, total: 99999, finalTotal: 99999, paymentMethod: 'cash', paidAmount: 99999,
    status: 'completed', createdAt: AT, date: '2026-10-24', seller: 'دزد', receiptToken: token,
  })));
  await sell(victim, 'sale-victim', token);

  const mine = await read(victim.tag, token);
  assert.equal(mine.status, 200);
  assert.equal(mine.body.receipt.total, '400 افغانی');
  assert.equal(mine.body.receipt.shop.name, 'دکانِ ت');
  assert.ok(!JSON.stringify(mine.body).includes('دزد'));

  //  نشانیِ ساختگیِ دزد فقط فروشِ خودِ او را دارد، نه هیچ چیزِ دکانِ اصلی
  const his = await read(thief.tag, token);
  assert.equal(his.status, 200);
  assert.equal(his.body.receipt.shop.name, 'دکانِ ث');
  assert.ok(!JSON.stringify(his.body).includes('چای سبز'));
});

test('۶۰ دکانِ رقیب با همان توکن، همه زودتر از دکانِ اصلی ⇒ فاکتورِ اصلی باز هم پیدا می‌شود', async () => {
  const victim = await shop('ثِ');
  const token = newToken();
  //  هر ردیف از حسابی جدا و با زمانِ رسیدنِ پیش از فروشِ اصلی (سنجشِ نشان باید پیش از هر سقفی باشد)
  for (let i = 0; i < 60; i++) {
    await query(
      `INSERT INTO sync_rows (app, account_kind, account_id, table_name, row_id, data, created_at, updated_at)
       VALUES ('shop','shop',$1,'sales',$2,$3::jsonb,1,1)`,
      [`rival-${i}`, `sale-rival-${i}`, JSON.stringify({ invoiceNumber: i, total: 1, finalTotal: 1, status: 'completed', receiptToken: token })]
    );
  }
  await sell(victim, 'sale-many', token);
  const r = await read(victim.tag, token);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.receipt.shop.name, 'دکانِ ثِ');
  assert.equal(r.body.receipt.total, '400 افغانی');
});

test('لغو ⇒ «لغو شد»؛ پاک‌شده، QR ِ خاموش و دکانِ غیرفعال ⇒ همان ۴۰۴', async () => {
  const a = await shop('ج');
  const t1 = newToken();
  const t2 = newToken();
  await sell(a, 'sale-c1', t1);
  await sell(a, 'sale-c2', t2);

  await push(a, [op('update', 'sales', 'sale-c1', { status: 'cancelled', cancelReason: 'اشتباه' })]);
  const cancelled = await read(a.tag, t1);
  assert.equal(cancelled.status, 200);
  assert.equal(cancelled.body.receipt.cancelled, true);
  assert.ok(!JSON.stringify(cancelled.body).includes('اشتباه'));

  await push(a, [op('delete', 'sales', 'sale-c2')]);
  assert.equal((await read(a.tag, t2)).status, 404);

  const off = await h.put('/api/shop/receipt-profile', { qr: false }, { token: a.token });
  assert.equal(off.status, 200, JSON.stringify(off.body));
  assert.equal((await read(a.tag, t1)).status, 404);
  await h.put('/api/shop/receipt-profile', { qr: true }, { token: a.token });
  assert.equal((await read(a.tag, t1)).status, 200);

  await query("UPDATE shops SET status='disabled' WHERE id=$1", [a.shopId]);
  assert.equal((await read(a.tag, t1)).status, 404);
  await query("UPDATE shops SET status='active' WHERE id=$1", [a.shopId]);
});

test('سربرگ، رقمِ فارسی و لوگوی دکان؛ نسیه بی نامِ قرض‌دار؛ پرداختِ ترکیبی با پولِ پس', async () => {
  const a = await shop('چ');
  const credit = newToken();
  const mixed = newToken();
  await sell(a, 'sale-d1', credit, { paymentMethod: 'credit', paidAmount: 100, remaining: 300 });
  await sell(a, 'sale-d2', mixed, {
    paymentMethod: 'mixed', tenderedMinor: 50000, changeMinor: 10000,
    payments: [{ method: 'cash', kind: 'cash', amount: 30000, status: 'confirmed' }, { method: 'hesabpay', kind: 'wallet', amount: 10000, status: 'pending' }],
  });
  const put = await h.put('/api/shop/receipt-profile', {
    name: 'سوپرمارکتِ چ', address: 'کابل، شهرِ نو', phone: '0799 123 456', footer: 'باز هم بیایید', digits: 'fa', symbol: 'sign',
  }, { token: a.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const logo = await h.raw('PUT', '/api/shop/receipt-profile/logo', F.png(64, 32), { token: a.token });
  assert.equal(logo.status, 200, JSON.stringify(logo.body));

  const c = (await read(a.tag, credit)).body.receipt;
  assert.equal(c.shop.name, 'سوپرمارکتِ چ');
  assert.equal(c.shop.address, 'کابل، شهرِ نو');
  assert.equal(c.shop.phone, '۰۷۹۹ ۱۲۳ ۴۵۶');
  assert.equal(c.shop.footer, 'باز هم بیایید');
  assert.match(c.shop.logo, /^data:image\/png;base64,/);
  assert.equal(c.kind, 'نسیه');
  assert.equal(c.total, '۴۰۰ ؋');
  assert.equal(c.date, '۱۴۰۵/۰۸/۰۲');
  assert.deepEqual(c.remaining, { label: 'باقی‌مانده (نسیه)', amount: '۳۰۰ ؋' });
  assert.ok(!JSON.stringify(c).includes('محمود'));

  const m = (await read(a.tag, mixed)).body.receipt;
  assert.equal(m.kind, 'ترکیبی');
  assert.deepEqual(m.payments, [
    { label: 'نقد', amount: '۳۰۰ ؋' }, { label: 'کیف پول', amount: '۱۰۰ ؋' },
    { label: 'دریافتی', amount: '۵۰۰ ؋' }, { label: 'پولِ پس', amount: '۱۰۰ ؋' },
  ]);
});

test('فروشِ Sync v3 (Minor/Milli) هم با همان قاعده پیدا می‌شود، فقط وقتی v3 روشن است', async () => {
  const a = await shop('ح');
  const token = newToken();
  const t = now();
  const row = (table, id, data) => query(
    `INSERT INTO sync_v3_rows (app, account_kind, account_id, table_name, row_id, data, created_at, updated_at)
     VALUES ('shop','shop',$1,$2,$3,$4::jsonb,$5,$5)`, [a.shopId, table, id, JSON.stringify(data), t]
  );
  await row('sales', 'v3-sale', {
    invoiceNumber: 7, totalMinor: 25050, discountMinor: 0, finalTotalMinor: 25050, paymentMethod: 'cash',
    paidAmountMinor: 25050, remainingMinor: 0, status: 'completed', createdAt: AT, date: '2026-10-24', receiptToken: token,
  });
  await row('saleItems', 'v3-item', { saleId: 'v3-sale', productId: '', name: 'برنج', quantityMilli: 2500, unitPriceMinor: 10020, totalPriceMinor: 25050, returnedQtyMilli: 0 });

  const was = config.syncV3.enabled;
  try {
    config.syncV3.enabled = false;
    assert.equal((await read(a.tag, token)).status, 404);
    config.syncV3.enabled = true;
    const r = await read(a.tag, token);
    assert.equal(r.status, 200, JSON.stringify(r.body));
    assert.equal(r.body.receipt.total, '251 افغانی');
    assert.deepEqual(r.body.receipt.items, [{ name: 'برنج', qty: '2.5', unit: '', unitPrice: '100', total: '251', returned: null }]);
  } finally {
    config.syncV3.enabled = was;
  }
});

test('سقفِ ضربه‌ای به‌ازای IP ⇒ ۴۲۹، آن هم noindex', async () => {
  const a = await shop('خ');
  const token = newToken();
  await sell(a, 'sale-e1', token);
  for (let i = 0; i < config.rateLimit.receiptReadBurstMax; i++) assert.equal((await read(a.tag, token)).status, 200);
  const r = await read(a.tag, token);
  assert.equal(r.status, 429);
  assertPrivate(r);
});

test('پوستهٔ /r/<نشان>/<توکن>: همیشه همان صفحه، با CSP ِ سخت و noindex، بی پرسشِ داده', async () => {
  const base = h.base();
  const page = await fetch(`${base}/r/${R.shopTag('nobody')}/${newToken()}`);
  assert.equal(page.status, 200);
  assert.match(page.headers.get('content-security-policy'), /script-src 'self'/);
  assert.equal(page.headers.get('x-robots-tag'), 'noindex, nofollow, noarchive');
  assert.equal(page.headers.get('cache-control'), 'no-store');
  const html = await page.text();
  assert.match(html, /<meta name="robots" content="noindex, nofollow, noarchive">/);
  assert.match(html, /src="\/r\/receipt\.js"/);
  assert.ok(!/<script>|style="/.test(html), 'بی اسکریپت یا style ِ درون‌خطی');
  const js = await fetch(`${base}/r/receipt.js`);
  assert.equal(js.status, 200);
  assert.ok(!/innerHTML/.test(await js.text()), 'متن فقط با textContent');
});
