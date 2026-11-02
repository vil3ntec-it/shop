package ir.vil3ntec.tohid.core.i18n

/**
 *  امتیازِ وفاداری و سطحِ مشتری (PRM-13) در صندوقِ گوشی و کامپیوتر (`LoyaltyBar`)، رسید، پروندهٔ مشتری و تنظیماتِ
 *  همین دستگاه. قاعده‌ها سرِ `I18n.kt`؛ منطق در `core/promo/Loyalty` (commonMain) و `data/LoyaltyPoints`.
 */
@Suppress("unused")
object StrLoyalty {
  val settingsTitle: String get() = t("امتیازِ وفاداری و سطحِ مشتری", "Loyalty points and customer tiers", "د وفادارۍ امتیاز او د پېرودونکي کچه")
  val settingsNote: String get() = t(
    "مشتریِ انتخاب‌شده با هر خریدِ پرداخت‌شده امتیاز می‌گیرد (به نسبتِ سطحش در ۱۲ ماهِ گذشته) و در صندوق می‌تواند امتیاز را به‌جای بخشی از پول بدهد. برگشت و لغوِ فاکتور امتیازِ همان فاکتور را به همان نسبت برمی‌گرداند.",
    "The selected customer earns points on every paid purchase (scaled by their tier over the last 12 months) and can use points for part of an invoice at the register. Returns and voids reverse that invoice's points in the same proportion.",
    "ټاکل شوی پېرودونکی په هر ورکړل شوي پېرود امتیاز اخلي (د تېرو ۱۲ میاشتو د کچې سره سم) او په صندوق کې کولای شي امتیاز د بل د یوې برخې پر ځای ورکړي. بېرته ورکول او لغوه د همغه بل امتیاز په همغه تناسب بېرته اخلي.",
  )
  val ownerOnly: String get() = t("قاعده را فقط صاحب یا مدیر عوض می‌کند.", "Only the owner or a manager can change the rule.", "قاعده یوازې څښتن یا مدیر بدلولای شي.")
  val everyAfn: String get() = t("هر چند افغانیِ پرداخت‌شده = ۱ امتیاز", "AFN paid per 1 point", "څو افغانۍ ورکړه = ۱ امتیاز")
  val pointAfn: String get() = t("ارزشِ هر امتیاز هنگامِ مصرف (افغانی)", "Value of 1 point when used (AFN)", "د مصرف پر مهال د هر امتیاز ارزښت (افغانۍ)")
  val maxShare: String get() = t("بیشترین سهمِ امتیاز از فاکتور (درصد)", "Max share of an invoice paid with points (%)", "د بل تر ټولو لوړه برخه چې په امتیاز ورکول کېږي (سلنه)")
  val silverAfn: String get() = t("خریدِ ۱۲ ماهه برای سطحِ نقره‌ای (افغانی)", "12-month spend for Silver (AFN)", "د سپینو زرو کچې لپاره ۱۲ میاشتنی پېرود (افغانۍ)")
  val goldAfn: String get() = t("خریدِ ۱۲ ماهه برای سطحِ طلایی (افغانی)", "12-month spend for Gold (AFN)", "د سرو زرو کچې لپاره ۱۲ میاشتنی پېرود (افغانۍ)")
  val tiersNote: String get() = t("ضریبِ کسب: برنزی ×۱، نقره‌ای ×۱٫۲۵، طلایی ×۱٫۵", "Earn multiplier: Bronze ×1, Silver ×1.25, Gold ×1.5", "د ګټلو ضریب: برونزي ×۱، سپین زر ×۱٫۲۵، سره زر ×۱٫۵")
  val save: String get() = t("ذخیرهٔ قاعده", "Save rule", "قاعده خوندي کړئ")
  val saved: String get() = t("قاعدهٔ امتیاز ذخیره شد", "Points rule saved", "د امتیاز قاعده خوندي شوه")
  val badRule: String get() = t("عددها درست نیستند (طلایی باید بیش از نقره‌ای باشد)", "The numbers are not valid (Gold must be above Silver)", "شمېرې سمې نه دي (سره زر باید له سپینو زرو لوړ وي)")

