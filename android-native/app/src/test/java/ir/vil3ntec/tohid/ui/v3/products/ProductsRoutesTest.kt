package ir.vil3ntec.tohid.ui.v3.products

import ir.vil3ntec.tohid.ui.v3.nav.RouteKind
import ir.vil3ntec.tohid.ui.v3.nav.V3Modules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** هر مسیرِ کالا با پیوندِ سرد باز می‌شود و برگشت به جای درست می‌رود */
class ProductsRoutesTest {
  private val table = V3Modules.table

  @Test
  fun `سازنده‌های مسیر به الگوی درست می‌رسند`() {
    mapOf(
      ProductsPaths.list(filter = "low") to "products",
      ProductsPaths.item("a b") to "products/{id}",
      ProductsPaths.new(barcode = "626") to "products/new",
      ProductsPaths.edit("7") to "products/{id}/edit",
      ProductsPaths.photo("7") to "products/{id}/photo",
      ProductsPaths.studio("7", "content://media/1?x=1") to "products/{id}/photo/studio",
      ProductsPaths.categories(pick = true, selected = "لبنیات") to "products/categories",
      ProductsPaths.bulkCategory(listOf("1", "2")) to "products/bulk/category",
    ).forEach { (path, pattern) -> assertEquals(path, pattern, table.resolve(path)!!.route.pattern) }
  }

  @Test
  fun `پرسش‌ها سالم برمی‌گردند`() {
    assertEquals("content://media/1?x=1", table.resolve(ProductsPaths.studio("7", "content://media/1?x=1"))!!.args.query("src"))
    assertEquals("1,2", table.resolve(ProductsPaths.bulkCategory(listOf("1", "2")))!!.args.query("ids"))
    assertEquals("لبنیات", table.resolve(ProductsPaths.categories(pick = true, selected = "لبنیات"))!!.args.query("selected"))
    assertEquals("a b", table.resolve(ProductsPaths.item("a b"))!!.args["id"])
    assertEquals("products/categories", ProductsPaths.categories(selected = "x"))
  }

  @Test
  fun `زنجیرهٔ برگشت`() {
    assertEquals(listOf("products", "products/7", "products/7/edit"), table.chain("products/7/edit"))
    assertEquals(listOf("products", "products/7", "products/7/photo/studio?src=a"), table.chain("products/7/photo/studio?src=a"))
    assertEquals(listOf("products", "products/new?barcode=626"), table.chain("products/new?barcode=626"))
  }

  @Test
  fun `گونهٔ هر مسیر (NAV-MAP)`() {
    mapOf(
      "products" to RouteKind.TAB, "products/1" to RouteKind.PAGE, "products/new" to RouteKind.PAGE, "products/1/edit" to RouteKind.PAGE,
      "products/1/photo" to RouteKind.SHEET, "products/1/photo/studio" to RouteKind.PAGE,
      "products/categories" to RouteKind.SHEET, "products/bulk/category" to RouteKind.SHEET,
    ).forEach { (p, k) -> assertNotNull(p, table.resolve(p)); assertEquals(p, k, table.resolve(p)!!.route.kind) }
  }
}
