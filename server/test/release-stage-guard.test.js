'use strict';
/**
 * OPS-01 (تصمیمِ صاحب‌کار ۱۴۰۵/۰۸/۲۳، OA §۹د) — «اول چند دکان، بعد همه؛ ایست و برگشت اگر خطا بالا رفت».
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) تصمیمِ خالصِ نگهبان (`decide`): کمتر از `minErrorDevices` ⇒ نه؛ سهمِ خطادار ≥ کف یا ≥ factor × پایه ⇒ ایست.
 *   ۲) فهرستِ دکان‌ها: درصدِ ۰ + `shops` ⇒ فقط همان دکان‌ها نسخهٔ تازه؛ بقیه و برنامهٔ قدیمی روان.
 *   ۳) پرچمِ خاموش (پیش‌فرض) ⇒ «همین حالا بسنج» فقط گزارش می‌دهد و هیچ چیز عوض نمی‌شود.
 *   ۴) پرچمِ روشن + خطای واقعی از `POST /api/errors` ⇒ ایست: همه (حتی دکانِ فهرست) برچسبِ روان؛ سابقه و هشدارِ پنل؛
 *      نگهبان روی تصمیمِ تازه‌ترِ مدیر نمی‌نشیند (compare-and-set) و خودش دوباره راه نمی‌اندازد؛ PUT ِ مدیر ادامه می‌دهد.
 *   ۵) درها: فقط مدیرِ ارشد (۴۰۳/۴۰۱)، ورودیِ ناجور ۴۰۰ و بی ذخیره، سقفِ نرخ ۴۲۹.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const os = require('node:os');
const fs = require('node:fs');
const path = require('node:path');

const stage = require('../src/lib/release-stage');

//  نگهبان `db` را می‌خواند ⇒ پس از `helpers` (که پایگاهِ آزمون را می‌گذارد) بار می‌شود
let guard;
let fake, h, root = '', plain = '';
const APK = Buffer.from('rolling-apk-3.2.50');
const APK70 = Buffer.from('staged-apk-3.2.70-bytes');

test.before(async () => {
  fake = http.createServer((req, res) => {
    const rel = (body, assets) => { res.setHeader('Content-Type', 'application/json'); res.end(JSON.stringify({ body, published_at: '2026-11-01T00:00:00Z', assets })); };
    const a = (id, name, size) => ({ id, name, size, updated_at: '2026-11-01T00:00:00Z' });
    switch (req.url) {
      case '/repos/o/r/releases/tags/tohid-native':
        return rel('روان', [a(1, 'Tohid-Native-3.2.50.apk', APK.length), a(2, 'version.txt', 7)]);
      case '/repos/o/r/releases/tags/tohid-native-v3.2.70':
        return rel('مرحله‌ای', [a(21, 'Tohid-Native-3.2.70.apk.beta', APK70.length), a(22, 'version.txt', 7)]);
      case '/repos/o/r/releases/assets/1': return res.end(APK);
      case '/repos/o/r/releases/assets/2': return res.end('3.2.50\n');
      case '/repos/o/r/releases/assets/21': return res.end(APK70);
      case '/repos/o/r/releases/assets/22': return res.end('3.2.70\n');
      default: res.statusCode = 404; return res.end('{}');
    }
  });
  await new Promise((r) => fake.listen(0, '127.0.0.1', r));
  process.env.RELEASE_API_BASE = `http://127.0.0.1:${fake.address().port}`;
  process.env.RELEASE_REPO = 'o/r';
  process.env.RELEASE_CACHE_PATH = fs.mkdtempSync(path.join(os.tmpdir(), 'rel-guard-'));
  h = require('./helpers');
  await h.start();
  guard = require('../src/lib/release-stage-guard');
  const { query, newId, now } = require('../src/db');
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'guardroot','ارشد',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Root!12345'), now()]
  );
  root = (await h.post('/api/admin/login', { username: 'guardroot', password: 'Root!12345' })).body.token;
  assert.ok(root);
  const made = await h.post('/api/admin/admins', { username: 'guardclerk', password: 'Clerk!12345', role: 'admin' }, { token: root });
  assert.ok([200, 201].includes(made.status), JSON.stringify(made.body));
  plain = (await h.post('/api/admin/login', { username: 'guardclerk', password: 'Clerk!12345' })).body.token;
  assert.ok(plain);
});
test.after(async () => { await h.stop(); fake.close(); });

const releases = () => require('../src/routes/releases');
const liveConfig = () => require('../src/lib/live-config');
async function setRaw(key, value) {
  if (value === undefined) await liveConfig().remove('release', key);
  else await liveConfig().set('release', key, value, 'test');
  releases()._forgetBeta();
  releases()._cache.clear();
}
const latest = (q) => h.api('GET', `/api/releases/tohid-native/latest${q}`);
const putStage = (body) => h.put('/api/admin/release/stage', { tag: 'tohid-native', ...body }, { token: root });
const audits = (action) => require('../src/db').many('SELECT detail FROM audit_logs WHERE action = $1 ORDER BY created_at', [action]);

/** دستگاه‌هایی که برای `3.2.70` بیرونِ سطلِ `percent` اند */
function outside(percent, n = 2) {
  const out = [];
  for (let i = 0; out.length < n && i < 10_000; i++) {
    if (stage.bucketOf('tohid-native', '3.2.70', `gd-${i}`) >= percent) out.push(`gd-${i}`);
  }
  return out;
}

