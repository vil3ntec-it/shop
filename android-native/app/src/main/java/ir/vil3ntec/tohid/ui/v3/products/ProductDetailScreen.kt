package ir.vil3ntec.tohid.ui.v3.products

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrProducts
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.WarehouseEngine
import ir.vil3ntec.tohid.sync.PhoneScanner
import ir.vil3ntec.tohid.ui.Platform
import ir.vil3ntec.tohid.ui.v3.V3Action
import ir.vil3ntec.tohid.ui.v3.V3ConfirmSheet
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3FormBar
import ir.vil3ntec.tohid.ui.v3.V3MenuItem
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.V3Toasts
import ir.vil3ntec.tohid.ui.v3.rememberV3Stagger
import ir.vil3ntec.tohid.ui.v3.v3Stagger

/** ناوبریِ صفحهٔ کالا */
internal class ProductDetailNav(
  val back: () -> Unit,
  val edit: () -> Unit,
  val photo: () -> Unit,
  val entry: () -> Unit,
  val stock: () -> Unit,
  val movements: () -> Unit,
)

/**
 *  صفحهٔ یک کالا (`products/{id}`): عنوانِ درشت = نامِ کالا، عکسِ بزرگ (یا کاشیِ
 *  حرف)، کارتِ عددها (موجودی — زدن ⇐ انبار؛ قیمتِ فروش؛ قیمتِ خرید)، کارتِ سودها،
 *  حرکت‌های اخیر و تاریخچهٔ تغییرات؛ دکمهٔ چسبانِ «ورود کالا به انبار». «ویرایش» در
 *  سربرگ؛ «عکس»، «عکس با دوربینِ گوشی» (کامپیوتر/گوشیِ جفت‌شده) و «حذف» در «…».
 */
@Composable
internal fun ProductDetailScreen(
  store: ShopStore,
  data: ShopData,
  toasts: V3Toasts,
  productId: String,
  nav: ProductDetailNav,
) {
  val product = data.products.find { it.id == productId }
  if (product == null) {
    V3Scaffold(title = StrProducts.listTitle, onBack = nav.back, largeTitle = false) { pad ->
      Box(Modifier.fillMaxSize().padding(bottom = pad.calculateBottomPadding())) {
        V3EmptyState(Icons.Outlined.Inventory2, StrProducts.missingTitle, body = StrProducts.missingBody, action = StrKit.back, onAction = nav.back)
      }
    }
    return
  }
  val context = LocalContext.current
  val ops = rememberProductOps(store, toasts)
  var confirmDelete by remember { mutableStateOf(false) }
  val stagger = rememberV3Stagger(productId)
  val figures = remember(data, product) { ProductDetailModel.figures(data, product) }
  val moves = remember(data, productId) { ProductDetailModel.recentMoves(data, productId) }
  val moveCount = remember(data, productId) { ProductDetailModel.moveCount(data, productId) }
  val history = remember(data, productId) { WarehouseEngine.productHistory(data, productId).take(HISTORY_MAX) }
  val phoneLink = Platform.DESKTOP || PhoneScanner.listenCode.value != null

  V3Scaffold(
    title = product.name,
    onBack = nav.back,
    actions = listOf(V3Action(Icons.Outlined.Edit, StrProducts.edit, onClick = nav.edit)),
    overflow = listOfNotNull(
      V3MenuItem(Icons.Outlined.PhotoCamera, StrProducts.productPhoto, onClick = nav.photo),
      if (phoneLink) V3MenuItem(Icons.Outlined.PhoneAndroid, StrProducts.phoneCamera) { toasts.show(requestPhonePhoto(context, product.id, product.name)) } else null,
      V3MenuItem(Icons.Outlined.DeleteOutline, StrProducts.deleteProduct, danger = true) { confirmDelete = true },
    ),
    bottomBar = { V3FormBar(StrProducts.entryCta, nav.entry) },
  ) { pad ->
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = pad.calculateBottomPadding())) {
      //  ورودِ پلکانیِ بخش‌ها (بارِ اول)؛ عددها با شمارنده
      Box(Modifier.v3Stagger(stagger, "hero", 0)) { ProductHero(product, figures.status, onPhoto = nav.photo) }
      Spacer(Modifier.height(8.dp))
      Box(Modifier.v3Stagger(stagger, "figures", 1)) { FiguresCard(product, figures, onStock = nav.stock) }
      Box(Modifier.v3Stagger(stagger, "profit", 2)) { ProfitCard(product, figures) }
      Box(Modifier.v3Stagger(stagger, "moves", 3)) { MovesSection(product, moves, moveCount, onAll = nav.movements) }
      if (history.isNotEmpty()) Box(Modifier.v3Stagger(stagger, "history", 4)) { HistorySection(history) }
      Spacer(Modifier.height(8.dp))
    }
  }

  if (confirmDelete) {
    V3ConfirmSheet(
      title = StrProducts.deleteTitle(product.name),
      message = WarehouseEngine.deleteWarning(data, product.id),
      confirm = StrProducts.delete,
      icon = Icons.Outlined.DeleteOutline,
      onConfirm = { ops.delete(product.id) { nav.back() } },
      onDismiss = { confirmDelete = false },
    )
  }
}

private const val HISTORY_MAX = 30

/**
 *  «عکس با دوربینِ گوشی» — همان کارِ امروز: اگر هنوز جفت نشده، کدِ جفت‌شدن؛ وگرنه
 *  درخواستِ عکس به گوشی. عکس که رسید، استودیو را پوسته باز می‌کند (`PhoneLinkHost`).
 *  پیامِ کوتاهِ همان مرحله برمی‌گردد.
 */
private fun requestPhonePhoto(context: android.content.Context, id: String, name: String): String {
  val sc = PhoneScanner
  return when {
    sc.listenCode.value == null -> {
      sc.startListening(context)
      StrProducts.pairCode(sc.listenCode.value.orEmpty())
    }
    sc.requestPhoto(id, name) -> StrProducts.phoneAsk
    else -> StrProducts.phoneOffline
  }
}
