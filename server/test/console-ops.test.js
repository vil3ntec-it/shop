'use strict';
/**
 * پنلِ مرکزیِ مالک — ماژول‌های عملیات (۴.۸ نسخه‌ها، ۴.۹ سلامت، ۴.۱۰ رخدادها، ۴.۱۱ هشدارها، ۴.۱۳ یکپارچه‌سازی).
 * `routes/admin-ops.js` ⇐ `lib/ops-overview.js` · `lib/ops-errors.js` · `lib/ops-alerts.js` · `lib/job-beat.js`
 *
 *   • مرزِ نقش: viewer می‌خواند و نمی‌نویسد؛ finance به این درها نمی‌رسد؛ بی‌توکن ۴۰۱
 *   • ۵xx ِ سرور با «کدِ پیگیری» ثبت و از راز پوشانده می‌شود (رازِ config، نشانیِ دارای رمز، ایمیل)
 *   • خطای تکراری = یک رخداد با شمارنده؛ پالایشِ دکان فقط رخدادهای همان دکان (جداییِ tenant)
 *   • چرخهٔ رخداد با سابقه و تاریخچه؛ نسخهٔ کهنه ⇒ ۴۰۹؛ «حل شد» + وقوعِ تازه ⇒ بازگشایی
 *   • یادداشت با کلیدِ یکتایی یک بار
 *   • هشدار: یکی شدنِ تکرار، سقفِ سیل + «و N مورد دیگر»، بستنِ خودکار فقط پس از اجرای موفق، بی چشمک،
 *     تأیید/بستن با سابقه و دلیل
 *   • سلامت: تعریفِ حالت‌ها، کارِ راه‌نیفتاده «نامعلوم» نه سبز، کارِ شکست‌خورده «هشدار»
 *   • یکپارچه‌سازی و نسخه‌ها: هیچ مقدارِ رازی در پاسخ؛ انتشارِ مرحله‌ای بی OPS-01 «پشتیبانی نمی‌شود» و
 *     «برگشت» هرگز وانمود نمی‌شود
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const os = require('node:os');
const fs = require('node:fs');
const path = require('node:path');

let fake;
let h;
let db;
const PASS = 'Admin!12345';

test.before(async () => {
  //  GitHubِ ساختگی برای انتشارها: پایدار 3.2.50، آزمایشی نیست (۴۰۴)
  fake = http.createServer((req, res) => {
    if (req.url === '/repos/o/r/releases/tags/tohid-native') {
      res.setHeader('Content-Type', 'application/json');
      return res.end(JSON.stringify({ body: 'یادداشتِ انتشار', published_at: '2026-10-18T00:00:00Z', assets: [
        { id: 1, name: 'Tohid-Native-3.2.50.apk', size: 3, updated_at: '2026-10-18T00:00:00Z' },
        { id: 2, name: 'version.txt', size: 7, updated_at: '2026-10-18T00:00:00Z' },
      ] }));
    }
    if (req.url === '/repos/o/r/releases/assets/2') return res.end('3.2.50\n');
    res.statusCode = 404; res.end('{}');
  });
  await new Promise((r) => fake.listen(0, '127.0.0.1', r));
  process.env.RELEASE_API_BASE = `http://127.0.0.1:${fake.address().port}`;
  process.env.RELEASE_REPO = 'o/r';
  process.env.RELEASE_CACHE_PATH = fs.mkdtempSync(path.join(os.tmpdir(), 'ops-rel-'));
  h = require('./helpers');
  await h.start();
  db = require('../src/db');
  const pw = require('../src/lib/password');
  const hash = await pw.hashPassword(PASS);
  const t = db.now();
  for (const [u, role] of [['ops_super', 'superadmin'], ['ops_admin', 'admin'], ['ops_view', 'viewer'], ['ops_fin', 'finance']]) {
    await db.query(
      `INSERT INTO admins (id, username, name, password_hash, role, status, created_at) VALUES ($1,$2,$2,$3,$4,'active',$5)`,
      [db.newId('adm'), u, hash, role, t]
    );
  }
});
test.after(async () => { await h.stop(); fake.close(); });

const tokens = {};
async function tok(u) {
  if (!tokens[u]) tokens[u] = (await h.post('/api/admin/login', { username: u, password: PASS })).body.token;
  return tokens[u];
}
const audits = (action) => db.many('SELECT * FROM audit_logs WHERE action=$1 ORDER BY created_at', [action]);
const forceIngest = () => require('../src/lib/ops-errors').ingest({ force: true });

async function shopOwner(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { ...u, shopId: made.body.shop.id };
}

async function clientError({ message, app = 'shop', shopId = '', version = '2.14.5', device = 'dev-1', at = db.now() }) {
  await db.query(
    `INSERT INTO client_errors (id, app, account_kind, account_id, device_id, app_version, message, stack, at, created_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$9)`,
    [db.newId('cer'), app, shopId ? 'shop' : '', shopId, device, version, message, 'at ir.vil3ntec.tohid.Pos.save(Pos.kt:42)', at]
  );
}

/* ── ۱) مرز ─────────────────────────────────────────────────────────── */

