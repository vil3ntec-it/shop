package ir.vil3ntec.tohid.desktop

import android.graphics.Bitmap
import ir.vil3ntec.tohid.LocaleChoice
import ir.vil3ntec.tohid.core.hw.MonoImage
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.receipt.ReceiptBlock
import ir.vil3ntec.tohid.core.receipt.ReceiptMetrics
import ir.vil3ntec.tohid.core.receipt.TextStyle
import ir.vil3ntec.tohid.core.text.LocaleFormat
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.QuoteBook
import ir.vil3ntec.tohid.data.Quotes
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SaleItem
import ir.vil3ntec.tohid.data.SalePayment
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.print.QuoteFile
import ir.vil3ntec.tohid.print.ReceiptPainter
import ir.vil3ntec.tohid.print.Receipts
import ir.vil3ntec.tohid.print.StatementImage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.awt.Font
import java.io.File

/**
 *  #348 روی کاغذِ واقعی (همان `ReceiptPainter` ِ گوشی روی شیمِ Java2D):
 *   - جهت: فارسی، دری و پشتو برچسب راست و مبلغ چپ (همان پیشین)؛ انگلیسی برچسب چپ و مبلغ راست؛
 *   - هر چهار زبان روی فاکتور، رسیدِ هدیه، PDF و صورت‌حساب کشیده می‌شوند و انگلیسی/پشتو دیگر همان کاغذِ فارسی نیستند؛
 *   - رقم: انتخابِ دکان (انگلیسی همیشه لاتین) — فاکتور و پیش‌فاکتور، فقط عددها، نه نامِ کالا؛
 *   - ایمنیِ چاپگر: هر راهِ کاغذ تصویر است (`EscPos`، رستر یا ستونی) با قلمِ وزیرمتن؛ همهٔ نویسه‌های برچسب‌های هر چهار
 *     زبان در خودِ قلم هست، پس هیچ چاپگری به جدولِ نویسه نیاز ندارد.
 */
class ReceiptLangDeskTest {

  private val sale = Sale(
    id = "s-lang", invoiceNumber = 7, total = 570.0, finalTotal = 570.0, paidAmount = 570.0,
    createdAt = 1L, date = "2026-10-24", paymentMethod = "mixed",
    payments = listOf(
      SalePayment(method = "cash", kind = "cash", amountMinor = 30000, status = "confirmed"),
      SalePayment(method = "hawala", kind = "transfer", amountMinor = 27000, status = "confirmed"),
    ),
  )
  private val d = ShopData(
    sales = listOf(sale),
    saleItems = listOf(SaleItem(id = "i1", saleId = sale.id, name = "Pepsi 330", quantity = 3.0, unitPrice = 190.0, totalPrice = 570.0)),
    debtors = listOf(Debtor(id = "c1", name = "کریمی")),
    products = listOf(Product(id = "p1", name = "Pepsi 330", salePrice = 190.0)),
  )

  @Before fun bind() {
    LocaleChoice.digits // `LocaleChoice` در نخستین خواندن `I18n.source` را می‌گیرد ⇒ پیش از زبانِ آزمون
  }

  @After fun reset() {
    LocaleChoice.setDigits(DesktopContext, LocaleFormat.DIGITS_LATN)
    LocaleChoice.setLang(DesktopContext, Lang.FA)
    I18n.source = { Lang.FA }
  }

  /** همان راهِ برنامه: کلیدِ زبان (`LocaleChoice`) ⇒ نوشته‌ها (`I18n`)، تاریخِ صفحه و رقمِ صفحه با هم */
  private fun lang(l: Lang) {
    LocaleChoice.setLang(DesktopContext, l)
    I18n.source = { LocaleChoice.lang }
  }

  /** پهنهٔ افقیِ نقطه‌های سیاه */
  private fun ink(img: MonoImage): IntRange {
    var lo = img.width
    var hi = -1
    for (y in 0 until img.height) for (x in 0 until img.width) if (img[x, y]) { lo = minOf(lo, x); hi = maxOf(hi, x) }
    return lo..hi
  }

