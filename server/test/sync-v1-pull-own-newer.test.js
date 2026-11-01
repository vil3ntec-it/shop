'use strict';
/**
 * پرچمِ `sync.pull_own_newer` — pull ِ Sync v1 نوشتهٔ تازه‌ترِ **خودِ** دستگاه را با opِ کهنه‌ترِ دیگری
 * بازنویسی نمی‌کند (شرح سرِ `ownNewerView` در `src/lib/sync-v1.js`).
 *
 * یافته (پیش از این PR، روی main): داوری «آخری که رسید» است و سرور درست داوری می‌کرد، ولی دستگاهِ برنده
 * با pull ِ بعدی opِ کهنه‌ترِ بازنده را روی دفترش می‌نشاند و برای همیشه با سرور و دستگاه‌های دیگر فرق می‌کرد —
 * opِ خودش هرگز به خودش برنمی‌گردد. کدِ زیرِ آزمون همان `license/sync-core.js` ِ وب است (تفاضل، صف،
 * `applyRemote`)؛ اندروید همان الگوریتم را دارد (`LedgerDiff.applyRemote`) و همین پاسخِ سرور را می‌گیرد.
 *
 *   ۱) یک فیلد از دو دستگاه، دومی دیرتر ⇒ با پرچم هر سه یکی و تعارض ثبت؛ بی پرچم دستگاهِ برنده بازنده را نشان می‌دهد.
 *   ۲) op ِ چندفیلدی ⇒ فقط فیلدِ نوشته‌شده به دستِ خودِ دستگاه برداشته می‌شود.
 *   ۳) `$inc` هرگز برداشته نمی‌شود.
 *   ۴) دستگاهِ سوم پس از هر دو ⇒ op ِ کهنه دست نمی‌خورد و برندهٔ واقعی می‌رسد.
 *   ۵) B حذف، A پس از آن ویرایش ⇒ ردیف از دفترِ A نمی‌رود. (B ِ حذف‌کننده: «مانده»، `todo`)
 *   ۶) B ویرایش، A پس از آن حذف ⇒ «شبح» در دفترِ A نمی‌ماند.
 *   ۷) صفحه‌بندی، پاسخِ گم‌شده و pull ِ تکراری ⇒ همان پاسخ و همان نتیجه؛ دستگاهِ بی نوشتهٔ تازه‌تر ⇒ پاسخِ مو‌به‌مو.
 *   ۸) push ِ تکراری (پاسخِ push گم شد) و بازشدنِ دوبارهٔ برنامه میانِ push و pull ⇒ همان نتیجه.
 *   ۹) پمپ (همان مسیرِ مشترک) با پرچمِ `all` و پرچمِ خودِ پمپ ⇒ پاسخِ pull همان امروز.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const path = require('node:path');
const h = require('./helpers');
const db = require('../src/db');
const syncV1 = require('../src/lib/sync-v1');

const core = require(path.join(__dirname, '..', '..', 'license', 'sync-core.js'));

const FLAG = 'sync.pull_own_newer';

const setFlag = (scope, on) => db.query(
  `INSERT INTO live_config (scope, key, value, updated_at) VALUES ($1,$2,$3,1)
   ON CONFLICT (scope, key) DO UPDATE SET value = excluded.value`,
  [String(scope), FLAG, on ? 'true' : 'false']
);

async function shop(label, { flag }) {
  const u = await h.newUser(`صاحب-${label}`, 'shop');
  const made = await h.api('POST', '/api/shop', { token: u.accessToken, body: { name: `دکان ${label}` } });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const shopId = made.body.shop.id;
  if (flag) await setFlag(shopId, true);
  return { token: u.accessToken, shopId };
}

/* ── یک دستگاهِ وب: دفتر، سایه، صف و cursor روی «دیسک» (Map) — بستن و باز کردن همان را برمی‌گرداند ── */
function device(id, token, disk = new Map()) {
  const io = {
    read: (k, dflt) => (disk.has(k) ? JSON.parse(disk.get(k)) : dflt),
    write: (k, v) => { disk.set(k, JSON.stringify(v)); return true; },
  };
  const q = new core.OpQueue(io, 'oplog');
  const get = (k, d) => io.read(k, d);
  const put = (k, v) => io.write(k, v);
  return {
    id, token, disk, queue: q,
    get ledger() { return get('ledger', {}); },
    get cursor() { return get('cursor', 0); },
    /** همان `recordChange`: تفاضل ⇒ صف ⇒ سایه */
    write(mutate) {
      const data = this.ledger;
      const base = get('shadow', null) || core.shadowOf(data);
      mutate(data);
      const { ops } = core.diff(base, data);
      q.push(ops);
      put('ledger', data);
      put('shadow', core.shadowOf(data));
      return ops;
    },
    /** همان حلقهٔ `pump()`: صف تا ته. `lose` ⇒ سرور پردازش می‌کند، پاسخ نمی‌رسد (صف دست نمی‌خورد) */
    async push({ lose = false } = {}) {
      for (let guard = 0; guard < 500 && q.size(); guard++) {
        const batch = q.batch();
        const r = await h.api('POST', '/api/sync/v1/push', {
          token, headers: { 'X-App': 'shop', 'X-Device': id },
          body: { device_id: id, schema_version: core.SCHEMA_VERSION, ops: batch, queued: q.size(), client: 'web' },
        });
        assert.equal(r.status, 200, JSON.stringify(r.body));
        if (lose) return r.body;
        const okIds = r.body.results.filter(x => x.status === 'applied' || x.status === 'duplicate').map(x => x.op_id);
        assert.equal(okIds.length, batch.length, JSON.stringify(r.body.results));
        q.ack(okIds);
      }
      return null;
    },
    /** همان `pullOnce`: صفحه‌به‌صفحه. `lose` ⇒ نخستین پاسخ نمی‌رسد (هیچ چیزِ محلی عوض نمی‌شود) */
    async pull({ limit, lose = false } = {}) {
      const bodies = [];
      for (let guard = 0; guard < 500; guard++) {
        const since = this.cursor;
        const r = await h.api('GET',
          `/api/sync/v1/pull?device_id=${id}&since=${since}${limit ? `&limit=${limit}` : ''}`,
          { token, headers: { 'X-App': 'shop' } });
        assert.equal(r.status, 200, JSON.stringify(r.body));
        bodies.push(r.body);
        if (lose) return bodies;
        const ops = r.body.ops || [];
        if (ops.length) {
          const out = core.applyRemote(this.ledger, ops);
          put('ledger', out.data);
          put('shadow', core.shadowOf(out.data));
        }
        put('cursor', Number(r.body.cursor || since));
        if (!r.body.has_more) break;
      }
      return bodies;
    },
    row(table, rowId) {
      return (this.ledger[table] || []).find(x => String(x.id) === String(rowId)) || null;
    },
  };
}

