package ir.vil3ntec.tohid.data


/**
 *  گزارش‌ها.
 *
 *  همان فرمول‌های نسخهٔ وب، مو به مو. جایی که می‌شد ساده‌ترش نوشت هم عوض
 *  نشده: عددِ سودِ ماه گذشته نباید با آمدنِ این نسخه فرق کند، وگرنه معلوم
 *  نیست کدام‌یک درست بوده.
 */
object ReportEngine {

  data class SalesReport(
    /** تعدادِ فاکتورهای فعال در بازه */
    val count: Int,
    /** جمعِ اقلام، پیش از تخفیف */
    val gross: Double,
    val discount: Double,
    /** مبلغِ نهاییِ فاکتورها */
    val net: Double,
    /** بهای تمام‌شدهٔ کالای فروخته‌شده */
    val cogs: Double,
    val grossProfit: Double,
    val expenses: Double,
    val netProfit: Double,
    /** مبلغِ مرجوعیِ مؤثر در این بازه — فقط برای نمایش */
    val returnAmount: Double,
  )

  /**
   * سودِ یک بازه.
   *
   * هر مرجوعی در بازهٔ تاریخِ **خودش** اثر می‌کند: مبلغی که پس داده شد منهای
   * بهای کالایی که به انبار برگشت. `cogs` کلِ مقدارِ فروخته‌شده است.
   *
   * پیش‌تر `cogs` مقدارِ مرجوعی را از بازهٔ فروش کم می‌کرد؛ کالایی که ماهِ
   * بعد کامل برمی‌گشت، در ماهِ فروش به‌اندازهٔ کلِ مبلغ سود نشان می‌داد و
   * جمعِ ماه‌ها با «همه» یکی درنمی‌آمد. فروشِ لغوشده کلاً بیرون است و
   * مرجوعی‌اش شمرده نمی‌شود.
   */
  fun sales(d: ShopData, from: String, to: String): SalesReport {
    //  جدولِ فروش یک بار ساخته و بارها خوانده می‌شود؛ شرحش سرِ `SalesIndex`.
    //  پیش از این، این تابع برای هر فاکتور کلِ اقلامِ فروش را می‌گشت.
    val index = ShopStore.salesIndex(d)

    val active = d.sales.filter { it.date in from..to && it.status != "cancelled" }

    val gross = active.sumOf { it.total }
    val discount = active.sumOf { it.discount }
    val net = active.sumOf { it.finalTotal }

    var cogs = 0.0
    active.forEach { sale ->
      index.itemsBySale[sale.id]?.forEach { item ->
        cogs += item.quantity * item.purchasePrice
      }
    }

    var returnAmount = 0.0
    var returnProfitImpact = 0.0
    d.saleReturns.filter { it.date in from..to }.forEach { r ->
      val sale = index.saleById[r.saleId]
      if (sale != null && sale.status == "cancelled") return@forEach
      returnAmount += r.amount
      val item = index.itemById[r.saleItemId]
      val cost = if (item != null) item.purchasePrice * r.quantity else 0.0
      returnProfitImpact += r.amount - cost
    }

    val grossProfit = net - cogs - returnProfitImpact
    val expenses = d.expenses.filter { it.date in from..to }.sumOf { it.amount }

    return SalesReport(
      count = active.size,
      gross = gross,
      discount = discount,
      net = net,
      cogs = cogs,
      grossProfit = grossProfit,
      expenses = expenses,
      netProfit = grossProfit - expenses,
      returnAmount = returnAmount,
    )
  }

  /* ----------------------------- خرید ----------------------------- */

  data class PurchaseReport(
    val count: Int,
    val total: Double,
    val paid: Double,
    val debt: Double,
    /** نامِ تأمین‌کننده ⇒ جمعِ خرید در بازه، بیشترین اول */
    val bySupplier: List<Pair<String, Double>>,
    val rows: List<Purchase>,
  )

  /** خریدهای یک بازه — جمع، پرداخت‌شده، بدهیِ به‌جامانده و سهمِ هر تأمین‌کننده */
  fun purchases(d: ShopData, from: String, to: String): PurchaseReport {
    val rows = d.purchases.filter { it.date in from..to }.sortedByDescending { it.date }
    val names = d.suppliers.associate { it.id to it.name }
    val bySupplier = rows.groupBy { names[it.supplierId] ?: "بی تأمین‌کننده" }
      .mapValues { (_, list) -> list.sumOf { it.totalAmount } }
      .toList().sortedByDescending { it.second }
    return PurchaseReport(
      count = rows.size,
      total = rows.sumOf { it.totalAmount },
      paid = rows.sumOf { it.paidAmount },
      debt = rows.sumOf { it.debt },
      bySupplier = bySupplier,
      rows = rows,
    )
  }

  /* ---------------------------- محصولات ---------------------------- */

  data class ProductStat(val product: Product, val quantity: Double, val profit: Double)

  /**
   * فروش و سودِ یک کالا، از آغاز تا حالا.
   *
   * سود از قیمتِ ثبت‌شده روی هر قلمِ فروش حساب می‌شود، نه از قیمتِ امروزِ
   * کالا — وگرنه با هر تغییرِ قیمت، سودِ گذشته هم عوض می‌شد.
   */
  fun productStat(d: ShopData, productId: String): Pair<Double, Double> {
    val sold = ShopStore.salesIndex(d).product(productId)
    return sold.quantity to sold.profit
  }

