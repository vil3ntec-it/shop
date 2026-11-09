'use strict';
/**
 * OPS-01@29 (§۲۹.۸) — «هشدارهای بحرانی به مسئولِ مربوط برسند»: `lib/ops-paging.js` و قاعدهٔ `critical_health`.
 *
 *   • پرچمِ `ops.critical_alerts` خاموش ⇒ هیچ ارسال، هیچ ردیف، شدتِ کارها همان «high»، قاعده «off»
 *   • سنجشِ هر شرط درست سرِ آستانه: db_busy، جهشِ ۵xx، صفِ گیرکرده، شکستِ پیاپیِ تطبیق، کارِ گیر/شکست‌خورده
 *   • یک خبر برای هر رخداد؛ دو دور، راه‌اندازیِ دوباره و دو نمونهٔ هم‌زمان دوباره نمی‌فرستند
 *   • سقفِ هر شرط (یک خبرِ تازه در ساعت)، پیگیری سرِ N دقیقه و حداکثر M بار، «دیدم» پیگیری را می‌ایستاند
 *   • «بسته شد» یک بار (دستی همان دم، خودکار پس از آرامش)؛ پس‌زده بی‌صدا بسته می‌شود
 *   • گیرنده فقط مدیرِ فعالِ پلتفرم (admin/superadmin) — هنگامِ نوشتن (۴۰۰/۴۰۳) و هنگامِ فرستادن
 */
const test = require('node:test');
const assert = require('node:assert/strict');

let h; let db; let paging; let alerts; let live; let jobBeat;
const PASS = 'Admin!12345';
const MIN = 60_000;
const ids = {};
const sent = [];

const tokens = {};
async function tok(u) {
  if (!tokens[u]) tokens[u] = (await h.post('/api/admin/login', { username: u, password: PASS })).body.token;
  return tokens[u];
}
const capture = (ch, r, m) => { sent.push({ ch, adminId: r.admin_id, kind: m.kind, level: m.level, alertId: m.alertId }); return { ok: true, detail: 'test' }; };
const flag = (on) => live.set('ops', 'ops.critical_alerts', on, 'test');

async function clean() {
  sent.length = 0;
  for (const tb of ['ops_alert_page_log', 'ops_alert_pages', 'ops_alerts', 'server_errors', 'otp_outbox', 'login_requests']) await db.query(`DELETE FROM ${tb}`);
}

let seq = 0;
async function mkAlert({ key = `k${++seq}`, t, severity = 'critical', status = 'open' }) {
  const id = db.newId('alr');
  await db.query(
    `INSERT INTO ops_alerts (id, rule, target, dedup_key, severity, status, title, detail, first_seen_at, last_seen_at, live_since)
     VALUES ($1,'critical_health',$2,$3,$4,$5,$6,'{}'::jsonb,$7,$7,$7)`,
    [id, key, `critical_health|${key}`, severity, status, `آزمونِ ${key}`, t]
  );
  return id;
}
const setStatus = (id, fields) => {
  const cols = Object.keys(fields);
  return db.query(`UPDATE ops_alerts SET ${cols.map((k, i) => `${k}=$${i + 2}`).join(', ')} WHERE id=$1`, [id, ...cols.map((k) => fields[k])]);
};
const page = (id) => db.one('SELECT * FROM ops_alert_pages WHERE alert_id=$1', [id]);
const of = (id, kind) => sent.filter((s) => s.alertId === id && (!kind || s.kind === kind));

async function serverErrors(n, { code = '', message = 'boom', at = db.now() - 1000 } = {}) {
  for (let i = 0; i < n; i++) {
    await db.query(
      `INSERT INTO server_errors (id, fingerprint, method, route, status, code, name, message, at) VALUES ($1,'s_x','GET','/x',$2,$3,'Error',$4,$5)`,
      [db.newId('srverr'), code === 'db_busy' ? 503 : 500, code, message, at]
    );
  }
}

async function configure(body, user = 'pg_owner') {
  return h.api('PUT', '/api/admin/ops/alerts/paging', { token: await tok(user), body });
}

