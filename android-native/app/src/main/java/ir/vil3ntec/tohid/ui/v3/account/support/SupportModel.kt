package ir.vil3ntec.tohid.ui.v3.account.support

import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrAccount
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.model.SupportMessageDto
import ir.vil3ntec.tohid.ui.v3.account.AccountFormat

/**
 *  گفت‌وگوی پشتیبانی (آزمون: `SupportModelTest`): سه گونه پیام — خودم، پاسخِ
 *  پشتیبانی، و خبرِ خودکارِ سامانه (رنگِ هشدار تا با گفت‌وگوی آدم‌ها قاطی نشود) —
 *  با جداکنندهٔ روز («امروز»، «دیروز»، تاریخ) میانشان.
 */
object SupportModel {

  enum class Kind { MINE, THEIRS, SYSTEM }

  fun kind(m: SupportMessageDto): Kind = when (m.sender) {
    "user" -> Kind.MINE
    "system" -> Kind.SYSTEM
    else -> Kind.THEIRS
  }

  sealed class Item {
    abstract val key: String
    data class Day(val label: String, override val key: String) : Item()
    data class Bubble(
      val id: String,
      val kind: Kind,
      /** نامِ فرستنده بالای حباب (پشتیبانی/سامانه)؛ برای پیامِ خودم null */
      val sender: String?,
      val body: String,
      val time: String,
      val sending: Boolean = false,
      override val key: String = "m$id",
    ) : Item()
  }

  /** «۰۹:۴۵» به وقتِ دستگاه */
  fun time(ms: Long): String {
    if (ms <= 0) return ""
    val c = java.util.Calendar.getInstance().apply { timeInMillis = AccountFormat.millis(ms) }
    return "%02d:%02d".format(c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE))
  }

  fun dayLabel(dayIso: String, todayIso: String, yesterdayIso: String, raw: Long): String = when (dayIso) {
    todayIso -> Str.today
    yesterdayIso -> StrKit.yesterday
    else -> AccountFormat.date(raw)
  }

  /**
   *  حباب‌ها با جداکنندهٔ روز؛ `pending` پیامی است که همین حالا فرستاده می‌شود
   *  (حبابِ خودم با «در حال فرستادن…»).
   */
  fun items(messages: List<SupportMessageDto>, now: Long, pending: String? = null): List<Item> {
    val today = AccountFormat.isoOf(now)
    val yesterday = AccountFormat.isoOf(now - 86_400_000L)
    val out = ArrayList<Item>()
    var lastDay: String? = null
    messages.forEach { m ->
      val day = if (m.createdAt > 0) AccountFormat.isoOf(AccountFormat.millis(m.createdAt)) else lastDay ?: today
      if (day != lastDay) {
        out += Item.Day(dayLabel(day, today, yesterday, m.createdAt), "d$day")
        lastDay = day
      }
      val k = kind(m)
      out += Item.Bubble(
        id = m.id.ifBlank { "${m.createdAt}-${out.size}" },
        kind = k,
        sender = when (k) {
          Kind.MINE -> null
          Kind.SYSTEM -> StrAccount.systemSender
          Kind.THEIRS -> m.senderName.ifBlank { Str.support }
        },
        body = m.body,
        time = time(m.createdAt),
      )
    }
    if (!pending.isNullOrBlank()) {
      if (lastDay != today) out += Item.Day(Str.today, "d$today")
      out += Item.Bubble("pending", Kind.MINE, null, pending, Str.sending, sending = true)
    }
    return out
  }

  fun canSend(draft: String, sending: Boolean): Boolean = draft.isNotBlank() && !sending
}
