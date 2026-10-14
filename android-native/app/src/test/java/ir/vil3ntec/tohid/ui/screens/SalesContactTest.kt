package ir.vil3ntec.tohid.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  F0-24 — شمارهٔ واتس‌اپِ فروش از سرور (`app_config.whatsapp_number`)،
 *  با همان شمارهٔ همیشگی به‌عنوانِ پشتوانه.
 *
 *  بندِ آخر کدِ `Vip.kt` را می‌خواند تا شمارهٔ ثابت بی‌صدا برنگردد
 *  (همان الگوی `PrimaryModifierTest`). همین پوشه در CIِ اندروید و کامپیوتر
 *  اجرا می‌شود.
 */
class SalesContactTest {
  private val screens: File? = listOf(
    "src/main/java/ir/vil3ntec/tohid/ui/screens",                          // android-native/app
    "../android-native/app/src/main/java/ir/vil3ntec/tohid/ui/screens",    // desktop
    "android-native/app/src/main/java/ir/vil3ntec/tohid/ui/screens",       // ریشهٔ مخزن
  ).map { File(it) }.firstOrNull { it.isDirectory }

  @Test
  fun `شمارهٔ سرور به کار می‌رود`() {
    assertEquals("0700123456", SalesContact.display("0700123456"))
    assertEquals("93700123456", SalesContact.waDigits("0700123456"))
    assertEquals("93700123456", SalesContact.waDigits("+93 70 012 3456"))
    assertEquals("93700123456", SalesContact.waDigits("0093700123456"))
  }

  @Test
  fun `بی پاسخِ سرور همان شمارهٔ همیشگی`() {
    for (none in listOf(null, "", "   ", "—")) {
      assertEquals("0792236008", SalesContact.display(none))
      assertEquals("93792236008", SalesContact.waDigits(none))
    }
  }

  @Test
  fun `Vip_kt شمارهٔ واتس‌اپ را ثابت نمی‌نویسد`() {
    val dir = screens
    assertTrue("پوشهٔ صفحه‌ها پیدا نشد (cwd=${File(".").absoluteFile})", dir != null)
    val src = File(dir, "Vip.kt").readText()
    for (n in listOf("93792236008", "0792236008")) {
      assertTrue("شمارهٔ $n هنوز در Vip.kt ثابت است — از SalesContact بخوانید", n !in src)
    }
    assertTrue(src.contains("SalesContact.waDigits("))
  }
}
