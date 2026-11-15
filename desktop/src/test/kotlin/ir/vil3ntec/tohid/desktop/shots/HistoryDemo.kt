package ir.vil3ntec.tohid.desktop.shots

import ir.vil3ntec.tohid.data.DebtTransaction
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SaleItem
import ir.vil3ntec.tohid.data.SalePayment
import ir.vil3ntec.tohid.data.SaleReturn
import ir.vil3ntec.tohid.data.ShopData
import java.time.LocalDate
import java.time.ZoneId

/**
 *  دفترِ نمایشیِ «تاریخچه فروش» ِ ۶۵h (نه دادهٔ دکانِ واقعی) — همان دو حالتِ عکسِ صاحب‌کار:
 *   • [empty]: امروز هیچ فروشی نیست، فقط یک رسیدِ قرضِ ۵۰۰ ؋ (همان عکسِ گوشیِ صاحب‌کار)؛
 *   • [busy]: امروز فروشِ نقد، نسیه (یکی با نامِ بلند) و کارت، یک برگشت و یک قرضِ وصول‌شده، و دو فروشِ دیروز.
 *  ساعت‌ها میانِ نیمه‌شبِ امروز و همین لحظه پخش می‌شوند تا همیشه «امروز» و پیش از اکنون باشند. فقط API ِ دفتر ⇒ همین پرونده
 *  روی شاخهٔ پایه هم کامپایل می‌شود (عکسِ «پیش»).
 */
internal object HistoryDemo {
  const val CASH = "h65-cash"
  const val CREDIT = "h65-credit"
  const val CARD = "h65-card"
  const val RETURNED = "h65-returned"
  const val LONG = "h65-long"
  const val DEBTOR = "h65-jalil"
  const val LONG_NAME = "محمدعلی عبدالرحیم‌زادهٔ کابلیِ سرای شهزاده"

  private val zone = ZoneId.systemDefault()

  private fun midnight(daysAgo: Long = 0): Long = LocalDate.now(zone).minusDays(daysAgo).atStartOfDay(zone).toInstant().toEpochMilli()

  /** لحظه‌ای امروز: [f] از ۰ (نیمه‌شب) تا ۱ (اکنون) */
  private fun today(f: Double): Long { val m = midnight(); return m + ((System.currentTimeMillis() - m) * f).toLong() }

  private fun iso(daysAgo: Long = 0): String = LocalDate.now(zone).minusDays(daysAgo).toString()

  private val products = listOf(
    Product(id = "h65-rice", name = "برنج سیلا ۵ کیلویی", unit = "بسته", salePrice = 1100.0),
    Product(id = "h65-oil", name = "روغن آفتاب‌گردان", unit = "بوتل", salePrice = 360.0),
    Product(id = "h65-tea", name = "چای سبز", unit = "بسته", salePrice = 250.0),
    Product(id = "h65-soap", name = "صابون", unit = "دانه", salePrice = 50.0),
  )

  private val debtors = listOf(
    Debtor(id = DEBTOR, name = "جلیل احمدی"),
    Debtor(id = "h65-nasim", name = "نسیم کریمی"),
    Debtor(id = "h65-long", name = LONG_NAME),
  )

  /** رسیدِ نقدِ قرضِ پیشین — «قرضِ وصول‌شده»، نه فروش */
  private fun receipt(at: Long) = DebtTransaction(id = "h65-rcv", debtorId = "h65-nasim", type = "receive", amount = 500.0, date = iso(), notes = "نقد", createdAt = at)

  /** امروز بی فروش، فقط ۵۰۰ ؋ رسیدِ قرض */
  fun empty(): ShopData = ShopData(products = products, debtors = debtors, transactions = listOf(receipt(today(0.5))))

  /** امروز: نقد، نسیه، کارت، یک برگشت و یک قرضِ وصول‌شده؛ و دیروز دو فروش */
  fun busy(): ShopData {
    val y = midnight(1)
    val sales = listOf(
      Sale(RETURNED, 105, finalTotal = 500.0, paidAmount = 500.0, createdAt = today(0.95), date = iso()),
      Sale(CARD, 104, finalTotal = 500.0, paidAmount = 500.0, paymentMethod = "card", createdAt = today(0.85), date = iso(),
        payments = listOf(SalePayment(method = "card", kind = "card", amountMinor = 50_000, status = "confirmed"))),
      Sale(LONG, 103, finalTotal = 1100.0, paidAmount = 0.0, remaining = 1100.0, debtGiven = 1100.0, paymentMethod = "credit", debtorId = "h65-long",
        createdAt = today(0.7), date = iso()),
      Sale(CREDIT, 102, finalTotal = 720.0, paidAmount = 0.0, remaining = 720.0, debtGiven = 720.0, paymentMethod = "credit", debtorId = DEBTOR,
        createdAt = today(0.55), date = iso()),
      Sale(CASH, 101, finalTotal = 1250.0, paidAmount = 1250.0, createdAt = today(0.4), date = iso()),
      Sale("h65-y1", 100, finalTotal = 360.0, paidAmount = 360.0, createdAt = y + 15 * 3_600_000L, date = iso(1)),
      Sale("h65-y2", 99, finalTotal = 2200.0, paidAmount = 2200.0, createdAt = y + 11 * 3_600_000L, date = iso(1)),
    )
    val items = listOf(
      SaleItem("h65-i1", RETURNED, "h65-tea", quantity = 2.0, unitPrice = 250.0, totalPrice = 500.0),
      SaleItem("h65-i2", CARD, "h65-tea", quantity = 2.0, unitPrice = 250.0, totalPrice = 500.0),
      SaleItem("h65-i3", LONG, "h65-rice", quantity = 1.0, unitPrice = 1100.0, totalPrice = 1100.0),
      SaleItem("h65-i4", CREDIT, "h65-oil", quantity = 2.0, unitPrice = 360.0, totalPrice = 720.0),
      SaleItem("h65-i5", CASH, "h65-rice", quantity = 1.0, unitPrice = 1100.0, totalPrice = 1100.0),
      SaleItem("h65-i6", CASH, "h65-soap", quantity = 3.0, unitPrice = 50.0, totalPrice = 150.0),
      SaleItem("h65-i7", "h65-y1", "h65-oil", quantity = 1.0, unitPrice = 360.0, totalPrice = 360.0),
      SaleItem("h65-i8", "h65-y2", "h65-rice", quantity = 2.0, unitPrice = 1100.0, totalPrice = 2200.0),
    )
    //  یک چای از دو چایِ فاکتورِ ۱۰۵ برگشت — ۲۵۰ ؋ نقد پس داده شد
    val ret = SaleReturn("h65-ret", saleId = RETURNED, saleItemId = "h65-i1", productId = "h65-tea", quantity = 1.0, amount = 250.0,
      date = iso(), createdAt = today(0.97))
    return ShopData(products = products, debtors = debtors, sales = sales, saleItems = items, saleReturns = listOf(ret), transactions = listOf(receipt(today(0.6))))
  }
}
