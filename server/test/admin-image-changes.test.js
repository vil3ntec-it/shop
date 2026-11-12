'use strict';
/**
 * IMG-PRO-17 در پنلِ مرکزی — `routes/admin-image-changes.js` (توانایی ‎image-changes.v1‎): دسته‌های «تغییرِ دسته‌ایِ عکس» ِ یک
 * دکان را پشتیبانی می‌بیند و با اجازهٔ زندهٔ `restore` برمی‌گرداند. همان `lib/image-changes.js` ِ درِ دکان.
 *
 *   ۱) نقش: مالی ۴۰۳ (پیش‌فرض‌بسته)، بیننده فقط می‌خواند، مدیرِ عادی برنمی‌گرداند — هیچ‌کدام چیزی نمی‌نویسند.
 *   ۲) مرزِ دکان: دکانِ ناموجود ‎404 shop_not_found‎؛ دستهٔ دکانِ دیگر ‎404 change_not_found‎ (حتی با اجازهٔ آن دکان).
 *   ۳) بانکِ خاموش: فهرست `enabled: false` و خالی، جزئیات و برگرداندن ‎403 image_bank_disabled‎.
 *   ۴) جزئیات: پرچمِ `admin.support_grant` روشن ⇒ ‎403 grant_required‎ تا اجازهٔ `read_private` ِ همین آدم روی همین دکان.
 *   ۵) برگرداندن: دلیل؛ همیشه اجازهٔ `restore` (دامنهٔ دیگر/دکانِ دیگر/آدمِ دیگر/کارِ خودکارِ پنل نه)؛ ‎409 changed_since‎
 *      بی نوشتن؛ سابقه با نوعِ «مدیر» و شناسهٔ اجازه در همان تراکنش؛ `Idempotency-Key` یک بار (بازپخش اجازهٔ دوم نمی‌خورد).
 *   ۶) سقفِ نرخ: نوشتنِ هر مدیر ۳۰ در ده دقیقه؛ IP پیش از احراز ۶۰ در دقیقه.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const F = require('./image-fixtures');
const { query, one, many, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const access = require('../src/lib/support-access');

const PASS = 'Admin!12345';
const ids = {};
const toks = {};
let svc;
let A; let B; let Z;
let y1 = ''; let y2 = '';
let b1 = ''; let b2 = '';
let seed = 7300;
let seq = 0;
const key = () => `aic-${Date.now()}-${++seq}`;

test.before(async () => {
  await h.start();
  const hash = await require('../src/lib/password').hashPassword(PASS);
  let i = 0;
  for (const [u, role] of [['aic-arshad', 'superadmin'], ['aic-modir', 'admin'], ['aic-mali', 'finance'], ['aic-binande', 'viewer']]) {
    ids[u] = newId('adm');
    await query(`INSERT INTO admins (id, username, name, password_hash, role, status, created_at) VALUES ($1,$2,$2,$3,$4,'active',$5)`,
      [ids[u], u, hash, role, now() + (++i)]);
  }
  svc = await require('../src/lib/admin-service').create({ name: 'پلِ عکس', roleCap: 'superadmin', createdBy: ids['aic-arshad'] });
  A = await shop('الف');
  B = await shop('ب');
  Z = await shop('خاموش');
});
test.after(async () => { await h.stop(); });
test.beforeEach(() => resetAll());

async function tok(u) {
  if (!toks[u]) {
    const r = await h.post('/api/admin/login', { username: u, password: PASS });
    assert.equal(r.status, 200, JSON.stringify(r.body));
    toks[u] = r.body.token;
  }
  return toks[u];
}
async function shop(label) {
  const owner = await h.newUser(`صاحبِ ${label}`);
  const made = await h.post('/api/shop', { name: `دکانِ عکسِ ${label}` }, { token: owner.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { owner, token: owner.accessToken, shopId: made.body.shop.id };
}
const base = (s) => `/api/admin/shops/${s.shopId}/images/changes`;
const M = async (who, method, path, body = null, headers = {}) => h.api(method, path, { token: await tok(who), body, headers });
const S = (actor, method, path, body = null, headers = {}) => h.api(method, path, {
  body, headers: { Authorization: `Bearer ${svc.key}`, 'X-Actor-Id': actor, 'X-Actor-Role': 'superadmin', ...headers },
});
const C = (s, method, path, body) => h.api(method, '/api/shop/images/changes' + path, { token: s.token, body });
const shown = async (s, pid) => {
  const r = (await h.api('GET', `/api/shop/images/products/${pid}`, { token: s.token })).body;
  return { source: r.source, image_id: r.image_id || '' };
};
const changeRow = (id) => one('SELECT status, rolled_back_by, rollback_reason FROM shop_image_changes WHERE id=$1', [id]);
const selRows = (s) => many('SELECT product_id, source, image_id, updated_by FROM shop_product_images WHERE shop_id=$1 ORDER BY product_id', [s.shopId]);
const audits = (s, action) => many('SELECT actor_type, user_id, target_id, detail FROM audit_logs WHERE shop_id=$1 AND action=$2 ORDER BY created_at, target_id, id', [s.shopId, action]);
const detailOf = (r) => (typeof r.detail === 'string' ? JSON.parse(r.detail) : r.detail);
const uses = async (gid) => Number((await one('SELECT uses FROM admin_access_grants WHERE id=$1', [gid])).uses);
const REASON = 'دستهٔ اشتباهِ عکس‌ها را برمی‌گردانیم';

/** اجازهٔ پشتیبانی با رضایتِ دکان: درخواستِ مدیر (یا پل با «انجام‌دهنده») ⇐ پذیرفتنِ صاحبِ همان دکان */
async function grant(s, scope, via) {
  const body = { scope, minutes: 30, reason: 'بررسیِ دسته‌های عکسِ این دکان', consent: 'tenant' };
  const r = via.actor
    ? await S(via.actor, 'POST', `/api/admin/shops/${s.shopId}/access-grants`, body)
    : await M(via.admin, 'POST', `/api/admin/shops/${s.shopId}/access-grants`, body);
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const ok = await h.api('POST', `/api/shop/support-access/${r.body.grant.id}/accept`, { token: s.owner.accessToken, body: {} });
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  return r.body.grant.id;
}
const setLive = async (scope, k, value) => {
  await query('DELETE FROM live_config WHERE scope=$1 AND key=$2', [scope, k]);
  if (value !== null) await query('INSERT INTO live_config (scope, key, value, updated_at) VALUES ($1,$2,$3,$4)', [scope, k, value, now()]);
};