  private fun same(a: Bitmap, b: Bitmap): Boolean {
    if (a.width != b.width || a.height != b.height) return false
    for (y in 0 until a.height) for (x in 0 until a.width) if (a.getPixel(x, y) != b.getPixel(x, y)) return false
    return true
  }

  @Test fun `جهت — راست‌به‌چپ برچسب راست و مبلغ چپ؛ انگلیسی برعکس`() {
    val w = 576
    val half = w / 2
    val m = ReceiptMetrics.MM80
    val painter = ReceiptPainter(DesktopContext)
    for (l in listOf(Lang.FA, Lang.PRS, Lang.PS)) {
      lang(l)
      val label = ink(painter.render(ReceiptBlock.Row(Receipts.labels().total, ""), w, m))
      val value = ink(painter.render(ReceiptBlock.Row("", "570"), w, m))
      assertTrue("$l: برچسب باید راست باشد $label", label.first > half)
      assertTrue("$l: مبلغ باید چپ باشد $value", value.last < half)
    }
    lang(Lang.EN)
    val label = ink(painter.render(ReceiptBlock.Row(Receipts.labels().total, ""), w, m))
    val value = ink(painter.render(ReceiptBlock.Row("", "570"), w, m))
    assertTrue("انگلیسی: برچسب باید چپ باشد $label", label.last < half)
    assertTrue("انگلیسی: مبلغ باید راست باشد $value", value.first > half)
    //  سطرِ آغاز‌چین (یادداشت، شمار) و نامِ قلم هم چپ
    assertTrue(ink(painter.render(ReceiptBlock.Text("Items: 1 line", TextStyle.SMALL, center = false), w, m)).last < half)
    assertTrue(ink(painter.render(ReceiptBlock.Item("Pepsi", "", ""), w, m)).last < half)
  }

  @Test fun `فاکتور، رسیدِ هدیه، PDF و صورت‌حساب در هر چهار زبان کشیده می‌شوند و انگلیسی و پشتو همان کاغذِ فارسی نیستند`() {
    lang(Lang.FA)
    val fa = Receipts.bitmap(DesktopContext, d, sale, 576)
    val faGift = Receipts.giftBitmap(DesktopContext, d, sale, 576)
    val faPdf = Receipts.pdf(DesktopContext, d, sale)
    val faStatement = StatementImage.png(DesktopContext, d, "c1", "2026-10-24")!!
    for (l in listOf(Lang.EN, Lang.PS)) {
      lang(l)
      assertFalse("$l: فاکتور همان فارسی است", same(fa, Receipts.bitmap(DesktopContext, d, sale, 576)))
      assertFalse("$l: رسیدِ هدیه همان فارسی است", same(faGift, Receipts.giftBitmap(DesktopContext, d, sale, 576)))
      val pdf = Receipts.pdf(DesktopContext, d, sale)
      assertEquals("%PDF", String(pdf.copyOfRange(0, 4), Charsets.US_ASCII))
      assertFalse("$l: PDF همان فارسی است", pdf.contentEquals(faPdf))
      assertFalse("$l: صورت‌حساب همان فارسی است", StatementImage.png(DesktopContext, d, "c1", "2026-10-24")!!.contentEquals(faStatement))
    }
    lang(Lang.PRS)
    assertTrue(Receipts.bitmap(DesktopContext, d, sale, 384).width == 384)
  }

  @Test fun `ورودیِ فاکتور — نوع و ردیف‌های پرداخت به زبانِ برنامه، نامِ کالا همان`() {
    lang(Lang.FA)
    val fa = Receipts.input(DesktopContext, d, sale, 576)
    assertEquals(listOf("نقد", "حواله"), fa.payments.map { it.label })
    assertEquals("ترکیبی", fa.kind)
    lang(Lang.EN)
    val en = Receipts.input(DesktopContext, d, sale, 576)
    assertEquals(listOf("Cash", "Transfer"), en.payments.map { it.label })
    assertEquals("Mixed", en.kind)
    assertEquals("Pepsi 330", en.items.single().name)
    lang(Lang.PS)
    assertEquals(listOf("نغد", "حواله"), Receipts.input(DesktopContext, d, sale, 576).payments.map { it.label })
  }

