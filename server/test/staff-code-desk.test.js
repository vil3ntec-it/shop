'use strict';
/**
 * میزِ «کدِ شاگرد» در پنلِ مدیریت — بندِ ۴.۴ سندِ ریمیکِ ریپوی `server`.
 *
 * ── خواستهٔ صاحب سامانه ────────────────────────────────────────────────
 *   «سومی بخشِ کد باشه که همهٔ حساب‌ها رو نشون بده و روی هر کدوم که زدم
 *    کدِ شاگردش رو ببینم و چند تا شاگرد بهش وصل است.»
 *
 * ⛔ و نیمی از آن خواسته **شدنی نبود و با دکمهٔ ساختگی پوشانده نشد**:
 * کدِ یک‌بارمصرف فقط HMAC دارد و هیچ‌کس — مدیرِ سامانه هم — نمی‌تواند
 * ببیندش. آن‌چه شدنی است کدِ **ثابت** است، چون `deriveStanding` از
 * `shopId` و `generation` بازساختنی است. این پرونده هر دو را قفل می‌کند:
 * آن یکی دیده شود، این یکی هیچ‌وقت.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, newId, now } = require('../src/db');

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'watcher','ناظر',$2,'admin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
});
test.after(async () => { await h.stop(); });

const tokenOf = async (username) =>
  (await h.post('/api/admin/login', { username, password: 'Admin!12345' })).body.token;

/** یک دکان با صاحب، یک شاگردِ پیوسته، و کدِ ثابتِ ساخته‌شده. */
async function aShopWithOneStudent(tag) {
  const owner = await h.newUser(`صاحبِ ${tag}`);
  const student = await h.newUser(`شاگردِ ${tag}`);
  const shop = await h.post('/api/shop', { name: `دکانِ ${tag}` }, { token: owner.accessToken });
  const made = await h.post('/api/shop/staff-code', { role: 'staff', maxUses: 1 }, { token: owner.accessToken });
  await h.post('/api/shop/staff/join', { code: made.body.code }, { token: student.accessToken });
  //  کدِ ثابت — همان که صاحبِ سامانه «کدِ شاگرد» می‌خواندش
  const standing = await h.get('/api/shop/staff-code', { token: owner.accessToken });
  return { shopId: shop.body.shop.id, owner, student, oneTime: made.body.code, standing: standing.body.code };
}

test('۱) میز شمارِ شاگردها را می‌دهد — و صاحبِ دکان شاگرد شمرده نمی‌شود', async () => {
  const s = await aShopWithOneStudent('یک');
  const token = await tokenOf('admin');
  const desk = await h.get(`/api/admin/shops/${s.shopId}/staff-codes`, { token });
  assert.equal(desk.status, 200);
  assert.equal(desk.body.shop.id, s.shopId);
  //  ⚠️ دو عضو هست (صاحب و شاگرد) ولی «شاگرد» یکی است
  assert.equal(desk.body.students.members, 2);
  assert.equal(desk.body.students.total, 1);
  assert.equal(desk.body.students.active, 1);
});

test('۲) ⛔ هیچ کدِ خامی در فهرست نمی‌آید — نه یک‌بارمصرف، نه ثابت', async () => {
  const s = await aShopWithOneStudent('دو');
  const token = await tokenOf('admin');
  const desk = await h.get(`/api/admin/shops/${s.shopId}/staff-codes`, { token });
  const text = JSON.stringify(desk.body);
  assert.ok(!text.includes(s.oneTime), 'کدِ یک‌بارمصرف هیچ‌وقت دیده نمی‌شود');
  assert.ok(!text.includes(s.standing), 'کدِ ثابت هم در **فهرست** نمی‌آید');
  //  و هیچ رشته‌ای به شکلِ کدِ شاگرد در پاسخ نباشد
  assert.ok(!/SHG-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}/.test(text),
    'هیچ خانه‌ای نباید شکلِ کدِ شاگرد داشته باشد');
  //  ⚠️ ولی چهار رقمِ آخر می‌آید تا مدیر ردیف‌ها را از هم جدا کند
  assert.ok(desk.body.codes.length >= 2);
  assert.ok(desk.body.codes.every((c) => typeof c.hint === 'string' && c.hint.length <= 4));
});

