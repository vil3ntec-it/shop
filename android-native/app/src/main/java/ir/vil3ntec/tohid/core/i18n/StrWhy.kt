package ir.vil3ntec.tohid.core.i18n

/**
 *  صندوق — «چرا این قیمت؟» (POS-22): قیمتِ هر ردیف گام‌به‌گام.
 *  قاعده‌ها سرِ `I18n.kt`؛ ترجمه‌های نامطمئن در `docs/i18n-glossary.md`.
 */
@Suppress("unused")
object StrWhy {
  val section: String get() = t("«چرا این قیمت؟» در صندوق", "\"Why this price?\" at the till", "په صندوق کې «ولې دا بیه؟»")
  val note: String get() = t(
    "روشن: کادرِ ویرایشِ هر ردیفِ سبد نشان می‌دهد قیمت از کجا آمد — قیمتِ فهرستِ کالا، قیمتِ دستیِ همین ردیف، هر تخفیفِ خودکار با نامش، گرد کردن و جمعِ ردیف. فقط نمایش است و قیمتی را عوض نمی‌کند. فقط روی همین دستگاه.",
    "On: each cart line's edit box shows where its price came from — the item's list price, this line's manual price, each automatic discount by name, rounding and the line total. Display only; it changes no price. This device only.",
    "روښانه: د ټوکرۍ د هرې کرښې د سمون بکس ښيي چې بیه له کومه راغله — د توکي د لړلیک بیه، د همدې کرښې لاسي بیه، هر خپلکاره تخفیف له نوم سره، ګردول او د کرښې ټولټال. یوازې ښودنه ده او هېڅ بیه نه بدلوي. یوازې همدا وسیله.",
  )
  val title: String get() = t("چرا این قیمت؟", "Why this price?", "ولې دا بیه؟")
  val list: String get() = t("قیمتِ فهرستِ کالا (هر واحد)", "Item list price (each)", "د توکي د لړلیک بیه (هر واحد)")
  val manual: String get() = t("قیمتِ دستیِ همین ردیف (هر واحد)", "Manual price on this line (each)", "د همدې کرښې لاسي بیه (هر واحد)")
  fun cheaper(a: Any): String = t("$a ؋ زیرِ فهرست", "$a ؋ below the list", "$a ؋ له لړلیک ټیټه")
  fun dearer(a: Any): String = t("$a ؋ بالای فهرست", "$a ؋ above the list", "$a ؋ له لړلیک لوړه")
  val base: String get() = t("جمع پیش از تخفیفِ خودکار", "Before automatic discounts", "له خپلکاره تخفیف مخکې")
  fun promo(a: Any): String = t("تخفیفِ خودکار: $a", "Automatic discount: $a", "خپلکاره تخفیف: $a")
  val rounding: String get() = t("گرد کردن", "Rounding", "ګردول")
  val total: String get() = t("جمعِ این ردیف", "Line total", "د دې کرښې ټولټال")
}
