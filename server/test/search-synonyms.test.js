'use strict';
/**
 * ۴-۱ «ب» (POS-04، WEB-05) — فهرستِ مرکزیِ مترادف، سمتِ سرورِ حساب (توانایی ‎synonyms.v1‎ ِ پلِ پنل، ۱.۵۰.۸۹).
 *
 *   ۱) سنجش همان سنجشِ پنل: ۵۰۰ گروه، ۲ تا ۲۰ واژه، ۶۰ نویسه، یک واژه فقط در یک گروه پس از یک‌شکل کردنِ
 *      «ي/ى/ی»، «ك/ک»، نیم‌فاصله، اعراب، رقمِ فارسی/عربی و بزرگ/کوچک — هر ردِ جدا با `synonym_invalid`.
 *   ۲) درها: خواندن برای هر نقشِ مدیر (مالی نه)، نوشتن فقط ارشد با دلیل؛ بی‌توکن و توکنِ دکان ۴۰۱؛ پلِ پنل با کلیدِ سرویس
 *      و «انجام‌دهنده»ٔ ارشد می‌نویسد (انجام‌دهنده در سابقه)، انجام‌دهندهٔ کم‌رتبه نه.
 *   ۳) قفلِ نسخه (‎409 synonyms_version‎)، همان فهرست ⇒ `unchanged` بی نسخه و سابقهٔ دوم، `Idempotency-Key`، سابقهٔ
 *      `admin.synonyms_updated`؛ ردِ ورودی هیچ چیزی نمی‌نویسد.
 *   ۴) جست‌وجوی ویترین با پرچمِ خاموشِ `shop.storefront.synonyms`: نتیجه دقیقاً همان `text-core.matches(name, q, groups)`
 *      (بردارهای مشترکِ `core/test-vectors/text-normalize.json`)؛ پرچمِ خاموش ⇒ همان جست‌وجوی بی مترادف.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const syn = require('../src/lib/search-synonyms');
const sf = require('../src/lib/storefront');
const tc = require('../src/lib/text-core');

const V = JSON.parse(fs.readFileSync(path.join(__dirname, '..', '..', 'core', 'test-vectors', 'text-normalize.json'), 'utf8'));
const PASS = 'Admin!12345';
const ids = {};
const toks = {};
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;
const R = 'فهرستِ نخستِ صاحب‌کار';

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  const hash = await pw.hashPassword(PASS);
  for (const [u, role] of [['syn-arshad', 'superadmin'], ['syn-modir', 'admin'], ['syn-mali', 'finance'], ['syn-binande', 'viewer']]) {
    ids[u] = newId('adm');
    await query(`INSERT INTO admins (id, username, name, password_hash, role, status, created_at) VALUES ($1,$2,$2,$3,$4,'active',$5)`,
      [ids[u], u, hash, role, now()]);
  }
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
const A = async (who, method, body = null, headers = {}) =>
  h.api(method, '/api/admin/search/synonyms', { token: await tok(who), body, headers });
const audits = () => many(`SELECT * FROM audit_logs WHERE action='admin.synonyms_updated' ORDER BY created_at, id`);
const stored = async () => syn.get();

/* ── ۱) سنجش ───────────────────────────────────────────────────────── */

test('۱) کلیدِ یک‌شکل: «ي/ى»، «ك»، نیم‌فاصله، اعراب و کشیده، رقمِ فارسی/عربی و بزرگ/کوچک یکی‌اند', () => {
  const same = [
    ['سیب‌زمینی', 'سیبزمینی'], ['كيك', 'کیک'], ['مُحَمَّد', 'محمد'], ['شـــکر', 'شکر'], ['۱۲۳', '123'], ['١٢٣', '123'],
    ['Tomato', 'tOMATO'], ['  بوره   سفید ', 'بوره سفید'], ['گوجهٔ', 'گوجه'], ['آب', 'اب'], ['ة', 'ه'],
  ];
  for (const [a, b] of same) assert.equal(syn.synonymKey(a), syn.synonymKey(b), `${a} ≡ ${b}`);
  assert.notEqual(syn.synonymKey('بوره سفید'), syn.synonymKey('بورهسفید'), 'فاصلهٔ میانِ دو واژه همان پنل: می‌ماند');
});

