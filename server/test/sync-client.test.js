'use strict';
/**
 * سمتِ **برنامه**ی Sync v1 — بندِ ۲۱ پرامپت.
 *
 * ── چرا این پرونده هست ──────────────────────────────────────────────
 * `sync-v1.test.js` می‌گوید سرور درست کار می‌کند. این یکی می‌گوید
 * **برنامه** درست کار می‌کند: دفترِ تغییرات، تفاضل، صف، دلتا، و
 * رفت‌وبرگشتِ واقعی با همان سروری که مشتری به آن وصل است.
 *
 * ⚠️ کدِ زیرِ آزمون همان فایلی است که در مرورگر می‌دود —
 * `license/sync-core.js`، بی هیچ نسخهٔ دوم و بی هیچ ساختگی.
 * برنامهٔ اندروید همان منطق را در `sync/v1/*.kt` دارد و آزمونِ خودش
 * را (`SyncOplogTest`, `LedgerDiffTest`, `OfflineDrainTest`) در همان
 * پروژه می‌دواند — این سندباکس SDKِ اندروید ندارد.
 *
 * ── آن‌چه سنجیده می‌شود ─────────────────────────────────────────────
 *   واحد      هر نوشتن = دقیقاً یک opِ درست · دلتا · اثرِ انگشت · صف
 *   یکپارچه   با سرورِ واقعی: تعارضِ فیلدی، دو `$inc`، سه روز آفلاین،
 *             Snapshot، ۴۲۶، و تکراری
 */
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';

const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const path = require('node:path');

const h = require('./helpers');
const { one } = require('../src/db');
const syncV1 = require('../src/lib/sync-v1');

/** همان فایلی که مرورگر بالا می‌آورد. */
const core = require(path.join(__dirname, '..', '..', 'license', 'sync-core.js'));

/* ==========================================================
   یک «مرورگرِ کاغذی» — حافظه‌ای که بستنِ برنامه را تقلید می‌کند
   ----------------------------------------------------------
   موتورِ وب (`sync-engine.js`) روی `localStorage` می‌نشیند. این‌جا
   همان `io` را با یک `Map` می‌دهیم، پس همان `OpQueue`ِ واقعی سنجیده
   می‌شود، نه یک بدلِ آزمونی.
   ========================================================== */
function makeDisk() {
  const box = new Map();
  return {
    io: {
      read: (k, dflt) => (box.has(k) ? JSON.parse(box.get(k)) : dflt),
      write: (k, v) => { box.set(k, JSON.stringify(v)); return true; },
    },
    box,
  };
}

/** یک دستگاه: دفتر، سایه، صف و cursor — همان چهار چیزی که موتور دارد. */
function makeDevice(deviceId, token) {
  const disk = makeDisk();
  const q = new core.OpQueue(disk.io, 'oplog');
  return {
    id: deviceId,
    token,
    ledger: {},
    shadow: null,
    cursor: 0,
    queue: q,
    disk,

    /** همان کارِ `recordChange()`: تفاضل ⇒ صف ⇒ سایه جلو می‌رود. */
    write(mutate) {
      if (this.shadow === null) this.shadow = core.shadowOf(this.ledger);
      mutate(this.ledger);
      const { ops } = core.diff(this.shadow, this.ledger);
      this.queue.push(ops);
      this.shadow = core.shadowOf(this.ledger);
      return ops;
    },

    /** دفترِ موجود را یک بار به سرور می‌دهد (دستگاهِ نخست). */
    baseline() {
      const { ops } = core.diff({}, this.ledger);
      this.queue.push(ops);
      this.shadow = core.shadowOf(this.ledger);
      return ops;
    },

    async push() {
      let sent = 0;
      let last = null;
      //  تا ته، دسته‌دسته — همان حلقهٔ `pump()`
      for (let guard = 0; guard < 500 && this.queue.size(); guard++) {
        const batch = this.queue.batch();
        if (!batch.length) break;
        const r = await h.api('POST', '/api/sync/v1/push', {
          token: this.token,
          body: { device_id: this.id, schema_version: core.SCHEMA_VERSION, ops: batch, queued: this.queue.size() },
          headers: { 'X-App': 'shop', 'X-Device': this.id },
        });
        last = r;
        if (r.status !== 200) return { status: r.status, body: r.body, sent };
        const ok = r.body.results.filter(x => x.status === 'applied' || x.status === 'duplicate').map(x => x.op_id);
        const bad = r.body.results.filter(x => x.status === 'rejected');
        this.queue.ack(ok);
        if (bad.length) {
          this.queue.drop(batch.filter(o => bad.some(b => b.op_id === o.op_id)), 'rejected');
          this.queue.ack(bad.map(b => b.op_id));
        }
        sent += batch.length;
      }
      return { status: last ? last.status : 200, body: last && last.body, sent };
    },

    async pull() {
      const r = await h.api('GET',
        `/api/sync/v1/pull?device_id=${this.id}&since=${this.cursor}`,
        { token: this.token, headers: { 'X-App': 'shop' } });
      assert.equal(r.status, 200, JSON.stringify(r.body));
      const out = core.applyRemote(this.ledger, r.body.ops || []);
      this.ledger = out.data;
      this.shadow = core.shadowOf(this.ledger);
      this.cursor = Number(r.body.cursor || this.cursor);
      return out.applied;
    },

    async snapshot() {
      const r = await h.api('GET', '/api/sync/v1/snapshot', { token: this.token, headers: { 'X-App': 'shop' } });
      assert.equal(r.status, 200);
      this.ledger = core.fromSnapshot(r.body, this.ledger);
      this.shadow = core.shadowOf(this.ledger);
      this.cursor = Number(r.body.cursor || 0);
      return r.body;
    },

    row(collection, id) {
      return (this.ledger[collection] || []).find(x => String(x.id) === String(id)) || null;
    },
  };
}

