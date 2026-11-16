package ir.vil3ntec.tohid.core.purchase

import ir.vil3ntec.tohid.core.purchase.InvoiceOcr.Decision
import ir.vil3ntec.tohid.core.purchase.InvoiceOcr.Flag
import ir.vil3ntec.tohid.core.purchase.InvoiceOcr.Line
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  PUR-12 / AI-10 — خواندنِ ردیف‌های فاکتور از متنِ OCR، تطبیقِ کالا و تأییدِ ردیف‌به‌ردیف ([InvoiceOcr]) روی نمونه‌های
 *  [InvoiceOcrFixtures] (یکی خروجیِ واقعیِ Tesseract). سنجهٔ اصلی: هیچ عددی ساخته نمی‌شود و هیچ ردیفی بی تأیید نمی‌رود.
 */
class InvoiceOcrTest {

  private val index = InvoiceOcr.Index(InvoiceOcrFixtures.CATALOG)

  @Test fun numbersAreTolerantButNeverGuessed() {
    assertEquals(listOf(1250.0), InvoiceOcr.numberValues("۱٬۲۵۰"))
    assertEquals(listOf(1250.0), InvoiceOcr.numberValues("1,250"))
    assertEquals(listOf(9500.0), InvoiceOcr.numberValues("٩,۵۰۰"))   // رقمِ عربیِ «٩» کنارِ فارسی
    assertEquals(listOf(1250000.0), InvoiceOcr.numberValues("1.250.000"))
    assertEquals(listOf(1250000.0), InvoiceOcr.numberValues("۱،۲۵۰،۰۰۰"))
    assertEquals(listOf(2.5), InvoiceOcr.numberValues("۲٫۵"))
    assertEquals(listOf(2.5), InvoiceOcr.numberValues("2,5"))
    assertEquals(listOf(2.5), InvoiceOcr.numberValues("۲/۵"))
    assertEquals(listOf(1250.5), InvoiceOcr.numberValues("1,250.50"))
    assertEquals(listOf(1250.5), InvoiceOcr.numberValues("1.250,50"))
    assertEquals(listOf(1250.5), InvoiceOcr.numberValues("۱٬۲۵۰٫۵"))
    //  «1.250» دو معنا دارد ⇒ هر دو، تا سنجهٔ جمع یکی را برگزیند
    assertEquals(listOf(1250.0, 1.25), InvoiceOcr.numberValues("1.250"))
    for (bad in listOf("12,34,5", "1,2,3", "۱٫۲٫۳", "", ",5", "5,", "1.2,3.4")) assertEquals(emptyList(), InvoiceOcr.numberValues(bad), bad)
    assertEquals("1250", InvoiceOcr.text(1250.0))
    assertEquals("2.5", InvoiceOcr.text(2.5))
    assertEquals("0.125", InvoiceOcr.text(0.125))
    assertEquals("12345678.5", InvoiceOcr.text(12345678.5))
    assertEquals(2.5, InvoiceOcr.parseTyped("۲٫۵"))
    assertEquals(1.25, InvoiceOcr.parseTyped("1.250"), "در کادرِ بازبینی «.» اعشار است")
    assertEquals(1250.0, InvoiceOcr.parseTyped("۱٬۲۵۰"))
    assertNull(InvoiceOcr.parseTyped("دو"))
    assertNull(InvoiceOcr.parseTyped(""))
  }

  @Test fun realTesseractInvoiceGivesOnlyRowsThatAddUp() {
    val p = InvoiceOcr.parse(InvoiceOcrFixtures.REAL_TESSERACT)
    assertEquals(listOf("برنج سیلا ۵ کیلویی", "روغن آفتابگردان ۵ لیتری", "چای سبز", "شکر سفید"), p.rows.map { it.name })
    val (rice, oil, tea, sugar) = p.rows
    //  ستونِ «جمع» پیش از «فی» آمد (پارهٔ LTR) — سنجهٔ مقدار × فی همان را درست جا داد
    assertEquals(Triple(10.0, 950.0, 9500.0), Triple(rice.qty, rice.price, rice.total))
    assertEquals(Triple(4.0, 1250.0, 5000.0), Triple(oil.qty, oil.price, oil.total))
    assertEquals(Triple(20.0, 85.0, 1700.0), Triple(sugar.qty, sugar.price, sugar.total))
    //  «۱٬۳۲۰» ⇒ «۱۱۳۲۰»: ۶ × ۲۲۰ نمی‌خواند ⇒ هیچ عددی، با نشان و متنِ خام
    assertEquals(Triple(null, null, null), Triple(tea.qty, tea.price, tea.total))
    assertEquals(setOf(Flag.MISMATCH), tea.flags)
    assertEquals("۳ چای سبز ۶ ۲۲۰ ۱۱۳۲۰", tea.raw)
    //  سرخط ترتیبِ ستون را داد ⇒ هیچ حدسی؛ خطِ عنوان، شماره/تاریخ و جمع ردیف نیستند
    assertTrue(p.rows.none { Flag.ORDER_GUESSED in it.flags })
    assertEquals(17520.0, p.printedTotal)
    assertEquals(0, p.dropped)
  }

