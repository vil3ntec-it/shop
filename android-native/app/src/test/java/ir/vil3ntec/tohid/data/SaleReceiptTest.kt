package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.LocaleText
import ir.vil3ntec.tohid.core.promo.PromoCore
import ir.vil3ntec.tohid.core.text.LocaleFormat
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.unitMoney
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  S3 — عددها و متن‌های فاکتورِ صفحه و چاپ (`SaleReceipt`)، پشتِ پرچمِ `shop.promo_stamp`.
 *  ستونِ قلم‌ها = مبلغِ پرداختی؛ «جمع اقلام» = جمعِ همان ستون؛ تخفیفِ خودکار فقط آگاهی.
 */
class SaleReceiptTest {

  /** ۳ × ۳۵۰ = ۱۰۵۰، تخفیفِ خودکار ۵۰ ⇒ ۱۰۰۰؛ قیمتِ واحدِ ثبت‌شده ۳۳۳٫۳۳… */
  private val promoted = SaleItem(
    id = "i", saleId = "s1", productId = "p", quantity = 3.0, unitPrice = 1000.0 / 3, totalPrice = 1000.0,
    listPriceMinor = 35000, promo = listOf(SalePromo("r1", 1, "ده درصد", 5000)),
  )
  private val plain = SaleItem(id = "j", saleId = "s1", productId = "q", quantity = 1.5, unitPrice = 115.0, totalPrice = 173.0)
  private val sale = Sale(id = "s1", total = 1173.0, discount = 73.0, finalTotal = 1100.0, promoDiscountMinor = 5000)

  private fun detail(item: SaleItem, stamped: Boolean, unit: String = "کیلو") =
    SaleReceipt.detail(item, unit, stamped, ::money, ::unitMoney, ::qty)

  @Test fun `پرچمِ خاموش ⇒ دقیقاً متن و عددِ پیشین`() {
    //  همان رشته‌ای که InvoiceDialog و Receipt پیش از این می‌ساختند
    assertEquals("${money(3.0)} کیلو × ${money(1000.0 / 3)}", detail(promoted, false))
    assertEquals("3 کیلو × 333", detail(promoted, false))
    assertEquals("2 × 115", detail(plain, false, unit = ""))
    assertEquals(SaleReceipt.Line(1000.0 / 3, 0.0, 1000.0), SaleReceipt.line(promoted, false))
    assertNull(SaleReceipt.included(sale, listOf(promoted, plain), false))
  }

  @Test fun `روشن — قلمِ تخفیف‌دار «مقدار × فهرست − تخفیف» و ستون همان مبلغِ پرداختی`() {
    assertEquals("3 کیلو × 350 − 50 تخفیفِ خودکار", detail(promoted, true))
    assertEquals("1.5 کیلو × 115", detail(plain, true))
    assertEquals("3 کیلو × 333.33", detail(promoted.copy(promo = emptyList()), true))
    for (it in listOf(promoted, plain)) {
      val l = SaleReceipt.line(it, true)
      //  «مقدار × قیمتِ نشان‌داده − تخفیف» = همان ستون (با گردکردنِ همان `lineTotal`)
      assertEquals(it.totalPrice, SalesEngine.lineTotal(l.unit, it.quantity) - l.promo, 0.0)
      assertEquals(it.totalPrice, l.total, 0.0)
    }
  }

  @Test fun `روشن — «جمع اقلام» = جمعِ ستون؛ تخفیفِ خودکار یک ردیفِ آگاهی و فقط یک بار کم می‌شود`() {
    val items = listOf(promoted, plain)
    assertEquals(sale.total, items.sumOf { SaleReceipt.line(it, true).total }, 0.0)
    val inc = SaleReceipt.included(sale, items, true)!!
    assertEquals(SaleReceipt.Included("شاملِ تخفیفِ خودکار (ده درصد)", 50.0), inc)
    //  «مبلغ نهایی» = «جمع اقلام» − تخفیفِ دستی؛ ردیفِ آگاهی کم نمی‌شود
    assertEquals(sale.finalTotal, sale.total - sale.discount, 0.0)
    //  نامِ ثبت‌شده بر نامِ امروزِ قاعده جلو است؛ قلمِ بی نام از قاعدهٔ امروز
    assertEquals("شاملِ تخفیفِ خودکار (ده درصد)", SaleReceipt.included(sale, items, true) { "نامِ تازه" }!!.label)
    val unnamed = promoted.copy(promo = listOf(SalePromo("r1", 1, "", 5000)))
    assertEquals("شاملِ تخفیفِ خودکار (نامِ امروز)", SaleReceipt.included(sale, listOf(unnamed), true) { "نامِ امروز" }!!.label)
    assertEquals("شاملِ تخفیفِ خودکار", SaleReceipt.included(sale, listOf(unnamed), true)!!.label)
    //  فاکتورِ بی فیلد (ساختِ پیشین) یا صفر ⇒ بی ردیف
    assertNull(SaleReceipt.included(sale.copy(promoDiscountMinor = null), items, true))
    assertNull(SaleReceipt.included(sale.copy(promoDiscountMinor = 0), items, true))
  }

