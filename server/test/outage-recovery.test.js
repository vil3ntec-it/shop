'use strict';
/**
 * PERF-05 — «قطع و بازگشتِ سرور/شبکه آزمایش شود» (§۲۹.۸).
 *
 * آزمون‌های نقطه‌ایِ پیشین (`mail-intermittent`، صفِ آفلاینِ پیک، «فرستادنِ دوبارهٔ یک دسته» در
 * `sync-client`) قطع را با صدا زدنِ دوبارهٔ تابع تقلید می‌کردند. این‌جا قطع **واقعی** است: یک پراکسیِ
 * TCP میانِ «دستگاه» و سرور، اتصال را وسطِ بارگذاری یا پس از پردازش (پیش از رسیدنِ پاسخ) می‌بُرد؛
 * خودِ سرور (برنامهٔ Express، و روی PostgreSQL استخرِ اتصال هم) بسته و دوباره ساخته می‌شود؛ و روی
 * PostgreSQL اتصال‌های پایگاه کشته یا بسته می‌شوند.
 *
 *   ۱) بریدنِ بدنه وسطِ push ⇒ هیچ opی نمی‌نشیند؛ تلاشِ دوباره دقیقاً یک بار.
 *   ۲) پاسخِ push گم می‌شود (سرور ثبت کرده) ⇒ تلاشِ دوباره `duplicate`؛ در oplog و ردیف یک بار؛
 *      دستگاهِ دیگر هر op را یک بار می‌گیرد.
 *   ۳) پاسخِ pull گم می‌شود ⇒ pull ِ دوباره با همان since همان opها را می‌دهد؛ چیزی جا نمی‌افتد.
 *   ۴) سرور خاموش و دوباره روشن ⇒ دستگاه فوراً «وصل نشد» می‌گیرد (نه انتظارِ بی‌پایان)، صفش
 *      می‌ماند، پس از بازگشت همه یک بار و به ترتیب می‌رسد.
 *   ۵) فقط PostgreSQL: کشتنِ همهٔ اتصال‌های سرور ⇒ بی راه‌اندازیِ دوباره بازمی‌گردد.
 *   ۶) فقط PostgreSQL: پایگاه اتصالِ تازه نمی‌پذیرد ⇒ پاسخِ سریعِ ۵xx (نه آویزان)، `/ready` ‏۵۰۳،
 *      هیچ opِ نیمه‌کاره؛ پس از بازگشت بی راه‌اندازیِ دوباره کار می‌کند.
 * PGlite درون‌فرآیندی است و «اتصالِ شبکه‌ای به پایگاه» ندارد ⇒ ۵ و ۶ آن‌جا معنا ندارند و رد می‌شوند؛
 * در ۴ هم PGlite ِ حافظه‌ای با بستن پاک می‌شود، پس آن‌جا فقط لایهٔ HTTP دوباره ساخته می‌شود.
 */
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';

const test = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const net = require('node:net');
const h = require('./helpers');
const db = require('../src/db');
const syncV1 = require('../src/lib/sync-v1');
const { createApp } = require('../src/app');

const PG = db.driverName() === 'postgres';
let seq = 0;
const opId = () => `01JO${String(Date.now()).slice(-9)}${String(++seq).padStart(5, '0')}`;

