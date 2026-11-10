package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.i18n.StrGoods
import ir.vil3ntec.tohid.core.i18n.StrLedger
import ir.vil3ntec.tohid.core.stock.Lot
import ir.vil3ntec.tohid.core.stock.Pick
import ir.vil3ntec.tohid.core.stock.StockBook
import ir.vil3ntec.tohid.core.stock.StockCore
import ir.vil3ntec.tohid.core.stock.StockDoc
import ir.vil3ntec.tohid.core.stock.StockMode

/**
 *  INV-21 — بسته‌بندیِ دوباره و تولید: **شکستنِ کارتن** (یک کالای «کارتن» ⇒ N دانه از کالای دیگر) و **ساختِ بسته**
 *  از اجزا (هر بسته = چند جزء). همان «تولید یا بسته‌بندی» ِ §۳.۶ و نوعِ `production` ِ `StockCore`.
 *
 *  ── ثبت، بی جدولِ تازه ───────────────────────────────────────────────
 *  هر کالا یک جفتِ امروز: ردیفِ اصلاحِ `warehouseEntries` (`isAdjustment`، `adjKind = repack`، `refId` = کلیدِ کار)
 *  + حرکتِ `stockMovements` (`type = production`، مقدارِ علامت‌دار، `refId` = همان ردیفِ انبار)؛ و یک سند در
 *  `auditLog` (`id` = کلیدِ کار، `refIds` = ردیف‌ها). ورودی‌ها منفی، خروجی مثبت؛ همه در یک دفترِ تازه (یا همه یا هیچ)
 *  و یک بار برای هر کلید ([has]). موتورِ امروز (ورودی منهای فروش) همان ردیف‌ها را می‌شمارد، سرورِ v3 جمعِ حرکت‌ها را،
 *  گزارشِ ضایعات `production` را زیان نمی‌داند (`ShrinkReport.classify`) و `StockLedger` آن را سندِ `production` می‌خواند.
 *
 *  ── بها (پول به یک‌صدم، مقدار به هزارم، گرد فقط `mulDivRound`) ─────────
 *  بهای هر ورودی = `purchasePrice` ِ همان کالا × مقدار (همان مبنای «ارزشِ موجودی» و بهای فروشِ امروز).
 *  بهای کلِ خروجی = جمعِ بهای ورودی‌ها ⇒ بهای یک دانه = بهای کارتن ÷ دانه‌ها، بهای یک بسته = جمعِ اجزا.
 *  همان بها روی `price` ِ ردیف‌ها می‌نشیند تا «ارزشِ انبار» (`WarehouseEngine.summary`) و بهای فروشِ گزارش (`ReportCore`،
 *  اصلاحِ مثبتِ بهادار) بها را ببرند، نه صفر.
 *
 *  ⛔ کیت (موجودیِ ساختگی) و کالای خدماتی نه ورودی‌اند نه خروجی؛ ورودی بیش از موجودی رد می‌شود (همان نگهبانِ `adjustStock`).
 *  ⛔ پشتِ تنظیمِ دستگاه (`StockPlacesPref.repack`، پیش‌فرض خاموش). منطقِ خالص؛ آزمون: `RepackTest`.
 *
 *  ── سری و بهای میانگین در سندِ تولید (`lots = true`؛ «دستورِ ساخت و سریِ تولید» = `StockPlacesPref.bom`، پیش‌فرض خاموش) ──
 *  ورودیِ هر جزء از سری‌های همان محل برداشته می‌شود — FEFO (`StockCore.pickFefo`: انقضای زودتر اول، منقضی هم، بی انقضا آخر)
 *  یا سری‌های برگزیده (همان قاعدهٔ `StockPlaces.pickFrom`) — یک ردیفِ منفی برای هر سری با `lotId`، انقضا و سری‌اش، تا
 *  موتورِ تازه (`StockLedger`) و موتورِ امروز (`ExpiryEngine.isLotUse`) همان سری را کم کنند. بها = بهای میانگینِ موزونِ همان
 *  محل (همان هستهٔ `StockCore` روی دفترهای [books]، به ترتیبِ همان ردیف‌ها) و بهای خروجی دقیقاً جمعِ آن‌ها (`costMinor`).
 *  دانه‌ها سری و انقضای هر تکهٔ کارتن را می‌گیرند (بی انقضا هم)؛ بستهٔ ساخته‌شده زودترین انقضای سری‌های برداشته‌شده را.
 *  خاموش ⇒ همان ردیف‌های امروز، بایت‌به‌بایت (`RepackBomTest`).
 */
