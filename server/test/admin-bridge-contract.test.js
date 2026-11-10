'use strict';
/**
 * دو درِ قراردادِ پلِ پنل (ریپوی server، `account-admin.js`) — ممیزیِ پنلِ مرکزی §۹:
 *
 *   S15 ـ `GET /api/admin/subscriptions/period-end` (`routes/admin-bridge.js`): پایانِ دوره را **سرورِ حساب** حساب می‌کند، با همان
 *         تابعی که اعطا می‌کند (`subscriptions.periodEnd` ⇒ دکان: روزِ تقویمِ کابل `plans.endOfPeriodIn`، هرگز کمتر از UTC؛ پمپ:
 *         `plans.endOfPeriod`). پنل امروز `addPeriod` ِ UTC ِ خودش را دارد و پایانی که یکمِ ماهِ کابل بود سه روز کم می‌گرفت.
 *   S18 ـ قراردادِ نسخه‌دار: سرآیندِ `X-Admin-API-Version` روی هر پاسخِ `/api/admin/*`، و `GET /api/admin/contract` (نسخه، اثرِ
 *         انگشتِ فهرست، توانایی‌ها، فهرستِ درهای مدیر) ساخته‌شده از `docs/api/routes.json` (`tools/api-contract/generate.cjs`).
 *         افزودنی است: پنلِ کهنه هیچ‌کدام را نمی‌خواند و همان کار می‌کند.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const nodePath = require('node:path');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const plans = require('../src/lib/plans');
const { resetAll } = require('../src/middleware/ratelimit');

const tok = async (u) => (await h.post('/api/admin/login', { username: u, password: 'Admin!12345' })).body.token;
const KABUL_MAR1 = Date.UTC(2027, 1, 28, 20, 30);   //  ۲۰۲۷/۰۳/۰۱ ساعتِ ۰۱:۰۰ ِ کابل = هنوز ۲۸ فوریه در UTC

test.before(async () => {
  await h.start();
  const hash = await require('../src/lib/password').hashPassword('Admin!12345');
  for (const [u, role, at] of [['bridge-malek', 'superadmin', 1], ['bridge-admin', 'admin', 2], ['bridge-mali', 'finance', 3], ['bridge-binande', 'viewer', 4]]) {
    await query(`INSERT INTO admins (id, username, name, password_hash, role, status, created_at) VALUES ($1,$2,$2,$3,$4,'active',$5)`,
      [newId('adm'), u, hash, role, at]);
  }
});
test.after(async () => { await h.stop(); });

const periodEnd = (q, token) => h.get(`/api/admin/subscriptions/period-end?${new URLSearchParams(q)}`, { token });

/* ── S15 ───────────────────────────────────────────────────────────── */

test('S15: یکمِ ماهِ کابل + یک ماه ⇒ یکمِ ماهِ بعدِ کابل (همان endOfPeriodIn)، نه ۲۹ ِ UTC ِ پنل', async () => {
  const sup = await tok('bridge-malek');
  const r = await periodEnd({ app: 'shop', amount: 1, unit: 'month', from: KABUL_MAR1 }, sup);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.base, KABUL_MAR1);
  assert.equal(r.body.endsAt, plans.endOfPeriodIn(KABUL_MAR1, 1, 'month', 'Asia/Kabul'));
  assert.equal(new Date(r.body.endsAt).toISOString(), '2027-03-31T20:30:00.000Z');
  assert.ok(r.body.endsAt > plans.endOfPeriod(KABUL_MAR1, 1, 'month'), 'بیش از حسابِ UTC ِ addPeriod ِ پنل');
  assert.equal(r.body.rule, 'calendar_local');
  assert.equal(r.body.zone, 'Asia/Kabul');
  assert.equal(r.body.app, 'shop');
  assert.ok(Number.isFinite(r.body.serverTime));
  //  هفته و روز و سال
  for (const [amount, unit] of [[2, 'week'], [10, 'day'], [1, 'year'], [6, 'month']]) {
    const x = await periodEnd({ amount, unit, from: KABUL_MAR1 }, sup);
    assert.equal(x.status, 200);
    assert.equal(x.body.endsAt, Math.max(plans.endOfPeriod(KABUL_MAR1, amount, unit), plans.endOfPeriodIn(KABUL_MAR1, amount, unit, 'Asia/Kabul')));
  }
});

test('S15: پمپ همان حسابِ اعطای پمپ (UTC، بیت‌به‌بیت همان)', async () => {
  const r = await periodEnd({ app: 'pump', amount: 1, unit: 'month', from: KABUL_MAR1 }, await tok('bridge-malek'));
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.endsAt, plans.endOfPeriod(KABUL_MAR1, 1, 'month'));
  assert.equal(r.body.rule, 'calendar_utc');
});