/** گزارشِ خطای برنامهٔ نصبی — همان درِ عمومی و سرآیندهای همیشگیِ برنامه */
async function crash(device, version) {
  const r = await h.api('POST', '/api/errors', {
    body: { app: 'shop', message: 'IllegalStateException در فروش', stack: 'at Sale.kt:10' },
    headers: { 'X-Device': device, 'X-App-Version': version },
  });
  assert.equal(r.status, 202, JSON.stringify(r.body));
}

/* ══ ۱) منطقِ خالص ══════════════════════════════════════════════════ */

test('نگهبان: تصمیمِ خالص — حداقلِ دستگاه، کف، و ضریبِ پایه', () => {
  const cfg = guard.parseSettings({ enabled: true });
  assert.deepEqual([cfg.minErrorDevices, cfg.factor, cfg.floorPercent], [2, 2, 20]);
  const st = (devices, errorDevices) => ({ version: '3.2.70', devices, errorDevices, errors: errorDevices });
  //  یک گوشیِ خراب پخش را نمی‌ایستاند، حتی ۱۰۰٪
  assert.equal(guard.decide({ staged: st(1, 1), baseline: null }, cfg).halt, false);
  //  ۲ از ۲۰ = ۱۰٪ < کفِ ۲۰٪
  assert.equal(guard.decide({ staged: st(20, 2), baseline: null }, cfg).halt, false);
  //  ۴ از ۲۰ = ۲۰٪ ≥ کف و پایهٔ ۵٪ × ۲ = ۱۰٪
  let d = guard.decide({ staged: st(20, 4), baseline: { version: '3.2.50', devices: 100, errorDevices: 5 } }, cfg);
  assert.equal(d.halt, true);
  assert.deepEqual([d.ratioPercent, d.limitPercent, d.baselinePercent], [20, 20, 5]);
  assert.match(d.reason, /۴|4/);
  //  پایهٔ ۱۵٪ ⇒ آستانه ۳۰٪: ۲۰٪ ایست نیست (نسخهٔ پایدار هم همین‌قدر خطا دارد)
  assert.equal(guard.decide({ staged: st(20, 4), baseline: { version: '3.2.50', devices: 100, errorDevices: 15 } }, cfg).halt, false);
  //  دستگاهِ خطادار حتماً آن نسخه را دارد: ۳ خطادار و ۰ همگام‌شده ⇒ ۱۰۰٪
  d = guard.decide({ staged: st(0, 3), baseline: null }, cfg);
  assert.equal(d.halt, true);
  assert.equal(d.ratioPercent, 100);
  //  آستانه هرگز بالای ۱۰۰٪ نمی‌رود (پایهٔ ۸۰٪ × ۲)
  assert.equal(guard.decide({ staged: st(3, 3), baseline: { version: '3.2.50', devices: 10, errorDevices: 8 } }, cfg).halt, true);
});

