package ir.vil3ntec.tohid.core.hw

/**
 *  چاپِ چندتکه از راهِ صفِ چاپ، با «لغو» در میانه — گزارشِ بلندِ «تاریخچه» (همهٔ فاکتورهای یک روز، ماه یا سال).
 *
 *  هر تکه کارِ جدای `PrintQueue` است (نوعِ `REPORT`) و صفحه هرگز منتظرِ چاپگر نمی‌ماند. قاعده‌ها:
 *    • تکه‌ها به ترتیب ساخته و سپرده می‌شوند و حداکثر [ahead] تکه هم‌زمان در صف است: بی چاپگر، صف از کاغذِ
 *      یک سال پر نمی‌شود و «لغو» فقط همان چند تکهٔ منتظر را کنار می‌گذارد ([cancel] شناسه‌هایشان را می‌دهد).
 *    • تکه‌ای که همین حالا به چاپگر می‌رود تمام می‌شود — بایتِ فرستاده پس نمی‌آید؛ بعدی چاپ نمی‌شود.
 *    • کارِ تکه‌ای که از بیرون کنار گذاشته شد (صفحهٔ «دستگاه‌ها») یعنی «بقیه هم نه»: لغو، نه چاپِ ناقصِ بی‌صدا.
 *    • تکه‌ای که پس از لغو از ساخت رسید پذیرفته نمی‌شود ([queued] ⇒ false؛ صدازننده کنارش می‌گذارد).
 *
 *  خالص (commonMain): نه نخ، نه صف، نه ساعت؛ صدازننده کارها را می‌سپارد و پایانِ هر کار را خبر می‌دهد.
 */
class PrintBatch(val total: Int, private val ahead: Int = 2) {

  init {
    require(total >= 0) { "شمارِ تکه منفی است" }
    require(ahead >= 1) { "دست‌کم یک تکه در صف" }
  }

  enum class Phase { RUNNING, DONE, CANCELLED, FAILED }

  private var next = 0
  private val waiting = ArrayList<String>()

  /** تکه‌هایی که روی کاغذ آمدند */
  var printed: Int = 0
    private set

  var phase: Phase = if (total == 0) Phase.DONE else Phase.RUNNING
    private set

  /** شناسهٔ کارهای منتظرِ همین دسته در صف، به ترتیب */
  val queuedJobs: List<String> get() = waiting.toList()

  /** شمارهٔ تکهٔ بعدی (از ۰) که باید ساخته و سپرده شود؛ null ⇒ فعلاً نه (صف پر است، تمام شد یا ایستاد) */
  fun take(): Int? {
    if (phase != Phase.RUNNING || next >= total || waiting.size >= ahead) return null
    return next++
  }

  /** تکهٔ گرفته‌شده به صف رفت. false ⇒ دسته در این میان ایستاد؛ صدازننده همین کار را کنار بگذارد */
  fun queued(jobId: String): Boolean {
    if (phase != Phase.RUNNING) return false
    waiting += jobId
    return true
  }

  /**
   *  کارِ صف تمام شد: [printed] = روی کاغذ آمد؛ وگرنه از بیرون کنار گذاشته شد ⇒ بقیهٔ دسته هم لغو.
   *  @return شناسه‌هایی که اکنون باید از صف کنار بروند
   */
  fun finished(jobId: String, printed: Boolean): List<String> {
    if (!waiting.remove(jobId)) return emptyList()
    if (!printed) return stop(Phase.CANCELLED)
    this.printed++
    if (phase == Phase.RUNNING && this.printed == total) phase = Phase.DONE
    return emptyList()
  }

  /** «لغوِ چاپ»: هیچ تکهٔ تازه‌ای نه؛ @return کارهای منتظر که باید از صف کنار بروند */
  fun cancel(): List<String> = stop(Phase.CANCELLED)

  /** ساختنِ تکه نشد (قلم، حافظه…): مثلِ لغو، با پیامِ خطا نزدِ صدازننده */
  fun fail(): List<String> = stop(Phase.FAILED)

  private fun stop(to: Phase): List<String> {
    if (phase != Phase.RUNNING) return emptyList()
    phase = to
    return waiting.toList().also { waiting.clear() }
  }

  companion object {
    /**
     *  بخش کردنِ تکه‌های پیاپی (بلندیِ تخمینیِ هر کدام به نقطه) به چند کارِ چاپ: هر کار تا [maxDots] و تا
     *  [maxItems] تکه؛ تکه‌ای که خودش بلندتر است کارِ خودش را می‌گیرد. ترتیب همیشه همان است.
     */
    fun plan(heights: List<Int>, maxDots: Int, maxItems: Int = Int.MAX_VALUE): List<IntRange> {
      require(maxDots > 0 && maxItems > 0)
      val out = ArrayList<IntRange>()
      var start = 0
      var sum = 0
      for (i in heights.indices) {
        val h = heights[i].coerceAtLeast(0)
        val count = i - start
        if (count > 0 && (sum + h > maxDots || count >= maxItems)) {
          out += start until i
          start = i
          sum = 0
        }
        sum += h
      }
      if (start < heights.size) out += start until heights.size
      return out
    }
  }
}
