'use strict';
/**
 * S47 (§۷.۵، SYN-01، D-05) — «هشدار از چند روز پیش از انقضا با تاریخِ دقیق».
 *
 * تا امروز هر دو یادآورِ سرور فقط «N روز دیگر» می‌گفتند: پیامِ گفت‌وگوی پشتیبانی (`subscriptions.notifyExpiring`) و
 * اعلانِ خودکارِ مرکزِ اعلان (`notices.checkSystem`، قالبِ `expiring`). آن‌چه این پرونده قفل می‌کند:
 *   ۱) `endDayOf`: روزِ خورشیدیِ کابل («۱۹ عقرب ۱۴۰۵»)، مرزِ نیمه‌شبِ کابل (نه UTC) و ورودیِ نادرست ⇒ تهی.
 *   ۲) پیامِ پشتیبانی همان «N روز دیگر تمام می‌شود» را دارد و روزِ پایانِ همان لحظه‌ای که N تا آن شمرده شد.
 *   ۳) اعلانِ خودکار با متغیرِ `{تاریخ-پایان}` (نامِ انگلیسیِ `endDate`)؛ متغیر در فهرستِ پنل هست.
 *   ۴) مهاجرتِ ۷۷۱ فقط متنِ پیش‌فرضِ دست‌نخورده را تازه می‌کند؛ متنِ مدیر می‌ماند؛ برگشت متنِ پیشین را برمی‌گرداند.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');

const DAY = 24 * 60 * 60 * 1000;
let admin = '';

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  admin = (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
  assert.ok(admin);
});
test.after(async () => { await h.stop(); });

/** دکانی که اشتراکش ۳ روزِ دیگر (بی مهلتِ اضافه) تمام می‌شود ⇒ همان لحظهٔ پایان */
async function endingShop(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const shopId = made.body.shop.id;
  const g = await h.post('/api/admin/subscriptions', { shopId, plan: 'm1', days: 30 }, { token: admin });
  assert.ok([200, 201].includes(g.status), JSON.stringify(g.body));
  const ends = Date.now() + 3 * DAY - 60_000;
  await query("UPDATE subscriptions SET ends_at=$1, grace_days=0 WHERE shop_id=$2 AND status='active'", [ends, shopId]);
  return { ...u, shopId, ends };
}

test('S47 endDayOf: روزِ خورشیدیِ کابل با نامِ ماهِ دری؛ مرزِ نیمه‌شبِ کابل؛ ورودیِ نادرست ⇒ تهی', () => {
  const { endDayOf } = require('../src/lib/subscriptions');
  assert.equal(endDayOf(Date.parse('2026-11-10T12:00:00Z')), '۱۹ عقرب ۱۴۰۵');
  //  ۱۹:۱۵ UTC = ۲۳:۴۵ کابل (همان روز)؛ ۱۹:۴۵ UTC = ۰۰:۱۵ کابل (روزِ بعد)
  assert.equal(endDayOf(Date.parse('2026-11-10T19:15:00Z')), '۱۹ عقرب ۱۴۰۵');
  assert.equal(endDayOf(Date.parse('2026-11-10T19:45:00Z')), '۲۰ عقرب ۱۴۰۵');
  assert.equal(endDayOf(Date.parse('2027-03-20T20:00:00Z')), '۱ حمل ۱۴۰۶');
  for (const bad of [0, -5, null, undefined, 'x', 1.5]) assert.equal(endDayOf(bad), '', String(bad));
});

test('S47: یادآورِ گفت‌وگوی پشتیبانی «N روز دیگر» را با روزِ دقیقِ پایان می‌گوید', async () => {
  const subs = require('../src/lib/subscriptions');
  const s = await endingShop('یادآورِ تاریخ‌دار');
  const out = await subs.notifyExpiring({ thresholds: [7, 3, 1] });
  assert.ok(out.sent >= 1, JSON.stringify(out));
  const thread = await h.get('/api/support/thread', { token: s.accessToken });
  const msg = thread.body.messages.find((m) => m.sender === 'system' && m.body.includes('روز دیگر تمام می‌شود'));
  assert.ok(msg, JSON.stringify(thread.body.messages));
  assert.match(msg.body, /3 روز دیگر/);
  assert.ok(msg.body.includes(`روزِ ${subs.endDayOf(s.ends)}`), msg.body);
  assert.match(subs.endDayOf(s.ends), /^[۰-۹]{1,2} [^\s۰-۹]+ [۰-۹]{4}$/);
});