async function serverRow(shopId, table, rowId) {
  const r = await db.one(
    `SELECT data, deleted_at FROM sync_rows WHERE app='shop' AND account_kind='shop' AND account_id=$1
       AND table_name=$2 AND row_id=$3`, [shopId, table, rowId]);
  if (!r) return null;
  const data = typeof r.data === 'string' ? JSON.parse(r.data) : r.data;
  return { data, deleted: r.deleted_at !== null };
}

/** ردیفِ دستگاه برابرِ سرور: هر دو نیست/حذف، یا همهٔ فیلدهای سرور (و فقط آن‌ها) با `id` */
async function sameAsServer(dev, shopId, table, rowId, label) {
  const s = await serverRow(shopId, table, rowId);
  const mine = dev.row(table, rowId);
  if (!s || s.deleted) {
    assert.equal(mine, null, `${label}: سرور ردیف را ندارد ولی ${dev.id} دارد: ${JSON.stringify(mine)}`);
    return;
  }
  assert.deepEqual(mine, { ...s.data, id: rowId }, `${label}: ${dev.id} با سرور فرق دارد`);
}

const conflictsOf = (shopId, rowId) => db.many(
  `SELECT field, loser_value, winner_value, loser_device, winner_device FROM sync_conflicts
    WHERE app='shop' AND account_kind='shop' AND account_id=$1 AND row_id=$2 ORDER BY id`, [shopId, rowId]);

