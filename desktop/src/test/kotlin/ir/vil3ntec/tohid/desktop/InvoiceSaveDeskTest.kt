package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SaleItem
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.print.InvoiceFile
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  POS-32، کامپیوتر — «رسیدِ دیجیتال: تصویر یا PDF ِ رسید». گوشی همان تصویر/PDF را با برگهٔ اشتراکِ سیستم می‌فرستد؛
 *  کامپیوتر برگهٔ اشتراک ندارد و پیش‌تر فقط «کپیِ پیوند» داشت. حالا همان رسید (`InvoiceFile.bytes`: تصویرِ ۸۰ میلی‌متریِ
 *  یک‌بیتی، `MonoPng`؛ یا `ReceiptPdf`) در پنجرهٔ فاکتورِ کامپیوتر و منوی صفحهٔ فاکتورِ نسخهٔ ۳ به فایل ذخیره می‌شود.
 */
class InvoiceSaveDeskTest {

  private val sale = Sale(id = "s-save", invoiceNumber = 7, total = 500.0, finalTotal = 500.0, paidAmount = 500.0, createdAt = 1L, date = "2026-11-03")
  private val d = ShopData(
    sales = listOf(sale),
    saleItems = listOf(SaleItem(id = "i1", saleId = "s-save", name = "بیسکویت", quantity = 2.0, unitPrice = 250.0, totalPrice = 500.0)),
  )

  @Test
  fun `تصویرِ رسید یک PNG ِ درست و PDF ِ آن یک PDF ِ درست است`() {
    val png = InvoiceFile.bytes(DesktopContext, d, sale, pdf = false)
    assertArrayEquals(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte()), png.copyOfRange(0, 4))
    assertTrue("تصویرِ خالی", png.size > 200)
    val pdf = InvoiceFile.bytes(DesktopContext, d, sale, pdf = true)
    assertEquals("%PDF", String(pdf.copyOfRange(0, 4), Charsets.US_ASCII))
    assertTrue(String(pdf, Charsets.ISO_8859_1).trimEnd().endsWith("%%EOF"))
    //  همان فاکتور ⇒ همان بایت‌ها (بی ساعت و تصادف)
    assertArrayEquals(png, InvoiceFile.bytes(DesktopContext, d, sale, pdf = false))
  }

  @Test
  fun `نامِ فایل شمارهٔ فاکتور را دارد`() {
    assertEquals("invoice-7.png", InvoiceFile.name(sale, pdf = false))
    assertEquals("invoice-7.pdf", InvoiceFile.name(sale, pdf = true))
  }

  @Test
  fun `پنجره و صفحهٔ فاکتورِ کامپیوتر دکمهٔ ذخیره دارند و گوشی همان فرستادن را`() {
    val root = listOf("../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, "ir/vil3ntec/tohid") }.first { it.isDirectory }
    val dialog = File(root, "ui/screens/SaleDialogs.kt").readText()
    assertTrue(dialog.contains("ir.vil3ntec.tohid.ui.components.pos.InvoiceSaveRow(d, sale, onMessage)"))
    val row = File(root, "ui/components/pos/InvoiceSaveRowAndroid.kt").readText()
    assertTrue(row.contains("if (!ir.vil3ntec.tohid.ui.Platform.DESKTOP) return"))
    assertTrue(row.contains("InvoiceFile.save(context, d, sale, pdf = false)"))
    assertTrue(row.contains("if (pdfOn) OutlinedButton("))
    val routes = File(root, "ui/v3/sales/history/HistoryRoutes.kt").readText()
    assertTrue(routes.contains("saveImage = if (Platform.DESKTOP)"))
    assertTrue(routes.contains("savePdf = if (receiptV2 && Platform.DESKTOP)"))
    //  گوشی دست نخورد: «فرستادن» و «فرستادنِ PDF» فقط بیرون از کامپیوتر
    assertTrue(routes.contains("send = if (Platform.DESKTOP) null else"))
    assertTrue(routes.contains("pdf = if (receiptV2 && !Platform.DESKTOP)"))
  }
}
