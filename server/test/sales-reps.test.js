'use strict';
/**
 * نماینده‌های فروش — شورا، چ۳.
 *
 * «نقشِ نماینده در سرورِ حساب و پنل: کدِ تخفیفِ هر نماینده، فروش‌های او، و
 *  گزارشِ کمیسیون (درصد از پنل). نماینده فقط مشتری‌های خودش را می‌بیند.
 *  آزمونِ جداسازی و آزمونِ گزارش.»
 *
 * آن‌چه قفل می‌شود:
 *   ۱) درصدِ کمیسیون **فقط از پنل** و بی پیش‌فرض.
 *   ۲) کدِ نماینده فقط برای نمایندهٔ فعالِ **همان بخش**.
 *   ۳) فروش با کد ⇒ ردیفِ `discount_uses` با نماینده و درصدِ **همان لحظه**؛
 *      عوض شدنِ درصد فروشِ گذشته را دست نمی‌زند.
 *   ۴) نماینده فقط فروش‌های خودش را می‌بیند — نه نمایندهٔ دیگر، نه با
 *      `?rep=`؛ و کسی که نماینده نیست ۴۰۳.
 *   ۵) گزارش: جمعِ فروش و کمیسیون از همان ردیف‌ها، به تفکیکِ ارز.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');

let T;
test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  T = (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
});
test.after(async () => { await h.stop(); });

async function shopOwner(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { ...u, shopId: made.body.shop.id };
}
async function pumpOwner(name) {
  const u = await h.newUser(name, 'pump');
  const made = await h.post('/api/pump', { name: `پمپِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { ...u, stationId: made.body.station.id };
}
/** قیمتِ روزِ پلن از خودِ سرور — هیچ عددی این‌جا ساخته نمی‌شود. */
async function pricedPlan(app) {
  const r = await h.get(`/api/plans?app=${app}`);
  const p = (r.body.plans || []).find(x => Number(x.price) > 0);
  assert.ok(p, `پلنِ قیمت‌دار در ${app} نیست: ${JSON.stringify(r.body)}`);
  return p;
}

const S = {};

test('۱) درصدِ کمیسیون فقط از پنل، بی پیش‌فرض؛ حسابِ نبوده ۴۰۴', async () => {
  S.r1 = await h.newUser('نمایندهٔ یک', 'shop');
  const noPct = await h.post('/api/admin/reps', { app: 'shop', email: S.r1.email }, { token: T });
  assert.equal(noPct.status, 400);
  assert.equal(noPct.body.error.code, 'commission_required');
  const bad = await h.post('/api/admin/reps', { app: 'shop', email: S.r1.email, commissionPct: 140 }, { token: T });
  assert.equal(bad.status, 400);
  const ghost = await h.post('/api/admin/reps', { app: 'shop', email: 'nobody@test.local', commissionPct: 10 }, { token: T });
  assert.equal(ghost.status, 404);
  const ok = await h.post('/api/admin/reps', { app: 'shop', email: S.r1.email, commissionPct: '۱۰', name: 'علی' }, { token: T });
  assert.equal(ok.status, 201, JSON.stringify(ok.body));
  assert.equal(ok.body.rep.commissionPct, 10, 'رقمِ فارسی هم خوانده می‌شود');
  S.rep1 = ok.body.rep;
  const dup = await h.post('/api/admin/reps', { app: 'shop', email: S.r1.email, commissionPct: 5 }, { token: T });
  assert.equal(dup.status, 409);

  S.r2 = await h.newUser('نمایندهٔ دو', 'shop');
  S.rep2 = (await h.post('/api/admin/reps', { app: 'shop', email: S.r2.email, commissionPct: 7.5 }, { token: T })).body.rep;
  S.r3 = await h.newUser('نمایندهٔ پمپ', 'pump');
  S.rep3 = (await h.post('/api/admin/reps', { app: 'pump', email: S.r3.email, commissionPct: 5 }, { token: T })).body.rep;
  assert.ok(S.rep2 && S.rep3);
});

