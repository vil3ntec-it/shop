package ir.vil3ntec.tohid.ui.v3.sales.cart

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Person
import androidx.compose.runtime.Composable
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
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.data.PromoEngine
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.ui.components.pos.CartSummary
import ir.vil3ntec.tohid.ui.components.pos.PhoneCartAmount
import ir.vil3ntec.tohid.ui.components.pos.PhoneCartClear
import ir.vil3ntec.tohid.ui.components.pos.PhoneCartInfoRow
import ir.vil3ntec.tohid.ui.components.pos.PhoneCartPay
import ir.vil3ntec.tohid.ui.components.pos.PhoneCartRow
import ir.vil3ntec.tohid.ui.components.pos.PhoneCartTitle
import ir.vil3ntec.tohid.ui.components.pos.PhoneCartTotal
import ir.vil3ntec.tohid.ui.components.pos.PhoneCatalog
import ir.vil3ntec.tohid.ui.components.pos.PhoneCatalogItem
import ir.vil3ntec.tohid.ui.components.pos.PhoneSaleFrame
import ir.vil3ntec.tohid.ui.components.pos.PhoneSaleText
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
)

/** حالتِ آغازِ دلخواه (عکس‌های پیش‌نمایش) */
data class SaleStart(val open: Boolean = false, val search: String = "", val expanded: String? = null, val menu: Boolean = false)

/**
 *  صندوقِ گوشی (`sale`) — همان r3 (کاتالوگ روی سطحِ روشن، نوارِ سبد روی سیاه) و r2 (سبدِ باز)
 *  که صاحب‌کار پسندیده، حالا با مسیرها: «پرداخت» ⇐ `sale/checkout`، نگه داشتنِ ردیف ⇐
 *  `sale/line/{i}`، نگه داشتنِ کاشی ⇐ `sale/keypad/{id}`، اسکن ⇐ `scan?for=sale`، «…» ⇐
 *  قلمِ آزاد، فروشِ سریع، تاریخچه. هر عدد از موتور (`total` با قیمتِ پس از تخفیف‌های خودکار).
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
  /** تخفیف‌های خودکارِ اعمال‌شده (PRM-18) — هر کدام یک ردیف با نامش زیرِ اقلام */
  promo: List<PromoEngine.Applied> = emptyList(),
  /** نوارِ فاکتورهای باز (POS-12) زیرِ سرخطِ سبد */
  heldStrip: (@Composable () -> Unit)? = null,
  /** «چند فاکتورِ هم‌زمان» روشن ⇒ «…» نگه‌داشتن و فهرست را هم دارد */
  onHold: (() -> Unit)? = null,
  onHeldList: (() -> Unit)? = null,
  start: SaleStart = SaleStart(),
) {
  var search by rememberSaveable { mutableStateOf(start.search) }
  var picked by rememberSaveable { mutableStateOf<String?>(null) }
  var open by rememberSaveable { mutableStateOf(start.open) }
  var expanded by remember { mutableStateOf(start.expanded) }
  var menu by remember { mutableStateOf(start.menu) }
  val category = CatalogModel.keepCategory(picked, d.productCategories)
  LaunchedEffect(cart.isEmpty()) { if (cart.isEmpty()) open = false }

  //  هر بار که چیزی به سبد افزوده شد (کاشی، اسکن، +، گوشیِ جفت‌شده) نوار می‌پرد
  val count = cart.sumOf { it.quantity }
  var last by remember { mutableStateOf(count) }
  var pulse by remember { mutableIntStateOf(0) }
  LaunchedEffect(count) { if (count > last) pulse++; last = count }

  val index = remember(d.products, d.sales, d.saleItems) { CatalogModel.index(d) }
  val inCart = remember(cart) { CartModel.inCart(cart) }
  val items = remember(index, search, category, inCart, d) { CatalogModel.items(d, index, search, category, inCart) }
  val best = remember(index, inCart, d) { CatalogModel.bestsellers(d, index, inCart) }
  val rows = remember(cart, d.products) { CartSummary.rows(cart, d.products) }

  Box(modifier.fillMaxSize()) {
    PhoneSaleFrame(
      model = CartSummary.of(cart, d.products, total),
      open = open,
      onOpen = { open = true },
      onClose = { open = false; expanded = null },
      pulse = pulse,
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
          onTap = actions.tap,
          onHold = actions.hold,
          onMinus = actions.minus,
          onMenu = { menu = true },
        )
      },
      cart = { revealed, dragHandle ->
        Box(dragHandle.fillMaxWidth().padding(top = 62.dp, bottom = if (heldStrip != null) 12.dp else 38.dp)) { PhoneCartTitle() }
        heldStrip?.let { strip -> Box(Modifier.fillMaxWidth().padding(bottom = 16.dp)) { strip() } }
        //  ردیف‌ها می‌لغزند؛ «مشتری» و «تخفیف» زیرشان می‌مانند و هرگز پشتِ «جمع:» نمی‌روند
        Box(Modifier.weight(1f).fillMaxWidth()) {
          Column(Modifier.fillMaxWidth()) {
            LazyColumn(Modifier.weight(1f, fill = false).fillMaxWidth()) {
              itemsIndexed(rows, key = { _, r -> r.id }) { i, r ->
                PhoneCartRow(
                  row = r,
                  index = i,
                  revealed = revealed,
                  expanded = expanded == r.id,
                  onToggle = { expanded = if (expanded == r.id) null else r.id },
                  onMinus = { actions.step(r, -1) },
                  onPlus = { actions.step(r, +1) },
                  onHold = { actions.line(r) },
                  onEdit = { actions.line(r) },
                )
              }
            }
            val debtor = debtorId?.let { id -> d.debtors.find { it.id == id } }
            PhoneCartInfoRow(
              icon = Icons.Outlined.Person,
              title = debtor?.name ?: StrSale.walkIn,
              hint = debtor?.let { CustomerPickerModel.label(ShopStore.debt(d, it.id)) } ?: StrSale.pickDebtorForCredit,
              index = rows.size,
              revealed = revealed,
              onClick = actions.customer,
              onClickLabel = StrSale.pickDebtor,
              trailing = { if (debtor != null) PhoneCartClear(StrSale.removeDebtor, actions.clearCustomer) },
            )
            val off = CartSummary.manualDiscount(cart, d.products)
            if (off > 0) {
              PhoneCartInfoRow(
                icon = Icons.Outlined.LocalOffer,
                title = StrSale.discountWord,
                hint = StrSale.manualDiscountHint,
                index = rows.size + 1,
                revealed = revealed,
                onClick = null,
                trailing = { PhoneCartAmount(PhoneSaleText.amount(-off)) },
              )
            }
            promo.forEachIndexed { i, p ->
              PhoneCartInfoRow(
                icon = Icons.Outlined.LocalOffer,
                title = p.name,
                hint = StrSale.autoDiscount,
                index = rows.size + 2 + i,
                revealed = revealed,
                onClick = null,
                trailing = { PhoneCartAmount(PhoneSaleText.amount(-p.discount)) },
              )
            }
          }
        }
        Spacer(Modifier.height(16.dp))
        PhoneCartTotal(total)
        Spacer(Modifier.height(36.dp))
        PhoneCartPay(StrSale.pay, onClick = actions.pay)
      },
    )
    if (menu) {
      SaleMenuSheet(
        onFreeLine = actions.freeLine,
        onQuickSale = actions.quickSale,
        onHistory = actions.history,
        onClose = { menu = false },
        onHold = onHold,
        onHeldList = onHeldList,
      )
    }
  }
}
