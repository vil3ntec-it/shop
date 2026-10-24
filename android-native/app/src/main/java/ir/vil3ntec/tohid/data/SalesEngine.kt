package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.money
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/**
 *  ثبتِ فروش — همان حسابی که نسخهٔ وب می‌کند.
 *
 *  عمداً تابعِ خالص است: ورودی «دفترِ فعلی + سبد + شرایطِ تسویه»، خروجی
 *  «دفترِ تازه». نه به صفحه دست می‌زند نه به فایل، پس می‌شود بدونِ گوشی
 *  درستیِ حساب را سنجید — و حسابِ پولِ دکان همان چیزی است که بیش از همه
 *  باید سنجیده شود.
 */
object SalesEngine {

  /**
   *  یک ردیفِ سبد.
   *
   *  ── دو چیزی که تازه اضافه شده و چرا ──────────────────────────────
   *  **قیمتِ آزادِ ردیف.** تا دیروز قیمت همیشه از `product.salePrice`
   *  خوانده می‌شد و تنها اهرمِ چانه‌زنی، تخفیفِ کلِ فاکتور بود. چانه‌زنی
   *  کارِ روزمرهٔ دکان است؛ فروشنده مجبور می‌شد تخفیفِ کل را دستکاری کند
   *  تا عددِ یک قلم در بیاید — و بعد گزارشِ سودِ همان کالا غلط از آب
   *  درمی‌آمد، چون سود از `unitPrice`ِ ثبت‌شده حساب می‌شود. حالا قیمتِ
   *  همان ردیف عوض می‌شود و سود درست ثبت می‌ماند.
   *
   *  **قلمِ آزاد.** هر چیزی که به سبد می‌رفت باید از قبل یک `Product`
   *  می‌بود. برای فروشِ یک کیسه، یک جنسِ تک، یا خدمتی که کالا نیست، راهی
   *  نبود جز ساختنِ کالای واقعی در فهرست — و نتیجه‌اش فهرستِ شلوغی از
   *  «متفرقه ۱، متفرقه ۲» بود. قلمِ آزاد نام و مبلغِ خودش را دارد، روی
   *  موجودی اثر نمی‌گذارد و در فهرستِ کالاها هم نمی‌نشیند.
   *  ────────────────────────────────────────────────────────────────
   *
   *  @param productId شناسهٔ کالا. برای قلمِ آزاد، شناسهٔ یکتای **همین
   *    ردیف** است تا کلیدِ فهرست و حذف و جمع‌زدن مثلِ بقیه کار کند.
   *  @param unitPrice قیمتِ دستیِ همین ردیف؛ `null` یعنی قیمتِ خودِ کالا.
   *  @param label نامِ قلمِ آزاد. ناخالی بودنش یعنی این ردیف کالا ندارد.
   */
  data class CartLine(
    val productId: String,
    val quantity: Double,
    val unitPrice: Double? = null,
    val label: String = "",
  ) {
    /** ردیفی که کالای ثبت‌شده‌ای پشتش نیست */
    val free: Boolean get() = label.isNotBlank()
  }

  /** قیمتِ واحدِ یک ردیف — دستی اگر گذاشته شده، وگرنه قیمتِ کالا */
  /**
   *  مقدار بر حسبِ واحدِ فروش ⇒ مقدار بر حسبِ واحدِ موجودی.
   *  ۲۵۰ گرم از کالای کیلویی با ضریبِ ۰٫۰۰۱ ⇒ ۰٫۲۵ کیلوگرم. نتیجه تا سه رقمِ
   *  اعشار گرد می‌شود تا خطای ممیزِ شناور (۰٫۲۵۰۰۰۰۰۰۰۱) در موجودی ننشیند.
   *  واحدِ فروش نداشت ⇒ همان عدد.
   */
  fun toStockQty(product: Product, quantityInSaleUnit: Double): Double {
    if (product.saleUnit.isBlank() || product.unitFactor <= 0 || product.unitFactor.isNaN()) return quantityInSaleUnit
    return Math.round(quantityInSaleUnit * product.unitFactor * 1000.0) / 1000.0
  }