object Repack {

  const val KIND = "repack"
  const val MOVE = "production"
  const val BREAK = "stock_repack"
  const val BUILD = "stock_assembly"
  /** سقفِ هر مقدار: یک میلیون واحد */
  const val MAX_MILLI = 1_000_000_000L
  const val MAX_PARTS = 20

  /** یک جزء: کالا و مقدار (هزارم) — در ساختِ بسته برای **یک** بسته */
  data class Part(val productId: String, val qtyMilli: Long)

  /** یک ردیفِ کار: کالا، مقدار (هزارم، ورودی مثبت نوشته می‌شود)، بهای یک واحد و کلِ ردیف (یک‌صدم)، و موجودیِ پیش از کار */
  data class Line(val productId: String, val qtyMilli: Long, val unitMinor: Long, val totalMinor: Long, val haveMilli: Long)

  /** INV-21 — یک ردیفِ مصرف با سری (`lots`): سریِ هسته (`""` = بی سری)، انقضا و سریِ ردیف، مقدار و بهای میانگینِ همان ردیف */
  data class Use(val productId: String, val lotId: String, val expiryDate: String, val batch: String, val qtyMilli: Long, val costMinor: Long)

  /** INV-21 — یک ردیفِ خروجی با سری (`lots`): [lotId] `""` = بی سری، [OWN] = سریِ تازهٔ خودِ همان ردیف (`w:` + شناسه) */
  data class Out(val lotId: String, val expiryDate: String, val batch: String, val qtyMilli: Long, val costMinor: Long)

  /** [uses]/[outs] فقط با `lots`؛ `null` ⇒ همان ردیف‌های امروز */
  data class Plan(val inputs: List<Line>, val output: Line, val uses: List<Use>? = null, val outs: List<Out>? = null)

  /** سریِ تازهٔ خروجی: همان `w:` + شناسهٔ ردیف ِ `StockLedger.lotRefOf` */
  const val OWN = "w:"

  sealed interface Planned {
    data class Ok(val plan: Plan) : Planned
    data class Bad(val message: String) : Planned
  }

  fun has(d: ShopData, key: String): Boolean = d.auditLog.any { it.id == key } || d.warehouseEntries.any { it.refId == key }

  /**
   *  ردیفِ یک سندِ تولید (`adjKind = repack` با کلیدِ کار): سند یا-همه-یا-هیچ نوشته شد، پس یک ردیفش **تنها** پاک نمی‌شود و
   *  کالا، مقدار و بهایش عوض نمی‌شود (`WarehouseEngine.deleteEntry`/`editEntry`، همان قاعدهٔ ورودِ خرید ِ PUR-05) — وگرنه
   *  برداشتنِ ردیفِ منفیِ کارتن کارتن را برمی‌گرداند و دانه‌ها هم می‌ماندند (کالا و ارزشِ ساختگی). اصلاح = همان کار برعکس.
   */
  fun isDocRow(e: WarehouseEntry): Boolean = e.isAdjustment && e.adjKind == KIND && !e.refId.isNullOrBlank()

  /**
   *  پیامِ ردِ حذف ([productId] `null`) یا ویرایشِ یک ورودِ انبار: ورودِ فاکتورِ خرید (`PurchaseBook.fixedEntry`، PUR-05: کالا و
   *  مقدار) یا ردیفِ سندِ تولید ([isDocRow]: کالا، مقدار و بها)؛ `null` ⇒ آزاد. یادداشت و انقضا در هر دو آزادند.
   */
  fun entryFixed(d: ShopData, e: WarehouseEntry, productId: String? = null, units: Double? = null, price: Double? = null): String? = when {
    PurchaseBook.fixedEntry(d, e, productId, units) -> PurchaseBook.PURCHASE_ENTRY
    isDocRow(e) && (productId == null || productId != e.productId || units != e.units || price != e.price) -> StrGoods.repackEntryFixed
    else -> null
  }

