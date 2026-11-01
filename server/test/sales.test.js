'use strict';
/**
 * داشبوردِ فروش، پرداخت‌ها و رسید — بخشِ ۱۱.۴ی پرامپت.
 *
 * ── خواستهٔ صاحب سامانه ────────────────────────────────────────────
 *   «درآمد امروز / ماه / سال به تفکیک برنامه و ارز، نمودار رشد…
 *    فهرست همهٔ اشتراک‌ها با فیلتر… اشتراک‌های رو به پایان (۷ روز
 *    مانده) با دکمهٔ یادآوری ایمیلی… بدهی مشتری‌ها (اشتراک داده شده،
 *    پرداخت نشده)… صدور و چاپ رسید و فاکتور فارسی… تبدیل به دائمی.»
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) ⛔ جدولِ پرداخت `sub_payments` است، نه `payments` — آن یکی از
 *      روزِ اول مالِ **دادهٔ خودِ دکان** است و هم‌نامی‌شان یک بار
 *      مهاجرت را شکست.
 *   ۲) درآمد به تفکیکِ بخش **و ارز** جمع می‌شود؛ دالرِ پمپ با افغانیِ
 *      دکان جمع نمی‌شود.
 *   ۳) رسید صفحهٔ چاپیِ فارسی است (نه PDF) و فقط رسیدِ حسابِ خودِ آدم
 *      به او داده می‌شود.
 *   ۴) بدهی = قیمتِ خودِ اشتراک منهای پرداخت‌های همان اشتراک.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');

const DAY = 24 * 60 * 60 * 1000;

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
});
test.after(async () => { await h.stop(); });

async function adminToken() {
  const r = await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' });
  return r.body.token;
}

async function shopOwner(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { ...u, shopId: made.body.shop.id };
}

async function pumpOwner(name, code) {
  const u = await h.newUser(name, 'pump');
  const made = await h.post('/api/pump', { name: `پمپِ ${name}`, code }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { ...u, stationId: made.body.station.id };
}

/* ══════════════════════════════════════════════════════════════════
   ۰) جدولِ هم‌نام — باگی که یک بار مهاجرت را شکست
   ══════════════════════════════════════════════════════════════════ */

test('⛔ `payments` مالِ دادهٔ خودِ دکان است؛ پرداختِ اشتراک در `sub_payments` می‌نشیند', async () => {
  const shopData = await query(
    `SELECT column_name FROM information_schema.columns WHERE table_name='payments'`);
  const cols = shopData.rows.map(r => r.column_name);
  assert.ok(cols.includes('shop_id') && cols.includes('data'),
    '`payments` همان جدولِ همگام‌سازیِ دکان است');
  assert.ok(!cols.includes('tenant_kind'), 'پرداختِ اشتراک این‌جا نیست');

  const subPay = await query(
    `SELECT column_name FROM information_schema.columns WHERE table_name='sub_payments'`);
  const c2 = subPay.rows.map(r => r.column_name);
  for (const want of ['app', 'tenant_kind', 'tenant_id', 'amount', 'currency', 'method', 'receipt_no']) {
    assert.ok(c2.includes(want), `ستونِ ${want} باید باشد`);
  }
});

/* ══════════════════════════════════════════════════════════════════
   ۱) پرداخت و رسید
   ══════════════════════════════════════════════════════════════════ */

