package ir.vil3ntec.tohid.ui.v3.account.subscription

import ir.vil3ntec.tohid.core.i18n.StrAccount
import ir.vil3ntec.tohid.core.net.ApiJson
import ir.vil3ntec.tohid.ui.v3.account.AccountFormat
import kotlinx.serialization.json.JsonObject

/**
 *  «اشتراکِ من» از تپشِ کش‌شده (آزمون: `SubscriptionModelTest`).
 *
 *  ⛔ رنگ و روزِ مانده از سرور می‌آید (`subscriptionView` در `routes/portal.js`)؛
 *  هیچ قاعدهٔ جدا یا قیمتی این‌جا ساخته نمی‌شود — وگرنه روزی برنامه سبز می‌گفت و
 *  پورتال سرخ. «دائمی ✓» شمارش ندارد (نه صفر، نه «۰ روز مانده»).
 */
object SubscriptionModel {

  /** رنگِ سرور: green / yellow / red / grey */
  enum class Tone { GREEN, YELLOW, RED, GREY }

  data class Hero(
    val plan: String,
    val headline: String,
    val permanent: Boolean,
    val active: Boolean,
    val days: Int,
    /** سرخط «N روز مانده» است (شمارنده)، نه «دائمی ✓» یا «تمام شده» */
    val countsDays: Boolean,
    /** ۰ تا ۱ */
    val progress: Float,
    val tone: Tone,
    val starts: String,
    val ends: String,
    val status: String,
  )

  fun toneOf(color: String): Tone = when (color.trim().lowercase()) {
    "green" -> Tone.GREEN
    "yellow" -> Tone.YELLOW
    "red" -> Tone.RED
    else -> Tone.GREY
  }

  fun statusLabel(raw: String): String = when (raw.trim().lowercase()) {
    "active" -> StrAccount.stateActive
    "trial" -> StrAccount.trialTitle
    "expired" -> StrAccount.bannerExpiredTitle
    "none", "" -> StrAccount.bannerNoneTitle
    else -> raw
  }

  /** نوارِ پیام زیرِ کارت: هفت روزِ آخر (همان قاعدهٔ قفلِ نرم) یا تمام‌شده */
  enum class Notice { NONE, ENDING, EXPIRED }

  fun hero(sub: JsonObject): Hero {
    val permanent = ApiJson.bool(sub, "permanent")
    val active = ApiJson.bool(sub, "active")
    val status = ApiJson.text(sub, "status")
    val days = ApiJson.int(sub, "daysLeft")
    val ended = !active && status.isNotBlank() && status != "none"
    return Hero(
      plan = ApiJson.text(sub, "plan").ifBlank { "—" },
      headline = when {
        permanent -> StrAccount.permanent
        ended -> StrAccount.bannerExpiredTitle
        else -> StrAccount.daysLeft(days)
      },
      permanent = permanent,
      active = active,
      days = days,
      countsDays = !permanent && !ended,
      progress = if (permanent) 1f else ApiJson.int(sub, "progress").coerceIn(0, 100) / 100f,
      tone = toneOf(ApiJson.text(sub, "color")),
      starts = AccountFormat.date(ApiJson.long(sub, "startsAt")),
      ends = if (permanent) StrAccount.permanent else AccountFormat.date(ApiJson.long(sub, "endsAt")),
      status = statusLabel(status),
    )
  }

  fun notice(sub: JsonObject): Notice {
    val permanent = ApiJson.bool(sub, "permanent")
    val active = ApiJson.bool(sub, "active")
    val status = ApiJson.text(sub, "status")
    return when {
      !permanent && active && ApiJson.int(sub, "daysLeft") in 1..7 -> Notice.ENDING
      !active && status != "none" -> Notice.EXPIRED
      else -> Notice.NONE
    }
  }

  /** یک ردیفِ فهرست (دستگاه، پرداخت، پیامِ مدیر): عنوان و مقدار */
  data class Line(val title: String, val value: String)

  fun devices(list: List<JsonObject>): List<Line> = list.map {
    Line(ApiJson.text(it, "name").ifBlank { ApiJson.text(it, "device_uid") }, AccountFormat.date(ApiJson.long(it, "last_seen_at")))
  }

  /** ⛔ مبلغ همان برچسبِ سرور است؛ این‌جا هیچ قیمتی ساخته نمی‌شود */
  fun payments(list: List<JsonObject>): List<Line> = list.map {
    Line(ApiJson.text(it, "planTitle").ifBlank { ApiJson.text(it, "plan") }, ApiJson.text(it, "amountLabel").ifBlank { ApiJson.text(it, "amount") }.ifBlank { "—" })
  }

  fun notices(list: List<JsonObject>): List<Line> = list.take(10).map {
    Line(ApiJson.text(it, "title"), AccountFormat.date(ApiJson.long(it, "createdAt")))
  }
}
