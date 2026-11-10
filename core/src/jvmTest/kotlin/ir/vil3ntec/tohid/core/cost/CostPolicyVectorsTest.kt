package ir.vil3ntec.tohid.core.cost

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 *  RPT-03: `CostPolicy` در برابرِ بردارِ `core/test-vectors/cost-policy-v1.json` — همان که
 *  `server/test/cost-policy-vectors.test.js` با `COST` ِ `license/report-board.js` (گزارشِ تازهٔ وب) می‌خواند.
 *  فایل‌خوانی فقط این‌جا (jvmTest). `decided: null` = هنوز تصمیم نشده.
 */
class CostPolicyVectorsTest {

  private val vec: JsonObject = run {
    var dir: File? = File(System.getProperty("user.dir")).absoluteFile
    while (dir != null) {
      val f = File(dir, "core/test-vectors/cost-policy-v1.json").takeIf { it.isFile } ?: File(dir, "test-vectors/cost-policy-v1.json")
      if (f.isFile) return@run Json.parseToJsonElement(f.readText()).jsonObject
      dir = dir.parentFile
    }
    error("core/test-vectors/cost-policy-v1.json پیدا نشد")
  }

  private fun method(id: String): CostMethod = CostMethod.entries.first { it.id == id }
  private fun decided(e: JsonElement?): CostMethod? = if (e == null || e is JsonNull) null else method(e.jsonPrimitive.content)
  private fun cases(k: String) = vec.getValue(k).jsonArray.map { it.jsonObject }

  @Test
  fun versionDefaultDecidedAndMethods() {
    assertEquals(1, vec.getValue("version").jsonPrimitive.int)
    assertEquals(vec.getValue("default").jsonPrimitive.content, CostPolicy.DEFAULT.id)
    assertEquals(vec.getValue("decided").jsonPrimitive.content, CostPolicy.DECIDED?.id)
    val want = cases("methods").map { Triple(it.getValue("id").jsonPrimitive.content, it.getValue("label").jsonPrimitive.content, it.getValue("note").jsonPrimitive.content) }
    assertEquals(want, CostMethod.entries.map { Triple(it.id, it.label, it.note) })
  }

  @Test
  fun parse() {
    val cs = cases("parse")
    assertTrue(cs.size >= 8)
    for (c in cs) {
      val raw = c.getValue("in").let { if (it is JsonNull) null else it.jsonPrimitive.content }
      assertEquals(c.getValue("out").jsonPrimitive.content, CostPolicy.parse(raw).id, c.toString())
    }
  }

  @Test
  fun approved() {
    for (c in cases("approved")) {
      assertEquals(c.getValue("out").jsonPrimitive.boolean, CostPolicy.approved(method(c.getValue("m").jsonPrimitive.content), decided(c["decided"])), c.toString())
    }
  }

  @Test
  fun labelAndExplain() {
    for (c in cases("label")) {
      val got = CostPolicy.label(c.getValue("base").jsonPrimitive.content, method(c.getValue("m").jsonPrimitive.content), decided(c["decided"]))
      assertEquals(c.getValue("out").jsonPrimitive.content, got, c.toString())
    }
    for (c in cases("explain")) {
      val got = CostPolicy.explain(method(c.getValue("m").jsonPrimitive.content), decided(c["decided"]))
      assertEquals(c.getValue("out").jsonPrimitive.content, got, c.toString())
    }
  }
}
