'use strict';
/**
 * SYN-05/06 — دستگاهِ v3 پس از push همان می‌شود که سرور دارد (`current` ِ پاسخِ push؛ سرور: `lib/sync-v3-apply.js`،
 * وب: `currentFixes` ِ `license/sync-v3-engine.js`، کاتلین: `V3Current`؛ بردارِ `core/test-vectors/v3-current-v1.json`).
 *
 *   • ساعتِ کج (+۲۴ ساعت): تغییرِ دستگاه نمی‌برد و به صندوق می‌رود — ولی خودِ دستگاه با قیمتِ خودش می‌فروخت تا
 *     دستگاهِ دیگری همان فیلد را عوض کند؛ حالا قیمتِ سرور روی همان دستگاه، بازنده برگرداندنی در صندوق.
 *   • حذفِ کهنه: دستگاهی که کالا را پاک کرد و دیگری پس از آن ویرایش کرد ⇒ سرور کالا را نگه می‌دارد؛ حذف‌کننده
 *     پیش‌تر با Pull ِ آن ویرایش یک ردیفِ نیمه (فقط فیلدِ ویرایش‌شده) می‌ساخت ⇒ حالا کالای کامل.
 *   • «تاریخِ تصحیح‌شده» (S28b): سرور `correctedAt` را می‌افزاید ولی Pull opِ خودِ دستگاه را برایش نمی‌آورد ⇒ فاکتور
 *     و حرکتِ همان دستگاه بی آن می‌ماند (نگهبانِ ساعتِ INV-10 روی همان دستگاه هم) ⇒ حالا از پاسخِ push.
 *   • پاسخِ گم‌شده ⇒ «تکراری» همان `current` را از حالِ امروزِ ردیف می‌گیرد؛ تغییرِ تازه‌ترِ هنوز در صف دست نمی‌خورد.
 */
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';
process.env.RATE_SYNC_MAX = process.env.RATE_SYNC_MAX || '100000';
process.env.SYNC_V3_ENABLED = '1';

const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const fs = require('node:fs');
const h = require('./helpers');
const { one } = require('../src/db');
const syncV1 = require('../src/lib/sync-v1');

const ROOT = path.join(__dirname, '..', '..');
const V3 = require(path.join(ROOT, 'license', 'sync-v3-engine.js'));
const core = require(path.join(ROOT, 'license', 'sync-core.js'));
const VEC = JSON.parse(fs.readFileSync(path.join(ROOT, 'core', 'test-vectors', 'v3-current-v1.json'), 'utf8'));
const DAY = 86_400_000;

let owner;
let shopId;

function ledgerOf(data = {}) {
  let state = core.cloneLedger(data);
  return { read: () => state, write: (d) => { state = core.cloneLedger(d); }, get: () => state };
}

/** انتقالِ واقعی؛ «پاسخ را گم کند» یا پیش از برگشتنِ پاسخ کاری بکند (کاربر وسطِ push ذخیره می‌کند) */
function transport(token) {
  const real = V3.FetchTransport(h.base(), token);
  const t = {
    offline: false, loseNext: 0, beforeReturn: null,
    async request(method, p, body) {
      if (t.offline) throw new Error('به سرور نرسیدیم');
      const r = await real.request(method, p, body);
      if (p.endsWith('/push') && t.beforeReturn) { const f = t.beforeReturn; t.beforeReturn = null; await f(); }
      if (t.loseNext > 0 && p.endsWith('/push')) { t.loseNext--; throw new Error('پاسخ گم شد'); }
      return r;
    },
  };
  return t;
}

/** دستگاهِ صاحب با موتورِ واقعیِ وب؛ `shift` ساعتِ خودِ دستگاه را جابه‌جا می‌کند */
function device(name, data) {
  const ledger = ledgerOf(data);
  const tr = transport(owner.accessToken);
  const d = { name, ledger, tr, shift: 0 };
  d.engine = new V3.Engine({ transport: tr, store: new V3.MemoryStore(), ledger, deviceId: name, random: () => 0.5, now: () => Date.now() + d.shift });
  d.engine.keepLocalOnly = true;
  d.edit = (fn) => { const x = ledger.get(); fn(x); ledger.write(x); };
  d.find = (table, id) => (ledger.get()[table] || []).find((r) => r.id === id);
  return d;
}
async function ready(d) {
  await d.engine.load();
  const r = await d.engine.sync();
  assert.equal(r.ok, true, JSON.stringify(r));
  return d;
}
const rowOf = (table, id) => one(
  `SELECT * FROM sync_v3_rows WHERE app='shop' AND account_id=$1 AND table_name=$2 AND row_id=$3`, [shopId, table, id]
);
const setPrice = (d, id, salePrice) => d.edit((x) => { x.products = x.products.map((p) => (p.id === id ? { ...p, salePrice } : p)); });
const push = (ops, deviceId = 'cur-raw') => h.api('POST', '/api/sync/v3/push', { token: owner.accessToken, body: { device_id: deviceId, ops } });
const stamp = (wall, node = 'cur-raw') => `${wall}:0:${node}`;

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
  owner = await h.newUser('صاحبِ دکانِ current ِ v3', 'shop');
  const shop = await h.api('POST', '/api/shop', { token: owner.accessToken, body: { name: 'دکانِ current ِ v3' } });
  shopId = shop.body?.shop?.id || shop.body?.id;
  owner = await h.signIn(owner, 'shop');
});
test.after(async () => { await h.stop(); });

