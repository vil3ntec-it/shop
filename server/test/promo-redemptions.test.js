'use strict';
/**
 * PRM-02 — دفترِ مصرفِ کدِ تخفیف (`/api/shop/promos/redeem`، مهاجرتِ ۱۳۶)، پشتِ
 * پرچمِ خاموشِ `shop.promo_uses`:
 *   • پرچم خاموش ⇒ همان رفتارِ امروز: redeem/release ۴۰۳ و «سقفِ مصرف» رد؛
 *   • سقف سخت است: آخرین ظرفیت فقط یک بار، حتی با درخواست‌های هم‌زمان؛
 *   • تکرارِ همان فاکتور مصرفِ دوم نمی‌سازد؛ چند کدِ یک فاکتور همه یا هیچ؛
 *   • آزاد کردن: خودِ صندوقدار تا مهلت، صاحب/مدیر (sales.cancel) همیشه؛
 *   • سقفِ نرخ برای هر کاربر؛ جداییِ دکان‌ها؛ بی دادهٔ شخصی در پاسخ.
 */
process.env.RATE_PROMO_REDEEM_MAX = '40';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { many, query } = require('../src/db');

test.before(async () => { await h.start(); });
test.after(async () => { await h.stop(); });

const P10 = { name: 'ده درصد', kind: 'percent', bp: 1000 };
let seq = 0;
const saleId = () => `01JSALE${String(++seq).padStart(19, '0')}`;

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
  const staff2 = await join(`شاگردِ دوم ${label}`, code);
  const inv = await h.post('/api/shop/invite', { role: 'manager' }, { token: owner.accessToken });
  assert.equal(inv.status, 201, JSON.stringify(inv.body));
  const manager = await join(`مدیر ${label}`, inv.body.code);
  const shopId = made.body.shop.id;
  if (flag) await setFlag(shopId, true);
  return { owner: owner.accessToken, staff, staff2, manager, shopId };
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
  return r.body.rule;
}

const redeem = (t, tok, sale, codes) => h.post('/api/shop/promos/redeem', { saleId: sale, codes }, { token: tok });
const release = (t, tok, sale, codes) => h.post('/api/shop/promos/redeem/release', { saleId: sale, ...(codes ? { codes } : {}) }, { token: tok });
const usesOf = async (t, id) => (await h.get('/api/shop/promos', { token: t.staff })).body.rules.find((r) => r.id === id).uses;
const live = async (shopId) => (await many(
  `SELECT promo_id, sale_id FROM shop_promo_redemptions WHERE shop_id=$1 AND released_at IS NULL ORDER BY promo_id, sale_id`, [shopId]));

test('پرچم خاموش: redeem و release ۴۰۳، سقفِ مصرف همان «پشتیبانی نمی‌شود»، redeem=false', async () => {
  const t = await shopWithTeam('خاموش', { flag: false });
  await rule(t, 'c1', { code: 'OFF10' });
  const r = await redeem(t, t.staff, saleId(), ['OFF10']);
  assert.equal(r.status, 403);
  assert.equal(r.body.error.code, 'promo_uses_disabled');
  const rel = await release(t, t.manager, saleId());
  assert.equal(rel.status, 403);
  assert.equal(rel.body.error.code, 'promo_uses_disabled');
  const capped = await h.put('/api/shop/promos/rules/c2', { rule: { ...P10, code: 'CAP2', maxUses: 2 } }, { token: t.owner });
  assert.equal(capped.status, 400);
  assert.equal(capped.body.error.code, 'max_uses_unsupported');
  const g = await h.get('/api/shop/promos', { token: t.staff });
  assert.equal(g.body.redeem, false);
  assert.equal((await live(t.shopId)).length, 0);
  //  پرچمِ `all` هم روشن می‌کند و پرچمِ خودِ دکان بر آن مقدم است
  await setFlag('all', true);
  try {
    assert.equal((await h.get('/api/shop/promos', { token: t.staff })).body.redeem, true);
    await setFlag(t.shopId, false);
    assert.equal((await h.get('/api/shop/promos', { token: t.staff })).body.redeem, false);
  } finally {
    await query(`DELETE FROM live_config WHERE key='shop.promo_uses' AND scope IN ('all',$1)`, [t.shopId]);
  }
});

