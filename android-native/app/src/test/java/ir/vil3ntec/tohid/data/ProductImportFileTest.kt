package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.product.ImportTable
import ir.vil3ntec.tohid.core.product.ProductImportPlan.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 *  PRD-20 در برنامه: خواندنِ فایل (xlsx با سقفِ بازشدن، CSV با UTF-8/UTF-16) و ثبت با
 *  `WarehouseEngine.importProducts` — یک بار، بی پاک کردن، با سابقه. منطقِ خالص در core:
 *  `ImportTableTest` و `ProductImportPlanTest`.
 */
class ProductImportFileTest {

  private var seq = 0
  private fun newId(): String = "t${seq++}"

  private val base = ShopData(
    products = listOf(
      Product(id = "p1", name = "برنج", category = "خوراکی", unit = "کیلوگرم", purchasePrice = 100.0, salePrice = 120.0,
        minStock = 2.0, barcodes = listOf("111")),
      Product(id = "p2", name = "روغن", unit = "عدد", purchasePrice = 1000.0 / 24, salePrice = 50.0, barcodes = listOf("222")),
    ),
  )

  private fun table(r: ImportTable.Read) = (r as? ImportTable.Read.Table)?.rows ?: error("رد شد: $r")
  private fun failed(r: ImportTable.Read) = (r as? ImportTable.Read.Failed)?.reason ?: error("پذیرفته شد: $r")
  private fun ok(r: WarehouseEngine.Result) = (r as? WarehouseEngine.Result.Ok) ?: error("رد شد: $r")
  private fun failed(r: WarehouseEngine.Result) = (r as? WarehouseEngine.Result.Failed)?.message ?: error("ثبت شد: $r")

