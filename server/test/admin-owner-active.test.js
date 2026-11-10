'use strict';
/**
 * S12 ِ ممیزیِ پنلِ مرکزی — «مالکِ اصلی» **پیشین‌ترین ارشدِ فعال** است و هرگز بیرون نمی‌ماند.
 *
 * `admin-admins.js` مالک را از بستن/تنزل/بستنِ نشست، و `admin-service-keys.js` کلیدش را از ابطالِ ارشدِ دیگر نگه می‌داشت
 * (`console-phase3.test.js`، `console-shell.test.js`). شکافِ مانده: مالک «پیشین‌ترین ارشد» بود، **فعال یا نه** (`lib/admin-owner.js`
 * و مهاجرتِ ۱۶۸). پایگاهی که پیشین‌ترین ارشدش حسابِ بسته‌ای بود (حسابِ آزمایشیِ آغاز، کارمندِ رفته) نشانِ مالک را به همان حسابِ
 * مُرده می‌داد ⇒ صاحبِ واقعی (ارشدِ فعالِ بعدی) بی‌پناه بود و ارشدِ دوم می‌بستش؛ و حسابِ نشان‌دار از هیچ API ای باز نمی‌شد.
 *
 * حالا: نشان فقط روی ارشدِ فعال می‌نشیند؛ نشانی که روی حسابِ نافعال/غیرِارشد مانده (فقط پوسته یا مهاجرتِ ۱۶۸ چنین می‌کند — هیچ
 * API ای نه) در نخستین خواندن (و هنگامِ بالا آمدنِ سرور) به پیشین‌ترین ارشدِ فعال می‌رود، با ردیفِ سابقه.
 * و هیچ دری رمز یا دوعاملیِ مدیرِ **دیگری** را عوض نمی‌کند (نگهبانِ سورس پایین).
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const nodePath = require('node:path');
const h = require('./helpers');
const { query, one, many, newId } = require('../src/db');
const { ownerId } = require('../src/lib/admin-owner');

const ids = {};
let hash = '';
const T0 = 1_700_000_000_000;

async function seed() {
  await query('DELETE FROM admins');
  const rows = [
    ['kohne', 'superadmin', 'disabled', T0],        //  پیشین‌ترین، ولی بسته
    ['malek', 'superadmin', 'active', T0 + 1000],   //  صاحبِ واقعی
    ['dovom', 'superadmin', 'active', T0 + 2000],
    ['sevom', 'superadmin', 'active', T0 + 3000],
  ];
  for (const [u, role, status, at] of rows) {
    ids[u] = newId('adm');
    await query(
      `INSERT INTO admins (id, username, name, password_hash, role, status, created_at, is_owner)
       VALUES ($1,$2,$2,$3,$4,$5,$6,false)`, [ids[u], u, hash, role, status, at]);
  }
}
const tok = async (u) => (await h.post('/api/admin/login', { username: u, password: 'Admin!12345' })).body.token;
const flagged = async () => (await many('SELECT username FROM admins WHERE is_owner')).map((r) => r.username);

test.before(async () => {
  await h.start();
  hash = await require('../src/lib/password').hashPassword('Admin!12345');
});
test.after(async () => { await h.stop(); });

test('پایگاهِ بی‌نشان: مالک پیشین‌ترین ارشدِ **فعال** است، نه حسابِ بسته؛ ارشدِ دوم نه می‌بندد نه تنزل می‌دهد', async () => {
  await seed();
  assert.equal(await ownerId(), ids.malek);
  assert.deepEqual(await flagged(), ['malek']);
  const second = await tok('dovom');
  for (const body of [{ status: 'disabled' }, { role: 'admin' }, { role: 'viewer' }, { role: 'finance', status: 'disabled' }]) {
    const r = await h.api('PATCH', `/api/admin/admins/${ids.malek}`, { token: second, body });
    assert.equal(r.status, 403, JSON.stringify(body));
    assert.equal(r.body.error.code, 'owner_protected');
  }
  const revoke = await h.post(`/api/admin/admins/${ids.malek}/sessions/revoke`, { reason: 'بیرونش کن' }, { token: second });
  assert.equal(revoke.status, 403);
  assert.equal(revoke.body.error.code, 'owner_protected');
  assert.deepEqual({ ...(await one('SELECT role, status FROM admins WHERE id=$1', [ids.malek])) }, { role: 'superadmin', status: 'active' });
  assert.equal((await h.get('/api/admin/me', { token: await tok('malek') })).body.admin.isOwner, true);
});

test('نشانِ مانده روی حسابِ بسته (مهاجرتِ ۱۶۸ روی چنین پایگاهی) ⇒ به پیشین‌ترین ارشدِ فعال می‌رود، با سابقه؛ صاحب امن است', async () => {
  await seed();
  await query('UPDATE admins SET is_owner = (id = $1)', [ids.kohne]);
  const second = await tok('dovom');
  const r = await h.api('PATCH', `/api/admin/admins/${ids.malek}`, { token: second, body: { status: 'disabled' } });
  assert.equal(r.status, 403, JSON.stringify(r.body));
  assert.equal(r.body.error.code, 'owner_protected');
  assert.deepEqual(await flagged(), ['malek']);
  const moved = await many(`SELECT * FROM audit_logs WHERE action='admin.owner_reassigned' AND target_id=$1`, [ids.malek]);
  assert.equal(moved.length, 1);
  const d = typeof moved[0].detail === 'string' ? JSON.parse(moved[0].detail) : moved[0].detail;
  assert.equal(d.from, ids.kohne);
  //  حسابِ بسته دیگر «مالک» نیست ⇒ ارشدِ دیگر می‌تواند بازش کند (پیش‌تر هیچ دری بازش نمی‌کرد)
  const reopen = await h.api('PATCH', `/api/admin/admins/${ids.kohne}`, { token: await tok('malek'), body: { status: 'active' } });
  assert.equal(reopen.status, 200, JSON.stringify(reopen.body));
  //  و بازگشتش مالکیت را دوباره جابه‌جا نمی‌کند (نشانِ درست سرِ جایش می‌ماند)
  assert.equal(await ownerId(), ids.malek);
  assert.deepEqual(await flagged(), ['malek']);
});

test('نشانِ مانده روی ارشدی که نقشش (با پوسته) عوض شد ⇒ همان جابه‌جایی؛ مالکِ فعال هرگز جابه‌جا نمی‌شود', async () => {
  await seed();
  await query(`UPDATE admins SET is_owner = (id = $1), role = CASE WHEN id = $1 THEN 'admin' ELSE role END`, [ids.malek]);
  assert.equal(await ownerId(), ids.dovom);
  //  پایدار: بارها خواندن همان
  for (let i = 0; i < 3; i++) assert.equal(await ownerId(), ids.dovom);
  assert.equal((await many(`SELECT 1 FROM audit_logs WHERE action='admin.owner_reassigned' AND target_id=$1`, [ids.dovom])).length, 1);
});

test('هیچ ارشدِ فعالی نیست ⇒ نشان همان‌جا می‌ماند (جایی برای بردن نیست؛ بازیابی فقط از پوسته)', async () => {
  await seed();
  await query(`UPDATE admins SET status='disabled'`);
  await query('UPDATE admins SET is_owner = (id = $1)', [ids.kohne]);
  assert.equal(await ownerId(), ids.kohne);
  assert.deepEqual(await flagged(), ['kohne']);
});

test('دو خوانندهٔ هم‌زمان (دو نمونهٔ خوشه پسِ هم بالا می‌آیند) ⇒ یک مالک، یک ردیفِ سابقه', async () => {
  await seed();
  await query('UPDATE admins SET is_owner = (id = $1)', [ids.kohne]);
  const got = await Promise.all([ownerId(), ownerId(), ownerId(), ownerId()]);
  assert.deepEqual([...new Set(got)], [ids.malek]);
  assert.deepEqual(await flagged(), ['malek']);
  assert.equal((await many(`SELECT 1 FROM audit_logs WHERE action='admin.owner_reassigned' AND target_id=$1`, [ids.malek])).length, 1);
});

test('همیشه دست‌کم یک ارشدِ فعال: ارشدهای دیگر کنار می‌روند، مالک می‌ماند', async () => {
  await seed();
  await ownerId();
  const sup = await tok('dovom');
  assert.equal((await h.api('PATCH', `/api/admin/admins/${ids.sevom}`, { token: sup, body: { status: 'disabled' } })).status, 200);
  const sup3 = await tok('malek');
  assert.equal((await h.api('PATCH', `/api/admin/admins/${ids.dovom}`, { token: sup3, body: { role: 'admin' } })).status, 200);
  const left = await many(`SELECT username FROM admins WHERE role='superadmin' AND status='active'`);
  assert.deepEqual(left.map((r) => r.username), ['malek']);
});

/*
 *  نگهبانِ سورس: رمز و دوعاملیِ مدیر فقط از درهای «خودِ همان مدیر» (`/2fa/*` با `req.admin.id`)، بازنویسیِ هشِ ورودِ درست، و
 *  راهِ پوسته (`admin-bootstrap`) عوض می‌شوند. درِ تازه‌ای که رمز/دوعاملیِ **مدیرِ دیگری** را بنویسد این‌جا سرخ می‌شود تا
 *  نویسنده‌اش آگاهانه نگهبانِ مالک (`owner_protected`) را بگذارد.
 */
