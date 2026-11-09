package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.i18n.StrGoods
import ir.vil3ntec.tohid.core.stock.Lot
import ir.vil3ntec.tohid.core.stock.Pick
import ir.vil3ntec.tohid.core.stock.PickResult
import ir.vil3ntec.tohid.core.stock.StockBook
import ir.vil3ntec.tohid.core.stock.StockCore
import ir.vil3ntec.tohid.core.stock.StockDoc
import ir.vil3ntec.tohid.core.stock.StockMode
import kotlin.math.abs

/**
 *  INV-20 — محل‌های نگه‌داری داخلِ دکان (قفسه، انبارِ پشتی، …) و انتقال بین آن‌ها، **روی موتورِ امروز**
 *  (ورودی منهای فروش، `ShopStore.stock`) و بی جدولِ تازه:
 *   • محل            ⇒ `auditLog` با `type = stock_place`، `refId` = نامِ محل، `reason` = `add` | `archive`
 *   • موجودیِ هر محل ⇒ ورودِ انبار با `locationId` + انتقال‌ها − فروش (فروشِ بی محل از محلِ اصلی)؛
 *                       جمعِ محل‌های یک کالا دقیقاً همان موجودیِ امروزِ آن کالاست
 *   • انتقال         ⇒ همان دو ردیفِ جمعِ صفرِ `stockMovements` ِ «دفترِ موجودی» (`StockActions.transferRows`)
 *                       که موتورِ امروز نمی‌بیندش و `StockLedger`/سرورِ v3 جمعش را صفر می‌خوانند ⇒ موجودیِ کل عوض نمی‌شود.
 *   • سری و انقضا    ⇒ [lotsAt] (همان هسته و رویدادهای موتورِ تازه، `StockLedger.placeBooks`): مبدأیی که سری دارد یک جفت برای هر
 *                       سری می‌نویسد (`lotId`، برگزیده یا FEFO)، سری با همان انقضا به مقصد می‌رود؛ مبدأِ بی سری همان یک جفتِ امروز.
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
    if (key.isBlank()) return Result.Failed(StrGoods.opIdEmpty)
    if (hasPlace(d, key)) return Result.Already
    val name = clean(raw)
    if (name.isEmpty()) return Result.Failed(StrGoods.placeNameNeeded)
    if (name.length > MAX_NAME) return Result.Failed(StrGoods.placeNameTooLong(MAX_NAME))
    if (registered(d).any { it.equals(name, ignoreCase = true) }) return Result.Failed(StrGoods.placeExists(name))
    return Result.Ok(d.copy(auditLog = d.auditLog + placeRow(key, name, ADD, user, today, now, "محلِ نگه‌داریِ «$name» افزوده شد")), key)  // داده: یادداشتِ سابقهٔ دفتر (ذخیره و همگام می‌شود)
  }

  /** بایگانی فقط وقتی هیچ کالایی در آن محل نمانده؛ ردیف‌های گذشته دست نمی‌خورند */
  fun archivePlace(d: ShopData, key: String, name: String, user: String, today: String, now: Long): Result {
    if (key.isBlank()) return Result.Failed(StrGoods.opIdEmpty)
    if (hasPlace(d, key)) return Result.Already
    if (name !in registered(d)) return Result.Failed(StrGoods.placeNotListed)
    val left = stockAt(d).values.count { (it[name] ?: 0L) != 0L }
    if (left > 0) return Result.Failed(StrGoods.placeNotEmpty(name, left))
    return Result.Ok(d.copy(auditLog = d.auditLog + placeRow(key, name, ARCHIVE, user, today, now, "محلِ نگه‌داریِ «$name» بایگانی شد")), key)  // داده: یادداشتِ سابقهٔ دفتر (ذخیره و همگام می‌شود)
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

  /* ------------------------------------------------- سری و انقضای هر محل */

  /**
   *  INV-20 — سری‌های یک کالا در هر محل (محل ⇒ سری‌های غیرِ صفر به ترتیبِ FEFO؛ سریِ "" = بی سری).
   */
  fun lotsAt(d: ShopData, productId: String, stock: Map<String, Map<String, Long>> = stockAt(d)): Map<String, List<Lot>> =
    lotsAt(d, setOf(productId), stock)[productId].orEmpty()

  /**
   *  همان برای چند کالا با یک بار خواندنِ دفتر: همان رویدادها و همان هستهٔ موتورِ تازه (`StockLedger.placeBooks` ⇐
   *  `StockCore`: FEFO، سریِ انتقال با همان انقضا به مقصد، فقط سریِ "" منفی) با قاعدهٔ امروزِ محل — فروشِ هر قلم از محلِ
   *  خودش، بی پخش — و جمعِ هر محل در پایان دقیقاً [stock] (`stockAt`؛ اجزای کیت و هر چه آن دفتر نمی‌بیند): کم ⇒ FEFO
   *  از همان محل، زیاد ⇒ بی سری. فقط‌خواندنی؛ نمای موتورِ تازه (`StockLedger.replay`) دست نمی‌خورد.
   */
  fun lotsAt(d: ShopData, productIds: Set<String>, stock: Map<String, Map<String, Long>> = stockAt(d)): Map<String, Map<String, List<Lot>>> =
    booksAt(d, productIds, stock).mapValues { (_, book) ->
      StockCore.positions(book).associate { StockLedger.locOf(it.warehouseId) to it.fefoLots }.filterValues { it.isNotEmpty() }
    }

  /** همان دفترهای [lotsAt] (هر کالا، هر محل: مقدار، ارزش و سری‌ها) — بهای میانگینِ تولید (INV-21، `Repack`) هم از همین‌هاست */
  internal fun booksAt(d: ShopData, productIds: Set<String>, stock: Map<String, Map<String, Long>> = stockAt(d)): Map<String, StockBook> {
    if (productIds.isEmpty()) return emptyMap()
    val books = StockLedger.placeBooks(d, productIds)
    val out = HashMap<String, StockBook>()
    for (pid in productIds) {
      var book = books[pid] ?: StockBook()
      val want = stock[pid].orEmpty()
      val have = StockCore.positions(book).associate { StockLedger.locOf(it.warehouseId) to it.qtyMilli }
      for (loc in (want.keys + have.keys).toSortedSet()) {
        val diff = (want[loc] ?: 0L) - (have[loc] ?: 0L)
        if (diff == 0L) continue
        val fix = StockDoc(
          id = "place:$loc", type = "adjustment", productId = pid, warehouseId = StockLedger.wid(loc), userId = StockLedger.LEGACY_USER,
          at = 1L, qtyMilli = diff, reason = "place",
        )
        val r = StockCore.apply(book, fix, StockMode.RECORDED, null)
        if (r.result.ok) book = r.book
      }
      out[pid] = book
    }
    return out
  }

  /** FEFO ِ [qtyMilli] از سری‌های یک محل — همان `StockCore.pickFefo` ِ `transfer_out` ِ هسته (منقضی هم، بی سری آخر) */
  fun fefo(lots: List<Lot>, qtyMilli: Long): PickResult = StockCore.pickFefo(lots.filter { it.qtyMilli > 0 }, qtyMilli, null, includeExpired = true)

  internal sealed interface Picked {
    /** مبدأ هیچ سری‌ای ندارد ⇒ همان یک جفتِ بی سریِ امروز */
    data object Plain : Picked
    data class Lots(val picks: List<Pick>) : Picked
    data class Bad(val message: String) : Picked
  }

  /**
   *  سری‌های این انتقال: [chosen] `null` ⇒ FEFO (همان `StockCore.pickFefo` ِ `transfer_out` ِ هسته، منقضی هم، بی سری آخر)؛
   *  وگرنه همان سری‌ها و مقدارها — هیچ سری‌ای بیش از ماندهٔ خودش در مبدأ نه، جمع همان [qtyMilli]. بی سری (`""`) فقط وقتی
   *  همهٔ سری‌های مبدأ کامل برداشته شده‌اند، چون ردیفِ بی `lotId` را دفتر به FEFO می‌خواند.
   */
  private fun pick(d: ShopData, stock: Map<String, Map<String, Long>>, p: Product, src: String, qtyMilli: Long, chosen: List<Pick>?): Picked =
    pickFrom(lotsAt(d, p.id, stock)[src].orEmpty(), p, src, qtyMilli, chosen)

  /** همان [pick] روی سری‌های داده‌شدهٔ یک محل — تولید (INV-21، `Repack`) همان قاعده را برای هر جزء می‌خواند */
  internal fun pickFrom(lots: List<Lot>, p: Product, src: String, qtyMilli: Long, chosen: List<Pick>?): Picked {
    val pool = lots.filter { it.qtyMilli > 0 }
    if (pool.none { it.lotId.isNotEmpty() }) {
      return if (chosen == null || chosen.all { it.lotId.isEmpty() }) Picked.Plain else Picked.Bad(StrGoods.transferImpossible)
    }
    if (chosen == null) {
      val r = fefo(pool, qtyMilli)
      return if (r.shortMilli > 0) Picked.Bad(StrGoods.onlyAt(label(src), ir.vil3ntec.tohid.qty(pool.sumOf { it.qtyMilli } / 1000.0), p.unit)) else Picked.Lots(r.picks)
    }
    val want = LinkedHashMap<String, Long>()
    for (c in chosen) {
      if (c.qtyMilli <= 0L) return Picked.Bad(StrGoods.qtyBad)
      want[c.lotId] = (want[c.lotId] ?: 0L) + c.qtyMilli
    }
    if (want.values.sum() != qtyMilli) return Picked.Bad(StrGoods.qtyBad)
    for ((lot, q) in want) {
      val have = pool.find { it.lotId == lot }?.qtyMilli ?: 0L
      if (q > have) return Picked.Bad(StrGoods.lotOnlyAt(StockActions.lotLabel(lot), label(src), ir.vil3ntec.tohid.qty(have / 1000.0), p.unit))
    }
    if (want.containsKey("") && pool.any { it.lotId.isNotEmpty() && (want[it.lotId] ?: 0L) < it.qtyMilli }) return Picked.Bad(StrGoods.lotlessLast)
    //  به ترتیبِ FEFO ِ مبدأ
    return Picked.Lots(pool.mapNotNull { l -> want[l.lotId]?.let { Pick(l.lotId, it) } })
  }

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
    /** سری‌های برگزیده (سری، هزارم)؛ `null` ⇒ FEFO */
    lots: List<Pick>? = null,
  ): Result {
    if (key.isBlank()) return Result.Failed(StrGoods.opIdEmpty)
    if (has(d, key)) return Result.Already
    val p = d.products.find { it.id == productId } ?: return Result.Failed(StrGoods.pickItem)
    if (ProductPlansMap.isKit(p)) return Result.Failed(StrGoods.isKit(p.name))
    if (p.stockless == true) return Result.Failed(StrGoods.isService(p.name))
    val src = clean(from)
    val dest = clean(to)
    if (src == dest) return Result.Failed(StrGoods.destSame)
    if (dest.length > MAX_NAME) return Result.Failed(StrGoods.placeNameTooLong(MAX_NAME))
    if (qtyMilli <= 0L) return Result.Failed(StrGoods.qtyBad)
    //  همان نگهبانِ `adjustStock`: مبدأ منفی نمی‌شود
    val stock = stockAt(d)
    val have = at(stock, productId, src)
    if (qtyMilli > have) {
      return Result.Failed(StrGoods.onlyAt(label(src), ir.vil3ntec.tohid.qty(maxOf(0L, have) / 1000.0), p.unit))
    }
    //  هیچ سری‌ای منفی نمی‌شود؛ یک جفت برای هر سری، همه با `refId` = کلید، نخستین با شناسهٔ همان کلید
    return when (val got = pick(d, stock, p, src, qtyMilli, lots)) {
      Picked.Plain -> Result.Ok(StockActions.transferRows(d, p, src, dest, qtyMilli, null, reason, user, today, now, key, newId), key)
      is Picked.Bad -> Result.Failed(got.message)
      is Picked.Lots -> {
        var out = d
        got.picks.forEachIndexed { i, pk ->
          out = StockActions.transferRows(out, p, src, dest, pk.qtyMilli, pk.lotId, reason, user, today, now, if (i == 0) key else newId(), newId, ref = key)
        }
        Result.Ok(out, key)
      }
    }
  }
}

