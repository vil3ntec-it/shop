package ir.vil3ntec.tohid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  INV-20 — محل‌های نگه‌داری و انتقال روی موتورِ امروز (`StockPlaces`): موجودیِ هر محل، جمعِ محل‌ها = موجودیِ امروز،
 *  انتقالِ یا-همه-یا-هیچ و یک‌بار برای هر کلید، کلِ موجودی بی تغییر، کمبودِ مبدأ رد.
 */
class StockPlacesTest {

  private var seq = 0
  private val newId: () -> String = { "n${(++seq).toString().padStart(3, '0')}" }
  private val today = "2026-11-02"

  private val base = ShopData(
    products = listOf(
      Product(id = "p1", name = "نوشابه", unit = "عدد", purchasePrice = 50.0),
      Product(id = "p2", name = "برنج", unit = "کیلوگرم", purchasePrice = 80.0),
      Product(id = "p3", name = "روبان", unit = "عدد", purchasePrice = 30.0),
      Product(id = "k1", name = "بستهٔ هدیه", unit = "عدد", kit = listOf(KitPart("p1", 2.0), KitPart("p3", 1.0))),
      Product(id = "s1", name = "بسته‌بندیِ هدیه", unit = "عدد", stockless = true),
    ),
    warehouseEntries = listOf(
      WarehouseEntry(id = "w1", productId = "p1", units = 20.0),
      WarehouseEntry(id = "w2", productId = "p1", units = 10.0, locationId = "انبار"),
      WarehouseEntry(id = "w3", productId = "p2", units = 12.5, locationId = "قفسهٔ ۲"),
      WarehouseEntry(id = "w4", productId = "p3", units = 5.0),
    ),
    sales = listOf(Sale(id = "s1", date = today), Sale(id = "s2", date = today, status = "cancelled")),
    saleItems = listOf(
      SaleItem(id = "i1", saleId = "s1", productId = "p1", quantity = 3.0, returnedQty = 1.0),
      SaleItem(id = "i2", saleId = "s1", productId = "k1", quantity = 1.0, parts = listOf(KitPart("p1", 2.0), KitPart("p3", 1.0))),
      SaleItem(id = "i3", saleId = "s2", productId = "p1", quantity = 7.0),
    ),
    //  تک‌ردیفِ پیش‌نمایشِ قدیمی (`transfer`) هنوز یک انتقال است
    stockMovements = listOf(
      StockMovement(id = "m0", productId = "p2", type = "transfer", qty = 2.5, date = today, locationId = "قفسهٔ ۲", toLocationId = ""),
    ),
  )

  private fun ok(r: StockPlaces.Result): ShopData = (r as StockPlaces.Result.Ok).data

  private fun total(d: ShopData, pid: String): Long = StockPlaces.stockAt(d)[pid].orEmpty().values.sum()

  @Test fun `موجودیِ هر محل و جمعِ محل‌ها همان موجودیِ امروز`() {
    val s = StockPlaces.stockAt(base)
    assertEquals(mapOf("" to 16_000L, "انبار" to 10_000L), s["p1"])
    assertEquals(mapOf("قفسهٔ ۲" to 10_000L, "" to 2_500L), s["p2"])
    assertEquals(mapOf("" to 4_000L), s["p3"])
    assertTrue("کیت و خدماتی موجودیِ خودشان را ندارند", "k1" !in s && "s1" !in s)
    for (pid in listOf("p1", "p2", "p3")) assertEquals(StockLedger.milli(ShopStore.stock(base, pid)), total(base, pid))
    assertEquals(listOf("", "انبار", "قفسهٔ ۲"), StockPlaces.places(base))
  }

  @Test fun `انتقال هر دو ردیف یا هیچ و یک بار برای هر کلید و کل دست‌نخورده`() {
    val before = listOf("p1", "p2", "p3").associateWith { ShopStore.stock(base, it) }
    val d = ok(StockPlaces.transfer(base, "op1", "p1", "", "انبار", 6_000, "پشتِ دکان", "کریم", today, 500, newId))
    val rows = d.stockMovements.filter { it.refId == "op1" }
    assertEquals(listOf(StockLedger.TRANSFER_OUT, StockLedger.TRANSFER_IN), rows.map { it.type })
    assertEquals("op1", rows[0].id)
    assertEquals(0.0, rows.sumOf { it.qty }, 0.0)
    assertEquals(1, d.auditLog.count { it.type == "stock_transfer" && it.refId == "op1" })
    assertEquals(mapOf("" to 10_000L, "انبار" to 16_000L), StockPlaces.stockAt(d)["p1"])
    for ((pid, q) in before) assertEquals(q, ShopStore.stock(d, pid), 0.0)
    //  همان کلید دوباره (دو ضربه، تکرار پس از قطع) ⇒ هیچ
    assertSame(StockPlaces.Result.Already, StockPlaces.transfer(d, "op1", "p1", "", "انبار", 6_000, "", "", today, 600, newId))
    assertTrue(StockPlaces.has(d, "op1"))
  }

