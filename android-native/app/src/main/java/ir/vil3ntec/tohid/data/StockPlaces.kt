package ir.vil3ntec.tohid.data

import kotlin.math.abs

/**
 *  INV-20 — محل‌های نگه‌داری داخلِ دکان (قفسه، انبارِ پشتی، …) و انتقال بین آن‌ها، **روی موتورِ امروز**
 *  (ورودی منهای فروش، `ShopStore.stock`) و بی جدولِ تازه:
 *   • محل            ⇒ `auditLog` با `type = stock_place`، `refId` = نامِ محل، `reason` = `add` | `archive`
 *   • موجودیِ هر محل ⇒ ورودِ انبار با `locationId` + انتقال‌ها − فروش (فروشِ بی محل از محلِ اصلی)؛
 *                       جمعِ محل‌های یک کالا دقیقاً همان موجودیِ امروزِ آن کالاست
 *   • انتقال         ⇒ همان دو ردیفِ جمعِ صفرِ `stockMovements` ِ «دفترِ موجودی» (`StockActions.transferRows`)
 *                       که موتورِ امروز نمی‌بیندش و `StockLedger`/سرورِ v3 جمعش را صفر می‌خوانند ⇒ موجودیِ کل عوض نمی‌شود.
 *
 *  ⛔ هر نوشتن یک کلید دارد ([has]): همان شناسهٔ ردیفِ تازه که پنجره هنگامِ باز شدن می‌سازد — دو ضربه یا تکرار
 *  پس از قطع یک کار (`StockPlacesPref.record` ⇐ `AppendOnce`). همهٔ ردیف‌های یک کار در یک دفترِ تازه‌اند: یا همه یا هیچ.
 *  ⛔ پشتِ تنظیمِ دستگاه (`StockPlacesPref`، پیش‌فرض خاموش) — خاموش ⇒ هیچ ردیفی، هیچ عددِ دیگری (`StockPlacesOffTest`).
 *  منطقِ خالص؛ آزمون: `StockPlacesTest`.
 */
object StockPlaces {

  const val PLACE_TYPE = "stock_place"
  const val ADD = "add"
  const val ARCHIVE = "archive"
  const val MAX_NAME = 40

  sealed interface Result {
    data class Ok(val data: ShopData, val id: String) : Result
    /** همین کلید پیش‌تر ثبت شده بود — هیچ نوشته نشد */
    data object Already : Result
    data class Failed(val message: String) : Result
  }

  fun label(loc: String): String = StockActions.locationLabel(loc)

