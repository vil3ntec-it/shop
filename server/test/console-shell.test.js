'use strict';
/**
 * پوستهٔ کنسولِ «فروشگاه» ِ پنل (ریپوی server، ماژول‌های ۴.۱/۴.۳/۴.۱۲ و جست‌وجوی سراسری) — سمتِ سرورِ حساب.
 *
 *   ۱) نشست‌های یک مدیر: فهرست (بی توکن و بی هشِ کامل)، بستنِ یکی یا همه، دلیلِ اجباری، سابقه؛ نشست‌های مالکِ اصلی
 *      را فقط خودش می‌بندد؛ فقط ارشد؛ کلیدِ سرویس هرگز
 *   ۲) ساختنِ مدیر: نقشِ نگفته ⇒ «فقط‌خواندن» (کمینهٔ دسترسی)؛ عوض کردنِ نقش/وضعیت ⇒ «پیشین ⇒ تازه» و دلیل در سابقه
 *   ۳) پرچمِ شناخته فقط مقدارِ هم‌نوع و scope ِ خودش؛ کلیدِ ناشناخته همان رفتارِ پیشین
 *   ۴) تنظیماتِ مرکزی (`PATCH /config`) ⇒ «پیشین ⇒ تازه» و دلیل در سابقه
 *   ۵) جست‌وجوی سراسری: به نقش، بی الگوی `%`، سفارش بی دادهٔ خریدار، مدیرها فقط برای ارشدِ انسانی
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');

const PASS = 'Admin!12345';
const ids = {};

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  const hash = await pw.hashPassword(PASS);
  const t = now();
  for (const [u, role, at] of [
    ['malek', 'superadmin', t - 10000], ['dovom', 'superadmin', t - 9000], ['modir', 'admin', t - 8000],
    ['mali', 'finance', t - 7000], ['binande', 'viewer', t - 6000],
  ]) {
    ids[u] = newId('adm');
    await query(
      `INSERT INTO admins (id, username, name, password_hash, role, status, created_at) VALUES ($1,$2,$2,$3,$4,'active',$5)`,
      [ids[u], u, hash, role, at]
    );
  }
  //  مالکِ اصلی همان پیشین‌ترین ارشد است (مهاجرتِ ۱۶۸ پیش از درجِ این‌ها دویده)
  await query('UPDATE admins SET is_owner = (id = $1)', [ids.malek]);
});
test.after(async () => { await h.stop(); });

const login = async (u) => {
  const r = await h.post('/api/admin/login', { username: u, password: PASS });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body.token;
};
const auditOf = (action, targetId) => many(
  'SELECT * FROM audit_logs WHERE action=$1 AND target_id=$2 ORDER BY created_at, id', [action, String(targetId)]);
const detailOf = (row) => (typeof row.detail === 'string' ? JSON.parse(row.detail) : row.detail);

/* ── ۱) نشست‌های مدیر ─────────────────────────────────────────────── */

