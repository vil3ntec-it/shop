package ir.vil3ntec.tohid.sync.v3

import ir.vil3ntec.tohid.sync.v1.SyncOp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 *  تبدیلِ دفترِ امروز به op ِ v3 = همان `fromV2`ِ سرور با همان `fromLegacy`
 *  (`core/test-vectors/sync-v3-convert-v1.json`، ساختهٔ سرور) — و جدول‌ها همان
 *  `sync-v3-schema.js`. وب همین فایل را در `server/test/sync-v3-client.test.js` می‌دواند.
 */
class V3ConvertVectorsTest {

  private fun vectors(name: String): JsonObject {
    var dir: File? = File(System.getProperty("user.dir")).absoluteFile
    while (dir != null) {
      listOf("core/test-vectors/$name", "license/test-vectors/$name").map { File(dir, it) }.firstOrNull { it.isFile }?.let {
        return Json.parseToJsonElement(it.readText()).jsonObject
      }
      dir = dir.parentFile
    }
    error("$name پیدا نشد")
  }

  private val v = vectors("sync-v3-convert-v1.json")
  private fun strings(e: JsonElement) = e.jsonArray.map { it.jsonPrimitive.content }

  @Test fun tablesMatchServer() {
    val server = v.getValue("tables").jsonObject
    assertEquals(server.keys.sorted(), V3Schema.TABLES.keys.sorted())
    for ((name, t) in server) {
      val s = V3Schema.specOf(name)!!
      val o = t.jsonObject
      assertEquals("$name.policy", o.getValue("policy").jsonPrimitive.content, s.policy)
      assertEquals("$name.money", strings(o.getValue("money")), s.money)
      assertEquals("$name.qty", strings(o.getValue("qty")), s.qty)
      assertEquals("$name.counters", strings(o.getValue("counters")), s.counters)
      assertEquals("$name.derived", strings(o.getValue("derived")), s.derived)
      assertEquals("$name.localOnly", strings(o.getValue("localOnly")), s.localOnly)
      assertEquals("$name.cancel", o.getValue("cancel").jsonPrimitive.content, s.cancel)
      assertEquals("$name.v3only", o.getValue("v3only").jsonPrimitive.boolean, s.v3only)
    }
  }

  @Test fun casesMatchServer() {
    val hlc = v.getValue("hlc").jsonPrimitive.content
    val cases = v.getValue("cases").jsonArray.map { it.jsonObject }
    for (c in cases) {
      val input = c.getValue("in").jsonObject
      val op = SyncOp.fromJson(input)!!
      val r = V3Convert.fromLegacyOp(op, hlc)
      val id = "${op.table} ${op.type} ${op.opId}"
      assertEquals("ops: $id", c.getValue("ops").jsonArray.map { it.jsonObject }, r.ops)
      assertEquals("dropped: $id", strings(c.getValue("dropped")), r.dropped)
      assertEquals("native: $id", c.getValue("native").jsonPrimitive.boolean, r.native)
      assertEquals("reason: $id", c.getValue("reason").jsonPrimitive.content, r.reason)
    }
  }

  /** همان قاعدهٔ یگانهٔ money-v1.json — بی گردکردنِ دوم */
  @Test fun moneyRuleIsFromLegacy() {
    val money = vectors("money-v1.json").getValue("fromLegacy").jsonObject
    for (row in money.getValue("money").jsonArray) {
      val (x, minor) = row.jsonArray
      assertEquals("$x", minor.jsonPrimitive.content.toLong(), V3Convert.toMinor(x))
    }
    for (row in money.getValue("qty").jsonArray) {
      val (x, milli) = row.jsonArray
      assertEquals("$x", milli.jsonPrimitive.content.toLong(), V3Convert.toMilli(x))
    }
    assertNull(V3Convert.toMinor(JsonPrimitive(1e300)))
    assertNull(V3Convert.toMinor(JsonPrimitive("0x10")))
    assertEquals(1250L, V3Convert.toMinor(JsonPrimitive(" 12.5 ")))
  }

  /** پاسخِ pull ⇒ دفترِ امروز: واحدها برمی‌گردند، جدولِ تازهٔ v3 به دفتر نمی‌رسد */
  @Test fun effectsBackToLegacy() {
    val item = Json.parseToJsonElement(
      """{"op_id":"x","seq":3,"table":"sales","row_id":"s1","kind":"delta","effect":{"inc":{"paidAmountMinor":5025}}}""",
    ).jsonObject
    val op = V3Convert.effectToLegacy(item)!!
    assertEquals(SyncOp.UPDATE, op.type)
    assertEquals(Json.parseToJsonElement("""{"paidAmount":{"${'$'}inc":50.25}}"""), op.fields)

    val set = Json.parseToJsonElement(
      """{"op_id":"y","table":"products","row_id":"p1","kind":"insert","effect":{"set":{"name":"شیر","salePriceMinor":12000,"minStockMilli":2500,"note":null}}}""",
    ).jsonObject
    val p = V3Convert.effectToLegacy(set)!!
    assertEquals(SyncOp.INSERT, p.type)
    assertEquals(Json.parseToJsonElement("""{"name":"شیر","salePrice":120,"minStock":2.5,"note":null}"""), p.fields)

    val del = Json.parseToJsonElement("""{"op_id":"z","table":"products","row_id":"p1","kind":"delete","effect":{"deleted":true}}""").jsonObject
    assertEquals(SyncOp.DELETE, V3Convert.effectToLegacy(del)!!.type)
    val count = Json.parseToJsonElement("""{"op_id":"c","table":"stockCounts","row_id":"k","kind":"insert","effect":{"set":{"countedQtyMilli":1}}}""").jsonObject
    assertNull(V3Convert.effectToLegacy(count))
    assertNotNull(V3Convert.snapshotToLegacy(Json.parseToJsonElement("""{"tables":{"stockCounts":[{"id":"k","data":{}}]},"cursor":4}""").jsonObject)["tables"])
  }
}
