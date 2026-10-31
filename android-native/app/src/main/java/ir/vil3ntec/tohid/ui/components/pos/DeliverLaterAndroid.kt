package ir.vil3ntec.tohid.ui.components.pos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.data.DeliverLaterCart
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.OrdersLive

/**
 *  POS-38 — روشن/خاموشِ «فروش با تحویلِ بعدی» روی همین دستگاه (`tohid-ui:deliver_later`)؛ فایلِ مرزیِ
 *  پلتفرم (`SharedPreferences`). پیش‌فرض **خاموش**: صندوق همان پیشین است و هیچ دکمه‌ای نمی‌آید.
 */
object DeliverLaterPrefs {
  val on = mutableStateOf(false)
  private var loaded = false
  private const val KEY = "deliver_later"

  fun load(context: android.content.Context) {
    if (loaded) return
    loaded = true
    on.value = context.getSharedPreferences("tohid-ui", 0).getBoolean(KEY, false)
  }

  fun set(context: android.content.Context, value: Boolean) {
    on.value = value
    context.getSharedPreferences("tohid-ui", 0).edit().putBoolean(KEY, value).apply()
  }
}

/**
 *  دکمهٔ «تحویلِ بعدی» دیده شود؟ — تنظیمِ همین دستگاه روشن، بخشِ سفارش‌ها برای دکان روشن
 *  (`OrdersLive.config`: پرچمِ `shop.orders` و اشتراک) و اجازهٔ `orders.create` برای همین کاربر.
 *  سرور همان‌ها را دوباره می‌سنجد؛ این فقط دکمهٔ بی‌کار را پنهان می‌کند.
 */
@Composable
fun deliverLaterReady(): Boolean {
  DeliverLaterPrefs.load(LocalContext.current)
  val cfg = OrdersLive.config.value ?: return false
  return DeliverLaterPrefs.on.value && cfg.enabled && cfg.permissions.create
}

/** پنجره با مخزنِ سفارشِ همین برنامه — مرزِ پلتفرم، تا صفحهٔ صندوق `LocalContext` ِ تازه نگیرد */
@Composable
fun DeliverLaterSheetWired(
  d: ShopData,
  cart: List<SalesEngine.CartLine>,
  onClose: () -> Unit,
  onCreated: (OrderDto) -> Unit,
) {
  val context = LocalContext.current
  DeliverLaterSheet(
    repo = OrdersLive.repository(context),
    lines = DeliverLaterCart.lines(d, cart),
    onClose = onClose,
    onCreated = onCreated,
  )
}

/** بخشِ تنظیمات با خواندن و نوشتنِ همین تنظیم — تا `Settings` یک خط بخواهد */
@Composable
fun DeliverLaterSettingsWired(context: android.content.Context) {
  DeliverLaterPrefs.load(context)
  DeliverLaterSettings(DeliverLaterPrefs.on.value) { DeliverLaterPrefs.set(context, it) }
}
