package ir.vil3ntec.tohid.core.i18n

/**
 *  صندوقِ سندهای ردشده (`core/sync/ConflictInbox`، `/api/shop/conflict-inbox`) — صفحه و نوارِ پوستهٔ صاحب/مدیر، و نشانِ
 *  «سرور نپذیرفت» روی فاکتورِ دستگاهِ سازنده. ساخته‌شده از جدولِ سه‌زبانه؛ قاعده‌ها سرِ `I18n.kt`.
 *  ⛔ نامِ کارمند و دستگاه، شمارهٔ فاکتور، مبلغ، دلیلِ نوشته‌شده و متنِ دلیلِ ترجمه‌شده از `asIs` می‌گذرند (لایهٔ دری دستشان نمی‌زند).
 */
@Suppress("unused")
object StrConflictInbox {
  val title: String get() = t("سندهای ردشده", "Rejected documents", "رد شوي اسناد")
  val intro: String get() = t(
    "سندهایی که سرور از کارمندان نپذیرفت. با تأییدِ خودتان دوباره ثبت کنید، در صندوق اصلاح کنید، یا با دلیل کنار بگذارید.",
    "Documents the server did not accept from staff. Re-submit with your approval, fix them at the till, or set them aside with a reason.",
    "هغه اسناد چې سرور له کارکوونکو ونه منل. په خپل تایید یې بیا ثبت کړئ، په صندوق کې یې سم کړئ، یا یې له لامل سره یو طرف ته کړئ.",
  )
  val empty: String get() = t("سندِ ردشده‌ای نیست.", "No rejected documents.", "هېڅ رد شوی سند نشته.")
  val emptyClosed: String get() = t("هنوز چیزی بسته نشده.", "Nothing has been closed yet.", "تر اوسه هېڅ شی نه دی تړل شوی.")
  fun pending(n: Any): String = t(
    "${asIs(n)} سندِ ردشده منتظرِ تصمیمِ شماست",
    "$n rejected ${pl(n, "document is", "documents are")} waiting for you",
    "$n رد ${pl(n, "شوی سند", "شوي اسناد")} ستاسو د پرېکړې په انتظار دي",
  )
  val open: String get() = t("دیدن", "Open", "کتل")
  val tabOpen: String get() = t("باز", "Open", "پرانیستي")
  val tabClosed: String get() = t("بسته‌ها", "Closed", "تړل شوي")
  val loadFailed: String get() = t("فهرست نیامد؛ دوباره بکشید.", "The list did not load; pull to retry.", "لړلیک رانغی؛ بیا یې راکاږئ.")

  //  یک ردیف
  fun saleDoc(no: String): String = t("فاکتورِ ${asIs(no)}", "Invoice $no", "بل $no")
  fun returnDoc(no: String): String = t("برگشتِ ${asIs(no)}", "Return $no", "بېرته ورکول $no")
  fun staff(name: String): String = t("کارمند: ${asIs(name)}", "Staff: $name", "کارکوونکی: $name")
  fun device(name: String): String = t("دستگاه: ${asIs(name)}", "Device: $name", "وسیله: $name")
  fun items(n: Any, names: String): String = t(
    "${asIs(n)} قلم: ${asIs(names)}",
    "$n ${pl(n, "item", "items")}: $names",
    "$n ${pl(n, "توکی", "توکي")}: $names",
  )
  fun why(reason: String): String = t("دلیل: ${asIs(reason)}", "Reason: $reason", "لامل: $reason")
  fun lastTry(reason: String): String = t("تلاشِ پیشین: ${asIs(reason)}", "Last attempt: $reason", "تېره هڅه: $reason")
  val busy: String get() = t("همین حالا دارد دوباره ثبت می‌شود…", "Being re-submitted right now…", "همدا اوس بیا ثبتېږي…")
  fun closedBy(status: String, who: String): String = t("${asIs(status)} — ${asIs(who)}", "$status — $who", "$status — $who")
  val statusResubmitted: String get() = t("دوباره ثبت شد", "Re-submitted", "بیا ثبت شو")
  val statusFixed: String get() = t("اصلاح و ثبت شد", "Fixed and recorded", "سم او ثبت شو")
  val statusDismissed: String get() = t("کنار گذاشته شد", "Set aside", "یو طرف ته شو")

