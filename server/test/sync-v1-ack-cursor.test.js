'use strict';
/**
 * پرچمِ `sync.cursor_from_since` — cursor ِ دستگاه در سرور = «آنچه دستگاه تأیید کرده»، نه «آنچه سرور فرستاد».
 *
 * یافتهٔ PERF-05 (#449، آزمونِ `todo` ِ «۳-ب»): `pull()` cursor ِ دستگاه را در `sync_devices` برابرِ
 * **آنچه فرستاد** می‌گذاشت. اگر پاسخِ pull به دستگاه نرسد، سرور خیال می‌کند دستگاه ویرایشِ دستگاهِ دیگر را
 * دیده و ویرایشِ بعدیِ همان فیلد **بی ثبتِ تعارض** رویش می‌نشیند. با پرچمِ روشن، cursor ِ ذخیره‌شده همان
 * `since` ِ pull است (دستگاه با آن می‌گوید «تا این‌جا را دارم») یا `since` ِ صریحِ push.
 *
 *   ۱) «۳-ب» ِ #449 با قطعِ واقعی (پراکسیِ TCP) ⇒ تعارض ثبت می‌شود؛ pull ِ دوباره همان op را می‌دهد.
 *   ۲) پرچمِ خاموش ⇒ همان رفتارِ امروز (همین سناریو تعارض ندارد؛ `since` ِ push نادیده).
 *   ۳) گردشِ عادی با `since` در push ⇒ پاسخ‌ها، ردیف و تعارض‌ها مو‌به‌مو مثلِ پرچمِ خاموش.
 *   ۴) برنامهٔ بی `since` در push ⇒ محتاط: ممکن است تعارضِ اضافه ثبت شود، هرگز کم.
 *   ۵) pull ِ تکراری بی‌اثر است؛ صفحه‌بندی همان.
 *   ۶) پرچم پس از یک قطع روشن شود ⇒ از نخستین pull ِ بعدی درست (cursor ِ کهنهٔ جلوافتاده نمی‌ماند).
 *   ۷) `since` ِ نادرست نادیده؛ `since` ِ بزرگ‌تر از سرِ دفتر به سرِ دفتر بریده می‌شود.
 *   ۸) پمپ (همان مسیرِ مشترک) حتی با پرچمِ `all` و پرچمِ همان پمپ دست نمی‌خورد.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const net = require('node:net');
const crypto = require('node:crypto');
const h = require('./helpers');
const db = require('../src/db');
const syncV1 = require('../src/lib/sync-v1');

const FLAG = 'sync.cursor_from_since';
let seq = 0;
const opId = () => `01JK${String(Date.now()).slice(-9)}${String(++seq).padStart(5, '0')}`;

const shopHeaders = (device) => ({ 'X-App': 'shop', ...(device ? { 'X-Device': device } : {}) });
const push = (token, device, ops, extra = {}) => h.api('POST', '/api/sync/v1/push', {
  token, headers: shopHeaders(device), body: { device_id: device, schema_version: 2, ops, ...extra },
});
const pull = (token, device, since, limit) => h.api('GET',
  `/api/sync/v1/pull?device_id=${device}&since=${since}${limit ? `&limit=${limit}` : ''}`,
  { token, headers: shopHeaders() });
const status = (token, device) => h.api('GET', `/api/sync/v1/status?device_id=${device}`, { token, headers: shopHeaders() });

async function ok(p) {
  const r = await p;
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body;
}

const setFlag = (scope, on) => db.query(
  `INSERT INTO live_config (scope, key, value, updated_at) VALUES ($1,$2,$3,1)
   ON CONFLICT (scope, key) DO UPDATE SET value = excluded.value`,
  [String(scope), FLAG, on ? 'true' : 'false']
);

async function ownerWithShop(label, { flag = false } = {}) {
  const u = await h.newUser(`صاحب-${label}`, 'shop');
  const made = await h.api('POST', '/api/shop', { token: u.accessToken, body: { name: `دکان ${label}` } });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const shopId = made.body.shop.id;
  if (flag) await setFlag(shopId, true);
  return { token: u.accessToken, shopId };
}

const conflictsOf = (shopId, rowId) => db.many(
  `SELECT field, loser_value, winner_value, loser_device, winner_device FROM sync_conflicts
    WHERE app='shop' AND account_kind='shop' AND account_id=$1 AND row_id=$2 ORDER BY id`,
  [shopId, rowId]
);
async function storedCursor(kind, accountId, device) {
  const r = await db.one(
    'SELECT cursor FROM sync_devices WHERE account_kind=$1 AND account_id=$2 AND device_id=$3',
    [kind, accountId, device]
  );
  return r ? Number(r.cursor) : null;
}
const rowData = async (shopId, rowId) => {
  const r = await db.one(
    `SELECT data FROM sync_rows WHERE app='shop' AND account_id=$1 AND table_name='products' AND row_id=$2`,
    [shopId, rowId]
  );
  return typeof r.data === 'string' ? JSON.parse(r.data) : r.data;
};

const ins = (rowId, fields) => ({ op_id: opId(), ts: Date.now(), table: 'products', row_id: rowId, type: 'insert', fields });
const upd = (rowId, fields) => ({ op_id: opId(), ts: Date.now(), table: 'products', row_id: rowId, type: 'update', fields });

/* ── پراکسیِ TCP: درخواستِ «مسلح» کامل به سرور می‌رسد، نخستین بایتِ پاسخ که آمد هر دو سو بسته می‌شوند ── */
function cutProxy(targetPort) {
  const state = { armed: false, cuts: 0 };
  const sockets = new Set();
  const server = net.createServer((client) => {
    const cut = state.armed;
    state.armed = false;
    const up = net.connect(targetPort, '127.0.0.1');
    for (const s of [client, up]) { sockets.add(s); s.on('close', () => sockets.delete(s)); s.on('error', () => {}); }
    client.on('data', (chunk) => up.write(chunk));
    client.on('end', () => up.end());
    up.on('data', (chunk) => {
      if (!cut) return client.write(chunk);
      state.cuts++;
      client.destroy();
      up.destroy();
      return undefined;
    });
    up.on('end', () => client.end());
  });
  return new Promise((resolve) => server.listen(0, '127.0.0.1', () => resolve({
    port: server.address().port,
    state,
    close: () => new Promise((r) => { for (const s of sockets) s.destroy(); server.close(r); }),
  })));
}

