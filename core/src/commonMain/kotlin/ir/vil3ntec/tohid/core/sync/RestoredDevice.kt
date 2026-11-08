package ir.vil3ntec.tohid.core.sync

/**
 *  DATA-06 — گوشی‌ای که از پشتیبان یا انتقالِ گوشیِ **دیگر** بالا آمد (منطقِ خالصِ مشترکِ گوشی، کامپیوتر و آیفون؛
 *  پیاده‌سازیِ اندروید: `sync/v1/V1ReplicaAndroid.kt`؛ Sync v3: `sync/v3/V3State.kt` با همین [decide]).
 *
 *  ── اشکالی که این می‌بندد ───────────────────────────────────────────
 *  ۱) از #991 `replica` ِ هر حساب (`device_id` ِ push/pull و سریِ سند) در `tohid-sync-v1.xml` بود و آن پرونده با پشتیبان
 *     می‌رفت ⇒ گوشیِ بازیابی‌شده با `device_uid` ِ تازه همان `device_id` ِ گوشیِ پیشین را به سرور می‌داد: دو گوشی با یک
 *     شناسه، یک سری، opهای «خودی»ِ گوشیِ دیگر که هرگز نمی‌رسیدند.
 *  ۲) شناسهٔ تازه با cursor ِ پشتیبان: opهای خودِ گوشیِ پیشین پس از آن cursor (همان‌ها که در لحظهٔ پشتیبان در صف بودند و
 *     اثرشان در دفترِ پشتیبان هست) «دیگری» دیده و دوباره روی دفتر می‌نشستند ⇒ `$inc` ِ موجودی و قرض دو بار.
 *  ──────────────────────────────────────────────────────────────────────
 *
 *  راه: حالِ همگام‌سازیِ هر حساب به **شناسهٔ همین دستگاه** بسته است (`<کلید>.device`). نبستگی ⇒ «حصار»: replica از شناسهٔ
 *  امروز (جداییِ حساب‌ها، #991، می‌ماند)، و پیش از هر Pull یا هم‌ترازی یک بار «بازسازی از سرور» (صف اول، بعد Snapshot؛
 *  `SyncRebuild`) ⇒ دفتر همان سرور، cursor سرِ Snapshot، هیچ opی دو بار.
 *  خطا همیشه به سوی «نچرخان» (همان قاعدهٔ آیفون، `docs/IOS-PLAN-fa.md` §۰.۱۶.۱): شناسهٔ ناخوانا ⇒ هیچ تصمیمی؛ به‌روزرسانی از
 *  نسخهٔ پیشین (بی بستگی، replica ِ همین دستگاه، شناسهٔ دستگاهِ پیشین) فقط می‌بندد، هرگز نمی‌چرخاند.
 */
object RestoredDevice {

  /** سقفِ `device_id` روی سرور — همان `SyncReplica` */
  private const val MAX = 64

  /** پرونده‌های `filesDir` که فقط با کار کردنِ همگام‌سازی ساخته می‌شوند (صف، ژورنال، دفترچهٔ §۹.۵، حالِ v3: `sync-v3/` و `sync-v3-*`) */
  private val SYNC_FILES = listOf("oplog-", "sync-journal-", "sync-replay-", "sync-v3")

  /**
   *  شناسهٔ دستگاه همین حالا **ساخته شد** (پروندهٔ «فقط همین دستگاه» نبود) — آیا ردِ کارِ پیشین هست؟ هست ⇒ پشتیبانِ گوشیِ
   *  دیگر (یا گم شدنِ همان پرونده)؛ نصبِ تازه هیچ‌کدام را ندارد و به‌روزرسانی شناسه را نمی‌سازد.
   *  @param deskOwner صاحبِ دفترِ روی میز (`LedgerOwner`، با پشتیبان می‌آید)
   *  @param files نامِ پرونده‌های `filesDir`
   */
  fun mintedOverHistory(deskOwner: String, files: Collection<String>): Boolean =
    deskOwner.isNotBlank() || files.any { f -> SYNC_FILES.any { f.startsWith(it) } }

