package ir.vil3ntec.tohid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 *  POS-42 — `ExpiryEngine.lots` ردیف‌های مصرفِ سری‌دار (INV-21) را حالا یک بار برای همهٔ کالاها جدا می‌کند، نه یک گذر روی
 *  همهٔ ورودها برای هر کالا. این آزمون همان حسابِ پیشین را (رونوشتِ مو‌به‌موی کدِ پیش از تغییر) روی دفترهای تصادفیِ قطعی
 *  کنارِ راهِ تازه می‌گذارد: همان محموله‌ها، همان مانده، همان ترتیب.
 */
class ExpiryLotsEquivalenceTest {

  /* -------- رونوشتِ کدِ پیشین (پیش از POS-42) — دست نخورد -------- */

  private fun oldCapacities(d: ShopData, productId: String, entries: List<WarehouseEntry>): List<Pair<WarehouseEntry, Double>> {
    val use = HashMap<Pair<String, String>, Double>()
    for (w in d.warehouseEntries) if (w.productId == productId && ExpiryEngine.isLotUse(w)) {
      val k = w.expiryDate to w.batch.trim()
      use[k] = (use[k] ?: 0.0) - w.units
    }
    if (use.isEmpty()) return entries.map { it to it.units }
    return entries.map { e ->
      val k = e.expiryDate to e.batch.trim()
      val u = use[k] ?: 0.0
      val cut = minOf(e.units, u)
      if (cut > 0) use[k] = u - cut
      e to (e.units - maxOf(0.0, cut))
    }
  }

  private fun oldLots(d: ShopData, today: String): List<ExpiryEngine.Lot> {
    val out = ArrayList<ExpiryEngine.Lot>()
    val byProduct = d.warehouseEntries
      .filter(ExpiryEngine::isLot)
      .groupBy { it.productId }
    val products = d.products.associateBy { it.id }
    for ((productId, entries) in byProduct) {
      if (entries.none { it.expiryDate.isNotBlank() }) continue
      val product = products[productId] ?: continue
      var left = ShopStore.stock(d, productId)
      if (left <= 0) continue
      val ordered = oldCapacities(d, productId, entries).sortedWith(
        compareByDescending<Pair<WarehouseEntry, Double>> { it.first.expiryDate.isBlank() }.thenByDescending { it.first.expiryDate }
      )
      for ((e, cap) in ordered) {
        if (left <= 0) break
        val take = minOf(cap, left)
        left -= take
        if (e.expiryDate.isNotBlank() && take > 0) {
          out += ExpiryEngine.Lot(e, product, take, ExpiryEngine.daysLeft(e.expiryDate, today))
        }
      }
    }
    return out.sortedBy { it.daysLeft }
  }

  /* ------------------------- دفترِ تصادفیِ قطعی ------------------------- */

  private val dates = listOf("", "2026-10-01", "2026-11-20", "2026-12-31", "2027-03-15", "2027-03-15", "2028-01-01")
  private val batches = listOf("", "A1", " A1 ", "B2", "C3")

  private fun ledger(seed: Int): ShopData {
    val r = Random(seed)
    val products = List(25) { i -> Product(id = "p$i", name = "کالا $i", unit = "عدد", purchasePrice = 10.0, salePrice = 12.0) }
    var n = 0
    val entries = ArrayList<WarehouseEntry>()
    repeat(120) {
      val p = products[r.nextInt(products.size)]
      val k = r.nextInt(10)
      entries += when {
        //  مصرفِ سری‌دارِ بسته‌بندی (INV-21): ردیفِ منفیِ `repack` با `lotId`
        k < 2 -> WarehouseEntry(
          id = "w${n++}", productId = p.id, units = -(1 + r.nextInt(8)).toDouble(), isAdjustment = true, adjKind = Repack.KIND,
          lotId = "lot${r.nextInt(3)}", expiryDate = dates[r.nextInt(dates.size)], batch = batches[r.nextInt(batches.size)],
        )
        //  خروجیِ بسته‌بندی (محموله است) یا اصلاحِ دیگر (محموله نیست)
        k < 3 -> WarehouseEntry(
          id = "w${n++}", productId = p.id, units = (1 + r.nextInt(6)).toDouble(), isAdjustment = true,
          adjKind = if (r.nextBoolean()) Repack.KIND else "count", expiryDate = dates[r.nextInt(dates.size)], batch = batches[r.nextInt(batches.size)],
        )
        //  مصرفِ بی سری (محموله و مصرفِ سری‌دار هیچ‌کدام نیست)
        k < 4 -> WarehouseEntry(id = "w${n++}", productId = p.id, units = -(1 + r.nextInt(4)).toDouble(), isAdjustment = true, adjKind = Repack.KIND)
        else -> WarehouseEntry(
          id = "w${n++}", productId = p.id, units = (1 + r.nextInt(30)).toDouble(),
          expiryDate = dates[r.nextInt(dates.size)], batch = batches[r.nextInt(batches.size)],
        )
      }
    }
    //  ورودِ کالایی که در فهرستِ کالا نیست (یتیم) — هر دو راه کنارش می‌گذارند
    entries += WarehouseEntry(id = "w${n++}", productId = "ghost", units = 5.0, expiryDate = "2027-01-01")
    val items = List(60) { i ->
      val p = products[r.nextInt(products.size)]
      SaleItem(id = "i$i", saleId = "s${i / 3}", productId = p.id, quantity = (1 + r.nextInt(12)).toDouble())
    }
    return ShopData(products = products, warehouseEntries = entries.shuffled(r), saleItems = items)
  }

  @Test fun `همان محموله‌ها، همان مانده و همان ترتیب — ۲۰۰ دفترِ تصادفی`() {
    var lots = 0
    var withUse = 0
    for (seed in 1..200) {
      val d = ledger(seed)
      for (today in listOf("2026-11-09", "2027-04-01")) {
        val want = oldLots(d, today)
        assertEquals("seed=$seed today=$today", want, ExpiryEngine.lots(d, today))
        lots += want.size
      }
      if (d.warehouseEntries.any(ExpiryEngine::isLotUse)) withUse++
    }
    //  آزمون واقعاً چیزی را می‌سنجد: محموله‌ها و مصرفِ سری‌دار هر دو هستند
    assertTrue("محموله‌ای ساخته نشد", lots > 1_000)
    assertTrue("مصرفِ سری‌دار کم بود", withUse > 150)
  }

  @Test fun `دفترِ بی مصرفِ سری‌دار و دفترِ خالی همان‌اند`() {
    val plain = ledger(7).let { d -> d.copy(warehouseEntries = d.warehouseEntries.filterNot(ExpiryEngine::isLotUse)) }
    assertEquals(oldLots(plain, "2026-11-09"), ExpiryEngine.lots(plain, "2026-11-09"))
    assertEquals(emptyList<ExpiryEngine.Lot>(), ExpiryEngine.lots(ShopData(), "2026-11-09"))
  }
}
