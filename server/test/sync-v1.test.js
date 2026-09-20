'use strict';
/**
 * VILL3N Sync v1 — بندِ ۲۰ پرامپت، با معیارِ پذیرشِ ۲۰.۱۰.
 *
 * ── قانونِ طلایی ────────────────────────────────────────────────────
 * «هرگز کلِ داده به سرور نمی‌رود، فقط **تغییر**. اگر یک اسم را پاک کنم،
 *  یک رکوردِ چندصدبایتی می‌رود، نه کلِ لیست.»
 *
 * هر بندِ ۲۰.۱۰ این‌جا یک سنجهٔ عددی دارد:
 *   • پاک کردنِ یک اسم = درخواستِ زیرِ یک کیلوبایت
 *   • سه روز آفلاین ⇒ همه‌چیز به ترتیب و بی گم شدن
 *   • دو دستگاه، دو فیلدِ یک ردیف ⇒ هیچ فیلدی گم نمی‌شود
 *   • دو فروشِ هم‌زمان ⇒ موجودی درست کم می‌شود (دلتا)
 *   • گوشیِ نو ⇒ Snapshot و ادامه از همان‌جا
 *   • برنامهٔ قدیمی با سرورِ جدید و برعکس ⇒ نه گم‌شدن نه Crash
 */
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';

const test = require('node:test');
const assert = require('node:assert/strict');
const zlib = require('node:zlib');
const h = require('./helpers');
const { one, query } = require('../src/db');
const syncV1 = require('../src/lib/sync-v1');

const DEV_A = 'dev-a-1';
const DEV_B = 'dev-b-1';

let owner;          // صاحبِ دکان، با توکن
let shopId;
let opSeq = 0;

/** ULIDِ ساده: مرتب بر اساس زمان، یکتا — همان چیزی که برنامه می‌سازد. */
const opId = () => `01J${String(Date.now()).slice(-9)}${String(++opSeq).padStart(5, '0')}`;

const push = (token, body) => h.api('POST', '/api/sync/v1/push', {
  token, body, headers: { 'X-App': 'shop', 'X-Device': body.device_id },
});
const pull = (token, deviceId, since = 0) => h.api('GET',
  `/api/sync/v1/pull?device_id=${deviceId}&since=${since}`, { token, headers: { 'X-App': 'shop' } });

function op(overrides) {
  return {
    op_id: opId(), ts: Date.now(), table: 'products', row_id: 'p-1',
    type: 'update', fields: {}, ...overrides,
  };
}

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
  owner = await h.newUser('صاحبِ دکان', 'shop');
  const shop = await h.api('POST', '/api/shop', { token: owner.accessToken, body: { name: 'دکانِ آزمون' } });
  shopId = shop.body?.shop?.id || shop.body?.id;
  //  توکنِ تازه، حالا که عضویت هست
  owner = { ...owner, ...(await h.api('POST', '/api/auth/login', {
    body: { identifier: owner.email, password: owner.password, app: 'shop',
      device: { deviceId: DEV_A, name: 'A', platform: 'test' } },
  })).body };
});
test.after(async () => { await h.stop(); });

/* ------------------------------------------------------- قانونِ طلایی */

test('پاک کردنِ یک اسم = یک درخواستِ زیرِ یک کیلوبایت، و حذف نرم است', async () => {
  const insert = op({ type: 'insert', row_id: 'c-88', table: 'debtors', fields: { name: 'احمد', phone: '0700000000' } });
  await push(owner.accessToken, { device_id: DEV_A, schema_version: 2, ops: [insert] });

  const body = { device_id: DEV_A, schema_version: 2, ops: [op({ type: 'delete', row_id: 'c-88', table: 'debtors' })] };
  const bytes = Buffer.byteLength(JSON.stringify(body), 'utf8');
  assert.ok(bytes < 1024, `بدنهٔ حذف ${bytes} بایت شد؛ باید زیرِ ۱۰۲۴ باشد`);

  const r = await push(owner.accessToken, body);
  assert.equal(r.status, 200);
  assert.equal(r.body.results[0].status, 'applied');

  const row = await one(
    `SELECT deleted_at, data FROM sync_rows WHERE app='shop' AND account_id=$1 AND table_name='debtors' AND row_id='c-88'`,
    [shopId]
  );
  assert.ok(row, 'ردیف باید بماند — حذفِ نرم');
  assert.ok(Number(row.deleted_at) > 0, 'حذف باید مهر بخورد، نه نابود کند');
});

/* -------------------------------------------------- سه روز آفلاین */

