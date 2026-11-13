'use strict';
/**
 * SEC-03 (نیمهٔ سرور) — لغوِ فاکتور حلقهٔ زنجیرهٔ سرور می‌شود و بازگرداندنِ تعارض فاکتورِ ثبت‌شده را عوض نمی‌کند
 * (`lib/finalized-sales.js`، همان پرچمِ خاموشِ `shop.doc_chain`).
 *
 *   ۱) پرچمِ خاموش ⇒ هیچ حلقه‌ای (رفتارِ امروز).
 *   ۲) لغو از هر راه (v1 ِ دستگاهِ زنجیره‌دار و بی زنجیره، راهِ قدیم، API ِ مستقیم) ⇒ **یک** حلقه در `void/<دکان>`،
 *      در همان تراکنشِ نوشتن؛ تکرار، بازپخش، لغوِ دوباره و برگشت‌ولغوِ دوباره ⇒ هیچ حلقهٔ تازه.
 *   ۳) بدنه بی متنِ دلیل (فقط درهم)؛ مبلغ با متنِ عددِ جاوااسکریپت (همان `JsNumber` ِ کاتلین)؛ `verifyChain` سالم.
 *   ۴) دستهٔ ردشده (۴۰۹ ِ API) هیچ حلقه‌ای نمی‌گذارد؛ شکستِ زنجیره لغو را نمی‌بندد (رویدادِ `void_unrecorded`).
 *   ۵) جداییِ دکان‌ها و شناسهٔ دستگاهِ دکانِ دیگر.
 *   ۶) بازگرداندنِ تعارض (مدیرِ ارشد) روی فیلدِ مالیِ فاکتورِ ثبت‌شده ۴۰۹؛ فیلدِ آزاد و پرچمِ خاموش همان امروز.
 */
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');
const hc = require('../src/lib/hash-chain');
const chainStore = require('../src/lib/hash-chain-store');
const syncV1 = require('../src/lib/sync-v1');
const SCHEMA = require('../src/lib/sync-v1-migrations').currentVersion('shop');

const CHAIN = 'dev-void-chain';   // دستگاهِ زنجیره‌دار
const OLD = 'dev-void-old';       // بی زنجیره (برنامهٔ کهنه، وب)
const REASON = 'sale_finalized_edit_forbidden';

let owner;
let shopId;
let other;
let otherShop;
let seq = 0;

const opId = () => `01JVOID${String(Date.now()).slice(-8)}${String(++seq).padStart(5, '0')}`;
const op = (table, rowId, type, fields, ts = Date.now()) => ({ op_id: opId(), ts, table, row_id: rowId, type, fields });
const push = (dev, ops, token = owner.accessToken) => h.api('POST', '/api/sync/v1/push', {
  token, headers: { 'X-App': 'shop', 'X-Device': dev }, body: { device_id: dev, schema_version: SCHEMA, caps: ['a00'], ops },
});
const statuses = (r) => r.body.results.map((x) => (x.status === 'rejected' ? `rejected:${x.reason}` : x.status));
const rowOf = async (table, id, shop = shopId) => {
  const r = await one(
    "SELECT data FROM sync_rows WHERE app='shop' AND account_kind='shop' AND account_id=$1 AND table_name=$2 AND row_id=$3",
    [shop, table, id]
  );
  return r ? r.data : null;
};
const flag = (on, shop = shopId) => query(
  `INSERT INTO live_config (scope, key, value, updated_at) VALUES ($1,'shop.doc_chain',$2,1)
   ON CONFLICT (scope, key) DO UPDATE SET value = excluded.value`, [shop, on ? 'true' : 'false']
);
const chainOf = (shop = shopId) => `void/${shop}`;
const links = async (shop = shopId) => (await many(
  'SELECT seq, body FROM hash_chain WHERE chain=$1 ORDER BY seq', [chainOf(shop)]
)).map((r) => ({ seq: Number(r.seq), raw: r.body, ...JSON.parse(r.body) }));
const linksFor = async (sale, shop = shopId) => (await links(shop)).filter((l) => l.saleId === sale);
const events = async (problem, shop = shopId) => (await many(
  "SELECT target_id, detail FROM audit_logs WHERE action='security.doc_chain' AND shop_id=$1 ORDER BY created_at, id", [shop]
)).map((r) => ({ sale: r.target_id, ...(typeof r.detail === 'string' ? JSON.parse(r.detail) : r.detail) }))
  .filter((e) => e.problem === problem);

