package ir.vil3ntec.tohid.core.shopper

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 *  CHK-08 — [PinText.parse] (پین از پیوند یا مختصاتِ برنامهٔ نقشه، بی شبکه) در برابرِ `core/test-vectors/pin-text-v1.json`،
 *  همان که `server/test/store-pin-text.test.js` با `server/public/store/pin-text.js` ِ ویترینِ وب می‌خواند: هر دو همان
 *  پین را از همان نوشته می‌سازند و همان گردِ شش‌رقمیِ سرور را دارند.
 */
class PinTextVectorsTest {

  private val v: JsonObject = run {
    var dir: File? = File("").absoluteFile
    while (dir != null) {
      val f = File(dir, "core/test-vectors/pin-text-v1.json")
      if (f.isFile) return@run Json.parseToJsonElement(f.readText()).jsonObject
      dir = dir.parentFile
    }
    error("core/test-vectors/pin-text-v1.json پیدا نشد")
  }

  @Test
  fun parseMatchesTheWebStorefrontVector() {
    assertEquals(1, v.getValue("version").jsonPrimitive.int)
    val cases = v.getValue("parse").jsonArray.map { it.jsonObject }
    assertTrue(cases.size >= 20)
    for (c in cases) {
      val want = c.getValue("want").let { w -> if (w is JsonNull) null else w.jsonArray.map { it.jsonPrimitive.double } }
      val got = PinText.parse(c.getValue("text").jsonPrimitive.content)?.let { listOf(it.lat, it.lng) }
      assertEquals(want, got, c.getValue("name").jsonPrimitive.content)
    }
  }
}
