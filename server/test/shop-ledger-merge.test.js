'use strict';
/**
 * پرچمِ ادغامِ سه‌طرفهٔ دفترِ برنامه — `GET /api/shop/ledger-merge/config` (#407، ذخیرهٔ کهنه).
 *
 * برنامهٔ کهنه (بی `v`): همان قاعدهٔ پیشین بیت‌به‌بیت — پیش‌فرض خاموش؛ روشن فقط با `live_config` برای همان دکان یا
 * `all`؛ مقدارِ دکان بر `all` جلو می‌افتد؛ فقط `true` روشن است؛ بی توکن ۴۰۱.
 * برنامهٔ تازه (`?v=2`، از ۱۴۰۵/۰۸/۱۸): **پیش‌فرض روشن**؛ فقط `false` ِ صریح (دکان بر `all`) یا «غیرفعال کردنِ سرویس»
 * خاموشش می‌کند؛ پمپ هرگز به این در نمی‌رسد.
 */
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, newId } = require('../src/db');

test.before(async () => { await h.start(); });
test.after(async () => { await h.stop(); });

const C = '/api/shop/ledger-merge/config';
const C2 = `${C}?v=2`;

async function shop(label) {
  const owner = await h.newUser(`صاحب ${label}`);
  const made = await h.post('/api/shop', { name: `دکان ${label}` }, { token: owner.accessToken });
  return { shopId: made.body.shop.id, token: owner.accessToken };
}

const flag = (scope, value) => query(
  "INSERT INTO live_config (scope, key, value, updated_at) VALUES ($1,'shop.ledger_merge',$2,1) "
  + 'ON CONFLICT (scope, key) DO UPDATE SET value=EXCLUDED.value',
  [String(scope), value],
);

test('پرچمِ shop.ledger_merge: پیش‌فرض خاموش؛ روشن برای همین دکان یا all؛ دکان بر all جلو؛ بی توکن ۴۰۱', async () => {
  const a = await shop('الف');
  const b = await shop('ب');
  assert.equal((await h.get(C)).status, 401);
  assert.deepEqual((await h.get(C, { token: a.token })).body, { enabled: false });

  await flag(a.shopId, 'true');
  assert.deepEqual((await h.get(C, { token: a.token })).body, { enabled: true });
  assert.deepEqual((await h.get(C, { token: b.token })).body, { enabled: false }, 'دکانِ دیگر خاموش می‌ماند');

  await flag('all', 'true');
  assert.deepEqual((await h.get(C, { token: b.token })).body, { enabled: true });
  await flag(a.shopId, 'false');
  assert.deepEqual((await h.get(C, { token: a.token })).body, { enabled: false }, 'مقدارِ دکان بر all جلو می‌افتد');

  await flag(b.shopId, '"yes"');
  assert.deepEqual((await h.get(C, { token: b.token })).body, { enabled: false }, 'فقط true روشن است');
  await query("DELETE FROM live_config WHERE key='shop.ledger_merge'");
});

