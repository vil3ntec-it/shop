package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.stock.StockTransfer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  INV-11 — گامِ انتقال در دفترِ دکان: یک ردیفِ `auditLog` ِ `stock_transfer` با `xfer` (همان شکلی که سرور می‌خواند)،
 *  یک بار برای هر کلید، و **هیچ** تغییری در موجودیِ دفترِ دستگاه (جابه‌جایی فقط در سرور ⇒ هیچ واحدی دو بار شمرده نمی‌شود).
 */
class StockTransferBookTest {

  private val today = "2026-11-07"
  private val base = ShopData(
    products = listOf(Product(id = "p1", name = "چای", unit = "عدد"), Product(id = "p2", name = "برنج", unit = "کیلوگرم")),
    warehouseEntries = listOf(WarehouseEntry(id = "w1", productId = "p1", units = 10.0), WarehouseEntry(id = "w2", productId = "p2", units = 4.5)),
    auditLog = listOf(AuditEntry(id = "a0", type = "sale", notes = "فروش")),
  )

  private fun send(key: String, q: Long = 3000) = (StockTransfer.send(
    StockTransfer.Draft(key, lines = listOf(StockTransfer.Line("p1", q), StockTransfer.Line("p2", 1500))), at = 100,
  ) as StockTransfer.Result.Ok).step

  @Test fun stepIsOneAuditRowWithTheServerShapeAndOnlyOnce() {
    val s = send("k-send-0001")
    val d = StockTransferBook.append(base, s, "علی", today)!!
    val row = d.auditLog.last()
    assertEquals("k-send-0001", row.id)
    assertEquals(StockTransfer.AUDIT_TYPE, row.type)
    assertEquals("علی", row.by)
    assertEquals(s.xfer(), row.xfer)
    assertTrue(row.notes, row.notes.startsWith("انتقالِ موجودی: قفسهٔ فروشگاه ⇐ انبار — چای 3، برنج 1.5"))
    //  دو ضربه یا تکرار پس از قطع ⇒ همان یک ردیف
    assertNull(StockTransferBook.append(d, s, "علی", today))
    assertNull(StockTransferBook.append(d, send("k-send-0001", 9000), "علی", today))
    assertEquals(listOf(s), StockTransferBook.steps(d))
  }

  @Test fun deviceStockIsUntouchedSoNothingIsCountedTwice() {
    val s = send("k-send-0002")
    val r = (StockTransfer.receive(
      StockTransfer.Transfer(null, s.key, s.from, s.to, StockTransfer.Status.IN_TRANSIT,
        s.lines.map { StockTransfer.TLine(it.productId, it.qtyMilli, 0, it.qtyMilli, 0) }),
      "k-recv-0002", mapOf("p1" to 2000L), final = true, reason = "یکی شکست", at = 101,
    ) as StockTransfer.Result.Ok).step
    var d = StockTransferBook.append(base, s, null, today)!!
    d = StockTransferBook.append(d, r, null, today)!!
    assertEquals(ShopStore.stock(base, "p1"), ShopStore.stock(d, "p1"), 0.0)
    assertEquals(ShopStore.stock(base, "p2"), ShopStore.stock(d, "p2"), 0.0)
    assertEquals(base.warehouseEntries, d.warehouseEntries)
    assertEquals(base.stockMovements, d.stockMovements)
    assertEquals(listOf("k-send-0002", "k-recv-0002"), StockTransferBook.steps(d).map { it.key })
    assertTrue(d.auditLog.last().notes.contains("یکی شکست"))
  }

  @Test fun otherAuditRowsDoNotGrowANewField() {
    //  `xfer` ِ تهی نوشته نمی‌شود ⇒ ردیف‌های پیشین op ِ تازه نمی‌سازند
    val plain = Json.encodeToString(AuditEntry.serializer(), AuditEntry(id = "a1", type = "sale"))
    assertFalse(plain, plain.contains("xfer"))
    val withStep = Json.encodeToString(AuditEntry.serializer(), StockTransferBook.row(send("k-send-0003"), null, today) { it })
    val back = Json.parseToJsonElement(withStep).jsonObject
    assertEquals(send("k-send-0003").xfer(), back["xfer"])
    assertFalse(StockTransferBook.has(base, "k-send-0003"))
  }
}
