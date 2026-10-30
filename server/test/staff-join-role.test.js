'use strict';
/**
 * نقشِ عضوِ فعال با کدِ پیوستن عوض نمی‌شود — درزِ بالا بردنِ نقشِ خود (بازبینیِ #336، یافتهٔ ۰).
 *
 * کدِ ثابتِ شاگرد بی‌شمار و بی‌پایان است و هر عضوی آن را دارد. پیش‌تر `redeem` برای
 * عضوِ فعالِ همین دکان هم `role = code.role` می‌نوشت. پس عضوی که صاحب دکان نقشش
 * را عوض کرده بود (مدیر به شاگرد، یا در #336 شاگرد به حسابدار/صندوق‌دار) با زدنِ
 * دوبارهٔ همان کد نقشش را خودش برمی‌گرداند. همین دربارهٔ پیک و کدِ ثابت هم بود.
 * کدِ دعوتِ یک‌بارِ نقشِ دیگر هم عضوِ فعال را جابه‌جا می‌کرد و یک استفاده‌اش هم
 * می‌سوخت.
 *
 * حالا تغییرِ نقش فقط از `PATCH`/`PUT /api/shop/members/:id` است، با تأییدِ دوبارهٔ
 * هویت. زدنِ دوبارهٔ کد برای عضوِ فعال هیچ کاری نمی‌کند: نقش همان است، کد مصرف
 * نمی‌شود و استفاده‌ای ثبت نمی‌شود. عضوِ کنار گذاشته که کدی بعد از کنار گذاشتنش
 * ساخته شده دارد، مثلِ پیش برمی‌گردد.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { one } = require('../src/db');

test.before(async () => { await h.start(); });
test.after(async () => { await h.stop(); });

async function shopWithStaff(label) {
  const owner = await h.newUser(`صاحب ${label}`);
  await h.post('/api/shop', { name: `دکان ${label}` }, { token: owner.accessToken });
  const standing = (await h.get('/api/shop/staff-code', { token: owner.accessToken })).body.code;
  const staff = await h.newUser(`کارمند ${label}`);
  const j = await h.post('/api/shop/join', { code: standing }, { token: staff.accessToken });
  assert.ok([200, 201].includes(j.status), JSON.stringify(j.body));
  const fresh = await h.signIn(owner, 'shop');
  const mem = (await h.get('/api/shop/members', { token: fresh.accessToken })).body.members
    .find((m) => m.name === `کارمند ${label}`);
  return { owner: fresh, staff, standing, mem };
}

const roleOf = (memberId) => one('SELECT role, status FROM shop_members WHERE id=$1', [memberId]);

test('کدِ ثابتِ شاگرد نقشِ عضوِ فعال را عوض نمی‌کند (مدیری که صاحب دکان کرده، مدیر می‌ماند)', async () => {
  const t = await shopWithStaff('ثابت');
  const ch = await h.patch(`/api/shop/members/${t.mem.id}`, { role: 'manager' }, { token: t.owner.accessToken });
  assert.equal(ch.status, 200, JSON.stringify(ch.body));
  assert.equal((await roleOf(t.mem.id)).role, 'manager');
  const uses = (await one('SELECT used_count FROM staff_codes WHERE shop_id=(SELECT shop_id FROM shop_members WHERE id=$1) AND max_uses=0 AND status=\'active\'', [t.mem.id])).used_count;

  const again = await h.post('/api/shop/join', { code: t.standing }, { token: t.staff.accessToken });
  assert.ok([200, 201].includes(again.status), JSON.stringify(again.body));
  assert.equal(again.body.role, 'manager', 'پاسخ همان نقشِ فعلی');
  assert.equal((await roleOf(t.mem.id)).role, 'manager', 'نقش با کد عوض نشد');
  const after = (await one('SELECT used_count FROM staff_codes WHERE shop_id=(SELECT shop_id FROM shop_members WHERE id=$1) AND max_uses=0 AND status=\'active\'', [t.mem.id])).used_count;
  assert.equal(after, uses, 'استفادهٔ تازه‌ای شمرده نشد');
});

test('کدِ دعوتِ یک‌بارِ نقشِ دیگر عضوِ فعال را جابه‌جا نمی‌کند و نمی‌سوزد', async () => {
  const t = await shopWithStaff('دعوت');
  const inv = await h.post('/api/shop/invite', { role: 'manager', maxUses: 1 }, { token: t.owner.accessToken });
  assert.equal(inv.status, 201, JSON.stringify(inv.body));
  //  شاگردِ فعال کدِ مدیرِ تازه را می‌زند
  const esc = await h.post('/api/shop/join', { code: inv.body.code }, { token: t.staff.accessToken });
  assert.ok([200, 201].includes(esc.status), JSON.stringify(esc.body));
  assert.equal((await roleOf(t.mem.id)).role, 'staff', 'شاگرد مدیر نشد');
  const code = await one('SELECT used_count, status FROM staff_codes WHERE id=$1', [inv.body.id]);
  assert.equal(code.used_count, 0);
  assert.equal(code.status, 'active', 'کد برای کسی که برایش ساخته شده می‌ماند');
  //  کسی که کد برایش بود هنوز مدیر می‌شود
  const m = await h.newUser('مدیرِ تازه');
  const ok = await h.post('/api/shop/join', { code: inv.body.code }, { token: m.accessToken });
  assert.ok([200, 201].includes(ok.status), JSON.stringify(ok.body));
  assert.equal(ok.body.role, 'manager');
});

test('مرجع: عضوِ کنار گذاشته با کدی که بعد از کنار گذاشتنش ساخته شده، با نقشِ همان کد برمی‌گردد', async () => {
  const t = await shopWithStaff('بازگشت');
  assert.equal((await h.del(`/api/shop/members/${t.mem.id}`, { token: t.owner.accessToken })).status, 200);
  //  کنار گذاشتن نشست‌هایش را می‌بندد ⇒ دوباره وارد می‌شود
  const s = await h.signIn(t.staff, 'shop');
  //  همان کدِ ثابت (پیش از کنار گذاشتن ساخته شده) برنمی‌گرداند
  const blocked = await h.post('/api/shop/join', { code: t.standing }, { token: s.accessToken });
  assert.equal(blocked.status, 403);
  const inv = await h.post('/api/shop/invite', { role: 'manager', maxUses: 1 }, { token: t.owner.accessToken });
  assert.equal(inv.status, 201, JSON.stringify(inv.body));
  const back = await h.post('/api/shop/join', { code: inv.body.code }, { token: s.accessToken });
  assert.ok([200, 201].includes(back.status), JSON.stringify(back.body));
  const r = await roleOf(t.mem.id);
  assert.equal(r.status, 'active');
  assert.equal(r.role, 'manager');
});
