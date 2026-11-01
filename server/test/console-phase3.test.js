'use strict';
/**
 * پنلِ مرکزی، فاز ۳ — سمتِ سرورِ حساب (`docs/spec/ADMIN-CONTROL-PLANE.md` بندِ ۱۲).
 *
 *   ۱) `GET /api/admin/shops/:id/health` — فقط دادهٔ واقعی؛ نبود ⇒ null؛ دکانِ الف دادهٔ ب را نمی‌گیرد
 *   ۲) چهار حالتِ جدا + بایگانی: اثرِ هر کدام، گذار (گذاشتن/برداشتن/دوباره)، دلیلِ اجباری، سابقه در همان
 *      تراکنش، هیچ داده‌ای پاک نمی‌شود، کلیدِ یکتایی برای «پایانِ اشتراک»، جداییِ دکانِ الف و ب
 *   ۳) نقش‌های کمینه (finance، viewer) در سرور
 *   ۴) کلیدِ سرویسِ پلِ پنل + «انجام‌دهنده» در سابقه؛ راهِ پیشینِ ارشد هنوز کار می‌کند
 *   ۵) پرچم‌ها با تاریخچه (چه کسی، کی، پیشین ⇒ تازه، دلیل)
 *   ۶) مالکِ اصلی را ارشدِ دیگر غیرفعال/تنزل نمی‌دهد
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');

const PASS = 'Admin!12345';
const ids = {};

test.before(async () => {
  await h.start();
  await require('../src/lib/sync-v1').syncSchemaTable();
  const pw = require('../src/lib/password');
  const hash = await pw.hashPassword(PASS);
  const t = now();
  for (const [u, role, at] of [
    ['malek', 'superadmin', t - 10000], ['dovom', 'superadmin', t - 9000], ['poshtiban', 'admin', t - 8000],
    ['mali', 'finance', t - 7000], ['binande', 'viewer', t - 6000],
  ]) {
    ids[u] = newId('adm');
    await query(
      `INSERT INTO admins (id, username, name, password_hash, role, status, created_at) VALUES ($1,$2,$2,$3,$4,'active',$5)`,
      [ids[u], u, hash, role, at]
    );
  }
});
test.after(async () => { await h.stop(); });

const tokens = {};
async function tok(u) {
  if (!tokens[u]) {
    const r = await h.post('/api/admin/login', { username: u, password: PASS });
    assert.equal(r.status, 200, JSON.stringify(r.body));
    tokens[u] = r.body.token;
  }
  return tokens[u];
}

let seq = 0;
async function shopOwner(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { ...u, shopId: made.body.shop.id };
}

const idem = () => `p3-${Date.now()}-${++seq}`;
const place = async (who, shopId, body, headers = {}) => h.api('POST', `/api/admin/shops/${shopId}/holds`, { token: await tok(who), body, headers });
const lift = async (who, shopId, holdId, body, headers = {}) => h.api('POST', `/api/admin/shops/${shopId}/holds/${holdId}/lift`, { token: await tok(who), body, headers });
const auditRows = (action, targetId) => many(
  'SELECT * FROM audit_logs WHERE action=$1 AND target_id=$2 ORDER BY created_at, id', [action, String(targetId)]
);
const detailOf = (row) => (typeof row.detail === 'string' ? JSON.parse(row.detail) : row.detail);
const members = (o) => h.get('/api/shop/members', { token: o.accessToken });

/** همهٔ ردیف‌های پولی و دفتری که هیچ حالتی نباید پاک کند. */
async function ledgerCounts(shopId) {
  const q1 = async (sql) => Number((await one(sql, [shopId])).n);
  return {
    subs: await q1('SELECT COUNT(*)::int n FROM subscriptions WHERE shop_id=$1'),
    subsRows: JSON.stringify(await many('SELECT id, status, ends_at FROM subscriptions WHERE shop_id=$1 ORDER BY id', [shopId])),
    pays: await q1(`SELECT COUNT(*)::int n FROM sub_payments WHERE tenant_id=$1`),
    sync: await q1(`SELECT COUNT(*)::int n FROM sync_rows WHERE account_id=$1`),
    oplog: await q1(`SELECT COUNT(*)::int n FROM oplog WHERE account_id=$1`),
    members: await q1(`SELECT COUNT(*)::int n FROM shop_members WHERE shop_id=$1`),
  };
}

const syncPush = (o, deviceId, version, ops) => h.api('POST', '/api/sync/v1/push', {
  token: o.accessToken,
  body: { device_id: deviceId, schema_version: 2, ops },
  headers: { 'X-App': 'shop', 'X-Device': deviceId, 'X-App-Version': version },
});
const op = (rowId) => ({
  op_id: `01J${String(Date.now()).slice(-9)}${String(++seq).padStart(5, '0')}`, ts: Date.now(),
  table: 'debtors', row_id: rowId, type: 'insert', fields: { name: 'قرض‌دار' },
});

/* ── ۱) سلامتِ دکان ──────────────────────────────────────────────── */

