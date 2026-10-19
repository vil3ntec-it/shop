package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.stock.Lot
import ir.vil3ntec.tohid.core.stock.LotRef
import ir.vil3ntec.tohid.core.stock.Pick
import ir.vil3ntec.tohid.core.stock.Position
import ir.vil3ntec.tohid.core.stock.StockBook
import ir.vil3ntec.tohid.core.stock.StockCore
import ir.vil3ntec.tohid.core.stock.StockDoc
import ir.vil3ntec.tohid.core.stock.StockMode
import ir.vil3ntec.tohid.core.stock.StockResult
import ir.vil3ntec.tohid.core.stock.UnitCost
import ir.vil3ntec.tohid.core.calendar.CivilDays
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 *  پلِ دفترِ دکان ⇐ هستهٔ موجودی (`core/stock/StockCore`) — مشترکِ اندروید و کامپیوتر.
 *
 *  PRD-15 · PRD-16 · INV-02 · INV-04 · INV-09/10 · INV-19 · INV-20 · PUR-06
 *  (بخشِ ۳.۶ و ۱۵.۴). بردارهای مرجع: `license/test-vectors/stock-ledger-v1.json`
 *  (`StockLedgerVectorsTest`؛ عددها با دست حساب شده‌اند، نه از خروجیِ موتور).
 *  برنامهٔ وبِ دکان کنار گذاشته شده (دستورِ صاحب محصول ۱۴۰۵/۰۷/۲۷) و این پل را ندارد.
 *
 *  ── چه می‌کند ──────────────────────────────────────────────────────
 *  `ShopData`ِ امروز (ورود، فروش، برگشت، انتقال) را به سندهای `StockCore`
 *  برمی‌گرداند و به ترتیبِ زمان می‌دواند. خروجی دفترِ «بها، لات و محل» است
 *  **کنارِ** موتورِ امروز (`ShopStore.stock`)، نه جایش: موجودیِ دست همان
 *  «ورودی منهای فروش» می‌ماند و برنامهٔ قدیمی همان عدد را می‌بیند.
 *
 *  ⚠️ پشتِ تنظیمِ دکان (`stock_mode` در دفترچهٔ ثبت، پیش‌فرض خاموش). روشِ بها
 *  تصمیمِ باز است (D-23)؛ هر عددِ بها «موقت/تقریبی» است.
 */
object StockLedger {
  const val FORMAT = "stock-ledger-v1"
  const val MODE_TYPE = "stock_mode"
  const val ACK_TYPE = "stock_review_ack"
  const val LEGACY_USER = "legacy"
  const val MAIN = ""

  val ADJ_KINDS = listOf("count", "damage", "expired", "lost", "other", "supplier_return")
  val ADJ_REASON = mapOf(
    "count" to "اصلاحِ شمارش", "damage" to "خراب", "expired" to "منقضی", "lost" to "گم‌شده",
    "other" to "اصلاح موجودی", "supplier_return" to "برگشت به تأمین‌کننده",
  )
  private val RANK = mapOf("entry" to 0, "transfer" to 1, "sale" to 2, "return" to 3)

  fun milli(q: Double): Long = if (q.isFinite()) Math.round(q * 1000.0) else 0L
  fun minor(a: Double?): Long = if (a != null && a.isFinite()) Math.round(a * 100.0) else 0L

  /*  بی java.time: اندرویدِ ۷ (minSdk ۲۴) آن را ندارد. روزِ JDNِ ۱۹۷۰-۰۱-۰۱ = ۲۴۴۰۵۸۸. */
  private const val EPOCH_JDN = 2_440_588
  private const val DAY_MS = 86_400_000L

  /** نیمه‌شبِ UTCِ یک روزِ `yyyy-MM-dd` (میلی‌ثانیه)؛ تاریخِ نادرست ⇒ ۰ */
  fun dayMs(iso: String): Long {
    if (!StockCore.isoDateOk(iso)) return 0L
    val jdn = CivilDays.jdnOfIso(iso) ?: return 0L
    return (jdn - EPOCH_JDN).toLong() * DAY_MS
  }

  /** روزِ UTCِ یک لحظه، `yyyy-MM-dd` */
  fun isoOf(ms: Long): String = CivilDays.isoOf((Math.floorDiv(ms, DAY_MS) + EPOCH_JDN).toInt())

