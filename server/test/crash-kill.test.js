'use strict';
/**
 * DATA-03 (§۲۹.۸): «crash میانِ نوشتنِ چندجدولی سندِ نیمه‌کاره نسازد» — با **کشتنِ واقعیِ فرآیندِ سرور**.
 *
 * `tx-atomicity.test.js` شکست را با trigger شبیه‌سازی می‌کند. این‌جا خودِ فرآیندِ سرور میانِ کار
 * با SIGKILL می‌میرد: نه catch، نه ROLLBACK، نه بستنِ اتصال از سوی Node — همان چیزی که با رفتنِ برق
 * یا کشته شدنِ سرویس رخ می‌دهد. سپس سرورِ تازه روی **همان** دیتابیس بالا می‌آید (PostgreSQL، یا
 * پوشهٔ PGlite روی دیسک که باید پس از مرگِ ناگهانی خودش را بازیابی کند) و می‌سنجیم:
 *   • هیچ ردی از نوشتن‌های پیش از مرگ نمانده (سندِ نیمه‌کاره نیست)؛
 *   • تکرارِ همان درخواست (همان eventId / کلیدِ یکتایی / op_id) دقیقاً یک بار اثر می‌کند و
 *     تکرارِ دوباره «تکراری» است — اثرِ مالیِ دوم ندارد.
 *
 * فرآیندِ کشته‌شده تا پیش از **آخرین** نوشتنِ هر کار جلو می‌رود (`fixtures/crash-child.js`):
 *   ۱) تحویلِ پیک (`POST /api/courier/events`، «تحویل» با نقد): online_orders + online_order_events
 *      (گذار و COD) + delivery_assignments + delivery_locations ⇐ مرگ پیش از INSERT ِ delivery_events.
 *   ۲) تسویهٔ پیک: courier_settlements + delivery_assignments + online_orders (COD) ⇐ مرگ پیش از
 *      INSERT ِ online_order_events ِ «به دکان رسید».
 *   ۳) Sync v3 push با tx ِ سه‌opی (فروش + قلم + پرداخت): sync_v3_ops + sync_v3_rows ⇐ مرگ پیش از
 *      نوشتنِ sync_v3_devices.
 *
 * PGlite ِ درون‌حافظه میانِ دو فرآیند مشترک نمی‌شود؛ پس این پرونده با PGlite پوشهٔ موقتِ خودش را
 * روی دیسک می‌گیرد. در تمامِ مدتِ کار ِ فرآیندهای فرزند، این فرآیند به دیتابیس دست نمی‌زند.
 */
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawn } = require('node:child_process');

let liteDir = '';
if (/^pglite:/i.test(process.env.TEST_DATABASE_URL || '')) {
  liteDir = fs.mkdtempSync(path.join(os.tmpdir(), 'shop-crash-kill-'));
  process.env.TEST_DATABASE_URL = `pglite:${liteDir}`;
  process.on('exit', () => fs.rmSync(liteDir, { recursive: true, force: true }));
}
process.env.SYNC_V3_ENABLED = '1';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { one } = require('../src/db');
const hlc = require('../src/lib/hlc');

const CHILD = path.join(__dirname, 'fixtures', 'crash-child.js');
const LIMIT_MS = 90_000;

/** فرزند را اجرا می‌کند و هر خطِ stdout را به `onLine` می‌دهد؛ خطای `onLine` یا دیر شدن ⇒ رد. */
function run(mode, env, onLine) {
  return new Promise((resolve, reject) => {
    const child = spawn(process.execPath, [CHILD, mode], {
      cwd: path.join(__dirname, '..'),
      env: { ...process.env, ...env },
      stdio: ['ignore', 'pipe', 'pipe'],
    });
    let err = '';
    let buf = '';
    let settled = false;
    const finish = (fn, v) => { if (!settled) { settled = true; clearTimeout(timer); fn(v); } };
    const timer = setTimeout(() => {
      child.kill('SIGKILL');
      finish(reject, new Error(`فرزندِ «${mode}» در ${LIMIT_MS}ms تمام نشد\n${err}`));
    }, LIMIT_MS);
    child.stderr.on('data', (d) => { err += d; });
    child.stdout.on('data', (d) => {
      buf += d;
      let i;
      while ((i = buf.indexOf('\n')) >= 0) {
        const line = buf.slice(0, i);
        buf = buf.slice(i + 1);
        Promise.resolve(onLine(line, child)).catch((e) => finish(reject, e));
      }
    });
    child.on('exit', (code, signal) => finish(resolve, { code, signal, err }));
  });
}

