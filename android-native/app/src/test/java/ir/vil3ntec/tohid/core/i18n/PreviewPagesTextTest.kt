package ir.vil3ntec.tohid.core.i18n

import ir.vil3ntec.tohid.core.text.DariText
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  LOC-06 دورِ ۳ (`claude/e-rows-28l`): متنِ خامِ صفحه‌های قدیمیِ گوشی که فقط پیش‌نمایشِ `DevShot` (یا آزمون) نشانشان می‌دهد و
 *  مرده‌های نگه‌داشته (`WarehouseDialogs`، `PhoneClaimStep`) به جدول‌های `Str*` رفت. فارسی بایت‌به‌بایت همان متنِ پیشینِ صفحه است
 *  (رشته‌های این‌جا رونوشتِ همان رشته‌های خام‌اند)، دری از لایهٔ واژه می‌گذرد و انگلیسی/پشتو ترجمه دارند.
 */
class PreviewPagesTextTest {

  @After fun reset() {
    I18n.source = { Lang.FA }
    I18n.desktop = false
  }

  /** پرونده‌هایی که این دور همهٔ متنِ خامشان را به جدول برد */
  private val files = listOf(
    "Sale.kt", "QuickSale.kt", "Profile.kt", "ProfileModel.kt", "Settings.kt", "Debtors.kt", "DebtorDetail.kt", "DebtorTxSheet.kt",
    "NavEditor.kt", "IdleLockChoice.kt", "VoiceSearch.kt", "PhoneHomeModel.kt", "PhoneClaimStep.kt", "WarehouseDialogs.kt",
  )

  private val letter = Regex("[\\u0621-\\u063A\\u0641-\\u064A\\u067E\\u0686\\u0698\\u06A9\\u06AF\\u06CC]")