test('ثبت، ویرایش و حذفِ پرداخت — و شمارهٔ رسیدِ خودکار', async () => {
  const t = await adminToken();
  const u = await shopOwner('پرداخت‌کننده');
  const sub = await h.post('/api/admin/subscriptions', { shopId: u.shopId, plan: 'm1', days: 30 }, { token: t });

  const made = await h.post('/api/admin/payments', {
    app: 'shop', tenantId: u.shopId, subscriptionId: sub.body.subscription.id,
    amount: 1500, currency: 'AFN', method: 'cash', note: 'نقدِ دستی',
  }, { token: t });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  assert.ok(made.body.payment.receiptNo, 'شمارهٔ رسید خودکار ساخته می‌شود');
  assert.equal(made.body.payment.tenantName, 'دکانِ پرداخت‌کننده', 'نامِ حساب هم می‌آید');
  assert.equal(made.body.payment.ownerEmail, u.email);

  const id = made.body.payment.id;
  const edited = await h.put(`/api/admin/payments/${id}`, { amount: 1800, method: 'hawala' }, { token: t });
  assert.equal(edited.status, 200);
  assert.equal(edited.body.payment.amount, 1800);
  assert.equal(edited.body.payment.method, 'hawala');

  const list = await h.get('/api/admin/payments?app=shop', { token: t });
  assert.equal(list.status, 200);
  assert.ok(list.body.payments.some(p => p.id === id));

  const gone = await h.del(`/api/admin/payments/${id}`, { token: t });
  assert.equal(gone.status, 200);
  const after = await h.get('/api/admin/payments?app=shop', { token: t });
  assert.ok(!after.body.payments.some(p => p.id === id), 'پرداختِ حذف‌شده در فهرست نیست');
});

test('⛔ پرداخت بی سابقه نمی‌ماند: نوشتنِ سابقه شکست ⇒ ثبت، ویرایش و حذف همه برمی‌گردند', async () => {
  const t = await adminToken();
  const u = await shopOwner('سابقهٔ پرداخت');
  const made = await h.post('/api/admin/payments', { app: 'shop', tenantId: u.shopId, amount: 1500 }, { token: t });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const id = made.body.payment.id;
  const rowOf = async () => {
    const r = await one('SELECT amount, deleted FROM sub_payments WHERE id=$1', [id]);
    return { amount: Number(r.amount), deleted: r.deleted };
  };
  const logs = async (action) => (await one(
    'SELECT COUNT(*)::int AS n FROM audit_logs WHERE action=$1 AND target_id=$2', [action, id])).n;
  const NOTE = 'پرداختِ بی‌سابقه';
  const orphans = async () => (await one('SELECT COUNT(*)::int AS n FROM sub_payments WHERE note=$1', [NOTE])).n;
  assert.equal(await logs('admin.payment_created'), 1);

  //  نوشتنِ سابقهٔ پرداخت در خودِ دیتابیس شکست می‌خورد
  await query(`CREATE OR REPLACE FUNCTION test_audit_down() RETURNS trigger LANGUAGE plpgsql AS $$
    BEGIN IF NEW.action LIKE 'admin.payment_%' THEN RAISE EXCEPTION 'audit down'; END IF; RETURN NEW; END $$`);
  await query('CREATE TRIGGER test_audit_down BEFORE INSERT ON audit_logs FOR EACH ROW EXECUTE FUNCTION test_audit_down()');
  try {
    const del = await h.del(`/api/admin/payments/${id}`, { token: t, body: { reason: 'رسیدِ تکراری' } });
    assert.equal(del.status, 500, JSON.stringify(del.body));
    assert.deepEqual(await rowOf(), { amount: 1500, deleted: false }, 'حذف بی سابقه برگشت');

    const put = await h.put(`/api/admin/payments/${id}`, { amount: 1800 }, { token: t });
    assert.equal(put.status, 500, JSON.stringify(put.body));
    assert.deepEqual(await rowOf(), { amount: 1500, deleted: false }, 'ویرایش بی سابقه برگشت');

    const post = await h.post('/api/admin/payments', { app: 'shop', tenantId: u.shopId, amount: 900, note: NOTE }, { token: t });
    assert.equal(post.status, 500, JSON.stringify(post.body));
    assert.equal(await orphans(), 0, 'ثبت بی سابقه برگشت');
  } finally {
    await query('DROP TRIGGER IF EXISTS test_audit_down ON audit_logs');
    await query('DROP FUNCTION IF EXISTS test_audit_down()');
  }
  assert.equal(await logs('admin.payment_deleted'), 0);
  assert.equal(await logs('admin.payment_updated'), 0);

  //  سابقه برگشت ⇒ همان حذف این بار با سابقه‌اش می‌نشیند
  const del = await h.del(`/api/admin/payments/${id}`, { token: t, body: { reason: 'رسیدِ تکراری' } });
  assert.equal(del.status, 200, JSON.stringify(del.body));
  assert.deepEqual(await rowOf(), { amount: 1500, deleted: true });
  assert.equal(await logs('admin.payment_deleted'), 1);
});

