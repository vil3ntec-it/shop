package ir.vil3ntec.tohid.core.guide

/*
 *  OPS-06 — راهنمای هر صفحه (F1 ِ کامپیوتر، بالای فهرستِ میان‌برها) و ویدیوی کوتاهِ آموزشی.
 *
 *  متنِ ۱۶ صفحه را Claude نوشته (O-14 ب: صاحب‌کار هر وقت خواست اصلاح می‌کند؛ ویدیو بعداً،
 *  `docs/spec/OWNER-ACTIONS-fa.md` بخشِ ۷). هر گام همان کاری است که صفحهٔ کامپیوترِ امروز
 *  می‌کند؛ نامِ دکمه و کادر میانِ «» (انگلیسی میانِ “”) بایت‌به‌بایت همان نوشتهٔ جدولِ `Str*` است
 *  (`ScreenGuidesTextTest` ِ اندروید). عنوان همان سرخطِ صفحه با پشتوی جدول؛ پشتوی گام‌ها هنوز
 *  نیست ⇒ همان فارسی (قاعدهٔ `I18n.kt`)؛ دری = همان فارسی از `DariText`.
 *
 *  صفحهٔ تازه: ردیفی با [GuideStatus.PLACEHOLDER] هیچ چیزی نشان نمی‌دهد (نه «به‌زودی»، نه متنِ
 *  نیمه‌کاره). پر کردن: `steps` (۱ تا [MAX_STEPS] گام، هر کدام حداکثر [MAX_STEP] نویسه، فارسی
 *  اجباری) و اگر هست `video`، با `status = READY`. [problems] قاعده‌ها را می‌سنجد و ردیفِ READY ِ
 *  ناقص هم نشان داده نمی‌شود (آزمون: `ScreenGuidesTest`).
 *
 *  شناسهٔ صفحه همان شناسهٔ `ui/AppNav.SHORTCUTS` است (`dashboard`، `sale`، …)؛ `AppRoot` مسیرِ
 *  نسخهٔ ۳ را با [ScreenGuides.screenOf] به آن می‌رساند («خانه» ⇐ `dashboard`، `stock` ⇐ `warehouse`).
 */

enum class GuideStatus { PLACEHOLDER, READY }

/** یک نوشته در سه زبان؛ انگلیسی یا پشتوی خالی ⇒ همان فارسی (قاعدهٔ `I18n.kt`)؛ دری (`prs`) ⇒ فارسی از `DariText` */
data class GuideText(val fa: String, val en: String = "", val ps: String = "") {
  fun of(lang: String): String = when (lang) {
    "en" -> en.ifBlank { fa }
    "ps" -> ps.ifBlank { fa }
    "prs" -> ir.vil3ntec.tohid.core.text.DariText.of(fa)
    else -> fa
  }
}

/**
 *  ویدیوی کوتاه: فایلِ MP4 روی سرورِ خودمان (`/guides/<نام>.mp4`)، نه سرویسِ بیرونی
 *  (در افغانستان در دسترس نیست یا گران است). [seconds] برای نشان دادنِ «۴۵ ثانیه» پیش از دانلود.
 */
data class GuideVideo(val path: String, val seconds: Int, val bytes: Long)

data class ScreenGuide(
  val screen: String,
  val title: GuideText,
  val steps: List<GuideText> = emptyList(),
  val video: GuideVideo? = null,
  val status: GuideStatus = GuideStatus.PLACEHOLDER,
)

object ScreenGuides {

  const val MAX_STEPS = 7
  const val MAX_STEP = 160
  const val MIN_VIDEO_SECONDS = 15
  const val MAX_VIDEO_SECONDS = 120
  /** ویدیوی بزرگ‌تر برای اینترنتِ کند و گران نیست */
  const val MAX_VIDEO_BYTES = 8L * 1024 * 1024

  private val SCREEN_ID = Regex("^[a-z][a-z0-9_-]{1,30}$")
  private val VIDEO_PATH = Regex("^/guides/[a-z0-9][a-z0-9-]{0,60}\\.mp4$")

  /** راهنمای آماده: گام‌ها فارسی و انگلیسی؛ پشتوی خالی ⇒ فارسی */
  private fun ready(screen: String, title: GuideText, vararg steps: GuideText) =
    ScreenGuide(screen, title, steps.toList(), status = GuideStatus.READY)

