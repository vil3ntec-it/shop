package ir.vil3ntec.tohid.core.pay

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** PAY-07 (د۱۸) — آینهٔ وضعیتِ بعدیِ حساب‌پیِ خودکار روی ردیفِ درگاهِ فاکتور (`PayMirror`). */
class PayMirrorTest {

  private fun page(s: String) = PayMirror.parse(Json.parseToJsonElement(s).jsonObject)

  private val gw = PayStatus.Row("hesabpay", "wallet", 145000, PayStatus.CONFIRMED, verification = "gateway")
  private val card = PayStatus.Row("card", "card", 5000, PayStatus.PENDING, verification = PayStatus.MANUAL)
  private val cash = PayStatus.Row("cash", "cash", 1000, PayStatus.CONFIRMED)

  @Test fun serverStateMapsToRowStatus() {
    fun e(st: String, review: Boolean) = PayMirror.Entry("pi1", "s1", "hesabpay", st, review, 1)
    assertEquals(PayStatus.CONFIRMED, PayMirror.statusOf(e("paid", false)))
    assertEquals(PayStatus.NEEDS_REVIEW, PayMirror.statusOf(e("paid", true)))
    assertEquals(PayStatus.REFUNDED, PayMirror.statusOf(e("refunded", false)))
    assertEquals(PayStatus.REFUNDED, PayMirror.statusOf(e("refunded", true)))
    for (st in listOf("pending", "created", "failed", "expired", "cancelled", "")) assertNull(PayMirror.statusOf(e(st, false)))
  }

  @Test fun parsePageSkipsBadRowsAndKeepsCursor() {
    val p = page(
      """{"intents":[{"id":"pi1","refId":"s1","method":"hesabpay","status":"refunded","needsReview":false,"updatedAt":10},
        {"id":"","refId":"s2","method":"hesabpay","status":"paid","updatedAt":11},
        {"id":"pi3","refId":"s3","method":"hesabpay","status":"weird","updatedAt":12},
        {"id":"pi4","refId":"s4","method":"hesabpay","status":"paid","needsReview":true,"updatedAt":13}],
        "cursor":"13~pi4","more":true}""",
    )!!
    assertEquals(listOf("pi1", "pi4"), p.entries.map { it.intentId })
    assertEquals("13~pi4", p.cursor)
    assertEquals(true, p.more)
    assertNull(PayMirror.parse(Json.parseToJsonElement("""{"cursor":"x"}""").jsonObject))
  }

  @Test fun mergeKeepsNewestPerSaleAndMethod() {
    val a = PayMirror.merge(emptyMap(), listOf(PayMirror.Entry("pi1", "s1", "hesabpay", "paid", true, 10)))
    assertEquals(mapOf("s1" to mapOf("hesabpay" to PayStatus.NEEDS_REVIEW)), PayMirror.view(a))
    //  تازه‌تر جای کهنه‌تر؛ کهنه‌ترِ دیررسیده جای تازه‌تر را نمی‌گیرد
    val b = PayMirror.merge(a, listOf(PayMirror.Entry("pi1", "s1", "hesabpay", "refunded", false, 20)))
    val c = PayMirror.merge(b, listOf(PayMirror.Entry("pi1", "s1", "hesabpay", "paid", false, 15)))
    assertEquals(PayStatus.REFUNDED, PayMirror.view(c)["s1"]?.get("hesabpay"))
    //  وضعیتِ بی‌معنا چیزی نمی‌نشاند
    assertEquals(c, PayMirror.merge(c, listOf(PayMirror.Entry("pi9", "s9", "hesabpay", "failed", false, 30))))
  }

  @Test fun overlayTouchesOnlyGatewayRowsOfThatMethod() {
    val rows = listOf(cash, gw, card)
    val st = listOf(PayStatus.CONFIRMED, PayStatus.CONFIRMED, PayStatus.CONFIRMED)
    //  بی آینه ⇒ بایت‌به‌بایت همان
    assertEquals(st, PayMirror.overlay(rows, st, emptyMap()))
    assertEquals(
      listOf(PayStatus.CONFIRMED, PayStatus.NEEDS_REVIEW, PayStatus.CONFIRMED),
      PayMirror.overlay(rows, st, mapOf("hesabpay" to PayStatus.NEEDS_REVIEW, "card" to PayStatus.FAILED, "cash" to PayStatus.FAILED)),
    )
    //  ردیفِ حساب‌پیِ «ساده» (`cashier`) سرورِ مرجع ندارد ⇒ دست نمی‌خورد
    val simple = gw.copy(verification = "cashier")
    assertEquals(listOf(PayStatus.CONFIRMED), PayMirror.overlay(listOf(simple), listOf(PayStatus.CONFIRMED), mapOf("hesabpay" to PayStatus.REFUNDED)))
  }

  @Test fun pathAndStateRoundTrip() {
    assertEquals("/shop/payments/mirror?limit=200", PayMirror.path(""))
    assertEquals("/shop/payments/mirror?after=13~pi4&limit=200", PayMirror.path("13~pi4"))
    assertEquals("/shop/payments/mirror?limit=200", PayMirror.path("13~bad id"), "cursor ِ بدشکل ⇒ از آغاز")
    val m = PayMirror.merge(emptyMap(), listOf(PayMirror.Entry("pi1", "s1", "hesabpay", "refunded", false, 20)))
    val back = PayMirror.decode(PayMirror.encode(PayMirror.State("20~pi1", m)))
    assertEquals("20~pi1", back.cursor)
    assertEquals(m, back.sales)
    assertEquals(PayMirror.State("", emptyMap()), PayMirror.decode("not json"))
  }
}
