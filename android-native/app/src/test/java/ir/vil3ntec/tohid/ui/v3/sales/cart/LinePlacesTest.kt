package ir.vil3ntec.tohid.ui.v3.sales.cart

import ir.vil3ntec.tohid.data.KitPart
import ir.vil3ntec.tohid.data.PlaceQty
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SaleItem
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.StockLedger
import ir.vil3ntec.tohid.data.StockPlaces
import ir.vil3ntec.tohid.data.WarehouseEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  POS-16 — «موجودی در محل‌ها» از صندوق: برگهٔ ردیفِ سبد (`sale/line/{i}`) موجودیِ همان کالا را در هر محلِ نگه‌داری
 *  (INV-20، `StockPlaces.stockAt`) نشان می‌دهد — فقط با «محل‌های نگه‌داری» ِ روشنِ همین دستگاه و فقط وقتی محلی جز محلِ
 *  اصلی عدد دارد؛ جمعِ محل‌ها همان موجودیِ امروز. خاموش ⇒ برگه همان امروز.
 */
class LinePlacesTest {

  private val today = "2026-11-03"
  private val base = ShopData(
    products = listOf(
      Product(id = "p1", name = "نوشابه", unit = "عدد", salePrice = 60.0),
      Product(id = "p2", name = "صابون", unit = "عدد", salePrice = 40.0),
      Product(id = "k1", name = "بستهٔ هدیه", unit = "عدد", kit = listOf(KitPart("p1", 2.0))),
      Product(id = "s1", name = "بسته‌بندی", unit = "عدد", stockless = true),
    ),
    warehouseEntries = listOf(
      WarehouseEntry(id = "w1", productId = "p1", units = 20.0),
      WarehouseEntry(id = "w2", productId = "p1", units = 10.0, locationId = "انبارِ پشتی"),
      WarehouseEntry(id = "w3", productId = "p1", units = 4.0, locationId = "قفسهٔ ۲"),
      WarehouseEntry(id = "w4", productId = "p2", units = 8.0),
    ),
    sales = listOf(Sale(id = "s1", date = today)),
    saleItems = listOf(SaleItem(id = "i1", saleId = "s1", productId = "p1", quantity = 3.0)),
  )

  private fun line(pid: String) = SalesEngine.CartLine(pid, 1.0)

  @Test fun `روشن ⇒ هر محل با موجودیِ خودش، محلِ اصلی اول و جمع همان موجودیِ امروز`() {
    val info = LineModel.info(base, line("p1"), places = true)
    assertEquals(
      listOf(Triple("", StockPlaces.label(""), 17_000L), Triple("انبارِ پشتی", "انبارِ پشتی", 10_000L), Triple("قفسهٔ ۲", "قفسهٔ ۲", 4_000L)),
      info.places.map { Triple(it.place, it.label, it.milli) },
    )
    assertEquals(StockLedger.milli(ShopStore.stock(base, "p1")), info.places.sumOf { it.milli })
    assertEquals(ir.vil3ntec.tohid.qty(10.0) + " عدد", LineModel.placeQtyText(info.places[1], "عدد"))
  }

  @Test fun `خاموش یا فقط محلِ اصلی یا کیت و خدماتی و قلمِ آزاد ⇒ هیچ`() {
    assertEquals(emptyList<PlaceQty>(), LineModel.info(base, line("p1")).places)
    assertEquals(emptyList<PlaceQty>(), LineModel.info(base, line("p2"), places = true).places)
    assertEquals(emptyList<PlaceQty>(), LineModel.info(base, line("k1"), places = true).places)
    assertEquals(emptyList<PlaceQty>(), LineModel.info(base, line("s1"), places = true).places)
    assertEquals(emptyList<PlaceQty>(), LineModel.info(base, SalesEngine.CartLine("free-1", 1.0, 25.0, "کیسه"), places = true).places)
  }

  @Test fun `انتقال میانِ محل‌ها در برگه دیده می‌شود و کل همان می‌ماند`() {
    var n = 0
    val moved = (StockPlaces.transfer(base, "op1", "p1", "انبارِ پشتی", "", 6_000, "", "کریم", today, 1, { "m${++n}" })
      as StockPlaces.Result.Ok).data
    val places = LineModel.info(moved, line("p1"), places = true).places.associate { it.place to it.milli }
    assertEquals(mapOf("" to 23_000L, "انبارِ پشتی" to 4_000L, "قفسهٔ ۲" to 4_000L), places)
    assertEquals(StockLedger.milli(ShopStore.stock(moved, "p1")), places.values.sum())
  }

  private fun src(rel: String): String =
    listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, rel) }.firstOrNull { it.isFile }?.readText() ?: error("$rel پیدا نشد (cwd=${File(".").absoluteFile})")

  @Test fun `برگهٔ ردیف فقط با تنظیمِ محل‌های همین دستگاه و بخش فقط با محل`() {
    val route = src("ir/vil3ntec/tohid/ui/v3/sales/cart/CartSheetRoutes.kt")
    assertTrue(route.contains("val placesOn = remember { StockPlacesPref.places(context) }"))
    assertTrue(route.contains("LineModel.info(data, it, places = placesOn)"))
    val sheet = src("ir/vil3ntec/tohid/ui/v3/sales/cart/LineSheet.kt")
    assertTrue(sheet.contains("if (info.places.isNotEmpty())"))
    assertTrue(sheet.contains("StrPlaces.atTill"))
  }
}