test('سه روز آفلاین ⇒ همهٔ opها به ترتیب می‌نشینند و هیچ‌کدام گم نمی‌شود', async () => {
  const ops = [];
  for (let i = 0; i < 120; i++) {
    ops.push(op({
      type: i === 0 ? 'insert' : 'update',
      table: 'products', row_id: 'p-offline',
      fields: i === 0 ? { name: 'کالا', price: 100 } : { price: 100 + i },
      ts: Date.now() - (3 * 86400_000) + i * 1000,
    }));
  }
  const r = await push(owner.accessToken, { device_id: DEV_A, schema_version: 2, ops });
  assert.equal(r.status, 200);
  assert.equal(r.body.applied, 120, 'هر ۱۲۰ باید بنشیند');
  assert.equal(r.body.results.filter((x) => x.status === 'applied').length, 120);

  const row = await one(
    `SELECT data FROM sync_rows WHERE app='shop' AND account_id=$1 AND table_name='products' AND row_id='p-offline'`,
    [shopId]
  );
  const data = typeof row.data === 'string' ? JSON.parse(row.data) : row.data;
  assert.equal(data.price, 219, 'آخرین قیمت باید برنده باشد');
});

/* --------------------------------------------- تعارضِ سطحِ فیلد */

test('دو دستگاه، دو فیلدِ یک ردیف ⇒ هیچ فیلدی گم نمی‌شود', async () => {
  await push(owner.accessToken, {
    device_id: DEV_A, schema_version: 2,
    ops: [op({ type: 'insert', table: 'debtors', row_id: 'c-two', fields: { name: 'قدیم', phone: '0700' } })],
  });
  await push(owner.accessToken, {
    device_id: DEV_A, schema_version: 2,
    ops: [op({ table: 'debtors', row_id: 'c-two', fields: { name: 'نامِ تازه' } })],
  });
  await push(owner.accessToken, {
    device_id: DEV_B, schema_version: 2,
    ops: [op({ table: 'debtors', row_id: 'c-two', fields: { phone: '0799' } })],
  });

  const row = await one(
    `SELECT data FROM sync_rows WHERE app='shop' AND account_id=$1 AND table_name='debtors' AND row_id='c-two'`,
    [shopId]
  );
  const data = typeof row.data === 'string' ? JSON.parse(row.data) : row.data;
  assert.equal(data.name, 'نامِ تازه');
  assert.equal(data.phone, '0799', 'فیلدِ دستگاهِ دوم نباید فیلدِ اولی را پاک کند');
});

test('دو دستگاه، یک فیلد ⇒ آخری برنده و بازنده در دفترِ تعارض می‌ماند', async () => {
  await push(owner.accessToken, {
    device_id: DEV_A, schema_version: 2,
    ops: [op({ type: 'insert', table: 'debtors', row_id: 'c-clash', fields: { name: 'اول' } })],
  });
  await push(owner.accessToken, {
    device_id: DEV_A, schema_version: 2,
    ops: [op({ table: 'debtors', row_id: 'c-clash', fields: { name: 'از دستگاهِ الف' } })],
  });
  await push(owner.accessToken, {
    device_id: DEV_B, schema_version: 2,
    ops: [op({ table: 'debtors', row_id: 'c-clash', fields: { name: 'از دستگاهِ ب' } })],
  });

  const row = await one(
    `SELECT data FROM sync_rows WHERE app='shop' AND account_id=$1 AND table_name='debtors' AND row_id='c-clash'`,
    [shopId]
  );
  const data = typeof row.data === 'string' ? JSON.parse(row.data) : row.data;
  assert.equal(data.name, 'از دستگاهِ ب', 'ترتیبِ رسیدن به سرور داور است');

  const conflicts = await h.api('GET', '/api/sync/v1/conflicts',
    { token: owner.accessToken, headers: { 'X-App': 'shop' } });
  assert.equal(conflicts.status, 200);
  const mine = conflicts.body.conflicts.filter((c) => c.row_id === 'c-clash');
  assert.ok(mine.length >= 1, 'نسخهٔ بازنده باید ثبت شده باشد');
  assert.equal(mine[0].field, 'name');
  assert.ok(JSON.stringify(mine[0]).includes('از دستگاهِ الف'), 'متنِ بازنده باید قابلِ بازگرداندن باشد');
});

/* --------------------------------------------------------- دلتا */

