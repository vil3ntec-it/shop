package ir.vil3ntec.tohid.core.i18n

import ir.vil3ntec.tohid.core.calendar.SolarHijri
import ir.vil3ntec.tohid.core.text.LocaleFormat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  زبان، جهت، رقم، پول و تاریخ برای هر سه زبان. فارسی با پیش‌فرض‌ها باید
 *  **دقیقاً** همان خروجیِ دیروز را بدهد.
 */
class I18nFormatTest {

  private val latn = LocaleFormat.DIGITS_LATN
  private val faDigits = LocaleFormat.DIGITS_FA

  @After fun reset() { I18n.source = { Lang.FA } }

  /* ---------------------------- زبان و جهت ---------------------------- */

  @Test
  fun `فارسی پیش‌فرض است و هر کدِ ناشناخته فارسی می‌شود`() {
    assertEquals(Lang.FA, Lang.of(null))
    assertEquals(Lang.FA, Lang.of(""))
    assertEquals(Lang.FA, Lang.of("de"))
    assertEquals(Lang.EN, Lang.of("en"))
    assertEquals(Lang.PS, Lang.of("ps"))
    assertEquals(Lang.FA, I18n.lang)
  }

  @Test
  fun `فارسی و پشتو راست‌به‌چپ، انگلیسی چپ‌به‌راست`() {
    assertTrue(Lang.FA.rtl)
    assertTrue(Lang.PS.rtl)
    assertFalse(Lang.EN.rtl)
    assertEquals(listOf("فارسی", "English", "پښتو"), Lang.entries.map { it.native })
  }

  /* -------------------------------- رقم -------------------------------- */

  @Test
  fun `انگلیسی همیشه رقمِ لاتین، فارسی و پشتو انتخابِ دکان`() {
    assertEquals(latn, I18n.screenDigits(Lang.EN, faDigits))
    assertEquals(latn, I18n.screenDigits(Lang.EN, latn))
    assertEquals(faDigits, I18n.screenDigits(Lang.FA, faDigits))
    assertEquals(faDigits, I18n.screenDigits(Lang.PS, faDigits))
    assertEquals(latn, I18n.screenDigits(Lang.PS, latn))
    assertEquals("1,234,567", I18n.number(1234567, latn))
    assertEquals("۱٬۲۳۴٬۵۶۷", I18n.number(1234567, faDigits))
  }

  @Test
  fun `مفرد و جمع`() {
    assertEquals("item", pl(1, "item", "items"))
    assertEquals("items", pl(0, "item", "items"))
    assertEquals("items", pl(2L, "item", "items"))
    assertEquals("item", pl("1", "item", "items"))
    assertEquals("items", pl("1,250", "item", "items"))
    assertEquals("items", pl(1.5, "item", "items"))
    assertEquals("items", pl("—", "item", "items"))
  }

  /* -------------------------------- پول -------------------------------- */

  @Test
  fun `فارسی دقیقاً همان مبلغِ دیروز`() {
    for (v in listOf(0.0, 1250.0, -980.4, 1234567.5)) for (d in listOf(latn, faDigits)) for (s in listOf(LocaleFormat.SYMBOL_WORD, LocaleFormat.SYMBOL_SIGN)) {
      assertEquals(LocaleFormat.formatAfghani(v, s, d), I18n.money(v, Lang.FA, d, s))
    }
    assertEquals("1,250 افغانی", I18n.money(1250.0, Lang.FA, latn))
  }

