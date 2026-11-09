package ir.vil3ntec.tohid.core.text

/**
 *  CRM-15 — «کمترین اطلاعاتِ شخصی، دسترسیِ محدود»: نقشی که مجوزِ `customers.contact` ندارد (پیش‌فرض: انباردار و
 *  جمع‌آور؛ صاحب برای هر نقش یا کاربر تنگ یا باز می‌کند) شمارهٔ مشتری را پوشیده می‌بیند — هر رقم جز دو رقمِ آخر «•»،
 *  تا مشتری را از هم‌نامش جدا کند ولی شماره به دستش نیاید. فقط نمایش است (داده روی دستگاه هست؛ امنیت نیست).
 *  رقمِ فارسی/عربی هم رقم است؛ نشانه‌ها (+، فاصله، خط تیره) و جهت‌نماهای متن همان می‌مانند. منطقِ خالص؛ آزمون:
 *  `ContactMaskTest` (commonTest).
 */
object ContactMask {

  const val DOT = '•'

  /** هر رقم جز [keep] رقمِ آخر ⇒ «•»؛ بی رقم ⇒ همان متن */
  fun phone(text: String, keep: Int = 2): String {
    val digits = text.count { it.isDigit() }
    var seen = 0
    return buildString(text.length) {
      for (ch in text) {
        if (ch.isDigit()) {
          seen++
          append(if (seen > digits - keep) ch else DOT)
        } else append(ch)
      }
    }
  }

  /** [visible] ⇒ همان متن؛ وگرنه پوشیده ([phone]) */
  fun show(text: String, visible: Boolean): String = if (visible) text else phone(text)

  /**
   *  شمارهٔ پوشیده (همین قالب، یا همان `maskPhone` ِ سرور برای اصلِ بی `customers.contact`)؟ ⇒ نه «زنگ»، نه «کپی»:
   *  `tel:` با «•» شماره‌گیرِ بی‌فایده باز می‌کند
   */
  fun isMasked(text: String): Boolean = DOT in text
}