  fun linePrice(d: ShopData, line: CartLine): Double {
    line.unitPrice?.let { return it }
    if (line.free) return 0.0
    return d.products.find { it.id == line.productId }?.salePrice ?: 0.0
  }

  enum class DiscountType { AMOUNT, PERCENT }
  enum class Payment { CASH, CREDIT }

  data class Checkout(
    /** نامِ فروشنده روی فاکتور — صفحه از حسابِ واردشده می‌گذارد */
    val seller: String = "",
    val discountType: DiscountType = DiscountType.AMOUNT,
    val discountValue: Double = 0.0,
    val payment: Payment = Payment.CASH,
    /** فقط وقتی نسیه است معنی دارد */
    val paidAmount: Double = 0.0,
    /**
     *  مشتریِ این فاکتور.
     *
     *  در فروشِ **نسیه** اجباری است و بدهی به حسابش نوشته می‌شود. در
     *  فروشِ **نقدی** اختیاری است و فقط اسمِ مشتری روی فاکتور می‌نشیند —
     *  هیچ بدهی‌ای ساخته نمی‌شود.
     *
     *  تا دیروز فروشِ نقدی به هیچ‌کس وصل نمی‌شد، پس «این مشتری امسال چقدر
     *  خرید کرده» فقط برای کسانی جواب داشت که نسیه برده بودند. حسابِ
     *  بدهی از `transactions` می‌آید نه از این فیلد، پس وصل کردنِ مشتری
     *  به فاکتورِ نقدی هیچ عددی را جابه‌جا نمی‌کند.
     */
    val debtorId: String? = null,
    /** سیاستِ دکان: کالای منقضی فروخته نشود (`ShopPolicy.blockExpired`) */
    val blockExpired: Boolean = false,
    /** برگهٔ تأییدِ تخفیفِ بیش از سقف (STF-02) — همان‌طور روی فاکتور می‌نشیند */
    val approval: kotlinx.serialization.json.JsonObject? = null,
  )

  data class Totals(
    val subtotal: Double,
    val discountValue: Double,
    val discount: Double,
    val finalTotal: Double,
  )

  sealed interface Result {
    data class Ok(val data: ShopData, val invoiceNumber: Int, val saleId: String) : Result
    data class Failed(val message: String) : Result
  }

  /* --------------------------- سبد خرید --------------------------- */

  /**
   * واحدهایی که اعشار می‌گیرند. «۲٫۵ کیلو» معنی دارد، «۲٫۵ عدد» ندارد.
   *
   * پیش‌تر فقط «کیلو» بود و فهرستِ پیش‌فرض «کیلوگرم» دارد؛ یعنی کالای
   * کیلویی ۰٫۲۵ نمی‌پذیرفت و ۲۵۰ گرم فروختنی نبود. نسخهٔ وب هم‌زمان عوض شد.
   */
  const val ALREADY_POSTED = "این فاکتور یک بار ثبت شده است و دوباره ثبت نمی‌شود"

  /** `DebtTransaction.kind` ِ «برد»ی که فروشِ نسیه می‌سازد (کنارِ `sale_cancel`/`customer_return`) */
  const val SALE_CREDIT = "sale_credit"

  private val FRACTIONAL_UNITS = setOf("کیلو", "کیلوگرم", "گرم", "لیتر", "میلی‌لیتر", "متر")

  fun isFractionalUnit(unit: String): Boolean = unit in FRACTIONAL_UNITS

  /**
   *  دقتِ اعشارِ هر واحد: «۱٫۵ عدد» پذیرفته نمی‌شود، «۱٫۵ کیلو» می‌شود.
   *  کالایی که واحدِ فروشِ دوم با ضریب دارد استثناست — «۱ بسته» از کالای
   *  کارتنی با ضریبِ ۰٫۱ یعنی ۰٫۱ کارتن، و درست است. `null` یعنی درست.
   */
  fun quantityProblem(p: Product, quantity: Double): String? {
    if (isFractionalUnit(p.unit) || p.saleUnit.isNotBlank()) return null
    if (kotlin.math.abs(quantity - Math.rint(quantity)) < 1e-9) return null
    return "«${p.name}» به «${p.unit.ifBlank { "عدد" }}» شمرده می‌شود و اعشار نمی‌گیرد"
  }

