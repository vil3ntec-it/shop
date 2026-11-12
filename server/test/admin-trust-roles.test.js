'use strict';
/**
 * ABUSE-01 در پنلِ مرکزی (server#147، پنل ۱.۵۰.۹۷) — سه بندِ سمتِ سرورِ حساب برای `/api/admin/trust`:
 *
 *   ۱) «چه کسی»: از راهِ کلیدِ سرویس مسئول، تصمیم‌گیرنده، رأی‌دهندهٔ اعتراض، جریمه‌گذار/بردارنده و `actor_id` ِ رخدادها
 *      همان کاربرِ پنل است (`X-Actor-Id`) و `assigneeName` از `X-Actor-Name` (کران‌دار و پاک‌شده)؛ مدیرِ مستقیم همان امروز.
 *   ۲) `trust-read.v1`: صف، پرونده و جریمه‌ها برای ارشد، مدیر و بیننده (مالی ۴۰۳)؛ زیرِ ارشد بی نشانه و سابقهٔ شاکی،
 *      هم‌متن‌ها، مدرک و هیچ شناسهٔ آدم؛ دیدنِ پرونده سابقه دارد؛ مدرک و هر نوشتن فقط ارشد (۴۰۳ برای هر نقشِ دیگر، بی تغییر).
 *   ۳) `Idempotency-Key` روی چهار نوشتن (`lib/admin-write-once.js`): همان کلید ⇒ همان پاسخ با `Idempotent-Replayed` و یک
 *      سابقه/رخداد؛ بدنه یا درِ دیگر ⇒ ۴۰۹؛ کلید بسته به «انجام‌دهنده»؛ بی کلید همان رفتارِ پیشین.
 *   و مهاجرتِ ۸۵۵: برگشت و اجرای دوباره.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');

const PASS = 'Trust!Roles-1405';
const ids = {};
const toks = {};
let svc;
let shop;
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;
const HINT = '+937•••201';
const SHOP_USER = 'usr_trust_staff_77';

test.before(async () => {
  await h.start();
  const hash = await require('../src/lib/password').hashPassword(PASS);
  let i = 0;
  for (const [u, role] of [['tr-arshad', 'superadmin'], ['tr-modir', 'admin'], ['tr-mali', 'finance'], ['tr-binande', 'viewer']]) {
    ids[u] = newId('adm');
    await query(`INSERT INTO admins (id, username, name, password_hash, role, status, created_at) VALUES ($1,$2,$2,$3,$4,'active',$5)`,
      [ids[u], u, hash, role, now() + (++i)]);
  }
  svc = await require('../src/lib/admin-service').create({ name: 'پلِ شکایت', roleCap: 'superadmin', createdBy: ids['tr-arshad'] });
  const owner = await h.newUser('صاحبِ دکانِ شکایت');
  const made = await h.post('/api/shop', { name: 'دکانِ شکایت' }, { token: owner.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  shop = made.body.shop.id;
});
test.after(() => h.stop());
test.beforeEach(() => resetAll());

async function tok(u) {
  if (!toks[u]) {
    const r = await h.post('/api/admin/login', { username: u, password: PASS });
    assert.equal(r.status, 200, JSON.stringify(r.body));
    toks[u] = r.body.token;
  }
  return toks[u];
}
const M = async (who, method, p, body = null, headers = {}) => h.api(method, `/api/admin/trust${p}`, { token: await tok(who), body, headers });
/** پلِ پنل: کلیدِ سرویس + «انجام‌دهنده» */
const S = (actor, method, p, body = null, { role = 'superadmin', name = '', headers = {} } = {}) => h.api(method, `/api/admin/trust${p}`, {
  body, headers: { Authorization: `Bearer ${svc.key}`, 'X-Actor-Id': actor, 'X-Actor-Role': role,
    ...(name ? { 'X-Actor-Name': encodeURIComponent(name) } : {}), ...headers },
});
const caseRow = (id) => one('SELECT * FROM trust_cases WHERE id=$1', [id]);
const events = (id) => many('SELECT actor, actor_id, type FROM trust_case_events WHERE case_id=$1 ORDER BY created_at, id', [id]);
const audits = (action, target) => many('SELECT * FROM audit_logs WHERE action=$1 AND target_id=$2', [action, target]);

