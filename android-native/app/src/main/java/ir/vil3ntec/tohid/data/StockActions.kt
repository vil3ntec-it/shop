package ir.vil3ntec.tohid.data

import kotlinx.serialization.Serializable

/**
 *  کارهای «دفترِ موجودی» روی دفتر — همه خالص: ورودی دفترِ فعلی، خروجی دفترِ تازه.
 *
 *  هر کار فقط **ردیفِ تازه** می‌سازد (دفترِ حرکت فقط افزودنی است، INV-02) و
 *  همه از همان مجموعه‌های امروزِ دفتر می‌روند، پس با Sync v1 بی هیچ جدولِ تازه‌ای
 *  به دستگاه‌های دیگر می‌رسند:
 *   • تنظیمِ دکان   ⇒ `auditLog` با `type = stock_mode`، `refId = on|off`
 *   • «بررسی شد»    ⇒ `auditLog` با `type = stock_review_ack`، `refId` = شناسهٔ سند
 *   • انتقال        ⇒ `stockMovements` با `type = transfer` (جمعِ صفر؛ موتورِ امروز نمی‌بیندش)
 *   • شمارش         ⇒ ردیفِ اصلاحِ `warehouseEntries` (همان شکلِ انبارگردانیِ امروز)
 *                     + `adjKind` و `userId`ِ افزودنی
 */
object StockActions {

  sealed interface Result {
    data class Ok(val data: ShopData, val id: String = "") : Result
    data class Failed(val message: String) : Result
  }

  const val MAIN_LABEL = "فروشگاه (محلِ اصلی)"

  fun locationLabel(loc: String): String = loc.ifEmpty { MAIN_LABEL }

  /** نامِ نمایشی ⇐ شناسهٔ محل (نامِ محلِ اصلی ⇐ "") */
  fun locationOf(label: String): String = label.trim().let { if (it == MAIN_LABEL) StockLedger.MAIN else it }

  fun lotLabel(lotId: String): String = when {
    lotId.isEmpty() -> "بی سری"
    lotId.startsWith("w:") -> "محموله"
    else -> "سری $lotId"
  }

  val TYPE_LABEL = mapOf(
    "purchase_receipt" to "دریافتِ خرید", "sale" to "فروش", "customer_return" to "برگشت از مشتری",
    "supplier_return" to "برگشت به تأمین‌کننده", "adjustment" to "اصلاح", "count_adjust" to "تعدیلِ شمارش",
    "damage" to "خراب", "expired" to "منقضی", "transfer_out" to "انتقالِ خروجی", "transfer_in" to "انتقالِ ورودی",
  )

  val REASON_LABEL = mapOf(
    "negative_stock" to "موجودیِ منفی", "expired_sold" to "فروشِ منقضی", "unknown_cost" to "بهای نامعلوم",
  )

  /** دلیل‌های کسریِ شمارش (INV-04)؛ افزایشِ شمارش همیشه «اصلاحِ شمارش» است */
  val LOSS_KINDS = listOf("count" to "اصلاحِ شمارش", "damage" to "خراب", "lost" to "گم‌شده", "expired" to "منقضی")

  /** نوع‌های اصلاحِ دستی (INV-04) */
  val ADJ_KINDS = listOf("other" to "دیگر", "count" to "اصلاحِ شمارش", "damage" to "خراب", "lost" to "گم‌شده", "expired" to "منقضی")

  fun userLabel(u: String): String = if (u == StockLedger.LEGACY_USER) "ثبتِ قدیمی" else u

  /* ------------------------------------------------------------ تنظیم */

  fun setMode(d: ShopData, on: Boolean, user: String, today: String, now: Long, newId: () -> String): ShopData =
    d.copy(
      auditLog = d.auditLog + AuditEntry(
        id = newId(), type = StockLedger.MODE_TYPE, date = today, refId = if (on) "on" else "off", createdAt = now,
        notes = "موتورِ تازهٔ موجودی و بها: ${if (on) "روشن" else "خاموش"}" + (if (user.isNotBlank()) " — ${user.trim()}" else ""),
      ),
    )

  /* ------------------------------------------------------- وظیفهٔ بررسی */

