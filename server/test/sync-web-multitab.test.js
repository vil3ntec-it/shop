'use strict';
/**
 * وب (`license/sync-engine.js`): دو برگه در یک مرورگر — فقط یک برگه نویسنده است (`TohidTabLock`).
 *
 * یافتهٔ بازبینِ مستقل (روی main، #506): سایه، صف و cursor در localStorage ِ مشترک‌اند ولی هر برگه دفترِ
 * حافظهٔ خودش را دارد. برگهٔ A کالای p-2 را ثبت و push می‌کند؛ برگهٔ B با حافظهٔ کهنه (با pull یا بی آن)
 * هر چیزی ذخیره می‌کند ⇒ تفاضلِ B با سایهٔ مشترک p-2 را «حذف‌شده» می‌بیند ⇒ DELETE ِ p-2 به سرور و همهٔ
 * دستگاه‌ها. اکنون برگهٔ دوم با Web Locks فقط‌خواندنی است: نه ثبت، نه push/pull؛ نویسنده که بسته شد،
 * برگهٔ منتظر تازه می‌شود. کدِ زیرِ آزمون همان دو فایلِ مرورگر است در `vm`، روبه‌روی سرورِ واقعی؛ مدیرِ قفلِ
 * ساختگی همان رفتارِ `navigator.locks` ِ مرورگر است (مشترک میانِ برگه‌های یک مرورگر).
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
const core = require(path.join(LICENSE, 'sync-core.js'));
const ENGINE_SRC = fs.readFileSync(path.join(LICENSE, 'sync-engine.js'), 'utf8');

function storage() {
  const box = new Map();
  return {
    getItem: (k) => (box.has(k) ? box.get(k) : null),
    setItem: (k, v) => { box.set(k, String(v)); },
    removeItem: (k) => { box.delete(k); },
  };
}

/** مدیرِ قفلِ یک مرورگر (`navigator.locks`): `forTab(id)` همان شیء برای هر برگه؛ `close(id)` = بستنِ برگه */
function lockManager() {
  const held = new Map();      // name ⇒ tabId
  const queue = new Map();     // name ⇒ [{ tab, run }]
  function grant(name, tab, cb) {
    held.set(name, tab);
    return Promise.resolve().then(() => cb({ name }));
  }
  return {
    forTab(tab) {
      return {
        request(name, opts, cb) {
          if (typeof opts === 'function') { cb = opts; opts = {}; }
          if (held.has(name)) {
            if (opts.ifAvailable) return Promise.resolve().then(() => cb(null));
            return new Promise((resolve) => {
              if (!queue.has(name)) queue.set(name, []);
              queue.get(name).push({ tab, run: () => resolve(grant(name, tab, cb)) });
            });
          }
          return grant(name, tab, cb);
        },
      };
    },
    close(tab) {
      for (const [name, owner] of [...held]) {
        if (owner !== tab) continue;
        held.delete(name);
        const next = (queue.get(name) || []).filter((w) => w.tab !== tab);
        queue.set(name, next);
        const w = next.shift();
        if (w) w.run();
      }
    },
    holder: (name) => held.get(name) || null,
  };
}