test('بردارِ v3-current-v1.json = `currentFixes` ِ وب (همزادِ `V3Current.fixes` ِ کاتلین)', () => {
  assert.equal(typeof V3.currentFixes, 'function');
  assert.ok(VEC.cases.length >= 15);
  for (const c of VEC.cases) assert.deepEqual(V3.currentFixes(c.items), c.want, c.name);
});

test('سرور: درجِ سندِ با ساعتِ کج ⇒ `current.correctedAt` در پاسخ و در تکرار؛ سندِ ساعتِ درست و دلتا بی current', async () => {
  const t = Date.now();
  const y2k = Date.UTC(2000, 0, 1);
  const ops = [
    { op_id: 'cur-raw-s1', table: 'sales', row_id: 'cur-raw-s1', kind: 'insert', hlc: stamp(t), fields: { totalMinor: 1000, finalTotalMinor: 1000, createdAt: y2k } },
    { op_id: 'cur-raw-m1', table: 'stockMovements', row_id: 'cur-raw-m1', kind: 'insert', hlc: stamp(t), fields: { productId: 'p-raw', qtyMilli: -1000, createdAt: y2k } },
    { op_id: 'cur-raw-s2', table: 'sales', row_id: 'cur-raw-s2', kind: 'insert', hlc: stamp(t), fields: { totalMinor: 500, finalTotalMinor: 500, createdAt: t } },
    { op_id: 'cur-raw-d1', table: 'sales', row_id: 'cur-raw-s1', kind: 'delta', hlc: stamp(t + 1), fields: { paidAmountMinor: 1000 } },
  ];
  const r1 = await push(ops);
  assert.equal(r1.status, 200, JSON.stringify(r1.body));
  const res = r1.body.results;
  const at = (await rowOf('sales', 'cur-raw-s1')).data.correctedAt;
  assert.ok(Number.isSafeInteger(at) && Math.abs(at - t) < 60_000, `correctedAt ِ سرور ${at}`);
  assert.equal((await rowOf('sales', 'cur-raw-s1')).data.createdAt, y2k, 'createdAt ِ دستگاه دست نخورد');
  assert.deepEqual(res[0].current, { deleted: false, data: { correctedAt: at } }, JSON.stringify(res[0]));
  assert.equal(res[0].status, 'applied');
  assert.deepEqual(res[1].current, { deleted: false, data: { correctedAt: (await rowOf('stockMovements', 'cur-raw-m1')).data.correctedAt } });
  assert.equal(res[2].current, undefined, 'سندِ ساعتِ درست هیچ current ای ندارد');
  assert.equal(res[3].current, undefined, 'دلتا');

  //  پاسخ گم شد ⇒ همان دسته دوباره: «تکراری» با همان current
  const r2 = await push(ops);
  assert.deepEqual(r2.body.results.map((x) => x.status), ['duplicate', 'duplicate', 'duplicate', 'duplicate']);
  assert.deepEqual(r2.body.results[0].current, { deleted: false, data: { correctedAt: at } });
  assert.ok(r2.body.results[1].current);
  assert.equal(r2.body.results[2].current, undefined);
  assert.equal(r2.body.results[3].current, undefined);
});

