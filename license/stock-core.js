/* ==========================================================
   فروشگاه من | هستهٔ موجودی و بهای تمام‌شده (stock-core)
   ----------------------------------------------------------
   PRD-15 · PRD-16 · INV-02 · INV-19 — بخشِ ۳.۶ و ۳.۴ مشخصات.

   این فایل **هیچ چیزی از مرورگر نمی‌خواهد** (نه DOM، نه fetch، نه
   ذخیره). همان کد در مرورگر، در سرور و در آزمونِ Node می‌دود. همتای
   Kotlinاش `core/stock/StockCore.kt` است و هر دو یک فایلِ بردارِ
   مرجع را می‌گذرانند: `license/test-vectors/stock-v1.json`.

   ⚠️ هنوز به هیچ صفحه‌ای وصل نیست. موتورِ امروزِ موجودی (جمعِ ورودی
   منهای فروش) و سود («قیمتِ خریدِ آخر» روی هر قلمِ فروش) دست نخورده‌اند.

   ── واحدها ──────────────────────────────────────────────────────
   پول = عددِ صحیحِ واحدِ کوچک (AFN یک‌صدم). مقدار = عددِ صحیحِ هزارم.
   هر عددی که صحیحِ امن (۵۳ بیت) نباشد رد می‌شود؛ هیچ اعشاری وارد نمی‌شود.

   ── قاعده‌ها ────────────────────────────────────────────────────
   ۱) بها: میانگینِ موزونِ متحرک برای هر کالا در هر انبار. «ارزشِ
      موجودی» و «مقدار» نگه داشته می‌شوند، نه «بهای واحد»؛ خروجِ q از
      موجودیِ مثبت = mulDivRound(ارزش، q، مقدار)، و خروجِ همهٔ موجودی =
      کلِ ارزش. پس آزمونِ تطبیق دقیق است، بی یک ریال خطای گرد کردن.
   ۲) موجودیِ منفی: کمبود با «آخرین بهای معلوم» حساب می‌شود (بهای
      موقت). دریافتِ بعدی با بهای صریح، تفاوتِ بهای واقعیِ همان کمبود با
      بهای موقت را به‌صورتِ costAdjustMinor (سندِ تعدیلِ بها) می‌دهد.
   ۳) لات فقط برای انقضا و FEFO است، نه بها. لاتِ "" یعنی «بی لات» و
      فقط همان می‌تواند منفی شود؛ دریافتِ بعدی اول کمبودِ آن را پر می‌کند.
   ۴) حالتِ local (دستگاه می‌داند): خروجِ بیش از موجود رد می‌شود (قاعدهٔ
      امروز). حالتِ recorded (سندِ ثبت‌شدهٔ آفلاین): **هرگز** برای
      کمبود رد نمی‌شود؛ موجودی منفی می‌شود و «وظیفهٔ بررسیِ موجودی» برمی‌گردد.
   ۵) سندِ بدشکل (بی کاربر، بی زمان، بی دلیل، …) در هر دو حالت رد می‌شود.
   ========================================================== */