/** پروندهٔ باز با نشانهٔ شاکی، متنِ هم‌چکیده با یک پروندهٔ دیگر، مدرک و پاسخِ دکان (رخدادِ کاربرِ دکان). */
async function seedCase({ bodyHash = '' } = {}) {
  const id = `tc_${crypto.randomBytes(12).toString('hex')}`;
  const t = now();
  await query(`INSERT INTO trust_cases (id, shop_id, kind, subject, category, severity, body, reporter_key, reporter_hint, body_hash, created_at, updated_at)
               VALUES ($1,$2,'complaint','shop','fraud','high','پول گرفت و جنس نداد',$3,$4,$5,$6,$6)`,
  [id, shop, `rk-${uniq()}`, HINT, bodyHash, t]);
  await query(`INSERT INTO trust_case_events (id, case_id, shop_id, actor, actor_id, type, created_at) VALUES ($1,$2,$3,'customer','','filed',$4)`,
    [newId('tce'), id, shop, t]);
  await query(`INSERT INTO trust_case_events (id, case_id, shop_id, actor, actor_id, type, body, created_at) VALUES ($1,$2,$3,'shop',$4,'shop_response','پاسخِ دکان',$5)`,
    [newId('tce'), id, shop, SHOP_USER, t + 1]);
  const bytes = crypto.randomBytes(64);
  await query(`INSERT INTO trust_evidence (id, case_id, shop_id, mime, size, sha256, data, created_at) VALUES ($1,$2,$3,'image/jpeg',$4,$5,$6,$7)`,
    [`tev_${crypto.randomBytes(12).toString('hex')}`, id, shop, bytes.length, crypto.createHash('sha256').update(bytes).digest('hex'), bytes, t]);
  return id;
}
/** همان کاری که درِ اعتراضِ دکان می‌کند (کاربرِ دکان در `appealed_by` و رخداد). */
async function appealed(id) {
  const t = now();
  await query(`UPDATE trust_cases SET status='appealed', appeal_body='جنس را فرستادیم', appealed_by=$2, appealed_at=$3, updated_at=$3 WHERE id=$1`, [id, SHOP_USER, t]);
  await query(`INSERT INTO trust_case_events (id, case_id, shop_id, actor, actor_id, type, body, created_at) VALUES ($1,$2,$3,'shop',$4,'appealed','جنس را فرستادیم',$5)`,
    [newId('tce'), id, shop, SHOP_USER, t + 5]);
}

/* ═══════════════════════ ۱) «چه کسی» از راهِ پل ═══════════════════════ */

