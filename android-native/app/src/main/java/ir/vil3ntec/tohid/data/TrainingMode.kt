package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.i18n.StrLedger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 *  OPS-07 — حالتِ آموزشی: **تنها کلیدِ** «آموزش روشن است؟» برای کلِ برنامه.
 *
 *  ── چرا یک کلید ───────────────────────────────────────────────────────
 *  هر راهی که به دفترِ واقعی یا شبکه می‌رسد همین را می‌پرسد، نه پرچمِ خودش:
 *   - `ShopStore`: دفترِ واقعی در حالتِ آموزشی **هیچ** نوشتنی نمی‌گیرد (`writeToDisk`، بازیابی، بازسازی،
 *     جابه‌جاییِ حساب)؛ دفترِ آموزشی (`ShopStore(…, training = true)`) فایلِ جدای `training/shop-data.json`
 *     است و هرگز op، صفِ همگام‌سازی، خلاصه یا sweep نمی‌سازد.
 *   - `HttpEngine`: هیچ درخواستی (همگام‌سازی، فرستادن، پشتیبانِ ابری…) پیش از ساختنِ نشانی؛ `LiveSocket` وصل نمی‌شود.
 *   - `AutoSync`، `SyncV1Worker`، `SyncV3Runner.work`: هیچ دورِ همگام‌سازی؛ دفترِ آموزشی هرگز به موتور نمی‌رسد.
 *   - `DocSeries`/`DocChainSync`: سریِ سندِ واقعیِ دستگاه و زنجیره‌اش جلو نمی‌رود.
 *   - `Receipts`: هر فاکتور و رسیدِ برگشت مهرِ [WATERMARK] می‌گیرد و QR ِ عمومی ندارد.
 *
 *  ⛔ عمداً روی دیسک نمی‌ماند: بسته و باز کردنِ برنامه یعنی دفترِ واقعی. دفترِ آموزشی تا «خروج با پاک کردن»
 *  روی دیسک می‌ماند و دفعهٔ بعد همان باز می‌شود.
 *
 *  منطقِ خالص (بی دیسک و بی سکو): آزمونِ `TrainingModeTest`. مرزِ دیسک و تنظیم: `TrainingLedgerAndroid.kt`.
 */
object TrainingMode {

  /** پوشهٔ جدا زیرِ `filesDir` — دفتر، پیش‌نویسِ سبد و فاکتورهای نگه‌داشتهٔ آموزشی */
  const val DIR = "training"

  /** همان نامِ دفترِ واقعی، ولی در [DIR] */
  const val LEDGER = "shop-data.json"

  /** مهرِ فاکتور و رسید — `Receipts` */
  val WATERMARK: String get() = StrLedger.trainingMark

  /** کدِ خطای `ApiFailure` وقتی درخواستی در حالتِ آموزشی خواسته شد */
  const val NETWORK_CODE = "training_mode"

  private val _active = MutableStateFlow(false)

  /** رابط از همین می‌کشد (نوار و دفترِ فعال در `AppRoot`) */
  val active: StateFlow<Boolean> = _active.asStateFlow()

  val on: Boolean get() = _active.value

  /** فقط از `TrainingLedger.enter/exit` — هیچ‌جای دیگر */
  internal fun turn(on: Boolean) { _active.value = on }

  /** نامِ نسبیِ دفتر زیرِ `filesDir`: آموزشی ⇒ `training/shop-data.json`، واقعی ⇒ `shop-data.json` */
  fun ledgerPath(training: Boolean): String = if (training) "$DIR/$LEDGER" else LEDGER

  /**
   *  آیا این دفتر الان نوشتنی است؟ دفترِ آموزشی همیشه (جدا از واقعی است)؛ دفترِ واقعی فقط با آموزشِ خاموش.
   */
  fun writable(training: Boolean): Boolean = training || !on

  /** آیا درخواستِ شبکه یا دورِ همگام‌سازی مجاز است؟ */
  fun networkAllowed(): Boolean = !on

  /** مهرِ رسید: خاموش ⇒ همان [mark] ِ امروز، بایت‌به‌بایت؛ روشن ⇒ «آموزشی» (و مهرِ پیشین کنارش) */
  fun watermark(mark: String?): String? {
    if (!on) return mark
    val rest = mark?.trim().orEmpty()
    return if (rest.isEmpty()) WATERMARK else "$WATERMARK · $rest"
  }

  /**
   *  چه کسی واردِ آموزش می‌شود: صاحب و مدیر همیشه؛ بقیه فقط اگر صاحب/مدیر «کارمند هم» را روشن کرده باشد.
   *  نقشِ خالی (دکانِ بی حساب، همان صاحب) مثلِ صاحب. پیک هرگز (دفترِ دکان ندارد).
   */
  fun canEnter(role: String, staffAllowed: Boolean): Boolean = when (role) {
    "", ShopRole.OWNER, ShopRole.MANAGER -> true
    ShopRole.COURIER -> false
    else -> staffAllowed
  }

  /** فقط صاحب و مدیر تنظیمِ «کارمند هم وارد شود» را عوض می‌کنند */
  fun canManage(role: String): Boolean = role == "" || role == ShopRole.OWNER || role == ShopRole.MANAGER
}