test('سلامت: دادهٔ واقعی (نسخهٔ هر دستگاه، آخرین همگام‌سازی، خطا، اشتراک)؛ نبود ⇒ null؛ الف دادهٔ ب را نمی‌گیرد', async () => {
  const a = await shopOwner('سلامتِ الف');
  const b = await shopOwner('سلامتِ ب');

  //  دکانِ تازه: هیچ همگام‌سازی‌ای نشده ⇒ null، نه صفر و نه «سالم»
  const empty = await h.get(`/api/admin/shops/${a.shopId}/health`, { token: await tok('poshtiban') });
  assert.equal(empty.status, 200, JSON.stringify(empty.body));
  assert.equal(empty.body.sync.lastSyncAt, null);
  assert.equal(empty.body.sync.v1.lastPushAt, null);
  assert.equal(empty.body.sync.v1.behindMax, null);
  assert.deepEqual(empty.body.devices, []);
  assert.equal(empty.body.errors.lastAt, null);
  assert.equal(empty.body.errors.lastAppVersion, null);
  assert.equal(empty.body.subscription.source, 'trial');
  assert.equal(empty.body.state.access, 'open');

  assert.equal((await syncPush(a, 'dev-a1', '2.14.5', [op('r-a1')])).status, 200);
  assert.equal((await syncPush(a, 'dev-a2', '2.14.3', [op('r-a2')])).status, 200);
  assert.equal((await syncPush(b, 'dev-b1', '9.9.9', [op('r-b1')])).status, 200);
  const er = await h.api('POST', '/api/errors', {
    token: a.accessToken, body: { message: 'خطای آزمون' }, headers: { 'X-App': 'shop', 'X-App-Version': '2.14.3', 'X-Device': 'dev-a2' },
  });
  assert.ok(er.status < 300, JSON.stringify(er.body));

  const r = await h.get(`/api/admin/shops/${a.shopId}/health`, { token: await tok('poshtiban') });
  assert.equal(r.status, 200);
  const devs = r.body.devices.map((d) => `${d.engine}:${d.deviceId}:${d.appVersion}`).sort();
  assert.deepEqual(devs, ['v1:dev-a1:2.14.5', 'v1:dev-a2:2.14.3'], '⛔ فقط دستگاه‌های همین دکان');
  assert.ok(r.body.sync.lastSyncAt > 0);
  assert.equal(r.body.sync.v1.devices, 2);
  assert.deepEqual(r.body.versions.map((x) => x.appVersion).sort(), ['2.14.3', '2.14.5']);
  assert.equal(r.body.errors.last24h, 1);
  assert.equal(r.body.errors.lastAppVersion, '2.14.3');
  assert.ok(!JSON.stringify(r.body).includes('9.9.9'), '⛔ نسخهٔ دستگاهِ دکانِ ب در سلامتِ الف');
  assert.ok(!JSON.stringify(r.body).includes('خطای آزمون'), 'متنِ خطا فراداده نیست');

  const rb = await h.get(`/api/admin/shops/${b.shopId}/health`, { token: await tok('poshtiban') });
  assert.equal(rb.body.errors.last24h, 0, '⛔ خطای الف در سلامتِ ب');
  assert.deepEqual(rb.body.devices.map((d) => d.deviceId), ['dev-b1']);

  assert.equal((await h.get('/api/admin/shops/shp_nist/health', { token: await tok('poshtiban') })).status, 404);
  assert.equal((await h.get(`/api/admin/shops/${a.shopId}/health`, {})).status, 401);
  assert.equal((await h.get(`/api/admin/shops/${a.shopId}/health`, { token: a.accessToken })).status, 401);
  //  نقش‌های باریک هم می‌خوانند
  assert.equal((await h.get(`/api/admin/shops/${a.shopId}/health`, { token: await tok('mali') })).status, 200);
  assert.equal((await h.get(`/api/admin/shops/${a.shopId}/health`, { token: await tok('binande') })).status, 200);
});

/* ── ۲) چهار حالت + بایگانی ────────────────────────────────────────── */

