package ir.vil3ntec.tohid.ui.v3

import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.ShopData
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 *  «روی فاکتور که بزنم همان فاکتور» (`claude/e-rows-63q`) — منطقِ خالصِ ارجاع‌ها (`InvoiceRefs`) و روزِ کوتاهِ سندِ
 *  پس‌تاریخ‌دار (`RowWhen.dayAt`):
 *   • از شرحِ آزاد فقط «فاکتور #n» پیوند است (سری‌دار هم)، بقیه دست‌نخورده و به همان ترتیب؛
 *   • نوشتهٔ ساختهٔ برنامه («از فاکتور #12 · …») همان نوشته است و «فاکتور #12» پیوند، نه «#123»؛
 *   • شمارهٔ یکتا همان فاکتور؛ شمارهٔ دوپهلو ⇒ فاکتورِ همین مشتری؛ شناسهٔ داده بر شماره مقدم؛ ناپیدا ⇒ null.
 */
class InvoiceRefsTest {

  @Before fun fa() { I18n.source = { Lang.FA } }
  @After fun reset() { I18n.source = { Lang.FA } }

  @Test fun splitLinksOnlyTheInvoicePartOfAFreeNote() {
    val p = InvoiceRefs.split("فروش نسیه — فاکتور #12 — بقیه فردا")
    assertEquals(listOf("فروش نسیه — ", "فاکتور #12", " — بقیه فردا"), p.map { it.text })
    assertEquals(listOf(null, "12", null), p.map { it.label })
    assertEquals("فروش نسیه — فاکتور #12 — بقیه فردا", InvoiceRefs.text(p))
    //  سری (F0-03) و دو ارجاع در یک شرح
    val two = InvoiceRefs.split("برگشت از فاکتور #B1-7 و فاکتور #9")
    assertEquals(listOf("B1-7", "9"), two.mapNotNull { it.label })
    //  بی ارجاع ⇒ یک تکهٔ ساده؛ خالی ⇒ هیچ
    assertEquals(listOf("پرداختِ نقد"), InvoiceRefs.split("پرداختِ نقد").map { it.text })
    assertTrue(InvoiceRefs.split("").isEmpty())
    //  شناسهٔ شناخته از دفتر روی همان شماره
    assertEquals("s12", InvoiceRefs.split("فاکتور #12", mapOf("12" to "s12")).single().saleId)
  }

  @Test fun splitAlsoFindsTheAppsOwnWordingInEnglishAndPashto() {
    //  ستونِ سندِ «پایانِ روز» به زبانِ رابط ساخته می‌شود؛ شرحِ داده (فارسی) هم در همان زبان پیوند می‌ماند
    I18n.source = { Lang.EN }
    assertEquals(listOf(null, "B1-12"), InvoiceRefs.split("Refund of Invoice #B1-12").map { it.label })
    assertEquals("12", InvoiceRefs.split("فروش نسیه — فاکتور #12").single { it.isRef }.label)
    I18n.source = { Lang.PS }
    assertEquals("7", InvoiceRefs.split("بل #7").single().label)
    //  «#» بی واژهٔ فاکتور پیوند نیست
    assertTrue(InvoiceRefs.split("#7").none { it.isRef })
  }

  @Test fun linkifyKeepsTheBuiltTextAndLinksTheInvoiceWord() {
    val p = InvoiceRefs.linkify("از فاکتور #12 · ۲۸ میزان", "12", "s12")
    assertEquals(listOf("از ", "فاکتور #12", " · ۲۸ میزان"), p.map { it.text })
    assertEquals("s12", p[1].saleId)
    //  زیرعنوانِ ردیف: فقط «#12» (پیشش «·» است، نه واژه)
    assertEquals(listOf("۱۴:۰۵ · ۳ قلم · ", "#12"), InvoiceRefs.linkify("۱۴:۰۵ · ۳ قلم · #12", "12", "s12").map { it.text })
    //  «#12» درونِ «#123» پیوند نمی‌شود
    assertEquals(listOf("فاکتور #123"), InvoiceRefs.linkify("فاکتور #123", "12", "s12").map { it.text })
  }

  private val book = ShopData(
    debtors = listOf(Debtor(id = "a", name = "احمد"), Debtor(id = "b", name = "بصیر")),
    sales = listOf(
      Sale("only", 5, createdAt = 1),
      //  دو فاکتورِ کهنهٔ بی سری با یک شماره (دو دستگاه)
      Sale("x-old", 12, debtorId = "a", createdAt = 2),
      Sale("x-new", 12, debtorId = "b", createdAt = 3),
      Sale("series", 12, series = "B1", createdAt = 4),
    ),
  )

  @Test fun resolvePrefersTheDebtorsOwnSaleWhenTheNumberIsAmbiguous() {
    assertEquals("only", InvoiceRefs.resolve(book, "5")?.id)
    assertEquals("x-old", InvoiceRefs.resolve(book, "12", debtorId = "a")?.id)
    assertEquals("x-new", InvoiceRefs.resolve(book, "12", debtorId = "b")?.id)
    //  بی مشتری ⇒ تازه‌ترین؛ سری‌دار فاکتورِ دیگری است
    assertEquals("x-new", InvoiceRefs.resolve(book, "12")?.id)
    assertEquals("series", InvoiceRefs.resolve(book, "B1-12")?.id)
    assertNull(InvoiceRefs.resolve(book, "99"))
  }

  @Test fun findUsesTheKnownIdFirstAndFallsBackToTheNumber() {
    assertEquals("x-old", InvoiceRefs.find(book, "12", "x-old", debtorId = "b"))
    //  شناسه‌ای که دیگر در دفتر نیست ⇒ همان شماره
    assertEquals("only", InvoiceRefs.find(book, "5", "gone", null))
    assertNull(InvoiceRefs.find(book, "99", null, "a"))
  }

  /* ─────────────── روزِ کوتاهِ سند (`RowWhen.dayAt`) ─────────────── */

  private val today = "2026-11-15"
  private val noon = 1_794_744_000_000L // 2026-11-15T12:00:00Z

  @Test fun dayAtShowsTheClockOnlyWhenTheMomentIsOnThatDay() {
    assertEquals("امروز 12:00", RowWhen.dayAt(today, noon, today, 0))
    //  بردِ «دیروز» که امروز ثبت شد: بی ساعتِ امروز
    assertEquals("دیروز", RowWhen.dayAt("2026-11-14", noon, today, 0))
    //  منطقهٔ +۴:۳۰ (کابل): همان لحظه ۱۶:۳۰ است
    assertEquals("امروز 16:30", RowWhen.dayAt(today, noon, today, 16_200_000))
    //  بی لحظه ⇒ فقط روز؛ همین سال بی سال
    assertEquals("امروز", RowWhen.dayAt(today, 0, today, 0))
    assertTrue(RowWhen.dayAt("2026-10-20", 0, today, 0).let { !it.contains("1405") && it.isNotBlank() })
    assertTrue(RowWhen.dayAt("2025-10-20", 0, today, 0).contains("1404"))
  }
}
