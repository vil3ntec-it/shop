'use strict';
/**
 * «تغییرِ کاربر روی دستگاه گم نشود» — سه یافتهٔ ممیزیِ SYN در Sync v1 (برنامه‌ها؛ وب همان کد را دارد):
 *
 *   ۱) صف بالای ۵۰٬۰۰۰ op کهنه‌ترین‌ها را `queue_overflow` می‌کرد و هرگز به سرور نمی‌رسیدند ⇐ هیچ سقفی؛
 *      از `QUEUE_WARN` پیامِ «X تغییر هنوز به سرور نرسیده» (`core/…/core/sync/OpBacklog.kt`).
 *   ۲) ویرایشی که وسطِ یک pull ثبت شد با opِ رسیده روی خودِ دستگاه بازنویسی می‌شد ⇐ پرچمِ دکانِ
 *      `sync.keep_pending` (`keep_pending` در پاسخِ pull؛ `core.keepPending`؛ `KeepPending.kt`).
 *   ۳) ردیفی که دستگاهِ دیگر پس از حذف زنده کرد روی دستگاهی که حذف را دیده نیمه‌ای می‌نشست ⇐ پرچمِ
 *      دکانِ `sync.revive_full_row` (`row` ِ کامل روی op ِ زنده‌کننده؛ `server/src/lib/sync-v1-keep.js`).
 *
 * کدِ زیرِ آزمون همان موتورِ مرورگر است (`license/sync-engine.js` در vm با localStorage ِ خودش) روبه‌روی
 * سرورِ واقعی؛ «گوشی» opهای خام را با همان مسیرِ برنامه می‌فرستد. هر سناریو بی پرچم هم سنجیده می‌شود
 * (رفتارِ امروز، مرجع) تا روشن باشد چه چیزی عوض شد.
 *
 *   ۰) بردارهای مشترک با Kotlin
 *   ۱) ۶۰٬۰۰۰ op همه می‌رسند؛ موتور: پیامِ صفِ بزرگ و خالی شدن با دسته‌های ۲۰۰تایی؛ حافظهٔ پر هیچ op ِ کهنه‌ای را نمی‌برد
 *   ۲) ویرایشِ وسطِ pull می‌ماند و به سرور می‌رسد
 *   ۳) حذفِ وسطِ pull ⇒ شبح نه؛ ویرایشِ وسطِ pull و حذفِ رسیده ⇒ ردیف می‌ماند (سرور زنده‌اش می‌کند)
 *   ۴) پول: دریافتِ وسطِ pull با دریافت و «اصلاحِ» دستگاهِ دیگر ⇒ برابرِ سرور
 *   ۵) زنده شدن پس از حذف ⇒ ردیفِ کامل (ویرایش، `$inc`، بازگردانیِ پنل، op ِ پس از آن، دو بار حذف و زنده)
 *   ۶) پاسخِ خام: فقط op ِ زنده‌کننده `row` دارد؛ صفحه‌بندی؛ پمپ و دکانِ بی پرچم همان پاسخِ پیشین
 *   ۷) پرچمِ `sync.gzip_pull`: pull ِ بزرگ با `Accept-Encoding: gzip` فشرده و همان JSON؛ بی سرآیند یا بی پرچم ساده
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const h = require('./helpers');
const db = require('../src/db');
const syncV1 = require('../src/lib/sync-v1');

const ROOT = path.join(__dirname, '..', '..');
const core = require(path.join(ROOT, 'license', 'sync-core.js'));
const ENGINE = fs.readFileSync(path.join(ROOT, 'license', 'sync-engine.js'), 'utf8');
const vectors = (name) => JSON.parse(fs.readFileSync(path.join(ROOT, 'core', 'test-vectors', name), 'utf8'));

const KEEP = 'sync.keep_pending';
const REVIVE = 'sync.revive_full_row';
const GZIP = 'sync.gzip_pull';

const setFlag = (scope, key, on) => db.query(
  `INSERT INTO live_config (scope, key, value, updated_at) VALUES ($1,$2,$3,1)
   ON CONFLICT (scope, key) DO UPDATE SET value = excluded.value`,
  [String(scope), key, on ? 'true' : 'false']
);

let n = 0;
const oid = () => `01JK${String(Date.now()).slice(-9)}${String(++n).padStart(6, '0')}`;

/** localStorage ِ ساده؛ `full` ⇒ نوشتنِ صف خطای سهمیه می‌دهد (همان مرورگر) */
function storage() {
  const box = new Map();
  const st = {
    full: false, box,
    getItem: (k) => (box.has(k) ? box.get(k) : null),
    setItem: (k, v) => {
      if (st.full && /-oplog$/.test(k)) { const e = new Error('QuotaExceededError'); e.name = 'QuotaExceededError'; throw e; }
      box.set(k, String(v));
    },
    removeItem: (k) => { box.delete(k); },
  };
  return st;
}

