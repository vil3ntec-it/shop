package ir.vil3ntec.tohid.ui.v3.art

import androidx.compose.ui.graphics.vector.ImageVector
import ir.vil3ntec.tohid.core.text.PlainError

/**
 *  صحنهٔ متحرکِ حالت‌های مشترک — یکی برای هر زمینه (دستهٔ ۶۵، e-rows-65a: صحنهٔ تختِ پهن روی خطِ زمین، سنگ و بوته، و
 *  آدمکی که بازی می‌کند؛ منبعِ طراحی `tools/art/scenes`، دادهٔ `ArtData*.kt`، موتورِ `ArtScene.kt`):
 *
 *  - [TREE]: پیش‌فرض — دختری زیرِ درخت کتاب می‌خواند، برگی می‌افتد و او بالا را نگاه می‌کند؛
 *  - [PRODUCTS]: فروشنده درِ کارتن را بالا می‌زند — جز یک شب‌پره چیزی نیست؛
 *  - [SALES]: صندوق‌دار پشتِ پیشخوان انگشت ضرب می‌گیرد و این‌سو و آن‌سو را می‌پاید؛
 *  - [PEOPLE]: پیرمردِ عینکی روی سنگ دفترِ خالی را ورق می‌زند و به شما لبخند می‌زند؛
 *  - [ORDERS]: پیک کنارِ موتورِ بسته‌دار گوشی را نگاه می‌کند، پا ضرب می‌گیرد و شانه بالا می‌اندازد؛
 *  - [STOCK]: انباردار با تخته‌یادداشت قفسهٔ خالی را می‌شمارد؛ عنکبوتی از طبقهٔ بالا پایین می‌آید؛
 *  - [SEARCH]: جوینده خم شده با ذره‌بین زمین را می‌گردد، بعد شانه بالا می‌اندازد؛
 *  - [ERROR_CLOUD]: ابرِ کوچک روی کمدِ سرور می‌بارد و فنی چتر را رویش گرفته (سرور)؛
 *  - [ERROR_CABLE]: فروشندهٔ پیش‌بند‌دار دوشاخهٔ کشیده را نگاه می‌کند، فوتش می‌کند و سرش را می‌خاراند (بی اینترنت)؛
 *  - [ERROR_MARK]: «!» ِ سنگی که نقطه‌اش می‌جهد و زنی که جا می‌خورد و شانه بالا می‌اندازد (دیگر).
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