test.before(async () => {
  h = require('./helpers');
  await h.start();
  db = require('../src/db');
  paging = require('../src/lib/ops-paging');
  alerts = require('../src/lib/ops-alerts');
  live = require('../src/lib/live-config');
  jobBeat = require('../src/lib/job-beat');
  const pw = require('../src/lib/password');
  const hash = await pw.hashPassword(PASS);
  const t = db.now();
  for (const [u, role, status] of [['pg_owner', 'superadmin', 'active'], ['pg_admin', 'admin', 'active'], ['pg_view', 'viewer', 'active'],
    ['pg_fin', 'finance', 'active'], ['pg_off', 'admin', 'disabled']]) {
    ids[u] = db.newId('adm');
    await db.query(`INSERT INTO admins (id, username, name, password_hash, role, status, created_at) VALUES ($1,$2,$2,$3,$4,$5,$6)`,
      [ids[u], u, hash, role, status, t]);
  }
  paging._setTransport(capture);
});
test.after(async () => { paging._setTransport(null); paging.stop(); await h.stop(); });

/* ── گیرنده: فقط مدیرِ پلتفرم ─────────────────────────────────────────── */

test('گیرنده فقط مدیرِ فعالِ پلتفرم: viewer/finance/غیرفعال/کاربرِ دکان/ناشناخته ⇒ ۴۰۰؛ مدیرِ غیرِارشد ⇒ ۴۰۳؛ ایمیل جز برای ارشد پوشیده', async () => {
  const shopUser = await h.newUser('دکان‌دار', 'shop');
  for (const bad of [ids.pg_view, ids.pg_fin, ids.pg_off, shopUser.user?.id || shopUser.id || 'usr_x', 'adm_nobody']) {
    const r = await configure({ recipients: [{ adminId: bad, tier: 'primary', email: 'x@ex.com' }] });
    assert.equal(r.status, 400, `${bad} ${JSON.stringify(r.body)}`);
    assert.equal(r.body.error.code, 'recipient_not_platform_admin');
  }
  assert.equal((await configure({ recipients: [{ adminId: ids.pg_admin, tier: 'escalation' }] })).body.error.code, 'primary_required');
  assert.equal((await configure({ recipients: [{ adminId: ids.pg_owner, email: 'not-an-email' }] })).body.error.code, 'bad_email');
  assert.equal((await configure({})).status, 400);
  assert.equal((await configure({ recipients: [{ adminId: ids.pg_admin }] }, 'pg_admin')).status, 403);
  assert.equal((await db.one('SELECT COUNT(*)::int AS n FROM ops_alert_recipients')).n, 0, 'درخواستِ ردشده چیزی ننوشت');

  const ok = await configure({ recipients: [{ adminId: ids.pg_owner, tier: 'primary', email: 'Owner@Example.com' },
    { adminId: ids.pg_admin, tier: 'escalation', email: 'ops@example.com' }], escalateMin: 15, maxEscalations: 2 });
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.equal(ok.body.recipients.find((r) => r.adminId === ids.pg_owner).email, 'owner@example.com');
  const view = await h.get('/api/admin/ops/alerts/paging', { token: await tok('pg_view') });
  assert.equal(view.status, 200);
  assert.equal(view.body.enabled, false);
  assert.equal(view.body.recipients.find((r) => r.adminId === ids.pg_owner).email, 'o***@example.com');
  assert.deepEqual(view.body.settings, { escalateMin: 15, maxEscalations: 2 });
  assert.equal((await db.many("SELECT * FROM audit_logs WHERE action='admin.ops_paging_updated'")).length, 1);
});

/* ── پرچمِ خاموش ─────────────────────────────────────────────────────── */

test('پرچمِ خاموش ⇒ همان پیشین: هیچ ارسال و ردیف، critical_health «off»، کارِ شکست‌خورده high', async () => {
  await clean();
  const t = db.now();
  const id = await mkAlert({ t });
  assert.deepEqual(await paging.run({ t }), { state: 'off' });
  assert.equal(sent.length, 0);
  assert.equal((await db.one('SELECT COUNT(*)::int AS n FROM ops_alert_pages')).n, 0);
  await serverErrors(20, { code: 'db_busy' });
  const rep = await alerts.evaluate({ force: true });
  assert.equal(rep.rules.find((r) => r.id === 'critical_health').state, 'off');
  assert.equal(rep.paging, undefined);
  assert.equal((await db.many("SELECT * FROM ops_alerts WHERE rule='critical_health' AND id <> $1", [id])).length, 0);
  jobBeat._reset();
  await jobBeat.wrap('login-outbox', MIN, async () => { throw Object.assign(new Error('x'), { code: 'EFAIL' }); })().catch(() => null);
  const jobs = (await alerts._finders.service_down(Date.now())).filter((f) => f.target === 'job.login-outbox');
  assert.equal(jobs[0].severity, 'high');
  jobBeat._reset();
  assert.equal(sent.length, 0);
});