test('نگهبان: پرچم — نبود/ناجور ⇒ خاموش؛ عددِ بیرون از بازه ⇒ پیش‌فرض', () => {
  //  مقدارِ پایگاه متنِ JSON است: «"true"» (رشته) روشن نیست، «true» هست
  for (const v of [null, undefined, false, '"true"', 1, [], {}, { enabled: 'yes' }, '{bad', 'false']) {
    assert.equal(guard.parseSettings(v).enabled, false, JSON.stringify(v));
  }
  assert.equal(guard.parseSettings(true).enabled, true);
  assert.equal(guard.parseSettings('true').enabled, true);
  assert.equal(guard.parseSettings('{"enabled":true}').enabled, true);
  const c = guard.parseSettings({ enabled: true, minErrorDevices: 0, factor: 1000, floorPercent: -5 });
  assert.deepEqual([c.minErrorDevices, c.factor, c.floorPercent], [2, 2, 20]);
  const k = guard.parseSettings({ enabled: true, minErrorDevices: 5, factor: 3, floorPercent: 10 });
  assert.deepEqual([k.minErrorDevices, k.factor, k.floorPercent], [5, 3, 10]);
});

test('منطق: فهرستِ دکان و ایست در parseStage/inStage؛ ناجور ⇒ کلِ مرحله نه', () => {
  const s = stage.parseStage({ version: '3.2.70', percent: 0, shops: ['shopA', 'shopA', 'shopB'], since: 5 });
  assert.deepEqual(s, { version: '3.2.70', percent: 0, shops: ['shopA', 'shopB'], since: 5 });
  assert.equal(stage.isActive(s), true);
  assert.equal(stage.inStage(s, 'tohid-native', '', 'shopA'), true, 'دکانِ فهرست بی شناسهٔ دستگاه هم');
  assert.equal(stage.inStage(s, 'tohid-native', 'dev-1', 'shopC'), false, 'درصدِ ۰ ⇒ بیرونِ فهرست نه');
  const halted = stage.parseStage({ ...s, halted: { at: 9, reason: 'x', by: 'auto' } });
  assert.equal(stage.inStage(halted, 'tohid-native', 'dev-1', 'shopA'), false, 'ایستاده ⇒ هیچ‌کس');
  assert.equal(stage.isActive(halted), false);
  assert.equal(stage.isActive(stage.parseStage({ version: '3.2.70', percent: 0 })), false);
  for (const bad of [
    { version: '3.2.70', percent: 5, shops: 'shopA' }, { version: '3.2.70', percent: 5, shops: ['../x'] },
    { version: '3.2.70', percent: 5, shops: Array.from({ length: stage.MAX_SHOPS + 1 }, (_, i) => `s${i}`) },
    { version: '3.2.70', percent: 5, since: -1 }, { version: '3.2.70', percent: 5, since: '5' },
    { version: '3.2.70', percent: 5, halted: true }, { version: '3.2.70', percent: 5, halted: { reason: 'بی زمان' } },
  ]) assert.equal(stage.parseStage(bad), null, JSON.stringify(bad).slice(0, 80));
});

/* ══ ۲) فهرستِ دکان‌ها از راهِ پنل ═══════════════════════════════════ */

test('پنل: «اول چند دکان» — درصدِ ۰ + فهرست ⇒ فقط همان دکان‌ها؛ فهرست با همان نسخه می‌ماند', async () => {
  let r = await putStage({ version: '3.2.70', percent: 0, shops: 'shopA, shopB', reason: 'دو دکانِ نخست' });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(r.body.stages['tohid-native'].shops, ['shopA', 'shopB']);
  const since = r.body.stages['tohid-native'].since;
  assert.ok(Number.isSafeInteger(since) && since > 0);
  const [d] = outside(10, 1);
  assert.deepEqual([(await latest(`?staged=1&shop=shopA&device=${d}`)).body.version, (await latest(`?staged=1&shop=shopA&device=${d}`)).body.channel], ['3.2.70', 'staged']);
  assert.equal((await latest(`?staged=1&shop=shopC&device=${d}`)).body.version, '3.2.50', 'دکانِ بیرونِ فهرست');
  assert.equal((await latest('?shop=shopA&device=x1')).body.version, '3.2.50', 'برنامهٔ قدیمی (بی staged) روان');
  //  بالا بردنِ درصد بی `shops` ⇒ فهرست می‌ماند؛ `since` همان
  r = await putStage({ version: '3.2.70', percent: 10 });
  assert.deepEqual(r.body.stages['tohid-native'].shops, ['shopA', 'shopB']);
  assert.equal(r.body.stages['tohid-native'].since, since);
  //  فهرستِ خالی ⇒ برداشته
  r = await putStage({ version: '3.2.70', percent: 10, shops: [] });
  assert.equal(r.body.stages['tohid-native'].shops, undefined);
  const rows = await audits('admin.release_stage_set');
  const detail = (x) => (typeof x.detail === 'string' ? JSON.parse(x.detail) : x.detail);
  assert.equal(detail(rows[0]).shops, 'shopA, shopB');
  assert.equal(detail(rows[1]).from, '3.2.70@0+2');
  await setRaw('stage.tohid-native', undefined);
});