/** موجودیِ یک کالا در یک محل — `milli` هزارمِ واحدِ موجودی (منفی هم می‌شود: فروش از محلی بیش از ورودش) */
data class PlaceQty(val place: String, val label: String, val milli: Long)

/**
 *  POS-16 — «موجودی در محل‌ها» برای صندوق: **فقط خواندنی** و همان حسابِ [StockPlaces.stockAt] (ورود با محل + انتقال − فروش؛
 *  جمعِ محل‌ها دقیقاً موجودیِ امروزِ کالا) — صفحه‌ها خودشان به `StockPlaces` دست نمی‌زنند (`StockPlacesOffTest`). فقط وقتی
 *  دست‌کم یک محلِ **دیگر** جز محلِ اصلی برای این کالا عدد دارد، وگرنه چیزی بیش از موجودیِ همیشگی نیست. کیت و خدماتی موجودیِ
 *  خودشان را ندارند ⇒ هیچ. موتورِ تازهٔ موجودی (`StockLedger.isOn`) محل‌ها را در «دفترِ موجودی» دارد ⇒ هیچ (همان دروازهٔ
 *  «محل‌ها و انتقال» در `StockExtras`). ترتیب: محلِ اصلی، سپس به نام. شعبه در داده نیست (فقط محل‌های همین دکان).
 *  صدازننده فقط با «محل‌های نگه‌داری» ِ روشنِ همین دستگاه (`StockPlacesPref.places`) می‌خواند؛ آزمون: `LinePlacesTest`.
 */
