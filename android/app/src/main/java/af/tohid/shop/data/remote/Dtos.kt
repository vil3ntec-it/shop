package af.tohid.shop.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/* ================================================================== */
/*  حساب کاربری                                                        */
/* ================================================================== */

/** اطلاعات دستگاه — سرور با این، نشست‌ها را از هم جدا می‌کند. */
@Serializable data class DeviceDto(
    val deviceId: String,
    val name: String = "",
    val platform: String = "android",
)

@Serializable data class RegisterRequest(
    val name: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val password: String,
    val device: DeviceDto? = null,
)

@Serializable data class LoginRequest(
    val identifier: String,
    val password: String,
    val device: DeviceDto? = null,
)

@Serializable data class OtpRequest(val phone: String)
@Serializable data class OtpVerifyRequest(
    val phone: String,
    val code: String,
    val name: String? = null,
    val device: DeviceDto? = null,
)
@Serializable data class GoogleRequest(val idToken: String, val device: DeviceDto? = null)
@Serializable data class RefreshRequest(val refreshToken: String)
@Serializable data class LogoutRequest(val refreshToken: String? = null)
@Serializable data class PasswordRequest(val currentPassword: String? = null, val newPassword: String)

@Serializable data class UserDto(
    val id: String,
    val name: String = "",
    val email: String? = null,
    val phone: String? = null,
    val createdAt: Long = 0,
    val hasPassword: Boolean = false,
)

/** دکان به شکل خلاصه — همان چیزی که همراه پاسخ ورود می‌آید. */
@Serializable data class ShopBriefDto(
    val id: String = "",
    val name: String = "",
    val role: String = "staff",
    val isOwner: Boolean = false,
)

@Serializable data class AuthResponse(
    val user: UserDto,
    val shop: ShopBriefDto? = null,
    val accessToken: String = "",
    val accessExpiresAt: Long = 0,
    val refreshToken: String = "",
    val refreshExpiresAt: Long = 0,
    val deviceId: String? = null,
    val created: Boolean = false,
)

@Serializable data class RefreshResponse(val accessToken: String, val accessExpiresAt: Long = 0)
@Serializable data class OtpResponse(
    val ok: Boolean = true,
    val phone: String = "",
    val sent: Boolean = false,
    val expiresAt: Long = 0,
    val resendAfter: Long = 0,
    /** فقط روی سرور آزمایشی پر می‌شود؛ در حالت واقعی همیشه خالی است. */
    val devCode: String? = null,
)
@Serializable data class OkResponse(val ok: Boolean = true)

/* ================================================================== */
/*  اشتراک و دسترسی                                                    */
/* ================================================================== */

@Serializable data class TrialDto(
    val enabled: Boolean = false,
    val active: Boolean = false,
    val used: Boolean = false,
    val startsAt: Long = 0,
    val endsAt: Long = 0,
    val daysLeft: Int = 0,
)

@Serializable data class SubscriptionStateDto(
    val id: String = "",
    val plan: String = "",
    val status: String = "none",          // none | active | suspended | expired | cancelled
    val active: Boolean = false,
    val startsAt: Long = 0,
    val endsAt: Long = 0,
    val daysLeft: Int = 0,
)

/** وضعیت دسترسی — همان چیزی که در برنامه کش می‌شود. */
@Serializable data class EntitlementDto(
    val source: String = "guest",         // guest | free | trial | subscription
    val features: List<String> = emptyList(),
    val trial: TrialDto = TrialDto(),
    val subscription: SubscriptionStateDto = SubscriptionStateDto(),
    val serverTime: Long = 0,
)

/** پاسخ /api/me/subscription */
@Serializable data class SubscriptionResponse(
    val shop: ShopBriefDto? = null,
    val status: String = "none",
    val active: Boolean = false,
    val source: String = "guest",
    val plan: String = "",
    val startsAt: Long = 0,
    val endsAt: Long = 0,
    val daysLeft: Int = 0,
    val trial: TrialDto = TrialDto(),
    val features: List<String> = emptyList(),
    val serverTime: Long = 0,
) {
    fun toEntitlement() = EntitlementDto(
        source = source,
        features = features,
        trial = trial,
        subscription = SubscriptionStateDto(
            plan = plan, status = status, active = active,
            startsAt = startsAt, endsAt = endsAt, daysLeft = daysLeft,
        ),
        serverTime = serverTime,
    )
}

@Serializable data class MeResponse(
    val user: UserDto,
    val shop: ShopBriefDto? = null,
    val permissions: List<String> = emptyList(),
    val entitlement: EntitlementDto? = null,
    val serverTime: Long = 0,
)

