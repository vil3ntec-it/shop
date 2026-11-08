'use strict';
/**
 * ⛔ سقفِ نرخِ درهای دستهٔ ۳۲ (همراهِ `routes-idor-32.test.js`):
 *   ف۳ `POST /api/auth/staff/device-key` فقط سطلِ IP (`sessionLimit`) داشت: یک توکن (دزدیده یا حلقهٔ برنامه) از IP های
 *      تازه بی‌سقف می‌زد. حالا سطلِ حساب پس از احراز (`deviceKeyMax`): همان حساب از IP ِ تازه هم ۴۲۹، حسابِ دیگر باز.
 *   لنگرِ سابقه (`/api/sync/v1/audit-anchor`): سطلِ IP پیش از احراز (`syncip`؛ توکنِ نادرست هم شمرده می‌شود ⇒ ۴۲۹ نه
 *      ۴۰۱) و سطلِ حسابِ همگام‌سازی (`sync`، به‌ازای دکان) پس از آن.
 * سقف‌ها پیش از ساختنِ برنامه (`h.start`) تنگ می‌شوند.
 */
process.env.TRUST_PROXY = 'true';

const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const h = require('./helpers');
//  `helpers` سقفِ عمومی را برای همهٔ آزمون‌ها باز می‌کند؛ این‌جا پیش از `h.start()` (ساختنِ روترها) تنگ می‌شود
Object.assign(require('../src/config').rateLimit, { sessionMax: 4, deviceKeyMax: 3, syncIpMax: 4, syncMax: 5 });
const { query, one, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');

const FORMAT = 'tohid-audit-chain-v1';
let A;
let B;
let ipN = 0;
const freshIp = () => { ipN += 1; return `198.19.${Math.floor(ipN / 250)}.${(ipN % 250) + 1}`; };
const from = (ip, token) => ({ headers: { 'X-Forwarded-For': ip, 'X-App': 'shop' }, ...(token ? { token } : {}) });
const devOf = async (userId) => (await one('SELECT device_uid FROM devices WHERE user_id=$1 ORDER BY created_at LIMIT 1', [userId])).device_uid;

async function shopWith(label) {
  resetAll();
  const o = await h.newUser(`صاحبِ ${label}`, 'shop');
  resetAll();
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: o.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const id = made.body.shop.id;
  await query("INSERT INTO live_config (scope, key, value, updated_at) VALUES ($1,'shop.doc_chain','true',1) ON CONFLICT DO NOTHING", [id]);
  const S = { id, owner: { token: o.accessToken, userId: o.user.id, dev: await devOf(o.user.id) } };
  resetAll();
  const u = await h.newUser(`حسابدارِ ${label}`, 'shop');
  await query(`INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at)
               VALUES ($1,$2,$3,'accountant','active',$4,$4)`, [newId('mem'), id, u.user.id, now()]);
  S.accountant = { token: u.accessToken, userId: u.user.id, dev: await devOf(u.user.id) };
  resetAll();
  return S;
}

test.before(async () => {
  await h.start();
  A = await shopWith('الف');
  B = await shopWith('ب');
});
test.after(async () => { await h.stop(); });
test.beforeEach(() => resetAll());

test('ف۳ رازِ دستگاه: سطلِ حساب پس از احراز — همان حساب از IP ِ تازه پس از سقف ۴۲۹؛ حسابِ دیگر (همان دکان و دکانِ دیگر) باز', async () => {
  for (let i = 1; i <= 3; i++) {
    const r = await h.post('/api/auth/staff/device-key', {}, from(freshIp(), A.owner.token));
    assert.equal(r.status, 200, `تلاشِ ${i}: ${JSON.stringify(r.body)}`);
    assert.deepEqual(r.body, { deviceKey: null, reason: 'not_staff_account' });
  }
  const blocked = await h.post('/api/auth/staff/device-key', {}, from(freshIp(), A.owner.token));
  assert.equal(blocked.status, 429, `IP ِ تازه، همان حساب: ${blocked.status} ${JSON.stringify(blocked.body)}`);
  assert.ok(Number(blocked.headers.get('retry-after')) > 0);
  assert.equal(JSON.stringify(blocked.body).includes(A.owner.userId), false, 'پاسخِ ۴۲۹ شناسهٔ حساب را نمی‌گوید');
  assert.equal((await h.post('/api/auth/staff/device-key', {}, from(freshIp(), A.accountant.token))).status, 200, 'حسابِ دیگرِ همان دکان');
  assert.equal((await h.post('/api/auth/staff/device-key', {}, from(freshIp(), B.owner.token))).status, 200, 'دکانِ دیگر');
});

test('رازِ دستگاه: سطلِ IP پیش از احراز همچنان — توکنِ نادرست شمرده می‌شود و پس از سقف ۴۲۹ (نه ۴۰۱)؛ IP ِ دیگر باز', async () => {
  const ip = freshIp();
  for (let i = 1; i <= 4; i++) {
    const r = await h.post('/api/auth/staff/device-key', {}, from(ip, `bad-${'x'.repeat(30)}-${i}`));
    assert.equal(r.status, 401, `تلاشِ ${i}`);
  }
  assert.equal((await h.post('/api/auth/staff/device-key', {}, from(ip, A.owner.token))).status, 429);
  assert.equal((await h.post('/api/auth/staff/device-key', {}, from(freshIp(), A.owner.token))).status, 200);
});

const heads = (dev, n) => [{ chain: `${dev}/1`, seq: n, hash: crypto.createHash('sha256').update(`${dev}-${n}`).digest('hex') }];
const anchor = (ip, who, n) => h.post('/api/sync/v1/audit-anchor', { device_id: who.dev, format: FORMAT, heads: heads(who.dev, n) }, from(ip, who.token));

test('لنگرِ سابقه: سطلِ IP پیش از احراز (توکنِ نادرست ⇒ پس از سقف ۴۲۹، توکنِ درست از همان IP هم)؛ IP ِ دیگر باز', async () => {
  const ip = freshIp();
  for (let i = 1; i <= 4; i++) {
    const r = await h.post('/api/sync/v1/audit-anchor', { device_id: 'x', format: FORMAT, heads: [] }, from(ip, i % 2 ? `bad-${'y'.repeat(30)}-${i}` : null));
    assert.equal(r.status, 401, `تلاشِ ${i}: ${JSON.stringify(r.body)}`);
  }
  assert.equal((await anchor(ip, A.owner, 1)).status, 429);
  assert.equal((await anchor(freshIp(), A.owner, 1)).status, 200);
});

test('لنگرِ سابقه: سطلِ حسابِ همگام‌سازی (به‌ازای دکان) از IP های تازه ۴۲۹؛ عضوِ دیگرِ همان دکان هم؛ دکانِ دیگر باز — بی ردیفِ پس از سقف', async () => {
  for (let i = 1; i <= 5; i++) {
    const r = await anchor(freshIp(), A.owner, i);
    assert.equal(r.status, 200, `تلاشِ ${i}: ${JSON.stringify(r.body)}`);
  }
  assert.equal((await anchor(freshIp(), A.owner, 6)).status, 429);
  assert.equal((await anchor(freshIp(), A.accountant, 1)).status, 429, 'سطلِ همان دکان');
  assert.equal((await anchor(freshIp(), B.owner, 1)).status, 200, 'دکانِ دیگر');
  const n = await one('SELECT COUNT(*)::int AS n FROM device_audit_anchors WHERE shop_id=$1', [A.id]);
  assert.equal(Number(n.n), 5);
});
