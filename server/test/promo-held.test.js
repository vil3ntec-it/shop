'use strict';
/**
 * PRM-02 — فهرستِ کدهای نگه‌داشته برای آزادِ دستیِ صاحب/مدیر (`GET /api/shop/promos/redemptions`).
 *
 * صندوقداری که فاکتور را با «درخواستِ تأیید» لغو کرد اجازهٔ آزاد کردن ندارد (`release_not_allowed`)؛
 * برنامه ردیف را از صفش بیرون می‌اندازد و کد تا ابد نگه‌داشته می‌ماند. صاحب/مدیر باید ببیند کدام
 * فاکتور کدام کد را نگه داشته تا با همان `POST /redeem/release` ِ موجود آزادش کند:
 *   • پرچمِ `shop.promo_uses` خاموش ⇒ ۴۰۳ `promo_uses_disabled`؛
 *   • فقط دارندهٔ `sales.cancel` (همان که کدِ دیگران را آزاد می‌کند) ⇒ شاگرد ۴۰۳ `release_not_allowed`؛
 *   • فقط ردیف‌های آزادنشدهٔ همین دکان، تازه‌ترین اول، سقفِ ۲۰۰ با `more`؛ بی شناسهٔ کاربر/دستگاه؛
 *   • سرتاسر: شاگرد پس از مهلت نمی‌تواند، صاحب در فهرست می‌بیند و آزاد می‌کند، ظرفیت برمی‌گردد.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query } = require('../src/db');

test.before(async () => { await h.start(); });
test.after(async () => { await h.stop(); });

const P10 = { name: 'ده درصد', kind: 'percent', bp: 1000 };
let seq = 0;
const saleId = () => `01JHELD${String(++seq).padStart(19, '0')}`;

async function shopWithTeam(label, { flag = true } = {}) {
  const owner = await h.newUser(`صاحب ${label}`);
  const made = await h.post('/api/shop', { name: `دکان ${label}` }, { token: owner.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const code = (await h.get('/api/shop/staff-code', { token: owner.accessToken })).body.code;
  const join = async (name, c) => {
    const u = await h.newUser(name);
    assert.ok([200, 201].includes((await h.post('/api/shop/join', { code: c }, { token: u.accessToken })).status));
    return u.accessToken;
  };
  const staff = await join(`شاگرد ${label}`, code);
  const inv = await h.post('/api/shop/invite', { role: 'manager' }, { token: owner.accessToken });
  assert.equal(inv.status, 201, JSON.stringify(inv.body));
  const manager = await join(`مدیر ${label}`, inv.body.code);
  const shopId = made.body.shop.id;
  if (flag) await setFlag(shopId, true);
  return { owner: owner.accessToken, staff, manager, shopId };
}

async function setFlag(scope, on) {
  await query(
    `INSERT INTO live_config (scope, key, value, updated_at) VALUES ($1,'shop.promo_uses',$2,1)
     ON CONFLICT (scope, key) DO UPDATE SET value=excluded.value`,
    [scope, JSON.stringify(on)]);
}

async function rule(t, id, extra) {
  const r = await h.put(`/api/shop/promos/rules/${id}`, { rule: { ...P10, ...extra } }, { token: t.owner });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

const redeem = (tok, sale, codes) => h.post('/api/shop/promos/redeem', { saleId: sale, codes }, { token: tok });
const release = (tok, sale) => h.post('/api/shop/promos/redeem/release', { saleId: sale }, { token: tok });
const held = (tok) => h.get('/api/shop/promos/redemptions', { token: tok });
/** مهلتِ ۱۵ دقیقه‌ای صندوقدار گذشت */
const age = (shopId, sale) => query(
  'UPDATE shop_promo_redemptions SET updated_at = updated_at - $3 WHERE shop_id=$1 AND sale_id=$2', [shopId, sale, 16 * 60 * 1000]);

test('پرچم خاموش ⇒ ۴۰۳ promo_uses_disabled؛ بی توکن ⇒ ۴۰۱', async () => {
  const t = await shopWithTeam('خاموش', { flag: false });
  const r = await held(t.owner);
  assert.equal(r.status, 403, JSON.stringify(r.body));
  assert.equal(r.body.error.code, 'promo_uses_disabled');
  assert.equal((await h.get('/api/shop/promos/redemptions')).status, 401);
});

test('فقط دارندهٔ لغوِ فاکتور: شاگرد ۴۰۳ release_not_allowed، صاحب و مدیر ۲۰۰', async () => {
  const t = await shopWithTeam('نقش');
  const s = await held(t.staff);
  assert.equal(s.status, 403, JSON.stringify(s.body));
  assert.equal(s.body.error.code, 'release_not_allowed');
  for (const tok of [t.owner, t.manager]) {
    const ok = await held(tok);
    assert.equal(ok.status, 200, JSON.stringify(ok.body));
    assert.deepEqual(ok.body, { held: [], more: false });
  }
});