test('۲) کدِ نماینده فقط برای نمایندهٔ فعالِ همان بخش', async () => {
  const c1 = await h.post('/api/admin/discount-codes',
    { app: 'shop', code: 'ALI20', kind: 'percent', value: 20, repId: S.rep1.id }, { token: T });
  assert.equal(c1.status, 201, JSON.stringify(c1.body));
  assert.equal(c1.body.code.repId, S.rep1.id);
  const c2 = await h.post('/api/admin/discount-codes',
    { app: 'shop', code: 'REZA10', kind: 'percent', value: 10, repId: S.rep2.id }, { token: T });
  assert.equal(c2.status, 201);
  const cross = await h.post('/api/admin/discount-codes',
    { app: 'shop', code: 'CROSS', kind: 'percent', value: 10, repId: S.rep3.id }, { token: T });
  assert.equal(cross.status, 400, '⛔ نمایندهٔ پمپ کدِ دکان نمی‌گیرد');
  assert.equal(cross.body.error.code, 'bad_rep');
  const c3 = await h.post('/api/admin/discount-codes',
    { app: 'pump', code: 'PUMP5', kind: 'percent', value: 5, repId: S.rep3.id }, { token: T });
  assert.equal(c3.status, 201);
  const mine = await h.get(`/api/admin/discount-codes?repId=${S.rep1.id}`, { token: T });
  assert.deepEqual(mine.body.codes.map(c => c.code), ['ALI20']);
});

test('۳) فروش با کد ⇒ نماینده و درصدِ همان لحظه روی ردیف — دکان و پمپ', async () => {
  const plan = await pricedPlan('shop');
  S.plan = plan;
  S.c1 = await shopOwner('مشتریِ علی');
  S.c2 = await shopOwner('مشتریِ رضا');
  const g1 = await h.post('/api/admin/subscriptions', { shopId: S.c1.shopId, plan: plan.code, discountCode: 'ALI20' }, { token: T });
  assert.equal(g1.status, 201, JSON.stringify(g1.body));
  const g2 = await h.post('/api/admin/subscriptions', { shopId: S.c2.shopId, plan: plan.code, discountCode: 'REZA10' }, { token: T });
  assert.equal(g2.status, 201);
  const u1 = await one(`SELECT * FROM discount_uses WHERE tenant_id=$1`, [S.c1.shopId]);
  assert.equal(u1.rep_id, S.rep1.id);
  assert.equal(Number(u1.commission_bp), 1000);
  assert.equal(Number(u1.final_price), Math.max(0, plan.price - Math.round(plan.price * 20 / 100)),
    'قیمتِ نهایی از قیمتِ روزِ خودِ سرور');

  const pplan = await pricedPlan('pump');
  S.p1 = await pumpOwner('مشتریِ پمپ');
  const gp = await h.post('/api/admin/pump/subscriptions', { stationId: S.p1.stationId, plan: pplan.code, discountCode: 'PUMP5' }, { token: T });
  assert.equal(gp.status, 201, JSON.stringify(gp.body));
  const up = await one(`SELECT * FROM discount_uses WHERE tenant_id=$1`, [S.p1.stationId]);
  assert.equal(up.rep_id, S.rep3.id, 'اشتراکِ پمپ هم کدِ نماینده را می‌گیرد');
  assert.equal(up.currency, 'USD');

  //  درصد عوض می‌شود ⇒ فروشِ گذشته همان ۱۰٪ می‌ماند؛ فروشِ تازه ۵۰٪
  const up1 = await h.patch(`/api/admin/reps/${S.rep1.id}`, { commissionPct: 50 }, { token: T });
  assert.equal(up1.status, 200);
  assert.equal(up1.body.rep.commissionPct, 50);
  S.c3 = await shopOwner('مشتریِ دومِ علی');
  await h.post('/api/admin/subscriptions', { shopId: S.c3.shopId, plan: plan.code, discountCode: 'ALI20' }, { token: T });
  const u3 = await one(`SELECT * FROM discount_uses WHERE tenant_id=$1`, [S.c3.shopId]);
  assert.equal(Number(u3.commission_bp), 5000);
  const again = await one(`SELECT commission_bp FROM discount_uses WHERE tenant_id=$1`, [S.c1.shopId]);
  assert.equal(Number(again.commission_bp), 1000, '⛔ فروشِ گذشته با درصدِ تازه بازنویسی نشد');
});