test('برنامهٔ تازه (?v=2): پیش‌فرض روشن؛ فقط false ِ صریح خاموش؛ دکان بر all؛ مقدارِ نادرست همان پیش‌فرض؛ برنامهٔ کهنه همان پیشین', async () => {
  const a = await shop('ج');
  const b = await shop('د');
  assert.equal((await h.get(C2)).status, 401);
  assert.deepEqual((await h.get(C2, { token: a.token })).body, { enabled: true, v: 2 });
  assert.deepEqual((await h.get(C, { token: a.token })).body, { enabled: false }, 'برنامهٔ کهنه (بی v) همان پیش‌فرضِ خاموشِ پیشین');
  assert.deepEqual((await h.get(`${C}?v=1`, { token: a.token })).body, { enabled: false });
  assert.deepEqual((await h.get(`${C}?v=abc`, { token: a.token })).body, { enabled: false });

  await flag('all', 'false');
  assert.deepEqual((await h.get(C2, { token: a.token })).body, { enabled: false, v: 2 }, 'خاموش برای همه');
  await flag(b.shopId, 'true');
  assert.deepEqual((await h.get(C2, { token: b.token })).body, { enabled: true, v: 2 }, 'مقدارِ دکان بر all جلو می‌افتد');
  await flag(a.shopId, 'false');
  await flag('all', 'true');
  assert.deepEqual((await h.get(C2, { token: a.token })).body, { enabled: false, v: 2 }, 'خاموشِ همین دکان');
  assert.deepEqual((await h.get(C2, { token: b.token })).body, { enabled: true, v: 2 });
  await flag(a.shopId, '"no"');
  assert.deepEqual((await h.get(C2, { token: a.token })).body, { enabled: true, v: 2 }, 'مقدارِ نادرست ⇒ پیش‌فرضِ روشن');
  await query("DELETE FROM live_config WHERE key='shop.ledger_merge'");

  //  «غیرفعال کردنِ سرویس» ِ پنلِ مرکزی برای همین کلید هر مقداری را می‌بُرد
  await query(
    "INSERT INTO shop_holds (id, shop_id, kind, service, reason, placed_by, placed_at) VALUES ($1,$2,'service_disabled','shop.ledger_merge','آزمون','admin',1)",
    [newId('hold'), a.shopId],
  );
  assert.deepEqual((await h.get(C2, { token: a.token })).body, { enabled: false, v: 2 });
  assert.deepEqual((await h.get(C2, { token: b.token })).body, { enabled: true, v: 2 }, 'دکانِ دیگر دست نمی‌خورد');
  await query("DELETE FROM shop_holds WHERE service='shop.ledger_merge'");
});

const C3 = `${C}?v=3`;
const baseFlag = (scope, value) => query(
  "INSERT INTO live_config (scope, key, value, updated_at) VALUES ($1,'shop.save_base',$2,1) "
  + 'ON CONFLICT (scope, key) DO UPDATE SET value=EXCLUDED.value',
  [String(scope), value],
);

test('پایهٔ صریحِ ذخیره (?v=3): base پیش‌فرض روشن؛ فقط false ِ صریح یا نگه‌داشت؛ دکان بر all؛ v=2 و بی v همان پیشین', async () => {
  const a = await shop('ه');
  const b = await shop('و');
  assert.equal((await h.get(C3)).status, 401);
  assert.deepEqual((await h.get(C3, { token: a.token })).body, { enabled: true, v: 2, base: true, shadowFile: false });
  //  برنامه‌های نصب‌شده (v=2، بی v) بایت‌به‌بایت همان پاسخِ پیشین
  assert.deepEqual((await h.get(C2, { token: a.token })).body, { enabled: true, v: 2 });
  assert.deepEqual((await h.get(C, { token: a.token })).body, { enabled: false });

  await baseFlag('all', 'false');
  assert.deepEqual((await h.get(C3, { token: a.token })).body, { enabled: true, v: 2, base: false, shadowFile: false }, 'خاموش برای همه');
  assert.deepEqual((await h.get(C2, { token: a.token })).body, { enabled: true, v: 2 });
  await baseFlag(b.shopId, 'true');
  assert.equal((await h.get(C3, { token: b.token })).body.base, true, 'مقدارِ دکان بر all جلو می‌افتد');
  await baseFlag(b.shopId, '"no"');
  assert.equal((await h.get(C3, { token: b.token })).body.base, true, 'مقدارِ نادرست ⇒ پیش‌فرضِ روشن');
  await query("DELETE FROM live_config WHERE key='shop.save_base' AND scope='all'");

  //  دو پرچمِ جدا: خاموشیِ یکی دیگری را نمی‌برد
  await flag(a.shopId, 'false');
  assert.deepEqual((await h.get(C3, { token: a.token })).body, { enabled: false, v: 2, base: true, shadowFile: false });
  await query("DELETE FROM live_config WHERE key='shop.ledger_merge'");

  await query(
    "INSERT INTO shop_holds (id, shop_id, kind, service, reason, placed_by, placed_at) VALUES ($1,$2,'service_disabled','shop.save_base','آزمون','admin',1)",
    [newId('hold'), a.shopId],
  );
  assert.deepEqual((await h.get(C3, { token: a.token })).body, { enabled: true, v: 2, base: false, shadowFile: false });
  assert.equal((await h.get(C3, { token: b.token })).body.base, true, 'دکانِ دیگر دست نمی‌خورد');
  await query("DELETE FROM shop_holds WHERE service='shop.save_base'");
  await query("DELETE FROM live_config WHERE key='shop.save_base'");
});

