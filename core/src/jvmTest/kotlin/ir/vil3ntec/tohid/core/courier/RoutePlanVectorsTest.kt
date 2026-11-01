package ir.vil3ntec.tohid.core.courier

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 *  DLV-04: `RoutePlan` در برابرِ بردارِ `core/test-vectors/courier-route-v1.json` — همان که
 *  `server/test/courier-route.test.js` با `lib/courier-route.plan` می‌خواند. فایل‌خوانی فقط این‌جا (jvmTest).
 */
class RoutePlanVectorsTest {

  private val vec: JsonObject = run {
    var dir: File? = File(System.getProperty("user.dir")).absoluteFile
    while (dir != null) {
      val f = File(dir, "core/test-vectors/courier-route-v1.json").takeIf { it.isFile } ?: File(dir, "test-vectors/courier-route-v1.json")
      if (f.isFile) return@run Json.parseToJsonElement(f.readText()).jsonObject
      dir = dir.parentFile
    }
    error("core/test-vectors/courier-route-v1.json پیدا نشد")
  }

  private fun point(e: JsonElement?): CourierChecks.Point? =
    if (e == null || e is JsonNull) null else (e as JsonArray).let { CourierChecks.Point(it[0].jsonPrimitive.double, it[1].jsonPrimitive.double) }

  @Test
  fun versionIsOne() = assertEquals(1, vec.getValue("version").jsonPrimitive.int)

  @Test
  fun plans() {
    val cs = vec.getValue("plan").jsonArray.map { it.jsonObject }
    assertTrue(cs.size >= 8)
    for (c in cs) {
      val name = c.getValue("name").jsonPrimitive.content
      val w = c.getValue("want").jsonObject
      val got = RoutePlan.plan(point(c["start"]), c.getValue("pins").jsonArray.map { point(it) })
      assertEquals(w.getValue("order").jsonArray.map { it.jsonPrimitive.int }, got.order, name)
      assertEquals(w.getValue("legsM").jsonArray.map { it.jsonPrimitive.long }, got.legsM, name)
      assertEquals(w.getValue("totalM").jsonPrimitive.long, got.totalM, name)
      assertEquals(w.getValue("unpinned").jsonArray.map { it.jsonPrimitive.int }, got.unpinned, name)
    }
  }
}
