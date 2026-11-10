'use strict';
/**
 * SYN-12 (§۶.۱۲، §۴.۱) — همگام‌سازی چرا نرسید: شبکهٔ خودِ دستگاه، سرور، راهِ میانِ این دو (قطعیِ سراسری) یا نشست،
 * و «هشدارِ بی‌مورد نه». منطق یکی است: `SyncOutage` ِ core (Kotlin) و `outageOf`/`outageStreak`/`outageFrom`/`dotOf`
 * ِ `license/sync-core.js`، هر دو با `core/test-vectors/sync-outage-v1.json` (همان که `SyncOutageVectorsTest` می‌خواند).
 *
 *   ۰) بردارها: classify، رشتهٔ شکست، حکم از رشته + lastError، چراغ
 *   ۱) همان عددها و کدها در SyncOutage.kt (Kotlin این‌جا ساخته نمی‌شود؛ متنِ منبع سنجیده می‌شود)
 *   ۲) موتورِ v3 ِ وب: قطعیِ کوتاه ⇒ «دوباره می‌کوشد»، ماندگار ⇒ «به سرور نمی‌رسیم» / «سرور»؛ ۴۰۱ ⇒ نشست؛ موفق ⇒ ok —
 *      و صف، بدنهٔ push و فاصلهٔ تلاش همان پیشین
 *   ۳) موتورِ v1 ِ وب (sync-engine.js در vm): همان حکم‌ها روی status() و چراغ؛ sync-core ِ کهنه ⇒ همان رفتارِ پیشین
 *
 * بی پایگاه و بی سرور.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const ROOT = path.join(__dirname, '..', '..');
const core = require(path.join(ROOT, 'license', 'sync-core.js'));
const V3 = require(path.join(ROOT, 'license', 'sync-v3-engine.js'));
const VECTORS = JSON.parse(fs.readFileSync(path.join(ROOT, 'core', 'test-vectors', 'sync-outage-v1.json'), 'utf8'));
const KT = fs.readFileSync(path.join(ROOT, 'core', 'src', 'commonMain', 'kotlin', 'ir', 'vil3ntec', 'tohid', 'core', 'sync', 'SyncOutage.kt'), 'utf8');
const ENGINE_SRC = fs.readFileSync(path.join(ROOT, 'license', 'sync-engine.js'), 'utf8');

/* ══════════════════════════ ۰) بردارها ══════════════════════════ */

test('۰) بردارها: classify ⇒ همان kind و level', () => {
  assert.ok(VECTORS.classify.length >= 25);
  for (const r of VECTORS.classify) assert.deepEqual(core.outageOf(r), { kind: r.kind, level: r.level }, r.name);
});

test('۰) بردارها: رشتهٔ شکست فقط وقتی دستگاه هنوز در همان شکست بود ادامه می‌یابد', () => {
  assert.ok(VECTORS.streak.length >= 5);
  for (const r of VECTORS.streak) {
    const prev = r.prevSince < 0 ? null : { status: 0, code: 'network', error: r.prevError, since: r.prevSince };
    assert.deepEqual(core.outageStreak(prev, r.before, r.status, r.code, r.error, r.now),
      { status: r.status, code: r.code, error: r.error, since: r.since }, r.name);
  }
});

test('۰) بردارها: حکم از رشتهٔ در حافظه و lastError ِ ماندگار', () => {
  assert.ok(VECTORS.of.length >= 9);
  for (const r of VECTORS.of) {
    const s = r.since < 0 ? null : { status: r.sStatus, code: r.sCode, error: r.sError, since: r.since };
    assert.deepEqual(core.outageFrom(s, r.lastError, r.online, r.failures, r.now), { kind: r.kind, level: r.level }, r.name);
  }
});

test('۰) بردارها: چراغ — سرخ فقط با هشدار، شکست هرگز سبز نیست؛ بی `alarm` همان چراغِ پیشین', () => {
  assert.ok(VECTORS.dot.length >= 10);
  for (const r of VECTORS.dot) {
    assert.equal(core.dotOf(r), r.dot, r.name);
    if (r.alarm) {
      const { alarm, ...old } = r;
      assert.equal(core.dotOf(old), r.dot, `${r.name} (بی alarm)`);
    }
  }
});

/* ══════════════════════════ ۱) همان عددها در Kotlin ══════════════════════════ */

