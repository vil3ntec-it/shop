package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.stock.Pick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  INV-20 — سری و انقضا در انتقالِ موتورِ امروز (`StockPlaces.transfer` ⇐ `StockLedger.placeBooks`، همان هسته و FEFO ِ
 *  موتورِ تازه): FEFO ِ پیش‌فرض، سریِ برگزیده، هیچ سریِ منفی، انقضای ماندگار در مقصد، کالای بی سری همان امروز، فروش و
 *  شمارش پس از انتقال، جمع‌های ناوردا و گزارشِ انقضا در محلِ تازه.
 */
class StockPlacesLotsTest {

  private var seq = 0
  private val newId: () -> String = { "n${(++seq).toString().padStart(3, '0')}" }
  private val today = "2026-11-08"
  private val back = "انبار"

  //  ۱۰ «شیر» در دو سری: ۶ تا با انقضای آذرِ ۱۴۰۵ (A) و ۴ تا با انقضای بهمنِ ۱۴۰۵ (B)؛ «برنج» بی سری
  private val base = ShopData(
    products = listOf(
      Product(id = "p1", name = "شیر", unit = "عدد", purchasePrice = 10.0),
      Product(id = "p2", name = "برنج", unit = "کیلوگرم", purchasePrice = 80.0),
    ),
    warehouseEntries = listOf(
      WarehouseEntry(id = "w1", productId = "p1", units = 6.0, price = 10.0, batch = "A", expiryDate = "2026-12-15", createdAt = 100),
      WarehouseEntry(id = "w2", productId = "p1", units = 4.0, price = 12.0, batch = "B", expiryDate = "2027-02-10", createdAt = 110),
      WarehouseEntry(id = "w3", productId = "p2", units = 10.0, price = 80.0, createdAt = 120),
    ),
  )

  private fun ok(r: StockPlaces.Result): ShopData = (r as StockPlaces.Result.Ok).data

  private fun move(d: ShopData, key: String, pid: String, from: String, to: String, milli: Long, lots: List<Pick>? = null, now: Long = 500) =
    StockPlaces.transfer(d, key, pid, from, to, milli, "", "کریم", today, now, newId, lots = lots)

  /** محل ⇒ (سری ⇒ هزارم) */
  private fun lots(d: ShopData, pid: String): Map<String, Map<String, Long>> =
    StockPlaces.lotsAt(d, pid).mapValues { (_, ls) -> ls.associate { it.lotId to it.qtyMilli } }

  private fun expiry(d: ShopData, pid: String, place: String, lot: String): String? =
    StockPlaces.lotsAt(d, pid)[place].orEmpty().single { it.lotId == lot }.expiryDate

  /** جمعِ سری‌های هر محل = `stockAt` و جمعِ محل‌ها = موجودیِ امروز */
  private fun invariant(d: ShopData, pid: String) {
    val byPlace = StockPlaces.stockAt(d)[pid].orEmpty().filterValues { it != 0L }
    assertEquals(byPlace, lots(d, pid).mapValues { it.value.values.sum() }.filterValues { it != 0L })
    assertEquals(StockLedger.milli(ShopStore.stock(d, pid)), byPlace.values.sum())
  }

  @Test fun `FEFO ِ پیش‌فرض اول زودترین سری را می‌برد و انقضا در مقصد می‌ماند`() {
    val d = ok(move(base, "op1", "p1", "", back, 7_000))
    val rows = d.stockMovements.filter { it.refId == "op1" }
    assertEquals(listOf("op1" to "A", "n003" to "B"), rows.filter { it.type == StockLedger.TRANSFER_OUT }.map { it.id to it.lotId })
    assertEquals(listOf(6.0, 1.0), rows.filter { it.type == StockLedger.TRANSFER_IN }.map { it.qty })
    assertEquals(0.0, rows.sumOf { it.qty }, 0.0)
    assertEquals(mapOf("" to mapOf("B" to 3_000L), back to mapOf("A" to 6_000L, "B" to 1_000L)), lots(d, "p1"))
    assertEquals("2026-12-15", expiry(d, "p1", back, "A"))
    assertEquals("2027-02-10", expiry(d, "p1", back, "B"))
    //  همهٔ ۱۰ (۶ ِ آذر + ۴ ِ بهمن) ⇒ هر دو سری با انقضای خودشان
    val all = ok(move(base, "op2", "p1", "", back, 10_000))
    assertEquals(mapOf(back to mapOf("A" to 6_000L, "B" to 4_000L)), lots(all, "p1"))
    //  موتورِ تازه همان ردیف‌ها را همان‌طور می‌خواند (سری و انقضا در مقصد)
    val pv = StockLedger.productView(StockLedger.replay(d), "p1")
    assertEquals(
      setOf(Triple("", "B", 3_000L), Triple(back, "A", 6_000L), Triple(back, "B", 1_000L)),
      pv.lots.map { Triple(it.location, it.lotId, it.qtyMilli) }.toSet(),
    )
    assertEquals("2026-12-15", pv.lots.single { it.location == back && it.lotId == "A" }.expiryDate)
    invariant(d, "p1")
    //  همان کلید دوباره ⇒ هیچ
    assertSame(StockPlaces.Result.Already, move(d, "op1", "p1", "", back, 1_000))
  }