test('پنل: فهرستِ ناجور ۴۰۰ و بی ذخیره؛ بیش از سقف ۴۰۰', async () => {
  for (const [shops, code] of [[['ok', '../x'], 'bad_release_id'], ['a b c$', 'bad_release_id'],
    [Array.from({ length: stage.MAX_SHOPS + 1 }, (_, i) => `s${i}`), 'too_many_ids']]) {
    const r = await putStage({ version: '3.2.70', percent: 0, shops });
    assert.equal(r.status, 400, JSON.stringify(shops).slice(0, 60));
    assert.equal(r.body.error.code, code);
  }
  assert.equal((await h.get('/api/admin/release/stage', { token: root })).body.stages['tohid-native'], null);
});

/* ══ ۳) ایستِ خودکار ════════════════════════════════════════════════ */

test('⛔ پرچمِ خاموش (پیش‌فرض): «بسنج» فقط گزارش می‌دهد، هیچ ایستی نیست', async () => {
  await putStage({ version: '3.2.70', percent: 100, shops: ['shopA'] });
  for (const d of ['crash-a1', 'crash-a2', 'crash-a3']) await crash(d, '3.2.70');
  const g = await h.get('/api/admin/release/stage/guard', { token: root });
  assert.equal(g.status, 200, JSON.stringify(g.body));
  assert.equal(g.body.enabled, false);
  assert.deepEqual(g.body.flag, { scope: 'release', key: 'auto_halt' });
  const m = g.body.results['tohid-native'];
  assert.equal(m.staged.errorDevices, 3);
  assert.equal(m.baseline.version, '3.2.50');
  assert.equal(m.decision.halt, true, 'سنجش می‌گوید «باید بایستد»');
  const c = await h.post('/api/admin/release/stage/guard/check', {}, { token: root });
  assert.equal(c.status, 200);
  assert.equal(c.body.results['tohid-native'].halted, false);
  assert.equal(c.body.stages['tohid-native'].halted, undefined);
  assert.equal((await latest('?staged=1&shop=shopA&device=crash-a1')).body.version, '3.2.70');
  assert.equal((await audits('admin.release_stage_halt')).length, 0);
});

test('پرچمِ روشن: خطای بالا ⇒ ایست؛ همه روان؛ سابقه و هشدار؛ خودش دوباره راه نمی‌اندازد؛ PUT ِ مدیر ادامه می‌دهد', async () => {
  let r = await h.put('/api/admin/release/stage/guard', { enabled: true, minErrorDevices: 3, reason: 'روشن' }, { token: root });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.settings.enabled, true);
  assert.equal(r.body.settings.minErrorDevices, 3);
  const set = await audits('admin.release_guard_set');
  assert.equal(set.length, 1);
  //  همان کارگرِ زمان‌بندی‌شده (`run`)
  const out = await guard.run({ act: true });
  assert.equal(out.results['tohid-native'].halted, true);
  const g = (await h.get('/api/admin/release/stage', { token: root })).body.stages['tohid-native'];
  assert.equal(g.halted.by, 'auto');
  assert.match(g.halted.reason, /3\.2\.70/);
  assert.equal(g.percent, 100, 'درصد برای دیدن می‌ماند');
  //  ایست: حتی دکانِ فهرست و درونِ ۱۰۰٪ ⇒ روان (پایدارِ پیشین)
  for (const q of ['?staged=1&shop=shopA&device=crash-a1', '?staged=1&device=any-1']) {
    const x = await latest(q);
    assert.deepEqual([x.body.tag, x.body.version], ['tohid-native', '3.2.50'], q);
  }
  //  دانلودِ نیمه‌کاره ادامه دارد (برچسبِ نسخه‌دار هنوز سرو می‌شود)
  assert.equal((await h.download('/api/releases/tohid-native-v3.2.70/file/Tohid-Native-3.2.70.apk.beta')).status, 200);
  const halts = await audits('admin.release_stage_halt');
  assert.equal(halts.length, 1);
  //  هشدارِ پنل
  const alerts = require('../src/lib/ops-alerts');
  await alerts.evaluate({ force: true });
  const list = await h.get('/api/admin/ops/alerts?rule=release_stage_halted', { token: root });
  assert.equal(list.status, 200, JSON.stringify(list.body));
  const mine = (list.body.alerts || list.body.items || []).filter((a) => a.rule === 'release_stage_halted');
  assert.equal(mine.length, 1, JSON.stringify(list.body).slice(0, 300));
  assert.equal(mine[0].target, 'tohid-native');
  //  دورِ دوم: ایستاده می‌ماند و سابقهٔ دوم نمی‌سازد
  await guard.run({ act: true });
  assert.equal((await audits('admin.release_stage_halt')).length, 1);
  //  تصمیمِ تازهٔ مدیر: ادامه با ۱۰٪ و بی فهرست ⇒ ایست برداشته، سابقهٔ «resumed»
  r = await putStage({ version: '3.2.70', percent: 10, shops: [], reason: 'رفعِ سمتِ سرور' });
  assert.equal(r.body.stages['tohid-native'].halted, undefined);
  const resumed = (await audits('admin.release_stage_set')).map((x) => (typeof x.detail === 'string' ? JSON.parse(x.detail) : x.detail)).pop();
  assert.equal(resumed.resumed, true);
  await alerts.evaluate({ force: true });
  const after = await h.get('/api/admin/ops/alerts?rule=release_stage_halted&status=open', { token: root });
  assert.equal((after.body.alerts || after.body.items || []).filter((a) => a.rule === 'release_stage_halted' && a.status === 'open').length, 0, 'خودکار بسته شد');
});