/* ── سنجشِ هر شرط سرِ آستانه ──────────────────────────────────────────── */

test('سنجش سرِ آستانه: db_busy ۹⇒نه/۱۰⇒بله، ۵xx ۹۹⇒نه/۱۰۰⇒بله، بیرون از پنجره شمرده نمی‌شود', async () => {
  await clean();
  await flag(true);
  const t = db.now();
  const targets = async () => (await paging.criticalFindings(t)).map((f) => f.target);
  await serverErrors(200, { code: 'db_busy', at: t - paging.CRIT.windowMs - 1000 });   //  کهنه
  assert.deepEqual(await targets(), []);
  await serverErrors(5, { code: 'db_busy' });
  await serverErrors(4, { message: 'timeout exceeded when trying to connect' });
  assert.ok(!(await targets()).includes('db.busy'));
  await serverErrors(1, { code: 'db_busy' });
  const f = (await paging.criticalFindings(t)).find((x) => x.target === 'db.busy');
  assert.equal(f.severity, 'critical');
  assert.equal(f.detail.busy, paging.CRIT.dbBusyMin);
  await serverErrors(paging.CRIT.errors5xxMin - 11);
  assert.ok(!(await targets()).includes('errors.5xx'), 'یکی کمتر از آستانه');
  await serverErrors(1);
  assert.ok((await targets()).includes('errors.5xx'));
});

test('سنجش سرِ آستانه: صفِ ایمیلِ ۲۹ دقیقه⇒نه/۳۱⇒بله، تطبیقِ پرداخت ۲ شکست⇒نه/۳⇒بله و موفقیت صفرش می‌کند', async () => {
  await clean();
  const add = async (ageMs) => {
    const id = db.newId('lrq'); const at = Date.now() - ageMs;
    await db.query(`INSERT INTO login_requests (request_id, app, email, code_hash, created_at, expires_at) VALUES ($1,'shop','q@ex.com','h',$2,$3)`, [id, at, at + 600_000]);
    await db.query(`INSERT INTO otp_outbox (id, app, email, created_at, updated_at) VALUES ($1,'shop','q@ex.com',$2,$2)`, [id, at]);
  };
  const targets = async () => (await paging.criticalFindings(db.now())).map((f) => f.target);
  await add(29 * MIN);
  assert.ok(!(await targets()).includes('queue.email'));
  await add(31 * MIN);
  assert.ok((await targets()).includes('queue.email'));

  const rec = require('../src/lib/payment-reconciler');
  rec.noteReconcile(null);
  rec.noteReconcile({ name: 'TypeError', code: 'ETIMEDOUT' });
  rec.noteReconcile({ name: 'ReconcileErrors', code: '4' });
  assert.ok(!(await targets()).includes('payments.reconcile'));
  rec.noteReconcile({ name: 'TypeError', code: 'ETIMEDOUT' });
  const f = (await paging.criticalFindings(db.now())).find((x) => x.target === 'payments.reconcile');
  assert.equal(f.detail.streak, 3);
  assert.equal(f.detail.lastError, 'TypeError:ETIMEDOUT');
  rec.noteReconcile(null);
  assert.ok(!(await targets()).includes('payments.reconcile'));
});