test('capabilities: ops.v1 اعلام می‌شود؛ release-stage.v1 فقط اگر OPS-01 در همین نسخه هست', async () => {
  const me = await h.get('/api/admin/me', { token: await tok('ops_view') });
  assert.equal(me.status, 200);
  assert.ok(me.body.capabilities.includes('ops.v1'));
  let hasStage = true;
  try { require.resolve('../src/lib/release-stage'); } catch { hasStage = false; }
  assert.equal(me.body.capabilities.includes('release-stage.v1'), hasStage);
});

test('نقش: viewer می‌خواند و نمی‌نویسد؛ finance هیچ؛ بی‌توکن ۴۰۱', async () => {
  for (const p of ['/api/admin/ops/health', '/api/admin/ops/integrations', '/api/admin/ops/incidents', '/api/admin/ops/alerts', '/api/admin/ops/releases']) {
    assert.equal((await h.get(p, {})).status, 401, p);
    assert.equal((await h.get(p, { token: await tok('ops_view') })).status, 200, `viewer ${p}`);
    assert.equal((await h.get(p, { token: await tok('ops_fin') })).status, 403, `finance ${p}`);
  }
  const w = await h.post('/api/admin/ops/alerts/evaluate', {}, { token: await tok('ops_view') });
  assert.equal(w.status, 403);
  assert.equal(w.body.error.code, 'role_forbidden');
  const n = await h.post('/api/admin/ops/incidents/inc_x/notes', { note: 'سلام' }, { token: await tok('ops_view') });
  assert.equal(n.status, 403);
});

/* ── ۲) خطای سرور و رخداد ──────────────────────────────────────────── */