/* ── دستگاه: درخواستِ HTTP با اتصالِ تازه در هر بار (بی keep-alive) تا قطعِ پراکسی روشن باشد ── */
function call(port, method, path, { token, device, body, timeoutMs = 15000 } = {}) {
  const data = body === undefined ? null : Buffer.from(JSON.stringify(body));
  return new Promise((resolve, reject) => {
    const req = http.request({
      host: '127.0.0.1', port, method, path, agent: false, timeout: timeoutMs,
      headers: {
        'Content-Type': 'application/json',
        'X-App': 'shop',
        ...(device ? { 'X-Device': device } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(data ? { 'Content-Length': data.length } : {}),
      },
    }, (res) => {
      const chunks = [];
      res.on('data', (c) => chunks.push(c));
      res.on('end', () => {
        const text = Buffer.concat(chunks).toString('utf8');
        let json = null;
        try { json = JSON.parse(text); } catch { /* متن */ }
        resolve({ status: res.statusCode, body: json ?? text });
      });
      res.on('error', reject);
    });
    req.on('timeout', () => req.destroy(Object.assign(new Error('timeout'), { code: 'ETIMEDOUT' })));
    req.on('error', reject);
    if (data) req.write(data);
    req.end();
  });
}
const push = (port, token, device, ops) => call(port, 'POST', '/api/sync/v1/push', {
  token, device, body: { device_id: device, schema_version: 2, ops },
});
const pull = (port, token, device, since = 0) => call(port, 'GET', `/api/sync/v1/pull?device_id=${device}&since=${since}`, { token, device });

/* ── پراکسیِ TCP با «حالتِ قطع» برای اتصالِ بعدی ──────────────────────────
 *   pass           همه‌چیز رد می‌شود
 *   cut-upload     سربرگ و نیمی از بدنه به سرور می‌رسد، بعد هر دو سو بسته
 *   cut-response   درخواست کامل می‌رسد (سرور پردازش می‌کند)، نخستین بایتِ پاسخ که آمد هر دو سو بسته
 */
function makeProxy(targetPort) {
  const state = { target: targetPort, mode: 'pass', cuts: 0, sockets: new Set() };
  const server = net.createServer((client) => {
    const mode = state.mode;
    state.mode = 'pass';
    const upstream = net.connect(state.target, '127.0.0.1');
    for (const s of [client, upstream]) { state.sockets.add(s); s.on('close', () => state.sockets.delete(s)); s.on('error', () => {}); }
    const cut = () => { state.cuts++; client.destroy(); upstream.destroy(); };
    let seen = Buffer.alloc(0);
    let budget = Infinity;
    client.on('data', (chunk) => {
      if (mode !== 'cut-upload') return upstream.write(chunk);
      seen = Buffer.concat([seen, chunk]);
      if (budget === Infinity) {
        const end = seen.indexOf('\r\n\r\n');
        if (end < 0) return undefined;
        const len = Number((/content-length:\s*(\d+)/i.exec(seen.subarray(0, end).toString()) || [])[1] || 0);
        budget = end + 4 + Math.floor(len / 2);
      }
      if (seen.length >= budget) {
        upstream.write(seen.subarray(0, budget), () => setTimeout(cut, 50));
        seen = Buffer.alloc(0);
        budget = -1;
      }
      return undefined;
    });
    client.on('end', () => upstream.end());
    upstream.on('data', (chunk) => {
      if (mode === 'cut-response') return cut();
      return client.write(chunk);
    });
    upstream.on('end', () => client.end());
  });
  return new Promise((resolve) => server.listen(0, '127.0.0.1', () => resolve({
    port: server.address().port,
    state,
    close: () => new Promise((r) => { for (const s of state.sockets) s.destroy(); server.close(r); }),
  })));
}

/* ── یک نمونهٔ جدا از سرور (برای خاموش/روشن) روی درگاهی ثابت ── */
async function bootApp(port = 0) {
  const app = await createApp({ runMigrations: false });
  const server = await new Promise((resolve, reject) => {
    const s = app.listen(port, '127.0.0.1', () => resolve(s));
    s.on('error', reject);
  });
  return {
    port: server.address().port,
    close: () => new Promise((r) => { server.closeAllConnections?.(); server.close(r); }),
  };
}

async function ownerWithShop(label) {
  const u = await h.newUser(`صاحب-${label}`, 'shop');
  const made = await h.api('POST', '/api/shop', { token: u.accessToken, body: { name: `دکان ${label}` } });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { token: u.accessToken, shopId: made.body.shop.id };
}

const saleOps = (id, total) => {
  const ts = Date.now();
  return [
    { op_id: opId(), ts, table: 'sales', row_id: id, type: 'insert', fields: { total, items: 1 } },
    { op_id: opId(), ts, table: 'products', row_id: `p-${id}`, type: 'insert', fields: { name: `کالای ${id}`, stock: 5 } },
  ];
};
const countOps = async (shopId, ids) => Number((await db.one(
  `SELECT COUNT(*)::int AS n FROM oplog WHERE app='shop' AND account_id=$1 AND op_id = ANY($2::text[])`, [shopId, ids])).n);
const rowOf = (shopId, table, id) => db.one(
  `SELECT data, deleted_at FROM sync_rows WHERE app='shop' AND account_id=$1 AND table_name=$2 AND row_id=$3`, [shopId, table, id]);

/** همهٔ صفحه‌های pull تا ته، همان کاری که برنامه می‌کند */
async function pullAll(port, token, device, since) {
  const ops = [];
  let cursor = since;
  for (let i = 0; i < 50; i++) {
    const r = await pull(port, token, device, cursor);
    assert.equal(r.status, 200, JSON.stringify(r.body));
    ops.push(...r.body.ops);
    cursor = r.body.cursor;
    if (!r.body.has_more) break;
  }
  return { ops, cursor };
}

let proxy = null;
let backendPort = 0;

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
  backendPort = Number(new URL(h.base()).port);
  proxy = await makeProxy(backendPort);
});
test.after(async () => {
  await proxy?.close();
  //  اگر آزمونِ ۶ وسطِ کار کشته شد، پایگاه برای پرونده‌های بعدی بسته نماند
  if (PG) {
    const name = decodeURIComponent(new URL(process.env.DATABASE_URL).pathname.slice(1));
    const admin = await adminClient('postgres').catch(() => null);
    await admin?.query(`ALTER DATABASE "${name.replace(/"/g, '""')}" ALLOW_CONNECTIONS true`).catch(() => {});
    await admin?.end().catch(() => {});
  }
  await h.stop();
});

