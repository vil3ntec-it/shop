package ir.vil3ntec.tohid.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.delivery.DeskConfig
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.orders.OrderText
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.data.repo.DeliveryRepository
import ir.vil3ntec.tohid.ui.theme.Shop

/**
 *  آیا «میزِ تحویل» دیده شود؟ — فقط وقتی سرور (`/shop/delivery/config`)
 *  می‌گوید پرچمِ `shop.delivery` (و `shop.orders`) روشن است و کاربر دسترسیِ
 *  دیدن دارد. ⛔ پیش‌فرض خاموش: بی اینترنت، بی حساب یا پرچمِ خاموش ⇒ نه
 *  ردیفی در منو، نه صفحه‌ای. قطعِ گذرا بخشِ روشن را خاموش نمی‌کند.
 */
object DeliveryGate {
  val config = mutableStateOf<DeskConfig?>(null)
  val visible: Boolean get() = config.value?.let { it.readable && it.perms.view } == true

  @Volatile private var repo: DeliveryRepository? = null

  fun repository(context: Context): DeliveryRepository =
    repo ?: synchronized(this) { repo ?: DeliveryRepository(Backend.api(context)).also { repo = it } }

  suspend fun refresh(context: Context) {
    if (!Backend.isReady(context)) { config.value = null; return }
    when (val r = repository(context).config()) {
      is ApiResult.Success -> config.value = r.value.takeIf { it.readable && it.perms.view }
      is ApiResult.Failure -> if (!r.error.retryable) config.value = null
    }
  }
}

/** «۱٬۲۵۰ افغانی» */
internal fun afn(minor: Long) = StrOnline.afnAmount(OrderText.money(minor))

@Composable
internal fun DeskSection(title: String, modifier: Modifier = Modifier) {
  Text(
    title, modifier.padding(top = 6.dp, bottom = 2.dp),
    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Shop.colors.text,
  )
}
