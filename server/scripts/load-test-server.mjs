#!/usr/bin/env node
/**
 * سرورِ آزمونِ بار (PERF-02/PERF-04) — فرآیندِ **جدا** از بارساز (`load-test.mjs`).
 *
 * خودِ `createApp`ِ سرور (همان کدِ تولید، با keepAlive و headersTimeoutِ `index.js` و همان
 * کارگرهای پس‌زمینه)، روی پایگاهِ آزمونی که **پاک می‌شود**؛ سپس دادهٔ ساختگی می‌کارد
 * و نشانی‌ها/توکن‌ها را با IPC به پدر می‌دهد. پدر هر چند ثانیه حافظه، استخرِ اتصال و
 * تأخیرِ حلقهٔ رویداد را از همین‌جا می‌پرسد (`{type:'stats'}`).
 *
 * فقط از راهِ `load-test.mjs` اجرا شود (پیکربندی در `LOAD_CFG`ِ JSON).
 * ⛔ هیچ دادهٔ واقعی‌ای این‌جا نیست: دفترها از `bench/datagen.mjs` (seed ثابت) می‌آیند.
 */
import { createRequire } from 'node:module';
import fs from 'node:fs';
import path from 'node:path';
import { monitorEventLoopDelay } from 'node:perf_hooks';
import { AsyncLocalStorage } from 'node:async_hooks';
import { makeReference, syncRowsOf, REFERENCE } from '../../bench/datagen.mjs';

//  خطای کاشتن پارامترهای چندمگابایتی را چاپ نکند — فقط پیام و قید
process.on('uncaughtException', (e) => { console.error(`سرورِ آزمونِ بار: ${e && e.message}${e && e.constraint ? ` (${e.constraint})` : ''}`); process.exit(1); });
process.on('unhandledRejection', (e) => { console.error(`سرورِ آزمونِ بار: ${e && e.message}${e && e.constraint ? ` (${e.constraint})` : ''}`); process.exit(1); });
if (!process.send) { console.error('این پرونده را load-test.mjs اجرا می‌کند (IPC لازم است)'); process.exit(2); }
const CFG = JSON.parse(process.env.LOAD_CFG || '{}');
const HUGE = '100000000';
process.env.TEST_DATABASE_URL = CFG.db;
//  سقف‌های نرخ به‌ازای IP و حساب: همهٔ بار از یک ماشین می‌آید؛ خودِ سقف‌ها آزمون‌های خودشان را دارند
for (const k of ['RATE_GENERAL_MAX', 'RATE_AUTH_MAX', 'RATE_SESSION_MAX', 'RATE_SYNC_MAX', 'RATE_SYNC_IP_MAX', 'RATE_ADMIN_MAX',
  'RATE_STORE_CHECKOUT_MAX', 'RATE_VERIFY_MAX', 'RATE_OTP_MAX', 'RATE_JOIN_MAX', 'RATE_PROBE_IP_MAX', 'RATE_PASSWORD_SESSION_MAX']) process.env[k] = HUGE;
process.env.LOGIN_LOCKOUT_TRIES = HUGE;
process.env.LOGIN_LOCKOUT_GLOBAL_TRIES = HUGE;
process.env.SYNC_V3_ENABLED = '1';
//  هزینهٔ scryptِ تولید (p=5)، نه p=1ِ آزمون‌ها — ورود باید همان هزینهٔ واقعی را بدهد
process.env.PASSWORD_SCRYPT_P = String(CFG.scryptP || 5);
for (const [k, v] of Object.entries(CFG.serverEnv || {})) process.env[k] = String(v);