test('آماده‌سازی: بانکِ «الف» و «ب» روشن، «خاموش» خاموش؛ دو دستهٔ «الف» (دومی p1 را دوباره عوض می‌کند)', async () => {
  await setLive('all', 'shop.image_bank', 'true');
  await setLive(Z.shopId, 'shop.image_bank', 'false');
  const up = async () => {
    const r = await h.raw('POST', '/api/shop/images/?product_id=p1', F.jpeg(800, 600, { seed: ++seed }), { token: A.token });
    assert.equal(r.status, 201, JSON.stringify(r.body));
    return r.body.id;
  };
  y1 = await up(); y2 = await up();
  assert.equal((await h.api('POST', '/api/shop/images/select', { token: A.token, body: { product_id: 'p1', source: 'shop', image_id: y1 } })).status, 200);
  const one1 = await C(A, 'POST', '', { key: 'b1', note: 'قفسهٔ چای', items: [
    { product_id: 'p1', source: 'shop', image_id: y2 }, { product_id: 'p2', source: 'shop', image_id: y2 }, { product_id: 'p3', source: 'none' }] });
  assert.equal(one1.status, 201, JSON.stringify(one1.body));
  b1 = one1.body.change.id;
  const two = await C(A, 'POST', '', { key: 'b2', items: [{ product_id: 'p1', source: 'shop', image_id: y1 }] });
  assert.equal(two.status, 201, JSON.stringify(two.body));
  b2 = two.body.change.id;
  assert.deepEqual(await shown(A, 'p1'), { source: 'shop', image_id: y1 });
});

