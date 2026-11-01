package ir.vil3ntec.tohid.core.i18n

/**
 *  کارهای میزِ سفارش در برنامهٔ مدیریت — ORD-07 («عقب انداختن»)، ORD-18 (هشدارِ زمان‌بندی‌شده)، ORD-03 (قطعِ اتصال)،
 *  ORD-19 (کارِ گروهی)، ORD-16 (پیام‌ها). قاعده‌ها سرِ `I18n.kt`؛ ترجمه‌های نامطمئن در `docs/i18n-glossary.md`.
 */
@Suppress("unused")
object StrOrderOps {

  // ORD-07 «عقب انداختن» (`ui/screens/OrderDelayUi.kt`)
  val delayAction: String get() = t("عقب انداختن", "Delay", "ځنډول")
  fun delayTitle(orderNo: String): String = t("عقب انداختنِ سفارش #$orderNo", "Delay order #$orderNo", "د #$orderNo فرمایش ځنډول")
  fun delayNow(at: String): String = t("اکنون آماده تا $at", "Now ready by $at", "اوس تر $at پورې چمتو")
  val delayNoTime: String get() = t("زمانِ آماده‌ای ثبت نشده؛ از همین حالا حساب می‌شود", "No ready time yet; counted from now", "د چمتو کېدو وخت نشته؛ له همدې اوسه شمېرل کېږي")
  fun delayMinutes(min: Int): String = t("$min دقیقه دیرتر", "$min min later", "$min دقیقې وروسته")
  val delayReason: String get() = t("دلیل (اختیاری)", "Reason (optional)", "لامل (اختیاري)")
  val delayReasonBusy: String get() = t("شلوغ", "Busy", "بوخت")
  val delayReasonStock: String get() = t("منتظرِ کالا", "Waiting for stock", "د توکو په تمه")
  val delayReasonCourier: String get() = t("منتظرِ پیک", "Waiting for courier", "د رسوونکي په تمه")
  val delayReasonCustomer: String get() = t("درخواستِ مشتری", "Customer request", "د پېرودونکي غوښتنه")
  val delayReasonOther: String get() = t("دلیلِ دیگر", "Other reason", "بل لامل")
  val delayNote: String get() = t("توضیح", "Note", "یادښت")
  val delayNoteHint: String get() = t("برای «دلیلِ دیگر» لازم است", "Required for “Other reason”", "د «بل لامل» لپاره اړین دی")
  val delayConfirm: String get() = t("تأیید", "Confirm", "تایید")
  val delayCancel: String get() = t("انصراف", "Cancel", "لغوه")
  fun delayDone(orderNo: String, at: String): String = t("سفارش #$orderNo عقب افتاد: آماده تا $at", "Order #$orderNo delayed: ready by $at", "د #$orderNo فرمایش وځنډول شو: تر $at پورې چمتو")
  val delayChanged: String get() = t("زمانِ آماده همین حالا جای دیگری عوض شد؛ زمانِ تازه را ببینید و دوباره بزنید", "The ready time just changed elsewhere; check it and try again", "د چمتو کېدو وخت همدا اوس بل ځای بدل شو؛ نوی وخت وګورئ او بیا یې وکړئ")
  val delayNotAllowed: String get() = t("این سفارش دیگر عقب انداختنی نیست", "This order can no longer be delayed", "دا فرمایش نور نه ځنډول کېږي")
  val delayRetry: String get() = t("اتصال قطع شد؛ دوباره «تأیید» را بزنید — دو بار عقب نمی‌رود", "Connection lost; press Confirm again — it will not be delayed twice", "اړیکه پرې شوه؛ بیا «تایید» ووهئ — دوه ځله نه ځنډول کېږي")
  fun delayEvent(at: String): String = t("عقب افتاد: آماده تا $at", "Delayed: ready by $at", "وځنډول شو: تر $at پورې چمتو")
  fun delayEventPrev(at: String): String = t("پیش‌تر $at", "was $at", "مخکې $at")

  // ORD-03 «اتصال قطع است» و ORD-18 «وقتِ شروعِ سفارشِ زمان‌بندی‌شده» (`ui/screens/OrderAlertsUi.kt`)
  val netDown: String get() = t("اتصال قطع است — سفارشِ تازه دیده نمی‌شود", "Connection lost — new orders are not showing", "اړیکه پرې ده — نوي فرمایشونه نه ښکاري")
  fun netLastOk(min: Int): String = t("آخرین به‌روزرسانی: $min دقیقه پیش.", "Last update: $min min ago.", "وروستی تازه کېدل: $min دقیقې مخکې.")
  val netBack: String get() = t("با برگشتِ اینترنت صف خودش تازه می‌شود.", "The queue refreshes by itself when the internet is back.", "کله چې انټرنېټ راشي، کتار پخپله تازه کېږي.")
  fun startTitle(orderNo: String): String = t("سفارشِ زمان‌بندی‌شده #$orderNo — وقتِ شروع", "Scheduled order #$orderNo — time to start", "مهالوېشل شوی فرمایش #$orderNo — د پیل وخت")
  fun startIn(min: Int): String = t("تا $min دقیقهٔ دیگر وقتش است؛ آماده‌سازی را شروع کنید.", "Due in $min min; start preparing.", "تر $min دقیقو پورې یې وخت دی؛ چمتو کول پیل کړئ.")
  val startDue: String get() = t("وقتش رسیده و هنوز شروع نشده است.", "It is due and has not started yet.", "وخت یې رسېدلی او لا نه دی پیل شوی.")
  val startOpen: String get() = t("باز کن", "Open", "پرانیزه")
  val startSeen: String get() = t("دیدم", "Got it", "ومې لید")
}
