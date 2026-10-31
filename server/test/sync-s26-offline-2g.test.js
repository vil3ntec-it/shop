'use strict';
/**
 * S26 · ۳۰ روز آفلاین (SYN-01، SYN-04) — سه دستگاه، فروش، برگشت، قرض، خرید و شمارش، بعد وصل شدن روی 2G.
 *
 * کدِ زیرِ آزمون همان موتورِ همگام‌سازیِ وب است (`license/sync-engine.js` در `vm` با `localStorage` ِ خودش و
 * `license/sync-core.js`) روبه‌روی سرورِ واقعی، از راهِ یک پراکسیِ TCPِ واقعی برای هر دستگاه که بایت‌های رفت و
 * برگشت را می‌شمرد، اتصال را وسطِ بدنه یا پاسخ می‌بُرد و در آزمونِ دوم پهنای باند و تأخیرِ 2G را واقعاً اعمال می‌کند.
 * ساعتِ هر دستگاه ۳۰ روز جلو می‌رود (`ts` و ULID ِ opها از همان ساعت) و هر روز چند «ذخیره» دارد (`recordChange`).
 *
 *   ۱) ۳۰ روز آفلاین روی سه دستگاه ⇒ وصل شدن با قطعِ وسطِ push، پاسخِ گم‌شدهٔ push و pull، و ادامه از همان‌جا:
 *      • هر op دقیقاً یک بار روی سرور (شمار و op_id ِ یکتا)، هیچ op ِ ردشده یا کنارگذاشته
 *      • دفترِ هر سه دستگاه ردیف‌به‌ردیف = Snapshot ِ سرور؛ موجودیِ هر کالا و ماندهٔ هر قرض‌دار در هر چهار یکی
 *      • زمانِ همگام‌سازیِ هر دستگاه روی نیم‌رخِ 2G (بایت‌های واقعیِ سیم ÷ پهنای باند + رفت‌وبرگشت × RTT، به‌اضافهٔ
 *        کارِ سرور و موتور = زمانِ دیواریِ همان همگام‌سازی)، برون‌یابیِ خطی تا ۱۰۰٬۰۰۰ op ِ S26. سد: بخشِ سیم ≤ ۴۵
 *        دقیقه (۱۵ دقیقه جا برای کارِ سرور که روی ماشینِ CI نمایندهٔ سرورِ واقعی نیست)؛ عددِ کامل در گزارشِ آزمون
 *   ۲) همان مدل روی پراکسیِ واقعاً کُند (2G) سنجیده می‌شود: زمانِ دیواری با پیش‌بینیِ مدل می‌خواند.
 *
 * اندازه: `S26_OPS` (پیش‌فرض ۴۵۰۰ برای CI؛ S26 خودش ۱۰۰٬۰۰۰ — همین پرونده با `S26_OPS=100000`).
 * مرزِ راست‌گویی: این موتورِ وب است؛ موتورِ اندروید همین پروتکل و همین سرور را دارد ولی این‌جا اجرا نمی‌شود.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const net = require('node:net');
const http = require('node:http');
const zlib = require('node:zlib');
const h = require('./helpers');
const db = require('../src/db');
const syncV1 = require('../src/lib/sync-v1');

const LICENSE = path.join(__dirname, '..', '..', 'license');
const core = require(path.join(LICENSE, 'sync-core.js'));
const ENGINE_SRC = fs.readFileSync(path.join(LICENSE, 'sync-engine.js'), 'utf8');

const OPS = Math.max(300, Number(process.env.S26_OPS || 4500));
const S26_OPS = 100000;
const DAYS = 30;
const SAVES_PER_DAY = Number(process.env.S26_SAVES_PER_DAY || 8);
const DAY = 24 * 3600 * 1000;
/** نیم‌رخِ «2G» ِ WebPageTest: ۲۸۰ kbps پایین، ۲۵۶ kbps بالا، RTT ۸۰۰ms */
const G2 = { name: '2G (WebPageTest)', down: 280000 / 8, up: 256000 / 8, rtt: 800 };
const LIMIT_MIN = 60;

