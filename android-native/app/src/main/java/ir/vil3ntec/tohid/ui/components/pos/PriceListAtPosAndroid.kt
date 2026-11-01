package ir.vil3ntec.tohid.ui.components.pos

import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.sale.PriceList
import ir.vil3ntec.tohid.data.CartStore
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.screens.SegmentedChoice
import ir.vil3ntec.tohid.ui.screens.SettingsSection
import ir.vil3ntec.tohid.ui.theme.Shop

/** عنوانِ بخشِ تنظیمِ لیستِ قیمت — همان که ستونِ تنظیماتِ کامپیوتر (`DeskSettings` ِ `AppRoot`) نشان می‌دهد */
const val PRICE_LIST_SETTINGS_TITLE = "لیستِ قیمت در صندوق (خرده/عمده)"

/*
 *  لیستِ قیمتِ فاکتور در صندوق — POS-18 (§۶.۱). همهٔ قاعده در `core/sale/PriceList`؛ این‌جا فقط مرزِ سکو
 *  (تنظیم، `CartStore`) و یک نشانِ قابلِ زدن.
 *
 *  ⛔ پیش‌فرض **خاموش** (`tohid-policy` ‹`price_lists`›، همین دستگاه): خاموش ⇒ هیچ چیز دیده نمی‌شود و سبد
 *  دست نمی‌خورد. روشن ⇒ انتخابِ مشتری لیستِ پیش‌فرضِ او را می‌نشاند (مگر صندوق‌دار لیست را خودش زده باشد)،
 *  هر قلمِ تازه با لیستِ فاکتور قیمت می‌خورد و فیِ دستی (نه خرده، نه عمده) دست نمی‌خورد. حذفِ ردیف نیست،
 *  پس سابقهٔ ضدتقلب (`FraudTrail.removed`، فقط شناسهٔ کالا) چیزی نمی‌نویسد. تخفیف‌ها از همان سبد حساب می‌شوند.
 */
object PriceListPrefs {
  private const val KEY = "price_lists"
  private fun prefs(context: Context) = context.getSharedPreferences("tohid-policy", Context.MODE_PRIVATE)
  fun on(context: Context): Boolean = prefs(context).getBoolean(KEY, false)
  fun set(context: Context, on: Boolean) { prefs(context).edit().putBoolean(KEY, on).apply() }
}

/** سبد ⇐ لیست: فقط ردیفِ کالادار و غیرِدستی؛ بی تغییر ⇒ همان شیء */
internal fun repriceCart(d: ShopData, lines: List<SalesEngine.CartLine>, list: String): List<SalesEngine.CartLine> {
  val byId = d.products.associateBy { it.id }
  var changed = false
  val out = lines.map { l ->
    val p = byId[l.productId]
    if (l.free || p == null) return@map l
    val prices = PriceList.Prices(Math.round(p.salePrice * 100.0), Math.round(p.wholesalePrice * 100.0))
    val unit = PriceList.cartUnit(prices, l.unitPrice?.let { Math.round(it * 100.0) }, list)?.let { it / 100.0 }
    if (unit == l.unitPrice) l else { changed = true; l.copy(unitPrice = unit) }
  }
  return if (changed) out else lines
}

@Composable
fun PriceListAtPos(cartStore: CartStore, d: ShopData, cart: List<SalesEngine.CartLine>, debtorId: String?, modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val on = remember { PriceListPrefs.on(context) }
  if (!on) return
  fun customerList(): String? = debtorId?.let { id -> d.debtors.find { it.id == id }?.priceList.orEmpty() }
  var state by remember { mutableStateOf(PriceList.FRESH) }
  LaunchedEffect(debtorId) { state = PriceList.onCustomer(state, customerList()) }
  //  فاکتورِ تازه (سبدِ خالی) از نو: لیستِ مشتریِ همین لحظه یا خرده، بی انتخابِ دستی
  LaunchedEffect(cart.isEmpty()) { if (cart.isEmpty()) state = PriceList.onCustomer(PriceList.FRESH, customerList()) }
  LaunchedEffect(cart, state.list) {
    val next = repriceCart(d, cart, state.list)
    if (next !== cart) cartStore.set(next)
  }
  FilterChip(
    selected = state.list == PriceList.WHOLESALE,
    onClick = { state = PriceList.toggle(state) },
    label = { Text(if (state.list == PriceList.WHOLESALE) "لیستِ قیمت: عمده" else "لیستِ قیمت: خرده") },
    modifier = modifier.padding(bottom = 6.dp),
  )
}

/** بخشِ تنظیمِ «لیستِ قیمت در صندوق» — تا `Settings` یک خط بخواهد */
@Composable
fun PriceListSettingsWired(context: Context) {
  var on by remember { mutableStateOf(PriceListPrefs.on(context)) }
  SettingsSection(
    icon = Icons.Filled.LocalOffer,
    title = PRICE_LIST_SETTINGS_TITLE,
    subtitle = if (on) "روشن" else "خاموش",
    tint = if (on) Shop.colors.primary else Shop.colors.muted,
  ) {
    Text(
      "روشن ⇒ مشتریِ «عمده» قیمتِ عمده می‌گیرد و نشانِ «لیستِ قیمت» در صندوق لیستِ همین فاکتور را عوض می‌کند؛ فیِ دستی دست نمی‌خورد.",
      style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2,
    )
    SegmentedChoice(
      options = listOf("off" to "خاموش", "on" to "روشن"),
      selected = if (on) "on" else "off",
      onSelect = { on = it == "on"; PriceListPrefs.set(context, on) },
    )
  }
}
