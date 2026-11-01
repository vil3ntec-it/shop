package ir.vil3ntec.tohid.core.pos

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** POS-26 — `SpecialOrder` روی همهٔ `core/test-vectors/special-order-v1.json` (core وابستگیِ JSON ندارد ⇒ هر ردیف با الگو) */
class SpecialOrderVectorsTest {

  private val text: String by lazy {
    var dir: File? = File("").absoluteFile
    var found: String? = null
    while (dir != null && found == null) {
      val f = File(dir, "core/test-vectors/special-order-v1.json")
      if (f.isFile) found = f.readText()
      dir = dir.parentFile
    }
    found ?: error("core/test-vectors/special-order-v1.json پیدا نشد")
  }

  private fun str(line: String, key: String): String = Regex("\"$key\": \"([^\"]*)\"").find(line)?.groupValues?.get(1) ?: error("$key در $line نیست")
  private fun num(line: String, key: String): Long = Regex("\"$key\": (-?[0-9]+)").find(line)?.groupValues?.get(1)?.toLong() ?: error("$key در $line نیست")
  private fun bool(line: String, key: String): Boolean = Regex("\"$key\": (true|false)").find(line)?.groupValues?.get(1)?.toBooleanStrict() ?: error("$key در $line نیست")
  private fun list(line: String, key: String): List<String> =
    (Regex("\"$key\": \\[([^\\]]*)\\]").find(line)?.groupValues?.get(1) ?: error("$key در $line نیست"))
      .split(',').map { it.trim().removeSurrounding("\"") }.filter { it.isNotEmpty() }
  /** منفیِ یک در بردار = کادرِ خالی یا نادرست */
  private fun opt(line: String, key: String): Long? = num(line, key).takeIf { it != -1L }
  private fun want(line: String): String? = str(line, "want").ifEmpty { null }

  private fun section(name: String): List<String> {
    val start = text.indexOf("\"$name\": [")
    require(start >= 0) { "$name نیست" }
    val end = text.indexOf("\n  ]", start)
    return text.substring(start, end).lines().filter { it.trimStart().startsWith("{ \"name\"") }
  }

  private fun status(line: String, key: String) = SpecialOrder.Status.of(str(line, key)) ?: error("وضعیتِ $key در $line")

  @Test fun draftProblems() {
    val rows = section("draft")
    assertTrue(rows.size >= 19)
    for (r in rows) {
      val d = SpecialOrder.Draft(
        hasCustomer = bool(r, "customer"), item = str(r, "item"), qtyMilli = opt(r, "qty"),
        totalMinor = opt(r, "total"), depositMinor = opt(r, "deposit"), due = str(r, "due"), today = "2026-11-01",
      )
      assertEquals(want(r), SpecialOrder.draftProblem(d), str(r, "name"))
    }
  }

  @Test fun moves() {
    val rows = section("move")
    assertTrue(rows.size >= 13)
    for (r in rows) {
      assertEquals(want(r), SpecialOrder.moveProblem(status(r, "from"), status(r, "to"), str(r, "reason")), str(r, "name"))
    }
  }

  @Test fun foldEvents() {
    val rows = section("fold")
    assertTrue(rows.size >= 10)
    for (r in rows) {
      val ids = list(r, "ids"); val at = list(r, "at"); val to = list(r, "to"); val void = list(r, "void")
      val events = ids.indices.map { SpecialOrder.Event(ids[it], at[it].toLong(), to[it], void[it].toBooleanStrict()) }
      val f = SpecialOrder.fold(events)
      val name = str(r, "name")
      assertEquals(str(r, "status"), f.status.code, name)
      assertEquals(list(r, "applied"), f.applied, name)
      assertEquals(bool(r, "review"), f.review, name)
    }
  }

  @Test fun settleAtDelivery() {
    val rows = section("settle")
    assertTrue(rows.size >= 6)
    for (r in rows) {
      val s = SpecialOrder.settle(num(r, "total"), num(r, "held"))
      val name = str(r, "name")
      assertEquals(num(r, "apply"), s.applyMinor, name)
      assertEquals(num(r, "pay"), s.payNowMinor, name)
      assertEquals(num(r, "left"), s.leftMinor, name)
      //  به حساب + نقد = جمعِ فاکتور، همیشه
      assertEquals(maxOf(0L, num(r, "total")), s.applyMinor + s.payNowMinor, name)
    }
  }

  @Test fun heldAndReview() {
    val rows = section("money")
    assertTrue(rows.size >= 7)
    for (r in rows) {
      val dep = num(r, "deposits"); val ref = num(r, "refunds")
      val f = SpecialOrder.Folded(status(r, "status"), emptyList(), emptyList(), bool(r, "foldReview"))
      val name = str(r, "name")
      assertEquals(num(r, "held"), SpecialOrder.held(dep, ref), name)
      assertEquals(bool(r, "review"), SpecialOrder.review(f, dep, ref), name)
    }
  }

  @Test fun overdueDays() {
    val rows = section("overdue")
    assertTrue(rows.size >= 7)
    for (r in rows) {
      assertEquals(bool(r, "want"), SpecialOrder.overdue(status(r, "status"), str(r, "due"), str(r, "today")), str(r, "name"))
    }
  }
}
