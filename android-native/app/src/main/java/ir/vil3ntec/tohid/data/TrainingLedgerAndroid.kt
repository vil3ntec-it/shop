package ir.vil3ntec.tohid.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 *  OPS-07 — تنظیمِ حالتِ آموزشی روی **همین دستگاه** (پروندهٔ `tohid-policy`). هر دو پیش‌فرض **خاموش**:
 *  خاموش ⇒ هیچ درِ ورودی به آموزش کشیده نمی‌شود و برنامه بایت‌به‌بایت همان امروز است.
 */
object TrainingPref {

  private const val ON = "training_mode"
  private const val STAFF = "training_staff"

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("tohid-policy", Context.MODE_PRIVATE)

  /** درِ ورود (ردیفِ تنظیمات و دکمهٔ «ورود به آموزش») نشان داده شود؟ */
  fun on(context: Context): Boolean = runCatching { prefs(context).getBoolean(ON, false) }.getOrDefault(false)

  fun setOn(context: Context, on: Boolean) {
    prefs(context).edit().putBoolean(ON, on).apply()
  }

  /** کارمند (غیرِ صاحب و مدیر) هم وارد شود؟ فقط صاحب/مدیر عوضش می‌کند (`TrainingMode.canManage`) */
  fun staff(context: Context): Boolean = runCatching { prefs(context).getBoolean(STAFF, false) }.getOrDefault(false)

  fun setStaff(context: Context, on: Boolean) {
    prefs(context).edit().putBoolean(STAFF, on).apply()
  }
}

/**
 *  OPS-07 — مرزِ دیسکِ حالتِ آموزشی: ورود، خروج، پاک کردن و دفترِ آموزشیِ فعال.
 *
 *  ورود: اول کلید روشن می‌شود (`TrainingMode.turn`) تا دفترِ واقعی و شبکه **پیش از** هر کاری بسته باشند؛ بعد
 *  `ShopStore(…, training = true)` روی `files/training/shop-data.json` باز می‌شود و اگر خالی بود دفترِ نمونه
 *  (`TrainingSample`) می‌گیرد. خروج: دفترِ فعال برمی‌گردد به واقعی، کلید خاموش، و به خواستِ کاربر پوشهٔ آموزش پاک.
 *  دفترِ واقعی در هیچ گامی خوانده و نوشته نمی‌شود — پس پس از خروج بایت‌به‌بایت همان است (`TrainingModeTest`).
 */
object TrainingLedger {

  private val _store = MutableStateFlow<ShopStore?>(null)

  /** دفترِ آموزشیِ فعال؛ `null` ⇒ دفترِ واقعی. `AppRoot` از همین می‌کشد. */
  val store: StateFlow<ShopStore?> = _store.asStateFlow()

  fun dir(filesDir: File): File = File(filesDir, TrainingMode.DIR)

  /** جای پرونده‌های کناریِ دفتر (پیش‌نویسِ سبد، فاکتورهای نگه‌داشته): در آموزش پوشهٔ جدا */
  fun filesDir(context: Context): File {
    val base = context.applicationContext.filesDir
    return if (TrainingMode.on) dir(base).apply { mkdirs() } else base
  }

  enum class Refused { OFF, ROLE }

  /** چرا این نقش الان وارد نمی‌شود؛ `null` ⇒ می‌شود */
  fun refusal(context: Context, role: String = ShopRole.current(context)): Refused? = when {
    !TrainingPref.on(context) -> Refused.OFF
    !TrainingMode.canEnter(role, TrainingPref.staff(context)) -> Refused.ROLE
    else -> null
  }

  /**
   *  ورود به آموزش. [today] و [now] فقط برای دفترِ نمونهٔ تازه (قطعی، `TrainingSample`).
   *  @return دفترِ آموزشی، یا `null` اگر تنظیم خاموش است یا نقش اجازه ندارد
   */
  suspend fun enter(context: Context, today: String, now: Long, role: String = ShopRole.current(context)): ShopStore? {
    if (refusal(context, role) != null) return null
    _store.value?.let { return it }
    //  ⛔ اول کلید: از همین لحظه دفترِ واقعی و شبکه بسته‌اند
    TrainingMode.turn(true)
    val s = ShopStore(context.applicationContext, training = true)
    s.load()
    if (!s.hasData()) s.seedTraining(TrainingSample.build(today, now))
    _store.value = s
    return s
  }

  /** خروج از آموزش؛ [wipe] ⇒ دفتر و پرونده‌های کناریِ آموزشی پاک می‌شوند (دفعهٔ بعد دفترِ نمونهٔ تازه) */
  fun exit(context: Context, wipe: Boolean) {
    _store.value = null
    TrainingMode.turn(false)
    HeldInvoices.forgetTraining()
    if (wipe) wipe(context)
  }

  /** پاک کردنِ پوشهٔ آموزش — فقط وقتی آموزش خاموش است (دفترِ باز زیرِ پا پاک نمی‌شود) */
  fun wipe(context: Context): Boolean {
    if (TrainingMode.on) return false
    val base = context.applicationContext.filesDir
    return runCatching { dir(base).deleteRecursively() }.getOrDefault(false)
  }

  /** آیا دفترِ آموزشیِ ذخیره‌شده‌ای روی دیسک هست (ورودِ بعدی همان را باز می‌کند) */
  fun hasSaved(context: Context): Boolean {
    val base = context.applicationContext.filesDir
    return File(base, TrainingMode.ledgerPath(true)).exists()
  }
}
