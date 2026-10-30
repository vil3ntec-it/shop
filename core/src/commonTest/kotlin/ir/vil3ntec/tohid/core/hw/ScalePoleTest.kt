package ir.vil3ntec.tohid.core.hw

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** HW-09 (پروتکلِ دوم و خواندنِ تکه‌تکهٔ سریال) و HW-10 (نمایشگرِ قطبی) — بی ترازو و نمایشگرِ واقعی */
/** «AnD» در نامِ آزمون‌ها = ترازوی A&D؛ Kotlin/Native «&» را در نام نمی‌پذیرد. */
class ScalePoleTest {

  private fun b(s: String) = s.encodeToByteArray()

  /** جمع‌کننده با نامِ لاتین — مرجعِ تابع در آزمونِ نام‌فارسی کلاسِ نام‌فارسی می‌سازد */
  private fun collector(p: ScaleProtocol, max: Int = 512) = SerialCollect({ p.complete(it) }, max)

  @Test fun `پروتکل‌ها قابل افزودن‌اند - NCI و AnD با شناسه`() {
    assertEquals(listOf("nci", "and"), Scales.all.map { it.id })
    assertEquals(AndScale, Scales.of("and"))
    assertNull(Scales.of("x"))
    assertContentEquals(b("Q\r\n"), AndScale.request())
    assertEquals(SerialParams(2400, 7, "even", 1), AndScale.serial)
  }

  @Test fun `AnD - ایستا، در حرکت، گرم و پوند، بیش از ظرفیت و شمارش`() {
    val st = AndScale.parse(b("ST,+00001.234 kg\r\n"))!!
    assertEquals(1.234, st.kg!!, 1e-9); assertFalse(st.motion); assertTrue(st.usable)
    val us = AndScale.parse(b("US,GS,+0000.500kg\r\n"))!!
    assertTrue(us.motion); assertFalse(us.usable)
    assertEquals(0.25, AndScale.parse(b("ST,+00250.00  g\r\n"))!!.kg!!, 1e-9)
    assertEquals(0.45359237, AndScale.parse(b("ST,NT,+0001.000 lb\r\n"))!!.kg!!, 1e-9)
    val ol = AndScale.parse(b("OL,+9999999E+19\r\n"))!!
    assertTrue(ol.over); assertNull(ol.kg); assertFalse(ol.usable)
    val qt = AndScale.parse(b("QT,+0000012 PC\r\n"))!!
    assertNull(qt.kg); assertFalse(qt.usable)
    val neg = AndScale.parse(b("ST,-00000.120 kg\r\n"))!!
    assertTrue(neg.under); assertFalse(neg.usable)
    assertNull(AndScale.parse(b("hello\r\n")))
  }

  @Test fun `AnD پیوسته - آخرین سطر کامل، نه سطر نیمه‌کاره`() {
    val stream = b("ST,+00001.000 kg\r\nUS,+00001.050 kg\r\nST,+00001.100 kg\r\nST,+0000")
    assertTrue(AndScale.complete(stream))
    assertEquals(1.1, AndScale.parse(stream)!!.kg!!, 1e-9)
    assertFalse(AndScale.complete(b("ST,+00001.000 kg")), "بی پایانِ سطر هنوز کامل نیست")
  }

  @Test fun `خواندن سریال تکه‌تکه - NCI و AnD با زباله و سقف بافر`() {
    //  NCI: پاسخ در سه تکه
    val nci = collector(NciScale)
    assertFalse(nci.add(b("\n00")))
    assertFalse(nci.add(b("1.250KG\r\n")))
    assertTrue(nci.add(b("S00\r\u0003")))
    assertEquals(1.25, NciScale.parse(nci.bytes())!!.kg!!, 1e-9)
    //  A&D پیوسته با زبالهٔ آغاز و یک کیلوبایت جریان: بافر از ۵۱۲ بزرگ‌تر نمی‌شود و آخرین سطر خوانده می‌شود
    val and = collector(AndScale, 512)
    and.add(byteArrayOf(0x7F, 0x00, 0x13))
    var done = false
    repeat(60) { i -> done = and.add(b("ST,+0000${i % 10}.000 kg\r\n")) }
    assertTrue(done)
    assertTrue(and.bytes().size <= 512)
    assertEquals(9.0, AndScale.parse(and.bytes())!!.kg!!, 1e-9)
    //  تکهٔ خالی کامل نمی‌کند
    assertFalse(collector(AndScale).add(ByteArray(0)))
  }

  @Test fun `نمایشگر قطبی - دو سطر ۲۰ نویسه، رقم لاتین، بی نویسه غیر ASCII`() {
    val f = PoleDisplay.frame("سلام 12", "Total ۱۲۵۰")
    assertContentEquals(byteArrayOf(0x1B, 0x40, 0x0C), f.copyOfRange(0, 3))
    val l1 = f.copyOfRange(3, 23).decodeToString()
    assertEquals("???? 12".padEnd(20), l1)
    assertContentEquals(byteArrayOf(0x1F, 0x24, 1, 2), f.copyOfRange(23, 27))
    assertEquals("Total 1250".padEnd(20), f.copyOfRange(27, 47).decodeToString())
    assertEquals(47, f.size)
    assertTrue(f.all { it.toInt() and 0xFF < 0x80 })
    assertEquals(20, PoleDisplay.fit("x".repeat(40)).length)
  }

  @Test fun `نمایشگر قطبی - سبد، پرداخت و باقی`() {
    assertEquals("1,250", PoleDisplay.amount(125_000))
    assertEquals("1,250.50", PoleDisplay.amount(125_050))
    assertEquals("-75", PoleDisplay.amount(-7_500))
    assertEquals("Items              3", PoleDisplay.row("Items", "3"))
    val cart = PoleDisplay.cart(3, 125_000)
    assertEquals("Items              3", cart.copyOfRange(3, 23).decodeToString())
    assertEquals("Total AFN      1,250", cart.copyOfRange(27, 47).decodeToString())
    assertEquals("WELCOME".padEnd(20), PoleDisplay.cart(0, 0).copyOfRange(3, 23).decodeToString())
    val paid = PoleDisplay.paid(125_000, tenderedMinor = 150_000)
    assertEquals("Paid AFN       1,500", paid.copyOfRange(3, 23).decodeToString())
    assertEquals("Change           250", paid.copyOfRange(27, 47).decodeToString())
    assertNotNull(PoleDisplay.paid(125_000))
    assertEquals("THANK YOU".padEnd(20), PoleDisplay.paid(125_000).copyOfRange(27, 47).decodeToString())
  }
}
