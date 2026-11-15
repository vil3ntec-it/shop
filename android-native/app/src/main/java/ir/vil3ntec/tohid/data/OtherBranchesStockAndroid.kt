package ir.vil3ntec.tohid.data

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.stock.BranchStock
import ir.vil3ntec.tohid.data.repo.Backend
import kotlin.coroutines.cancellation.CancellationException

/**
 *  POS-16/S17 — مرزِ پلتفرمِ «موجودیِ همین کالا در شعبه‌های دیگر» برای قیمت‌پرسی (گوشی و کامپیوتر؛ هسته: `core/stock/BranchStock`).
 *
 *  ⛔ پیش‌فرض خاموش و فقط‌خواندنی: پرچمِ `shop.branches` (یا `shop.catalog`) خاموش ⇒ سرور همان درِ ناشناخته ⇒ هیچ سطری، و
 *     برای همان دکان تا [BranchStock.OFF_RECHECK_MS] دوباره پرسیده نمی‌شود. بی سرور، حالتِ آموزش یا هر خطا ⇒ هیچ (کارتِ
 *     قیمت‌پرسی همان امروز). کدام شعبه‌ها و چه عددی را فقط سرور می‌گوید (دسترسی و مجوزِ `stock.view_other_branches`).
 */
object OtherBranchesStock {

  private val offAt = java.util.concurrent.ConcurrentHashMap<String, Long>()

  suspend fun of(context: Context, productId: String, now: Long = System.currentTimeMillis()): List<BranchStock.Qty> {
    val app = context.applicationContext
    val shop = LedgerOwner.currentShop(app).ifBlank { "-" }
    if (BranchStock.nextAsk(offAt[shop] ?: 0L, now) > 0L) return emptyList()
    if (!Backend.isReady(app) || !TrainingMode.networkAllowed()) return emptyList()
    return try {
      BranchStock.parse(Backend.api(app).get(BranchStock.path(productId)))
    } catch (c: CancellationException) {
      throw c
    } catch (f: ApiFailure) {
      if (BranchStock.off(f.status, f.code)) offAt[shop] = now
      emptyList()
    } catch (e: Exception) {
      emptyList()
    }
  }
}

/** برای کارتِ قیمت‌پرسی: کالای [productId] (null ⇒ هیچ) ⇒ ردیف‌های شعبه‌های دیگر، پس از پاسخِ سرور */
@Composable
fun rememberOtherBranches(productId: String?): List<BranchStock.Qty> {
  val context = LocalContext.current
  val rows by produceState(emptyList<BranchStock.Qty>(), productId) {
    value = emptyList()
    if (productId != null) value = OtherBranchesStock.of(context, productId)
  }
  return rows
}
