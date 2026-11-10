package ir.vil3ntec.tohid.core.pay

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 *  PAY-10: `FxTill` و تسویه با ارز (`Pay.settle`/`exactCash` با `SettleInput.fx`) در برابرِ
 *  `core/test-vectors/fx-till-v1.json` — همان که `server/test/fx-till.test.js` با `license/fx-till.js` و
 *  `license/pay-core.js` می‌خواند. خروجی به همان شکلِ JSON ِ JS برگردانده و برابر سنجیده می‌شود.
 *  مبلغِ اعشاری (۱۰٫۵ پول) در کاتلین ساختنی نیست (Long)؛ چنین ورودی‌ای این‌جا صفر است و همان `INVALID_AMOUNT` را می‌دهد.
 */
class FxTillVectorsTest {

  private val v: JsonObject = run {
    var dir: File? = File(System.getProperty("user.dir")).absoluteFile
    while (dir != null) {
      val f = File(dir, "core/test-vectors/fx-till-v1.json").takeIf { it.isFile } ?: File(dir, "test-vectors/fx-till-v1.json")
      if (f.isFile) return@run Json.parseToJsonElement(f.readText()).jsonObject
      dir = dir.parentFile
    }
    error("core/test-vectors/fx-till-v1.json پیدا نشد")
  }

  private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
  private fun JsonObject.lng(k: String): Long? = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.longOrNull
  private fun JsonObject.arr(k: String): List<JsonObject> = (this[k] as? JsonArray)?.map { it.jsonObject }.orEmpty()
  private fun JsonObject.obj(k: String): JsonObject = this[k]!!.jsonObject
  private fun cases(k: String): List<JsonObject> = v[k]!!.jsonArray.map { it.jsonObject }

  // ── به JSON ِ JS ───────────────────────────────────────
  private fun j(x: Any?): JsonElement = when (x) {
    null -> JsonNull
    is JsonElement -> x
    is String -> JsonPrimitive(x)
    is Number -> JsonPrimitive(x)
    is Boolean -> JsonPrimitive(x)
    is Map<*, *> -> JsonObject(x.entries.associate { (k, y) -> k.toString() to j(y) })
    is List<*> -> JsonArray(x.map { j(it) })
    else -> error("نوعِ ناشناخته: $x")
  }

  private fun <T> res(r: PayResult<T>, ok: (T) -> Map<String, Any?>): JsonElement = when (r) {
    is PayResult.Ok -> j(mapOf("ok" to true) + ok(r.value))
    is PayResult.Err -> j(
      linkedMapOf<String, Any?>("ok" to false, "error" to r.code).apply {
        r.index?.let { put("index", it) }
        r.field?.let { put("field", it) }
      }
    )
  }

  private fun recordJson(x: ExchangeRecord) = mapOf(
    "from" to mapOf("currency" to x.fromCurrency, "amount" to x.fromAmount),
    "to" to mapOf("currency" to x.toCurrency, "amount" to x.toAmount),
    "rate" to x.rate, "per" to x.per, "source" to x.source, "at" to x.at, "approvedBy" to x.approvedBy,
  )

  private fun recordOf(o: JsonObject): ExchangeRecord {
    val from = o.obj("from")
    val to = o.obj("to")
    return ExchangeRecord(
      fromCurrency = from.str("currency")!!, fromAmount = from.lng("amount")!!, toCurrency = to.str("currency")!!,
      toAmount = to.lng("amount")!!, rate = o.lng("rate")!!, source = o.str("source")!!, at = o.lng("at")!!,
      approvedBy = o.str("approvedBy")!!, per = o.lng("per") ?: 1,
    )
  }

  private fun rateInputs(list: List<JsonObject>) = list.map {
    FxRateInput(it.str("currency"), it.lng("rate"), it.lng("per"), it.str("source"), it.lng("at"), it.str("by"))
  }

  private fun settleInput(o: JsonObject) = SettleInput(
    total = o.lng("total")!!,
    tenders = o.arr("tenders").map { Tender(it.str("method")!!, it.lng("amount")!!, it.str("currency"), it.str("ref")) },
    cashRounding = o.lng("cashRounding") ?: 0,
    customerId = o.str("customerId"),
    fx = o.arr("fx").map(::recordOf),
  )

