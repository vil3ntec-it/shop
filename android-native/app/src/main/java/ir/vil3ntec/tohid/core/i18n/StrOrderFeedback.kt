package ir.vil3ntec.tohid.core.i18n

/**
 *  NTF-07/09 — «گزارش‌ها و نظرها» در صفحهٔ سفارش‌ها (`ui/screens/OrderFeedbackPanel.kt`، منطق در `core/orders/OrderFeedback`).
 *  قاعده‌ها سرِ `I18n.kt`؛ ترجمه‌های نامطمئن در `docs/i18n-glossary.md`.
 */
@Suppress("unused")
object StrOrderFeedback {

  val title: String get() = t("گزارش‌ها و نظرها", "Reports and reviews", "راپورونه او نظرونه")
  fun entry(waiting: Int): String = t("گزارش‌ها و نظرها ($waiting)", "Reports and reviews ($waiting)", "راپورونه او نظرونه ($waiting)")
  fun tabIssues(n: Int): String = t("گزارشِ مشکل ($n)", "Problem reports ($n)", "د ستونزې راپورونه ($n)")
  fun tabReviews(n: Int): String = t("نظرها ($n)", "Reviews ($n)", "نظرونه ($n)")

  // فیلترها
  val fOpen: String get() = t("باز", "Open", "پرانیستي")
  val fResolved: String get() = t("رسیدگی‌شده", "Resolved", "حل شوي")
  val fAll: String get() = t("همه", "All", "ټول")
  val fPending: String get() = t("در انتظار", "Pending", "په تمه")
  val fApproved: String get() = t("منتشرشده", "Published", "خپاره شوي")
  val fRejected: String get() = t("ردشده", "Rejected", "رد شوي")

  // نوعِ مشکل — همان `KINDS` ِ سرور
  val kMissing: String get() = t("کالایی کم بود", "An item was missing", "یو توکی کم و")
  val kWrong: String get() = t("کالای اشتباه", "Wrong item", "ناسم توکی")
  val kDamaged: String get() = t("خراب یا آسیب‌دیده", "Broken or damaged", "مات یا زیانمن")
  val kQuality: String get() = t("کیفیتِ بد", "Poor quality", "خراب کیفیت")
  val kLate: String get() = t("دیر رسید", "Arrived late", "ناوخته ورسید")
  val kOther: String get() = t("دیگر", "Other", "بل")

  // گزارش‌ها
  val issuesHint: String get() = t(
    "گزارش‌هایی که مشتری از صفحهٔ پیگیریِ سفارش فرستاده؛ پاسخِ شما همان‌جا به مشتری نشان داده می‌شود.",
    "Reports customers sent from the order tracking page; your reply is shown to them there.",
    "هغه راپورونه چې پېرودونکي د فرمایش د تعقیب له پاڼې لېږلي؛ ستاسو ځواب هلته ورته ښودل کېږي.",
  )
  fun issueHead(orderNo: Long, kind: String): String = t("سفارش #$orderNo — $kind", "Order #$orderNo — $kind", "فرمایش #$orderNo — $kind")
  val stOpen: String get() = t("در انتظارِ رسیدگی", "Awaiting reply", "د ځواب په تمه")
  val stResolved: String get() = t("رسیدگی شد", "Resolved", "حل شو")
  fun replied(reply: String): String = t("پاسخِ دکان: $reply", "Shop's reply: $reply", "د دوکان ځواب: $reply")
  val replyLabel: String get() = t("پاسخ به مشتری", "Reply to the customer", "پېرودونکي ته ځواب")
  val replyHint: String get() = t(
    "۲ تا ۱۰۰۰ نویسه؛ مشتری در صفحهٔ پیگیری می‌بیند.",
    "2 to 1000 characters; the customer sees it on the tracking page.",
    "له ۲ تر ۱۰۰۰ تورو؛ پېرودونکی یې د تعقیب په پاڼه کې ویني.",
  )
  val resolve: String get() = t("رسیدگی شد — فرستادنِ پاسخ", "Mark resolved and send reply", "حل شو — ځواب ولېږئ")
  val resolvedOk: String get() = t("پاسخ فرستاده شد و گزارش بسته شد", "Reply sent and report closed", "ځواب ولېږل شو او راپور وتړل شو")
  fun photos(n: Int): String = t("$n عکس", "$n photos", "$n انځورونه")
  val photo: String get() = t("عکسِ گزارش", "Report photo", "د راپور انځور")
  val photoFailed: String get() = t("عکس نیامد — دوباره بزنید", "Photo did not load — tap again", "انځور رانغی — بیا یې کېکاږئ")
  val issuesEmpty: String get() = t("در این برگه گزارشی نیست.", "No reports in this tab.", "په دې برخه کې راپور نشته.")

  // نظرها
  val reviewsHint: String get() = t(
    "هیچ نظری پیش از تأییدِ شما در ویترین دیده نمی‌شود؛ ویترین فقط ستاره و نوشته را نشان می‌دهد، بی نام و شماره.",
    "No review appears on the storefront before you approve it; the storefront shows only stars and text, no name or phone.",
    "هېڅ نظر ستاسو له منلو مخکې په هټۍ کې نه ښکاري؛ هټۍ یوازې ستوري او لیک ښيي، بې نوم او شمېرې.",
  )
  fun reviewHead(orderNo: Long): String = t("سفارش #$orderNo", "Order #$orderNo", "فرمایش #$orderNo")
  fun stars(n: Int): String = t("$n از ۵ ستاره", "$n of 5 stars", "له ۵ څخه $n ستوري")
  val noComment: String get() = t("بی نوشته — فقط ستاره", "No text — stars only", "بې لیکه — یوازې ستوري")
  val stPending: String get() = t("در انتظارِ بازبینی", "Awaiting review", "د کتنې په تمه")
  val stApproved: String get() = t("منتشر شده", "Published", "خپور شوی")
  val stRejected: String get() = t("رد شده", "Rejected", "رد شوی")
  val approve: String get() = t("تأیید و انتشار", "Approve and publish", "منل او خپرول")
  val reject: String get() = t("رد", "Reject", "رد")
  val approvedOk: String get() = t("نظر در ویترین منتشر شد", "Review published on the storefront", "نظر په هټۍ کې خپور شو")
  val rejectedOk: String get() = t("نظر منتشر نمی‌شود", "Review will not be published", "نظر نه خپرېږي")
  val reviewsEmpty: String get() = t("در این برگه نظری نیست.", "No reviews in this tab.", "په دې برخه کې نظر نشته.")

  val readOnly: String get() = t(
    "پاسخ و بازبینی فقط با اجازهٔ مدیریتِ سفارش (صاحب یا مدیر).",
    "Replying and reviewing need the manage-orders permission (owner or manager).",
    "ځواب او کتنه یوازې د فرمایش د مدیریت په اجازه (خاوند یا مدیر).",
  )
  val listFailed: String get() = t("فهرست نیامد", "The list did not load", "لړلیک رانغی")
  val retry: String get() = t("دوباره", "Retry", "بیا")
}