const DEV_A = 'wpe1-a';
const DEV_B = 'wpe1-b';
let owner; let shopId;

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
  owner = await h.newUser('دکان‌دارِ آزمون', 'shop');
  const shop = await h.api('POST', '/api/shop', { token: owner.accessToken, body: { name: 'دکانِ سنجه' } });
  shopId = shop.body?.shop?.id || shop.body?.id;
  const again = await h.api('POST', '/api/auth/login', {
    body: {
      identifier: owner.email, password: owner.password, app: 'shop',
      device: { deviceId: DEV_A, name: 'A', platform: 'test' },
    },
  });
  owner = { ...owner, ...again.body };
});
test.after(async () => { await h.stop(); });

/* ══════════════════════════════════════════════════════════
   ۱) واحد — «هر نوشتن، دقیقاً یک opِ درست»
   ══════════════════════════════════════════════════════════ */

test('درجِ یک کالا = دقیقاً یک opِ insert، با همهٔ فیلدها و بی `id` تکراری', () => {
  const d = makeDevice('unit', '');
  const ops = d.write((L) => {
    L.products = [{ id: 'p-1', name: 'برنج', salePrice: 120, barcodes: ['111'] }];
  });
  assert.equal(ops.length, 1);
  assert.equal(ops[0].type, 'insert');
  assert.equal(ops[0].table, 'products');
  assert.equal(ops[0].row_id, 'p-1');
  assert.deepEqual(ops[0].fields, { name: 'برنج', salePrice: 120, barcodes: ['111'] });
  assert.equal(ops[0].fields.id, undefined, 'شناسه داخلِ fields تکرار نمی‌شود');
  assert.match(ops[0].op_id, /^[0-9A-HJKMNP-TV-Z]{26}$/, 'op_id باید ULID باشد');
});

test('عوض کردنِ یک حرف از یک اسم = یک opِ update با **یک** فیلد', () => {
  const d = makeDevice('unit', '');
  d.write((L) => { L.debtors = [{ id: 'c-1', name: 'احمد', phone: '070', notes: 'x' }]; });
  const ops = d.write((L) => { L.debtors[0].name = 'احمدی'; });
  assert.equal(ops.length, 1);
  assert.equal(ops[0].type, 'update');
  assert.deepEqual(Object.keys(ops[0].fields), ['name']);
  assert.equal(ops[0].fields.name, 'احمدی');
});

test('حذف = یک opِ بی فیلد، و بدنهٔ کاملِ درخواست زیرِ یک کیلوبایت', () => {
  const d = makeDevice('unit', '');
  d.write((L) => { L.debtors = [{ id: 'c-2', name: 'کریم', phone: '0700000000' }]; });
  const ops = d.write((L) => { L.debtors = []; });
  assert.equal(ops.length, 1);
  assert.equal(ops[0].type, 'delete');
  assert.equal(ops[0].fields, undefined);
  const body = { device_id: 'x', schema_version: core.SCHEMA_VERSION, ops };
  const bytes = Buffer.byteLength(JSON.stringify(body), 'utf8');
  assert.ok(bytes < 1024, `بدنهٔ حذف ${bytes} بایت شد`);
});