test('پرچم روشن: سقف فقط عددِ صحیحِ ۰ تا ۱٬۰۰۰٬۰۰۰ و uses فقط از دفتر', async () => {
  const t = await shopWithTeam('سقف');
  for (const maxUses of ['10', 2.5, -1, true, [5], 1_000_001, { n: 1 }]) {
    const bad = await h.put('/api/shop/promos/rules/c1', { rule: { ...P10, code: 'CAP10', maxUses } }, { token: t.owner });
    assert.equal(bad.status, 400, `${JSON.stringify(maxUses)} ⇒ ${JSON.stringify(bad.body)}`);
    assert.equal(bad.body.error.code, 'invalid_rule');
    assert.deepEqual(bad.body.error.fields, ['uses']);
  }
  const ok = await rule(t, 'c1', { code: 'CAP10', maxUses: 10, uses: 7 });
  assert.equal(ok.maxUses, 10);
  assert.equal(ok.uses, 0, 'uses ِ برنامه نادیده');
  assert.equal((await rule(t, 'c0', { code: 'FREE0', maxUses: null })).maxUses, 0);
  assert.equal((await h.get('/api/shop/promos', { token: t.staff })).body.redeem, true);
});

test('سقفِ سخت: دو بار مصرف، سومی used_up؛ تکرارِ همان فاکتور مصرفِ دوم نمی‌سازد', async () => {
  const t = await shopWithTeam('سخت');
  await rule(t, 'c2', { code: 'TWO2', maxUses: 2 });
  const [s1, s2, s3] = [saleId(), saleId(), saleId()];

  const a = await redeem(t, t.staff, s1, ['two-2']);
  assert.equal(a.status, 200, JSON.stringify(a.body));
  assert.deepEqual(a.body.codes, [{ code: 'TWO2', promoId: 'c2', status: 'redeemed', uses: 1, maxUses: 2 }]);
  //  همان فاکتور دوباره (تلاشِ پس از قطعِ شبکه) ⇒ «پیش‌تر»، بی مصرفِ دوم
  const again = await redeem(t, t.staff, s1, ['TWO2']);
  assert.equal(again.status, 200);
  assert.deepEqual(again.body.codes, [{ code: 'TWO2', promoId: 'c2', status: 'already', uses: 1, maxUses: 2 }]);
  assert.equal(await usesOf(t, 'c2'), 1);

  assert.equal((await redeem(t, t.staff2, s2, ['TWO2'])).body.codes[0].uses, 2);
  const full = await redeem(t, t.staff, s3, ['TWO2']);
  assert.equal(full.status, 409);
  assert.equal(full.body.error.code, 'coupon_rejected');
  assert.deepEqual(full.body.error.codes, [{ code: 'TWO2', promoId: 'c2', status: 'used_up', uses: 2, maxUses: 2 }]);
  assert.equal(await usesOf(t, 'c2'), 2);
  assert.deepEqual((await live(t.shopId)).map((r) => r.sale_id), [s1, s2].sort());
  //  فاکتوری که پیش‌تر گرفته، حتی با سقفِ پُر، همان «پیش‌تر» است
  assert.equal((await redeem(t, t.staff, s2, ['TWO2'])).body.codes[0].status, 'already');

  //  سقف پایین‌تر از مصرف آمد ⇒ فقط مصرفِ تازه بسته است، سابقه می‌ماند
  await rule(t, 'c2', { code: 'TWO2', maxUses: 1 });
  assert.equal(await usesOf(t, 'c2'), 2);
  assert.equal((await redeem(t, t.staff, saleId(), ['TWO2'])).status, 409);
  const audit = await many(`SELECT action, detail FROM audit_logs WHERE shop_id=$1 AND action='promos.redeem'`, [t.shopId]);
  assert.equal(audit.length, 2, 'هر مصرفِ تازه یک سابقه؛ «پیش‌تر» نه');
  assert.deepEqual(audit.map((a) => (typeof a.detail === 'string' ? JSON.parse(a.detail) : a.detail).promos), ['c2', 'c2']);
});

