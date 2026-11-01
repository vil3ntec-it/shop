//  ⚠️ تولیدشده از keymap.json با `node android-native/app/src/main/java/ir/vil3ntec/tohid/core/keymap/gen-keymap.mjs` — دستی ویرایش نشود.
//  منبع: keymap.json در همین پوشه (MASTER-SPEC ۳.۱۶ و ۵.۲). آزمون: KeyMapTest.
package ir.vil3ntec.tohid.core.keymap

/** نامِ کلیدِ فیزیکی. `codes` همان KeyboardEvent.codeِ وب است؛ نگاشتِ Compose در ui/screens/KeyBinding.kt. */
enum class KeyName(val label: String, val codes: List<String>) {
  A("A", listOf("KeyA")),
  B("B", listOf("KeyB")),
  C("C", listOf("KeyC")),
  D("D", listOf("KeyD")),
  E("E", listOf("KeyE")),
  F("F", listOf("KeyF")),
  G("G", listOf("KeyG")),
  H("H", listOf("KeyH")),
  I("I", listOf("KeyI")),
  J("J", listOf("KeyJ")),
  K("K", listOf("KeyK")),
  L("L", listOf("KeyL")),
  M("M", listOf("KeyM")),
  N("N", listOf("KeyN")),
  O("O", listOf("KeyO")),
  P("P", listOf("KeyP")),
  Q("Q", listOf("KeyQ")),
  R("R", listOf("KeyR")),
  S("S", listOf("KeyS")),
  T("T", listOf("KeyT")),
  U("U", listOf("KeyU")),
  V("V", listOf("KeyV")),
  W("W", listOf("KeyW")),
  X("X", listOf("KeyX")),
  Y("Y", listOf("KeyY")),
  Z("Z", listOf("KeyZ")),
  D0("0", listOf("Digit0", "Numpad0")),
  D1("1", listOf("Digit1")),
  D2("2", listOf("Digit2")),
  D3("3", listOf("Digit3")),
  D4("4", listOf("Digit4")),
  D5("5", listOf("Digit5")),
  D6("6", listOf("Digit6")),
  D7("7", listOf("Digit7")),
  D8("8", listOf("Digit8")),
  D9("9", listOf("Digit9")),
  F1("F1", listOf("F1")),
  F2("F2", listOf("F2")),
  F3("F3", listOf("F3")),
  F4("F4", listOf("F4")),
  F5("F5", listOf("F5")),
  F6("F6", listOf("F6")),
  F7("F7", listOf("F7")),
  F8("F8", listOf("F8")),
  F9("F9", listOf("F9")),
  F10("F10", listOf("F10")),
  F11("F11", listOf("F11")),
  F12("F12", listOf("F12")),
  Enter("Enter", listOf("Enter", "NumpadEnter")),
  Escape("Esc", listOf("Escape")),
  Delete("Delete", listOf("Delete")),
  Tab("Tab", listOf("Tab")),
  Space("Space", listOf("Space")),
  Home("Home", listOf("Home")),
  End("End", listOf("End")),
  PageUp("PgUp", listOf("PageUp")),
  PageDown("PgDn", listOf("PageDown")),
  Up("↑", listOf("ArrowUp")),
  Down("↓", listOf("ArrowDown")),
  Left("←", listOf("ArrowLeft")),
  Right("→", listOf("ArrowRight")),
  Plus("+", listOf("NumpadAdd")),
  Minus("−", listOf("Minus", "NumpadSubtract")),
  Equals("=", listOf("Equal")),
  Slash("/", listOf("Slash")),
}

object KeyMap {
  val SCOPES: List<KeyScope> = listOf(
    KeyScope("global", KeyScope.Kind.GLOBAL, "همه‌جا"),
    KeyScope("sale", KeyScope.Kind.SCREEN, "صندوقِ فروش"),
    KeyScope("sale.payment", KeyScope.Kind.WINDOW, "پنلِ پرداخت", parent = "sale"),
    KeyScope("customers", KeyScope.Kind.SCREEN, "مشتری‌ها و قرض‌داران"),
    KeyScope("supplier", KeyScope.Kind.SCREEN, "حسابِ تأمین‌کننده"),
    KeyScope("purchase", KeyScope.Kind.SCREEN, "خرید"),
    KeyScope("orders", KeyScope.Kind.SCREEN, "سفارش‌های اینترنتی"),
    KeyScope("stocktake", KeyScope.Kind.SCREEN, "انبارگردانی"),
    KeyScope("reports", KeyScope.Kind.SCREEN, "گزارش‌ها"),
  )

