package ir.vil3ntec.tohid.core.delivery

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  DLV-18: `DeliveryRating` در برابرِ بردارِ `core/test-vectors/delivery-ratings-v1.json` — همان که
 *  `server/test/delivery-ratings.test.js` با `lib/delivery-ratings` می‌خواند. فایل‌خوانی فقط این‌جا (jvmTest).
 */
class DeliveryRatingVectorsTest {

  private val vec: JsonObject = run {
    var dir: File? = File(System.getProperty("user.dir")).absoluteFile
    while (dir != null) {
      val f = File(dir, "core/test-vectors/delivery-ratings-v1.json").takeIf { it.isFile } ?: File(dir, "test-vectors/delivery-ratings-v1.json")
      if (f.isFile) return@run Json.parseToJsonElement(f.readText()).jsonObject
      dir = dir.parentFile
    }
    error("core/test-vectors/delivery-ratings-v1.json پیدا نشد")
  }

  /** JSON ⇒ همان نوع‌هایی که سرور می‌بیند: عددِ صحیح Long، اعشار Double، متن String، فهرست List */
  private fun plain(e: JsonElement?): Any? = when (e) {
    null, is JsonNull -> null
    is JsonArray -> e.map { plain(it) }
    is JsonObject -> e.mapValues { plain(it.value) }
    is JsonPrimitive -> when {
      e.isString -> e.content
      e.booleanOrNull != null -> e.booleanOrNull
      e.content.any { it == '.' || it == 'e' || it == 'E' } -> e.content.toDouble()
      else -> e.longOrNull
    }
  }

  @Test
  fun versionAndTags() {
    assertEquals(1, vec.getValue("version").jsonPrimitive.int)
    assertEquals(DeliveryRating.MAX_TAGS, vec.getValue("maxTags").jsonPrimitive.int)
    val tags = vec.getValue("tags").jsonObject
    for (r in listOf("customer", "courier")) assertEquals(tags.getValue(r).jsonArray.map { it.jsonPrimitive.content }, DeliveryRating.TAGS[r])
  }

  @Test
  fun parse() {
    val cs = vec.getValue("parse").jsonArray.map { it.jsonObject }
    assertTrue(cs.size >= 12)
    for (c in cs) {
      val name = c.getValue("name").jsonPrimitive.content
      val body = c.getValue("body").jsonObject
      val w = c.getValue("want").jsonObject
      val (got, err) = DeliveryRating.parse(plain(body["stars"]), plain(body["tags"]), c.getValue("rater").jsonPrimitive.content)
      if (w.containsKey("error")) {
        assertNull(got, name)
        assertEquals(w.getValue("error").jsonPrimitive.content, err, name)
      } else {
        assertEquals("", err, name)
        assertEquals(DeliveryRating.Rating(w.getValue("stars").jsonPrimitive.int, w.getValue("tags").jsonArray.map { it.jsonPrimitive.content }), got, name)
      }
    }
  }

  @Test
  fun summarize() {
    for (c in vec.getValue("summarize").jsonArray.map { it.jsonObject }) {
      val name = c.getValue("name").jsonPrimitive.content
      val list = c.getValue("list").jsonArray.map { it.jsonObject }
        .map { DeliveryRating.Rating(it.getValue("stars").jsonPrimitive.int, it.getValue("tags").jsonArray.map { t -> t.jsonPrimitive.content }) }
      val w = c.getValue("want").jsonObject
      val got = DeliveryRating.summarize(list)
      assertEquals(w.getValue("count").jsonPrimitive.int, got.count, name)
      assertEquals(w.getValue("avgTenths").jsonPrimitive.intOrNull, got.avgTenths, name)
      assertEquals(w.getValue("tags").jsonObject.mapValues { it.value.jsonPrimitive.int }, got.tags, name)
    }
  }
}
