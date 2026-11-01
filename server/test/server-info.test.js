'use strict';
/**
 * `GET /api/v1/config/server` — هویت و توانِ سرور برای پرچمِ `app.multi_server` ِ برنامه‌ها
 * (`lib/server-identity.js`، `core/server/ServerInfo.kt`).
 *
 *  • عمومی و بی توکن، روی `/api/v1` و `/api`؛ شکلِ پاسخ همان قراردادِ `ServerInfo.parse` ِ core
 *  • شناسه پایدار است: راه‌اندازیِ دوباره (حافظهٔ تازه، همان دفتر) همان شناسه؛ دفترِ تازه ⇒ شناسهٔ تازه
 *  • ⛔ بی راز: فقط کلیدهای فهرستِ سفید، و هیچ مقدارِ رازی از `app_config`
 *  • SUB-10: زیرِ پیشوندِ موجودِ `/config`، فقط GET، بی کلیدِ پولی (دستهٔ «FREE/عمومی»)
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const inv = require('./route-inventory');
const { query } = require('../src/db');
const config = require('../src/config');
const identity = require('../src/lib/server-identity');

const KEYS = ['ok', 'kind', 'serverId', 'name', 'version', 'wire', 'api', 'features', 'publicUrl', 'time'].sort();

test.before(async () => { await h.start(); });
test.after(async () => { await h.stop(); });

test('server-info: عمومی، بی توکن، روی هر دو پیشوند، با قراردادِ ServerInfo', async () => {
  const a = await h.get('/api/v1/config/server');
  assert.equal(a.status, 200);
  assert.equal(a.body.kind, 'tohid-server');
  assert.match(a.body.serverId, /^srv_[a-z0-9]{16,40}$/);
  assert.deepEqual(a.body.wire, { min: 1, max: 1 });
  assert.deepEqual(a.body.api, ['v1']);
  assert.ok(Array.isArray(a.body.features) && a.body.features.includes('sync.v1') && a.body.features.includes('config.server'));
  assert.equal(a.body.version, require('../package.json').version);
  assert.ok(a.body.name.length > 0 && a.body.name.length <= 60);
  const b = await h.get('/api/config/server');
  assert.equal(b.status, 200);
  assert.equal(b.body.serverId, a.body.serverId);
  //  `/config` ِ پیشین دست نخورده
  const c = await h.get('/api/v1/config');
  assert.equal(c.status, 200);
  assert.equal(c.body.serverId, undefined);
});

test('server-info: شناسه پس از راه‌اندازیِ دوباره همان است؛ دفترِ تازه یا مقدارِ خراب ⇒ شناسهٔ تازه', async () => {
  const first = (await h.get('/api/v1/config/server')).body.serverId;
  //  «راه‌اندازیِ دوباره»: حافظهٔ فرآیند خالی، همان دفتر
  identity.forgetCache();
  assert.equal(await identity.serverId(), first);
  assert.equal((await h.get('/api/v1/config/server')).body.serverId, first);
  assert.equal((await query(`SELECT value FROM app_config WHERE key='server_id'`)).rows[0].value, first);

  //  مقدارِ خراب در دفتر ⇒ شناسهٔ تازهٔ معتبر (دیگر نمی‌شود گفت همان سرور است)
  await query(`UPDATE app_config SET value='hacked' WHERE key='server_id'`);
  identity.forgetCache();
  const repaired = (await h.get('/api/v1/config/server')).body.serverId;
  assert.match(repaired, /^srv_[a-z0-9]{16,40}$/);
  assert.notEqual(repaired, first);

  //  دفترِ تازه (سرورِ دیگر) ⇒ شناسهٔ دیگر
  await query(`DELETE FROM app_config WHERE key='server_id'`);
  identity.forgetCache();
  const fresh = (await h.get('/api/v1/config/server')).body.serverId;
  assert.match(fresh, /^srv_[a-z0-9]{16,40}$/);
  assert.notEqual(fresh, repaired);
});

test('server-info: ⛔ بی راز — فقط کلیدهای فهرستِ سفید و هیچ مقدارِ رازیِ app_config', async () => {
  const secrets = {
    license_private_key: 'SECRET-LICENSE-KEY-0123456789', vapid_private: 'SECRET-VAPID-0123456789',
    email_pass: 'SECRET-SMTP-PASS-0123', sms_api_key: 'SECRET-SMS-0123',
  };
  for (const [k, v] of Object.entries(secrets)) {
    await query(
      `INSERT INTO app_config (key,value,updated_at) VALUES ($1,$2,0) ON CONFLICT (key) DO UPDATE SET value=excluded.value`, [k, v]);
  }
  const r = await h.get('/api/v1/config/server');
  assert.deepEqual(Object.keys(r.body).sort(), KEYS);
  const text = JSON.stringify(r.body);
  for (const v of Object.values(secrets)) assert.ok(!text.includes(v), `راز بیرون رفت: ${v}`);
  assert.ok(!/secret|private|passphrase|token/i.test(text), text);
});

test('server-info: publicUrl فقط نشانیِ درستِ SERVER_URL/PUBLIC_URL؛ نامِ سرور از SERVER_NAME', async () => {
  const saved = config.serverUrl;
  try {
    config.serverUrl = 'https://shop.example.com/';
    assert.equal((await h.get('/api/v1/config/server')).body.publicUrl, 'https://shop.example.com');
    for (const bad of ['https://user:pw@shop.example.com', 'javascript:alert(1)', 'not a url', 'https://x.example/?a=1']) {
      config.serverUrl = bad;
      assert.equal((await h.get('/api/v1/config/server')).body.publicUrl, '', bad);
    }
  } finally { config.serverUrl = saved; }
  process.env.SERVER_NAME = 'سرورِ دکانِ مرکزی';
  try {
    assert.equal((await h.get('/api/v1/config/server')).body.name, 'سرورِ دکانِ مرکزی');
  } finally { delete process.env.SERVER_NAME; }
});

test('server-info: SUB-10 — زیرِ پیشوندِ موجودِ /config، فقط GET، بی کلیدِ پولی و بی میان‌افزارِ ورود', () => {
  const mounts = inv.mounts('/config');
  assert.deepEqual(mounts, [{ prefix: '/config/server', file: 'server-info.js' }]);
  const routes = inv.routes('/config');
  assert.deepEqual(routes.map((r) => r.key), ['GET /config/server']);
  for (const r of routes) {
    assert.ok(!/needWrite|requireFeature|requireUser|requireShop|requireAdmin/.test(`${r.uses} ${r.head}`), r.key);
  }
});

test('tls-listener: بی TLS_* همان http؛ پروندهٔ نبوده پیامِ روشن؛ اثرِ انگشتِ گواهیِ خراب خالی', () => {
  const tls = require('../src/lib/tls-listener');
  assert.equal(tls.options(null), null);
  assert.equal(tls.options({ certFile: '', keyFile: '', pfxFile: '' }), null);
  assert.throws(() => tls.options({ certFile: '/nope/cert.pem', keyFile: '/nope/key.pem' }), /TLS_CERT_FILE خوانده نشد/);
  assert.throws(() => tls.options({ pfxFile: '/nope/server.pfx' }), /TLS_PFX_FILE خوانده نشد/);
  assert.equal(tls.fingerprintOf({ cert: Buffer.from('not a certificate') }), '');
  assert.equal(tls.fingerprintOf(null), '');
});