test('چند کدِ یک فاکتور همه یا هیچ؛ کدِ ناشناخته، پاک‌شده و خاموش رد؛ بی سقف هم شمرده می‌شود', async () => {
  const t = await shopWithTeam('همه');
  await rule(t, 'one', { code: 'ONE1', maxUses: 1 });
  await rule(t, 'free', { code: 'FREE1', stack: 'combine' });
  await rule(t, 'off', { code: 'OFF1', active: false });
  await rule(t, 'gone', { code: 'GONE1' });
  assert.equal((await h.del('/api/shop/promos/rules/gone', { token: t.owner })).status, 200);

  assert.equal((await redeem(t, t.staff, saleId(), ['ONE1'])).status, 200);
  const s = saleId();
  const mixed = await redeem(t, t.staff, s, ['FREE1', 'ONE1']);
  assert.equal(mixed.status, 409);
  assert.deepEqual(mixed.body.error.codes.map((c) => [c.code, c.status]), [['FREE1', 'ok'], ['ONE1', 'used_up']]);
  assert.equal((await live(t.shopId)).filter((r) => r.sale_id === s).length, 0, 'هیچ‌کدام نماند');
  assert.equal(await usesOf(t, 'free'), 0);

  for (const [code, status] of [['NOPE1', 'unknown_code'], ['GONE1', 'unknown_code'], ['OFF1', 'inactive']]) {
    const r = await redeem(t, t.staff, saleId(), [code]);
    assert.equal(r.status, 409, code);
    assert.deepEqual(r.body.error.codes.map((c) => [c.code, c.status]), [[code, status]]);
  }
  //  بی سقف ⇒ پذیرفته و شمرده (برای گزارش)؛ دو شکلِ یک کد یک بار
  const f = await redeem(t, t.staff, saleId(), ['free1', 'FREE 1']);
  assert.equal(f.status, 200, JSON.stringify(f.body));
  assert.deepEqual(f.body.codes, [{ code: 'FREE1', promoId: 'free', status: 'redeemed', uses: 1, maxUses: 0 }]);
});

test('ورودیِ نادرست ۴۰۰؛ فقط عضوِ نویسنده؛ دکانِ دیگر کد را نمی‌بیند', async () => {
  const t = await shopWithTeam('ورودی');
  const o = await shopWithTeam('دیگر');
  await rule(t, 'c', { code: 'MINE1', maxUses: 5 });
  for (const body of [
    { saleId: 'bad id', codes: ['MINE1'] }, { saleId: '', codes: ['MINE1'] }, { saleId: 'x'.repeat(65), codes: ['MINE1'] },
    { saleId: saleId(), codes: [] }, { saleId: saleId(), codes: 'MINE1' }, { saleId: saleId(), codes: [5] },
    { saleId: saleId(), codes: ['--'] }, { saleId: saleId(), codes: Array.from({ length: 11 }, (_, i) => `CODE${i}X`) },
  ]) {
    const r = await h.post('/api/shop/promos/redeem', body, { token: t.staff });
    assert.equal(r.status, 400, `${JSON.stringify(body)} ⇒ ${JSON.stringify(r.body)}`);
  }
  assert.equal((await h.post('/api/shop/promos/redeem', { saleId: saleId(), codes: ['MINE1'] })).status, 401);
  const other = await redeem(o, o.staff, saleId(), ['MINE1']);
  assert.equal(other.status, 409);
  assert.equal(other.body.error.codes[0].status, 'unknown_code');
  assert.equal(await usesOf(t, 'c'), 0);
  //  release ِ دکانِ دیگر به فاکتورِ این دکان نمی‌رسد
  const s = saleId();
  assert.equal((await redeem(t, t.staff, s, ['MINE1'])).status, 200);
  assert.equal((await release(o, o.owner, s)).body.released, 0);
  assert.equal(await usesOf(t, 'c'), 1);
});