object PlaceStock {
  /**
   *  INV-20 — گزارشِ انقضا: هر محموله ([ExpiryEngine.Lot]، شناسهٔ ردیفِ انبار) ⇒ نامِ محل‌هایی که سریِ همان محموله حالا
   *  در آن‌ها مانده (همان [StockPlaces.lotsAt]، پس پس از انتقال محلِ تازه). فقط‌خواندنی و فقط وقتی دست‌کم یک سریِ همین
   *  محموله‌ها بیرون از محلِ اصلی است؛ موتورِ تازه روشن ⇒ هیچ (همان دروازهٔ [of]). عددِ محموله همان `ExpiryEngine` می‌ماند.
   */
  fun lotPlaces(d: ShopData, lots: List<ExpiryEngine.Lot>): Map<String, List<String>> {
    if (lots.isEmpty() || StockLedger.isOn(d)) return emptyMap()
    val byPid = StockPlaces.lotsAt(d, lots.mapTo(HashSet()) { it.product.id })
    if (byPid.values.none { m -> m.any { (loc, ls) -> loc != StockLedger.MAIN && ls.any { it.lotId.isNotEmpty() && it.qtyMilli > 0 } } }) return emptyMap()
    val out = HashMap<String, List<String>>()
    for (l in lots) {
      val id = StockLedger.lotRefOf(l.entry)?.lotId.orEmpty()
      if (id.isEmpty()) continue
      val at = byPid[l.product.id].orEmpty().entries
        .filter { (_, ls) -> ls.any { it.lotId == id && it.qtyMilli > 0 } }.map { it.key }
        .sortedWith(compareBy({ it != StockLedger.MAIN }, { it }))
      if (at.isNotEmpty()) out[l.entry.id] = at.map(StockPlaces::label)
    }
    return out
  }

  /** INV-01 — فهرستِ انبار: محل‌ها (اصلی اول) و کالا ⇒ محل ⇒ هزارم، همان [StockPlaces.places]/[StockPlaces.stockAt]؛ فقط خواندنی */
  fun all(d: ShopData): Pair<List<String>, Map<String, Map<String, Long>>> = StockPlaces.stockAt(d).let { StockPlaces.places(d, it) to it }

  fun of(d: ShopData, p: Product?): List<PlaceQty> {
    if (p == null || p.stockless == true || !p.kit.isNullOrEmpty() || StockLedger.isOn(d)) return emptyList()
    val byPlace = StockPlaces.stockAt(d)[p.id].orEmpty().filterValues { it != 0L }
    if (byPlace.keys.none { it != StockLedger.MAIN }) return emptyList()
    return byPlace.entries
      .sortedWith(compareBy({ it.key != StockLedger.MAIN }, { it.key }))
      .map { (loc, q) -> PlaceQty(loc, StockPlaces.label(loc), q) }
  }
}
