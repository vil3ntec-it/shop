package ir.vil3ntec.tohid.sync

import ir.vil3ntec.tohid.core.config.AppConfig

/**
 *  نشانیِ سرور — نگهدارِ نامِ قدیمی.
 *
 *  منطق به `core.config.AppConfig` رفت تا پیکربندی یک جا باشد و بشود
 *  بدونِ اندروید سنجیدش. این شیء فقط برای صفحه‌هایی مانده که هنوز با
 *  همین نام صدایش می‌زنند؛ مقدارها همان‌هاست.
 *
 *  قاعده عوض نشده: چیزی که هنگامِ ساخت داخلِ برنامه می‌نشیند باید
 *  **دامنهٔ عمومی** باشد، نه IP و پورتِ واقعیِ سرور. سرور روزی از خانه به
 *  یک VPS می‌رود و برنامه نباید بفهمد.
 */
object ApiBase {

  /** نشانیِ ساخت — اگر خالی باشد یعنی این نسخه به سروری بسته نشده */
  val fixed: String get() = AppConfig.buildTimeBaseUrl

  /** آیا نشانی در خودِ برنامه نشسته است */
  val locked: Boolean get() = AppConfig.isLocked
}
