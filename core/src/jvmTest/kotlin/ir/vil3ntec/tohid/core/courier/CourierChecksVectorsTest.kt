package ir.vil3ntec.tohid.core.courier

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
 *  DLV-07/17: `CourierChecks` در برابرِ بردارِ `core/test-vectors/courier-checks-v1.json` — همان که
 *  `server/test/courier-checks.test.js` با `lib/courier-checks` می‌خواند. فایل‌خوانی فقط این‌جا (jvmTest).
 */
class CourierChecksVectorsTest {

  private val vec: JsonObject = run {
    var dir: File? = File(System.getProperty("user.dir")).absoluteFile
    while (dir != null) {
      val f = File(dir, "core/test-vectors/courier-checks-v1.json").takeIf { it.isFile } ?: File(dir, "test-vectors/courier-checks-v1.json")
      if (f.isFile) return@run Json.parseToJsonElement(f.readText()).jsonObject
      dir = dir.parentFile
    }
    error("core/test-vectors/courier-checks-v1.json پیدا نشد")
  }

  private fun point(e: JsonElement?): CourierChecks.Point? =
    if (e == null || e is JsonNull) null else (e as JsonArray).let { CourierChecks.Point(it[0].jsonPrimitive.double, it[1].jsonPrimitive.double) }

  private fun ints(e: JsonElement?): List<Int> = e?.jsonArray?.map { it.jsonPrimitive.int } ?: emptyList()

  @Test
  fun versionIsOne() = assertEquals(1, vec.getValue("version").jsonPrimitive.int)

  @Test
  fun bags() {
    val cs = vec.getValue("bags").jsonArray.map { it.jsonObject }
    assertTrue(cs.size >= 8)
    for (c in cs) {
      val name = c.getValue("name").jsonPrimitive.content
      val w = c.getValue("want").jsonObject
      val got = CourierChecks.bagsCheck(
        c.getValue("labels").jsonArray.map { it.jsonPrimitive.content }, c.getValue("orderNo").jsonPrimitive.long, c.getValue("bags").jsonPrimitive.int,
      )
      assertEquals(w.getValue("ok").jsonPrimitive.boolean, got.ok, name)
      assertEquals(w["code"]?.jsonPrimitive?.content ?: "", got.code, name)
      assertEquals(ints(w["missing"]), got.missing, name)
      assertEquals(w["scanned"]?.jsonPrimitive?.int ?: 0, got.scanned, name)
    }
  }

  @Test
  fun distance() {
    val cs = vec.getValue("distance").jsonArray.map { it.jsonObject }
    assertTrue(cs.size >= 6)
    for (c in cs) assertEquals(c.getValue("m").jsonPrimitive.long, CourierChecks.distanceM(point(c["a"])!!, point(c["b"])!!), c.toString())
  }

  @Test
  fun nearPin() {
    for (c in vec.getValue("nearPin").jsonArray.map { it.jsonObject }) {
      val name = c.getValue("name").jsonPrimitive.content
      val w = c.getValue("want").jsonObject
      val got = CourierChecks.nearPin(point(c["position"]), point(c["pin"]), c.getValue("maxM").jsonPrimitive.int)
      assertEquals(w.getValue("ok").jsonPrimitive.boolean, got.ok, name)
      assertEquals(w["code"]?.jsonPrimitive?.content ?: "", got.code, name)
      assertEquals(w["checked"]?.jsonPrimitive?.boolean ?: false, got.checked, name)
      assertEquals(w["distanceM"]?.jsonPrimitive?.long, got.distanceM, name)
    }
  }

  @Test
  fun maxM() {
    for (c in vec.getValue("maxM").jsonArray.map { it.jsonObject }) {
      val raw = c.getValue("raw")
      //  فقط عددِ صحیحِ JSON عدد است؛ متن، درست/نادرست و اعشار مثلِ نبود (همان `Number.isSafeInteger` ِ سرور)
      val v = (raw as? JsonPrimitive)?.takeIf { !it.isString && it.content.none { ch -> ch == '.' } }?.longOrNull
      assertEquals(c.getValue("want").jsonPrimitive.int, CourierChecks.maxMOf(v), raw.toString())
    }
  }

  @Test
  fun scanOneKeepsEarlierBagsOnReject() {
    var bags = emptySet<Int>()
    var r = CourierChecks.scanOne(bags, "VLNB-12-2-3", 12, 3)
    assertEquals("", r.second); bags = r.first
    r = CourierChecks.scanOne(bags, "VLNB-13-1-3", 12, 3)
    assertEquals("bag_wrong_order", r.second); assertEquals(setOf(2), r.first)
    r = CourierChecks.scanOne(bags, "VLNB-12-1-2", 12, 3)
    assertEquals("label_mismatch", r.second)
    r = CourierChecks.scanOne(bags, "سلام", 12, 3)
    assertEquals("bad_label", r.second)
    r = CourierChecks.scanOne(bags, "VLNB-۱۲-۲-۳", 12, 3)
    assertEquals(setOf(2), r.first, "تکرار بی‌اثر")
    assertEquals(listOf(1, 3), CourierChecks.bagsCheck(r.first.map { "VLNB-12-$it-3" }, 12, 3).missing)
  }
}
