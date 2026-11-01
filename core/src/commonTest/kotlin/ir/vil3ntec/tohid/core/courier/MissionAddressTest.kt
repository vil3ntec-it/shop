package ir.vil3ntec.tohid.core.courier

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  کارتِ «اول نشانه» (DLV-02) و نشانیِ اصلاح‌شده (DLV-09) — همان `courierView`ِ سرور.
 *  نشانه، ناحیه و رنگِ دروازه مثلِ باقیِ نشانی پس از پایانِ مأموریت روی گوشی نمی‌مانند.
 */
class MissionAddressTest {

  private fun card(status: String = "moving") = MissionCard(
    id = "A1", orderNo = 7, status = status,
    landmark = "پشتِ مسجدِ جامع", addressText = "کوچهٔ سوم", lat = 34.5, lng = 69.1,
    customerName = "احمد", phoneMasked = "0791•••567",
    area = "کارتهٔ چهار", gateColor = "سبز", addressCorrectedAt = 1_700_000_000_000,
  )

  @Test
  fun landmarkComesFirstThenAreaAndGateThenText() {
    assertEquals(listOf("پشتِ مسجدِ جامع", "ناحیه: کارتهٔ چهار · دروازهٔ سبز", "کوچهٔ سوم"), MissionView.landmarkFirst(card()))
  }

  @Test
  fun withoutLandmarkTheTextLeadsAndIsNotRepeated() {
    assertEquals(listOf("کوچهٔ سوم", "دروازهٔ سبز"), MissionView.landmarkFirst(card().copy(landmark = " ", area = "")))
    assertEquals(listOf("پشتِ مسجدِ جامع", "ناحیه: کارتهٔ چهار"), MissionView.landmarkFirst(card().copy(addressText = "", gateColor = "")))
    assertEquals(emptyList(), MissionView.landmarkFirst(card().copy(landmark = "", addressText = "", area = "", gateColor = "")))
  }

  @Test
  fun finishedMissionKeepsNoAreaGateOrCorrection() {
    val gone = MissionView.forDisplay(card("delivered"), QueueState())
    assertEquals("", gone.area)
    assertEquals("", gone.gateColor)
    assertNull(gone.addressCorrectedAt)
    assertEquals(emptyList(), MissionView.landmarkFirst(gone))
    val stored = MissionView.forStorage(listOf(card("returned"), card("failed"))).associateBy { it.status }
    assertEquals("", stored.getValue("returned").area)
    assertEquals("سبز", stored.getValue("failed").gateColor)
    assertEquals(1_700_000_000_000, stored.getValue("failed").addressCorrectedAt)
  }

  @Test
  fun pendingFinishInTheQueueAlreadyHidesTheAddress() {
    val (q, _) = CourierQueue.enqueue(QueueState(), "ev-00000001", "A1", "delivered", 1L, from = "arrived")
    val shown = MissionView.forDisplay(card("arrived"), q)
    assertEquals("delivered", shown.status)
    assertTrue(shown.area.isEmpty() && shown.gateColor.isEmpty() && shown.landmark.isEmpty())
  }
}
