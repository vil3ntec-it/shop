package ir.vil3ntec.tohid.sync.v1

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 *  بازفرستِ opهای `hash_mismatch` ِ عددِ نماییِ ساخت‌های پیشین — DATA-SAFETY §۷، `DroppedResend`.
 *
 *  دفترِ ردشده با **بایت‌های واقعیِ ساختِ پیشین** (کالا، فاکتور و خریدِ `core/test-vectors/ledger-sync-v1.json` با
 *  `Double.toString` ِ جاوا: `4.0E-4`، `5.684341886080802E-14`، `1.23456785E7`، و اثرِ انگشتِ کاتلین روی همان) و ردهای
 *  دلیلِ دیگر. اثرِ انگشتِ تازه با **آینهٔ مستقلِ** `canonical`/`hashOf` ِ `server/src/lib/sync-v1.js` (عدد از راهِ
 *  `BigDecimal` و قاعدهٔ `Number#toString` ِ ECMAScript، نه `JsNumber`) و با `hash` ِ همان بردار (که آزمونِ سرور با خودِ
 *  `hashOf` سنجیده) سنجیده می‌شود. بردارِ `core/test-vectors/resend-dropped-v1.json` همان ورودی و خروجی را به
 *  `server/test/sync-resend-dropped.test.js` (push ِ واقعیِ v1) می‌دهد؛ `TOHID_WRITE_VECTORS=resend-dropped` از نو می‌نویسد.
 *  `book`/`ledger`/`snapshot`/`vectorNow` همان ورودی‌های `DroppedResendDesktopTest` (صفِ واقعیِ دیسک) هم هستند.
 */
class DroppedResendTest {

  private val coverage = Json.parseToJsonElement(locate("core/test-vectors/ledger-sync-v1.json").readText()).jsonObject
  private val vecOps = coverage.getValue("ops").jsonArray.map { it.jsonObject }
  private val outFile = File(locate("core/test-vectors/ledger-sync-v1.json").parentFile, "resend-dropped-v1.json")

  private fun locate(name: String): File {
    var dir: File? = File(System.getProperty("user.dir")).absoluteFile
    while (dir != null) {
      File(dir, name).takeIf { it.isFile }?.let { return it }
      dir = dir.parentFile
    }
    error("$name پیدا نشد")
  }

  /* ---------------- آینهٔ سرور (`canonical` و `hashOf` ِ lib/sync-v1.js) — بی `JsNumber` ---------------- */

  /** `JSON.stringify(JSON.parse(text))` برای یک عدد: کوتاه‌ترین رقم‌ها (`BigDecimal`) + قالبِ ECMAScript ۶.۱.۶.۱.۲۰ */
  private fun esNumber(text: String): String {
    val d = text.toDouble()
    if (d == 0.0) return "0"
    val exact = BigDecimal(d).abs()
    //  ECMAScript: کم‌ترین k که عددی از k رقم همان `Double` را بدهد؛ اگر دو تا، نزدیک‌تر به مقدارِ دقیق
    var s: BigDecimal? = null
    for (p in 1..17) {
      val ok = listOf(RoundingMode.FLOOR, RoundingMode.CEILING).map { exact.round(MathContext(p, it)) }.filter { it.toDouble() == Math.abs(d) }
      if (ok.isNotEmpty()) { s = ok.minByOrNull { (it - exact).abs() }!!.stripTrailingZeros(); break }
    }
    val digits = s!!.unscaledValue().toString()
    val k = digits.length
    val n = k - s.scale()
    val body = when {
      n in k..21 -> digits + "0".repeat(n - k)
      n in 1..21 -> digits.substring(0, n) + "." + digits.substring(n)
      n in -5..0 -> "0." + "0".repeat(-n) + digits
      else -> {
        val e = n - 1
        (if (k == 1) digits else digits[0] + "." + digits.substring(1)) + "e" + (if (e < 0) "-" else "+") + Math.abs(e)
      }
    }
    return if (d < 0) "-$body" else body
  }

