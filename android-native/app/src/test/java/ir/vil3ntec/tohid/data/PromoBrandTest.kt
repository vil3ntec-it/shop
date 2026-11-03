package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.promo.PromoCore
import ir.vil3ntec.tohid.ui.screens.PromoForm
import ir.vil3ntec.tohid.ui.screens.PromoText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  PRM-08 — «قاعده‌های شامل و مستثنا (برند، دسته، تأمین‌کننده)»: برندِ کالا (PRD-06، `Product.brand`) حالا به هسته
 *  می‌رود (`PromoEngine.lines`) و ویرایشگرِ تخفیف برندِ شامل و مستثنا را می‌گیرد (`PromoForm.brands/exBrands`)؛
 *  کالای بی برند و قاعدهٔ بی برند همان پیشین.
 */
class PromoBrandTest {

  private val tea = Product(id = "tea", name = "چای", unit = "عدد", category = "نوشیدنی", salePrice = 300.0, brand = " احمد ")
  private val tea2 = Product(id = "tea2", name = "چای سبز", unit = "عدد", category = "نوشیدنی", salePrice = 200.0, brand = "گلستان")
  private val rice = Product(id = "rice", name = "برنج", unit = "کیلو", category = "خوراکی", salePrice = 100.0)
  private val d = ShopData(
    products = listOf(tea, tea2, rice),
    warehouseEntries = listOf(
      WarehouseEntry(id = "w1", productId = "tea", units = 50.0),
      WarehouseEntry(id = "w2", productId = "tea2", units = 50.0),
      WarehouseEntry(id = "w3", productId = "rice", units = 50.0),
    ),
  )
  private val cart = listOf(SalesEngine.CartLine("tea", 1.0), SalesEngine.CartLine("tea2", 1.0), SalesEngine.CartLine("rice", 1.0))

  private fun rule(scope: PromoCore.Scope) = PromoCore.Rule(id = "b1", name = "ده درصد", kind = "percent", bp = 1000, scope = scope, channels = listOf("pos"))
  private fun eval(scope: PromoCore.Scope) =
    PromoEngine.evaluate(d, cart, PromoEngine.Snapshot(enabled = true, rules = listOf(rule(scope))), emptyList(), "2026-11-03", 600)

  /** تخفیفِ هر ردیف (پولِ خرد) از فیِ تازهٔ سبد */
  private fun off(o: PromoEngine.Outcome?): Map<String, Double> {
    val priced = PromoEngine.priced(cart, o)
    return cart.zip(priced).associate { (a, b) -> a.productId to ((d.products.first { it.id == a.productId }.salePrice) - (b.unitPrice ?: d.products.first { it.id == a.productId }.salePrice)) }
  }

  @Test
  fun `برندِ کالا به هسته می‌رود — تمیز، و بی برند خالی`() {
    val lines = PromoEngine.lines(d, cart).associateBy { it.productId }
    assertEquals("احمد", lines.getValue("tea").brand)
    assertEquals("گلستان", lines.getValue("tea2").brand)
    assertEquals("", lines.getValue("rice").brand)
  }

  @Test
  fun `قاعدهٔ برند فقط کالای همان برند؛ مستثنای برند بر شاملِ دسته مقدم است`() {
    val only = off(eval(PromoCore.Scope(brands = listOf("احمد"))))
    assertEquals(30.0, only.getValue("tea"), 0.001)
    assertEquals(0.0, only.getValue("tea2"), 0.001)
    assertEquals(0.0, only.getValue("rice"), 0.001)
    val ex = off(eval(PromoCore.Scope(categories = listOf("نوشیدنی"), exBrands = listOf("گلستان"))))
    assertEquals(30.0, ex.getValue("tea"), 0.001)
    assertEquals(0.0, ex.getValue("tea2"), 0.001)
    //  قاعدهٔ بی برند همان پیشین: دستهٔ «نوشیدنی» هر دو چای
    val cat = off(eval(PromoCore.Scope(categories = listOf("نوشیدنی"))))
    assertEquals(30.0, cat.getValue("tea"), 0.001)
    assertEquals(20.0, cat.getValue("tea2"), 0.001)
    //  برندی که هیچ کالایی ندارد ⇒ هیچ تخفیف
    assertNull(eval(PromoCore.Scope(brands = listOf("ناشناخته"))).takeIf { o -> off(o).values.any { it > 0 } })
  }

  @Test
  fun `ویرایشگر برندِ شامل و مستثنا را رفت‌وبرگشت نگه می‌دارد و متنِ دامنه آن را می‌گوید`() {
    val base = rule(PromoCore.Scope(categories = listOf("نوشیدنی"), brands = listOf("احمد"), exBrands = listOf("گلستان"), suppliers = listOf("s1")))
    val f = PromoForm.of(base)
    assertEquals(setOf("احمد"), f.brands)
    assertEquals(setOf("گلستان"), f.exBrands)
    //  فرمِ دست‌نخورده ⇒ همان قاعده (تأمین‌کننده که فرم نشان نمی‌دهد هم می‌ماند)
    assertEquals(base.scope, f.toRule(base).scope)
    val next = f.copy(brands = setOf("گلستان", "احمد"), exBrands = emptySet()).toRule(base)
    assertEquals(listOf("احمد", "گلستان"), next.scope.brands)
    assertEquals(emptyList<String>(), next.scope.exBrands)
    assertEquals(listOf("s1"), next.scope.suppliers)
    val text = PromoText.scopeText(base, d)
    assertTrue(text, text.contains("برندِ احمد"))
    assertTrue(text, text.contains("جز برندِ گلستان"))
    //  ویرایشگر تراشهٔ برند را فقط با برندِ کالا یا قاعده نشان می‌دهد
    val src = listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, "ir/vil3ntec/tohid/ui/screens/Promos.kt") }.first { it.isFile }.readText()
    assertNotNull(src.indexOf("if (brands.isNotEmpty()) {").takeIf { it > 0 })
    assertTrue(src.contains("(d.products.mapNotNull { it.brand?.trim()?.takeIf { b -> b.isNotEmpty() } } + rule.scope.brands + rule.scope.exBrands).distinct().sorted()"))
  }
}
