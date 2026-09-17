'use strict';
/**
 * پوش — «برنامه بسته هم باشد، خبر برسد».
 *
 * خواستهٔ صریحِ صاحب مخزن: «برنامه‌ها جوری باشند که بسته هم باشند، هر
 * اتفاقی که در برنامه بیفتد — کم‌بودی یا هر چه — به سرور برود، و سرور
 * وقتی برنامه‌ها بسته‌اند پیام‌ها را برایشان بفرستد، اگر کاربر نت داشت.»
 *
 * ── چرا تا امروز هیچ سنجه‌ای نداشت ─────────────────────────────────
 * `lib/push.js` کامل نوشته شده بود ولی هیچ‌کس نمی‌توانست ثابت کند کار
 * می‌کند: FCM از ماشینِ آزمون در دسترس نیست، پس هر سنجه‌ای یا باید
 * شبکه می‌زد یا سبزِ دروغ می‌داد. حالا `push.setDeliver` درِ فرستادن را
 * می‌گیرد و خودِ **تصمیم** سنجیده می‌شود: چه کسی، چه چیزی، چند بار.
 *
 * ⛔ آن در فقط در آزمون مقدار می‌گیرد و همین‌جا هم برداشته می‌شود.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, newId, now } = require('../src/db');
const push = require('../src/lib/push');

/** هر چه فرستاده شد، این‌جا می‌نشیند. */
let outbox = [];

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  push.setDeliver((row, message) => { outbox.push({ row, message }); });
});
test.after(async () => {
  push.setDeliver(null);
  await h.stop();
});

