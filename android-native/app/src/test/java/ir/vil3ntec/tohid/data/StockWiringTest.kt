package ir.vil3ntec.tohid.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  سیم‌کشیِ موتورِ تازهٔ موجودی و بها به فروش، انبار، خرید، شمارش و انتقال.
 *
 *  ── خاموش (پیش‌فرض) = رفتارِ امروز — آزمونِ «همان که بود» ──────────
 *  دفترِ بی ردیفِ `stock_mode`: قیمتِ خریدِ قلمِ فروش همان «قیمتِ خریدِ آخرِ
 *  کالا»، سریِ منقضی با سیاستِ پیش‌فرض فروخته می‌شود، و هیچ فیلدِ تازه‌ای روی
 *  ردیف‌ها نوشته نمی‌شود (پس Sync v1 هیچ opِ اضافه‌ای نمی‌سازد).
 *
 *  ── روشن ──────────────────────────────────────────────────────────
 *  بهای موقت = میانگینِ موزون با هزینهٔ جانبی؛ سریِ منقضی و بیش از سالم بسته؛
 *  سریِ اسکن‌شده روی قلم؛ شمارش نسبت به لحظهٔ شروع؛ انتقال جمعِ صفر.
 */
class StockWiringTest {
  private var n = 0
  private fun newId() = "id${++n}"
  private val today = "2026-10-15"
  private val now = 1_790_000_000_000L

  private val base = ShopData(
    products = listOf(
      Product(id = "p1", name = "روغن", unit = "عدد", purchasePrice = 130.0, salePrice = 200.0),
      Product(id = "p2", name = "شیر", unit = "عدد", purchasePrice = 10.0, salePrice = 30.0),
    ),
    warehouseEntries = listOf(
      WarehouseEntry(id = "w1", productId = "p1", units = 10.0, price = 100.0, date = "2026-10-01", createdAt = 1000),
      WarehouseEntry(id = "w2", productId = "p1", units = 5.0, price = 130.0, landedCost = 50.0, date = "2026-10-01", createdAt = 2000),
      WarehouseEntry(id = "w3", productId = "p2", units = 4.0, price = 10.0, batch = "OLD", expiryDate = "2026-01-01", date = "2025-12-01", createdAt = 3000),
      WarehouseEntry(id = "w4", productId = "p2", units = 6.0, price = 10.0, batch = "NEW1", expiryDate = "2099-01-01", date = "2026-10-01", createdAt = 4000),
    ),
    suppliers = listOf(Supplier(id = "s1", name = "شرکت")),
  )
  private fun on(d: ShopData = base) = StockActions.setMode(d, true, "مالک", today, 10, ::newId)

  private fun sell(d: ShopData, vararg lines: SalesEngine.CartLine): SalesEngine.Result =
    SalesEngine.record(d, lines.toList(), SalesEngine.Checkout(), today, now, ::newId)

  private val wire = Json { encodeDefaults = true }

  /* ============================== خاموش ============================== */

  @Test
  fun off_saleKeepsLastPurchasePriceAndNoNewFields() {
    assertFalse(StockLedger.isOn(base))
    val r = sell(base, SalesEngine.CartLine("p1", 3.0)) as SalesEngine.Result.Ok
    val item = r.data.saleItems.single()
    assertEquals(130.0, item.purchasePrice, 0.0)
    assertNull(item.lotId)
    val json = wire.encodeToJsonElement(SaleItem.serializer(), item).jsonObject
    assertFalse("lotId" in json)
    assertFalse("locationId" in json)
  }

  @Test
  fun off_expiredLotSellsLikeToday() {
    //  سیاستِ پیش‌فرضِ «فقط هشدار»: ۸ تا از ۱۰ (۴ منقضی) فروخته می‌شود
    assertTrue(sell(base, SalesEngine.CartLine("p2", 8.0)) is SalesEngine.Result.Ok)
  }

