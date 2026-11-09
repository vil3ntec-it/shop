package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.product.PurchaseUnits
import ir.vil3ntec.tohid.core.report.ReportCore
import ir.vil3ntec.tohid.core.report.ReportLedger
import ir.vil3ntec.tohid.sync.v1.LedgerDiff
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  PRD-07 — واحدهای خریدِ چندگانه در دفتر: چای با واحدِ پایهٔ «دانه»، کارتنِ ۱۲ از الف و کارتنِ ۲۴ از ب. فرمِ کالا
 *  (موتور)، فاکتورِ خرید و سفارشِ خرید به واحدِ خرید (موجودی = مقدار × ضریب، جمع دقیق)، میانگینِ موزون با واحدهای
 *  قاطی، برگشت به تأمین‌کننده به واحدِ خرید، اسکنِ بارکدِ واحد، همگام‌سازی و ادغامِ فیلدِ تازه، و کالای بی واحدِ خرید
 *  همان رفتار و همان JSON ِ پیشین.
 */
class PurchaseUnitsLedgerTest {

  private val twelve = PurchaseUnit("کارتن ۱۲", 12_000, "supA", "444")
  private val twentyFour = PurchaseUnit("کارتن ۲۴", 24_000, "supB", "555")

  private val d0 = ShopData(
    products = listOf(
      Product(id = "tea", name = "چای", unit = "دانه", purchasePrice = 40.0, salePrice = 50.0, barcodes = listOf("111"), purchaseUnits = listOf(twelve, twentyFour)),
      Product(id = "rice", name = "برنج", unit = "دانه", purchasePrice = 10.0, salePrice = 12.0, barcodes = listOf("222")),
    ),
    suppliers = listOf(Supplier(id = "supA", name = "الف"), Supplier(id = "supB", name = "ب")),
  )

  private var seq = 0
  private fun id(): String = "id${++seq}"

  private fun ok(r: PurchaseBook.Result): ShopData = (r as? PurchaseBook.Result.Ok)?.data ?: error("رد شد: $r")
  private fun okW(r: WarehouseEngine.Result): ShopData = (r as? WarehouseEngine.Result.Ok)?.data ?: error("رد شد: $r")
  private fun why(r: WarehouseEngine.Result): String = (r as? WarehouseEngine.Result.Failed)?.message ?: error("پذیرفته شد")

  private fun buy(d: ShopData, supplier: String, vararg lines: PurchaseBook.Line, now: Long = 1) =
    ok(PurchaseBook.record(d, supplier, lines.toList(), 0.0, "2026-11-01", "", "2026-11-01", now, ::id))

  @Test fun `یک کارتنِ ۲۴ به ۱۰۰۰ — موجودی ۲۴ دانه، جمع و بدهی دقیقاً ۱۰۰۰ و بهای هر دانه ۴۱٫۶۷`() {
    val d = buy(d0, "supB", PurchaseBook.Line("tea", 1.0, 1000.0, unitName = "کارتن ۲۴"))
    assertEquals(24.0, ShopStore.stock(d, "tea"), 1e-9)
    val p = d.purchases.single()
    assertEquals(24.0, p.quantity, 1e-9)
    assertEquals(41.67, p.purchasePrice, 1e-9)
    assertEquals(1000.0, p.totalAmount, 0.0)
    assertEquals(1000.0, ShopStore.supplierDebt(d, "supB"), 0.0)
    assertEquals("کارتن ۲۴", p.unitName)
    assertEquals(1_000L, p.unitQtyMilli)
    assertEquals(24_000L, p.unitFactorMilli)
    val w = d.warehouseEntries.single()
    assertEquals(24.0, w.units, 1e-9)
    assertEquals(100_000L, w.costMinor)
    //  ۲۴ × ۴۱٫۶۷ = ۱۰۰۰٫۰۸ ⇒ باقیِ −۸ صدم در ارزشِ همان ردیف می‌ماند، نه در بهای واحد
    assertEquals(PurchaseUnits.BaseCost(24_000, 4_167, 100_000, -8), PurchaseUnits.baseCost(1_000, 24_000, 100_000))
    assertEquals(41.67, d.products.first { it.id == "tea" }.purchasePrice, 1e-9)
    //  ده کارتن: جمعِ مقدار × بهای گردشده ۱۰٬۰۰۰٫۸ می‌شد؛ جمعِ دفتر ۱۰٬۰۰۰ می‌ماند
    val ten = buy(d0, "supB", PurchaseBook.Line("tea", 10.0, 1000.0, unitName = "کارتن ۲۴"))
    assertEquals(10_000.0, ten.purchases.single().totalAmount, 0.0)
    assertEquals(1_000_000L, ten.warehouseEntries.single().costMinor)
    assertEquals(240.0, ShopStore.stock(ten, "tea"), 1e-9)
  }