/** هر دو دستگاه ردیف را دارند، همگام */
async function seeded(A, B, table, rows) {
  A.write((L) => { L[table] = (L[table] || []).concat(rows); });
  await A.push();
  await A.pull();
  await B.pull();
  for (const r of rows) assert.ok(B.row(table, r.id), 'B ردیفِ پایه را گرفت');
}

const price = (L, id, v) => { L.products.find(x => x.id === id).price = v; };

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
});
test.after(async () => { await h.stop(); });

test('۱) یک فیلد، B زودتر و A دیرتر (هیچ‌کدام دیگری را ندیده) ⇒ با پرچم A = B = سرور و تعارض ثبت؛ بی پرچم A بازنده را نشان می‌دهد', async () => {
  for (const flag of [true, false]) {
    const s = await shop(`own1-${flag}`, { flag });
    const A = device('own1-a', s.token);
    const B = device('own1-b', s.token);
    await seeded(A, B, 'products', [{ id: 'p-1', name: 'چای', price: 100 }]);

    B.write((L) => price(L, 'p-1', 300));
    A.write((L) => price(L, 'p-1', 200));
    await B.push();
    await A.push();
    const [page] = await A.pull();
    await B.pull();
    //  با پرچم op ِ B که تنها فیلدش را A پس از آن نوشت اصلاً فرستاده نمی‌شود (op ِ تهی نه)
    assert.equal(page.ops.some(o => o.device_id === 'own1-b' && o.row_id === 'p-1'), !flag);

    const srv = await serverRow(s.shopId, 'products', 'p-1');
    assert.equal(srv.data.price, 200, 'آخری که رسید (A) برنده است');
    assert.equal(B.row('products', 'p-1').price, 200);
    const cf = await conflictsOf(s.shopId, 'p-1');
    assert.equal(cf.length, 1);
    assert.deepEqual([cf[0].field, cf[0].loser_value, cf[0].winner_value, cf[0].loser_device, cf[0].winner_device],
      ['price', 300, 200, 'own1-b', 'own1-a']);
    if (flag) {
      await sameAsServer(A, s.shopId, 'products', 'p-1', 'پرچم روشن');
      await sameAsServer(B, s.shopId, 'products', 'p-1', 'پرچم روشن');
    } else {
      //  رفتارِ امروز (مرجع): دستگاهِ برنده مقدارِ بازنده را نشان می‌دهد و هرگز درست نمی‌شود
      assert.equal(A.row('products', 'p-1').price, 300);
      await A.pull();
      assert.equal(A.row('products', 'p-1').price, 300);
    }
  }
});

test('۲) op ِ چندفیلدیِ دیگری ⇒ فقط فیلدی برداشته می‌شود که خودِ دستگاه پس از آن نوشته', async () => {
  const s = await shop('own2', { flag: true });
  const A = device('own2-a', s.token);
  const B = device('own2-b', s.token);
  await seeded(A, B, 'products', [{ id: 'p-2', name: 'قند', price: 100, unit: 'کیلو' }]);

  B.write((L) => { const p = L.products.find(x => x.id === 'p-2'); p.price = 300; p.name = 'قندِ تازه'; });
  A.write((L) => price(L, 'p-2', 200));
  await B.push();
  await A.push();
  const [page] = await A.pull();
  const theirs = page.ops.find(o => o.device_id === 'own2-b' && o.row_id === 'p-2');
  assert.deepEqual(theirs.fields, { name: 'قندِ تازه' }, 'price ِ B برای A برداشته شد، name ماند');
  await B.pull();
  assert.deepEqual(A.row('products', 'p-2'), { id: 'p-2', name: 'قندِ تازه', price: 200, unit: 'کیلو' });
  await sameAsServer(A, s.shopId, 'products', 'p-2', 'چندفیلدی');
  await sameAsServer(B, s.shopId, 'products', 'p-2', 'چندفیلدی');
});

