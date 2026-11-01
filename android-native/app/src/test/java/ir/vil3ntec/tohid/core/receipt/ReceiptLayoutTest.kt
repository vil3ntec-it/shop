package ir.vil3ntec.tohid.core.receipt

import ir.vil3ntec.tohid.core.hw.MonoImage
import ir.vil3ntec.tohid.core.hw.PrinterProfile
import ir.vil3ntec.tohid.core.qr.QrCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  چیدمانِ فاکتورِ نسخهٔ ۲ (RECEIPT-V2 §۱.۳)، رسترِ یک‌بیتی و PDF — با قالبِ ساده‌ای که
 *  عدد را همان‌طور می‌نویسد، تا ترتیب و محتوای بلوک‌ها روشن سنجیده شود.
 */
class ReceiptLayoutTest {

  private val fmt = object : ReceiptFormat {
    override fun money(afn: Double) = "${afn.toLong()} افغانی"
    override fun amount(afn: Double) = afn.toLong().toString()
    override fun qty(q: Double) = if (q == Math.floor(q)) q.toLong().toString() else q.toString()
    override fun digits(text: String) = text
    override fun phone(text: String) = "<$text>"
    override fun date(iso: String) = "1405/08/02"
  }

  private val url = "https://api.example.com/r/d2jcj71t/3t3gq9p07z23q5tf2b58cggxv6"

  private fun input(
    items: List<LineView> = listOf(
      LineView("چای سبز", 3.0, "بسته", 150.0, 450.0),
      LineView("برنج", 1.5, "کیلو", 100.0, 150.0, returnedQty = 0.5, listPrice = 120.0),
    ),
    remaining: Double = 0.0,
    qrUrl: String? = url,
    cancelled: Boolean = false,
    logo: MonoImage? = null,
  ) = ReceiptInput(
    shop = ShopHeader("سوپرمارکتِ نمونه", "کابل، شهرِ نو", "0799 123 456", ""),
    invoiceNo = "B1-154", dateIso = "2026-10-24", time = "14:35", cashier = "احمد", kind = "نقدی",
    cancelled = cancelled, items = items, subtotal = 600.0,
    discounts = listOf(DiscountLine("تخفیفِ ویژه", 30.0), DiscountLine("تخفیف", 0.0)),
    total = 570.0, payments = emptyList(), paid = 570.0, change = 30.0, remaining = remaining,
    debtorName = "محمود", qrUrl = qrUrl, logo = logo,
  )

  private fun texts(doc: ReceiptDoc) = doc.blocks.map {
    when (it) {
      is ReceiptBlock.Text -> "T:${it.text}"
      is ReceiptBlock.Row -> "R:${it.label}=${it.value}"
      is ReceiptBlock.Item -> "I:${it.name}|${it.detail}|${it.total}|${it.notes.joinToString(";")}"
      is ReceiptBlock.Rule -> if (it.dashed) "--" else "=="
      is ReceiptBlock.Gap -> "gap"
      is ReceiptBlock.Logo -> "logo"
      is ReceiptBlock.Qr -> "qr:${it.url}"
    }
  }

  @Test fun `ترتیبِ کاملِ فاکتور، همان فیلدهای ذخیره‌شده`() {
    val doc = ReceiptLayout.build(input(), PrinterProfile.WIDTH_58MM, fmt)
    assertEquals(ReceiptMetrics.MM58, doc.metrics)
    assertEquals(listOf(
      "T:سوپرمارکتِ نمونه", "T:کابل، شهرِ نو", "T:تلفن: <0799 123 456>", "--",
      "R:شمارهٔ فاکتور=#B1-154", "R:تاریخ=1405/08/02 — 14:35", "R:فروشنده=احمد", "R:نوع=نقدی", "--",
      "I:چای سبز|3 بسته × 150|450|", "I:برنج|1.5 کیلو × 100|150|قیمتِ اصلی: 120;مرجوعی: 0.5", "--",
      "T:تعداد اقلام: 2 ردیف",
      "R:جمع اقلام=600 افغانی", "R:تخفیفِ ویژه=−30 افغانی", "==", "R:مبلغ نهایی=570 افغانی",
      "R:پرداختی=570 افغانی", "R:پولِ پس=30 افغانی", "--",
      "T:برای دیدنِ این خرید اسکن کنید", "gap", "qr:$url", "gap", "T:تشکر از خرید شما",
    ), texts(doc))
    assertEquals(TextStyle.TOTAL, (doc.blocks.first { it is ReceiptBlock.Row && it.label == "مبلغ نهایی" } as ReceiptBlock.Row).style)
  }