test('نشست‌های مدیر: فهرست بی توکن، بستنِ یکی/همه با دلیل، مالکِ اصلی محفوظ، فقط ارشد', async () => {
  const sup = await login('dovom');
  const m1 = await login('modir');
  const m2 = await login('modir');

  const list = await h.get(`/api/admin/admins/${ids.modir}/sessions`, { token: sup });
  assert.equal(list.status, 200, JSON.stringify(list.body));
  assert.equal(list.body.sessions.length, 2);
  for (const s of list.body.sessions) {
    assert.match(s.id, /^[0-9a-f]{16}$/);
    assert.ok(s.issuedAt > 0 && s.expiresAt > s.issuedAt);
  }
  const text = JSON.stringify(list.body);
  assert.ok(!text.includes(m1) && !text.includes(m2), '⛔ توکن در فهرست');
  const fullHashes = (await many(`SELECT token_hash FROM tokens WHERE subject_id=$1`, [ids.modir])).map((r) => r.token_hash);
  assert.ok(fullHashes.every((x) => !text.includes(x)), '⛔ هشِ کامل در فهرست');
  //  «همین نشست» درست گفته می‌شود
  const own = await h.get(`/api/admin/admins/${ids.dovom}/sessions`, { token: sup });
  assert.equal(own.body.sessions.filter((s) => s.current).length, 1);

  //  فقط ارشد
  for (const who of ['modir', 'mali', 'binande']) {
    const r = await h.get(`/api/admin/admins/${ids.modir}/sessions`, { token: await login(who) });
    assert.equal(r.status, 403, `${who} ⇒ ${r.status}`);
  }

  //  بی دلیل ⇒ ۴۰۰ و هیچ نشستی بسته نشد
  const noReason = await h.post(`/api/admin/admins/${ids.modir}/sessions/revoke`, {}, { token: sup });
  assert.equal(noReason.status, 400);
  assert.equal(noReason.body.error.code, 'reason_required');
  assert.equal((await h.get('/api/admin/me', { token: m1 })).status, 200);

  //  شناسهٔ بد / نشستِ دیگری ⇒ نه «انجام شد»
  assert.equal((await h.post(`/api/admin/admins/${ids.modir}/sessions/revoke`, { sessionId: 'x', reason: 'آزمون' }, { token: sup })).status, 400);
  const foreign = own.body.sessions[0].id;
  const wrong = await h.post(`/api/admin/admins/${ids.modir}/sessions/revoke`, { sessionId: foreign, reason: 'آزمون' }, { token: sup });
  assert.equal(wrong.status, 404);
  assert.equal(wrong.body.error.code, 'session_not_found');

  //  بستنِ یکی ⇒ همان یکی ۴۰۱، دیگری زنده
  const target = list.body.sessions.find((s) => s.id);
  const one1 = await h.post(`/api/admin/admins/${ids.modir}/sessions/revoke`, { sessionId: target.id, reason: 'دستگاهِ گم‌شده' }, { token: sup });
  assert.equal(one1.status, 200, JSON.stringify(one1.body));
  assert.equal(one1.body.revoked, 1);
  const alive = await Promise.all([m1, m2].map(async (t) => (await h.get('/api/admin/me', { token: t })).status));
  assert.deepEqual(alive.sort(), [200, 401]);

  //  همه ⇒ هر دو ۴۰۱؛ سابقه با دلیل و شمار، بی توکن
  const all = await h.post(`/api/admin/admins/${ids.modir}/sessions/revoke`, { reason: 'همه بیرون' }, { token: sup });
  assert.equal(all.status, 200);
  for (const t of [m1, m2]) assert.equal((await h.get('/api/admin/me', { token: t })).status, 401);
  const rows = await auditOf('admin.admin_sessions_revoked', ids.modir);
  assert.equal(rows.length, 2);
  assert.deepEqual(detailOf(rows[0]), { session: target.id, revoked: 1, reason: 'دستگاهِ گم‌شده' });
  assert.equal(detailOf(rows[1]).session, 'all');
  assert.equal(rows[1].user_id, ids.dovom);

  //  ⛔ مالکِ اصلی: ارشدِ دیگر نشست‌هایش را نمی‌بندد؛ خودش می‌تواند
  const ownerTok = await login('malek');
  const deny = await h.post(`/api/admin/admins/${ids.malek}/sessions/revoke`, { reason: 'بیرونش کن' }, { token: sup });
  assert.equal(deny.status, 403);
  assert.equal(deny.body.error.code, 'owner_protected');
  assert.equal((await h.get('/api/admin/me', { token: ownerTok })).status, 200);
  const extra = await login('malek');
  const sessions = (await h.get(`/api/admin/admins/${ids.malek}/sessions`, { token: ownerTok })).body.sessions;
  const other = sessions.find((s) => !s.current);
  const self = await h.post(`/api/admin/admins/${ids.malek}/sessions/revoke`, { sessionId: other.id, reason: 'نشستِ کهنهٔ خودم' }, { token: ownerTok });
  assert.equal(self.status, 200);
  assert.equal((await h.get('/api/admin/me', { token: extra })).status, 401);
  assert.equal((await h.get('/api/admin/me', { token: ownerTok })).status, 200);
});

test('کلیدِ سرویس به نشست‌های مدیر دست ندارد (SERVICE_DENY)', async () => {
  const sup = await login('malek');
  const made = await h.post('/api/admin/service-keys', { name: 'پل', roleCap: 'superadmin', reason: 'آزمونِ پوسته' }, { token: sup });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const as = { 'X-Actor-Id': 'p1', 'X-Actor-Role': 'superadmin', 'X-Actor-Name': 'owner' };
  for (const [method, path] of [['GET', `/api/admin/admins/${ids.modir}/sessions`], ['POST', `/api/admin/admins/${ids.modir}/sessions/revoke`]]) {
    const r = await h.api(method, path, { token: made.body.key, headers: as, body: method === 'POST' ? { reason: 'از کلید' } : null });
    assert.equal(r.status, 403, `${method} ${path} ⇒ ${r.status}`);
    assert.equal(r.body.error.code, 'service_forbidden');
  }
  //  جست‌وجو با کلید: حتی انجام‌دهندهٔ «ارشد» مدیرها را نمی‌بیند
  const s = await h.get('/api/admin/search?q=modir', { token: made.body.key, headers: as });
  assert.equal(s.status, 200, JSON.stringify(s.body));
  assert.ok(!s.body.kinds.includes('admins'));
  assert.equal(s.body.results.admins, undefined);
});

