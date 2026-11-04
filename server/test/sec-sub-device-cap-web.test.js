'use strict';
/**
 * O-09 / SUB-09 در نسخهٔ وب — صاحبِ دکان از مرورگرِ تازه، وقتی جاهای دکان پر است.
 *
 * ریشه: `login()` ِ `license/license-client.js` در بدنهٔ ورود هیچ `device` نمی‌فرستاد (برنامه‌ها همیشه می‌فرستند).
 * پس سرور برای مرورگر ردیفِ دستگاه نمی‌ساخت و `claimSlot` ِ `routes/license.js` آن را «دستگاهِ ثبت‌شدهٔ خودِ صاحب»
 * نمی‌شناخت: `POST /license/sync` ⇒ `device_limit` و صندوق و قرض‌داران روی همان مرورگر قفل. پیامِ صاحب («یک بار خارج و
 * دوباره وارد شوید») هم روی وب کاری نمی‌کرد، چون ورودِ دوباره هم دستگاه را نمی‌گفت.
 *
 * این‌جا خودِ `license/license-client.js` در vm (localStorage و DOM ِ ساختگی) با **سرورِ واقعی** حرف می‌زند — همان
 * درخواست‌هایی که مرورگر می‌فرستد، همان سنجشِ امضای مجوز. SEC-CLI-01 سرِ جایش: شناسهٔ ثبت‌نشده با همان نشست
 * (مرورگرِ دیگر با توکنِ کپی‌شده) فقط با جای خالی؛ کارمندِ بیرون از سقف هم رد. نوارِ هشدار: `test/web-device-cap.mjs`.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');

const SRC = fs.readFileSync(path.join(__dirname, '..', '..', 'license', 'license-client.js'), 'utf8');
const ACCT_KEY = 'tohid-license-v1';
const DEVICE_KEY = 'tohid-device-uid-v1';

let admin = '';
test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  admin = (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
  assert.ok(admin);
});
test.after(async () => { await h.stop(); });

/** صاحبِ دکان با اشتراکِ `max` دستگاهی؛ دستگاهِ ثبت‌نامِ صاحب (`dev-N`) خودش یک جا است */
async function shopWithCap(label, max) {
  const owner = await h.newUser(label);
  const shop = (await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: owner.accessToken })).body.shop;
  const g = await h.post('/api/admin/subscriptions', { shopId: shop.id, plan: 'm1', days: 30, maxDevices: max }, { token: admin });
  assert.equal(g.status, 201, JSON.stringify(g.body));
  return { owner, shop };
}
/** کارمندِ همین دکان؛ دستگاهِ ثبت‌نامش جای دوم را می‌گیرد ⇒ با سقفِ ۲ دکان پر است */
async function staffOf(shop, label) {
  const u = await h.newUser(label);
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at)
     VALUES ($1,$2,$3,'staff','active',$4,$4)`, [newId('mem'), shop.id, u.user.id, now()]);
  return u;
}
async function flag(shop, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope: shop.id, key: 'shop.device_cap', value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

/**
 *  یک مرورگر: همان `license/license-client.js`، با localStorage ِ خودش و سرورِ واقعیِ آزمون.
 *  `seed` ⇒ کلیدهای localStorage پیش از بار شدن (مثلاً نشستِ کپی‌شده از مرورگرِ دیگر).
 */
function browser(seed = {}) {
  const ls = new Map(Object.entries(seed));
  const node = { classList: { add() {}, remove() {}, toggle() {} }, setAttribute() {}, removeAttribute() {}, remove() {} };
  const timers = [];
  const ctx = {
    localStorage: {
      getItem: (k) => (ls.has(k) ? ls.get(k) : null),
      setItem: (k, v) => { ls.set(k, String(v)); },
      removeItem: (k) => { ls.delete(k); },
    },
    document: {
      readyState: 'complete', documentElement: node,
      addEventListener() {}, dispatchEvent() {}, querySelector: () => null, querySelectorAll: () => [], getElementById: () => null,
    },
    navigator: {
      userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/130.0 Safari/537.36',
      language: 'fa', platform: 'Win32', hardwareConcurrency: 4, onLine: true,
    },
    screen: { width: 1280, height: 800 },
    TohidApiConfig: { baseUrl: () => h.base(), setBaseUrl() {}, appId: () => 'shop', appVersion: () => 'web-test' },
    fetch, AbortController, setTimeout, clearTimeout, CustomEvent, TextDecoder, TextEncoder, Intl, URL, atob, btoa, console,
    crypto: globalThis.crypto,
    //  init() دو شمارندهٔ همیشگی دارد؛ آزمون نباید به خاطرِ آن‌ها باز بماند
    setInterval: (fn, ms) => { const t = setInterval(fn, ms); t.unref(); timers.push(t); return t; },
    clearInterval,
    addEventListener() {},
  };
  ctx.window = ctx;
  ctx.self = ctx;
  vm.createContext(ctx);
  vm.runInContext(SRC, ctx, { filename: 'license/license-client.js' });
  return {
    L: ctx.TohidLicense,
    ls,
    acct: () => JSON.parse(ls.get(ACCT_KEY) || '{}'),
    close: () => timers.forEach((t) => clearInterval(t)),
  };
}

const devicesOf = async (userId) => (await query(
  'SELECT device_uid, status, platform FROM devices WHERE user_id=$1 ORDER BY created_at', [userId])).rows;

test('O-09 وب: صاحب از مرورگرِ تازه، دکانِ پر و کلیدِ سقف روشن ⇒ ورود همین مرورگر را ثبت می‌کند و مجوز با هشدار می‌آید', async () => {
  const { owner, shop } = await shopWithCap('وب-صاحب', 2);
  await staffOf(shop, 'وب-کارمند');
  await flag(shop, true);

  const web = browser();
  try {
    const uid = web.L.getDeviceUid();
    assert.match(uid, /^[0-9a-f]{32}$/, 'شناسهٔ تصادفیِ همین مرورگر');
    assert.equal(web.ls.get(DEVICE_KEY), uid, 'در localStorage ِ همین مرورگر می‌ماند');
    assert.ok(!uid.includes(owner.user.id) && !uid.includes(String(owner.email).split('@')[0]), 'از دادهٔ کاربر ساخته نشده');

    await web.L.login(owner.email, owner.password);
    //  «ثبت مثلِ برنامه‌ها»: ردیفِ دستگاه با رمزِ ورود ساخته می‌شود، نه با `license/sync`
    const row = (await devicesOf(owner.user.id)).find((d) => d.device_uid === uid);
    assert.ok(row, `ورودِ وب دستگاهِ مرورگر را ثبت نکرد: ${JSON.stringify(await devicesOf(owner.user.id))}`);
    assert.equal(row.status, 'active');
    assert.equal(row.platform, 'web');

    await web.L.sync();
    const st = web.acct();
    assert.ok(st.license, `صاحب روی مرورگرِ خودش قفل شد: ${JSON.stringify(st.serverDenied)}`);
    assert.equal(st.serverDenied, null);
    assert.equal(web.L.deniedCode(), '');
    assert.deepEqual(JSON.parse(JSON.stringify(web.L.deviceCapOver())), { devices: 3, maxDevices: 2 },
      'هشدارِ «بیرون از سقف» به رابطِ وب نرسید');

    //  همان مرورگر دوباره وارد شود ⇒ همان شناسه، جای تازه‌ای نمی‌گیرد
    const before = (await devicesOf(owner.user.id)).length;
    await web.L.login(owner.email, owner.password);
    assert.equal(web.L.getDeviceUid(), uid, 'شناسه پایدار است');
    assert.equal((await devicesOf(owner.user.id)).length, before, 'ورودِ دوباره دستگاهِ تازه ساخت');
  } finally { web.close(); }
});

test('SEC-CLI-01 در وب: توکنِ صاحب روی مرورگرِ دیگر با شناسهٔ ثبت‌نشده ⇒ device_limit، بی هشدار', async () => {
  const { owner, shop } = await shopWithCap('وب-جعل', 2);
  await staffOf(shop, 'وب-جعل-کارمند');
  await flag(shop, true);

  const web = browser();
  const copy = browser();   //  شناسهٔ خودش را می‌سازد؛ نشست را از مرورگرِ اول برمی‌دارد
  try {
    await web.L.login(owner.email, owner.password);
    await web.L.sync();
    assert.ok(web.acct().license);

    const forged = copy.L.getDeviceUid();
    assert.notEqual(forged, web.L.getDeviceUid());
    copy.ls.set(ACCT_KEY, JSON.stringify({ ...web.acct(), license: '', serverDenied: null, deviceCap: null }));
    await copy.L.sync();
    const st = copy.acct();
    assert.ok(!st.license, 'شناسهٔ ثبت‌نشده با نشستِ صاحب سقف را دور زد');
    assert.equal(copy.L.deniedCode(), 'device_limit');
    assert.equal(copy.L.deviceCapOver(), null);
    assert.equal((await devicesOf(owner.user.id)).some((d) => d.device_uid === forged), false,
      'license/sync شناسهٔ ساختگی را بی جای خالی نشاند');
  } finally { web.close(); copy.close(); }
});

test('O-09 وب: کارمند از مرورگرِ تازه بیرون از سقف (کلید روشن) ⇒ device_limit؛ کلیدِ خاموش ⇒ همان رفتارِ برنامه‌ها', async () => {
  const { shop } = await shopWithCap('وب-کارمند-سقف', 2);
  const staff = await staffOf(shop, 'وب-کارمندِ-بیرون');
  await flag(shop, true);

  const web = browser();
  try {
    await web.L.login(staff.email, staff.password);
    await web.L.sync();
    assert.ok(!web.acct().license, 'دستگاهِ کارمندِ بیرون از سقف مجوز گرفت');
    assert.equal(web.L.deniedCode(), 'device_limit');
    assert.equal(web.L.deviceCapOver(), null, 'هشدارِ صاحب به کارمند نمی‌رسد');
    assert.match(web.L.deniedMessage(), /صاحبِ دکان/);

    //  کلیدِ خاموش: دستگاهی که با ورود ثبت شده مثلِ پیش مجوز می‌گیرد — همان برنامه‌های نصب‌شده، بی هشدار
    await flag(shop, false);
    await web.L.sync();
    assert.ok(web.acct().license);
    assert.equal(web.L.deviceCapOver(), null);
  } finally { web.close(); }
});

test('O-09 وب: صاحب دستگاهِ کهنه را برمی‌دارد ⇒ هشدار از مرورگر پاک می‌شود؛ خروج هم پاکش می‌کند', async () => {
  const { owner, shop } = await shopWithCap('وب-برداشتن', 2);
  const staff = await staffOf(shop, 'وب-برداشتن-کارمند');
  await flag(shop, true);

  const web = browser();
  try {
    await web.L.login(owner.email, owner.password);
    await web.L.sync();
    assert.ok(web.L.deviceCapOver(), 'بیرون از سقف ⇒ هشدار');

    const staffDev = (await one('SELECT id FROM devices WHERE user_id=$1', [staff.user.id])).id;
    const off = await h.post(`/api/shop/devices/${staffDev}/disable`, {}, { token: web.acct().accessToken });
    assert.equal(off.status, 200, JSON.stringify(off.body));
    await web.L.sync();
    assert.ok(web.acct().license);
    assert.equal(web.L.deviceCapOver(), null, 'زیرِ سقف هشدار ماند');

    //  دوباره بیرون از سقف، بعد خروج ⇒ چیزی از هشدار در این مرورگر نمی‌ماند
    await staffOf(shop, 'وب-برداشتن-کارمندِ-۲');
    await web.L.sync();
    assert.ok(web.L.deviceCapOver());
    web.L.logout();
    assert.equal(web.L.deviceCapOver(), null);
  } finally { web.close(); }
});