test('آزاد کردن: خودِ صندوقدار تا مهلت، شاگردِ دیگر نه، مدیر همیشه؛ تکرار بی‌اثر؛ ظرفیت برمی‌گردد', async () => {
  const t = await shopWithTeam('آزاد');
  await rule(t, 'c1', { code: 'SOLO1', maxUses: 1 });
  const s1 = saleId();
  assert.equal((await redeem(t, t.staff, s1, ['SOLO1'])).status, 200);

  const stranger = await release(t, t.staff2, s1);
  assert.equal(stranger.status, 403);
  assert.equal(stranger.body.error.code, 'release_not_allowed');
  assert.equal(await usesOf(t, 'c1'), 1);

  const mine = await release(t, t.staff, s1);
  assert.equal(mine.status, 200, JSON.stringify(mine.body));
  assert.equal(mine.body.released, 1);
  assert.equal((await release(t, t.staff, s1)).body.released, 0, 'تکرار بی‌اثر');
  assert.equal(await usesOf(t, 'c1'), 0);

  //  ظرفیت برگشت: فاکتورِ دیگر می‌گیرد؛ همان فاکتورِ آزادشده دوباره فقط با ظرفیت
  const s2 = saleId();
  assert.equal((await redeem(t, t.staff2, s2, ['SOLO1'])).status, 200);
  assert.equal((await redeem(t, t.staff, s1, ['SOLO1'])).status, 409);

  //  پس از مهلت خودِ شاگرد هم نه؛ مدیر (sales.cancel) همیشه
  await query(`UPDATE shop_promo_redemptions SET created_at = created_at - 3600000, updated_at = updated_at - 3600000
                WHERE shop_id=$1 AND sale_id=$2`, [t.shopId, s2]);
  const late = await release(t, t.staff2, s2);
  assert.equal(late.status, 403);
  assert.equal(late.body.error.code, 'release_not_allowed');
  const byManager = await release(t, t.manager, s2, ['solo-1']);
  assert.equal(byManager.status, 200);
  assert.equal(byManager.body.released, 1);
  const rows = await many(`SELECT released_by FROM shop_promo_redemptions WHERE shop_id=$1 AND sale_id=$2`, [t.shopId, s2]);
  assert.equal(rows.length, 1, 'ردیف پاک نمی‌شود');
  assert.ok(rows[0].released_by);
  const audit = await many(`SELECT action FROM audit_logs WHERE shop_id=$1 AND action='promos.release'`, [t.shopId]);
  assert.equal(audit.length, 2);
});

test('هم‌زمانی: سقفِ ۱ و هشت فاکتورِ هم‌زمان ⇒ دقیقاً یکی می‌گیرد', async () => {
  const t = await shopWithTeam('هم‌زمان');
  await rule(t, 'last', { code: 'LAST1', maxUses: 1 });
  const tokens = [t.staff, t.staff2, t.manager, t.owner];
  const out = await Promise.all(Array.from({ length: 8 }, (_, i) => redeem(t, tokens[i % 4], saleId(), ['LAST1'])));
  assert.deepEqual(out.map((r) => r.status).sort(), [200, 409, 409, 409, 409, 409, 409, 409]);
  assert.equal((await live(t.shopId)).length, 1);
  assert.equal(await usesOf(t, 'last'), 1);
});

test('سقفِ نرخ برای هر کاربر (RATE_PROMO_REDEEM_MAX) — کاربرِ دیگر بسته نمی‌شود', async () => {
  const t = await shopWithTeam('نرخ');
  await rule(t, 'r', { code: 'RATE1' });
  const codes = [];
  for (let i = 0; i < 41; i++) codes.push((await redeem(t, t.staff, saleId(), ['RATE1'])).status);
  assert.deepEqual(codes, [...Array(40).fill(200), 429]);
  assert.equal((await redeem(t, t.staff2, saleId(), ['RATE1'])).status, 200);
});
