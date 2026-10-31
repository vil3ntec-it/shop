package ir.vil3ntec.tohid.core.marketing

import ir.vil3ntec.tohid.core.hw.MonoImage
import ir.vil3ntec.tohid.core.qr.QrCode
import ir.vil3ntec.tohid.core.receipt.ReceiptBlock
import ir.vil3ntec.tohid.core.receipt.ReceiptMetrics
import ir.vil3ntec.tohid.core.receipt.ReceiptRaster
import ir.vil3ntec.tohid.core.receipt.TextStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** MKT-10 و MKT-11 — قاعده‌های پیوندِ کمپین در برنامهٔ مدیریت (همان `server/src/lib/campaigns.js`) */
class CampaignLinksTest {

  /** نام: همان پاک‌سازیِ سرور — نویسهٔ کنترلی و فاصله‌های پیاپی یک فاصله، دو سر بریده */
  @Test
  fun nameNormalizesLikeServer() {
    assertEquals("اینستاگرامِ دکان", CampaignLinks.normalizeName("  اینستاگرامِ   دکان "))
    assertEquals("QR پیشخوان", CampaignLinks.normalizeName("QR\n\tپیشخوان"))
    assertEquals("الف ب", CampaignLinks.normalizeName("الف\u0000\u007Fب"))
    assertEquals("الف ب", CampaignLinks.normalizeName("الف" + Char(0x2028) + "ب"))
    //  نیم‌فاصله بخشی از واژه است، نه فاصله
    assertEquals("می‌خواهم", CampaignLinks.normalizeName("می‌خواهم"))
  }

  /** طول به نویسه، نه واحدِ UTF-16 — ۶۰ درست، ۶۱ خطا؛ خالی خطا */
  @Test
  fun nameLimitCountsCharactersLikeServer() {
    assertNull(CampaignLinks.nameError("ن".repeat(CampaignLinks.NAME_MAX)))
    assertNotNull(CampaignLinks.nameError("ن".repeat(CampaignLinks.NAME_MAX + 1)))
    assertNotNull(CampaignLinks.nameError("   \n\t"))
    //  شکلک دو واحدِ UTF-16 است ولی یک نویسه
    val emoji = "😀"
    assertEquals(1, CampaignLinks.codePoints(emoji))
    assertNull(CampaignLinks.nameError(emoji.repeat(CampaignLinks.NAME_MAX)))
    assertNotNull(CampaignLinks.nameError(emoji.repeat(CampaignLinks.NAME_MAX + 1)))
  }

  /** منبع: هفت کلیدِ سرور به همان ترتیب؛ کلیدِ ناشناخته «دیگر» */
  @Test
  fun sourcesMatchServerKeys() {
    assertEquals(
      listOf("qr_wall", "qr_receipt", "social", "messaging", "sms", "print", "other"),
      CampaignLinks.Source.entries.map { it.key },
    )
    assertEquals(CampaignLinks.Source.QR_RECEIPT, CampaignLinks.Source.of("qr_receipt"))
    assertEquals(CampaignLinks.Source.OTHER, CampaignLinks.Source.of("billboard"))
    assertEquals(CampaignLinks.Source.OTHER, CampaignLinks.Source.of(null))
  }

