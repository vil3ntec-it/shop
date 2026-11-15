package ir.vil3ntec.tohid.update

/**
 *  «برنامه با نامِ تازه آمده» — شناسهٔ نصبِ برنامهٔ دکان از ۱۴۰۵/۰۸/۲۴ `com.vill3n.dailyshop` است (پیش از آن
 *  `ir.vil3ntec.tohid`؛ تصمیم در `docs/OWNER-DECISIONS-fa.md`). نصبی با شناسهٔ دیگر **کنارِ** برنامهٔ فعلی نصب
 *  می‌شود، نه رویش؛ دفترِ برنامهٔ فعلی به آن نمی‌رسد. پس هرگز «به‌روزرسانی» نشان داده نمی‌شود.
 *
 *   • انتشار شناسهٔ نصبیِ خودش را در `app-id.txt` دارد (ورک‌فلوی «توحید بومی» از خودِ APK می‌نویسد)؛ سرور آن را
 *     `appId` ِ `/api/releases/<برچسب>/latest` می‌دهد و راهِ GitHub همان فایل را می‌خواند.
 *   • شناسهٔ انتشار ≠ شناسهٔ همین نصب ⇒ [movedTo]: برگهٔ راهنما به‌جای «نسخهٔ تازه» (`ui/v3/shell/AppMoveSheetAndroid.kt`).
 *   • فایلِ گرفته‌شده‌ای که بسته‌اش دیگر است، حتی بی `app-id.txt`، بی برگهٔ راهنما نصب نمی‌شود (`UpdateManager.install`).
 *   • پاک کردنِ برنامهٔ قدیمی فقط وقتی پیشنهاد می‌شود که چیزی همگام‌نشده نمانده باشد ([advice])؛ کد هیچ داده‌ای پاک نمی‌کند.
 *
 *  منطقِ خالص، بی اندروید (`AppMoveTest`).
 */
object AppMove {

  /** شناسهٔ نصبِ پیشین — گوشی‌هایی که پیش از ۱۴۰۵/۰۸/۲۴ نصب کرده‌اند */
  const val LEGACY_ID = "ir.vil3ntec.tohid"

  /** شناسهٔ نصبِ تازه (پیش‌نمایش: `….preview`) */
  const val CURRENT_ID = "com.vill3n.dailyshop"

  /** فایلِ کوچکِ انتشار با شناسهٔ نصبِ همان نصبی (یک خط) */
  const val FILE = "app-id.txt"

  /** همان قاعدهٔ `server/src/routes/releases.js`: نامِ بستهٔ اندروید، دست‌کم دو تکه */
  private val PACKAGE = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")

  fun valid(id: String): Boolean = id.length <= 150 && PACKAGE.matches(id)

  /** محتوای `app-id.txt` ⇐ شناسه؛ خالی، چندتکه یا ناجور ⇒ «» (یعنی «نامعلوم» = همین برنامه، مثلِ پیش) */
  fun parseId(text: String?): String {
    val line = text?.trim()?.lineSequence()?.firstOrNull()?.trim().orEmpty()
    return line.takeIf(::valid).orEmpty()
  }

  /**
   *  شناسهٔ برنامهٔ تازه اگر انتشار مالِ برنامهٔ دیگری است؛ وگرنه «».
   *  شناسهٔ نامعلوم یا ناجور ⇒ «» (انتشارِ پیش از این تصمیم `app-id.txt` نداشت).
   */
  fun movedTo(releaseId: String, ownId: String): String =
    if (valid(releaseId) && ownId.isNotBlank() && releaseId != ownId) releaseId else ""

  /**
   *  `UpdateManager.download`: گرفتنِ نسخه بی برگهٔ راهنما فقط وقتی همین برنامه است؛ برنامهٔ دیگر فقط پس از
   *  دکمهٔ خودِ برگه برای **همان** شناسه ([confirmed]).
   */
  fun mayGet(movedTo: String, confirmed: String): Boolean = movedTo.isEmpty() || confirmed == movedTo

  /**
   *  `UpdateManager.install`: [foreign] = بستهٔ فایلِ گرفته‌شده وقتی همین برنامه نیست (`Updater.foreignPackage`).
   *  @return شناسه‌ای که باید اول برگهٔ راهنمایش دیده شود، یا null یعنی «به نصب‌کننده بده»
   */
  fun installGate(foreign: String?, movedTo: String, confirmed: String): String? = when {
    foreign == null -> null
    movedTo == foreign && confirmed == foreign -> null
    else -> foreign
  }

  /** آیا پیشنهادِ پاک کردنِ برنامهٔ قدیمی امن است */
  enum class Advice {
    /** وارد است، صف خالی است و دفتر دست‌کم یک بار کامل به سرور رسیده */
    SAFE,
    /** هنوز چیزی به سرور نرفته (یا دفتر هرگز کامل نرفته) — پاک نکنید */
    PENDING,
    /** به حسابی وصل نیست — دفترش جایی جز همین گوشی نیست؛ پاک نکنید */
    NOT_SIGNED_IN,
  }

  /**
   *  @param pending تغییرهای در صف (همان قفلِ خروج: `SignOutModel.pending`)
   *  @param everSynced همگام‌سازیِ موفق دست‌کم یک بار (`SyncStatus.lastOkAt > 0`)
   */
  fun advice(signedIn: Boolean, pending: Int, everSynced: Boolean): Advice = when {
    !signedIn -> Advice.NOT_SIGNED_IN
    pending > 0 || !everSynced -> Advice.PENDING
    else -> Advice.SAFE
  }

  /** کلیدِ «برگهٔ راهنما یک بار خودش باز شد» — برای هر شناسهٔ تازه جدا */
  fun seenKey(to: String): String = "app_move_seen_$to"
}