//  سطرِ «GET /api/… 200 3ms» را سرور بیرون از production برای هر درخواست می‌نویسد؛ در تولید
//  فقط کندتر از ۱ ثانیه یا خطا — همان رفتارِ تولید (و بی هزینهٔ نوشتنِ هزاران سطر در لوله)
const log = console.log.bind(console);
let slowLines = 0;
console.log = (...a) => {
  const m = typeof a[0] === 'string' && /^(GET|POST|PUT|PATCH|DELETE) \/\S* (\d{3}) (\d+)ms$/.exec(a[0]);
  if (m && Number(m[2]) < 400 && Number(m[3]) <= 1000) return;
  if (typeof a[0] === 'string' && a[0].startsWith('[otp]')) return;
  if (m) { slowLines++; if (slowLines > 50) return; }
  log(...a);
};

const require = createRequire(import.meta.url);
const SERVER = path.join(import.meta.dirname, '..');
const h = require(path.join(SERVER, 'test', 'helpers.js'));
const db = require(path.join(SERVER, 'src', 'db.js'));
const migrations = require(path.join(SERVER, 'src', 'lib', 'sync-v1-migrations.js'));
const pw = require(path.join(SERVER, 'src', 'lib', 'password.js'));
const gate = require(path.join(SERVER, 'src', 'lib', 'sync-v3-gate.js'));

/*
 *  `--query-stats` (فقط PostgreSQL): زمانِ هر پرس‌وجو به‌ازای متنِ SQL (پارامترها جدا) — همان کارِ pg_stat_statements که
 *  روی پایگاهِ مشترک بی preload و بی راه‌اندازیِ دوباره ممکن نیست. زمان = فرستادن تا پاسخ روی همان اتصال (انتظارِ قفل هم).
 */
const qstats = new Map();
if (CFG.queryStats && !db.isPglite()) {
  const { Client } = require('pg');
  const orig = Client.prototype.query;
  const note = (text, ms, err) => {
    try {
      const k = String(text).replace(/\s+/g, ' ').trim().slice(0, 400);
      let e = qstats.get(k);
      if (!e) qstats.set(k, e = { calls: 0, totalMs: 0, maxMs: 0, errors: 0 });
      e.calls++; e.totalMs += ms; if (ms > e.maxMs) e.maxMs = ms; if (err) e.errors++;
    } catch { /* سنجه هرگز پرس‌وجو را نمی‌اندازد */ }
  };
  Client.prototype.query = function (config, values, callback) {
    const text = typeof config === 'string' ? config : config && typeof config.submit !== 'function' ? config.text : null;
    if (!text) return orig.call(this, config, values, callback);
    const t0 = performance.now();
    const wrap = (cb) => (e, r) => { note(text, performance.now() - t0, e); cb(e, r); };
    if (typeof values === 'function') return orig.call(this, config, wrap(values));
    if (typeof callback === 'function') return orig.call(this, config, values, wrap(callback));
    const r = orig.call(this, config, values);
    if (r && typeof r.then === 'function') r.then(() => note(text, performance.now() - t0, null), (e) => note(text, performance.now() - t0, e));
    return r;
  };
}
function topQueries(n = 40) {
  const r2 = (v) => Math.round(v * 100) / 100;
  return [...qstats.entries()].sort((a, b) => b[1].totalMs - a[1].totalMs).slice(0, n)
    .map(([sql, e]) => ({ sql, calls: e.calls, totalMs: Math.round(e.totalMs), meanMs: r2(e.totalMs / e.calls), maxMs: r2(e.maxMs), errors: e.errors }));
}

/*
 *  زمانِ تراکنش‌های قفل‌دارِ Sync v3 (`lib/sync-v3-gate.js` ⇒ `lockedTx`): انتظار در دروازه (نوبتِ دکان + جای push)، گرفتنِ
 *  اتصال از استخر پس از دروازه، و اجرا — هر کدام جدا، برای push (`heavy`) و بقیه. فقط سنجه: همان `run` و همان `connect`،
 *  بی هیچ تغییری در رفتار (کدِ تولید نمی‌داند). `gatestats` در IPC.
 */
