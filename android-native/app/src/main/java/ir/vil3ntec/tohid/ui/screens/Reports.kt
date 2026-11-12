package ir.vil3ntec.tohid.ui.screens

/** بیسیس‌پوینت ⇒ «۲۶.۸٪» (یک رقمِ اعشار، رقمِ لاتین مثلِ بقیهٔ عددها) */
internal fun bpText(bp: Long): String {
  val a = kotlin.math.abs(bp)
  return "${if (bp < 0) "-" else ""}${a / 100}.${(a % 100) / 10}٪"
}

