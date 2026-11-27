package ir.vil3ntec.tohid.core.i18n

object StrPurchaseWorkflow {
  val loose get() = t("تکی", "Individual units", "جلا دانې")
  val carton get() = t("کارتن", "Carton", "کارتن")
  val cartons get() = t("چند کارتن؟", "Carton count", "څو کارتنونه؟")
  val pieces get() = t("چند عدد در هر کارتن؟", "Units per carton", "په هر کارتن کې څو دانې؟")
  val cartonPrice get() = t("قیمت یک کارتن", "Price per carton", "د یوه کارتن بیه")
  val cartonInvalid get() = t("تعداد کارتن و تعداد هر کارتن باید عدد صحیح و مثبت باشد.", "Carton count and units per carton must be positive whole numbers.", "د کارتن او د دانو شمېر باید مثبت بشپړ عدد وي.")
  val dateOrder get() = t("تاریخ تولید بعد از انقضا است.", "Production date is after expiry.", "د تولید نېټه له ختمېدو وروسته ده.")
  val optionalBarcode get() = t("بارکد (اختیاری)", "Barcode (optional)", "بارکوډ (اختیاري)")
  val paymentNow get() = t("پرداخت کردم", "Paid now", "اوس مې ورکړل")
  val paymentLater get() = t("بعداً می‌پردازم", "Pay later", "وروسته به یې ورکړم")
  val paidAmount get() = t("مبلغی که به تأمین‌کننده دادید", "Amount paid to supplier", "عرضه کوونکي ته ورکړل شوی مبلغ")
  val productDates get() = t("تاریخ تولید، انقضا و بارکد", "Production date, expiry and barcode", "د تولید نېټه، ختمېدل او بارکوډ")
  val statement get() = t("گردش حساب", "Account activity", "د حساب راکړه ورکړه")
  val allCategories get() = t("همهٔ دسته‌ها", "All categories", "ټولې ډلې")
  fun basePrice(total: String, each: String) = t("${asIs(total)} عدد · هر عدد ${asIs(each)}", "$total units · $each each", "$total دانې · هره دانه $each")
  val deliveryOptions get() = t("زمان دریافت کالا و هزینهٔ جانبی", "Delivery timing and extra costs", "د توکو د ترلاسه کولو وخت او اضافي لګښتونه")
}
