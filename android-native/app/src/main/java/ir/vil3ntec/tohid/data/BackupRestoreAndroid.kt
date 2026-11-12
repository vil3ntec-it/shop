package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.core.atrest.AtRestException
import ir.vil3ntec.tohid.core.backup.BackupStamp
import ir.vil3ntec.tohid.core.i18n.StrAppData
import ir.vil3ntec.tohid.core.i18n.StrBackupSafety
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 *  OPS-02 — راهِ واحدِ پشتیبانِ کامل و بازیابی، همان که صفحه‌ها (گوشیِ نسخهٔ ۳ و کامپیوتر: `BackupController`؛
 *  تنظیماتِ قدیم) صدا می‌زنند و `BackupRestoreDrillTest` سرتاسری می‌آزماید: نوشتن ⇐ پاک شدنِ دادهٔ برنامه ⇐
 *  برگرداندن ⇐ دفتر و تنظیمات بایت‌به‌بایت همان.
 *
 *  ── قاعده‌ها ─────────────────────────────────────────────────────────
 *   • [open] پیش از برگهٔ تأیید: ZIP ِ ناقص (بریده سرِ هر بایتی) یا خراب ⇒ رد با پیامِ روشن (`BackupBundle.read`)؛
 *     قالبِ تازه‌تر ⇒ «به‌روز کنید»؛ پشتیبانِ دکانِ دیگر (`BackupStamp.judge` با صاحبِ دفترِ روی میز، همان قاعدهٔ
 *     `LedgerOwner`) ⇒ رد. هیچ‌کدام دفتر، عکس یا تنظیمی را نمی‌نویسد؛ پوشهٔ موقت پاک می‌شود.
 *   • [confirm] پس از تأیید: نسخهٔ ایمنی ⇐ دفتر (`ShopStore.restoreBackup`، راه از `RestoreRoute`: هرگز op
 *     نمی‌سازد، صف دست نمی‌خورد) ⇐ **فقط اگر نشست** عکس‌ها، تنظیماتِ کنار گذاشته و فاکتورهای نگه‌داشته
 *     (`DeviceDocsBundle.commitHeld`). پیش از این عکس‌ها و تنظیمات
 *     **پیش از** دفتر می‌نشستند: بازیابیِ ردشده («اول وارد شوید»، «اول همگام کنید»، صفِ نرفته) عکسِ تازه‌ترِ کالا
 *     را با عکسِ کهنهٔ پشتیبان رونویسی می‌کرد.
 *   • [openSnapshot]: نسخهٔ خودکار، ساده، رمزدار با کلیدِ دستگاه، یا رمزدار با عبارت (`SnapshotSeal`)؛ پوشهٔ
 *     موقتِ ZIP ِ پیشین پیش از آن پاک می‌شود (وگرنه عکس و تنظیمِ آن ZIP با دفترِ این نسخه می‌نشست).
 *   • [decode] (= `ShopStore.parseBackup`): متنِ پشتیبان ⇒ دفتر، با پیامِ روشن برای JSON ِ بریده، قالبِ تازه‌تر و
 *     فایلِ بیگانه؛ خروجیِ کاملِ مالک (OPS-08) با خوانندهٔ خودش (`formatVersion` ِ تازه‌تر رد).
 */
object BackupRestore {

