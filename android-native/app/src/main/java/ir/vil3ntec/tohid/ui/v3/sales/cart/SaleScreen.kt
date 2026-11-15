package ir.vil3ntec.tohid.ui.v3.sales.cart

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PostAdd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrPos
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.product.VisualSearch
import ir.vil3ntec.tohid.data.PromoEngine
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.ui.components.pos.CartSummary
import ir.vil3ntec.tohid.ui.components.pos.LocalTillMenu
import ir.vil3ntec.tohid.ui.components.pos.PhoneCatalog
import ir.vil3ntec.tohid.ui.components.pos.PhoneCatalogItem
import ir.vil3ntec.tohid.ui.components.pos.TillAction
import ir.vil3ntec.tohid.ui.components.pos.TillMenu
import ir.vil3ntec.tohid.ui.components.pos.TillOffer
import ir.vil3ntec.tohid.ui.screens.VisualSearchHandle
import ir.vil3ntec.tohid.ui.screens.VisualSearchHost
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.sales.checkout.CustomerPickerModel

/** کارهای صندوق — هر کدام یک کارِ سبد یا یک مسیر در `SaleRoute` */
@Stable
class SaleActions(
  /** زدنِ کاشی: یک پله (وزنی ⇐ صفحه‌کلید) */
  val tap: (PhoneCatalogItem) -> Unit,
  /** نگه داشتنِ کاشی ⇐ `sale/keypad/{id}` */
  val hold: (PhoneCatalogItem) -> Unit,
  val minus: (PhoneCatalogItem) -> Unit,
  /** −/+ ِ یک ردیفِ سبد */
  val step: (CartSummary.Row, Int) -> Unit,
  /** نگه داشتنِ ردیف یا زدنِ مقدار ⇐ `sale/line/{i}` */
  val line: (CartSummary.Row) -> Unit,
  val customer: () -> Unit,
  val clearCustomer: () -> Unit,
  val pay: () -> Unit,
  val scan: () -> Unit,
  /** «+» و نگه داشتنِ دسته ⇐ `products/categories` */
  val categories: () -> Unit,
  val freeLine: () -> Unit,
  val quickSale: () -> Unit,
  val history: () -> Unit,
  val back: () -> Unit,
  val toggleMode: () -> Unit,
  /** POS-03: لمسِ کاشی با پیشوندِ «۳*» ِ کادرِ جست‌وجو — همان شمار (وزنی هم، بی صفحه‌کلید) */
  val tapCount: (PhoneCatalogItem, Double) -> Unit = { item, _ -> tap(item) },
  /** POS-15: «قیمت‌پرسی» ِ منوی «…» — `null` ⇒ بی ردیف (تنظیمِ خاموش) */
  val priceCheck: (() -> Unit)? = null,
)

/**
 *  حالتِ آغازِ دلخواه (عکس‌های پیش‌نمایش). [expanded] از چیدمانِ پیشین مانده و بی‌اثر است (−/+ هر ردیف همیشه پیداست)؛
 *  [more] ⇒ «…» ِ سبد باز.
 */
data class SaleStart(val open: Boolean = false, val search: String = "", val expanded: String? = null, val menu: Boolean = false, val more: Boolean = false)

/**
 *  صندوقِ گوشی (`sale`) — **حالتِ تمرکز** (صاحب‌کار ۱۴۰۵/۰۸/۲۴): بی نوارِ شناورِ برنامه (`ShellFrame.FOCUS`)، سربرگ با برگشت، و
 *  یک نوارِ سبدِ روشن پایین (`TillFrame`: اسکن · «۳ قلم / جمع» · «پرداخت»). سبدِ باز برگهٔ رنگِ کارت است و پیش‌فرض فقط قلم‌ها با
 *  −/+، مشتری (یک خط)، جمع و «پرداخت» را دارد؛ بقیهٔ کارها در «…» (`TillMenu`) با توضیحِ یک‌خطی — هیچ قابلیتی برداشته نشده.
 *  مسیرها همان: «پرداخت» ⇐ `sale/checkout`، ردیف ⇐ `sale/line/{i}`، نگه داشتنِ کالا ⇐ `sale/keypad/{id}`، اسکن ⇐
 *  `scan?for=sale`، «…» ِ سربرگ ⇐ قلمِ آزاد، فروشِ سریع، تاریخچه. هر عدد از موتور (`total` با قیمتِ پس از تخفیف‌های خودکار).
 *
 *  @param modifier گیرندهٔ بارکدخوانِ سخت‌افزاری (`Modifier.hidScanner`)
 */