  @Test fun dariPriceFirstColumnsDiscountAndGluedNames() {
    val p = InvoiceOcr.parse(InvoiceOcrFixtures.DARI_PRICE_FIRST)
    assertEquals(listOf("آرد سفید ۴۹ کیلویی", "روغن مایع 16 لیتری", "صابون لوکس", "نسکافه 3in1"), p.rows.map { it.name })
    //  «قیمت فی» پیش از «تعداد» در سرخط ⇒ عددِ اول فی است
    assertEquals(listOf(5.0, 2.0, 24.0, 10.0), p.rows.map { it.qty })
    assertEquals(listOf(2450.0, 3100.0, 35.0, 12.5), p.rows.map { it.price })
    assertEquals(listOf(12250.0, 6200.0, 840.0, 125.0), p.rows.map { it.total })
    assertTrue(p.rows.all { it.flags.isEmpty() }, p.rows.map { it.flags }.toString())
    //  «تخفیف» ردیف نیست؛ جمعِ چاپی پس از تخفیف
    assertEquals(19115.0, p.printedTotal)
  }

  @Test fun noHeaderWrappedLineLowConfidenceAndAddressAreHandled() {
    val p = InvoiceOcr.parse(InvoiceOcrFixtures.NO_HEADER)
    assertEquals(listOf("شیر پاستوریزه یک لیتری", "ماست چکیده ۱ کیلویی", "پنیر"), p.rows.map { it.name })
    val (milk, yogurt, cheese) = p.rows
    //  نام در یک خط و عددها در خطِ بعد ⇒ یک ردیف
    assertEquals(Triple(12.0, 45.0, 540.0), Triple(milk.qty, milk.price, milk.total))
    assertTrue(Flag.ORDER_GUESSED in milk.flags)
    assertEquals(setOf(Flag.ORDER_GUESSED, Flag.LOW_CONFIDENCE), yogurt.flags)
    //  دو عدد ⇒ نمی‌شود سنجید ⇒ خالی
    assertEquals(setOf(Flag.MISMATCH), cheese.flags)
    assertNull(cheese.qty)

    val r = InvoiceOcr.review(p, index)
    //  بی سرخط، قیمتِ خریدِ کالای تطبیق‌خورده جای مقدار و فی را روشن کرد (ماست: ۱۴۰ فی است، نه مقدار)
    assertEquals(listOf("12" to "45", "3" to "140", "" to ""), r.rows.map { it.qty to it.price })
    assertTrue(r.rows.take(2).none { Flag.ORDER_GUESSED in it.flags })
    assertEquals(listOf("p-milk", "p-yogurt", "p-cheese"), r.rows.map { it.productId })
  }

  @Test fun ambiguousSeparatorIsResolvedOnlyByTheRowArithmetic() {
    val p = InvoiceOcr.parse(listOf(Line("ردیف شرح تعداد فی جمع"), Line("زعفران ۲ 1.250 2.5"), Line("قند ۳ 1.250 3,750")))
    assertEquals(listOf(1.25, 1250.0), p.rows.map { it.price })
    assertEquals(listOf(2.5, 3750.0), p.rows.map { it.total })
  }

  @Test fun arabicLettersZwnjAndPrintedBarcodeMatchTheCatalog() {
    val r = InvoiceOcr.review(InvoiceOcr.parse(InvoiceOcrFixtures.ARABIC_LETTERS), index)
    assertEquals(listOf("p-biscuit", "p-tea", "p-beans"), r.rows.map { it.productId })
    //  بارکدِ چاپی قطعی است، حتی با نامِ دیگر
    assertEquals(1.0, r.rows[0].choices.single().score)
    assertEquals(1.0, r.rows[1].choices.first().score, "چاي‌سبز = چای‌سبز")
    assertTrue(r.rows.none { Flag.NO_MATCH in it.flags || Flag.WEAK_MATCH in it.flags })
    assertEquals(1.0, InvoiceOcr.similarity("كنسرو لوبيا", "کنسرو لوبیا"))
    assertEquals(1.0, InvoiceOcr.similarity("چای‌سبز", "چای سبز"))
  }

