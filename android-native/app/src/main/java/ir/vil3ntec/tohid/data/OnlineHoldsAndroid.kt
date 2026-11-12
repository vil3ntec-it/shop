package ir.vil3ntec.tohid.data

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.stock.OnlineHolds
import ir.vil3ntec.tohid.data.repo.Backend
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

/**
 *  POS-05 / INV-03 — مرزِ پلتفرمِ «رزروِ سفارشِ اینترنتی در صندوق»: پرسیدنِ `GET /shop/catalog/reserved` و نگه‌داشتنِ آخرین نمای
 *  هر دکان برای بی اینترنت. منطق (شمردن، مهلت، «فروشِ تحویل رسید») در `core/stock/OnlineHolds.kt`.
 *
 *  ⛔ **پیش‌فرض خاموش**: سرور فقط با `shop.catalog` و `shop.stock_reserve` ردیف می‌دهد؛ «روشن نیست»، ۴۰۳ ِ نقش (بی `orders.view`)
 *     یا ۴۰۴ ِ سرورِ پیشین ⇒ نمای دکان پاک ⇒ «قابل‌فروش» همان امروز، و پرسشِ بعد [OnlineHolds.IDLE_MS] دیرتر.
 *  ⛔ قطعِ شبکه، ۴۲۹ و ۵xx ⇒ همان آخرین نما (کهنه‌تر از `MAX_AGE_MS` خودبه‌خود شمرده نمی‌شود).
 *  ⛔ فقط خواندن: هیچ چیز در دفتر، صفِ همگام‌سازی یا موجودی نوشته نمی‌شود.
 */
object OnlineHoldsStore {

  private const val PREFS = "tohid-online-holds"

  /** (دکان، نما) ِ آخرین خواندن؛ دکانِ خالی ⇒ هنوز خوانده نشده */
  val state = mutableStateOf<Pair<String, OnlineHolds.Snapshot?>>("" to null)

  private val asked = HashMap<String, Long>()
  private val mutex = Mutex()

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  /** نمای دکانِ فعال: از حافظه، وگرنه از دیسک */
  fun current(context: Context): OnlineHolds.Snapshot? {
    val shop = LedgerOwner.currentShop(context)
    val (seen, snap) = state.value
    if (shop.isNotBlank() && seen == shop) return snap
    val loaded = if (shop.isBlank()) null else OnlineHolds.decode(runCatching { prefs(context).getString(shop, null) }.getOrNull())
    state.value = shop to loaded
    return loaded
  }

  /** از سرور بپرس (نه زودتر از [OnlineHolds.REFRESH_MS]، یا [OnlineHolds.IDLE_MS] وقتی خاموش است) */
  suspend fun refresh(context: Context, now: Long = System.currentTimeMillis()) = mutex.withLock {
    val app = context.applicationContext
    val shop = LedgerOwner.currentShop(app)
    val old = current(app)
    if (shop.isBlank() || !Backend.isReady(app) || !TrainingMode.networkAllowed()) return@withLock
    if (!OnlineHolds.due(asked[shop], now, on = old != null)) return@withLock
    asked[shop] = now
    try {
      save(app, shop, OnlineHolds.parse(Backend.api(app).get(OnlineHolds.PATH), now))
    } catch (c: CancellationException) {
      throw c
    } catch (f: Exception) {
      if (!keepsOnFailure(f)) save(app, shop, null)
    }
  }

  /** گذرا (قطعِ شبکه، ۴۲۹، ۵xx) ⇒ آخرین نما می‌ماند؛ ماندگار (۴۰۳ ِ نقش یا کاتالوگ، ۴۰۴ ِ سرورِ پیشین، پاسخِ ناجور) ⇒ پاک */
  fun keepsOnFailure(f: Throwable): Boolean =
    f is ApiFailure && (f.retryable || f is ApiFailure.RateLimited || f is ApiFailure.ServerFault)

  private fun save(app: Context, shop: String, snap: OnlineHolds.Snapshot?) {
    runCatching {
      val e = prefs(app).edit()
      if (snap == null) e.remove(shop) else e.putString(shop, OnlineHolds.encode(snap))
      e.apply()
    }
    if (LedgerOwner.currentShop(app) == shop) state.value = shop to snap
  }
}