  @Test fun `دو تأمین‌کننده با دو کارتن و یک ردیفِ واحدِ پایه در یک فاکتور`() {
    val a = buy(d0, "supA", PurchaseBook.Line("tea", 3.0, 480.0, unitName = "کارتن ۱۲"), PurchaseBook.Line("rice", 5.0, 10.0))
    assertEquals(36.0, ShopStore.stock(a, "tea"), 1e-9)
    assertEquals(5.0, ShopStore.stock(a, "rice"), 1e-9)
    assertEquals(1440.0 + 50.0, ShopStore.supplierDebt(a, "supA"), 0.0)
    assertNull("ردیفِ واحدِ پایه همان پیشین", a.purchases.first { it.productId == "rice" }.unitName)
    assertNull(a.warehouseEntries.first { it.productId == "rice" }.costMinor)
    val b = buy(a, "supB", PurchaseBook.Line("tea", 2.0, 1000.0, unitName = "کارتن ۲۴"), now = 2)
    assertEquals(36.0 + 48.0, ShopStore.stock(b, "tea"), 1e-9)
  }

  @Test fun `واحدِ ناشناخته و مقدارِ پایهٔ اعشاری برای کالای شمارشی رد می‌شود`() {
    val unknown = PurchaseBook.record(d0, "supA", listOf(PurchaseBook.Line("tea", 1.0, 10.0, unitName = "بوری")), 0.0, "2026-11-01", "", "2026-11-01", 1, ::id)
    assertTrue(unknown is PurchaseBook.Result.Failed)
    val half = PurchaseBook.record(d0, "supA", listOf(PurchaseBook.Line("tea", 0.25, 10.0, unitName = "کارتن ۲۴")), 0.0, "2026-11-01", "", "2026-11-01", 1, ::id)
    assertEquals(6.0, ShopStore.stock(ok(half), "tea"), 1e-9)
    val frac = PurchaseBook.record(d0, "supA", listOf(PurchaseBook.Line("tea", 0.1, 10.0, unitName = "کارتن ۱۲")), 0.0, "2026-11-01", "", "2026-11-01", 1, ::id)
    assertTrue("۰٫۱ × ۱۲ = ۱٫۲ دانه", frac is PurchaseBook.Result.Failed)
    assertTrue("هیچ اثری نماند", d0.purchases.isEmpty())
  }

  @Test fun `میانگینِ موزون با واحدهای قاطی — ۱۰ دانه به ۴۰ و یک کارتنِ ۲۴ به ۱۰۰۰ ⇒ ارزش دقیقاً ۱۴۰۰`() {
    val a = buy(d0, "supA", PurchaseBook.Line("tea", 10.0, 40.0), now = 1)
    val b = buy(a, "supB", PurchaseBook.Line("tea", 1.0, 1000.0, unitName = "کارتن ۲۴"), now = 2)
    //  موتورِ موجودی و بها (`StockLedger` ⇐ `StockCore`)
    val pv = StockLedger.productView(StockLedger.replay(b), "tea")
    assertEquals(34_000L, pv.qtyMilli)
    assertEquals(140_000L, pv.valueMinor)
    assertEquals(PurchaseUnits.roundDiv(140_000L * 1000, 34_000), pv.unitCostMinor)
    //  بهای تمام‌شدهٔ گزارش (`ReportCore` MAVG): فروشِ همهٔ ۳۴ دانه دقیقاً ۱۴۰۰ هزینه دارد، نه ۱۴۰۰٫۰۸
    val sold = b.copy(
      sales = listOf(Sale(id = "s1", total = 1700.0, finalTotal = 1700.0, date = "2026-11-02", createdAt = 3)),
      saleItems = listOf(SaleItem(id = "i1", saleId = "s1", productId = "tea", quantity = 34.0, unitPrice = 50.0, purchasePrice = 41.67, totalPrice = 1700.0)),
    )
    val raw = Json.encodeToJsonElement(ShopData.serializer(), sold).jsonObject
    val costs = ReportCore.costs(ReportLedger.parse(raw), ReportCore.Costing.MAVG)
    assertEquals(140_000L, costs.line["i1"])
    //  همان دفتر بی `costMinor` (رفتارِ پیشین) ۱۴۰۰٫۰۸ می‌داد
    val old = sold.copy(warehouseEntries = sold.warehouseEntries.map { it.copy(costMinor = null) })
    assertEquals(140_008L, ReportCore.costs(ReportLedger.parse(Json.encodeToJsonElement(ShopData.serializer(), old).jsonObject), ReportCore.Costing.MAVG).line["i1"])
  }

