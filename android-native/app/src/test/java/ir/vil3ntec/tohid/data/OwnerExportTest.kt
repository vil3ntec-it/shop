package ir.vil3ntec.tohid.data

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 *  خروجیِ کاملِ مالک (OPS-08): JSON برمی‌گردد و مو به مو همان داده است؛
 *  اکسل یک ZIPِ درست با XMLِ خوش‌ساخت است و هر مجموعه برگهٔ خودش را دارد.
 *
 *  ⚠️ دادهٔ این‌جا ساختگی است — هیچ دادهٔ دکانِ واقعی در آزمون نیست.
 */
class OwnerExportTest {

  private val longNote = "ی".repeat(40_000)

  private val shop = ShopData(
    debtors = listOf(Debtor("d1", "احمد", "0700123456", "همسایه <&>", 1_700_000_000_000)),
    transactions = listOf(DebtTransaction("t1", "d1", "give", 500.0, "2026-08-28", "نسیه", 1_700_000_000_000)),
    expenses = listOf(Expense("e1", "کرایه", "کرایه", 3000.0, "2026-08-01", "", 1_700_000_000_000)),
    expenseCategories = listOf("کرایه", "برق"),
    products = listOf(
      Product(
        id = "p1", name = "برنج \"ممتاز\"", category = "خوراکه", unit = "کیلوگرم",
        purchasePrice = 200.5, salePrice = 300.0, wholesalePrice = 280.0, minStock = 5.0,
        notes = longNote, barcodes = listOf("6291234567890", "0001"), photo = true,
        createdAt = 1_700_000_000_000, subcategory = "دانه", supplierId = "su1", active = false,
        saleUnit = "گرم", unitFactor = 1000.0,
      ),
      Product(id = "p2", name = "روغن"),
    ),
    productCategories = listOf("خوراکه"),
    productUnits = listOf("عدد", "کیلوگرم"),
    warehouseEntries = listOf(
      WarehouseEntry("w1", "p1", 4.0, 25.0, 100.0, "کیلوگرم", 200.0, "2026-08-02", "", false, "pu1", 1_700_000_000_000, "2027-01-01", "B7", "2026-07-01"),
      WarehouseEntry("w2", "p1", 0.0, 0.0, -3.0, "کیلوگرم", 0.0, "2026-08-10", "خراب شد", true, null, 1_700_000_000_000),
    ),
    sales = listOf(
      Sale("s1", 1, 600.0, "percent", 10.0, 60.0, 540.0, "credit", "d1", 40.0, 500.0, "completed", 500.0, 0.0, 1_700_000_000_000, "2026-08-28", "pending", "", "علی", series = "B1"),
      Sale("s2", null, 0.0, status = "cancelled", cancelReason = "اشتباه"),
    ),
    saleItems = listOf(SaleItem("si1", "s1", "p1", "برنج", 2.0, 300.0, 200.0, 600.0, 0.5)),
    saleReturns = listOf(SaleReturn("r1", "s1", "si1", "p1", 0.5, 150.0, "خراب بود", "2026-08-28", 1_700_000_000_000)),
    nextInvoiceNo = 2,
    suppliers = listOf(Supplier("su1", "شرکت نور", "0788", "کابل", "", 1_700_000_000_000)),
    purchases = listOf(Purchase("pu1", "p1", "su1", 100.0, "کیلوگرم", 200.0, 20000.0, "2026-08-02", "", 15000.0, 5000.0, "w1", 1_700_000_000_000, 7)),
    supplierPayments = listOf(SupplierPayment("sp1", "su1", 5000.0, "2026-08-20", "تسویه", 1_700_000_000_000)),
    stockMovements = listOf(StockMovement("m1", "p1", "purchase_in", 100.0, "2026-08-02", "", "pu1", 1_700_000_000_000)),
    priceHistory = listOf(PriceChange("ph1", "p1", 290.0, 300.0, "2026-08-03", 1_700_000_000_000)),
    auditLog = listOf(AuditEntry("a1", "shift_open", "2026-08-28", null, "صندوق", 1_700_000_000_000, 1000.0)),
  )

