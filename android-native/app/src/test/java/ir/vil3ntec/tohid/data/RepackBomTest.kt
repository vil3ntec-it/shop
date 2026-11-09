package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrGoods
import ir.vil3ntec.tohid.core.stock.Pick
import ir.vil3ntec.tohid.core.stock.Position
import ir.vil3ntec.tohid.core.stock.StockBook
import ir.vil3ntec.tohid.core.stock.StockCore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  INV-21 — سری و بهای میانگین در سندِ تولید و دستورِ ساختِ ثبت‌شده (`Repack` ِ `lots`، `Bom`؛ پشتِ «دستورِ ساخت و سریِ
 *  تولید»). دو موتور با هم و با بردارِ مشترکِ هسته (`license/test-vectors/stock-v1.json`، همان که `StockCoreVectorsTest` و
 *  `server/test/stock-core.test.js` می‌گذرانند) می‌خوانند: همان سندها روی `StockLedger.replay` همان جایگاه‌ها، ارزش‌ها و
 *  سری‌های بردار را می‌دهند (`W1` = محلِ اصلی، `W2` = «انبار»)، و `ExpiryEngine` (موتورِ امروز) همان محموله‌های انقضادار را.
 */
class RepackBomTest {

  private var seq = 0
  private val newId: () -> String = { "n${(++seq).toString().padStart(3, '0')}" }
  private val today = "2026-11-08"
  private val store = "انبار"

  private val products = listOf(
    Product(id = "C", name = "شیرِ خشک", unit = "کیلو", purchasePrice = 11.0),
    Product(id = "B", name = "بستهٔ صبحانه", unit = "بسته", purchasePrice = 0.0),
    Product(id = "D", name = "پاکتِ شیر", unit = "عدد", purchasePrice = 6.0),
    Product(id = "R", name = "روبان", unit = "عدد", purchasePrice = 0.5),
  )

  /** همان سه دریافتِ موردِ اولِ بردار: X (۱۴۰۵/۰۹)، Y (دیرتر) و Z (سری بی انقضا) */
  private val fefoBase = ShopData(
    products = products,
    warehouseEntries = listOf(
      WarehouseEntry(id = "wx", productId = "C", units = 3.0, price = 10.0, expiryDate = "2026-12-01", batch = "X", createdAt = 10),
      WarehouseEntry(id = "wy", productId = "C", units = 2.0, price = 13.0, expiryDate = "2027-02-01", batch = "Y", createdAt = 20),
      WarehouseEntry(id = "wz", productId = "C", units = 4.0, price = 11.0, batch = "Z", createdAt = 30),
      WarehouseEntry(id = "wr", productId = "R", units = 10.0, price = 0.5, createdAt = 40),
    ),
  )

  private fun ok(r: StockPlaces.Result): ShopData = (r as? StockPlaces.Result.Ok)?.data ?: error("نشد: $r")

  /* ---------------------------------------------------------------- بردارِ مشترک */

  private val vectors: JsonObject by lazy {
    var dir: File? = File(".").absoluteFile
    var file: File? = null
    while (dir != null && file == null) {
      file = File(dir, "license/test-vectors/stock-v1.json").takeIf { it.isFile }
      dir = dir.parentFile
    }
    assertNotNull("license/test-vectors/stock-v1.json پیدا نشد", file)
    Json.parseToJsonElement(file!!.readText()).jsonObject
  }

  private fun case(prefix: String): JsonObject =
    vectors["ledger"]!!.jsonArray.map { it.jsonObject }.single { it["name"]!!.jsonPrimitive.content.startsWith(prefix) }

  private fun all(books: Map<String, StockBook>): List<Position> =
    books.values.flatMap { StockCore.positions(it) }.sortedWith(compareBy<Position> { it.productId }.thenBy { it.warehouseId })