  /** پلهٔ دکمه‌های کم/زیاد — گرم و میلی‌لیتر ۱۰تایی، کیلو و لیتر و متر ۰٫۱، بقیه ۱ */
  fun cartStep(unit: String): Double =
    if (unit == "گرم" || unit == "میلی‌لیتر") 10.0 else if (isFractionalUnit(unit)) 0.1 else 1.0

  /**
   * افزودن به سبد. اگر کالا از قبل در سبد باشد فقط عددش بالا می‌رود —
   * ردیفِ تازه ساخته نمی‌شود، همان‌طور که در نسخهٔ وب.
   */
  fun addToCart(cart: List<CartLine>, productId: String, quantity: Double = 1.0): List<CartLine> {
    if (cart.any { it.productId == productId }) {
      return cart.map { if (it.productId == productId) it.copy(quantity = it.quantity + quantity) else it }
    }
    return cart + CartLine(productId, quantity)
  }

  /** نتیجهٔ افزودن به سبد: سبدِ تازه، و اینکه از موجودی جلو افتاد یا نه */
  data class Added(val cart: List<CartLine>, val capped: Boolean, val available: Double)

  /**
   *  افزودن به سبد، با نگهبانِ موجودی.
   *
   *  تا حالا موجودی فقط لحظهٔ **ثبتِ فروش** سنجیده می‌شد. یعنی فروشنده
   *  می‌توانست از کالایی که شش تا مانده، هفت تا در سبد بگذارد و کارتِ
   *  کالا هم بگوید «۷ عدد در سبد» زیرِ «۶ عدد مانده» — و خطا تازه سرِ
   *  ثبت درمی‌آمد، وقتی مشتری جلوی پیشخوان ایستاده.
   *
   *  حالا همان‌جا جلویش گرفته می‌شود و سبد روی موجودی می‌ایستد.
   */
  fun addToCartCapped(
    cart: List<CartLine>,
    productId: String,
    quantity: Double,
    available: Double,
  ): Added {
    val inCart = cart.find { it.productId == productId }?.quantity ?: 0.0
    val room = (available - inCart).coerceAtLeast(0.0)
    if (room <= 0.0) return Added(cart, capped = true, available = available)
    val take = minOf(quantity, room)
    return Added(
      cart = addToCart(cart, productId, take),
      capped = take < quantity,
      available = available,
    )
  }

  /**
   *  افزودنِ یک قلمِ آزاد — چیزی که کالای ثبت‌شده‌ای ندارد.
   *
   *  هر بار ردیفِ تازه‌ای ساخته می‌شود و با ردیف‌های دیگر جمع نمی‌شود:
   *  دو «کیسه»ی جدا ممکن است دو قیمتِ جدا داشته باشند.
   */
  fun addFreeLine(
    cart: List<CartLine>,
    label: String,
    unitPrice: Double,
    quantity: Double,
    newId: () -> String,
  ): List<CartLine> {
    val name = label.trim()
    if (name.isEmpty()) return cart
    val price = if (unitPrice.isNaN() || unitPrice < 0) 0.0 else unitPrice
    val count = if (quantity.isNaN() || quantity <= 0) 1.0 else quantity
    return cart + CartLine(
      productId = newId(),
      quantity = count,
      unitPrice = price,
      label = name,
    )
  }

  /** قیمتِ دستیِ یک ردیف. `null` یعنی برگشت به قیمتِ خودِ کالا. */
  fun setLinePrice(cart: List<CartLine>, productId: String, price: Double?): List<CartLine> {
    val clean = price?.takeIf { !it.isNaN() && it >= 0 }
    return cart.map { if (it.productId == productId) it.copy(unitPrice = clean) else it }
  }

  /** تعیینِ تعداد. صفر یا کمتر یعنی حذفِ ردیف. */
  fun setCartQty(cart: List<CartLine>, productId: String, quantity: Double): List<CartLine> {
    if (quantity <= 0) return cart.filter { it.productId != productId }
    // اعشارِ شناور را مهار می‌کند: ۰٫۱+۰٫۲ نباید ۰٫۳۰۰۰۰۰۰۰۴ شود
    val clean = Math.round(quantity * 1000) / 1000.0
    return cart.map { if (it.productId == productId) it.copy(quantity = clean) else it }
  }