test('بند ۱: از راهِ پل مسئول/تصمیم/رأی/جریمه به نامِ کاربرِ پنل (X-Actor-Id) با assigneeName ِ پاک‌شده؛ مدیرِ مستقیم همان امروز', async () => {
  const c1 = await seedCase();
  const a = await S('panel:3', 'POST', `/cases/${c1}/assign`, {}, { name: '  علی‮ \u0007 رضا  ' });
  assert.equal(a.status, 200, JSON.stringify(a.body));
  assert.equal(a.body.assignee, 'panel:3');
  assert.equal(a.body.assigneeName, 'علی رضا', 'نویسهٔ کنترلی و جهت‌دهیِ پنهان پاک، فاصله یکی');
  let row = await caseRow(c1);
  assert.equal(row.assignee, 'panel:3');
  assert.equal(row.assignee_name, 'علی رضا');
  assert.notEqual(row.assignee, svc.id, 'نه شناسهٔ مشترکِ کلید');
  const q = await M('tr-arshad', 'GET', '/cases?status=all');
  const inQueue = q.body.cases.find((x) => x.id === c1);
  assert.equal(inQueue.assignee, 'panel:3');
  assert.equal(inQueue.assigneeName, 'علی رضا');

  //  تصمیم روی پروندهٔ بی مسئول ⇒ مسئول و نامش همان تصمیم‌گیرنده؛ جریمه‌گذار هم
  const c2 = await seedCase();
  const d = await S('panel:4', 'POST', `/cases/${c2}/decide`, { outcome: 'upheld', reason: 'کلاه‌برداری ثابت شد', sanction: { kind: 'warning' } },
    { name: 'مریم' });
  assert.equal(d.status, 200, JSON.stringify(d.body));
  row = await caseRow(c2);
  assert.deepEqual([row.decided_by, row.assignee, row.assignee_name], ['panel:4', 'panel:4', 'مریم']);
  const sn = await one('SELECT created_by, lifted_by FROM trust_sanctions WHERE id=$1', [d.body.sanction.id]);
  assert.equal(sn.created_by, 'panel:4');
  const adminEv = (await events(c2)).filter((e) => e.actor === 'admin');
  assert.deepEqual([...new Set(adminEv.map((e) => e.actor_id))], ['panel:4'], JSON.stringify(adminEv));

  //  رأیِ «برگشت» ⇒ رأی‌دهنده و بردارندهٔ جریمه همان کاربر
  await appealed(c2);
  const ap = await S('panel:5', 'POST', `/cases/${c2}/appeal-decision`, { outcome: 'reversed', reason: 'رسیدِ تحویل آمد' });
  assert.equal(ap.status, 200, JSON.stringify(ap.body));
  row = await caseRow(c2);
  assert.equal(row.appeal_decided_by, 'panel:5');
  assert.equal((await one('SELECT lifted_by FROM trust_sanctions WHERE id=$1', [d.body.sanction.id])).lifted_by, 'panel:5');
  assert.ok((await events(c2)).filter((e) => ['appeal_decided', 'sanction_lifted'].includes(e.type)).every((e) => e.actor_id === 'panel:5'));

  //  برداشتنِ جریمه از درِ خودش
  const c3 = await seedCase();
  const d3 = await M('tr-arshad', 'POST', `/cases/${c3}/decide`, { outcome: 'upheld', reason: 'هشدار', sanction: { kind: 'warning' } });
  assert.equal(d3.status, 200, JSON.stringify(d3.body));
  const l3 = await S('panel:6', 'POST', `/sanctions/${d3.body.sanction.id}/lift`, { reason: 'اشتباه بود' });
  assert.equal(l3.status, 200, JSON.stringify(l3.body));
  assert.equal((await one('SELECT lifted_by FROM trust_sanctions WHERE id=$1', [d3.body.sanction.id])).lifted_by, 'panel:6');

  //  مدیرِ مستقیم: همان امروز (شناسهٔ خودش، بی نام)؛ نامِ بلند کران‌دار
  row = await caseRow(c3);
  assert.deepEqual([row.decided_by, row.assignee, row.assignee_name], [ids['tr-arshad'], ids['tr-arshad'], '']);
  const c4 = await seedCase();
  const own = await M('tr-arshad', 'POST', `/cases/${c4}/assign`, {}, { 'X-Actor-Name': encodeURIComponent('جعلی') });
  assert.equal(own.status, 200);
  assert.deepEqual([own.body.assignee, own.body.assigneeName], [ids['tr-arshad'], ''], 'سرآیندِ نام بی کلیدِ سرویس نادیده');
  const long = await S('panel:7', 'POST', `/cases/${c4}/assign`, {}, { name: 'ن'.repeat(200) });
  assert.equal(long.body.assigneeName.length, 80);
});

/* ═══════════════════════ ۲) خواندن برای بیننده، نمای کم‌تر ═══════════════════════ */

/** پرونده‌ای با همهٔ چیزهای حساس: هم‌متن، مدرک، پاسخِ دکان، مسئول، تصمیم با جریمه، اعتراض و رأی */
async function fullCase() {
  const bh = `bh-${uniq()}`;
  const id = await seedCase({ bodyHash: bh });
  await seedCase({ bodyHash: bh });
  assert.equal((await S('panel:3', 'POST', `/cases/${id}/assign`, {}, { name: 'علی' })).status, 200);
  const d = await M('tr-arshad', 'POST', `/cases/${id}/decide`, { outcome: 'upheld', reason: 'ثابت شد', sanction: { kind: 'suspend_storefront' } });
  assert.equal(d.status, 200, JSON.stringify(d.body));
  await appealed(id);
  assert.equal((await M('tr-arshad', 'POST', `/cases/${id}/appeal-decision`, { outcome: 'upheld', reason: 'تصمیم می‌ماند' })).status, 200);
  return { id, sanctionId: d.body.sanction.id };
}

const SECRET_KEYS = ['reporterHint', 'reporterStats', 'sameText', 'evidence'];

