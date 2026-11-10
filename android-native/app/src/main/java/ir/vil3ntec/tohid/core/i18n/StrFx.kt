package ir.vil3ntec.tohid.core.i18n

/**
 *  ارزِ نقدِ دیگر در صندوق (PAY-10): کادرِ صندوق، پیام‌ها، تنظیمِ نرخ و خطِ X/Z.
 *
 *  قاعده‌ها سرِ `I18n.kt`؛ منطق در `core/pay/FxTill` (commonMain) و `data/FxTillFlow`. عدد، کدِ ارز و خطِ نرخ
 *  («1 USD = 70.25 AFN») همان رقمِ لاتینِ برنامه‌اند و از بیرون می‌آیند.
 */
@Suppress("unused")
object StrFx {
  /** نامِ ارز با کدش (نگاشتِ کد ⇒ نام: `FxTillFlow.name`) */
  val usd: String get() = t("دالر (USD)", "US dollar (USD)", "ډالر (USD)")
  val pkr: String get() = t("کلدار (PKR)", "Pakistani rupee (PKR)", "کلدار (PKR)")
  val irr: String get() = t("ریال (IRR)", "Iranian rial (IRR)", "ریال (IRR)")

  // ── صندوق ──
  val tillTitle: String get() = t("ارزِ نقد", "Foreign cash", "بهرنۍ نغدې پیسې")
  fun amountIn(currency: String): String = t("مبلغ به ${asIs(currency)}", "Amount in $currency", "مقدار په $currency")
  fun rateNote(line: String, source: String, at: String): String =
    t("نرخ: ${asIs(line)} — ${asIs(source)}، ${asIs(at)}", "Rate: $line — $source, $at", "نرخ: $line — $source، $at")
  fun afnValue(amount: String): String = t("ارزشِ افغانی: ${asIs(amount)}", "Value in AFN: $amount", "په افغانیو ارزښت: $amount")
  val changeNote: String get() = t(
    "پولِ پس همیشه افغانیِ نقد است؛ فروش به افغانی ثبت می‌شود و ارز با همین نرخ کنارِ فاکتور می‌ماند.",
    "Change is always given in AFN cash; the sale is recorded in AFN and the foreign cash stays on the invoice at this rate.",
    "بېرته پیسې تل افغانۍ نغدې دي؛ پلور په افغانیو ثبتېږي او بهرنۍ پیسې په همدې نرخ د بل تر څنګ پاتې کېږي.",
  )

  // ── پیام‌ها ──
  val amountBad: String get() = t("مبلغِ ارز درست نیست", "The foreign amount is not valid", "د بهرنیو پیسو مقدار سم نه دی")
  val noRate: String get() = t("برای این ارز نرخی گذاشته نشده؛ مدیر در تنظیماتِ پرداخت نرخ بگذارد", "No rate is set for this currency; a manager can set one in payment settings", "د دې اسعارو لپاره نرخ نه دی ټاکل شوی؛ مدیر دې د تادیې په تنظیماتو کې نرخ وټاکي")
  val stale: String get() = t("نرخِ این ارز کهنه است (بیش از یک روز)؛ مدیر نرخِ امروز را بگذارد", "This currency's rate is out of date (over a day old); a manager should set today's rate", "د دې اسعارو نرخ زوړ دی (له یوې ورځې زیات)؛ مدیر دې د نن نرخ وټاکي")
  val excess: String get() = t("این ارز لازم نیست؛ فاکتور بی آن پرداخت شده است", "This foreign cash is not needed; the invoice is already paid", "دا بهرنۍ پیسې اړینې نه دي؛ بل پرته له دې ورکړل شوی")
  val invalid: String get() = t("ارزِ این پرداخت درست نیست؛ مبلغ را دوباره بنویسید", "The foreign cash on this payment is not valid; enter the amount again", "د دې تادیې بهرنۍ پیسې سمې نه دي؛ مقدار بیا ولیکئ")

