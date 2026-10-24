package ir.vil3ntec.tohid.ui.screens

import androidx.compose.runtime.Composable
import ir.vil3ntec.tohid.data.LockStore
import ir.vil3ntec.tohid.ui.v3.onboarding.LockGate
import ir.vil3ntec.tohid.ui.v3.onboarding.LockSetupGate

/*
 *  نام‌های پیشینِ دروازه‌های قفل — `AppRoot` هنوز این‌ها را صدا می‌زند. صفحه‌های تازه
 *  `ui/v3/onboarding/LockGate` و `LockSetupGate` است (G1)؛ فاز ۳ این فایل را برمی‌دارد.
 */

/** صفحهٔ قفل — پیش از هر چیزِ دیگر */
@Composable
fun AppLockScreen(onUnlocked: () -> Unit) = LockGate(onUnlocked)

/** گذاشتنِ رمز، یک بار پس از ساختنِ حساب («فعلاً نه» هم همان `onDone`) */
@Composable
fun LockSetupScreen(onDone: () -> Unit) = LockSetupGate(onDone)

/**
 *  طولِ رمزِ **تازه** (تنظیمات ⇐ رمزِ برنامه). صفحهٔ قفل این را نمی‌خواند و از
 *  `LockStore.length` می‌پرسد تا رمزِ چهاررقمیِ قدیم هم باز شود.
 */
const val MAX_PIN = LockStore.NEW_PIN_LEN