/* ── SEC-API-09: ثبتِ پرداخت یک بار، و شمارهٔ رسیدِ یکتا (docs/security/data-fa.md) ─────────────── */
//  مبلغ‌ها کوچک‌اند تا درآمدِ «امروز» ِ آزمونِ «پرداختِ پارسال…» زیرِ ۹۹۹۹ بماند

const payWithKey = (t, key, body) => h.api('POST', '/api/admin/payments', {
  token: t, body, headers: key ? { 'Idempotency-Key': key } : {},
});

test('SEC-API-09: همان Idempotency-Key ⇒ یک پرداخت و همان پاسخ؛ بدنهٔ دیگر با همان کلید ⇒ ۴۰۹', async () => {
  const t = await adminToken();
  const u = await shopOwner('تکرار‌کننده');
  const body = { app: 'shop', tenantId: u.shopId, amount: 13, method: 'cash' };
  const first = await payWithKey(t, 'pay-key-0001-aaaa', body);
  assert.equal(first.status, 201, JSON.stringify(first.body));
  const again = await payWithKey(t, 'pay-key-0001-aaaa', body);
  assert.equal(again.status, 201);
  assert.equal(again.headers.get('idempotent-replayed'), 'true');
  assert.equal(again.body.payment.id, first.body.payment.id);
  assert.equal(again.body.payment.receiptNo, first.body.payment.receiptNo);
  const other = await payWithKey(t, 'pay-key-0001-aaaa', { ...body, amount: 99 });
  assert.equal(other.status, 409);
  assert.equal(other.body.error.code, 'idempotency_key_reused');
  const n = await one('SELECT COUNT(*)::int AS n FROM sub_payments WHERE tenant_id=$1', [u.shopId]);
  assert.equal(n.n, 1, 'فقط یک پرداخت');
});

test('SEC-API-09: ده ثبتِ هم‌زمان با یک کلید (قطعِ شبکه و تکرار) ⇒ یک پرداخت', async () => {
  const t = await adminToken();
  const u = await shopOwner('شبکهٔ لرزان');
  const body = { app: 'shop', tenantId: u.shopId, amount: 21, method: 'hawala' };
  const all = await Promise.all(Array.from({ length: 10 }, () => payWithKey(t, 'pay-key-0002-bbbb', body)));
  assert.ok(all.every((r) => r.status === 201), all.map((r) => r.status).join(','));
  assert.equal(new Set(all.map((r) => r.body.payment.id)).size, 1);
  const n = await one('SELECT COUNT(*)::int AS n FROM sub_payments WHERE tenant_id=$1', [u.shopId]);
  assert.equal(n.n, 1);
});

test('SEC-API-09: ده ثبتِ هم‌زمانِ بی شمارهٔ رسید ⇒ ده شمارهٔ رسیدِ یکتا', async () => {
  const t = await adminToken();
  const u = await shopOwner('صف‌دار');
  const all = await Promise.all(Array.from({ length: 10 }, (_, i) =>
    payWithKey(t, '', { app: 'shop', tenantId: u.shopId, amount: 1 + i })));
  assert.ok(all.every((r) => r.status === 201), all.map((r) => `${r.status}:${JSON.stringify(r.body).slice(0, 80)}`).join(' '));
  const nos = all.map((r) => r.body.payment.receiptNo);
  assert.equal(new Set(nos).size, 10, `شماره‌ها: ${nos.join(' ')}`);
  for (const no of nos) assert.match(no, /^\d{4}-\d{5,}$/);
});