test('۱) سنجشِ گروه‌ها: ۹ ردِ جدا، همه `synonym_invalid`؛ پاک‌سازیِ همان پنل', () => {
  const bad = [
    ['نافهرست', 'بوره=شکر'],
    ['بیش از ۵۰۰ گروه', Array.from({ length: 501 }, (_, i) => [`الف${i}`, `ب${i}`])],
    ['گروهِ نافهرست', [['بوره', 'شکر'], 'چای']],
    ['بیش از ۲۰ واژه', [Array.from({ length: 21 }, (_, i) => `واژه${i}`)]],
    ['واژهٔ نامتن', [['بوره', 7]]],
    ['واژهٔ ۶۱ نویسه', [['بوره', 'ش'.repeat(61)]]],
    ['یک واژه', [['بوره']]],
    ['دو شکلِ یک واژه در یک گروه = یک واژه', [['كيك', 'کیک']]],
    ['یک واژه در دو گروه پس از یک‌شکل کردن', [['بوره', 'شکر'], ['چای', 'شكر']]],
  ];
  for (const [what, groups] of bad) {
    assert.throws(() => syn.validate(groups), (e) => e.code === 'synonym_invalid' && e.status === 400, what);
  }
  for (const [a, b] of [['سیب‌زمینی', 'سیبزمینی'], ['Tomato', 'tomato'], ['۱۰ کیلو', '10 کیلو'], ['مُحَمَّد', 'محمد']]) {
    assert.throws(() => syn.validate([['x1', a], ['y1', b]]), (e) => e.code === 'synonym_invalid', `${a} / ${b}`);
  }
  //  پاک: نویسهٔ کنترلی و فاصلهٔ اضافه بیرون، تکرارِ همان گروه یک بار، ترتیب همان
  assert.deepEqual(syn.validate([['  بوره\u0007 ', 'شکر', 'شكر', ''], ['کچالو', 'سیب   زمینی']]),
    [['بوره', 'شکر'], ['کچالو', 'سیب زمینی']]);
  assert.equal(syn.validate(Array.from({ length: 500 }, (_, i) => [`الف${i}`, `ب${i}`])).length, 500, 'سقفِ دقیقِ ۵۰۰');
  assert.equal(syn.validate([Array.from({ length: 20 }, (_, i) => `واژه${i}`)])[0].length, 20, 'سقفِ دقیقِ ۲۰');
  assert.equal(syn.validate([['بوره', 'ش'.repeat(60)]])[0][1].length, 60, 'سقفِ دقیقِ ۶۰');
});

/* ── ۲ و ۳) درها ───────────────────────────────────────────────────── */

test('۲) پیش از هر نوشتن: سندِ خالی؛ هر مدیر جز مالی می‌خواند؛ بی‌توکن و توکنِ دکان ۴۰۱', async () => {
  const u = await h.newUser('صاحبِ مترادف');
  assert.equal((await h.get('/api/admin/search/synonyms')).status, 401);
  assert.equal((await h.get('/api/admin/search/synonyms', { token: u.accessToken })).status, 401);
  for (const who of ['syn-arshad', 'syn-modir', 'syn-binande']) {
    const r = await A(who, 'GET');
    assert.equal(r.status, 200, `${who} ⇒ ${r.status}`);
    assert.deepEqual(r.body, { version: 0, updatedAt: 0, groups: [] });
  }
  assert.equal((await A('syn-mali', 'GET')).status, 403, 'نقشِ مالی به این در نمی‌رسد');
});

test('۲) نوشتن فقط ارشد با دلیل؛ ردِ نقش، دلیل یا ورودی هیچ چیزی نمی‌نویسد', async () => {
  const u = await h.newUser('صاحبِ مترادفِ دوم');
  const body = { version: 0, groups: [['بوره', 'شکر']], reason: R };
  assert.equal((await h.put('/api/admin/search/synonyms', body)).status, 401);
  assert.equal((await h.put('/api/admin/search/synonyms', body, { token: u.accessToken })).status, 401);
  for (const who of ['syn-modir', 'syn-mali', 'syn-binande']) assert.equal((await A(who, 'PUT', body)).status, 403, who);
  const noReason = await A('syn-arshad', 'PUT', { version: 0, groups: [['بوره', 'شکر']] });
  assert.equal(noReason.status, 400);
  assert.equal(noReason.body.error.code, 'reason_required');
  const dup = await A('syn-arshad', 'PUT', { version: 0, groups: [['بوره', 'شکر'], ['قند', 'شكر']], reason: R });
  assert.equal(dup.status, 400);
  assert.equal(dup.body.error.code, 'synonym_invalid');
  const noVersion = await A('syn-arshad', 'PUT', { groups: [['بوره', 'شکر']], reason: R });
  assert.equal(noVersion.status, 400);
  assert.equal(noVersion.body.error.code, 'bad_version');
  assert.equal((await stored()).version, 0, 'هیچ نوشتنی نشست');
  assert.equal((await audits()).length, 0);
});

