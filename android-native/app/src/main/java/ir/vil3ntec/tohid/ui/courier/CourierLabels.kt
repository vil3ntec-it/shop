package ir.vil3ntec.tohid.ui.courier

import ir.vil3ntec.tohid.core.i18n.StrCourierApp
import ir.vil3ntec.tohid.money

/*
 *  برچسب‌ها و کمکی‌های قالبِ حالتِ پیک (وضعیت، جملهٔ هر پله، دکمه، دلیل، مبلغ، ساعت، ورودیِ عدد) — بی تغییر از
 *  `CourierHome.kt` جدا شد تا آن پرونده زیرِ سقفِ اندازه بماند (S94).
 */

internal val STATUS_LABEL: Map<String, String> get() = mapOf(
  "requested" to StrCourierApp.stRequested, "assigned" to StrCourierApp.stAssigned, "picked_up" to StrCourierApp.stPickedUp,
  "moving" to StrCourierApp.stMoving, "arrived" to StrCourierApp.stArrived, "failed" to StrCourierApp.stFailed,
  "delivered" to StrCourierApp.stDelivered, "rejected" to StrCourierApp.stRejected, "withdrawn" to StrCourierApp.stWithdrawn, "returned" to StrCourierApp.stReturned,
)

/** جملهٔ بزرگِ هر پله */
internal fun headline(s: String): String = when (s) {
  "requested" -> StrCourierApp.hlRequested
  "assigned" -> StrCourierApp.hlAssigned
  "picked_up" -> StrCourierApp.hlPickedUp
  "moving" -> StrCourierApp.hlMoving
  "arrived" -> StrCourierApp.hlArrived
  "failed" -> StrCourierApp.hlFailed
  "delivered" -> StrCourierApp.stDelivered
  "rejected" -> StrCourierApp.hlRejected
  "withdrawn" -> StrCourierApp.hlWithdrawn
  "returned" -> StrCourierApp.hlReturned
  else -> STATUS_LABEL[s] ?: s
}

internal fun actionLabel(from: String, to: String): String = when (to) {
  "assigned" -> StrCourierApp.acAccept
  "rejected" -> StrCourierApp.acReject
  "picked_up" -> StrCourierApp.acPickedUp
  "moving" -> if (from == "failed") StrCourierApp.acRetry else StrCourierApp.acMoving
  "arrived" -> StrCourierApp.acArrived
  "delivered" -> StrCourierApp.stDelivered
  "failed" -> StrCourierApp.stFailed
  else -> to
}

internal fun isNegative(to: String) = to == "rejected" || to == "failed"

internal val REASON_LABEL: Map<String, String> get() = mapOf(
  "busy" to StrCourierApp.rsBusy, "too_far" to StrCourierApp.rsTooFar, "cash_limit" to StrCourierApp.rsCashLimit,
  "no_answer" to StrCourierApp.rsNoAnswer, "wrong_address" to StrCourierApp.rsWrongAddress, "refused" to StrCourierApp.rsRefused,
  "recalled" to StrCourierApp.rsRecalled, "partial_payment" to StrCourierApp.rsPartialPayment, "refused_amount" to StrCourierApp.rsRefusedAmount,
  "damaged_or_short" to StrCourierApp.rsDamagedOrShort, "other" to StrCourierApp.rsOther,
)

/** یادداشتِ «دکان پس خواست» روی کارت و جزئیات */
internal val RECALL_NOTE: String get() = StrCourierApp.recallNote

/** پله‌های نوارِ پیشرفت: گرفتم ⇐ در راه ⇐ رسیدم ⇐ تحویل */
internal val STEPS: List<String> get() = listOf(StrCourierApp.stepGot, StrCourierApp.stMoving, StrCourierApp.acArrived, StrCourierApp.stepDelivered)

/** چند پله رسیده — منفی یعنی نوار نشان داده نمی‌شود */
internal fun reached(status: String): Int = when (status) {
  "assigned" -> 0
  "picked_up" -> 1
  "moving" -> 2
  "arrived" -> 3
  "delivered" -> 4
  else -> -1
}

internal fun afn(minor: Long): String = ir.vil3ntec.tohid.core.i18n.StrOnline.afnAmount(money(minor / 100.0))

/** ساعت:دقیقهٔ گوشی (منطقهٔ زمانیِ گوشی از همان پلِ گزارش‌ها؛ متن بی java.* — راهِ آیفون) */
internal fun clock(at: Long): String =
  if (at <= 0) "—" else ir.vil3ntec.tohid.ui.screens.DayEndModel.hhmm(at, ir.vil3ntec.tohid.data.ReportBridge.offsetMinutes(at))

/** ورودیِ پول به افغانی (رقمِ فارسی هم) ⇒ واحدِ کوچک */
internal fun minorOf(raw: String): Long? {
  val t = buildString { for (c in raw) when (c) { in '0'..'9', '.' -> append(c); in '۰'..'۹' -> append('0' + (c - '۰')); '٫' -> append('.') } }
  val v = t.toDoubleOrNull() ?: return null
  if (v < 0 || v > 1_000_000_000) return null
  return Math.round(v * 100)
}

/** همان پالایشِ `NumberField`: رقمِ فارسی/عربی ⇒ لاتین، فقط رقم و نقطه */
internal fun numberInput(raw: String): String = buildString {
  for (c in raw) when (c) {
    in '0'..'9', '.' -> append(c)
    in '۰'..'۹' -> append('0' + (c - '۰'))
    in '٠'..'٩' -> append('0' + (c - '٠'))
    '٫' -> append('.')
  }
}