  /** رشته به شکلِ `JSON.stringify` */
  private fun esString(s: String): String = buildString {
    append('"')
    for (ch in s) when {
      ch == '"' -> append("\\\"")
      ch == '\\' -> append("\\\\")
      ch == '\n' -> append("\\n")
      ch == '\r' -> append("\\r")
      ch == '\t' -> append("\\t")
      ch == '\b' -> append("\\b")
      ch == '\u000C' -> append("\\f")
      ch < ' ' -> append("\\u%04x".format(ch.code))
      else -> append(ch)
    }
    append('"')
  }

  private fun serverCanonical(e: JsonElement): String = when (e) {
    is JsonNull -> "null"
    is JsonObject -> e.keys.sorted().joinToString(",", "{", "}") { esString(it) + ":" + serverCanonical(e.getValue(it)) }
    is JsonArray -> e.joinToString(",", "[", "]") { serverCanonical(it) }
    is JsonPrimitive -> when {
      e.isString -> esString(e.content)
      e.content == "true" || e.content == "false" -> e.content
      else -> esNumber(e.content)
    }
  }

  private fun serverHash(o: JsonObject): String = OpHash.sha256Hex(
    "${o.getValue("table").jsonPrimitive.content}|${o.getValue("row_id").jsonPrimitive.content}|" +
      "${o.getValue("type").jsonPrimitive.content}|" + serverCanonical(o["fields"] ?: JsonObject(emptyMap())),
  )

  /* ---------------- ساختِ دفترِ ردشدهٔ واقعی ---------------- */

  /** همان که `LedgerDiff.toJson` ِ پیش از `JsNumber` می‌نوشت: `Double` ِ ناصحیح با `Double.toString`، صحیح بی اعشار */
  private fun javaForm(e: JsonElement): JsonElement = when (e) {
    is JsonObject -> JsonObject(e.mapValues { (_, v) -> javaForm(v) })
    is JsonArray -> JsonArray(e.map { javaForm(it) })
    is JsonNull -> e
    is JsonPrimitive -> {
      val d = if (e.isString) null else e.content.toDoubleOrNull()
      when {
        d == null -> e
        d == Math.rint(d) && Math.abs(d) < 9.007199254740992E15 -> JsonPrimitive(d.toLong())
        else -> JsonPrimitive(d)
      }
    }
  }

  private fun vecOp(rowId: String): JsonObject = vecOps.single { it.getValue("row_id").jsonPrimitive.content == rowId }
  private fun fieldsOf(rowId: String): JsonObject = vecOp(rowId).getValue("fields").jsonObject
  private fun edit(o: JsonObject, vararg kv: Pair<String, JsonElement>): JsonObject = JsonObject(o + kv)

  private fun entry(op: SyncOp, reason: String, at: Long = 1_762_500_100_000): JsonObject = buildJsonObject {
    put("op", op.toJson())
    put("reason", JsonPrimitive(reason))
    put("at", JsonPrimitive(at))
  }

  private fun inc(n: Number) = buildJsonObject { put("\$inc", JsonPrimitive(n)) }

  /** ردیفِ فروشِ نیمه: درجش رد شد، ویرایشِ بعدی پذیرفته شد (سرور: فقط همان دو فیلد) */
  private val sHalf: JsonObject = edit(fieldsOf("s-full"), "remaining" to JsonPrimitive(5.684341886080802E-14))

