package ir.vil3ntec.tohid.core.perm

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 *  STF-03 — `SaleCaps` در برابرِ `core/test-vectors/sale-caps-v1.json`؛ همان فایل را `server/test/sale-caps.test.js` برای
 *  `server/src/lib/sale-caps.js` می‌خواند. پس پیش‌سنجیِ صندوق و داوریِ سرور یک قاعده‌اند (قیمتِ فهرست، تخفیفِ پنهان، قرض).
 */
class SaleCapsVectorsTest {

  private val v: JsonObject = run {
    var dir: File? = File("").absoluteFile
    while (dir != null) {
      val f = File(dir, "core/test-vectors/sale-caps-v1.json")
      if (f.isFile) return@run Json.parseToJsonElement(f.readText()).jsonObject
      dir = dir.parentFile
    }
    error("core/test-vectors/sale-caps-v1.json پیدا نشد")
  }

  private fun JsonObject.l(k: String): Long = getValue(k).jsonPrimitive.long
  private fun JsonObject.s(k: String): String = getValue(k).jsonPrimitive.content
  private fun JsonObject.ln(k: String): Long? = (get(k) as? JsonPrimitive)?.longOrNull
  /** تصمیمِ ۱-۱۱ (۳): `wholesale` ِ بردار؛ نیامده ⇒ درست (همان پیش‌فرضِ هسته) */
  private fun JsonObject.ws(): Boolean = (get("wholesale") as? JsonPrimitive)?.booleanOrNull ?: true
  private fun rows(k: String): List<JsonObject> = v.getValue(k).jsonArray.map { it.jsonObject }

  private fun ref(e: JsonElement?): SaleCaps.Ref? {
    val o = e as? JsonObject ?: return null
    return SaleCaps.Ref(
      saleMinor = o.l("saleMinor"),
      wholesaleMinor = o.ln("wholesaleMinor") ?: 0,
      windows = (o["windows"] as? JsonArray).orEmpty().map { w -> w.jsonObject.let { SaleCaps.Window(it.s("from"), it.s("to"), it.l("priceMinor")) } },
      packs = (o["packs"] as? JsonArray).orEmpty().map { p -> p.jsonObject.let { SaleCaps.Pack(it.l("sizeMilli"), it.l("priceMinor")) } },
    )
  }

  private fun line(o: JsonObject) = SaleCaps.Line(o.s("productId"), o.l("unitMinor"), o.l("qtyMilli"), o.ln("promoMinor") ?: 0)

  private fun sale(o: JsonObject) = SaleCaps.Sale(
    id = o.s("id"), totalMinor = o.l("totalMinor"), discountMinor = o.l("discountMinor"), finalTotalMinor = o.ln("finalTotalMinor"),
    roundingMinor = o.ln("roundingMinor") ?: 0, debtGivenMinor = o.ln("debtGivenMinor") ?: 0, date = o.s("date"),
    priorDebtMinor = o.ln("priorDebtMinor") ?: 0, loyaltyMinor = o.ln("loyaltyMinor") ?: 0,
    wholesale = (o["wholesale"] as? JsonPrimitive)?.booleanOrNull ?: false,
    taxMinor = o.ln("taxMinor") ?: 0, // POS-37
  )

  private fun need(o: JsonObject) = ApprovalSlip.Need(o.s("perm"), o.s("doc"), o.ln("pctBp"), o.ln("amount"))

  /** درختِ JSON ⇐ Map / List / String / Double / Boolean (همان `PermPolicy.tree` ِ برنامه) */
  private fun tree(e: JsonElement?): Any? = when (e) {
    null, JsonNull -> null
    is JsonObject -> LinkedHashMap<String, Any?>().also { m -> e.forEach { (k, x) -> m[k] = tree(x) } }
    is JsonArray -> e.map { tree(it) }
    is JsonPrimitive -> if (e.isString) e.content else e.booleanOrNull ?: e.doubleOrNull
  }

  @Test
  fun constants() {
    assertEquals(SaleCaps.VERSION.toLong(), v.l("version"))
    assertEquals(SaleCaps.ROUND_MAX_MINOR, v.l("round_max_minor"))
    assertEquals(SaleCaps.TOL_MINOR, v.l("tol_minor"))
  }

  @Test
  fun grossAndRefs() {
    for (r in rows("gross")) assertEquals(r.l("out"), SaleCaps.gross(r.l("unit"), r.l("qty")), r.toString())
    for (r in rows("refs")) assertEquals(r.getValue("out").jsonArray.map { it.jsonPrimitive.long }, SaleCaps.refUnits(ref(r["product"])!!, r.s("date"), r.ws()), r.toString())
  }

