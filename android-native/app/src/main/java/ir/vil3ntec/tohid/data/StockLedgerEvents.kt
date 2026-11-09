package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.i18n.StrStock
import ir.vil3ntec.tohid.core.stock.StockCore
import ir.vil3ntec.tohid.data.StockLedger.ADJ_KINDS
import ir.vil3ntec.tohid.data.StockLedger.ADJ_REASON
import ir.vil3ntec.tohid.data.StockLedger.DAMAGED_RETURN_NOTE
import ir.vil3ntec.tohid.data.StockLedger.Event
import ir.vil3ntec.tohid.data.StockLedger.LEGACY_USER
import ir.vil3ntec.tohid.data.StockLedger.TRANSFER_LEGACY
import ir.vil3ntec.tohid.data.StockLedger.TRANSFER_OUT
import ir.vil3ntec.tohid.data.StockLedger.dayMs
import ir.vil3ntec.tohid.data.StockLedger.isoOf
import ir.vil3ntec.tohid.data.StockLedger.landedMinorOf
import ir.vil3ntec.tohid.data.StockLedger.lotRefOf
import ir.vil3ntec.tohid.data.StockLedger.milli
import ir.vil3ntec.tohid.data.StockLedger.minor
import ir.vil3ntec.tohid.data.StockLedger.roundOr0
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 *  دفترِ دکان ⇐ رویدادهای `StockLedger` (ورود، اصلاح، انتقال، فروش، برگشت) به ترتیبِ زمان — همان
 *  `StockLedger.events`، فقط جدا شده تا پلِ موجودی زیرِ سقفِ اندازه بماند (رفتار بایت‌به‌بایت همان؛
 *  آزمون‌ها: `StockLedgerVectorsTest`، `StockWiringTest`). شرحِ پل سرِ `StockLedger`.
 */
internal object StockLedgerEvents {
  private val RANK = mapOf("entry" to 0, "transfer" to 1, "sale" to 2, "return" to 3)

  private fun atOf(createdAt: Long, date: String): Long {
    if (createdAt > 0) return createdAt
    val d = dayMs(date)
    return if (d > 0) d else 1L
  }

  private fun dayOf(date: String, at: Long): String = if (StockCore.isoDateOk(date)) date else isoOf(at)

  private fun adjKindOf(w: WarehouseEntry, moveTypeByRef: Map<String, String>, damagedReturn: Boolean): String {
    val k = w.adjKind.orEmpty()
    if (k in ADJ_KINDS) return k
    if (moveTypeByRef[w.id] == "supplier_return") return "supplier_return"
    if (w.notes.startsWith("انبارگردانی")) return "count"  // داده: کلید یا سنجشِ منطق
    if (damagedReturn || w.notes.startsWith(DAMAGED_RETURN_NOTE)) return "damage"
    return "other"
  }