  @Test fun `رقم — انتخابِ دکان روی کاغذ، انگلیسی همیشه لاتین؛ فقط عددها، نه نامِ کالا`() {
    LocaleChoice.setDigits(DesktopContext, LocaleFormat.DIGITS_FA)
    lang(Lang.FA)
    assertEquals("۵۷۰ افغانی", Receipts.format().money(570.0))
    assertEquals("۱۴۰۵/۰۸/۰۲", Receipts.format().date("2026-10-24"))
    lang(Lang.PS)
    assertEquals("۵۷۰ افغانۍ", Receipts.format().money(570.0))
    lang(Lang.EN)
    assertEquals("570 AFN", Receipts.format().money(570.0))
    assertEquals("2026-10-24", Receipts.format().date("2026-10-24"))

    //  پیش‌فاکتور: شماره، تاریخ، مقدار و مبلغ با رقمِ دکان؛ نامِ کالا («Pepsi 330») دست‌نخورده
    val q = (Quotes.create(QuoteBook(), d, listOf(SalesEngine.CartLine("p1", 2.0)), "c1", "تحویل 10 صبح", "2026-11-01", 7, 1000, "q1", "علی") as Quotes.Result.Ok).quote
    lang(Lang.FA)
    val faLines = QuoteFile.lines(q, d).map { it.text }
    assertTrue(faLines.toString(), faLines.any { it.contains("Pepsi 330") && it.contains("۳۸۰") && it.contains("۱۹۰") })
    assertTrue("یادداشت داده است: $faLines", faLines.contains("تحویل 10 صبح"))
    lang(Lang.EN)
    val enLines = QuoteFile.lines(q, d).map { it.text }
    assertTrue(enLines.toString(), enLines.any { it.startsWith("Pepsi 330") && it.contains("380") })
    assertFalse(enLines.toString(), enLines.any { Regex("[۰-۹]").containsMatchIn(it.replace("تحویل 10 صبح", "")) })
  }

  /* ------------------------------ ایمنیِ چاپگر ------------------------------ */

  private fun fontFile(name: String): File {
    var dir: File? = File(System.getProperty("user.dir")).absoluteFile
    while (dir != null) {
      val f = File(dir, "android-native/app/src/main/res/font/$name")
      if (f.isFile) return f
      dir = dir.parentFile
    }
    error("قلم پیدا نشد: $name")
  }

  @Test fun `همهٔ نویسه‌های برچسب‌های چهار زبان در قلمِ کاغذ هست — هیچ چاپگری جدولِ نویسه نمی‌خواهد`() {
    val fonts = listOf("vazirmatn_regular.ttf", "vazirmatn_bold.ttf").map { Font.createFont(Font.TRUETYPE_FONT, fontFile(it)) }
    for (l in Lang.entries) {
      lang(l)
      val r = Receipts.labels()
      val s = Receipts.statementLabels()
      val g = Receipts.giftLabels()
      val text = listOf(r.toString(), s.toString(), g.title, g.note, Receipts.kindOf(sale, r)) + Receipts.paymentLines(sale).map { it.label } +
        Receipts.format().money(1250.0) + Receipts.format().date("2026-10-24")
      //  نیم‌فاصله و نشانه‌های جهت (Cf) نویسهٔ دیدنی نیستند و قلم آن‌ها را نمی‌کشد
      for (f in fonts) for (t in text.map { s -> s.filter { Character.getType(it) != Character.FORMAT.toInt() } }) {
        val bad = f.canDisplayUpTo(t)
        assertEquals("$l (${f.fontName}): «${if (bad >= 0) t[bad] else ' '}» در قلم نیست — $t", -1, bad)
      }
    }
  }
}
