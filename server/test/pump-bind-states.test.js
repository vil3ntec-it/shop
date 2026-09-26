'use strict';
/**
 * ⛔ «ثبتِ همین کامپیوتر» هرگز ۵۰۰ نمی‌دهد — در هیچ حالی از اشتراک و دستگاه.
 *
 * گزارشِ صاحبِ سامانه (۱۴۰۵/۰۷/۱۳، عکسِ پروفایلِ برنامهٔ پمپ): «خطای داخلیِ
 * سرور (کدِ پیگیری: …)» روی `POST /api/pump/device/bind`. هر حالی که مدیر از
 * پنل می‌سازد (استاندارد، وی‌آی‌پی، دائمی، سفارشی، مهلت، لغو، تعلیق، دورهٔ
 * آزمایشیِ تمام‌شده، دستگاهِ جداشده و برگشته) و بند شدنِ **هم‌زمان** (برنامه از
 * چند راه با هم می‌زند) این‌جا زده می‌شود و هیچ‌کدام نباید ۵۰۰ بدهد.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, newId, now } = require('../src/db');

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(`INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
               VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`, [newId('adm'), await pw.hashPassword('Admin!12345'), now()]);
});
test.after(async () => { await h.stop(); });

const dev = (uid) => ({ device: { uid, name: 'DESKTOP-پمپ', platform: 'windows' } });

test('هر حالِ اشتراک و دستگاه ⇒ بند شدن بی ۵۰۰', async () => {
  const T = { token: (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token };
  const give = (s, body) => h.post('/api/admin/pump/subscriptions', { stationId: s, ...body }, T);
  const setStatus = async (s, status) => {
    const r = await give(s, { plan: 'vip', days: 30 });
    return h.post(`/api/admin/pump/subscriptions/${r.body.subscription?.id || r.body.id}/status`, { status }, T);
  };
  const scenarios = {
    plain: async () => {},
    std: (s) => give(s, { plan: 'std', days: 30 }),
    vip: (s) => give(s, { plan: 'vip', days: 365 }),
    perm: (s) => give(s, { plan: 'perm' }),
    custom: (s) => give(s, { features: ['kar'], days: 5 }),
    grace: (s) => give(s, { plan: 'std', days: 1, graceDays: 3 }),
    cancelled: (s) => setStatus(s, 'cancelled'),
    suspended: (s) => setStatus(s, 'suspended'),
    trialOver: (s) => query('UPDATE stations SET created_at=$2, trial_started_at=$2 WHERE id=$1', [s, now() - 90 * 86400000]),
  };
  const bad = [];
  let i = 0;
  for (const [name, fn] of Object.entries(scenarios)) {
    const u = await h.newUser('b' + (++i), 'pump');
    const made = await h.post('/api/pump', { name: 'پمپ ' + name }, { token: u.accessToken });
    assert.equal(made.status, 201);
    const s = made.body.station.id;
    await h.post('/api/pump/device/bind', dev('pc-0'), { token: u.accessToken });
    const r = await fn(s);
    assert.ok(!r?.status || r.status < 300, `${name}: ${JSON.stringify(r?.body)}`);
    for (const uid of ['pc-0', 'pc-1']) {
      const b = await h.post('/api/pump/device/bind', dev(uid), { token: u.accessToken });
      if (b.status >= 500) bad.push(`${name}/${uid}: ${JSON.stringify(b.body)}`);
      else assert.equal(b.status, 201, `${name}/${uid}: ${JSON.stringify(b.body)}`);
    }
  }
  assert.deepEqual(bad, []);
});

test('بند شدنِ هم‌زمان از چند راه (برنامه: حلقه + کلیک + پروفایل) ⇒ بی ۵۰۰، یک دستگاه', async () => {
  const u = await h.newUser('هم‌زمان', 'pump');
  const tok = { token: u.accessToken };
  await Promise.all([h.get('/api/pump/me', tok), h.post('/api/pump', { name: 'پمپ' }, tok), h.post('/api/pump', { name: 'پمپ' }, tok)]);
  const all = await Promise.all([1, 2, 3, 4, 5, 6].map(() => h.post('/api/pump/device/bind', dev('pc-x'), tok)));
  for (const r of all) assert.ok(r.status < 500, JSON.stringify(r.body));
  const n = await query(`SELECT COUNT(*)::int AS n FROM station_devices d JOIN station_members m ON m.station_id=d.station_id WHERE m.user_id=$1`, [u.user?.id || u.id]);
  assert.equal(Number(n.rows[0].n), 1, 'یک کامپیوتر، یک ردیف');
});
