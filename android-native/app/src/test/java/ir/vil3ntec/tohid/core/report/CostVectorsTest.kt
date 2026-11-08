package ir.vil3ntec.tohid.core.report

import ir.vil3ntec.tohid.core.promo.PromoEffectRow
import ir.vil3ntec.tohid.data.PromoEffectBook
import ir.vil3ntec.tohid.data.ReportBridge
import ir.vil3ntec.tohid.data.ShopData
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  RPT-03 · PRD-15 · PRM-16 — بردارهای مشترکِ میانگینِ موزونِ متحرک (`core/test-vectors/cost-v1.json`) روی هستهٔ Kotlin:
 *  `ReportCore.costs`/`stockCost`/`sales` (بازپخش با `StockCore`)، پلِ دفترِ برنامه (`ReportBridge.stockCostOf`، کامل و
 *  فقط یک کالا) و اثرِ تخفیف (`PromoEffectBook` ⇐ `PromoEffect`) روی موردِ F. همین فایل را `server/test/cost-vectors.test.js`
 *  روی `license/report-core.js` و گزارشِ خودکارِ سرور می‌دواند — پس عددِ برنامه و سرور برای یک دفتر یکی است.
 *  هم در `android-native/app` و هم در `desktop/` اجرا می‌شود.
 */
class CostVectorsTest {
  private val vectors: JsonObject by lazy { Json.parseToJsonElement(vectorFile().readText()).jsonObject }
  private val cases get() = vectors.getValue("cases").jsonArray.map { it.jsonObject }
  private val from get() = vectors.getValue("range").jsonObject.getValue("from").jsonPrimitive.content
  private val to get() = vectors.getValue("range").jsonObject.getValue("to").jsonPrimitive.content
  private val lenient = Json { ignoreUnknownKeys = true }
  private val enc = Json { encodeDefaults = true }

  private fun expect(c: JsonObject) = c.getValue("expect").jsonObject
  private fun longs(o: JsonElement) = o.jsonObject.mapValues { it.value.jsonPrimitive.long }
  private fun stockJson(s: ReportCore.StockCost): JsonElement = enc.encodeToJsonElement(ReportCore.StockCost.serializer(), s)

  private fun totals(t: ReportCore.Totals): Map<String, Any?> = mapOf(
    "sales" to t.sales, "returns" to t.returns, "netSales" to t.netSales, "cogs" to t.cogs, "grossProfit" to t.grossProfit,
    "marginBp" to t.marginBp, "costEstimatedLines" to t.costEstimatedLines, "profitApproximate" to t.profitApproximate,
  )

  private fun expTotals(o: JsonObject): Map<String, Any?> = mapOf(
    "sales" to o.getValue("sales").jsonPrimitive.long, "returns" to o.getValue("returns").jsonPrimitive.long,
    "netSales" to o.getValue("netSales").jsonPrimitive.long, "cogs" to o.getValue("cogs").jsonPrimitive.long,
    "grossProfit" to o.getValue("grossProfit").jsonPrimitive.long,
    "marginBp" to (o.getValue("marginBp") as? JsonPrimitive)?.takeIf { it !is JsonNull }?.long,
    "costEstimatedLines" to o.getValue("costEstimatedLines").jsonPrimitive.int,
    "profitApproximate" to o.getValue("profitApproximate").jsonPrimitive.boolean,
  )

  @Test
  fun `پوشش و قالب`() {
    assertEquals("cost-v1", vectors.getValue("format").jsonPrimitive.content)
    assertEquals(listOf("A", "B", "C", "D", "D2", "E", "F"), cases.map { it.getValue("id").jsonPrimitive.content })
  }

  @Test
  fun `هر مورد عیناً همان expect در هسته`() {
    for (c in cases) {
      val id = c.getValue("id").jsonPrimitive.content
      val raw = c.getValue("ledger").jsonObject
      val l = ReportLedger.parse(raw)
      val e = expect(c)
      val costs = ReportCore.costs(l, ReportCore.Costing.MAVG)
      assertEquals("$id بهای قلم", longs(e.getValue("line")), costs.line)
      assertEquals("$id بهای برگشتی", longs(e.getValue("ret")), costs.ret)
      assertEquals("$id تخمینی", e.getValue("est").jsonArray.map { it.jsonPrimitive.content }, costs.est.sorted())
      assertEquals("$id بهای میانگین و ارزشِ موجودی", e.getValue("stock"), stockJson(ReportCore.stockCost(l)))
      val s = ReportCore.sales(l, from, to, null, ReportCore.Costing.MAVG, costApproved = true)
      assertEquals("$id سود با میانگینِ موزون", expTotals(e.getValue("sales").jsonObject), totals(s.totals))
      e["lineSales"]?.let { assertEquals("$id قیمتِ روی قلم", expTotals(it.jsonObject), totals(ReportCore.sales(l, from, to).totals)) }
    }
  }

