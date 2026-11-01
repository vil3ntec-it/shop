'use strict';
/**
 * `since` ِ push از برنامهٔ وب — دنبالهٔ #481 (پرچمِ دکانِ `sync.cursor_from_since`).
 *
 * سرورِ #481 با پرچم، `since` ِ بدنهٔ push را ملاکِ «این دستگاه نوشتهٔ دیگری را دیده بود؟» ِ تعارض می‌گیرد.
 * فرستادنِ cursor ِ **همین حالا** کافی نیست: opی که وسطِ یک pull ثبت شد پیش از دیدنِ opهای همان pull ساخته
 * شده؛ اگر cursor ِ پس از pull برود، سرور خیال می‌کند دستگاه دیده و تعارض ثبت نمی‌شود (بدتر از نفرستادن،
 * که «تأییدِ pull ِ پیشین» بود). پس برنامه «یادداشت» نگه می‌دارد: cursor ِ لحظه‌ای که صفِ خالی نخستین op را
 * گرفت، تا خالی شدنِ صف فقط پایین — و push کم‌ترینِ آن و cursor را می‌فرستد (`PushSince` در core، قرینهٔ
 * `pushSinceNote`/`pushSince` در `license/sync-core.js`).
 *
 * کدِ زیرِ آزمون همان موتورِ مرورگر است (`sync-engine.js` در vm با localStorage ِ خودش) روبه‌روی سرورِ واقعی؛
 * بدنهٔ هر push ثبت می‌شود.
 *   ۰) بردارهای `core/test-vectors/push-since-v1.json` (همان که `PushSinceVectorsTest` ِ Kotlin می‌خواند)
 *   ۱) گردشِ عادی (صفِ خالی، ثبت، push، pull) ⇒ since = cursor
 *   ۲) ثبت **وسطِ pull** ⇒ push ِ بعدی since ِ پیش از آن pull را می‌فرستد، نه پس از آن
 *   ۳) push ِ نیمه‌کاره (قطع) + بستن و باز کردنِ مرورگر + ثبتِ تازه روی صفِ پر ⇒ یادداشت پایین نمی‌رود بالا
 *   ۴) sync-core.js ِ کهنه (پوستهٔ stale-while-revalidate) ⇒ ثبت و push بی خطا، بی `since`
 *   ۵) با سرورِ #481 و پرچم: همان «ثبتِ وسطِ pull» ⇒ تعارض ثبت می‌شود (بی #481 روی این شاخه: رد، با دلیل)
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const h = require('./helpers');
const db = require('../src/db');
const syncV1 = require('../src/lib/sync-v1');

const ROOT = path.join(__dirname, '..', '..');
const core = require(path.join(ROOT, 'license', 'sync-core.js'));
const ENGINE_SRC = fs.readFileSync(path.join(ROOT, 'license', 'sync-engine.js'), 'utf8');
const VECTORS = JSON.parse(fs.readFileSync(path.join(ROOT, 'core', 'test-vectors', 'push-since-v1.json'), 'utf8'));
const SERVER_ACK = fs.readFileSync(path.join(__dirname, '..', 'src', 'lib', 'sync-v1.js'), 'utf8').includes("'sync.cursor_from_since'");

/** یک مرورگر: موتورِ واقعی در vm؛ `hook(p, o)` پیش از هر درخواست صدا زده می‌شود؛ بدنهٔ push‌ها در `pushes` */
function browser({ token, userId, deviceId, ls, ledgerBox, coreImpl = core, hook = null }) {
  const pushes = [];
  const window = {
    TohidSyncCore: coreImpl,
    TohidApiConfig: { baseUrl: () => 'http://127.0.0.1' },
    TohidAccount: {
      configured: () => true, isSignedIn: () => true, account: () => ({ userId }),
      deviceId: () => deviceId, appId: () => 'shop', on() { }, heartbeat: async () => ({}),
      accessToken: async () => token,
      async authFetch(p, o = {}) {
        if (hook) await hook(p, o);
        if (p === '/api/sync/v1/push') pushes.push(JSON.parse(JSON.stringify(o.body)));
        const r = await h.api(o.method || 'GET', p, { token, body: o.body, headers: { 'X-App': 'shop', 'X-Device': deviceId } });
        if (r.status >= 400) throw Object.assign(new Error(r.body?.error || `http ${r.status}`), { status: r.status, code: r.body?.code });
        return r.body;
      },
    },
    addEventListener() { }, dispatchEvent() { },
  };
  const ctx = vm.createContext({
    window, localStorage: ls, navigator: { onLine: true }, document: { addEventListener() { } },
    URL, console, setTimeout: () => 0, clearTimeout() { }, setInterval: () => 0, clearInterval() { },
  });
  vm.runInContext(ENGINE_SRC, ctx);
  const sync = window.TohidSync;
  sync.bind({ read: () => JSON.parse(JSON.stringify(ledgerBox.data)), write: (d) => { ledgerBox.data = JSON.parse(JSON.stringify(d)); } });
  return {
    sync, pushes,
    save(mutate) {
      const d = JSON.parse(JSON.stringify(ledgerBox.data));
      mutate(d);
      ledgerBox.data = d;
      return sync.recordChange('save');
    },
    row: (t, id) => (ledgerBox.data[t] || []).find(r => String(r.id) === String(id)) || null,
  };
}