test('سنجش: شکستِ پیاپیِ تطبیق میانِ نمونه‌ها و پس از راه‌اندازیِ دوباره گم نمی‌شود (شمارش در پایگاه)', async () => {
  //  «نمونهٔ دیگر» یا «همین سرور پس از راه‌اندازیِ دوباره» = نسخهٔ تازهٔ همان پرونده با حافظهٔ صفر، روی همان پایگاه
  const file = require.resolve('../src/lib/payment-reconciler');
  const here = require(file);
  await here.noteReconcile(null);
  const mine = require.cache[file];
  delete require.cache[file];
  const other = require(file);
  require.cache[file] = mine;
  assert.notEqual(other, here);
  for (let i = 0; i < 2; i++) await other.noteReconcile({ name: 'TypeError', code: 'ETIMEDOUT' });
  //  دو نوبت در نمونهٔ دیگر، نوبتِ سوم پس از «راه‌اندازیِ دوباره» (باز نسخهٔ تازه)
  delete require.cache[file];
  const restarted = require(file);
  require.cache[file] = mine;
  await restarted.noteReconcile({ name: 'ReconcileErrors', code: '2' });
  assert.equal(here.reconcileHealth().streak, 0, 'حافظهٔ نمونهٔ پیجر هیچ شکستی ندیده');
  const f = (await paging.criticalFindings(db.now())).find((x) => x.target === 'payments.reconcile');
  assert.ok(f, '⛔ سه شکستِ پیاپی در نمونه‌های دیگر ⇒ هشدارِ بحرانی در پیجر');
  assert.deepEqual([f.detail.streak, f.detail.lastError], [3, 'ReconcileErrors:2']);
  await other.noteReconcile(null);
  assert.ok(!(await paging.criticalFindings(db.now())).some((x) => x.target === 'payments.reconcile'), 'نوبتِ موفقِ هر نمونه شمارش را صفر می‌کند');
  const row = await db.one(`SELECT value FROM app_config WHERE key = 'ops:payments.reconcile.health'`);
  assert.ok(!/ETIMEDOUT|stack|at /.test(row.value.replace('"lastError":""', '')), 'فقط عدد و زمان، بی متنِ خطا');
});

test('سنجش: کارِ حیاتیِ شکست‌خورده یا از مهلت‌گذشته با پرچمِ روشن critical، کارِ دیگر همان high', async () => {
  jobBeat._reset();
  await jobBeat.wrap('order-notify', MIN, async () => { throw new Error('x'); })().catch(() => null);
  await jobBeat.wrap('housekeeping', MIN, async () => { throw new Error('x'); })().catch(() => null);
  jobBeat.register('order-timeout', MIN);   //  هرگز اجرا نشده ⇒ پس از ۲×فاصله+۶۰ث «دیر»
  const sev = async (t) => Object.fromEntries((await alerts._finders.service_down(t)).filter((f) => f.target.startsWith('job.')).map((f) => [f.target, f.severity]));
  const now0 = await sev(Date.now());
  assert.equal(now0['job.order-notify'], 'critical');
  assert.equal(now0['job.housekeeping'], 'high');
  assert.equal(now0['job.order-timeout'], undefined, 'هنوز در مهلت');
  assert.equal((await sev(Date.now() + 3 * MIN + 1000))['job.order-timeout'], 'critical');
  jobBeat._reset();
});

/* ── یک خبر برای هر رخداد ────────────────────────────────────────────── */

test('یک خبر برای هر رخداد: دو دور، راه‌اندازیِ دوباره و دو نمونهٔ هم‌زمان دوباره نمی‌فرستند', async () => {
  await clean();
  await flag(true);
  const t0 = db.now();
  const a = await mkAlert({ t: t0 });
  const r1 = await paging.run({ t: t0 });
  assert.equal(r1.state, 'ok');
  assert.deepEqual(of(a).map((s) => `${s.kind}:${s.ch}:${s.adminId}`).sort(), [`alert:email:${ids.pg_owner}`, `alert:push:${ids.pg_owner}`]);
  await paging.run({ t: t0 + MIN });
  assert.equal(of(a).length, 2, 'دورِ دوم');

  //  «راه‌اندازیِ دوباره»: ماژولِ تازه با حافظهٔ خالی — وضعیت از پایگاه است
  const path = require.resolve('../src/lib/ops-paging');
  delete require.cache[path];
  const fresh = require('../src/lib/ops-paging');
  fresh._setTransport(capture);
  await fresh.run({ t: t0 + 2 * MIN });
  assert.equal(of(a).length, 2, 'پس از راه‌اندازیِ دوباره');

  //  دو نمونهٔ هم‌زمان روی رخدادِ تازه ⇒ یک خبر (قفل + کلیدِ یکتا)
  delete require.cache[path];
  const other = require('../src/lib/ops-paging');
  other._setTransport(capture);
  const b = await mkAlert({ t: t0 + 3 * MIN });
  await Promise.all([fresh.run({ t: t0 + 3 * MIN }), other.run({ t: t0 + 3 * MIN })]);
  assert.equal(of(b, 'alert').length, 2, JSON.stringify(of(b)));
  assert.equal((await page(b)).sends, 1);
  delete require.cache[path];
  paging = require('../src/lib/ops-paging');
  paging._setTransport(capture);
  const log = await db.many('SELECT * FROM ops_alert_page_log WHERE alert_id=$1', [a]);
  assert.equal(log.length, 2);
  assert.ok(log.every((l) => l.ok === true && l.kind === 'alert'));
});

