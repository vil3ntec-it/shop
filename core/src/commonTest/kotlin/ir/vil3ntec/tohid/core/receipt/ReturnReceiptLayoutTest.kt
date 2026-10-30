package ir.vil3ntec.tohid.core.receipt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 *  RET-12 — رسیدِ چاپیِ برگشت از همان سندِ ثبت‌شده؛ S13 (برگشت با رسید، ضایعات، بازپرداختِ نقد)
 *  و S15 (تعویض: یک رسید با دو بخش و دریافتِ تفاوت).
 */
class ReturnReceiptLayoutTest {

  /** قالبِ سادهٔ آزمون: عددِ صحیح با «افغانی»؛ رقم‌ها با [persian] فارسی */
  private class Fmt(val persian: Boolean = false) : ReceiptFormat {
    private fun n(v: Double): String = if (v == kotlin.math.floor(v)) v.toLong().toString() else v.toString()
    override fun money(afn: Double) = digits(n(afn)) + " افغانی"
    override fun amount(afn: Double) = digits(n(afn))
    override fun qty(q: Double) = digits(n(q))
    override fun digits(text: String) =
      if (!persian) text else text.map { if (it in '0'..'9') '۰' + (it - '0') else it }.joinToString("")
    override fun phone(text: String) = digits(text)
    override fun date(iso: String) = digits(iso.replace('-', '/'))
  }

  private val shop = ShopHeader("دکانِ آزمون", "کابل، کارتهٔ سه", "0700123456")

  /** S13: ۱ از ۲ واحدِ «ب» (۲۵۰ با ۱۰٪ تخفیف ⇐ ۲۲۵)، مقصدِ ضایعات، بازپرداختِ نقد */
  private val s13 = ReturnReceiptInput(
    shop = shop,
    label = "R-ABC123",
    invoiceNo = "B1-154",
    dateIso = "2026-10-30",
    time = "14:35",
    lines = listOf(ReturnLineView("کالای ب", 1.0, "عدد", 225.0, damaged = true)),
    total = 225.0,
    refunds = listOf(PaymentLine("کم از بدهی", 0.0), PaymentLine("اعتبار فروشگاه", 0.0), PaymentLine("پس‌داده نقد", 225.0)),
    reason = "خراب یا معیوب",
    approvedBy = "مدیر",
    by = "صندوقدار",
  )

  private fun texts(doc: ReceiptDoc) = doc.blocks.filterIsInstance<ReceiptBlock.Text>().map { it.text }
  private fun rows(doc: ReceiptDoc) = doc.blocks.filterIsInstance<ReceiptBlock.Row>()
  private fun indexOf(doc: ReceiptDoc, pred: (ReceiptBlock) -> Boolean) = doc.blocks.indexOfFirst(pred).also { assertTrue(it >= 0) }

  /** S13 — سربرگ، شمارهٔ سند و فاکتورِ اصل، ردیفِ «ضایعات»، مبلغ و سهمِ نقد؛ سهمِ صفر چاپ نمی‌شود */
  @Test
  fun s13ReturnWithReceiptWasteAndCash() {
    val doc = ReturnReceiptLayout.build(s13, 384, Fmt())
    val t = texts(doc)
    assertEquals("دکانِ آزمون", t.first())
    assertTrue("تلفن: 0700123456" in t)
    assertTrue("رسید برگشت R-ABC123" in t)
    assertTrue("از فاکتور #B1-154" in t)
    assertTrue("2026/10/30 — 14:35" in t)
    assertFalse("برگشتِ بی رسید" in t)

    val items = doc.blocks.filterIsInstance<ReceiptBlock.Item>()
    assertEquals(listOf(ReceiptBlock.Item("کالای ب", "1 عدد · ضایعات", "225")), items)

    assertEquals(
      listOf(
        ReceiptBlock.Row("مبلغ برگشت", "225 افغانی", TextStyle.TOTAL),
        ReceiptBlock.Row("پس‌داده نقد", "225 افغانی"),
      ),
      rows(doc),
    )
    //  ترتیب: ردیف ⇐ مبلغ ⇐ نقد ⇐ دلیل ⇐ تأیید ⇐ ثبت ⇐ «سندِ جدا»
    val order = listOf(
      indexOf(doc) { it is ReceiptBlock.Item },
      indexOf(doc) { it is ReceiptBlock.Row && it.label == "مبلغ برگشت" },
      indexOf(doc) { it is ReceiptBlock.Row && it.label == "پس‌داده نقد" },
      indexOf(doc) { it is ReceiptBlock.Text && it.text == "دلیل: خراب یا معیوب" },
      indexOf(doc) { it is ReceiptBlock.Text && it.text == "تأیید: مدیر" },
      indexOf(doc) { it is ReceiptBlock.Text && it.text == "ثبت: صندوقدار" },
      indexOf(doc) { it is ReceiptBlock.Text && it.text == ReturnReceiptLabels.FA.separate },
    )
    assertEquals(order.sorted(), order)
    assertEquals(ReturnReceiptLabels.FA.separate, t.last())
    //  رسیدِ برگشت QR ندارد (کدِ برگشت فقط روی رسیدِ فروش است)
    assertFalse(doc.blocks.any { it is ReceiptBlock.Qr })
  }

