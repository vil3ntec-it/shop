package ir.vil3ntec.tohid.core.promo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
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
 *  PRM-13 / STF-03 — `Loyalty.Rule.valid`، `cap`، `valueMinor` و `spend` در برابرِ `core/test-vectors/loyalty-spend-v1.json`؛ همان
 *  فایل را `server/test/loyalty-spend.test.js` برای `server/src/lib/loyalty-balance.js` می‌خواند. پس مصرفِ امتیازِ صندوق و
 *  بررسیِ سرور (`lib/promo-loyalty-review.js`) یک قاعده‌اند.
 */
class LoyaltySpendVectorsTest {

  private val v: JsonObject = run {
    var dir: File? = File("").absoluteFile
    while (dir != null) {
      val f = File(dir, "core/test-vectors/loyalty-spend-v1.json")
      if (f.isFile) return@run Json.parseToJsonElement(f.readText()).jsonObject
      dir = dir.parentFile
    }
    error("core/test-vectors/loyalty-spend-v1.json پیدا نشد")
  }

  private fun JsonObject.l(k: String): Long = getValue(k).jsonPrimitive.long
  private fun rows(k: String): List<JsonObject> = v.getValue(k).jsonArray.map { it.jsonObject }

  private fun rule(o: JsonObject): Loyalty.Rule {
    val r = o.getValue("rule").jsonObject
    return Loyalty.Rule(
      everyAfn = r.l("everyAfn"), pointAfn = r.l("pointAfn"), maxSharePct = r.getValue("maxSharePct").jsonPrimitive.int,
      silverAfn = r.l("silverAfn"), goldAfn = r.l("goldAfn"), expiryMonths = r.getValue("expiryMonths").jsonPrimitive.int,
    )
  }

  @Test
  fun version() {
    assertEquals(1L, v.l("version"))
  }

  @Test
  fun valid() {
    val cases = rows("valid")
    assertTrue(cases.isNotEmpty())
    for (c in cases) assertEquals(c.getValue("out").jsonPrimitive.boolean, rule(c).valid, c.toString())
  }

  @Test
  fun cap() {
    for (c in rows("cap")) assertEquals(c.l("out"), Loyalty.cap(rule(c), c.l("balance"), c.l("invoiceMinor")), c.toString())
  }

  @Test
  fun valueMinor() {
    for (c in rows("valueMinor")) assertEquals(c.l("out"), Loyalty.valueMinor(rule(c), c.l("points")), c.toString())
  }

  @Test
  fun spend() {
    for (c in rows("spend")) {
      val out = c.getValue("out").jsonObject
      val want: Loyalty.Spend = if (out.containsKey("code")) Loyalty.Spend.Bad(out.getValue("code").jsonPrimitive.content)
      else Loyalty.Spend.Ok(out.l("points"), out.l("valueMinor"))
      assertEquals(want, Loyalty.spend(rule(c), c.l("balance"), c.l("invoiceMinor"), c.l("points")), c.toString())
    }
  }
}