test('تعلیقِ دسترسی: اعضا ۴۰۳ shop_suspended (API، همگام‌سازی، دکانِ تازه)؛ دکانِ ب دست نمی‌خورد؛ برداشتن ⇒ برمی‌گردد؛ هیچ داده‌ای نمی‌رود', async () => {
  const a = await shopOwner('تعلیقِ الف');
  const b = await shopOwner('تعلیقِ ب');
  assert.equal((await syncPush(a, 'dev-s1', '2.14.5', [op('r-s1')])).status, 200);
  const before = await ledgerCounts(a.shopId);

  const noReason = await place('poshtiban', a.shopId, { kind: 'access_suspended' });
  assert.equal(noReason.status, 400);
  assert.equal(noReason.body.error.code, 'reason_required');

  const p = await place('poshtiban', a.shopId, { kind: 'access_suspended', reason: 'پرداختِ برگشتی' });
  assert.equal(p.status, 201, JSON.stringify(p.body));
  assert.equal(p.body.state.access, 'blocked');
  assert.deepEqual(p.body.state.blockedBy, ['access_suspended']);

  const m = await members(a);
  assert.equal(m.status, 403);
  assert.equal(m.body.error.code, 'shop_suspended');
  const sy = await syncPush(a, 'dev-s1', '2.14.5', [op('r-s2')]);
  assert.equal(sy.status, 403);
  assert.equal(sy.body.error.code, 'shop_suspended');
  const evade = await h.post('/api/shop', { name: 'دکانِ دوم' }, { token: a.accessToken });
  assert.equal(evade.status, 403, '⛔ صاحبِ دکانِ معلق با ساختنِ دکانِ تازه دورش نمی‌زند');
  assert.equal(evade.body.error.code, 'shop_suspended');
  assert.equal((await members(b)).status, 200, '⛔ تعلیقِ الف دکانِ ب را نبست');

  const again = await place('poshtiban', a.shopId, { kind: 'access_suspended', reason: 'دوباره' });
  assert.equal(again.status, 409);
  assert.equal(again.body.error.code, 'hold_exists');

  const l = await lift('poshtiban', a.shopId, p.body.hold.id, { reason: 'پرداخت رسید' });
  assert.equal(l.status, 200, JSON.stringify(l.body));
  assert.equal(l.body.state.access, 'open');
  assert.equal((await members(a)).status, 200);
  const twice = await lift('poshtiban', a.shopId, p.body.hold.id, { reason: 'دوباره' });
  assert.equal(twice.status, 409);
  assert.equal(twice.body.error.code, 'hold_already_lifted');

  assert.deepEqual(await ledgerCounts(a.shopId), before, '⛔ تعلیق و برداشتن هیچ ردیفی پاک نکرد');
  const placed = await auditRows('admin.shop_hold_placed', p.body.hold.id);
  const lifted = await auditRows('admin.shop_hold_lifted', p.body.hold.id);
  assert.equal(placed.length, 1);
  assert.equal(lifted.length, 1);
  assert.equal(placed[0].shop_id, a.shopId);
  assert.equal(detailOf(placed[0]).reason, 'پرداختِ برگشتی');
  assert.equal(detailOf(lifted[0]).reason, 'پرداخت رسید');

  //  تاریخچه می‌ماند و گذاشتنِ دوباره ردیفِ تازه است
  const re = await place('poshtiban', a.shopId, { kind: 'access_suspended', reason: 'بارِ دوم' });
  assert.equal(re.status, 201);
  const list = await h.get(`/api/admin/shops/${a.shopId}/holds`, { token: await tok('mali') });
  assert.equal(list.status, 200);
  assert.equal(list.body.history.length, 2);
  assert.equal(list.body.open.length, 1);
  await lift('poshtiban', a.shopId, re.body.hold.id, { reason: 'پایانِ آزمون' });
});

test('غیرفعال کردنِ یک سرویس: فقط همان سرویس و فقط همان دکان؛ برداشتن ⇒ برمی‌گردد', async () => {
  const a = await shopOwner('سرویسِ الف');
  const b = await shopOwner('سرویسِ ب');
  await require('../src/lib/live-config').set('all', 'shop.chat', true, 'test');
  const cfg = async (o) => (await h.get('/api/shop/chat/config', { token: o.accessToken })).body.enabled;
  assert.equal(await cfg(a), true);

  const bad = await place('poshtiban', a.shopId, { kind: 'service_disabled', service: 'shop.nist', reason: 'آزمون' });
  assert.equal(bad.status, 400);
  assert.equal(bad.body.error.code, 'bad_service');

  const p = await place('poshtiban', a.shopId, { kind: 'service_disabled', service: 'shop.chat', reason: 'شکایتِ مشتری' });
  assert.equal(p.status, 201, JSON.stringify(p.body));
  assert.deepEqual(p.body.state.servicesDisabled, ['shop.chat']);
  assert.equal(p.body.state.access, 'open', 'سرویس، نه دسترسی');
  assert.equal(await cfg(a), false);
  const gated = await h.get('/api/shop/chat/conversations', { token: a.accessToken });
  assert.equal(gated.status, 404);
  assert.equal(gated.body.error.code, 'chat_disabled');
  assert.equal((await members(a)).status, 200, 'بقیهٔ دکان باز است');
  assert.equal(await cfg(b), true, '⛔ سرویسِ دکانِ ب خاموش شد');

  //  پرچمِ خودِ دکان هم روشن باشد، حالت می‌بُرد
  await require('../src/lib/live-config').set(a.shopId, 'shop.chat', true, 'test');
  assert.equal(await cfg(a), false);

  const l = await lift('poshtiban', a.shopId, p.body.hold.id, { reason: 'رسیدگی شد' });
  assert.equal(l.status, 200);
  assert.equal(await cfg(a), true);
  await require('../src/lib/live-config').remove('all', 'shop.chat');
  await require('../src/lib/live-config').remove(a.shopId, 'shop.chat');
});