  /** فاصلهٔ دو روزِ `yyyy-MM-dd` (b − a)؛ نادرست ⇒ null */
  fun daysBetween(a: String, b: String): Int? {
    val x = CivilDays.jdnOfIso(a) ?: return null
    val y = CivilDays.jdnOfIso(b) ?: return null
    return y - x
  }

  private fun atOf(createdAt: Long, date: String): Long {
    if (createdAt > 0) return createdAt
    val d = dayMs(date)
    return if (d > 0) d else 1L
  }

  private fun dayOf(date: String, at: Long): String = if (StockCore.isoDateOk(date)) date else isoOf(at)

  /* ==========================================================
     تنظیمِ دکان — آخرین ردیفِ `stock_mode` (همگام می‌شود؛ پیش‌فرض خاموش)
     ========================================================== */
  fun modeRow(d: ShopData): AuditEntry? {
    var best: AuditEntry? = null
    for (a in d.auditLog) {
      if (a.type != MODE_TYPE) continue
      val b = best
      if (b == null || a.createdAt > b.createdAt || (a.createdAt == b.createdAt && a.id > b.id)) best = a
    }
    return best
  }

  fun isOn(d: ShopData): Boolean = modeRow(d)?.refId == "on"

  /* ==========================================================
     دفتر ⇐ رویدادها
     ========================================================== */
  data class Event(
    val id: String,
    val src: String,
    val type: String,
    val at: Long,
    val day: String,
    val productId: String,
    val location: String,
    val qtyMilli: Long,
    val userId: String,
    val refId: String,
    val reason: String? = null,
    val lotId: String? = null,
    val lot: LotRef? = null,
    val costMinor: Long? = null,
    val landedMinor: Long = 0,
    val toLocation: String = "",
    val adjKind: String = "",
  )

  private fun lotRefOf(w: WarehouseEntry): LotRef? {
    val batch = w.batch.trim()
    val exp = w.expiryDate.takeIf { it.isNotEmpty() && StockCore.isoDateOk(it) }
    var prod = w.productionDate.takeIf { it.isNotEmpty() && StockCore.isoDateOk(it) }
    if (prod != null && exp != null && prod > exp) prod = null
    val id = batch.ifEmpty { if (exp != null || prod != null) "w:" + w.id else "" }
    return if (id.isEmpty()) null else LotRef(id, prod, exp)
  }

  /** هزینهٔ جانبیِ ردیف به صدمِ افغانی: `landedCostMinor` (دقیق) اگر هست، وگرنه از `landedCost` */
  fun landedMinorOf(w: WarehouseEntry): Long = w.landedCostMinor ?: minor(w.landedCost)

  private fun adjKindOf(w: WarehouseEntry, moveTypeByRef: Map<String, String>, damagedReturn: Boolean): String {
    val k = w.adjKind.orEmpty()
    if (k in ADJ_KINDS) return k
    if (moveTypeByRef[w.id] == "supplier_return") return "supplier_return"
    if (w.notes.startsWith("انبارگردانی")) return "count"
    if (damagedReturn || w.notes.startsWith(DAMAGED_RETURN_NOTE)) return "damage"
    return "other"
  }

  /**
   *  برگشتِ ضایعات (RET، #318): ردیفِ اصلاحیِ منفی + حرکتِ `damage` با همان کالا و همان
   *  `createdAt` که `refId`ش ردیفِ برگشت است. ردیفی که `adjKind` ندارد از همین شناخته
   *  می‌شود تا در «ضایعات»ِ دفترِ موجودی بیاید و کاربرش `by` ِ همان برگشت باشد.
   */
  const val DAMAGED_RETURN_NOTE = "ضایعاتِ برگشتی"