  @Test fun `دفترِ موجودی همان محل‌های انتقالِ موتورِ امروز را می‌خواند`() {
    //  بی فروشِ کیت: `StockLedger` کیت را هنوز نمی‌فروشد و فروشِ پیشینش را به اجزا پخش نمی‌کند
    val plain = base.copy(saleItems = base.saleItems.filter { it.productId != "k1" })
    val d = ok(StockPlaces.transfer(plain, "op1", "p1", "", "انبار", 6_000, "", "کریم", today, 500, newId))
    val pv = StockLedger.productView(StockLedger.replay(d), "p1")
    assertEquals(mapOf("" to 12_000L, "انبار" to 16_000L), pv.locations.associate { it.location to it.qtyMilli }.filterValues { it != 0L })
    assertEquals(StockPlaces.stockAt(d)["p1"], pv.locations.associate { it.location to it.qtyMilli })
  }

  @Test fun `کمبودِ مبدأ و کیت و خدماتی و همان محل رد می‌شوند بی هیچ ردیف`() {
    fun bad(r: StockPlaces.Result) = assertTrue(r.toString(), r is StockPlaces.Result.Failed)
    val r = StockPlaces.transfer(base, "op2", "p1", "انبار", "", 10_001, "", "", today, 500, newId)
    bad(r)
    assertTrue((r as StockPlaces.Result.Failed).message.contains("انبار"))
    bad(StockPlaces.transfer(base, "op2", "k1", "", "انبار", 1_000, "", "", today, 500, newId))
    bad(StockPlaces.transfer(base, "op2", "s1", "", "انبار", 1_000, "", "", today, 500, newId))
    bad(StockPlaces.transfer(base, "op2", "p1", "انبار", " انبار ", 1_000, "", "", today, 500, newId))
    bad(StockPlaces.transfer(base, "op2", "p1", "", "انبار", 0, "", "", today, 500, newId))
    bad(StockPlaces.transfer(base, "", "p1", "", "انبار", 1_000, "", "", today, 500, newId))
    //  تمامِ موجودیِ مبدأ مجاز است، بیشتر نه
    val d = ok(StockPlaces.transfer(base, "op3", "p1", "انبار", "", 10_000, "", "", today, 500, newId))
    assertEquals(mapOf("" to 26_000L, "انبار" to 0L), StockPlaces.stockAt(d)["p1"])
    assertEquals(base.warehouseEntries, d.warehouseEntries)
  }

  @Test fun `محل افزوده و بایگانی می‌شود و محلِ دارای کالا بایگانی نمی‌شود`() {
    var d = ok(StockPlaces.addPlace(base, "a1", "  قفسهٔ   ۳ ", "کریم", today, 100))
    assertEquals(listOf("قفسهٔ ۳"), StockPlaces.registered(d))
    assertEquals(listOf("", "انبار", "قفسهٔ ۲", "قفسهٔ ۳"), StockPlaces.places(d))
    assertTrue(StockPlaces.addPlace(d, "a2", "قفسهٔ ۳", "", today, 110) is StockPlaces.Result.Failed)
    assertTrue(StockPlaces.addPlace(d, "a2", StockActions.MAIN_LABEL, "", today, 110) is StockPlaces.Result.Failed)
    assertSame(StockPlaces.Result.Already, StockPlaces.addPlace(d, "a1", "دیگر", "", today, 110))
    d = ok(StockPlaces.transfer(d, "t1", "p3", "", "قفسهٔ ۳", 1_000, "", "", today, 120, newId))
    assertTrue(StockPlaces.archivePlace(d, "a3", "قفسهٔ ۳", "", today, 130) is StockPlaces.Result.Failed)
    d = ok(StockPlaces.transfer(d, "t2", "p3", "قفسهٔ ۳", "", 1_000, "", "", today, 140, newId))
    d = ok(StockPlaces.archivePlace(d, "a4", "قفسهٔ ۳", "", today, 150))
    assertEquals(emptyList<String>(), StockPlaces.registered(d))
    assertEquals(listOf("", "انبار", "قفسهٔ ۲"), StockPlaces.places(d))
    assertEquals(StockLedger.milli(ShopStore.stock(d, "p3")), total(d, "p3"))
  }
}