  /**
   * پیامِ کمبودِ موجودی — همان جمله‌های نسخهٔ وب، چون فروشنده به همین
   * جمله‌ها عادت کرده و باید بداند دقیقاً چقدر کم دارد.
   */
  fun shortageMessage(product: Product, wanted: Double, available: Double): String {
    val u = if (product.unit.isNotBlank()) " ${product.unit}" else ""
    if (available <= 0) {
      return "«${product.name}» در برنامه موجودی ندارد. اگر جنس در دکان هست، اول ورودی انبار را ثبت کنید."
    }
    return "«${product.name}»: ${money(available)}$u موجود است، شما ${money(wanted)}$u خواستید."
  }

  /**
   *  مبلغِ یک ردیف — همان لحظه به افغانیِ کامل گرد می‌شود (۱٫۵ × ۱۱۵ = ۱۷۳).
   *  بی این، بدهیِ نیم‌افغانی‌ای می‌نشست که هیچ پرداختی صافش نمی‌کرد، چون پول
   *  با عددِ گردشده نشان داده و پرداخت می‌شود. همان `lineTotal`ِ نسخهٔ وب.
   */
  fun lineTotal(price: Double, quantity: Double): Double = (price * quantity).roundToLong().toDouble()

  fun cartTotal(d: ShopData, cart: List<CartLine>): Double =
    cart.sumOf { line ->
      //  ردیفِ آزاد کالا ندارد ولی مبلغ دارد؛ ردیفِ کالایی که کالایش حذف
      //  شده، مثلِ قبل صفر حساب می‌شود
      if (line.free) lineTotal(line.unitPrice ?: 0.0, line.quantity)
      else {
        val p = d.products.find { it.id == line.productId }
        if (p == null) 0.0 else lineTotal(line.unitPrice ?: p.salePrice, line.quantity)
      }
    }

  /**
   * جمع، تخفیف و مبلغِ نهایی.
   * گِرد کردن دقیقاً همان‌جایی است که نسخهٔ وب گِرد می‌کند — نه زودتر، نه
   * دیرتر — وگرنه عدد با فاکتورِ کاغذی یکی درنمی‌آید.
   */
  fun totals(d: ShopData, cart: List<CartLine>, checkout: Checkout): Totals {
    val subtotal = cartTotal(d, cart)
    var value = checkout.discountValue.takeIf { it > 0 && !it.isNaN() } ?: 0.0

    val discount: Double
    if (checkout.discountType == DiscountType.PERCENT) {
      value = min(value, 100.0)
      discount = (subtotal * (value / 100.0)).roundToLong().toDouble()
    } else {
      value = min(value, subtotal)
      discount = value.roundToLong().toDouble()
    }

    return Totals(
      subtotal = subtotal,
      discountValue = value,
      discount = discount,
      finalTotal = max(0.0, subtotal - discount),
    )
  }

