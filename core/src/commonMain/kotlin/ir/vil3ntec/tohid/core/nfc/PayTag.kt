package ir.vil3ntec.tohid.core.nfc

/**
 *  برچسبِ NFC ِ پرداختِ حساب‌پی در صندوقِ گوشیِ نسخهٔ ۳ (PAY-21؛ گوشیِ فروشنده برچسبِ نوعِ ۴ را با HCE شبیه‌سازی
 *  می‌کند، [NdefType4Tag.processFile]). گوشیِ مشتری که نزدیک شود یک پیامِ NDEF با دو رکورد می‌خواند:
 *
 *   1. **URI** — نشانیِ پرداختِ حساب‌پی: در «خودکار» همان `checkoutUrl` ِ قصدِ سرور (جلسه‌ای که حساب‌پی با همین مبلغ و
 *      مرجع ساخته)؛ در «ساده» QR ِ خودِ دکان اگر نشانیِ `http(s)` است. اندروید و آیفون رکوردِ URI ِ نخست را باز می‌کنند
 *      (برنامهٔ حساب‌پی اگر آن نشانی را می‌گیرد، وگرنه مرورگر). نشانیِ ناپذیرفتنی (شمارهٔ خالی، متنِ غیرِ نشانی) ⇒ این
 *      رکورد نیست.
 *   2. **Text** — همان چیزی که کنارِ QR نوشته است، تا مشتری پیش از پرداخت ببیند: مبلغ، کدِ یکتای فاکتور
 *      (`HesabPayShop.invoiceRef`) و گیرنده (نام و ۴ رقمِ آخرِ حسابِ فعال، #853).
 *
 *  ⛔ هیچ رازی (کلید، شمارهٔ کامل، اثرِ انگشت) در برچسب نیست؛ فقط آنچه روی صفحه هم دیده می‌شود.
 *  ⛔ مبلغ فقط عددِ صحیح به پول؛ متن رقمِ لاتین (هر گوشی می‌خواند). کاتلینِ خالص؛ آزمون: `PayTagTest`.
 */
object PayTag {

  /** آنچه برچسب نشان می‌دهد */
  data class Info(val amountMinor: Long, val ref: String, val payeeName: String, val payeeLast4: String)

  /** «HesabPay · 1,450 AFN · HP-ABCD-EFGH · → نام ••1234» */
  fun text(i: Info): String {
    val payee = buildString {
      append("→ ")
      append(i.payeeName.trim().ifEmpty { "—" })
      if (i.payeeLast4.isNotBlank()) append(" ••").append(i.payeeLast4.trim())
    }
    return "HesabPay · ${amount(i.amountMinor)} AFN · ${i.ref.trim()} · $payee"
  }

  /** پیامِ NDEF: URI (اگر [link] پذیرفتنی است) + Text؛ مبلغِ نادرست یا کدِ خالی ⇒ `null` (برچسب خاموش) */
  fun message(link: String?, i: Info): ByteArray? {
    if (i.amountMinor <= 0 || i.ref.isBlank()) return null
    val uri = link?.trim()?.takeIf { Ndef.linkOk(it) }
    return Ndef.message(listOfNotNull(uri?.let(Ndef::uriRecord), Ndef.textRecord(text(i), "fa")))
  }

  /** پروندهٔ NDEF ِ برچسب (NLEN + پیام)؛ بیش از اندازه یا ناپذیرفتنی ⇒ `null` */
  fun file(link: String?, i: Info): ByteArray? = message(link, i)?.let(Ndef::fileOf)

  /** پول ⇒ «1,450» یا «1,450.25» (رقمِ لاتین، جداکنندهٔ هزارگان) */
  internal fun amount(minor: Long): String {
    val whole = (minor / 100).toString().reversed().chunked(3).joinToString(",").reversed()
    val cents = minor % 100
    return if (cents == 0L) whole else whole + "." + cents.toString().padStart(2, '0')
  }
}