  @Test fun `سریِ برگزیده همان سری‌ها را با همان مقدار می‌برد`() {
    val d = ok(move(base, "op1", "p1", "", back, 5_000, listOf(Pick("B", 4_000), Pick("A", 1_000))))
    val outs = d.stockMovements.filter { it.refId == "op1" && it.type == StockLedger.TRANSFER_OUT }
    assertEquals(listOf("A" to -1.0, "B" to -4.0), outs.map { it.lotId to it.qty })
    assertEquals(mapOf("" to mapOf("A" to 5_000L), back to mapOf("A" to 1_000L, "B" to 4_000L)), lots(d, "p1"))
    assertEquals("2027-02-10", expiry(d, "p1", back, "B"))
    invariant(d, "p1")
  }

  @Test fun `هیچ سری‌ای منفی نمی‌شود و انتخابِ نادرست بی هیچ ردیف رد می‌شود`() {
    fun bad(r: StockPlaces.Result) = assertTrue(r.toString(), r is StockPlaces.Result.Failed)
    val more = move(base, "op1", "p1", "", back, 5_000, listOf(Pick("B", 5_000)))
    bad(more)
    assertTrue((more as StockPlaces.Result.Failed).message.contains("B"))
    bad(move(base, "op1", "p1", "", back, 5_000, listOf(Pick("B", 4_000))))
    bad(move(base, "op1", "p1", "", back, 1_000, listOf(Pick("Z", 1_000))))
    bad(move(base, "op1", "p1", "", back, 1_000, listOf(Pick("A", 0))))
    bad(move(base, "op1", "p1", back, "", 1_000, listOf(Pick("A", 1_000))))
    //  بی سری فقط پس از همهٔ سری‌های مبدأ (ردیفِ بی `lotId` را دفتر به FEFO می‌خواند)
    val mixed = base.copy(warehouseEntries = base.warehouseEntries + WarehouseEntry(id = "w4", productId = "p1", units = 2.0, createdAt = 130))
    bad(move(mixed, "op1", "p1", "", back, 2_000, listOf(Pick("", 2_000))))
    val rest = ok(move(mixed, "op1", "p1", "", back, 12_000, listOf(Pick("A", 6_000), Pick("B", 4_000), Pick("", 2_000))))
    assertEquals(mapOf(back to mapOf("A" to 6_000L, "B" to 4_000L, "" to 2_000L)), lots(rest, "p1"))
    //  FEFO ِ مبدأ با بی سری: سری‌ها اول، بی سری آخر
    val fefo = ok(move(mixed, "op2", "p1", "", back, 11_000))
    assertEquals(mapOf("" to mapOf("" to 1_000L), back to mapOf("A" to 6_000L, "B" to 4_000L, "" to 1_000L)), lots(fefo, "p1"))
    invariant(fefo, "p1")
    //  کالای بی سری سریِ برگزیده ندارد
    bad(move(base, "op3", "p2", "", back, 1_000, listOf(Pick("A", 1_000))))
  }

  @Test fun `کالای بی سری همان یک جفتِ امروز را می‌نویسد`() {
    val d = ok(move(base, "op1", "p2", "", back, 3_000))
    var s2 = 0
    val id2: () -> String = { "n${(++s2).toString().padStart(3, '0')}" }
    val p2 = base.products.single { it.id == "p2" }
    assertEquals(StockActions.transferRows(base, p2, "", back, 3_000, null, "", "کریم", today, 500, "op1", id2), d)
    assertEquals(listOf(null, null), d.stockMovements.map { it.lotId })
    assertEquals(mapOf("" to mapOf("" to 7_000L), back to mapOf("" to 3_000L)), lots(d, "p2"))
    //  انتخابِ «بی سری» برای کالای بی سری همان FEFO است
    val again = ok(move(base, "op1", "p2", "", back, 3_000, listOf(Pick("", 3_000))))
    assertEquals(d.stockMovements.map { it.copy(id = "") }, again.stockMovements.map { it.copy(id = "") })
  }