function saleOps(id, { ts = Date.now(), credit = false, total = 1000, status = 'completed', invoiceNumber = 7 } = {}) {
  const sale = {
    id, invoiceNumber, series: 'B1', total, discountType: 'amount', discountValue: 0, discount: 0, finalTotal: total,
    paymentMethod: credit ? 'credit' : 'cash', debtorId: credit ? 'deb-1' : null,
    paidAmount: credit ? 0 : total, remaining: credit ? total : 0, status,
    debtGiven: credit ? total : 0, debtSettled: 0, createdAt: ts, date: '2026-11-01', syncStatus: 'pending',
    cancelReason: status === 'cancelled' ? 'بی اینترنت لغو شد' : '', seller: 'صاحب', channel: 'pos',
  };
  const item = { id: `${id}-i1`, saleId: id, productId: 'p1', name: '', quantity: 1, unitPrice: total, purchasePrice: 1, totalPrice: total, returnedQty: 0 };
  return [op('sales', id, 'insert', sale, ts), op('saleItems', item.id, 'insert', item, ts)];
}
const cancel = (id, reason = 'اشتباهِ صندوق', extra = {}) => op('sales', id, 'update', { status: 'cancelled', cancelReason: reason, ...extra });

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
  owner = await h.newUser('صاحبِ لغو');
  shopId = (await h.post('/api/shop', { name: 'دکانِ لغو' }, { token: owner.accessToken })).body.shop.id;
  other = await h.newUser('صاحبِ دکانِ دیگرِ لغو');
  otherShop = (await h.post('/api/shop', { name: 'دکانِ دیگرِ لغو' }, { token: other.accessToken })).body.shop.id;
  await query(
    `INSERT INTO device_chain_heads (shop_id, series, epoch, device_id, reported_at, created_at) VALUES ($1,'B1',1,$2,1,1)`,
    [shopId, CHAIN]
  );
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'void-arshad','ارشد',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
});
test.after(async () => { await h.stop(); });

test('پرچمِ خاموش: لغو همان امروز است و هیچ حلقه‌ای نمی‌سازد', async () => {
  let r = await push(CHAIN, saleOps('v-off'));
  assert.deepEqual(statuses(r), ['applied', 'applied']);
  r = await push(CHAIN, [cancel('v-off')]);
  assert.deepEqual(statuses(r), ['applied']);
  assert.equal((await rowOf('sales', 'v-off')).status, 'cancelled');
  assert.deepEqual(await links(), []);
});

