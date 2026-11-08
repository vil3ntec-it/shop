package ir.vil3ntec.tohid.core.sync

import ir.vil3ntec.tohid.core.sync.PhotoThumbBackfill.Row
import ir.vil3ntec.tohid.core.sync.PhotoThumbBackfill.Verdict
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** WEB-15 — کارتِ عکس‌های کهنه: فقط آن‌چه لازم است، بی‌اثر در تکرار، ادامه از جای قطع، با سقف و فاصله */
class PhotoThumbBackfillTest {

  private fun <T> runSync(block: suspend () -> T): T {
    var out: Result<T>? = null
    block.startCoroutine(Continuation(EmptyCoroutineContext) { out = it })
    return checkNotNull(out) { "معلق ماند" }.getOrThrow()
  }

  /** سرورِ جعلی (فهرستِ `/sync/v1/photos` با `thumb`) + دستگاهِ جعلی (نسخهٔ محلی) */
  private class World(rows: List<Row>, val local: Map<String, String>, val pageSize: Int = 100) : PhotoThumbBackfill.Io {
    val server = rows.associateBy { it.productId }.toMutableMap()
    val sent = ArrayList<Pair<String, String>>()
    val pauses = ArrayList<Long>()
    val built = ArrayList<String>()
    var saved = -1L
    var offlinePages = false
    /** کدِ پاسخِ هر فرستادن (پیش‌فرض ۲۰۰)؛ با هر بار برداشته می‌شود */
    val replies = HashMap<String, ArrayDeque<Int>>()
    var unbuildable = emptySet<String>()

    override suspend fun page(since: Long): PhotoThumbBackfill.Page? {
      if (offlinePages) return null
      val all = server.values.filter { it.rev > since }.sortedBy { it.rev }
      val rows = all.take(pageSize)
      return PhotoThumbBackfill.Page(rows, rows.lastOrNull()?.rev ?: since, all.size > pageSize)
    }
    override fun localHash(productId: String) = local[productId]
    override fun build(productId: String): ByteArray? { built += productId; return if (productId in unbuildable) null else byteArrayOf(1, 2, 3) }
    override suspend fun send(productId: String, of: String, thumb: ByteArray): Int {
      val code = replies[productId]?.removeFirstOrNull() ?: 200
      if (code == 200) {
        sent += productId to of
        server[productId] = server.getValue(productId).copy(thumb = true)
      }
      return code
    }
    override suspend fun pause(ms: Long) { pauses += ms }
    override fun save(cursor: Long) { saved = cursor }
  }

  private fun row(id: String, rev: Long, thumb: Boolean = false, deleted: Boolean = false, hash: String = "h-$id") =
    Row(id, if (deleted) "" else hash, rev, deleted, thumb)

  @Test
  fun onlyPhotosWithoutAThumbWhoseSameOriginalIsOnThisDeviceAreSent() {
    val w = World(
      listOf(
        row("rice", 1), row("oil", 2, thumb = true), row("gone", 3, deleted = true),
        row("tea", 4), row("other", 5), row("soap", 6),
      ),
      //  `tea` روی این دستگاه نسخهٔ دیگری است؛ `other` اینجا نیست
      local = mapOf("rice" to "h-rice", "oil" to "h-oil", "tea" to "h-tea-old", "soap" to "h-soap"),
    )
    val out = runSync { PhotoThumbBackfill.run(0, w) }
    assertEquals(listOf("rice" to "h-rice", "soap" to "h-soap"), w.sent, "of = همان hash ِ اصلِ سرور")
    assertEquals(listOf("rice", "soap"), w.built, "کارت فقط برای همان‌ها ساخته شد")
    assertEquals(2, out.sent)
    assertEquals(6L, out.cursor)
    assertEquals(6L, w.saved)
    assertFalse(out.more)
    assertEquals(listOf(PhotoThumbBackfill.SPACING_MS), w.pauses, "میانِ دو فرستادن فاصله، نه پیش از نخستین")
  }

  @Test
  fun aSecondRunIsANoOpEvenFromZero() {
    val w = World(listOf(row("a", 1), row("b", 2)), mapOf("a" to "h-a", "b" to "h-b"))
    runSync { PhotoThumbBackfill.run(0, w) }
    assertEquals(2, w.sent.size)
    //  همان دستگاه دوباره (حتی با نشانهٔ پاک‌شده): سرور هر دو را «کارت دارد» می‌گوید ⇒ هیچ فرستادن و ساختنی
    w.built.clear()
    val again = runSync { PhotoThumbBackfill.run(0, w) }
    assertEquals(0, again.sent)
    assertEquals(2, w.sent.size)
    assertTrue(w.built.isEmpty())
  }