  private fun zip(vararg parts: Pair<String, ByteArray>): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { z -> parts.forEach { (n, b) -> z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() } }
    return out.toByteArray()
  }

  @Test fun `PRD-20 همان xlsx ِ خروجیِ برنامه برمی‌گردد با بارکدِ صفردار و عدد`() {
    val book = Xlsx.book(listOf(Xlsx.Sheet("کالا", listOf(
      listOf(Xlsx.Cell.Text("نام محصول"), Xlsx.Cell.Text("قیمت فروش"), Xlsx.Cell.Text("بارکد")),
      listOf(Xlsx.Cell.Text("چای & شیر"), Xlsx.Cell.Num(1250.5), Xlsx.Cell.Text("0036000291452")),
      listOf(Xlsx.Cell.Text("قند"), Xlsx.Cell.Guess("1,200"), null),
    ))))
    assertEquals(
      listOf(listOf("نام محصول", "قیمت فروش", "بارکد"), listOf("چای & شیر", "1250.5", "0036000291452"), listOf("قند", "1200")),
      table(ProductImport.read(book)),
    )
  }

  @Test fun `PRD-20 CSV با UTF-8 و UTF-16؛ کدگذاریِ دیگر و xls و ZIP ِ غیرِاکسل رد`() {
    assertEquals(listOf(listOf("name"), listOf("چای")), table(ProductImport.read("\uFEFFname\nچای\n".toByteArray())))
    val utf16 = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "name\tprice\nچای\t5\n".toByteArray(Charsets.UTF_16LE)
    assertEquals(listOf(listOf("name", "price"), listOf("چای", "5")), table(ProductImport.read(utf16)))
    //  «چای» در Windows-1256 — نه UTF-8
    assertTrue(failed(ProductImport.read(byteArrayOf(0x6E, 0x61, 0x6D, 0x65, 0x0A, 0xED.toByte(), 0xC7.toByte(), 0xED.toByte()))).contains("UTF-8"))
    assertTrue(failed(ProductImport.read(byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0, 0))).contains("xls"))
    assertTrue(failed(ProductImport.read(zip("content.xml" to "<x/>".toByteArray()))).contains("xlsx"))
    assertTrue(failed(ProductImport.read(ByteArray(0))).contains("خالی"))
    assertTrue(failed(ProductImport.read(ByteArray(ImportTable.MAX_FILE_BYTES + 1) { 'a'.code.toByte() })).contains("مگابایت"))
  }

  @Test fun `PRD-20 بمبِ فشرده پیش از پر شدنِ حافظه رد می‌شود`() {
    //  ۱۰۰ مگابایت صفر ⇒ چند صد کیلوبایت ZIP؛ بخشِ ردشده هم به سقفِ کلِ بازشدن می‌خورد
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { z ->
      z.putNextEntry(ZipEntry("xl/workbook.xml")); z.write("<workbook/>".toByteArray()); z.closeEntry()
      z.putNextEntry(ZipEntry("xl/media/big.bin"))
      val mb = ByteArray(1024 * 1024)
      repeat(100) { z.write(mb) }
      z.closeEntry()
    }
    assertTrue(out.size() < ImportTable.MAX_FILE_BYTES)
    assertTrue(failed(ProductImport.read(out.toByteArray())).contains("بزرگ"))
    //  برگهٔ نگه‌داشته‌ای بیش از سقفِ هر بخش
    val sheet = ByteArrayOutputStream()
    ZipOutputStream(sheet).use { z ->
      z.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
      val mb = ByteArray(1024 * 1024) { ' '.code.toByte() }
      repeat(ImportTable.MAX_PART_BYTES / mb.size + 1) { z.write(mb) }
      z.closeEntry()
    }
    assertTrue(failed(ProductImport.read(sheet.toByteArray())).contains("بزرگ"))
  }

  private val rows = listOf(
    listOf("نام محصول", "دسته", "قیمت خرید", "قیمت فروش", "بارکد", "موجودی"),
    listOf("چای", "نوشیدنی", "80", "100", "333، 444", "50"),
    listOf("برنج", "", "110", "", "999", ""),
    listOf("روغن", "", "42", "55", "", ""),
    listOf("قند", "", "abc", "", "", ""),
  )

  @Test fun `PRD-20 ثبت فقط کالای تازه بی به‌روزرسانی و سابقهٔ واردسازی`() {
    val plan = ProductImport.planOf(base, rows, update = false)
    assertEquals(listOf(Status.NEW, Status.KEPT, Status.KEPT, Status.ERROR), plan.rows.map { it.status })
    val r = ok(WarehouseEngine.importProducts(base, rows, plan, "کالا.xlsx", "احمد", "2026-10-30", 7, ::newId))
    val tea = r.data.products.single { it.name == "چای" }
    assertEquals(listOf(80.0, 100.0), listOf(tea.purchasePrice, tea.salePrice))
    assertEquals(listOf("333", "444"), tea.barcodes)
    assertEquals("عدد", tea.unit)
    //  کالاهای موجود دست نخوردند
    assertEquals(base.products, r.data.products.filter { it.id in setOf("p1", "p2") })
    val log = ProductImport.history(r.data).single()
    assertEquals(ProductImport.refOf(plan.fingerprint), log.refId)
    assertEquals("احمد", log.by)
    assertTrue(log.notes, "کالا.xlsx" in log.notes && "1 کالای تازه" in log.notes && "1 ردیفِ رد" in log.notes)
    assertNotNull(ProductImport.previous(r.data, plan.fingerprint))
    assertNull(ProductImport.previous(base, plan.fingerprint))

    //  همان پیش‌نمایش دوباره (دو بار زدن) ⇒ کهنه است و هیچ چیز نوشته نمی‌شود
    assertTrue(failed(WarehouseEngine.importProducts(r.data, rows, plan, "کالا.xlsx", "احمد", "2026-10-30", 8, ::newId)).contains("دوباره"))
    //  همان فایل دوباره ⇒ چیزی برای ثبت نیست
    val again = ProductImport.planOf(r.data, rows, update = false)
    assertEquals(0, again.writes)
    assertTrue(failed(WarehouseEngine.importProducts(r.data, rows, again, "کالا.xlsx", "احمد", "2026-10-30", 9, ::newId)).contains("از قبل"))
  }

  @Test fun `PRD-20 به‌روزرسانیِ انتخابی فقط تغییرهای پیش‌نمایش و با نام و دلیل`() {
    val plan = ProductImport.planOf(base, rows, update = true)
    assertEquals(listOf(Status.NEW, Status.UPDATE, Status.UPDATE, Status.ERROR), plan.rows.map { it.status })
    val r = ok(WarehouseEngine.importProducts(base, rows, plan, "کالا.xlsx", "احمد", "2026-10-30", 7, ::newId))
    val rice = r.data.products.single { it.id == "p1" }
    //  خانه‌های خالی (دسته و قیمت فروش) همان؛ بارکد افزوده
    assertEquals(listOf("خوراکی", "110.0", "120.0", "2.0"), listOf(rice.category, "${rice.purchasePrice}", "${rice.salePrice}", "${rice.minStock}"))
    assertEquals(listOf("111", "999"), rice.barcodes)
    val oil = r.data.products.single { it.id == "p2" }
    //  «42» همان گردشدهٔ ۴۱٫۶۷ است ⇒ قیمتِ خرید دست نخورد؛ فقط فروش
    assertEquals(1000.0 / 24, oil.purchasePrice, 0.0)
    assertEquals(55.0, oil.salePrice, 0.0)
    val ch = r.data.priceHistory.single()
    assertEquals(listOf("p1", "احمد", "ورود از فایلِ «کالا.xlsx»"), listOf(ch.productId, ch.by, ch.reason))
    assertTrue(r.data.auditLog.any { it.type == "product_edit" && it.refId == "p2" && it.by == "احمد" })
  }

  @Test fun `PRD-20 مسیرِ پرچمِ خاموش همان خواننده و همان پیش‌نمایشِ پیشین`() {
    val csv = "name,price\nچای,10\nبرنج,5\n"
    assertEquals(ImportTable.csv(csv), ProductImport.parse(csv))
    val old = ProductImport.plan(base, csv)
    assertEquals(listOf("چای"), old.drafts.map { it.name })
    assertEquals(listOf(3), old.skipped.map { it.row })
  }
}