  @Test
  fun off_entryAdjustPurchaseWriteNoNewFields() {
    val e = WarehouseEngine.addEntry(base, WarehouseEngine.EntryDraft(productId = "p1", units = 2.0, price = 100.0), today, now, ::newId)
      as WarehouseEngine.Result.Ok
    val w = e.data.warehouseEntries.last()
    val wj = wire.encodeToJsonElement(WarehouseEntry.serializer(), w).jsonObject
    for (k in listOf("landedCost", "locationId", "userId", "adjKind", "lotId")) assertFalse(k, k in wj)

    val a = WarehouseEngine.adjustStock(base, "p1", 1.0, false, "شکست", WarehouseEngine.AdjustKind.ADJUSTMENT, today, now, ::newId)
      as WarehouseEngine.Result.Ok
    assertNull(a.data.warehouseEntries.last().adjKind)
    val mj = wire.encodeToJsonElement(StockMovement.serializer(), a.data.stockMovements.last()).jsonObject
    for (k in listOf("userId", "reason", "locationId", "toLocationId", "lotId")) assertFalse(k, k in mj)

    val p = PurchaseBook.record(base, "s1", listOf(PurchaseBook.Line("p1", 2.0, 100.0)), 0.0, today, "", today, now, ::newId)
      as PurchaseBook.Result.Ok
    assertNull(p.data.purchases.last().landedCost)
    assertNull(p.data.warehouseEntries.last().landedCost)
  }

  @Test
  fun off_oldRowsEncodeExactlyAsBefore() {
    //  ردیفِ قدیمی (بی فیلدِ تازه) همان JSONِ پیش از این تغییر را می‌دهد ⇒ تفاضلِ Sync v1 خالی
    val w = base.warehouseEntries[0]
    val keys = wire.encodeToJsonElement(WarehouseEntry.serializer(), w).jsonObject.keys
    assertEquals(
      setOf("id", "productId", "cartons", "perCarton", "units", "unit", "price", "date", "notes", "isAdjustment",
        "purchaseId", "createdAt", "expiryDate", "batch", "productionDate"),
      keys,
    )
  }

  /* ============================== روشن ============================== */

  @Test
  fun on_settingIsTheLatestAuditRow() {
    val d = on()
    assertTrue(StockLedger.isOn(d))
    val row = d.auditLog.last()
    assertEquals("stock_mode", row.type)
    assertEquals("on", row.refId)
    assertFalse(StockLedger.isOn(StockActions.setMode(d, false, "", today, 11, ::newId)))
  }

  @Test
  fun on_saleCostIsMovingAverageWithLanded() {
    //  (10×100 + 5×130 + 50) / 15 = 113.33
    val r = sell(on(), SalesEngine.CartLine("p1", 3.0)) as SalesEngine.Result.Ok
    assertEquals(113.33, r.data.saleItems.single().purchasePrice, 0.0)
  }

  @Test
  fun on_expiredAndShortageAreRefusedWithClearMessages() {
    val d = on()
    val exp = sell(d, SalesEngine.CartLine("p2", 8.0)) as SalesEngine.Result.Failed
    assertTrue(exp.message, exp.message.contains("منقضی"))
    assertTrue(exp.message, exp.message.contains("6"))
    val short = sell(d, SalesEngine.CartLine("p1", 16.0)) as SalesEngine.Result.Failed
    assertTrue(short.message, short.message.contains("روغن"))
    //  دو ردیفِ یک کالا روی هم سنجیده می‌شوند
    assertTrue(sell(d, SalesEngine.CartLine("p2", 6.0)) is SalesEngine.Result.Ok)
  }

  @Test
  fun on_scannedLotIsStampedAndFound() {
    val d = on()
    assertEquals("p2" to "NEW1", StockActions.lotForCode(d, "NEW1"))
    assertNull("سریِ منقضی هم پیدا می‌شود ولی موجودی دارد؟", StockActions.lotForCode(d, "nope"))
    assertNull("خاموش", StockActions.lotForCode(base, "NEW1"))
    val cart = SalesEngine.setLineLot(SalesEngine.addToCart(emptyList(), "p2", 2.0), "p2", "NEW1")
    val r = SalesEngine.record(d, cart, SalesEngine.Checkout(), today, now, ::newId) as SalesEngine.Result.Ok
    val item = r.data.saleItems.single()
    assertEquals("NEW1", item.lotId)
    val row = StockLedger.replay(r.data).rows.first { it.id == item.id }
    assertEquals(listOf("NEW1"), row.picks.map { it.lotId })
  }