  /** دفترِ ردشدهٔ یک دستگاه: ۴ نامزد و ردهای دیگر */
  internal fun book(): List<JsonObject> = listOf(
    /* 0 */ entry(SyncOp("01JRSND00000000000000P0001", 1_762_500_000_001, "products", "p-full", "insert", javaForm(fieldsOf("p-full")) as JsonObject), "hash_mismatch"),
    /* 1 */ entry(SyncOp("01JRSND00000000000000S0001", 1_762_500_000_002, "sales", "s-full", "insert", javaForm(fieldsOf("s-full")) as JsonObject), "hash_mismatch"),
    /* 2 */ entry(SyncOp("01JRSND00000000000000U0001", 1_762_500_000_003, "purchases", "pu-full", "insert", javaForm(fieldsOf("pu-full")) as JsonObject), "hash_mismatch"),
    /* 3 */ entry(SyncOp("01JRSND00000000000000S0002", 1_762_500_000_004, "sales", "s-half", "insert", javaForm(sHalf) as JsonObject), "hash_mismatch"),
    /* 4 */ entry(SyncOp("01JRSND00000000000000E0001", 1_762_500_000_005, "products", "p-edit", "update", buildJsonObject { put("purchasePrice", JsonPrimitive(4.0E-4)) }), "hash_mismatch"),
    //  ردهای دلیلِ دیگر — حتی با عددِ نمایی دست نمی‌خورند
    /* 5 */ entry(SyncOp("01JRSND00000000000000X0001", 1_762_500_000_006, "sales", "s-full", "update", buildJsonObject { put("discountValue", JsonPrimitive(2.0E-4)) }), "approval_required"),
    /* 6 */ entry(SyncOp("01JRSND00000000000000X0002", 1_762_500_000_007, "products", "p-full", "update", buildJsonObject { put("unitFactor", JsonPrimitive(3.0E-4)) }), "sale_finalized_edit_forbidden"),
    /* 7 */ entry(SyncOp("01JRSND00000000000000X0003", 1_762_500_000_008, "purchases", "pu-full", "update", buildJsonObject { put("landedCost", JsonPrimitive(1.5E7)) }), "fields_too_large"),
    /* 8 */ entry(SyncOp("01JRSND00000000000000X0004", 1_762_500_000_009, "products", "p-gone", "delete", null), "delete_not_own"),
    /* 9 */ entry(SyncOp("01JRSND00000000000000X0005", 1_762_500_000_010, "products", "p-full", "update", buildJsonObject { put("salePrice", JsonPrimitive(1.0E-4)) }), "stale"),
    //  `hash_mismatch` ولی شکلِ عدد علت نیست (عددِ ساده) ⇒ نه
    /* 10 */ entry(SyncOp("01JRSND00000000000000H0001", 1_762_500_000_011, "products", "p-plain", "update", buildJsonObject { put("salePrice", JsonPrimitive(12.5)) }), "hash_mismatch"),
    //  `hash_mismatch` با اثرِ انگشتِ دست‌خورده (دفترِ ناسالم) ⇒ نه
    /* 11 */ entry(SyncOp("01JRSND00000000000000H0002", 1_762_500_000_012, "products", "p-bad", "update", buildJsonObject { put("salePrice", JsonPrimitive(4.0E-4)) }), "hash_mismatch")
      .let { e -> JsonObject(e + ("op" to JsonObject(e.getValue("op").jsonObject + ("hash" to JsonPrimitive("0".repeat(64)))))) },
    //  همان op ِ ۰ دو بار در دفتر ⇒ یک نامزد
    /* 12 */ entry(SyncOp("01JRSND00000000000000P0001", 1_762_500_000_001, "products", "p-full", "insert", javaForm(fieldsOf("p-full")) as JsonObject), "hash_mismatch", 1_762_500_200_000),
  )

  private val candidateIds = listOf("01JRSND00000000000000P0001", "01JRSND00000000000000S0001", "01JRSND00000000000000U0001", "01JRSND00000000000000S0002", "01JRSND00000000000000E0001")

  /** opهایی که سرور پیش از به‌روزرسانی **پذیرفته بود**: درجِ کالای `p-edit` و ویرایشِ بعدیِ فروشِ نیمه */
  internal fun acceptedBefore(): List<SyncOp> = listOf(
    SyncOp("01JRSND00000000000000A0001", 1_762_499_000_000, "products", "p-edit", "insert", pEdit),
    SyncOp("01JRSND00000000000000A0002", 1_762_500_000_050, "sales", "s-half", "update", buildJsonObject { put("debtSettled", inc(25)); put("seller", JsonPrimitive("u-owner")) }),
  )

