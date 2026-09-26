'use strict';
/**
 * ⛔ «همهٔ حساب‌ها اول چک شوند؛ هر حسابی که آزمایشی نگرفته باید بگیرد —
 * و از سرور، خودکار» (صاحبِ سامانه، ۱۴۰۵/۰۷/۱۳). مهاجرتِ ۰۳۲.
 *
 * حالِ پیش از مهاجرت همین‌جا ساخته می‌شود (ستونِ تازه `NULL`) و بعد خودِ
 * متنِ مهاجرت اجرا می‌شود — همان کاری که سرور سرِ به‌روزرسانی یک بار می‌کند.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const h = require('./helpers');
const { query, newId, now } = require('../src/db');

const DAY = 86400000;
const SQL = fs.readFileSync(path.join(__dirname, '..', 'migrations', '032_station_trial_start.sql'), 'utf8');
const backfill = SQL.slice(SQL.indexOf('UPDATE stations s'));

test.before(async () => { await h.start(); });
test.after(async () => { await h.stop(); });

async function station(label, ageDays) {
  const u = await h.newUser(label, 'pump');
  const made = await h.post('/api/pump', { name: 'پمپ ' + label }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const id = made.body.station.id;
  //  «پیش از مهاجرت»: پمپی به این سن، و ستونِ آغاز هنوز خالی
  await query('UPDATE stations SET created_at=$2, trial_started_at=NULL WHERE id=$1', [id, now() - ageDays * DAY]);
  return { id, u };
}

async function give(id, status) {
  const t = now();
  await query(
    `INSERT INTO station_subscriptions (id, station_id, plan, status, starts_at, ends_at, created_at, updated_at)
     VALUES ($1,$2,'vip',$3,$4,$5,$4,$4)`,
    [newId('sub'), id, status, t - 40 * DAY, t - 5 * DAY]
  );
}

test('پمپِ تازه همان لحظهٔ ساخته شدن مُهرِ آغاز می‌گیرد', async () => {
  const u = await h.newUser('new-stamp', 'pump');
  const made = await h.post('/api/pump', { name: 'پمپِ تازه' }, { token: u.accessToken });
  const row = (await query('SELECT created_at, trial_started_at FROM stations WHERE id=$1', [made.body.station.id])).rows[0];
  assert.equal(Number(row.trial_started_at), Number(row.created_at));
});

test('⛔ یک بار، برای همه: هر پمپی که آزمایشی نگرفته، می‌گیرد — و بقیه دست نمی‌خورند', async () => {
  const old = await station('old-never', 90);          // دوره‌اش بی‌آن‌که دیده باشد تمام شد
  const fresh = await station('in-trial', 10);         // هنوز در دوره
  const cancelled = await station('had-sub', 90);      // روزی اشتراک داشت (لغو)
  await give(cancelled.id, 'cancelled');
  const pending = await station('pending-only', 90);   // فقط درخواستِ خرید، تاییدنشده
  await give(pending.id, 'pending');

  const t0 = now();
  await query(backfill);

  const read = async (id) => (await query('SELECT created_at, trial_started_at FROM stations WHERE id=$1', [id])).rows[0];
  const o = await read(old.id);
  assert.ok(Number(o.trial_started_at) >= t0 - 60000, 'پمپِ کهنهٔ بی‌اشتراک از همین حالا آزمایشی گرفت');
  const f = await read(fresh.id);
  assert.equal(Number(f.trial_started_at), Number(f.created_at), '⛔ دورهٔ جاری دراز نشد');
  const c = await read(cancelled.id);
  assert.equal(Number(c.trial_started_at), Number(c.created_at), '⛔ حسابی که اشتراک داشته دوباره آزمایشی نگرفت');
  const p = await read(pending.id);
  assert.ok(Number(p.trial_started_at) >= t0 - 60000, 'درخواستِ خریدِ تاییدنشده آزمایشی را نمی‌خورد');

  //  خودِ سرور همین را می‌گوید — همان جوابی که برنامه و پنل می‌گیرند
  const me = await h.get('/api/pump/me', { token: old.u.accessToken });
  assert.equal(me.body.entitlement.source, 'trial', JSON.stringify(me.body.entitlement));
  assert.equal(me.body.entitlement.trial.daysLeft, 30);
  const meF = await h.get('/api/pump/me', { token: fresh.u.accessToken });
  assert.equal(meF.body.entitlement.trial.daysLeft, 20);
  const meC = await h.get('/api/pump/me', { token: cancelled.u.accessToken });
  assert.notEqual(meC.body.entitlement.source, 'trial');

  //  ⛔ بارِ دوم هیچ چیزی را عوض نمی‌کند — دوره خودبه‌خود دوباره باز نمی‌شود
  await query('UPDATE stations SET trial_started_at=$2 WHERE id=$1', [old.id, now() - 90 * DAY]);
  await query(backfill);
  assert.equal(Number((await read(old.id)).trial_started_at) < now() - 80 * DAY, true);
});

test('⛔ رباتِ دوره‌ای همان قاعده را همیشه اجرا می‌کند — فقط خالی‌ها را پر می‌کند', async () => {
  const { sweep } = require('../src/lib/trial-sweep');
  const lost = await station('sweep-lost', 60);          // مُهر ندارد، دوره گذشته
  const young = await station('sweep-young', 5);         // مُهر ندارد، هنوز در دوره
  const t0 = now();
  const n = await sweep();
  assert.ok(n >= 2, `دستِ‌کم دو پمپ مُهر خوردند (${n})`);
  const read = async (id) => (await query('SELECT created_at, trial_started_at FROM stations WHERE id=$1', [id])).rows[0];
  assert.ok(Number((await read(lost.id)).trial_started_at) >= t0 - 60000, 'پمپِ سوخته سی روزِ تازه گرفت');
  const y = await read(young.id);
  assert.equal(Number(y.trial_started_at), Number(y.created_at), 'پمپِ درونِ دوره دست نخورد');
  const me = await h.get('/api/pump/me', { token: lost.u.accessToken });
  assert.equal(me.body.entitlement.trial.daysLeft, 30);
  //  بارِ دوم هیچ
  assert.equal(await sweep(), 0);
});