test('ویرایشِ ده ردیف = ده op، نه یک opِ «کلِ دفتر»', () => {
  const d = makeDevice('unit', '');
  d.write((L) => {
    L.products = [];
    for (let i = 0; i < 10; i++) L.products.push({ id: `p${i}`, name: `کالا ${i}`, salePrice: i });
  });
  const ops = d.write((L) => { L.products.forEach((p) => { p.salePrice += 1; }); });
  assert.equal(ops.length, 10);
  ops.forEach((o) => {
    assert.equal(o.type, 'update');
    assert.deepEqual(Object.keys(o.fields), ['salePrice']);
  });
});

test('هیچ تغییری ⇒ هیچ opی', () => {
  const d = makeDevice('unit', '');
  d.write((L) => { L.products = [{ id: 'p-1', name: 'شکر' }]; });
  const ops = d.write(() => { });
  assert.equal(ops.length, 0);
});

test('فیلدی که از ردیف برداشته شود، صریح `null` می‌رود', () => {
  const d = makeDevice('unit', '');
  d.write((L) => { L.debtors = [{ id: 'c-3', name: 'نور', notes: 'قدیمی' }]; });
  const ops = d.write((L) => { delete L.debtors[0].notes; });
  assert.equal(ops.length, 1);
  assert.equal(ops[0].fields.notes, null);
});

test('شمارنده‌ها دلتا می‌روند، بقیه مقدارِ تازه', () => {
  const d = makeDevice('unit', '');
  d.write((L) => {
    L.saleItems = [{ id: 'si-1', saleId: 's-1', quantity: 5, returnedQty: 0 }];
    L.purchases = [{ id: 'pu-1', totalAmount: 1000, paidAmount: 200, debt: 800 }];
  });
  const ops = d.write((L) => {
    L.saleItems[0].returnedQty = 2;
    L.saleItems[0].quantity = 5;
    L.purchases[0].paidAmount = 500;
    L.purchases[0].debt = 500;
  });
  const si = ops.find(o => o.table === 'saleItems');
  const pu = ops.find(o => o.table === 'purchases');
  assert.deepEqual(si.fields.returnedQty, { $inc: 2 });
  assert.deepEqual(pu.fields.paidAmount, { $inc: 300 });
  assert.deepEqual(pu.fields.debt, { $inc: -300 });
});

test('اثرِ انگشتِ op با فرمولِ خودِ سرور یکی است', () => {
  const sample = { table: 'products', row_id: 'p-1', type: 'insert', fields: { name: 'برنج', n: { $inc: -3 } } };
  assert.equal(core.hashOf(sample), syncV1.hashOf(sample));
  assert.equal(core.canonical(sample.fields), syncV1.canonical(sample.fields));
});

test('SHA-256 خالصِ برنامه همان SHA-256 است — روی متنِ فارسی هم', () => {
  for (const text of ['', 'a', 'برنج و شکر و روغن', 'x'.repeat(1000), '💡 چراغ']) {
    assert.equal(core.sha256Hex(text), crypto.createHash('sha256').update(text, 'utf8').digest('hex'), text.slice(0, 20));
  }
});

test('ULID یکتا، ۲۶ نویسه، و مرتب بر اساس زمان — حتی در یک میلی‌ثانیه', () => {
  const seen = new Set();
  const same = [];
  for (let i = 0; i < 500; i++) same.push(core.ulid(1700000000000));
  same.forEach((u) => { assert.equal(u.length, 26); seen.add(u); });
  assert.equal(seen.size, 500, 'هیچ دو ULIDی نباید یکی باشد');
  const sorted = same.slice().sort();
  assert.deepEqual(sorted, same, 'ULIDهای یک میلی‌ثانیه باید صعودی بمانند');
  assert.ok(core.ulid(1700000001000) > same[same.length - 1], 'میلی‌ثانیهٔ بعدی باید بزرگ‌تر باشد');
});