/**
 * یک مرورگر: همان موتور در vm. `fetch(p, o)` به‌جای سرورِ واقعی (اگر داده شود)؛ `hook(p, o)` پیش از هر درخواست
 * (برای «وسطِ pull»). زمان‌سنج‌ها هرگز نمی‌دوند: آزمون خودش `sync()` می‌زند.
 */
function browser({ token = '', userId = 'u', deviceId, ls = storage(), ledgerBox = { data: {} }, hook = null, fetch = null }) {
  const window = {
    TohidSyncCore: core,
    TohidApiConfig: { baseUrl: () => 'http://127.0.0.1' },
    TohidAccount: {
      configured: () => true, isSignedIn: () => true, account: () => ({ userId }),
      deviceId: () => deviceId, appId: () => 'shop', on() { }, heartbeat: async () => ({}),
      accessToken: async () => token,
      async authFetch(p, o = {}) {
        if (hook) await hook(p, o);
        if (fetch) return fetch(p, o);
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
    CustomEvent: class { constructor(t, o) { this.type = t; this.detail = o && o.detail; } },
  });
  vm.runInContext(ENGINE, ctx, { filename: 'sync-engine.js' });
  const sync = window.TohidSync;
  sync.bind({ read: () => JSON.parse(JSON.stringify(ledgerBox.data)), write: (d) => { ledgerBox.data = JSON.parse(JSON.stringify(d)); } });
  return {
    sync, ls, ledgerBox,
    /** همان `saveState()` ِ صفحه: دفتر، بعد `recordChange` */
    save(mutate) {
      const d = JSON.parse(JSON.stringify(ledgerBox.data));
      mutate(d);
      ledgerBox.data = d;
      return sync.recordChange('save');
    },
    row: (t, id) => (ledgerBox.data[t] || []).find((r) => String(r.id) === String(id)) || null,
  };
}

async function shop(label) {
  const u = await h.newUser(`صاحب-${label}`, 'shop');
  const made = await h.api('POST', '/api/shop', { token: u.accessToken, body: { name: `دکان ${label}` } });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { token: u.accessToken, shopId: made.body.shop.id, userId: `u-${label}` };
}

/** «گوشی»: opهای خام با همان مسیرِ برنامه */
async function phone(acc, ops, device = 'phone') {
  const r = await h.api('POST', '/api/sync/v1/push', {
    token: acc.token, headers: { 'X-App': 'shop', 'X-Device': device },
    body: { device_id: device, schema_version: core.SCHEMA_VERSION, ops },
  });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.ok(r.body.results.every((x) => x.status === 'applied'), JSON.stringify(r.body.results));
}
const upd = (table, rowId, fields) => ({ op_id: oid(), ts: Date.now(), table, row_id: rowId, type: 'update', fields });
const del = (table, rowId) => ({ op_id: oid(), ts: Date.now(), table, row_id: rowId, type: 'delete' });

async function serverRow(shopId, table, rowId) {
  const r = await db.one(
    `SELECT data, deleted_at FROM sync_rows WHERE app='shop' AND account_kind='shop' AND account_id=$1
       AND table_name=$2 AND row_id=$3`, [shopId, table, rowId]);
  if (!r) return null;
  return { data: typeof r.data === 'string' ? JSON.parse(r.data) : r.data, deleted: r.deleted_at !== null };
}

/** ردیفِ مرورگر برابرِ سرور (یا هر دو نیست/حذف) */
async function sameAsServer(web, acc, table, rowId, label) {
  const s = await serverRow(acc.shopId, table, rowId);
  const mine = web.row(table, rowId);
  if (!s || s.deleted) { assert.equal(mine, null, `${label}: سرور ندارد ولی مرورگر دارد ${JSON.stringify(mine)}`); return; }
  assert.deepEqual(mine, { ...s.data, id: rowId }, `${label}: مرورگر با سرور فرق دارد`);
}

/** مرورگرِ همگام با `data`؛ `flags` روی همین دکان */
async function start(label, data, { flags = [], hook = null } = {}) {
  const acc = await shop(label);
  for (const f of flags) await setFlag(acc.shopId, f, true);
  const ledgerBox = { data: JSON.parse(JSON.stringify(data)) };
  const web = browser({ token: acc.token, userId: acc.userId, deviceId: `${label}-web`, ledgerBox, hook });
  assert.equal((await web.sync.firstSync()).did, 'baseline');
  assert.equal(web.sync.queueSize(), 0);
  return { acc, web };
}

/** «وسطِ pull»: کارِ داده‌شده درست وقتی درخواستِ pull در راه است */
function duringPull() {
  const slot = { run: null };
  slot.hook = async (p) => { if (p.startsWith('/api/sync/v1/pull') && slot.run) { const f = slot.run; slot.run = null; f(); } };
  return slot;
}

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
});
test.after(async () => { await h.stop(); });

