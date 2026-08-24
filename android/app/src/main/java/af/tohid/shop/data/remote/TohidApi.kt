package af.tohid.shop.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface TohidApi {
    @POST("api/v1/auth/register") suspend fun register(@Body body: RegisterRequest): UserWrapper
    @POST("api/v1/auth/login") suspend fun login(@Body body: LoginRequest): LoginResponse
    @POST("api/v1/auth/refresh") suspend fun refresh(@Body body: RefreshRequest): RefreshResponse

    @GET("api/v1/shop/me") suspend fun shopMe(): ShopMeResponse
    @POST("api/v1/shop/create") suspend fun createShop(@Body body: CreateShopRequest): ShopWrapper
    @POST("api/v1/shop/join") suspend fun joinShop(@Body body: JoinShopRequest): ShopWrapper
    @POST("api/v1/shop/invite") suspend fun invite(@Body body: InviteRequest): InviteResponse

    @POST("api/v1/shop/sync/push") suspend fun push(@Body body: PushRequest): PushResponse
    @GET("api/v1/shop/sync/pull") suspend fun pull(
        @Query("since") since: Long,
        @Query("limit") limit: Int = 2000,
    ): PullResponse
}

@kotlinx.serialization.Serializable data class UserWrapper(val user: UserDto)
@kotlinx.serialization.Serializable data class ShopWrapper(val shop: ShopDto? = null, val rev: Long = 0)
