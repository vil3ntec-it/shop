package ir.vil3ntec.tohid.ui.screens

import androidx.compose.runtime.Composable
import ir.vil3ntec.tohid.data.LockStore
import ir.vil3ntec.tohid.ui.v3.onboarding.LockGate
import ir.vil3ntec.tohid.ui.v3.onboarding.LockSetupGate
import ir.vil3ntec.tohid.ui.v3.onboarding.PinPad

/*
 *  نام‌های پیشینِ دروازه‌های قفل — `AppRoot` هنوز این‌ها را صدا می‌زند. صفحه‌های تازه
 *  `ui/v3/onboarding/LockGate` و `LockSetupGate` است (G1)؛ فاز ۳ این فایل را برمی‌دارد.
 */

/** صفحهٔ قفل — پیش از هر چیزِ دیگر */
@Composable
fun AppLockScreen(onUnlocked: () -> Unit) {
  //  پرچمِ نسخهٔ ۳ خاموش (پیش‌فرض) ⇒ همان رابطِ امروز (`ui/legacy`، `UiV3`)
  if (!ir.vil3ntec.tohid.ui.UiV3.on) { AppLockScreenLegacy(onUnlocked); return }
  LockGate(onUnlocked)
}

/** گذاشتنِ رمز، یک بار پس از ساختنِ حساب («فعلاً نه» هم همان `onDone`) */
@Composable
fun LockSetupScreen(onDone: () -> Unit) {
  if (!ir.vil3ntec.tohid.ui.UiV3.on) { LockSetupScreenLegacy(onDone); return }
  LockSetupGate(onDone)
}

/**
 *  صفحه‌کلیدِ رمزِ پیشین — «PINِ هر کاربر» در صندوق (`components/staff/PinPad`) هنوز این نام را
 *  صدا می‌زند؛ همان صفحه‌کلیدِ نسخهٔ ۳ ِ قفل (`ui/v3/onboarding/PinPad`، بی اثرِ انگشت) را می‌کشد.
 */
@Composable
internal fun Keypad(onDigit: (Char) -> Unit, onBack: () -> Unit) {
  if (!ir.vil3ntec.tohid.ui.UiV3.on) { KeypadLegacy(onDigit, onBack); return }
  PinPad(onDigit = onDigit, onErase = onBack, onFinger = null)
}

/**
 *  طولِ رمزِ **تازه** (تنظیمات ⇐ رمزِ برنامه). صفحهٔ قفل این را نمی‌خواند و از
 *  `LockStore.length` می‌پرسد تا رمزِ چهاررقمیِ قدیم هم باز شود.
 */
const val MAX_PIN = LockStore.NEW_PIN_LEN