  @Test
  fun `واژهٔ پول در هر زبان و نمادِ ؋ یکی`() {
    assertEquals("افغانی", I18n.currencyWord(Lang.FA))
    assertEquals("AFN", I18n.currencyWord(Lang.EN))
    assertEquals("افغانۍ", I18n.currencyWord(Lang.PS))
    assertEquals("1,250 AFN", I18n.money(1250.0, Lang.EN, faDigits))
    assertEquals("۱٬۲۵۰ افغانۍ", I18n.money(1250.0, Lang.PS, faDigits))
    assertEquals("1,250 افغانۍ", I18n.money(1250.0, Lang.PS, latn))
    assertEquals("1,250 ؋", I18n.money(1250.0, Lang.EN, latn, LocaleFormat.SYMBOL_SIGN))
    assertEquals("1,250", I18n.money(1250.0, Lang.EN, latn, LocaleFormat.SYMBOL_NONE))
  }

  /* -------------------------------- تاریخ -------------------------------- */

  @Test
  fun `فارسی و پشتو خورشیدی، انگلیسی میلادی — با تقویمِ خودکار`() {
    val iso = "2026-10-20"
    val solar = SolarHijri.formatNumeric(iso, latn)
    assertEquals(solar, I18n.dateNumeric(iso, Lang.FA, I18n.CAL_AUTO))
    assertEquals(solar, I18n.dateNumeric(iso, Lang.PS, I18n.CAL_AUTO))
    assertEquals("2026-10-20", I18n.dateNumeric(iso, Lang.EN, I18n.CAL_AUTO))
    //  انتخابِ دستی همه را می‌برد
    assertEquals(solar, I18n.dateNumeric(iso, Lang.EN, I18n.CAL_SOLAR))
    assertEquals("2026-10-20", I18n.dateNumeric(iso, Lang.FA, I18n.CAL_GREGORIAN))
    //  ساعت پس از تاریخ هم خوانده می‌شود؛ تاریخِ ناخوانا همان‌طور برمی‌گردد
    assertEquals("2026-10-20", I18n.dateNumeric("2026-10-20T08:00:00Z", Lang.EN, I18n.CAL_AUTO))
    assertEquals("نامعلوم", I18n.dateNumeric("نامعلوم", Lang.EN, I18n.CAL_AUTO))
    assertEquals("نامعلوم", I18n.dateNumeric("نامعلوم", Lang.FA, I18n.CAL_AUTO))
    assertEquals(I18n.CAL_AUTO, I18n.calendarOf(null))
    assertEquals(I18n.CAL_AUTO, I18n.calendarOf("lunar"))
  }

  @Test
  fun `نامِ ماه — دری، پشتو، انگلیسی و میلادی`() {
    val iso = "2026-10-20"
    val j = SolarHijri.toSolar(iso)!!
    val i = j.month - 1
    assertEquals("${j.day} ${SolarHijri.MONTHS_FA[i]} ${j.year}", I18n.dateLong(iso, Lang.FA, I18n.CAL_AUTO, "fa"))
    //  فارسی با انتخابِ «ماهِ پشتو» — همان رفتارِ LOC-01
    assertEquals("${j.day} ${SolarHijri.MONTHS_PS[i]} ${j.year}", I18n.dateLong(iso, Lang.FA, I18n.CAL_AUTO, "ps"))
    //  پشتو همیشه وری، غویی…، حتی اگر انتخابِ ماهِ فارسی دری مانده باشد
    assertEquals("${j.day} ${SolarHijri.MONTHS_PS[i]} ${j.year}", I18n.dateLong(iso, Lang.PS, I18n.CAL_AUTO, "fa"))
    assertEquals("20 October 2026", I18n.dateLong(iso, Lang.EN, I18n.CAL_AUTO, "fa"))
    assertEquals("${j.day} ${I18n.SOLAR_MONTHS_EN[i]} ${j.year}", I18n.dateLong(iso, Lang.EN, I18n.CAL_SOLAR, "fa"))
    assertEquals("20 اکتوبر 2026", I18n.dateLong(iso, Lang.FA, I18n.CAL_GREGORIAN, "fa"))
    //  رقمِ فارسی برای پشتو، هرگز برای انگلیسی
    assertEquals(LocaleFormat.displayDigits("${j.day}", faDigits) + " " + SolarHijri.MONTHS_PS[i] + " " + LocaleFormat.displayDigits("${j.year}", faDigits),
      I18n.dateLong(iso, Lang.PS, I18n.CAL_AUTO, "fa", faDigits))
    assertEquals("20 October 2026", I18n.dateLong(iso, Lang.EN, I18n.CAL_AUTO, "fa", faDigits))
    //  همان شکلِ SolarHijri.formatLong برای فارسی
    assertEquals(SolarHijri.formatLong(iso, "fa", latn), I18n.dateLong(iso, Lang.FA, I18n.CAL_AUTO, "fa"))
  }