function assertRedacted(c, who) {
  for (const k of SECRET_KEYS) assert.ok(!(k in c), `${who}: «${k}» نباید باشد`);
  assert.ok(c.history.length >= 5, `${who}: تاریخچه هست`);
  assert.ok(c.history.every((e) => !('actorId' in e)), `${who}: history[].actorId نباید باشد`);
  assert.ok(c.history.every((e) => !('assignee' in (e.data || {}))), `${who}: شناسهٔ مسئول در دادهٔ رخداد نه`);
  assert.ok(!('by' in c.decision), `${who}: decision.by نه`);
  assert.ok(!('by' in c.appeal) && !('decidedBy' in c.appeal), `${who}: appeal.by/decidedBy نه`);
  assert.ok(c.sanctions.every((s) => !('createdBy' in s) && !('liftedBy' in s)), `${who}: جریمه بی شناسهٔ آدم`);
  const json = JSON.stringify(c);
  for (const leak of [HINT, SHOP_USER, ids['tr-arshad'], 'tev_']) assert.ok(!json.includes(leak), `${who}: «${leak}» نشت کرد`);
  //  آن‌چه می‌ماند: خودِ کارِ پرونده
  assert.equal(c.body, 'پول گرفت و جنس نداد');
  assert.equal(c.assignee, 'panel:3');
  assert.equal(c.assigneeName, 'علی');
  assert.equal(c.evidenceCount, 1);
  assert.equal(c.decision.reason, 'ثابت شد');
  assert.equal(c.appeal.body, 'جنس را فرستادیم');
}

test('بند ۲: trust-read.v1 — ارشد کامل؛ مدیر و بیننده (انسان یا از راهِ پل) نمای کم‌تر با سابقهٔ دیدن؛ مالی ۴۰۳', async () => {
  const { id } = await fullCase();
  const me = await h.api('GET', '/api/admin/me', { token: await tok('tr-binande') });
  assert.ok(me.body.capabilities.includes('trust-read.v1'));

  const full = (await M('tr-arshad', 'GET', `/cases/${id}`)).body.case;
  assert.equal(full.reporterHint, HINT);
  assert.equal(full.reporterStats.total >= 1, true);
  assert.equal(full.sameText.length, 1);
  assert.equal(full.evidence.length, 1);
  assert.ok(full.history.some((e) => e.actorId === SHOP_USER));
  assert.equal(full.decision.by, ids['tr-arshad']);
  assert.equal(full.appeal.by, SHOP_USER);

  for (const who of ['tr-modir', 'tr-binande']) {
    const q = await M(who, 'GET', '/cases?status=all');
    assert.equal(q.status, 200, `${who} ⇒ ${q.status} ${JSON.stringify(q.body)}`);
    assert.ok(q.body.cases.some((x) => x.id === id));
    const before = (await audits('admin.trust_case_viewed', id)).length;
    const r = await M(who, 'GET', `/cases/${id}`);
    assert.equal(r.status, 200, JSON.stringify(r.body));
    assert.match(r.headers.get('cache-control') || '', /no-store/);
    assertRedacted(r.body.case, who);
    const seen = await audits('admin.trust_case_viewed', id);
    assert.equal(seen.length, before + 1, `${who}: دیدنِ پرونده سابقه دارد (TEN-04)`);
    assert.ok(seen.some((x) => x.user_id === ids[who]), `${who}: سابقه به نامِ همان مدیر`);
    const sl = await M(who, 'GET', '/sanctions');
    assert.equal(sl.status, 200);
    assert.ok(sl.body.sanctions.length >= 1);
    assert.ok(sl.body.sanctions.every((s) => !('createdBy' in s) && !('liftedBy' in s)), `${who}: جریمه‌ها بی شناسهٔ آدم`);
  }
  //  بیننده از راهِ پل (نقشِ مؤثر = viewer) همان نمای کم‌تر
  const viaBridge = await S('panel:8', 'GET', `/cases/${id}`, null, { role: 'viewer' });
  assert.equal(viaBridge.status, 200);
  assertRedacted(viaBridge.body.case, 'پل/viewer');
  assert.equal((await S('panel:8', 'GET', '/sanctions', null, { role: 'superadmin' })).body.sanctions.every((s) => 'createdBy' in s), true,
    'ارشدِ پل نمای کامل');

  for (const p of ['/cases', `/cases/${id}`, '/sanctions']) {
    const r = await M('tr-mali', 'GET', p);
    assert.equal(r.status, 403, `مالی ${p} ⇒ ${r.status}`);
    assert.equal(r.body.error.code, 'role_forbidden');
  }
});

