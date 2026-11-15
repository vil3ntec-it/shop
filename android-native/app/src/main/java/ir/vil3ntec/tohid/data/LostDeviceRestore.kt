package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.i18n.StrLostDevice

/**
 *  S44 — گام‌های «بازیابی از پشتیبان» ِ دستگاهِ غیرفعال (خالص؛ آزمون: `LostDeviceRestoreTest`). صفحه
 *  (`LostDeviceReplaySheetAndroid`، گوشیِ نسخهٔ ۳ و کامپیوتر) فقط فایل می‌دهد و گام را نشان می‌دهد:
 *  انتخابِ فایل ⇐ خواندنِ صفِ نرفتهٔ پشتیبان (`LostDevicePendingAndroid.read`؛ رمزدار با عبارت ⇐ پرسیدنِ عبارت) ⇐ شمار ⇐
 *  فرستادن (`LostDevicePendingAndroid.replay`، همان `POST /shop/devices/:id/replay`) ⇐ «رسید / از پیش بود / پذیرفته نشد».
 *  ⛔ دکمه فقط برای دستگاهِ **غیرفعال**، فقط برای کسی که دستگاه‌ها را عوض می‌کند و فقط با پرچمِ روشن ([canReplay]).
 */
object LostDeviceRestore {

  sealed interface Step {
    data object Idle : Step
    data object Reading : Step
    /** پشتیبانِ رمزدار با عبارت که کلیدش این‌جا نیست؛ [wrong] = عبارتِ پیشین نادرست بود */
    data class Phrase(val wrong: Boolean) : Step
    data class Ready(val pending: LostDevicePending.Pending) : Step
    data class Sending(val pending: LostDevicePending.Pending) : Step
    data class Done(val counts: LostDevicePending.Counts) : Step
    data class Failed(val message: String) : Step
  }

  /** دکمه دیده شود؟ دستگاهِ غیرفعال (نه همین دستگاه)، اجازهٔ ویرایش و پرچمِ روشنِ سرور (`GET /shop/devices` ⇒ `replay`) */
  fun canReplay(disabled: Boolean, current: Boolean, canEdit: Boolean, replay: Boolean): Boolean = disabled && !current && canEdit && replay

  /** نتیجهٔ خواندنِ پشتیبان ⇐ گام: صفِ خالی یا پشتیبانِ بی صف ⇐ «کارِ نرفته‌ای نیست» */
  fun afterRead(pending: LostDevicePending.Pending?): Step =
    if (pending == null || pending.ops.isEmpty()) Step.Failed(StrLostDevice.none) else Step.Ready(pending)

  /** کدِ ردِ سرور (`lib/lost-device-replay.js`) ⇐ پیامِ روشن؛ کدِ دیگر ⇐ همان پیامِ سرور */
  fun failText(code: String?, fallback: String?): String = when (code) {
    "device_mismatch" -> StrLostDevice.errMismatch
    "device_not_disabled" -> StrLostDevice.errNotDisabled
    "lost_device_replay_off" -> StrLostDevice.errOff
    else -> fallback?.takeIf { it.isNotBlank() } ?: StrLostDevice.errRead
  }

  fun doneText(c: LostDevicePending.Counts): String = StrLostDevice.done(c.applied, c.duplicate, c.rejected)
}