  val GLOBAL_HELP = KeyAction(
    "global.help", "global", "راهنمای کلیدهای همین صفحه", KeyStatus.LIVE,
    keys = listOf(Chord(KeyName.F1)),
    web = listOf(Chord(KeyName.Slash, alt = true)),
  )
  val GLOBAL_PALETTE = KeyAction(
    "global.palette", "global", "جست‌وجوی فرمان و همه چیز", KeyStatus.LIVE,
    keys = listOf(Chord(KeyName.K, mod = true)),
    web = listOf(Chord(KeyName.K, mod = true)),
  )
  val GLOBAL_PAGE1 = KeyAction(
    "global.page1", "global", "داشبورد", KeyStatus.LIVE,
    keys = listOf(Chord(KeyName.D1, mod = true)),
    web = listOf(Chord(KeyName.D1, alt = true)),
    page = "dashboard",
  )
  val GLOBAL_PAGE2 = KeyAction(
    "global.page2", "global", "فروش", KeyStatus.LIVE,
    keys = listOf(Chord(KeyName.D2, mod = true)),
    web = listOf(Chord(KeyName.D2, alt = true)),
    page = "sale",
  )
  val GLOBAL_PAGE3 = KeyAction(
    "global.page3", "global", "قرض‌داران", KeyStatus.LIVE,
    keys = listOf(Chord(KeyName.D3, mod = true)),
    web = listOf(Chord(KeyName.D3, alt = true)),
    page = "debtors",
  )
  val GLOBAL_PAGE4 = KeyAction(
    "global.page4", "global", "انبار", KeyStatus.LIVE,
    keys = listOf(Chord(KeyName.D4, mod = true)),
    web = listOf(Chord(KeyName.D4, alt = true)),
    page = "warehouse",
  )
  val GLOBAL_PAGE5 = KeyAction(
    "global.page5", "global", "محصولات", KeyStatus.LIVE,
    keys = listOf(Chord(KeyName.D5, mod = true)),
    web = listOf(Chord(KeyName.D5, alt = true)),
    page = "products",
  )
  val GLOBAL_PAGE6 = KeyAction(
    "global.page6", "global", "تاریخچهٔ فروش", KeyStatus.LIVE,
    keys = listOf(Chord(KeyName.D6, mod = true)),
    web = listOf(Chord(KeyName.D6, alt = true)),
    page = "sales",
  )
  val GLOBAL_PAGE7 = KeyAction(
    "global.page7", "global", "خرید", KeyStatus.LIVE,
    keys = listOf(Chord(KeyName.D7, mod = true)),
    web = listOf(Chord(KeyName.D7, alt = true)),
    page = "purchasing",
  )
  val GLOBAL_PAGE8 = KeyAction(
    "global.page8", "global", "گزارش‌ها", KeyStatus.LIVE,
    keys = listOf(Chord(KeyName.D8, mod = true)),
    web = listOf(Chord(KeyName.D8, alt = true)),
    page = "reports",
  )
  val GLOBAL_PAGE9 = KeyAction(
    "global.page9", "global", "مصارف", KeyStatus.LIVE,
    keys = listOf(Chord(KeyName.D9, mod = true)),
    web = listOf(Chord(KeyName.D9, alt = true)),
    page = "expenses",
  )
  val GLOBAL_ORDERS = KeyAction(
    "global.orders", "global", "سفارش‌های اینترنتی", KeyStatus.PLANNED,
    keys = listOf(Chord(KeyName.O, mod = true)),
    web = listOf(Chord(KeyName.O, alt = true)),
  )
  val GLOBAL_ADMIN = KeyAction(
    "global.admin", "global", "مرکزِ مدیریت", KeyStatus.LIVE,
    keys = listOf(Chord(KeyName.M, mod = true)),
    web = listOf(Chord(KeyName.M, alt = true)),
  )
  val GLOBAL_NEW = KeyAction(
    "global.new", "global", "موردِ تازه در همین صفحه", KeyStatus.LIVE,
    keys = listOf(Chord(KeyName.N, mod = true)),
    web = listOf(Chord(KeyName.N, alt = true)),
  )
  val GLOBAL_FIND = KeyAction(
    "global.find", "global", "جست‌وجو در همین صفحه", KeyStatus.PARTIAL,
    keys = listOf(Chord(KeyName.F, mod = true)),
    web = listOf(Chord(KeyName.Slash)),
  )
  val GLOBAL_SAVE = KeyAction(
    "global.save", "global", "ذخیره یا تأیید", KeyStatus.PARTIAL,
    keys = listOf(Chord(KeyName.S, mod = true), Chord(KeyName.Enter, mod = true)),
    web = listOf(Chord(KeyName.S, mod = true), Chord(KeyName.Enter, mod = true)),
  )
  val GLOBAL_PRINT = KeyAction(
    "global.print", "global", "چاپ", KeyStatus.PARTIAL,
    keys = listOf(Chord(KeyName.P, mod = true)),
    web = listOf(Chord(KeyName.P, mod = true)),
  )
  val GLOBAL_EXPORT = KeyAction(
    "global.export", "global", "خروجی", KeyStatus.PARTIAL,
    keys = listOf(Chord(KeyName.E, mod = true)),
    web = listOf(Chord(KeyName.E, mod = true)),
  )
  val GLOBAL_LOCK = KeyAction(
    "global.lock", "global", "قفلِ صندوق و تعویضِ کاربر با PIN", KeyStatus.PARTIAL,
    keys = listOf(Chord(KeyName.L, mod = true)),
    web = listOf(Chord(KeyName.L, alt = true)),
  )
  val GLOBAL_ZOOM_IN = KeyAction(
    "global.zoomIn", "global", "بزرگ‌نمایی", KeyStatus.LIVE,
    keys = listOf(Chord(KeyName.Equals, mod = true), Chord(KeyName.Plus, mod = true)),
    web = emptyList(),
  )
  val GLOBAL_ZOOM_OUT = KeyAction(
    "global.zoomOut", "global", "کوچک‌نمایی", KeyStatus.LIVE,
    keys = listOf(Chord(KeyName.Minus, mod = true)),
    web = emptyList(),
  )
  val GLOBAL_ZOOM_RESET = KeyAction(
    "global.zoomReset", "global", "اندازهٔ عادیِ متن", KeyStatus.LIVE,
    keys = listOf(Chord(KeyName.D0, mod = true)),
    web = emptyList(),
  )
  val GLOBAL_FULLSCREEN = KeyAction(
    "global.fullscreen", "global", "تمام‌صفحه", KeyStatus.LIVE,
    keys = listOf(Chord(KeyName.F11)),
    web = emptyList(),
  )
  val GLOBAL_BACK = KeyAction(
    "global.back", "global", "برگشت یا بستن (هرگز حذف نمی‌کند)", KeyStatus.LIVE,
    keys = listOf(Chord(KeyName.Escape)),
    web = listOf(Chord(KeyName.Escape)),
  )
  val SALE_ADD = KeyAction(
    "sale.add", "sale", "افزودن (بارکد یا نام)", KeyStatus.LIVE,
    keys = listOf(Chord(KeyName.Enter)),
    web = listOf(Chord(KeyName.Enter)),
  )
  val SALE_UP = KeyAction(
    "sale.up", "sale", "ردیف یا پیشنهادِ بالا", KeyStatus.LIVE,
    keys = listOf(Chord(KeyName.Up)),
    web = listOf(Chord(KeyName.Up)),
  )
  val SALE_DOWN = KeyAction(
    "sale.down", "sale", "ردیف یا پیشنهادِ پایین", KeyStatus.LIVE,
    keys = listOf(Chord(KeyName.Down)),
    web = listOf(Chord(KeyName.Down)),
  )
  val SALE_QTY_UP = KeyAction(
    "sale.qtyUp", "sale", "یکی بیشتر", KeyStatus.LIVE,
    keys = listOf(Chord(KeyName.Plus), Chord(KeyName.Equals, shift = true)),
    web = listOf(Chord(KeyName.Plus), Chord(KeyName.Equals, shift = true)),
  )
  val SALE_QTY_DOWN = KeyAction(
    "sale.qtyDown", "sale", "یکی کمتر", KeyStatus.LIVE,
    keys = listOf(Chord(KeyName.Minus)),
    web = listOf(Chord(KeyName.Minus)),
  )
  val SALE_DELETE_ROW = KeyAction(
    "sale.deleteRow", "sale", "حذفِ ردیف", KeyStatus.LIVE,
    keys = listOf(Chord(KeyName.Delete)),
    web = listOf(Chord(KeyName.Delete)),
  )
  val SALE_UNDO = KeyAction(
    "sale.undo", "sale", "برگرداندن", KeyStatus.PLANNED,
    keys = listOf(Chord(KeyName.Z, mod = true)),
    web = listOf(Chord(KeyName.Z, mod = true)),
  )
  val SALE_CUSTOMER = KeyAction(
    "sale.customer", "sale", "انتخاب یا ساختِ مشتری", KeyStatus.LIVE,
    keys = listOf(Chord(KeyName.F2)),
    web = listOf(Chord(KeyName.C, alt = true)),
  )
  val SALE_EDIT_ROW = KeyAction(
    "sale.editRow", "sale", "ویرایشِ ردیف", KeyStatus.LIVE,
    keys = listOf(Chord(KeyName.F3)),
    web = listOf(Chord(KeyName.J, alt = true)),
  )
  val SALE_FREE_ITEM = KeyAction(
    "sale.freeItem", "sale", "قلمِ آزاد", KeyStatus.LIVE,
    keys = listOf(Chord(KeyName.F4)),
    web = listOf(Chord(KeyName.Q, alt = true)),
  )
  val SALE_DISCOUNT_ROW = KeyAction(
    "sale.discountRow", "sale", "تخفیفِ ردیف", KeyStatus.PLANNED,
    keys = listOf(Chord(KeyName.F5)),
    web = listOf(Chord(KeyName.X, alt = true)),
  )
  val SALE_DISCOUNT_INVOICE = KeyAction(
    "sale.discountInvoice", "sale", "تخفیفِ کلِ فاکتور", KeyStatus.PARTIAL,
    keys = listOf(Chord(KeyName.F5, shift = true)),
    web = listOf(Chord(KeyName.X, shift = true, alt = true)),
  )
  val SALE_HOLD = KeyAction(
    "sale.hold", "sale", "نگه‌داشتنِ سبد", KeyStatus.PARTIAL,
    keys = listOf(Chord(KeyName.F6)),
    web = listOf(Chord(KeyName.G, alt = true)),
  )
  val SALE_HELD_LIST = KeyAction(
    "sale.heldList", "sale", "فهرستِ سبدهای نگه‌داشته", KeyStatus.PARTIAL,
    keys = listOf(Chord(KeyName.F6, shift = true)),
    web = listOf(Chord(KeyName.G, shift = true, alt = true)),
  )
  val SALE_NEXT_OPEN = KeyAction(
    "sale.nextOpen", "sale", "فاکتورِ بازِ بعدی", KeyStatus.PARTIAL,
    keys = listOf(Chord(KeyName.PageDown, mod = true)),
    web = emptyList(),
  )
  val SALE_PREV_OPEN = KeyAction(
    "sale.prevOpen", "sale", "فاکتورِ بازِ قبلی", KeyStatus.PARTIAL,
    keys = listOf(Chord(KeyName.PageUp, mod = true)),
    web = emptyList(),
  )
  val SALE_RECEIVE = KeyAction(
    "sale.receive", "sale", "دریافتِ وجه یا قسط از مشتری", KeyStatus.PARTIAL,
    keys = listOf(Chord(KeyName.F7)),
    web = listOf(Chord(KeyName.R, alt = true)),
  )
  val SALE_REPRINT = KeyAction(
    "sale.reprint", "sale", "چاپِ دوبارهٔ آخرین رسید", KeyStatus.LIVE,
    keys = listOf(Chord(KeyName.F8)),
    web = listOf(Chord(KeyName.P, alt = true)),
  )
  val SALE_DRAWER = KeyAction(
    "sale.drawer", "sale", "باز کردنِ کشوی پول بدون فروش", KeyStatus.PLANNED,
    keys = listOf(Chord(KeyName.F8, shift = true)),
    web = emptyList(),
  )
  val SALE_PAY_PANEL = KeyAction(
    "sale.payPanel", "sale", "پنلِ پرداخت (امروز F9 فروش را فوری می‌بندد)", KeyStatus.PLANNED,
    keys = listOf(Chord(KeyName.F9)),
    web = listOf(Chord(KeyName.Enter, alt = true)),
  )
  val SALE_RETURN_MODE = KeyAction(
    "sale.returnMode", "sale", "حالتِ برگشت", KeyStatus.PLANNED,
    keys = listOf(Chord(KeyName.F9, shift = true)),
    web = listOf(Chord(KeyName.Enter, shift = true, alt = true)),
  )
  val SALE_FINISH_EXACT = KeyAction(
    "sale.finishExact", "sale", "پایانِ فوری با نقدِ دقیق", KeyStatus.LIVE,
    keys = listOf(Chord(KeyName.F12), Chord(KeyName.Enter, mod = true)),
    web = listOf(Chord(KeyName.Enter, mod = true)),
    overrides = listOf("global.save"),
  )
  val SALE_PRICE_CHECK = KeyAction(
    "sale.priceCheck", "sale", "قیمت‌پرسی بدون افزودن", KeyStatus.PLANNED,
    keys = listOf(Chord(KeyName.I, mod = true)),
    web = listOf(Chord(KeyName.I, alt = true)),
  )
  val SALE_UNIT = KeyAction(
    "sale.unit", "sale", "تغییرِ واحدِ ردیف", KeyStatus.PLANNED,
    keys = listOf(Chord(KeyName.U, mod = true)),
    web = listOf(Chord(KeyName.U, alt = true)),
  )
  val SALE_PRICE_LIST = KeyAction(
    "sale.priceList", "sale", "تغییرِ لیستِ قیمتِ فاکتور", KeyStatus.PLANNED,
    keys = listOf(Chord(KeyName.R, mod = true)),
    web = listOf(Chord(KeyName.R, shift = true, alt = true)),
  )
  val SALE_CLEAR = KeyAction(
    "sale.clear", "sale", "پاک کردنِ کادر؛ خالی کردنِ فاکتور فقط با تأیید", KeyStatus.LIVE,
    keys = listOf(Chord(KeyName.Escape)),
    web = listOf(Chord(KeyName.Escape)),
    overrides = listOf("global.back"),
  )
  val SALE_PAYMENT_SUBMIT = KeyAction(
    "sale.payment.submit", "sale.payment", "ثبت؛ مبلغِ خالی = نقدِ دقیق", KeyStatus.PLANNED,
    keys = listOf(Chord(KeyName.Enter)),
    web = listOf(Chord(KeyName.Enter)),
  )
  val SALE_PAYMENT_NEXT_FIELD = KeyAction(
    "sale.payment.nextField", "sale.payment", "فیلدِ بعدی", KeyStatus.PLANNED,
    keys = listOf(Chord(KeyName.Tab)),
    web = listOf(Chord(KeyName.Tab)),
  )
  val SALE_PAYMENT_PREV_FIELD = KeyAction(
    "sale.payment.prevField", "sale.payment", "فیلدِ قبلی", KeyStatus.PLANNED,
    keys = listOf(Chord(KeyName.Tab, shift = true)),
    web = listOf(Chord(KeyName.Tab, shift = true)),
  )
  val SALE_PAYMENT_NEXT_METHOD = KeyAction(
    "sale.payment.nextMethod", "sale.payment", "روشِ پرداختِ بعدی", KeyStatus.PLANNED,
    keys = listOf(Chord(KeyName.PageDown)),
    web = listOf(Chord(KeyName.PageDown)),
  )
  val SALE_PAYMENT_PREV_METHOD = KeyAction(
    "sale.payment.prevMethod", "sale.payment", "روشِ پرداختِ قبلی", KeyStatus.PLANNED,
    keys = listOf(Chord(KeyName.PageUp)),
    web = listOf(Chord(KeyName.PageUp)),
  )
  val SALE_PAYMENT_NOTE10 = KeyAction(
    "sale.payment.note10", "sale.payment", "اسکناسِ ۱۰", KeyStatus.PLANNED,
    keys = listOf(Chord(KeyName.D1, shift = true)),
    web = listOf(Chord(KeyName.D1, shift = true)),
  )
  val SALE_PAYMENT_NOTE20 = KeyAction(
    "sale.payment.note20", "sale.payment", "اسکناسِ ۲۰", KeyStatus.PLANNED,
    keys = listOf(Chord(KeyName.D2, shift = true)),
    web = listOf(Chord(KeyName.D2, shift = true)),
  )
  val SALE_PAYMENT_NOTE50 = KeyAction(
    "sale.payment.note50", "sale.payment", "اسکناسِ ۵۰", KeyStatus.PLANNED,
    keys = listOf(Chord(KeyName.D3, shift = true)),
    web = listOf(Chord(KeyName.D3, shift = true)),
  )
  val SALE_PAYMENT_NOTE100 = KeyAction(
    "sale.payment.note100", "sale.payment", "اسکناسِ ۱۰۰", KeyStatus.PLANNED,
    keys = listOf(Chord(KeyName.D4, shift = true)),
    web = listOf(Chord(KeyName.D4, shift = true)),
  )
  val SALE_PAYMENT_NOTE500 = KeyAction(
    "sale.payment.note500", "sale.payment", "اسکناسِ ۵۰۰", KeyStatus.PLANNED,
    keys = listOf(Chord(KeyName.D5, shift = true)),
    web = listOf(Chord(KeyName.D5, shift = true)),
  )
  val SALE_PAYMENT_NOTE1000 = KeyAction(
    "sale.payment.note1000", "sale.payment", "اسکناسِ ۱۰۰۰", KeyStatus.PLANNED,
    keys = listOf(Chord(KeyName.D6, shift = true)),
    web = listOf(Chord(KeyName.D6, shift = true)),
  )
  val SALE_PAYMENT_TOGGLE_PRINT = KeyAction(
    "sale.payment.togglePrint", "sale.payment", "چاپ برای همین فاکتور: روشن یا خاموش", KeyStatus.PLANNED,
    keys = listOf(Chord(KeyName.P, alt = true)),
    web = listOf(Chord(KeyName.P, alt = true)),
  )
  val SALE_PAYMENT_CUSTOMER = KeyAction(
    "sale.payment.customer", "sale.payment", "مشتری (برای باقی‌ماندهٔ قرضی)", KeyStatus.PLANNED,
    keys = listOf(Chord(KeyName.F2)),
    web = listOf(Chord(KeyName.C, alt = true)),
  )
  val SALE_PAYMENT_BACK = KeyAction(
    "sale.payment.back", "sale.payment", "برگشت به سبد، بی ثبتِ هیچ چیز", KeyStatus.PLANNED,
    keys = listOf(Chord(KeyName.Escape)),
    web = listOf(Chord(KeyName.Escape)),
  )
  val CUSTOMERS_LEND = KeyAction(
    "customers.lend", "customers", "قرض دادن", KeyStatus.LIVE,
    keys = listOf(Chord(KeyName.F6)),
    web = emptyList(),
  )
  val CUSTOMERS_RECEIVE = KeyAction(
    "customers.receive", "customers", "دریافت", KeyStatus.LIVE,
    keys = listOf(Chord(KeyName.F7)),
    web = emptyList(),
  )
  val CUSTOMERS_OPEN = KeyAction(
    "customers.open", "customers", "باز کردنِ حساب", KeyStatus.LIVE,
    keys = listOf(Chord(KeyName.Enter)),
    web = emptyList(),
  )
  val SUPPLIER_PAY = KeyAction(
    "supplier.pay", "supplier", "پرداخت", KeyStatus.LIVE,
    keys = listOf(Chord(KeyName.F7)),
    web = emptyList(),
  )
  val PURCHASE_NEXT = KeyAction(
    "purchase.next", "purchase", "فیلد یا ردیفِ بعدی", KeyStatus.LIVE,
    keys = listOf(Chord(KeyName.Enter)),
    web = emptyList(),
  )
  val PURCHASE_SAVE = KeyAction(
    "purchase.save", "purchase", "ذخیرهٔ فاکتور (Ctrl+S و Ctrl+Enter هم)", KeyStatus.LIVE,
    keys = listOf(Chord(KeyName.F9)),
    web = emptyList(),
  )
  val PURCHASE_CREATE_INLINE = KeyAction(
    "purchase.createInline", "purchase", "کالا یا تأمین‌کنندهٔ تازه، درجا", KeyStatus.PLANNED,
    keys = listOf(Chord(KeyName.N, mod = true, shift = true)),
    web = listOf(Chord(KeyName.N, shift = true, alt = true)),
  )
  val PURCHASE_DUPLICATE_ROW = KeyAction(
    "purchase.duplicateRow", "purchase", "تکرارِ ردیف", KeyStatus.PLANNED,
    keys = listOf(Chord(KeyName.D, mod = true)),
    web = emptyList(),
  )
  val PURCHASE_LAST_PRICES = KeyAction(
    "purchase.lastPrices", "purchase", "آخرین قیمت‌های خریدِ این کالا", KeyStatus.PLANNED,
    keys = listOf(Chord(KeyName.I, mod = true)),
    web = emptyList(),
  )
  val ORDERS_OPEN = KeyAction(
    "orders.open", "orders", "باز کردن", KeyStatus.PLANNED,
    keys = listOf(Chord(KeyName.Enter)),
    web = emptyList(),
  )
  val ORDERS_ADVANCE = KeyAction(
    "orders.advance", "orders", "قبول یا مرحلهٔ بعد", KeyStatus.PLANNED,
    keys = listOf(Chord(KeyName.F9)),
    web = emptyList(),
  )
  val ORDERS_REJECT = KeyAction(
    "orders.reject", "orders", "رد با دلیل", KeyStatus.PLANNED,
    keys = listOf(Chord(KeyName.F9, shift = true)),
    web = emptyList(),
  )
  val ORDERS_PRINT_SLIP = KeyAction(
    "orders.printSlip", "orders", "چاپِ برگه", KeyStatus.PLANNED,
    keys = listOf(Chord(KeyName.F8)),
    web = emptyList(),
  )
  val ORDERS_BUSY = KeyAction(
    "orders.busy", "orders", "حالتِ «شلوغم» (با مدت)", KeyStatus.PLANNED,
    keys = listOf(Chord(KeyName.B, mod = true)),
    web = emptyList(),
  )
  val ORDERS_PAUSE = KeyAction(
    "orders.pause", "orders", "توقفِ موقتِ سفارش‌گیری", KeyStatus.PLANNED,
    keys = listOf(Chord(KeyName.P, mod = true, shift = true)),
    web = emptyList(),
  )
  val STOCKTAKE_CONFIRM = KeyAction(
    "stocktake.confirm", "stocktake", "تأیید", KeyStatus.PLANNED,
    keys = listOf(Chord(KeyName.Enter)),
    web = emptyList(),
  )
  val STOCKTAKE_FINISH = KeyAction(
    "stocktake.finish", "stocktake", "بازبینی و پایان", KeyStatus.PLANNED,
    keys = listOf(Chord(KeyName.F9)),
    web = emptyList(),
  )
  val REPORTS_DRILL = KeyAction(
    "reports.drill", "reports", "ریز شدن در ردیف", KeyStatus.PLANNED,
    keys = listOf(Chord(KeyName.Enter)),
    web = emptyList(),
  )
  val REPORTS_NEXT_PERIOD = KeyAction(
    "reports.nextPeriod", "reports", "دورهٔ بعد", KeyStatus.PLANNED,
    keys = listOf(Chord(KeyName.Plus), Chord(KeyName.Equals, shift = true)),
    web = emptyList(),
  )
  val REPORTS_PREV_PERIOD = KeyAction(
    "reports.prevPeriod", "reports", "دورهٔ قبل", KeyStatus.PLANNED,
    keys = listOf(Chord(KeyName.Minus)),
    web = emptyList(),
  )
  val REPORTS_COMPARE = KeyAction(
    "reports.compare", "reports", "ستونِ مقایسه با دورهٔ قبل", KeyStatus.PLANNED,
    keys = listOf(Chord(KeyName.C, mod = true, shift = true)),
    web = emptyList(),
  )

