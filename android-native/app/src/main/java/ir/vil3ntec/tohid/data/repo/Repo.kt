package ir.vil3ntec.tohid.data.repo

import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.net.ApiResult
import kotlinx.coroutines.CancellationException

/**
 *  پوششِ مشترکِ مخزن‌ها.
 *
 *  هر استثنایی به یک `ApiFailure` معنادار تبدیل می‌شود، **جز** لغوِ
 *  کوروتین. آن یکی باید بالا برود: وقتی کاربر صفحه را می‌بندد، Compose
 *  کارِ نیمه‌کاره را لغو می‌کند و اگر آن لغو را «خطای شبکه» حساب کنیم،
 *  روی صفحه‌ای که دیگر وجود ندارد پیامِ خطا می‌نشیند و کارِ لغوشده تا
 *  آخر جلو می‌رود.
 */
internal inline fun <T> result(block: () -> T): ApiResult<T> =
  try {
    ApiResult.Success(block())
  } catch (cancelled: CancellationException) {
    throw cancelled
  } catch (error: Throwable) {
    ApiResult.Failure(ApiFailure.fromException(error))
  }
