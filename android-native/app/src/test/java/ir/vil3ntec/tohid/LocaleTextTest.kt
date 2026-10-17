package ir.vil3ntec.tohid

import ir.vil3ntec.tohid.core.text.LocaleFormat
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.DebtorEngine
import ir.vil3ntec.tohid.data.LedgerEngine
import ir.vil3ntec.tohid.data.ShopData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  سیم‌کشیِ LOC-01، ‎-05، ‎-07، ‎-09 (`LocaleText`).
 *
 *  اول آزمون‌های «همان دیروز»: در حالتِ پیش‌فرض (رقمِ لاتین، ماهِ دری) فاکتور
 *  و برچسب‌ها **دقیقاً** همان متنِ پیشین را می‌سازند. بعد حالت‌های تازه.
 */
class LocaleTextTest {

  private val latn = LocaleFormat.DIGITS_LATN
  private val fa = LocaleFormat.DIGITS_FA

  /** همان فهرستی که پیش از این در `Jalali.kt` نوشته شده بود */
  private val oldMonths = listOf(
    "حمل", "ثور", "جوزا", "سرطان", "اسد", "سنبله",
    "میزان", "عقرب", "قوس", "جدی", "دلو", "حوت",
  )

  private val amounts = listOf(
    0.0, -0.0, 1.0, 0.5, 1.5, 2.5, -0.5, -1.5, 999.0, 1000.0, 1250000.0, 1234567.89,
    -1234567.5, 49.999, 12.345, 1e12, -1e12, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
  )

  /* ------------------------------ همان دیروز ------------------------------ */

  @Test
  fun `فاکتور با رقم لاتین دقیقا همان متن پیشین است`() {
    for (x in amounts) {
      assertEquals("$x", money(x), LocaleText.amount(x, latn))
      assertEquals("$x", "${money(x)} افغانی", LocaleText.afghani(x, latn))
    }
    var v = -50_000.25
    while (v < 50_000) {
      assertEquals("$v", money(v), LocaleText.amount(v, latn))
      v += 7.75
    }
    for (iso in listOf("2026-10-14", "2026-03-21", "2025-03-20", "1999-12-31", "چیزی-نیست", "")) {
      assertEquals(iso, formatDate(iso), LocaleText.date(iso, latn))
    }
    assertEquals("فاکتور فروش #A1-12", LocaleText.digitsIn("فاکتور فروش #A1-12", latn))
  }

  @Test
  fun `پیش فرض ها رفتار امروزند`() {
    for (v in listOf(null, "", "latn", "LATN", "ar", "arab", "x")) assertEquals("$v", latn, LocaleText.digitsOf(v))
    assertEquals(fa, LocaleText.digitsOf("fa"))
    for (v in listOf(null, "", "fa", "dari", "PS", "en")) assertEquals("$v", LocaleText.MONTHS_DARI, LocaleText.monthsOf(v))
    assertEquals(LocaleText.MONTHS_PASHTO, LocaleText.monthsOf("ps"))
    assertEquals(oldMonths, LocaleText.monthNames(LocaleText.MONTHS_DARI))
    //  بی انتخاب (همان دستگاهِ امروز) ⇒ دری
    assertEquals(oldMonths, JALALI_MONTHS)
    assertEquals("سنبله 1405", formatMonth("2026-09"))
    assertNull("رقمِ لاتین ⇒ هیچ ویژگیِ فونتی، همان سبکِ امروز", LocaleText.fontFeatures(latn))
  }

  /* ------------------------------ حالت‌های تازه ------------------------------ */

  @Test
  fun `رقم فارسی در فاکتور و بی اعشار`() {
    assertEquals("۱٬۲۵۰٬۰۰۰ افغانی", LocaleText.afghani(1250000.0, fa))
    assertEquals("۱٬۲۳۴٬۵۶۸", LocaleText.amount(1234567.89, fa))
    assertEquals("-۱", LocaleText.amount(-1.5, fa))   // Math.round: نیمه رو به بالا، همان money()
    assertEquals("۱۴۰۵/۰۷/۲۲", LocaleText.date("2026-10-14", fa))
    assertEquals("فاکتور فروش #A۱-۱۲", LocaleText.digitsIn("فاکتور فروش #A1-12", fa))
    assertEquals("۱۴:۰۵", LocaleText.digitsIn("14:05", fa))
    //  رقمِ فارسی فقط شکلِ نمایش است: برگرداندنش همان ASCII است
    assertEquals("1405/07/22", LocaleFormat.asciiDigits(LocaleText.date("2026-10-14", fa)))
    assertEquals("ss01", LocaleText.fontFeatures(fa))
  }

  @Test
  fun `نماد پول در فاکتور قابل تنظیم است و پیش فرض افغانی است`() {
    for (v in listOf(null, "", "word", "none", "AFN", "x")) assertEquals("$v", LocaleFormat.SYMBOL_WORD, LocaleText.symbolOf(v))
    assertEquals(LocaleFormat.SYMBOL_SIGN, LocaleText.symbolOf("sign"))
    assertEquals("1,250,000 ؋", LocaleText.afghani(1250000.0, latn, LocaleFormat.SYMBOL_SIGN))
    assertEquals("۱٬۲۵۰٬۰۰۰ ؋", LocaleText.afghani(1250000.0, fa, LocaleFormat.SYMBOL_SIGN))
    //  مقدارِ ناشناخته هرگز مبلغِ بی نماد نمی‌سازد
    assertEquals("1,250,000 افغانی", LocaleText.afghani(1250000.0, latn, "none"))
    assertEquals(LocaleFormat.SYMBOL_WORD, LocaleChoice.symbol)
  }