  /** همان `applyOp` ِ سرور (بی تعارض): `$inc` جمع، بقیه جایگزین */
  private fun apply(row: JsonObject?, op: SyncOp): JsonObject {
    val data = LinkedHashMap<String, JsonElement>(row ?: JsonObject(emptyMap()))
    for ((f, v) in op.fields ?: JsonObject(emptyMap())) {
      val d = (v as? JsonObject)?.get("\$inc")
      data[f] = if (d != null) JsNumber.of(((data[f] as? JsonPrimitive)?.content?.toDoubleOrNull() ?: 0.0) + d.jsonPrimitive.content.toDouble()) else v
    }
    return JsonObject(data)
  }

  /** درجِ پذیرفتهٔ کالای `p-edit` (پیش از ویرایشِ ردشدهٔ قیمت) */
  private val pEdit: JsonObject = edit(fieldsOf("p-full"), "purchasePrice" to JsonPrimitive(0.5), "name" to JsonPrimitive("کالای ویرایشی"),
    "unitFactor" to JsonPrimitive(1), "kit" to JsonArray(emptyList()))

  /** دفترِ امروزِ دستگاه (پس از Pull) — ردیف‌ها با همان شکلِ `LedgerDiff.toJson` */
  internal fun ledger(): JsonObject {
    fun row(id: String, body: JsonObject) = JsonObject(body + ("id" to JsonPrimitive(id)))
    val sHalfNow = edit(sHalf, "debtSettled" to JsonPrimitive(75), "seller" to JsonPrimitive("u-owner"))
    val pEditNow = LedgerDiff.jsNumbers(edit(pEdit, "purchasePrice" to JsonPrimitive(0.0004))) as JsonObject
    return buildJsonObject {
      put("products", JsonArray(listOf(row("p-full", fieldsOf("p-full")), row("p-edit", pEditNow))))
      put("sales", JsonArray(listOf(row("s-full", fieldsOf("s-full")), row("s-half", LedgerDiff.jsNumbers(sHalfNow) as JsonObject))))
      put("purchases", JsonArray(listOf(row("pu-full", fieldsOf("pu-full")))))
    }
  }

  /** Snapshot ِ سرور همان دم: فقط آنچه پذیرفته شده بود */
  internal fun snapshot(extra: Map<String, JsonObject> = emptyMap()): JsonObject {
    val rows = LinkedHashMap<String, JsonObject>()
    for (op in acceptedBefore()) rows["${op.table}/${op.rowId}"] = apply(rows["${op.table}/${op.rowId}"], op)
    rows.putAll(extra)
    val tables = rows.entries.groupBy({ it.key.substringBefore('/') }) { (k, data) ->
      buildJsonObject { put("id", JsonPrimitive(k.substringAfter('/'))); put("data", data) }
    }
    return buildJsonObject {
      put("cursor", JsonPrimitive(2))
      put("tables", JsonObject(tables.mapValues { JsonArray(it.value) }))
    }
  }

  private fun plan(book: List<JsonObject>, snap: JsonObject = snapshot(), local: JsonObject = ledger(), gone: Set<RestoreMerge.Row> = emptySet(), queued: List<SyncOp> = emptyList(), archived: Map<String, Set<String>> = emptyMap()) =
    DroppedResend.plan(DroppedResend.candidates(book, emptySet()), book, queued, local, archived, snap, gone)

  /* ---------------- آزمون‌ها ---------------- */

