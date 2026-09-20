'use strict';
/**
 * `vill3n-sdk.js` — بخشِ ۱۳.۳ی پرامپت.
 *
 * ── خواستهٔ صاحب سامانه ────────────────────────────────────────────
 *   «یک SDK کوچک (`vill3n-sdk.js`) به برنامه اضافه می‌شود که همه‌چیز را
 *    یکجا می‌دهد: ورود با کد ایمیلی، بررسی لایسنس، Heartbeat، Sync
 *    اختیاری، بخش پشتیبانی، دریافت آپدیت، ارسال خطاها. برنامه فقط این
 *    را صدا می‌زند: `VILL3N.init({ app: "dukan", version: "2.1.0" })`.»
 *
 * ── چطور سنجیده می‌شود ─────────────────────────────────────────────
 * ⛔ **هیچ چیزی ساختگی نیست جز خودِ مرورگر.** فایل در یک `vm` با یک
 *    `window`ِ کوچک بار می‌شود و `fetch`ش **واقعاً** به همین سرورِ
 *    آزمون می‌رود. اگر SDK چیزی بخواهد که سرور ندارد، همین‌جا سرخ
 *    می‌شود — نه روی گوشیِ مشتری.
 * ⛔ **هیچ وابستگی‌ای ندارد**: `require`، `import` و `process` در آن
 *    فایل نباید باشند (سنجهٔ آخر).
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');

const SDK_PATH = path.join(__dirname, '..', 'public', 'sdk', 'vill3n-sdk.js');
const SOURCE = fs.readFileSync(SDK_PATH, 'utf8');

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
});
test.after(async () => { await h.stop(); });

async function adminToken() {
  const r = await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' });
  return r.body.token;
}

/** یک `localStorage`ِ کوچک — همان قراردادِ مرورگر. */
function fakeStorage() {
  const map = new Map();
  return {
    getItem: (k) => (map.has(k) ? map.get(k) : null),
    setItem: (k, val) => { map.set(k, String(val)); },
    removeItem: (k) => { map.delete(k); },
    get size() { return map.size; },
    _map: map,
  };
}

/**
 * بارِ SDK در یک «مرورگرِ» کوچک.
 *
 * `fetch` همان `fetch`ِ واقعیِ Node است با نشانیِ پایهٔ سرورِ آزمون، پس
 * هر درخواستی که SDK می‌زند واقعاً به سرور می‌رسد.
 */
function loadSdk({ storage = fakeStorage(), calls = [] } = {}) {
  const base = h.base();
  const sandbox = {
    localStorage: storage,
    setInterval: () => ({ unref() {} }),
    clearInterval: () => {},
    setTimeout, clearTimeout,
    console,
    fetch: (url, init) => {
      calls.push({ url: String(url), init });
      return fetch(String(url), init);
    },
  };
  sandbox.window = sandbox;
  sandbox.globalThis = sandbox;
  vm.createContext(sandbox);
  vm.runInContext(SOURCE, sandbox, { filename: 'vill3n-sdk.js' });
  return { sdk: sandbox.window.VILL3N, sandbox, storage, calls };
}

async function shopOwner(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { ...u, shopId: made.body.shop.id };
}

/* ══════════════════════════════════════════════════════════════════
   ۱) بار شدن و init
   ══════════════════════════════════════════════════════════════════ */

test('SDK در یک مرورگرِ کوچک بار می‌شود و `window.VILL3N` را می‌گذارد', () => {
  const { sdk } = loadSdk();
  assert.ok(sdk, 'VILL3N باید روی window بنشیند');
  for (const fn of ['init', 'login', 'verify', 'heartbeat', 'subscription', 'notices',
    'markRead', 'reportError', 'checkUpdate', 'onChange', 'logout', 'settings', 'setCloudSync']) {
    assert.equal(typeof sdk[fn], 'function', `${fn} باید تابع باشد`);
  }
  assert.equal(typeof sdk.support.thread, 'function');
  assert.equal(typeof sdk.support.send, 'function');

  const state = sdk.init({ app: 'shop', version: '2.1.0', base: h.base() });
  assert.equal(state.ready, true);
  assert.equal(state.signedIn, false);
  assert.equal(sdk.config().app, 'shop');
  assert.equal(sdk.config().version, '2.1.0');
});

