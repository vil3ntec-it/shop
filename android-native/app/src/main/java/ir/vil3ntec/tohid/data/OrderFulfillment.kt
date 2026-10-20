package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.money

/**
 *  تحویلِ سفارشِ آنلاین ⇒ یک **فروشِ عادی** در دفترِ همین دکان؛ برگشتِ آن به دکان ⇒ لغوِ همان فروش.
 *
 *  ⛔ تصمیمِ صاحبِ مخزن (۱۴۰۵/۰۷/۲۷): سمتِ مشتری (سایت و سرور) به موجودی دست نمی‌زند —
 *  نه رزرو، نه کسر. کالا را دکان از برنامهٔ خودش تحویل می‌دهد و کسر همین‌جاست، با همان
 *  `SalesEngine.record` ِ صندوق؛ بعد همان همگام‌سازیِ همیشگی موجودی را به سرور، ویترین و
 *  دستگاه‌های دیگر می‌رساند.
 *
 *  ── قاعده‌ها ─────────────────────────────────────────────────────────
 *  • لحظه: «مشتری گرفت» (`picked_up`)، «به پیک داده شد» (`out_for_delivery`) یا «رسید»
 *    (`delivered`) — هر کدام زودتر ([HANDED_OVER]). زمان و روزِ فاکتور = زمانِ همان گذار
 *    ([handedOverAt])، نه لحظهٔ ثبت ⇒ صندوق و گزارشِ روز درست.
 *  • یکتایی و یکسانی: فروش `o-<سفارش>` با سریِ [SERIES] (`O`) و **شمارهٔ همان سفارش**؛ قلم‌ها،
 *    حرکت‌ها و ممیزی شناسهٔ ثابتِ مشتق از سفارش دارند و شمارندهٔ فاکتورِ دستگاه دست نمی‌خورد.
 *    دو دستگاه یا تلاشِ دوباره ⇒ همان ردیف‌ها با همان مقدارها؛ Sync v1 روی هم می‌نشاندشان.
 *    پیش از ساختن دفتر گشته می‌شود ([saleOf]: `orderId`، `o-…` یا فاکتورِ W ِ قدیمی `w-…`).
 *  • برگشت به دکان (`returned_to_store`/`restocked`) ⇒ [reverse] همان فروش را **لغو** می‌کند:
 *    موجودی یک بار برمی‌گردد، صندوق و گزارش دیگر نمی‌شمارندش. (مرجوعی نه: `returnedQty` در
 *    Sync v1 شمارندهٔ `$inc` است و دو دستگاه آن را دو بار می‌افزودند؛ وضعیت تکرارپذیر است.)
 *  • قیمت‌ها همانِ سفارش‌اند (به پول، دقیق)؛ هزینهٔ ارسال یک قلمِ آزاد است (بی اثر بر
 *    موجودی)؛ تخفیفِ سفارش تخفیفِ فاکتور.
 *  • پول ([paymentOf]) — همیشه `channel = online`:
 *      آنلاین‌پرداخت ⇒ `online` و ردیفِ `other`؛ نقدِ هنگامِ تحویل با پیک ⇒ `cod` و ردیفِ
 *      نقدِ `custody = courier` (تا تسویهٔ پیک پولِ دخل نیست)؛ «تحویل در دکان» و نقد سرِ
 *      پیشخوان ⇒ `cash` و ردیفِ نقدِ بی‌امانت — تنها حالتی که به دخل می‌آید
 *      (`CashShift.paidAtCounter`).
 *  • موجودی: پیش از گذار (`allowShortage = false`) همان سیاستِ صندوق — بیش از موجودی (جمعِ
 *    قلم‌های هر کالا) نه، با پیامِ روشن. پس از تحویل (کالا رفته است) فروش **ثبت می‌شود** و
 *    کمبود فقط گزارش می‌شود ([Result.Ok.short]) — INV-19: «ثبت‌شده رد نمی‌شود».
 *  • مشتری: اگر شمارهٔ کامل آمده و همان شماره در «مشتریان/قرض‌داران» هست، فاکتور به او
 *    وصل می‌شود (بی بدهی). مشتریِ تازه ساخته نمی‌شود.
 *
 *  تابعِ خالص است (دفتر + سفارش ⇒ دفترِ تازه)؛ `OrderFulfillmentTest` و قرینه‌اش در
 *  `server/test/e2e/lib/native.mjs` (`fulfillOrder`/`reverseOrder`) آن را می‌سنجند.
 */
