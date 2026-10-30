package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.fillMaxWidth
import ir.vil3ntec.tohid.core.orders.StockHold
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.data.repo.CatalogReserveRepository
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch

/**
 *  M5 — «نگه‌داشتنِ کالا برای سفارشِ آنلاین» در تنظیماتِ ویترین.
 *
 *  ⛔ فقط وقتی سرور می‌گوید رزروِ نرم روشن است (`GET /shop/catalog/config` → `reserve`؛ پرچم‌های
 *  `shop.catalog` + `shop.stock_reserve`). بی آن، بی اینترنت یا بی اجازهٔ دیدنِ موجودی: هیچ (نه کادرِ بی‌اثر).
 *  ⛔ سنجش همان سنجشِ سرور (`StockHold.parseTtlMinutes` ⇒ ۶۰ تا ۸۶۴۰۰ ثانیه)؛ «ذخیره شد» فقط پس از پاسخِ
 *  سرور و با مقداری که سرور برگرداند. ذخیره اجازهٔ `settings.write` می‌خواهد؛ ردِ سرور همان پیامِ سرور است.
 */
@Composable
internal fun ReserveTtlPanel(snackbar: SnackbarHostState) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val repo = remember(context) { CatalogReserveRepository(Backend.api(context)) }
  var ttlSec by remember { mutableStateOf<Int?>(null) }
  var field by remember { mutableStateOf("") }
  var error by remember { mutableStateOf<String?>(null) }
  var saving by remember { mutableStateOf(false) }

  LaunchedEffect(Unit) {
    if (!Backend.isReady(context)) return@LaunchedEffect
    repo.config().onSuccess { cfg ->
      val r = cfg.reserve?.takeIf { cfg.enabled && it.enabled } ?: return@onSuccess
      val sec = if (StockHold.ttlSecValid(r.ttlSec.toLong())) r.ttlSec else StockHold.TTL_DEFAULT_SEC
      ttlSec = sec
      field = StockHold.minutesField(sec)
    }
  }
  val current = ttlSec ?: return

  Panel {
    Text("نگه‌داشتنِ کالا برای سفارشِ آنلاین", style = MaterialTheme.typography.titleSmall)
    Text(
      "سفارشِ آنلاین کالا را تا این مهلت برای مشتری نگه می‌دارد (پس از قبول، تا پایانِ سفارش). فروشِ حضوری هرگز " +
        "بسته نمی‌شود؛ اگر کالای نگه‌داشته را ببرد، سفارش «در بررسی» می‌رود. اکنون: ${StockHold.ttlLabel(current)}.",
      style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted,
    )
    TohidTextField(
      field, { field = it; error = null }, "مهلت (دقیقه)",
      supporting = "${StockHold.TTL_MIN_MINUTES} تا ${StockHold.TTL_MAX_MINUTES} دقیقه (۲۴ ساعت)",
      error = error,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
    TohidButton(if (saving) "در حالِ ذخیره…" else "ذخیرهٔ مهلت", onClick = {
      when (val r = StockHold.parseTtlMinutes(field)) {
        is StockHold.Ttl.Error -> error = r.message
        is StockHold.Ttl.Ok -> {
          saving = true
          scope.launch {
            repo.saveTtl(r.sec)
              .onSuccess { saved ->
                if (saved == null) {
                  //  سرور پرچم را همین حالا خاموش دید ⇒ چیزی ذخیره نشد؛ کادر می‌رود
                  ttlSec = null
                  snackbar.showSnackbar("نگه‌داشتنِ کالا برای این دکان دیگر روشن نیست؛ چیزی ذخیره نشد")
                } else {
                  ttlSec = saved
                  field = StockHold.minutesField(saved)
                  snackbar.showSnackbar("مهلتِ نگه‌داشتن ذخیره شد: ${StockHold.ttlLabel(saved)}")
                }
              }
              .onFailure { error = it.userMessage }
            saving = false
          }
        }
      }
    }, enabled = !saving && field.isNotBlank(), busy = saving, modifier = Modifier.fillMaxWidth())
  }
}
