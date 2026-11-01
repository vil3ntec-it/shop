package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.product.BulkPrice
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  PRD-04 (چند بارکد)، PRD-19 (نامِ مشابه)، PRD-14 (کاربر و دلیلِ تغییرِ قیمت) و PRD-21 (قیمتِ گروهی)
 *  در موتورِ انبار و ورود از فایل. منطقِ خالص در core: `ProductBarcodesTest`، `SimilarNamesTest`، `BulkPriceTest`.
 */
class ProductToolsTest {

  private var seq = 0
  private fun newId(): String = "t${seq++}"

  private val base = ShopData(
    products = listOf(
      Product(id = "p1", name = "چای سبز", unit = "عدد", salePrice = 100.0, barcodes = listOf("111", "222")),
      Product(id = "p2", name = "روغن ۱ لیتری", unit = "عدد", salePrice = 300.0, barcodes = listOf("333")),
    ),
  )

  private fun ok(r: WarehouseEngine.Result) = (r as? WarehouseEngine.Result.Ok) ?: error("رد شد: $r")
  private fun failed(r: WarehouseEngine.Result) = (r as? WarehouseEngine.Result.Failed)?.message ?: error("ثبت شد: $r")

  @Test fun `PRD-04 ویرایش با فرمِ تک‌بارکدی بارکدِ دوم را نمی‌اندازد`() {
    val r = ok(WarehouseEngine.editProduct(base, "p1",
      WarehouseEngine.ProductDraft(name = "چای سبز", unit = "عدد", salePrice = 120.0, barcode = "111"),
      "2026-10-30", 0, ::newId))
    assertEquals(listOf("111", "222"), r.data.products.first { it.id == "p1" }.barcodes)
    //  فقط قیمت عوض شد ⇒ «بارکد عوض شد» در سابقه نیست
    assertTrue(r.data.auditLog.none { "بارکد" in it.notes })
  }

  @Test fun `PRD-04 کادرِ چندبارکدی همهٔ فهرست را می‌نشاند و تکراری بین کالاها رد می‌شود`() {
    val r = ok(WarehouseEngine.editProduct(base, "p1",
      WarehouseEngine.ProductDraft(name = "چای سبز", unit = "عدد", salePrice = 100.0, barcodes = listOf("۱۱۱، 444", "444")),
      "2026-10-30", 0, ::newId))
    assertEquals(listOf("111", "444"), r.data.products.first { it.id == "p1" }.barcodes)
    val msg = failed(WarehouseEngine.addProduct(base,
      WarehouseEngine.ProductDraft(name = "قند", unit = "عدد", salePrice = 50.0, barcodes = listOf("555", "333")), 0, ::newId))
    assertTrue(msg, "333" in msg)
    val added = ok(WarehouseEngine.addProduct(base,
      WarehouseEngine.ProductDraft(name = "قند", unit = "عدد", salePrice = 50.0, barcodes = listOf("555", "666")), 0, ::newId))
    assertEquals(listOf("555", "666"), added.data.products.first { it.id == added.id }.barcodes)
  }

  @Test fun `PRD-04 بارکدِ تکِ تکراری همان پیامِ همیشگی`() {
    assertEquals("این بارکد قبلاً برای کالای دیگری ثبت شده", failed(WarehouseEngine.addProduct(base,
      WarehouseEngine.ProductDraft(name = "قند", unit = "عدد", salePrice = 50.0, barcode = "333"), 0, ::newId)))
  }

