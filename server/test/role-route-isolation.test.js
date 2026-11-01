'use strict';
/**
 * E2E-SEC-13 (§۲۴.۶): مشتری، فروشگاه، پیک و مدیر هر کدام فقط دادهٔ نقشِ خود را
 * می‌بینند؛ عوض کردنِ شناسهٔ نشانی دسترسی نمی‌دهد.
 *
 * `shop-route-inventory.test.js` همین را برای `/api/shop` و `courier-scope.test.js`
 * برای توکنِ پیک روی درهای دیگر قفل می‌کنند. این پرونده بقیهٔ ماتریس را، از
 * فهرستِ زندهٔ روترها (نه فهرستِ دستی)، می‌بندد:
 *   ۱) درِ مدیر (`/api/admin/*`): توکنِ دکان، پمپ، پیک و نشستِ مشتری ⇒ ۴۰۱ روی هر در؛
 *      هر دری که `requireSuperAdmin` دارد برای مدیرِ عادی ⇒ ۴۰۳.
 *   ۲) درِ پیک (`/api/courier/*`): بی‌توکن، صاحبِ دکان، مدیر و مشتری ⇒ هیچ ۲xx؛
 *      پیکِ دیگرِ همان دکان و پیکِ دکانِ دیگر مأموریتِ پیکِ اول را نه می‌بینند نه عوض می‌کنند.
 *   ۳) درِ مشتری (`/api/store/*` با نشستِ حساب یا برگهٔ تأییدِ شماره، از جمله چت و شکایت):
 *      بی‌نشست، با توکنِ دکان/پیک/مدیر ⇒ ۴۰۱؛
 *      مشتریِ دیگر (و مشتریِ ویترینِ دیگر) با شناسه‌های واقعیِ مشتریِ اول هیچ دادهٔ او را نمی‌گیرد؛
 *      توکنِ پیگیریِ دست‌کاری‌شده یا روی ویترینِ دیگر ⇒ ۴۰۴.
 *   کارمندانِ دکان (شاگرد و چهار نقشِ تازهٔ A16: صندوق‌دار، انباردار، جمع‌آور، حسابدار) در هر سه
 *   ماتریس همان انتظارِ صاحبِ دکان را دارند: هیچ‌کدام بازتر از شاگرد نیست (STF-01).
 */
const test = require('node:test');
const assert = require('node:assert/strict');

