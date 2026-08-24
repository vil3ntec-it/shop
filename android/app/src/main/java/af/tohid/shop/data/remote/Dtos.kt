package af.tohid.shop.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable data class LoginRequest(val identifier: String, val password: String)
@Serializable data class RegisterRequest(
    val name: String? = null, val email: String? = null,
    val phone: String? = null, val password: String,
)
@Serializable data class UserDto(
    val id: String, val name: String = "", val email: String? = null, val phone: String? = null,
)
@Serializable data class LoginResponse(
    val user: UserDto,
    val accessToken: String, val accessExpiresAt: Long = 0,
    val refreshToken: String, val refreshExpiresAt: Long = 0,
)
@Serializable data class RefreshRequest(val refreshToken: String)
@Serializable data class RefreshResponse(val accessToken: String, val accessExpiresAt: Long = 0)

@Serializable data class ShopDto(
    val id: String, val name: String, val ownerId: String,
    val maxMembers: Int = 5, val myRole: String = "staff", val createdAt: Long = 0,
)
@Serializable data class MemberDto(
    val userId: String, val name: String = "", val email: String? = null, val phone: String? = null,
    val role: String = "staff", val joinedAt: Long = 0, val isMe: Boolean = false,
)
@Serializable data class ShopMeResponse(
    val shop: ShopDto? = null, val members: List<MemberDto> = emptyList(),
    val rev: Long = 0, val serverTime: Long = 0,
)
@Serializable data class CreateShopRequest(val name: String, val maxMembers: Int = 5)
@Serializable data class JoinShopRequest(val code: String)
@Serializable data class InviteRequest(val role: String = "staff")
@Serializable data class InviteResponse(val code: String, val expiresAt: Long = 0, val role: String = "staff")

/** یک رکورد در جریان همگام‌سازی. data محتوای خام همان رکورد است. */
@Serializable data class SyncChange(
    val collection: String,
    val id: String,
    val rev: Long = 0,
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
    val deviceId: String = "",
    val userId: String = "",
    val data: JsonElement? = null,
)
@Serializable data class SyncSettings(
    val data: JsonElement? = null, val updatedAt: Long = 0, val rev: Long = 0,
)
@Serializable data class PushRequest(
    val deviceId: String,
    val changes: List<SyncChange>,
    val settings: SyncSettings? = null,
)
@Serializable data class PushResponse(
    val applied: Int = 0, val skipped: Int = 0, val rev: Long = 0, val serverTime: Long = 0,
)
@Serializable data class PullResponse(
    val changes: List<SyncChange> = emptyList(),
    val rev: Long = 0,
    val hasMore: Boolean = false,
    @SerialName("shopRev") val shopRev: Long = 0,
    val settings: SyncSettings? = null,
    val serverTime: Long = 0,
)

@Serializable data class ApiErrorBody(val error: ApiErrorDetail? = null)
@Serializable data class ApiErrorDetail(val code: String = "", val message: String = "")