/** یک برگه: همان موتور، localStorage ِ مشترک، دفترِ حافظهٔ خودش */
function tab({ token, userId, deviceId, ls, ledgerBox, locks, id }) {
  const reloads = [];
  const window = {
    TohidSyncCore: core,
    TohidApiConfig: { baseUrl: () => 'http://127.0.0.1' },
    TohidAccount: {
      configured: () => true, isSignedIn: () => true, account: () => ({ userId }),
      deviceId: () => deviceId, appId: () => 'shop', on() { }, heartbeat: async () => ({}),
      accessToken: async () => token,
      async authFetch(p, o = {}) {
        const r = await h.api(o.method || 'GET', p, { token, body: o.body, headers: { 'X-App': 'shop', 'X-Device': deviceId } });
        if (r.status >= 400) { const e = new Error(r.body?.error || `http ${r.status}`); e.status = r.status; throw e; }
        return r.body;
      },
    },
    addEventListener() { },
    dispatchEvent() { },
  };
  const navigator = { onLine: true };
  if (locks) navigator.locks = locks.forTab(id);
  const ctx = vm.createContext({
    window, localStorage: ls, navigator, document: { addEventListener() { } },
    location: { reload: () => reloads.push(id) },
    URL, console, setTimeout: () => 0, clearTimeout() { }, setInterval: () => 0, clearInterval() { },
    CustomEvent: class { constructor(t, o) { this.type = t; this.detail = o && o.detail; } },
  });
  vm.runInContext(ENGINE_SRC, ctx);
  const sync = window.TohidSync;
  sync.bind({ read: () => JSON.parse(JSON.stringify(ledgerBox.data)), write: (d) => { ledgerBox.data = JSON.parse(JSON.stringify(d)); } });
  return {
    sync, lock: window.TohidTabLock, reloads,
    save(mutate) {
      const d = JSON.parse(JSON.stringify(ledgerBox.data));
      mutate(d);
      ledgerBox.data = d;
      return sync.recordChange('save');
    },
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

async function otherPush(acc, deviceId, ops) {
  const r = await h.api('POST', '/api/sync/v1/push', {
    token: acc.token, headers: { 'X-App': 'shop', 'X-Device': deviceId },
    body: { device_id: deviceId, schema_version: 3, ops },
  });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.ok(r.body.results.every((x) => x.status === 'applied'), JSON.stringify(r.body.results));
}
let n = 0;
const opId = () => `01JW${String(Date.now()).slice(-9)}${String(++n).padStart(5, '0')}`;

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
});
test.after(async () => { await h.stop(); });

/** سناریوی بازبین: A ثبت و push می‌کند؛ B ِ کهنه (با pull یا بی آن) ذخیره می‌کند */
async function twoTabs(label, pullFirst) {
  const acc = await account(label);
  const ls = storage();
  const locks = lockManager();
  const boxA = { data: { products: [{ id: 'p-1', name: 'چای', price: 100 }] } };
  const A = tab({ ...acc, deviceId: `${label}-web`, ls, ledgerBox: boxA, locks, id: 'A' });
  assert.equal(await A.lock.ready, 'writer');
  assert.equal((await A.sync.firstSync()).did, 'baseline');
  const boxB = { data: JSON.parse(JSON.stringify(boxA.data)) };
  const B = tab({ ...acc, deviceId: `${label}-web`, ls, ledgerBox: boxB, locks, id: 'B' });
  assert.equal(await B.lock.ready, 'reader');
  assert.equal(B.lock.readOnly(), true, 'صفحه ذخیره نمی‌کند');

  A.save((d) => { d.products.push({ id: 'p-2', name: 'قند', price: 50 }); });
  await A.sync.sync();
  assert.ok(await serverRow(acc.shopId, 'products', 'p-2'), 'قندِ برگهٔ A روی سرور');
  await otherPush(acc, `${label}-phone`, [{ op_id: opId(), ts: Date.now(), table: 'products', row_id: 'p-9', type: 'insert', fields: { name: 'نان', price: 10 } }]);
  const queueBefore = A.sync.queueSize();
  if (pullFirst) assert.equal(await B.sync.pullOnce(), 0, 'برگهٔ فقط‌خواندنی pull نمی‌کند');
  if (pullFirst) await B.sync.sync();
  assert.equal(B.save((d) => { d.products.find((p) => p.id === 'p-1').price = 120; }), 0, 'برگهٔ فقط‌خواندنی op نمی‌سازد');
  await B.sync.sync();
  assert.equal(A.sync.queueSize(), queueBefore, 'صفِ مشترک دست نخورد');
  const st = B.sync.status();
  assert.equal(st.otherTab, true);
  assert.match(st.lastError, /برگهٔ دیگری/);
  await assert.rejects(B.sync.restoreFromServer(), (e) => e.code === 'other_tab');
  assert.equal((await B.sync.firstSync()).did, 'other_tab');
  return { acc, ls, locks, A, B, boxA };
}