/* ───────────────────────────────────────────── ۰) بردارها */

test('۰) keepPendingVerdicts و faCount/backlogWarning = بردارهای مشترک با Kotlin', () => {
  const show = (v) => (v.v !== 'trim' ? v.v
    : ['trim', ...v.drop.map((f) => `-${f}`), ...Object.entries(v.rebase).map(([f, d]) => `+${f}=${d}`)].join(' '));
  const op = (s) => {
    const [table, rowId, t, ...fs] = s.trim().split(' ');
    const fields = {};
    for (const f of fs) {
      const [name, v] = f.split(/:(.*)/s);
      fields[name] = v.startsWith('+') ? { $inc: (fields[name]?.$inc || 0) + Number(v.slice(1)) } : 1;
    }
    return { table, row_id: rowId, type: { i: 'insert', u: 'update', d: 'delete' }[t], ...(t === 'd' ? {} : { fields }) };
  };
  const kp = vectors('keep-pending-v1.json');
  assert.ok(kp.cases.length >= 15);
  for (const c of kp.cases) {
    assert.deepEqual(core.keepPendingVerdicts(c.incoming.map(op), c.pending.map(op)).map(show), c.want, c.name);
  }
  const ob = vectors('op-backlog-v1.json');
  assert.equal(core.QUEUE_WARN, ob.warnAt);
  for (const r of ob.count) assert.equal(core.faCount(r.n), r.want, JSON.stringify(r));
  for (const r of ob.warning) assert.equal(core.backlogWarning(r.queued), r.want, JSON.stringify(r));
});

/* ───────────────────────────────────────────── ۱) صف */

test('۱-الف) ۶۰٬۰۰۰ op در صف ⇒ هیچ‌کدام کنار نمی‌رود و همه یک بار و به همان ترتیب می‌رسند', () => {
  const box = new Map();
  const io = { read: (k) => (box.has(k) ? JSON.parse(box.get(k)) : null), write: (k, v) => { box.set(k, JSON.stringify(v)); return true; } };
  const q = new core.OpQueue(io, 'kp-oplog');
  const total = 60000;
  const per = 3000;
  for (let r = 0; r < total / per; r++) {
    const ops = [];
    for (let i = 0; i < per; i++) ops.push({ op_id: `o${r * per + i}`, ts: 1000 + r, table: 'sales', row_id: `s${r * per + i}`, type: 'insert', fields: { n: i } });
    assert.equal(q.push(ops), (r + 1) * per);
  }
  assert.equal(q.size(), total);
  assert.deepEqual(q.dropped(), [], 'هیچ queue_overflow');
  assert.match(core.backlogWarning(q.size()), /^۶۰٬۰۰۰ تغییر هنوز به سرور نرسیده/);
  //  «سرور» همه را می‌پذیرد. دسته‌ها ۶٬۰۰۰تایی (موتور ۲۰۰): هر batch/ack کلِ صف را می‌خواند و ۳۰۰ دور روی
  //  صفِ ۶ مگابایتی دقیقه‌ای طول می‌کشید؛ دستهٔ ۲۰۰تاییِ خودِ موتور در ۱-ب
  const arrived = [];
  for (let guard = 0; q.size() && guard < 1000; guard++) {
    const b = q.batch(6000, 4 * 1024 * 1024);
    assert.ok(b.length);
    for (const o of b) arrived.push(o.op_id);
    q.ack(b.map((o) => o.op_id));
  }
  assert.equal(arrived.length, total);
  assert.equal(new Set(arrived).size, total, 'هر op یک بار');
  assert.ok(arrived.every((id, i) => id === `o${i}`), 'به همان ترتیب');
  assert.deepEqual(q.dropped(), []);
});