  /** بهای یک واحدِ کالا (یک‌صدمِ افغانی) — همان `purchasePrice` */
  fun unitMinor(p: Product): Long = maxOf(0L, StockLedger.minor(p.purchasePrice))

  /** مقدار × مقدار به هزارم؛ ناصحیح (کم‌تر از یک هزارم) ⇒ null */
  private fun times(aMilli: Long, bMilli: Long): Long? {
    val n = aMilli * bMilli
    return if (n % 1000L == 0L) n / 1000L else null
  }

  private fun haveOf(d: ShopData, stock: Map<String, Map<String, Long>>?, location: String?, pid: String): Long =
    if (location == null || stock == null) StockLedger.milli(ShopStore.stock(d, pid)) else StockPlaces.at(stock, pid, location)

  private fun stockProblem(p: Product?, role: String): String? = when {
    p == null -> StrGoods.pickRole(role)
    ProductPlansMap.isKit(p) -> StrGoods.isKit(p.name)
    p.stockless == true -> StrGoods.isService(p.name)
    !p.active -> StrGoods.isInactive(p.name)
    else -> null
  }

  private fun line(d: ShopData, stock: Map<String, Map<String, Long>>?, location: String?, p: Product, q: Long): Line {
    val u = unitMinor(p)
    return Line(p.id, q, u, StockCore.mulDivRound(u, q, 1000), haveOf(d, stock, location, p.id))
  }

  private fun outLine(d: ShopData, stock: Map<String, Map<String, Long>>?, location: String?, p: Product, q: Long, total: Long): Line =
    Line(p.id, q, StockCore.mulDivRound(total, 1000, q), total, haveOf(d, stock, location, p.id))

  private fun shortage(d: ShopData, inputs: List<Line>, location: String?): String? {
    val names = d.products.associate { it.id to it }
    for (l in inputs) {
      if (l.qtyMilli <= l.haveMilli) continue
      val p = names[l.productId]
      val where = if (location == null) "" else StrGoods.atPlace(StockPlaces.label(location))
      return StrGoods.repackShort(p?.name.orEmpty(), where, ir.vil3ntec.tohid.qty(maxOf(0L, l.haveMilli) / 1000.0), p?.unit.orEmpty(), ir.vil3ntec.tohid.qty(l.qtyMilli / 1000.0))
    }
    return null
  }

  /**
   *  شکستنِ [packsMilli] از کالای [fromId] (مثلاً کارتن) به [perPackMilli] × همان، از کالای [toId] (مثلاً دانه).
   *  [location] `null` ⇒ موجودیِ کل (محل‌ها خاموش)، وگرنه موجودیِ همان محل و ردیف‌ها با همان محل.
   */
  fun planBreak(
    d: ShopData, fromId: String, packsMilli: Long, toId: String, perPackMilli: Long, location: String? = null,
    /** INV-21 — سری و بهای میانگین در سندِ تولید (`StockPlacesPref.bom`)؛ `false` ⇒ همان امروز */
    lots: Boolean = false,
    /** سری‌های برگزیدهٔ کارتن (سری، هزارم)؛ `null` ⇒ FEFO */
    chosen: List<Pick>? = null,
  ): Planned {
    val from = d.products.find { it.id == fromId }
    val to = d.products.find { it.id == toId }
    stockProblem(from, StrGoods.packedItemBox)?.let { return Planned.Bad(it) }
    stockProblem(to, StrGoods.looseItem)?.let { return Planned.Bad(it) }
    if (fromId == toId) return Planned.Bad(StrGoods.looseSame)
    if (packsMilli <= 0 || packsMilli > MAX_MILLI) return Planned.Bad(StrGoods.packCountBad)
    if (perPackMilli <= 0 || perPackMilli > MAX_MILLI) return Planned.Bad(StrGoods.perPackBad)
    val outQ = times(packsMilli, perPackMilli)?.takeIf { it in 1..MAX_MILLI } ?: return Planned.Bad(StrGoods.piecesBad)
    val stock = location?.let { StockPlaces.stockAt(d) }
    val input = line(d, stock, location, from!!, packsMilli)
    shortage(d, listOf(input), location)?.let { return Planned.Bad(it) }
    if (!lots) return Planned.Ok(Plan(listOf(input), outLine(d, stock, location, to!!, outQ, input.totalMinor)))
    return lotted(d, stock, location, listOf(input), chosen?.let { mapOf(fromId to it) }.orEmpty(), to!!, outQ) { uses, total ->
      breakOuts(uses, packsMilli, outQ, total)
    }
  }