  @Test
  fun `نام ماه پشتو`() {
    val ps = LocaleText.monthNames(LocaleText.MONTHS_PASHTO)
    assertEquals(12, ps.size)
    assertEquals("وری", ps[0])
    assertEquals("تله", ps[6])
    assertEquals("کب", ps[11])
  }

  /* --------------------------------- تلفن --------------------------------- */

  @Test
  fun `تلفن هنگام ذخیره به +93 می رود و چیز نادرست گم نمی شود`() {
    assertEquals("+93799123456", LocaleText.phoneForSave("0799 123 456"))
    assertEquals("+93799123456", LocaleText.phoneForSave(" ۰۷۹۹۱۲۳۴۵۶ "))
    assertEquals("+93799123456", LocaleText.phoneForSave("+93 0799-123-456"))
    assertEquals("+93799123456", LocaleText.phoneForSave("0093799123456"))
    assertEquals("+12025550100", LocaleText.phoneForSave("+1 202 555 0100"))
    assertEquals("", LocaleText.phoneForSave("   "))
    //  شمارهٔ کوتاه یا ناقص: همان‌طور که نوشته شده، فقط رقمش ASCII
    assertEquals("0788", LocaleText.phoneForSave("0788"))
    assertEquals("0788", LocaleText.phoneForSave("۰۷۸۸"))
    assertEquals("دفتر: 0788", LocaleText.phoneForSave(" دفتر: ۰۷۸۸ "))
  }

  @Test
  fun `نمایش تلفن`() {
    assertEquals("‎+93 79 912 3456", LocaleText.phoneDisplay("+93799123456"))
    assertEquals("0799123456", LocaleText.phoneDisplay("0799123456"))
    assertEquals("", LocaleText.phoneDisplay(""))
    assertEquals("‎+12025550100", LocaleText.phoneDisplay("+12025550100"))
  }

  @Test
  fun `جست وجوی تلفن هر چه دیروز پیدا می شد را باز پیدا می کند`() {
    val phones = listOf("0700", "0799123456", "+93799123456", "۰۷۹۹۱۲۳۴۵۶", "079-912-3456", "", "دفتر 0788", "+12025550100")
    val queries = listOf("0", "07", "0799", "799", "123", "456", "-", "+", "+93", "۰۷۹۹", "دفتر", "x", "93", "0700", " ")
    for (p in phones) for (q in queries) {
      if (p.contains(q)) assertTrue("«$p» با «$q» دیروز پیدا می‌شد", LocaleText.phoneMatches(p, q))
    }
    //  تازه: شمارهٔ ذخیره‌شده با ‎+93 با عادتِ «07…» و رقمِ فارسی هم پیدا می‌شود
    assertTrue(LocaleText.phoneMatches("+93799123456", "0799"))
    assertTrue(LocaleText.phoneMatches("+93799123456", "۰۷۹۹۱۲"))
    assertTrue(LocaleText.phoneMatches("+93799123456", "799 123"))
    assertTrue(LocaleText.phoneMatches("0799123456", "۰۷۹۹"))
    assertFalse(LocaleText.phoneMatches("+93799123456", "0788"))
    assertFalse(LocaleText.phoneMatches("+93799123456", "احمد"))
    assertFalse(LocaleText.phoneMatches("", "07"))
  }

  @Test
  fun `قرض دار و تامین کننده تلفن را با قالب +93 ذخیره می کنند و مقدار قدیمی خوانده می شود`() {
    var seq = 0
    val newId = { "id${seq++}" }
    val base = ShopData(debtors = listOf(Debtor(id = "old", name = "احمد", phone = "0700 12")))
    val added = DebtorEngine.add(base, DebtorEngine.DebtorDraft(name = "محمود", phone = "0799 123 456"), 1, newId)
      as DebtorEngine.Result.Ok
    assertEquals("+93799123456", added.data.debtors.first { it.id == added.id }.phone)
    //  مقدارِ قدیمیِ دیگران دست نمی‌خورد
    assertEquals("0700 12", added.data.debtors.first { it.id == "old" }.phone)
    val edited = DebtorEngine.edit(added.data, "old", DebtorEngine.DebtorDraft(name = "احمد", phone = "۰۷۸۸۱۲۳۴۵۶")) as DebtorEngine.Result.Ok
    assertEquals("+93788123456", edited.data.debtors.first { it.id == "old" }.phone)

    val s = LedgerEngine.addSupplier(ShopData(), LedgerEngine.SupplierDraft(name = "شرکت نور", phone = "93 700 000 001"), 1, newId)
      as LedgerEngine.Result.Ok
    assertEquals("+93700000001", s.data.suppliers.single().phone)
    val e = LedgerEngine.editSupplier(s.data, s.data.suppliers.single().id, LedgerEngine.SupplierDraft(name = "شرکت نور", phone = "  "))
      as LedgerEngine.Result.Ok
    assertEquals("", e.data.suppliers.single().phone)
  }
}