/** pull از راهِ پراکسیِ مسلح ⇒ سرور کامل پردازش می‌کند، پاسخ به «دستگاه» نمی‌رسد */
function lostPull(token, device, since) {
  proxy.state.armed = true;
  const before = proxy.state.cuts;
  const sent = new Promise((resolve, reject) => {
    const req = http.request({
      host: '127.0.0.1', port: proxy.port, method: 'GET', agent: false, timeout: 15000,
      path: `/api/sync/v1/pull?device_id=${device}&since=${since}`,
      headers: { 'X-App': 'shop', Authorization: `Bearer ${token}` },
    }, (res) => { res.resume(); res.on('end', () => resolve(res.statusCode)); res.on('error', reject); });
    req.on('timeout', () => req.destroy(Object.assign(new Error('timeout'), { code: 'ETIMEDOUT' })));
    req.on('error', reject);
    req.end();
  });
  return assert.rejects(sent, (e) => ['ECONNRESET', 'EPIPE'].includes(e.code) || /socket hang up/.test(e.message))
    .then(() => assert.equal(proxy.state.cuts, before + 1, 'پراکسی پاسخ را نبرید'));
}

/**
 * «۳-ب» ِ #449: A کالا را می‌سازد، B می‌گیرد، A قیمت را ۲۰۰ می‌کند، پاسخِ pull ِ B گم می‌شود (B ۲۰۰ را ندیده)،
 * B قیمت را ۳۰۰ می‌کند. ⇒ تعارضِ واقعی است: ۲۰۰ ِ A بی آن‌که B دیده باشد رونویسی شد.
 */