test('۱) بدنهٔ push وسطِ راه بریده می‌شود ⇒ هیچ opی نمی‌نشیند؛ تلاشِ دوباره دقیقاً یک بار', async () => {
  const { token, shopId } = await ownerWithShop('بریدنِ-بارگذاری');
  const ops = saleOps('s-up-1', 1500);
  proxy.state.mode = 'cut-upload';
  await assert.rejects(push(proxy.port, token, 'dev-up', ops), (e) => ['ECONNRESET', 'EPIPE', 'ETIMEDOUT'].includes(e.code) || /socket hang up/.test(e.message));
  assert.equal(proxy.state.cuts > 0, true, 'پراکسی نبرید');
  //  سرور فرصتِ «پردازش» دارد؛ با بدنهٔ نیمه چیزی نباید بنشیند
  await new Promise((r) => setTimeout(r, 300));
  assert.equal(await countOps(shopId, ops.map((o) => o.op_id)), 0, 'opِ نیمه‌کاره نشست');
  assert.equal(await rowOf(shopId, 'sales', 's-up-1'), null);

  const again = await push(proxy.port, token, 'dev-up', ops);
  assert.equal(again.status, 200, JSON.stringify(again.body));
  assert.deepEqual(again.body.results.map((r) => r.status), ['applied', 'applied']);
  assert.equal(await countOps(shopId, ops.map((o) => o.op_id)), 2);
  assert.equal((await rowOf(shopId, 'sales', 's-up-1')).data.total, 1500);
});

