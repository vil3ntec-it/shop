package ir.vil3ntec.tohid.core.stock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/**
 *  آزمونِ «رفتار عوض نشد» برای بهینه‌سازیِ [StockCore.replay] (PERF، bench/kotlin).
 *
 *  تعریفِ پیشینِ replay دقیقاً «apply پشتِ سرِ هم» بود که برای هر سند کلِ نقشهٔ
 *  جایگاه‌ها را کپی می‌کرد. حالا replay روی یک نقشهٔ کاری می‌نشیند؛ این‌جا همان
 *  تعریفِ پیشین مرجع است و هر دو روی هزاران سندِ تصادفی‌ولی‌ثابت (همهٔ نوع‌ها، رد شده‌ها،
 *  لات و انقضا، دو انبار، هر دو حالت) باید همان دفتر، همان ترتیبِ نقشه و همان نتیجه‌ها بدهند.
 */
class StockCoreReplayTest {

  /** همان replay ِ پیش از بهینه‌سازی — بی هیچ تغییری */
  private fun referenceReplay(docs: List<StockDoc>, mode: StockMode, today: String?): Pair<StockBook, List<StockResult>> {
    var book = StockBook()
    val results = ArrayList<StockResult>(docs.size)
    for (d in docs) {
      val a = StockCore.apply(book, d, mode, today)
      book = a.book
      results += a.result
    }
    return book to results
  }

  private fun docs(seed: Long, n: Int): List<StockDoc> {
    val r = Random(seed)
    val types = StockCore.TYPE_NAMES.toList().sorted() + listOf("purchase_receipt", "sale", "sale", "sale", "nope")
    val dates = listOf(null, "2026-01-10", "2026-03-01", "2026-10-01", "2027-02-28")
    return List(n) { i ->
      val type = types[r.nextInt(types.size)]
      val lot = if (r.nextInt(4) == 0) {
        val a = dates[r.nextInt(dates.size)]
        val b = dates[r.nextInt(dates.size)]
        LotRef("L${r.nextInt(5)}", productionDate = if (a != null && b != null && a > b) b else a, expiryDate = if (a != null && b != null && a > b) a else b)
      } else null
      val signedTypes = setOf("adjustment", "count_adjust", "production")
      val q = (r.nextInt(5000) + 1).toLong() * (if (type in signedTypes && r.nextBoolean()) -1 else 1)
      StockDoc(
        id = if (i % 97 == 13) "" else "d$i",
        type = type,
        productId = "p${r.nextInt(30)}",
        warehouseId = if (r.nextInt(5) == 0) "w2" else "main",
        userId = "u${r.nextInt(3)}",
        at = 1_000L + i,
        qtyMilli = if (type == "cost_adjust") null else q,
        reason = if (r.nextInt(6) == 0) null else "دلیل",
        costMinor = if (type in setOf("purchase_receipt", "transfer_in") || r.nextInt(3) == 0) (r.nextInt(90_000) + 100).toLong() else null,
        landedMinor = if (r.nextInt(8) == 0) (r.nextInt(500)).toLong() else null,
        valueMinor = if (type == "cost_adjust") (r.nextInt(2000) - 1000).toLong().takeIf { it != 0L } ?: 7L else null,
        lot = lot,
        lotId = if (r.nextInt(6) == 0) "L${r.nextInt(5)}" else null,
        refId = null,
      )
    }
  }

  @Test
  fun replayEqualsSequentialApply() {
    for (seed in 1L..6L) {
      for (mode in StockMode.values()) {
        for (today in listOf(null, "2026-06-01")) {
          val ds = docs(seed, 3_000)
          val (book, results) = StockCore.replay(ds, mode, today)
          val (refBook, refResults) = referenceReplay(ds, mode, today)
          val where = "seed=$seed mode=$mode today=$today"
          assertEquals(where, refResults, results)
          assertEquals(where, refBook, book)
          assertEquals("ترتیبِ نقشه $where", refBook.positions.keys.toList(), book.positions.keys.toList())
          assertTrue("هم پذیرفته و هم ردشده لازم است ($where)", results.any { it.ok } && results.any { !it.ok })
        }
      }
    }
  }

  @Test
  fun replayOfNothingIsEmptyBook() {
    val (book, results) = StockCore.replay(emptyList())
    assertEquals(StockBook(), book)
    assertTrue(results.isEmpty())
  }

  @Test
  fun applyOnReplayedBookDoesNotChangeIt() {
    val (book, _) = StockCore.replay(docs(42L, 500))
    val before = book.positions.toMap()
    val doc = StockDoc("x", "purchase_receipt", "p1", "main", "u", 99_999L, qtyMilli = 1000, costMinor = 500)
    val after = StockCore.apply(book, doc).book
    assertEquals(before, book.positions)
    assertTrue(after.positions != book.positions)
  }
}
