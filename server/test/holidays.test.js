'use strict';
/**
 * LOC-04 — جدولِ تعطیلات از سرور.
 *
 *  • `GET /api/calendar/holidays` بی‌توکن، با ETag ِ نسخه و بازهٔ اختیاری؛
 *  • نوشتن فقط مدیرِ ارشد، با سنجشِ کامل (تاریخِ واقعی، نامِ دری، بی تکرار)
 *    و قفلِ نسخه: دو نوشتن با یک نسخه ⇒ دومی ۴۰۹ و جدولِ اولی سرِ جایش؛
 *  • جدولِ سرور همان شکلی است که `calendar-core` (همتای `BusinessDay.kt`) می‌گیرد.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, newId, now } = require('../src/db');
const cal = require('../src/lib/calendar-core');

let superToken = '';
let adminToken = '';

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  for (const [username, role] of [['hol-super', 'superadmin'], ['hol-clerk', 'admin']]) {
    await query(
      `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
       VALUES ($1,$2,'مدیر',$3,$4,'active',$5)`,
      [newId('adm'), username, await pw.hashPassword('Admin!12345'), role, now()],
    );
  }
  superToken = (await h.post('/api/admin/login', { username: 'hol-super', password: 'Admin!12345' })).body.token;
  adminToken = (await h.post('/api/admin/login', { username: 'hol-clerk', password: 'Admin!12345' })).body.token;
  assert.ok(superToken && adminToken);
});
test.after(async () => { await h.stop(); });

const TABLE = [
  { date: '2027-03-21', name: { fa: 'نوروز', en: 'Nowruz', ps: 'نوروز' } },
  { date: '2026-08-19', name: { fa: 'روزِ استقلال', en: 'Independence Day' } },
  { date: '2027-03-10', name: { fa: 'عید فطر' } },
];

test('پیش از هر نوشتن: جدولِ خالی با نسخهٔ ۰، بی‌توکن', async () => {
  const r = await h.get('/api/calendar/holidays');
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(r.body, { version: 0, updatedAt: 0, holidays: [] });
});

test('فقط مدیرِ ارشد می‌نویسد؛ بی‌توکن، توکنِ دکان و مدیرِ عادی هیچ چیزی را عوض نمی‌کنند', async () => {
  const u = await h.newUser('صاحبِ تعطیلات');
  const body = { version: 0, holidays: TABLE };
  assert.equal((await h.put('/api/admin/calendar/holidays', body)).status, 401);
  assert.equal((await h.put('/api/admin/calendar/holidays', body, { token: u.accessToken })).status, 401);
  assert.equal((await h.put('/api/admin/calendar/holidays', body, { token: adminToken })).status, 403);
  assert.equal((await h.get('/api/admin/calendar/holidays', { token: adminToken })).status, 200, 'خواندن برای مدیرِ عادی باز است');
  assert.equal((await h.get('/api/calendar/holidays')).body.version, 0);
});

test('مدیرِ ارشد جدول را می‌گذارد: مرتب، پاک، با نسخهٔ تازه؛ همه می‌خوانند و بازه کار می‌کند', async () => {
  const w = await h.put('/api/admin/calendar/holidays', { version: 0, holidays: TABLE }, { token: superToken });
  assert.equal(w.status, 200, JSON.stringify(w.body));
  assert.equal(w.body.version, 1);
  assert.deepEqual(w.body.holidays.map((x) => x.date), ['2026-08-19', '2027-03-10', '2027-03-21']);
  assert.deepEqual(w.body.holidays[0].name, { fa: 'روزِ استقلال', en: 'Independence Day' });

  const r = await h.get('/api/calendar/holidays');
  assert.equal(r.status, 200);
  assert.deepEqual(r.body.holidays, w.body.holidays);
  assert.equal(r.body.version, 1);
  const etag = r.headers.get('etag');
  assert.ok(etag && etag.includes('hol-1'), etag);

  const again = await h.api('GET', '/api/calendar/holidays', { headers: { 'If-None-Match': etag } });
  assert.equal(again.status, 304, 'همان نسخه ⇒ ۳۰۴');

  const part = await h.get('/api/calendar/holidays?from=2027-01-01&to=2027-03-15');
  assert.deepEqual(part.body.holidays.map((x) => x.date), ['2027-03-10']);
  assert.equal((await h.get('/api/calendar/holidays?from=1405-01-01')).status, 400, 'تاریخِ نادرست در بازه ⇒ ۴۰۰');

  const audit = await query(`SELECT detail FROM audit_logs WHERE action='admin.holidays_updated'`);
  assert.equal(audit.rows.length, 1);

  //  همان شکلی که calendar-core (و BusinessDay.kt) می‌گیرد
  const rules = { holidays: r.body.holidays.map((x) => x.date) };
  assert.equal(cal.dayKind('2027-03-21', rules), 'holiday');
  assert.equal(cal.dayKind('2027-03-22', rules), 'work');
});

test('قفلِ نسخه: نوشتن با نسخهٔ کهنه ۴۰۹ است و جدولِ تازه را رونویسی نمی‌کند', async () => {
  const before = (await h.get('/api/calendar/holidays')).body;
  const stale = await h.put('/api/admin/calendar/holidays', { version: 0, holidays: [] }, { token: superToken });
  assert.equal(stale.status, 409, JSON.stringify(stale.body));
  assert.deepEqual((await h.get('/api/calendar/holidays')).body, before);
  const noVersion = await h.put('/api/admin/calendar/holidays', { holidays: [] }, { token: superToken });
  assert.equal(noVersion.status, 400, 'بی نسخه نوشته نمی‌شود');

  //  دو نوشتنِ هم‌زمان با یک نسخه: دقیقاً یکی می‌نشیند
  const v = before.version;
  const [a, b] = await Promise.all([
    h.put('/api/admin/calendar/holidays', { version: v, holidays: [{ date: '2027-05-16', name: { fa: 'الف' } }] }, { token: superToken }),
    h.put('/api/admin/calendar/holidays', { version: v, holidays: [{ date: '2027-05-17', name: { fa: 'ب' } }] }, { token: superToken }),
  ]);
  assert.deepEqual([a.status, b.status].sort(), [200, 409]);
  const after = (await h.get('/api/calendar/holidays')).body;
  assert.equal(after.version, v + 1);
  assert.equal(after.holidays.length, 1);
});

test('سنجش: هر ردیفِ نادرست کلِ درخواست را رد می‌کند و چیزی نوشته نمی‌شود', async () => {
  const cur = (await h.get('/api/calendar/holidays')).body;
  const bad = [
    'not a list',
    [{ date: '2026-02-30', name: { fa: 'نیست' } }],
    [{ date: '1405-01-01', name: { fa: 'خورشیدی' } }],
    [{ date: '2026-03-21', name: { en: 'Nowruz' } }],
    [{ date: '2026-03-21', name: { fa: 'الف' } }, { date: '2026-03-21', name: { fa: 'ب' } }],
    [{ date: '2026-03-21', name: { fa: 'x'.repeat(81) } }],
    [{ date: '2026-03-21', name: { fa: 'الف', en: 5 } }],
    Array.from({ length: 501 }, (_, i) => ({ date: cal.addDays('2026-01-01', i), name: { fa: 'روز' } })),
  ];
  for (const holidays of bad) {
    const r = await h.put('/api/admin/calendar/holidays', { version: cur.version, holidays }, { token: superToken });
    assert.equal(r.status, 400, `${JSON.stringify(holidays).slice(0, 80)} ⇒ ${r.status} ${JSON.stringify(r.body)}`);
  }
  assert.deepEqual((await h.get('/api/calendar/holidays')).body, cur);
});

test('PATCH /api/admin/config راهِ دورِ نوشتنِ جدول نیست', async () => {
  const cur = (await h.get('/api/calendar/holidays')).body;
  await h.patch('/api/admin/config', { calendar_holidays: '{"version":99,"holidays":[]}' }, { token: superToken });
  assert.deepEqual((await h.get('/api/calendar/holidays')).body, cur);
});

test('سندِ خراب در app_config مثلِ «جدولی نیست» خوانده می‌شود، نه ۵۰۰', async () => {
  await query(`UPDATE app_config SET value='{oops' WHERE key='calendar_holidays'`);
  const r = await h.get('/api/calendar/holidays');
  assert.equal(r.status, 200);
  assert.deepEqual(r.body, { version: 0, updatedAt: 0, holidays: [] });
});
