package ir.vil3ntec.tohid.ui.v3.account.team

import ir.vil3ntec.tohid.core.i18n.StrAccount

/**
 *  فرمِ «کد پیوستن تازه» (آزمون: `NewCodeModelTest`) — همان پیش‌فرض‌ها و قاعده‌ها:
 *  شاگرد، یک بار، هفت روز؛ «پیک» فقط وقتی سرور تحویل را برای این دکان روشن کرده
 *  و همیشه یک‌بارمصرف؛ عددِ ناخوانا ⇒ همان پیش‌فرضِ پیشین (بار ۱، روز ۰).
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

  fun body(role: String): String = if (role == COURIER) StrAccount.newCodeCourierBody else StrAccount.newCodeStaffBody

  /** پیک یک‌بارمصرف است — انتخابش «چند بار» را ۱ می‌کند */
  fun usesAfterPick(role: String, uses: String): String = if (role == COURIER) "1" else uses

  data class Request(val role: String, val maxUses: Int, val days: Int)

  fun request(role: String, uses: String, days: String): Request =
    Request(role, uses.trim().toIntOrNull() ?: 1, days.trim().toIntOrNull() ?: 0)
}