function storage() {
  const box = new Map();
  return { box, getItem: (k) => (box.has(k) ? box.get(k) : null), setItem: (k, v) => { box.set(k, String(v)); }, removeItem: (k) => box.delete(k) };
}

async function shop(label) {
  const u = await h.newUser(`صاحب-${label}`, 'shop');
  const made = await h.api('POST', '/api/shop', { token: u.accessToken, body: { name: `دکان ${label}` } });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { token: u.accessToken, userId: `${u.user?.id || label}`, shopId: made.body.shop.id };
}

let n = 0;
const opId = () => `01JS${String(Date.now()).slice(-9)}${String(++n).padStart(5, '0')}`;
/** دستگاهِ دیگر (همان مسیرِ برنامه) */
async function other(acc, device, ops) {
  const r = await h.api('POST', '/api/sync/v1/push', { token: acc.token, headers: { 'X-App': 'shop', 'X-Device': device },
    body: { device_id: device, schema_version: 3, ops } });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body;
}
const price = (id, v) => ({ op_id: opId(), ts: Date.now(), table: 'products', row_id: id, type: 'update', fields: { price: v } });
const headOf = async (shopId) => Number((await db.one(
  `SELECT COALESCE(MAX(server_seq),0)::bigint AS h FROM oplog WHERE app='shop' AND account_kind='shop' AND account_id=$1`, [shopId])).h);

/** مرورگرِ A با یک کالا، همگام */
async function start(label, opts = {}) {
  const acc = await shop(label);
  const ls = storage();
  const ledgerBox = { data: { products: [{ id: 'p-1', name: 'چای', price: 100 }] } };
  const web = browser({ ...acc, deviceId: `${label}-web`, ls, ledgerBox, ...opts });
  assert.equal((await web.sync.firstSync()).did, 'baseline');
  assert.equal(web.sync.queueSize(), 0);
  return { acc, ls, ledgerBox, web };
}

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
});
test.after(async () => { await h.stop(); });

test('۰) pushSinceNote و pushSince = بردارهای مشترک با Kotlin (core/test-vectors/push-since-v1.json)', () => {
  assert.ok(VECTORS.note.length >= 9 && VECTORS.since.length >= 6);
  for (const r of VECTORS.note) assert.equal(core.pushSinceNote(r.previous, r.empty, r.cursor), r.want, JSON.stringify(r));
  for (const r of VECTORS.since) assert.equal(core.pushSince(r.noted, r.cursor), r.want, JSON.stringify(r));
  //  ورودیِ نادرست (رشتهٔ غیرعددی، اعشاری، null) = «یادداشتِ نبوده»
  for (const bad of [null, undefined, 'x', 1.5, -2, true, {}]) assert.equal(core.pushSince(bad, 9), 9, String(bad));
  assert.equal(core.pushSinceNote('abc', false, 9), 9);
});

test('۱) گردشِ عادی: ثبت روی صفِ خالی ⇒ since = cursor ِ همان لحظه (پس از هر pull)', async () => {
  const { acc, web } = await start('ps1');
  await other(acc, 'ps1-phone', [price('p-1', 110)]);
  await web.sync.sync();                                     // pull ⇒ cursor = سرِ دفتر
  const cursor = web.sync.status().cursor;
  assert.equal(cursor, await headOf(acc.shopId));
  web.save((d) => { d.products[0].name = 'چای سبز'; });
  await web.sync.sync();
  const last = web.pushes.at(-1);
  assert.equal(last.since, cursor);
  assert.equal(web.sync.queueSize(), 0);
});

test('۲) ثبت **وسطِ pull** ⇒ push ِ بعدی since ِ پیش از آن pull را می‌فرستد', async () => {
  let during = null;
  const { acc, web } = await start('ps2', {
    hook: async (p) => { if (p.startsWith('/api/sync/v1/pull') && during) { const f = during; during = null; f(); } },
  });
  const before = web.sync.status().cursor;
  await other(acc, 'ps2-phone', [price('p-1', 300)]);
  //  کاربر درست وقتی pull در راه است ذخیره می‌کند (همان پنجرهٔ واقعیِ `await pullOnce()`)
  during = () => web.save((d) => { d.products[0].price = 200; });
  await web.sync.sync();
  assert.ok(web.sync.status().cursor > before, 'pull جلو رفت');
  assert.equal(web.sync.queueSize(), 1, 'ثبتِ وسطِ pull هنوز در صف است');
  await web.sync.sync();
  assert.equal(web.pushes.at(-1).since, before, 'since = cursor ِ پیش از pull، نه پس از آن');
  assert.equal(web.sync.queueSize(), 0);
});

