package ir.vil3ntec.tohid.core.hw

/**
 *  صفِ چاپ (HW-02، سناریوی S16) — منطقِ خالص، بی نخ و بی فایل.
 *
 *  قاعده‌ها:
 *    • **به ترتیب**: فقط سرِ صف چاپ می‌شود؛ تا او چاپ نشده، بعدی منتظر است.
 *    • **تلاشِ دوباره** با فاصلهٔ فزاینده (`backoffMs`). وقتی پله‌ها تمام
 *      شد: «چاپِ معوق» روشن ⇒ با آخرین فاصله ادامه؛ خاموش ⇒ کار می‌ایستد
 *      و از کاربر پرسیده می‌شود (`needsUser`).
 *    • **چاپِ دوباره هرگز فروش نمی‌سازد**: کارِ صف فقط شناسهٔ فروش و
 *      بایت‌های آماده را دارد؛ صف به دفتر دست نمی‌زند.
 *    • رسیدِ خودکارِ یک فروش دو بار در صف نمی‌نشیند.
 *    • **کشو هرگز در صف نمی‌نشیند** (HW-03): ضربهٔ کشو «همین حالا یا هرگز»
 *      است — نه در صف، نه در فایل، نه تلاشِ دوباره. وگرنه تلاشِ دوباره یا
 *      بازگشتِ صف پس از بسته شدنِ برنامه کشو را بی فروش باز می‌کرد.
 *    • تلاشِ دوبارهٔ رسید (`attempts > 0`) یا رسیدی که پس از بازگشتِ صف
 *      چاپ می‌شود نسخهٔ «چاپِ دوباره» را می‌برد (`needsCopyMark`): شاید
 *      تلاشِ پیشین نیمه‌کاره روی کاغذ آمده باشد.
 */
