package ir.vil3ntec.tohid.print

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 *  کدام چاپگر، و با چه چیزی وصل است.
 *
 *  ── چرا این سنجه‌ها مهم‌اند ─────────────────────────────────────────
 *  این رشته همان چیزی است که در حافظهٔ گوشیِ هر دکان‌دار نوشته شده.
 *  اگر خواندنش خراب شود، چاپگرِ دکانی که سال‌ها کار می‌کرده یک روز
 *  صبح «پیدا نمی‌شود» — و هیچ‌کس نمی‌فهمد چرا.
 *
 *  به‌ویژه نشانیِ **لخت**: نسخه‌های قبلی فقط MAC را می‌نوشتند، بی هیچ
 *  پیشوندی. اگر روزی کسی این حالت را بردارد، چاپگرِ همهٔ نصب‌های
 *  قدیمی با هم از کار می‌افتد.
 */
class PrinterLinkTest {

  private val Link = ThermalPrinter.Link.Companion

  /* ---------------------------- بلوتوث ---------------------------- */

  @Test
  fun `نشانیِ لختِ نسخه‌های قبلی بلوتوث فهمیده می‌شود`() {
    val link = Link.parse("00:11:22:33:44:55")
    assertEquals(ThermalPrinter.Link.Bluetooth("00:11:22:33:44:55"), link)
  }

  @Test
  fun `بلوتوثِ پیشونددار هم همان است`() {
    assertEquals(
      ThermalPrinter.Link.Bluetooth("00:11:22:33:44:55"),
      Link.parse("bt:00:11:22:33:44:55")
    )
  }

  /* ---------------------------- شبکه ---------------------------- */

  @Test
  fun `چاپگرِ شبکه با درگاهِ پیش‌فرض`() {
    val link = Link.parse("net:192.168.1.50")
    assertEquals(ThermalPrinter.Link.Network("192.168.1.50", 9100), link)
  }

  @Test
  fun `چاپگرِ شبکه با درگاهِ دلخواه`() {
    assertEquals(
      ThermalPrinter.Link.Network("192.168.1.50", 9101),
      Link.parse("net:192.168.1.50:9101")
    )
  }

  /* ---------------------------- سیم ---------------------------- */

  @Test
  fun `چاپگرِ سیمی با نامِ دستگاه`() {
    assertEquals(
      ThermalPrinter.Link.Usb("/dev/bus/usb/001/002"),
      Link.parse("usb:/dev/bus/usb/001/002")
    )
  }

  /* ---------------------------- رفت و برگشت ---------------------------- */

  @Test
  fun `هرچه نوشته شد، همان خوانده می‌شود`() {
    val all = listOf(
      ThermalPrinter.Link.Bluetooth("AA:BB:CC:DD:EE:FF"),
      ThermalPrinter.Link.Network("10.0.0.7"),
      ThermalPrinter.Link.Network("10.0.0.7", 9101),
      ThermalPrinter.Link.Usb("/dev/bus/usb/001/002"),
    )
    for (link in all) {
      assertEquals("رفت‌وبرگشتِ $link", link, Link.parse(link.save()))
    }
  }

  /* ---------------------------- هیچ ---------------------------- */

  @Test
  fun `خالی یعنی چاپگری انتخاب نشده`() {
    assertNull(Link.parse(null))
    assertNull(Link.parse(""))
    assertNull(Link.parse("   "))
  }

  @Test
  fun `شبکهٔ بی‌نشانی پذیرفته نمی‌شود`() {
    //  وگرنه برنامه به `` وصل می‌شد و خطایش هیچ‌چیز نمی‌گفت
    assertNull(Link.parse("net:"))
  }

  /* ---------------------------- نمایش ---------------------------- */

  @Test
  fun `برچسبِ هر کدام همان چیزی است که کاربر می‌شناسد`() {
    assertEquals("192.168.1.50", ThermalPrinter.Link.Network("192.168.1.50").label())
    //  درگاهِ غیرپیش‌فرض دیده می‌شود، وگرنه کاربر نمی‌فهمد چرا کار نمی‌کند
    assertEquals("192.168.1.50:9101", ThermalPrinter.Link.Network("192.168.1.50", 9101).label())
    //  مسیرِ دراز USB روی صفحه جا نمی‌شود؛ همان تکهٔ آخر بس است
    assertEquals("002", ThermalPrinter.Link.Usb("/dev/bus/usb/001/002").label())
  }
}
