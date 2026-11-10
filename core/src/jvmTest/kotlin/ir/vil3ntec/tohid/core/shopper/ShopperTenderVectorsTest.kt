package ir.vil3ntec.tohid.core.shopper

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 *  CHK-14 — `core/test-vectors/cash-tender-v1.json`: «با کدام اسکناس…» ِ برنامهٔ خرید ([CheckoutRules.tenderOptions]) برای
 *  هر جمع همان گزینه‌های ویترین است؛ همان فایل را `server/test/cash-tender-vectors.test.js` روی `tenders` ِ `checkout.js`
 *  و قاعدهٔ سرور (`delivery-prefs.checkTender`) می‌دواند.
 */
class ShopperTenderVectorsTest {

  private val v: JsonObject = run {
    var dir: File? = File("").absoluteFile
    while (dir != null) {
      val f = File(dir, "core/test-vectors/cash-tender-v1.json")
      if (f.isFile) return@run Json.parseToJsonElement(f.readText()).jsonObject
      dir = dir.parentFile
    }
    error("core/test-vectors/cash-tender-v1.json پیدا نشد")
  }

  @Test fun appOptionsAreTheWebOptionsForEveryVector() {
    assertEquals(CheckoutRules.NOTE_MINOR, v.getValue("noteMinor").jsonPrimitive.long)
    val cases = v.getValue("cases").jsonArray.map { it.jsonObject }
    assertTrue(cases.size >= 15, "${cases.size}")
    for (c in cases) {
      val total = c.getValue("totalMinor").jsonPrimitive.long
      val want = c.getValue("options").jsonArray.map { it.jsonPrimitive.long }
      assertEquals(want, CheckoutRules.tenderOptions(total), "جمع $total")
    }
  }
}
