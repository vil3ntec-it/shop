'use strict';
/**
 * وب (`license/sync-engine.js`): تغییری که هنوز op نشده (ثبتش نگرفت) با pull گم نمی‌شود — SYN-01.
 *
 * یافته (پیش از این PR، روی main): وقتی حافظهٔ مرورگر پر است، `recordChange` تغییر را در صف نمی‌گذارد و سایه
 * را هم جلو نمی‌برد تا «دفعهٔ بعد دوباره op شود» (پیامِ «تغییرها روی همین دستگاه مانده‌اند»). ولی `pullOnce`
 * پس از نشاندنِ opهای رسیده سایه را برابرِ **کلِ دفترِ امروز** می‌گذاشت ⇒ همان تغییرِ ثبت‌نشده جزوِ سایه
 * می‌شد و هیچ تفاضلی دیگر آن را نمی‌دید: روی همین مرورگر می‌ماند و **هرگز به سرور نمی‌رسید**.
 * اندروید از اول «سایهٔ پیشین + همین opها» دارد (`SyncCommit.absorbedShadow`)؛ وب حالا همان را دارد.
 *
 * کدِ زیرِ آزمون همان دو فایلِ مرورگر است (`sync-core.js` و `sync-engine.js`) در یک `vm`، با `localStorage`ی
 * که می‌شود «پرش» کرد، روبه‌روی سرورِ واقعی.
 *
 *   ۱) حافظهٔ پر ⇒ ثبت نمی‌گیرد ⇒ pull ِ op ِ دستگاهِ دیگر ⇒ حافظه آزاد ⇒ ثبتِ بعدی همان تغییرِ جامانده را هم می‌برد.
 *   ۲) همان، وقتی op ِ رسیده **همان ردیف** را عوض کرده (فیلدِ دیگر) ⇒ هر دو فیلد می‌مانند و به سرور می‌رسند.
 *   ۳) بی تغییرِ ثبت‌نشده ⇒ سایه پس از pull همان دفتر است (رفتارِ پیشین، بی op ِ اضافه).
 *   ۴) بستن و باز کردنِ مرورگر (همان localStorage) میانِ ثبتِ ناموفق و pull ⇒ همان نتیجه.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const h = require('./helpers');
const db = require('../src/db');
const syncV1 = require('../src/lib/sync-v1');

const LICENSE = path.join(__dirname, '..', '..', 'license');
//  هسته همان پرونده با `require` (تصادفِ امنِ Node)؛ موتور در vm با localStorage ِ خودش
const core = require(path.join(LICENSE, 'sync-core.js'));
const ENGINE_SRC = fs.readFileSync(path.join(LICENSE, 'sync-engine.js'), 'utf8');

/** localStorage با سهمیه: `full = true` ⇒ هر نوشتنِ تازه روی کلیدِ صف خطای سهمیه می‌دهد (همان رفتارِ مرورگر) */
function storage() {
  const box = new Map();
  const st = {
    full: false,
    box,
    getItem: (k) => (box.has(k) ? box.get(k) : null),
    setItem: (k, v) => {
      if (st.full && /-oplog$/.test(k)) { const e = new Error('QuotaExceededError'); e.name = 'QuotaExceededError'; throw e; }
      box.set(k, String(v));
    },
    removeItem: (k) => { box.delete(k); },
  };
  return st;
}

