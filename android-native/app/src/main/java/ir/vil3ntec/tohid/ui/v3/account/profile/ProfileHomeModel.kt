package ir.vil3ntec.tohid.ui.v3.account.profile

import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrAccount
import ir.vil3ntec.tohid.ui.v3.account.AccountFormat
import ir.vil3ntec.tohid.ui.v3.account.AccountLinks
import ir.vil3ntec.tohid.ui.v3.account.AccountPaths

/**
 *  حالتِ خالصِ «حساب من» (r4) — آزمون: `ProfileHomeModelTest`.
 *
 *  `plan` همان تصمیمِ پیشین است (مجوزِ روی گوشی مقدم، وگرنه حرفِ سرور از
 *  `SubscriptionPulse`) و نوارِ تیرهٔ کارت از همین می‌خواند. فهرست و سه کادرِ
 *  آمار هر کدام مسیرِ دقیقِ خودشان را دارند («هر کار به کادرِ خودش»).
 */
object ProfileHomeModel {

  data class Plan(val active: Boolean, val expired: Boolean, val trial: Boolean, val days: Int, val urgent: Boolean)

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
    return Plan(
      active = active,
      expired = localExpired,
      trial = !localActive && serverOn && serverTrial,
      days = days,
      urgent = localExpired || (active && days <= warnDays),
    )
  }

  /** آن‌چه کارت از حساب می‌داند — از `SyncStore` */
  data class Account(
    val signedIn: Boolean,
    val name: String,
    val email: String = "",
    val phone: String = "",
    val role: String = "owner",
  )

  /** نامِ روی کارت: بیرون از حساب «مهمان»، نامِ خالی «بی‌نام» */
  fun displayName(a: Account): String = when {
    !a.signedIn -> StrAccount.guest
    a.name.isBlank() -> StrAccount.noName
    else -> a.name.trim()
  }

  /** خطِ خاکستریِ زیرِ نام: شماره، وگرنه ایمیل؛ بیرون از حساب نامِ دکان */
  fun contact(a: Account, shopName: String): String = when {
    !a.signedIn -> shopName.trim()
    a.phone.isNotBlank() -> a.phone.trim()
    else -> a.email.trim()
  }

  /** قرصِ نقش (صاحب دکان / مدیر / فروشنده / پیک) — فقط برای حسابِ واردشده */
  fun badge(a: Account): String? = if (!a.signedIn) null else when (AccountFormat.roleOf(a.role)) {
    AccountFormat.Role.OWNER -> StrAccount.roleOwner
    AccountFormat.Role.MANAGER -> StrAccount.roleManager
    AccountFormat.Role.STAFF -> StrAccount.roleSeller
    AccountFormat.Role.COURIER -> StrAccount.roleCourier
  }

  /** کارِ دکمهٔ سفیدِ نوارِ تیره ⇒ مسیرِ دقیق */
  enum class Action(val route: String) {
    SIGN_IN(AccountLinks.SIGN_IN),
    RENEW(AccountPaths.PLANS),
    PLANS(AccountPaths.PLANS),
  }

  data class Banner(val title: String, val subtitle: String, val button: String, val action: Action)

  /** نوارِ تیرهٔ کارت — همیشه هست و حالِ واقعیِ اشتراک را می‌گوید */
  fun banner(signedIn: Boolean, p: Plan?, warnDays: Int): Banner = when {
    !signedIn || p == null -> Banner(StrAccount.bannerGuestTitle, StrAccount.bannerGuestBody, Str.signIn, Action.SIGN_IN)
    p.expired -> Banner(StrAccount.bannerExpiredTitle, StrAccount.bannerExpiredBody, Str.renew, Action.RENEW)
    p.active && p.trial -> Banner(
      if (p.days > 0) StrAccount.bannerTrialDays(p.days) else StrAccount.bannerTrialLast,
      StrAccount.bannerTrialBody, StrAccount.plansButton, Action.PLANS,
    )
    p.active -> Banner(
      if (p.days > 0) StrAccount.bannerActiveDays(p.days) else StrAccount.bannerActiveLast,
      if (p.days <= warnDays) StrAccount.bannerSoonBody else StrAccount.bannerOkBody,
      Str.renew, Action.RENEW,
    )
    else -> Banner(StrAccount.bannerNoneTitle, StrAccount.bannerNoneBody, StrAccount.plansButton, Action.PLANS)
  }

  enum class Item { EDIT, DEVICES, PLAN, STATS, SETTINGS, SUPPORT, SIGN_OUT }

  /** ردیفِ فهرستِ زیرِ کارت با مسیرِ دقیقِ همان کار */
  data class MenuItem(val item: Item, val label: String, val route: String, val danger: Boolean = false)

  /**
   *  فهرستِ «حساب من» به ترتیبِ r4. ویرایش و خروج فقط برای حسابِ واردشده؛
   *  «تنظیمات» فقط برای کسی که حقِ بازکردنش را دارد (همان `ShopRole.canOpenSettings`).
   *  «دستگاه‌ها» همان مقصدِ امروز: دستگاه‌های جانبی (`devices`).
   */
  fun menu(signedIn: Boolean, canSettings: Boolean): List<MenuItem> = buildList {
    if (signedIn) add(MenuItem(Item.EDIT, StrAccount.menuEdit, AccountPaths.EDIT))
    add(MenuItem(Item.DEVICES, StrAccount.menuDevices, AccountLinks.DEVICES))
    add(MenuItem(Item.PLAN, StrAccount.menuPlan, AccountPaths.SUBSCRIPTION))
    add(MenuItem(Item.STATS, StrAccount.menuStats, AccountLinks.STATS))
    if (canSettings) add(MenuItem(Item.SETTINGS, Str.settings, AccountLinks.SETTINGS))
    add(MenuItem(Item.SUPPORT, Str.support, AccountPaths.SUPPORT))
    if (signedIn) add(MenuItem(Item.SIGN_OUT, StrAccount.menuSignOut, AccountPaths.SIGN_OUT, danger = true))
  }

  /** یکی از سه کادرِ آمار — هر کدام درِ بخشِ خودش */
  data class Door(val label: String, val count: Int, val route: String)

  fun doors(sales: Int, products: Int, debtors: Int): List<Door> = listOf(
    Door(StrAccount.statInvoices, sales, AccountLinks.SALES),
    Door(StrAccount.statItems, products, AccountLinks.PRODUCTS),
    Door(StrAccount.statDebtors, debtors, AccountLinks.DEBTORS),
  )
}
