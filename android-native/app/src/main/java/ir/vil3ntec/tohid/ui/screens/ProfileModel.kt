package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrAccount
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
  ): Plan = ir.vil3ntec.tohid.ui.v3.account.profile.ProfileHomeModel
    .plan(localActive, localDays, localExpired, serverActive, serverDays, serverTrial, warnDays)
    .let { Plan(it.active, it.expired, it.trial, it.days, it.urgent) }

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
    "manager" -> StrAccount.roleManager
    "staff" -> StrAccount.roleSeller
    else -> StrAccount.roleOwner
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
    a.name.isBlank() -> StrAccount.noName
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

  /** کارِ دکمهٔ سفیدِ نوارِ تیره */
  enum class Action { SIGN_IN, RENEW, PLANS }

  data class Banner(val title: String, val subtitle: String, val button: String, val action: Action)

  /**
   *  نوارِ تیرهٔ کارت (r4 «Kredin Tükendi!») — **همیشه** هست و حالِ واقعیِ اشتراک
   *  را می‌گوید. `n` رقم را با تنظیمِ رقمِ برنامه می‌نویسد.
   */
  fun banner(signedIn: Boolean, p: Plan?, warnDays: Int, n: (Int) -> String = Int::toString): Banner = when {
    !signedIn || p == null -> Banner(
      StrAccount.bannerGuestTitle,
      "با حساب، دفترِ دکان پشتیبان می‌گیرد و روی گوشیِ دیگر هم می‌آید",
      Str.signIn, Action.SIGN_IN,
    )
    p.expired -> Banner(
      StrAccount.bannerExpiredTitle,
      StrAccount.bannerExpiredBody,
      Str.renew, Action.RENEW,
    )
    p.active && p.trial -> Banner(
      if (p.days > 0) "آزمایشی: ${n(p.days)} روزِ دیگر" else StrAccount.bannerTrialLast,
      StrAccount.bannerTrialBody,
      StrAccount.plansButton, Action.PLANS,
    )
    p.active -> Banner(
      if (p.days > 0) "اشتراک: ${n(p.days)} روزِ دیگر" else StrAccount.bannerActiveLast,
      if (p.days <= warnDays) StrAccount.bannerSoonBody else StrAccount.bannerOkBody,
      Str.renew, Action.RENEW,
    )
    else -> Banner(
      StrAccount.bannerNoneTitle,
      StrAccount.bannerNoneBody,
      StrAccount.plansButton, Action.PLANS,
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
    if (signedIn) add(MenuItem(EDIT, StrAccount.menuEdit, null))
    add(MenuItem("devices", StrAccount.menuDevices, "devices"))
    add(MenuItem("plan", StrAccount.menuPlan, "myplan"))
    add(MenuItem("stats", StrAccount.menuStats, "reports"))
    if (canSettings) add(MenuItem("settings", Str.settings, "settings"))
    add(MenuItem("support", Str.support, "support"))
    if (signedIn) add(MenuItem(SIGN_OUT, StrAccount.menuSignOut, null, danger = true))
  }
}