  /** [only]: فقط همین کالاها (سنجشِ سبد)؛ `null` یعنی همه */
  fun events(d: ShopData, only: Set<String>? = null): List<Event> {
    val out = ArrayList<Event>()
    fun want(pid: String) = only == null || pid in only
    val moveTypeByRef = HashMap<String, String>()
    val damageRef = HashMap<String, String>()
    for (mv in d.stockMovements) {
      if (mv.type == "damage" && mv.createdAt > 0) damageRef.putIfAbsent(mv.productId + "|" + mv.createdAt, mv.refId.orEmpty())
      val r = mv.refId ?: continue
      if (!moveTypeByRef.containsKey(r)) moveTypeByRef[r] = mv.type
    }
    val returnBy = HashMap<String, String>()
    for (r in d.saleReturns) r.by?.trim()?.takeIf { it.isNotEmpty() }?.let { returnBy[r.id] = it }

    d.warehouseEntries.forEachIndexed { i, w ->
      if (w.productId.isEmpty() || !want(w.productId)) return@forEachIndexed
      val q = milli(w.units)
      if (q == 0L) return@forEachIndexed
      val at = atOf(w.createdAt, w.date)
      val id = w.id.ifEmpty { "w#$i" }
      val dmg = if (w.isAdjustment && q < 0 && w.adjKind.isNullOrEmpty() && w.createdAt > 0) damageRef[w.productId + "|" + w.createdAt] else null
      val user = w.userId?.trim()?.ifEmpty { null } ?: dmg?.let { returnBy[it] } ?: LEGACY_USER
      val ref = w.purchaseId.orEmpty().ifEmpty { w.id }
      val loc = w.locationId.orEmpty()
      if (!w.isAdjustment && q > 0) {
        out += Event(
          id, "entry", "purchase_receipt", at, dayOf(w.date, at), w.productId, loc, q, user, ref,
          costMinor = max(0L, Math.round(w.units * w.price * 100.0)),
          landedMinor = max(0L, landedMinorOf(w)),
          lot = lotRefOf(w),
        )
      } else {
        val kind = if (w.isAdjustment) adjKindOf(w, moveTypeByRef, dmg != null) else "other"
        val (type, qty) = when {
          kind == "supplier_return" && q < 0 -> "supplier_return" to -q
          (kind == "damage" || kind == "expired") && q < 0 -> kind to -q
          kind == "count" -> "count_adjust" to q
          else -> "adjustment" to q
        }
        out += Event(
          id, "entry", type, at, dayOf(w.date, at), w.productId, loc, qty, user, ref,
          reason = w.notes.trim().ifEmpty { ADJ_REASON.getValue(kind) },
          lotId = w.lotId?.takeIf { it.isNotEmpty() },
          adjKind = kind,
        )
      }
    }

    d.stockMovements.forEachIndexed { t, x ->
      if (x.type != "transfer" || x.productId.isEmpty() || !want(x.productId)) return@forEachIndexed
      val to = x.toLocationId ?: return@forEachIndexed
      val tq = abs(milli(x.qty))
      val from = x.locationId.orEmpty()
      if (tq == 0L || from == to) return@forEachIndexed
      val at = atOf(x.createdAt, x.date)
      out += Event(
        x.id.ifEmpty { "m#$t" }, "transfer", "transfer", at, dayOf(x.date, at), x.productId, from, tq,
        x.userId.orEmpty().trim().ifEmpty { LEGACY_USER },
        x.refId.orEmpty().ifEmpty { x.id },
        reason = x.reason.orEmpty().trim().ifEmpty { x.notes.trim().ifEmpty { "انتقال" } },
        lotId = x.lotId?.takeIf { it.isNotEmpty() },
        toLocation = to,
      )
    }

    val saleById = HashMap<String, Sale>()
    for (s in d.sales) saleById[s.id] = s
    val retByItem = HashMap<String, MutableList<SaleReturn>>()
    for (r in d.saleReturns) retByItem.getOrPut(r.saleItemId) { ArrayList() } += r
    d.saleItems.forEachIndexed { k, si ->
      if (si.productId.isEmpty() || !want(si.productId)) return@forEachIndexed
      val sale = saleById[si.saleId]
      if (sale != null && sale.status == "cancelled") return@forEachIndexed
      val sq = milli(si.quantity)
      val sat = if (sale != null) atOf(sale.createdAt, sale.date) else 1L
      val sday = if (sale != null) dayOf(sale.date, sat) else isoOf(sat)
      val sid = si.id.ifEmpty { "si#$k" }
      val seller = sale?.seller?.trim()?.ifEmpty { null } ?: LEGACY_USER
      val loc = si.locationId.orEmpty()
      val pref = si.lotId?.takeIf { it.isNotEmpty() }
      if (sq > 0) {
        out += Event(sid, "sale", "sale", sat, sday, si.productId, loc, sq, seller, si.saleId, lotId = pref)
      } else if (sq < 0) {
        out += Event(sid, "sale", "adjustment", sat, sday, si.productId, loc, -sq, seller, si.saleId, reason = "قلمِ منفیِ فاکتور")
      }
      var left = milli(si.returnedQty)
      if (left <= 0) return@forEachIndexed
      val mine = (retByItem[sid] ?: emptyList<SaleReturn>()).sortedWith { a, b ->
        val c = atOf(a.createdAt, a.date).compareTo(atOf(b.createdAt, b.date))
        if (c != 0) c else a.id.compareTo(b.id)
      }
      for ((j, r) in mine.withIndex()) {
        if (left <= 0) break
        val take = min(left, milli(r.quantity))
        if (take <= 0) continue
        val rat = atOf(r.createdAt, r.date)
        out += Event(
          r.id.ifEmpty { "$sid:r$j" }, "return", "customer_return", rat, dayOf(r.date, rat), si.productId, loc, take,
          r.by?.trim()?.ifEmpty { null } ?: LEGACY_USER, si.saleId,
          reason = r.reason.trim().ifEmpty { null }, lotId = pref,
        )
        left -= take
      }
      if (left > 0) out += Event("ret:$sid", "return", "customer_return", sat, sday, si.productId, loc, left, seller, si.saleId, lotId = pref)
    }

    out.sortWith { a, b ->
      val c = a.at.compareTo(b.at)
      if (c != 0) c else {
        val r = RANK.getValue(a.src) - RANK.getValue(b.src)
        if (r != 0) r else a.id.compareTo(b.id)
      }
    }
    return out
  }