  @Test
  fun aNetworkCutResumesFromTheSameRowWithoutResending() {
    val w = World(listOf(row("a", 1), row("b", 2), row("c", 3)), mapOf("a" to "h-a", "b" to "h-b", "c" to "h-c"))
    w.replies["b"] = ArrayDeque(listOf(0))
    val first = runSync { PhotoThumbBackfill.run(0, w) }
    assertEquals(listOf("a"), w.sent.map { it.first })
    assertEquals(1L, first.cursor, "نشانه پیش از b می‌ماند")
    assertEquals(1L, w.saved)
    assertFalse(first.more, "بی شبکه ⇒ دورِ بعد با kick ِ بعدی، نه پشتِ سرِ هم")
    //  شبکه برگشت: از همان b، و a دوباره نمی‌رود
    val second = runSync { PhotoThumbBackfill.run(w.saved, w) }
    assertEquals(listOf("a", "b", "c"), w.sent.map { it.first })
    assertEquals(3L, second.cursor)
    //  خاموشیِ وسطِ دور: نشانهٔ ذخیره‌شده همان آخرین صفحهٔ تمام
    val w2 = World(listOf(row("x", 10)), mapOf("x" to "h-x"))
    w2.offlinePages = true
    val off = runSync { PhotoThumbBackfill.run(7, w2) }
    assertEquals(7L, off.cursor)
    assertEquals(-1L, w2.saved, "صفحه نیامد ⇒ نشانه دست نمی‌خورد")
  }

  @Test
  fun theBatchCapStopsBeforeTheNextRowAndAsksForAnotherRun() {
    val ids = (1..5).map { "p$it" }
    val w = World(ids.mapIndexed { i, id -> row(id, (i + 1).toLong()) }, ids.associateWith { "h-$it" })
    val first = runSync { PhotoThumbBackfill.run(0, w, budget = 2) }
    assertEquals(listOf("p1", "p2"), w.sent.map { it.first })
    assertEquals(2L, first.cursor)
    assertTrue(first.more)
    assertEquals(listOf("p1", "p2"), w.built, "سومی حتی ساخته نشد")
    val second = runSync { PhotoThumbBackfill.run(first.cursor, w, budget = 2) }
    val third = runSync { PhotoThumbBackfill.run(second.cursor, w, budget = 2) }
    assertEquals(ids, w.sent.map { it.first }, "هر کدام یک بار")
    assertFalse(third.more)
    assertEquals(5L, third.cursor)
    assertEquals(PhotoThumbBackfill.BATCH, 12)
  }

  @Test
  fun staleGoneOrRejectedRowsAreSkippedNeverLooped() {
    val w = World(
      listOf(row("stale", 1), row("gone", 2), row("bad", 3), row("broken", 4), row("ok", 5)),
      mapOf("stale" to "h-stale", "gone" to "h-gone", "bad" to "h-bad", "broken" to "h-broken", "ok" to "h-ok"),
    )
    w.replies["stale"] = ArrayDeque(listOf(409))
    w.replies["gone"] = ArrayDeque(listOf(404))
    w.replies["bad"] = ArrayDeque(listOf(415))
    w.unbuildable = setOf("broken")
    val out = runSync { PhotoThumbBackfill.run(0, w) }
    assertEquals(listOf("ok"), w.sent.map { it.first })
    assertEquals(5L, out.cursor, "ردیف‌های ردشده نشانه را نگه نمی‌دارند")
    assertFalse(out.more)
  }

  @Test
  fun sessionThrottleAndServerErrorsWaitForTheNextRun() {
    for (code in listOf(0, 401, 403, 408, 429, 500, 503)) {
      assertEquals(Verdict.RETRY, PhotoThumbBackfill.verdict(code), "$code")
      val w = World(listOf(row("a", 1), row("b", 2)), mapOf("a" to "h-a", "b" to "h-b"))
      w.replies["a"] = ArrayDeque(listOf(code))
      val out = runSync { PhotoThumbBackfill.run(0, w) }
      assertEquals(0L, out.cursor, "$code: همان ردیف دورِ بعد")
      assertTrue(w.sent.isEmpty(), "$code: پس از آن چیزی فرستاده نشد")
    }
    for (code in listOf(400, 404, 409, 413, 415, 422)) assertEquals(Verdict.SKIP, PhotoThumbBackfill.verdict(code), "$code")
    for (code in listOf(200, 201)) assertEquals(Verdict.SENT, PhotoThumbBackfill.verdict(code), "$code")
  }

  @Test
  fun pagesAreFollowedAndTheCursorIsSavedAfterEach() {
    val ids = (1..7).map { "q$it" }
    val w = World(ids.mapIndexed { i, id -> row(id, (i + 1).toLong(), thumb = i % 2 == 0) }, ids.associateWith { "h-$it" }, pageSize = 3)
    val out = runSync { PhotoThumbBackfill.run(0, w) }
    assertEquals(listOf("q2", "q4", "q6"), w.sent.map { it.first })
    assertEquals(7L, out.cursor)
    assertEquals(7L, w.saved)
  }

  @Test
  fun needsIsStrictAboutTheSameOriginal() {
    assertTrue(PhotoThumbBackfill.needs(row("a", 1), "h-a"))
    assertFalse(PhotoThumbBackfill.needs(row("a", 1), null))
    assertFalse(PhotoThumbBackfill.needs(row("a", 1), "h-other"))
    assertFalse(PhotoThumbBackfill.needs(row("a", 1, thumb = true), "h-a"))
    assertFalse(PhotoThumbBackfill.needs(row("a", 1, deleted = true), ""))
    assertFalse(PhotoThumbBackfill.needs(Row("a", "", 1, false, false), ""), "بی hash ⇒ نه")
  }
}
