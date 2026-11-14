package ir.vil3ntec.tohid.desktop.hub

import ir.vil3ntec.tohid.desktop.offline.DeviceContext
import ir.vil3ntec.tohid.platform.HubLinkV1Android
import ir.vil3ntec.tohid.platform.HubPanel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 *  S29/SYN-09 — سیم‌کشیِ هاب: پیش‌فرض خاموش (پرچم و تنظیمِ دستگاه)، موتورِ v1 فقط پس از دورِ شکست‌خورده و با cursor ِ
 *  دست‌نخورده، برگهٔ جفت‌سازی فقط از صفحهٔ همین کامپیوتر (هیچ درِ شبکه‌ای)، و پیوندِ اولِ «دیده از سرور» در pull.
 */
class HubWiringTest {

  private val root: File = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
    .first { File(it, "android-native").isDirectory && File(it, "desktop").isDirectory }

  private fun src(path: String) = File(root, path).readText(Charsets.UTF_8)

  @Test fun phoneLinkIsOffByDefault() {
    val dir = Files.createTempDirectory("hub-wiring").toFile()
    try {
      val ctx = DeviceContext(dir)
      assertNull("پرچم و تنظیمِ خاموش ⇒ هیچ پیوندی", HubLinkV1Android.of(ctx, "dev-1"))
      assertTrue(!HubLinkV1Android.paired(ctx))
      //  «دیده از سرور» بی جفت شدن هیچ نمی‌نویسد
      HubLinkV1Android.saw(ctx, listOf("op-1"))
      assertEquals(null, ctx.getSharedPreferences("tohid-hub-link", 0).getString("seen", null))
    } finally {
      dir.deleteRecursively()
    }
  }

  @Test fun engineTalksToTheHubOnlyAfterAFailedRoundAndNeverMovesTheCursor() {
    val worker = src("android-native/app/src/main/java/ir/vil3ntec/tohid/sync/v1/SyncV1Worker.kt")
    val run = worker.indexOf("val outcome = engine.runOnce(")
    val hub = worker.indexOf("engine.hubExchange(app, outcome.exceptionOrNull()")
    assertTrue("هاب پس از دورِ سرور", run in 0 until hub)
    val ext = src("android-native/app/src/main/java/ir/vil3ntec/tohid/sync/v1/SyncV1Hub.kt")
    assertTrue(ext.contains("if (error == null || !HubPeerLink.cloudDown(error)) return null"))
    assertTrue(ext.contains("absorbRemote(ops, read, write, cursor = null, keepPending = true)"))
    assertTrue(ext.contains("st.restorePending || st.restoredRebuild || st.recoverArchive"))
    val engine = src("android-native/app/src/main/java/ir/vil3ntec/tohid/sync/v1/SyncV1Engine.kt")
    assertTrue(engine.contains("val got = client.pull(device, state.cursor).also { ir.vil3ntec.tohid.platform.HubLinkV1Android.saw(app"))
    val link = src("android-native/app/src/main/java/ir/vil3ntec/tohid/platform/HubLinkV1Android.kt")
    assertTrue(link.contains("if (!ServerSwitchAndroid.enabled(app)) return null"))
    assertTrue(link.contains("if (!s.getBoolean(SETTING_PEER, false)) return null"))
    assertTrue(link.contains("if (ServerSwitchAndroid.activeProfile(app)?.serverId != sid) return null"))
  }

  @Test fun pairingTicketsComeOnlyFromThisComputersOwnerOrManager() {
    val v1 = src("desktop/src/main/kotlin/ir/vil3ntec/tohid/desktop/hub/HubV1.kt")
    assertEquals("فقط تعریف، هیچ درِ شبکه‌ای", 1, Regex("""newTicket\(""").findAll(v1).count())
    val boot = src("desktop/src/main/kotlin/ir/vil3ntec/tohid/desktop/hub/HubBoot.kt")
    assertTrue(boot.contains("v.newTicket(ShopRole.current(app), by)"))
    assertTrue(boot.contains("if (!HubPair.mayPair(ShopRole.current(app))) return HubPanel.Why.ROLE"))
    assertTrue("هاب پیش‌فرض خاموش", boot.contains(".getBoolean(SETTING, false)) return"))
    assertEquals(HubPanel.State.OFF.running, false)
  }
}
