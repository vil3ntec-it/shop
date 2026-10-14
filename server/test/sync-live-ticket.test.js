'use strict';
/**
 * F0-11 — توکنِ دسترسی در آدرسِ سوکتِ زنده (`?token=`) در لاگِ پراکسی می‌ماند.
 *
 * راهِ نو: برنامه با `POST /api/sync/v1/live/ticket` (توکن در سرآیند) یک
 * «بلیتِ» یک‌بارمصرفِ ۶۰ ثانیه‌ای می‌گیرد و با `?ticket=` وصل می‌شود.
 * قاعدهٔ سازگاری: `?token=` و `Authorization: Bearer` هنوز پذیرفته‌اند تا
 * برنامه‌های کهنه از کار نیفتند.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const { WebSocket } = require('ws');
const h = require('./helpers');
const live = require('../src/lib/sync-v1-live');

let a; let base;

async function shopOwner(name, deviceId) {
  const u = await h.newUser(name, 'shop');
  await h.api('POST', '/api/shop', { token: u.accessToken, body: { name: 'دکانِ ' + name } });
  const login = await h.api('POST', '/api/auth/login', {
    body: { identifier: u.email, password: u.password, app: 'shop', device: { deviceId, name: deviceId, platform: 'test' } },
  });
  return { ...u, ...login.body };
}

/** وصل شدن؛ نتیجه «open» یا شمارهٔ وضعیتِ ردِ HTTP */
function dial(query) {
  return new Promise((resolve) => {
    const ws = new WebSocket(base.replace('http', 'ws') + '/api/sync/v1/live?' + query);
    ws.on('open', () => { ws.close(); resolve('open'); });
    ws.on('unexpected-response', (_req, res) => { resolve(res.statusCode); ws.terminate(); });
    ws.on('error', () => resolve('error'));
  });
}

const ticketOf = (token) => h.api('POST', '/api/sync/v1/live/ticket', { token, body: {}, headers: { 'X-App': 'shop' } });

test.before(async () => {
  base = await h.start();
  live.attach(h.server());
  a = await shopOwner('بلیت‌دار', 'dev-t');
});
test.after(async () => { await h.stop(); });

test('بلیت با POST گرفته می‌شود و توکن در آن نیست', async () => {
  const r = await ticketOf(a.accessToken);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(typeof r.body.ticket, 'string');
  assert.ok(r.body.ticket.length >= 32);
  assert.ok(!r.body.ticket.includes(a.accessToken), 'بلیت نباید خودِ توکن باشد');
  assert.equal(r.body.expires_in, 60);
});

test('بی توکن بلیتی داده نمی‌شود', async () => {
  const r = await ticketOf(null);
  assert.equal(r.status, 401);
});

test('با ?ticket= وصل می‌شود — و همان بلیت بارِ دوم رد می‌شود (یک‌بارمصرف)', async () => {
  const { body } = await ticketOf(a.accessToken);
  const q = `ticket=${encodeURIComponent(body.ticket)}&device_id=dev-t&app=shop`;
  assert.equal(await dial(q), 'open');
  assert.equal(await dial(q), 401, 'بلیتِ مصرف‌شده دوباره کار نمی‌کند');
});

test('بلیتِ ساختگی یا کهنه‌تر از ۶۰ ثانیه رد می‌شود', async () => {
  assert.equal(await dial('ticket=' + 'x'.repeat(43) + '&app=shop'), 401);
  const { body } = await ticketOf(a.accessToken);
  const realNow = Date.now;
  Date.now = () => realNow() + 61_000;
  try {
    assert.equal(await dial(`ticket=${encodeURIComponent(body.ticket)}&app=shop`), 401);
  } finally { Date.now = realNow; }
});

test('سازگاری: ?token= کهنه هنوز وصل می‌شود', async () => {
  assert.equal(await dial(`token=${encodeURIComponent(a.accessToken)}&device_id=old&app=shop`), 'open');
});

test('بلیتِ نشستی که در این ۶۰ ثانیه بیرون رفت، دیگر کار نمی‌کند', async () => {
  const b = await shopOwner('بیرون‌رونده', 'dev-out');
  const { body } = await ticketOf(b.accessToken);
  await h.api('POST', '/api/auth/logout', { token: b.accessToken, body: { refreshToken: b.refreshToken } });
  const still = await h.api('GET', '/api/sync/v1/status', { token: b.accessToken, headers: { 'X-App': 'shop' } });
  assert.equal(still.status, 401, 'خروج توکنِ دسترسی را باطل می‌کند');
  assert.equal(await dial(`ticket=${encodeURIComponent(body.ticket)}&app=shop`), 401);
});

/* ---- برنامهٔ وب (`license/sync-engine.js`) دیگر توکن را در آدرس نمی‌گذارد ---- */
function webEngine(authFetch) {
  const fs = require('node:fs');
  const path = require('node:path');
  const vm = require('node:vm');
  const dialed = [];
  class FakeSocket { constructor(url) { dialed.push(url); } close() { } }
  const window = {
    TohidSyncCore: require(path.join(__dirname, '..', '..', 'license', 'sync-core.js')),
    TohidApiConfig: { baseUrl: () => 'https://api.example.test' },
    TohidAccount: {
      configured: () => true, isSignedIn: () => true, accessToken: async () => 'SECRET-ACCESS-TOKEN-0123456789',
      deviceId: () => 'web-1', appId: () => 'shop', authFetch, on() { },
    },
    addEventListener() { },
  };
  const ctx = vm.createContext({ window, WebSocket: FakeSocket, URL, setTimeout: () => 0, clearTimeout() { }, console });
  vm.runInContext(fs.readFileSync(path.join(__dirname, '..', '..', 'license', 'sync-engine.js'), 'utf8'), ctx);
  return { sync: window.TohidSync, dialed };
}

test('وب: با بلیت وصل می‌شود و توکن در آدرس نیست', async () => {
  const asked = [];
  const { sync, dialed } = webEngine(async (p, o) => { asked.push([p, o && o.method]); return { ticket: 'TICKET-1', expires_in: 60 }; });
  await sync.connectLive();
  assert.deepEqual(asked, [['/api/sync/v1/live/ticket', 'POST']]);
  assert.equal(dialed.length, 1);
  const u = new URL(dialed[0]);
  assert.equal(u.protocol, 'wss:');
  assert.equal(u.searchParams.get('ticket'), 'TICKET-1');
  assert.equal(u.searchParams.get('token'), null);
  assert.ok(!dialed[0].includes('SECRET-ACCESS-TOKEN'));
});

test('وب: سرورِ کهنه (۴۰۴ برای بلیت) ⇒ همان ?token= قدیم، تا سوکت از کار نیفتد', async () => {
  const { sync, dialed } = webEngine(async () => { const e = new Error('nf'); e.status = 404; throw e; });
  await sync.connectLive();
  assert.equal(new URL(dialed[0]).searchParams.get('token'), 'SECRET-ACCESS-TOKEN-0123456789');
});

test('وب: بی اینترنت (خطای بلیت غیر از ۴۰۴) ⇒ توکن در آدرس نمی‌رود', async () => {
  const { sync, dialed } = webEngine(async () => { throw new TypeError('Failed to fetch'); });
  await sync.connectLive();
  assert.equal(dialed.length, 0);
});