const inGate = new AsyncLocalStorage();
const gateTimes = { push: [], other: [] };
{
  const origRun = gate.run;
  gate.run = function run(key, heavy, fn) {
    const t0 = performance.now();
    const g = { tIn: 0, acquireMs: 0, acquireFailed: false };
    const done = () => {
      const end = performance.now();
      (heavy ? gateTimes.push : gateTimes.other).push([g.tIn ? g.tIn - t0 : end - t0, g.acquireMs, g.tIn ? end - g.tIn - g.acquireMs : 0, end - t0, g.tIn ? 1 : 0, g.acquireFailed ? 1 : 0]);
    };
    return origRun(key, heavy, (o) => { g.tIn = performance.now(); return inGate.run(g, () => fn(o)); }).then((r) => { done(); return r; }, (e) => { done(); throw e; });
  };
}
function gateSummary(xs) {
  const r1 = (v) => Math.round(v * 10) / 10;
  const st = (a) => { const s = Float64Array.from(a).sort(); const q = (p) => (s.length ? r1(s[Math.min(s.length - 1, Math.ceil(p * s.length) - 1)]) : null); return { p50: q(0.5), p95: q(0.95), max: s.length ? r1(s[s.length - 1]) : null }; };
  const adm = xs.filter((x) => x[4]);
  return { n: xs.length, admitted: adm.length, rejected: xs.length - adm.length, acquireFailed: adm.filter((x) => x[5]).length,
    wait: st(xs.map((x) => x[0])), acquire: st(adm.map((x) => x[1])), exec: st(adm.map((x) => x[2])), total: st(xs.map((x) => x[3])),
    over10s: xs.filter((x) => x[3] > 10_000).length, over15s: xs.filter((x) => x[3] > 15_000).length };
}

const loop = monitorEventLoopDelay({ resolution: 10 });
loop.enable();
let cpu0 = process.cpuUsage();
let t0 = performance.now();

//  PostgreSQL: چند اتصالِ این پایگاه پشتِ قفل منتظرند (advisory ِ همگام‌سازی، قفلِ سفارش) — از استخرِ
//  جدای یک‌اتصالی، تا وقتی استخرِ اصلی پر است هم جواب بدهد
const monitor = db.isPglite() ? null : db.extraPool(1);
async function pgActivity() {
  if (!monitor) return null;
  try {
    const r = await monitor.query(
      `SELECT count(*)::int AS total, count(*) FILTER (WHERE state = 'active')::int AS active,
              count(*) FILTER (WHERE wait_event_type = 'Lock')::int AS lock_wait,
              count(*) FILTER (WHERE state LIKE 'idle in transaction%')::int AS idle_in_tx
         FROM pg_stat_activity WHERE datname = current_database() AND pid <> pg_backend_pid()`);
    return r.rows[0];
  } catch { return null; }
}
async function stats({ gc = false } = {}) {
  if (gc && global.gc) { global.gc(); global.gc(); }
  const m = process.memoryUsage();
  const pool = db.isPglite() ? null : db.getPool();
  const cpu = process.cpuUsage(cpu0);
  const wall = performance.now() - t0;
  cpu0 = process.cpuUsage(); t0 = performance.now();
  const out = {
    at: Date.now(), gc: gc && !!global.gc,
    rssMB: +(m.rss / 2 ** 20).toFixed(1), heapMB: +(m.heapUsed / 2 ** 20).toFixed(1), externalMB: +(m.external / 2 ** 20).toFixed(1),
    cpuPct: wall > 0 ? Math.round(((cpu.user + cpu.system) / 1000 / wall) * 100) : 0,
    loopP99Ms: +(loop.percentile(99) / 1e6).toFixed(1), loopMaxMs: +(loop.max / 1e6).toFixed(1),
    pool: pool ? { total: pool.totalCount, idle: pool.idleCount, waiting: pool.waitingCount } : null,
    pg: await pgActivity(),
    //  دروازهٔ push ِ v3: منتظر (صفِ دکان + سقفِ push، بی اتصال) و push ِ دارای جا
    gate: (({ on, waiting, inflight, rejected, timedOut }) => ({ on, waiting, inflight, rejected, timedOut }))(gate.stats()),
    //  `--instances`: رهبرِ خوشه (کارگرها) و خبرهای گذرگاه
    cluster: cluster.enabled() ? (({ leader, published, received }) => ({ leader, published, received }))(cluster.stats()) : null,
    slowLines,
  };
  loop.reset();
  return out;
}