  /** نامِ تمیزِ محل: فاصله‌های پشتِ‌هم یکی؛ نامِ نمایشیِ محلِ اصلی ⇒ "" */
  fun clean(raw: String): String = StockActions.locationOf(raw.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" "))

  /* ------------------------------------------------------------ محل‌ها */

  /** محل‌های ثبت‌شده و بایگانی‌نشده (آخرین ردیفِ هر نام تصمیم می‌گیرد) */
  fun registered(d: ShopData): List<String> {
    val last = HashMap<String, AuditEntry>()
    for (a in d.auditLog) {
      if (a.type != PLACE_TYPE) continue
      val name = a.refId?.takeIf { it.isNotEmpty() } ?: continue
      val b = last[name]
      if (b == null || a.createdAt > b.createdAt || (a.createdAt == b.createdAt && a.id > b.id)) last[name] = a
    }
    return last.values.filter { it.reason == ADD }.mapNotNull { it.refId }.sorted()
  }

  /** محلِ اصلی + محل‌های ثبت‌شده + هر محلی که هنوز موجودی دارد (حتی بایگانی‌شده یا ثبت‌شده با نسخهٔ پیشین) */
  fun places(d: ShopData, stock: Map<String, Map<String, Long>> = stockAt(d)): List<String> {
    val set = HashSet<String>(registered(d))
    for (byLoc in stock.values) for ((loc, q) in byLoc) if (q != 0L) set += loc
    set.remove(StockLedger.MAIN)
    return listOf(StockLedger.MAIN) + set.sorted()
  }

  fun hasPlace(d: ShopData, key: String): Boolean = d.auditLog.any { it.id == key }

  fun addPlace(d: ShopData, key: String, raw: String, user: String, today: String, now: Long): Result {
    if (key.isBlank()) return Result.Failed("شناسهٔ کار خالی است")
    if (hasPlace(d, key)) return Result.Already
    val name = clean(raw)
    if (name.isEmpty()) return Result.Failed("نامِ محل را بنویسید")
    if (name.length > MAX_NAME) return Result.Failed("نامِ محل حداکثر $MAX_NAME حرف است")
    if (registered(d).any { it.equals(name, ignoreCase = true) }) return Result.Failed("«$name» از پیش هست")
    return Result.Ok(d.copy(auditLog = d.auditLog + placeRow(key, name, ADD, user, today, now, "محلِ نگه‌داریِ «$name» افزوده شد")), key)
  }

  /** بایگانی فقط وقتی هیچ کالایی در آن محل نمانده؛ ردیف‌های گذشته دست نمی‌خورند */
  fun archivePlace(d: ShopData, key: String, name: String, user: String, today: String, now: Long): Result {
    if (key.isBlank()) return Result.Failed("شناسهٔ کار خالی است")
    if (hasPlace(d, key)) return Result.Already
    if (name !in registered(d)) return Result.Failed("این محل در فهرست نیست")
    val left = stockAt(d).values.count { (it[name] ?: 0L) != 0L }
    if (left > 0) return Result.Failed("در «$name» هنوز $left کالا هست — اول جابه‌جا کنید")
    return Result.Ok(d.copy(auditLog = d.auditLog + placeRow(key, name, ARCHIVE, user, today, now, "محلِ نگه‌داریِ «$name» بایگانی شد")), key)
  }

  private fun placeRow(id: String, name: String, op: String, user: String, today: String, now: Long, note: String) = AuditEntry(
    id = id, type = PLACE_TYPE, date = today, refId = name, createdAt = now, reason = op,
    by = user.trim().ifEmpty { null }, notes = note + (if (user.isNotBlank()) " — ${user.trim()}" else ""),
  )

  /* ---------------------------------------------------- موجودیِ هر محل */

  /**
   *  کالا ⇒ (محل ⇒ هزارم). همان حسابِ `ShopStore.StockIndex` جدا به محل: کیت موجودیِ خودش را ندارد (ورود و
   *  فروشش به اجزا پخش می‌شود)، فروشِ لغوشده و مرجوعی کم نمی‌شوند. انتقال فقط از ردیفِ خروجی (یا تک‌ردیفِ
   *  قدیمی) خوانده می‌شود — همان قاعدهٔ `StockLedger.events`.
   */
  fun stockAt(d: ShopData): Map<String, Map<String, Long>> {
    val kits: Map<String, List<KitPart>> = d.products.mapNotNull { p -> p.kit?.ifEmpty { null }?.let { p.id to it } }.toMap()
    val out = HashMap<String, HashMap<String, Long>>()
    fun put(pid: String, loc: String, q: Long) {
      if (pid.isEmpty() || q == 0L || pid in kits) return
      val m = out.getOrPut(pid) { HashMap() }
      m[loc] = (m[loc] ?: 0L) + q
    }
    for (e in d.warehouseEntries) {
      val loc = e.locationId.orEmpty()
      val k = kits[e.productId]
      if (k != null) k.forEach { put(it.productId, loc, StockLedger.milli(e.units * it.qty)) } else put(e.productId, loc, StockLedger.milli(e.units))
    }
    val cancelled = d.sales.filter { it.status == "cancelled" }.mapTo(HashSet()) { it.id }
    for (si in d.saleItems) {
      if (si.saleId in cancelled) continue
      val loc = si.locationId.orEmpty()
      val q = si.quantity - si.returnedQty
      put(si.productId, loc, -StockLedger.milli(q))
      si.parts?.forEach { put(it.productId, loc, -StockLedger.milli(q * it.qty)) }
    }
    for (m in d.stockMovements) {
      if (m.type != StockLedger.TRANSFER_OUT && m.type != StockLedger.TRANSFER_LEGACY) continue
      val to = m.toLocationId ?: continue
      val from = m.locationId.orEmpty()
      val q = abs(StockLedger.milli(m.qty))
      if (q == 0L || from == to) continue
      put(m.productId, from, -q)
      put(m.productId, to, q)
    }
    return out
  }

  fun at(stock: Map<String, Map<String, Long>>, productId: String, location: String): Long = stock[productId]?.get(location) ?: 0L

  /* ------------------------------------------------------------ انتقال */

  /** کلیدِ انتقال همان شناسهٔ ردیفِ خروجی (و `refId` ِ هر دو ردیف) است */
  fun has(d: ShopData, key: String): Boolean = d.stockMovements.any { it.id == key || it.refId == key }

  fun transfer(
    d: ShopData,
    key: String,
    productId: String,
    from: String,
    to: String,
    qtyMilli: Long,
    reason: String,
    user: String,
    today: String,
    now: Long,
    newId: () -> String,
  ): Result {
    if (key.isBlank()) return Result.Failed("شناسهٔ کار خالی است")
    if (has(d, key)) return Result.Already
    val p = d.products.find { it.id == productId } ?: return Result.Failed("کالا را انتخاب کنید")
    if (ProductPlansMap.isKit(p)) return Result.Failed("«${p.name}» کیت است و موجودیِ خودش را ندارد")
    if (p.stockless == true) return Result.Failed("«${p.name}» خدماتی است و موجودی ندارد")
    val src = clean(from)
    val dest = clean(to)
    if (src == dest) return Result.Failed("مقصد باید با مبدأ فرق کند")
    if (dest.length > MAX_NAME) return Result.Failed("نامِ محل حداکثر $MAX_NAME حرف است")
    if (qtyMilli <= 0L) return Result.Failed("مقدار درست نیست")
    //  همان نگهبانِ `adjustStock`: مبدأ منفی نمی‌شود
    val have = at(stockAt(d), productId, src)
    if (qtyMilli > have) {
      return Result.Failed("در «${label(src)}» فقط ${ir.vil3ntec.tohid.qty(maxOf(0L, have) / 1000.0)} ${p.unit} هست")
    }
    return Result.Ok(StockActions.transferRows(d, p, src, dest, qtyMilli, null, reason, user, today, now, key, newId), key)
  }
}