  /** ساختِ [countMilli] بسته از کالای [bundleId]؛ [parts] مقدارِ هر جزء برای **یک** بسته */
  fun planBuild(
    d: ShopData, bundleId: String, countMilli: Long, parts: List<Part>, location: String? = null,
    lots: Boolean = false,
    /** سری‌های برگزیدهٔ هر جزء (کالا ⇒ سری‌ها)؛ نبودِ کالا ⇒ FEFO */
    chosen: Map<String, List<Pick>> = emptyMap(),
  ): Planned = planWith(d, bundleId, countMilli, parts, location, lots, chosen) { times(it, countMilli) }

  /** همان [planBuild] با قاعدهٔ مقدارِ کلِ هر جزء از مقدارِ یک بسته ([need]؛ `null` ⇒ نادرست) — دستورِ ساخت (`Bom`) با اتلاف */
  internal fun planWith(
    d: ShopData, bundleId: String, countMilli: Long, parts: List<Part>, location: String?, lots: Boolean,
    chosen: Map<String, List<Pick>>, need: (Long) -> Long?,
  ): Planned {
    val bundle = d.products.find { it.id == bundleId }
    stockProblem(bundle, StrGoods.packedItem)?.let { return Planned.Bad(it) }
    if (countMilli <= 0 || countMilli > MAX_MILLI) return Planned.Bad(StrGoods.packCountBad)
    val clean = parts.filter { it.productId.isNotEmpty() }
    if (clean.isEmpty()) return Planned.Bad(StrGoods.addOnePart)
    if (clean.size > MAX_PARTS) return Planned.Bad(StrGoods.maxParts(MAX_PARTS))
    if (clean.map { it.productId }.toSet().size != clean.size) return Planned.Bad(StrGoods.partTwice)
    if (clean.any { it.productId == bundleId }) return Planned.Bad(StrGoods.packSelfPart)
    val stock = location?.let { StockPlaces.stockAt(d) }
    val byId = d.products.associateBy { it.id }
    val inputs = ArrayList<Line>()
    for (pt in clean) {
      val p = byId[pt.productId]
      stockProblem(p, StrGoods.part)?.let { return Planned.Bad(it) }
      if (pt.qtyMilli <= 0 || pt.qtyMilli > MAX_MILLI) return Planned.Bad(StrLedger.qtyOfBad(p!!.name))
      val q = need(pt.qtyMilli)?.takeIf { it in 1..MAX_MILLI } ?: return Planned.Bad(StrLedger.qtyOfBad(p!!.name))
      inputs += line(d, stock, location, p!!, q)
    }
    shortage(d, inputs, location)?.let { return Planned.Bad(it) }
    val total = inputs.fold(0L) { s, l -> s + l.totalMinor }
    if (!lots) return Planned.Ok(Plan(inputs, outLine(d, stock, location, bundle!!, countMilli, total)))
    return lotted(d, stock, location, inputs, chosen, bundle!!, countMilli) { uses, sum ->
      //  بسته از سری‌های اجزا ساخته می‌شود ⇒ سریِ خودش، با زودترین انقضای سری‌های برداشته‌شده؛ هیچ ⇒ بی سری
      val first = uses.mapNotNull { it.expiryDate.ifEmpty { null } }.minOrNull()
      listOf(Out(if (first != null) OWN else "", first.orEmpty(), "", countMilli, sum))
    }
  }

  /* ---------------------------------------------- INV-21 · سری و بهای میانگین */