test('بی سیل: همان شرط پس از بسته شدن در کمتر از یک ساعت دوباره خبر نمی‌دهد؛ پس از مهلت، اگر هنوز باز است، می‌دهد', async () => {
  await clean();
  const t0 = db.now();
  const a = await mkAlert({ key: 'flood', t: t0 });
  await paging.run({ t: t0 });
  await setStatus(a, { status: 'resolved', resolution: 'manual', resolved_at: t0 + MIN });
  await paging.run({ t: t0 + 2 * MIN });
  assert.equal(of(a, 'resolved').length, 2, 'ایمیل و پوشِ گیرندهٔ اصلی');
  const b = await mkAlert({ key: 'flood', t: t0 + 5 * MIN });
  await paging.run({ t: t0 + 5 * MIN });
  assert.equal(of(b).length, 0);
  assert.equal((await page(b)).state, 'suppressed');
  await paging.run({ t: t0 + 59 * MIN });
  assert.equal(of(b).length, 0);
  await paging.run({ t: t0 + 61 * MIN });
  assert.equal(of(b, 'alert').length, 2);
  assert.equal((await page(b)).state, 'sent');
});

/* ── پیگیری، «دیدم»، «بسته شد» ───────────────────────────────────────── */

test('پیگیری سرِ ۱۵ دقیقه به اصلی و پیگیری، حداکثر ۲ بار', async () => {
  await clean();
  const t0 = db.now();
  const a = await mkAlert({ t: t0 });
  await paging.run({ t: t0 });
  await paging.run({ t: t0 + 14 * MIN });
  assert.equal(of(a, 'escalation').length, 0, '۱۴ دقیقه');
  await paging.run({ t: t0 + 15 * MIN });
  const e1 = of(a, 'escalation');
  assert.deepEqual([...new Set(e1.map((s) => s.adminId))].sort(), [ids.pg_owner, ids.pg_admin].sort());
  assert.ok(e1.every((s) => s.level === 1));
  await paging.run({ t: t0 + 29 * MIN });
  assert.equal(of(a, 'escalation').length, 4);
  await paging.run({ t: t0 + 30 * MIN });
  assert.equal(of(a, 'escalation').filter((s) => s.level === 2).length, 4);
  await paging.run({ t: t0 + 120 * MIN });
  assert.equal(of(a, 'escalation').length, 8, 'سقفِ ۲');
  assert.equal((await page(a)).next_at, null);
});

test('«دیدم» از پنل پیگیری را می‌ایستاند؛ «بسته شد» ِ دستی یک بار به همه', async () => {
  await clean();
  const t0 = db.now();
  const a = await mkAlert({ t: t0 });
  await paging.run({ t: t0 });
  const ack = await h.post(`/api/admin/ops/alerts/${a}/ack`, { note: 'دیدم' }, { token: await tok('pg_admin') });
  assert.equal(ack.status, 200, JSON.stringify(ack.body));
  await paging.run({ t: t0 + 20 * MIN });
  await paging.run({ t: t0 + 40 * MIN });
  assert.equal(of(a, 'escalation').length, 0);
  assert.equal((await page(a)).state, 'acked');
  const res = await h.post(`/api/admin/ops/alerts/${a}/resolve`, { reason: 'دیسک آزاد شد' }, { token: await tok('pg_admin') });
  assert.equal(res.status, 200);
  await paging.run({ t: t0 + 41 * MIN });
  await paging.run({ t: t0 + 42 * MIN });
  assert.equal(of(a, 'resolved').length, 2, 'ایمیل و پوشِ گیرندهٔ اصلی، یک بار');
  assert.equal((await page(a)).state, 'resolved_sent');
});

