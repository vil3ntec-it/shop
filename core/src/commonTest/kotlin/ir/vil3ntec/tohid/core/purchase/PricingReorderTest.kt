package ir.vil3ntec.tohid.core.purchase

import ir.vil3ntec.tohid.core.purchase.ReorderCore.Confidence
import ir.vil3ntec.tohid.core.purchase.ReorderCore.Delivery
import ir.vil3ntec.tohid.core.purchase.ReorderCore.Input
import ir.vil3ntec.tohid.core.purchase.ReorderCore.Reason
import ir.vil3ntec.tohid.core.purchase.SupplierPricing.Buy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** PUR-09 (تاریخچهٔ قیمتِ هر تأمین‌کننده) و PUR-10 (زمانِ تأمین در پیشنهادِ سفارش) — عددها دستی */
class PricingReorderTest {

  private val buys = listOf(
    Buy("s1", "p1", "2026-09-01", 10, 10_000, 10_000),
    Buy("s1", "p1", "2026-10-01", 20, 5_000, 11_000),
    Buy("s2", "p1", "2026-10-05", 30, 2_000, 10_500),
    Buy("s1", "p1", "2026-10-20", 40, 3_000, 9_900),
    //  سندِ کسر (برگشت، مقدارِ منفی) قیمتِ خرید نیست
    Buy("s1", "p1", "2026-10-21", 50, -1_000, 9_900),
    Buy("s1", "p2", "2026-10-21", 60, 1_000, 500),
    //  دو خریدِ یک روز: ترتیب با زمانِ ثبت
    Buy("s2", "p1", "2026-10-25", 80, 1_000, 10_000),
    Buy("s2", "p1", "2026-10-25", 70, 1_000, 10_200),
  )

  @Test fun historyNewestFirstWithChangeAgainstSameSupplier() {
    val h = SupplierPricing.history(buys, "p1", "s1")
    assertEquals(listOf(9_900L, 11_000L, 10_000L), h.map { it.priceMinor })
    assertEquals(listOf(-1_100L, 1_000L, null), h.map { it.changeMinor })
    //  ‎−۱۱۰۰/۱۱۰۰۰ = −۱۰٪ ⇒ −۱۰۰۰ ده‌هزارم؛ ‎+۱۰۰۰/۱۰۰۰۰ = +۱۰٪
    assertEquals(listOf(-1_000L, 1_000L, null), h.map { it.changeBp })

    val all = SupplierPricing.history(buys, "p1")
    assertEquals(listOf("s2", "s2", "s1", "s2", "s1", "s1"), all.map { it.supplierId })
    //  تغییرِ s2 نسبت به خریدِ پیشینِ خودِ s2، نه s1
    assertEquals(listOf(-200L, -300L, -1_100L, null, 1_000L, null), all.map { it.changeMinor })
  }

  @Test fun lastAndCheapestSupplier() {
    assertEquals(9_900L, SupplierPricing.last(buys, "p1", "s1")?.priceMinor)
    assertEquals(10_000L, SupplierPricing.last(buys, "p1", "s2")?.priceMinor)
    assertNull(SupplierPricing.last(buys, "p1", "s3"))
    assertEquals(listOf("s1" to 9_900L, "s2" to 10_000L), SupplierPricing.bySupplier(buys, "p1").map { it.supplierId to it.priceMinor })
  }

  @Test fun percentNullWhenPreviousIsFreeOrJumpIsHuge() {
    val h = SupplierPricing.history(
      listOf(Buy("s", "p", "2026-01-01", 1, 1_000, 0), Buy("s", "p", "2026-01-02", 2, 1_000, 500), Buy("s", "p", "2026-01-03", 3, 1_000, PurchaseOrderCore.MAX_SAFE)),
      "p",
    )
    assertEquals(listOf(PurchaseOrderCore.MAX_SAFE - 500, 500L, null), h.map { it.changeMinor })
    assertEquals(listOf(null, null, null), h.map { it.changeBp }, "پیشینِ صفر و جهشِ بیش از سقف ⇒ بی درصد")
  }

  @Test fun leadTimeIsMedianOfSentToFirstReceiptRoundedUp() {
    val d = ReorderCore.DAY_MS
    assertEquals(ReorderCore.Lead(null, 0), ReorderCore.leadTime(emptyList()))
    //  ۲ ساعت ⇒ ۱ روز؛ ۳ روزِ تمام ⇒ ۳؛ ۵ روز و ۱ میلی‌ثانیه ⇒ ۶؛ رسید پیش از فرستادن کنار
    val l = ReorderCore.leadTime(listOf(
      Delivery(1_000, 1_000 + 2 * 3_600_000), Delivery(2_000, 2_000 + 3 * d), Delivery(3_000, 3_000 + 5 * d + 1),
      Delivery(4_000, 3_999), Delivery(0, 10),
    ))
    assertEquals(ReorderCore.Lead(3, 3), l)
    assertEquals(1, ReorderCore.leadTime(listOf(Delivery(1, 1 + 2 * 3_600_000))).days, "چند ساعت ⇒ یک روز")
    assertEquals(6, ReorderCore.leadTime(listOf(Delivery(1, 2 + 5 * d))).days, "پنج روز و اندی ⇒ شش")
    assertEquals(0, ReorderCore.leadTime(listOf(Delivery(5, 5))).days, "همان لحظه ⇒ صفر")
    //  زوج ⇒ میانهٔ بالا
    assertEquals(4, ReorderCore.leadTime(listOf(Delivery(1, 1 + 2 * d), Delivery(2, 2 + 4 * d))).days)
    //  فقط ۱۰ سفارشِ تازه‌تر
    val old = (1..10).map { Delivery(it.toLong(), it + 30 * d) }
    val fresh = (11..20).map { Delivery(it * 10L, it * 10L + 2 * d) }
    assertEquals(ReorderCore.Lead(2, 10), ReorderCore.leadTime(old + fresh))
  }