  @Test
  fun on_entryAndPurchaseCarryLandedLocationUser() {
    val d = on()
    val e = WarehouseEngine.addEntry(
      d, WarehouseEngine.EntryDraft(productId = "p1", units = 4.0, price = 100.0, landedCost = 20.0, locationId = "انبار", userId = "کریم"),
      today, now, ::newId,
    ) as WarehouseEngine.Result.Ok
    val w = e.data.warehouseEntries.last()
    assertEquals(20.0, w.landedCost!!, 0.0)
    assertEquals("انبار", w.locationId)
    assertEquals("کریم", w.userId)
    assertTrue(WarehouseEngine.addEntry(d, WarehouseEngine.EntryDraft(productId = "p1", units = 1.0, price = 1.0, landedCost = -1.0), today, now, ::newId)
      is WarehouseEngine.Result.Failed)
    //  ویرایش با فرمِ خاموش (null) مقدارِ قبلی را نگه می‌دارد
    val edited = WarehouseEngine.editEntry(e.data, w.id, WarehouseEngine.EntryDraft(productId = "p1", units = 4.0, price = 100.0), today)
      as WarehouseEngine.Result.Ok
    assertEquals(20.0, edited.data.warehouseEntries.last().landedCost!!, 0.0)

    //  فاکتورِ دوردیفه: ۱۰۰ هزینهٔ جانبی به نسبتِ ۱۰۰۰ و ۳۰۰۰ ⇒ ۲۵ و ۷۵؛ بدهیِ تأمین‌کننده همان جمعِ فاکتور
    val p = PurchaseBook.record(
      d, "s1", listOf(PurchaseBook.Line("p1", 10.0, 100.0), PurchaseBook.Line("p2", 100.0, 30.0)), 0.0, today, "", today, now, ::newId,
      landedTotal = 100.0, locationId = "انبار", userId = "کریم",
    ) as PurchaseBook.Result.Ok
    val mine = p.data.purchases.filter { it.invoiceNo == p.invoiceNo }
    assertEquals(listOf(25.0, 75.0), mine.map { it.landedCost })
    assertEquals(4000.0, ShopStore.supplierDebt(p.data, "s1"), 0.0)
    val entries = p.data.warehouseEntries.filter { it.purchaseId in mine.map { m -> m.id } }
    assertEquals(listOf(25.0, 75.0), entries.map { it.landedCost })
    assertTrue(entries.all { it.locationId == "انبار" && it.userId == "کریم" })
    val pv = StockLedger.productView(StockLedger.replay(p.data), "p1")
    //  (1000 + 650 + 50 + 1000 + 25) / 25 = 109 افغانی
    assertEquals(10900L, pv.unitCostMinor)
  }

  @Test
  fun on_adjustCarriesKindAndUser() {
    val d = on()
    val a = WarehouseEngine.adjustStock(d, "p1", 2.0, false, "شکست", WarehouseEngine.AdjustKind.ADJUSTMENT, today, now, ::newId,
      adjKind = "damage", userId = "کریم") as WarehouseEngine.Result.Ok
    val w = a.data.warehouseEntries.last()
    assertEquals("damage", w.adjKind)
    assertEquals("کریم", w.userId)
    assertEquals("شکست", a.data.stockMovements.last().reason)
    val row = StockLedger.replay(a.data).rows.first { it.id == w.id }
    assertEquals("damage", row.type)
    assertEquals("کریم", row.userId)
    //  افزایشِ «خراب» معنی ندارد ⇒ اصلاحِ شمارش
    val up = WarehouseEngine.adjustStock(d, "p1", 1.0, true, "پیدا شد", WarehouseEngine.AdjustKind.ADJUSTMENT, today, now, ::newId,
      adjKind = "damage") as WarehouseEngine.Result.Ok
    assertEquals("count", up.data.warehouseEntries.last().adjKind)
    val back = WarehouseEngine.adjustStock(d, "p1", 1.0, false, "پس", WarehouseEngine.AdjustKind.SUPPLIER_RETURN, today, now, ::newId,
      adjKind = "other") as WarehouseEngine.Result.Ok
    assertEquals("supplier_return", back.data.warehouseEntries.last().adjKind)
  }

