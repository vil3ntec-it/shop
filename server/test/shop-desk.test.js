'use strict';
/**
 * میزِ «فروشگاه» — بندهای ۴.۲ و ۴.۳ سندِ ریمیکِ ریپوی `server`.
 *
 * ── خواستهٔ صاحب سامانه ────────────────────────────────────────────────
 *   «این بخش را تمام‌صفحه کن. اول داشبورد… تعدادِ مشتری‌ها، اشتراک‌دارها،
 *    آنلاین‌ها، کسانی که اشتراکشان رو به پایان است با ایمیل و وقتِ مانده،
 *    و پیام‌های پشتیبانی. دوم اشتراک‌ها با سه گروه.»
 *
 * ⛔ و همه‌اش **سمتِ سرور** حساب می‌شود، نه در پنل — قاعدهٔ «یک دفترِ حساب».
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, newId, now } = require('../src/db');

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

const adminToken = async () =>
  (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;

/** یک دکانِ تازه با صاحبش. */
async function aShop(tag) {
  const owner = await h.newUser(`صاحبِ ${tag}`);
  const shop = await h.post('/api/shop', { name: `دکانِ ${tag}` }, { token: owner.accessToken });
  return { owner, id: shop.body.shop.id };
}

test('۱) داشبورد همان شش عددی را می‌دهد که خواسته شد', async () => {
  await aShop('د۱');
  const token = await adminToken();
  const r = await h.get('/api/admin/shop-desk/overview', { token });
  assert.equal(r.status, 200);
  for (const k of ['shops', 'customers', 'subscribed', 'online', 'expiring', 'supportOpen', 'supportUnread']) {
    assert.equal(typeof r.body.counts[k], 'number', `عددِ «${k}» باید باشد`);
  }
  assert.ok(r.body.counts.shops >= 1);
  //  ⚠️ «آنلاین» تعریفش در خودِ پاسخ می‌آید تا کسی عددِ دیگری از آن نفهمد
  assert.equal(typeof r.body.onlineWithinMs, 'number');
  assert.ok(r.body.onlineWithinMs > 0);
});

test('۲) «رو به پایان» ایمیل و روزِ مانده دارد — همان چیزی که خواسته شد', async () => {
  const s = await aShop('د۲');
  const token = await adminToken();
  await h.post('/api/admin/subscriptions', { shopId: s.id, plan: 'm1', endsAt: now() + 3 * DAY }, { token });

  const r = await h.get('/api/admin/shop-desk/overview?days=7', { token });
  const row = r.body.expiring.find((x) => x.tenantId === s.id);
  assert.ok(row, 'اشتراکِ سه‌روزه باید در فهرستِ رو به پایان باشد');
  assert.equal(row.ownerEmail, s.owner.email, 'ایمیلِ صاحبِ دکان باید بیاید');
  assert.ok(row.daysLeft >= 1 && row.daysLeft <= 4, `روزِ مانده: ${row.daysLeft}`);
  assert.ok(r.body.counts.expiring >= 1);
});

test('۳) سه گروه: دارد / ندارد / تمام‌شده — و هر دکان فقط در یکی', async () => {
  const token = await adminToken();
  const has = await aShop('د۳-دارد');
  const none = await aShop('د۳-ندارد');
  const gone = await aShop('د۳-تمام');

  await h.post('/api/admin/subscriptions', { shopId: has.id, plan: 'm1', endsAt: now() + 30 * DAY }, { token });
  //  اشتراکی که گذشته — از همان درِ همیشگی ساخته می‌شود و بعد تاریخش عقب می‌رود
  const old = await h.post('/api/admin/subscriptions',
    { shopId: gone.id, plan: 'm1', endsAt: now() + DAY }, { token });
  await query('UPDATE subscriptions SET starts_at=$2, ends_at=$3, grace_days=0 WHERE id=$1',
    [old.body.subscription.id, now() - 40 * DAY, now() - 10 * DAY]);

  const r = await h.get('/api/admin/shop-desk/groups', { token });
  assert.equal(r.status, 200);
  const idsOf = (g) => r.body.groups[g].map((x) => x.tenantId);
  assert.ok(idsOf('has').includes(has.id), 'اشتراکِ زنده در گروهِ «دارد»');
  assert.ok(idsOf('none').includes(none.id), 'دکانِ بی‌اشتراک در گروهِ «ندارد»');
  assert.ok(idsOf('expired').includes(gone.id), 'اشتراکِ گذشته در گروهِ «تمام‌شده»');

  //  ⛔ و هیچ دکانی در دو گروه نیست
  const all = [...idsOf('has'), ...idsOf('none'), ...idsOf('expired')];
  assert.equal(new Set(all).size, all.length, 'یک دکان نباید در دو گروه باشد');
  assert.equal(r.body.counts.has, r.body.groups.has.length);
});

test('۴) ⛔ «هیچ‌وقت نداشت» با «داشت و تمام شد» یکی شمرده نمی‌شود', async () => {
  const token = await adminToken();
  const r = await h.get('/api/admin/shop-desk/groups', { token });
  const noneIds = new Set(r.body.groups.none.map((x) => x.tenantId));
  //  هر ردیفِ «ندارد» باید واقعاً بی شناسهٔ اشتراک باشد
  assert.ok(r.body.groups.none.every((x) => !x.subscriptionId));
  //  و هیچ ردیفِ «تمام‌شده»ای در آن نباشد
  assert.ok(r.body.groups.expired.every((x) => !noneIds.has(x.tenantId)));
  //  ⚠️ و «تمام‌شده» شناسهٔ اشتراک **دارد** — وگرنه صفحه نمی‌داند چه چیزی را تمدید کند
  assert.ok(r.body.groups.expired.every((x) => Boolean(x.subscriptionId)));
});

test('۵) جست‌وجو با ایمیلِ صاحبِ دکان کار می‌کند', async () => {
  const s = await aShop('د۵');
  const token = await adminToken();
  const r = await h.get(`/api/admin/shop-desk/groups?q=${encodeURIComponent(s.owner.email)}`, { token });
  const all = [...r.body.groups.has, ...r.body.groups.none, ...r.body.groups.expired];
  assert.ok(all.some((x) => x.tenantId === s.id), 'با ایمیل پیدا شود');
  //  ⛔ و فیلتر واقعاً فیلتر کند، نه این‌که همه را برگرداند
  assert.ok(all.length < 50, `${all.length} ردیف برگشت`);
});

test('۶) ⛔ بی نشستِ مدیر بسته است', async () => {
  assert.equal((await h.get('/api/admin/shop-desk/overview')).status, 401);
  assert.equal((await h.get('/api/admin/shop-desk/groups')).status, 401);
});
