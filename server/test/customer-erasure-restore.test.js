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
