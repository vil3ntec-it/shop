package ir.vil3ntec.tohid.ui.components.pos

import ir.vil3ntec.tohid.data.ShopStore
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrCustomerPrices
import ir.vil3ntec.tohid.core.i18n.StrPos
import ir.vil3ntec.tohid.core.i18n.StrSalePages
import ir.vil3ntec.tohid.core.i18n.StrSettingsPages
import ir.vil3ntec.tohid.core.sale.PriceList
import ir.vil3ntec.tohid.data.CartPriceList
import ir.vil3ntec.tohid.data.CartStore
import ir.vil3ntec.tohid.data.CustomerPriceCart
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.screens.SegmentedChoice
import ir.vil3ntec.tohid.ui.screens.SettingsSection
import ir.vil3ntec.tohid.ui.theme.Shop

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

/**
 *  «قیمتِ خاصِ مشتری» (POS-18، هسته `core/sale/CustomerPrice`) — ⛔ پیش‌فرض **خاموش** (`tohid-policy` ‹`customer_prices`›،
 *  مالِ دکان مثلِ `price_lists`). فقط همراهِ «لیستِ قیمت در صندوق» ِ روشن اثر دارد (همان نشانِ صندوق). خاموش ⇒ بخشِ
 *  پروندهٔ مشتری کشیده نمی‌شود و سبد فقط با همان `repriceCart` ِ لیست، بایت‌به‌بایت مثلِ پیش.
 */
object CustomerPricePrefs {
  private const val KEY = "customer_prices"
  private fun prefs(context: Context) = context.getSharedPreferences("tohid-policy", Context.MODE_PRIVATE)
  fun on(context: Context): Boolean = prefs(context).getBoolean(KEY, false)
  fun set(context: Context, on: Boolean) { prefs(context).edit().putBoolean(KEY, on).apply() }
}

/** سبد ⇐ لیست: فقط ردیفِ کالادار و غیرِدستی؛ بی تغییر ⇒ همان شیء */
internal fun repriceCart(d: ShopData, lines: List<SalesEngine.CartLine>, list: String): List<SalesEngine.CartLine> {
  val byId = ShopStore.productsById(d.products)
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

/**
 *  @param toggles شمارِ Ctrl+R ِ صندوقِ کامپیوتر (`PosKeys.priceList`، POS-18): هر یک بیشتر ⇒ همان `PriceList.toggle` ِ
 *  زدنِ نشان. صفر (گوشی و پیش‌فرض) ⇒ هیچ.
 */
@Composable
fun PriceListAtPos(cartStore: CartStore, d: ShopData, cart: List<SalesEngine.CartLine>, debtorId: String?, modifier: Modifier = Modifier, toggles: Int = 0) {
  val context = LocalContext.current
  val on = remember { PriceListPrefs.on(context) }
  if (!on) return
  fun customerList(): String? = debtorId?.let { id -> d.debtors.find { it.id == id }?.priceList.orEmpty() }
  var state by remember { mutableStateOf(PriceList.FRESH) }
  //  POS-18: انتخابِ دستیِ لیست مالِ همان فاکتور است (`CartStore.priceList`) — با نگه‌داشتن، بازگشت و بازیابی می‌رود و
  //  برمی‌گردد. فاکتورِ صندوق که عوض شد (یا خالی شد) حالت از همان فاکتور، نه از فاکتورِ پیشین (`CartPriceList`)
  val hand by cartStore.priceList.collectAsState()
  LaunchedEffect(hand) { state = CartPriceList.restore(hand, customerList()) }
  LaunchedEffect(debtorId) { state = PriceList.onCustomer(state, customerList()) }
  fun toggle() {
    state = PriceList.toggle(state)
    cartStore.setPriceList(CartPriceList.hand(state))
  }
  //  قیمت‌گذاری با لیستِ همین فاکتور — مقدارِ سبد، نه حالتِ صفحه که شاید هنوز به سبدِ تازه نرسیده
  fun current(): PriceList.State = CartPriceList.effective(state, cartStore.priceList.value)
  //  Ctrl+R: فقط افزایشِ شمار، نه نخستین ترکیب (همان شمارِ پیشین ⇒ هیچ)
  val seen = remember { intArrayOf(toggles) }
  LaunchedEffect(toggles) { if (toggles != seen[0]) { seen[0] = toggles; toggle() } }
  //  فاکتورِ تازه (سبدِ خالی) از نو: لیستِ مشتریِ همین لحظه یا خرده، بی انتخابِ دستی
  LaunchedEffect(cart.isEmpty()) { if (cart.isEmpty()) state = PriceList.onCustomer(PriceList.FRESH, customerList()) }
  val special = remember { CustomerPricePrefs.on(context) }
  if (!special) {
    LaunchedEffect(cart, state.list) {
      val next = repriceCart(d, cart, current().list)
      if (next !== cart) cartStore.set(next)
    }
    PriceListChip(state.list, { toggle() }, modifier.padding(bottom = 6.dp))
    return
  }
  //  POS-18 «قیمتِ خاص»: دستی ← قیمتِ خاص ← لیستِ مشتری ← لیستِ فاکتور ← خرده (`CustomerPrice.resolve`)؛ فیِ دستی فقط
  //  وقتی نقش تغییرِ قیمت دارد (`sales.price` ممنوع نیست). عوض شدنِ مشتری ⇒ همان بازقیمت‌گذاری با پیامِ شمارِ ردیف‌ها.
  val manualAllowed = remember { ir.vil3ntec.tohid.data.ShopRole.decide(context, "sales.price").decision != "deny" }
  val specials = debtorId?.let { id -> d.debtors.find { it.id == id }?.specialPrices }
  val seenDebtor = remember { arrayOfNulls<String>(1) }
  var before by remember { mutableStateOf<List<SalesEngine.CartLine>?>(null) }
  var notice by remember { mutableStateOf<String?>(null) }
  LaunchedEffect(cart, state, debtorId, specials) {
    if (debtorId != seenDebtor[0]) { seenDebtor[0] = debtorId; before = cart.takeIf { debtorId != null && it.isNotEmpty() } }
    if (cart.isEmpty()) before = null
    val r = CustomerPriceCart.reprice(d, cart, current(), debtorId, ir.vil3ntec.tohid.todayIso(), manualAllowed)
    notice = before?.let { b -> CustomerPriceCart.changedSince(b, r.cart).takeIf { it > 0 || r.keptManual > 0 }?.let { StrCustomerPrices.repriced(it, r.keptManual) } }
      ?: r.special.takeIf { it > 0 }?.let(StrCustomerPrices::specialLines)
    if (r.cart !== cart) cartStore.set(r.cart)
  }
  Column(modifier.padding(bottom = 6.dp)) {
    PriceListChip(state.list, { toggle() }, Modifier)
    notice?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2) }
  }
}

