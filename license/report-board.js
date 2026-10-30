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
  var SPANS = ['today', 'week', 'month', 'quarter', 'lastQuarter', 'all'];
  var SPAN_LABELS = { today: 'امروز', week: '۷ روز', month: 'این ماه', quarter: 'این سه‌ماهه', lastQuarter: 'سه‌ماههٔ پیش', all: 'همه' };
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
    //  LOC-02: سه‌ماههٔ مالی (حمل–جوزا … جدی–حوت) — «این» تا امروز، «پیش» کامل
    if (span === 'quarter' || span === 'lastQuarter') {
      //  کشِ کهنهٔ calendar-core.js (سرویس‌ورکر یک بار دیرتر تازه می‌کند) ⇒ همان «امروز»، نه خطا
      var at = typeof cal.fiscalQuarterOf === 'function' ? cal.fiscalQuarterOf(today) : null;
      if (at) {
        if (span === 'quarter') {
          var cur = cal.fiscalQuarterRange(at.y, at.q);
          if (cur) return { from: cur.from, to: today };
        } else {
          var prev = at.q === 1 ? cal.fiscalQuarterRange(at.y - 1, 4) : cal.fiscalQuarterRange(at.y, at.q - 1);
          if (prev) return prev;
        }
      }
    }
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
    return { date: t.date, kpis: kpis, pills: pills };
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
  };
});
