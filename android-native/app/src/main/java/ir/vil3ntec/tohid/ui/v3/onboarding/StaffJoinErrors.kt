package ir.vil3ntec.tohid.ui.v3.onboarding

import ir.vil3ntec.tohid.core.i18n.StrAuth
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.net.userText

/**
 *  پیوستن با کدِ شاگرد: برای هر شکست پیامی که کارِ بعدی را می‌گوید (کدِ غلط، تمام‌شده، مصرف‌شده، دکانِ پر، دستگاهِ ناشناس،
 *  بی اینترنت، حسابِ دیگرِ واردشده). بقیه همان پیامِ فارسیِ سرور (`userText`) — مثلاً «اشتراکِ این دکان اجازه
 *  نمی‌دهد» یا «صاحبِ دکان دسترسی شما را بسته است».
 */
object StaffJoinErrors {

  fun message(failure: Throwable): String {
    if (failure is ApiFailure.Offline) return StrAuth.staffOffline
    return when ((failure as? ApiFailure)?.code) {
      "bad_code", "code_not_found" -> StrAuth.staffCodeWrong
      "code_expired" -> StrAuth.staffCodeExpired
      "code_used", "code_inactive" -> StrAuth.staffCodeSpent
      "shop_full" -> StrAuth.staffShopFull
      //  شناسهٔ این دستگاه به حسابی بسته است ولی رازِ دستگاه نیامد/نخورد (نصبِ تازه، داده پاک‌شده) — سرور ۲.۱۴.۵
      "staff_device_unverified" -> StrAuth.staffDeviceUnverified
      "already_owner", "already_member" -> StrAuth.staffSignedInElsewhere
      else -> failure.userText(StrAuth.joinFailed)
    }
  }
}
