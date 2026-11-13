package ir.vil3ntec.tohid.ui.courier

import ir.vil3ntec.tohid.core.courier.MissionCard
import ir.vil3ntec.tohid.core.courier.MissionView
import ir.vil3ntec.tohid.core.i18n.StrCourierApp
import ir.vil3ntec.tohid.core.net.ApiResult

/*
 *  DLV-02 در نسخهٔ ۳ (کارتِ «مسیر» و جزئیاتِ مأموریت، گوشی و تبلت). این دو فقط در `CourierHomeLegacy.kt` بودند و
 *  دستهٔ ۱۳ (#855) آن را بی جانشین پاک کرد؛ `CourierCallV3Test` جلوی تکرارش را می‌گیرد. بی وابستگیِ سکو:
 *  باز کردنِ شماره‌گیر پشتِ مرزِ `CourierDialAndroid.kt` است (اندروید Intent، آیفون و کامپیوتر همان شیم‌ها).
 */

/** خطوطِ «اول نشانه»: نشانه، «ناحیه · دروازهٔ رنگ» به زبانِ صفحه، بعد نشانیِ متنی (`MissionView.landmarkFirst`) */
internal fun landmarkLines(c: MissionCard): List<String> =
  MissionView.landmarkFirst(c, StrCourierApp::areaLine, StrCourierApp::gateLine)

/**
 *  نمای جدولیِ تبلت (`MissionRow`): همان «اول نشانه» در دو خط — نشانه (بی نشانه، نشانیِ متنی) و «ناحیه · دروازه»؛
 *  نشانیِ متنیِ کنارِ نشانه فقط در جزئیات (ردیف جا ندارد). پیش از این ردیف فقط نشانه داشت.
 */
internal fun rowLines(c: MissionCard): List<String> =
  landmarkLines(if (c.landmark.isBlank()) c else c.copy(addressText = "")).take(2)

/**
 *  «تماس با مشتری»: شمارهٔ کامل فقط همین لحظه از سرور ([fetch] = `CourierRepository.callNumber`، POST و در سابقهٔ دکان؛
 *  سرور پرچم، پیکِ فعال، مالکیت و `CALLABLE` را می‌سنجد)، فقط شماره‌گیر باز می‌شود (تماس را خودِ پیک می‌زند) و شماره
 *  جایی نگه داشته نمی‌شود. ⇒ پیام برای نوارِ پیام، یا `null` وقتی شماره‌گیر باز شد. بی اینترنت ⇒ پیامِ روشن.
 */
internal suspend fun callCustomer(card: MissionCard, fetch: suspend (String) -> ApiResult<String>, dial: (String) -> Unit): String? {
  var text: String? = null
  fetch(card.id)
    .onSuccess { phone ->
      text = when {
        phone.isBlank() -> StrCourierApp.customerPhoneMissing
        runCatching { dial(phone) }.isFailure -> StrCourierApp.dialerFailed
        else -> null
      }
    }
    .onFailure { f -> text = courierFailureText(f, StrCourierApp.callOnlineOnly) }
  return text
}
