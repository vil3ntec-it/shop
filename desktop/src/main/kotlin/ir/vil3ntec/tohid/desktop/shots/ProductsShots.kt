package ir.vil3ntec.tohid.desktop.shots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ir.vil3ntec.tohid.core.nav.RoutePaths
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.StockMovement
import ir.vil3ntec.tohid.data.WarehouseEntry
import ir.vil3ntec.tohid.ui.components.common.PhoneNav
import ir.vil3ntec.tohid.ui.v3.LocalV3Toasts
import ir.vil3ntec.tohid.ui.v3.V3SnackbarHost
import ir.vil3ntec.tohid.ui.v3.products.EditProductScreen
import ir.vil3ntec.tohid.ui.v3.products.NewProductsScreen
import ir.vil3ntec.tohid.ui.v3.products.ProductDetailNav
import ir.vil3ntec.tohid.ui.v3.products.ProductDetailScreen
import ir.vil3ntec.tohid.ui.v3.products.ProductFormNav
import ir.vil3ntec.tohid.ui.v3.products.ProductRowDraft
import ir.vil3ntec.tohid.ui.v3.products.ProductsListModel
import ir.vil3ntec.tohid.ui.v3.products.ProductsListNav
import ir.vil3ntec.tohid.ui.v3.products.ProductsListScreen
import ir.vil3ntec.tohid.ui.v3.rememberV3Toasts
import java.io.File

/**
 *  عکس‌های گروهِ G2 کالاها (`products.md` › Shots) — مسیرها روی دفترِ نمایشیِ CI
 *  (`desktop/tools/demo-ledger.mjs`: p1…p12)، و حالت‌هایی که آن دفتر ندارد (فهرستِ
 *  ۳۰تاییِ نام‌های بلند و عددهای بزرگ، خالی، انتخاب، فرمِ سه‌ردیفی، کالای تمام‌شده،
 *  «بیشتر»ِ باز) روی دفترِ کوچکِ همین فایل.
 */
object ProductsShots : GroupShots {
  override val group = "products"

  /** عکسِ واقعی برای استودیو: نشانِ خودِ برنامه از مخزن (کارِ CI در `desktop/`) */
  private val studioSource: String get() = File("../icons/icon-512.png").absoluteFile.toURI().toString()

  override val shots = listOf(
    Shot("list", route = "products"),
    Shot("list-long", content = { store -> ListShot(store, Demo.long) }),
    Shot("grid", route = "products?view=grid"),
    Shot("filter-low", route = "products?filter=low"),
    Shot("selection", content = { store -> ListShot(store, Demo.long, selected = setOf("x1", "x3", "x4")) }),
    Shot("search-none", route = "products?q=" + RoutePaths.encode("زززز")),
    Shot("empty", content = { store -> ListShot(store, ShopData(products = emptyList())) }),
    Shot("detail", route = "products/p1"),
    Shot("detail-out", content = { store -> DetailShot(store, Demo.long, "x2") }),
    Shot("detail-huge", content = { store -> DetailShot(store, Demo.long, "x1") }),
    Shot("detail-missing", route = "products/nope"),
    Shot("new", route = "products/new"),
    Shot("new-barcode", route = "products/new?barcode=6260099"),
    Shot("new-rows", content = { store -> NewShot(store) }),
    Shot("edit", route = "products/p2/edit"),
    Shot("edit-more", content = { store -> EditShot(store) }),
    Shot("photo-sheet", route = "products/p1/photo"),
    Shot("studio", route = "products/p1/photo/studio?src=" + RoutePaths.encode(studioSource)),
    Shot("studio-error", route = "products/p1/photo/studio?src=" + RoutePaths.encode("file:/nope/missing.png")),
    Shot("categories", route = "products/categories?pick=1&selected=" + RoutePaths.encode("خوراکی")),
    Shot("bulk-category", route = "products/bulk/category?ids=p1,p2,p3"),
  )
}

