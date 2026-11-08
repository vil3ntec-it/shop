'use strict';
/**
 * دورِ دومِ فازِ تفاضلی، بخشِ دوم (شرح: `core-fuzz.js` و `core-fuzz-gen2.js`):
 *   pick      server/src/lib/pick-core.js           ⇄ core/pick/{PickCore,PickSubstitute}.kt
 *   po        server/src/lib/purchase-order-core.js ⇄ core/purchase/PurchaseOrderCore.kt
 *   board     license/report-board.js               ⇄ android-native/.../core/report/ReportBoard.kt (+ core/risk/CashierAlerts.kt)
 */
const path = require('path');
const G = require('./core-fuzz-gen.js');
const { BLANKISH, PROTO, putOpt, T0 } = require('./core-fuzz-gen2.js');

const { ROOT, CAL, MAX, S53, nearDate, baseDate, EDGE_DATES, BAD_DATES, reportLedger } = G;

const PICK = require(path.join(ROOT, 'server', 'src', 'lib', 'pick-core.js'));
const PO = require(path.join(ROOT, 'server', 'src', 'lib', 'purchase-order-core.js'));
const BOARD = require(path.join(ROOT, 'license', 'report-board.js'));

/* ======================= جمع‌آوری ======================= */

const LOC = ['', 'A', 'a', 'B', 'A2', 'A10', 'A02', '۱۲', '٣', ' B 1 ', 'B 1', '﻿C', '\u001FC', '0', '00', '9', 'x'.repeat(25), 'ب۲', 'A-1', 'a1b2', 'A 2'];
const PICK_TEXT = ['کیلو', 'کیلوگرم', 'کیلو گرم', 'kg', 'KG', ' Kg ', 'gram', 'g', 'عدد', 'دانه', 'halal', 'HALAL', 'non_halal', ' halal ', '﻿halal', 'halal\u001F',
  'نوشیدنی', 'Drinks', ' drinks ', 'drinks ', '۶۲۶۰-۰۱', '6260012345678', 'ß', 'ﬀ', 'İ', 'ı', 'ΑΣ', 'K', '\u001F', '﻿', ' ', '\t', '-', ' - '];
function pickText(r) { let s = ''; for (let i = r.int(0, 3); i > 0; i--) s += r.pick(PICK_TEXT); return s; }
const BARCODES = ['6260012345678', '06260012345678', '00006260012345678', '۶۲۶۰۰۱۲۳۴۵۶۷۸', '626-0012 345678', '626 0012345678', 'abc-123', 'ABC123', '', ' ', '-',
  '00000000', '0000000', '0', '12345670', '012345670', 'ß', 'ẞ', 'ﬀ', '﻿6260012345678', '6260012345678\u001F', 'x'.repeat(70)];