test('۳) `$inc` هرگز برداشته نمی‌شود: دو دریافت روی یک فاکتور از دو دستگاه هر دو حساب می‌شوند', async () => {
  const s = await shop('own3', { flag: true });
  const A = device('own3-a', s.token);
  const B = device('own3-b', s.token);
  await seeded(A, B, 'sales', [{ id: 's-3', total: 1000, paidAmount: 100, remaining: 900, note: '' }]);

  B.write((L) => { const x = L.sales.find(r => r.id === 's-3'); x.paidAmount += 50; x.remaining -= 50; x.note = 'B'; });
  A.write((L) => { const x = L.sales.find(r => r.id === 's-3'); x.paidAmount += 30; x.remaining -= 30; x.note = 'A'; });
  const bOps = B.queue.all();
  assert.deepEqual(bOps[0].fields.paidAmount, { $inc: 50 }, 'دریافت دلتا می‌رود');
  await B.push();
  await A.push();
  await A.pull();
  await B.pull();
  const srv = await serverRow(s.shopId, 'sales', 's-3');
  assert.equal(srv.data.paidAmount, 180);
  assert.equal(srv.data.remaining, 820);
  assert.equal(srv.data.note, 'A');
  await sameAsServer(A, s.shopId, 'sales', 's-3', '$inc');
  await sameAsServer(B, s.shopId, 'sales', 's-3', '$inc');
});

test('۴) دستگاهِ سوم پس از هر دو ⇒ op ِ کهنه دست نمی‌خورد و برندهٔ واقعی به همه می‌رسد', async () => {
  const s = await shop('own4', { flag: true });
  const A = device('own4-a', s.token);
  const B = device('own4-b', s.token);
  const C = device('own4-c', s.token);
  await seeded(A, B, 'products', [{ id: 'p-4', name: 'نان', price: 10 }]);
  await C.pull();

  B.write((L) => price(L, 'p-4', 30));
  A.write((L) => price(L, 'p-4', 20));
  C.write((L) => price(L, 'p-4', 40));
  await B.push();
  await A.push();
  await C.push();
  const [page] = await A.pull();
  assert.equal(page.ops.find(o => o.device_id === 'own4-b').fields.price, 30, 'آخرین نویسنده C است ⇒ op ِ B دست نخورد');
  await B.pull();
  await C.pull();
  assert.equal((await serverRow(s.shopId, 'products', 'p-4')).data.price, 40);
  for (const d of [A, B, C]) await sameAsServer(d, s.shopId, 'products', 'p-4', 'سه دستگاه');
});

test('۵) B حذف می‌کند، A (ندیده) پس از آن ویرایش ⇒ ردیف روی سرور زنده است و از دفترِ A نمی‌رود', async () => {
  const s = await shop('own5', { flag: true });
  const A = device('own5-a', s.token);
  const B = device('own5-b', s.token);
  await seeded(A, B, 'products', [{ id: 'p-5', name: 'روغن', price: 100 }]);

  B.write((L) => { L.products = L.products.filter(x => x.id !== 'p-5'); });
  A.write((L) => price(L, 'p-5', 200));
  await B.push();
  await A.push();
  const [page] = await A.pull();
  assert.equal(page.ops.some(o => o.row_id === 'p-5' && o.type === 'delete'), false, 'حذفِ B برای A فرستاده نشد');
  const srv = await serverRow(s.shopId, 'products', 'p-5');
  assert.equal(srv.deleted, false, 'ویرایشِ پس از حذف ردیف را زنده کرد');
  await sameAsServer(A, s.shopId, 'products', 'p-5', 'زنده‌شده');
});

test('۵-ب) مانده: B ِ حذف‌کننده پس از زنده شدن ردیفِ نیمه‌ای می‌گیرد (op ِ ویرایش فقط فیلدهای خودش را دارد)', {
  todo: 'زنده شدنِ ردیف به دستِ دستگاهِ دیگر — جدا از این پرچم؛ ردیفِ کامل لازم است',
}, async () => {
  const s = await shop('own5b', { flag: true });
  const A = device('own5b-a', s.token);
  const B = device('own5b-b', s.token);
  await seeded(A, B, 'products', [{ id: 'p-5b', name: 'روغن', price: 100 }]);
  B.write((L) => { L.products = L.products.filter(x => x.id !== 'p-5b'); });
  A.write((L) => price(L, 'p-5b', 200));
  await B.push();
  await A.push();
  await B.pull();
  await sameAsServer(B, s.shopId, 'products', 'p-5b', 'حذف‌کننده');
});