  /** جایگاه‌های نما همان جایگاه‌های پایانیِ موردِ بردار (`w:…` = سریِ تازهٔ خودِ ردیف) و جمعِ تولید صفر */
  private fun sameAsVector(prefix: String, books: Map<String, StockBook>) {
    val fin = case(prefix)["final"]!!.jsonObject
    val want = fin["positions"]!!.jsonArray.map { it.jsonObject }
    val got = all(books)
    assertEquals("$prefix: شمارِ جایگاه‌ها", want.size, got.size)
    fun lot(id: String) = if (id.startsWith("w:")) "w:" else id
    want.zip(got).forEach { (w, p) ->
      val wh = mapOf("W1" to "", "W2" to store).getValue(w["warehouseId"]!!.jsonPrimitive.content)
      assertEquals(w["productId"]!!.jsonPrimitive.content, p.productId)
      assertEquals(StockLedger.wid(wh), p.warehouseId)
      assertEquals("${p.productId} مقدار", w["qtyMilli"]!!.jsonPrimitive.long, p.qtyMilli)
      assertEquals("${p.productId} ارزش", w["valueMinor"]!!.jsonPrimitive.long, p.valueMinor)
      assertEquals(
        "${p.productId} سری‌ها",
        w["lots"]!!.jsonArray.map { lot(it.jsonObject["lotId"]!!.jsonPrimitive.content) to it.jsonObject["qtyMilli"]!!.jsonPrimitive.long },
        p.fefoLots.map { lot(it.lotId) to it.qtyMilli },
      )
      assertTrue("${p.productId}: آزمونِ تطبیق", p.reconciles)
    }
    val totals = books.values.fold(ir.vil3ntec.tohid.core.stock.StockTotals()) { a, b -> a + StockCore.bookTotals(b) }
    assertEquals(0L, totals.productionMinor)
    assertEquals(fin["totals"]!!.jsonObject["purchasesMinor"]!!.jsonPrimitive.long, totals.purchasesMinor)
    //  هیچ سری‌ای منفی نیست (فقط بی سری می‌تواند)
    assertTrue(got.all { p -> p.lots.all { it.lotId.isEmpty() || it.qtyMilli >= 0 } })
  }

  /** موتورِ تازه و موتورِ امروز: مقدارِ هر (کالا، انقضا) ِ محموله‌های انقضادار یکی است */
  private fun enginesAgree(d: ShopData) {
    val v = StockLedger.replay(d)
    assertEquals(emptyList<Pair<String, String>>(), v.errors)
    val fresh = all(v.books).flatMap { p -> p.lots.filter { it.qtyMilli > 0 && it.expiryDate != null }.map { (p.productId to it.expiryDate!!) to it.qtyMilli } }
      .groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
    val today0 = ExpiryEngine.lots(d, today).groupBy({ it.product.id to it.entry.expiryDate }, { StockLedger.milli(it.remaining) }).mapValues { it.value.sum() }
    assertEquals(fresh, today0)
    //  و سری‌های هر محل ِ موتورِ امروز (`StockPlaces.lotsAt`، انتقالِ INV-20) همان سری‌های موتورِ تازه
    val ids = d.products.mapTo(HashSet()) { it.id }
    val places = StockPlaces.lotsAt(d, ids).mapValues { (_, m) -> m.mapValues { (_, ls) -> ls.map { it.lotId to it.qtyMilli } } }.filterValues { it.isNotEmpty() }
    val replayed = v.books.mapValues { (_, b) ->
      StockCore.positions(b).associate { StockLedger.locOf(it.warehouseId) to it.fefoLots.map { l -> l.lotId to l.qtyMilli } }.filterValues { it.isNotEmpty() }
    }.filterValues { it.isNotEmpty() }
    assertEquals(replayed, places)
  }

  /* ---------------------------------------------------------------- آزمون‌ها */

