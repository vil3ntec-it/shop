/* ==========================================================
   فروشگاه من | «گزارشِ تازه» در وب — صفحهٔ گزارش و کارت‌های خانه
   ----------------------------------------------------------
   RPT-01/02/03/06/09/11 و v3 §۱۹.۵ بند ۲، روی همان هستهٔ مشترک
   (`report-core.js` + `report-board.js`، دوقلوی `core/report/*.kt`).

   پشتِ تنظیمِ «گزارشِ تازه»، پیش‌فرض خاموش و فقط روی همین مرورگر
   (`localStorage`، همگام نمی‌شود). خاموش ⇒ همان صفحهٔ گزارش و داشبوردِ امروز،
   بی هیچ تغییری. دفتر و فرمتِ داده دست نمی‌خورد؛ این فایل فقط می‌خواند.

   دفتر و ابزارهای صفحه (رفتن به صفحه، CSV، چاپ، قالبِ پول و تاریخ) را
   `window.tohidReportEnv()`ِ index.html می‌دهد — نسخهٔ دومی از آن‌ها این‌جا نیست.

   RPT-03: روشِ بهای سود و حاشیه همان `COST` ِ report-board (دوقلوی `CostPolicy`): پیش‌فرض و مصوب میانگینِ موزون
   با `costApproved`؛ «قیمت خرید روی قلم» انتخابِ همین مرورگر (`tohid-report-cost`) و همیشه «تقریبی».
   RPT-11: «اکسل» = ‎.xlsx‎ ِ واقعی (`TohidOwnerExport.report`، عدد عدد می‌ماند) و «PDF» = فایلِ مستقیم (`report-pdf.js`،
   دیربار با نخستین بار) — هر دو از همان برگهٔ CSV و چاپ.
   ========================================================== */
