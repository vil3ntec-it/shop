package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.ui.v3.products.ProductFormModel
import ir.vil3ntec.tohid.ui.v3.products.ProductFormModel.Field
import ir.vil3ntec.tohid.ui.v3.products.ProductFormModel.Mode
import ir.vil3ntec.tohid.ui.v3.products.ProductFormModel.Problem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** MKT-06: «چرخهٔ مصرف» ِ کالا — کادر، دفتر و JSON ِ همگام‌سازی (همان سقفِ `cycleOf` ِ سرور) */
class ProductCycleTest {
  private val tea = Product(id = "tea", name = "چای", category = "خوراکی", unit = "بسته", salePrice = 100.0)
  private val data = ShopData(products = listOf(tea, Product(id = "rice", name = "برنج")))

  @Test
  fun `کادر — خالی پاک، عددِ صحیحِ ۱ تا ۳۶۵ درست، بقیه خراب`() {
    assertEquals(ProductCycle.Clear, ProductCycle.parse("  "))
    assertEquals(ProductCycle.Days(30), ProductCycle.parse("30"))
    assertEquals(ProductCycle.Days(14), ProductCycle.parse("۱۴"))
    assertEquals(ProductCycle.Days(365), ProductCycle.parse("365"))
    for (bad in listOf("0", "366", "-3", "7.5", "abc", "1000")) assertTrue(bad, ProductCycle.bad(bad))
    assertEquals("", ProductCycle.text(null))
    assertEquals("", ProductCycle.text(0))
    assertEquals("30", ProductCycle.text(30))
  }

  @Test
  fun `دفتر — فقط همان کالا؛ بی‌تغییر یا خراب همان دفتر، بی op ِ تازه`() {
    val set = ProductCycle.apply(data, "tea", "30")
    assertEquals(30, set.products.first { it.id == "tea" }.consumptionDays)
    assertNull(set.products.first { it.id == "rice" }.consumptionDays)
    assertSame(set, ProductCycle.apply(set, "tea", "30"))
    assertSame(set, ProductCycle.apply(set, "tea", "400"))
    assertSame(set, ProductCycle.apply(set, "missing", "30"))
    assertNull(ProductCycle.apply(set, "tea", "").products.first { it.id == "tea" }.consumptionDays)
  }

  @Test
  fun `JSON — بی چرخه بایت‌به‌بایت همان ردیفِ پیشین؛ با چرخه همان کلیدی که سرور می‌خواند`() {
    val json = Json { encodeDefaults = true }
    val before = json.encodeToString(Product.serializer(), tea)
    assertFalse(before.contains("consumptionDays"))
    val after = json.encodeToString(Product.serializer(), tea.copy(consumptionDays = 30))
    assertEquals("30", json.parseToJsonElement(after).jsonObject["consumptionDays"].toString())
    //  ردیفِ کهنهٔ بی این کلید همان کالا خوانده می‌شود
    assertEquals(tea, Json { ignoreUnknownKeys = true }.decodeFromString(Product.serializer(), before))
  }

  @Test
  fun `فرمِ ویرایش — از کالا پر می‌شود و کادرِ خراب جلوی ذخیره را می‌گیرد`() {
    val row = ProductFormModel.of(tea.copy(consumptionDays = 30))
    assertEquals("30", row.cycle)
    assertTrue(ProductFormModel.errors(row, Mode.EDIT).isEmpty())
    assertEquals(mapOf(Field.CYCLE to Problem.NOT_A_NUMBER), ProductFormModel.errors(row.copy(cycle = "400"), Mode.EDIT))
    assertTrue(ProductFormModel.errors(row.copy(cycle = ""), Mode.EDIT).isEmpty())
    //  کالای تازه این کادر را ندارد
    assertFalse(ProductFormModel.errors(row.copy(cycle = "400"), Mode.NEW).containsKey(Field.CYCLE))
  }
}