test('پایانِ اشتراک: فقط مالی/ارشد، فقط با Idempotency-Key، یک‌بار؛ قابلیت‌های پولی بسته؛ ردیفِ اشتراک و پول دست نمی‌خورد؛ برداشتن روزی اضافه نمی‌کند', async () => {
  const a = await shopOwner('اشتراکِ الف');
  const b = await shopOwner('اشتراکِ ب');
  const sup = await tok('malek');
  for (const o of [a, b]) {
    const g = await h.post('/api/admin/subscriptions', { shopId: o.shopId, plan: 'm1', days: 30 }, { token: sup });
    assert.equal(g.status, 201, JSON.stringify(g.body));
  }
  const pay = await h.post('/api/admin/payments', { app: 'shop', tenantId: a.shopId, amount: 1500 }, { token: sup });
  assert.ok(pay.status < 300, JSON.stringify(pay.body));
  const before = await ledgerCounts(a.shopId);
  const entOf = async (id) => require('../src/lib/entitlement').entitlementOf(id);
  assert.equal((await entOf(a.shopId)).source, 'subscription');

  const body = { kind: 'subscription_ended', reason: 'پرداخت نشد' };
  const asAdmin = await place('poshtiban', a.shopId, body, { 'Idempotency-Key': idem() });
  assert.equal(asAdmin.status, 403, '⛔ مدیرِ عادی کارِ پولی نمی‌کند');
  const noKey = await place('mali', a.shopId, body);
  assert.equal(noKey.status, 400);
  assert.equal(noKey.body.error.code, 'idempotency_key_required');

  const key = idem();
  const p = await place('mali', a.shopId, body, { 'Idempotency-Key': key });
  assert.equal(p.status, 201, JSON.stringify(p.body));
  const replay = await place('mali', a.shopId, body, { 'Idempotency-Key': key });
  assert.equal(replay.status, 200, 'تکرارِ همان درخواست ⇒ همان پاسخ');
  assert.equal(replay.headers.get('idempotent-replayed'), 'true');
  assert.equal(replay.body.hold.id, p.body.hold.id);
  const reused = await place('mali', a.shopId, { ...body, reason: 'متنِ دیگر' }, { 'Idempotency-Key': key });
  assert.equal(reused.status, 409);
  assert.equal(reused.body.error.code, 'idempotency_key_reused');
  assert.equal((await many(`SELECT id FROM shop_holds WHERE shop_id=$1 AND kind='subscription_ended'`, [a.shopId])).length, 1);
  assert.equal((await auditRows('admin.shop_hold_placed', p.body.hold.id)).length, 1, 'تکرار ردیفِ سابقهٔ دوم نساخت');

  const ent = await entOf(a.shopId);
  assert.equal(ent.source, 'free');
  assert.equal(ent.subscription.active, false);
  assert.equal(ent.subscription.endedByAdmin, true);
  assert.equal(ent.trial.active, false, '⛔ پایانِ اشتراک دورهٔ آزمایشی را باز نمی‌کند');
  assert.equal((await entOf(b.shopId)).source, 'subscription', '⛔ اشتراکِ دکانِ ب بسته شد');
  assert.equal((await members(a)).status, 200, 'دسترسی باز است — فقط قابلیت‌های پولی');
  const health = await h.get(`/api/admin/shops/${a.shopId}/health`, { token: await tok('mali') });
  assert.equal(health.body.subscription.endedByAdmin, true);
  assert.deepEqual(await ledgerCounts(a.shopId), before, '⛔ ردیفِ اشتراک یا پرداخت عوض/پاک شد');

  const liftNoKey = await lift('mali', a.shopId, p.body.hold.id, { reason: 'پرداخت رسید' });
  assert.equal(liftNoKey.status, 400);
  const lk = idem();
  const l = await lift('mali', a.shopId, p.body.hold.id, { reason: 'پرداخت رسید' }, { 'Idempotency-Key': lk });
  assert.equal(l.status, 200, JSON.stringify(l.body));
  const l2 = await lift('mali', a.shopId, p.body.hold.id, { reason: 'پرداخت رسید' }, { 'Idempotency-Key': lk });
  assert.equal(l2.status, 200);
  assert.equal(l2.headers.get('idempotent-replayed'), 'true');
  const back = await entOf(a.shopId);
  assert.equal(back.source, 'subscription');
  assert.equal(JSON.stringify((await ledgerCounts(a.shopId)).subsRows), JSON.stringify(before.subsRows), 'روزی اضافه نشد');
});