(function () {
  'use strict';
  var KEY = 'tohid-report-fresh';
  var VM_KEY = 'view_mode:local:report-fresh';
  var COST_KEY = 'tohid-report-cost';
  var PDF_SRC = 'license/report-pdf.js?v=1';
  var state = { tab: 'today', span: 'month', dim: 'product', sort: null };

  function core() { return window.TohidReportCore; }
  function board() { return window.TohidReportBoard; }
  function env() { return typeof window.tohidReportEnv === 'function' ? window.tohidReportEnv() : null; }
  function get(k) { try { return window.localStorage.getItem(k); } catch (e) { return null; } }
  function put(k, v) { try { window.localStorage.setItem(k, v); } catch (e) { /* حالتِ خصوصی: فقط همین بار */ } }

  function enabled() { return get(KEY) === '1'; }

  /** RPT-03: روشِ بهای همین مرورگر (`COST.parse`: نبود ⇒ مصوب) و تأییدش برای هسته — هرگز `true` ِ ثابت */
  function cost() {
    var C = board() && board().COST;
    var m = C ? C.parse(get(COST_KEY)) : 'line';
    return { m: m, costing: m, costApproved: C ? C.approved(m) : false };
  }
  function homeOf(E) {
    var c = cost();
    return board().build(E.ledger, { date: E.today, clock: clock(), money: E.money, costing: c.costing, costApproved: c.costApproved });
  }
  function costLabel(base, m) { var C = board().COST; return C ? C.label(base, m) : base + ' (تقریبی)'; }
  /** همان `costLabel`، هر تکه در span ِ خودش تا لایهٔ زبان (`web-i18n.js`) هر کدام را جدا بشناسد */
  function labelHtml(base, m) {
    var full = costLabel(base, m);
    if (full === base) return esc(base);
    return '<span>' + esc(base) + '</span> (' + full.slice(base.length + 2, -1).split('، ').map(function (t) {
      return '<span>' + esc(t) + '</span>';
    }).join('، ') + ')';
  }
  /** `COST.explain`: شرحِ روش و (اگر مصوب نیست) چرا تقریبی است، دو جملهٔ جدا */
  function explainHtml(m) {
    var C = board().COST, def = C && C.METHODS.filter(function (x) { return x.id === m; })[0];
    if (!def) return '';
    var rest = C.explain(m).slice(def.note.length).replace(/^\s+/, '');
    return '<span>' + esc(def.note) + '</span>' + (rest ? ' <span>' + esc(rest) + '</span>' : '');
  }

  /** ساعتِ همین دستگاه: روزِ کاری از نیمه‌شب؛ فقط تفکیکِ «ساعت» از آن درمی‌آید */
  function clock() { return { offsetMinutes: -new Date().getTimezoneOffset(), dayStartMinute: 0 }; }

  function esc(s) {
    return String(s === undefined || s === null ? '' : s).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }

  /** «12.3٪» — یک رقمِ اعشار، بی علامتِ مثبت (همان `ReportFreshModel.percent`) */
  function percent(bp) {
    var a = bp < 0 ? -bp : bp, tenths = Math.floor((a + 5) / 10), r = tenths % 10;
    return (bp < 0 && tenths > 0 ? '-' : '') + Math.floor(tenths / 10) + (r ? '.' + r : '') + '٪';
  }

  function fmt(v, kind, E) {
    if (v === null || v === undefined) return '—';
    if (kind === 'money') return E.formatMoney(v / 100);
    if (kind === 'qty') return String(Math.round(v) / 1000);
    if (kind === 'bp') return percent(v);
    if (kind === 'flag') return v === true ? 'بله' : 'نه';
    if (kind === 'date') return E.formatDate(v);
    return String(v);
  }

  function phone() { return !!(window.matchMedia && window.matchMedia('(max-width: 599px)').matches); }
  /** VISUAL-DIRECTION §۱.۵: صفحهٔ مدیریت پیش‌فرض جدول؛ گوشی همیشه کارت */
  function viewMode() { return phone() ? 'card' : (get(VM_KEY) === 'card' ? 'card' : 'table'); }

  function tabsFor(E) { var B = board(); return E.money ? B.TABS : B.TABS.filter(function (t) { return t.id === 'debts'; }); }

  /* ---------------- خانه ---------------- */

  function presetAttrs(p) {
    return ' data-tab="' + esc(p.tab) + '" data-span="' + esc(p.span) + '" data-dim="' + esc(p.dim) + '"';
  }

  function kpiHtml(k, E) {
    var value = k.kind === 'money' ? E.formatMoney(k.value / 100) + ' ؋' : String(k.value);
    var cls = k.trend === 'flat' ? 'flat' : (k.good ? 'good' : 'bad');
    var arrow = k.trend === 'up' ? '▲' : (k.trend === 'down' ? '▼' : '–');
    var delta = k.deltaText ? '<span class="d ' + cls + '"><span aria-hidden="true">' + arrow + '</span> ' + esc(k.deltaText) + '</span>' : '<span></span>';
    return '<button type="button" class="rf-kpi" data-kpi="' + esc(k.id) + '"' + presetAttrs(k.target) +
      ' aria-label="' + esc([k.title, value, k.deltaText, k.period].filter(Boolean).join('، ')) + ' — نمایشِ فهرست">' +
      '<span class="t">' + esc(k.title) + '</span><span class="v">' + esc(value) + '</span>' +
      '<span class="f">' + delta + '<span>' + esc(k.period) + '</span></span></button>';
  }

  function pillHtml(p) {
    return '<button type="button" class="rf-pill rf-' + esc(p.tone) + '" data-pill="' + esc(p.id) + '"' + presetAttrs(p.target) +
      ' aria-label="وضعیت: ' + esc(p.text) + ' — نمایشِ فهرست"><span class="dot" aria-hidden="true"></span>' + esc(p.text) + '</button>';
  }

  function openPreset(el) {
    var E = env();
    state.tab = el.getAttribute('data-tab') || 'today';
    state.span = el.getAttribute('data-span') || 'today';
    if (el.getAttribute('data-dim')) state.dim = el.getAttribute('data-dim');
    state.sort = null;
    if (E && E.goTo) E.goTo('reports');
  }

  function renderHome() {
    var el = document.getElementById('dash-fresh');
    if (!el) return;
    var E = env(), B = board();
    if (!enabled() || !E || !B || !core()) { el.hidden = true; el.innerHTML = ''; return; }
    var home = homeOf(E);
    el.innerHTML = '<div class="rf-kpis">' + home.kpis.map(function (k) { return kpiHtml(k, E); }).join('') + '</div>' +
      '<div class="rf-pills" role="list" aria-label="وضعیتِ دکان">' + home.pills.map(pillHtml).join('') + '</div>';
    el.hidden = false;
    if (!el.__rf) {
      el.__rf = true;
      el.addEventListener('click', function (e) {
        var b = e.target.closest('[data-tab]');
        if (b && el.contains(b)) openPreset(b);
      });
    }
  }

  /* ---------------- صفحهٔ گزارش ---------------- */

  /** جدول‌های یک زبانه — همان `ReportFreshModel.view`ِ Kotlin */
  function compute(E) {
    var C = core(), B = board(), L = C.ledger(E.ledger), ck = clock(), co = cost();
    var r = B.range(state.span, E.today), dim = B.DIMS.indexOf(state.dim) >= 0 ? state.dim : 'product';
    var tab = state.tab;
    if (tab === 'today') {
      return { tables: C.tables('today', C.today(L, { date: E.today, clock: ck, costing: co.costing, costApproved: co.costApproved })) };
    }
    if (tab === 'sales' || tab === 'profit') {
      var s = C.sales(L, { from: r.from, to: r.to, clock: ck, costing: co.costing, costApproved: co.costApproved });
      var t = C.tables('sales', s).filter(function (x) { return x.id === 'sales.' + dim; })[0];
      if (tab === 'sales') return { tables: [t], sales: s };
      var keys = ['name', 'netSales', 'cogs', 'grossProfit', 'marginBp'];
      var idx = keys.map(function (k) { return t.columns.map(function (c) { return c.key; }).indexOf(k); }).filter(function (i) { return i >= 0; });
      var pick = function (row) { return idx.map(function (i) { return row[i]; }); };
      return { sales: s, tables: [{ id: 'profit', title: costLabel('سود و حاشیه به تفکیک ' + B.DIM_LABELS[dim], s.costing),
        columns: idx.map(function (i) { return t.columns[i]; }), rows: t.rows.map(pick), total: t.total ? pick(t.total) : null }] };
    }
    if (tab === 'debts') {
      var d = C.debts(L, { asOf: E.today, clock: ck });
      return { tables: C.tables('debts', d), debts: d };
    }
    var f = C.fraud(L, { from: r.from, to: r.to, clock: ck });
    return { tables: C.tables('fraud', f), fraud: f };
  }

  function cardKeys(id) {
    if (id === 'today') return ['netSales', 'invoices', 'grossProfit', 'avgTicket'];
    if (id === 'profit') return ['netSales', 'cogs', 'grossProfit', 'marginBp'];
    if (id.indexOf('sales.') === 0) return ['netSales', 'invoices', 'qty', 'returns'];
    if (id.indexOf('debts.') === 0) return ['balance', 'credit', 'oldestDays'];
    if (id === 'fraud') return ['discount', 'returnCount', 'voidCount', 'lineDeleteCount', 'noSaleCount'];
    return [];
  }

  function seg(name, items, current, label) {
    return '<div class="rf-seg" role="group" aria-label="' + esc(label) + '">' + items.map(function (it) {
      return '<button type="button" data-' + name + '="' + esc(it[0]) + '" aria-pressed="' + (it[0] === current) + '">' + esc(it[1]) + '</button>';
    }).join('') + '</div>';
  }

  function stat(title, value, period) { return statHtml(esc(title), value, period); }
  function statHtml(title, value, period) {
    return '<div class="rf-stat"><span class="t">' + title + '</span><span class="v">' + esc(value) + '</span><span class="p">' + esc(period) + '</span></div>';
  }

  function periodOf(from, to, E) {
    if (from === '0000-01-01') return 'همهٔ تاریخ‌ها';
    return from === to ? E.formatDate(from) : E.formatDate(from) + ' تا ' + E.formatDate(to);
  }

  function summary(v, E) {
    var af = function (x) { return E.formatMoney(x / 100) + ' ؋'; };
    if (state.tab === 'today') {
      var home = homeOf(E);
      return '<div class="rf-kpis">' + home.kpis.map(function (k) { return kpiHtml(k, E); }).join('') + '</div>';
    }
    if (v.sales && state.tab === 'sales') {
      var t = v.sales.totals, p = periodOf(v.sales.from, v.sales.to, E);
      return '<div class="rf-stats">' + stat('فروش خالص', af(t.netSales), p) + stat('فاکتور', String(t.invoices), p) +
        stat('تخفیف', af(t.discount), p) + stat('برگشتی', af(t.returns), p) + stat('میانگین فاکتور', af(t.avgTicket), p) + '</div>';
    }
    if (v.sales) {
      //  RPT-03: روشِ بها همان که هسته با آن حساب کرد (`costing`)، نه حدسِ صفحه — همان کارت‌های `ReportFresh` ِ برنامه
      var u = v.sales.totals, q = periodOf(v.sales.from, v.sales.to, E), m = v.sales.costing, CP = board().COST;
      return '<div class="rf-stats">' + stat('فروش خالص', af(u.netSales), q) + statHtml(labelHtml('بهای تمام‌شده', m), af(u.cogs), q) +
        statHtml(labelHtml('سود ناخالص', m), af(u.grossProfit), q) + statHtml(labelHtml('حاشیه', m), u.marginBp === null ? '—' : percent(u.marginBp), q) + '</div>' +
        (CP ? '<div class="rf-bar"><span class="rf-label">روش بها</span>' +
          seg('cost', CP.METHODS.map(function (x) { return [x.id, x.label]; }), m, 'روش بها') + '</div>' : '') +
        '<p class="rf-note">' + (u.profitApproximate
          ? '<span class="rf-pill rf-warning" role="status"><span class="dot" aria-hidden="true"></span>سود تقریبی</span> '
          : '<span class="rf-pill rf-success" role="status"><span class="dot" aria-hidden="true"></span>سود قطعی</span> ') +
        explainHtml(m) +
        (u.costEstimatedLines > 0 ? ' <span>' + esc(u.costEstimatedLines + ' قلم بی بهای خرید است.') + '</span>' : '') + '</p>';
    }
    if (v.debts) {
      var c = v.debts.customers, edge = v.debts.buckets[v.debts.buckets.length - 1];
      var late = c.rows.filter(function (x) { return x.buckets[x.buckets.length - 1] > 0; }).length;
      return '<div class="rf-stats">' + stat('طلب از مشتریان', af(c.totals.balance), 'تا امروز') +
        stat('مشتریانِ بدهکار', String(c.rows.filter(function (x) { return x.balance > 0; }).length), 'تا امروز') +
        stat('قرضِ بیش از ' + edge + ' روز', String(late), 'مشتری') + stat('بدهی به تأمین‌کنندگان', af(v.debts.suppliers.totals.balance), 'تا امروز') + '</div>';
    }
    if (v.fraud) {
      var f = v.fraud.totals, w = periodOf(v.fraud.from, v.fraud.to, E);
      return '<div class="rf-stats">' + stat('لغوِ فاکتور', String(f.voidCount), w) + stat('برگشتی', String(f.returnCount), w) +
        stat('حذفِ ردیف', String(f.lineDeleteCount), w) + stat('کشو بی فروش', String(f.noSaleCount), w) + stat('نیازمندِ تأیید', String(f.overrideCount), w) + '</div>' +
        alertsHtml(v.fraud, E);
    }
    return '';
  }

  /**
   *  RSK-10: هشدارِ رفتارِ غیرعادیِ صندوقدار در زبانهٔ ضدتقلب — همان `alertsOf` ِ تابلو که نشانِ خانه را می‌سازد
   *  (دوقلوی `CashierAlertBox` ِ برنامه، `StrRisk`). بی هشدار ⇒ هیچ کادری؛ بی اجازهٔ دیدنِ پول ⇒ هیچ (همان نشانِ خانه).
   */
  var ALERT_LABELS = { void: 'لغوِ فاکتور', discount: 'تخفیف', no_sale: 'کشو بی فروش' };
  function alertsHtml(fraud, E) {
    if (E.money === false) return '';
    var a = board().alertsOf(fraud);
    if (!a.length) return '';
    return '<div class="rf-alerts" role="status"><p class="rf-alerts-title">' + esc(a.length + ' هشدارِ رفتارِ غیرعادیِ صندوقدار') + '</p><ul>' +
      a.map(function (x) {
        return '<li>' + esc(x.key + ': ' + x.count + ' ' + (ALERT_LABELS[x.kind] || x.kind) + ' در ' + x.invoices + ' فاکتور — بقیه ' + x.peerCount + ' در ' + x.peerInvoices) + '</li>';
      }).join('') + '</ul><p class="rf-note">سنجش با بقیهٔ کاربرانِ همین بازه: دستِ کم ۳ بار و دو برابرِ نرخِ آن‌ها</p></div>';
  }

  function tableHtml(t, E) {
    var s = state.sort && state.sort.id === t.id ? state.sort : null;
    var rows = s ? board().sortRows(t.rows, s.col, s.desc) : t.rows;
    var num = function (k) { return k !== 'text' && k !== 'date'; };
    var head = t.columns.map(function (c, i) {
      var sort = s && s.col === i ? (s.desc ? 'descending' : 'ascending') : 'none';
      return '<th scope="col" aria-sort="' + sort + '" class="' + (num(c.kind) ? 'n' : '') + '"><button type="button" data-sort="' + i +
        '" data-table="' + esc(t.id) + '">' + esc(c.label) + (sort === 'none' ? '' : (sort === 'descending' ? ' ▼' : ' ▲')) + '</button></th>';
    }).join('');
    var body = rows.length ? rows.map(function (r) {
      return '<tr>' + r.map(function (v, i) { return '<td class="' + (num(t.columns[i].kind) ? 'n' : '') + '">' + esc(fmt(v, t.columns[i].kind, E)) + '</td>'; }).join('') + '</tr>';
    }).join('') : '<tr><td class="rf-empty" colspan="' + t.columns.length + '">در این بازه چیزی نیست.</td></tr>';
    var foot = t.total ? '<tfoot><tr>' + t.total.map(function (v, i) {
      return '<td class="' + (num(t.columns[i].kind) ? 'n' : '') + '">' + (v === null ? '' : esc(fmt(v, t.columns[i].kind, E))) + '</td>';
    }).join('') + '</tr></tfoot>' : '';
    return '<div class="rf-table-wrap" tabindex="0" role="region" aria-label="' + esc(t.title) + '"><table class="rf-table" data-table-id="' + esc(t.id) + '">' +
      '<caption class="rf-sr">' + esc(t.title) + '</caption><thead><tr>' + head + '</tr></thead><tbody>' + body + '</tbody>' + foot + '</table></div>';
  }

  function cardsHtml(t, E) {
    if (!t.rows.length) return '<p class="rf-empty">در این بازه چیزی نیست.</p>';
    var keys = cardKeys(t.id).map(function (k) { return t.columns.map(function (c) { return c.key; }).indexOf(k); }).filter(function (i) { return i >= 0; });
    return '<div class="rf-cards" data-table-id="' + esc(t.id) + '">' + t.rows.map(function (r) {
      return '<div class="rf-card"><h4>' + esc(fmt(r[0], t.columns[0].kind, E)) + '</h4>' + keys.map(function (i) {
        return '<div class="l"><span>' + esc(t.columns[i].label) + '</span><b>' + esc(fmt(r[i], t.columns[i].kind, E)) + '</b></div>';
      }).join('') + '</div>';
    }).join('') + '</div>';
  }

  /** برگهٔ خروجی — همان `ReportFreshModel.exportRows`: عددِ `plain()` (رقمِ لاتین) تا اکسل حسابش کند */
  function exportRows(tables) {
    var C = core(), out = [];
    tables.forEach(function (t, i) {
      if (i > 0) out.push(['']);
      var p = C.plain(t);
      if (tables.length > 1) out.push([p.title]);
      out.push(p.head);
      p.rows.forEach(function (r) { out.push(r); });
      if (p.total) out.push(p.total);
    });
    return out;
  }

  var last = null;

  function renderReports() {
    var el = document.getElementById('rep-fresh');
    var E = env(), B = board();
    if (!el || !E || !B || !core()) return;
    var tabs = tabsFor(E);
    if (!tabs.some(function (t) { return t.id === state.tab; })) state.tab = tabs[0].id;
    var def = tabs.filter(function (t) { return t.id === state.tab; })[0];
    if (B.SPANS.indexOf(state.span) < 0) state.span = 'month';
    var v = compute(E);
    last = { v: v, label: def.label, name: ('گزارش-' + def.label + '-' + E.today).replace(/ /g, '-') };  // داده: نامِ پروندهٔ خروجی
    var mode = viewMode();
    var html = '<div class="rf-head"><div><h2>گزارش‌ها</h2><p>از هستهٔ مشترکِ گزارش (همان عددهای برنامهٔ نصبی).</p></div>' +
      '<div class="rf-actions"><button type="button" class="btn btn-secondary btn-sm" data-act="print">چاپ</button>' +
      '<button type="button" class="btn btn-secondary btn-sm" data-act="pdf">خروجی PDF</button>' +
      '<button type="button" class="btn btn-secondary btn-sm" data-act="csv">CSV</button>' +
      '<button type="button" class="btn btn-primary btn-sm" data-act="xlsx">خروجی اکسل</button></div></div>' +
      '<div class="rf-bar">' + seg('rtab', tabs.map(function (t) { return [t.id, t.label]; }), state.tab, 'نوعِ گزارش') +
      (def.ranged ? seg('span', B.SPANS.map(function (s) { return [s, B.SPAN_LABELS[s]]; }), state.span, 'بازه') : '') +
      '<span class="sp"></span>' + (phone() ? '' : seg('vm', [['card', 'نمای کارتی'], ['table', 'نمای جدولی']], mode, 'نمایش')) + '</div>' +
      (state.tab === 'sales' || state.tab === 'profit'
        ? '<div class="rf-bar">' + seg('dim', B.DIMS.map(function (d) { return [d, B.DIM_LABELS[d]]; }), state.dim, 'تفکیک') + '</div>' : '') +
      summary(v, E) +
      v.tables.map(function (t) {
        return (v.tables.length > 1 ? '<h3 class="rf-title">' + esc(t.title) + '</h3>' : '') + (mode === 'table' ? tableHtml(t, E) : cardsHtml(t, E));
      }).join('');
    el.innerHTML = html;
    if (!el.__rf) {
      el.__rf = true;
      el.addEventListener('click', onClick);
    }
  }

  function onClick(e) {
    var b = e.target.closest('button');
    if (!b) return;
    var E = env();
    if (b.hasAttribute('data-rtab')) { state.tab = b.getAttribute('data-rtab'); state.sort = null; }
    else if (b.hasAttribute('data-span')) state.span = b.getAttribute('data-span');
    else if (b.hasAttribute('data-dim')) { state.dim = b.getAttribute('data-dim'); state.sort = null; }
    else if (b.hasAttribute('data-vm')) put(VM_KEY, b.getAttribute('data-vm'));
    else if (b.hasAttribute('data-cost')) put(COST_KEY, b.getAttribute('data-cost'));
    else if (b.hasAttribute('data-sort')) {
      var col = +b.getAttribute('data-sort'), id = b.getAttribute('data-table');
      var t = last && last.v.tables.filter(function (x) { return x.id === id; })[0];
      var textCol = t && t.columns[col].kind === 'text';
      state.sort = state.sort && state.sort.id === id && state.sort.col === col
        ? { id: id, col: col, desc: !state.sort.desc } : { id: id, col: col, desc: !textCol };
    } else if (b.hasAttribute('data-kpi') || b.hasAttribute('data-pill')) { openPreset(b); }
    else if (b.hasAttribute('data-act') && b.getAttribute('data-act') !== 'print' && last && E) {
      var rows = exportRows(last.v.tables);
      if (rows.length <= 1) { if (E.showToast) E.showToast('در این گزارش داده‌ای برای خروجی وجود ندارد', 'warning'); return; }
      var act = b.getAttribute('data-act');
      if (act === 'csv') E.downloadCsv(last.name + '.csv', rows);
      else if (act === 'xlsx') excel(rows, last.name, E);
      else if (act === 'pdf') pdf(rows, last, E, b);
      return;
    } else if (b.getAttribute('data-act') === 'print' && last && E) { E.printRows(exportRows(last.v.tables), last.name); return; }
    else return;
    //  بعد از کشیدنِ دوباره، فوکوس روی همان دکمه می‌ماند (کار با کیبورد)
    var attr = ['data-rtab', 'data-span', 'data-dim', 'data-vm', 'data-cost', 'data-sort'].filter(function (a) { return b.hasAttribute(a); })[0];
    var sel = attr ? 'button[' + attr + '="' + b.getAttribute(attr) + '"]' +
      (attr === 'data-sort' ? '[data-table="' + b.getAttribute('data-table') + '"]' : '') : null;
    renderReports();
    var again = sel ? document.querySelector('#rep-fresh ' + sel) : null;
    if (again) again.focus();
  }

  /* ---------------- RPT-11: اکسل و PDF ---------------- */

  function saveBlob(blob, filename) {
    var url = URL.createObjectURL(blob), a = document.createElement('a');
    a.href = url; a.download = filename;
    document.body.appendChild(a); a.click(); a.remove();
    setTimeout(function () { URL.revokeObjectURL(url); }, 10000);
  }
  function failed(E) { if (E.showToast) E.showToast('فایل ساخته نشد', 'danger'); }

  /** همان برگهٔ CSV ⇒ ‎.xlsx‎ ِ راست‌به‌چپ، سرستونِ ثابت و عددِ عدد (`Xlsx.of` ِ برنامه) */
  function excel(rows, name, E) {
    var X = window.TohidOwnerExport;
    if (!X || typeof X.report !== 'function') { failed(E); return; }
    X.report(name, rows).then(function (u8) {
      saveBlob(new Blob([u8], { type: X.XLSX_MIME }), name + '.xlsx');
    }, function () { failed(E); });
  }

  var pdfLoad = null;
  /** `report-pdf.js` فقط با نخستین «PDF» (هر بارِ صفحه یک بار)؛ نبودش ⇒ پیامِ خطا، نه صفحهٔ خراب */
  function pdfLib() {
    if (window.TohidReportPdf) return Promise.resolve(window.TohidReportPdf);
    if (!pdfLoad) {
      pdfLoad = new Promise(function (ok, no) {
        var sc = document.createElement('script');
        sc.src = PDF_SRC;
        sc.onload = function () { if (window.TohidReportPdf) ok(window.TohidReportPdf); else no(new Error('pdf')); };
        sc.onerror = function () { pdfLoad = null; no(new Error('pdf')); };
        document.head.appendChild(sc);
      });
    }
    return pdfLoad;
  }

  /** PDF ِ A4 ِ سیاه‌وسفید با قلمِ همین صفحه (`report-pdf.js`)؛ دکمه تا پایانِ ساختن خاموش */
  function pdf(rows, at, E, btn) {
    btn.disabled = true;
    btn.setAttribute('aria-busy', 'true');
    var done = function () { btn.disabled = false; btn.removeAttribute('aria-busy'); };
    var fonts = document.fonts && document.fonts.ready ? document.fonts.ready : Promise.resolve();
    pdfLib().then(function (P) {
      return fonts.then(function () {
        var font = window.getComputedStyle(document.body).fontFamily;
        try {
          saveBlob(P.blob(rows, { title: 'گزارش‌ها — ' + at.label, sub: at.name, font: font }), at.name + '.pdf'); // داده: عنوانِ درونِ پرونده، همان برگهٔ CSV
        } catch (e) {
          if (e && e.code === 'too_many_pages') {
            if (E.showToast) E.showToast('PDF ساخته نشد: گزارش بیش از ' + e.max + ' صفحه است. بازهٔ کوتاه‌تری بگیرید یا خروجیِ Excel بگیرید.', 'danger');
          } else failed(E);
        }
      });
    }).then(done, function () { done(); failed(E); });
  }

  /** صفحهٔ گزارش: روشن ⇒ گزارشِ تازه؛ خاموش ⇒ همان گزارشِ امروز */
  function syncReports() {
    var on = enabled();
    var sw = document.getElementById('rep-fresh-toggle');
    var legacy = document.getElementById('rep-legacy');
    var fresh = document.getElementById('rep-fresh');
    if (sw) {
      sw.checked = on;
      if (!sw.__rf) { sw.__rf = true; sw.addEventListener('change', function () { setEnabled(sw.checked); }); }
    }
    if (legacy) legacy.hidden = on;
    if (fresh) {
      fresh.hidden = !on;
      if (on) renderReports(); else fresh.innerHTML = '';
    }
  }

  function setEnabled(on) {
    put(KEY, on ? '1' : '0');
    syncReports();
    renderHome();
  }

  window.TohidReportUi = {
    enabled: enabled,
    setEnabled: setEnabled,
    renderHome: renderHome,
    syncReports: syncReports,
    renderReports: renderReports,
    state: state,
  };
})();
