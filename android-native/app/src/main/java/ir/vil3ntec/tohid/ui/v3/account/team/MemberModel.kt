package ir.vil3ntec.tohid.ui.v3.account.team

import ir.vil3ntec.tohid.core.i18n.StrAccount
import ir.vil3ntec.tohid.core.model.MemberDto
import ir.vil3ntec.tohid.core.model.MemberPermsDto
import ir.vil3ntec.tohid.core.model.ShopDeviceDto
import ir.vil3ntec.tohid.core.model.ShopPermsDto
import ir.vil3ntec.tohid.ui.theme.V3Status
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 *  صفحهٔ هر کارمند (آزمون: `MemberModelTest`) — مجوز و دستگاه‌هایش. ⛔ هیچ چیزی
 *  این‌جا تصمیم گرفته نمی‌شود: کلیدهای قابل‌تغییر و حالتشان از سرور می‌آید
 *  (`GET /api/shop/permissions`)، همان قاعدهٔ `MemberPermsSection`ِ پیشین؛ پیک و
 *  صاحب دکان لایهٔ مجوز ندارند.
 */
object MemberModel {

  fun permLabel(key: String): String = when (key) {
    "settings.write" -> StrAccount.permSettings
    "audit.view" -> StrAccount.permAudit
    "reports.money" -> StrAccount.permMoney
    else -> key
  }

  data class PermRow(val key: String, val label: String, val on: Boolean)

  /** لایهٔ مجوزِ همین عضو (با شناسهٔ عضویت، وگرنه کاربر) */
  fun permsOf(page: ShopPermsDto?, member: MemberDto): MemberPermsDto? =
    page?.members?.firstOrNull { it.id == member.id } ?: page?.members?.firstOrNull { it.userId.isNotBlank() && it.userId == member.userId }

  /** کلیدهایی که برای این عضو معنا دارند — کلیدِ restrict که نقش اصلاً ندارد چیزی برای تغییر ندارد */
  fun permRows(page: ShopPermsDto?, m: MemberPermsDto?): List<PermRow> {
    if (page == null || m == null || m.role == "owner" || m.role == "courier") return emptyList()
    return page.catalog.filter { it.edit != "none" }.mapNotNull { entry ->
      val roleDefault = entry.defaults[m.role] ?: "deny"
      if (entry.edit == "restrict" && roleDefault != "allow") null
      else PermRow(entry.key, permLabel(entry.key), m.effective[entry.key]?.mode == "allow")
    }
  }

  /** لایهٔ فعلیِ عضو با یک کلیدِ عوض‌شده — بقیه دست نمی‌خورند */
  fun withMode(m: MemberPermsDto, key: String, on: Boolean): JsonObject = buildJsonObject {
    m.layer.forEach { (k, v) -> if (k != key) put(k, v) }
    put(key, buildJsonObject { put("mode", JsonPrimitive(if (on) "allow" else "deny")) })
  }

  fun devicesOf(all: List<ShopDeviceDto>?, member: MemberDto): List<ShopDeviceDto> =
    all.orEmpty().filter { it.member.userId.isNotBlank() && it.member.userId == member.userId }

  enum class DeviceState { CURRENT, BLOCKED, OUT, ACTIVE }

  fun stateOf(d: ShopDeviceDto): DeviceState = when {
    d.current -> DeviceState.CURRENT
    d.disabledAt != null -> DeviceState.BLOCKED
    d.status != "active" -> DeviceState.OUT
    else -> DeviceState.ACTIVE
  }

  /** وضعیت همیشه رنگ + برچسب (§۸) */
  fun pill(d: ShopDeviceDto): Pair<V3Status, String> = when (stateOf(d)) {
    DeviceState.CURRENT -> V3Status.PREPARING to StrAccount.deviceThis
    DeviceState.BLOCKED -> V3Status.CANCELLED to StrAccount.deviceBlocked +
      if (d.blockedAttempts > 0) " · " + StrAccount.blockedAttempts(d.blockedAttempts) else ""
    DeviceState.OUT -> V3Status.INACTIVE to StrAccount.deviceOut
    DeviceState.ACTIVE -> V3Status.DELIVERED to StrAccount.stateActive
  }

  /** کارِ صاحب دکان روی دستگاه: از کار انداختن (تأیید)، برگرداندن، یا هیچ (همین دستگاه/خارج‌شده) */
  enum class DeviceAction { BLOCK, UNBLOCK }

  fun actionOf(d: ShopDeviceDto, canEdit: Boolean): DeviceAction? = when {
    !canEdit -> null
    stateOf(d) == DeviceState.BLOCKED -> DeviceAction.UNBLOCK
    stateOf(d) == DeviceState.ACTIVE -> DeviceAction.BLOCK
    else -> null
  }

  fun deviceName(d: ShopDeviceDto): String = d.name.ifBlank { d.platform.ifBlank { StrAccount.device } }
}