  @Test fun constants() {
    val c = v["constants"]!!.jsonObject
    assertEquals(FxTill.BASE, c.str("base"))
    assertEquals(FxTill.MAX_RATES.toLong(), c.lng("maxRates"))
    assertEquals(FxTill.MAX_TEXT.toLong(), c.lng("maxText"))
    assertEquals(FxTill.MAX_AGE_MS, c.lng("maxAgeMs"))
    assertEquals(FxTill.SKEW_MS, c.lng("skewMs"))
    assertEquals(Pay.FX_PER, c["per"]!!.jsonArray.map { it.jsonPrimitive.longOrNull })
    assertEquals(FxTill.DEFAULT_PER, c.obj("defaultPer").mapValues { it.value.jsonPrimitive.longOrNull })
  }

  @Test fun rates() {
    for (c in cases("rates")) {
      val got = res(FxTill.rates(rateInputs(c["input"]!!.jsonArray.map { it.jsonObject }))) { list ->
        mapOf("rates" to list.map { mapOf("currency" to it.currency, "rate" to it.rate, "per" to it.per, "source" to it.source, "at" to it.at, "by" to it.by) })
      }
      assertEquals(c["expect"], got, c.str("name"))
    }
  }

  @Test fun tender() {
    for (c in cases("tender")) {
      val table = (FxTill.rates(rateInputs(c.arr("rates"))) as PayResult.Ok).value
      val o = c.obj("input")
      val got = res(FxTill.tender(table, o.str("currency")!!, o.lng("amount") ?: 0, o.lng("now")!!, o.lng("maxAgeMs") ?: FxTill.MAX_AGE_MS)) {
        mapOf("record" to recordJson(it))
      }
      assertEquals(c["expect"], got, c.str("name"))
    }
  }

  @Test fun settle() {
    for (c in cases("settle")) {
      val got = res(Pay.settle(settleInput(c.obj("input")))) { s ->
        mapOf(
          "currency" to s.currency, "status" to s.status, "total" to s.total, "rounding" to s.rounding, "due" to s.due,
          "tendered" to s.tendered, "change" to s.change, "remaining" to s.remaining, "toAccount" to s.toAccount,
          "payments" to s.payments.map { p ->
            linkedMapOf<String, Any?>(
              "method" to p.method, "kind" to p.kind, "amount" to p.amount, "ref" to p.ref, "status" to p.status, "verification" to p.verification,
            ).apply { p.fx?.let { put("fx", recordJson(it)) } }
          },
        )
      }
      assertEquals(c["expect"], got, c.str("name"))
    }
  }

  @Test fun exactCash() {
    for (c in cases("exactCash")) {
      val got = res(Pay.exactCash(settleInput(c.obj("input")))) { mapOf("amount" to it) }
      assertEquals(c["expect"], got, c.str("name"))
    }
  }

  @Test fun text() {
    for (row in v["parseAmount"]!!.jsonArray.map { it.jsonArray }) {
      val text = row[0].jsonPrimitive.content
      val cur = row[1].jsonPrimitive.content
      assertEquals((row[2] as? JsonPrimitive)?.longOrNull, FxTill.parseAmount(text, cur), "parseAmount($text, $cur)")
    }
    for (row in v["format"]!!.jsonArray.map { it.jsonArray }) {
      assertEquals(row[2].jsonPrimitive.content, FxTill.format(row[0].jsonPrimitive.longOrNull!!, row[1].jsonPrimitive.content), "format $row")
    }
    for (row in v["rateLine"]!!.jsonArray.map { it.jsonArray }) {
      val got = FxTill.rateLine(row[0].jsonPrimitive.longOrNull!!, row[1].jsonPrimitive.longOrNull!!, row[2].jsonPrimitive.content)
      assertEquals(row[3].jsonPrimitive.content, got, "rateLine $row")
    }
  }

  /** نرخِ تازه رکوردِ پیشین را عوض نمی‌کند: رکورد همان می‌ماند و تسویهٔ دوباره‌اش همان عدد را می‌دهد */
  @Test fun recordIsFrozen() {
    val at = 1_760_000_000_000L
    val old = (FxTill.rates(listOf(FxRateInput("USD", 7025, 1, "x", at, "u"))) as PayResult.Ok).value
    val rec = (FxTill.tender(old, "USD", 1000, at + 1) as PayResult.Ok).value
    val fresh = (FxTill.rates(listOf(FxRateInput("USD", 7400, 1, "x", at + 2, "u"))) as PayResult.Ok).value
    assertEquals(74000L, (FxTill.tender(fresh, "USD", 1000, at + 3) as PayResult.Ok).value.toAmount)
    val s = (Pay.settle(SettleInput(total = 50000, fx = listOf(rec))) as PayResult.Ok).value
    assertEquals(70300L, s.payments.single().fx!!.toAmount)
    assertEquals(20300L, s.change)
  }
}