async function lostPullThenEdit({ token, shopId }, rowId, { pushSince = false } = {}) {
  await ok(push(token, 'dev-a', [ins(rowId, { name: 'برنج', price: 100 })]));
  const first = await ok(pull(token, 'dev-b', 0));
  assert.deepEqual(first.ops.map((o) => o.row_id), [rowId]);
  await ok(push(token, 'dev-a', [upd(rowId, { price: 200 })]));
  await lostPull(token, 'dev-b', first.cursor);
  //  سرِ دفتر در لحظهٔ همان pull ِ گم‌شده = cursorی که سرور فرستاد (بی پرچم همین ذخیره می‌شد)
  const sent = (await ok(status(token, 'dev-b'))).head;
  //  برنامهٔ تازه `since` را هم می‌فرستد: همان cursorی که واقعاً دارد (first.cursor)
  const edit = await ok(push(token, 'dev-b', [upd(rowId, { price: 300 })], pushSince ? { since: first.cursor } : {}));
  assert.equal(edit.results[0].status, 'applied');
  return { first, sent, conflicts: await conflictsOf(shopId, rowId) };
}

let proxy = null;

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
  proxy = await cutProxy(Number(new URL(h.base()).port));
});
test.after(async () => {
  await proxy?.close();
  await h.stop();
});

test('۱) «۳-ب» ِ #449 با پرچمِ روشن: پاسخِ pull ِ گم‌شده cursor را جلو نمی‌برد و تعارضِ بعدی ثبت می‌شود', async () => {
  const shop = await ownerWithShop('قطع-روشن', { flag: true });
  const { first, conflicts } = await lostPullThenEdit(shop, 'p-lost');
  assert.equal(conflicts.length, 1, 'ویرایشِ هم‌زمانِ قیمت (۲۰۰ از A که B ندیده) باید تعارض ثبت شود');
  assert.equal(conflicts[0].field, 'price');
  assert.equal(Number(conflicts[0].loser_value), 200);
  assert.equal(Number(conflicts[0].winner_value), 300);
  assert.equal(conflicts[0].loser_device, 'dev-a');
  assert.equal(conflicts[0].winner_device, 'dev-b');
  //  برنده همان «آخری که رسید» است — پرچم داوری را عوض نمی‌کند، فقط ردِ بازنده را نگه می‌دارد
  assert.equal((await rowData(shop.shopId, 'p-lost')).price, 300);

  //  دستگاه دوباره با همان since می‌کشد ⇒ تغییرِ A دوباره می‌آید (opِ خودِ B نه)
  const again = await ok(pull(shop.token, 'dev-b', first.cursor));
  assert.deepEqual(again.ops.map((o) => [o.device_id, o.fields.price]), [['dev-a', 200]]);
  assert.equal(again.has_more, false);
  //  cursor ِ ذخیره‌شده همان since ِ همین pull است؛ pull ِ بعدی (since = again.cursor) آن را جلو می‌برد
  assert.equal(await storedCursor('shop', shop.shopId, 'dev-b'), first.cursor);
  await ok(pull(shop.token, 'dev-b', again.cursor));
  assert.equal(await storedCursor('shop', shop.shopId, 'dev-b'), again.cursor);

  //  برنامهٔ تازه با `since` ِ صریح در push: همان نتیجه
  const explicit = await lostPullThenEdit(await ownerWithShop('قطع-روشن-۲', { flag: true }), 'p-lost-2', { pushSince: true });
  assert.equal(explicit.conflicts.length, 1);
});

test('۲) پرچمِ خاموش ⇒ همان رفتارِ امروز: cursor = آنچه فرستاده شد، `since` ِ push نادیده', async () => {
  const shop = await ownerWithShop('قطع-خاموش');
  const { first, sent, conflicts } = await lostPullThenEdit(shop, 'p-off', { pushSince: true });
  //  همان یافتهٔ #449 — بی پرچم دست نخورده
  assert.equal(conflicts.length, 0);
  assert.ok(sent > first.cursor);
  assert.equal(await storedCursor('shop', shop.shopId, 'dev-b'), sent, 'بی پرچم cursor همان آنچه فرستاده شد است');
  //  `since` ِ کوچک در push cursor را پایین نمی‌آورد
  await ok(push(shop.token, 'dev-b', [upd('p-off', { name: 'برنجِ ۲' })], { since: 0 }));
  assert.equal(await storedCursor('shop', shop.shopId, 'dev-b'), sent);
});