  /* ==========================================================
     بازپخش — هر کالا دفترِ جدای خودش
     ========================================================== */

  /** شناسهٔ انبار در هسته خالی نمی‌شود؛ محلِ اصلی "" است ⇒ «L:» + نام */
  private fun wid(location: String) = "L:$location"
  private fun locOf(warehouseId: String) = warehouseId.substring(2)

  data class Snap(val location: String, val position: Position) {
    val lots: List<Lot> get() = position.lots.filter { it.qtyMilli != 0L }
  }

  private fun snaps(book: StockBook): List<Snap> = StockCore.positions(book).map { Snap(locOf(it.warehouseId), it) }

  private data class Health(val healthy: Long, val expired: Long, val onHand: Long, val reserved: Long)

  private fun healthOf(p: Position, today: String?): Health {
    var exp = 0L
    for (l in p.lots) if (l.qtyMilli > 0 && StockCore.isExpired(l, today)) exp += l.qtyMilli
    return Health(max(0L, p.qtyMilli - exp - p.reservedMilli), exp, p.qtyMilli, p.reservedMilli)
  }

  private fun saleOrder(ss: List<Snap>, loc0: String, prefLot: String?): List<String> {
    val order = LinkedHashSet<String>()
    if (prefLot != null) for (s in ss) for (l in s.lots) if (l.lotId == prefLot && l.qtyMilli > 0) order += s.location
    order += loc0
    ss.map { it.location }.sorted().forEach { order += it }
    return order.toList()
  }

  private fun singleAt(book: StockBook, productId: String, loc: String): Boolean {
    val n = book.positions.size
    return n == 0 || (n == 1 && book.positions.containsKey(StockCore.keyOf(productId, wid(loc))))
  }

  data class Part(val location: String, val qtyMilli: Long)

  private fun saleParts(book: StockBook, productId: String, q: Long, loc0: String, prefLot: String?, today: String?, recorded: Boolean): List<Part> {
    if (singleAt(book, productId, loc0)) return listOf(Part(loc0, q))
    val ss = snaps(book)
    val order = saleOrder(ss, loc0, prefLot)
    val by = ss.associate { it.location to healthOf(it.position, today) }
    val take = HashMap<String, Long>()
    var left = q
    for (l in order) {
      if (left <= 0) break
      val t = min(left, by[l]?.healthy ?: 0L)
      if (t > 0) { take[l] = t; left -= t }
    }
    if (left > 0 && recorded) {
      for (l in order) {
        if (left <= 0) break
        val h = by[l] ?: continue
        val room = max(0L, h.onHand - h.reserved - (take[l] ?: 0L))
        val t2 = min(left, room)
        if (t2 > 0) { take[l] = (take[l] ?: 0L) + t2; left -= t2 }
      }
      if (left > 0) { take[loc0] = (take[loc0] ?: 0L) + left; left = 0 }
    }
    val parts = ArrayList<Part>()
    for (l in order) take[l]?.takeIf { it > 0 }?.let { parts += Part(l, it) }
    if (left > 0) parts += Part(loc0, left)
    return parts
  }