  //  جزئیات
  val invoice: String get() = t("فاکتور", "Invoice", "بل")
  val lines: String get() = t("قلم‌ها", "Items", "توکي")
  val total: String get() = t("جمع", "Total", "ټول")
  val discount: String get() = t("تخفیف", "Discount", "تخفیف")
  val payable: String get() = t("قابلِ پرداخت", "Payable", "د ورکړې وړ")
  val paid: String get() = t("پرداخت‌شده", "Paid", "ورکړل شوی")
  val debt: String get() = t("قرض", "On credit", "پور")
  fun debtor(name: String): String = t("قرض‌دار: ${asIs(name)}", "Debtor: $name", "پوروړی: $name")
  val partial: String get() = t(
    "بستهٔ این سند کامل نرسیده؛ فقط «اصلاح و ثبت» یا «کنار بگذار».",
    "This document did not arrive complete; only “Fix and record” or “Set aside”.",
    "د دې سند بسته بشپړه نه ده رسېدلې؛ یوازې «سمول او ثبت» یا «یو طرف ته کول».",
  )

  //  کارها
  val resubmit: String get() = t("دوباره ثبت با تأییدِ من", "Re-submit with my approval", "زما په تایید بیا ثبت")
  val fix: String get() = t("اصلاح و ثبت", "Fix and record", "سمول او ثبت")
  val dismiss: String get() = t("کنار بگذار", "Set aside", "یو طرف ته کول")
  fun confirmResubmit(doc: String): String = t(
    "«${asIs(doc)}» با تأییدِ شما دوباره به سرور می‌رود و با اجازه و سقفِ خودِ شما سنجیده می‌شود. فقط یک بار ثبت می‌شود، هر چند بار هم بزنید.",
    "“$doc” goes to the server again with your approval and is checked against your own permissions and limits. It is recorded only once, however many times you tap.",
    "«$doc» ستاسو په تایید بیا سرور ته ځي او ستاسو د خپل اجازې او حد سره سنجول کېږي. یوازې یو ځل ثبتېږي، هر څو ځله چې یې ووهئ.",
  )
  val notePlaceholder: String get() = t("یادداشت (اختیاری)", "Note (optional)", "یادښت (اختیاري)")
  val dismissTitle: String get() = t("کنار گذاشتنِ سند", "Set the document aside", "د سند یو طرف ته کول")
  val dismissBody: String get() = t(
    "چیزی ثبت نمی‌شود. دلیل برای کارمند و در سابقه می‌ماند.",
    "Nothing is recorded. The reason stays in the history and is shown to the staff member.",
    "هېڅ شی نه ثبتېږي. لامل په تاریخچه کې پاتې کېږي او کارکوونکي ته ښودل کېږي.",
  )
  val reasonLabel: String get() = t("دلیل", "Reason", "لامل")
  val reasonRequired: String get() = t("دلیل را بنویسید.", "Write a reason.", "لامل ولیکئ.")
  val fixReason: String get() = t("اصلاح و دوباره ثبت شد", "Fixed and recorded again", "سم او بیا ثبت شو")