  @Test fun `فقط hash_mismatch ِ عددی نامزد است و همان op با عددِ جاوااسکریپت و اثرِ انگشتِ سرور`() {
    val book = book()
    val cands = DroppedResend.candidates(book, emptySet())
    assertEquals(candidateIds, cands.map { it.op.opId })
    assertEquals(listOf(0, 1, 2, 3, 4), cands.map { it.at })
    for (c in cands) {
      val stored = book[c.at].getValue("op").jsonObject
      //  سرخ (همان op ِ نوشته‌شده): سرور همان بایت‌ها را می‌خواند و اثرِ انگشتِ دیگری می‌سازد ⇒ hash_mismatch
      assertNotEquals(c.op.opId, stored.getValue("hash").jsonPrimitive.content, serverHash(stored))
      //  سبز: همان op_id/ts/جدول/ردیف/گونه؛ اثرِ انگشتِ تازه = آینهٔ سرور
      val wire = c.op.toJson()
      assertEquals(c.op.opId, serverHash(wire), wire.getValue("hash").jsonPrimitive.content)
      for (k in listOf("op_id", "ts", "table", "row_id", "type")) assertEquals(k, stored[k], wire[k])
      assertTrue(c.op.opId, !Regex("\\dE-?\\d").containsMatchIn(OpHash.canonical(c.op.fields!!)))
    }
    //  بایت‌به‌بایت همان ops ِ بردارِ پوشش (اثرِ انگشتی که آزمونِ سرور با خودِ `hashOf` سنجیده)
    for ((id, row) in listOf("01JRSND00000000000000P0001" to "p-full", "01JRSND00000000000000S0001" to "s-full", "01JRSND00000000000000U0001" to "pu-full")) {
      val op = cands.single { it.op.opId == id }.op
      assertEquals(row, OpHash.canonical(fieldsOf(row)), OpHash.canonical(op.fields!!))
      assertEquals(row, vecOp(row).getValue("hash").jsonPrimitive.content, op.hash)
    }
    assertEquals("0.0004", cands.single { it.op.rowId == "p-edit" }.op.fields!!.getValue("purchasePrice").jsonPrimitive.content)
    //  دفترچه پیش‌تر تصمیم گرفته ⇒ هیچ (یک بار برای هر op)
    assertEquals(emptyList<DroppedResend.Candidate>(), DroppedResend.candidates(book, candidateIds.toSet()))
  }

  @Test fun `کوتاه‌ترین رقم‌ها — همان که جاوااسکریپت می‌نویسد`() {
    for (t in listOf("0.0004", "5.684341886080802e-14", "12345678.5", "1e+21", "0.1", "0", "-0.0005", "9.689999999999998", "120")) {
      assertTrue(t, DroppedResend.jsStable(t))
      assertEquals(t, esNumber(t), t)
    }
    //  رقمِ زیادیِ `Double.toString` ِ زمان‌اجرای کهنه ⇒ اثرِ انگشت باز ناجور ⇒ نامزد نیست
    for (t in listOf("0.10000000000000001", "282879384806159008", "4.0E-4", "0.30000000000000000")) assertTrue(t, !DroppedResend.jsStable(t))
  }

  @Test fun `سرور ردیف را ندارد ⇒ همان opها همان‌طور؛ ردیفِ نیمه و ویرایشِ کالای موجود ⇒ فقط آنچه سرور ندارد`() {
    val decisions = plan(book())
    assertEquals(candidateIds, decisions.map { it.opId })
    val by = decisions.associateBy { it.opId }
    val cands = DroppedResend.candidates(book(), emptySet()).associateBy { it.op.opId }
    //  کالا، فاکتور و خرید که هرگز نرسیدند: همان op، همان فیلدها
    for (id in candidateIds.take(3)) {
      assertEquals(id, "replay", by.getValue(id).why)
      assertEquals(id, cands.getValue(id).op.toJson(), by.getValue(id).send!!.toJson())
    }
    //  فروشِ نیمه: سرور { debtSettled: 25, seller } دارد ⇒ ویرایش، شمارنده‌ها سهمِ همین درج، seller ِ تازه‌تر دست نمی‌خورد
    val half = by.getValue("01JRSND00000000000000S0002").send!!
    assertEquals(SyncOp.UPDATE, half.type)
    val f = half.fields!!
    assertTrue("seller ِ تازه‌تر بازنویسی نمی‌شود", "seller" !in f)
    assertEquals(inc(50), f["debtSettled"])
    assertEquals(inc(130), f["paidAmount"])
    assertEquals(inc(100), f["debtGiven"])
    assertEquals("5.684341886080802e-14", f.getValue("remaining").jsonObject.getValue("\$inc").jsonPrimitive.content)
    //  پس از همین op ردیفِ سرور = دفترِ دستگاه، فیلدبه‌فیلد
    val server = snapshot().getValue("tables").jsonObject.getValue("sales").jsonArray.single().jsonObject.getValue("data").jsonObject
    val want = ledger().getValue("sales").jsonArray.map { it.jsonObject }.single { it["id"]!!.jsonPrimitive.content == "s-half" }
    assertEquals(OpHash.canonical(JsonObject(want - "id")), OpHash.canonical(apply(server, half)))
    //  ویرایشِ کالای موجود: فقط همان قیمت
    val edit = by.getValue("01JRSND00000000000000E0001").send!!
    assertEquals(SyncOp.UPDATE, edit.type)
    assertEquals("""{"purchasePrice":0.0004}""", OpHash.canonical(edit.fields!!))
  }

