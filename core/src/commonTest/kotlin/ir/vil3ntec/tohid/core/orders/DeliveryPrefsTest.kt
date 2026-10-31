package ir.vil3ntec.tohid.core.orders

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  دستورِ تحویل (CHK-14، CHK-24) — همان مرزهای `server/test/order-delivery-prefs.test.js`.
 */
class DeliveryPrefsTest {
  private val total = 1_240_000L

  @Test
  fun tenderBoundsMatchServer() {
    assertNull(DeliveryPrefs.tenderError(null, total))
    assertNull(DeliveryPrefs.tenderError(total, total))
    assertNull(DeliveryPrefs.tenderError(1_300_000L, total))
    //  پول خردِ ۹۹۹ افغانی آری، ۱۰۰۰ نه
    assertNull(DeliveryPrefs.tenderError(total + 99_900L, total))
    assertTrue(DeliveryPrefs.tenderError(total + 100_000L, total)!!.contains("۱۰۰۰"))
    assertTrue(DeliveryPrefs.tenderError(1_200_000L, total)!!.contains("کمتر"))
  }

  @Test
  fun tenderFieldAcceptsWholeAfghaniInPersianDigits() {
    assertEquals(DeliveryPrefs.Tender.None, DeliveryPrefs.parseTender("  "))
    assertEquals(DeliveryPrefs.Tender.Ok(100_000L), DeliveryPrefs.parseTender("۱۰۰۰"))
    assertEquals(DeliveryPrefs.Tender.Ok(1_300_000L), DeliveryPrefs.parseTender("13,000"))
    assertTrue(DeliveryPrefs.parseTender("12.5") is DeliveryPrefs.Tender.Error)
    assertTrue(DeliveryPrefs.parseTender("abc") is DeliveryPrefs.Tender.Error)
    assertTrue(DeliveryPrefs.parseTender("0") is DeliveryPrefs.Tender.Error)
  }

  @Test
  fun changeAndCardLine() {
    assertEquals(60_000L, DeliveryPrefs.changeOf(1_300_000L, total))
    assertNull(DeliveryPrefs.changeOf(1_000_000L, total))
    assertEquals("مشتری با 13,000 افغانی می‌پردازد — پول خرد: 600 افغانی", DeliveryPrefs.tenderLine(1_300_000L, total))
    assertEquals("مشتری با 12,400 افغانی می‌پردازد — پول خرد لازم نیست", DeliveryPrefs.tenderLine(total, total))
    assertTrue(DeliveryPrefs.tenderLine(1_000_000L, total).contains("تماس"))
  }

  @Test
  fun manualFormSendsTenderOnlyForDeliveryAndOnlyWhenWritten() {
    val base = OrderDraft.Form(
      name = "احمد", phone = "0791234567", fulfillment = "delivery", landmark = "مسجد",
      lines = listOf(OrderDraft.LineInput("برنج", "1", "120")),
    )
    fun built(f: OrderDraft.Form) = OrderDraft.build(f)
    assertNull((built(base) as OrderDraft.Result.Ok).body.cashTenderMinor)
    assertEquals(100_000L, (built(base.copy(cashTender = "۱۰۰۰")) as OrderDraft.Result.Ok).body.cashTenderMinor)
    assertTrue(built(base.copy(cashTender = "99.5")) is OrderDraft.Result.Error)
    //  حضوری: کادر نادیده (پیک در کار نیست)
    assertNull((built(base.copy(fulfillment = "pickup", cashTender = "1000")) as OrderDraft.Result.Ok).body.cashTenderMinor)
  }

  @Test
  fun editableOnlyBeforeDispatchAndOnlyForDelivery() {
    for (s in listOf("new", "accepted", "picking", "packed")) assertTrue(DeliveryPrefs.editable(s, "delivery"), s)
    for (s in listOf("out_for_delivery", "delivered", "delivery_failed", "retry_scheduled", "cancelled")) {
      assertFalse(DeliveryPrefs.editable(s, "delivery"), s)
    }
    assertFalse(DeliveryPrefs.editable("accepted", "pickup"))
  }
}
