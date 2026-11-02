package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.stock.StockCore

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

  data class Plan(val inputs: List<Line>, val output: Line)

  sealed interface Planned {
    data class Ok(val plan: Plan) : Planned
    data class Bad(val message: String) : Planned
  }

  fun has(d: ShopData, key: String): Boolean = d.auditLog.any { it.id == key } || d.warehouseEntries.any { it.refId == key }

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
    p == null -> "$role را انتخاب کنید"
    ProductPlansMap.isKit(p) -> "«${p.name}» کیت است و موجودیِ خودش را ندارد"
    p.stockless == true -> "«${p.name}» خدماتی است و موجودی ندارد"
    !p.active -> "«${p.name}» غیرفعال است"
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
      val where = if (location == null) "" else " در «${StockPlaces.label(location)}»"
      return "از «${p?.name.orEmpty()}»$where فقط ${ir.vil3ntec.tohid.qty(maxOf(0L, l.haveMilli) / 1000.0)} ${p?.unit.orEmpty()} هست، " +
        "این کار ${ir.vil3ntec.tohid.qty(l.qtyMilli / 1000.0)} می‌خواهد"
    }
    return null
  }

  /**
   *  شکستنِ [packsMilli] از کالای [fromId] (مثلاً کارتن) به [perPackMilli] × همان، از کالای [toId] (مثلاً دانه).
   *  [location] `null` ⇒ موجودیِ کل (محل‌ها خاموش)، وگرنه موجودیِ همان محل و ردیف‌ها با همان محل.
   */
  fun planBreak(d: ShopData, fromId: String, packsMilli: Long, toId: String, perPackMilli: Long, location: String? = null): Planned {
    val from = d.products.find { it.id == fromId }
    val to = d.products.find { it.id == toId }
    stockProblem(from, "کالای بسته (کارتن)")?.let { return Planned.Bad(it) }
    stockProblem(to, "کالای دانه")?.let { return Planned.Bad(it) }
    if (fromId == toId) return Planned.Bad("کالای دانه باید با کالای بسته فرق کند")
    if (packsMilli <= 0 || packsMilli > MAX_MILLI) return Planned.Bad("تعدادِ بسته درست نیست")
    if (perPackMilli <= 0 || perPackMilli > MAX_MILLI) return Planned.Bad("تعدادِ دانهٔ هر بسته درست نیست")
    val outQ = times(packsMilli, perPackMilli)?.takeIf { it in 1..MAX_MILLI } ?: return Planned.Bad("مقدارِ دانه‌ها درست نیست")
    val stock = location?.let { StockPlaces.stockAt(d) }
    val input = line(d, stock, location, from!!, packsMilli)
    shortage(d, listOf(input), location)?.let { return Planned.Bad(it) }
    return Planned.Ok(Plan(listOf(input), outLine(d, stock, location, to!!, outQ, input.totalMinor)))
  }

  /** ساختِ [countMilli] بسته از کالای [bundleId]؛ [parts] مقدارِ هر جزء برای **یک** بسته */
  fun planBuild(d: ShopData, bundleId: String, countMilli: Long, parts: List<Part>, location: String? = null): Planned {
    val bundle = d.products.find { it.id == bundleId }
    stockProblem(bundle, "کالای بسته")?.let { return Planned.Bad(it) }
    if (countMilli <= 0 || countMilli > MAX_MILLI) return Planned.Bad("تعدادِ بسته درست نیست")
    val clean = parts.filter { it.productId.isNotEmpty() }
    if (clean.isEmpty()) return Planned.Bad("دست‌کم یک جزء بیفزایید")
    if (clean.size > MAX_PARTS) return Planned.Bad("حداکثر $MAX_PARTS جزء")
    if (clean.map { it.productId }.toSet().size != clean.size) return Planned.Bad("یک کالا دو بار در اجزا آمده")
    if (clean.any { it.productId == bundleId }) return Planned.Bad("بسته جزءِ خودش نمی‌شود")
    val stock = location?.let { StockPlaces.stockAt(d) }
    val byId = d.products.associateBy { it.id }
    val inputs = ArrayList<Line>()
    for (pt in clean) {
      val p = byId[pt.productId]
      stockProblem(p, "جزء")?.let { return Planned.Bad(it) }
      if (pt.qtyMilli <= 0 || pt.qtyMilli > MAX_MILLI) return Planned.Bad("مقدارِ «${p!!.name}» درست نیست")
      val need = times(pt.qtyMilli, countMilli)?.takeIf { it in 1..MAX_MILLI } ?: return Planned.Bad("مقدارِ «${p!!.name}» درست نیست")
      inputs += line(d, stock, location, p!!, need)
    }
    shortage(d, inputs, location)?.let { return Planned.Bad(it) }
    val total = inputs.fold(0L) { s, l -> s + l.totalMinor }
    return Planned.Ok(Plan(inputs, outLine(d, stock, location, bundle!!, countMilli, total)))
  }

  fun breakPack(
    d: ShopData, key: String, fromId: String, packsMilli: Long, toId: String, perPackMilli: Long, location: String?,
    note: String, user: String, today: String, now: Long, newId: () -> String,
  ): StockPlaces.Result {
    if (key.isBlank()) return StockPlaces.Result.Failed("شناسهٔ کار خالی است")
    if (has(d, key)) return StockPlaces.Result.Already
    return when (val p = planBreak(d, fromId, packsMilli, toId, perPackMilli, location)) {
      is Planned.Bad -> StockPlaces.Result.Failed(p.message)
      is Planned.Ok -> StockPlaces.Result.Ok(write(d, key, BREAK, "شکستنِ بسته", p.plan, location, note, user, today, now, newId), key)
    }
  }

  fun build(
    d: ShopData, key: String, bundleId: String, countMilli: Long, parts: List<Part>, location: String?,
    note: String, user: String, today: String, now: Long, newId: () -> String,
  ): StockPlaces.Result {
    if (key.isBlank()) return StockPlaces.Result.Failed("شناسهٔ کار خالی است")
    if (has(d, key)) return StockPlaces.Result.Already
    return when (val p = planBuild(d, bundleId, countMilli, parts, location)) {
      is Planned.Bad -> StockPlaces.Result.Failed(p.message)
      is Planned.Ok -> StockPlaces.Result.Ok(write(d, key, BUILD, "ساختِ بسته", p.plan, location, note, user, today, now, newId), key)
    }
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
  ): ShopData {
    val byId = d.products.associateBy { it.id }
    val who = user.trim().ifEmpty { null }
    val loc = location?.ifEmpty { null }
    val why = listOf(title, note.trim()).filter { it.isNotEmpty() }.joinToString(" — ")
    val entries = ArrayList<WarehouseEntry>()
    val moves = ArrayList<StockMovement>()
    fun row(l: Line, sign: Int) {
      val id = newId()
      val units = sign * l.qtyMilli / 1000.0
      entries += WarehouseEntry(
        id = id, productId = l.productId, units = units, unit = byId[l.productId]?.unit.orEmpty(), price = l.unitMinor / 100.0,
        date = today, notes = why, isAdjustment = true, refId = key, createdAt = now, locationId = loc, userId = who, adjKind = KIND,
      )
      moves += StockMovement(
        id = newId(), productId = l.productId, type = MOVE, qty = units, date = today, notes = why, refId = id,
        createdAt = now, userId = who, reason = why,
      )
    }
    plan.inputs.forEach { row(it, -1) }
    row(plan.output, 1)
    fun txt(l: Line) = "${ir.vil3ntec.tohid.qty(l.qtyMilli / 1000.0)} «${byId[l.productId]?.name.orEmpty()}»"
    val audit = AuditEntry(
      id = key, type = type, date = today, refId = key, createdAt = now, by = who,
      notes = "$title: ${plan.inputs.joinToString(" + ") { txt(it) }} ⇐ ${txt(plan.output)}، بهای هر واحد " +
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
