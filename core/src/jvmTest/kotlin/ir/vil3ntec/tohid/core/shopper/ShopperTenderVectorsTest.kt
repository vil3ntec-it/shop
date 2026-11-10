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
 *  هر جمع همان گزینه‌های ویترین است؛ همان فایل را `server/test/cash-tender-vectors.test.js` روی `K.tenders` ِ `kit/order-kit.js`
 *  (ثبت و پیگیریِ وب) و قاعدهٔ سرور (`delivery-prefs.checkTender`) می‌دواند.
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

  /** CHK-24: «با کدام اسکناس…» پس از ثبت ([PrefsRules.tenders]) برای سفارشِ ارسال + نقد همان بردار است؛ هر گزینه را سرور می‌پذیرد */
  @Test fun afterPlacingOptionsAreTheSameVectorAndEachIsServerValid() {
    for (c in v.getValue("cases").jsonArray.map { it.jsonObject }) {
      val total = c.getValue("totalMinor").jsonPrimitive.long
      val want = c.getValue("options").jsonArray.map { it.jsonPrimitive.long }
      val o = ir.vil3ntec.tohid.core.market.PublicOrder(
        token = "t", orderNo = 1, status = "new", fulfillment = "delivery", paymentMethod = "cod", totalMinor = total, createdAt = 1, updatedAt = 1,
      )
      val p = CustomerDeliveryPrefs(editable = true, version = 1)
      assertEquals(want, PrefsRules.tenders(o, p), "جمع $total")
      for (x in want) assertTrue(PrefsRules.valid(x, total), "جمع $total: $x")
      //  اسکناسِ کنونیِ برابرِ یکی از گزینه‌ها ⇒ همان فهرست (بی تکرار)
      want.firstOrNull()?.let { assertEquals(want, PrefsRules.tenders(o, p.copy(cashTenderMinor = it)), "جمع $total") }
      if (total > 0) assertTrue(!PrefsRules.valid(total + CheckoutRules.NOTE_MINOR, total) && !PrefsRules.valid(total - 100, total))
    }
  }
}