test('فهرست: آزادنشده‌های همین دکان، تازه‌ترین اول، بی شناسهٔ کاربر و دستگاه؛ دکانِ دیگر نه', async () => {
  const a = await shopWithTeam('الف');
  const b = await shopWithTeam('ب');
  await rule(a, 'r1', { code: 'EID5', maxUses: 5 });
  await rule(a, 'r2', { code: 'NOROOZ', maxUses: 5 });
  await rule(b, 'r1', { code: 'EID5', maxUses: 5 });
  const [s1, s2, s3, sb] = [saleId(), saleId(), saleId(), saleId()];
  assert.equal((await redeem(a.staff, s1, ['EID5'])).status, 200);
  assert.equal((await redeem(a.staff, s2, ['EID5', 'NOROOZ'])).status, 200);
  assert.equal((await redeem(a.staff, s3, ['NOROOZ'])).status, 200);
  assert.equal((await redeem(b.staff, sb, ['EID5'])).status, 200);
  //  s3 را خودِ صندوقدار در مهلت آزاد کرد ⇒ دیگر نگه‌داشته نیست
  assert.equal((await release(a.staff, s3)).body.released, 1);
  //  ترتیبِ زمانی معین
  await query('UPDATE shop_promo_redemptions SET updated_at = 1000 WHERE shop_id=$1 AND sale_id=$2', [a.shopId, s1]);
  await query('UPDATE shop_promo_redemptions SET updated_at = 2000 WHERE shop_id=$1 AND sale_id=$2', [a.shopId, s2]);

  const r = await held(a.owner);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.more, false);
  assert.deepEqual(r.body.held, [
    { saleId: s2, code: 'EID5', promoId: 'r1', at: 2000 },
    { saleId: s2, code: 'NOROOZ', promoId: 'r2', at: 2000 },
    { saleId: s1, code: 'EID5', promoId: 'r1', at: 1000 },
  ]);
  const text = JSON.stringify(r.body);
  assert.ok(!text.includes(sb), 'فاکتورِ دکانِ «ب» در فهرستِ «الف» نیست');
  assert.ok(!/user|device/i.test(text), 'بی شناسهٔ کاربر و دستگاه');
  const rb = await held(b.owner);
  assert.deepEqual(rb.body.held.map((x) => x.saleId), [sb]);
});

test('سقفِ ۲۰۰ ردیف با more', async () => {
  const t = await shopWithTeam('سقف');
  await rule(t, 'r1', { code: 'MANY', maxUses: 1000 });
  const rows = [];
  for (let i = 0; i < 201; i++) rows.push(`('${t.shopId}','r1','01JBULK${String(i).padStart(19, '0')}','MANY','u','',${1000 + i},${1000 + i})`);
  await query(`INSERT INTO shop_promo_redemptions (shop_id, promo_id, sale_id, code, user_id, device_id, created_at, updated_at) VALUES ${rows.join(',')}`);
  const r = await held(t.owner);
  assert.equal(r.status, 200);
  assert.equal(r.body.held.length, 200);
  assert.equal(r.body.more, true);
  assert.equal(r.body.held[0].at, 1200, 'تازه‌ترین اول');
});

test('سرتاسر: لغوِ شاگرد پس از مهلت آزاد نمی‌کند؛ صاحب در فهرست می‌بیند، آزاد می‌کند و ظرفیت برمی‌گردد', async () => {
  const t = await shopWithTeam('سرتاسر');
  await rule(t, 'r1', { code: 'ONE1', maxUses: 1 });
  const s1 = saleId();
  assert.equal((await redeem(t.staff, s1, ['ONE1'])).status, 200);
  await age(t.shopId, s1);
  const denied = await release(t.staff, s1);
  assert.equal(denied.status, 403);
  assert.equal(denied.body.error.code, 'release_not_allowed');
  //  ظرفیت پر است ⇒ فاکتورِ تازه رد می‌شود
  assert.equal((await redeem(t.staff, saleId(), ['ONE1'])).status, 409);

  const list = await held(t.owner);
  assert.deepEqual(list.body.held.map((x) => [x.saleId, x.code]), [[s1, 'ONE1']]);
  const ok = await release(t.owner, s1);
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.equal(ok.body.released, 1);
  assert.deepEqual((await held(t.owner)).body.held, []);
  //  همان آزاد کردن دوباره بی اثر است
  assert.equal((await release(t.owner, s1)).body.released, 0);
  assert.equal((await redeem(t.staff, saleId(), ['ONE1'])).status, 200, 'ظرفیت برگشت');
});
