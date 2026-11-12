package ir.vil3ntec.tohid.core.text

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  POS-04: فهرستِ مرکزیِ مترادف در برنامه (`GET /api/search/synonyms`). بی فهرست (هنوز نگرفته، بی‌شبکه، پاسخِ خراب) همان
 *  جست‌وجوی پیشین است؛ سندِ بدشکل نسخهٔ ذخیره‌شده را پاک نمی‌کند؛ ذخیره و خواندن رفت‌وبرگشتِ دقیق.
 */
class SynonymListTest {

  private val doc = """{"version":4,"updatedAt":9,"groups":[["بوره","شکر"],["کچالو","سیب‌زمینی","Potato"]]}"""
  private fun obj(s: String) = Json.parseToJsonElement(s) as JsonObject

  @Test
  fun noListIsEmpty() {
    assertEquals(SynonymTable.EMPTY, SynonymList.parse(null))
    assertEquals(SynonymTable.EMPTY, SynonymList.parse(""))
    assertEquals(SynonymTable.EMPTY, SynonymList.parse("{oops"))
    assertEquals(SynonymTable.EMPTY, SynonymList.parse("[1,2]"))
  }

  @Test
  fun serverDocumentIsReadAsIs() {
    val t = SynonymList.parse(doc)
    assertEquals(4L, t.version)
    assertEquals(listOf(listOf("بوره", "شکر"), listOf("کچالو", "سیب‌زمینی", "Potato")), t.groups)
    //  `2.0` همان ۲ (مثلِ `Number.isInteger` ِ سرور)
    assertEquals(2L, SynonymList.of(obj("""{"version":2.0,"groups":[]}"""))?.version)
  }

  @Test
  fun malformedDocumentIsNoListSoTheStoredOneStays() {
    assertNull(SynonymList.of(obj("""{"version":"4","groups":[]}""")))
    assertNull(SynonymList.of(obj("""{"version":-1,"groups":[]}""")))
    assertNull(SynonymList.of(obj("""{"version":1.5,"groups":[]}""")))
    assertNull(SynonymList.of(obj("""{"version":1,"groups":{"a":1}}""")))
    assertNull(SynonymList.of(obj("""{"groups":[]}""")))
  }

  @Test
  fun badTermsAndShortGroupsAreDroppedNotTheList() {
    val long = "ا".repeat(SynonymList.TERM_MAX + 1)
    val t = SynonymList.of(obj("""{"version":1,"groups":[["تنها"],["ماست"," ماست ",""," "],["دوغ",3,"$long","Doogh"],"x",["چای","چای سبز"]]}"""))!!
    assertEquals(listOf(listOf("دوغ", "Doogh"), listOf("چای", "چای سبز")), t.groups)
  }

  @Test
  fun capsAreTheServersCaps() {
    val groups = (1..SynonymList.GROUPS_MAX + 3).joinToString(",") { """["a$it","b$it"]""" }
    assertEquals(SynonymList.GROUPS_MAX, SynonymList.of(obj("""{"version":1,"groups":[$groups]}"""))!!.groups.size)
    val terms = (1..SynonymList.TERMS_MAX + 5).joinToString(",") { "\"w$it\"" }
    assertEquals(SynonymList.TERMS_MAX, SynonymList.of(obj("""{"version":1,"groups":[[$terms]]}"""))!!.groups.single().size)
  }

  @Test
  fun encodeRoundTrips() {
    val t = SynonymList.parse(doc)
    assertEquals(t, SynonymList.parse(SynonymList.encode(t)))
    assertEquals(SynonymTable.EMPTY, SynonymList.parse(SynonymList.encode(SynonymTable.EMPTY)))
  }

  @Test
  fun dueOncePerDayOrWhenTheClockWentBack() {
    val now = 1_800_000_000_000L
    assertTrue(SynonymList.due(0L, now))
    assertFalse(SynonymList.due(now - 60_000L, now))
    assertTrue(SynonymList.due(now - SynonymList.REFRESH_MS, now))
    assertTrue(SynonymList.due(now + 60_000L, now))
  }
}