  /**
   * ثبتِ فروش. اگر چیزی درست نباشد، دفتر دست‌نخورده می‌ماند و دلیلش
   * برگردانده می‌شود — نصفه ثبت نمی‌شود.
   *
   * @param series «سری سند»ِ این دستگاه (F0-03، `DocSeries.current`)؛ خالی
   *               یعنی هنوز نرسیده و فاکتور مثلِ پیش بی سری ثبت می‌شود.
   */
  fun record(
    d: ShopData,
    cart: List<CartLine>,
    checkout: Checkout,
    today: String,
    now: Long,
    newId: () -> String,
    series: String = "",
    /**
     *  کلیدِ یکتای فاکتور (POS-14) — همان `SaleDraft.key`. فروشِ تازه همین
     *  شناسه را می‌گیرد و اگر پیش‌تر ثبت شده باشد **دوباره ثبت نمی‌شود**.
     *  `null` ⇒ مثلِ همیشه شناسهٔ تازه.
     */
    saleId: String? = null,
  ): Result {
    if (cart.isEmpty()) return Result.Failed("سبد خرید خالی است")
    if (saleId != null && d.sales.any { it.id == saleId }) return Result.Failed(ALREADY_POSTED)

    // موجودی درست پیش از ثبت سنجیده می‌شود، نه بعد از آن
    for (line in cart) {
      val p = d.products.find { it.id == line.productId } ?: continue
      if (!p.active) return Result.Failed("«${p.name}» غیرفعال است و فروخته نمی‌شود")
      quantityProblem(p, line.quantity)?.let { return Result.Failed(it) }
      var available = ShopStore.stock(d, p.id)
      //  سیاستِ دکان: کالای منقضی فروخته نشود ⇒ ماندهٔ محموله‌های منقضی از
      //  موجودیِ فروختنی کم می‌شود (FEFO؛ شرح سرِ `ExpiryEngine`)
      if (checkout.blockExpired) {
        val expired = ExpiryEngine.expiredRemaining(d, p.id, today)
        if (expired > 0 && line.quantity > available - expired) {
          return Result.Failed(
            "از «${p.name}» ${ir.vil3ntec.tohid.qty(expired)} واحد منقضی است؛ " +
              "فقط ${ir.vil3ntec.tohid.qty(max(0.0, available - expired))} واحدِ سالم مانده"
          )
        }
      }
      if (line.quantity > available) return Result.Failed(shortageMessage(p, line.quantity, available))
    }

    val t = totals(d, cart, checkout)

    // فروشِ نقدی همیشه کامل تسویه می‌شود
    var paid = if (checkout.payment == Payment.CASH) t.finalTotal else checkout.paidAmount
    if (paid.isNaN() || paid < 0) return Result.Failed("مبلغ پرداختی درست نیست")
    paid = min(paid, t.finalTotal)
    val remaining = max(0.0, t.finalTotal - paid)

    /*
     *  مشتری. در نسیه اجباری است و بدهی می‌سازد؛ در نقدی اختیاری است و
     *  فقط نامِ مشتری روی فاکتور می‌نشیند. `debtGiven` پایین‌تر تعیین
     *  می‌کند که بدهی ساخته شود یا نه — نه خودِ این فیلد.
     */
    val debtorId: String? = checkout.debtorId?.takeIf { it.isNotBlank() }
    if (debtorId != null && d.debtors.none { it.id == debtorId }) {
      return Result.Failed("قرض‌دار پیدا نشد")
    }
    if (checkout.payment == Payment.CREDIT && remaining > 0 && debtorId == null) {
      return Result.Failed("برای فروش نسیه، قرض‌دار را انتخاب کنید")
    }
    //  بدهی فقط از فروشِ نسیه‌ای می‌آید که هنوز تسویه نشده
    val onCredit = checkout.payment == Payment.CREDIT && remaining > 0

    val saleId = saleId?.takeIf { it.isNotBlank() } ?: newId()
    val invoiceNumber = d.nextInvoiceNo

    val sale = Sale(
      id = saleId,
      invoiceNumber = invoiceNumber,
      series = series.trim().ifEmpty { null },
      total = t.subtotal,
      discountType = if (checkout.discountType == DiscountType.PERCENT) "percent" else "amount",
      discountValue = t.discountValue,
      discount = t.discount,
      finalTotal = t.finalTotal,
      paymentMethod = if (checkout.payment == Payment.CASH) "cash" else "credit",
      debtorId = debtorId,
      paidAmount = paid,
      remaining = remaining,
      status = "completed",
      debtGiven = if (onCredit) remaining else 0.0,
      debtSettled = 0.0,
      createdAt = now,
      date = today,
      syncStatus = "pending",
      seller = checkout.seller.trim(),
      approval = checkout.approval,
    )

    val items = mutableListOf<SaleItem>()
    val movements = mutableListOf<StockMovement>()
    cart.forEach { line ->
      /*
       *  قلمِ آزاد: نه کالایی دارد، نه حرکتِ انبار می‌سازد، نه بهای
       *  تمام‌شده. سودش برابرِ کلِ مبلغش حساب می‌شود، چون خریدش جای دیگری
       *  ثبت شده (یا اصلاً کالا نبوده).
       */
      if (line.free) {
        val price = line.unitPrice ?: 0.0
        items += SaleItem(
          id = newId(),
          saleId = saleId,
          productId = "",
          name = line.label,
          quantity = line.quantity,
          unitPrice = price,
          purchasePrice = 0.0,
          totalPrice = lineTotal(price, line.quantity),
          returnedQty = 0.0,
        )
        return@forEach
      }

      val p = d.products.find { it.id == line.productId } ?: return@forEach
      //  قیمتِ دستیِ همین ردیف، وگرنه قیمتِ خودِ کالا
      val unit = line.unitPrice ?: p.salePrice
      items += SaleItem(
        id = newId(),
        saleId = saleId,
        productId = p.id,
        quantity = line.quantity,
        unitPrice = unit,
        purchasePrice = p.purchasePrice,
        totalPrice = lineTotal(unit, line.quantity),
        returnedQty = 0.0,
      )
      movements += StockMovement(
        id = newId(),
        productId = p.id,
        type = "sale",
        qty = -line.quantity,
        date = today,
        notes = "فاکتور #${sale.invoiceLabel()}",
        refId = saleId,
        createdAt = now,
      )
    }

    //  فروشِ نسیه به حسابِ قرض‌دار می‌رود، با همان منطقِ قرض‌داران.
    //  مشتریِ فاکتورِ نقدی اینجا نمی‌آید: نامش روی فاکتور هست ولی بدهی
    //  ندارد.
    val transactions = d.transactions.toMutableList()
    //  `debtorId != null` بالاتر تضمین شده؛ اینجا فقط برای کامپایلر است
    if (onCredit && debtorId != null) {
      transactions += DebtTransaction(
        id = newId(),
        debtorId = debtorId,
        type = "give",
        amount = remaining,
        date = today,
        notes = "فروش نسیه — فاکتور #${sale.invoiceLabel()}",
        createdAt = now,
        //  ساختهٔ سیستم: اگر سرور این فاکتور را (سقفِ تخفیف) رد کند، این «برد» هم با آن برمی‌گردد
        refId = saleId,
        kind = SALE_CREDIT,
      )
    }

    val audit = d.auditLog + AuditEntry(
      id = newId(),
      type = "sale",
      date = today,
      refId = saleId,
      notes = "ثبت فروش فاکتور #${sale.invoiceLabel()} به مبلغ ${money(t.finalTotal)} افغانی",
      createdAt = now,
    )

    return Result.Ok(
      d.copy(
        sales = d.sales + sale,
        saleItems = d.saleItems + items,
        stockMovements = d.stockMovements + movements,
        transactions = transactions,
        auditLog = audit,
        nextInvoiceNo = invoiceNumber + 1,
      ),
      invoiceNumber = invoiceNumber,
      saleId = saleId,
    )
  }