  //  نتیجه‌ها
  val doneResubmitted: String get() = t("دوباره ثبت شد؛ همهٔ دستگاه‌ها همین را می‌گیرند.", "Re-submitted; every device receives it.", "بیا ثبت شو؛ ټولې وسیلې همدا ترلاسه کوي.")
  val doneAlready: String get() = t("پیش‌تر ثبت شده بود؛ چیزی دوباره ثبت نشد.", "It was already recorded; nothing was recorded twice.", "مخکې ثبت شوی و؛ هېڅ شی دوه ځله ثبت نه شو.")
  val doneDismissed: String get() = t("کنار گذاشته شد.", "Set aside.", "یو طرف ته شو.")
  val fixOpened: String get() = t(
    "فاکتور در صندوق باز شد. پس از ثبت، این مورد خودش بسته می‌شود.",
    "The invoice is open at the till. Once recorded, this item closes by itself.",
    "بل په صندوق کې پرانیستل شو. د ثبت وروسته، دا مورد پخپله تړل کېږي.",
  )
  val fixClosed: String get() = t("فاکتورِ اصلاح‌شده ثبت شد و سندِ ردشده بسته شد.", "The fixed invoice was recorded and the rejected document closed.", "سم شوی بل ثبت شو او رد شوی سند وتړل شو.")
  val cartBusy: String get() = t(
    "صندوق فاکتورِ باز دارد؛ اول آن را ثبت یا نگه دارید.",
    "The till has an open invoice; record or hold it first.",
    "صندوق پرانیستی بل لري؛ لومړی یې ثبت یا وساتئ.",
  )
  val fixNoLines: String get() = t("قلمی برای صندوق پیدا نشد.", "No items were found for the till.", "د صندوق لپاره هېڅ توکی پیدا نه شو.")
  fun rejectedAgain(reason: String): String = t("سرور دوباره هم نپذیرفت: ${asIs(reason)}", "The server rejected it again: $reason", "سرور بیا هم ونه منله: $reason")
  val inProgress: String get() = t("همین سند همین حالا دارد ثبت می‌شود؛ کمی بعد دوباره ببینید.", "This document is being recorded right now; check again shortly.", "دا سند همدا اوس ثبتېږي؛ لږ وروسته بیا وګورئ.")

  //  نشانِ فاکتورِ دستگاهِ سازنده
  fun serverRejected(reason: String): String = t("سرور نپذیرفت: ${asIs(reason)}", "The server did not accept it: $reason", "سرور ونه منله: $reason")
  fun serverRejectedReturn(reason: String): String = t(
    "برگشتِ این فاکتور را سرور نپذیرفت: ${asIs(reason)}",
    "The server did not accept the return on this invoice: $reason",
    "د دې بل بېرته ورکول سرور ونه منل: $reason",
  )
  val markResubmitted: String get() = t("صاحب یا مدیر با تأییدِ خودش دوباره ثبت کرد.", "The owner or a manager re-submitted it with their approval.", "خاوند یا مدیر په خپل تایید بیا ثبت کړ.")
  val markFixed: String get() = t("صاحب یا مدیر اصلاح کرد و دوباره ثبت شد.", "The owner or a manager fixed it and it was recorded again.", "خاوند یا مدیر سم کړ او بیا ثبت شو.")
  fun markDismissed(note: String): String = t("صاحب یا مدیر کنار گذاشت: ${asIs(note)}", "The owner or a manager set it aside: $note", "خاوند یا مدیر یو طرف ته کړ: $note")

  //  سندی که صاحب «کنار گذاشت»/«اصلاح کرد» — روی دستگاهِ سازنده، بیرون از دفترِ شمرده (`data/SetAside`)
  val setAsideTitle: String get() = t("سندهایی که سرور نپذیرفت", "Documents the server did not accept", "هغه اسناد چې سرور ونه منل")
  val setAsideIntro: String get() = t(
    "در موجودی، قرض، دخل و گزارش شمرده نمی‌شوند؛ برای سابقه کامل نگه داشته شده‌اند.",
    "Not counted in stock, debts, the till or reports; kept in full for the record.",
    "په موجودي، پور، صندوق او راپور کې نه شمېرل کېږي؛ د سابقې لپاره بشپړ ساتل شوي دي.",
  )
  fun setAsideDismissed(note: String): String = t(
    "سرور نپذیرفت — کنار گذاشته شد: ${asIs(note)}",
    "The server did not accept it — set aside: $note",
    "سرور ونه منله — یو طرف ته شو: $note",
  )
  fun setAsideFixed(note: String): String = t(
    "سرور نپذیرفت — اصلاح شد: ${asIs(note)}",
    "The server did not accept it — fixed: $note",
    "سرور ونه منله — سم شو: $note",
  )
  fun setAsideKept(n: Any): String = t(
    "${asIs(n)} مقدار با سرور یکی نشد (از آن پس روی همین دستگاه عوض شده بود)",
    "$n ${pl(n, "value", "values")} could not be matched to the server (changed on this device since)",
    "$n ${pl(n, "ارزښت", "ارزښتونه")} له سرور سره برابر نه شول (له هغه وروسته په همدې وسیله بدل شوي وو)",
  )

