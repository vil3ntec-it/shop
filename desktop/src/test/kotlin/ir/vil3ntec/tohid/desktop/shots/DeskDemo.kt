package ir.vil3ntec.tohid.desktop.shots

import ir.vil3ntec.tohid.data.DebtTransaction
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.Expense
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SaleItem
import ir.vil3ntec.tohid.data.ShopData
import java.time.LocalDate
import java.time.ZoneId

/**
 *  دفترِ نمایشیِ پوستهٔ کامپیوتر (عکس‌های پیش/پسِ `claude/e-desk-d1`، نه دادهٔ دکانِ واقعی): کالاها و ورودهای [StockDemo]،
 *  و ۳۰ روز فروشِ تعیین‌شده (نقد، کارت، نسیه با قرض‌دار)، هشت قرض‌دار با برد و رسید، و چند مصرف. ساعتِ فروش‌های امروز میانِ
 *  نیمه‌شب و همین لحظه است تا همیشه «امروز» و پیش از اکنون باشند. فقط API ِ دفتر ⇒ روی شاخهٔ پایه هم کامپایل می‌شود.
 */
internal object DeskDemo {
  private val zone = ZoneId.systemDefault()

  private fun midnight(daysAgo: Long): Long = LocalDate.now(zone).minusDays(daysAgo).atStartOfDay(zone).toInstant().toEpochMilli()

  private fun iso(daysAgo: Long): String = LocalDate.now(zone).minusDays(daysAgo).toString()

  private val names = listOf(
    "احمد رحیمی", "محمود کریمی", "فریدون نوری", "جلیل احمدی", "نسیم کریمی", "حمید صدیقی", "زلمی امیری", "عبدالله وحیدی",
  )

  fun full(): ShopData {
    val base = StockDemo.full()
    val products = base.products
    val debtors = names.mapIndexed { i, n -> Debtor(id = "dd$i", name = n, phone = "07${(100_000_00 + i * 1_234_567) % 100_000_000}") }
    val sales = ArrayList<Sale>()
    val items = ArrayList<SaleItem>()
    var no = 2400
    val now = System.currentTimeMillis()
    for (day in 29 downTo 0) {
      val start = midnight(day.toLong())
      val end = if (day == 0) now else start + 21 * 3_600_000L
      //  روزهای تعیین‌شده: کم و زیاد، با پنجشنبه‌های شلوغ‌تر
      val count = 6 + ((day * 7 + 3) % 9)
      for (k in 0 until count) {
        val id = "ds$day-$k"
        val at = start + (8 * 3_600_000L) + ((end - start - 8 * 3_600_000L).coerceAtLeast(60_000L) * (k + 1) / (count + 1))
        val lines = 1 + (k + day) % 4
        var total = 0.0
        repeat(lines) { j ->
          val p = products[(day * 5 + k * 3 + j * 7) % products.size]
          val q = 1.0 + ((k + j) % 3)
          val line = q * p.salePrice
          total += line
          items += SaleItem("$id-$j", id, p.id, quantity = q, unitPrice = p.salePrice, purchasePrice = p.purchasePrice, totalPrice = line)
        }
        val kind = (k + day) % 7
        val credit = kind == 3
        val card = kind == 5
        val debtor = if (credit) debtors[(day + k) % debtors.size].id else null
        sales += Sale(
          id, no++, total = total, finalTotal = total,
          paidAmount = if (credit) 0.0 else total, remaining = if (credit) total else 0.0, debtGiven = if (credit) total else 0.0,
          paymentMethod = when { credit -> "credit"; card -> "card"; else -> "cash" },
          debtorId = debtor, createdAt = at, date = iso(day.toLong()),
          status = if (day == 2 && k == 1) "cancelled" else "completed",
        )
      }
    }
    val transactions = ArrayList<DebtTransaction>()
    debtors.forEachIndexed { i, d ->
      transactions += DebtTransaction("dt-g$i", d.id, "give", amount = 1_500.0 + i * 850, date = iso(40L + i * 9), createdAt = midnight(40L + i * 9))
      if (i % 2 == 0) transactions += DebtTransaction("dt-r$i", d.id, "receive", amount = 600.0 + i * 120, date = iso(i.toLong()), createdAt = midnight(i.toLong()) + 10 * 3_600_000L)
    }
    val expenses = listOf(
      Expense("dx1", "کرایهٔ دکان", "کرایه", 12_000.0, iso(3), createdAt = midnight(3)),
      Expense("dx2", "برق", "خدمات", 1_850.0, iso(1), createdAt = midnight(1)),
      Expense("dx3", "چای و نان", "روزانه", 240.0, iso(0), createdAt = midnight(0) + 9 * 3_600_000L),
    )
    return base.copy(
      debtors = debtors,
      sales = sales + base.sales,
      saleItems = items + base.saleItems,
      transactions = transactions,
      expenses = expenses,
    )
  }
}
