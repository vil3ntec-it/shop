package ir.vil3ntec.tohid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  PRD-04 (چند بارکد) و PRD-19 (نامِ مشابه) در موتورِ انبار و ورود از فایل.
 *  منطقِ خالص در core: `ProductBarcodesTest` و `SimilarNamesTest`.
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
}
