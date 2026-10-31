package ir.vil3ntec.tohid.core.hw

/**
 *  HW-10 — نمایشگرِ قطبیِ مشتری (VFD دوخطیِ ۲۰ نویسه‌ای) با فرمان‌های ESC/POSِ نمایشگر (Epson DM-D و
 *  سازگارها): `ESC @` آغاز، `FF` (0x0C) پاک کردن، `US $ x y` جای نشانگر (از ۱).
 *
 *  جدولِ نویسهٔ این نمایشگرها دری و پشتو ندارد؛ پس فقط **رقمِ لاتین و برچسبِ انگلیسیِ کوتاه** فرستاده
 *  می‌شود (نامِ کالا نه) و هر نویسهٔ بیرون از ASCII ِ دیدنی به `?` بدل می‌شود. پنجرهٔ دومِ کامپیوتر
 *  (`CustomerFacing`) همچنان نام‌ها را به دری نشان می‌دهد.
 *
 *  خالص است؛ روی نمایشگرِ واقعی آزموده نشده.
 */
object PoleDisplay {
  const val COLS = 20

  /** یک سطر: فقط ASCII ِ دیدنی، دقیقاً `COLS` نویسه (بلندتر بریده، کوتاه‌تر با فاصله پر) */
  fun fit(s: String): String {
    val ascii = buildString {
      for (c in s) append(
        when (c) {
          in '۰'..'۹' -> '0' + (c - '۰')
          in '٠'..'٩' -> '0' + (c - '٠')
          else -> if (c.code in 32..126) c else '?'
        },
      )
    }
    return if (ascii.length >= COLS) ascii.substring(0, COLS) else ascii.padEnd(COLS)
  }

  /** دو برچسب و دو مقدار: برچسب چپ، مقدار راست */
  fun row(label: String, value: String): String {
    val v = fit(value).trimEnd()
    val room = (COLS - v.length - 1).coerceAtLeast(0)
    val l = fit(label).trimEnd().take(room)
    return fit(l + " ".repeat(COLS - l.length - v.length) + v)
  }

  /** مبلغ به واحدِ کوچک (پول × ۱۰۰) ⇒ «1,250» یا «1,250.50» */
  fun amount(minor: Long): String {
    val neg = minor < 0
    val a = if (neg) -minor else minor
    val whole = (a / 100).toString().reversed().chunked(3).joinToString(",").reversed()
    val cents = (a % 100).toInt()
    return (if (neg) "-" else "") + whole + if (cents != 0) "." + cents.toString().padStart(2, '0') else ""
  }

  /** بایت‌های یک صفحه: آغاز، پاک کردن، سطرِ ۱، رفتن به سطرِ ۲، سطرِ ۲ */
  fun frame(line1: String, line2: String): ByteArray {
    val out = EscPos.Bytes()
    out.add(byteArrayOf(0x1B, 0x40, 0x0C))
    out.add(fit(line1).encodeToByteArray())
    out.add(byteArrayOf(0x1F, 0x24, 1, 2))
    out.add(fit(line2).encodeToByteArray())
    return out.toByteArray()
  }

  /** سبد: شمارِ قلم‌ها و جمع؛ سبدِ خالی ⇒ خوش‌آمد */
  fun cart(items: Int, totalMinor: Long, currency: String = "AFN"): ByteArray =
    if (items <= 0) frame("WELCOME", "")
    else frame(row("Items", items.toString()), row("Total $currency", amount(totalMinor)))

  /** پس از فروش: پرداخت‌شده و باقی (اگر هست) */
  fun paid(totalMinor: Long, tenderedMinor: Long? = null, currency: String = "AFN"): ByteArray {
    val change = tenderedMinor?.let { it - totalMinor }?.takeIf { it > 0 }
    return if (change != null) frame(row("Paid $currency", amount(tenderedMinor)), row("Change", amount(change)))
    else frame(row("Total $currency", amount(totalMinor)), "THANK YOU")
  }
}