test('۵xx ِ سرور: با کدِ پیگیری ثبت می‌شود و هیچ رازی در آن نیست', async () => {
  const errs = require('../src/lib/ops-errors');
  const config = require('../src/config');
  const secret = config.secrets.api;
  const err = new Error(`boom ${secret} user a.b@c.af postgres://shop:hunter22@db:5432/x token abcdefabcdefabcdefabcdefabcdefabcdef12`);
  err.code = '57P01';
  errs.recordServer({ ref: 'eopstest1', err, req: { method: 'GET', originalUrl: '/api/admin/shops/shp_0123456789abcdef?x=1' }, status: 500 });
  errs.recordServer({ ref: 'eopstest2', err, req: { method: 'GET', originalUrl: '/api/admin/shops/shp_fedcba9876543210?x=2' }, status: 500 });
  await new Promise((r) => setTimeout(r, 100));
  const rows = await db.many("SELECT * FROM server_errors WHERE id IN ('eopstest1','eopstest2') ORDER BY id");
  assert.equal(rows.length, 2);
  const blob = JSON.stringify(rows);
  assert.ok(!blob.includes(secret), 'رازِ API');
  assert.ok(!blob.includes('hunter22'), 'رمزِ نشانیِ پایگاه');
  assert.ok(!blob.includes('a.b@c.af'), 'ایمیل');
  assert.ok(!blob.includes('abcdefabcdefabcdefabcdefabcdefabcdef12'), 'توکن');
  assert.equal(rows[0].fingerprint, rows[1].fingerprint, 'دو دکانِ گوناگون، همان خطا ⇒ یک اثرِ انگشت');
  assert.equal(rows[0].route.includes('shp_0123'), false, 'شناسهٔ دکان در مسیر نمی‌ماند');
  await forceIngest();
  const list = await h.get('/api/admin/ops/incidents?source=server', { token: await tok('ops_admin') });
  assert.equal(list.status, 200);
  const inc = list.body.incidents.find((i) => i.occurrences === 2 && i.source === 'server');
  assert.ok(inc, JSON.stringify(list.body.incidents));
  assert.equal(inc.priority, 'p2');
  const one = await h.get(`/api/admin/ops/incidents/${inc.id}`, { token: await tok('ops_view') });
  assert.equal(one.status, 200);
  assert.equal(one.body.occurrences.length, 2);
  assert.ok(one.body.occurrences.every((o) => o.source === 'server' && o.status === 500));
  assert.ok(!JSON.stringify(one.body).includes(secret));
});

test('یک ۵xx ِ واقعی از درِ مدیر به server_errors می‌رسد (کدِ پیگیری همان ref ِ پاسخ)', async () => {
  //  مسیرِ رخدادِ ناموجود با شناسهٔ ناجور ⇒ ۴۰۰ (نه ۵۰۰) — پس خطای واقعی را با شکستنِ موقتِ پایگاه نمی‌سازیم؛
  //  همان errorHandler را مستقیم صدا می‌زنیم، با همان شکلِ req ِ Express
  const { errorHandler } = require('../src/middleware/errors');
  const out = {};
  const res = { status(s) { out.status = s; return this; }, json(b) { out.body = b; return this; } };
  const orig = console.error;
  console.error = () => {};
  try { errorHandler(new Error('internal kaboom'), { method: 'POST', originalUrl: '/api/admin/ops/x', baseUrl: '/api/admin/ops', route: { path: '/x' } }, res, () => {}); }
  finally { console.error = orig; }
  assert.equal(out.status, 500);
  await new Promise((r) => setTimeout(r, 100));
  const row = await db.one('SELECT * FROM server_errors WHERE id=$1', [out.body.error.ref]);
  assert.ok(row, 'ردیف با همان کدِ پیگیری');
  assert.equal(row.route, '/api/admin/ops/x');
});

test('خطای تکراریِ برنامه = یک رخداد با شمارنده؛ پالایشِ دکان فقط همان دکان', async () => {
  const a = await shopOwner('رخداد الف');
  const b = await shopOwner('رخداد ب');
  await clientError({ message: 'NullPointer in sale 1234 for item 99', shopId: a.shopId, device: 'devA1' });
  await clientError({ message: 'NullPointer in sale 5678 for item 12', shopId: a.shopId, device: 'devA2' });
  await clientError({ message: 'NullPointer in sale 42 for item 7', shopId: a.shopId, device: 'devA1' });
  await clientError({ message: 'Printer offline zq', shopId: b.shopId, device: 'devB1', version: '2.14.4' });
  await forceIngest();
  const all = await h.get('/api/admin/ops/incidents?source=client&q=NullPointer', { token: await tok('ops_admin') });
  assert.equal(all.body.total, 1, 'سه پیامِ هم‌شکل ⇒ یک رخداد');
  const inc = all.body.incidents[0];
  assert.equal(inc.occurrences, 3);
  assert.equal(inc.lastVersion, '2.14.5');
  const detail = await h.get(`/api/admin/ops/incidents/${inc.id}`, { token: await tok('ops_admin') });
  assert.equal(detail.body.spread.shops, 1);
  assert.equal(detail.body.spread.devices, 2);
  assert.ok(detail.body.occurrences.every((o) => o.shopId === a.shopId));
  assert.ok(detail.body.occurrences.every((o) => !o.device || o.device.startsWith('…')), 'شناسهٔ کاملِ دستگاه برنمی‌گردد');

  const onlyB = await h.get(`/api/admin/ops/incidents?shop=${b.shopId}`, { token: await tok('ops_admin') });
  assert.equal(onlyB.body.total, 1);
  assert.match(onlyB.body.incidents[0].title, /Printer offline/);
  const onlyA = await h.get(`/api/admin/ops/incidents?shop=${a.shopId}`, { token: await tok('ops_admin') });
  assert.ok(onlyA.body.incidents.every((i) => !/Printer/.test(i.title)), '⛔ رخدادِ دکانِ ب در پالایشِ دکانِ الف');
  const byVersion = await h.get('/api/admin/ops/incidents?version=2.14.4', { token: await tok('ops_admin') });
  assert.ok(byVersion.body.incidents.some((i) => /Printer/.test(i.title)));
  assert.ok(byVersion.body.incidents.every((i) => !/NullPointer/.test(i.title)));
  //  بازخوانیِ دوباره هیچ چیزی را دو بار نمی‌شمارد
  await forceIngest();
  const again = await h.get(`/api/admin/ops/incidents/${inc.id}`, { token: await tok('ops_admin') });
  assert.equal(again.body.incident.occurrences, 3);
});