/* ── ۲) ساختن و نقشِ مدیر ─────────────────────────────────────────── */

test('ساختنِ مدیر بی نقش ⇒ viewer؛ تغییرِ نقش/وضعیت ⇒ «پیشین ⇒ تازه» و دلیل در سابقه', async () => {
  const sup = await login('malek');
  const made = await h.post('/api/admin/admins', { username: 'tazeh', password: 'Tazeh!12345x', reason: 'همکارِ تازه' }, { token: sup });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  assert.equal(made.body.admin.role, 'viewer', '⛔ پیش‌فرضِ ناامن');
  const created = await auditOf('admin.admin_created', made.body.admin.id);
  assert.equal(detailOf(created[0]).reason, 'همکارِ تازه');
  assert.ok(!JSON.stringify(created).includes('Tazeh!12345x'), '⛔ رمز در سابقه');

  const up = await h.api('PATCH', `/api/admin/admins/${made.body.admin.id}`, { token: sup, body: { role: 'finance', reason: 'کارِ مالی' } });
  assert.equal(up.status, 200, JSON.stringify(up.body));
  const off = await h.api('PATCH', `/api/admin/admins/${made.body.admin.id}`, { token: sup, body: { status: 'disabled', reason: 'رفت' } });
  assert.equal(off.status, 200);
  const rows = await auditOf('admin.admin_updated', made.body.admin.id);
  assert.deepEqual(detailOf(rows[0]), { status: 'active', role: 'finance', from: { status: 'active', role: 'viewer' }, reason: 'کارِ مالی' });
  assert.deepEqual(detailOf(rows[1]).from, { status: 'active', role: 'finance' });
  assert.equal(detailOf(rows[1]).status, 'disabled');
  //  مالکِ اصلی هنوز محفوظ (همان قاعدهٔ فاز ۳)
  const deny = await h.api('PATCH', `/api/admin/admins/${ids.malek}`, { token: await login('dovom'), body: { role: 'viewer', reason: 'x12' } });
  assert.equal(deny.status, 403);
});

/* ── ۳) پرچمِ شناخته، هم‌نوع و در scope ِ خودش ───────────────────── */

test('پرچمِ شناخته: مقدارِ هم‌نوع و scope ِ خودش؛ کلیدِ ناشناخته مثلِ پیش', async () => {
  const sup = await login('malek');
  const put = (body) => h.put('/api/admin/flags', { reason: 'آزمونِ نوع', ...body }, { token: sup });
  const bad = [
    [{ scope: 'all', key: 'shop.receipt_v2', value: 'true' }, 'bad_flag_value'],
    [{ scope: 'all', key: 'shop.receipt_v2', value: 1 }, 'bad_flag_value'],
    [{ scope: 'security', key: 'shop.receipt_v2', value: true }, 'bad_flag_scope'],
    [{ scope: 'no-such-shop', key: 'shop.receipt_v2', value: true }, 'bad_flag_scope'],
    [{ scope: 'all', key: 'hashchain.audit', value: true }, 'bad_flag_scope'],
    [{ scope: 'shop1', key: 'image_bank.central', value: true }, 'bad_flag_scope'],
    [{ scope: 'release', key: 'beta.shops', value: 'x1' }, 'bad_flag_value'],
    [{ scope: 'release', key: 'beta.shops', value: ['bad id!'] }, 'bad_flag_value'],
  ];
  for (const [body, code] of bad) {
    const r = await put(body);
    assert.equal(r.status, 400, `${JSON.stringify(body)} ⇒ ${r.status}`);
    assert.equal(r.body.error.code, code, JSON.stringify(body));
  }
  assert.equal(Number((await one(`SELECT COUNT(*)::int n FROM live_config WHERE key IN ('shop.receipt_v2','hashchain.audit','image_bank.central')`)).n), 0,
    '⛔ مقدارِ نادرست نوشته شد');

  //  درست‌ها
  const u = await h.newUser('دارندهٔ پرچم', 'shop');
  const shop = await h.post('/api/shop', { name: 'دکانِ پرچم' }, { token: u.accessToken });
  for (const body of [
    { scope: 'all', key: 'shop.receipt_v2', value: true },
    { scope: shop.body.shop.id, key: 'shop.receipt_v2', value: false },
    { scope: 'security', key: 'hashchain.audit', value: false },
    { scope: 'release', key: 'beta.shops', value: [shop.body.shop.id] },
    { scope: 'all', key: 'pump.custom_note', value: { any: 'json' } },
  ]) {
    const r = await put(body);
    assert.equal(r.status, 200, `${JSON.stringify(body)} ⇒ ${JSON.stringify(r.body)}`);
  }
});

