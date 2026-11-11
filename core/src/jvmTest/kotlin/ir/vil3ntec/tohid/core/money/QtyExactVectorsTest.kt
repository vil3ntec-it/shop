package ir.vil3ntec.tohid.core.money

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 *  PRD-08 — `QtyExact` روی همهٔ `core/test-vectors/qty-exact-v1.json` (سازنده `server/scripts/qty-exact-vectors.js`؛ همان
 *  فایل را `server/test/qty-exact.test.js` روی هم‌زادِ JS می‌دواند). «legacyMinor» خودِ `lineTotal` ِ وبِ امروز است، پس
 *  `Math.round` ِ JDK و `mathRound` ِ هسته روی همان ضربِ Double همان عدد را می‌دهند؛ «exactMinor» قاعدهٔ ۳.۴.
 */
class QtyExactVectorsTest {

  private val v: JsonObject = run {
    var dir: File? = File("").absoluteFile
    while (dir != null) {
      val f = File(dir, "core/test-vectors/qty-exact-v1.json")
      if (f.isFile) return@run Json.parseToJsonElement(f.readText()).jsonObject
      dir = dir.parentFile
    }
    error("core/test-vectors/qty-exact-v1.json پیدا نشد")
  }

  private fun rows(k: String): List<JsonObject> = v.getValue(k).jsonArray.map { it.jsonObject }
  private fun JsonObject.d(k: String): Double = getValue(k).jsonPrimitive.double
  private fun JsonObject.l(k: String): Long = getValue(k).jsonPrimitive.long
  private fun JsonObject.n(k: String): String = getValue(k).jsonPrimitive.content

  @Test fun lineTotals_offIsTodayOnIsTheExactRule() {
    assertEquals(2, v.getValue("version").jsonPrimitive.int)
    assertEquals(QtyExact.VERSION, v.getValue("version").jsonPrimitive.int)
    val line = rows("line")
    assertTrue(line.size >= 20)
    for (r in line) {
      val price = r.d("price"); val qty = r.d("qty"); val name = r.n("name")
      assertEquals(r.l("qtyMilli"), QtyExact.milli(qty), name)
      assertEquals(r.l("legacyMinor"), QtyExact.legacyMinor(price, qty), name)
      //  همان `SalesEngine.lineTotal` ِ امروز (`roundToLong` = `Math.round` ِ JDK)
      assertEquals(r.l("legacyMinor"), Math.round(price * qty) * 100, name)
      assertEquals(r.getValue("exactMinor").jsonPrimitive.longOrNull, QtyExact.exactMinor(r.l("priceMinor"), r.l("qtyMilli")), name)
      assertEquals(r.l("legacyMinor"), QtyExact.lineMinor(price, qty, on = false), "$name: پرچمِ خاموش")
      assertEquals(r.l("onMinor"), QtyExact.lineMinor(price, qty, on = true), name)
    }
  }

  @Test fun cartSumRoomAndClean_exactToTheMilli() {
    for (r in rows("plus")) {
      assertEquals(r.d("legacy"), r.d("a") + r.d("b"), r.n("name"))
      assertEquals(r.l("milli") / 1000.0, QtyExact.plus(r.d("a"), r.d("b")), r.n("name"))
    }
    for (r in rows("room")) assertEquals(r.l("milli") / 1000.0, QtyExact.room(r.d("available"), r.d("inCart")), r.n("name"))
  }

  @Test fun quantityText_tooPrecise() {
    for (r in rows("precise")) assertEquals(r.getValue("over").jsonPrimitive.boolean, QtyExact.tooPrecise(r.n("text")), r.n("name"))
  }

  /** همان جاروبِ JS: فرق فقط مقدارِ اعشاری، فقط +۱ افغانی در نیمهٔ دقیق؛ مقدارِ درست هرگز */
  @Test fun sweep_sameCountsAsJs() {
    val s = v.getValue("sweep").jsonObject
    val qr = s.getValue("qtyMilli").jsonArray; val pr = s.getValue("priceMinor").jsonArray
    val step = s.l("priceStepMinor")
    var pairs = 0L; var diffs = 0L; var sumOn = 0L; var sumLegacy = 0L
    val first = mutableListOf<List<Long>>()
    for (q in qr[0].jsonPrimitive.long..qr[1].jsonPrimitive.long) {
      if (q % 1000 == 0L) continue
      var p = pr[0].jsonPrimitive.long
      while (p <= pr[1].jsonPrimitive.long) {
        val lg = QtyExact.lineMinor(p / 100.0, q / 1000.0, on = false)
        val got = QtyExact.lineMinor(p / 100.0, q / 1000.0, on = true)
        pairs++; sumOn += got; sumLegacy += lg
        if (got != lg) {
          diffs++
          assertEquals(100L, got - lg, "$q × $p")
          assertEquals(50_000L, (q * p) % 100_000, "$q × $p")
          if (first.size < s.getValue("first").jsonArray.size) first.add(listOf(q, p, lg, got))
        }
        p += step
      }
    }
    assertEquals(s.l("pairs"), pairs)
    assertEquals(s.l("diffs"), diffs)
    assertEquals(s.l("sumOn"), sumOn)
    assertEquals(s.l("sumLegacy"), sumLegacy)
    assertEquals(s.getValue("first").jsonArray.map { r -> r.jsonArray.map { it.jsonPrimitive.long } }, first)

    val w = v.getValue("whole").jsonObject
    val wq = w.getValue("qtyMilli").jsonArray; val wp = w.getValue("priceMinor").jsonArray
    var wPairs = 0L; var wOn = 0L; var wExact = 0L
    var q = wq[0].jsonPrimitive.long
    while (q <= wq[1].jsonPrimitive.long) {
      for (p in wp[0].jsonPrimitive.long..wp[1].jsonPrimitive.long) {
        val lg = QtyExact.lineMinor(p / 100.0, q / 1000.0, on = false)
        wPairs++
        if (QtyExact.lineMinor(p / 100.0, q / 1000.0, on = true) != lg) wOn++
        if (QtyExact.exactMinor(p, q) != lg) wExact++
      }
      q += w.l("qtyStepMilli")
    }
    assertEquals(w.l("pairs"), wPairs)
    //  ۳-۱۲: روشن ⇒ مقدارِ درست هم همان قاعدهٔ دقیق (۴۲ نیمهٔ دقیق از ۹۰٬۰۰۰)
    assertEquals(w.l("onDiffs"), wOn)
    assertEquals(w.l("exactDiffs"), wExact)
    assertEquals(wExact, wOn, "روشن جز قاعدهٔ دقیق")
  }
}
