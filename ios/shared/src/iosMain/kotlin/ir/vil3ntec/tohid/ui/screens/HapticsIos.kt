package ir.vil3ntec.tohid.ui.screens

import android.content.Context
import ir.vil3ntec.tohid.ios.IosHaptics

/*
 *  جایگزینِ iOS ِ `ui/screens/Haptics.kt` (همان API). همان سه معنای اندروید با بازخوردِ لمسیِ خودِ UIKit
 *  (`IosHaptics`، گامِ ۱۰ ِ docs/IOS-PLAN-fa.md §۰.۱۵): «ثبت شد» ⇐ success، «تأیید» ⇐ ضربهٔ متوسط، «خطا» ⇐ error.
 *  الگوی `Vibrator` ِ اندروید (سه ضرب، دو ضربِ پرفاصله) این‌جا نیست: راهنمای اپل همان مولدهای معنایی است و
 *  «لرزش‌های سیستم» ِ تنظیمات را خودشان رعایت می‌کنند.
 */
object Haptics {
  fun success(context: Context) = IosHaptics.play(IosHaptics.Event.SUCCESS)

  fun confirm(context: Context) = IosHaptics.play(IosHaptics.Event.CONFIRM)

  fun error(context: Context) = IosHaptics.play(IosHaptics.Event.ERROR)
}
