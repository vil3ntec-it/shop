package ir.vil3ntec.tohid.ui.components.pos

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
}
