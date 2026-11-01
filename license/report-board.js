/* ==========================================================
   فروشگاه من | تابلوی گزارش (report-board) — کارت‌ها و نشان‌های خانه
   ----------------------------------------------------------
   خالص: نه DOM، نه ساعتِ دستگاه. روی خروجیِ `report-core.js` می‌نشیند و
   فقط می‌گوید «کدام کارت، کدام عدد، کدام نشان، و کلیک به کدام فهرست» —
   v3 §۱۹.۵ بند ۲ و VISUAL-DIRECTION §۳ بند ۲. عددِ تازه‌ای نمی‌سازد؛ هر
   عدد همان عددِ هسته است (پس کارت و جمعِ جدولِ مقصد همیشه یکی‌اند).
   سود تا تصمیمِ D-23 «تقریبی» نوشته می‌شود (v3 §۱۵.۴ بند ۴).

   دوقلوی `core/report/ReportBoard.kt`؛ هر دو `core/test-vectors/report-board-v1.json`
   را عیناً می‌گذرانند. رفتارِ یکی را بی دیگری و بی فایلِ مرجع عوض نکنید.
   `cashierAlerts` (RSK-10) دوقلوی `core/risk/CashierAlerts.kt` (commonMain) است و هر دو
   `core/test-vectors/cashier-alerts-v1.json` را می‌گذرانند.
   ========================================================== */