  @Test
  fun `فهرستِ ماه‌ها برای برچسب`() {
    assertEquals(SolarHijri.MONTHS_FA, I18n.solarMonths(Lang.FA, "fa"))
    assertEquals(SolarHijri.MONTHS_PS, I18n.solarMonths(Lang.FA, "ps"))
    assertEquals(SolarHijri.MONTHS_PS, I18n.solarMonths(Lang.PS, "fa"))
    assertEquals(I18n.SOLAR_MONTHS_EN, I18n.solarMonths(Lang.EN, "ps"))
    assertEquals("وری", SolarHijri.MONTHS_PS[0])
    assertEquals("حمل", SolarHijri.MONTHS_FA[0])
    assertEquals(12, I18n.SOLAR_MONTHS_EN.size)
    assertEquals(12, I18n.GREGORIAN_MONTHS_PS.size)
    assertEquals(12, I18n.GREGORIAN_MONTHS_FA.size)
  }

  /* ------------------------------ رنگِ پیام ------------------------------ */

  @Test
  fun `رنگِ پیامِ فارسی همان قاعدهٔ پیشین`() {
    fun old(m: String) = when {
      m.contains("نشد") || m.contains("نیست") || m.contains("نبود") || m.contains("ناموفق") || m.contains("خطا") || m.contains("معتبر") -> I18n.Tone.ERROR
      m.contains("شد") || m.contains("موفق") -> I18n.Tone.SUCCESS
      else -> I18n.Tone.INFO
    }
    val samples = listOf(
      "پشتیبان ساخته شد", "فایل ساخته نشد: x", "کد شاگرد کپی شد", "سبد خالی است", "نمونهٔ تازه",
      "عدد درست نیست", "در حالِ بررسی…", "کالا به سبد اضافه شد", "۳ ردیف بایگانی شد",
    )
    for (m in samples) assertEquals(m, old(m), I18n.toneOf(m, Lang.FA))
  }

  @Test
  fun `رنگِ پیامِ انگلیسی و پشتو`() {
    assertEquals(I18n.Tone.SUCCESS, I18n.toneOf("Backup created", Lang.EN))
    assertEquals(I18n.Tone.ERROR, I18n.toneOf("The file wasn't created: disk full", Lang.EN))
    assertEquals(I18n.Tone.ERROR, I18n.toneOf("Export failed: x", Lang.EN))
    assertEquals(I18n.Tone.INFO, I18n.toneOf("Checking…", Lang.EN))
    assertEquals(I18n.Tone.SUCCESS, I18n.toneOf("بیک‌اپ جوړ شو", Lang.PS))
    assertEquals(I18n.Tone.ERROR, I18n.toneOf("دوتنه جوړه نه شوه: x", Lang.PS))
    assertEquals(I18n.Tone.ERROR, I18n.toneOf("ایمیل ونه لېږل شو", Lang.PS))
    //  «بدلونونه» نفی نیست
    assertEquals(I18n.Tone.SUCCESS, I18n.toneOf("بدلونونه ثبت شول", Lang.PS))
    //  پیامی که هنوز فارسی است، در انگلیسی هم رنگِ درستش را می‌گیرد
    assertEquals(I18n.Tone.ERROR, I18n.toneOf("فایل ساخته نشد", Lang.EN))
  }
}
