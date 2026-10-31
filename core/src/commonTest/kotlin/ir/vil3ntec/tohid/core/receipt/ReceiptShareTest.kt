package ir.vil3ntec.tohid.core.receipt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 *  POS-32: رسیدِ دیجیتال (تصویر/PDF به واتس‌اپ، تلگرام یا پیامک) **با پیوندِ فاکتورِ آنلاین**.
 *  پیوند فقط وقتی هست که فروش توکنِ QR دارد و پرچمِ فاکتورِ نسخهٔ ۲ روشن است؛ نبودنش ⇒ همان متنِ امروز.
 */
class ReceiptShareTest {

  private val url = "https://app.vill3n.top/r/abcdefgh/0123456789abcdefghjkmnpqrs"

  @Test
  fun withoutLinkCaptionIsExactlyTodays() {
    assertEquals("فاکتور #12 — دکان احمد", ReceiptShare.caption("12", "دکان احمد", null))
    assertEquals("فاکتور #12", ReceiptShare.caption("12", "", null))
    assertEquals("فاکتور #12", ReceiptShare.caption("12", "   ", null))
    assertEquals("فاکتور #", ReceiptShare.caption(null, "", null))
  }

  @Test
  fun linkGoesOnItsOwnLastLine() {
    assertEquals(
      "فاکتور #12 — دکان احمد\nفاکتورِ آنلاین:\n$url",
      ReceiptShare.caption("12", "دکان احمد", url),
    )
    //  نشانی هیچ نویسهٔ دیگری کنارش ندارد تا واتس‌اپ و پیامک پیوندش کنند
    assertEquals(url, ReceiptShare.caption("12", "", url).lines().last())
  }

  @Test
  fun blankOrUnsafeLinkIsDropped() {
    assertEquals("فاکتور #12", ReceiptShare.caption("12", "", ""))
    assertEquals("فاکتور #12", ReceiptShare.caption("12", "", "  "))
    //  فقط http/https؛ هر طرحِ دیگر (javascript:، file:) فرستاده نمی‌شود
    assertEquals("فاکتور #12", ReceiptShare.caption("12", "", "javascript:alert(1)"))
    assertEquals("فاکتور #12", ReceiptShare.caption("12", "", "file:///sdcard/x"))
    //  نشانیِ چندخطی یا با فاصله خراب است
    assertEquals("فاکتور #12", ReceiptShare.caption("12", "", "https://a.b/r/x y"))
    assertEquals("فاکتور #12", ReceiptShare.caption("12", "", "https://a.b/r/x\nfake"))
    assertNull(ReceiptShare.safeLink("ftp://a.b/r/x"))
    assertEquals(url, ReceiptShare.safeLink("  $url  "))
    assertEquals("http://192.168.1.5:8080/r/abcdefgh/x", ReceiptShare.safeLink("http://192.168.1.5:8080/r/abcdefgh/x"))
  }

  @Test
  fun storeNameIsOneLine() {
    assertEquals("فاکتور #3 — دکان احمد کابل", ReceiptShare.caption("3", "دکان احمد\nکابل", null))
  }
}
