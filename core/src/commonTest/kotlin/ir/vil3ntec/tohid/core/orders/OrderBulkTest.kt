package ir.vil3ntec.tohid.core.orders

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** ORD-19: انتخابِ چندتایی و گذارِ گروهی همان قاعده‌های `lib/order-bulk.js` و تحویلِ تک‌تک */
class OrderBulkTest {

  @Test
  fun selectionCappedAtServerLimit() {
    var s = emptySet<String>()
    for (i in 1..60) s = OrderBulk.toggle(s, "o$i")
    assertEquals(OrderBulk.MAX, s.size, "بیش از ۵۰ نه")
    assertTrue("o51" !in s)
    s = OrderBulk.toggle(s, "o1")
    assertEquals(49, s.size, "دوباره ⇒ بیرون")
    assertEquals(setOf("o2", "o9", "x"), OrderBulk.all(setOf("o2"), listOf("o9", "x")))
    assertEquals(OrderBulk.MAX, OrderBulk.all(emptySet(), (1..80).map { "v$it" }).size)
  }

  @Test
  fun commonTargetsOnlyForOneStatusAndSafeMoves() {
    val newPickup = "new" to listOf("accepted", "rejected")
    assertEquals(listOf("accepted", "rejected"), OrderBulk.targets(listOf(newPickup, newPickup)))
    assertEquals(emptyList(), OrderBulk.targets(listOf(newPickup, "accepted" to listOf("picking", "cancelled"))), "دو وضعیت ⇒ هیچ")
    assertEquals(emptyList(), OrderBulk.targets(emptyList()))
    //  بسته‌بندی‌شده: حضوری «آماده»، ارسالی «در مسیر» (تحویل، تک‌تک) ⇒ فقط لغوِ مشترک
    val packedPickup = "packed" to listOf("ready_for_pickup", "cancelled")
    val packedDelivery = "packed" to listOf("out_for_delivery", "cancelled")
    assertEquals(listOf("ready_for_pickup", "cancelled"), OrderBulk.targets(listOf(packedPickup)))
    assertEquals(listOf("cancelled"), OrderBulk.targets(listOf(packedPickup, packedDelivery)))
    //  تحویل، برگشت و پول هرگز گروهی نیستند
    for (t in listOf("picked_up", "out_for_delivery", "delivered", "refunded_full", "refunded_partial", "auto_rejected", "restocked")) {
      assertTrue(t !in OrderBulk.TARGETS, t)
      assertEquals(emptyList(), OrderBulk.targets(listOf("x" to listOf(t))), t)
    }
  }

  @Test
  fun resultsSummarizedAndFailedStaySelected() {
    val r = listOf(
      OrderBulk.Result("a", ok = true, unchanged = false, orderNo = 1, code = ""),
      OrderBulk.Result("b", ok = true, unchanged = true, orderNo = 2, code = ""),
      OrderBulk.Result("c", ok = false, unchanged = false, orderNo = 3, code = "status_changed"),
    )
    val s = OrderBulk.summarize(r)
    assertEquals(1, s.changed)
    assertEquals(1, s.unchanged)
    assertEquals(listOf("c"), s.failed.map { it.id })
    assertEquals(setOf("c", "d"), OrderBulk.remaining(setOf("a", "b", "c", "d"), r))
  }
}