  fun ack(
    d: ShopData,
    view: StockLedger.View,
    taskIds: Collection<String>,
    user: String,
    today: String,
    now: Long,
    newId: () -> String,
  ): ShopData {
    val names = d.products.associate { it.id to it.name }
    val rows = view.tasks.filter { it.id in taskIds && !it.acked }.distinctBy { it.id }.map { t ->
      AuditEntry(
        id = newId(), type = StockLedger.ACK_TYPE, date = today, refId = t.id, createdAt = now,
        notes = "بررسیِ موجودیِ «${names[t.productId].orEmpty()}»: " + t.reasons.joinToString("، ") { REASON_LABEL[it] ?: it } +
          (if (user.isNotBlank()) " — ${user.trim()}" else ""),
      )
    }
    return if (rows.isEmpty()) d else d.copy(auditLog = d.auditLog + rows)
  }

  /* ------------------------------------------------------------ انتقال */

  fun transfer(
    d: ShopData,
    view: StockLedger.View,
    productId: String,
    from: String,
    to: String,
    quantity: Double,
    lotId: String?,
    reason: String,
    user: String,
    today: String,
    now: Long,
    newId: () -> String,
  ): Result {
    val product = d.products.find { it.id == productId } ?: return Result.Failed("کالا را انتخاب کنید")
    val dest = to.trim()
    if (from == dest) return Result.Failed("مقصد باید با مبدأ فرق کند")
    if (quantity.isNaN() || quantity <= 0) return Result.Failed("مقدار درست نیست")
    val q = StockLedger.milli(quantity)
    val chk = StockLedger.checkTransfer(view, productId, from, dest, q, lotId, today)
    if (!chk.ok) {
      return Result.Failed(
        if (chk.error == "insufficient_stock") "در «${locationLabel(from)}» فقط ${ir.vil3ntec.tohid.qty((chk.availableMilli ?: 0L) / 1000.0)} ${product.unit} هست"
        else "انتقال ممکن نیست",
      )
    }
    val id = newId()
    val why = reason.trim()
    val mv = StockMovement(
      id = id, productId = productId, type = "transfer", qty = q / 1000.0, date = today,
      notes = why.ifEmpty { "انتقال بین محل‌ها" }, refId = id, createdAt = now,
      userId = user.trim().ifEmpty { null }, reason = why.ifEmpty { null },
      locationId = from, toLocationId = dest, lotId = lotId?.takeIf { it.isNotEmpty() },
    )
    return Result.Ok(
      d.copy(
        stockMovements = d.stockMovements + mv,
        auditLog = d.auditLog + AuditEntry(
          id = newId(), type = "stock_transfer", date = today, refId = id, createdAt = now,
          notes = "انتقالِ ${ir.vil3ntec.tohid.qty(q / 1000.0)} «${product.name}» از ${locationLabel(from)} به ${locationLabel(dest)}" +
            (if (why.isNotEmpty()) " — $why" else ""),
        ),
      ),
      id,
    )
  }

  /* ------------------------------------------------- شمارش (INV-05/09/10) */

  /**
   *  پیش‌نویسِ شمارشِ همین دستگاه. `snapshot` موجودیِ لحظهٔ شروع است (هزارم)؛ اختلاف
   *  نسبت به همان حساب می‌شود، پس فروش و خریدِ وسطِ شمارش اختلافِ دروغ نمی‌سازد.
   */
  @Serializable
  data class CountDraft(
    val startedAt: Long = 0,
    val snapshot: Map<String, Long> = emptyMap(),
    /** همان متنِ تایپ‌شده (رقمِ فارسی هم) */
    val counts: Map<String, String> = emptyMap(),
    /** دلیلِ کسری برای هر کالا: count · damage · lost · expired */
    val reasons: Map<String, String> = emptyMap(),
    val note: String = "",
  )

  fun startCount(d: ShopData, view: StockLedger.View, now: Long): CountDraft =
    CountDraft(startedAt = now, snapshot = d.products.associate { it.id to StockLedger.productView(view, it.id).qtyMilli })

