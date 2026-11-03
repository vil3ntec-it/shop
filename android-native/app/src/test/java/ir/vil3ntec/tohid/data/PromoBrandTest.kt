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

  /**
   *  PRM-08 — تأمین‌کنندهٔ شامل/مستثنا و دستهٔ مستثنا در ویرایشگر (`PromoForm.suppliers/exSuppliers/exCategories`):
   *  هسته همان قاعده را داشت؛ فرم نشانش نمی‌داد. رفت‌وبرگشت، «جز» بر «شامل» مقدم، و متنِ دامنه با نامِ تأمین‌کننده.
   */
  @Test
  fun `PRM-08 تأمین‌کنندهٔ شامل و مستثنا و دستهٔ مستثنا از ویرایشگر به قاعده می‌رسند و مستثنا مقدم است`() {
    val ds = d.copy(
      products = listOf(tea.copy(supplierId = "s1"), tea2.copy(supplierId = "s2"), rice.copy(supplierId = "s1")),
      suppliers = listOf(Supplier(id = "s1", name = "پخشِ کابل"), Supplier(id = "s2", name = "تجارتیِ هرات")),
    )
    fun offOf(scope: PromoCore.Scope): Map<String, Double> {
      val o = PromoEngine.evaluate(ds, cart, PromoEngine.Snapshot(enabled = true, rules = listOf(rule(scope))), emptyList(), "2026-11-03", 600)
      val priced = PromoEngine.priced(cart, o)
      return cart.zip(priced).associate { (a, b) -> a.productId to (ds.products.first { it.id == a.productId }.salePrice - (b.unitPrice ?: ds.products.first { it.id == a.productId }.salePrice)) }
    }

    //  فرمِ تازه: «تأمین‌کننده: پخشِ کابل» و «جز این دسته‌ها: خوراکی» ⇒ فقط چایِ پخشِ کابل
    val base = rule(PromoCore.Scope())
    val f = PromoForm.of(base).copy(suppliers = setOf("s1"), exCategories = setOf("خوراکی"))
    val r = f.toRule(base)
    assertEquals(listOf("s1"), r.scope.suppliers)
    assertEquals(listOf("خوراکی"), r.scope.exCategories)
    val off = offOf(r.scope)
    assertEquals(30.0, off.getValue("tea"), 0.001)
    assertEquals(0.0, off.getValue("tea2"), 0.001)
    assertEquals(0.0, off.getValue("rice"), 0.001)
    //  «جز این تأمین‌کنندگان» بر دستهٔ شامل مقدم است
    val ex = PromoForm.of(base).copy(categories = setOf("نوشیدنی"), exSuppliers = setOf("s2")).toRule(base)
    val exOff = offOf(ex.scope)
    assertEquals(30.0, exOff.getValue("tea"), 0.001)
    assertEquals(0.0, exOff.getValue("tea2"), 0.001)

    //  رفت‌وبرگشت: فرمِ دست‌نخورده همان قاعده و همان ترتیب (N2)
    val old = rule(PromoCore.Scope(suppliers = listOf("s2", "s1"), exSuppliers = listOf("gone"), exCategories = listOf("ب", "الف")))
    assertEquals(old.scope, PromoForm.of(old).toRule(old).scope)
    val text = PromoText.scopeText(old, ds)
    assertTrue(text, text.contains("تأمین‌کنندهٔ تجارتیِ هرات، پخشِ کابل"))
    assertTrue(text, text.contains("جز تأمین‌کنندهٔ (حذف‌شده)"))
    assertTrue(text, text.contains("جز دستهٔ ب، الف"))

    //  ویرایشگر تراشه‌های تأمین‌کننده و «جز این دسته‌ها/تأمین‌کنندگان» را دارد و هر تراشه طرفِ دیگر را پاک می‌کند
    val src = listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, "ir/vil3ntec/tohid/ui/screens/Promos.kt") }.first { it.isFile }.readText()
    assertTrue(src.contains("onClick = { f = f.copy(suppliers = if (id in f.suppliers) f.suppliers - id else f.suppliers + id, exSuppliers = f.exSuppliers - id) }"))
    assertTrue(src.contains("onClick = { f = f.copy(exSuppliers = if (id in f.exSuppliers) f.exSuppliers - id else f.exSuppliers + id, suppliers = f.suppliers - id) }"))
    assertTrue(src.contains("onClick = { f = f.copy(exCategories = if (cat in f.exCategories) f.exCategories - cat else f.exCategories + cat, categories = f.categories - cat) }"))
    assertTrue(src.contains("(d.suppliers.map { it.id } + rule.scope.suppliers + rule.scope.exSuppliers).distinct()"))
  }
}
