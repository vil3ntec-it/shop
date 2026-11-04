'use strict';
/**
 * «حذفِ حساب» به دستِ خودِ کاربر (Google Play؛ `routes/account-deletion.js`، `lib/self-delete.js`).
 *
 * ⛔ پیش‌فرض خاموش (`account.self_delete`) ⇒ هیچ نوشتنی؛ روشن ⇒ فقط با تأییدِ دوبارهٔ هویت و تایپِ نشانیِ
 *    همان حساب، و همان حذفِ کاملِ `lib/user-delete.js`. حسابِ پمپ‌دار از این در حذف نمی‌شود (۴۰۹ `has_pump`).
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one } = require('../src/db');
const { hashToken } = require('../src/lib/tokens');

test.before(async () => { await h.start(); });
test.after(async () => { await h.stop(); });

const flag = (on) => query(
  `INSERT INTO live_config (scope, key, value, updated_at) VALUES ('all','account.self_delete',$1,1)
   ON CONFLICT (scope, key) DO UPDATE SET value=EXCLUDED.value`, [on ? 'true' : 'false']);
/** نشستِ این توکن دیگر «تازه» نیست (ده دقیقه از ورود گذشت) */
const stale = (token) => query(`DELETE FROM login_attempts WHERE scope='session_auth' AND identifier=$1`, [hashToken(token)]);

async function shopOwner(name) {
  const u = await h.newUser(name);
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { ...u, shopId: made.body.shop.id };
}

test('خاموش (پیش‌فرض): پیش‌نمایش می‌گوید خاموش است و حذف هیچ نمی‌نویسد', async () => {
  await query("DELETE FROM live_config WHERE key='account.self_delete'");
  const a = await shopOwner('خاموش');
  const pv = await h.get('/api/me/account/deletion', { token: a.accessToken });
  assert.equal(pv.status, 200, JSON.stringify(pv.body));
  assert.equal(pv.body.enabled, false);
  assert.equal(pv.body.blocked, null);
  assert.equal(pv.body.confirmWith, 'email');
  assert.equal(pv.body.confirmValue, a.email);
  assert.deepEqual(pv.body.shops, [{ name: 'دکانِ خاموش', otherMembers: 0 }]);

  const no = await h.post('/api/me/account/deletion', { confirm: a.email }, { token: a.accessToken });
  assert.equal(no.status, 403, JSON.stringify(no.body));
  assert.equal(no.body.error.code, 'self_delete_disabled');
  assert.ok(await one('SELECT id FROM users WHERE id=$1', [a.user.id]), 'حساب سرِ جایش');
  assert.ok(await one('SELECT id FROM shops WHERE id=$1', [a.shopId]), 'دکان سرِ جایش');

  //  مقدارِ غیرِ true هم روشن نیست
  await flag(false);
  assert.equal((await h.get('/api/me/account/deletion', { token: a.accessToken })).body.enabled, false);
});

test('روشن: تأییدِ دوبارهٔ هویت، نشانیِ درست، و بعد حساب و دکان و نشست همه می‌روند', async () => {
  await flag(true);
  const a = await shopOwner('رفتنی');
  const b = await shopOwner('همسایه');
  assert.equal((await h.get('/api/me/account/deletion', { token: a.accessToken })).body.enabled, true);

  //  ⛔ نشستِ کهنه ⇒ reauth_required و هیچ نوشتنی
  await stale(a.accessToken);
  const old = await h.post('/api/me/account/deletion', { confirm: a.email }, { token: a.accessToken });
  assert.equal(old.status, 403, JSON.stringify(old.body));
  assert.equal(old.body.error.code, 'reauth_required');
  assert.equal((await h.post('/api/auth/reauth', { password: a.password }, { token: a.accessToken })).status, 200);

  //  ⛔ نشانیِ غلط
  const wrong = await h.post('/api/me/account/deletion', { confirm: b.email }, { token: a.accessToken });
  assert.equal(wrong.status, 400, JSON.stringify(wrong.body));
  assert.equal(wrong.body.error.code, 'confirm_mismatch');
  assert.ok(await one('SELECT id FROM users WHERE id=$1', [a.user.id]), 'با نشانیِ غلط چیزی نرفت');

  const done = await h.post('/api/me/account/deletion', { confirm: `  ${a.email.toUpperCase()} ` }, { token: a.accessToken });
  assert.equal(done.status, 200, JSON.stringify(done.body));
  assert.deepEqual(done.body, { ok: true, shops: 1 });

  assert.equal(await one('SELECT id FROM users WHERE id=$1', [a.user.id]), null, 'حساب');
  assert.equal(await one('SELECT id FROM shops WHERE id=$1', [a.shopId]), null, 'دکانِ خودش');
  assert.equal((await h.get('/api/me', { token: a.accessToken })).status, 401, 'نشستِ همان حساب دیگر باز نیست');
  //  همسایه دست نخورد
  assert.ok(await one('SELECT id FROM shops WHERE id=$1', [b.shopId]));
  assert.equal((await h.get('/api/me', { token: b.accessToken })).status, 200);
  //  سابقه بی ایمیل
  const log = await one("SELECT detail FROM audit_logs WHERE action='user.self_deleted' AND target_id=$1", [a.user.id]);
  assert.ok(log, 'سابقهٔ حذف');
  assert.ok(!JSON.stringify(log.detail).includes(a.email), 'ایمیل در سابقه نیست');
});

