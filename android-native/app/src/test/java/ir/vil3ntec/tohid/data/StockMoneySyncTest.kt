package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.sync.v1.LedgerDiff
import ir.vil3ntec.tohid.sync.v3.V3Convert
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  پول و کاربرِ موتورِ تازهٔ موجودی در همگام‌سازی و دفتر:
 *  • هزینهٔ جانبی **دقیق** به صدمِ افغانی (`landedCostMinor`) از Sync v1 ⇐ v3 ⇐ دفتر بی یک صدم تغییر برمی‌گردد؛
 *  • کاربرِ برگشت از `by` ِ برگشت (RET، #318) خوانده می‌شود، نه «legacy»؛
 *  • برگشتِ ضایعات (ردیفِ اصلاحیِ منفی + حرکتِ `damage`، یا `adjKind = damage`) در «خراب»ِ دفترِ موجودی است.
 */
class StockMoneySyncTest {

  private val hlc = "001760000000000:000000:dev-x"

  /** یک opِ v1 ⇐ v3 (همان مبدلِ بومی) ⇐ اثرِ سرور ⇐ دفترِ دستگاهِ دیگر */
  private fun roundTrip(d: ShopData): ShopData {
    val ops = LedgerDiff.diff(LedgerDiff.shadowOf(ShopData()), LedgerDiff.toJson(d), now = 1)
    assertTrue(ops.isNotEmpty())
    val back = ops.map { op ->
      val r = V3Convert.fromLegacyOp(op, hlc)
      assertTrue("${op.table}: ${r.reason}", r.ok && r.native)
      val insert = r.ops.single()
      val item = buildJsonObject {
        put("op_id", insert.getValue("op_id"))
        put("table", insert.getValue("table"))
        put("row_id", insert.getValue("row_id"))
        put("kind", insert.getValue("kind"))
        put("effect", buildJsonObject { put("set", insert.getValue("fields")) })
      }
      V3Convert.effectToLegacy(item)!!
    }
    return LedgerDiff.fromJson(LedgerDiff.applyRemote(LedgerDiff.toJson(ShopData()), back))
  }

  @Test
  fun landedCostMinorTravelsExactlyThroughV3() {
    //  مبلغی که در Double دقیق نیست (۰٫۱ + ۰٫۲) و مبلغِ بزرگ نزدیکِ سقفِ JS
    val w1 = WarehouseEntry(id = "w1", productId = "p1", units = 3.0, price = 0.1, landedCost = 0.3, landedCostMinor = 30, createdAt = 10)
    val w2 = WarehouseEntry(id = "w2", productId = "p1", units = 1.0, price = 1.0, landedCost = 90_071_992_547_409.91, landedCostMinor = 9_007_199_254_740_991, createdAt = 11)
    val pu = Purchase(id = "pu1", productId = "p1", supplierId = "s1", totalAmount = 10.0, landedCost = 12.34, landedCostMinor = 1234, createdAt = 12)
    val d = ShopData(warehouseEntries = listOf(w1, w2), purchases = listOf(pu))

    //  در opِ v3 همان عددِ صحیح است (بی تبدیلِ دوم؛ `landedCost` در `V3Schema.money` نیست)
    val op = LedgerDiff.diff(LedgerDiff.shadowOf(ShopData()), LedgerDiff.toJson(d), now = 1).first { it.rowId == "w2" }
    val fields = V3Convert.fromLegacyOp(op, hlc).ops.single().getValue("fields") as JsonObject
    assertEquals(9_007_199_254_740_991L, fields.getValue("landedCostMinor").jsonPrimitive.long)
    assertFalse("landedCostMinorMinor" in fields)

    val got = roundTrip(d)
    assertEquals(listOf(w1, w2), got.warehouseEntries)
    assertEquals(listOf(pu), got.purchases)
    assertEquals(9_007_199_254_740_991L, StockLedger.landedMinorOf(got.warehouseEntries[1]))
  }

  @Test
  fun ledgerPrefersExactMinorAndFallsBackToDouble() {
    assertEquals(30L, StockLedger.landedMinorOf(WarehouseEntry(id = "a", landedCost = 0.30000000000000004, landedCostMinor = 30)))
    assertEquals(5000L, StockLedger.landedMinorOf(WarehouseEntry(id = "b", landedCost = 50.0)))
    assertEquals(0L, StockLedger.landedMinorOf(WarehouseEntry(id = "c")))
  }

  @Test
  fun writersSetBothLandedFields() {
    val d = ShopData(products = listOf(Product(id = "p1", name = "شیر", unit = "عدد")))
    val ok = WarehouseEngine.addEntry(
      d, WarehouseEngine.EntryDraft(productId = "p1", units = 3.0, price = 10.0, landedCost = 0.3),
      "2026-10-01", 1000L, { "w1" },
    ) as WarehouseEngine.Result.Ok
    val w = ok.data.warehouseEntries.single()
    assertEquals(0.3, w.landedCost!!, 0.0)
    assertEquals(30L, w.landedCostMinor)
    //  خاموش: هیچ‌کدام نوشته نمی‌شود
    val off = WarehouseEngine.addEntry(
      d, WarehouseEngine.EntryDraft(productId = "p1", units = 3.0, price = 10.0),
      "2026-10-01", 1000L, { "w2" },
    ) as WarehouseEngine.Result.Ok
    val raw = LedgerDiff.toJson(off.data).getValue("warehouseEntries").toString()
    assertFalse(raw, "landedCost" in raw)
  }

  private fun saleWithReturn(by: String?): ShopData = ShopData(
    products = listOf(Product(id = "p1", name = "شیر", unit = "عدد")),
    warehouseEntries = listOf(WarehouseEntry(id = "w1", productId = "p1", units = 5.0, price = 10.0, date = "2026-10-01", createdAt = 1000)),
    sales = listOf(Sale(id = "s1", date = "2026-10-02", createdAt = 2000, seller = "مریم")),
    saleItems = listOf(SaleItem(id = "i1", saleId = "s1", productId = "p1", quantity = 2.0, unitPrice = 20.0, totalPrice = 40.0, returnedQty = 1.0)),
    saleReturns = listOf(SaleReturn(id = "r1", saleId = "s1", saleItemId = "i1", productId = "p1", quantity = 1.0, amount = 20.0, reason = "خراب بود", date = "2026-10-03", createdAt = 3000, by = by)),
  )

  @Test
  fun returnUserComesFromBy() {
    val withBy = StockLedger.replay(saleWithReturn("کریم")).rows.single { it.type == "customer_return" }
    assertEquals("کریم", withBy.userId)
    val old = StockLedger.replay(saleWithReturn(null)).rows.single { it.type == "customer_return" }
    assertEquals(StockLedger.LEGACY_USER, old.userId)
  }

  @Test
  fun damagedReturnShowsAsDamage() {
    //  شکلِ امروزِ #318: ردیفِ اصلاحیِ منفی بی `adjKind` + حرکتِ `damage` با همان کالا و همان لحظه
    val base = saleWithReturn("کریم")
    val shape318 = base.copy(
      warehouseEntries = base.warehouseEntries + WarehouseEntry(
        id = "w-dmg", productId = "p1", units = -1.0, date = "2026-10-03", notes = "ضایعاتِ برگشتی ر-۱ — خراب بود",
        isAdjustment = true, createdAt = 3000,
      ),
      stockMovements = listOf(
        StockMovement(id = "m1", productId = "p1", type = "customer_return", qty = 1.0, date = "2026-10-03", refId = "r1", createdAt = 3000),
        StockMovement(id = "m2", productId = "p1", type = "damage", qty = -1.0, date = "2026-10-03", refId = "r1", createdAt = 3000),
      ),
    )
    val v = StockLedger.replay(shape318)
    val dmg = v.rows.single { it.type == "damage" }
    assertEquals(1000L, dmg.qtyMilli.let { kotlin.math.abs(it) })
    assertEquals("کریم", dmg.userId)
    assertTrue(StockLedger.filterRows(v.rows, StockLedger.RowFilter(types = setOf("damage"))).isNotEmpty())
    //  موجودی همان موتورِ امروز: ۵ − ۲ + ۱ − ۱ = ۳
    assertEquals(3000L, StockLedger.productView(v, "p1").qtyMilli)

    //  همان برگشت با `adjKind = damage` (پس از ادغام با #315) — و بی یادداشت
    val tagged = shape318.copy(
      warehouseEntries = base.warehouseEntries + WarehouseEntry(
        id = "w-dmg", productId = "p1", units = -1.0, date = "2026-10-03", isAdjustment = true, createdAt = 3001, adjKind = "damage",
      ),
    )
    assertEquals(1, StockLedger.replay(tagged).rows.count { it.type == "damage" })
  }
}
