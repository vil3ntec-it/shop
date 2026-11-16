'use strict';
/**
 * رقابتِ واقعیِ تمدیدِ اشتراک (Task B ِ e-sec-close، ۱۴۰۵/۰۸/۲۶) — فقط روی PostgreSQL ِ واقعی معنا دارد (کارِ CI
 * «آزمون‌های سرور روی PostgreSQL»؛ روی PGlite هم سبز می‌ماند چون یک اتصال است و همه چیز پشتِ هم می‌نشیند).
 *
 * `admin-idempotency.test.js` «همان کلید هم‌زمان ⇒ یک دوره» را قفل کرده است. این‌جا روی دیگرِ همان سکه: دو (یا پنج)
 * تمدیدِ **با کلیدهای جدا** در یک لحظه برای یک دکان ⇒ هر کدام دقیقاً یک دوره، نه «گم شدنِ یکی» (هر دو `ends_at` ِ کهنه را
 * بخوانند و یکی روی دیگری بنویسد) و نه بیش از شمارِ درخواست‌ها. نگهبان: قفلِ `sub-grant` ِ `grantLocked`.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');

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
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body.token;
}

async function shopOwner(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { ...u, shopId: made.body.shop.id };
}

const grant = (t, body, key) => h.api('POST', '/api/admin/subscriptions', { body, token: t, headers: { 'Idempotency-Key': key } });
const liveSub = (shopId) => one(`SELECT * FROM subscriptions WHERE shop_id=$1 AND status IN ('active','suspended')
                                  ORDER BY updated_at DESC LIMIT 1`, [shopId]);
const historyCount = async (shopId) =>
  (await many(`SELECT id FROM subscription_history WHERE shop_id=$1 AND action IN ('grant','renew')`, [shopId])).length;

for (const n of [2, 5]) {
  test(`${n} تمدیدِ هم‌زمان با کلیدهای جدا ⇒ دقیقاً ${n} دوره (هیچ‌کدام گم نمی‌شود، هیچ‌کدام دو بار نمی‌نشیند)`, async () => {
    const t = await adminToken();
    const o = await shopOwner(`رقابت-${n}`);
    const before = await liveSub(o.shopId);
    const baseEnd = before ? Math.max(Number(before.ends_at), Date.now()) : Date.now();
    const baseHistory = await historyCount(o.shopId);
    const body = { shopId: o.shopId, plan: 'custom', days: 30 };

    const res = await Promise.all(Array.from({ length: n }, (_, i) => grant(t, body, `race-${n}-${i}-${newId('k')}`)));
    for (const r of res) assert.equal(r.status, 201, JSON.stringify(r.body));
    assert.equal(new Set(res.map((r) => String(r.body.subscription.ends_at))).size, n, 'هر تمدید پایانِ تازهٔ خودش را دید');

    const live = await liveSub(o.shopId);
    const gotDays = Math.round((Number(live.ends_at) - baseEnd) / DAY);
    assert.equal(gotDays, n * 30, `پایان باید دقیقاً ${n * 30} روز جلو برود، رفت ${gotDays}`);
    assert.equal(await historyCount(o.shopId) - baseHistory, n, 'یک ردِ تغییر برای هر تمدید');
  });
}