  /** دفترهای بها و سریِ تولید: موتورِ تازه روشن ⇒ همان نمای `StockLedger.replay`، وگرنه دفترهای محلِ موتورِ امروز (`StockPlaces.booksAt`) */
  internal fun books(d: ShopData, ids: Set<String>): Map<String, StockBook> =
    if (StockLedger.isOn(d)) StockLedger.replay(d, ids).books else StockPlaces.booksAt(d, ids)

  /** سری‌های یک کالا در یک محل از همان [books] (FEFO؛ `""` = بی سری) — پیش‌نمایش و «انتخابِ سری» ِ پنجره */
  fun lotsOf(d: ShopData, productId: String, location: String?): List<Lot> =
    StockCore.position(books(d, setOf(productId))[productId] ?: StockBook(), productId, StockLedger.wid(location.orEmpty()))?.fefoLots.orEmpty()
      .filter { it.qtyMilli > 0 }

  private fun batchOf(lotId: String): String = if (lotId.startsWith(OWN)) "" else lotId

  /** هم‌سری‌ها یکی، صفرها کنار، به ترتیبِ نخستین دیدن */
  private fun merged(picks: List<Pick>): List<Pick> {
    val m = LinkedHashMap<String, Long>()
    for (p in picks) if (p.qtyMilli > 0) m[p.lotId] = (m[p.lotId] ?: 0L) + p.qtyMilli
    return m.map { Pick(it.key, it.value) }
  }

  /**
   *  ردیف‌های مصرفِ هر جزء: سری‌های همان محل به FEFO (`StockCore.pickFefo` ِ خروجِ غیرِ فروش: منقضی هم، بی انقضا آخر؛ کمبود ⇒
   *  بی سری) یا [chosen] با همان قاعدهٔ `StockPlaces.pickFrom`؛ بهای هر ردیف همان که هسته به همین ترتیب می‌دهد (میانگینِ موزون).
   *  خروجی‌ها ([outs]) از جمعِ همین بهاها؛ ورودی و خروجی با بهای میانگین (ارزشِ دفتر بی تغییر).
   */
  private fun lotted(
    d: ShopData, stock: Map<String, Map<String, Long>>?, location: String?, inputs: List<Line>, chosen: Map<String, List<Pick>>,
    outP: Product, outQ: Long, outs: (List<Use>, Long) -> List<Out>,
  ): Planned {
    val wid = StockLedger.wid(location.orEmpty())
    val books = books(d, inputs.mapTo(HashSet()) { it.productId })
    val byId = d.products.associateBy { it.id }
    val uses = ArrayList<Use>()
    val lines = ArrayList<Line>()
    for (l in inputs) {
      var book = books[l.productId] ?: StockBook()
      val pool = StockCore.position(book, l.productId, wid)?.lots.orEmpty().filter { it.qtyMilli > 0 }
      val want = chosen[l.productId]
      val picks = if (want == null) {
        val r = StockCore.pickFefo(pool, l.qtyMilli, null, includeExpired = true)
        merged(r.picks + Pick("", r.shortMilli))
      } else when (val got = StockPlaces.pickFrom(pool, byId.getValue(l.productId), location.orEmpty(), l.qtyMilli, want)) {
        StockPlaces.Picked.Plain -> listOf(Pick("", l.qtyMilli))
        is StockPlaces.Picked.Bad -> return Planned.Bad(got.message)
        is StockPlaces.Picked.Lots -> got.picks
      }
      var sum = 0L
      picks.forEachIndexed { i, pk ->
        val doc = StockDoc(
          id = "plan:$i", type = MOVE, productId = l.productId, warehouseId = wid, userId = "plan", at = StockCore.MAX_SAFE,
          qtyMilli = -pk.qtyMilli, lotId = pk.lotId.ifEmpty { null },
        )
        val r = StockCore.apply(book, doc, StockMode.RECORDED, null)
        book = r.book
        val lot = if (pk.lotId.isEmpty()) null else pool.firstOrNull { it.lotId == pk.lotId }
        uses += Use(l.productId, pk.lotId, lot?.expiryDate.orEmpty(), batchOf(pk.lotId), pk.qtyMilli, r.result.costMinor)
        sum += r.result.costMinor
      }
      lines += l.copy(unitMinor = StockCore.mulDivRound(sum, 1000, l.qtyMilli), totalMinor = sum)
    }
    val total = lines.fold(0L) { a, l -> StockCore.add(a, l.totalMinor) }
    val rows = outs(uses, total)
    check(rows.sumOf { it.qtyMilli } == outQ && rows.sumOf { it.costMinor } == total) { "repack lots" }
    return Planned.Ok(Plan(lines, outLine(d, stock, location, outP, outQ, total), uses, rows))
  }