//  نقشهٔ سوارشدن از روترِ زنده — پیش از ساختنِ برنامه (`route-inventory.tapMounts`، همان شگردِ courier-scope)
const inv = require('./route-inventory');
inv.tapMounts();
const h = require('./helpers');
const { query, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');

/** همهٔ درهای زیرِ `/api` با نامِ میان‌افزارهای زنجیره‌شان (`route-inventory.live`). */
const live = () => inv.live(h.server().listeners('request')[0]);
const fill = (p, ids = {}) => p.replace(/:([A-Za-z_]+)/g, (_, k) => ids[k] ?? 'x1');
const noBody = (m) => m === 'GET' || m === 'DELETE';
//  سقف‌های درخواست (۴۲۹) این‌جا موضوع نیستند — هر درخواستِ فهرست با شمارندهٔ پاک
const call = (...args) => { resetAll(); return h.api(...args); };
const leaks = (res, secrets) => secrets.filter((s) => s && JSON.stringify(res.body ?? '').includes(s));

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
test.after(async () => { await h.stop(); });
test.beforeEach(() => resetAll());

const flag = (scope, key) => h.put('/api/admin/pump/live-config', { scope, key, value: true }, { token: admin })
  .then((r) => assert.equal(r.status, 200, JSON.stringify(r.body)));

/** دکانِ روشن با ویترین، سفارش و پیک؛ دو کالای منتشرشده. */
async function liveShop(label) {
  const u = await h.newUser(`صاحب-${label}`);
  const shopId = (await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken })).body.shop.id;
  for (const k of ['shop.orders', 'shop.storefront', 'shop.delivery', 'shop.chat', 'shop.storefront.zones', 'shop.storefront.notify_area', 'shop.storefront.cart']) await flag(shopId, k);
  const slug = `iso-${uniq()}`;
  const put = await h.put('/api/shop/storefront', { slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001' }, { token: u.accessToken });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const ops = ['rice', 'oil'].map((id) => ({ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: id, type: 'insert', fields: { name: id, unit: 'کیلو', salePrice: 100 } }));
  const p = await h.api('POST', '/api/sync/v1/push', { token: u.accessToken, headers: { 'X-App': 'shop', 'X-Device': 'dev-iso' }, body: { device_id: 'dev-iso', schema_version: 2, ops } });
  assert.equal(p.status, 200, JSON.stringify(p.body));
  for (const id of ['rice', 'oil']) assert.equal((await h.put(`/api/shop/storefront/products/${id}`, { published: true }, { token: u.accessToken })).status, 200);
  return { ...u, token: u.accessToken, shopId, slug };
}

async function courier(owner, name) {
  const code = (await h.post('/api/shop/invite', { role: 'courier' }, { token: owner.token })).body.code;
  const r = await h.post('/api/auth/staff-login', { code, name, device: { uid: `cphone-${uniq()}`, name: 'گوشی', platform: 'android' } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const c = { token: r.body.accessToken, uid: r.body.device?.uid };
  c.id = (await h.get('/api/courier/me', { token: c.token })).body.courier.id;
  return c;
}

async function customer(slug, phone) {
  const s = await h.post(`/api/store/${slug}/verify/start`, { phone });
  assert.equal(s.status, 200, JSON.stringify(s.body));
  const c = await h.post(`/api/store/${slug}/verify/check`, { phone, code: s.body.devCode });
  const a = await h.post(`/api/store/${slug}/account`, {}, { headers: { 'X-Store-Ticket': c.body.ticket } });
  assert.ok(a.status < 300, JSON.stringify(a.body));
  return { session: a.body.session, ticket: c.body.ticket, phone };
}
const as = (k) => ({ headers: { 'X-Store-Session': k.session } });

/** شاگرد و چهار نقشِ تازهٔ A16 — هر کدام با نقشِ واقعی‌اش وارد شده (`actualRole`) */
const STAFF = [['staff', 'شاگرد'], ['cashier', 'صندوق‌دار'], ['storekeeper', 'انباردار'], ['picker', 'جمع‌آور'], ['accountant', 'حسابدار']];
async function members(owner) {
  const fresh = await h.signIn(owner, 'shop');          //  دعوتِ نقش‌های تازه هویتِ تازه می‌خواهد
  const out = [];
  for (const [role, name] of STAFF) {
    resetAll();
    const inv = await h.post('/api/shop/invite', { role }, { token: fresh.accessToken });
    assert.equal(inv.status, 201, `${role}: ${JSON.stringify(inv.body)}`);
    const u = await h.newUser(`${name}-${uniq()}`, 'shop');
    const j = await h.post('/api/shop/join', { code: inv.body.code }, { token: u.accessToken });
    assert.ok([200, 201].includes(j.status), `${role}: ${JSON.stringify(j.body)}`);
    const s = await h.signIn(u, 'shop');
    assert.equal(s.shop.actualRole, role, `${role}: نقشِ واقعی`);
    //  توکنِ زنده — آزمون بی‌اثر نیست
    assert.equal((await h.get('/api/me', { token: s.accessToken })).status, 200, `${role}: /api/me`);
    out.push([name, { token: s.accessToken }]);
  }
  resetAll();
  return out;
}
/** درِ مشتری با نشستِ حساب (session_required) یا با برگهٔ تأییدِ شماره (verification_required، چت و شکایت). */
const AUTH = { session_required: (k) => ({ 'X-Store-Session': k.session }), verification_required: (k) => ({ 'X-Store-Ticket': k.ticket }) };

test('E2E-SEC-13 (مدیر): توکنِ دکان، پمپ، پیک و نشستِ مشتری روی هیچ درِ مدیر کار نمی‌کند؛ درهای ارشد برای مدیرِ عادی ۴۰۳', async () => {
  const a = await liveShop('مدیر');
  const pump = await h.signIn(a, 'pump');
  const c = await courier(a, 'پیکِ مدیر');
  const k = await customer(a.slug, '0791230001');
  const staff = await members(a);
  const made = await h.post('/api/admin/admins', { username: 'clerk', password: 'Clerk!12345', role: 'admin' }, { token: admin });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const clerk = (await h.post('/api/admin/login', { username: 'clerk', password: 'Clerk!12345' })).body.token;
  assert.equal((await h.get('/api/admin/me', { token: clerk })).status, 200, 'مدیرِ عادی وارد است — آزمون بی‌اثر نیست');

  const routes = live().filter((r) => r.path.startsWith('/api/admin/'));
  assert.ok(routes.length > 150, `فقط ${routes.length} درِ مدیر`);
  const PUBLIC = new Set(['POST /api/admin/login', 'POST /api/admin/logout']);
  const supers = routes.filter((r) => r.chain.includes('requireSuperAdmin'));
  assert.ok(supers.length >= 40, `درهای ارشد: ${supers.length}`);
  const bad = [];
  for (const r of routes) {
    if (PUBLIC.has(r.key)) continue;
    const body = noBody(r.method) ? null : {};
    for (const [who, opts] of [['بی‌توکن', {}], ['دکان', { token: a.token }], ['پمپ', { token: pump.accessToken }],
      ['پیک', { token: c.token }], ['مشتری', as(k)], ...staff]) {
      const res = await call(r.method, fill(r.path), { body, ...opts });
      if (res.status !== 401) bad.push(`${r.key} با ${who} ⇒ ${res.status}`);
    }
    if (r.chain.includes('requireSuperAdmin')) {
      const res = await call(r.method, fill(r.path), { body, token: clerk });
      if (res.status !== 403) bad.push(`${r.key} با مدیرِ عادی ⇒ ${res.status}`);
    }
  }
  assert.deepEqual(bad, [], bad.join('\n'));
});

test('E2E-SEC-13 (پیک): نقش‌های دیگر به درِ پیک نمی‌رسند؛ پیکِ دیگر مأموریتِ پیکِ اول را نمی‌بیند و عوض نمی‌کند', async () => {
  const a = await liveShop('پیک-الف');
  const b = await liveShop('پیک-ب');
  const [c1, c2, cb] = [await courier(a, 'پیکِ یک'), await courier(a, 'پیکِ دو'), await courier(b, 'پیکِ ب')];
  const PHONE = '0791230002';
  const order = await h.post('/api/shop/orders', {
    source: 'phone', fulfillment: 'delivery', paymentMethod: 'cod', customer: { name: 'احمد', phone: PHONE },
    address: { landmark: 'مسجدِ جامع', text: 'کوچهٔ رازِ-پیک', lat: 34.5, lng: 69.1 }, delivery: { photoConsent: true },
    lines: [{ name: 'برنج', qtyMilli: 1000, unitPriceMinor: 10_000 }], deliveryFeeMinor: 0,
  }, { token: a.token, headers: { 'Idempotency-Key': `o-${uniq()}` } });
  assert.equal(order.status, 201, JSON.stringify(order.body));
  for (const to of ['accepted', 'picking', 'packed']) assert.equal((await h.post(`/api/shop/orders/${order.body.order.id}/transition`, { to }, { token: a.token })).status, 200);
  const asg = await h.post(`/api/shop/delivery/orders/${order.body.order.id}/assign`, { mode: 'shop_courier', courierId: c1.id, overrideCashCap: true }, { token: a.token });
  assert.equal(asg.status, 201, JSON.stringify(asg.body));
  const asgId = asg.body.assignment?.id || asg.body.id;
  assert.ok(asgId, JSON.stringify(asg.body));
  const mine = await h.get('/api/courier/missions', { token: c1.token });
  assert.ok(JSON.stringify(mine.body).includes(asgId), 'پیکِ یک مأموریتِ خودش را می‌بیند');

  const routes = live().filter((r) => r.path.startsWith('/api/courier/'));
  assert.ok(routes.length >= 4, `درهای پیک: ${routes.length}`);
  const k = await customer(a.slug, '0791230003');
  const staff = await members(a);
  const secrets = [asgId, order.body.order.id, 'رازِ-پیک', '791230002'];
  const bad = [];
  for (const r of routes) {
    const url = fill(r.path, { id: asgId });
    const body = noBody(r.method) ? null : { deviceId: 'x', events: [{ eventId: `e-${uniq()}`, assignmentId: asgId, type: 'accept', deviceTime: Date.now(), deviceSeq: 1 }] };
    for (const [who, opts] of [['بی‌توکن', {}], ['صاحبِ دکان', { token: a.token }], ['مدیر', { token: admin }], ['مشتری', as(k)], ...staff]) {
      const res = await call(r.method, url, { body, ...opts });
      if (res.status < 400 || res.status >= 500) bad.push(`${r.key} با ${who} ⇒ ${res.status}`);
      if (leaks(res, secrets).length) bad.push(`${r.key} با ${who} دادهٔ مأموریت را داد`);
    }
    for (const [who, c] of [['پیکِ دو', c2], ['پیکِ دکانِ ب', cb]]) {
      const res = await call(r.method, url, { body, token: c.token });
      const applied = (res.body?.results || []).some((x) => x.status === 'applied' || x.status === 'duplicate');
      if (res.status >= 500 || applied || (r.method === 'POST' && r.path.includes(':id') && res.status < 400)) bad.push(`${r.key} با ${who} ⇒ ${res.status} ${JSON.stringify(res.body?.results || '')}`);
      if (leaks(res, secrets).length) bad.push(`${r.key} با ${who} دادهٔ مأموریتِ پیکِ یک را داد`);
    }
  }
  assert.deepEqual(bad, [], bad.join('\n'));
  const still = await h.get('/api/courier/missions', { token: c1.token });
  assert.ok(JSON.stringify(still.body).includes(asgId), 'مأموریت هنوز مالِ پیکِ یک است');
});

test('E2E-SEC-13 (مشتری): درهای نشست‌دار فقط با نشستِ همان مشتری؛ شناسه و توکنِ دیگری دسترسی نمی‌دهد', async () => {
  const a = await liveShop('مشتری-الف');
  const b = await liveShop('مشتری-ب');
  const c = await courier(a, 'پیکِ مشتری');
  const ka = await customer(a.slug, '0791230004');
  const kb = await customer(a.slug, '0791230005');
  const kc = await customer(b.slug, '0791230006');
  const staff = await members(a);
  const addr = await h.post(`/api/store/${a.slug}/account/addresses`, { label: 'خانه', text: 'کوچهٔ رازِ-مشتری' }, as(ka));
  assert.equal(addr.status, 201, JSON.stringify(addr.body));
  //  CHK-03: سبدِ سروریِ الف با یادداشتِ راز — درِ سبد هم در همین ماتریس است
  const cart = await h.put(`/api/store/${a.slug}/account/cart`, { version: 0, items: [{ productId: 'oil', qtyMilli: 1000, note: 'رازِ-سبدِ-مشتری' }] }, as(ka));
  assert.equal(cart.status, 200, JSON.stringify(cart.body));
  const ck = await h.post(`/api/store/${a.slug}/checkout`, {
    fulfillment: 'pickup', customer: { name: 'مشتریِ الف', phone: ka.phone }, lines: [{ productId: 'rice', qtyMilli: 1000 }],
  }, { headers: { 'Idempotency-Key': `ck-${uniq()}-key`, 'X-Store-Session': ka.session } });
  assert.equal(ck.status, 201, JSON.stringify(ck.body));
  const token = ck.body.trackToken || ck.body.token || ck.body.order?.token;
  assert.ok(token, JSON.stringify(ck.body));
  const conv = await h.post(`/api/store/${a.slug}/chat/conversations`, { name: 'مشتریِ الف' }, { headers: AUTH.verification_required(ka) });
  assert.ok(conv.status < 300, JSON.stringify(conv.body));
  const msg = await h.post(`/api/store/${a.slug}/chat/conversations/${conv.body.conversation.id}/messages`, { body: 'رازِ-چتِ-مشتری', clientId: `m-${uniq()}` }, { headers: AUTH.verification_required(ka) });
  assert.ok(msg.status < 300, JSON.stringify(msg.body));
  const idsOf = (p) => ({ slug: a.slug, id: p.includes('/chat/') ? conv.body.conversation.id : addr.body.address.id, token });
  const secrets = [addr.body.address.id, 'رازِ-مشتری', 'رازِ-چتِ-مشتری', 'رازِ-سبدِ-مشتری', '791230004', token, ck.body.order?.id, conv.body.conversation.id];

  const routes = live().filter((r) => r.path.startsWith('/api/store/'));
  const gated = [];
  for (const r of routes) {
    const res = await call(r.method, fill(r.path, idsOf(r.path)), { body: noBody(r.method) ? null : {} });
    if (res.status === 401 && AUTH[res.body?.error?.code]) gated.push({ ...r, cred: AUTH[res.body.error.code] });
  }
  assert.ok(gated.length >= 20, `درهای مشتری: ${gated.map((r) => r.key).join(', ')}`);
  assert.ok(gated.some((r) => r.path.includes('/chat/')) && gated.some((r) => r.path.includes('/account')), 'هم چت هم حساب');
  const bad = [];
  for (const r of gated) {
    if (/logout|DELETE \/api\/store\/:slug\/account$/.test(r.key)) continue;   // نشستِ فرستنده را می‌بندند
    const url = fill(r.path, idsOf(r.path));
    const body = noBody(r.method) ? null : { label: 'دزدی', text: 'x' };
    for (const [who, opts] of [['صاحبِ دکان', { token: a.token }], ['پیک', { token: c.token }], ['مدیر', { token: admin }], ...staff]) {
      const res = await call(r.method, url, { body, ...opts });
      if (res.status !== 401) bad.push(`${r.key} با ${who} ⇒ ${res.status}`);
    }
    for (const [who, k] of [['مشتریِ دیگر', kb], ['مشتریِ ویترینِ ب', kc]]) {
      const res = await call(r.method, url, { body, headers: r.cred(k) });
      if (res.status >= 500) bad.push(`${r.key} با ${who} ⇒ ${res.status}`);
      if (leaks(res, secrets).length) bad.push(`${r.key} با ${who} دادهٔ مشتریِ الف را داد: ${leaks(res, secrets).join('،')}`);
    }
  }
  assert.deepEqual(bad, [], bad.join('\n'));
  //  شناسهٔ واقعیِ الف در دستِ ب: نه می‌بیند، نه عوض می‌کند
  assert.deepEqual((await h.get(`/api/store/${a.slug}/account/addresses`, as(ka))).body.addresses.map((x) => x.text), ['کوچهٔ رازِ-مشتری']);
  //  CHK-10: «خبرم کن» با برگهٔ شماره در همین ماتریس است
  assert.ok(gated.some((r) => r.key === 'POST /api/store/:slug/area-request'), 'درِ «خبرم کن» در ماتریس است');
  assert.ok(gated.some((r) => r.key === 'PUT /api/store/:slug/account/cart'), 'درِ سبد در ماتریس است');
  assert.deepEqual((await h.get(`/api/store/${a.slug}/account/cart`, as(ka))).body.cart.lines.map((l) => l.note), ['رازِ-سبدِ-مشتری']);
  //  توکنِ پیگیری: دست‌کاری‌شده یا روی ویترینِ دیگر ⇒ ۴۰۴؛ خودش ⇒ ۲۰۰
  assert.equal((await h.get(`/api/store/${a.slug}/orders/${token}`)).status, 200);
  const flip = token.slice(0, -1) + (token.endsWith('A') ? 'B' : 'A');
  assert.equal((await h.get(`/api/store/${a.slug}/orders/${flip}`)).status, 404);
  assert.equal((await h.get(`/api/store/${b.slug}/orders/${token}`)).status, 404);
});
