package ir.vil3ntec.tohid.desktop.shots

import ir.vil3ntec.tohid.data.DebtTransaction
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.Expense
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.Purchase
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SaleItem
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.Supplier
import ir.vil3ntec.tohid.data.SupplierPayment
import ir.vil3ntec.tohid.todayIso

/**
 *  دفترِ نمایشیِ عکس‌های گروهِ پول (نه دادهٔ دکانِ واقعی): قرض‌دارِ بدهکار با بردِ عقب‌افتاده و
 *  فاکتوردار، قرض‌دارِ صاف، قرض‌دارِ بیش از سقف، نام‌های بلند، سه تأمین‌کننده با خرید و پرداخت،
 *  و مصارفِ ماه در چند دسته. تاریخ‌ها نسبت به امروز ساخته می‌شوند تا «عقب‌افتاده» همیشه درست باشد.
 */
internal object MoneyDemo {
  const val OWING = "d-jalil"
  const val SETTLED = "d-haroon"
  const val OVER = "d-nasir"
  const val SUPPLIER = "s-alborz"

  private fun ago(days: Int): String {
    val c = java.util.Calendar.getInstance()
    c.add(java.util.Calendar.DAY_OF_MONTH, -days)
    return "%04d-%02d-%02d".format(c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.DAY_OF_MONTH))
  }
  private fun ahead(days: Int) = ago(-days)
  private fun at(days: Int) = System.currentTimeMillis() - days * 86_400_000L

  private val products = listOf(
    Product(id = "p-rice", name = "برنج سیلا ۵ کیلویی", unit = "بسته", purchasePrice = 950.0, salePrice = 1100.0),
    Product(id = "p-oil", name = "روغن آفتاب‌گردان", unit = "بوتل", purchasePrice = 310.0, salePrice = 360.0),
    Product(id = "p-tea", name = "چای سبز", unit = "کیلو", purchasePrice = 420.0, salePrice = 500.0),
  )

  private val names = listOf(
    "جلیل احمدی", "هارون", "نصیر کریمی", "محمدعلی عبدالرحیم‌زادهٔ کابلی", "فریده", "سمیع‌الله", "زرغونه", "احمدشاه", "مریم", "عبدالقدیر",
    "شکیلا", "رحیم‌الله", "گل‌احمد", "نادیه", "بشیر",
  )

  /** دفترِ کامل — ۱۵ قرض‌دار، فاکتورِ نسیه با اقلام، رسیدها، تأمین‌کننده‌ها و مصارف */
  fun full(): ShopData {
    val debtors = names.mapIndexed { i, n ->
      Debtor(
        id = when (i) { 0 -> OWING; 1 -> SETTLED; 2 -> OVER; else -> "d$i" },
        name = n,
        phone = if (i % 3 == 0) "07${(91112230 + i)}" else "",
        notes = if (i == 0) "مغازهٔ سرِ کوچه — هر جمعه حساب می‌کند" else "",
        creditLimit = when (i) { 0 -> 5000.0; 2 -> 1000.0; else -> null },
        createdAt = at(90 - i),
      )
    }
    val sale = Sale(
      id = "sale-12", invoiceNumber = 12, total = 3260.0, finalTotal = 3160.0, discount = 100.0, paymentMethod = "credit",
      debtorId = OWING, paidAmount = 500.0, remaining = 2660.0, date = ago(40), createdAt = at(40),
    )
    val items = listOf(
      SaleItem(id = "i1", saleId = sale.id, productId = "p-rice", quantity = 2.0, unitPrice = 1100.0, totalPrice = 2200.0),
      SaleItem(id = "i2", saleId = sale.id, productId = "p-oil", quantity = 2.0, unitPrice = 360.0, totalPrice = 720.0),
      SaleItem(id = "i3", saleId = sale.id, productId = "p-tea", quantity = 0.5, unitPrice = 500.0, totalPrice = 250.0),
    )
    val tx = mutableListOf(
      DebtTransaction(id = "t1", debtorId = OWING, type = "give", amount = 2660.0, date = ago(40), notes = "فاکتور #12", createdAt = at(40), dueDate = ago(10)),
      DebtTransaction(id = "t2", debtorId = OWING, type = "receive", amount = 800.0, date = ago(25), notes = "نقد", createdAt = at(25)),
      DebtTransaction(id = "t3", debtorId = OWING, type = "give", amount = 1200.0, date = ago(6), notes = "آرد و شکر", createdAt = at(6), dueDate = ahead(8)),
      DebtTransaction(id = "t4", debtorId = OWING, type = "receive", amount = 400.0, date = ago(2), createdAt = at(2)),
      DebtTransaction(id = "t5", debtorId = SETTLED, type = "give", amount = 600.0, date = ago(30), createdAt = at(30)),
      DebtTransaction(id = "t6", debtorId = SETTLED, type = "receive", amount = 600.0, date = ago(12), createdAt = at(12)),
      DebtTransaction(id = "t7", debtorId = OVER, type = "give", amount = 1450.0, date = ago(70), createdAt = at(70)),
    )
    debtors.drop(3).forEachIndexed { i, d ->
      tx += DebtTransaction(id = "g$i", debtorId = d.id, type = "give", amount = 150.0 * (i + 2), date = ago(5 + i * 9), createdAt = at(5 + i * 9))
      if (i % 2 == 0) tx += DebtTransaction(id = "r$i", debtorId = d.id, type = "receive", amount = 100.0, date = ago(2 + i), createdAt = at(2 + i))
    }
    val suppliers = listOf(
      Supplier(id = SUPPLIER, name = "شرکت پخش البرز", phone = "0791112233", address = "کابل، مندوی، سرای شهزاده", createdAt = at(60)),
      Supplier(id = "s-pamir", name = "عمده‌فروشی پامیر", phone = "0700223344", createdAt = at(50)),
      Supplier(id = "s-herat", name = "بازرگانی هرات", createdAt = at(40)),
    )
    val purchases = listOf(
      Purchase(id = "u1", productId = "p-rice", supplierId = SUPPLIER, quantity = 20.0, unit = "بسته", purchasePrice = 950.0, totalAmount = 19000.0, paidAmount = 10000.0, debt = 9000.0, date = ago(20), createdAt = at(20)),
      Purchase(id = "u2", productId = "p-oil", supplierId = SUPPLIER, quantity = 12.0, unit = "بوتل", purchasePrice = 310.0, totalAmount = 3720.0, paidAmount = 3720.0, debt = 0.0, date = ago(9), createdAt = at(9)),
      Purchase(id = "u3", productId = "p-tea", supplierId = "s-pamir", quantity = 5.0, unit = "کیلو", purchasePrice = 420.0, totalAmount = 2100.0, paidAmount = 0.0, debt = 2100.0, date = ago(4), createdAt = at(4)),
    )
    val payments = listOf(
      SupplierPayment(id = "y1", supplierId = SUPPLIER, amount = 4000.0, date = ago(5), notes = "حواله", createdAt = at(5)),
      SupplierPayment(id = "y2", supplierId = "s-herat", amount = 500.0, date = ago(3), createdAt = at(3)),
    )
    val month = todayIso().take(7)
    val expenses = listOf(
      Expense(id = "e1", title = "کرایهٔ دکان", category = "کرایه", amount = 6000.0, date = "$month-01", createdAt = at(3)),
      Expense(id = "e2", title = "برق", category = "برق", amount = 850.0, date = "$month-02", createdAt = at(2)),
      Expense(id = "e3", title = "معاشِ شاگرد", category = "معاش", amount = 9000.0, date = "$month-03", createdAt = at(1)),
      Expense(id = "e4", title = "ترانسپورتِ بار از مندوی", category = "ترانسپورت", amount = 600.0, date = "$month-04", createdAt = at(0)),
      Expense(id = "e5", title = "", category = "برق", amount = 400.0, date = ago(40), createdAt = at(40)),
    )
    return ShopData(
      debtors = debtors, transactions = tx, products = products, sales = listOf(sale), saleItems = items,
      suppliers = suppliers, purchases = purchases, supplierPayments = payments,
      expenses = expenses, expenseCategories = listOf("کرایه", "برق", "معاش", "ترانسپورت"),
    )
  }

  /** ۶۰ قرض‌دار (فهرستِ بلند، نام‌های بلند) */
  fun many(): ShopData {
    val base = full()
    val extra = (0 until 45).map { i -> Debtor(id = "m$i", name = names[i % names.size] + " " + (i + 1), createdAt = at(i)) }
    val tx = extra.mapIndexed { i, d -> DebtTransaction(id = "mt$i", debtorId = d.id, type = "give", amount = 75.0 * (i + 1), date = ago(i % 50), createdAt = at(i % 50)) }
    return base.copy(debtors = base.debtors + extra, transactions = base.transactions + tx)
  }
}