@Composable
fun SaleScreen(
  d: ShopData,
  cart: List<SalesEngine.CartLine>,
  total: Double,
  debtorId: String?,
  listMode: Boolean,
  actions: SaleActions,
  modifier: Modifier = Modifier,
  /** تخفیف‌های خودکارِ اعمال‌شده (PRM-18) — هر کدام یک ردیفِ کم‌رنگ بالای جمع */
  promo: List<PromoEngine.Applied> = emptyList(),
  /** نوارِ فاکتورهای باز (POS-12) زیرِ سرِ سبد — صدازننده فقط وقتی بیش از یک فاکتور باز است می‌دهد */
  heldStrip: (@Composable () -> Unit)? = null,
  /** «چند فاکتورِ هم‌زمان» روشن ⇒ «…» نگه‌داشتن و فهرست را هم دارد */
  onHold: (() -> Unit)? = null,
  onHeldList: (() -> Unit)? = null,
  start: SaleStart = SaleStart(),
  /** ابزارهای سبد (پیش‌فاکتور، سفارشِ ویژه، کارتِ هدیه، امانی، لیستِ قیمت، دریافتِ بدهی، ارسال) — `PhoneTillTools`؛ ردیف‌های «…» */
  tools: (@Composable () -> Unit)? = null,
  /** INV-03: مقدارِ هر کالا در فاکتورهای نگه‌داشته (`HeldStock.reserved`) ⇒ موجودیِ کاشی «قابل‌فروش» */
  held: Map<String, Double> = emptyMap(),
  /** POS-06: کالاهای سنجاق‌شدهٔ همین دستگاه (`TillPinsPrefs`) سرِ شبکه؛ خالی ⇒ همان شبکهٔ امروز */
  pins: List<String> = emptyList(),
  /** AI-11: «جست‌وجو با عکس» (`rememberVisualSearch`)؛ `null` (پرچمِ خاموش، بی حساب) ⇒ بی ردیف در «…» */
  visual: VisualSearchHandle? = null,
  /** POS-12: «فاکتورِ تازه» در «…» ِ سبد (نوارِ فاکتورها پنهان است)؛ `null` ⇒ بی ردیف */
  onNewInvoice: (() -> Unit)? = null,
) {
  var search by rememberSaveable { mutableStateOf(start.search) }
  var picked by rememberSaveable { mutableStateOf<String?>(null) }
  var open by rememberSaveable { mutableStateOf(start.open) }
  var menu by remember { mutableStateOf(start.menu) }
  var more by remember { mutableStateOf(start.more) }
  //  OPS-06: راهنمای صندوق از «…» (`LocalV3Guide`؛ بی راهنما ⇒ بی ردیف)
  val guide = ir.vil3ntec.tohid.ui.v3.LocalV3Guide.current
  var guideOpen by remember { mutableStateOf(false) }
  val category = CatalogModel.keepCategory(picked, d.productCategories)
  LaunchedEffect(cart.isEmpty()) { if (cart.isEmpty()) { open = false; more = false } }

  //  هر بار که چیزی به سبد افزوده شد (کاشی، اسکن، +، گوشیِ جفت‌شده) جمعِ نوار کمی می‌پرد
  val count = cart.sumOf { it.quantity }
  var last by remember { mutableStateOf(count) }
  var pulse by remember { mutableIntStateOf(0) }
  LaunchedEffect(count) { if (count > last) pulse++; last = count }

  val codes = ir.vil3ntec.tohid.ui.components.product.productInfoOn() // POS-01: کدِ کالا (SKU) در همان کادر
  val index = remember(d.products, d.sales, d.saleItems, codes) { CatalogModel.index(d, codes) }
  val inCart = remember(cart) { CartModel.inCart(cart) }
  val items = remember(index, search, category, inCart, d, held, pins, ir.vil3ntec.tohid.TillSynonyms.search) { CatalogModel.items(d, index, search, category, inCart, held, pins) }
  val best = remember(index, inCart, d, held) { CatalogModel.bestsellers(d, index, inCart, held) }
  val rows = remember(cart, d.products) { CartSummary.rows(cart, d.products) }
  //  ردیف‌های «…» ِ سبد — ابزارها همیشه روی صفحه‌اند (لیستِ قیمت و پنجره‌هایشان پس از بستنِ سبد هم کار می‌کنند)
  val tillMenu = remember { TillMenu() }

  Box(modifier.fillMaxSize()) {
    CompositionLocalProvider(LocalTillMenu provides tillMenu) {
      Box(Modifier.size(0.dp)) {
        tools?.invoke()
        TillOffer("invoice.new", onNewInvoice?.takeIf { cart.isNotEmpty() }?.let { go -> TillAction(10, StrPos.newInvoice, StrSale.hintNewInvoice, Icons.Outlined.PostAdd, V3Hue.BRAND, onClick = go) })
      }
    }
    TillFrame(
      model = CartSummary.of(cart, d.products, total),
      open = open,
      onOpen = { open = true },
      onClose = { open = false },
      pulse = pulse,
      onPay = actions.pay,
      onScan = actions.scan,
      catalog = { bottomGap ->
        PhoneCatalog(
          items = items,
          bestsellers = best,
          categories = d.productCategories,
          category = category,
          onCategory = { picked = it },
          onCategoryHold = { actions.categories() },
          onAddCategory = actions.categories,
          search = search,
          onSearch = { search = it },
          listMode = listMode,
          onToggleMode = actions.toggleMode,
          onBack = actions.back,
          onScan = actions.scan,
          scanning = false,
          scanPanel = {},
          emptyText = when (CatalogModel.empty(index, items.size, search)) {
            CatalogEmpty.NO_PRODUCTS -> StrSale.noProductsYet
            CatalogEmpty.NO_MATCH -> StrSale.noItemFor(search.trim())
            else -> StrSale.emptyCategory
          },
          bottomGap = bottomGap,
          //  POS-03: «۳*نام» ⇒ هر لمس سه تا؛ پس از افزودن کادر پاک می‌شود (لمسِ بعدی دوباره سه تا نمی‌شود)
          onTap = { item ->
            val q = CatalogModel.query(search, d)
            if (q.count != 1.0) { actions.tapCount(item, q.count); search = "" } else actions.tap(item)
          },
          onHold = actions.hold,
          onMinus = actions.minus,
          onMenu = { menu = true },
          till = true,
        )
      },
      sheet = { handle ->
        TillCartHeader(rows.size, tillMenu, onMore = { more = true }, modifier = handle)
        heldStrip?.let { strip -> Box(Modifier.fillMaxWidth().padding(bottom = 8.dp)) { strip() } }
        //  قلم‌ها می‌لغزند؛ مشتری، تخفیف‌ها، جمع و «پرداخت» زیرشان می‌مانند
        LazyColumn(Modifier.weight(1f, fill = false).fillMaxWidth()) {
          itemsIndexed(rows, key = { _, r -> r.id }) { i, r ->
            if (i > 0) V3Divider(start = 52.dp)
            TillCartRow(r, onMinus = { actions.step(r, -1) }, onPlus = { actions.step(r, +1) }, onDetails = { actions.line(r) })
          }
        }
        V3Divider()
        val debtor = debtorId?.let { id -> d.debtors.find { it.id == id } }
        TillCustomerRow(
          name = debtor?.name,
          balance = debtor?.let { CustomerPickerModel.label(ShopStore.debt(d, it.id)) },
          onPick = actions.customer,
          onClear = actions.clearCustomer,
        )
        V3Divider()
        val off = CartSummary.manualDiscount(cart, d.products)
        if (off > 0 || promo.isNotEmpty()) Spacer(Modifier.height(6.dp))
        if (off > 0) TillAdjustRow(StrSale.discountWord, off)
        promo.forEach { p -> TillAdjustRow(p.name, p.discount) }
        TillTotal(total, tillMenu)
        V3Button(StrSale.pay, actions.pay, Modifier.fillMaxWidth(), height = 52.dp, haptic = true)
      },
    )
    if (more) TillMoreSheet(tillMenu) { more = false }
    if (menu) {
      SaleMenuSheet(
        onFreeLine = actions.freeLine,
        onQuickSale = actions.quickSale,
        onHistory = actions.history,
        onClose = { menu = false },
        onHold = onHold,
        onHeldList = onHeldList,
        onPriceCheck = actions.priceCheck,
        onVisualSearch = visual?.let { v -> { v.start() } },
        onGuide = guide?.let { { guideOpen = true } },
      )
    }
    if (guideOpen && guide != null) ir.vil3ntec.tohid.ui.v3.V3GuideSheet(guide) { guideOpen = false }
    //  «مشابه» فقط کالا را در کادرِ جست‌وجو می‌نشاند (دستهٔ «همه» تا زیرِ دستهٔ دیگر گم نشود)؛ هرگز سبد
    visual?.let { VisualSearchHost(it) { t -> if (t is VisualSearch.Tap.Search) { picked = null; search = t.query } } }
  }
}
