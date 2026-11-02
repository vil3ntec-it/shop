package ir.vil3ntec.tohid.scan

import android.content.Context
import ir.vil3ntec.tohid.core.hw.DeviceRoster
import ir.vil3ntec.tohid.hw.DeviceLinks

/**
 *  اسکن وقتی پنجره بسته است (کامپیوتر) — همان API ِ `scan/ScanServiceAndroid.kt`.
 *
 *  کامپیوتر سرویسِ پیش‌رو ندارد و لازم هم ندارد: بستنِ پنجره برنامه را نمی‌بندد و در سینیِ سیستم می‌ماند (`Main.kt`)؛
 *  پس کارگرهای «دستگاه‌ها» (درگاهِ COM ِ بارکدخوانِ SPP یا USB-CDC، از راهِ jSerialComm مثلِ ترازوی HW-09) در همان
 *  فرایند می‌مانند و تا پنجره پنهان است هر اسکن به صفِ پایدار می‌رود (`ScanInbox.foreground` = پنجرهٔ پیدا).
 *  «با روشن شدنِ ویندوز» هنوز نیست (گامِ بعد).
 */
object ScanBackground {
  fun wanted(context: Context): Boolean = DeviceRoster.backgroundScanners(DeviceLinks.load(context)).isNotEmpty()

  fun sync(context: Context) {
    if (wanted(context)) DeviceLinks.syncWorkers(context) else DeviceLinks.stopWorkers()
  }
}
