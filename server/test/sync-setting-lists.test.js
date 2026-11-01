'use strict';
/**
 * F0-04 — دسته‌های کالا، واحدها و دسته‌های مصارف هم همگام می‌شوند.
 *
 * تا امروز این سه فهرست فقط روی همان دستگاه می‌ماندند: Sync v1 نامِ جدولِ
 * مجاز را از `lib/sync.js → TABLES` می‌گیرد و این سه آن‌جا نبودند؛ سمتِ
 * برنامه هم (`license/sync-core.js`، `LedgerDiff.kt`) عمداً کنارشان گذاشته
 * بود. دسته‌ای که روی گوشی ساخته می‌شد، روی کامپیوتر و وب دیده نمی‌شد.
 *
 * هر نام یک ردیف است: `id` = ‎sha256(نام)‎ (۳۲ نویسهٔ اول)، `fields = {name}`.
 * دو دستگاه که یک نام را بسازند یک ردیف می‌سازند، نه دو تا.
 *
 * ⚠️ کدِ زیرِ آزمون همان `license/sync-core.js`ِ مرورگر است، با سرورِ واقعی.
 */
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';

const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const path = require('node:path');

const h = require('./helpers');
const { one } = require('../src/db');
const syncV1 = require('../src/lib/sync-v1');
const { TABLES } = require('../src/lib/sync');

const core = require(path.join(__dirname, '..', '..', 'license', 'sync-core.js'));

const LISTS = ['productCategories', 'productUnits', 'expenseCategories'];
const idOf = (name) => crypto.createHash('sha256').update(name, 'utf8').digest('hex').slice(0, 32);

/** یک دستگاه: دفتر، سایه، صف و cursor — همان چهار چیزی که موتورِ وب دارد. */
function makeDevice(deviceId, token, ledger) {
  const box = new Map();
  const io = {
    read: (k, d) => (box.has(k) ? JSON.parse(box.get(k)) : d),
    write: (k, v) => { box.set(k, JSON.stringify(v)); return true; },
  };
  return {
    id: deviceId, token, cursor: 0,
    ledger: ledger || { productCategories: [], productUnits: [], expenseCategories: [] },
    shadow: null,
    queue: new core.OpQueue(io, 'oplog'),

    write(mutate) {
      if (this.shadow === null) this.shadow = core.shadowOf(this.ledger);
      mutate(this.ledger);
      const { ops } = core.diff(this.shadow, this.ledger);
      this.queue.push(ops);
      this.shadow = core.shadowOf(this.ledger);
      return ops;
    },
    async push() {
      const batch = this.queue.batch();
      const r = await h.api('POST', '/api/sync/v1/push', {
        token: this.token,
        body: { device_id: this.id, schema_version: core.wireSchema(0), ops: batch },
        headers: { 'X-App': 'shop', 'X-Device': this.id },
      });
      assert.equal(r.status, 200, JSON.stringify(r.body));
      const ok = r.body.results.filter(x => x.status === 'applied' || x.status === 'duplicate').map(x => x.op_id);
      this.queue.ack(ok);
      return r.body;
    },
    async pull() {
      const r = await h.api('GET', `/api/sync/v1/pull?device_id=${this.id}&since=${this.cursor}`,
        { token: this.token, headers: { 'X-App': 'shop' } });
      assert.equal(r.status, 200, JSON.stringify(r.body));
      const out = core.applyRemote(this.ledger, r.body.ops || []);
      this.ledger = out.data;
      this.shadow = core.shadowOf(this.ledger);
      this.cursor = Number(r.body.cursor || this.cursor);
      return out.applied;
    },
  };
}

let owner; let shopId;

/** هر سناریو دکانِ خودش را دارد تا ردیف‌های یکی در دیگری دیده نشود. */
let shops = 0;
async function freshShop() {
  shops++;
  const u = await h.newUser(`صاحبِ فهرست‌ها ${shops}`, 'shop');
  const s = await h.api('POST', '/api/shop', { token: u.accessToken, body: { name: `دکانِ فهرست‌ها ${shops}` } });
  const id = s.body.shop?.id || s.body.id;
  assert.ok(id, JSON.stringify(s.body));
  return { owner: u, shopId: id };
}

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
});
test.beforeEach(async () => { ({ owner, shopId } = await freshShop()); });
test.after(async () => { await h.stop(); });

/* ------------------------------------------------------------ واحد */