  private fun docOf(
    ev: Event, type: String, location: String, qtyMilli: Long, id: String = ev.id,
    lotId: String? = null, lot: LotRef? = null, costMinor: Long? = null, landedMinor: Long? = null,
  ) = StockDoc(
    id = id, type = type, productId = ev.productId, warehouseId = wid(location), userId = ev.userId, at = ev.at,
    qtyMilli = qtyMilli, reason = ev.reason?.takeIf { it.isNotEmpty() }, costMinor = costMinor, landedMinor = landedMinor,
    lot = lot, lotId = lotId, refId = ev.refId.ifEmpty { null },
  )

  data class Applied(val doc: StockDoc, val result: StockResult)
  private data class Step(val book: StockBook, val applied: List<Applied>, val error: StockResult?)

  private fun applyEvent(book0: StockBook, ev: Event, recorded: Boolean, today: String?): Step {
    val mode = if (recorded) StockMode.RECORDED else StockMode.LOCAL
    var book = book0
    val applied = ArrayList<Applied>()
    when (ev.type) {
      "transfer" -> {
        val out = docOf(ev, "transfer_out", ev.location, ev.qtyMilli, id = ev.id + ":out", lotId = ev.lotId)
        val r = StockCore.apply(book, out, mode, today)
        if (!r.result.ok) return Step(book0, emptyList(), r.result)
        book = r.book
        applied += Applied(out, r.result)
        val cost = r.result.costMinor
        val pieces = ArrayList<Pick>(r.result.picks)
        if (r.result.shortMilli > 0) pieces += Pick("", r.result.shortMilli)
        var cum = 0L
        var prev = 0L
        val srcPos = StockCore.position(book0, ev.productId, wid(ev.location))
        for ((i, pc) in pieces.withIndex()) {
          cum += pc.qtyMilli
          val c = StockCore.mulDivRound(cost, cum, ev.qtyMilli)
          val src = if (pc.lotId.isNotEmpty()) srcPos?.lots?.firstOrNull { it.lotId == pc.lotId } else null
          val lot = if (pc.lotId.isNotEmpty()) LotRef(pc.lotId, src?.productionDate, src?.expiryDate) else null
          val inn = docOf(
            ev, "transfer_in", ev.toLocation, pc.qtyMilli,
            id = ev.id + ":in" + (if (pieces.size > 1) ":$i" else ""), lot = lot, costMinor = c - prev,
          )
          prev = c
          val ri = StockCore.apply(book, inn, mode, today)
          if (!ri.result.ok) return Step(book0, emptyList(), ri.result)
          book = ri.book
          applied += Applied(inn, ri.result)
        }
        return Step(book, applied, null)
      }
      "sale" -> {
        val parts = saleParts(book, ev.productId, ev.qtyMilli, ev.location, ev.lotId, today, recorded)
        for ((p, part) in parts.withIndex()) {
          val sd = docOf(ev, "sale", part.location, part.qtyMilli, id = if (parts.size > 1) "${ev.id}:$p" else ev.id, lotId = ev.lotId)
          val r = StockCore.apply(book, sd, mode, today)
          if (!r.result.ok) return Step(book0, emptyList(), r.result)
          book = r.book
          applied += Applied(sd, r.result)
        }
        return Step(book, applied, null)
      }
      else -> {
        val d = if (ev.type == "purchase_receipt") {
          docOf(ev, ev.type, ev.location, ev.qtyMilli, lot = ev.lot, costMinor = ev.costMinor, landedMinor = ev.landedMinor.takeIf { it > 0 })
        } else {
          docOf(ev, ev.type, ev.location, ev.qtyMilli, lotId = ev.lotId)
        }
        val r = StockCore.apply(book, d, mode, today)
        if (!r.result.ok) return Step(book0, emptyList(), r.result)
        return Step(r.book, listOf(Applied(d, r.result)), null)
      }
    }
  }

  data class Row(
    val id: String,
    val eventId: String,
    val src: String,
    val type: String,
    val productId: String,
    val location: String,
    val qtyMilli: Long,
    val costMinor: Long,
    val costAdjustMinor: Long,
    val at: Long,
    val day: String,
    val userId: String,
    val reason: String,
    val refId: String,
    val lotId: String,
    val picks: List<Pick>,
  )

