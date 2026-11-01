package ir.vil3ntec.tohid.core.chat

/**
 *  رسانهٔ چتِ دکان روی اینترنتِ ضعیف و دستگاهِ پر (v3 §۳۳.۴؛ CHAT-05، CHAT-07) — منطقِ خالص،
 *  بی اندروید و بی JDK، برای اندروید، کامپیوتر و بعداً آیفون.
 *
 *  ── CHAT-05: پیشرفتِ فرستادن (`ChatProgress`) ─────────────────────────
 *  یک پیامِ عکس دو بارگذاری دارد (اصلی + پیش‌نمایش)؛ صدا یکی. نوارِ پیشرفت جمعِ
 *  بایت‌های رفتهٔ همهٔ بخش‌های **هنوز نرفته** است، نه درصدِ هر بخش جدا — وگرنه پس از
 *  عکسِ اصلی نوار به صفر برمی‌گشت. «۱۰۰» فقط وقتی همهٔ بخش‌ها تمام شد؛ درصدِ عقب‌رونده
 *  (تلاشِ دوبارهٔ همان بخش) نشان داده نمی‌شود. فقط تغییرِ دست‌کم یک درصد گزارش می‌شود تا
 *  صفحه با هر تکهٔ ۱۶ کیلوبایتی از نو کشیده نشود.
 *
 *  ── CHAT-07: جای دستگاه (`ChatSpace`) ─────────────────────────────────
 *  پیش از ضبط، پخش یا آماده کردنِ عکس سنجیده می‌شود که پس از نوشتن دست‌کم
 *  `RESERVE_BYTES` جا بماند؛ نبود ⇒ پیامِ روشنِ «جا کم است» به‌جای خطای مبهمِ دیسک یا
 *  فایلِ نیمه‌کاره. جای نامعلوم (`null`) کار را نمی‌بندد: سنجش فقط کمک است، نه قفل.
 */
object ChatSpace {
  /** پس از نوشتن این‌قدر جا بماند تا خودِ برنامه و دفترش گیر نکنند */
  const val RESERVE_BYTES: Long = 16L * 1024 * 1024

  /** سربارِ ظرفِ صدا (OGG/MP4) و فراداده */
  const val VOICE_OVERHEAD_BYTES: Long = 64L * 1024

  /** بدترین حجمِ ضبطِ `maxMs` با `bitRate` بیت در ثانیه (پیش‌فرضِ برنامه: ۲۴ کیلوبیت) */
  fun voiceNeed(maxMs: Long, bitRate: Int = 24_000): Long =
    maxOf(0L, maxMs) * maxOf(0, bitRate) / 8 / 1000 + VOICE_OVERHEAD_BYTES

  /** جای پیامِ عکس روی دستگاه: اصلی + پیش‌نمایش در صفِ ارسال، تا سقفِ سرور */
  fun imageNeed(imageMax: Long, thumbMax: Long): Long = maxOf(0L, imageMax) + maxOf(0L, thumbMax)

  /** آیا `need` بایت جا هست و پس از آن `RESERVE_BYTES` می‌ماند؟ `free` نامعلوم ⇒ بله. */
  fun fits(free: Long?, need: Long): Boolean {
    if (free == null || free < 0) return true
    return free - maxOf(0L, need) >= RESERVE_BYTES
  }
}

/**
 *  پیشرفتِ یک پیامِ رسانه‌ای از چند بارگذاری (`sizes` به ترتیبِ فرستادن، فقط بخش‌های
 *  هنوز نرفته). یک نمونه برای هر تلاشِ فرستادن؛ هم‌زمان‌امن نیست و لازم هم نیست (هر پیام
 *  در صفِ خودش یکی‌یکی می‌رود).
 */
class ChatProgress(sizes: List<Long>) {
  private val sizes = sizes.map { maxOf(0L, it) }
  private val sent = LongArray(this.sizes.size)
  private val finished = BooleanArray(this.sizes.size)
  private val total = this.sizes.sum()
  private var shown = -1

  /** درصدِ کنونی (۰ تا ۱۰۰)؛ «۱۰۰» فقط وقتی سرور همهٔ بخش‌ها را پذیرفت، پیش از آن حداکثر ۹۹ */
  val percent: Int
    get() {
      val all = finished.all { it }
      if (total <= 0L) return if (all) 100 else 0
      val raw = ((sent.sum() * 100) / total).toInt().coerceIn(0, 100)
      return if (all) 100 else minOf(raw, 99)
    }

  /**
   *  بخشِ `part` تا `bytes` بایت رفت ⇒ درصدِ تازه، یا `null` اگر درصدِ نشان‌داده عوض نمی‌شود.
   *  بخشِ ناشناخته و عددِ بیرون از بازه نادیده/بریده می‌شود؛ عقب‌گرد نشان داده نمی‌شود.
   */
  fun at(part: Int, bytes: Long): Int? {
    if (part !in sizes.indices) return null
    sent[part] = maxOf(sent[part], bytes.coerceIn(0L, sizes[part]))
    return emit()
  }

  /** بخشِ `part` تمام شد (سرور پذیرفت) */
  fun done(part: Int): Int? {
    if (part !in sizes.indices) return null
    sent[part] = sizes[part]
    finished[part] = true
    return emit()
  }

  private fun emit(): Int? {
    val p = percent
    if (p <= shown) return null
    shown = p
    return p
  }
}
