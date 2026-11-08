package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.sync.SyncStore
import ir.vil3ntec.tohid.sync.v1.SyncV1Engine
import ir.vil3ntec.tohid.sync.v1.SyncV1Keys
import kotlinx.coroutines.launch

/**
 *  **تنها** درِ «این حساب روی این دستگاه وارد شد» — همهٔ راه‌های ورود (رمز، کدِ ایمیلی، کدِ یک‌بار، گوگل، اپل،
 *  ثبت‌نام، کدِ شاگرد، پیوستنِ حسابِ واردشده) از همین می‌گذرند.
 *
 *  ── ریشهٔ گم شدنِ داده که این می‌بندد ─────────────────────────────────────
 *  دفترِ روی میز (`LedgerOwner`) و کلیدِ همگام‌سازی (`SyncStateV1.currentAccount`) را راه‌های ورودِ مختلف
 *  جداجدا می‌نوشتند (برگهٔ کدِ ایمیلی فقط دومی را، بقیه فقط اولی را). پس از «خروج ⇒ ورود با حسابِ دیگر» یا
 *  برگشتن، دفترِ یک حساب با سایهٔ حسابِ دیگر تفاضل می‌گرفت: شاگرد دکانِ خالی می‌دید و نخستین ذخیره برای هر
 *  ردیف DELETE می‌ساخت؛ دفترِ واقعیِ صاحب در بایگانی می‌ماند. شرح سرِ `core/sync/AccountSafety.kt`.
 *  ──────────────────────────────────────────────────────────────────────────
 *
 *  ترتیب: ۱) دفترِ روی میز مالِ همین حساب@دکان (`LedgerOwner`) ۲) کلیدِ صف/سایه/cursor = شناسهٔ همین حساب
 *  (`SyncV1Keys.bind`، با آوردنِ حالِ کهنهٔ **همین** حساب) ۳) تغییرهای رمزدارِ خروجِ اجباریِ همین حساب جلوی صف
 *  (`SignOutVault`). همگام‌سازی فقط **پس از** این‌ها راه می‌افتد.
 */
object AccountBind {

  /**
   *  @param shopId دکانِ نشست؛ `null` یعنی نامعلوم (برگهٔ کدِ ایمیلی و سرور جواب نداد) ⇒ دفتر فقط وقتی
   *    جابه‌جا می‌شود که حساب عوض شده باشد؛ دکانِ واقعی را `AppRoot` با `LedgerOwner.shopChanged` می‌نشاند.
   *  @param display نامِ نمایشیِ حساب (کلیدِ کهنهٔ همگام‌سازی پیش از این اصلاح)
   */
  suspend fun signedIn(
    context: Context,
    store: ShopStore,
    userId: String,
    shopId: String?,
    display: String,
  ): LedgerOwner.Result {
    val app = context.applicationContext
    val user = userId.trim()
    if (user.isBlank()) return LedgerOwner.Result.SAME
    val before = LedgerOwner.currentUser(app)
    val result = when {
      shopId != null -> LedgerOwner.signedIn(app, store, user, shopId)
      //  همان حساب، دکانِ نامعلوم ⇒ دفتر سرِ جایش (هرگز «حساب@خالی» ِ تازه جای دفترِ دکان)
      before == user -> LedgerOwner.Result.SAME
      else -> LedgerOwner.signedIn(app, store, user, "")
    }
    val sameDesk = result == LedgerOwner.Result.SAME || (result == LedgerOwner.Result.ADOPTED && before == user)
    SyncV1Keys.bind(app, user, display, sameDesk)
    runCatching { SyncV1Engine.of(app).restoreSealedFor(user) }
    SyncStore(app).accountId = user
    return result
  }

  /**
   *  گوشیِ صاحب که با ساختِ پیشین در همان حالِ خراب ماند و هنوز وارد است (کلیدِ همگام‌سازی = صاحب، دفترِ روی
   *  میز = شاگرد، دفترِ واقعیِ صاحب در بایگانی): با نخستین باز شدنِ ساختِ تازه، حسابِ واقعیِ نشست از سرور
   *  پرسیده می‌شود (`/me`)؛ همان حسابِ کلید است ⇒ همان ورودِ [signedIn] (دفترِ شاگرد کنار، دفترِ صاحب باز و
   *  نشانِ بازیابی) و بازیابیِ بایگانی همان دم. حسابِ دیگری بود یا سرور جواب نداد ⇒ هیچ کاری.
   *  `AppRoot` با دفترِ زندهٔ صفحه صدایش می‌زند (کارگرِ پس‌زمینه نه: نمونهٔ دیگرِ دفتر، دفترِ کهنه را بازمی‌نوشت).
   */
  suspend fun healOnStart(context: Context, store: ShopStore) {
    val app = context.applicationContext
    if (store.training) return
    val keyUser = ir.vil3ntec.tohid.sync.v1.SyncStateV1.currentAccount(app)
    val deskUser = LedgerOwner.currentUser(app)
    if (keyUser.isNotBlank() && deskUser.isNotBlank() && keyUser != deskUser) {
      val me = (ir.vil3ntec.tohid.data.repo.Backend.account(app).me() as? ir.vil3ntec.tohid.core.net.ApiResult.Success)?.value
      val id = me?.user?.id.orEmpty()
      if (id.isNotBlank() && id == keyUser) signedIn(app, store, id, me?.shop?.id.orEmpty(), me?.user?.name.orEmpty())
    }
    if (SyncV1Engine.of(app).recoveryPending()) bootstrapSoon(app, store)
  }

  private val background = kotlinx.coroutines.CoroutineScope(
    kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
  )

  /**
   *  دفترِ روی میز خالی است (شاگردِ تازه، گوشیِ نو، نصبِ دوباره) ⇒ Snapshot ِ دکان **همین حالا** در پس‌زمینه
   *  (`SyncV1Engine.prepare`: دفترِ خالی ⇒ همهٔ ردیف‌های سرور، سایه و cursor با هم) و بعد دورِ عادی.
   *
   *  ⛔ صفحه منتظرش نمی‌ماند: پیش‌تر نخستین دادهٔ دکان فقط از کارگرِ زمان‌بندی‌شده می‌آمد که پیش از Snapshot
   *  سه درخواستِ دیگر (تپش، سری سند، زنجیرهٔ سند) می‌زد. `prepare` زیرِ قفلِ موتور و یک‌باره است، پس اجرای
   *  هم‌زمان با کارگر چیزی را دو بار نمی‌نشاند.
   */
  fun bootstrapSoon(context: Context, store: ShopStore) {
    val app = context.applicationContext
    val engine = SyncV1Engine.of(app)
    if (store.training) return
    background.launch {
      val read = { store.ledgerNow() }
      val write = { next: ShopData -> store.applyFromServerNow(next) }
      //  بایگانیِ کهنه ⇒ اول بازیابی؛ دفترِ خالی ⇒ Snapshot همین حالا؛ بعد تنظیماتِ نبوده از پشتیبانِ ابری
      if (engine.recoveryPending()) runCatching { engine.recoverArchived(read, write) }
      else if (store.deskEmpty()) runCatching { engine.prepare(read, write) }
      runCatching { CloudSettingsRestore.once(app, store) }
      runCatching { ir.vil3ntec.tohid.sync.v1.SyncV1Worker.now(app) }
    }
  }
}
