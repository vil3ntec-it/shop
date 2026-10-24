'use strict';
/**
 * گزارشِ سوءاستفاده در پنلِ VILL3N — OPS-09 (`GET /api/admin/trust/report`).
 *
 *   ۱) فقط مدیرِ ارشد؛ بی توکن ۴۰۱، مدیرِ عادی ۴۰۳.
 *   ۲) به‌ازای هر دکان در بازه: پرونده، در جریان، واردشده (بی اعتراضِ برگشته)،
 *      ردشده، شدید، نشان‌دار و جریمهٔ فعال؛ واردشده‌تر بالا؛ بیرونِ بازه نه.
 *   ۳) ⛔ نه متنِ شکایت، نه نشانهٔ شاکی؛ دیدنِ گزارش سابقه دارد.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');

const DAY = 24 * 60 * 60 * 1000;
let root = '';
let clerk = '';
const shops = {};
const get = (token, q = '') => h.get(`/api/admin/trust/report${q}`, { token });
const caseId = () => 'tc_' + crypto.randomBytes(12).toString('hex');

async function makeShop(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return made.body.shop.id;
}

async function addCase(shopId, { kind = 'complaint', severity = 'low', status = 'open', outcome = '', appeal = '', flags = [], age = DAY, category = 'rude' } = {}) {
  const id = caseId();
  const subject = kind === 'report' ? 'product' : 'shop';
  await query(
    `INSERT INTO trust_cases (id, shop_id, kind, subject, product_id, category, severity, body, status, outcome, appeal_outcome,
        reporter_key, reporter_hint, flags, created_at, updated_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7,'متنِ محرمانهٔ شکایت',$8,$9,$10,'rk-secret','0791***567',$11::jsonb,$12,$12)`,
    [id, shopId, kind, subject, kind === 'report' ? 'prod-1' : '', category, severity, status, outcome, appeal,
      JSON.stringify(flags), now() - age]
  );
  return id;
}

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'root','ارشد',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Root!12345'), now()]
  );
  root = (await h.post('/api/admin/login', { username: 'root', password: 'Root!12345' })).body.token;
  await h.post('/api/admin/admins', { username: 'clerk', password: 'Clerk!12345', role: 'admin' }, { token: root });
  clerk = (await h.post('/api/admin/login', { username: 'clerk', password: 'Clerk!12345' })).body.token;

  shops.bad = await makeShop('پرشکایت');
  shops.calm = await makeShop('آرام');
  //  پرشکایت: ۲ واردشده (یکی شدید)، ۱ ردشده، ۱ واردشده که با اعتراض برگشت (⇒ ردشده)، ۱ باز و نشان‌دار
  const up = await addCase(shops.bad, { severity: 'high', status: 'decided', outcome: 'upheld', category: 'fraud' });
  await addCase(shops.bad, { kind: 'report', status: 'decided', outcome: 'upheld', category: 'counterfeit' });
  await addCase(shops.bad, { status: 'closed', outcome: 'dismissed' });
  await addCase(shops.bad, { status: 'closed', outcome: 'upheld', appeal: 'reversed' });
  await addCase(shops.bad, { status: 'open', flags: ['same_text'] });
  await addCase(shops.bad, { status: 'decided', outcome: 'upheld', age: 60 * DAY });   // بیرونِ ۳۰ روز
  await query(
    `INSERT INTO trust_sanctions (id, shop_id, case_id, kind, product_id, reason, created_by, created_at)
     VALUES ($1,$2,$3,'hide_listing','prod-1','کالای تقلبی','adm',$4), ($5,$2,$3,'warning','','هشدار','adm',$4)`,
    ['tsn_' + crypto.randomBytes(12).toString('hex'), shops.bad, up, now(), 'tsn_' + crypto.randomBytes(12).toString('hex')]
  );
  await query(`UPDATE trust_sanctions SET lifted_at=$2 WHERE shop_id=$1 AND kind='warning'`, [shops.bad, now()]);
  //  آرام: یک پرونده در جریان
  await addCase(shops.calm, { status: 'in_review' });
});
test.after(async () => { await h.stop(); });

test('فقط مدیرِ ارشد', async () => {
  assert.equal((await get(null)).status, 401);
  assert.equal((await get(clerk)).status, 403);
});

test('OPS-09: گزارشِ هر دکان در بازه، واردشده‌تر بالا', async () => {
  const r = await get(root);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.headers.get('cache-control'), 'no-store');
  assert.equal(r.body.days, 30);
  assert.deepEqual(r.body.shops.map((s) => s.shopId), [shops.bad, shops.calm]);
  const bad = r.body.shops[0];
  assert.deepEqual(
    [bad.cases, bad.pending, bad.upheld, bad.dismissed, bad.high, bad.complaints, bad.reports, bad.flagged, bad.activeSanctions],
    [5, 1, 2, 2, 1, 4, 1, 1, 1]
  );
  assert.equal(bad.shopName, 'دکانِ پرشکایت');
  assert.deepEqual(r.body.totals, { cases: 6, pending: 2, upheld: 2, dismissed: 2, high: 1, flagged: 1 });
  assert.ok(r.body.categories.some((c) => c.kind === 'report' && c.category === 'counterfeit' && c.count === 1));
  //  ۹۰ روز ⇒ پروندهٔ کهنه هم
  assert.equal((await get(root, '?days=90')).body.shops[0].cases, 6);
  assert.equal((await get(root, '?days=x')).body.days, 30);
  assert.equal((await get(root, '?days=9999')).body.days, 366);
});

test('⛔ نه متنِ شکایت نه نشانهٔ شاکی؛ دیدن سابقه دارد', async () => {
  const text = JSON.stringify((await get(root)).body);
  for (const s of ['متنِ محرمانه', 'rk-secret', '0791']) assert.ok(!text.includes(s), s);
  assert.ok(await one(`SELECT 1 FROM audit_logs WHERE action='admin.trust_report_viewed'`));
});
