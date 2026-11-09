package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.i18n.StrGoods
import ir.vil3ntec.tohid.core.stock.Pick
import ir.vil3ntec.tohid.core.stock.StockCore

/**
 *  INV-21 — **دستورِ ساختِ ثبت‌شده** (BOM): برای هر کالای ساخته‌شده، اجزا و مقدارِ هر جزء برای **یک** واحد (هزارم) و اتلافِ
 *  پیش‌بینی‌شده (یک‌دهم‌هزارم، روی مصرفِ هر جزء). «ساختن» از دستور همان سندِ تولیدِ `Repack` را با سری و بهای میانگین پر می‌کند.
 *
 *  ── ثبت، بی جدولِ تازه ───────────────────────────────────────────────
 *  هر دستور یک ردیفِ `auditLog` (`type = stock_bom`، `refId` = کالا، `bom` = [BomDoc]) — همان راهِ همگام‌سازی، ادغام و
 *  خروجیِ صاحب (`OwnerExport.HEADERS` ‹`bom`›). ویرایش = ردیفِ تازه (آخرین ردیفِ هر کالا به `createdAt` و شناسه)؛ برداشتن =
 *  ردیفی با فهرستِ خالی. ⛔ سندِ ساختِ پیشین مقدارها و بهای خودش را دارد و به دستور اشاره نمی‌کند ⇒ ویرایشِ دستور هیچ سندِ
 *  گذشته را عوض نمی‌کند.
 *
 *  ── مقدار و بها ──────────────────────────────────────────────────────
 *  مصرفِ هر جزء برای n واحد = گردِ هسته (`mulDivRound`) ِ مقدار × (۱ + اتلاف) × n، به هزارم. پیش از ثبت موجودیِ همان محل
 *  سنجیده می‌شود (کمبود ⇒ پیامِ روشن، هیچ ردیف). بهای واحدِ ساخته‌شده = جمعِ بهای میانگینِ اجزا ÷ n (هستهٔ `StockCore`، جمع
 *  دقیقاً همان). کار و سربار در هسته نیست ⇒ افزوده نمی‌شود.
 *  ⛔ پشتِ «دستورِ ساخت و سریِ تولید» (`StockPlacesPref.bom`، پیش‌فرض خاموش). منطقِ خالص؛ آزمون: `RepackBomTest`.
 */
object Bom {

  const val TYPE = "stock_bom"
  /** سقفِ اتلاف: ٪۵۰ */
  const val MAX_LOSS_BP = 5_000
  private const val BP = 10_000L

  data class Recipe(val id: String, val productId: String, val parts: List<Repack.Part>, val lossBp: Int, val createdAt: Long)

  fun has(d: ShopData, key: String): Boolean = d.auditLog.any { it.id == key }

  /** آخرین ردیفِ هر کالا؛ برداشته‌شده (فهرستِ خالی) نیست */
  fun all(d: ShopData): Map<String, Recipe> {
    val last = HashMap<String, AuditEntry>()
    for (a in d.auditLog) {
      if (a.type != TYPE) continue
      val pid = a.refId?.takeIf { it.isNotEmpty() } ?: continue
      val b = last[pid]
      if (b == null || a.createdAt > b.createdAt || (a.createdAt == b.createdAt && a.id > b.id)) last[pid] = a
    }
    return last.mapNotNull { (pid, a) ->
      val doc = a.bom ?: return@mapNotNull null
      if (doc.parts.isEmpty()) null else pid to Recipe(a.id, pid, doc.parts.map { Repack.Part(it.productId, it.qtyMilli) }, doc.lossBp, a.createdAt)
    }.toMap()
  }

  fun of(d: ShopData, productId: String): Recipe? = all(d)[productId]

  /** مصرفِ کلِ یک جزء برای [countMilli] واحد، با اتلاف (گردِ هسته) */
  fun need(qtyMilli: Long, lossBp: Int, countMilli: Long): Long = StockCore.mulDivRound(qtyMilli * (BP + lossBp), countMilli, BP * 1000)

  /** ایرادِ دستور یا null — همان نگهبان‌های ساختِ بسته (`Repack.planBuild`) */
  private fun problem(d: ShopData, productId: String, parts: List<Repack.Part>, lossBp: Int): String? {
    val byId = d.products.associateBy { it.id }
    val p = byId[productId]
    when {
      p == null -> return StrGoods.pickRole(StrGoods.packedItem)
      ProductPlansMap.isKit(p) -> return StrGoods.isKit(p.name)
      p.stockless == true -> return StrGoods.isService(p.name)
    }
    if (parts.isEmpty()) return StrGoods.addOnePart
    if (parts.size > Repack.MAX_PARTS) return StrGoods.maxParts(Repack.MAX_PARTS)
    if (parts.map { it.productId }.toSet().size != parts.size) return StrGoods.partTwice
    if (parts.any { it.productId == productId }) return StrGoods.packSelfPart
    for (pt in parts) {
      val c = byId[pt.productId] ?: return StrGoods.pickRole(StrGoods.part)
      if (ProductPlansMap.isKit(c)) return StrGoods.isKit(c.name)
      if (c.stockless == true) return StrGoods.isService(c.name)
      if (pt.qtyMilli <= 0 || pt.qtyMilli > Repack.MAX_MILLI) return ir.vil3ntec.tohid.core.i18n.StrLedger.qtyOfBad(c.name)
    }
    if (lossBp !in 0..MAX_LOSS_BP) return StrGoods.bomLossBad(ir.vil3ntec.tohid.qty(MAX_LOSS_BP / 100.0))
    return null
  }