/**
 * یک گردشِ عادی (push ⇐ pull، همان ترتیبِ برنامه‌ها) با یک ویرایشِ «دیده‌شده» و یک تعارضِ واقعی.
 * خروجی بی شناسه‌ها و شماره‌های سراسری، تا دو دکان را بشود مو‌به‌مو مقایسه کرد.
 */
async function normalFlow({ token, shopId }, { pushSince }) {
  const log = [];
  const cursors = { 'dev-a': 0, 'dev-b': 0 };
  const extra = (dev) => (pushSince ? { since: cursors[dev] } : {});
  const shape = (r) => ({
    ops: r.ops.map((o) => [o.device_id, o.table, o.row_id, o.type, o.fields]),
    has_more: r.has_more, at_head: r.cursor === r.head,
  });
  const cycle = async (dev, ops, limit) => {
    if (ops.length) {
      const p = await ok(push(token, dev, ops, extra(dev)));
      log.push(['push', dev, p.results.map((x) => [x.status, x.conflicts])]);
    }
    for (let i = 0; i < 10; i++) {
      const r = await ok(pull(token, dev, cursors[dev], limit));
      log.push(['pull', dev, shape(r)]);
      cursors[dev] = r.cursor;
      if (!r.has_more) break;
    }
  };
  await cycle('dev-a', [ins('p-n', { name: 'چای', price: 100 }), ins('p-m', { name: 'قند', price: 50 })]);
  await cycle('dev-b', []);
  await cycle('dev-a', [upd('p-n', { price: 200 })]);
  await cycle('dev-b', []);                                   //  B قیمتِ ۲۰۰ را می‌بیند
  await cycle('dev-b', [upd('p-n', { price: 250 })]);         //  ⇒ آگاهانه: تعارض نیست
  await cycle('dev-a', [upd('p-m', { price: 60 })]);          //  B این را ندیده…
  await cycle('dev-b', [upd('p-m', { price: 70 })], 1);       //  ⇒ تعارضِ واقعی؛ pull صفحه‌به‌صفحه
  await cycle('dev-a', [upd('p-n', { name: 'چای سبز' })]);    //  فیلدِ دیگر: بی تعارض
  await cycle('dev-b', [upd('p-n', { price: 260 })]);
  await cycle('dev-a', []);
  const conflicts = (await conflictsOf(shopId, 'p-n')).concat(await conflictsOf(shopId, 'p-m'))
    .map((c) => [c.field, c.loser_value, c.winner_value, c.loser_device, c.winner_device]);
  return { log, conflicts, rows: [await rowData(shopId, 'p-n'), await rowData(shopId, 'p-m')] };
}

test('۳) گردشِ عادی با `since` در push ⇒ مو‌به‌مو مثلِ پرچمِ خاموش', async () => {
  const off = await normalFlow(await ownerWithShop('عادی-خاموش'), { pushSince: true });
  const on = await normalFlow(await ownerWithShop('عادی-روشن', { flag: true }), { pushSince: true });
  assert.deepEqual(on, off);
  //  و خودِ مرجع درست است: فقط همان یک تعارضِ واقعی
  assert.deepEqual(off.conflicts, [['price', 60, 70, 'dev-a', 'dev-b']]);
  assert.deepEqual(off.rows, [{ name: 'چای سبز', price: 260 }, { name: 'قند', price: 70 }]);
});

test('۴) برنامهٔ بی `since` در push با پرچمِ روشن ⇒ محتاط: تعارضِ اضافه ممکن، تعارضِ گم‌شده نه', async () => {
  const off = await normalFlow(await ownerWithShop('کهنه-خاموش'), { pushSince: false });
  const on = await normalFlow(await ownerWithShop('کهنه-روشن', { flag: true }), { pushSince: false });
  //  داده و پاسخ‌های pull همان؛ فقط دفترِ تعارض فرق دارد
  assert.deepEqual(on.rows, off.rows);
  assert.deepEqual(on.log.filter((l) => l[0] === 'pull'), off.log.filter((l) => l[0] === 'pull'));
  for (const c of off.conflicts) assert.ok(on.conflicts.some((x) => JSON.stringify(x) === JSON.stringify(c)), `تعارضِ ${JSON.stringify(c)} گم شد`);
  //  ۲۵۰ ِ B پس از دیدنِ ۲۰۰ ِ A: سرور (بی since ِ push) نمی‌داند پاسخِ pull ِ پیشین رسیده یا نه ⇒ ثبت
  assert.ok(on.conflicts.some((x) => x[0] === 'price' && x[1] === 200 && x[2] === 250), JSON.stringify(on.conflicts));
});

