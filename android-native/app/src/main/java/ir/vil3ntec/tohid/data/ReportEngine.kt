package ir.vil3ntec.tohid.data

import kotlin.math.max

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
   * تکه‌ی ظریفش مرجوعی است. `net` مبلغِ اولیهٔ فاکتورهاست (مرجوعی از آن
   * کم نشده) ولی `cogs` مرجوعی را کم کرده، پس اثرِ مرجوعی باید یک بار —
   * و فقط یک بار — اعمال شود. سه حالت دارد:
   *
   *   ۱) فروشِ لغوشده: کلِ فروش قبلاً برگشته، پس مرجوعی‌اش نباید دوباره
   *      کم شود، وگرنه سود دوبار جریمه می‌شود.
   *   ۲) فروشِ فعالِ داخلِ بازه: هم درآمدش در net است هم هزینه‌اش از cogs
   *      کم شده، پس کلِ مبلغِ مرجوعی کم می‌شود.
   *   ۳) فروشِ فعالِ خارجِ بازه: نه درآمدش در net است نه هزینه‌اش در cogs،
   *      پس فقط سودِ از‌دست‌رفته (مبلغ منهای بهای تمام‌شده) کم می‌شود.
   */
  fun sales(d: ShopData, from: String, to: String): SalesReport {
    //  جدولِ فروش یک بار ساخته و بارها خوانده می‌شود؛ شرحش سرِ `SalesIndex`.
    //  پیش از این، این تابع برای هر فاکتور کلِ اقلامِ فروش را می‌گشت.
    val index = ShopStore.salesIndex(d)

    val active = d.sales.filter { it.date in from..to && it.status != "cancelled" }
    val activeIds = active.mapTo(HashSet()) { it.id }

    val gross = active.sumOf { it.total }
    val discount = active.sumOf { it.discount }
    val net = active.sumOf { it.finalTotal }

    var cogs = 0.0
    active.forEach { sale ->
      index.itemsBySale[sale.id]?.forEach { item ->
        cogs += (item.quantity - item.returnedQty) * item.purchasePrice
      }
    }

    var returnAmount = 0.0
    var returnProfitImpact = 0.0
    d.saleReturns.filter { it.date in from..to }.forEach { r ->
      val sale = index.saleById[r.saleId]
      if (sale != null && sale.status == "cancelled") return@forEach   // حالت ۱
      returnAmount += r.amount
      if (r.saleId in activeIds) {
        returnProfitImpact += r.amount                                 // حالت ۲
      } else {
        val item = index.itemById[r.saleItemId]
        val cost = if (item != null) item.purchasePrice * r.quantity else 0.0
        returnProfitImpact += max(0.0, r.amount - cost)                // حالت ۳
      }
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

  enum class Range { TODAY, WEEK, MONTH, ALL }

  /** بازهٔ `from..to` برای هر انتخاب — همان بازه‌هایی که نسخهٔ وب داشت */
  fun rangeOf(choice: Range, today: String): Pair<String, String> = when (choice) {
    Range.TODAY -> today to today
    Range.WEEK -> shiftDays(today, -6) to today
    // «این ماه» یعنی از اولِ همان ماهِ میلادیِ فایل، مثل نسخهٔ وب
    Range.MONTH -> today.take(7) + "-01" to today
    Range.ALL -> "0000-01-01" to "9999-12-31"
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
