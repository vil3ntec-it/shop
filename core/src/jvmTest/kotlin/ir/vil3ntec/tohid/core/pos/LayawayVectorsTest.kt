package ir.vil3ntec.tohid.core.pos

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** POS-27 — `Layaway` روی همهٔ `core/test-vectors/layaway-v1.json` (core وابستگیِ JSON ندارد ⇒ هر ردیف با الگو) */
class LayawayVectorsTest {

  private val text: String by lazy {
    var dir: File? = File("").absoluteFile
    var found: String? = null
    while (dir != null && found == null) {
      val f = File(dir, "core/test-vectors/layaway-v1.json")
      if (f.isFile) found = f.readText()
      dir = dir.parentFile
    }
    found ?: error("core/test-vectors/layaway-v1.json پیدا نشد")
  }

  private fun str(line: String, key: String): String = Regex("\"$key\": \"([^\"]*)\"").find(line)?.groupValues?.get(1) ?: error("$key در $line نیست")
  private fun num(line: String, key: String): Long = Regex("\"$key\": (-?[0-9]+)").find(line)?.groupValues?.get(1)?.toLong() ?: error("$key در $line نیست")
  private fun bool(line: String, key: String): Boolean = Regex("\"$key\": (true|false)").find(line)?.groupValues?.get(1)?.toBooleanStrict() ?: error("$key در $line نیست")
  private fun list(line: String, key: String): List<String> =
    (Regex("\"$key\": \\[([^\\]]*)\\]").find(line)?.groupValues?.get(1) ?: error("$key در $line نیست"))
      .split(',').map { it.trim().removeSurrounding("\"") }.filter { it.isNotEmpty() }
  /** منفیِ یک در بردار = کادرِ خالی یا نادرست */
  private fun opt(line: String, key: String): Long? = num(line, key).takeIf { it != -1L }
  private fun want(line: String, key: String = "want"): String? = str(line, key).ifEmpty { null }
  private fun name(line: String): String = str(line, "name")

  private val today: String by lazy { Regex("\"today\": \"([^\"]*)\"").find(text)!!.groupValues[1] }

  private fun section(name: String): List<String> {
    val start = text.indexOf("\"$name\": [")
    require(start >= 0) { "$name نیست" }
    val end = text.indexOf("\n  ]", start)
    return text.substring(start, end).lines().filter { it.trimStart().startsWith("{\"name\"") }
  }

  private fun draft(r: String) = Layaway.Draft(
    kind = str(r, "kind"), hasCustomer = bool(r, "customer"), totalMinor = opt(r, "total"), depositMinor = opt(r, "deposit"),
    count = opt(r, "count")?.toInt(), everyDays = opt(r, "every")?.toInt(), firstDue = str(r, "due"), today = today,
  )

  private fun lines(r: String): List<Layaway.Installment> = list(r, "lines").map { s ->
    val (no, due, amount) = s.split(':')
    Layaway.Installment(no.toInt(), due, amount.toLong())
  }

  @Test fun draftProblems() {
    val rows = section("draft")
    assertTrue(rows.size >= 19)
    for (r in rows) assertEquals(want(r), Layaway.draftProblem(draft(r)), name(r))
  }

  @Test fun schedules() {
    val rows = section("schedule")
    assertTrue(rows.size >= 5)
    for (r in rows) {
      val s = Layaway.schedule(draft(r))
      assertEquals(lines(r), s, name(r))
      //  جمعِ قسط‌ها دقیقاً همان باقی، و هیچ قسطی از قبلی بزرگ‌تر نیست
      if (s.isNotEmpty()) {
        assertEquals(num(r, "total") - num(r, "deposit"), s.sumOf { it.amountMinor }, name(r))
        assertTrue(s.zipWithNext().all { (a, b) -> b.amountMinor <= a.amountMinor }, name(r))
      }
    }
  }

  @Test fun progressRows() {
    val rows = section("progress")
    assertTrue(rows.size >= 11)
    for (r in rows) {
      val p = Layaway.progress(lines(r), num(r, "paid"), str(r, "today"))
      assertEquals(list(r, "out"), p.lines.map { "${it.no}:${it.state.code}:${it.paidMinor}" }, name(r))
      assertEquals(num(r, "remaining"), p.remainingMinor, name(r))
      assertEquals(num(r, "late"), p.lateMinor, name(r))
      assertEquals(opt(r, "next")?.toInt(), p.next?.no, name(r))
      assertEquals(num(r, "over"), p.overMinor, name(r))
      assertEquals(p.next == null, p.settled, name(r))
    }
  }

  @Test fun pays() {
    val rows = section("pay")
    assertTrue(rows.size >= 6)
    for (r in rows) assertEquals(want(r), Layaway.payProblem(Layaway.progress(lines(r), num(r, "paid"), today), num(r, "amount")), name(r))
  }

  @Test fun deliverAndCancel() {
    val rows = section("end")
    assertTrue(rows.size >= 5)
    for (r in rows) {
      val p = Layaway.progress(lines(r), num(r, "paid"), today)
      assertEquals(want(r, "deliver"), Layaway.deliverProblem(str(r, "kind"), p), name(r))
      assertEquals(opt(r, "refund"), Layaway.cancelRefund(str(r, "kind"), num(r, "deposit"), p), name(r))
    }
  }
}
