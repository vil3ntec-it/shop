package ir.vil3ntec.tohid.ui.v3.account.subscription

import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrHome
import ir.vil3ntec.tohid.ui.v3.account.AccountLinks
import ir.vil3ntec.tohid.ui.v3.settings.SettingsPaths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  O-09 / SUB-09 — صاحبِ دکان در برنامه بن‌بست نمی‌بیند (حالتِ Compose، پس متن سنجیده می‌شود). منطق در
 *  `core/.../account/DeviceCap.kt` (`DeviceCapTest`)؛ قراردادِ سرور در `server/test/sec-sub-device-cap.test.js`.
 *    • هشدارِ مجوز ⇒ `SyncStore.deviceCapOver` ⇒ نوارِ پوسته در همهٔ صفحه‌ها (صندوق هم) با راهِ «دستگاه‌های وصل»؛
 *    • `device_limit` ⇒ صفحهٔ قفل به «دستگاه‌های وصل» می‌برد، نه به «قیمت‌ها».
 */
class DeviceCapWiringTest {

  private fun src(rel: String): String =
    listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, rel) }.firstOrNull { it.isFile }?.readText() ?: error("$rel پیدا نشد (cwd=${File(".").absoluteFile})")

  private val sessions = "SettingsPaths.section(\"دستگاه‌های وارد شده\")"

  @Test fun `هشدارِ مجوز نگه داشته و با ردِ سرور پاک می‌شود`() {
    val s = src("ir/vil3ntec/tohid/sync/Syncer.kt")
    assertEquals(1, Regex(Regex.escape("state.deviceCapOver = DeviceCap.encode(DeviceCap.overOf(body))")).findAll(s).count())
    assertTrue(s.indexOf("state.deviceCapOver = DeviceCap.encode") in s.indexOf("state.serverDenied = null") until s.indexOf("LicenseGuard.changed()"))
    assertTrue("ردِ سرور هشدارِ کهنه را برمی‌دارد", s.contains("state.serverDenied = reason\n        state.deviceCapOver = null"))
  }

  @Test fun `نوارِ هشدار در پوسته است و به دستگاه‌های وصل می‌برد`() {
    val shell = src("ir/vil3ntec/tohid/ui/v3/shell/ShellNotice.kt")
    assertEquals(1, Regex(Regex.escape("DeviceCapNotice()")).findAll(shell).count())
    //  شرطِ صفحهٔ خانه/کامپیوتر ندارد: صندوق هم می‌بیند
    val line = shell.lines().first { it.contains("DeviceCapNotice()") }
    assertFalse(line.contains("if ("))
    val n = src("ir/vil3ntec/tohid/ui/v3/shell/ShellNotices.kt")
    assertTrue(n.contains("val over = rememberDeviceCapOver() ?: return"))
    assertTrue(src("ir/vil3ntec/tohid/ui/v3/shell/DeviceCapAndroid.kt").contains("DeviceCap.decode(SyncStore(context).deviceCapOver)"))
    assertTrue(n.contains("val devices = $sessions"))
    assertTrue(n.contains("onAction = { openRoute(nav, devices) }"))
  }

  @Test fun `صفحهٔ قفل با device_limit به خریدِ اشتراک نمی‌فرستد`() {
    val gate = src("ir/vil3ntec/tohid/ui/screens/Vip.kt")
    assertTrue(gate.contains("LockedFeature(label, signedIn, state.serverDenied)"))
    val locked = src("ir/vil3ntec/tohid/ui/v3/account/subscription/LockedFeature.kt")
    assertTrue(locked.contains("val next = DeviceCap.lockedNext(signedIn, serverDenied)"))
    assertTrue(locked.contains("DeviceCap.Next.DEVICES -> AccountLinks.SESSIONS"))
    //  پیوند با مسیر (نه import)، ولی همان صفحه: اگر «دستگاه‌های وارد شده» جابه‌جا شود این‌جا سرخ می‌شود
    assertEquals(SettingsPaths.section("دستگاه‌های وارد شده"), AccountLinks.SESSIONS)
    assertTrue(locked.contains("DeviceCap.Next.DEVICES -> StrAccount.lockedDeviceFull(label)"))
  }

  @Test fun `متنِ نوار عدد را نگه می‌دارد و در دری جملهٔ کامل است`() {
    try {
      for (lang in listOf(Lang.FA, Lang.PRS, Lang.EN)) {
        I18n.source = { lang }
        val t = StrHome.deviceCapOver(3, 2)
        assertTrue("$lang: $t", t.contains("3") && t.contains("2"))
      }
    } finally {
      I18n.source = { Lang.FA }
    }
  }
}
