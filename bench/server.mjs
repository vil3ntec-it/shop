#!/usr/bin/env node
/**
 * bench — کارایی سرور با دادهٔ مرجعِ بزرگ (PERF-01..04، PERF-06؛ بخش‌های ۹.۲ و ۲۹.۶).
 *
 *   node bench/server.mjs                                   # PGlite در حافظه، دادهٔ مرجعِ کامل
 *   node bench/server.mjs --db postgres://shop:shoppass@127.0.0.1:5432/shop_bench
 *   node bench/server.mjs --scale 0.1 --reps 20             # یک‌دهمِ داده — سریع
 *   node bench/server.mjs --skip snapshot,catchup           # بخش‌های سنگین را نسنج (sync،catalog،orders،burst،plans هم)
 *   node bench/server.mjs --json out.json --compare base.json --max-regress 10
 *
 * ⛔ پایگاه داده را **پاک می‌کند** (همان `resetDatabase`ِ آزمون‌ها). فقط
 * `pglite:memory` یا پایگاهی که نامش `test` یا `bench` دارد پذیرفته می‌شود.
 * هیچ دادهٔ واقعی‌ای این‌جا نیست: دفتر از `bench/datagen.mjs` (seed ثابت) می‌آید.
 *
 * چه چیزی سنجیده می‌شود (هر کدام p50/p95/p99، خطا، و بایتِ پاسخ):
 *   • Sync v1 (همان راهِ نسخهٔ وب و اندروید): pull صفحهٔ اول، pull وسطِ دفتر،
 *     pull افزایشی، push یک فروش (۵ op)، push دستهٔ ۲۰۰تایی، snapshot کامل،
 *     و همگام‌سازیِ کاملِ دستگاهِ تازه (همهٔ صفحه‌ها پشتِ سرِ هم).
 *   • انفجار (PERF-02): N دستگاهِ هم‌زمان، هر کدام چند فروش.
 *   • ویترین: کاتالوگِ عمومی (صفحهٔ اول، جست‌وجو، دسته، صفحهٔ بعد) و فهرستِ کالای صاحب دکان.
 *   • سفارش: ثبتِ دستی، فهرستِ هر زبانه، خلاصه و جزئیات.
 *   • PERF-03: EXPLAIN (ANALYZE) پرس‌وجوهای پرتکرار — آیا Seq Scan دارند.
 * دادهٔ ردیف‌ها مستقیم با SQL کاشته می‌شود — همان شکلی که `applyOp`ِ
 * `sync-v1.js` برای درجِ ردیفِ تازه می‌نویسد (هر فیلد ⇒ همان server_seq و
 * همان دستگاه)؛ ۵۲۵ هزار push ِ API برای کاشتن ده‌ها دقیقه طول می‌کشید.
 */
import { createRequire } from 'node:module';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { makeReference, syncRowsOf, REFERENCE } from './datagen.mjs';

const arg = (k, d) => { const i = process.argv.indexOf(k); return i > 0 && process.argv[i + 1] ? process.argv[i + 1] : d; };
const DB = arg('--db', process.env.BENCH_DATABASE_URL || 'pglite:memory');
const SCALE = Number(arg('--scale', 1));
const REPS = Number(arg('--reps', 30));
const BURST = Number(arg('--burst', 10));
const ORDERS = Number(arg('--orders', 1000));
const SKIP = new Set(arg('--skip', '').split(',').filter(Boolean));
const TIMEOUT = Number(arg('--timeout', 120000));
const JSON_OUT = arg('--json', '');
const COMPARE = arg('--compare', '');
const MAX_REGRESS = Number(arg('--max-regress', 10));

