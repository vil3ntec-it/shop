package ir.vil3ntec.tohid.sync.v3

import ir.vil3ntec.tohid.sync.v1.SyncOp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  SYN-05/06 — `current` ِ پاسخِ push ⇒ اصلاحِ دفتر و سایه (`V3Current`) = `currentFixes` ِ وب
 *  (`license/sync-v3-engine.js`)، با بردارِ `core/test-vectors/v3-current-v1.json` که وب در
 *  `server/test/sync-v3-current.test.js` هم می‌دواند (شکلِ پاسخ همان `lib/sync-v3-apply.js` ِ سرور).
 */
class V3CurrentVectorsTest {

  private fun vectors(name: String): JsonObject {
    var dir: File? = File(System.getProperty("user.dir")).absoluteFile
    while (dir != null) {
      File(dir, "core/test-vectors/$name").takeIf { it.isFile }?.let { return Json.parseToJsonElement(it.readText()).jsonObject }
      dir = dir.parentFile
    }
    error("$name پیدا نشد")
  }

  private fun shape(op: SyncOp): JsonObject = buildJsonObject {
    put("op_id", JsonPrimitive(op.opId))
    put("type", JsonPrimitive(op.type))
    put("table", JsonPrimitive(op.table))
    put("row_id", JsonPrimitive(op.rowId))
    op.fields?.let { put("fields", it) }
  }

  @Test fun everyCaseMatchesTheWebEngine() {
    val cases = vectors("v3-current-v1.json").getValue("cases").jsonArray
    assertTrue(cases.size >= 15)
    for (c in cases) {
      val o = c.jsonObject
      val items = o.getValue("items").jsonArray.map { it.jsonObject }.map { i ->
        V3Current.Item(
          table = i.getValue("table").jsonPrimitive.content,
          rowId = i.getValue("row_id").jsonPrimitive.content,
          kind = i.getValue("kind").jsonPrimitive.content,
          fields = i["fields"] as? JsonObject,
          res = i["res"] as? JsonObject,
          pending = i.getValue("pending").jsonPrimitive.boolean,
          exists = i.getValue("exists").jsonPrimitive.boolean,
        )
      }
      val want = o.getValue("want").jsonArray.map { it.jsonObject }
      assertEquals(o.getValue("name").jsonPrimitive.content, want, V3Current.fixes(items).map { shape(it) })
    }
  }
}