  val tierGold: String get() = t("طلایی", "Gold", "سره زر")
  val tierSilver: String get() = t("نقره‌ای", "Silver", "سپین زر")
  val tierBronze: String get() = t("برنزی", "Bronze", "برونزي")

  // صندوق
  fun bar(points: Any, tier: Any, mult: Any): String = t("امتیاز: $points · سطحِ $tier (×$mult)", "Points: $points · $tier tier (×$mult)", "امتیاز: $points · $tier کچه (×$mult)")
  val use: String get() = t("مصرفِ امتیاز", "Use points", "د امتیاز مصرف")
  fun using(points: Any, value: Any): String = t("مصرفِ $points امتیاز = $value ؋ کم شد", "Using $points points = $value ؋ off", "د $points امتیازونو مصرف = $value ؋ کم شول")
  val stopUsing: String get() = t("برداشتن", "Remove", "لرې کول")
  fun howMany(max: Any): String = t("چند امتیاز؟ (حداکثر $max)", "How many points? (max $max)", "څو امتیازه؟ (تر ټولو ډېر $max)")
  val apply: String get() = t("اعمال", "Apply", "پلي کول")
  val cancel: String get() = t("انصراف", "Cancel", "لغوه")
  val nothingToUse: String get() = t("برای این فاکتور امتیازِ قابلِ مصرف نیست", "No points can be used on this invoice", "په دې بل کې د مصرف وړ امتیاز نشته")
  val negative: String get() = t("ماندهٔ امتیاز منفی است — نیازمندِ بررسی", "Points balance is negative — needs review", "د امتیاز پاتې منفي دی — کتنې ته اړتیا لري")

  // رسید
  fun receiptUsed(points: Any): String = t("مصرفِ امتیاز ($points)", "Points used ($points)", "د امتیاز مصرف ($points)")
  fun receiptEarned(points: Any): String = t("امتیازِ این خرید: +$points", "Points earned: +$points", "د دې پېرود امتیاز: +$points")

  // خطا
  val errCustomer: String get() = t("امتیاز مالِ مشتریِ دیگری است؛ مشتریِ فاکتور را عوض نکنید یا امتیاز را بردارید", "The points belong to another customer; keep the invoice customer or remove the points", "امتیاز د بل پېرودونکي دی؛ د بل پېرودونکی مه بدلوئ یا امتیاز لرې کړئ")
  val errChanged: String get() = t("سبد یا امتیاز همین حالا عوض شد؛ دوباره بزنید", "The cart or points just changed; try again", "ټوکرۍ یا امتیاز همدا اوس بدل شو؛ بیا هڅه وکړئ")
  val errBalance: String get() = t("امتیازِ مشتری کافی نیست", "The customer does not have enough points", "د پېرودونکي امتیاز بس نه دی")
  val errShare: String get() = t("امتیاز بیش از سهمِ مجاز از این فاکتور است", "Points exceed the allowed share of this invoice", "امتیاز د دې بل له اجازه لرونکې برخې ډېر دی")
  val errRule: String get() = t("قاعدهٔ امتیاز درست نیست؛ تنظیمات را ببینید", "The points rule is not valid; check settings", "د امتیاز قاعده سمه نه ده؛ تنظیمات وګورئ")

  // پروندهٔ مشتری
  fun historyTitle(balance: Any): String = t("امتیازِ وفاداری (مانده: $balance)", "Loyalty points (balance: $balance)", "د وفادارۍ امتیاز (پاتې: $balance)")
  val kindEarn: String get() = t("کسب", "Earned", "ګټل")
  val kindSpend: String get() = t("مصرف", "Used", "مصرف")
  val kindEarnBack: String get() = t("برگشتِ کسب", "Earn reversed", "د ګټلو بېرته اخیستل")
  val kindSpendBack: String get() = t("برگشتِ مصرف", "Use refunded", "د مصرف بېرته ورکول")
  fun invoice(label: Any): String = t("فاکتور #$label", "Invoice #$label", "بل #$label")
  val empty: String get() = t("هنوز امتیازی نیست", "No points yet", "لا امتیاز نشته")
}
