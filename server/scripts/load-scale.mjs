/**
 * آزمونِ بار — آمیزهٔ «scale» (۲۰۰ دکان × ۵ دستگاه، §۹.۳ بندِ ۹) و دفترِ درستیِ بارساز (PERF-02).
 *
 * `load-test.mjs` این را می‌سازد (`scaleKit`) و سناریوهایش را کنارِ سناریوهای خودش می‌گذارد:
 *   sync3.push   فروش (سند، قلم، حرکتِ منفیِ کالا، تراکنش — یک tx)؛ ۵٪ صفِ برگشت از آفلاین (۱۰ تا ۳۰ فروش)
 *   sync3.edit   ویرایشِ بهای کالا (`products` update) + `priceHistory` در یک tx — چند دستگاه روی همان کالا ⇒ LWW با HLC
 *   sync3.stock  حرکتِ کالا (خرید/تعدیل)
 *   sync3.customer  مشتریِ تازه یا ویرایشِ نامِ مشتریِ همان دستگاه (`debtors`)
 *   sync3.count  انبارگردانیِ باز: ۵ تا ۴۰ ردیفِ شمارشِ تازه (`stockCounts`) در جلسهٔ شمارشِ همان دستگاه؛ هر شمارش تعدیلِ سرور
 *                می‌سازد و هر حرکتِ دیررسیده (صفِ آفلاین، ۵۰۳) با HLC ِ پیش از آن «تعدیلِ دیررسیده» (`v3.lateCount`). شمارش‌های بستهٔ
 *                پیشین (۱۰۰ تا ۱۰ هزار ردیف برای هر دکان) را `countHistory` پیش از بار می‌فرستد
 *   held.*       فاکتورِ نگه‌داشته (`/api/shop/held-invoices`: نگه‌داشتن، ویرایش، فروش/دور انداختن، خواندن)
 *   owner.sub    `GET /api/me/subscription` ِ صاحبِ دکان
 *   admin.*      خلاصه‌های پنلِ مدیر (آمار، خانه، میزِ دکان‌ها، همگام‌سازی، مالی)
 *   xt.probe     کالای «نشانِ» دکانِ دیگر از راهِ ویترینِ این دکان ⇒ باید ۴۰۴ باشد
 *
 * دفتر (`ledger()`): هر op با سرانجامش، جمعِ فروش و حرکتِ هر کالا، بهای برندهٔ LWW، مشتری‌ها، نگه‌داشته‌های زنده،
 * سفارش‌ها و آنچه هر دستگاه در pull دید. `scripts/load-check.js` همین را با پایگاه می‌سنجد.
 * با `retry` (همان `--check`) opِ بی‌پاسخ (مهلت، ۵xx، ۵۰۳ ِ `db_busy`) مثلِ برنامهٔ واقعی در صفِ همان دستگاه می‌ماند و
 * با push ِ بعدی (همان op_id) دوباره می‌رود؛ `drain()` در پایان صف‌ها را خالی و هر دستگاه را تا سرِ دفتر می‌برد.
 */

export const SCALE_MIX = Object.freeze({
  'sync3.pull': 24, 'sync3.push': 14, 'sync3.edit': 4, 'sync3.stock': 4, 'sync3.customer': 3, 'sync3.count': 1,
  'held.put': 3, 'held.del': 2, 'held.get': 2,
  'store.home': 3, 'store.products': 7, 'store.search': 5, 'store.product': 6, 'store.checkout': 2, 'store.track': 2, 'xt.probe': 1,
  'desk.list': 3, 'desk.summary': 1, 'owner.sub': 4,
  'admin.stats': 0.4, 'admin.overview': 0.4, 'admin.desk': 0.4, 'admin.sync': 0.4, 'admin.finance': 0.4,
  'auth.refresh': 2, 'auth.login': 0.5,
});
//  پس از «قطعِ سراسری»: همهٔ دستگاه‌ها با صفِ پر برمی‌گردند
export const SCALE_BURST_MIX = Object.freeze({ ...SCALE_MIX, 'sync3.push': 30, 'sync3.pull': 30 });