test('S15: همان عددی که اعطای واقعیِ پلنِ یک‌ماهه می‌نشاند (یک تابع برای پیش‌نمایش و اعطا)', async () => {
  const sup = await tok('bridge-malek');
  const u = await h.newUser('صاحبِ پایانِ دوره', 'shop');
  const shopId = (await h.post('/api/shop', { name: 'دکانِ پایانِ دوره' }, { token: u.accessToken })).body.shop.id;
  const first = await h.post('/api/admin/subscriptions', { shopId, plan: 'custom', endsAt: KABUL_MAR1 }, { token: sup });
  assert.equal(first.status, 201, JSON.stringify(first.body));
  const preview = await periodEnd({ app: 'shop', amount: 1, unit: 'month', from: Number(first.body.subscription.ends_at) }, sup);
  const grant = await h.post('/api/admin/subscriptions', { shopId, plan: 'm1' }, { token: sup });
  assert.equal(grant.status, 201, JSON.stringify(grant.body));
  assert.equal(Number(grant.body.subscription.ends_at), preview.body.endsAt);
});

test('S15: «از» گذشته یا نیامده ⇒ از همین حالا (هیچ دوره‌ای از گذشته شروع نمی‌شود)', async () => {
  const sup = await tok('bridge-malek');
  for (const q of [{ amount: 1, unit: 'week' }, { amount: 1, unit: 'week', from: 1000 }]) {
    const t0 = now();
    const r = await periodEnd(q, sup);
    assert.equal(r.status, 200, JSON.stringify(r.body));
    assert.ok(r.body.base >= t0 && r.body.base <= now(), JSON.stringify(r.body));
    assert.equal(r.body.endsAt, r.body.base + 7 * 86400e3);
  }
});

test('S15: ورودیِ بد ⇒ ۴۰۰ با کد؛ هر نقشِ مدیر می‌خواند؛ بی‌توکن ۴۰۱', async () => {
  const sup = await tok('bridge-malek');
  const bad = [
    [{ amount: 0, unit: 'month' }, 'bad_amount'], [{ amount: -1, unit: 'month' }, 'bad_amount'],
    [{ amount: '1.5', unit: 'month' }, 'bad_amount'], [{ amount: '1e1', unit: 'month' }, 'bad_amount'],
    [{ amount: '01', unit: 'month' }, 'bad_amount'], [{ amount: 121, unit: 'month' }, 'bad_amount'],
    [{ amount: 11, unit: 'year' }, 'bad_amount'], [{ amount: 3651, unit: 'day' }, 'bad_amount'], [{ unit: 'month' }, 'bad_amount'],
    [{ amount: 1, unit: 'decade' }, 'bad_unit'], [{ amount: 1 }, 'bad_unit'],
    [{ amount: 1, unit: 'month', from: 'دیروز' }, 'bad_from'], [{ amount: 1, unit: 'month', from: '1e12' }, 'bad_from'],
    [{ amount: 1, unit: 'month', from: String(4102444800001) }, 'bad_from'],
    [{ amount: 1, unit: 'month', app: 'bank' }, 'bad_app'],
  ];
  for (const [q, code] of bad) {
    const r = await periodEnd(q, sup);
    assert.equal(r.status, 400, `${JSON.stringify(q)} ⇒ ${r.status}`);
    assert.equal(r.body.error.code, code, JSON.stringify(q));
  }
  const twice = await h.get('/api/admin/subscriptions/period-end?amount=1&amount=2&unit=month', { token: sup });
  assert.equal(twice.status, 400);
  for (const u of ['bridge-admin', 'bridge-mali', 'bridge-binande']) {
    assert.equal((await periodEnd({ amount: 1, unit: 'month' }, await tok(u))).status, 200, u);
  }
  assert.equal((await periodEnd({ amount: 1, unit: 'month' }, null)).status, 401);
});

/* ── S18 ───────────────────────────────────────────────────────────── */

const CONTRACT_SRC = nodePath.join(__dirname, '..', 'src', 'lib', 'admin-contract.json');

