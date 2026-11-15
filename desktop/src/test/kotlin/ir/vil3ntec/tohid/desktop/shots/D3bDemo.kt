package ir.vil3ntec.tohid.desktop.shots

import ir.vil3ntec.tohid.data.DebtTransaction
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.Expense
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.Purchase
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SaleItem
import ir.vil3ntec.tohid.data.SalePayment
import ir.vil3ntec.tohid.data.SaleReturn
import ir.vil3ntec.tohid.data.ShopData
import java.time.LocalDate
import java.time.ZoneId

/**
 *  دفترِ نمایشیِ عکس‌ها و آزمون‌های صفحه‌های داده‌ایِ کامپیوتر (`claude/e-desk-d3b`) — نه دادهٔ دکانِ واقعی:
 *  همان قرض‌داران، تأمین‌کنندگان و مصارفِ [MoneyDemo] به‌علاوهٔ ۴۵ روز فروشِ واقعی‌نما (نقد، نسیهٔ مشتری با بردِ دفتر،
 *  کارت، لغوشده و برگشت) با تخمِ ثابت، تا عکسِ «پیش» و «پس» یک داده را نشان بدهند. فقط API ِ دفتر ⇒ روی شاخهٔ پایه هم.
 */
internal object D3bDemo {
  private val zone = ZoneId.systemDefault()
  private fun iso(daysAgo: Long) = LocalDate.now(zone).minusDays(daysAgo).toString()
  private fun midnight(daysAgo: Long) = LocalDate.now(zone).minusDays(daysAgo).atStartOfDay(zone).toInstant().toEpochMilli()

  private val goods = listOf(
    Product(id = "p-rice", name = "برنج سیلا ۵ کیلویی", unit = "بسته", purchasePrice = 950.0, salePrice = 1100.0),
    Product(id = "p-oil", name = "روغن آفتاب‌گردان", unit = "بوتل", purchasePrice = 310.0, salePrice = 360.0),
    Product(id = "p-tea", name = "چای سبز", unit = "کیلو", purchasePrice = 420.0, salePrice = 500.0),
    Product(id = "p-sugar", name = "بوره (شکر) ۱ کیلو", unit = "کیلو", purchasePrice = 70.0, salePrice = 85.0),
    Product(id = "p-flour", name = "آرد گندم ۷ کیلو", unit = "خریطه", purchasePrice = 380.0, salePrice = 430.0),
    Product(id = "p-soap", name = "صابون دست", unit = "دانه", purchasePrice = 35.0, salePrice = 50.0),
    Product(id = "p-milk", name = "شیر خشک", unit = "قطی", purchasePrice = 240.0, salePrice = 290.0),
    Product(id = "p-beans", name = "لوبیا سرخ", unit = "کیلو", purchasePrice = 110.0, salePrice = 140.0),
    Product(id = "p-salt", name = "نمک", unit = "بسته", purchasePrice = 15.0, salePrice = 25.0),
    Product(id = "p-cola", name = "نوشابه ۱٫۵ لیتری", unit = "بوتل", purchasePrice = 55.0, salePrice = 75.0),
  )

