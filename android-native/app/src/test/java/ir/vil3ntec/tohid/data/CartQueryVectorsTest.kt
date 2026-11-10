package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.ui.components.pos.PosSearch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  POS-03: بردارِ مرجعِ پیشوندِ شمار (`license/test-vectors/cart-query-v1.json`) — همان پاسخِ کادرِ صندوقِ نسخهٔ وب
 *  (`license/cart-query.js`، `server/test/web-cart-query.test.js`) برای هر ورودی، از جمله فاصله و پایانِ خطِ JVM؛ و بخشِ
 *  `fullCode`: متنی که خودش بارکدِ کامل است («12x34») بر پیشوند پیش است (`PosSearch.parse` ⇐ همان `decide` ِ وب).
 *
 *  cwd در CIِ اندروید `android-native/app` و در کامپیوتر `desktop` است؛ فایل با بالا رفتن از cwd پیدا می‌شود
 *  (همان `ProductCycleVectorsTest`).
 */
class CartQueryVectorsTest {

  private val vectors: JsonObject by lazy {
    var dir: File? = File(".").absoluteFile
    var file: File? = null
    while (dir != null && file == null) {
      file = File(dir, "license/test-vectors/cart-query-v1.json").takeIf { it.isFile }
      dir = dir.parentFile
    }
    assertNotNull("license/test-vectors/cart-query-v1.json پیدا نشد (cwd=${File(".").absoluteFile})", file)
    Json.parseToJsonElement(file!!.readText()).jsonObject
  }
  private val cases: JsonArray by lazy { vectors.getValue("parse").jsonArray }

  @Test
  fun `پیشوندِ شمار — همان پاسخِ نسخهٔ وب برای هر ورودیِ بردار`() {
    assertTrue("موردها: ${cases.size}", cases.size >= 40)
    for (c in cases) {
      val o = c.jsonObject
      val got = CartQuery.parse(o.getValue("in").jsonPrimitive.content)
      assertEquals(o.toString(), o.getValue("count").jsonPrimitive.double, got.count, 0.0)
      assertEquals(o.toString(), o.getValue("term").jsonPrimitive.content, got.term)
    }
  }

  @Test
  fun `بارکدِ کامل بر پیشوند پیش است — همان تصمیمِ کادرِ وب برای هر ورودیِ fullCode`() {
    val full = vectors.getValue("fullCode").jsonArray
    assertTrue("موردها: ${full.size}", full.size >= 10)
    for (c in full) {
      val o = c.jsonObject
      //  همان `ShopStore.barcodeIndex`: بارکد ⇐ شناسهٔ کالا (رقمِ فارسی را خودِ `BarcodeMatch` یک‌شکل می‌کند)
      val index = o.getValue("codes").jsonArray.mapIndexed { i, code -> code.jsonPrimitive.content to "p$i" }.toMap()
      val got = PosSearch.parse(o.getValue("in").jsonPrimitive.content, index)
      assertEquals(o.toString(), o.getValue("count").jsonPrimitive.double, got.count, 0.0)
      assertEquals(o.toString(), o.getValue("term").jsonPrimitive.content, got.term)
    }
  }
}