test('نقش: مالی ۴۰۳ همه؛ بیننده فقط می‌خواند؛ مدیرِ عادی برنمی‌گرداند — و هیچ‌کدام چیزی نمی‌نویسد', async () => {
  const before = { sel: await selRows(A), ch: await changeRow(b2) };
  for (const [m, p] of [['GET', base(A)], ['GET', `${base(A)}/${b2}`], ['POST', `${base(A)}/${b2}/rollback`]]) {
    const r = await M('aic-mali', m, p, m === 'POST' ? { reason: REASON } : null);
    assert.equal(r.status, 403, `${m} ${p} ⇒ ${r.status}`);
    assert.equal(r.body.error.code, 'role_forbidden');
  }
  const list = await M('aic-binande', 'GET', base(A));
  assert.equal(list.status, 200, JSON.stringify(list.body));
  assert.equal(list.body.shopId, A.shopId);
  assert.equal(list.body.enabled, true);
  assert.deepEqual(list.body.changes.map((c) => c.id), [b2, b1], 'تازه‌ترین بالا');
  assert.equal(list.body.changes[1].note, 'قفسهٔ چای');
  assert.equal(list.body.changes[1].item_count, 3);
  assert.equal(list.body.changes[0].items, undefined, 'فهرست بی پیش و پس');
  assert.equal(list.headers.get('cache-control'), 'no-store');
  const d = await M('aic-binande', 'GET', `${base(A)}/${b1}`);
  assert.equal(d.status, 200, JSON.stringify(d.body));
  assert.deepEqual(d.body.change.items.map((x) => x.product_id), ['p1', 'p2', 'p3']);
  assert.equal(d.body.change.items[0].before.image_id, y1);
  assert.equal(d.body.change.items[1].before, null);
  assert.equal(d.body.change.items[0].after.image_id, y2);
  //  TEN-04: دیدنِ فهرست و جزئیات هر دو سابقهٔ «مدیر» دارند، با شناسهٔ موضوع (دکان / دسته)
  const seenList = await audits(A, 'admin.image_changes_viewed');
  assert.ok(seenList.length >= 1 && seenList.every((r) => r.actor_type === 'admin' && r.target_id === A.shopId), JSON.stringify(seenList));
  const seenOne = await audits(A, 'admin.image_change_viewed');
  assert.ok(seenOne.some((r) => r.actor_type === 'admin' && r.target_id === b1), JSON.stringify(seenOne));
  for (const [who, code] of [['aic-binande', 'role_forbidden'], ['aic-modir', 'forbidden']]) {
    const r = await M(who, 'POST', `${base(A)}/${b2}/rollback`, { reason: REASON });
    assert.equal(r.status, 403, `${who} ⇒ ${r.status}`);
    assert.equal(r.body.error.code, code, `${who}: فقط ارشد`);
  }
  assert.deepEqual({ sel: await selRows(A), ch: await changeRow(b2) }, before);
  assert.equal((await audits(A, 'image.changes_rolled_back')).length, 0);
});

test('مرزِ دکان: دکانِ ناموجود ۴۰۴؛ دستهٔ «الف» از نشانیِ «ب» ‎404 change_not_found‎ — حتی با اجازهٔ زندهٔ «ب»', async () => {
  const ghost = { shopId: 'shp_nope' };
  assert.equal((await M('aic-arshad', 'GET', base(ghost))).body.error.code, 'shop_not_found');
  assert.equal((await M('aic-arshad', 'GET', `${base(ghost)}/${b1}`)).body.error.code, 'shop_not_found');
  assert.equal((await M('aic-arshad', 'POST', `${base(ghost)}/${b1}/rollback`, { reason: REASON })).body.error.code, 'shop_not_found');
  const gB = await grant(B, 'restore', { admin: 'aic-arshad' });
  const x = await M('aic-arshad', 'GET', `${base(B)}/${b1}`);
  assert.equal(x.status, 404, JSON.stringify(x.body));
  assert.equal(x.body.error.code, 'change_not_found');
  const before = { sel: await selRows(A), ch: await changeRow(b2) };
  const y = await M('aic-arshad', 'POST', `${base(B)}/${b2}/rollback`, { reason: REASON });
  assert.equal(y.status, 404, JSON.stringify(y.body));
  assert.equal(y.body.error.code, 'change_not_found');
  assert.deepEqual({ sel: await selRows(A), ch: await changeRow(b2) }, before, 'هیچ کالای «الف» عوض نشد');
  assert.deepEqual((await M('aic-arshad', 'GET', base(B))).body.changes, [], 'فهرستِ «ب» دسته‌های «الف» را ندارد');
  //  ⛔ اجازهٔ «ب» درِ «الف» را باز نمی‌کند
  const z = await M('aic-arshad', 'POST', `${base(A)}/${b2}/rollback`, { reason: REASON });
  assert.equal(z.status, 403, JSON.stringify(z.body));
  assert.equal(z.body.error.code, 'grant_required');
  assert.ok(await uses(gB) >= 1);
  assert.equal((await changeRow(b2)).status, 'applied');
});