test('دستگاهِ زنجیره‌دار: لغو ⇒ یک حلقه؛ تکرار، لغوِ دوباره و بازپخش ⇒ هیچ حلقهٔ تازه؛ زنجیره سالم', async () => {
  await flag(true);
  const original = saleOps('v1', { credit: true });
  let r = await push(CHAIN, original);
  assert.deepEqual(statuses(r), ['applied', 'applied']);
  assert.deepEqual(await linksFor('v1'), [], 'ثبت حلقهٔ لغو نیست');

  const c1 = cancel('v1', 'اشتباهِ صندوق', { debtSettled: { $inc: 1000 } });
  r = await push(CHAIN, [c1]);
  assert.deepEqual(statuses(r), ['applied']);
  let got = await linksFor('v1');
  assert.equal(got.length, 1);
  const l = got[0];
  assert.equal(l.kind, 'doc');
  assert.equal(l.type, 'void');
  assert.equal(l.v, 1);
  assert.equal(l.chain, chainOf());
  assert.equal(l.invoiceNumber, '7');
  assert.equal(l.series, 'B1');
  assert.equal(l.total, '1000');
  assert.equal(l.path, 'v1');
  assert.equal(l.enforced, true);
  assert.equal(l.deviceId, CHAIN);
  assert.equal(l.by, owner.user.id);
  assert.equal(l.atInsert, false);
  assert.equal(l.store, 'v1');
  assert.equal(l.reasonHash, hc.sha256Hex('اشتباهِ صندوق'));
  assert.match(l.saleHash, /^[0-9a-f]{64}$/);
  assert.ok(Number.isSafeInteger(l.at) && l.at > 0);
  assert.ok(!l.raw.includes('اشتباه'), 'متنِ دلیل در زنجیره نمی‌آید (شاید دادهٔ شخصی)');

  //  همان op_id (پاسخِ گم‌شده) ⇐ «تکراری» و هیچ حلقه
  r = await push(CHAIN, [c1]);
  assert.deepEqual(statuses(r), ['duplicate']);
  //  لغوِ دوباره با op_id ِ تازه (دستگاهِ دیگر، دفترِ کهنه) ⇐ نشست ولی لغو نیست
  r = await push(CHAIN, [cancel('v1', 'دلیلِ دیگر')]);
  assert.deepEqual(statuses(r), ['applied']);
  //  بازپخشِ درجِ پیش از لغو (پشتیبانِ کهنه) ⇐ بازپخش، بی بازنویسی و بی حلقه
  r = await push(CHAIN, original.map((o) => ({ ...o, op_id: opId() })));
  assert.deepEqual(statuses(r), ['duplicate', 'duplicate']);
  got = await linksFor('v1');
  assert.equal(got.length, 1, 'یک فاکتور ⇐ یک حلقهٔ لغو');
  assert.equal((await rowOf('sales', 'v1')).status, 'cancelled');

  const v = await chainStore.verifyChain(chainOf());
  assert.equal(v.ok, true, JSON.stringify(v));
});

test('مبلغ با متنِ دقیقِ عددِ جاوااسکریپت؛ درهمِ محتوای مالی همان فاکتور را می‌گوید', async () => {
  const totals = [0.1 + 0.2, 12345678.5, 0.0004, 1e21];
  for (const [i, total] of totals.entries()) {
    const id = `v-num-${i}`;
    let r = await push(CHAIN, saleOps(id, { total }));
    assert.deepEqual(statuses(r), ['applied', 'applied']);
    r = await push(CHAIN, [cancel(id)]);
    assert.deepEqual(statuses(r), ['applied']);
    const [l] = await linksFor(id);
    assert.equal(l.total, String(total), `${total}`);
  }
  assert.deepEqual((await linksFor('v-num-0')).map((l) => l.total), ['0.30000000000000004']);
  assert.deepEqual((await linksFor('v-num-3')).map((l) => l.total), ['1e+21']);
  //  دو فاکتور با محتوای مالیِ دیگر ⇐ درهمِ دیگر
  assert.notEqual((await linksFor('v-num-0'))[0].saleHash, (await linksFor('v-num-1'))[0].saleHash);
  assert.equal((await chainStore.verifyChain(chainOf())).ok, true);
});

test('دستگاهِ بی زنجیره (log): لغو حلقه می‌شود؛ برگشت از لغو رویداد؛ لغوِ دوباره حلقهٔ دوم نیست', async () => {
  let r = await push(OLD, saleOps('v-old'));
  assert.deepEqual(statuses(r), ['applied', 'applied']);
  r = await push(OLD, [cancel('v-old')]);
  assert.deepEqual(statuses(r), ['applied']);
  let got = await linksFor('v-old');
  assert.equal(got.length, 1);
  assert.deepEqual([got[0].enforced, got[0].deviceId, got[0].path], [false, OLD, 'v1']);
  //  برگشت از لغو: برنامهٔ بی زنجیره همان امروز می‌نشیند + رویداد
  r = await push(OLD, [op('sales', 'v-old', 'update', { status: 'completed' })]);
  assert.deepEqual(statuses(r), ['applied']);
  assert.ok((await events('finalized_edit')).some((e) => e.sale === 'v-old' && e.edits === 'sales/v-old update: status'));
  r = await push(OLD, [cancel('v-old')]);
  assert.deepEqual(statuses(r), ['applied']);
  got = await linksFor('v-old');
  assert.equal(got.length, 1, 'سندِ معکوسِ یک فاکتور یک بار');
  assert.deepEqual((await events('void_unrecorded')).filter((e) => e.sale === 'v-old'), [], 'لغوِ دوباره «شکست» هم نیست');
});

