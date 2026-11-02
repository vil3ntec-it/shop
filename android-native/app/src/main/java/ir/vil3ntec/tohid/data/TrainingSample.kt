package ir.vil3ntec.tohid.data

/**
 *  OPS-07 — دفترِ نمونهٔ حالتِ آموزشی: چند کالا (با موجودی)، چند مشتری و چند فروش.
 *
 *  ⛔ قطعی: همان [today] و [now] ⇒ همان دفتر، بایت‌به‌بایت (آزمونِ `TrainingModeTest`). هیچ تصادف، ساعت یا
 *  شناسهٔ تازه این‌جا ساخته نمی‌شود؛ ساعت و تاریخ را فراخوان می‌دهد تا فروش‌های نمونه در گزارشِ «امروز» دیده شوند.
 *  همهٔ شناسه‌ها با `trn-` شروع می‌شوند تا هرگز با ردیفِ واقعی یکی نشوند.
 */
object TrainingSample {

  const val PREFIX = "trn-"

  private data class Item(val name: String, val category: String, val unit: String, val buy: Double, val sell: Double, val stock: Double, val barcode: String)

  private val ITEMS = listOf(
    Item("برنج (نمونه)", "خوراکی", "کیلو", 70.0, 90.0, 40.0, "2000000000017"),
    Item("روغن ۱ لیتری (نمونه)", "خوراکی", "عدد", 110.0, 135.0, 24.0, "2000000000024"),
    Item("شکر (نمونه)", "خوراکی", "کیلو", 55.0, 70.0, 30.0, "2000000000031"),
    Item("چای ۵۰۰ گرمی (نمونه)", "خوراکی", "بسته", 160.0, 200.0, 18.0, "2000000000048"),
    Item("صابون (نمونه)", "بهداشتی", "عدد", 20.0, 30.0, 50.0, "2000000000055"),
    Item("شامپو (نمونه)", "بهداشتی", "عدد", 90.0, 120.0, 12.0, "2000000000062"),
    Item("آب معدنی (نمونه)", "نوشیدنی", "بطری", 12.0, 20.0, 60.0, "2000000000079"),
    Item("دفتر ۱۰۰ برگ (نمونه)", "نوشت‌افزار", "عدد", 35.0, 50.0, 25.0, "2000000000086"),
  )

  private val CUSTOMERS = listOf(
    "مشتریِ نمونه — احمد" to "0700000001",
    "مشتریِ نمونه — مریم" to "0700000002",
    "مشتریِ نمونه — کریم" to "0700000003",
  )

  /** (شمارهٔ کالا، تعداد) برای هر فروشِ نمونهٔ نقدی — نسیه را خودِ آموزش‌گیرنده روی مشتریِ نمونه می‌زند */
  private val SALES = listOf(
    listOf(0 to 2.0, 4 to 3.0),
    listOf(1 to 1.0, 3 to 1.0, 6 to 4.0),
    listOf(2 to 5.0, 7 to 2.0),
  )

  fun build(today: String, now: Long): ShopData {
    val products = ITEMS.mapIndexed { i, it ->
      Product(
        id = "${PREFIX}p${i + 1}", name = it.name, category = it.category, unit = it.unit,
        purchasePrice = it.buy, salePrice = it.sell, minStock = 5.0, barcodes = listOf(it.barcode), createdAt = now,
      )
    }
    val entries = ITEMS.mapIndexed { i, it ->
      WarehouseEntry(id = "${PREFIX}w${i + 1}", productId = products[i].id, units = it.stock, unit = it.unit, price = it.buy, date = today)
    }
    val debtors = CUSTOMERS.mapIndexed { i, (name, phone) ->
      Debtor(id = "${PREFIX}c${i + 1}", name = name, phone = phone, createdAt = now)
    }
    val sales = mutableListOf<Sale>()
    val items = mutableListOf<SaleItem>()
    SALES.forEachIndexed { s, lines ->
      val saleId = "${PREFIX}s${s + 1}"
      var total = 0.0
      lines.forEachIndexed { l, (p, qty) ->
        val it = ITEMS[p]
        val line = it.sell * qty
        total += line
        items += SaleItem(
          id = "${PREFIX}s${s + 1}i${l + 1}", saleId = saleId, productId = products[p].id, name = it.name,
          quantity = qty, unitPrice = it.sell, purchasePrice = it.buy, totalPrice = line,
        )
      }
      sales += Sale(
        id = saleId, invoiceNumber = s + 1, total = total, finalTotal = total,
        paymentMethod = "cash", paidAmount = total, remaining = 0.0,
        createdAt = now - (SALES.size - s) * 60_000L, date = today, seller = WATERMARK_SELLER,
      )
    }
    return ShopData(
      products = products, warehouseEntries = entries, debtors = debtors,
      sales = sales, saleItems = items, nextInvoiceNo = sales.size + 1,
    )
  }

  private const val WATERMARK_SELLER = "آموزشی"
}