function pickItem(r, i) {
  const o = {
    productId: r.weighted([[9, () => 'p' + i], [1, () => r.pick(['', 'p0'])]]),
    category: r.pick(['نوشیدنی', 'نوشیدنی ', ' نوشیدنی', 'drinks', 'Drinks', '', '﻿نوشیدنی', 'نوشیدنی\u001F', 'خوراکی']),
    subcategory: r.pick(['', '', 'گازدار', 'گازدار ', 'آب']),
    halal: r.pick(['', 'halal', 'HALAL', ' halal', '﻿halal', 'halal\u001F', 'non_halal', 'x']),
    unit: r.pick(['', 'عدد', 'کیلو', 'kg', ' KG ', 'گرم', 'kg\u001F']),
    brand: r.pick(['', 'کوکا', 'کوکا ', 'پپسی']),
    name: r.pick(['', 'الف', 'ب', 'Cola', 'cola']),
  };
  const p = r.weighted([[7, () => r.int(1, 5000) * 100], [1, undefined], [1, () => r.pick([0, -5, 2 ** 60, MAX])]]);
  if (p !== undefined) o.priceMinor = p;
  return o;
}
const pickQty = (r) => r.weighted([[5, () => r.int(1, 10000)], [2, () => r.int(-1000, 1000)], [1, () => r.int(0, 1e12)], [1, () => r.pick([0, MAX, -MAX, 2 ** 60, S53])]]);
const pick = {
  'pick.line': (r) => ({ q: pickQty(r), p: r.weighted([[5, () => r.int(0, 5000) * 100], [2, () => r.int(-1e6, 1e6)], [1, () => r.pick([0, MAX, -MAX, 2 ** 60])]]) }),
  'pick.order': (r) => {
    const lines = [];
    for (let i = 1, n = r.int(0, 6); i <= n; i++) {
      const l = { lineNo: r.chance(0.9) ? i : r.int(1, 3), name: r.pick(['', 'آب', 'برنج', 'Cola', 'cola', 'آب']) };
      for (const k of ['aisle', 'shelf', 'level']) if (r.chance(0.8)) l[k] = r.pick(LOC);
      lines.push(l);
    }
    return { lines };
  },
  'pick.barcode': (r) => {
    const codes = [];
    for (let i = r.int(0, 3); i > 0; i--) codes.push(r.pick(BARCODES));
    return { codes, scanned: r.pick(BARCODES) };
  },
  'pick.text': (r) => ({ s: pickText(r) }),
  'pick.weigh': (r) => {
    const o = r.weighted([[6, () => r.int(1, 5000)], [1, () => r.pick([0, -100, 1e12, MAX])]]);
    const x = { ordered: o, actual: Number.isSafeInteger(o) ? o + r.int(-Math.min(Math.abs(o), 1e6), Math.min(Math.abs(o), 1e6)) : 1 };
    putOpt(x, 'bp', r.weighted([[4, undefined], [5, () => r.pick([0, 500, 1000, 1500, 5000])], [1, () => r.pick([-5, 6000, 10000, 1])]]));
    return x;
  },
  'pick.settle': (r) => {
    const lines = [];
    for (let i = 1, n = r.int(0, 5); i <= n; i++) {
      const ordered = r.weighted([[6, () => r.int(1, 10) * 1000], [1, () => r.int(1, 9999)]]);
      const unit = r.weighted([[7, () => r.int(1, 5000) * 100], [1, () => r.pick([0, -100, MAX, 2 ** 52])]]);
      lines.push({
        lineNo: i, status: r.pick(['pending', 'picked', 'picked', 'short', 'substituted', 'not_found', 'removed', '']),
        orderedQtyMilli: ordered, pickedQtyMilli: r.weighted([[5, ordered], [3, () => r.int(0, ordered)], [1, () => r.pick([-1000, 0, ordered + 500])]]),
        unitPriceMinor: unit, originalTotalMinor: r.weighted([[8, () => (Number.isSafeInteger(unit * ordered) ? Math.round(ordered * unit / 1000 / 100) * 100 : 0)], [2, () => r.int(0, 1e6)]]),
      });
    }
    const x = { lines };
    putOpt(x, 'discountMinor', r.weighted([[4, undefined], [4, () => r.int(0, 1e6)], [1, () => r.pick([-100, 1e15])]]));
    putOpt(x, 'deliveryFeeMinor', r.weighted([[4, undefined], [4, () => r.int(0, 50000)], [1, () => -500]]));
    return x;
  },
  'pick.metrics': (r) => {
    const picks = [];
    for (let i = r.int(0, 5); i > 0; i--) {
      const start = r.weighted([[8, () => T0 + r.int(0, 1e6)], [1, null], [1, undefined]]);
      const p = { lines: [] };
      putOpt(p, 'startedAt', start);
      putOpt(p, 'packedAt', r.weighted([[7, () => (typeof start === 'number' ? start + r.int(-1000, 1e6) : T0)], [1, null], [1, undefined]]));
      for (let k = r.int(0, 4); k > 0; k--) p.lines.push({ status: r.pick(['picked', 'short', 'substituted', 'not_found', 'removed', 'pending', '']) });
      picks.push(p);
    }
    return { picks };
  },
  'pick.sub': (r) => {
    const orig = pickItem(r, 0);
    const cands = [];
    for (let i = 1, n = r.int(0, 4); i <= n; i++) cands.push(r.chance(0.05) ? null : pickItem(r, i));
    const x = { orig, cands, qty: r.weighted([[8, () => r.int(1, 5) * 1000], [1, () => r.int(1, 9999)]]) };
    putOpt(x, 'limit', r.weighted([[5, undefined], [5, () => r.int(0, 4)]]));
    return x;
  },
};
const PICK_RUN = {
  'pick.line': (x) => PICK.lineTotalMinor(x.q, x.p),
  'pick.order': (x) => PICK.pickOrder(x.lines),
  'pick.barcode': (x) => ({ matches: PICK.barcodeMatches(x.codes, x.scanned), norm: PICK.normBarcode(x.scanned) }),
  'pick.text': (x) => ({
    unit: PICK.isWeighedUnit(x.s), loc: PICK.locPart(x.s), norm: PICK.normBarcode(x.s), halal: PICK.halalOf(x.s), category: PICK.normCategory(x.s), latin: PICK.latin(x.s),
  }),
  'pick.weigh': (x) => PICK.weighVerdict(x.ordered, x.actual, x.bp),
  'pick.settle': (x) => PICK.settle(x),
  'pick.metrics': (x) => PICK.metrics(x.picks),
  'pick.sub': (x) => {
    const c0 = x.cands.find((c) => c && Number.isSafeInteger(c.priceMinor));
    return {
      blocks: x.cands.map((c) => PICK.substituteBlock(x.orig, c)),
      rank: PICK.rankSubstitutes(x.orig, x.cands.filter((c) => c), x.limit),
      charge: c0 && Number.isSafeInteger(x.orig.priceMinor) ? PICK.substituteCharge(x.orig.priceMinor, c0.priceMinor, x.qty) : null,
    };
  },
};
const runPick = (fn, x) => PICK_RUN[fn](x);