test('۳) کدِ ثابت با یک کلیکِ جدا دیده می‌شود، و همان کدِ واقعی است', async () => {
  const s = await aShopWithOneStudent('سه');
  const token = await tokenOf('admin');
  const shown = await h.post(`/api/admin/shops/${s.shopId}/staff-codes/reveal`, {}, { token });
  assert.equal(shown.status, 200);
  assert.equal(shown.body.code, s.standing, 'همان کدی که صاحبِ دکان می‌بیند');
  assert.match(shown.body.code, /^SHG-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$/);

  //  ⛔ و «همان کدِ واقعی» با رفتار سنجیده می‌شود، نه با رشته: یک شاگردِ
  //  تازه واقعاً با همین کد وارد همان دکان می‌شود.
  const newcomer = await h.newUser('شاگردِ سهِ دوم');
  const joined = await h.post('/api/shop/staff/join', { code: shown.body.code }, { token: newcomer.accessToken });
  assert.equal(joined.status, 201);
  assert.equal(joined.body.shop.id, s.shopId);
});

test('۴) ⛔ نمایش فقط مدیرِ کل، و همیشه ثبت می‌شود', async () => {
  const s = await aShopWithOneStudent('چهار');

  const plain = await tokenOf('watcher');
  const denied = await h.post(`/api/admin/shops/${s.shopId}/staff-codes/reveal`, {}, { token: plain });
  assert.equal(denied.status, 403, 'مدیرِ معمولی کدِ شاگرد را نمی‌بیند');
  //  ⚠️ ولی خودِ فهرست برایش باز است — شمارِ شاگردها راز نیست
  assert.equal((await h.get(`/api/admin/shops/${s.shopId}/staff-codes`, { token: plain })).status, 200);

  const token = await tokenOf('admin');
  await h.post(`/api/admin/shops/${s.shopId}/staff-codes/reveal`, {}, { token });
  const row = await query(
    `SELECT * FROM audit_logs WHERE action='staff_code.revealed' ORDER BY created_at DESC LIMIT 1`
  );
  assert.equal(row.rows.length, 1, 'هر نمایش یک ردیف در دفترِ رخدادها');
});

test('۵) ⛔ و این در هیچ کدی نمی‌سازد', async () => {
  //  دکانی که هیچ‌وقت کدِ ثابت نساخته — `GET` نباید برایش یکی بسازد
  const owner = await h.newUser('صاحبِ پنج');
  const shop = await h.post('/api/shop', { name: 'دکانِ پنج' }, { token: owner.accessToken });
  const id = shop.body.shop.id;
  const token = await tokenOf('admin');

  const desk = await h.get(`/api/admin/shops/${id}/staff-codes`, { token });
  assert.equal(desk.status, 200);
  assert.equal(desk.body.standing, null, 'کدِ ثابتی نیست و ساخته هم نشد');

  const before = await query(`SELECT COUNT(*)::int n FROM staff_codes WHERE shop_id=$1`, [id]);
  assert.equal(before.rows[0].n, 0, 'یک `GET`ِ مدیر نباید ردیفی بسازد');

  //  و نمایش هم چیزی نمی‌سازد؛ می‌گوید نیست
  const shown = await h.post(`/api/admin/shops/${id}/staff-codes/reveal`, {}, { token });
  assert.equal(shown.status, 404);
  assert.equal(shown.body.error.code, 'no_standing_code');
  const after = await query(`SELECT COUNT(*)::int n FROM staff_codes WHERE shop_id=$1`, [id]);
  assert.equal(after.rows[0].n, 0);
});

test('۶) ⛔ و دکانِ نبوده ۴۰۴ می‌گیرد، نه ۵۰۰ِ گنگ', async () => {
  const token = await tokenOf('admin');
  const desk = await h.get('/api/admin/shops/shp_nobody/staff-codes', { token });
  assert.equal(desk.status, 404);
});