/** یک مرورگر: همان دو فایل، همان localStorage (بستن/باز کردن = همین تابع با همان `ls`) */
function browser({ token, userId, deviceId, ls, ledgerBox }) {
  const window = {
    TohidSyncCore: core,
    TohidApiConfig: { baseUrl: () => 'http://127.0.0.1' },
    TohidAccount: {
      configured: () => true, isSignedIn: () => true, account: () => ({ userId }),
      deviceId: () => deviceId, appId: () => 'shop', on() { }, heartbeat: async () => ({}),
      accessToken: async () => token,
      async authFetch(p, o = {}) {
        const r = await h.api(o.method || 'GET', p, {
          token, body: o.body, headers: { 'X-App': 'shop', 'X-Device': deviceId },
        });
        if (r.status >= 400) { const e = new Error(r.body?.error || `http ${r.status}`); e.status = r.status; e.code = r.body?.code; throw e; }
        return r.body;
      },
    },
    addEventListener() { },
    dispatchEvent() { },
  };
  const ctx = vm.createContext({
    window, localStorage: ls, navigator: { onLine: true }, document: { addEventListener() { } },
    URL, console, setTimeout: () => 0, clearTimeout() { }, setInterval: () => 0, clearInterval() { },
    CustomEvent: class { constructor(t, o) { this.type = t; this.detail = o && o.detail; } },
  });
  vm.runInContext(ENGINE_SRC, ctx);
  const sync = window.TohidSync;
  assert.ok(sync, 'موتورِ وب بالا آمد');
  //  دفترِ صفحه (index.html صاحبش است) — این‌جا یک جعبهٔ ساده که بستن/باز کردن را تاب می‌آورد
  sync.bind({ read: () => JSON.parse(JSON.stringify(ledgerBox.data)), write: (d) => { ledgerBox.data = JSON.parse(JSON.stringify(d)); } });
  return {
    sync,
    /** همان `saveState()` ِ صفحه: دفتر نوشته می‌شود، بعد `recordChange` */
    save(mutate) {
      const d = JSON.parse(JSON.stringify(ledgerBox.data));
      mutate(d);
      ledgerBox.data = d;
      return sync.recordChange('save');
    },
    row: (table, id) => (ledgerBox.data[table] || []).find(r => String(r.id) === String(id)) || null,
  };
}

async function account(label) {
  const u = await h.newUser(`صاحب-${label}`, 'shop');
  const made = await h.api('POST', '/api/shop', { token: u.accessToken, body: { name: `دکان ${label}` } });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { token: u.accessToken, userId: u.user?.id || u.id || label, shopId: made.body.shop.id };
}

async function serverRow(shopId, table, rowId) {
  const r = await db.one(
    `SELECT data, deleted_at FROM sync_rows WHERE app='shop' AND account_kind='shop' AND account_id=$1
       AND table_name=$2 AND row_id=$3`, [shopId, table, rowId]);
  if (!r) return null;
  return { data: typeof r.data === 'string' ? JSON.parse(r.data) : r.data, deleted: r.deleted_at !== null };
}

/** دستگاهِ دیگری که فقط یک op می‌فرستد (همان مسیرِ برنامه) */
async function otherPush(acc, deviceId, ops) {
  const r = await h.api('POST', '/api/sync/v1/push', {
    token: acc.token, headers: { 'X-App': 'shop', 'X-Device': deviceId },
    body: { device_id: deviceId, schema_version: 3, ops },
  });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.ok(r.body.results.every(x => x.status === 'applied'), JSON.stringify(r.body.results));
}
let n = 0;
const opId = () => `01JW${String(Date.now()).slice(-9)}${String(++n).padStart(5, '0')}`;

/** دو دستگاه با یک کالای پایه، همگام */
async function start(label) {
  const acc = await account(label);
  const ls = storage();
  const ledgerBox = { data: { products: [{ id: 'p-1', name: 'چای', price: 100 }] } };
  const web = browser({ ...acc, deviceId: `${label}-web`, ls, ledgerBox });
  assert.equal((await web.sync.firstSync()).did, 'baseline');
  assert.equal(web.sync.queueSize(), 0, 'پایه رفت');
  return { acc, ls, ledgerBox, web };
}

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
});
test.after(async () => { await h.stop(); });