  @Test fun `بهای ورود عوض شد — ارزش با بهای تازه، نه costMinor ِ کهنه`() {
    val d = buy(d0, "supB", PurchaseBook.Line("tea", 1.0, 1000.0, unitName = "کارتن ۲۴"))
    val w = d.warehouseEntries.single()
    fun value(x: ShopData) = StockLedger.productView(StockLedger.replay(x), "tea").valueMinor
    fun cogs(x: ShopData): Long? {
      val sold = x.copy(
        sales = listOf(Sale(id = "s1", total = 1500.0, finalTotal = 1500.0, date = "2026-11-02", createdAt = 3)),
        saleItems = listOf(SaleItem(id = "i1", saleId = "s1", productId = "tea", quantity = 24.0, unitPrice = 62.5, totalPrice = 1500.0)),
      )
      return ReportCore.costs(ReportLedger.parse(Json.encodeToJsonElement(ShopData.serializer(), sold).jsonObject), ReportCore.Costing.MAVG).line["i1"]
    }
    assertEquals(100_000L, value(d))
    assertEquals(100_000L, cogs(d))
    val draft = WarehouseEngine.EntryDraft(productId = "tea", units = w.units, unit = w.unit, price = w.price, date = w.date)
    //  این نسخه: بها همان ⇒ ارزشِ دقیق می‌ماند؛ بهای تازه ⇒ برداشته (پیش‌تر می‌ماند و ارزش همان ۱۰۰۰ ِ کهنه بود)
    assertEquals(100_000L, okW(WarehouseEngine.editEntry(d, w.id, draft.copy(notes = "یادداشت"), "2026-11-02")).warehouseEntries.single().costMinor)
    val edited = okW(WarehouseEngine.editEntry(d, w.id, draft.copy(price = 50.0), "2026-11-02"))
    assertNull(edited.warehouseEntries.single().costMinor)
    assertEquals(120_000L, value(edited))
    //  نسخهٔ پیش از دستهٔ ۳۱ `costMinor` را نمی‌شناسد: ویرایشش فقط `price` را می‌فرستد و سرور مقدارِ کهنه را نگه می‌دارد (A00)
    val stale = d.copy(warehouseEntries = listOf(w.copy(price = 50.0)))
    assertEquals(100_000L, stale.warehouseEntries.single().costMinor)
    assertEquals(120_000L, value(stale))
    assertEquals(120_000L, cogs(stale))
    //  گردکردنِ بهای واحد (۲۴ × ۴۱٫۶۷ = ۱۰۰۰٫۰۸) هنوز «جور» است
    assertEquals(100_000L, ReportLedger.exactValue(100_000L, 24_000, 4_167))
    assertNull(ReportLedger.exactValue(100_000L, 24_000, 5_000))
  }

  @Test fun `برگشت به تأمین‌کننده به واحدِ خرید — یک کارتنِ ۲۴ ⇒ ۲۴ دانه بیرون و سندِ کسرِ دقیقِ ۱۰۰۰`() {
    val b = buy(d0, "supB", PurchaseBook.Line("tea", 2.0, 1000.0, unitName = "کارتن ۲۴"))
    val r = ok(PurchaseBook.supplierReturn(b, "supB", "tea", 1.0, 1000.0, "خراب", "2026-11-02", "2026-11-02", 5, ::id, key = "ret1", unitName = "کارتن ۲۴"))
    assertEquals(24.0, ShopStore.stock(r, "tea"), 1e-9)
    val note = r.purchases.first { it.id == "ret1" }
    assertEquals(-24.0, note.quantity, 1e-9)
    assertEquals(-1000.0, note.totalAmount, 0.0)
    assertEquals("کارتن ۲۴", note.unitName)
    assertEquals(-1_000L, note.unitQtyMilli)
    assertEquals(1000.0, ShopStore.supplierDebt(r, "supB"), 0.0)
    //  بیش از خریده از همین تأمین‌کننده (۳ کارتن = ۷۲ > ۴۸) رد
    assertTrue(PurchaseBook.supplierReturn(b, "supB", "tea", 3.0, 1000.0, "x", "2026-11-02", "2026-11-02", 5, ::id, unitName = "کارتن ۲۴") is PurchaseBook.Result.Failed)
    //  واحدی که روی کالا نیست
    assertTrue(PurchaseBook.supplierReturn(b, "supB", "tea", 1.0, 1000.0, "x", "2026-11-02", "2026-11-02", 5, ::id, unitName = "بوری") is PurchaseBook.Result.Failed)
  }

