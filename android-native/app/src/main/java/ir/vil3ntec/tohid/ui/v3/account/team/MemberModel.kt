package ir.vil3ntec.tohid.ui.v3.account.team

import ir.vil3ntec.tohid.core.i18n.StrAccount
import ir.vil3ntec.tohid.core.model.MemberDto
import ir.vil3ntec.tohid.core.model.MemberPermsDto
import ir.vil3ntec.tohid.core.model.ShopDeviceDto
import ir.vil3ntec.tohid.core.model.ShopPermsDto
import ir.vil3ntec.tohid.core.model.StaffKeyDto
import ir.vil3ntec.tohid.core.model.StaffKeysDto
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.v3.account.AccountFormat
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

  fun permLabel(key: String): String = CustomRoleModel.permLabel(key)

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

  /* ---------------- دستگاه‌های ورود با کد (رازِ دستگاه، `GET /api/shop/staff-keys`) ---------------- */

  /** رازهای زندهٔ همین عضو (با شناسهٔ کاربر، وگرنه عضویت) — تازه‌ترین بالا */
  fun keysOf(page: StaffKeysDto?, member: MemberDto): List<StaffKeyDto> =
    page?.devices.orEmpty()
      .filter { (member.userId.isNotBlank() && it.userId == member.userId) || (it.memberId.isNotBlank() && it.memberId == member.id) }
      .sortedByDescending { it.createdAt }

  data class KeyRow(val id: String, val title: String, val meta: String)

  /** برچسب (نامِ دستگاه، وگرنه سکو)، شناسهٔ کوتاه، آخرین استفاده و ساخت */
  fun keyRow(k: StaffKeyDto): KeyRow = KeyRow(
    id = k.id,
    title = k.label.trim().ifBlank { k.platform.ifBlank { StrAccount.device } },
    meta = StrAccount.deviceKeyMeta(
      k.device,
      k.lastUsedAt?.takeIf { it > 0 }?.let { AccountFormat.date(it) } ?: StrAccount.notUsedYet,
      AccountFormat.date(k.createdAt),
    ),
  )

  /** بخش دیده شود؟ سرورِ قدیمی یا بی اجازه ⇒ نه؛ صاحب دکان رازِ کد ندارد */
  fun keysVisible(page: StaffKeysDto?, member: MemberDto): Boolean =
    page != null && AccountFormat.roleOf(member.role) != AccountFormat.Role.OWNER

  /** «باطل کردن» و «خروج از همه» فقط وقتی سرور گفته این نشست می‌تواند (members.manage) — سرور باز هم می‌سنجد */
  fun canRevoke(page: StaffKeysDto?, member: MemberDto): Boolean = page?.canRevoke == true && keysVisible(page, member)

  data class StaleHint(val oldMemberId: String, val oldName: String, val title: String, val body: String)

  /**
   *  «شاید همان <نام> است با دستگاهِ تازه» روی عضوِ تازه — فقط وقتی عضوِ کهنه هنوز در فهرستِ اعضاست (برداشته‌شده دیگر نه).
   *  ⛔ فقط نشانه: هیچ چیز یکی نمی‌شود؛ تنها کار «برداشتنِ عضوِ کهنه» با تأیید است.
   */
  fun hintsOf(page: StaffKeysDto?, member: MemberDto, members: List<MemberDto>): List<StaleHint> =
    page?.hints.orEmpty()
      .filter { it.memberId == member.id || (it.userId.isNotBlank() && it.userId == member.userId) }
      .filter { h -> h.oldMemberId != member.id && members.any { it.id == h.oldMemberId && it.status != "removed" } }
      .distinctBy { it.oldMemberId }
      .map { h ->
        val name = h.oldName.trim().ifBlank { members.firstOrNull { it.id == h.oldMemberId }?.name?.trim().orEmpty() }
          .ifBlank { StrAccount.noName }
        StaleHint(
          oldMemberId = h.oldMemberId,
          oldName = name,
          title = StrAccount.staleHint(name),
          body = StrAccount.staleHintBody(h.device) + if (h.sameName) " " + StrAccount.staleSameName else "",
        )
      }
}