test('سرور: opِ productCategories پذیرفته می‌شود (پیش‌تر «جدولِ ناشناخته» و دور ریخته)', async () => {
  const fields = { name: 'خشکبار' };
  const op = {
    op_id: '01JF0040000000000000000001', ts: Date.now(), table: 'productCategories',
    row_id: idOf('خشکبار'), type: 'insert', fields,
  };
  const r = await h.api('POST', '/api/sync/v1/push', {
    token: owner.accessToken,
    headers: { 'X-App': 'shop', 'X-Device': 'raw-1' },
    body: { device_id: 'raw-1', schema_version: 2, ops: [op] },
  });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.results[0].status, 'applied', JSON.stringify(r.body.results));
});

test('سرور: سه فهرست در TABLES هستند و هر کدام جدولِ خودش را دارد', async () => {
  assert.equal(TABLES.productCategories, 'product_categories');
  assert.equal(TABLES.productUnits, 'product_units');
  assert.equal(TABLES.expenseCategories, 'expense_categories');
  for (const t of ['product_categories', 'product_units', 'expense_categories']) {
    const r = await one('SELECT COUNT(*)::int AS n FROM ' + t);
    assert.equal(r.n, 0);
  }
});

test('برنامه: ساختنِ دسته یک opِ insert با شناسهٔ sha256ِ نام می‌سازد؛ حذفش یک delete', () => {
  for (const key of LISTS) assert.ok(core.LIST_COLLECTIONS.includes(key), key);
  assert.ok(core.SCHEMA_VERSION >= 3, 'جدولِ تازه = یک پله بالاتر، این‌جا و روی سرور');
  const mig = require('../src/lib/sync-v1-migrations');
  assert.equal(core.SCHEMA_VERSION, mig.currentVersion('shop', { safeEntries: true }), 'وب و سرور یک پله');
  assert.equal(core.BASE_SCHEMA, mig.currentVersion('shop'), 'پایهٔ وب = پلهٔ سرورِ بی پرچم');
  assert.deepEqual([0, 2, 3, 4, 9].map(core.wireSchema), [3, 3, 3, 4, 4], 'پلهٔ همان سرور، نه بیشتر');
  assert.equal(core.listRowId('نوشیدنی'), idOf('نوشیدنی'));

  const shadow = core.shadowOf({ productCategories: ['لبنیات'], productUnits: [], expenseCategories: [] });
  const add = core.diff(shadow, { productCategories: ['لبنیات', ' نوشیدنی '], productUnits: [], expenseCategories: [] });
  assert.equal(add.ops.length, 1);
  assert.equal(add.ops[0].table, 'productCategories');
  assert.equal(add.ops[0].type, 'insert');
  assert.equal(add.ops[0].row_id, idOf('نوشیدنی'));
  assert.deepEqual(add.ops[0].fields, { name: 'نوشیدنی' }, 'نام بی فاصلهٔ اضافه می‌رود');

  const del = core.diff(shadow, { productCategories: [], productUnits: [], expenseCategories: [] });
  assert.deepEqual(del.ops.map(o => [o.table, o.type, o.row_id]), [['productCategories', 'delete', idOf('لبنیات')]]);
});

test('برنامه: سایهٔ پیش از پلهٔ ۳ (بی این کلیدها) ⇒ همهٔ نام‌ها یک بار درج می‌شوند، نه حذف', () => {
  const oldShadow = { products: [], sales: [] };
  const out = core.diff(oldShadow, { products: [], sales: [], productUnits: ['عدد', 'کیلوگرم'] });
  assert.deepEqual(out.ops.map(o => [o.table, o.type]), [['productUnits', 'insert'], ['productUnits', 'insert']]);
  //  دفتری که کلید را اصلاً ندارد چیزی را پاک نمی‌کند
  const none = core.diff(core.shadowOf({ productUnits: ['عدد'] }), { products: [] });
  assert.equal(none.ops.length, 0);
});

test('برنامه: opِ رسیده روی فهرستِ نام‌ها می‌نشیند و پژواک نمی‌سازد', () => {
  const ledger = { productCategories: ['لبنیات'], productUnits: ['عدد'], expenseCategories: ['برق'] };
  const ops = [
    { op_id: '1', table: 'productCategories', row_id: idOf('نوشیدنی'), type: 'insert', fields: { name: 'نوشیدنی' } },
    { op_id: '2', table: 'productCategories', row_id: idOf('نوشیدنی'), type: 'insert', fields: { name: 'نوشیدنی' } },
    { op_id: '3', table: 'expenseCategories', row_id: idOf('برق'), type: 'delete' },
  ];
  const out = core.applyRemote(ledger, ops);
  assert.deepEqual(out.data.productCategories, ['لبنیات', 'نوشیدنی'], 'ترتیبِ محلی می‌ماند، تازه ته می‌نشیند، تکراری نه');
  assert.deepEqual(out.data.expenseCategories, []);
  assert.deepEqual(ledger.productCategories, ['لبنیات'], 'دفترِ ورودی دست نمی‌خورد');
  assert.equal(core.diff(core.shadowOf(out.data), out.data).ops.length, 0);
});

