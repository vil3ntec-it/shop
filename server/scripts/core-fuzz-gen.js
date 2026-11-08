'use strict';
/**
 * مولّد و دوندهٔ JS ِ خانواده‌های پول، سقفِ برگشت، موجودی و گزارش + کمک‌های دانه‌دارِ مشترک — بخشی از
 * فازِ تفاضلیِ `server/scripts/core-fuzz.js` (شرحِ کامل همان‌جا). فقط از همان پرونده بار می‌شود.
 */
const path = require('path');

const ROOT = path.join(__dirname, '..', '..');

const M = require(path.join(ROOT, 'license', 'money.js'));
const STOCK = require(path.join(ROOT, 'license', 'stock-core.js'));
const REPORT = require(path.join(ROOT, 'license', 'report-core.js'));
const PROMO = require(path.join(ROOT, 'license', 'promo-core.js'));
const DEBT = require(path.join(ROOT, 'license', 'debt-book.js'));
const CAL = require(path.join(ROOT, 'license', 'calendar-core.js'));
const TEXT = require(path.join(ROOT, 'license', 'text-core.js'));
const CAP = require(path.join(ROOT, 'server', 'src', 'lib', 'return-line-cap.js'));

const MAX = 9007199254740991;
const S53 = 9007199254740992;

/* ---------------- مولّدِ دانه‌دار ---------------- */