  /** مسیرِ نسخهٔ ۳ که شناسهٔ راهنمایش دیگر است */
  private val ROUTE_ALIASES = mapOf("home" to "dashboard", "stock" to "warehouse")

  /** ریشهٔ مسیرِ باز (`RoutePaths.base`) ⇒ شناسهٔ راهنما */
  fun screenOf(base: String): String = ROUTE_ALIASES[base] ?: base

  /**
   *  همهٔ صفحه‌هایی که راهنما می‌گیرند — ترتیب = ترتیبِ فهرستِ `OWNER-ACTIONS-fa.md` بخشِ ۷.
   *  متن روی کامپیوتر نشان داده می‌شود (F1)، پس گام‌ها کارِ صفحهٔ کامپیوتر را می‌گویند.
   */
  val ALL: List<ScreenGuide> = listOf(
    ready(
      "dashboard", GuideText("خانه", "Home", "کور"),
      GuideText(
        "«فروشِ امروز» فروشِ امروز را با همین ساعتِ دیروز می‌سنجد؛ با زدنش «گزارش‌ها» باز می‌شود.",
        "“Today's sales” compares today with the same hour yesterday; click it to open “Reports”.",
      ),
      GuideText(
        "کادرِ «صندوق» پولِ نقدِ دخل را همین حالا نشان می‌دهد.",
        "The “Drawer” card shows the cash in the drawer right now.",
      ),
      GuideText(
        "میان‌برها کار را یک‌راست باز می‌کنند: «سفارش‌های آنلاین»، «دریافت» از قرض‌دار، «ورودِ کالا» و «تاریخچه».",
        "The shortcuts open each job directly: “Online orders”, “Receive” from a debtor, “Stock in” and “History”.",
      ),
      GuideText(
        "«کارهای فوری» فقط وقتی چیزی منتظرِ شماست می‌آید، مثلِ سفارشِ تازه یا کالای تمام‌شده؛ «همه» فهرستِ کامل را باز می‌کند.",
        "“Needs attention” appears only when something is waiting, such as a new order or an item that ran out; “All” opens the full list.",
      ),
      GuideText(
        "برای فروش «فروشِ تازه» ِ بالای صفحه یا Ctrl+2 را بزنید؛ Ctrl+K هر کالا، مشتری یا صفحه را پیدا می‌کند.",
        "To sell, click “New sale” at the top or press Ctrl+2; Ctrl+K finds any item, customer or page.",
      ),
    ),
    ready(
      "sale", GuideText("فروش", "Sale", "پلور"),
      GuideText(
        "بارکدِ کالا را با بارکدخوان بخوانید یا نامش را در کادرِ بالا بنویسید و Enter بزنید؛ ۳* پیش از آن یعنی سه تا.",
        "Scan the item with the barcode scanner, or type its name in the box at the top and press Enter; 3* before it means three.",
      ),
      GuideText(
        "در سبد، ردیف را انتخاب کنید و تعداد را با + و − عوض کنید؛ F3 قیمت یا تعدادِ دستی است و Delete ردیف را حذف می‌کند.",
        "In the cart, select a row and change the quantity with + and −; F3 sets the price or quantity by hand and Delete removes the row.",
      ),
      GuideText(
        "فروشِ قرضی: F2 بزنید یا روی «مشتریِ گذری» ِ سبد کلیک کنید و مشتری را انتخاب کنید.",
        "Selling on credit: press F2 or click “Walk-in customer” in the cart and choose the customer.",
      ),
      GuideText(
        "نوعِ پرداخت را بزنید («نقدی» یا «نسیه») و اگر لازم است «تخفیف» را بنویسید.",
        "Pick the payment type (“Cash” or “Credit”) and type a “Discount” if needed.",
      ),
      GuideText(
        "«ثبتِ فروش» یا F9 را بزنید؛ رسید را با F8 یا «چاپِ فاکتورِ آخر» چاپ کنید.",
        "Click “Record sale” or press F9; print the receipt with F8 or “Print last invoice”.",
      ),
      GuideText(
        "کالایی که در فهرست نیست: «قلمِ آزاد» (F4) را بزنید و نام و قیمتش را بنویسید.",
        "An item that is not in the list: click “Custom item” (F4) and type its name and price.",
      ),
      GuideText(
        "Esc کادرِ جست‌وجو را پاک می‌کند؛ خالی کردنِ سبد با «خالی» همیشه تأیید می‌خواهد.",
        "Esc clears the search box; emptying the cart with “Clear” always asks you to confirm.",
      ),
    ),
    ready(
      "debtors", GuideText("قرض‌داران", "Debtors", "پوروړي"),
      GuideText(
        "نام یا تلفنِ مشتری را در کادرِ جست‌وجو بنویسید؛ کارتِ هر مشتری مانده و روزهای قرضش را نشان می‌دهد.",
        "Type the customer's name or phone in the search box; each card shows the balance and how many days the debt is old.",
      ),
      GuideText(
        "مشتریِ تازه: نامش را در «مشتریِ تازه — نام» و اگر خواستید «تلفن» را بنویسید و Enter بزنید.",
        "New customer: type the name in “New customer — name”, and the “Phone” if you like, then press Enter.",
      ),
      GuideText(
        "روی کارتِ مشتری بزنید تا «صورت‌حساب»، «فاکتورها» و «اقلامِ برداشته» ِ او باز شود.",
        "Click a customer's card to open their “Statement”, “Invoices” and “Items taken”.",
      ),
      GuideText(
        "پولی که آورد: «دریافتِ وجه» (F7) را بزنید، «مبلغ (؋)» را بنویسید و Enter بزنید.",
        "Money they brought: click “Receive payment” (F7), type the “Amount (؋)” and press Enter.",
      ),
      GuideText(
        "قرضی که بیرون از صندوق دادید: «قرضِ دستی» (F6)؛ «سررسید (اختیاری)» را هم می‌توانید بگذارید.",
        "Credit you gave outside the till: “Manual credit” (F6); you can also set a “Due date (optional)”.",
      ),
      GuideText(
        "Ctrl+P همان زبانه را چاپ می‌کند و Ctrl+E آن را به اکسل می‌برد؛ Esc به کارت‌ها برمی‌گردد.",
        "Ctrl+P prints the open tab and Ctrl+E saves it to Excel; Esc goes back to the cards.",
      ),
    ),
    ready(
      "receipts", GuideText("رسیدها", "Receipts", "رسیدونه"),
      GuideText(
        "زبانهٔ «رسیدهای قرض‌داران» پولی است که از قرض‌داران گرفته‌اید؛ زبانهٔ «مصارف» خرج‌های دکان است.",
        "The “Debtor receipts” tab is money you collected from debtors; the “Expenses” tab is what the shop spent.",
      ),
      GuideText(
        "با «همهٔ سال‌ها» و «همهٔ ماه‌ها» سال و ماهِ خورشیدی را انتخاب کنید؛ در زبانهٔ اول «همهٔ قرض‌داران» یک نفر را جدا می‌کند.",
        "Use “All years” and “All months” to choose a solar year and month; on the first tab “All debtors” picks one person.",
      ),
      GuideText(
        "کادرِ «جمع» مبلغِ همهٔ ردیف‌های همین انتخاب را نشان می‌دهد.",
        "The “Total” card shows the amount of all rows in this selection.",
      ),
      GuideText(
        "روی یک رسید بزنید تا حسابِ همان قرض‌دار باز شود؛ روی یک مصرف، برگهٔ همان مصرف.",
        "Click a receipt to open that debtor's account; click an expense to open that expense.",
      ),
      GuideText(
        "این صفحه فقط برای دیدن است؛ دریافتِ تازه را از «قرض‌داران» یا «دریافت» ِ خانه ثبت کنید.",
        "This page is only for viewing; record new payments from “Debtors” or “Receive” on the home page.",
      ),
    ),
    ready(
      "products", GuideText("کالاها", "Products", "توکي"),
      GuideText(
        "«افزودن کالا» (Ctrl+N) را بزنید تا ردیفِ «کالای تازه» آماده شود.",
        "Click “Add item” (Ctrl+N) to get the “New item” row ready.",
      ),
      GuideText(
        "نام، بارکد (بارکدخوان هم می‌خواند)، دسته، واحد، قیمتِ خرید و فروش و موجودی را بنویسید؛ Tab خانهٔ بعد است.",
        "Type the name, barcode (the scanner can fill it), category, unit, cost and sale price and stock; Tab moves to the next cell.",
      ),
      GuideText(
        "Enter بزنید تا کالا ثبت شود؛ ردیفِ بعدی برای کالای دیگر آماده می‌ماند.",
        "Press Enter to save the item; the next row stays ready for another item.",
      ),
      GuideText(
        "برای پیدا کردن، نام، دسته یا بارکد را در کادرِ جست‌وجو (Ctrl+F) بنویسید یا دسته را انتخاب کنید.",
        "To find an item, type its name, category or barcode in the search box (Ctrl+F), or pick a category.",
      ),
      GuideText(
        "روی کالا بزنید تا «ویرایشِ کالا» کنارِ فهرست باز شود؛ نام یا قیمت را عوض کنید و Enter یا Ctrl+S بزنید.",
        "Click an item to open “Edit item” beside the list; change the name or price and press Enter or Ctrl+S.",
      ),
      GuideText(
        "چند کالا را تیک بزنید تا کارِ گروهی بیاید: «خروجی»، «فعال»، «غیرفعال» یا «حذف» (حذف فقط صاحب یا مدیر).",
        "Tick several items for bulk actions: “Export”, “Activate”, “Deactivate” or “Delete” (delete is for the owner or a manager).",
      ),
    ),
    ready(
      "warehouse", GuideText("انبار", "Warehouse", "ګدام"),
      GuideText(
        "«ورود کالا» را بزنید، «کالا» را انتخاب کنید یا بارکدش را بخوانید و «تعداد واحد» یا «تعداد کارتن» را بنویسید.",
        "Click “Receive stock”, choose the “Item” or scan its barcode, and type the “Units” or “Cartons”.",
      ),
      GuideText(
        "«قیمت خرید (هر واحد)» و اگر دارد «تاریخ انقضا» را بنویسید و «ثبت ورود» را بزنید.",
        "Type the “Purchase price (per unit)” and, if it has one, the “Expiry date”, then click “Save delivery”.",
      ),
      GuideText(
        "با «کم» و «تمام‌شده» کالاهایی را ببینید که باید دوباره بخرید؛ «نزدیک به انقضا» وقتی کالایی نزدیکِ انقضاست هم می‌آید.",
        "Use “Low” and “Out of stock” to see what to buy again; “Expiring soon” also appears when an item is close to its expiry date.",
      ),
      GuideText(
        "روی کالا بزنید تا موجودی و ورودهایش کنارِ فهرست باز شود؛ «حرکات انبار» همهٔ رفت‌وآمدش را نشان می‌دهد.",
        "Click an item to see its stock and deliveries beside the list; “Stock movements” shows everything that came in and went out.",
      ),
      GuideText(
        "شمارِ واقعی فرق دارد؟ «اصلاح موجودی» را بزنید و دلیلش را بنویسید.",
        "Is the real count different? Click “Adjust stock” and write the reason.",
      ),
      GuideText(
        "«انبارگردانی» برای شمردنِ همهٔ انبار است و «ورودِ چند قلم با هم» برای چند کالا در یک بار.",
        "“Stock count” is for counting the whole warehouse and “Receive several items” for many items at once.",
      ),
    ),
    ready(
      "purchasing", GuideText("خرید و تأمین‌کننده", "Purchases & suppliers", "پېرود او عرضه‌کوونکي"),
      GuideText(
        "«فاکتورهای خرید» همهٔ خریدها را نشان می‌دهد؛ روی یکی بزنید تا ردیف‌هایش با چاپ و اکسل کنارش باز شود.",
        "“Purchase invoices” lists every purchase; click one to see its rows beside it, with print and Excel.",
      ),
      GuideText(
        "خریدِ تازه: دکمهٔ فاکتورِ خریدِ تازه یا Ctrl+N را بزنید و تأمین‌کننده را از فهرست انتخاب کنید.",
        "New purchase: click the new purchase invoice button or press Ctrl+N, then choose the supplier from the list.",
      ),
      GuideText(
        "تأمین‌کنندهٔ تازه است؟ نامش را در «یا تأمین‌کنندهٔ تازه + Enter» بنویسید و Enter بزنید.",
        "A new supplier? Type the name in “Or a new supplier + Enter” and press Enter.",
      ),
      GuideText(
        "در هر ردیف «کالا (نام یا بارکد)»، «مقدار» و «فی (؋)» را بنویسید؛ Enter به خانه یا ردیفِ بعد می‌رود و بارکدخوان هم ردیف می‌افزاید.",
        "In each row type the “Item (name or barcode)”, “Quantity” and “Unit price (؋)”; Enter moves on and the scanner adds a row.",
      ),
      GuideText(
        "پولی که همین حالا دادید در «پرداخت‌شده (؋)» بنویسید؛ باقی به حسابِ تأمین‌کننده می‌رود. F9 فاکتور را ثبت می‌کند.",
        "Type what you paid now in “Paid (؋)”; the rest goes to the supplier's account. F9 saves the invoice.",
      ),
      GuideText(
        "در زبانهٔ «تأمین‌کنندگان» روی هر کدام بزنید تا صورت‌حسابش را ببینید؛ F7 پرداخت به او را ثبت می‌کند.",
        "On the “Suppliers” tab, click one to see their statement; F7 records a payment to them.",
      ),
    ),
    ready(
      "sales", GuideText("تاریخچه فروش", "Sales history", "د پلور تاریخچه"),
      GuideText(
        "«روز»، «ماه» یا «سال» را بزنید تا فروش‌ها به همان دوره دسته شوند و جمعِ هر دوره را ببینید.",
        "Click “Day”, “Month” or “Year” to group sales by that period and see each period's total.",
      ),
      GuideText(
        "شمارهٔ فاکتور یا نامِ مشتری را در کادرِ جست‌وجو بنویسید.",
        "Type an invoice number or a customer name in the search box.",
      ),
      GuideText(
        "روی یک فاکتور بزنید تا باز شود؛ «چاپ» رسیدش را دوباره چاپ می‌کند.",
        "Click an invoice to open it; “Print” prints its receipt again.",
      ),
      GuideText(
        "کالای پس‌آورده: در منوی سه‌نقطهٔ فاکتور «مرجوعی» را بزنید و کالا و تعداد را انتخاب کنید.",
        "Goods brought back: in the invoice's three-dot menu click “Return” and choose the items and quantities.",
      ),
      GuideText(
        "فاکتورِ اشتباه: «اصلاح فاکتور» یا «لغو فروش» را با نوشتنِ دلیل بزنید (فقط اگر اجازه دارید).",
        "A wrong invoice: use “Fix invoice” or “Cancel sale” and write the reason (only if you have permission).",
      ),
      GuideText(
        "«جدولِ فاکتورها» همهٔ فاکتورها را جدولی و با خروجیِ اکسل نشان می‌دهد.",
        "“Invoice table” shows all invoices as a table, with Excel export.",
      ),
    ),
    ready(
      "expenses", GuideText("مصارف", "Expenses", "لګښتونه"),
      GuideText(
        "«مصرف تازه» را بزنید، «مبلغ (افغانی)» را بنویسید و «دسته» را انتخاب کنید یا «دستهٔ تازه» بسازید.",
        "Click “New expense”, type the “Amount (AFN)” and choose a “Category”, or make a “New category”.",
      ),
      GuideText(
        "اگر خواستید «بابت چی؟ (اختیاری)» و «تاریخ» را هم بنویسید و «ذخیره» را بزنید.",
        "If you like, also fill “What for? (optional)” and the “Date”, then click “Save”.",
      ),
      GuideText(
        "بالای صفحه با پیکان‌ها یا زدنِ نامِ ماه، ماهِ دیگری را ببینید؛ کادرِ بزرگ جمعِ مصارفِ همان ماه است.",
        "At the top, use the arrows or click the month name to see another month; the big card is that month's total.",
      ),
      GuideText(
        "روی نامِ یک دسته در نوارِ رنگی بزنید تا فقط مصارفِ همان دسته بیاید.",
        "Click a category in the coloured bar to see only that category's expenses.",
      ),
      GuideText(
        "روی یک مصرف بزنید تا «ویرایش مصرف» باز شود؛ مبلغ را درست کنید یا «حذف» را بزنید.",
        "Click an expense to open “Edit expense”; fix the amount or click “Delete”.",
      ),
    ),
    ready(
      "reports", GuideText("گزارش‌ها", "Reports", "راپورونه"),
      GuideText(
        "در ستونِ «نوعِ گزارش» یکی را بزنید، مثلاً «فروش»، «سود و زیان»، «مصارف» یا «قرض‌داران».",
        "In the “Report” column click one, such as “Sales”, “Profit & loss”, “Expenses” or “Debtors”.",
      ),
      GuideText(
        "بازه را انتخاب کنید: «امروز»، «۷ روز»، «این ماه»، «ماهِ خورشیدی»، «سالِ خورشیدی» یا «از … تا …» برای تاریخِ دلخواه.",
        "Choose the period: “Today”, “7 days”, “This month”, “Solar month”, “Solar year”, or “From … to …” for your own dates.",
      ),
      GuideText(
        "بالای جدول عددهای کلِ همان بازه است و زیرش ردیف‌ها.",
        "The totals for that period are above the table, with the rows below.",
      ),
      GuideText(
        "«چاپ» (Ctrl+P) گزارش را چاپ می‌کند و «اکسل» (Ctrl+E) آن را در پروندهٔ اکسل ذخیره می‌کند.",
        "“Print” (Ctrl+P) prints the report and “Excel” (Ctrl+E) saves it to an Excel file.",
      ),
      GuideText(
        "«کالاها»، «قرض‌داران» و «انقضا» بازه ندارند و «وضعیتِ همین حالا» را نشان می‌دهند.",
        "“Items”, “Debtors” and “Expiry” have no period; they show “As of right now”.",
      ),
    ),
    ready(
      "orders", GuideText("سفارش‌ها", "Orders", "فرمایشونه"),
      GuideText(
        "سفارش‌های ویترین، تلفن، تلگرام و واتس‌اپ در همین صف می‌آیند؛ سفارشِ تازه در زبانهٔ «جدید» است.",
        "Orders from the storefront, phone, Telegram and WhatsApp arrive in this one queue; new orders are on the “New” tab.",
      ),
      GuideText(
        "روی سفارش بزنید تا اقلام، مشتری و نشانی کنارِ فهرست باز شود.",
        "Click an order to see its items, customer and address beside the list.",
      ),
      GuideText(
        "«قبول» را بزنید، یا «رد» را با یک دلیل مثلِ «ناموجود».",
        "Click “Accept”, or “Reject” with a reason such as “Out of stock”.",
      ),
      GuideText(
        "سپس به ترتیب «شروعِ جمع‌آوری»، «بسته شد» و بعد «آماده برای تحویل» یا «ارسال شد» را بزنید.",
        "Then click “Start picking”, “Packed”, and then “Ready for pickup” or “Sent out”.",
      ),
      GuideText(
        "وقتی مشتری گرفت یا پیک رساند، «مشتری گرفت» یا «تحویل شد» را بزنید تا سفارش به «تمام‌شده» برود.",
        "When the customer collects it or the courier delivers it, click “Customer picked up” or “Delivered” and it moves to “Done”.",
      ),
      GuideText(
        "سفارشِ تلفنی را خودتان با «سفارشِ دستی» (Ctrl+N) ثبت کنید.",
        "Record a phone order yourself with “Manual order” (Ctrl+N).",
      ),
    ),
    ready(
      "storefront", GuideText("ویترین", "Storefront", "ویترین"),
      GuideText(
        "در زبانهٔ «تنظیمات» نام، «نشانی (زیرِ vill3n.top)» و «ساعتِ کاری» را بنویسید و «ویترین روشن باشد» را روشن کنید.",
        "On the “Settings” tab fill the name, “Address (under vill3n.top)” and “Working hours”, and turn on “Storefront on”.",
      ),
      GuideText(
        "«دریافت از دکان» یا «ارسال به نشانیِ مشتری» را روشن کنید (دست‌کم یکی)، «هزینهٔ ارسال» را بگذارید و «ذخیرهٔ ویترین» را بزنید.",
        "Turn on “Pickup from the shop” or “Deliver to the customer's address” (at least one), set the “Delivery fee” and click “Save storefront”.",
      ),
      GuideText(
        "در زبانهٔ «کالاها» کلیدِ کنارِ هر کالا آن را در ویترین می‌گذارد یا از ویترین برمی‌دارد.",
        "On the “Products” tab, the switch next to each item puts it in the storefront or takes it out.",
      ),
      GuideText(
        "روی یک کالا بزنید تا «قیمتِ آنلاین (خالی = قیمتِ دفتر)» را بگذارید؛ کالای بی‌قیمت در ویترین دیده نمی‌شود.",
        "Click an item to set its “Online price (empty = ledger price)”; an item with no price is not shown in the storefront.",
      ),
      GuideText(
        "زبانهٔ «وضعیت» نشان می‌دهد مشتری الان چه می‌بیند؛ سفارش‌ها در صفحهٔ «سفارش‌ها» می‌آیند.",
        "The “Status” tab shows what customers see now; orders arrive on the “Orders” page.",
      ),
    ),
    ready(
      "promos", GuideText("تخفیف‌ها", "Discounts", "تخفیفونه"),
      GuideText(
        "کلیدِ «تخفیف‌های خودکار در صندوق» را روشن کنید؛ خاموش باشد صندوق هیچ قاعده‌ای را حساب نمی‌کند.",
        "Turn on “Automatic discounts at the till”; while it is off the till applies no rule at all.",
      ),
      GuideText(
        "«+ قاعدهٔ تازه» را بزنید و «نام (روی سبد دیده می‌شود)» را بنویسید.",
        "Click “+ New rule” and type the “Name (shown on the cart)”.",
      ),
      GuideText(
        "«نوع» را انتخاب کنید، مثلاً «درصدی»، «مبلغ از هر واحد» یا «بخر X بگیر Y»، و مقدارِ تخفیف را بنویسید.",
        "Choose the “Type”, such as “Percent”, “Amount off each unit” or “Buy X get Y”, and type the discount.",
      ),
      GuideText(
        "در «روی کدام کالاها» دسته‌ها یا «کالاهای مشخص» را برگزینید؛ هیچ‌کدام یعنی همهٔ کالاها.",
        "Under “On which items” pick categories or “Specific items”; picking none means all items.",
      ),
      GuideText(
        "در «چه وقت» تاریخ یا ساعت را بگذارید (خالی یعنی همیشه)؛ با «کدِ تخفیف (اختیاری)» تخفیف فقط با همان کد داده می‌شود.",
        "Under “When” set dates or hours (empty means always); with a “Discount code (optional)” the discount is given only with that code.",
      ),
      GuideText(
        "«ذخیره» را بزنید؛ قاعده خودش در سبدِ صندوق حساب می‌شود و با «غیرفعال» متوقف می‌شود.",
        "Click “Save”; the rule is applied in the till's cart by itself, and “Deactivate” stops it.",
      ),
    ),
    ready(
      "team", GuideText("کارمندان دکان", "Shop staff", "د دوکان کارکوونکي"),
      GuideText(
        "«کد دکان شما» را با دکمهٔ «کپی کد» بردارید و به شاگرد بدهید؛ او در برنامهٔ خودش «شاگرد دکان هستید؟» را می‌زند و همین کد را می‌نویسد.",
        "Copy “Your shop code” with “Copy code” and give it to the staff member; in their app they choose “Are you shop staff?” and type it.",
      ),
      GuideText(
        "برای کدِ محدود «کدِ پیوستنِ تازه» را بزنید، «نقش» و شمارِ روز و بار را بگذارید و «بساز» را بزنید.",
        "For a limited code click “New join code”, set the “Role” and the number of days and uses, then click “Create”.",
      ),
      GuideText(
        "روی نامِ هر کارمند بزنید تا نقش، «مجوزِ کارمندان» و دستگاه‌هایش را ببینید و عوض کنید.",
        "Click a staff member's name to see and change their role, “Staff permissions” and devices.",
      ),
      GuideText(
        "کارمندی که دیگر نمی‌آید: در صفحهٔ او «برداشتن» یا «خروج از همهٔ دستگاه‌ها» را بزنید.",
        "Someone who no longer works here: on their page click “Remove” or “Sign out of all devices”.",
      ),
      GuideText(
        "اگر کدِ دکان لو رفت «کد تازه» را بزنید؛ شاگردهای فعلی می‌مانند و فقط کدِ پیشین دیگر کسی را وارد نمی‌کند.",
        "If the shop code leaked, click “New code”; current staff stay and only the old code stops letting anyone in.",
      ),
    ),
    ready(
      "devices", GuideText("دستگاه‌های جانبی", "Devices", "جانبي وسایل"),
      GuideText(
        "بارکدخوان را به کامپیوتر وصل کنید (بلوتوثی را در تنظیماتِ کامپیوتر جفت کنید)؛ در «بارکدخوان‌ها» کادرِ آزمون را بزنید و یک کالا اسکن کنید.",
        "Connect the barcode scanner to the computer (pair a Bluetooth one in the computer's settings); in “Barcode scanners” click the test box and scan an item.",
      ),
      GuideText(
        "چاپگر: در «چاپگرها» «افزودنِ چاپگر» را بزنید، راهِ وصل و «عرض کاغذ» را انتخاب کنید و «ثبتِ چاپگر» را بزنید.",
        "Printer: in “Printers” click “Add a printer”, choose how it connects and the “Paper width”, then click “Add printer”.",
      ),
      GuideText(
        "«آزمون» ِ کنارِ هر چاپگر یک برگهٔ آزمایشی چاپ می‌کند.",
        "“Test” next to each printer prints a test page.",
      ),
      GuideText(
        "گوشی به‌جای بارکدخوان: در «گوشی ⇄ کامپیوتر» روی کامپیوتر «ساختنِ کدِ جفت‌شدن» را بزنید و همان کد را در گوشی بزنید.",
        "A phone as the scanner: in “Phone ⇄ computer” click “Create a pairing code” on the computer and enter the same code on the phone.",
      ),
      GuideText(
        "«همهٔ دستگاه‌ها» نشان می‌دهد چه وصل است: سبز یعنی همین حالا وصل است و سرخ یعنی پاسخ نداد.",
        "“All devices” shows what is connected: green means connected now and red means it did not answer.",
      ),
    ),
    ready(
      "settings", GuideText("تنظیمات", "Settings", "امستنې"),
      GuideText(
        "در «جست‌وجو در تنظیمات» بنویسید چه می‌خواهید، مثلاً چاپگر یا زبان؛ نتیجه همان دم می‌آید.",
        "In “Search settings” type what you need, such as printer or language; results appear at once.",
      ),
      GuideText(
        "یا یکی از شش دسته را بزنید، مثلاً «فروش و پرداخت»، «کالا و انبار» یا «ظاهر و زبان».",
        "Or click one of the six groups, such as “Sales & payment”, “Items & stock” or “Look & language”.",
      ),
      GuideText(
        "هر بخش کارتِ «این چه می‌کند؟» با یک مثال دارد؛ پیش از عوض کردن آن را بخوانید.",
        "Each section has a “What does this do?” card with an example; read it before you change anything.",
      ),
      GuideText(
        "بخش‌های اختیاری مثلِ کارتِ هدیه یا پیش‌فاکتور را با پیوندِ «قابلیت‌ها» ِ تهِ صفحه روشن یا خاموش کنید؛ خاموش کردن داده را پاک نمی‌کند.",
        "Turn optional parts such as gift cards or quotes on or off with the “Features” link at the bottom; turning one off deletes no data.",
      ),
      GuideText(
        "زبانِ برنامه (فارسی، دری، English، پښتو) در «ظاهر و زبان» است و فقط همین دستگاه را عوض می‌کند.",
        "The app language (Farsi, Dari, English, Pashto) is under “Look & language” and changes this device only.",
      ),
    ),
  )

