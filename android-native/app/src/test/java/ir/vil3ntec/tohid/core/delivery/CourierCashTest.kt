package ir.vil3ntec.tohid.core.delivery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  دفترِ نقدِ پیک و حسابِ تسویه — همان قاعده‌های `server/src/lib/delivery.js`
 *  (`assign`: درگیر + سفارش > سقف ⇒ cash_cap_exceeded) و
 *  `delivery-states.js` (`settlementTotals`، S40).
 */
class CourierCashTest {

  private fun courier(held: Long, live: Long, cap: Long = 1_000_000, active: Boolean = true, liveCount: Int = 0) =
    CourierRow(id = "C", name = "احمد", active = active, cashCapMinor = cap, cashHeldMinor = held, liveExpectedMinor = live, liveCount = liveCount)

  @Test fun `جای خالی و جا شدن همان شرطِ سرور است`() {
    val c = courier(held = 400_000, live = 300_000)
    assertEquals(700_000, CourierCash.committed(c))
    assertEquals(300_000, CourierCash.headroom(c))
    assertTrue("درست تا سقف جا می‌شود", CourierCash.fits(c, 300_000))
    assertFalse("یک واحد بیشتر ⇒ سرور رد می‌کند", CourierCash.fits(c, 300_001))
    assertTrue("سفارشِ پیش‌پرداخت (۰) همیشه جا می‌شود تا وقتی از سقف نگذشته", CourierCash.fits(c, 0))
    assertEquals(70, CourierCash.usePercent(c))
  }

  @Test fun `به سقف رسیده و از سقف گذشته — دلیلِ فارسی`() {
    val full = courier(held = 1_000_000, live = 0)
    assertTrue(CourierCash.atCap(full))
    assertFalse(CourierCash.overCap(full))
    assertTrue(CourierCash.fits(full, 0))
    assertFalse(CourierCash.fits(full, 1))
    assertTrue(CourierCash.autoBlockReason(full)!!.contains("به سقفِ نقد رسیده"))
    assertTrue(CourierCash.autoBlockReason(full)!!.contains("10,000"))

    val over = courier(held = 1_200_000, live = 0)
    assertTrue(CourierCash.overCap(over))
    assertFalse("بالای سقف حتی پیش‌پرداخت نمی‌گیرد (سرور: held+live+0 > cap)", CourierCash.fits(over, 0))
    assertEquals(0, CourierCash.headroom(over))
    assertEquals(100, CourierCash.usePercent(over))
    assertTrue(CourierCash.autoBlockReason(over)!!.contains("گذشته"))

    assertEquals("غیرفعال است", CourierCash.autoBlockReason(courier(0, 0, active = false)))
    assertNull(CourierCash.autoBlockReason(courier(0, 0)))
  }

  @Test fun `سقفِ صفر یعنی نقد نگیرد`() {
    val none = courier(held = 0, live = 0, cap = 0)
    assertTrue(CourierCash.fits(none, 0))
    assertFalse(CourierCash.fits(none, 1))
    assertEquals(0, CourierCash.usePercent(none))
  }

  @Test fun `دفتر — گرفته، تحویل‌داده، مانده`() {
    val c = courier(held = 250_000, live = 0)
    val l = CourierCash.ledger(c, SettlementSums(collectedCashMinor = 1_240_000, receivedMinor = 1_200_000, fareTotalMinor = 40_000, shortageMinor = 40_000))
    assertEquals(1_490_000, l.collectedMinor)
    assertEquals(1_200_000, l.handedBackMinor)
    assertEquals(250_000, l.outstandingMinor)
    assertEquals(40_000, l.shortageMinor)
  }

  @Test fun `تسویه S40 — موردِ انتظار ۱۲٬۴۰۰، شمرده ۱۲٬۰۰۰ ⇒ کسریِ ۴۰۰ با دلیل`() {
    val rows = listOf(SettleRow("A", expectedCollectMinor = 1_240_000, cashCollectedMinor = 1_240_000, fareMinor = 30_000))
    val t = SettlementMath.totals(rows, handedOverMinor = 1_200_000, receivedMinor = 1_200_000)
    assertEquals(1_240_000, t.handoverDueMinor)
    assertEquals(40_000, t.shortageMinor)
    assertEquals(0, t.excessMinor)
    assertEquals(0, t.collectionDiffMinor)
    assertTrue(t.reasonRequired)
    assertFalse(t.handedOverMismatch)
  }

  @Test fun `تسویه — کرایه از نقد، اضافه، و اختلافِ مشتری جدا`() {
    val rows = listOf(
      SettleRow("A", expectedCollectMinor = 1_000_000, cashCollectedMinor = 900_000, fareMinor = 20_000),
      SettleRow("B", expectedCollectMinor = 0, cashCollectedMinor = 0, fareMinor = 20_000),
    )
    val t = SettlementMath.totals(rows, handedOverMinor = 860_000, receivedMinor = 870_000, farePaidFromCash = true)
    assertEquals(2, t.count)
    assertEquals(-100_000, t.collectionDiffMinor)
    assertEquals(40_000, t.fareTotalMinor)
    assertEquals(860_000, t.handoverDueMinor)
    assertEquals(10_000, t.excessMinor)
    assertEquals(0, t.shortageMinor)
    assertTrue(t.handedOverMismatch)
    assertTrue("اختلافِ مشتری به‌تنهایی هم دلیل می‌خواهد", SettlementMath.totals(rows, 860_000, 860_000, true).reasonRequired)
  }

  @Test fun `تسویهٔ بی اختلاف دلیل نمی‌خواهد`() {
    val rows = listOf(SettleRow("A", expectedCollectMinor = 500_000, cashCollectedMinor = 500_000))
    val t = SettlementMath.totals(rows, 500_000, 500_000)
    assertFalse(t.reasonRequired)
    assertEquals(0, t.differenceMinor)
  }
}