test('۱-ب) موتورِ وب: صفِ ۱۲٬۰۰۰ op ⇒ پیامِ «هنوز نرسیده» بی چراغِ قرمز؛ با دسته‌های ۲۰۰تایی تا ته و همه به «سرور»', async () => {
  const got = [];
  const web = browser({
    deviceId: 'kp-big', userId: 'u-big',
    fetch: (p, o) => {
      if (p === '/api/sync/v1/push') {
        assert.ok(o.body.ops.length <= 200);
        for (const op of o.body.ops) got.push(op.op_id);
        return { ok: true, results: o.body.ops.map((op) => ({ op_id: op.op_id, status: 'applied' })), cursor: got.length, schema_version: core.SCHEMA_VERSION };
      }
      if (p.startsWith('/api/sync/v1/pull')) return { ok: true, ops: [], cursor: got.length, has_more: false };
      throw new Error(`مسیرِ پیش‌بینی‌نشده ${p}`);
    },
  });
  const q = new core.OpQueue({
    read: (k) => JSON.parse(web.ls.getItem(k) || 'null'),
    write: (k, v) => { web.ls.setItem(k, JSON.stringify(v)); return true; },
  }, 'tohid-sync-v1-u-big-oplog');
  for (let r = 0; r < 12; r++) {
    const ops = [];
    for (let i = 0; i < 1000; i++) ops.push({ op_id: `b${r * 1000 + i}`, ts: 5000 + r, table: 'sales', row_id: `x${r * 1000 + i}`, type: 'insert', fields: { n: i } });
    q.push(ops);
  }
  const before = web.sync.status();
  assert.equal(before.queued, 12000);
  assert.match(before.lastError, /^۱۲٬۰۰۰ تغییر هنوز به سرور نرسیده/);
  assert.notEqual(before.dot, core.dotOf({ error: true, online: true, signedIn: true, configured: true, queued: 12000, busy: false }), 'چراغ قرمز نیست');
  await web.sync.sync();
  assert.equal(web.sync.queueSize(), 0);
  assert.equal(got.length, 12000);
  assert.deepEqual(got, Array.from({ length: 12000 }, (_, i) => `b${i}`));
  assert.equal(web.sync.status().lastError, '');
  assert.deepEqual(web.sync.dropped(), []);
});

test('۱-ج) حافظهٔ پر ⇒ ثبت با پیامِ روشن رد می‌شود و هیچ op ِ کهنه‌ای نمی‌رود؛ حافظهٔ آزاد ⇒ همان تغییر می‌رود', async () => {
  const { acc, web } = await start('kp-full', { products: [{ id: 'p-1', name: 'چای', price: 100 }] });
  web.save((d) => { d.products[0].price = 110; });          // یک op ِ «کهنه» در صف
  assert.equal(web.sync.queueSize(), 1);
  web.ls.full = true;
  assert.equal(web.save((d) => { d.products.push({ id: 'p-2', name: 'قند', price: 50 }); }), 0);
  assert.match(web.sync.status().lastError, /حافظهٔ مرورگر پر است/);
  assert.equal(web.sync.queueSize(), 1, 'op ِ کهنه سرِ جایش');
  web.ls.full = false;
  web.save(() => { });
  await web.sync.sync();
  assert.equal(web.sync.queueSize(), 0);
  assert.equal((await serverRow(acc.shopId, 'products', 'p-1')).data.price, 110);
  assert.deepEqual((await serverRow(acc.shopId, 'products', 'p-2')).data, { name: 'قند', price: 50 });
});

/* ───────────────────────────────────────────── ۲–۴) تغییرِ در صف */

