package ir.vil3ntec.tohid.core.i18n

/**
 *  «پرسیدن از مدیر» و «درخواست‌های تأیید» (STF-02، `core/perm/RemoteApproval`) — صندوقِ گوشیِ نسخهٔ ۳ و کامپیوتر،
 *  و صندوقِ تأییدِ صاحب/مدیر. ساخته‌شده از جدولِ سه‌زبانه؛ قاعده‌ها سرِ `I18n.kt`.
 *  ⛔ نامِ کارمند، شمارهٔ سند، مبلغ و دلیل از سرور می‌آیند و از `asIs` می‌گذرند (لایهٔ دری دستشان نمی‌زند).
 */
@Suppress("unused")
object StrRemoteApproval {
  val ask: String get() = t("پرسیدن از مدیر", "Ask a manager", "له مدیر وپوښتئ")
  val askHint: String get() = t(
    "صاحب یا مدیرِ دکان روی دستگاهِ خودش، گوشی یا کمپیوتر، تأیید می‌کند؛ دلیل را بالا بنویسید.",
    "The shop owner or a manager approves on their own phone or computer; write the reason above.",
    "د دوکان خاوند یا مدیر په خپل ټیلیفون یا کمپیوټر تایید کوي؛ لامل پورته ولیکئ.",
  )
  val orPin: String get() = t("یا مدیرِ کنارِ صندوق PIN بزند", "Or a manager at the till enters their PIN", "یا د صندوق تر څنګ مدیر PIN ووهي")
  val sending: String get() = t("فرستادنِ درخواست…", "Sending the request…", "غوښتنه لېږل کېږي…")
  fun waiting(left: String): String = t(
    "منتظرِ پاسخِ مدیر — ${asIs(left)} مانده",
    "Waiting for a manager — $left left",
    "د مدیر ځواب ته انتظار — $left پاتې",
  )
  val offline: String get() = t(
    "اینترنت وصل نیست؛ با وصل شدن دوباره می‌پرسیم. PIN ِ همین دستگاه هم کار می‌کند.",
    "No internet; we will ask again once connected. The PIN on this device still works.",
    "انټرنېټ نشته؛ د وصلېدو سره به بیا وپوښتو. د همدې وسیلې PIN هم کار کوي.",
  )
  fun approved(who: String): String = t("${asIs(who)} تأیید کرد", "Approved by $who", "$who تایید کړ")
  val approvedAnon: String get() = t("مدیر تأیید کرد", "A manager approved", "مدیر تایید کړ")
  val denied: String get() = t("مدیر رد کرد", "A manager declined", "مدیر رد کړ")
  fun deniedNote(note: String): String = t("مدیر رد کرد: ${asIs(note)}", "A manager declined: $note", "مدیر رد کړ: $note")
  val expired: String get() = t("زمانِ درخواست گذشت؛ دوباره بپرسید.", "The request expired; ask again.", "د غوښتنې وخت تېر شو؛ بیا وپوښتئ.")
  val cancelled: String get() = t("درخواست پس گرفته شد.", "The request was withdrawn.", "غوښتنه بېرته واخیستل شوه.")
  val failed: String get() = t("درخواست فرستاده نشد.", "The request could not be sent.", "غوښتنه ونه لېږل شوه.")
  val withdraw: String get() = t("پس گرفتن", "Withdraw", "بېرته اخیستل")
  val askAgain: String get() = t("دوباره بپرس", "Ask again", "بیا وپوښتئ")

  //  صندوقِ تأییدِ صاحب/مدیر
  val inboxTitle: String get() = t("درخواست‌های تأیید", "Approval requests", "د تایید غوښتنې")
  val inboxIntro: String get() = t(
    "کارمندان از صندوق می‌پرسند؛ فقط درخواست‌هایی دیده می‌شوند که خودتان اختیارِ همان مقدار را دارید.",
    "Staff ask from the till; you only see requests you are allowed to approve for that amount.",
    "کارکوونکي له صندوق څخه پوښتنه کوي؛ یوازې هغه غوښتنې ښکاري چې تاسو یې د همغه اندازې واک لرئ.",
  )
  val inboxEmpty: String get() = t("درخواستی در انتظار نیست.", "No requests are waiting.", "هېڅ غوښتنه په انتظار نه ده.")
  val inboxOff: String get() = t(
    "«پرسیدن از مدیر» برای این دکان روشن نیست.",
    "“Ask a manager” is not turned on for this shop.",
    "«له مدیر وپوښتئ» د دې دوکان لپاره نه دی فعال.",
  )
  fun pending(n: Any): String = t(
    "${asIs(n)} درخواستِ تأیید در انتظارِ شماست",
    "$n approval ${pl(n, "request is", "requests are")} waiting for you",
    "$n د تایید ${pl(n, "غوښتنه", "غوښتنې")} ستاسو په انتظار ده",
  )
  val open: String get() = t("دیدن", "Open", "کتل")
  val permDiscount: String get() = t("تخفیف", "Discount", "تخفیف")
  val permCancel: String get() = t("لغوِ فاکتور", "Invoice cancellation", "د بل لغوه")
  val permReturn: String get() = t("برگشت", "Return", "بېرته ورکول")
  //  STF-03 — پیش‌سنجیِ صندوق (`SaleCapGate`) قرض و تغییرِ قیمتِ ردیف را هم می‌پرسد
  val permCredit: String get() = t("فروشِ قرضی", "Credit sale", "پور پلور")
  val permPrice: String get() = t("تغییرِ قیمت", "Price change", "د بیې بدلون")
  val permFreeLine: String get() = t("قلمِ آزاد", "Custom item", "آزاد توکی") // POS-10، تصمیمِ ۱-۱۱ (۲)
  fun saleDoc(no: String): String = t("فاکتورِ ${asIs(no)}", "Invoice $no", "بل $no")
  fun returnDoc(no: String): String = t("سندِ برگشتِ ${asIs(no)}", "Return $no", "د بېرته ورکولو سند $no")
  fun who(name: String): String = t("از ${asIs(name)}", "From $name", "له $name")
  fun why(reason: String): String = t("دلیل: ${asIs(reason)}", "Reason: $reason", "لامل: $reason")
  fun left(time: String): String = t("${asIs(time)} مانده", "$time left", "$time پاتې")
  val approve: String get() = t("تأیید", "Approve", "تایید")
  val deny: String get() = t("رد", "Decline", "رد")
  fun confirmApprove(what: String): String = t(
    "«${asIs(what)}» را تأیید می‌کنید؟ تأیید فقط یک بار و فقط برای همین سند و تا همین مقدار به کار می‌رود.",
    "Approve “$what”? It can be used once, only for this document and up to this amount.",
    "«$what» تاییدوئ؟ تایید یوازې یو ځل او یوازې د همدې سند او تر همدې اندازې کارېږي.",
  )
  fun confirmDeny(what: String): String = t("«${asIs(what)}» را رد می‌کنید؟", "Decline “$what”?", "«$what» ردوئ؟")
  val noteLabel: String get() = t("یادداشت (اختیاری)", "Note (optional)", "یادښت (اختیاري)")
  val doneApproved: String get() = t("تأیید شد؛ صندوق همین حالا ادامه می‌دهد.", "Approved; the till continues now.", "تایید شو؛ صندوق همدا اوس دوام ورکوي.")
  val doneDenied: String get() = t("رد شد.", "Declined.", "رد شو.")
}
