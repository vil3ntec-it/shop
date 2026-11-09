package ir.vil3ntec.tohid.sync.v3

import ir.vil3ntec.tohid.core.sync.RestoredDevice
import ir.vil3ntec.tohid.core.sync.v3.Hlc
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.sync.v1.LedgerDiff
import ir.vil3ntec.tohid.sync.v1.V1Replica
import kotlinx.serialization.Serializable

/** حالِ Sync v3 ِ یک حساب روی همین دستگاه (`state.json` ِ پوشهٔ `V3StateDir`) */
@Serializable
data class V3Meta(
  val initialized: Boolean = false,
  val shadow: String = "",
  val cursor: Long = 0,
  val clock: String = "",
  val offsetMs: Long = 0,
  val lastOkAt: Long = 0,
  val lastPushAt: Long = 0,
  val lastPullAt: Long = 0,
  val lastError: String = "",
  val lastErrorAt: Long = 0,
  val attempt: Int = 0,
  val upgradeProtocol: Int = 0,
  val upgradeMessage: String = "",
  val conflictsOpen: Int = 0,
  val conflictsDirty: Boolean = false,
  val pushBytes: Int = SyncV3Engine.MIN_BYTES,
  val pullBytes: Int = SyncV3Engine.MIN_BYTES,
  /** «دورانِ بازیابیِ» سروری که آخرین بار دیده شد — §۹.۵، `core/sync/RestoreReplay.kt` */
  val epoch: String = "",
  /** سرور برگشته (یا [restoredRebuild]) و دفتر هنوز از Snapshot از نو ساخته نشده — تا آن وقت Pull نه */
  val restorePending: Boolean = false,
  /** دفترچه همهٔ پنجرهٔ پس از پایه را نداشت — هشدار تا `RESTORE_NOTICE_MS` */
  val restoreNoticeAt: Long = 0,
  /** A00: بزرگ‌ترین شناسهٔ صف در نخستین شروع پس از ساختِ پیش از A00 — شرح سرِ `SyncCommit.start` */
  val legacyOpsUpTo: String = "",
  /** DATA-06: دستگاهی (`SyncV3Engine.deviceId`) که این حال به آن بسته است؛ خالی ⇒ هنوز نه (ساختِ پیشین یا حسابِ تازه) */
  val device: String = "",
  /** DATA-06: حالِ دستگاهِ دیگر ([V3Fence]) — همراهِ [restorePending]؛ با همان «بازسازی از سرور» برداشته می‌شود */
  val restoredRebuild: Boolean = false,
)

/** `device_id` و گرهٔ HLC از شناسهٔ خامِ دستگاه — همان که سرور می‌پذیرد (حرف، رقم، `_.-`؛ ۶۴ نویسه) */
internal fun v3Node(raw: String): String = raw.replace(Regex("[^A-Za-z0-9_.-]"), "-").take(64)

