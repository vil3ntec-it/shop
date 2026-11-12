package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.core.backup.WipeAll
import ir.vil3ntec.tohid.data.ShopRole
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  «پاک‌سازی کامل اطلاعات» — صفحهٔ زنده (نسخهٔ ۳: تنظیمات ⇐ داده‌ها، `BackupScreen` + `WipeAllSheet` + `BackupController.wipe`):
 *  مرحلهٔ دوم فقط با نامِ درستِ دکان باز می‌شود، ردیف را فقط صاحبِ دکان می‌بیند و «پاک شد» فقط پس از نوشتنِ موفق — این کار
 *  دفترِ دکان را روی همهٔ دستگاه‌ها و سرور خالی می‌کند. قاعده در `core/backup/WipeAll` (شرح و آزمونِ کاملش `WipeAllTest`)؛
 *  پنجرهٔ واقعیِ Compose ِ همین صفحه: `WipeAllUiTest` (کامپیوتر). `ui/screens/Settings.kt` + `WipeAllDialog` فقط پیش‌نمایشِ DevShot‌اند.
 */
class WipeAllNameTest {

  private fun source(rel: String): String? = listOf(
    "src/main/java/ir/vil3ntec/tohid/$rel",
    "android-native/app/src/main/java/ir/vil3ntec/tohid/$rel",
    "../android-native/app/src/main/java/ir/vil3ntec/tohid/$rel",
  ).map { File(it) }.firstOrNull { it.isFile }?.readText()

  @Test fun `نامِ درست، با تفاوت‌های نوشتاریِ بی‌ضرر`() {
    assertTrue(WipeAll.matches("دکان احمد", "دکان احمد"))
    assertTrue("فاصلهٔ دو سر", WipeAll.matches("  دکان احمد ", "دکان احمد"))
    assertTrue("نیم‌فاصله ⇔ فاصله", WipeAll.matches("سوپر مارکت", "سوپر‌مارکت"))
    assertTrue("ی/ک ِ عربی", WipeAll.matches("كتاب علي", "کتاب علی"))
    assertTrue("کسرهٔ اضافه", WipeAll.matches("دکان احمد", "دکانِ احمد"))
    assertTrue("حروفِ لاتین", WipeAll.matches("vill3n shop", "VILL3N Shop"))
  }

  @Test fun `نامِ نادرست یا خالی پاک نمی‌کند`() {
    assertFalse(WipeAll.matches("", "دکان احمد"))
    assertFalse(WipeAll.matches("دکان", "دکان احمد"))
    assertFalse(WipeAll.matches("دکان محمود", "دکان احمد"))
    assertFalse("نامِ خالیِ دکان هیچ‌وقت با خالی برابر نیست", WipeAll.matches("", ""))
    assertFalse(WipeAll.matches("   ", " ‌ "))
  }

  @Test fun `نقشِ صاحب همان ثابتِ ShopRole است`() {
    assertTrue(WipeAll.visible(ShopRole.OWNER))
    assertFalse(WipeAll.visible(ShopRole.MANAGER))
  }

  @Test fun `صفحهٔ زنده — ردیف فقط برای صاحب، دو مرحله و نتیجهٔ واقعیِ نوشتن`() {
    val screen = source("ui/v3/settings/sections/BackupScreen.kt") ?: return
    val ctrl = source("ui/v3/settings/sections/BackupController.kt")!!
    val sheet = source("ui/v3/settings/sections/WipeAllSheet.kt")!!
    val row = screen.indexOf("V3ListRow(StrSettings.wipeAll")
    assertTrue("ردیفِ پاک‌سازی پیدا نشد", row > 0)
    val gate = screen.lastIndexOf("if (wipeOk) {", row)
    assertTrue("ردیف پشتِ نگهبانِ صاحب نیست", gate in (row - 300) until row)
    assertTrue(screen.contains("val wipeOk = remember { WipeAll.visible(ShopRole.current(context)) }"))
    assertTrue("برگهٔ دو مرحله‌ای", screen.contains("Ask.WIPE -> WipeAllSheet("))
    assertFalse("برگهٔ تأییدِ ساده دیگر نه", screen.contains("V3ConfirmSheet(StrSettings.wipeQ"))
    assertTrue("مرحلهٔ دوم با نام", sheet.contains("WipeAll.matches(typed, expected)") && sheet.contains("StrSettings.wipeNameHint(expected)"))
    assertTrue("نقش و نام دوباره پیش از نوشتن", ctrl.contains("WipeAll.decide(ShopRole.current(context), typed, wipeName)"))
    assertTrue("«پاک شد» فقط با نتیجهٔ replaceAll", ctrl.contains("WipeAll.outcome(store.replaceAll(ShopData()))"))
    assertTrue(ctrl.contains("WipeAll.Outcome.FAILED -> toasts.show(StrSettings.wipeFailed"))
  }
}