  /** [only]: فقط همین کالاها (سنجشِ سبد)؛ `null` یعنی همه */
  fun of(d: ShopData, only: Set<String>?): List<Event> {
    val out = ArrayList<Event>()
    fun want(pid: String) = only == null || pid in only
    /*
     *  سنجشِ سبد (`only` ≠ null) سرِ هر ثبتِ فروش صدا می‌شود: نمایه‌ها فقط برای ردیف‌های
     *  همین کالاها ساخته می‌شوند، نه برای کلِ دفتر (کندیِ صندوق روی دفترِ بزرگ).
     */
    val moveTypeByRef = HashMap<String, String>()
    val damageRef = HashMap<String, String>()
    for (mv in d.stockMovements) {
      if (!want(mv.productId)) continue
      if (mv.type == "damage" && mv.createdAt > 0) damageRef.putIfAbsent(mv.productId + "|" + mv.createdAt, mv.refId.orEmpty())
      val r = mv.refId ?: continue
      if (!moveTypeByRef.containsKey(r)) moveTypeByRef[r] = mv.type
    }
    //  ثبت‌کنندهٔ برگشت (`SaleReturn.by`): با شناسهٔ ردیف (حرکتِ `damage`) و با `docId` ِ سند
    //  (ردیفِ انبارِ برگشتِ ضایعات یا بی رسید، `WarehouseEntry.refId`، RET #318)
    val wantRet = HashSet<String>(damageRef.values)
    for (w in d.warehouseEntries) if (want(w.productId)) w.refId?.let(wantRet::add)
    val returnBy = HashMap<String, String>()
    if (wantRet.isNotEmpty()) {
      for (r in d.saleReturns) {
        val by = r.by?.trim()?.takeIf { it.isNotEmpty() } ?: continue
        if (r.id in wantRet) returnBy[r.id] = by
        val doc = r.docId
        if (doc != null && doc in wantRet) returnBy.putIfAbsent(doc, by)
      }
    }

    d.warehouseEntries.forEachIndexed { i, w ->
      if (w.productId.isEmpty() || !want(w.productId)) return@forEachIndexed
      val q = milli(w.units)
      if (q == 0L) return@forEachIndexed
      val at = atOf(w.createdAt, w.date)
      val id = w.id.ifEmpty { "w#$i" }
      val dmg = if (w.isAdjustment && q < 0 && w.adjKind.isNullOrEmpty() && w.createdAt > 0) damageRef[w.productId + "|" + w.createdAt] else null
      val user = w.userId?.trim()?.ifEmpty { null }
        ?: w.refId?.let { returnBy[it] }
        ?: dmg?.let { returnBy[it] }
        ?: LEGACY_USER
      val ref = w.purchaseId.orEmpty().ifEmpty { w.id }
      val loc = w.locationId.orEmpty()
      if (!w.isAdjustment && q > 0) {
        out += Event(
          id, "entry", "purchase_receipt", at, dayOf(w.date, at), w.productId, loc, q, user, ref,
          //  PRD-07: خریدِ به واحدِ خرید ارزشِ دقیقِ خودش را دارد (نه مقدار × بهای گردشدهٔ واحدِ پایه)
          //  ناجور با مقدار × بها (ویرایشِ بها روی نسخهٔ کهنه‌تر) ⇒ همان پیشین (`ReportLedger.exactValue`)
          costMinor = max(0L, ir.vil3ntec.tohid.core.report.ReportLedger.exactValue(w.costMinor, milli(w.units), minor(w.price)) ?: roundOr0(w.units * w.price * 100.0)),
          landedMinor = max(0L, landedMinorOf(w)),
          lot = lotRefOf(w),
        )
      } else if (w.isAdjustment && w.adjKind == Repack.KIND) {
        //  INV-21: بسته‌بندیِ دوباره/ساختِ بسته ⇒ سندِ `production` ِ هسته (علامت‌دار)؛ خروجی با بهای همان ردیف
        //  (بهای کارتن ÷ دانه، جمعِ اجزا — `Repack`)، ورودی به میانگینِ همان محل. ردیفی که تا امروز بوده این نوع را ندارد.
        //  سری فقط از ردیفِ «دستورِ ساخت و سریِ تولید» (`lotId`): ورودی همان سری، خروجی [lotRefOf] (`Repack` همان شناسه را می‌نویسد) و `costMinor`
        val lid = w.lotId?.takeIf { it.isNotEmpty() }
        out += Event(
          id, "entry", Repack.MOVE, at, dayOf(w.date, at), w.productId, loc, q, user, ref,
          reason = w.notes.trim().ifEmpty { "بسته‌بندیِ دوباره" },  // داده: دلیلِ رویدادِ دفترِ موجودی (ستونِ خروجی)
          costMinor = if (q > 0) max(0L, ir.vil3ntec.tohid.core.report.ReportLedger.exactValue(w.costMinor, milli(w.units), minor(w.price)) ?: roundOr0(w.units * w.price * 100.0)) else null,
          lotId = if (q < 0) lid else null, lot = if (q > 0 && lid != null) lotRefOf(w) else null,
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
    //  INV-10 (`CountAbsorbWiring`): جذب‌شده ⇒ اصلاحِ برعکس در لحظه و محلِ همان ردیفِ شمارش؛ پرچم خاموش ⇒ هیچ
    for (g in CountAbsorbWiring.gaps(d).values) {
      val w = d.warehouseEntries.find { it.id == g.lineId }?.takeIf { want(g.productId) } ?: continue
      val at = atOf(w.createdAt, w.date)
      out += Event("${CountAbsorbWiring.ABSORB_TYPE}:${w.id}", "entry", "adjustment", at, dayOf(w.date, at), g.productId, w.locationId.orEmpty(),
        -g.gapMilli, w.userId?.trim()?.ifEmpty { null } ?: LEGACY_USER, w.id, reason = StrStock.countAbsorbed, adjKind = "count")
    }

    d.stockMovements.forEachIndexed { t, x ->
      //  فقط ردیفِ خروجی (یا تک‌ردیفِ قدیمی)؛ ردیفِ `transfer_in` نیمهٔ دیگرِ همان انتقال است
      if ((x.type != TRANSFER_OUT && x.type != TRANSFER_LEGACY) || x.productId.isEmpty() || !want(x.productId)) return@forEachIndexed
      val to = x.toLocationId ?: return@forEachIndexed
      val tq = abs(milli(x.qty))
      val from = x.locationId.orEmpty()
      if (tq == 0L || from == to) return@forEachIndexed
      val at = atOf(x.createdAt, x.date)
      out += Event(
        x.id.ifEmpty { "m#$t" }, "transfer", "transfer", at, dayOf(x.date, at), x.productId, from, tq,
        x.userId.orEmpty().trim().ifEmpty { LEGACY_USER },
        x.refId.orEmpty().ifEmpty { x.id },
        reason = x.reason.orEmpty().trim().ifEmpty { x.notes.trim().ifEmpty { "انتقال" } },  // داده: دلیلِ رویدادِ دفترِ موجودی (ستونِ خروجی)
        lotId = x.lotId?.takeIf { it.isNotEmpty() },
        toLocation = to,
      )
    }

    //  فقط فاکتورها و برگشت‌های قلم‌های همین کالاها
    val items = ArrayList<IndexedValue<SaleItem>>()
    d.saleItems.forEachIndexed { k, si -> if (si.productId.isNotEmpty() && want(si.productId)) items += IndexedValue(k, si) }
    val saleIds = if (only == null) null else items.mapTo(HashSet()) { it.value.saleId }
    val itemIds = if (only == null) null else items.mapTo(HashSet()) { it.value.id.ifEmpty { "si#" + it.index } }
    val saleById = HashMap<String, Sale>()
    for (s in d.sales) if (saleIds == null || s.id in saleIds) saleById[s.id] = s
    val retByItem = HashMap<String, MutableList<SaleReturn>>()
    for (r in d.saleReturns) if (itemIds == null || r.saleItemId in itemIds) retByItem.getOrPut(r.saleItemId) { ArrayList() } += r
    for ((k, si) in items) {
      val sale = saleById[si.saleId]
      if (sale != null && sale.status == "cancelled") continue
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
        out += Event(sid, "sale", "adjustment", sat, sday, si.productId, loc, -sq, seller, si.saleId, reason = "قلمِ منفیِ فاکتور")  // داده: دلیلِ رویدادِ دفترِ موجودی (ستونِ خروجی)
      }
      var left = milli(si.returnedQty)
      if (left <= 0) continue
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
}
