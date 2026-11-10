/* ==========================================================
   فروشگاه من | نقشهٔ حرارتیِ «ساعت در برابر روزِ هفته» (RPT-08) — هستهٔ خالصِ وب
   ----------------------------------------------------------
   ‹`build`› دوقلوی `core/report/SalesHeatmap.build` (Kotlin، commonMain) است: همان ۷ × ۲۴، همان عدد، با بردارِ
   مشترکِ `core/test-vectors/sales-heatmap-v1.json` (JS: `server/test/report-heatmap-vectors.test.js`، Kotlin:
   `core/src/jvmTest/.../report/SalesHeatmapVectorsTest.kt`). یکی را بی دیگری و بردار عوض نکن.
     • ساعتِ دیواری با جابه‌جاییِ منطقهٔ زمانیِ دکان (`offsetMinutes`، بیرون از ±۱۴ ساعت ⇒ ۰)؛
     • ردیفِ روز با همان پایانِ روزِ کاری (`dayStartMinutes`، همان `ShopDay`؛ بیرون از شبانه‌روز ⇒ نیمه‌شب):
       فروشِ ۱:۳۰ ِ دکانی که روزش ساعتِ ۲ تمام می‌شود ردیفِ دیروز و ستونِ ساعتِ ۱ را می‌گیرد؛
     • ردیف‌ها از آغازِ هفتهٔ دکان (`weekStart` به شمارِ ISO، بیرون از ۱..۷ ⇒ ۶ = شنبه)؛
     • شمار فقط فروش (`isSale`)، جمعِ مبلغ خالص — برگشتی با مبلغِ منفی در خانهٔ **خودش**؛ شدت (`level`) با عددِ صحیح.

   ‹`events`› و ‹`tables`› همتای `HeatmapModel.events`/`tables` ِ برنامه‌اند (زبانهٔ «ساعت و روز» ِ گزارشِ تازه):
   فاکتورِ لغونشدهٔ بازه با لحظهٔ ثبت و مبلغِ نهایی، برگشتِ بازه با مبلغِ منفی (برگشتِ فاکتورِ لغوشده بیرون)؛ بازه با تاریخِ
   سند؛ سندِ بی لحظه جایی در نقشه ندارد. ورودی همان دفترِ `report-core.ledger` (پول به واحدِ کوچک). ES5.
   ========================================================== */