test('۴) نماینده فقط فروش‌های خودش را می‌بیند — نه دیگری، نه با ?rep=', async () => {
  const me = await h.get('/api/rep/me', { token: S.r1.accessToken });
  assert.equal(me.status, 200, JSON.stringify(me.body));
  const names = me.body.sales.map(s => s.customer).sort();
  assert.deepEqual(names, ['دکانِ مشتریِ دومِ علی', 'دکانِ مشتریِ علی']);
  assert.ok(!JSON.stringify(me.body).includes('مشتریِ رضا'), '⛔ مشتریِ نمایندهٔ دیگر دیده نمی‌شود');
  assert.ok(!JSON.stringify(me.body).includes(S.c1.email), '⛔ ایمیلِ مشتری به نماینده نمی‌رسد');
  assert.deepEqual(me.body.codes.map(c => c.code), ['ALI20']);

  const spoof = await h.get(`/api/rep/me?rep=${S.rep2.id}&repId=${S.rep2.id}`, { token: S.r1.accessToken });
  assert.ok(!JSON.stringify(spoof.body).includes('مشتریِ رضا'), '⛔ ?rep=ِ دستی بی‌اثر است');

  const two = await h.get('/api/rep/me', { token: S.r2.accessToken });
  assert.deepEqual(two.body.sales.map(s => s.customer), ['دکانِ مشتریِ رضا']);

  const pump = await h.get('/api/rep/me', { token: S.r3.accessToken });
  assert.deepEqual(pump.body.sales.map(s => s.customer), ['پمپِ مشتریِ پمپ'], 'نمایندهٔ پمپ فقط فروشِ پمپِ خودش');

  const notRep = await h.get('/api/rep/me', { token: S.c1.accessToken });
  assert.equal(notRep.status, 403);
  assert.equal(notRep.body.error.code, 'not_a_rep');
  assert.equal((await h.get('/api/rep/me')).status, 401);

  //  ⛔ نشستِ پمپِ همان آدم، نمایندگیِ دکانش را باز نمی‌کند (دو بخش، دو در)
  const crossApp = await h.signIn(S.r1, 'pump');
  assert.equal((await h.get('/api/rep/me', { token: crossApp.accessToken })).status, 403);
});

test('۵) گزارش: جمعِ فروش و کمیسیون از همان ردیف‌ها', async () => {
  const final = Math.max(0, S.plan.price - Math.round(S.plan.price * 20 / 100));
  const r = await h.get(`/api/admin/reps/${S.rep1.id}/report`, { token: T });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.totals.count, 2);
  assert.equal(r.body.totals.customers, 2);
  const afn = r.body.totals.byCurrency.find(x => x.currency === 'AFN');
  assert.equal(afn.sales, final * 2);
  assert.equal(afn.commission, Math.round(final * 0.10) + Math.round(final * 0.50),
    'هر فروش با درصدِ لحظهٔ خودش');
  const me = await h.get('/api/rep/me', { token: S.r1.accessToken });
  assert.deepEqual(me.body.totals, r.body.totals, 'نماینده همان عددی را می‌بیند که مدیر');

  //  بازهٔ زمانی: آینده ⇒ هیچ
  const later = await h.get(`/api/admin/reps/${S.rep1.id}/report?from=${now() + 86400000}`, { token: T });
  assert.equal(later.body.totals.count, 0);
});

test('۶) نمایندهٔ غیرفعال: درش بسته و فروشِ تازه بی کمیسیون', async () => {
  const off = await h.patch(`/api/admin/reps/${S.rep2.id}`, { status: 'disabled' }, { token: T });
  assert.equal(off.body.rep.status, 'disabled');
  assert.equal((await h.get('/api/rep/me', { token: S.r2.accessToken })).status, 403);
  const c4 = await shopOwner('مشتریِ چهارم');
  const g = await h.post('/api/admin/subscriptions', { shopId: c4.shopId, plan: S.plan.code, discountCode: 'REZA10' }, { token: T });
  assert.equal(g.status, 201);
  const u = await one(`SELECT rep_id, commission_bp FROM discount_uses WHERE tenant_id=$1`, [c4.shopId]);
  assert.equal(u.rep_id, '');
  assert.equal(Number(u.commission_bp), 0);
});