  val ALL: List<KeyAction> = listOf(
    GLOBAL_HELP,
    GLOBAL_PALETTE,
    GLOBAL_PAGE1,
    GLOBAL_PAGE2,
    GLOBAL_PAGE3,
    GLOBAL_PAGE4,
    GLOBAL_PAGE5,
    GLOBAL_PAGE6,
    GLOBAL_PAGE7,
    GLOBAL_PAGE8,
    GLOBAL_PAGE9,
    GLOBAL_ORDERS,
    GLOBAL_ADMIN,
    GLOBAL_NEW,
    GLOBAL_FIND,
    GLOBAL_SAVE,
    GLOBAL_PRINT,
    GLOBAL_EXPORT,
    GLOBAL_LOCK,
    GLOBAL_ZOOM_IN,
    GLOBAL_ZOOM_OUT,
    GLOBAL_ZOOM_RESET,
    GLOBAL_FULLSCREEN,
    GLOBAL_BACK,
    SALE_ADD,
    SALE_UP,
    SALE_DOWN,
    SALE_QTY_UP,
    SALE_QTY_DOWN,
    SALE_DELETE_ROW,
    SALE_UNDO,
    SALE_CUSTOMER,
    SALE_EDIT_ROW,
    SALE_FREE_ITEM,
    SALE_DISCOUNT_ROW,
    SALE_DISCOUNT_INVOICE,
    SALE_HOLD,
    SALE_HELD_LIST,
    SALE_NEXT_OPEN,
    SALE_PREV_OPEN,
    SALE_RECEIVE,
    SALE_REPRINT,
    SALE_DRAWER,
    SALE_PAY_PANEL,
    SALE_RETURN_MODE,
    SALE_FINISH_EXACT,
    SALE_PRICE_CHECK,
    SALE_UNIT,
    SALE_PRICE_LIST,
    SALE_CLEAR,
    SALE_PAYMENT_SUBMIT,
    SALE_PAYMENT_NEXT_FIELD,
    SALE_PAYMENT_PREV_FIELD,
    SALE_PAYMENT_NEXT_METHOD,
    SALE_PAYMENT_PREV_METHOD,
    SALE_PAYMENT_NOTE10,
    SALE_PAYMENT_NOTE20,
    SALE_PAYMENT_NOTE50,
    SALE_PAYMENT_NOTE100,
    SALE_PAYMENT_NOTE500,
    SALE_PAYMENT_NOTE1000,
    SALE_PAYMENT_TOGGLE_PRINT,
    SALE_PAYMENT_CUSTOMER,
    SALE_PAYMENT_BACK,
    CUSTOMERS_LEND,
    CUSTOMERS_RECEIVE,
    CUSTOMERS_OPEN,
    SUPPLIER_PAY,
    PURCHASE_NEXT,
    PURCHASE_SAVE,
    PURCHASE_CREATE_INLINE,
    PURCHASE_DUPLICATE_ROW,
    PURCHASE_LAST_PRICES,
    ORDERS_OPEN,
    ORDERS_ADVANCE,
    ORDERS_REJECT,
    ORDERS_PRINT_SLIP,
    ORDERS_BUSY,
    ORDERS_PAUSE,
    STOCKTAKE_CONFIRM,
    STOCKTAKE_FINISH,
    REPORTS_DRILL,
    REPORTS_NEXT_PERIOD,
    REPORTS_PREV_PERIOD,
    REPORTS_COMPARE,
  )

