package ir.vil3ntec.tohid.core.i18n

/**
 *  RET-11 — «درخواست‌های برگشت» ِ ویترین در صفِ سفارش‌های برنامهٔ دکان (`ui/v3/online/orders/ReturnRequestsAndroid.kt`؛
 *  منطق در `core/orders/OrderReturnRequests`، حال در `ui/screens/ReturnRequestsState.kt`).
 */
@Suppress("unused")
object StrReturnRequests {

  val title: String get() = t("درخواست‌های برگشت", "Return requests", "د بېرته ورکولو غوښتنې")
  fun entry(n: Int): String = t("درخواست‌های برگشت (${asIs(n)})", "Return requests ($n)", "د بېرته ورکولو غوښتنې ($n)")

  fun filter(f: String): String = when (f) {
    "pending" -> t("در انتظار", "Waiting", "په تمه")
    "accepted" -> t("پذیرفته", "Accepted", "منل شوې")
    "completed" -> t("ثبت‌شده", "Recorded", "ثبت شوې")
    "rejected" -> t("ردشده", "Declined", "رد شوې")
    else -> t("همه", "All", "ټولې")
  }

  fun status(s: String): String = when (s) {
    "pending" -> t("در انتظارِ بررسی", "Waiting for review", "کتنې ته په تمه")
    "accepted" -> t("پذیرفته؛ برگشت هنوز ثبت نشده", "Accepted; return not recorded yet", "منل شوې؛ بېرته ورکول لا نه دي ثبت")
    "completed" -> t("برگشت ثبت شد", "Return recorded", "بېرته ورکول ثبت شول")
    else -> t("رد شد", "Declined", "رد شوه")
  }

  fun reason(code: String): String = when (code) {
    "damaged" -> t("خراب یا شکسته", "Damaged or broken", "خراب یا مات")
    "wrong_item" -> t("کالای اشتباه", "Wrong item", "ناسم توکی")
    "expired" -> t("تاریخ گذشته", "Expired", "نېټه تېره")
    "not_as_described" -> t("با شرح یا اندازه جور نیست", "Not as described or wrong size", "له شرح یا اندازې سره سم نه دی")
    "changed_mind" -> t("پشیمانیِ مشتری", "Customer changed mind", "د پېرودونکي پښېمانتیا")
    else -> t("دلیلِ دیگر", "Other reason", "بل لامل")
  }

  fun method(m: String): String = when (m) {
    "courier" -> t("پیکِ دکان کالا را می‌گیرد", "Shop courier collects the items", "د دوکان پیک توکي اخلي")
    else -> t("مشتری کالا را به دکان می‌آورد", "Customer brings the items to the shop", "پېرودونکی توکي دوکان ته راوړي")
  }

  fun order(no: Long): String = t("سفارشِ ${asIs(no)}", "Order $no", "فرمایش $no")
  fun line(name: String, qty: String, bought: String): String =
    t("${asIs(name)} — ${asIs(qty)} از ${asIs(bought)}", "$name — $qty of $bought", "${asIs(name)} — ${asIs(qty)} له ${asIs(bought)} څخه")
  fun photos(n: Int): String = t("${asIs(n)} عکس", "$n photos", "${asIs(n)} انځورونه")
  fun note(text: String): String = t("دلیلِ دکان: ${asIs(text)}", "Shop's reason: $text", "د دوکان لامل: ${asIs(text)}")
  val empty: String get() = t("درخواستی در این فهرست نیست.", "No requests in this list.", "په دې لړ کې غوښتنه نشته.")

  val accept: String get() = t("پذیرفتن و ثبتِ برگشت", "Accept and record return", "منل او بېرته ورکول ثبتول")
  val post: String get() = t("ثبتِ برگشت", "Record return", "بېرته ورکول ثبتول")
  val report: String get() = t("برگشت ثبت شد؛ خبر به سرور", "Return recorded; tell the server", "بېرته ورکول ثبت شول؛ سرور ته خبر")
  val reject: String get() = t("رد با دلیل", "Decline with reason", "له لامل سره رد")
  val rejectTitle: String get() = t("ردِ درخواستِ برگشت", "Decline return request", "د بېرته ورکولو غوښتنه رد")
  val rejectHint: String get() = t("مشتری همین دلیل را در صفحهٔ سفارش می‌بیند.", "The customer sees this reason on the order page.", "پېرودونکی دا لامل د فرمایش په پاڼه کې ویني.")
  val rejectField: String get() = t("دلیل", "Reason", "لامل")
  val rejectSend: String get() = t("رد کن", "Decline", "رد یې کړه")

  val accepted: String get() = t("پذیرفته شد؛ برگهٔ برگشت را کامل کنید.", "Accepted; complete the return form.", "ومنل شوه؛ د بېرته ورکولو پاڼه بشپړه کړئ.")
  val rejected: String get() = t("درخواست رد شد.", "Request declined.", "غوښتنه رد شوه.")
  val completed: String get() = t("برگشت ثبت و به مشتری خبر داده شد.", "Return recorded and the customer was told.", "بېرته ورکول ثبت او پېرودونکي ته خبر ورکړل شو.")
  val noSale: String get() = t(
    "فروشِ این سفارش هنوز در دفترِ همین دستگاه نیست؛ پس از همگام شدنِ تحویلِ سفارش دوباره بزنید.",
    "This order's sale is not in this device's ledger yet; try again after the order handover syncs.",
    "د دې فرمایش پلور لا د دې وسیلې په دفتر کې نشته؛ د فرمایش د سپارلو له همغږي کېدو وروسته بیا یې ووهئ.",
  )
  val onlineRefund: String get() = t(
    "بازپرداختِ آنلاین هنوز نیست (منتظرِ حساب‌پی)؛ نقد یا اعتبارِ فروشگاه را برگزینید.",
    "Online refunds are not available yet (waiting for HesabPay); choose cash or store credit.",
    "آنلاین بېرته ورکړه لا نشته (د حساب‌پی په تمه)؛ نغدې یا د دوکان اعتبار وټاکئ.",
  )

  //  رویدادهای «پیام‌ها به مشتری» (ORD-16) — همان `RETURN_EVENTS` ِ `lib/customer-notify.js`
  val eventAccepted: String get() = t("درخواستِ برگشت پذیرفته شد", "Return request accepted", "د بېرته ورکولو غوښتنه ومنل شوه")
  val eventRejected: String get() = t("درخواستِ برگشت رد شد", "Return request declined", "د بېرته ورکولو غوښتنه رد شوه")
  val eventCompleted: String get() = t("برگشت ثبت شد", "Return recorded", "بېرته ورکول ثبت شول")
}