test('سرور: تکرارِ op ِ ساعت‌کج و حذفِ کهنه همان `current` ِ بارِ نخست را می‌گیرد؛ op ِ همان‌نشسته بی current', async () => {
  const t = Date.now();
  const ins = await push([
    { op_id: 'cur-raw-p1', table: 'products', row_id: 'cur-raw-p1', kind: 'insert', hlc: stamp(t), fields: { name: 'نمک', salePriceMinor: 5000 } },
    { op_id: 'cur-raw-p2', table: 'products', row_id: 'cur-raw-p2', kind: 'insert', hlc: stamp(t), fields: { name: 'برنج', salePriceMinor: 9000, barcodes: ['6260000000048'] } },
  ]);
  assert.deepEqual(ins.body.results.map((x) => [x.status, x.current]), [['applied', undefined], ['applied', undefined]]);

  const skew = [{ op_id: 'cur-raw-u1', table: 'products', row_id: 'cur-raw-p1', kind: 'update', hlc: stamp(t + DAY), fields: { salePriceMinor: 13000 } }];
  const s1 = (await push(skew)).body.results[0];
  assert.equal(s1.status, 'conflict');
  assert.equal(s1.skewed, true);
  assert.deepEqual(s1.current, { deleted: false, data: { salePriceMinor: 5000 } });
  const s2 = (await push(skew)).body.results[0];
  assert.equal(s2.status, 'duplicate');
  assert.deepEqual(s2.current, { deleted: false, data: { salePriceMinor: 5000 } }, 'تکرار همان current را دارد');

  const plain = [{ op_id: 'cur-raw-u2', table: 'products', row_id: 'cur-raw-p1', kind: 'update', hlc: stamp(t + 10), fields: { name: 'نمکِ ید‌دار' } }];
  assert.equal((await push(plain)).body.results[0].current, undefined);
  const p2 = (await push(plain)).body.results[0];
  assert.deepEqual([p2.status, p2.current], ['duplicate', undefined], 'همان‌نشسته ⇒ بی current');

  //  حذفِ کهنه: ویرایشِ تازه‌ترِ دستگاهِ دیگر هست ⇒ ردیف می‌ماند و دادهٔ کاملش برمی‌گردد
  await push([{ op_id: 'cur-raw-u3', table: 'products', row_id: 'cur-raw-p2', kind: 'update', hlc: stamp(t + 1000, 'cur-raw-b'), fields: { salePriceMinor: 9500 } }], 'cur-raw-b');
  const del = [{ op_id: 'cur-raw-x1', table: 'products', row_id: 'cur-raw-p2', kind: 'delete', hlc: stamp(t + 500) }];
  const full = { deleted: false, data: { name: 'برنج', salePriceMinor: 9500, barcodes: ['6260000000048'] } };
  const d1 = (await push(del)).body.results[0];
  assert.deepEqual([d1.status, d1.current], ['conflict', full]);
  const d2 = (await push(del)).body.results[0];
  assert.deepEqual([d2.status, d2.current], ['duplicate', full], 'تکرارِ حذفِ کهنه همان دادهٔ کامل را دارد');
});

test('ساعتِ کج (+۲۴ ساعت): قیمتِ بازنده روی همان دستگاه به قیمتِ سرور برمی‌گردد؛ تغییرِ در صف دست نمی‌خورد؛ برگرداندن هر دو را می‌رساند', async () => {
  const P = { id: 'p-cur-skew', name: 'چای', salePrice: 50, purchasePrice: 40, stock: 0 };
  const D1 = await ready(device('cur-skew-1', { products: [P] }));
  const D2 = await ready(device('cur-skew-2', {}));
  assert.equal(D2.find('products', P.id).salePrice, 50);

  D1.shift = DAY;
  setPrice(D1, P.id, 130);
  assert.equal(await D1.engine.record(), 1);
  //  کاربر وسطِ همین push دوباره قیمت را عوض می‌کند: آن تغییر در صف است و نباید زیرِ قیمتِ سرور برود
  D1.tr.beforeReturn = async () => { setPrice(D1, P.id, 140); assert.equal(await D1.engine.record(), 1); };
  const r = await D1.engine.pushOnce();
  assert.equal(r.ok, true, JSON.stringify(r));
  assert.equal((await rowOf('products', P.id)).data.salePriceMinor, 5000, '۱۳۰ ِ ساعت‌جلو نبرد');
  assert.equal(D1.find('products', P.id).salePrice, 140, 'تغییرِ هنوز در صف دست نخورد');
  assert.equal(D1.engine.queued(), 1);

  assert.equal((await D1.engine.sync()).ok, true);
  assert.equal(D1.engine.queued(), 0);
  assert.equal(D1.find('products', P.id).salePrice, 50, 'دستگاهِ ساعت‌کج همان قیمتِ سرور را دارد');
  assert.equal(await D1.engine.record(), 0, 'نشاندنِ current هیچ op ِ تازه‌ای نمی‌سازد');
  assert.equal(D1.find('products', P.id).name, 'چای');

  //  بازنده در صندوق، برگرداندنی؛ برگرداندن ⇒ هر دو دستگاه
  const box = (await D1.engine.fetchConflicts('open')).filter((c) => c.row_id === P.id && c.kind === 'clock_skew');
  assert.equal(box.length, 2, JSON.stringify(box));
  const last = box.find((c) => c.loser_value && c.loser_value.salePriceMinor === 14000);
  assert.ok(last && last.revertible, JSON.stringify(box));
  assert.equal((await D1.engine.resolveConflict(last.id, 'revert')).ok, true);
  assert.equal((await D1.engine.sync()).ok, true);
  assert.equal((await D2.engine.sync()).ok, true);
  assert.equal(D1.find('products', P.id).salePrice, 140);
  assert.equal(D2.find('products', P.id).salePrice, 140);
  assert.equal((await rowOf('products', P.id)).data.salePriceMinor, 14000);
});