test('پورتالِ وب همان در است — و حسابِ پمپ‌دار از این در حذف نمی‌شود', async () => {
  await flag(true);
  const s = await shopOwner('وبی');
  const pv = await h.get('/api/portal/account/deletion', { token: s.accessToken });
  assert.equal(pv.status, 200, JSON.stringify(pv.body));
  assert.equal(pv.body.enabled, true);

  const p = await h.newUser('پمپی', 'pump');
  const made = await h.post('/api/pump', { name: 'پمپِ پمپی' }, { token: p.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const pp = await h.get('/api/portal/account/deletion', { token: p.accessToken });
  assert.equal(pp.status, 200, JSON.stringify(pp.body));
  assert.equal(pp.body.blocked, 'has_pump');
  const no = await h.post('/api/portal/account/deletion', { confirm: p.email }, { token: p.accessToken });
  assert.equal(no.status, 409, JSON.stringify(no.body));
  assert.equal(no.body.error.code, 'has_pump');
  assert.ok(await one('SELECT id FROM stations WHERE id=$1', [made.body.station.id]), 'پمپ سرِ جایش');

  const web = await h.post('/api/portal/account/deletion', { confirm: s.email }, { token: s.accessToken });
  assert.equal(web.status, 200, JSON.stringify(web.body));
  assert.equal(await one('SELECT id FROM users WHERE id=$1', [s.user.id]), null);
});

test('نشستِ بسته به دکانِ دیگری (شاگرد) ⇒ ۴۰۳ owner_session_required: نه نامِ دکانِ خودش، نه حذف', async () => {
  await flag(true);
  const a = await shopOwner('کارفرما');
  const b = await shopOwner('شاگردِ دکان‌دار');
  const t = Date.now();
  await query(`INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at) VALUES ($1,$2,$3,'staff','active',$4,$4)`,
    [`mem-sd-${t}`, a.shopId, b.user.id, t]);
  const pick = await h.post('/api/shop/active', { shopId: a.shopId }, { token: b.accessToken });
  assert.equal(pick.status, 200, JSON.stringify(pick.body));
  for (const door of ['/api/me/account/deletion', '/api/portal/account/deletion']) {
    const pv = await h.get(door, { token: b.accessToken });
    assert.equal(pv.status, 403, door);
    assert.equal(pv.body.error.code, 'owner_session_required');
    assert.ok(!JSON.stringify(pv.body).includes('شاگردِ دکان‌دار'), 'نامِ دکانِ خودش از نشستِ دکانِ دیگر دیده نمی‌شود');
    const run = await h.post(door, { confirm: b.email }, { token: b.accessToken });
    assert.equal(run.status, 403, door);
    assert.equal(run.body.error.code, 'owner_session_required');
  }
  assert.ok(await one('SELECT id FROM users WHERE id=$1', [b.user.id]), 'حساب سرِ جایش است');
  //  برگشت به دکانِ خودش ⇒ همان پیش‌نمایشِ عادی
  assert.equal((await h.post('/api/shop/active', { shopId: b.shopId }, { token: b.accessToken })).status, 200);
  assert.equal((await h.get('/api/me/account/deletion', { token: b.accessToken })).status, 200);
  await flag(false);
});

test('بی نشست ⇒ ۴۰۱', async () => {
  assert.equal((await h.get('/api/me/account/deletion')).status, 401);
  assert.equal((await h.post('/api/portal/account/deletion', { confirm: 'x' })).status, 401);
});

test('privacy.html بخشِ #delete-account دارد: راهِ برنامه، راهِ وب (پورتال) و آن‌چه می‌ماند — همان نشانیِ Play Console', () => {
  const html = require('node:fs').readFileSync(require('node:path').join(__dirname, '..', '..', 'privacy.html'), 'utf8');
  const at = html.indexOf('id="delete-account"');
  assert.ok(at > 0, 'بخشِ #delete-account نیست');
  const section = html.slice(at, html.indexOf('</section>', at));
  assert.ok(section.includes('حساب من ← ویرایش حساب ← حذفِ حساب'), 'راهِ درونِ برنامه');
  assert.ok(section.includes('/portal/#delete-account'), 'راهِ وب بی برنامه');
  assert.ok(section.includes('چه می‌ماند'), 'آن‌چه پس از حذف می‌ماند');
});