test('۳) ارشد می‌نویسد؛ نسخهٔ کهنه ۴۰۹؛ همان فهرست unchanged؛ سابقه یک بار؛ کلیدِ یکتایی', async () => {
  const groups = [['بوره', 'شکر'], ['کچالو', 'سیب‌زمینی', 'Potato']];
  const w = await A('syn-arshad', 'PUT', { version: 0, groups, reason: R });
  assert.equal(w.status, 200, JSON.stringify(w.body));
  assert.equal(w.body.version, 1);
  assert.equal(w.body.unchanged, false);
  assert.deepEqual(w.body.groups, groups);
  assert.ok(w.body.updatedAt > 0);
  assert.deepEqual(Object.keys(w.body).sort(), ['groups', 'unchanged', 'updatedAt', 'version']);
  const r = await A('syn-binande', 'GET');
  assert.deepEqual(r.body, { version: 1, updatedAt: w.body.updatedAt, groups });

  //  پاسخِ گم‌شده و تکرار با همان نسخهٔ کهنه ⇒ unchanged، نه ۴۰۹ ِ دروغ و نه نسخهٔ تازه
  const again = await A('syn-arshad', 'PUT', { version: 0, groups, reason: R });
  assert.equal(again.status, 200);
  assert.equal(again.body.unchanged, true);
  assert.equal(again.body.version, 1);
  //  فهرستِ دیگر با نسخهٔ کهنه ⇒ ۴۰۹ و فهرستِ پیشین سرِ جایش
  const stale = await A('syn-arshad', 'PUT', { version: 0, groups: [['چای', 'Tea']], reason: R });
  assert.equal(stale.status, 409);
  assert.equal(stale.body.error.code, 'synonyms_version');
  assert.deepEqual((await stored()).groups, groups);

  const rows = await audits();
  assert.equal(rows.length, 1, 'یک نوشتن ⇒ یک سابقه');
  const d = typeof rows[0].detail === 'string' ? JSON.parse(rows[0].detail) : rows[0].detail;
  assert.equal(rows[0].user_id, ids['syn-arshad']);
  assert.equal(d.reason, R);
  assert.equal(d.version, 1);
  assert.equal(d.from, 0);
  assert.equal(d.groups, 2);
  assert.equal(d.terms, 5);
  assert.equal(d.added, 5);

  //  کلیدِ یکتایی: همان کلید + همان بدنه ⇒ همان پاسخ با «بازپخش»؛ بدنهٔ دیگر ⇒ ۴۰۹
  const key = `syn-${uniq()}-key`;
  const next = { version: 1, groups: [...groups, ['چای', 'Tea']], reason: R };
  const k1 = await A('syn-arshad', 'PUT', next, { 'Idempotency-Key': key });
  assert.equal(k1.status, 200, JSON.stringify(k1.body));
  assert.equal(k1.body.version, 2);
  const k2 = await A('syn-arshad', 'PUT', next, { 'Idempotency-Key': key });
  assert.equal(k2.status, 200);
  assert.equal(k2.headers.get('idempotent-replayed'), 'true');
  assert.deepEqual(k2.body, k1.body);
  const k3 = await A('syn-arshad', 'PUT', { ...next, groups: [['قهوه', 'Coffee']] }, { 'Idempotency-Key': key });
  assert.equal(k3.status, 409);
  assert.equal(k3.body.error.code, 'idempotency_key_reused');
  assert.equal((await stored()).version, 2);
  assert.equal((await audits()).length, 2);
});

