package ir.vil3ntec.tohid.core.pay

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 *  POS-40: `PayFix` در برابرِ بخشِ `payFix` ِ `core/test-vectors/pay-v1.json` — همان که
 *  `server/test/pay-core.test.js` با `license/pay-core.js` می‌خواند. فایل‌خوانی فقط این‌جا (jvmTest).
 */
class PayFixVectorsTest {

  private val vec: JsonObject = run {
    var dir: File? = File(System.getProperty("user.dir")).absoluteFile
    while (dir != null) {
      val f = File(dir, "core/test-vectors/pay-v1.json").takeIf { it.isFile } ?: File(dir, "test-vectors/pay-v1.json")
      if (f.isFile) return@run Json.parseToJsonElement(f.readText()).jsonObject["payFix"]!!.jsonObject
      dir = dir.parentFile
    }
    error("core/test-vectors/pay-v1.json پیدا نشد")
  }

  private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
  private fun JsonObject.lng(k: String): Long? = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.longOrNull
  private fun JsonObject.bool(k: String): Boolean = (this[k] as? JsonPrimitive)?.booleanOrNull == true
  private fun JsonObject.arr(k: String): List<JsonObject> = (this[k] as? JsonArray)?.map { it.jsonObject }.orEmpty()

  private fun row(o: JsonObject) = PayFix.Row(o.str("method")!!, o.lng("amount")!!)

  private fun fix(o: JsonObject) = PayFix.Fix(
    id = o.str("id")!!, saleId = o.str("saleId")!!, from = o.str("from")!!, to = o.str("to")!!,
    amount = o.lng("amount")!!, at = o.lng("at")!!, by = o.str("by")!!, approvedBy = o.str("approvedBy")!!,
    reason = o.str("reason")!!, ref = o.str("ref"),
  )

  private fun method(o: JsonObject) = PayMethod(
    id = o.str("id")!!, kind = o.str("kind")!!, name = o.str("name")!!, needsRef = o.bool("needsRef"),
    limit = o.lng("limit"), enabled = (o["enabled"] as? JsonPrimitive)?.booleanOrNull != false,
  )

  private fun input(o: JsonObject): PayFix.Input {
    val s = o["sale"]!!.jsonObject
    return PayFix.Input(
      id = o.str("id")!!,
      sale = PayFix.SaleInfo(s.str("id")!!, s.arr("rows").map(::row), s.bool("cancelled"), s.bool("online"), s.bool("returned")),
      from = o.str("from")!!, to = o.str("to")!!, amount = o.lng("amount")!!, at = o.lng("at")!!,
      by = o.str("by"), decision = o.str("decision")!!, approvedBy = o.str("approvedBy"), reason = o.str("reason"),
      ref = o.str("ref"), closed = o.bool("closed"), existing = o.arr("existing").map(::fix),
      methods = (o["methods"] as? JsonArray)?.map { method(it.jsonObject) },
    )
  }

  @Test
  fun check() {
    val cases = vec["check"]!!.jsonArray.map { it.jsonObject }
    assertTrue(cases.size >= 30)
    for (c in cases) {
      val name = c.str("name")!!
      val e = c["expect"]!!.jsonObject
      when (val r = PayFix.check(input(c["input"]!!.jsonObject))) {
        is PayResult.Ok -> {
          assertTrue(e["ok"]!!.jsonPrimitive.boolean, "$name ⇐ $r")
          assertEquals(e["duplicate"]!!.jsonPrimitive.boolean, r.duplicate, name)
          assertEquals(fix(e["fix"]!!.jsonObject), r.value, name)
        }
        is PayResult.Err -> {
          assertEquals(false, e["ok"]!!.jsonPrimitive.boolean, "$name ⇐ $r")
          assertEquals(e.str("error"), r.code, name)
          if ("field" in e) assertEquals(e.str("field"), r.field, "$name — field")
          if ("method" in e) assertEquals(e.str("method"), r.method, "$name — method")
        }
      }
    }
  }

  @Test
  fun apply() {
    for (c in vec["apply"]!!.jsonArray.map { it.jsonObject }) {
      val name = c.str("name")!!
      val rows = c.arr("rows").map(::row)
      val r = PayFix.apply(c.str("saleId")!!, rows, c.arr("fixes").map(::fix), c.lng("until") ?: Long.MAX_VALUE)
      val e = c["expect"]!!.jsonObject
      assertEquals(e.arr("rows").map(::row), r.rows, name)
      assertEquals(e["applied"]!!.jsonArray.map { it.jsonPrimitive.content }, r.applied, name)
      assertEquals(e["skipped"]!!.jsonArray.map { it.jsonPrimitive.content }, r.skipped, name)
      assertEquals(rows.sumOf { it.amount }, r.rows.sumOf { it.amount }, "$name — جمعِ فاکتور همان")
      assertTrue(r.rows.all { it.amount > 0 }, "$name — بی ردیفِ صفر")
    }
  }

  @Test
  fun closed() {
    for (c in vec["closed"]!!.jsonArray.map { it.jsonObject }) {
      val shifts = c.arr("shifts").map { PayFix.Window(it.lng("openedAt")!!, it.lng("closedAt"), it.str("day").orEmpty()) }
      assertEquals(c["expect"]!!.jsonPrimitive.boolean, PayFix.closed(c["saleAt"]!!.jsonPrimitive.long, c.str("saleDay"), shifts), c.str("name"))
    }
  }
}