  @Test fun `درجِ دوباره و ویرایشِ دستگاهِ دیگر ⇒ هیچ بازنویسی؛ حذف ⇒ هیچ زنده کردن`() {
    val book = book()
    //  سرور ردیف‌ها را با درجِ دوباره (`prepare`) دارد و دستگاهِ دیگر نامِ کالا را عوض کرده؛ این دستگاه Pull کرده
    val local = ledger().let { l ->
      val products = l.getValue("products").jsonArray.map { r ->
        val o = r.jsonObject
        if (o["id"]!!.jsonPrimitive.content == "p-full") edit(o, "name" to JsonPrimitive("نامِ دستگاهِ دیگر")) else o
      }
      JsonObject(l + ("products" to JsonArray(products)))
    }
    val full = mapOf(
      "products/p-full" to edit(fieldsOf("p-full"), "name" to JsonPrimitive("نامِ دستگاهِ دیگر")),
      "sales/s-full" to fieldsOf("s-full"),
      "purchases/pu-full" to fieldsOf("pu-full"),
    )
    val by = plan(book, snap = snapshot(full), local = local).associateBy { it.opId }
    for (id in candidateIds.take(3)) {
      assertNull(id, by.getValue(id).send)
      assertEquals(id, "nothing_left", by.getValue(id).why)
    }
    //  حذف‌شده روی سرور ⇒ نه؛ پاک‌شده در همین دفتر ⇒ نه؛ بایگانی‌شده و نبودِ سرور ⇒ همان op
    val gone = plan(book, gone = setOf(RestoreMerge.Row("products", "p-full"))).associateBy { it.opId }
    assertEquals("deleted_on_server", gone.getValue("01JRSND00000000000000P0001").why)
    val bare = JsonObject(ledger() + ("purchases" to JsonArray(emptyList())))
    assertEquals("deleted_here", plan(book, local = bare).single { it.opId == "01JRSND00000000000000U0001" }.why)
    val kept = plan(book, local = bare, archived = mapOf("purchases" to setOf("pu-full"))).single { it.opId == "01JRSND00000000000000U0001" }
    assertEquals("replay", kept.why)
  }

