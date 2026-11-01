package ir.vil3ntec.tohid.core.pay

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** PAY-24 — `GiftCard` و `StoreCredit` روی همهٔ `core/test-vectors/gift-v1.json` (core وابستگیِ JSON ندارد ⇒ هر ردیف با الگو) */
class GiftCardVectorsTest {

  private val text: String by lazy {
    var dir: File? = File("").absoluteFile
    var found: String? = null
    while (dir != null && found == null) {
      val f = File(dir, "core/test-vectors/gift-v1.json")
      if (f.isFile) found = f.readText()
      dir = dir.parentFile
    }
    found ?: error("core/test-vectors/gift-v1.json پیدا نشد")
  }

  private fun str(line: String, key: String): String = Regex("\"$key\": \"([^\"]*)\"").find(line)?.groupValues?.get(1) ?: error("$key در $line نیست")
  private fun num(line: String, key: String): Long = Regex("\"$key\": (-?[0-9]+)").find(line)?.groupValues?.get(1)?.toLong() ?: error("$key در $line نیست")
  private fun bool(line: String, key: String): Boolean = Regex("\"$key\": (true|false)").find(line)?.groupValues?.get(1)?.toBooleanStrict() ?: error("$key در $line نیست")
  private fun list(line: String, key: String): List<String> =
    (Regex("\"$key\": \\[([^\\]]*)\\]").find(line)?.groupValues?.get(1) ?: error("$key در $line نیست"))
      .split(',').map { it.trim().removeSurrounding("\"") }.filter { it.isNotEmpty() }
  private fun want(line: String): String? = str(line, "want").ifEmpty { null }
  private fun name(line: String): String = str(line, "name")

  private fun section(name: String): List<String> {
    val start = text.indexOf("\"$name\": [")
    require(start >= 0) { "$name نیست" }
    //  بخشِ یک‌خطی (`"display": [ {…} ],`) خودش یک ردیف است
    val first = text.substring(start, text.indexOf('\n', start)).trimEnd()
    if (first.endsWith("],") || first.endsWith("]")) return listOf(first)
    val end = text.indexOf("\n  ]", start)
    return text.substring(start, end).lines().filter { it.trimStart().startsWith("{\"name\"") }
  }

  /** id|kind|code|amountMinor|at|by|refId|reason — خالی ⇒ null */
  private fun parts(s: String): List<String?> = s.split('|').map { it.ifEmpty { null } }.also { require(it.size == 8) { s } }

  private fun event(s: String): GiftCard.Event = parts(s).let {
    GiftCard.Event(it[0].orEmpty(), it[1].orEmpty(), it[2].orEmpty(), it[3]!!.toLong(), it[4]!!.toLong(), it[5], it[6], it[7])
  }

  private fun input(s: String): GiftCard.Input = parts(s).let {
    GiftCard.Input(it[0] ?: "", it[1].orEmpty(), it[2], it[3]!!.toLong(), it[4]!!.toLong(), it[5], it[6], it[7])
  }

  private fun events(line: String) = list(line, "events").map(::event)

  @Test fun alphabetAndCodes() {
    assertEquals(GiftCard.ALPHABET, Regex("\"alphabet\": \"([^\"]*)\"").find(text)!!.groupValues[1])
    val rows = section("code")
    assertTrue(rows.size >= 14)
    for (r in rows) assertEquals(want(r), GiftCard.normalize(str(r, "text")), name(r))
    assertNull(GiftCard.normalize(null))
  }

  @Test fun makeAndDisplay() {
    val rows = section("make")
    assertTrue(rows.size >= 3)
    for (r in rows) {
      val picks = list(r, "picks").map { it.toInt() }.iterator()
      val code = GiftCard.make { picks.next() }
      assertEquals(want(r), code, name(r))
      assertEquals(code, GiftCard.normalize(code))
    }
    for (r in section("display")) assertEquals(want(r), GiftCard.display(str(r, "code")), name(r))
  }

  @Test fun shapes() {
    val rows = section("shape")
    assertTrue(rows.size >= 18)
    for (r in rows) assertEquals(want(r), GiftCard.check(event(str(r, "event"))), name(r))
  }

  @Test fun views() {
    val rows = section("view")
    assertTrue(rows.size >= 10)
    for (r in rows) {
      val v = GiftCard.view(events(r))
      val cards = v.cards.map { "${it.code}:${it.balanceMinor}:${it.issuedMinor}:${it.redeemedMinor}:${it.refundedMinor}:${it.void}:${it.review}" }
      assertEquals(list(r, "cards"), cards, name(r))
      assertEquals(list(r, "invalid"), v.invalid, name(r))
      //  تاریخچهٔ هر کارت: تازه‌ترین اول، آخرین مانده همان ماندهٔ کارت
      v.cards.forEach { c -> assertEquals(c.balanceMinor, c.history.first().balanceMinor, name(r)) }
    }
  }

  @Test fun builds() {
    val rows = section("build")
    assertTrue(rows.size >= 20)
    for (r in rows) {
      val out = GiftCard.build(events(r), input(str(r, "in")))
      when (val w = want(r)) {
        null -> {
          assertTrue(out is GiftCard.Result.Ok, "${name(r)}: $out")
          assertEquals(num(r, "at"), out.value.at, name(r))
          assertEquals(bool(r, "dup"), out.duplicate, name(r))
        }
        else -> assertEquals(GiftCard.Result.Err(w), out, name(r))
      }
    }
  }

  @Test fun spends() {
    val rows = section("spend")
    assertTrue(rows.size >= 6)
    for (r in rows) assertEquals(want(r), GiftCard.spendProblem(GiftCard.view(events(r)), str(r, "code"), num(r, "amount")), name(r))
  }

  @Test fun storeCredit() {
    val rows = section("credit")
    assertTrue(rows.size >= 6)
    for (r in rows) {
      assertEquals(num(r, "available"), StoreCredit.available(num(r, "balance")), name(r))
      assertEquals(want(r), StoreCredit.problem(bool(r, "customer"), num(r, "balance"), num(r, "amount")), name(r))
    }
  }

  @Test fun liabilityCountsOnlyLivePositiveCards() {
    val c1 = GiftCard.make { 0 }
    val c2 = GiftCard.make { 1 }
    val v = GiftCard.view(
      listOf(
        GiftCard.Event("i1", GiftCard.ISSUE, c1, 5_000, 1),
        GiftCard.Event("i2", GiftCard.ISSUE, c2, 7_000, 2),
        GiftCard.Event("v2", GiftCard.VOID, c2, 0, 3, reason = "گم شد"),
      ),
    )
    assertEquals(5_000L, v.liabilityMinor)
    assertEquals(0L, v.card(c2)!!.spendableMinor)
    assertEquals(emptyList(), v.review)
  }
}