test('SEC-API-09: شمارهٔ رسیدِ دستیِ تکراری ⇒ ۴۰۹، در ثبت و در ویرایش', async () => {
  const t = await adminToken();
  const u = await shopOwner('رسیدِ دستی');
  const a = await payWithKey(t, '', { app: 'shop', tenantId: u.shopId, amount: 5, receiptNo: 'R-SEC-0042' });
  assert.equal(a.status, 201, JSON.stringify(a.body));
  const dup = await payWithKey(t, '', { app: 'shop', tenantId: u.shopId, amount: 6, receiptNo: 'R-SEC-0042' });
  assert.equal(dup.status, 409, JSON.stringify(dup.body));
  assert.equal(dup.body.error.code, 'receipt_no_taken');
  //  شمارهٔ خودکارِ دیگری را هم دستی نمی‌شود دوباره زد
  const auto = await payWithKey(t, '', { app: 'shop', tenantId: u.shopId, amount: 7 });
  const dupAuto = await payWithKey(t, '', { app: 'shop', tenantId: u.shopId, amount: 8, receiptNo: auto.body.payment.receiptNo });
  assert.equal(dupAuto.status, 409);
  //  ویرایش به شمارهٔ رسیدِ پرداختِ دیگر
  const ed = await h.put(`/api/admin/payments/${auto.body.payment.id}`, { receiptNo: 'R-SEC-0042' }, { token: t });
  assert.equal(ed.status, 409, JSON.stringify(ed.body));
  assert.equal(ed.body.error.code, 'receipt_no_taken');
  //  همان شمارهٔ خودش ⇒ بی خطا
  const same = await h.put(`/api/admin/payments/${a.body.payment.id}`, { receiptNo: 'R-SEC-0042', amount: 4 }, { token: t });
  assert.equal(same.status, 200, JSON.stringify(same.body));
  const n = await one(`SELECT COUNT(*)::int AS n FROM sub_payments WHERE receipt_no='R-SEC-0042'`);
  assert.equal(n.n, 1);
});

test('SEC-API-09: ده ثبتِ هم‌زمان با یک شمارهٔ رسیدِ دستی ⇒ یکی ۲۰۱، بقیه ۴۰۹ (نه ۵۰۰، نه تکراری)', async () => {
  const t = await adminToken();
  const u = await shopOwner('رسیدِ هم‌زمان');
  const all = await Promise.all(Array.from({ length: 10 }, (_, i) =>
    payWithKey(t, '', { app: 'shop', tenantId: u.shopId, amount: 1 + i, receiptNo: 'R-SEC-RACE-1' })));
  const codes = all.map((r) => r.status).sort();
  assert.equal(codes.filter((c) => c === 201).length, 1, codes.join(','));
  assert.ok(all.filter((r) => r.status !== 201).every((r) => r.status === 409 && r.body.error.code === 'receipt_no_taken'), codes.join(','));
  const n = await one(`SELECT COUNT(*)::int AS n FROM sub_payments WHERE receipt_no='R-SEC-RACE-1'`);
  assert.equal(n.n, 1);
});