test('۳) push ِ نیمه‌کاره + بستن و باز کردن + ثبت روی صفِ پر ⇒ یادداشت فقط پایین؛ صفِ خالی ⇒ از نو', async () => {
  let failNext = false;
  const hook = async (p) => { if (p === '/api/sync/v1/push' && failNext) { failNext = false; throw Object.assign(new Error('قطع'), { code: 'net' }); } };
  const { acc, ls, ledgerBox, web } = await start('ps3', { hook });
  const c0 = web.sync.status().cursor;
  web.save((d) => { d.products.push({ id: 'p-2', name: 'قند', price: 50 }); });
  failNext = true;
  await web.sync.sync();                                     // push شکست؛ صف ماند
  assert.equal(web.sync.queueSize(), 1);
  await other(acc, 'ps3-phone', [price('p-1', 120)]);
  const reopened = browser({ ...acc, deviceId: 'ps3-web', ls, ledgerBox, hook });
  //  pull ِ جدا (بی push) ⇒ cursor جلو؛ صف هنوز پر
  await reopened.sync.pullOnce();
  assert.ok(reopened.sync.status().cursor > c0);
  reopened.save((d) => { d.products[0].name = 'چای ۲'; });   // ثبت روی صفِ پر
  await reopened.sync.sync();
  assert.equal(reopened.pushes.at(-1).since, c0, 'یادداشتِ صفِ پر بالا نمی‌رود');
  assert.equal(reopened.sync.queueSize(), 0);
  const c1 = reopened.sync.status().cursor;
  assert.ok(c1 > c0);
  reopened.save((d) => { d.products[0].price = 130; });     // صفِ خالی ⇒ از نو
  await reopened.sync.sync();
  assert.equal(reopened.pushes.at(-1).since, c1, 'صفِ خالی ⇒ یادداشتِ تازه = cursor ِ همان لحظه');
});

test('۴) sync-core.js ِ کهنه (بی pushSince) ⇒ ثبت و push بی خطا، بی کلیدِ since', async () => {
  const old = Object.assign(Object.create(core), { pushSinceNote: undefined, pushSince: undefined });
  const { acc, web } = await start('ps4', { coreImpl: old });
  assert.equal(web.save((d) => { d.products[0].price = 101; }), 1);
  await web.sync.sync();
  assert.equal(web.sync.status().lastError, '');
  assert.equal(Object.prototype.hasOwnProperty.call(web.pushes.at(-1), 'since'), false);
  assert.equal(web.sync.queueSize(), 0);
  void acc;
});

test('۵) با سرورِ #481 و پرچمِ دکان: ثبتِ وسطِ pull ⇒ تعارض ثبت می‌شود (بازنده ۳۰۰ ِ گوشی، برنده ۲۰۰ ِ وب)', {
  skip: SERVER_ACK ? false : 'سرورِ این شاخه `sync.cursor_from_since` (#481) را ندارد',
}, async () => {
  let during = null;
  const { acc, web } = await start('ps5', {
    hook: async (p) => { if (p.startsWith('/api/sync/v1/pull') && during) { const f = during; during = null; f(); } },
  });
  await db.query(`INSERT INTO live_config (scope, key, value, updated_at) VALUES ($1,'sync.cursor_from_since','true',1)
    ON CONFLICT (scope, key) DO UPDATE SET value = excluded.value`, [String(acc.shopId)]);
  await other(acc, 'ps5-phone', [price('p-1', 300)]);
  during = () => web.save((d) => { d.products[0].price = 200; });
  await web.sync.sync();                                     // pull ِ ۳۰۰ رسید؛ ۲۰۰ وسطش ثبت شد
  await web.sync.sync();                                     // push ِ ۲۰۰ با since ِ پیش از pull
  //  گوشیِ این آزمون هرگز pull نکرده (تأییدِ ۰) ⇒ پایهٔ ۱۰۰ ِ وب برایش «ندیده» است؛ آن تعارض موضوعِ این‌جا نیست
  const cf = await db.many(`SELECT field, loser_value, winner_value, loser_device, winner_device FROM sync_conflicts
    WHERE app='shop' AND account_id=$1 AND row_id='p-1' AND winner_device='ps5-web'`, [acc.shopId]);
  assert.equal(cf.length, 1, 'تعارض گم نشد');
  assert.deepEqual([cf[0].field, cf[0].loser_value, cf[0].winner_value, cf[0].loser_device, cf[0].winner_device],
    ['price', 300, 200, 'ps5-phone', 'ps5-web']);
});