  @Test fun suggestUsesSalesLeadTimeAndOnOrder() {
    //  ۳۰ روز ۶۰ فروش ⇒ روزی ۲؛ زمانِ تأمین ۵ ⇒ نقطهٔ سفارش ۱۰ + حدِ کم ۴ = ۱۴
    val base = Input(onHandMilli = 12_000, onOrderMilli = 0, minStockMilli = 4_000, soldMilli = 60_000, leadDays = 5)
    val s = ReorderCore.suggest(base)
    assertEquals(Reason.REORDER_POINT, s.reason)
    assertEquals(Confidence.HIGH, s.confidence)
    assertEquals(2_000L, s.dailyMilli)
    assertEquals(14_000L, s.reorderPointMilli)
    //  هدف = روزی ۲ × (۵ + ۱۴) + ۴ = ۴۲ ⇒ ۴۲ − ۱۲ = ۳۰
    assertEquals(30_000L, s.qtyMilli)
    //  در راه حساب می‌شود: ۸ تای سفارشِ باز ⇒ جایگاه ۲۰ > ۱۴ ⇒ کافی
    assertEquals(Reason.ENOUGH, ReorderCore.suggest(base.copy(onOrderMilli = 8_000)).reason)
    assertEquals(0L, ReorderCore.suggest(base.copy(onOrderMilli = 8_000)).qtyMilli)
    //  زمانِ تأمینِ بلندتر ⇒ زودتر و بیشتر: ۱۰ روز ⇒ نقطه ۲۴، هدف ۵۲ ⇒ ۵۲ − ۲۰ = ۳۲
    val longer = ReorderCore.suggest(base.copy(onOrderMilli = 8_000, leadDays = 10))
    assertEquals(Reason.REORDER_POINT, longer.reason)
    assertEquals(32_000L, longer.qtyMilli)
    //  نسنجیده ⇒ ۷ روزِ پیش‌فرض و اطمینانِ متوسط
    val unknown = ReorderCore.suggest(base.copy(leadDays = null))
    assertEquals(ReorderCore.DEFAULT_LEAD_DAYS, unknown.leadDaysUsed)
    assertEquals(Confidence.MEDIUM, unknown.confidence)
  }

  /** همان عددهای `PurchaseOrderBookTest` ِ برنامه (فروشِ ۵۵ در ۳۰ روز، زمانِ تأمینِ ۵، حدِ کمِ ۵) */
  @Test fun suggestMatchesAppPlannerNumbers() {
    val i = Input(onHandMilli = 2_000, onOrderMilli = 13_000, minStockMilli = 5_000, soldMilli = 55_000, leadDays = 5)
    val enough = ReorderCore.suggest(i)
    assertEquals(14_167L, enough.reorderPointMilli, "۹٫۱۶۷ + ۵")
    assertEquals(Reason.ENOUGH, enough.reason)
    val s = ReorderCore.suggest(i.copy(onOrderMilli = 0))
    assertEquals(38_000L, s.qtyMilli, "۳۴٫۸۳۳ + ۵ − ۲ = ۳۷٫۸۳۳ ⇒ ۳۸")
    assertEquals(1_833L, s.dailyMilli)
  }

  @Test fun suggestWithoutSalesFallsBackToMinStockAndRoundsUp() {
    val none = Input(onHandMilli = 1_500, onOrderMilli = 0, minStockMilli = 4_000, soldMilli = 0, leadDays = null)
    val s = ReorderCore.suggest(none)
    assertEquals(Reason.BELOW_MIN, s.reason)
    assertEquals(Confidence.LOW, s.confidence)
    assertEquals(3_000L, s.qtyMilli, "۲٫۵ تا حدِ کم ⇒ ۳ واحدِ کامل")
    assertEquals(2_500L, ReorderCore.suggest(none.copy(wholeUnits = false)).qtyMilli, "وزنی ⇒ هزارم")
    assertEquals(Reason.ENOUGH, ReorderCore.suggest(none.copy(onHandMilli = 4_000)).reason, "برابرِ حدِ کم کافی است")
    assertEquals(Reason.ENOUGH, ReorderCore.suggest(none.copy(minStockMilli = 0, onHandMilli = -2_000)).reason, "بی فروش و بی حدِ کم ⇒ پیشنهاد نیست")
    //  موجودیِ منفی (فروشِ آفلاین) هم جایگاه است
    assertEquals(6_000L, ReorderCore.suggest(none.copy(onHandMilli = -2_000)).qtyMilli)
  }

  @Test fun suggestRejectsBadWindow() {
    assertFailsWith<IllegalArgumentException> { ReorderCore.suggest(Input(0, 0, 0, 1, windowDays = 0, leadDays = 1)) }
    assertFailsWith<IllegalArgumentException> { ReorderCore.suggest(Input(0, 0, 0, 1, leadDays = -1)) }
  }
}