/* ======================= سفارشِ خرید ======================= */

function poOrder(r, state) {
  const lines = [];
  for (let i = 1, n = r.weighted([[9, () => r.int(1, 4)], [1, 0]]); i <= n; i++) {
    lines.push({
      id: r.weighted([[9, () => 'L' + i], [1, () => r.pick(['', 'L 1', 'x'.repeat(65), 'ل۱', 'L1\n', 'L1', 'a:b.c-d_e'])]]),
      productId: r.weighted([[9, () => 'p' + i], [1, () => r.pick(['', ' ', '\t', '﻿', '\u001F', 'x'.repeat(129), 'p1'])]]),
      qtyMilli: r.weighted([[7, () => r.int(1, 20) * 1000], [2, () => r.int(1, 99999)], [1, () => r.pick([0, -1, MAX, 2 ** 60])]]),
      priceMinor: r.weighted([[7, () => r.int(0, 5000) * 100], [2, () => r.int(0, 99999)], [1, () => r.pick([-1, MAX, 2 ** 60, 1e12])]]),
    });
  }
  return {
    supplierId: r.weighted([[9, 'sup1'], [1, () => r.pick(['', ' \t', '﻿', '\u001F', 'x'.repeat(129)])]]),
    state: state || r.weighted([[9, () => r.pick(['draft', 'sent', 'cancelled'])], [1, () => r.pick(['', 'partial', 'Draft'])]]),
    lines,
  };
}
function poReceipt(r, o, k) {
  const lines = [];
  for (let i = r.int(0, 3); i > 0; i--) {
    const l = o.lines.length && r.chance(0.9) ? r.pick(o.lines) : { id: 'L9', qtyMilli: 1000, priceMinor: 100 };
    const received = r.weighted([[6, () => r.int(0, Math.min(l.qtyMilli > 0 ? l.qtyMilli : 1000, 1e9))], [2, () => l.qtyMilli], [1, () => r.pick([0, -1, MAX, 2 ** 60])], [1, () => (Number.isSafeInteger(l.qtyMilli) ? l.qtyMilli + 1000 : 5)]]);
    lines.push({
      lineId: l.id, receivedMilli: received,
      damagedMilli: r.weighted([[7, 0], [2, () => (Number.isSafeInteger(received) && received > 0 ? r.int(0, Math.min(received, 1e9)) : 0)], [1, () => r.pick([-1, 1, 2 ** 60])]]),
      priceMinor: r.weighted([[8, () => l.priceMinor], [2, () => r.int(0, 5000) * 100], [1, () => r.pick([-1, 2 ** 60])]]),
    });
  }
  return { id: r.weighted([[9, () => 'rc' + k], [1, () => r.pick(['', 'rc 1', 'rc1'])]]), lines };
}
function poOps(r, o) {
  const ops = [];
  for (let i = 1, n = r.int(1, 5); i <= n; i++) {
    ops.push(r.weighted([
      [3, () => ({ op: 'send' })],
      [4, () => ({ op: 'receive', input: poReceipt(r, o, r.int(1, i)), allowOver: r.chance(0.15) })],
      [2, () => ({ op: 'cancel', reason: r.weighted([[5, 'تأخیر'], [3, () => r.pick(BLANKISH)], [1, undefined]]) })],
      [1, () => ({ op: 'edit', next: poOrder(r, r.chance(0.85) ? 'draft' : 'sent') })],
    ]));
  }
  return ops;
}
const po = {
  'po.mul': (r) => ({
    a: r.weighted([[5, () => r.int(-1e7, 1e7)], [2, () => r.sign() * r.int(0, MAX)], [1, () => r.pick([0, MAX, -MAX, S53, 3037000499])]]),
    b: r.weighted([[5, () => r.int(-1e6, 1e6)], [2, () => r.sign() * r.int(0, MAX)], [1, () => r.pick([0, 1, -1, MAX, -MAX])]]),
    c: r.weighted([[6, 1000], [2, 10000], [1, () => r.int(1, 2147483647)], [1, () => r.pick([0, -1, 1, 2147483647, 2147483648, 3])]]),
  }),
  'po.check': (r) => ({ order: poOrder(r) }),
  'po.flow': (r) => { const order = poOrder(r, r.chance(0.7) ? 'draft' : undefined); return { order, ops: poOps(r, order) }; },
  'po.match': (r) => {
    const order = poOrder(r, 'sent');
    //  سفارشِ فرستاده‌شده از checkOrder گذشته است: فقط گاهی لبهٔ بیرون از بازه می‌ماند
    for (const l of order.lines) {
      if (r.chance(0.95) && !(Number.isSafeInteger(l.qtyMilli) && l.qtyMilli > 0 && l.qtyMilli <= 1e9)) l.qtyMilli = 1000;
      if (r.chance(0.95) && !(Number.isSafeInteger(l.priceMinor) && l.priceMinor >= 0 && l.priceMinor <= 1e9)) l.priceMinor = 100;
    }
    const receipts = [];
    for (let k = 1, n = r.int(0, 3); k <= n; k++) {
      const res = PO.receive(order, receipts, poReceipt(r, order, k), r.chance(0.2));
      if (res.ok) receipts.push(res.ok.receipt);
    }
    const bills = [];
    for (let i = r.int(0, 4); i > 0; i--) {
      const l = order.lines.length ? r.pick(order.lines) : { id: 'L9', productId: 'p9', qtyMilli: 1000, priceMinor: 100 };
      const b = { productId: r.chance(0.85) ? l.productId : r.pick(['p8', 'p9', 'p0']), qtyMilli: r.weighted([[7, () => r.int(0, 20) * 1000], [1, () => r.pick([-1000, 0, 2 ** 60])]]) };
      putOpt(b, 'lineId', r.weighted([[4, () => l.id], [3, undefined], [1, null], [1, 'L9']]));
      b.totalMinor = r.weighted([[6, () => (Number.isSafeInteger(b.qtyMilli * l.priceMinor) ? Math.round(b.qtyMilli * l.priceMinor / 1000) + r.int(-500, 500) : 0)], [2, () => r.int(0, 1e7)], [1, () => r.pick([-100, 2 ** 60])]]);
      bills.push(b);
    }
    const x = { order, receipts, bills };
    if (r.chance(0.6)) {
      x.tol = {};
      putOpt(x.tol, 'qtyMilli', r.weighted([[3, undefined], [4, () => r.int(0, 2000)], [1, -1]]));
      putOpt(x.tol, 'priceBp', r.weighted([[3, undefined], [4, () => r.int(0, 1000)], [1, () => r.pick([-1, 2 ** 60])]]));
      putOpt(x.tol, 'absMinor', r.weighted([[3, undefined], [4, () => r.int(0, 1000)], [1, -1]]));
    }
    return x;
  },
};
function runPoFlow(x) {
  let o = JSON.parse(JSON.stringify(x.order));
  const receipts = [];
  const steps = x.ops.map((s) => {
    let res;
    if (s.op === 'send') res = PO.send(o, receipts);
    else if (s.op === 'cancel') res = PO.cancel(o, receipts, s.reason);
    else if (s.op === 'edit') res = PO.edit(o, receipts, s.next);
    else {
      res = PO.receive(o, receipts, s.input, s.allowOver);
      if (res.ok) receipts.push(res.ok.receipt);
      return res;
    }
    if (res.changed) o = s.op === 'edit' ? Object.assign({}, s.next) : Object.assign({}, o, { state: res.changed });
    return res;
  });
  let after;
  try { after = { status: PO.status(o, receipts), totals: PO.totals(o, receipts), progress: PO.progress(o, receipts) }; } catch (e) { after = { throws: true }; }
  return { steps, after };
}
function runPo(fn, x) {
  switch (fn) {
    case 'po.mul': return PO.mulDivRound(x.a, x.b, x.c);
    case 'po.check': return PO.checkOrder(x.order);
    case 'po.flow': return runPoFlow(x);
    case 'po.match': return PO.match(x.order, x.receipts, x.bills, x.tol);
    default: throw new Error('fn: ' + fn);
  }
}

