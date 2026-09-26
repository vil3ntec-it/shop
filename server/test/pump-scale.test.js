'use strict';
/**
 * ══ «کاربران پمپ ۱۰۰۰ تا ۲۰۰۰ تا هم شدن، سرور آخ نگه و حساب‌ها قاطی نشه» ══
 *
 * خواستهٔ صاحب سامانه (۱۴۰۵/۰۷/۱۴): «حساب‌های هر شخص جدا و دقیق… کاربرانِ
 * پمپ تنها ۱۰۰۰ تا ۲۰۰۰ تا هم شدن برنامه و سرور آخ نگن و همه‌چی دقیق کار
 * کنه، حساب‌ها قاطی نشه… همه رو با تستِ واقعی می‌سنجی.»
 *
 * این آزمون N پمپ‌دارِ **واقعی** می‌سازد — همان سه پلهٔ ثبت‌نام، همان
 * `POST /api/pump`، همان `device/bind` که برنامهٔ کامپیوتر می‌زند — و بعد
 * هر کدام یک ردیفِ Sync v1 با نشانهٔ خودش می‌نویسد. سپس:
 *
 *   ۱) هر پمپ از دستگاهِ دومش دقیقاً **یک** op می‌بیند و آن مالِ خودش است
 *   ۲) `/api/pump/me`ِ هر حساب پمپِ خودِ همان حساب را می‌گوید
 *   ۳) هیچ پاسخی نشانهٔ پمپِ دیگری را در خود ندارد
 *   ۴) توکنِ حسابِ A با `?account=<پمپِ B>` باز هم فقط دفترِ A را می‌گیرد
 *   ۵) دستگاهِ جداشده همان لحظه ۴۰۱ می‌گیرد و دفترِ همسایه دست‌نخورده است
 *
 * و زمانِ هر مرحله چاپ می‌شود تا با بالا رفتنِ N دیده شود **خطی** می‌ماند
 * یا نه. ⚠️ عددِ زمانی حکم نیست (ماشینِ CI نوسان دارد)؛ حکم همان
 * شمارشِ ساختاری است: ۰ نشت، N/N درست.
 *
 *   node --test test/pump-scale.test.js               (پیش‌فرض: ۲۰۰ پمپ)
 *   SCALE=2000 node --test test/pump-scale.test.js    «دو هزار مشتری»
 *
 * ⚠️ دیتابیس همان PGlite است که سرورِ حسابِ **واقعیِ** صاحب سامانه روی
 * آن می‌دود (`pglite:<dataDir>/account-server/pg`)، پس این عددها مالِ همان
 * موتورند، نه یک ساختگی.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const h = require('./helpers');

const N = Math.max(2, Number(process.env.SCALE || 200));
const PAR = Math.max(1, Number(process.env.SCALE_PAR || 25));   // هم‌زمانیِ هر موج
const ms = (n) => `${Math.round(n)}ms`;

test.before(async () => { await h.start(); });
test.after(async () => { await h.stop(); });

/** موج‌به‌موج، مثلِ N برنامه که هم‌زمان روشن‌اند — نه یکی‌یکی، نه همه با هم. */
async function waves(items, fn) {
  const out = new Array(items.length);
  for (let i = 0; i < items.length; i += PAR) {
    const slice = items.slice(i, i + PAR);
    const got = await Promise.all(slice.map((it, j) => fn(it, i + j)));
    for (let j = 0; j < got.length; j++) out[i + j] = got[j];
  }
  return out;
}

const stamp = { 'x-app-id': 'tohid-pump-app', 'x-app-version': '3.1.186', 'x-app-platform': 'windows' };