test('۲) ویرایشِ وسطِ pull ⇒ با پرچم می‌ماند، به سرور می‌رسد و دستگاه = سرور؛ بی پرچم بازنویسی و واگرا (مرجع)', async () => {
  for (const flag of [true, false]) {
    const slot = duringPull();
    const { acc, web } = await start(`kp-edit-${flag}`, { products: [{ id: 'p-1', name: 'چای', price: 100 }] },
      { flags: flag ? [KEEP] : [], hook: slot.hook });
    await phone(acc, [upd('products', 'p-1', { price: 300, name: 'چای سبز' })]);
    slot.run = () => web.save((d) => { d.products[0].price = 200; });
    await web.sync.sync();                                     // push (هیچ) ⇐ pull، و ذخیره درست وسطش
    assert.equal(web.sync.queueSize(), 1, 'ذخیرهٔ وسطِ pull در صف است');
    assert.equal(web.row('products', 'p-1').name, 'چای سبز', 'فیلدِ دیگرِ رسیده نشست');
    assert.equal(web.row('products', 'p-1').price, flag ? 200 : 300);
    await web.sync.sync();                                     // push ِ ۲۰۰
    assert.equal(web.sync.queueSize(), 0);
    assert.equal((await serverRow(acc.shopId, 'products', 'p-1')).data.price, 200, 'سرور: آخری که رسید');
    if (flag) {
      await sameAsServer(web, acc, 'products', 'p-1', 'ویرایشِ وسطِ pull');
      assert.equal(web.save(() => { }), 0, 'هیچ op ِ پژواکی');
    } else {
      assert.equal(web.row('products', 'p-1').price, 300, 'مرجع: دستگاه ۳۰۰، سرور ۲۰۰ — برای همیشه');
      await web.sync.sync();
      assert.equal(web.row('products', 'p-1').price, 300);
    }
  }
});

test('۳-الف) حذفِ وسطِ pull و ویرایشِ رسیده ⇒ با پرچم شبح نمی‌آید و دستگاه = سرور؛ بی پرچم شبح (مرجع)', async () => {
  for (const flag of [true, false]) {
    const slot = duringPull();
    const { acc, web } = await start(`kp-del-${flag}`, { products: [{ id: 'p-1', name: 'چای', price: 100 }] },
      { flags: flag ? [KEEP] : [], hook: slot.hook });
    await phone(acc, [upd('products', 'p-1', { name: 'چای سبز' })]);
    slot.run = () => web.save((d) => { d.products = []; });
    await web.sync.sync();
    assert.equal(web.row('products', 'p-1') === null, flag);
    await web.sync.sync();
    assert.equal((await serverRow(acc.shopId, 'products', 'p-1')).deleted, true);
    if (flag) await sameAsServer(web, acc, 'products', 'p-1', 'حذفِ وسطِ pull');
    else assert.deepEqual(web.row('products', 'p-1'), { id: 'p-1', name: 'چای سبز' }, 'مرجع: شبح');
  }
});

test('۳-ب) ویرایشِ وسطِ pull و حذفِ رسیده ⇒ با پرچم ردیف می‌ماند و سرور با رسیدنِ ویرایش زنده‌اش می‌کند', async () => {
  for (const flag of [true, false]) {
    const slot = duringPull();
    const { acc, web } = await start(`kp-revdel-${flag}`, { products: [{ id: 'p-1', name: 'چای', price: 100 }] },
      { flags: flag ? [KEEP] : [], hook: slot.hook });
    await phone(acc, [del('products', 'p-1')]);
    slot.run = () => web.save((d) => { d.products[0].price = 200; });
    await web.sync.sync();
    await web.sync.sync();
    const srv = await serverRow(acc.shopId, 'products', 'p-1');
    assert.deepEqual(srv, { data: { name: 'چای', price: 200 }, deleted: false }, 'سرور: ویرایشِ پس از حذف زنده می‌کند');
    if (flag) await sameAsServer(web, acc, 'products', 'p-1', 'ویرایشِ وسطِ pull و حذفِ رسیده');
    else assert.equal(web.row('products', 'p-1'), null, 'مرجع: ردیف از دستگاه رفت ولی سرور دارد');
  }
});