test('بازبین (وارونه): برگهٔ B پس از pull ذخیره می‌کند ⇒ p-2 روی سرور پاک نمی‌شود', async () => {
  const { acc } = await twoTabs('mt1', true);
  const row = await serverRow(acc.shopId, 'products', 'p-2');
  assert.ok(row && row.deleted === false, JSON.stringify(row));
});

test('بازبین (وارونه): برگهٔ B بی pull ذخیره می‌کند ⇒ p-2 روی سرور پاک نمی‌شود', async () => {
  const { acc } = await twoTabs('mt2', false);
  const row = await serverRow(acc.shopId, 'products', 'p-2');
  assert.ok(row && row.deleted === false, JSON.stringify(row));
});

test('نویسنده بسته شد ⇒ برگهٔ منتظر تازه می‌شود؛ برگهٔ تازه نویسنده است و تغییرش می‌رود، بی حذفِ p-2', async () => {
  const { acc, ls, locks, B, boxA } = await twoTabs('mt3', true);
  locks.close('A');
  await new Promise((r) => setImmediate(r));
  assert.deepEqual(B.reloads, ['B'], 'برگهٔ کهنه خودش تازه شد');
  assert.equal(locks.holder('tohid-ledger-writer'), 'B');
  //  همان برگه پس از تازه‌سازی: دفتر از حافظهٔ ذخیره‌شده (همان دفترِ A)، نویسنده
  locks.close('B');
  const C = tab({ ...acc, deviceId: 'mt3-web', ls, ledgerBox: boxA, locks, id: 'C' });
  assert.equal(await C.lock.ready, 'writer');
  await C.sync.sync();
  assert.ok(C.save((d) => { d.products.find((p) => p.id === 'p-1').price = 130; }) > 0);
  await C.sync.sync();
  assert.equal((await serverRow(acc.shopId, 'products', 'p-1')).data.price, 130);
  const p2 = await serverRow(acc.shopId, 'products', 'p-2');
  assert.ok(p2 && p2.deleted === false, JSON.stringify(p2));
  assert.ok(await serverRow(acc.shopId, 'products', 'p-9'), 'کالای گوشی هم سرِ جایش');
});

test('تا روشن شدنِ نقش (چند میلی‌ثانیه) همان رفتارِ امروز؛ بی Web Locks هم همان: نویسنده', async () => {
  const acc = await account('mt4');
  const ls = storage();
  const locks = lockManager();
  const box = { data: { products: [{ id: 'p-1', name: 'چای', price: 100 }] } };
  const A = tab({ ...acc, deviceId: 'mt4-web', ls, ledgerBox: box, locks, id: 'A' });
  assert.equal(await A.lock.ready, 'writer');
  assert.equal((await A.sync.firstSync()).did, 'baseline');
  locks.close('A');
  //  برگهٔ تازه پیش از پاسخِ قفل: ذخیره و ثبت مثلِ امروز
  const P = tab({ ...acc, deviceId: 'mt4-web', ls, ledgerBox: box, locks, id: 'P' });
  assert.equal(P.lock.role(), 'pending');
  assert.equal(P.lock.readOnly(), false, 'صفحه ذخیره را نمی‌بندد');
  assert.equal(P.save((d) => { d.products.push({ id: 'p-3', name: 'نمک', price: 5 }); }), 1);
  assert.equal(await P.lock.ready, 'writer');
  await P.sync.sync();
  assert.ok(await serverRow(acc.shopId, 'products', 'p-3'));
  //  بی Web Locks (مرورگرِ کهنه): نویسنده، همان امروز
  const old = tab({ ...acc, deviceId: 'mt4-web', ls, ledgerBox: box, locks: null, id: 'O' });
  assert.equal(old.lock.role(), 'writer');
  assert.equal(old.lock.readOnly(), false);
  assert.equal(old.sync.status().otherTab, false);
});
