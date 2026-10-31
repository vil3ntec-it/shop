package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.product.ProductInfo
import ir.vil3ntec.tohid.core.product.ShelfLocation
import ir.vil3ntec.tohid.core.text.CatalogMatch
import ir.vil3ntec.tohid.sync.v1.LedgerDiff
import ir.vil3ntec.tohid.ui.components.pos.PosCatalog
import ir.vil3ntec.tohid.ui.components.product.ProductInfoForm
import ir.vil3ntec.tohid.ui.screens.ProductListModel
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  PRD-02 (نامِ پشتو و انگلیسی، مترادف، واژهٔ جست‌وجو) و PRD-26 (محلِ قفسه) در مدلِ دفتر، موتورِ
 *  انبار و جست‌وجوهای برنامه. خاموش (پیش‌فرض) یعنی همان JSON، همان ویرایش و همان نتیجهٔ جست‌وجو.
 */
class ProductInfoMapTest {

  private var seq = 0
  private fun newId(): String = "i${seq++}"

  private val potato = Product(id = "p1", name = "کچالو", unit = "کیلوگرم", salePrice = 40.0)
  private val chips = Product(id = "p2", name = "چیپس", unit = "بسته", salePrice = 20.0)
  private val base = ShopData(products = listOf(potato, chips))

  private val info = ProductInfo.of("کچالو", "Potato", "سیب‌زمینی، آلو", "سبزی", "۳", "B", "۲")

  private fun rowOf(d: ShopData, id: String) =
    LedgerDiff.toJson(d)["products"]!!.jsonArray.first { it.jsonObject["id"]!!.jsonPrimitive.content == id }.jsonObject

  @Test
  fun `کالای بی این فیلدها همان JSON ِ پیشین را دارد`() {
    val row = rowOf(base, "p1")
    for (k in listOf("namePs", "nameEn", "synonyms", "keywords", "aisle", "shelf", "shelfLevel")) {
      assertFalse("$k نوشته شد", row.containsKey(k))
    }
    //  فرمِ خالی هم چیزی نمی‌نویسد
    val empty = ProductInfoMap.apply(potato, ProductInfoForm().toInfo())
    assertEquals(potato, empty)
  }

  @Test
  fun `ثبت و ویرایش با فرم همه را می‌نویسد و پاک کردن null می‌فرستد`() {
    val added = WarehouseEngine.addProduct(
      base, WarehouseEngine.ProductDraft(name = "برنج", salePrice = 1.0, info = info), 0, ::newId,
    ) as WarehouseEngine.Result.Ok
    val rice = added.data.products.first { it.name == "برنج" }
    assertEquals("Potato", rice.nameEn)
    assertEquals(listOf("سیب‌زمینی", "آلو"), rice.synonyms)
    assertEquals(ShelfLocation("۳", "B", "۲"), ProductInfoMap.shelf(rice))
    val row = rowOf(added.data, rice.id)
    assertEquals("کچالو", row["namePs"]!!.jsonPrimitive.content)
    assertEquals("B", row["shelf"]!!.jsonPrimitive.content)

    //  پاک کردنِ همه از فرم ⇒ null و کلید برداشته؛ تغییرِ فیلدها با `null` ِ صریح همگام می‌شود
    val cleared = WarehouseEngine.editProduct(
      added.data, rice.id,
      WarehouseEngine.ProductDraft(name = "برنج", salePrice = 1.0, info = ProductInfo()), "2026-10-31", 0, ::newId,
    ) as WarehouseEngine.Result.Ok
    val after = cleared.data.products.first { it.id == rice.id }
    assertNull(after.namePs)
    assertNull(after.synonyms)
    assertNull(after.shelfLevel)
    val diff = LedgerDiff.changedFields("products", row, rowOf(cleared.data, rice.id))!!
    assertEquals(JsonNull, diff["namePs"])
    assertEquals(JsonNull, diff["aisle"])
    //  ردپای ویرایش
    assertTrue(cleared.data.auditLog.last().notes.contains("محلِ قفسه"))
  }

  @Test
  fun `ویرایش بی این بخش (تنظیمِ خاموش) فیلدها را دست نمی‌زند`() {
    val with = base.copy(products = listOf(ProductInfoMap.apply(potato, info), chips))
    val r = WarehouseEngine.editProduct(
      with, "p1", WarehouseEngine.ProductDraft(name = "کچالو", unit = "کیلوگرم", salePrice = 45.0), "2026-10-31", 0, ::newId,
    ) as WarehouseEngine.Result.Ok
    val p = r.data.products.first { it.id == "p1" }
    assertEquals(45.0, p.salePrice, 0.0)
    assertEquals(info, ProductInfoMap.of(p))
    assertFalse(r.data.auditLog.last().notes.contains("واژه"))
  }

  @Test
  fun `فرم همان مقدار را برمی‌گرداند`() {
    val form = ProductInfoForm.of(info)
    assertEquals("سیب‌زمینی، آلو", form.synonyms)
    assertEquals(info, form.toInfo())
  }

  @Test
  fun `جست‌وجو فقط با تنظیمِ روشن نام‌های دیگر را می‌گردد`() {
    val products = listOf(ProductInfoMap.apply(potato, info), chips)
    //  خاموش ⇒ همان نتیجهٔ پیشین
    assertEquals(emptyList<Product>(), PosCatalog.matches(products, "potato"))
    assertEquals(listOf("p1"), PosCatalog.matches(products, "potato", extra = true).map { it.id })
    assertEquals(listOf("p1"), PosCatalog.matches(products, "آلو", extra = true).map { it.id })
    assertEquals(listOf("p1"), PosCatalog.matches(products, "کچ", extra = true).map { it.id })
    assertFalse(ProductListModel.matches(products[0], "سبزی"))
    assertTrue(ProductListModel.matches(products[0], "سبزی", extra = true))
    assertFalse(ProductInfoMap.matches(chips, "potato"))
    //  نمایهٔ جست‌وجوی مقاوم
    val off = PosCatalog.searchIndex(products)
    val on = PosCatalog.searchIndex(products, extra = true)
    assertEquals(emptyList<String>(), CatalogMatch.search(off, "potato"))
    assertEquals(listOf("p1"), CatalogMatch.search(on, "potato"))
    //  بی فیلدِ تازه، نمایهٔ «روشن» همان نمایهٔ پیشین است
    assertEquals(
      CatalogMatch.search(PosCatalog.searchIndex(base.products), "چیپس"),
      CatalogMatch.search(PosCatalog.searchIndex(base.products, extra = true), "چیپس"),
    )
  }
}