test('۲) پاسخِ push گم می‌شود ⇒ تلاشِ دوباره «تکراری»؛ یک بار در دفتر؛ دستگاهِ دیگر یک بار می‌گیرد', async () => {
  const { token, shopId } = await ownerWithShop('گمِ-پاسخ');
  const ops = [...saleOps('s-lost-1', 2000), { op_id: opId(), ts: Date.now(), table: 'products', row_id: 'p-s-lost-1', type: 'update', fields: { stock: { $inc: -1 } } }];
  proxy.state.mode = 'cut-response';
  await assert.rejects(push(proxy.port, token, 'dev-lost', ops), (e) => ['ECONNRESET', 'EPIPE'].includes(e.code) || /socket hang up/.test(e.message));
  //  سرور ثبت کرده بود، فقط پاسخ نرسید
  assert.equal(await countOps(shopId, ops.map((o) => o.op_id)), 3);

  const again = await push(proxy.port, token, 'dev-lost', ops);
  assert.equal(again.status, 200, JSON.stringify(again.body));
  assert.deepEqual(again.body.results.map((r) => r.status), ['duplicate', 'duplicate', 'duplicate']);
  assert.equal(await countOps(shopId, ops.map((o) => o.op_id)), 3);
  //  `$inc` دو بار حساب نشد
  assert.equal((await rowOf(shopId, 'products', 'p-s-lost-1')).data.stock, 4);

  const other = await pullAll(proxy.port, token, 'dev-other', 0);
  const mine = other.ops.filter((o) => ops.some((x) => x.op_id === o.op_id));
  assert.equal(mine.length, 3);
  assert.equal(new Set(mine.map((o) => o.op_id)).size, 3);
});

test('۳) پاسخِ pull گم می‌شود ⇒ pull ِ دوباره با همان since همان opها را می‌دهد', async () => {
  const { token } = await ownerWithShop('گمِ-pull');
  const ops = saleOps('s-pull-1', 700);
  assert.equal((await push(backendPort, token, 'dev-w', ops)).status, 200);
  proxy.state.mode = 'cut-response';
  await assert.rejects(pull(proxy.port, token, 'dev-r', 0), (e) => ['ECONNRESET', 'EPIPE'].includes(e.code) || /socket hang up/.test(e.message));
  const again = await pullAll(proxy.port, token, 'dev-r', 0);
  assert.deepEqual(again.ops.map((o) => o.op_id), ops.map((o) => o.op_id));
});

test('۳-ب) یافته: پاسخِ pull ِ گم‌شده، cursor ِ سمتِ سرور را جلو می‌برد و تعارضِ بعدی ثبت نمی‌شود', {
  todo: 'سرور cursor ِ دستگاه را هنگامِ pull برابرِ «آنچه فرستاد» می‌گذارد، نه «آنچه دستگاه گرفت» (since)؛ '
    + 'تغییرِ معنای همگام‌سازی است ⇒ فقط پشتِ پرچم و با تصمیمِ صاحب محصول (پرسشِ PERF-05)',
}, async () => {
  const { token, shopId } = await ownerWithShop('تعارضِ-پس-از-قطع');
  const t = Date.now();
  assert.equal((await push(backendPort, token, 'dev-a', [{ op_id: opId(), ts: t, table: 'products', row_id: 'p-c', type: 'insert', fields: { name: 'برنج', price: 100 } }])).status, 200);
  const first = await pullAll(backendPort, token, 'dev-b', 0);
  assert.equal((await push(backendPort, token, 'dev-a', [{ op_id: opId(), ts: t + 1, table: 'products', row_id: 'p-c', type: 'update', fields: { price: 200 } }])).status, 200);
  //  پاسخِ این pull به دستگاهِ B نمی‌رسد؛ B قیمتِ ۲۰۰ را هرگز ندیده
  proxy.state.mode = 'cut-response';
  await assert.rejects(pull(proxy.port, token, 'dev-b', first.cursor));
  assert.equal((await push(backendPort, token, 'dev-b', [{ op_id: opId(), ts: t + 2, table: 'products', row_id: 'p-c', type: 'update', fields: { price: 300 } }])).status, 200);
  const c = await db.one(`SELECT COUNT(*)::int AS n FROM sync_conflicts WHERE app='shop' AND account_id=$1 AND row_id='p-c'`, [shopId]);
  assert.equal(Number(c.n), 1, 'ویرایشِ هم‌زمانِ قیمت (۲۰۰ از A که B ندیده) باید تعارض ثبت شود');
});

