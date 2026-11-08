package ir.vil3ntec.tohid.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.MessageDigest

/**
 *  INV-21 — «دستورِ ساخت و سریِ تولید» خاموش (پیش‌فرض) ⇒ بسته‌بندی و ساختِ بسته بایت‌به‌بایت همان امروز: ردیف‌ها، دفترِ
 *  موجودیِ موتورِ تازه (`StockLedger`)، محموله‌های موتورِ امروز (`ExpiryEngine`)، سری‌های هر محل (`StockPlaces.lotsAt`)
 *  و JSON ِ همگام‌سازی. SHA-256 روی پایهٔ همین شاخه (`origin/claude/stock-places`، پیش از INV-21 ِ BOM) گرفته شد؛ این
 *  پرونده فقط API ِ همان پایه را صدا می‌زند تا روی هر دو کامپایل شود.
 */
class RepackBomOffTest {

  private var seq = 0
  private val newId: () -> String = { "f${(++seq).toString().padStart(3, '0')}" }
  private val json = Json { encodeDefaults = true }
  private val today = "2026-11-08"

  private val base = ShopData(
    products = listOf(
      Product(id = "C", name = "شیرِ خشک", unit = "کیلو", purchasePrice = 11.0),
      Product(id = "B", name = "بستهٔ صبحانه", unit = "بسته", purchasePrice = 0.0),
      Product(id = "D", name = "پاکتِ شیر", unit = "عدد", purchasePrice = 6.0),
      Product(id = "R", name = "روبان", unit = "عدد", purchasePrice = 0.5),
    ),
    warehouseEntries = listOf(
      WarehouseEntry(id = "wx", productId = "C", units = 3.0, price = 10.0, expiryDate = "2026-12-01", batch = "X", createdAt = 10),
      WarehouseEntry(id = "wy", productId = "C", units = 2.0, price = 13.0, expiryDate = "2027-02-01", batch = "Y", createdAt = 20),
      WarehouseEntry(id = "wz", productId = "C", units = 4.0, price = 11.0, batch = "Z", createdAt = 30),
      WarehouseEntry(id = "wr", productId = "R", units = 10.0, price = 0.5, createdAt = 40, locationId = "انبار"),
    ),
  )

  private fun ok(r: StockPlaces.Result): ShopData = (r as StockPlaces.Result.Ok).data

  private fun scenario(): String {
    var d = base
    d = ok(Repack.breakPack(d, "op1", "C", 2_000, "D", 2_000, null, "", "کریم", today, 100, newId))
    d = ok(Repack.build(d, "op2", "B", 1_000, listOf(Repack.Part("C", 3_500), Repack.Part("R", 2_000)), null, "", "کریم", today, 110, newId))
    d = ok(StockPlaces.transfer(d, "t1", "C", "", "انبار", 1_000, "پشتِ دکان", "کریم", today, 120, newId))
    d = ok(Repack.build(d, "op3", "B", 1_000, listOf(Repack.Part("C", 1_000), Repack.Part("R", 1_000)), "انبار", "", "کریم", today, 130, newId))
    val out = StringBuilder()
    val v = StockLedger.replay(d)
    v.rows.forEach { out.append(it).append('\n') }
    d.products.forEach { out.append(StockLedger.productView(v, it.id)).append('\n') }
    out.append(v.tasks).append('\n').append(v.errors).append('\n')
    ExpiryEngine.lots(d, today).forEach { out.append(it.entry.id).append(' ').append(it.remaining).append('\n') }
    d.products.forEach { out.append(ExpiryEngine.take(d, it.id, 1_500)).append('\n') }
    out.append(StockPlaces.lotsAt(d, d.products.mapTo(HashSet()) { it.id }).toSortedMap()).append('\n')
    out.append(json.encodeToString(d))
    return out.toString()
  }

  private fun sha(s: String): String = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

  @Test fun `تنظیمِ خاموش ⇒ بسته‌بندی و ساختِ بسته و دفتر و محموله‌ها و همگام‌سازی همان پایه`() {
    assertEquals(GOLDEN, sha(scenario()))
  }

  private companion object {
    const val GOLDEN = "82cce9202be3e2c996a3961e81f1bd0bda28cb85647701e78e4065577a421ede"
  }
}