test('S47: اعلانِ خودکارِ «رو به پایان» با `{تاریخ-پایان}` و متغیر در فهرستِ پنل', async () => {
  const notices = require('../src/lib/notices');
  const subs = require('../src/lib/subscriptions');
  const s = await endingShop('اعلانِ تاریخ‌دار');
  await notices.checkSystem({ thresholds: [7, 3, 1] });
  const rows = await many(
    "SELECT title, body FROM notice_deliveries WHERE user_id=$1 AND channel='inapp' ORDER BY created_at DESC", [s.user.id]);
  const hit = rows.find((r) => r.body.includes('روز دیگر'));
  assert.ok(hit, JSON.stringify(rows));
  assert.ok(hit.body.includes(`روزِ ${subs.endDayOf(s.ends)}`), hit.body);
  assert.ok(!hit.body.includes('{تاریخ-پایان}'), 'متغیر پر شد');
  //  نامِ انگلیسی هم کار می‌کند؛ گیرندهٔ بی اشتراک «—»
  assert.equal(notices.render('{endDate}', { 'تاریخ-پایان': '۲ قوس ۱۴۰۵' }), '۲ قوس ۱۴۰۵');
  const panel = await h.get('/api/admin/notice-templates', { token: admin });
  assert.equal(panel.status, 200);
  assert.ok(panel.body.variables.includes('{تاریخ-پایان}'));
  const tpl = panel.body.templates.find((t) => t.key === 'expiring');
  assert.ok(tpl.body.includes('{تاریخ-پایان}'), tpl.body);
});

test('مهاجرتِ ۷۷۱: فقط متنِ پیش‌فرضِ دست‌نخوردهٔ «رو به پایان» تازه می‌شود؛ برگشت همان متنِ پیشین', async () => {
  const notices = require('../src/lib/notices');
  const fresh = notices.TEMPLATES.find((t) => t.key === 'expiring').body;
  const old = fresh.replace(' — روزِ {تاریخ-پایان} — تمام می‌شود.', ' تمام می‌شود.');
  assert.notEqual(old, fresh);
  const dir = path.join(__dirname, '..', 'migrations');
  const up = fs.readFileSync(path.join(dir, '771_notice_expiring_end_day.sql'), 'utf8');
  const down = fs.readFileSync(path.join(dir, 'rollback', '771_notice_expiring_end_day.down.sql'), 'utf8');

  //  پایگاهِ کهنه: ردیفِ «both» با متنِ پیشین؛ و نسخهٔ دکان که مدیر خودش نوشته
  await query("UPDATE notice_templates SET body=$1 WHERE key='expiring' AND app='both'", [old]);
  await notices.saveTemplate('expiring', 'shop', { title: 'خودم نوشتم', body: 'متنِ مدیر: {روز-مانده} روز' });
  await query(up.replace(/^--.*$/gm, ''));
  const both = await one("SELECT body FROM notice_templates WHERE key='expiring' AND app='both'");
  assert.equal(both.body, fresh, 'متنِ پیش‌فرضِ کهنه تازه شد');
  const mine = await one("SELECT body FROM notice_templates WHERE key='expiring' AND app='shop'");
  assert.equal(mine.body, 'متنِ مدیر: {روز-مانده} روز', '⛔ متنِ مدیر دست نخورد');

  await query(down);
  assert.equal((await one("SELECT body FROM notice_templates WHERE key='expiring' AND app='both'")).body, old);
  assert.equal((await one("SELECT body FROM notice_templates WHERE key='expiring' AND app='shop'")).body, 'متنِ مدیر: {روز-مانده} روز');
  const migrate = require('../src/migrate');
  assert.deepEqual(await migrate.run(), ['771_notice_expiring_end_day.sql']);
  assert.equal((await one("SELECT body FROM notice_templates WHERE key='expiring' AND app='both'")).body, fresh);
});
