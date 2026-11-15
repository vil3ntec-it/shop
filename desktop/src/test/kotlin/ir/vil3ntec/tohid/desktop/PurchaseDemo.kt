package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.core.calendar.CivilDays
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ProductPack
import ir.vil3ntec.tohid.data.PurchaseBook
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.Supplier
import ir.vil3ntec.tohid.todayIso

/**
 *  دفترِ نمونهٔ «خریدها» (e-rows-63b) برای آزمونِ صفحه و عکس: همه از همان `PurchaseBook.record` (نه ردیفِ دستی):
 *   #1 پخش البرز، ۳ روز پیش، پنج قلم (آرد با بارکد، سری و انقضا)، ۵٬۰۰۰ از ۸٬۳۲۰ پرداخت ⇒ نیمه‌پرداخت
 *   #2 پامیر، امروز، روغن، پرداختِ کامل
 *   #3 پخش البرز، ۲۰ روز پیش، نسیه با سررسیدِ گذشته ⇒ سررسید گذشته
 *   #4 پامیر، دیروز، نسیه با سررسیدِ ۱۰ روزِ دیگر
 */
internal object PurchaseDemo {
  const val ALBORZ = "s-alborz"
  const val PAMIR = "s-pamir"
  const val FLOUR_CODE = "6261111111111"

  private fun day(delta: Int): String = CivilDays.addDays(todayIso(), delta) ?: todayIso()

  fun full(): ShopData {
    var n = 0
    val id = { "pd-${n++}" }
    var d = ShopData(
      products = listOf(
        Product(id = "flour", name = "آرد", unit = "کیلو", purchasePrice = 40.0, salePrice = 50.0, barcodes = listOf(FLOUR_CODE)),
        Product(id = "oil", name = "روغن", unit = "بوتل", purchasePrice = 300.0, salePrice = 360.0, barcodes = listOf("6262222222222"), packs = listOf(ProductPack("کارتن", 12_000, listOf("16262222222222")))),
        Product(id = "tea", name = "چای سبز", unit = "بسته", purchasePrice = 150.0, salePrice = 190.0, sku = "TEA-01"),
        Product(id = "rice", name = "برنج", unit = "کیلو", purchasePrice = 90.0, salePrice = 110.0),
        Product(id = "salt", name = "نمک", unit = "بسته", purchasePrice = 20.0, salePrice = 30.0),
        Product(id = "sugar", name = "شکر", unit = "کیلو", purchasePrice = 70.0, salePrice = 85.0, barcodes = listOf("6263333333333")),
      ),
      suppliers = listOf(
        Supplier(id = ALBORZ, name = "شرکت پخش البرز", phone = "0791112233", address = "کابل، مندوی"),
        Supplier(id = PAMIR, name = "عمده‌فروشی پامیر", phone = "0700223344"),
      ),
    )
    fun rec(sid: String, lines: List<PurchaseBook.Line>, paid: Double, date: String, due: String? = null) {
      d = (PurchaseBook.record(d, sid, lines, paid, date, "", todayIso(), n.toLong(), id, dueDate = due) as PurchaseBook.Result.Ok).data
    }
    rec(ALBORZ, listOf(PurchaseBook.Line("sugar", 30.0, 70.0)), 0.0, day(-20), due = day(-5))
    rec(
      ALBORZ,
      listOf(
        PurchaseBook.Line("flour", 50.0, 40.0, expiryDate = day(200)), PurchaseBook.Line("oil", 12.0, 310.0),
        PurchaseBook.Line("tea", 4.0, 150.0), PurchaseBook.Line("rice", 20.0, 90.0), PurchaseBook.Line("salt", 10.0, 20.0),
      ),
      5000.0, day(-3),
    )
    rec(PAMIR, listOf(PurchaseBook.Line("tea", 10.0, 145.0)), 0.0, day(-1), due = day(10))
    rec(PAMIR, listOf(PurchaseBook.Line("oil", 6.0, 305.0)), 1830.0, day(0))
    //  سریِ ساختِ آرد روی ورودِ انبارِ همان خرید (فرمِ «ورودِ کالا» می‌نویسد؛ فاکتورِ خرید کادرش را ندارد)
    return d.copy(warehouseEntries = d.warehouseEntries.map { if (it.productId == "flour") it.copy(batch = "A-17") else it })
  }

  /** شمارهٔ فاکتورهای بالا به همان ترتیبِ ثبت */
  const val OVERDUE_NO = 1
  const val BIG_NO = 2
  const val CREDIT_NO = 3
  const val PAID_NO = 4
}
