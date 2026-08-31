package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.sync.SyncStore

/**
 *  دفترِ روی گوشی، مالِ کدام حساب و کدام دکان است.
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
 *  ## چرا دکان هم در شناسه هست، نه فقط حساب
 *
 *  سرور نمی‌گذارد یک حساب هم‌زمان عضو دو دکان باشد؛ ولی «همیشه همان
 *  دکان» را هم تضمین نمی‌کند: اگر صاحبِ دکان شاگردی را بردارد، آن حساب
 *  می‌تواند با کدِ دکانِ دیگری وارد شود. آن‌وقت حساب عوض نشده و اگر
 *  شناسه فقط حساب بود، هیچ جابه‌جایی‌ای رخ نمی‌داد — و دفترِ دکانِ قبلی
 *  صاف می‌رفت داخلِ دکانِ تازه. همان اشکال، از درِ دیگر.
 *
 *  ## قاعده
 *
 *  • **همان حساب و همان دکان** → هیچ اتفاقی نمی‌افتد.
 *  • **دفترِ بی‌صاحب** (کسی که آفلاین شروع کرده و حالا اولین بار وارد
 *    می‌شود) → همین دفتر به نامِ او سند می‌خورد و بالا می‌رود. این همان
 *    چیزی است که باید بشود؛ کارِ چند هفته‌اش را از دست نمی‌دهد.
 *  • **حسابی که تازه دکان ساخته یا به دکانی پیوسته** (از بی‌دکان به
 *    بادکان) → باز هم سند خوردن است، نه جابه‌جایی: همان دفتر مالِ همان
 *    دکان می‌شود.
 *  • **حسابِ دیگر، یا دکانِ دیگر** → دفترِ قبلی زیرِ نامِ صاحبش بایگانی
 *    می‌شود، سایه و شمارهٔ تغییر و مجوز صفر می‌شوند، و دفترِ خودِ این
 *    حساب باز می‌شود (اگر روی این گوشی سابقه داشته باشد) یا دفتری خالی.
 *
 *  هیچ داده‌ای پاک نمی‌شود: دفترِ احمد همان‌جا می‌ماند و اگر دوباره وارد
 *  شود، سرِ جایش برمی‌گردد.
 */
object LedgerOwner {

  private const val PREFS = "tohid-ledger"
  private const val OWNER = "owner_user_id"
  private const val SHOP = "owner_shop_id"

  private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  /** شناسهٔ حسابی که دفترِ روی میز مالِ اوست — خالی یعنی بی‌صاحب */
  fun currentUser(context: Context): String = prefs(context).getString(OWNER, "").orEmpty()

  /** دکانی که دفترِ روی میز مالِ اوست — خالی یعنی هنوز دکانی نداشته */
  fun currentShop(context: Context): String = prefs(context).getString(SHOP, "").orEmpty()

  private fun remember(context: Context, userId: String, shopId: String) {
    prefs(context).edit().putString(OWNER, userId).putString(SHOP, shopId).apply()
  }

  /**
   *  نامِ بایگانی.
   *
   *  دکان در شناسه هست ولی حساب همیشه هم هست، پس دفترِ دو حساب حتی اگر
   *  به یک دکان وصل شوند هم قاطی نمی‌شود.
   */
  private fun key(userId: String, shopId: String): String =
    if (shopId.isBlank()) userId else "$userId@$shopId"

  /** نتیجهٔ ورود — برای اینکه صفحه بداند چه چیزی به کاربر بگوید */
  enum class Result {
    /** همان حساب و همان دکان؛ دست نخورد */
    SAME,

    /** دفترِ بی‌صاحب (یا بی‌دکان) به نامِ این حساب خورد */
    ADOPTED,

    /** حساب یا دکان عوض شد؛ دفترِ قبلی بایگانی و دفترِ این یکی باز شد */
    SWITCHED,
  }