test('SEC-API-09: ویرایشِ شمارهٔ رسیدِ ردیف‌های کهنه زیرِ قیدِ یکتاست ⇒ ده ویرایشِ هم‌زمان به یک شماره، یکی ۲۰۰ و بقیه ۴۰۹', async () => {
  const t = await adminToken();
  const u = await shopOwner('رسیدِ کهنه');
  //  ردیف‌های پیش از مهاجرتِ ۱۶۷ (receipt_unique=false)، یکی‌شان با شمارهٔ تکراریِ کهنه
  const ids = [];
  for (let i = 0; i < 10; i++) {
    const r = await payWithKey(t, '', { app: 'shop', tenantId: u.shopId, amount: 10 + i, receiptNo: `R-SEC-OLD-${i}` });
    assert.equal(r.status, 201, JSON.stringify(r.body));
    ids.push(r.body.payment.id);
  }
  await query(`UPDATE sub_payments SET receipt_unique=false WHERE id = ANY($1)`, [ids]);
  await query(`UPDATE sub_payments SET receipt_no='R-SEC-OLD-0' WHERE id=$1`, [ids[1]]);

  //  ویرایشِ بی تغییرِ شماره روی تکراریِ کهنه ⇒ بی خطا، و ردیف کهنه می‌ماند (قید فقط روی شمارهٔ تازه)
  const keep = await h.put(`/api/admin/payments/${ids[1]}`, { amount: 99 }, { token: t });
  assert.equal(keep.status, 200, JSON.stringify(keep.body));
  assert.equal((await one('SELECT receipt_unique FROM sub_payments WHERE id=$1', [ids[1]])).receipt_unique, false);

  //  عوض کردنِ شماره ⇒ receipt_unique=true، تا ایندکسِ یکتا پشتش باشد
  const moved = await h.put(`/api/admin/payments/${ids[2]}`, { receiptNo: 'R-SEC-OLD-MOVED' }, { token: t });
  assert.equal(moved.status, 200, JSON.stringify(moved.body));
  assert.equal((await one('SELECT receipt_unique FROM sub_payments WHERE id=$1', [ids[2]])).receipt_unique, true);

  //  هم‌زمان: هر ده ردیفِ کهنه به یک شمارهٔ تازه ⇒ فقط یکی
  const all = await Promise.all(ids.map((id) => h.put(`/api/admin/payments/${id}`, { receiptNo: 'R-SEC-STALE-RACE' }, { token: t })));
  const codes = all.map((r) => r.status).sort();
  assert.equal(codes.filter((c) => c === 200).length, 1, codes.join(','));
  assert.ok(all.filter((r) => r.status !== 200).every((r) => r.status === 409 && r.body.error.code === 'receipt_no_taken'), codes.join(','));
  const n = await one(`SELECT COUNT(*)::int AS n FROM sub_payments WHERE receipt_no='R-SEC-STALE-RACE'`);
  assert.equal(n.n, 1);
});

test('روشِ ناشناخته و ارزِ ناشناخته پذیرفته نمی‌شوند', async () => {
  const t = await adminToken();
  const u = await shopOwner('بدپرداخت');
  for (const body of [
    { app: 'shop', tenantId: u.shopId, amount: 100, method: 'bitcoin' },
    { app: 'shop', tenantId: u.shopId, amount: 100, currency: 'EUR' },
    { app: 'shop', tenantId: 'shp_nobody', amount: 100 },
  ]) {
    const r = await h.post('/api/admin/payments', body, { token: t });
    assert.ok(r.status >= 400, `${JSON.stringify(body)} باید رد شود (${r.status})`);
  }
});

test('رسید صفحهٔ چاپیِ فارسی است — نه PDF، و مشتری فقط رسیدِ خودش را می‌بیند', async () => {
  const t = await adminToken();
  const a = await shopOwner('رسیدی');
  const b = await shopOwner('کنجکاو');
  const sub = await h.post('/api/admin/subscriptions', { shopId: a.shopId, plan: 'm1', days: 30 }, { token: t });
  const pay = await h.post('/api/admin/payments', {
    app: 'shop', tenantId: a.shopId, subscriptionId: sub.body.subscription.id, amount: 2500, method: 'cash',
  }, { token: t });
  const id = pay.body.payment.id;

  const asAdmin = await h.get(`/api/admin/payments/${id}/receipt`, { token: t });
  assert.equal(asAdmin.status, 200);
  assert.ok(asAdmin.headers.get('content-type').includes('text/html'), 'HTML است، نه PDF');
  const html = asAdmin.body.raw;
  assert.ok(html.includes('dir="rtl"'), 'راست‌به‌چپ');
  assert.ok(html.includes('رسیدِ پرداخت'));
  assert.ok(html.includes('window.print()'), 'چاپ با خودِ مرورگر — عمدی');
  assert.ok(html.includes('دکانِ رسیدی'));

  const mine = await h.get(`/api/portal/payments/${id}/receipt`, { token: a.accessToken });
  assert.equal(mine.status, 200);
  const theirs = await h.get(`/api/portal/payments/${id}/receipt`, { token: b.accessToken });
  assert.equal(theirs.status, 404, '⛔ رسیدِ دیگری داده نمی‌شود');
});