test('بند ۲: مدرک و هر چهار نوشتن فقط ارشد — ۴۰۳ برای مدیر، مالی، بیننده (و بیننده/مدیرِ پل) بی هیچ تغییر', async () => {
  const id = await seedCase();
  const d = await M('tr-arshad', 'POST', `/cases/${id}/decide`, { outcome: 'upheld', reason: 'آزمون', sanction: { kind: 'warning' } });
  assert.equal(d.status, 200);
  await appealed(id);
  const open = await seedCase();
  const ev = (await one('SELECT id FROM trust_evidence WHERE case_id=$1', [id])).id;
  const snap = async () => JSON.stringify([await caseRow(id), await caseRow(open), await events(id), await events(open),
    await many('SELECT * FROM trust_sanctions WHERE case_id=$1', [id])]);
  const before = await snap();
  const doors = [
    ['GET', `/cases/${id}/evidence/${ev}`, null],
    ['POST', `/cases/${open}/assign`, {}],
    ['POST', `/cases/${open}/decide`, { outcome: 'dismissed', reason: 'آزمونِ نقش' }],
    ['POST', `/cases/${id}/appeal-decision`, { outcome: 'reversed', reason: 'آزمونِ نقش' }],
    ['POST', `/sanctions/${d.body.sanction.id}/lift`, { reason: 'آزمونِ نقش' }],
  ];
  for (const [method, p, body] of doors) {
    for (const who of ['tr-modir', 'tr-mali', 'tr-binande']) {
      const r = await M(who, method, p, body);
      assert.equal(r.status, 403, `${who} ${method} ${p} ⇒ ${r.status}`);
    }
    for (const role of ['admin', 'viewer']) {
      const r = await S('panel:9', method, p, body, { role });
      assert.equal(r.status, 403, `پل/${role} ${method} ${p} ⇒ ${r.status}`);
    }
  }
  assert.equal(await snap(), before, 'هیچ ۴۰۳ی چیزی را عوض نکرد');
  const ok = await h.download(`/api/admin/trust/cases/${id}/evidence/${ev}`, { token: await tok('tr-arshad') });
  assert.equal(ok.status, 200, 'ارشد مدرک را می‌گیرد');
});

/* ═══════════════════════ ۳) Idempotency-Key ═══════════════════════ */

