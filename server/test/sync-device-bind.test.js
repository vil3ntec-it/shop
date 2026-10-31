'use strict';
/**
 * SEC-AUTH-14 (§۷ و §۱۳ِ برنامهٔ امنیت؛ گزارشِ عاملِ S89/#571): شناسهٔ دستگاهِ همگام‌سازی به نشست بسته است.
 *
 * `device_id`ِ Sync v1/v3 را کلاینت می‌فرستد و چند تصمیمِ سرور به آن بسته است — از جمله «شاگرد فقط ردیفی را
 * پاک می‌کند که دستگاهِ خودش ساخته» (`data.delete.own`). شاگردی که شناسهٔ دستگاهِ صاحب را (از `GET /status`)
 * برمی‌داشت، ردیف‌های صاحب را پاک می‌کرد. حالا شناسه باید همان دستگاهی باشد که توکن با آن ساخته شده:
 *   • پرچمِ `sync.device_bind` روشن ⇒ ۴۰۳ `device_mismatch`، هیچ اثری؛
 *   • خاموش (پیش‌فرض، سازگاریِ برنامه‌های نصب‌شده) ⇒ همان رفتارِ پیشین + ردیفِ سابقهٔ `sync.device_mismatch`.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');
const syncV1 = require('../src/lib/sync-v1');

let admin = '';
let seq = 0;
const opId = () => `01K${String(Date.now()).slice(-9)}${String(++seq).padStart(5, '0')}`;
const push = (token, device, ops, headers = {}) => h.api('POST', '/api/sync/v1/push', {
  token, body: { device_id: device, schema_version: 2, ops }, headers: { 'X-App': 'shop', ...headers },
});
const ins = (row) => ({ op_id: opId(), ts: Date.now(), table: 'products', row_id: row, type: 'insert', fields: { name: row, unit: 'دانه', salePrice: 10 } });
const del = (row) => ({ op_id: opId(), ts: Date.now(), table: 'products', row_id: row, type: 'delete', fields: {} });
const flag = (scope, value) => h.put('/api/admin/pump/live-config', { scope, key: 'sync.device_bind', value }, { token: admin })
  .then((r) => assert.equal(r.status, 200, JSON.stringify(r.body)));
const mismatches = (shopId) => many(`SELECT * FROM audit_logs WHERE action='sync.device_mismatch' AND shop_id=$1`, [shopId]);

/** صاحب با دستگاهِ OWNER و شاگرد با دستگاهِ STAFF، هر دو با توکنِ بسته به دستگاهِ خودشان. */
async function shopWithStaff(label) {
  const o = await h.newUser(`صاحب-${label}`);
  const shopId = (await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: o.accessToken })).body.shop.id;
  const ownerDev = `own-${++seq}`;
  const owner = (await h.post('/api/auth/login', { identifier: o.email, password: o.password, device: { deviceId: ownerDev, name: 'گوشیِ صاحب', platform: 'android' } })).body;
  const s = await h.newUser(`شاگرد-${label}`);
  await query(`INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at)
               VALUES ($1,$2,$3,'staff','active',$4,$4)`, [newId('mem'), shopId, s.user.id, now()]);
  const staffDev = `stf-${++seq}`;
  const staff = (await h.post('/api/auth/login', { identifier: s.email, password: s.password, device: { deviceId: staffDev, name: 'گوشیِ شاگرد', platform: 'android' } })).body;
  //  شاگرد برای نوشتن `multi_device` می‌خواهد — اشتراکِ دکان از پنل
  const g = await h.post('/api/admin/subscriptions', { shopId, plan: 'pro', days: 30 }, { token: admin, headers: { 'Idempotency-Key': `g-${Date.now()}-${seq++}` } });
  assert.ok(g.status < 300, JSON.stringify(g.body));
  return { shopId, owner: owner.accessToken, ownerDev, staff: staff.accessToken, staffDev };
}

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
  const pw = require('../src/lib/password');
  await query(`INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
               VALUES ($1,'arshad','ارشد',$2,'superadmin','active',$3)`, [newId('adm'), await pw.hashPassword('Admin!12345'), now()]);
  admin = (await h.post('/api/admin/login', { username: 'arshad', password: 'Admin!12345' })).body.token;
});
test.after(async () => { await h.stop(); });

