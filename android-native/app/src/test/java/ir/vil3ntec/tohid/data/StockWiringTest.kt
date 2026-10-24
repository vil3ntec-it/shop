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
    for (k in listOf("landedCost", "landedCostMinor", "locationId", "userId", "adjKind", "lotId")) assertFalse(k, k in wj)

    val a = WarehouseEngine.adjustStock(base, "p1", 1.0, false, "شکست", WarehouseEngine.AdjustKind.ADJUSTMENT, today, now, ::newId)
      as WarehouseEngine.Result.Ok
    assertNull(a.data.warehouseEntries.last().adjKind)
    val mj = wire.encodeToJsonElement(StockMovement.serializer(), a.data.stockMovements.last()).jsonObject
    for (k in listOf("userId", "reason", "locationId", "toLocationId", "lotId")) assertFalse(k, k in mj)

    val p = PurchaseBook.record(base, "s1", listOf(PurchaseBook.Line("p1", 2.0, 100.0)), 0.0, today, "", today, now, ::newId)
      as PurchaseBook.Result.Ok
    assertNull(p.data.purchases.last().landedCost)
    assertNull(p.data.warehouseEntries.last().landedCost)
    assertNull(p.data.warehouseEntries.last().landedCostMinor)
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
    assertEquals(2000L, w.landedCostMinor)
    assertEquals("انبار", w.locationId)
    assertEquals("کریم", w.userId)
    assertTrue(WarehouseEngine.addEntry(d, WarehouseEngine.EntryDraft(productId = "p1", units = 1.0, price = 1.0, landedCost = -1.0), today, now, ::newId)
      is WarehouseEngine.Result.Failed)
    //  ویرایش با فرمِ خاموش (null) مقدارِ قبلی را نگه می‌دارد
    val edited = WarehouseEngine.editEntry(e.data, w.id, WarehouseEngine.EntryDraft(productId = "p1", units = 4.0, price = 100.0), today)
      as WarehouseEngine.Result.Ok
    assertEquals(20.0, edited.data.warehouseEntries.last().landedCost!!, 0.0)
    assertEquals(2000L, edited.data.warehouseEntries.last().landedCostMinor)

    //  فاکتورِ دوردیفه: ۱۰۰ هزینهٔ جانبی به نسبتِ ۱۰۰۰ و ۳۰۰۰ ⇒ ۲۵ و ۷۵؛ بدهیِ تأمین‌کننده همان جمعِ فاکتور
    val p = PurchaseBook.record(
      d, "s1", listOf(PurchaseBook.Line("p1", 10.0, 100.0), PurchaseBook.Line("p2", 100.0, 30.0)), 0.0, today, "", today, now, ::newId,
      landedTotal = 100.0, locationId = "انبار", userId = "کریم",
    ) as PurchaseBook.Result.Ok
    val mine = p.data.purchases.filter { it.invoiceNo == p.invoiceNo }
    assertEquals(listOf(25.0, 75.0), mine.map { it.landedCost })
    assertEquals(listOf(2500L, 7500L), mine.map { it.landedCostMinor })
    assertEquals(4000.0, ShopStore.supplierDebt(p.data, "s1"), 0.0)
    val entries = p.data.warehouseEntries.filter { it.purchaseId in mine.map { m -> m.id } }
    assertEquals(listOf(25.0, 75.0), entries.map { it.landedCost })
    assertEquals(listOf(2500L, 7500L), entries.map { it.landedCostMinor })
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
    //  دو ردیف با یک `refId`: خروجیِ منفی از مبدأ (با مقصد)، ورودیِ مثبت در مقصد
    val (out, inn) = ok.data.stockMovements.takeLast(2)
    assertEquals(StockLedger.TRANSFER_OUT, out.type)
    assertEquals(-4.0, out.qty, 0.0)
    assertEquals("", out.locationId)
    assertEquals("انبار", out.toLocationId)
    assertEquals("کریم", out.userId)
    assertEquals(StockLedger.TRANSFER_IN, inn.type)
    assertEquals(4.0, inn.qty, 0.0)
    assertEquals("انبار", inn.locationId)
    assertEquals(out.refId, inn.refId)
    //  سرورِ Sync v3 موجودی را جمعِ `qty` ِ حرکت‌ها می‌داند (`sumMoves`) ⇒ انتقال آن را عوض نمی‌کند
    assertEquals(
      d.stockMovements.filter { it.productId == "p1" }.sumOf { it.qty },
      ok.data.stockMovements.filter { it.productId == "p1" }.sumOf { it.qty }, 0.0,
    )
    //  CSVِ «گردشِ موجودی»: برچسبِ فارسی و علامتِ درست
    val csv = ReportCsv.of("stock", ok.data, today, today, today).rows.takeLast(2)
    assertEquals(listOf("انتقالِ خروجی", "-4"), listOf(csv[0][2], csv[0][3]))
    assertEquals(listOf("انتقالِ ورودی", "4"), listOf(csv[1][2], csv[1][3]))
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
  fun on_legacySingleRowTransferStillReads() {
    //  پیش‌نمایشِ پیشین یک ردیفِ مثبتِ `transfer` می‌نوشت — هنوز همان انتقال خوانده می‌شود
    val d = on().let {
      it.copy(stockMovements = it.stockMovements + StockMovement(
        id = "mt", productId = "p1", type = "transfer", qty = 3.0, date = today, createdAt = now, locationId = "", toLocationId = "انبار",
      ))
    }
    val pv = StockLedger.productView(StockLedger.replay(d), "p1")
    assertEquals(listOf("" to 12000L, "انبار" to 3000L), pv.locations.map { it.location to it.qtyMilli })
  }

  @Test
  fun on_zeroCostIsUnknownAndFallsBackToLastPurchasePrice() {
    //  کالایی که فقط با اصلاحِ + (بی‌بها) یا ورودِ ۰ آمده: هسته lastCost = 0 می‌گذارد؛
    //  فروش نباید بهای صفر (سودِ دروغ) بگیرد ⇒ قیمتِ خریدِ آخرِ کالا، همان رفتارِ امروز
    val p3 = Product(id = "p3", name = "صابون", unit = "عدد", purchasePrice = 40.0, salePrice = 60.0)
    val adj = on(base.copy(
      products = base.products + p3,
      warehouseEntries = base.warehouseEntries + WarehouseEntry(id = "wa", productId = "p3", units = 5.0, isAdjustment = true, date = today, createdAt = 5000),
    ))
    val chk = StockLedger.checkCart(StockLedger.replay(adj), listOf(StockLedger.Line("p3", 1000)), today).single()
    assertTrue(chk.ok)
    assertTrue(chk.unknownCost)
    val r1 = sell(adj, SalesEngine.CartLine("p3", 1.0)) as SalesEngine.Result.Ok
    assertEquals(40.0, r1.data.saleItems.single().purchasePrice, 0.0)

    val zero = on(base.copy(
      products = base.products + p3,
      warehouseEntries = base.warehouseEntries + WarehouseEntry(id = "wz", productId = "p3", units = 5.0, price = 0.0, date = today, createdAt = 5000),
    ))
    val r2 = sell(zero, SalesEngine.CartLine("p3", 2.0)) as SalesEngine.Result.Ok
    assertEquals(40.0, r2.data.saleItems.single().purchasePrice, 0.0)
    //  بهای معلوم دست نمی‌خورد
    val r3 = sell(zero, SalesEngine.CartLine("p1", 3.0)) as SalesEngine.Result.Ok
    assertEquals(113.33, r3.data.saleItems.single().purchasePrice, 0.0)
  }

  @Test
  fun on_damagedReturnUserIsTheReturnsBy() {
    //  برگشتِ ضایعاتِ RET (#318): ردیفِ انبار با adjKind = damage و refId = docId ِ سند
    val d = on(base.copy(
      saleReturns = listOf(SaleReturn(id = "r1", saleId = "s0", productId = "p1", quantity = 1.0, docId = "D1", by = "کریم", createdAt = 6000)),
      warehouseEntries = base.warehouseEntries + WarehouseEntry(
        id = "wd", productId = "p1", units = -1.0, isAdjustment = true, notes = "ضایعاتِ برگشتی R-1 — شکسته", date = today, createdAt = 6000,
        adjKind = "damage", refId = "D1",
      ),
    ))
    val row = StockLedger.replay(d).rows.first { it.id == "wd" }
    assertEquals("damage", row.type)
    assertEquals("کریم", row.userId)
    //  همان، وقتی فقط برای همین کالا بازپخش می‌شود (سنجشِ سبد)
    assertEquals("کریم", StockLedger.replay(d, listOf("p1")).rows.first { it.id == "wd" }.userId)
  }

  @Test
  fun on_archiveKeepsModeAcksAndTransfers() {
    var d = on()
    d = (StockActions.transfer(d, StockLedger.replay(d), "p1", "", "انبار", 4.0, null, "", "کریم", today, now, ::newId) as StockActions.Result.Ok).data
    d = d.copy(
      sales = d.sales + Sale(id = "sx", status = "completed", date = today, createdAt = now + 1000),
      saleItems = d.saleItems + SaleItem(id = "ix", saleId = "sx", productId = "p1", quantity = 20.0),
    )
    d = StockActions.ack(d, StockLedger.replay(d), listOf("ix"), "کریم", today, now + 2000, ::newId)
    //  همهٔ ردیف‌ها کهنه‌تر از ۴۰۰ روز، به‌علاوهٔ یک حرکتِ عادی که باید برود
    d = d.copy(
      stockMovements = d.stockMovements + StockMovement(id = "plain", productId = "p1", type = "sale", qty = -1.0, createdAt = 1000),
      auditLog = d.auditLog + AuditEntry(id = "a-plain", type = "sale", createdAt = 1000),
    )
    val later = now + 500L * 24 * 60 * 60 * 1000
    val before = StockLedger.replay(d)
    val (kept, gone) = LedgerArchive.split(d, later)
    assertEquals(listOf("plain"), gone.stockMovements.map { it.id })
    assertTrue(gone.auditLog.any { it.id == "a-plain" })
    assertTrue(gone.auditLog.none { LedgerArchive.pinned(it) })
    assertEquals(2, kept.auditLog.count { LedgerArchive.pinned(it) })
    assertEquals(LedgerArchive.Plan(1, 0, gone.auditLog.size), LedgerArchive.plan(d, later))
    assertTrue(StockLedger.isOn(kept))
    val after = StockLedger.replay(kept)
    assertTrue(after.tasks.single().acked)
    assertEquals(
      StockLedger.productView(before, "p1").locations,
      StockLedger.productView(after, "p1").locations,
    )
  }

  @Test
  fun on_saleCheckOnBigLedgerStaysFast() {
    //  دفترِ بزرگ: ۴۰۰ کالا، ۸۰۰۰۰ قلمِ فروش؛ سنجشِ سبدِ یک‌قلمی فقط همان کالا را می‌دواند
    val products = (0 until 400).map { Product(id = "b$it", name = "کالا $it", unit = "عدد", purchasePrice = 10.0, salePrice = 20.0) }
    val entries = products.mapIndexed { i, p -> WarehouseEntry(id = "be$i", productId = p.id, units = 100_000.0, price = 10.0, date = "2026-01-01", createdAt = 100L + i) }
    val sales = ArrayList<Sale>()
    val items = ArrayList<SaleItem>()
    for (k in 0 until 80_000) {
      if (k % 4 == 0) sales += Sale(id = "bs${k / 4}", status = "completed", date = "2026-06-01", createdAt = 10_000L + k)
      items += SaleItem(id = "bi$k", saleId = "bs${k / 4}", productId = "b${k % 400}", quantity = 1.0)
    }
    val d = on(base.copy(products = base.products + products, warehouseEntries = base.warehouseEntries + entries, sales = sales, saleItems = items))
    assertTrue(StockLedger.events(d, setOf("b7")).all { it.productId == "b7" })
    repeat(2) { sell(d, SalesEngine.CartLine("b7", 1.0)) }
    val t0 = System.nanoTime()
    repeat(5) { assertTrue(sell(d, SalesEngine.CartLine("b7", 1.0)) is SalesEngine.Result.Ok) }
    val ms = (System.nanoTime() - t0) / 1_000_000 / 5
    assertTrue("ثبتِ یک فروش روی دفترِ بزرگ $ms ms طول کشید", ms < 1500)
  }

  @Test
  fun on_singlePurchaseCarriesLandedOnPurchaseRow() {
    val r = LedgerEngine.addPurchase(
      on(), LedgerEngine.PurchaseDraft(supplierId = "s1", productId = "p1", quantity = 2.0, purchasePrice = 100.0, date = today, landedCost = 30.0),
      today, now, ::newId,
    ) as LedgerEngine.Result.Ok
    val p = r.data.purchases.last()
    assertEquals(30.0, p.landedCost!!, 0.0)
    assertEquals(3000L, p.landedCostMinor)
    assertEquals(3000L, r.data.warehouseEntries.last().landedCostMinor)
    //  خاموش ⇒ ردیفِ خرید فیلدِ تازه ندارد
    val off = LedgerEngine.addPurchase(
      base, LedgerEngine.PurchaseDraft(supplierId = "s1", productId = "p1", quantity = 2.0, purchasePrice = 100.0, date = today),
      today, now, ::newId,
    ) as LedgerEngine.Result.Ok
    assertNull(off.data.purchases.last().landedCost)
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