  @Test
  fun `پلِ دفترِ برنامه همان عدد، کامل و فقط یک کالا`() {
    for (c in cases) {
      val id = c.getValue("id").jsonPrimitive.content
      val d = lenient.decodeFromJsonElement(ShopData.serializer(), c.getValue("ledger"))
      val want = expect(c).getValue("stock")
      assertEquals("$id پلِ کامل", want, stockJson(ReportBridge.stockCostOf(d)))
      for (row in want.jsonObject.getValue("rows").jsonArray) {
        val pid = row.jsonObject.getValue("productId").jsonPrimitive.content
        assertEquals("$id فقط $pid", listOf(row), stockJson(ReportBridge.stockCostOf(d, listOf(pid))).jsonObject.getValue("rows").jsonArray.toList())
      }
    }
  }

  @Test
  fun `F اثرِ تخفیف با میانگینِ موزون، فاکتورِ باطل بیرون و برگشتی به نسبت`() {
    val c = cases.single { it.getValue("id").jsonPrimitive.content == "F" }
    val d = lenient.decodeFromJsonElement(ShopData.serializer(), c.getValue("ledger"))
    val p = expect(c).getValue("promo").jsonObject
    val r = PromoEffectBook.of(d, ReportBridge.ledgerOf(d), from, to, ReportCore.Costing.MAVG, approved = true)
    assertEquals(p.getValue("totalRevenueMinor").jsonPrimitive.long, r.totalRevenueMinor)
    assertEquals(p.getValue("promoRevenueMinor").jsonPrimitive.long, r.promoRevenueMinor)
    assertEquals(p.getValue("promoDiscountMinor").jsonPrimitive.long, r.promoDiscountMinor)
    assertEquals(p.getValue("profitApproximate").jsonPrimitive.boolean, r.profitApproximate)
    fun row(x: PromoEffectRow) = mapOf(
      "id" to x.id, "name" to x.name, "invoices" to x.invoices.toLong(), "lines" to x.lines.toLong(), "qtyMilli" to x.qtyMilli,
      "discountMinor" to x.discountMinor, "revenueMinor" to x.revenueMinor, "profitMinor" to x.profitMinor,
      "costMissingLines" to x.costMissingLines.toLong(), "revenueShareBp" to x.revenueShareBp, "discountDepthBp" to x.discountDepthBp,
    )
    val want = p.getValue("rows").jsonArray.map { o ->
      o.jsonObject.mapValues { (k, v) -> if (k == "id" || k == "name") v.jsonPrimitive.content else v.jsonPrimitive.long }
    }
    assertEquals(want, r.rows.map(::row))
    //  قیمتِ روی قلم (انتخابِ همین دستگاه) هرگز سودِ قطعی نمی‌دهد
    val line = PromoEffectBook.of(d, ReportBridge.ledgerOf(d), from, to, ReportCore.Costing.LINE, approved = false)
    assertTrue(line.profitApproximate)
    //  قیمتِ آخرِ روی قلم ۵۵ است و میانگین ۵۰: (۲۸۰ − ۲۲۰) + (۲۸۰ − ۲۲۰) و (۲۸۰ − ۲۲۰)، نه ۱۶۰ و ۸۰
    assertEquals(listOf(12_000L, 6_000L), line.rows.map { it.profitMinor })
  }

  companion object {
    fun vectorFile(): File {
      var d: File? = File(".").absoluteFile
      while (d != null) {
        val f = File(d, "core/test-vectors/cost-v1.json")
        if (f.isFile) return f
        d = d.parentFile
      }
      error("core/test-vectors/cost-v1.json پیدا نشد (cwd=${File(".").absoluteFile})")
    }
  }
}