  @Test fun `PRD-19 نامِ مشابه هشدار است، نامِ یکسان رد`() {
    assertEquals(listOf("p1"), WarehouseEngine.similarNames(base, "چای‌سبز").map { it.id })
    assertEquals(listOf("p1"), WarehouseEngine.similarNames(base, "سبز چای").map { it.id })
    //  خودِ کالا در ویرایش و عددِ متفاوت هیچ
    assertTrue(WarehouseEngine.similarNames(base, "چای‌سبز", exceptId = "p1").isEmpty())
    assertTrue(WarehouseEngine.similarNames(base, "روغن ۵ لیتری").isEmpty())
    //  نامِ عیناً یکسان همچنان رد می‌شود
    failed(WarehouseEngine.addProduct(base, WarehouseEngine.ProductDraft(name = "چاي سبز", unit = "عدد", salePrice = 1.0), 0, ::newId))
    //  مشابه ثبت می‌شود (فقط هشدار)
    ok(WarehouseEngine.addProduct(base, WarehouseEngine.ProductDraft(name = "سبز چای", unit = "عدد", salePrice = 1.0), 0, ::newId))
  }

  @Test fun `PRD-19 ورود از فایل ردیفِ مشابه را ثبت می‌کند ولی هشدار می‌دهد`() {
    val csv = "نام محصول,قیمت فروش\nچای‌سبز,10\nقند,20\nقند سفید,30\nسفید قند,40\n"
    val plan = ProductImport.plan(base, csv)
    assertEquals(4, plan.drafts.size)
    assertEquals(listOf(2 to "چای سبز", 5 to "قند سفید"), plan.similar.map { it.row to it.other })
    assertEquals(listOf(false, true), plan.similar.map { it.inFile })
  }

  @Test fun `PRD-14 تغییرِ قیمتِ خرید با کاربر و دلیل؛ بی آن‌ها همان JSON ِ پیشین`() {
    val priced = base.copy(products = base.products.map { if (it.id == "p1") it.copy(purchasePrice = 80.0) else it })
    val r = ok(WarehouseEngine.editProduct(priced, "p1",
      WarehouseEngine.ProductDraft(name = "چای سبز", unit = "عدد", purchasePrice = 90.0, salePrice = 100.0, barcode = "111",
        changedBy = "احمد", reason = "نرخِ تازهٔ عمده‌فروش"),
      "2026-10-30", 0, ::newId))
    val ch = r.data.priceHistory.single()
    assertEquals("احمد", ch.by)
    assertEquals("نرخِ تازهٔ عمده‌فروش", ch.reason)
    val a = r.data.auditLog.single { it.type == "price_change" }
    assertEquals("احمد", a.by)
    assertTrue(a.notes, "نرخِ تازهٔ عمده‌فروش" in a.notes)
    //  پرچمِ خاموش (null) ⇒ ردیف بی by/reason و JSON ِ بی کلیدِ تازه
    val plainEdit = ok(WarehouseEngine.editProduct(priced, "p1",
      WarehouseEngine.ProductDraft(name = "چای سبز", unit = "عدد", purchasePrice = 95.0, salePrice = 100.0, barcode = "111"),
      "2026-10-30", 0, ::newId)).data.priceHistory.single()
    assertNull(plainEdit.by)
    //  همان تنظیمِ دفتر (`encodeDefaults = true`) — `EncodeDefault.NEVER` باز هم null را نمی‌نویسد
    val json = Json { encodeDefaults = true }.encodeToString(PriceChange.serializer(), plainEdit)
    assertFalse(json, "\"by\"" in json || "\"reason\"" in json)
  }

