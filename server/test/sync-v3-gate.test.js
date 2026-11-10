'use strict';
/**
 * PERF-02/PERF-04: دروازهٔ push ِ v3 (`lib/sync-v3-gate.js`) — صفِ هر دکان و سقفِ push ِ هم‌زمان
 * **پیش از** گرفتنِ اتصالِ استخر، و پس‌زدنِ زود (۵۰۳ ِ `db_busy`).
 *
 *   • درستی: N دستگاهِ یک دکان هم‌زمان push می‌کنند ⇒ همان ردیف‌ها، همان ترتیبِ دفتر، همان
 *     تکراری‌ها و همان ردها که اجرای پشتِ سرِ همِ همان دسته‌ها به همان ترتیب روی دکانِ دوم؛
 *     و opهای هر push در دفتر پیوسته‌اند (هیچ دو push ِ یک دکان در هم نرفتند).
 *   • اتصالِ بیکار: push های منتظرِ همان دکان اتصالِ استخر نمی‌گیرند (پیش از این ۵ از ۵).
 *   • پس‌زدن: انتظارِ بلند، صفِ پر، سقفِ push، و صفِ استخرِ پر ⇒ ۵۰۳ ِ `db_busy` با `Retry-After`؛ هیچ ۵۰۰.
 * سه‌تای آخر فقط PostgreSQL (PGlite استخر ندارد و دروازه رویش خاموش است).
 */
process.env.SYNC_V3_ENABLED = '1';
process.env.PG_POOL_MAX = '8';
process.env.RATE_LOGIN_EDGE_MAX ||= '100000';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const db = require('../src/db');
const config = require('../src/config');
const gate = require('../src/lib/sync-v3-gate');
const hlc = require('../src/lib/hlc');

const PG = !db.isPglite();
const NO_PG = !PG && 'PGlite: استخر ندارد و دروازه رویش خاموش است';
const BASE = Date.now() - 3_600_000;
let tick = 0;
const H = (node) => hlc.format({ wall: BASE + (++tick) * 10, counter: 0, node });
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const push = (token, device, ops) => h.api('POST', '/api/sync/v3/push', { token, body: { device_id: device, protocol: 3, ops } });