  /** نشانیِ عمومی: ریشهٔ سرور + `/store/go/<کد>`؛ مسیرِ دیگر یا ریشهٔ بی‌طرح ⇒ null */
  @Test
  fun publicUrlJoinsRootAndOnlyGoPaths() {
    assertEquals("https://api.vill3n.top/store/go/ABCDEFGHJK", CampaignLinks.publicUrl("https://api.vill3n.top", "/store/go/ABCDEFGHJK"))
    assertEquals("http://10.0.2.2:3000/store/go/0123456789", CampaignLinks.publicUrl("http://10.0.2.2:3000/", "/store/go/0123456789"))
    assertNull(CampaignLinks.publicUrl("https://api.vill3n.top", "/store/go/abcdefghjk"))
    assertNull(CampaignLinks.publicUrl("https://api.vill3n.top", "/store/go/ILOU000000"))
    assertNull(CampaignLinks.publicUrl("https://api.vill3n.top", "https://evil.example/store/go/ABCDEFGHJK"))
    assertNull(CampaignLinks.publicUrl("api.vill3n.top", "/store/go/ABCDEFGHJK"))
    assertEquals("api.vill3n.top/store/go/ABCDEFGHJK", CampaignLinks.displayUrl("https://api.vill3n.top/store/go/ABCDEFGHJK"))
  }

  /** برگهٔ رسید: نامِ دکان، جمله، QR ِ همان نشانی (همان رمزگذار) و نشانیِ کوتاه */
  @Test
  fun slipCarriesStoreNameQrAndShortUrl() {
    val url = "https://api.vill3n.top/store/go/ABCDEFGHJK"
    val doc = CampaignLinks.slip("  دکانِ   نمونه ", url, 576)
    assertEquals(576, doc.widthDots)
    assertEquals(ReceiptMetrics.MM80, doc.metrics)
    assertEquals(ReceiptBlock.Text("دکانِ نمونه", TextStyle.TITLE), doc.blocks.first())
    val qr = doc.blocks.filterIsInstance<ReceiptBlock.Qr>().single()
    assertEquals(url, qr.url)
    assertEquals(ReceiptBlock.Qr(url, QrCode.encode(url)), qr)
    assertEquals(ReceiptBlock.Text("api.vill3n.top/store/go/ABCDEFGHJK", TextStyle.SMALL), doc.blocks.last())
    //  بی نامِ دکان: بلوکِ خالی نیست
    assertTrue(CampaignLinks.slip("   ", url, 384).blocks.none { it is ReceiptBlock.Text && it.style == TextStyle.TITLE })
    assertEquals(ReceiptMetrics.MM58, CampaignLinks.slip("x", url, 384).metrics)
  }

  /** برگهٔ دیوار: پهنای A4 با ۸ نقطه در میلی‌متر و QR ِ دست‌کم ده سانتی‌متر */
  @Test
  fun posterQrIsWallSized() {
    val url = "https://api.vill3n.top/store/go/ABCDEFGHJK"
    val doc = CampaignLinks.poster("دکان", url)
    assertEquals(1680, doc.widthDots)
    val modules = QrCode.encode(url).size
    val dots = ReceiptRaster.moduleDots(modules, doc.widthDots, doc.metrics)
    val sideMm = (modules + 2 * QrCode.QUIET_ZONE) * dots / 8
    assertTrue(sideMm >= 100, "QR ِ دیوار $sideMm میلی‌متر است")
    assertTrue(sideMm <= doc.widthDots / 8, "QR در پهنای برگه جا می‌شود")
  }

  /** صفحهٔ A4: تصویرِ کوتاه وسطِ عمودی؛ تصویرِ بلندتر یا پهنای دیگر دست‌نخورده */
  @Test
  fun pageCentersPosterOnA4() {
    val img = MonoImage(CampaignLinks.A4_WIDTH, 100)
    img.fill(0, 0, CampaignLinks.A4_WIDTH, 100)
    val page = CampaignLinks.page(img)
    assertEquals(CampaignLinks.A4_WIDTH, page.width)
    assertEquals(2376, page.height)
    val top = (2376 - 100) / 2
    assertTrue(page[10, top] && page[10, top + 99])
    assertTrue(!page[10, top - 1] && !page[10, top + 100])
    val tall = MonoImage(CampaignLinks.A4_WIDTH, 3000)
    assertTrue(CampaignLinks.page(tall) === tall)
    val narrow = MonoImage(576, 100)
    assertTrue(CampaignLinks.page(narrow) === narrow)
  }
}