test('فاکتوری که بی اینترنت ثبت و لغو شد (درج با وضعیتِ لغو) ⇒ یک حلقه با `atInsert`', async () => {
  const r = await push(CHAIN, saleOps('v-ins', { status: 'cancelled' }));
  assert.deepEqual(statuses(r), ['applied', 'applied']);
  const got = await linksFor('v-ins');
  assert.equal(got.length, 1);
  assert.equal(got[0].atInsert, true);
  assert.equal(got[0].reasonHash, hc.sha256Hex('بی اینترنت لغو شد'));
});

test('دستهٔ ردشده حلقه نمی‌گذارد؛ ویرایشِ مالی + لغو از دستگاهِ زنجیره‌دار رد و بی حلقه', async () => {
  let r = await push(CHAIN, saleOps('v-rej'));
  r = await push(CHAIN, [cancel('v-rej', 'دلیل', { total: 1, finalTotal: 1 })]);
  assert.deepEqual(statuses(r), [`rejected:${REASON}`]);
  assert.deepEqual(await linksFor('v-rej'), []);
  assert.equal((await rowOf('sales', 'v-rej')).status, 'completed');
});

test('راهِ قدیم: لغو با دستگاهِ زنجیره‌دار حلقهٔ `legacy` ِ اجباری، بی زنجیره رویداد؛ ۴۰۹ (همه یا هیچ) هیچ حلقه‌ای', async () => {
  const T = owner.accessToken;
  const H = { 'X-App': 'shop' };
  const sale = { invoiceNumber: 11, series: 'A1', total: 500, finalTotal: 500, paidAmount: 500, remaining: 0, status: 'completed', date: '2026-11-01', createdAt: 5 };
  //  درِ مستقیمِ API (`/api/sales/full`، `PUT /api/sales/:id`) برداشته شد؛ همان `pushChanges` با دستگاهِ زنجیره‌دار ⇒ همان اجبار
  const legacy = (deviceId, changes) => h.api('POST', '/api/sync/push', {
    token: T, headers: H, body: { deviceId, changes: changes.map((c) => ({ updatedAt: Date.now(), ...c })) },
  });
  const full = (id, deviceId = 'old-app') => legacy(deviceId, [
    { collection: 'sales', id, data: { ...sale } },
    { collection: 'saleItems', id: `${id}-i1`, data: { saleId: id, productId: 'p1', quantity: 1, unitPrice: 500, totalPrice: 500 } },
  ]);
  for (const id of ['L1', 'L2']) {
    const r = await full(id);
    assert.deepEqual([r.status, r.body.applied], [200, 2], JSON.stringify(r.body));
  }
  //  لغو + تغییرِ مبلغ با دستگاهِ زنجیره‌دار ⇒ ۴۰۹ و هیچ چیزی (نه فاکتور، نه حلقه)
  let r = await legacy(CHAIN, [{ collection: 'sales', id: 'L1', data: { ...sale, status: 'cancelled', cancelReason: 'x', total: 1, finalTotal: 1 } }]);
  assert.equal(r.status, 409, JSON.stringify(r.body));
  assert.deepEqual(await linksFor('L1'), []);
  r = await legacy(CHAIN, [{ collection: 'sales', id: 'L1', data: { ...sale, status: 'cancelled', cancelReason: 'اشتباه' } }]);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  let got = await linksFor('L1');
  assert.deepEqual(got.map((l) => [l.path, l.enforced, l.invoiceNumber, l.series, l.total]), [['legacy', true, '11', 'A1', '500']]);
  //  همان لغو دوباره ⇒ هیچ
  r = await legacy(CHAIN, [{ collection: 'sales', id: 'L1', data: { ...sale, status: 'cancelled', cancelReason: 'اشتباه' } }]);
  assert.equal(r.status, 200);
  assert.equal((await linksFor('L1')).length, 1);

  //  راهِ قدیمِ برنامهٔ کهنه (بی زنجیره)
  r = await legacy('old-app', [{ collection: 'sales', id: 'L2', data: { ...sale, status: 'cancelled', cancelReason: 'کهنه' } }]);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  got = await linksFor('L2');
  assert.deepEqual(got.map((l) => [l.path, l.enforced, l.deviceId, l.store]), [['legacy', false, 'old-app', 'rest']]);

  //  همان شناسه در انبارِ دیگر فاکتورِ دیگری است: لغوِ ساختگیِ «راهِ قدیم» با شناسهٔ فاکتورِ v1 لغوِ واقعیِ آن را نمی‌پوشاند
  r = await full('v-twin');
  assert.equal(r.status, 200);
  r = await legacy('old-app', [{ collection: 'sales', id: 'v-twin', data: { ...sale, status: 'cancelled', cancelReason: 'پیش‌دستی' } }]);
  assert.equal(r.status, 200);
  await push(CHAIN, saleOps('v-twin'));
  r = await push(CHAIN, [cancel('v-twin')]);
  assert.deepEqual(statuses(r), ['applied']);
  assert.deepEqual((await linksFor('v-twin')).map((l) => l.store), ['rest', 'v1']);
  assert.equal((await chainStore.verifyChain(chainOf())).ok, true);
});

