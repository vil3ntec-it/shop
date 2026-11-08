'use strict';
/**
 * دو نمونهٔ سرور روی **یک** PostgreSQL (`CLUSTER_ENABLED=1`؛ docs/SCALING-fa.md) — هر کدام فرآیندِ جدا
 * (`fixtures/cluster-child.js`)، با همان `createApp`، همان سوکتِ زنده و همان کارگرهای `src/index.js`.
 *
 *   • سقفِ نرخ مشترک است: درخواست‌های یک IP میانِ دو نمونه پخش می‌شوند و روی هم از سقف نمی‌گذرند (هر
 *     نمونه با انبارِ حافظه دو برابر می‌پذیرفت).
 *   • خبرها از نمونه‌ای به نمونهٔ دیگر می‌رسند: رلهٔ «عکسِ کالا عوض شد» (PUT روی یکی ⇒ سوکتِ دستگاهِ دیگر
 *     روی دیگری)، بلیتِ سوکتِ صادرشده روی یکی روی دیگری کار می‌کند و فقط یک بار، خروج روی یکی سوکتِ همان
 *     نشست روی دیگری را همان دم می‌بندد (4401)، و بیدارباشِ long-poll ِ چت.
 *   • کارگرهای پس‌زمینه فقط روی یک نمونه (رهبر)؛ رهبر کشته شود ⇒ دیگری زود رهبر می‌شود و همان کارگرها را دارد.
 *
 * PGlite درونِ یک فرآیند است و میانِ دو فرآیند مشترک نمی‌شود ⇒ این پرونده فقط روی PostgreSQL اجرا می‌شود.
 */
const path = require('node:path');
const { fork } = require('node:child_process');
const test = require('node:test');
const assert = require('node:assert/strict');

const PGLITE = /^pglite:/i.test(process.env.TEST_DATABASE_URL || '');
const CHILD = path.join(__dirname, 'fixtures', 'cluster-child.js');
const GENERAL_MAX = 6;

const kids = [];
let seq = 0;
function spawnChild(name) {
  const child = fork(CHILD, [], {
    cwd: path.join(__dirname, '..'),
    env: {
      ...process.env, CLUSTER_ENABLED: '1', INSTANCE_ID: `test-${name}`, CLUSTER_LEADER_RETRY_MS: '250', CLUSTER_HEARTBEAT_MS: '250',
      RATE_LIMIT_STORE: 'pg', TRUST_PROXY: 'true', CLUSTER_TEST_GENERAL_MAX: String(GENERAL_MAX),
      SYNC_LIVE_RECHECK_MS: '50',
    },
    stdio: ['ignore', process.env.CT_DEBUG ? 'inherit' : 'ignore', 'pipe', 'ipc'],
  });
  let err = '';
  child.stderr.on('data', (d) => { err += d; if (process.env.CT_DEBUG) process.stderr.write(`[${name}] ${d}`); if (err.length > 20000) err = err.slice(-20000); });
  const waiters = new Map();
  child.on('message', (m) => { const w = waiters.get(m.id); if (w && m.type === 'reply') { waiters.delete(m.id); w(m.out); } });
  const ready = new Promise((resolve, reject) => {
    const t = setTimeout(() => reject(new Error(`نمونهٔ ${name} بالا نیامد\n${err}`)), 60_000);
    child.on('message', (m) => { if (m.type === 'ready') { clearTimeout(t); resolve(m); } });
    child.on('exit', (code) => { clearTimeout(t); reject(new Error(`نمونهٔ ${name} بیرون رفت (${code})\n${err}`)); });
  });
  const ask = (m) => new Promise((resolve) => { const id = ++seq; waiters.set(id, resolve); child.send({ ...m, id }); });
  const kid = { name, child, ready, ask, base: '', stderr: () => err };
  kids.push(kid);
  return kid;
}