test('۲) پلِ پنل: کلیدِ سرویس با انجام‌دهندهٔ ارشد می‌نویسد و نامش در سابقه است؛ انجام‌دهندهٔ کم‌رتبه نه', async () => {
  const svc = await require('../src/lib/admin-service').create({ name: 'پلِ مترادف', roleCap: 'superadmin', createdBy: ids['syn-arshad'] });
  const cur = await stored();
  const body = { version: cur.version, groups: [...cur.groups, ['روغن', 'Oil']], reason: 'افزودنِ روغن' };
  const low = await h.api('PUT', '/api/admin/search/synonyms', {
    body, headers: { Authorization: `Bearer ${svc.key}`, 'X-Actor-Id': 'panel:9', 'X-Actor-Role': 'admin' },
  });
  assert.equal(low.status, 403, JSON.stringify(low.body));
  const read = await h.api('GET', '/api/admin/search/synonyms', {
    headers: { Authorization: `Bearer ${svc.key}`, 'X-Actor-Id': 'panel:9', 'X-Actor-Role': 'viewer' },
  });
  assert.equal(read.status, 200, 'بینندهٔ پنل می‌خواند');
  const ok = await h.api('PUT', '/api/admin/search/synonyms', {
    body, headers: { Authorization: `Bearer ${svc.key}`, 'X-Actor-Id': 'panel:3', 'X-Actor-Role': 'superadmin', 'X-Actor-Name': 'saheb' },
  });
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.equal(ok.body.version, cur.version + 1);
  const last = (await audits()).at(-1);
  const d = typeof last.detail === 'string' ? JSON.parse(last.detail) : last.detail;
  assert.equal(d.actor?.id, 'panel:3', JSON.stringify(d));
  assert.equal(d.reason, 'افزودنِ روغن');
});

test('۳) دو نوشتنِ هم‌زمان با یک نسخه: یکی می‌نشیند، دیگری ۴۰۹ — هیچ فهرستی بی‌صدا رونویسی نمی‌شود', async () => {
  const cur = await stored();
  const n0 = (await audits()).length;
  const [x, y] = await Promise.all([
    A('syn-arshad', 'PUT', { version: cur.version, groups: [['نمک', 'Salt']], reason: 'نوشتنِ هم‌زمانِ یک' }),
    A('syn-arshad', 'PUT', { version: cur.version, groups: [['برنج', 'Rice']], reason: 'نوشتنِ هم‌زمانِ دو' }),
  ]);
  assert.deepEqual([x.status, y.status].sort(), [200, 409], `${x.status} ${y.status}`);
  const won = x.status === 200 ? x : y;
  assert.deepEqual((await stored()).groups, won.body.groups);
  assert.equal((await stored()).version, cur.version + 1);
  assert.equal((await audits()).length, n0 + 1);
});

/* ── ۴) جست‌وجوی ویترین ───────────────────────────────────────────── */

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: await tok('syn-arshad') });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

