package af.tohid.shop.data.remote

import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * تبدیل خطای شبکه به پیامی که کاربر بفهمد.
 *
 * پیام خام سرور یا Stack Trace هرگز به کاربر نشان داده نمی‌شود.
 */
object ApiErrors {

    /** کد خطای سرور، اگر آمده باشد (مثلاً subscription_expired). */
    fun codeOf(error: Throwable): String = when (error) {
        is HttpException -> parse(error)?.code.orEmpty()
        else -> ""
    }

    fun message(error: Throwable): String = when (error) {
        is UnknownHostException, is SocketTimeoutException ->
            "ارتباط با سرور برقرار نشد. اطلاعات شما در صف همگام‌سازی قرار گرفت."
        is IOException ->
            "ارتباط با سرور برقرار نشد. اطلاعات شما در صف همگام‌سازی قرار گرفت."
        is HttpException -> httpMessage(error)
        else -> error.message?.takeIf { it.isNotBlank() } ?: "خطای ناشناخته"
    }

    private fun httpMessage(error: HttpException): String {
        val detail = parse(error)
        if (detail != null && detail.message.isNotBlank()) return detail.message
        return when (error.code()) {
            401 -> "نشست شما تمام شده است. دوباره وارد شوید."
            403 -> "این کار در حد دسترسی شما نیست."
            404 -> "این مورد پیدا نشد."
            409 -> "این مورد از قبل ثبت شده است."
            429 -> "درخواست‌ها زیاد بود. کمی بعد دوباره امتحان کنید."
            in 500..599 -> "سرور در دسترس نیست. کمی بعد دوباره امتحان کنید."
            else -> "درخواست انجام نشد."
        }
    }

    private fun parse(error: HttpException): ApiErrorDetail? = runCatching {
        val raw = error.response()?.errorBody()?.string().orEmpty()
        if (raw.isBlank()) return null
        ApiClient.json.decodeFromString(ApiErrorBody.serializer(), raw).error
    }.getOrNull()
}
