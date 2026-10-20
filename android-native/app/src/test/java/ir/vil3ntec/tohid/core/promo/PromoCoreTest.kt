package ir.vil3ntec.tohid.core.promo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File

/**
 *  بردارهای مشترکِ `core/test-vectors/promo-v1.json` روی `PromoCore` — همان فایلی
 *  که `server/test/promo-core.test.js` روی `license/promo-core.js` می‌دواند.
 *  ورودی همان درختِ JSON است (عدد ⇒ Double، مثلِ JS) و از `ruleOf`/`cartOf` می‌گذرد.
 */
class PromoCoreTest {

  private val v: JsonObject = run {
    var dir: File? = File(System.getProperty("user.dir")).absoluteFile
    while (dir != null) {
      val f = File(dir, "core/test-vectors/promo-v1.json")
      if (f.isFile) return@run Json.parseToJsonElement(f.readText()).jsonObject
      dir = dir.parentFile
    }
    error("core/test-vectors/promo-v1.json پیدا نشد")
  }

  private fun tree(e: JsonElement?): Any? = when (e) {
    null, JsonNull -> null
    is JsonObject -> LinkedHashMap<String, Any?>().also { m -> e.forEach { (k, x) -> m[k] = tree(x) } }
    is JsonArray -> e.map { tree(it) }
    is JsonPrimitive -> when {
      e.isString -> e.content
      e.booleanOrNull != null -> e.booleanOrNull
      else -> e.doubleOrNull ?: e.content
    }
  }

  private fun list(name: String) = v.getValue(name).jsonArray.map { tree(it) as Map<*, *> }
  private fun Map<*, *>.l(k: String): Long = (this[k] as Number).toLong()

  private fun resultOf(o: Map<*, *>) = PromoCore.Result(
    subtotalMinor = o.l("subtotalMinor"),
    discountMinor = o.l("discountMinor"),
    totalMinor = o.l("totalMinor"),
    lines = (o["lines"] as List<*>).map { it as Map<*, *>; PromoCore.LineOut(it["key"] as String, it.l("amountMinor"), it.l("discountMinor"), it.l("netMinor")) },
    applied = (o["applied"] as List<*>).map { it as Map<*, *>; PromoCore.Applied(it["id"] as String, it["name"] as String, it.l("discountMinor")) },
    codes = (o["codes"] as List<*>).map { it as Map<*, *>; PromoCore.CodeStatus(it["code"] as String, it["status"] as String) },
  )

  @Test fun constants() {
    assertEquals((tree(v.getValue("version")) as Number).toInt(), PromoCore.VERSION)
    assertEquals(tree(v.getValue("kinds")), PromoCore.KINDS)
    assertEquals(tree(v.getValue("stacks")), PromoCore.STACKS)
    assertEquals(tree(v.getValue("channels")), PromoCore.CHANNELS)
  }

  @Test fun normalizeCode() {
    for (c in list("normalizeCode")) assertEquals(c.toString(), c["out"], PromoCore.normalizeCode(c["in"] as String))
  }

  @Test fun isoDow() {
    for (c in list("isoDow")) assertEquals(c.toString(), (c["out"] as Number).toInt(), PromoCore.isoDow(c["date"] as String))
  }

  @Test fun window() {
    for (c in list("window")) {
      val at = (c["at"] as? Map<*, *>)?.let { PromoCore.At(it["date"] as String, (it["minute"] as Number).toLong()) }
      assertEquals(c.toString(), c["out"], PromoCore.inWindow(PromoCore.windowOf(c["window"]), at))
    }
  }

  @Test fun validate() {
    for (c in list("validate")) {
      val step = (c["step"] as? Number)?.toLong() ?: 100L
      assertEquals(c.toString(), c["out"], PromoCore.validate(PromoCore.ruleOf(c["rule"]), step))
    }
  }

  @Test fun evaluate() {
    for (c in list("evaluate")) {
      val cart = PromoCore.cartOf(c["cart"])
      val rules = (c["rules"] as List<*>).map { PromoCore.ruleOf(it) }
      if (c["error"] == true) {
        assertThrows(c["name"].toString(), ArithmeticException::class.java) { PromoCore.evaluate(cart, rules) }
        continue
      }
      assertEquals(c["name"].toString(), resultOf(c["out"] as Map<*, *>), PromoCore.evaluate(cart, rules))
    }
  }
}
