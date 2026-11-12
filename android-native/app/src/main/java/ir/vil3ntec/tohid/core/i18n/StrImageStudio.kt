package ir.vil3ntec.tohid.core.i18n

/**
 *  ۴-۲۳ پرسشِ ۱ (AI-02، IMG-PRO-01..04) — صفحه‌های «استودیوی عکس» (`ui/v3/stock/ImageStudioPages.kt`؛ منطق در
 *  `core/image/ImageStudio`).
 */
@Suppress("unused")
object StrImageStudio {

  val title: String get() = t("استودیوی عکس", "Photo studio", "د انځور سټوډیو")
  val off: String get() = t("استودیوی عکس برای این دکان روشن نیست.", "The photo studio is not turned on for this shop.", "د انځور سټوډیو د دې دوکان لپاره فعاله نه ده.")
  val loading: String get() = t("از سرور می‌آید…", "Loading from the server…", "له سرور څخه راځي…")
  val failed: String get() = t("از سرور نیامد. اینترنت را ببینید و دوباره بزنید.", "Could not load from the server. Check the internet and try again.", "له سرور څخه رانغی. انټرنېټ وګورئ او بیا هڅه وکړئ.")
  val retry: String get() = t("دوباره", "Retry", "بیا")
  val none: String get() = t("هنوز هیچ دسته‌ای نیست.", "No batches yet.", "لا تر اوسه هېڅ ډله نشته.")
  val note: String get() = t(
    "استودیو نور، رنگ، برش و زمینه را روی سرورِ خودتان درست می‌کند. اصلِ عکس دست نمی‌خورد و نسخهٔ تازه تا شما تأیید نکنید منتشر نمی‌شود.",
    "The studio fixes light, colour, crop and background on your own server. The original is untouched and the new version is not published until you approve it.",
    "سټوډیو رڼا، رنګ، پرې کول او شاليد ستاسو په خپل سرور سموي. اصلي انځور نه بدلېږي او نوې نسخه تر ستاسو تایید پورې نه خپرېږي.",
  )
  val newBatch: String get() = t("دستهٔ تازه", "New batch", "نوې ډله")
  val batch: String get() = t("دستهٔ استودیو", "Studio batch", "د سټوډیو ډله")
  val presetCatalog: String get() = t("عکسِ کاتالوگ (زمینهٔ سفید، مربع)", "Catalogue photo (white, square)", "د کتلاګ انځور (سپین، مربع)")
  val presetEnhance: String get() = t("فقط نور و رنگ", "Light and colour only", "یوازې رڼا او رنګ")
  val presetAd: String get() = t("تصویرِ تبلیغ (۱۶:۹)", "Ad image (16:9)", "د اعلان انځور (۱۶:۹)")
  val stateActive: String get() = t("در کار", "Running", "په کار کې")
  val statePaused: String get() = t("متوقف", "Paused", "درول شوې")
  val stateCancelled: String get() = t("لغو شد", "Cancelled", "لغوه شوه")
  val stateDone: String get() = t("تمام شد", "Finished", "پای ته ورسېده")
  fun progress(done: Int, total: Int, percent: Int): String = t("${asIs(done)} از ${asIs(total)} (${asIs(percent)}٪)", "$done of $total ($percent%)", "$done له $total ($percent٪)")
  fun toReview(n: Int): String = t("${asIs(n)} عکس منتظرِ تأییدِ شما", "$n photos await your review", "$n انځورونه ستاسو تایید ته منتظر دي")
  fun line(state: String, progress: String): String = t("${asIs(state)} · ${asIs(progress)}", "$state · $progress", "$state · $progress")
  val pause: String get() = t("توقف", "Pause", "درول")
  val resume: String get() = t("ادامه", "Resume", "دوام")
  val cancel: String get() = t("لغوِ دسته", "Cancel batch", "ډله لغوه کول")
  val retryFailed: String get() = t("دوباره برای ناموفق‌ها", "Retry failed ones", "ناکامې بیا هڅه")
  val before: String get() = t("اصل", "Original", "اصلي")
  val after: String get() = t("نسخهٔ استودیو", "Studio version", "د سټوډیو نسخه")
  val approve: String get() = t("تأیید و انتشار", "Approve and publish", "تایید او خپرول")
  val reject: String get() = t("رد", "Reject", "ردول")
  val riskTitle: String get() = t("این خروجی پرخطر است؛ پیش از تأیید با اصل مقایسه کنید.", "This output is risky; compare it with the original before approving.", "دا پایله خطرناکه ده؛ له تایید مخکې یې له اصلي سره پرتله کړئ.")
  val approveRisk: String get() = t("با وجودِ خطر تأیید می‌کنم", "Approve despite the risk", "سره له خطر تاییدوم")
  val approved: String get() = t("تأیید شد", "Approved", "تایید شو")
  val rejected: String get() = t("رد شد", "Rejected", "رد شو")
  val failedItem: String get() = t("ناموفق", "Failed", "ناکام")
  val waiting: String get() = t("در صف", "Queued", "په کتار کې")
  val more: String get() = t("بیشتر", "More", "نور")
  // ── دستهٔ تازه ──
  val pickPhotos: String get() = t("عکس‌هایی را که می‌خواهید درست شوند برگزینید.", "Choose the photos you want fixed.", "هغه انځورونه وټاکئ چې غواړئ سم شي.")
  fun selected(n: Int, max: Int): String = t("${asIs(n)} از ${asIs(max)} برگزیده", "$n of $max selected", "$n له $max ټاکل شوي")
  val start: String get() = t("آغاز", "Start", "پیل")
  val noPhotos: String get() = t("این دکان هنوز عکسی در بانک ندارد.", "This shop has no photos in the bank yet.", "دا دوکان لا په بانک کې انځور نه لري.")
  val emptyPick: String get() = t("دستِ‌کم یک عکس برگزینید.", "Choose at least one photo.", "لږ تر لږه یو انځور وټاکئ.")
  fun tooMany(max: Int): String = t("بیش از ${asIs(max)} عکس در یک دسته نمی‌شود.", "No more than $max photos in one batch.", "په یوه ډله کې له $max څخه ډېر انځورونه نه شي.")
}