  enum class Verdict {
    /** شناسهٔ دستگاه خوانده نشد ⇒ هیچ نوشتنی */
    UNDECIDED,

    /** همین دستگاه */
    SAME,

    /** نخستین بار با این نسخه (به‌روزرسانی، یا حسابِ تازه): فقط بستن، بی چرخش */
    BIND,

    /** حالِ دستگاهِ دیگر: replica از شناسهٔ امروز و «بازسازی از سرور» پیش از هر Pull */
    FENCE,
  }

  /**
   *  @param device شناسهٔ همین دستگاه (`SyncStore.deviceUid`)؛ خالی ⇒ ناخوانا
   *  @param bound دستگاهی که این حال به آن بسته شد؛ خالی ⇒ هنوز نه (نسخهٔ پیشین یا حسابِ تازه)
   *  @param replica `SyncStateV1.replica` ِ همین حساب
   *  @param restoredAt شناسهٔ همین دستگاه روی ردِ کارِ پیشین ساخته شد ([mintedOverHistory])؛ ۰ ⇒ نه
   *  @param history این حساب روی این دستگاه چیزی دارد (حالِ همگام‌سازی، صف، یا ردیفِ دفترِ روی میز)
   */
  fun decide(device: String, bound: String, replica: String, restoredAt: Long, history: Boolean): Verdict = when {
    device.isBlank() -> Verdict.UNDECIDED
    //  replica فقط از شناسهٔ همین دستگاه انتخاب می‌شود (`SyncReplica`) ⇒ جز آن یعنی شناسهٔ دستگاهِ دیگر
    replica.isNotBlank() && !belongs(replica, device) -> Verdict.FENCE
    bound == device -> Verdict.SAME
    bound.isNotBlank() -> Verdict.FENCE
    restoredAt > 0 && history -> Verdict.FENCE
    else -> Verdict.BIND
  }

  /** replica از همین دستگاه است: خودِ شناسه یا `<شناسه>-<برچسبِ حساب>` (`SyncReplica.scoped`) */
  fun belongs(replica: String, device: String): Boolean {
    if (device.isBlank()) return false
    if (replica == device) return true
    val dash = replica.lastIndexOf('-')
    if (dash <= 0) return false
    val head = replica.substring(0, dash)
    return head.isNotEmpty() && device.startsWith(head) && head.length >= minOf(device.length, MAX - (replica.length - dash))
  }

  /**
   *  replica پس از حصار، روی [device]: `<کهنه>-<برچسب>` ⇒ `<تازه>-<برچسب>` (همان برچسب ⇒ همان جداییِ حساب‌ها)؛ شناسهٔ خام
   *  ⇒ خامِ تازه اگر هنوز حسابِ دیگری آن را نگرفته، وگرنه شناسهٔ خودِ همین حساب. خالی ⇒ خالی (`V1Replica` از نو انتخاب می‌کند).
   *  همان `DeviceIdentity.remap` ِ آیفون، بی نیاز به شناسهٔ کهنه.
   *  @param rawOwner حسابی که شناسهٔ خامِ [device] را گرفته است (`SyncStore.replicaOwner`)
   */
  fun remap(replica: String, device: String, account: String, rawOwner: String): String {
    val r = replica.trim()
    if (r.isEmpty() || device.isBlank()) return ""
    if (belongs(r, device)) return r
    val dash = r.lastIndexOf('-')
    if (dash > 0 && dash < r.length - 1) {
      val tag = r.substring(dash + 1)
      return device.take(MAX - tag.length - 1) + "-" + tag
    }
    val a = account.trim()
    return if (a.isEmpty() || rawOwner.isBlank() || rawOwner.trim() == a) device else SyncReplica.scoped(device, a)
  }
}
