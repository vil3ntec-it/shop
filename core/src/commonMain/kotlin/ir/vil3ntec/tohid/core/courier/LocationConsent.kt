package ir.vil3ntec.tohid.core.courier

/**
 *  S94 (§۲۱) — «بدونِ رضایت ردیابیِ لحظه‌ایِ پیک فعال نشود؛ لغوِ رضایت اثرِ بعدی را متوقف کند و دادهٔ قبلی پاک شود.»
 *
 *  رضایتِ خودِ پیک برای موقعیتِ زنده. سرور داور است (`server/src/lib/courier-location-consent.js`):
 *  - `GET /api/courier/me` ⇒ `locationConsent: {required, granted, grantedAt}`؛ [required] همان پرچمِ خاموشِ
 *    `shop.delivery_live_location.consent` ِ دکان — خاموش ⇒ رضایت خوانده نمی‌شود و رفتار همان پیشین (فقط پرچم‌های دکان).
 *  - `PUT /api/courier/location-consent` با `{granted}` فقط از خودِ پیک؛ «نه» همان دم نقطه‌های ذخیره‌شدهٔ مأموریت‌هایش را پاک
 *    می‌کند؛ همان حالِ پیشین ⇒ بی‌اثر.
 *  - پرچمِ روشن و بی «بله» ⇒ سرور هر نقطه را ۴۰۳ ِ [REQUIRED_CODE] می‌دهد.
 *
 *  این‌جا فقط این‌که گوشی **کی** بفرستد و کلید **کی** دیده شود؛ بی سکو تا روی JVM و آیفون آزموده شود
 *  (`LocationConsentTest`). سرویسِ موقعیت (`ui/courier/CourierLiveTrackAndroid.kt`) پیش از هر دور [mayShare] را می‌خواند.
 */
data class LocationConsent(val required: Boolean = false, val granted: Boolean = false, val grantedAt: Long? = null) {

  /** گوشی موقعیت بفرستد؟ پرچمِ خاموش ⇒ همان پیشین؛ روشن ⇒ فقط با «بله» ِ خودِ پیک */
  val mayShare: Boolean get() = !required || granted

  /** کلیدِ «اشتراکِ موقعیت» فقط وقتی سرور رضایت را می‌خواهد — کلیدی که اثری ندارد نشان داده نمی‌شود */
  val showToggle: Boolean get() = required

  /** پاسخِ `PUT` (`{granted, grantedAt}`) ⇒ حالِ تازه با همان پرچمِ پیشین؛ «نه» هیچ زمانی نگه نمی‌دارد */
  fun answered(granted: Boolean, grantedAt: Long?): LocationConsent =
    copy(granted = granted, grantedAt = if (granted) grantedAt else null)

  /** ۴۰۳ ِ [REQUIRED_CODE] (رضایت برداشته شد یا پرچم همین حالا روشن شد) ⇒ گوشی دیگر نفرستد تا «بله» ِ پیک */
  fun refusedByServer(): LocationConsent = LocationConsent(required = true, granted = false, grantedAt = null)

  companion object {
    /** کدِ ۴۰۳ ِ `POST /api/courier/missions/:id/location` بی رضایت (`courier-location-consent.assertIn`) */
    const val REQUIRED_CODE: String = "location_consent_required"
  }
}
