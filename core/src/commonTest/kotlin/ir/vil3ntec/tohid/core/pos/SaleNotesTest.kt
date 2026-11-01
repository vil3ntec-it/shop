package ir.vil3ntec.tohid.core.pos

import ir.vil3ntec.tohid.core.receipt.DiscountLine
import ir.vil3ntec.tohid.core.receipt.LineView
import ir.vil3ntec.tohid.core.receipt.ReceiptBlock
import ir.vil3ntec.tohid.core.receipt.ReceiptFormat
import ir.vil3ntec.tohid.core.receipt.ReceiptInput
import ir.vil3ntec.tohid.core.receipt.ReceiptLayout
import ir.vil3ntec.tohid.core.receipt.ShopHeader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  یادداشتِ ردیف و فاکتور روی رسید و قالب‌های یادداشت — POS-11.
 *  نام‌ها camelCase اند: کامپایلرِ iOS نامِ آزمون با فاصله و نشانه نمی‌پذیرد.
 */
class SaleNotesTest {

  @Test
  fun cleanMakesOneShortLineAndBlankIsNull() {
    assertNull(SaleNotes.clean(null))
    assertNull(SaleNotes.clean(" \n\t "))
    assertEquals("بدون پیاز، تند", SaleNotes.clean("  بدون   پیاز،\nتند \u0007"))
    //  نیم‌فاصله نویسهٔ واژه است، فاصله نیست
    assertEquals("می‌خواهد", SaleNotes.clean("می‌خواهد"))
    val long = "ا".repeat(SaleNotes.MAX + 30)
    assertEquals(SaleNotes.MAX, SaleNotes.clean(long)!!.length)
  }

  @Test
  fun templatesAreUniqueCleanAndCapped() {
    assertEquals(emptyList(), SaleNotes.templates(null))
    assertEquals(listOf("بدون پیاز", "تحویل فردا"), SaleNotes.templates("بدون  پیاز\n\nتحویل فردا\nبدون پیاز\n"))
    val many = (1..20).joinToString("\n") { "قالب $it" }
    assertEquals(SaleNotes.MAX_TEMPLATES, SaleNotes.templates(many).size)
    val list = SaleNotes.addTemplate(listOf("بدون پیاز"), " تحویل  فردا ")
    assertEquals(listOf("بدون پیاز", "تحویل فردا"), list)
    assertEquals(list, SaleNotes.addTemplate(list, "بدون پیاز"))
    assertEquals(list, SaleNotes.addTemplate(list, "  "))
    assertEquals(listOf("تحویل فردا"), SaleNotes.removeTemplate(list, "بدون پیاز"))
    assertEquals(list, SaleNotes.templates(SaleNotes.encodeTemplates(list)))
  }

  @Test
  fun insertAppendsOnceAndNeverOverflows() {
    assertEquals("بدون پیاز", SaleNotes.insert(null, "بدون پیاز"))
    assertEquals("تند، بدون پیاز", SaleNotes.insert("تند", "بدون پیاز"))
    assertEquals("تند، بدون پیاز", SaleNotes.insert("تند، بدون پیاز", "بدون پیاز"))
    val full = "ب".repeat(SaleNotes.MAX - 3)
    assertEquals(full, SaleNotes.insert(full, "بدون پیاز"))
  }

  private class Fmt : ReceiptFormat {
    private fun n(v: Double): String = if (v == kotlin.math.floor(v)) v.toLong().toString() else v.toString()
    override fun money(afn: Double) = n(afn) + " افغانی"
    override fun amount(afn: Double) = n(afn)
    override fun qty(q: Double) = n(q)
    override fun digits(text: String) = text
    override fun phone(text: String) = text
    override fun date(iso: String) = iso
  }

  private fun input(note: String? = null, lineNote: String? = null) = ReceiptInput(
    shop = ShopHeader("دکانِ آزمون"),
    invoiceNo = "B1-7",
    dateIso = "2026-11-01",
    time = "10:00",
    cashier = "علی",
    kind = "نقدی",
    cancelled = false,
    items = listOf(LineView("قورمه", 2.0, "عدد", 150.0, 300.0, note = lineNote), LineView("نان", 1.0, "عدد", 20.0, 20.0)),
    subtotal = 320.0,
    discounts = listOf(DiscountLine("تخفیف", 0.0)),
    total = 320.0,
    payments = emptyList(),
    paid = 320.0,
    note = note,
  )

  @Test
  fun receiptPrintsLineNoteUnderItsLineAndInvoiceNoteAboveFooter() {
    val doc = ReceiptLayout.build(input(note = "تحویل ساعت ۵", lineNote = "بدون پیاز"), 384, Fmt())
    val items = doc.blocks.filterIsInstance<ReceiptBlock.Item>()
    assertEquals(listOf("بدون پیاز"), items[0].notes)
    assertEquals(emptyList(), items[1].notes)
    val texts = doc.blocks.filterIsInstance<ReceiptBlock.Text>().map { it.text }
    val at = texts.indexOf("یادداشت: تحویل ساعت ۵")
    assertTrue(at >= 0, texts.toString())
    assertTrue(at < texts.indexOf("تشکر از خرید شما"))
  }

  @Test
  fun receiptWithoutNotesIsTheSameAsBefore() {
    val plain = ReceiptLayout.build(input(), 384, Fmt())
    assertEquals(plain, ReceiptLayout.build(input(note = "  ", lineNote = ""), 384, Fmt()))
    assertTrue(plain.blocks.filterIsInstance<ReceiptBlock.Text>().none { it.text.startsWith("یادداشت") })
  }
}
