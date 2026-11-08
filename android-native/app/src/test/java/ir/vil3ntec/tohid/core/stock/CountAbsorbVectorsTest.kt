package ir.vil3ntec.tohid.core.stock

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  بردارهای مرجعِ INV-10 (`license/test-vectors/count-absorb-v1.json`) — همان فایلی که
 *  `server/test/count-absorb.test.js` روی `license/count-absorb-core.js` می‌دواند؛ هر دو یک عدد.
 *  قاعده: `gaps(lines, events)` ⇒ فقط ناصفرها با `lineId`؛ موجودی = `raw − gap`.
 *  در CIِ اندروید (cwd = `android-native/app`) و کامپیوتر (cwd = `desktop`)؛ فایل با بالا رفتن از cwd پیدا می‌شود.
 */
class CountAbsorbVectorsTest {

  private val vectors: JsonObject by lazy {
    var dir: File? = File(".").absoluteFile
    var file: File? = null
    while (dir != null && file == null) {
      file = File(dir, "license/test-vectors/count-absorb-v1.json").takeIf { it.isFile }
      dir = dir.parentFile
    }
    assertNotNull("license/test-vectors/count-absorb-v1.json پیدا نشد (cwd=${File(".").absoluteFile})", file)
    Json.parseToJsonElement(file!!.readText()).jsonObject
  }

  private fun JsonObject.str(k: String): String? = this[k]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content
  private fun JsonObject.long(k: String): Long? = this[k]?.takeIf { it !is JsonNull }?.jsonPrimitive?.longOrNull

  private fun event(o: JsonObject) = CountAbsorb.Event(
    id = o.str("id")!!, productId = o.str("productId")!!, at = o.long("at")!!, qtyMilli = o.long("qtyMilli")!!,
    type = o.str("type") ?: "", refId = o.str("refId"), correctedAt = o.long("correctedAt"),
  )

  private fun line(o: JsonObject) = CountAbsorb.Line(
    id = o.str("id")!!, productId = o.str("productId")!!, countedAt = o.long("countedAt")!!, seenMilli = o.long("seenMilli"),
    createdAt = o.long("createdAt")!!, correctedAt = o.long("correctedAt"), serverAt = o.long("serverAt"),
  )

  @Test fun formatAndBounds() {
    assertEquals(CountAbsorb.VECTORS_FORMAT, vectors.str("format"))
    assertEquals(CountAbsorb.WINDOW_MS, vectors.long("window_ms"))
    assertEquals(CountAbsorb.SKEW_MS, vectors.long("skew_ms"))
  }

  @Test fun cases() {
    val cases = vectors["cases"]!!.jsonArray
    assertTrue(cases.size >= 20)
    for (c in cases.map { it.jsonObject }) {
      val name = c.str("name")
      val gaps = CountAbsorb.gaps(c["lines"]!!.jsonArray.map { line(it.jsonObject) }, c["events"]!!.jsonArray.map { event(it.jsonObject) })
      val expect = c["expect"]!!.jsonObject
      val want = expect["gaps"]!!.jsonObject.mapValues { (_, v) -> v.jsonObject.str("lineId") to v.jsonObject.long("gapMilli") }
      assertEquals("$name: gaps", want, gaps.mapValues { (_, g) -> g.lineId to g.gapMilli })
      val raw = c["raw"]!!.jsonObject.mapValues { it.value.jsonPrimitive.longOrNull!! }
      val stock = raw.mapValues { (pid, v) -> v - (gaps[pid]?.gapMilli ?: 0L) }
      assertEquals("$name: stock", expect["stock"]!!.jsonObject.mapValues { it.value.jsonPrimitive.longOrNull }, stock)
    }
  }

  @Test fun seen() {
    for (s in vectors["seen"]!!.jsonArray.map { it.jsonObject }) {
      val got = CountAbsorb.seen(s["events"]!!.jsonArray.map { event(it.jsonObject) }, s.str("productId")!!, s.long("countedAt")!!, s.long("createdAt")!!)
      assertEquals(s.str("name"), s.long("expect"), got)
    }
  }

  @Test fun trusted() {
    for (t in vectors["trusted"]!!.jsonArray.map { it.jsonObject }) {
      val l = line(t["line"]!!.jsonObject)
      assertEquals(l.id, t["expect"]!!.jsonPrimitive.booleanOrNull, CountAbsorb.trusted(l))
    }
  }
}
