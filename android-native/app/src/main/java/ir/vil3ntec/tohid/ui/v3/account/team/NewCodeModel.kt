package ir.vil3ntec.tohid.ui.v3.account.team

import ir.vil3ntec.tohid.core.i18n.StrAccount

/**
 *  فرمِ «کد پیوستن تازه» (آزمون: `NewCodeModelTest`) — همان پیش‌فرض‌ها و قاعده‌ها:
 *  شاگرد، یک بار، هفت روز؛ «پیک» فقط وقتی سرور تحویل را برای این دکان روشن کرده،
 *  همیشه یک‌بارمصرف و **با مهلتِ ۱ تا ۷ روز** (سرور #305 «۰ = همیشه» را با ۴۰۰ رد
 *  می‌کند؛ پیش‌فرضِ سرور ۷۲ ساعت) — پس برای پیک فقط همین مهلت‌ها پیشنهاد می‌شود.
 *  شاگرد/مدیر: عددِ ناخوانا ⇒ همان پیش‌فرضِ پیشین (بار ۱، روز ۰ = همیشه).
 */
object NewCodeModel {
  const val STAFF = "staff"
  const val MANAGER = "manager"
  const val COURIER = "courier"

  data class RoleOption(val role: String, val title: String, val detail: String)

  fun roles(courierAllowed: Boolean): List<RoleOption> = buildList {
    add(RoleOption(STAFF, StrAccount.roleStaff, StrAccount.roleStaffDetail))
    add(RoleOption(MANAGER, StrAccount.roleManager, StrAccount.roleManagerDetail))
    if (courierAllowed) add(RoleOption(COURIER, StrAccount.roleCourier, StrAccount.roleCourierDetail))
  }

  /** مهلت‌هایی که سرور برای دعوتِ پیک می‌پذیرد (`COURIER_INVITE.maxDays` = ۷) */
  val courierDays: List<Int> = listOf(1, 2, 3, 5, 7)
  const val COURIER_DEFAULT_DAYS = 3
  private const val COURIER_MAX_DAYS = 7

  fun body(role: String): String = if (role == COURIER) StrAccount.newCodeCourierBody else StrAccount.newCodeStaffBody

  /** پیک یک‌بارمصرف است — انتخابش «چند بار» را ۱ می‌کند */
  fun usesAfterPick(role: String, uses: String): String = if (role == COURIER) "1" else uses

  /** پیک «همیشه» ندارد: مهلتِ بیرون از ۱..۷ ⇒ همان پیش‌فرضِ سرور (۳ روز) */
  fun daysAfterPick(role: String, days: String): String =
    if (role == COURIER && days.trim().toIntOrNull() !in 1..COURIER_MAX_DAYS) COURIER_DEFAULT_DAYS.toString() else days

  data class Request(val role: String, val maxUses: Int, val days: Int)

  fun request(role: String, uses: String, days: String): Request =
    if (role == COURIER) Request(role, 1, days.trim().toIntOrNull()?.takeIf { it in 1..COURIER_MAX_DAYS } ?: COURIER_DEFAULT_DAYS)
    else Request(role, uses.trim().toIntOrNull() ?: 1, days.trim().toIntOrNull() ?: 0)
}
