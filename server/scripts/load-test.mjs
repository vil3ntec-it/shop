#!/usr/bin/env node
/**
 * آزمونِ بار — بارِ معمول، اوج، burst، soak و فشارِ استخر (PERF-02، PERF-04؛ بخش‌های ۹.۳ بندِ ۹، ۱۵.۹ و ۲۹.۶).
 *
 *   node scripts/load-test.mjs --db postgres://shop:shoppass@127.0.0.1:5432/shop_load_bench --profile load,peak,burst
 *   node scripts/load-test.mjs --db … --profile soak --soak 600              # ثانیه (پیش‌فرض ۳۰۰)
 *   node scripts/load-test.mjs --db … --profile overload             # PERF-04: استخرِ ۲ اتصال زیرِ ۴ برابرِ بار
 *   node scripts/load-test.mjs --profile smoke --no-budget            # PGlite در حافظه، چند ثانیه
 *   node scripts/load-test.mjs --db … --profile knee,knee-store --steps 1,2,3,4,6   # نقطهٔ شکست: پله‌های ۲۰ثانیه‌ای
 *   node scripts/load-test.mjs --db … --profile knee --instances 2   # دو نمونهٔ سرور روی همان پایگاه (CLUSTER_ENABLED=1)
 *   … --rate 40 --shops 20 --devices 3 --scale 0.1 --json out.json
 *
 * ⛔ پایگاه داده را **پاک می‌کند** (`resetDatabase`ِ آزمون‌ها): فقط `pglite:memory`/`pglite:<پوشه>`
 *    یا پایگاهی که نامش `test` یا `bench` دارد.
 *
 * سرور در فرآیندِ جدا (`load-test-server.mjs`) بالا می‌آید و دادهٔ ساختگی می‌کارد؛ این فرآیند فقط
 * بار می‌سازد. بار «باز» است (نرخِ رسیدنِ ثابت، نه N کاربرِ منتظرِ پاسخ): کندیِ سرور جلوی رسیدنِ
 * درخواستِ بعدی را نمی‌گیرد و p99 پنهان نمی‌شود. هر درخواست جدا با برچسبِ مسیرش ثبت می‌شود.
 *
 * بودجه‌ها **پیش از اجرا** همین‌جا ثابت‌اند (بخشِ ۲۹.۶ بندِ ۵) — `BUDGET` پایین؛ شکستِ هر کدام ⇒ خروجِ ۱.
 * `--no-budget` فقط می‌سنجد (CI). بارسازِ اشباع (درخواستِ جاافتاده بیش از ۱٪) ⇒ خروجِ ۳: عددِ آن اجرا معتبر نیست.
 */
import { fork } from 'node:child_process';
import { createRequire } from 'node:module';
import { randomUUID } from 'node:crypto';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { monitorEventLoopDelay } from 'node:perf_hooks';
import { scaleKit, SCALE_MIX, SCALE_BURST_MIX } from './load-scale.mjs';

const argv = process.argv.slice(2);
const arg = (k, d) => { const i = argv.indexOf(k); return i >= 0 && argv[i + 1] !== undefined ? argv[i + 1] : d; };
const has = (k) => argv.includes(k);
const DB = arg('--db', process.env.LOAD_DATABASE_URL || 'pglite:memory');
const PROFILES = arg('--profile', 'load').split(',').map((s) => s.trim()).filter(Boolean);
const RATE = Number(arg('--rate', 40));
const DURATION = arg('--duration') === undefined ? null : Number(arg('--duration'));   //  smoke/load/peak/overload
const SOAK_SEC = Number(arg('--soak', 300));
const SHOPS = Math.max(1, Number(arg('--shops', 20)));
const DEVICES = Math.max(1, Number(arg('--devices', 3)));
const SCALE = Number(arg('--scale', 0.1));
const SMALL_SCALE = Number(arg('--small-scale', 0.005));
const TIMEOUT = Number(arg('--timeout', 10000));
const WARMUP = Number(arg('--warmup', 5));
const MAX_INFLIGHT = Number(arg('--max-inflight', 512));
const JSON_OUT = arg('--json', '');
const BUDGET_ON = !has('--no-budget');
const QUIET = has('--quiet');
const SEED = Number(arg('--seed', 20261102));
//  knee: ضریب‌های `--rate` برای پله‌های پیاپی (هر پله `--duration` یا ۲۰ ثانیه)
const STEPS = arg('--steps', '1,1.5,2,2.5,3,4,5').split(',').map(Number).filter((x) => Number.isFinite(x) && x > 0);
//  چند نمونهٔ سرور روی **همان** پایگاه (نخستین می‌کارد، بقیه فقط بالا می‌آیند)؛ بار به نوبت میانشان پخش می‌شود.
//  بیش از یکی ⇒ `CLUSTER_ENABLED=1` (سقفِ نرخِ مشترک، گذرگاهِ LISTEN/NOTIFY، کارگرِ تک‌رهبر) مگر `--no-cluster`
const INSTANCES = Math.max(1, Math.min(8, Number(arg('--instances', 1)) || 1));
const CLUSTER = INSTANCES > 1 && !has('--no-cluster');
//  opهای v3 ِ پیشینِ دکانِ بزرگ (هر دکانِ کوچک ۱۵۰) — pull و push روی دفترِ پر
const HISTORY = Number(arg('--history', Math.round(60000 * Math.max(SCALE, 0.02))));
//  scale (۲۰۰ دکان × ۵ دستگاه): ویترینِ گشت‌وگذار و سفارش فقط روی زیرمجموعه‌ای از دکان‌ها
const STORE_SHOPS = Math.max(1, Math.min(SHOPS, Number(arg('--store-shops', SHOPS)) || SHOPS));
const CHECKOUT_SHOPS = Math.max(1, Math.min(SHOPS, Number(arg('--checkout-shops', Math.min(SHOPS, 4))) || 1));
//  `--check`: opِ بی‌پاسخ مثلِ برنامه دوباره می‌رود، در پایان صف‌ها خالی و `scripts/load-check.js` روی پایگاه ⇒ خروجِ ۱ اگر سرخ
const CHECK = has('--check');
const LEDGER_OUT = arg('--ledger', '');
//  `--query-stats` (فقط PostgreSQL): زمانِ هر پرس‌وجو در سرورِ آزمون، به‌جای pg_stat_statements که preload می‌خواهد
const QSTATS = has('--query-stats');
const SETUP_PAR = Math.max(1, Number(arg('--setup-par', 4)) || 1);

/** بودجه‌ها — پیش از آزمون ثابت (بخشِ ۲۹.۶ بندِ ۵)؛ عوض کردنشان پس از اجرا یعنی سبز کردنِ شکست. */
export const BUDGET = Object.freeze({
  p95Ms: 300,            //  §۹.۳ بندِ ۹: «p95 API ≤ ۳۰۰ms و بدون خطا» — برای هر مسیر جدا
  errors: 0,             //  همان بند: بی خطا (هر پاسخِ بیرون از ۲xx، timeout و اتصالِ بریده خطاست)
  soakDriftFactor: 1.5,  //  soak: p95ِ دقیقهٔ آخر ≤ ۱٫۵ × دقیقهٔ اول + ۲۰ms (کند شدنِ تدریجی)
  soakDriftSlackMs: 20,
  heapGrowthMB: 32,      //  soak/فشار: heap پس از GC در پایان ≤ آغاز + بیشینهٔ (۳۲MB، ۲۵٪)
  heapGrowthPct: 25,
  recoverMs: 10000,      //  فشار: ۱۰ ثانیه پس از پایانِ بار، درخواستِ عادی دوباره زیرِ بودجه
});