  /** دفترِ کامل: [MoneyDemo.full] + فروشِ ۴۵ روز */
  fun full(): ShopData {
    val base = MoneyDemo.full()
    val rnd = java.util.Random(65)
    val debtors = base.debtors
    val sales = ArrayList<Sale>()
    val items = ArrayList<SaleItem>()
    val tx = ArrayList<DebtTransaction>(base.transactions)
    val returns = ArrayList<SaleReturn>()
    var no = 1300
    val now = System.currentTimeMillis()
    for (day in 44L downTo 0L) {
      val start = midnight(day) + 8 * 3_600_000L
      val end = if (day == 0L) now - 60_000L else midnight(day) + 21 * 3_600_000L
      val count = if (day == 0L) 9 else 5 + rnd.nextInt(9)
      repeat(count) { k ->
        val at = start + ((end - start) * (k + 1.0) / (count + 1)).toLong()
        if (at <= start) return@repeat
        val id = "ds-${no}"
        val lines = 1 + rnd.nextInt(4)
        var total = 0.0
        repeat(lines) { j ->
          val p = goods[rnd.nextInt(goods.size)]
          val q = (1 + rnd.nextInt(3)).toDouble()
          val line = q * p.salePrice
          total += line
          items += SaleItem(id = "$id-$j", saleId = id, productId = p.id, quantity = q, unitPrice = p.salePrice, totalPrice = line)
        }
        val discount = if (rnd.nextInt(8) == 0) 25.0 else 0.0
        val final = total - discount
        val roll = rnd.nextInt(100)
        val sale = when {
          roll < 3 -> Sale(id, no, total = total, discount = discount, finalTotal = final, paidAmount = final, status = "cancelled", cancelReason = "اشتباهِ صندوق", createdAt = at, date = iso(day), seller = "صاحب")
          roll < 28 -> {
            val who = debtors[rnd.nextInt(debtors.size)]
            val paid = if (rnd.nextBoolean()) 0.0 else Math.round(final * 0.4).toDouble()
            val rest = final - paid
            tx += DebtTransaction(id = "dt-$id", debtorId = who.id, type = "give", amount = rest, date = iso(day), notes = "فاکتور #$no", createdAt = at, refId = id)
            Sale(id, no, total = total, discount = discount, finalTotal = final, paymentMethod = "credit", debtorId = who.id, paidAmount = paid, remaining = rest,
              debtGiven = rest, createdAt = at, date = iso(day), seller = "صاحب")
          }
          roll < 43 -> Sale(id, no, total = total, discount = discount, finalTotal = final, paidAmount = final, paymentMethod = "card", createdAt = at, date = iso(day), seller = "شاگرد",
            payments = listOf(SalePayment(method = "card", kind = "card", amountMinor = Math.round(final * 100), status = "confirmed")))
          else -> Sale(id, no, total = total, discount = discount, finalTotal = final, paidAmount = final, createdAt = at, date = iso(day), seller = if (k % 2 == 0) "صاحب" else "شاگرد")
        }
        sales += sale
        //  هر چند روز یک برگشتِ نقدِ یک دانه
        if (sale.status != "cancelled" && sale.paymentMethod == "cash" && rnd.nextInt(14) == 0) {
          val it = items.last()
          returns += SaleReturn("dr-$id", saleId = id, saleItemId = it.id, productId = it.productId, quantity = 1.0, amount = it.unitPrice, date = iso(day), createdAt = at + 600_000L)
        }
        no++
      }
    }
    //  رسیدهای پراکندهٔ قرض‌داران در همین ۴۵ روز
    debtors.forEachIndexed { i, d ->
      if (i % 3 == 1) tx += DebtTransaction(id = "dk-$i", debtorId = d.id, type = "receive", amount = 300.0 + i * 40, date = iso((i * 3 % 40).toLong()), notes = "نقد", createdAt = midnight((i * 3 % 40).toLong()) + 13 * 3_600_000L)
    }
    val month = LocalDate.now(zone).toString().take(7)
    val more = listOf(
      Expense(id = "e6", title = "انترنت", category = "برق", amount = 700.0, date = "$month-05", createdAt = now - 3_000_000L),
      Expense(id = "e7", title = "پاک‌کاری", category = "دیگر", amount = 250.0, date = iso(1), createdAt = now - 2_000_000L),
      Expense(id = "e8", title = "کرایهٔ دکان", category = "کرایه", amount = 6000.0, date = iso(33), createdAt = now - 33 * 86_400_000L),
      Expense(id = "e9", title = "معاشِ شاگرد", category = "معاش", amount = 9000.0, date = iso(31), createdAt = now - 31 * 86_400_000L),
    )
    val buys = listOf(
      Purchase(id = "u4", productId = "p-flour", supplierId = "s-herat", quantity = 30.0, unit = "خریطه", purchasePrice = 380.0, totalAmount = 11400.0, paidAmount = 6000.0, debt = 5400.0, date = iso(14), createdAt = now - 14 * 86_400_000L),
      Purchase(id = "u5", productId = "p-sugar", supplierId = "s-pamir", quantity = 50.0, unit = "کیلو", purchasePrice = 70.0, totalAmount = 3500.0, paidAmount = 3500.0, debt = 0.0, date = iso(2), createdAt = now - 2 * 86_400_000L),
      Purchase(id = "u6", productId = "p-cola", supplierId = "s-alborz", quantity = 48.0, unit = "بوتل", purchasePrice = 55.0, totalAmount = 2640.0, paidAmount = 0.0, debt = 2640.0, date = iso(1), createdAt = now - 86_400_000L),
    )
    return base.copy(
      products = goods,
      sales = base.sales + sales,
      saleItems = base.saleItems + items,
      saleReturns = returns,
      transactions = tx,
      expenses = base.expenses + more,
      expenseCategories = base.expenseCategories + "دیگر",
      purchases = base.purchases + buys,
    )
  }

  /** دفترِ بزرگ برای آزمونِ «تنبل»: [customers] مشتری و [lines] قلمِ فروش */
  fun big(customers: Int, lines: Int): ShopData {
    val now = System.currentTimeMillis()
    val debtors = (0 until customers).map { Debtor(id = "bd$it", name = "مشتری $it", createdAt = now - it) }
    val tx = (0 until customers).map { DebtTransaction(id = "bt$it", debtorId = "bd$it", type = "give", amount = 100.0 + it % 900, date = iso((it % 60).toLong()), createdAt = now - it * 1_000L) }
    val perSale = 5
    val sales = (0 until lines / perSale).map { i ->
      Sale("bs$i", 10_000 + i, total = 500.0, finalTotal = 500.0, paidAmount = 500.0, createdAt = now - i * 60_000L, date = iso((i / 300).toLong().coerceAtMost(200)))
    }
    val items = ArrayList<SaleItem>(lines)
    sales.forEach { s -> repeat(perSale) { j -> items += SaleItem(id = "${s.id}-$j", saleId = s.id, productId = goods[j].id, quantity = 1.0, unitPrice = 100.0, totalPrice = 100.0) } }
    return ShopData(products = goods, debtors = debtors, transactions = tx, sales = sales, saleItems = items)
  }
}
