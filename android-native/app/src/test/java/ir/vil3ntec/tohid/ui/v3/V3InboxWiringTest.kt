package ir.vil3ntec.tohid.ui.v3

import ir.vil3ntec.tohid.ui.v3.shell.ShellPaths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  SYN-07 / S28 — سیم‌کشیِ صندوقِ تعارضِ v3 در پوستهٔ گوشیِ نسخهٔ ۳ و کامپیوتر (مثلِ `ConflictInboxWiringTest`، با متنِ همان
 *  پرونده‌ها) و سه لایهٔ «پیش‌فرض خاموش». منطق: `V3InboxTest`/`V3InboxOutboxTest` (core)؛ صفحه: `V3InboxDesktopUiTest`.
 */
class V3InboxWiringTest {

  private fun src(rel: String): String =
    listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, "ir/vil3ntec/tohid/$rel") }.firstOrNull { it.isFile }?.readText()
      ?: error("$rel پیدا نشد (cwd=${File(".").absoluteFile})")

  @Test
  fun `نوار و صفحه در پوسته، و صف پس از دورِ همگام‌سازی`() {
    assertTrue(src("ui/v3/shell/ShellNotice.kt").contains("V3InboxNotice()"))
    val routes = src("ui/v3/shell/ShellRoutes.kt")
    assertTrue(routes.contains("V3Route(ShellPaths.SYNC_CONFLICTS, RouteKind.PAGE"))
    assertTrue(routes.contains("V3InboxRoute()"))
    assertEquals("sync-conflicts", ShellPaths.SYNC_CONFLICTS)
    assertTrue(src("sync/v1/SyncV1Worker.kt").contains("V3InboxOutboxAndroid.flush(applicationContext)"))
  }

  @Test
  fun `پیش‌فرض خاموش ـ دستگاهِ v1 و نقشِ دیگر هیچ نمی‌پرسند و نوار فقط با inbox ِ سرور`() {
    val gate = src("data/V3InboxOutboxAndroid.kt")
    assertTrue(gate.contains("SyncChoice.isV3(context)"))
    assertTrue(gate.contains("ShopRole.current(context) in RemoteApproval.APPROVER_ROLES"))
    //  صفِ خالی ⇒ هیچ درخواستی پس از دور
    assertTrue(gate.contains("if (c.queued().isEmpty()) return 0"))
    val live = src("ui/v3/shell/V3InboxAndroid.kt")
    assertTrue(live.contains("if (!m.on.value || count <= 0) return"))
    val model = src("ui/v3/shell/V3InboxModel.kt")
    assertTrue(model.contains("page != null && page.on ->"))
    //  کدِ مشترکِ پمپ و موتورِ v1 دست نمی‌خورند
    assertFalse(gate.contains("sync-v1.js") || model.contains("PUMP_TABLES"))
  }
}
