package ir.vil3ntec.tohid.core.receipt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 *  POS-34 — رسیدِ هدیه بی قیمت. پیش از این تنها رسیدِ فروش همان فاکتور بود و هر قیمت، تخفیف،
 *  پرداخت و QR ِ صفحهٔ عمومی (با همهٔ قیمت‌ها) روی آن می‌آمد.
 */
class GiftReceiptLayoutTest {

  private class Fmt : ReceiptFormat {
    private fun n(v: Double): String = if (v == kotlin.math.floor(v)) v.toLong().toString() else v.toString()
    override fun money(afn: Double) = n(afn) + " افغانی"
    override fun amount(afn: Double) = n(afn)
    override fun qty(q: Double) = n(q)
    override fun digits(text: String) = text
    override fun phone(text: String) = text
    override fun date(iso: String) = iso.replace('-', '/')
  }

  private val sale = ReceiptInput(
    shop = ShopHeader("دکانِ آزمون", "کابل", "0799887766", footer = "تشکر"),
    invoiceNo = "B1-154",
    dateIso = "2026-10-31",
    time = "14:35",
    cashier = "علی",
    kind = "نسیه",
    cancelled = false,
    items = listOf(
      LineView("پیراهن", 2.0, "عدد", 1_234.0, 2_468.0, listPrice = 1_500.0),
      LineView("کمربند", 1.0, "عدد", 777.0, 777.0, returnedQty = 1.0),        // کاملاً برگشته
      LineView("جوراب", 3.0, "جفت", 95.0, 285.0, returnedQty = 1.0),
    ),
    subtotal = 3_530.0,
    discounts = listOf(DiscountLine("تخفیف", 61.0)),
    total = 3_469.0,
    payments = listOf(PaymentLine("نقدی", 2_000.0)),
    paid = 2_000.0,
    tendered = 2_500.0,
    change = 500.0,
    remaining = 1_469.0,
    debtorName = "احمد",
    qrUrl = "https://example.test/r/abc/def",
    copyMark = null,
  )

  private fun allText(doc: ReceiptDoc): List<String> = doc.blocks.flatMap { b ->
    when (b) {
      is ReceiptBlock.Text -> listOf(b.text)
      is ReceiptBlock.Row -> listOf(b.label, b.value)
      is ReceiptBlock.Item -> listOf(b.name, b.detail, b.total) + b.notes
      else -> emptyList()
    }
  }

  @Test
  fun noPriceTotalPaymentDebtorOrQrReachesThePaper() {
    val doc = GiftReceiptLayout.build(sale, 576, Fmt())
    val text = allText(doc).joinToString("\n")
    for (secret in listOf("1234", "2468", "1500", "777", "95", "285", "3530", "61", "3469", "2000", "2500", "500", "1469", "افغانی", "احمد", "نسیه", "علی")) {
      assertFalse(text.contains(secret), "«$secret» روی رسیدِ هدیه آمد:\n$text")
    }
    assertTrue(doc.blocks.none { it is ReceiptBlock.Qr })
  }

  @Test
  fun keepsShopInvoiceDateAndRemainingQuantities() {
    val doc = GiftReceiptLayout.build(sale, 576, Fmt())
    val t = doc.blocks.filterIsInstance<ReceiptBlock.Text>().map { it.text }
    assertEquals("دکانِ آزمون", t.first())
    assertTrue("رسیدِ هدیه" in t)
    assertTrue(ReceiptBlock.Row("شمارهٔ فاکتور", "#B1-154") in doc.blocks)
    assertTrue(ReceiptBlock.Row("تاریخ", "2026/10/31") in doc.blocks)
    assertEquals(
      listOf(ReceiptBlock.Item("پیراهن", "2 عدد", ""), ReceiptBlock.Item("جوراب", "2 جفت", "")),
      doc.blocks.filterIsInstance<ReceiptBlock.Item>(),
    )
    assertTrue("تعداد اقلام: 2 ردیف · 4 عدد" in t)
    assertEquals("تشکر", t.last())
  }

  @Test
  fun labelsComeFromTheCaller() {
    val en = GiftReceiptLayout.Labels(title = "Gift receipt", note = "No prices", base = ReceiptLabels(invoiceNo = "Invoice"))
    val doc = GiftReceiptLayout.build(sale.copy(shop = sale.shop.copy(footer = "")), 384, Fmt(), en)
    val t = doc.blocks.filterIsInstance<ReceiptBlock.Text>().map { it.text }
    assertTrue("Gift receipt" in t)
    assertTrue("No prices" in t)
    assertTrue(ReceiptBlock.Row("Invoice", "#B1-154") in doc.blocks)
  }
}