//  پدر رفت (کشته شد، Ctrl+C) ⇒ این سرور هم برود؛ یتیم نماند
process.on('disconnect', () => process.exit(0));
const tStart = performance.now();
const step = (label) => console.error(`  [سرورِ بار] ${label} — ${Math.round((performance.now() - tStart) / 1000)}s`);
const base = await h.start({ reset: !CFG.follower });
step(CFG.follower ? 'نمونهٔ دیگر روی همان پایگاه' : 'پایگاه و migrationها');
//  همان مهلت‌های `src/index.js` — بی آن keepAliveِ پیش‌فرضِ ۵ثانیه‌ای اتصال‌های بارساز را می‌بُرد
h.server().keepAliveTimeout = 65_000;
h.server().headersTimeout = 70_000;
//  گرفتنِ اتصال درونِ دروازه (همان `pool.connect` ِ promise ِ `db.tx`؛ گونهٔ callback ِ `pool.query` دست نمی‌خورد)
if (!db.isPglite()) {
  const pool = db.getPool();
  const orig = pool.connect.bind(pool);
  pool.connect = function connect(cb) {
    const g = inGate.getStore();
    if (typeof cb === 'function' || !g) return orig(cb);
    const t0 = performance.now();
    return orig().then((c) => { g.acquireMs += performance.now() - t0; return c; }, (e) => { g.acquireMs += performance.now() - t0; g.acquireFailed = true; throw e; });
  };
}
if (!CFG.follower) await require(path.join(SERVER, 'src', 'lib', 'sync-v1.js')).syncSchemaTable();

//  کارگرهای پس‌زمینهٔ `index.js` (soak بی آن‌ها نیمه است)؛ هر کدام بی پرچمش تقریباً بیکار است
const workers = [];
const cluster = require(path.join(SERVER, 'src', 'lib', 'cluster.js'));
if (cluster.enabled()) {
  //  `--instances` (`CLUSTER_ENABLED=1`): همان فهرستِ `src/index.js` و فقط روی رهبر؛ گذرگاه و سوکتِ زنده هم
  require(path.join(SERVER, 'src', 'lib', 'sync-v1-live.js')).attach(h.server());
  cluster.jobs(require(path.join(SERVER, 'src', 'workers.js')).backgroundJobs());
  await cluster.start();
  workers.push('cluster');
} else if (CFG.workers !== false) {
  for (const [mod, fn] of [['login-outbox', 'start'], ['order-notify', 'start'], ['customer-notify', 'start'], ['order-timeout', 'start'],
    ['order-desk', 'start'], ['order-start-alert', 'start'], ['owner-report', 'start'], ['storefront-alerts', 'start'],
    ['payment-intents', 'startReconciler'], ['ops-alerts', 'start']]) {
    try { require(path.join(SERVER, 'src', 'lib', mod))[fn](); workers.push(mod); } catch (e) { log(`کارگرِ ${mod}: ${e.message}`); }
  }
}