object OrderFulfillment {

  /** همان `HANDED_OVER` ِ `server/src/lib/order-ledger.js` */
  val HANDED_OVER: Set<String> = setOf("picked_up", "out_for_delivery", "delivered")

  /** کالا بیرونِ دکان است ⇒ فروش باید در دفتر باشد — همان `OUT_OF_SHOP` ِ سرور */
  val OUT_OF_SHOP: Set<String> =
    setOf("picked_up", "out_for_delivery", "delivered", "delivery_failed", "retry_scheduled", "return_requested")

  /** کالا به دکان برگشت ⇒ فروشِ تحویل لغو می‌شود — همان `BACK_IN_SHOP` ِ سرور */
  val BACK_IN_SHOP: Set<String> = setOf("returned_to_store", "restocked")

  const val CHANNEL = "online"
  /** سریِ فاکتورِ تحویلِ سفارش — هیچ دستگاهی آن را نمی‌گیرد (`doc-series.LETTERS` بی O) */
  const val SERIES = "O"
  const val FEE_LABEL = "هزینهٔ ارسال"
  const val REVERSE_REASON = "سفارشِ اینترنتی به دکان برگشت"

  fun saleIdOf(orderId: String): String = "o-$orderId"
  fun legacySaleIdOf(orderId: String): String = "w-$orderId"
  fun itemIdOf(orderId: String, lineNo: Int): String = "o-$orderId-$lineNo"
  fun feeItemIdOf(orderId: String): String = "o-$orderId-fee"
  fun moveIdOf(orderId: String, lineNo: Int): String = "o-$orderId-$lineNo-m"
  fun auditIdOf(orderId: String): String = "o-$orderId-a"
  fun reverseAuditIdOf(orderId: String): String = "o-$orderId-x"

  /** فروشِ همین سفارش در دفتر (از همین دستگاه، دستگاهِ دیگر، یا فاکتورِ W ِ قدیمیِ سرور) */
  fun saleOf(d: ShopData, orderId: String): Sale? {
    if (orderId.isBlank()) return null
    val mine = saleIdOf(orderId)
    val legacy = legacySaleIdOf(orderId)
    return d.sales.firstOrNull { it.orderId == orderId || it.id == mine || it.id == legacy }
  }

  /** نخستین گذارِ تحویل (زمانِ سرور) — از فهرستِ `handover` یا خطِ زمانیِ سفارش */
  fun handedOverAt(o: OrderDto): Long? =
    o.handedOverAt ?: o.events.filter { it.kind == "status" && it.toStatus in HANDED_OVER }.minOfOrNull { it.at }

  /**
   *  سفارشِ بیرون‌رفته‌ای که فروشش هنوز در این دفتر نیست — فقط برای **نمایش**؛ هر ثبت پیش از
   *  انجام از سرور هم پرسیده می‌شود (`OrdersLive.postById`). [since]: کفِ سرور
   *  (`handoverSince`) — تحویلِ پیش از به‌کارگیریِ این قابلیت هرگز.
   */
  fun needsSale(d: ShopData, o: OrderDto, since: Long): Boolean {
    if (o.isTest || o.status !in OUT_OF_SHOP || saleOf(d, o.id) != null) return false
    val at = handedOverAt(o) ?: return false
    return at >= since
  }

