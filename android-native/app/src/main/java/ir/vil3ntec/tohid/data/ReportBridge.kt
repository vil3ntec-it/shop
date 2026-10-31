package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.report.ReportClock
import ir.vil3ntec.tohid.core.report.ReportLedger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/**
 *  پلِ دفترِ برنامه (`ShopData`) به هستهٔ گزارش — RPT-01/02/03/06/09/11.
 *
 *  هسته ورودی‌اش همان شکلِ دفترِ وب است (`tohid-shop-data-v1`)؛ `ShopData` هم
 *  با همان نام‌ها ذخیره می‌شود، پس پل فقط یک بار آن را به JSON می‌برد — با
 *  `encodeDefaults` تا «نقد» و «تکمیل‌شده»ِ پیش‌فرض گم نشوند (همان تنظیمِ
 *  `ShopStore`). هیچ عددی این‌جا حساب نمی‌شود.
 *
 *  ⚠️ فیلدهایی که مدلِ امروزِ برنامه ندارد به هسته نمی‌رسند (تأییدکنندهٔ تخفیف،
 *  کاربرِ برگشتی و سابقه)؛ هسته آن‌ها را «نامعلوم» می‌شمارد. کانالِ فروش (`Sale.channel`)
 *  از #275 در مدل هست و عیناً می‌رسد. آزمونِ `ReportBridgeParityTest` همین را با عدد می‌پاید.
 */
object ReportBridge {
  private val json = Json { encodeDefaults = true }

  fun ledgerOf(d: ShopData): ReportLedger =
    ReportLedger.parse(json.encodeToJsonElement(ShopData.serializer(), d).jsonObject)

  /**
   *  ساعتِ همین دستگاه: اختلافِ امروزش با UTC و روزِ کاری از «پایانِ روزِ کاری»
   *  (`ShopDayChoice`، LOC-03؛ پیش‌فرض نیمه‌شب). روزِ هر فاکتور همان تاریخی است که
   *  موقعِ ثبت روی همین دستگاه دیده شد (`todayIso` با همان پایان)؛ فقط تفکیکِ
   *  «ساعت» از آن درمی‌آید (بی ساعت، همه «نامعلوم» می‌شدند).
   */
  fun deviceClock(
    now: Long = nowMs(),
    dayEndHours: Int = ir.vil3ntec.tohid.ShopDayChoice.hours,
  ): ReportClock =
    ReportClock.fixed(offsetMinutes(now), ir.vil3ntec.tohid.core.calendar.ShopDay.dayStartMinutes(dayEndHours))

  /** همین لحظه (میلی‌ثانیهٔ UTC) — برای X ِ شیفتِ باز در «پایانِ روز» */
  fun nowMs(): Long = System.currentTimeMillis()

  /** اختلافِ این دستگاه با UTC در همان لحظه (دقیقه) — همان عددِ [deviceClock] */
  fun offsetMinutes(now: Long = nowMs()): Int = java.util.TimeZone.getDefault().getOffset(now) / 60_000
}
