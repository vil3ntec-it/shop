package ir.vil3ntec.tohid.core.stock

import ir.vil3ntec.tohid.core.stock.CountAbsorb.Event
import ir.vil3ntec.tohid.core.stock.CountAbsorb.Line
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** INV-10: فروشِ آفلاینِ پیش از شمارش که پس از ثبتِ شمارش می‌رسد (بردارهای کامل: `CountAbsorbVectorsTest`) */
class CountAbsorbTest {

  private val t = 1_760_000_000_000L
  private val counted = t + 1_800_000          // ۱۰:۳۰
  private val made = counted + 60_000
  private val receipt = Event("in", "p", t - 3_600_000, 20_000, "purchase_in")
  private val lateSale = Event("sale", "p", t, -3_000, "sale")
  private fun line(seen: Long, correctedAt: Long? = null, serverAt: Long? = null) =
    Line("L1", "p", counted, seen, made, correctedAt, serverAt)

  @Test fun lateOfflineSaleBeforeTheCountIsAbsorbed() {
    //  B فقط ورودی را داشت (۲۰) و ۱۷ شمرد ⇒ −۳؛ فروشِ ۱۰:۰۰ ِ A بعد می‌رسد ⇒ جمعِ دفتر ۱۴، موجودی ۱۷
    val seen = CountAbsorb.seen(listOf(receipt), "p", counted, made)
    assertEquals(20_000, seen)
    val events = listOf(receipt, Event("own", "p", made, -3_000, "count_adjust", refId = "L1"), lateSale)
    val g = CountAbsorb.gaps(listOf(line(seen)), events).getValue("p")
    assertEquals(-3_000, g.gapMilli)
    assertEquals(17_000, 14_000 - g.gapMilli)
  }

  @Test fun saleTheCounterAlreadyHadIsNotAbsorbedAgain() {
    val seen = CountAbsorb.seen(listOf(receipt, lateSale), "p", counted, made)
    assertTrue(CountAbsorb.gaps(listOf(line(seen)), listOf(receipt, lateSale)).isEmpty())
  }

  @Test fun backDatedSaleAfterTheCountIsRefused() {
    //  ساعتِ فروشنده عقب کشیده شد؛ سرورِ v3 زمانِ تصحیح‌شده = پس از ساختِ سندِ شمارش
    val skewed = lateSale.copy(correctedAt = made + 1)
    assertTrue(CountAbsorb.gaps(listOf(line(20_000)), listOf(receipt, skewed)).isEmpty())
    //  شمارندهٔ ساعت‌جلو: سرور سند را زودتر دید ⇒ ردیف معتبر نیست
    assertTrue(CountAbsorb.gaps(listOf(line(20_000, serverAt = counted - CountAbsorb.SKEW_MS - 1)), listOf(receipt, lateSale)).isEmpty())
    assertTrue(CountAbsorb.gaps(listOf(line(20_000, correctedAt = made)), listOf(receipt, lateSale)).isEmpty())
  }

  @Test fun onlyTheLatestCountAbsorbsAndDuplicatesCountOnce() {
    val l2 = Line("L2", "p", counted + 3_600_000, 20_000, made + 3_600_000)
    val g = CountAbsorb.gaps(listOf(line(20_000), l2, l2), listOf(receipt, lateSale, lateSale)).getValue("p")
    assertEquals("L2", g.lineId)
    assertEquals(-3_000, g.gapMilli)
  }

  @Test fun overflowFails() {
    assertFailsWith<ArithmeticException> {
      CountAbsorb.seen(listOf(Event("a", "p", 10, Long.MAX_VALUE), Event("b", "p", 10, 1)), "p", 10, 10)
    }
  }
}