test('۶) B ویرایش می‌کند، A (ندیده) پس از آن حذف ⇒ «شبح» در دفترِ A نمی‌ماند', async () => {
  for (const flag of [true, false]) {
    const s = await shop(`own6-${flag}`, { flag });
    const A = device('own6-a', s.token);
    const B = device('own6-b', s.token);
    await seeded(A, B, 'products', [{ id: 'p-6', name: 'برنج', price: 100 }]);

    B.write((L) => price(L, 'p-6', 300));
    A.write((L) => { L.products = L.products.filter(x => x.id !== 'p-6'); });
    await B.push();
    await A.push();
    await A.pull();
    await B.pull();
    assert.equal((await serverRow(s.shopId, 'products', 'p-6')).deleted, true);
    await sameAsServer(B, s.shopId, 'products', 'p-6', 'حذف');
    if (flag) await sameAsServer(A, s.shopId, 'products', 'p-6', 'حذف');
    else assert.deepEqual(A.row('products', 'p-6'), { id: 'p-6', price: 300 }, 'رفتارِ امروز (مرجع): شبح');
    if (!flag) continue;
    //  دستگاهِ سومِ بی نوشته روی همین ردیفِ حذف‌شده: پاسخِ با پرچم = بی پرچم (ویرایشِ B و حذفِ A هر دو می‌آیند)
    const fresh = () => h.api('GET', '/api/sync/v1/pull?device_id=own6-c&since=0', { token: s.token, headers: { 'X-App': 'shop' } });
    const on = await fresh();
    assert.ok(on.body.ops.some(o => o.device_id === 'own6-b' && o.row_id === 'p-6' && o.type === 'update'));
    await setFlag(s.shopId, false);
    const off = await fresh();
    await setFlag(s.shopId, true);
    assert.deepEqual(on.body, off.body);
  }
});

test('۷) صفحه‌بندی + پاسخِ گم‌شده + pull ِ تکراری ⇒ همان پاسخ و همان نتیجه؛ بی نوشتهٔ تازه‌تر ⇒ مو‌به‌مو مثلِ بی پرچم', async () => {
  const s = await shop('own7', { flag: true });
  const A = device('own7-a', s.token);
  const B = device('own7-b', s.token);
  const ids = ['p-71', 'p-72', 'p-73', 'p-74', 'p-75'];
  await seeded(A, B, 'products', ids.map((id, i) => ({ id, name: `کالا ${i}`, price: 100 + i })));

  //  B همه را عوض می‌کند (قیمت و نام)، A پس از آن فقط قیمتِ دو تا را
  for (const id of ids) B.write((L) => { const p = L.products.find(x => x.id === id); p.price += 1000; p.name += ' (B)'; });
  A.write((L) => { price(L, 'p-72', 7); price(L, 'p-74', 9); });
  await B.push();
  await A.push();

  //  پاسخِ نخستین صفحه گم می‌شود ⇒ همان since دوباره همان پاسخ را می‌دهد (cursor ِ محلی جلو نرفت)
  const before = A.cursor;
  const [lost] = await A.pull({ limit: 2, lose: true });
  assert.equal(A.cursor, before);
  const pages = await A.pull({ limit: 2 });
  assert.deepEqual(pages[0], lost, 'pull ِ دوباره با همان since = همان پاسخ');
  assert.ok(pages.length >= 3, 'صفحه‌بندی');
  const again = await h.api('GET', `/api/sync/v1/pull?device_id=own7-a&since=${before}&limit=2`,
    { token: s.token, headers: { 'X-App': 'shop' } });
  assert.deepEqual(again.body, lost, 'و باز هم همان، پس از رسیدنِ پاسخِ دوم');

  await B.pull();
  for (const id of ids) {
    await sameAsServer(A, s.shopId, 'products', id, `صفحه‌بندی ${id}`);
    await sameAsServer(B, s.shopId, 'products', id, `صفحه‌بندی ${id}`);
  }
  assert.equal(A.row('products', 'p-72').price, 7);
  assert.equal(A.row('products', 'p-72').name, 'کالا 1 (B)');

  //  دستگاهِ سوم بی هیچ نوشته‌ای: پاسخِ با پرچم = پاسخِ بی پرچم، مو‌به‌مو
  const fresh = (since) => h.api('GET', `/api/sync/v1/pull?device_id=own7-c&since=${since}`,
    { token: s.token, headers: { 'X-App': 'shop' } });
  const on = await fresh(0);
  await setFlag(s.shopId, false);
  const off = await fresh(0);
  await setFlag(s.shopId, true);
  assert.deepEqual(on.body, off.body);
});