if (!/^pglite:/i.test(DB)) {
  let name = '';
  try { name = new URL(DB).pathname.replace(/^\//, ''); } catch { /* پایین */ }
  if (!/(test|bench)/i.test(name)) {
    console.error(`⛔ این ابزار پایگاه داده را پاک می‌کند؛ نامِ پایگاه باید test یا bench داشته باشد (داده شد: «${name || DB}»)`);
    process.exit(2);
  }
}
process.env.TEST_DATABASE_URL = DB;
Object.assign(process.env, {
  RATE_SYNC_MAX: '10000000', RATE_SYNC_IP_MAX: '10000000', RATE_ADMIN_MAX: '100000',
  RATE_STORE_CHECKOUT_MAX: '100000',
});

//  سطرِ «GET /api/… 200 3ms» را سرور فقط بیرون از production برای هر درخواست
//  می‌نویسد؛ در تولید فقط کندتر از ۱ ثانیه یا خطا. همان رفتارِ تولید را می‌گیریم.
const log = console.log.bind(console);
console.log = (...a) => {
  const m = typeof a[0] === 'string' && /^(GET|POST|PUT|PATCH|DELETE) \/\S* (\d{3}) (\d+)ms$/.exec(a[0]);
  if (m && Number(m[2]) < 400 && Number(m[3]) <= 1000) return;
  if (typeof a[0] === 'string' && a[0].startsWith('[otp]')) return;
  log(...a);
};

const require = createRequire(import.meta.url);
const SERVER = path.join(import.meta.dirname, '..', 'server');
const h = require(path.join(SERVER, 'test', 'helpers.js'));
const db = require(path.join(SERVER, 'src', 'db.js'));
const migrations = require(path.join(SERVER, 'src', 'lib', 'sync-v1-migrations.js'));

const stat = (xs) => {
  const s = xs.slice().sort((a, b) => a - b);
  const q = (p) => s[Math.min(s.length - 1, Math.max(0, Math.ceil(p * s.length) - 1))];
  const r1 = (v) => Math.round(v * 10) / 10;
  return { n: s.length, p50: r1(q(0.5)), p95: r1(q(0.95)), p99: r1(q(0.99)), max: r1(s[s.length - 1]) };
};

let base = '';
async function call(method, p, { token, body, headers = {} } = {}, retried = false) {
  let res;
  try {
    res = await fetch(base + p, {
      method,
      headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}), ...headers },
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: AbortSignal.timeout(TIMEOUT),
    });
  } catch (e) {
    //  اتصالِ keep-alive که سرور پس از ۵ ثانیه بیکاری بسته (پس از کاشتن) — یک بار دوباره؛
    //  push با op_id و سفارش با کلیدِ یکتایی تکرارپذیرند
    if (!retried && e.cause && e.cause.code === 'ECONNRESET') return call(method, p, { token, body, headers }, true);
    throw e;
  }
  const buf = Buffer.from(await res.arrayBuffer());
  return { status: res.status, bytes: buf.length, text: () => buf.toString('utf8') };
}

/** fn ⇒ پاسخ؛ هر وضعیتِ بیرون از ۲xx خطا شمرده می‌شود. */
async function measure(fn, { reps = REPS, warm = 2 } = {}) {
  for (let i = 0; i < warm; i++) await fn(-1 - i);
  const xs = [];
  let errors = 0, bytes = 0, firstError = null;
  for (let i = 0; i < reps; i++) {
    const t0 = performance.now();
    let r;
    try { r = await fn(i); } catch (e) { r = { status: 0, err: String(e) }; }
    xs.push(performance.now() - t0);
    if (!r || r.status < 200 || r.status >= 300) { errors++; firstError ||= r && (r.err || `${r.status} ${r.text ? r.text().slice(0, 200) : ''}`); } else bytes = r.bytes;
  }
  return { ...stat(xs), errors, bytes, ...(firstError ? { firstError } : {}) };
}

const t00 = performance.now();
const result = {
  at: new Date().toISOString(),
  env: {
    cpu: os.cpus()[0]?.model, cores: os.cpus().length, memGB: Math.round(os.totalmem() / 2 ** 30),
    os: `${os.platform()} ${os.release()}`, node: process.version, driver: /^pglite:/i.test(DB) ? 'pglite' : 'postgres',
  },
  scale: SCALE, reps: REPS, dataset: {}, seed: {}, sync: {}, burst: null, catalog: {}, orders: {}, plans: {}, memory: {},
};