/* ══════════════════════════════════════════════════════════════════
   ۲) درآمد
   ══════════════════════════════════════════════════════════════════ */

test('درآمد به تفکیکِ بخش و ارز — دالرِ پمپ با افغانیِ دکان جمع نمی‌شود', async () => {
  const t = await adminToken();
  const s = await shopOwner('درآمدِ دکان');
  const p = await pumpOwner('درآمدِ پمپ', 'PMPS1');

  //  ⚠️ آزمون‌های بالاتر هم پرداخت ثبت کرده‌اند، پس **اختلاف** سنجیده
  //  می‌شود نه عددِ مطلق — وگرنه افزودنِ یک آزمونِ تازه این یکی را
  //  می‌شکند بی آن‌که چیزی خراب شده باشد.
  const before = (await h.get('/api/admin/sales/summary', { token: t })).body;
  const was = (period, app, cur) => (before.revenue[period][app][cur] || 0);

  await h.post('/api/admin/payments', { app: 'shop', tenantId: s.shopId, amount: 1000, currency: 'AFN' }, { token: t });
  await h.post('/api/admin/payments', { app: 'shop', tenantId: s.shopId, amount: 500, currency: 'AFN' }, { token: t });
  await h.post('/api/admin/payments', { app: 'pump', tenantId: p.stationId, amount: 120, currency: 'USD' }, { token: t });

  const r = await h.get('/api/admin/sales/summary', { token: t });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  const got = (period, app, cur) => (r.body.revenue[period][app][cur] || 0);

  assert.equal(got('today', 'shop', 'AFN') - was('today', 'shop', 'AFN'), 1500);
  assert.equal(got('today', 'pump', 'USD') - was('today', 'pump', 'USD'), 120);
  assert.equal(r.body.revenue.today.shop.USD, undefined, '⛔ ارزها با هم جمع نمی‌شوند');
  assert.equal(r.body.revenue.today.pump.AFN, undefined, '⛔ و بخش‌ها هم با هم جمع نمی‌شوند');
  assert.equal(got('month', 'shop', 'AFN') - was('month', 'shop', 'AFN'), 1500);
  assert.equal(got('year', 'pump', 'USD') - was('year', 'pump', 'USD'), 120);

  //  نمودارِ رشد: دوازده ماه، آخرینش ماهِ جاری
  assert.equal(r.body.series.length, 12);
  const last = r.body.series[11];
  const lastWas = before.series[11];
  assert.equal((last.shop.AFN || 0) - (lastWas.shop.AFN || 0), 1500);
  assert.equal((last.pump.USD || 0) - (lastWas.pump.USD || 0), 120);
  assert.equal(last.payments - lastWas.payments, 3);

  assert.ok(r.body.counts.shop, 'شمارِ اشتراک‌ها هم می‌آید');
  assert.ok(r.body.counts.pump);
});

test('پرداختِ پارسال در «امروز» و «ماه» نمی‌آید', async () => {
  const t = await adminToken();
  const s = await shopOwner('کهنه');
  const old = await h.post('/api/admin/payments',
    { app: 'shop', tenantId: s.shopId, amount: 9999, currency: 'AFN', paidAt: now() - 400 * DAY }, { token: t });
  assert.equal(old.status, 201);
  const r = await h.get('/api/admin/sales/summary', { token: t });
  const todayAfn = r.body.revenue.today.shop.AFN || 0;
  assert.ok(todayAfn < 9999, 'پرداختِ کهنه در درآمدِ امروز نیست');
});

/* ══════════════════════════════════════════════════════════════════
   ۳) اشتراک‌ها، رو به پایان، بدهی
   ══════════════════════════════════════════════════════════════════ */