test('compare-and-set: نگهبان روی تصمیمِ تازه‌ترِ مدیر نمی‌نشیند', async () => {
  const lc = liveConfig();
  const before = await require('../src/db').one("SELECT value FROM live_config WHERE scope='release' AND key='stage.tohid-native'", []);
  //  مدیر میانِ سنجش و نوشتن درصد را عوض کرد
  await putStage({ version: '3.2.70', percent: 20 });
  const out = await lc.setIf('release', 'stage.tohid-native', before.value, { version: '3.2.70', percent: 10, halted: { at: 1, reason: 'x', by: 'auto' } }, 'auto-halt');
  assert.equal(out.changed, false);
  const now = (await h.get('/api/admin/release/stage', { token: root })).body.stages['tohid-native'];
  assert.deepEqual([now.percent, now.halted], [20, undefined]);
  //  کلیدِ نبوده ⇒ نه
  assert.equal((await lc.setIf('release', 'stage.tohid-desktop', '{}', { version: '1.0.1', percent: 1 })).changed, false);
  await setRaw('stage.tohid-native', undefined);
  await setRaw('auto_halt', undefined);
});

/* ══ ۴) درها ═══════════════════════════════════════════════════════ */

test('درهای نگهبان: فقط مدیرِ ارشد؛ ورودیِ ناجور ۴۰۰ و بی ذخیره', async () => {
  assert.equal((await h.get('/api/admin/release/stage/guard', { token: plain })).status, 403);
  assert.equal((await h.put('/api/admin/release/stage/guard', { enabled: true }, { token: plain })).status, 403);
  assert.equal((await h.post('/api/admin/release/stage/guard/check', {}, { token: plain })).status, 403);
  assert.equal((await h.api('GET', '/api/admin/release/stage/guard', {})).status, 401);
  for (const body of [{}, { enabled: 'true' }, { minErrorDevices: 0 }, { minErrorDevices: 2.5 }, { factor: 0.5 },
    { floorPercent: 101 }, { floorPercent: '20' }]) {
    const r = await h.put('/api/admin/release/stage/guard', body, { token: root });
    assert.equal(r.status, 400, JSON.stringify(body));
  }
  assert.equal((await guard.settings()).enabled, false);
  const rows = await require('../src/db').many("SELECT 1 FROM live_config WHERE scope='release' AND key='auto_halt'", []);
  assert.equal(rows.length, 0);
});

test('درهای نگهبان: سقفِ نرخ (۳۰ در ۱۰ دقیقه برای هر مدیر) ⇒ ۴۲۹', async () => {
  let last = 0;
  for (let i = 0; i < 40 && last !== 429; i++) last = (await h.get('/api/admin/release/stage/guard', { token: root })).status;
  assert.equal(last, 429);
});