/* ── ۴) تنظیماتِ مرکزی با «پیشین ⇒ تازه» ─────────────────────────── */

test('PATCH /config ⇒ سابقه «پیشین ⇒ تازه» و دلیل را دارد', async () => {
  const sup = await login('malek');
  const before = await h.get('/api/admin/plans', { token: sup });
  const from = String(before.body.config?.trial_days ?? '');
  const to = from === '21' ? '22' : '21';
  const r = await h.api('PATCH', '/api/admin/config', { token: sup, body: { trial_days: to, reason: 'کمپینِ نوروز' } });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  const row = (await many(`SELECT * FROM audit_logs WHERE action='admin.config_updated' ORDER BY created_at DESC, id DESC LIMIT 1`))[0];
  const d = detailOf(row);
  assert.equal(d.reason, 'کمپینِ نوروز');
  assert.equal(d.changes.trial_days.to, to);
  assert.equal(String(d.changes.trial_days.from ?? ''), from);
  //  بی تغییر ⇒ «changes» ِ خالی، نه «پیشین ⇒ همان»
  await h.api('PATCH', '/api/admin/config', { token: sup, body: { trial_days: to } });
  const again = (await many(`SELECT * FROM audit_logs WHERE action='admin.config_updated' ORDER BY created_at DESC, id DESC LIMIT 1`))[0];
  assert.deepEqual(detailOf(again).changes, {});
});

/* ── ۵) جست‌وجوی سراسری ───────────────────────────────────────────── */

test('جست‌وجو: نوع‌ها به نقش، `%` بی‌اثر، سفارش بی دادهٔ خریدار، کوتاه ⇒ ۴۰۰', async () => {
  const owner = await h.newUser('صاحبِ zqsearch', 'shop');
  const made = await h.post('/api/shop', { name: 'دکانِ zqsearch' }, { token: owner.accessToken });
  const shopId = made.body.shop.id;
  await query(
    `INSERT INTO sub_payments (id, app, tenant_kind, tenant_id, amount, currency, method, receipt_no, paid_at, created_at)
     VALUES ($1,'shop','shop',$2,500,'AFN','cash','RCPT-zq-77',$3,$3)`, [newId('pay'), shopId, now()]);
  await query(
    `INSERT INTO client_errors (id, app, message, at) VALUES ($1,'shop','zqsearch crashed on save',$2)`, [newId('err'), now()]);

  const sup = await login('malek');
  const all = await h.get('/api/admin/search?q=zqsearch', { token: sup });
  assert.equal(all.status, 200, JSON.stringify(all.body));
  assert.deepEqual(all.body.kinds, ['shops', 'users', 'payments', 'orders', 'errors', 'admins']);
  assert.ok(all.body.results.shops.some((s) => s.id === shopId));
  assert.ok(all.body.results.users.some((u) => u.id === owner.user.id));
  assert.ok(all.body.results.errors.length >= 1);
  const pay = await h.get('/api/admin/search?q=rcpt-zq', { token: sup });
  assert.equal(pay.body.results.payments.length, 1);

  //  مالی: فقط دکان و پرداخت
  const fin = await h.get('/api/admin/search?q=zqsearch', { token: await login('mali') });
  assert.equal(fin.status, 200, JSON.stringify(fin.body));
  assert.deepEqual(fin.body.kinds, ['shops', 'payments']);
  assert.equal(fin.body.results.users, undefined);
  //  بیننده و مدیرِ عادی: نه مدیرها
  for (const who of ['binande', 'modir']) {
    const r = await h.get('/api/admin/search?q=modir', { token: await login(who) });
    assert.equal(r.status, 200);
    assert.ok(!r.body.kinds.includes('admins'), who);
  }
  const admins = await h.get('/api/admin/search?q=modir', { token: sup });
  assert.ok(admins.body.results.admins.some((a) => a.username === 'modir'));
  assert.ok(!JSON.stringify(admins.body).includes('password'), '⛔ رمز/هش');

  //  `%` و `_` حرفِ معمولی‌اند
  const pct = await h.get(`/api/admin/search?q=${encodeURIComponent('%%')}`, { token: sup });
  assert.equal(pct.status, 200);
  for (const k of pct.body.kinds) assert.equal(pct.body.results[k].length, 0, `${k} با «%%»`);
  //  کوتاه
  const short = await h.get('/api/admin/search?q=a', { token: sup });
  assert.equal(short.status, 400);
  assert.equal(short.body.error.code, 'query_too_short');
  //  سقف
  const lim = await h.get('/api/admin/search?q=zqsearch&limit=99', { token: sup });
  assert.equal(lim.status, 400);
  //  بی‌توکن ⇒ ۴۰۱
  assert.equal((await h.get('/api/admin/search?q=zqsearch')).status, 401);
});