  /**
   *  بعد از هر ورودِ موفق، پیش از اینکه همگام‌سازی راه بیفتد.
   *
   *  ترتیب مهم است: اگر `AutoSync` زودتر از این اجرا شود، همان نشتی که
   *  این تابع جلویش را می‌گیرد یک بار اتفاق افتاده است.
   *
   *  @param shopId اگر هنوز معلوم نیست خالی بگذارید؛ بعداً که دکان ساخته
   *    یا پیوسته شد، `shopChanged` همان را ثبت می‌کند.
   */
  suspend fun signedIn(
    context: Context,
    store: ShopStore,
    userId: String,
    shopId: String = "",
  ): Result {
    val user = userId.trim()
    if (user.isBlank()) return Result.SAME   // شناسه نداریم؛ چیزی را خراب نمی‌کنیم
    return align(context, store, user, shopId.trim())
  }

  /**
   *  وقتی همان حساب دکانش عوض می‌شود — دکان تازه ساخته یا به یکی پیوسته.
   *
   *  از بی‌دکان به بادکان، سند خوردن است. از دکانی به دکانِ دیگر،
   *  جابه‌جایی.
   */
  suspend fun shopChanged(context: Context, store: ShopStore, shopId: String): Result {
    val user = currentUser(context)
    if (user.isBlank()) return Result.SAME
    return align(context, store, user, shopId.trim())
  }

  private suspend fun align(
    context: Context,
    store: ShopStore,
    user: String,
    shop: String,
  ): Result {
    val state = SyncStore(context)
    val previousUser = currentUser(context)
    val previousShop = currentShop(context)

    //  همان حساب و همان دکان — کارِ هر روز، و هیچ کاری ندارد
    if (previousUser == user && previousShop == shop) {
      state.accountId = user                  // نسخهٔ قبل این را ثبت نمی‌کرد
      return Result.SAME
    }

    /*
     *  سند خوردن، نه جابه‌جایی — دو حالت:
     *
     *   • دفتر اصلاً صاحبی نداشت (نصبِ تازه، یا کسی که آفلاین شروع کرده)
     *   • همان حساب است و تازه صاحبِ دکان شده؛ دفتری که آفلاین ساخته،
     *     دفترِ همان دکان است. اگر اینجا جابه‌جا می‌کردیم، کارش را
     *     همان لحظه از دستش می‌گرفتیم.
     */
    val adopting = previousUser.isBlank() || (previousUser == user && previousShop.isBlank())
    if (adopting) {
      state.accountId = user
      remember(context, user, shop)
      return Result.ADOPTED
    }

    // حسابِ دیگری است، یا دکانِ دیگری — دفترِ قبلی می‌رود کنار
    val from = key(previousUser, previousShop)
    val to = key(user, shop)

    store.stashTo(from)
    state.forgetAccountState()
    store.openFrom(to)

    /*
     *  چیزهای دیگری هم به حسابِ قبلی بسته بودند و روی گوشی می‌ماندند:
     *
     *  • **نامِ دکان** — روی داشبورد، در تنظیمات، و بدتر از همه بالای
     *    **فاکتورِ چاپ‌شده**. محمود فاکتورهایش را با نامِ دکانِ احمد چاپ
     *    می‌کرد.
     *  • **عکسِ محصول‌ها** — پوشه‌شان مشترک بود و هیچ‌وقت خالی نمی‌شد.
     *  • **سبدِ نیمه‌کاره** — فروشی که احمد شروع کرده و تمام نکرده بود،
     *    جلوی محمود باز می‌شد.
     *  • **کلیدِ حساب و کدِ شاگرد** — محمود کلیدِ احمد را در تنظیمات
     *    می‌دید و می‌توانست کدِ شاگردِ او را به کسی بدهد.
     *
     *  مجوزِ اشتراک را `forgetAccountState` برداشته است؛ پس اشتراکِ
     *  خریده‌شدهٔ احمد برای محمود باز نمی‌ماند.
     *
     *  نام و عکس‌ها **بایگانی** می‌شوند، نه پاک: احمد که برگردد، دکانش
     *  دقیقاً همان‌طور که گذاشته بود برمی‌گردد.
     */
    runCatching { AccountVault.switch(context, from, to) }
    runCatching { PhotoStore.stashTo(context, from) }
    runCatching { PhotoStore.openFrom(context, to) }
    runCatching { CartStore(context).clear() }
    runCatching { AccountKeys.forget(context) }

    state.accountId = user
    remember(context, user, shop)
    return Result.SWITCHED
  }
}