test('عقب‌نشینی نمایی است، سقف دارد و هیچ‌وقت صفر نمی‌شود', () => {
  const noJitter = (n) => core.backoffMs(n, 0.5);
  assert.equal(noJitter(0), core.BACKOFF_MIN_MS);
  assert.equal(noJitter(1), core.BACKOFF_MIN_MS * 2);
  assert.equal(noJitter(3), core.BACKOFF_MIN_MS * 8);
  assert.equal(noJitter(50), core.BACKOFF_MAX_MS);
  for (let i = 0; i < 20; i++) assert.ok(core.backoffMs(i) >= core.BACKOFF_MIN_MS * 0.7);
});

test('چراغ: سبز همگام · زرد در صف · خاکستری آفلاین · قرمز خطا', () => {
  const base = { online: true, signedIn: true, configured: true, queued: 0, busy: false, error: false };
  assert.equal(core.dotOf(base), 'green');
  assert.equal(core.dotOf({ ...base, queued: 3 }), 'yellow');
  assert.equal(core.dotOf({ ...base, online: false }), 'grey');
  assert.equal(core.dotOf({ ...base, signedIn: false }), 'grey');
  assert.equal(core.dotOf({ ...base, error: true }), 'red');
  //  خطا از همه بالاتر است، حتی وقتی آفلاینیم — وگرنه خطا پنهان می‌ماند
  assert.equal(core.dotOf({ ...base, online: false, error: true }), 'red');
});

/* ══════════════════════════════════════════════════════════
   ۲) صف — «بستنِ برنامه چیزی را نمی‌برد»
   ══════════════════════════════════════════════════════════ */

test('صف روی همان حافظه می‌ماند و با باز شدنِ دوبارهٔ برنامه سرِ جایش است', () => {
  const disk = makeDisk();
  const q1 = new core.OpQueue(disk.io, 'oplog');
  q1.push([{ op_id: 'a' }, { op_id: 'b' }]);
  //  «برنامه بسته شد»: شیءِ صف از بین می‌رود، حافظه نه
  const q2 = new core.OpQueue(disk.io, 'oplog');
  assert.equal(q2.size(), 2);
});

test('دسته سقفِ خودِ سرور را رعایت می‌کند: ۲۰۰ op و ۲۵۶ کیلوبایت', () => {
  const disk = makeDisk();
  const q = new core.OpQueue(disk.io, 'oplog');
  const many = [];
  for (let i = 0; i < 500; i++) many.push({ op_id: 'o' + i, table: 'products', row_id: 'p' + i, type: 'update', fields: { name: 'x' } });
  q.push(many);
  assert.equal(q.batch().length, 200);

  const fat = [];
  for (let i = 0; i < 50; i++) {
    fat.push({ op_id: 'f' + i, table: 'products', row_id: 'p' + i, type: 'update', fields: { notes: 'ن'.repeat(9000) } });
  }
  const q2 = new core.OpQueue(makeDisk().io, 'oplog');
  q2.push(fat);
  const batch = q2.batch();
  assert.ok(batch.length < 50, 'دستهٔ سنگین باید بریده شود');
  assert.ok(Buffer.byteLength(JSON.stringify(batch), 'utf8') <= 256 * 1024);
  assert.ok(batch.length >= 1, 'دستِ‌کم یک op باید برود، وگرنه صف قفل می‌شود');
});

test('فقط opهای پذیرفته‌شده از صف می‌روند؛ بقیه سرِ جایشان می‌مانند', () => {
  const q = new core.OpQueue(makeDisk().io, 'oplog');
  q.push([{ op_id: 'a' }, { op_id: 'b' }, { op_id: 'c' }]);
  q.ack(['a', 'c']);
  assert.deepEqual(q.all().map(o => o.op_id), ['b']);
});

test('opی که سرور رد کند بی‌صدا گم نمی‌شود — دلیلش در دفترِ کنار می‌ماند', () => {
  const q = new core.OpQueue(makeDisk().io, 'oplog');
  q.drop([{ op_id: 'x', table: 'ناشناخته' }], 'rejected');
  const book = q.dropped();
  assert.equal(book.length, 1);
  assert.equal(book[0].reason, 'rejected');
});

/* ══════════════════════════════════════════════════════════
   ۳) اعمالِ opهای رسیده
   ══════════════════════════════════════════════════════════ */

