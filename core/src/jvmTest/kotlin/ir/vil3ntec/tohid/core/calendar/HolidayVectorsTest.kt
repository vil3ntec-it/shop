package ir.vil3ntec.tohid.core.calendar

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
 *  LOC-04: `Holidays` در برابرِ بردارِ `core/test-vectors/calendar-holidays-v1.json` — همان که
 *  `server/test/holidays.test.js` با `lib/holidays.parse` ِ سرور می‌خواند. فایل‌خوانی فقط این‌جا (jvmTest).
 */
class HolidayVectorsTest {

  private val vec: JsonObject = run {
    var dir: File? = File(System.getProperty("user.dir")).absoluteFile
    while (dir != null) {
      val f = File(dir, "core/test-vectors/calendar-holidays-v1.json").takeIf { it.isFile }
        ?: File(dir, "test-vectors/calendar-holidays-v1.json")
      if (f.isFile) return@run Json.parseToJsonElement(f.readText()).jsonObject
      dir = dir.parentFile
    }
    error("core/test-vectors/calendar-holidays-v1.json پیدا نشد")
  }

  private fun cases(k: String) = vec.getValue(k).jsonArray.map { it.jsonObject }

  private fun name(c: JsonObject) = c.getValue("name").jsonPrimitive.content

  @Test
  fun versionIsOne() = assertEquals(1, vec.getValue("version").jsonPrimitive.int)

  @Test
  fun parseMatchesServer() {
    val cs = cases("parse")
    assertTrue(cs.size >= 10)
    for (c in cs) {
      val body = (c["body"] as? JsonPrimitive)?.takeIf { it.isString }?.content
      val want = c.getValue("want").jsonObject
      val got = Holidays.parse(body)
      assertEquals(want.getValue("version").jsonPrimitive.long, got.version, name(c))
      assertEquals(want.getValue("dates").jsonArray.map { it.jsonPrimitive.content }, got.dates.toList(), name(c))
    }
  }

  @Test
  fun dueOncePerDay() {
    val cs = cases("due")
    assertTrue(cs.size >= 5)
    for (c in cs) {
      val got = Holidays.due(c.getValue("last").jsonPrimitive.long, c.getValue("now").jsonPrimitive.long)
      assertEquals(c.getValue("want").jsonPrimitive.boolean, got, name(c))
    }
  }
}