test('چرخهٔ رخداد: وضعیت با سابقه، نسخهٔ کهنه ۴۰۹، «حل شد» + وقوعِ تازه ⇒ بازگشایی', async () => {
  await clientError({ message: 'Lifecycle crash qq', version: '2.14.5' });
  await forceIngest();
  const inc = (await h.get('/api/admin/ops/incidents?q=Lifecycle', { token: await tok('ops_admin') })).body.incidents[0];
  const bad = await h.post(`/api/admin/ops/incidents/${inc.id}/status`, { status: 'closed' }, { token: await tok('ops_admin') });
  assert.equal(bad.status, 400);
  const stale = await h.post(`/api/admin/ops/incidents/${inc.id}/status`, { status: 'investigating', expectedVersion: inc.version + 5 }, { token: await tok('ops_admin') });
  assert.equal(stale.status, 409);
  assert.equal(stale.body.error.code, 'incident_changed');
  const inv = await h.post(`/api/admin/ops/incidents/${inc.id}/status`, { status: 'investigating', note: 'دارم می‌بینم', expectedVersion: inc.version }, { token: await tok('ops_admin') });
  assert.equal(inv.status, 200);
  assert.equal(inv.body.changed, true);
  const same = await h.post(`/api/admin/ops/incidents/${inc.id}/status`, { status: 'investigating' }, { token: await tok('ops_admin') });
  assert.equal(same.body.changed, false, 'همان وضعیت ⇒ بی تغییر و بی ردیفِ دوم');
  const res = await h.post(`/api/admin/ops/incidents/${inc.id}/status`, { status: 'resolved' }, { token: await tok('ops_admin') });
  assert.equal(res.body.incident.status, 'resolved');
  const rows = await audits('admin.ops_incident_status');
  assert.equal(rows.filter((r) => r.target_id === inc.id).length, 2);

  await new Promise((r) => setTimeout(r, 5));
  await clientError({ message: 'Lifecycle crash qq', version: '2.14.6' });
  await forceIngest();
  const back = await h.get(`/api/admin/ops/incidents/${inc.id}`, { token: await tok('ops_admin') });
  assert.equal(back.body.incident.status, 'open', 'وقوعِ تازه پس از «حل شد» ⇒ باز');
  assert.equal(back.body.incident.lastVersion, '2.14.6');
  assert.ok(back.body.events.some((e) => e.kind === 'reopened'));
  assert.deepEqual(back.body.events.filter((e) => e.kind === 'status').map((e) => e.to).sort(), ['investigating', 'resolved']);
});