/** دفترِ کوچکِ عکس‌ها — نام‌های بلند، عددهای میلیونی، کم/تمام‌شده/غیرفعال */
private object Demo {
  private val names = listOf(
    "روغن آفتاب‌گردان پنج لیتری با درِ پیچی و دستهٔ پلاستیکی", "شیر خشک کودک مرحلهٔ دو", "برنج سلّا درجه یک", "چای سیاه کیسه‌ای صد عددی",
    "پودر لباس‌شویی دستی", "آب معدنی", "بیسکویت شکلاتی", "کیک یزدی", "رب گوجه‌فرنگی", "عدس", "لوبیا چیتی", "نخود",
  )
  val long: ShopData by lazy {
    val products = (1..30).map { i ->
      Product(
        id = "x$i",
        name = names[(i - 1) % names.size] + if (i > names.size) " ${i}" else "",
        category = listOf("خوراکی", "نوشیدنی", "شوینده")[i % 3],
        unit = listOf("عدد", "کیلوگرم", "بسته")[i % 3],
        purchasePrice = if (i == 1) 10_345_678.0 else 40.0 * i,
        salePrice = if (i == 1) 12_345_678.0 else 52.0 * i,
        minStock = 5.0,
        barcodes = listOf("62601${100 + i}"),
        active = i != 4,
        createdAt = i.toLong(),
        subcategory = if (i == 1) "روغن مایع" else "",
      )
    }
    val stock = mapOf("x1" to 12_345.0, "x2" to 0.0, "x3" to 3.0)
    ShopData(
      products = products,
      productCategories = listOf("خوراکی", "نوشیدنی", "شوینده"),
      warehouseEntries = products.map { WarehouseEntry("w${it.id}", productId = it.id, units = stock[it.id] ?: 40.0, date = "2026-10-01") },
      stockMovements = products.take(3).map { StockMovement("m${it.id}", productId = it.id, type = "purchase_in", qty = stock[it.id] ?: 40.0, date = "2026-10-01", createdAt = 1) },
    )
  }
}

@Composable
private fun Toasted(content: @Composable (ir.vil3ntec.tohid.ui.v3.V3Toasts) -> Unit) {
  val host = remember { SnackbarHostState() }
  val toasts = rememberV3Toasts(host)
  CompositionLocalProvider(LocalV3Toasts provides toasts) {
    Box(Modifier.fillMaxSize()) {
      content(toasts)
      V3SnackbarHost(host, Modifier.align(Alignment.TopCenter))
    }
  }
}

private val noListNav = ProductsListNav({}, {}, {}, {}, {}, { _, _ -> })
private val noFormNav = ProductFormNav({}, {}, { _, _ -> })

@Composable
private fun ListShot(store: ShopStore, data: ShopData, selected: Set<String> = emptySet()) = Toasted { toasts ->
  ProductsListScreen(store, data, toasts, ProductsListModel.Query(), PhoneNav.RESERVE, noListNav, initialSelection = selected)
}

@Composable
private fun DetailShot(store: ShopStore, data: ShopData, id: String) = Toasted { toasts ->
  ProductDetailScreen(store, data, toasts, id, ProductDetailNav({}, {}, {}, {}, {}, {}))
}

@Composable
private fun NewShot(store: ShopStore) = Toasted { toasts ->
  val rows = listOf(
    ProductRowDraft(key = 0, name = "بیسکویت شکلاتی", category = "خوراکی", unit = "بسته", purchase = "80", sale = "100", barcode = "6260011"),
    ProductRowDraft(key = 1, name = "چای سبز", sale = "260"),
    ProductRowDraft(key = 2, name = "صابون دست", category = "شوینده", unit = "عدد", purchase = "60", sale = "50"),
  )
  NewProductsScreen(store, store.data.value, toasts, null, noFormNav, seed = listOf(rows[1], rows[0], rows[2]), showErrors = true) {}
}

@Composable
private fun EditShot(store: ShopStore) = Toasted { toasts ->
  val p = Demo.long.products.first()
  val data = Demo.long.copy(products = listOf(p.copy(saleUnit = "لیتر", unitFactor = 0.2)) + Demo.long.products.drop(1))
  EditProductScreen(store, data, toasts, p.id, noFormNav) {}
}