test('بانکِ خاموش: فهرست `enabled: false` و خالی؛ جزئیات و برگرداندن ‎403 image_bank_disabled‎ بی هیچ نوشتن', async () => {
  const l = await M('aic-arshad', 'GET', base(Z));
  assert.equal(l.status, 200, JSON.stringify(l.body));
  assert.deepEqual(l.body, { shopId: Z.shopId, enabled: false, changes: [] });
  assert.equal((await M('aic-arshad', 'GET', `${base(Z)}/${b1}`)).body.error.code, 'image_bank_disabled');
  const r = await M('aic-arshad', 'POST', `${base(Z)}/${b1}/rollback`, { reason: REASON });
  assert.equal(r.status, 403);
  assert.equal(r.body.error.code, 'image_bank_disabled');
  //  و خاموش شدنِ همان «الف» (همه‌جا) هم
  await setLive(A.shopId, 'shop.image_bank', 'false');
  try {
    assert.equal((await M('aic-arshad', 'GET', base(A))).body.enabled, false);
    assert.equal((await M('aic-arshad', 'POST', `${base(A)}/${b2}/rollback`, { reason: REASON })).body.error.code, 'image_bank_disabled');
    assert.equal((await changeRow(b2)).status, 'applied');
  } finally {
    await setLive(A.shopId, 'shop.image_bank', null);
  }
});

test('ورودی: limit ِ بد ‎400 bad_limit‎ (نه بریدن)؛ limit=1 یک دسته؛ شناسهٔ دستهٔ بد ۴۰۰', async () => {
  for (const bad of ['0', '51', 'x', '-1', '1.5']) {
    const r = await M('aic-arshad', 'GET', `${base(A)}?limit=${bad}`);
    assert.equal(r.status, 400, `limit=${bad} ⇒ ${r.status}`);
    assert.equal(r.body.error.code, 'bad_limit');
  }
  assert.deepEqual((await M('aic-arshad', 'GET', `${base(A)}?limit=1`)).body.changes.map((c) => c.id), [b2]);
  assert.equal((await M('aic-arshad', 'GET', `${base(A)}/${encodeURIComponent('a b')}`)).status, 400);
});

test('جزئیات با `admin.support_grant` ِ روشن: بی اجازهٔ `read_private` ‎403‎؛ با اجازهٔ همین آدم ⇒ ۲۰۰، شمار و سابقه', async () => {
  await setLive('security', access.ENFORCE_KEY, 'true');
  try {
    const no = await M('aic-arshad', 'GET', `${base(A)}/${b1}`);
    assert.equal(no.status, 403, JSON.stringify(no.body));
    assert.equal(no.body.error.code, 'grant_required');
    assert.equal((await M('aic-arshad', 'GET', base(A))).status, 200, 'فهرست اجازه نمی‌خواهد');
    const g = await grant(A, 'read_private', { admin: 'aic-arshad' });
    assert.equal((await M('aic-binande', 'GET', `${base(A)}/${b1}`)).body.error.code, 'grant_required', 'اجازهٔ آدمِ دیگر نه');
    const ok = await M('aic-arshad', 'GET', `${base(A)}/${b1}`);
    assert.equal(ok.status, 200, JSON.stringify(ok.body));
    assert.equal(ok.body.change.id, b1);
    assert.equal(await uses(g), 1);
    const used = (await audits(A, 'admin.support_access_used')).map(detailOf).filter((d) => d.grant === g);
    assert.equal(used.length, 1);
    assert.equal(used[0].scope, 'read_private');
    //  ⛔ `read_private` برگرداندن را باز نمی‌کند
    assert.equal((await M('aic-arshad', 'POST', `${base(A)}/${b2}/rollback`, { reason: REASON })).body.error.code, 'grant_required');
  } finally {
    await setLive('security', access.ENFORCE_KEY, null);
  }
  assert.equal((await M('aic-binande', 'GET', `${base(A)}/${b1}`)).status, 200, 'پرچمِ خاموش ⇒ همان امروز');
});