/** سرورِ فرزند را بالا می‌آورد، درخواست را به آن می‌فرستد و درست در CRASH_POINT با SIGKILL می‌کشد. */
async function killMidWrite(crashAt, method, url, { token, body, headers = {} }) {
  let reached = false;
  let answered = '';
  const out = await run('serve', { CRASH_AT: crashAt }, (line, child) => {
    if (line.startsWith('LISTEN ')) {
      const port = Number(line.slice(7));
      //  پاسخی نباید بیاید: فرآیند پیش از آن می‌میرد. اگر آمد، کار بی رسیدن به نقطهٔ مرگ تمام شد.
      fetch(`http://127.0.0.1:${port}${url}`, {
        method,
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}`, ...headers },
        body: JSON.stringify(body),
      }).then(async (r) => {
        if (reached) return;
        answered = `${r.status} ${await r.text()}`;
        child.kill('SIGKILL');
      }).catch(() => {});
    } else if (line === 'CRASH_POINT') {
      reached = true;
      child.kill('SIGKILL');
    }
  });
  assert.equal(reached, true, `فرآیند به نقطهٔ مرگ (${crashAt}) نرسید؛ پاسخ: ${answered || '—'}`);
  assert.equal(out.signal, 'SIGKILL', 'فرآیند باید کشته شده باشد، نه بسته');
}

let S = null;
const killed = {};   //  صحنه ⇒ خطای کشتن (اگر به نقطهٔ مرگ نرسید)
const DEVICE_TIME = Date.now() - 60_000;   //  همان رویداد در مرگ و در تکرار
const COURIER_EVENT = () => ({
  deviceId: 'crash-courier',
  events: [{ eventId: `crash-${S.deliver.assignmentId}-delivered`, assignmentId: S.deliver.assignmentId,
    type: 'delivered', cashCollectedMinor: S.deliver.totalMinor, deviceTime: DEVICE_TIME }],
});
const SETTLE_KEY = 'crash-settle-1';
const SETTLE_BODY = () => ({
  assignmentIds: [S.settle.assignmentId], handedOverMinor: S.settle.totalMinor, receivedMinor: S.settle.totalMinor,
});
const V3_TX = 'crash-tx-1';
const WALL = Date.now() - 3_600_000;
const HLC = (i) => hlc.format({ wall: WALL + i, counter: 0, node: 'crash-dev' });
const V3_OPS = [
  { op_id: '01JV3K00000000000000000001', tx: V3_TX, hlc: HLC(1), table: 'sales', row_id: 's-crash',
    kind: 'insert', fields: { totalMinor: 2000, status: 'completed' } },
  { op_id: '01JV3K00000000000000000002', tx: V3_TX, hlc: HLC(2), table: 'saleItems', row_id: 'si-crash',
    kind: 'insert', fields: { saleId: 's-crash', productId: 'p-crash', quantityMilli: 2000 } },
  { op_id: '01JV3K00000000000000000003', tx: V3_TX, hlc: HLC(3), table: 'transactions', row_id: 't-crash',
    kind: 'insert', fields: { amountMinor: 2000 } },
];
const V3_BODY = { device_id: 'crash-dev', protocol: 3, ops: V3_OPS };

const n = async (sql, p) => Number((await one(sql, p)).n);
const deliveryState = async () => ({
  assignment: (await one('SELECT status, cash_collected_minor FROM delivery_assignments WHERE id=$1', [S.deliver.assignmentId])),
  order: (await one('SELECT status, cod_status FROM online_orders WHERE id=$1', [S.deliver.orderId])),
  deliveryEvents: await n('SELECT COUNT(*)::int n FROM delivery_events WHERE assignment_id=$1', [S.deliver.assignmentId]),
  orderEvents: await n('SELECT COUNT(*)::int n FROM online_order_events WHERE order_id=$1', [S.deliver.orderId]),
});
const settleState = async () => ({
  settlements: await n('SELECT COUNT(*)::int n FROM courier_settlements WHERE shop_id=$1', [S.shopId]),
  settlementId: (await one('SELECT settlement_id FROM delivery_assignments WHERE id=$1', [S.settle.assignmentId])).settlement_id,
  cod: (await one('SELECT cod_status FROM online_orders WHERE id=$1', [S.settle.orderId])).cod_status,
  orderEvents: await n('SELECT COUNT(*)::int n FROM online_order_events WHERE order_id=$1', [S.settle.orderId]),
});
const v3State = async () => ({
  ops: await n(`SELECT COUNT(*)::int n FROM sync_v3_ops WHERE account_id=$1 AND op_id = ANY($2::text[])`,
    [S.v3ShopId, V3_OPS.map((o) => o.op_id)]),
  rows: await n(`SELECT COUNT(*)::int n FROM sync_v3_rows WHERE account_id=$1 AND row_id = ANY($2::text[])`,
    [S.v3ShopId, V3_OPS.map((o) => o.row_id)]),
  device: await n(`SELECT COUNT(*)::int n FROM sync_v3_devices WHERE account_id=$1 AND device_id='crash-dev'`, [S.v3ShopId]),
});

test.before(async () => {
  //  ۰) چیدنِ داده در فرآیندِ جدا؛ این فرآیند هنوز دیتابیس را باز نکرده است
  const setup = await run('setup', {}, (line) => {
    if (line.startsWith('SETUP ')) S = JSON.parse(line.slice(6));
  });
  assert.equal(setup.code, 0, setup.err);
  assert.ok(S, 'دادهٔ صحنه‌ها نیامد');

  //  ۱ تا ۳) سه فرآیند، هر کدام میانِ نوشتن کشته می‌شود؛ شکستِ هر صحنه فقط آزمونِ خودش را سرخ می‌کند
  const scene = async (name, ...args) => {
    try { await killMidWrite(...args); } catch (err) { killed[name] = err; }
  };
  await scene('deliver', '^\\s*INSERT INTO delivery_events', 'POST', '/api/courier/events',
    { token: S.courier, body: COURIER_EVENT() });
  await scene('settle', '^\\s*INSERT INTO online_order_events', 'POST',
    `/api/shop/delivery/couriers/${S.courierId}/settlements`,
    { token: S.owner, body: SETTLE_BODY(), headers: { 'Idempotency-Key': SETTLE_KEY } });
  await scene('v3', '^\\s*INSERT INTO sync_v3_devices', 'POST', '/api/sync/v3/push', { token: S.v3Owner, body: V3_BODY });

  //  سرورِ تازه روی همان دیتابیس (بی پاک کردن) — PGlite این‌جا بازیابیِ پس از مرگ را انجام می‌دهد
  await h.start({ reset: false });
});
test.after(async () => { await h.stop(); });

test('DATA-03 کشتن ۱) پیک «تحویل» را می‌فرستد و سرور میانِ نوشتن می‌میرد ⇒ نه گذارِ سفارش، نه COD، نه رویداد؛ تکرار ⇒ یک بار', async () => {
  assert.ifError(killed.deliver);
  const after = await deliveryState();
  assert.equal(after.assignment.status, 'arrived');
  assert.equal(Number(after.assignment.cash_collected_minor || 0), 0, 'نقدِ گرفته‌شده ننشسته است');
  assert.deepEqual(after.order, { status: 'out_for_delivery', cod_status: 'to_collect' });

  const first = await h.post('/api/courier/events', COURIER_EVENT(), { token: S.courier });
  assert.equal(first.status, 200, JSON.stringify(first.body));
  assert.equal(first.body.results[0].status, 'applied', 'رویدادِ گم‌شده دوباره می‌نشیند');
  const again = await h.post('/api/courier/events', COURIER_EVENT(), { token: S.courier });
  assert.equal(again.body.results[0].status, 'duplicate');

  const done = await deliveryState();
  assert.equal(done.assignment.status, 'delivered');
  assert.equal(Number(done.assignment.cash_collected_minor), S.deliver.totalMinor);
  assert.deepEqual(done.order, { status: 'delivered', cod_status: 'collected_by_courier' });
  assert.equal(done.deliveryEvents, after.deliveryEvents + 1, 'یک رویدادِ تحویل، نه دو');
  assert.equal(done.orderEvents, after.orderEvents + 2, 'یک گذار و یک COD، نه دو بار');
});

test('DATA-03 کشتن ۲) تسویهٔ پیک میانِ نوشتن می‌میرد ⇒ نه تسویه، نه نشانِ مأموریت، نه COD ِ «به دکان رسید»؛ همان کلید ⇒ یک تسویه', async () => {
  assert.ifError(killed.settle);
  const after = await settleState();
  assert.deepEqual({ settlements: after.settlements, settlementId: after.settlementId, cod: after.cod },
    { settlements: 0, settlementId: '', cod: 'collected_by_courier' });

  const url = `/api/shop/delivery/couriers/${S.courierId}/settlements`;
  const first = await h.post(url, SETTLE_BODY(), { token: S.owner, headers: { 'Idempotency-Key': SETTLE_KEY } });
  assert.equal(first.status, 201, JSON.stringify(first.body));
  const again = await h.post(url, SETTLE_BODY(), { token: S.owner, headers: { 'Idempotency-Key': SETTLE_KEY } });
  assert.equal(again.status, 200, JSON.stringify(again.body));
  assert.equal(again.body.settlement.id, first.body.settlement.id);

  const done = await settleState();
  assert.equal(done.settlements, 1);
  assert.equal(done.settlementId, first.body.settlement.id);
  assert.equal(done.cod, 'settled_to_shop');
  assert.equal(done.orderEvents, after.orderEvents + 1);
});

test('DATA-03 کشتن ۳) Sync v3 push با tx ِ سه‌opی میانِ نوشتن می‌میرد ⇒ نه op، نه ردیف، نه دستگاه؛ تکرار ⇒ applied و سپس duplicate', async () => {
  assert.ifError(killed.v3);
  assert.deepEqual(await v3State(), { ops: 0, rows: 0, device: 0 });

  const first = await h.api('POST', '/api/sync/v3/push', { token: S.v3Owner, body: V3_BODY });
  assert.equal(first.status, 200, JSON.stringify(first.body));
  assert.deepEqual(first.body.results.map((r) => r.status), ['applied', 'applied', 'applied']);
  const again = await h.api('POST', '/api/sync/v3/push', { token: S.v3Owner, body: V3_BODY });
  assert.deepEqual(again.body.results.map((r) => r.status), ['duplicate', 'duplicate', 'duplicate']);

  assert.deepEqual(await v3State(), { ops: 3, rows: 3, device: 1 });
});