test('یادداشت با کلیدِ یکتایی یک بار؛ پیوندِ پشتیبانی فقط به گفت‌وگوی واقعی؛ اولویت با سابقه', async () => {
  await clientError({ message: 'Note target zz' });
  await forceIngest();
  const inc = (await h.get('/api/admin/ops/incidents?q=Note%20target', { token: await tok('ops_admin') })).body.incidents[0];
  const headers = { 'Idempotency-Key': 'ops-note-0001' };
  const n1 = await h.post(`/api/admin/ops/incidents/${inc.id}/notes`, { note: 'تماس با دکان؛ token abcdefabcdefabcdefabcdefabcdefabcd99' }, { token: await tok('ops_admin'), headers });
  const n2 = await h.post(`/api/admin/ops/incidents/${inc.id}/notes`, { note: 'تماس با دکان؛ token abcdefabcdefabcdefabcdefabcdefabcd99' }, { token: await tok('ops_admin'), headers });
  assert.equal(n1.status, 201);
  assert.equal(n2.status, 200);
  assert.equal(n2.headers.get('idempotent-replayed'), 'true');
  const d = await h.get(`/api/admin/ops/incidents/${inc.id}`, { token: await tok('ops_admin') });
  const notes = d.body.events.filter((e) => e.kind === 'note');
  assert.equal(notes.length, 1, 'دوبار زدن ⇒ یک یادداشت');
  assert.ok(!notes[0].note.includes('abcdefabcdefabcdefabcdefabcdefabcd99'), 'توکن در یادداشت پوشانده می‌شود');

  const missing = await h.post(`/api/admin/ops/incidents/${inc.id}/link`, { supportThreadId: 'thr_nist' }, { token: await tok('ops_admin') });
  assert.equal(missing.status, 404);
  const thread = db.newId('thr');
  await db.query(`INSERT INTO support_threads (id, app, subject, status, created_at, updated_at) VALUES ($1,'shop','چاپگر','open',$2,$2)`, [thread, db.now()]);
  const linked = await h.post(`/api/admin/ops/incidents/${inc.id}/link`, { supportThreadId: thread }, { token: await tok('ops_admin') });
  assert.equal(linked.status, 200);
  const d2 = await h.get(`/api/admin/ops/incidents/${inc.id}`, { token: await tok('ops_admin') });
  assert.equal(d2.body.support.id, thread);
  assert.equal(d2.body.support.subject, 'چاپگر');

  const pr = await h.post(`/api/admin/ops/incidents/${inc.id}/priority`, { priority: 'p1', reason: 'همهٔ صندوق‌ها' }, { token: await tok('ops_admin') });
  assert.equal(pr.body.incident.priority, 'p1');
  assert.equal((await audits('admin.ops_incident_priority')).filter((r) => r.target_id === inc.id).length, 1);
});

/* ── ۳) هشدارها ─────────────────────────────────────────────────────── */

