package ir.vil3ntec.tohid.core.i18n

/**
 *  PUR-14 روی گوشیِ نسخهٔ ۳ — «خرید ⇐ فاکتورهای تأمین‌کننده» (`ui/v3/money/SupplierBillsPages.kt`): سربرگ، حالت‌های
 *  صفحه و فهرستِ سفارش‌ها. متنِ خودِ فرم و «باطل» همان پنجرهٔ مشترکِ کامپیوتر است (`ui/screens/PurchaseBills.kt`).
 */
@Suppress("unused")
object StrSupplierBills {
  val title: String get() = t("فاکتورهای تأمین‌کننده", "Supplier bills", "د عرضه‌کوونکي بلونه")
  val order: String get() = t("سفارشِ خرید", "Purchase order", "د پېرود امر")
  val newBill: String get() = t("ثبتِ فاکتورِ تأمین‌کننده", "Record a supplier bill", "د عرضه‌کوونکي بل ثبتول")
  val note: String get() = t(
    "فاکتورِ کاغذیِ خودِ تأمین‌کننده را این‌جا ثبت کنید؛ برنامه آن را با سفارش و رسیدها تطبیق می‌دهد. فقط مدیر و صاحب.",
    "Record the supplier's own paper bill here; the app matches it with the order and receipts. Managers and the owner only.",
    "د عرضه‌کوونکي خپل کاغذي بل دلته ثبت کړئ؛ اپ یې له امر او رسیدونو سره پرتله کوي. یوازې مدیر او خاوند.",
  )
  val loading: String get() = t("از سرور می‌آید…", "Loading from the server…", "له سرور څخه راځي…")
  val failed: String get() = t("از سرور نیامد. اینترنت را ببینید و دوباره بزنید.", "Could not load from the server. Check the internet and try again.", "له سرور څخه رانغی. انټرنېټ وګورئ او بیا هڅه وکړئ.")
  val retry: String get() = t("دوباره", "Retry", "بیا")
  val off: String get() = t("سفارشِ خرید برای این دکان روشن نیست.", "Purchase orders are not turned on for this shop.", "د پېرود امرونه د دې دوکان لپاره فعال نه دي.")
  val managerOnly: String get() = t("فاکتورِ تأمین‌کننده فقط کارِ مدیر و صاحبِ دکان است.", "Supplier bills are for managers and the shop owner only.", "د عرضه‌کوونکي بل یوازې د مدیر او د دوکان د خاوند کار دی.")
  val notSent: String get() = t(
    "این سفارش هنوز فرستاده نشده؛ فاکتورِ تأمین‌کننده پس از فرستادن ثبت می‌شود.",
    "This order has not been sent yet; a supplier bill is recorded after sending.",
    "دا امر لا نه دی لېږل شوی؛ د عرضه‌کوونکي بل له لېږلو وروسته ثبتېږي.",
  )
  val none: String get() = t("هنوز سفارشِ فرستاده‌ای نیست.", "No sent purchase orders yet.", "تر اوسه لېږل شوی امر نشته.")
  val more: String get() = t("بیشتر", "More", "نور")
  fun orderTitle(no: Any, supplier: Any): String = t("#${asIs(no)} · ${asIs(supplier)}", "#$no · $supplier", "#$no · $supplier")
  fun bills(n: Int): String = if (n == 0) t("بی فاکتور", "No bill", "بې بله") else t("${asIs(n)} فاکتور", "$n ${pl(n, "bill", "bills")}", "$n بلونه")
  fun line(status: String, bills: String): String = t("${asIs(status)} · ${asIs(bills)}", "$status · $bills", "$status · $bills")
  fun totals(ordered: String, accepted: String): String =
    t("سفارش ${asIs(ordered)} ؋ · رسیده ${asIs(accepted)} ؋", "Ordered $ordered AFN · received $accepted AFN", "امر $ordered ؋ · رسیدلي $accepted ؋")
  val voided: String get() = t("فاکتور باطل شد", "Bill voided", "بل باطل شو")
  val failedShort: String get() = t("نشد", "Failed", "ونه شو")
}
