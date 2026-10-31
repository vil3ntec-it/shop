package ir.vil3ntec.tohid.core.i18n

/**
 *  قیمتِ ردیف در صندوق — تخفیفِ ردیف، «قاعدهٔ جهت» و سقفِ قلمِ آزاد (POS-08، POS-10، POS-20، POS-21).
 *  قاعده‌ها سرِ `I18n.kt`؛ ترجمه‌های نامطمئن در `docs/i18n-glossary.md`.
 */
@Suppress("unused")
object StrLine {

  // تنظیمات
  val section: String get() = t("قیمت و تخفیفِ ردیف", "Line prices and discounts", "د کرښې بیه او تخفیف")
  val sectionOn: String get() = t("با سقفِ تخفیف سنجیده می‌شود", "Checked against the discount limit", "د تخفیف له حد سره سنجول کېږي")
  val note: String get() = t(
    "روشن: قیمتِ دستیِ پایین‌تر از قیمتِ کالا و تخفیفِ ردیف هم تخفیف‌اند و با همان سقفِ تخفیفِ هر نقش و «درخواستِ تأیید» سنجیده می‌شوند. «تغییرِ قیمت» و «سقفِ قلمِ آزاد» برای همه جز صاحبِ دکان است. فقط روی همین دستگاه.",
    "On: a manual price below the item's price and a line discount are discounts too, checked against each role's discount limit and \"request approval\". \"Price change\" and \"custom item limit\" apply to everyone except the shop owner. This device only.",
    "روښانه: د توکي له بیې ټیټه لاسي بیه او د کرښې تخفیف هم تخفیف دی او د هر رول د تخفیف له حد او «د تایید غوښتنې» سره سنجول کېږي. «د بیې بدلون» او «د آزاد توکي حد» پرته د دوکان له خاوند څخه په ټولو پلي کېږي. یوازې همدا وسیله.",
  )
  val direction: String get() = t("تغییرِ قیمتِ ردیف", "Changing a line price", "د کرښې د بیې بدلون")
  val dirBoth: String get() = t("هر دو", "Both ways", "دواړه")
  val dirUp: String get() = t("فقط بالا", "Up only", "یوازې پورته")
  val dirDown: String get() = t("فقط پایین", "Down only", "یوازې ښکته")
  val dirNone: String get() = t("هیچ", "Never", "هېڅ")
  val freeCap: String get() = t("سقفِ قیمتِ قلمِ آزاد (؋)", "Custom item price limit (؋)", "د آزاد توکي د بیې حد (؋)")
  val freeCapNote: String get() = t("خالی = بی سقف، ۰ = قلمِ آزاد بسته", "Empty = no limit, 0 = custom items closed", "تش = بې حده، ۰ = آزاد توکی بند")

  // ردیفِ سبد
  val lineDiscount: String get() = t("تخفیفِ ردیف", "Line discount", "د کرښې تخفیف")
  val percent: String get() = t("درصد", "Percent", "سلنه")
  val amount: String get() = t("مبلغ", "Amount", "اندازه")
  val lineDiscountNote: String get() = t(
    "تخفیف به قیمتِ هر واحدِ همین ردیف تبدیل می‌شود و با سقفِ تخفیفِ شما سنجیده می‌شود.",
    "The discount becomes this line's unit price and is checked against your discount limit.",
    "تخفیف د همدې کرښې د هر واحد په بیه اوړي او ستاسو د تخفیف له حد سره سنجول کېږي.",
  )
  fun afterDiscount(a: Any): String = t("پس از تخفیف: $a ؋", "After discount: $a ؋", "له تخفیف وروسته: $a ؋")

  // رد
  val priceUpBlocked: String get() = t("بالا بردنِ قیمتِ ردیف در این دکان بسته است", "Raising a line price is not allowed in this shop", "په دې دوکان کې د کرښې بیه لوړول بند دي")
  val priceDownBlocked: String get() = t("پایین آوردنِ قیمتِ ردیف در این دکان بسته است", "Lowering a line price is not allowed in this shop", "په دې دوکان کې د کرښې بیه ټیټول بند دي")
  val reductionDenied: String get() = t("کاهشِ قیمت یا تخفیفِ ردیف برای شما ممنوع است", "You're not allowed to lower a price or give a line discount", "تاسو ته د بیې ټیټولو یا د کرښې تخفیف اجازه نشته")
  val freeLineClosed: String get() = t("قلمِ آزاد برای شما بسته است", "Custom items are closed for you", "آزاد توکی ستاسو لپاره بند دی")
  val tooLarge: String get() = t("عدد بیش از اندازه است", "The number is too large", "شمېره له حده ډېره لویه ده")
  fun freeLineOverCap(a: Any): String = t("قیمتِ قلمِ آزاد حداکثر $a ؋", "A custom item can cost at most $a ؋", "د آزاد توکي بیه تر $a ؋ پورې")
}
