package ir.vil3ntec.tohid.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 *  مدل‌های API — شکلِ چیزی که با سرور رد و بدل می‌شود.
 *
 *  تا دیروز هر پاسخ یک `JsonObject` خام بود که تا داخلِ صفحه‌ها می‌رفت و
 *  همان‌جا با رشته‌های کلید («`accessToken`»، «`maxUses`») باز می‌شد. دو
 *  عیب داشت: غلطِ املاییِ یک کلید تا زمانِ اجرا معلوم نمی‌شد، و صفحه
 *  مجبور بود شکلِ API را بداند.
 *
 *  هر میدان مقدارِ پیش‌فرض دارد. سرورِ کمی قدیمی‌تر که میدانی را
 *  نمی‌فرستد نباید صفحه را بشکند — همان چیزی که قابلیتِ «کدِ ثابتِ دکان»
 *  را روی سرورهای قدیمی زمین می‌زد.
 */

/* ------------------------------ حساب و نشست ------------------------------ */

@Serializable
data class UserDto(
  val id: String = "",
  val name: String = "",
  val email: String? = null,
  val phone: String? = null,
  val createdAt: Long = 0,
  val hasPassword: Boolean = false,
) {
  /** چیزی که در صفحه کنارِ نام نشان داده می‌شود */
  val contact: String get() = phone?.takeIf { it.isNotBlank() } ?: email?.takeIf { it.isNotBlank() } ?: ""
}

@Serializable
data class ShopRefDto(
  val id: String = "",
  val name: String = "",
  val role: String = "staff",
)

/**
 *  پاسخِ همهٔ راه‌های ورود — رمز، کد یک‌بارمصرف، گوگل، و رمزِ تازه.
 *  هر چهار مسیر دقیقاً همین شکل را برمی‌گردانند.
 */
@Serializable
data class SessionDto(
  val accessToken: String = "",
  val accessExpiresAt: Long = 0,
  val refreshToken: String? = null,
  val refreshExpiresAt: Long = 0,
  val deviceId: String? = null,
  val user: UserDto = UserDto(),
  val shop: ShopRefDto? = null,
  /** فقط در ورود با کد و گوگل می‌آید: حساب همین حالا ساخته شد یا نه */
  val created: Boolean = false,
) {
  val isValid: Boolean get() = accessToken.isNotBlank()
}

/* ------------------------------ کد یک‌بارمصرف ------------------------------ */

@Serializable
data class OtpChallengeDto(
  val sent: Boolean = false,
  val destination: String = "",
  val expiresAt: Long = 0,
  /**
   *  ثانیه تا اجازهٔ درخواستِ دوباره.
   *
   *  ثانیه می‌آید نه ساعتِ مطلق، چون ساعتِ گوشی ممکن است با سرور جور
   *  نباشد. سرورِ قدیمی که این را نمی‌دهد، دو دقیقه فرض می‌شود.
   */
  val resendSeconds: Int = 120,
  /** فقط در سرورِ آزمایشی و وقتی هیچ راهِ ارسالی تنظیم نشده */
  val devCode: String? = null,
)

/* ------------------------------ تنظیماتِ سرور ------------------------------ */

/**
 *  آنچه سرور دربارهٔ خودش می‌گوید.
 *
 *  با همین، روشن و خاموش کردنِ «ورود با گوگل» یا «ثبت‌نامِ باز» کارِ
 *  مدیرِ سرور است، نه دلیلی برای ساختنِ نسخهٔ تازهٔ برنامه.
 */
@Serializable
data class ServerConfigDto(
  val serverTime: Long = 0,
  val registrationOpen: Boolean = true,
  val googleClientId: String = "",
  val otpEnabled: Boolean = true,
  val trialDays: Int = 0,
  val minAppVersion: String = "",
)

/* ------------------------------ دکان ------------------------------ */

/**
 *  دکان — آن‌طور که سرور می‌شناسدش.
 *
 *  `role` و `permissions` هم از سرور می‌آید نه از حدسِ برنامه: اجازه را
 *  سرور می‌دهد، و پنهان کردنِ یک دکمه در گوشی هیچ‌وقت «کنترلِ دسترسی»
 *  نبوده.
 */
@Serializable
data class ShopDto(
  val id: String = "",
  val name: String = "",
  val status: String = "active",
  val ownerUserId: String = "",
  val createdAt: Long = 0,
  val maxMembers: Int = 0,
)

/** پاسخِ «دکانِ من» — دکان به‌همراه نقش و اجازه‌های همین کاربر */
@Serializable
data class ShopStateDto(
  val shop: ShopDto? = null,
  val role: String? = null,
  val isOwner: Boolean = false,
  val permissions: List<String> = emptyList(),
  val memberCount: Int = 0,
  val serverTime: Long = 0,
) {
  val hasShop: Boolean get() = shop != null && shop.id.isNotBlank()
}

