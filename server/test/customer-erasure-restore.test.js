'use strict';
/**
 * SEC-10 — پاک‌شدنِ دوبارهٔ مشتریِ پاک‌شده پس از بازگردانیِ کلِ سرور (تصمیمِ صاحب‌کار ۱۴۰۵/۰۸/۲۳ #۱۵: «بله، خودکار»).
 *
 * پشتیبانِ واقعیِ پیش از حذف (`backup.run`) ⇒ حذفِ مشتری (`erase`) ⇒ بازگرداندنِ همان پشتیبان در دیتابیسی **جدا** (همان راهِ
 * تمرینِ بازیابی، `restore-drill.restoreIsolated`: PGlite ِ تازه در حافظه یا دیتابیسِ `shop_drill_*`) ⇒ دادهٔ پاک‌شده برگشته
 * است ⇒ `replay` با دفترِ بیرون از دیتابیس (`lib/erasure-ledger.js`) همان مشتری را دوباره پاک می‌کند و سابقه با همان شناسه.
 * و: مشتریِ دیگر دست نمی‌خورد؛ بارِ دوم هیچ؛ روی دیتابیسِ زنده (بی بازگردانی) هیچ — حتی سفارشِ تازهٔ همان مشتری پس از حذف؛
 * دفتر هیچ شماره/نامی ندارد؛ خطِ بدشکل نادیده؛ بی دفتر (جهش) چیزی پاک نمی‌شود.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const os = require('node:os');
const path = require('node:path');

const DIR = path.join(os.tmpdir(), `shop-erasure-restore-${process.pid}`);
process.env.ERASURE_LEDGER_PATH = path.join(DIR, 'ledger', 'erasure-ledger.jsonl');

const h = require('./helpers');
const config = require('../src/config');
const backup = require('../src/lib/backup');
const drill = require('../src/lib/restore-drill');
const erasure = require('../src/lib/customer-erasure');
const ledger = require('../src/lib/erasure-ledger');
const { query, one, newId, now } = require('../src/db');

const P1 = '+93700555111';
const P2 = '+93700555222';
const NAME1 = 'کریمِ پاک‌شدنی';
let shopId = '';
let seq = 0;

/** تراکنش روی دیتابیسِ بازگردانده (همان شکلِ `db.tx`) */
function txOf(pool) {
  return async (fn) => {
    const c = await pool.connect();
    try {
      await c.query('BEGIN');
      const r = await fn(c);
      await c.query('COMMIT');
      return r;
    } catch (err) {
      await c.query('ROLLBACK').catch(() => {});
      throw err;
    } finally { c.release(); }
  };
}

async function order(phone, name, status = 'delivered') {
  const id = newId('ord');
  const at = now();
  await query(
    `INSERT INTO online_orders (id, shop_id, order_no, client_key, request_hash, source, status, fulfillment,
       customer_name, customer_phone, address, delivery, note, items_count, subtotal_minor, delivery_fee_minor, total_minor,
       created_at, updated_at)
     VALUES ($1,$2,$3,$4,'h','storefront',$5,'delivery',$6,$7,$8::jsonb,'{}'::jsonb,'زنگ بزنید',1,20000,5000,25000,$9,$9)`,
    [id, shopId, ++seq, `ck-${id}`, status, name, phone, JSON.stringify({ landmark: 'مسجدِ سبز', zone: { id: 'z1', feeMinor: 5000 } }), at]
  );
  return id;
}

const rowsOf = (q, phone) => q('SELECT id, customer_name, customer_phone, note, total_minor FROM online_orders WHERE shop_id=$1 AND customer_phone=$2 ORDER BY id', [shopId, phone]);

