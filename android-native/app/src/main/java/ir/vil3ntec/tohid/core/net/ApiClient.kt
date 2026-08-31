package ir.vil3ntec.tohid.core.net

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 *  کارگزارِ API — تنها راهِ حرف زدنِ برنامه با سرور.
 *
 *  کارِ این لایه چیزی است که تا دیروز اصلاً انجام نمی‌شد:
 *
 *  ── نشستی که خودش تازه می‌شود ─────────────────────────────────────
 *  توکنِ دسترسی روی سرور **یک ساعت** عمر دارد و توکنِ تازه‌سازی نود روز.
 *  برنامه توکنِ تازه‌سازی را ذخیره می‌کرد ولی هیچ‌وقت از آن استفاده
 *  نمی‌کرد و مسیرِ `/auth/refresh` هرگز صدا زده نمی‌شد. یعنی یک ساعت
 *  بعد از ورود، همگام‌سازی بی‌صدا می‌مرد و اگر کاربر سراغش می‌رفت
 *  می‌خواند: «نام کاربری یا رمز عبور درست نیست» — که نه درست بود و نه
 *  کاری از دستش برمی‌آمد.
 *
 *  حالا هر ۴۰۱ روی یک درخواستِ توکن‌دار یعنی «توکن پیر شده»: یک بار
 *  تازه‌اش می‌کنیم و همان درخواست را دوباره می‌فرستیم. کاربر هیچ‌کدام
 *  را نمی‌بیند. فقط اگر تازه‌سازی هم نگرفت — توکنِ نود‌روزه هم مرده، یا
 *  مدیر حساب را بسته — تازه آن‌وقت «دوباره وارد شوید».
 *
 *  تازه‌سازی پشتِ یک قفل است: اگر همگام‌سازی و صفحهٔ اشتراک با هم به
 *  ۴۰۱ بخورند، یکی تازه می‌کند و آن یکی منتظرِ همان می‌ماند — نه دو
 *  درخواستِ موازی که هرکدام دیگری را باطل کند.
 *  ──────────────────────────────────────────────────────────────────
 */
class ApiClient(
  private val engine: HttpEngine,
  private val tokens: TokenStorage,
  /** وقتی نشست واقعاً از دست رفت — تا برنامه کاربر را به صفحهٔ ورود ببرد */
  private val onSessionLost: () -> Unit = {},
) {

  private val refreshLock = Mutex()

  /* ------------------------ درخواست‌های بی‌نیاز به ورود ------------------------ */

  suspend fun getPublic(path: String): JsonObject =
    engine.send("GET", path, token = null)

  suspend fun postPublic(path: String, body: JsonObject): JsonObject =
    engine.send("POST", path, body = body, token = null)

  /* ------------------------ درخواست‌های حساب‌دار ------------------------ */

  suspend fun get(path: String): JsonObject = authorized("GET", path, null, idempotent = true)

  suspend fun post(path: String, body: JsonObject = EMPTY): JsonObject =
    authorized("POST", path, body, idempotent = false)

  suspend fun put(path: String, body: JsonObject = EMPTY): JsonObject =
    authorized("PUT", path, body, idempotent = false)

  suspend fun delete(path: String): JsonObject = authorized("DELETE", path, null, idempotent = false)

  /**
   *  درخواست با توکن — و اگر توکن پیر بود، یک بار تازه‌سازی و تکرار.
   *
   *  «یک بار» عمدی است: اگر بعد از توکنِ تازه هم ۴۰۱ آمد، مشکل از پیریِ
   *  توکن نیست و تکرارِ دوباره فقط یک حلقه می‌سازد.
   */
  private suspend fun authorized(
    method: String,
    path: String,
    body: JsonObject?,
    idempotent: Boolean,
  ): JsonObject {
    val token = tokens.accessToken ?: throw ApiFailure.SessionExpired()

    try {
      return engine.send(method, path, body, token, idempotent)
    } catch (failure: ApiFailure) {
      if (failure !is ApiFailure.SessionExpired) throw failure

      val fresh = renew(usedToken = token)
      return engine.send(method, path, body, fresh, idempotent)
    }
  }

  /**
   *  گرفتنِ توکنِ دسترسیِ تازه.
   *
   *  @param usedToken توکنی که همین حالا ۴۰۱ گرفت. اگر توکنِ ذخیره‌شده
   *         با آن فرق دارد یعنی درخواستِ دیگری زودتر تازه‌اش کرده — پس
   *         بی‌خود دوباره تازه نمی‌کنیم و همان تازه را برمی‌داریم.
   */
  private suspend fun renew(usedToken: String): String = refreshLock.withLock {
    tokens.accessToken?.let { current -> if (current != usedToken) return@withLock current }

    val refresh = tokens.refreshToken ?: run {
      forgetSession()
      throw ApiFailure.SessionExpired()
    }

    val body = try {
      engine.send(
        "POST",
        ApiEndpoints.Auth.REFRESH,
        buildJsonObject { put("refreshToken", JsonPrimitive(refresh)) },
        token = null,
      )
    } catch (failure: ApiFailure) {
      //  «نت نیست» یعنی نشست هنوز سالم است و فقط الان نمی‌شود؛ حساب را
      //  به‌خاطرِ یک قطعیِ گذرا پاک نمی‌کنیم
      if (failure.retryable) throw failure
      forgetSession()
      throw ApiFailure.SessionExpired()
    }

    val access = (body["accessToken"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
      ?: run { forgetSession(); throw ApiFailure.SessionExpired() }

    val expiresAt = (body["accessExpiresAt"] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0
    tokens.save(access = access, refresh = null, expiresAt = expiresAt)
    access
  }

  private fun forgetSession() {
    tokens.clear()
    runCatching { onSessionLost() }
  }

  private companion object {
    val EMPTY = JsonObject(emptyMap())
  }
}