async function storeWith(names) {
  const u = await h.newUser(`ویترینِ مترادف ${uniq()}`, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${uniq()}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const shopId = made.body.shop.id;
  await setFlag(shopId, 'shop.orders', true);
  await setFlag(shopId, 'shop.storefront', true);
  const slug = `syn-${uniq()}`;
  const put = await h.put('/api/shop/storefront', { slug, enabled: true, title: 'ویترینِ مترادف', contactPhone: '0700000001' },
    { token: u.accessToken });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const pids = names.map((_, i) => `p${i}`);
  const p = await h.api('POST', '/api/sync/v1/push', {
    token: u.accessToken, headers: { 'X-App': 'shop', 'X-Device': 'dev-syn' },
    body: {
      device_id: 'dev-syn', schema_version: 2,
      ops: names.map((name, i) => ({ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: pids[i], type: 'insert',
        fields: { name, unit: 'دانه', salePrice: 10 + i } })),
    },
  });
  assert.equal(p.status, 200, JSON.stringify(p.body));
  for (const id of pids) {
    const r = await h.put(`/api/shop/storefront/products/${id}`, { published: true }, { token: u.accessToken });
    assert.equal(r.status, 200, JSON.stringify(r.body));
  }
  return { shopId, slug, byId: Object.fromEntries(pids.map((id, i) => [id, names[i]])) };
}

async function search(s, q) {
  resetAll();
  const r = await h.get(`/api/store/${s.slug}/products?q=${encodeURIComponent(q)}&limit=100`);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body.items.map((p) => s.byId[p.id]).sort();
}

test('۴) ویترین با پرچمِ shop.storefront.synonyms: نتیجه دقیقاً همان text-core.matches با همان گروه‌ها؛ خاموش ⇒ بی مترادف', async () => {
  const vecs = V.matches.filter((v) => v.synonyms);
  assert.ok(vecs.length >= 4, 'بردارِ مترادف کم است');
  const groups = [['کچالو', 'سیب‌زمینی'], ['بادنجان رومی', 'گوجه‌فرنگی', 'Tomato'], ['بوره', 'شکر']];
  for (const v of vecs) for (const g of v.synonyms) assert.ok(groups.some((x) => JSON.stringify(x) === JSON.stringify(g)), 'گروهِ بردار در فهرست هست');
  const cur = await stored();
  const w = await A('syn-arshad', 'PUT', { version: cur.version, groups, reason: 'فهرستِ آزمونِ جست‌وجو' });
  assert.equal(w.status, 200, JSON.stringify(w.body));

  const extra = ['شکر سفید', 'بوره ۵ کیلو', 'رب گوجه‌فرنگی ۸۰۰ گرم', 'چای سبز', 'سیب زمینی هلندی'];
  const names = [...new Set([...vecs.map((v) => v.text), ...extra])];
  const s = await storeWith(names);
  await setFlag(s.shopId, sf.SEARCH_FLAG, true);
  const queries = [...new Set([...vecs.map((v) => v.query), 'بوره', 'شکر', 'tomato', 'بادنجان رومی ۸۰۰', 'سیب‌زمینی تازه', 'بادنجان', 'چای'])];

  //  خاموش: همان جست‌وجوی بی مترادف
  for (const q of queries) assert.deepEqual(await search(s, q), names.filter((x) => tc.matches(x, q)).sort(), `خاموش «${q}»`);
  assert.deepEqual(await search(s, 'بوره'), ['بوره ۵ کیلو']);

  await setFlag(s.shopId, syn.FLAG, true);
  for (const v of vecs) assert.equal((await search(s, v.query)).includes(v.text), v.out, `«${v.text}» با «${v.query}»`);
  for (const q of queries) assert.deepEqual(await search(s, q), names.filter((x) => tc.matches(x, q, groups)).sort(), `روشن «${q}»`);
  assert.deepEqual(await search(s, 'بوره'), ['بوره ۵ کیلو', 'شکر سفید'].sort(), '«بوره» = «شکر»');
  assert.deepEqual(await search(s, 'بادنجان رومی'), names.filter((x) => tc.matches(x, 'بادنجان رومی', groups)).sort());
  assert.ok((await search(s, 'بادنجان رومی')).includes('Tomato paste'), 'کلِ پرس‌وجو یک واژهٔ گروه ⇒ هم‌معنای کامل');

  //  فهرستِ خالی ⇒ همان جست‌وجوی بی مترادف، حتی با پرچمِ روشن
  const now2 = await stored();
  assert.equal((await A('syn-arshad', 'PUT', { version: now2.version, groups: [], reason: 'خالی کردن' })).status, 200);
  assert.deepEqual(await search(s, 'بوره'), ['بوره ۵ کیلو']);
});

test('۵) POS-04: درِ همگانیِ صندوق `GET /api/search/synonyms` همان سندِ پنل است، بی‌توکن؛ ETag ⇒ ۳۰۴؛ فقط خواندن', async () => {
  const cur = (await A('syn-binande', 'GET')).body;
  const set = await A('syn-arshad', 'PUT', { version: cur.version, groups: [['ماست', 'Yogurt']], reason: R });
  assert.equal(set.status, 200, JSON.stringify(set.body));
  const pub = await h.get('/api/search/synonyms');
  assert.equal(pub.status, 200, JSON.stringify(pub.body));
  const admin = await A('syn-binande', 'GET');
  assert.deepEqual(pub.body, admin.body, 'همان نسخه، همان گروه‌ها');
  assert.deepEqual(pub.body.groups, [['ماست', 'Yogurt']]);
  const etag = pub.headers.get('etag');
  assert.equal(etag, `W/"syn-${pub.body.version}"`);
  const again = await h.api('GET', '/api/search/synonyms', { headers: { 'If-None-Match': etag } });
  assert.equal(again.status, 304);
  //  نوشتنِ تازه ⇒ نسخهٔ تازه و ETag ِ تازه (برنامه نسخهٔ کهنه را نگه نمی‌دارد)
  const w = await A('syn-arshad', 'PUT', { version: pub.body.version, groups: [...pub.body.groups, ['گوشت', 'Meat']], reason: R });
  assert.equal(w.status, 200, JSON.stringify(w.body));
  const fresh = await h.api('GET', '/api/search/synonyms', { headers: { 'If-None-Match': etag } });
  assert.equal(fresh.status, 200);
  assert.equal(fresh.body.version, w.body.version);
  //  فقط خواندن: هیچ نوشتنی از این مسیر
  for (const m of ['PUT', 'POST', 'DELETE']) {
    const r = await h.api(m, '/api/search/synonyms', { body: { version: w.body.version, groups: [] } });
    assert.ok([401, 404, 405].includes(r.status), `${m} ⇒ ${r.status}`);
  }
  assert.equal((await A('syn-binande', 'GET')).body.version, w.body.version, 'هیچ چیز عوض نشد');
});