  @Test fun `LOC-01 تاریخِ قمری فقط وقتی قالب می‌دهد، درست زیرِ تاریخ؛ خالی چاپ نمی‌شود`() {
    val plain = texts(ReceiptLayout.build(input(), PrinterProfile.WIDTH_58MM, fmt))
    val lunar = object : ReceiptFormat by fmt {
      override fun lunarDate(iso: String) = if (iso == "2026-10-24") "12 جمادی\u200cالاول 1448" else null
    }
    val t = texts(ReceiptLayout.build(input(), PrinterProfile.WIDTH_58MM, lunar))
    val i = t.indexOf("R:تاریخ=1405/08/02 — 14:35")
    assertEquals("R:تاریخِ قمری=12 جمادی\u200cالاول 1448", t[i + 1])
    assertEquals(plain, t.filterIndexed { k, _ -> k != i + 1 })
    val blank = object : ReceiptFormat by fmt { override fun lunarDate(iso: String) = " " }
    assertEquals("خالی ⇒ بی ردیف", plain, texts(ReceiptLayout.build(input(), PrinterProfile.WIDTH_58MM, blank)))
  }

  @Test fun `ردیفِ آگاهیِ تخفیفِ خودکار زیرِ «جمع اقلام» — بی «−» و بی کسر؛ صفر نمی‌آید (پیگیریِ #307)`() {
    val base = input().copy(discounts = emptyList(), total = 600.0)
    val t = texts(ReceiptLayout.build(base.copy(included = listOf(DiscountLine("شاملِ تخفیفِ خودکار (ده درصد)", 50.0))), PrinterProfile.WIDTH_58MM, fmt))
    val i = t.indexOf("R:جمع اقلام=600 افغانی")
    assertEquals("R:شاملِ تخفیفِ خودکار (ده درصد)=50 افغانی", t[i + 1])
    assertEquals("R:مبلغ نهایی=600 افغانی", t[i + 3])
    //  بی ردیف ⇒ همان فاکتورِ پیشین
    assertEquals(texts(ReceiptLayout.build(base, PrinterProfile.WIDTH_58MM, fmt)),
      texts(ReceiptLayout.build(base.copy(included = listOf(DiscountLine("x", 0.0))), PrinterProfile.WIDTH_58MM, fmt)))
  }

  @Test fun `شمارِ عدد فقط وقتی همه دانه‌ای‌اند؛ نسیه با نامِ قرض‌دار؛ بی QR؛ لغو و لوگو`() {
    val whole = listOf(LineView("الف", 2.0, "", 10.0, 20.0), LineView("ب", 3.0, "", 10.0, 30.0))
    val logo = MonoImage(40, 20)
    val t = texts(ReceiptLayout.build(input(items = whole, remaining = 200.0, qrUrl = null, cancelled = true, logo = logo), PrinterProfile.WIDTH_80MM, fmt))
    assertEquals(listOf("logo", "gap", "T:سوپرمارکتِ نمونه"), t.take(3))
    assertTrue(t.contains("T:لغو شد"))
    assertTrue(t.contains("T:تعداد اقلام: 2 ردیف · 5 عدد"))
    assertTrue(t.contains("R:باقی‌مانده (نسیه)=200 افغانی"))
    assertTrue(t.contains("R:قرض‌دار=محمود"))
    assertFalse(t.any { it.startsWith("qr:") || it.contains("اسکن") })
    //  بی بدهی ⇒ نامِ قرض‌دار هرگز
    assertFalse(texts(ReceiptLayout.build(input(), PrinterProfile.WIDTH_80MM, fmt)).any { it.contains("محمود") })
  }