  private val archive = LedgerArchive.Archive(
    stockMovements = listOf(StockMovement("m0", "p1", "sale_out", -1.0, "2024-01-01", "", "s0", 1_600_000_000_000)),
    auditLog = listOf(AuditEntry("a0", "sale", "2024-01-01", "s0", "", 1_600_000_000_000)),
  )

  private val meta = OwnerExport.Meta(1_760_000_000_000, "android", "2.14.2", "دکان آزمون")

  @Test
  fun `JSON برمی‌گردد و همان داده است`() {
    val raw = OwnerExport.json(shop, meta, archive)
    val back = OwnerExport.parse(raw)
    assertEquals(1, back.formatVersion)
    assertEquals(shop, back.data)
    assertEquals(archive, back.archive)
  }

  @Test
  fun `نوشتن روی جریان همان JSON است`() {
    val out = java.io.ByteArrayOutputStream()
    OwnerExport.writeJson(shop, meta, archive, out)
    assertEquals(OwnerExport.json(shop, meta, archive), out.toString("UTF-8"))
    val xl = java.io.ByteArrayOutputStream()
    OwnerExport.writeXlsx(shop, meta, archive, xl)
    assertEquals(unzip(OwnerExport.xlsx(shop, meta, archive)), unzip(xl.toByteArray()))
  }

  @Test
  fun `سرآیندِ نسخه‌دار جلوی داده است`() {
    val tree = Json.parseToJsonElement(OwnerExport.json(shop, meta, archive)).jsonObject
    assertEquals(
      listOf("format", "formatVersion", "exportedAt", "source", "counts"),
      tree.keys.take(5),
    )
    assertEquals(OwnerExport.FORMAT, tree["format"]!!.jsonPrimitive.content)
    assertEquals(OwnerExport.FORMAT_VERSION, tree["formatVersion"]!!.jsonPrimitive.int)
    assertEquals("2025-10-09T08:53:20Z", tree["exportedAt"]!!.jsonPrimitive.content)
    assertEquals("android", tree["source"]!!.jsonObject["app"]!!.jsonPrimitive.content)
    val counts = tree["counts"]!!.jsonObject
    assertEquals(2, counts["sales"]!!.jsonPrimitive.int)
    assertEquals(1, counts["returns"]!!.jsonPrimitive.int)
    assertEquals("دکان آزمون", tree["settings"]!!.jsonObject["storeName"]!!.jsonPrimitive.content)
  }

  @Test
  fun `بی بایگانی، کلیدِ archive نیست`() {
    val tree = Json.parseToJsonElement(OwnerExport.json(shop, meta)).jsonObject
    assertFalse(tree.containsKey("archive"))
    assertEquals(LedgerArchive.Archive(), OwnerExport.parse(OwnerExport.json(shop, meta)).archive)
  }

  @Test
  fun `همین فایل با خوانندهٔ پشتیبان هم باز می‌شود`() {
    //  همان تنظیمِ `ShopStore.parseBackup`
    val store = Json { ignoreUnknownKeys = true; encodeDefaults = true; isLenient = true }
    assertEquals(shop, store.decodeFromString(ShopData.serializer(), OwnerExport.json(shop, meta, archive)))
  }

  @Test
  fun `فایلِ دیگر یا قالبِ تازه‌تر رد می‌شود`() {
    for (raw in listOf("", "[]", "{}", """{"format":"x","formatVersion":1}""", """{"format":"tohid-owner-export","formatVersion":99}""")) {
      try { OwnerExport.parse(raw); fail("پذیرفته شد: $raw") } catch (_: IllegalStateException) { }
    }
  }

  /* ------------------------------ اکسل ------------------------------ */

  private fun unzip(bytes: ByteArray): Map<String, String> = XlsxTest().unzip(bytes)

  private fun xml(s: String): Element =
    DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
      .newDocumentBuilder().parse(ByteArrayInputStream(s.toByteArray(Charsets.UTF_8))).documentElement

  private fun rows(sheet: Element) = sheet.getElementsByTagName("row").length