test('بستنِ خودکار: «بسته شد» فقط پس از ۱۰ دقیقه آرامش؛ پس از پیگیری به همه؛ پس‌زده بی‌صدا', async () => {
  await clean();
  const t0 = db.now();
  const a = await mkAlert({ t: t0 });
  await paging.run({ t: t0 });
  await paging.run({ t: t0 + 15 * MIN });
  await setStatus(a, { status: 'resolved', resolution: 'auto', resolved_at: t0 + 16 * MIN });
  await paging.run({ t: t0 + 25 * MIN });
  assert.equal(of(a, 'resolved').length, 0, 'هنوز در آرامش');
  await paging.run({ t: t0 + 26 * MIN });
  assert.deepEqual([...new Set(of(a, 'resolved').map((s) => s.adminId))].sort(), [ids.pg_owner, ids.pg_admin].sort());

  const b = await mkAlert({ key: 'quiet', t: t0 });
  await paging.run({ t: t0 });
  await setStatus(b, { status: 'resolved', resolution: 'auto', resolved_at: t0 + MIN });
  const c = await mkAlert({ key: 'quiet', t: t0 + 2 * MIN });
  await paging.run({ t: t0 + 2 * MIN });
  assert.equal((await page(c)).state, 'suppressed');
  await setStatus(c, { status: 'resolved', resolution: 'auto', resolved_at: t0 + 3 * MIN });
  await paging.run({ t: t0 + 30 * MIN });
  assert.equal((await page(c)).state, 'closed');
  assert.equal(of(c).length, 0);
});

test('هنگامِ فرستادن هم: مدیرِ غیرفعال‌شده یا تنزل‌یافته خبر نمی‌گیرد؛ بی گیرندهٔ اصلی هیچ ردیفی نوشته نمی‌شود', async () => {
  await clean();
  const t0 = db.now();
  await db.query("UPDATE admins SET role='viewer' WHERE id=$1", [ids.pg_admin]);
  const a = await mkAlert({ t: t0 });
  await paging.run({ t: t0 });
  await paging.run({ t: t0 + 15 * MIN });
  assert.ok(of(a).length > 0 && of(a).every((s) => s.adminId === ids.pg_owner), JSON.stringify(of(a)));
  await db.query("UPDATE admins SET role='admin' WHERE id=$1", [ids.pg_admin]);

  await db.query("UPDATE admins SET status='disabled' WHERE id=$1", [ids.pg_owner]);
  const b = await mkAlert({ t: t0 + MIN });
  const r = await paging.run({ t: t0 + MIN });
  assert.equal(r.primary, 0);
  assert.equal(r.waiting, 1);
  assert.equal(await page(b), null, 'بی گیرنده ⇒ رخداد منتظر می‌ماند، «فرستاده» نمی‌شود');
  await db.query("UPDATE admins SET status='active' WHERE id=$1", [ids.pg_owner]);
  await paging.run({ t: t0 + 2 * MIN });
  assert.equal(of(b, 'alert').length, 2);
});

test('سرتاسر: ۱۰ پس‌زدنِ db_busy ⇒ هشدارِ critical_health ⇒ یک خبر؛ ارزیابیِ دوباره خبرِ تازه نمی‌سازد', async () => {
  await clean();
  await serverErrors(paging.CRIT.dbBusyMin, { code: 'db_busy' });
  const rep = await alerts.evaluate({ force: true });
  assert.equal(rep.rules.find((r) => r.id === 'critical_health').state, 'ok');
  assert.equal(rep.paging.state, 'ok', JSON.stringify(rep.paging));
  const row = await db.one("SELECT * FROM ops_alerts WHERE rule='critical_health' AND target='db.busy'");
  assert.equal(row.severity, 'critical');
  assert.equal(of(row.id, 'alert').length, 2);
  await alerts.evaluate({ force: true });
  assert.equal(of(row.id, 'alert').length, 2);
  await flag(false);
  assert.deepEqual(await paging.run(), { state: 'off' });
});