//  هر سنجه همان لحظه چاپ و (با --json) نوشته می‌شود — اجرای بلندی که نیمه‌کاره بماند
//  (کمبودِ حافظه، کشتنِ فرایند) عددهای گرفته‌شده را از دست نمی‌دهد
const line = (label, s) => s && console.log(`  ${label.padEnd(26)} p50 ${String(s.p50).padStart(8)}  p95 ${String(s.p95).padStart(8)}  p99 ${String(s.p99).padStart(8)} ms${s.errors ? `  ❌ ${s.errors} خطا (${s.firstError})` : ''}${s.bytes ? `  ${(s.bytes / 1024).toFixed(0)}KB` : ''}`);
const save = () => { if (JSON_OUT) fs.writeFileSync(JSON_OUT, JSON.stringify(result, null, 2)); };
const live = (group) => new Proxy({}, { set(o, k, v) {
  o[k] = v;
  if (v && v.p50 !== undefined) line(`${group}.${String(k)}`, v);
  else console.log(`  ${group}.${String(k)} ${JSON.stringify(v)}`);
  save();
  return true;
} });
result.sync = live('sync'); result.catalog = live('catalog'); result.orders = live('orders');

base = await h.start();
if (result.env.driver === 'postgres') result.env.server = (await db.one('SHOW server_version')).server_version;

// ── حساب، دکان، پرچم‌ها ──
const pw = require(path.join(SERVER, 'src', 'lib', 'password.js'));
await db.query(
  `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
   VALUES ($1,'bench','bench',$2,'superadmin','active',$3)`,
  [db.newId('adm'), await pw.hashPassword('Bench!12345'), db.now()]
);
const admin = JSON.parse((await call('POST', '/api/admin/login', { body: { username: 'bench', password: 'Bench!12345' } })).text()).token;
const owner = await h.newUser('صاحبِ دکانِ سنجش', 'shop');
const token = owner.accessToken;
const shopId = JSON.parse((await call('POST', '/api/shop', { token, body: { name: 'دکانِ سنجش' } })).text()).shop.id;
for (const key of ['shop.orders', 'shop.storefront']) {
  const r = await call('PUT', '/api/admin/pump/live-config', { token: admin, body: { scope: shopId, key, value: true } });
  if (r.status !== 200) throw new Error(`پرچمِ ${key}: ${r.status} ${r.text()}`);
}

// ── دادهٔ مرجع ──
const scaled = Object.fromEntries(Object.entries(REFERENCE).map(([k, v]) => [k, k === 'days' ? v : Math.max(1, Math.round(v * SCALE))]));
let t = performance.now();
const ledger = makeReference(scaled);
result.dataset = { ...scaled, transactions: ledger.transactions.length, warehouseEntries: ledger.warehouseEntries.length, returns: ledger.returns.length, genMs: Math.round(performance.now() - t) };
const rows = [...syncRowsOf(ledger)];
result.dataset.syncRows = rows.length;

