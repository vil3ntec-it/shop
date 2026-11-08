/* ==========================================================
   فروشگاه من | فروشِ پیش از شمارش که پس از ثبتِ شمارش می‌رسد (count-absorb)
   ----------------------------------------------------------
   INV-10 (§۶.۵، S20). همزادِ Kotlinِ `core/stock/CountAbsorb.kt`؛ هر دو یک
   فایلِ بردارِ مرجع را می‌گذرانند: `license/test-vectors/count-absorb-v1.json`.
   `server/src/lib/count-absorb-core.js` کپیِ بایت‌به‌بایتِ همین است (آزمونِ
   `count-absorb.test.js`)، چون imageِ سرور فقط پوشهٔ `server/` را دارد.

   ردیفِ شمارش دانستهٔ شمارنده را می‌برد (`seenMilli`: جمعِ حرکت‌هایی از همان
   کالا که شمارنده هنگامِ ثبت داشت و در پنجره بودند). هر گره همان پنجره را روی
   حرکت‌هایی که امروز دارد می‌شمارد؛ تفاوت = حرکت‌های پیش از شمارشی که شمارنده
   ندیده بود ⇒ «جذب‌شده»، و موجودی = جمعِ دفتر − همان. قاعدهٔ دقیق سرِ پروندهٔ Kotlin.
   مقدار هزارم (عددِ صحیحِ امن)؛ هیچ چیزی از مرورگر نمی‌خواهد.
   ========================================================== */
(function (factory) {
  'use strict';
  var api = factory();
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  if (typeof window !== 'undefined') window.TohidCountAbsorb = api;
  else if (typeof globalThis !== 'undefined') globalThis.TohidCountAbsorb = api;
})(function () {
  'use strict';

  var VECTORS_FORMAT = 'count-absorb-v1';
  var WINDOW_MS = 30 * 86400000;
  var SKEW_MS = 5 * 60000;
  var TRANSFER_TYPES = ['transfer_out', 'transfer_in', 'transfer'];

  function isNum(x) { return typeof x === 'number' && isFinite(x); }
  function given(x) { return x !== null && x !== undefined; }

  function plus(a, b) {
    var r = a + b;
    if (!Number.isSafeInteger(r)) throw new RangeError('count-absorb: overflow');
    return r;
  }

  function inWindow(e, productId, countedAt, createdAt) {
    return e.productId === productId && TRANSFER_TYPES.indexOf(e.type || '') < 0 && isNum(e.at) && e.at > 0 &&
      e.at > countedAt - WINDOW_MS && e.at <= countedAt &&
      (!given(e.correctedAt) || e.correctedAt < createdAt);
  }

  /** آیا حرکتِ e در پنجرهٔ ردیفِ l است */
  function member(e, l) {
    return (given(e.refId) ? e.refId : null) !== l.id && inWindow(e, l.productId, l.countedAt, l.createdAt);
  }

  /** آیا ردیفِ l جذب می‌کند */
  function trusted(l) {
    return given(l.seenMilli) && isNum(l.countedAt) && isNum(l.createdAt) && l.countedAt > 0 && l.createdAt > 0 &&
      l.countedAt <= l.createdAt && l.countedAt > l.createdAt - WINDOW_MS &&
      !given(l.correctedAt) &&
      (!given(l.serverAt) || l.countedAt <= l.serverAt + SKEW_MS);
  }

  function unique(events) {
    var ids = Object.create(null);
    var out = [];
    for (var i = 0; i < events.length; i++) {
      var id = String(events[i].id);
      if (ids[id]) continue;
      ids[id] = true;
      out.push(events[i]);
    }
    return out;
  }

  /** دستگاهِ شمارنده هنگامِ ثبت: جمعِ حرکت‌هایی از productId که همین حالا دارد و در پنجره‌اند */
  function seen(events, productId, countedAt, createdAt) {
    var s = 0;
    var evs = unique(events);
    for (var i = 0; i < evs.length; i++) if (inWindow(evs[i], productId, countedAt, createdAt)) s = plus(s, evs[i].qtyMilli);
    return s;
  }

  /** ردیفِ حاکمِ هر کالا: دیرترین لحظه، برابر ⇒ شناسهٔ بزرگ‌تر */
  function governing(lines) {
    var out = new Map();
    for (var i = 0; i < lines.length; i++) {
      var l = lines[i];
      if (!l.productId) continue;
      var cur = out.get(l.productId);
      if (!cur || l.countedAt > cur.countedAt || (l.countedAt === cur.countedAt && l.id > cur.id)) out.set(l.productId, l);
    }
    return out;
  }

  /** جذب‌شدهٔ هر کالا (فقط ناصفر): Map<productId, {productId, lineId, gapMilli}>؛ موجودی = جمعِ دفتر − gapMilli */
  function gaps(lines, events) {
    var heads = new Map();
    governing(lines).forEach(function (g, pid) { if (trusted(g)) heads.set(pid, g); });
    var out = new Map();
    if (!heads.size) return out;
    //  یک بار گروه‌بندی: فقط حرکت‌های کالاهای دارای ردیفِ معتبر
    var byProduct = new Map();
    var evs = unique(events);
    for (var i = 0; i < evs.length; i++) {
      if (!heads.has(evs[i].productId)) continue;
      if (!byProduct.has(evs[i].productId)) byProduct.set(evs[i].productId, []);
      byProduct.get(evs[i].productId).push(evs[i]);
    }
    heads.forEach(function (g, pid) {
      var list = byProduct.get(pid) || [];
      var sum = 0;
      for (var j = 0; j < list.length; j++) if (member(list[j], g)) sum = plus(sum, list[j].qtyMilli);
      var gap = plus(sum, -g.seenMilli);
      if (gap !== 0) out.set(pid, { productId: pid, lineId: g.id, gapMilli: gap });
    });
    return out;
  }

  return {
    VECTORS_FORMAT: VECTORS_FORMAT, WINDOW_MS: WINDOW_MS, SKEW_MS: SKEW_MS, TRANSFER_TYPES: TRANSFER_TYPES,
    member: member, trusted: trusted, seen: seen, governing: governing, gaps: gaps,
  };
});