/** نشانِ دکان در داده‌ای که بارساز می‌سازد: `~S<n>~` (نام)، `hx<n>-` (نگه‌داشته)، `l?-ld-<n>-`/`l?-lh-<n>-` (op و ردیف) */
const TAG_RE = /~S(\d+)~|\bhx(\d+)-|\bl[a-z]-l[dh]-(\d+)-/g;
export function foreignTag(text, own) {
  TAG_RE.lastIndex = 0;
  for (let m; (m = TAG_RE.exec(text));) {
    const n = Number(m[1] ?? m[2] ?? m[3]);
    if (n !== own) return n;
  }
  return -1;
}
const ACCEPTED = new Set(['applied', 'duplicate', 'conflict', 'partial']);
const MAX_PUSH_OPS = 500;

export function scaleKit({ hit, rnd, pick, int, hlc, state, withDevice, retry }) {
  const units = [];
  let seq = 0;
  const foreign = { count: 0, samples: [] };
  const xt = { probes: 0, violations: 0, samples: [] };
  const held = new Map();      //  id ⇒ {shop, device, version, live, unknown}
  const H = (d) => { d.wall = Math.max(Date.now(), d.wall + 1); return hlc.format({ wall: d.wall, counter: 0, node: d.id }); };
  const tag = (sh) => `~S${sh.index}~`;
  const unit = (d, kind, ops, data) => {
    const u = { shop: d.shop.index, device: d.id, kind, ops, data, st: null, tries: 0 };
    units.push(u);
    return u;
  };
  const note = (label, own, text) => {
    const n = foreignTag(text, own);
    if (n < 0) return;
    foreign.count++;
    if (foreign.samples.length < 10) foreign.samples.push({ label, own, foreign: n, text: text.slice(0, 200) });
  };

  // ── واحدها (هر واحد = یک tx ِ برنامه) ──
  function sale(d) {
    const n = ++seq, sid = `ls-${d.id}-${n}`, tx = `ltx-${d.id}-${n}`;
    const ops = [];
    let total = 0;
    const moves = [];
    const lines = int(1, 4);
    for (let j = 0; j < lines; j++) {
      const p = pick(d.shop.products);
      const qm = 1000 * int(1, 3);
      const line = Math.round(p.priceMinor * qm / 1000);
      total += line;
      moves.push([p.id, -qm]);
      ops.push({ op_id: `lo-${d.id}-${n}-i${j}`, tx, hlc: H(d), table: 'saleItems', row_id: `${sid}-i${j}`, kind: 'insert',
        fields: { saleId: sid, productId: p.id, productName: p.name, quantityMilli: qm, unitPriceMinor: p.priceMinor,
          purchasePriceMinor: p.buyMinor, totalPriceMinor: line, returnedQtyMilli: 0 } });
      ops.push({ op_id: `lo-${d.id}-${n}-m${j}`, tx, hlc: H(d), table: 'stockMovements', row_id: `${sid}-m${j}`, kind: 'insert',
        fields: { productId: p.id, qtyMilli: -qm, reason: 'sale', refId: sid } });
    }
    ops.unshift({ op_id: `lo-${d.id}-${n}-s`, tx, hlc: H(d), table: 'sales', row_id: sid, kind: 'insert',
      fields: { totalMinor: total, discountMinor: 0, finalTotalMinor: total, paidAmountMinor: total, remainingMinor: 0,
        paymentMethod: 'cash', status: 'completed' } });
    ops.push({ op_id: `lo-${d.id}-${n}-t`, tx, hlc: H(d), table: 'transactions', row_id: `${sid}-t`, kind: 'insert',
      fields: { type: 'sale', amountMinor: total, saleId: sid } });
    return unit(d, 'sale', ops, { total, moves });
  }
  function edit(d) {
    const n = ++seq, p = pick(d.shop.products), tx = `ltx-${d.id}-${n}`;
    const minor = Math.max(100, Math.round(p.priceMinor * (0.8 + rnd() * 0.4) / 100) * 100);
    const h = H(d);
    return unit(d, 'edit', [
      { op_id: `lo-${d.id}-${n}-e`, tx, hlc: h, table: 'products', row_id: p.id, kind: 'update', fields: { salePriceMinor: minor } },
      { op_id: `lo-${d.id}-${n}-p`, tx, hlc: H(d), table: 'priceHistory', row_id: `lp-${d.id}-${n}`, kind: 'insert',
        fields: { productId: p.id, oldPriceMinor: p.priceMinor, newPriceMinor: minor } },
    ], { pid: p.id, minor, hlc: h });
  }
  function stock(d) {
    const n = ++seq, p = pick(d.shop.products);
    const buy = rnd() < 0.7;
    const qm = (buy ? 1 : -1) * 1000 * int(1, buy ? 24 : 2);
    return unit(d, 'stock', [{ op_id: `lo-${d.id}-${n}-k`, hlc: H(d), table: 'stockMovements', row_id: `lm-${d.id}-${n}`, kind: 'insert',
      fields: { productId: p.id, qtyMilli: qm, reason: buy ? 'purchase' : 'adjust' } }], { moves: [[p.id, qm]] });
  }
  function customer(d) {
    const n = ++seq, mine = d.customers || (d.customers = []);
    const h = H(d);
    if (mine.length && rnd() < 0.4) {
      const row = pick(mine);
      const name = `مشتریِ ${tag(d.shop)} ${row.slice(-6)} ویرایش ${n}`;
      return unit(d, 'customer', [{ op_id: `lo-${d.id}-${n}-c`, hlc: h, table: 'debtors', row_id: row, kind: 'update', fields: { name } }],
        { row, name, hlc: h });
    }
    const row = `lc-${d.id}-${n}`;
    mine.push(row);
    const name = `مشتریِ ${tag(d.shop)} ${n}`;
    const phone = `07${String(d.shop.index).padStart(3, '0')}${String(n % 100000).padStart(5, '0')}`;
    return unit(d, 'customer', [{ op_id: `lo-${d.id}-${n}-c`, hlc: h, table: 'debtors', row_id: row, kind: 'insert',
      fields: { name, phone, balanceMinor: 0 } }], { row, name, phone, hlc: h, insert: true });
  }

  /**
   * ردیف‌های شمارشِ یک جلسهٔ انبارگردانی (هر ردیف یک واحد، مثلِ برنامه که هر کالای شمرده را جدا ثبت می‌کند). جلسهٔ باز
   * کالاهایش را تکرار نمی‌کند؛ پس از همهٔ کالاها یا به تصادف (۱۰٪) بسته می‌شود و جلسهٔ بعدی تازه است.
   */
  function counts(d, k, { status = 'open' } = {}) {
    const out = [];
    for (let j = 0; j < k; j++) {
      let cs = d.countSession;
      if (!cs || cs.done.size >= d.shop.products.length || (status === 'open' && j === 0 && rnd() < 0.1)) {
        cs = d.countSession = { id: `lk-${d.id}-${++seq}`, done: new Set() };
      }
      const left = d.shop.products.filter((p) => !cs.done.has(p.id));
      const p = pick(left);
      cs.done.add(p.id);
      const n = ++seq, counted = 1000 * int(0, 60);
      out.push(unit(d, 'count', [{ op_id: `lo-${d.id}-${n}-k`, hlc: H(d), table: 'stockCounts', row_id: `lk-${d.id}-${n}`, kind: 'insert',
        fields: { productId: p.id, countedQtyMilli: counted, session: cs.id, status } }], { pid: p.id, counted }));
    }
    return out;
  }
  /**
   * شمارش‌های بستهٔ پیشینِ دکان (پیش از بار، ثبت نمی‌شود): [lines] ردیف، دور به دور روی همهٔ کالاها، ۱۰۰ ردیف در هر push
   * (هر ردیف تعدیلِ سرور هم می‌سازد ⇒ push ِ ۵۰۰تایی روی دکانِ بزرگ از ۱۰ ثانیهٔ بارساز گذشت). شکست ⇒ همان صف دوباره، تا ۸ بار.
   */
  async function countHistory(d, lines, label = 'sync3.countHistory') {
    let sent = 0, fails = 0, r = null;
    const flush = async () => {
      while (d.queue && d.queue.length) {
        r = await pushUnits(d, [], label);
        if (r && r.status === 200) { fails = 0; continue; }
        if (++fails > 8) return false;
        await new Promise((ok) => setTimeout(ok, 1000 * fails));
      }
      return true;
    };
    while (sent < lines) {
      const k = Math.min(100, lines - sent);
      d.queue = [...(d.queue || []), ...counts(d, k, { status: 'closed' })];
      sent += k;
      if (!(await flush())) return { ...(r || {}), sent };
    }
    return { status: 200, sent };
  }

  /** همان push ِ برنامه: اول صفِ کهنهٔ همین دستگاه، سپس تازه‌ها؛ فقط واحدِ کامل، تا ۵۰۰ op */
  async function pushUnits(d, fresh, label) {
    const q = d.queue || (d.queue = []);
    q.push(...fresh);
    const send = [];
    let n = 0;
    while (q.length && n + q[0].ops.length <= MAX_PUSH_OPS) { const u = q.shift(); send.push(u); n += u.ops.length; }
    if (!send.length) return null;
    for (const u of send) u.tries++;
    const r = await hit(label, 'POST', '/api/sync/v3/push',
      { token: d.token, body: { device_id: d.id, protocol: 3, ops: send.flatMap((u) => u.ops) }, shop: d.shop.index });
    const res = r.status === 200 && r.json && Array.isArray(r.json.results) ? new Map(r.json.results.map((x) => [x.op_id, x.status])) : null;
    const again = [];
    for (const u of send) {
      if (res) {
        u.st = u.ops.map((o) => res.get(o.op_id) || 'missing');
        if (u.st.includes('missing')) again.push(u);
      } else {
        //  ۵۰۳ ِ `db_busy` پیش از تراکنش است (هیچ نوشته نشد)؛ هر شکستِ دیگر سرانجامِ نامعلوم دارد
        u.st = u.ops.map(() => (r.status === 503 && r.code === 'db_busy' ? 'busy' : 'unknown'));
        again.push(u);
      }
    }
    if (retry && again.length) q.unshift(...again);
    return { ...r, ops: n };
  }
  async function pull(d, label = 'sync3.pull') {
    const r = await hit(label, 'GET', `/api/sync/v3/pull?device_id=${encodeURIComponent(d.id)}&since=${d.cursor}`, { token: d.token, shop: d.shop.index });
    if (r.status !== 200 || !r.json) return r;
    const seen = d.seen || (d.seen = new Set());
    for (const o of r.json.ops || []) {
      if (typeof o.op_id !== 'string' || !o.op_id.startsWith('lo-')) continue;
      if (seen.has(o.op_id)) d.dup = (d.dup || 0) + 1;
      else seen.add(o.op_id);
    }
    if (r.json.cursor !== undefined) d.cursor = r.json.cursor;
    d.hasMore = !!r.json.has_more;
    return r;
  }
  const large = () => rnd() < 0.05;
  const salesOf = (d, k) => Array.from({ length: k }, () => sale(d));

  // ── فاکتورِ نگه‌داشته ──
  const heldLines = (d) => Array.from({ length: int(1, 4) }, () => { const p = pick(d.shop.products); return { productId: p.id, quantity: int(1, 3), unitPrice: p.priceMinor / 100 }; });
  async function heldPut(d) {
    const mine = [...held.entries()].filter(([, h]) => h.device === d.id && h.live && !h.unknown);
    const id = mine.length && rnd() < 0.3 ? pick(mine)[0] : `hx${d.shop.index}-${d.id.split('-').pop()}-${++seq}`;
    const cur = held.get(id);
    const version = Math.max(Date.now(), (cur ? cur.version : 0) + 1);
    const body = { id, label: `نگه‌داشتهٔ ${tag(d.shop)}`, heldAt: Date.now(), lines: heldLines(d) };
    const r = await hit('held.put', 'PUT', `/api/shop/held-invoices/${id}`, { token: d.token, body: { version, body, device_id: d.id }, shop: d.shop.index });
    const h = cur || { shop: d.shop.index, device: d.id, version: 0, live: false };
    if (r.status === 200 && r.json && r.json.item) Object.assign(h, { version, live: true, unknown: false });
    else h.unknown = true;
    held.set(id, h);
    return r;
  }
  async function heldDel(d) {
    const mine = [...held.entries()].filter(([, h]) => h.device === d.id && h.live && !h.unknown);
    if (!mine.length) return heldPut(d);
    const [id, h] = pick(mine);
    const version = Math.max(Date.now(), h.version + 1);
    const reason = rnd() < 0.6 ? 'sold' : 'deleted';
    const r = await hit('held.del', 'DELETE', `/api/shop/held-invoices/${id}?version=${version}&reason=${reason}&device_id=${encodeURIComponent(d.id)}`,
      { token: d.token, shop: d.shop.index });
    if (r.status === 200 && r.json && r.json.deleted) Object.assign(h, { version, live: false });
    else h.unknown = true;
    return r;
  }

  const SCENARIOS = {
    'sync3.push': () => withDevice((d) => pushUnits(d, salesOf(d, large() ? int(10, 30) : int(1, 2)), 'sync3.push')),
    'sync3.edit': () => withDevice((d) => pushUnits(d, [edit(d)], 'sync3.edit')),
    'sync3.stock': () => withDevice((d) => pushUnits(d, [stock(d)], 'sync3.stock')),
    'sync3.customer': () => withDevice((d) => pushUnits(d, [customer(d)], 'sync3.customer')),
    'sync3.count': () => withDevice((d) => pushUnits(d, counts(d, int(5, 40)), 'sync3.count')),
    'sync3.pull': () => withDevice((d) => pull(d)),
    'held.put': () => withDevice((d) => heldPut(d)),
    'held.del': () => withDevice((d) => heldDel(d)),
    'held.get': () => withDevice((d) => hit('held.get', 'GET', '/api/shop/held-invoices?since=0', { token: d.token, shop: d.shop.index })),
    'owner.sub': () => { const sh = pick(state.shops); return hit('owner.sub', 'GET', '/api/me/subscription', { token: sh.token }); },
    'admin.stats': () => hit('admin.stats', 'GET', '/api/admin/stats', { token: state.admin }),
    'admin.overview': () => hit('admin.overview', 'GET', '/api/admin/overview', { token: state.admin }),
    'admin.desk': () => hit('admin.desk', 'GET', '/api/admin/shop-desk/overview', { token: state.admin }),
    'admin.sync': () => hit('admin.sync', 'GET', '/api/admin/sync/status?app=shop&limit=100', { token: state.admin }),
    'admin.finance': () => hit('admin.finance', 'GET', '/api/admin/finance/summary', { token: state.admin }),
    'xt.probe': async () => {
      const own = pick(state.storeShops);
      const other = state.storeShops.length > 1 ? pick(state.storeShops.filter((s) => s !== own)) : own;
      const mineToo = other === own || rnd() < 0.3;
      const target = mineToo ? own : other;
      const r = await hit('xt.probe', 'GET', `/api/store/${own.slug}/products/mk-${target.index}`, {
        shop: own.index, ok: (s) => (target === own ? s === 200 : s === 404),
      });
      xt.probes++;
      const leak = target !== own && r.status === 200;
      if (leak || (target === own && r.status === 200 && foreignTag(r.text || '', own.index) >= 0)) {
        xt.violations++;
        if (xt.samples.length < 10) xt.samples.push({ own: own.index, asked: target.index, status: r.status });
      }
      return r;
    },
  };

  /**
   * «قطعِ سراسری»: هر دستگاه یک صفِ ۳ تا ۱۵ فروش و چند ویرایش/حرکت/مشتری دارد؛ همه را می‌فرستد و تا سرِ دفتر pull می‌کند.
   * شکست (۵۰۳، مهلت، اتصالِ بریده) ⇒ همان درنگِ برنامه‌ها پیش از دوباره (`sync-core.js backoffMs` و `SyncV3Engine.backoffMs`:
   * ۲ ثانیه × ۲ⁿ، ±۲۵٪، بی نگاه به `Retry-After`) تا [limitMs]؛ موفق ⇒ بی درنگ دستهٔ بعد. «تخلیه» = از آغاز تا صفِ خالی و pull تا سر.
   */
  const burst = { devices: 0, busy: 0, drainedMs: [], left: 0, retries: 0, t0: 0, lastMs: 0 };
  const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
  const backoffMs = (attempt) => Math.round(Math.min(300_000, 2000 * 2 ** attempt) * (0.75 + rnd() * 0.5));
  async function outage(d, { limitMs = 120_000 } = {}) {
    if (d.busy) { burst.busy++; return; }
    d.busy = true;
    const t0 = performance.now();
    if (!burst.t0) burst.t0 = t0;
    burst.devices++;
    let fails = 0;
    const failed = async () => { if (performance.now() - t0 > limitMs) return false; burst.retries++; await sleep(backoffMs(fails++)); return true; };
    try {
      let r = await pushUnits(d, [...salesOf(d, int(3, 15)), edit(d), stock(d), customer(d)], 'sync3.pushOutage');
      while (d.queue.length && performance.now() - t0 < limitMs) {
        if (r && r.status === 200) fails = 0;
        else if (!(await failed())) break;
        r = await pushUnits(d, [], 'sync3.pushOutage');
      }
      let synced = false;
      while (!d.queue.length && performance.now() - t0 < limitMs) {
        const p = await pull(d, 'sync3.pullOutage');
        if (p.status === 200) { fails = 0; if (!d.hasMore) { synced = true; break; } } else if (!(await failed())) break;
      }
      if (synced) { const ms = performance.now() - t0; burst.drainedMs.push(ms); burst.lastMs = Math.max(burst.lastMs, t0 + ms - burst.t0); } else burst.left++;
    } finally { d.busy = false; }
  }
  /** خلاصهٔ «قطعِ سراسری»: چند دستگاه در چند ثانیه تا سر رسیدند (و بقیه تا [limitMs] نه) */
  function burstStats() {
    const s = [...burst.drainedMs].sort((a, b) => a - b);
    const q = (p) => (s.length ? Math.round(s[Math.min(s.length - 1, Math.ceil(p * s.length) - 1)]) : null);
    const within = (sec) => s.filter((x) => x <= sec * 1000).length;
    const out = { devices: burst.devices, skippedBusy: burst.busy, drained: s.length, notDrained: burst.left, retries: burst.retries,
      p50Ms: q(0.5), p95Ms: q(0.95), maxMs: s.length ? Math.round(s[s.length - 1]) : null, lastDrainedAfterMs: Math.round(burst.lastMs),
      within10s: within(10), within30s: within(30), within60s: within(60) };
    Object.assign(burst, { devices: 0, busy: 0, drainedMs: [], left: 0, retries: 0, t0: 0, lastMs: 0 });
    return out;
  }

  /**
   * پس از بار (ثبت نمی‌شود): اول صفِ **همهٔ** دستگاه‌ها خالی، سپس هر دستگاه تا سرِ دفتر — مثلِ برنامه‌ای که شبکه‌اش برگشت.
   * ⛔ دو گذر، نه یکی: دستگاهی که پیش از push ِ صفِ دستگاهِ دیگرِ همان دکان تا سر pull کند، آن op ها را نمی‌بیند (اجرای نخستِ
   *    ۲۰۰ × ۵: ۶۱٬۰۴۴ «نرسیده»، همه بالای cursor ِ پایانیِ دستگاه).
   */
  async function drain({ par = 8, tries = 30 } = {}) {
    const devs = [...state.devices];
    const stats = { pushes: 0, pulls: 0, left: 0 };
    const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
    const each = async (fn) => { for (let i = 0; i < devs.length; i += par) await Promise.all(devs.slice(i, i + par).map(fn)); };
    await each(async (d) => {
      for (let i = 0; i < tries && d.queue && d.queue.length; i++) {
        const r = await pushUnits(d, [], 'drain.push');
        stats.pushes++;
        if (!r || r.status !== 200) await sleep(250 * (i + 1));
      }
      stats.left += d.queue ? d.queue.length : 0;
    });
    await each(async (d) => {
      for (let i = 0; i < 500; i++) {
        const r = await pull(d, 'drain.pull');
        stats.pulls++;
        if (r.status === 200 && !d.hasMore) break;
        if (r.status !== 200) await sleep(250);
      }
    });
    return stats;
  }

  function ledger() {
    const ops = [];
    const sales = {}, moves = {}, prices = {}, debtors = {}, counts = {};
    const bump = (o, k, v) => { o[k] = (o[k] || 0) + v; };
    const win = (cur, h) => !cur || hlc.compare(h, cur.hlc) > 0;
    for (const u of units) {
      const st = u.st || u.ops.map(() => 'unsent');
      u.ops.forEach((o, i) => ops.push([o.op_id, u.shop, st[i]]));
      if (!st.every((s) => ACCEPTED.has(s))) continue;
      const s = String(u.shop);
      if (u.kind === 'sale') { const x = sales[s] ||= { n: 0, totalMinor: 0 }; x.n++; x.totalMinor += u.data.total; }
      if (u.data.moves) for (const [pid, q] of u.data.moves) bump(moves[s] ||= {}, pid, q);
      if (u.kind === 'count') bump(counts, s, 1);
      if (u.kind === 'edit') { const m = prices[s] ||= {}; if (win(m[u.data.pid], u.data.hlc)) m[u.data.pid] = { minor: u.data.minor, hlc: u.data.hlc }; }
      if (u.kind === 'customer') {
        const m = debtors[s] ||= {};
        if (u.data.insert) m[u.data.row] = { ...(m[u.data.row] || {}), phone: u.data.phone, inserted: true };
        if (win(m[u.data.row] && m[u.data.row].nameHlc ? { hlc: m[u.data.row].nameHlc } : null, u.data.hlc)) {
          m[u.data.row] = { ...(m[u.data.row] || {}), name: u.data.name, nameHlc: u.data.hlc };
        }
      }
    }
    const seedPrices = {};
    for (const sh of state.shops) {
      const m = seedPrices[sh.index] = {};
      for (const p of sh.products) if (!(prices[sh.index] && prices[sh.index][p.id])) m[p.id] = p.priceMinor;
    }
    const heldOut = {};
    for (const [id, h] of held) {
      const x = heldOut[h.shop] ||= { live: [], gone: [], unknown: [] };
      (h.unknown ? x.unknown : h.live ? x.live : x.gone).push(id);
    }
    return {
      version: 1,
      shops: state.shops.map((sh) => ({ index: sh.index, shopId: sh.shopId, slug: sh.slug })),
      devices: state.devices.map((d) => ({ id: d.id, shop: d.shop.index, cursor: d.cursor, queued: d.queue ? d.queue.length : 0,
        dup: d.dup || 0, seen: [...(d.seen || [])] })),
      ops, sales, moves, prices, seedPrices, debtors, counts, held: heldOut,
      orders: Object.fromEntries([...state.orders.entries()].map(([slug, toks]) => [slug, toks.slice()])),
      foreign, xt,
    };
  }

  return { SCENARIOS, pushUnits, pull, outage, burstStats, drain, ledger, note, sales: salesOf, countHistory };
}
