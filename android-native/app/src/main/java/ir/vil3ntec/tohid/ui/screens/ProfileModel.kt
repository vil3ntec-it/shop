package ir.vil3ntec.tohid.ui.screens

/**
 *  منطقِ خالصِ «حساب من» (phone-ui، تصویرِ مرجعِ ۳) — بی Compose، برای آزمونِ JVM.
 *
 *  `plan` همان تصمیمی است که پیش‌تر درونِ `SubscriptionRow` بود (مجوزِ روی گوشی
 *  مقدم، وگرنه حرفِ سرور از `SubscriptionPulse`) و فقط جابه‌جا شد تا نوارِ
 *  هشدارِ بالای کارت هم از **همان** حساب بخواند — دو جا، یک حرف.
 *  `notice` می‌گوید نوارِ هشدار اصلاً لازم است یا نه: فقط وقتی واقعاً چیزی برای
 *  گفتن هست (تمام شده، یا ≤ `warnDays` روز مانده)، نه برای «بدون اشتراک».
 */
object ProfileModel {

  data class Plan(
    val active: Boolean,
    val expired: Boolean,
    val trial: Boolean,
    val days: Int,
    val urgent: Boolean,
  )

  fun plan(
    localActive: Boolean,
    localDays: Int,
    localExpired: Boolean,
    serverActive: Boolean,
    serverDays: Int,
    serverTrial: Boolean,
    warnDays: Int,
  ): Plan {
    val serverOn = serverActive && serverDays > 0
    val active = localActive || serverOn
    val days = when {
      localActive -> localDays
      serverOn -> serverDays
      else -> 0
    }
    val trial = !localActive && serverOn && serverTrial
    return Plan(
      active = active,
      expired = localExpired,
      trial = trial,
      days = days,
      urgent = localExpired || (active && days <= warnDays),
    )
  }

  sealed class Notice {
    /** اشتراک تمام شده (یا در مهلتِ پس از پایان) */
    data object Expired : Notice()
    /** چند روزِ کم مانده؛ `trial` یعنی دورهٔ آزمایشی */
    data class Ending(val days: Int, val trial: Boolean) : Notice()
  }

  fun notice(p: Plan, warnDays: Int): Notice? = when {
    p.expired -> Notice.Expired
    p.active && p.days <= warnDays -> Notice.Ending(p.days.coerceAtLeast(0), p.trial)
    else -> null
  }

  /** نشانِ نقش — همان نام‌های `TEAM_ROLES`ِ وب */
  fun roleLabel(role: String): String = when (role.trim().lowercase()) {
    "manager" -> "مدیر"
    "staff" -> "شاگرد"
    else -> "صاحب دکان"
  }
}