if (!/^pglite:/i.test(DB)) {
  let name = '';
  try { name = new URL(DB).pathname.replace(/^\//, ''); } catch { /* پایین */ }
  if (!/(test|bench)/i.test(name)) {
    console.error(`⛔ این ابزار پایگاه داده را پاک می‌کند؛ نامِ پایگاه باید test یا bench داشته باشد (داده شد: «${name || DB}»)`);
    process.exit(2);
  }
}

const require = createRequire(import.meta.url);
const hlc = require(path.join(import.meta.dirname, '..', 'src', 'lib', 'hlc.js'));

// ── آمیزهٔ درخواست‌ها (وزن ≈ سهمِ ترافیک) ──
const MIX = Object.freeze({
  'store.home': 6, 'store.products': 16, 'store.search': 12, 'store.product': 14, 'store.checkout': 4, 'store.track': 6,
  'sync3.push': 12, 'sync3.pull': 12, 'desk.list': 9, 'desk.summary': 3, 'auth.login': 2, 'auth.refresh': 4,
});
//  پس از «قطعِ سراسری»: دستگاه‌ها با صفِ پر برمی‌گردند (§۹.۳ بندِ ۹، §۱۵.۹ بندِ ۲)
const BURST_MIX = Object.freeze({ ...MIX, 'sync3.push': 30, 'sync3.pull': 30 });
//  فقط سمتِ مشتری (ویترین): «میلیون‌ها مشتری» همین آمیزه است — بی همگام‌سازی، میز و ورود
const STORE_MIX = Object.freeze({ 'store.home': 6, 'store.products': 16, 'store.search': 12, 'store.product': 14, 'store.checkout': 4, 'store.track': 6 });
const sec = (d) => (DURATION ?? d);
const PROFILE_DEFS = {
  smoke: () => ({ phases: [{ sec: sec(3), rps: RATE }], sampleSec: 1 }),
  load: () => ({ phases: [{ sec: sec(60), rps: RATE }] }),
  peak: () => ({ phases: [{ sec: sec(60), rps: RATE * 2.5 }] }),
  burst: () => ({ phases: [{ sec: 5, rps: RATE }, { sec: 5, rps: RATE * 5, mix: BURST_MIX, stampede: true }, { sec: 10, rps: RATE }] }),
  soak: () => ({ phases: [{ sec: SOAK_SEC, rps: RATE }], sampleSec: 5, gcEverySec: 60, soak: true }),
  //  PERF-04: استخرِ ۲ اتصال و مهلتِ اتصالِ ۱ ثانیه زیرِ ۴ برابرِ بار — رفتارِ کنترل‌شده می‌خواهیم، نه سرعت
  overload: () => ({ serverEnv: { PG_POOL_MAX: 2, PG_CONNECT_TIMEOUT_MS: 1000 }, phases: [{ sec: sec(15), rps: RATE * 4 }], overload: true }),
  //  نقطهٔ شکست: پله‌های پیاپی بی مکث؛ هر پله جدا گزارش می‌شود و «زانو» = آخرین پلهٔ سبز (بودجهٔ کلی سنجیده نمی‌شود)
  knee: () => ({ phases: STEPS.map((k) => ({ sec: sec(20), rps: RATE * k })), knee: true }),
  'knee-store': () => ({ phases: STEPS.map((k) => ({ sec: sec(20), rps: RATE * k, mix: STORE_MIX })), knee: true }),
  //  PERF-02 در مقیاسِ §۹.۳: آمیزهٔ کامل (فروش، ویرایشِ کالا، حرکت، مشتری، نگه‌داشته، ویترین، اشتراک، پنلِ مدیر)، پایدار
  scale: () => ({ phases: [{ sec: sec(600), rps: RATE, mix: SCALE_MIX }], sampleSec: 5, gcEverySec: 60, soak: true }),
  //  «قطعِ سراسری» در همان مقیاس: هر دستگاه صفِ ۳ تا ۱۵ فروش + ویرایش/حرکت/مشتری را با هم می‌فرستد
  'scale-burst': () => ({ phases: [{ sec: 10, rps: RATE, mix: SCALE_MIX }, { sec: 10, rps: RATE * 3, mix: SCALE_BURST_MIX, stampede: 'outage' },
    { sec: 40, rps: RATE, mix: SCALE_MIX }], sampleSec: 2 }),
};
for (const p of PROFILES) if (!PROFILE_DEFS[p]) { console.error(`نمایهٔ ناشناخته: ${p} (${Object.keys(PROFILE_DEFS).join('، ')})`); process.exit(2); }

// ── تصادفیِ seed‌دار (اجرای تکرارپذیر) ──
let rs = SEED >>> 0;
const rnd = () => { rs = (rs + 0x6d2b79f5) >>> 0; let t = rs; t = Math.imul(t ^ (t >>> 15), t | 1); t ^= t + Math.imul(t ^ (t >>> 7), t | 61); return ((t ^ (t >>> 14)) >>> 0) / 4294967296; };
const pick = (a) => a[Math.floor(rnd() * a.length)];
const int = (lo, hi) => lo + Math.floor(rnd() * (hi - lo + 1));
const weighted = (mix) => {
  const entries = Object.entries(mix);
  const total = entries.reduce((s, [, w]) => s + w, 0);
  return () => { let x = rnd() * total; for (const [k, w] of entries) { x -= w; if (x < 0) return k; } return entries[0][0]; };
};

const q = (s, p) => s[Math.min(s.length - 1, Math.max(0, Math.ceil(p * s.length) - 1))];
const r1 = (v) => Math.round(v * 10) / 10;
function summarize(xs) {
  if (!xs.length) return { n: 0 };
  const s = Float64Array.from(xs).sort();
  return { n: s.length, p50: r1(q(s, 0.5)), p95: r1(q(s, 0.95)), p99: r1(q(s, 0.99)), max: r1(s[s.length - 1]) };
}

// ── ثبتِ درخواست‌ها ──
let base = '';
let bases = [];   //  `--instances`: نشانیِ هر نمونه؛ هر درخواست به نوبت
let baseRR = 0;
let rec = null;   //  نمایهٔ در حالِ اجرا؛ null ⇒ گرم‌کردن (ثبت نمی‌شود)
function newRec(name) {
  return { name, t0: performance.now(), ends: 0, by: new Map(), timeline: [], dropped: 0, scheduled: 0, lagMaxMs: 0, recovery: null };
}
function record(label, ms, status, err, bytes, startedAt, ok, errCode) {
  if (!rec) return;
  let e = rec.by.get(label);
  if (!e) rec.by.set(label, e = { lat: [], at: [], codes: {}, errors: 0, firstError: '', bytes: 0 });
  e.lat.push(ms);
  //  زمانِ **آغازِ** درخواست: پنجره بر پایهٔ پایان، کُندهای جامانده پس از پایانِ بار را در پنجرهٔ آخر جمع می‌کرد
  e.at.push((startedAt - rec.t0) / 1000);
  (e.phase ||= []).push(rec.phase || 0);
  //  ۵xx با کدِ خطای بدنه (`503:db_busy`) — «فقط ۵۰۳ ِ کنترل‌شده» را می‌شود جدا سنجید
  const code = err || (status >= 500 && errCode ? `${status}:${errCode}` : String(status));
  e.codes[code] = (e.codes[code] || 0) + 1;
  if (err || !(ok ? ok(status) : status >= 200 && status < 300)) { e.errors++; (e.errAt ||= []).push(rec.phase || 0); }
  e.bytes += bytes;
}
async function hit(label, method, p, { token, body, headers = {}, shop, ok } = {}) {
  const t0 = performance.now();
  let status = 0, err = '', json = null, bytes = 0, text = '';
  try {
    const res = await fetch((bases.length > 1 ? bases[(baseRR++) % bases.length] : base) + p, {
      method,
      headers: { 'Content-Type': 'application/json', 'X-App': 'shop', ...(token ? { Authorization: `Bearer ${token}` } : {}), ...headers },
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: AbortSignal.timeout(TIMEOUT),
    });
    const buf = Buffer.from(await res.arrayBuffer());
    status = res.status; bytes = buf.length; text = buf.toString('utf8');
    try { json = JSON.parse(text); } catch { /* بی JSON */ }
  } catch (e) {
    err = e && (e.name === 'TimeoutError' || e.name === 'AbortError') ? 'timeout' : String((e && e.cause && e.cause.code) || (e && e.name) || 'error');
  }
  const ms = performance.now() - t0;
  const code = json && json.error && typeof json.error === 'object' ? json.error.code : undefined;
  record(label, ms, status, err, bytes, t0, ok, code);
  if (rec && (err || !(ok ? ok(status) : status >= 200 && status < 300))) {
    const e = rec.by.get(label);
    if (e && !e.firstError) e.firstError = err || `${status} ${text.slice(0, 160)}`;
  }
  //  دادهٔ دکانِ دیگر در پاسخِ این دکان (نشان‌های بارساز) ⇒ دفترِ درستی
  if (shop !== undefined && status >= 200 && status < 300 && text) kit.note(label, shop, text);
  return { status, json, err, ms, code, text };
}

// ── سرورِ جدا ──
let msgSeq = 0;
const pending = new Map();
function startServer(cfg) {
  const child = fork(path.join(import.meta.dirname, 'load-test-server.mjs'), [], {
    env: { ...process.env, LOAD_CFG: JSON.stringify(cfg) },
    execArgv: ['--expose-gc', '--max-old-space-size=4096'],
    stdio: QUIET ? ['ignore', 'ignore', 'inherit', 'ipc'] : ['ignore', 'inherit', 'inherit', 'ipc'],
  });
  const ready = new Promise((resolve, reject) => {
    child.on('message', (m) => {
      if (m.type === 'ready') resolve(m);
      else if (pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); }
    });
    child.on('exit', (code) => reject(new Error(`سرورِ آزمون بیرون رفت (${code})`)));
  });
  const ask = (m) => new Promise((resolve) => { const id = ++msgSeq; pending.set(id, resolve); child.send({ ...m, id }); });
  return { child, ready, ask };
}

// ── وضعیتِ سناریوها ──
let shops = [];
let devices = [];
let sessions = [];
let checkoutShops = [];
let storeShops = [];
let opSeq = 0;
const orders = new Map();   //  slug ⇒ توکن‌های پیگیری (حداکثر ۲۰۰ تای آخر)
//  آمیزهٔ scale و دفترِ درستی (`load-scale.mjs`)؛ `state` همیشه همان آرایه‌های زندهٔ این‌جا
const state = { get shops() { return shops; }, get devices() { return devices; }, get storeShops() { return storeShops; }, admin: '', orders, checkouts: {} };

/** opهای v3ِ یک فروشِ واقعی: سند، قلم‌ها، حرکتِ موجودی و تراکنشِ پول (یک tx) */
function saleOps(d, sales) {
  const sh = d.shop;
  const ops = [];
  const H = () => { d.wall = Math.max(Date.now(), d.wall + 1); return hlc.format({ wall: d.wall, counter: 0, node: d.id }); };
  for (let k = 0; k < sales; k++) {
    const n = ++opSeq;
    const sid = `ls-${d.id}-${n}`;
    const tx = `ltx-${d.id}-${n}`;
    const lines = int(1, 4);
    let total = 0;
    const items = [];
    for (let j = 0; j < lines; j++) {
      const p = pick(sh.products);
      const qm = 1000 * int(1, 3);
      const line = Math.round(p.priceMinor * qm / 1000);
      total += line;
      items.push({ op_id: `lo-${d.id}-${n}-i${j}`, tx, hlc: H(), table: 'saleItems', row_id: `${sid}-i${j}`, kind: 'insert',
        fields: { saleId: sid, productId: p.id, productName: p.name, quantityMilli: qm, unitPriceMinor: p.priceMinor,
          purchasePriceMinor: p.buyMinor, totalPriceMinor: line, returnedQtyMilli: 0 } });
      items.push({ op_id: `lo-${d.id}-${n}-m${j}`, tx, hlc: H(), table: 'stockMovements', row_id: `${sid}-m${j}`, kind: 'insert',
        fields: { productId: p.id, qtyMilli: -qm, reason: 'sale', refId: sid } });
    }
    ops.push({ op_id: `lo-${d.id}-${n}-s`, tx, hlc: H(), table: 'sales', row_id: sid, kind: 'insert',
      fields: { totalMinor: total, discountMinor: 0, finalTotalMinor: total, paidAmountMinor: total, remainingMinor: 0,
        paymentMethod: 'cash', status: 'completed' } }, ...items);
    ops.push({ op_id: `lo-${d.id}-${n}-t`, tx, hlc: H(), table: 'transactions', row_id: `${sid}-t`, kind: 'insert',
      fields: { type: 'sale', amountMinor: total, saleId: sid } });
  }
  return ops;
}
/** دستگاهِ آزاد (دستگاهِ واقعی push/pull ِ هم‌زمانِ خودش را ندارد) */
function freeDevice() {
  for (let i = 0; i < 8; i++) { const d = pick(devices); if (!d.busy) return d; }
  return devices.find((d) => !d.busy) || null;
}
async function push(d, large, label) {
  //  کوچک: ۱ تا ۲ فروش پس از هر فروش (برخط)؛ بزرگ: صفِ برگشت از آفلاین (۴۵ تا ۶۰ فروش، سقفِ ۵۰۰ op)
  const ops = saleOps(d, large ? int(45, 60) : int(1, 2)).slice(0, 500);
  const r = await hit(label || (large ? 'sync3.pushLarge' : 'sync3.push'), 'POST', '/api/sync/v3/push',
    { token: d.token || d.shop.token, body: { device_id: d.id, protocol: 3, ops } });
  return { ...r, ops: ops.length };
}
async function pull(d, label = 'sync3.pull') {
  const r = await hit(label, 'GET', `/api/sync/v3/pull?device_id=${encodeURIComponent(d.id)}&since=${d.cursor}`, { token: d.token || d.shop.token });
  if (r.status === 200 && r.json && r.json.cursor !== undefined) d.cursor = r.json.cursor;
  return r;
}
async function withDevice(fn) {
  const d = freeDevice();
  if (!d) return null;
  d.busy = true;
  try { return await fn(d); } finally { d.busy = false; }
}
const storeShop = () => (rnd() < 0.5 ? shops[0] : pick(storeShops));
let ticketRR = 0;
async function checkout(sh = pick(checkoutShops)) {
  const tk = sh.tickets.filter((t) => t.used < 9);
  if (!tk.length) { if (rec) rec.noTicket = (rec.noTicket || 0) + 1; return null; }
  const t = tk[(ticketRR++) % tk.length];
  t.used++;
  const lines = Array.from({ length: int(1, 3) }, () => ({ productId: pick(sh.products).id, qtyMilli: 1000 * int(1, 3) }));
  const uniq = new Map(lines.map((l) => [l.productId, l]));
  const r = await hit('store.checkout', 'POST', `/api/store/${sh.slug}/checkout`, {
    headers: { 'Idempotency-Key': `load-${randomUUID()}`, 'X-Store-Ticket': t.ticket },
    body: { fulfillment: 'pickup', customer: { name: `مشتریِ آزمونِ بار ~S${sh.index}~`, phone: t.phone }, lines: [...uniq.values()] },
    shop: sh.index,
  });
  const tok = r.json && r.json.order && (r.json.order.token || r.json.order.trackToken);
  //  دفترِ درستی: سفارشِ پذیرفته، و سرانجامِ نامعلوم (مهلت/۵xx) — سرور باید میانِ این دو باشد
  const c = state.checkouts[sh.index] ||= { ok: 0, unknown: 0 };
  if (tok) c.ok++; else if (r.err || r.status >= 500) c.unknown++;
  if (tok) { const a = orders.get(sh.slug) || []; a.push(tok); if (a.length > 200) a.shift(); orders.set(sh.slug, a); }
  return r;
}
const SCENARIO = {
  'store.home': () => { const sh = storeShop(); return hit('store.home', 'GET', `/api/store/${sh.slug}`, { shop: sh.index }); },
  'store.products': () => { const sh = storeShop(); return hit('store.products', 'GET', `/api/store/${sh.slug}/products`, { shop: sh.index }); },
  'store.search': () => { const sh = storeShop(); return hit('store.search', 'GET', `/api/store/${sh.slug}/products?q=${encodeURIComponent(pick(sh.terms))}`, { shop: sh.index }); },
  'store.product': () => { const sh = storeShop(); return hit('store.product', 'GET', `/api/store/${sh.slug}/products/${encodeURIComponent(pick(sh.products).id)}`, { shop: sh.index }); },
  'store.checkout': () => checkout(),
  'store.track': () => {
    const sh = pick(checkoutShops);
    const a = orders.get(sh.slug);
    if (!a || !a.length) return checkout(sh);
    return hit('store.track', 'GET', `/api/store/${sh.slug}/orders/${encodeURIComponent(pick(a))}`, { shop: sh.index });
  },
  'sync3.push': () => withDevice((d) => push(d, rnd() < 0.1)),
  'sync3.pull': () => withDevice((d) => pull(d)),
  'desk.list': () => {
    const sh = rnd() < 0.6 ? pick(checkoutShops) : pick(shops);
    return hit('desk.list', 'GET', `/api/shop/orders?tab=${pick(['new', 'preparing', 'done'])}&limit=50`, { token: sh.token, shop: sh.index });
  },
  'desk.summary': () => { const sh = pick(checkoutShops); return hit('desk.summary', 'GET', '/api/shop/orders/summary', { token: sh.token, shop: sh.index }); },
  'auth.login': () => {
    const sh = pick(shops);
    return hit('auth.login', 'POST', '/api/auth/login', {
      body: { identifier: sh.email, password: sh.password, app: 'shop', device: { deviceId: `load-login-${sh.index}`, name: 'آزمونِ بار', platform: 'test' } },
    });
  },
  'auth.refresh': async () => {
    const s = sessions.pop();
    if (!s) return SCENARIO['auth.login']();
    const r = await hit('auth.refresh', 'POST', '/api/auth/refresh', { body: { refreshToken: s.refreshToken } });
    if (r.status === 200 && r.json && r.json.refreshToken) s.refreshToken = r.json.refreshToken;
    if (r.status === 200 || r.err) sessions.unshift(s);   //  نشستِ ردشده (۴۰۱) کنار می‌رود و خطاست
    return r;
  },
};
const kit = scaleKit({ hit, rnd, pick, int, hlc, state, withDevice, retry: CHECK });
for (const [k, fn] of Object.entries(kit.SCENARIOS)) {
  //  بی `--check` push/pull ِ همیشگی همان می‌مانند (عددِ نمایه‌های پیشین هم‌سنج بماند)؛ با آن، همه از راهِ دفتر
  if (!SCENARIO[k] || CHECK) SCENARIO[k] = fn;
}

// ── زمان‌بندِ بارِ باز ──
const inflight = new Set();
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
function launch(fn) {
  if (inflight.size >= MAX_INFLIGHT) { if (rec) { rec.dropped++; (rec.droppedBy ||= {})[rec.phase || 0] = ((rec.droppedBy || {})[rec.phase || 0] || 0) + 1; } return; }
  const p = Promise.resolve().then(fn).catch((e) => { if (rec) rec.generatorErrors = (rec.generatorErrors || 0) + 1, rec.generatorError ||= String(e && e.stack || e); });
  inflight.add(p);
  p.finally(() => inflight.delete(p));
}
async function runPhases(phases) {
  for (const [pi, ph] of phases.entries()) {
    if (rec) rec.phase = pi;
    const next = weighted(ph.mix || MIX);
    if (ph.stampede === 'outage') for (const d of devices) launch(() => kit.outage(d));
    else if (ph.stampede) for (const d of devices) launch(async () => { if (d.busy) return; d.busy = true; try { await push(d, true, 'sync3.pushLarge'); await pull(d); } finally { d.busy = false; } });
    const t0 = performance.now();
    let sent = 0;
    for (;;) {
      const el = performance.now() - t0;
      if (el >= ph.sec * 1000) break;
      const due = Math.floor((el / 1000) * ph.rps);
      if (rec && due - sent > 1) rec.lagMaxMs = Math.max(rec.lagMaxMs, ((due - sent) / ph.rps) * 1000);
      while (sent < due) { sent++; if (rec) rec.scheduled++; launch(SCENARIO[next()]); }
      await sleep(2);
    }
  }
  await Promise.allSettled([...inflight]);
}

// ── نتیجه و بودجه ──
function report(r, server) {
  const durSec = (r.ends - r.t0) / 1000;
  const endpoints = {};
  const all = [];
  for (const [label, e] of [...r.by.entries()].sort()) {
    all.push(...e.lat);
    endpoints[label] = { ...summarize(e.lat), rps: r1(e.lat.length / durSec), errors: e.errors, errorPct: r1((e.errors / e.lat.length) * 100),
      codes: e.codes, avgKB: r1(e.bytes / e.lat.length / 1024), ...(e.firstError ? { firstError: e.firstError } : {}) };
  }
  //  کند شدنِ تدریجی: p95ِ همهٔ درخواست‌ها در هر پنجره (۶۰ ثانیه در soak، ۱۰ ثانیه در بقیه)
  const win = r.soak ? 60 : 10;
  const windows = [];
  for (const e of r.by.values()) e.at.forEach((t, i) => { const w = Math.floor(t / win); (windows[w] ||= []).push(e.lat[i]); });
  //  پنجرهٔ نیمه‌کارهٔ آخر (کمتر از نیمِ پنجره از بار) کنار می‌رود — چند درخواستِ تهِ بار نماینده نیستند
  const drift = windows.map((xs, i) => ({ fromSec: i * win, ...summarize(xs || []) }))
    .filter((w) => w.n && durSec - w.fromSec >= win / 2);
  //  همان پنجره‌ها به‌ازای هر مسیر (p50/p95): کند شدنِ همه (ماشین) از کند شدنِ یک مسیر (رشدِ داده) جدا می‌شود
  const driftBy = {};
  for (const [label, e] of r.by.entries()) {
    const ws = [];
    e.at.forEach((t, i) => { (ws[Math.floor(t / win)] ||= []).push(e.lat[i]); });
    driftBy[label] = drift.map((w) => { const x = summarize(ws[w.fromSec / win] || []); return [x.p50 ?? null, x.p95 ?? null]; });
  }
  const gcd = server.filter((s) => s.gc);
  const knee = r.phases ? kneeOf(r) : null;
  //  گروهِ مسیرها (p50/p95/p99 ِ همهٔ درخواست‌های گروه، نه میانگینِ مسیرها)؛ ۴۲۹ و ۵xx جدا
  const groups = {};
  for (const [label, e] of r.by.entries()) {
    const g = groupOf(label);
    const x = groups[g] ||= { lat: [], errors: 0, r429: 0, r5xx: 0 };
    for (const v of e.lat) x.lat.push(v);   //  نه `push(...lat)`: soak ِ بلند ده‌ها هزار عدد دارد
    x.errors += e.errors;
    for (const [c, n] of Object.entries(e.codes)) { if (c === '429') x.r429 += n; if (/^5/.test(c)) x.r5xx += n; }
  }
  for (const [g, x] of Object.entries(groups)) {
    const s = summarize(x.lat);
    groups[g] = { ...s, rps: r1(s.n / durSec), errorPct: r1((x.errors / s.n) * 100), r429Pct: r1((x.r429 / s.n) * 100), r5xx: x.r5xx };
  }
  const out = {
    profile: r.name, durationSec: r1(durSec), scheduled: r.scheduled, dropped: r.dropped, generatorLagMaxMs: r1(r.lagMaxMs),
    ...(r.noTicket ? { noTicket: r.noTicket } : {}), ...(r.generatorErrors ? { generatorErrors: r.generatorErrors, generatorError: r.generatorError } : {}),
    total: { ...summarize(all), rps: r1(all.length / durSec), errors: Object.values(endpoints).reduce((s, e) => s + e.errors, 0) },
    endpoints, groups, windowSec: win, drift, driftBy,
    server: {
      samples: server,
      rssMaxMB: Math.max(...server.map((s) => s.rssMB)), heapMaxMB: Math.max(...server.map((s) => s.heapMB)),
      heapAfterGcStartMB: gcd[0]?.heapMB ?? null, heapAfterGcEndMB: gcd[gcd.length - 1]?.heapMB ?? null,
      poolWaitingMax: Math.max(0, ...server.map((s) => (s.pool ? s.pool.waiting : 0))),
      poolTotalMax: Math.max(0, ...server.map((s) => (s.pool ? s.pool.total : 0))),
      pgLockWaitMax: Math.max(0, ...server.map((s) => (s.pg ? s.pg.lock_wait : 0))),
      pgIdleInTxMax: Math.max(0, ...server.map((s) => (s.pg ? s.pg.idle_in_tx : 0))),
      gateWaitingMax: Math.max(0, ...server.map((s) => (s.gate?.on ? s.gate.waiting : 0))),
      gateInflightMax: Math.max(0, ...server.map((s) => (s.gate?.on ? s.gate.inflight : 0))),
      loopMaxMs: Math.max(...server.map((s) => s.loopMaxMs)), cpuMaxPct: Math.max(...server.map((s) => s.cpuPct)),
    },
    ...(r.recovery ? { recovery: r.recovery } : {}),
    ...(knee ? { steps: knee.steps, knee: knee.knee } : {}),
  };
  return out;
}
/*
 *  knee: هر پله جدا. «سبز» = بی خطا و p95 ِ هر مسیر ≤ بودجه — جز دو استثنای شناخته‌شدهٔ همیشگی (ورود با
 *  scrypt ِ p=5 و push ِ بزرگ ِ ۲۰۰ تا ۵۰۰ op، PERF-LOAD «مانده» ۱ و ۲) که جدا نشان داده می‌شوند.
 */
const KNEE_EXEMPT = new Set(['auth.login', 'sync3.pushLarge']);
const GROUP_OF = Object.freeze({ 'store.home': 'store.browse', 'store.products': 'store.browse', 'store.search': 'store.browse', 'store.product': 'store.browse',
  'xt.probe': 'store.browse', 'store.checkout': 'store.order', 'store.track': 'store.order', 'desk.list': 'owner.desk', 'desk.summary': 'owner.desk',
  'sync3.push': 'sync.push', 'sync3.edit': 'sync.push', 'sync3.stock': 'sync.push', 'sync3.customer': 'sync.push', 'sync3.pushLarge': 'sync.pushLarge',
  'sync3.pushOutage': 'sync.outage', 'sync3.pullOutage': 'sync.outage', 'sync3.pull': 'sync.pull' });
const groupOf = (label) => GROUP_OF[label] || (label.startsWith('admin.') ? 'admin' : label.startsWith('held.') ? 'held' : label.startsWith('auth.') ? 'auth' : label);
const KNEE_GROUPS = Object.freeze({
  'store.read': ['store.home', 'store.products', 'store.search', 'store.product'],
  'store.checkout': ['store.checkout'], 'store.track': ['store.track'], 'sync3.push': ['sync3.push'], 'sync3.pull': ['sync3.pull'],
  desk: ['desk.list', 'desk.summary'], 'auth.refresh': ['auth.refresh'], 'auth.login': ['auth.login'], 'sync3.pushLarge': ['sync3.pushLarge'],
});
function kneeOf(r) {
  const steps = r.phases.map((ph, pi) => {
    const by = {};
    let all = [], errors = 0, exemptErrors = 0, worst = 0, worstLabel = '';
    for (const [label, e] of r.by.entries()) {
      const xs = [];
      let errs = 0;
      e.phase.forEach((p, i) => {
        if (p !== pi) return;
        xs.push(e.lat[i]);
      });
      if (e.errAt) e.errAt.forEach((p) => { if (p === pi) errs++; });
      by[label] = { ...summarize(xs), errors: errs };
      all = all.concat(xs);
      if (KNEE_EXEMPT.has(label)) { exemptErrors += errs; continue; }
      errors += errs;
      if (xs.length && by[label].p95 > worst) { worst = by[label].p95; worstLabel = label; }
    }
    const groups = {};
    for (const [g, labels] of Object.entries(KNEE_GROUPS)) {
      const xs = [];
      for (const l of labels) { const e = r.by.get(l); if (e) e.phase.forEach((p, i) => { if (p === pi) xs.push(e.lat[i]); }); }
      if (xs.length) { const s = summarize(xs); groups[g] = { n: s.n, rps: r1(s.n / ph.sec), p95: s.p95 }; }
    }
    const t = summarize(all);
    const dropped = (r.droppedBy && r.droppedBy[pi]) || 0;
    return { offeredRps: ph.rps, rps: r1(t.n / ph.sec), n: t.n, p50: t.p50, p95: t.p95, p99: t.p99, errors, exemptErrors, dropped,
      worst: { label: worstLabel, p95: worst }, ok: errors === 0 && dropped === 0 && worst <= BUDGET.p95Ms, groups };
  });
  let knee = null;
  for (const st of steps) { if (!st.ok) break; knee = st; }
  return { steps, knee: knee ? { offeredRps: knee.offeredRps, rps: knee.rps, groups: knee.groups } : null };
}
function evaluate(o, def) {
  const fails = [];
  if (def.knee) return fails;   //  نتیجه همان «زانو» است، نه سبز/سرخ
  const heap = o.server;
  const heapLimit = heap.heapAfterGcStartMB != null ? heap.heapAfterGcStartMB + Math.max(BUDGET.heapGrowthMB, heap.heapAfterGcStartMB * BUDGET.heapGrowthPct / 100) : null;
  const heapCheck = () => {
    if (heapLimit != null && heap.heapAfterGcEndMB > heapLimit) fails.push(`heap پس از GC ${heap.heapAfterGcStartMB} ⇐ ${heap.heapAfterGcEndMB}MB (سقف ${r1(heapLimit)}MB)`);
  };
  if (def.overload) {
    //  PERF-04: زیرِ فشار فقط پاسخِ کنترل‌شده (۲xx، ۴۲۹، ۵۰۳) — نه ۵۰۰، نه بی‌پاسخی، نه اتصالِ بریده
    for (const [label, e] of Object.entries(o.endpoints)) {
      const bad = Object.entries(e.codes).filter(([c]) => !/^(2\d\d|429|503)(:|$)/.test(c));
      if (bad.length) fails.push(`${label}: پاسخِ کنترل‌نشده ${bad.map(([c, n]) => `${c}×${n}`).join('، ')}`);
    }
    if (!o.recovery || !o.recovery.ok) fails.push(`بازگشت پس از فشار: ${o.recovery ? o.recovery.detail : 'سنجیده نشد'}`);
    heapCheck();
    return fails;
  }
  for (const [label, e] of Object.entries(o.endpoints)) {
    if (e.p95 > BUDGET.p95Ms) fails.push(`${label}: p95 ${e.p95}ms > ${BUDGET.p95Ms}ms`);
    if (e.errors > BUDGET.errors) fails.push(`${label}: ${e.errors} خطا از ${e.n} (${Object.entries(e.codes).filter(([c]) => !/^2/.test(c)).map(([c, n]) => `${c}×${n}`).join('، ')})`);
  }
  if (def.soak && o.drift.length >= 2) {
    const first = o.drift[0], last = o.drift[o.drift.length - 1];
    const lim = first.p95 * BUDGET.soakDriftFactor + BUDGET.soakDriftSlackMs;
    if (last.p95 > lim) fails.push(`soak: p95 از ${first.p95} به ${last.p95}ms رسید (سقف ${r1(lim)}ms)`);
  }
  if (def.soak) heapCheck();
  return fails;
}
const pad = (s, n) => String(s).padStart(n);
function print(o, fails) {
  console.log(`\n── ${o.profile} (بارِ ماشین ${o.loadAvgStart[0]} ⇐ ${o.loadAvgEnd[0]}): ${o.durationSec}s، ${o.total.n} درخواست (${o.total.rps}/s)، خطا ${o.total.errors}، جاافتاده ${o.dropped}، تأخیرِ بارساز ≤ ${o.generatorLagMaxMs}ms`);
  console.log(`  ${'مسیر'.padEnd(18)} ${pad('n', 6)} ${pad('rps', 6)} ${pad('p50', 7)} ${pad('p95', 7)} ${pad('p99', 7)} ${pad('max', 7)}  خطا`);
  for (const [label, e] of Object.entries(o.endpoints)) {
    console.log(`  ${label.padEnd(18)} ${pad(e.n, 6)} ${pad(e.rps, 6)} ${pad(e.p50, 7)} ${pad(e.p95, 7)} ${pad(e.p99, 7)} ${pad(e.max, 7)}  ${e.errors ? `${e.errors} (${e.firstError})` : '0'}`);
  }
  console.log(`  گروه‌ها: ${Object.entries(o.groups).map(([g, x]) => `${g} ${x.n}× p50 ${x.p50} p95 ${x.p95} p99 ${x.p99}${x.errorPct ? ` خطا ${x.errorPct}%` : ''}${x.r429Pct ? ` ۴۲۹ ${x.r429Pct}%` : ''}`).join(' | ')}`);
  const s = o.server;
  console.log(`  سرور: RSS ≤ ${s.rssMaxMB}MB، heap ≤ ${s.heapMaxMB}MB، heap پس از GC ${s.heapAfterGcStartMB} ⇐ ${s.heapAfterGcEndMB}MB، CPU ≤ ${s.cpuMaxPct}%، حلقهٔ رویداد ≤ ${s.loopMaxMs}ms، استخر ≤ ${s.poolTotalMax} اتصال، صفِ انتظار ≤ ${s.poolWaitingMax}، اتصالِ پشتِ قفل ≤ ${s.pgLockWaitMax}، idle-in-tx ≤ ${s.pgIdleInTxMax}، دروازهٔ push: منتظر ≤ ${s.gateWaitingMax}، در کار ≤ ${s.gateInflightMax}`);
  if (o.drift.length > 1) console.log(`  p95 در هر ${o.windowSec}s: ${o.drift.map((w) => w.p95).join(' ')}`);
  if (o.queries) {
    console.log('  پرس‌وجوهای پرهزینه (کلِ ms، بار، میانگین، بیشینه):');
    for (const x of o.queries.slice(0, 12)) console.log(`   ${pad(Math.round(x.totalMs), 8)} ${pad(x.calls, 7)} ${pad(x.meanMs, 7)} ${pad(x.maxMs, 7)}  ${x.sql.slice(0, 150)}`);
  }
  if (o.recovery) console.log(`  بازگشت: ${o.recovery.ok ? '✅' : '❌'} ${o.recovery.detail}`);
  if (o.steps) {
    console.log(`  پله‌ها (خواسته ⇒ رسیده/s، p95 ِ همه، بدترین مسیر، خطا [+استثنا]، جاافتاده؛ p95 ِ گروه‌ها):`);
    for (const st of o.steps) {
      const g = Object.entries(st.groups).map(([k, v]) => `${k} ${v.rps}/s ${v.p95}`).join('، ');
      console.log(`   ${st.ok ? '✅' : '❌'} ${pad(st.offeredRps, 5)} ⇒ ${pad(st.rps, 6)}/s  p95 ${pad(st.p95, 7)}  ${st.worst.label} ${st.worst.p95}  خطا ${st.errors}${st.exemptErrors ? ` [+${st.exemptErrors}]` : ''}  جاافتاده ${st.dropped}  | ${g}`);
    }
    console.log(o.knee ? `  زانو: ${o.knee.offeredRps}/s خواسته (${o.knee.rps}/s رسیده)` : '  زانو: حتی نخستین پله سبز نبود');
  }
  if (BUDGET_ON) console.log(fails.length ? `  ❌ بودجه:\n    - ${fails.join('\n    - ')}` : '  ✅ همهٔ بودجه‌ها');
}

// ── اجرا ──
const plannedCheckouts = (defs) => {
  const w = (m) => (m['store.checkout'] + m['store.track'] * 0.05) / Object.values(m).reduce((s, x) => s + x, 0);
  return defs.reduce((s, d) => s + d.phases.reduce((t, ph) => t + ph.sec * ph.rps * w(ph.mix || MIX), 0), 0) + WARMUP * RATE * w(MIX);
};
const groups = new Map();
for (const name of PROFILES) {
  const def = { name, ...PROFILE_DEFS[name]() };
  const key = JSON.stringify(def.serverEnv || {});
  if (!groups.has(key)) groups.set(key, []);
  groups.get(key).push(def);
}

const result = {
  at: new Date().toISOString(),
  env: { cpu: os.cpus()[0]?.model, cores: os.cpus().length, memGB: Math.round(os.totalmem() / 2 ** 30), os: `${os.platform()} ${os.release()}`, node: process.version,
    driver: /^pglite:/i.test(DB) ? 'pglite' : 'postgres', loadAvg: os.loadavg().map(r1) },
  options: { rate: RATE, shops: SHOPS, devices: DEVICES, scale: SCALE, smallScale: SMALL_SCALE, history: HISTORY, timeoutMs: TIMEOUT, seed: SEED, warmupSec: WARMUP,
    instances: INSTANCES, cluster: CLUSTER, steps: STEPS },
  budget: BUDGET, setups: [], profiles: [],
};
const genLoop = monitorEventLoopDelay({ resolution: 10 });
genLoop.enable();
let failed = false, invalid = false;

for (const defs of groups.values()) {
  const checkoutShopsN = CHECKOUT_SHOPS;
  const phonesPerShop = Math.max(3, Math.ceil((plannedCheckouts(defs) * 1.3 + checkoutShopsN * 4) / checkoutShopsN / 9));
  const serverEnv = { ...(CLUSTER ? { CLUSTER_ENABLED: 1 } : {}), ...(defs[0].serverEnv || {}) };
  const srv = startServer({ db: DB, shops: SHOPS, scale: SCALE, smallScale: SMALL_SCALE, checkoutShops: checkoutShopsN, phonesPerShop, serverEnv,
    queryStats: QSTATS, setupPar: SETUP_PAR });
  const tReady = performance.now();
  const ready = await srv.ready;
  base = ready.base;
  //  `--instances`: نمونه‌های دیگر پس از کاشتن، روی همان پایگاه (بی پاک کردن و بی کاشتن)
  const followers = [];
  for (let i = 1; i < INSTANCES; i++) followers.push(startServer({ db: DB, follower: true, serverEnv }));
  bases = [base, ...(await Promise.all(followers.map((f) => f.ready))).map((m) => m.base)];
  const servers = [srv, ...followers];
  shops = ready.shops;
  checkoutShops = shops.slice(0, checkoutShopsN);
  storeShops = shops.slice(0, Math.max(STORE_SHOPS, checkoutShopsN));
  state.admin = ready.admin || '';
  for (const sh of checkoutShops) for (const t of sh.tickets) t.used = 0;
  devices = shops.flatMap((sh) => Array.from({ length: DEVICES }, (_, k) => ({ shop: sh, id: `ld-${sh.index}-${k}`, cursor: 0, busy: false, wall: Date.now() })));
  orders.clear();
  sessions = [];
  const setup = { profiles: defs.map((d) => d.name), serverEnv: defs[0].serverEnv || {}, readyMs: Math.round(performance.now() - tReady), dataset: ready.dataset, phonesPerShop };
  console.log(`سرور آماده (${ready.dataset.server}): ${ready.dataset.rows} ردیفِ دفتر در ${SHOPS} دکان، ${Math.round(setup.readyMs / 1000)}s`);

  //  گرم‌کردن (ثبت نمی‌شود): نشستِ هر دستگاه، تاریخچهٔ v3، همگام شدنِ هر دستگاه تا سرِ دفتر،
  //  نشست‌های تازه‌سازی، چند سفارش و JIT
  const tw = performance.now();
  const wstep = (label) => console.log(`  [گرم‌کردن] ${label} — ${Math.round((performance.now() - tw) / 1000)}s`);
  const must = (r, what) => { if (!r || r.status < 200 || r.status >= 300) throw new Error(`${what}: ${r ? `${r.status || r.err} ${JSON.stringify(r.json).slice(0, 300)}` : 'بی پاسخ'}`); return r; };
  const login = async (sh, deviceId) => must(await hit('auth.login', 'POST', '/api/auth/login', {
    body: { identifier: sh.email, password: sh.password, app: 'shop', device: { deviceId, name: 'آزمونِ بار', platform: 'test' } } }), 'ورود').json;
  //  چند دکان با هم (قفلِ هر دکان جداست؛ ترتیبِ کارِ هر دستگاه همان)
  const parallel = async (xs, fn) => { for (let i = 0; i < xs.length; i += SETUP_PAR) await Promise.all(xs.slice(i, i + SETUP_PAR).map(fn)); };
  //  هر دستگاه نشستِ خودش را دارد (همان برنامهٔ واقعی) — توکنِ یک دستگاه برای دستگاهِ دیگر نیست
  await parallel(devices, async (d) => { d.token = (await login(d.shop, d.id)).accessToken; });
  wstep(`${devices.length} نشستِ دستگاه`);
  let histOps = 0;
  await parallel(shops, async (sh) => {
    const d = { shop: sh, id: `lh-${sh.index}`, cursor: 0, busy: false, wall: Date.now() - 3_600_000 };
    d.token = (await login(sh, d.id)).accessToken;
    const target = sh.index === 0 ? HISTORY : Math.min(150, HISTORY);
    for (let n = 0; n < target;) {
      //  `--check`: تاریخچه هم در دفتر (همان op ها در pull ِ دستگاه‌ها سنجیده می‌شوند)
      const r = must(CHECK ? await kit.pushUnits(d, kit.sales(d, int(45, 60)), 'sync3.pushLarge') : await push(d, true), 'تاریخچهٔ v3');
      n += r.ops; histOps += r.ops;
    }
    //  واحدهایی که در ۵۰۰ op ِ آخر جا نشدند هم بروند (وگرنه در دفتر «نفرستاده» می‌مانند)
    while (d.queue && d.queue.length) histOps += must(await kit.pushUnits(d, [], 'sync3.pushLarge'), 'تاریخچهٔ v3').ops;
  });
  wstep(`تاریخچهٔ v3 (${histOps} op)`);
  const pullFn = CHECK ? kit.pull : pull;
  await parallel(devices, async (d) => { for (let i = 0; i < 100; i++) { const r = must(await pullFn(d), 'pull ِ آغاز'); if (!r.json.has_more) break; } });
  wstep('همهٔ دستگاه‌ها تا سرِ دفتر');
  //  نشست‌های تازه‌سازی از ۲۰ دکانِ نخست بس است (هر ورود یک scrypt ِ p=5)
  for (const sh of shops.slice(0, 20)) for (let k = 0; k < 2; k++) {
    const j = await login(sh, `load-refresh-${sh.index}-${k}`);
    if (!j.refreshToken) throw new Error('پاسخِ ورود refreshToken نداشت');
    sessions.push({ refreshToken: j.refreshToken });
  }
  for (const sh of checkoutShops) for (let k = 0; k < 3; k++) must(await checkout(sh), 'سفارشِ آغاز');
  if (!orders.size) throw new Error('پاسخِ سفارش توکنِ پیگیری نداشت');
  wstep('نشست‌ها و سفارش‌های آغاز');
  if (WARMUP > 0) await runPhases([{ sec: WARMUP, rps: RATE, mix: defs[0].phases[0].mix }]);
  setup.warmupMs = Math.round(performance.now() - tw);
  setup.v3HistoryOps = histOps;
  result.setups.push(setup);

  for (const def of defs) {
    await sleep(1000);
    const samples = [];
    const sampleOnce = async (gc) => {
      const t = rec ? r1((performance.now() - rec.t0) / 1000) : 0;
      const ms = await Promise.all(servers.map((x) => x.ask({ type: 'stats', gc })));
      ms.forEach((m, inst) => samples.push({ t, ...(servers.length > 1 ? { inst } : {}), ...m.stats }));
    };
    await Promise.all(servers.map((x) => x.ask({ type: 'stats' })));   //  پنجرهٔ CPU/حلقه از همین لحظه
    if (QSTATS) await srv.ask({ type: 'qstats', reset: true });
    genLoop.reset();
    const loadStart = os.loadavg().map(r1);
    rec = newRec(def.name);
    rec.soak = !!def.soak;
    if (def.knee) rec.phases = def.phases;
    await sampleOnce(true);
    let lastGc = performance.now();
    const timer = setInterval(() => {
      const gc = def.gcEverySec && performance.now() - lastGc >= def.gcEverySec * 1000;
      if (gc) lastGc = performance.now();
      sampleOnce(!!gc).catch(() => {});
    }, (def.sampleSec || 2) * 1000);
    await runPhases(def.phases);
    clearInterval(timer);
    rec.ends = performance.now();
    if (def.overload) {
      //  بازگشت: بی بار، درخواستِ عادی باید زود دوباره زیرِ بودجه باشد
      const t0 = performance.now();
      const live = rec;
      rec = null;
      let detail = '', ok = false;
      while (performance.now() - t0 < BUDGET.recoverMs && !ok) {
        const xs = [];
        let bad = 0;
        for (let i = 0; i < 10; i++) {
          for (const k of ['store.products', 'desk.list', 'sync3.pull']) { const r = await SCENARIO[k](); if (!r) continue; xs.push(r.ms); if (r.status !== 200) bad++; }
        }
        const s = summarize(xs);
        ok = bad === 0 && s.p95 <= BUDGET.p95Ms;
        detail = `${r1(performance.now() - t0)}ms پس از پایانِ بار: ${xs.length} درخواست، ${bad} خطا، p95 ${s.p95}ms`;
        if (!ok) await sleep(500);
      }
      rec = live;
      rec.recovery = { ok, detail };
    }
    await sampleOnce(true);
    const o = report(rec, samples);
    o.loadAvgStart = loadStart; o.loadAvgEnd = os.loadavg().map(r1);
    o.generatorLoopMaxMs = r1(genLoop.max / 1e6);
    if (!/^pglite:/i.test(DB)) o.dbBytes = (await srv.ask({ type: 'dbsize' })).bytes;
    if (QSTATS) o.queries = (await srv.ask({ type: 'qstats' })).top;
    rec = null;
    const fails = evaluate(o, def);
    o.budgetFails = fails;
    //  در `overload` جاافتادن خودش نتیجه است (سقفِ درخواستِ باز پر شد چون سرور پس نمی‌زند)، نه اشباعِ بارساز
    if (!def.overload && !def.knee && o.dropped > o.scheduled * 0.01) { invalid = true; fails.push(`بارساز اشباع شد: ${o.dropped} از ${o.scheduled} درخواست جا افتاد (عدد معتبر نیست)`); }
    if (fails.length) failed = true;
    result.profiles.push(o);
    print(o, fails);
    if (JSON_OUT) fs.writeFileSync(JSON_OUT, JSON.stringify(result, null, 2));
  }
  if (CHECK) {
    //  درستی زیرِ بار: صف‌ها خالی، هر دستگاه تا سرِ دفتر، سپس `load-check.js` روی همان پایگاه (درونِ سرورِ آزمون)
    const td = performance.now();
    const drained = await kit.drain({ par: SETUP_PAR * 2 });
    const file = LEDGER_OUT || path.join(os.tmpdir(), `load-ledger-${process.pid}.json`);
    //  کدهای پاسخِ همهٔ نمایه‌ها (فقط ۵۰۳ ِ `db_busy` ِ کنترل‌شده پذیرفته است)
    const http = {};
    for (const o of result.profiles) for (const [label, e] of Object.entries(o.endpoints)) for (const [c, n] of Object.entries(e.codes)) (http[label] ||= {})[c] = (http[label][c] || 0) + n;
    fs.writeFileSync(file, JSON.stringify({ ...kit.ledger(), http, checkouts: state.checkouts, drained }));
    const res = await srv.ask({ type: 'check', ledger: file });
    if (!LEDGER_OUT) fs.rmSync(file, { force: true });
    const check = { drain: { ...drained, ms: Math.round(performance.now() - td) }, ...(res.check || { ok: false, error: res.error || 'بی پاسخ' }) };
    result.check = check;
    console.log(`\n── درستی پس از بار (پایان‌دادنِ صف‌ها: ${drained.pushes} push، ${drained.pulls} pull، ${drained.left} مانده، ${check.drain.ms}ms)`);
    for (const c of check.checks || []) {
      console.log(`  ${c.ok ? '✅' : '❌'} ${c.name}: ${c.detail}`);
      if (!c.ok) for (const x of c.samples || []) console.log(`       ${x}`);
    }
    if (check.error) console.log(`  ❌ ${check.error}`);
    if (!check.ok) failed = true;
  }
  for (const x of [...followers, srv]) {
    x.child.removeAllListeners('exit');
    await new Promise((resolve) => { x.child.once('exit', resolve); x.child.send({ type: 'stop' }); setTimeout(() => { x.child.kill('SIGKILL'); resolve(); }, 15000).unref(); });
  }
}

if (JSON_OUT) fs.writeFileSync(JSON_OUT, JSON.stringify(result, null, 2));
process.exit(invalid ? 3 : ((failed && BUDGET_ON) || (CHECK && result.check && !result.check.ok) ? 1 : 0));