  @Test fun `ترتیب — ویرایشِ بعدی در صف یا ردشده با دلیلِ دیگر مقدارِ کهنه را برنمی‌گرداند`() {
    //  ردیفِ موجود روی سرور با قیمتِ ۰٫۵؛ پس از opِ ردشده یک ویرایشِ دیگرِ قیمت با دلیلِ دیگر رد شد ⇒ قیمت نمی‌رود
    val blocked = book() + entry(SyncOp("01JRSND00000000000000X0009", 1_762_500_000_099, "products", "p-edit", "update", buildJsonObject { put("purchasePrice", JsonPrimitive(0.7)) }), "approval_required")
    assertEquals("nothing_left", plan(blocked).single { it.opId == "01JRSND00000000000000E0001" }.why)
    //  دفتر پس از آن قیمت را عوض کرده (ویرایشِ در صف) ⇒ مقدارِ همین op کهنه است ⇒ نمی‌رود
    val newer = JsonObject(ledger() + ("products" to JsonArray(ledger().getValue("products").jsonArray.map { r ->
      val o = r.jsonObject
      if (o["id"]!!.jsonPrimitive.content == "p-edit") edit(o, "purchasePrice" to JsonPrimitive(0.9)) else o
    })))
    val queued = listOf(SyncOp("01JRSND00000000000000Q0001", 1_762_500_000_200, "products", "p-edit", "update", buildJsonObject { put("purchasePrice", JsonPrimitive(0.9)) }))
    assertEquals("nothing_left", plan(book(), local = newer, queued = queued).single { it.opId == "01JRSND00000000000000E0001" }.why)
    //  شمارنده: دلتای در صف هم حساب می‌شود (سرور ۲۵ + سهمِ درج ۵۰ + صف ۱۰ = دفتر ۸۵)
    val more = JsonObject(ledger() + ("sales" to JsonArray(ledger().getValue("sales").jsonArray.map { r ->
      val o = r.jsonObject
      if (o["id"]!!.jsonPrimitive.content == "s-half") edit(o, "debtSettled" to JsonPrimitive(85)) else o
    })))
    val q = listOf(SyncOp("01JRSND00000000000000Q0002", 1_762_500_000_300, "sales", "s-half", "update", buildJsonObject { put("debtSettled", inc(10)) }))
    assertEquals(inc(50), plan(book(), local = more, queued = q).single { it.opId == "01JRSND00000000000000S0002" }.send!!.fields!!["debtSettled"])
    //  نخواند (دلتای نامعلوم) ⇒ آن شمارنده نمی‌رود، بقیهٔ فیلدها می‌روند
    assertTrue("debtSettled" !in plan(book(), local = more).single { it.opId == "01JRSND00000000000000S0002" }.send!!.fields!!)
  }

  /* ---------------- بردارِ آزمونِ سرور ---------------- */

  internal fun vectorNow(): JsonObject {
    val book = book()
    val decisions = plan(book)
    val rows = LinkedHashMap<String, JsonElement>()
    for (key in listOf("products", "sales", "purchases")) for (r in ledger().getValue(key).jsonArray.map { it.jsonObject }) {
      val id = r.getValue("id").jsonPrimitive.content
      if (decisions.any { it.send?.rowId == id }) rows["$key/$id"] = JsonObject(r - "id")
    }
    return buildJsonObject {
      put("v", JsonPrimitive(1))
      put("about", JsonPrimitive("DATA-SAFETY §۷: دفترِ ردشدهٔ ساختِ پیشین (بایت‌های جاوا) و آنچه DroppedResend دوباره می‌فرستد — DroppedResendTest می‌نویسد (TOHID_WRITE_VECTORS=resend-dropped)، server/test/sync-resend-dropped.test.js به push ِ واقعی می‌دهد"))
      put("accepted", JsonArray(acceptedBefore().map { it.toJson() }))
      put("dropped", JsonArray(book))
      put("resend", JsonArray(decisions.mapNotNull { it.send?.toJson() }))
      put("rows", JsonObject(rows))
    }
  }

  @Test fun `بردارِ سرور همان خروجیِ امروزِ کد است`() {
    val now = vectorNow()
    if (System.getenv("TOHID_WRITE_VECTORS").orEmpty().contains("resend-dropped")) {
      outFile.writeText(now.entries.joinToString(",\n", "{\n", "\n}\n") { (k, v) ->
        "  ${JsonPrimitive(k)}: " + if (v is JsonArray) v.joinToString(",\n", "[\n", "\n  ]") { "    $it" } else v.toString()
      })
    }
    val file = Json.parseToJsonElement(outFile.readText()).jsonObject
    assertEquals("resend-dropped-v1.json کهنه است — TOHID_WRITE_VECTORS=resend-dropped", OpHash.canonical(now), OpHash.canonical(file))
    assertEquals(5, now.getValue("resend").jsonArray.size)
    for (o in now.getValue("resend").jsonArray.map { it.jsonObject }) assertEquals(serverHash(o), o.getValue("hash").jsonPrimitive.content)
  }
}