async function newShop(name) {
  let owner = await h.newUser(`صاحبِ ${name}`, 'shop');
  const shop = await h.api('POST', '/api/shop', { token: owner.accessToken, body: { name } });
  const id = shop.body?.shop?.id || shop.body?.id;
  owner = await h.signIn(owner, 'shop');
  const s = await h.newUser(`شاگردِ ${name}`, 'shop');
  await db.query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at)
     VALUES ($1,$2,$3,'staff','active',$4,$4)`, [db.newId('mem'), id, s.user.id, db.now()]);
  const staff = await h.signIn(s, 'shop');
  return { id, owner: owner.accessToken, staff: staff.accessToken, ids: [id, owner.user.id, s.user.id] };
}

let shopA;   //  هم‌زمان
let shopB;   //  پشتِ سرِ هم
let shopC;   //  آزمون‌های استخر و پس‌زدن
let shopD;
test.before(async () => {
  await h.start();
  await require('../src/lib/sync-v1').syncSchemaTable();
  shopA = await newShop('دکانِ هم‌زمان');
  shopB = await newShop('دکانِ پشتِ سرِ هم');
  shopC = await newShop('دکانِ استخر');
  shopD = await newShop('دکانِ دوم');
});
test.after(async () => { await h.stop(); });

/* ------------------------------------------------------------------ درستی */

const N = 6;
const SHARED = ['p-sh-1', 'p-sh-2', 'p-sh-3'];

/** دستهٔ دستگاهِ [d]: درج، «آخرین نوشتن» روی ردیفِ مشترک، tx ِ درست، tx ِ شکسته، تکراریِ دستگاهِ دیگر، حذف */
function batchOf(d, seed) {
  const dev = `gate-dev-${d}`;
  const id = (k) => `01JGATE${String(d).padStart(2, '0')}${String(k).padStart(17, '0')}`;
  let k = 0;
  const ops = [];
  //  ردیفِ مشترک: هر دستگاه همه را درج/اصلاح می‌کند؛ HLC ِ دستگاه‌ها در هم ⇒ برنده و «تعارض» به ترتیب بسته است
  for (const p of SHARED) ops.push({ op_id: id(++k), hlc: seed.h[d][p], table: 'products', row_id: p, kind: d === 0 ? 'insert' : 'update', fields: { name: `${p} از ${d}`, salePriceMinor: 100 * (d + 1) } });
  for (let j = 0; j < 30; j++) ops.push({ op_id: id(++k), hlc: H(dev), table: 'products', row_id: `p-${d}-${j}`, kind: 'insert', fields: { name: `کالای ${d}-${j}`, salePriceMinor: 1000 + j } });
  //  tx ِ درست: همه یا هیچ
  const t1 = `tx-ok-${d}`;
  ops.push({ op_id: id(++k), tx: t1, hlc: H(dev), table: 'sales', row_id: `s-${d}`, kind: 'insert', fields: { totalMinor: 2000, status: 'completed' } });
  ops.push({ op_id: id(++k), tx: t1, hlc: H(dev), table: 'stockMovements', row_id: `m-${d}`, kind: 'insert', fields: { productId: `p-${d}-0`, qtyMilli: -2000, type: 'sale' } });
  //  tx ِ شکسته (فیلدِ قدیمی) ⇒ هر دو رد
  const t2 = `tx-bad-${d}`;
  ops.push({ op_id: id(++k), tx: t2, hlc: H(dev), table: 'sales', row_id: `s-bad-${d}`, kind: 'insert', fields: { totalMinor: 10 } });
  ops.push({ op_id: id(++k), tx: t2, hlc: H(dev), table: 'stockMovements', row_id: `m-bad-${d}`, kind: 'insert', fields: { productId: 'x', qty: -2 } });
  //  op ِ خودش و همان op ِ دستگاهِ قبلی (یکی تکراری می‌شود، کدام به ترتیب بسته است)
  ops.push({ ...seed.first[d] });
  if (d > 0) ops.push({ ...seed.first[d - 1] });
  //  حذف و اصلاحِ بعدیِ یک ردیف (تعارضِ «پس از حذف» برای دستگاهِ دیر)
  ops.push({ op_id: id(++k), hlc: seed.h[d].del, table: 'products', row_id: 'p-del', kind: d % 2 ? 'delete' : (d === 0 ? 'insert' : 'update'), ...(d % 2 ? {} : { fields: { name: `حذفی ${d}` } }) });
  //  دلتا روی یک فروشِ مشترک
  ops.push({ op_id: id(++k), hlc: H(dev), table: 'sales', row_id: 's-shared', kind: d === 0 ? 'insert' : 'delta', fields: d === 0 ? { totalMinor: 9000, paidAmountMinor: 0, status: 'completed' } : { paidAmountMinor: 100 } });
  return { dev, ops };
}

function seedOf() {
  const hl = [];
  for (let d = 0; d < N; d++) hl.push({});
  //  HLC ِ ردیف‌های مشترک در هم: دستگاهِ زوج جلوتر از فرد، با فاصله
  for (const p of [...SHARED, 'del']) {
    const order = [...Array(N).keys()].sort((a, b) => ((a * 7 + p.length) % N) - ((b * 7 + p.length) % N));
    for (const d of order) hl[d][p] = H(`gate-dev-${d}`);
  }
  const first = [];
  for (let d = 0; d < N; d++) first.push({ op_id: `01JGATEFIRST${String(d).padStart(14, '0')}`, hlc: H(`gate-dev-${d}`), table: 'products', row_id: `p-first-${d}`, kind: 'insert', fields: { name: `نخست ${d}` } });
  return { h: hl, first };
}

/** شاگرد: فروشِ با تخفیف بی اجازه ⇒ ردِ درونِ تراکنش ِ کلِ tx */
function staffBatch() {
  const t = 'tx-staff';
  return { dev: 'gate-dev-staff', staff: true, ops: [
    { op_id: '01JGATESTAFF00000000000001', tx: t, hlc: H('st'), table: 'stockMovements', row_id: 'm-st', kind: 'insert', fields: { productId: 'p-st', qtyMilli: -1000 } },
    { op_id: '01JGATESTAFF00000000000002', tx: t, hlc: H('st'), table: 'sales', row_id: 's-st', kind: 'insert', fields: { totalMinor: 1000, discountMinor: 100 } },
    { op_id: '01JGATESTAFF00000000000003', hlc: H('st'), table: 'products', row_id: 'p-st-ok', kind: 'insert', fields: { name: 'شاگرد' } },
  ] };
}

/** حالِ دکان با شمارهٔ دفتر به‌جای server_seq ِ سراسری (دو دکان شمارهٔ یکسان ندارند) */
async function stateOf(shop) {
  const shopId = shop.id;
  const ops = await db.many(
    `SELECT op_id, server_seq, device_id, origin, tx_id, hlc, table_name, row_id, kind, fields, effect, status, skewed
       FROM sync_v3_ops WHERE app='shop' AND account_id=$1 ORDER BY server_seq`, [shopId]);
  const rank = new Map(ops.map((o, i) => [Number(o.server_seq), i + 1]));
  const r = (s) => (s == null ? s : rank.get(Number(s)) ?? `?${s}`);
  const rows = (await db.many(
    `SELECT table_name, row_id, data, field_hlc, field_src, row_hlc, inserted, created_by, deleted_hlc,
            deleted_at IS NOT NULL AS deleted, match_key, updated_seq
       FROM sync_v3_rows WHERE app='shop' AND account_id=$1 ORDER BY table_name, row_id`, [shopId]))
    .map((x) => ({
      ...x, updated_seq: r(x.updated_seq),
      field_src: Object.fromEntries(Object.entries(x.field_src || {}).map(([k, v]) => [k, { ...v, s: r(v.s) }])),
    }));
  const conflicts = await db.many(
    `SELECT kind, table_name, row_id, field, winner_value, loser_value, winner_hlc, loser_hlc, winner_op_id, loser_op_id,
            winner_device, loser_device, detail, status, dedupe_key
       FROM sync_v3_conflicts WHERE app='shop' AND account_id=$1
      ORDER BY kind, table_name, row_id, field, loser_op_id, winner_op_id`, [shopId]);
  //  شناسهٔ دکان و کاربرانش در دو دکان فرق دارد
  const norm = (v) => JSON.parse(shop.ids.reduce((t, id, i) => t.split(id).join(`<id${i}>`), JSON.stringify(v)));
  return { rank, r, norm, ops: norm(ops.map(({ server_seq, ...o }) => o)), rows: norm(rows), conflicts: norm(conflicts) };
}

const shape = (results, st) => st.norm(results.map(({ server_seq, ...x }) => ({ ...x, ...(server_seq != null ? { seq: st.r(server_seq) } : {}) })));

test('N دستگاهِ یک دکان هم‌زمان ⇒ همان نتیجهٔ اجرای پشتِ سرِ هم (ردیف، ترتیب، تکراری، رد)', async () => {
  const seed = seedOf();
  const batches = [...Array(N).keys()].map((d) => batchOf(d, seed));
  batches.push(staffBatch());
  const tokenOf = (s, b) => (b.staff ? s.staff : s.owner);
  gate.stats({ reset: true });

  //  دو دور: دسته‌ها، سپس همان دسته‌ها دوباره (پاسخ گم شد) — همه هم‌زمان
  const rounds = [];
  for (let round = 0; round < 2; round++) {
    const res = await Promise.all(batches.map((b) => push(tokenOf(shopA, b), b.dev, b.ops)));
    for (const x of res) assert.equal(x.status, 200, JSON.stringify(x.body));
    rounds.push(res.map((x) => x.body.results));
  }
  const st = gate.stats();

  const a = await stateOf(shopA);
  //  opهای هر push در دفتر یک بازهٔ پیوسته‌اند (دستگاهِ هر دسته یکتاست): هیچ دو push ِ یک دکان در هم نرفتند
  const devs = a.ops.map((o) => o.device_id).filter((d) => d !== 'server');
  const devsOrder = [];
  devs.forEach((d, k) => { if (k === 0 || devs[k - 1] !== d) devsOrder.push(d); });
  assert.equal(new Set(devsOrder).size, devsOrder.length, `هر push یک بازه: ${devsOrder.join(' ')}`);
  assert.equal(devsOrder.length, batches.length);

  //  همان دسته‌ها به همان ترتیبِ اعمال، پشتِ سرِ هم، روی دکانِ دوم
  const order = devsOrder.map((d) => batches.findIndex((x) => x.dev === d));
  const seqRounds = [[], []];
  for (let round = 0; round < 2; round++) {
    for (const i of order) {
      const b = batches[i];
      const x = await push(tokenOf(shopB, b), b.dev, b.ops);
      assert.equal(x.status, 200, JSON.stringify(x.body));
      seqRounds[round][i] = x.body.results;
    }
  }
  const b = await stateOf(shopB);
  for (let round = 0; round < 2; round++) {
    for (let i = 0; i < batches.length; i++) {
      assert.deepEqual(shape(rounds[round][i], a), shape(seqRounds[round][i], b), `پاسخِ دستهٔ ${i} در دورِ ${round + 1}`);
    }
  }
  assert.deepEqual(a.ops, b.ops, 'دفتر: همان opها به همان ترتیب، همان اثر و وضع');
  assert.deepEqual(a.rows, b.rows, 'ردیف‌ها');
  assert.deepEqual(a.conflicts, b.conflicts, 'تعارض‌ها و وظیفه‌ها');

  //  آنچه باید در هر دو باشد: رد، تکراری، و tx ِ شکسته بی هیچ ردیف
  const all = rounds[0].flat();
  assert.ok(all.some((x) => x.status === 'duplicate'), 'تکراریِ میانِ دستگاه‌ها');
  assert.ok(all.some((x) => x.reason === 'legacy_field') && all.some((x) => x.reason === 'tx_rejected'), 'ردِ tx ِ شکسته');
  assert.ok(all.some((x) => x.reason === 'discount_not_allowed'), 'ردِ درونِ تراکنشِ شاگرد');
  assert.ok(rounds[1].flat().every((x) => x.status === 'duplicate' || x.status === 'rejected'), 'دورِ دوم: فقط تکراری و همان ردها');
  assert.equal(a.rows.filter((x) => /-bad-|^m-st$|^s-st$/.test(x.row_id)).length, 0);
  //  دسته‌ها واقعاً هم‌زمان رسیدند و در صفِ دروازه پشتِ هم ماندند (روی PGlite دروازه خاموش است)
  if (PG) assert.ok(st.waitingMax >= 1, `بیشینهٔ منتظرِ دروازه ${st.waitingMax}`);
});

/* -------------------------------------------------- اتصالِ بیکار (فقط PG) */

/** قفلِ حسابِ [shopId] از بیرون (مثلِ نمونهٔ دیگرِ سرور) تا `release` */
async function holdDbLock(shopId) {
  const p = db.extraPool(1);
  const c = await p.connect();
  await c.query('BEGIN');
  await c.query('SELECT pg_advisory_xact_lock(hashtext($1))', [`sync-v3:shop:shop:${shopId}`]);
  return async () => { try { await c.query('COMMIT'); } finally { c.release(); await p.end(); } };
}

async function lockWaiters() {
  return (await db.one(
    `SELECT count(*) FILTER (WHERE wait_event_type = 'Lock')::int AS n,
            count(*) FILTER (WHERE state LIKE 'idle in transaction%')::int AS idle_tx
       FROM pg_stat_activity WHERE datname = current_database() AND pid <> pg_backend_pid()`));
}

async function until(fn, what, ms = 5000) {
  const end = Date.now() + ms;
  for (;;) {
    const v = await fn();
    if (v) return v;
    if (Date.now() > end) throw new Error(`نرسید: ${what}`);
    await sleep(20);
  }
}

const inUse = () => { const p = db.getPool(); return p.totalCount - p.idleCount; };

async function waitingPushes(shop, n, tag) {
  const pending = [];
  for (let i = 0; i < n; i++) {
    pending.push(push(shop.owner, `idle-${tag}-${i}`, [{ op_id: `01JIDLE${tag}${String(i).padStart(18, '0')}`, hlc: H(`idle-${i}`),
      table: 'products', row_id: `p-idle-${tag}-${i}`, kind: 'insert', fields: { name: `منتظر ${i}` } }]));
  }
  return pending;
}

test('push های منتظرِ همان دکان اتصالِ استخر نگه نمی‌دارند (پیش از دروازه: هر کدام یکی)', { skip: NO_PG }, async () => {
  const before = { ...config.syncV3.gate };
  try {
    for (const on of [false, true]) {
      config.syncV3.gate.enabled = on;
      const tag = on ? 'G' : 'X';
      const release = await holdDbLock(shopC.id);
      let pending;
      let seen;
      try {
        const base = inUse();
        pending = await waitingPushes(shopC, 5, tag);
        if (on) {
          //  یکی جلوی صف پشتِ قفلِ پایگاه (همان رفتارِ میانِ دو نمونه)، چهار تا در صفِ فرآیند، بی اتصال
          await until(async () => gate.stats().waiting === 4 && (await lockWaiters()).n === 1, 'یک منتظرِ قفل و چهار در صف');
        } else {
          await until(async () => (await lockWaiters()).n === 5, 'پنج اتصال پشتِ قفل');
        }
        await sleep(100);
        seen = { held: inUse() - base, lock: (await lockWaiters()).n, gate: gate.stats().waiting, pool: db.getPool().waitingCount };
        //  خواندنِ دیگر (ویترین) همچنان اتصال می‌گیرد، بی صف
        const t0 = Date.now();
        assert.equal((await db.one('SELECT 1 AS ok')).ok, 1);
        assert.ok(Date.now() - t0 < 1000);
      } finally {
        await release();
      }
      const res = await Promise.all(pending);
      for (const x of res) {
        assert.equal(x.status, 200, JSON.stringify(x.body));
        assert.equal(x.body.results[0].status, 'applied');
      }
      if (on) {
        assert.equal(seen.lock, 1, `دروازه: فقط یک اتصال پشتِ قفل ${JSON.stringify(seen)}`);
        assert.equal(seen.held, 1, `دروازه: push های منتظر اتصال نگرفتند ${JSON.stringify(seen)}`);
        assert.equal(seen.gate, 4);
      } else {
        assert.equal(seen.lock, 5, `بی دروازه (رفتارِ پیشین): ${JSON.stringify(seen)}`);
        assert.equal(seen.held, 5);
      }
      assert.equal(seen.pool, 0, 'هیچ کس در صفِ استخر نماند');
    }
  } finally {
    Object.assign(config.syncV3.gate, before);
  }
});

/* -------------------------------------------------------- پس‌زدن (فقط PG) */

function assertBusy(r, what) {
  assert.equal(r.status, 503, `${what}: ${JSON.stringify(r.body)}`);
  assert.equal(r.body.error.code, 'db_busy');
  assert.match(r.body.error.message, /شلوغ/);
  assert.equal(r.headers.get('retry-after'), '2');
}

test('دروازه: انتظارِ بلند، صفِ پر و سقفِ push ⇒ ۵۰۳ ِ db_busy با Retry-After؛ هیچ چیز اعمال نشد', { skip: NO_PG }, async () => {
  const before = { ...config.syncV3.gate };
  const statuses = [];
  try {
    Object.assign(config.syncV3.gate, { enabled: true, waitMs: 400, queueMax: 2, slots: 1 });
    const release = await holdDbLock(shopC.id);
    let first;
    let second;
    try {
      [first] = await waitingPushes(shopC, 1, 'B1');
      await until(async () => (await lockWaiters()).n === 1, 'push ِ نخست پشتِ قفلِ پایگاه');
      //  ۱. انتظار در صفِ همان دکان بیش از waitMs
      const t0 = Date.now();
      [second] = await waitingPushes(shopC, 1, 'B2');
      const late = await second;
      statuses.push(late.status);
      assertBusy(late, 'انتظارِ بلند');
      assert.ok(Date.now() - t0 < 3000, 'زود برگشت');
      assert.equal(await db.one(`SELECT 1 FROM sync_v3_ops WHERE account_id=$1 AND row_id='p-idle-B2-0'`, [shopC.id]), null);
      //  ۲. سقفِ push: دکانِ دیگر هم جا نمی‌گیرد (تنها جا در دستِ push ِ نخست)
      const other = await push(shopD.owner, 'slot-dev', [{ op_id: '01JSLOT0000000000000000001', hlc: H('slot'), table: 'products', row_id: 'p-slot', kind: 'insert', fields: { name: 'جا' } }]);
      statuses.push(other.status);
      assertBusy(other, 'سقفِ push');
      //  ۳. صفِ پر: دو منتظر (waitMs بلند) ⇒ سومی بی‌درنگ
      config.syncV3.gate.waitMs = 10_000;
      const queued = await waitingPushes(shopC, 2, 'B3');
      await until(() => gate.stats().waiting === 2, 'دو منتظر در صف');
      const t1 = Date.now();
      const [full] = await waitingPushes(shopC, 1, 'B4');
      const r = await full;
      statuses.push(r.status);
      assertBusy(r, 'صفِ پر');
      assert.ok(Date.now() - t1 < 1000, 'صفِ پر بی انتظار');
      second = queued;
    } finally {
      await release();
    }
    const done = await Promise.all([first, ...second]);
    for (const x of done) { statuses.push(x.status); assert.equal(x.status, 200, JSON.stringify(x.body)); }
    //  همان دستهٔ پس‌زده دوباره ⇒ اعمال
    const [again] = await waitingPushes(shopC, 1, 'B2');
    const ok = await again;
    statuses.push(ok.status);
    assert.equal(ok.status, 200);
    assert.equal(ok.body.results[0].status, 'applied');
  } finally {
    Object.assign(config.syncV3.gate, before);
  }
  assert.ok(!statuses.includes(500), `هیچ ۵۰۰: ${statuses}`);
  assert.equal(gate.stats().waiting, 0);
  assert.equal(gate.stats().inflight, 0);
});

/*
 *  SYNC_V3_GATE_POOL_DEADLINE (پیش‌فرض خاموش؛ docs/PERF-LOAD-fa.md ۱۴۰۵/۰۸/۱۹ (سوم)): push ِ پذیرفته در دروازه تا اکنون برای
 *  اتصالِ تراکنش جدا تا `PG_CONNECT_TIMEOUT_MS` (۱۰ ثانیه) در صفِ استخر می‌ماند ⇒ انتظارِ پیش از کار تا ۸ + ۱۰ ثانیه، بیش از
 *  مهلتِ ۱۵ثانیه‌ایِ برنامهٔ وب. روشن ⇒ همان سقفِ waitMs ِ دروازه گرفتنِ اتصال را هم می‌پوشاند: ۵۰۳ ِ `db_busy` پیش از هر کار.
 */
test('SYNC_V3_GATE_POOL_DEADLINE: انتظارِ اتصال پس از دروازه هم زیرِ همان سقف ⇒ ۵۰۳ ِ db_busy سرِ وقت؛ هیچ چیز اعمال نشد', { skip: NO_PG }, async () => {
  const before = { ...config.syncV3.gate };
  const pool = db.getPool();
  const held = [];
  let extra = null;
  let first;
  let late;
  let waited = 0;
  try {
    Object.assign(config.syncV3.gate, { enabled: true, waitMs: 1500, queueMax: 0, slots: 0, poolDeadline: true });
    const release = await holdDbLock(shopC.id);
    let t0 = 0;
    let second;
    try {
      [first] = await waitingPushes(shopC, 1, 'D1');
      await until(async () => (await lockWaiters()).n === 1, 'push ِ نخست پشتِ قفلِ پایگاه');
      t0 = Date.now();
      [second] = await waitingPushes(shopC, 1, 'D2');
      await until(() => gate.stats().waiting === 1, 'push ِ دوم در صفِ همان دکان (کارهای پیش از دروازه‌اش گذشت)');
      //  همهٔ اتصال‌های بیکار، و یک منتظرِ جلوی صف که اتصالِ push ِ نخست را پس از پایانش می‌گیرد
      while (pool.idleCount > 0 || pool.totalCount < config.db.poolMax) held.push(await pool.connect());
      extra = pool.connect();
      await until(() => pool.waitingCount === 1, 'یک منتظر جلوی صفِ استخر');
    } finally {
      await release();
    }
    late = await second;
    waited = Date.now() - t0;
  } finally {
    Object.assign(config.syncV3.gate, before);
    for (const c of held) c.release();
    if (extra) (await extra).release();
  }
  assertBusy(late, 'اتصال تا سقفِ دروازه نرسید');
  assert.ok(waited < 3000, `۵۰۳ سرِ سقفِ دروازه (${waited}ms؛ نه پس از PG_CONNECT_TIMEOUT_MS = ${config.db.connectTimeoutMs}ms)`);
  assert.equal(await db.one(`SELECT 1 FROM sync_v3_ops WHERE account_id=$1 AND row_id='p-idle-D2-0'`, [shopC.id]), null, 'هیچ چیز اعمال نشد');
  const ok = await first;
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  //  همان دسته دوباره ⇒ اعمال
  const [again] = await waitingPushes(shopC, 1, 'D2');
  const r = await again;
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.results[0].status, 'applied');
  assert.equal(gate.stats().waiting, 0);
  assert.equal(gate.stats().inflight, 0);
});

test('PG_POOL_MAX_WAITING: صفِ استخرِ پر ⇒ ۵۰۳ ِ db_busy بی‌درنگ (نه پس از PG_CONNECT_TIMEOUT_MS)', { skip: NO_PG }, async () => {
  const pool = db.getPool();
  const held = [];
  const before = config.db.maxWaiting;
  let queued;
  try {
    config.db.maxWaiting = 1;
    while (pool.totalCount < config.db.poolMax || pool.idleCount > 0) held.push(await pool.connect());
    queued = pool.query('SELECT 1');           //  تنها جای صف
    await until(() => pool.waitingCount === 1, 'یک منتظر در صفِ استخر');
    const t0 = Date.now();
    const r = await h.get('/api/store/hich-dokani-nist');
    assertBusy(r, 'صفِ استخرِ پر');
    assert.ok(Date.now() - t0 < 2000, `بی‌درنگ (${Date.now() - t0}ms؛ مهلتِ اتصال ${config.db.connectTimeoutMs}ms)`);
  } finally {
    config.db.maxWaiting = before;
    for (const c of held) c.release();
  }
  await queued;
  const after = await h.get('/api/store/hich-dokani-nist');
  assert.equal(after.status, 404, 'پس از آزاد شدن همان پاسخِ عادی');
});