  data class Task(
    val id: String,
    val eventId: String,
    val src: String,
    val productId: String,
    val location: String,
    val reasons: List<String>,
    val shortMilli: Long,
    val qtyAfterMilli: Long,
    val at: Long,
    val day: String,
    val refId: String,
    val acked: Boolean,
  )

  data class View(
    val books: Map<String, StockBook>,
    val rows: List<Row>,
    val tasks: List<Task>,
    val errors: List<Pair<String, String>>,
  )

  private fun rowOf(ev: Event, a: Applied): Row {
    val d = a.doc
    return Row(
      d.id, ev.id, ev.src, d.type, d.productId, locOf(d.warehouseId), StockCore.signedQtyMilli(d),
      a.result.costMinor, a.result.costAdjustMinor, ev.at, ev.day, d.userId, d.reason.orEmpty(),
      d.refId.orEmpty(), d.lot?.lotId ?: d.lotId.orEmpty(), a.result.picks,
    )
  }

  /**
   *  کلِ دفتر ⇐ نما. [productIds]: فقط همین کالاها (سرِ صندوق)، با همان نتیجهٔ
   *  بازپخشِ کامل برای هر کالا.
   */
  fun replay(d: ShopData, productIds: Collection<String>? = null): View {
    val evs = events(d, productIds?.toSet())
    val books = HashMap<String, StockBook>()
    val rows = ArrayList<Row>(evs.size)
    val tasks = ArrayList<Task>()
    val errors = ArrayList<Pair<String, String>>()
    val acked = d.auditLog.filter { it.type == ACK_TYPE }.mapNotNull { it.refId }.toHashSet()
    for (ev in evs) {
      val step = applyEvent(books[ev.productId] ?: StockBook(), ev, true, ev.day)
      if (step.error != null) { errors += ev.id to (step.error.error ?: ""); continue }
      books[ev.productId] = step.book
      for (a in step.applied) {
        rows += rowOf(ev, a)
        val rt = a.result.reviewTask ?: continue
        tasks += Task(
          a.doc.id, ev.id, ev.src, rt.productId, locOf(rt.warehouseId), rt.reasons, rt.shortMilli, rt.qtyAfterMilli,
          ev.at, ev.day, ev.refId, a.doc.id in acked,
        )
      }
    }
    return View(books, rows, tasks, errors)
  }

  /* ==========================================================
     خواندنِ نما
     ========================================================== */
  data class LocQty(val location: String, val qtyMilli: Long, val valueMinor: Long)
  data class LotLine(val lotId: String, val location: String, val productionDate: String?, val expiryDate: String?, val qtyMilli: Long)
  data class ProductView(
    val productId: String,
    val qtyMilli: Long,
    val valueMinor: Long,
    /** بهای میانگینِ یک واحد (واحدِ کوچکِ پول)؛ `null` = نامعلوم */
    val unitCostMinor: Long?,
    val lastCost: UnitCost?,
    val locations: List<LocQty>,
    val lots: List<LotLine>,
  )

  private val LOT_LINE_ORDER = Comparator<LotLine> { x, y ->
    val c = StockCore.LOT_ORDER.compare(
      Lot(x.lotId, x.productionDate, x.expiryDate, x.qtyMilli, 0),
      Lot(y.lotId, y.productionDate, y.expiryDate, y.qtyMilli, 0),
    )
    if (c != 0) c else x.location.compareTo(y.location)
  }

  fun productView(view: View, productId: String): ProductView {
    val ss = view.books[productId]?.let { snaps(it) } ?: emptyList()
    var qty = 0L
    var value = 0L
    var last: UnitCost? = null
    val locs = ArrayList<LocQty>()
    val lots = ArrayList<LotLine>()
    for (s in ss) {
      val p = s.position
      qty += p.qtyMilli
      value += p.valueMinor
      if (last == null || s.location == MAIN) last = p.lastCost ?: last
      locs += LocQty(s.location, p.qtyMilli, p.valueMinor)
      for (l in s.lots) lots += LotLine(l.lotId, s.location, l.productionDate, l.expiryDate, l.qtyMilli)
    }
    lots.sortWith(LOT_LINE_ORDER)
    val unit = when {
      qty > 0 -> StockCore.mulDivRound(value, 1000, qty)
      last != null -> StockCore.mulDivRound(last.num, 1000, last.den)
      else -> null
    }
    return ProductView(productId, qty, value, unit, last, locs, lots)
  }

  fun totalValueMinor(view: View): Long = view.books.values.sumOf { b -> b.positions.values.sumOf { it.valueMinor } }