  /** سفارشِ برگشته به دکان که فروشِ لغونشدهٔ `o-…` دارد */
  fun needsReverse(d: ShopData, o: OrderDto): Boolean =
    !o.isTest && o.status in BACK_IN_SHOP && d.sales.any { it.id == saleIdOf(o.id) && it.status != "cancelled" }

  sealed interface Result {
    /** [short]: کالاهایی که پس از تحویل از موجودیِ دفتر بیشتر بودند (موجودی منفی شد) */
    data class Ok(val data: ShopData, val saleId: String, val short: List<String> = emptyList()) : Result
    /** پیش‌تر ثبت شده — هیچ تغییری نیست */
    data class Already(val saleId: String) : Result
    data class Failed(val message: String) : Result
  }

  data class Pay(val method: String, val rows: List<SalePayment>)

  fun paymentOf(o: OrderDto): Pay = when {
    o.paymentMethod == "online" -> {
      val paid = o.paymentStatus == "paid"
      Pay("online", listOf(SalePayment(
        method = "online", kind = "other", amountMinor = o.totalMinor,
        status = if (paid) "confirmed" else "pending", verification = if (paid) "provider" else null,
      )))
    }
    o.fulfillment == "delivery" -> Pay("cod", listOf(SalePayment(
      method = "cod", kind = "cash", amountMinor = o.totalMinor, status = "pending", custody = "courier",
    )))
    else -> Pay("cash", listOf(SalePayment(method = "cash", kind = "cash", amountMinor = o.totalMinor, status = "confirmed")))
  }

  /** مقدارِ قلمِ سفارش به واحدِ موجودی — واحدِ فروش (مثلاً کارتن) با ضریبِ کالا */
  fun stockQty(p: Product, unit: String, qtyMilli: Long): Double {
    val q = qtyMilli / 1000.0
    return if (unit.isNotBlank() && unit == p.saleUnit && unit != p.unit) SalesEngine.toStockQty(p, q) else q
  }

  private fun digits(s: String?): String = s.orEmpty().filter { it.isDigit() }.takeLast(9)

  private fun round2(x: Double): Double = Math.round(x * 100.0) / 100.0

