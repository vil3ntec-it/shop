package ir.vil3ntec.tohid.desktop.perf

import ir.vil3ntec.tohid.data.Purchase
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SaleItem
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.Supplier
import ir.vil3ntec.tohid.data.SupplierPayment

/**
 *  PERF-SCREEN — «هر صفحه زیرِ ۱۰۰ میلی‌ثانیه» (`docs/PERFORMANCE-fa.md`): دادهٔ بزرگ و فهرستِ بخش‌ها.
 *
 *  ── داده ──────────────────────────────────────────────────────────────
 *  دکانِ [PosSpeed.shop] (همان کالاهای دکانیِ POS-42 روی دفترِ قطعیِ [BigLedger]: ۲۰ هزار کالا، ۵ هزار مشتری با ۶ ردیفِ
 *  قرض/پرداخت، فاکتورها با ۱ تا ۵ قلم، دو حرکتِ انبار برای هر فاکتور) به‌علاوهٔ تأمین‌کننده، خرید و پرداختِ تأمین‌کننده.
 *  ⛔ هیچ دادهٔ واقعی‌ای نیست؛ seed ثابت.
 */
internal object ScreenSpeed {

  data class Size(val products: Int, val sales: Int, val suppliers: Int = 200, val purchases: Int = 5_000, val today: Int = 200)

  /** سنجهٔ دستی: ۲۰ هزار کالا، ۱۰۰ هزار فاکتور (~۳۰۰ هزار قلم، ۲۰۰ هزار حرکت)، ۵ هزار مشتری (۳۰ هزار ردیفِ قرض) */
  val FULL = Size(products = 20_000, sales = 100_000)

  /** آزمونِ همیشگیِ CI (heap ِ پیش‌فرضِ ۵۱۲ مگ ِ Gradle): همان ۲۰ هزار کالا و ۵ هزار مشتری، ۱۰ هزار فاکتور */
  val CI = Size(products = 20_000, sales = 10_000)

  fun shop(size: Size, seed: Int = 1405): ShopData {
    val base = PosSpeed.shop(size.products, size.sales, seed).d
    val r = BigLedger.Rng(seed + 7)
    val start = BigLedger.NOW - 3 * 365 * 86_400_000L
    val suppliers = List(size.suppliers) { i ->
      Supplier(id = BigLedger.ulid(start, 30, i.toLong()), name = "تأمین‌کنندهٔ ${i + 1}", phone = "07" + (20_000_000 + i), createdAt = start + i)
    }
    val purchases = List(size.purchases) { i ->
      val t = start + (3 * 365 * 86_400_000L) * i / size.purchases
      val p = base.products[r.int(base.products.size)]
      val q = (10 + r.int(90)).toDouble()
      val total = q * p.purchasePrice
      Purchase(
        id = BigLedger.ulid(t, 31, i.toLong()), productId = p.id, supplierId = suppliers[r.int(suppliers.size)].id, quantity = q,
        unit = p.unit, purchasePrice = p.purchasePrice, totalAmount = total, date = BigLedger.dateOf(t),
        paidAmount = if (i % 4 == 0) total / 2 else total, debt = if (i % 4 == 0) total / 2 else 0.0, createdAt = t,
      )
    }
    val pays = List(size.purchases / 5) { i ->
      val t = start + (3 * 365 * 86_400_000L) * i / (size.purchases / 5)
      SupplierPayment(id = BigLedger.ulid(t, 32, i.toLong()), supplierId = suppliers[r.int(suppliers.size)].id, amount = (100 + r.int(5_000)).toDouble(), date = BigLedger.dateOf(t), createdAt = t)
    }
    //  `BigLedger` پرداختِ مشتری را «take» می‌نویسد؛ برنامه «receive» می‌نویسد (رسیدها، جمعِ گرفته‌شده) — همان مبلغ و مانده
    val tx = base.transactions.map { if (it.type == "take") it.copy(type = "receive") else it }
    return withToday(base.copy(transactions = tx, suppliers = suppliers, purchases = purchases, supplierPayments = pays), size.today, r)
  }

  /** [n] فاکتورِ «امروز» (ساعتِ همین ماشین) تا خانه، صندوق و تاریخچه کارِ امروز هم داشته باشند — بقیهٔ دفتر قطعی است */
  private fun withToday(d: ShopData, n: Int, r: BigLedger.Rng): ShopData {
    val now = System.currentTimeMillis()
    val today = ir.vil3ntec.tohid.todayIso()
    val sales = ArrayList<Sale>(n)
    val items = ArrayList<SaleItem>(n * 3)
    for (k in 0 until n) {
      val t = now - (n - k) * 60_000L
      val id = BigLedger.ulid(t, 40, k.toLong())
      var total = 0.0
      repeat(1 + r.int(3)) { j ->
        val p = d.products[r.int(d.products.size)]
        total += p.salePrice
        items += SaleItem(id = BigLedger.ulid(t, 41, k * 10L + j), saleId = id, productId = p.id, name = p.name, quantity = 1.0, unitPrice = p.salePrice, purchasePrice = p.purchasePrice, totalPrice = p.salePrice)
      }
      val credit = k % 10 == 0
      sales += Sale(
        id = id, invoiceNumber = d.nextInvoiceNo + k, total = total, finalTotal = total, paymentMethod = if (credit) "credit" else "cash",
        debtorId = if (credit) d.debtors[k % d.debtors.size].id else null, paidAmount = if (credit) 0.0 else total, remaining = if (credit) total else 0.0,
        status = "completed", createdAt = t, date = today, seller = "صاحب",
      )
    }
    return d.copy(sales = d.sales + sales, saleItems = d.saleItems + items, nextInvoiceNo = d.nextInvoiceNo + n)
  }

  /** بخش‌های اصلیِ گوشیِ نسخهٔ ۳ (نامِ گزارش ⇒ مسیر)؛ پروندهٔ قرض‌دار با پُرردیف‌ترین مشتری */
  fun routes(d: ShopData): List<Pair<String, String>> {
    val busiest = d.transactions.groupingBy { it.debtorId }.eachCount().maxByOrNull { it.value }?.key ?: d.debtors.first().id
    return listOf(
      "home" to "home",
      "all sections (more)" to "more",
      "sale" to "sale",
      "quick sale" to "sale/quick",
      "products" to "products",
      "stock (گدام)" to "stock",
      "debtors" to "debtors",
      "debtor file" to "debtors/$busiest",
      "sales history" to "sales",
      "receipts" to "receipts",
      "reports" to "reports",
      "expenses" to "expenses",
      "purchasing" to "purchasing",
      "settings" to "settings",
      "search" to "search",
    )
  }
}
