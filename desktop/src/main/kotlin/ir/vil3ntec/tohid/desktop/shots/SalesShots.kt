package ir.vil3ntec.tohid.desktop.shots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.DebtTransaction
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SaleItem
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.SalesEngine.CartLine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.WarehouseEntry
import ir.vil3ntec.tohid.ui.v3.sales.cart.FreeLineForm
import ir.vil3ntec.tohid.ui.v3.sales.cart.FreeLineSheet
import ir.vil3ntec.tohid.ui.v3.sales.cart.KeypadModel
import ir.vil3ntec.tohid.ui.v3.sales.cart.KeypadSheet
import ir.vil3ntec.tohid.ui.v3.sales.cart.LineForm
import ir.vil3ntec.tohid.ui.v3.sales.cart.LineModel
import ir.vil3ntec.tohid.ui.v3.sales.cart.LineSheet
import ir.vil3ntec.tohid.ui.v3.sales.checkout.CustomerPickerSheet
import ir.vil3ntec.tohid.ui.v3.sales.draft.DraftSheet
import ir.vil3ntec.tohid.ui.v3.sales.quick.QuickSaleActions
import ir.vil3ntec.tohid.ui.v3.sales.quick.QuickSaleScreen
import ir.vil3ntec.tohid.ui.v3.sales.scan.CameraAccess
import ir.vil3ntec.tohid.ui.v3.sales.scan.ScanScreen

/**
 *  عکس‌های گروهِ G5 فروش (`sales.md`) — روی دفترِ نمایشیِ همین فایل (نه دادهٔ دکانِ واقعی):
 *  ۱۴ کالا در چهار دسته با نام‌های بلند، تمام‌شده و وزنی، سه قرض‌دار (بدهکار، صاف، پیش‌پرداخت)
 *  و سبدی با چهار قلم. صفحه‌ها مستقیم با همین دفتر کشیده می‌شوند؛ کارها بی‌اثرند.
 *  مسیرهای `route-*` همان پوستهٔ برنامه با دفترِ نمایشیِ CI است.
 */
object SalesShots : GroupShots {
  override val group = "sales"
  override val shots = listOf(
    Shot("route-sale", route = "sale"),
    Shot("route-quick", route = "sale/quick"),
    Shot("route-history", route = "sales"),
    Shot("quick", content = { QuickShot(SalesDemo.ledger(), SalesDemo.cart) }),
    Shot("quick-empty-cart", content = { QuickShot(SalesDemo.ledger(), emptyList()) }),
    Shot("quick-category", content = { QuickShot(SalesDemo.ledger(), SalesDemo.cart, category = SalesDemo.DRINKS) }),
    Shot("quick-nomatch", content = { QuickShot(SalesDemo.ledger(), SalesDemo.cart, query = "کالای ناموجود") }),
    Shot("quick-no-products", content = { QuickShot(ShopData(), emptyList()) }),
    Shot("line", content = { LineShot(SalesDemo.cart[0]) }),
    Shot("line-manual-price", content = { LineShot(SalesDemo.cart[0], LineForm("3", "1150")) }),
    Shot("line-second-unit", content = { LineShot(CartLine(SalesDemo.CARTON, 1.0), LineForm("6", "1800", inSaleUnit = true)) }),
    Shot("line-error", content = { LineShot(SalesDemo.cart[1], LineForm("1.5", "450")) }),
    Shot("free-line", content = { FreeShot(FreeLineForm()) }),
    Shot("free-line-filled", content = { FreeShot(FreeLineForm("کیسهٔ بزرگِ پلاستیکی", "15", "3")) }),
    Shot("free-line-errors", content = { FreeShot(FreeLineForm(priceText = "0"), errors = true) }),
    Shot("keypad-weighed", content = { KeypadShot(SalesDemo.RICE, "2.5") }),
    Shot("keypad-in-cart", content = { KeypadShot(SalesDemo.OIL, null) }),
    Shot("keypad-too-many", content = { KeypadShot(SalesDemo.SOAP, "40") }),
    Shot("customer", content = { CustomerShot("") }),
    Shot("customer-search", content = { CustomerShot("احمد") }),
    Shot("customer-none", content = { CustomerShot("ناشناس") }),
    Shot("scan-live", content = { ScanScreen(onCode = {}, onClose = {}, camera = false, start = CameraAccess.GRANTED) }),
    Shot("scan-ask", content = { ScanScreen(onCode = {}, onClose = {}, camera = false, start = CameraAccess.ASK) }),
    Shot("scan-denied", content = { ScanScreen(onCode = {}, onClose = {}, camera = false, start = CameraAccess.DENIED) }),
    Shot("draft", content = { DraftShot() }),
  )
}

private val noQuick = QuickSaleActions({ _, _ -> }, {}, {}, {}, {}, {}, {}, {})

@Composable
private fun QuickShot(d: ShopData, cart: List<CartLine>, query: String = "", category: String? = null) {
  QuickSaleScreen(d, cart, SalesEngine.cartTotal(d, cart), onBack = {}, actions = noQuick, initialQuery = query, initialCategory = category)
}

/** برگه روی همان «فروشِ سریع» که از آن باز می‌شود */
@Composable
private fun OverQuick(content: @Composable () -> Unit) {
  Box(Modifier.fillMaxSize()) {
    QuickShot(SalesDemo.ledger(), SalesDemo.cart)
    content()
  }
}

