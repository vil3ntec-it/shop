package ir.vil3ntec.tohid.core.delivery

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
 *  S83: `CourierArea` در برابرِ بردارِ `core/test-vectors/courier-area-v1.json` — همان که
 *  `server/test/courier-area.test.js` با `lib/delivery-courier-area` می‌خواند. فایل‌خوانی فقط این‌جا (jvmTest).
 */
class CourierAreaVectorsTest {

  private val vec: JsonObject = run {
    var dir: File? = File(System.getProperty("user.dir")).absoluteFile
    while (dir != null) {
      val f = File(dir, "core/test-vectors/courier-area-v1.json").takeIf { it.isFile } ?: File(dir, "test-vectors/courier-area-v1.json")
      if (f.isFile) return@run Json.parseToJsonElement(f.readText()).jsonObject
      dir = dir.parentFile
    }
    error("core/test-vectors/courier-area-v1.json پیدا نشد")
  }

  private fun cases(k: String) = vec.getValue(k).jsonArray.map { it.jsonObject }

  private fun strings(e: Any?): List<String> = (e as JsonArray).map { it.jsonPrimitive.content }

  /** همان خواندنِ سرور: `zone.name` فقط اگر `zone` شیء و `name` متن باشد */
  private fun text(o: JsonObject?, key: String): String = (o?.get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()

  @Test
  fun versionIsOne() = assertEquals(1, vec.getValue("version").jsonPrimitive.int)

  @Test
  fun orderArea() {
    val cs = cases("orderArea")
    assertTrue(cs.size >= 5)
    for (c in cs) {
      val a = c.getValue("address").jsonObject
      val got = CourierArea.orderArea(text(a["zone"] as? JsonObject, "name"), text(a, "area"))
      assertEquals(c.getValue("want").jsonPrimitive.content, got, c.getValue("name").jsonPrimitive.content)
    }
  }

  @Test
  fun covers() {
    val cs = cases("covers")
    assertTrue(cs.size >= 8)
    for (c in cs) {
      val got = CourierArea.covers(strings(c["areas"]), c.getValue("area").jsonPrimitive.content)
      assertEquals(c.getValue("want").jsonPrimitive.boolean, got, c.getValue("name").jsonPrimitive.content)
    }
  }

  @Test
  fun cleanMatchesServerParse() {
    val cs = cases("parse")
    assertTrue(cs.size >= 3)
    for (c in cs) assertEquals(strings(c["want"]), CourierArea.clean(strings(c["in"])), c.getValue("name").jsonPrimitive.content)
  }

  /** آنچه سرور رد می‌کند (۴۰۰ `bad_areas`)، فرم هرگز نمی‌فرستد: نامِ خالی و بلند کنار می‌رود و فهرست ۳۰ تایی می‌ماند */
  @Test
  fun rejectedInputNeverLeavesTheForm() {
    for (c in cases("reject")) {
      val raw = c["in"] as? JsonArray ?: continue
      val names = raw.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
      val out = CourierArea.clean(names)
      val name = c.getValue("name").jsonPrimitive.content
      assertTrue(out.size <= CourierArea.MAX_AREAS, name)
      assertTrue(out.all { it.isNotBlank() && it.length <= CourierArea.MAX_LEN && CourierArea.key(it).isNotEmpty() }, name)
    }
  }
}