@Composable
private fun PriceListChip(list: String, onClick: () -> Unit, modifier: Modifier) {
  FilterChip(
    selected = list == PriceList.WHOLESALE,
    onClick = onClick,
    label = { Text(if (list == PriceList.WHOLESALE) StrPos.priceListWholesaleChip else StrPos.priceListRetailChip) },
    modifier = modifier,
  )
}

/** بخشِ تنظیمِ «لیستِ قیمت در صندوق» — تا `Settings` یک خط بخواهد */
@Composable
fun PriceListSettingsWired(context: Context) {
  var on by remember { mutableStateOf(PriceListPrefs.on(context)) }
  SettingsSection(
    icon = Icons.Filled.LocalOffer,
    title = StrPos.priceListTitle,
    subtitle = if (on) StrSettingsPages.on else StrSettingsPages.off,
    tint = if (on) Shop.colors.primary else Shop.colors.muted,
  ) {
    Text(
      StrSalePages.priceListNote,
      style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2,
    )
    SegmentedChoice(
      options = listOf("off" to StrSettingsPages.off, "on" to StrSettingsPages.on),
      selected = if (on) "on" else "off",
      onSelect = { on = it == "on"; PriceListPrefs.set(context, on) },
    )
    //  POS-18 «قیمتِ خاصِ مشتری» — همان کلیدِ صفحهٔ نسخهٔ ۳ (`PriceListSettingsScreen`)
    var special by remember { mutableStateOf(CustomerPricePrefs.on(context)) }
    Text(StrCustomerPrices.settingsTitle, style = MaterialTheme.typography.labelLarge, color = Shop.colors.text)
    Text(StrCustomerPrices.settingsNote, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    SegmentedChoice(
      options = listOf("off" to StrSettingsPages.off, "on" to StrSettingsPages.on),
      selected = if (special) "on" else "off",
      onSelect = { special = it == "on"; CustomerPricePrefs.set(context, special) },
    )
  }
}
