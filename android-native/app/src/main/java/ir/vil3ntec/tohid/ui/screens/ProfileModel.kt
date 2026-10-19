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

  /** نشانِ نقش روی کارتِ حساب (گوشی نسخهٔ ۳، بندِ B: «صاحب دکان» / «مدیر» / «فروشنده») */
  fun roleLabel(role: String): String = when (role.trim().lowercase()) {
    "manager" -> "مدیر"
    "staff" -> "فروشنده"
    else -> "صاحب دکان"
  }

  /* ===================== کارتِ «حساب من» (r4) ===================== */

  /** آن‌چه کارت از حساب می‌داند — از `SyncStore`، یا از پیش‌نمایشِ عکس‌ها */
  data class Account(
    val signedIn: Boolean,
    val name: String,
    val email: String = "",
    val phone: String = "",
    val role: String = "owner",
  )

  const val GUEST = "مهمان"

  /** نامِ روی کارت: بیرون از حساب «مهمان»، نامِ خالی «بی‌نام» */
  fun displayName(a: Account): String = when {
    !a.signedIn -> GUEST
    a.name.isBlank() -> "بی‌نام"
    else -> a.name.trim()
  }

  /** خطِ خاکستریِ زیرِ نام: شماره، وگرنه ایمیل؛ بیرون از حساب نامِ دکان (اگر هست) */
  fun contact(a: Account, shopName: String): String = when {
    !a.signedIn -> shopName.trim()
    a.phone.isNotBlank() -> a.phone.trim()
    else -> a.email.trim()
  }

  /** نشانِ سیاهِ نقش — فقط برای حسابِ واردشده */
  fun badge(a: Account): String? = if (a.signedIn) roleLabel(a.role) else null

  /** حرفِ اولِ نام برای دایرهٔ بی‌عکس؛ مهمان حرف ندارد (آیکنِ آدمک) */
  fun initial(a: Account): String? =
    if (!a.signedIn) null else a.name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercase()

  /** کارِ دکمهٔ سفیدِ نوارِ تیره */
  enum class Action { SIGN_IN, RENEW, PLANS }

  data class Banner(val title: String, val subtitle: String, val button: String, val action: Action)

  /**
   *  نوارِ تیرهٔ کارت (r4 «Kredin Tükendi!») — **همیشه** هست و حالِ واقعیِ اشتراک
   *  را می‌گوید. `n` رقم را با تنظیمِ رقمِ برنامه می‌نویسد.
   */
  fun banner(signedIn: Boolean, p: Plan?, warnDays: Int, n: (Int) -> String = Int::toString): Banner = when {
    !signedIn || p == null -> Banner(
      "وارد حساب نشده‌اید",
      "با حساب، دفترِ دکان پشتیبان می‌گیرد و روی گوشیِ دیگر هم می‌آید",
      "ورود", Action.SIGN_IN,
    )
    p.expired -> Banner(
      "اشتراک تمام شده",
      "هیچ داده‌ای پاک نمی‌شود؛ خروجی و چاپ باز می‌ماند",
      "تمدید", Action.RENEW,
    )
    p.active && p.trial -> Banner(
      if (p.days > 0) "آزمایشی: ${n(p.days)} روزِ دیگر" else "امروز آخرین روزِ آزمایشی",
      "برای ادامه پس از آزمایش، یکی از پلن‌ها را بگیرید",
      "پلن‌ها", Action.PLANS,
    )
    p.active -> Banner(
      if (p.days > 0) "اشتراک: ${n(p.days)} روزِ دیگر" else "امروز آخرین روزِ اشتراک",
      if (p.days <= warnDays) "به‌زودی تمام می‌شود؛ پیش از پایان تمدید کنید" else "همهٔ بخش‌ها باز است",
      "تمدید", Action.RENEW,
    )
    else -> Banner(
      "بدونِ اشتراک",
      "برای باز شدنِ همهٔ بخش‌ها یکی از پلن‌ها را بگیرید",
      "پلن‌ها", Action.PLANS,
    )
  }

  /** ردیفِ فهرستِ زیرِ کارت؛ `route` مسیرِ همان صفحهٔ برنامه است */
  data class MenuItem(val id: String, val label: String, val route: String?, val danger: Boolean = false)

  const val EDIT = "edit"
  const val SIGN_OUT = "signout"

  /**
   *  فهرستِ «حساب من» (بندِ B) به ترتیبِ مرجع. ویرایشِ حساب و خروج فقط برای
   *  حسابِ واردشده؛ «تنظیمات» فقط برای کسی که حقِ بازکردنش را دارد (شاگرد نه —
   *  همان سدِ `ShopRole.canOpenSettings`).
   */
  fun menu(signedIn: Boolean, canSettings: Boolean): List<MenuItem> = buildList {
    if (signedIn) add(MenuItem(EDIT, "ویرایش حساب و دکان", null))
    add(MenuItem("devices", "دستگاه‌ها", "devices"))
    add(MenuItem("plan", "اشتراک و پرداخت", "myplan"))
    add(MenuItem("stats", "آمار", "reports"))
    if (canSettings) add(MenuItem("settings", "تنظیمات", "settings"))
    add(MenuItem("support", "پشتیبانی", "support"))
    if (signedIn) add(MenuItem(SIGN_OUT, "خروج از حساب", null, danger = true))
  }
}