  /* ========================= لغو فروش و مرجوعی ========================= */

  /**
   * لغوِ یک فروش.
   *
   * فاکتور پاک نمی‌شود، فقط «لغوشده» می‌شود — سابقه‌ای که افتاده نباید
   * از دفتر برود. موجودی خودبه‌خود برمی‌گردد، چون فروشِ لغوشده در
   * حسابِ «فروخته‌شده» شمرده نمی‌شود.
   *
   * اگر نسیه بوده، فقط همان مقداری از بدهی خنثی می‌شود که هنوز تسویه
   * نشده — نه کلِ مبلغِ اولیه. وگرنه فروشی که پیش‌تر مرجوعی خورده، با
   * لغو شدن بدهیِ قرض‌دار را منفی می‌کرد.
   */
  fun cancel(
    d: ShopData,
    saleId: String,
    today: String,
    now: Long,
    newId: () -> String,
    /** رابط بی دلیل اجازه نمی‌دهد؛ این‌جا پیش‌فرضِ خالی فقط برای صدازننده‌های قدیمی است */
    reason: String = "",
  ): Result {
    val sale = d.sales.find { it.id == saleId } ?: return Result.Failed("فاکتور پیدا نشد")
    if (sale.status == "cancelled") return Result.Failed("این فاکتور قبلاً لغو شده است")
    //  ORD-20: فاکتورِ سفارشِ اینترنتی را سرور ساخته؛ لغو/«اصلاح در سبد» یعنی دوباره زدن
    if (sale.isOnlineOrder()) return Result.Failed(ONLINE_ORDER_LOCKED)
    val why = reason.trim()

    var updated = sale.copy(status = "cancelled", cancelReason = why)
    val transactions = d.transactions.toMutableList()

    if (sale.debtorId != null) {
      val outstanding = sale.debtGiven - sale.debtSettled
      if (outstanding > 0) {
        transactions += DebtTransaction(
          id = newId(),
          debtorId = sale.debtorId,
          type = "receive",
          amount = outstanding,
          date = today,
          notes = "لغو فروش — فاکتور #${sale.invoiceLabel() ?: "0"}",
          createdAt = now,
        )
        updated = updated.copy(debtSettled = sale.debtSettled + outstanding)
      }
    }

    //  جنسِ برگشته در دفترِ گردشِ انبار هم دیده شود — پیش‌تر موجودی برمی‌گشت
    //  (فروشِ لغوشده شمرده نمی‌شود) ولی گردش هیچ ردیفی نداشت و با موجودی نمی‌خواند
    val back = d.saleItems
      .filter { it.saleId == saleId && it.productId.isNotBlank() && it.quantity - it.returnedQty > 0 }
      .map {
        StockMovement(
          id = newId(), productId = it.productId, type = "sale_cancel",
          qty = it.quantity - it.returnedQty, date = today,
          notes = "لغو فروش — فاکتور #${sale.invoiceLabel() ?: "0"}", refId = saleId, createdAt = now,
        )
      }

    return Result.Ok(
      d.copy(
        sales = d.sales.map { if (it.id == saleId) updated else it },
        transactions = transactions,
        stockMovements = d.stockMovements + back,
        auditLog = d.auditLog + AuditEntry(
          id = newId(),
          type = "cancel_sale",
          date = today,
          refId = saleId,
          notes = "لغو فروش فاکتور #${sale.invoiceLabel() ?: "0"}" + if (why.isNotEmpty()) " — دلیل: $why" else "",
          createdAt = now,
        ),
      ),
      invoiceNumber = sale.invoiceNumber ?: 0,
      saleId = saleId,
    )
  }

