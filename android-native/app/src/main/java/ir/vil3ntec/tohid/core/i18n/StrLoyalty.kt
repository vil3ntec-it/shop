package ir.vil3ntec.tohid.core.i18n

/**
 *  امتیازِ وفاداری و سطحِ مشتری (PRM-13) در صندوقِ گوشی و کامپیوتر (`LoyaltyBar`)، رسید، پروندهٔ مشتری (با اصلاحِ دستی)،
 *  تنظیماتِ همین دستگاه، انقضا و قاعدهٔ یکسانِ دکان. قاعده‌ها سرِ `I18n.kt`؛ منطق در `core/promo/Loyalty` (commonMain) و `data/LoyaltyPoints`.
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
  fun bar(points: Any, tier: Any, mult: Any): String = t("امتیاز: ${asIs(points)} · سطحِ ${asIs(tier)} (×${asIs(mult)})", "Points: $points · $tier tier (×$mult)", "امتیاز: $points · $tier کچه (×$mult)")
  val use: String get() = t("مصرفِ امتیاز", "Use points", "د امتیاز مصرف")
  fun using(points: Any, value: Any): String = t("مصرفِ ${asIs(points)} امتیاز = ${asIs(value)} ؋ کم شد", "Using $points points = $value ؋ off", "د $points امتیازونو مصرف = $value ؋ کم شول")
  val stopUsing: String get() = t("برداشتن", "Remove", "لرې کول")
  fun howMany(max: Any): String = t("چند امتیاز؟ (حداکثر ${asIs(max)})", "How many points? (max $max)", "څو امتیازه؟ (تر ټولو ډېر $max)")
  val apply: String get() = t("اعمال", "Apply", "پلي کول")
  val cancel: String get() = t("انصراف", "Cancel", "لغوه")
  val nothingToUse: String get() = t("برای این فاکتور امتیازِ قابلِ مصرف نیست", "No points can be used on this invoice", "په دې بل کې د مصرف وړ امتیاز نشته")
  val negative: String get() = t("ماندهٔ امتیاز منفی است — نیازمندِ بررسی", "Points balance is negative — needs review", "د امتیاز پاتې منفي دی — کتنې ته اړتیا لري")

  // رسید
  fun receiptUsed(points: Any): String = t("مصرفِ امتیاز (${asIs(points)})", "Points used ($points)", "د امتیاز مصرف ($points)")
  fun receiptEarned(points: Any): String = t("امتیازِ این خرید: +${asIs(points)}", "Points earned: +$points", "د دې پېرود امتیاز: +$points")

  // خطا
  val errCustomer: String get() = t("امتیاز مالِ مشتریِ دیگری است؛ مشتریِ فاکتور را عوض نکنید یا امتیاز را بردارید", "The points belong to another customer; keep the invoice customer or remove the points", "امتیاز د بل پېرودونکي دی؛ د بل پېرودونکی مه بدلوئ یا امتیاز لرې کړئ")
  val errChanged: String get() = t("سبد یا امتیاز همین حالا عوض شد؛ دوباره بزنید", "The cart or points just changed; try again", "ټوکرۍ یا امتیاز همدا اوس بدل شو؛ بیا هڅه وکړئ")
  val errBalance: String get() = t("امتیازِ مشتری کافی نیست", "The customer does not have enough points", "د پېرودونکي امتیاز بس نه دی")
  val errShare: String get() = t("امتیاز بیش از سهمِ مجاز از این فاکتور است", "Points exceed the allowed share of this invoice", "امتیاز د دې بل له اجازه لرونکې برخې ډېر دی")
  val errRule: String get() = t("قاعدهٔ امتیاز درست نیست؛ تنظیمات را ببینید", "The points rule is not valid; check settings", "د امتیاز قاعده سمه نه ده؛ تنظیمات وګورئ")

  // پروندهٔ مشتری
  fun historyTitle(balance: Any): String = t("امتیازِ وفاداری (مانده: ${asIs(balance)})", "Loyalty points (balance: $balance)", "د وفادارۍ امتیاز (پاتې: $balance)")
  val kindEarn: String get() = t("کسب", "Earned", "ګټل")
  val kindSpend: String get() = t("مصرف", "Used", "مصرف")
  val kindEarnBack: String get() = t("برگشتِ کسب", "Earn reversed", "د ګټلو بېرته اخیستل")
  val kindSpendBack: String get() = t("برگشتِ مصرف", "Use refunded", "د مصرف بېرته ورکول")
  fun invoice(label: Any): String = t("فاکتور #${asIs(label)}", "Invoice #$label", "بل #$label")
  val empty: String get() = t("هنوز امتیازی نیست", "No points yet", "لا امتیاز نشته")

  // انقضا
  val expiryTitle: String get() = t("انقضای امتیاز (ماه؛ ۰ = هرگز)", "Points expire after (months; 0 = never)", "د امتیاز پای ته رسېدل (میاشتې؛ ۰ = هېڅکله)")
  val expiryNote: String get() = t(
    "هر امتیاز این‌قدر ماه پس از کسب می‌سوزد؛ اول کهنه‌ترین امتیاز خرج می‌شود.",
    "Each point expires this many months after it was earned; the oldest points are used first.",
    "هر امتیاز د ګټلو څخه دومره میاشتې وروسته سوځي؛ لومړی تر ټولو زوړ امتیاز مصرفېږي.",
  )
  val expirySave: String get() = t("ذخیرهٔ انقضا", "Save expiry", "پای ته رسېدل خوندي کړئ")
  val badExpiry: String get() = t("ماه باید از ۰ تا ۱۲۰ باشد", "Months must be 0 to 120", "میاشتې باید له ۰ تر ۱۲۰ وي")
  fun soon(points: Any, date: Any): String = t("${asIs(points)} امتیاز تا ${asIs(date)} می‌سوزد", "$points points expire by $date", "$points امتیازونه تر $date پورې سوځي")

  // قاعدهٔ یکسانِ دکان
  val shopTitle: String get() = t("قاعدهٔ یکسان برای همهٔ دستگاه‌های دکان", "One rule for all shop devices", "د هټۍ ټولو وسیلو لپاره یوه قاعده")
  val shopInForce: String get() = t(
    "قاعدهٔ یکسانِ دکان در کار است؛ قاعده و روشن/خاموشِ همین دستگاه کنار می‌رود.",
    "The shop-wide rule is in force; this device's own rule and switch are set aside.",
    "د هټۍ یوه قاعده چلېږي؛ د همدې وسیلې خپله قاعده او بدلون یو طرف ته کېږي.",
  )
  val shopNotSaved: String get() = t("قاعدهٔ یکسانِ دکان هنوز ذخیره نشده است؛ هر دستگاه قاعدهٔ خودش را دارد.", "No shop-wide rule saved yet; each device uses its own.", "د هټۍ یوه قاعده لا نه ده خوندي شوې؛ هره وسیله خپله قاعده لري.")
  val shopOffline: String get() = t("بی‌اینترنت: آخرین قاعدهٔ دکان که همین دستگاه گرفته به کار می‌رود.", "Offline: the last shop rule this device received is used.", "بې انټرنېټه: د هټۍ وروستۍ قاعده چې همدې وسیلې اخیستې کارول کېږي.")
  val shopPublish: String get() = t("ذخیرهٔ قاعدهٔ همین دستگاه برای همهٔ دستگاه‌ها", "Save this device's rule for all devices", "د همدې وسیلې قاعده د ټولو وسیلو لپاره خوندي کړئ")
  val shopSaved: String get() = t("قاعدهٔ یکسانِ دکان ذخیره شد", "Shop-wide rule saved", "د هټۍ یوه قاعده خوندي شوه")
  val shopSaveFailed: String get() = t("قاعدهٔ دکان ذخیره نشد؛ اینترنت را ببینید و دوباره بزنید", "The shop rule was not saved; check the connection and try again", "د هټۍ قاعده خوندي نه شوه؛ انټرنېټ وګورئ او بیا هڅه وکړئ")
  fun ruleSummary(every: Any, point: Any, share: Any, months: Any): String = t(
    "هر ${asIs(every)} افغانی ۱ امتیاز · هر امتیاز ${asIs(point)} افغانی · تا ${asIs(share)}٪ فاکتور · انقضا ${asIs(months)} ماه",
    "1 point per $every AFN · 1 point = $point AFN · up to $share% of an invoice · expiry $months months",
    "هرو $every افغانیو ته ۱ امتیاز · هر امتیاز $point افغانۍ · د بل تر $share٪ پورې · پای ته رسېدل $months میاشتې",
  )
  fun shopVersion(v: Any): String = t("نسخهٔ ${asIs(v)}", "Version $v", "نسخه $v")

  // اصلاحِ دستی
  val adjust: String get() = t("اصلاحِ امتیاز", "Adjust points", "د امتیاز سمون")
  val adjustAdd: String get() = t("افزودن", "Add", "زیاتول")
  val adjustRemove: String get() = t("کم‌کردن", "Remove", "کمول")
  val adjustPoints: String get() = t("چند امتیاز؟", "How many points?", "څو امتیازه؟")
  val adjustReason: String get() = t("دلیل (لازم)", "Reason (required)", "دلیل (اړین)")
  val adjustDone: String get() = t("امتیاز اصلاح شد", "Points adjusted", "امتیاز سم شو")
  val adjustOwnerOnly: String get() = t("اصلاحِ امتیاز فقط با صاحب یا مدیر است", "Only the owner or a manager can adjust points", "امتیاز یوازې څښتن یا مدیر سمولای شي")
  val adjustNeedsReason: String get() = t("دلیلِ اصلاح را بنویسید", "Write a reason for the adjustment", "د سمون دلیل ولیکئ")
  fun adjustReasonLong(max: Any): String = t("دلیل حداکثر ${asIs(max)} حرف", "Reason is at most $max characters", "دلیل تر ټولو ډېر $max توري")
  val adjustBadPoints: String get() = t("شمارِ امتیاز درست نیست", "The number of points is not valid", "د امتیاز شمېر سم نه دی")
  val kindAdjust: String get() = t("اصلاحِ دستی", "Manual adjustment", "لاسي سمون")
}
