'use strict';
/**
 * پیوندِ کمپین و QR ِ دکان — MKT-10 و MKT-11 (بخشِ ۶.۲۰؛ `lib/campaigns.js`، مهاجرتِ ۱۵۳).
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) ⛔ پیش‌فرض خاموش: بی پرچمِ `shop.storefront.campaigns` نه ساختن، نه فهرست، و
 *      پیوندِ موجود همان ۴۰۴ ِ «ویترین پیدا نشد» است — یکسان با کدِ ناشناخته.
 *   ۲) ساختن با کلیدِ یکتاییِ برنامه (`clientId`): تکرار ⇒ همان پیوند؛ اعتبارسنجی؛
 *      سقفِ هر دکان؛ شاگرد می‌بیند ولی نمی‌سازد.
 *   ۳) `/store/go/<کد>` ⇒ ویترینِ همان دکان با `?c=<کد>`؛ پیوند به دکان گره دارد، نه
 *      به slug؛ بایگانی‌شده هنوز می‌برد ولی نمی‌شمارد؛ HEAD نمی‌شمارد؛ JSON برای برنامه.
 *   ۴) بازدیدِ یکتای روز (حذفِ تکرار با HMAC، بی IP و مرورگر) و پاک شدنِ ردیف‌های کهنه.
 *   ۵) سفارشِ ویترین ⇐ پیوند: فقط پیوندِ زندهٔ همان دکان؛ کدِ بد/بیگانه ثبت را
 *      نمی‌بندد؛ تکرارِ ثبت یک ردیف؛ «انجام‌شده» و جمعش فقط پس از تحویل.
 *   ۶) نمای دکان فقط شمار است — نه شماره، نه نامِ مشتری.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const campaigns = require('../src/lib/campaigns');

let admin = '';
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;
const FLAG = 'shop.storefront.campaigns';

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
test.after(async () => { await h.stop(); });
test.beforeEach(() => { resetAll(); campaigns.resetSweep(); });

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

const push = (token, ops, device = 'dev-camp') => h.api('POST', '/api/sync/v1/push', {
  token, headers: { 'X-App': 'shop', 'X-Device': device },
  body: { device_id: device, schema_version: 2, ops },
});
const op = (rowId, fields) => ({ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: rowId, type: 'insert', fields });

/** دکانِ روشن با ویترین و یک کالای منتشرشده؛ پرچمِ کمپین فقط با `campaignsOn`. */
async function liveStore(label, { campaignsOn = true } = {}) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { ...u, token: u.accessToken, shopId: made.body.shop.id };
  await setFlag(o.shopId, 'shop.orders', true);
  await setFlag(o.shopId, 'shop.storefront', true);
  if (campaignsOn) await setFlag(o.shopId, FLAG, true);
  const slug = `c-${uniq()}`;
  const put = await h.put('/api/shop/storefront', { slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001' }, { token: o.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  assert.equal((await push(o.token, [op('rice', { name: 'برنج', unit: 'کیلو', salePrice: 100, category: 'خوراکی' })])).status, 200);
  const pub = await h.put('/api/shop/storefront/products/rice', { published: true }, { token: o.token });
  assert.equal(pub.status, 200, JSON.stringify(pub.body));
  return { ...o, slug };
}

const create = (o, body) => h.post('/api/shop/storefront/campaigns', { clientId: `cl-${uniq()}`, name: 'QR ِ پیشخوان', source: 'qr_wall', ...body }, { token: o.token });
const list = (o, q = '') => h.get(`/api/shop/storefront/campaigns${q}`, { token: o.token });
const statsOf = async (o, id) => (await list(o)).body.campaigns.find((c) => c.id === id).stats;

/** پیوندِ عمومی بی دنبال کردنِ تغییرِ مسیر */
const go = (code, { method = 'GET', ua = 'Mozilla/5.0 test', accept = 'text/html' } = {}) =>
  fetch(`${h.base()}/store/go/${code}`, { method, redirect: 'manual', headers: { 'User-Agent': ua, Accept: accept } });

const tickets = new Map();
async function ticketFor(slug, phone) {
  const k = `${slug}|${phone}`;
  if (tickets.has(k)) return tickets.get(k);
  const s = await h.post(`/api/store/${slug}/verify/start`, { phone });
  assert.equal(s.status, 200, JSON.stringify(s.body));
  const c = await h.post(`/api/store/${slug}/verify/check`, { phone, code: s.body.devCode });
  assert.equal(c.status, 200, JSON.stringify(c.body));
  tickets.set(k, c.body.ticket);
  return c.body.ticket;
}
const PHONE = '0791112233';
async function checkout(o, extra = {}, { key = `ck-${uniq()}-key` } = {}) {
  const ticket = await ticketFor(o.slug, PHONE);
  return h.post(`/api/store/${o.slug}/checkout`, {
    fulfillment: 'pickup', customer: { name: 'مشتریِ کمپین', phone: PHONE },
    lines: [{ productId: 'rice', qtyMilli: 2000 }], ...extra,
  }, { headers: { 'Idempotency-Key': key, 'X-Store-Ticket': ticket } });
}
const attributed = (orderNo, shopId) => one(
  `SELECT a.campaign_id FROM store_campaign_orders a JOIN online_orders o ON o.id=a.order_id
    WHERE o.shop_id=$1 AND o.order_no=$2`, [shopId, orderNo]
);

/* ══════════════════════════════════════════════════════════════════
   ۱) پیش‌فرض خاموش
   ══════════════════════════════════════════════════════════════════ */

test('بی پرچم: config خاموش، ساختن و فهرست ۴۰۳ و بی ردیف؛ پیوندِ موجود با پرچمِ خاموش = همان ۴۰۴ ِ کدِ ناشناخته', async () => {
  const o = await liveStore('خاموش', { campaignsOn: false });
  const cfg = await h.get('/api/shop/storefront/campaigns/config', { token: o.token });
  assert.equal(cfg.status, 200, JSON.stringify(cfg.body));
  assert.equal(cfg.body.flag, false);
  assert.equal(cfg.body.enabled, false);
  const c = await create(o);
  assert.equal(c.status, 403);
  assert.equal(c.body.error.code, 'campaigns_disabled');
  assert.equal((await list(o)).status, 403);
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM store_campaigns WHERE shop_id=$1', [o.shopId])).n, 0);

  //  پرچم روشن ⇒ پیوند؛ دوباره خاموش ⇒ پیوند همان ۴۰۴ ِ «نیست»
  await setFlag(o.shopId, FLAG, true);
  const made = await create(o);
  assert.equal(made.status, 201, JSON.stringify(made.body));
  assert.equal((await go(made.body.campaign.code)).status, 302);
  await setFlag(o.shopId, FLAG, false);
  const off = await go(made.body.campaign.code, { accept: 'application/json' });
  const unknown = await go('ZZZZZZZZZZ', { accept: 'application/json' });
  assert.equal(off.status, 404);
  assert.equal(unknown.status, 404);
  assert.deepEqual(await off.json(), await unknown.json(), 'پرچمِ خاموش و کدِ ناشناخته یک پاسخ‌اند');
  //  و چیزی شمرده نشد
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM store_campaign_days WHERE shop_id=$1', [o.shopId])).n, 1);
  //  پوستهٔ HTML ِ ۴۰۴ ِ ویترین
  const html = await go('ZZZZZZZZZZ');
  assert.equal(html.status, 404);
  assert.match(html.headers.get('content-type') || '', /text\/html/);
  assert.match(html.headers.get('content-security-policy') || '', /script-src 'self'/);
});

/* ══════════════════════════════════════════════════════════════════
   ۲) ساختن، تکرار، اعتبارسنجی، اجازه و سقف
   ══════════════════════════════════════════════════════════════════ */

test('ساختن: کدِ ۱۰ نویسه‌ای و نشانیِ /store/go؛ همان clientId ⇒ همان پیوند بی ردیفِ دوم؛ ورودیِ بد ۴۰۰', async () => {
  const o = await liveStore('ساختن');
  const clientId = `cl-${uniq()}`;
  const a = await create(o, { clientId, name: '  اینستاگرامِ   دکان ', source: 'social' });
  assert.equal(a.status, 201, JSON.stringify(a.body));
  const c = a.body.campaign;
  assert.match(c.code, campaigns.CODE_RE);
  assert.equal(c.path, `/store/go/${c.code}`);
  assert.equal(c.name, 'اینستاگرامِ دکان');
  assert.equal(c.source, 'social');
  assert.equal(c.archived, false);
  assert.equal(c.createdBy, undefined, 'شناسهٔ کارمند در نما نیست');

  const again = await create(o, { clientId, name: 'نامِ دیگر', source: 'sms' });
  assert.equal(again.status, 200, JSON.stringify(again.body));
  assert.equal(again.body.replayed, true);
  assert.deepEqual(again.body.campaign, c, 'تکرار همان پیوند را برمی‌گرداند و چیزی را عوض نمی‌کند');
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM store_campaigns WHERE shop_id=$1', [o.shopId])).n, 1);

  for (const [body, codeName] of [
    [{ clientId: 'x' }, 'bad_client_id'],
    [{ name: '   ' }, 'bad_name'],
    [{ name: 'ن'.repeat(61) }, 'bad_name'],
    [{ source: 'billboard' }, 'bad_source'],
  ]) {
    const r = await create(o, body);
    assert.equal(r.status, 400, JSON.stringify(body));
    assert.equal(r.body.error.code, codeName);
  }
  //  نامِ ۶۰ نویسه‌ای درست است
  assert.equal((await create(o, { name: 'ن'.repeat(60) })).status, 201);

  //  ویرایش: نام و بایگانی؛ چیزی نفرستادن ۴۰۰؛ نوعِ نادرست ۴۰۰
  const p = await h.put(`/api/shop/storefront/campaigns/${c.id}`, { name: 'اینستا', archived: true }, { token: o.token });
  assert.equal(p.status, 200, JSON.stringify(p.body));
  assert.equal(p.body.campaign.name, 'اینستا');
  assert.equal(p.body.campaign.archived, true);
  assert.equal(p.body.campaign.code, c.code, 'کد عوض نمی‌شود');
  assert.equal((await h.put(`/api/shop/storefront/campaigns/${c.id}`, {}, { token: o.token })).body.error.code, 'nothing_to_update');
  assert.equal((await h.put(`/api/shop/storefront/campaigns/${c.id}`, { archived: 'yes' }, { token: o.token })).body.error.code, 'bad_archived');
  const audit = await one(`SELECT COUNT(*)::int AS n FROM audit_logs WHERE shop_id=$1 AND action LIKE 'storefront.campaign.%'`, [o.shopId]);
  assert.equal(audit.n, 3, 'دو ساختن و یک ویرایش؛ تکرار سابقهٔ تازه نمی‌سازد');
});

test('دکانِ دیگر پیوندِ این دکان را نه می‌بیند نه عوض می‌کند؛ شاگرد می‌بیند ولی نمی‌سازد', async () => {
  const a = await liveStore('الفِ کمپین');
  const b = await liveStore('بِ کمپین');
  const made = (await create(a)).body.campaign;
  const cross = await h.put(`/api/shop/storefront/campaigns/${made.id}`, { archived: true }, { token: b.token });
  assert.equal(cross.status, 404);
  assert.equal(cross.body.error.code, 'campaign_not_found');
  assert.ok(!JSON.stringify((await list(b)).body).includes(made.id));
  assert.equal((await one('SELECT archived FROM store_campaigns WHERE id=$1', [made.id])).archived, false);

  const code = (await h.post('/api/shop/staff-code', {}, { token: a.token })).body.code;
  const staff = await h.newUser('شاگردِ کمپین');
  assert.ok((await h.post('/api/shop/staff/join', { code }, { token: staff.accessToken })).status < 300);
  const seen = await h.get('/api/shop/storefront/campaigns', { token: staff.accessToken });
  assert.equal(seen.status, 200, JSON.stringify(seen.body));
  assert.equal(seen.body.campaigns.length, 1);
  const cfg = await h.get('/api/shop/storefront/campaigns/config', { token: staff.accessToken });
  assert.equal(cfg.body.permissions.manage, false);
  const st = await h.post('/api/shop/storefront/campaigns', { clientId: `cl-${uniq()}`, name: 'x', source: 'other' }, { token: staff.accessToken });
  assert.equal(st.status, 403);
  const sp = await h.put(`/api/shop/storefront/campaigns/${made.id}`, { archived: true }, { token: staff.accessToken });
  assert.equal(sp.status, 403);
});

test(`سقفِ ${campaigns.MAX_PER_SHOP} پیوند برای هر دکان (بایگانی‌شده هم می‌شمارد) ⇒ ۴۰۹`, async () => {
  const o = await liveStore('سقف');
  const t = now();
  for (let i = 0; i < campaigns.MAX_PER_SHOP - 1; i++) {
    const code = `0000${String(i).padStart(3, '0')}${'ABC'}`.slice(0, 10);
    await query(
      `INSERT INTO store_campaigns (id, shop_id, client_id, code, name, source, archived, created_at, updated_at)
       VALUES ($1,$2,$3,$4,'پر','other',true,$5,$5)`,
      [`cmp-fill-${i}-${uniq()}`, o.shopId, `fill-${i}`, code, t]
    );
  }
  assert.equal((await create(o)).status, 201, 'آخرین جای خالی');
  const over = await create(o);
  assert.equal(over.status, 409);
  assert.equal(over.body.error.code, 'campaign_limit');
});

/* ══════════════════════════════════════════════════════════════════
   ۳) پیوندِ عمومی و ۴) بازدیدِ یکتا
   ══════════════════════════════════════════════════════════════════ */

test('/store/go: ویترینِ همان دکان با ?c=؛ حروفِ کوچک هم؛ بازدیدِ یکتای روز؛ HEAD نمی‌شمارد؛ JSON برای برنامه؛ بی IP در دفتر', async () => {
  const o = await liveStore('پیوند');
  const c = (await create(o)).body.campaign;
  const r = await go(c.code);
  assert.equal(r.status, 302);
  assert.equal(r.headers.get('location'), `/store/${o.slug}?c=${c.code}`);
  assert.equal(r.headers.get('cache-control'), 'no-store');
  assert.equal((await go(c.code.toLowerCase())).status, 302, 'کدِ تایپ‌شده با حروفِ کوچک');
  assert.equal((await statsOf(o, c.id)).visits, 1, 'همان مرورگر همان روز ⇒ یک بازدید');
  assert.equal((await go(c.code, { ua: 'Other/2.0' })).status, 302);
  assert.equal((await statsOf(o, c.id)).visits, 2, 'مرورگرِ دیگر ⇒ بازدیدِ دوم');
  assert.equal((await go(c.code, { method: 'HEAD', ua: 'Prefetch/1.0' })).status, 302);
  assert.equal((await statsOf(o, c.id)).visits, 2, 'HEAD شمرده نمی‌شود');

  const j = await go(c.code, { accept: 'application/json', ua: 'App/1.0' });
  assert.equal(j.status, 200);
  assert.deepEqual(await j.json(), { store: { slug: o.slug, path: `/store/${o.slug}` }, campaign: c.code });
  assert.equal((await statsOf(o, c.id)).visits, 3, 'برنامه هم بازدید است');

  const v = await one('SELECT * FROM store_campaign_visitors WHERE campaign_id=$1 LIMIT 1', [c.id]);
  assert.equal(Object.keys(v).sort().join(','), 'campaign_id,day,visitor', 'جز اثرِ HMAC چیزی از بازدیدکننده نیست');
  assert.match(v.visitor, /^[A-Za-z0-9_-]{22}$/);
  const dump = JSON.stringify(await query('SELECT * FROM store_campaign_visitors'));
  for (const s of ['127.0.0.1', '::1', 'Mozilla', 'App/1.0']) assert.ok(!dump.includes(s), `«${s}» در دفترِ بازدید`);
});

test('پیوند به دکان گره دارد: slug عوض شود همان دکان؛ بایگانی‌شده می‌برد بی ?c= و بی شمارش؛ ویترینِ بسته ۴۰۴', async () => {
  const o = await liveStore('گره');
  const c = (await create(o)).body.campaign;
  const slug2 = `c2-${uniq()}`;
  assert.equal((await h.put('/api/shop/storefront', { slug: slug2 }, { token: o.token })).status, 200);
  assert.equal((await go(c.code)).headers.get('location'), `/store/${slug2}?c=${c.code}`);

  assert.equal((await h.put(`/api/shop/storefront/campaigns/${c.id}`, { archived: true }, { token: o.token })).status, 200);
  const before = (await statsOf(o, c.id)).visits;
  const arch = await go(c.code, { ua: 'Fresh/9.9' });
  assert.equal(arch.status, 302);
  assert.equal(arch.headers.get('location'), `/store/${slug2}`, 'QR ِ چاپ‌شده نمی‌شکند ولی دیگر نسبت نمی‌دهد');
  assert.equal((await statsOf(o, c.id)).visits, before);
  const aj = await go(c.code, { accept: 'application/json' });
  assert.equal((await aj.json()).campaign, null);

  assert.equal((await h.put('/api/shop/storefront', { enabled: false }, { token: o.token })).status, 200);
  assert.equal((await go(c.code)).status, 404);
});

test('ردیف‌های حذفِ تکرار پس از سه روز پاک می‌شوند؛ بازه‌ی فهرست فقط روزهای خودش را می‌شمارد', async () => {
  const o = await liveStore('کهنه');
  const c = (await create(o)).body.campaign;
  await query(`INSERT INTO store_campaign_visitors (campaign_id, day, visitor) VALUES ($1,'2001-01-01','old-visitor-hash-000000')`, [c.id]);
  await query(`INSERT INTO store_campaign_days (campaign_id, shop_id, day, visits) VALUES ($1,$2,'2001-01-01',40)`, [c.id, o.shopId]);
  campaigns.resetSweep();
  assert.equal((await go(c.code)).status, 302);
  assert.equal((await one(`SELECT COUNT(*)::int AS n FROM store_campaign_visitors WHERE day='2001-01-01'`)).n, 0);
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM store_campaign_visitors WHERE campaign_id=$1', [c.id])).n, 1, 'امروزی می‌ماند');
  assert.equal((await statsOf(o, c.id)).visits, 1, '۳۰ روزِ پیش‌فرض بازدیدِ ۲۰۰۱ را نمی‌شمارد');
  const wide = await list(o, '?days=365');
  assert.equal(wide.body.days, 365);
  for (const bad of ['0', '366', 'x', '1.5']) {
    const r = await list(o, `?days=${bad}`);
    assert.equal(r.status, 400, bad);
    assert.equal(r.body.error.code, 'bad_days');
  }
});

/* ══════════════════════════════════════════════════════════════════
   ۵) سفارش ⇐ پیوند، و ۶) نمای دکان فقط شمار
   ══════════════════════════════════════════════════════════════════ */

test('سفارشِ ویترین با campaign ⇐ همان پیوند؛ تکرارِ ثبت یک ردیف؛ کدِ بد/بیگانه/بایگانی ثبت را نمی‌بندد و نسبت نمی‌دهد', async () => {
  const o = await liveStore('سفارش');
  const other = await liveStore('سفارشِ دیگر');
  const c = (await create(o)).body.campaign;
  const foreign = (await create(other)).body.campaign;
  const dead = (await create(o, { name: 'کهنه' })).body.campaign;
  await h.put(`/api/shop/storefront/campaigns/${dead.id}`, { archived: true }, { token: o.token });

  const key = `ck-${uniq()}-key`;
  const first = await checkout(o, { campaign: c.code.toLowerCase() }, { key });
  assert.equal(first.status, 201, JSON.stringify(first.body));
  const replay = await checkout(o, { campaign: c.code.toLowerCase() }, { key });
  assert.equal(replay.status, 200, JSON.stringify(replay.body));
  assert.equal((await attributed(first.body.order.orderNo, o.shopId)).campaign_id, c.id);
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM store_campaign_orders WHERE campaign_id=$1', [c.id])).n, 1);
  assert.equal(first.body.order.totalMinor, 20000, 'نسبت دادن مبلغ را دست نمی‌زند');

  for (const campaign of [foreign.code, dead.code, 'not-a-code', 12345, { x: 1 }]) {
    const r = await checkout(o, { campaign });
    assert.equal(r.status, 201, `${JSON.stringify(campaign)}: ${JSON.stringify(r.body)}`);
    assert.equal(await attributed(r.body.order.orderNo, o.shopId), null, JSON.stringify(campaign));
  }
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM store_campaign_orders WHERE campaign_id=$1', [foreign.id])).n, 0);

  //  پرچمِ خاموش ⇒ ثبت می‌شود، نسبت نه
  await setFlag(o.shopId, FLAG, false);
  const off = await checkout(o, { campaign: c.code });
  assert.equal(off.status, 201);
  assert.equal(await attributed(off.body.order.orderNo, o.shopId), null);
  await setFlag(o.shopId, FLAG, true);

  //  شمار: یک سفارش، هنوز انجام‌نشده
  let s = await statsOf(o, c.id);
  assert.deepEqual({ orders: s.orders, doneOrders: s.doneOrders, doneMinor: s.doneMinor }, { orders: 1, doneOrders: 0, doneMinor: 0 });
  //  تا «برداشته شد» ⇒ انجام‌شده با همان جمعِ سفارش
  const id = (await one('SELECT id FROM online_orders WHERE shop_id=$1 AND order_no=$2', [o.shopId, first.body.order.orderNo])).id;
  for (const to of ['accepted', 'picking', 'packed', 'ready_for_pickup', 'picked_up']) {
    const r = await h.post(`/api/shop/orders/${id}/transition`, { to }, { token: o.token });
    assert.equal(r.status, 200, `${to}: ${JSON.stringify(r.body)}`);
  }
  s = await statsOf(o, c.id);
  assert.deepEqual({ orders: s.orders, doneOrders: s.doneOrders, doneMinor: s.doneMinor }, { orders: 1, doneOrders: 1, doneMinor: 20000 });

  //  ۶) نمای دکان: نه شماره، نه نامِ مشتری، نه شناسهٔ سفارش
  const text = JSON.stringify((await list(o)).body);
  for (const secret of [PHONE, 'مشتریِ کمپین', id]) assert.ok(!text.includes(secret), `«${secret}» در نمای پیوندها`);

  //  سفارشِ آزمایشی در شمار نمی‌آید (همان قاعدهٔ گزارش‌های فروشِ آنلاین)
  await query('UPDATE online_orders SET is_test=true WHERE id=$1', [id]);
  s = await statsOf(o, c.id);
  assert.deepEqual({ orders: s.orders, doneOrders: s.doneOrders, doneMinor: s.doneMinor }, { orders: 0, doneOrders: 0, doneMinor: 0 });
});

test('JSON ِ عمومی فقط slug و کد است؛ پیوندِ دکانِ بی ویترین ۴۰۴', async () => {
  const o = await liveStore('عمومی');
  const c = (await create(o)).body.campaign;
  const body = await (await go(c.code, { accept: 'application/json' })).json();
  assert.deepEqual(Object.keys(body).sort(), ['campaign', 'store']);
  assert.deepEqual(Object.keys(body.store).sort(), ['path', 'slug']);
  assert.ok(!JSON.stringify(body).includes(o.shopId));

  //  دکانی که پرچم دارد ولی هنوز ویترین نساخته: ساختن آزاد است، باز کردن ۴۰۴
  const u = await h.newUser('بی‌ویترین', 'shop');
  const shopId = (await h.post('/api/shop', { name: 'دکانِ بی ویترین' }, { token: u.accessToken })).body.shop.id;
  for (const k of ['shop.orders', 'shop.storefront', FLAG]) await setFlag(shopId, k, true);
  const bare = await h.post('/api/shop/storefront/campaigns', { clientId: `cl-${uniq()}`, name: 'دیوار', source: 'qr_wall' }, { token: u.accessToken });
  assert.equal(bare.status, 201, JSON.stringify(bare.body));
  assert.equal((await go(bare.body.campaign.code)).status, 404);
});