test('PostgreSQL: لغوهای هم‌زمانِ v1 و راهِ قدیمِ زنجیره‌دارِ یک دکان ⇒ هر فاکتور یک حلقه، زنجیرهٔ پیوسته، بی ۵۰۰ و بی بن‌بست',
  { skip: require('../src/db').isPglite() && 'PGlite یک اتصال دارد؛ رقابت فقط روی PostgreSQL' }, async () => {
    const T = owner.accessToken;
    const H = { 'X-App': 'shop' };
    const sale = { invoiceNumber: 12, series: 'A1', total: 700, finalTotal: 700, paidAmount: 700, remaining: 0, status: 'completed', date: '2026-11-01', createdAt: 6 };
    const N = 8;
    const before = (await links()).length;
    for (let i = 0; i < N; i++) {
      await push(`dev-race-${i}`, saleOps(`r-v1-${i}`));
      const r = await h.api('POST', '/api/sync/push', { token: T, headers: H, body: { deviceId: 'old-race', changes: [
        { collection: 'sales', id: `r-api-${i}`, updatedAt: Date.now(), data: { ...sale } },
        { collection: 'saleItems', id: `r-api-${i}-i1`, updatedAt: Date.now(), data: { saleId: `r-api-${i}`, productId: 'p1', quantity: 1, unitPrice: 700, totalPrice: 700 } },
      ] } });
      assert.equal(r.status, 200, JSON.stringify(r.body));
    }
    const jobs = [];
    for (let i = 0; i < N; i++) {
      //  دو دستگاه همان فاکتورِ v1 را هم‌زمان لغو می‌کنند؛ دو درخواستِ راهِ قدیمِ زنجیره‌دار همان فاکتور را
      jobs.push(push(`dev-race-${i}`, [cancel(`r-v1-${i}`)]), push(`dev-race-b${i}`, [cancel(`r-v1-${i}`, 'دیگری')]));
      const put = () => h.api('POST', '/api/sync/push', { token: T, headers: H, body: { deviceId: CHAIN, changes: [
        { collection: 'sales', id: `r-api-${i}`, updatedAt: Date.now(), data: { ...sale, status: 'cancelled', cancelReason: 'هم‌زمان' } },
      ] } });
      jobs.push(put(), put());
    }
    const out = await Promise.all(jobs);
    for (const r of out) assert.equal(r.status, 200, JSON.stringify(r.body));
    const all = await links();
    assert.equal(all.length, before + 2 * N);
    assert.deepEqual(all.map((l) => l.seq), all.map((_, i) => i + 1), 'seq پیوسته');
    for (let i = 0; i < N; i++) {
      assert.equal((await linksFor(`r-v1-${i}`)).length, 1, `r-v1-${i}`);
      assert.equal((await linksFor(`r-api-${i}`)).length, 1, `r-api-${i}`);
    }
    assert.equal((await chainStore.verifyChain(chainOf())).ok, true);
  });

