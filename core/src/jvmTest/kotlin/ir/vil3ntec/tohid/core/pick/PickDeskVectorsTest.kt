package ir.vil3ntec.tohid.core.pick

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  PCK-10/12: `PickDesk` در برابرِ بردارِ `core/test-vectors/pick-shelf-v1.json` — همان که
 *  `server/test/pick-shelf.test.js` با `lib/pick-shelf.parseLabel` می‌خواند. فایل‌خوانی فقط این‌جا (jvmTest).
 */
class PickDeskVectorsTest {

  private val vec: JsonObject = run {
    var dir: File? = File(System.getProperty("user.dir")).absoluteFile
    while (dir != null) {
      val f = File(dir, "core/test-vectors/pick-shelf-v1.json").takeIf { it.isFile } ?: File(dir, "test-vectors/pick-shelf-v1.json")
      if (f.isFile) return@run Json.parseToJsonElement(f.readText()).jsonObject
      dir = dir.parentFile
    }
    error("core/test-vectors/pick-shelf-v1.json پیدا نشد")
  }

  @Test
  fun versionIsOne() = assertEquals(1, vec.getValue("version").jsonPrimitive.int)

  @Test
  fun bagLabels() {
    val cs = vec.getValue("label").jsonArray.map { it.jsonObject }
    assertTrue(cs.size >= 8)
    for (c in cs) {
      val raw = c.getValue("raw").jsonPrimitive.content
      val w = c.getValue("want")
      val got = PickDesk.parseLabel(raw)
      if (w is JsonNull) {
        assertNull(got, raw)
      } else {
        val o = w.jsonObject
        assertEquals(PickDesk.Label(o.getValue("orderNo").jsonPrimitive.long, o.getValue("bag").jsonPrimitive.int, o.getValue("of").jsonPrimitive.int), got, raw)
      }
    }
  }

  @Test
  fun slotOrder() {
    for (c in vec.getValue("slotOrder").jsonArray.map { it.jsonObject }) {
      val slots = c.getValue("slots").jsonArray.map { it.jsonPrimitive.content }
      assertEquals(c.getValue("want").jsonArray.map { it.jsonPrimitive.content }, PickDesk.slotOrder(slots))
    }
  }

  @Test
  fun batchRules() {
    assertFalse(PickDesk.canBatch(1))
    assertTrue(PickDesk.canBatch(2))
    assertTrue(PickDesk.canBatch(10))
    assertFalse(PickDesk.canBatch(11))
    assertTrue(PickDesk.batchable("accepted"))
    assertTrue(PickDesk.batchable("picking"))
    for (s in listOf("new", "packed", "ready_for_pickup", "cancelled")) assertFalse(PickDesk.batchable(s), s)
    assertFalse(PickDesk.ready(0, 0))
    assertFalse(PickDesk.ready(1, 2))
    assertTrue(PickDesk.ready(2, 2))
  }
}