  /** قاعده‌های ردیفِ READY؛ خالی ⇒ درست. جای خالی همیشه درست است (نشان داده نمی‌شود). */
  fun problems(g: ScreenGuide): List<String> {
    val out = ArrayList<String>()
    if (!SCREEN_ID.matches(g.screen)) out += "شناسهٔ صفحه: ${g.screen}"
    if (g.title.fa.isBlank()) out += "عنوانِ فارسی خالی است"
    if (g.status == GuideStatus.PLACEHOLDER) return out
    if (g.steps.isEmpty() || g.steps.size > MAX_STEPS) out += "گام‌ها باید ۱ تا $MAX_STEPS باشند (${g.steps.size})"
    g.steps.forEachIndexed { i, s ->
      if (s.fa.isBlank()) out += "گامِ ${i + 1}: فارسی خالی است"
      for (t in listOf(s.fa, s.en, s.ps)) if (t.length > MAX_STEP) out += "گامِ ${i + 1}: بیش از $MAX_STEP نویسه"
    }
    g.video?.let { v ->
      if (!VIDEO_PATH.matches(v.path)) out += "نشانیِ ویدیو: ${v.path}"
      if (v.seconds !in MIN_VIDEO_SECONDS..MAX_VIDEO_SECONDS) out += "طولِ ویدیو ${v.seconds} ثانیه"
      if (v.bytes !in 1..MAX_VIDEO_BYTES) out += "حجمِ ویدیو ${v.bytes} بایت"
    }
    return out
  }

  /** راهنمای آمادهٔ همین صفحه؛ جای خالی، ناقص یا ناشناخته ⇒ null (هیچ چیز نشان داده نمی‌شود) */
  fun forScreen(screen: String?, all: List<ScreenGuide> = ALL): ScreenGuide? =
    all.firstOrNull { it.screen == screen && it.status == GuideStatus.READY && problems(it).isEmpty() }

  /** صفحه‌هایی که هنوز متن یا ویدیو ندارند — همان فهرستِ کارِ صاحب محصول */
  fun pending(): List<ScreenGuide> = ALL.filter { it.status == GuideStatus.PLACEHOLDER || it.video == null }
}