test('۴) سرور خاموش و دوباره روشن ⇒ «وصل نشد» ِ فوری، صفِ دستگاه می‌ماند و پس از بازگشت همه یک بار می‌رسد', async () => {
  const { token, shopId } = await ownerWithShop('خاموش-روشن');
  let node = await bootApp();
  const port = node.port;
  const before = saleOps('s-r-1', 100);
  assert.equal((await push(port, token, 'dev-1', before)).status, 200);
  const reader = await pullAll(port, token, 'dev-2', 0);
  assert.equal(reader.ops.length, 2);

  //  خاموش: HTTP بسته؛ روی PostgreSQL استخرِ اتصال هم (روی PGlite ِ حافظه‌ای بستن یعنی پاک شدن)
  await node.close();
  if (PG) await db.closeDb();
  const queue = [...saleOps('s-r-2', 200), ...saleOps('s-r-3', 300)];
  const t0 = Date.now();
  await assert.rejects(push(port, token, 'dev-1', queue), (e) => e.code === 'ECONNREFUSED');
  assert.ok(Date.now() - t0 < 2000, `«وصل نشد» ${Date.now() - t0}ms طول کشید`);
  assert.equal(await countOps(shopId, queue.map((o) => o.op_id)), 0);

  //  روشن: نمونهٔ تازه روی همان درگاه و همان پایگاه
  node = await bootApp(port);
  try {
    const ready = await call(port, 'GET', '/api/ready');
    assert.equal(ready.status, 200, JSON.stringify(ready.body));
    const sent = await push(port, token, 'dev-1', queue);
    assert.equal(sent.status, 200, JSON.stringify(sent.body));
    assert.ok(sent.body.results.every((r) => r.status === 'applied'));
    //  دستگاهِ خواننده از cursor ِ خودش ادامه می‌دهد: همه، یک بار، به ترتیبِ سرور
    const rest = await pullAll(port, token, 'dev-2', reader.cursor);
    assert.deepEqual(rest.ops.map((o) => o.op_id), queue.map((o) => o.op_id));
    const seqs = rest.ops.map((o) => o.server_seq);
    assert.deepEqual(seqs, [...seqs].sort((a, b) => a - b));
    assert.ok(seqs[0] > reader.cursor);
    for (const [id, total] of [['s-r-1', 100], ['s-r-2', 200], ['s-r-3', 300]]) {
      assert.equal((await rowOf(shopId, 'sales', id)).data.total, total);
    }
  } finally {
    await node.close();
  }
});

/**
 * استثنای بی‌صاحب (مثلاً رخدادِ `error` ِ اتصالِ بیکارِ کشته‌شده بی شنونده) در تولید فرایند را می‌اندازد.
 * این‌جا شمرده می‌شود تا آزمون به‌جای «شاید» قطعاً سرخ شود.
 */
function watchCrashes() {
  const seen = [];
  const on = (e) => seen.push(e?.message || String(e));
  process.on('uncaughtException', on);
  return { seen, stop: () => process.off('uncaughtException', on) };
}
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

/** فقط PostgreSQL: یک اتصالِ جدا (نه از استخرِ سرور) برای کشتن و بستن */
async function adminClient(database) {
  const { Client } = require('pg');
  const url = new URL(process.env.DATABASE_URL);
  if (database) url.pathname = `/${database}`;
  const c = new Client({ connectionString: url.toString() });
  await c.connect();
  return c;
}