  /** جملهٔ هشدارِ پیش از لغو */
  /** پیامِ ردِ لغوِ فاکتورِ سفارشِ اینترنتی */
  const val ONLINE_ORDER_LOCKED =
    "این فاکتورِ سفارشِ اینترنتی است و از صندوق لغو نمی‌شود؛ برگشتِ کاملش از بخشِ «سفارش‌ها» (بازپرداخت) ثبت می‌شود"

  fun cancelWarning(sale: Sale): String =
    "فروش #${sale.invoiceLabel() ?: "0"} لغو می‌شود و موجودی کالاهای آن به انبار برمی‌گردد. سابقه فروش حذف نمی‌شود."

  /**
   *  نامی که روی یک قلمِ فاکتور نوشته می‌شود.
   *
   *  قلمِ معمولی نامش را از خودِ کالا می‌گیرد — پس تغییرِ نامِ کالا در
   *  فاکتورهای قدیمی هم دیده می‌شود، همان‌طور که تا امروز بوده. قلمِ آزاد
   *  کالایی ندارد و نامش روی خودِ قلم نوشته شده.
   */
  fun itemName(d: ShopData, item: SaleItem): String {
    if (item.productId.isBlank()) return item.name.ifBlank { "قلم آزاد" }
    return d.products.find { it.id == item.productId }?.name
      ?: item.name.ifBlank { "(محصول حذف‌شده)" }
  }

  /** مقداری از یک قلم که هنوز می‌شود برگرداند */
  fun returnable(item: SaleItem): Double = (item.quantity - item.returnedQty).coerceAtLeast(0.0)