test('⛔ هیچ وابستگی‌ای ندارد — نه require، نه import، نه process', () => {
  const code = SOURCE.split('\n').filter(l => !l.trim().startsWith('*') && !l.trim().startsWith('//')).join('\n');
  for (const bad of [/\brequire\s*\(/, /\bimport\s+/, /\bprocess\./, /from\s+['"]/]) {
    assert.ok(!bad.test(code.replace(/module\.exports/g, '')), `${bad} نباید در SDK باشد`);
  }
  //  و در یک `vm`ِ بی `module` هم بار می‌شود (همان `<script src=…>`)
  const sandbox = { window: {}, console, fetch, setInterval: () => ({}), clearInterval: () => {}, localStorage: fakeStorage() };
  sandbox.window.window = sandbox.window;
  sandbox.globalThis = sandbox;
  vm.createContext(sandbox);
  vm.runInContext(SOURCE, sandbox);
  assert.ok(sandbox.VILL3N, 'بی module هم روی جهانی می‌نشیند');
});

test('بی `localStorage` هم نمی‌شکند — پنجرهٔ ناشناس و WebView', () => {
  const angry = {
    getItem() { throw new Error('blocked'); },
    setItem() { throw new Error('blocked'); },
    removeItem() { throw new Error('blocked'); },
  };
  const { sdk } = loadSdk({ storage: angry });
  const state = sdk.init({ app: 'shop', base: h.base() });
  assert.equal(state.ready, true, 'حافظهٔ بسته نباید SDK را بکشد');
});

/* ══════════════════════════════════════════════════════════════════
   ۲) ورود — همان دو پلهٔ واقعی روی همین سرور
   ══════════════════════════════════════════════════════════════════ */

test('login/verify: کد واقعاً از سرور می‌آید و نشست می‌نشیند', async () => {
  const u = await shopOwner('اس‌دی‌کی');
  const { sdk, storage } = loadSdk();
  sdk.init({ app: 'shop', version: '1.0.0', base: h.base(), autoHeartbeat: false });

  const asked = await sdk.login(u.email);
  assert.equal(asked.sent, true);
  //  کد را از همان دفترِ سرور برمی‌داریم (در مرورگرِ واقعی از ایمیل می‌آید)
  const sent = await h.post('/api/auth/otp/request', { email: u.email, app: 'shop' });
  const code = sent.body.devCode;
  assert.ok(code);

  //  ⚠️ خطایی که از داخلِ `vm` می‌آید نمونهٔ `Error`ِ **این** قلمرو
  //  نیست (هر قلمرو سازندهٔ خودش را دارد)، پس با `instanceof` نسنجید.
  const bad = await sdk.verify('000000').then(() => null, err => err);
  assert.ok(bad && bad.message, 'کدِ غلط باید خطا بدهد');
  assert.equal(sdk.state().signedIn, false);

  const state = await sdk.verify(code);
  assert.equal(state.signedIn, true);
  assert.ok(storage.getItem('vill3n.tokens'), 'توکن در حافظهٔ مرورگر می‌نشیند');
  const saved = JSON.parse(storage.getItem('vill3n.tokens'));
  assert.ok(saved.accessToken && saved.refreshToken);
});

test('هر درخواستی شناسهٔ برنامه و نسخه را می‌برد', async () => {
  const u = await shopOwner('هدردار');
  const calls = [];
  const { sdk } = loadSdk({ calls });
  sdk.init({ app: 'pump', version: '4.5.6', base: h.base(), autoHeartbeat: false });
  await sdk.login(u.email);
  const last = calls[calls.length - 1];
  assert.equal(last.init.headers['X-App-Id'], 'pump');
  assert.equal(last.init.headers['X-App-Version'], '4.5.6');
  assert.ok(last.init.headers['X-App-Platform']);
  assert.equal(JSON.parse(last.init.body).app, 'pump', 'و در بدنه هم — سرورِ قدیمی هدر را نمی‌خواند');
});

/* ══════════════════════════════════════════════════════════════════
   ۳) تپش، نمای اشتراک و کَش
   ══════════════════════════════════════════════════════════════════ */

/** ورودِ کامل با SDK — برای سنجه‌های بعدی. */
async function signedInSdk(name, opts = {}) {
  const u = await shopOwner(name);
  const kit = loadSdk();
  kit.sdk.init({ app: 'shop', version: '1.0.0', base: h.base(), autoHeartbeat: false, ...opts });
  const sent = await h.post('/api/auth/otp/request', { email: u.email, app: 'shop' });
  await kit.sdk.verify(sent.body.devCode, u.email);
  return { ...kit, user: u };
}

test('heartbeat: اشتراک، شمارِ نخوانده و آخرین نسخه — و نمای رنگ و روز', async () => {
  const t = await adminToken();
  const { sdk, user } = await signedInSdk('تپنده');
  await h.post('/api/admin/subscriptions', { shopId: user.shopId, plan: 'm1', days: 90 }, { token: t });

  const data = await sdk.heartbeat({ force: true });
  assert.equal(data.tenantId, user.shopId);
  const s = sdk.subscription();
  assert.ok(s, 'نمای اشتراک باید باشد');
  assert.equal(s.active, true);
  assert.equal(s.permanent, false);
  assert.ok(s.daysLeft > 30);
  assert.equal(s.color, 'green');
  assert.equal(s.urgent, false);
  assert.ok(s.label.includes('روز مانده'));
  assert.ok(s.progress >= 0 && s.progress <= 100);

  //  قابلیت‌ها از سرور می‌آیند، نه از برنامه
  assert.ok(Array.isArray(sdk.entitlement().features));
  assert.equal(sdk.can('هیچ-قابلیتی'), false);
});

test('اشتراکِ نزدیکِ پایان `urgent` می‌شود و دائمی «دائمی ✓» بی شمارش', async () => {
  const t = await adminToken();
  const a = await signedInSdk('فوری');
  await h.post('/api/admin/subscriptions', { shopId: a.user.shopId, plan: 'm1', days: 3 }, { token: t });
  await a.sdk.heartbeat({ force: true });
  const s1 = a.sdk.subscription();
  assert.equal(s1.color, 'yellow');
  assert.equal(s1.urgent, true, '≤ ۷ روز یعنی فوری');

  const b = await signedInSdk('همیشگی');
  const sub = await h.post('/api/admin/subscriptions', { shopId: b.user.shopId, plan: 'm1', days: 30 }, { token: t });
  await h.post(`/api/admin/subscriptions/${sub.body.subscription.id}/permanent`, {}, { token: t });
  await b.sdk.heartbeat({ force: true });
  const s2 = b.sdk.subscription();
  assert.equal(s2.permanent, true);
  assert.equal(s2.daysLeft, null, '⛔ دائمی روز نمی‌شمارد');
  assert.equal(s2.label, 'دائمی ✓');
});

test('تپش در حافظه کَش می‌شود — همان «هر ۱۵ دقیقه»، و `force` ردش می‌کند', async () => {
  const { sdk, storage, calls } = await signedInSdk('کَشی');
  await sdk.heartbeat({ force: true });
  assert.ok(storage.getItem('vill3n.beat'), 'عکسِ تپش در حافظهٔ مرورگر می‌ماند');

  const before = calls.filter(c => c.url.includes('/heartbeat')).length;
  await sdk.heartbeat();                  // در پنجرهٔ ۱۵ دقیقه ⇒ از کَش
  assert.equal(calls.filter(c => c.url.includes('/heartbeat')).length, before,
    'درخواستِ تازه نزد');
  await sdk.heartbeat({ force: true });   // دکمهٔ «تازه‌سازی»
  assert.equal(calls.filter(c => c.url.includes('/heartbeat')).length, before + 1);
});

test('عکسِ کهنه با بالا آمدنِ دوباره برمی‌گردد — برنامهٔ آفلاین صفحهٔ خالی نمی‌بیند', async () => {
  const t = await adminToken();
  const { sdk, storage, user } = await signedInSdk('آفلاین');
  await h.post('/api/admin/subscriptions', { shopId: user.shopId, plan: 'm1', days: 45 }, { token: t });
  await sdk.heartbeat({ force: true });

  //  همان حافظه، یک نمونهٔ تازه — مثلِ باز شدنِ دوبارهٔ برنامه
  const again = loadSdk({ storage });
  const state = again.sdk.init({ app: 'shop', base: h.base(), autoHeartbeat: false });
  assert.equal(state.signedIn, true, 'توکن از حافظه برمی‌گردد');
  const s = again.sdk.subscription();
  assert.ok(s && s.active, 'اشتراکِ کَش‌شده پیش از هر درخواستی دیده می‌شود');
});

test('onChange: هر تغییری به شنونده‌ها می‌رسد و «لغوِ اشتراک» کار می‌کند', async () => {
  const { sdk } = await signedInSdk('شنونده');
  const seen = [];
  const off = sdk.onChange(s => seen.push(s));
  assert.equal(seen.length, 1, 'همان لحظهٔ ثبت، حالِ فعلی می‌رسد');
  await sdk.heartbeat({ force: true });
  assert.ok(seen.length >= 2, 'تپش خبر می‌دهد');
  off();
  const n = seen.length;
  await sdk.heartbeat({ force: true });
  assert.equal(seen.length, n, 'پس از لغو دیگر خبری نمی‌رسد');
});

test('شنوندهٔ خراب بقیه را نمی‌کشد', async () => {
  const { sdk } = await signedInSdk('خراب');
  let ok = 0;
  sdk.onChange(() => { throw new Error('من خرابم'); });
  sdk.onChange(() => { ok++; });
  await sdk.heartbeat({ force: true });
  assert.ok(ok >= 1, 'شنوندهٔ سالم خبرش را گرفت');
});

/* ══════════════════════════════════════════════════════════════════
   ۴) اعلان، پشتیبانی، تخفیف، تنظیمات
   ══════════════════════════════════════════════════════════════════ */

test('notices/markRead روی همان صندوقِ واقعیِ سرور', async () => {
  const t = await adminToken();
  const { sdk, user } = await signedInSdk('اعلانی');
  const made = await h.post('/api/admin/notices', {
    app: 'shop', audience: { kind: 'user', user_id: user.user.id },
    channels: ['inapp'], title: 'خبرِ SDK', body: 'متن',
  }, { token: t });
  await h.post(`/api/admin/notices/${made.body.notice.id}/send`, {}, { token: t });

  const box = await sdk.notices();
  assert.equal(box.notices.length, 1);
  assert.equal(box.unread, 1);
  assert.equal(sdk.state().unread, 1);

  await sdk.markRead(box.notices[0].id);
  assert.equal(sdk.state().unread, 0);
  const again = await sdk.notices();
  assert.equal(again.unread, 0);
});

test('پشتیبانی: رشته باز می‌شود و پیام واقعاً می‌نشیند', async () => {
  const { sdk } = await signedInSdk('پشتیبانی‌خواه');
  const first = await sdk.support.thread();
  assert.ok(first.thread);
  await sdk.support.send('یک پرسش از SDK');
  const after = await sdk.support.thread();
  assert.equal(after.messages.length, 1);
  assert.equal(after.messages[0].body, 'یک پرسش از SDK');
});

test('⛔ قیمت فقط از سرور: `plans()` و `redeem()` هیچ عددی از خودشان نمی‌سازند', async () => {
  const t = await adminToken();
  await h.post('/api/admin/discount-codes',
    { app: 'shop', code: 'SDK50', kind: 'percent', value: 50 }, { token: t });
  const { sdk } = await signedInSdk('خریدار');

  const list = await sdk.plans();
  const server = (await h.get('/api/plans?app=shop')).body;
  //  ⚠️ رشته مقایسه می‌شود نه شیء: آرایهٔ آمده از `vm` قلمروِ دیگری
  //  دارد و `deepEqual`ِ سخت‌گیر فقط سرِ همین سرخ می‌شود.
  assert.equal(
    JSON.stringify(list.plans.map(p => [p.code, p.price])),
    JSON.stringify(server.plans.map(p => [p.code, p.price])),
    'همان قیمتی که سرور می‌دهد، بی یک ریال کم و زیاد');

  const q = await sdk.redeem('SDK50', 'm1');
  const m1 = server.plans.find(p => p.code === 'm1');
  assert.equal(q.price, m1.price);
  assert.equal(q.finalPrice, m1.price - Math.round(m1.price * 50 / 100));
  //  هیچ عددِ قیمتی در خودِ فایلِ SDK نیست
  assert.ok(!/price\s*[:=]\s*\d{3,}/.test(SOURCE), '⛔ هیچ قیمتی در کدِ SDK نوشته نشده');
});

test('Cloud Sync از SDK روشن و خاموش می‌شود', async () => {
  const { sdk, user } = await signedInSdk('همگام');
  const before = await sdk.settings();
  assert.equal(before.settings.shop.cloudSync, false);
  await sdk.setCloudSync(true);
  const row = await one('SELECT cloud_sync FROM shops WHERE id=$1', [user.shopId]);
  assert.equal(row.cloud_sync, true);
  await sdk.setCloudSync(false);
  assert.equal((await one('SELECT cloud_sync FROM shops WHERE id=$1', [user.shopId])).cloud_sync, false);
});

/* ══════════════════════════════════════════════════════════════════
   ۵) خطا و به‌روزرسانی
   ══════════════════════════════════════════════════════════════════ */

test('reportError: بی نشست در صف می‌ماند، با ورود می‌رود — و هیچ‌وقت نمی‌شکند', async () => {
  const u = await shopOwner('خطاگزار');
  const { sdk, storage } = loadSdk();
  sdk.init({ app: 'shop', version: '9.9.9', base: h.base(), autoHeartbeat: false });

  const queued = await sdk.reportError(new Error('پیش از ورود'));
  assert.equal(queued.queued, true, 'بی نشست، صف');
  assert.ok(storage.getItem('vill3n.errq'));

  const sent = await h.post('/api/auth/otp/request', { email: u.email, app: 'shop' });
  await sdk.verify(sent.body.devCode, u.email);
  await sdk.flushErrors();

  const rows = await h.query('SELECT * FROM client_errors WHERE user_id=$1', [u.user.id]);
  assert.ok(rows.rows.length >= 1, 'خطای صف‌شده پس از ورود رفت');
  assert.ok(rows.rows[0].message.includes('پیش از ورود'));

  //  و خطایی که سرور ردش کند هم SDK را نمی‌شکند
  const after = await sdk.reportError(new Error('x'.repeat(5000)), { screen: 'فروش' });
  assert.ok(after, 'reportError هیچ‌وقت استثنا بیرون نمی‌دهد');
});

test('checkUpdate: نسخهٔ تازه از سرور، با مقایسهٔ عددی نه متنی', async () => {
  const t = await adminToken();
  await h.put('/api/admin/downloads',
    { app: 'shop', url: 'https://example.test/app.apk', version: '2.10.0', notes: 'تغییرات' }, { token: t });

  const { sdk } = loadSdk();
  sdk.init({ app: 'shop', version: '2.9.0', base: h.base(), autoHeartbeat: false });
  //  ⛔ بی نشست هم کار می‌کند — برنامه‌ای که وارد نشده باید بتواند به‌روز شود
  const up = await sdk.checkUpdate();
  assert.equal(up.latest, '2.10.0');
  assert.equal(up.url, 'https://example.test/app.apk');
  assert.equal(up.hasUpdate, true, '۲.۱۰.۰ از ۲.۹.۰ تازه‌تر است — عددی، نه متنی');

  const { sdk: newer } = loadSdk();
  newer.init({ app: 'shop', version: '2.10.0', base: h.base(), autoHeartbeat: false });
  assert.equal((await newer.checkUpdate()).hasUpdate, false);

  assert.equal(sdk.compareVersions('2.10.0', '2.9.0'), 1);
  assert.equal(sdk.compareVersions('1.0.0', '1.0.0'), 0);
  assert.equal(sdk.compareVersions('1.0', '1.0.1'), -1);
});

/* ══════════════════════════════════════════════════════════════════
   ۶) نشست: تازه‌سازی و خروج
   ══════════════════════════════════════════════════════════════════ */

test('توکنِ منقضی یک بار تازه می‌شود؛ نشستِ مرده بیرون می‌اندازد', async () => {
  const { sdk, storage, calls } = await signedInSdk('نشستی');
  //  توکنِ دسترسی را خراب می‌کنیم؛ توکنِ تازه‌سازی سالم است
  const t = JSON.parse(storage.getItem('vill3n.tokens'));
  //  ⚠️ توکنِ ساختگی باید ASCII باشد: سرآیندِ HTTP نویسهٔ فارسی نمی‌گیرد
  //  (`fetch` همان‌جا می‌شکند و سنجه چیزِ دیگری را نشان می‌دهد).
  storage.setItem('vill3n.tokens', JSON.stringify({ ...t, accessToken: 'broken-access' }));

  const data = await sdk.heartbeat({ force: true });
  assert.ok(data.serverTime, 'با توکنِ تازه دوباره رفت');
  assert.ok(calls.some(c => c.url.includes('/auth/refresh')), 'یک بار refresh زده شد');
  assert.notEqual(JSON.parse(storage.getItem('vill3n.tokens')).accessToken, 'broken-access');

  //  حالا هر دو را خراب می‌کنیم ⇒ نشست مرده است
  storage.setItem('vill3n.tokens', JSON.stringify({ accessToken: 'broken-a', refreshToken: 'broken-r' }));
  const err = await sdk.heartbeat({ force: true }).then(() => null, e => e);
  assert.ok(err && err.message);
  assert.equal(sdk.state().signedIn, false, 'نشستِ مرده پاک می‌شود');
  assert.equal(storage.getItem('vill3n.tokens'), null);
});

test('logout: توکن روی سرور هم باطل می‌شود', async () => {
  const { sdk, storage } = await signedInSdk('خروجی');
  const access = JSON.parse(storage.getItem('vill3n.tokens')).accessToken;
  assert.equal((await h.get('/api/me', { token: access })).status, 200);

  await sdk.logout();
  assert.equal(sdk.state().signedIn, false);
  assert.equal(storage.getItem('vill3n.tokens'), null);
  assert.equal((await h.get('/api/me', { token: access })).status, 401,
    '⛔ خروج فقط محلی نیست — توکن روی سرور هم می‌میرد');
});

test('سندِ فارسیِ SDK هست و نمونهٔ کارش را دارد', () => {
  const doc = path.join(__dirname, '..', '..', 'docs', 'SDK-fa.md');
  assert.ok(fs.existsSync(doc), 'docs/SDK-fa.md باید باشد');
  const text = fs.readFileSync(doc, 'utf8');
  assert.ok(text.includes('VILL3N.init'), 'نمونهٔ کار باید در سند باشد');
  for (const fn of ['login', 'verify', 'heartbeat', 'subscription', 'notices', 'reportError', 'checkUpdate', 'onChange']) {
    assert.ok(text.includes(fn), `${fn} باید در سند توضیح داده شده باشد`);
  }
});