test('SEC-AUTH-14: شاگرد با شناسهٔ دستگاهِ صاحب ردیفِ صاحب را پاک نمی‌کند (پرچم روشن)؛ با شناسهٔ خودش همان قاعدهٔ پیشین', async () => {
  const a = await shopWithStaff('الف');
  await flag(a.shopId, true);
  assert.equal((await push(a.owner, a.ownerDev, [ins('rice')])).status, 200);
  //  ⛔ پیش از اصلاح: ۲۰۰ و ردیفِ صاحب پاک می‌شد
  const spoof = await push(a.staff, a.ownerDev, [del('rice')]);
  assert.equal(spoof.status, 403, JSON.stringify(spoof.body));
  assert.equal(spoof.body.error.code, 'device_mismatch');
  //  هر جای دیگرِ ادعا هم: X-Device، querystring، pull و status
  assert.equal((await push(a.staff, a.staffDev, [del('rice')], { 'X-Device': a.ownerDev })).status, 403);
  assert.equal((await h.get(`/api/sync/v1/pull?device_id=${a.ownerDev}&since=0`, { token: a.staff, headers: { 'X-App': 'shop' } })).status, 403);
  assert.equal((await h.get(`/api/sync/v1/status?device_id=${a.ownerDev}`, { token: a.staff, headers: { 'X-App': 'shop' } })).status, 403);
  const own = await push(a.staff, a.staffDev, [del('rice')]);
  assert.equal(own.status, 200, JSON.stringify(own.body));
  assert.ok(JSON.stringify(own.body).includes('delete_not_allowed'), JSON.stringify(own.body));
  const snap = await h.get('/api/sync/v1/snapshot', { token: a.owner, headers: { 'X-App': 'shop' } });
  assert.ok(JSON.stringify(snap.body).includes('rice'), 'ردیفِ صاحب سرِ جایش است');
  //  دستگاهِ خودِ هر کس بی هیچ ردیفِ ناجوری
  assert.equal((await push(a.owner, a.ownerDev, [ins('oil')])).status, 200);
  const rows = await mismatches(a.shopId);
  assert.ok(rows.length >= 1 && rows.every((r) => r.target_id === a.ownerDev), JSON.stringify(rows.map((r) => r.target_id)));
});

test('SEC-AUTH-14: پرچمِ خاموش (پیش‌فرض) برنامهٔ نصب‌شده را نمی‌شکند — فقط یک ردیفِ سابقه در روز', async () => {
  const b = await shopWithStaff('ب');
  const first = await push(b.owner, 'another-install', [ins('tea')]);
  assert.equal(first.status, 200, JSON.stringify(first.body));
  assert.equal((await push(b.owner, 'another-install', [ins('sugar')])).status, 200);
  const rows = await mismatches(b.shopId);
  assert.equal(rows.length, 1);
  const d = typeof rows[0].detail === 'string' ? JSON.parse(rows[0].detail) : rows[0].detail;
  assert.deepEqual({ bound: d.bound, enforced: d.enforced }, { bound: b.ownerDev, enforced: false });
  //  پرچمِ `all` هم کار می‌کند و پرچمِ خودِ دکان بر آن جلو می‌افتد
  await flag('all', true);
  try {
    assert.equal((await push(b.owner, 'another-install', [ins('salt')])).status, 403);
    await flag(b.shopId, false);
    assert.equal((await push(b.owner, 'another-install', [ins('salt')])).status, 200);
  } finally {
    await flag('all', false);
  }
});

test('SEC-AUTH-14: نشستِ بی دستگاه با پرچمِ روشن شناسهٔ ثبت‌شدهٔ کاربرِ دیگر را نمی‌گیرد؛ شناسهٔ تازه‌اش آزاد است', async () => {
  const c = await shopWithStaff('ج');
  await flag(c.shopId, true);
  const s = await one(`SELECT u.email FROM users u JOIN shop_members m ON m.user_id=u.id WHERE m.shop_id=$1 AND m.role='staff'`, [c.shopId]);
  const bare = (await h.post('/api/auth/login', { identifier: s.email, password: 'Passw0rd!test' })).body.accessToken;
  assert.ok(bare);
  assert.equal((await one('SELECT device_id FROM tokens WHERE kind=$1 AND token_hash=$2', ['access', require('../src/lib/tokens').hashToken(bare)])).device_id, null);
  assert.equal((await push(bare, c.ownerDev, [ins('x-' + seq)])).status, 403);
  assert.equal((await push(bare, 'fresh-unbound-install', [ins('y-' + seq)])).status, 200);
});

test('SEC-AUTH-14: کامپیوترِ پمپ (توکنِ دستگاه) هم فقط با شناسهٔ خودش', async () => {
  const p = await h.newUser('صاحبِ پمپ', 'pump');
  const st = await h.post('/api/pump', { name: 'پمپِ بند' }, { token: p.accessToken });
  assert.equal(st.status, 201, JSON.stringify(st.body));
  const bound = await h.post('/api/pump/device/bind', { device: { uid: 'pc-1', name: 'کامپیوتر', platform: 'windows' } }, { token: p.accessToken });
  assert.equal(bound.status, 201, JSON.stringify(bound.body));
  const dev = bound.body.deviceToken;
  await flag(st.body.station.id, true);
  const status = (id) => h.get(`/api/sync/v1/status?device_id=${id}`, { token: dev, headers: { 'X-App': 'pump' } });
  assert.equal((await status('pc-1')).status, 200);
  const other = await status('pc-2');
  assert.equal(other.status, 403);
  assert.equal(other.body.error.code, 'device_mismatch');
});
