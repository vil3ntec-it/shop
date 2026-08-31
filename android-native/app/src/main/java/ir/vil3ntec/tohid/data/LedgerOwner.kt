package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.sync.SyncStore

/**
 *  دفترِ روی گوشی، مالِ کدام حساب است.
 *
 *  ── اشکالی که این می‌بندد ─────────────────────────────────────────
 *  سرور حساب‌ها را کاملاً جدا نگه می‌دارد؛ آن سمت درست بود. خرابی این
 *  طرف بود: روی گوشی فقط **یک** دفتر وجود داشت و به هیچ حسابی بسته
 *  نبود. خروج از حساب توکن را پاک می‌کرد و دفتر را دست‌نخورده می‌گذاشت.
 *
 *      ۱) احمد وارد می‌شود، ۵۰۰ فروش ثبت می‌کند، خارج می‌شود
 *      ۲) محمود روی همان گوشی وارد می‌شود
 *      ۳) سایه خالی است ← همگام‌سازی همهٔ ۵۰۰ فروشِ احمد را
 *         «تغییرِ تازهٔ محمود» می‌بیند
 *      ۴) همه‌شان می‌روند داخلِ دکانِ محمود
 *
 *  یک گوشیِ مشترک در دکان، یا فروختنِ گوشی، یا حتی امتحان کردنِ دو حساب
 *  کافی بود.
 *  ──────────────────────────────────────────────────────────────────
 *
 *  قاعدهٔ حالا: هر دفتر یک صاحب دارد.
 *
 *  • **حسابِ همان دفتر** دوباره وارد شود → هیچ اتفاقی نمی‌افتد.
 *  • دفتر **بی‌صاحب** باشد (کسی که آفلاین شروع کرده و حالا اولین بار
 *    وارد می‌شود) → همین دفتر به نامِ او سند می‌خورد و بالا می‌رود. این
 *    همان چیزی است که باید بشود؛ کارِ چند هفته‌اش را از دست نمی‌دهد.
 *  • **حسابِ دیگری** وارد شود → دفترِ قبلی زیرِ نامِ صاحبش بایگانی
 *    می‌شود، سایه و شمارهٔ تغییر صفر می‌شوند، و دفترِ خودِ این حساب باز
 *    می‌شود (اگر روی این گوشی سابقه داشته باشد) یا دفتری خالی.
 *
 *  هیچ داده‌ای پاک نمی‌شود: دفترِ احمد همان‌جا می‌ماند و اگر دوباره وارد
 *  شود، سرِ جایش برمی‌گردد.
 */
object LedgerOwner {

  private const val PREFS = "tohid-ledger"
  private const val OWNER = "owner_user_id"

  private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  /** شناسهٔ حسابی که دفترِ روی میز مالِ اوست — خالی یعنی بی‌صاحب */
  fun current(context: Context): String = prefs(context).getString(OWNER, "").orEmpty()

  private fun setCurrent(context: Context, userId: String) {
    prefs(context).edit().putString(OWNER, userId).apply()
  }

  /** نتیجهٔ ورود — برای اینکه صفحه بداند چه چیزی به کاربر بگوید */
  enum class Result {
    /** همان حسابِ قبلی؛ دست نخورد */
    SAME,

    /** دفترِ بی‌صاحب به نامِ این حساب خورد */
    ADOPTED,

    /** حساب عوض شد؛ دفترِ قبلی بایگانی و دفترِ این حساب باز شد */
    SWITCHED,
  }

  /**
   *  بعد از هر ورودِ موفق، پیش از اینکه همگام‌سازی راه بیفتد.
   *
   *  ترتیب مهم است: اگر `AutoSync` زودتر از این اجرا شود، همان نشتی که
   *  این تابع جلویش را می‌گیرد یک بار اتفاق افتاده است.
   */
  suspend fun signedIn(context: Context, store: ShopStore, userId: String): Result {
    val id = userId.trim()
    if (id.isBlank()) return Result.SAME          // شناسه نداریم؛ چیزی را خراب نمی‌کنیم

    val state = SyncStore(context)
    val previous = current(context)
    if (previous == id) {
      state.accountId = id                        // نسخهٔ قبل این را ثبت نمی‌کرد
      return Result.SAME
    }

    if (previous.isBlank()) {
      state.accountId = id
      setCurrent(context, id)
      return Result.ADOPTED
    }

    // حسابِ دیگری است — دفترِ قبلی می‌رود کنار
    store.stashTo(previous)
    state.forgetAccountState()
    store.openFrom(id)

    /*
     *  چیزهای دیگری هم به حسابِ قبلی بسته بودند و روی گوشی می‌ماندند:
     *
     *  • **سبدِ نیمه‌کاره** — فروشی که احمد شروع کرده و تمام نکرده بود،
     *    جلوی محمود باز می‌شد.
     *  • **کلیدِ حساب و کدِ شاگرد** — محمود کلیدِ احمد را در تنظیمات
     *    می‌دید و می‌توانست کدِ شاگردِ او را به کسی بدهد.
     *
     *  مجوزِ اشتراک را `forgetAccountState` برداشته است.
     */
    runCatching { CartStore(context).clear() }
    runCatching { AccountKeys.forget(context) }

    state.accountId = id
    setCurrent(context, id)
    return Result.SWITCHED
  }
}