test.before(async () => {
  await fs.rm(DIR, { recursive: true, force: true });
  await h.start();
  config.backup.dir = path.join(DIR, 'backups');
  config.backup.passphrase = '';
  const u = await h.newUser('صاحبِ حذف و بازگردانی', 'shop');
  const made = await h.post('/api/shop', { name: 'دکانِ بازگردانی' }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  shopId = made.body.shop.id;
});
test.after(async () => { await h.stop(); await fs.rm(DIR, { recursive: true, force: true }); });

test('پشتیبانِ پیش از حذف برگشت ⇒ همان مشتری خودکار دوباره پاک می‌شود؛ دیگری، تکرار و دیتابیسِ زنده دست نمی‌خورند', async () => {
  await order(P1, NAME1);
  await order(P1, NAME1);
  await order(P2, 'مشتریِ ماندنی');
  const before = await backup.run({ kind: 'manual' });

  //  حذف روی دیتابیسِ زنده ⇒ یک خط در دفترِ بیرون از دیتابیس، بی شماره و نام
  const done = await erasure.erase(shopId, { phone: P1 }, { by: 'owner', userId: 'u1' });
  assert.equal(done.counts.orders, 2);
  const text = await fs.readFile(process.env.ERASURE_LEDGER_PATH, 'utf8');
  const lines = text.trim().split('\n');
  assert.equal(lines.length, 1);
  for (const secret of [P1, '700555111', NAME1, 'مسجدِ سبز']) assert.ok(!text.includes(secret), `دفتر «${secret}» دارد`);
  const entry = JSON.parse(lines[0]);
  assert.deepEqual([entry.id, entry.shop, entry.key], [done.erasureId, shopId, ledger.keyOf(P1)]);
  assert.equal(ledger.keyOf('+930700555111'), entry.key, 'همهٔ شکل‌های یک شماره یک کلید');

  //  مشتری پس از حذف دوباره سفارش داد (دیتابیسِ زنده) — هیچ بازپخشی نباید به آن دست بزند
  const fresh = await order(P1, 'کریمِ تازه');
  const liveRun = await erasure.replay();
  assert.deepEqual([liveRun.replayed, liveRun.empty, liveRun.failed], [0, 0, 0], 'زنده: سابقهٔ همان حذف هست');
  assert.deepEqual((await rowsOf((t, p) => query(t, p).then((r) => r.rows), P1)).map((r) => r.id), [fresh]);

  //  بازگردانیِ کلِ سرور از پشتیبانِ پیش از حذف (دیتابیسِ جدا) ⇒ دادهٔ پاک‌شده برگشته است
  const copy = await drill.restoreIsolated(before.file);
  try {
    const q = (t, p) => copy.q(t, p).then((r) => r.rows);
    assert.equal((await rowsOf(q, P1)).length, 2, 'پشتیبانِ کهنه مشتریِ پاک‌شده را برگرداند');
    assert.equal((await q("SELECT 1 FROM audit_logs WHERE target_type='customer_erasure' AND target_id=$1", [done.erasureId])).length, 0);

    const run = txOf(copy.pool);
    const r1 = await erasure.replay({ run });
    assert.deepEqual([r1.entries, r1.replayed, r1.empty, r1.failed], [1, 1, 0, 0]);
    assert.equal((await rowsOf(q, P1)).length, 0, 'شماره دیگر نیست');
    const erased = await q("SELECT customer_name, customer_phone, note, total_minor FROM online_orders WHERE shop_id=$1 AND customer_phone LIKE 'erased:%'", [shopId]);
    assert.equal(erased.length, 2);
    for (const o of erased) {
      assert.deepEqual([o.customer_name, o.note, Number(o.total_minor)], ['', '', 25000], 'مبلغ می‌ماند، دادهٔ شخصی نه');
      assert.equal(o.customer_phone, `erased:${done.erasureId}`, 'همان شناسهٔ حذف');
    }
    const p2 = await rowsOf(q, P2);
    assert.deepEqual(p2.map((o) => o.customer_name), ['مشتریِ ماندنی'], 'مشتریِ دیگر دست نخورد');
    const mark = await q("SELECT actor_type, detail FROM audit_logs WHERE target_type='customer_erasure' AND target_id=$1", [done.erasureId]);
    assert.equal(mark.length, 1);
    assert.equal(mark[0].actor_type, 'system');
    assert.ok(!JSON.stringify(mark[0].detail).includes(P1));

    //  بارِ دوم (بالا آمدنِ بعدی) ⇒ هیچ؛ و سفارشِ تازهٔ همان مشتری پس از بازگردانی پاک نمی‌شود
    await copy.q(
      `INSERT INTO online_orders (id, shop_id, order_no, client_key, request_hash, source, status, fulfillment, customer_name, customer_phone,
         address, delivery, note, items_count, subtotal_minor, delivery_fee_minor, total_minor, created_at, updated_at)
       VALUES ('ord-after', $1, 9999, 'ck-after', 'h', 'storefront', 'new', 'pickup', 'کریمِ پس از بازگردانی', $2, '{}'::jsonb, '{}'::jsonb, '', 1, 1, 0, 1, 1, 1)`,
      [shopId, P1]);
    const r2 = await erasure.replay({ run });
    assert.deepEqual([r2.replayed, r2.empty, r2.failed], [0, 0, 0]);
    assert.deepEqual((await rowsOf(q, P1)).map((o) => o.id), ['ord-after']);
  } finally {
    await copy.close();
  }
});

test('مشتری‌ای که دادهٔ او در پشتیبان نیست ⇒ فقط سابقه (بی پاک کردن)، تا سفارشِ بعدیِ او هرگز بازپخش نشود؛ خطِ بدشکل نادیده', async () => {
  const empty = await backup.run({ kind: 'manual' });
  const P3 = '+93700555333';
  await order(P3, 'مشتریِ دیرآمده');
  const done = await erasure.erase(shopId, { phone: P3 }, { by: 'owner', userId: 'u1' });
  await fs.appendFile(process.env.ERASURE_LEDGER_PATH, 'نه-json\n{"v":1,"id":"bad id"}\n');
  const copy = await drill.restoreIsolated(empty.file);
  try {
    const run = txOf(copy.pool);
    const r = await erasure.replay({ run });
    assert.equal(r.bad, 2);
    //  حذفِ P1 (آزمونِ پیشین) سابقه‌اش در همین پشتیبان هست ⇒ بازپخش نمی‌شود و سفارشِ تازهٔ P1 می‌ماند؛ P3 در پشتیبان نیست ⇒ فقط سابقه
    assert.deepEqual([r.replayed, r.empty, r.failed], [0, 1, 0], JSON.stringify(r));
    assert.equal((await copy.q('SELECT 1 FROM online_orders WHERE shop_id=$1 AND customer_phone=$2', [shopId, P1])).rows.length, 1);
    const q = (t, p) => copy.q(t, p).then((x) => x.rows);
    assert.equal((await q("SELECT 1 FROM audit_logs WHERE target_type='customer_erasure' AND target_id=$1", [done.erasureId])).length, 1);
    //  همان مشتری پس از بازگردانی سفارش می‌دهد ⇒ بازپخشِ بعدی به آن دست نمی‌زند
    await copy.q(
      `INSERT INTO online_orders (id, shop_id, order_no, client_key, request_hash, source, status, fulfillment, customer_name, customer_phone,
         address, delivery, note, items_count, subtotal_minor, delivery_fee_minor, total_minor, created_at, updated_at)
       VALUES ('ord-p3', $1, 9998, 'ck-p3', 'h', 'storefront', 'new', 'pickup', 'دیرآمده', $2, '{}'::jsonb, '{}'::jsonb, '', 1, 1, 0, 1, 1, 1)`,
      [shopId, P3]);
    const again = await erasure.replay({ run });
    assert.deepEqual([again.replayed, again.empty], [0, 0]);
    assert.equal((await q('SELECT 1 FROM online_orders WHERE id=$1 AND customer_phone=$2', ['ord-p3', P3])).length, 1);
  } finally {
    await copy.close();
  }
});

test('دفتر نوشته نشد ⇒ حذف هم نمی‌نشیند (همه یا هیچ)؛ پیش‌نمایش دفتر نمی‌نویسد', async () => {
  const P4 = '+93700555444';
  await order(P4, 'مشتریِ چهارم');
  const lines = async () => (await fs.readFile(process.env.ERASURE_LEDGER_PATH, 'utf8')).trim().split('\n').length;
  const n = await lines();
  const dry = await erasure.erase(shopId, { phone: P4 }, { dryRun: true, by: 'owner' });
  assert.equal(dry.counts.orders, 1);
  assert.equal(await lines(), n, 'پیش‌نمایش بی خطِ دفتر');
  //  جای دفتر نوشتنی نیست (پوشه جای پرونده) ⇒ حذف شکست می‌خورد و داده می‌ماند
  const real = process.env.ERASURE_LEDGER_PATH;
  process.env.ERASURE_LEDGER_PATH = path.join(DIR, 'blocked');
  await fs.mkdir(process.env.ERASURE_LEDGER_PATH, { recursive: true });
  try {
    await assert.rejects(() => erasure.erase(shopId, { phone: P4 }, { by: 'owner' }));
  } finally {
    process.env.ERASURE_LEDGER_PATH = real;
  }
  assert.equal((await one('SELECT count(*)::int AS n FROM online_orders WHERE shop_id=$1 AND customer_phone=$2', [shopId, P4])).n, 1);
});

/* ── «فقط ایمیل» × بازپخش (ادغامِ e-rows-47e و e-rows-50d) ──────────────────────────────────────────────────────────── */

const E1 = 'karim.erase@example.com';
const E2 = 'stays@example.com';

async function orderE(email, { phone = '', name = 'مشتریِ ایمیلی', customerId = null, status = 'delivered' } = {}) {
  const id = await order(phone, name, status);
  await query('UPDATE online_orders SET customer_email=$2, store_customer_id=$3 WHERE id=$1', [id, email, customerId]);
  return id;
}
async function chatOf(key, name) {
  const id = newId('cv');
  await query(`INSERT INTO shop_chat_conversations (id, shop_id, customer_phone, customer_name, created_at, updated_at)
               VALUES ($1,$2,$3,$4,$5,$5)`, [id, shopId, key, name, now()]);
  return id;
}
async function areaOf(key) {
  await query(`INSERT INTO storefront_area_requests (id, shop_id, phone, cell_lat, cell_lng, created_at, updated_at)
               VALUES ($1,$2,$3,34.5,69.2,$4,$4)`, [newId('ar'), shopId, key, now()]);
}
const auditsOf = (q, id) => q("SELECT actor_type FROM audit_logs WHERE target_type='customer_erasure' AND target_id=$1", [id]);

test('«فقط ایمیل»: حذف با ایمیل (بی شماره) ⇒ خطِ دفتر با کلیدِ ایمیل ⇒ پس از بازگردانی همان آدم با ایمیل پیدا و دوباره پاک می‌شود؛ همان شناسه، یک سابقه', async () => {
  const contact = '+93700555666';   //  شمارهٔ تماسِ پیک روی یکی از سفارش‌ها (تأییدنشده)
  const o1 = await orderE(E1, { name: NAME1 });
  const o2 = await orderE(E1, { name: NAME1, phone: contact });
  const keep = await orderE(E2, { name: 'ایمیلیِ ماندنی' });
  const cv = await chatOf(E1, NAME1);
  const cvKeep = await chatOf(E2, 'ایمیلیِ ماندنی');
  await areaOf(E1);
  await areaOf(E2);
  const before = await backup.run({ kind: 'manual' });

  const lines0 = (await fs.readFile(process.env.ERASURE_LEDGER_PATH, 'utf8').catch(() => '')).trim().split('\n').filter(Boolean).length;
  const done = await erasure.erase(shopId, { email: E1 }, { by: 'owner', userId: 'u1' });
  assert.equal(done.mode, 'email');
  assert.equal(done.counts.orders, 2);
  assert.equal(done.counts.conversations, 1);
  assert.equal(done.counts.areaRequests, 1);
  const text = await fs.readFile(process.env.ERASURE_LEDGER_PATH, 'utf8');
  const lines = text.trim().split('\n');
  assert.equal(lines.length, lines0 + 1, 'یک خطِ تازه');
  for (const secret of [E1, 'karim.erase', NAME1, contact, '700555666']) assert.ok(!text.includes(secret), `دفتر «${secret}» دارد`);
  const entry = JSON.parse(lines[lines.length - 1]);
  assert.equal(entry.id, done.erasureId);
  assert.equal(entry.ekey, ledger.emailKeyOf(E1));
  assert.equal(ledger.emailKeyOf(' Karim.Erase@Example.COM '), entry.ekey, 'ایمیل پیش از HMAC یکدست می‌شود');
  assert.notEqual(ledger.emailKeyOf(E1), ledger.keyOf(E1), 'کلیدِ ایمیل و کلیدِ شماره دو دامنهٔ جدا');
  assert.equal(entry.key, '', 'حذفِ ایمیلی کلیدِ شماره ندارد');

  const copy = await drill.restoreIsolated(before.file);
  try {
    const q = (t, p) => copy.q(t, p).then((r) => r.rows);
    assert.equal((await q('SELECT 1 FROM online_orders WHERE shop_id=$1 AND customer_email=$2', [shopId, E1])).length, 2, 'پشتیبانِ کهنه برگرداند');
    assert.equal((await auditsOf(q, done.erasureId)).length, 0);
    const run = txOf(copy.pool);
    const r1 = await erasure.replay({ run });
    assert.deepEqual([r1.replayed, r1.empty, r1.failed], [1, 0, 0], JSON.stringify(r1));
    const gone = await q('SELECT id, customer_name, customer_email, customer_phone, note, total_minor FROM online_orders WHERE id = ANY($1::text[]) ORDER BY id', [[o1, o2]]);
    assert.equal(gone.length, 2);
    for (const o of gone) {
      assert.deepEqual([o.customer_name, o.customer_email, o.note, Number(o.total_minor)], ['', '', '', 25000]);
      assert.ok(o.customer_phone === '' || o.customer_phone === `erased:${done.erasureId}`, o.customer_phone);
    }
    assert.equal((await q('SELECT 1 FROM online_orders WHERE customer_phone=$1', [contact])).length, 0, 'شمارهٔ تماسِ همان سفارش هم رفت');
    assert.deepEqual((await q('SELECT customer_phone, customer_name FROM shop_chat_conversations WHERE id=$1', [cv]))[0],
      { customer_phone: `erased:${cv}`, customer_name: '' });
    assert.equal((await q('SELECT 1 FROM storefront_area_requests WHERE shop_id=$1 AND phone=$2', [shopId, E1])).length, 0);
    //  دیگری دست نخورد
    assert.deepEqual((await q('SELECT customer_email, customer_name FROM online_orders WHERE id=$1', [keep]))[0], { customer_email: E2, customer_name: 'ایمیلیِ ماندنی' });
    assert.equal((await q('SELECT customer_phone FROM shop_chat_conversations WHERE id=$1', [cvKeep]))[0].customer_phone, E2);
    assert.equal((await q('SELECT 1 FROM storefront_area_requests WHERE shop_id=$1 AND phone=$2', [shopId, E2])).length, 1);
    const mark = await auditsOf(q, done.erasureId);
    assert.deepEqual(mark.map((m) => m.actor_type), ['system'], 'همان شناسه، یک سابقه');

    //  بارِ دوم ⇒ هیچ؛ سفارشِ تازهٔ همان ایمیل پس از بازگردانی دست نمی‌خورد؛ سابقه تکرار نمی‌شود
    await copy.q(
      `INSERT INTO online_orders (id, shop_id, order_no, client_key, request_hash, source, status, fulfillment, customer_name, customer_phone,
         customer_email, address, delivery, note, items_count, subtotal_minor, delivery_fee_minor, total_minor, created_at, updated_at)
       VALUES ('ord-e-after', $1, 9997, 'ck-e-after', 'h', 'storefront', 'new', 'pickup', 'کریمِ تازه', '', $2, '{}'::jsonb, '{}'::jsonb, '', 1, 1, 0, 1, 1, 1)`,
      [shopId, E1]);
    const r2 = await erasure.replay({ run });
    assert.deepEqual([r2.replayed, r2.empty, r2.failed], [0, 0, 0]);
    assert.deepEqual((await q('SELECT id FROM online_orders WHERE customer_email=$1', [E1])).map((o) => o.id), ['ord-e-after']);
    assert.equal((await auditsOf(q, done.erasureId)).length, 1, 'بی سابقهٔ تکراری');
  } finally {
    await copy.close();
  }
});

test('«فقط ایمیل»: «پاک کردنِ دادهٔ من» ِ خودِ مشتری (حساب با ایمیل) ⇒ بازپخش با ایمیل و حساب؛ حسابی که پس از پشتیبان ایمیلش عوض شد هم با شناسهٔ حساب پیدا می‌شود', async () => {
  const at = now();
  const cid = newId('sc');
  const OLD = 'old.mail@example.com';
  const NEW = 'new.mail@example.com';
  await query('INSERT INTO store_customers (id, email, phone, name, created_at, updated_at) VALUES ($1,$2,NULL,$3,$4,$4)', [cid, OLD, 'حسابِ ایمیلی', at]);
  const oOld = await orderE(OLD, { customerId: cid, name: 'حسابِ ایمیلی' });
  const cvOld = await chatOf(OLD, 'حسابِ ایمیلی');
  const before = await backup.run({ kind: 'manual' });
  //  پس از پشتیبان: ایمیلِ حساب عوض شد و سفارشِ تازه با ایمیلِ تازه
  await query('UPDATE store_customers SET email=$2 WHERE id=$1', [cid, NEW]);
  const oNew = await orderE(NEW, { customerId: cid, name: 'حسابِ ایمیلی' });
  const cust = await one('SELECT id, email, phone FROM store_customers WHERE id=$1', [cid]);
  const done = await erasure.erase(shopId, erasure.targetOfCustomer(cust), { by: 'customer' });
  assert.equal(done.mode, 'account');
  assert.equal(done.counts.orders, 2, 'سفارشِ ایمیلِ پیشین هم با حساب');
  const entry = (await ledger.read()).entries.find((e) => e.id === done.erasureId);
  assert.deepEqual([entry.ekey, entry.key, entry.customer], [ledger.emailKeyOf(NEW), '', cid]);

  const copy = await drill.restoreIsolated(before.file);
  try {
    const q = (t, p) => copy.q(t, p).then((r) => r.rows);
    assert.equal((await q('SELECT email FROM store_customers WHERE id=$1', [cid]))[0].email, OLD, 'پشتیبان ایمیلِ پیشین را دارد');
    const r = await erasure.replay({ run: txOf(copy.pool) });
    assert.deepEqual([r.replayed, r.empty, r.failed], [1, 0, 0], JSON.stringify(r));
    assert.deepEqual((await q('SELECT customer_email, customer_name FROM online_orders WHERE id=$1', [oOld]))[0], { customer_email: '', customer_name: '' });
    assert.equal((await q('SELECT customer_phone FROM shop_chat_conversations WHERE id=$1', [cvOld]))[0].customer_phone, `erased:${cvOld}`);
    assert.equal((await q('SELECT 1 FROM online_orders WHERE id=$1', [oNew])).length, 0, 'سفارشِ پس از پشتیبان در پشتیبان نیست');
    assert.equal((await q('SELECT 1 FROM store_customers WHERE id=$1', [cid])).length, 1, 'حسابِ سراسری می‌ماند (فقط با حذفِ حساب)');
    assert.deepEqual((await auditsOf(q, done.erasureId)).map((m) => m.actor_type), ['system']);
  } finally {
    await copy.close();
  }
});

test('دفترِ کهنه: خطِ v1 (فقط کلیدِ شماره، پیش از «فقط ایمیل») همان‌طور خوانده و بازپخش می‌شود', async () => {
  const P6 = '+93700555777';
  const o = await order(P6, 'مشتریِ دفترِ کهنه');
  const before = await backup.run({ kind: 'manual' });
  //  خطی که کدِ پیشین (v1) نوشته بود — بی `ekey`
  const id = newId('era');
  const v1 = { v: 1, id, shop: shopId, at: now(), key: ledger.keyOf(P6), orders: [], customer: '' };
  assert.deepEqual(ledger.parse(JSON.stringify(v1)), { id, shop: shopId, at: v1.at, key: v1.key, ekey: '', orders: [], customer: '' });
  assert.equal(ledger.parse(JSON.stringify({ ...v1, key: '' })), null, 'v1 بی کلید و بی سفارش همان «بدشکل»');
  assert.equal(ledger.parse(JSON.stringify({ ...v1, key: '', ekey: ledger.emailKeyOf(E1) })), null, 'v1 کلیدِ ایمیل را نمی‌شناسد');
  await fs.appendFile(process.env.ERASURE_LEDGER_PATH, `${JSON.stringify(v1)}\n`);
  const copy = await drill.restoreIsolated(before.file);
  try {
    const q = (t, p) => copy.q(t, p).then((r) => r.rows);
    const r = await erasure.replay({ run: txOf(copy.pool) });
    assert.deepEqual([r.replayed, r.failed], [1, 0], JSON.stringify(r));
    assert.deepEqual((await q('SELECT customer_phone, customer_name FROM online_orders WHERE id=$1', [o]))[0], { customer_phone: `erased:${id}`, customer_name: '' });
    assert.deepEqual((await auditsOf(q, id)).map((m) => m.actor_type), ['system']);
  } finally {
    await copy.close();
  }
});
