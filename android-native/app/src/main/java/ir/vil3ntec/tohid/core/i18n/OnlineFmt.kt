package ir.vil3ntec.tohid.core.i18n

import ir.vil3ntec.tohid.core.delivery.DeskActions
import ir.vil3ntec.tohid.core.orders.OrderText

/**
 *  کمکِ نمایشِ گروهِ G8 کنارِ جدولِ `StrOnline`. جدول نیست و در `I18nTables` نمی‌آید.
 *  این‌جا هیچ `t(…)` ِ تازه‌ای نیست: هر نوشته از ردیفی در `StrOnline` می‌آید تا آزمونِ پوشش آن را بشمارد.
 *  فقط کارهایی این‌جاست که ردیفِ جدول نمی‌تواند باشد: کدِ ناشناخته ⇐ هیچ، آرگومانِ نوع‌دار، و قالبِ بی‌ترجمه.
 */
object OnlineFmt {
  /** مبلغ از واحدِ خرد (پول) */
  fun afn(minor: Long): String = StrOnline.afnAmount(OrderText.money(minor))

  fun orderNo(n: Long): String = "#$n"

  fun ago(ms: Long, now: Long): String {
    val d = maxOf(0L, (now - ms) / 60_000)
    return when {
      d < 1 -> StrOnline.agoNow
      d < 60 -> StrOnline.agoMinutes(d)
      d < 24 * 60 -> StrOnline.agoHours(d / 60)
      else -> StrOnline.agoDays(d / (24 * 60))
    }
  }

  /** بایت ⇐ کیلوبایتِ گردشده، کمینه ۱ */
  fun kb(bytes: Long): String = StrOnline.kbAmount(maxOf(1, (bytes + 512) / 1024))

  fun pay(paymentStatus: String, paymentMethod: String): String = when {
    paymentStatus == "paid" -> StrOnline.payPaid
    paymentMethod == "cod" -> StrOnline.payCod
    else -> StrOnline.payUnpaid
  }

  fun presenceBadge(badge: String, until: String?): String = when (badge) {
    "open_online" -> StrOnline.presOpenOnline
    "open_unconfirmed" -> StrOnline.presOpenUnconfirmed
    "declared_until" -> if (until != null) StrOnline.presDeclaredUntil(until) else StrOnline.presDeclared
    "closed" -> StrOnline.presClosed
    "offline" -> StrOnline.presOffline
    else -> StrOnline.presUnknown
  }

  fun presenceShort(badge: String, until: String?): String = when (badge) {
    "open_online" -> StrOnline.presShortOpenOnline
    "open_unconfirmed" -> StrOnline.presShortOpenUnconfirmed
    "declared_until" -> if (until != null) StrOnline.presShortUntil(until) else StrOnline.presShortOnline
    "closed" -> StrOnline.presShortClosed
    "offline" -> StrOnline.presShortOffline
    else -> StrOnline.presShortUnknown
  }

  /** شماره، مختصات و کدِ لاتین درونِ جملهٔ فارسی: جدا از جهتِ جمله (LRI…PDI) تا وارونه نشود */
  fun ltr(s: String): String = "\u2066$s\u2069"

  fun deskAction(act: DeskActions.Act): String = StrOnline.deskActionLabel(act.name)

  /** ۰ = یکشنبه، مثلِ `StorefrontOwner.DAY_NAMES` */
  fun dayName(d: Int): String = when (d) {
    0 -> StrOnline.daySunday
    1 -> StrOnline.dayMonday
    2 -> StrOnline.dayTuesday
    3 -> StrOnline.dayWednesday
    4 -> StrOnline.dayThursday
    5 -> StrOnline.dayFriday
    6 -> StrOnline.daySaturday
    else -> throw IndexOutOfBoundsException("day $d")
  }

  fun locationText(lat: Double?, lng: Double?): String =
    if (lat == null || lng == null) StrOnline.locNotSet
    else StrOnline.locAbout("%.2f".format(java.util.Locale.ROOT, lat) + "، " + "%.2f".format(java.util.Locale.ROOT, lng))

  /** پذیرشِ سفارشِ ویترین؛ کدِ ناشناخته ⇐ "" (صفحه آن ردیف را نشان نمی‌دهد) */
  fun acceptance(a: String): String = when (a) {
    "accepting" -> StrOnline.acceptAccepting
    "delayed" -> StrOnline.acceptDelayed
    "later" -> StrOnline.acceptLater
    else -> ""
  }

  /** نوعِ پیامِ بی‌متن؛ متن یا نوعِ ناشناخته ⇐ "" */
  fun ctKind(kind: String): String = when (kind) {
    "image" -> StrOnline.ctKindImage
    "audio" -> StrOnline.ctKindAudio
    "file" -> StrOnline.ctKindFile
    else -> ""
  }

  /** وضعیتِ پیام (صفِ دستگاه یا رسیدِ سرور)؛ ناشناخته ⇐ "" */
  fun ctState(id: String): String = when (id) {
    "queued" -> StrOnline.ctStateQueued
    "sending" -> StrOnline.ctStateSending
    "waiting" -> StrOnline.ctStateWaiting
    "auth" -> StrOnline.ctStateAuth
    "failed" -> StrOnline.ctStateFailed
    "sent" -> StrOnline.ctStateSent
    "delivered" -> StrOnline.ctStateDelivered
    "read" -> StrOnline.ctStateRead
    else -> ""
  }
}
