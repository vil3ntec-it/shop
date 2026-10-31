'use strict';
/**
 * `/api/shop/promos` — کلیدِ دکان (پیش‌فرض خاموش)، قاعده‌ها با `validate`ِ همان
 * هسته، مجوزِ صاحب/مدیر/شاگرد، کدِ یکتا که پس از پاک شدن هم آزاد نمی‌شود،
 * «زیرِ بها» فقط با صاحب، و جداییِ دکان‌ها.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const h = require('./helpers');
const { many, query } = require('../src/db');

const ROOT = path.join(__dirname, '..', '..');

test.before(async () => { await h.start(); });
test.after(async () => { await h.stop(); });

async function shopWithTeam(label) {
  const owner = await h.newUser(`صاحب ${label}`);
  const made = await h.post('/api/shop', { name: `دکان ${label}` }, { token: owner.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const code = (await h.get('/api/shop/staff-code', { token: owner.accessToken })).body.code;
  const staff = await h.newUser(`شاگرد ${label}`);
  assert.ok([200, 201].includes((await h.post('/api/shop/join', { code }, { token: staff.accessToken })).status));
  const inv = await h.post('/api/shop/invite', { role: 'manager' }, { token: owner.accessToken });
  assert.equal(inv.status, 201, JSON.stringify(inv.body));
  const manager = await h.newUser(`مدیر ${label}`);
  assert.ok([200, 201].includes((await h.post('/api/shop/join', { code: inv.body.code }, { token: manager.accessToken })).status));
  return { owner: owner.accessToken, staff: staff.accessToken, manager: manager.accessToken, shopId: made.body.shop.id };
}

const P10 = { name: 'ده درصد', kind: 'percent', bp: 1000 };

test('نسخهٔ سرورِ هسته عیناً همان نسخهٔ وب است', () => {
  assert.equal(
    fs.readFileSync(path.join(__dirname, '..', 'src', 'lib', 'promo-core.js'), 'utf8'),
    fs.readFileSync(path.join(ROOT, 'license', 'promo-core.js'), 'utf8'));
});

test('پیش‌فرض خاموش و بی قاعده؛ هر عضو می‌بیند، فقط صاحب و مدیر می‌نویسند', async () => {
  const t = await shopWithTeam('الف');
  for (const [tok, manage, below] of [[t.owner, true, true], [t.manager, true, false], [t.staff, false, false]]) {
    const r = await h.get('/api/shop/promos', { token: tok });
    assert.equal(r.status, 200, JSON.stringify(r.body));
    assert.equal(r.body.enabled, false);
    assert.deepEqual(r.body.rules, []);
    assert.deepEqual(r.body.permissions, { manage, belowCost: below });
  }
  assert.equal((await h.put('/api/shop/promos/settings', { enabled: true }, { token: t.staff })).status, 403);
  assert.equal((await h.put('/api/shop/promos/rules/r1', { rule: P10 }, { token: t.staff })).status, 403);
  assert.equal((await h.get('/api/shop/promos')).status, 401);

  assert.equal((await h.put('/api/shop/promos/settings', { enabled: 'yes' }, { token: t.manager })).status, 400);
  const on = await h.put('/api/shop/promos/settings', { enabled: true }, { token: t.manager });
  assert.equal(on.status, 200, JSON.stringify(on.body));
  assert.equal((await h.get('/api/shop/promos', { token: t.staff })).body.enabled, true);
  const logs = await many(`SELECT action FROM audit_logs WHERE shop_id=$1 AND action LIKE 'promos.%'`, [t.shopId]);
  assert.deepEqual(logs.map((l) => l.action), ['promos.settings']);
});

test('قاعده: اعتبارسنجیِ هسته، کدِ نرمال، uses فقط از سرور، پاک کردنِ نرم', async () => {
  const t = await shopWithTeam('ب');
  const bad = await h.put('/api/shop/promos/rules/r1', { rule: { ...P10, bp: 0 } }, { token: t.owner });
  assert.equal(bad.status, 400);
  assert.equal(bad.body.error.code, 'invalid_rule');
  assert.deepEqual(bad.body.error.fields, ['bp']);
  assert.equal((await h.put('/api/shop/promos/rules/bad%20id', { rule: P10 }, { token: t.owner })).status, 400);
  assert.equal((await h.put('/api/shop/promos/rules/r1', { rule: [] }, { token: t.owner })).status, 400);

  //  سقفِ مصرف تا شمارشش نیامده رد می‌شود — نه این‌که بی‌صدا بی‌اثر بماند؛ نوعِ دیگر هم ("10" ⇏ ۰ ِ بی‌صدا)
  for (const maxUses of [1, 10, '10', '0', 2.5, -1, true, [5], 1e300]) {
    const capped = await h.put('/api/shop/promos/rules/r1', { rule: { ...P10, code: 'CAP10', maxUses } }, { token: t.owner });
    assert.equal(capped.status, 400, `${JSON.stringify(maxUses)} ⇒ ${JSON.stringify(capped.body)}`);
    assert.equal(capped.body.error.code, 'max_uses_unsupported');
  }
  assert.equal((await many(`SELECT id FROM shop_promotions WHERE shop_id=$1`, [t.shopId])).length, 0);

  const ok = await h.put('/api/shop/promos/rules/r1',
    { rule: { ...P10, code: 'sale-۱۰', uses: 5, maxUses: 0, stack: 'combine' } }, { token: t.manager });
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.equal(ok.body.rule.code, 'SALE10');
  assert.equal(ok.body.rule.uses, 0);
  assert.equal(ok.body.rule.id, 'r1');
  assert.equal(ok.body.rule.rev, 1);

  const again = await h.put('/api/shop/promos/rules/r1', { rule: { ...P10, code: 'SALE10', bp: 1500 } }, { token: t.owner });
  assert.equal(again.body.rule.rev, 2);
  assert.equal(again.body.rule.bp, 1500);

  //  کدِ یکتا؛ پس از پاک شدن هم آزاد نمی‌شود (PRM-02)
  const dup = await h.put('/api/shop/promos/rules/r2', { rule: { ...P10, code: 'Sale 10' } }, { token: t.owner });
  assert.equal(dup.status, 409);
  assert.equal(dup.body.error.code, 'code_taken');
  assert.equal((await h.del('/api/shop/promos/rules/r1', { token: t.staff })).status, 403);
  assert.equal((await h.del('/api/shop/promos/rules/r1', { token: t.manager })).status, 200);
  assert.equal((await h.del('/api/shop/promos/rules/r1', { token: t.manager })).status, 404);
  assert.equal((await h.put('/api/shop/promos/rules/r2', { rule: { ...P10, code: 'SALE10' } }, { token: t.owner })).status, 409);
  const list = await h.get('/api/shop/promos', { token: t.staff });
  assert.deepEqual(list.body.rules, []);
  const kept = await many(`SELECT id, deleted FROM shop_promotions WHERE shop_id=$1`, [t.shopId]);
  assert.deepEqual(kept.map((k) => [k.id, k.deleted]), [['r1', true]]);
});

test('سقفِ مصرف: بی سقف پذیرفته؛ ردیفِ پیش از این PR با سقف ویرایش‌پذیر و سقفش صفر می‌شود', async () => {
  const t = await shopWithTeam('سقف');
  for (const maxUses of [0, null, undefined]) {
    const free = await h.put('/api/shop/promos/rules/free', { rule: { ...P10, maxUses } }, { token: t.owner });
    assert.equal(free.status, 200, JSON.stringify(free.body));
    assert.equal(free.body.rule.maxUses, 0);
  }
  //  ردیفی که پیش از این PR با سقف نوشته شد (آن سقف هرگز شمرده نمی‌شد)
  const ok = await h.put('/api/shop/promos/rules/old', { rule: { ...P10, code: 'OLD10' } }, { token: t.owner });
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  await query(`UPDATE shop_promotions SET data = jsonb_set(data, '{maxUses}', '10') WHERE shop_id=$1 AND id='old'`, [t.shopId]);
  const legacy = (await h.get('/api/shop/promos', { token: t.owner })).body.rules.find((r) => r.id === 'old');
  assert.equal(legacy.maxUses, 10);
  //  مقدارِ تازه هنوز رد است
  const raised = await h.put('/api/shop/promos/rules/old', { rule: { ...legacy, maxUses: 11 } }, { token: t.owner });
  assert.equal(raised.status, 400);
  assert.equal(raised.body.error.code, 'max_uses_unsupported');
  const asText = await h.put('/api/shop/promos/rules/old', { rule: { ...legacy, maxUses: '10' } }, { token: t.owner });
  assert.equal(asText.status, 400);
  //  همان چیزی که فرمِ برنامهٔ پیشین می‌فرستد: قاعدهٔ پایه با همان سقف + خاموش ⇒ می‌نشیند و سقف صفر
  const off = await h.put('/api/shop/promos/rules/old', { rule: { ...legacy, active: false } }, { token: t.owner });
  assert.equal(off.status, 200, JSON.stringify(off.body));
  assert.equal(off.body.rule.maxUses, 0);
  assert.equal(off.body.rule.active, false);
  const stored = await many(`SELECT data->>'maxUses' AS m FROM shop_promotions WHERE shop_id=$1 AND id='old'`, [t.shopId]);
  assert.deepEqual(stored.map((r) => Number(r.m)), [0]);
  //  حالا که صفر است، ۱۰ ِ دوباره سقفِ تازه است
  assert.equal((await h.put('/api/shop/promos/rules/old', { rule: { ...legacy } }, { token: t.owner })).status, 400);
});

test('stamp = پرچمِ shop.promo_stamp ِ live_config — پیش‌فرض خاموش، مالِ همان دکان', async () => {
  const a = await shopWithTeam('مهر');
  const b = await shopWithTeam('مهرِ دوم');
  assert.equal((await h.get('/api/shop/promos', { token: a.staff })).body.stamp, false);
  await query("INSERT INTO live_config (scope, key, value, updated_at) VALUES ($1,'shop.promo_stamp','true',1)", [a.shopId]);
  assert.equal((await h.get('/api/shop/promos', { token: a.staff })).body.stamp, true);
  assert.equal((await h.get('/api/shop/promos', { token: b.staff })).body.stamp, false);
});

test('POS-19: پلهٔ قیمتِ ثابت فقط با پرچمِ shop.promo_tier_price، مالِ همان دکان؛ پلهٔ درصدی بی پرچم', async () => {
  const a = await shopWithTeam('پله');
  const b = await shopWithTeam('پلهٔ دوم');
  const rule = { name: 'عمده', kind: 'tiers', tiers: [{ min: 3000, unitMinor: 9000 }, { min: 6000, unitMinor: 8000 }] };
  assert.equal((await h.get('/api/shop/promos', { token: a.staff })).body.tierPrice, false);
  const off = await h.put('/api/shop/promos/rules/tp', { rule }, { token: a.owner });
  assert.equal(off.status, 403, JSON.stringify(off.body));
  assert.equal(off.body.error.code, 'tier_price_disabled');
  assert.deepEqual((await h.get('/api/shop/promos', { token: a.owner })).body.rules, []);
  //  پلهٔ درصدی همان امروز است و `unitMinor` در دادهٔ ذخیره‌شده‌اش نمی‌نشیند
  const pct = await h.put('/api/shop/promos/rules/tb', { rule: { name: 'درصدی', kind: 'tiers', tiers: [{ min: 3000, bp: 500 }] } }, { token: a.owner });
  assert.equal(pct.status, 200, JSON.stringify(pct.body));
  assert.deepEqual(pct.body.rule.tiers, [{ min: 3000, bp: 500, amountMinor: 0 }]);

  await query("INSERT INTO live_config (scope, key, value, updated_at) VALUES ($1,'shop.promo_tier_price','true',1)", [a.shopId]);
  assert.equal((await h.get('/api/shop/promos', { token: a.staff })).body.tierPrice, true);
  const on = await h.put('/api/shop/promos/rules/tp', { rule }, { token: a.owner });
  assert.equal(on.status, 200, JSON.stringify(on.body));
  assert.deepEqual(on.body.rule.tiers, [{ min: 3000, bp: 0, amountMinor: 0, unitMinor: 9000 }, { min: 6000, bp: 0, amountMinor: 0, unitMinor: 8000 }]);
  //  قیمتِ پلهٔ بالاتر باید کمتر باشد — همان اعتبارسنجیِ هسته
  const up = await h.put('/api/shop/promos/rules/tq', { rule: { ...rule, tiers: [{ min: 3000, unitMinor: 9000 }, { min: 6000, unitMinor: 9500 }] } }, { token: a.owner });
  assert.equal(up.status, 400);
  assert.deepEqual(up.body.error.fields, ['tiers']);
  //  دکانِ دیگر پرچم ندارد
  assert.equal((await h.put('/api/shop/promos/rules/tp', { rule }, { token: b.owner })).status, 403);
  assert.equal((await h.get('/api/shop/promos', { token: b.staff })).body.tierPrice, false);
});

test('«زیرِ بهای خرید» فقط با صاحب', async () => {
  const t = await shopWithTeam('پ');
  const rule = { ...P10, allowBelowCost: true };
  const m = await h.put('/api/shop/promos/rules/below', { rule }, { token: t.manager });
  assert.equal(m.status, 403);
  assert.equal(m.body.error.code, 'below_cost_owner_only');
  const o = await h.put('/api/shop/promos/rules/below', { rule }, { token: t.owner });
  assert.equal(o.status, 200);
  //  مدیر هم نمی‌تواند قاعدهٔ «زیرِ بها»یِ صاحب را با همان پرچم دوباره بنویسد
  assert.equal((await h.put('/api/shop/promos/rules/below', { rule: { ...rule, bp: 9000 } }, { token: t.manager })).status, 403);
  assert.equal((await h.put('/api/shop/promos/rules/below', { rule: { ...rule, allowBelowCost: false } }, { token: t.manager })).status, 200);
});

test('جداییِ دکان‌ها', async () => {
  const a = await shopWithTeam('ت');
  const b = await shopWithTeam('ث');
  assert.equal((await h.put('/api/shop/promos/rules/same', { rule: { ...P10, code: 'TWIN1' } }, { token: a.owner })).status, 200);
  //  همان شناسه و همان کد در دکانِ دیگر آزاد است و ردیفِ دکانِ اول را دست نمی‌زند
  assert.equal((await h.put('/api/shop/promos/rules/same', { rule: { ...P10, name: 'دکانِ دوم', code: 'TWIN1' } }, { token: b.owner })).status, 200);
  assert.equal((await h.get('/api/shop/promos', { token: a.staff })).body.rules[0].name, 'ده درصد');
  assert.equal((await h.get('/api/shop/promos', { token: b.staff })).body.rules[0].name, 'دکانِ دوم');
  await h.put('/api/shop/promos/settings', { enabled: true }, { token: a.owner });
  assert.equal((await h.get('/api/shop/promos', { token: b.owner })).body.enabled, false);
});