(function (factory) {
  'use strict';
  var g = typeof globalThis !== 'undefined' ? globalThis : (typeof window !== 'undefined' ? window : {});
  var node = typeof module !== 'undefined' && module.exports;
  var cal = node ? require('./calendar-core.js') : g.TohidCalendar;
  var core = node ? require('./report-core.js') : g.TohidReportCore;
  if (!cal || !core) throw new Error('report-board: calendar-core.js and report-core.js must load first');
  var api = factory(cal, core);
  if (node) module.exports = api;
  if (typeof window !== 'undefined') window.TohidReportBoard = api;
  else g.TohidReportBoard = api;
})(function (cal, core) {
  'use strict';

  var VERSION = 1;
  /** بازه‌های صفحهٔ گزارشِ تازه — همان معنای `ReportEngine.rangeOf` */
  var SPANS = ['today', 'week', 'month', 'all'];
  var SPAN_LABELS = { today: 'امروز', week: '۷ روز', month: 'این ماه', all: 'همه' };
  /** زبانه‌ها؛ `ranged` یعنی بازه دارد */
  var TABS = [
    { id: 'today', label: 'امروز', ranged: false },
    { id: 'sales', label: 'فروش', ranged: true },
    { id: 'profit', label: 'سود و حاشیه', ranged: true },
    { id: 'debts', label: 'سن بدهی', ranged: false },
    { id: 'fraud', label: 'ضدتقلب', ranged: true },
  ];
  var DIMS = ['product', 'category', 'user', 'hour', 'payment', 'channel'];
  var DIM_LABELS = { product: 'کالا', category: 'دسته', user: 'کاربر', hour: 'ساعت', payment: 'روش پرداخت', channel: 'کانال' };
  var VS = 'نسبت به دیروز';

  function preset(tab, span, dim) { return { tab: tab, span: span, dim: dim }; }

  /** بازه ⇐ {from, to}؛ ناشناخته = امروز */
  function range(span, today) {
    if (span === 'all') return { from: '0000-01-01', to: '9999-12-31' };
    if (span === 'week') return { from: cal.addDays(today, -6) || today, to: today };
    if (span === 'month') return { from: String(today).slice(0, 7) + '-01', to: today };
    return { from: today, to: today };
  }

  /** بیسیس‌پوینت ⇐ «+12.5٪» (یک رقمِ اعشار، نیمه رو به بالا روی قدرِ مطلق)؛ null ⇐ null */
  function pct(bp) {
    if (bp === null || bp === undefined) return null;
    var a = bp < 0 ? -bp : bp;
    var tenths = Math.floor((a + 5) / 10);
    if (tenths === 0) return '0٪';
    var r = tenths % 10;
    return (bp > 0 ? '+' : '-') + Math.floor(tenths / 10) + (r ? '.' + r : '') + '٪';
  }

  function trendOf(diff) { return diff > 0 ? 'up' : (diff < 0 ? 'down' : 'flat'); }

  function deltaText(d) {
    if (!d) return null;
    if (d.bp !== null && d.bp !== undefined) return pct(d.bp) + ' ' + VS;
    return d.diff === 0 ? 'بی تغییر ' + VS : 'دیروز صفر یا منفی بود';
  }

  function kpi(id, title, value, kind, d, period, approximate, target) {
    return {
      id: id, title: title, value: value, kind: kind,
      delta: d ? { diff: d.diff, bp: d.bp === undefined ? null : d.bp } : null,
      trend: d ? trendOf(d.diff) : null, good: d ? d.diff > 0 : null, deltaText: deltaText(d),
      period: period, approximate: approximate, target: target,
    };
  }

  function pill(id, text, tone, target) { return { id: id, text: text, tone: tone, target: target }; }

  /**
   * کارت‌ها و نشان‌های خانه از سه گزارشِ هسته (`today`، `debts`، `fraud`ِ همان روز).
   * `money === false` (شاگرد): سود و بهای خرید پنهان است، نه صفر.
   */
  function home(t, debts, fraud, money) {
    var d0 = t.today, vs = t.vsYesterday;
    var showMoney = money !== false;
    var kpis = [
      kpi('netSales', 'فروش خالص امروز', d0.netSales, 'money', vs.netSales, 'امروز', false, preset('sales', 'today', 'product')),
      kpi('invoices', 'فاکتورهای امروز', d0.invoices, 'count', vs.invoices, 'امروز', false, preset('sales', 'today', 'user')),
    ];
    if (showMoney) {
      kpis.push(kpi('grossProfit', d0.profitApproximate ? 'سود ناخالص امروز (تقریبی)' : 'سود ناخالص امروز', d0.grossProfit,
        'money', vs.grossProfit, 'امروز', d0.profitApproximate, preset('profit', 'today', 'product')));
    }
    kpis.push(kpi('avgTicket', 'میانگین فاکتور', d0.avgTicket, 'money', vs.avgTicket, 'امروز', false, preset('today', 'today', '')));
    kpis.push(kpi('receivable', 'طلب از مشتریان', debts.customers.totals.balance, 'money', null, 'تا امروز', false, preset('debts', 'all', '')));

    var pills = [];
    if (showMoney) {
      pills.push(d0.profitApproximate
        ? pill('profit', 'سود تقریبی', 'warning', preset('profit', 'today', 'product'))
        : pill('profit', 'سود قطعی', 'success', preset('profit', 'today', 'product')));
      if (d0.costEstimatedLines > 0) {
        pills.push(pill('cost', d0.costEstimatedLines + ' قلم بی بهای خرید', 'warning', preset('profit', 'today', 'product')));
      }
    }
    var edge = debts.buckets.length ? debts.buckets[debts.buckets.length - 1] : 0;
    var late = debts.customers.rows.filter(function (r) { return r.buckets[r.buckets.length - 1] > 0; }).length;
    pills.push(late > 0
      ? pill('overdue', late + ' مشتری با قرض بیش از ' + edge + ' روز', 'danger', preset('debts', 'all', ''))
      : pill('overdue', 'قرض بیش از ' + edge + ' روز نیست', 'success', preset('debts', 'all', '')));
    var f = fraud.totals, events = f.returnCount + f.voidCount + f.lineDeleteCount + f.noSaleCount;
    pills.push(events > 0
      ? pill('fraud', events + ' رویداد حساس امروز', 'warning', preset('fraud', 'today', ''))
      : pill('fraud', 'رویداد حساسی امروز نیست', 'success', preset('fraud', 'today', '')));
    //  RSK-10: هشدار برای صاحب و مدیر است (همان که زبانهٔ ضدتقلب را می‌بیند)، نه شاگرد
    var alerts = money === false ? [] : alertsOf(fraud);
    if (alerts.length) pills.push(pill('cashier', alerts.length + ' هشدارِ رفتارِ غیرعادیِ صندوقدار امروز', 'danger', preset('fraud', 'today', '')));
    return { date: t.date, kpis: kpis, pills: pills };
  }

  /* ---------------- RSK-10: هشدارِ رفتارِ غیرعادیِ صندوقدار ---------------- */

  var ALERT_KINDS = ['void', 'discount', 'no_sale'];
  var MAX_EVENTS = 100000, MAX_INVOICES = 10000000;
  function clampInt(n, lo, hi) { n = Math.floor(Number(n) || 0); return n < lo ? lo : (n > hi ? hi : n); }
  function countOf(r, k) { return k === 'void' ? r.voids : (k === 'discount' ? r.discounts : r.noSales); }

  /**
   * «معمول» = نرخِ بقیهٔ کاربرانِ همین بازه (رویداد به‌ازای فاکتور). هشدار: دستِ کم `minCount` بار و نرخِ کاربر
   * دستِ کم `factorPct` درصدِ نرخِ بقیه (شمارِ بقیه کمِ کم ۱؛ کاربرِ بی فاکتور مخرجِ ۱). بی فاکتورِ همکار هشداری نیست؛
   * کلیدِ خالی در «بقیه» هست ولی هشدار نمی‌گیرد. فقط عددِ صحیحِ زیرِ ۲^۵۳ — همان حسابِ CashierAlerts.kt.
   * `rows`: [{key, invoices, voids, discounts, noSales}]؛ `policy`: {minCount = 3, factorPct = 200}.
   */
  function cashierAlerts(rows, policy) {
    policy = policy || {};
    var min = clampInt(policy.minCount === undefined ? 3 : policy.minCount, 1, MAX_EVENTS);
    var factor = clampInt(policy.factorPct === undefined ? 200 : policy.factorPct, 100, 1000);
    var out = [];
    ALERT_KINDS.forEach(function (k, ki) {
      rows.forEach(function (u, i) {
        var c = clampInt(countOf(u, k), 0, MAX_EVENTS);
        if (!u.key || c < min) return;
        var peerCount = 0, peerInvoices = 0;
        rows.forEach(function (p, j) {
          if (j === i) return;
          peerCount = clampInt(peerCount + clampInt(countOf(p, k), 0, MAX_EVENTS), 0, MAX_EVENTS);
          peerInvoices = clampInt(peerInvoices + clampInt(p.invoices, 0, MAX_INVOICES), 0, MAX_INVOICES);
        });
        if (peerInvoices === 0) return;
        var mine = clampInt(u.invoices, 0, MAX_INVOICES), den = Math.max(1, mine), base = Math.max(1, peerCount);
        if (c * peerInvoices * 100 < factor * base * den) return;
        out.push({ key: String(u.key), kind: k, count: c, invoices: mine, peerCount: peerCount, peerInvoices: peerInvoices,
          ratioPct: Math.floor((c * peerInvoices * 100) / (den * base)), k: ki });
      });
    });
    out.sort(function (a, b) {
      return a.k - b.k || b.ratioPct - a.ratioPct || b.count - a.count || (a.key < b.key ? -1 : (a.key > b.key ? 1 : 0));
    });
    return out.map(function (a) { delete a.k; return a; });
  }

  /** هشدارهای یک گزارشِ ضدتقلبِ هسته (`core.fraud`) */
  function alertsOf(fraud, policy) {
    return cashierAlerts((fraud && fraud.rows || []).map(function (r) {
      return { key: r.key, invoices: r.invoices, voids: r.voidCount, discounts: r.discountCount, noSales: r.noSaleCount };
    }), policy);
  }

  /**
   * از دفتر تا تابلو، با همان هسته. `opts`: {date (اجباری), clock, costing, costApproved, money}.
   */
  function build(raw, opts) {
    opts = opts || {};
    var L = core.ledger(raw), date = String(opts.date || '');
    var common = { clock: opts.clock, costing: opts.costing, costApproved: opts.costApproved };
    var t = core.today(L, { date: date, clock: opts.clock, costing: opts.costing, costApproved: opts.costApproved });
    var debts = core.debts(L, { asOf: date, clock: common.clock });
    var fraud = core.fraud(L, { from: date, to: date, clock: common.clock });
    return home(t, debts, fraud, opts.money);
  }

  function cmpCell(a, b) {
    if (typeof a === 'number' && typeof b === 'number') return a < b ? -1 : (a > b ? 1 : 0);
    var x = String(a), y = String(b);
    return x < y ? -1 : (x > y ? 1 : 0);
  }

  /**
   * ردیف‌های یک جدولِ `tables()` مرتب به ستونِ `col` (پایدار؛ خانهٔ خالی همیشه آخر).
   * جدول دست نمی‌خورد؛ ردیفِ جمع جابه‌جا نمی‌شود.
   */
  function sortRows(rows, col, desc) {
    return rows.map(function (r, i) { return { r: r, i: i }; }).sort(function (p, q) {
      var a = p.r[col], b = q.r[col];
      var an = a === null || a === undefined, bn = b === null || b === undefined;
      if (an || bn) return an === bn ? p.i - q.i : (an ? 1 : -1);
      var c = cmpCell(a, b);
      return (desc ? -c : c) || p.i - q.i;
    }).map(function (p) { return p.r; });
  }

  return {
    VERSION: VERSION,
    SPANS: SPANS,
    SPAN_LABELS: SPAN_LABELS,
    TABS: TABS,
    DIMS: DIMS,
    DIM_LABELS: DIM_LABELS,
    range: range,
    pct: pct,
    deltaText: deltaText,
    home: home,
    build: build,
    sortRows: sortRows,
    cashierAlerts: cashierAlerts,
    alertsOf: alertsOf,
  };
});
