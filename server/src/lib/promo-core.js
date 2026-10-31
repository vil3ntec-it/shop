/* ==========================================================
   فروشگاه من | هستهٔ تخفیف و قاعده‌های قیمت — PRM-01..09، 11، 12، 17
   ----------------------------------------------------------
   هم‌زادِ `core/src/commonMain/.../core/promo/PromoCore.kt`. هر دو با یک
   فایلِ بردارِ مرجع (`core/test-vectors/promo-v1.json`) سنجیده می‌شوند؛
   یکی را عوض کنی، دیگری هم.

   خالص است: نه DOM، نه ساعت، نه ذخیره، نه شبکه. هر مبلغ عددِ صحیح به
   واحدِ کوچک (پول = یک‌صدمِ افغانی) و هر مقدار به هزارم (`qtyMilli`).
   ES5 — بی let/const/arrow، تا روی مرورگرهای کهنهٔ صندوق هم بدود.

   ── نوع‌ها (kind) ────────────────────────────────────────────
   percent    درصد از مبلغِ ردیف (bp: ۱۰۰ = ۱٪)
   amount     مبلغِ ثابت از هر واحد (amountMinor)
   price      قیمتِ ویژه برای کالاهای نام‌برده (prices) — مثلاً برای یک گروهِ مشتری
   tiers      پله‌ای بر اساسِ تعدادِ همان ردیف (tiers: {min=qtyMilli, bp} یا POS-19: {min, unitMinor} = قیمتِ ثابتِ هر واحد)
   bxgy       بخر X بگیر Y؛ ارزان‌ترین واحدهای دامنه (buy، get، bp)
   bundle     بسته: هر qty واحد از دامنه به priceMinor
   threshold  آستانهٔ مبلغِ خرید (tiers: {min=مبلغ, bp | amountMinor})

   ── ترکیب و اولویت (PRM-12) ──────────────────────────────────
   ۱. قاعدهٔ واجدِ شرط: فعال، معتبر، در بازهٔ زمانی، گروه/کانالِ درست، کدِ
      واردشده (اگر کد دارد) و ظرفیتِ مصرف.
   ۲. اثرِ هر قاعده «تنها» روی سبدِ دست‌نخورده سنجیده می‌شود (D).
   ۳. اگر قاعدهٔ «انحصاری» با D>0 هست، فقط یکی‌شان می‌ماند:
      اولویتِ بیشتر ⇒ D بیشتر ⇒ شناسهٔ کوچک‌تر.
   ۴. وگرنه از «بهترین قیمت»ها فقط یکی با D بیشتر (⇒ اولویت ⇒ شناسه)، به‌علاوهٔ
      همهٔ «ترکیبی»ها.
   ۵. اعمال به ترتیب: اول قاعده‌های ردیفی، بعد آستانهٔ مبلغ؛ در هر دسته اولویتِ
      بیشتر اول، بعد شناسه. هر قاعده روی «ماندهٔ» ردیف‌ها اعمال می‌شود.

   ── گردکردن (بخشِ ۳.۴) و کف ─────────────────────────────────
   - هر تخفیف مضربِ پلهٔ ارز (`stepMinor`، AFN = ۱۰۰) است، نیمه رو به بالا.
   - تخفیفِ سطحِ فاکتور و بسته با «بزرگ‌ترین باقی‌مانده» بینِ ردیف‌ها پخش
     می‌شود؛ ردیفی که پُر شد سهمِ اضافه را به بقیه می‌دهد.
   - هیچ ردیفی زیرِ بهای خرید (`costMinor`، گرد رو به بالا به پله) نمی‌رود،
     مگر قاعده `allowBelowCost` داشته باشد؛ آن‌وقت هم زیرِ صفر نمی‌رود.
   ========================================================== */