  /** عددِ شمرده ⇐ هزارم؛ خالی ⇒ null؛ نادرست یا منفی ⇒ -1 */
  fun parseCount(raw: String?): Long? {
    val s = ir.vil3ntec.tohid.normalizeDigits(raw.orEmpty()).trim()
    if (s.isEmpty()) return null
    val n = s.toDoubleOrNull() ?: return -1
    return if (n.isFinite() && n >= 0) StockLedger.milli(n) else -1
  }

  fun counted(draft: CountDraft): Map<String, Long> =
    draft.counts.mapNotNull { (k, v) -> parseCount(v)?.takeIf { it >= 0 }?.let { k to it } }.toMap()

  fun invalidCounts(draft: CountDraft): List<String> = draft.counts.filter { (_, v) -> parseCount(v) == -1L }.keys.toList()

  fun countReview(d: ShopData, view: StockLedger.View, draft: CountDraft): StockLedger.CountReview =
    StockLedger.countReview(view, d.products.filter { it.active }.map { it.id }, counted(draft), draft.snapshot)

  fun saveCount(
    d: ShopData,
    view: StockLedger.View,
    draft: CountDraft,
    user: String,
    today: String,
    now: Long,
    newId: () -> String,
  ): Result {
    if (invalidCounts(draft).isNotEmpty()) return Result.Failed("عددِ شمرده‌شدهٔ یک کالا درست نیست")
    val lines = countReview(d, view, draft).lines.filter { it.diffMilli != 0L }
    if (lines.isEmpty()) return Result.Failed("اختلافی نیست — موجودیِ شمرده‌شده با برنامه یکی است")
    val note = draft.note.trim()
    val byId = d.products.associateBy { it.id }
    val entries = ArrayList<WarehouseEntry>()
    val moves = ArrayList<StockMovement>()
    val audit = ArrayList<AuditEntry>()
    for (l in lines) {
      val p = byId[l.productId]
      val kind = if (l.diffMilli > 0) "count" else draft.reasons[l.productId]?.takeIf { k -> LOSS_KINDS.any { it.first == k } } ?: "count"
      val why = listOf("انبارگردانی", StockLedger.ADJ_REASON.getValue(kind), note).filter { it.isNotEmpty() }.joinToString(" — ")
      val id = newId()
      val units = l.diffMilli / 1000.0
      entries += WarehouseEntry(
        id = id, productId = l.productId, units = units, unit = p?.unit.orEmpty(), price = 0.0, date = today, notes = why,
        isAdjustment = true, createdAt = now, adjKind = kind, userId = user.trim().ifEmpty { null },
      )
      moves += StockMovement(
        id = newId(), productId = l.productId, type = "adjustment", qty = units, date = today, notes = why, refId = id,
        createdAt = now, userId = user.trim().ifEmpty { null }, reason = why,
      )
      audit += AuditEntry(
        id = newId(), type = "stock_adjustment", date = today, refId = id, createdAt = now,
        notes = "اصلاح موجودی «${p?.name.orEmpty()}» به مقدار ${if (units > 0) "+" else "−"}${ir.vil3ntec.tohid.qty(kotlin.math.abs(units))} — دلیل: $why",
      )
    }
    return Result.Ok(
      d.copy(
        warehouseEntries = d.warehouseEntries + entries,
        stockMovements = d.stockMovements + moves,
        auditLog = d.auditLog + audit,
      ),
      lines.size.toString(),
    )
  }

  /* ------------------------------------------------- اسکنِ سری سرِ صندوق */

  /**
   *  بارکدِ ناشناس که «سری»ِ یک کالاست ⇒ (کالا، سری). فقط وقتی موتور روشن است؛
   *  سری باید هنوز موجودی داشته باشد؛ سریِ هم‌نامِ دو کالا ⇒ null (حدس زده نمی‌شود).
   */
  fun lotForCode(d: ShopData, code: String): Pair<String, String>? {
    val c = code.trim()
    if (c.isEmpty() || !StockLedger.isOn(d)) return null
    val owners = d.warehouseEntries.filter { it.batch.trim() == c && it.productId.isNotEmpty() }.map { it.productId }.distinct()
    if (owners.size != 1) return null
    val pid = owners[0]
    val pv = StockLedger.productView(StockLedger.replay(d, listOf(pid)), pid)
    return if (pv.lots.any { it.lotId == c && it.qtyMilli > 0 }) pid to c else null
  }
}
