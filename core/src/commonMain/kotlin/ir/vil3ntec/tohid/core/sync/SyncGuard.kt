package ir.vil3ntec.tohid.core.sync

/**
 *  حالِ همگام‌سازی که همیشه دیده می‌شود (SYN-08) و قفلِ خروج وقتی چیزی
 *  هنوز نرفته (SYN-10) — منطقِ خالص، بی اندروید، تا روی JVM سنجیده شود و
 *  برنامهٔ کامپیوتر هم همین را بخواند.
 *
 *  عددها از جای واقعی می‌آیند: `SyncV1Engine.status` (صفِ opها، آخرین
 *  همگام‌سازیِ موفق، آخرین خطا). این‌جا فقط تصمیم و متن ساخته می‌شود.
 *  نسخهٔ وب همین قاعده‌ها را در `license/account-ui.js` دارد.
 */
object SyncBadge {

  /** خطای سرور گاهی بلند است؛ نوارِ وضعیت یک خط است. */
  const val MAX_ERROR = 80

  /** «۵ دقیقه پیش» — زمانِ آخرین همگام‌سازیِ موفق. صفر یعنی هنوز نشده. */
  fun since(lastOkAt: Long, now: Long): String {
    if (lastOkAt <= 0) return "هنوز همگام نشده"
    val minutes = ((now - lastOkAt) / 60_000L).coerceAtLeast(0)
    return when {
      minutes < 1 -> "همین حالا"
      minutes < 60 -> "$minutes دقیقه پیش"
      minutes < 24 * 60 -> "${minutes / 60} ساعت پیش"
      else -> "${minutes / (24 * 60)} روز پیش"
    }
  }

  /**
   *  یک خط برای نوارِ وضعیت: سرخط (حال)، خطا اگر هست، شمارِ منتظر اگر
   *  چیزی مانده، و همیشه زمانِ آخرین همگام‌سازیِ موفق.
   */
  fun line(head: String, queued: Int, lastOkAt: Long, lastError: String, now: Long): String {
    val error = lastError.trim().replace('\n', ' ')
    val parts = mutableListOf(if (error.isEmpty()) head else "$head: ${clip(error)}")
    if (queued > 0) parts += "در صف: $queued"
    parts += "آخرین همگام‌سازی: ${since(lastOkAt, now)}"
    return parts.joinToString(" · ")
  }

  private fun clip(s: String): String = if (s.length <= MAX_ERROR) s else s.take(MAX_ERROR - 1) + "…"
}

/**
 *  SYN-10: تا وقتی تغییری از این دستگاه به سرور نرفته، خروج از حساب بسته
 *  است — مگر «خروجِ اجباری»: اول یک پشتیبانِ کامل در پوشهٔ دیدنی نوشته
 *  شود (بخش ۳.۸ بند ۶: «پیش از هر خروج از حساب، یک پشتیبان در پوشه قابل‌دید
 *  گرفته می‌شود») و بعد کاربر دو بار تأیید کند.
 */
object LogoutGuard {

  sealed interface Verdict
  data object Allow : Verdict
  data class Block(val pending: Int) : Verdict

  /**
   *  @param queued opهای صفِ Sync v1 (`SyncStatus.queued`)
   *  @param legacyPending شمارِ راهِ قدیم (`AutoSync.pendingCount`)
   */
  fun decide(queued: Int, legacyPending: Int = 0): Verdict {
    val pending = queued.coerceAtLeast(0) + legacyPending.coerceAtLeast(0)
    return if (pending > 0) Block(pending) else Allow
  }

  /** خروجِ اجباری فقط با پشتیبانِ نوشته‌شده و دو تأیید. */
  fun canForce(backupWritten: Boolean, confirmations: Int): Boolean = backupWritten && confirmations >= 2

  const val TITLE = "هنوز همه‌چیز به سرور نرفته"
  const val SYNC_NOW = "همین حالا همگام کن"
  const val FORCE = "خروجِ اجباری…"
  const val STAY = "ماندن"
  const val FORCE_TITLE = "خروجِ اجباری؟"

  fun message(pending: Int, online: Boolean): String = buildString {
    append("$pending تغییرِ این دستگاه هنوز به سرور نرسیده است. ")
    append("تا این‌ها نروند، خروج از حساب بسته است؛ وگرنه این کارها روی سرور و دستگاه‌های دیگر دیده نمی‌شوند. ")
    append(
      if (online) "«$SYNC_NOW» را بزنید و بمانید تا صف خالی شود."
      else "اینترنتِ این دستگاه وصل نیست؛ اول وصل شوید و بعد «$SYNC_NOW» را بزنید."
    )
  }

  /** پرسشِ دوم — پس از نوشته شدنِ پشتیبان. */
  fun forceMessage(pending: Int, backupAt: String): String =
    "پشتیبانِ کامل این‌جا نوشته شد:\n$backupAt\n\n" +
      "$pending تغییر هنوز به سرور نرفته و تا دوباره با همین حساب وارد نشوید فرستاده نمی‌شود. " +
      "باز هم خارج می‌شوید؟"
}