  fun reconcileAll(view: View): Boolean = view.books.values.all { b -> b.positions.values.all { it.reconciles } }

  fun locations(d: ShopData): List<String> {
    val set = LinkedHashSet<String>()
    d.warehouseEntries.forEach { it.locationId?.let(set::add) }
    d.stockMovements.forEach { if (it.type == "transfer") { it.locationId?.let(set::add); it.toLocationId?.let(set::add) } }
    d.saleItems.forEach { it.locationId?.let(set::add) }
    set.remove(MAIN)
    return listOf(MAIN) + set.sorted()
  }

  /* ==========================================================
     سنجشِ کارِ تازه (local)
     ========================================================== */
  fun unitCostMinor(costMinor: Long, qtyMilli: Long): Long =
    if (qtyMilli > 0) StockCore.mulDivRound(costMinor, 1000, qtyMilli) else 0L

  data class Line(val productId: String, val qtyMilli: Long, val lotId: String? = null, val location: String = MAIN)
  data class LocPick(val location: String, val lotId: String, val qtyMilli: Long)
  data class Check(
    val ok: Boolean,
    val error: String? = null,
    val availableMilli: Long? = null,
    val expiredMilli: Long? = null,
    val costMinor: Long = 0,
    val unitCostMinor: Long = 0,
    val unknownCost: Boolean = false,
    val picks: List<LocPick> = emptyList(),
  )

  fun checkCart(view: View, lines: List<Line>, today: String): List<Check> {
    val books = HashMap<String, StockBook>()
    val out = ArrayList<Check>()
    lines.forEachIndexed { i, ln ->
      val pid = ln.productId
      val book = books[pid] ?: view.books[pid] ?: StockBook()
      val q = ln.qtyMilli
      val ev = Event("check:$i", "sale", "sale", 1L, today, pid, ln.location, q, "check", "", lotId = ln.lotId?.takeIf { it.isNotEmpty() })
      val ss = snaps(book)
      if (ss.size > 1 || (ss.size == 1 && ss[0].location != ev.location)) {
        var h = 0L
        var e = 0L
        var oh = 0L
        for (s in ss) {
          val x = healthOf(s.position, today)
          h += x.healthy; e += x.expired; oh += s.position.qtyMilli - s.position.reservedMilli
        }
        if (q > h) {
          out += if (e > 0 && q <= oh) Check(false, "expired_stock", max(0L, h), e)
          else Check(false, "insufficient_stock", max(0L, h))
          return@forEachIndexed
        }
      }
      val step = applyEvent(book, ev, false, today)
      val err = step.error
      if (err != null) {
        out += Check(false, err.error, err.availableMilli ?: 0L, err.expiredMilli)
        return@forEachIndexed
      }
      books[pid] = step.book
      var cost = 0L
      val picks = ArrayList<LocPick>()
      for (a in step.applied) {
        cost += a.result.costMinor
        for (p in a.result.picks) picks += LocPick(locOf(a.doc.warehouseId), p.lotId, p.qtyMilli)
      }
      val pv = productView(View(mapOf(pid to book), emptyList(), emptyList(), emptyList()), pid)
      out += Check(true, costMinor = cost, unitCostMinor = unitCostMinor(cost, q), unknownCost = cost == 0L && pv.lastCost == null && pv.qtyMilli <= 0, picks = picks)
    }
    return out
  }

  fun checkTransfer(view: View, productId: String, from: String, to: String, qtyMilli: Long, lotId: String?, today: String): Check {
    if (from == to) return Check(false, "same_location")
    if (qtyMilli <= 0) return Check(false, "bad_qty")
    val book = view.books[productId] ?: StockBook()
    val ev = Event("check", "transfer", "transfer", 1L, today, productId, from, qtyMilli, "check", "", reason = "انتقال",
      lotId = lotId?.takeIf { it.isNotEmpty() }, toLocation = to)
    val step = applyEvent(book, ev, false, today)
    val err = step.error
    if (err != null) return Check(false, err.error, err.availableMilli ?: 0L)
    val first = step.applied[0]
    return Check(true, costMinor = first.result.costMinor, picks = first.result.picks.map { LocPick(from, it.lotId, it.qtyMilli) })
  }