  @Test fun `تولید سری‌های جزء را به FEFO برمی‌دارد و بسته زودترین انقضا را می‌گیرد — همان بردارِ مشترک`() {
    //  فقط همان سه دریافتِ بردار (بی روبان)
    val only = fefoBase.copy(warehouseEntries = fefoBase.warehouseEntries.filter { it.productId == "C" })
    val d1 = ok(Bom.save(only, "bom1", "B", listOf(Repack.Part("C", 5_500)), 0, "کریم", today, 50))
    val d = ok(Bom.build(d1, "op1", "B", 1_000, null, "", "کریم", today, 100, newId))
    val use = d.warehouseEntries.filter { it.refId == "op1" && it.units < 0 }
    //  انقضادار اول (X ِ زودتر، سپس Y)، سریِ بی انقضا (Z) پس از آن‌ها
    assertEquals(
      listOf(Triple("X", -3.0, "2026-12-01"), Triple("Y", -2.0, "2027-02-01"), Triple("Z", -0.5, "")),
      use.map { Triple(it.lotId, it.units, it.expiryDate) },
    )
    assertEquals(listOf("X", "Y", "Z"), use.map { it.batch })
    val out = d.warehouseEntries.single { it.refId == "op1" && it.units > 0 }
    assertEquals("w:" + out.id, out.lotId)
    assertEquals("2026-12-01", out.expiryDate)
    assertEquals("", out.batch)
    //  بها = جمعِ بهای میانگینِ سه ردیفِ مصرف (۳۳۳۳ + ۲۲۲۲ + ۵۵۶ صدم) — همان `costMinor` ِ بردار
    assertEquals(6111L, out.costMinor)
    assertEquals(61.11, out.price, 0.0)
    val v = StockLedger.replay(d)
    val useIds = use.mapTo(HashSet()) { it.id }
    assertEquals(6111L, v.rows.filter { it.id in useIds }.sumOf { it.costMinor })
    assertEquals(listOf(3333L, 2222L, 556L), v.rows.filter { it.id in useIds }.map { it.costMinor })
    sameAsVector("production takes component lots FEFO", v.books)
    enginesAgree(d)
    //  موجودیِ کل همان: ۹ − ۵٫۵ و یک بسته
    assertEquals(3.5, ShopStore.stock(d, "C"), 1e-9)
    assertEquals(1.0, ShopStore.stock(d, "B"), 1e-9)
  }

  @Test fun `انتقال و سپس تولید در همان محل با سریِ برگزیده و سریِ بی انقضا — همان بردارِ مشترک`() {
    val d0 = ShopData(
      products = products,
      warehouseEntries = listOf(
        WarehouseEntry(id = "wx", productId = "C", units = 5.0, price = 10.0, expiryDate = "2026-12-01", batch = "X", createdAt = 10),
        WarehouseEntry(id = "wn", productId = "C", units = 5.0, price = 12.0, batch = "N", createdAt = 20),
      ),
    )
    var d = ok(StockPlaces.transfer(d0, "t1", "C", "", store, 4_000, "پشتِ دکان", "کریم", today, 30, newId, lots = listOf(Pick("N", 4_000))))
    //  ۱٫۵ کارتن از سریِ N در «انبار» ⇒ ۳ دانه با همان سریِ بی انقضا و همان بها
    d = ok(Repack.breakPack(d, "op2", "C", 1_500, "D", 2_000, store, "", "کریم", today, 40, newId, lots = true, chosen = listOf(Pick("N", 1_500))))
    val pieces = d.warehouseEntries.single { it.refId == "op2" && it.units > 0 }
    assertEquals(listOf("N", "N", "", store), listOf(pieces.lotId, pieces.batch, pieces.expiryDate, pieces.locationId))
    assertEquals(3.0, pieces.units, 0.0)
    assertEquals(1650L, pieces.costMinor)
    //  ساختن در محلِ اصلی: FEFO از همان محل (X سپس N)، بی هیچ سریِ منفی
    d = ok(Bom.save(d, "bom2", "B", listOf(Repack.Part("C", 3_000)), 0, "کریم", today, 50))
    d = ok(Bom.build(d, "op3", "B", 2_000, null, "", "کریم", today, 60, newId))
    assertEquals(listOf("X" to -5.0, "N" to -1.0), d.warehouseEntries.filter { it.refId == "op3" && it.units < 0 }.map { it.lotId to it.units })
    assertEquals(6600L, d.warehouseEntries.single { it.refId == "op3" && it.units > 0 }.costMinor)
    sameAsVector("production at a place after a lot transfer", StockLedger.replay(d).books)
    enginesAgree(d)
  }