  /** دانه‌های هر تکهٔ کارتن با سری و انقضای همان تکه؛ مقدار و بها با جمعِ تجمعی (`mulDivRound`) تا جمع دقیقاً همان بماند */
  private fun breakOuts(uses: List<Use>, inQ: Long, outQ: Long, total: Long): List<Out> {
    val out = ArrayList<Out>()
    var cum = 0L
    var q0 = 0L
    var c0 = 0L
    for (u in uses) {
      cum += u.qtyMilli
      val q = StockCore.mulDivRound(outQ, cum, inQ) - q0
      q0 += q
      val c = StockCore.mulDivRound(total, q0, outQ) - c0
      c0 += c
      if (q > 0) out += Out(if (u.lotId.startsWith(OWN)) OWN else u.lotId, u.expiryDate, u.batch, q, c)
    }
    return out
  }

  fun breakPack(
    d: ShopData, key: String, fromId: String, packsMilli: Long, toId: String, perPackMilli: Long, location: String?,
    note: String, user: String, today: String, now: Long, newId: () -> String,
    /** INV-21 — «دستورِ ساخت و سریِ تولید» (`StockPlacesPref.bom`)؛ `false` ⇒ همان ردیف‌های امروز */
    lots: Boolean = false,
    chosen: List<Pick>? = null,
  ): StockPlaces.Result {
    if (key.isBlank()) return StockPlaces.Result.Failed(StrGoods.opIdEmpty)
    if (has(d, key)) return StockPlaces.Result.Already
    return when (val p = planBreak(d, fromId, packsMilli, toId, perPackMilli, location, lots, chosen)) {
      is Planned.Bad -> StockPlaces.Result.Failed(p.message)
      is Planned.Ok -> StockPlaces.Result.Ok(
        if (p.plan.uses != null) write(d, key, BREAK, "شکستنِ بسته", p.plan, location, note, user, today, now, newId)  // داده: نامِ کار درونِ یادداشتِ سابقه
        else write(d, key, BREAK, "شکستنِ بسته", p.plan, location, note, user, today, now, newId, breakOutputs(d, p.plan, perPackMilli)),  // داده: نامِ کار درونِ یادداشتِ سابقه
        key,
      )
    }
  }

  /**
   *  INV-21 — سری و انقضای دانه‌ها: کارتن‌هایی که شکسته می‌شوند به FEFO از زودترین ماندهٔ انقضادارِ کارتن برداشته می‌شوند
   *  (`ExpiryEngine.take`، همان که موجودیِ کارتن پس از کار نشان می‌دهد)، پس دانه‌های هر تکه سری و انقضای همان محموله را
   *  می‌گیرند — یک ردیفِ خروجی برای هر تکه، جمع دقیقاً همان دانه‌ها. کارتنِ بی انقضا ⇒ همان یک ردیفِ پیشین.
   */
  internal fun breakOutputs(d: ShopData, plan: Plan, perPackMilli: Long): List<ExpiryEngine.Take> {
    val input = plan.inputs.single()
    val taken = ExpiryEngine.take(d, input.productId, input.qtyMilli)
    if (taken.all { it.expiryDate.isEmpty() && it.batch.isEmpty() }) return listOf(ExpiryEngine.Take("", "", plan.output.qtyMilli))
    val out = ArrayList<ExpiryEngine.Take>()
    var left = plan.output.qtyMilli
    taken.forEachIndexed { i, t ->
      val q = if (i == taken.lastIndex) left else minOf(left, StockCore.mulDivRound(t.qtyMilli, perPackMilli, 1000))
      left -= q
      if (q > 0) out += ExpiryEngine.Take(t.expiryDate, t.batch, q)
    }
    return out
  }

