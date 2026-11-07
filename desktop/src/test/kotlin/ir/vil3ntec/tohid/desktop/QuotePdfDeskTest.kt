package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.core.i18n.StrPos
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.QuoteBook
import ir.vil3ntec.tohid.data.Quotes
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.SavedQuote
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.print.QuoteFile
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  POS-25 «ارسال» — پیش‌فاکتور به PDF (`QuoteFile`): همان برگهٔ چاپی با قیمتِ ثابتِ لحظهٔ ساختن، روی تصویرِ یک‌بیتیِ
 *  ۸۰ میلی‌متری و `ReceiptPdf` ِ هسته؛ گوشی و آیفون با برگهٔ اشتراک، کامپیوتر با «ذخیره در…». سندِ مالی نمی‌سازد.
 */
class QuotePdfDeskTest {

  private val d = ShopData(
    products = listOf(Product(id = "p1", name = "برنج", salePrice = 250.0), Product(id = "p2", name = "روغن", salePrice = 120.0)),
    debtors = listOf(Debtor(id = "c1", name = "کریمی")),
  )

  private fun quote(cart: List<SalesEngine.CartLine>, note: String? = " تحویل فردا "): SavedQuote =
    (Quotes.create(QuoteBook(), d, cart, "c1", note, "2026-11-01", 7, 1000, "q1", "علی") as Quotes.Result.Ok).quote

  private val two = listOf(SalesEngine.CartLine("p1", 2.0), SalesEngine.CartLine("p2", 1.0, unitPrice = 100.0))

  private fun ascii(b: ByteArray) = String(b, Charsets.ISO_8859_1)
  private fun height(pdf: ByteArray): Int = Regex("/Height (\\d+)").find(ascii(pdf))!!.groupValues[1].toInt()

  @Test
  fun `PDF ِ پیش‌فاکتور یک PDF ِ درستِ یک‌صفحه‌ای است و همان پیش‌فاکتور همان بایت‌ها`() {
    val q = quote(two)
    val pdf = QuoteFile.bytes(DesktopContext, d, q)
    assertEquals("%PDF", String(pdf.copyOfRange(0, 4), Charsets.US_ASCII))
    assertTrue(ascii(pdf).trimEnd().endsWith("%%EOF"))
    assertTrue(ascii(pdf).contains("/Count 1"))
    assertTrue("برگهٔ خالی", pdf.size > 2_000)
    assertArrayEquals(pdf, QuoteFile.bytes(DesktopContext, d, q))
  }

  @Test
  fun `برگه قیمتِ ثابتِ پیش‌فاکتور را دارد نه قیمتِ امروزِ کالا و «این فاکتور نیست»`() {
    val q = quote(two)
    //  قیمتِ کالا پس از ساختن دو برابر شد ⇒ برگه همان قیمتِ قول‌داده
    val later = d.copy(products = d.products.map { it.copy(salePrice = it.salePrice * 4) })
    val text = QuoteFile.lines(q, later).map { it.text }
    assertEquals(StrPos.quoteSlipTitle(1), text.first())
    assertTrue(text.contains(StrPos.quoteSlipCustomer("کریمی")))
    assertTrue(text.any { it.contains("برنج") && it.contains(money(250.0)) && it.contains(money(500.0)) })
    assertFalse(text.any { it.contains("برنج") && it.contains(money(1000.0)) })
    assertTrue(text.contains(StrPos.quoteSlipTotal(money(600.0))))
    assertTrue(text.contains("تحویل فردا"))
    assertEquals(StrPos.quoteSlipFooter, text.last())
    //  بی یادداشت ⇒ سطرِ یادداشت نمی‌آید
    assertEquals(text.size - 1, QuoteFile.lines(quote(two, note = null), later).size)
  }

  @Test
  fun `ردیفِ بیشتر برگهٔ بلندتر — ردیف‌ها واقعاً در PDF کشیده می‌شوند`() {
    val one = QuoteFile.bytes(DesktopContext, d, quote(two.take(1)))
    val both = QuoteFile.bytes(DesktopContext, d, quote(two))
    assertTrue("${height(one)} < ${height(both)}", height(one) < height(both))
  }

  @Test
  fun `نامِ فایل شمارهٔ پیش‌فاکتور را دارد`() {
    assertEquals("quote-1.pdf", QuoteFile.name(quote(two)))
  }

  @Test
  fun `دکمهٔ PDF در فهرستِ پیش‌فاکتورها — گوشی و آیفون برگهٔ اشتراک، کامپیوتر ذخیره، چاپ همان سطرها`() {
    val root = listOf("../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, "ir/vil3ntec/tohid") }.first { it.isDirectory }
    val bar = File(root, "ui/components/pos/QuoteBarAndroid.kt").readText()
    assertTrue(bar.contains("TextButton(onClick = { onPdf(q) })"))
    assertTrue(bar.contains("if (ir.vil3ntec.tohid.ui.Platform.DESKTOP) toast(QuoteFile.save(context, d, q))"))
    assertTrue(bar.contains("else QuoteFile.share(context, d, q)?.let(toast)"))
    //  چاپ و PDF یک متن‌اند
    assertTrue(bar.contains("EscPos.document(HwPages.text(context, p.paperDots, QuoteFile.lines(q, d)), p)"))
    assertFalse(bar.contains("store.save"))
    val file = File(root, "print/QuoteFileAndroid.kt").readText()
    assertTrue(file.contains("type = \"application/pdf\""))
    assertTrue(file.contains("putExtra(android.content.Intent.EXTRA_STREAM, uri)"))
    assertTrue(file.contains("ir.vil3ntec.tohid.ui.Platform.saveFile(name(q), data)"))
  }
}