  /**
   *  آمارِ همهٔ کالاها.
   *
   *  پیش از این برای هر کالا کلِ اقلامِ فروش پیمایش می‌شد و برای هر قلم
   *  هم کلِ فاکتورها — ضربِ سه فهرست. حالا جدول یک بار ساخته می‌شود و
   *  هر کالا یک خواندن است.
   */
  fun productStats(d: ShopData): List<ProductStat> {
    val index = ShopStore.salesIndex(d)
    return d.products.map { p ->
      val sold = index.product(p.id)
      ProductStat(p, sold.quantity, sold.profit)
    }
  }

  data class ProductsReport(
    val low: List<Product>,
    val out: List<Product>,
    val inventoryValue: Double,
    val topSelling: List<ProductStat>,
    val slowest: List<ProductStat>,
    val mostProfitable: List<ProductStat>,
    val leastProfitable: List<ProductStat>,
  )

  fun products(d: ShopData): ProductsReport {
    val stats = productStats(d)
    val stock = ShopStore.index(d)
    return ProductsReport(
      low = d.products.filter { stock.status(it) == "low" },
      out = d.products.filter { stock.status(it) == "out" },
      // ارزشِ موجودیِ فعلی به بهای خرید — نه به قیمتِ فروش
      inventoryValue = d.products.sumOf { stock.stock(it.id) * it.purchasePrice },
      topSelling = stats.sortedByDescending { it.quantity }.take(5),
      // «کم‌فروش‌ها» فقط بین آن‌هایی که اصلاً فروش داشته‌اند معنی دارد
      slowest = stats.filter { it.quantity > 0 }.sortedBy { it.quantity }.take(5),
      mostProfitable = stats.sortedByDescending { it.profit }.take(5),
      leastProfitable = stats.sortedBy { it.profit }.take(5),
    )
  }

  /* ---------------------------- قرض‌داران ---------------------------- */

  data class DebtorStat(
    val debtor: Debtor,
    /** جمعِ فروشِ نسیه و قرضِ داده‌شده */
    val given: Double,
    /** جمعِ پولِ گرفته‌شده */
    val received: Double,
    val remaining: Double,
  )

  fun debtors(d: ShopData): List<DebtorStat> {
    //  یک گذر روی تراکنش‌ها، نه یک پیمایشِ کامل به ازای هر قرض‌دار
    val byDebtor = d.transactions.groupBy { it.debtorId }
    return d.debtors.map { debtor ->
      val mine = byDebtor[debtor.id].orEmpty()
      var given = 0.0
      var received = 0.0
      mine.forEach { if (it.type == "give") given += it.amount else received += it.amount }
      DebtorStat(
        debtor = debtor,
        given = given,
        received = received,
        remaining = given - received,
      )
    }
  }

  /* ------------------------- گردشِ موجودی ------------------------- */

  /** حرکاتِ انبار، تازه‌ترین اول. مثل نسخهٔ وب حداکثر ۲۰۰ ردیف. */
  fun stockLedger(d: ShopData, productId: String? = null, limit: Int = 200): List<StockMovement> =
    d.stockMovements
      .filter { productId == null || it.productId == productId }
      .sortedByDescending { it.createdAt }
      .take(limit)

  /* --------------------------- بازه‌های آماده --------------------------- */

  /** `JMONTH`/`JYEAR` — ماه یا سالِ خورشیدیِ انتخاب‌شده (`jy`/`jm`) */
  enum class Range { TODAY, WEEK, MONTH, ALL, JMONTH, JYEAR }

  /** بازهٔ `from..to` برای هر انتخاب — همان بازه‌هایی که نسخهٔ وب داشت */
  fun rangeOf(choice: Range, today: String, jy: Int = 0, jm: Int = 1): Pair<String, String> = when (choice) {
    Range.JMONTH -> jalaliMonth(jy, jm)
    Range.JYEAR -> jalaliMonth(jy, 1).first to jalaliMonth(jy, 12).second
    Range.TODAY -> today to today
    Range.WEEK -> shiftDays(today, -6) to today
    // «این ماه» یعنی از اولِ همان ماهِ میلادیِ فایل، مثل نسخهٔ وب
    Range.MONTH -> today.take(7) + "-01" to today
    Range.ALL -> "0000-01-01" to "9999-12-31"
  }

  /**
   *  یک ماهِ خورشیدی به بازهٔ میلادیِ فایل. اسفند ۲۹ یا ۳۰ روز است —
   *  همان `Jalali.isValid` تصمیم می‌گیرد، نه عددِ ثابت.
   */
  fun jalaliMonth(jy: Int, jm: Int): Pair<String, String> {
    val last = (31 downTo 29).first { ir.vil3ntec.tohid.Jalali.isValid(jy, jm, it) }
    val from = ir.vil3ntec.tohid.Jalali.toIso(jy, jm, 1) ?: "0000-01-01"
    val to = ir.vil3ntec.tohid.Jalali.toIso(jy, jm, last) ?: "9999-12-31"
    return from to to
  }

  private fun shiftDays(iso: String, days: Int): String {
    val parts = iso.split('-').mapNotNull { it.toIntOrNull() }
    if (parts.size != 3) return iso
    val c = java.util.GregorianCalendar(parts[0], parts[1] - 1, parts[2])
    c.add(java.util.Calendar.DAY_OF_MONTH, days)
    val y = c.get(java.util.Calendar.YEAR)
    val m = (c.get(java.util.Calendar.MONTH) + 1).toString().padStart(2, '0')
    val day = c.get(java.util.Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
    return "$y-$m-$day"
  }
}
