package ir.vil3ntec.tohid.core.shopper

import kotlinx.serialization.json.Json
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
 *  CHK-08/10 — گردِ شش‌رقمیِ پین در برنامهٔ خرید ([AddressRules.round6]، و از آن [PinText.parse]) همان `round6` ِ سرور
 *  (`lib/storefront-delivery.js`) و `orders.coord` است: بخشِ `round6` ِ `core/test-vectors/delivery-zone-v1.json`، همان که
 *  `server/test/delivery-zone-v1.test.js` می‌خواند. پیش‌تر نیمه زوج می‌شد (34.5000005 ⇒ 34.5، سرور 34.500001).
 */
class AddressRoundVectorsTest {

  private val v: JsonObject = run {
    var dir: File? = File("").absoluteFile
    while (dir != null) {
      val f = File(dir, "core/test-vectors/delivery-zone-v1.json")
      if (f.isFile) return@run Json.parseToJsonElement(f.readText()).jsonObject
      dir = dir.parentFile
    }
    error("core/test-vectors/delivery-zone-v1.json پیدا نشد")
  }

  @Test
  fun roundSixMatchesTheServerHalfUp() {
    assertEquals(1, v.getValue("version").jsonPrimitive.int)
    val cases = v.getValue("round6").jsonArray.map { it.jsonObject }
    assertTrue(cases.size >= 8)
    for (c in cases) {
      val raw = c.getValue("raw").jsonPrimitive.double
      assertEquals(c.getValue("want").jsonPrimitive.double, AddressRules.round6(raw), "round6($raw)")
    }
  }

  @Test
  fun pastedCoordinatesGetTheSamePinAsTheServer() {
    for (c in v.getValue("round6").jsonArray.map { it.jsonObject }) {
      val raw = c.getValue("raw").jsonPrimitive.double
      if (raw < -90 || raw > 90 || kotlin.math.abs(raw) < 1e-3) continue
      val want = c.getValue("want").jsonPrimitive.double
      val p = PinText.parse("geo:$raw,69.1")
      assertEquals(want, p?.lat, "geo:$raw")
    }
  }
}