test('۵) pull ِ تکراری بی‌اثر است: همان پاسخ، همان cursor ِ ذخیره، بی نوشتن در دفتر', async () => {
  const shop = await ownerWithShop('تکرار', { flag: true });
  await ok(push(shop.token, 'dev-a', [1, 2, 3, 4, 5].map((i) => ins(`p-r${i}`, { name: `کالا ${i}`, price: i }))));
  const countOf = async () => Number((await db.one(
    "SELECT (SELECT COUNT(*) FROM oplog WHERE app='shop' AND account_id=$1) + (SELECT COUNT(*) FROM sync_rows WHERE app='shop' AND account_id=$1) AS n",
    [shop.shopId])).n);
  const writes = await countOf();

  let since = 0;
  const seen = [];
  for (let page = 0; page < 5; page++) {
    const a = await ok(pull(shop.token, 'dev-b', since, 2));
    const b = await ok(pull(shop.token, 'dev-b', since, 2));
    assert.deepEqual(b, a, 'pull ِ دوباره با همان since باید همان پاسخ را بدهد');
    assert.equal(await storedCursor('shop', shop.shopId, 'dev-b'), since, 'cursor ِ ذخیره همان since است');
    seen.push(...a.ops.map((o) => o.row_id));
    since = a.cursor;
    if (!a.has_more) break;
  }
  assert.deepEqual(seen, ['p-r1', 'p-r2', 'p-r3', 'p-r4', 'p-r5']);
  assert.equal(await countOf(), writes, 'pull نباید در oplog یا sync_rows بنویسد');
  //  یک pull ِ دیگر از سرِ دفتر ⇒ چیزی نمی‌آید و cursor تأیید می‌شود
  const tail = await ok(pull(shop.token, 'dev-b', since, 2));
  assert.deepEqual(tail.ops, []);
  assert.equal(await storedCursor('shop', shop.shopId, 'dev-b'), since);
});

test('۶) پرچم پس از یک قطع روشن شود ⇒ از نخستین pull ِ بعدی cursor همان since است، نه بیشینهٔ کهنه', async () => {
  const shop = await ownerWithShop('روشن-شدنِ-دیر');
  const { token, shopId } = shop;
  await ok(push(token, 'dev-a', [ins('p-late', { name: 'روغن', price: 10 })]));
  const first = await ok(pull(token, 'dev-b', 0));
  await ok(push(token, 'dev-a', [upd('p-late', { price: 20 })]));
  await lostPull(token, 'dev-b', first.cursor);                    //  بی پرچم: cursor = سرِ دفتر (جلوتر از B)
  assert.ok(await storedCursor('shop', shopId, 'dev-b') > first.cursor);
  await setFlag(shopId, true);
  await lostPull(token, 'dev-b', first.cursor);                    //  باز هم نرسید — حالا با پرچم
  assert.equal(await storedCursor('shop', shopId, 'dev-b'), first.cursor);
  await ok(push(token, 'dev-b', [upd('p-late', { price: 30 })]));
  assert.equal((await conflictsOf(shopId, 'p-late')).length, 1);
});