  /**
   *  فروشِ تحویلِ سفارش [o] (جزئیاتِ کامل با قلم‌ها). [today]/[now] = روز و زمانِ **گذارِ
   *  تحویل** ([handedOverAt])؛ `seller`/`blockExpired` همان مقدارهای صندوقِ همین دستگاه.
   *  [allowShortage]: پس از تحویل (کالا رفته) ⇒ ثبت با کمبودِ گزارش‌شده؛ پیش از گذار ⇒ نه.
   */
  fun fulfill(
    d: ShopData,
    o: OrderDto,
    today: String,
    now: Long,
    seller: String = "",
    blockExpired: Boolean = false,
    allowShortage: Boolean = false,
  ): Result {
    if (o.id.isBlank()) return Result.Failed("سفارش معتبر نیست")
    saleOf(d, o.id)?.let { return Result.Already(it.id) }
    if (o.isTest) return Result.Failed("سفارشِ آزمایشی فروش و کسرِ موجودی ندارد")
    val lines = o.lines.sortedBy { it.lineNo }
    if (lines.isEmpty()) return Result.Failed("قلم‌های سفارش #${o.orderNo} نیامد؛ سفارش را دوباره باز کنید")
    val tag = "سفارش #${o.orderNo}: "

    class Plan(val itemId: String, val lineNo: Int, val cart: SalesEngine.CartLine, val totalMinor: Long, val product: Product?)

    val byId = d.products.associateBy { it.id }
    val plan = ArrayList<Plan>()
    for (l in lines) {
      val total = l.lineTotalMinor / 100.0
      val p = l.productId.takeIf { it.isNotEmpty() }?.let { byId[it] }
      if (p == null) {
        //  کالایی که در دفترِ دکان نیست (یا قلمِ آزادِ سفارشِ دستی) ⇒ قلمِ آزاد، بی اثر بر موجودی
        val q = l.qtyMilli / 1000.0
        if (!(q > 0)) return Result.Failed(tag + "مقدارِ «${l.name}» درست نیست")
        val id = itemIdOf(o.id, l.lineNo)
        plan += Plan(id, l.lineNo, SalesEngine.CartLine(id, q, round2(total / q), l.name.ifBlank { "قلمِ سفارش" }), l.lineTotalMinor, null)
      } else {
        val q = stockQty(p, l.unit, l.qtyMilli)
        if (!(q > 0)) return Result.Failed(tag + "مقدارِ «${p.name}» درست نیست")
        plan += Plan(itemIdOf(o.id, l.lineNo), l.lineNo, SalesEngine.CartLine(p.id, q, round2(total / q)), l.lineTotalMinor, p)
      }
    }

    //  سیاستِ موجودیِ صندوق، روی جمعِ قلم‌های هر کالا (دو قلمِ یک کالا با هم)
    val need = LinkedHashMap<String, Double>()
    for (pl in plan) pl.product?.let { need[it.id] = (need[it.id] ?: 0.0) + pl.cart.quantity }
    val short = ArrayList<String>()
    val cover = ArrayList<WarehouseEntry>()
    for ((pid, q) in need) {
      val available = ShopStore.stock(d, pid)
      if (q <= available + 1e-9) continue
      val msg = SalesEngine.shortageMessage(byId.getValue(pid), q, available)
      if (!allowShortage) return Result.Failed(tag + msg)
      short += msg
      //  فقط برای گذشتن از سنجشِ صندوق در `record`؛ این ردیف هرگز در دفتر نمی‌نشیند
      cover += WarehouseEntry(id = "o-cover-$pid", productId = pid, units = q - available)
    }

    val fee = o.deliveryFeeMinor
    val feeLine = if (fee > 0) SalesEngine.CartLine(feeItemIdOf(o.id), 1.0, fee / 100.0, FEE_LABEL) else null
    val cart = plan.map { it.cart } + listOfNotNull(feeLine)
    val phone = digits(o.customer.phone)
    val debtorId = if (phone.length >= 7) d.debtors.firstOrNull { digits(it.phone) == phone }?.id else null

    var n = 0
    val recorded = SalesEngine.record(
      if (cover.isEmpty()) d else d.copy(warehouseEntries = d.warehouseEntries + cover),
      cart,
      SalesEngine.Checkout(
        seller = seller,
        discountType = SalesEngine.DiscountType.AMOUNT,
        discountValue = o.discountMinor / 100.0,
        payment = SalesEngine.Payment.CASH,
        debtorId = debtorId,
        //  کالای رفته منقضی بوده یا نه، دیگر سنجه‌ای برای ثبت نیست
        blockExpired = blockExpired && !allowShortage,
      ),
      today, now, newId = { "o-tmp-${n++}" }, series = SERIES, saleId = saleIdOf(o.id),
    )
    val base = when (recorded) {
      is SalesEngine.Result.Failed -> return Result.Failed(tag + recorded.message)
      is SalesEngine.Result.Ok -> recorded.data
    }

    //  شناسه‌های ثابت و مبلغ‌های دقیقِ سفارش روی همان ردیف‌هایی که صندوق ساخت
    val newItems = base.saleItems.drop(d.saleItems.size)
    val newMoves = base.stockMovements.drop(d.stockMovements.size)
    val newAudit = base.auditLog.drop(d.auditLog.size)
    val rows = plan.map { it.itemId to it.totalMinor } + listOfNotNull(feeLine?.let { feeItemIdOf(o.id) to fee })
    if (newItems.size != rows.size) return Result.Failed(tag + "قلم‌های فاکتور با سفارش نمی‌خواند")
    val items = newItems.mapIndexed { i, it ->
      val total = rows[i].second / 100.0
      it.copy(id = rows[i].first, totalPrice = total, unitPrice = if (it.quantity > 0) round2(total / it.quantity) else total)
    }
    val stocked = plan.filter { it.product != null }
    if (newMoves.size != stocked.size) return Result.Failed(tag + "حرکت‌های انبار با سفارش نمی‌خواند")

    val pay = paymentOf(o)
    val made = base.sales.last()
    val finalTotal = o.totalMinor / 100.0
    //  شمارهٔ فاکتور = شمارهٔ سفارش (O-12) — روی هر دستگاه یکی، شمارندهٔ دستگاه دست نمی‌خورد
    val sale = made.copy(
      invoiceNumber = o.orderNo.toInt(),
      series = SERIES,
      total = rows.sumOf { it.second } / 100.0,
      discountType = "amount",
      discountValue = o.discountMinor / 100.0,
      discount = o.discountMinor / 100.0,
      finalTotal = finalTotal,
      paymentMethod = pay.method,
      paidAmount = finalTotal,
      remaining = 0.0,
      debtGiven = 0.0,
      channel = CHANNEL,
      orderId = o.id,
      orderNo = o.orderNo,
      payments = pay.rows,
    )
    val label = sale.invoiceLabel() ?: "0"
    val moves = newMoves.mapIndexed { i, m ->
      m.copy(id = moveIdOf(o.id, stocked[i].lineNo), notes = "فاکتور #$label — سفارشِ اینترنتی #${o.orderNo}")
    }
    val audit = newAudit.map {
      it.copy(
        id = auditIdOf(o.id),
        notes = "ثبت فروش فاکتور #$label — سفارشِ اینترنتی #${o.orderNo} به مبلغ ${money(finalTotal)} افغانی",
      )
    }
    return Result.Ok(
      d.copy(
        sales = d.sales + sale,
        saleItems = d.saleItems + items,
        stockMovements = d.stockMovements + moves,
        auditLog = d.auditLog + audit,
        //  ردیف‌های دیگرِ `record` (فهرستِ واحد/دسته و …) همان؛ شمارنده و ورودِ انبار دستِ دفترِ اصلی
        transactions = base.transactions,
      ),
      sale.id,
      short,
    )
  }

