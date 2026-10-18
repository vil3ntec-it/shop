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
)

@Serializable
data class CatalogEntryDto(
  val key: String = "",
  /** none | restrict | full — آنچه سرور امروز می‌پذیرد */
  val edit: String = "none",
  val display: Boolean = false,
  val defaults: Map<String, String> = emptyMap(),
)

@Serializable
data class ShopPermsDto(
  val v: Int = 1,
  val catalog: List<CatalogEntryDto> = emptyList(),
  val members: List<MemberPermsDto> = emptyList(),
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
