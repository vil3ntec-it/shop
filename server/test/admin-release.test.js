'use strict';
/**
 * «کانالِ آزمایشی» در پنل (F0-27) — `routes/admin-release.js`.
 *
 *   • فقط مدیرِ ارشد می‌خواند و می‌نویسد
 *   • همان `live_config`ِ scope `release` که `/api/releases` می‌خواند — و همان دم
 *     (نه پس از ۶۰ ثانیه کشِ فهرست) دکانِ افزوده نسخهٔ آزمایشی می‌گیرد
 *   • شناسهٔ ناجور رد می‌شود، نه بی‌صدا دور ریخته
 *   • هر تغییر با «افزوده/برداشته» و دلیل در سابقه؛ بی‌تغییر ⇒ نه نوشتن، نه سابقه
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const os = require('node:os');
const fs = require('node:fs');
const path = require('node:path');

let fake, h, root = '', plain = '';
const APK = Buffer.from('fake-apk-bytes-0123456789');

test.before(async () => {
  //  GitHubِ ساختگی: پایدار 3.2.50، آزمایشی 3.2.60 (همان شکلِ releases.test.js)
  fake = http.createServer((req, res) => {
    const rel = (body, assets) => { res.setHeader('Content-Type', 'application/json'); res.end(JSON.stringify({ body, published_at: '2026-10-18T00:00:00Z', assets })); };
    if (req.url === '/repos/o/r/releases/tags/tohid-native') {
      return rel('پایدار', [
        { id: 1, name: 'Tohid-Native-3.2.50.apk', size: APK.length, updated_at: '2026-10-18T00:00:00Z' },
        { id: 2, name: 'version.txt', size: 7, updated_at: '2026-10-18T00:00:00Z' },
      ]);
    }
    if (req.url === '/repos/o/r/releases/tags/tohid-native-beta') {
      return rel('آزمایشی', [
        { id: 11, name: 'Tohid-Native-3.2.60.apk.beta', size: APK.length, updated_at: '2026-10-18T00:00:00Z' },
        { id: 12, name: 'version.txt', size: 7, updated_at: '2026-10-18T00:00:00Z' },
      ]);
    }
    if (req.url === '/repos/o/r/releases/assets/2') return res.end('3.2.50\n');
    if (req.url === '/repos/o/r/releases/assets/12') return res.end('3.2.60\n');
    res.statusCode = 404; res.end('{}');
  });
  await new Promise((r) => fake.listen(0, '127.0.0.1', r));
  process.env.RELEASE_API_BASE = `http://127.0.0.1:${fake.address().port}`;
  process.env.RELEASE_REPO = 'o/r';
  process.env.RELEASE_CACHE_PATH = fs.mkdtempSync(path.join(os.tmpdir(), 'rel-admin-'));
  h = require('./helpers');
  await h.start();

  const { query, newId, now } = require('../src/db');
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'relroot','ارشد',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Root!12345'), now()]
  );
  const login = await h.post('/api/admin/login', { username: 'relroot', password: 'Root!12345' });
  assert.equal(login.status, 200, JSON.stringify(login.body));
  root = login.body.token;
  const made = await h.post('/api/admin/admins', { username: 'relclerk', password: 'Clerk!12345', role: 'admin' }, { token: root });
  assert.ok([200, 201].includes(made.status), JSON.stringify(made.body));
  const l2 = await h.post('/api/admin/login', { username: 'relclerk', password: 'Clerk!12345' });
  assert.equal(l2.status, 200, JSON.stringify(l2.body));
  plain = l2.body.token;
});
test.after(async () => { await h.stop(); fake.close(); });

const put = (body, token = root) => h.put('/api/admin/release/beta', body, { token });
const latest = (q) => h.api('GET', `/api/releases/tohid-native/latest${q}`);
const auditRows = () => require('../src/db').many(
  `SELECT * FROM audit_logs WHERE action='admin.release_beta_set' ORDER BY created_at`, []
);

test('مدیرِ عادی نه می‌بیند نه می‌نویسد؛ بی‌توکن ۴۰۱', async () => {
  assert.equal((await h.get('/api/admin/release/beta', { token: plain })).status, 403);
  assert.equal((await put({ shops: ['shopA'] }, plain)).status, 403);
  assert.equal((await h.api('GET', '/api/admin/release/beta', {})).status, 401);
});

test('افزودنِ دکان ⇒ همان دم نسخهٔ آزمایشی؛ دستگاه با فاصله/ویرگول هم پذیرفته می‌شود؛ سابقه با دلیل', async () => {
  //  کشِ ۶۰ ثانیه‌ایِ فهرست پیش از تغییر پر می‌شود — باید همان دم تازه شود
  assert.equal((await latest('?shop=shopA')).body.channel, 'stable');

  const r = await put({ shops: ['shopA', 'shopB', 'shopA'], devices: 'dev1, dev2\ndev3', reason: 'گوشیِ خودم' });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(r.body.shops.ids, ['shopA', 'shopB']);
  assert.deepEqual(r.body.devices.ids, ['dev1', 'dev2', 'dev3']);
  assert.equal(r.body.shops.updatedBy, 'relroot');

  const live = await require('../src/lib/live-config').list('release');
  assert.deepEqual(live.items.find((i) => i.key === 'beta.shops').value, ['shopA', 'shopB']);

  const b = await latest('?shop=shopA');
  assert.equal(b.body.channel, 'beta');
  assert.equal(b.body.tag, 'tohid-native-beta');
  assert.equal(b.body.version, '3.2.60');
  assert.equal((await latest('?device=dev3')).body.channel, 'beta');
  assert.equal((await latest('?shop=shopZ')).body.channel, 'stable');

  const rows = await auditRows();
  assert.equal(rows.length, 1);
  const d = typeof rows[0].detail === 'string' ? JSON.parse(rows[0].detail) : rows[0].detail;
  assert.equal(d.shops.added, 'shopA, shopB');
  assert.equal(d.devices.added, 'dev1, dev2, dev3');
  assert.equal(d.reason, 'گوشیِ خودم');

  const g = await h.get('/api/admin/release/beta', { token: root });
  assert.equal(g.status, 200);
  assert.deepEqual(g.body.shops.ids, ['shopA', 'shopB']);
});

test('برداشتن ⇒ همان دم پایدار؛ فقط فیلدِ فرستاده‌شده عوض می‌شود؛ بی‌تغییر ⇒ نه نوشتن نه سابقه', async () => {
  const r = await put({ shops: ['shopB'] });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(r.body.changed, ['shops']);
  assert.deepEqual(r.body.devices.ids, ['dev1', 'dev2', 'dev3'], 'دستگاه‌ها دست نخوردند');
  assert.equal((await latest('?shop=shopA')).body.channel, 'stable');

  const rows = await auditRows();
  assert.equal(rows.length, 2);
  const d = typeof rows[1].detail === 'string' ? JSON.parse(rows[1].detail) : rows[1].detail;
  assert.equal(d.shops.removed, 'shopA');
  assert.equal(d.shops.added, '');

  const same = await put({ shops: ['shopB'], devices: ['dev3', 'dev2', 'dev1'] });
  assert.deepEqual(same.body.changed, []);
  assert.equal((await auditRows()).length, 2);
});

test('شناسهٔ ناجور یا فهرستِ خالیِ بدنه ⇒ ۴۰۰ و هیچ تغییری', async () => {
  const bad = await put({ shops: ['shopB', '../x'] });
  assert.equal(bad.status, 400);
  assert.equal(bad.body.error.code, 'bad_release_id');
  assert.equal((await put({})).status, 400);
  const g = await h.get('/api/admin/release/beta', { token: root });
  assert.deepEqual(g.body.shops.ids, ['shopB']);
});