  @Test fun `انتقال سپس فروش FEFO را در محلِ تازه می‌گیرد`() {
    var d = ok(move(base, "op1", "p1", "", back, 6_000))
    assertEquals(mapOf("" to mapOf("B" to 4_000L), back to mapOf("A" to 6_000L)), lots(d, "p1"))
    //  فروشِ صندوق (بی محل ⇒ محلِ اصلی) از B ِ همان‌جا؛ فروشِ ثبت‌شده در «انبار» از A ِ همان‌جا
    d = d.copy(
      sales = listOf(Sale(id = "s1", date = today, createdAt = 600), Sale(id = "s2", date = today, createdAt = 610)),
      saleItems = listOf(
        SaleItem(id = "i1", saleId = "s1", productId = "p1", quantity = 1.0),
        SaleItem(id = "i2", saleId = "s2", productId = "p1", quantity = 2.0, locationId = back),
      ),
    )
    assertEquals(mapOf("" to mapOf("B" to 3_000L), back to mapOf("A" to 4_000L)), lots(d, "p1"))
    invariant(d, "p1")
    //  و انتقالِ بعدی از «انبار» همان A ِ مانده با همان انقضا را برمی‌گرداند
    val back2 = ok(move(d, "op2", "p1", back, "", 4_000, now = 700))
    assertEquals(mapOf("" to mapOf("A" to 4_000L, "B" to 3_000L)), lots(back2, "p1"))
    assertEquals("2026-12-15", expiry(back2, "p1", "", "A"))
    invariant(back2, "p1")
  }

  @Test fun `انتقال سپس شمارش فقط سری‌های محلِ اصلی را کم می‌کند`() {
    var d = ok(move(base, "op1", "p1", "", back, 7_000))
    d = (WarehouseEngine.stockCount(d, mapOf("p1" to 8.0), "قفسه", today, 600, newId) as WarehouseEngine.Result.Ok).data
    assertEquals(8.0, ShopStore.stock(d, "p1"), 0.0)
    assertEquals(mapOf("" to mapOf("B" to 1_000L), back to mapOf("A" to 6_000L, "B" to 1_000L)), lots(d, "p1"))
    invariant(d, "p1")
  }

  @Test fun `جمعِ کل و جمعِ هر محل با هر انتقالِ سری‌دار دست نمی‌خورد`() {
    val before = base.products.associate { it.id to ShopStore.stock(base, it.id) }
    var d = base
    d = ok(move(d, "op1", "p1", "", back, 7_000, now = 500))
    d = ok(move(d, "op2", "p1", back, "قفسهٔ ۲", 2_000, listOf(Pick("B", 1_000), Pick("A", 1_000)), now = 510))
    d = ok(move(d, "op3", "p1", "قفسهٔ ۲", "", 2_000, now = 520))
    d = ok(move(d, "op4", "p2", "", back, 4_000, now = 530))
    for ((pid, q) in before) {
      assertEquals(q, ShopStore.stock(d, pid), 0.0)
      invariant(d, pid)
    }
    assertEquals(mapOf("" to mapOf("A" to 1_000L, "B" to 4_000L), back to mapOf("A" to 5_000L)), lots(d, "p1"))
    assertEquals(base.warehouseEntries, d.warehouseEntries)
    //  هر کار جمعِ صفر
    for (k in listOf("op1", "op2", "op3", "op4")) assertEquals(0.0, d.stockMovements.filter { it.refId == k }.sumOf { it.qty }, 1e-9)
  }

  @Test fun `گزارشِ انقضا محموله را با همان عدد در محلِ تازه می‌بیند`() {
    assertEquals(emptyMap<String, List<String>>(), PlaceStock.lotPlaces(base, ExpiryEngine.lots(base, today)))
    val d = ok(move(base, "op1", "p1", "", back, 6_000))
    //  تخفیفِ نزدیکِ انقضا (PRM-10) و گزارش همان محموله‌ها، همان مانده و همان انقضا را می‌بینند
    assertEquals(ExpiryEngine.lots(base, today), ExpiryEngine.lots(d, today))
    assertEquals(mapOf("w1" to listOf(back), "w2" to listOf(StockPlaces.label(""))), PlaceStock.lotPlaces(d, ExpiryEngine.lots(d, today)))
  }
}