test('بند ۳: Idempotency-Key روی چهار نوشتن — همان کلید یک کار و یک سابقه؛ بدنه/درِ دیگر ۴۰۹؛ کلید بسته به انجام‌دهنده', async () => {
  const id = await seedCase();
  const K = (n) => ({ 'Idempotency-Key': `trust-${n}-${uniq()}` });

  //  مسئول
  const ka = K('asg');
  const a1 = await S('panel:3', 'POST', `/cases/${id}/assign`, {}, { headers: ka });
  const a2 = await S('panel:3', 'POST', `/cases/${id}/assign`, {}, { headers: ka });
  assert.equal(a1.status, 200);
  assert.equal(a2.status, 200);
  assert.equal(a2.headers.get('idempotent-replayed'), 'true');
  assert.deepEqual(a2.body, a1.body);
  assert.equal((await events(id)).filter((e) => e.type === 'assigned').length, 1);
  assert.equal((await audits('admin.trust_case_assigned', id)).length, 1);

  //  تصمیم با جریمه: یک تصمیم، یک جریمه، یک سابقه؛ همان پاسخ (همان شناسهٔ جریمه)
  const kd = K('dec');
  const body = { outcome: 'upheld', reason: 'ثابت شد', sanction: { kind: 'warning' } };
  const d1 = await S('panel:3', 'POST', `/cases/${id}/decide`, body, { headers: kd });
  const d2 = await S('panel:3', 'POST', `/cases/${id}/decide`, body, { headers: kd });
  assert.equal(d1.status, 200, JSON.stringify(d1.body));
  assert.equal(d2.status, 200, `بازپخش، نه ۴۰۹ already_decided: ${JSON.stringify(d2.body)}`);
  assert.equal(d2.headers.get('idempotent-replayed'), 'true');
  assert.equal(d2.body.sanction.id, d1.body.sanction.id);
  assert.equal(Number((await one('SELECT COUNT(*)::int n FROM trust_sanctions WHERE case_id=$1', [id])).n), 1);
  assert.equal((await audits('admin.trust_case_decided', id)).length, 1);
  //  همان کلید، بدنهٔ دیگر یا درِ دیگر ⇒ ۴۰۹ و هیچ تغییر
  const other = await S('panel:3', 'POST', `/cases/${id}/decide`, { ...body, reason: 'دلیلِ دیگر' }, { headers: kd });
  assert.equal(other.status, 409);
  assert.equal(other.body.error.code, 'idempotency_key_reused');
  const door = await S('panel:3', 'POST', `/sanctions/${d1.body.sanction.id}/lift`, { reason: 'ثابت شد' }, { headers: kd });
  assert.equal(door.body.error.code, 'idempotency_key_reused');
  assert.equal((await one('SELECT lifted_at FROM trust_sanctions WHERE id=$1', [d1.body.sanction.id])).lifted_at, null);
  //  همان کلید از کاربرِ دیگرِ پنل ⇒ کارِ خودِ او (۴۰۹ ِ ماشینِ وضعیت)، نه پاسخِ کسِ دیگر
  const foreign = await S('panel:4', 'POST', `/cases/${id}/decide`, body, { headers: kd });
  assert.equal(foreign.status, 409);
  assert.equal(foreign.body.error.code, 'already_decided');
  const bad = await S('panel:3', 'POST', `/cases/${id}/decide`, body, { headers: { 'Idempotency-Key': 'bad key!' } });
  assert.equal(bad.status, 400);
  assert.equal(bad.body.error.code, 'bad_idempotency_key');

  //  رأیِ اعتراض و برداشتن
  await appealed(id);
  const kp = K('app');
  const p1 = await M('tr-arshad', 'POST', `/cases/${id}/appeal-decision`, { outcome: 'upheld', reason: 'می‌ماند' }, kp);
  const p2 = await M('tr-arshad', 'POST', `/cases/${id}/appeal-decision`, { outcome: 'upheld', reason: 'می‌ماند' }, kp);
  assert.equal(p1.status, 200, JSON.stringify(p1.body));
  assert.equal(p2.headers.get('idempotent-replayed'), 'true');
  assert.equal((await audits('admin.trust_appeal_decided', id)).length, 1);
  const kl = K('lift');
  const l1 = await M('tr-arshad', 'POST', `/sanctions/${d1.body.sanction.id}/lift`, { reason: 'اشتباه' }, kl);
  const l2 = await M('tr-arshad', 'POST', `/sanctions/${d1.body.sanction.id}/lift`, { reason: 'اشتباه' }, kl);
  assert.equal(l1.status, 200, JSON.stringify(l1.body));
  assert.equal(l2.status, 200, `بازپخش، نه ۴۰۹ already_lifted: ${JSON.stringify(l2.body)}`);
  assert.equal(l2.headers.get('idempotent-replayed'), 'true');
  assert.equal((await audits('admin.trust_sanction_lifted', d1.body.sanction.id)).length, 1);
  //  بی کلید همان رفتارِ پیشین: تکرار ⇒ ۴۰۹ ِ ماشینِ وضعیت
  const l3 = await M('tr-arshad', 'POST', `/sanctions/${d1.body.sanction.id}/lift`, { reason: 'اشتباه' });
  assert.equal(l3.status, 409);
  assert.equal(l3.body.error.code, 'already_lifted');
  //  نقش و دلیل پیش از کلید: درخواستِ ردشده نشانی نمی‌گذارد
  const kr = K('noreason');
  const nr = await M('tr-arshad', 'POST', `/sanctions/${d1.body.sanction.id}/lift`, {}, kr);
  assert.equal(nr.body.error.code, 'reason_required');
  assert.equal(Number((await one('SELECT COUNT(*)::int n FROM idempotency_keys WHERE key=$1', [kr['Idempotency-Key']])).n), 0);
});

test('مهاجرتِ ۸۵۵: برگشت و اجرای دوباره', async () => {
  const dir = path.join(__dirname, '..', 'migrations');
  const col = async () => Boolean(await one(
    `SELECT 1 AS x FROM information_schema.columns WHERE table_name='trust_cases' AND column_name='assignee_name'`));
  assert.equal(await col(), true);
  await query(fs.readFileSync(path.join(dir, 'rollback', '855_trust_assignee_name.down.sql'), 'utf8'));
  assert.equal(await col(), false);
  await query(fs.readFileSync(path.join(dir, '855_trust_assignee_name.sql'), 'utf8'));
  await query(fs.readFileSync(path.join(dir, '855_trust_assignee_name.sql'), 'utf8'));
  assert.equal(await col(), true);
});
