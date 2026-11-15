package ir.vil3ntec.tohid.ui.v3.art

import androidx.compose.ui.graphics.vector.ImageVector
import ir.vil3ntec.tohid.core.text.PlainError

/**
 *  تصویرِ متحرکِ حالت‌های مشترک (دستهٔ ۶۳، e-rows-63i) — یکی برای هر زمینه، همه با `Canvas` ِ برداری، بی bitmap و بی Lottie:
 *
 *  - [TREE]: پیش‌فرض — درختی که شاخه‌هایش را باد می‌برد؛
 *  - [PRODUCTS]: جعبهٔ کالا با گیاهِ کوچکی که برگ‌هایش در باد تاب می‌خورند؛
 *  - [SALES]: رسیدِ آویزان با سکه‌ای که می‌چرخد؛
 *  - [PEOPLE]: دو نفر (قرض‌داران، مشتریان، کارمندان) با سکهٔ شناور؛
 *  - [ORDERS]: سبدِ خرید با میوه و بطری که بالا و پایین می‌روند؛
 *  - [STOCK]: قفسه با جعبه‌هایی که کمی تکان می‌خورند و گلدانِ بالای آن؛
 *  - [SEARCH]: ذره‌بینی که می‌گردد، با جرقه‌های کوچک؛
 *  - [ERROR_CLOUD]: ابر با آذرخشِ کوچک (سرور)؛ [ERROR_CABLE]: دوشاخهٔ کشیده (بی اینترنت)؛ [ERROR_MARK]: بادکنکِ «!» (دیگر).
 */
enum class V3ArtKind {
  TREE, PRODUCTS, SALES, PEOPLE, ORDERS, STOCK, SEARCH, ERROR_CLOUD, ERROR_CABLE, ERROR_MARK;

  /** حالتِ خطا؟ ⇒ متن از `PlainError` می‌گذرد */
  val isError: Boolean get() = this == ERROR_CLOUD || this == ERROR_CABLE || this == ERROR_MARK

  companion object {
    /** آیکنِ امروزِ هر `V3EmptyState` ⇒ تصویرِ همان زمینه، بی دست زدن به صدها جای صدا زدن (با نام، بی ساختنِ آیکن) */
    fun forIcon(icon: ImageVector?): V3ArtKind = when (icon?.name?.substringAfterLast('.')) {
      null -> TREE
      "Inventory2", "Inventory", "Category", "AddBox", "Sell", "LocalOffer", "QrCode2" -> PRODUCTS
      "ReceiptLong", "Receipt", "Payments", "FactCheck", "AssignmentReturn", "PointOfSale", "RequestQuote", "InsertChart" -> SALES
      "PeopleOutline", "People", "Groups", "Person", "PersonOff", "PersonAdd", "SupervisorAccount", "Contacts" -> PEOPLE
      "ShoppingBasket", "ShoppingCart", "ShoppingBag", "LocalShipping", "DeliveryDining", "Storefront", "Store" -> ORDERS
      "SwapVert", "SwapHoriz", "Warehouse", "Shelves", "EventBusy", "UploadFile" -> STOCK
      "SearchOff", "Search", "ManageSearch", "QrCodeScanner" -> SEARCH
      "CloudOff", "SyncProblem", "CloudSync" -> ERROR_CLOUD
      "ErrorOutline", "Error", "ReportProblem", "Warning", "WarningAmber" -> ERROR_MARK
      "WifiOff", "SignalWifiOff", "PortableWifiOff", "SignalCellularOff", "SignalWifiConnectedNoInternet4" -> ERROR_CABLE
      else -> TREE
    }

    /** گونهٔ خطا ⇒ تصویر */
    fun forError(kind: PlainError.Kind): V3ArtKind = when (kind) {
      PlainError.Kind.OFFLINE -> ERROR_CABLE
      PlainError.Kind.SERVER -> ERROR_CLOUD
      PlainError.Kind.OTHER -> ERROR_MARK
    }
  }
}
