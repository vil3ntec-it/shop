package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.stock.ShrinkReport.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  INV-17 — گزارشِ ضایعات و کسری روی دفترِ واقعیِ دکان: همان ردیف‌هایی که انبارگردانی، اصلاحِ دستی و
 *  برگشتِ ضایعات می‌سازند؛ فروش و برگشت به تأمین‌کننده بیرون؛ هر کار یک بار (حرکت، نه ردیفِ انبار).
 */
class ShrinkLossTest {

  private var seq = 0
  private fun newId(): String = "id${seq++}"

  private val base = ShopData(
    products = listOf(
      Product(id = "rice", name = "برنج", unit = "کیلوگرم", purchasePrice = 100.0),
      Product(id = "soap", name = "صابون", unit = "عدد", purchasePrice = 20.0),
      Product(id = "milk", name = "شیر", unit = "عدد", purchasePrice = 50.0),
    ),
    warehouseEntries = listOf(
      WarehouseEntry(id = "w1", productId = "rice", units = 10.0),
      WarehouseEntry(id = "w2", productId = "soap", units = 5.0),
      WarehouseEntry(id = "w3", productId = "milk", units = 20.0),
    ),
  )

  private fun ok(r: WarehouseEngine.Result): ShopData = (r as WarehouseEngine.Result.Ok).data

  private fun ledger(): ShopData {
    //  انبارگردانی: برنج ۹٫۵ (کسری ۰٫۵ کیلو = ۵۰ ؋)، صابون ۶ (اضافه ۱ = ۲۰ ؋)
    var d = ok(WarehouseEngine.stockCount(base, mapOf("rice" to 9.5, "soap" to 6.0), "قفسه ۱", "2026-10-05", 1L, ::newId))
    //  اصلاحِ دستیِ بی نوع: ۱ شیر (۵۰ ؋)
    d = ok(WarehouseEngine.adjustStock(d, "milk", 1.0, false, "موش خورد", WarehouseEngine.AdjustKind.ADJUSTMENT, "2026-10-06", 2L, ::newId))
    //  برگشت به تأمین‌کننده جزوِ زیان نیست
    d = ok(WarehouseEngine.adjustStock(d, "milk", 2.0, false, "برگشت به پخش", WarehouseEngine.AdjustKind.SUPPLIER_RETURN, "2026-10-06", 3L, ::newId))
    //  ضایعاتِ برگشتی (همان شکلِ `ReturnFlow`): ۲ شیر = ۱۰۰ ؋
    d = d.copy(
      stockMovements = d.stockMovements +
        StockMovement(id = "m-sale", productId = "milk", type = "sale", qty = -3.0, date = "2026-10-07") +
        StockMovement(id = "m-dmg", productId = "milk", type = "damage", qty = -2.0, date = "2026-10-07", notes = "ضایعاتِ برگشتی — شکسته"),
    )
    //  اصلاحِ با نوعِ «منقضی» (INV-04): ردیفِ انبار + حرکتِ همان ردیف ⇒ ۳ شیر = ۱۵۰ ؋
    d = d.copy(
      warehouseEntries = d.warehouseEntries +
        WarehouseEntry(id = "w-exp", productId = "milk", units = -3.0, isAdjustment = true, adjKind = "expired", date = "2026-10-08"),
      stockMovements = d.stockMovements +
        StockMovement(id = "m-exp", productId = "milk", type = "adjustment", qty = -3.0, date = "2026-10-08", notes = "تاریخ گذشت", refId = "w-exp"),
    )
    return d
  }

  @Test
  fun `انبارگردانی، اصلاحِ دستی، ضایعات و منقضی هر کدام یک بار و با نوعِ خودشان`() {
    val r = ShrinkLoss.report(ledger(), "2026-10-01", "2026-10-31")
    assertEquals(listOf(Kind.WASTE, Kind.EXPIRED, Kind.SHORTAGE, Kind.SURPLUS, Kind.OTHER), r.kinds.map { it.kind })
    assertEquals(10_000L + 15_000L, r.wasteMinor)
    assertEquals(5_000L - 2_000L + 5_000L, r.shrinkMinor)
    assertEquals(33_000L, r.netLossMinor)
    assertEquals(5, r.rows)
    assertEquals(0, r.unpricedRows)
    //  فروشی در بازه نیست
    assertNull(r.rateBp)
    assertEquals("milk", r.products.first().productId)
  }

  @Test
  fun `برگهٔ اکسل یک ردیف برای هر حرکت دارد و جمعِ زیانش همان زیانِ کل است`() {
    val sheet = ShrinkLoss.sheet(ledger(), "2026-10-01", "2026-10-31")
    assertEquals(listOf("تاریخ", "نام کالا", "نوع", "مقدار", "واحد", "زیان", "دلیل"), sheet.rows.first())
    val body = sheet.rows.drop(1)
    assertEquals(5, body.size)
    assertEquals(330.0, body.sumOf { it[5].toDouble() }, 1e-9)
    assertTrue(body.any { it[2] == "کسریِ شمارش" && it[3] == "-0.5" && it[5] == "50" && it[6].startsWith("انبارگردانی") })
    assertTrue(body.any { it[2] == "اضافهٔ شمارش" && it[3] == "1" && it[5] == "-20" })
    assertEquals(body.map { it[0] }.sorted(), body.map { it[0] })
  }

  @Test
  fun `بیرون از بازه هیچ ردیفی نیست`() {
    val r = ShrinkLoss.report(ledger(), "2026-11-01", "2026-11-30")
    assertTrue(r.isEmpty)
    assertEquals(1, ShrinkLoss.sheet(ledger(), "2026-11-01", "2026-11-30").rows.size)
  }

  @Test
  fun `مبلغ و مقدارِ برگه رقمِ لاتین و بی صفرِ اضافه`() {
    assertEquals("33.30", ShrinkLoss.afn(3_330))
    assertEquals("-20", ShrinkLoss.afn(-2_000))
    assertEquals("0", ShrinkLoss.afn(0))
    assertEquals("2.5", ShrinkLoss.qtyText(2_500))
    assertEquals("-0.005", ShrinkLoss.qtyText(-5))
    assertEquals("12", ShrinkLoss.qtyText(12_000))
  }
}