/* ---------------------------------------------------- دو دستگاه، سرورِ واقعی */

test('دو دستگاه: دسته، واحد و دستهٔ مصرف از A به B می‌رسد، و حذف هم', async () => {
  const A = makeDevice('lists-a', owner.accessToken);
  const B = makeDevice('lists-b', owner.accessToken);

  A.write(d => {
    d.productCategories.push('نوشیدنی');
    d.productUnits.push('بسته');
    d.expenseCategories.push('اینترنت');
  });
  const pushed = await A.push();
  assert.deepEqual(pushed.results.map(r => r.status), ['applied', 'applied', 'applied'], JSON.stringify(pushed.results));

  await B.pull();
  assert.deepEqual(B.ledger.productCategories, ['نوشیدنی']);
  assert.deepEqual(B.ledger.productUnits, ['بسته']);
  assert.deepEqual(B.ledger.expenseCategories, ['اینترنت']);

  //  حذف روی B ⇒ روی A هم می‌رود
  B.write(d => { d.productCategories = d.productCategories.filter(n => n !== 'نوشیدنی'); });
  await B.push();
  await A.pull();
  assert.deepEqual(A.ledger.productCategories, []);
  assert.deepEqual(A.ledger.productUnits, ['بسته'], 'بقیه دست نمی‌خورد');

  const row = await one(
    `SELECT deleted_at FROM sync_rows WHERE app='shop' AND account_id=$1 AND table_name='productCategories' AND row_id=$2`,
    [shopId, idOf('نوشیدنی')]
  );
  assert.ok(row && row.deleted_at, 'روی سرور سنگِ قبر نشسته');
});

test('دو دستگاه یک نام را جدا می‌سازند ⇒ یک ردیف، و روی هر دو یک بار', async () => {
  const A = makeDevice('same-a', owner.accessToken);
  const B = makeDevice('same-b', owner.accessToken);
  A.write(d => { d.productCategories.push('شوینده'); });
  B.write(d => { d.productCategories.push('شوینده'); });
  await A.push();
  await B.push();
  await A.pull();
  await B.pull();
  assert.deepEqual(A.ledger.productCategories, ['شوینده']);
  assert.deepEqual(B.ledger.productCategories, ['شوینده']);
  const r = await one(
    `SELECT COUNT(*)::int AS n FROM sync_rows WHERE app='shop' AND account_id=$1 AND table_name='productCategories' AND row_id=$2`,
    [shopId, idOf('شوینده')]
  );
  assert.equal(r.n, 1);
});

test('دستگاهِ نو فهرست‌ها را از Snapshot می‌گیرد', async () => {
  const A = makeDevice('snap-a', owner.accessToken);
  A.write(d => { d.productUnits.push('جعبه'); });
  await A.push();
  const r = await h.api('GET', '/api/sync/v1/snapshot', { token: owner.accessToken, headers: { 'X-App': 'shop' } });
  assert.equal(r.status, 200);
  const fresh = core.fromSnapshot(r.body, { productUnits: ['عدد'], productCategories: [], expenseCategories: ['برق'] });
  assert.deepEqual(fresh.productUnits, ['جعبه'], 'فهرستِ سرور جای پیش‌فرضِ دستگاهِ نو می‌نشیند');
  assert.deepEqual(fresh.expenseCategories, ['برق'], 'فهرستی که سرور هنوز ندارد دست نمی‌خورد');
});

test('سازگاری: برنامهٔ پلهٔ ۲ هنوز پذیرفته می‌شود و راهِ قدیم هم با جدول‌های تازه کار می‌کند', async () => {
  const old = await h.api('POST', '/api/sync/v1/push', {
    token: owner.accessToken,
    headers: { 'X-App': 'shop', 'X-Device': 'old-2' },
    body: { device_id: 'old-2', schema_version: 2, ops: [] },
  });
  assert.equal(old.status, 200, JSON.stringify(old.body));
  assert.equal(old.body.upgrade_available, true);

  const legacy = await h.api('GET', '/api/sync?since=0', { token: owner.accessToken });
  assert.equal(legacy.status, 200, JSON.stringify(legacy.body));
});