  @Test fun `نشانیِ بلندتر از ظرفیتِ QR — فاکتور بی QR چاپ می‌شود، نمی‌شکند`() {
    val long = "https://api.example.com/r/d2jcj71t/" + "x".repeat(200)
    assertTrue(runCatching { QrCode.encode(long) }.isFailure)
    val t = texts(ReceiptLayout.build(input(qrUrl = long), PrinterProfile.WIDTH_58MM, fmt))
    assertFalse(t.any { it.startsWith("qr:") || it.contains("اسکن") })
    assertEquals("T:تشکر از خرید شما", t.last())
  }

  @Test fun `رستر — پهنای کاغذ، QR با حاشیهٔ خالی وسطِ کاغذ و خطِ نقطه‌چین`() {
    val doc = ReceiptLayout.build(input(), PrinterProfile.WIDTH_58MM, fmt)
    val textH = 10
    val img = ReceiptRaster.compose(doc) { _, w, _ -> MonoImage(w, textH).also { it.fill(0, 0, w, textH) } }
    assertEquals(PrinterProfile.WIDTH_58MM, img.width)
    val m = doc.metrics
    val modules = QrCode.encode(url).size
    val dots = ReceiptRaster.moduleDots(modules, img.width, m)
    assertEquals(m.qrModule, dots)
    val qr = ReceiptRaster.qr(QrCode.encode(url), dots)
    assertEquals((modules + 2 * QrCode.QUIET_ZONE) * dots, qr.width)
    //  گوشهٔ بالا-چپِ نشانگر سیاه است و حاشیه سفید
    assertTrue(qr[QrCode.QUIET_ZONE * dots, QrCode.QUIET_ZONE * dots])
    assertFalse(qr[QrCode.QUIET_ZONE * dots - 1, QrCode.QUIET_ZONE * dots])
    val texts = doc.blocks.count { it.isText }
    val rules = doc.blocks.filterIsInstance<ReceiptBlock.Rule>().size
    val gaps = doc.blocks.filterIsInstance<ReceiptBlock.Gap>().sumOf { it.dots }
    assertEquals(texts * textH + rules * (m.gap * 2 + m.ruleThick) + gaps + qr.height + m.pad * 3, img.height)
    val rule = ReceiptRaster.rule(img.width, m, dashed = true)
    assertTrue(rule[m.pad, m.gap]); assertFalse(rule[m.pad + m.dashOn, m.gap]); assertFalse(rule[m.pad - 1, m.gap])
  }

  @Test fun `QR در کاغذِ باریک کوچک می‌شود، هرگز بیرون نمی‌زند`() {
    val m = ReceiptMetrics.MM58
    assertEquals(m.qrModule, ReceiptRaster.moduleDots(33, 384, m))
    val narrow = ReceiptRaster.moduleDots(41, 300, m)
    assertTrue((41 + 2 * QrCode.QUIET_ZONE) * narrow <= 300 - 2 * m.pad)
  }

  @Test fun `PDF — سرآیند، جدولِ xref ِ درست و تصویرِ یک‌بیتی`() {
    val img = MonoImage(16, 8).also { it.fill(0, 0, 8, 8) }
    val pdf = ReceiptPdf.single(img)
    val text = String(pdf, Charsets.ISO_8859_1)
    assertTrue(text.startsWith("%PDF-1.4\n"))
    assertTrue(text.trimEnd().endsWith("%%EOF"))
    assertTrue(text.contains("/Width 16 /Height 8 /ColorSpace /DeviceGray /BitsPerComponent 1 /Decode [1 0] /Length 16"))
    assertTrue(text.contains("/MediaBox [0 0 226.77 113.39]"))
    val xref = text.substringAfter("startxref\n").substringBefore("\n").toInt()
    assertTrue(text.startsWith("xref", xref))
    val offsets = text.substring(xref).lines().drop(3).take(5).map { it.take(10).toInt() }
    offsets.forEachIndexed { i, at -> assertTrue("شیءِ ${i + 1}", text.startsWith("${i + 1} 0 obj", at)) }
  }
}
