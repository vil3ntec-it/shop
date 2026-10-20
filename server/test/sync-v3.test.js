'use strict';
/**
 * Sync v3 — سمتِ سرور (بخشِ ۳.۸؛ SYN-01..08، 10، 11، 13)، از راهِ HTTPِ واقعی.
 *
 * هر بند یک سنجهٔ روشن دارد:
 *   • خاموش به‌طورِ پیش‌فرض ⇒ همان ۴۰۴ِ سرورِ قدیمی؛ v1 دست‌نخورده
 *   • تکرار ⇒ «تکراری»، نه دو بار (دلتا هم)
 *   • ترتیبِ رسیدن مهم نیست: برنده با HLC
 *   • ساعتِ ۵+ دقیقه جلوتر ⇒ ثبت، ولی نمی‌برد و ساعتِ سرور را جلو نمی‌برد
 *   • حذف/ویرایش با HLC؛ سندِ مالی فقط افزودنی؛ لغو جدا
 *   • tx: سند + قلم + حرکت + پرداخت همه یا هیچ
 *   • شمارشِ انبار با HLC و حرکتِ دیررس
 *   • صندوقِ تعارض: دیدن، نگه داشتن، برگرداندن، با اجازه
 *   • مبدلِ v2 و پلِ v1 ⇒ v3
 */
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';
process.env.SYNC_V3_ENABLED = '1';

const test = require('node:test');
const assert = require('node:assert/strict');
const zlib = require('node:zlib');
const { execFileSync } = require('node:child_process');
const path = require('node:path');
const h = require('./helpers');
const { one, query, newId, now } = require('../src/db');
const config = require('../src/config');
const hlc = require('../src/lib/hlc');
const syncV1 = require('../src/lib/sync-v1');

const A = 'v3-dev-a';
const B = 'v3-dev-b';
const C = 'v3-dev-c';

let owner;
let staff;
let shopId;
let n = 0;
const oid = () => `01JV3${String(++n).padStart(21, '0')}`;
const BASE = Date.now() - 3_600_000;     //  یک ساعت پیش — هیچ HLCی «کج» نیست مگر عمداً
let tick = 0;
/** HLCِ تازه و بزرگ‌تر از همهٔ قبلی‌ها، روی یک دستگاه */
const H = (node, wall = BASE + (++tick) * 10) => hlc.format({ wall, counter: 0, node });

const push = (token, device, ops, extra = {}) => h.api('POST', '/api/sync/v3/push', {
  token, body: { device_id: device, protocol: 3, ops, ...extra },
});
const pull = (token, device, since = 0, q = '') => h.api('GET',
  `/api/sync/v3/pull?device_id=${device}&since=${since}${q}`, { token });
const rowOf = (table, id) => one(
  `SELECT * FROM sync_v3_rows WHERE app='shop' AND account_id=$1 AND table_name=$2 AND row_id=$3`, [shopId, table, id]
);
const conflictsOf = async (token, status = 'open') =>
  (await h.api('GET', `/api/sync/v3/conflicts?status=${status}`, { token })).body.conflicts;