class PrintQueue(
  private val backoffMs: List<Long> = listOf(2_000, 5_000, 15_000, 30_000, 60_000),
) {

  enum class Kind(val key: String) {
    RECEIPT("receipt"), REPRINT("reprint"), TEST("test"), DRAWER("drawer"), LABEL("label");

    companion object {
      fun of(key: String?): Kind = entries.firstOrNull { it.key == key } ?: RECEIPT
    }
  }

  data class Job(
    val id: String,
    val kind: Kind,
    /** چاپگر — همان رشتهٔ `Link.save()` */
    val link: String,
    val title: String,
    val createdAt: Long,
    val saleId: String? = null,
    /** دلیلِ چاپِ دوباره — برای `REPRINT` اجباری */
    val reason: String = "",
    val attempts: Int = 0,
    val nextAt: Long = 0,
    val lastError: String? = null,
    /** پله‌ها تمام شد و «چاپِ معوق» خاموش است — منتظرِ کاربر */
    val held: Boolean = false,
    /** از فایلِ صف برگشته (پس از بسته شدنِ برنامه) — ذخیره نمی‌شود */
    val restored: Boolean = false,
  ) {
    /** این تلاش نسخهٔ «چاپِ دوباره» را می‌برد: رسیدی که پیش‌تر تلاش شده یا از فایل برگشته */
    val needsCopyMark: Boolean get() = kind == Kind.RECEIPT && (attempts > 0 || restored)
  }

  sealed interface Status {
    /** صف خالی */
    data object Idle : Status
    /** سرِ صف آماده یا در حالِ چاپ */
    data class Busy(val job: Job, val waiting: Int) : Status
    /** چاپ نشد؛ تلاشِ بعدی در `nextAt` */
    data class Retrying(val job: Job, val waiting: Int) : Status
    /** چاپ نشد و صف ایستاد — «تلاشِ دوباره» یا «کنار گذاشتن» */
    data class NeedsUser(val job: Job, val waiting: Int) : Status
  }

  private val jobs = ArrayList<Job>()

  /** «چاپِ معوق»: پس از تمام شدنِ پله‌ها هم خودش ادامه بدهد */
  var deferred: Boolean = false

  val size: Int get() = jobs.size
  fun all(): List<Job> = jobs.toList()

  /** @return `false` اگر همین رسیدِ خودکار از پیش در صف باشد */
  fun enqueue(job: Job): Boolean {
    require(job.kind != Kind.DRAWER) { "کشو در صف نمی‌رود — همین حالا یا هرگز" }
    require(job.kind != Kind.REPRINT || job.reason.isNotBlank()) { "چاپِ دوباره دلیل می‌خواهد" }
    if (job.kind == Kind.RECEIPT && job.saleId != null &&
      jobs.any { it.kind == Kind.RECEIPT && it.saleId == job.saleId }
    ) return false
    jobs += job
    return true
  }

  /** کارِ آمادهٔ چاپ — همیشه سرِ صف، و فقط وقتی زمانش رسیده و نایستاده */
  fun next(now: Long): Job? {
    val head = jobs.firstOrNull() ?: return null
    if (head.held || now < head.nextAt) return null
    return head
  }

  fun succeeded(id: String) {
    jobs.removeAll { it.id == id }
  }

  fun failed(id: String, error: String, now: Long) {
    val i = jobs.indexOfFirst { it.id == id }
    if (i < 0) return
    val j = jobs[i]
    val attempts = j.attempts + 1
    val exhausted = attempts > backoffMs.size
    val wait = backoffMs[minOf(attempts, backoffMs.size) - 1]
    jobs[i] = j.copy(
      attempts = attempts,
      lastError = error,
      nextAt = now + wait,
      held = exhausted && !deferred,
    )
  }

  /** «تلاشِ دوباره» — همین حالا، و ایستادن برداشته می‌شود */
  fun retryNow(now: Long) {
    val head = jobs.firstOrNull() ?: return
    jobs[0] = head.copy(nextAt = now, held = false)
  }

  /** کنار گذاشتنِ یک کار (مثلاً «رسیدِ دیجیتال فرستادم»). فروش دست نمی‌خورد. */
  fun discard(id: String) {
    jobs.removeAll { it.id == id }
  }

  fun status(): Status {
    val head = jobs.firstOrNull() ?: return Status.Idle
    val waiting = jobs.size - 1
    return when {
      head.held -> Status.NeedsUser(head, waiting)
      head.attempts > 0 -> Status.Retrying(head, waiting)
      else -> Status.Busy(head, waiting)
    }
  }

  /**
   *  بازگرداندنِ صفِ ذخیره‌شده پس از بسته شدنِ برنامه — به همان ترتیب.
   *  کارِ کشو (از نسخه‌ای کهنه) دور ریخته می‌شود و هر رسید `restored` می‌شود.
   */
  fun restore(saved: List<Job>) {
    jobs.clear()
    jobs.addAll(saved.filter { it.kind != Kind.DRAWER }.map { it.copy(restored = true) })
  }

  companion object {
    /**
     *  بایت‌هایی که این تلاش به چاپگر می‌برد. `read(true)` = نسخهٔ «چاپِ دوباره».
     *  رسیدی که نسخهٔ نشان‌دار ندارد `null` می‌شود (چاپِ بی‌نشانِ دوباره بدتر از
     *  نبودنش است) و ضربهٔ کشو، اگر بایتی از نسخه‌ای کهنه داشته باشد، برداشته می‌شود.
     */
    fun payload(job: Job, read: (copy: Boolean) -> ByteArray?): ByteArray? =
      read(job.needsCopyMark)?.let(EscPos::withoutDrawerKick)
  }
}

/**
 *  ضربهٔ کشو «همین حالا یا هرگز» (HW-03): فقط یک بار، و فقط اگر تا `windowMs`
 *  پس از درخواست به چاپگر رسید. نه صف دارد، نه فایل، نه تلاشِ دوباره.
 */
class KickOnce(private val requestedAt: Long, private val windowMs: Long = WINDOW_MS) {
  private var used = false

  /** `true` فقط یک بار و فقط در مهلت */
  fun take(now: Long): Boolean {
    if (used || now - requestedAt > windowMs) return false
    used = true
    return true
  }

  companion object {
    /** چاپگرِ مشغول تا این مدت صبر می‌شود؛ پس از آن کشو باز نمی‌شود */
    const val WINDOW_MS = 3_000L
  }
}
