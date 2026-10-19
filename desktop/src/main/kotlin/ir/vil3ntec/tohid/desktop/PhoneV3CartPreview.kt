package ir.vil3ntec.tohid.desktop

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.data.CartStore
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.ui.components.pos.PhoneCartShot
import ir.vil3ntec.tohid.ui.components.pos.PhoneCartUi
import ir.vil3ntec.tohid.ui.screens.SaleScreen
import kotlinx.coroutines.delay

/**
 *  عکس‌های صندوقِ گوشیِ نسخهٔ ۳ (r3 بسته، r2 باز) برای `PhonePreview` — فقط با `DevShot`.
 *
 *   • v3cart-closed / v3cart-open — چهار کالای اولِ دفترِ نمایشی (اولی دو تا)
 *   • v3cart-empty — سبدِ خالی (سطحِ کالاها تا پایین)
 *   • v3cart-long / v3cart-long-open — نام‌های درازِ فارسی، مبلغ‌های میلیونی، قرض‌دارِ
 *     انتخاب‌شده، یک قیمتِ دستیِ کمتر (ردیفِ «تخفیف») و −/+ِ ردیفِ اولِ باز
 *   • v3cart-scan — کادرِ اسکن زیرِ سربرگ
 *
 *  دفترِ واقعی دست نمی‌خورد: نام و قیمتِ درازِ «long» فقط در همین نسخهٔ نمایشیِ حافظه است.
 */
val PHONE_V3_CART = setOf("v3cart-closed", "v3cart-open", "v3cart-empty", "v3cart-long", "v3cart-long-open", "v3cart-scan")

private val LONG_NAMES = listOf(
  "برنجِ هندیِ دانه‌بلندِ معطرِ سوپر باسماتی، کیسهٔ ده کیلویی",
  "روغن آفتاب‌گردانِ خالصِ درجهٔ یک، بطریِ پنج لیتریِ خانوادگی",
  "شکرِ سفیدِ تصفیه‌شدهٔ کارخانه‌ای، بستهٔ اقتصادیِ پنج کیلویی",
  "چای سبزِ ممتازِ کوهی با برگِ کامل و هلِ طبیعی، ۵۰۰ گرمی",
)
private val BIG_PRICES = listOf(4_250_000.0, 2_999_999.0, 1_875_500.0, 1_219_679.0)

@Composable
fun PhoneV3CartPreview(screen: String, store: ShopStore, data: ShopData, snackbar: SnackbarHostState) {
  key(screen) {
    val context = LocalContext.current
    val cartStore = remember { CartStore(context) }
    val long = screen.startsWith("v3cart-long")
    val picks = remember(data.products) { data.products.filter { it.active }.take(4).map { it.id } }
    val shown = remember(data, long, picks) {
      if (!long) data
      else data.copy(
        products = data.products.map { p ->
          val i = picks.indexOf(p.id)
          if (i < 0) p else p.copy(name = LONG_NAMES[i], salePrice = BIG_PRICES[i])
        },
      )
    }
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(picks) {
      PhoneCartShot.open.value = false
      PhoneCartShot.expand.value = null
      cartStore.set(
        if (screen == "v3cart-empty") emptyList()
        else picks.mapIndexed { i, id ->
          //  long: دومی با قیمتِ دستیِ کمتر ⇐ ردیفِ «تخفیف»
          val manual = if (long && i == 1) BIG_PRICES[1] - 99_999.0 else null
          SalesEngine.CartLine(id, if (i == 0) 2.0 else 1.0, unitPrice = manual)
        },
      )
      cartStore.setDebtor(if (long) data.debtors.firstOrNull()?.id else null)
      ready = true
      if (screen == "v3cart-scan") PhoneCartUi.scanRequest.value = true
      if (screen.endsWith("-open")) {
        delay(500)
        PhoneCartShot.open.value = true
        if (long) PhoneCartShot.expand.value = picks.firstOrNull()
      }
    }
    if (ready) SaleScreen(store, cartStore, shown, snackbar, onQuickSale = {}, onBack = {}, onRegisterBarcode = null)
  }
}
