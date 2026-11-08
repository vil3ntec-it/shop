package ir.vil3ntec.tohid.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/*
 *  کارمندان، مجوزها و دستگاه‌ها — پاسخ‌های `GET /api/shop/permissions` و
 *  `GET /api/shop/devices` (STF-01..03، STF-10).
 */

@Serializable
data class PermStateDto(
  val mode: String = "deny",
  val caps: Map<String, Double> = emptyMap(),
)

@Serializable
data class MemberPermsDto(
  val id: String = "",
  val userId: String = "",
  val name: String = "",
  val role: String = "staff",
  val status: String = "active",
  /** لایهٔ همین کاربر، همان‌طور که ذخیره شده */
  val layer: JsonObject = JsonObject(emptyMap()),
  val effective: Map<String, PermStateDto> = emptyMap(),
  /** STF-01: نقشِ سفارشیِ مؤثر (فقط با پرچمِ `shop.custom_roles`) */
  val customRole: CustomRoleRefDto? = null,
)

@Serializable
data class CustomRoleRefDto(val id: String = "", val name: String = "")

/** STF-01 — نقشِ سفارشی: نام + نقشِ پایه + لایهٔ مجوز (قالبِ `PermCore`)؛ `members` = شناسهٔ کاربرها */
@Serializable
data class CustomRoleDto(
  val id: String = "",
  val name: String = "",
  val baseRole: String = "staff",
  val perms: JsonObject = JsonObject(emptyMap()),
  val members: List<String> = emptyList(),
)

@Serializable
data class CatalogEntryDto(
  val key: String = "",
  /** none | restrict | full — آنچه سرور امروز می‌پذیرد */
  val edit: String = "none",
  val display: Boolean = false,
  val defaults: Map<String, String> = emptyMap(),
  /** «نیازمند تأیید» برایش معنا دارد (برگهٔ امضاشده، STF-02) */
  val approvable: Boolean = false,
  /** سقف‌های قابلِ تنظیم: maxPct، maxAmount (Minor) — STF-03 */
  val caps: List<String> = emptyList(),
)

@Serializable
data class ShopPermsDto(
  val v: Int = 1,
  val catalog: List<CatalogEntryDto> = emptyList(),
  val members: List<MemberPermsDto> = emptyList(),
  /** STF-01: `null` ⇐ پرچمِ `shop.custom_roles` خاموش (یا سرورِ قدیمی) — بخش دیده نمی‌شود */
  val customRoles: List<CustomRoleDto>? = null,
)

@Serializable
data class PermsPageDto(
  val role: String = "",
  /** فقط برای صاحب و مدیر (`members.view`) */
  val shop: ShopPermsDto? = null,
)

@Serializable
data class DeviceMemberDto(
  val userId: String = "",
  val role: String = "",
  val name: String = "",
)

@Serializable
data class ShopDeviceDto(
  val id: String = "",
  val name: String = "",
  val platform: String = "",
  val status: String = "active",
  val lastSeenAt: Long? = null,
  val disabledAt: Long? = null,
  val blockedAttempts: Int = 0,
  val member: DeviceMemberDto = DeviceMemberDto(),
  /** دستگاهی که همین درخواست از آن آمده — از کار انداختنی نیست */
  val current: Boolean = false,
)

@Serializable
data class ShopDevicesDto(val devices: List<ShopDeviceDto> = emptyList())

/*
 *  دستگاه‌های شناختهٔ کارمندان — رازِ دستگاهِ ورود با کد (`GET /api/shop/staff-keys`، مهاجرتِ ۳۴۵). سرور شناسهٔ کاملِ
 *  دستگاه و خودِ راز را هرگز نمی‌دهد؛ `device` همان شناسهٔ کوتاه است («abcd…wxyz»).
 */
@Serializable
data class StaffKeyDto(
  val id: String = "",
  val userId: String = "",
  val memberId: String = "",
  val label: String = "",
  val platform: String = "",
  val device: String = "",
  val createdAt: Long = 0,
  val lastUsedAt: Long? = null,
)

/** «شاید همان <نام> است با دستگاهِ تازه» — عضوِ تازه‌ای که دستگاهش پیش‌تر مالِ عضوِ کهنه‌تر بوده (فقط نشانه، هرگز یکی شدن) */
@Serializable
data class StaffRejoinHintDto(
  val memberId: String = "",
  val userId: String = "",
  val oldMemberId: String = "",
  val oldUserId: String = "",
  val oldName: String = "",
  val sameName: Boolean = false,
  val device: String = "",
)

@Serializable
data class StaffKeysDto(
  val devices: List<StaffKeyDto> = emptyList(),
  val hints: List<StaffRejoinHintDto> = emptyList(),
  /** سرور می‌گوید این نشست «باطل کردن» را می‌تواند (members.manage) */
  val canRevoke: Boolean = false,
)

/*
 *  STF-06 — محدودهٔ مکانِ کارمندان (`/api/shop/staff-scope`، `core/staff/StaffScope`).
 *  `places`/`mine` ِ خالی یعنی «همهٔ مکان‌ها» (همان رفتارِ پیشین).
 */
@Serializable
data class StaffScopeConfigDto(
  val enabled: Boolean = false,
  val places: List<String> = emptyList(),
  /** محدودهٔ خودم */
  val mine: List<String> = emptyList(),
)

@Serializable
data class StaffScopeMemberDto(
  val id: String = "",
  val userId: String = "",
  val name: String = "",
  val role: String = "",
  val places: List<String> = emptyList(),
)

@Serializable
data class StaffScopeMembersDto(
  val places: List<String> = emptyList(),
  val members: List<StaffScopeMemberDto> = emptyList(),
) {
  fun of(id: String, userId: String): StaffScopeMemberDto? =
    members.firstOrNull { it.id == id } ?: members.firstOrNull { userId.isNotBlank() && it.userId == userId }
}