  /** پخشِ امتیاز همان `Loyalty.split` ِ صندوق است که `sale-caps.split` ِ سرور هم همان را می‌دهد */
  @Test
  fun loyaltySplit() {
    val all = rows("split")
    assertTrue(all.size >= 5)
    for (r in all) {
      val want = (r["out"] as? JsonArray)?.map { it.jsonPrimitive.long }
      assertEquals(want, ir.vil3ntec.tohid.core.promo.Loyalty.split(r.getValue("lines").jsonArray.map { it.jsonPrimitive.long }, r.l("value")), r.toString())
    }
  }

  @Test
  fun lines() {
    val all = rows("lines")
    assertTrue(all.size >= 15)
    for (r in all) {
      val o = r.getValue("out").jsonObject
      val got = SaleCaps.line(line(r.getValue("line").jsonObject), ref(r["product"]), r.s("date"), r.ws())
      assertEquals(
        SaleCaps.LineCheck(o.getValue("exempt").jsonPrimitive.boolean, o.l("listMinor"), o.l("soldMinor"), o.l("deviationBp"), o.l("reductionMinor")),
        got, r.s("_"),
      )
    }
  }

  @Test
  fun basis() {
    for (r in rows("basis")) {
      val o = r.getValue("out").jsonObject
      assertEquals(
        SaleCaps.Basis(o.l("discountMinor"), o.l("baseMinor"), o.l("reductionMinor"), o.l("gapMinor")),
        SaleCaps.basis(r.l("discount"), r.l("total"), r.ln("final"), r.l("rounding"), r.l("reduction"), r.ln("tax") ?: 0), r.toString(),
      )
    }
  }

  @Test
  fun needsAndDecisions() {
    for (r in rows("needs")) {
      val refs = r.getValue("refs").jsonObject.mapValues { ref(it.value)!! }
      val got = SaleCaps.needs(
        sale(r.getValue("sale").jsonObject), r.getValue("lines").jsonArray.map { line(it.jsonObject) }, refs,
        r.getValue("enforcePrice").jsonPrimitive.boolean, r.getValue("enforceCredit").jsonPrimitive.boolean,
      )
      assertEquals(r.getValue("out").jsonArray.map { need(it.jsonObject) }, got, r.s("_"))
    }
    for (r in rows("judge")) {
      @Suppress("UNCHECKED_CAST")
      val layers = tree(r.getValue("layers")) as List<Any?>
      val got = r.getValue("needs").jsonArray.map { need(it.jsonObject) }
        .map { it.perm to PermCore.evaluate(r.s("role"), layers, it.perm, SaleCaps.req(it)).decision }
      assertEquals(r.getValue("out").jsonArray.map { it.jsonObject.let { o -> o.s("perm") to o.s("decision") } }, got, r.s("_"))
    }
  }

  /** تصمیمِ ۱-۱۱ (۲): قلمِ آزاد همان `isFree` ِ سرور — بی کارت یا کالای ناشناخته، مقدارِ مثبت */
  @Test
  fun freeLineIsALineWithoutAProductCard() {
    val refs = mapOf("p1" to SaleCaps.Ref(100_000))
    assertTrue(SaleCaps.isFree(SaleCaps.Line("", 5_000, 1_000), refs))
    assertTrue(SaleCaps.isFree(SaleCaps.Line(" ", 0, 1_000), refs))
    assertTrue(SaleCaps.isFree(SaleCaps.Line("p9", 5_000, 1_000), refs))
    assertFalse(SaleCaps.isFree(SaleCaps.Line("p1", 5_000, 1_000), refs))
    assertFalse(SaleCaps.isFree(SaleCaps.Line("", 5_000, 0), refs))
    assertFalse(SaleCaps.isFree(SaleCaps.Line("", -1, 1_000), refs))
    assertTrue(rows("needs").any { r -> r.getValue("out").jsonArray.any { it.jsonObject.s("perm") == SaleCaps.FREE_LINE } })
  }

  @Test
  fun stateFollowsSlipCoverage() {
    val n = ApprovalSlip.Need(SaleCaps.CREDIT, "sales/s1", null, 50_000)
    val slip = ApprovalSlip.Slip("shop", "sales/s1", SaleCaps.CREDIT, null, 50_000, "u-owner", "remote", 1, 2, "apr_000000000000000000000001", "x")
    assertEquals(SaleCaps.State.FREE, SaleCaps.state("allow", n, null))
    assertEquals(SaleCaps.State.NEEDS_APPROVAL, SaleCaps.state("approve", n, null))
    assertEquals(SaleCaps.State.APPROVED, SaleCaps.state("approve", n, slip))
    assertEquals(SaleCaps.State.NEEDS_APPROVAL, SaleCaps.state("approve", n.copy(amount = 50_001), slip), "تأییدِ کوچک‌تر نمی‌پوشاند")
    assertEquals(SaleCaps.State.NEEDS_APPROVAL, SaleCaps.state("approve", n.copy(doc = "sales/s2"), slip), "سندِ دیگر نه")
    assertEquals(SaleCaps.State.DENIED, SaleCaps.state("deny", n, slip), "ممنوع با تأیید هم نه")
  }
}