/* ── عددِ شبه‌تصادفیِ تکرارپذیر ── */
function rng(seed) {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6D2B79F5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/* ── پراکسیِ TCP ِ یک دستگاه: شمارشِ بایت، بُریدن، و (اختیاری) پهنای باند و تأخیرِ واقعی ── */
const PULL_LINE = 'GET /api/sync/v1/pull?';
function linkProxy(targetPort, shape = null) {
  //  cutPullAfter: بُرشِ پاسخ فقط از نخستین `GET …/pull` به بعد (پاسخِ push ِ پیش از آن دست نمی‌خورد)
  const st = { up: 0, down: 0, conns: 0, cutUpAfter: -1, cutDownAfter: -1, cutPullAfter: -1, cuts: 0 };
  const sockets = new Set();
  const free = { up: 0, down: 0 };
  function send(dir, to, chunk) {
    if (!shape) { to.write(chunk); return; }
    const rate = dir === 'up' ? shape.up : shape.down;
    for (let i = 0; i < chunk.length; i += 1460) {
      const piece = chunk.subarray(i, i + 1460);
      const at = Math.max(Date.now(), free[dir]) + (piece.length * 1000) / rate;
      free[dir] = at;
      setTimeout(() => { if (!to.destroyed) to.write(piece); }, Math.max(0, at + shape.rtt / 2 - Date.now()));
    }
  }
  const server = net.createServer((client) => {
    st.conns++;
    const up = net.connect(targetPort, '127.0.0.1');
    for (const s of [client, up]) { sockets.add(s); s.on('close', () => sockets.delete(s)); s.on('error', () => { }); }
    const cut = () => { st.cuts++; client.destroy(); up.destroy(); };
    client.on('data', (c) => {
      st.up += c.length;
      if (st.cutPullAfter >= 0 && c.subarray(0, PULL_LINE.length).toString('latin1') === PULL_LINE) {
        st.cutDownAfter = st.cutPullAfter;
        st.cutPullAfter = -1;
      }
      if (st.cutUpAfter >= 0) {
        st.cutUpAfter -= c.length;
        if (st.cutUpAfter < 0) { st.cutUpAfter = -1; return cut(); }
      }
      return send('up', up, c);
    });
    up.on('data', (c) => {
      st.down += c.length;
      if (st.cutDownAfter >= 0) {
        st.cutDownAfter -= c.length;
        if (st.cutDownAfter < 0) { st.cutDownAfter = -1; return cut(); }
      }
      return send('down', client, c);
    });
    client.on('end', () => up.end());
    up.on('end', () => client.end());
  });
  return new Promise((resolve) => server.listen(0, '127.0.0.1', () => resolve({
    port: server.address().port, st,
    close: () => new Promise((r) => { for (const s of sockets) s.destroy(); server.close(r); }),
  })));
}

/* ── `authFetch` ِ دستگاه از راهِ پراکسی (یک اتصالِ ماندگار، مثلِ OkHttp) + شمارشِ بدنه و gzip ِ همان بدنه ── */
function transport(port, token, deviceId, meter) {
  const fresh = () => new http.Agent({ keepAlive: true, maxSockets: 1 });
  let agent = fresh();
  function authFetch(p, o = {}) {
    const body = o.body === undefined || o.body === null ? null : Buffer.from(JSON.stringify(o.body), 'utf8');
    return new Promise((resolve, reject) => {
      const req = http.request({
        host: '127.0.0.1', port, path: p, method: o.method || 'GET', agent, timeout: 600000,
        headers: {
          Authorization: `Bearer ${token}`, 'X-App': 'shop', 'X-Device': deviceId,
          ...(body ? { 'Content-Type': 'application/json', 'Content-Length': body.length } : {}),
        },
      }, (res) => {
        const chunks = [];
        res.on('data', (c) => chunks.push(c));
        res.on('aborted', () => reject(Object.assign(new Error('پاسخ نیمه‌کاره ماند'), { code: 'aborted' })));
        res.on('error', reject);
        res.on('end', () => {
          const buf = Buffer.concat(chunks);
          meter.requests++;
          meter.reqBody += body ? body.length : 0;
          meter.resBody += buf.length;
          meter.reqGz += body ? zlib.gzipSync(body).length : 0;
          meter.resGz += buf.length ? zlib.gzipSync(buf).length : 0;
          let json = {};
          try { json = JSON.parse(buf.toString('utf8') || '{}'); } catch { json = {}; }
          if (res.statusCode >= 400) {
            return reject(Object.assign(new Error(json.error || `http ${res.statusCode}`), { status: res.statusCode, code: json.code }));
          }
          return resolve(json);
        });
      });
      req.on('error', reject);
      req.on('timeout', () => req.destroy(new Error('timeout')));
      if (body) req.write(body);
      req.end();
    });
  }
  //  گوشیِ برگشته از آفلاین اتصالِ ماندگارِ پیشین را ندارد. بی این، نخستین درخواست گاهی روی سوکتی می‌رفت که سرور
  //  در همان لحظه به‌خاطرِ بیکاری می‌بست («socket hang up») و قطعِ ساختگیِ پراکسی اصلاً به کار نمی‌آمد.
  authFetch.dropConnections = () => { agent.destroy(); agent = fresh(); };
  return authFetch;
}

/* ── یک دستگاه: موتورِ وب در vm، ساعتِ خودش، دفترِ صفحه در یک جعبه ── */
function device({ name, token, userId, port, ledger }) {
  const clock = { now: Date.now() };
  const made = { ops: 0 };
  //  همان هسته؛ فقط `ts` ِ تفاضل از ساعتِ این دستگاه (ULID هم از همان)
  const coreHere = Object.assign(Object.create(core), {
    diff(a, b, o) {
      const out = core.diff(a, b, { ...(o || {}), ts: clock.now });
      made.ops += out.ops.length;
      return out;
    },
  });
  const meter = { requests: 0, reqBody: 0, resBody: 0, reqGz: 0, resGz: 0 };
  const authFetch = transport(port, token, name, meter);
  const box = new Map();
  const ls = { getItem: (k) => (box.has(k) ? box.get(k) : null), setItem: (k, v) => { box.set(k, String(v)); }, removeItem: (k) => box.delete(k) };
  const window = {
    TohidSyncCore: coreHere,
    TohidApiConfig: { baseUrl: () => `http://127.0.0.1:${port}` },
    TohidAccount: {
      configured: () => true, isSignedIn: () => true, account: () => ({ userId }),
      deviceId: () => name, appId: () => 'shop', on() { }, heartbeat: async () => ({}),
      accessToken: async () => token, authFetch,
    },
    addEventListener() { }, dispatchEvent() { },
  };
  const ctx = vm.createContext({
    window, localStorage: ls, navigator: { onLine: true }, document: { addEventListener() { } },
    URL, console, setTimeout: () => 0, clearTimeout() { }, setInterval: () => 0, clearInterval() { },
  });
  vm.runInContext(ENGINE_SRC, ctx);
  const sync = window.TohidSync;
  const box2 = { data: ledger };
  sync.bind({ read: () => JSON.parse(JSON.stringify(box2.data)), write: (d) => { box2.data = JSON.parse(JSON.stringify(d)); } });
  return {
    name, sync, clock, made, meter, box, ledger: box2,
    save(mutate) {
      const d = JSON.parse(JSON.stringify(box2.data));
      mutate(d);
      box2.data = d;
      return sync.recordChange('save');
    },
    queueBytes() {
      let n = 0;
      for (const [k, v] of box) if (/-oplog$/.test(k)) n += Buffer.byteLength(v, 'utf8');
      return n;
    },
    /** بایتِ opهای دستهٔ بعدیِ push، با همان `OpQueue.batch` ِ موتور (بدنهٔ واقعی کمی بزرگ‌تر است) */
    nextBatchBytes() {
      for (const [k, v] of box) {
        if (!/-oplog$/.test(k)) continue;
        const q = new core.OpQueue({ read: (key) => (key === k ? JSON.parse(v) : null), write() { } }, k);
        return Buffer.byteLength(JSON.stringify(q.batch()), 'utf8');
      }
      return 0;
    },
    dropConnections: () => authFetch.dropConnections(),
  };
}

/* ── کارِ یک روزِ دکان روی دفترِ یک دستگاه (فقط ردیف‌های خودش؛ قیمت‌گذاری S28 است، نه این‌جا) ── */
function shopDay(dev, rand, products, debtors, budget) {
  const id = () => core.ulid(dev.clock.now);
  const pick = (list) => list[Math.floor(rand() * list.length)];
  let used = 0;
  const step = Math.max(1, Math.round(budget / SAVES_PER_DAY));
  for (let save = 0; save < SAVES_PER_DAY && used < budget; save++) {
    dev.clock.now += Math.floor(DAY / (SAVES_PER_DAY + 1));
    const before = dev.made.ops;
    dev.save((L) => {
      for (const k of ['sales', 'saleItems', 'returns', 'transactions', 'purchases', 'warehouseEntries', 'stockMovements']) L[k] = L[k] || [];
      const mine = (r) => r.deviceId === dev.name;
      let n = 0;
      while (n < step) {
        const r = rand();
        if (r < 0.55) {                                   // فروش (نقد یا قرضی)
          const saleId = id();
          const items = 1 + Math.floor(rand() * 3);
          let total = 0;
          for (let i = 0; i < items; i++) {
            const p = pick(products);
            const qty = 1 + Math.floor(rand() * 3);
            total += qty * p.price;
            L.saleItems.push({ id: id(), saleId, productId: p.id, qty, price: p.price, total: qty * p.price, returnedQty: 0, deviceId: dev.name });
          }
          const debt = rand() < 0.3;
          const d = pick(debtors);
          L.sales.push({ id: saleId, deviceId: dev.name, date: new Date(dev.clock.now).toISOString(), total,
            paidAmount: debt ? Math.floor(total / 2) : total, remaining: debt ? total - Math.floor(total / 2) : 0,
            debtorId: debt ? d.id : '', channel: 'pos' });
          if (debt) L.transactions.push({ id: id(), debtorId: d.id, type: 'debt', amount: total - Math.floor(total / 2), saleId, deviceId: dev.name });
          n += items + 1 + (debt ? 1 : 0);
        } else if (r < 0.65) {                            // برگشت از فروشِ خودش
          const item = L.saleItems.filter(mine).find(x => x.qty > x.returnedQty);
          if (!item) continue;
          item.returnedQty += 1;
          L.returns.push({ id: id(), saleId: item.saleId, itemId: item.id, productId: item.productId, qty: 1, amount: item.price, deviceId: dev.name });
          n += 2;
        } else if (r < 0.78) {                            // پرداختِ قرض روی فاکتورِ قرضیِ خودش
          const sale = L.sales.filter(mine).find(x => x.remaining > 0);
          if (!sale) continue;
          const pay = Math.min(sale.remaining, 50 + Math.floor(rand() * 200));
          sale.paidAmount += pay;
          sale.remaining -= pay;
          L.transactions.push({ id: id(), debtorId: sale.debtorId, type: 'payment', amount: pay, saleId: sale.id, deviceId: dev.name });
          n += 2;
        } else if (r < 0.9) {                             // خرید از تأمین‌کننده + ورودِ انبار
          const pid = id();
          const lines = 1 + Math.floor(rand() * 2);
          let total = 0;
          for (let i = 0; i < lines; i++) {
            const p = pick(products);
            const qty = 5 + Math.floor(rand() * 20);
            total += qty * Math.floor(p.price * 0.8);
            L.warehouseEntries.push({ id: id(), purchaseId: pid, productId: p.id, qty, cost: Math.floor(p.price * 0.8), deviceId: dev.name });
          }
          L.purchases.push({ id: pid, supplierId: 'sup-1', total, paidAmount: total, debt: 0, deviceId: dev.name });
          n += lines + 1;
        } else {                                          // شمارشِ انبار (اصلاح)
          const p = pick(products);
          L.stockMovements.push({ id: id(), productId: p.id, qty: rand() < 0.5 ? -1 : 1, reason: 'count', deviceId: dev.name });
          n += 1;
        }
      }
    });
    used += dev.made.ops - before;
  }
  return used;
}

/** موجودیِ هر کالا و ماندهٔ هر قرض‌دار از روی خودِ ردیف‌ها (همان جمع‌پذیریِ دفتر) */
function figures(L) {
  const stock = {};
  const add = (p, q) => { stock[p] = (stock[p] || 0) + q; };
  for (const w of L.warehouseEntries || []) add(w.productId, w.qty);
  for (const m of L.stockMovements || []) add(m.productId, m.qty);
  for (const i of L.saleItems || []) add(i.productId, -i.qty + i.returnedQty);
  const debt = {};
  for (const t of L.transactions || []) debt[t.debtorId] = (debt[t.debtorId] || 0) + (t.type === 'debt' ? t.amount : -t.amount);
  let paid = 0; let remaining = 0;
  for (const s of L.sales || []) { paid += s.paidAmount; remaining += s.remaining; }
  return { stock, debt, paid, remaining };
}

const SYNCED = ['products', 'debtors', 'sales', 'saleItems', 'returns', 'transactions', 'purchases', 'warehouseEntries', 'stockMovements'];
function rowsOf(L) {
  const out = {};
  for (const k of SYNCED) out[k] = [...(L[k] || [])].sort((a, b) => String(a.id).localeCompare(String(b.id)));
  return out;
}

/** ردیف‌به‌ردیف، با پیامِ کوتاه (deepEqual روی هزاران ردیف پیامِ خطای چندگیگی می‌ساخت) */
function sameRows(actual, want, label) {
  for (const k of SYNCED) {
    const a = actual[k];
    const w = want[k];
    if (a.length !== w.length) assert.fail(`${label}: ${k} ${a.length} ردیف، سرور ${w.length}`);
    for (let i = 0; i < a.length; i++) {
      if (core.canonical(a[i]) !== core.canonical(w[i])) {
        assert.fail(`${label}: ${k}[${i}] ${core.canonical(a[i]).slice(0, 300)} ≠ ${core.canonical(w[i]).slice(0, 300)}`);
      }
    }
  }
}

async function headOf(shopId) {
  const r = await db.one(`SELECT COALESCE(MAX(server_seq),0)::bigint AS head FROM oplog WHERE app='shop' AND account_kind='shop' AND account_id=$1`, [shopId]);
  return Number(r.head);
}

/** همگام‌سازی تا ته: هر دور یک `sync()` ِ خودِ موتور (خطا را خودش نگه می‌دارد؛ این‌جا جای زمان‌سنجِ عقب‌نشینی) */
async function drain(dev, shopId, max = 400) {
  const t0 = Date.now();
  let rounds = 0;
  for (; rounds < max; rounds++) {
    if (dev.sync.queueSize() === 0 && dev.sync.status().cursor >= await headOf(shopId) && !dev.sync.status().lastError) break;
    await dev.sync.sync();
  }
  assert.ok(rounds < max, `${dev.name} به ته نرسید: ${JSON.stringify(dev.sync.status())}`);
  dev.busyMs = (dev.busyMs || 0) + (Date.now() - t0);
  return rounds;
}

/** زمانِ مدل روی نیم‌رخ: بایتِ سیم ÷ پهنای باند + درخواست × RTT + کارِ خودِ سرور و موتور (دقیقه) */
function modelMinutes(up, down, requests, busyMs = 0, shape = G2) {
  return (up / shape.up + down / shape.down + requests * shape.rtt / 1000 + busyMs / 1000) / 60;
}

let owner; let shopId; let port;

test.before(async () => {
  const base = await h.start();
  port = Number(new URL(base).port);
  await syncV1.syncSchemaTable();
  const u = await h.newUser('صاحبِ دکانِ S26', 'shop');
  const made = await h.api('POST', '/api/shop', { token: u.accessToken, body: { name: 'دکانِ S26' } });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  owner = u;
  shopId = made.body.shop.id;
});
test.after(async () => { await h.stop(); });

test(`۱) S26: سه دستگاه ${DAYS} روز آفلاین (${OPS} op، برون‌یابی تا ${S26_OPS}) ⇒ بی گم شدن، بی تکرار، ادامه از جای قطع، دفترها = سرور، 2G ≤ ${LIMIT_MIN} دقیقه`, { timeout: 3 * 3600 * 1000 }, async (t) => {
  const products = Array.from({ length: 200 }, (_, i) => ({ id: `p-${String(i).padStart(3, '0')}`, name: `کالای ${i}`, price: 20 + (i % 37) * 15 }));
  const debtors = Array.from({ length: 20 }, (_, i) => ({ id: `d-${String(i).padStart(2, '0')}`, name: `قرض‌دار ${i}`, phone: `0700${String(i).padStart(6, '0')}` }));
  const links = [];
  const devs = [];
  for (const name of ['s26-a', 's26-b', 's26-c']) {
    const link = await linkProxy(port);
    links.push(link);
    devs.push(device({ name, token: owner.accessToken, userId: `${owner.user?.id || 'u'}`, port: link.port,
      ledger: name === 's26-a' ? { products, debtors } : {} }));
  }
  try {
    //  روزِ صفر: A دفترِ پایه را می‌دهد، B و C از Snapshot
    assert.equal((await devs[0].sync.firstSync()).did, 'baseline');
    await drain(devs[0], shopId);
    for (const d of devs.slice(1)) {
      assert.equal((await d.sync.firstSync()).did, 'snapshot');
      assert.equal(d.ledger.data.products.length, 200);
    }
    const baseOps = devs[0].made.ops;
    const startOps = Number((await db.one(`SELECT COUNT(*)::int AS n FROM oplog WHERE account_kind='shop' AND account_id=$1`, [shopId])).n);
    assert.equal(startOps, baseOps);
    for (const d of devs) { d.meter.requests = d.meter.reqBody = d.meter.resBody = d.meter.reqGz = d.meter.resGz = 0; d.busyMs = 0; }
    for (const l of links) { l.st.up = 0; l.st.down = 0; }

    //  ۳۰ روزِ آفلاین: هیچ درخواستی (همگام‌سازی صدا زده نمی‌شود)
    const rand = rng(26);
    const perDevice = Math.floor(OPS / 3);
    let peakQueue = 0;
    for (const d of devs) {
      let left = perDevice;
      for (let day = 0; day < DAYS && left > 0; day++) {
        left -= shopDay(d, rand, products, debtors, Math.ceil(left / (DAYS - day)));
      }
      peakQueue = Math.max(peakQueue, d.queueBytes());
    }
    for (const d of devs) assert.equal(d.meter.requests, 0, 'آفلاین بود');
    const offline = devs.reduce((s, d) => s + d.made.ops, 0) - baseOps;
    assert.ok(offline >= OPS * 0.95, `op ِ ساخته‌شده ${offline}`);
    for (const d of devs) assert.equal(d.sync.queueSize(), d.made.ops - (d === devs[0] ? baseOps : 0), `${d.name}: همه در صف`);

    //  وصل شدن: A با قطعِ وسطِ بدنهٔ push و پاسخِ گم‌شدهٔ push، B با پاسخِ گم‌شدهٔ pull، C بی قطع؛ بعد دورِ دوم
    const onServer = async () => Number((await db.one(`SELECT COUNT(*)::int AS n FROM oplog WHERE account_kind='shop' AND account_id=$1`, [shopId])).n);
    const queuedA = devs[0].sync.queueSize();
    for (const d of devs) d.dropConnections();
    //  وسطِ بدنهٔ نخستین دسته: سرور چیزی نمی‌گیرد. اندازه از خودِ دسته؛ عددِ ثابت (۱۵۰KB ِ پیشین) از دستهٔ
    //  ۲۰۰ opی (~۶۳KB) بزرگ‌تر بود و دو دستهٔ نخست کامل می‌رسیدند
    const firstBatch = devs[0].nextBatchBytes();
    assert.ok(firstBatch > 8 * 1024, `دستهٔ نخست ${firstBatch} بایت`);
    links[0].st.cutUpAfter = Math.floor(firstBatch / 2);
    await devs[0].sync.sync();
    assert.ok(devs[0].sync.status().lastError, 'قطعِ وسطِ بدنه دیده شد');
    assert.equal(links[0].st.cuts, 1, 'خطا از همان قطعِ پراکسی است');
    assert.equal(devs[0].sync.queueSize(), queuedA);
    assert.equal(await onServer(), startOps);
    links[0].st.cutDownAfter = 10;             // سرور نخستین دسته را گرفت، پاسخش نرسید
    await devs[0].sync.sync();
    assert.ok(devs[0].sync.status().lastError, 'پاسخِ گم‌شده دیده شد');
    assert.equal(links[0].st.cuts, 2);
    assert.equal(devs[0].sync.queueSize(), queuedA, 'صف دست نخورد');
    assert.ok(await onServer() > startOps, 'ولی سرور همان دسته را دارد');
    await drain(devs[0], shopId);              // همان دسته دوباره ⇒ «تکراری»، بقیه از همان‌جا
    devs[1].dropConnections();
    links[1].st.cutPullAfter = 64 * 1024;      // پاسخِ pull ِ B وسطِ راه بریده شود (پس از push ِ کاملش)
    await drain(devs[1], shopId);
    assert.equal(links[1].st.cuts, 1, 'پاسخِ pull ِ B بریده شد');
    devs[2].dropConnections();
    await drain(devs[2], shopId);
    for (const d of devs) { d.dropConnections(); await drain(d, shopId); }
    const cuts = links.reduce((s, l) => s + l.st.cuts, 0);
    assert.equal(cuts, 3, `قطع‌ها اعمال شد (${cuts})`);

    //  هر op دقیقاً یک بار
    const total = devs.reduce((s, d) => s + d.made.ops, 0);
    const c = await db.one(`SELECT COUNT(*)::int AS n, COUNT(DISTINCT op_id)::int AS u FROM oplog WHERE account_kind='shop' AND account_id=$1`, [shopId]);
    assert.equal(c.n, total, 'هیچ op گم یا دوتایی نشد');
    assert.equal(c.u, total);
    for (const d of devs) {
      assert.equal(d.sync.queueSize(), 0);
      assert.deepEqual(d.sync.dropped(), [], `${d.name}: هیچ op ِ ردشده یا کنارگذاشته`);
    }

    //  دفترِ هر سه = Snapshot ِ سرور؛ موجودی و مانده در هر چهار یکی
    const snap = await h.api('GET', '/api/sync/v1/snapshot', { token: owner.accessToken, headers: { 'X-App': 'shop' } });
    assert.equal(snap.status, 200);
    const server = core.fromSnapshot(snap.body, {});
    const want = rowsOf(server);
    const wantFig = figures(server);
    for (const d of devs) {
      sameRows(rowsOf(d.ledger.data), want, `${d.name}: دفتر = سرور`);
      assert.deepEqual(figures(d.ledger.data), wantFig, `${d.name}: موجودی و مانده = سرور`);
    }
    assert.ok(Object.keys(wantFig.debt).length > 0 && wantFig.remaining > 0, 'قرض در سناریو هست');

    //  2G: هر دستگاه، بایت‌های واقعیِ سیم
    const scale = S26_OPS / offline;
    for (let i = 0; i < 3; i++) {
      const d = devs[i];
      const { up, down } = links[i].st;
      //  کارِ سرور و موتور همان زمانِ دیواریِ همین همگام‌سازیِ بی‌کُندی است (با قطع‌ها و دوباره‌فرستادن‌ها)
      const raw = modelMinutes(up, down, d.meter.requests, d.busyMs);
      const gzUp = up - d.meter.reqBody + d.meter.reqGz;
      const gzDown = down - d.meter.resBody + d.meter.resGz;
      const gz = modelMinutes(gzUp, gzDown, d.meter.requests, d.busyMs);
      t.diagnostic(`${d.name}: ${d.meter.requests} درخواست، بالا ${(up / 1048576).toFixed(2)}MB، پایین ${(down / 1048576).toFixed(2)}MB، کارِ سرور/موتور ${(d.busyMs / 1000).toFixed(1)}s ⇒ ${G2.name}: ${raw.toFixed(1)} دقیقه (با gzip ِ بدنه‌ها ${gz.toFixed(1)}) · برون‌یابی تا ${S26_OPS} op: ${(raw * scale).toFixed(1)} (gzip ${(gz * scale).toFixed(1)})`);
      //  سد روی بخشِ قطعی (بایتِ سیم و رفت‌وبرگشت)، با ۱۵ دقیقه جا برای کارِ سرور؛ عددِ کامل در گزارش
      const wire = modelMinutes(up, down, d.meter.requests) * scale;
      assert.ok(wire <= LIMIT_MIN - 15, `${d.name}: برون‌یابیِ سیمِ S26 روی 2G ${wire.toFixed(1)} دقیقه > ${LIMIT_MIN - 15}`);
    }
    t.diagnostic(`op ِ آفلاین: ${offline}؛ بیشترین اندازهٔ صف در localStorage: ${(peakQueue / 1048576).toFixed(2)}MB (سقفِ رایجِ مرورگر ~۵MB)؛ قطع‌ها: ${cuts}`);
  } finally {
    for (const l of links) await l.close();
  }
});

test('۲) مدلِ 2G روی پراکسیِ واقعاً کُند: زمانِ دیواری با پیش‌بینیِ مدل می‌خواند (۰٫۷۵ تا ۱٫۳۵ برابر)', { timeout: 600000 }, async (t) => {
  //  دو دکانِ هم‌شکل با همان داده (همان بذر): یکی بی‌کُندی (کارِ سرور و موتور)، یکی روی 2G ِ واقعی
  const run = async (label, shape) => {
    const u = await h.newUser(`صاحبِ دکانِ ${label}`, 'shop');
    const made = await h.api('POST', '/api/shop', { token: u.accessToken, body: { name: `دکانِ ${label}` } });
    assert.equal(made.status, 201);
    const link = await linkProxy(port, shape);
    try {
      const products = Array.from({ length: 20 }, (_, i) => ({ id: `q-${i}`, name: `کالا ${i}`, price: 30 + i }));
      const dev = device({ name: `${label}-a`, token: u.accessToken, userId: `${u.user?.id || label}`, port: link.port,
        ledger: { products, debtors: [{ id: 'd-0', name: 'قرض‌دار' }] } });
      shopDay(dev, rng(2), products, [{ id: 'd-0' }], 400);
      const t0 = Date.now();
      assert.equal((await dev.sync.firstSync()).did, 'baseline');
      await drain(dev, made.body.shop.id);
      assert.equal(dev.sync.queueSize(), 0);
      return { ms: Date.now() - t0, up: link.st.up, down: link.st.down, requests: dev.meter.requests, ops: dev.made.ops };
    } finally {
      await link.close();
    }
  };
  const fast = await run('fast', null);
  const slow = await run('slow', G2);
  const model = modelMinutes(slow.up, slow.down, slow.requests, fast.ms) * 60;
  const wall = slow.ms / 1000;
  t.diagnostic(`${slow.ops} op، ${slow.requests} درخواست، بالا ${(slow.up / 1024).toFixed(0)}KB، پایین ${(slow.down / 1024).toFixed(0)}KB، کارِ سرور/موتور ${(fast.ms / 1000).toFixed(1)}s ⇒ دیواری ${wall.toFixed(1)}s، مدل ${model.toFixed(1)}s`);
  assert.ok(wall >= model * 0.75 && wall <= model * 1.35, `دیواری ${wall.toFixed(1)}s در برابرِ مدل ${model.toFixed(1)}s`);
});