test('درخواستِ حذفِ دائمی: فقط ارشد؛ دسترسی بسته؛ لغوشدنی؛ حذفِ واقعی فقط از راهِ تأییدشدهٔ موجود و برگشت ندارد', async () => {
  const a = await shopOwner('حذفِ الف');
  const asAdmin = await place('poshtiban', a.shopId, { kind: 'deletion_requested', reason: 'خواستِ صاحب' });
  assert.equal(asAdmin.status, 403);
  const p = await place('malek', a.shopId, { kind: 'deletion_requested', reason: 'خواستِ صاحب' });
  assert.equal(p.status, 201, JSON.stringify(p.body));
  assert.equal(p.body.state.deletionRequested, true);
  const m = await members(a);
  assert.equal(m.status, 403);
  assert.equal(m.body.error.code, 'shop_deletion_pending');
  const byAdmin = await lift('poshtiban', a.shopId, p.body.hold.id, { reason: 'پشیمان شد' });
  assert.equal(byAdmin.status, 403, '⛔ لغوِ درخواستِ حذف هم فقط ارشد');
  const cancel = await lift('malek', a.shopId, p.body.hold.id, { reason: 'پشیمان شد' });
  assert.equal(cancel.status, 200);
  assert.equal((await members(a)).status, 200);

  //  دوباره، و این بار حذفِ واقعی از راهِ موجود (تایپِ ایمیل) — برگشت ندارد
  const p2 = await place('malek', a.shopId, { kind: 'deletion_requested', reason: 'خواستِ قطعی' });
  assert.equal(p2.status, 201);
  const wrong = await h.del(`/api/admin/users/${a.user.id}`, { token: await tok('malek'), body: { confirm: 'x@y.z' } });
  assert.equal(wrong.status, 400, 'حذف بی تأیید نمی‌شود');
  const del = await h.api('DELETE', `/api/admin/users/${a.user.id}`, { token: await tok('malek'), body: { confirm: a.email } });
  assert.equal(del.status, 200, JSON.stringify(del.body));
  const gone = await lift('malek', a.shopId, p2.body.hold.id, { reason: 'برگردان' });
  assert.equal(gone.status, 404, 'حذفِ واقعی برگشت ندارد');
  assert.ok((await auditRows('admin.shop_hold_placed', p2.body.hold.id)).length === 1, 'سابقه پس از حذف هم می‌ماند');
});

test('بایگانی: دسترسی بسته، پالایشِ فهرست؛ با تعلیقِ هم‌زمان، برداشتنِ یکی دکان را باز نمی‌کند', async () => {
  const a = await shopOwner('بایگانیِ الف');
  const arch = await place('poshtiban', a.shopId, { kind: 'archived', reason: 'دکان بسته شد' });
  assert.equal(arch.status, 201);
  const m = await members(a);
  assert.equal(m.status, 403);
  assert.equal(m.body.error.code, 'shop_archived');
  const list = await h.get('/api/admin/shops?hold=archived&limit=200', { token: await tok('poshtiban') });
  assert.equal(list.status, 200);
  assert.ok(list.body.shops.some((s) => s.id === a.shopId && s.holds.includes('archived')));
  assert.ok(list.body.shops.every((s) => s.holds.includes('archived')));

  const sus = await place('poshtiban', a.shopId, { kind: 'access_suspended', reason: 'هم‌زمان' });
  assert.equal(sus.status, 201);
  await lift('poshtiban', a.shopId, arch.body.hold.id, { reason: 'بازگشایی' });
  const still = await members(a);
  assert.equal(still.status, 403, 'تعلیق هنوز باز است');
  assert.equal(still.body.error.code, 'shop_suspended');
  await lift('poshtiban', a.shopId, sus.body.hold.id, { reason: 'بازگشایی' });
  assert.equal((await members(a)).status, 200);
  assert.equal((await one('SELECT status FROM shops WHERE id=$1', [a.shopId])).status, 'active');
});

test('جداییِ دکانِ الف و ب: حالتِ الف از نشانیِ ب برداشته نمی‌شود؛ نوع و نقش در سرور', async () => {
  const a = await shopOwner('جداییِ الف');
  const b = await shopOwner('جداییِ ب');
  const p = await place('poshtiban', a.shopId, { kind: 'access_suspended', reason: 'آزمونِ جدایی' });
  assert.equal(p.status, 201);
  const cross = await lift('poshtiban', b.shopId, p.body.hold.id, { reason: 'از درِ ب' });
  assert.equal(cross.status, 404, '⛔ حالتِ الف از نشانیِ ب');
  assert.equal((await members(a)).status, 403, 'حالتِ الف هنوز باز است');
  assert.equal((await auditRows('admin.shop_hold_lifted', p.body.hold.id)).length, 0, 'ردیفِ سابقهٔ دروغ ساخته نشد');
  const listB = await h.get(`/api/admin/shops/${b.shopId}/holds`, { token: await tok('poshtiban') });
  assert.equal(listB.body.history.length, 0);
  const badKind = await place('poshtiban', a.shopId, { kind: 'delete_now', reason: 'آزمون' });
  assert.equal(badKind.status, 400);
  assert.equal((await place('poshtiban', 'shp_nist', { kind: 'archived', reason: 'آزمون' })).status, 404);
  //  نقش‌ها: مالی فقط «پایانِ اشتراک»، بیننده هیچ
  const f = await place('mali', b.shopId, { kind: 'access_suspended', reason: 'آزمون' });
  assert.equal(f.status, 403);
  const vw = await place('binande', b.shopId, { kind: 'archived', reason: 'آزمون' });
  assert.equal(vw.status, 403);
  assert.equal(vw.body.error.code, 'role_forbidden');
  await lift('poshtiban', a.shopId, p.body.hold.id, { reason: 'پایان' });
});