test('۷) `since` ِ نادرست در push نادیده؛ `since` ِ بزرگ‌تر از سرِ دفتر بریده می‌شود', async () => {
  const shop = await ownerWithShop('since-نادرست', { flag: true });
  const { token, shopId } = shop;
  await ok(push(token, 'dev-a', [ins('p-v', { name: 'نمک', price: 5 })]));
  const first = await ok(pull(token, 'dev-b', 0));
  assert.equal(await storedCursor('shop', shopId, 'dev-b'), 0);
  for (const bad of [-5, 'abc', 1.5, null, '', true, [3], { n: 1 }]) {
    const r = await ok(push(token, 'dev-b', [upd('p-v', { name: `نمک ${JSON.stringify(bad)}` })], { since: bad }));
    assert.equal(r.results[0].status, 'applied');
    assert.equal(await storedCursor('shop', shopId, 'dev-b'), 0, `since=${JSON.stringify(bad)} نباید cursor را عوض کند`);
  }
  //  عدد به شکلِ متن (همان شکلِ querystring) پذیرفته است
  await ok(push(token, 'dev-b', [upd('p-v', { name: 'نمکِ متنی' })], { since: String(first.cursor) }));
  assert.equal(await storedCursor('shop', shopId, 'dev-b'), first.cursor);

  //  ادعای «همه‌چیز را دیده‌ام» از سرِ دفتر بالاتر نمی‌رود
  const head = (await ok(status(token, 'dev-b'))).head;
  await ok(push(token, 'dev-b', [upd('p-v', { price: 6 })], { since: 1e15 }));
  assert.equal(await storedCursor('shop', shopId, 'dev-b'), head);
  await ok(pull(token, 'dev-b', 1e15));
  const after = (await ok(status(token, 'dev-b'))).head;
  assert.equal(await storedCursor('shop', shopId, 'dev-b'), after);
});

test('۸) پمپ: همان مسیر، حتی با پرچمِ `all` و پرچمِ خودِ پمپ ⇒ رفتارِ امروز', async () => {
  const stamp = { 'x-app-id': 'tohid-pump-app', 'x-app-version': '3.1.186', 'x-app-platform': 'windows' };
  const u = await h.newUser('پمپ‌دار', 'pump');
  const made = await h.post('/api/pump', { name: 'پمپِ آزمون', code: 'ack-1' }, { token: u.accessToken, headers: stamp });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const station = made.body.station.id;
  const bound = await h.post('/api/pump/device/bind',
    { device: { uid: `duid-${crypto.randomBytes(4).toString('hex')}`, name: 'کامپیوترِ پمپ', platform: 'windows' } },
    { token: u.accessToken, headers: stamp });
  assert.equal(bound.status, 201, JSON.stringify(bound.body));
  const token = bound.body.deviceToken;
  await setFlag('all', true);
  await setFlag(station, true);
  try {
    const pPush = (device, ops, extra = {}) => h.post('/api/sync/v1/push',
      { device_id: device, schema_version: 1, ops, ...extra }, { token, headers: stamp });
    const pPull = (device, since) => h.get(`/api/sync/v1/pull?device_id=${device}&since=${since}`, { token, headers: stamp });
    const row = `ROWACK${crypto.randomBytes(4).toString('hex')}`.toUpperCase();
    const entry = (type, fields) => ({ op_id: crypto.randomUUID(), table: 'SafeEntry', row_id: row, type, ts: Date.now(), fields });

    assert.equal((await pPush('pc-1', [entry('insert', { Title: 'الف', Amount: '100', MonthKey: '1405-08' })])).status, 200);
    const first = await pPull('pc-2', 0);
    assert.equal(first.status, 200);
    assert.equal(await storedCursor('station', station, 'pc-2'), first.body.cursor, 'پمپ: cursor همان آنچه فرستاده شد');
    assert.equal((await pPush('pc-1', [entry('update', { Amount: '200' })])).status, 200);
    const second = await pPull('pc-2', first.body.cursor);
    assert.equal(await storedCursor('station', station, 'pc-2'), second.body.cursor);
    //  `since` ِ push برای پمپ نادیده: cursor پایین نمی‌آید و تعارضِ «دیده‌شده» ساخته نمی‌شود
    assert.equal((await pPush('pc-2', [entry('update', { Amount: '300' })], { since: 0 })).status, 200);
    assert.equal(await storedCursor('station', station, 'pc-2'), second.body.cursor);
    const c = await db.one("SELECT COUNT(*)::int AS n FROM sync_conflicts WHERE app='pump' AND account_id=$1", [station]);
    assert.equal(Number(c.n), 0);
  } finally {
    await setFlag('all', false);
  }
});