  @Test
  fun `اکسل یک برگه برای هر مجموعه دارد، با XMLِ خوش‌ساخت`() {
    val files = unzip(OwnerExport.xlsx(shop, meta, archive))
    files.forEach { (name, text) -> if (name.endsWith(".xml") || name.endsWith(".rels")) xml(text) }

    val book = xml(files.getValue("xl/workbook.xml"))
    val names = (0 until book.getElementsByTagName("sheet").length).map {
      (book.getElementsByTagName("sheet").item(it) as Element).getAttribute("name")
    }
    val expected = listOf(OwnerExport.INFO_SHEET) + OwnerExport.SHEETS.values +
      listOf(OwnerExport.ARCHIVE_PREFIX + "حرکات انبار", OwnerExport.ARCHIVE_PREFIX + "سابقهٔ قیمت‌ها", OwnerExport.ARCHIVE_PREFIX + "سابقهٔ عملیات")
    assertEquals(expected, names)
    assertTrue(names.all { it.length <= 31 })
    assertEquals(names.size, names.map { it.lowercase() }.toSet().size)

    //  هر برگه در Content_Types و rels هست
    val types = files.getValue("[Content_Types].xml")
    val rels = files.getValue("xl/_rels/workbook.xml.rels")
    for (i in 1..names.size) {
      assertTrue(types.contains("/xl/worksheets/sheet$i.xml"))
      assertTrue(rels.contains("worksheets/sheet$i.xml"))
      assertTrue(files.containsKey("xl/worksheets/sheet$i.xml"))
    }

    //  ردیف‌ها = سرستون + داده
    val sheet = { label: String -> xml(files.getValue("xl/worksheets/sheet${names.indexOf(label) + 1}.xml")) }
    assertEquals(1 + shop.products.size, rows(sheet("کالاها")))
    assertEquals(1 + shop.sales.size, rows(sheet("فاکتورهای فروش")))
    assertEquals(1 + shop.expenseCategories.size, rows(sheet("دسته‌های مصارف")))
    assertEquals(1 + archive.stockMovements.size, rows(sheet(OwnerExport.ARCHIVE_PREFIX + "حرکات انبار")))
    assertEquals(1, rows(sheet(OwnerExport.ARCHIVE_PREFIX + "سابقهٔ قیمت‌ها")))
  }

  @Test
  fun `سرستون‌ها فارسی‌اند و رشته رشته می‌ماند`() {
    val files = unzip(OwnerExport.xlsx(shop, meta, archive))
    val products = files.getValue("xl/worksheets/sheet2.xml")
    assertTrue(products.contains(">نام<") && products.contains(">قیمت فروش<") && products.contains(">زمان ثبت (UTC)<"))
    //  بارکد متن است، نه عدد (صفرِ اول نمی‌افتد)
    assertTrue(products.contains("t=\"inlineStr\"><is><t xml:space=\"preserve\">6291234567890، 0001</t>"))
    //  متنِ بیش از سقفِ اکسل کوتاه می‌شود (JSON کاملش را دارد)
    assertFalse(products.contains(longNote))
    assertTrue(products.contains("ی".repeat(Xlsx.MAX_CELL_TEXT - 1) + "…"))
    //  درست/نادرست، عدد و زمان نوعِ خودشان را دارند
    assertTrue(products.contains("t=\"b\"><v>1</v>"))
    assertTrue(products.contains("s=\"3\"><v>"))
    val debtors = files.getValue("xl/worksheets/sheet9.xml")
    assertTrue(debtors.contains(">0700123456<"))
    assertTrue(debtors.contains("همسایه &lt;&amp;&gt;"))
  }

  @OptIn(ExperimentalSerializationApi::class)
  @Test
  fun `هر مجموعه و هر فیلدِ دفتر نامِ فارسی دارد`() {
    val d = ShopData.serializer().descriptor
    for (i in 0 until d.elementsCount) {
      val key = d.getElementName(i)
      val el = d.getElementDescriptor(i)
      if (el.kind.toString() != "LIST") {
        assertTrue("برچسبِ $key نیست", OwnerExport.HEADERS.containsKey(key))
        continue
      }
      assertTrue("برگهٔ $key نیست", OwnerExport.SHEETS.containsKey(key))
      val item = el.getElementDescriptor(0)
      for (j in 0 until item.elementsCount) {
        val f = item.getElementName(j)
        assertTrue("سرستونِ $key.$f نیست", OwnerExport.HEADERS.containsKey(f))
      }
    }
  }
}