  /**
   *  سفارشی که پس از بیرون رفتن به دکان برگشت ⇒ لغوِ فروشِ `o-…` ِ همان سفارش (همان کارِ
   *  `SalesEngine.cancel`، که برای فاکتورِ اینترنتی از صندوق بسته است) با شناسه‌های ثابت.
   *  بی فروش یا فروشِ لغوشده ⇒ [Result.Already]. فاکتورِ W ِ قدیمی دست نمی‌خورد.
   */
  fun reverse(d: ShopData, o: OrderDto, today: String, now: Long): Result {
    val saleId = saleIdOf(o.id)
    val sale = d.sales.firstOrNull { it.id == saleId } ?: return Result.Already(saleId)
    if (sale.status == "cancelled") return Result.Already(saleId)
    val label = sale.invoiceLabel() ?: "0"
    val back = d.saleItems
      .filter { it.saleId == saleId && it.productId.isNotBlank() && it.quantity - it.returnedQty > 0 }
      .map {
        StockMovement(
          id = "${it.id}-x", productId = it.productId, type = "sale_cancel", qty = it.quantity - it.returnedQty,
          date = today, notes = "لغو فروش — فاکتور #$label — $REVERSE_REASON", refId = saleId, createdAt = now,
        )
      }
    val updated = sale.copy(status = "cancelled", cancelReason = REVERSE_REASON)
    return Result.Ok(
      d.copy(
        sales = d.sales.map { if (it.id == saleId) updated else it },
        stockMovements = d.stockMovements + back,
        auditLog = d.auditLog + AuditEntry(
          id = reverseAuditIdOf(o.id), type = "cancel_sale", date = today, refId = saleId,
          notes = "لغو فروش فاکتور #$label — سفارشِ اینترنتی #${o.orderNo}: $REVERSE_REASON", createdAt = now,
        ),
      ),
      saleId,
    )
  }
}