test('opهای رسیده روی دفترِ محلی می‌نشینند: insert · update · $inc · delete', () => {
  let data = {};
  data = core.applyRemote(data, [
    { table: 'products', row_id: 'p-1', type: 'insert', fields: { name: 'روغن', stockNote: '', salePrice: 10 } },
  ]).data;
  assert.equal(data.products.length, 1);
  assert.equal(data.products[0].name, 'روغن');
  assert.equal(data.products[0].id, 'p-1');

  data = core.applyRemote(data, [
    { table: 'products', row_id: 'p-1', type: 'update', fields: { salePrice: 15 } },
  ]).data;
  assert.equal(data.products[0].salePrice, 15);
  assert.equal(data.products[0].name, 'روغن', 'فیلدهای دیگر دست نمی‌خورند');

  data = core.applyRemote(data, [
    { table: 'saleItems', row_id: 'si-1', type: 'insert', fields: { returnedQty: 1 } },
    { table: 'saleItems', row_id: 'si-1', type: 'update', fields: { returnedQty: { $inc: 2 } } },
  ]).data;
  assert.equal(data.saleItems[0].returnedQty, 3);

  const out = core.applyRemote(data, [{ table: 'products', row_id: 'p-1', type: 'delete' }]);
  assert.equal(out.data.products.length, 0);
  assert.equal(out.applied, 1);
});

test('جدولِ ناشناخته اعمال نمی‌شود و دفتر را خراب نمی‌کند', () => {
  const out = core.applyRemote({}, [{ table: 'AppUser', row_id: 'x', type: 'insert', fields: { p: 1 } }]);
  assert.equal(out.applied, 0);
  assert.equal(out.skipped, 1);
  assert.equal(out.data.AppUser, undefined);
});

test('اعمالِ opهای رسیده هیچ opِ تازه‌ای نمی‌سازد (حلقهٔ رفت‌وبرگشت بسته است)', () => {
  const d = makeDevice('unit', '');
  d.write((L) => { L.products = [{ id: 'p-1', name: 'نمک' }]; });
  d.queue.clear();
  const out = core.applyRemote(d.ledger, [
    { table: 'products', row_id: 'p-2', type: 'insert', fields: { name: 'فلفل' } },
  ]);
  d.ledger = out.data;
  d.shadow = core.shadowOf(d.ledger);      // همان کاری که موتور می‌کند
  const ops = d.write(() => { });
  assert.equal(ops.length, 0, 'ردیفِ رسیده نباید دوباره به سرور برگردد');
});

/* ══════════════════════════════════════════════════════════
   ۴) یکپارچه — با سرورِ واقعی
   ══════════════════════════════════════════════════════════ */

test('چرخهٔ کامل: دستگاه A می‌نویسد ⇒ سرور ⇒ دستگاه B همان را می‌بیند', async () => {
  const A = makeDevice(DEV_A, owner.accessToken);
  const B = makeDevice(DEV_B, owner.accessToken);

  A.write((L) => {
    L.debtors = [{ id: 'd-100', name: 'احمد', phone: '0700', notes: '', createdAt: 1 }];
  });
  const r = await A.push();
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(A.queue.size(), 0, 'صف باید خالی شده باشد');

  await B.pull();
  assert.equal(B.row('debtors', 'd-100').name, 'احمد');
  assert.ok(B.cursor > 0);
});

test('دستگاه خودش opهای خودش را دوباره نمی‌گیرد', async () => {
  const A = makeDevice(DEV_A, owner.accessToken);
  A.cursor = 0;
  A.write((L) => { L.expenses = [{ id: 'e-1', title: 'کرایه', amount: 500 }]; });
  await A.push();
  const got = await A.pull();
  const mine = (A.ledger.expenses || []).filter(x => x.id === 'e-1');
  assert.equal(mine.length, 1, 'ردیفِ خودش نباید دو بار بنشیند');
  assert.ok(got >= 0);
});

test('تعارضِ سطحِ فیلد: A نام را عوض کند و B تلفن را ⇒ هیچ‌کدام گم نمی‌شود', async () => {
  const A = makeDevice(DEV_A, owner.accessToken);
  const B = makeDevice(DEV_B, owner.accessToken);
  A.write((L) => { L.debtors = [{ id: 'd-200', name: 'قدیم', phone: '0711' }]; });
  await A.push();
  await B.pull();
  await A.pull();

  A.write((L) => { L.debtors.find(x => x.id === 'd-200').name = 'نامِ تازه'; });
  B.write((L) => { L.debtors.find(x => x.id === 'd-200').phone = '0799'; });
  await A.push();
  await B.push();

  const row = await one(
    `SELECT data FROM sync_rows WHERE app='shop' AND account_id=$1 AND table_name='debtors' AND row_id='d-200'`,
    [shopId]
  );
  assert.equal(row.data.name, 'نامِ تازه');
  assert.equal(row.data.phone, '0799');

  //  و هر دو دستگاه به همان حال می‌رسند
  await A.pull();
  await B.pull();
  assert.equal(A.row('debtors', 'd-200').phone, '0799');
  assert.equal(B.row('debtors', 'd-200').name, 'نامِ تازه');
});