test('۱) SyncOutage.kt همان سقف‌ها، کدها و دسته‌های وضعیت را دارد', () => {
  const num = (name) => Number(new RegExp(`const val ${name}: \\w+ = (-?[\\d_]+)L?`).exec(KT)[1].replace(/_/g, ''));
  assert.equal(num('SUSTAIN_FAILURES'), VECTORS.sustainFailures);
  assert.equal(num('SUSTAIN_MS'), VECTORS.sustainMs);
  assert.equal(core.OUTAGE_SUSTAIN_FAILURES, VECTORS.sustainFailures);
  assert.equal(core.OUTAGE_SUSTAIN_MS, VECTORS.sustainMs);
  assert.equal(num('NO_RESPONSE'), 0);
  assert.equal(num('LOCAL'), -1);
  const set = (name) => [...new RegExp(`${name} = setOf\\(([^)]*)\\)`).exec(KT)[1].matchAll(/"([^"]+)"/g)].map((m) => m[1]).sort();
  assert.deepEqual(set('AUTH_CODES'), ['no_session', 'session_expired', 'session_required']);
  assert.deepEqual(set('OFFLINE_CODES'), ['offline']);
  assert.match(KT, /fun network\(status: Int\): Boolean = status == NO_RESPONSE \|\| status == 408/);
  assert.match(KT, /fun serverSide\(status: Int\): Boolean = status == 429 \|\| status in 500\.\.599/);
  for (const k of ['ok', 'offline', 'retrying', 'unreachable', 'server', 'auth', 'error']) assert.match(KT, new RegExp(`\\("${k}"\\)`), k);
  //  همان کدها در JS: کدِ نشست بی کدِ HTTP و کدِ offline
  for (const code of ['no_session', 'session_expired', 'session_required']) {
    assert.equal(core.outageOf({ online: true, failed: true, status: 0, code, failures: 1, streakMs: 0 }).kind, 'auth', code);
  }
  assert.equal(core.outageOf({ online: true, failed: true, status: 0, code: 'offline', failures: 9, streakMs: 1e6 }).kind, 'offline');
  //  فقط JS: کدِ HTTP ِ نبوده (خطای غیرِ ApiError) = محلی ⇒ همان سرخ، نه «بی پاسخ»
  assert.equal(core.outageOf({ online: true, failed: true, failures: 9, streakMs: 1e6 }).kind, 'error');
  assert.equal(core.outageStreak(null, '', undefined, '', 'x', 5).status, -1);
});

/* ══════════════════════════ ۲) موتورِ v3 ِ وب ══════════════════════════ */

function ledgerOf(data = {}) {
  let state = core.cloneLedger(data);
  return { read: () => state, write: (d) => { state = core.cloneLedger(d); } };
}

/** انتقالِ ساختگی: down ⇒ پرتاب (بی پاسخ)؛ http ⇒ همان کد؛ up ⇒ پاسخِ درست */
function fakeTransport() {
  const t = {
    mode: 'down', status: 503, code: 'db_busy', pushes: [],
    async request(method, p, body) {
      const push = p === '/api/sync/v3/push';
      if (push) t.pushes.push(JSON.stringify(body.ops));
      if (t.mode === 'down') throw new Error('به سرور نرسیدیم');
      if (t.mode === 'http') return { status: t.status, body: { error: { code: t.code, message: `خطای سرور (${t.status})` } } };
      if (push) return { status: 200, body: { results: body.ops.map((o) => ({ op_id: o.op_id, status: 'applied', server_seq: 1 })) } };
      return { status: 200, body: { cursor: 1, ops: [], has_more: false } };
    },
  };
  return t;
}

async function v3Device() {
  let clock = 1_700_000_000_000;
  const tr = fakeTransport();
  const ledger = ledgerOf({ products: [{ id: 'p-1', name: 'چای', price: 100 }] });
  const engine = new V3.Engine({ transport: tr, store: new V3.MemoryStore(), ledger, deviceId: 'web-outage', random: () => 0.5, now: () => clock });
  await engine.load();
  //  همگام‌شده با سرور (سایه = دفتر)؛ از این‌جا هر تغییر یک op ِ صف است
  await engine.locked(function () { return this.persist({ initialized: true, shadow: core.shadowOf(ledger.read()) }); });
  return { tr, ledger, engine, tick: (ms) => { clock += ms; } };
}