  @Test fun `از فروشِ واقعی — ستون، «جمع اقلام» و ردیفِ آگاهی با هم می‌خوانند`() {
    val tea = Product(id = "tea", name = "چای", unit = "عدد", category = "نوشیدنی", purchasePrice = 100.0, salePrice = 300.0)
    val rice = Product(id = "rice", name = "برنج", unit = "کیلوگرم", category = "خوراکی", salePrice = 115.0)
    val d = ShopData(products = listOf(tea, rice), warehouseEntries = listOf(WarehouseEntry(id = "w1", productId = "tea", units = 50.0), WarehouseEntry(id = "w2", productId = "rice", units = 50.0)))
    val r1 = PromoCore.Rule(id = "r1", name = "ده درصدِ نوشیدنی", kind = "percent", bp = 1000, scope = PromoCore.Scope(categories = listOf("نوشیدنی")), channels = listOf("pos"))
    val snap = PromoEngine.Snapshot(enabled = true, rules = listOf(r1), stamp = true)
    val cart = listOf(SalesEngine.CartLine("tea", 3.0), SalesEngine.CartLine("rice", 1.5))
    val o = PromoEngine.evaluate(d, cart, snap, emptyList(), "2026-10-19", 600)!!
    var n = 0
    val r = PromoEngine.stamp(SalesEngine.record(d, PromoEngine.priced(cart, o), SalesEngine.Checkout(discountValue = 20.0), "2026-10-19", 1L, { "x${n++}" }), o) as SalesEngine.Result.Ok
    val s = r.data.sales.last()
    val items = r.data.saleItems.filter { it.saleId == s.id }
    assertEquals(s.total, items.sumOf { SaleReceipt.line(it, true).total }, 0.0)
    assertEquals(SaleReceipt.Included("شاملِ تخفیفِ خودکار (ده درصدِ نوشیدنی)", o.discount), SaleReceipt.included(s, items, true))
    assertEquals(s.finalTotal, s.total - s.discount, 0.0)
    assertTrue(items.all { SalesEngine.lineTotal(SaleReceipt.line(it, true).unit, it.quantity) - SaleReceipt.line(it, true).promo == it.totalPrice })
  }

  @Test fun `قیمتِ واحدِ اعشاری با دو رقم — صفحه، چاپ و نسخهٔ ۲`() {
    assertEquals("333.33", unitMoney(1000.0 / 3))
    assertEquals("1,250", unitMoney(1250.0))
    assertEquals("1,234.50", unitMoney(1234.5))
    assertEquals("0", unitMoney(Double.NaN))
    assertEquals("333.33", LocaleText.unitAmount(1000.0 / 3, LocaleFormat.DIGITS_LATN))
    assertEquals("۱٬۲۳۴٫۵۰", LocaleText.unitAmount(1234.5, LocaleFormat.DIGITS_FA))
    assertEquals(LocaleText.amount(1250.0, LocaleFormat.DIGITS_FA), LocaleText.unitAmount(1250.0, LocaleFormat.DIGITS_FA))
  }

  @Test fun `برچسبِ پنجرهٔ تسویه`() {
    assertEquals("تخفیفِ خودکار (ده درصد، کدِ عید)", SaleReceipt.promoLabel(listOf("ده درصد", "کدِ عید")))
    assertEquals("تخفیفِ خودکار", SaleReceipt.promoLabel(emptyList()))
  }
}
