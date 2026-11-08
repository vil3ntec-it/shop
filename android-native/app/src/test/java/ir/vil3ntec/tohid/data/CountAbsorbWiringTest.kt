package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.i18n.StrStock
import ir.vil3ntec.tohid.ui.v3.stock.MovementsModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  INV-10 روی دفترِ دکان: A آفلاین ۱۰:۰۰ سه فروخت؛ B ساعتِ ۱۰:۳۰ ۱۷ شمرد (فروشِ A را نداشت) و ثبت کرد؛ فروشِ A بعد
 *  رسید ⇒ هر دو دستگاه ۱۷، نه ۱۴. فروش در دفتر می‌ماند و سابقه ردیفِ «پیش از شمارش ثبت شده بود — در شمارش آمده
 *  است» را دارد. پرچمِ `shop.count_absorb` خاموش ⇒ بیت‌به‌بیت همان ۱۴ ِ پیشین.
 */
class CountAbsorbWiringTest {
  private var seq = 0
  private fun id() = "n${seq++}"
  private val t = 1_790_000_000_000L
  private val counted = t + 1_800_000
  private val saved = counted + 60_000

  /** دفترِ هر دو دستگاه پیش از فروشِ A: ۲۰ ورود */
  private val base = ShopData(
    products = listOf(Product(id = "x", name = "روغن", unit = "عدد", purchasePrice = 80.0)),
    warehouseEntries = listOf(WarehouseEntry(id = "w1", productId = "x", units = 20.0, price = 80.0, date = "2026-11-08", createdAt = t - 3_600_000)),
    stockMovements = listOf(StockMovement(id = "m-in", productId = "x", type = "purchase_in", qty = 20.0, refId = "w1", createdAt = t - 3_600_000)),
  )

  /** فروشِ A (همان سه ردیفِ `SalesEngine.record`) */
  private fun lateSale(d: ShopData) = d.copy(
    sales = d.sales + Sale(id = "s1", date = "2026-11-08", status = "completed", createdAt = t),
    saleItems = d.saleItems + SaleItem(id = "i1", saleId = "s1", productId = "x", quantity = 3.0),
    stockMovements = d.stockMovements + StockMovement(id = "m-s1", productId = "x", type = "sale", qty = -3.0, refId = "s1", createdAt = t),
  )

  /** B: «انبارگردانیِ تازه» با لحظهٔ کالا */
  private fun count(d: ShopData, n: Double): ShopData {
    val at = mapOf("x" to counted)
    val r = WarehouseEngine.stockCount(d, mapOf("x" to n), "", "2026-11-08", saved, ::id, CountShift.of(d, at), WarehouseEngine.AdjustKind.COUNT, countedAt = at)
    return (r as WarehouseEngine.Result.Ok).data
  }

  @After fun reset() { CountAbsorbWiring.serverFlag = false; StockLedger.serverFlag = false }

  @Test fun newStockEngineShowsTheSameNumber() {
    //  موتورِ تازهٔ موجودی و بها (`shop.stock_v2`): همان ۱۷ با اصلاحِ نمایشیِ جذب‌شده
    StockLedger.serverFlag = true
    CountAbsorbWiring.serverFlag = true
    val on = StockActions.setMode(base, true, "مالک", "2026-11-08", t - 7_200_000, ::id)
    val after = lateSale(count(on, 17.0))
    assertEquals(17_000L, StockLedger.productView(StockLedger.replay(after), "x").qtyMilli)
    CountAbsorbWiring.serverFlag = false
    assertEquals(14_000L, StockLedger.productView(StockLedger.replay(after), "x").qtyMilli)
  }

  @Test fun countLineCarriesTheMomentAndWhatTheCounterKnew() {
    val b = count(base, 17.0)
    val line = b.warehouseEntries.last()
    assertEquals(counted, line.countedAt)
    assertEquals(20_000L, line.seenMilli)
    assertEquals("count", line.adjKind)
    //  ردیفِ انبارِ دیگر هیچ کدام را ندارد
    assertNull(base.warehouseEntries.single().countedAt)
  }

  @Test fun lateOfflineSaleBeforeTheCountIsNotDeductedTwice() {
    CountAbsorbWiring.serverFlag = true
    val b = count(base, 17.0)
    assertEquals(17.0, ShopStore.stock(b, "x"), 1e-9)
    //  B فروشِ A را می‌گیرد؛ A شمارشِ B را — هر دو همان سندها
    val after = lateSale(b)
    assertEquals(17.0, ShopStore.stock(after, "x"), 1e-9)
    assertEquals(3, after.saleItems.single().quantity.toInt())
    //  سابقه: فروش همان، و ردیفِ جذب با برچسب
    val rows = MovementsModel.days(after, "x").flatMap { it.moves }.map { it.move }
    assertTrue(rows.any { it.type == "sale" && it.qty == -3.0 })
    val absorbed = rows.single { it.type == CountAbsorbWiring.ABSORB_TYPE }
    assertEquals(3.0, absorbed.qty, 1e-9)
    assertEquals(StrStock.countAbsorbed, absorbed.notes)
    //  پرچم خاموش ⇒ همان ۱۴ ِ پیشین
    CountAbsorbWiring.serverFlag = false
    assertEquals(14.0, ShopStore.stock(after, "x"), 1e-9)
    assertTrue(MovementsModel.days(after, "x").flatMap { it.moves }.none { it.move.type == CountAbsorbWiring.ABSORB_TYPE })
  }

  @Test fun saleTheCounterAlreadyHadIsNotAbsorbed() {
    CountAbsorbWiring.serverFlag = true
    val b = count(lateSale(base), 16.0)
    assertEquals(17_000L, b.warehouseEntries.last().seenMilli)
    assertEquals(16.0, ShopStore.stock(b, "x"), 1e-9)
  }

  @Test fun saleAfterTheCountIsDeducted() {
    CountAbsorbWiring.serverFlag = true
    val b = count(base, 19.0)
    val after = b.copy(
      sales = b.sales + Sale(id = "s2", date = "2026-11-08", status = "completed", createdAt = saved + 60_000),
      saleItems = b.saleItems + SaleItem(id = "i2", saleId = "s2", productId = "x", quantity = 3.0),
      stockMovements = b.stockMovements + StockMovement(id = "m-s2", productId = "x", type = "sale", qty = -3.0, refId = "s2", createdAt = saved + 60_000),
    )
    assertEquals(16.0, ShopStore.stock(after, "x"), 1e-9)
  }

  @Test fun momentLaterThanSaveOrOlderThanTheWindowIsClampedOrDropped() {
    assertEquals(saved, CountAbsorbWiring.momentOf(saved + 5, saved))
    assertNull(CountAbsorbWiring.momentOf(saved - 31L * 86_400_000, saved))
    assertNull(CountAbsorbWiring.momentOf(null, saved))
  }
}
