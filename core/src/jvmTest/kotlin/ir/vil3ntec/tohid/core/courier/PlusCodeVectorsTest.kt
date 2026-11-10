package ir.vil3ntec.tohid.core.courier

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 *  S33: `PlusCode` در برابرِ بردارِ مرجعِ `core/test-vectors/plus-code-v1.json` — همان که `server/test/plus-code.test.js`
 *  با `server/public/store/plus-code.js` (ویترین و سرور) می‌خواند؛ پس کدِ سفارش، ویترین و کارتِ پیک یکی است.
 *  مختصاتِ نامعتبر در بردار رشته است ("NaN"، "Infinity") ⇒ `null`. فایل‌خوانی فقط این‌جا (jvmTest).
 */
class PlusCodeVectorsTest {

  private val vec: JsonObject = run {
    var dir: File? = File(System.getProperty("user.dir")).absoluteFile
    while (dir != null) {
      val f = File(dir, "core/test-vectors/plus-code-v1.json").takeIf { it.isFile } ?: File(dir, "test-vectors/plus-code-v1.json")
      if (f.isFile) return@run Json.parseToJsonElement(f.readText()).jsonObject
      dir = dir.parentFile
    }
    error("core/test-vectors/plus-code-v1.json پیدا نشد")
  }

  @Test
  fun versionIsOne() = assertEquals(1, vec.getValue("version").jsonPrimitive.int)

  @Test
  fun everyCaseGivesTheSameCode() {
    val cases = vec.getValue("cases").jsonArray.map { it.jsonObject }
    assertTrue(cases.size >= 15)
    for (c in cases) {
      val lat = c.getValue("lat").jsonPrimitive.content.toDouble()
      val lng = c.getValue("lng").jsonPrimitive.content.toDouble()
      val want = c.getValue("code").let { if (it is JsonNull) null else it.jsonPrimitive.content }
      assertEquals(want, PlusCode.encode(lat, lng), c.toString())
    }
  }
}