test('۴) پول: دریافتِ وسطِ pull با دریافت و «اصلاحِ» دستگاهِ دیگر ⇒ با پرچم هر دو دستگاه و سرور یکی‌اند', async () => {
  for (const flag of [true, false]) {
    const slot = duringPull();
    const { acc, web } = await start(`kp-money-${flag}`,
      { sales: [{ id: 's-1', total: 1000, paidAmount: 100, remaining: 900, note: '' }] }, { flags: flag ? [KEEP] : [], hook: slot.hook });
    //  گوشی: یک دریافتِ ۳۰ (دلتا) و یک «اصلاحِ» دستی که paidAmount را مقدارِ ساده می‌دهد
    await phone(acc, [upd('sales', 's-1', { paidAmount: { $inc: 30 }, remaining: { $inc: -30 } })]);
    await phone(acc, [upd('sales', 's-1', { paidAmount: 400, note: 'اصلاح' })]);
    slot.run = () => web.save((d) => { d.sales[0].paidAmount += 50; d.sales[0].remaining -= 50; });
    await web.sync.sync();
    await web.sync.sync();
    const srv = await serverRow(acc.shopId, 'sales', 's-1');
    assert.equal(srv.data.paidAmount, 450, 'سرور: ۴۰۰ و سپس ‎+۵۰');
    assert.equal(srv.data.remaining, 820);
    if (flag) await sameAsServer(web, acc, 'sales', 's-1', 'پول');
    else assert.equal(web.row('sales', 's-1').paidAmount, 400, 'مرجع: دریافتِ ۵۰ ِ همین دستگاه روی خودش گم');
  }
});

/* ───────────────────────────────────────────── ۵–۶) زنده شدن پس از حذف */

const P5 = { id: 'p-5', name: 'روغن', price: 100, unit: 'لیتر', barcodes: ['111'], category: 'خوراکی' };
const S5 = { id: 's-5', total: 1000, finalTotal: 1000, paidAmount: 100, remaining: 900, note: '' };

test('۵) ردیفی که دستگاهِ دیگر پس از حذف زنده کرد ⇒ با پرچم کامل و برابرِ سرور؛ بی پرچم نیمه‌ای (مرجع)', async () => {
  for (const flag of [true, false]) {
    const { acc, web } = await start(`kp-rev-${flag}`, { products: [P5], sales: [S5] }, { flags: flag ? [REVIVE] : [] });
    web.save((d) => { d.products = []; d.sales = []; });
    await web.sync.sync();
    assert.equal((await serverRow(acc.shopId, 'products', 'p-5')).deleted, true);
    //  گوشی حذف را ندیده: ویرایش و دریافت ⇒ هر دو ردیف روی سرور زنده‌اند؛ و پس از آن یک op ِ دیگر
    await phone(acc, [upd('products', 'p-5', { price: 250 })]);
    await phone(acc, [upd('sales', 's-5', { paidAmount: { $inc: 50 }, remaining: { $inc: -50 } })]);
    await phone(acc, [upd('products', 'p-5', { unit: 'گالن' }), upd('sales', 's-5', { note: 'پس از زنده شدن' })]);
    await web.sync.sync();
    const { id: _id, ...body } = P5;
    assert.deepEqual(await serverRow(acc.shopId, 'products', 'p-5'), { data: { ...body, price: 250, unit: 'گالن' }, deleted: false },
      'سرور: دادهٔ پیش از حذف + ویرایش‌ها');
    if (flag) {
      await sameAsServer(web, acc, 'products', 'p-5', 'ویرایشِ پس از حذف');
      await sameAsServer(web, acc, 'sales', 's-5', '$inc ِ پس از حذف (یک بار)');
      assert.equal(web.row('sales', 's-5').paidAmount, 150);
      assert.equal(web.save(() => { }), 0, 'هیچ op ِ پژواکی');
    } else {
      assert.deepEqual(web.row('products', 'p-5'), { id: 'p-5', price: 250, unit: 'گالن' }, 'مرجع: نیمه‌ای');
      assert.equal(web.row('sales', 's-5').total, undefined, 'مرجع: فاکتورِ بی مبلغ');
    }
  }
});

