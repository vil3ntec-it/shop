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
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.WarehouseEntry
import ir.vil3ntec.tohid.ui.components.pos.PhoneCartShot
import ir.vil3ntec.tohid.ui.components.pos.PhoneCartUi
import ir.vil3ntec.tohid.ui.screens.SaleScreen
import kotlinx.coroutines.delay

/**
 *  عکس‌های صندوقِ گوشیِ نسخهٔ ۳ برای `PhonePreview` — فقط با `DevShot`.
 *
 *   • v3cart-grid / v3cart-list — کاتالوگِ کاشی / فهرست با چهار کالا در سبد (نوارِ r3)
 *   • v3cart-search — جستجو («ر»): پرفروش‌ها پنهان، نتیجه‌ها پرفروش‌ترین اول
 *   • v3cart-keypad — صفحه‌کلیدِ وزنِ کالای کیلویی
 *   • v3cart-open — سبدِ باز (r2) روی کاتالوگ
 *   • v3cart-empty — سبدِ خالی (سطح تا پایین)
 *   • v3cart-long / v3cart-long-open — نام‌های درازِ فارسی، مبلغ‌های میلیونی، قرض‌دار،
 *     یک قیمتِ دستیِ کمتر (ردیفِ «تخفیف») و −/+ِ ردیفِ اولِ باز
 *   • v3cart-scan — کادرِ اسکن
 *
 *  دفترِ نمایشی فقط ۱۱ کالای فعال دارد؛ برای دیدنِ تراکمِ واقعی (۱۲+ کاشی) چند کالای
 *  نمایشیِ دیگر **فقط در همین نسخهٔ حافظه** افزوده می‌شود. دفترِ روی دیسک دست نمی‌خورد.
 */
val PHONE_V3_CART = setOf(
  "v3cart-grid", "v3cart-list", "v3cart-search", "v3cart-keypad", "v3cart-open",
  "v3cart-empty", "v3cart-long", "v3cart-long-open", "v3cart-scan",
)

private val LONG_NAMES = listOf(
  "برنجِ هندیِ دانه‌بلندِ معطرِ سوپر باسماتی، کیسهٔ ده کیلویی",
  "روغن آفتاب‌گردانِ خالصِ درجهٔ یک، بطریِ پنج لیتریِ خانوادگی",
  "شکرِ سفیدِ تصفیه‌شدهٔ کارخانه‌ای، بستهٔ اقتصادیِ پنج کیلویی",
  "چای سبزِ ممتازِ کوهی با برگِ کامل و هلِ طبیعی، ۵۰۰ گرمی",
)
private val BIG_PRICES = listOf(4_250_000.0, 2_999_999.0, 1_875_500.0, 1_219_679.0)

private val EXTRA = listOf(
  Triple("ماکارونی ۵۰۰ گرمی", "خوراکی", "بسته") to (45.0 to 30.0),
  Triple("پنیر سفید", "خوراکی", "کیلوگرم") to (320.0 to 8.5),
  Triple("شیر یک لیتری", "نوشیدنی", "عدد") to (55.0 to 24.0),
  Triple("تخم مرغ", "خوراکی", "عدد") to (12.0 to 90.0),
  Triple("نمک یددار", "خوراکی", "بسته") to (25.0 to 40.0),
  Triple("لوبیا چیتی", "خوراکی", "کیلوگرم") to (150.0 to 12.0),
  Triple("کنسرو ماهی تن", "خوراکی", "عدد") to (110.0 to 0.0),
  Triple("بیسکویت ساقه‌طلایی", "خوراکی", "بسته") to (35.0 to 60.0),
)

private fun withExtras(d: ShopData): ShopData {
  val products = EXTRA.mapIndexed { i, (t, ps) ->
    Product("demo-x$i", name = t.first, category = t.second, unit = t.third, salePrice = ps.first, barcodes = listOf("62699${100 + i}"))
  }
  val stock = EXTRA.mapIndexedNotNull { i, (_, ps) ->
    if (ps.second <= 0) null else WarehouseEntry("demo-w$i", productId = "demo-x$i", units = ps.second)
  }
  return d.copy(products = d.products + products, warehouseEntries = d.warehouseEntries + stock)
}

@Composable
fun PhoneV3CartPreview(screen: String, store: ShopStore, data: ShopData, snackbar: SnackbarHostState) {
  key(screen) {
    val context = LocalContext.current
    val cartStore = remember { CartStore(context) }
    val long = screen.startsWith("v3cart-long")
    val picks = remember(data.products) { data.products.filter { it.active }.take(4).map { it.id } }
    val shown = remember(data, long, picks) {
      val base = withExtras(data)
      if (!long) base
      else base.copy(
        products = base.products.map { p ->
          val i = picks.indexOf(p.id)
          if (i < 0) p else p.copy(name = LONG_NAMES[i], salePrice = BIG_PRICES[i])
        },
      )
    }
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(picks) {
      PhoneCartShot.open.value = false
      PhoneCartShot.expand.value = null
      PhoneCartShot.keypad.value = null
      PhoneCartShot.search.value = null
      PhoneCartShot.list.value = screen == "v3cart-list"
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
      when (screen) {
        "v3cart-scan" -> PhoneCartUi.scanRequest.value = true
        "v3cart-search" -> { delay(300); PhoneCartShot.search.value = "ر" }
        //  برنج (کیلوگرم) — همان کالای وزنیِ دفترِ نمایشی
        "v3cart-keypad" -> { delay(300); PhoneCartShot.keypad.value = picks.firstOrNull() }
      }
      if (screen.endsWith("-open")) {
        delay(500)
        PhoneCartShot.open.value = true
        if (long) PhoneCartShot.expand.value = picks.firstOrNull()
      }
    }
    if (ready) SaleScreen(store, cartStore, shown, snackbar, onQuickSale = {}, onBack = {}, onRegisterBarcode = null)
  }
}