test('۲) v3 ِ وب: قطعیِ کوتاه بی هشدار، ماندگار ⇒ «به سرور نمی‌رسیم»، بی‌نت ⇒ آفلاین؛ صف و فاصلهٔ تلاش همان', async () => {
  const d = await v3Device();
  assert.deepEqual(d.engine.outage(true), { kind: 'ok', level: 'none' });
  //  یک تغییر در صف — همان op در همهٔ تلاش‌ها
  d.ledger.write({ products: [{ id: 'p-1', name: 'چای', price: 120 }] });
  await d.engine.record();
  const queued = d.engine.queued();
  assert.ok(queued > 0);

  const first = await d.engine.pushOnce();
  assert.equal(first.ok, false);
  assert.equal(first.retryInMs, core.backoffMs(0, 0.5), 'فاصلهٔ تلاش همان فرمول');
  assert.deepEqual(d.engine.outage(true), { kind: 'retrying', level: 'none' }, 'نخستین شکست هشدار نیست');
  assert.ok(d.engine.status().lastError, 'خطا پنهان نمی‌شود (SYN-08)');

  d.tick(30_000);
  const second = await d.engine.pushOnce();
  assert.equal(second.retryInMs, core.backoffMs(1, 0.5));
  assert.equal(d.engine.outage(true).kind, 'retrying', '۳۰ ثانیه هنوز کوتاه است');

  d.tick(31_000);
  assert.deepEqual(d.engine.outage(true), { kind: 'unreachable', level: 'notice' }, '۶۱ ثانیه و دو شکست ⇒ قطعی');
  assert.equal(d.engine.outage(false).kind, 'offline', 'همان شکست روی دستگاهِ بی‌نت ⇒ آفلاین');

  //  سرور پاسخ داد ولی ۵۰۳: همان رشته (دستگاه هنوز در شکست) ⇒ «سرور»
  d.tr.mode = 'http';
  const third = await d.engine.pushOnce();
  assert.equal(third.status, 503);
  assert.equal(third.retryInMs, core.backoffMs(2, 0.5));
  assert.deepEqual(d.engine.outage(true), { kind: 'server', level: 'notice' });

  assert.equal(d.engine.queued(), queued, 'صف دست نخورد');
  assert.equal(new Set(d.tr.pushes).size, 1, 'همان opها در هر تلاش');

  d.tr.mode = 'up';
  const ok = await d.engine.pushOnce();
  assert.equal(ok.ok, true);
  assert.equal(d.tr.pushes.at(-1), d.tr.pushes[0], 'همان بدنه پس از برگشتن');
  assert.deepEqual(d.engine.outage(true), { kind: 'ok', level: 'none' });

  //  شکستِ پس از دورِ موفق رشتهٔ تازه است
  d.tr.mode = 'down';
  d.tick(600_000);
  await d.engine.pullOnce();
  assert.equal(d.engine.outage(true).kind, 'retrying', 'رشتهٔ تازه، نه ادامهٔ رشتهٔ پیشین');
});

test('۲) v3 ِ وب: ۴۰۱ ⇒ نشست (سرخ) همان دم؛ ۴۰۰ ⇒ خطا؛ INCOMPLETE ِ نویسندهٔ دیگر ⇒ همان سرخِ پیشین', async () => {
  const d = await v3Device();
  d.tr.mode = 'http';
  d.tr.status = 401; d.tr.code = 'session_required';
  const r = await d.engine.pullOnce();
  assert.equal(r.stopped, true);
  assert.deepEqual(d.engine.outage(true), { kind: 'auth', level: 'alarm' });
  assert.equal(d.engine.outage(false).kind, 'auth', 'نشست بالاتر از بی‌نت');

  d.tr.status = 400; d.tr.code = 'bad_request';
  await d.engine.pullOnce();
  assert.deepEqual(d.engine.outage(true), { kind: 'error', level: 'alarm' });

  //  خطایی که موتور جای دیگری نوشت (نه از رشته) ⇒ ناشناخته ⇒ سرخ
  await d.engine.locked(function () { return this.persist({ lastError: 'سرور پس از بازیابی هنوز دفتر را آماده نکرده است', lastErrorAt: 1 }); });
  assert.equal(d.engine.outage(true).kind, 'error');
});

test('۲) v3 ِ وب: نمای TohidSync (`install`) حکم و چراغ را از همان موتور می‌گیرد', async () => {
  //  همان facadeStatus: outage + dot با alarm
  const d = await v3Device();
  await d.engine.pullOnce();
  d.tick(61_000);
  await d.engine.pullOnce();
  const st = d.engine.status();
  const o = d.engine.outage(true);
  assert.equal(o.kind, 'unreachable');
  assert.equal(core.dotOf({ error: !!st.lastError, alarm: o.level === 'alarm', online: true, signedIn: true, configured: true, queued: 0, busy: false }), 'yellow');
  assert.equal(core.dotOf({ error: !!st.lastError, online: true, signedIn: true, configured: true, queued: 0, busy: false }), 'red', 'پیش از SYN-12 سرخ بود');
  //  متنِ facadeStatus در منبع: outage و alarm کنارِ dot
  const src = fs.readFileSync(path.join(ROOT, 'license', 'sync-v3-engine.js'), 'utf8');
  assert.match(src, /outage = engine \? engine\.outage\(online\) : null;/);
  assert.match(src, /error: !!st\.lastError, alarm: outage \? outage\.level === 'alarm' : true, online: online,/);
});