test(`${N} پمپ‌دار: هر کدام حساب، پمپ، دستگاه و دفترِ خودش — و هیچ‌کدام دفترِ دیگری را نمی‌بیند`, async () => {
  //  ── ۱) N حسابِ واقعی (ثبت‌نامِ سه‌پله‌ای) ─────────────────────────────
  let t0 = performance.now();
  const users = await waves(Array.from({ length: N }, (_, i) => i), (i) => h.newUser(`پمپ‌دار ${i + 1}`, 'pump'));
  const tReg = performance.now() - t0;
  assert.equal(users.filter((u) => u?.accessToken).length, N, 'همهٔ حساب‌ها باید ساخته شوند');

  //  ── ۲) هر کدام پمپِ خودش ────────────────────────────────────────────
  t0 = performance.now();
  const pumps = await waves(users, async (u, i) => {
    const r = await h.post('/api/pump', { name: `پمپِ شمارهٔ ${i + 1}`, code: `scale-${i + 1}` },
      { token: u.accessToken, headers: stamp });
    return { status: r.status, id: r.body?.station?.id, code: r.body?.station?.code };
  });
  const tPump = performance.now() - t0;
  const badPump = pumps.filter((p) => p.status !== 201 || !p.id);
  assert.equal(badPump.length, 0, `ساختنِ پمپ شکست خورد: ${JSON.stringify(badPump.slice(0, 3))}`);
  assert.equal(new Set(pumps.map((p) => p.id)).size, N, 'هیچ دو حسابی یک پمپ نگرفتند');

  //  ── ۳) هر کدام کامپیوترش را بند می‌کند ⇒ توکنِ دستگاه ──────────────────
  t0 = performance.now();
  const devs = await waves(users, async (u, i) => {
    const r = await h.post('/api/pump/device/bind',
      { device: { uid: `duid-${i + 1}-${crypto.randomBytes(4).toString('hex')}`, name: 'کامپیوترِ پمپ', platform: 'windows' } },
      { token: u.accessToken, headers: stamp });
    return { status: r.status, token: r.body?.deviceToken, station: r.body?.station?.id, body: r.body };
  });
  const tBind = performance.now() - t0;
  const badBind = devs.filter((d) => d.status !== 201 || !d.token);
  assert.equal(badBind.length, 0, `بند شدن شکست خورد: ${JSON.stringify(badBind.slice(0, 2).map((d) => [d.status, d.body]))}`);
  assert.equal(new Set(devs.map((d) => d.token)).size, N, 'هیچ دو دستگاهی توکنِ یکسان ندارند');
  devs.forEach((d, i) => assert.equal(d.station, pumps[i].id, 'دستگاه به پمپِ همان حساب بند شد، نه پمپِ دیگری'));

  //  ── ۴) هر پمپ یک ردیفِ واقعی با نشانهٔ خودش می‌نویسد ────────────────
  const marks = users.map((_, i) => `MARK-${i + 1}-${crypto.randomBytes(6).toString('hex')}`);
  t0 = performance.now();
  const pushes = await waves(devs, async (d, i) => {
    const r = await h.post('/api/sync/v1/push', {
      device_id: `dev-a-${i + 1}`, schema_version: 1, queued: 1,
      ops: [{ op_id: crypto.randomUUID(), table: 'SafeEntry', row_id: `ROW${i + 1}X${crypto.randomBytes(4).toString('hex')}`.toUpperCase(),
        type: 'insert', ts: Date.now(), fields: { Title: marks[i], Amount: String(1000 + i), MonthKey: '1405-07' } }],
    }, { token: d.token, headers: stamp });
    return { status: r.status, applied: r.body?.applied, body: r.body };
  });
  const tPush = performance.now() - t0;
  const badPush = pushes.filter((p) => p.status !== 200 || Number(p.applied) !== 1);
  assert.equal(badPush.length, 0, `نوشتن شکست خورد: ${JSON.stringify(badPush.slice(0, 2))}`);

  //  ── ۵) دستگاهِ دومِ هر پمپ دفتر را می‌گیرد: فقط یک op، و مالِ خودش ────
  t0 = performance.now();
  const pulls = await waves(devs, async (d, i) => {
    const r = await h.get(`/api/sync/v1/pull?device_id=dev-b-${i + 1}&since=0&limit=500`, { token: d.token, headers: stamp });
    return { status: r.status, ops: r.body?.ops || [], text: JSON.stringify(r.body) };
  });
  const tPull = performance.now() - t0;
  const allMarks = new Set(marks);
  let leaks = 0, wrongCount = 0, foreign = 0;
  pulls.forEach((p, i) => {
    if (p.status !== 200 || p.ops.length !== 1) wrongCount++;
    const mine = p.ops.some((o) => o.fields?.Title === marks[i]);
    if (!mine) leaks++;
    //  نشانهٔ هر پمپِ دیگری در این پاسخ؟
    for (const m of allMarks) if (m !== marks[i] && p.text.includes(m)) { foreign++; break; }
  });
  assert.equal(wrongCount, 0, `${wrongCount} پمپ عددِ opِ اشتباه گرفتند (باید دقیقاً یک باشد)`);
  assert.equal(leaks, 0, `${leaks} پمپ ردیفِ خودشان را نگرفتند`);
  assert.equal(foreign, 0, `${foreign} پمپ نشانهٔ پمپِ دیگری را در پاسخ دیدند — نشتِ داده`);

  //  ── ۶) خودِ دستگاهِ نویسنده پژواک نمی‌گیرد ──────────────────────────
  const echo = await h.get('/api/sync/v1/pull?device_id=dev-a-1&since=0&limit=500', { token: devs[0].token, headers: stamp });
  assert.equal((echo.body?.ops || []).length, 0, 'سرور opِ خودِ همان دستگاه را پس نمی‌دهد');

  //  ── ۷) /api/pump/me هر حساب پمپِ خودش را می‌گوید ─────────────────────
  t0 = performance.now();
  const mes = await waves(users, (u) => h.get('/api/pump/me', { token: u.accessToken, headers: stamp }));
  const tMe = performance.now() - t0;
  let wrongMe = 0;
  mes.forEach((r, i) => { if (r.status !== 200 || r.body?.station?.id !== pumps[i].id || r.body?.station?.code !== `scale-${i + 1}`) wrongMe++; });
  assert.equal(wrongMe, 0, `${wrongMe} حساب پمپِ اشتباه دیدند`);

  //  عددها همین‌جا چاپ می‌شوند، پیش از سنجه‌های تکی — سرخیِ بعدی نباید آن‌ها را ببرد
  console.log(`\n  ══ ${N} پمپ‌دار (موج‌های ${PAR}تایی) ══`);
  console.log(`  ثبت‌نامِ سه‌پله‌ای   ${ms(tReg)}   (${ms(tReg / N)} هر حساب)`);
  console.log(`  ساختنِ پمپ          ${ms(tPump)}   (${ms(tPump / N)} هر پمپ)`);
  console.log(`  بند شدنِ دستگاه     ${ms(tBind)}   (${ms(tBind / N)} هر دستگاه)`);
  console.log(`  نوشتنِ یک op        ${ms(tPush)}   (${ms(tPush / N)})`);
  console.log(`  گرفتنِ دفتر         ${ms(tPull)}   (${ms(tPull / N)})`);
  console.log(`  /api/pump/me        ${ms(tMe)}   (${ms(tMe / N)})`);
  console.log(`  نشت: ${leaks} · پاسخِ بیگانه: ${foreign} · حسابِ اشتباه: ${wrongMe}\n`);

  //  ── ۸) «account=» در نشانی هیچ اثری ندارد — شناسه از توکن است ─────────
  const a = 0, b = 1;
  const cross = await h.get(`/api/sync/v1/pull?device_id=dev-x&since=0&limit=500&account=${encodeURIComponent(pumps[b].id)}`,
    { token: devs[a].token, headers: stamp });
  assert.equal(cross.status, 200);
  assert.ok(cross.body.ops.some((o) => o.fields?.Title === marks[a]), 'همچنان دفترِ خودِ A');
  assert.ok(!JSON.stringify(cross.body).includes(marks[b]), 'و هیچ ردی از دفترِ B');
  //  و توکنِ حسابِ A (نه دستگاه) هم همان دفترِ A را می‌بیند، نه B
  const asUser = await h.get('/api/sync/v1/pull?device_id=dev-u&since=0&limit=500', { token: users[a].accessToken, headers: stamp });
  assert.equal(asUser.status, 200);
  assert.ok(asUser.body.ops.some((o) => o.fields?.Title === marks[a]));
  assert.ok(!JSON.stringify(asUser.body).includes(marks[b]));

  //  ── ۹) نوشتنِ حسابِ B هیچ‌وقت در دفترِ A نمی‌نشیند ───────────────────
  const stray = await h.post('/api/sync/v1/push', {
    device_id: 'dev-b-stray', schema_version: 1,
    ops: [{ op_id: crypto.randomUUID(), table: 'SafeEntry', row_id: 'STRAYROW1', type: 'insert', ts: Date.now(),
      fields: { Title: 'STRAY-FROM-B', Amount: '1', MonthKey: '1405-07' } }],
  }, { token: devs[b].token, headers: stamp });
  assert.equal(stray.status, 200);
  const aAgain = await h.get('/api/sync/v1/pull?device_id=dev-c-1&since=0&limit=500', { token: devs[a].token, headers: stamp });
  assert.equal(aAgain.body.ops.length, 1, 'دفترِ A هنوز همان یک ردیفِ خودش را دارد');
  assert.ok(!JSON.stringify(aAgain.body).includes('STRAY-FROM-B'));

  //  ── ۱۰) دستگاهِ جداشده همان لحظه بیرون است، و همسایه‌اش نه ────────────
  const list = await h.get('/api/pump/devices', { token: users[a].accessToken, headers: stamp });
  assert.equal(list.status, 200, JSON.stringify(list.body));
  //  ⚠️ نامِ فیلد `deviceUid` است (پاسخِ واقعیِ `GET /api/pump/devices`)؛ بارِ اول
  //  با `uid` نوشته شد و همین سنجه با ۲۰۰۰ پمپ گرفتش — ایرادِ سنجه بود، نه سرور.
  const row = (list.body?.devices || []).find((d) => String(d.deviceUid || '').startsWith('duid-1-'));
  assert.ok(row, `کامپیوترِ بندشدهٔ A در فهرستِ دستگاه‌هایش نیست: ${JSON.stringify(list.body).slice(0, 300)}`);
  {
    const gone = await h.post(`/api/pump/devices/${row.id}/revoke`, {}, { token: users[a].accessToken, headers: stamp });
    assert.ok(gone.status < 300, `جدا کردن: ${gone.status} ${JSON.stringify(gone.body)}`);
    const dead = await h.get('/api/sync/v1/pull?device_id=dev-a-1&since=0', { token: devs[a].token, headers: stamp });
    assert.equal(dead.status, 401, 'توکنِ دستگاهِ جداشده باید همان لحظه بمیرد');
    const alive = await h.get('/api/sync/v1/pull?device_id=dev-b-2&since=0', { token: devs[b].token, headers: stamp });
    assert.equal(alive.status, 200, 'جدا کردنِ دستگاهِ A دستگاهِ B را نمی‌اندازد');
  }
});