test('شکستِ زنجیره لغو را نمی‌بندد: لغو و بقیهٔ دسته می‌نشینند، رویدادِ `void_unrecorded`', async () => {
  await push(CHAIN, saleOps('v-fail'));
  await query(`CREATE OR REPLACE FUNCTION test_refuse_void_link() RETURNS trigger LANGUAGE plpgsql AS $$
    BEGIN IF NEW.chain LIKE 'void/%' THEN RAISE EXCEPTION 'test: void chain down'; END IF; RETURN NEW; END; $$`);
  await query('CREATE TRIGGER test_refuse_void_link BEFORE INSERT ON hash_chain FOR EACH ROW EXECUTE FUNCTION test_refuse_void_link()');
  let r;
  try {
    r = await push(CHAIN, [cancel('v-fail'), op('products', 'p-after-fail', 'insert', { name: 'کالای پس از شکست' })]);
  } finally {
    await query('DROP TRIGGER IF EXISTS test_refuse_void_link ON hash_chain');
  }
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(statuses(r), ['applied', 'applied']);
  assert.equal((await rowOf('sales', 'v-fail')).status, 'cancelled');
  assert.equal((await rowOf('products', 'p-after-fail')).name, 'کالای پس از شکست');
  assert.deepEqual(await linksFor('v-fail'), []);
  const ev = (await events('void_unrecorded')).filter((e) => e.sale === 'v-fail');
  assert.equal(ev.length, 1);
  assert.equal(ev[0].path, 'v1');
  assert.equal((await chainStore.verifyChain(chainOf())).ok, true);
});

test('جداییِ دکان‌ها: همان شناسهٔ فاکتور و دستگاه در دکانِ دیگر زنجیرهٔ خودش؛ دکانِ نخست دست نمی‌خورد', async () => {
  const before = (await links()).length;
  await flag(true, otherShop);
  let r = await push(CHAIN, saleOps('v1'), other.accessToken);
  assert.deepEqual(statuses(r), ['applied', 'applied']);
  r = await push(CHAIN, [cancel('v1')], other.accessToken);
  assert.deepEqual(statuses(r), ['applied']);
  const got = await linksFor('v1', otherShop);
  assert.equal(got.length, 1);
  assert.equal(got[0].chain, chainOf(otherShop));
  assert.equal(got[0].enforced, false, 'سریِ دستگاه در دکانِ نخست است؛ این‌جا بی زنجیره');
  assert.equal((await links()).length, before);
  await flag(false, otherShop);
});

test('دست‌کاری: حلقهٔ لغو عوض یا پاک نمی‌شود', async () => {
  const [l] = await linksFor('v1');
  await assert.rejects(query('UPDATE hash_chain SET body=$2 WHERE chain=$1 AND seq=$3', [chainOf(), l.raw.replace('"1000"', '"1"'), l.seq]));
  await assert.rejects(query('DELETE FROM hash_chain WHERE chain=$1', [chainOf()]));
  assert.equal((await chainStore.verifyChain(chainOf())).ok, true);
});

