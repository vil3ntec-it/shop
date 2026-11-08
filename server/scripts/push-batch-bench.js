#!/usr/bin/env node
'use strict';
/**
 * سنجشِ push ِ بزرگِ v3 در دو راه — تک‌تک (پیش‌فرض) و دسته‌ای (`SYNC_V3_BATCH`، `lib/sync-v3-batch.js`)؛ docs/PERF-LOAD-fa.md «scale-4».
 *
 *   TEST_DATABASE_URL=postgres://…/shop_test node scripts/push-batch-bench.js [دور=8] [فروشِ هر push=120]
 *   TEST_DATABASE_URL=pglite:memory node scripts/push-batch-bench.js
 *
 * دو دکانِ هم‌شکل، هر push همان شکلِ `sync3.pushLarge` ِ `load-test.mjs` (هر فروش: سند، قلم، حرکتِ منفیِ کالا، تراکنش در یک tx)،
 * پشتِ سرِ هم و به نوبت (دورِ زوج اول تک‌تک، فرد اول دسته‌ای) تا بارِ ماشین هر دو را یکسان بزند. خروجی: میانه/کمینه/بیشینهٔ
 * زمانِ هر push و op در ثانیه. ⛔ پایگاه پاک می‌شود (`test/helpers`): فقط pglite یا نامی با test/bench.
 */
process.env.SYNC_V3_ENABLED = '1';
const url = process.env.TEST_DATABASE_URL || '';
const dbName = () => { try { return decodeURIComponent(new URL(url).pathname.slice(1)); } catch { return ''; } };
if (!url.startsWith('pglite:') && !/test|bench/.test(dbName())) {
  console.error('⛔ این ابزار پایگاه را پاک می‌کند؛ TEST_DATABASE_URL باید pglite:… یا نامی با test/bench باشد');
  process.exit(2);
}
const os = require('os');
const { performance } = require('perf_hooks');
const h = require('../test/helpers');
const config = require('../src/config');
const hlc = require('../src/lib/hlc');
const { resetAll } = require('../src/middleware/ratelimit');

const ROUNDS = Math.max(1, Number(process.argv[2]) || 8);
const SALES = Math.max(1, Math.min(125, Number(process.argv[3]) || 120));
let n = 0;
let wall = Date.now() - 3_600_000;
const H = () => hlc.format({ wall: ++wall, counter: 0, node: 'bench' });
const id = () => `01JBENCH${String(++n).padStart(18, '0')}`;

function opsOf(tag) {
  const ops = [];
  for (let i = 0; i < SALES; i++) {
    const sid = `${tag}-s${++n}`;
    const tx = `tx-${sid}`;
    const pid = `p${i % 7}`;
    ops.push({ op_id: id(), tx, hlc: H(), table: 'sales', row_id: sid, kind: 'insert', fields: { totalMinor: 100, finalTotalMinor: 100, paidAmountMinor: 100, status: 'completed' } });
    ops.push({ op_id: id(), tx, hlc: H(), table: 'saleItems', row_id: `${sid}-i`, kind: 'insert', fields: { saleId: sid, productId: pid, quantityMilli: 1000, totalPriceMinor: 100 } });
    ops.push({ op_id: id(), tx, hlc: H(), table: 'stockMovements', row_id: `${sid}-m`, kind: 'insert', fields: { productId: pid, qtyMilli: -1000, type: 'sale', refId: sid } });
    ops.push({ op_id: id(), tx, hlc: H(), table: 'transactions', row_id: `${sid}-t`, kind: 'insert', fields: { kind: 'sale_cash', type: 'in', amountMinor: 100, refId: sid } });
  }
  return ops;
}

const stat = (a) => {
  const s = [...a].sort((x, y) => x - y);
  return { median: Math.round(s[Math.floor(s.length / 2)]), min: Math.round(s[0]), max: Math.round(s[s.length - 1]) };
};

(async () => {
  await h.start();
  await require('../src/lib/sync-v1').syncSchemaTable();
  const who = {};
  for (const mode of ['direct', 'batch']) {
    const o = await h.newUser(`سنجشِ ${mode}`, 'shop');
    await h.post('/api/shop', { name: `دکانِ سنجشِ ${mode}` }, { token: o.accessToken });
    who[mode] = await h.signIn(o, 'shop');
  }
  const ms = { direct: [], batch: [] };
  for (let r = 0; r < ROUNDS; r++) {
    for (const mode of r % 2 ? ['batch', 'direct'] : ['direct', 'batch']) {
      const ops = opsOf(mode);
      config.syncV3.batch = mode === 'batch';
      resetAll();
      const t0 = performance.now();
      const res = await h.api('POST', '/api/sync/v3/push', { token: who[mode].accessToken, body: { device_id: 'bench', protocol: 3, ops } });
      ms[mode].push(performance.now() - t0);
      if (res.status !== 200 || !res.body.results.every((x) => x.status === 'applied')) {
        throw new Error(`${mode}: ${res.status} ${JSON.stringify(res.body).slice(0, 300)}`);
      }
    }
  }
  const out = { opsPerPush: SALES * 4, rounds: ROUNDS, db: url.startsWith('pglite') ? 'pglite' : 'postgres', loadAvg: os.loadavg().map((x) => +x.toFixed(1)) };
  for (const m of ['direct', 'batch']) { const s = stat(ms[m]); out[m] = { ...s, opsPerSec: Math.round(out.opsPerPush / (s.median / 1000)) }; }
  console.log(JSON.stringify(out));
  await h.stop();
  process.exit(0);
})().catch((e) => { console.error(e); process.exit(1); });