  @Test fun `PRD-21 قیمتِ گروهی با دلیل و سابقه؛ هشدار بی تأیید رد`() {
    val ids = setOf("p1", "p2")
    val up = ok(WarehouseEngine.bulkPrice(base, ids, BulkPrice.Field.SALE, BulkPrice.Op.Percent(1_000), 100,
      confirmed = false, by = "احمد", reason = "گرانی", today = "2026-10-30", now = 0, newId = ::newId))
    assertEquals(listOf(110.0, 330.0), up.data.products.map { it.salePrice })
    val edits = up.data.auditLog.filter { it.type == "product_edit" }
    assertEquals(2, edits.size)
    assertTrue(edits.all { it.by == "احمد" && "گرانی" in it.notes })
    //  بی دلیل ⇒ نه
    failed(WarehouseEngine.bulkPrice(base, ids, BulkPrice.Field.SALE, BulkPrice.Op.Percent(1_000), 100,
      confirmed = true, by = "احمد", reason = " ", today = "2026-10-30", now = 0, newId = ::newId))
    //  +۶۰٪ ⇒ هشدارِ «تغییرِ بزرگ» ⇒ بی تأیید رد، با تأیید اعمال
    failed(WarehouseEngine.bulkPrice(base, ids, BulkPrice.Field.SALE, BulkPrice.Op.Percent(6_000), 100,
      confirmed = false, by = "احمد", reason = "x", today = "2026-10-30", now = 0, newId = ::newId))
    ok(WarehouseEngine.bulkPrice(base, ids, BulkPrice.Field.SALE, BulkPrice.Op.Percent(6_000), 100,
      confirmed = true, by = "احمد", reason = "x", today = "2026-10-30", now = 0, newId = ::newId))
  }

  @Test fun `PRD-21 قیمتِ خریدِ گروهی در تاریخچهٔ قیمت با کاربر و دلیل`() {
    val r = ok(WarehouseEngine.bulkPrice(base, setOf("p2"), BulkPrice.Field.PURCHASE, BulkPrice.Op.SetTo(25_000), 100,
      confirmed = false, by = "احمد", reason = "فاکتورِ تازه", today = "2026-10-30", now = 0, newId = ::newId))
    assertEquals(250.0, r.data.products.first { it.id == "p2" }.purchasePrice, 0.0)
    val ch = r.data.priceHistory.single()
    assertEquals(listOf("p2", "احمد", "فاکتورِ تازه"), listOf(ch.productId, ch.by, ch.reason))
    assertEquals(250.0, ch.newPrice, 0.0)
  }

  @Test fun `PRD-06 برند و برچسب ثبت و ویرایش می‌شوند؛ null یعنی دست نخورد و کالای بی برند همان JSON`() {
    val added = ok(WarehouseEngine.addProduct(base,
      WarehouseEngine.ProductDraft(name = "قند", unit = "عدد", salePrice = 50.0, brand = "  کابل   فود ", tags = listOf("تخفیفی", " ارگانیک ", "تخفيفي")),
      0, ::newId))
    val p = added.data.products.first { it.id == added.id }
    assertEquals("کابل فود", p.brand)
    assertEquals(listOf("تخفیفی", "ارگانیک"), p.tags)
    //  ویرایش بی این دو (پرچمِ خاموش) ⇒ همان
    val kept = ok(WarehouseEngine.editProduct(added.data, added.id,
      WarehouseEngine.ProductDraft(name = "قند", unit = "عدد", salePrice = 55.0), "2026-10-30", 0, ::newId))
      .data.products.first { it.id == added.id }
    assertEquals("کابل فود", kept.brand)
    assertEquals(listOf("تخفیفی", "ارگانیک"), kept.tags)
    //  خالی ⇒ پاک، و سابقه می‌گوید
    val cleared = ok(WarehouseEngine.editProduct(added.data, added.id,
      WarehouseEngine.ProductDraft(name = "قند", unit = "عدد", salePrice = 50.0, brand = " ", tags = emptyList()), "2026-10-30", 0, ::newId))
    val c = cleared.data.products.first { it.id == added.id }
    assertNull(c.brand)
    assertNull(c.tags)
    assertTrue(cleared.data.auditLog.any { it.type == "product_edit" && "برند" in it.notes })
    //  کالای بی برند و برچسب: هیچ کلیدِ تازه‌ای در JSON (همان تنظیمِ دفتر)
    val json = Json { encodeDefaults = true }.encodeToString(Product.serializer(), base.products.first())
    assertFalse(json, "\"brand\"" in json || "\"tags\"" in json)
  }
}
