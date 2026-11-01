package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.product.ProductVariants
import ir.vil3ntec.tohid.sync.v1.LedgerDiff
import ir.vil3ntec.tohid.ui.components.product.ProductInfoForm
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  PRD-11 — تنوع با ماتریس در دفتر: ذخیرهٔ مدل با ویژگی‌ها هر ترکیب را کالای جدا با بارکد، قیمت و موجودیِ
 *  خودش می‌سازد، دوباره‌سازی و پاک کردن ندارد، و کالای بی ویژگی همان JSON و همان ثبت را دارد.
 */
class ProductVariantsMapTest {

  private var seq = 0
  private fun newId(): String = "v${seq++}"

  private val form = ProductInfoForm(axes = "رنگ: قرمز، آبی\nاندازه: S، M")
  private fun draft(info: ir.vil3ntec.tohid.core.product.ProductInfo?) =
    WarehouseEngine.ProductDraft(name = "پیراهن", category = "پوشاک", unit = "دانه", purchasePrice = 300.0, salePrice = 450.0, info = info)

  @Test
  fun `ذخیرهٔ مدل هر ترکیب را کالای جدا می‌سازد`() {
    val ok = WarehouseEngine.addProduct(ShopData(), draft(form.toInfo(emptyList(), null)), 7, ::newId) as WarehouseEngine.Result.Ok
    val parent = ok.data.products.first { it.name == "پیراهن" }
    val kids = ProductVariantsMap.children(ok.data.products, parent.id)
    assertEquals(listOf("پیراهن — قرمز / S", "پیراهن — قرمز / M", "پیراهن — آبی / S", "پیراهن — آبی / M"), kids.map { it.name })
    val k = kids.first()
    assertEquals(450.0, k.salePrice, 0.0)
    assertEquals("پوشاک", k.category)
    assertEquals(emptyList<String>(), k.barcodes)
    assertEquals(mapOf("رنگ" to "قرمز", "اندازه" to "S"), k.variant)
    assertEquals(7L, k.createdAt)
    //  ماتریس: هر خانه همان تنوع
    val (m, byId) = ProductVariantsMap.matrix(ok.data.products, ProductVariantsMap.axesOf(parent), parent.id)
    assertEquals("پیراهن — آبی / M", byId.getValue(m.cells.getValue(1 to 1)).name)
  }

  @Test
  fun `ویرایشِ دوباره تنوعِ موجود را دوباره نمی‌سازد و مقدارِ برداشته را پاک نمی‌کند`() {
    val ok = WarehouseEngine.addProduct(ShopData(), draft(form.toInfo(emptyList(), null)), 0, ::newId) as WarehouseEngine.Result.Ok
    val parent = ok.data.products.first { it.name == "پیراهن" }
    //  تنوعِ «قرمز / S» قیمت و بارکدِ خودش را گرفت
    val priced = ok.data.copy(products = ok.data.products.map { if (it.name.endsWith("قرمز / S")) it.copy(salePrice = 500.0, barcodes = listOf("2000000000015")) else it })
    val again = ProductInfoForm(axes = "رنگ: قرمز، سبز\nاندازه: S، M").toInfo(emptyList(), parent.id)
    val r = WarehouseEngine.editProduct(priced, parent.id, draft(again), "2026-11-01", 0, ::newId) as WarehouseEngine.Result.Ok
    val kids = ProductVariantsMap.children(r.data.products, parent.id)
    //  ۴ پیشین (آبی‌ها هم می‌مانند) + ۲ سبزِ تازه
    assertEquals(6, kids.size)
    assertEquals(1, kids.count { it.name.endsWith("قرمز / S") })
    assertEquals(500.0, kids.first { it.name.endsWith("قرمز / S") }.salePrice, 0.0)
    assertTrue(kids.any { it.name.endsWith("سبز / M") })
    assertTrue(r.data.auditLog.last().notes.contains("ویژگی‌های تنوع"))
  }

  @Test
  fun `نامِ ساخته‌شده‌ای که مالِ کالای دیگر است ساخته نمی‌شود`() {
    val d = ShopData(products = listOf(Product(id = "x", name = "پیراهن — قرمز / S")))
    val ok = WarehouseEngine.addProduct(d, draft(form.toInfo(emptyList(), null)), 0, ::newId) as WarehouseEngine.Result.Ok
    assertEquals(3, ProductVariantsMap.children(ok.data.products, ok.id!!).size)
    assertEquals(1, ok.data.products.count { it.name == "پیراهن — قرمز / S" })
  }

  @Test
  fun `کالای بی ویژگی همان JSON و هیچ کالای تازه`() {
    val ok = WarehouseEngine.addProduct(ShopData(), draft(null), 0, ::newId) as WarehouseEngine.Result.Ok
    assertEquals(1, ok.data.products.size)
    val row = LedgerDiff.toJson(ok.data)["products"]!!.jsonArray.single().jsonObject
    for (k in listOf("variantAxes", "variantOf", "variant")) assertFalse(k, row.containsKey(k))
    //  کادرِ فرم همان متن را برمی‌گرداند
    assertEquals(form.axes, ProductVariants.format(form.toInfo(emptyList(), null).axes))
    assertEquals("پیراهن", row["name"]!!.jsonPrimitive.content)
  }
}