test('برگرداندن: دلیل پیش از اجازه؛ کارِ خودکارِ پنل و «انجام‌دهنده»ٔ دیگر نه؛ ‎409 changed_since‎ بی نوشتن', async () => {
  const before = { sel: await selRows(A), b1: await changeRow(b1), b2: await changeRow(b2) };
  const r0 = await M('aic-arshad', 'POST', `${base(A)}/${b2}/rollback`, { reason: 'x' });
  assert.equal(r0.status, 400);
  assert.equal(r0.body.error.code, 'reason_required');
  const sys = await S('panel:system', 'POST', `${base(A)}/${b2}/rollback`, { reason: REASON });
  assert.equal(sys.status, 403, JSON.stringify(sys.body));
  assert.equal(sys.body.error.code, 'human_actor_required');
  const g7 = await grant(A, 'restore', { actor: 'panel:7' });
  //  ⛔ همان «انجام‌دهنده» با نقشِ اپراتور (admin) ِ پنل، با اجازهٔ زنده هم نه — برگرداندن فقط ارشد
  const weak = await h.api('POST', `${base(A)}/${b2}/rollback`, { body: { reason: REASON },
    headers: { Authorization: `Bearer ${svc.key}`, 'X-Actor-Id': 'panel:7', 'X-Actor-Role': 'admin' } });
  assert.equal(weak.status, 403, JSON.stringify(weak.body));
  assert.equal(weak.body.error.code, 'forbidden');
  const other = await S('panel:9', 'POST', `${base(A)}/${b2}/rollback`, { reason: REASON });
  assert.equal(other.body.error.code, 'grant_required', 'اجازهٔ «انجام‌دهنده»ٔ دیگر نه');
  assert.equal((await M('aic-arshad', 'POST', `${base(A)}/${b2}/rollback`, { reason: REASON })).body.error.code, 'grant_required', 'مدیرِ پشتِ کلید هم نه');
  const early = await S('panel:7', 'POST', `${base(A)}/${b1}/rollback`, { reason: REASON }, { 'Idempotency-Key': key() });
  assert.equal(early.status, 409, JSON.stringify(early.body));
  assert.equal(early.body.error.code, 'changed_since');
  assert.deepEqual(early.body.error.products, ['p1']);
  assert.deepEqual({ sel: await selRows(A), b1: await changeRow(b1), b2: await changeRow(b2) }, before, 'هیچ چیز برنگشت');
  assert.equal((await audits(A, 'image.restored')).length, 0);
  assert.equal((await audits(A, 'image.changes_rolled_back')).length, 0);
  assert.equal(await uses(g7), 1, 'تلاشِ ۴۰۹ یک استفاده است');
});

