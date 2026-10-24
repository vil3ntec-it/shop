package ir.vil3ntec.tohid.core.safe

import ir.vil3ntec.tohid.data.SafeBook
import ir.vil3ntec.tohid.data.SafeEntry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  بردارهای مشترکِ `core/test-vectors/safe-v1.json` روی `SafeCore`. بخشِ `shape` را
 *  `server/test/safe-ledger.test.js` هم روی سنجشِ سرور می‌دواند. ردیف‌ها از درِ مدلِ
 *  روی خطِ برنامه (`SafeEntry`) خوانده می‌شوند تا نام‌های روی خط هم سنجیده شوند.
 */
class SafeCoreTest {

  private val v: JsonObject = run {
    var dir: File? = File(System.getProperty("user.dir")).absoluteFile
    while (dir != null) {
      val f = File(dir, "core/test-vectors/safe-v1.json")
      if (f.isFile) return@run Json.parseToJsonElement(f.readText()).jsonObject
      dir = dir.parentFile
    }
    error("core/test-vectors/safe-v1.json پیدا نشد")
  }

  private val json = Json { ignoreUnknownKeys = true }
  private fun row(e: JsonElement): SafeCore.Entry = SafeBook.toCore(json.decodeFromJsonElement(SafeEntry.serializer(), e))
  private fun cases(name: String) = v.getValue(name).jsonArray.map { it.jsonObject }
  private fun JsonObject.s(k: String): String? = this[k]?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull
  private fun JsonObject.l(k: String): Long? = this[k]?.takeIf { it !is JsonNull }?.jsonPrimitive?.longOrNull
  private fun JsonObject.entries() = getValue("entries").jsonArray.map(::row)
  private val threshold = v.getValue("threshold").jsonPrimitive.longOrNull!!

  @Test fun vectorsCoverEverySection() {
    assertEquals(SafeCore.VERSION.toLong(), v.getValue("version").jsonPrimitive.longOrNull)
    assertTrue(cases("shape").size >= 30 && cases("view").isNotEmpty() && cases("build").isNotEmpty())
  }

  @Test fun shape() {
    for (c in cases("shape")) {
      assertEquals(c.s("name"), c.s("code"), SafeCore.check(row(c.getValue("row"))))
    }
  }

  @Test fun view() {
    for (c in cases("view")) {
      val name = c.s("name")
      val o = c.getValue("out").jsonObject
      val got = SafeCore.view(c.entries())
      assertEquals(name, o.l("balanceMinor"), got.balanceMinor)
      assertEquals(name, o.s("lastCountId"), got.lastCount?.entry?.id)
      assertEquals(name, o.l("inSinceCountMinor"), got.inSinceCountMinor)
      assertEquals(name, o.l("outSinceCountMinor"), got.outSinceCountMinor)
      assertEquals(name, o.l("varianceTotalMinor"), got.varianceTotalMinor)
      assertEquals(name, o.getValue("negative").jsonPrimitive.booleanOrNull, got.negative)
      assertEquals(name, o.getValue("needsReview").jsonArray.map { it.jsonPrimitive.content }, got.needsReview)
      assertEquals(name, o.getValue("invalid").jsonArray.map { it.jsonPrimitive.content }, got.invalid)
      val lines = o.getValue("lines").jsonArray.map { l ->
        val a = l.jsonArray
        listOf(a[0].jsonPrimitive.content, a[1].jsonPrimitive.longOrNull, a[2].jsonPrimitive.longOrNull,
          a[3].takeIf { it !is JsonNull }?.jsonPrimitive?.longOrNull, a[4].jsonPrimitive.booleanOrNull)
      }
      assertEquals(name, lines, got.history.map { listOf(it.entry.id, it.deltaMinor, it.balanceMinor, it.varianceMinor, it.late) })
    }
  }

  @Test fun build() {
    for (c in cases("build")) {
      val name = c.s("name")
      val i = c.getValue("in").jsonObject
      val o = c.getValue("out").jsonObject
      val input = SafeCore.Input(
        id = i.s("id"), kind = i.s("kind")!!, amountMinor = i.l("amountMinor")!!, at = i.l("at")!!,
        by = i.s("by"), reason = i.s("reason"), note = i.s("note"), bagNo = i.s("bagNo"),
        shiftId = i.s("shiftId"), auditId = i.s("auditId"),
      )
      when (val r = SafeCore.build(c.entries(), input, threshold)) {
        is SafeCore.Result.Err -> {
          assertEquals(name, o.s("code"), r.code)
          assertEquals(name, o.l("varianceMinor"), r.varianceMinor)
        }
        is SafeCore.Result.Ok -> {
          assertEquals(name, null, o.s("code"))
          assertEquals(name, o.l("at"), r.value.at)
          assertEquals(name, o.l("expectedMinor"), r.value.expectedMinor)
          assertEquals(name, o.getValue("duplicate").jsonPrimitive.booleanOrNull, r.duplicate)
          assertEquals("$name: ردیفِ ساخته‌شده خودش درست است", null, SafeCore.check(r.value))
        }
      }
    }
  }

  /** ردیفِ ساختهٔ build بی‌درنگ در view همان مانده‌ای را می‌دهد که سنجیده شد */
  @Test fun buildThenViewAgree() {
    var rows = emptyList<SafeCore.Entry>()
    fun add(i: SafeCore.Input) {
      val r = SafeCore.build(rows, i, threshold)
      assertTrue("$i ⇒ $r", r is SafeCore.Result.Ok)
      rows = rows + (r as SafeCore.Result.Ok).value
    }
    add(SafeCore.Input("a", SafeCore.COUNT, 50_000, 10))
    add(SafeCore.Input("b", SafeCore.IN, 12_500, 20, reason = "deposit"))
    add(SafeCore.Input("c", SafeCore.TO_BANK, 30_000, 30, bagNo = "K-9"))
    add(SafeCore.Input("d", SafeCore.COUNT, 32_000, 40))
    val view = SafeCore.view(rows)
    assertEquals(32_000L, view.balanceMinor)
    assertEquals(-500L, view.lastCount?.varianceMinor)
    assertEquals(emptyList<String>(), view.needsReview)
    assertEquals(32_500L, rows.last().expectedMinor)
  }
}
