package ir.vil3ntec.tohid

import ir.vil3ntec.tohid.data.DashboardEngine
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/**
 *  LOC-05: تاریخی که در فایلِ داده می‌نشیند همیشه رقمِ لاتین دارد، زبانِ
 *  دستگاه هر چه باشد. پیش از این، روی دستگاهِ دری/پشتو/فارسی/عربی
 *  `Jalali.toIso(1405, 1, 1)` می‌شد `۲۰۲۶-۰۳-۲۱`.
 */
class AsciiIsoDateTest {

  private fun underEach(block: () -> Unit) {
    val keep = Locale.getDefault()
    try {
      for (tag in listOf("fa-AF", "ps-AF", "fa-IR", "ar-SA", "en-US")) {
        Locale.setDefault(Locale.forLanguageTag(tag))
        block()
      }
    } finally {
      Locale.setDefault(keep)
    }
  }

  @Test
  fun `تاریخ خورشیدی به ISO با رقم لاتین`() = underEach {
    val tag = Locale.getDefault().toLanguageTag()
    assertEquals(tag, "2026-03-21", Jalali.toIso(1405, 1, 1))
    assertEquals(tag, "2026-10-14", Jalali.parseTyped("۱۴۰۵/۰۷/۲۲"))
    assertEquals(tag, "2026-10-14", Jalali.parseTyped("1405/7/22"))
    assertEquals(tag, "2026-10-14", Jalali.parseTyped("2026/10/14"))
  }

  @Test
  fun `برچسب روز داشبورد با رقم لاتین`() = underEach {
    //  ۲۰۲۶-۱۰-۱۴ = روزِ ۲۰۷۴۰ از ۱۹۷۰-۰۱-۰۱
    assertEquals(Locale.getDefault().toLanguageTag(), "2026-10-14", DashboardEngine.isoOf(20740))
  }
}