test('ردیفِ سابقه در همان تراکنش: نوشته نشد ⇒ حالتی هم گذاشته نمی‌شود (بی ردِ بی‌سابقه)', async () => {
  const a = await shopOwner('سابقهٔ اجباری');
  await query('ALTER TABLE audit_logs RENAME TO audit_logs_off');
  let r;
  try {
    r = await place('poshtiban', a.shopId, { kind: 'access_suspended', reason: 'بی سابقه نه' });
  } finally {
    await query('ALTER TABLE audit_logs_off RENAME TO audit_logs');
  }
  assert.equal(r.status, 500);
  assert.equal((await many('SELECT id FROM shop_holds WHERE shop_id=$1', [a.shopId])).length, 0, '⛔ حالت بی ردیفِ سابقه ماند');
  assert.equal((await one('SELECT status FROM shops WHERE id=$1', [a.shopId])).status, 'active');
  assert.equal((await members(a)).status, 200);
});

/* ── ۳) نقش‌های کمینه ───────────────────────────────────────────────── */

test('نقشِ مالی و بیننده: مرز در سرور، پیش‌فرض‌بسته', async () => {
  const o = await shopOwner('نقش‌ها');
  const fin = await tok('mali');
  const vw = await tok('binande');
  //  مالی: خواندنِ فراداده و کارِ اشتراک ✓
  assert.equal((await h.get('/api/admin/shops', { token: fin })).status, 200);
  assert.equal((await h.get('/api/admin/subscriptions', { token: fin })).status, 200);
  const g = await h.post('/api/admin/subscriptions', { shopId: o.shopId, plan: 'm1', days: 30 }, { token: fin });
  assert.equal(g.status, 201, JSON.stringify(g.body));
  //  مالی: پشتیبانی، کاربر، اعلانِ جمعی، تنظیم ✗
  for (const [m, p, body] of [
    ['GET', '/api/admin/support/threads'], ['GET', '/api/admin/users'], ['GET', `/api/admin/users/${o.user.id}`],
    ['POST', '/api/admin/support/broadcast', { body: 'x' }], ['PUT', `/api/admin/users/${o.user.id}/city`, { city: 'هرات' }],
    ['POST', `/api/admin/users/${o.user.id}/status`, { status: 'disabled' }], ['GET', '/api/admin/shop-desk/overview'],
    ['GET', '/API/ADMIN/USERS'],
  ]) {
    const r = await h.api(m, p, { token: fin, body: body || null });
    assert.equal(r.status, 403, `مالی ${m} ${p} ⇒ ${r.status}`);
    assert.equal(r.body.error.code, 'role_forbidden');
  }
  //  بیننده: هر خواندنِ مدیرِ عادی ✓، هیچ نوشتنی ✗
  assert.equal((await h.get('/api/admin/users', { token: vw })).status, 200);
  for (const [m, p, body] of [
    ['POST', '/api/admin/subscriptions', { shopId: o.shopId, plan: 'm1', days: 30 }],
    ['POST', `/api/admin/users/${o.user.id}/status`, { status: 'disabled' }],
    ['POST', '/api/admin/support/broadcast', { body: 'x' }],
  ]) {
    const r = await h.api(m, p, { token: vw, body });
    assert.equal(r.status, 403, `بیننده ${m} ${p} ⇒ ${r.status}`);
  }
  //  هر دو خودشان بیرون می‌روند و «من» را می‌بینند
  assert.equal((await h.get('/api/admin/me', { token: vw })).body.admin.role, 'viewer');
  assert.equal((await h.get('/api/admin/2fa', { token: fin })).status, 200);
  //  مدیرِ عادی همان اختیاراتِ دیروز (رفتار عوض نشد)
  assert.equal((await h.get('/api/admin/users', { token: await tok('poshtiban') })).status, 200);
});

/* ── ۴) کلیدِ سرویسِ پلِ پنل ──────────────────────────────────────── */

