package ir.vil3ntec.tohid.ui.components.pos

import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.SalesEngine.CartLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** نوارِ تیرهٔ سبدِ گوشی: شمار، عکس‌های تازه‌ترین قلم‌ها، «+N» و حرفِ اول (phone-ui). */
class CartSummaryTest {

  private fun line(id: String, name: String = id, product: Boolean = true) = CartSummary.Line(id, name, product)

  @Test
  fun `سبدِ خالی نوار ندارد`() {
    val m = CartSummary.of(emptyList(), 0.0)
    assertFalse(m.visible); assertEquals(0, m.count); assertEquals(emptyList<CartSummary.Thumb>(), m.thumbs); assertEquals(0, m.more)
  }

  @Test
  fun `شمار ردیف‌هاست و عکس‌ها از تازه‌ترین`() {
    val m = CartSummary.of(listOf(line("a", "برنج"), line("b", "روغن"), line("c", "صابون"), line("d", "چای"), line("e", "قند")), 820.0)
    assertTrue(m.visible)
    assertEquals(5, m.count)
    assertEquals(820.0, m.total, 0.0)
    assertEquals(listOf("e", "d", "c"), m.thumbs.map { it.id })
    assertEquals(listOf("ق", "چ", "ص"), m.thumbs.map { it.initials })
    assertEquals(2, m.more)
  }

  @Test
  fun `کمتر از سه قلم بی «+N»`() {
    val m = CartSummary.of(listOf(line("a"), line("free", "قلم آزاد", product = false)), 10.0)
    assertEquals(listOf("free", "a"), m.thumbs.map { it.id })
    assertFalse(m.thumbs.first().hasProduct)
    assertEquals(0, m.more)
  }

  @Test
  fun `حرفِ اول — نیم‌فاصله و نشانه رد می‌شوند، لاتین بزرگ، خالی «؟»`() {
    assertEquals("ن", CartSummary.initials("‌نان"))
    assertEquals("C", CartSummary.initials("  coca cola"))
    assertEquals("3", CartSummary.initials("«3 in 1»"))
    assertEquals("؟", CartSummary.initials("  "))
    assertEquals("؟", CartSummary.initials(""))
  }

  /* ---------------- نسخهٔ ۳: ردیف‌های سبدِ باز (r2) و تخفیفِ قیمتِ دستی ---------------- */

  private val rice = Product("rice", name = "برنج", unit = "کیلوگرم", salePrice = 120.0)
  private val soap = Product("soap", name = "صابون", unit = "عدد", salePrice = 50.0)
  private val gone = CartLine("gone", 1.0)

  @Test
  fun `ردیف‌ها به ترتیبِ سبد، با پله و جمعِ ردیف؛ کالای حذف‌شده کنار می‌رود`() {
    val cart = listOf(
      CartLine("rice", 2.5),
      gone,
      CartLine("soap", 3.0, unitPrice = 45.0),
      CartLine("f1", 2.0, unitPrice = 30.0, label = "کیسه"),
    )
    val rows = CartSummary.rows(cart, listOf(rice, soap))
    assertEquals(listOf("rice", "soap", "f1"), rows.map { it.id })

    val r = rows[0]
    assertEquals("برنج", r.name); assertEquals(0.1, r.step, 0.0); assertEquals(300.0, r.lineTotal, 0.0)
    assertFalse(r.custom); assertFalse(r.lastStep); assertTrue(r.hasProduct)

    val s = rows[1]
    assertEquals(45.0, s.unitPrice, 0.0); assertEquals(135.0, s.lineTotal, 0.0); assertTrue(s.custom); assertEquals(1.0, s.step, 0.0)

    val f = rows[2]
    assertEquals("کیسه", f.name); assertTrue(f.free); assertTrue(f.custom); assertFalse(f.hasProduct)
    assertEquals(60.0, f.lineTotal, 0.0); assertEquals("ک", f.thumb.initials)
  }

  @Test
  fun `پلهٔ آخر ⇐ سطل`() {
    val rows = CartSummary.rows(listOf(CartLine("soap", 1.0), CartLine("rice", 0.1)), listOf(rice, soap))
    assertTrue(rows.all { it.lastStep })
  }

  @Test
  fun `تخفیف فقط از قیمتِ دستیِ کمتر؛ گران‌تر و آزاد و حذف‌شده صفر`() {
    val cart = listOf(
      CartLine("soap", 3.0, unitPrice = 45.0),   // ۱۵۰ − ۱۳۵ = ۱۵
      CartLine("rice", 2.0, unitPrice = 130.0),  // گران‌تر ⇐ صفر
      CartLine("f1", 1.0, unitPrice = 10.0, label = "کیسه"),
      CartLine("gone", 1.0, unitPrice = 1.0),
    )
    assertEquals(15.0, CartSummary.manualDiscount(cart, listOf(rice, soap)), 0.0)
    assertEquals(0.0, CartSummary.manualDiscount(listOf(CartLine("soap", 2.0)), listOf(soap)), 0.0)
  }

  @Test
  fun `مدلِ نوار از سبد و کالاها — ردیفِ آزاد نامِ خودش را دارد`() {
    val cart = listOf(CartLine("rice", 1.0), CartLine("soap", 1.0), CartLine("f1", 1.0, 5.0, "کیسه"), CartLine("x", 1.0, 5.0, "تار"))
    val m = CartSummary.of(cart, listOf(rice, soap), 999.0)
    assertEquals(4, m.count)
    assertEquals(listOf("ت", "ک", "ص"), m.thumbs.map { it.initials })
    assertEquals(listOf(false, false, true), m.thumbs.map { it.hasProduct })
    assertEquals(1, m.more)
    assertEquals(999.0, m.total, 0.0)
  }
}

/** اندازهٔ قلمِ مبلغ‌های درشت و خطِ موجودیِ کارت (نسخهٔ ۳) */
class PhoneSaleTextTest {
  @Test
  fun `مبلغِ دراز کوچک‌تر می‌شود و هرگز بزرگ‌تر نه`() {
    val texts = listOf("85 ؋", "1,650 ؋", "12,500 ؋", "1,234,567 ؋", "12,345,678 ؋", "1,234,567,890 ؋")
    val price = texts.map { PhoneSaleText.priceSp(it) }
    val total = texts.map { PhoneSaleText.totalSp(it) }
    assertEquals(14, price.first()); assertEquals(34, total.first())
    assertTrue(price.zipWithNext().all { (a, b) -> b <= a })
    assertTrue(total.zipWithNext().all { (a, b) -> b <= a })
    assertEquals(30, PhoneSaleText.totalSp("12,345,678 ؋"))
    assertEquals(13, PhoneSaleText.priceSp("4,250,000 ؋"))
    assertTrue(PhoneSaleText.priceSp("1,234,567,890 ؋") < 13)
  }

  @Test
  fun `موجودی با واحد، صفر «تمام شد»`() {
    assertEquals("40 کیلوگرم مانده", PhoneSaleText.stock(40.0, "کیلوگرم"))
    assertEquals("2.5 مانده", PhoneSaleText.stock(2.5, ""))
    assertEquals("تمام شد", PhoneSaleText.stock(0.0, "عدد"))
    assertEquals("تمام شد", PhoneSaleText.stock(-3.0, "عدد"))
  }
}