test('فهرستِ اشتراک‌ها با فیلترِ بخش، وضعیت و شهر', async () => {
  const t = await adminToken();
  const a = await shopOwner('کابلی');
  await query('UPDATE users SET city=$2 WHERE id=$1', [a.user.id, 'کابل']);
  const b = await shopOwner('هراتی');
  await query('UPDATE users SET city=$2 WHERE id=$1', [b.user.id, 'هرات']);
  await h.post('/api/admin/subscriptions', { shopId: a.shopId, plan: 'm1', days: 30 }, { token: t });
  await h.post('/api/admin/subscriptions', { shopId: b.shopId, plan: 'm1', days: 30 }, { token: t });

  const all = await h.get('/api/admin/sales/subscriptions', { token: t });
  assert.equal(all.status, 200);
  assert.ok(all.body.subscriptions.length >= 2);

  const kabul = await h.get('/api/admin/sales/subscriptions?city=' + encodeURIComponent('کابل'), { token: t });
  assert.equal(kabul.body.subscriptions.length, 1);
  assert.equal(kabul.body.subscriptions[0].tenantId, a.shopId);
  assert.equal(kabul.body.subscriptions[0].app, 'shop');
  assert.ok(kabul.body.subscriptions[0].planTitle, 'عنوانِ پلن هم می‌آید');

  const onlyPump = await h.get('/api/admin/sales/subscriptions?app=pump', { token: t });
  assert.ok(onlyPump.body.subscriptions.every(s => s.app === 'pump'));
});

test('رو به پایان: فقط نزدیک‌ها — و دکمهٔ یادآوری واقعاً می‌فرستد', async () => {
  const t = await adminToken();
  const soon = await shopOwner('نزدیکِ پایان');
  const far = await shopOwner('دورِ پایان');
  await h.post('/api/admin/subscriptions', { shopId: soon.shopId, plan: 'm1', days: 4 }, { token: t });
  await h.post('/api/admin/subscriptions', { shopId: far.shopId, plan: 'm1', days: 120 }, { token: t });

  const r = await h.get('/api/admin/sales/expiring?days=7&app=shop', { token: t });
  assert.equal(r.status, 200);
  const ids = r.body.expiring.map(x => x.shop_id || x.shopId || x.tenantId);
  assert.ok(ids.includes(soon.shopId));
  assert.ok(!ids.includes(far.shopId));

  const before = (await h.get('/api/me/notices', { token: soon.accessToken })).body.notices.length;
  const sent = await h.post('/api/admin/sales/expiring/remind',
    { days: 7, app: 'shop', channels: ['inapp'] }, { token: t });
  assert.equal(sent.status, 200, JSON.stringify(sent.body));
  assert.ok(sent.body.noticeId, 'یادآوری از مرکزِ اعلان می‌رود، پس گزارش دارد');
  assert.ok(sent.body.recipients >= 1);

  const after = (await h.get('/api/me/notices', { token: soon.accessToken })).body.notices.length;
  assert.ok(after > before, 'یادآوری واقعاً رسید');

  const rep = await h.get(`/api/admin/notices/${sent.body.noticeId}/report`, { token: t });
  assert.ok(rep.body.deliveries.length >= 1, 'گزارشِ هر گیرنده هست');
});

test('بدهی = قیمتِ خودِ اشتراک منهای پرداخت‌های همان اشتراک', async () => {
  const t = await adminToken();
  const u = await shopOwner('بدهکار');
  const sub = await h.post('/api/admin/subscriptions', { shopId: u.shopId, plan: 'm1', days: 30 }, { token: t });
  const price = Number(sub.body.subscription.price);
  assert.ok(price > 0, 'قیمتِ روزِ خرید باید نشسته باشد');

  const before = await h.get('/api/admin/sales/debts?app=shop', { token: t });
  const mine = before.body.debts.find(d => d.tenantId === u.shopId);
  assert.ok(mine, 'اشتراکِ پرداخت‌نشده بدهی است');
  assert.equal(mine.debt, price);

  await h.post('/api/admin/payments', {
    app: 'shop', tenantId: u.shopId, subscriptionId: sub.body.subscription.id, amount: price,
  }, { token: t });

  const after = await h.get('/api/admin/sales/debts?app=shop', { token: t });
  assert.ok(!after.body.debts.some(d => d.tenantId === u.shopId), 'پرداختِ کامل بدهی را می‌بندد');
});