t = performance.now();
const schema = migrations.currentVersion('shop');
const at = db.now();
const B = 5000;
//  یک پارامترِ JSON برای هر دسته — PGlite با هزاران پارامترِ جدا ده برابر کندتر است
for (let i = 0; i < rows.length; i += B) {
  const batch = rows.slice(i, i + B).map((r) => ({ t: r.table, i: r.id, d: r.data }));
  await db.query(
    `WITH src AS (
       SELECT x->>'t' AS t, x->>'i' AS i, x->'d' AS d, ord
         FROM jsonb_array_elements($1::jsonb) WITH ORDINALITY AS a(x, ord)
     ), ins AS (
       INSERT INTO oplog (id, app, account_kind, account_id, device_id, op_id, client_ts, table_name, row_id,
                          op_type, fields, hash, schema_version, received_at)
       SELECT 'op_seed_' || t || '_' || i, 'shop', 'shop', $2, 'seed', 'seed-' || t || '-' || i, 0, t, i,
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
// ویترین: همهٔ کالاها منتشرشده — بدترین حالتِ کاتالوگ
await db.query(
  `INSERT INTO storefront_products (shop_id, product_id, published, updated_by, updated_at)
   SELECT account_id, row_id, true, 'bench', $2 FROM sync_rows
    WHERE app='shop' AND account_kind='shop' AND account_id=$1 AND table_name='products'`, [shopId, at]);
result.seed = { ms: Math.round(performance.now() - t), rowsPerSec: Math.round(rows.length / ((performance.now() - t) / 1000)) };
//  مقایسهٔ پیش/پس: `--drop-index <نام>` نمایه‌ای را که migration ساخته برمی‌دارد تا
//  حالِ پیش از آن migration سنجیده شود (همراهِ --no-maintenance = حالِ main پیش از PERF)
for (const name of arg('--drop-index', '').split(',').filter(Boolean)) {
  if (!/^[a-z0-9_]+$/.test(name)) throw new Error(`نامِ نمایه: ${name}`);
  await db.query(`DROP INDEX IF EXISTS ${name}`);
  (result.seed.droppedIndexes ||= []).push(name);
}
//  آمارِ planner: PostgreSQL آن را با autovacuum خودش می‌گیرد (این‌جا یک بار، تا منتظرش نمانیم).
//  PGlite تک‌کاربره است و autovacuum ندارد؛ اگر سرور نگه‌دارِ آمارِ PGlite را دارد
//  (`lib/pglite-stats.js`)، همان یک گذرِ آن اجرا می‌شود — همان کاری که سرور پس از بالا آمدن و
//  هر چند ساعت می‌کند. `--no-maintenance` ⇒ دیتابیسِ بی آمار (حالِ سرورِ بی آن ماژول).
if (result.env.driver === 'postgres') {
  const m0 = performance.now();
  await db.query('ANALYZE');
  result.seed.maintenance = { kind: 'ANALYZE (به جای autovacuum)', ms: Math.round(performance.now() - m0) };
} else if (!process.argv.includes('--no-maintenance') && fs.existsSync(path.join(SERVER, 'src', 'lib', 'pglite-stats.js'))) {
  const m0 = performance.now();
  await require(path.join(SERVER, 'src', 'lib', 'pglite-stats.js')).analyzeNow();
  result.seed.maintenance = { kind: 'pglite-stats.analyzeNow', ms: Math.round(performance.now() - m0) };
} else {
  result.seed.maintenance = null;
}
if (result.env.driver === 'postgres') {
  result.seed.dbBytes = Number((await db.one('SELECT pg_database_size(current_database())::bigint AS b')).b);
}
console.log(`کاشته شد: ${rows.length} ردیف در ${result.seed.ms}ms (${result.env.driver})`);

const S = { 'X-App': 'shop' };
const dev = (d) => ({ ...S, 'X-Device': d });
const head = Number((await db.one(`SELECT COALESCE(MAX(server_seq),0)::bigint AS h FROM oplog WHERE app='shop' AND account_id=$1`, [shopId])).h);
const firstSeq = Number((await db.one(`SELECT COALESCE(MIN(server_seq),0)::bigint AS h FROM oplog WHERE app='shop' AND account_id=$1`, [shopId])).h);

// ── Sync v1 ──
const mid = Math.floor((firstSeq + head) / 2);
if (!SKIP.has('sync')) {
result.sync.pullFirstPage = await measure((i) => call('GET', `/api/sync/v1/pull?since=0&limit=500&device_id=new-${i}`, { token, headers: dev(`new-${i}`) }));
result.sync.pullMiddle = await measure((i) => call('GET', `/api/sync/v1/pull?since=${mid}&limit=1000&device_id=mid-${i}`, { token, headers: dev(`mid-${i}`) }));
result.sync.pullIncremental = await measure((i) => call('GET', `/api/sync/v1/pull?since=${head - 20}&limit=500&device_id=inc-${i}`, { token, headers: dev(`inc-${i}`) }));
}

let opSeq = 0;
const saleOps = (deviceId) => {
  const n = ++opSeq;
  const sid = `bench-sale-${deviceId}-${n}`;
  const p1 = ledger.products[(n * 7919) % ledger.products.length], p2 = ledger.products[(n * 104729) % ledger.products.length];
  const ts = Date.now();
  const o = (table, row_id, fields) => ({ op_id: `b-${deviceId}-${n}-${table}-${row_id}`, ts, table, row_id, type: 'insert', fields });
  return [
    o('sales', sid, { invoiceNumber: 900000 + n, total: p1.salePrice + p2.salePrice, discount: 0, finalTotal: p1.salePrice + p2.salePrice, paymentMethod: 'cash', paidAmount: p1.salePrice + p2.salePrice, remaining: 0, status: 'completed', createdAt: ts, date: '2026-10-19' }),
    o('saleItems', `${sid}-1`, { saleId: sid, productId: p1.id, quantity: 1, unitPrice: p1.salePrice, purchasePrice: p1.purchasePrice, totalPrice: p1.salePrice, returnedQty: 0 }),
    o('saleItems', `${sid}-2`, { saleId: sid, productId: p2.id, quantity: 1, unitPrice: p2.salePrice, purchasePrice: p2.purchasePrice, totalPrice: p2.salePrice, returnedQty: 0 }),
    o('stockMovements', `${sid}-m1`, { productId: p1.id, type: 'sale', qty: -1, refId: sid }),
    o('stockMovements', `${sid}-m2`, { productId: p2.id, type: 'sale', qty: -1, refId: sid }),
  ];
};
const push = (deviceId, ops) => call('POST', '/api/sync/v1/push', { token, headers: dev(deviceId), body: { device_id: deviceId, schema_version: schema, ops } });
if (!SKIP.has('sync')) {
  result.sync.pushSale = await measure(() => push('till-1', saleOps('till-1')));
  result.sync.pushBatch200 = await measure(() => {
    const ops = [];
    while (ops.length < 200) ops.push(...saleOps('till-2'));
    return push('till-2', ops.slice(0, 200));
  }, { reps: Math.max(5, Math.round(REPS / 3)) });
}

if (!SKIP.has('catchup')) {
  // دستگاهِ تازه: همهٔ دفتر، صفحه به صفحه (۱۰۰۰تایی)، تا has_more=false
  const pages = [];
  let since = 0, total = 0, more = true;
  const t0 = performance.now();
  while (more) {
    const p0 = performance.now();
    const r = await call('GET', `/api/sync/v1/pull?since=${since}&limit=1000&device_id=fresh`, { token, headers: dev('fresh') });
    pages.push(performance.now() - p0);
    if (r.status !== 200) throw new Error(`catch-up: ${r.status}`);
    const j = JSON.parse(r.text());
    total += j.ops.length; since = j.cursor; more = j.has_more;
  }
  result.sync.catchUp = { ops: total, pages: pages.length, totalMs: Math.round(performance.now() - t0), page: stat(pages) };
}

// ── انفجار: N دستگاهِ هم‌زمان (PERF-02) ──
if (!SKIP.has('burst')) {
  const per = 10, lat = [];
  let errors = 0;
  const t0 = performance.now();
  await Promise.all(Array.from({ length: BURST }, async (_, d) => {
    for (let k = 0; k < per; k++) {
      const a = performance.now();
      const r = await push(`burst-${d}`, saleOps(`burst-${d}`)).catch(() => ({ status: 0 }));
      lat.push(performance.now() - a);
      if (r.status !== 200) errors++;
    }
  }));
  const ms = performance.now() - t0;
  result.burst = { devices: BURST, requests: BURST * per, errors, totalMs: Math.round(ms), rps: Math.round(BURST * per / (ms / 1000)), ...stat(lat) };
}

// ── ویترین ──
const slug = `bench-${Date.now().toString(36)}`;
{
  const r = await call('PUT', '/api/shop/storefront', { token, body: { slug, enabled: true, title: 'ویترینِ سنجش', contactPhone: '0700000001' } });
  if (r.status !== 200) throw new Error(`ویترین: ${r.status} ${r.text()}`);
}
if (!SKIP.has('catalog')) {
result.catalog.storeHome = await measure(() => call('GET', `/api/store/${slug}`));
result.catalog.publicFirstPage = await measure(() => call('GET', `/api/store/${slug}/products`));
result.catalog.publicSearch = await measure((i) => call('GET', `/api/store/${slug}/products?q=${encodeURIComponent(['برنج', 'روغن', 'چای', 'صابون'][Math.abs(i) % 4])}`));
result.catalog.publicCategory = await measure(() => call('GET', `/api/store/${slug}/products?category=${encodeURIComponent('خوراکی')}`));
{
  const first = JSON.parse((await call('GET', `/api/store/${slug}/products?limit=100`)).text());
  let cursor = first.nextCursor;
  for (let k = 0; k < 49 && cursor; k++) cursor = JSON.parse((await call('GET', `/api/store/${slug}/products?limit=100&cursor=${cursor}`)).text()).nextCursor;
  if (cursor) result.catalog.publicPage51 = await measure(() => call('GET', `/api/store/${slug}/products?limit=100&cursor=${cursor}`));
}
result.catalog.ownerFirstPage = await measure(() => call('GET', '/api/shop/storefront/products?limit=100', { token }));
result.catalog.ownerMiddlePage = await measure(() => call('GET', `/api/shop/storefront/products?limit=100&cursor=${ledger.products[Math.floor(ledger.products.length / 2)].id}`, { token }));
}

// ── سفارش ──
if (!SKIP.has('orders')) {
  let k = 0;
  const order = () => {
    const p = ledger.products[(++k * 7919) % ledger.products.length];
    return call('POST', '/api/shop/orders', {
      token, headers: { 'Idempotency-Key': `bench-order-${k}-${slug}` },
      body: { source: 'phone', fulfillment: 'pickup', paymentMethod: 'cod', customer: { name: 'مشتری سنجش', phone: '0791234567' },
        lines: [{ productId: p.id, name: p.name, unit: p.unit, qtyMilli: 1000, unitPriceMinor: p.salePrice * 100 }, { name: 'قلمِ دوم', qtyMilli: 2500, unitPriceMinor: 1250 }] },
    });
  };
  result.orders.create = await measure(order, { reps: ORDERS, warm: 0 });
  for (const tab of ['new', 'preparing', 'done']) {
    result.orders[`list_${tab}`] = await measure(() => call('GET', `/api/shop/orders?tab=${tab}&limit=50`, { token }));
  }
  result.orders.summary = await measure(() => call('GET', '/api/shop/orders/summary', { token }));
  const someId = (await db.one('SELECT id FROM online_orders WHERE shop_id=$1 ORDER BY created_at DESC LIMIT 1', [shopId])).id;
  result.orders.detail = await measure(() => call('GET', `/api/shop/orders/${someId}`, { token }));
}

// ── PERF-03: نقشهٔ پرس‌وجوهای پرتکرار ──
if (!SKIP.has('plans')) {
  const acc = ['shop', 'shop', shopId];
  const SQL_NAME = "translate(lower(r.data->>'name'), 'يىكۀةأإآ' || chr(1600) || chr(8204), 'ییکههااا  ')";
  const PLANS = {
    pull: [`SELECT op_id FROM oplog WHERE app=$1 AND account_kind=$2 AND account_id=$3 AND server_seq > $4 AND server_seq <= $5 AND device_id <> 'x' ORDER BY server_seq ASC LIMIT 1001`, [...acc, mid, head]],
    head: [`SELECT COALESCE(MAX(server_seq),0)::bigint FROM oplog WHERE app=$1 AND account_kind=$2 AND account_id=$3`, acc],
    pushRowLock: [`SELECT * FROM sync_rows WHERE app=$1 AND account_kind=$2 AND account_id=$3 AND table_name='products' AND row_id=$4`, [...acc, ledger.products[5].id]],
    deleteOwnerCheck: [`SELECT device_id FROM oplog WHERE app=$1 AND account_kind=$2 AND account_id=$3 AND table_name='sales' AND row_id=$4 AND op_type='insert' ORDER BY server_seq ASC LIMIT 1`, [...acc, ledger.sales[5].id]],
    snapshot: [`SELECT table_name, row_id, data FROM sync_rows WHERE app=$1 AND account_kind=$2 AND account_id=$3 AND deleted_at IS NULL ORDER BY table_name, row_id`, acc],
    catalogFirstPage: [`SELECT r.row_id FROM storefront_products sp JOIN sync_rows r ON r.app='shop' AND r.account_kind='shop' AND r.table_name='products' AND r.account_id = sp.shop_id AND r.row_id = sp.product_id
       WHERE sp.shop_id = $1 AND sp.published AND r.deleted_at IS NULL AND COALESCE(r.data->>'active','true') <> 'false'
       ORDER BY COALESCE(r.data->>'name',''), r.row_id LIMIT 41`, [shopId]],
    catalogSearch: [`SELECT r.row_id FROM storefront_products sp JOIN sync_rows r ON r.app='shop' AND r.account_kind='shop' AND r.table_name='products' AND r.account_id = sp.shop_id AND r.row_id = sp.product_id
       WHERE sp.shop_id = $1 AND sp.published AND r.deleted_at IS NULL AND COALESCE(r.data->>'active','true') <> 'false' AND ${SQL_NAME} LIKE '%چای%'
       ORDER BY COALESCE(r.data->>'name',''), r.row_id LIMIT 41`, [shopId]],
    ordersList: [`SELECT id FROM online_orders WHERE shop_id=$1 AND status='new' ORDER BY created_at ASC, id ASC LIMIT 51`, [shopId]],
  };
  //  بی آمار، planner برای کاتالوگ گاهی حلقهٔ O(n²) می‌سازد؛ EXPLAIN ANALYZE همان را اجرا می‌کند،
  //  پس وقتی کاتالوگ کنار گذاشته شده، نقشه‌اش هم
  if (SKIP.has('catalog')) { delete PLANS.catalogFirstPage; delete PLANS.catalogSearch; }
  for (const [name, [sql, params]] of Object.entries(PLANS)) {
    const r = await db.query(`EXPLAIN (ANALYZE, FORMAT JSON) ${sql}`, params);
    const plan = (r.rows[0]['QUERY PLAN'] || Object.values(r.rows[0])[0]);
    const root = (typeof plan === 'string' ? JSON.parse(plan) : plan)[0];
    const nodes = [];
    (function walk(n) { nodes.push(n['Node Type'] + (n['Index Name'] ? `(${n['Index Name']})` : n['Relation Name'] ? `(${n['Relation Name']})` : '')); (n.Plans || []).forEach(walk); })(root.Plan);
    result.plans[name] = { ms: Math.round(root['Execution Time'] * 10) / 10, seqScan: nodes.some((x) => x.startsWith('Seq Scan')), nodes: nodes.join(' > ') };
  }
}

//  عکسِ کامل آخر از همه: کلِ دفتر یک JSON در حافظهٔ سرور است و در اجرای پیشین (PGlite، ۵۹۶k
//  ردیف) فرایند را از حافظه انداخت؛ پس یک بار، و پس از این‌که بقیهٔ عددها نوشته شده‌اند
if (!SKIP.has('snapshot')) {
  const rss0 = process.memoryUsage().rss;
  result.sync.snapshotGzip = await measure(() => call('GET', '/api/sync/v1/snapshot', { token, headers: { ...dev('snap'), 'Accept-Encoding': 'gzip' } }), { reps: 1, warm: 0 });
  result.memory.snapshotRssDeltaMB = Math.round((process.memoryUsage().rss - rss0) / 2 ** 20);
}

result.memory = { ...result.memory, rssMB: Math.round(process.memoryUsage().rss / 2 ** 20), heapMB: Math.round(process.memoryUsage().heapUsed / 2 ** 20) };
result.totalMs = Math.round(performance.now() - t00);
await h.stop();

// ── خروجی ──
console.log(`\n── سرور (${result.env.driver}${result.env.server ? ' ' + result.env.server : ''}) — ${rows.length} ردیف، مقیاس ${SCALE}`);
if (result.burst) console.log(`  burst ${result.burst.devices} دستگاه × ۱۰ فروش  p50 ${result.burst.p50}  p95 ${result.burst.p95}  p99 ${result.burst.p99} ms، ${result.burst.rps} درخواست/ثانیه، خطا ${result.burst.errors}`);
for (const [k, v] of Object.entries(result.plans)) console.log(`  plan.${k.padEnd(20)} ${String(v.ms).padStart(8)}ms ${v.seqScan ? '⚠️ Seq Scan' : ''}  ${v.nodes}`);
console.log(`  حافظهٔ فرایند: RSS ${result.memory.rssMB}MB؛ کلِ زمان ${(result.totalMs / 1000).toFixed(0)}s`);
if (JSON_OUT) fs.writeFileSync(JSON_OUT, JSON.stringify(result, null, 2));

if (COMPARE) {
  const was = JSON.parse(fs.readFileSync(COMPARE, 'utf8'));
  const worse = [];
  for (const group of ['sync', 'catalog', 'orders']) {
    for (const [k, v] of Object.entries(result[group] || {})) {
      const b = was[group] && was[group][k];
      if (!b || !b.p50 || !v.p50) continue;
      const pct = ((v.p50 - b.p50) / b.p50) * 100;
      if (pct > MAX_REGRESS) worse.push(`${group}.${k}: ${b.p50} → ${v.p50}ms (+${pct.toFixed(0)}%)`);
    }
  }
  console.log(worse.length ? `\n❌ افتِ بیش از ${MAX_REGRESS}٪:\n  ${worse.join('\n  ')}` : `\n✅ هیچ افتی بیش از ${MAX_REGRESS}٪ نیست`);
  process.exit(worse.length ? 1 : 0);
}
process.exit(0);