  // ── تنظیم (صاحب/مدیر) ──
  val settingTitle: String get() = t("پرداخت با ارزِ نقد (دالر، کلدار، ریال)", "Accept foreign cash (USD, PKR, IRR)", "په بهرنیو نغدو پیسو تادیه (ډالر، کلدار، ریال)")
  val settingNote: String get() = t(
    "صندوق کادرِ «ارزِ نقد» را نشان می‌دهد: مشتری با ارز می‌پردازد، نرخ و ارزشِ افغانی دیده می‌شود و پولِ پس افغانی است. فروش و جمع‌ها همیشه افغانی‌اند؛ ارز با نرخ، منبع، زمان و نامِ گذارندهٔ نرخ روی فاکتور می‌ماند و نرخِ تازه فاکتورهای پیشین را عوض نمی‌کند. نرخِ کهنه‌تر از یک روز پذیرفته نمی‌شود. برای همهٔ دستگاه‌های دکان؛ فقط با «ترکیبی و شیفت».",
    "The till shows a “Foreign cash” box: the customer pays in another currency, the rate and AFN value are shown and change is given in AFN. Sales and totals are always in AFN; the foreign cash stays on the invoice with the rate, source, time and who set the rate, and a new rate never changes earlier invoices. Rates older than one day are refused. For every device of the shop; only with “Mixed and shifts”.",
    "صندوق د «بهرنۍ نغدې پیسې» بکس ښيي: پېرودونکی په بل اسعارو ورکوي، نرخ او په افغانیو ارزښت ښکاري او بېرته پیسې افغانۍ دي. پلور او ټولګې تل افغانۍ دي؛ بهرنۍ پیسې د نرخ، سرچینې، وخت او د نرخ ټاکونکي له نوم سره په بل پاتې کېږي او نوی نرخ پخواني بلونه نه بدلوي. له یوې ورځې زوړ نرخ نه منل کېږي. د دوکان ټولو وسیلو لپاره؛ یوازې له «ګډ او شفټ» سره.",
  )
  fun rateLabel(per: String, currency: String): String = t("نرخِ ${asIs(per)} ${asIs(currency)} به افغانی", "AFN for $per $currency", "د $per $currency نرخ په افغانیو")
  val sourceLabel: String get() = t("منبعِ نرخ (صرافی یا بانک)", "Rate source (exchange or bank)", "د نرخ سرچینه (صرافي یا بانک)")
  val save: String get() = t("ذخیرهٔ نرخ", "Save rate", "نرخ خوندي کړئ")
  val remove: String get() = t("برداشتنِ نرخ", "Remove rate", "نرخ لرې کړئ")
  val none: String get() = t("نرخی گذاشته نشده", "No rate set", "نرخ نه دی ټاکل شوی")
  fun setAt(line: String, source: String, at: String, by: String): String =
    t("${asIs(line)} — ${asIs(source)}، ${asIs(at)}، ${asIs(by)}", "$line — $source, $at, $by", "$line — $source، $at، $by")
  val staleTag: String get() = t("کهنه — صندوق نمی‌پذیرد", "Out of date — the till refuses it", "زوړ — صندوق یې نه مني")
  val saved: String get() = t("نرخ ذخیره شد", "Rate saved", "نرخ خوندي شو")
  val rateBad: String get() = t("نرخ درست نیست (عددِ مثبت به افغانی)", "The rate is not valid (a positive AFN amount)", "نرخ سم نه دی (په افغانیو مثبت عدد)")
  val sourceBad: String get() = t("منبعِ نرخ را بنویسید (یک خط، تا ۶۰ نویسه)", "Enter the rate source (one line, up to 60 characters)", "د نرخ سرچینه ولیکئ (یوه کرښه، تر ۶۰ توریو)")

  // ── X/Z ──
  fun xzLine(currency: String, amount: String, afn: String, count: Int): String =
    t("${asIs(currency)} ${asIs(amount)} (ارزشِ ${asIs(afn)}) — ${asIs(count)} فاکتور", "$currency $amount (worth $afn) — $count ${pl(count, "invoice", "invoices")}", "$currency $amount (ارزښت $afn) — $count بله")
  fun xzCount(currency: String, count: Int): String =
    t("${asIs(currency)} — ${asIs(count)} فاکتور با ارز", "$currency — $count ${pl(count, "invoice", "invoices")} in foreign cash", "$currency — $count بله په بهرنیو پیسو")
}