test('حذفِ کهنه: دستگاهی که کالا را پاک کرد و دیگری پس از آن قیمت را عوض کرد ⇒ کالای کامل، همان سرور، نه ردیفِ نیمه', async () => {
  const P = { id: 'p-cur-del', name: 'شکر', salePrice: 70, purchasePrice: 60, minStock: 1.5, barcodes: ['6260000000031'], stock: 0 };
  const D1 = await ready(device('cur-del-1', { products: [P] }));
  const D2 = await ready(device('cur-del-2', {}));
  D1.tr.offline = true;
  D1.edit((x) => { x.products = x.products.filter((p) => p.id !== P.id); });
  assert.equal(await D1.engine.record(), 1);
  const hDel = D1.engine.outbox[0].ops[0].hlc;
  while (D2.engine.now() <= V3.hlc.parse(hDel).wall) await new Promise((r) => setTimeout(r, 5));
  setPrice(D2, P.id, 75);
  assert.equal((await D2.engine.sync()).ok, true);

  D1.tr.offline = false;
  assert.equal((await D1.engine.sync()).ok, true);
  const srv = (await rowOf('products', P.id));
  assert.equal(srv.deleted_at, null, 'ویرایشِ تازه‌تر ردیف را نگه داشت');
  assert.deepEqual(D1.find('products', P.id), D2.find('products', P.id), 'همان کالای کامل روی هر دو دستگاه');
  assert.equal(D1.find('products', P.id).name, 'شکر');
  assert.deepEqual(D1.find('products', P.id).barcodes, ['6260000000031']);
  assert.equal(D1.find('products', P.id).salePrice, 75);
  assert.equal(await D1.engine.record(), 0, 'بی op ِ تازه');
  const box = await D1.engine.fetchConflicts('open');
  assert.ok(box.some((c) => c.row_id === P.id && c.kind === 'stale_delete' && c.revertible), JSON.stringify(box.map((c) => c.kind)));
});

test('«تاریخِ تصحیح‌شده» ِ فاکتور و حرکتِ خودِ دستگاه (سالِ ۲۰۰۰) روی همان دستگاه — حتی با پاسخِ گم‌شده', async () => {
  const D = await ready(device('cur-y2k', {}));
  const O = await ready(device('cur-y2k-other', {}));
  const y2k = Date.UTC(2000, 0, 1, 9);
  D.edit((x) => {
    x.sales = [...(x.sales || []), { id: 's-cur-y2k', total: 140, finalTotal: 140, paidAmount: 140, remaining: 0, status: 'completed', createdAt: y2k, date: '2000-01-01' }];
    x.stockMovements = [...(x.stockMovements || []), { id: 's-cur-y2k-m', productId: 'p-y2k', type: 'sale', qty: -1, createdAt: y2k, refId: 's-cur-y2k' }];
  });
  assert.equal(await D.engine.record(), 2);
  D.tr.loseNext = 1;
  assert.equal((await D.engine.pushOnce()).ok, false, 'پاسخ گم شد');
  assert.equal(D.engine.queued(), 2);
  assert.equal((await D.engine.sync()).ok, true);
  assert.equal(D.engine.queued(), 0);

  const srvSale = (await rowOf('sales', 's-cur-y2k')).data;
  const srvMove = (await rowOf('stockMovements', 's-cur-y2k-m')).data;
  assert.ok(Number.isSafeInteger(srvSale.correctedAt) && Math.abs(srvSale.correctedAt - Date.now()) < 60 * 60_000);
  assert.equal(D.find('sales', 's-cur-y2k').correctedAt, srvSale.correctedAt, 'فاکتورِ خودِ دستگاه همان تاریخِ تصحیح‌شدهٔ سرور');
  assert.equal(D.find('stockMovements', 's-cur-y2k-m').correctedAt, srvMove.correctedAt, 'حرکتِ خودِ دستگاه هم (نگهبانِ ساعتِ INV-10)');
  assert.equal(D.find('sales', 's-cur-y2k').createdAt, y2k, 'createdAt ِ دستگاه همان');
  assert.equal(await D.engine.record(), 0, 'بی op ِ تازه (سایه همان دفتر)');
  assert.equal((await O.engine.sync()).ok, true);
  assert.equal(O.find('sales', 's-cur-y2k').correctedAt, srvSale.correctedAt, 'دستگاهِ دیگر با Pull، مثلِ پیش');
});
