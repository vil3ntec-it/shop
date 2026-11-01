'use strict';
/**
 * PAY-20 / RPT-12 — گزارشِ خودکار برای صاحبِ دکان (`lib/owner-report.js`، `routes/owner-report.js`).
 *
 *   - هستهٔ گزارشِ سرور بایت‌به‌بایت همان `license/report-core.js` است.
 *   - ریاضیِ خلاصه روی ردیف‌های ثابت: فروش، تخفیف، برگشتیِ فاکتورِ دیروز، لغوشده، روشِ پرداخت، پرفروش‌ها.
 *   - مرزِ روز و منطقه (کابل +۴:۳۰): ساعتِ انتخابی، صبح ⇒ دیروز، هفتگی، مهلتِ جبران؛ و در پایگاه: ردیفِ ۲۳:۵۹ و
 *     ۰۰:۰۱ ِ محلی، ردیفِ پس از ساعتِ گزارش، ردیفِ بی `createdAt`.
 *   - تکرار و هم‌زمانی ⇒ یک گزارش و یک تحویل در هر کانال (UNIQUE (shop_id, report_date)).
 *   - فقط صاحب: مدیر و کارمند ۴۰۳ `owner_only` و GET ِ بی عدد؛ گیرندهٔ اعلان فقط صاحب.
 *   - پرچم خاموش ⇒ `{enabled:false}`، ۴۰۳ `owner_report_disabled`، کارگر هیچ ردیف و اعلانی نمی‌سازد.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const h = require('./helpers');
const { query, many, newId, now } = require('../src/db');
const rep = require('../src/lib/owner-report');

const ZONE = 'Asia/Kabul';
const MIN = 60_000;
const HOUR = 60 * MIN;
//  نیمه‌شبِ محلیِ ۲۰۲۶-۱۰-۳۱ در کابل (۴:۳۰ پیش از نیمه‌شبِ UTC)
const MIDNIGHT = Date.UTC(2026, 9, 30, 19, 30);
const local = (hh, mm = 0) => MIDNIGHT + hh * HOUR + mm * MIN;
let admin = '';
let pushed = [];

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  admin = (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
  assert.ok(admin);
  require('../src/lib/push').setDeliver((row, msg) => { pushed.push({ row, msg }); });
});
test.after(async () => { require('../src/lib/push').setDeliver(null); rep.stop(); await h.stop(); });

async function setFlag(scope, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key: rep.FLAG_KEY, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

async function shop(label, { on = true } = {}) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { ...u, token: u.accessToken, shopId: made.body.shop.id };
  if (on) await setFlag(o.shopId, true);
  return o;
}

async function member(s, role, label) {
  const u = await h.newUser(label, 'shop');
  await query(`INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at) VALUES ($1,$2,$3,$4,'active',$5,$5)`,
    [newId('mem'), s.shopId, u.user.id, role, now()]);
  return { ...u, token: u.accessToken };
}

async function row(s, table, id, data) {
  await query(
    `INSERT INTO sync_rows (app, account_kind, account_id, table_name, row_id, data, created_at, updated_at)
     VALUES ('shop','shop',$1,$2,$3,$4::jsonb,1,1)`, [s.shopId, table, id, JSON.stringify(data)]
  );
}
async function sale(s, id, finalTotal, createdAt, extra = {}) {
  await row(s, 'sales', id, { total: finalTotal, discount: 0, finalTotal, paymentMethod: 'cash', status: 'completed', createdAt, date: '', ...extra });
}

const reportsOf = (s) => many('SELECT * FROM owner_reports WHERE shop_id=$1 ORDER BY report_date', [s.shopId]);
const deliveriesOf = (userId) => many(`SELECT * FROM notice_deliveries WHERE user_id=$1 ORDER BY channel`, [userId]);
const putSettings = (s, body, token = s.token) => h.put('/api/shop/owner-report', body, { token });

test('هستهٔ گزارشِ سرور عیناً همان هستهٔ وب است', () => {
  const root = path.join(__dirname, '..', '..');
  assert.equal(fs.readFileSync(path.join(__dirname, '..', 'src', 'lib', 'report-core.js'), 'utf8'),
    fs.readFileSync(path.join(root, 'license', 'report-core.js'), 'utf8'));
});

test('ریاضیِ خلاصه روی ردیف‌های ثابت: تخفیف، لغو، برگشتیِ فاکتورِ دیروز، روشِ پرداخت، پرفروش‌ها', () => {
  const raw = {
    sales: [
      { id: 's0', total: 200, discount: 0, finalTotal: 200, paymentMethod: 'cash', status: 'completed', createdAt: local(-4) },
      { id: 's1', total: 1000, discount: 100, finalTotal: 900, paymentMethod: 'cash', status: 'completed', createdAt: local(9) },
      { id: 's2', total: 500, discount: 0, finalTotal: 500, paymentMethod: 'credit', status: 'completed', createdAt: local(12) },
      { id: 's3', total: 300, discount: 0, finalTotal: 300, paymentMethod: 'cash', status: 'cancelled', createdAt: local(13) },
    ],
    saleItems: [
      { id: 'i0', saleId: 's0', productId: 'p1', name: 'نان', quantity: 1, totalPrice: 200 },
      { id: 'i1', saleId: 's1', productId: 'p1', name: 'نان', quantity: 2, totalPrice: 600 },
      { id: 'i2', saleId: 's1', productId: 'p2', name: 'شیر', quantity: 1, totalPrice: 400 },
      { id: 'i3', saleId: 's2', productId: 'p2', name: 'شیر', quantity: 1, totalPrice: 500 },
      { id: 'i4', saleId: 's3', productId: 'p1', name: 'نان', quantity: 1, totalPrice: 300 },
    ],
    returns: [
      { id: 'r1', saleId: 's0', saleItemId: 'i0', productId: 'p1', quantity: 1, amount: 200, createdAt: local(14) },
      { id: 'r2', saleId: 's1', saleItemId: 'i2', productId: 'p2', quantity: 0.25, amount: 100, createdAt: local(15) },
    ],
    products: [{ id: 'p1', name: 'نانِ تازه' }, { id: 'p2', name: 'شیر' }],
  };
  const w = { frequency: 'daily', from: '2026-10-31', to: '2026-10-31', until: local(21) };
  const sum = rep.summarize(raw, w, ZONE, { lowStock: 3 });
  assert.equal(sum.invoices, 2);
  assert.equal(sum.sales, 140000);
  assert.equal(sum.discount, 10000);
  assert.equal(sum.returnCount, 2);
  assert.equal(sum.returns, 30000);
  assert.equal(sum.netSales, 110000);
  assert.deepEqual(sum.payments, [
    { method: 'cash', invoices: 1, sales: 90000, returns: 30000, netSales: 60000 },
    { method: 'credit', invoices: 1, sales: 50000, returns: 0, netSales: 50000 },
  ]);
  assert.equal(sum.cashNet, 60000);
  assert.equal(sum.otherNet, 50000);
  //  تخفیفِ ۱۰۰ ِ s1 به نسبتِ ۶۰۰/۴۰۰ پخش ⇒ نان ۵۴۰ − ۲۰۰ برگشت = ۳۴۰؛ شیر ۳۶۰ + ۵۰۰ − ۱۰۰ = ۷۶۰
  assert.deepEqual(sum.topProducts, [
    { productId: 'p2', name: 'شیر', qtyMilli: 1750, netSales: 76000 },
    { productId: 'p1', name: 'نانِ تازه', qtyMilli: 1000, netSales: 34000 },
  ]);
  assert.equal(sum.lowStock, 3);
  assert.equal(sum.partial, false);
  const text = rep.render(sum, { shopName: 'دکانِ آزمون' });
  assert.equal(text.title, 'گزارشِ پایانِ روز — دکانِ آزمون');
  assert.match(text.body, /تا ساعتِ ۲۱:۰۰/);
  assert.match(text.body, /فروش: ۲ فاکتور — ۱٬۴۰۰/);
  assert.match(text.body, /برگشتی: ۲ — ۳۰۰/);
  assert.match(text.body, /خالص: ۱٬۱۰۰/);
  assert.match(text.body, /نقد ۶۰۰، نسیه ۵۰۰/);
  assert.match(text.body, /۱\. شیر \(۷۶۰\)، ۲\. نانِ تازه \(۳۴۰\)/);
  assert.match(text.body, /کالای کم‌موجود: ۳/);
  //  بی ردیف ⇒ «هیچ فاکتوری»، نه صفرهای ساختگیِ روشِ پرداخت؛ کم‌موجودِ نامعلوم ⇒ بی خط
  const empty = rep.render(rep.summarize({}, w, ZONE), {});
  assert.match(empty.body, /هیچ فاکتور یا برگشتیِ همگام‌شده‌ای نیست/);
  assert.doesNotMatch(empty.body, /کم‌موجود|روشِ پرداخت/);
});

test('مرزِ روز و منطقه: ساعتِ محلی، صبح ⇒ دیروز، هفتگی، مهلتِ جبران', () => {
  const daily = { active: true, frequency: 'daily', weekday: 6, hour: 21, channels: ['inapp'] };
  assert.equal(rep.dueOf(daily, local(20, 59), ZONE), null);
  assert.deepEqual(rep.dueOf(daily, local(21), ZONE), {
    reportDate: '2026-10-31', frequency: 'daily', from: '2026-10-31', to: '2026-10-31',
    fromAt: MIDNIGHT, until: local(21), dueAt: local(21),
  });
  //  پس از نیمه‌شبِ محلی هنوز گزارشِ دیروز (مهلتِ سه‌ساعته)، و نه بیشتر
  assert.equal(rep.dueOf({ ...daily, hour: 22 }, local(24, 30), ZONE).reportDate, '2026-10-31');
  assert.equal(rep.dueOf(daily, local(24) - 1, ZONE).reportDate, '2026-10-31');
  assert.equal(rep.dueOf(daily, local(21) + rep.CATCHUP_MS, ZONE), null);
  assert.equal(rep.dueOf({ ...daily, active: false }, local(21), ZONE), null);
  //  همان لحظهٔ UTC در منطقهٔ دیگر روزِ دیگری است
  assert.equal(rep.dueOf(daily, local(21), 'UTC'), null);
  assert.equal(rep.dueOf(daily, Date.UTC(2026, 9, 31, 21, 5), 'UTC').reportDate, '2026-10-31');
  //  صبح ⇒ دیروزِ کامل، تا نیمه‌شب
  const morning = rep.dueOf({ ...daily, hour: 8 }, local(8, 10), ZONE);
  assert.deepEqual([morning.reportDate, morning.from, morning.to, morning.until], ['2026-10-31', '2026-10-30', '2026-10-30', MIDNIGHT]);
  //  هفتگی: ۲۰۲۶-۱۰-۳۱ شنبه است (۶) ⇒ هفت روزِ ۲۵ تا ۳۱؛ روزِ دیگرِ هفته ⇒ هیچ
  const weekly = rep.dueOf({ ...daily, frequency: 'weekly', weekday: 6 }, local(21, 30), ZONE);
  assert.deepEqual([weekly.from, weekly.to, weekly.frequency], ['2026-10-25', '2026-10-31', 'weekly']);
  assert.equal(rep.dueOf({ ...daily, frequency: 'weekly', weekday: 5 }, local(21, 30), ZONE), null);
});

test('پایگاه: ردیفِ ۲۳:۵۹ ِ دیروز و پس از ساعتِ گزارش بیرون؛ ۰۰:۰۱ و بی createdAt درون؛ حذف‌شده لغو', async () => {
  const s = await shop('مرز');
  assert.equal((await putSettings(s, { active: true, hour: 21, channels: ['inapp'] })).status, 200);
  await sale(s, 'b1', 111, local(0, -1));   //  ۲۳:۵۹ ِ دیروزِ محلی
  await sale(s, 'b2', 222, local(0, 1));
  await sale(s, 'b3', 333, local(20, 59));
  await sale(s, 'b4', 444, local(21, 1));
  await sale(s, 'b5', 555, 0, { date: '2026-10-31', createdAt: undefined });
  await sale(s, 'b6', 666, local(10));
  await query(`UPDATE sync_rows SET deleted_at=1 WHERE account_id=$1 AND row_id='b6'`, [s.shopId]);
  assert.equal(await rep.sweep(local(20, 59)), 0);
  assert.equal((await reportsOf(s)).length, 0);
  assert.ok((await rep.sweep(local(21, 5))) >= 1);
  const [r] = await reportsOf(s);
  assert.equal(r.status, 'sent', r.error);
  assert.equal(r.report_date, '2026-10-31');
  assert.equal(r.summary.invoices, 3);
  assert.equal(r.summary.sales, (222 + 333 + 555) * 100);
  assert.equal(r.summary.lowStock, null);
  assert.equal(r.summary.timezone, ZONE);
});

test('تکرار و هم‌زمانی ⇒ یک گزارش و یک تحویل در هر کانال؛ عوض کردنِ ساعت همان روز گزارشِ دوم نمی‌سازد', async () => {
  const s = await shop('تکرار');
  assert.equal((await putSettings(s, { active: true, hour: 21, channels: ['inapp', 'email'] })).status, 200);
  await sale(s, 'c1', 1000, local(9));
  await Promise.all([rep.sweep(local(21, 2)), rep.sweep(local(21, 2)), rep.sweep(local(21, 3))]);
  await rep.sweep(local(22));
  assert.equal((await putSettings(s, { hour: 23 })).status, 200);
  await rep.sweep(local(23, 1));
  const reps = await reportsOf(s);
  assert.equal(reps.length, 1);
  assert.equal(reps[0].status, 'sent');
  assert.deepEqual(reps[0].results, { inapp: 'sent', email: 'sent' });
  assert.deepEqual((await deliveriesOf(s.user.id)).filter((d) => d.notice_id === reps[0].notice_id).map((d) => d.channel), ['email', 'inapp']);
  //  فردا دوباره
  await rep.sweep(local(24 + 23, 5));
  assert.deepEqual((await reportsOf(s)).map((r) => r.report_date), ['2026-10-31', '2026-11-01']);
});

test('فقط صاحب: مدیر و کارمند ۴۰۳ owner_only و GET ِ بی عدد؛ گیرنده فقط صاحب، نه صندوقِ کارمند', async () => {
  const s = await shop('صاحب');
  const manager = await member(s, 'manager', 'مدیرِ دکان');
  const staff = await member(s, 'staff', 'شاگردِ دکان');
  for (const who of [manager, staff]) {
    const r = await putSettings(s, { active: true }, who.token);
    assert.equal(r.status, 403);
    assert.equal(r.body.error?.code || r.body.code, 'owner_only', JSON.stringify(r.body));
    assert.deepEqual((await h.get('/api/shop/owner-report', { token: who.token })).body, { enabled: true, owner: false });
  }
  assert.equal((await putSettings(s, { hour: 24 })).status, 400);
  assert.equal((await putSettings(s, { channels: ['whatsapp'] })).status, 400);
  assert.equal((await putSettings(s, { channels: [] })).status, 400);
  const ok = await putSettings(s, { active: true, frequency: 'weekly', weekday: 6, hour: 21, channels: ['push', 'inapp'], expectedVersion: 0 });
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.deepEqual(ok.body.settings.channels, ['inapp', 'push']);
  assert.equal((await putSettings(s, { hour: 20, expectedVersion: 0 })).status, 409);
  const view = (await h.get('/api/shop/owner-report', { token: s.token })).body;
  assert.equal(view.owner, true);
  assert.equal(view.settings.frequency, 'weekly');
  assert.equal(view.channels.whatsapp, false);
  assert.equal(view.timezone, ZONE);

  await sale(s, 'd1', 700, local(10));
  const push = require('../src/lib/push');
  await push.register({ app: 'shop', token: `own-${s.shopId}`, userId: s.user.id, shopId: s.shopId });
  await push.register({ app: 'shop', token: `stf-${s.shopId}`, userId: staff.user.id, shopId: s.shopId });
  pushed = [];
  assert.ok((await rep.sweep(local(21, 1))) >= 1);
  const mine = pushed.filter((p) => p.msg.data && p.msg.data.type === 'notice');
  assert.ok(mine.length >= 1);
  assert.ok(mine.every((p) => p.row.user_id === s.user.id), JSON.stringify(mine.map((p) => p.row.user_id)));
  const inbox = (await h.get('/api/me/notices', { token: s.token })).body.notices;
  assert.ok(inbox.some((n) => n.title.startsWith('گزارشِ هفتگی')), JSON.stringify(inbox));
  for (const who of [manager, staff]) {
    const theirs = (await h.get('/api/me/notices', { token: who.token })).body.notices || [];
    assert.ok(!theirs.some((n) => n.title.startsWith('گزارشِ')), JSON.stringify(theirs));
  }
});

test('پرچم خاموش ⇒ همان پاسخ‌ها و هیچ ردیف و اعلانی، حتی با تنظیمِ روشن در پایگاه', async () => {
  const s = await shop('خاموش', { on: false });
  const before = await h.get('/api/me/notices', { token: s.token });
  assert.deepEqual((await h.get('/api/shop/owner-report', { token: s.token })).body, { enabled: false });
  const put = await putSettings(s, { active: true });
  assert.equal(put.status, 403);
  assert.equal(put.body.error?.code || put.body.code, 'owner_report_disabled', JSON.stringify(put.body));
  await query(
    `INSERT INTO owner_report_settings (shop_id, active, frequency, weekday, hour, channels, version, updated_by, updated_at)
     VALUES ($1,true,'daily',6,21,'["inapp","email","push"]'::jsonb,1,'',1)`, [s.shopId]
  );
  await sale(s, 'e1', 900, local(9));
  pushed = [];
  await rep.sweep(local(21, 5));
  assert.equal((await reportsOf(s)).length, 0);
  assert.equal((await deliveriesOf(s.user.id)).length, 0);
  assert.equal(pushed.filter((p) => p.row.user_id === s.user.id).length, 0);
  const after = await h.get('/api/me/notices', { token: s.token });
  assert.deepEqual({ ...after.body, serverTime: 0 }, { ...before.body, serverTime: 0 });
});