(function (factory) {
  'use strict';
  var api = factory();
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  if (typeof window !== 'undefined') window.TohidStockCore = api;
  else if (typeof globalThis !== 'undefined') globalThis.TohidStockCore = api;
})(function () {
  'use strict';

  var MAX_SAFE = 9007199254740991;
  var SMALL = 1125899906842624; // 2^50: تا این‌جا ضرب و تقسیم با Number دقیق است

  function isSafeInt(x) {
    return typeof x === 'number' && isFinite(x) && Math.floor(x) === x && Math.abs(x) <= MAX_SAFE;
  }

  function add(a, b) {
    var r = a + b;
    if (!isSafeInt(r)) throw new RangeError('stock-core: overflow');
    return r;
  }

  /* floor(n / d) برای d > 0، دقیق برای |n| ≤ 2^52 */
  function floorDivSmall(n, d) {
    var q = Math.floor(n / d);
    var r = n - q * d;
    if (r < 0) q -= 1;
    else if (r >= d) q += 1;
    return q;
  }

  /**
   * round(a × b / c) با نیمه به سمتِ +∞ (همان Math.round) = floorDiv(2ab + c, 2c).
   * c > 0. اگر حاصل‌ضرب از ۲^۵۰ بگذرد با BigInt حساب می‌شود.
   */
  function mulDivRound(a, b, c) {
    if (!isSafeInt(a) || !isSafeInt(b) || !isSafeInt(c) || c <= 0) {
      throw new RangeError('stock-core: mulDivRound needs safe integers and c > 0');
    }
    var ab = a * b;
    if (Math.abs(ab) <= SMALL && c <= SMALL) return floorDivSmall(2 * ab + c, 2 * c);
    if (typeof BigInt !== 'function') throw new RangeError('stock-core: overflow (no BigInt)');
    var ZERO = BigInt(0), ONE = BigInt(1), TWO = BigInt(2);
    var n = BigInt(a) * BigInt(b) * TWO + BigInt(c);
    var d = BigInt(c) * TWO;
    var q = n / d;
    if (n % d !== ZERO && n < ZERO) q = q - ONE;
    var out = Number(q);
    if (!isSafeInt(out)) throw new RangeError('stock-core: overflow');
    return out;
  }

  /* ==========================================================
     نوع‌های حرکت (بخشِ ۳.۶)
     dir: +1 ورود، -1 خروج، 0 علامت‌دار (خودِ qtyMilli علامت دارد)
     ========================================================== */
  var TYPES = {
    purchase_receipt: { dir: 1, cost: 'required' },
    sale: { dir: -1 },
    customer_return: { dir: 1, cost: 'optional' },
    supplier_return: { dir: -1 },
    adjustment: { dir: 0, reason: true, cost: 'optional' },
    count_adjust: { dir: 0, reason: true, cost: 'optional' },
    damage: { dir: -1, reason: true },
    expired: { dir: -1, reason: true },
    transfer_out: { dir: -1 },
    transfer_in: { dir: 1, cost: 'required' },
    reserve: { reserve: 1 },
    release: { reserve: -1 },
    production: { dir: 0, cost: 'optional' },
    cost_adjust: { value: true, reason: true },
  };

  /* فقط کلیدِ خودِ جدول؛ "constructor" یا "__proto__" نوع نیستند */
  function specOf(type) {
    return typeof type === 'string' && Object.prototype.hasOwnProperty.call(TYPES, type) ? TYPES[type] : null;
  }

  var TOTAL_KEYS = ['purchasesMinor', 'landedMinor', 'cogsMinor', 'wasteMinor', 'supplierReturnsMinor',
    'adjustmentsMinor', 'transfersMinor', 'productionMinor', 'revaluationMinor', 'costAdjustMinor'];

  function zeroTotals() {
    var t = {};
    for (var i = 0; i < TOTAL_KEYS.length; i++) t[TOTAL_KEYS[i]] = 0;
    return t;
  }

  function nonEmpty(s) { return typeof s === 'string' && s.trim() !== ''; }
  function present(x) { return x !== undefined && x !== null; }

  /* ---- تاریخ: yyyy-MM-dd واقعی، یا خالی/null یعنی «ندارد» ---- */
  var ISO = /^(\d{4})-(\d{2})-(\d{2})$/;
  function isoDateOk(s) {
    var m = ISO.exec(s);
    if (!m) return false;
    var y = +m[1], mo = +m[2], d = +m[3];
    if (mo < 1 || mo > 12 || d < 1) return false;
    var leap = (y % 4 === 0 && y % 100 !== 0) || y % 400 === 0;
    var max = [31, leap ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31][mo - 1];
    return d <= max;
  }
  function normDate(s) { return present(s) && s !== '' ? s : null; }

  /** نخستین ایرادِ سند، یا null. ترتیبِ بررسی ثابت است (Kotlin هم همین). */
  function validate(doc) {
    if (!doc || !nonEmpty(doc.id)) return 'id_required';
    var spec = specOf(doc.type);
    if (!spec) return 'unknown_type';
    if (!nonEmpty(doc.productId)) return 'product_required';
    if (!nonEmpty(doc.warehouseId)) return 'warehouse_required';
    if (!nonEmpty(doc.userId)) return 'user_required';
    if (!isSafeInt(doc.at) || doc.at <= 0) return 'time_required';
    if (spec.reason && !nonEmpty(doc.reason)) return 'reason_required';

    var q = doc.qtyMilli;
    if (spec.value) {
      if (present(q) && q !== 0) return 'bad_qty';
    } else if (spec.dir === 0) {
      if (!isSafeInt(q) || q === 0) return 'bad_qty';
    } else if (!isSafeInt(q) || q <= 0) return 'bad_qty';

    if (spec.value) {
      if (!isSafeInt(doc.valueMinor) || doc.valueMinor === 0) return 'bad_cost';
    }
    if (present(doc.costMinor) && (!isSafeInt(doc.costMinor) || doc.costMinor < 0)) return 'bad_cost';
    if (present(doc.landedMinor) && (!isSafeInt(doc.landedMinor) || doc.landedMinor < 0)) return 'bad_cost';
    if (spec.cost === 'required' && !present(doc.costMinor)) return 'cost_required';

    if (present(doc.lotId) && typeof doc.lotId !== 'string') return 'bad_lot';
    if (present(doc.lot)) {
      if (typeof doc.lot !== 'object' || !nonEmpty(doc.lot.lotId)) return 'bad_lot';
      var pd = normDate(doc.lot.productionDate), ed = normDate(doc.lot.expiryDate);
      if (pd !== null && (typeof pd !== 'string' || !isoDateOk(pd))) return 'bad_date';
      if (ed !== null && (typeof ed !== 'string' || !isoDateOk(ed))) return 'bad_date';
      if (pd !== null && ed !== null && pd > ed) return 'bad_date';
    }
    return null;
  }

  /** اثرِ سند روی موجودیِ دست (هزارم، با علامت). رزرو و تعدیلِ بها صفرند. */
  function signedQtyMilli(doc) {
    var spec = doc ? specOf(doc.type) : null;
    if (!spec || spec.reserve || spec.value) return 0;
    var q = doc.qtyMilli || 0;
    return spec.dir === 0 ? q : spec.dir * q;
  }

  /* ==========================================================
     لات و FEFO
     ========================================================== */
  function cmpNullLast(a, b) {
    if (a === b) return 0;
    if (a === null || a === undefined) return 1;
    if (b === null || b === undefined) return -1;
    return a < b ? -1 : 1;
  }

  /** انقضای زودتر، بعد تولیدِ زودتر، بعد نوبتِ دریافت، بعد شناسه */
  function compareLots(x, y) {
    return cmpNullLast(normDate(x.expiryDate), normDate(y.expiryDate)) ||
      cmpNullLast(normDate(x.productionDate), normDate(y.productionDate)) ||
      ((x.seq || 0) - (y.seq || 0)) ||
      (x.lotId < y.lotId ? -1 : x.lotId > y.lotId ? 1 : 0);
  }

  /** منقضی یعنی تاریخِ انقضا پیش از امروز؛ لاتی که امروز منقضی می‌شود هنوز سالم است */
  function isExpired(lot, today) {
    var e = normDate(lot.expiryDate);
    return !!today && e !== null && e < today;
  }

  /**
   * برداشتِ qtyMilli از لات‌ها به ترتیبِ FEFO. لاتِ preferLotId (اسکنِ سری)
   * اول می‌آید، مگر منقضی باشد و منقضی مجاز نباشد. ورودی دست نمی‌خورد.
   */
  function pickFefo(lots, qtyMilli, opts) {
    opts = opts || {};
    var today = opts.today || null;
    var order = [];
    for (var i = 0; i < lots.length; i++) {
      var l = lots[i];
      if (l.qtyMilli > 0 && (opts.includeExpired || !isExpired(l, today))) order.push(l);
    }
    order.sort(compareLots);
    if (present(opts.preferLotId)) {
      for (var j = 0; j < order.length; j++) {
        if (order[j].lotId === opts.preferLotId) {
          order.unshift(order.splice(j, 1)[0]);
          break;
        }
      }
    }
    var left = qtyMilli, picks = [];
    for (var k = 0; k < order.length && left > 0; k++) {
      var take = Math.min(order[k].qtyMilli, left);
      picks.push({ lotId: order[k].lotId, qtyMilli: take });
      left -= take;
    }
    return { picks: picks, shortMilli: left };
  }

  /* ==========================================================
     جایگاه (کالا × انبار) — تغییرناپذیر؛ هر سند یک نسخهٔ تازه می‌سازد
     ========================================================== */
  function keyOf(productId, warehouseId) { return productId + '\u0000' + warehouseId; }

  function newPosition(productId, warehouseId) {
    return {
      productId: productId, warehouseId: warehouseId,
      qtyMilli: 0, valueMinor: 0, reservedMilli: 0,
      lastCost: null, lots: [], nextSeq: 0, totals: zeroTotals(),
    };
  }

  function clonePosition(p) {
    var lots = [];
    for (var i = 0; i < p.lots.length; i++) {
      var l = p.lots[i];
      lots.push({ lotId: l.lotId, productionDate: l.productionDate, expiryDate: l.expiryDate, qtyMilli: l.qtyMilli, seq: l.seq });
    }
    var totals = {};
    for (var k = 0; k < TOTAL_KEYS.length; k++) totals[TOTAL_KEYS[k]] = p.totals[TOTAL_KEYS[k]];
    return {
      productId: p.productId, warehouseId: p.warehouseId,
      qtyMilli: p.qtyMilli, valueMinor: p.valueMinor, reservedMilli: p.reservedMilli,
      lastCost: p.lastCost ? { num: p.lastCost.num, den: p.lastCost.den } : null,
      lots: lots, nextSeq: p.nextSeq, totals: totals,
    };
  }

  function findLot(pos, lotId) {
    for (var i = 0; i < pos.lots.length; i++) if (pos.lots[i].lotId === lotId) return pos.lots[i];
    return null;
  }

  function ensureLot(pos, lotId, productionDate, expiryDate) {
    var l = findLot(pos, lotId);
    if (!l) {
      l = { lotId: lotId, productionDate: normDate(productionDate), expiryDate: normDate(expiryDate), qtyMilli: 0, seq: pos.nextSeq++ };
      pos.lots.push(l);
    }
    return l;
  }

  function expiredQty(pos, today) {
    var s = 0;
    for (var i = 0; i < pos.lots.length; i++) {
      var l = pos.lots[i];
      if (l.qtyMilli > 0 && isExpired(l, today)) s += l.qtyMilli;
    }
    return s;
  }

  function lastCostOf(pos, q) {
    return pos.lastCost ? mulDivRound(pos.lastCost.num, q, pos.lastCost.den) : 0;
  }

  /** بهای خروجِ q: بخشِ پوشیده به میانگین، کمبود به آخرین بهای معلوم */
  function outCost(pos, q) {
    var covered = Math.min(q, Math.max(pos.qtyMilli, 0));
    var short = q - covered;
    var cost = 0;
    if (covered > 0) cost = covered === pos.qtyMilli ? pos.valueMinor : mulDivRound(pos.valueMinor, covered, pos.qtyMilli);
    if (short > 0) cost = add(cost, lastCostOf(pos, short));
    return { cost: cost, unknown: short > 0 && !pos.lastCost };
  }

  /** بهای ورودِ q بی بهای صریح: به میانگینِ جاری (یا بهای موقتِ کمبود) */
  function implicitInCost(pos, q) {
    if (pos.qtyMilli > 0) return mulDivRound(pos.valueMinor, q, pos.qtyMilli);
    if (pos.qtyMilli < 0) {
      var shortage = -pos.qtyMilli;
      var covered = Math.min(q, shortage);
      var pc = covered === shortage ? -pos.valueMinor : mulDivRound(-pos.valueMinor, covered, shortage);
      return add(pc, q > covered ? lastCostOf(pos, q - covered) : 0);
    }
    return lastCostOf(pos, q);
  }

  function bucketOut(t, type, cost) {
    switch (type) {
      case 'sale': t.cogsMinor = add(t.cogsMinor, cost); break;
      case 'supplier_return': t.supplierReturnsMinor = add(t.supplierReturnsMinor, cost); break;
      case 'damage': case 'expired': t.wasteMinor = add(t.wasteMinor, cost); break;
      case 'transfer_out': t.transfersMinor = add(t.transfersMinor, -cost); break;
      case 'adjustment': case 'count_adjust': t.adjustmentsMinor = add(t.adjustmentsMinor, -cost); break;
      case 'production': t.productionMinor = add(t.productionMinor, -cost); break;
    }
  }

  function bucketIn(t, doc, cost) {
    switch (doc.type) {
      case 'purchase_receipt':
        t.purchasesMinor = add(t.purchasesMinor, doc.costMinor);
        t.landedMinor = add(t.landedMinor, doc.landedMinor || 0);
        break;
      case 'customer_return': t.cogsMinor = add(t.cogsMinor, -cost); break;
      case 'transfer_in': t.transfersMinor = add(t.transfersMinor, cost); break;
      case 'adjustment': case 'count_adjust': t.adjustmentsMinor = add(t.adjustmentsMinor, cost); break;
      case 'production': t.productionMinor = add(t.productionMinor, cost); break;
    }
  }

  function refuse(book, error, extra) {
    var r = { ok: false, error: error };
    if (extra) for (var k in extra) if (Object.prototype.hasOwnProperty.call(extra, k)) r[k] = extra[k];
    return { book: book, result: r };
  }

  /** موجودیِ فروش‌پذیرِ سالم: موجودی − منقضی − رزرو. ایرادِ رد یا null. */
  function saleShortage(pos, q, today) {
    var onHand = pos ? pos.qtyMilli : 0;
    var reserved = pos ? pos.reservedMilli : 0;
    var expired = pos ? expiredQty(pos, today) : 0;
    var available = onHand - expired - reserved;
    if (q <= available) return null;
    if (expired > 0 && q <= onHand - reserved) {
      return { error: 'expired_stock', extra: { availableMilli: Math.max(0, available), expiredMilli: expired } };
    }
    return { error: 'insufficient_stock', extra: { availableMilli: Math.max(0, available) } };
  }

  function emptyBook() { return { positions: {} }; }

  function withPosition(book, pos) {
    var positions = {};
    for (var k in book.positions) if (Object.prototype.hasOwnProperty.call(book.positions, k)) positions[k] = book.positions[k];
    positions[keyOf(pos.productId, pos.warehouseId)] = pos;
    return { positions: positions };
  }

  /**
   * یک سند را روی دفتر می‌نشاند. دفترِ ورودی دست نمی‌خورد.
   * opts.mode: 'local' (پیش‌فرض) | 'recorded' · opts.today: 'yyyy-MM-dd' برای انقضا
   * خروجی: { book, result }؛ اگر result.ok نباشد book همان دفترِ ورودی است.
   */
  function apply(book, doc, opts) {
    opts = opts || {};
    var recorded = opts.mode === 'recorded';
    var today = opts.today || null;
    var err = validate(doc);
    if (err) return refuse(book, err);

    var spec = specOf(doc.type);
    var prev = book.positions[keyOf(doc.productId, doc.warehouseId)] || null;
    var pos = prev ? clonePosition(prev) : newPosition(doc.productId, doc.warehouseId);
    var result = { ok: true, costMinor: 0, costAdjustMinor: 0, picks: [], shortMilli: 0, reviewTask: null };

    if (spec.value) {
      pos.valueMinor = add(pos.valueMinor, doc.valueMinor);
      pos.totals.revaluationMinor = add(pos.totals.revaluationMinor, doc.valueMinor);
      result.costMinor = doc.valueMinor;
    } else if (spec.reserve === 1) {
      if (!recorded) {
        var rs = saleShortage(prev, doc.qtyMilli, today);
        if (rs) return refuse(book, rs.error, rs.extra);
      }
      pos.reservedMilli = add(pos.reservedMilli, doc.qtyMilli);
    } else if (spec.reserve === -1) {
      if (doc.qtyMilli > pos.reservedMilli) return refuse(book, 'release_exceeds_reserved');
      pos.reservedMilli -= doc.qtyMilli;
    } else {
      var signed = signedQtyMilli(doc);
      if (signed < 0) outbound(pos, doc, -signed, recorded, today, result);
      else inbound(pos, doc, signed, result);
      if (result.error) return refuse(book, result.error, result.extra);
    }

    if (pos.qtyMilli > 0) pos.lastCost = { num: pos.valueMinor, den: pos.qtyMilli };
    return { book: withPosition(book, pos), result: result };
  }

  function outbound(pos, doc, q, recorded, today, result) {
    var isSale = doc.type === 'sale';
    if (!recorded) {
      var s = isSale ? saleShortage(pos, q, today) : null;
      if (!isSale && q > Math.max(0, pos.qtyMilli)) {
        s = { error: 'insufficient_stock', extra: { availableMilli: Math.max(0, pos.qtyMilli) } };
      }
      if (s) { result.error = s.error; result.extra = s.extra; return; }
    }
    var prefer = present(doc.lotId) ? doc.lotId : null;
    var first = pickFefo(pos.lots, q, { today: today, includeExpired: !isSale, preferLotId: prefer });
    var picks = first.picks, short = first.shortMilli, expiredSold = false;
    if (isSale && short > 0) {
      // ثبت‌شده: کالا واقعاً رفته؛ بعد از لات‌های سالم، منقضی‌ها
      var rest = [];
      for (var i = 0; i < pos.lots.length; i++) {
        var l = pos.lots[i], used = 0;
        for (var j = 0; j < picks.length; j++) if (picks[j].lotId === l.lotId) used += picks[j].qtyMilli;
        rest.push({ lotId: l.lotId, productionDate: l.productionDate, expiryDate: l.expiryDate, qtyMilli: l.qtyMilli - used, seq: l.seq });
      }
      var second = pickFefo(rest, short, { today: today, includeExpired: true });
      expiredSold = second.picks.length > 0;
      picks = picks.concat(second.picks);
      short = second.shortMilli;
    }
    var oc = outCost(pos, q);
    for (var p = 0; p < picks.length; p++) findLot(pos, picks[p].lotId).qtyMilli -= picks[p].qtyMilli;
    if (short > 0) ensureLot(pos, '', null, null).qtyMilli -= short;
    pos.qtyMilli -= q;
    pos.valueMinor = add(pos.valueMinor, -oc.cost);
    bucketOut(pos.totals, doc.type, oc.cost);

    result.costMinor = oc.cost;
    result.picks = picks;
    result.shortMilli = short;
    if (short > 0 || expiredSold) {
      var reasons = [];
      if (short > 0) reasons.push('negative_stock');
      if (expiredSold) reasons.push('expired_sold');
      if (oc.unknown) reasons.push('unknown_cost');
      result.reviewTask = {
        kind: 'stock_review', productId: pos.productId, warehouseId: pos.warehouseId, docId: doc.id,
        reasons: reasons, shortMilli: short, qtyAfterMilli: pos.qtyMilli,
      };
    }
  }

  function inbound(pos, doc, q, result) {
    var explicit = present(doc.costMinor);
    // هزینهٔ جانبیِ خرید جزوِ بهاست (بخشِ ۳.۶)
    var cost = explicit ? add(doc.costMinor, doc.type === 'purchase_receipt' ? (doc.landedMinor || 0) : 0)
      : implicitInCost(pos, q);
    var adj = 0;
    if (explicit && pos.qtyMilli < 0) {
      var shortage = -pos.qtyMilli;
      var s = Math.min(q, shortage);
      var trueCost = s === q ? cost : mulDivRound(cost, s, q);
      var provisional = s === shortage ? -pos.valueMinor : mulDivRound(-pos.valueMinor, s, shortage);
      adj = trueCost - provisional;
    }
    // لات: اول کمبودِ لاتِ "" پر می‌شود، باقی به لاتِ همین سند
    var lotId = doc.lot ? doc.lot.lotId : (present(doc.lotId) ? doc.lotId : '');
    var left = q;
    var blank = findLot(pos, '');
    if (lotId !== '' && blank && blank.qtyMilli < 0) {
      var fill = Math.min(left, -blank.qtyMilli);
      blank.qtyMilli += fill;
      left -= fill;
    }
    if (left > 0) {
      var lot = ensureLot(pos, lotId, doc.lot ? doc.lot.productionDate : null, doc.lot ? doc.lot.expiryDate : null);
      lot.qtyMilli += left;
    }
    pos.qtyMilli = add(pos.qtyMilli, q);
    pos.valueMinor = add(add(pos.valueMinor, cost), -adj);
    bucketIn(pos.totals, doc, cost);
    pos.totals.costAdjustMinor = add(pos.totals.costAdjustMinor, adj);
    if (explicit && pos.qtyMilli <= 0) pos.lastCost = { num: cost, den: q };

    result.costMinor = cost;
    result.costAdjustMinor = adj;
  }

  /** همهٔ سندها به همان ترتیبِ داده‌شده (سرور: ترتیبِ HLC) */
  function replay(docs, opts) {
    var book = emptyBook(), results = [];
    for (var i = 0; i < docs.length; i++) {
      var r = apply(book, docs[i], opts);
      book = r.book;
      results.push(r.result);
    }
    return { book: book, results: results };
  }

  /* ==========================================================
     خواندن
     ========================================================== */
  function snapshot(p) {
    var lots = [];
    var sorted = p.lots.slice().sort(compareLots);
    for (var i = 0; i < sorted.length; i++) {
      var l = sorted[i];
      if (l.qtyMilli !== 0) lots.push({ lotId: l.lotId, productionDate: l.productionDate, expiryDate: l.expiryDate, qtyMilli: l.qtyMilli });
    }
    var totals = {};
    for (var k = 0; k < TOTAL_KEYS.length; k++) totals[TOTAL_KEYS[k]] = p.totals[TOTAL_KEYS[k]];
    return {
      productId: p.productId, warehouseId: p.warehouseId,
      qtyMilli: p.qtyMilli, valueMinor: p.valueMinor, reservedMilli: p.reservedMilli,
      sellableMilli: p.qtyMilli - p.reservedMilli,
      lastCost: p.lastCost ? { num: p.lastCost.num, den: p.lastCost.den } : null,
      lots: lots, totals: totals,
    };
  }

  function position(book, productId, warehouseId) {
    var p = book.positions[keyOf(productId, warehouseId)];
    return p ? snapshot(p) : null;
  }

  function positions(book) {
    var list = [];
    for (var k in book.positions) if (Object.prototype.hasOwnProperty.call(book.positions, k)) list.push(book.positions[k]);
    list.sort(function (a, b) {
      if (a.productId !== b.productId) return a.productId < b.productId ? -1 : 1;
      return a.warehouseId < b.warehouseId ? -1 : a.warehouseId > b.warehouseId ? 1 : 0;
    });
    return list.map(snapshot);
  }

  function bookTotals(book) {
    var t = zeroTotals();
    for (var k in book.positions) {
      if (!Object.prototype.hasOwnProperty.call(book.positions, k)) continue;
      for (var i = 0; i < TOTAL_KEYS.length; i++) t[TOTAL_KEYS[i]] = add(t[TOTAL_KEYS[i]], book.positions[k].totals[TOTAL_KEYS[i]]);
    }
    return t;
  }

  /** آزمونِ تطبیق (بخشِ ۳.۶): ارزش = خرید + جانبی − بهای فروش − ضایعات − برگشت به تأمین‌کننده ± بقیه */
  function reconcile(p) {
    var t = p.totals;
    var expected = t.purchasesMinor + t.landedMinor - t.cogsMinor - t.wasteMinor - t.supplierReturnsMinor +
      t.adjustmentsMinor + t.transfersMinor + t.productionMinor + t.revaluationMinor - t.costAdjustMinor;
    return { ok: expected === p.valueMinor, valueMinor: p.valueMinor, expectedMinor: expected };
  }

  /* ==========================================================
     دفترِ حرکت (INV-02): فیلتر و خروجی
     ========================================================== */
  function onHandMilli(docs, productId, warehouseId) {
    var s = 0;
    for (var i = 0; i < docs.length; i++) {
      var d = docs[i];
      if (d.productId === productId && d.warehouseId === warehouseId) s = add(s, signedQtyMilli(d));
    }
    return s;
  }

  /** filter: productId، warehouseId، types (آرایه)، userId، from و to (میلی‌ثانیه، هر دو شامل) */
  function filterDocs(docs, f) {
    f = f || {};
    var out = [];
    for (var i = 0; i < docs.length; i++) {
      var d = docs[i];
      if (present(f.productId) && d.productId !== f.productId) continue;
      if (present(f.warehouseId) && d.warehouseId !== f.warehouseId) continue;
      if (present(f.userId) && d.userId !== f.userId) continue;
      if (present(f.types) && f.types.indexOf(d.type) < 0) continue;
      if (present(f.from) && d.at < f.from) continue;
      if (present(f.to) && d.at > f.to) continue;
      out.push(d);
    }
    return out;
  }

  var EXPORT_HEADER = ['id', 'at', 'type', 'productId', 'warehouseId', 'qtyMilli', 'amountMinor', 'userId', 'reason', 'lotId', 'refId'];

  function exportRows(docs) {
    return docs.map(function (d) {
      var amount = '';
      var spec = specOf(d.type);
      if (spec && spec.value) amount = d.valueMinor;
      else if (present(d.costMinor)) amount = d.costMinor + (d.type === 'purchase_receipt' ? (d.landedMinor || 0) : 0);
      var lotId = d.lot && d.lot.lotId ? d.lot.lotId : (d.lotId || '');
      return [d.id, d.at, d.type, d.productId, d.warehouseId, signedQtyMilli(d), amount,
        d.userId, d.reason || '', lotId, d.refId || ''];
    });
  }

  return {
    VECTORS_FORMAT: 'stock-v1',
    TYPES: TYPES,
    TOTAL_KEYS: TOTAL_KEYS,
    EXPORT_HEADER: EXPORT_HEADER,
    mulDivRound: mulDivRound,
    isoDateOk: isoDateOk,
    validate: validate,
    signedQtyMilli: signedQtyMilli,
    compareLots: compareLots,
    isExpired: isExpired,
    pickFefo: pickFefo,
    emptyBook: emptyBook,
    apply: apply,
    replay: replay,
    position: position,
    positions: positions,
    bookTotals: bookTotals,
    reconcile: reconcile,
    onHandMilli: onHandMilli,
    filterDocs: filterDocs,
    exportRows: exportRows,
  };
});