  /**
   *  INV-21 — انقضای بستهٔ ساخته‌شده: زودترین انقضای اجزایی که برداشته می‌شوند (به FEFO)؛ بسته از سریِ اجزا ساخته
   *  می‌شود، پس سریِ خودش را ندارد. هیچ جزءِ انقضادار ⇒ همان ردیفِ بی انقضای پیشین.
   */
  internal fun buildOutputs(d: ShopData, plan: Plan): List<ExpiryEngine.Take> {
    val first = plan.inputs.flatMap { ExpiryEngine.take(d, it.productId, it.qtyMilli) }.map { it.expiryDate }.filter { it.isNotEmpty() }.minOrNull()
    return listOf(ExpiryEngine.Take(first.orEmpty(), "", plan.output.qtyMilli))
  }

  fun build(
    d: ShopData, key: String, bundleId: String, countMilli: Long, parts: List<Part>, location: String?,
    note: String, user: String, today: String, now: Long, newId: () -> String,
    lots: Boolean = false,
    chosen: Map<String, List<Pick>> = emptyMap(),
  ): StockPlaces.Result {
    if (key.isBlank()) return StockPlaces.Result.Failed(StrGoods.opIdEmpty)
    if (has(d, key)) return StockPlaces.Result.Already
    return built(d, key, planBuild(d, bundleId, countMilli, parts, location, lots, chosen), location, note, user, today, now, newId)
  }

  /** ثبتِ ساختِ بسته از یک برنامهٔ آماده — [build] و دستورِ ساخت (`Bom.build`) */
  internal fun built(
    d: ShopData, key: String, planned: Planned, location: String?, note: String, user: String, today: String, now: Long, newId: () -> String,
  ): StockPlaces.Result = when (planned) {
    is Planned.Bad -> StockPlaces.Result.Failed(planned.message)
    is Planned.Ok -> StockPlaces.Result.Ok(
      if (planned.plan.uses != null) write(d, key, BUILD, "ساختِ بسته", planned.plan, location, note, user, today, now, newId)  // داده: نامِ کار درونِ یادداشتِ سابقه
      else write(d, key, BUILD, "ساختِ بسته", planned.plan, location, note, user, today, now, newId, buildOutputs(d, planned.plan)),  // داده: نامِ کار درونِ یادداشتِ سابقه
      key,
    )
  }

  /** اجزای آخرین ساختِ همین بسته (برای پر کردنِ فرم)، مقدار برای یک بسته؛ نبود ⇒ خالی */
  fun lastRecipe(d: ShopData, bundleId: String): List<Part> {
    val doc = d.auditLog.filter { it.type == BUILD }.sortedWith(compareBy<AuditEntry>({ it.createdAt }, { it.id })).lastOrNull { a ->
      d.warehouseEntries.any { it.refId == a.id && it.productId == bundleId && it.units > 0 }
    } ?: return emptyList()
    val rows = d.warehouseEntries.filter { it.refId == doc.id }
    val count = rows.firstOrNull { it.units > 0 }?.let { StockLedger.milli(it.units) } ?: return emptyList()
    return rows.filter { it.units < 0 }.map { Part(it.productId, StockCore.mulDivRound(StockLedger.milli(-it.units), 1000, count)) }
  }