  /** بی رسید، به موجودی، بی تأیید و بی نامِ ثبت‌کننده؛ همهٔ مقصدها با هم */
  @Test
  fun noReceiptRestockAndEverySplit() {
    val doc = ReturnReceiptLayout.build(
      s13.copy(
        invoiceNo = null, time = "", approvedBy = " ", by = "",
        lines = listOf(ReturnLineView("کالای الف", 2.0, "", 300.0, damaged = false)),
        total = 300.0,
        refunds = listOf(
          PaymentLine("کم از بدهی", 100.0), PaymentLine("اعتبار فروشگاه", 50.0),
          PaymentLine("پس به کیف پول", 70.0), PaymentLine("پس‌داده نقد", 80.0),
        ),
      ),
      576, Fmt(),
    )
    val t = texts(doc)
    assertTrue("برگشتِ بی رسید" in t)
    assertFalse(t.any { it.startsWith("از فاکتور") })
    assertTrue("2026/10/30" in t)
    assertFalse(t.any { it.startsWith("تأیید:") || it.startsWith("ثبت:") })
    assertEquals(listOf(ReceiptBlock.Item("کالای الف", "2 · به موجودی", "300")), doc.blocks.filterIsInstance<ReceiptBlock.Item>())
    assertEquals(
      listOf("مبلغ برگشت", "کم از بدهی", "اعتبار فروشگاه", "پس به کیف پول", "پس‌داده نقد"),
      rows(doc).map { it.label },
    )
    assertEquals(ReceiptMetrics.MM80, doc.metrics)
  }

  /** S15 — یک رسید با دو بخش: برگشت و فروشِ تازه با «دریافتِ تفاوت» ۲۰۰ */
  @Test
  fun s15ExchangeOneReceiptTwoPartsCollect() {
    val doc = ReturnReceiptLayout.build(
      s13.copy(
        lines = listOf(ReturnLineView("کفش ۴۰", 1.0, "جفت", 1000.0, damaged = false)),
        total = 1000.0,
        refunds = listOf(PaymentLine("پس‌داده نقد", 1000.0)),
        reason = "اندازه یا مدلِ نامناسب", approvedBy = "",
        exchange = ExchangeView("B1-155", listOf(LineView("کفش ۴۲", 1.0, "جفت", 1200.0, 1200.0)), total = 1200.0, due = 200.0),
      ),
      384, Fmt(),
    )
    assertEquals(1, doc.blocks.count { it is ReceiptBlock.Text && it.text == "رسید برگشت R-ABC123" })
    assertTrue("خرید تازه — فاکتور #B1-155" in texts(doc))
    assertEquals(
      listOf(ReceiptBlock.Item("کفش ۴۰", "1 جفت · به موجودی", "1000"), ReceiptBlock.Item("کفش ۴۲", "1 جفت × 1200", "1200")),
      doc.blocks.filterIsInstance<ReceiptBlock.Item>(),
    )
    assertEquals(ReceiptBlock.Row("جمع خرید تازه", "1200 افغانی"), rows(doc)[2])
    assertEquals(ReceiptBlock.Row("دریافت تفاوت", "200 افغانی", TextStyle.TOTAL), rows(doc).last())
    //  بخشِ دوم پس از بازپرداختِ برگشت و پیش از دلیل می‌آید
    val ex = indexOf(doc) { it is ReceiptBlock.Text && it.text.startsWith("خرید تازه") }
    assertTrue(ex > indexOf(doc) { it is ReceiptBlock.Row && it.label == "پس‌داده نقد" })
    assertTrue(ex < indexOf(doc) { it is ReceiptBlock.Text && it.text.startsWith("دلیل:") })
  }

  /** تعویض با کالای ارزان‌تر ⇐ «پرداختِ تفاوت» با قدرِ مطلق؛ تفاوتِ صفر «دریافت» است */
  @Test
  fun exchangeSignPicksCollectOrPay() {
    fun last(due: Double) = rows(
      ReturnReceiptLayout.build(s13.copy(exchange = ExchangeView("B1-156", emptyList(), 175.0, due)), 384, Fmt()),
    ).last()
    assertEquals(ReceiptBlock.Row("پرداخت تفاوت", "50 افغانی", TextStyle.TOTAL), last(-50.0))
    assertEquals(ReceiptBlock.Row("دریافت تفاوت", "0 افغانی", TextStyle.TOTAL), last(0.0))
    assertEquals(ReceiptBlock.Row("دریافت تفاوت", "25 افغانی", TextStyle.TOTAL), last(25.0))
  }

  /** زبانِ دیگر و رقمِ فارسی: برچسب‌ها جای `{0}` را می‌گیرند و رقمِ شماره‌ها با قالبِ دستگاه است */
  @Test
  fun labelsAndDigitsFollowTheDevice() {
    val en = ReturnReceiptLabels(receipt = "Return receipt {0}", fromInvoice = "From invoice #{0}", damaged = "Waste")
    val doc = ReturnReceiptLayout.build(s13.copy(label = "R-1A2"), 384, Fmt(persian = true), en)
    val t = texts(doc)
    assertTrue("Return receipt R-۱A۲" in t)
    assertTrue("From invoice #B۱-۱۵۴" in t)
    assertEquals("۱ عدد · Waste", doc.blocks.filterIsInstance<ReceiptBlock.Item>().single().detail)
    assertEquals("Return receipt X", ReturnReceiptLabels.fill(en.receipt, "X"))
  }
}
