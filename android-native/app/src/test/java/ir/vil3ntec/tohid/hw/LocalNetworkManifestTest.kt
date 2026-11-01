package ir.vil3ntec.tohid.hw

import ir.vil3ntec.tohid.core.hw.LocalNetwork
import ir.vil3ntec.tohid.core.i18n.DesktopPhrases
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  HW-13 — قفلِ ارتقای هدف به API 37 (اندروید ۱۷): `ACCESS_LOCAL_NETWORK` در مانیفست **فقط و دقیقاً**
 *  وقتی هدف ۳۷ یا بیشتر است (اندروید برای هدفِ ۳۶ و کمتر می‌گوید نیاید). هر کس `targetSdk` را بالا
 *  ببرد و اجازه را فراموش کند، چاپگرِ شبکه بی‌صدا مهلت می‌خورد — این آزمون پیش از آن سرخ است.
 */
class LocalNetworkManifestTest {

  private val app = DesktopPhrases.repo().resolve("android-native/app")

  private fun targetSdk(): Int {
    val gradle = app.resolve("build.gradle.kts").readText()
    val m = Regex("""(?m)^\s*targetSdk\s*=\s*(\d+)""").findAll(gradle).toList()
    assertEquals("یک و فقط یک targetSdk در build.gradle.kts", 1, m.size)
    return m.single().groupValues[1].toInt()
  }

  private fun declared(): Boolean =
    app.resolve("src/main/AndroidManifest.xml").readText().contains("android:name=\"${LocalNetworkAccess.PERMISSION}\"")

  @Test fun `اجازهٔ شبکهٔ محلی در مانیفست همان است که هدف می‌خواهد`() {
    val t = targetSdk()
    assertEquals(
      "targetSdk = $t: ACCESS_LOCAL_NETWORK " + (if (LocalNetwork.declare(t)) "باید در مانیفست باشد" else "نباید در مانیفست باشد"),
      LocalNetwork.declare(t), declared(),
    )
  }

  @Test fun `با هدفِ امروز اجازه ضمنی است و چاپگرِ شبکه بسته نمی‌شود`() {
    val t = targetSdk()
    if (LocalNetwork.declare(t)) return
    for (device in listOf(24, 35, 36, 37, 40)) {
      assertFalse(LocalNetwork.needsPermission(t, device))
      assertFalse(LocalNetwork.blocked(t, device, granted = false, host = "192.168.1.50"))
    }
  }

  @Test fun `نامِ اجازه همان نامِ اندروید است`() {
    assertEquals("android.permission.ACCESS_LOCAL_NETWORK", LocalNetworkAccess.PERMISSION)
  }

  @Test fun `کامپیوتر جایگزینِ همان API را دارد و فایلِ اندرویدی کنار می‌رود`() {
    val repo = DesktopPhrases.repo()
    val gradle = repo.resolve("desktop/build.gradle.kts").readText()
    assertTrue("hw/LocalNetworkAndroid.kt در فهرستِ replaced ِ کامپیوتر", gradle.contains("\"hw/LocalNetworkAndroid.kt\""))
    val desk = repo.resolve("desktop/src/main/kotlin/ir/vil3ntec/tohid/hw/LocalNetworkDesk.kt").readText()
    val android = app.resolve("src/main/java/ir/vil3ntec/tohid/hw/LocalNetworkAndroid.kt").readText()
    val api = Regex("""(?m)^\s*fun (\w+)\(([^)]*)\): (\w+)""")
    fun sigs(s: String) = api.findAll(s).map { it.groupValues[1] + "(" + it.groupValues[2] + "): " + it.groupValues[3] }.toSet()
    assertEquals(sigs(android), sigs(desk))
  }
}