test('دو فروشِ هم‌زمان از دو دستگاه ⇒ موجودی درست کم می‌شود', async () => {
  await push(owner.accessToken, {
    device_id: DEV_A, schema_version: 2,
    ops: [op({ type: 'insert', table: 'products', row_id: 'p-stock', fields: { name: 'روغن', stock: 10 } })],
  });
  await push(owner.accessToken, {
    device_id: DEV_A, schema_version: 2,
    ops: [op({ table: 'products', row_id: 'p-stock', fields: { stock: { $inc: -3 } } })],
  });
  await push(owner.accessToken, {
    device_id: DEV_B, schema_version: 2,
    ops: [op({ table: 'products', row_id: 'p-stock', fields: { stock: { $inc: -4 } } })],
  });

  const row = await one(
    `SELECT data FROM sync_rows WHERE app='shop' AND account_id=$1 AND table_name='products' AND row_id='p-stock'`,
    [shopId]
  );
  const data = typeof row.data === 'string' ? JSON.parse(row.data) : row.data;
  assert.equal(data.stock, 3, '۱۰ − ۳ − ۴ = ۳؛ با «مقدارِ تازه» یکی از دو فروش گم می‌شد');
});

/* ------------------------------------------------ تکراری و Pull */

test('op با شناسهٔ تکراری دوباره اعمال نمی‌شود', async () => {
  const dup = op({ type: 'insert', table: 'products', row_id: 'p-dup', fields: { name: 'یکی', stock: 5 } });
  const first = await push(owner.accessToken, { device_id: DEV_A, schema_version: 2, ops: [dup] });
  assert.equal(first.body.results[0].status, 'applied');
  const second = await push(owner.accessToken, { device_id: DEV_A, schema_version: 2, ops: [dup] });
  assert.equal(second.body.results[0].status, 'duplicate', 'ارسالِ دوباره پس از قطعِ اینترنت نباید دوباره بنشیند');
});

test('Pull فقط opهای دستگاه‌های دیگر را می‌دهد', async () => {
  const before = await pull(owner.accessToken, DEV_A);
  const cursor = before.body.cursor;

  await push(owner.accessToken, {
    device_id: DEV_B, schema_version: 2,
    ops: [op({ type: 'insert', table: 'products', row_id: 'p-from-b', fields: { name: 'از ب' } })],
  });
  await push(owner.accessToken, {
    device_id: DEV_A, schema_version: 2,
    ops: [op({ type: 'insert', table: 'products', row_id: 'p-from-a', fields: { name: 'از الف' } })],
  });

  const got = await pull(owner.accessToken, DEV_A, cursor);
  const rows = got.body.ops.map((o) => o.row_id);
  assert.ok(rows.includes('p-from-b'), 'opِ دستگاهِ دیگر باید بیاید');
  assert.ok(!rows.includes('p-from-a'), 'opِ خودِ دستگاه برنمی‌گردد');
});

/* ------------------------------------------------------ Snapshot */

test('گوشیِ نو: Snapshot همهٔ حالِ امروز را می‌دهد و فشرده می‌آید', async () => {
  const res = await fetch(`${h.base()}/api/sync/v1/snapshot`, {
    headers: { Authorization: `Bearer ${owner.accessToken}`, 'X-App': 'shop', 'Accept-Encoding': 'gzip' },
  });
  assert.equal(res.status, 200);
  const buf = Buffer.from(await res.arrayBuffer());
  //  undici خودش باز می‌کند؛ اگر نکرد، ما باز می‌کنیم
  let text;
  try { text = JSON.parse(buf.toString('utf8')) ? buf.toString('utf8') : ''; }
  catch { text = zlib.gunzipSync(buf).toString('utf8'); }
  const snap = JSON.parse(text);
  assert.ok(snap.cursor >= 0);
  assert.ok(snap.schema_version >= 1, 'عکس باید بگوید با کدام نسخه ساخته شده');
  //  عکس جدول‌به‌جدول است: { tables: { products: [{id, data}], … } }
  const names = Object.values(snap.tables).flat().map((r) => r.id);
  assert.ok(names.includes('p-stock'), 'عکس باید ردیف‌های زنده را داشته باشد');
  assert.ok(!names.includes('c-88'), 'ردیفِ حذف‌شده در عکسِ دستگاهِ نو نمی‌آید');
  assert.equal(snap.rows, names.length, 'شمارِ اعلام‌شده با آن‌چه آمده یکی است');
});

/* ------------------------------------------------ نسخهٔ Schema */

test('برنامهٔ جدیدتر از سرور ⇒ ۴۲۶ و هیچ چیزی اعمال نمی‌شود', async () => {
  const r = await push(owner.accessToken, {
    device_id: DEV_A, schema_version: 99,
    ops: [op({ type: 'insert', table: 'products', row_id: 'p-future', fields: { name: 'آینده' } })],
  });
  assert.equal(r.status, 426);
  assert.equal(r.body.error, 'UPGRADE_REQUIRED');
  const row = await one(
    `SELECT 1 AS x FROM sync_rows WHERE app='shop' AND account_id=$1 AND row_id='p-future'`, [shopId]
  );
  assert.equal(row, null, 'هیچ opی از دستهٔ ردشده نباید نشسته باشد');
});