  @Test fun reviewSuggestsButNeverConfirmsOrCreatesProducts() {
    val r0 = InvoiceOcr.review(InvoiceOcr.parse(InvoiceOcrFixtures.REAL_TESSERACT), index)
    assertEquals(listOf("p-rice", "p-oil", "p-tea", "p-sugar"), r0.rows.map { it.productId })
    assertTrue(Flag.WEAK_MATCH in r0.rows[3].flags, "«شکر سفید» ≈ «شکر»: فقط شبیه")
    //  هیچ ردیفی خودبه‌خود تأیید نشده؛ هیچ چیز به فاکتور نمی‌رود
    assertTrue(r0.rows.all { it.decision == Decision.PENDING })
    assertEquals(emptyList(), InvoiceOcr.accepted(r0))
    //  ردیفِ ناخوانا تأییدشدنی نیست تا کاربر مقدار و فی را بنویسد
    var r = InvoiceOcr.confirm(r0, 2)
    assertEquals(Decision.PENDING, r.rows[2].decision)
    r = InvoiceOcr.setPrice(InvoiceOcr.setQty(r, 2, "۶"), 2, "۲۲۰")
    r = InvoiceOcr.confirm(r, 2)
    r = InvoiceOcr.confirm(r, 0)
    r = InvoiceOcr.skip(r, 1)
    assertEquals(
      listOf(InvoiceOcr.Accepted("p-rice", 10.0, 950.0), InvoiceOcr.Accepted("p-tea", 6.0, 220.0)),
      InvoiceOcr.accepted(r),
    )
    assertEquals(9500.0 + 1320.0, InvoiceOcr.confirmedTotal(r))
    assertEquals(2, r.confirmed)
    assertEquals(1, r.pending)
    //  ویرایشِ ردیفِ تأییدشده تأیید را برمی‌دارد
    r = InvoiceOcr.setQty(r, 0, "11")
    assertEquals(Decision.PENDING, r.rows[0].decision)
    assertEquals(listOf("p-tea"), InvoiceOcr.accepted(r).map { it.productId })
    //  بی کالا تأیید نمی‌شود — کالای تازه هرگز ساخته نمی‌شود
    r = InvoiceOcr.confirm(InvoiceOcr.pick(r, 3, null), 3)
    assertEquals(Decision.PENDING, r.rows[3].decision)
    assertFalse(InvoiceOcr.canConfirm(r.rows[3]))
    //  رد و برگشت
    r = InvoiceOcr.undo(r, 1)
    assertEquals(Decision.PENDING, r.rows[1].decision)
  }

  @Test fun unknownNamesAreFlaggedAndRowsAreCapped() {
    val r = InvoiceOcr.review(InvoiceOcr.parse(listOf(Line("زردچوبه هندی ۲ ۴۰ ۸۰"))), index)
    assertNull(r.rows.single().productId)
    assertTrue(Flag.NO_MATCH in r.rows.single().flags)
    val many = (1..60).map { Line("کالای شماره ${it}م ۲ ۳ ۶") }
    val p = InvoiceOcr.parse(many)
    assertEquals(InvoiceOcr.MAX_ROWS, p.rows.size)
    assertEquals(10, p.dropped)
  }

  @Test fun serverJsonIsReadStrictly() {
    val cfg = InvoiceOcr.config(Json.parseToJsonElement(
      """{"enabled":true,"permitted":true,"paid":"true","available":false,"missing":["tesseract-ocr-fas","rm -rf /",3]}""",
    ).jsonObject)
    assertTrue(cfg.enabled && cfg.permitted && cfg.visible)
    assertFalse(cfg.paid, "رشتهٔ «true» روشن نیست")
    assertEquals(listOf("tesseract-ocr-fas"), cfg.missing)
    assertFalse(InvoiceOcr.config(Json.parseToJsonElement("""{"enabled":false}""").jsonObject).visible)
    val lines = InvoiceOcr.lines(Json.parseToJsonElement(
      """{"lines":[{"text":" برنج ۲ ۳ ۶ ","conf":88},{"text":""},{"conf":4},{"text":"x","conf":400},"bad"]}""",
    ).jsonObject)
    assertEquals(listOf(Line("برنج ۲ ۳ ۶", 88), Line("x", 100)), lines)
  }
}