test('هشدارِ اشتراک: یکی شدنِ تکرار، تأیید و بستن با سابقه، بستنِ خودکار وقتی درست شد', async () => {
  const alerts = require('../src/lib/ops-alerts');
  alerts._reset();
  const s = await shopOwner('هشدارِ اشتراک');
  const t = db.now();
  await db.query("UPDATE subscriptions SET status='expired' WHERE shop_id=$1", [s.shopId]);
  await db.query(
    `INSERT INTO subscriptions (id, shop_id, plan, status, starts_at, ends_at, created_at, updated_at) VALUES ($1,$2,'m1','active',$3,$4,$3,$3)`,
    [db.newId('sub'), s.shopId, t - 86400e3, t + 2 * 86400e3]
  );
  const sup = await tok('ops_admin');
  const first = await h.get('/api/admin/ops/alerts?rule=subscription_expiring', { token: sup });
  assert.equal(first.status, 200);
  assert.equal(first.body.alerts.length, 1, JSON.stringify(first.body.evaluation));
  const a = first.body.alerts[0];
  assert.equal(a.rule, 'subscription_expiring');
  assert.ok(a.detail.shops.some((x) => x.id === s.shopId));
  assert.ok(first.body.definitions.some((d) => d.id === 'admin_suspicious' && d.what));

  await alerts.evaluate({ force: true });
  const again = await h.get('/api/admin/ops/alerts?rule=subscription_expiring', { token: sup });
  assert.equal(again.body.alerts.length, 1, '⛔ دیدنِ دوباره هشدارِ دوم نمی‌سازد');
  assert.equal(again.body.alerts[0].id, a.id);
  assert.ok(again.body.alerts[0].occurrences >= 2);
  assert.equal(again.body.alerts[0].liveSince, a.liveSince);

  const ack = await h.post(`/api/admin/ops/alerts/${a.id}/ack`, { note: 'دیدم' }, { token: sup });
  assert.equal(ack.body.alert.status, 'acknowledged');
  const noReason = await h.post(`/api/admin/ops/alerts/${a.id}/resolve`, {}, { token: sup });
  assert.equal(noReason.status, 400);
  assert.equal((await audits('admin.ops_alert_ack')).filter((r) => r.target_id === a.id).length, 1);

  //  شرط برطرف شد (تمدید) ⇒ ارزیابیِ بعدی خودکار می‌بندد
  await db.query("UPDATE subscriptions SET ends_at=$2 WHERE shop_id=$1 AND status='active'", [s.shopId, t + 60 * 86400e3]);
  await alerts.evaluate({ force: true });
  const closed = await h.get('/api/admin/ops/alerts?status=resolved&rule=subscription_expiring', { token: sup });
  const row = closed.body.alerts.find((x) => x.id === a.id);
  assert.ok(row, JSON.stringify(closed.body));
  assert.equal(row.resolution, 'auto');
});

test('هشدار: سقفِ سیل + «و N مورد دیگر»، بی چشمک، و قاعدهٔ شکست‌خورده چیزی را نمی‌بندد', async () => {
  const alerts = require('../src/lib/ops-alerts');
  const finders = alerts._finders;
  const orig = finders.version_incompatible;
  let findings = Array.from({ length: 30 }, (_, i) => ({ target: `app${i}`, title: `آزمونِ سیل ${i}`, detail: { i } }));
  finders.version_incompatible = async () => findings;
  try {
    const rep = await alerts.evaluate({ force: true });
    const r = rep.rules.find((x) => x.id === 'version_incompatible');
    assert.equal(r.state, 'ok');
    assert.equal(r.created, alerts.LIMITS.maxPerRule);
    assert.equal(r.suppressed, 10);
    const live = await db.many("SELECT * FROM ops_alerts WHERE rule='version_incompatible' AND status <> 'resolved'");
    assert.equal(live.length, alerts.LIMITS.maxPerRule + 1, 'بیست هشدار + یک «و ۱۰ موردِ دیگر»');
    assert.ok(live.some((x) => x.target === '*overflow'));

    //  قاعده شکست خورد ⇒ هیچ هشداری بسته نمی‌شود
    finders.version_incompatible = async () => { throw new Error('db hiccup'); };
    const rep2 = await alerts.evaluate({ force: true });
    assert.equal(rep2.rules.find((x) => x.id === 'version_incompatible').state, 'error');
    assert.equal((await db.many("SELECT id FROM ops_alerts WHERE rule='version_incompatible' AND status <> 'resolved'")).length, live.length);

    //  درست شد ⇒ همه خودکار بسته؛ برگشتِ زودِ همان هدف ⇒ همان ردیف دوباره باز، نه ردیف/اعلانِ تازه
    findings = [];
    finders.version_incompatible = async () => findings;
    await alerts.evaluate({ force: true });
    assert.equal((await db.many("SELECT id FROM ops_alerts WHERE rule='version_incompatible' AND status <> 'resolved'")).length, 0);
    const before = await db.one("SELECT id, live_since FROM ops_alerts WHERE rule='version_incompatible' AND target='app0'");
    findings = [{ target: 'app0', title: 'برگشت', detail: {} }];
    await alerts.evaluate({ force: true });
    const rows = await db.many("SELECT id, status, live_since FROM ops_alerts WHERE rule='version_incompatible' AND target='app0'");
    assert.equal(rows.length, 1, '⛔ چشمک ردیفِ تازه نمی‌سازد');
    assert.equal(rows[0].status, 'open');
    assert.equal(Number(rows[0].live_since), Number(before.live_since), 'live_since جلو نمی‌رود ⇒ اعلانِ دوباره نمی‌رود');
  } finally {
    finders.version_incompatible = orig;
    await alerts.evaluate({ force: true });
  }
});