const adminToken = async () =>
  (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;

function clear() { outbox = []; }
const tokensSent = () => outbox.map(o => o.row.token).sort();

async function shopOwner(name) {
  const u = await h.newUser(name);
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { ...u, shopId: made.body.shop.id };
}

async function pumpDevice(uid) {
  const t = await adminToken();
  const made = await h.post('/api/admin/pump/vip-codes', { plan: 'custom', days: 90 }, { token: t });
  const on = await h.post('/api/pump/device/activate', {
    code: made.body.code,
    device: { uid, name: 'کامپیوترِ پمپ', platform: 'windows' },
    station: { code: uid, name: `پمپِ ${uid}` },
  });
  assert.equal(on.status, 201, JSON.stringify(on.body));
  return { token: on.body.deviceToken, stationId: on.body.station.id };
}

// ══════════════════════════════════════════════════════════════════
//  ۱) ثبتِ توکن — از درِ درست
// ══════════════════════════════════════════════════════════════════

test('برنامهٔ دکان توکنش را از `/api/me/push` ثبت می‌کند', async () => {
  const o = await shopOwner('پوشی');
  const r = await h.post('/api/me/push',
    { token: 'tok-shop-me-1', platform: 'android' }, { token: o.accessToken });
  assert.equal(r.status, 201, JSON.stringify(r.body));

  const rows = await push.targetsFor({ shopId: o.shopId, app: 'shop' });
  assert.equal(rows.length, 1);
  assert.equal(rows[0].token, 'tok-shop-me-1');
  assert.equal(rows[0].user_id, o.user.id);
  assert.equal(rows[0].shop_id, o.shopId, 'دکان هم باید ثبت شود، نه فقط کاربر');
});

test('درِ قدیمیِ `/api/support/push` هنوز کار می‌کند', async () => {
  const o = await shopOwner('قدیمی');
  const r = await h.post('/api/support/push',
    { token: 'tok-shop-old-1', deviceUid: 'dev-old-1' }, { token: o.accessToken });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const rows = await push.targetsFor({ userId: o.user.id, app: 'shop' });
  assert.ok(rows.some(x => x.token === 'tok-shop-old-1'));
});

test('ثبتِ دوباره‌ی همان توکن ردیفِ دوم نمی‌سازد', async () => {
  const o = await shopOwner('تکراری');
  for (let i = 0; i < 3; i++) {
    await h.post('/api/me/push', { token: 'tok-same-1' }, { token: o.accessToken });
  }
  const rows = await push.targetsFor({ shopId: o.shopId, app: 'shop' });
  assert.equal(rows.length, 1, 'یک توکن، یک ردیف');
});

test('توکنی که برداشته شود دیگر گیرنده نیست', async () => {
  const o = await shopOwner('رفتنی');
  await h.post('/api/me/push', { token: 'tok-gone-1' }, { token: o.accessToken });
  assert.equal((await push.targetsFor({ shopId: o.shopId })).length, 1);

  const off = await h.del('/api/me/push?token=tok-gone-1', { token: o.accessToken });
  assert.equal(off.status, 200);
  assert.equal((await push.targetsFor({ shopId: o.shopId })).length, 0);
});

test('توکنِ بی‌حساب پذیرفته نمی‌شود', async () => {
  const r = await h.post('/api/me/push', { token: 'tok-nobody' });
  assert.equal(r.status, 401);
});

test('کامپیوترِ پمپ توکنش را به نامِ خودِ پمپ ثبت می‌کند', async () => {
  const d = await pumpDevice('pc-push-1');
  const r = await h.post('/api/pump/device/support/push',
    { token: 'tok-pump-dev-1', platform: 'windows' }, { token: d.token });
  assert.equal(r.status, 201);

  const rows = await push.targetsFor({ stationId: d.stationId, app: 'pump' });
  assert.equal(rows.length, 1, 'بی `station_id` این ردیف هیچ‌وقت پیدا نمی‌شد');
  assert.equal(rows[0].station_id, d.stationId);
});

// ══════════════════════════════════════════════════════════════════
//  ۲) هر کسی فقط پیامِ خودش
// ══════════════════════════════════════════════════════════════════

test('پیام به گوشیِ دکانِ دیگری نمی‌رود', async () => {
  const a = await shopOwner('الفِ پوش');
  const b = await shopOwner('بِ پوش');
  await h.post('/api/me/push', { token: 'tok-a' }, { token: a.accessToken });
  await h.post('/api/me/push', { token: 'tok-b' }, { token: b.accessToken });

  clear();
  await push.sendTo({ shopId: a.shopId, app: 'shop' }, { title: 'x', body: 'y' });
  assert.deepEqual(tokensSent(), ['tok-a']);
});

test('توکنِ پمپ با پرس‌وجوی دکان پیدا نمی‌شود', async () => {
  const d = await pumpDevice('pc-push-2');
  await h.post('/api/pump/device/support/push', { token: 'tok-pump-only' }, { token: d.token });

  const asShop = await push.targetsFor({ stationId: d.stationId, app: 'shop' });
  assert.equal(asShop.length, 0, '`app` واقعاً شرط است');

  const asPump = await push.targetsFor({ stationId: d.stationId, app: 'pump' });
  assert.equal(asPump.length, 1);
});

test('بی گیرنده، چیزی فرستاده نمی‌شود و خطا هم نمی‌دهد', async () => {
  clear();
  const out = await push.sendTo({ shopId: 'shop_nothing', app: 'shop' }, { title: 'x', body: 'y' });
  assert.equal(out.sent, 0);
  assert.equal(out.skipped, 'no_devices');
  assert.equal(outbox.length, 0);
});

test('هدفِ خالی هیچ‌کس را نمی‌گیرد — نه «همه»', async () => {
  const o = await shopOwner('محتاط');
  await h.post('/api/me/push', { token: 'tok-careful' }, { token: o.accessToken });
  clear();
  //  ⚠️ بدترین باگِ ممکن در پوش این است که «هدفِ خالی» یعنی همه.
  const rows = await push.targetsFor({ app: 'shop' });
  assert.deepEqual(rows, []);
  assert.equal(outbox.length, 0);
});

// ══════════════════════════════════════════════════════════════════
//  ۳) خبرهای دکان — برنامهٔ بسته
// ══════════════════════════════════════════════════════════════════

test('«کالا تمام شد» گوشیِ بستهٔ صاحبِ دکان را بیدار می‌کند', async () => {
  const owner = await shopOwner('صاحبِ خبردار');
  await h.post('/api/me/push', { token: 'tok-owner-ev' }, { token: owner.accessToken });

  clear();
  const r = await h.post('/api/events',
    { kind: 'stock_out', title: 'برنج تمام شد' }, { token: owner.accessToken });
  assert.equal(r.status, 201, JSON.stringify(r.body));

  //  خودش این کار را کرده، پس خودش زنگ نمی‌شنود
  assert.equal(outbox.length, 0, 'کسی که خودش ثبت کرده نباید خبر بگیرد');
});

test('خبرِ شاگرد به گوشیِ صاحبِ دکان می‌رسد، نه به خودِ شاگرد', async () => {
  const owner = await shopOwner('کارفرما');
  const staff = await h.newUser('شاگردِ پوش');

  const code = await h.post('/api/shop/staff-code', { role: 'staff', maxUses: 1 }, { token: owner.accessToken });
  const joined = await h.post('/api/shop/staff/join', { code: code.body.code }, { token: staff.accessToken });
  assert.equal(joined.status, 201, JSON.stringify(joined.body));

  await h.post('/api/me/push', { token: 'tok-boss' }, { token: owner.accessToken });
  await h.post('/api/me/push', { token: 'tok-staff' }, { token: staff.accessToken });

  clear();
  const r = await h.post('/api/events',
    { kind: 'stock_out', title: 'روغن تمام شد' }, { token: staff.accessToken });
  assert.equal(r.status, 201, JSON.stringify(r.body));

  assert.deepEqual(tokensSent(), ['tok-boss'], 'فقط کارفرما');
  assert.match(outbox[0].message.title, /کالا تمام شد/);
  assert.match(outbox[0].message.body, /روغن تمام شد/);
});

test('فروش و یادداشت زنگ نمی‌زنند — وگرنه کاربر اعلان‌ها را خاموش می‌کند', async () => {
  const owner = await shopOwner('کارفرمای دوم');
  const staff = await h.newUser('شاگردِ فروشنده');
  const code = await h.post('/api/shop/staff-code', { role: 'staff', maxUses: 1 }, { token: owner.accessToken });
  await h.post('/api/shop/staff/join', { code: code.body.code }, { token: staff.accessToken });
  await h.post('/api/me/push', { token: 'tok-boss-2' }, { token: owner.accessToken });

  clear();
  for (const kind of ['sale', 'expense', 'note']) {
    await h.post('/api/events', { kind, title: `${kind} ۱` }, { token: staff.accessToken });
  }
  assert.equal(outbox.length, 0, 'هیچ‌کدام ارزشِ بیدار کردنِ گوشی را ندارند');

  //  ولی در فهرست هستند
  const feed = await h.get('/api/events', { token: owner.accessToken });
  assert.equal(feed.body.events.length, 3);
});

test('صفِ بیست خبرِ آفلاین یک زنگ می‌زند، نه بیست‌تا', async () => {
  const owner = await shopOwner('کارفرمای سوم');
  const staff = await h.newUser('شاگردِ آفلاین');
  const code = await h.post('/api/shop/staff-code', { role: 'staff', maxUses: 1 }, { token: owner.accessToken });
  await h.post('/api/shop/staff/join', { code: code.body.code }, { token: staff.accessToken });
  await h.post('/api/me/push', { token: 'tok-boss-3' }, { token: owner.accessToken });

  clear();
  const events = [];
  for (let i = 0; i < 20; i++) events.push({ kind: 'low_stock', title: `کالای ${i}` });
  const r = await h.post('/api/events', { events }, { token: staff.accessToken });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.equal(r.body.saved, 20);

  assert.equal(outbox.length, 1, 'یک پیام برای یک دسته');
  assert.match(outbox[0].message.body, /و ۱۹ خبرِ دیگر|و 19 خبرِ دیگر/);
  assert.equal(outbox[0].message.data.count, '20');
});

test('خبرِ دکان به گوشیِ دکانِ دیگر نمی‌رود', async () => {
  const a = await shopOwner('مرزِ الف');
  const b = await shopOwner('مرزِ ب');
  await h.post('/api/me/push', { token: 'tok-border-a' }, { token: a.accessToken });
  await h.post('/api/me/push', { token: 'tok-border-b' }, { token: b.accessToken });

  const staff = await h.newUser('شاگردِ الف');
  const code = await h.post('/api/shop/staff-code', { role: 'staff', maxUses: 1 }, { token: a.accessToken });
  await h.post('/api/shop/staff/join', { code: code.body.code }, { token: staff.accessToken });

  clear();
  await h.post('/api/events', { kind: 'debt', title: 'قرضِ کریم' }, { token: staff.accessToken });
  assert.deepEqual(tokensSent(), ['tok-border-a']);
});

// ══════════════════════════════════════════════════════════════════
//  ۴) پشتیبانی — جوابِ مدیر به گوشیِ بسته
// ══════════════════════════════════════════════════════════════════

test('جوابِ مدیر به گوشیِ بستهٔ دکان‌دار می‌رسد', async () => {
  const t = await adminToken();
  const o = await shopOwner('پرسشگر');
  await h.post('/api/me/push', { token: 'tok-ask' }, { token: o.accessToken });
  await h.post('/api/support/messages', { body: 'سلام' }, { token: o.accessToken });

  const threads = await h.get('/api/admin/support/threads', { token: t });
  const mine = threads.body.threads.find(x => x.userId === o.user.id);
  assert.ok(mine);

  clear();
  await h.post(`/api/admin/support/threads/${mine.id}/messages`, { body: 'بفرمایید' }, { token: t });
  assert.deepEqual(tokensSent(), ['tok-ask']);
  assert.equal(outbox[0].message.data.type, 'support');
});

test('جوابِ مدیر به کامپیوترِ پمپِ بی‌حساب هم می‌رسد', async () => {
  const t = await adminToken();
  const d = await pumpDevice('pc-push-3');
  await h.post('/api/pump/device/support/push', { token: 'tok-pump-reply' }, { token: d.token });
  await h.post('/api/pump/device/support/messages', { body: 'مشکل دارم' }, { token: d.token });

  const threads = await h.get('/api/admin/support/threads?app=pump', { token: t });
  const mine = threads.body.threads.find(x => x.stationId === d.stationId);
  assert.ok(mine, 'رشتهٔ پمپ باید باشد');

  clear();
  await h.post(`/api/admin/support/threads/${mine.id}/messages`, { body: 'حل شد؟' }, { token: t });
  assert.deepEqual(tokensSent(), ['tok-pump-reply'],
    'پمپی که حساب ندارد هم باید جواب را روی همان کامپیوتر ببیند');
});

test('پیامِ کاربر گوشیِ مدیر را بیدار می‌کند', async () => {
  const t = await adminToken();
  const admin = await require('../src/db').one("SELECT * FROM admins WHERE username='admin'");
  await h.post('/api/admin/push/register', { token: 'tok-admin-1' }, { token: t });

  const o = await shopOwner('صدازننده');
  clear();
  await h.post('/api/support/messages', { body: 'کمک!' }, { token: o.accessToken });
  assert.ok(tokensSent().includes('tok-admin-1'), 'مدیر باید خبر شود');
  assert.equal(outbox[0].row.admin_id, admin.id);
});

test('پیامِ همگانی هم گوشیِ بسته را بیدار می‌کند', async () => {
  const t = await adminToken();
  const d = await pumpDevice('pc-push-4');
  await h.post('/api/pump/device/support/push', { token: 'tok-bc-pump' }, { token: d.token });

  clear();
  const out = await h.post('/api/admin/support/broadcast',
    { body: 'سرور فردا خاموش است', target: 'all', app: 'pump' }, { token: t });
  assert.equal(out.status, 200, JSON.stringify(out.body));
  assert.ok(tokensSent().includes('tok-bc-pump'));
});

// ══════════════════════════════════════════════════════════════════
//  ۵) بی تنظیماتِ پوش، هیچ‌چیز نمی‌شکند
// ══════════════════════════════════════════════════════════════════

test('بی تنظیماتِ FCM، پیام گم نمی‌شود — فقط زنگ نمی‌زند', async () => {
  push.setDeliver(null);
  push.invalidate();
  try {
    const o = await shopOwner('بی‌پوش');
    await h.post('/api/me/push', { token: 'tok-nofcm' }, { token: o.accessToken });

    const sent = await h.post('/api/support/messages', { body: 'سلام بی پوش' }, { token: o.accessToken });
    assert.equal(sent.status, 201, 'پیام باید ثبت شود حتی وقتی پوش تنظیم نیست');

    const out = await push.sendTo({ shopId: o.shopId, app: 'shop' }, { title: 'x', body: 'y' });
    assert.equal(out.sent, 0);
    assert.equal(out.skipped, 'push_off');

    const thread = await h.get('/api/support/thread', { token: o.accessToken });
    assert.ok(thread.body.messages.some(m => m.body === 'سلام بی پوش'), 'پیام سرِ جایش است');
  } finally {
    push.setDeliver((row, message) => { outbox.push({ row, message }); });
  }
});