  private fun write(
    d: ShopData, key: String, type: String, title: String, plan: Plan, location: String?, note: String,
    user: String, today: String, now: Long, newId: () -> String,
    /** ردیف‌های خروجی با سری و انقضا (INV-21)؛ جمعِ مقدارها = `plan.output.qtyMilli` */
    outputs: List<ExpiryEngine.Take> = listOf(ExpiryEngine.Take("", "", plan.output.qtyMilli)),
  ): ShopData {
    val byId = d.products.associateBy { it.id }
    val who = user.trim().ifEmpty { null }
    val loc = location?.ifEmpty { null }
    val why = listOf(title, note.trim()).filter { it.isNotEmpty() }.joinToString(" — ")
    val entries = ArrayList<WarehouseEntry>()
    val moves = ArrayList<StockMovement>()
    fun row(l: Line, sign: Int, qtyMilli: Long = l.qtyMilli, lot: ExpiryEngine.Take? = null) {
      val id = newId()
      val units = sign * qtyMilli / 1000.0
      entries += WarehouseEntry(
        id = id, productId = l.productId, units = units, unit = byId[l.productId]?.unit.orEmpty(), price = l.unitMinor / 100.0,
        date = today, notes = why, isAdjustment = true, refId = key, createdAt = now, locationId = loc, userId = who, adjKind = KIND,
        expiryDate = lot?.expiryDate.orEmpty(), batch = lot?.batch.orEmpty(),
      )
      moves += StockMovement(
        id = newId(), productId = l.productId, type = MOVE, qty = units, date = today, notes = why, refId = id,
        createdAt = now, userId = who, reason = why,
      )
    }
    val uses = plan.uses
    val outs = plan.outs
    if (uses != null && outs != null) {
      //  INV-21 (`lots`): یک ردیف برای هر سری با `lotId`، انقضا و سری؛ خروجی با بهای دقیق (`costMinor`). دفتر ردیف‌های یک لحظه
      //  را به ترتیبِ شناسه می‌خواند ⇒ شناسه‌های هر کالا مرتب و به ترتیبِ همان برداشت، تا بهای هسته همان بهای [lotted] باشد.
      val unit = plan.inputs.associate { it.productId to it.unitMinor }
      fun lotRow(id: String, pid: String, qtyMilli: Long, unitMinor: Long, lotId: String?, exp: String, batch: String, cost: Long?) {
        val units = qtyMilli / 1000.0
        entries += WarehouseEntry(
          id = id, productId = pid, units = units, unit = byId[pid]?.unit.orEmpty(), price = unitMinor / 100.0,
          date = today, notes = why, isAdjustment = true, refId = key, createdAt = now, locationId = loc, userId = who, adjKind = KIND,
          expiryDate = exp, batch = batch, lotId = lotId, costMinor = cost,
        )
        moves += StockMovement(
          id = newId(), productId = pid, type = MOVE, qty = units, date = today, notes = why, refId = id,
          createdAt = now, userId = who, reason = why,
        )
      }
      for ((pid, us) in uses.groupBy { it.productId }) {
        val ids = List(us.size) { newId() }.sorted()
        us.forEachIndexed { i, u -> lotRow(ids[i], pid, -u.qtyMilli, unit[pid] ?: 0L, u.lotId.ifEmpty { null }, u.expiryDate, u.batch, null) }
      }
      for (o in outs) {
        val id = newId()
        lotRow(id, plan.output.productId, o.qtyMilli, plan.output.unitMinor, if (o.lotId == OWN) OWN + id else o.lotId.ifEmpty { null }, o.expiryDate, o.batch, o.costMinor)
      }
    } else {
      plan.inputs.forEach { row(it, -1) }
      check(outputs.sumOf { it.qtyMilli } == plan.output.qtyMilli) { "repack outputs" }
      outputs.forEach { row(plan.output, 1, it.qtyMilli, it) }
    }
    fun txt(l: Line) = "${ir.vil3ntec.tohid.qty(l.qtyMilli / 1000.0)} «${byId[l.productId]?.name.orEmpty()}»"
    val audit = AuditEntry(
      id = key, type = type, date = today, refId = key, createdAt = now, by = who,
      notes = "$title: ${plan.inputs.joinToString(" + ") { txt(it) }} ⇐ ${txt(plan.output)}، بهای هر واحد " +  // داده: یادداشتِ سابقهٔ دفتر (ذخیره و همگام می‌شود)
        "${ir.vil3ntec.tohid.money(plan.output.unitMinor / 100.0)} ؋" +
        (if (loc != null) " — ${StockPlaces.label(loc)}" else "") + (if (note.isNotBlank()) " — ${note.trim()}" else "") +
        (if (who != null) " — $who" else ""),
      refIds = entries.map { "warehouseEntries/${it.id}" },
    )
    return d.copy(
      warehouseEntries = d.warehouseEntries + entries,
      stockMovements = d.stockMovements + moves,
      auditLog = d.auditLog + audit,
    )
  }
}