  @Test fun `رسیدِ سفارشِ خرید به واحدِ خرید — ۲ کارتنِ ۲۴ ⇒ ۴۸ دانه و جمعِ دقیق`() {
    val po = PurchaseOrderDto(
      id = "po1", no = 3, supplierId = "supB", supplierName = "ب", state = "sent", status = "complete",
      lines = listOf(PoLineDto("l1", "tea", "چای", "کارتن ۲۴", 2_000, 100_000, factorMilli = 24_000), PoLineDto("l2", "rice", "برنج", "دانه", 5_000, 1_000)),
      receipts = listOf(
        PoReceiptDto(
          id = "r1", poId = "po1",
          posted = listOf(PoPostedDto("l1", 2_000, 2_000, 0, 2_000, 0, 0, 100_000, 200_000), PoPostedDto("l2", 5_000, 5_000, 0, 5_000, 0, 0, 1_000, 5_000)),
        ),
      ),
    )
    val d = (PurchaseOrderBook.post(d0, po, po.receipts.single(), "2026-11-01", 1) as PurchaseOrderBook.Posting.Ok).data
    assertEquals(48.0, ShopStore.stock(d, "tea"), 1e-9)
    assertEquals(5.0, ShopStore.stock(d, "rice"), 1e-9)
    assertEquals(2000.0 + 50.0, ShopStore.supplierDebt(d, "supB"), 0.0)
    assertEquals(200_000L, d.warehouseEntries.first { it.productId == "tea" }.costMinor)
    assertNull(d.warehouseEntries.first { it.productId == "rice" }.costMinor)
    //  همان پاسخِ سرور با `factorMilli`؛ ردیفِ بی آن کلید ندارد
    val json = Json { ignoreUnknownKeys = true }
    assertEquals(24_000L, json.decodeFromString(PoLineDto.serializer(), """{"id":"l1","unit":"کارتن ۲۴","qtyMilli":2000,"priceMinor":100000,"factorMilli":24000}""").factorMilli)
    assertFalse(Json.encodeToString(PoLineDto.serializer(), po.lines[1]).contains("factorMilli"))
  }

  @Test fun `اسکنِ بارکدِ واحدِ خرید همان واحد را برمی‌گزیند؛ بارکد مالِ همان کالاست`() {
    val idx = ShopStore.purchaseUnitIndex(d0)
    assertEquals("tea", idx.find("555")?.productId)
    assertEquals("کارتن ۲۴", idx.find("۵۵۵")?.unit?.name)
    assertEquals("کارتن ۱۲", idx.find("444")?.unit?.name)
    assertNull("بارکدِ خودِ کالا واحدِ خرید نیست", idx.find("111"))
    assertEquals("tea", ShopStore.codeOwners(d0)["555"])
    //  کالای دیگر نمی‌تواند همان بارکد را بگیرد
    val taken = WarehouseEngine.editProduct(d0, "rice", WarehouseEngine.ProductDraft(name = "برنج", category = "", unit = "دانه", purchasePrice = 10.0, salePrice = 12.0, barcode = "555"), "2026-11-01", 1, ::id)
    assertTrue(taken is WarehouseEngine.Result.Failed)
    assertTrue(ShopStore.purchaseUnitIndex(d0.copy(products = d0.products.map { it.copy(purchaseUnits = null) })).isEmpty)
  }