test('S18: GET /api/admin/contract ⇒ نسخه، اثرِ انگشت، توانایی‌ها و همان درهای مدیرِ docs/api/routes.json', async () => {
  const view = await tok('bridge-binande');
  const r = await h.get('/api/admin/contract', { token: view });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.version, 1);
  assert.match(r.body.routesHash, /^[0-9a-f]{16}$/);
  assert.equal(r.body.count, r.body.routes.length);
  assert.equal(r.body.generatedFrom, 'docs/api/routes.json');
  assert.equal(r.body.serverVersion, require('../package.json').version);
  const me = await h.get('/api/admin/me', { token: view });
  assert.deepEqual(r.body.capabilities, me.body.capabilities);
  assert.ok(r.body.capabilities.includes('admin-contract.v1'));
  assert.ok(r.body.capabilities.includes('subscription-period.v1'));
  assert.ok(r.body.capabilities.includes('sync-restore-once.v1'));
  const keyOf = (x) => `${x.method} ${x.path}`;
  const keys = new Set(r.body.routes.map(keyOf));
  for (const k of ['GET /api/admin/contract', 'GET /api/admin/subscriptions/period-end', 'POST /api/admin/sync/conflicts/:id/restore',
    'GET /api/admin/me', 'POST /api/admin/subscriptions']) assert.ok(keys.has(k), k);
  assert.equal(r.body.routes.find((x) => keyOf(x) === 'POST /api/admin/sync/conflicts/:id/restore').superadmin, true);
  assert.equal(r.body.routes.find((x) => keyOf(x) === 'GET /api/admin/me').superadmin, false);
  assert.ok(r.body.routes.every((x) => x.path.startsWith('/api/admin')), 'فقط درهای مدیر');
  //  ساخته‌شده از همان docs/api/routes.json ِ مخزن (generate.cjs --check هر دو را با هم می‌سنجد)
  const routes = JSON.parse(fs.readFileSync(nodePath.join(__dirname, '..', '..', 'docs', 'api', 'routes.json'), 'utf8')).routes
    .filter((x) => x.area === 'admin' && !x.duplicateOfEarlier).map(keyOf);
  assert.deepEqual([...keys].sort(), [...new Set(routes)].sort());
  assert.deepEqual(r.body.routes, JSON.parse(fs.readFileSync(CONTRACT_SRC, 'utf8')).routes);
  //  مالی هم؛ بی‌توکن ۴۰۱
  assert.equal((await h.get('/api/admin/contract', { token: await tok('bridge-mali') })).status, 200);
  assert.equal((await h.get('/api/admin/contract', {})).status, 401);
  //  پلِ پنل با کلیدِ سرویس و هر «انجام‌دهنده» (حتی مالی و فقط‌خواندن) هر دو در را می‌خواند
  const made = await h.post('/api/admin/service-keys', { name: 'پلِ آزمون', roleCap: 'admin', reason: 'آزمونِ قرارداد' }, { token: await tok('bridge-malek') });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  for (const role of ['admin', 'finance', 'viewer']) {
    const headers = { 'X-Actor-Id': 'panel-7', 'X-Actor-Role': role, 'X-Actor-Name': 'panel' };
    const c = await h.get('/api/admin/contract', { token: made.body.key, headers });
    assert.equal(c.status, 200, `${role} ${JSON.stringify(c.body)}`);
    assert.equal(c.body.routesHash, r.body.routesHash);
    assert.equal((await h.get(`/api/admin/subscriptions/period-end?amount=1&unit=month&from=${KABUL_MAR1}`, { token: made.body.key, headers })).body.endsAt,
      plans.endOfPeriodIn(KABUL_MAR1, 1, 'month', 'Asia/Kabul'), role);
  }
});

test('S18: سرآیندِ X-Admin-API-Version روی هر پاسخِ /api/admin (۲۰۰، ۴۰۱، ۴۰۴، ورود) — و نه بیرون از آن', async () => {
  const sup = await tok('bridge-malek');
  const cases = [
    await h.get('/api/admin/me', { token: sup }),
    await h.get('/api/admin/me', {}),
    await h.get('/api/admin/no-such-route', { token: sup }),
    await h.post('/api/admin/login', { username: 'bridge-malek', password: 'نادرست' }),
    await h.get('/api/v1/admin/me', { token: sup }),
    await h.get('/api/admin/finance/summary', { token: sup }),
  ];
  for (const r of cases) assert.equal(r.headers.get('x-admin-api-version'), '1', String(r.status));
  assert.equal((await h.get('/api/health')).headers.get('x-admin-api-version'), null);
  assert.equal((await h.get('/api/plans')).headers.get('x-admin-api-version'), null);
});

test('S18 و S15: سقفِ نرخِ IP پیش از احراز (درخواستِ بی‌توکن هم شمرده می‌شود)', async () => {
  resetAll();
  for (const [path, max] of [['/api/admin/contract', 60], ['/api/admin/subscriptions/period-end?amount=1&unit=month', 240]]) {
    let last = null;
    for (let i = 0; i <= max; i++) last = await h.get(path, {});
    assert.equal(last.status, 429, `${path} ⇒ ${last.status}`);
  }
  resetAll();
});
