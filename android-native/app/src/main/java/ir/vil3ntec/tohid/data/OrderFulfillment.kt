package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.money

/**
 *  تحویلِ سفارشِ آنلاین ⇒ یک **فروشِ عادی** در دفترِ همین دکان.
 *
 *  ⛔ تصمیمِ صاحبِ مخزن (۱۴۰۵/۰۷/۲۷): سمتِ مشتری (سایت و سرور) به موجودی دست نمی‌زند —
 *  نه رزرو، نه کسر. کالا را دکان از برنامهٔ خودش تحویل می‌دهد و کسر همین‌جاست، با همان
 *  `SalesEngine.record` ِ صندوق؛ بعد همان همگام‌سازیِ همیشگی موجودی را به سرور، ویترین و
 *  دستگاه‌های دیگر می‌رساند.
 *
 *  ── قاعده‌ها ─────────────────────────────────────────────────────────
 *  • لحظه: «مشتری گرفت» (`picked_up`)، «به پیک داده شد» (`out_for_delivery`) یا «رسید»
 *    (`delivered`) — هر کدام زودتر ([HANDED_OVER]). برگشت/لغو پس از آن «مرجوعیِ» عادیِ
 *    همین فاکتور است (لغوِ فاکتورِ اینترنتی از صندوق بسته است).
 *  • یکتایی: فروش `o-<سفارش>`؛ قلم‌ها، حرکت‌ها و ممیزی هم شناسهٔ ثابتِ مشتق از سفارش
 *    دارند. پیش از ساختن دفتر گشته می‌شود ([saleOf]: `orderId`، `o-…` یا فاکتورِ W ِ قدیمی
 *    `w-…`). دو دستگاه یا تلاشِ دوباره ⇒ **همان ردیف‌ها**؛ Sync v1 آن‌ها را روی هم می‌نشاند
 *    ⇒ یک فروش و یک بار کسر.
 *  • قیمت‌ها همانِ سفارش‌اند (به پول، دقیق)؛ هزینهٔ ارسال یک قلمِ آزاد است (بی اثر بر
 *    موجودی)؛ تخفیفِ سفارش تخفیفِ فاکتور.
 *  • پول ([paymentOf]) — همیشه `channel = online`:
 *      آنلاین‌پرداخت ⇒ `online` و ردیفِ `other` (تأییدشده فقط وقتی سفارش `paid` است)؛
 *      نقدِ هنگامِ تحویل با پیک ⇒ `cod` و ردیفِ نقدِ `custody = courier` (تا تسویهٔ پیک
 *      پولِ دخل نیست)؛ «تحویل در دکان» و نقد سرِ پیشخوان ⇒ `cash` و ردیفِ نقدِ بی‌امانت —
 *      تنها حالتی که پول به دخل می‌آید (`CashShift.paidAtCounter`).
 *  • موجودی: همان سیاستِ صندوق — بیش از موجودی (جمعِ قلم‌های هر کالا) فروخته نمی‌شود و
 *    پیامِ روشن برمی‌گردد؛ هرگز بی‌صدا نمی‌ماند.
 *  • مشتری: اگر شمارهٔ کامل آمده و همان شماره در «مشتریان/قرض‌داران» هست، فاکتور به او
 *    وصل می‌شود (بی بدهی). مشتریِ تازه ساخته نمی‌شود.
 *
 *  تابعِ خالص است (دفتر + سفارش ⇒ دفترِ تازه)؛ `OrderFulfillmentTest` و قرینه‌اش در
 *  `server/test/e2e/lib/native.mjs` (`fulfillOrder`) آن را می‌سنجند.
 */
object OrderFulfillment {

  /** همان `HANDED_OVER` ِ `server/src/lib/order-ledger.js` */
  val HANDED_OVER: Set<String> = setOf("picked_up", "out_for_delivery", "delivered")

  /** کالا بیرونِ دکان است ⇒ فروش باید در دفتر باشد — همان `OUT_OF_SHOP` ِ سرور */
  val OUT_OF_SHOP: Set<String> =
    setOf("picked_up", "out_for_delivery", "delivered", "delivery_failed", "retry_scheduled", "return_requested")