test('۵-ب) بازگردانیِ ردیفِ حذف‌شده از پنل و دو بار حذف و زنده ⇒ با پرچم ردیفِ کامل', async () => {
  const { acc, web } = await start('kp-rev2', { products: [P5] }, { flags: [REVIVE] });
  const ctx = { app: 'shop', accountKind: 'shop', accountId: acc.shopId };
  web.save((d) => { d.products = []; });
  await web.sync.sync();
  const back = await syncV1.restoreDeleted(ctx, { table: 'products', rowId: 'p-5' });   // op ِ سرور با fields ِ خالی
  assert.equal(back.restored, true);
  await web.sync.sync();
  await sameAsServer(web, acc, 'products', 'p-5', 'بازگردانیِ پنل');
  assert.equal(web.row('products', 'p-5').name, 'روغن');
  //  بارِ دوم: حذفِ گوشی، ویرایشِ گوشی (زنده) — مرورگر هر دو را در یک صفحه می‌گیرد
  await phone(acc, [del('products', 'p-5')]);
  await phone(acc, [upd('products', 'p-5', { price: 999 })]);
  await web.sync.sync();
  await sameAsServer(web, acc, 'products', 'p-5', 'دو بار حذف و زنده');
  assert.equal(web.row('products', 'p-5').price, 999);
});

test('۶) پاسخِ خام: فقط op ِ زنده‌کننده row دارد (در صفحه‌بندی هم)؛ دکانِ بی پرچم و پمپ همان پاسخِ پیشین', async () => {
  const acc = await shop('kp-raw');
  const pull = (since, limit) => h.api('GET', `/api/sync/v1/pull?device_id=kp-raw-x&since=${since}${limit ? `&limit=${limit}` : ''}`,
    { token: acc.token, headers: { 'X-App': 'shop' } });
  await phone(acc, [{ op_id: oid(), ts: 1, table: 'products', row_id: 'p-1', type: 'insert', fields: { name: 'چای', price: 1 } },
    { op_id: oid(), ts: 1, table: 'products', row_id: 'p-2', type: 'insert', fields: { name: 'قند', price: 2 } }]);
  await phone(acc, [del('products', 'p-1')]);
  await phone(acc, [upd('products', 'p-1', { price: 5 }), upd('products', 'p-2', { price: 6 })]);
  await phone(acc, [upd('products', 'p-1', { name: 'چای ۲' })]);

  const off = await pull(0);
  assert.equal(off.status, 200);
  assert.equal(off.body.keep_pending, undefined);
  assert.ok(off.body.ops.every((o) => !('row' in o)), 'بی پرچم هیچ row');

  await setFlag(acc.shopId, REVIVE, true);
  await setFlag(acc.shopId, KEEP, true);
  const on = await pull(0);
  assert.equal(on.body.keep_pending, true);
  const withRow = on.body.ops.filter((o) => 'row' in o);
  assert.equal(withRow.length, 1, 'فقط op ِ زنده‌کننده');
  assert.deepEqual([withRow[0].row_id, withRow[0].row], ['p-1', { name: 'چای', price: 5 }], 'ردیف درست پس از همان op، نه امروز');
  //  جز row و keep_pending، مو‌به‌مو همان
  assert.deepEqual(on.body.ops.map(({ row, ...o }) => o), off.body.ops);
  //  صفحه‌بندی: op ِ زنده‌کننده در هر صفحه‌ای بیفتد همان row را دارد
  const pages = [];
  for (let since = 0, guard = 0; guard < 20; guard++) {
    const r = await pull(since, 2);
    pages.push(...r.body.ops);
    since = r.body.cursor;
    if (!r.body.has_more) break;
  }
  assert.deepEqual(pages.filter((o) => 'row' in o).map((o) => o.row), [{ name: 'چای', price: 5 }]);
  //  pull ِ بی op: کلیدی نیست
  const empty = await pull(on.body.cursor);
  assert.deepEqual(empty.body.ops, []);
  assert.equal(empty.body.keep_pending, undefined);
  //  پرچمِ `all` و خاموشِ خودِ دکان ⇒ خاموش (مقدارِ دکان جلو می‌افتد)
  await setFlag('all', REVIVE, true);
  await setFlag(acc.shopId, REVIVE, false);
  await setFlag(acc.shopId, KEEP, false);
  try {
    const mine = await pull(0);
    assert.ok(mine.body.ops.every((o) => !('row' in o)));
    assert.equal(mine.body.keep_pending, undefined);
  } finally {
    await setFlag('all', REVIVE, false);
  }
});