test('۵) PostgreSQL: همهٔ اتصال‌های سرور کشته می‌شوند ⇒ بی راه‌اندازیِ دوباره بازمی‌گردد', { skip: PG ? false : 'PGlite درون‌فرآیندی است؛ اتصالِ شبکه‌ای به پایگاه ندارد' }, async () => {
  const { token, shopId } = await ownerWithShop('کشتنِ-اتصال');
  //  استخر را گرم کن تا اتصالِ بیکار داشته باشد
  await Promise.all([1, 2, 3].map(() => call(backendPort, 'GET', '/api/ready')));
  const crashes = watchCrashes();
  const admin = await adminClient();
  try {
    const killed = await admin.query(
      `SELECT COUNT(pg_terminate_backend(pid))::int AS n FROM pg_stat_activity
        WHERE datname = current_database() AND pid <> pg_backend_pid() AND backend_type = 'client backend'`);
    assert.ok(killed.rows[0].n > 0, 'اتصالی برای کشتن نبود');
    //  اتصال‌های بیکارِ استخر پیامِ FATAL را می‌گیرند
    await sleep(300);
  } finally {
    await admin.end();
    crashes.stop();
  }
  assert.deepEqual(crashes.seen, [], 'اتصالِ کشته‌شده فرایندِ سرور را انداخت');
  //  نخستین درخواست شاید روی اتصالِ مرده بیفتد؛ برنامه با عقب‌نشینی دوباره می‌فرستد
  const ops = saleOps('s-kill-1', 900);
  let ok = null;
  const statuses = [];
  for (let i = 0; i < 4 && !ok; i++) {
    const r = await push(backendPort, token, 'dev-k', ops);
    statuses.push(r.status);
    if (r.status === 200) ok = r;
    else assert.ok(r.status >= 500 && r.body?.error?.code, `پاسخِ نادرست ${r.status}: ${JSON.stringify(r.body)}`);
  }
  assert.ok(ok, `پس از ۴ تلاش هنوز نه: ${statuses}`);
  assert.equal(await countOps(shopId, ops.map((o) => o.op_id)), 2);
  assert.equal((await call(backendPort, 'GET', '/api/ready')).status, 200);
});

test('۶) PostgreSQL: پایگاه اتصالِ تازه نمی‌پذیرد ⇒ ۵xx ِ سریع، ‎/ready‎ ‏۵۰۳، بی opِ نیمه؛ بازگشت بی راه‌اندازیِ دوباره', { skip: PG ? false : 'PGlite درون‌فرآیندی است؛ اتصالِ شبکه‌ای به پایگاه ندارد' }, async () => {
  const { token, shopId } = await ownerWithShop('بستنِ-پایگاه');
  //  پایگاهِ خودش را نمی‌شود بست ⇒ از پایگاهِ نگه‌داریِ `postgres`
  const name = decodeURIComponent(new URL(process.env.DATABASE_URL).pathname.slice(1));
  const admin = await adminClient('postgres');
  const ident = `"${name.replace(/"/g, '""')}"`;
  const ops = saleOps('s-down-1', 1200);
  try {
    await admin.query(`ALTER DATABASE ${ident} ALLOW_CONNECTIONS false`);
    await admin.query(`SELECT pg_terminate_backend(pid) FROM pg_stat_activity
                        WHERE datname = $1 AND pid <> pg_backend_pid() AND backend_type = 'client backend'`, [name]);
    const t0 = Date.now();
    const r = await push(backendPort, token, 'dev-d', ops);
    const took = Date.now() - t0;
    assert.ok(r.status >= 500, `پایگاه بسته است ولی ${r.status}: ${JSON.stringify(r.body)}`);
    assert.equal(typeof r.body?.error?.ref, 'string', 'خطای ۵xx بی کدِ پیگیری');
    //  هیچ متنِ خام از پایگاه (نامِ پایگاه، میزبان) به دستگاه نمی‌رسد
    assert.doesNotMatch(JSON.stringify(r.body), new RegExp(name));
    assert.ok(took < 5000, `پاسخ ${took}ms طول کشید (باید سریع شکست بخورد)`);
    const ready = await call(backendPort, 'GET', '/api/ready');
    assert.equal(ready.status, 503);
    assert.equal(ready.body.database, 'unavailable');
  } finally {
    await admin.query(`ALTER DATABASE ${ident} ALLOW_CONNECTIONS true`);
    await admin.end();
  }
  assert.equal(await countOps(shopId, ops.map((o) => o.op_id)), 0, 'op در نبودِ پایگاه نشست؟');
  const back = await push(backendPort, token, 'dev-d', ops);
  assert.equal(back.status, 200, JSON.stringify(back.body));
  assert.ok(back.body.results.every((x) => x.status === 'applied'));
  assert.equal((await call(backendPort, 'GET', '/api/ready')).status, 200);
  assert.equal((await rowOf(shopId, 'sales', 's-down-1')).data.total, 1200);
});
