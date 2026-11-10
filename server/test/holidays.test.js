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

//  دلیلِ نوشتن (۳ تا ۲۰۰ نویسه) از LOC-04 ِ ۱۴۰۵/۰۸/۲۰ لازم است؛ آزمون‌های پیشین همان را می‌فرستند
const R = 'جدولِ رسمیِ ۱۴۰۶';

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
  const body = { version: 0, holidays: TABLE, reason: R };
  assert.equal((await h.put('/api/admin/calendar/holidays', body)).status, 401);
  assert.equal((await h.put('/api/admin/calendar/holidays', body, { token: u.accessToken })).status, 401);
  assert.equal((await h.put('/api/admin/calendar/holidays', body, { token: adminToken })).status, 403);
  assert.equal((await h.get('/api/admin/calendar/holidays', { token: adminToken })).status, 200, 'خواندن برای مدیرِ عادی باز است');
  assert.equal((await h.get('/api/calendar/holidays')).body.version, 0);
});

test('مدیرِ ارشد جدول را می‌گذارد: مرتب، پاک، با نسخهٔ تازه؛ همه می‌خوانند و بازه کار می‌کند', async () => {
  const w = await h.put('/api/admin/calendar/holidays', { version: 0, holidays: TABLE, reason: R }, { token: superToken });
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
  const stale = await h.put('/api/admin/calendar/holidays', { version: 0, holidays: [], reason: R }, { token: superToken });
  assert.equal(stale.status, 409, JSON.stringify(stale.body));
  assert.deepEqual((await h.get('/api/calendar/holidays')).body, before);
  const noVersion = await h.put('/api/admin/calendar/holidays', { holidays: [], reason: R }, { token: superToken });
  assert.equal(noVersion.status, 400, 'بی نسخه نوشته نمی‌شود');

  //  دو نوشتنِ هم‌زمان با یک نسخه: دقیقاً یکی می‌نشیند
  const v = before.version;
  const [a, b] = await Promise.all([
    h.put('/api/admin/calendar/holidays', { version: v, holidays: [{ date: '2027-05-16', name: { fa: 'الف' } }], reason: R }, { token: superToken }),
    h.put('/api/admin/calendar/holidays', { version: v, holidays: [{ date: '2027-05-17', name: { fa: 'ب' } }], reason: R }, { token: superToken }),
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
    const r = await h.put('/api/admin/calendar/holidays', { version: cur.version, holidays, reason: R }, { token: superToken });
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

/* ── LOC-04 ِ ۱۴۰۵/۰۸/۲۰ (`claude/loc-04`): دلیل، یک‌باره بودن، سابقه در همان تراکنش، خواندنِ سندِ نیمه‌خراب، سقفِ نرخ ── */

const holidays = require('../src/lib/holidays');
const VEC = require('../../core/test-vectors/calendar-holidays-v1.json');
const auditCount = async () => Number((await query(`SELECT count(*)::int AS n FROM audit_logs WHERE action='admin.holidays_updated'`)).rows[0].n);

test('بردارِ مشترکِ calendar-holidays-v1 (همان Holidays.parse ِ کاتلین): نسخه و روزهای خوانده‌شده', () => {
  assert.equal(VEC.version, 1);
  assert.ok(VEC.parse.length >= 10);
  for (const c of VEC.parse) {
    const doc = holidays.parse(c.body);
    assert.equal(doc.version, c.want.version, c.name);
    assert.deepEqual(doc.holidays.map((x) => x.date), c.want.dates, c.name);
  }
});

test('سندِ نیمه‌خراب در پایگاه: ردیفِ بد کنار می‌رود و GET ۵۰۰ نمی‌دهد', async () => {
  const bad = VEC.parse.find((c) => c.want.version === 7);
  await query(`UPDATE app_config SET value=$1 WHERE key='calendar_holidays'`, [bad.body]);
  const r = await h.get('/api/calendar/holidays?from=2000-01-01');
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.version, 7);
  assert.deepEqual(r.body.holidays.map((x) => x.date), bad.want.dates);
  for (const x of r.body.holidays) assert.equal(typeof x.name, 'object', 'نام همیشه شیء است');
  await query(`DELETE FROM app_config WHERE key='calendar_holidays'`);
});

test('نوشتن بی دلیل (کمتر از ۳ نویسه) ۴۰۰ ِ reason_required است و هیچ چیزی نمی‌نشیند', async () => {
  const before = (await h.get('/api/calendar/holidays')).body;
  const n = await auditCount();
  for (const reason of [undefined, '', 'ab', '   ']) {
    const r = await h.put('/api/admin/calendar/holidays', { version: before.version, holidays: TABLE, reason }, { token: superToken });
    assert.equal(r.status, 400, `${JSON.stringify(reason)} ⇒ ${r.status}`);
    assert.equal(r.body?.error?.code, 'reason_required', JSON.stringify(r.body));
  }
  assert.deepEqual((await h.get('/api/calendar/holidays')).body, before);
  assert.equal(await auditCount(), n);
});

test('سابقه با دلیل و شمارِ افزوده/برداشته؛ تکرارِ همان جدول (پاسخِ گم‌شده) یک بار است', async () => {
  const before = (await h.get('/api/calendar/holidays')).body;
  const n = await auditCount();
  const w = await h.put('/api/admin/calendar/holidays', { version: before.version, holidays: TABLE, reason: 'رؤیتِ ماه' }, { token: superToken });
  assert.equal(w.status, 200, JSON.stringify(w.body));
  assert.equal(w.body.version, before.version + 1);
  assert.equal(w.body.unchanged, false);
  const rows = (await query(`SELECT detail FROM audit_logs WHERE action='admin.holidays_updated' ORDER BY created_at DESC, id DESC LIMIT 1`)).rows;
  const d = typeof rows[0].detail === 'string' ? JSON.parse(rows[0].detail) : rows[0].detail;
  assert.equal(d.reason, 'رؤیتِ ماه');
  assert.equal(d.version, w.body.version);
  assert.equal(d.count, 3);
  assert.equal(typeof d.added, 'number');
  assert.equal(typeof d.removed, 'number');
  assert.equal(await auditCount(), n + 1);

  //  همان درخواست دوباره (برنامه پاسخ را نگرفت و همان نسخهٔ کهنه را دارد) ⇒ همان جدول، نسخهٔ تازه‌ای نه، سابقهٔ دومی نه
  const again = await h.put('/api/admin/calendar/holidays', { version: before.version, holidays: [...TABLE].reverse(), reason: 'رؤیتِ ماه' }, { token: superToken });
  assert.equal(again.status, 200, JSON.stringify(again.body));
  assert.equal(again.body.version, w.body.version);
  assert.equal(again.body.unchanged, true);
  //  با نسخهٔ درست هم: جدولِ یکسان ⇒ ETag ِ برنامه‌ها عوض نمی‌شود
  const same = await h.put('/api/admin/calendar/holidays', { version: w.body.version, holidays: TABLE, reason: 'دوباره' }, { token: superToken });
  assert.equal(same.body.version, w.body.version);
  assert.equal(same.body.unchanged, true);
  assert.equal(await auditCount(), n + 1);
  //  ولی جدولِ دیگر با نسخهٔ کهنه همچنان ۴۰۹ است
  const other = await h.put('/api/admin/calendar/holidays', { version: before.version, holidays: TABLE.slice(1), reason: 'کهنه' }, { token: superToken });
  assert.equal(other.status, 409);
});

test('بازه: from پس از to ⇒ ۴۰۰؛ If-None-Match با چند ETag ⇒ ۳۰۴', async () => {
  assert.equal((await h.get('/api/calendar/holidays?from=2027-03-22&to=2027-03-01')).status, 400);
  const r = await h.get('/api/calendar/holidays');
  const etag = r.headers.get('etag');
  const again = await h.api('GET', '/api/calendar/holidays', { headers: { 'If-None-Match': `W/"hol-0--", ${etag}` } });
  assert.equal(again.status, 304);
  assert.match(r.headers.get('cache-control') || '', /public/);
});

test('نخستین نوشتنِ هم‌زمان (هنوز هیچ ردیفی نیست): دقیقاً یکی می‌نشیند، با یک سابقه', async () => {
  await query(`DELETE FROM app_config WHERE key='calendar_holidays'`);
  const n = await auditCount();
  const [a, b] = await Promise.all([
    h.put('/api/admin/calendar/holidays', { version: 0, holidays: [{ date: '2028-01-01', name: { fa: 'الف' } }], reason: R }, { token: superToken }),
    h.put('/api/admin/calendar/holidays', { version: 0, holidays: [{ date: '2028-01-02', name: { fa: 'ب' } }], reason: R }, { token: superToken }),
  ]);
  assert.deepEqual([a.status, b.status].sort(), [200, 409], `${JSON.stringify(a.body)} ${JSON.stringify(b.body)}`);
  const after = (await h.get('/api/calendar/holidays')).body;
  assert.equal(after.version, 1);
  assert.equal(after.holidays.length, 1);
  assert.equal(await auditCount(), n + 1);
});

test('سقفِ نرخِ IP پیش از هر کار: خواندنِ همگانی و درِ مدیر (حتی بی‌توکن) ۴۲۹ می‌گیرند', async () => {
  const { resetAll } = require('../src/middleware/ratelimit');
  resetAll();
  let pub = 0;
  for (let i = 0; i < 125; i++) if ((await h.get('/api/calendar/holidays')).status === 429) pub++;
  assert.ok(pub >= 5, `خواندنِ همگانی ۱۲۵ بار در دقیقه ⇒ ${pub} بار ۴۲۹`);
  let adm = 0;
  for (let i = 0; i < 65; i++) if ((await h.get('/api/admin/calendar/holidays')).status === 429) adm++;
  assert.ok(adm >= 5, `درِ مدیرِ بی‌توکن ۶۵ بار در دقیقه ⇒ ${adm} بار ۴۲۹ (سقف پیش از احراز)`);
  resetAll();
});
