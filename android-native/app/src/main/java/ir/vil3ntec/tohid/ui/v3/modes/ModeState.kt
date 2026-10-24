package ir.vil3ntec.tohid.ui.v3.modes

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import ir.vil3ntec.tohid.core.nav.AppMode
import ir.vil3ntec.tohid.core.nav.AppModes
import ir.vil3ntec.tohid.core.nav.DeepLink
import ir.vil3ntec.tohid.core.nav.ModeFacts
import ir.vil3ntec.tohid.data.ShopRole

/**
 *  حالتِ برنامه روی همین دستگاه (دکان / پیک / خرید) — انتخابِ کاربر، ماندگار و زنده.
 *
 *  نصبِ پیشین (بی انتخابِ ذخیره‌شده) همان رفتارِ امروز را می‌گیرد: نشستِ پیک ⇐ پیک،
 *  بقیه ⇐ دکان (`AppModes.resolve`). «دکان» فقط برای کسی که دکانی دارد (عضوِ دکان
 *  یا کسی که یک بار «دکان دارم» را زده/پیش‌تر وارد شده) پیدا می‌شود؛ «خرید» همیشه.
 */
object ModeState {
  private const val PREFS = "tohid"
  private const val KEY = "app_mode"
  private const val SHOP_ON = "mode_shop_on"
  private const val WELCOMED = "welcomed"

  /** انتخابِ ذخیره‌شده (null = هنوز انتخاب نشده) */
  val chosen = mutableStateOf<AppMode?>(null)

  /** پیوندی که حالتِ دیگری خواسته (QRِ دکان ⇐ خرید) و هنوز باز نشده */
  val pendingLink = mutableStateOf<DeepLink?>(null)

  /** خروج از حسابِ پیک ⇐ دروازهٔ ورود — `MainActivity` می‌گذارد */
  var onCourierSignedOut: () -> Unit = {}

  private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  fun load(context: Context) {
    chosen.value = AppMode.of(runCatching { prefs(context).getString(KEY, null) }.getOrNull())
  }

  fun facts(context: Context): ModeFacts {
    val p = runCatching { prefs(context) }.getOrNull()
    val courier = runCatching { ShopRole.isCourier(context) }.getOrDefault(false)
    val shop = !courier && (p?.getBoolean(SHOP_ON, false) == true || p?.getBoolean(WELCOMED, false) == true)
    return ModeFacts(shopMember = shop, courier = courier)
  }

  fun current(context: Context): AppMode = AppModes.resolve(chosen.value, facts(context))

  fun available(context: Context): List<AppMode> = AppModes.available(facts(context))

  /** انتخاب (اولین بار از `ModeChooser`، بعد از سوییچرِ «حساب من») */
  fun choose(context: Context, mode: AppMode) {
    chosen.value = mode
    runCatching {
      val e = prefs(context).edit().putString(KEY, mode.id)
      if (mode == AppMode.SHOP) e.putBoolean(SHOP_ON, true)
      e.apply()
    }
  }

  fun signedOutOfCourier() = onCourierSignedOut()

  /** «دکان دارم» از حالتِ خرید ⇐ دروازهٔ ورود/ساختِ دکان — `MainActivity` می‌گذارد */
  var onStartShop: () -> Unit = {}

  fun startShop(context: Context) {
    choose(context, AppMode.SHOP)
    onStartShop()
  }
}