test('هیچ درِ API ای رمز یا دوعاملیِ مدیرِ دیگری را نمی‌نویسد (فقط خودش، بازنویسیِ هش، و پوسته)', () => {
  const SRC = nodePath.join(__dirname, '..', 'src');
  const ALLOWED = new Set(['routes/admin-2fa.js', 'lib/admin-2fa.js', 'lib/admin-bootstrap.js', 'lib/auth-session-hardening.js']);
  const hits = [];
  const walk = (dir) => {
    for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
      const p = nodePath.join(dir, e.name);
      if (e.isDirectory()) walk(p);
      else if (e.name.endsWith('.js')) {
        const text = fs.readFileSync(p, 'utf8');
        for (const m of text.matchAll(/UPDATE\s+admins\s+SET\s+([^`'"]*)/gi)) {
          if (/password_hash|totp_/.test(m[1])) hits.push(nodePath.relative(SRC, p).split(nodePath.sep).join('/'));
        }
      }
    }
  };
  walk(SRC);
  const stray = [...new Set(hits)].filter((f) => !ALLOWED.has(f));
  assert.deepEqual(stray, [], `نوشتنِ رمز/دوعاملیِ مدیر بیرون از درهای خودِ مدیر: ${stray.join(', ')}`);
  //  و در `admin-2fa.js` هر نوشتن روی همان مدیرِ نشست است
  const twoFa = fs.readFileSync(nodePath.join(SRC, 'routes', 'admin-2fa.js'), 'utf8');
  assert.equal((twoFa.match(/req\.params/g) || []).length, 0, 'درهای دوعاملی شناسهٔ مدیرِ دیگر نمی‌گیرند');
});