test('قاعده‌های دادهٔ واقعی: تسویهٔ پیک با کسری، ورودِ ناموفقِ مدیر', async () => {
  const alerts = require('../src/lib/ops-alerts');
  const t = db.now();
  for (let i = 0; i < 10; i++) {
    await db.query(`INSERT INTO login_attempts (scope, identifier, ip, ok, created_at) VALUES ('admin','hacker_x','10.9.9.9',false,$1)`, [t - i * 1000]);
  }
  const s = await shopOwner('تسویهٔ کسری');
  await db.query(
    `INSERT INTO courier_settlements (id, shop_id, courier_id, settle_no, client_key, request_hash, assignments_count,
       expected_cash_minor, collected_cash_minor, collection_diff_minor, fare_total_minor, handover_due_minor, handed_over_minor,
       received_minor, shortage_minor, excess_minor, confirmed_by, created_at)
     VALUES ($1,$2,'cr1',1,'ck-1','rh',2, 50000,50000,0, 0,50000,45000, 45000,5000,0,'u1',$3)`,
    [db.newId('cst'), s.shopId, t - 3600e3]
  );
  const rep = await alerts.evaluate({ force: true });
  const mm = await db.many("SELECT * FROM ops_alerts WHERE rule='settlement_mismatch' AND status <> 'resolved' AND target=$1", [s.shopId]);
  assert.equal(mm.length, 1, JSON.stringify(rep.rules.find((r) => r.id === 'settlement_mismatch')));
  assert.equal(mm[0].severity, 'high');
  assert.equal(Number((typeof mm[0].detail === 'string' ? JSON.parse(mm[0].detail) : mm[0].detail).shortageMinor), 5000);
  const sus = rep.rules.find((r) => r.id === 'admin_suspicious');
  assert.equal(sus.state, 'ok');
  const live = await db.many("SELECT * FROM ops_alerts WHERE rule='admin_suspicious' AND status <> 'resolved'");
  assert.ok(live.some((a) => a.target === 'user:hacker_x'), JSON.stringify(live));
  //  هر قاعده یک حالتِ صریح دارد (ok / unavailable / error)، هیچ‌کدام بی‌حالت
  assert.deepEqual(rep.rules.map((r) => r.id).sort(), Object.keys(alerts.RULES).sort());
  assert.ok(rep.rules.every((r) => ['ok', 'unavailable', 'error'].includes(r.state)), JSON.stringify(rep.rules));
});

test('evaluate دستی: فقط admin و حداکثر هر ۱۵ ثانیه', async () => {
  const alerts = require('../src/lib/ops-alerts');
  alerts._reset();
  const first = await h.post('/api/admin/ops/alerts/evaluate', {}, { token: await tok('ops_admin') });
  assert.equal(first.status, 200);
  assert.ok(Array.isArray(first.body.rules));
  const second = await h.post('/api/admin/ops/alerts/evaluate', {}, { token: await tok('ops_admin') });
  assert.equal(second.status, 429);
});

/* ── ۴) سلامت، یکپارچه‌سازی، نسخه‌ها ─────────────────────────────────── */

function secretsOfConfig() {
  const config = require('../src/config');
  return [config.secrets.api, config.secrets.otp, config.secrets.jwt, process.env.DATABASE_URL].filter((x) => x && x.length >= 8);
}