  @Test fun `سریِ برگزیدهٔ دیرتر — هر دو موتور همان سری را کم می‌کنند نه FEFO`() {
    val d0 = ShopData(
      products = products,
      warehouseEntries = listOf(
        WarehouseEntry(id = "wx", productId = "C", units = 5.0, price = 10.0, expiryDate = "2026-12-01", batch = "X", createdAt = 10),
        WarehouseEntry(id = "wy", productId = "C", units = 5.0, price = 10.0, expiryDate = "2027-02-01", batch = "Y", createdAt = 20),
      ),
    )
    val d = ok(Repack.breakPack(d0, "op4", "C", 2_000, "D", 1_000, null, "", "", today, 40, newId, lots = true, chosen = listOf(Pick("Y", 2_000))))
    val c = ExpiryEngine.lots(d, today).filter { it.product.id == "C" }.map { it.entry.expiryDate to it.remaining }
    assertEquals(listOf("2026-12-01" to 5.0, "2027-02-01" to 3.0), c)
    assertEquals(listOf(Triple("Y", "2027-02-01", 2.0)), d.warehouseEntries.filter { it.refId == "op4" && it.units > 0 }.map { Triple(it.batch, it.expiryDate, it.units) })
    enginesAgree(d)
    //  سریِ برگزیده بیش از ماندهٔ خودش ⇒ رد، بی هیچ ردیف
    val bad = Repack.breakPack(d0, "op5", "C", 6_000, "D", 1_000, null, "", "", today, 40, newId, lots = true, chosen = listOf(Pick("Y", 6_000)))
    assertTrue(bad is StockPlaces.Result.Failed)
  }

  @Test fun `ساختن از دستور با موجودیِ کافی و ناکافی — کمبود با پیامِ روشن و بی هیچ ردیف`() {
    val d1 = ok(Bom.save(fefoBase, "bom3", "B", listOf(Repack.Part("C", 4_000), Repack.Part("R", 1_000)), 250, "کریم", today, 50))
    assertEquals(Bom.Recipe("bom3", "B", listOf(Repack.Part("C", 4_000), Repack.Part("R", 1_000)), 250, 50), Bom.of(d1, "B"))
    //  ۳ بسته با اتلافِ ٪۲٫۵ ⇒ ۱۲٫۳ کیلو؛ فقط ۹ هست
    val short = Bom.build(d1, "op6", "B", 3_000, null, "", "کریم", today, 100, newId)
    assertEquals(StockPlaces.Result.Failed(StrGoods.repackShort("شیرِ خشک", "", ir.vil3ntec.tohid.qty(9.0), "کیلو", ir.vil3ntec.tohid.qty(12.3))), short)
    val before = I18n.source
    try {
      I18n.source = { Lang.PRS }
      val dari = (Bom.build(d1, "op6", "B", 3_000, null, "", "کریم", today, 100, newId) as StockPlaces.Result.Failed).message
      assertTrue(dari, dari.contains("«شیرِ خشک»") && dari.contains(ir.vil3ntec.tohid.qty(12.3)))
    } finally {
      I18n.source = before
    }
    //  ۲ بسته ⇒ ۸٫۲ کیلو و ۲٫۰۵ روبان؛ بها جمعِ بهای میانگینِ همهٔ ردیف‌های مصرف، دقیقاً
    val d = ok(Bom.build(d1, "op6", "B", 2_000, null, "", "کریم", today, 100, newId))
    val rows = d.warehouseEntries.filter { it.refId == "op6" }
    assertEquals(8_200L, rows.filter { it.productId == "C" }.sumOf { -StockLedger.milli(it.units) })
    assertEquals(2_050L, rows.filter { it.productId == "R" }.sumOf { -StockLedger.milli(it.units) })
    val out = rows.single { it.units > 0 }
    val v = StockLedger.replay(d)
    val inCost = v.rows.filter { r -> rows.any { it.id == r.id && it.units < 0 } }.sumOf { it.costMinor }
    assertEquals(inCost, out.costMinor)
    assertEquals(out.costMinor, v.rows.single { it.id == out.id }.costMinor)
    //  ارزشِ کلِ دفتر = جمعِ خریدها (تولید فقط جابه‌جا می‌کند)
    assertEquals(3_000L + 2_600L + 4_400L + 500L, StockLedger.totalValueMinor(v))
    assertTrue(StockLedger.reconcileAll(v))
    enginesAgree(d)
    //  دستورِ نادرست رد می‌شود
    assertTrue(Bom.save(fefoBase, "bad1", "B", listOf(Repack.Part("B", 1_000)), 0, "", today, 1) is StockPlaces.Result.Failed)
    assertTrue(Bom.save(fefoBase, "bad2", "B", listOf(Repack.Part("C", 1_000)), 6_000, "", today, 1) is StockPlaces.Result.Failed)
    assertTrue(Bom.save(fefoBase, "bad3", "B", emptyList(), 0, "", today, 1) is StockPlaces.Result.Failed)
    assertTrue(Bom.build(fefoBase, "op9", "B", 1_000, null, "", "", today, 1, newId) is StockPlaces.Result.Failed)
  }

