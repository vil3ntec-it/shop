package ir.vil3ntec.tohid.ui.v3.sales.quick

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.components.pos.CartSummary
import ir.vil3ntec.tohid.ui.components.pos.PhoneTile
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.MotionTokens
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.v3.V3AddChip
import ir.vil3ntec.tohid.ui.v3.sales.cart.CartModel
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3ChipRow
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.V3SearchField
import ir.vil3ntec.tohid.ui.v3.V3Springs
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.rememberV3Stagger
import ir.vil3ntec.tohid.ui.v3.v3Stagger

/** کارهای «فروشِ سریع» — هر کدام یک مسیر یا یک کارِ سبد در `SalesRoutes` */
@Stable
class QuickSaleActions(
  /** زدنِ کاشی: یک پله (کالای وزنی ⇐ صفحه‌کلید) */
  val tap: (productId: String, weighed: Boolean) -> Unit,
  val minus: (productId: String) -> Unit,
  /** نگه داشتنِ کاشی ⇐ `sale/keypad/{productId}` */
  val hold: (productId: String) -> Unit,
  val scan: () -> Unit,
  /** «+ دسته» و نگه داشتنِ دسته ⇐ `products/categories` */
  val categories: () -> Unit,
  val newProduct: () -> Unit,
  val pay: () -> Unit,
  val openCart: () -> Unit,
)

/**
 *  فروشِ سریع (`sale/quick`) — برای دکانی که بارکد ندارد: جست‌وجو (با اسکن)، تراشه‌های
 *  دسته، کاشیِ سه‌ستونهٔ صندوق (عکس یا حرف، قیمت، «۵ در سبد»، تمام‌شده کم‌رنگ) و نوارِ
 *  روشنِ سبد با «پرداخت». کاشی‌ها پله‌پله می‌آیند؛ نوار با فنر از پایین.
 *
 *  @param total جمعِ سبد از موتور (`SalesEngine.cartTotal` با قیمتِ پس از تخفیف‌های خودکار)
 */
@Composable
fun QuickSaleScreen(
  d: ShopData,
  cart: List<SalesEngine.CartLine>,
  total: Double,
  onBack: (() -> Unit)?,
  actions: QuickSaleActions,
  initialQuery: String = "",
  initialCategory: String? = null,
) {
  val c = PhoneV3.colors
  var query by rememberSaveable { mutableStateOf(initialQuery) }
  var picked by rememberSaveable { mutableStateOf(initialCategory) }
  val category = QuickSaleModel.keepCategory(picked, d.productCategories)
  val index = remember(d.products, d.sales, d.saleItems) { QuickSaleModel.index(d) }
  val inCart = remember(cart) { CartModel.inCart(cart) }
  val items = remember(index, query, category, inCart, d) { QuickSaleModel.items(d, index, query, category, inCart) }
  val empty = QuickSaleModel.empty(index, items.size, query)
  val strip = remember(cart, d.products, total) { CartSummary.of(cart, d.products, total) }
  val stagger = rememberV3Stagger(category, query.isBlank())

  V3Scaffold(
    title = StrSale.quickSale,
    subtitle = StrSale.quickHint,
    onBack = onBack,
    search = {
      V3SearchField(query, { query = it }, StrSale.catalogHint, onScan = actions.scan)
    },
    chips = {
      V3ChipRow {
        V3Chip(StrSale.allChip, category == null, { picked = null })
        d.productCategories.forEach { name ->
          V3Chip(name, category == name, { picked = if (category == name) null else name })
        }
        V3AddChip(StrSale.addCategoryChip, actions.categories)
      }
    },
    bottomBar = {
      AnimatedVisibility(
        visible = strip.visible,
        enter = slideInVertically(V3Springs.ui()) { it } + fadeIn(MotionSpec.tweenOf(MotionTokens.SHORT)),
        exit = slideOutVertically(MotionSpec.tweenOf(MotionTokens.SHORT)) { it } + fadeOut(MotionSpec.tweenOf(MotionTokens.SHORT)),
      ) { QuickCartStrip(strip, onOpen = actions.openCart, onPay = actions.pay) }
    },
  ) { pad ->
    when (empty) {
      QuickEmpty.NO_PRODUCTS -> V3EmptyState(
        Icons.Outlined.Inventory2, StrSale.noProductsYet, Modifier.fillMaxSize(),
        action = StrSale.registerItemAlt, onAction = actions.newProduct,
      )
      QuickEmpty.NO_MATCH -> V3EmptyState(Icons.Outlined.SearchOff, StrSale.noItemFor(query.trim()), Modifier.fillMaxSize())
      QuickEmpty.EMPTY_CATEGORY -> V3EmptyState(Icons.Outlined.Category, StrSale.emptyCategory, Modifier.fillMaxSize())
      QuickEmpty.NONE -> LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = TILE_MIN),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = pad.calculateBottomPadding()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        if (query.isNotBlank()) {
          item(key = "count", span = { GridItemSpan(maxLineSpan) }) {
            Text(StrSale.linesCount(items.size), style = V3Type.footnote, color = c.inkMuted)
          }
        }
        itemsIndexed(items, key = { _, it -> it.id }) { i, item ->
          Box(Modifier.v3Stagger(stagger, item.id, i)) {
            PhoneTile(
              item,
              onTap = { actions.tap(it.id, it.weighed) },
              onHold = { actions.hold(it.id) },
              onMinus = { actions.minus(it.id) },
            )
          }
        }
      }
    }
  }
}

/** کمینهٔ پهنای کاشی: گوشیِ ۳۹۰ ⇐ سه ستون، تبلت و کامپیوتر بیشتر */
private val TILE_MIN = 104.dp
