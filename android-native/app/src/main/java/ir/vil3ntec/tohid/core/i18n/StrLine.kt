package ir.vil3ntec.tohid.core.i18n

/**
 *  قیمتِ ردیف در صندوق — تخفیفِ ردیف، «قاعدهٔ جهت» و سقفِ قلمِ آزاد (POS-08، POS-10، POS-20، POS-21)؛
 *  با همین تنظیم تخفیفِ کلِ فاکتورِ کامپیوتر به مبلغ هم (POS-20).
 *  قاعده‌ها سرِ `I18n.kt`؛ ترجمه‌های نامطمئن در `docs/i18n-glossary.md`.
 */
@Suppress("unused")
object StrLine {

  // تنظیمات
  val section: String get() = t("قیمت و تخفیفِ ردیف", "Line prices and discounts", "د کرښې بیه او تخفیف")
  val sectionOn: String get() = t("با سقفِ تخفیف سنجیده می‌شود", "Checked against the discount limit", "د تخفیف له حد سره سنجول کېږي")
  val note: String get() = t(
    "روشن: قیمتِ دستیِ پایین‌تر از قیمتِ کالا و تخفیفِ ردیف هم تخفیف‌اند و با همان سقفِ تخفیفِ هر نقش و «درخواستِ تأیید» سنجیده می‌شوند. «تغییرِ قیمت» و «سقفِ قلمِ آزاد» برای همه جز صاحبِ دکان است. صندوقِ کامپیوتر تخفیفِ کلِ فاکتور را به مبلغ هم می‌گیرد. فقط روی همین دستگاه.",
    "On: a manual price below the item's price and a line discount are discounts too, checked against each role's discount limit and \"request approval\". \"Price change\" and \"custom item limit\" apply to everyone except the shop owner. The computer till also takes an invoice discount as an amount. This device only.",
    "روښانه: د توکي له بیې ټیټه لاسي بیه او د کرښې تخفیف هم تخفیف دی او د هر رول د تخفیف له حد او «د تایید غوښتنې» سره سنجول کېږي. «د بیې بدلون» او «د آزاد توکي حد» پرته د دوکان له خاوند څخه په ټولو پلي کېږي. د کمپیوټر صندوق د ټول بل تخفیف په اندازه هم اخلي. یوازې همدا وسیله.",
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
  // POS-19 — پلهٔ قیمتِ ثابت در فرمِ تخفیف
  val tierFixedPrice: String get() = t("قیمتِ ثابت", "Fixed price", "ثابته بیه")
  val tierUnitPrice: String get() = t("قیمتِ هر واحد (؋)", "Price per unit (؋)", "د هر واحد بیه (؋)")
  fun tierUnitText(a: Any): String = t("${asIs(a)} ؋ هر واحد", "$a ؋ per unit", "$a ؋ د هر واحد")
  fun afterDiscount(a: Any): String = t("پس از تخفیف: ${asIs(a)} ؋", "After discount: $a ؋", "له تخفیف وروسته: $a ؋")

  // رد
  val priceUpBlocked: String get() = t("بالا بردنِ قیمتِ ردیف در این دکان بسته است", "Raising a line price is not allowed in this shop", "په دې دوکان کې د کرښې بیه لوړول بند دي")
  val priceDownBlocked: String get() = t("پایین آوردنِ قیمتِ ردیف در این دکان بسته است", "Lowering a line price is not allowed in this shop", "په دې دوکان کې د کرښې بیه ټیټول بند دي")
  val reductionDenied: String get() = t("کاهشِ قیمت یا تخفیفِ ردیف برای شما ممنوع است", "You're not allowed to lower a price or give a line discount", "تاسو ته د بیې ټیټولو یا د کرښې تخفیف اجازه نشته")
  val freeLineClosed: String get() = t("قلمِ آزاد برای شما بسته است", "Custom items are closed for you", "آزاد توکی ستاسو لپاره بند دی")
  val tooLarge: String get() = t("عدد بیش از اندازه است", "The number is too large", "شمېره له حده ډېره لویه ده")
  fun freeLineOverCap(a: Any): String = t("قیمتِ قلمِ آزاد حداکثر ${asIs(a)} ؋", "A custom item can cost at most $a ؋", "د آزاد توکي بیه تر $a ؋ پورې")

  // PRM-11 — تخفیفِ خودکار فقط برای گروه‌های مشتری (گروهِ CRM-08 در پروندهٔ مشتری)
  val promoGroupsTitle: String get() = t("برای کدام مشتریان", "For which customers", "د کومو پیرودونکو لپاره")
  val promoGroupsHint: String get() = t(
    "هیچ گروهی نزنید ⇐ همه، حتی فروشِ بی مشتری. با گروه، تخفیف فقط وقتی می‌آید که مشتریِ همان گروه روی فاکتور باشد (گروه در پروندهٔ مشتری: «گروه، لیستِ قیمت و رضایت»).",
    "Pick no group ⇒ everyone, even a sale without a customer. With groups, the discount applies only when a customer of that group is on the invoice (set the group in the customer's file: “Group, price list and consent”).",
    "هېڅ ډله مه ټاکئ ⇐ ټول، حتی بې پیرودونکي پلور. له ډلې سره، تخفیف یوازې هغه وخت راځي چې د هماغې ډلې پیرودونکی په بل کې وي (ډله د پیرودونکي په دوسیه کې: «ډله، د بیو لړ او رضایت»).",
  )
  fun promoOnlyGroups(names: String): String = t("فقط گروهِ ${asIs(names)}", "Only group $names", "یوازې ډله $names")

  // PRM-11 — کانالِ قاعده: صندوقِ دکان، ویترینِ اینترنتی یا هر دو (فقط وقتی ویترین تخفیف می‌خواند)
  val promoWhereTitle: String get() = t("کجا اعمال شود", "Where it applies", "چېرته پلي شي")
  val promoWherePos: String get() = t("فقط صندوقِ دکان", "Shop till only", "یوازې د دوکان صندوق")
  val promoWhereOnline: String get() = t("فقط ویترینِ اینترنتی", "Online storefront only", "یوازې آنلاین ویترین")
  val promoWhereBoth: String get() = t("صندوق و ویترین", "Till and storefront", "صندوق او ویترین")
  val promoOnlyOnline: String get() = t("فقط ویترین", "Storefront only", "یوازې ویترین")
  val promoWhereHint: String get() = t(
    "«ویترین» یعنی سفارشِ اینترنتیِ همین دکان. قاعدهٔ «فقط ویترینِ اینترنتی» در صندوق اعمال نمی‌شود و کدش در صندوق پذیرفته نمی‌شود؛ قاعدهٔ «فقط صندوقِ دکان» در ویترین اعمال نمی‌شود.",
    "“Storefront” means this shop's online orders. An “Online storefront only” rule never applies at the till and its code isn't accepted there; a “Shop till only” rule never applies online.",
    "«ویترین» یعنی د همدې دوکان آنلاین فرمایش. د «یوازې آنلاین ویترین» قاعده په صندوق کې نه پلي کېږي او کوډ یې په صندوق کې نه منل کېږي؛ د «یوازې د دوکان صندوق» قاعده په ویترین کې نه پلي کېږي.",
  )
}