  /** ثبت یا ویرایشِ دستورِ ساختِ [productId] — یک ردیفِ تازه، یک بار برای هر کلید */
  fun save(d: ShopData, key: String, productId: String, parts: List<Repack.Part>, lossBp: Int, user: String, today: String, now: Long): StockPlaces.Result {
    if (key.isBlank()) return StockPlaces.Result.Failed(StrGoods.opIdEmpty)
    if (has(d, key)) return StockPlaces.Result.Already
    val clean = parts.filter { it.productId.isNotEmpty() }
    problem(d, productId, clean, lossBp)?.let { return StockPlaces.Result.Failed(it) }
    val names = d.products.associate { it.id to it.name }
    val text = "دستورِ ساختِ «${names[productId].orEmpty()}»: " +  // داده: یادداشتِ سابقهٔ دفتر (ذخیره و همگام می‌شود)
      clean.joinToString(" + ") { "${ir.vil3ntec.tohid.qty(it.qtyMilli / 1000.0)} «${names[it.productId].orEmpty()}»" } +
      (if (lossBp > 0) "، اتلاف ٪${ir.vil3ntec.tohid.qty(lossBp / 100.0)}" else "")  // داده: یادداشتِ سابقهٔ دفتر
    return StockPlaces.Result.Ok(d.copy(auditLog = d.auditLog + row(key, productId, BomDoc(clean.map { BomPart(it.productId, it.qtyMilli) }, lossBp), text, user, today, now)), key)
  }

  /** برداشتنِ دستورِ ساختِ [productId] — ردیفی با فهرستِ خالی؛ سندهای ساختِ پیشین دست نمی‌خورند */
  fun remove(d: ShopData, key: String, productId: String, user: String, today: String, now: Long): StockPlaces.Result {
    if (key.isBlank()) return StockPlaces.Result.Failed(StrGoods.opIdEmpty)
    if (has(d, key)) return StockPlaces.Result.Already
    if (of(d, productId) == null) return StockPlaces.Result.Failed(StrGoods.noBom(d.products.find { it.id == productId }?.name.orEmpty()))
    val text = "برداشتنِ دستورِ ساختِ «${d.products.find { it.id == productId }?.name.orEmpty()}»"  // داده: یادداشتِ سابقهٔ دفتر
    return StockPlaces.Result.Ok(d.copy(auditLog = d.auditLog + row(key, productId, BomDoc(), text, user, today, now)), key)
  }

  private fun row(key: String, productId: String, doc: BomDoc, notes: String, user: String, today: String, now: Long) = AuditEntry(
    id = key, type = TYPE, date = today, refId = productId, createdAt = now, by = user.trim().ifEmpty { null }, notes = notes, bom = doc,
  )

  /**
   *  ساختِ [countMilli] واحد از دستورِ ثبت‌شدهٔ [productId] در [location] (`null` ⇒ موجودیِ کل): همان `Repack.planWith` با مصرفِ
   *  [need] و همیشه با سری و بهای میانگین (`lots`). نبودِ دستور، کمبود (کل یا همان محل) یا ورودیِ نادرست ⇒ رد.
   */
  fun plan(d: ShopData, productId: String, countMilli: Long, location: String? = null, chosen: Map<String, List<Pick>> = emptyMap()): Repack.Planned {
    val r = of(d, productId) ?: return Repack.Planned.Bad(StrGoods.noBom(d.products.find { it.id == productId }?.name.orEmpty()))
    return planParts(d, productId, countMilli, r.parts, r.lossBp, location, chosen)
  }

  /** همان [plan] با اجزا و اتلافِ فرم (دستورِ پرشده و سپس دست‌خورده، یا بی دستور) */
  fun planParts(
    d: ShopData, productId: String, countMilli: Long, parts: List<Repack.Part>, lossBp: Int, location: String? = null,
    chosen: Map<String, List<Pick>> = emptyMap(),
  ): Repack.Planned {
    if (countMilli <= 0 || countMilli > Repack.MAX_MILLI) return Repack.Planned.Bad(StrGoods.packCountBad)
    if (lossBp !in 0..MAX_LOSS_BP) return Repack.Planned.Bad(StrGoods.bomLossBad(ir.vil3ntec.tohid.qty(MAX_LOSS_BP / 100.0)))
    return Repack.planWith(d, productId, countMilli, parts, location, lots = true, chosen = chosen) { need(it, lossBp, countMilli) }
  }

  /** ساختن با اجزا و اتلافِ فرم — همان سندِ تولیدِ [build] */
  fun buildParts(
    d: ShopData, key: String, productId: String, countMilli: Long, parts: List<Repack.Part>, lossBp: Int, location: String?,
    note: String, user: String, today: String, now: Long, newId: () -> String,
  ): StockPlaces.Result {
    if (key.isBlank()) return StockPlaces.Result.Failed(StrGoods.opIdEmpty)
    if (Repack.has(d, key)) return StockPlaces.Result.Already
    return Repack.built(d, key, planParts(d, productId, countMilli, parts, lossBp, location), location, note, user, today, now, newId)
  }

  fun build(
    d: ShopData, key: String, productId: String, countMilli: Long, location: String?,
    note: String, user: String, today: String, now: Long, newId: () -> String,
    chosen: Map<String, List<Pick>> = emptyMap(),
  ): StockPlaces.Result {
    if (key.isBlank()) return StockPlaces.Result.Failed(StrGoods.opIdEmpty)
    if (Repack.has(d, key)) return StockPlaces.Result.Already
    return Repack.built(d, key, plan(d, productId, countMilli, location, chosen), location, note, user, today, now, newId)
  }
}
