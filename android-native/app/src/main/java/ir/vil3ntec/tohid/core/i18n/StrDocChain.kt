package ir.vil3ntec.tohid.core.i18n

/**
 *  SEC-02 — صفحهٔ «بررسیِ یکپارچگیِ اسناد» (`DocChainCheckScreen`) و درِ آن در «سابقهٔ عملیات». منطق در
 *  `core/audit/DocChainAudit`؛ پرچمِ خاموشِ `shop.doc_chain` ⇒ نه در هست، نه صفحه.
 */
@Suppress("unused")
object StrDocChain {
  val title: String get() = t("بررسیِ یکپارچگیِ اسناد", "Document integrity check", "د اسنادو د بشپړتیا کتنه")
  val entryBody: String get() = t(
    "فاکتور، برگشت، قرض و خرید با زنجیرهٔ هشِ همین دستگاه سنجیده می‌شوند",
    "Invoices, returns, debts and purchases are checked against this device's hash chain",
    "بلونه، بېرته راګرځېدنې، پورونه او پېرودونه د همدې وسیلې د هش له ځنځیر سره کتل کېږي",
  )
  val checking: String get() = t("در حالِ سنجش…", "Checking…", "کتل کېږي…")
  val off: String get() = t(
    "زنجیرهٔ اسناد برای این دکان روشن نیست یا روی این دستگاه هنوز ساخته نشده است",
    "The document chain is not on for this shop or has not started on this device yet",
    "د اسنادو ځنځیر د دې دوکان لپاره روښانه نه دی یا په دې وسیله لا نه دی جوړ شوی",
  )
  val okTitle: String get() = t("همه سالم است", "Everything is intact", "ټول روغ دي")
  fun okBody(links: Any, acked: Any): String = t(
    "${asIs(links)} سند در زنجیره؛ ${asIs(acked)} سند روی سرور هم تأیید شده است",
    "$links documents in the chain; $acked also confirmed by the server",
    "$links سندونه په ځنځیر کې؛ $acked یې په سرور هم تایید شوي",
  )
  val badTitle: String get() = t("ناسازگاری پیدا شد", "A mismatch was found", "ناسمون وموندل شو")
  fun brokenAt(seq: Any): String = t(
    "زنجیره از حلقهٔ ${asIs(seq)} شکسته است؛ فایلِ زنجیره روی همین دستگاه دست خورده",
    "The chain breaks at link $seq; the chain file on this device was altered",
    "ځنځیر له $seq کړۍ مات دی؛ په دې وسیله د ځنځیر فایل لاسوهل شوی",
  )
  val changedTitle: String get() = t("پس از ثبت عوض شده", "Changed after it was recorded", "له ثبت وروسته بدل شوی")
  val missingTitle: String get() = t("در دفتر نیست", "Not in the books", "په دفتر کې نشته")
  fun waiting(n: Any): String = t("${asIs(n)} سند در انتظارِ همگام‌سازیِ بعدی", "$n documents wait for the next sync", "$n سندونه د راتلونکي همغږي په تمه دي")
  fun unanchored(n: Any): String = t(
    "${asIs(n)} سند بی لنگر (بسیار دیر رسید یا ساعتِ دستگاه عقب بود)",
    "$n documents without an anchor (arrived very late or the device clock was behind)",
    "$n سندونه بې لنګره (ډېر ناوخته راغلل یا د وسیلې ساعت شاته و)",
  )
  val halted: String get() = t(
    "زنجیره متوقف شده است؛ در همگام‌سازیِ بعدی دورهٔ تازه آغاز می‌شود و دورهٔ پیشین شاهد می‌ماند",
    "The chain is stopped; the next sync starts a new period and the old one stays as evidence",
    "ځنځیر ودرېدلی؛ راتلونکی همغږي نوې دوره پیلوي او پخوانۍ دوره د شاهد په توګه پاتې کېږي",
  )
  fun since(time: Any): String = t("برگشت، قرض و خرید از ${asIs(time)}", "Returns, debts and purchases since $time", "بېرته راګرځېدنې، پورونه او پېرودونه له $time راهیسې")
  fun upTo(time: Any): String = t("زنجیره‌شده تا ${asIs(time)}", "Chained up to $time", "تر $time پورې ځنځیر شوي")
  fun lastInvoice(no: Any): String = t("آخرین فاکتورِ زنجیره‌شده #${asIs(no)}", "Last chained invoice #$no", "وروستی ځنځیر شوی بل #$no")
  fun count(label: Any, n: Any): String = t("${asIs(label)}: ${asIs(n)}", "$label: $n", "$label: $n")
  val recheck: String get() = t("دوباره بسنج", "Check again", "بیا یې وګوره")
  val typeSale: String get() = t("فاکتور", "Invoice", "بل")
  val typeVoid: String get() = t("لغوِ فاکتور", "Cancelled invoice", "لغوه شوی بل")
  val typeReturn: String get() = t("برگشت", "Return", "بېرته راګرځېدنه")
  val typeDebt: String get() = t("قرض و رسید", "Debt and receipt", "پور او رسید")
  val typePurchase: String get() = t("خرید", "Purchase", "پېرود")
  val typeSupplierPay: String get() = t("پرداخت به تأمین‌کننده", "Supplier payment", "عرضه کوونکي ته ورکړه")
}