/* ======================= تابلوی گزارش ======================= */

const CELLS = [null, 0, 1, 2, 9, 10, -1, 1.5, -0.5, 1e21, 1e-7, 'abc', 'Abc', '10', '9', '', 'ä', 'Z', 'ب', true, false, 'true', '1e3'];
const board = {
  'board.range': (r) => ({
    span: r.weighted([[8, () => r.pick(['today', 'week', 'month', 'all', 'quarter', 'lastQuarter'])], [1, () => r.pick(['', 'Week', 'year', ...PROTO])]]),
    today: r.weighted([[7, () => r.pick(EDGE_DATES)], [2, () => CAL.addDays('2026-01-01', r.int(-4000, 4000))], [1, () => r.pick(BAD_DATES)]]),
  }),
  'board.pct': (r) => ({ bp: r.weighted([[6, () => r.int(-100000, 100000)], [2, () => r.int(-20, 20)], [1, () => r.pick([null, 0, 4, 5, -5, -4, 14, 15, 95, 994, 995, MAX, -MAX])]]) }),
  'board.sort': (r) => {
    const rows = [];
    for (let i = r.int(0, 6); i > 0; i--) { const row = []; for (let k = r.int(0, 3); k > 0; k--) row.push(r.pick(CELLS)); rows.push(row); }
    return { rows, col: r.int(0, 3), desc: r.chance(0.5) };
  },
  'board.alerts': (r) => {
    const rows = [];
    const ev = () => r.weighted([[6, () => r.int(0, 12)], [2, () => r.int(0, 300)], [1, () => r.pick([-5, 100001, 2 ** 40])]]);
    for (let i = 1, n = r.int(0, 5); i <= n; i++) {
      rows.push({ key: r.weighted([[8, () => 'u' + i], [1, ''], [1, () => 'u' + r.int(1, 2)]]), invoices: r.weighted([[7, () => r.int(0, 200)], [1, () => r.pick([0, -1, 2e7, 2 ** 40])]]), voids: ev(), discounts: ev(), noSales: ev() });
    }
    const x = { rows };
    if (r.chance(0.5)) {
      x.policy = {};
      putOpt(x.policy, 'minCount', r.weighted([[3, undefined], [3, () => r.int(1, 5)], [1, () => r.pick([0, -1, 200000])]]));
      putOpt(x.policy, 'factorPct', r.weighted([[3, undefined], [3, () => r.pick([100, 150, 200, 300])], [1, () => r.pick([0, 50, 5000])]]));
    }
    return x;
  },
  'board.build': (r) => {
    const ledger = reportLedger(r);
    const base = (ledger.sales[0] && ledger.sales[0].date) || baseDate(r);
    const opts = { date: r.chance(0.97) ? nearDate(r, base, 2) : r.pick(BAD_DATES) };
    putOpt(opts, 'clock', r.weighted([[3, undefined], [5, { offsetMinutes: 270, dayStartMinute: 0 }], [2, { offsetMinutes: 270, dayStartMinute: 240 }], [1, { offsetMinutes: 0, dayStartMinute: 0 }]]));
    putOpt(opts, 'costing', r.weighted([[3, undefined], [1, 'line'], [1, 'mavg']]));
    if (r.chance(0.3)) opts.costApproved = true;
    if (r.chance(0.3)) opts.money = false;
    return { ledger, opts };
  },
};
function runBoard(fn, x) {
  switch (fn) {
    case 'board.range': return BOARD.range(x.span, x.today);
    case 'board.pct': return BOARD.pct(x.bp);
    case 'board.sort': return BOARD.sortRows(x.rows, x.col, x.desc);
    case 'board.alerts': return BOARD.cashierAlerts(x.rows, x.policy);
    case 'board.build': return BOARD.build(x.ledger, x.opts);
    default: throw new Error('fn: ' + fn);
  }
}

module.exports = { PICK, PO, BOARD, pick, runPick, po, runPo, board, runBoard };