test('دو `$inc` هم‌زمان از دو دستگاه ⇒ هر دو حساب می‌شوند', async () => {
  const A = makeDevice(DEV_A, owner.accessToken);
  const B = makeDevice(DEV_B, owner.accessToken);
  A.write((L) => {
    L.purchases = [{ id: 'pu-9', supplierId: 's1', totalAmount: 1000, paidAmount: 0, debt: 1000 }];
  });
  await A.push();
  await B.pull();
  await A.pull();

  //  هر دو گوشی آفلاین یک قسط می‌گیرند
  A.write((L) => { const p = L.purchases.find(x => x.id === 'pu-9'); p.paidAmount = 300; p.debt = 700; });
  B.write((L) => { const p = L.purchases.find(x => x.id === 'pu-9'); p.paidAmount = 200; p.debt = 800; });
  await A.push();
  await B.push();

  const row = await one(
    `SELECT data FROM sync_rows WHERE app='shop' AND account_id=$1 AND table_name='purchases' AND row_id='pu-9'`,
    [shopId]
  );
  assert.equal(Number(row.data.paidAmount), 500, 'دو قسط باید جمع شوند، نه اینکه یکی دیگری را بخورد');
  assert.equal(Number(row.data.debt), 500);
});

test('سه روز آفلاین: opهای سه روز در صف می‌مانند و همه، به ترتیب، می‌روند', async () => {
  const A = makeDevice('wpe1-offline', owner.accessToken);
  const DAY = 24 * 60 * 60 * 1000;
  const t0 = Date.now() - 3 * DAY;

  //  سه روز کارِ واقعیِ یک دکان: کالا، فروش، قلمِ فروش، قرض‌دار، مصرف
  let n = 0;
  for (let d = 0; d < 3; d++) {
    for (let k = 0; k < 40; k++) {
      n += 1;
      const day = t0 + d * DAY + k * 60000;
      A.write((L) => {
        L.products = L.products || [];
        L.sales = L.sales || [];
        L.saleItems = L.saleItems || [];
        L.expenses = L.expenses || [];
        L.products.push({ id: `off-p-${n}`, name: `کالا ${n}`, salePrice: n, createdAt: day });
        L.sales.push({ id: `off-s-${n}`, finalTotal: n * 2, status: 'completed', createdAt: day, channel: 'pos' });
        L.saleItems.push({ id: `off-i-${n}`, saleId: `off-s-${n}`, productId: `off-p-${n}`, quantity: 1, returnedQty: 0 });
        if (k % 10 === 0) L.expenses.push({ id: `off-e-${n}`, title: 'مصرف', amount: k, createdAt: day });
      });
    }
  }
  const queued = A.queue.size();
  assert.ok(queued > 300, `صف باید پر باشد، ${queued} op شد`);

  //  «اینترنت آمد»
  const r = await A.push();
  assert.equal(r.status, 200);
  assert.equal(A.queue.size(), 0, 'هیچ opی نباید در صف بماند');
  assert.equal(A.queue.dropped().length, 0, 'هیچ opی نباید رد شده باشد');
  assert.equal(r.sent, queued, 'همان تعدادی که در صف بود باید رفته باشد');

  const rows = await one(
    `SELECT COUNT(*)::int n FROM sync_rows WHERE app='shop' AND account_id=$1 AND row_id LIKE 'off-%'`,
    [shopId]
  );
  assert.equal(rows.n, 120 + 120 + 120 + 12, 'همهٔ ردیف‌های سه روز باید روی سرور باشند');

  //  و ترتیب حفظ شده: `server_seq` باید با ترتیبِ ULID یکی باشد
  const order = await one(
    `SELECT COUNT(*)::int n FROM (
       SELECT op_id, server_seq,
              LAG(op_id) OVER (ORDER BY server_seq) AS prev
         FROM oplog WHERE app='shop' AND account_id=$1 AND device_id='wpe1-offline'
     ) t WHERE prev IS NOT NULL AND op_id < prev`,
    [shopId]
  );
  assert.equal(order.n, 0, 'opها باید به همان ترتیبی که ساخته شده‌اند نشسته باشند');
});