@Serializable
data class MemberDto(
  val id: String = "",
  val userId: String = "",
  val name: String = "",
  val phone: String = "",
  val email: String = "",
  val role: String = "staff",
  val status: String = "active",
  val joinedAt: Long = 0,
  val lastLoginAt: Long? = null,
) {
  val contact: String get() = phone.ifBlank { email }
}

@Serializable
data class MembersPageDto(
  val members: List<MemberDto> = emptyList(),
  /** سقفِ اعضا بر اساسِ اشتراک؛ صفر یعنی سرور چیزی نگفته */
  val maxMembers: Int = 0,
)

@Serializable
data class StaffCodeDto(
  val id: String = "",
  /** نشانهٔ کد — متنِ کامل فقط یک بار، هنگامِ ساخت، برمی‌گردد */
  val hint: String = "",
  val role: String = "staff",
  val status: String = "active",
  val maxUses: Int = 0,
  val usedCount: Int = 0,
  val expiresAt: Long? = null,
)

@Serializable
data class StaffCodesDto(val codes: List<StaffCodeDto> = emptyList())

/**
 *  پاسخِ ساخت یا عوض کردنِ کد — تنها جایی که متنِ کد دیده می‌شود.
 *  بعد از این، فقط `hint` می‌ماند.
 */
@Serializable
data class IssuedCodeDto(
  val code: String = "",
  val id: String = "",
  val role: String = "staff",
  val maxUses: Int = 0,
  val expiresAt: Long? = null,
  /** کدِ ثابتِ دکان است یا یک کدِ یک‌بارمصرف */
  val standing: Boolean = false,
)

/* ------------------------------ دستگاه‌ها ------------------------------ */

/**
 *  یک دستگاهِ واردشده به این حساب.
 *
 *  ── چرا نام‌ها `@SerialName` دارند ────────────────────────────────
 *  مسیر `/me/devices` سطرِ خامِ دیتابیس را برمی‌گرداند، پس نامِ ستون‌ها
 *  با `_` است نه camelCase — برخلافِ بقیهٔ مسیرها که پاسخ را می‌سازند.
 *
 *  صفحهٔ تنظیمات دنبالِ `uid` و `deviceUid` می‌گشت و هیچ‌کدام وجود
 *  نداشت. نتیجه‌اش این بود که «همین گوشی» هیچ‌وقت شناخته نمی‌شد و
 *  دکمهٔ «ببند» کنارِ نشستِ خودِ کاربر هم می‌آمد؛ کسی که آن را می‌زد،
 *  خودش را بیرون می‌کرد.
 *  ──────────────────────────────────────────────────────────────────
 */
@Serializable
data class DeviceDto(
  val id: String = "",
  @SerialName("device_uid") val deviceUid: String = "",
  val name: String = "",
  val platform: String = "",
  val status: String = "active",
  @SerialName("created_at") val createdAt: Long = 0,
  @SerialName("last_seen_at") val lastSeenAt: Long? = null,
) {
  val label: String get() = name.ifBlank { platform.ifBlank { "دستگاه" } }
}

@Serializable
data class DevicesDto(val devices: List<DeviceDto> = emptyList())

/* ------------------------------ اشتراک ------------------------------ */

@Serializable
data class PlanDto(
  val code: String = "",
  val name: String = "",
  val price: Long = 0,
  val months: Int = 0,
  val features: List<String> = emptyList(),
)

@Serializable
data class PlansDto(
  val plans: List<PlanDto> = emptyList(),
  val currency: String = "افغانی",
  val trialDays: Int = 0,
)

@Serializable
data class SubscriptionDto(
  val plan: String? = null,
  val status: String = "none",
  val endsAt: Long? = null,
  val daysLeft: Int = 0,
  val trial: Boolean = false,
  val features: List<String> = emptyList(),
  val serverTime: Long = 0,
)

/* ------------------------------ صفحه‌بندی ------------------------------ */

/**
 *  شکلِ عمومیِ فهرست‌های صفحه‌بندی‌شده.
 *
 *  همگام‌سازی صفحه‌بندیِ خودش را دارد (`rev` و `hasMore`)، ولی فهرست‌های
 *  معمولیِ سرور با `limit`/`offset`/`total` می‌آیند. تا صفحه‌ای در برنامه
 *  به فهرستِ بلند نیاز پیدا کند، شکلش همین است — نه اینکه هر جا از نو
 *  اختراع شود.
 */
@Serializable
data class PageDto<T>(
  val items: List<T> = emptyList(),
  val total: Int = 0,
  val limit: Int = 0,
  val offset: Int = 0,
) {
  val hasMore: Boolean get() = offset + items.size < total
  val nextOffset: Int get() = offset + items.size
}