  val RESERVED_NATIVE: List<ReservedKey> = listOf(
    ReservedKey(Chord(KeyName.F10), "ویندوز: منوی پنجره (۵.۱ بند ۴)"),
    ReservedKey(Chord(KeyName.F4, alt = true), "ویندوز: بستنِ پنجره"),
    ReservedKey(Chord(KeyName.Tab, alt = true), "ویندوز: جابه‌جایی میانِ برنامه‌ها"),
    ReservedKey(Chord(KeyName.Escape, alt = true), "ویندوز: پنجرهٔ بعدی"),
    ReservedKey(Chord(KeyName.Space, alt = true), "ویندوز: منوی سیستمیِ پنجره"),
    ReservedKey(Chord(KeyName.Escape, mod = true), "ویندوز: منوی Start"),
    ReservedKey(Chord(KeyName.Escape, mod = true, shift = true), "ویندوز: Task Manager"),
    ReservedKey(Chord(KeyName.Delete, mod = true, alt = true), "ویندوز: صفحهٔ امنیتی"),
    ReservedKey(Chord(KeyName.Tab, mod = true), "مک: Cmd+Tab جابه‌جایی میانِ برنامه‌ها"),
    ReservedKey(Chord(KeyName.Space, mod = true), "مک: Spotlight؛ ویندوز: عوض کردنِ IME"),
    ReservedKey(Chord(KeyName.Q, mod = true), "مک: بستنِ برنامه"),
    ReservedKey(Chord(KeyName.H, mod = true), "مک: پنهان کردنِ برنامه"),
    ReservedKey(Chord(KeyName.Escape, mod = true, alt = true), "مک: Force Quit"),
  )
  val RESERVED_WEB: List<ReservedKey> = listOf(
    ReservedKey(Chord(KeyName.T, mod = true), "مرورگر: زبانهٔ تازه (۵.۱ بند ۱۶)"),
    ReservedKey(Chord(KeyName.T, mod = true, shift = true), "مرورگر: بازگشاییِ زبانهٔ بسته"),
    ReservedKey(Chord(KeyName.W, mod = true), "مرورگر: بستنِ زبانه (۵.۱ بند ۱۶)"),
    ReservedKey(Chord(KeyName.W, mod = true, shift = true), "مرورگر: بستنِ پنجره"),
    ReservedKey(Chord(KeyName.N, mod = true), "مرورگر: پنجرهٔ تازه (۵.۱ بند ۱۶)"),
    ReservedKey(Chord(KeyName.N, mod = true, shift = true), "Chrome و Edge: پنجرهٔ ناشناس (۵.۱ بند ۸ و ۱۶)"),
    ReservedKey(Chord(KeyName.P, mod = true, shift = true), "Firefox: پنجرهٔ خصوصی"),
    ReservedKey(Chord(KeyName.Tab, mod = true), "مرورگر: زبانهٔ بعدی (۵.۱ بند ۱۶)"),
    ReservedKey(Chord(KeyName.Tab, mod = true, shift = true), "مرورگر: زبانهٔ قبلی"),
    ReservedKey(Chord(KeyName.Q, mod = true), "مک: بستنِ مرورگر"),
    ReservedKey(Chord(KeyName.H, mod = true), "Safari: پنهان کردن"),
    ReservedKey(Chord(KeyName.M, mod = true), "Safari: کوچک کردنِ پنجره"),
    ReservedKey(Chord(KeyName.R, mod = true), "مرورگر: بارگذاریِ دوباره"),
    ReservedKey(Chord(KeyName.R, mod = true, shift = true), "مرورگر: بارگذاریِ کامل"),
    ReservedKey(Chord(KeyName.L, mod = true), "مرورگر: نوارِ نشانی"),
    ReservedKey(Chord(KeyName.U, mod = true), "مرورگر: کدِ صفحه"),
    ReservedKey(Chord(KeyName.F, mod = true), "مرورگر: جست‌وجو در صفحه — وب «/» دارد"),
    ReservedKey(Chord(KeyName.I, mod = true, shift = true), "مرورگر: ابزارِ توسعه"),
    ReservedKey(Chord(KeyName.J, mod = true, shift = true), "مرورگر: کنسول"),
    ReservedKey(Chord(KeyName.C, mod = true, shift = true), "Chrome و Edge: بازرسیِ عنصر"),
    ReservedKey(Chord(KeyName.Delete, mod = true, shift = true), "مرورگر: پاک کردنِ داده‌ها"),
    ReservedKey(Chord(KeyName.Equals, mod = true), "مرورگر: بزرگ‌نمایی"),
    ReservedKey(Chord(KeyName.Plus, mod = true), "مرورگر: بزرگ‌نمایی"),
    ReservedKey(Chord(KeyName.Minus, mod = true), "مرورگر: کوچک‌نمایی"),
    ReservedKey(Chord(KeyName.D0, mod = true), "مرورگر: اندازهٔ عادی"),
    ReservedKey(Chord(KeyName.F, alt = true), "Chrome: منو (۵.۱ بند ۱۶)"),
    ReservedKey(Chord(KeyName.E, alt = true), "Chrome و Edge: منو (۵.۱ بند ۱۶)"),
    ReservedKey(Chord(KeyName.V, alt = true), "Firefox: منوی View (۵.۱ بند ۱۶)"),
    ReservedKey(Chord(KeyName.S, alt = true), "Firefox: منوی History (۵.۱ بند ۱۶)"),
    ReservedKey(Chord(KeyName.B, alt = true), "Firefox: منوی Bookmarks (۵.۱ بند ۱۶)"),
    ReservedKey(Chord(KeyName.T, alt = true), "Firefox: منوی Tools (۵.۱ بند ۱۶)"),
    ReservedKey(Chord(KeyName.H, alt = true), "مرورگر: منوی Help (۵.۱ بند ۱۶)"),
    ReservedKey(Chord(KeyName.D, alt = true), "مرورگر: نوارِ نشانی (۵.۱ بند ۱۶)"),
    ReservedKey(Chord(KeyName.Left, alt = true), "مرورگر: صفحهٔ قبل"),
    ReservedKey(Chord(KeyName.Right, alt = true), "مرورگر: صفحهٔ بعد"),
    ReservedKey(Chord(KeyName.Home, alt = true), "مرورگر: صفحهٔ خانه"),
    ReservedKey(Chord(KeyName.F4, alt = true), "ویندوز: بستنِ پنجره"),
    ReservedKey(Chord(KeyName.Tab, alt = true), "ویندوز: جابه‌جایی میانِ برنامه‌ها"),
    ReservedKey(Chord(KeyName.F1), "مرورگر: راهنما — فقط پس از آزمونِ Playwright (۵.۱ بند ۱۶)"),
    ReservedKey(Chord(KeyName.F3), "مرورگر: جست‌وجوی بعدی"),
    ReservedKey(Chord(KeyName.F5), "مرورگر: بارگذاریِ دوباره — فقط پس از آزمونِ Playwright (۵.۱ بند ۱۶)"),
    ReservedKey(Chord(KeyName.F6), "مرورگر: نوارِ نشانی"),
    ReservedKey(Chord(KeyName.F7), "مرورگر: caret browsing — فقط پس از آزمونِ Playwright (۵.۱ بند ۱۶)"),
    ReservedKey(Chord(KeyName.F10), "مرورگر و ویندوز: منو"),
    ReservedKey(Chord(KeyName.F11), "مرورگر: تمام‌صفحه"),
    ReservedKey(Chord(KeyName.F12), "مرورگر: ابزارِ توسعه — فقط پس از آزمونِ Playwright (۵.۱ بند ۱۶)"),
  )
}
