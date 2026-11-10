package ir.vil3ntec.tohid.ui.courier

import ir.vil3ntec.tohid.core.courier.MissionCard
import ir.vil3ntec.tohid.core.i18n.StrCourier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** S33 — کارتِ مأموریتِ پیک Plus Code را از پینِ مقصد می‌سازد؛ بی پین همان کارتِ پیشین */
class CourierPlusCodeTest {

  @Test fun `پین ⇒ Plus Code ِ ده‌نویسه‌ای؛ بی پین یا پینِ ناقص ⇒ هیچ`() {
    assertEquals("8J6FH645+42", missionPlusCode(MissionCard("a", 1, "assigned", lat = 34.5553, lng = 69.2075)))
    assertNull(missionPlusCode(MissionCard("a", 1, "assigned")))
    assertNull(missionPlusCode(MissionCard("a", 1, "assigned", lat = 34.5553)))
    assertEquals("Plus Code: 8J6FH645+42", StrCourier.plusCode("8J6FH645+42"))
  }

  @Test fun `جزئیاتِ مأموریت کد را فقط در حالِ زنده و زیرِ نشانی نشان می‌دهد`() {
    val repo = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "android-native").isDirectory }
    val src = File(repo, "android-native/app/src/main/java/ir/vil3ntec/tohid/ui/courier/CourierDetail.kt").readText()
    val live = src.indexOf("if (live) {")
    val code = src.indexOf("missionPlusCode(c)?.let")
    val after = src.indexOf("} else {", live)
    assertTrue("Plus Code بیرون از شاخهٔ زنده", live in 0 until code && code < after)
  }

  @Test fun `کارتِ فهرستِ مأموریت‌ها هم کد را فقط در حالِ زنده و پس از خطوطِ نشانی نشان می‌دهد`() {
    val repo = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "android-native").isDirectory }
    val src = File(repo, "android-native/app/src/main/java/ir/vil3ntec/tohid/ui/courier/CourierMissionCard.kt").readText()
    val lines = src.indexOf("landmarkLines(c).forEachIndexed")
    val code = src.indexOf("if (live) missionPlusCode(c)?.let")
    assertTrue("کارتِ فهرست Plus Code ندارد یا پیش از نشانی است", lines >= 0 && code > lines)
    assertTrue(src.substring(code, src.indexOf('}', code)).contains("StrCourier.plusCode(it)"))
  }
}
