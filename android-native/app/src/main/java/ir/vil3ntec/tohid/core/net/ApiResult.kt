package ir.vil3ntec.tohid.core.net

/**
 *  نتیجهٔ یک درخواست — موفق یا ناموفق، بدونِ استثنا.
 *
 *  مخزن‌ها هم این را می‌دهند و هم شکلِ پرتاب‌کننده را (`…OrThrow`). دلیلش
 *  این است که صفحه‌ها امروز با `runCatching` نوشته شده‌اند و یک‌شبه عوض
 *  کردنِ همهٔ آن‌ها یعنی دست بردن در کدی که کار می‌کند، بی‌آنکه چیزی
 *  بهتر شود. صفحهٔ تازه از این استفاده می‌کند، صفحهٔ قدیمی سرِ جایش
 *  می‌ماند و کم‌کم می‌آید.
 */
sealed class ApiResult<out T> {

  data class Success<T>(val value: T) : ApiResult<T>()

  data class Failure(val error: ApiFailure) : ApiResult<Nothing>() {
    val message: String get() = error.userMessage
  }

  val isSuccess: Boolean get() = this is Success

  fun valueOrNull(): T? = (this as? Success)?.value

  /** پیامِ خطا برای نمایش — یا `null` اگر موفق بوده */
  fun errorMessage(): String? = (this as? Failure)?.message

  inline fun <R> map(transform: (T) -> R): ApiResult<R> = when (this) {
    is Success -> Success(transform(value))
    is Failure -> this
  }

  inline fun onSuccess(action: (T) -> Unit): ApiResult<T> {
    if (this is Success) action(value)
    return this
  }

  inline fun onFailure(action: (ApiFailure) -> Unit): ApiResult<T> {
    if (this is Failure) action(error)
    return this
  }

  companion object {
    /**
     *  هر استثنایی را به یک `Failure` معنادار تبدیل می‌کند — جز لغوِ
     *  کوروتین، که باید بالا برود تا بسته شدنِ صفحه «خطای شبکه» حساب نشود.
     */
    inline fun <T> of(block: () -> T): ApiResult<T> =
      try {
        Success(block())
      } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
      } catch (error: Throwable) {
        Failure(ApiFailure.fromException(error))
      }
  }
}