test('۱) حافظهٔ پر ⇒ ثبت نمی‌گیرد؛ pull ِ op ِ دستگاهِ دیگر؛ حافظه آزاد ⇒ ثبتِ بعدی تغییرِ جامانده را هم به سرور می‌برد', async () => {
  const { acc, ls, web } = await start('unrec1');
  ls.full = true;
  assert.equal(web.save((d) => { d.products.push({ id: 'p-2', name: 'قند', price: 50 }); }), 0, 'ثبت نگرفت');
  assert.match(web.sync.status().lastError, /حافظهٔ مرورگر پر است/);
  await otherPush(acc, 'unrec1-phone', [{ op_id: opId(), ts: Date.now(), table: 'products', row_id: 'p-9', type: 'insert', fields: { name: 'نان', price: 10 } }]);
  await web.sync.sync();                              // push (هیچ) + pull ِ نان
  assert.ok(web.row('products', 'p-9'), 'op ِ دستگاهِ دیگر نشست');
  assert.ok(web.row('products', 'p-2'), 'قند هنوز روی همین مرورگر است');

  ls.full = false;
  web.save((d) => { d.products.find(p => p.id === 'p-1').price = 120; });
  await web.sync.sync();
  assert.equal(web.sync.queueSize(), 0);
  assert.deepEqual((await serverRow(acc.shopId, 'products', 'p-2'))?.data, { name: 'قند', price: 50 }, 'قند به سرور رسید');
  assert.equal((await serverRow(acc.shopId, 'products', 'p-1')).data.price, 120);
});

test('۲) op ِ رسیده همان ردیف را عوض کرده (فیلدِ دیگر) ⇒ هر دو فیلد می‌مانند و تغییرِ جامانده به سرور می‌رسد', async () => {
  const { acc, ls, web } = await start('unrec2');
  ls.full = true;
  web.save((d) => { d.products.find(p => p.id === 'p-1').price = 150; });
  await otherPush(acc, 'unrec2-phone', [{ op_id: opId(), ts: Date.now(), table: 'products', row_id: 'p-1', type: 'update', fields: { name: 'چای سبز' } }]);
  await web.sync.sync();
  assert.deepEqual(web.row('products', 'p-1'), { id: 'p-1', name: 'چای سبز', price: 150 });
  ls.full = false;
  web.save(() => { });                                // هر ذخیرهٔ بعدی (حتی بی تغییرِ تازه)
  await web.sync.sync();
  assert.deepEqual((await serverRow(acc.shopId, 'products', 'p-1')).data, { name: 'چای سبز', price: 150 });
});

test('۳) بی تغییرِ ثبت‌نشده ⇒ سایه پس از pull همان دفتر است و ذخیرهٔ بعدی هیچ op ِ اضافه نمی‌سازد', async () => {
  const { acc, web } = await start('unrec3');
  await otherPush(acc, 'unrec3-phone', [{ op_id: opId(), ts: Date.now(), table: 'products', row_id: 'p-1', type: 'update', fields: { price: 130 } }]);
  await web.sync.sync();
  assert.equal(web.row('products', 'p-1').price, 130);
  assert.equal(web.save(() => { }), 0, 'هیچ op');
  assert.equal(web.sync.queueSize(), 0);
});

test('۴) بستن و باز کردنِ مرورگر میانِ ثبتِ ناموفق و pull ⇒ تغییرِ جامانده باز هم به سرور می‌رسد', async () => {
  const { acc, ls, ledgerBox, web } = await start('unrec4');
  ls.full = true;
  web.save((d) => { d.products.push({ id: 'p-4', name: 'روغن', price: 300 }); });
  const again = browser({ ...acc, deviceId: 'unrec4-web', ls, ledgerBox });   // همان حافظه، موتورِ تازه
  await otherPush(acc, 'unrec4-phone', [{ op_id: opId(), ts: Date.now(), table: 'products', row_id: 'p-8', type: 'insert', fields: { name: 'برنج', price: 80 } }]);
  await again.sync.sync();
  const reopened = browser({ ...acc, deviceId: 'unrec4-web', ls, ledgerBox });
  ls.full = false;
  reopened.save(() => { });
  await reopened.sync.sync();
  assert.deepEqual((await serverRow(acc.shopId, 'products', 'p-4'))?.data, { name: 'روغن', price: 300 });
  assert.equal(web.sync.queueSize(), 0);
});
