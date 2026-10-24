package ir.vil3ntec.tohid.ui.v3.shell

import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrHome
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.data.repo.EventsRepository

/**
 *  «خبرِ دکان» — کارهای بقیهٔ اعضا (`events.feed`، بی خبرِ خودِ من) به شکلِ ردیف‌های
 *  روزبه‌روز. منطقِ خالص: جمله، زمانِ نسبی («۵ دقیقه پیش»)، سرخطِ روز و «دیده‌نشده».
 *  هیچ رخدادی شناسهٔ سند ندارد، پس ردیف به جایی نمی‌رود (shell.md: «else no action»).
 *  آزمون: `NewsModelTest`.
 */
object NewsModel {
  data class Row(
    val key: String,
    /** حرفِ اولِ نامِ عضو برای آواتار */
    val letter: String,
    val title: String,
    val subtitle: String,
    val at: Long,
    val unseen: Boolean,
  )

  data class Day(val label: String, val rows: List<Row>)

  private const val MINUTE = 60_000L
  private const val HOUR = 60 * MINUTE
  private const val DAY = 24 * HOUR

  /** «همین حالا» / «۵ دقیقه پیش» / «۲ ساعت پیش» / «۳ روز پیش» */
  fun since(at: Long, now: Long): String {
    val minutes = ((now - at) / MINUTE).coerceAtLeast(0)
    return when {
      minutes < 1 -> StrHome.justNow
      minutes < 60 -> StrHome.minutesAgo(minutes)
      minutes < 24 * 60 -> StrHome.hoursAgo(minutes / 60)
      else -> StrHome.daysAgo(minutes / (24 * 60))
    }
  }

  /** جملهٔ ردیف: فروش ⇐ «احمد فروشی ثبت کرد»؛ بقیه همان سرخطِ سرور */
  fun sentence(e: EventsRepository.Event): String {
    val name = e.userName.ifBlank { StrHome.someone }
    return when (e.kind) {
      "sale" -> StrHome.newsSale(name)
      else -> e.title.ifBlank { name }
    }
  }

  /** زیرِ جمله: مبلغ/شرحِ رخداد و زمانِ نسبی (نامِ عضو برای رخدادی که نامش در جمله نیست) */
  fun detail(e: EventsRepository.Event, now: Long): String {
    val parts = buildList {
      if (e.kind != "sale" && e.userName.isNotBlank()) add(e.userName)
      if (e.body.isNotBlank()) add(e.body)
      add(since(e.at, now))
    }
    return parts.joinToString(" · ")
  }

  /**
   *  ردیف‌ها، تازه‌ترین بالا، زیرِ سرخطِ روز («امروز»، «دیروز»، تاریخ).
   *  @param seenAt آخرین باری که فهرستِ خبر دیده شد (`ShopFeed.seenAt`)
   *  @param dayOf تاریخِ `YYYY-MM-DD` ِ یک لحظه (روزِ محلیِ دستگاه)
   *  @param dateLabel نوشتهٔ تاریخِ روزهای دورتر
   */
  fun days(
    events: List<EventsRepository.Event>,
    me: String,
    now: Long,
    seenAt: Long,
    dayOf: (Long) -> String,
    dateLabel: (String) -> String,
  ): List<Day> {
    val today = dayOf(now)
    val yesterday = dayOf(now - DAY)
    return events
      .filter { it.userId != me }
      .sortedByDescending { it.at }
      .groupBy { dayOf(it.at) }
      .map { (day, list) ->
        Day(
          label = when (day) {
            today -> Str.today
            yesterday -> StrKit.yesterday
            else -> dateLabel(day)
          },
          rows = list.map { e ->
            Row(
              key = e.id.ifBlank { "${e.kind}:${e.at}" },
              letter = e.userName.trim().firstOrNull()?.toString() ?: "؟",
              title = sentence(e),
              subtitle = detail(e, now),
              at = e.at,
              unseen = e.at > seenAt,
            )
          },
        )
      }
  }

  /** خبرِ دیده‌نشده هست؟ (نقطهٔ زنگ) */
  fun hasUnseen(events: List<EventsRepository.Event>, me: String, seenAt: Long): Boolean =
    events.any { it.userId != me && it.at > seenAt }
}