//  نمونهٔ دوم به بعدِ `--instances` (`CFG.follower`): همان پایگاهِ کاشته‌شده، بی پاک کردن و بی کاشتن
async function seedAll() {
  async function call(method, p, { token, body, headers = {} } = {}) {
    const res = await fetch(base + p, {
      method,
      headers: { 'Content-Type': 'application/json', 'X-App': 'shop', ...(token ? { Authorization: `Bearer ${token}` } : {}), ...headers },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    const text = await res.text();
    let json = null;
    try { json = JSON.parse(text); } catch { /* بی JSON */ }
    if (res.status >= 300) throw new Error(`${method} ${p} ⇒ ${res.status} ${text.slice(0, 300)}`);
    return json;
  }

  // ── مدیر، دکان‌ها و ویترین‌ها ──
  const tSetup = performance.now();
  await db.query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'loadtest','loadtest',$2,'superadmin','active',$3)`,
    [db.newId('adm'), await pw.hashPassword('Load!12345'), db.now()]
  );
  const admin = (await call('POST', '/api/admin/login', { body: { username: 'loadtest', password: 'Load!12345' } })).token;

  //  چند دکان با هم (`--setup-par`)؛ شمارهٔ هر دکان همان جایگاهش
  const par = Math.max(1, Number(CFG.setupPar) || 1);
  const parallel = async (xs, fn) => { for (let i = 0; i < xs.length; i += par) await Promise.all(xs.slice(i, i + par).map(fn)); };
  const shops = new Array(CFG.shops);
  await parallel([...Array(CFG.shops).keys()], async (s) => {
    const u = await h.newUser(`صاحبِ دکانِ بار ${s}`, 'shop');
    const shopId = (await call('POST', '/api/shop', { token: u.accessToken, body: { name: `دکانِ بار ${s}` } })).shop.id;
    //  همان درِ پرچم‌های پنلِ مدیر (`/api/admin/flags`) — نه درِ تنظیماتِ پمپ
    for (const key of ['shop.orders', 'shop.storefront', 'shop.held_sync']) {
      await call('PUT', '/api/admin/flags', { token: admin, body: { scope: shopId, key, value: true, reason: 'آزمونِ بار' } });
    }
    const owner = await h.signIn(u, 'shop');
    const slug = `load-${s}-${shopId.slice(-6).toLowerCase().replace(/[^a-z0-9]/g, 'x')}`;
    await call('PUT', '/api/shop/storefront', { token: owner.accessToken, body: { slug, enabled: true, title: `ویترینِ بار ${s}`, contactPhone: '0700000001' } });
    shops[s] = { index: s, shopId, slug, token: owner.accessToken, email: u.email, password: u.password };
  });

  step(`${shops.length} دکان و ویترین`);
  // ── دادهٔ دفتر: دکانِ ۰ بزرگ (مقیاسِ دادهٔ مرجع)، بقیه کوچک ──
  //  همان کاشتنِ مستقیمِ `bench/server.mjs` (شکلِ ردیفی که `applyOp`ِ sync v1 می‌نویسد)
  const schema = migrations.currentVersion('shop');
  async function seedRows(shopId, rows) {
    const at = db.now();
    for (let i = 0; i < rows.length; i += 5000) {
      const batch = rows.slice(i, i + 5000).map((r) => ({ t: r.table, i: r.id, d: r.data }));
      await db.query(
        `WITH src AS (
           SELECT x->>'t' AS t, x->>'i' AS i, x->'d' AS d, ord
             FROM jsonb_array_elements($1::jsonb) WITH ORDINALITY AS a(x, ord)
         ), ins AS (
           INSERT INTO oplog (id, app, account_kind, account_id, device_id, op_id, client_ts, table_name, row_id,
                              op_type, fields, hash, schema_version, received_at)
           SELECT 'op_seed_' || $2 || '_' || t || '_' || i, 'shop', 'shop', $2, 'seed', 'seed-' || $2 || '-' || t || '-' || i, 0, t, i,
                  'insert', d, '', $3, $4
             FROM src ORDER BY ord
           RETURNING table_name, row_id, server_seq, fields
         )
         INSERT INTO sync_rows (app, account_kind, account_id, table_name, row_id, data, field_seq, field_src,
                                deleted_at, updated_seq, created_at, updated_at)
         SELECT 'shop', 'shop', $2, table_name, row_id, fields,
                (SELECT COALESCE(jsonb_object_agg(k, server_seq), '{}'::jsonb) FROM jsonb_object_keys(fields) k),
                (SELECT COALESCE(jsonb_object_agg(k, jsonb_build_object('d', 'seed', 'o', 'seed-' || table_name || '-' || row_id)), '{}'::jsonb)
                   FROM jsonb_object_keys(fields) k),
                NULL, server_seq, $4, $4
           FROM ins`,
        [JSON.stringify(batch), shopId, schema, at]
      );
    }
    //  ویترین: همهٔ کالاها منتشرشده — بدترین حالتِ کاتالوگ
    await db.query(
      `INSERT INTO storefront_products (shop_id, product_id, published, updated_by, updated_at)
       SELECT account_id, row_id, true, 'loadtest', $2 FROM sync_rows
        WHERE app='shop' AND account_kind='shop' AND account_id=$1 AND table_name='products'`, [shopId, at]);
  }
  const scaleOf = (k) => Object.fromEntries(Object.entries(REFERENCE).map(([key, v]) => [key, key === 'days' ? v : Math.max(1, Math.round(v * k))]));
  const dataset = { big: scaleOf(CFG.scale), small: scaleOf(CFG.smallScale), rows: 0 };
  const tSeed = performance.now();
  const ledgers = {};
  for (const sh of shops) {
    const kind = sh.index === 0 ? 'big' : 'small';
    const ledger = ledgers[kind] ||= makeReference(dataset[kind]);
    const rows = [...syncRowsOf(ledger)];
    //  «نشانِ دکان»: یک کالای منتشرشده با شناسه و نامِ یکتای همین دکان — ویترینِ دکانِ دیگر نباید آن را نشان دهد (`xt.probe`)
    const first = rows.find((r) => r.table === 'products');
    rows.push({ table: 'products', id: `mk-${sh.index}`, data: { ...first.data, name: `نشانِ دکان ~S${sh.index}~`, barcodes: [`mk${sh.index}x`] } });
    dataset.rows += rows.length;
    await seedRows(sh.shopId, rows);
    //  نمونه‌ای از کالاها برای «صفحهٔ کالا»، سبد و opهای فروش (همه منتشرشده و قیمت‌دار)
    sh.products = ledger.products.filter((p) => p.salePrice > 0).slice(0, 400)
      .map((p) => ({ id: p.id, name: p.name, priceMinor: Math.round(p.salePrice * 100), buyMinor: Math.round(p.purchasePrice * 100) }));
    sh.terms = [...new Set(ledger.products.slice(0, 2000).map((p) => String(p.name).split(/\s+/)[0]).filter(Boolean))].slice(0, 40);
  }
  dataset.seedMs = Math.round(performance.now() - tSeed);
  step(`${dataset.rows} ردیفِ دفتر کاشته شد`);
  if (!db.isPglite()) await db.query('ANALYZE');
  else {
    try { await require(path.join(SERVER, 'src', 'lib', 'pglite-stats.js')).analyzeNow(); } catch { /* بی آن */ }
  }

  //  پلِ v1 ⇒ v3: دفترِ کاشته‌شده در v1 است و نخستین pull ِ v3 هر دکان تا ۳×۱۰۰۰ op ِ آن را درونِ
  //  همان درخواست تبدیل می‌کند (`catchUp`ِ lib/sync-v3). دکانِ واقعی این را یک بار هنگامِ رفتن به v3
  //  می‌پردازد؛ این‌جا پیش از بار و جدا سنجیده می‌شود تا بارِ پایدار با پلِ تمام‌شده باشد.
  const sync3 = require(path.join(SERVER, 'src', 'lib', 'sync-v3.js'));
  const tBridge = performance.now();
  let bridged = 0;
  //  هر دکان قفلِ خودش را دارد ⇒ چند دکان با هم (بارِ پایدار همان پلِ تمام‌شده است)
  await parallel(shops, async (sh) => {
    const ctx = { app: 'shop', accountKind: 'shop', accountId: sh.shopId };
    for (let i = 0; i < 100000; i++) {
      const r = await sync3.bridge(ctx, { max: 2000 });
      bridged += r.imported || 0;
      if (!r.behind) break;
    }
  });
  dataset.bridge = { ops: bridged, ms: Math.round(performance.now() - tBridge) };
  dataset.bridge.opsPerSec = Math.round(bridged / Math.max(0.001, dataset.bridge.ms / 1000));
  step(`پلِ v1⇒v3: ${bridged} op، ${dataset.bridge.opsPerSec} op/s`);

  // ── شماره‌های تأییدشدهٔ مشتری (هر شماره ۱۰ سفارش در ساعت در هر دکان: RSK-07) ──
  let phoneSeq = 0;
  const checkoutShops = shops.slice(0, CFG.checkoutShops);
  await parallel(checkoutShops, async (sh) => {
    sh.tickets = [];
    for (let i = 0; i < CFG.phonesPerShop; i++) {
      const phone = `079${String(1000000 + (++phoneSeq)).slice(-7)}`;
      const st = await call('POST', `/api/store/${sh.slug}/verify/start`, { body: { phone } });
      const ck = await call('POST', `/api/store/${sh.slug}/verify/check`, { body: { phone, code: st.devCode } });
      sh.tickets.push({ phone, ticket: ck.ticket });
    }
  });

  step(`${CFG.phonesPerShop} شمارهٔ تأییدشده در ${checkoutShops.length} ویترین`);
  dataset.setupMs = Math.round(performance.now() - tSetup);
  dataset.server = db.isPglite() ? 'pglite' : (await db.one('SHOW server_version')).server_version;
  dataset.poolMax = db.isPglite() ? 1 : Number(process.env.PG_POOL_MAX || 10);
  dataset.workers = workers;
  dataset.gc = !!global.gc;
  return { dataset, shops, admin };
}

process.on('message', async (m) => {
  try {
    if (m.type === 'stats') process.send({ type: 'stats', id: m.id, stats: await stats(m) });
    else if (m.type === 'dbsize') {
      const out = db.isPglite() ? null : Number((await db.one('SELECT pg_database_size(current_database())::bigint AS b')).b);
      process.send({ type: 'dbsize', id: m.id, bytes: out });
    } else if (m.type === 'qstats') {
      process.send({ type: 'qstats', id: m.id, top: topQueries() });
      if (m.reset) qstats.clear();
    } else if (m.type === 'gatestats') {
      process.send({ type: 'gatestats', id: m.id, gate: { push: gateSummary(gateTimes.push), other: gateSummary(gateTimes.other) } });
      if (m.reset) { gateTimes.push = []; gateTimes.other = []; }
    } else if (m.type === 'sql') {
      //  `--plan`: پیش از گام روی همین پایگاهِ آزمون (نامش test/bench دارد؛ `load-test.mjs`)، مثلاً برداشتن/ساختنِ نمایه
      for (const q of [].concat(m.sql)) await db.query(q);
      process.send({ type: 'sql', id: m.id, ok: true });
    } else if (m.type === 'check') {
      //  درستی زیرِ بار (`scripts/load-check.js`): دفترِ بارساز در برابرِ همین پایگاه (PGlite ِ در حافظه هم فقط از این‌جا دیده می‌شود)
      const ledger = JSON.parse(fs.readFileSync(m.ledger, 'utf8'));
      const check = await require(path.join(SERVER, 'scripts', 'load-check.js')).check(db, ledger);
      process.send({ type: 'check', id: m.id, check });
    } else if (m.type === 'stop') {
      await h.stop().catch(() => {});
      process.exit(0);
    }
  } catch (e) {
    process.send({ type: 'error', id: m.id, error: String(e && e.message) });
  }
});
const seeded = CFG.follower ? { dataset: null, shops: [] } : await seedAll();
await stats();
process.send({ type: 'ready', base, ...seeded });
