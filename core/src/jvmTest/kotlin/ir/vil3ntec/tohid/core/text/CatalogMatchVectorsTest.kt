package ir.vil3ntec.tohid.core.text

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 *  POS-04 — بردارِ مرجعِ جست‌وجوی مقاومِ کاتالوگ (`core/test-vectors/catalog-match-v1.json`): همان پاسخِ کادرِ صندوقِ
 *  نسخهٔ وب (`license/catalog-match.js`، `server/test/web-catalog-match.test.js`) برای هر پرس‌وجو، و همان فاصلهٔ ویرایشی.
 *  موردهای `test`/`groups`/`aliases` همان انتظارهای `CatalogMatchTest` اند؛ بقیه کادرِ وب (حرفِ عربی در نامِ ذخیره‌شده،
 *  نیم‌فاصله، اعراب، کشیده، رقمِ فارسیِ بارکد، «12x34»). یکی را بی دیگری عوض نکن.
 */
class CatalogMatchVectorsTest {

  private val vectors: JsonObject by lazy {
    val root = generateSequence(File("").absoluteFile) { it.parentFile }
      .firstOrNull { File(it, "core/test-vectors/catalog-match-v1.json").isFile }
      ?: error("core/test-vectors/catalog-match-v1.json پیدا نشد (cwd=${File("").absoluteFile})")
    Json.parseToJsonElement(File(root, "core/test-vectors/catalog-match-v1.json").readText()).jsonObject
  }

  private fun strings(a: JsonArray?): List<String> = a?.map { it.jsonPrimitive.content }.orEmpty()

  private val indexes: Map<String, CatalogMatch.Index> by lazy {
    vectors.getValue("catalogs").jsonObject.mapValues { (_, items) ->
      CatalogMatch.index(
        items.jsonArray.map { it.jsonObject }.map { o ->
          CatalogMatch.Item(
            id = o.getValue("id").jsonPrimitive.content,
            name = o.getValue("name").jsonPrimitive.content,
            barcodes = strings(o["barcodes"]?.jsonArray),
            aliases = strings(o["aliases"]?.jsonArray),
            keywords = strings(o["keywords"]?.jsonArray),
          )
        },
      )
    }
  }

  @Test
  fun everySearchRowIsTheWebAnswer() {
    val rows = vectors.getValue("search").jsonArray
    assertTrue(rows.size >= 50, "موردها: ${rows.size}")
    for (r in rows) {
      val o = r.jsonObject
      val index = indexes.getValue(o.getValue("catalog").jsonPrimitive.content)
      val synonyms = o["synonyms"]?.jsonArray?.map { strings(it.jsonArray) }
      val below = o["fuzzyBelow"]?.jsonPrimitive?.int ?: 8
      val got = CatalogMatch.search(index, o.getValue("query").jsonPrimitive.content, synonyms, below)
      assertEquals(strings(o.getValue("out").jsonArray), got, o.toString())
    }
  }

  @Test
  fun everyDistanceRowIsTheWebAnswer() {
    val rows = vectors.getValue("distance").jsonArray
    assertTrue(rows.size >= 100, "موردها: ${rows.size}")
    for (r in rows) {
      val o = r.jsonObject
      val got = CatalogMatch.distance(o.getValue("a").jsonPrimitive.content, o.getValue("b").jsonPrimitive.content, o.getValue("max").jsonPrimitive.int)
      assertEquals(o.getValue("out").jsonPrimitive.int, got, o.toString())
    }
  }
}