test('گوشیِ نو: Snapshot می‌گیرد و از همان‌جا ادامه می‌دهد', async () => {
  const NEW = makeDevice('wpe1-new-phone', owner.accessToken);
  const snap = await NEW.snapshot();
  assert.ok(Number(snap.rows) > 0);
  assert.ok(NEW.cursor > 0);
  assert.equal(NEW.row('debtors', 'd-100').name, 'احمد');

  //  از این به بعد فقط تغییر
  NEW.write((L) => { L.debtors.find(x => x.id === 'd-100').notes = 'از گوشیِ نو'; });
  const ops = NEW.queue.all();
  assert.equal(ops.length, 1, 'پس از Snapshot نباید کلِ دفتر دوباره برود');
  assert.equal(ops[0].type, 'update');
  await NEW.push();

  const row = await one(
    `SELECT data FROM sync_rows WHERE app='shop' AND account_id=$1 AND table_name='debtors' AND row_id='d-100'`,
    [shopId]
  );
  assert.equal(row.data.notes, 'از گوشیِ نو');
});

test('فرستادنِ دوبارهٔ یک دسته (اینترنتِ نصفه) چیزی را دو بار نمی‌نشاند', async () => {
  const A = makeDevice(DEV_A, owner.accessToken);
  A.write((L) => { L.suppliers = [{ id: 'sup-1', name: 'تأمین‌کننده', phone: '077' }]; });
  const batch = A.queue.batch();
  const body = { device_id: DEV_A, schema_version: core.SCHEMA_VERSION, ops: batch };
  const first = await h.api('POST', '/api/sync/v1/push', { token: owner.accessToken, body, headers: { 'X-App': 'shop' } });
  const again = await h.api('POST', '/api/sync/v1/push', { token: owner.accessToken, body, headers: { 'X-App': 'shop' } });
  assert.equal(first.body.results[0].status, 'applied');
  assert.equal(again.body.results[0].status, 'duplicate');
  //  و موتور هر دو را «رفت» می‌شمارد، پس صف قفل نمی‌شود
  A.queue.ack(again.body.results.filter(x => x.status === 'duplicate').map(x => x.op_id));
  assert.equal(A.queue.size(), 0);
});

test('۴۲۶: برنامهٔ جلوتر از سرور ⇒ هیچ opی اعمال نمی‌شود و صف دست‌نخورده می‌ماند', async () => {
  const A = makeDevice('wpe1-future', owner.accessToken);
  A.write((L) => { L.products = [{ id: 'future-1', name: 'از آینده' }]; });
  const before = A.queue.size();

  const r = await h.api('POST', '/api/sync/v1/push', {
    token: owner.accessToken,
    body: { device_id: A.id, schema_version: core.SCHEMA_VERSION + 5, ops: A.queue.batch() },
    headers: { 'X-App': 'shop' },
  });
  assert.equal(r.status, 426);
  assert.equal(r.body.error, 'UPGRADE_REQUIRED');
  //  موتور روی ۴۲۶ هیچ `ack`ی نمی‌زند
  assert.equal(A.queue.size(), before, 'opها باید سرِ جایشان بمانند');

  const row = await one(
    `SELECT COUNT(*)::int n FROM sync_rows WHERE app='shop' AND account_id=$1 AND row_id='future-1'`,
    [shopId]
  );
  assert.equal(row.n, 0, 'هیچ چیزی نباید نشسته باشد');

  //  و با نسخهٔ درست، همان صف بی گم شدن می‌رود
  const ok = await A.push();
  assert.equal(ok.status, 200);
  assert.equal(A.queue.size(), 0);
});

test('برنامهٔ عقب‌تر از سرور پذیرفته می‌شود و «نسخهٔ تازه آماده است» می‌گیرد', async () => {
  const A = makeDevice('wpe1-old', owner.accessToken);
  const r = await h.api('POST', '/api/sync/v1/push', {
    token: owner.accessToken,
    body: {
      device_id: A.id, schema_version: 1,
      ops: [core.diff({}, { sales: [{ id: 'old-1', finalTotal: 7, status: 'completed' }] }).ops[0]],
    },
    headers: { 'X-App': 'shop' },
  });
  assert.equal(r.status, 200);
  assert.equal(r.body.upgrade_available, true);
  const row = await one(
    `SELECT data FROM sync_rows WHERE app='shop' AND account_id=$1 AND table_name='sales' AND row_id='old-1'`,
    [shopId]
  );
  assert.equal(row.data.channel, 'pos', 'سرور خودش فیلدِ تازه را پر می‌کند');
});

