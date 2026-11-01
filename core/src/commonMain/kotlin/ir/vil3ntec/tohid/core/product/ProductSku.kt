package ir.vil3ntec.tohid.core.product

/*
 *  کدِ کالا (SKU) — PRD-03، §۶.۳.
 *
 *  کدی کوتاه که دکان خودش به کالا می‌دهد («SKU-00042»، «CH-500»)؛ جدا از بارکد (بارکد را کارخانه
 *  می‌دهد و اسکن می‌شود). یک کالا یک کد؛ کالای بی کد همان پیشین است.
 *
 *  ── شکل ────────────────────────────────────────────────────────────
 *  فاصله‌ها بیرون، رقمِ فارسی/عربی ⇒ لاتین، حرفِ لاتین بزرگ؛ فقط حرف، رقم و «- _ . /»؛ حداکثر [MAX].
 *  پس «sku-۰۰۰۴۲» و «SKU-00042» یک کدند و دو کالا آن را نمی‌گیرند.
 *
 *  ── تولیدِ خودکار ───────────────────────────────────────────────────
 *  [next]: «SKU-» + یکی بیش از بزرگ‌ترین شمارهٔ همین پیشوند در دکان، پنج رقمی. دو دستگاهِ آفلاین
 *  می‌توانند یک شماره بسازند؛ ثبت روی هر دستگاه یکتایی را می‌سنجد ([owner]) و تعارضِ آفلاین
 *  ویرایشِ کالا را قفل نمی‌کند (همان قاعدهٔ بارکد، §۳.۴) — فقط کدِ **تازه** سنجیده می‌شود.
 *
 *  منطقِ خالص (commonMain، بی java.* و android.*)؛ آزمون: `ProductSkuTest`.
 */
object ProductSku {

  /** سقفِ نویسهٔ کد */
  const val MAX = 32

  /** پیشوندِ کدِ خودکار */
  const val PREFIX = "SKU-"

  /** پهنای شمارهٔ کدِ خودکار (۰۰۰۴۲) */
  private const val WIDTH = 5

  enum class Problem { BAD_CHAR, TOO_LONG }

  private fun latinDigit(c: Char): Char = when (c) {
    in '۰'..'۹' -> '0' + (c - '۰')
    in '٠'..'٩' -> '0' + (c - '٠')
    else -> c
  }

  private fun allowed(c: Char): Boolean = c.isLetterOrDigit() || c == '-' || c == '_' || c == '.' || c == '/'

  /** متنِ کادر ⇒ کد، بی بریدن (سقف را [problem] می‌گوید) */
  private fun normal(text: String): String = buildString {
    for (ch in text) {
      if (ch.isWhitespace() || ch == '‌') continue
      append(latinDigit(ch).let { if (it in 'a'..'z') it - 32 else it })
    }
  }

  /** کادر ⇒ کدِ ذخیره؛ تهی یعنی «کد ندارد». نویسهٔ نادرست بیرون می‌رود و حداکثر [MAX] */
  fun clean(text: String): String = normal(text).filter(::allowed).take(MAX)

  /** ایرادِ متنِ کادر، پیش از ذخیره؛ `null` ⇒ همان [clean] ذخیره می‌شود */
  fun problem(text: String): Problem? {
    val n = normal(text)
    return when {
      n.any { !allowed(it) } -> Problem.BAD_CHAR
      n.length > MAX -> Problem.TOO_LONG
      else -> null
    }
  }

  /** کلیدِ مقایسه — همان [clean]؛ کدِ تهی هرگز کلید نیست */
  fun key(sku: String): String = clean(sku)

  /**
   *  کدِ خودکارِ بعدی: [PREFIX] + (بزرگ‌ترین شمارهٔ همین پیشوند + ۱)، پنج رقمی (بیشتر اگر لازم شد).
   *  کدهای دیگر (بی این پیشوند یا با پسوند) به حساب نمی‌آیند؛ هرگز کدِ موجودی را برنمی‌گرداند.
   */
  fun next(existing: Collection<String>): String {
    val taken = existing.mapTo(HashSet()) { key(it) }
    var n = existing.maxOfOrNull { s ->
      key(s).takeIf { it.startsWith(PREFIX) }?.substring(PREFIX.length)
        ?.takeIf { it.isNotEmpty() && it.length <= 12 && it.all { c -> c in '0'..'9' } }?.toLong() ?: 0L
    } ?: 0L
    while (true) {
      n += 1
      val code = PREFIX + n.toString().padStart(WIDTH, '0')
      if (code !in taken) return code
    }
  }

  /**
   *  شناسهٔ کالای دیگری که همین کد را دارد؛ `null` ⇒ آزاد. [codes]: شناسهٔ کالا ⇒ کدش (کالای بی کد
   *  نیاید). [selfId]: کالای در حالِ ویرایش، که کدِ خودش را می‌تواند نگه دارد.
   */
  fun owner(sku: String, selfId: String?, codes: Map<String, String>): String? {
    val k = key(sku)
    if (k.isEmpty()) return null
    return codes.entries.firstOrNull { (id, s) -> id != selfId && key(s) == k }?.key
  }

  /** پرس‌وجو آغازِ همین کد است؟ (کد و پرس‌وجو هر دو با [key]) — پرس‌وجوی تهی ⇒ نه */
  fun startsWith(sku: String, query: String): Boolean {
    val q = key(query)
    return q.isNotEmpty() && key(sku).startsWith(q)
  }
}