test('برنامهٔ قدیمی‌تر ⇒ پذیرفته می‌شود و «نسخهٔ تازه هست» می‌گیرد', async () => {
  const r = await push(owner.accessToken, {
    device_id: DEV_A, schema_version: 1,
    ops: [op({ type: 'insert', table: 'sales', row_id: 's-old', fields: { total: 500 } })],
  });
  assert.equal(r.status, 200);
  assert.equal(r.body.applied, 1, 'مشتریِ نسخهٔ قدیم نباید بیرون بماند');
  assert.equal(r.body.upgrade_available, true);
});

/* -------------------------------------------- مرزِ حساب و بخش */

test('جدولِ ناشناخته رد می‌شود', async () => {
  const r = await push(owner.accessToken, {
    device_id: DEV_A, schema_version: 2,
    ops: [op({ type: 'insert', table: 'users', row_id: 'x', fields: { role: 'admin' } })],
  });
  assert.equal(r.body.results[0].status, 'rejected', 'نامِ بیرونِ فهرستِ سفید نباید بنشیند');
});

test('توکنِ دکان نمی‌تواند در دفترِ پمپ بنویسد', async () => {
  const r = await h.api('POST', '/api/sync/v1/push', {
    token: owner.accessToken,
    headers: { 'X-App': 'pump', 'X-Device': DEV_A },
    body: { device_id: DEV_A, schema_version: 1, ops: [] },
  });
  assert.ok(r.status === 401 || r.status === 403, `انتظار ۴۰۱/۴۰۳ بود، ${r.status} آمد`);
});

test('بی توکن ⇒ ۴۰۱', async () => {
  const r = await h.api('POST', '/api/sync/v1/push', {
    headers: { 'X-App': 'shop', 'X-Device': DEV_A },
    body: { device_id: DEV_A, schema_version: 2, ops: [] },
  });
  assert.equal(r.status, 401);
});

/* ----------------------------------------- بازگرداندنِ حذف و تعارض */

test('ویرایش پس از حذف ⇒ ردیف زنده می‌شود', async () => {
  await push(owner.accessToken, {
    device_id: DEV_A, schema_version: 2,
    ops: [op({ type: 'insert', table: 'debtors', row_id: 'c-undel', fields: { name: 'حسن' } })],
  });
  await push(owner.accessToken, {
    device_id: DEV_A, schema_version: 2,
    ops: [op({ type: 'delete', table: 'debtors', row_id: 'c-undel' })],
  });
  await push(owner.accessToken, {
    device_id: DEV_B, schema_version: 2,
    ops: [op({ table: 'debtors', row_id: 'c-undel', fields: { name: 'حسنِ تازه' } })],
  });

  const row = await one(
    `SELECT deleted_at, data FROM sync_rows WHERE app='shop' AND account_id=$1 AND table_name='debtors' AND row_id='c-undel'`,
    [shopId]
  );
  assert.ok(!row.deleted_at, 'ویرایش پس از حذف باید ردیف را برگرداند');
  const data = typeof row.data === 'string' ? JSON.parse(row.data) : row.data;
  assert.equal(data.name, 'حسنِ تازه');
});

test('حالِ دستگاه در /status دیده می‌شود', async () => {
  const r = await h.api('GET', `/api/sync/v1/status?device_id=${DEV_A}`,
    { token: owner.accessToken, headers: { 'X-App': 'shop' } });
  assert.equal(r.status, 200);
  assert.ok(r.body.head > 0, 'شمارندهٔ دفتر باید جلو رفته باشد');
  assert.ok(Array.isArray(r.body.tables) || r.body.device, JSON.stringify(r.body).slice(0, 200));
});

/* ------------------------------------------------- گزارشِ خطا */

test('گزارشِ خطا ثبت می‌شود و راز در آن نمی‌ماند', async () => {
  const r = await h.api('POST', '/api/errors', {
    token: owner.accessToken,
    headers: { 'X-App': 'shop', 'X-Device': DEV_A, 'X-App-Version': '2.1.0' },
    body: {
      message: 'خطا برای ahmad@gmail.com با توکنِ abcdefghijklmnopqrstuvwxyz0123456789',
      stack: 'at somewhere', log_tail: 'شماره 0700123456',
    },
  });
  assert.equal(r.status, 202);
  const row = await one('SELECT * FROM client_errors ORDER BY at DESC, id DESC LIMIT 1');
  assert.ok(!row.message.includes('ahmad@gmail.com'), 'ایمیل باید پوشانده شود');
  assert.ok(!row.message.includes('abcdefghijklmnopqrstuvwxyz0123456789'), 'توکن باید پوشانده شود');
  assert.ok(!row.log_tail.includes('0700123456'), 'شماره باید پوشانده شود');
  assert.equal(row.app, 'shop');
});