/* ══════════════════════════════════════════════════════════════════
   ۴) تبدیل به دائمی
   ══════════════════════════════════════════════════════════════════ */

test('تبدیل به دائمی: «دائمی ✓» بی شمارشِ روز، و در تاریخچه', async () => {
  const t = await adminToken();
  const u = await shopOwner('دائمی');
  const sub = await h.post('/api/admin/subscriptions', { shopId: u.shopId, plan: 'm1', days: 30 }, { token: t });
  const id = sub.body.subscription.id;

  const done = await h.post(`/api/admin/subscriptions/${id}/permanent`, {}, { token: t });
  assert.equal(done.status, 200, JSON.stringify(done.body));
  assert.equal(done.body.permanent, true);

  const view = await h.get('/api/portal/me', { token: u.accessToken });
  const m = view.body.memberships.find(x => x.app === 'shop');
  assert.equal(m.subscription.permanent, true);
  assert.equal(m.subscription.daysLeft, null, '⛔ دائمی روز نمی‌شمارد');
  assert.equal(m.subscription.label, 'دائمی ✓');
  assert.equal(m.subscription.color, 'green');

  const hist = await one(
    `SELECT * FROM subscription_history WHERE subscription_id=$1 AND action='permanent'`, [id]);
  assert.ok(hist, 'ردیفِ «دائمی» در تاریخچه');
});

/* ══════════════════════════════════════════════════════════════════
   ۵) نشانیِ دانلود، شهر، و مرزِ دسترسی
   ══════════════════════════════════════════════════════════════════ */

test('نشانیِ دانلود و آخرین نسخه از پنل می‌آید و `/api/downloads` باز است', async () => {
  const t = await adminToken();
  const saved = await h.put('/api/admin/downloads',
    { app: 'shop', url: 'https://example.test/shop.apk', version: '2.9.0', notes: 'نسخهٔ تازه' }, { token: t });
  assert.equal(saved.status, 200, JSON.stringify(saved.body));

  //  ⛔ بی توکن هم باز است — برنامه‌ای که هنوز وارد نشده باید بتواند به‌روز شود
  const open = await h.get('/api/downloads?app=shop');
  assert.equal(open.status, 200);
  assert.equal(open.body.downloads[0].version, '2.9.0');
  assert.equal(open.body.downloads[0].url, 'https://example.test/shop.apk');
});

test('شهرِ مشتری از پنل نوشته می‌شود و فیلترِ فروش می‌بیندش', async () => {
  const t = await adminToken();
  const u = await shopOwner('شهری');
  await h.post('/api/admin/subscriptions', { shopId: u.shopId, plan: 'm1', days: 30 }, { token: t });
  const saved = await h.put(`/api/admin/users/${u.user.id}/city`, { city: 'قندهار' }, { token: t });
  assert.equal(saved.status, 200);
  const r = await h.get('/api/admin/sales/subscriptions?city=' + encodeURIComponent('قندهار'), { token: t });
  assert.equal(r.body.subscriptions.length, 1);
  assert.equal(r.body.subscriptions[0].tenantId, u.shopId);
});

test('همهٔ مسیرهای فروش بی توکنِ مدیر بسته‌اند', async () => {
  for (const p of ['/api/admin/sales/summary', '/api/admin/sales/subscriptions',
    '/api/admin/sales/expiring', '/api/admin/sales/debts', '/api/admin/payments']) {
    const r = await h.get(p);
    assert.ok(r.status === 401 || r.status === 403, `${p} باید بسته باشد (${r.status})`);
  }
  //  و توکنِ یک مشتری هم مدیر نیست
  const u = await shopOwner('غیرمدیر');
  const r = await h.get('/api/admin/sales/summary', { token: u.accessToken });
  assert.ok(r.status === 401 || r.status === 403);
});
