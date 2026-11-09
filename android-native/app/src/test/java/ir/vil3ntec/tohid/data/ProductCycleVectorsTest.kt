package ir.vil3ntec.tohid.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  MKT-06: بردارِ مرجعِ «چرخهٔ مصرف» (`license/test-vectors/product-cycle-v1.json`) — همان پاسخِ کادرِ نسخهٔ وب
 *  (`license/product-cycle.js`، `server/test/web-product-cycle.test.js`) برای هر ورودی.
 *
 *  cwd در CIِ اندروید `android-native/app` و در کامپیوتر `desktop` است؛ فایل با بالا رفتن از cwd پیدا می‌شود
 *  (همان `StockLedgerVectorsTest`).
 */
class ProductCycleVectorsTest {

  private val vectors: JsonObject by lazy {
    var dir: File? = File(".").absoluteFile
    var file: File? = null
    while (dir != null && file == null) {
      file = File(dir, "license/test-vectors/product-cycle-v1.json").takeIf { it.isFile }
      dir = dir.parentFile
    }
    assertNotNull("license/test-vectors/product-cycle-v1.json پیدا نشد (cwd=${File(".").absoluteFile})", file)
    Json.parseToJsonElement(file!!.readText()).jsonObject
  }

  @Test
  fun `کادر — همان پاسخِ نسخهٔ وب برای هر ورودیِ بردار`() {
    val cases = vectors.getValue("parse").jsonArray
    assertTrue("موردها: ${cases.size}", cases.size >= 30)
    for (c in cases) {
      val o = c.jsonObject
      val input = o.getValue("in").jsonPrimitive.content
      val out = o.getValue("out").jsonPrimitive
      val want: ProductCycle.Parsed = when {
        out.isString && out.content == "clear" -> ProductCycle.Clear
        out.isString && out.content == "bad" -> ProductCycle.Bad
        else -> ProductCycle.Days(out.int)
      }
      assertEquals(o.toString(), want, ProductCycle.parse(input))
    }
  }

  @Test
  fun `متنِ کادر از مقدارِ دفتر و سقف — همان نسخهٔ وب`() {
    for (c in vectors.getValue("text").jsonArray) {
      val o = c.jsonObject
      val raw = o.getValue("in")
      val days: Int? = if (raw is JsonNull) null else raw.jsonPrimitive.int
      assertEquals(o.toString(), o.getValue("out").jsonPrimitive.content, ProductCycle.text(days))
    }
    assertEquals(vectors.getValue("max").jsonPrimitive.int, ProductCycle.MAX)
  }
}
