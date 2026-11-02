package ir.vil3ntec.tohid.core.courier

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MissionNavTest {

  //  همان قالبِ `courier-map.navigation` ِ سرور
  private val geo = "geo:34.528312,69.172451?q=34.528312,69.172451"
  private val google = "https://www.google.com/maps/dir/?api=1&destination=34.528312,69.172451"
  private val osmand = "https://osmand.net/map?pin=34.528312,69.172451#17/34.528312/69.172451"

  @Test
  fun serverLinksAreKeptInPhoneOsmandGoogleOrder() {
    val nav = MissionNav.of(geo, google, osmand)
    assertEquals(listOf(geo, osmand, google), nav?.links)
    assertEquals(listOf(osmand), MissionNav.of(null, "", osmand)?.links)
    assertEquals(listOf("geo:-3.5,-60?q=-3.5,-60"), MissionNav.of("geo:-3.5,-60?q=-3.5,-60", null, null)?.links)
  }

  @Test
  fun foreignOrLabelledLinksAreDroppedAndNothingLeftIsNull() {
    for (bad in listOf(
      "geo:34.5,69.1?q=34.5,69.1(خانهٔ مشتری)", "javascript:alert(1)", "intent://x#Intent;end",
      "https://www.google.com.evil.af/maps/dir/?api=1&destination=34.5,69.1",
      "http://www.google.com/maps/dir/?api=1&destination=34.5,69.1",
      "https://osmand.net/map?pin=34.5,69.1#17/34.5/69.1&name=0791112233", "",
    )) assertNull(MissionNav.of(bad, bad, bad), bad)
    assertNull(MissionNav.of(null, null, null))
  }

  @Test
  fun finishedMissionLosesNavigationLikeTheAddress() {
    val live = MissionCard(id = "A", orderNo = 1, status = "picked_up", lat = 34.5, lng = 69.1, navigation = MissionNav.of(geo, google, osmand))
    assertEquals(live.navigation, MissionView.forStorage(listOf(live)).single().navigation)
    assertNull(MissionView.redact(live).navigation)
    assertNull(MissionView.forStorage(listOf(live.copy(status = "delivered"))).single().navigation)
  }
}
