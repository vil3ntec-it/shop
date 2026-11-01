package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.core.model.OrderPermsDto
import ir.vil3ntec.tohid.core.orders.OrderRefunds
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
 *    حرکت یا ممیزیِ لغوی که از دستگاهِ دیگر پیش‌تر رسیده دوباره ساخته نمی‌شود (شناسهٔ تکراری نه).
 *    فاکتوری که پیش‌تر «مرجوعیِ» دستی گرفته ([reverseBlock]) ⇒ لغوِ خاموش **نه**: پولِ مرجوعی از دخل
 *    رفته و کالا یک بار برگشته؛ لغو آن را دوباره برمی‌گرداند ⇒ [Result.Review] برای صاحب.
 *  • بازپرداختِ سفارشِ تحویل‌شده (کامل، ردیفِ دفترِ بازپرداخت، پولِ برگشتهٔ درگاه) ⇒ [refund] سندِ برگشتِ
 *    همان فروش (مدلِ RET، شناسهٔ ثابتِ `core/orders/OrderRefunds`، روزِ بازپرداخت)؛ کالا فقط با [restock].
 *  • کف: فقط سفارشی که **نخستین** گذارِ تحویلش پس از کفِ دکان است خودکار فروش می‌گیرد؛ «ارسال»
 *    پیش از کف ⇒ فهرستِ بررسیِ صاحب ([REVIEW_BEFORE_FLOOR]).
 *  • قیمت‌ها همانِ سفارش‌اند (به پول، دقیق)؛ هزینهٔ ارسال یک قلمِ آزاد است (بی اثر بر
 *    موجودی)؛ تخفیفِ سفارش تخفیفِ فاکتور.
 *  • پول ([paymentOf]) — همیشه `channel = online`:
 *      آنلاین‌پرداخت ⇒ `online` و ردیفِ `other`؛ نقدِ هنگامِ تحویل با پیک ⇒ `cod` و ردیفِ
 *      نقدِ `custody = courier` (تا تسویهٔ پیک پولِ دخل نیست)؛ کالایی که مشتری **سرِ پیشخوان**
 *      گرفت ([atCounter]: «تحویل در دکان»، یا سفارشِ «ارسال» که مشتری خودش در دکان گرفت) و
 *      نقد داد ⇒ `cash` و ردیفِ نقدِ بی‌امانت — تنها حالتی که به دخل می‌آید
 *      (`CashShift.paidAtCounter`). مرجوعیِ دستیِ بعدی همیشه از دخل است (`CashShift`).
 *  • اجازه ([allowed]): پرچمِ سرور برای همین کاربر و همین سفارش (`recordable`/`reversible`)
 *    بر اجازهٔ کلیِ نقش جلو می‌افتد — سقفِ درصد/مبلغِ تخفیفِ هر کاربر همان‌جا سنجیده شده.
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

  /**
   *  کالا بیرونِ دکان است ⇒ فروش باید در دفتر باشد — همان `OUT_OF_SHOP` ِ سرور.
   *  `refunded_partial`: بخشی از پول برگشت ولی کالا پیشِ مشتری ماند.
   */
  val OUT_OF_SHOP: Set<String> = setOf(
    "picked_up", "out_for_delivery", "delivered", "delivery_failed", "retry_scheduled", "return_requested",
    "refunded_partial",
  )

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

  /**
   *  این دستگاه کارِ ردیفِ [o] را می‌تواند بنویسد؟ (Sync v1 همان را از این کاربر می‌پذیرد.)
   *  `sale`: پرچمِ سرور `recordable`، وگرنه (سرورِ کهنه) اجازهٔ کلیِ نقش با تخفیف ⇒ `saleDiscount`؛
   *  `reverse`: `reversible`، وگرنه `saleCancel`. بی `recordSale` هیچ.
   */
  fun allowed(o: OrderDto, perms: OrderPermsDto): Boolean {
    if (!perms.recordSale) return false
    return if (o.action == "reverse") o.reversible ?: perms.saleCancel
    else o.recordable ?: (o.discountMinor <= 0 || perms.saleDiscount)
  }

  /**
   *  کالا سرِ پیشخوانِ دکان به دستِ مشتری رسید — نخستین گذارِ تحویلش «مشتری گرفت» (`picked_up`)
   *  بود، نه «به پیک داده شد». نوعِ سفارش («ارسال» یا «تحویل در دکان») مهم نیست.
   */
  fun atCounter(o: OrderDto): Boolean {
    val first = o.events.filter { it.kind == "status" && it.toStatus in HANDED_OVER }.minByOrNull { it.at }
    return first?.toStatus == "picked_up" || (first == null && o.status == "picked_up")
  }

  /** دلیلِ فهرستِ بررسی برای ردیفِ `review` ِ سرور (`reviewCode = before_floor`) */
  const val REVIEW_BEFORE_FLOOR = "پیش از راه‌اندازی تحویل شده؛ اگر دستی ثبت نکرده‌اید، ثبت کنید"

  /** فروشِ این سفارش (از هر دستگاه) در دفتر هست ⇒ هشدارِ «ثبت نشد» دیگر لازم نیست */
  fun settled(d: ShopData, orderId: String): Boolean = saleOf(d, orderId) != null

  /** یکی از این کالاها هنوز موجودیِ منفی دارد ⇒ هشدارِ «کمبود» بماند */
  fun stillShort(d: ShopData, productIds: List<String>): Boolean =
    productIds.any { ShopStore.stock(d, it) < -1e-9 }

  /**
   *  چرا [reverse] نباید فروشِ این سفارشِ برگشته را خاموش لغو کند — یا null. فاکتوری که پیش‌تر
   *  «مرجوعیِ» دستی گرفته (صندوق‌دار کالای پیک را به‌جای «برگشت به دکان» مرجوع زد): پولِ مرجوعی از
   *  دخل رفته و کالا یک بار برگشته؛ لغو موجودی را دوباره برمی‌گرداند و فروش را از درآمد برمی‌دارد.
   */
  fun reverseBlock(d: ShopData, o: OrderDto): String? {
    val saleId = saleIdOf(o.id)
    val returned = d.saleItems.any { it.saleId == saleId && it.returnedQty > 1e-9 } || d.saleReturns.any { it.saleId == saleId }
    if (!returned) return null
    return "سفارش #${o.orderNo} به دکان برگشت ولی برای فاکتورش پیش‌تر «مرجوعیِ» دستی ثبت شده — لغوِ خودکار نشد. " +
      "مرجوعی پول را از دخل برداشته و کالا را یک بار برگردانده؛ صاحب دخل و موجودی را بررسی کند."
  }

  sealed interface Result {
    /**
     *  [short]: کالاهایی که پس از تحویل از موجودیِ دفتر بیشتر بودند (موجودی منفی شد)؛
     *  [shortIds]: شناسهٔ همان کالاها (هشدار با درست شدنِ موجودی می‌رود)
     */
    data class Ok(
      val data: ShopData,
      val saleId: String,
      val short: List<String> = emptyList(),
      val shortIds: List<String> = emptyList(),
    ) : Result
    /** کارِ خودکار انجام نشد؛ صاحب باید ببیند ([message] دلیل است) — هیچ تغییری نیست */
    data class Review(val saleId: String, val message: String) : Result
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
    o.fulfillment == "delivery" && !atCounter(o) -> Pay("cod", listOf(SalePayment(
      method = "cod", kind = "cash", amountMinor = o.totalMinor, status = "pending", custody = "courier",
    )))
    else -> Pay("cash", listOf(SalePayment(method = "cash", kind = "cash", amountMinor = o.totalMinor, status = "confirmed")))
  }

  /** مقدارِ قلمِ سفارش به واحدِ موجودی — واحدِ فروش (مثلاً کارتن) با ضریبِ کالا */
  fun stockQty(p: Product, unit: String, qtyMilli: Long): Double {
    val q = qtyMilli / 1000.0
    return if (unit.isNotBlank() && unit == p.saleUnit && unit != p.unit) SalesEngine.toStockQty(p, q) else q
  }

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
    val shortIds = ArrayList<String>()
    val cover = ArrayList<WarehouseEntry>()
    for ((pid, q) in need) {
      val available = ShopStore.stock(d, pid)
      if (q <= available + 1e-9) continue
      val msg = SalesEngine.shortageMessage(byId.getValue(pid), q, available)
      if (!allowShortage) return Result.Failed(tag + msg)
      short += msg
      shortIds += pid
      //  فقط برای گذشتن از سنجشِ صندوق در `record`؛ این ردیف هرگز در دفتر نمی‌نشیند
      cover += WarehouseEntry(id = "o-cover-$pid", productId = pid, units = q - available)
    }

    val fee = o.deliveryFeeMinor
    val feeLine = if (fee > 0) SalesEngine.CartLine(feeItemIdOf(o.id), 1.0, fee / 100.0, FEE_LABEL) else null
    val cart = plan.map { it.cart } + listOfNotNull(feeLine)
    //  CRM-07: سفارشِ اینترنتی در تاریخچهٔ همان مشتری — هر دو شماره (CRM-02، ‎+93/07… یکی)، فقط مشتریِ فعال
    //  (ادغام‌شده نه، CRM-10) و فقط وقتی یکی است؛ دو مشتریِ فعال با یک شماره ⇒ بی نام، نه حدس
    val debtorId = CustomerCrm.findByPhone(d, o.customer.phone.orEmpty()).singleOrNull()?.id

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
      shortIds,
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
    reverseBlock(d, o)?.let { return Result.Review(saleId, it) }
    val label = sale.invoiceLabel() ?: "0"
    //  همگام‌سازیِ نیمه (حرکت یا ممیزیِ لغو از دستگاهِ دیگر رسیده، وضعیتِ فاکتور هنوز نه) ⇒ همان‌ها
    val moved = d.stockMovements.mapTo(HashSet()) { it.id }
    val back = d.saleItems
      .filter { it.saleId == saleId && it.productId.isNotBlank() && it.quantity - it.returnedQty > 0 && "${it.id}-x" !in moved }
      .map {
        StockMovement(
          id = "${it.id}-x", productId = it.productId, type = "sale_cancel", qty = it.quantity - it.returnedQty,
          date = today, notes = "لغو فروش — فاکتور #$label — $REVERSE_REASON", refId = saleId, createdAt = now,
        )
      }
    val updated = sale.copy(status = "cancelled", cancelReason = REVERSE_REASON)
    val auditId = reverseAuditIdOf(o.id)
    val audit = if (d.auditLog.any { it.id == auditId }) emptyList() else listOf(
      AuditEntry(
        id = auditId, type = "cancel_sale", date = today, refId = saleId,
        notes = "لغو فروش فاکتور #$label — سفارشِ اینترنتی #${o.orderNo}: $REVERSE_REASON", createdAt = now,
      ),
    )
    return Result.Ok(
      d.copy(
        sales = d.sales.map { if (it.id == saleId) updated else it },
        stockMovements = d.stockMovements + back,
        auditLog = d.auditLog + audit,
      ),
      saleId,
    )
  }

  /* ===================== بازپرداختِ سفارشِ تحویل‌شده (RET-11، FIN-01) ===================== */

  const val REFUND_REASON = "بازپرداختِ سفارشِ اینترنتی"
  const val RESTOCK_REASON = "کالای سفارشِ بازپرداخت‌شده به انبار برگشت"

  /** رویدادهای بازپرداختِ ردیفِ `refunds` ِ سرور ⇒ رویدادِ هسته */
  fun eventsOf(o: OrderDto): List<OrderRefunds.Event> =
    o.refundEvents.map { OrderRefunds.Event(it.key, it.amountMinor, it.at, it.source) }

  /** کلیدهای بازپرداختی که سندشان (یا ردیفِ سابقه‌شان) در این دفتر هست — از هر دستگاه */
  fun refundedKeys(d: ShopData, orderId: String): Set<String> {
    val out = HashSet<String>()
    d.saleReturns.forEach { r -> OrderRefunds.keyOfDoc(orderId, r.docId)?.let(out::add) }
    val prefix = OrderRefunds.docPrefixOf(orderId)
    d.auditLog.forEach { a ->
      if (a.id.startsWith(prefix) && a.id.endsWith("-a")) OrderRefunds.keyOfDoc(orderId, a.id.removeSuffix("-a"))?.let(out::add)
    }
    return out
  }

  /** پولِ پس‌رفته از یک فروش (همهٔ برگشت‌ها، دستی و آنلاین) — به پول */
  private fun returnedMinor(d: ShopData, saleId: String): Long =
    d.saleReturns.filter { it.saleId == saleId }.sumOf { ReturnFlow.minor(it.amount) }

  /** این دستگاه سندِ برگشتِ بازپرداخت را می‌تواند بنویسد؟ پرچمِ سرور (`refundable`)، وگرنه نوشتنِ فروش */
  fun refundAllowed(o: OrderDto, perms: OrderPermsDto): Boolean = perms.recordSale && (o.refundable ?: true)

  /**
   *  بازپرداختِ سفارشِ تحویل‌شده ⇒ **سندِ برگشتِ همان فروش**، مثلِ برگشتِ صندوق (`ReturnFlow`):
   *  ردیفِ `SaleReturn` با `docId` ِ ثابتِ هر رویداد ([OrderRefunds.docIdOf]) و ردیفِ سابقه
   *  ([OrderRefunds.markIdOf]). فاکتور (قیمت، اقلام، وضعیت) دست نمی‌خورد و هیچ شمارندهٔ `$inc`
   *  (`returnedQty`، `debtSettled`) بالا نمی‌رود ⇒ دو دستگاه یا تلاشِ دوباره همان ردیف‌ها، یک بار.
   *  • مبلغ: همان مبلغِ رویداد به پول، با سقفِ ماندهٔ فروش ([OrderRefunds.plan]؛ مرجوعیِ دستیِ پیشین کم
   *    می‌شود)؛ رویدادِ بی‌مانده ⇒ فقط ردیفِ سابقه (تا سرور دیگر فهرستش نکند).
   *  • پول به کجا: [ReturnFlow.split] با «روشِ اصلی» — پرداختِ آنلاین/درگاه ⇒ `toMethods` (از دخل نه)؛
   *    نقد (سرِ پیشخوان یا هنگامِ تحویل) ⇒ نقدِ دخل، همان قاعدهٔ مرجوعیِ صندوق (`CashShift`).
   *  • روز و زمان = زمانِ سرورِ همان بازپرداخت ([dayOf]) ⇒ گزارش و صندوق در روزِ بازپرداخت، نه روزِ فروش.
   *  • کالا دست نمی‌خورد؛ «کالا به انبار برگشت؟» جداست ([restock]).
   *  بی فروشِ `o-…` ⇒ [Result.Failed] (پس از همگام‌سازی دوباره)؛ فروشِ لغوشده یا همه نشسته ⇒ [Result.Already].
   */
  fun refund(d: ShopData, o: OrderDto, by: String, dayOf: (Long) -> String): Result {
    val saleId = saleIdOf(o.id)
    val sale = d.sales.firstOrNull { it.id == saleId }
      ?: return Result.Failed("سفارش #${o.orderNo}: فروشِ تحویلِ این سفارش هنوز در این دستگاه نیست؛ پس از همگام‌سازی ثبت می‌شود")
    if (sale.status == "cancelled") return Result.Already(saleId)
    val steps = OrderRefunds.plan(ReturnFlow.minor(sale.finalTotal), returnedMinor(d, saleId), refundedKeys(d, o.id), eventsOf(o))
    if (steps.isEmpty()) return Result.Already(saleId)
    val label = sale.invoiceLabel() ?: "0"
    val known = d.auditLog.mapTo(HashSet()) { it.id }
    val rows = ArrayList<SaleReturn>()
    val audit = ArrayList<AuditEntry>()
    for (s in steps) {
      val docId = OrderRefunds.docIdOf(o.id, s.key)
      val day = dayOf(s.at)
      val amount = s.amountMinor / 100.0
      var where = ""
      if (s.amountMinor > 0) {
        val prior = d.saleReturns.filter { it.saleId == saleId } + rows
        val split = when (val r = ReturnFlow.split(sale, amount, ReturnFlow.ORIGINAL, by.ifBlank { "owner" }, null, prior)) {
          is ReturnFlow.Outcome.Bad -> return Result.Failed("سفارش #${o.orderNo}: ${r.message}")
          is ReturnFlow.Outcome.Ok -> r.value
        }
        //  فروشِ تحویل نسیه و اعتبار ندارد (`debtGiven = 0`، روشِ اصلی)؛ اگر شد، بی‌صدا نه
        if (split.account > 0 || split.credit > 0) return Result.Failed("سفارش #${o.orderNo}: بازپرداخت به حسابِ مشتری نمی‌رود؛ دستی بررسی کنید")
        where = buildList {
          if (split.cash > 0) add("نقد ${money(split.cash)}")
          split.methods.forEach { (m, a) -> add("${ReturnFlow.methodName(m)} ${money(a)}") }
        }.joinToString("، ")
        rows += SaleReturn(
          id = "$docId-0", saleId = saleId, quantity = 0.0, amount = amount, reason = REFUND_REASON,
          date = day, createdAt = s.at, docId = docId, refund = ReturnFlow.ORIGINAL,
          toMethods = split.methods.takeIf { it.isNotEmpty() }, by = by.trim().ifEmpty { null },
        )
      }
      val markId = OrderRefunds.markIdOf(o.id, s.key)
      if (markId in known) continue
      audit += AuditEntry(
        id = markId, type = "return", date = day, refId = saleId, createdAt = s.at, amount = amount,
        notes = if (s.amountMinor > 0) {
          "برگشت ${ReturnFlow.label(docId)} به مبلغ ${money(amount)} افغانی — فاکتور #$label — سفارشِ اینترنتی #${o.orderNo}: $REFUND_REASON" +
            if (where.isNotEmpty()) " — بازپرداخت: $where" else ""
        } else {
          "سفارشِ اینترنتی #${o.orderNo}: بازپرداخت رسید ولی از فاکتور #$label چیزی برای برگشت نمانده بود"
        },
      )
    }
    if (rows.isEmpty() && audit.isEmpty()) return Result.Already(saleId)
    return Result.Ok(d.copy(saleReturns = d.saleReturns + rows, auditLog = d.auditLog + audit), saleId)
  }

  /** مقدارِ یک قلمِ فروشِ تحویل که «کالا به انبار برگشت؟» برگردانده (ردیفِ [OrderRefunds.restockReturnIdOf]) */
  fun restockedQty(d: ShopData, saleItemId: String): Double =
    d.saleReturns.filter { it.saleItemId == saleItemId && it.id == "$saleItemId-rsr" }.sumOf { it.quantity }

  private fun lineNoOf(orderId: String, itemId: String): Int? =
    if (itemId.startsWith("o-$orderId-")) itemId.removePrefix("o-$orderId-").toIntOrNull() else null

  /** قلم‌های کالادارِ فروشِ تحویل از نگاهِ «کالا به انبار برگشت؟» (هزینهٔ ارسال نه) */
  fun refundLines(d: ShopData, orderId: String): List<OrderRefunds.Line> {
    val saleId = saleIdOf(orderId)
    val entries = d.warehouseEntries.mapTo(HashSet()) { it.id }
    return d.saleItems.filter { it.saleId == saleId }.mapNotNull { item ->
      val lineNo = lineNoOf(orderId, item.id) ?: return@mapNotNull null
      OrderRefunds.Line(
        lineNo = lineNo,
        productId = item.productId,
        remaining = item.quantity - item.returnedQty - restockedQty(d, item.id),
        restocked = OrderRefunds.restockIdOf(orderId, lineNo) in entries,
      )
    }.sortedBy { it.lineNo }
  }

  /** قلم‌هایی که «کالا به انبار برگشت؟» می‌پرسد: (شمارهٔ قلم، نام و مقدارِ مانده) */
  fun restockChoices(d: ShopData, orderId: String): List<Pair<Int, String>> {
    val items = d.saleItems.associateBy { it.id }
    val all = refundLines(d, orderId)
    return OrderRefunds.restockLines(all, all.mapTo(HashSet()) { it.lineNo }).map { l ->
      val name = items[itemIdOf(orderId, l.lineNo)]?.let { SalesEngine.itemName(d, it) } ?: "قلمِ ${l.lineNo}"
      l.lineNo to "$name — ${ir.vil3ntec.tohid.qty(l.remaining)}"
    }
  }

  /**
   *  رویدادهای بازپرداختِ نشسته (با مبلغ) که هنوز «کالا به انبار برگشت؟» نگرفته‌اند — و فقط اگر
   *  قلمی برای برگرداندن مانده. بازپرداختی که سرور یا دستگاهِ دیگر زد همین‌جا «برگشتِ کالا؟» می‌شود.
   */
  fun restockPending(d: ShopData, orderId: String): List<String> {
    val sale = d.sales.firstOrNull { it.id == saleIdOf(orderId) } ?: return emptyList()
    if (sale.status == "cancelled") return emptyList()
    val paid = d.saleReturns.filter { it.saleId == sale.id && it.amount > 0 }.mapNotNull { OrderRefunds.keyOfDoc(orderId, it.docId) }
    if (paid.isEmpty()) return emptyList()
    val decided = d.auditLog.mapTo(HashSet()) { it.id }
    val keys = OrderRefunds.pendingKeys(paid, paid.filterTo(HashSet()) { OrderRefunds.decisionIdOf(orderId, it) in decided })
    if (keys.isEmpty()) return emptyList()
    val open = refundLines(d, orderId).any { it.productId.isNotBlank() && !it.restocked && it.remaining > 1e-9 }
    return if (open) keys else emptyList()
  }

  /** فروش‌های تحویلی که «برگشتِ کالا؟» دارند — برای هشدارِ فهرستِ «سفارش‌ها» */
  fun restockPendingSales(d: ShopData): List<Sale> {
    val withRefund = d.saleReturns.filter { it.amount > 0 && it.docId?.contains("-rf-") == true }.mapTo(HashSet()) { it.saleId }
    if (withRefund.isEmpty()) return emptyList()
    return d.sales.filter { s ->
      val id = s.orderId
      s.id in withRefund && id != null && s.id == saleIdOf(id) && restockPending(d, id).isNotEmpty()
    }
  }

  /**
   *  «کالا به انبار برگشت؟» ِ رویدادهای [restockPending] ِ سفارشِ [orderId]: فقط قلم‌های [chosen]
   *  (پیش‌فرضِ سفارشِ تحویل‌شده «نه»، تهی = هیچ کالایی برنگشت) به موجودی برمی‌گردند — همان راهِ
   *  برگشتِ بی رسیدِ `ReturnFlow` (ورودِ انبارِ اصلاحی، نه `returnedQty` ِ `$inc`) با شناسه‌های ثابت:
   *  ورودِ انبار [OrderRefunds.restockIdOf]، حرکت [OrderRefunds.restockMoveIdOf] و ردیفِ برگشتِ
   *  بی‌مبلغ [OrderRefunds.restockReturnIdOf] (گزارش بهای کالای برگشته را همان روز پس می‌دهد).
   *  هر رویداد یک ردیفِ سابقهٔ تصمیم ([OrderRefunds.decisionIdOf]) می‌گیرد ⇒ دیگر نمی‌پرسد.
   */
  fun restock(d: ShopData, orderId: String, orderNo: Long, chosen: Set<Int>, by: String, today: String, now: Long): Result {
    val saleId = saleIdOf(orderId)
    val sale = d.sales.firstOrNull { it.id == saleId } ?: return Result.Failed("فروشِ سفارش #$orderNo در این دفتر نیست")
    val keys = restockPending(d, orderId)
    if (keys.isEmpty()) return Result.Already(saleId)
    val doc = OrderRefunds.decisionIdOf(orderId, keys.first())
    val label = sale.invoiceLabel() ?: "0"
    val items = d.saleItems.associateBy { it.id }
    val products = d.products.associateBy { it.id }
    val entries = ArrayList<WarehouseEntry>()
    val moves = ArrayList<StockMovement>()
    val rows = ArrayList<SaleReturn>()
    val names = ArrayList<String>()
    for (line in OrderRefunds.restockLines(refundLines(d, orderId), chosen)) {
      val item = items[itemIdOf(orderId, line.lineNo)] ?: continue
      val product = products[line.productId]
      val name = SalesEngine.itemName(d, item)
      val note = "برگشتِ کالا — فاکتور #$label — سفارشِ اینترنتی #$orderNo — $name"
      entries += WarehouseEntry(
        id = OrderRefunds.restockIdOf(orderId, line.lineNo), productId = line.productId, units = line.remaining,
        unit = product?.unit.orEmpty(), date = today, notes = note, isAdjustment = true, createdAt = now, refId = doc,
      )
      moves += StockMovement(
        id = OrderRefunds.restockMoveIdOf(orderId, line.lineNo), productId = line.productId, type = "customer_return",
        qty = line.remaining, date = today, notes = note, refId = OrderRefunds.restockReturnIdOf(orderId, line.lineNo), createdAt = now,
      )
      rows += SaleReturn(
        id = OrderRefunds.restockReturnIdOf(orderId, line.lineNo), saleId = saleId, saleItemId = item.id,
        productId = line.productId, quantity = line.remaining, amount = 0.0, reason = RESTOCK_REASON, date = today,
        createdAt = now, docId = doc, disposition = ReturnFlow.RESTOCK, by = by.trim().ifEmpty { null },
      )
      names += "$name ${ir.vil3ntec.tohid.qty(line.remaining)}"
    }
    val known = d.auditLog.mapTo(HashSet()) { it.id }
    val what = if (names.isEmpty()) "هیچ کالایی به انبار برنگشت" else "به انبار برگشت: ${names.joinToString("، ")}"
    val audit = keys.map { OrderRefunds.decisionIdOf(orderId, it) }.filter { it !in known }.map {
      AuditEntry(id = it, type = "return", date = today, refId = saleId, createdAt = now,
        notes = "برگشتِ کالای سفارشِ اینترنتی #$orderNo (فاکتور #$label): $what" + if (by.isNotBlank()) " — ثبت: ${by.trim()}" else "")
    }
    return Result.Ok(
      d.copy(
        warehouseEntries = d.warehouseEntries + entries,
        stockMovements = d.stockMovements + moves,
        saleReturns = d.saleReturns + rows,
        auditLog = d.auditLog + audit,
      ),
      saleId,
    )
  }
}