  @Test
  fun on_transferIsZeroSumAndChecked() {
    val d = on()
    val view = StockLedger.replay(d)
    val bad = StockActions.transfer(d, view, "p1", "", "انبار", 99.0, null, "", "کریم", today, now, ::newId)
    assertTrue(bad is StockActions.Result.Failed)
    assertTrue(StockActions.transfer(d, view, "p1", "", "", 1.0, null, "", "", today, now, ::newId) is StockActions.Result.Failed)
    val ok = StockActions.transfer(d, view, "p1", "", "انبار", 4.0, null, "پشتِ دکان", "کریم", today, now, ::newId) as StockActions.Result.Ok
    val mv = ok.data.stockMovements.last()
    assertEquals("transfer", mv.type)
    assertEquals("", mv.locationId)
    assertEquals("انبار", mv.toLocationId)
    assertEquals("کریم", mv.userId)
    assertEquals(ShopStore.stock(d, "p1"), ShopStore.stock(ok.data, "p1"), 0.0)
    val pv = StockLedger.productView(StockLedger.replay(ok.data), "p1")
    assertEquals(listOf("" to 11000L, "انبار" to 4000L), pv.locations.map { it.location to it.qtyMilli })
    assertEquals(listOf("", "انبار"), StockLedger.locations(ok.data))
  }

  @Test
  fun on_countIsRelativeToStartAndLeavesUncountedAlone() {
    var d = on()
    val draft0 = StockActions.startCount(d, StockLedger.replay(d), now)
    //  وسطِ شمارش ۲ روغن فروخته شد؛ شمارنده قفسه را پیش از فروش شمرده بود: ۱۴ (یکی خراب)
    d = (sell(d, SalesEngine.CartLine("p1", 2.0)) as SalesEngine.Result.Ok).data
    val draft = draft0.copy(counts = mapOf("p1" to "۱۴", "p2" to "abc"), reasons = mapOf("p1" to "damage"), note = "قفسهٔ ۲")
    assertEquals(listOf("p2"), StockActions.invalidCounts(draft))
    assertTrue(StockActions.saveCount(d, StockLedger.replay(d), draft, "کریم", today, now, ::newId) is StockActions.Result.Failed)
    val good = draft.copy(counts = mapOf("p1" to "۱۴"))
    val review = StockActions.countReview(d, StockLedger.replay(d), good)
    assertEquals(1, review.variance)
    assertEquals(1, review.uncounted)
    val r = StockActions.saveCount(d, StockLedger.replay(d), good, "کریم", today, now, ::newId) as StockActions.Result.Ok
    val adj = r.data.warehouseEntries.last()
    assertEquals(-1.0, adj.units, 0.0)
    assertEquals("damage", adj.adjKind)
    assertEquals("انبارگردانی — خراب — قفسهٔ ۲", adj.notes)
    assertEquals(12.0, ShopStore.stock(r.data, "p1"), 0.0)
    assertEquals(ShopStore.stock(d, "p2"), ShopStore.stock(r.data, "p2"), 0.0)
  }

  @Test
  fun on_reviewTaskAndAck() {
    var d = on()
    //  دو دستگاهِ آفلاین آخرین واحد را فروختند: فروشِ ثبت‌شده رد نمی‌شود
    d = d.copy(
      sales = d.sales + Sale(id = "sx", status = "completed", date = today, createdAt = 9000),
      saleItems = d.saleItems + SaleItem(id = "ix", saleId = "sx", productId = "p1", quantity = 20.0),
    )
    val view = StockLedger.replay(d)
    val task = view.tasks.single()
    assertEquals("ix", task.id)
    assertEquals(listOf("negative_stock"), task.reasons)
    val acked = StockActions.ack(d, view, listOf("ix"), "کریم", today, now, ::newId)
    assertTrue(StockLedger.replay(acked).tasks.single().acked)
    assertEquals(acked, StockActions.ack(acked, StockLedger.replay(acked), listOf("ix"), "", today, now, ::newId))
  }

  @Test
  fun draftLineKeepsLot() {
    val cart = listOf(SalesEngine.CartLine("p2", 1.0, lotId = "NEW1"))
    assertEquals(cart, SaleDraft.of("k", cart, null, 1).cart())
    val old = Json { ignoreUnknownKeys = true }.decodeFromString(SaleDraft.serializer(), """{"v":2,"lines":[{"productId":"p2","quantity":1}]}""")
    assertNull(old.cart().single().lotId)
    assertNotNull(JsonObject(emptyMap()))
  }
}