@Serializable data class PlanDto(
    val code: String = "",
    val title: String = "",
    val amount: Int? = null,
    val unit: String? = null,
    val price: Int = 0,
    val negotiable: Boolean = false,
    val badge: String = "",
    val days: Int = 0,
    // این دو را خود برنامه پر می‌کند
    val pricePerDay: Double? = null,
    val whatsappUrl: String = "",
)

@Serializable data class WhatsappDto(
    val number: String = "",
    val message: String = "",
    val url: String = "",
)

@Serializable data class PlansResponse(
    val plans: List<PlanDto> = emptyList(),
    val currency: String = "افغانی",
    val trialDays: Int = 14,
    val whatsapp: WhatsappDto = WhatsappDto(),
)

@Serializable data class PurchaseRequestBody(val plan: String, val note: String = "")

/* ================================================================== */
/*  دکان، اعضا و کد شاگرد                                              */
/* ================================================================== */

@Serializable data class ShopDto(
    val id: String,
    val name: String = "",
    val status: String = "active",
    val ownerUserId: String = "",
    val createdAt: Long = 0,
    val maxMembers: Int = 10,
)

@Serializable data class ShopResponse(
    val shop: ShopDto? = null,
    val role: String? = null,
    val isOwner: Boolean = false,
    val permissions: List<String> = emptyList(),
    val memberCount: Int = 0,
    val entitlement: EntitlementDto? = null,
    val serverTime: Long = 0,
)

@Serializable data class CreateShopRequest(val name: String)
@Serializable data class JoinShopRequest(val code: String)

@Serializable data class MemberDto(
    val id: String = "",
    val userId: String = "",
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val role: String = "staff",
    val status: String = "active",
    val joinedAt: Long = 0,
    val lastLoginAt: Long? = null,
)
@Serializable data class MembersResponse(
    val members: List<MemberDto> = emptyList(),
    val maxMembers: Int = 10,
)
@Serializable data class MemberPatch(val role: String? = null, val status: String? = null)

@Serializable data class StaffCodeRequest(
    val role: String = "staff",
    val maxUses: Int = 1,
    val expiresInDays: Int = 0,
)
@Serializable data class StaffCodeResponse(
    val code: String = "",
    val id: String = "",
    val role: String = "staff",
    val maxUses: Int = 1,
    val expiresAt: Long? = null,
)
@Serializable data class StaffCodeDto(
    val id: String = "",
    val hint: String = "",
    val role: String = "staff",
    val status: String = "active",
    val createdAt: Long = 0,
    val expiresAt: Long? = null,
    val maxUses: Int = 1,
    val usedCount: Int = 0,
)
@Serializable data class StaffCodesResponse(val codes: List<StaffCodeDto> = emptyList())

/* ================================================================== */
/*  همگام‌سازی                                                          */
/* ================================================================== */

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

@Serializable data class PushRequest(
    val deviceId: String,
    /** شناسه‌ی یکتای این عملیات — اگر درخواست دوباره برسد، دوباره ثبت نمی‌شود. */
    val operationId: String,
    val changes: List<SyncChange>,
)

@Serializable data class SyncConflictDto(
    val collection: String = "",
    val id: String = "",
    val reason: String = "",
    val serverUpdatedAt: Long = 0,
    val serverVersion: Int = 0,
)

@Serializable data class PushResponse(
    val applied: Int = 0,
    val skipped: Int = 0,
    val rev: Long = 0,
    val conflicts: List<SyncConflictDto> = emptyList(),
    val replayed: Boolean = false,
    val serverTime: Long = 0,
)

@Serializable data class PullResponse(
    val changes: List<SyncChange> = emptyList(),
    val rev: Long = 0,
    val hasMore: Boolean = false,
    val serverRev: Long = 0,
    val serverTime: Long = 0,
)

/* ================================================================== */
/*  عمومی                                                              */
/* ================================================================== */

/** تنظیمات عمومی سرور — برنامه با این می‌فهمد چه راه‌های ورودی باز است. */
@Serializable data class ServerConfigDto(
    val serverTime: Long = 0,
    val registrationOpen: Boolean = true,
    val googleClientId: String = "",
    val otpEnabled: Boolean = true,
    val trialDays: Int = 14,
    val whatsapp: WhatsappDto = WhatsappDto(),
    val minAppVersion: String = "",
)

@Serializable data class HealthResponse(
    val ok: Boolean = false,
    val server: String = "",
    val database: String = "",
    val version: String = "",
    val time: Long = 0,
)

@Serializable data class ApiErrorBody(val error: ApiErrorDetail? = null)
@Serializable data class ApiErrorDetail(val code: String = "", val message: String = "")
