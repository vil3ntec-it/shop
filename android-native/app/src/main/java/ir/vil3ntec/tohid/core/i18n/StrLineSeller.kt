package ir.vil3ntec.tohid.core.i18n

/**
 *  POS-23 / STF-08 — «فروشندهٔ هر ردیف» (پایهٔ پورسانت): تنظیمِ دکان (`LineSellerPref`)، برگهٔ صندوق
 *  (`ui/components/pos/LineSellerAndroid.kt`، گوشی و کامپیوتر) و ستونِ برگشتیِ «فروشِ هر کاربر».
 *  ⛔ نام و مبلغ از `asIs` می‌گذرند (لایهٔ دری دستشان نمی‌زند).
 */
@Suppress("unused")
object StrLineSeller {
  val settingsTitle: String get() = t("فروشندهٔ هر ردیف", "Seller for each line", "د هرې کرښې پلورونکی")
  val settingsNote: String get() = t(
    "روشن: در صندوق هر ردیف (یا چند ردیف) به نامِ کارمندی زده می‌شود که آن را فروخت (در کامپیوتر Shift+F2). " +
      "«فروشِ هر کاربر» و «پورسانتِ این ماه» هر ردیف را به نامِ فروشندهٔ خودش می‌شمارند و برگشت از همان فروشنده کم می‌شود. " +
      "فقط صاحب و مدیر نامِ کسِ دیگری را می‌گذارند؛ هر کس ردیف را به نامِ خودش می‌زند. خاموش: فروشنده همان کاربرِ فاکتور است، مثلِ همیشه.",
    "On: at the till each line (or several lines) is put on the staff member who sold it (Shift+F2 on the computer). " +
      "“Sales by user” and “Commission this month” count each line for its own seller, and a return comes off that same seller. " +
      "Only the owner and managers can pick someone else; anyone can put a line on themselves. Off: the seller is the invoice's user, as always.",
    "روښانه: په صندوق کې هره کرښه (یا څو کرښې) د هغه کارکوونکي په نوم کېږي چې پلورلې یې ده (په کمپیوټر کې Shift+F2). " +
      "«د هر کارن پلور» او «د دې میاشتې کمیشن» هره کرښه د خپل پلورونکي په نوم شمېري او بېرته ورکړه له همغه پلورونکي کمېږي. " +
      "یوازې خاوند او مدیر د بل چا نوم ټاکي؛ هر څوک کرښه په خپل نوم کولی شي. بند: پلورونکی د فاکتور کارن دی، لکه تل.",
  )
  val helpLine: String get() = t(
    "هر ردیف به نامِ کارمندی که فروخت؛ پایهٔ پورسانت",
    "Put each line on the staff member who sold it; the basis for commission",
    "هره کرښه د هغه کارکوونکي په نوم چې پلورلې یې ده؛ د کمیشن بنسټ",
  )
  val helpExample: String get() = t(
    "مثال: احمد فاکتور را می‌زند ولی روغن را زهرا فروخت؛ ردیفِ روغن و پورسانتش به نامِ زهرا می‌رود.",
    "Example: Ahmad rings up the invoice but Zahra sold the oil; the oil line and its commission go on Zahra.",
    "بېلګه: احمد فاکتور جوړوي خو غوړي زهرا پلورلي؛ د غوړیو کرښه او کمیشن یې د زهرا په نوم کېږي.",
  )

  val button: String get() = t("فروشندهٔ ردیف‌ها", "Line sellers", "د کرښو پلورونکي")
  val sheetTitle: String get() = t("فروشندهٔ ردیف", "Seller for lines", "د کرښې پلورونکی")
  val seller: String get() = t("فروشنده", "Seller", "پلورونکی")
  val whichLines: String get() = t("کدام ردیف‌ها", "Which lines", "کومې کرښې")
  fun invoiceSeller(name: String): String = t(
    "فروشندهٔ فاکتور (${asIs(name)})",
    "Invoice seller ($name)",
    "د فاکتور پلورونکی ($name)",
  )
  val onlyMe: String get() = t(
    "فقط صاحب و مدیر ردیف را به نامِ کسِ دیگری می‌زنند؛ شما ردیف را به نامِ خودتان می‌زنید.",
    "Only the owner and managers can put a line on someone else; you can put lines on yourself.",
    "یوازې خاوند او مدیر کرښه د بل چا په نوم کوي؛ تاسو کرښه په خپل نوم کولی شئ.",
  )
  val pickLine: String get() = t("دست‌کم یک ردیف را بزنید.", "Tick at least one line.", "لږ تر لږه یوه کرښه وټاکئ.")
  fun returnsOf(amount: String): String = t("برگشتی ${asIs(amount)}", "Returns $amount", "بېرته ورکړه $amount")
}