  private fun diffValueMinor(pv: ProductView, diff: Long): Long {
    if (diff == 0L) return 0L
    val a = abs(diff)
    val lc = pv.lastCost
    val v = when {
      pv.qtyMilli > 0 -> StockCore.mulDivRound(pv.valueMinor, a, pv.qtyMilli)
      lc != null -> StockCore.mulDivRound(lc.num, a, lc.den)
      else -> 0L
    }
    return if (diff < 0) -v else v
  }

  data class CountLine(val productId: String, val systemMilli: Long, val countedMilli: Long, val diffMilli: Long, val valueMinor: Long)
  data class CountReview(
    val lines: List<CountLine>,
    val matched: Int,
    val variance: Int,
    val uncounted: Int,
    val valueMinor: Long,
    val gainMinor: Long,
    val lossMinor: Long,
  )

  /** بازبینیِ شمارش (INV-09/10، D-19): snapshot = موجودیِ لحظهٔ شروع؛ شمرده‌نشده دست نمی‌خورد */
  fun countReview(view: View, productIds: List<String>, counts: Map<String, Long>, snapshot: Map<String, Long>?): CountReview {
    val lines = ArrayList<CountLine>()
    var matched = 0
    var variance = 0
    var uncounted = 0
    var value = 0L
    var gain = 0L
    var loss = 0L
    for (pid in productIds) {
      val counted = counts[pid]
      if (counted == null) { uncounted++; continue }
      val pv = productView(view, pid)
      val sys = snapshot?.get(pid) ?: pv.qtyMilli
      val diff = counted - sys
      val dv = diffValueMinor(pv, diff)
      if (diff == 0L) matched++ else variance++
      value += dv
      if (dv > 0) gain += dv else loss -= dv
      lines += CountLine(pid, sys, counted, diff, dv)
    }
    return CountReview(lines, matched, variance, uncounted, value, gain, loss)
  }

  /** هزینهٔ جانبیِ فاکتور روی ردیف‌ها به نسبتِ ارزش؛ جمع دقیقاً همان کل (PUR-06) */
  fun spreadLanded(totalMinor: Long, weightsMinor: List<Long>): List<Long> {
    val n = weightsMinor.size
    if (totalMinor <= 0 || n == 0) return List(n) { 0L }
    var w = weightsMinor.map { max(0L, it) }
    var total = w.sum()
    if (total == 0L) { w = List(n) { 1L }; total = n.toLong() }
    val out = ArrayList<Long>(n)
    var cum = 0L
    var prev = 0L
    for (x in w) {
      cum += x
      val c = StockCore.mulDivRound(totalMinor, cum, total)
      out += c - prev
      prev = c
    }
    return out
  }

  data class RowFilter(
    val productId: String? = null,
    /** `null` = همه */
    val location: String? = null,
    val types: Set<String>? = null,
    val userId: String? = null,
    val from: Long? = null,
    val to: Long? = null,
  )

  fun filterRows(rows: List<Row>, f: RowFilter): List<Row> = rows.filter { r ->
    (f.productId.isNullOrEmpty() || r.productId == f.productId) &&
      (f.location == null || r.location == f.location) &&
      (f.types.isNullOrEmpty() || r.type in f.types) &&
      (f.userId.isNullOrEmpty() || r.userId == f.userId) &&
      (f.from == null || r.at >= f.from) &&
      (f.to == null || r.at <= f.to)
  }

  val EXPORT_HEADER = listOf("شناسه", "زمان", "روز", "نوع", "کالا", "محل", "مقدار", "بهای موقت (افغانی)", "کاربر", "دلیل", "سری", "سند")

  fun exportRows(rows: List<Row>, nameOf: (String) -> String): List<List<String>> = rows.map { r ->
    listOf(
      r.id, r.at.toString(), r.day, r.type, nameOf(r.productId), r.location,
      plain(r.qtyMilli, 1000), plain(r.costMinor, 100),
      r.userId, r.reason, r.lotId, r.refId,
    )
  }

  /** عددِ بی اعشارِ اضافه — همان `String(n / scale)`ِ جاوااسکریپت برای مقادیرِ متناهی */
  fun plain(v: Long, scale: Int): String {
    val neg = v < 0
    val a = abs(v)
    val whole = a / scale
    var frac = (a % scale).toString().padStart(scale.toString().length - 1, '0').trimEnd('0')
    if (frac.isNotEmpty()) frac = ".$frac"
    return (if (neg && (whole != 0L || frac.isNotEmpty())) "-" else "") + whole + frac
  }
}