test('۶-ب) پمپ: همان مسیر، با پرچم‌های `all` ⇒ هیچ row و هیچ keep_pending', async () => {
  const stamp = { 'x-app-id': 'tohid-pump-app', 'x-app-version': '3.1.186', 'x-app-platform': 'windows' };
  const u = await h.newUser('پمپ‌دارِ نگه‌دار', 'pump');
  const made = await h.post('/api/pump', { name: 'پمپِ نگه‌دار', code: 'kp-1' }, { token: u.accessToken, headers: stamp });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const bound = await h.post('/api/pump/device/bind',
    { device: { uid: `duid-${crypto.randomBytes(4).toString('hex')}`, name: 'کامپیوترِ پمپ', platform: 'windows' } },
    { token: u.accessToken, headers: stamp });
  assert.equal(bound.status, 201, JSON.stringify(bound.body));
  const token = bound.body.deviceToken;
  await setFlag('all', REVIVE, true);
  await setFlag('all', KEEP, true);
  try {
    const push = (dev, ops) => h.post('/api/sync/v1/push', { device_id: dev, schema_version: 1, ops }, { token, headers: stamp });
    const row = `ROWKP${crypto.randomBytes(4).toString('hex')}`.toUpperCase();
    const op = (type, fields) => ({ op_id: crypto.randomUUID(), table: 'SafeEntry', row_id: row, type, ts: Date.now(), ...(fields ? { fields } : {}) });
    assert.equal((await push('pc-1', [op('insert', { Title: 'الف', Amount: '100', MonthKey: '1405-08' })])).status, 200);
    assert.equal((await push('pc-1', [op('delete')])).status, 200);
    assert.equal((await push('pc-1', [op('update', { Amount: '300' })])).status, 200);
    const r = await h.get('/api/sync/v1/pull?device_id=pc-2&since=0', { token, headers: stamp });
    assert.equal(r.status, 200);
    assert.equal(r.body.ops.length, 3);
    assert.ok(r.body.ops.every((o) => !('row' in o)));
    assert.equal(r.body.keep_pending, undefined);
  } finally {
    await setFlag('all', REVIVE, false);
    await setFlag('all', KEEP, false);
  }
});

test('۷) پرچمِ sync.gzip_pull: pull ِ بزرگ با Accept-Encoding: gzip فشرده و همان JSON؛ بی سرآیند، کوچک یا بی پرچم ساده', async () => {
  const acc = await shop('kp-gzip');
  const ops = [];
  for (let i = 0; i < 40; i++) ops.push({ op_id: oid(), ts: 1, table: 'products', row_id: `g-${i}`, type: 'insert', fields: { name: `کالای فشرده ${i}`, price: i } });
  await phone(acc, ops);
  const pull = (enc, since = 0) => fetch(`${h.base()}/api/sync/v1/pull?device_id=kp-gz&since=${since}`, {
    headers: { Authorization: `Bearer ${acc.token}`, 'X-App': 'shop', 'Accept-Encoding': enc },
  });
  const plain = await pull('gzip');
  assert.equal(plain.headers.get('content-encoding'), null, 'بی پرچم ساده');
  const want = await plain.json();
  assert.equal(want.ops.length, 40);

  await setFlag(acc.shopId, GZIP, true);
  const packed = await pull('gzip');
  assert.equal(packed.status, 200);
  assert.equal(packed.headers.get('content-encoding'), 'gzip');
  assert.deepEqual(await packed.json(), want, 'همان JSON');
  const identity = await pull('identity');
  assert.equal(identity.headers.get('content-encoding'), null, 'بی Accept-Encoding: gzip ساده');
  assert.deepEqual(await identity.json(), want);
  const small = await pull('gzip', want.cursor);
  assert.equal(small.headers.get('content-encoding'), null, 'pull ِ بی op ساده');
  assert.deepEqual((await small.json()).ops, []);

  //  موتورِ وب با پرچمِ روشن همان را می‌گیرد
  const web = browser({ token: acc.token, userId: 'u-gz', deviceId: 'kp-gz-web' });
  await web.sync.restoreFromServer();
  await phone(acc, [upd('products', 'g-1', { price: 77 })]);
  await web.sync.sync();
  assert.equal(web.row('products', 'g-1').price, 77);
});
