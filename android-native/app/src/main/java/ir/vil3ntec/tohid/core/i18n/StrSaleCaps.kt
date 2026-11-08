package ir.vil3ntec.tohid.core.i18n

/**
 *  STF-03 — پیش‌سنجیِ سقف‌های کارمند در صندوق (`data/SaleCapGate`، قاعده در `core/perm/SaleCaps`): فروشِ قرضی، تغییرِ
 *  قیمتِ ردیف و «تخفیفِ پنهان». فقط وقتی سرور همان سقف را اجرا می‌کند (پرچم‌های `shop.credit_cap` و `shop.price_cap`).
 *  ⛔ مبلغ، درصد و سقف از `asIs` می‌گذرند (لایهٔ دری دستشان نمی‌زند).
 */
@Suppress("unused")
object StrSaleCaps {
  val creditTitle: String get() = t("فروشِ قرضی بیش از سقفِ شما", "Credit sale above your limit", "ستاسو له حد څخه زیات پور پلور")
  fun creditSummary(amount: String, limit: String): String = t(
    "قرضِ این فاکتور ${asIs(amount)} افغانی است؛ سقفِ شما ${asIs(limit)} افغانی.",
    "This invoice puts $amount afghani on credit; your limit is $limit afghani.",
    "د دې بل پور $amount افغانۍ دی؛ ستاسو حد $limit افغانۍ.",
  )
  val creditDenied: String get() = t(
    "فروشِ قرضی برای نقشِ شما ممنوع است؛ فاکتور را کامل پرداخت کنید یا صاحبِ دکان ثبت کند.",
    "Credit sales are not allowed for your role; take full payment or ask the owner to record it.",
    "ستاسو رول ته پور پلور اجازه نه لري؛ بشپړه پیسې واخلئ یا د دوکان خاوند یې ثبت کړي.",
  )
  val priceTitle: String get() = t("تغییرِ قیمت بیش از سقفِ شما", "Price change above your limit", "ستاسو له حد څخه زیات د بیې بدلون")
  fun priceSummary(pct: String, limit: String): String = t(
    "بیشترین تغییرِ قیمتِ یک ردیف نسبت به قیمتِ فهرست ${asIs(pct)}٪ است؛ سقفِ شما ${asIs(limit)}٪.",
    "The largest line price change from the list price is $pct%; your limit is $limit%.",
    "د لیست له بیې څخه د یوې کرښې تر ټولو لوی بدلون $pct٪ دی؛ ستاسو حد $limit٪.",
  )
  val priceDenied: String get() = t(
    "تغییرِ قیمتِ ردیف برای نقشِ شما ممنوع است؛ قیمتِ فهرست را بگذارید.",
    "Changing a line price is not allowed for your role; use the list price.",
    "ستاسو رول ته د کرښې د بیې بدلول اجازه نه لري؛ د لیست بیه وکاروئ.",
  )
  val hiddenTitle: String get() = t("تخفیف (با کاهشِ قیمتِ ردیف) بیش از سقف", "Discount (including lowered line prices) above the limit", "تخفیف (د کرښې د ټیټې بیې په ګډون) له حد څخه زیات")
  fun hiddenSummary(amount: String, pct: String): String = t(
    "کاهشِ قیمتِ ردیف و کمبودِ جمعِ نهایی هم تخفیف‌اند: روی‌هم ${asIs(amount)} افغانی (${asIs(pct)}٪).",
    "Lowered line prices and a short final total count as discount: $amount afghani in all ($pct%).",
    "د کرښې ټیټه بیه او د وروستي ټولټال کمښت هم تخفیف دی: ټول $amount افغانۍ ($pct٪).",
  )
  val hiddenDenied: String get() = t(
    "کاهشِ قیمتِ ردیف هم تخفیف است و تخفیف برای نقشِ شما ممنوع است؛ قیمتِ فهرست را بگذارید.",
    "A lowered line price is a discount too, and discounts are not allowed for your role; use the list price.",
    "د کرښې ټیټه بیه هم تخفیف دی او ستاسو رول ته تخفیف اجازه نه لري؛ د لیست بیه وکاروئ.",
  )
  fun needsApproval(title: String): String = t(
    "${asIs(title)} — تأییدِ مدیر لازم است",
    "$title — a manager's approval is needed",
    "$title — د مدیر تایید ته اړتیا ده",
  )
}
