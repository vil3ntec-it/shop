package ir.vil3ntec.tohid.core.sync

/**
 *  WEB-15 — نسخهٔ کارتِ ویترین برای عکس‌هایی که پیش از این قابلیت همگام شدند: سرِ فرصت، در پس‌زمینه، با سقف و
 *  قابلِ ادامه. `PhotoSync` ِ برنامه شبکه و دیسک را می‌زند ([Io])؛ تصمیم‌ها این‌جاست تا با آزمونِ واحد سنجیده شوند.
 *
 *  هر عکسِ تازه کارتش را درست پس از خودش می‌فرستد (همان درِ کارت؛ نشد ⇒ همین‌جا). این‌جا عکسِ کهنه و کارتِ جامانده:
 *  ردیفِ `GET /sync/v1/photos?since=` که `thumb: false` است و همین دستگاه **همان** بایت‌ها را دارد (hash ِ نسخهٔ
 *  محلی = hash ِ سرور) ⇒ کارت از نسخهٔ محلی ساخته و با `PUT /sync/v1/photos/:id/thumb` (`of` = همان hash) فرستاده
 *  می‌شود. پس عکسِ اصل هرگز دوباره بالا نمی‌رود، و کارت هرگز از عکسی جز همان اصلِ سرور ساخته نمی‌شود.
 *
 *    • **ادامه از جای قطع:** نشانهٔ جدای `trev` فقط تا پیش از نخستین ردیفِ ناتمام جلو می‌رود (همان
 *      [PhotoSyncPlan.cursor]) و پس از هر صفحه ذخیره می‌شود؛ قطعِ شبکه/خاموشی ⇒ دورِ بعد از همان ردیف.
 *    • **بی‌اثر در تکرار:** ردیفی که کارت دارد (`thumb: true`) کاری نمی‌خواهد؛ سرور هم کارتِ تکراری را `unchanged`
 *      می‌گوید. ۴۰۹ (اصل عوض شد)، ۴۰۴ (عکس برداشته شد) و هر ۴xx ِ دیگر ⇒ همان ردیف رد می‌شود، هرگز حلقه.
 *    • **با سقف:** حداکثر [BATCH] کارت در هر دور با [SPACING_MS] فاصله؛ دفتر مشغول ⇒ پیش از هر صفحه صبر ([Io.page]
 *      ِ برنامه با همان `PhotoSyncPlan.waitMs`). هیچ‌وقت روی نخِ رابط نیست (`PhotoSync`: IO، پشتِ همان قفل).
 */
object PhotoThumbBackfill {

  data class Row(val productId: String, val hash: String, val rev: Long, val deleted: Boolean, val thumb: Boolean)

  class Page(val rows: List<Row>, val head: Long, val hasMore: Boolean)

  interface Io {
    /** صفحهٔ `GET /sync/v1/photos?since=`؛ null ⇒ شبکه نشد */
    suspend fun page(since: Long): Page?
    /** hash ِ نسخهٔ محلیِ این کالا (همان که از سرور گرفته یا به آن فرستاده شد)؛ نبود ⇒ null */
    fun localHash(productId: String): String?
    /** کارت از نسخهٔ محلی؛ نشد (فایلِ خراب) ⇒ null */
    fun build(productId: String): ByteArray?
    /** `PUT /sync/v1/photos/:id/thumb` ⇒ کدِ HTTP؛ `0` ⇒ شبکه نشد */
    suspend fun send(productId: String, of: String, thumb: ByteArray): Int
    /** صبرِ میانِ دو فرستادن (و پیش از صفحه، تا دفتر مشغول است) */
    suspend fun pause(ms: Long)
    /** نشانهٔ تازه، پس از هر صفحه */
    fun save(cursor: Long)
  }

  /** @property more کار مانده و همین حالا شدنی است (سقفِ دور پر شد) ⇒ برنامه کمی بعد دوباره صدا می‌زند */
  class Outcome(val cursor: Long, val sent: Int, val more: Boolean)

  const val BATCH = 12
  const val SPACING_MS = 1_500L
  /** پس از دورِ پر، دورِ بعد این‌قدر بعد (نه پشتِ سرِ هم) */
  const val NEXT_RUN_MS = 30_000L
  const val MAX_PAGES = 40

  /** این ردیف کارت می‌خواهد و این دستگاه همان اصل را دارد */
  fun needs(row: Row, localHash: String?): Boolean =
    !row.deleted && !row.thumb && row.hash.isNotEmpty() && localHash == row.hash

  enum class Verdict { SENT, SKIP, RETRY }

  /** ۲xx ⇒ رسید؛ شبکه، ۴۰۱/۴۰۳ (نشست/اجازه)، ۴۰۸، ۴۲۹ و ۵xx ⇒ بعداً از همین ردیف؛ بقیهٔ ۴xx ⇒ این ردیف رد */
  fun verdict(status: Int): Verdict = when {
    status in 200..299 -> Verdict.SENT
    status == 0 || status == 401 || status == 403 || status == 408 || status == 429 || status >= 500 -> Verdict.RETRY
    else -> Verdict.SKIP
  }

  suspend fun run(since: Long, io: Io, budget: Int = BATCH): Outcome {
    var cursor = since
    var sent = 0
    repeat(MAX_PAGES) {
      val page = io.page(cursor) ?: return Outcome(cursor, sent, more = false)
      val done = ArrayList<PhotoSyncPlan.Item>(page.rows.size)
      var full = false
      var offline = false
      for (row in page.rows) {
        if (!needs(row, io.localHash(row.productId))) { done += PhotoSyncPlan.Item(row.rev, true); continue }
        if (sent >= budget) { full = true; done += PhotoSyncPlan.Item(row.rev, false); break }
        val bytes = io.build(row.productId)
        if (bytes == null) { done += PhotoSyncPlan.Item(row.rev, true); continue }
        if (sent > 0) io.pause(SPACING_MS)
        when (verdict(io.send(row.productId, row.hash, bytes))) {
          Verdict.SENT -> { sent++; done += PhotoSyncPlan.Item(row.rev, true) }
          Verdict.SKIP -> done += PhotoSyncPlan.Item(row.rev, true)
          Verdict.RETRY -> { offline = true; done += PhotoSyncPlan.Item(row.rev, false) }
        }
        if (offline) break
      }
      cursor = PhotoSyncPlan.cursor(cursor, done, page.head)
      io.save(cursor)
      if (full) return Outcome(cursor, sent, more = true)
      if (offline || PhotoSyncPlan.stopped(done) || !page.hasMore) return Outcome(cursor, sent, more = false)
    }
    return Outcome(cursor, sent, more = true)
  }
}
