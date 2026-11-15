'use strict';
/**
 * «هیچ جزئیاتِ فنی/زیرساخت به بیرون» (`src/lib/public-detail.js`، `docs/RULES-fa.md`):
 *
 *  • `/health`، `/api/health`، `/ready` و `/api/v1/config/server` برای درخواستی که از پیش‌کش/تونل گذشته (سرآیندِ
 *    `X-Forwarded-For`، `Forwarded`، `CF-Connecting-IP`…) نسخهٔ سرور، مدتِ روشن بودن و حالِ کارگرِ ایمیل را نمی‌گویند؛
 *  • همان درها برای خودِ میزبان (loopback، بی سرآیند — `update.sh`، healthcheck ِ Docker) همان پاسخِ کامل‌اند؛
 *  • میدان‌هایی که برنامه‌ها می‌خوانند (`ok`/`server`/`database`/`service`، `serverId`/`wire`/`features`/`publicUrls`) همیشه هستند؛
 *  • پاسخِ خطای ۵۰۰ بی stack و بی نامِ میزبان است (فقط کدِ پیگیری).
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const config = require('../src/config');
const pd = require('../src/lib/public-detail');

test.before(async () => { await h.start(); });
test.after(async () => { await h.stop(); });

const OUTSIDE = [
  { 'X-Forwarded-For': '203.0.113.7' },
  { Forwarded: 'for=203.0.113.7;proto=https' },
  { 'CF-Connecting-IP': '203.0.113.7' },
  { 'X-Real-IP': '203.0.113.7' },
];

test('public-detail: نشانیِ خصوصی و loopback از عمومی جداست', () => {
  for (const a of ['127.0.0.1', '::1', '::ffff:127.0.0.1', '10.1.2.3', '172.17.0.1', '172.31.255.255', '192.168.1.5', '::ffff:172.18.0.4', 'fd12:3456::1', 'fe80::1%eth0', '[::1]']) {
    assert.equal(pd.isPrivateAddr(a), true, a);
  }
  for (const a of ['', '8.8.8.8', '172.32.0.1', '172.15.0.1', '203.0.113.7', '::ffff:8.8.8.8', '2001:db8::1', '192.169.0.1', 'localhost', '1.2.3']) {
    assert.equal(pd.isPrivateAddr(a), false, a);
  }
  const req = (addr, headers = {}) => ({ socket: { remoteAddress: addr }, headers });
  assert.equal(pd.internalCaller(req('127.0.0.1')), true);
  assert.equal(pd.internalCaller(req('172.17.0.1')), true);
  assert.equal(pd.internalCaller(req('8.8.8.8')), false);
  for (const k of pd.FORWARD_HEADERS) assert.equal(pd.internalCaller(req('127.0.0.1', { [k]: 'x' })), false, k);
  assert.equal(pd.internalCaller(null), false);
});

test('public-detail: /health و /ready بیرون از شبکه بی نسخه، مدت و کارگر؛ درون همان کامل', async () => {
  for (const path of ['/health', '/api/health', '/api/v1/health']) {
    const inside = await h.api('GET', path);
    assert.equal(inside.status, 200, path);
    assert.equal(inside.body.version, require('../package.json').version, path);
    assert.equal(typeof inside.body.uptimeSeconds, 'number', path);
    assert.ok(['up', 'down'].includes(inside.body.email_worker), path);
    for (const headers of OUTSIDE) {
      const out = await h.api('GET', path, { headers });
      assert.equal(out.status, 200, path);
      for (const k of pd.HEALTH_DETAIL) assert.equal(out.body[k], undefined, `${path} ${k} ${JSON.stringify(headers)}`);
      //  همان میدان‌هایی که برنامه‌ها (ServerPulse، ServerInfo.fromHealth) می‌خوانند
      assert.equal(out.body.ok, true);
      assert.equal(out.body.server, 'online');
      assert.equal(out.body.service, 'vill3n-auth');
      assert.ok(['connected', 'unavailable'].includes(out.body.database));
    }
  }
  const ready = await h.api('GET', '/ready', { headers: OUTSIDE[0] });
  assert.equal(ready.body.version, undefined);
  assert.ok(['connected', 'unavailable'].includes(ready.body.database));
  assert.equal((await h.api('GET', '/ready')).body.version, require('../package.json').version);
});

test('public-detail: /config/server بیرون از شبکه بی نسخه؛ serverId، wire و publicUrls برای جابه‌جایی می‌مانند', async () => {
  const before = config.publicBaseUrls;
  config.publicBaseUrls = 'https://a.example.org,https://b.example.org';
  try {
    const inside = await h.api('GET', '/api/v1/config/server');
    assert.equal(inside.body.version, require('../package.json').version);
    for (const headers of OUTSIDE) {
      const out = await h.api('GET', '/api/v1/config/server', { headers });
      assert.equal(out.status, 200);
      assert.equal(out.body.version, undefined);
      assert.equal(out.body.serverId, inside.body.serverId);
      assert.deepEqual(out.body.wire, inside.body.wire);
      assert.deepEqual(out.body.features, inside.body.features);
      assert.deepEqual(out.body.publicUrls, inside.body.publicUrls);
    }
  } finally {
    config.publicBaseUrls = before;
  }
});

test('public-detail: مسیرِ ناشناخته و خطای بدنه پیامِ ساده می‌دهند — بی stack، بی نامِ میزبان', async () => {
  for (const path of ['/api/v1/no-such-route-63h', '/api/v1/shop/no-such-route-63h', '/no-such-route-63h']) {
    const miss = await h.api('GET', path, { headers: OUTSIDE[0] });
    assert.ok(miss.status >= 400 && miss.status < 500, `${path} ${miss.status}`);
    const text = JSON.stringify(miss.body);
    assert.doesNotMatch(text, /\bat\s+\S+\s+\(|node_modules|\/src\/|localhost|127\.0\.0\.1|vill3n\.top/i, path);
  }
  const bad = await h.raw('POST', '/api/v1/auth/login', Buffer.from('{"x":'), { headers: { ...OUTSIDE[0], 'Content-Type': 'application/json' } });
  assert.equal(bad.status, 400);
  assert.doesNotMatch(JSON.stringify(bad.body), /Unexpected|position|SyntaxError|\bat\s/);
});