async function pullAll(token, device, since = 0, q = '') {
  const all = [];
  let cursor = since;
  for (let i = 0; i < 100; i++) {
    const r = await pull(token, device, cursor, q);
    assert.equal(r.status, 200, JSON.stringify(r.body));
    all.push(...r.body.ops);
    cursor = r.body.cursor;
    if (!r.body.has_more) break;
  }
  return { ops: all, cursor };
}

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
  owner = await h.newUser('صاحبِ دکانِ v3', 'shop');
  const shop = await h.api('POST', '/api/shop', { token: owner.accessToken, body: { name: 'دکانِ v3' } });
  shopId = shop.body?.shop?.id || shop.body?.id;
  owner = await h.signIn(owner, 'shop');

  //  شاگرد: عضوِ همان دکان
  const s = await h.newUser('شاگرد', 'shop');
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at)
     VALUES ($1,$2,$3,'staff','active',$4,$4)`,
    [newId('mem'), shopId, s.user.id, now()]
  );
  staff = await h.signIn(s, 'shop');
});
test.after(async () => { await h.stop(); });

/* ------------------------------------------------------------ پرچم */

test('پیش‌فرض خاموش است: بی SYNC_V3_ENABLED مقدار false', () => {
  const env = { ...process.env, ENV_FILE: path.join(__dirname, 'no-such.env') };
  delete env.SYNC_V3_ENABLED;
  const out = execFileSync(process.execPath, ['-e', "console.log(require('./src/config').syncV3.enabled)"], {
    cwd: path.join(__dirname, '..'), env,
  }).toString().trim();
  assert.equal(out, 'false');
});

test('خاموش ⇒ همان ۴۰۴ِ مسیرِ ناشناخته، حتی با توکن؛ v1 کارش را می‌کند', async () => {
  config.syncV3.enabled = false;
  try {
    const r = await h.api('GET', '/api/sync/v3/hello', { token: owner.accessToken });
    const unknown = await h.api('GET', '/api/no-such-route', { token: owner.accessToken });
    assert.equal(r.status, 404);
    assert.deepEqual(r.body, unknown.body);
    const p = await push(owner.accessToken, A, []);
    assert.equal(p.status, 404);
    const v1 = await h.api('GET', `/api/sync/v1/status?device_id=${A}`, { token: owner.accessToken });
    assert.equal(v1.status, 200);
  } finally {
    config.syncV3.enabled = true;
  }
});

test('hello: پروتکل، سقف‌ها، واحدها و جدول‌ها (SYN-13 هم)؛ زمانِ سرور در هر پاسخ', async () => {
  const r = await h.api('GET', '/api/sync/v3/hello', { token: owner.accessToken });
  assert.equal(r.status, 200);
  assert.equal(r.body.protocol, 3);
  assert.equal(r.body.skew_limit_ms, 5 * 60 * 1000);
  assert.equal(r.body.tables.sales.policy, 'doc');
  assert.equal(r.body.tables.products.policy, 'master');
  for (const t of ['productCategories', 'productUnits', 'expenseCategories']) assert.ok(r.body.tables[t]);
  assert.ok(Math.abs(Number(r.headers.get('x-server-time')) - Date.now()) < 60_000);
  assert.ok(Math.abs(r.body.server_time - Date.now()) < 60_000);
});

test('بی توکن ⇒ ۴۰۱؛ توکنِ پمپ ⇒ ۴۰۳ (v3 فقط دکان)', async () => {
  const r = await h.api('GET', '/api/sync/v3/hello');
  assert.equal(r.status, 401);
  const pump = await h.newUser('پمپ‌دار', 'pump');
  const p = await h.api('GET', '/api/sync/v3/hello', { token: pump.accessToken });
  assert.equal(p.status, 403);
});

/* ------------------------------------------------------- تکرار (SYN-03) */

test('همان op دو بار ⇒ «تکراری» با همان server_seq؛ دلتا یک بار جمع می‌شود', async () => {
  const ins = { op_id: oid(), hlc: H(A), table: 'sales', row_id: 's-dup', kind: 'insert',
    fields: { totalMinor: 10000, paidAmountMinor: 0, status: 'completed' } };
  const delta = { op_id: oid(), hlc: H(A), table: 'sales', row_id: 's-dup', kind: 'delta', fields: { paidAmountMinor: 2500 } };
  const r1 = await push(owner.accessToken, A, [ins, delta]);
  assert.equal(r1.status, 200, JSON.stringify(r1.body));
  assert.deepEqual(r1.body.results.map(x => x.status), ['applied', 'applied']);
  const r2 = await push(owner.accessToken, A, [ins, delta]);
  assert.deepEqual(r2.body.results.map(x => x.status), ['duplicate', 'duplicate']);
  assert.equal(r2.body.results[0].server_seq, r1.body.results[0].server_seq);
  const row = await rowOf('sales', 's-dup');
  assert.equal(row.data.paidAmountMinor, 2500);
});

/* --------------------------------------- «آخرین نوشتن» با HLC (SYN-05/06) */

test('ترتیبِ رسیدن مهم نیست: HLCِ بزرگ‌تر می‌برد، بازنده در صندوق', async () => {
  const early = H(B);
  const late = H(A);
  //  ردیفِ ۱: اول تازه‌تر می‌رسد، بعد قدیمی‌تر
  await push(owner.accessToken, A, [{ op_id: oid(), hlc: late, table: 'products', row_id: 'p-order-1', kind: 'insert', fields: { name: 'A' } }]);
  const lost = await push(owner.accessToken, B, [{ op_id: oid(), hlc: early, table: 'products', row_id: 'p-order-1', kind: 'update', fields: { name: 'B' } }]);
  assert.equal(lost.body.results[0].status, 'conflict');
  assert.deepEqual(lost.body.results[0].current, { deleted: false, data: { name: 'A' } });
  //  ردیفِ ۲: برعکس
  await push(owner.accessToken, B, [{ op_id: oid(), hlc: early, table: 'products', row_id: 'p-order-2', kind: 'insert', fields: { name: 'B' } }]);
  const won = await push(owner.accessToken, A, [{ op_id: oid(), hlc: late, table: 'products', row_id: 'p-order-2', kind: 'update', fields: { name: 'A' } }]);
  assert.equal(won.body.results[0].status, 'applied');

  assert.equal((await rowOf('products', 'p-order-1')).data.name, 'A');
  assert.equal((await rowOf('products', 'p-order-2')).data.name, 'A');
  const box = (await conflictsOf(owner.accessToken)).filter(c => c.row_id === 'p-order-1');
  assert.equal(box.length, 1);
  assert.equal(box[0].kind, 'lww');
  assert.equal(box[0].loser_value, 'B');
  assert.equal(box[0].winner_value, 'A');
});

test('دو دستگاه، دو فیلدِ یک ردیف ⇒ هر دو می‌مانند، بی تعارض', async () => {
  await push(owner.accessToken, A, [{ op_id: oid(), hlc: H(A), table: 'products', row_id: 'p-two', kind: 'insert', fields: { name: 'x', salePriceMinor: 100 } }]);
  await pullAll(owner.accessToken, B);          //  B کالا را دارد، بعد آفلاین می‌رود
  const a = await push(owner.accessToken, A, [{ op_id: oid(), hlc: H(A), table: 'products', row_id: 'p-two', kind: 'update', fields: { name: 'نام' } }]);
  const b = await push(owner.accessToken, B, [{ op_id: oid(), hlc: H(B), table: 'products', row_id: 'p-two', kind: 'update', fields: { salePriceMinor: 15050 } }]);
  assert.equal(a.body.results[0].status, 'applied');
  assert.equal(b.body.results[0].status, 'applied');
  assert.equal(b.body.results[0].conflicts, undefined);
  const row = await rowOf('products', 'p-two');
  assert.deepEqual({ name: row.data.name, p: row.data.salePriceMinor }, { name: 'نام', p: 15050 });
});

test('نوشتهٔ دیده‌نشدهٔ دستگاهِ دیگر که بازنده شد به صندوق می‌رود؛ دیده‌شده نه', async () => {
  const d = 'v3-dev-unseen';
  await push(owner.accessToken, A, [{ op_id: oid(), hlc: H(A), table: 'debtors', row_id: 'c-seen', kind: 'insert', fields: { name: 'اول' } }]);
  const r = await push(owner.accessToken, d, [{ op_id: oid(), hlc: H(d), table: 'debtors', row_id: 'c-seen', kind: 'update', fields: { name: 'دوم' } }]);
  assert.equal(r.body.results[0].status, 'applied');
  assert.equal(r.body.results[0].conflicts, 1);
  const box = (await conflictsOf(owner.accessToken)).find(c => c.row_id === 'c-seen');
  assert.equal(box.loser_value, 'اول');

  //  حالا d اول pull می‌کند، بعد آگاهانه عوض می‌کند ⇒ تعارض نیست
  await push(owner.accessToken, A, [{ op_id: oid(), hlc: H(A), table: 'debtors', row_id: 'c-seen', kind: 'update', fields: { name: 'سوم' } }]);
  await pullAll(owner.accessToken, d);
  const r2 = await push(owner.accessToken, d, [{ op_id: oid(), hlc: H(d), table: 'debtors', row_id: 'c-seen', kind: 'update', fields: { name: 'چهارم' } }]);
  assert.equal(r2.body.results[0].conflicts, undefined);
});

/* -------------------------------------------------- ساعتِ غلط (SYN-05) */

test('HLCِ بیش از ۵ دقیقه جلوتر: ثبت می‌شود، نمی‌برد، ساعتِ سرور جلو نمی‌رود', async () => {
  await push(owner.accessToken, A, [{ op_id: oid(), hlc: H(A), table: 'products', row_id: 'p-skew', kind: 'insert', fields: { name: 'درست' } }]);
  const future = hlc.format({ wall: Date.now() + 10 * 60_000, counter: 0, node: C });
  const r = await push(owner.accessToken, C, [{ op_id: oid(), hlc: future, table: 'products', row_id: 'p-skew', kind: 'update', fields: { name: 'ساعتِ غلط' } }]);
  assert.equal(r.status, 200);
  const res = r.body.results[0];
  assert.equal(res.status, 'conflict');
  assert.equal(res.skewed, true);
  assert.ok(hlc.compare(r.body.hlc, future) < 0, 'ساعتِ سرور با آن جلو نرفت');
  assert.equal((await rowOf('products', 'p-skew')).data.name, 'درست');
  const stored = await one(`SELECT skewed, client_hlc FROM sync_v3_ops WHERE account_id=$1 AND op_id=$2`, [shopId, res.op_id]);
  assert.equal(stored.skewed, true);
  assert.equal(stored.client_hlc, future);

  //  نوشتهٔ عادیِ بعدی (با ساعتِ درست، کمی پیش از الان) هنوز می‌برد
  const ok = await push(owner.accessToken, A, [{ op_id: oid(), hlc: H(A, Date.now() - 1000), table: 'products', row_id: 'p-skew', kind: 'update', fields: { name: 'بعدی' } }]);
  assert.equal(ok.body.results[0].status, 'applied');

  const box = (await conflictsOf(owner.accessToken)).find(c => c.row_id === 'p-skew' && c.kind === 'clock_skew');
  assert.ok(box, 'در صندوقِ تعارض است');
  assert.deepEqual(box.loser_value, { name: 'ساعتِ غلط' });
  assert.equal(box.revertible, true);

  //  سندِ مالیِ ساعت‌کج گم نمی‌شود — فقط HLCِ مؤثرش زمانِ سرور است
  const sale = await push(owner.accessToken, C, [{ op_id: oid(), hlc: future, table: 'sales', row_id: 's-skew', kind: 'insert', fields: { totalMinor: 500 } }]);
  assert.equal(sale.body.results[0].status, 'applied');
  assert.ok(hlc.compare(sale.body.results[0].hlc, future) < 0);
});

test('آفلاینِ ۴۵ روزه (D-05، SYN-01): op قدیمی پذیرفته می‌شود و فقط به نوشتهٔ تازه‌تر می‌بازد', async () => {
  const old = hlc.format({ wall: Date.now() - 45 * 86_400_000, counter: 0, node: B });
  const r = await push(owner.accessToken, B, [
    { op_id: oid(), hlc: old, table: 'sales', row_id: 's-old', kind: 'insert', fields: { totalMinor: 700 } },
    { op_id: oid(), hlc: old, table: 'products', row_id: 'p-two', kind: 'update', fields: { name: 'خیلی قدیمی' } },
  ]);
  assert.deepEqual(r.body.results.map(x => x.status), ['applied', 'conflict']);
  assert.equal((await rowOf('sales', 's-old')).data.totalMinor, 700);
});

/* ------------------------------------------------------------- حذف */

test('حذف نرم با HLC: ویرایشِ قدیمی‌تر از حذف (دیررس) به صندوق، تازه‌ترش زنده می‌کند', async () => {
  await push(owner.accessToken, A, [{ op_id: oid(), hlc: H(A), table: 'suppliers', row_id: 'sup-1', kind: 'insert', fields: { name: 'تأمین' } }]);
  const staleEdit = H(B);
  const del = await push(owner.accessToken, A, [{ op_id: oid(), hlc: H(A), table: 'suppliers', row_id: 'sup-1', kind: 'delete' }]);
  assert.equal(del.body.results[0].status, 'applied');
  const late = await push(owner.accessToken, B, [{ op_id: oid(), hlc: staleEdit, table: 'suppliers', row_id: 'sup-1', kind: 'update', fields: { name: 'دیررس' } }]);
  assert.equal(late.body.results[0].status, 'conflict');
  assert.equal(late.body.results[0].current.deleted, true);
  let row = await rowOf('suppliers', 'sup-1');
  assert.ok(row.deleted_at, 'ویرایشِ قدیمی‌تر از حذف زنده نمی‌کند');
  //  ولی پنهان می‌نشیند (همان که اگر به‌موقع رسیده بود) تا نتیجه به ترتیبِ رسیدن بسته نباشد
  assert.equal(row.data.name, 'دیررس');
  assert.ok((await conflictsOf(owner.accessToken)).some(c => c.row_id === 'sup-1' && c.kind === 'stale_after_delete'));

  const revive = await push(owner.accessToken, B, [{ op_id: oid(), hlc: H(B), table: 'suppliers', row_id: 'sup-1', kind: 'update', fields: { phone: '0700' } }]);
  assert.equal(revive.body.results[0].status, 'applied');
  row = await rowOf('suppliers', 'sup-1');
  assert.equal(row.deleted_at, null);
  //  دستگاهِ دیگر ردیفِ زنده‌شده را کامل می‌گیرد
  const { ops } = await pullAll(owner.accessToken, 'v3-dev-watch');
  const last = ops.filter(o => o.row_id === 'sup-1').pop();
  assert.equal(last.effect.revived, true);
  assert.deepEqual(last.effect.set, { name: 'دیررس', phone: '0700' });
});

test('حذفِ قدیمی‌تر از یک ویرایشِ تازه ⇒ ردیف می‌ماند و حذف به صندوق می‌رود', async () => {
  const oldDelete = H(B);
  await push(owner.accessToken, A, [{ op_id: oid(), hlc: H(A), table: 'expenses', row_id: 'e-1', kind: 'insert', fields: { title: 'کرایه', amountMinor: 50000 } }]);
  const r = await push(owner.accessToken, B, [{ op_id: oid(), hlc: oldDelete, table: 'expenses', row_id: 'e-1', kind: 'delete' }]);
  assert.equal(r.body.results[0].status, 'conflict');
  assert.equal((await rowOf('expenses', 'e-1')).deleted_at, null);
  assert.ok((await conflictsOf(owner.accessToken)).some(c => c.row_id === 'e-1' && c.kind === 'stale_delete'));
});

/* ------------------------------------------------ سندِ مالی: فقط افزودنی */

test('فروش فقط افزودنی است: ویرایش و حذف رد، درجِ همسان بی‌اثر، درجِ ناجور به صندوق', async () => {
  const base = { table: 'sales', row_id: 's-ledger' };
  await push(owner.accessToken, A, [{ ...base, op_id: oid(), hlc: H(A), kind: 'insert', fields: { totalMinor: 9000, status: 'completed' } }]);
  const r = await push(owner.accessToken, A, [
    { ...base, op_id: oid(), hlc: H(A), kind: 'update', fields: { totalMinor: 1 } },
    { ...base, op_id: oid(), hlc: H(A), kind: 'delete' },
    { ...base, op_id: oid(), hlc: H(A), kind: 'insert', fields: { totalMinor: 9000, status: 'completed' } },
    { ...base, op_id: oid(), hlc: H(A), kind: 'insert', fields: { totalMinor: 1 } },
    { ...base, op_id: oid(), hlc: H(A), kind: 'cancel' },
  ]);
  assert.deepEqual(r.body.results.map(x => x.reason || x.status),
    ['append_only', 'append_only', 'applied', 'conflict', 'reason_required']);
  assert.equal((await rowOf('sales', 's-ledger')).data.totalMinor, 9000);
  assert.ok((await conflictsOf(owner.accessToken)).some(c => c.row_id === 's-ledger' && c.kind === 'append_only_edit' && !c.revertible));
});

test('لغو یک op جداست: با دلیل، یک بار؛ درجی که بعد از لغو برسد لغوشده می‌ماند', async () => {
  const c1 = await push(owner.accessToken, A, [{ op_id: oid(), hlc: H(A), table: 'sales', row_id: 's-ledger', kind: 'cancel', reason: 'اشتباه' }]);
  assert.equal(c1.body.results[0].status, 'applied');
  const row = await rowOf('sales', 's-ledger');
  assert.equal(row.data.status, 'cancelled');
  assert.equal(row.data.cancelReason, 'اشتباه');
  assert.equal(row.data.totalMinor, 9000);

  //  لغو و دلتا پیش از خودِ سند (از دستگاهِ دیگر) — سند که برسد هر دو می‌مانند
  await push(owner.accessToken, B, [
    { op_id: oid(), hlc: H(B), table: 'sales', row_id: 's-early', kind: 'cancel', reason: 'زود' },
    { op_id: oid(), hlc: H(B), table: 'sales', row_id: 's-early', kind: 'delta', fields: { paidAmountMinor: 300 } },
  ]);
  assert.equal(await one(`SELECT COUNT(*)::int AS n FROM sync_v3_rows WHERE account_id=$1 AND row_id='s-early' AND inserted`, [shopId]).then(r => r.n), 0);
  const snap0 = await h.api('GET', '/api/sync/v3/snapshot', { token: owner.accessToken });
  assert.ok(!(snap0.body.tables.sales || []).some(s => s.id === 's-early'), 'نیمه‌سند در عکس نمی‌آید');
  await push(owner.accessToken, A, [{ op_id: oid(), hlc: H(A), table: 'sales', row_id: 's-early', kind: 'insert', fields: { totalMinor: 1000, paidAmountMinor: 200, status: 'completed' } }]);
  const early = await rowOf('sales', 's-early');
  assert.equal(early.data.status, 'cancelled');
  assert.equal(early.data.paidAmountMinor, 500);
});

test('شمارنده فقط دلتا؛ نامِ قدیم، عددِ اعشاری و مانده در v3 رد می‌شوند', async () => {
  const r = await push(owner.accessToken, A, [
    { op_id: oid(), hlc: H(A), table: 'sales', row_id: 's-dup', kind: 'delta', fields: { totalMinor: 5 } },
    { op_id: oid(), hlc: H(A), table: 'products', row_id: 'p-bad', kind: 'insert', fields: { salePrice: 15.5 } },
    { op_id: oid(), hlc: H(A), table: 'products', row_id: 'p-bad', kind: 'insert', fields: { salePriceMinor: 15.5 } },
    { op_id: oid(), hlc: H(A), table: 'products', row_id: 'p-bad', kind: 'insert', fields: { stock: 4 } },
    { op_id: oid(), hlc: 'not-a-clock', table: 'products', row_id: 'p-bad', kind: 'insert', fields: { name: 'x' } },
    { op_id: oid(), hlc: H(A), table: 'users', row_id: 'x', kind: 'insert', fields: { role: 'admin' } },
    { op_id: 'srv.count.cnt-9', hlc: H(A), table: 'stockMovements', row_id: 'm-9', kind: 'insert', fields: { productId: 'p', qtyMilli: 1 } },
    { op_id: oid(), hlc: H(A), table: 'stockMovements', row_id: 'srv.cadj.cnt-9', kind: 'insert', fields: { productId: 'p', qtyMilli: 1 } },
  ]);
  assert.deepEqual(r.body.results.map(x => x.reason),
    ['not_a_counter', 'legacy_field', 'not_integer', 'derived_field', 'bad_hlc', 'unknown_table', 'reserved_id', 'reserved_id']);
  assert.equal(await rowOf('products', 'p-bad'), null);
});

/* -------------------------------------------- ثبتِ اتمیک (SYN-02) */

test('tx: فروش + قلم + حرکت + پرداخت همه یا هیچ', async () => {
  const tx = 'tx-sale-1';
  const good = [
    { op_id: oid(), tx, hlc: H(A), table: 'sales', row_id: 's-tx', kind: 'insert', fields: { totalMinor: 2000, status: 'completed' } },
    { op_id: oid(), tx, hlc: H(A), table: 'saleItems', row_id: 'si-tx', kind: 'insert', fields: { saleId: 's-tx', productId: 'p-tx', quantityMilli: 2000 } },
    { op_id: oid(), tx, hlc: H(A), table: 'stockMovements', row_id: 'm-tx', kind: 'insert', fields: { productId: 'p-tx', qtyMilli: -2000, type: 'sale' } },
    { op_id: oid(), tx, hlc: H(A), table: 'transactions', row_id: 't-tx', kind: 'insert', fields: { amountMinor: 2000 } },
  ];
  const broken = good.map((o, i) => ({ ...o, op_id: oid(), row_id: o.row_id + '-x', tx: 'tx-sale-2',
    ...(i === 2 ? { fields: { productId: 'p-tx', qty: -2 } } : {}) }));
  const r = await push(owner.accessToken, A, [...broken, ...good]);
  assert.deepEqual(r.body.results.slice(0, 4).map(x => x.reason),
    ['tx_rejected', 'tx_rejected', 'legacy_field', 'tx_rejected']);
  assert.deepEqual(r.body.results.slice(4).map(x => x.status), ['applied', 'applied', 'applied', 'applied']);
  for (const id of ['s-tx-x', 'si-tx-x', 'm-tx-x', 't-tx-x']) {
    assert.equal(await one(`SELECT 1 FROM sync_v3_rows WHERE account_id=$1 AND row_id=$2`, [shopId, id]), null);
  }
});

test('tx: شاگردِ بی اجازهٔ تخفیف ⇒ کلِ tx برمی‌گردد (هیچ ردیفی نمی‌ماند)', async () => {
  const tx = 'tx-staff-1';
  const r = await push(staff.accessToken, 'v3-dev-staff', [
    { op_id: oid(), tx, hlc: H('s'), table: 'stockMovements', row_id: 'm-staff', kind: 'insert', fields: { productId: 'p-st', qtyMilli: -1000 } },
    { op_id: oid(), tx, hlc: H('s'), table: 'sales', row_id: 's-staff', kind: 'insert', fields: { totalMinor: 1000, discountMinor: 100 } },
  ]);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(r.body.results.map(x => x.reason), ['tx_rejected', 'discount_not_allowed']);
  assert.equal(await one(`SELECT 1 FROM sync_v3_rows WHERE account_id=$1 AND row_id='m-staff'`, [shopId]), null);
  assert.equal(await one(`SELECT 1 FROM sync_v3_ops WHERE account_id=$1 AND row_id='m-staff'`, [shopId]), null);
});

/* --------------------------------------- انبار: حرکت، منفی، شمارش */

test('موجودی منفی ⇒ یک «وظیفهٔ بررسی»، نه رد شدنِ فروش', async () => {
  const r = await push(owner.accessToken, A, [
    { op_id: oid(), hlc: H(A), table: 'stockMovements', row_id: 'm-neg-1', kind: 'insert', fields: { productId: 'p-neg', qtyMilli: 1000 } },
    { op_id: oid(), hlc: H(A), table: 'stockMovements', row_id: 'm-neg-2', kind: 'insert', fields: { productId: 'p-neg', qtyMilli: -3000 } },
    { op_id: oid(), hlc: H(A), table: 'stockMovements', row_id: 'm-neg-3', kind: 'insert', fields: { productId: 'p-neg', qtyMilli: -1000 } },
  ]);
  assert.deepEqual(r.body.results.map(x => x.status), ['applied', 'applied', 'applied']);
  const tasks = (await conflictsOf(owner.accessToken)).filter(c => c.kind === 'negative_stock' && c.row_id === 'p-neg');
  assert.equal(tasks.length, 1, 'برای هر کالا یک وظیفهٔ باز');
  assert.equal(tasks[0].detail.stockMilli, -3000);
  assert.equal(tasks[0].task, true);
});

test('شمارشِ انبار با HLC: تعدیل از جمعِ حرکت‌های ≤ T؛ حرکتِ دیررس جبران می‌شود؛ کالای شمرده‌نشده دست نمی‌خورد', async () => {
  const stock = async (pid) => Number((await one(
    `SELECT COALESCE(SUM((data->>'qtyMilli')::bigint),0) AS s FROM sync_v3_rows
      WHERE account_id=$1 AND table_name='stockMovements' AND data->>'productId'=$2`, [shopId, pid])).s);
  const before = H(B);                 //  فروشِ آفلاینِ B، پیش از شمارش
  await push(owner.accessToken, A, [
    { op_id: oid(), hlc: H(A), table: 'stockMovements', row_id: 'm-c-1', kind: 'insert', fields: { productId: 'p-cnt', qtyMilli: 10_000 } },
    { op_id: oid(), hlc: H(A), table: 'stockMovements', row_id: 'm-u-1', kind: 'insert', fields: { productId: 'p-uncounted', qtyMilli: 5_000 } },
  ]);
  const T = H(A);
  const after = H(A);
  const cnt = await push(owner.accessToken, A, [
    { op_id: oid(), hlc: T, table: 'stockCounts', row_id: 'cnt-1', kind: 'insert', fields: { productId: 'p-cnt', countedQtyMilli: 7_000 } },
    { op_id: oid(), hlc: after, table: 'stockMovements', row_id: 'm-c-after', kind: 'insert', fields: { productId: 'p-cnt', qtyMilli: -1_000 } },
  ]);
  assert.deepEqual(cnt.body.results.map(x => x.status), ['applied', 'applied']);
  assert.equal(await stock('p-cnt'), 6_000, 'شمرده‌شده ۷، یک فروشِ بعد از شمارش');
  assert.equal(Number((await rowOf('stockMovements', 'srv.cadj.cnt-1')).data.qtyMilli), -3_000);

  //  فروشِ آفلاینِ B با HLCِ پیش از شمارش، دیر می‌رسد
  await push(owner.accessToken, B, [{ op_id: oid(), hlc: before, table: 'stockMovements', row_id: 'm-c-late', kind: 'insert', fields: { productId: 'p-cnt', qtyMilli: -2_000 } }]);
  assert.equal(await stock('p-cnt'), 6_000, 'شمارش آن را ندیده بود ⇒ جبران شد');
  const comp = await rowOf('stockMovements', 'srv.late.m-c-late');
  assert.equal(Number(comp.data.qtyMilli), 2_000);
  assert.equal(comp.data.type, 'count_adjust_late');

  //  فروشِ دیررسِ بعد از شمارش جبران نمی‌شود
  await push(owner.accessToken, B, [{ op_id: oid(), hlc: H(B), table: 'stockMovements', row_id: 'm-c-late2', kind: 'insert', fields: { productId: 'p-cnt', qtyMilli: -500 } }]);
  assert.equal(await stock('p-cnt'), 5_500);
  assert.equal(await rowOf('stockMovements', 'srv.late.m-c-late2'), null);

  assert.equal(await stock('p-uncounted'), 5_000);

  //  حرکت‌های سرور به دستگاه‌ها هم می‌رسند
  const { ops } = await pullAll(owner.accessToken, 'v3-dev-watch2');
  assert.ok(ops.some(o => o.row_id === 'srv.cadj.cnt-1' && o.device_id === 'server' && o.origin === 'server'));
});

/* ---------------------------------------------------- تکراری‌ها */

test('بارکدِ تکراری بین دو کالای آفلاین: هر دو می‌مانند + وظیفهٔ «ادغام یا اصلاح»', async () => {
  const r1 = await push(owner.accessToken, A, [{ op_id: oid(), hlc: H(A), table: 'products', row_id: 'p-bc-a', kind: 'insert', fields: { name: 'آب', barcodes: ['6260001'] } }]);
  const r2 = await push(owner.accessToken, B, [{ op_id: oid(), hlc: H(B), table: 'products', row_id: 'p-bc-b', kind: 'insert', fields: { name: 'آبِ معدنی', barcodes: ['6260001', '999'] } }]);
  assert.equal(r1.body.results[0].status, 'applied');
  assert.equal(r2.body.results[0].status, 'applied');
  assert.ok(await rowOf('products', 'p-bc-a'));
  assert.ok(await rowOf('products', 'p-bc-b'));
  const t = (await conflictsOf(owner.accessToken)).filter(c => c.kind === 'duplicate_barcode');
  assert.equal(t.length, 1);
  assert.deepEqual(t[0].detail, { barcode: '6260001', rows: ['p-bc-a', 'p-bc-b'] });
});

test('مشتریِ تکراری (یک شماره، حتی با رقمِ فارسی): هر دو می‌مانند + پیشنهادِ ادغام', async () => {
  await push(owner.accessToken, A, [{ op_id: oid(), hlc: H(A), table: 'debtors', row_id: 'c-ph-a', kind: 'insert', fields: { name: 'احمد', phone: '0799 123 456' } }]);
  await push(owner.accessToken, B, [{ op_id: oid(), hlc: H(B), table: 'debtors', row_id: 'c-ph-b', kind: 'insert', fields: { name: 'احمد جان', phone: '+93۷۹۹۱۲۳۴۵۶' } }]);
  const t = (await conflictsOf(owner.accessToken)).filter(c => c.kind === 'duplicate_customer');
  assert.equal(t.length, 1);
  assert.deepEqual(t[0].detail.rows, ['c-ph-a', 'c-ph-b']);
});

/* ------------------------------------------------ مبدلِ v2 (SYN-11) */

test('صفِ v2ِ برنامه از راهِ v3: مبلغ‌ها Long، $inc دلتا، لغو جدا؛ تکرار بی‌اثر', async () => {
  const ts = Date.now() - 60_000;
  const ops = [
    { op_id: oid(), ts, table: 'sales', row_id: 's-v2', type: 'insert', fields: { total: 120.5, paidAmount: 100, remaining: 20.5, status: 'completed', syncStatus: 'pending' } },
    { op_id: oid(), ts: ts + 1, table: 'sales', row_id: 's-v2', type: 'update', fields: { paidAmount: { $inc: 20.5 }, remaining: { $inc: -20.5 } } },
    { op_id: oid(), ts: ts + 2, table: 'productUnits', row_id: 'u-v2', type: 'insert', fields: { name: 'کارتن' } },
  ];
  const r = await push(owner.accessToken, A, ops, { format: 'v2', schema_version: 3 });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(r.body.results.map(x => x.status), ['applied', 'applied', 'applied']);
  assert.deepEqual(r.body.results[0].dropped, ['syncStatus']);
  const row = await rowOf('sales', 's-v2');
  assert.equal(row.data.totalMinor, 12050);
  assert.equal(row.data.paidAmountMinor, 12050);
  assert.equal(row.data.remainingMinor, 0);
  assert.equal('syncStatus' in row.data, false);
  assert.equal((await rowOf('productUnits', 'u-v2')).data.name, 'کارتن');

  const again = await push(owner.accessToken, A, ops, { format: 'v2', schema_version: 3 });
  assert.deepEqual(again.body.results.map(x => x.status), ['duplicate', 'duplicate', 'duplicate']);
  assert.equal((await rowOf('sales', 's-v2')).data.paidAmountMinor, 12050);
});

test('426: برنامهٔ جلوتر از سرور ⇒ هیچ چیزی اعمال نمی‌شود (داده روی برنامه می‌ماند)', async () => {
  const before = await one(`SELECT COUNT(*)::int AS n FROM sync_v3_ops WHERE account_id=$1`, [shopId]);
  const op = { op_id: oid(), hlc: H(A), table: 'products', row_id: 'p-426', kind: 'insert', fields: { name: 'x' } };
  const r1 = await push(owner.accessToken, A, [op], { protocol: 4 });
  assert.equal(r1.status, 426);
  assert.equal(r1.body.error, 'UPGRADE_REQUIRED');
  const r2 = await push(owner.accessToken, A, [{ op_id: oid(), ts: Date.now(), table: 'products', row_id: 'p-426', type: 'insert', fields: { name: 'x' } }],
    { format: 'v2', schema_version: 99 });
  assert.equal(r2.status, 426);
  const after = await one(`SELECT COUNT(*)::int AS n FROM sync_v3_ops WHERE account_id=$1`, [shopId]);
  assert.equal(after.n, before.n);
});

/* -------------------------------------------------- پلِ v1 ⇒ v3 */

test('پلِ v1: opهای دستگاهِ هنوز-v1 به v3 می‌رسند، v1 دست نمی‌خورد، و تکرار بی‌اثر است', async () => {
  const v1op = (o) => ({ op_id: oid(), ts: Date.now() - 5000, ...o });
  const shared = v1op({ table: 'products', row_id: 'p-bridge', type: 'insert', fields: { name: 'از v1', salePrice: 25.75 } });
  const v1 = await h.api('POST', '/api/sync/v1/push', {
    token: owner.accessToken, headers: { 'X-App': 'shop' },
    body: { device_id: 'v1-old-phone', schema_version: 3, ops: [
      shared,
      v1op({ table: 'sales', row_id: 's-bridge', type: 'insert', fields: { total: 50, paidAmount: 0 } }),
      v1op({ table: 'sales', row_id: 's-bridge', type: 'update', fields: { paidAmount: { $inc: 50 } } }),
    ] },
  });
  assert.equal(v1.status, 200, JSON.stringify(v1.body));
  const v1Rows = await one(`SELECT COUNT(*)::int AS n FROM sync_rows WHERE account_id=$1`, [shopId]);

  //  همان op از راهِ v3 هم رسیده بود (برنامه‌ای که وسطِ کار به‌روز شد)
  const dup = await push(owner.accessToken, 'v1-old-phone', [shared], { format: 'v2', schema_version: 3 });
  assert.equal(dup.body.results[0].status, 'applied');

  const { ops } = await pullAll(owner.accessToken, 'v3-dev-new');
  const bridged = ops.filter(o => ['p-bridge', 's-bridge'].includes(o.row_id));
  assert.ok(bridged.some(o => o.origin === 'v1' && o.row_id === 's-bridge'));
  assert.equal(bridged.filter(o => o.row_id === 'p-bridge').length, 1, 'opِ مشترک یک بار');
  assert.equal((await rowOf('products', 'p-bridge')).data.salePriceMinor, 2575);
  assert.equal((await rowOf('sales', 's-bridge')).data.paidAmountMinor, 5000);

  //  دوباره ⇒ چیزی دو بار نمی‌نشیند
  await pullAll(owner.accessToken, 'v3-dev-new');
  assert.equal((await rowOf('sales', 's-bridge')).data.paidAmountMinor, 5000);
  const st = await h.api('GET', '/api/sync/v3/status?device_id=v3-dev-new', { token: owner.accessToken });
  assert.equal(st.body.bridge.caught_up, true);
  assert.equal((await one(`SELECT COUNT(*)::int AS n FROM sync_rows WHERE account_id=$1`, [shopId])).n, v1Rows.n,
    'v3 در جدول‌های v1 نمی‌نویسد');
});

/* ------------------------------------ انتقال: سقفِ بایت، ادامه، gzip (SYN-04) */

test('pull با سقفِ ۶۴KB، ادامه از cursor، هر op دقیقاً یک بار و به ترتیب', async () => {
  const filler = 'ك'.repeat(400);
  for (let b = 0; b < 3; b++) {
    const ops = [];
    for (let i = 0; i < 100; i++) {
      ops.push({ op_id: oid(), hlc: H(A), table: 'auditLog', row_id: `log-${b}-${i}`, kind: 'insert', fields: { notes: filler } });
    }
    const r = await push(owner.accessToken, A, ops);
    assert.equal(r.status, 200, JSON.stringify(r.body).slice(0, 300));
  }
  const first = await pull(owner.accessToken, 'v3-dev-slow', 0);
  assert.equal(first.body.has_more, true);
  assert.ok(first.body.bytes <= 64 * 1024, `${first.body.bytes} بایت`);
  const { ops } = await pullAll(owner.accessToken, 'v3-dev-slow');
  const logs = ops.filter(o => o.table === 'auditLog');
  assert.equal(logs.length, 300);
  assert.equal(new Set(logs.map(o => o.op_id)).size, 300);
  const seqs = ops.map(o => o.seq);
  assert.deepEqual(seqs, [...seqs].sort((x, y) => x - y));
  assert.ok(ops.every(o => o.device_id !== 'v3-dev-slow'));

  const big = await pull(owner.accessToken, 'v3-dev-fast', 0, '&max_bytes=262144');
  assert.ok(big.body.bytes > 64 * 1024 && big.body.bytes <= 256 * 1024);
  assert.equal(big.headers.get('content-encoding'), 'gzip');
});

test('بدنهٔ فشرده (gzip) پذیرفته می‌شود', async () => {
  const body = { device_id: A, protocol: 3, ops: [{ op_id: oid(), hlc: H(A), table: 'productCategories', row_id: 'cat-gz', kind: 'insert', fields: { name: 'لبنیات' } }] };
  const r = await h.raw('POST', '/api/sync/v3/push', zlib.gzipSync(Buffer.from(JSON.stringify(body))), {
    token: owner.accessToken, headers: { 'Content-Type': 'application/json', 'Content-Encoding': 'gzip' },
  });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.results[0].status, 'applied');
  assert.equal((await rowOf('productCategories', 'cat-gz')).data.name, 'لبنیات');
});

/* --------------------------------------------- وضعیت (SYN-08، SYN-10) */

test('وضعیت: منتظر، آخرین موفق، خطای آخر؛ دستگاهِ همگام‌نشده دیده می‌شود', async () => {
  await push(owner.accessToken, 'v3-dev-st', [{ op_id: oid(), hlc: H('st'), table: 'productUnits', row_id: 'u-st', kind: 'insert', fields: { name: 'بسته' } }],
    { queued: 4, last_error: 'timeout', clock_offset_ms: -1200 });
  let st = await h.api('GET', '/api/sync/v3/status?device_id=v3-dev-st', { token: owner.accessToken });
  assert.equal(st.status, 200);
  const d = st.body.device;
  assert.equal(d.queued, 4);
  assert.equal(d.unsynced, true);
  assert.equal(d.last_error, 'timeout');
  assert.ok(d.last_ok_at > 0 && d.last_push_at > 0);
  assert.equal(d.clock_offset_ms, -1200);
  assert.ok(d.pending > 0, 'opهای دیگران هنوز pull نشده‌اند');
  assert.ok(st.body.conflicts_open > 0);
  assert.ok(st.body.devices.some(x => x.device_id === 'v3-dev-st' && x.unsynced));

  await pullAll(owner.accessToken, 'v3-dev-st');
  await h.api('POST', '/api/sync/v3/report', { token: owner.accessToken, body: { device_id: 'v3-dev-st', queued: 0, last_error: '' } });
  st = await h.api('GET', '/api/sync/v3/status?device_id=v3-dev-st', { token: owner.accessToken });
  assert.equal(st.body.device.pending, 0);
  assert.equal(st.body.device.unsynced, false);
  assert.equal(st.body.device.last_error, '');

  //  شاگرد فقط دستگاهِ خودش را می‌بیند
  const s = await h.api('GET', '/api/sync/v3/status?device_id=v3-dev-staff', { token: staff.accessToken });
  assert.ok(s.body.devices.every(x => x.device_id === 'v3-dev-staff'));
});

/* -------------------------------------------- صندوقِ تعارض (SYN-07) */

test('صندوقِ تعارض: شاگرد نمی‌بندد؛ مدیر نگه می‌دارد یا برمی‌گرداند؛ دوباره بستن ۴۰۹', async () => {
  await push(owner.accessToken, A, [{ op_id: oid(), hlc: H(A), table: 'products', row_id: 'p-rv', kind: 'insert', fields: { name: 'اصلی', salePriceMinor: 1000 } }]);
  const loserHlc = H(B);
  await push(owner.accessToken, A, [{ op_id: oid(), hlc: H(A), table: 'products', row_id: 'p-rv', kind: 'update', fields: { salePriceMinor: 2000 } }]);
  await push(owner.accessToken, B, [{ op_id: oid(), hlc: loserHlc, table: 'products', row_id: 'p-rv', kind: 'update', fields: { salePriceMinor: 1500 } }]);
  const box = (await conflictsOf(owner.accessToken)).find(c => c.row_id === 'p-rv');
  assert.equal(box.loser_value, 1500);

  const denied = await h.api('POST', `/api/sync/v3/conflicts/${box.id}/resolve`, { token: staff.accessToken, body: { action: 'revert' } });
  assert.equal(denied.status, 403);

  const rv = await h.api('POST', `/api/sync/v3/conflicts/${box.id}/resolve`, { token: owner.accessToken, body: { action: 'revert' } });
  assert.equal(rv.status, 200, JSON.stringify(rv.body));
  assert.equal(rv.body.conflict.status, 'reverted');
  assert.equal((await rowOf('products', 'p-rv')).data.salePriceMinor, 1500);
  //  بقیهٔ دستگاه‌ها مقدارِ برگشته را می‌گیرند
  const { ops } = await pullAll(owner.accessToken, A);
  assert.ok(ops.some(o => o.row_id === 'p-rv' && o.origin === 'server' && o.effect.set.salePriceMinor === 1500));

  const again = await h.api('POST', `/api/sync/v3/conflicts/${box.id}/resolve`, { token: owner.accessToken, body: { action: 'keep' } });
  assert.equal(again.status, 409);

  const appendOnly = (await conflictsOf(owner.accessToken)).find(c => c.kind === 'append_only_edit');
  const nr = await h.api('POST', `/api/sync/v3/conflicts/${appendOnly.id}/resolve`, { token: owner.accessToken, body: { action: 'revert' } });
  assert.equal(nr.status, 400);
  const keep = await h.api('POST', `/api/sync/v3/conflicts/${appendOnly.id}/resolve`, { token: owner.accessToken, body: { action: 'keep' } });
  assert.equal(keep.body.conflict.status, 'kept');

  const skew = (await conflictsOf(owner.accessToken)).find(c => c.kind === 'clock_skew' && c.row_id === 'p-skew');
  const sk = await h.api('POST', `/api/sync/v3/conflicts/${skew.id}/resolve`, { token: owner.accessToken, body: { action: 'revert' } });
  assert.equal(sk.status, 200);
  assert.equal((await rowOf('products', 'p-skew')).data.name, 'ساعتِ غلط');

  const all = await conflictsOf(owner.accessToken, 'all');
  assert.ok(all.some(c => c.status === 'reverted') && all.some(c => c.status === 'kept'));
});

test('حذف در v3 برای شاگرد: فقط ردیفی که خودش ساخته', async () => {
  const d = 'v3-dev-staff';
  await push(owner.accessToken, A, [{ op_id: oid(), hlc: H(A), table: 'debtors', row_id: 'c-own-a', kind: 'insert', fields: { name: 'مالِ A' } }]);
  await push(staff.accessToken, d, [{ op_id: oid(), hlc: H(d), table: 'debtors', row_id: 'c-own-s', kind: 'insert', fields: { name: 'مالِ شاگرد' } }]);
  const r = await push(staff.accessToken, d, [
    { op_id: oid(), hlc: H(d), table: 'debtors', row_id: 'c-own-a', kind: 'delete' },
    { op_id: oid(), hlc: H(d), table: 'debtors', row_id: 'c-own-s', kind: 'delete' },
  ]);
  assert.deepEqual(r.body.results.map(x => x.reason || x.status), ['delete_not_allowed', 'applied']);
});

test('snapshot: ردیف‌های زنده + cursor، فشرده؛ حذف‌شده نمی‌آید', async () => {
  const r = await h.api('GET', '/api/sync/v3/snapshot', { token: owner.accessToken });
  assert.equal(r.status, 200);
  assert.equal(r.headers.get('content-encoding'), 'gzip');
  assert.ok(r.body.cursor > 0);
  assert.equal(r.body.complete, true);
  assert.ok(r.body.tables.products.some(p => p.id === 'p-two'));
  assert.ok(!(r.body.tables.debtors || []).some(p => p.id === 'c-own-s'));
  //  دستگاهِ تازه از همان cursor ادامه می‌دهد و چیزی دو بار نمی‌گیرد
  const next = await pull(owner.accessToken, 'v3-dev-fresh', r.body.cursor);
  assert.equal(next.body.ops.length, 0);
});

test('v1 و پمپ دست‌نخورده: v3 در oplogِ v1 نمی‌نویسد', async () => {
  const before = await one(`SELECT COUNT(*)::int AS n FROM oplog`);
  await push(owner.accessToken, A, [{ op_id: oid(), hlc: H(A), table: 'products', row_id: 'p-v1-safe', kind: 'insert', fields: { name: 'x' } }]);
  const after = await one(`SELECT COUNT(*)::int AS n FROM oplog`);
  assert.equal(after.n, before.n);
});

test('A00: null ِ ساختِ پیش از A00 برای فیلدی که نمی‌شناسد کنار می‌رود؛ ساختی که می‌شناسد و وب پاکش می‌کنند', async () => {
  const as = (version, ops) => h.api('POST', '/api/sync/v3/push', {
    token: owner.accessToken, headers: { 'X-App-Version': version }, body: { device_id: A, protocol: 3, ops },
  });
  const upd = (fields) => ({ op_id: oid(), hlc: H(A), table: 'products', row_id: 'p-a00', kind: 'update', fields });
  const ins = await as('3.2.9999', [{ op_id: oid(), hlc: H(A), table: 'products', row_id: 'p-a00', kind: 'insert',
    fields: { name: 'x', salePriceMinor: 100, subcategory: 's', futureX: 'f' } }]);
  assert.equal(ins.body.results[0].status, 'applied');

  //  ساختِ 3.2.290: نه subcategory (از 3.2.300) را می‌شناسد نه futureX
  const old = await as('3.2.290', [upd({ subcategory: null, futureX: null, name: 'y' }), upd({ futureX: null })]);
  assert.equal(old.status, 200, JSON.stringify(old.body));
  assert.deepEqual(old.body.results.map((r) => r.status), ['applied', 'applied']);
  assert.deepEqual(old.body.results[0].ignored, ['subcategory', 'futureX']);
  assert.equal(old.body.results[1].reason, 'stale_null_ignored');
  let row = await rowOf('products', 'p-a00');
  assert.deepEqual([row.data.name, row.data.subcategory, row.data.futureX], ['y', 's', 'f']);

  await as('3.2.300', [upd({ subcategory: null })]);
  await as('3.2', [upd({ futureX: null })]);
  row = await rowOf('products', 'p-a00');
  assert.deepEqual([row.data.subcategory, row.data.futureX], [null, null]);

  //  نامِ v3 با پسوندِ واحد: `salePriceMinor` همان `salePrice` است که همهٔ ساخت‌ها می‌شناسند؛
  //  `futureYMinor` ناشناخته می‌ماند
  await as('3.2.9999', [upd({ futureYMinor: 5 })]);
  const minor = await as('3.2.290', [upd({ salePriceMinor: null, futureYMinor: null })]);
  assert.deepEqual(minor.body.results[0].ignored, ['futureYMinor']);
  row = await rowOf('products', 'p-a00');
  assert.deepEqual([row.data.salePriceMinor, row.data.futureYMinor], [null, 5]);

  //  نشانِ صریح: ساختِ A00 (`caps`) و وب (`client`) از تور می‌گذرند، هر نسخه‌ای بگویند
  const marked = (body, ops) => h.api('POST', '/api/sync/v3/push', {
    token: owner.accessToken, headers: { 'X-App-Version': '3.2.1' }, body: { device_id: A, protocol: 3, ops, ...body },
  });
  await marked({ caps: ['a00'] }, [upd({ futureYMinor: null })]);
  row = await rowOf('products', 'p-a00');
  assert.equal(row.data.futureYMinor, null);
});