  @Test fun `ویرایشِ دستور هیچ سندِ ساختِ پیشین را عوض نمی‌کند`() {
    var d = ok(Bom.save(fefoBase, "v1", "B", listOf(Repack.Part("C", 2_000)), 0, "کریم", today, 50))
    d = ok(Bom.build(d, "op7", "B", 1_000, null, "", "کریم", today, 60, newId))
    val rows = d.warehouseEntries.filter { it.refId == "op7" }
    val doc = d.auditLog.single { it.id == "op7" }
    val ledger = StockLedger.replay(d).rows.filter { r -> rows.any { it.id == r.id } }
    //  ویرایش: ردیفِ تازه با اجزا و اتلافِ دیگر
    d = ok(Bom.save(d, "v2", "B", listOf(Repack.Part("C", 3_000)), 1_000, "کریم", today, 70))
    assertEquals(listOf(Repack.Part("C", 3_000)) to 1_000, Bom.of(d, "B")!!.let { it.parts to it.lossBp })
    assertEquals(2, d.auditLog.count { it.type == Bom.TYPE })
    assertEquals(rows, d.warehouseEntries.filter { it.refId == "op7" })
    assertEquals(doc, d.auditLog.single { it.id == "op7" })
    assertEquals(ledger, StockLedger.replay(d).rows.filter { r -> rows.any { it.id == r.id } })
    //  ساختِ بعدی از دستورِ تازه: ۳ × ۱٫۱ = ۳٫۳
    d = ok(Bom.build(d, "op8", "B", 1_000, null, "", "کریم", today, 80, newId))
    assertEquals(3_300L, d.warehouseEntries.filter { it.refId == "op8" && it.units < 0 }.sumOf { -StockLedger.milli(it.units) })
    //  برداشتن ⇒ دستور نیست؛ سندها همان
    d = ok(Bom.remove(d, "v3", "B", "کریم", today, 90))
    assertNull(Bom.of(d, "B"))
    assertEquals(rows, d.warehouseEntries.filter { it.refId == "op7" })
    assertEquals(ledger, StockLedger.replay(d).rows.filter { r -> rows.any { it.id == r.id } })
    assertTrue(Bom.remove(d, "v4", "B", "کریم", today, 95) is StockPlaces.Result.Failed)
  }

  @Test fun `کالای بی سری با تنظیمِ روشن همان یک ردیف و بها از میانگین`() {
    val plain = fefoBase.copy(warehouseEntries = listOf(WarehouseEntry(id = "w1", productId = "C", units = 3.0, price = 10.0, createdAt = 5)))
    val d = ok(Repack.breakPack(plain, "op10", "C", 3_000, "D", 2_000, null, "", "", today, 40, newId, lots = true))
    val rows = d.warehouseEntries.filter { it.refId == "op10" }
    assertEquals(listOf(null to -3.0, null to 6.0), rows.map { it.lotId to it.units })
    assertEquals(listOf("", ""), rows.map { it.expiryDate })
    assertEquals(3_000L, rows.single { it.units > 0 }.costMinor)
    //  تنظیمِ خاموش: هیچ `lotId` و `costMinor` ِ تازه (همان ردیف‌های امروز)
    val off = ok(Repack.breakPack(plain, "op10", "C", 3_000, "D", 2_000, null, "", "", today, 40, newId))
    assertFalse(off.warehouseEntries.any { it.refId == "op10" && (it.lotId != null || it.costMinor != null) })
    assertEquals(rows.map { it.units }, off.warehouseEntries.filter { it.refId == "op10" }.map { it.units })
    enginesAgree(d)
  }
}