  /** همان قاعدهٔ `DariCoverageTest.raw`: سطرِ «// داده» و توضیح شمرده نمی‌شود */
  private fun raw(f: File): List<String> {
    val text = f.readLines().filterNot { it.contains("// داده") }
      .filterNot { it.trimStart().let { t -> t.startsWith("//") || t.startsWith("*") || t.startsWith("/*") } }
      .joinToString("\n") { it.replace(Regex("""\s//\s.*$"""), "") }
    return Regex(""""((?:[^"\\\n]|\\.)*)"""").findAll(text).map { it.groupValues[1] }.filter { letter.containsMatchIn(it) }.toList()
  }

  @Test fun `صفحه‌های فقط‌پیش‌نمایش و مرده‌های نگه‌داشته متنِ خامِ فارسی ندارند`() {
    val dir = DesktopPhrases.repo().resolve("android-native/app/src/main/java/ir/vil3ntec/tohid/ui/screens")
    val bad = files.flatMap { name ->
      val f = dir.resolve(name)
      assertTrue("$name دیگر نیست — از فهرست بردارید", f.isFile)
      raw(f).map { "$name: «$it»" }
    }
    assertTrue("متنِ خام — به جدولِ Str* ببرید:\n" + bad.joinToString("\n"), bad.isEmpty())
  }

  /** نوشتهٔ تازهٔ جدول ⇐ همان رشتهٔ خامِ پیشین */
  private val same: List<Pair<() -> String, String>> = listOf(
    { StrSalePages.addFreeItem } to "+ قلم آزاد",
    { StrSalePages.pickDebtorForCreditHint } to "برای فروشِ نسیه، قرض‌دار را انتخاب کنید",
    { StrSalePages.removeDebtor } to "برداشتنِ قرض‌دار",
    { StrSalePages.newCategory } to "دسته‌بندیِ تازه",
    { StrSalePages.categoryNameHint } to "نام دسته‌بندی را بنویسید…",
    { StrSalePages.tapToAddOne } to "روی محصول بزنید تا یک عدد به سبد خرید اضافه شود",
    { StrSalePages.searchNameOrBarcode } to "جستجوی نام کالا یا بارکد",
    { StrSalePages.allCategories } to "همه دسته‌بندی‌ها",
    { StrSalePages.addCategoryChip } to "+ دسته‌بندی",
    { StrSalePages.deleteThisCategory } to "حذف این دسته",
    { StrDebtPages.colPhone } to "شماره",
    { StrDebtPages.colBalance } to "مانده",
    { StrDebtPages.colAge } to "مدت",
    { StrDebtPages.receiveMenu } to "ثبتِ رسید (پول گرفتم)",
    { StrDebtPages.newDebtMenu } to "قرضِ تازه",
    { StrDebtPages.returnedDot("2 عدد") } to " · مرجوعی 2 عدد",
    { StrShellPages.accountPhoto } to "عکسِ حساب",
    { StrShellPages.onThisPhoneOnly } to "فقط روی همین گوشی می‌ماند",
    { StrShellPages.themeHint } to "روشن، تیره، یا خودکار (مثل گوشی)",
    { StrShellPages.syncSignInFirst } to "برای همگام‌سازی اول وارد حساب شوید",
    { StrShellPages.syncBody(StrShellPages.syncNever) } to
      "دفترِ دکان روی سرور پشتیبان می‌گیرد و روی گوشیِ دیگر هم می‌آید. " + "آخرین همگام‌سازی: " + "هنوز انجام نشده",
    { StrShellPages.voiceSearch } to "جستجوی صوتی",
    { StrShellPages.mobileOnce } to "شماره موبایل — یک بار ثبت می‌شود",
    { StrShellPages.smsCodeLabel } to "کدی که به همین شماره پیامک شد",
    { StrShellPages.smsCodeSent } to "کدی به همین شماره پیامک شد؛ آن را بنویسید و «ذخیره» را بزنید",
    { StrStockPages.editProduct } to "ویرایش محصول",
    { StrStockPages.itemName } to "نام کالا",
    { StrStockPages.sameItemTail } to " — اگر همان کالاست، ثبتِ دوباره نکنید.",
    { StrStockPages.priceReasonKept } to "دلیلِ تغییرِ قیمت (با نامِ شما در سابقه می‌ماند)",
    { StrStockPages.subcategoryOpt } to "زیردسته (اختیاری)",
    { StrStockPages.brandOpt } to "برند (اختیاری)",
    { StrStockPages.secondUnitOpt } to "واحدِ فروشِ دوم (اختیاری)",
    { StrStockPages.supplierOpt } to "تأمین‌کننده (اختیاری)",
    { StrStockPages.saleStatus } to "وضعیت در فروش",
    { StrStockPages.receiveGoods } to "ثبت ورود کالا",
    { StrStockPages.makesQty("12 عدد") } to "12 عدد می‌شود",
    { StrStockPages.expiryOpt } to "تاریخ انقضا (اختیاری)",
    { StrStockPages.batchOpt } to "سری ساخت (اختیاری)",
    { StrStockPages.madeOpt } to "تاریخ تولید (اختیاری)",
    { StrStockPages.landedOpt } to "هزینهٔ جانبی (کرایه، گمرک) — اختیاری، کلِ همین ورود",
    { StrStockPages.landedHint } to "به بهای همین کالا اضافه می‌شود (میانگینِ موزون)؛ جدا در «مصارف» ثبتش نکنید.",
    { StrStockPages.storagePlace } to "محلِ نگه‌داری",
    { StrStockPages.newPlace } to "محلِ تازه",
    { StrStockPages.supplierReturnHint } to
      "برگشت به تأمین‌کننده: از «خرید و تأمین‌کننده» ⇐ همان تأمین‌کننده ⇐ «برگشت (سندِ کسر)»؛ هم کالا کم می‌شود هم بدهی.",
    { StrStockPages.adjustKind } to "نوعِ اصلاح",
    { StrStockPages.wasteHint } to "خراب و منقضی در گزارشِ ضایعات جمع می‌شوند.",
  )

  /** کامپیوتر: جدولِ `phrases` ِ `desktop/build.gradle.kts` «گوشی/دوربین» ِ فارسیِ کدِ اندروید (جدول‌ها هم) را پیش از کامپایل برمی‌گرداند */
  private val phrases = DesktopPhrases.load()

  private fun desk(text: String): String {
    var out = text
    for ((from, to) in phrases) if (out.contains(from)) out = out.replace(from, to)
    return out
  }

  /** همان رشتهٔ خام — یا روی کامپیوتر همان رشته پس از بازنویسیِ `phrases` (رشتهٔ خامِ صفحه هم همان بازنویسی را می‌گرفت) */
  private fun assertFaSame(fa: String, actual: String) =
    assertTrue("«$actual» ≠ «$fa»", actual == fa || actual == desk(fa))

  @Test fun `فارسیِ نوشته‌های این دور همان رشتهٔ خامِ پیشین است و دری از لایهٔ واژه می‌گذرد`() {
    var dariDiffers = 0
    for ((get, fa) in same) {
      I18n.source = { Lang.FA }
      val faNow = get()
      assertFaSame(fa, faNow)
      val withArg = fa.contains("عدد")
      I18n.source = { Lang.PRS }
      val dari = get()
      if (!withArg) assertEquals(fa, DariText.of(faNow), dari)
      if (dari != fa) dariDiffers++
      I18n.source = { Lang.EN }
      val en = get()
      assertFalse("$fa: انگلیسی حرفِ فارسی دارد: $en", !withArg && letter.containsMatchIn(en))
      assertTrue("$fa: انگلیسی همان فارسی است", en != fa)
    }
    assertTrue("دری هیچ فرقی ندارد", dariDiffers > 0)
  }

  /** همان رشته‌های خام که حالا ویژگی یا تابعِ پیشینِ جدول را می‌خوانند — فارسیِ آن‌ها هم همان است */
  @Test fun `جمله‌هایی که به ویژگیِ پیشینِ جدول رفتند در فارسی همان رشتهٔ خام‌اند`() {
    I18n.source = { Lang.FA }
    val q = "3 بسته"
    assertEquals("کرم فقط $q موجود است", StrSale.onlyLeftOf("کرم", q))
    assertEquals("فقط $q موجود است", StrSale.onlyInStock(q))
    assertEquals("$q مانده", StrSale.leftQty(q))
    assertEquals("$q در سبد", StrSale.inCartQty(q))
    assertEquals("کرم — سری L7 به سبد اضافه شد", StrSalePages.lotAdded("کرم", "L7"))
    assertEquals("کالایی با «شیر» پیدا نشد", StrSale.noItemFor("شیر"))
    assertEquals("فروش به مبلغ 1,200 ؋ ثبت شد", StrSale.saleRecorded("1,200"))
    assertFaSame("دوربین خاموش است", StrSale.cameraOff)
    assertFaSame("روشن کردن دوربین", StrSale.turnOnCamera)
    assertEquals(
      "دسته‌بندی «لبنیات» حذف می‌شود. محصولات این دسته حذف نمی‌شوند، فقط دیگر در این دسته‌بندی فیلتر نمی‌شوند.",
      StrProducts.deleteCategoryBody("لبنیات"),
    )
    assertEquals("هر واحد = چند کارتن", StrProducts.factorHelper("کارتن"))
    assertEquals("جمع: 1,200 ؋", StrStock.total("1,200"))
    assertEquals("قرص · 2 تا 5 مورد؛ «بیشتر» همیشه هست", StrSettings.slotPill(2, 5))
    assertEquals("جای «فروش»", StrSettings.placeOf("فروش"))
    assertEquals("5 دقیقه", StrSettings.minutes(5))
    assertEquals("مهمان", StrAccount.guest)
    assertEquals("آزمایشی: 3 روزِ دیگر", StrAccount.bannerTrialDays(3))
    assertEquals("اشتراک: 3 روزِ دیگر", StrAccount.bannerActiveDays(3))
    assertEquals("عضو از 1405/08/22", StrAccount.memberSince("1405/08/22"))
    assertEquals("2 برد از سررسید گذشته — 500 افغانی", StrDebtPages.overdueBanner(2, "500"))
    assertEquals("تخفیف 50", StrDebtPages.discountOf("50"))
    assertEquals("پرداختِ همان لحظه 50", StrDebtPages.paidOnSpot("50"))
    assertEquals(listOf("هفته", "ماه", "سال"), listOf(StrReports.periodWeek, StrReports.periodMonth, StrReports.periodYear))
    assertEquals(listOf("دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه", "شنبه", "یکشنبه"), StrReports.weekdaysShort)
  }

  @Test fun `نوشتهٔ گوشی‌دار روی کامپیوتر انگلیسیِ کامپیوتری می‌گیرد`() {
    I18n.source = { Lang.EN }
    I18n.desktop = false
    assertEquals("Stays on this phone only", StrShellPages.onThisPhoneOnly)
    assertTrue(StrShellPages.syncBody("x").contains("phone"))
    I18n.desktop = true
    assertEquals("Stays on this computer only", StrShellPages.onThisPhoneOnly)
    assertEquals("Light, dark, or automatic (like the system)", StrShellPages.themeHint)
    assertTrue(StrShellPages.syncBody("x").contains("device"))
  }
}