test('کلیدِ سرویس: فقط ارشد، یک بار دیده می‌شود؛ «انجام‌دهنده» اجباری و در سابقه؛ نقش = کمترینِ انجام‌دهنده و سقف؛ ابطال همان دم', async () => {
  const o = await shopOwner('پلِ پنل');
  const sup = await tok('malek');
  assert.equal((await h.post('/api/admin/service-keys', { name: 'پنل', roleCap: 'superadmin', reason: 'پل' }, { token: await tok('poshtiban') })).status, 403);
  assert.equal((await h.post('/api/admin/service-keys', { name: 'پنل', roleCap: 'superadmin' }, { token: sup })).status, 400);
  const made = await h.post('/api/admin/service-keys', { name: 'پنلِ خانه', roleCap: 'superadmin', reason: 'جایگزینِ رمزِ ارشد' }, { token: sup });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const key = made.body.key;
  assert.match(key, /^vsk_/);
  const list = await h.get('/api/admin/service-keys', { token: sup });
  assert.ok(!JSON.stringify(list.body).includes(key), '⛔ کلید دوباره برنمی‌گردد');
  assert.ok(!JSON.stringify(await many('SELECT * FROM audit_logs')).includes(key), '⛔ کلید در سابقه');
  assert.ok(!JSON.stringify(await many('SELECT * FROM admin_service_keys')).includes(key), '⛔ کلید خام ذخیره شد');

  const as = (role, extra = {}) => ({ 'X-Actor-Id': 'panel-user-7', 'X-Actor-Role': role, 'X-Actor-Name': encodeURIComponent('علی پنل'), ...extra });
  const noActor = await h.get('/api/admin/stats', { token: key });
  assert.equal(noActor.status, 400);
  assert.equal(noActor.body.error.code, 'actor_required');

  const me = await h.get('/api/admin/me', { token: key, headers: as('admin') });
  assert.equal(me.status, 200);
  assert.equal(me.body.actor.name, 'علی پنل');
  assert.equal(me.body.actor.effectiveRole, 'admin');
  assert.ok(!JSON.stringify(me.body).includes(key));

  //  نوشتن با انجام‌دهنده ⇒ سابقه انجام‌دهنده را دارد
  const p = await h.api('POST', `/api/admin/shops/${o.shopId}/holds`, { token: key, headers: as('admin'), body: { kind: 'archived', reason: 'از پنل' } });
  assert.equal(p.status, 201, JSON.stringify(p.body));
  const row = (await auditRows('admin.shop_hold_placed', p.body.hold.id))[0];
  assert.equal(row.user_id, made.body.serviceKey.id, 'کدام ماشین');
  assert.deepEqual(
    { id: detailOf(row).actor.id, name: detailOf(row).actor.name, role: detailOf(row).actor.role },
    { id: 'panel-user-7', name: 'علی پنل', role: 'admin' }, 'کدام آدم'
  );
  assert.equal(p.body.hold.placedActor.id, 'panel-user-7');
  //  سابقهٔ درهای پیشین هم (بی هیچ تغییری در آن‌ها) انجام‌دهنده را می‌گیرد
  const city = await h.api('PUT', `/api/admin/users/${o.user.id}/city`, { token: key, headers: as('admin'), body: { city: 'بلخ' } });
  assert.equal(city.status, 200);
  assert.equal(detailOf((await auditRows('admin.user_city', o.user.id)).at(-1)).actor.id, 'panel-user-7');

  //  بیننده از راهِ پل نمی‌نویسد — سرور خودش می‌سنجد
  const vw = await h.api('POST', `/api/admin/shops/${o.shopId}/holds/${p.body.hold.id}/lift`, { token: key, headers: as('viewer'), body: { reason: 'x12' } });
  assert.equal(vw.status, 403);
  //  سقفِ کلید: انجام‌دهندهٔ «ارشد» با کلیدِ سقفِ admin ارشد نمی‌شود
  const capped = await h.post('/api/admin/service-keys', { name: 'محدود', roleCap: 'admin', reason: 'آزمونِ سقف' }, { token: sup });
  const cap = capped.body.key;
  const supOnly = await h.get('/api/admin/release/beta', { token: cap, headers: as('superadmin') });
  assert.equal(supOnly.status, 403, '⛔ سقفِ کلید شکسته شد');
  assert.equal((await h.get('/api/admin/release/beta', { token: key, headers: as('superadmin') })).status, 200);
  //  کلیدِ سرویس به مدیران و کلیدها دست ندارد
  for (const path of ['/api/admin/admins', '/api/admin/service-keys']) {
    const r = await h.get(path, { token: key, headers: as('superadmin') });
    assert.equal(r.status, 403, `${path} ⇒ ${r.status}`);
    assert.equal(r.body.error.code, 'service_forbidden');
  }
  //  ابطال ⇒ همان دم ۴۰۱؛ راهِ پیشین (ورودِ ارشد) هنوز کار می‌کند
  const rv = await h.post(`/api/admin/service-keys/${capped.body.serviceKey.id}/revoke`, { reason: 'آزمون' }, { token: sup });
  assert.equal(rv.status, 200);
  const dead = await h.get('/api/admin/stats', { token: cap, headers: as('admin') });
  assert.equal(dead.status, 401);
  assert.equal(dead.body.error.code, 'invalid_service_key');
  assert.equal((await h.get('/api/admin/stats', { token: sup })).status, 200);
  //  کلیدِ مالک را ارشدِ دیگر باطل نمی‌کند
  const other = await h.post(`/api/admin/service-keys/${made.body.serviceKey.id}/revoke`, { reason: 'خاموش کن' }, { token: await tok('dovom') });
  assert.equal(other.status, 403);
  assert.equal(other.body.error.code, 'owner_protected');
  assert.equal((await h.get('/api/admin/stats', { token: key, headers: as('viewer') })).status, 200);
  await lift('poshtiban', o.shopId, p.body.hold.id, { reason: 'پایان' });
});

/* ── ۵) پرچم‌ها با تاریخچه ─────────────────────────────────────────── */