test('۸) push ِ تکراری (پاسخِ push گم شد) و باز شدنِ دوبارهٔ برنامه میانِ push و pull ⇒ همان نتیجه', async () => {
  const s = await shop('own8', { flag: true });
  const A = device('own8-a', s.token);
  const B = device('own8-b', s.token);
  await seeded(A, B, 'products', [{ id: 'p-8', name: 'صابون', price: 100 }]);

  B.write((L) => price(L, 'p-8', 300));
  A.write((L) => price(L, 'p-8', 200));
  await B.push();
  await A.push({ lose: true });               // سرور گرفت، پاسخ نرسید ⇒ صف همان
  assert.equal(A.queue.size(), 1);
  const reopened = device('own8-a', s.token, A.disk);   // برنامه بسته و باز شد: همان دیسک
  await reopened.push();                       // «تکراری» ⇒ از صف بیرون
  assert.equal(reopened.queue.size(), 0);
  const again = device('own8-a', s.token, A.disk);
  await again.pull();
  await B.pull();
  assert.equal((await serverRow(s.shopId, 'products', 'p-8')).data.price, 200);
  await sameAsServer(again, s.shopId, 'products', 'p-8', 'تکراری');
  await sameAsServer(B, s.shopId, 'products', 'p-8', 'تکراری');
  assert.equal((await conflictsOf(s.shopId, 'p-8')).length, 1, 'تعارض یک بار، نه دو بار');
});

test('۹) پمپ: همان مسیر، با پرچمِ `all` و پرچمِ خودِ پمپ ⇒ pull ِ همان امروز (هیچ فیلدی برداشته نمی‌شود)', async () => {
  const stamp = { 'x-app-id': 'tohid-pump-app', 'x-app-version': '3.1.186', 'x-app-platform': 'windows' };
  const u = await h.newUser('پمپ‌دار', 'pump');
  const made = await h.post('/api/pump', { name: 'پمپِ آزمون', code: 'own-1' }, { token: u.accessToken, headers: stamp });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const station = made.body.station.id;
  const bound = await h.post('/api/pump/device/bind',
    { device: { uid: `duid-${crypto.randomBytes(4).toString('hex')}`, name: 'کامپیوترِ پمپ', platform: 'windows' } },
    { token: u.accessToken, headers: stamp });
  assert.equal(bound.status, 201, JSON.stringify(bound.body));
  const token = bound.body.deviceToken;
  await setFlag('all', true);
  await setFlag(station, true);
  try {
    const pPush = (dev, ops) => h.post('/api/sync/v1/push', { device_id: dev, schema_version: 1, ops }, { token, headers: stamp });
    const pPull = (dev, since) => h.get(`/api/sync/v1/pull?device_id=${dev}&since=${since}`, { token, headers: stamp });
    const row = `ROWOWN${crypto.randomBytes(4).toString('hex')}`.toUpperCase();
    const entry = (type, fields) => ({ op_id: crypto.randomUUID(), table: 'SafeEntry', row_id: row, type, ts: Date.now(), fields });

    assert.equal((await pPush('pc-1', [entry('insert', { Title: 'الف', Amount: '100', MonthKey: '1405-08' })])).status, 200);
    const base = await pPull('pc-2', 0);
    assert.equal(base.status, 200);
    assert.equal((await pPush('pc-1', [entry('update', { Amount: '300' })])).status, 200);
    assert.equal((await pPush('pc-2', [entry('update', { Amount: '200' })])).status, 200);
    const r = await pPull('pc-2', base.body.cursor);
    assert.equal(r.status, 200);
    const theirs = r.body.ops.filter(o => o.device_id === 'pc-1');
    assert.equal(theirs.length, 1);
    assert.deepEqual(theirs[0].fields, { Amount: '300' }, 'پمپ: op ِ کامل، همان امروز');
  } finally {
    await setFlag('all', false);
  }
});