test('سلامت: تعریفِ حالت‌ها، کارِ راه‌نیفتاده «نامعلوم»، کارِ شکست‌خورده «هشدار»، بی راز', async () => {
  const beat = require('../src/lib/job-beat');
  const failing = beat.wrap('order-timeout', 30_000, async () => { const e = new Error('x'); e.code = 'ECONNRESET'; throw e; });
  await failing().catch(() => {});
  const r = await h.get('/api/admin/ops/health', { token: await tok('ops_view') });
  assert.equal(r.status, 200);
  for (const k of ['ok', 'warn', 'down', 'unknown']) assert.ok(r.body.definitions[k], k);
  const jobs = Object.fromEntries(r.body.jobs.map((j) => [j.name, j]));
  assert.equal(jobs['order-timeout'].status, 'warn');
  assert.equal(jobs['order-timeout'].lastError, 'Error:ECONNRESET');
  assert.equal(jobs['login-outbox'].status, 'unknown', 'کاری که در این پروسه راه نیفتاده سبز نیست');
  assert.ok(r.body.serverErrors.lastHour >= 2);
  assert.ok(r.body.schema.migration >= '173', r.body.schema.migration);
  assert.equal(r.body.parts.jobs, 'warn');
  const blob = JSON.stringify(r.body);
  for (const s of secretsOfConfig()) assert.ok(!blob.includes(s));
  beat._reset();
});

test('یکپارچه‌سازی: اعتبارنامه فقط «هست/نیست»، وب‌هوکِ حساب‌پی بی PAY-21 «مسدود»، بی راز', async () => {
  process.env.HESABPAY_API_KEY = 'hp-live-key-should-never-leak-123';
  try {
    const r = await h.get('/api/admin/ops/integrations', { token: await tok('ops_view') });
    assert.equal(r.status, 200);
    const blob = JSON.stringify(r.body);
    assert.ok(!blob.includes('hp-live-key-should-never-leak-123'));
    for (const s of secretsOfConfig()) assert.ok(!blob.includes(s));
    const key = r.body.credentials.find((c) => c.id === 'HESABPAY_API_KEY');
    assert.deepEqual(Object.keys(key).sort(), ['id', 'rotate', 'service', 'set']);
    assert.equal(key.set, true);
    const hook = r.body.webhooks.find((w) => w.id === 'hesabpay');
    let route = true;
    try { require.resolve('../src/routes/payments-webhook'); } catch { route = false; }
    if (!route) { assert.equal(hook.state, 'blocked'); assert.equal(hook.reason, 'route_missing'); }
    assert.ok(r.body.policies.rateLimit.windowMs > 0);
    assert.ok(r.body.providers.some((p) => p.id === 'email' && p.activity));
  } finally { delete process.env.HESABPAY_API_KEY; }
});

test('نسخه‌ها: پایدارِ منتشرشده، سه نسخهٔ جدا (برنامه/API/طرح)، مرحله‌ای بی OPS-01 «پشتیبانی نمی‌شود»، برگشت هرگز', async () => {
  await clientError({ message: 'after release err', app: 'shop', version: '3.2.50', at: Date.parse('2026-10-19T00:00:00Z') });
  const r = await h.get('/api/admin/ops/releases', { token: await tok('ops_view') });
  assert.equal(r.status, 200);
  const native = r.body.channels.find((c) => c.tag === 'tohid-native');
  assert.equal(native.platform, 'android');
  assert.equal(native.stable.state, 'published');
  assert.equal(native.stable.version, '3.2.50');
  assert.equal(native.beta.state, 'none', 'برچسبِ منتشرنشده «نیست»، نه خطا');
  assert.ok(r.body.api.version);
  assert.ok(r.body.schema.migration);
  assert.equal(r.body.apps.shop.afterRelease.version, '3.2.50');
  assert.ok(r.body.apps.shop.afterRelease.errors >= 1);
  assert.equal(r.body.stage.rollback.supported, false);
  let hasStage = true;
  try { require.resolve('../src/lib/release-stage'); } catch { hasStage = false; }
  assert.equal(r.body.stage.supported, hasStage);
  if (!hasStage) assert.equal(r.body.stage.reason, 'ops01_missing');
});
