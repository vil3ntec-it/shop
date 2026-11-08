package ir.vil3ntec.tohid.sync.v3

import ir.vil3ntec.tohid.core.sync.ReplayBook
import java.io.File

/**
 *  جای Sync v3 ِ هر حساب روی دیسک — DATA-06، همان قاعدهٔ v1:
 *   • **حالِ همین دستگاه** در `filesDir/sync-v3/<کلید>/` ([of]): `state.json` (cursor، سایه، ساعتِ HLC، بستگی)،
 *     `journal.json` (ژورنالِ `SyncCommit` که سایه را جلو می‌برد) و دفترچهٔ §۹.۵ (`replay-sent.jsonl`). پوشهٔ [DIR] از هر سه
 *     راهِ پشتیبانِ اندروید بیرون است (`backup_rules.xml` ابری و انتقال، `backup_rules_legacy.xml`،
 *     `TohidBackupAgent.EXCLUDED_DIRS`؛ با رمزِ دفتر `AtRestScope.syncState`) — مثلِ `tohid-sync-v1` و `sync-shadow/`:
 *     cursor ِ گوشیِ پیشین روی گوشیِ دوم opهای خودِ آن را دوباره می‌نشاند.
 *   • **صف** (`queue.json`: نرفته‌ها و ردشده‌ها) همان‌جای پیشین، `filesDir/sync-v3-<کلید>/` ([queueDir]) — با پشتیبان می‌رود،
 *     مثلِ `oplog-*` ِ v1: گوشیِ دوم آن را **اول** می‌فرستد (همان op_idها ⇒ سرور «تکراری»)، پس تغییرِ نرفتهٔ گوشیِ از کار
 *     افتاده گم نمی‌شود (`V3Fence`).
 *  ساخت‌های پیشین همه را در [queueDir] می‌گذاشتند (و `exclude` ِ اندروید الگو نمی‌پذیرد) ⇒ هر فایلِ حال در نخستین خواندن
 *  به [of] می‌رود ([file]). حالی که با این همه باز هم از دستگاهِ دیگر برسد (پشتیبانِ کهنه، رونوشتِ خام) را `V3Fence` می‌گیرد.
 */
internal object V3StateDir {
  /** همین نام در `backup_rules*.xml`، `TohidBackupAgent` و `AtRestScope.syncState` */
  const val DIR = "sync-v3"
  /** پوشهٔ صفِ هر حساب (و همهٔ حالِ ساخت‌های پیشین): `filesDir/sync-v3-<کلید>` */
  const val QUEUE_PREFIX = "sync-v3-"
  const val QUEUE = "queue.json"
  const val META = "state.json"
  const val JOURNAL = "journal.json"
  /** دفترچهٔ §۹.۵ (`FileReplayStore.inDir(dir, "replay")`) */
  val REPLAY: String = ReplayBook.fileName("replay")

  /** پوشهٔ حالِ [fileKey] (`OpQueue.fileKey`) — بیرون از پشتیبان */
  fun of(filesDir: File, fileKey: String): File = File(File(filesDir, DIR), fileKey)

  /** پوشهٔ صفِ [fileKey] — با پشتیبان */
  fun queueDir(filesDir: File, fileKey: String): File = File(filesDir, QUEUE_PREFIX + fileKey)

  /** همان دو پوشه برای [FileV3Store] */
  fun store(filesDir: File, fileKey: String): FileV3Store = FileV3Store(of(filesDir, fileKey), queueDir(filesDir, fileKey))

  /**
   *  فایلِ حالِ [name] در [state]، با کوچِ یک‌باره از [legacy] (جای ساختِ پیشین): یک `rename` ِ همان دیسک (اتمی) — برق
   *  میانِ کار ⇒ یا هنوز جای پیشین یا جای تازه، هرگز نیمه؛ هر فایل جدا، پس فایلِ ماندهٔ کوچِ نیمه‌کاره بارِ بعد می‌رود.
   *  جای تازه هست ⇒ همان (فایلِ جای پیشین دست نمی‌خورد و پاک نمی‌شود). rename نشد ⇒ جای پیشین برای این اجرا.
   */
  fun file(state: File, legacy: File, name: String): File {
    val here = File(state, name)
    val old = File(legacy, name)
    if (here.exists() || !old.isFile) return here
    state.mkdirs()
    return if (old.renameTo(here)) here else old
  }
}
