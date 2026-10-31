package ir.vil3ntec.tohid.core.product

import ir.vil3ntec.tohid.core.product.RecentlyViewed.Seen
import kotlin.test.Test
import kotlin.test.assertEquals

/** WEB-13 — همان پرونده‌ها که `server/test/storefront-recent.test.js` برای `recent.js` می‌سنجد. */
class RecentlyViewedTest {

  private val now = 1_800_000_000_000L
  private val day = 24L * 60 * 60 * 1000

  @Test
  fun newestFirstAndReopenMovesToTop() {
    var l = emptyList<Seen>()
    l = RecentlyViewed.add(l, "rice", now - 3_000)
    l = RecentlyViewed.add(l, "oil", now - 2_000)
    l = RecentlyViewed.add(l, "soap", now - 1_000)
    l = RecentlyViewed.add(l, "rice", now)
    assertEquals(listOf("rice", "soap", "oil"), l.map { it.id })
    assertEquals(now, l.first().atMs)
  }

  @Test
  fun capAndExpiry() {
    var l = emptyList<Seen>()
    for (i in 1..20) l = RecentlyViewed.add(l, "p$i", now - (20 - i) * 1_000L)
    assertEquals(RecentlyViewed.MAX, l.size)
    assertEquals("p20", l.first().id)
    assertEquals("p9", l.last().id)
    val old = listOf(Seen("a", now - 31 * day), Seen("b", now - 29 * day))
    assertEquals(listOf("b"), RecentlyViewed.fresh(old, now).map { it.id })
  }

  @Test
  fun badRowsAreDroppedNotFatal() {
    val raw = listOf(
      Seen("ok", now - 10), Seen("", now - 5), Seen("bad id", now - 4), Seen("x".repeat(81), now - 3),
      Seen("far", now + 31 * day), Seen("ok", now - 20),
    )
    assertEquals(listOf(Seen("ok", now - 10)), RecentlyViewed.fresh(raw, now))
    assertEquals(listOf("ok"), RecentlyViewed.add(raw, "no/slash", now).map { it.id })
    //  برابریِ زمان ⇒ ترتیبِ شناسه، تا دو سکو یک‌جور بچینند
    assertEquals(listOf("a", "b"), RecentlyViewed.fresh(listOf(Seen("b", now), Seen("a", now)), now).map { it.id })
  }
}
