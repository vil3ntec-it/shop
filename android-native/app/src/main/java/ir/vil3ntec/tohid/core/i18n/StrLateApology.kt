package ir.vil3ntec.tohid.core.i18n

/**
 *  NTF-10 — بخشِ «پوزش و کوپنِ خودکارِ تأخیر» در تنظیمات (`ui/screens/LateApologySettings.kt`، نسخهٔ ۳:
 *  `ui/v3/settings/sections/LateApologyScreen.kt`؛ منطق در `core/orders/LateApology`).
 */
@Suppress("unused")
object StrLateApology {

  val title: String get() = t("پوزش و کوپنِ خودکارِ تأخیر", "Automatic apology and coupon for delays", "د ځنډ لپاره اتومات بښنه او کوپون")
  fun on(minutes: String): String = t("روشن — پس از $minutes دقیقه تأخیر", "On — after $minutes minutes late", "فعال — له $minutes دقیقو ځنډ وروسته")
  val off: String get() = t("خاموش", "Off", "بند")
  val switchOn: String get() = t("روشن", "On", "فعال")
  val note: String get() = t(
    "اگر سفارشِ ویترین بیش از آستانه از زمانِ آماده‌شدن (وگرنه زمان‌بندی یا ثبت) بگذرد، یک بار پیامِ پوزش و — اگر بخواهید — " +
      "کوپنی یک‌بارمصرف برای همان مشتری می‌رود. تأخیری که کارِ خودِ مشتری است پوزش ندارد؛ پیام از همان صفِ پیامِ مشتری می‌رود.",
    "If a storefront order passes the threshold after its ready time (or schedule, or placement), the customer gets one apology " +
      "and, if you want, a single-use coupon. Delays caused by the customer get no apology; the message uses the customer message queue.",
    "که د ویټرین فرمایش له چمتو کېدو (یا مهالوېش، یا ثبت) وروسته له حد څخه واوړي، یو ځل د بښنې پیغام او — که وغواړئ — " +
      "یو ځل کارېدونکی کوپون همغه پېرودونکي ته ځي. هغه ځنډ چې د پېرودونکي کار وي بښنه نه لري؛ پیغام د پېرودونکي د پیغام له لیکې ځي.",
  )
  val minutes: String get() = t("آستانهٔ تأخیر (دقیقه، ۵ تا ۱۴۴۰)", "Delay threshold (minutes, 5 to 1440)", "د ځنډ حد (دقیقې، ۵ تر ۱۴۴۰)")
  val coupon: String get() = t("کوپن", "Coupon", "کوپون")
  val kindNone: String get() = t("بی کوپن", "No coupon", "بې کوپونه")
  val kindAmount: String get() = t("مبلغ", "Amount", "مبلغ")
  val kindPercent: String get() = t("درصد", "Percent", "سلنه")
  val amount: String get() = t("مبلغِ کوپن (افغانی)", "Coupon amount (afghani)", "د کوپون مبلغ (افغانۍ)")
  val percent: String get() = t("درصدِ کوپن (تا ۵۰)", "Coupon percent (up to 50)", "د کوپون سلنه (تر ۵۰)")
  val days: String get() = t("مهلتِ کوپن (روز، ۱ تا ۹۰)", "Coupon validity (days, 1 to 90)", "د کوپون موده (ورځې، ۱ تر ۹۰)")
  val badMinutes: String get() = t("عددی از ۵ تا ۱۴۴۰", "A number from 5 to 1440", "له ۵ تر ۱۴۴۰ یو شمېر")
  val badAmount: String get() = t("مبلغی بیش از صفر", "An amount above zero", "له صفر څخه زیات مبلغ")
  val badPercent: String get() = t("درصدی بیش از صفر و تا ۵۰ (حداکثر دو رقمِ اعشار)", "A percent above zero and up to 50 (two decimals at most)", "له صفر زیاته او تر ۵۰ سلنه (تر دوو اعشاري رقمونو)")
  val badDays: String get() = t("عددی از ۱ تا ۹۰", "A number from 1 to 90", "له ۱ تر ۹۰ یو شمېر")
  val fixFields: String get() = t("چند کادر درست نیست؛ پیامِ زیرِ هر کادر را ببینید", "Some fields are not valid; see the message under each one", "ځینې خانې سمې نه دي؛ د هرې خانې لاندې پیغام وګورئ")
  val save: String get() = t("ذخیره", "Save", "خوندي کول")
  val saved: String get() = t("ذخیره شد", "Saved", "خوندي شو")
  val conflict: String get() = t("تنظیم در این میان عوض شده؛ دوباره باز کنید", "The setting changed meanwhile; open it again", "تنظیم په دې منځ کې بدل شوی؛ بیا یې پرانیزئ")
  val readOnly: String get() = t("فقط صاحب و مدیرِ دکان این تنظیم را عوض می‌کنند.", "Only the shop owner and managers can change this setting.", "یوازې د دوکان خاوند او مدیران دا تنظیم بدلولی شي.")
  val recent: String get() = t("پوزش‌های اخیر", "Recent apologies", "وروستۍ بښنې")
  fun recentLine(orderNo: String, minutes: String, at: String): String =
    t("سفارش #$orderNo · $minutes دقیقه دیر · $at", "Order #$orderNo · $minutes min late · $at", "فرمایش #$orderNo · $minutes دقیقې ځنډ · $at")
  val skipped: String get() = t("تأخیرِ کارِ مشتری — بی پیام", "Customer-caused delay — no message", "د پېرودونکي له امله ځنډ — بې پیغامه")
  fun couponLast(last4: String): String = t("کوپنِ …$last4", "Coupon …$last4", "کوپون …$last4")
  val redeemed: String get() = t("به کار رفت", "used", "وکارول شو")
  val queued: String get() = t("در صفِ پیام", "queued", "په لیکه کې")
}
