package ir.vil3ntec.tohid.core.report

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  RPT-14 — صفحهٔ «گزارشِ فروشِ اینترنتی» ِ برنامهٔ مدیریت همان عددهای سرور را می‌خواند. نمونه همان پاسخِ قفل‌شدهٔ
 *  `server/test/order-online-report.test.js` است (شش سفارش، دو ازدست‌رفته، یک ناموفقِ تحویل).
 */
class OnlineReportTest {

  private fun json(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject
  private val s = 1000L

  private val sample = json(
    """{"enabled":true,"window":{"from":1000,"to":87401000},
      "funnel":{"placed":6,"accepted":4,"packed":3,"handedOver":3,"completed":3},
      "lost":{"rejected":1,"auto_rejected":0,"cancelled_by_customer":0,"cancelled":1,"returned_to_store":0,"deliveryFailed":1},
      "rates":{"acceptBp":6667,"completeBp":5000,"lostBp":3333,"deliveryFailBp":5000},
      "times":{"accept":{"n":4,"medianMs":60000,"p90Ms":240000},"prep":{"n":3,"medianMs":370000,"p90Ms":540000},
               "delivery":{"n":2,"medianMs":1100000,"p90Ms":1200000},"total":{"n":3,"medianMs":1900000,"p90Ms":2000000}},
      "completedTotalMinor":1234500,"currency":"AFN",
      "zeroResultSearches":[{"q":"زعفران","n":2,"lastAt":5},{"q":"کیک","n":1,"lastAt":6}],
      "visits":null}""",
  )

  @Test
  fun readsTheServerNumbersAsTheyAre() {
    val v = OnlineReport.of(sample)!!
    assertEquals(listOf(6, 4, 3, 3, 3), OnlineReport.STEPS.map { v.funnel[it] })
    assertEquals(listOf(1, 0, 0, 1, 0, 1), (OnlineReport.LOST + OnlineReport.DELIVERY_FAILED).map { v.lost[it] })
    assertEquals(listOf(6667L, 5000L, 3333L, 5000L), listOf(v.acceptBp, v.completeBp, v.lostBp, v.deliveryFailBp))
    assertEquals(OnlineReport.Time(4, 60 * s, 240 * s), v.times["accept"])
    assertEquals(OnlineReport.Time(3, 1900 * s, 2000 * s), v.times["total"])
    assertEquals(1_234_500L, v.completedTotalMinor)
    assertEquals(listOf(OnlineReport.Miss("زعفران", 2), OnlineReport.Miss("کیک", 1)), v.misses)
    assertNull(v.visits, "بازدید پیش از شمارشِ سرور عدد نیست، نه صفر")
    assertEquals(1000L to 87_401_000L, v.from to v.to)
    assertEquals(false, v.empty)
  }

  @Test
  fun topOfFunnelIsTheServerWebCountAndNullBeforeCounting() {
    val v = OnlineReport.of(
      json(
        """{"enabled":true,"funnel":{"placed":1},"visits":3,
          "top":{"web":true,"visits":3,"productViews":2,"cartAdds":1,"viewBp":6667,"cartBp":3333,"since":86400000,"capped":true}}""",
      ),
    )!!
    assertEquals(3L, v.visits)
    assertEquals(OnlineReport.Top(3, 2, 1, 6667L, 3333L, 86_400_000L, true), v.top)
    assertNull(OnlineReport.of(sample)!!.top, "پیش از شمارش ⇒ null، نه صفر")
    val odd = OnlineReport.of(json("""{"enabled":true,"funnel":{},"top":{"visits":"3","cartAdds":-1,"capped":"true"}}"""))!!
    assertEquals(OnlineReport.Top(null, null, null, null, null, null, false), odd.top, "بدشکل ⇒ null، نه صفر")
  }

  @Test
  fun stepRatesUseTheServerRounding() {
    val v = OnlineReport.of(sample)!!
    //  همان `bp` ِ سرور: ۴/۶ ⇒ ۶۶۶۷، ۳/۶ ⇒ ۵۰۰۰
    assertEquals(v.acceptBp, OnlineReport.stepBp(v, "accepted"))
    assertEquals(v.completeBp, OnlineReport.stepBp(v, "completed"))
    assertEquals(5000L, OnlineReport.stepBp(v, "packed"))
    assertEquals(10_000L, OnlineReport.stepBp(v, "placed"))
  }

  @Test
  fun emptyAndMissingStayNullNotZero() {
    val v = OnlineReport.of(
      json(
        """{"enabled":true,"funnel":{"placed":0,"accepted":0,"packed":0,"handedOver":0,"completed":0},
          "rates":{"acceptBp":null},"times":{"accept":{"n":0,"medianMs":null,"p90Ms":null}},
          "zeroResultSearches":[{"q":"  ","n":3},{"q":"x"},{"q":7,"n":1}]}""",
      ),
    )!!
    assertTrue(v.empty)
    assertNull(v.acceptBp)
    assertNull(OnlineReport.stepBp(v, "accepted"), "مخرجِ صفر ⇒ null")
    assertEquals(OnlineReport.Time(0, null, null), v.times["prep"])
    assertNull(v.lost["rejected"])
    assertNull(v.completedTotalMinor)
    assertEquals(emptyList(), v.misses, "عبارتِ خالی، بی شمار یا غیرِ متن کنار می‌رود")
  }

  @Test
  fun offOrMalformedIsNoReport() {
    assertNull(OnlineReport.of(json("""{"enabled":false}""")))
    assertNull(OnlineReport.of(json("""{"funnel":{"placed":1}}""")))
    assertNull(OnlineReport.of(json("""{"enabled":true}""")))
    assertNull(OnlineReport.of(json("""{"enabled":"true","funnel":{}}""")))
  }

  @Test
  fun windowIsClampedToTheServerLimit() {
    val now = 100L * OnlineReport.DAY_MS
    assertEquals("/shop/orders/online-report?from=${now - OnlineReport.DAY_MS}&to=$now", OnlineReport.path(1, now))
    assertEquals(OnlineReport.path(92, now), OnlineReport.path(400, now))
    assertEquals(OnlineReport.path(1, now), OnlineReport.path(0, now))
    assertEquals(listOf(1, 7, 30, 92), OnlineReport.SPANS)
  }

  @Test
  fun durationsRoundToTheNearestMinute() {
    assertEquals(0L to 1L, OnlineReport.hoursMinutes(60 * s))
    assertEquals(0L to 6L, OnlineReport.hoursMinutes(370 * s))
    assertEquals(0L to 18L, OnlineReport.hoursMinutes(1100 * s))
    assertEquals(0L to 32L, OnlineReport.hoursMinutes(1900 * s))
    assertEquals(2L to 5L, OnlineReport.hoursMinutes(7_500 * s))
    assertNull(OnlineReport.hoursMinutes(null))
    assertNull(OnlineReport.hoursMinutes(-1))
  }
}