  const val CHANNEL = "online"
  const val FEE_LABEL = "هزینهٔ ارسال"

  fun saleIdOf(orderId: String): String = "o-$orderId"
  fun legacySaleIdOf(orderId: String): String = "w-$orderId"
  fun itemIdOf(orderId: String, lineNo: Int): String = "o-$orderId-$lineNo"
  fun feeItemIdOf(orderId: String): String = "o-$orderId-fee"
  fun moveIdOf(orderId: String, lineNo: Int): String = "o-$orderId-$lineNo-m"
  fun auditIdOf(orderId: String): String = "o-$orderId-a"

  /** فروشِ همین سفارش در دفتر (از همین دستگاه، دستگاهِ دیگر، یا فاکتورِ W ِ قدیمیِ سرور) */
  fun saleOf(d: ShopData, orderId: String): Sale? {
    if (orderId.isBlank()) return null
    val mine = saleIdOf(orderId)
    val legacy = legacySaleIdOf(orderId)
    return d.sales.firstOrNull { it.orderId == orderId || it.id == mine || it.id == legacy }
  }

  /** سفارشِ بیرون‌رفته‌ای که فروشش هنوز در این دفتر نیست */
  fun needsSale(d: ShopData, o: OrderDto): Boolean =
    !o.isTest && o.status in OUT_OF_SHOP && saleOf(d, o.id) == null

  sealed interface Result {
    data class Ok(val data: ShopData, val saleId: String) : Result
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
   *  فروشِ تحویلِ سفارش [o] (جزئیاتِ کامل با قلم‌ها). `series`/`seller`/`blockExpired` همان
   *  مقدارهای صندوقِ همین دستگاه.
   */
  fun fulfill(
    d: ShopData,
    o: OrderDto,
    today: String,
    now: Long,
    series: String = "",
    seller: String = "",
    blockExpired: Boolean = false,
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
    for ((pid, q) in need) {
      val available = ShopStore.stock(d, pid)
      if (q > available + 1e-9) return Result.Failed(tag + SalesEngine.shortageMessage(byId.getValue(pid), q, available))
    }

    val fee = o.deliveryFeeMinor
    val feeLine = if (fee > 0) SalesEngine.CartLine(feeItemIdOf(o.id), 1.0, fee / 100.0, FEE_LABEL) else null
    val cart = plan.map { it.cart } + listOfNotNull(feeLine)
    val phone = digits(o.customer.phone)
    val debtorId = if (phone.length >= 7) d.debtors.firstOrNull { digits(it.phone) == phone }?.id else null

    var n = 0
    val recorded = SalesEngine.record(
      d, cart,
      SalesEngine.Checkout(
        seller = seller,
        discountType = SalesEngine.DiscountType.AMOUNT,
        discountValue = o.discountMinor / 100.0,
        payment = SalesEngine.Payment.CASH,
        debtorId = debtorId,
        blockExpired = blockExpired,
      ),
      today, now, newId = { "o-tmp-${n++}" }, series = series, saleId = saleIdOf(o.id),
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
    val moves = newMoves.mapIndexed { i, m ->
      m.copy(id = moveIdOf(o.id, stocked[i].lineNo), notes = "${m.notes} — سفارشِ اینترنتی #${o.orderNo}")
    }

    val pay = paymentOf(o)
    val made = base.sales.last()
    val finalTotal = o.totalMinor / 100.0
    val sale = made.copy(
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
    val audit = newAudit.map {
      it.copy(
        id = auditIdOf(o.id),
        notes = "ثبت فروش فاکتور #${sale.invoiceLabel() ?: "0"} — سفارشِ اینترنتی #${o.orderNo} به مبلغ ${money(finalTotal)} افغانی",
      )
    }
    return Result.Ok(
      base.copy(
        sales = d.sales + sale,
        saleItems = d.saleItems + items,
        stockMovements = d.stockMovements + moves,
        auditLog = d.auditLog + audit,
      ),
      sale.id,
    )
  }
}