@Composable
private fun LineShot(line: CartLine, form: LineForm? = null) = OverQuick {
  LineSheet(LineModel.info(SalesDemo.ledger(), line), onSave = { _, _ -> }, onRemove = {}, onClose = {}, initial = form)
}

@Composable
private fun FreeShot(form: FreeLineForm, errors: Boolean = false) = OverQuick {
  FreeLineSheet(onAdd = {}, onClose = {}, initial = form, showErrors = errors)
}

@Composable
private fun KeypadShot(productId: String, text: String?) = OverQuick {
  val item = KeypadModel.item(SalesDemo.ledger(), productId, SalesDemo.cart)!!
  KeypadSheet(item, onConfirm = {}, onClose = {}, initial = text)
}

@Composable
private fun CustomerShot(query: String) = OverQuick {
  CustomerPickerSheet(SalesDemo.ledger(), selected = SalesDemo.DEBTOR, onPick = {}, onNew = {}, onClose = {}, initialQuery = query)
}

@Composable
private fun DraftShot() = OverQuick {
  DraftSheet(lines = 4, total = 2_350.0, dropped = 1, onContinue = {}, onDiscard = {}, onLater = {})
}

/** دفترِ نمایشیِ عکس‌های فروش */
private object SalesDemo {
  const val RICE = "p-rice"
  const val OIL = "p-oil"
  const val SOAP = "p-soap"
  const val CARTON = "p-cola"
  const val DEBTOR = "d-ahmad"
  const val DRINKS = "نوشیدنی"

  private val products = listOf(
    Product(RICE, "برنجِ سلامِ هندیِ درجه‌یک (کیسهٔ پنج کیلویی)", "خوراکی", "کیلوگرم", salePrice = 380.0, barcodes = listOf("6260010071")),
    Product(OIL, "روغنِ آفتاب‌گردانِ ۱٫۸ لیتری", "خوراکی", "عدد", salePrice = 450.0),
    Product(SOAP, "صابونِ گلنار", "شوینده", "عدد", salePrice = 35.0),
    Product(CARTON, "نوشابهٔ قوطی", DRINKS, "کارتن", salePrice = 1_800.0, saleUnit = "قوطی", unitFactor = 1.0 / 24),
    Product("p-tea", "چای سبزِ احمد ۵۰۰ گرمی", DRINKS, "عدد", salePrice = 320.0),
    Product("p-water", "آبِ معدنی ۱٫۵ لیتری", DRINKS, "عدد", salePrice = 25.0),
    Product("p-juice", "آب‌میوهٔ انار", DRINKS, "عدد", salePrice = 60.0),
    Product("p-sugar", "شکر", "خوراکی", "کیلوگرم", salePrice = 95.0),
    Product("p-flour", "آردِ گندمِ کابل", "خوراکی", "کیلوگرم", salePrice = 70.0),
    Product("p-shampoo", "شامپوی کودک", "شوینده", "عدد", salePrice = 210.0),
    Product("p-pen", "خودکارِ آبی", "لوازم", "عدد", salePrice = 15.0),
    Product("p-notebook", "دفترِ صدبرگ", "لوازم", "عدد", salePrice = 85.0),
    Product("p-battery", "باتریِ قلمی (بستهٔ چهارتایی)", "لوازم", "بسته", salePrice = 120.0),
    Product("p-gone", "کبریت", "لوازم", "عدد", salePrice = 5.0),
  )

  private val stock = mapOf(
    RICE to 42.5, OIL to 18.0, SOAP to 24.0, CARTON to 6.0, "p-tea" to 12.0, "p-water" to 96.0, "p-juice" to 3.0,
    "p-sugar" to 60.0, "p-flour" to 120.0, "p-shampoo" to 9.0, "p-pen" to 200.0, "p-notebook" to 40.0, "p-battery" to 15.0,
  )

  /** فروش‌های گذشته — ترتیبِ «پرفروش‌ترین» از همین */
  private val sold = listOf(RICE, RICE, RICE, OIL, OIL, "p-water", "p-water", "p-tea", SOAP)

  val cart = listOf(
    CartLine(RICE, 2.5),
    CartLine(OIL, 2.0),
    CartLine("p-water", 6.0),
    CartLine("free-bag", 3.0, unitPrice = 15.0, label = "کیسه"),
  )

  fun ledger(): ShopData = ShopData(
    products = products,
    productCategories = listOf("خوراکی", DRINKS, "شوینده", "لوازم"),
    warehouseEntries = stock.map { (id, q) -> WarehouseEntry(id = "w-$id", productId = id, units = q) },
    sales = sold.indices.map { Sale(id = "s$it", invoiceNumber = 100 + it, status = "completed") },
    saleItems = sold.mapIndexed { i, id -> SaleItem(id = "i$i", saleId = "s$i", productId = id, quantity = 0.0) },
    debtors = listOf(
      Debtor(id = DEBTOR, name = "احمد ولی‌زاده", phone = "0700123456"),
      Debtor(id = "d-mahmood", name = "محمود (دکانِ کنارِ مسجد)"),
      Debtor(id = "d-karim", name = "کریم فروشگاهِ شمال", phone = "0799000111"),
    ),
    transactions = listOf(
      DebtTransaction(id = "t1", debtorId = DEBTOR, type = "give", amount = 12_450.0),
      DebtTransaction(id = "t2", debtorId = "d-karim", type = "receive", amount = 3_000.0),
    ),
  )
}
