package ir.vil3ntec.tohid.core.image

import ir.vil3ntec.tohid.core.image.BankPhotoCard.Resolved
import ir.vil3ntec.tohid.core.image.BankPhotoCard.Source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 *  §۳۷.۷ «گزارشِ عکسِ اشتباه یا بستهٔ قدیمی» (`POST /api/shop/images/:id/report`): فقط عکسِ مرکزیِ دیده‌شده گزارش
 *  می‌شود (عکسِ خودِ دکان را خودش عوض می‌کند)؛ نوع فقط از همان فهرستِ سرور (`REPORT_KINDS`، بی «پروانه» که کارِ مدیر
 *  است) و یادداشت تا ۵۰۰ نویسه.
 */
class BankPhotoReportTest {

  private val central = Resolved(Source.CENTRAL, imageId = "img_c", version = 1, centralImageId = "img_c", centralVersion = 1)

  @Test fun onlyTheShownCentralPhotoIsReportable() {
    assertEquals("img_c", BankPhotoReport.reportable(central))
    assertEquals("img_c", BankPhotoReport.reportable(central.copy(archived = true)), "بایگانی‌شده هم دیده می‌شود ⇒ گزارش‌شدنی")
    assertNull(BankPhotoReport.reportable(Resolved(Source.SHOP, imageId = "img_own", version = 1)))
    assertNull(BankPhotoReport.reportable(Resolved(Source.LEGACY)))
    assertNull(BankPhotoReport.reportable(Resolved(Source.NONE)))
    assertNull(BankPhotoReport.reportable(central.copy(imageId = "")), "بی شناسه چیزی برای گزارش نیست")
  }

  @Test fun kindsAreTheServerListAndTheNoteIsCapped() {
    assertEquals(listOf("wrong_product", "old_packaging", "quality", "other"), BankPhotoReport.KINDS)
    assertEquals("old_packaging" to "بستهٔ تازه آمده", BankPhotoReport.body("old_packaging", "  بستهٔ تازه آمده  "))
    assertEquals(500, BankPhotoReport.body("other", "x".repeat(900))!!.second.length)
    assertNull(BankPhotoReport.body("license", "پروانه کارِ مدیر است"))
    assertNull(BankPhotoReport.body("", ""))
    assertEquals("/shop/images/img_c/report", BankPhotoReport.path("img_c"))
  }
}