test('برگرداندن با اجازهٔ `restore`: کالاها برمی‌گردند، سابقهٔ «مدیر» با شناسهٔ اجازه، و همان کلید یک بار', async () => {
  const g = await grant(A, 'restore', { admin: 'aic-arshad' });
  const k = key();
  const r = await M('aic-arshad', 'POST', `${base(A)}/${b2}/rollback`, { reason: REASON }, { 'Idempotency-Key': k });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.already, false);
  assert.equal(r.body.change.status, 'rolled_back');
  assert.equal(r.body.change.rolled_back_by, `admin:${ids['aic-arshad']}`);
  assert.equal(r.body.change.rollback_reason, REASON);
  assert.deepEqual(await shown(A, 'p1'), { source: 'shop', image_id: y2 }, 'p1 به وضعِ پیش از دستهٔ دوم');
  const p1 = (await selRows(A)).find((x) => x.product_id === 'p1');
  assert.equal(p1.updated_by, `admin:${ids['aic-arshad']}`);
  const restored = await audits(A, 'image.restored');
  assert.equal(restored.length, 1);
  assert.equal(restored[0].actor_type, 'admin');
  assert.equal(restored[0].user_id, ids['aic-arshad']);
  assert.equal(detailOf(restored[0]).grant, g);
  const done = await audits(A, 'image.changes_rolled_back');
  assert.equal(done.length, 1);
  assert.equal(done[0].target_id, b2);
  assert.deepEqual({ ...detailOf(done[0]) }, { items: 1, reason: REASON, grant: g });
  assert.equal(await uses(g), 1);

  //  همان کلید ⇒ همان پاسخ، بی کار و بی استفادهٔ دوم از اجازه
  const again = await M('aic-arshad', 'POST', `${base(A)}/${b2}/rollback`, { reason: REASON }, { 'Idempotency-Key': k });
  assert.equal(again.status, 200);
  assert.equal(again.headers.get('idempotent-replayed'), 'true');
  assert.deepEqual(again.body, r.body);
  assert.equal(await uses(g), 1);
  assert.equal((await audits(A, 'image.changes_rolled_back')).length, 1);
  //  همان کلید روی دستهٔ دیگر ⇒ ۴۰۹ و هیچ
  const reused = await M('aic-arshad', 'POST', `${base(A)}/${b1}/rollback`, { reason: REASON }, { 'Idempotency-Key': k });
  assert.equal(reused.status, 409, JSON.stringify(reused.body));
  assert.equal(reused.body.error.code, 'idempotency_key_reused');
  assert.equal((await changeRow(b1)).status, 'applied');
  //  بی کلید، دوباره ⇒ `already` بی نوشتن
  const twice = await M('aic-arshad', 'POST', `${base(A)}/${b2}/rollback`, { reason: REASON });
  assert.equal(twice.status, 200);
  assert.equal(twice.body.already, true);
  assert.equal((await audits(A, 'image.changes_rolled_back')).length, 1);

  //  حالا دستهٔ نخست از راهِ پل («انجام‌دهنده»ٔ panel:7 با اجازهٔ خودش) ⇒ همه به وضعِ پیش از هر دو دسته
  const first = await S('panel:7', 'POST', `${base(A)}/${b1}/rollback`, { reason: REASON }, { 'Idempotency-Key': key() });
  assert.equal(first.status, 200, JSON.stringify(first.body));
  assert.deepEqual(await shown(A, 'p1'), { source: 'shop', image_id: y1 });
  assert.deepEqual((await selRows(A)).map((x) => x.product_id), ['p1'], 'p2 و p3 پیش از دسته ردیفی نداشتند');
  const last = (await audits(A, 'image.changes_rolled_back')).find((x) => x.target_id === b1);
  assert.equal(detailOf(last).actor.id, 'panel:7', '«انجام‌دهنده» در سابقه');
  //  درِ خودِ دکان همان وضع را می‌بیند
  assert.equal((await C(A, 'GET', `/${b1}`)).body.change.status, 'rolled_back');
});

test('سقفِ نرخ: نوشتنِ هر مدیر ۳۰ در ده دقیقه؛ IP پیش از احراز ۶۰ در دقیقه', async () => {
  const ghost = `/api/admin/shops/shp_nope/images/changes/x1/rollback`;
  for (let i = 0; i < 30; i++) assert.equal((await M('aic-arshad', 'POST', ghost, { reason: REASON })).status, 404);
  const over = await M('aic-arshad', 'POST', ghost, { reason: REASON });
  assert.equal(over.status, 429, JSON.stringify(over.body));
  resetAll();
  for (let i = 0; i < 60; i++) assert.equal((await h.api('GET', base(A))).status, 401);
  assert.equal((await h.api('GET', base(A))).status, 429, 'بی‌توکن هم پیش از احراز سقف دارد');
});