/**
 *  DATA-06 برای Sync v3 — همان قاعده‌های v1 (`sync/v1/V1ReplicaAndroid.kt`) و همان تصمیم (`RestoredDevice.decide`)، این بار
 *  برای حالِ v3 (`state.json`: cursor، سایه، ساعتِ HLC، صف).
 *
 *  ── اشکال ───────────────────────────────────────────────────────────
 *  `device_id` ِ v3 خودِ `SyncStore.deviceUid` است (گرهٔ HLC هم)، پس گوشیِ بازیابی‌شده شناسهٔ تازه دارد؛ ولی cursor ِ
 *  پشتیبان با آن می‌آمد ⇒ Pull ِ سرور opهای «دستگاه‌های دیگر» پس از cursor را می‌دهد (`device_id <> $6`) و opهای خودِ گوشیِ
 *  پیشین (همان‌ها که در لحظهٔ پشتیبان در صف بودند و اثرشان در دفترِ پشتیبان هست) دوباره می‌نشستند ⇒ `$inc` ِ
 *  `returnedQty`/`debtSettled` (و موجودی و قرض) دو بار.
 *
 *  ── راه ─────────────────────────────────────────────────────────────
 *  حال به گرهٔ همین دستگاه بسته است ([V3Meta.device]). «replica» ِ v3 گرهٔ آخرین ساعتِ نوشته‌شده است ([V3Meta.clock]،
 *  `<wall>:<counter>:<node>`): حالی که دستگاهِ دیگری نوشت گرهٔ آن را دارد. حصار ([plan]، **یک** نوشتنِ اتمیِ `state.json`):
 *   • پایهٔ سایه = دفترِ روی میز (همان گامِ ۱ ِ v1: تغییرِ پس از بازیابی op می‌شود، چیزی دو بار فرستاده نمی‌شود)؛
 *   • cursor ِ بازیابی‌شده دور (۰)؛
 *   • `restorePending` + [V3Meta.restoredRebuild] ⇒ Pull نه تا `SyncV3Engine.rebuild`: صف **اول** (همان op_idها ⇒ سرور
 *     «تکراری»)، بعد دفتر = Snapshot + ردیف‌های تنهای همین دستگاه (جز حذف‌شده‌های سرور)، cursor = سرِ همان Snapshot؛
 *   • بستن به همین دستگاه؛ گرهٔ HLC همان شناسهٔ امروز (از `SyncStore.deviceUid`، هرگز از حالِ پشتیبان).
 *  خطا به سوی «نچرخان»: شناسهٔ دستگاه یا حالِ ناخوانا ⇒ هیچ تصمیمی؛ به‌روزرسانی از ساختِ پیشین (بی بستگی، ساعتِ همین
 *  دستگاه، `restoredAt` ِ صفر) فقط می‌بندد. صف و ردشده‌ها هرگز دور ریخته نمی‌شوند؛ دفتر هرگز پاک نمی‌شود.
 */
internal object V3Fence {

  class Plan(val verdict: RestoredDevice.Verdict, val next: V3Meta?)

  /**
   *  @param node گرهٔ همین دستگاه (`SyncV3Engine.deviceId`)؛ خالی ⇒ شناسهٔ دستگاه ناخوانا
   *  @param restoredAt `SyncStore.restoredAt`
   *  @param unreadable حالِ v3 روی دیسک خوانده نشد (`V3Store.unreadable`)
   *  @param ledger دفترِ روی میزِ همین حساب
   *  @param queued شمارِ صفِ v3
   *  @return تصمیم و حالِ تازه برای نوشتن (`null` ⇒ هیچ)
   */
  fun plan(meta: V3Meta, node: String, restoredAt: Long, unreadable: Boolean, ledger: ShopData, queued: Int): Plan {
    if (unreadable) return Plan(RestoredDevice.Verdict.UNDECIDED, null)
    val replica = Hlc.parse(meta.clock)?.node.orEmpty()
    val kept = meta.initialized || meta.cursor > 0 || meta.shadow.isNotBlank() || meta.lastPushAt > 0 || queued > 0
    //  ردیف‌های دفتر فقط وقتی تصمیم به آن بسته است (بی بستگی و شناسهٔ ساخته‌شده روی ردِ کار) — دفترِ بزرگ هر دور شمرده نشود
    val history = kept || (restoredAt > 0 && meta.device.isBlank() && V1Replica.rowsOf(ledger) > 0)
    val verdict = RestoredDevice.decide(node, meta.device, replica, restoredAt, history)
    val next = when (verdict) {
      RestoredDevice.Verdict.UNDECIDED, RestoredDevice.Verdict.SAME -> null
      //  صفِ بی حال (صف با پشتیبان آمد، حال نه) ⇒ همان بازسازی، نه `initialize`: Snapshot پیش از صف دفتر را بی اثرِ آن
      //  opها می‌گذاشت و opِ خودِ دستگاه هرگز Pull نمی‌شود
      RestoredDevice.Verdict.BIND -> if (!meta.initialized && queued > 0) rebuild(meta, node, ledger, false) else meta.copy(device = node)
      RestoredDevice.Verdict.FENCE -> rebuild(meta, node, ledger, true)
    }
    return Plan(verdict, next)
  }

  /** پایهٔ سایه = [ledger]، cursor دور، Pull بسته تا «بازسازی از سرور» (صف اول)، بستن به [node] — یک حال */
  private fun rebuild(meta: V3Meta, node: String, ledger: ShopData, restored: Boolean): V3Meta = meta.copy(
    device = node, initialized = true, shadow = LedgerDiff.shadowOf(LedgerDiff.toJson(ledger)).toString(), cursor = 0,
    restorePending = true, restoredRebuild = restored,
  )
}