test('نامِ جدولِ بیرونِ فهرستِ سفید رد می‌شود و برنامه دلیلش را نگه می‌دارد', async () => {
  const A = makeDevice('wpe1-bad', owner.accessToken);
  A.queue.push([{
    op_id: core.ulid(), ts: Date.now(), table: 'AppUser', row_id: 'x', type: 'insert', fields: { p: 1 },
  }]);
  const r = await A.push();
  assert.equal(r.status, 200);
  assert.equal(A.queue.size(), 0, 'صف نباید روی opِ ردشده قفل بماند');
  const book = A.queue.dropped();
  assert.equal(book.length, 1);
  assert.equal(book[0].op.table, 'AppUser');
});

test('ورود با کدِ شش‌رقمی: همان سه پله‌ای که نسخهٔ وب می‌رود', async () => {
  const codes = require('../src/lib/login-codes');
  const email = `wpe1.login.${Date.now()}@example.com`;
  const req = await h.api('POST', '/api/auth/shop/request-code', {
    body: { email, device_name: 'نسخهٔ وب' },
    headers: { 'X-App': 'shop', 'X-Device': 'wpe1-web' },
  });
  assert.equal(req.status, 200);
  assert.ok(req.body.request_id);
  assert.equal(req.body.resend_after, 60);

  const st = await h.api('GET', `/api/auth/shop/request-status?request_id=${req.body.request_id}`,
    { headers: { 'X-App': 'shop' } });
  assert.equal(st.status, 200);
  assert.ok(['queued', 'sending', 'sent'].includes(st.body.state));

  const row = await one('SELECT code_sealed FROM login_requests WHERE request_id=$1', [req.body.request_id]);
  const code = codes.unseal(row.code_sealed);

  //  ارقامِ فارسی — همان چیزی که صفحه‌کلیدِ گوشیِ کاربر می‌دهد
  const FA = '۰۱۲۳۴۵۶۷۸۹';
  const persian = code.split('').map(d => FA[+d]).join('');
  const english = persian.replace(/[۰-۹]/g, (d) => String(FA.indexOf(d)));
  assert.equal(english, code);

  const ver = await h.api('POST', '/api/auth/shop/verify', {
    body: { request_id: req.body.request_id, code: english, device_id: 'wpe1-web', device_name: 'نسخهٔ وب' },
    headers: { 'X-App': 'shop', 'X-Device': 'wpe1-web' },
  });
  assert.equal(ver.status, 200, JSON.stringify(ver.body));
  assert.ok(ver.body.access_token);
  assert.ok(ver.body.refresh_token);
  assert.ok(ver.body.subscription, 'پاسخِ ورود باید حالِ اشتراک را هم بدهد');

  //  تازه‌سازیِ چرخشی — همان کاری که `account-code.js` می‌کند
  const ref = await h.api('POST', '/api/auth/refresh', { body: { refreshToken: ver.body.refresh_token } });
  assert.equal(ref.status, 200);
  assert.ok(ref.body.accessToken);
  assert.notEqual(ref.body.refreshToken, ver.body.refresh_token, 'توکنِ تازه‌سازی باید چرخیده باشد');
});

test('تپش همهٔ آن‌چه «اشتراکِ من» لازم دارد را یک‌جا می‌دهد', async () => {
  const r = await h.api('GET', '/api/me/heartbeat', { token: owner.accessToken, headers: { 'X-App': 'shop' } });
  assert.equal(r.status, 200);
  assert.ok(r.body.subscription, 'حالِ اشتراک');
  assert.ok(['green', 'yellow', 'red'].includes(r.body.subscription.color), 'رنگ از سرور می‌آید، نه از برنامه');
  assert.equal(typeof r.body.subscription.label, 'string');
  assert.ok('permanent' in r.body.subscription);
  assert.ok('unreadNotices' in r.body);
  assert.ok(r.body.serverTime > 0, 'ساعتِ سرور — برنامه به ساعتِ گوشی تکیه نمی‌کند');
});