function rng(seed) {
  let s = seed >>> 0;
  const next = () => {
    s = (s + 0x6D2B79F5) >>> 0;
    let t = s;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
  const r = {
    next,
    /** صحیحِ [lo, hi]؛ تا ۲^۵۳ با دو برداشت */
    int(lo, hi) {
      const span = hi - lo + 1;
      if (span <= 4294967296) return lo + Math.floor(next() * span);
      const big = Math.floor(next() * 2097152) * 4294967296 + Math.floor(next() * 4294967296);
      return lo + (big % span);
    },
    chance(p) { return next() < p; },
    pick(list) { return list[Math.floor(next() * list.length)]; },
    /** [[وزن، مقدار یا تابعِ سازنده]…] ⇒ مقدار */
    weighted(list) {
      let total = 0;
      for (const [w] of list) total += w;
      let x = next() * total;
      let v = list[list.length - 1][1];
      for (const [w, c] of list) { if ((x -= w) < 0) { v = c; break; } }
      return typeof v === 'function' ? v() : v;
    },
    sign() { return next() < 0.5 ? -1 : 1; },
  };
  return r;
}

/** عددِ اعشاری از JSON: رشته‌های NaN/Infinity/-0 */
function D(x) {
  if (x === 'NaN') return NaN;
  if (x === 'Infinity') return Infinity;
  if (x === '-Infinity') return -Infinity;
  if (x === '-0') return -0;
  return x;
}
/** عدد ⇒ شکلِ JSON (ضد ِ D) */
function J(x) {
  if (typeof x !== 'number') return x;
  if (Number.isNaN(x)) return 'NaN';
  if (x === Infinity) return 'Infinity';
  if (x === -Infinity) return '-Infinity';
  if (Object.is(x, -0)) return '-0';
  return x;
}

const EDGE_INTS = [0, 1, -1, 2, 99, 100, 101, 499, 500, 501, 999, 1000, 1001, MAX, -MAX, S53, -S53,
  2 ** 52, 2 ** 50 + 1, 2 ** 31, -(2 ** 31), 2 ** 31 - 1, 2 ** 32, 94906265, 94906266, 3037000499];

function anyInt(r) {
  return r.weighted([
    [5, () => r.int(-1000, 1000)],
    [4, () => r.int(-1e7, 1e7)],
    [3, () => r.sign() * r.int(0, 1e12)],
    [2, () => r.sign() * r.int(0, MAX)],
    [2, () => r.pick(EDGE_INTS) * r.sign()],
  ]);
}

/** عددِ اعشاریِ «دفترِ امروز» (افغانی/مقدار) با لبه‌های نیم و گردِ ممیز */
function legacyDouble(r) {
  return r.weighted([
    [4, () => r.int(0, 100000) + r.pick([0, 0.5, 0.25, 0.75, 0.005, 0.015, 0.045, 0.285, 0.675, 0.335, 0.1, 0.01, 0.995])],
    [2, () => r.int(1, 99999) / r.pick([3, 7, 100, 1000, 8])],
    [2, () => r.pick([0.1 + 0.2, 1.005, 2.675, 1.255, 0.285, 1e-9, 0.0049999999999999, 0.49999999999999994, 4.35, 1.15, 8.325])],
    [1, () => r.pick([9.007199254740991e13, 9.0071992547409e13, 9.1e13, 1e14, 1e16, 2 ** 52 / 100 + 0.5, 4503599627370495.5])],
    [1, () => r.pick([NaN, Infinity, -Infinity, -0, 0])],
  ]) * (r.chance(0.15) ? -1 : 1);
}

/** همان، درونِ بازهٔ امنِ هسته (|x × scale| ≤ ۲^۵۳−۱)؛ NaN/Infinity می‌مانند (هسته باید ردشان کند) */
function boundedDouble(r, scale) {
  for (;;) {
    const x = legacyDouble(r);
    if (!Number.isFinite(x) || Math.abs(x * scale) <= MAX) return x;
  }
}

/* ---------------- تاریخ‌های لبه ---------------- */

/** روزهای مرزیِ خورشیدی و میلادی (پایانِ ماه، پایانِ سال، کبیسه) */
function edgeDates() {
  const out = new Set();
  for (let jy = 1398; jy <= 1410; jy++) {
    for (const [m, d] of [[1, 1], [6, 31], [7, 1], [9, 30], [10, 1], [12, 1]]) out.add(CAL.fromSolar(jy, m, d));
    out.add(CAL.fromSolar(jy, 12, CAL.solarMonthLength(jy, 12)));
  }
  for (const y of [2020, 2023, 2024, 2025, 2026, 2027, 2028, 2100]) {
    out.add(`${y}-02-28`); if (CAL.jdnOfIso(`${y}-02-29`) !== null) out.add(`${y}-02-29`);
    out.add(`${y}-03-01`); out.add(`${y}-12-31`); out.add(`${y + 1}-01-01`);
  }
  return [...out].filter(Boolean).sort();
}
const EDGE_DATES = edgeDates();

function nearDate(r, base, spread) { return CAL.addDays(base, r.int(-spread, spread)); }
function baseDate(r) { return r.chance(0.7) ? r.pick(EDGE_DATES) : CAL.addDays('2026-01-01', r.int(-800, 800)); }

const BAD_DATES = ['2026-02-29', '2025-02-30', '2026-13-01', '2026-00-10', '0000-01-01', '2026-1-01', '۲۰۲۶-۰۱-۰۱',
  '2026-01-01T00:00', '', ' 2026-01-01', '2026-04-31', '9999-12-32'];

/** میلی‌ثانیهٔ UTC ِ لحظه‌ای از روزِ محلیِ کابل (+۴:۳۰) */
function kabulMs(date, minute) { return (CAL.jdnOfIso(date) - 2440588) * 86400000 + minute * 60000 - 270 * 60000; }

/* ---------------- رشته‌های لبه ---------------- */

/** نویسه‌هایی که JS و Kotlin شاید یکسان «فاصله» نشمارند */
const WS = [' ', '\t', '\u00A0', '\uFEFF', '\u001F', '\u2007', '\u202F', '\u3000', '\u200B', '\u200C', '\u0085', '\u2028'];
function messy(r, s) {
  if (!r.chance(0.12)) return s;
  return r.pick(WS) + s + (r.chance(0.5) ? r.pick(WS) : '');
}

/* ======================= خانواده‌ها ======================= */

/* ---------- پول: license/money.js ⇄ core/money/{Rounding,Money}.kt ---------- */

const money = {
  'money.lineTotalMinor': (r) => ({
    q: r.weighted([[5, () => r.pick([1000, 2000, 500, 1500, 250, 333, 1, 999, 1001, 125, 2500, 750])], [4, () => r.int(1, 100000)],
      [1, () => -r.int(1, 20000)], [2, () => anyInt(r)]]),
    p: r.weighted([[5, () => r.int(1, 1000) * 100], [4, () => r.int(1, 10000000)], [1, () => -r.int(1, 100000)], [2, () => anyInt(r)]]),
    step: r.weighted([[8, 100], [3, 1], [2, 500], [1, 1000], [1, 50], [1, 0], [1, -100], [1, MAX], [1, S53]]),
  }),
  'money.roundHalfUp': (r) => ({ n: anyInt(r), d: r.weighted([[5, () => r.int(1, 2000)], [2, () => r.pick([2, 100, 1000, 100000])], [1, () => r.pick([0, -1, -2])], [2, () => anyInt(r)]]) }),
  'money.allocateDiscount': (r) => {
    const step = r.weighted([[12, 100], [4, 1], [4, 500], [1, 0]]);
    const n = r.int(0, 6);
    const lines = [];
    for (let i = 0; i < n; i++) lines.push(r.weighted([[30, () => r.int(1, 2000) * 100], [15, () => r.int(0, 999999)], [4, () => 0], [1, () => -r.int(1, 100)], [1, () => r.int(0, MAX)]]));
    const sum = lines.reduce((s, x) => s + Math.max(0, x), 0);
    const units = step > 0 ? Math.floor(sum / step) : 0;
    const discount = r.weighted([
      [6, () => r.int(0, Math.max(0, Math.min(units, 1e9))) * Math.max(step, 1)],
      [1, () => units * Math.max(step, 1)],
      [1, () => r.int(1, 99)],
      [1, () => -r.int(1, 10) * 100],
      [1, () => (units + 1) * Math.max(step, 1)],
    ]);
    return { step, discount, lines };
  },
  'money.cashRounding': (r) => ({ total: anyInt(r), step: r.weighted([[4, 500], [3, 1000], [2, 100], [1, 1], [1, 0], [1, -500]]) }),
  'money.fromLegacy': (r) => ({ major: J(legacyDouble(r)) }),
  'money.qtyFromLegacy': (r) => ({ q: J(legacyDouble(r)) }),
  'money.returnShare': (r) => {
    const quantity = r.weighted([[5, () => r.int(1, 20)], [3, () => r.pick([0.5, 1.5, 2.25, 0.333, 2.5, 0.125, 7.75])], [1, () => r.pick([0, -1, NaN, Infinity])]]);
    const unit = r.weighted([[5, () => r.int(1, 2000)], [3, () => r.int(1, 99999) / 100], [1, () => r.int(1, 9) / 3]]);
    const lineTotal = r.chance(0.9) ? quantity * unit : r.pick([NaN, Infinity, -5, 0, 1e15]);
    const total = lineTotal;
    const pr = r.weighted([[5, 1], [3, () => (total > 0 ? Math.min(1, Math.max(0, (total - r.int(0, Math.ceil(Math.abs(total) || 1))) / total)) : 1)],
      [1, () => r.pick([0, 0.5, 1 / 3, 2 / 3, -0.1, 1.2, NaN])]]);
    const q0 = Number.isFinite(quantity) && quantity > 0 ? quantity : 3;
    const b = r.weighted([[4, 0], [4, () => Math.round(r.next() * q0 * 4) / 4], [1, () => r.pick([-1, NaN, q0, q0 + 1])]]);
    const qty = r.weighted([[6, () => Math.max(0.25, Math.round(r.next() * Math.max(0.25, q0 - (Number.isFinite(b) ? Math.max(0, b) : 0)) * 4) / 4)],
      [1, () => q0], [1, () => r.pick([0, -1, NaN, 0.001, 1e-9])]]);
    const o = { lineTotal: J(lineTotal), quantity: J(quantity), returnedBefore: J(b), qty: J(qty), paidRatio: J(pr) };
    if (r.chance(0.5)) {
      o.refundedBefore = J(r.weighted([[3, () => Math.round(r.next() * Math.abs(Number.isFinite(lineTotal) ? lineTotal : 10))], [1, () => r.pick([0, -2, NaN])]]));
      o.refundedQty = J(r.weighted([[3, () => (Number.isFinite(b) ? Math.max(0, b - r.pick([0, 0, 0.5, 1])) : 0)], [1, () => r.pick([0, NaN, -1])]]));
    }
    return o;
  },
};

function runMoney(fn, x) {
  switch (fn) {
    case 'money.lineTotalMinor': return M.lineTotalMinor(x.q, x.p, x.step);
    case 'money.roundHalfUp': return M.roundHalfUp(x.n, x.d);
    case 'money.allocateDiscount': {
      const cur = M.currency('AFN', x.step);
      return M.allocateDiscount(M.money(x.discount, cur), x.lines.map((m) => M.money(m, cur))).map((m) => m.minor);
    }
    case 'money.cashRounding': {
      const line = M.cashRounding(M.money(x.total, 'AFN'), x.step);
      return line === null ? null : line.amount.minor;
    }
    case 'money.fromLegacy': return M.moneyFromLegacy(D(x.major), 'AFN').minor;
    case 'money.qtyFromLegacy': return M.qtyFromLegacy(D(x.q)).milli;
    case 'money.returnShare': return x.refundedBefore === undefined
      ? M.returnShare(D(x.lineTotal), D(x.quantity), D(x.returnedBefore), D(x.qty), D(x.paidRatio))
      : M.returnShare(D(x.lineTotal), D(x.quantity), D(x.returnedBefore), D(x.qty), D(x.paidRatio), D(x.refundedBefore), D(x.refundedQty));
    default: throw new Error('fn: ' + fn);
  }
}

/* ---------- سقفِ برگشت: server/src/lib/return-line-cap.js ⇄ core/money/ReturnCap.kt ---------- */

const cap = {
  'cap.milli': (r) => ({ x: J(boundedDouble(r, 1000)) }),
  'cap.minor': (r) => ({ x: J(boundedDouble(r, 100)) }),
  'cap.capMinor': (r) => ({ lineTotal: J(boundedDouble(r, 100)), paidRatio: J(r.weighted([[4, 1], [3, () => r.int(0, 100) / 100], [1, () => r.pick([1 / 3, -0.5, 1.5, NaN])]])) }),
  'cap.check': (r) => {
    const sold = r.weighted([[6, () => r.int(1, 20) * 1000], [2, () => r.int(1, 99999)], [1, () => 0]]);
    const unit = r.int(1, 5000);
    const capMinor = Math.round((sold / 1000) * unit) * 100;
    const prior = [];
    let left = sold;
    for (let i = r.int(0, 4); i > 0 && left > 0; i--) {
      const q = r.int(1, left);
      prior.push([q, Math.round((capMinor * q) / Math.max(1, sold)) + r.pick([0, 0, 0, 1, -1, 50, 51])]);
      left -= q;
    }
    const reqQ = r.weighted([[5, () => r.int(0, Math.max(0, left))], [2, () => left], [1, () => left + 1], [1, () => r.int(1, sold + 2000)]]);
    const reqA = r.weighted([[5, () => Math.round((capMinor * reqQ) / Math.max(1, sold))], [2, () => Math.round((capMinor * reqQ) / Math.max(1, sold)) + r.int(-100, 300)], [1, () => capMinor + 1]]);
    return {
      sold, cap: capMinor, prior, req: [reqQ, reqA],
      tol: r.pick([0, 0, 50, CAP.TOL_PER_ROW, -10]), counter: r.weighted([[5, 0], [2, () => r.int(0, sold)], [1, () => sold + 1000]]),
      extra: r.pick([0, 0, 0, 100, -100, 5000]),
    };
  },
};

function runCap(fn, x) {
  switch (fn) {
    case 'cap.milli': return CAP.milli(D(x.x));
    case 'cap.minor': return CAP.minor(D(x.x));
    case 'cap.capMinor': return CAP.capMinor(D(x.lineTotal), D(x.paidRatio));
    case 'cap.check': {
      const prior = x.prior.map(([q, a]) => ({ qtyMilli: q, amountMinor: a }));
      const req = { qtyMilli: x.req[0], amountMinor: x.req[1] };
      const s = CAP.state(x.sold, x.cap, prior, x.counter);
      const v = CAP.check(x.sold, x.cap, prior, req, { tolMinorPerRow: x.tol, counterMilli: x.counter, extraMinor: x.extra });
      return { returnedMilli: s.returnedMilli, returnedMinor: s.returnedMinor, leftMilli: s.leftMilli, leftMinor: s.leftMinor, verdict: v };
    }
    default: throw new Error('fn: ' + fn);
  }
}

/* ---------- موجودی: license/stock-core.js ⇄ android-native/.../core/stock/StockCore.kt ---------- */

const STOCK_TYPES = Object.keys(STOCK.TYPES);

function stockDoc(r, i, at, today) {
  const type = r.weighted([[8, 'purchase_receipt'], [8, 'sale'], [3, 'customer_return'], [2, 'supplier_return'], [2, 'adjustment'],
    [3, 'count_adjust'], [2, 'damage'], [2, 'expired'], [2, 'transfer_out'], [2, 'transfer_in'], [2, 'reserve'], [2, 'release'],
    [1, 'production'], [2, 'cost_adjust'], [1, 'gift']]);
  const spec = STOCK.TYPES[type] || {};
  const d = { id: 'd' + (i + 1), type, productId: r.pick(['P1', 'P1', 'P2', 'P3']), warehouseId: r.pick(['W1', 'W1', 'W2']), userId: r.pick(['u1', 'u2']), at };
  const qty = () => r.weighted([[5, () => r.int(1, 20) * 1000], [3, () => r.int(1, 25000)], [1, () => r.pick([1, 999, 1001, 333])]]);
  if (spec.value) {
    d.valueMinor = r.sign() * r.weighted([[4, () => r.int(1, 100000)], [1, () => r.int(1, 99)]]);
  } else if (spec.dir === 0) {
    d.qtyMilli = r.sign() * qty();
  } else {
    d.qtyMilli = qty();
  }
  if (spec.cost === 'required' || (spec.cost === 'optional' && r.chance(0.5))) {
    const q = Math.abs(d.qtyMilli || 1000);
    d.costMinor = r.weighted([[5, () => Math.round((q / 1000) * r.int(1, 5000)) * 100], [3, () => r.int(0, 999999)], [1, () => 0]]);
  }
  if (type === 'purchase_receipt' && r.chance(0.25)) d.landedMinor = r.int(0, 50000);
  if (spec.reason) d.reason = r.pick(['شمارش', 'خراب', 'x']);
  if ((spec.dir === 1 || spec.dir === 0) && r.chance(0.35)) {
    const lot = { lotId: r.pick(['L1', 'L2', 'L3']) };
    if (r.chance(0.6)) lot.expiryDate = nearDate(r, today || '2026-06-01', 40);
    if (r.chance(0.3)) lot.productionDate = nearDate(r, lot.expiryDate || today || '2026-06-01', 60);
    d.lot = lot;
  }
  if (spec.dir === -1 && r.chance(0.25)) d.lotId = r.pick(['L1', 'L2', 'L3', '']);
  if (r.chance(0.15)) d.refId = 'ref' + r.int(1, 9);
  /* نادرست‌ها: هر دو سو باید یک کد بدهند */
  if (r.chance(0.12)) {
    r.weighted([
      [2, () => { d.userId = r.pick(['', ' ', '\t']); }],
      [1, () => { d.userId = r.pick(WS); }],
      [1, () => { d.productId = r.pick(['', ' ']); }],
      [1, () => { d.at = r.pick([0, -5]); }],
      [2, () => { if (d.qtyMilli !== undefined) d.qtyMilli = r.pick([0, -1000]); else d.qtyMilli = 1000; }],
      [1, () => { delete d.costMinor; }],
      [1, () => { d.costMinor = -r.int(1, 100); }],
      [1, () => { delete d.reason; d.reason = r.pick(['', ' ', undefined]); if (d.reason === undefined) delete d.reason; }],
      [1, () => { d.lot = { lotId: 'L9', expiryDate: r.pick(BAD_DATES) }; }],
      [1, () => { d.lot = { lotId: 'L9', productionDate: '2026-05-02', expiryDate: '2026-05-01' }; }],
      [1, () => { d.lot = { lotId: r.pick(['', ' ']) }; }],
      [1, () => { delete d.valueMinor; }],
      [1, () => { d.landedMinor = -1; }],
    ]);
  }
  return d;
}

const stock = {
  'stock.mulDivRound': (r) => ({ a: anyInt(r), b: anyInt(r), c: r.weighted([[5, () => r.int(1, 100000)], [2, () => r.int(1, MAX)], [1, () => r.pick([0, -1, 1, 2])], [1, () => anyInt(r)]]) }),
  'stock.ledger': (r) => {
    const today = r.chance(0.7) ? baseDate(r) : null;
    const n = r.int(2, 8);
    const steps = [];
    let at = 1700000000000 + r.int(0, 1e6) * 1000;
    for (let i = 0; i < n; i++) {
      at += r.int(1, 86400) * 1000;
      steps.push({ mode: r.chance(0.35) ? 'recorded' : 'local', doc: stockDoc(r, i, at, today) });
    }
    return { today, steps };
  },
  'stock.fefo': (r) => {
    const today = r.chance(0.8) ? baseDate(r) : null;
    const lots = [];
    for (let i = r.int(0, 5); i > 0; i--) {
      const l = { lotId: r.pick(['', 'L1', 'L2', 'L3', 'L4']), qtyMilli: r.weighted([[5, () => r.int(1, 10) * 1000], [1, () => -r.int(1, 3000)], [1, () => 0]]), seq: r.int(0, 9) };
      l.expiryDate = r.chance(0.7) ? nearDate(r, today || '2026-06-01', 20) : null;
      l.productionDate = r.chance(0.3) ? nearDate(r, today || '2026-06-01', 90) : null;
      lots.push(l);
    }
    return { today, lots, qtyMilli: r.int(0, 40000), includeExpired: r.chance(0.3), preferLotId: r.chance(0.3) ? r.pick(['L1', 'L2', 'L9', '']) : null };
  },
  'stock.view': (r) => {
    const docs = [];
    let at = 1700000000000;
    for (let i = r.int(1, 8); i > 0; i--) { at += r.int(1, 1000) * 1000; docs.push(stockDoc(r, docs.length, at, '2026-06-01')); }
    return { docs, product: r.pick(['P1', 'P2']), warehouse: r.pick(['W1', 'W2']) };
  },
};

function stockResult(x) {
  const t = x.reviewTask;
  return {
    ok: x.ok, error: x.error === undefined ? null : x.error,
    costMinor: x.costMinor === undefined ? 0 : x.costMinor, costAdjustMinor: x.costAdjustMinor === undefined ? 0 : x.costAdjustMinor,
    picks: (x.picks || []).map((p) => ({ lotId: p.lotId, qtyMilli: p.qtyMilli })), shortMilli: x.shortMilli === undefined ? 0 : x.shortMilli,
    availableMilli: x.availableMilli === undefined ? null : x.availableMilli, expiredMilli: x.expiredMilli === undefined ? null : x.expiredMilli,
    reviewTask: t ? { kind: t.kind, productId: t.productId, warehouseId: t.warehouseId, docId: t.docId, reasons: t.reasons, shortMilli: t.shortMilli, qtyAfterMilli: t.qtyAfterMilli } : null,
  };
}

function runStock(fn, x) {
  switch (fn) {
    case 'stock.mulDivRound': return STOCK.mulDivRound(x.a, x.b, x.c);
    case 'stock.ledger': {
      let book = STOCK.emptyBook();
      const results = [];
      for (const s of x.steps) {
        const a = STOCK.apply(book, s.doc, { mode: s.mode, today: x.today });
        results.push(stockResult(a.result));
        book = a.book;
      }
      const positions = STOCK.positions(book).map((p) => ({
        productId: p.productId, warehouseId: p.warehouseId, qtyMilli: p.qtyMilli, valueMinor: p.valueMinor,
        reservedMilli: p.reservedMilli, sellableMilli: p.sellableMilli, lastCost: p.lastCost,
        lots: p.lots.map((l) => ({ lotId: l.lotId, productionDate: l.productionDate, expiryDate: l.expiryDate, qtyMilli: l.qtyMilli })),
        totals: p.totals, reconciles: STOCK.reconcile(p).ok,
      }));
      return { results, positions, totals: STOCK.bookTotals(book) };
    }
    case 'stock.fefo': {
      const got = STOCK.pickFefo(x.lots, x.qtyMilli, { today: x.today, includeExpired: x.includeExpired, preferLotId: x.preferLotId });
      return { picks: got.picks.map((p) => ({ lotId: p.lotId, qtyMilli: p.qtyMilli })), shortMilli: got.shortMilli };
    }
    case 'stock.view': return {
      signed: x.docs.map(STOCK.signedQtyMilli),
      onHand: STOCK.onHandMilli(x.docs, x.product, x.warehouse),
      exportRows: STOCK.exportRows(x.docs),
    };
    default: throw new Error('fn: ' + fn);
  }
}

/* ---------- گزارش: license/report-core.js ⇄ android-native/.../core/report/ReportCore.kt ---------- */

const NAMES = ['احمد', 'کریم', 'زهرا', 'Ali', ''];
const METHODS = ['cash', 'cash', 'cash', 'credit', 'card', 'mixed', 'hesabpay', 'transfer', ''];

function afn(r) {
  return r.weighted([[6, () => r.int(1, 2000) * 5], [3, () => r.int(1, 99999) / 100], [1, () => r.int(1, 999) / 4]]);
}
/** عددِ دفتر: گاهی رشته (دادهٔ وبِ کهنه)؛ رفتارِ تبدیل باید یکی باشد */
function ledgerNum(r, v) {
  if (!r.chance(0.04)) return v;
  return r.pick([String(v), ' ' + v + ' ', String(v) + 'd', '0x1F', '1e2', '', 'abc', '\u00A0' + v, '+' + v, '.5', '5.', '1_000', 'Infinity']);
}

function reportLedger(r) {
  const base = baseDate(r);
  const day = () => nearDate(r, base, 4);
  const at = (date) => (r.chance(0.85) ? kabulMs(date, r.int(0, 1439)) : 0);
  const L = { products: [], warehouseEntries: [], sales: [], saleItems: [], returns: [], debtors: [], transactions: [], suppliers: [], purchases: [], supplierPayments: [], auditLog: [] };
  const np = r.int(1, 3);
  for (let i = 1; i <= np; i++) {
    L.products.push({ id: 'p' + i, name: 'کالا ' + i, category: messy(r, r.pick(['نوشیدنی', 'خوراکی', '', ' شوینده '])), purchasePrice: afn(r), salePrice: afn(r) });
  }
  for (let i = 1, n = r.int(0, 3); i <= n; i++) {
    const adj = r.chance(0.2);
    const date = day();
    L.warehouseEntries.push({ id: 'w' + i, productId: 'p' + r.int(1, np), units: adj ? -r.int(1, 5) : r.pick([r.int(1, 50), r.int(1, 99) / 4]), price: adj ? 0 : ledgerNum(r, afn(r)), isAdjustment: adj ? true : undefined, date, createdAt: at(date) });
  }
  let item = 0;
  for (let s = 1, n = r.int(0, 5); s <= n; s++) {
    const date = day();
    const id = 's' + s;
    const lines = [];
    for (let k = r.int(1, 2); k > 0; k--) {
      item++;
      const free = r.chance(0.1);
      const q = r.pick([1, 2, 3, 1.5, 0.25, 0.75, 2.5, r.int(1, 12)]);
      const unit = afn(r);
      const pid = free ? '' : 'p' + r.int(1, np);
      const it = { id: 'i' + item, saleId: id, productId: pid, quantity: q, unitPrice: unit, purchasePrice: free ? 0 : afn(r), totalPrice: ledgerNum(r, Math.round(q * unit * 100) / 100), returnedQty: 0 };
      if (free) it.name = 'قلم آزاد';
      lines.push(it);
      L.saleItems.push(it);
    }
    const total = Math.round(lines.reduce((a, l) => a + (typeof l.totalPrice === 'number' ? l.totalPrice : 0), 0) * 100) / 100;
    const discount = r.chance(0.3) ? Math.min(total, r.pick([5, 10, 0.5, Math.round(total * 10) / 100])) : 0;
    const status = r.weighted([[8, 'completed'], [2, 'cancelled'], [1, 'held']]);
    const sale = {
      id, invoiceNumber: s, total, discount, finalTotal: Math.round((total - discount) * 100) / 100, paymentMethod: r.pick(METHODS),
      paidAmount: total - discount, status, seller: messy(r, r.pick(NAMES)), date, createdAt: at(date),
    };
    if (status === 'cancelled') sale.cancelledBy = messy(r, r.pick(NAMES));
    if (r.chance(0.15)) sale.approvedBy = r.pick(NAMES);
    if (r.chance(0.5)) sale.channel = r.pick(['pos', 'online', '']);
    L.sales.push(sale);
    for (const it of lines) {
      if (status === 'completed' && r.chance(0.25)) {
        const q = r.chance(0.5) ? it.quantity : Math.min(it.quantity, r.pick([1, 0.5, 0.25]));
        it.returnedQty = q;
        const rd = nearDate(r, date, 2);
        L.returns.push({
          id: 'r' + (L.returns.length + 1), saleId: id, saleItemId: it.id, productId: it.productId, quantity: q,
          amount: Math.round((Number(it.totalPrice) || 0) * (q / it.quantity) * 100) / 100, reason: 'خراب',
          [r.chance(0.5) ? 'user' : 'by']: messy(r, r.pick(NAMES)), approvedBy: r.chance(0.2) ? 'مدیر' : undefined, date: rd, createdAt: at(rd),
        });
      }
    }
  }
  for (let i = 1, n = r.int(0, 2); i <= n; i++) L.debtors.push({ id: 'd' + i, name: r.pick(NAMES) || 'بی‌نام' });
  for (let i = 1, n = L.debtors.length ? r.int(0, 4) : 0; i <= n; i++) {
    const date = nearDate(r, base, 120);
    L.transactions.push({ id: 't' + i, debtorId: 'd' + r.int(1, L.debtors.length), type: r.pick(['give', 'give', 'receive']), amount: ledgerNum(r, afn(r)), date, createdAt: at(date) });
  }
  for (let i = 1, n = r.int(0, 2); i <= n; i++) L.suppliers.push({ id: 'sup' + i, name: 'تأمین ' + i });
  for (let i = 1, n = L.suppliers.length ? r.int(0, 2) : 0; i <= n; i++) {
    const date = nearDate(r, base, 90);
    const total = afn(r) * r.int(1, 20);
    const paid = r.pick([0, total, Math.round(total / 2)]);
    L.purchases.push({ id: 'pu' + i, productId: 'p' + r.int(1, np), supplierId: 'sup' + r.int(1, L.suppliers.length), quantity: r.int(1, 20), purchasePrice: afn(r), totalAmount: total, paidAmount: paid, debt: total - paid, date, createdAt: at(date) });
  }
  for (let i = 1, n = L.suppliers.length ? r.int(0, 2) : 0; i <= n; i++) {
    const date = nearDate(r, base, 60);
    L.supplierPayments.push({ id: 'sp' + i, supplierId: 'sup' + r.int(1, L.suppliers.length), amount: afn(r), date, createdAt: at(date) });
  }
  for (let i = 1, n = r.int(0, 3); i <= n; i++) {
    const date = day();
    const a = { id: 'a' + i, type: r.pick(['cancel_sale', 'line_delete', 'no_sale', 'sale', 'drawer_open']), refId: L.sales.length ? 's' + r.int(1, L.sales.length) : '', date };
    a[r.chance(0.5) ? 'user' : 'by'] = messy(r, r.pick(NAMES));
    if (r.chance(0.3)) a.approvedBy = 'مدیر';
    if (a.type === 'line_delete') a.amount = afn(r);
    if (r.chance(0.5)) a.createdAt = at(date); else a.at = at(date);
    L.auditLog.push(a);
  }
  return JSON.parse(JSON.stringify(L));
}

function reportOpts(r, fn, base) {
  const clock = r.weighted([[3, null], [5, { offsetMinutes: 270, dayStartMinute: 0 }], [3, { offsetMinutes: 270, dayStartMinute: 240 }], [1, { offsetMinutes: 270, dayStartMinute: 120 }], [1, { offsetMinutes: 0, dayStartMinute: 0 }]]);
  const costing = r.pick(['line', 'mavg']);
  const o = {};
  if (fn === 'today') {
    if (clock && r.chance(0.3)) o.now = kabulMs(nearDate(r, base, 3), r.int(0, 1439)); else o.date = nearDate(r, base, 3);
    o.clock = clock; o.costing = costing;
  } else if (fn === 'sales') {
    if (r.chance(0.85)) o.from = nearDate(r, base, 6);
    if (r.chance(0.85)) o.to = o.from && r.chance(0.9) ? CAL.addDays(o.from, r.int(0, 10)) : nearDate(r, base, 6);
    o.clock = clock; o.costing = costing;
  } else if (fn === 'debts') {
    o.asOf = nearDate(r, base, 40);
    if (r.chance(0.5)) o.buckets = r.pick([[7, 30], [30, 60, 90], [], [1]]);
  } else {
    o.from = nearDate(r, base, 3); o.to = CAL.addDays(o.from, r.int(0, 4)); o.clock = clock;
  }
  if ((fn === 'today' || fn === 'sales') && r.chance(0.3)) o.costApproved = true;
  return o;
}

const report = {
  'report.run': (r) => {
    const ledger = reportLedger(r);
    const base = (ledger.sales[0] && ledger.sales[0].date) || baseDate(r);
    const tables = r.chance(0.3);
    const fn = r.pick(['today', 'sales', 'sales', 'debts', 'fraud']);
    const c = { ledger, fn: tables ? 'tables' : fn, opts: reportOpts(r, fn, base) };
    if (tables) c.of = fn;
    return c;
  },
};

function runReport(x) {
  if (x.fn === 'tables') return REPORT.tables(x.of, REPORT[x.of](x.ledger, x.opts)).map(REPORT.plain);
  return REPORT[x.fn](x.ledger, x.opts);
}


module.exports = {
  ROOT, M, STOCK, REPORT, PROMO, DEBT, CAL, TEXT, CAP, MAX, S53, rng,
  D, J, anyInt, legacyDouble, boundedDouble, EDGE_DATES, BAD_DATES, nearDate, baseDate, kabulMs, WS, messy,
  money, runMoney, cap, runCap, stock, runStock, report, runReport,
};
