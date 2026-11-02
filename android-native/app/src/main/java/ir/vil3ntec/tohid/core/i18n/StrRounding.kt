package ir.vil3ntec.tohid.core.i18n

/**
 *  گردکردنِ نقدی در صندوق (PAY-09): ردیفِ فاکتور، رسید، گزارش و تنظیمِ همین دستگاه.
 *
 *  قاعده‌ها سرِ `I18n.kt`؛ منطق در `core/pay/TillRounding` (commonMain). ترجمه‌های نامطمئن در
 *  `docs/i18n-glossary.md` برای بازبینیِ گویشور.
 */
@Suppress("unused")
object StrRounding {
  /** ردیفِ جدای فاکتور، رسید و گزارش */
  val line: String get() = t("گردکردنِ نقدی", "Cash rounding", "د نغدو ګردول")
  val off: String get() = StrShell.switchOff
  val five: String get() = t("۵ افغانی", "5 AFN", "۵ افغانۍ")
  val ten: String get() = t("۱۰ افغانی", "10 AFN", "۱۰ افغانۍ")
  val note: String get() = t(
    "نقدی که مشتری می‌دهد به نزدیک‌ترین ۵ یا ۱۰ افغانی گرد می‌شود و تفاوت یک ردیفِ جدای «گردکردنِ نقدی» روی فاکتور است؛ قیمت و تخفیف دست نمی‌خورند. فقط وقتی نقد فاکتور را می‌بندد: در پرداختِ ترکیبی فقط سهمِ نقد، و فروشِ نسیه هرگز. برگشتِ کاملِ فاکتور همان پرداختی را پس می‌دهد و برگشت‌ها هرگز بیش از آن نمی‌شوند. روشن کردنش با تأییدِ حسابدار؛ فقط روی همین دستگاه.",
    "The cash the customer pays is rounded to the nearest 5 or 10 AFN and the difference is a separate “Cash rounding” line on the invoice; prices and discounts are not changed. Only when cash settles the invoice: in a mixed payment only the cash part, and never on credit sales. A full return gives back exactly what was paid, and returns never exceed it. Turn it on only with your accountant's approval; this device only.",
    "هغه نغدې پیسې چې پیرودونکی یې ورکوي تر ټولو نږدې ۵ یا ۱۰ افغانیو ته ګردېږي او توپیر یې په بل کې د «د نغدو ګردول» جلا کرښه ده؛ بیې او تخفیف نه بدلېږي. یوازې کله چې نغدې پیسې بل تړي: په ګډه ورکړه کې یوازې د نغدو برخه، او په نسیه پلور کې هېڅکله. د بل بشپړ بېرته ورکول همغه ورکړل شوې پیسې بېرته ورکوي او بېرته ورکړې له هغې نه زیاتېږي. یوازې د محاسب په تایید یې چالان کړئ؛ یوازې په همدې وسیله.",
  )
}