  @Test fun `فرمِ کالا — افزودن، ویرایش، برداشتن و سنجش با پیامِ روشن`() {
    val base = d0.copy(products = d0.products.map { it.copy(purchaseUnits = null) })
    fun edit(d: ShopData, vararg u: PurchaseUnits.Draft?) = WarehouseEngine.editProduct(
      d, "tea",
      WarehouseEngine.ProductDraft(name = "چای", category = "", unit = "دانه", purchasePrice = 40.0, salePrice = 50.0, barcode = "111",
        purchaseUnits = if (u.size == 1 && u[0] == null) null else u.filterNotNull()),
      "2026-11-01", 1, ::id,
    )
    val added = okW(edit(base, PurchaseUnits.Draft("کارتن ۱۲", "12", "supA", "444"), PurchaseUnits.Draft("کارتن ۲۴", "24", "supB", "555")))
    assertEquals(listOf(twelve, twentyFour), added.products.first { it.id == "tea" }.purchaseUnits)
    assertTrue(added.auditLog.any { it.notes.contains("واحدهای خرید") })
    val kept = okW(edit(added, null))
    assertEquals(listOf(twelve, twentyFour), kept.products.first { it.id == "tea" }.purchaseUnits)
    val changed = okW(edit(added, PurchaseUnits.Draft("کارتن ۲۴", "20", "supB", "555")))
    assertEquals(listOf(PurchaseUnit("کارتن ۲۴", 20_000, "supB", "555")), changed.products.first { it.id == "tea" }.purchaseUnits)
    val removed = okW(edit(added))
    assertNull("همه برداشته ⇒ null و همان JSON ِ بی واحد", removed.products.first { it.id == "tea" }.purchaseUnits)
    assertTrue(why(edit(base, PurchaseUnits.Draft("کارتن", "12"), PurchaseUnits.Draft("كارتن", "24"))).contains("دو بار"))
    assertTrue(why(edit(base, PurchaseUnits.Draft("کارتن", "12.5"))).contains("اعشار"))
    assertTrue(why(edit(base, PurchaseUnits.Draft("کارتن", "12", barcode = "222"))).contains("کالای دیگری"))
    assertTrue(why(edit(base, PurchaseUnits.Draft("کارتن", "12", supplierId = "supZ"))).isNotBlank())
    //  واحدِ پایه بی فرمِ واحدها عوض نمی‌شود (ضریب به همان واحد است)
    val unitChange = WarehouseEngine.editProduct(added, "tea", WarehouseEngine.ProductDraft(name = "چای", category = "", unit = "کیلو", purchasePrice = 40.0, salePrice = 50.0, barcode = "111"), "2026-11-01", 1, ::id)
    assertTrue(unitChange is WarehouseEngine.Result.Failed)
  }

  @Test fun `همگام‌سازی و ادغام — فیلدِ تازه یک op ِ کالاست و با ویرایشِ قیمتِ دستگاهِ دیگر هر دو می‌مانند`() {
    val base = d0.copy(products = d0.products.map { it.copy(purchaseUnits = null) })
    val a = base.copy(products = base.products.map { if (it.id == "tea") it.copy(purchaseUnits = listOf(twelve, twentyFour)) else it })
    val b = base.copy(products = base.products.map { if (it.id == "tea") it.copy(salePrice = 55.0) else it })
    val opsA = LedgerDiff.diff(LedgerDiff.shadowOf(base), LedgerDiff.toJson(a))
    val op = opsA.single()
    assertEquals("products", op.table)
    assertEquals("tea", op.rowId)
    assertEquals(setOf("purchaseUnits"), op.fields!!.keys)
    assertEquals(2, (op.fields!!["purchaseUnits"] as JsonArray).size)
    //  op ِ A روی دفترِ B
    val merged = LedgerDiff.fromJson(LedgerDiff.applyRemote(LedgerDiff.toJson(b), opsA))
    val tea = merged.products.first { it.id == "tea" }
    assertEquals(listOf(twelve, twentyFour), tea.purchaseUnits)
    assertEquals(55.0, tea.salePrice, 0.0)
    //  ادغامِ سه‌طرفه (کپیِ دیگرِ همان دفتر)
    val m = LedgerMerge.merge(base, a, b).products.first { it.id == "tea" }
    assertEquals(listOf(twelve, twentyFour), m.purchaseUnits)
    assertEquals(55.0, m.salePrice, 0.0)
  }

  @Test fun `کالای بی واحدِ خرید همان JSON و همان خرید است`() {
    val plain = Product(id = "x", name = "x", unit = "دانه")
    assertFalse(Json.encodeToString(Product.serializer(), plain).contains("purchaseUnits"))
    val d = buy(d0, "supA", PurchaseBook.Line("rice", 3.0, 10.0))
    val text = Json.encodeToString(ShopData.serializer(), d)
    for (k in listOf("costMinor", "unitName", "unitQtyMilli", "unitFactorMilli")) assertFalse(k, text.contains("\"$k\""))
    assertEquals(30.0, d.purchases.single().totalAmount, 0.0)
    //  دفترِ بی هیچ واحدِ خرید: همان `barcodeIndex` برای سنجشِ تکرارِ بارکد
    val none = d0.copy(products = d0.products.map { it.copy(purchaseUnits = null) })
    assertEquals(ShopStore.barcodeIndex(none), ShopStore.codeOwners(none))
  }
}