  /** همان تنظیمِ `ShopStore` */
  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; isLenient = true }

  /**
   *  فایلی که JSON درستی باشد ولی مالِ این برنامه نباشد همه‌جا خالی می‌خواند و بی‌صدا دفتر را پاک می‌کرد؛ پس
   *  پشتیبانِ واقعی دستِ‌کم یکی از فهرست‌هایش پر است، یا صریحاً مُهرِ `exportedAt` دارد.
   */
  fun decode(raw: String): ShopData {
    //  JSON ِ بریده یا بی‌معنی ⇒ پیامِ روشن (پیش از این متنِ فنیِ `kotlinx.serialization` به صفحه می‌رسید)
    val tree = runCatching { json.parseToJsonElement(raw) }.getOrNull() as? JsonObject ?: error(StrBackupSafety.damaged)
    //  خروجیِ کاملِ مالک: `formatVersion` ِ تازه‌تر رد می‌شود، نه نیمه‌خوانده (پیش از این بی‌صدا نیمه‌خوانده می‌نشست)
    if ((tree["format"] as? JsonPrimitive)?.content == OwnerExport.FORMAT) {
      return runCatching { OwnerExport.parse(raw).data }
        .getOrElse { e -> if (e is IllegalStateException && !e.message.isNullOrBlank()) throw e else error(StrBackupSafety.newerApp) }
    }
    //  JSON ِ درست ولی نه دفترِ این نسخه — همان `LedgerRecovery.Damage.NEWER_FORMAT`
    val parsed = runCatching { json.decodeFromJsonElement(ShopData.serializer(), tree) }.getOrElse { error(StrBackupSafety.newerApp) }
    if (!tree.containsKey("exportedAt") && ShopStore.backupSize(parsed) == 0) error(StrAppData.notOurBackup)
    return parsed
  }

  /**
   *  پشتیبانِ کامل (مُهر، دفتر، تنظیماتِ دکان، عکس‌ها). دفتر **پیش از** باز کردنِ مقصد خوانده می‌شود: دفترِ خوانده‌نشده
   *  (`ShopStore.exportBackup` پرتاب) فایلِ خالی‌ای جا نمی‌گذارد. @return شمارِ عکس‌ها
   */
  suspend fun writeFull(context: Context, store: ShopStore, storeName: String, open: () -> OutputStream): Int {
    val ledger = store.exportBackup(storeName)
    //  OPS-02: «رمزِ پشتیبانِ دستی و پوشه» روشن و کلید باز نشد ⇒ پرتاب پیش از باز کردنِ مقصد (`BackupSeal.sealer`)
    val seal = BackupSeal.sealer(context)
    return withContext(Dispatchers.IO) {
      open().use { raw -> BackupBundle.write(context, seal?.invoke(raw) ?: raw, ledger).getOrThrow() }
    }
  }

  /**
   *  خواندن و سنجیدنِ فایلِ برگزیده — هنوز هیچ چیزی ننشسته. فایلِ رمزدار با عبارت (`BackupSeal`): کلیدِ همین دستگاه یا
   *  [typed]؛ نبودِ هر دو ⇒ [BackupSeal.needsPhrase].
   */
  fun open(context: Context, store: ShopStore, input: InputStream, typed: String? = null): Result<BackupBundle.Opened> =
    runCatching { BackupSeal.unwrap(context, input, typed) }
      .mapCatching { plain -> BackupBundle.read(context, plain) { store.parseBackup(it) }.getOrThrow() }
      .mapCatching { o ->
        when (BackupStamp.judge(o.stamp, LedgerOwner.currentUser(context), LedgerOwner.currentShop(context))) {
          BackupStamp.Verdict.OK -> o
          BackupStamp.Verdict.NEWER_FORMAT -> error(StrBackupSafety.newerApp)
          BackupStamp.Verdict.OTHER_SHOP -> error(StrBackupSafety.otherShop)
        }
      }
      .onFailure { BackupBundle.dropStaging(context) }

  /**
   *  نسخهٔ خودکار ⇒ دفتر. @param typed عبارتی که کاربر برای نسخهٔ رمزدارِ بی‌کلید نوشت
   *  @see needsPhrase
   */
  fun openSnapshot(context: Context, store: ShopStore, file: File, typed: String? = null): Result<ShopData> = runCatching {
    BackupBundle.dropStaging(context)
    val text = String(SnapshotSeal.readBytes(context, file, typed), Charsets.UTF_8)
    store.parseBackup(text).getOrThrow()
  }

  /** نسخه با عبارت رمز شده و کلیدش روی این دستگاه نیست ⇒ صفحه عبارت را می‌پرسد */
  fun needsPhrase(error: Throwable?, file: File): Boolean =
    error is AtRestException && error.kind == AtRestException.Kind.KEY_MISSING && SnapshotSeal.isPhraseSealed(file)

  data class Confirmed(val ok: Boolean, val photos: Int, val message: String)

  /** پس از تأییدِ کاربر — شرحِ ترتیب سرِ همین پرونده */
  suspend fun confirm(context: Context, store: ShopStore, next: ShopData): Confirmed {
    store.keepSafetyCopy()
    //  پرچمِ `photo` با عکس‌هایی که پس از نشستن خواهند بود (روی دیسک ∪ کنار گذاشته)
    val fixed = withContext(Dispatchers.IO) { BackupBundle.reconcilePhotoFlags(context, next, staged = true) }
    val r = store.restoreBackup(fixed)
    if (!r.ok) {
      withContext(Dispatchers.IO) { BackupBundle.dropStaging(context) }
      return Confirmed(false, 0, r.message)
    }
    val moved = withContext(Dispatchers.IO) { BackupBundle.commitPhotos(context) }
    //  DATA-SAFETY §۸: نگه‌داشته‌ها و سبدِ جاری — فقط پس از نشستنِ دفتر (بالا) و با همان دفتر (فروخته‌ها نه)
    withContext(Dispatchers.IO) { DeviceDocsBundle.commitHeld(context, store.data.value) }
    return Confirmed(true, moved, r.message)
  }
}