test('پرچم‌ها: نوشتن فقط ارشد با دلیل؛ تاریخچهٔ پیشین ⇒ تازه، چه کسی، کی، چرا — از هر دری', async () => {
  const sup = await tok('malek');
  const noReason = await h.put('/api/admin/flags', { scope: 'all', key: 'shop.receipt_v2', value: true }, { token: sup });
  assert.equal(noReason.status, 400);
  assert.equal((await h.put('/api/admin/flags', { scope: 'all', key: 'shop.receipt_v2', value: true, reason: 'آزمون' }, { token: await tok('poshtiban') })).status, 403);

  const t0 = now();
  const s1 = await h.put('/api/admin/flags', { scope: 'all', key: 'shop.receipt_v2', value: true, reason: 'روشن برای همه' }, { token: sup });
  assert.equal(s1.status, 200, JSON.stringify(s1.body));
  const s2 = await h.put('/api/admin/flags', { scope: 'all', key: 'shop.receipt_v2', value: false, reason: 'خطا داشت' }, { token: sup });
  assert.equal(s2.status, 200);
  //  درِ پیشینِ پمپ (بی دلیلِ اجباری) هم رد می‌گذارد
  const pump = await h.put('/api/admin/pump/live-config', { scope: 'all', key: 'pump.test', value: 3 }, { token: sup });
  assert.equal(pump.status, 200);
  const del = await h.api('DELETE', '/api/admin/flags', { token: sup, body: { scope: 'all', key: 'shop.receipt_v2', reason: 'برگشت به پیش‌فرض' } });
  assert.equal(del.status, 200);
  assert.equal(del.body.removed, true);

  const hist = await h.get('/api/admin/flags/history?scope=all&key=shop.receipt_v2', { token: await tok('mali') });
  assert.equal(hist.status, 200);
  const e = hist.body.entries;
  assert.deepEqual(e.map((x) => [x.action, x.oldValue, x.newValue, x.reason]), [
    ['remove', false, null, 'برگشت به پیش‌فرض'],
    ['set', true, false, 'خطا داشت'],
    ['set', null, true, 'روشن برای همه'],
  ]);
  assert.ok(e.every((x) => x.at >= t0 && x.changedBy === 'malek' && x.actor.id === ids.malek));
  const page = await h.get(`/api/admin/flags/history?scope=all&key=shop.receipt_v2&limit=1&before=${e[0].at}`, { token: sup });
  assert.ok(page.body.entries.length <= 1);
  const ph = await h.get('/api/admin/flags/history?scope=all&key=pump.test', { token: await tok('binande') });
  assert.equal(ph.body.entries.length, 1);
  assert.equal(ph.body.entries[0].newValue, 3);
  const flags = await h.get('/api/admin/flags?scope=all', { token: await tok('binande') });
  assert.equal(flags.status, 200);
  assert.ok(flags.body.known.some((k) => k.key === 'shop.orders'));
  assert.ok(flags.body.items.some((i) => i.key === 'pump.test'));
  assert.equal((await auditRows('admin.flag_set', 'all:shop.receipt_v2')).length, 2);
});

/* ── ۶) مالکِ اصلی ─────────────────────────────────────────────────── */

test('مالکِ اصلی: ارشدِ دوم نه غیرفعالش می‌کند نه تنزلش می‌دهد؛ مالک ارشدِ دوم را می‌تواند', async () => {
  const list = await h.get('/api/admin/admins', { token: await tok('malek') });
  const owner = list.body.admins.find((a) => a.is_owner);
  assert.equal(owner.username, 'malek', 'پیشین‌ترین ارشد مالک است');
  assert.equal(list.body.admins.filter((a) => a.is_owner).length, 1);
  const second = await tok('dovom');
  for (const body of [{ status: 'disabled' }, { role: 'admin' }, { role: 'viewer' }]) {
    const r = await h.api('PATCH', `/api/admin/admins/${ids.malek}`, { token: second, body });
    assert.equal(r.status, 403, JSON.stringify(body));
    assert.equal(r.body.error.code, 'owner_protected');
  }
  assert.equal((await h.get('/api/admin/me', { token: await tok('malek') })).body.admin.isOwner, true);
  //  مالک هنوز ارشد و فعال است
  const row = await one('SELECT role, status FROM admins WHERE id=$1', [ids.malek]);
  assert.deepEqual({ ...row }, { role: 'superadmin', status: 'active' });
  //  نقشِ تازه ساختنی است و مالک ارشدِ دوم را تنزل می‌دهد
  const demote = await h.api('PATCH', `/api/admin/admins/${ids.dovom}`, { token: await tok('malek'), body: { role: 'finance' } });
  assert.equal(demote.status, 200, JSON.stringify(demote.body));
  const back = await h.api('PATCH', `/api/admin/admins/${ids.dovom}`, { token: await tok('malek'), body: { role: 'superadmin' } });
  assert.equal(back.status, 200);
  const made = await h.post('/api/admin/admins', { username: 'mali2', password: 'Mali!12345', role: 'finance' }, { token: await tok('malek') });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  assert.equal(made.body.admin.role, 'finance');
});