async function call(base, method, p, { token, body, ip, headers = {} } = {}) {
  const res = await fetch(base + p, {
    method,
    headers: {
      'Content-Type': 'application/json', 'X-App': 'shop',
      //  هر درخواست IP ِ خودش را دارد (پشتِ پروکسیِ مطمئن) تا سقفِ همگانیِ کوچکِ این آزمون فقط جایی که می‌خواهیم بخورد
      'X-Forwarded-For': ip || `10.${(seq >> 16) & 255}.${(seq >> 8) & 255}.${(++seq) & 255}`,
      ...(token ? { Authorization: `Bearer ${token}` } : {}), ...headers,
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  let json = null;
  try { json = JSON.parse(text); } catch { /* بی JSON */ }
  return { status: res.status, body: json, headers: res.headers };
}

/** همان سه پلهٔ ثبت‌نامِ `helpers.newUser`، دکان، و ورود به دکان روی همان نمونه */
async function shopOwner(base, tag) {
  const email = `cluster-${tag}-${Date.now()}@test.local`;
  const password = 'Passw0rd!test';
  const st = await call(base, 'POST', '/api/auth/register/start', { body: { name: tag, email, password } });
  assert.ok(st.status < 300 && st.body.devCode, JSON.stringify(st.body));
  const vf = await call(base, 'POST', '/api/auth/register/verify', { body: { email, code: st.body.devCode } });
  const done = await call(base, 'POST', '/api/auth/register/complete', {
    body: { ticket: vf.body.ticket, name: tag, password, app: 'shop', terms: { accepted: true }, device: { deviceId: `reg-${tag}`, name: tag, platform: 'test' } },
  });
  assert.ok(done.status < 300, JSON.stringify(done.body));
  const shop = await call(base, 'POST', '/api/shop', { token: done.body.accessToken, body: { name: `دکانِ ${tag}` } });
  assert.ok(shop.status < 300, JSON.stringify(shop.body));
  const login = async (deviceId) => {
    const r = await call(base, 'POST', '/api/auth/login', { body: { identifier: email, password, app: 'shop', device: { deviceId, name: deviceId, platform: 'test' } } });
    assert.equal(r.status, 200, JSON.stringify(r.body));
    return r.body;
  };
  return { email, password, login };
}

/** سوکتِ زنده؛ پیام‌ها در صف، `next(pred)` منتظرِ نخستین پیامِ جور می‌ماند */
function socket(url) {
  const ws = new WebSocket(url);
  const got = [];
  const subs = new Set();
  let closed = null;
  ws.addEventListener('message', (e) => { let m; try { m = JSON.parse(e.data); } catch { return; } got.push(m); for (const s of subs) s(); });
  ws.addEventListener('close', (e) => { closed = { code: e.code, reason: e.reason }; for (const s of subs) s(); });
  //  دست‌دادنِ ردشده (۴۰۱) در WebSocket ِ Node فقط «error» می‌دهد
  ws.addEventListener('error', () => { closed ||= { code: 1006, refused: true }; for (const s of subs) s(); });
  const until = (pred, ms, what) => new Promise((resolve, reject) => {
    const check = () => { const v = pred(); if (v) { subs.delete(check); clearTimeout(t); resolve(v); } };
    const t = setTimeout(() => { subs.delete(check); reject(new Error(`${what}: ${ms}ms گذشت؛ رسیده: ${JSON.stringify(got)}، بسته: ${JSON.stringify(closed)}`)); }, ms);
    subs.add(check);
    check();
  });
  return {
    ws,
    next: (pred, ms = 5000) => until(() => got.find(pred), ms, 'پیام نرسید'),
    closedWith: (ms = 5000) => until(() => closed, ms, 'سوکت بسته نشد'),
    close: () => { try { ws.close(); } catch { /* بسته */ } },
  };
}
const wsUrl = (base, q) => `${base.replace('http', 'ws')}/api/sync/v1/live?${q}&app=shop`;

let A; let B;
test.before(async () => {
  if (PGLITE) return;
  //  پایگاهِ تازه (پاک + همهٔ مهاجرت‌ها) پیش از دو نمونه؛ این فرآیند پس از آن به پایگاه دست نمی‌زند
  const h = require('./helpers');
  await h.resetDatabase();
  await require('../src/db').closeDb();
  //  هم‌زمان بالا می‌آیند — همان چیزی که با چند نمونه پشتِ load balancer رخ می‌دهد
  A = spawnChild('a');
  B = spawnChild('b');
  const [ra, rb] = await Promise.all([A.ready, B.ready]);
  A.base = ra.base;
  B.base = rb.base;
});
test.after(async () => {
  for (const k of kids) { try { k.child.kill('SIGKILL'); } catch { /* رفته */ } }
});

test('سقفِ نرخ میانِ دو نمونه مشترک است (انبارِ pg)', { skip: PGLITE && 'فقط PostgreSQL' }, async () => {
  const ip = '198.51.100.7';
  const codes = [];
  for (let i = 0; i < GENERAL_MAX; i++) codes.push((await call(i % 2 ? B.base : A.base, 'GET', '/api/health', { ip })).status);
  assert.deepEqual(codes, Array(GENERAL_MAX).fill(200), 'تا سقف، از هر دو نمونه');
  const overA = await call(A.base, 'GET', '/api/health', { ip });
  const overB = await call(B.base, 'GET', '/api/health', { ip });
  assert.equal(overA.status, 429, 'نمونهٔ اول: روی هم از سقف گذشت');
  assert.equal(overB.status, 429, 'نمونهٔ دوم هم همان سطل را می‌بیند');
  assert.ok(Number(overB.headers.get('retry-after')) > 0, 'Retry-After');
  //  IP ِ دیگر سطلِ خودش را دارد
  assert.equal((await call(B.base, 'GET', '/api/health', { ip: '198.51.100.8' })).status, 200);
});

test('رله، بلیت و بستنِ سوکت از نمونه‌ای به نمونهٔ دیگر', { skip: PGLITE && 'فقط PostgreSQL' }, async () => {
  const owner = await shopOwner(A.base, 'live');
  const desk = await owner.login('desk-b');
  const phone = await owner.login('phone-a');

  //  بلیت روی A، سوکت روی B ⇒ پذیرفته؛ همان بلیت دوباره (روی A) ⇒ ۴۰۱
  const tk = await call(A.base, 'POST', '/api/sync/v1/live/ticket', { token: desk.accessToken, headers: { 'X-Device': 'desk-b' } });
  assert.equal(tk.status, 200, JSON.stringify(tk.body));
  const s = socket(wsUrl(B.base, `ticket=${encodeURIComponent(tk.body.ticket)}&device_id=desk-b`));
  await s.next((m) => m.event === 'hello');
  const again = socket(wsUrl(A.base, `ticket=${encodeURIComponent(tk.body.ticket)}&device_id=desk-b`));
  const refused = await again.closedWith();
  assert.equal(refused.code, 1006, `بلیتِ مصرف‌شده در نمونهٔ دیگر هم پذیرفته نمی‌شود: ${JSON.stringify(refused)}`);

  //  عکسِ کالا روی A ⇒ رلهٔ «photos» به سوکتِ دستگاهِ دیگر روی B
  const jpeg = Buffer.concat([Buffer.from([0xff, 0xd8, 0xff, 0xe0]), Buffer.alloc(200, 7)]).toString('base64');
  const put = await call(A.base, 'PUT', '/api/sync/v1/photos/p-1', { token: phone.accessToken, headers: { 'X-Device': 'phone-a' }, body: { data: jpeg, device_id: 'phone-a' } });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const msg = await s.next((m) => m.event === 'photos');
  assert.equal(msg.rev, put.body.rev);

  //  خروجِ همین نشست روی A ⇒ سوکتش روی B همان دم بسته (لایهٔ ۱؛ نه تپشِ ۳۰ثانیه‌ای)
  const t0 = Date.now();
  const out = await call(A.base, 'POST', '/api/auth/logout', { token: desk.accessToken, body: { refreshToken: desk.refreshToken } });
  assert.ok(out.status < 300, JSON.stringify(out.body));
  const closed = await s.closedWith(5000);
  assert.equal(closed.code, 4401, JSON.stringify(closed));
  assert.ok(Date.now() - t0 < 5000);
});

test('بیدارباشِ long-poll ِ چت از نمونهٔ دیگر', { skip: PGLITE && 'فقط PostgreSQL' }, async () => {
  const conv = `conv-${Date.now()}`;
  const listening = new Promise((resolve) => {
    const onMsg = (m) => { if (m.type === 'listening') { B.child.off('message', onMsg); resolve(); } };
    B.child.on('message', onMsg);
  });
  const waited = B.ask({ type: 'chatWait', conv, ms: 8000 });
  await listening;
  await A.ask({ type: 'chatNotify', conv });
  const r = await waited;
  assert.equal(r.woke, true, JSON.stringify(r));
  assert.ok(r.ms < 4000, `بیدارباش ${r.ms}ms`);
});

test('گذرگاه پس از کشته شدنِ اتصالش دوباره وصل می‌شود و خبر باز می‌رسد', { skip: PGLITE && 'فقط PostgreSQL' }, async () => {
  const { Client } = require('pg');
  const admin = new Client({ connectionString: process.env.DATABASE_URL });
  await admin.connect();
  try {
    const before = await B.ask({ type: 'stats' });
    //  هر دو اتصالِ خوشهٔ B (شنونده و قفلِ رهبر) — همان قطعِ شبکه یا راه‌اندازیِ دوبارهٔ PostgreSQL
    const r = await admin.query("SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE application_name = 'shop-cluster:test-b'");
    assert.ok(r.rowCount >= 1, 'اتصالِ خوشهٔ B پیدا نشد');
    const t0 = Date.now();
    let st;
    for (;;) {
      st = await B.ask({ type: 'stats' });
      if ((st.listening && st.reconnects > before.reconnects) || Date.now() - t0 > 10_000) break;
      await new Promise((res) => setTimeout(res, 100));
    }
    assert.ok(st.listening && st.reconnects > before.reconnects, `دوباره وصل نشد: ${JSON.stringify(st)}`);
    //  و همان خبر (بیدارباشِ چت) از A دوباره به B می‌رسد
    const conv = `conv-re-${Date.now()}`;
    const listening = new Promise((resolve) => {
      const onMsg = (m) => { if (m.type === 'listening') { B.child.off('message', onMsg); resolve(); } };
      B.child.on('message', onMsg);
    });
    const waited = B.ask({ type: 'chatWait', conv, ms: 8000 });
    await listening;
    await A.ask({ type: 'chatNotify', conv });
    const w = await waited;
    assert.equal(w.woke, true);
    assert.ok(w.ms < 4000, `بیدارباش ${w.ms}ms`);
    //  یک رهبر می‌ماند (B اگر رهبر بود رها کرد؛ یکی دوباره می‌گیرد)
    const t1 = Date.now();
    let leaders = 0;
    while (Date.now() - t1 < 10_000) {
      const [sa, sb] = await Promise.all([A.ask({ type: 'stats' }), B.ask({ type: 'stats' })]);
      leaders = [sa.leader, sb.leader].filter(Boolean).length;
      if (leaders === 1) break;
      await new Promise((res) => setTimeout(res, 100));
    }
    assert.equal(leaders, 1, 'دقیقاً یک رهبر پس از قطع');
  } finally {
    await admin.end();
  }
});

/** تا `pred(health)` راست شود (تپشِ ۲۵۰ms) — یا آخرین پاسخ */
async function healthUntil(kid, pred, ms = 10_000) {
  const t0 = Date.now();
  let hl;
  for (;;) {
    hl = await kid.ask({ type: 'health' });
    if (pred(hl) || Date.now() - t0 > ms) return hl;
    await new Promise((r) => setTimeout(r, 150));
  }
}

test('پنلِ ۴.۹ از هر دو نمونه: هر دو نمونه، رهبرِ قفل و کارهای رهبر (روی پیرو هم، نه «نامعلوم»)', { skip: PGLITE && 'فقط PostgreSQL' }, async () => {
  const [sa, sb] = await Promise.all([A.ask({ type: 'stats' }), B.ask({ type: 'stats' })]);
  const leaderId = sa.leader ? sa.instanceId : sb.instanceId;
  for (const kid of [A, B]) {
    const hl = await healthUntil(kid, (x) => x.cluster && x.cluster.instances.filter((i) => i.status === 'ok').length === 2);
    const c = hl.cluster;
    assert.ok(c, `نمای خوشه نیست: ${JSON.stringify(hl.parts)}`);
    assert.deepEqual(c.instances.map((i) => i.instanceId).sort(), ['test-a', 'test-b']);
    assert.equal(c.instances.filter((i) => i.self).length, 1);
    assert.equal(c.leader && c.leader.instanceId, leaderId, JSON.stringify(c.leader));
    assert.equal(c.leader.source, 'lock', 'رهبر از دارندهٔ واقعیِ قفل');
    assert.equal(c.status, 'ok');
    assert.equal(hl.parts.cluster, 'ok');
    //  کارهای بالای پاسخ همان کارهای رهبرند، حتی روی پیرو
    const jobs = Object.fromEntries(hl.jobs.map((j) => [j.name, j]));
    for (const j of ['login-outbox', 'order-notify', 'order-timeout', 'owner-report']) {
      assert.ok(jobs[j], `کارِ ${j}`);
      assert.equal(jobs[j].instanceId, leaderId, j);
      assert.notEqual(jobs[j].status, 'unknown', `${j} روی ${kid.name} «نامعلوم» است`);
    }
    //  هر نمونه برشِ خودش را هم در فهرست دارد (پیروِ تازه‌کنارگذاشته تا مهلتِ هر کار «سالم» ِ پیشین را نشان می‌دهد)
    assert.ok(c.instances.every((i) => Array.isArray(i.jobs) && i.jobs.length > 0));
    assert.ok(!JSON.stringify(c).includes(process.env.TEST_DATABASE_URL || 'shoppass'), 'بی نشانیِ پایگاه/راز');
  }
});

test('کارگرهای پس‌زمینه فقط روی رهبر؛ رهبر بیفتد ⇒ دیگری', { skip: PGLITE && 'فقط PostgreSQL' }, async () => {
  const [sa, sb] = await Promise.all([A.ask({ type: 'stats' }), B.ask({ type: 'stats' })]);
  assert.equal([sa.leader, sb.leader].filter(Boolean).length, 1, `دقیقاً یک رهبر: ${JSON.stringify([sa, sb])}`);
  const [leader, follower] = sa.leader ? [A, B] : [B, A];
  const ls = sa.leader ? sa : sb;
  const fs0 = sa.leader ? sb : sa;
  assert.deepEqual(fs0.jobs, [], 'پیرو هیچ کارگری ندارد');
  for (const j of ['login-outbox', 'order-notify', 'order-timeout', 'owner-report', 'notices', 'ops-paging', 'backup']) {
    assert.ok(ls.jobs.includes(j), `کارگرِ ${j} روی رهبر`);
  }
  //  رهبر می‌میرد (بی خاموشیِ تمیز) ⇒ پایگاه قفلِ نشستش را رها می‌کند ⇒ پیرو رهبر می‌شود
  leader.child.kill('SIGKILL');
  const t0 = Date.now();
  let st;
  for (;;) {
    st = await follower.ask({ type: 'stats' });
    if (st.leader || Date.now() - t0 > 10_000) break;
    await new Promise((r) => setTimeout(r, 100));
  }
  assert.equal(st.leader, true, `پیرو رهبر نشد: ${JSON.stringify(st)}`);
  assert.deepEqual(st.jobs, ls.jobs, 'همان کارگرها');
  assert.ok(Date.now() - t0 < 10_000);
});

test('پس از افتادنِ رهبر: پنل رهبرِ تازه را از قفل می‌بیند و نمونهٔ کشته «کهنه» و هشدار', { skip: PGLITE && 'فقط PostgreSQL' }, async () => {
  //  آزمونِ بالا رهبر را کشت؛ نمونهٔ زنده همان پیروِ پیشین است
  const alive = [A, B].find((k) => k.child.exitCode === null && !k.child.killed);
  assert.ok(alive, 'یک نمونه زنده است');
  const self = (await alive.ask({ type: 'stats' })).instanceId;
  const hl = await healthUntil(alive, (x) => x.cluster && x.cluster.leader && x.cluster.leader.instanceId === self
    && x.cluster.instances.some((i) => i.instanceId !== self && i.status === 'stale'), 15_000);
  const c = hl.cluster;
  assert.equal(c.leader && c.leader.instanceId, self, JSON.stringify(c));
  const dead = c.instances.find((i) => i.instanceId !== self);
  assert.equal(dead.status, 'stale', JSON.stringify(dead));
  assert.equal(c.status, 'warn', 'نمونهٔ بی‌تپش ⇒ هشدار تا رهبر ردیفش را بردارد');
  assert.equal(hl.parts.cluster, 'warn');
  assert.ok(hl.jobs.every((j) => j.instanceId === self), 'کارها حالا از رهبرِ تازه');
});
