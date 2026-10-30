package ir.vil3ntec.tohid.core.orders

import ir.vil3ntec.tohid.core.orders.StockHold.Line
import ir.vil3ntec.tohid.core.orders.StockHold.Phase
import ir.vil3ntec.tohid.core.orders.StockHold.Ref
import ir.vil3ntec.tohid.core.orders.StockHold.Ttl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StockHoldTest {

  @Test
  fun ttlBoundsMirrorServer() {
    //  همان intIn(…, 60, 86400) ِ stock.saveSettings
    assertFalse(StockHold.ttlSecValid(59))
    assertTrue(StockHold.ttlSecValid(60))
    assertTrue(StockHold.ttlSecValid(86_400))
    assertFalse(StockHold.ttlSecValid(86_401))
    assertFalse(StockHold.ttlSecValid(0))
    assertFalse(StockHold.ttlSecValid(-60))
  }

  @Test
  fun parseMinutesAcceptsPersianArabicLatin() {
    assertEquals(Ttl.Ok(1_800), StockHold.parseTtlMinutes("30"))
    assertEquals(Ttl.Ok(1_800), StockHold.parseTtlMinutes(" ۳۰ "))
    assertEquals(Ttl.Ok(1_800), StockHold.parseTtlMinutes("٣٠"))
    assertEquals(Ttl.Ok(60), StockHold.parseTtlMinutes("1"))
    assertEquals(Ttl.Ok(86_400), StockHold.parseTtlMinutes("1440"))
  }

  @Test
  fun parseMinutesRejectsWhatServerRejects() {
    for (bad in listOf("", "  ", "0", "1441", "-5", "2.5", "۱۰د", "abc", "99999999999")) {
      val r = StockHold.parseTtlMinutes(bad)
      assertTrue(r is Ttl.Error, "«$bad» باید رد شود")
    }
  }

  @Test
  fun everyAcceptedMinuteIsAcceptedByServer() {
    for (m in 0..1500) {
      val r = StockHold.parseTtlMinutes(m.toString())
      assertEquals(StockHold.ttlSecValid(m * 60L), r is Ttl.Ok, "دقیقهٔ $m")
    }
  }

  @Test
  fun fieldAndLabel() {
    assertEquals("30", StockHold.minutesField(1_800))
    assertEquals("2", StockHold.minutesField(90))   // هرگز کمتر از سرور
    assertEquals("30 دقیقه", StockHold.ttlLabel(1_800))
    assertEquals("2 ساعت", StockHold.ttlLabel(7_200))
    assertEquals("1 ساعت و 30 دقیقه", StockHold.ttlLabel(5_400))
    assertEquals("90 ثانیه", StockHold.ttlLabel(90))
  }

  @Test
  fun phaseMapping() {
    assertEquals(Phase.REVIEW, StockHold.phase("held", review = true))
    assertEquals(Phase.HELD, StockHold.phase("held", review = false))
    assertEquals(Phase.EXPIRED, StockHold.phase("expired", review = false))
    assertEquals(Phase.COMMITTED, StockHold.phase("committed", review = false))
    assertEquals(Phase.RELEASED, StockHold.phase("released", review = false))
    assertNull(StockHold.phase("something_new", review = false))
    assertTrue(StockHold.isWarning(Phase.REVIEW))
    assertFalse(StockHold.isWarning(Phase.HELD))
    assertEquals("سفارش در بررسی", StockHold.label(Phase.REVIEW))
  }

  @Test
  fun shortLinesAndNames() {
    val lines = listOf(Line(2, "held", true), Line(1, "held", false), Line(3, "held", true), Line(2, "held", true))
    assertEquals(listOf(2, 3), StockHold.shortLineNos(lines))
    assertEquals(listOf("شیر", "قلمِ 3"), StockHold.shortNames(lines, mapOf(1 to "نان", 2 to "شیر", 3 to " ")))
    assertTrue(StockHold.reviewBody(listOf("شیر")).contains("«شیر»"))
    assertTrue(StockHold.reviewBody(listOf("a", "b", "c", "d", "e")).contains("2 قلمِ دیگر"))
    assertTrue(StockHold.reviewBody(emptyList()).contains("بخشی از کالای این سفارش"))
  }

  @Test
  fun suggestionFollowsStateMachineAndPermissions() {
    val all = OrderStates.STATUSES.flatMap { OrderStates.TRANSITIONS[it].orEmpty() }.distinct()
    assertEquals(StockHold.Suggestion("rejected", "out_of_stock"), StockHold.suggestion("new", all))
    for (s in listOf("accepted", "picking", "packed", "ready_for_pickup")) {
      assertEquals(StockHold.Suggestion("cancelled", "out_of_stock"), StockHold.suggestion(s, all), s)
    }
    //  پس از بیرون رفتنِ کالا یا وضعیتِ پایانی ⇒ پیشنهادی نیست
    for (s in listOf("out_for_delivery", "delivered", "picked_up", "rejected", "cancelled")) {
      assertNull(StockHold.suggestion(s, all), s)
    }
    //  کاربری که دکمه را ندارد ⇒ پیشنهاد هم نه
    assertNull(StockHold.suggestion("new", listOf("accepted")))
    //  «ناموجود» در دلیل‌های سرور هست
    assertTrue("out_of_stock" in OrderStates.REASONS["rejected"].orEmpty())
    assertTrue("out_of_stock" in OrderStates.REASONS["cancelled"].orEmpty())
  }

  @Test
  fun freshNotifiesOnceAndRearmsAfterLeaving() {
    val a = Ref("A", 7)
    val b = Ref("B", 8)
    val first = StockHold.fresh(listOf(a), emptySet())
    assertEquals(listOf(a), first.fresh)
    val again = StockHold.fresh(listOf(a, b), first.keep)
    assertEquals(listOf(b), again.fresh, "A دوباره خبر نمی‌دهد")
    val left = StockHold.fresh(listOf(b), again.keep)
    assertEquals(emptyList(), left.fresh)
    assertEquals(setOf("B"), left.keep, "A از حافظه بیرون رفت")
    val back = StockHold.fresh(listOf(a, b), left.keep)
    assertEquals(listOf(a), back.fresh, "A که دوباره به بررسی رفت، دوباره خبر می‌دهد")
    assertEquals(emptyList(), StockHold.fresh(listOf(Ref("", 1)), emptySet()).fresh)
  }

  @Test
  fun noticeHasNoCustomerData() {
    assertNull(StockHold.notice(emptyList()))
    val (t1, _) = StockHold.notice(listOf(Ref("A", 12)))!!
    assertEquals("سفارش به بررسی رفت — #12", t1)
    val (t2, b2) = StockHold.notice((1..7).map { Ref("o$it", it.toLong()) })!!
    assertEquals("7 سفارش به بررسی رفت", t2)
    assertTrue(b2.endsWith("در «سفارش‌ها» تصمیم بگیرید.") && b2.contains("#5") && !b2.contains("#6"))
  }
}