/* ══════════════════════════ ۳) موتورِ v1 ِ وب ══════════════════════════ */

function storage() {
  const box = new Map();
  return { getItem: (k) => (box.has(k) ? box.get(k) : null), setItem: (k, v) => { box.set(k, String(v)); }, removeItem: (k) => box.delete(k) };
}

const apiError = (message, code, status) => Object.assign(new Error(message), { code, status });

/** مرورگر: sync-engine.js ِ واقعی در vm؛ `next()` خطای درخواستِ بعدی را می‌دهد (null ⇒ پاسخِ درست) */
function webV1({ coreImpl = core } = {}) {
  const box = { now: 1_700_000_000_000, error: null };
  const navigator = { onLine: true };
  const window = {
    TohidSyncCore: coreImpl,
    TohidApiConfig: { baseUrl: () => 'http://127.0.0.1' },
    TohidAccount: {
      configured: () => true, isSignedIn: () => true, account: () => ({ userId: 'u-outage' }),
      deviceId: () => 'web-outage', appId: () => 'shop', on() { }, heartbeat: async () => ({}),
      accessToken: async () => 'tok',
      async authFetch(p) {
        if (box.error) throw box.error;
        if (p.startsWith('/api/sync/v1/pull')) return { ops: [], cursor: 0, has_more: false };
        return {};
      },
    },
    addEventListener() { }, dispatchEvent() { },
  };
  const ctx = vm.createContext({
    window, localStorage: storage(), navigator, document: { addEventListener() { } }, URL, console,
    setTimeout: () => 0, clearTimeout() { }, setInterval: () => 0, clearInterval() { }, __now: () => box.now,
  });
  vm.runInContext('Date.now = function () { return __now(); };', ctx);
  vm.runInContext(ENGINE_SRC, ctx);
  const sync = window.TohidSync;
  sync.bind({ read: () => ({ products: [] }), write: () => { } });
  return { sync, box, navigator, failWith: (e) => { box.error = e; }, tick: (ms) => { box.now += ms; } };
}

test('۳) v1 ِ وب: قطعیِ کوتاه زرد، ماندگار «به سرور نمی‌رسیم»، بی‌نت خاکستری، سرور، نشست سرخ، موفق سبز', async () => {
  const w = webV1();
  w.failWith(apiError('به سرور نرسیدیم', 'network', 0));
  await w.sync.sync();
  let st = w.sync.status();
  assert.equal(st.lastError, 'به سرور نرسیدیم', 'خطای آخر پیداست (SYN-08)');
  assert.deepEqual(st.outage, { kind: 'retrying', level: 'none' });
  assert.equal(st.dot, 'yellow', 'نخستین شکستِ شبکه دیگر سرخ نیست');

  w.tick(61_000);
  await w.sync.sync();
  st = w.sync.status();
  assert.deepEqual(st.outage, { kind: 'unreachable', level: 'notice' });
  assert.equal(st.dot, 'yellow');

  w.navigator.onLine = false;
  st = w.sync.status();
  assert.equal(st.outage.kind, 'offline');
  assert.equal(st.dot, 'grey');
  w.navigator.onLine = true;

  w.failWith(apiError('سرور الان شلوغ است', 'db_busy', 503));
  await w.sync.sync();
  st = w.sync.status();
  assert.deepEqual(st.outage, { kind: 'server', level: 'notice' }, 'هنوز در همان شکست ⇒ ماندگار');

  w.failWith(apiError('دوباره وارد شوید', 'session_required', 401));
  await w.sync.sync();
  st = w.sync.status();
  assert.deepEqual(st.outage, { kind: 'auth', level: 'alarm' });
  assert.equal(st.dot, 'red');

  w.failWith(null);
  await w.sync.sync();
  st = w.sync.status();
  assert.equal(st.lastError, '');
  assert.deepEqual(st.outage, { kind: 'ok', level: 'none' });
  assert.equal(st.dot, 'green');

  //  خطای غیرِ شبکه (بی کدِ HTTP) ⇒ همان سرخ
  w.failWith(new TypeError('boom'));
  await w.sync.sync();
  st = w.sync.status();
  assert.equal(st.outage.kind, 'error');
  assert.equal(st.dot, 'red');
});

test('۳) v1 ِ وب: sync-core ِ کهنه (بی outage*) ⇒ بی خطا و همان چراغِ پیشین', async () => {
  const old = { ...core };
  delete old.outageFrom; delete old.outageStreak; delete old.outageOf;
  const w = webV1({ coreImpl: old });
  w.failWith(apiError('به سرور نرسیدیم', 'network', 0));
  await w.sync.sync();
  const st = w.sync.status();
  assert.equal(st.outage, null);
  assert.equal(st.dot, 'red');
});