test('?v=3: همان enabled ِ v=2 و base، و shadowFile ِ shop.sync_shadow_file — پیش‌فرض خاموش؛ فقط true؛ دکان بر all؛ نگه‌داشت می‌بُرد', async () => {
  const C3 = `${C}?v=3`;
  const shadow = (scope, value) => query(
    "INSERT INTO live_config (scope, key, value, updated_at) VALUES ($1,'shop.sync_shadow_file',$2,1) "
    + 'ON CONFLICT (scope, key) DO UPDATE SET value=EXCLUDED.value',
    [String(scope), value],
  );
  const a = await shop('ه');
  const b = await shop('و');
  assert.equal((await h.get(C3)).status, 401);
  assert.deepEqual((await h.get(C3, { token: a.token })).body, { enabled: true, v: 2, base: true, shadowFile: false });
  assert.deepEqual((await h.get(C2, { token: a.token })).body, { enabled: true, v: 2 }, 'v=2 همان پاسخِ پیشین');

  await shadow(a.shopId, 'true');
  assert.deepEqual((await h.get(C3, { token: a.token })).body, { enabled: true, v: 2, base: true, shadowFile: true });
  assert.deepEqual((await h.get(C3, { token: b.token })).body, { enabled: true, v: 2, base: true, shadowFile: false }, 'دکانِ دیگر خاموش');
  await shadow('all', 'true');
  await shadow(b.shopId, 'false');
  assert.equal((await h.get(C3, { token: b.token })).body.shadowFile, false, 'مقدارِ دکان بر all جلو می‌افتد');
  await shadow(b.shopId, '"yes"');
  assert.equal((await h.get(C3, { token: b.token })).body.shadowFile, false, 'فقط true روشن است');
  await flag(a.shopId, 'false');
  assert.deepEqual((await h.get(C3, { token: a.token })).body, { enabled: false, v: 2, base: true, shadowFile: true }, 'دو پرچم جدا');

  await query(
    "INSERT INTO shop_holds (id, shop_id, kind, service, reason, placed_by, placed_at) VALUES ($1,$2,'service_disabled','shop.sync_shadow_file','آزمون','admin',1)",
    [newId('hold'), a.shopId],
  );
  assert.equal((await h.get(C3, { token: a.token })).body.shadowFile, false, '«غیرفعال کردنِ سرویس» می‌بُرد');
  await query("DELETE FROM shop_holds WHERE service='shop.sync_shadow_file'");
  await query("DELETE FROM live_config WHERE key IN ('shop.sync_shadow_file','shop.ledger_merge')");
});

test('جدایی از پمپ: نشستِ برنامهٔ پمپ به این در نمی‌رسد و هیچ پرچمی نمی‌گیرد', async () => {
  const u = await h.newUser('صاحبِ پمپ', 'pump');
  const made = await h.post('/api/pump', { name: 'پمپ آزمون', code: 'lmerge-pump' }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  for (const path of [C, C2, C3]) {
    const r = await h.get(path, { token: u.accessToken });
    assert.ok([401, 403, 404].includes(r.status), `${path} ⇒ ${r.status} ${JSON.stringify(r.body)}`);
    assert.equal(r.body && r.body.enabled, undefined);
  }
});