(function (factory) {
  'use strict';
  var api = factory();
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  if (typeof window !== 'undefined') window.TohidPromo = api;
  else if (typeof globalThis !== 'undefined') globalThis.TohidPromo = api;
})(function () {
  'use strict';

  var VERSION = 1;
  var KINDS = ['percent', 'amount', 'price', 'tiers', 'bxgy', 'bundle', 'threshold'];
  var STACKS = ['exclusive', 'best', 'combine'];
  var CHANNELS = ['pos', 'online'];
  var FULL_BP = 10000;
  var MAX = 9007199254740991;          // ۲^۵۳−۱ — کاتلین هم همین سقف را می‌سنجد

  // ── حسابِ صحیحِ امن ────────────────────────────────────
  function safe(x) {
    if (!(x <= MAX && x >= -MAX) || Math.floor(x) !== x) throw new Error('promo: عدد از بازهٔ امن بیرون است');
    return x;
  }
  function mul(a, b) { return safe(a * b); }
  function add(a, b) { return safe(a + b); }
  /** ⌊a/b⌋ برای a ≥ 0 و b > 0 */
  function div(a, b) { return Math.floor(a / b); }
  /** n/d گرد به نزدیک‌ترین مضربِ پله، نیمه رو به بالا (n ≥ 0) ⇒ به واحدِ کوچک */
  function roundStep(n, d, step) {
    var q = mul(d, step);
    return mul(div(add(n, div(q, 2)), q), step);
  }
  /** n/d رو به بالا به مضربِ پله (n ≥ 0) */
  function ceilStep(n, d, step) {
    var q = mul(d, step);
    return mul(div(add(n, q - 1), q), step);
  }
  function floorStep(n, step) { return n <= 0 ? 0 : mul(div(n, step), step); }

  // ── خواندنِ ورودی ──────────────────────────────────────
  /** عددِ صحیح در بازهٔ امن؛ هر چیزِ دیگر ⇒ پیش‌فرض */
  function int(x, dflt) { return (typeof x === 'number' && isFinite(x) && Math.floor(x) === x && Math.abs(x) <= MAX) ? x : dflt; }
  function str(x) { return typeof x === 'string' ? x : ''; }
  function bool(x, dflt) { return typeof x === 'boolean' ? x : dflt; }
  function strs(x) {
    var out = [];
    if (Object.prototype.toString.call(x) !== '[object Array]') return out;
    for (var i = 0; i < x.length; i++) if (typeof x[i] === 'string') out.push(x[i]);
    return out;
  }
  function ints(x) {
    var out = [];
    if (Object.prototype.toString.call(x) !== '[object Array]') return out;
    for (var i = 0; i < x.length; i++) out.push(int(x[i], -1));
    return out;
  }
  function objs(x) {
    var out = [];
    if (Object.prototype.toString.call(x) !== '[object Array]') return out;
    for (var i = 0; i < x.length; i++) out.push(x[i] && typeof x[i] === 'object' ? x[i] : {});
    return out;
  }
  function has(list, v) { for (var i = 0; i < list.length; i++) if (list[i] === v) return true; return false; }

  var DIGITS = { '۰': '0', '۱': '1', '۲': '2', '۳': '3', '۴': '4', '۵': '5', '۶': '6', '۷': '7', '۸': '8', '۹': '9',
    '٠': '0', '١': '1', '٢': '2', '٣': '3', '٤': '4', '٥': '5', '٦': '6', '٧': '7', '٨': '8', '٩': '9' };

  /** کدِ تخفیف: ارقامِ فارسی/عربی ⇒ لاتین، a-z ⇒ A-Z، فاصله و خط‌تیره حذف. */
  function normalizeCode(s) {
    s = str(s);
    var out = '';
    for (var i = 0; i < s.length; i++) {
      var c = s.charAt(i);
      if (DIGITS[c]) { out += DIGITS[c]; continue; }
      if (c >= 'a' && c <= 'z') { out += String.fromCharCode(c.charCodeAt(0) - 32); continue; }
      if (c === ' ' || c === '-' || c === '\t' || c === '\n' || c === '\r' || c === '‌') continue;
      out += c;
    }
    return out;
  }

  function scopeOf(o) {
    o = o && typeof o === 'object' ? o : {};
    return {
      products: strs(o.products), categories: strs(o.categories), suppliers: strs(o.suppliers), brands: strs(o.brands),
      exProducts: strs(o.exProducts), exCategories: strs(o.exCategories), exSuppliers: strs(o.exSuppliers), exBrands: strs(o.exBrands),
    };
  }
  function windowOf(o) {
    o = o && typeof o === 'object' ? o : {};
    return { from: str(o.from), to: str(o.to), days: ints(o.days), start: int(o.start, -1), end: int(o.end, -1), except: strs(o.except) };
  }
  //  POS-19: `unitMinor` فقط وقتی آمده نوشته می‌شود — قاعدهٔ درصدیِ ذخیره‌شده بایت‌به‌بایت همان
  function tierOf(o) {
    var t = { min: int(o.min, -1), bp: int(o.bp, 0), amountMinor: int(o.amountMinor, 0) };
    if (o.unitMinor !== undefined && o.unitMinor !== null) t.unitMinor = int(o.unitMinor, -1);
    return t;
  }
  /** قیمتِ ثابتِ پله؛ `-1` = پلهٔ درصدی */
  function tierUnit(t) { return typeof t.unitMinor === 'number' ? t.unitMinor : -1; }
  /** POS-19 — پله‌های این قاعده قیمتِ ثابتِ واحد‌اند، نه درصد (پلهٔ نخست می‌گوید) */
  function tierPrice(r) { return r.kind === 'tiers' && r.tiers.length > 0 && tierUnit(r.tiers[0]) >= 0; }
  function priceOf(o) { return { productId: str(o.productId), unitMinor: int(o.unitMinor, -1) }; }

  /** هر شیء ⇒ قاعدهٔ کامل با پیش‌فرض‌ها. نوعِ نادرست ⇒ پیش‌فرض (که `validate` می‌گیرد). */
  function ruleOf(o) {
    o = o && typeof o === 'object' ? o : {};
    var i, tiers = [], prices = [], to = objs(o.tiers), po = objs(o.prices);
    for (i = 0; i < to.length; i++) tiers.push(tierOf(to[i]));
    for (i = 0; i < po.length; i++) prices.push(priceOf(po[i]));
    return {
      id: str(o.id), name: str(o.name), kind: str(o.kind),
      active: bool(o.active, true), stack: typeof o.stack === 'string' ? o.stack : 'best', priority: int(o.priority, 0),
      code: str(o.code), maxUses: int(o.maxUses, 0), uses: int(o.uses, 0), allowBelowCost: bool(o.allowBelowCost, false),
      scope: scopeOf(o.scope), groups: strs(o.groups), channels: strs(o.channels), window: windowOf(o.window),
      bp: int(o.bp, 0), amountMinor: int(o.amountMinor, 0), prices: prices, tiers: tiers,
      buy: int(o.buy, 0), get: int(o.get, 0), qty: int(o.qty, 0), priceMinor: int(o.priceMinor, 0),
    };
  }

  // ── تاریخ ──────────────────────────────────────────────
  /** "YYYY-MM-DD" ⇒ شمارهٔ روز از ۱۹۷۰-۰۱-۰۱؛ نادرست ⇒ null */
  function epochDay(s) {
    var m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(str(s));
    if (!m) return null;
    var y = +m[1], mo = +m[2], d = +m[3];
    if (mo < 1 || mo > 12 || d < 1) return null;
    var leap = (y % 4 === 0 && y % 100 !== 0) || y % 400 === 0;
    var dim = [31, leap ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31][mo - 1];
    if (d > dim) return null;
    // Howard Hinnant — days_from_civil
    var yy = mo <= 2 ? y - 1 : y;
    var era = Math.floor(yy / 400);
    var yoe = yy - era * 400;
    var mp = (mo + 9) % 12;
    var doy = Math.floor((153 * mp + 2) / 5) + d - 1;
    var doe = yoe * 365 + Math.floor(yoe / 4) - Math.floor(yoe / 100) + doy;
    return era * 146097 + doe - 719468;
  }
  /** روزِ هفتهٔ ISO: دوشنبه ۱ … یکشنبه ۷ */
  function isoDow(day) { return ((day + 3) % 7 + 7) % 7 + 1; }

  function hasWindow(w) {
    return w.from !== '' || w.to !== '' || w.days.length > 0 || w.start !== -1 || w.end !== -1 || w.except.length > 0;
  }

  /** PRM-09 — بازه‌ای که از نیمه‌شب رد می‌شود به روزِ شروعش تعلق دارد. */
  function inWindow(w, at) {
    if (!hasWindow(w)) return true;
    if (!at || typeof at !== 'object') return false;
    var today = epochDay(at.date), minute = int(at.minute, -1);
    if (today === null || minute < 0 || minute > 1439) return false;
    var day = today;
    if (w.start !== -1) {
      if (w.start < w.end) { if (minute < w.start || minute >= w.end) return false; }
      else if (minute < w.end) day = today - 1;
      else if (minute < w.start) return false;
    }
    var fd = epochDay(w.from), td = epochDay(w.to);
    if (w.from !== '' && (fd === null || day < fd)) return false;
    if (w.to !== '' && (td === null || day > td)) return false;
    if (w.days.length > 0 && !has(w.days, isoDow(day))) return false;
    for (var i = 0; i < w.except.length; i++) if (epochDay(w.except[i]) === day) return false;
    return true;
  }

  // ── اعتبارسنجی (PRM-17) ────────────────────────────────
  var CODE_RE = /^[A-Z0-9_]{3,32}$/;

  function validBp(bp) { return bp >= 1 && bp <= FULL_BP; }
  /** فقط فاصله، تب، سطرِ تازه، فاصلهٔ نشکن و نیم‌فاصله ⇒ خالی (کاتلین هم همین فهرست) */
  function blank(s) {
    for (var i = 0; i < s.length; i++) if (' \t\n\r\u00a0\u200c'.indexOf(s.charAt(i)) < 0) return false;
    return true;
  }

  /** خطاهای یک قاعده به ترتیبِ ثابت؛ آرایهٔ خالی یعنی معتبر. */
  function validate(raw, stepMinor) {
    var r = ruleOf(raw), step = int(stepMinor, 100), e = [], i;
    if (!(step > 0)) step = 100;
    if (r.id === '') e.push('id');
    if (blank(r.name)) e.push('name');
    if (!has(KINDS, r.kind)) e.push('kind');
    if (!has(STACKS, r.stack)) e.push('stack');
    if (r.priority < -1000 || r.priority > 1000) e.push('priority');
    if (r.code !== '' && !CODE_RE.test(normalizeCode(r.code))) e.push('code');
    if (r.maxUses < 0 || r.uses < 0) e.push('uses');
    for (i = 0; i < r.channels.length; i++) if (!has(CHANNELS, r.channels[i])) { e.push('channel'); break; }
    var w = r.window, wbad = false;
    if (w.from !== '' && epochDay(w.from) === null) wbad = true;
    if (w.to !== '' && epochDay(w.to) === null) wbad = true;
    if (!wbad && w.from !== '' && w.to !== '' && epochDay(w.from) > epochDay(w.to)) wbad = true;
    for (i = 0; i < w.days.length; i++) if (w.days[i] < 1 || w.days[i] > 7) wbad = true;
    for (i = 0; i < w.except.length; i++) if (epochDay(w.except[i]) === null) wbad = true;
    if ((w.start === -1) !== (w.end === -1)) wbad = true;
    if (w.start !== -1 && (w.start < 0 || w.start > 1439 || w.end < 0 || w.end > 1440 || w.start === w.end)) wbad = true;
    if (wbad) e.push('window');

    var k = r.kind;
    if (k === 'percent' && !validBp(r.bp)) e.push('bp');
    if (k === 'amount' && (r.amountMinor <= 0 || r.amountMinor % step !== 0)) e.push('amount');
    if (k === 'price') {
      var seen = {}, pbad = r.prices.length === 0;
      for (i = 0; i < r.prices.length; i++) {
        var p = r.prices[i];
        if (p.productId === '' || p.unitMinor < 0 || seen['#' + p.productId]) pbad = true;
        seen['#' + p.productId] = true;
      }
      if (pbad) e.push('prices');
    }
    if (k === 'tiers' || k === 'threshold') {
      var tbad = r.tiers.length === 0, useAmt = r.tiers.length > 0 && r.tiers[0].amountMinor > 0;
      //  POS-19: پلهٔ قیمتِ ثابت — قیمتِ هر پلهٔ بالاتر کمتر، بی درصد و بی مبلغ
      var byPrice = tierPrice(r), byBp = !byPrice && (k === 'tiers' || !useAmt);
      for (i = 0; i < r.tiers.length; i++) {
        var t = r.tiers[i];
        if (t.min <= 0) tbad = true;
        if (byPrice) { if (tierUnit(t) < 0 || t.bp !== 0 || t.amountMinor !== 0) tbad = true; }
        else if (tierUnit(t) !== -1) tbad = true;
        else if (byBp) { if (!validBp(t.bp) || t.amountMinor !== 0) tbad = true; }
        else if (t.bp !== 0 || t.amountMinor <= 0 || t.amountMinor % step !== 0) tbad = true;
        if (i > 0) {
          var a = r.tiers[i - 1];
          if (t.min <= a.min) tbad = true;
          if (byPrice ? tierUnit(t) >= tierUnit(a) : byBp ? t.bp <= a.bp : t.amountMinor <= a.amountMinor) tbad = true;
        }
      }
      if (tbad) e.push('tiers');
    }
    if (k === 'bxgy' && (r.buy < 1 || r.get < 1 || r.buy > 1000 || r.get > 1000 || !validBp(r.bp))) e.push('bxgy');
    if (k === 'bundle' && (r.qty < 2 || r.qty > 1000 || r.priceMinor <= 0 || r.priceMinor % step !== 0)) e.push('bundle');
    return e;
  }

  // ── سبد ────────────────────────────────────────────────
  function lineTotalMinor(qtyMilli, unitMinor, step) { return roundStep(mul(qtyMilli, unitMinor), 1000, step); }

  function prepare(cart) {
    cart = cart && typeof cart === 'object' ? cart : {};
    var step = int(cart.stepMinor, 100);
    if (!(step > 0)) step = 100;
    var raw = objs(cart.lines), lines = [];
    for (var i = 0; i < raw.length; i++) {
      var l = raw[i];
      var qty = int(l.qtyMilli, 0), unit = int(l.unitMinor, 0);
      var amount = int(l.amountMinor, null);
      if (amount === null) amount = (qty > 0 && unit > 0) ? lineTotalMinor(qty, unit, step) : 0;
      var cost = int(l.costMinor, 0);
      lines.push({
        key: str(l.key), productId: str(l.productId), qtyMilli: qty, unitMinor: unit, amountMinor: amount,
        costMinor: cost, category: str(l.category), supplierId: str(l.supplierId), brand: str(l.brand),
        noPromo: bool(l.noPromo, false),
        units: qty > 0 ? div(qty, 1000) : 0,
        costFloor: (cost > 0 && qty > 0) ? ceilStep(mul(qty, cost), 1000, step) : 0,
        open: str(l.productId) !== '' && !bool(l.noPromo, false) && qty > 0 && amount > 0,
      });
    }
    var codes = [], seen = {}, cc = strs(cart.coupons);
    for (i = 0; i < cc.length; i++) {
      var c = normalizeCode(cc[i]);
      if (c !== '' && !seen['#' + c]) { seen['#' + c] = true; codes.push(c); }
    }
    return {
      step: step, lines: lines, codes: codes, group: str(cart.group),
      channel: typeof cart.channel === 'string' ? cart.channel : 'pos', at: cart.at,
    };
  }

  function inScope(s, l) {
    if (!l.open) return false;
    var any = s.products.length + s.categories.length + s.suppliers.length + s.brands.length > 0;
    var inc = !any
      || has(s.products, l.productId)
      || (l.category !== '' && has(s.categories, l.category))
      || (l.supplierId !== '' && has(s.suppliers, l.supplierId))
      || (l.brand !== '' && has(s.brands, l.brand));
    if (!inc) return false;
    // PRM-08 — مستثنا بر شامل مقدم است
    return !(has(s.exProducts, l.productId)
      || (l.category !== '' && has(s.exCategories, l.category))
      || (l.supplierId !== '' && has(s.exSuppliers, l.supplierId))
      || (l.brand !== '' && has(s.exBrands, l.brand)));
  }

  /** مانده‌ای که این قاعده هنوز می‌تواند از ردیف کم کند (مضربِ پله). */
  function roomOf(r, l, remaining, step) {
    var floor = r.allowBelowCost ? 0 : l.costFloor;
    return floorStep(remaining - floor, step);
  }

  /**
   * پخشِ `total` بینِ ردیف‌ها به نسبتِ `weights` با بزرگ‌ترین باقی‌مانده، هر سهم
   * حداکثر `caps`ِ خودش؛ سهمِ اضافهٔ ردیفِ پُرشده دوباره بینِ بقیه پخش می‌شود.
   * همه به واحدِ کوچک و مضربِ پله.
   */
  function allocate(total, weights, caps, step) {
    var n = weights.length, shares = [], active = [], i, capSum = 0;
    for (i = 0; i < n; i++) {
      shares.push(0);
      capSum = add(capSum, div(caps[i], step));
      if (weights[i] > 0 && caps[i] >= step) active.push(i);
    }
    var left = Math.min(div(total, step), capSum);
    while (left > 0 && active.length > 0) {
      var W = 0, j;
      for (j = 0; j < active.length; j++) W = add(W, weights[active[j]]);
      var base = {}, rem = {}, given = 0;
      for (j = 0; j < active.length; j++) {
        var idx = active[j], num = mul(left, weights[idx]);
        base[idx] = div(num, W); rem[idx] = num - base[idx] * W; given += base[idx];
      }
      var order = active.slice().sort(function (a, b) { return rem[b] - rem[a] || a - b; });
      for (j = 0; j < left - given; j++) base[order[j]] += 1;
      var overflow = 0, next = [];
      for (j = 0; j < active.length; j++) {
        var x = active[j], room = div(caps[x], step) - shares[x];
        var take = Math.min(base[x], room);
        shares[x] += take; overflow += base[x] - take;
        if (shares[x] < div(caps[x], step)) next.push(x);
      }
      left = overflow; active = next;
    }
    for (i = 0; i < n; i++) shares[i] = mul(shares[i], step);
    return shares;
  }

  /** واحدهای کامل از ردیف‌های داخلِ دامنه، مرتب بر قیمتِ واحد (asc یا desc)؛ برابری ⇒ ردیفِ جلوتر. */
  function unitsOf(idx, lines, desc) {
    var sorted = idx.slice().sort(function (a, b) {
      var d = lines[a].unitMinor - lines[b].unitMinor;
      return (desc ? -d : d) || a - b;
    });
    return sorted;
  }

  /**
   * اثرِ یک قاعده روی مانده‌ها ⇒ سهمِ هر ردیف (مضربِ پله). `rem` را عوض نمی‌کند.
   */
  function shareOf(r, cx, rem) {
    var step = cx.step, lines = cx.lines, n = lines.length, out = [], idx = [], i, k = r.kind;
    for (i = 0; i < n; i++) {
      out.push(0);
      if (inScope(r.scope, lines[i])) idx.push(i);
    }
    if (idx.length === 0) return out;
    var l, d, j;
    if (k === 'percent' || k === 'amount' || k === 'price' || k === 'tiers') {
      for (j = 0; j < idx.length; j++) {
        i = idx[j]; l = lines[i]; d = 0;
        if (k === 'percent') d = roundStep(mul(l.amountMinor, r.bp), FULL_BP, step);
        else if (k === 'amount') d = roundStep(mul(r.amountMinor, l.qtyMilli), 1000, step);
        else if (k === 'price') {
          for (var p = 0; p < r.prices.length; p++) {
            if (r.prices[p].productId === l.productId) {
              d = Math.max(0, l.amountMinor - lineTotalMinor(l.qtyMilli, r.prices[p].unitMinor, step));
              break;
            }
          }
        } else {
          var tier = null;
          for (var t = 0; t < r.tiers.length; t++) if (r.tiers[t].min <= l.qtyMilli) tier = r.tiers[t];
          //  POS-19: همان راهِ «price» — مبلغِ ردیف با قیمتِ پله، هرگز تخفیفِ منفی
          if (tier === null) d = 0;
          else if (tierUnit(tier) >= 0) d = Math.max(0, l.amountMinor - lineTotalMinor(l.qtyMilli, tierUnit(tier), step));
          else d = roundStep(mul(l.amountMinor, tier.bp), FULL_BP, step);
        }
        out[i] = Math.min(d, roomOf(r, l, rem[i], step));
      }
      return out;
    }
    if (k === 'bxgy') {
      var total = 0;
      for (j = 0; j < idx.length; j++) total += lines[idx[j]].units;
      var free = mul(div(total, r.buy + r.get), r.get);
      var asc = unitsOf(idx, lines, false);
      for (j = 0; j < asc.length && free > 0; j++) {
        i = asc[j]; l = lines[i];
        var cnt = Math.min(free, l.units);
        free -= cnt;
        if (cnt > 0) out[i] = Math.min(roundStep(mul(mul(cnt, l.unitMinor), r.bp), FULL_BP, step), roomOf(r, l, rem[i], step));
      }
      return out;
    }
    if (k === 'bundle') {
      var all = 0;
      for (j = 0; j < idx.length; j++) all += lines[idx[j]].units;
      var groups = div(all, r.qty), take = mul(groups, r.qty);
      if (groups === 0) return out;
      var desc = unitsOf(idx, lines, true), weights = [], caps = [], value = 0;
      for (i = 0; i < n; i++) { weights.push(0); caps.push(0); }
      for (j = 0; j < desc.length && take > 0; j++) {
        i = desc[j]; l = lines[i];
        var c = Math.min(take, l.units);
        take -= c;
        weights[i] = mul(c, l.unitMinor);
        value = add(value, weights[i]);
        caps[i] = roomOf(r, l, rem[i], step);
      }
      var disc = value - mul(groups, r.priceMinor);
      if (disc <= 0) return out;
      return allocate(roundStep(disc, 1, step), weights, caps, step);
    }
    if (k === 'threshold') {
      var w = [], cp = [], base = 0;
      for (i = 0; i < n; i++) { w.push(0); cp.push(0); }
      for (j = 0; j < idx.length; j++) {
        i = idx[j];
        w[i] = Math.max(0, rem[i]);
        cp[i] = roomOf(r, lines[i], rem[i], step);
        base = add(base, w[i]);
      }
      var tier = null;
      for (var q = 0; q < r.tiers.length; q++) if (r.tiers[q].min <= base) tier = r.tiers[q];
      if (!tier) return out;
      var amt = tier.amountMinor > 0 ? Math.min(tier.amountMinor, floorStep(base, step)) : roundStep(mul(base, tier.bp), FULL_BP, step);
      return allocate(amt, w, cp, step);
    }
    return out;
  }

  function sum(a) { var s = 0; for (var i = 0; i < a.length; i++) s = add(s, a[i]); return s; }

  function eligible(r, cx) {
    if (!r.active || validate(r, cx.step).length > 0) return false;
    if (r.code !== '' && !has(cx.codes, normalizeCode(r.code))) return false;
    if (r.maxUses > 0 && r.uses >= r.maxUses) return false;
    if (r.groups.length > 0 && !has(r.groups, cx.group)) return false;
    if (r.channels.length > 0 && !has(r.channels, cx.channel)) return false;
    return inWindow(r.window, cx.at);
  }

  /** همان شرط‌ها بی کد و بی ظرفیت — برای پیامِ «کد الآن کار نمی‌کند» */
  function liveIgnoringCode(r, cx) {
    if (!r.active || validate(r, cx.step).length > 0) return false;
    if (r.groups.length > 0 && !has(r.groups, cx.group)) return false;
    if (r.channels.length > 0 && !has(r.channels, cx.channel)) return false;
    return inWindow(r.window, cx.at);
  }

  function cmpId(a, b) { return a.id < b.id ? -1 : a.id > b.id ? 1 : 0; }

  /**
   * سبد + قاعده‌ها ⇒ تخفیفِ هر ردیف، قاعده‌های اعمال‌شده و وضعیتِ هر کد.
   * @returns {{subtotalMinor:number, discountMinor:number, totalMinor:number,
   *   lines:{key:string, amountMinor:number, discountMinor:number, netMinor:number}[],
   *   applied:{id:string, name:string, discountMinor:number}[],
   *   codes:{code:string, status:string}[]}}
   */
  function evaluate(cart, rawRules) {
    var cx = prepare(cart), n = cx.lines.length, i, j;
    var rules = [], rr = objs(rawRules);
    for (i = 0; i < rr.length; i++) rules.push(ruleOf(rr[i]));
    var fresh = [];
    for (i = 0; i < n; i++) fresh.push(cx.lines[i].amountMinor);

    // ۱–۲. واجدِ شرط‌ها و اثرِ تنهای هرکدام
    var cand = [];
    for (i = 0; i < rules.length; i++) {
      if (!eligible(rules[i], cx)) continue;
      var d = sum(shareOf(rules[i], cx, fresh));
      if (d > 0) cand.push({ r: rules[i], d: d });
    }
    // ۳–۴. انتخاب
    var chosen = [], ex = [], best = null;
    for (i = 0; i < cand.length; i++) if (cand[i].r.stack === 'exclusive') ex.push(cand[i]);
    if (ex.length > 0) {
      ex.sort(function (a, b) { return (b.r.priority - a.r.priority) || (b.d - a.d) || cmpId(a.r, b.r); });
      chosen.push(ex[0].r);
    } else {
      for (i = 0; i < cand.length; i++) {
        var c = cand[i];
        if (c.r.stack === 'combine') chosen.push(c.r);
        else if (c.r.stack === 'best') {
          if (!best || c.d > best.d || (c.d === best.d && (c.r.priority > best.r.priority
            || (c.r.priority === best.r.priority && cmpId(c.r, best.r) < 0)))) best = c;
        }
      }
      if (best) chosen.push(best.r);
    }
    // ۵. اعمال
    chosen.sort(function (a, b) {
      var ca = a.kind === 'threshold' ? 1 : 0, cb = b.kind === 'threshold' ? 1 : 0;
      return (ca - cb) || (b.priority - a.priority) || cmpId(a, b);
    });
    var rem = fresh.slice(), disc = [], applied = [];
    for (i = 0; i < n; i++) disc.push(0);
    for (j = 0; j < chosen.length; j++) {
      var sh = shareOf(chosen[j], cx, rem), got = 0;
      for (i = 0; i < n; i++) { rem[i] -= sh[i]; disc[i] += sh[i]; got += sh[i]; }
      if (got > 0) applied.push({ id: chosen[j].id, name: chosen[j].name, discountMinor: got });
    }

    var lines = [], subtotal = 0, discount = 0;
    for (i = 0; i < n; i++) {
      var l = cx.lines[i];
      lines.push({ key: l.key, amountMinor: l.amountMinor, discountMinor: disc[i], netMinor: l.amountMinor - disc[i] });
      subtotal = add(subtotal, l.amountMinor);
      discount = add(discount, disc[i]);
    }

    // وضعیتِ هر کدِ واردشده
    var codes = [];
    for (j = 0; j < cx.codes.length; j++) {
      var code = cx.codes[j], st = 'unknown';
      for (i = 0; i < rules.length; i++) {
        var r = rules[i];
        if (r.code === '' || normalizeCode(r.code) !== code) continue;
        var s;
        if (applied.some(function (a) { return a.id === r.id; })) s = 'applied';
        else if (eligible(r, cx)) s = 'no_effect';
        else if (liveIgnoringCode(r, cx) && r.maxUses > 0 && r.uses >= r.maxUses) s = 'used_up';
        else s = 'inactive';
        if (rankOf(s) > rankOf(st)) st = s;
      }
      codes.push({ code: code, status: st });
    }

    return { subtotalMinor: subtotal, discountMinor: discount, totalMinor: subtotal - discount, lines: lines, applied: applied, codes: codes };
  }
  var RANKS = ['unknown', 'inactive', 'used_up', 'no_effect', 'applied'];
  function rankOf(s) { for (var i = 0; i < RANKS.length; i++) if (RANKS[i] === s) return i; return 0; }

  return {
    VERSION: VERSION,
    KINDS: KINDS,
    STACKS: STACKS,
    CHANNELS: CHANNELS,
    normalizeCode: normalizeCode,
    ruleOf: ruleOf,
    validate: validate,
    tierPrice: function (r) { return tierPrice(ruleOf(r)); },
    inWindow: function (w, at) { return inWindow(windowOf(w), at); },
    isoDow: function (date) { var d = epochDay(date); return d === null ? 0 : isoDow(d); },
    evaluate: evaluate,
  };
});
