package af.tohid.shop.data.remote

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * مسیرهای سرور.
 *
 * همه زیر /api هستند. سرور همین مسیرها را زیر /api/v1 هم می‌شناسد،
 * پس نسخه‌های قدیمی برنامه هم از کار نمی‌افتند.
 */
interface TohidApi {

    // ---------- عمومی ----------
    @GET("api/health") suspend fun health(): HealthResponse
    @GET("api/config") suspend fun serverConfig(): ServerConfigDto

    // ---------- ورود ----------
    @POST("api/auth/register") suspend fun register(@Body body: RegisterRequest): AuthResponse
    @POST("api/auth/login") suspend fun login(@Body body: LoginRequest): AuthResponse
    @POST("api/auth/otp/request") suspend fun otpRequest(@Body body: OtpRequest): OtpResponse
    @POST("api/auth/otp/verify") suspend fun otpVerify(@Body body: OtpVerifyRequest): AuthResponse
    @POST("api/auth/google") suspend fun googleLogin(@Body body: GoogleRequest): AuthResponse
    @POST("api/auth/refresh") suspend fun refresh(@Body body: RefreshRequest): RefreshResponse
    @POST("api/auth/logout") suspend fun logout(@Body body: LogoutRequest): OkResponse
    @POST("api/auth/password") suspend fun setPassword(@Body body: PasswordRequest): OkResponse

    // ---------- حساب ----------
    @GET("api/me") suspend fun me(): MeResponse
    @GET("api/me/subscription") suspend fun subscription(): SubscriptionResponse
    @GET("api/me/plans") suspend fun plans(): PlansResponse
    @POST("api/me/purchase-request") suspend fun requestPurchase(@Body body: PurchaseRequestBody): OkResponse

    // ---------- دکان ----------
    @GET("api/shop") suspend fun shop(): ShopResponse
    @POST("api/shop") suspend fun createShop(@Body body: CreateShopRequest): ShopResponse
    @PUT("api/shop") suspend fun renameShop(@Body body: CreateShopRequest): ShopResponse

    @GET("api/shop/members") suspend fun members(): MembersResponse
    @PATCH("api/shop/members/{id}") suspend fun updateMember(
        @Path("id") id: String, @Body body: MemberPatch,
    ): OkResponse
    @DELETE("api/shop/members/{id}") suspend fun removeMember(@Path("id") id: String): OkResponse

    @GET("api/shop/staff-codes") suspend fun staffCodes(): StaffCodesResponse
    @POST("api/shop/staff-code") suspend fun createStaffCode(@Body body: StaffCodeRequest): StaffCodeResponse
    @DELETE("api/shop/staff-codes/{id}") suspend fun revokeStaffCode(@Path("id") id: String): OkResponse
    @POST("api/shop/staff/join") suspend fun joinShop(@Body body: JoinShopRequest): ShopResponse

    // ---------- همگام‌سازی ----------
    @POST("api/sync") suspend fun push(@Body body: PushRequest): PushResponse
    @GET("api/sync") suspend fun pull(
        @Query("since") since: Long,
        @Query("limit") limit: Int = 1000,
        @Query("deviceId") deviceId: String = "",
    ): PullResponse
}