  /**
   * مرجوعیِ جزئی — چند قلم از یک فاکتور، هرکدام به هر مقدار.
   *
   * مبلغِ هر قلم از قیمتِ واقعیِ همان قلم در همان فاکتور حساب می‌شود، نه
   * از قیمتِ امروز: جنسی که ماه پیش ارزان‌تر فروخته شده، گران‌تر پس
   * گرفته نمی‌شود.
   */
  fun recordReturn(
    d: ShopData,
    saleId: String,
    quantities: Map<String, Double>,
    reason: String,
    today: String,
    now: Long,
    newId: () -> String,
  ): Result {
    val sale = d.sales.find { it.id == saleId } ?: return Result.Failed("فاکتور پیدا نشد")
    //  فاکتورِ لغوشده کلاً برگشته است؛ مرجوعیِ دوباره موجودی را دو بار برمی‌گرداند
    if (sale.status == "cancelled") return Result.Failed("این فاکتور لغو شده است")
    //  سهمِ تخفیفِ فاکتور: مشتری مبلغِ بعد از تخفیف را داده، پس همان پس داده می‌شود
    val paidRatio =
      if (sale.total > 0 && sale.discount > 0) ((sale.total - sale.discount) / sale.total).coerceIn(0.0, 1.0) else 1.0

    val items = d.saleItems.toMutableList()
    val returns = d.saleReturns.toMutableList()
    val movements = d.stockMovements.toMutableList()
    var totalAmount = 0.0
    var any = false

    d.saleItems.forEachIndexed { index, item ->
      if (item.saleId != saleId) return@forEachIndexed
      var quantity = quantities[item.id] ?: return@forEachIndexed
      if (quantity.isNaN() || quantity <= 0) return@forEachIndexed

      val allowed = returnable(item)
      if (quantity > allowed) quantity = allowed
      if (quantity <= 0) return@forEachIndexed

      // قیمتِ همان قلم در همان فاکتور، نه قیمتِ امروز
      val amount = if (item.quantity > 0) {
        (item.totalPrice / item.quantity * quantity * paidRatio).roundToLong().toDouble()
      } else 0.0

      items[index] = item.copy(returnedQty = item.returnedQty + quantity)

      val returnId = newId()
      returns += SaleReturn(
        id = returnId,
        saleId = saleId,
        saleItemId = item.id,
        productId = item.productId,
        quantity = quantity,
        amount = amount,
        reason = reason,
        date = today,
        createdAt = now,
      )
      //  قلمِ آزاد کالایی ندارد، پس حرکتِ انبار هم نمی‌سازد — وگرنه ردیفی
      //  با شناسهٔ کالای خالی در گردشِ موجودی می‌نشست
      if (item.productId.isNotBlank()) movements += StockMovement(
        id = newId(),
        productId = item.productId,
        type = "customer_return",
        qty = quantity,
        date = today,
        notes = reason.ifBlank { "مرجوعی فاکتور #${sale.invoiceLabel() ?: "0"}" },
        refId = returnId,
        createdAt = now,
      )
      totalAmount += amount
      any = true
    }

    if (!any) return Result.Failed("مقدار معتبری برای مرجوعی وارد نشده")

    var updatedSale = sale
    val transactions = d.transactions.toMutableList()

    // در فروشِ نسیه، معادلِ مرجوعی از حسابِ قرض‌دار کم می‌شود — حداکثر تا
    // سقفِ بدهیِ باقی‌ماندهٔ همین فروش، تا با لغوِ بعدی منفی نشود
    if (sale.debtorId != null && totalAmount > 0) {
      val outstanding = sale.debtGiven - sale.debtSettled
      val settle = minOf(totalAmount, max(0.0, outstanding))
      if (settle > 0) {
        transactions += DebtTransaction(
          id = newId(),
          debtorId = sale.debtorId,
          type = "receive",
          amount = settle,
          date = today,
          notes = "مرجوعی کالا — فاکتور #${sale.invoiceLabel() ?: "0"}",
          createdAt = now,
        )
        updatedSale = updatedSale.copy(debtSettled = sale.debtSettled + settle)
      }
    }

    return Result.Ok(
      d.copy(
        sales = d.sales.map { if (it.id == saleId) updatedSale else it },
        saleItems = items,
        saleReturns = returns,
        stockMovements = movements,
        transactions = transactions,
        auditLog = d.auditLog + AuditEntry(
          id = newId(),
          type = "return",
          date = today,
          refId = saleId,
          notes = "مرجوعی به مبلغ ${money(totalAmount)} افغانی — فاکتور #${sale.invoiceLabel() ?: "0"}",
          createdAt = now,
        ),
      ),
      invoiceNumber = sale.invoiceNumber ?: 0,
      saleId = saleId,
    )
  }
}