  /** دلیلِ رد به زبانِ کاربر — همان کدهای سرور (`staff-approval.DENY_CODE`، `return-line-cap`، `approval-core`) */
  fun reason(code: String): String = when (code) {
    "approval_required" -> t("نیازمندِ تأییدِ صاحب یا مدیر", "Needs the owner’s or a manager’s approval", "د خاوند یا مدیر تایید ته اړتیا لري")
    "approval_needs_v3" -> t("تأیید از این راه پذیرفته نمی‌شود", "Approval is not accepted this way", "تایید له دې لارې نه منل کېږي")
    "approval_invalid", "approval_mismatch", "approval_bad_signature", "approval_key_unknown", "approval_replayed",
    "approval_self", "approver_not_allowed", "approval_expired", "approval_doc_mismatch", "approval_perm_mismatch",
    "approval_amount_exceeded", "approval_pct_exceeded" -> t("برگهٔ تأیید پذیرفته نشد", "The approval was not accepted", "تایید ونه منل شو")
    "credit_not_allowed" -> t("فروشِ قرضی در حدِ نقشِ کارمند نیست", "Credit sales are beyond the staff role", "پور پلور د کارکوونکي له رول څخه بهر دی")
    "discount_not_allowed" -> t("تخفیف در حدِ نقشِ کارمند نیست", "The discount is beyond the staff role", "تخفیف د کارکوونکي له رول څخه بهر دی")
    "cancel_not_allowed" -> t("لغوِ فاکتور در حدِ نقشِ کارمند نیست", "Cancelling is beyond the staff role", "د بل لغوه د کارکوونکي له رول څخه بهر ده")
    "return_not_allowed" -> t("برگشت در حدِ نقشِ کارمند نیست", "Returns are beyond the staff role", "بېرته ورکول د کارکوونکي له رول څخه بهر دي")
    "price_not_allowed" -> t("تغییرِ قیمت در حدِ نقشِ کارمند نیست", "The price change is beyond the staff role", "د بیې بدلون د کارکوونکي له رول څخه بهر دی")
    "free_line_not_allowed" -> t("قلمِ آزاد در حدِ نقشِ کارمند نیست", "Custom items are beyond the staff role", "آزاد توکی د کارکوونکي له رول څخه بهر دی") // POS-10، ۱-۱۱
    "pay_fix_not_allowed" -> t("اصلاحِ پرداخت در حدِ نقشِ کارمند نیست", "Fixing payments is beyond the staff role", "د ورکړې سمون د کارکوونکي له رول څخه بهر دی")
    "return_exceeds_sold" -> t("برگشت بیش از مقدارِ فروخته‌شدهٔ همان قلم است", "The return is more than was sold of that item", "بېرته ورکول د همغه توکي له پلورل شوي مقدار څخه ډېر دي")
    "return_exceeds_sale" -> t("برگشت بیش از مبلغِ فاکتور است", "The return is more than the invoice amount", "بېرته ورکول د بل له مبلغ څخه ډېر دي")
    "table_not_allowed", "perm_denied", "permission_denied", "delete_not_allowed", "safe_not_allowed" ->
      t("در حدِ اجازهٔ کارمند نیست", "Beyond the staff member’s permissions", "د کارکوونکي له اجازې څخه بهر دی")
    "tx_rejected" -> t("سندِ همراهش رد شد", "Its document was rejected", "د هغه سند رد شو")
    //  کدِ ناشناخته (سرورِ تازه‌تر) ⇒ خودِ کد کنارِ جمله، برای پشتیبانی
    else -> t("سرور این سند را نپذیرفت (${asIs(code)})", "The server did not accept this document ($code)", "سرور دا سند ونه منه ($code)")
  }
}
