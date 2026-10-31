package ir.vil3ntec.tohid.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  «پاک‌سازی کامل اطلاعات» (`WipeAllDialog`): مرحلهٔ دوم فقط با نامِ درستِ دکان باز می‌شود، و دکمه را فقط
 *  صاحبِ دکان می‌بیند — این کار دفترِ دکان را روی همهٔ دستگاه‌ها و سرور خالی می‌کند.
 */
class WipeAllNameTest {

  @Test fun `نامِ درست، با تفاوت‌های نوشتاریِ بی‌ضرر`() {
    assertTrue(WipeAllName.matches("دکان احمد", "دکان احمد"))
    assertTrue("فاصلهٔ دو سر", WipeAllName.matches("  دکان احمد ", "دکان احمد"))
    assertTrue("نیم‌فاصله ⇔ فاصله", WipeAllName.matches("سوپر مارکت", "سوپر\u200Cمارکت"))
    assertTrue("ی/ک ِ عربی", WipeAllName.matches("\u0643\u062A\u0627\u0628 \u0639\u0644\u064A", "کتاب علی"))
    assertTrue("کسرهٔ اضافه", WipeAllName.matches("دکان احمد", "دکان\u0650 احمد"))
    assertTrue("حروفِ لاتین", WipeAllName.matches("vill3n shop", "VILL3N Shop"))
  }

  @Test fun `نامِ نادرست یا خالی پاک نمی‌کند`() {
    assertFalse(WipeAllName.matches("", "دکان احمد"))
    assertFalse(WipeAllName.matches("دکان", "دکان احمد"))
    assertFalse(WipeAllName.matches("دکان محمود", "دکان احمد"))
    assertFalse("نامِ خالیِ دکان هیچ‌وقت با خالی برابر نیست", WipeAllName.matches("", ""))
    assertFalse(WipeAllName.matches("   ", " \u200C "))
  }

  @Test fun `دکمه فقط برای صاحبِ دکان و پشتِ دو مرحله`() {
    val screens = listOf(
      File("src/main/java/ir/vil3ntec/tohid/ui/screens"),
      File("android-native/app/src/main/java/ir/vil3ntec/tohid/ui/screens"),
      File("../android-native/app/src/main/java/ir/vil3ntec/tohid/ui/screens"),
    ).firstOrNull { it.resolve("Settings.kt").isFile } ?: return
    val settings = screens.resolve("Settings.kt").readText()
    val button = settings.indexOf("text = StrSettings.wipeAll")
    assertTrue("دکمهٔ پاک‌سازی پیدا نشد", button > 0)
    val gate = settings.lastIndexOf("ShopRole.current(context) == ir.vil3ntec.tohid.data.ShopRole.OWNER", button)
    assertTrue("دکمه پشتِ نقشِ صاحب نیست", gate in (button - 400) until button)
    assertTrue("گفت‌وگوی دو مرحله‌ای", settings.contains("WipeAllDialog("))
  }
}