(function (factory) {
  'use strict';
  var api = factory();
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  if (typeof window !== 'undefined') window.TohidSalesHeatmap = api;
  else if (typeof globalThis !== 'undefined') globalThis.TohidSalesHeatmap = api;
})(function () {
  'use strict';

  var DAYS = 7, HOURS = 24, MINUTE_MS = 60000, DAY_MS = 86400000, MAX_OFFSET_MIN = 14 * 60;

  function floorDiv(a, b) { var r = a % b; return (a - r) / b - (r !== 0 && (r < 0) !== (b < 0) ? 1 : 0); }
  function floorMod(a, b) { return a - floorDiv(a, b) * b; }
  function isInt(n) { return typeof n === 'number' && Math.floor(n) === n && Math.abs(n) <= 9007199254740991; }

  /** روزِ هفتهٔ ISO ِ «روزِ چندم از ۱۹۷۰-۰۱-۰۱» (آن روز پنجشنبه = ۴ بود) */
  function weekdayOf(epochDay) { return floorMod(epochDay + 3, DAYS) + 1; }

  function cell(count, total) { return { count: count, totalMinor: total }; }
  function sum(list) {
    var c = 0, t = 0;
    for (var i = 0; i < list.length; i++) { c += list[i].count; t += list[i].totalMinor; }
    return cell(c, t);
  }

  /** نقشه از رویدادهای {atMs, amountMinor, isSale}؛ تنظیمِ نامعتبر ⇒ پیش‌فرضِ امن (بی جابه‌جایی، نیمه‌شب، شنبه) */
  function build(sales, offsetMinutes, dayStartMinutes, weekStart) {
    var off = isInt(offsetMinutes) && offsetMinutes >= -MAX_OFFSET_MIN && offsetMinutes <= MAX_OFFSET_MIN ? offsetMinutes : 0;
    var start = isInt(dayStartMinutes) && dayStartMinutes >= 0 && dayStartMinutes < 24 * 60 ? dayStartMinutes : 0;
    var ws = isInt(weekStart) && weekStart >= 1 && weekStart <= 7 ? weekStart : 6;
    var counts = [], totals = [], r, h, i;
    for (r = 0; r < DAYS; r++) { counts.push([]); totals.push([]); for (h = 0; h < HOURS; h++) { counts[r].push(0); totals[r].push(0); } }
    for (i = 0; i < (sales || []).length; i++) {
      var s = sales[i], local = s.atMs + off * MINUTE_MS;
      var day = floorDiv(local, DAY_MS), minute = Math.floor(floorMod(local, DAY_MS) / MINUTE_MS);
      if (minute < start) day -= 1;
      var row = ((weekdayOf(day) - ws) % DAYS + DAYS) % DAYS, hour = Math.floor(minute / 60);
      if (s.isSale !== false) counts[row][hour] += 1;
      totals[row][hour] += s.amountMinor;
    }
    var weekdays = [], cells = [];
    for (r = 0; r < DAYS; r++) {
      weekdays.push((ws - 1 + r) % DAYS + 1);
      var line = [];
      for (h = 0; h < HOURS; h++) line.push(cell(counts[r][h], totals[r][h] === 0 ? 0 : totals[r][h]));
      cells.push(line);
    }
    return finish(weekdays, cells);
  }

  function finish(weekdays, cells) {
    var max = 0, r, h;
    for (r = 0; r < DAYS; r++) for (h = 0; h < HOURS; h++) if (cells[r][h].count > max) max = cells[r][h].count;
    var peak = null;
    for (r = 0; r < DAYS && max > 0 && !peak; r++) for (h = 0; h < HOURS; h++) if (cells[r][h].count === max) { peak = [r, h]; break; }
    var rowTotals = cells.map(sum), hourTotals = [];
    for (h = 0; h < HOURS; h++) hourTotals.push(sum(cells.map(function (line) { return line[h]; })));
    return { weekdays: weekdays, cells: cells, maxCount: max, peak: peak, rowTotals: rowTotals, hourTotals: hourTotals, total: sum(rowTotals) };
  }

  /** خانهٔ (ردیف، ساعت)؛ بیرون از نقشه ⇒ خانهٔ خالی */
  function cellOf(m, row, hour) { return (m.cells[row] && m.cells[row][hour]) || cell(0, 0); }

  /** شدتِ خانه از ۰ تا `levels`: ۰ فقط برای خانهٔ بی فروش؛ وگرنه ⌈count × levels / max⌉ */
  function level(m, row, hour, levels) {
    var lv = levels === undefined ? 4 : levels, c = cellOf(m, row, hour).count;
    if (c <= 0 || m.maxCount <= 0 || lv <= 0) return 0;
    return floorDiv(c * lv + m.maxCount - 1, m.maxCount);
  }

  /* ---------------- همتای HeatmapModel ِ برنامه ---------------- */

  /** روزِ هفتهٔ ISO (۱ دوشنبه … ۷ یکشنبه) */
  var DAY_NAMES = ['دوشنبه', 'سه‌شنبه', 'چهارشنبه', 'پنجشنبه', 'جمعه', 'شنبه', 'یکشنبه'];
  function dayName(isoWeekday) { return DAY_NAMES[isoWeekday - 1] || ''; }
  /** «08:00» با رقمِ لاتین، مثلِ ستونِ «ساعت» ِ گزارش‌ها */
  function hourLabel(h) { return (h < 10 ? '0' : '') + h + ':00'; }

  /** رویدادهای بازهٔ [from, to] از دفترِ `report-core.ledger` */
  function events(L, from, to) {
    var cancelled = {}, out = [];
    function inRange(d) { return d >= from && d <= to; }
    L.sales.forEach(function (s) { if (s.cancelled) cancelled[s.id] = true; });
    L.sales.forEach(function (s) {
      if (!s.cancelled && s.createdAt > 0 && inRange(s.date)) out.push({ atMs: s.createdAt, amountMinor: s.finalTotal, isSale: true });
    });
    L.returns.forEach(function (r) {
      if (r.createdAt > 0 && inRange(r.date) && !cancelled[r.saleId]) out.push({ atMs: r.createdAt, amountMinor: -r.amount, isSale: false });
    });
    return out;
  }

  /** سه جدول (نقشهٔ شمار، هر ساعت، هر روز) — همان `HeatmapModel.tables`، همان راهِ اکسل، چاپ و PDF */
  function tables(m) {
    var hours = [], h;
    for (h = 0; h < HOURS; h++) hours.push(h);
    var line = function (name, c) { return [name, c.count, c.totalMinor]; };
    var grid = {
      id: 'heatmap.grid', title: 'شمارِ فروش: روز × ساعت',
      columns: [{ key: 'day', label: 'روز', kind: 'text' }].concat(hours.map(function (x) { return { key: 'h' + x, label: hourLabel(x), kind: 'count' }; }),
        [{ key: 'total', label: 'جمع', kind: 'count' }]),
      rows: m.weekdays.map(function (wd, r) {
        return [dayName(wd)].concat(hours.map(function (x) { return m.cells[r][x].count; }), [m.rowTotals[r].count]);
      }),
      total: ['جمع'].concat(hours.map(function (x) { return m.hourTotals[x].count; }), [m.total.count]),
    };
    var cols = function (first) {
      return [{ key: 'name', label: first, kind: 'text' }, { key: 'count', label: 'شمارِ فروش', kind: 'count' }, { key: 'net', label: 'خالصِ مبلغ', kind: 'money' }];
    };
    return [
      grid,
      { id: 'heatmap.hours', title: 'به تفکیکِ ساعت', columns: cols('ساعت'),
        rows: hours.map(function (x) { return line(hourLabel(x), m.hourTotals[x]); }), total: line('جمع', m.total) },
      { id: 'heatmap.days', title: 'به تفکیکِ روزِ هفته', columns: cols('روز'),
        rows: m.weekdays.map(function (wd, r) { return line(dayName(wd), m.rowTotals[r]); }), total: line('جمع', m.total) },
    ];
  }

  return {
    VECTORS_FORMAT: 'sales-heatmap-v1',
    DAYS: DAYS,
    HOURS: HOURS,
    build: build,
    cell: cellOf,
    level: level,
    weekdayOf: weekdayOf,
    dayName: dayName,
    hourLabel: hourLabel,
    events: events,
    tables: tables,
  };
});