test('نمایهٔ ۳۲۶: هر حلقهٔ لغو یک ردیف (دکان، انبار، فاکتور ⇐ seq)؛ rollback فقط نمایه را برمی‌دارد', async () => {
  const fs = require('node:fs');
  const path = require('node:path');
  const idx = await many('SELECT store, sale_id, seq FROM sale_void_index WHERE shop_id=$1 ORDER BY seq', [shopId]);
  const all = await links();
  assert.deepEqual(idx.map((r) => [r.store, r.sale_id, Number(r.seq)]), all.map((l) => [l.store, l.saleId, l.seq]));
  const dir = path.join(__dirname, '..', 'migrations');
  const name = '326_sale_void_index.sql';
  const mark = await one('SELECT * FROM schema_migrations WHERE version = $1', [name]);
  assert.ok(mark, 'مهاجرت نشسته است');
  const has = async (t) => (await query('SELECT 1 FROM information_schema.tables WHERE table_name=$1', [t])).rows.length;
  const saved = await many('SELECT shop_id, store, sale_id, seq, created_at FROM sale_void_index');
  await query(fs.readFileSync(path.join(dir, 'rollback', '326_sale_void_index.down.sql'), 'utf8'));
  assert.equal(await has('sale_void_index'), 0);
  assert.equal(await has('hash_chain'), 1, 'زنجیره دست نمی‌خورد');
  assert.equal((await links()).length, all.length);
  await query(fs.readFileSync(path.join(dir, name), 'utf8'));
  await query('INSERT INTO schema_migrations (version, checksum, applied_at) VALUES ($1,$2,$3)', [mark.version, mark.checksum, mark.applied_at]);
  for (const r of saved) {
    await query('INSERT INTO sale_void_index (shop_id, store, sale_id, seq, created_at) VALUES ($1,$2,$3,$4,$5)', [r.shop_id, r.store, r.sale_id, r.seq, r.created_at]);
  }
  //  پس از برگشت همان رفتار: لغوِ دوبارهٔ فاکتورِ لغوشده حلقهٔ تازه نمی‌گیرد
  const r = await push(OLD, [op('sales', 'v-old', 'update', { status: 'completed' }), cancel('v-old')]);
  assert.deepEqual(statuses(r), ['applied', 'applied']);
  assert.equal((await linksFor('v-old')).length, 1);
});

test('بازگرداندنِ تعارض: فیلدِ مالیِ فاکتورِ ثبت‌شده ۴۰۹؛ فیلدِ آزاد و پرچمِ خاموش همان امروز', async () => {
  const token = (await h.post('/api/admin/login', { username: 'void-arshad', password: 'Admin!12345' })).body.token;
  await push(CHAIN, saleOps('v-cf'));
  const conflict = async (sale, field, loser) => (await one(
    `INSERT INTO sync_conflicts (app, account_kind, account_id, table_name, row_id, field, loser_value, winner_value,
                                 loser_op_id, winner_op_id, loser_device, winner_device, at)
     VALUES ('shop','shop',$1,'sales',$2,$3,$4::jsonb,'null'::jsonb,'o1','o2',$5,$6,$7) RETURNING id`,
    [shopId, sale, field, JSON.stringify(loser), OLD, CHAIN, now()]
  )).id;
  const restore = (id) => h.post(`/api/admin/sync/conflicts/${id}/restore`, { reason: 'درخواستِ صاحب' }, { token });

  let r = await restore(await conflict('v-cf', 'finalTotal', 1));
  assert.equal(r.status, 409, JSON.stringify(r.body));
  assert.equal(r.body.error.code, REASON);
  assert.equal((await rowOf('sales', 'v-cf')).finalTotal, 1000);
  assert.ok((await events('finalized_edit')).some((e) => e.sale === 'v-cf' && e.path === 'admin' && e.enforced === true));
  //  برگشت از لغو هم ویرایش است
  await push(CHAIN, [cancel('v-cf')]);
  r = await restore(await conflict('v-cf', 'status', 'completed'));
  assert.equal(r.status, 409);
  assert.equal((await rowOf('sales', 'v-cf')).status, 'cancelled');
  //  فیلدِ آزاد (برچسب) همان امروز
  r = await restore(await conflict('v-cf', 'seller', 'مدیر'));
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal((await rowOf('sales', 'v-cf')).seller, 'مدیر');
  //  پرچمِ خاموش ⇒ همان امروز
  await flag(false);
  r = await restore(await conflict('v-cf', 'finalTotal', 2));
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal((await rowOf('sales', 'v-cf')).finalTotal, 2);
  await flag(true);
});
